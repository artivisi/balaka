package com.artivisi.accountingfinance.functional.service;

import com.artivisi.accountingfinance.entity.AmortizationEntry;
import com.artivisi.accountingfinance.entity.AmortizationSchedule;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.AmortizationEntryStatus;
import com.artivisi.accountingfinance.enums.AmortizationFrequency;
import com.artivisi.accountingfinance.enums.ScheduleStatus;
import com.artivisi.accountingfinance.enums.ScheduleType;
import com.artivisi.accountingfinance.repository.AmortizationEntryRepository;
import com.artivisi.accountingfinance.repository.AmortizationScheduleRepository;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.service.AmortizationScheduleService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Amortization workflow for the IT service company: quarterly deferred-revenue schedule created through
 * the form, journal direction for intangible-asset and accrued-revenue schedules, and the list's pending
 * counter. Create/edit/post/skip/cancel/delete/filter/batch details are in AmortizationControllerFunctionalTest.
 *
 * Schedules are created by the test with unique codes and 2047 dates.
 */
@DisplayName("Service Industry - Amortization")
@Import(ServiceTestDataInitializer.class)
class ServiceAmortizationTest extends PlaywrightTestBase {

    @Autowired
    private AmortizationScheduleRepository scheduleRepository;

    @Autowired
    private AmortizationEntryRepository entryRepository;

    @Autowired
    private AmortizationScheduleService scheduleService;

    @Autowired
    private ChartOfAccountRepository chartOfAccountRepository;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setup() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("Quarterly deferred-revenue schedule caps the last quarter at the end date and posts revenue recognition")
    void shouldCreateQuarterlyUnearnedRevenueScheduleAndPostRecognition() {
        String code = code();
        String name = "Kontrak Maintenance " + code;

        navigateTo("/amortization/new");
        waitForPageLoad();
        page.locator("input[name='code']").fill(code);
        page.locator("input[name='name']").fill(name);
        page.locator("select[name='scheduleType']").selectOption(ScheduleType.UNEARNED_REVENUE.name());
        page.locator("select[name='sourceAccountId']").selectOption(account("2.1.04").getId().toString());
        page.locator("select[name='targetAccountId']").selectOption(account("4.1.02").getId().toString());
        page.locator("input[name='totalAmount']").fill("1000000");
        page.locator("input[name='startDate']").fill("2047-01-01");
        page.locator("input[name='endDate']").fill("2047-11-30");
        page.locator("select[name='frequency']").selectOption(AmortizationFrequency.QUARTERLY.name());
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        AmortizationSchedule schedule = schedule(code);
        assertThat(page).hasURL(baseUrl() + "/amortization/" + schedule.getId());
        assertThat(page.getByTestId("amortization-detail")).containsText("Pendapatan Diterima Dimuka");
        assertThat(page.getByTestId("amortization-detail")).containsText("Triwulanan");
        assertThat(page.getByTestId("amortization-detail")).containsText("0/4");

        assertThat(schedule.getFrequency()).isEqualTo(AmortizationFrequency.QUARTERLY);
        assertThat(schedule.getTotalPeriods()).isEqualTo(4);
        assertThat(schedule.getPeriodAmount()).isEqualByComparingTo("250000");
        List<AmortizationEntry> entries = entries(schedule);
        assertThat(entries).extracting(AmortizationEntry::getPeriodStart).containsExactly(
                LocalDate.of(2047, 1, 1), LocalDate.of(2047, 4, 1), LocalDate.of(2047, 7, 1), LocalDate.of(2047, 10, 1));
        assertThat(entries).extracting(AmortizationEntry::getPeriodEnd).containsExactly(
                LocalDate.of(2047, 3, 31), LocalDate.of(2047, 6, 30), LocalDate.of(2047, 9, 30), LocalDate.of(2047, 11, 30));
        assertThat(entries).extracting(e -> e.getAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString())
                .containsExactly("250000.00", "250000.00", "250000.00", "250000.00");

        entryRow(entries.get(3)).locator("button:has-text('Posting')").click();
        assertThat(entryRow(entries.get(3))).containsText("Terposting");

        AmortizationEntry posted = entryRepository.findById(entries.get(3).getId()).orElseThrow();
        Posting journal = posting(posted);
        assertThat(journal.date()).isEqualTo(LocalDate.of(2047, 11, 30));
        assertThat(journal.reference()).isEqualTo(code + "-4");
        assertThat(journal.description()).startsWith("Pengakuan Pendapatan Diterima Dimuka - " + name);
        assertThat(journal.lines()).containsExactly("2.1.04 D250000.00 C0.00", "4.1.02 D0.00 C250000.00");
        assertThat(schedule(code).getRemainingAmount()).isEqualByComparingTo("750000");
    }

    @Test
    @DisplayName("Intangible-asset schedule posts debit amortization expense, credit accumulated amortization")
    void shouldPostIntangibleAssetAmortizationAsExpense() {
        AmortizationSchedule schedule = createSchedule("Lisensi Software", ScheduleType.INTANGIBLE_ASSET,
                "1.2.02", "5.1.09", "900000", LocalDate.of(2047, 1, 1), LocalDate.of(2047, 3, 31));

        postAll(schedule);

        List<AmortizationEntry> entries = entries(schedule);
        assertThat(entries).extracting(AmortizationEntry::getStatus).containsOnly(AmortizationEntryStatus.POSTED);
        Posting first = posting(entries.getFirst());
        assertThat(first.description()).startsWith("Amortisasi Aset Tak Berwujud - " + schedule.getName());
        assertThat(first.lines()).containsExactly("5.1.09 D300000.00 C0.00", "1.2.02 D0.00 C300000.00");
        assertThat(schedule(schedule.getCode()).getStatus()).isEqualTo(ScheduleStatus.COMPLETED);
    }

    @Test
    @DisplayName("Accrued-revenue schedule posts debit receivable, credit revenue")
    void shouldPostAccruedRevenueAsReceivable() {
        AmortizationSchedule schedule = createSchedule("Retainer Akrual", ScheduleType.ACCRUED_REVENUE,
                "1.1.10", "4.1.03", "1000000", LocalDate.of(2047, 1, 1), LocalDate.of(2047, 3, 31));

        postAll(schedule);

        List<AmortizationEntry> entries = entries(schedule);
        Posting last = posting(entries.getLast());
        assertThat(last.description()).startsWith("Pengakuan Pendapatan Akrual - " + schedule.getName());
        assertThat(last.date()).isEqualTo(LocalDate.of(2047, 3, 31));
        assertThat(last.lines()).containsExactly("1.1.10 D333333.34 C0.00", "4.1.03 D0.00 C333333.34");
        assertThat(posting(entries.getFirst()).lines())
                .containsExactly("1.1.10 D333333.33 C0.00", "4.1.03 D0.00 C333333.33");
    }

    @Test
    @DisplayName("List shows the new schedule's row and raises the pending-entry counter by its period count")
    void shouldListScheduleAndCountItsPendingEntries() {
        long pendingBefore = entryRepository.countPendingEntries();
        AmortizationSchedule schedule = createSchedule("Asuransi Kantor", ScheduleType.PREPAID_EXPENSE,
                "1.1.15", "5.1.05", "12000000", LocalDate.of(2047, 1, 1), LocalDate.of(2047, 12, 31));

        navigateTo("/amortization?search=" + schedule.getCode());
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Jadwal Amortisasi");
        assertThat(page.getByTestId("amortization-list"))
                .containsText((pendingBefore + 12) + " entry pending");
        Locator row = page.locator("[data-testid='schedule-table'] tbody tr[data-id='" + schedule.getId() + "']");
        assertThat(row).containsText(schedule.getCode());
        assertThat(row).containsText(schedule.getName());
        assertThat(row).containsText("Beban Dibayar Dimuka");
        assertThat(row).containsText("12,000,000");
        assertThat(row).containsText("0/12");
        assertThat(row).containsText("Aktif");

        row.locator("a:has-text('Detail')").click();
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/amortization/" + schedule.getId());
        assertThat(page.getByTestId("amortization-detail")).containsText(schedule.getName());
        assertThat(page.getByTestId("amortization-detail")).containsText("Rp 1,000,000");
    }

    private record Posting(LocalDate date, String reference, String description, List<String> lines) {
    }

    private void postAll(AmortizationSchedule schedule) {
        navigateTo("/amortization/" + schedule.getId());
        waitForPageLoad();
        page.locator("form[action='/amortization/" + schedule.getId() + "/entries/post-all'] button").click();
        waitForPageLoad();
        assertThat(page.getByTestId("flash-success")).hasText("Semua entry pending berhasil diposting");
    }

    private Posting posting(AmortizationEntry entry) {
        assertThat(entry.getJournalEntryId()).as("journal of period " + entry.getPeriodNumber()).isNotNull();
        return new TransactionTemplate(transactionManager).execute(status -> {
            Transaction tx = journalEntryRepository.findById(entry.getJournalEntryId()).orElseThrow().getTransaction();
            List<String> lines = journalEntryRepository.findByTransactionIdOrderByJournalNumberAsc(tx.getId()).stream()
                    .map(l -> l.getAccount().getAccountCode()
                            + " D" + l.getDebitAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString()
                            + " C" + l.getCreditAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString())
                    .toList();
            return new Posting(tx.getTransactionDate(), tx.getReferenceNumber(), tx.getDescription(), lines);
        });
    }

    private AmortizationSchedule createSchedule(String name, ScheduleType type, String source, String target,
                                                String amount, LocalDate start, LocalDate end) {
        String code = code();
        AmortizationSchedule schedule = new AmortizationSchedule();
        schedule.setCode(code);
        schedule.setName(name + " " + code);
        schedule.setScheduleType(type);
        schedule.setSourceAccount(account(source));
        schedule.setTargetAccount(account(target));
        schedule.setTotalAmount(new BigDecimal(amount));
        schedule.setStartDate(start);
        schedule.setEndDate(end);
        schedule.setFrequency(AmortizationFrequency.MONTHLY);
        schedule.setAutoPost(false);
        schedule.setPostDay(1);
        return scheduleService.create(schedule);
    }

    private Locator entryRow(AmortizationEntry entry) {
        return page.locator("[data-testid='entry-table'] tbody tr[data-id='" + entry.getId() + "']");
    }

    private AmortizationSchedule schedule(String code) {
        return scheduleRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Amortization schedule missing: " + code));
    }

    private List<AmortizationEntry> entries(AmortizationSchedule schedule) {
        return entryRepository.findByScheduleIdOrderByPeriodNumberAsc(schedule.getId());
    }

    private ChartOfAccount account(String code) {
        return chartOfAccountRepository.findByAccountCode(code)
                .orElseThrow(() -> new IllegalStateException("Seeded account missing: " + code));
    }

    private static String code() {
        return "AMT47-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
