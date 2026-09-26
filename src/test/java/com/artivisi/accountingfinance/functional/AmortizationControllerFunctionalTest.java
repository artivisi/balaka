package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.AmortizationEntry;
import com.artivisi.accountingfinance.entity.AmortizationSchedule;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.AmortizationEntryStatus;
import com.artivisi.accountingfinance.enums.AmortizationFrequency;
import com.artivisi.accountingfinance.enums.ScheduleStatus;
import com.artivisi.accountingfinance.enums.ScheduleType;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.AmortizationEntryRepository;
import com.artivisi.accountingfinance.repository.AmortizationScheduleRepository;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.service.AmortizationEntryService;
import com.artivisi.accountingfinance.service.AmortizationScheduleService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * AmortizationController: create (UI) with schedule math, validation, edit, entry post/skip/post-all,
 * cancel, delete, list filters/search/pagination, HTMX fragment and batch auto-post.
 *
 * Every schedule is created by the test itself with a unique code. Schedules use 2045 dates so that
 * batch processing (which posts all due auto-post entries) never touches them, except the batch test's
 * own 2002 schedules which are due by construction.
 */
@DisplayName("Amortization Controller Tests")
@Import(ServiceTestDataInitializer.class)
class AmortizationControllerFunctionalTest extends PlaywrightTestBase {

    private static final String PREPAID = "1.1.15";
    private static final String RENT_EXPENSE = "5.1.05";
    private static final String UNEARNED = "2.1.04";
    private static final String CONSULTING_REVENUE = "4.1.02";

    @Autowired
    private AmortizationScheduleRepository scheduleRepository;

    @Autowired
    private AmortizationEntryRepository entryRepository;

    @Autowired
    private AmortizationScheduleService scheduleService;

    @Autowired
    private AmortizationEntryService entryService;

    @Autowired
    private ChartOfAccountRepository coaRepository;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
    }

    // ==================== create ====================

    @Test
    @DisplayName("Create form lists schedule types, frequencies, active accounts and auto-post defaults")
    void shouldShowCreateFormOptions() {
        navigateTo("/amortization/new");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Jadwal Amortisasi Baru");
        assertThat(page.locator("select[name='scheduleType'] option")).hasText(new String[]{
                "-- Pilih Tipe --", "Beban Dibayar Dimuka", "Pendapatan Diterima Dimuka",
                "Aset Tak Berwujud", "Pendapatan Akrual"});
        assertThat(page.locator("select[name='frequency'] option")).hasText(new String[]{"Bulanan", "Triwulanan"});
        assertThat(page.locator("select[name='sourceAccountId'] option[value='" + account(PREPAID).getId() + "']"))
                .hasText("1.1.15 - Beban Dibayar Dimuka");
        assertThat(page.locator("select[name='targetAccountId'] option[value='" + account(RENT_EXPENSE).getId() + "']"))
                .hasText("5.1.05 - Beban Sewa");
        assertThat(page.locator("input[name='autoPost']")).not().isChecked();
        assertThat(page.locator("input[name='postDay']")).hasValue("1");
        takeManualScreenshot("amortization-form");
    }

    @Test
    @DisplayName("Creating a monthly schedule generates one entry per month with the rounding remainder on the last")
    void shouldCreateMonthlyScheduleWithRemainderOnLastEntry() {
        String code = code();
        String name = "Sewa Kantor " + code;

        fillCreateForm(code, name, ScheduleType.PREPAID_EXPENSE, PREPAID, RENT_EXPENSE, "10000000",
                LocalDate.of(2045, 1, 1), LocalDate.of(2045, 3, 31), AmortizationFrequency.MONTHLY);
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        AmortizationSchedule schedule = schedule(code);
        assertThat(page).hasURL(baseUrl() + "/amortization/" + schedule.getId());
        assertThat(page.getByTestId("flash-success")).hasText("Jadwal amortisasi berhasil dibuat");

        assertThat(schedule.getName()).isEqualTo(name);
        assertThat(schedule.getStatus()).isEqualTo(ScheduleStatus.ACTIVE);
        assertThat(schedule.getTotalPeriods()).isEqualTo(3);
        assertThat(schedule.getPeriodAmount()).isEqualByComparingTo("3333333.33");
        assertThat(schedule.getTotalAmount()).isEqualByComparingTo("10000000");
        assertThat(schedule.getRemainingAmount()).isEqualByComparingTo("10000000");
        assertThat(schedule.getAmortizedAmount()).isEqualByComparingTo("0");
        assertThat(schedule.getAutoPost()).isFalse();

        List<AmortizationEntry> entries = entries(schedule);
        assertThat(entries).extracting(AmortizationEntry::getPeriodNumber).containsExactly(1, 2, 3);
        assertThat(entries).extracting(AmortizationEntry::getPeriodStart).containsExactly(
                LocalDate.of(2045, 1, 1), LocalDate.of(2045, 2, 1), LocalDate.of(2045, 3, 1));
        assertThat(entries).extracting(AmortizationEntry::getPeriodEnd).containsExactly(
                LocalDate.of(2045, 1, 31), LocalDate.of(2045, 2, 28), LocalDate.of(2045, 3, 31));
        assertThat(entries).extracting(e -> e.getAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString())
                .containsExactly("3333333.33", "3333333.33", "3333333.34");
        assertThat(entries).extracting(AmortizationEntry::getStatus).containsOnly(AmortizationEntryStatus.PENDING);

        var detail = page.getByTestId("amortization-detail");
        assertThat(detail).containsText(name);
        assertThat(detail).containsText(code);
        assertThat(detail).containsText("Aktif");
        assertThat(detail).containsText("Rp 10,000,000");
        assertThat(detail).containsText("0/3");
        assertThat(detail).containsText("1.1.15 - Beban Dibayar Dimuka");
        assertThat(detail).containsText("5.1.05 - Beban Sewa");
        assertThat(detail).containsText("01 Jan 2045 - 31 Mar 2045");
        assertThat(entryRows()).hasCount(3);
        assertThat(entryRow(entries.get(2))).containsText("#3");
        assertThat(entryRow(entries.get(2))).containsText("01 Mar - 31 Mar 2045");
        assertThat(entryRow(entries.get(2))).containsText("Rp 3,333,333");
        assertThat(entryRow(entries.get(2))).containsText("Menunggu");
    }

    @Test
    @DisplayName("Create rejects identical source/target account, duplicate code and inverted dates without persisting")
    void shouldRejectInvalidScheduleOnCreate() {
        String sameAccountCode = code();
        fillCreateForm(sameAccountCode, "Akun Sama " + sameAccountCode, ScheduleType.PREPAID_EXPENSE, PREPAID, PREPAID,
                "1200000", LocalDate.of(2045, 1, 1), LocalDate.of(2045, 12, 31), AmortizationFrequency.MONTHLY);
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        assertThat(page.getByTestId("flash-error")).hasText("Source and target accounts must be different");
        assertThat(page.locator("input[name='code']")).hasValue(sameAccountCode);
        assertThat(page.locator("input[name='name']")).hasValue("Akun Sama " + sameAccountCode);
        assertThat(page.locator("input[name='totalAmount']")).hasValue("1200000");
        assertThat(page.locator("input[name='startDate']")).hasValue("2045-01-01");
        assertThat(page.locator("input[name='endDate']")).hasValue("2045-12-31");
        assertThat(scheduleRepository.findByCode(sameAccountCode)).isEmpty();

        String invertedCode = code();
        fillCreateForm(invertedCode, "Tanggal Terbalik " + invertedCode, ScheduleType.PREPAID_EXPENSE, PREPAID,
                RENT_EXPENSE, "1200000", LocalDate.of(2045, 12, 31), LocalDate.of(2045, 1, 1),
                AmortizationFrequency.MONTHLY);
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        assertThat(page.getByTestId("flash-error")).hasText("Start date must be before end date");
        assertThat(scheduleRepository.findByCode(invertedCode)).isEmpty();

        AmortizationSchedule existing = createSchedule("Asli", ScheduleType.PREPAID_EXPENSE, PREPAID, RENT_EXPENSE,
                "1200000", LocalDate.of(2045, 1, 1), LocalDate.of(2045, 12, 31), false);
        fillCreateForm(existing.getCode(), "Duplikat", ScheduleType.PREPAID_EXPENSE, PREPAID, RENT_EXPENSE,
                "500000", LocalDate.of(2045, 1, 1), LocalDate.of(2045, 6, 30), AmortizationFrequency.MONTHLY);
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        assertThat(page.getByTestId("flash-error")).hasText("Schedule code already exists: " + existing.getCode());
        AmortizationSchedule reloaded = schedule(existing.getCode());
        assertThat(reloaded.getName()).isEqualTo(existing.getName());
        assertThat(reloaded.getTotalAmount()).isEqualByComparingTo("1200000");
        assertThat(entries(reloaded)).hasSize(12);
    }

    // ==================== edit ====================

    @Test
    @DisplayName("Edit updates name, description and auto-post settings only")
    void shouldUpdateEditableFields() {
        AmortizationSchedule schedule = createSchedule("Edit", ScheduleType.PREPAID_EXPENSE, PREPAID, RENT_EXPENSE,
                "1200000", LocalDate.of(2045, 1, 1), LocalDate.of(2045, 12, 31), false);

        navigateTo("/amortization/" + schedule.getId());
        waitForPageLoad();
        page.locator("a[href='/amortization/" + schedule.getId() + "/edit']").click();
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Edit Jadwal Amortisasi");
        assertThat(page.getByTestId("amortization-form")).containsText(schedule.getCode());
        assertThat(page.locator("input[name='code']")).hasCount(0);
        assertThat(page.locator("input[name='totalAmount']")).hasCount(0);
        assertThat(page.locator("input[name='name']")).hasValue(schedule.getName());
        assertThat(page.locator("input[name='autoPost']")).not().isChecked();
        assertThat(page.locator("input[name='postDay']")).hasValue("1");

        String newName = schedule.getName() + " Diubah";
        page.locator("input[name='name']").fill(newName);
        page.locator("textarea[name='description']").fill("Sewa gedung lantai 3");
        page.locator("input[name='autoPost']").check();
        page.locator("input[name='postDay']").fill("15");
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/amortization/" + schedule.getId());
        assertThat(page.getByTestId("flash-success")).hasText("Jadwal amortisasi berhasil diperbarui");
        assertThat(page.getByTestId("amortization-detail")).containsText("Ya (tanggal 15)");
        assertThat(page.getByTestId("amortization-detail")).containsText("Sewa gedung lantai 3");

        AmortizationSchedule updated = schedule(schedule.getCode());
        assertThat(updated.getName()).isEqualTo(newName);
        assertThat(updated.getDescription()).isEqualTo("Sewa gedung lantai 3");
        assertThat(updated.getAutoPost()).isTrue();
        assertThat(updated.getPostDay()).isEqualTo(15);
        assertThat(updated.getTotalAmount()).isEqualByComparingTo("1200000");
        assertThat(updated.getStartDate()).isEqualTo(LocalDate.of(2045, 1, 1));
        assertThat(updated.getEndDate()).isEqualTo(LocalDate.of(2045, 12, 31));
        assertThat(entries(updated)).hasSize(12);
    }

    @Test
    @DisplayName("Edit is rejected once an entry is posted")
    void shouldRejectEditAfterPosting() {
        AmortizationSchedule schedule = createSchedule("Edit Terposting", ScheduleType.PREPAID_EXPENSE, PREPAID,
                RENT_EXPENSE, "1200000", LocalDate.of(2045, 1, 1), LocalDate.of(2045, 12, 31), false);
        entryService.postEntry(entries(schedule).getFirst().getId());

        navigateTo("/amortization/" + schedule.getId() + "/edit");
        waitForPageLoad();
        page.locator("input[name='name']").fill("Tidak Boleh Berubah");
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/amortization/" + schedule.getId());
        assertThat(page.getByTestId("flash-error")).hasText("Cannot update schedule with posted entries");
        assertThat(schedule(schedule.getCode()).getName()).isEqualTo(schedule.getName());
    }

    // ==================== entries ====================

    @Test
    @DisplayName("Posting one entry creates a posted journal (debit expense, credit prepaid) dated at period end")
    void shouldPostSingleEntryWithJournal() {
        AmortizationSchedule schedule = createSchedule("Posting", ScheduleType.PREPAID_EXPENSE, PREPAID, RENT_EXPENSE,
                "10000000", LocalDate.of(2045, 4, 1), LocalDate.of(2045, 6, 30), false);
        List<AmortizationEntry> entries = entries(schedule);

        navigateTo("/amortization/" + schedule.getId());
        waitForPageLoad();
        entryRow(entries.get(0)).locator("button:has-text('Posting')").click();

        assertThat(entryRow(entries.get(0))).containsText("Terposting");
        assertThat(entryRow(entries.get(0)).locator("button")).hasCount(0);
        assertThat(entryRow(entries.get(1))).containsText("Menunggu");
        assertThat(entryRow(entries.get(1)).locator("button:has-text('Posting')")).isVisible();

        AmortizationEntry posted = entryRepository.findById(entries.get(0).getId()).orElseThrow();
        assertThat(posted.getStatus()).isEqualTo(AmortizationEntryStatus.POSTED);
        assertThat(posted.getPostedAt()).isNotNull();
        assertThat(posted.getJournalNumber()).isNotBlank();
        assertThat(entryRepository.findById(entries.get(1).getId()).orElseThrow().getStatus())
                .isEqualTo(AmortizationEntryStatus.PENDING);

        Posting journal = posting(posted);
        assertThat(journal.status()).isEqualTo(TransactionStatus.POSTED);
        assertThat(journal.date()).isEqualTo(LocalDate.of(2045, 4, 30));
        assertThat(journal.reference()).isEqualTo(schedule.getCode() + "-1");
        assertThat(journal.description()).startsWith("Amortisasi Beban Dibayar Dimuka - " + schedule.getName());
        assertThat(journal.lines()).containsExactly(
                RENT_EXPENSE + " D3333333.33 C0.00",
                PREPAID + " D0.00 C3333333.33");

        AmortizationSchedule updated = schedule(schedule.getCode());
        assertThat(updated.getCompletedPeriods()).isEqualTo(1);
        assertThat(updated.getAmortizedAmount()).isEqualByComparingTo("3333333.33");
        assertThat(updated.getRemainingAmount()).isEqualByComparingTo("6666666.67");
        assertThat(updated.getStatus()).isEqualTo(ScheduleStatus.ACTIVE);

        Locator journalLink = entryRow(entries.get(0)).locator("a:has-text('Lihat')");
        assertThat(journalLink).hasAttribute("href", "/transactions/" + journal.transactionId());
        journalLink.click();
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/transactions/" + journal.transactionId());
        assertThat(page.locator("body")).containsText(schedule.getCode() + "-1");

        navigateTo("/amortization/" + schedule.getId());
        waitForPageLoad();
        assertThat(page.getByTestId("amortization-detail")).containsText("1/3");
        assertThat(page.getByTestId("amortization-detail")).containsText("Rp 3,333,333");
        assertThat(page.getByTestId("amortization-detail")).containsText("Rp 6,666,667");
    }

    @Test
    @DisplayName("Unearned revenue entry posts debit unearned revenue, credit revenue")
    void shouldPostUnearnedRevenueEntryInRevenueDirection() {
        AmortizationSchedule schedule = createSchedule("Pendapatan", ScheduleType.UNEARNED_REVENUE, UNEARNED,
                CONSULTING_REVENUE, "600000", LocalDate.of(2045, 1, 1), LocalDate.of(2045, 6, 30), false);
        List<AmortizationEntry> entries = entries(schedule);

        navigateTo("/amortization/" + schedule.getId());
        waitForPageLoad();
        entryRow(entries.get(0)).locator("button:has-text('Posting')").click();
        assertThat(entryRow(entries.get(0))).containsText("Terposting");

        Posting journal = posting(entryRepository.findById(entries.get(0).getId()).orElseThrow());
        assertThat(journal.description()).startsWith("Pengakuan Pendapatan Diterima Dimuka - " + schedule.getName());
        assertThat(journal.lines()).containsExactly(
                UNEARNED + " D100000.00 C0.00",
                CONSULTING_REVENUE + " D0.00 C100000.00");
    }

    @Test
    @DisplayName("Skipping an entry marks it skipped without a journal; post-all then posts only the remaining pending entries")
    void shouldSkipEntryThenPostAllRemaining() {
        AmortizationSchedule schedule = createSchedule("Lewati", ScheduleType.PREPAID_EXPENSE, PREPAID, RENT_EXPENSE,
                "10000000", LocalDate.of(2045, 7, 1), LocalDate.of(2045, 9, 30), false);
        List<AmortizationEntry> entries = entries(schedule);

        navigateTo("/amortization/" + schedule.getId());
        waitForPageLoad();
        entryRow(entries.get(0)).locator("button:has-text('Lewati')").click();

        assertThat(entryRow(entries.get(0))).containsText("Dilewati");
        assertThat(entryRow(entries.get(0)).locator("button")).hasCount(0);
        assertThat(entryRow(entries.get(0)).locator("a:has-text('Lihat')")).hasCount(0);
        AmortizationEntry skipped = entryRepository.findById(entries.get(0).getId()).orElseThrow();
        assertThat(skipped.getStatus()).isEqualTo(AmortizationEntryStatus.SKIPPED);
        assertThat(skipped.getJournalEntryId()).isNull();
        assertThat(schedule(schedule.getCode()).getAmortizedAmount()).isEqualByComparingTo("0");

        navigateTo("/amortization/" + schedule.getId());
        waitForPageLoad();
        page.locator("form[action='/amortization/" + schedule.getId() + "/entries/post-all'] button").click();
        waitForPageLoad();

        assertThat(page.getByTestId("flash-success")).hasText("Semua entry pending berhasil diposting");
        assertThat(page.locator("form[action$='/entries/post-all']")).hasCount(0);
        assertThat(entryRow(entries.get(0))).containsText("Dilewati");
        assertThat(entryRow(entries.get(1))).containsText("Terposting");
        assertThat(entryRow(entries.get(2))).containsText("Terposting");

        List<AmortizationEntry> after = entries(schedule);
        assertThat(after).extracting(AmortizationEntry::getStatus).containsExactly(
                AmortizationEntryStatus.SKIPPED, AmortizationEntryStatus.POSTED, AmortizationEntryStatus.POSTED);
        assertThat(posting(after.get(1)).lines()).containsExactly(
                RENT_EXPENSE + " D3333333.33 C0.00", PREPAID + " D0.00 C3333333.33");
        assertThat(posting(after.get(2)).lines()).containsExactly(
                RENT_EXPENSE + " D3333333.34 C0.00", PREPAID + " D0.00 C3333333.34");
        AmortizationSchedule updated = schedule(schedule.getCode());
        assertThat(updated.getCompletedPeriods()).isEqualTo(2);
        assertThat(updated.getAmortizedAmount()).isEqualByComparingTo("6666666.67");
        assertThat(updated.getRemainingAmount()).isEqualByComparingTo("3333333.33");
    }

    @Test
    @DisplayName("Post-all posts every period at its period end and completes the schedule at the full amount")
    void shouldPostAllEntriesAndCompleteSchedule() {
        AmortizationSchedule schedule = createSchedule("Semua", ScheduleType.PREPAID_EXPENSE, PREPAID, RENT_EXPENSE,
                "10000000", LocalDate.of(2045, 10, 1), LocalDate.of(2045, 12, 31), false);

        navigateTo("/amortization/" + schedule.getId());
        waitForPageLoad();
        page.locator("form[action='/amortization/" + schedule.getId() + "/entries/post-all'] button").click();
        waitForPageLoad();

        assertThat(page.getByTestId("flash-success")).hasText("Semua entry pending berhasil diposting");
        var detail = page.getByTestId("amortization-detail");
        assertThat(detail).containsText("Selesai");
        assertThat(detail).containsText("3/3");
        assertThat(detail.locator("a[href$='/edit']")).hasCount(0);
        assertThat(page.locator("#form-cancel")).hasCount(0);

        List<AmortizationEntry> entries = entries(schedule);
        assertThat(entries).extracting(AmortizationEntry::getStatus).containsOnly(AmortizationEntryStatus.POSTED);
        List<Posting> journals = entries.stream().map(this::posting).toList();
        assertThat(journals).extracting(Posting::date).containsExactly(
                LocalDate.of(2045, 10, 31), LocalDate.of(2045, 11, 30), LocalDate.of(2045, 12, 31));
        assertThat(journals).extracting(Posting::reference).containsExactly(
                schedule.getCode() + "-1", schedule.getCode() + "-2", schedule.getCode() + "-3");
        assertThat(journals).extracting(Posting::status).containsOnly(TransactionStatus.POSTED);
        assertThat(journals.get(2).lines()).containsExactly(
                RENT_EXPENSE + " D3333333.34 C0.00", PREPAID + " D0.00 C3333333.34");

        AmortizationSchedule completed = schedule(schedule.getCode());
        assertThat(completed.getStatus()).isEqualTo(ScheduleStatus.COMPLETED);
        assertThat(completed.getCompletedPeriods()).isEqualTo(3);
        assertThat(completed.getAmortizedAmount()).isEqualByComparingTo("10000000");
        assertThat(completed.getRemainingAmount()).isEqualByComparingTo("0");
    }

    // ==================== cancel / delete ====================

    @Test
    @DisplayName("Cancel sets the schedule to cancelled and removes all posting and edit controls")
    void shouldCancelSchedule() {
        AmortizationSchedule schedule = createSchedule("Batal", ScheduleType.PREPAID_EXPENSE, PREPAID, RENT_EXPENSE,
                "1200000", LocalDate.of(2045, 1, 1), LocalDate.of(2045, 12, 31), false);

        navigateTo("/amortization/" + schedule.getId());
        waitForPageLoad();
        page.onceDialog(dialog -> dialog.accept());
        page.locator("#form-cancel button").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/amortization/" + schedule.getId());
        assertThat(page.getByTestId("flash-success")).hasText("Jadwal amortisasi berhasil dibatalkan");
        var detail = page.getByTestId("amortization-detail");
        assertThat(detail).containsText("Dibatalkan");
        assertThat(page.locator("#form-cancel")).hasCount(0);
        assertThat(detail.locator("a[href$='/edit']")).hasCount(0);
        assertThat(page.locator("form[action$='/entries/post-all']")).hasCount(0);
        assertThat(page.locator("[data-testid='entry-table'] button")).hasCount(0);

        assertThat(schedule(schedule.getCode()).getStatus()).isEqualTo(ScheduleStatus.CANCELLED);
        assertThat(entries(schedule)).hasSize(12)
                .extracting(AmortizationEntry::getStatus).containsOnly(AmortizationEntryStatus.PENDING);
    }

    @Test
    @DisplayName("Delete removes a schedule without posted entries together with its entries")
    void shouldDeleteScheduleWithoutPostedEntries() {
        AmortizationSchedule schedule = createSchedule("Hapus", ScheduleType.PREPAID_EXPENSE, PREPAID, RENT_EXPENSE,
                "600000", LocalDate.of(2045, 1, 1), LocalDate.of(2045, 6, 30), false);

        navigateTo("/amortization/" + schedule.getId());
        waitForPageLoad();
        page.onceDialog(dialog -> dialog.accept());
        page.locator("#form-delete button").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/amortization");
        assertThat(page.getByTestId("flash-success")).hasText("Jadwal amortisasi berhasil dihapus");
        assertThat(scheduleRepository.findByCode(schedule.getCode())).isEmpty();
        assertThat(entryRepository.findByScheduleIdOrderByPeriodNumberAsc(schedule.getId())).isEmpty();
    }

    @Test
    @DisplayName("Delete is not offered and is rejected once an entry is posted")
    void shouldRejectDeleteAfterPosting() {
        AmortizationSchedule schedule = createSchedule("Hapus Terposting", ScheduleType.PREPAID_EXPENSE, PREPAID,
                RENT_EXPENSE, "600000", LocalDate.of(2045, 1, 1), LocalDate.of(2045, 6, 30), false);
        entryService.postEntry(entries(schedule).getFirst().getId());

        navigateTo("/amortization/" + schedule.getId());
        waitForPageLoad();
        assertThat(page.locator("#form-delete")).hasCount(0);

        APIResponse response = postForm("/amortization/" + schedule.getId() + "/delete");
        assertThat(response.url()).isEqualTo(baseUrl() + "/amortization/" + schedule.getId());
        assertThat(response.text()).contains("Cannot delete schedule with posted entries");
        assertThat(scheduleRepository.findByCode(schedule.getCode())).isPresent();
        assertThat(entries(schedule)).hasSize(6);
    }

    // ==================== list ====================

    @Test
    @DisplayName("List filters by status, type and search, alone and combined")
    void shouldFilterListByStatusTypeAndSearch() {
        String token = "FLT" + uid();
        AmortizationSchedule prepaid = createSchedule(token + " Sewa", ScheduleType.PREPAID_EXPENSE, PREPAID,
                RENT_EXPENSE, "1200000", LocalDate.of(2045, 1, 1), LocalDate.of(2045, 12, 31), false);
        AmortizationSchedule unearned = createSchedule(token + " Kontrak", ScheduleType.UNEARNED_REVENUE, UNEARNED,
                CONSULTING_REVENUE, "2400000", LocalDate.of(2045, 1, 1), LocalDate.of(2045, 12, 31), false);
        AmortizationSchedule cancelled = createSchedule(token + " Batal", ScheduleType.PREPAID_EXPENSE, PREPAID,
                RENT_EXPENSE, "600000", LocalDate.of(2045, 1, 1), LocalDate.of(2045, 6, 30), false);
        scheduleService.cancel(cancelled.getId());

        navigateTo("/amortization");
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Jadwal Amortisasi");
        page.getByTestId("search-input").fill(token);
        page.getByTestId("search-button").click();
        assertThat(scheduleRows()).hasCount(3);
        assertThat(page).hasURL(Pattern.compile(".*/amortization\\?search=" + token + "$"));

        Locator prepaidRow = scheduleRow(prepaid);
        assertThat(prepaidRow).containsText(prepaid.getCode());
        assertThat(prepaidRow).containsText(prepaid.getName());
        assertThat(prepaidRow).containsText("Beban Dibayar Dimuka");
        assertThat(prepaidRow).containsText("1,200,000");
        assertThat(prepaidRow).containsText("0/12");
        assertThat(prepaidRow).containsText("Aktif");
        assertThat(scheduleRow(cancelled)).containsText("Dibatalkan");
        takeManualScreenshot("amortization-list");

        assertListCodes("?status=CANCELLED&search=" + token, cancelled);
        assertListCodes("?status=ACTIVE&search=" + token, prepaid, unearned);
        assertListCodes("?type=UNEARNED_REVENUE&search=" + token, unearned);
        assertListCodes("?status=ACTIVE&type=PREPAID_EXPENSE&search=" + token, prepaid);
        assertListCodes("?status=COMPLETED&search=" + token);
        assertListCodes("?search=" + prepaid.getCode(), prepaid);

        navigateTo("/amortization?status=COMPLETED&search=" + token);
        waitForPageLoad();
        assertThat(page.getByTestId("schedule-table")).containsText("Tidak ada jadwal amortisasi");
    }

    @Test
    @DisplayName("List paginates by the requested page size and keeps it when moving to the next page")
    void shouldPaginateList() {
        String token = "PGN" + uid();
        AmortizationSchedule first = createSchedule(token + " A", ScheduleType.PREPAID_EXPENSE, PREPAID,
                RENT_EXPENSE, "600000", LocalDate.of(2045, 1, 1), LocalDate.of(2045, 6, 30), false);
        AmortizationSchedule second = createSchedule(token + " B", ScheduleType.PREPAID_EXPENSE, PREPAID,
                RENT_EXPENSE, "600000", LocalDate.of(2045, 1, 1), LocalDate.of(2045, 6, 30), false);
        AmortizationSchedule third = createSchedule(token + " C", ScheduleType.PREPAID_EXPENSE, PREPAID,
                RENT_EXPENSE, "600000", LocalDate.of(2045, 1, 1), LocalDate.of(2045, 6, 30), false);
        List<String> codes = java.util.stream.Stream.of(first, second, third)
                .map(AmortizationSchedule::getCode).sorted().toList();

        navigateTo("/amortization?search=" + token + "&size=2");
        waitForPageLoad();
        assertThat(scheduleRows()).hasCount(2);
        assertThat(scheduleRows().nth(0)).containsText(codes.get(0));
        assertThat(scheduleRows().nth(1)).containsText(codes.get(1));
        assertThat(page.getByTestId("amortization-list")).containsText("Menampilkan 1 - 2 dari 3 jadwal");

        page.locator("a:has-text('Selanjutnya')").click();
        assertThat(scheduleRows()).hasCount(1);
        assertThat(scheduleRows().nth(0)).containsText(codes.get(2));
        assertThat(page.getByTestId("amortization-list")).containsText("Menampilkan 3 - 3 dari 3 jadwal");
    }

    @Test
    @DisplayName("HTMX list request returns only the schedule table fragment")
    void shouldReturnTableFragmentForHtmxRequest() {
        AmortizationSchedule schedule = createSchedule("HTMX", ScheduleType.PREPAID_EXPENSE, PREPAID, RENT_EXPENSE,
                "600000", LocalDate.of(2045, 1, 1), LocalDate.of(2045, 6, 30), false);

        APIResponse response = page.request().get(baseUrl() + "/amortization?search=" + schedule.getCode(),
                RequestOptions.create().setHeader("HX-Request", "true"));

        assertThat(response.status()).isEqualTo(200);
        String body = response.text();
        assertThat(body).contains("id=\"schedule-table\"");
        assertThat(body).contains(schedule.getCode());
        assertThat(body).doesNotContain("<html");
        assertThat(body).doesNotContain("page-title");
    }

    @Test
    @DisplayName("Batch process posts due entries of auto-post schedules only")
    void shouldBatchPostDueAutoPostEntries() {
        AmortizationSchedule due = createSchedule("Batch Jatuh Tempo", ScheduleType.PREPAID_EXPENSE, PREPAID,
                RENT_EXPENSE, "5000000", LocalDate.of(2002, 1, 1), LocalDate.of(2002, 2, 28), true);
        AmortizationSchedule manual = createSchedule("Batch Manual", ScheduleType.PREPAID_EXPENSE, PREPAID,
                RENT_EXPENSE, "5000000", LocalDate.of(2002, 1, 1), LocalDate.of(2002, 2, 28), false);
        AmortizationSchedule future = createSchedule("Batch Mendatang", ScheduleType.PREPAID_EXPENSE, PREPAID,
                RENT_EXPENSE, "5000000", LocalDate.of(2045, 1, 1), LocalDate.of(2045, 2, 28), true);

        navigateTo("/amortization");
        waitForPageLoad();
        page.locator("form[action='/amortization/batch/process'] button").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/amortization");
        String flash = page.getByTestId("flash-success").textContent().trim();
        Matcher matcher = Pattern.compile("Batch selesai: (\\d+) entry berhasil diproses").matcher(flash);
        assertThat(matcher.matches()).as(flash).isTrue();
        assertThat(Integer.parseInt(matcher.group(1))).isGreaterThanOrEqualTo(2);

        List<AmortizationEntry> dueEntries = entries(due);
        assertThat(dueEntries).extracting(AmortizationEntry::getStatus).containsOnly(AmortizationEntryStatus.POSTED);
        assertThat(dueEntries.stream().map(this::posting).map(Posting::date).toList()).containsExactly(
                LocalDate.of(2002, 1, 31), LocalDate.of(2002, 2, 28));
        assertThat(schedule(due.getCode()).getStatus()).isEqualTo(ScheduleStatus.COMPLETED);
        assertThat(schedule(due.getCode()).getAmortizedAmount()).isEqualByComparingTo("5000000");

        assertThat(entries(manual)).extracting(AmortizationEntry::getStatus)
                .containsOnly(AmortizationEntryStatus.PENDING);
        assertThat(entries(future)).extracting(AmortizationEntry::getStatus)
                .containsOnly(AmortizationEntryStatus.PENDING);
    }

    // ==================== helpers ====================

    private record Posting(UUID transactionId, LocalDate date, String reference, String description,
                           TransactionStatus status, List<String> lines) {
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
            return new Posting(tx.getId(), tx.getTransactionDate(), tx.getReferenceNumber(), tx.getDescription(),
                    tx.getStatus(), lines);
        });
    }

    private AmortizationSchedule createSchedule(String name, ScheduleType type, String source, String target,
                                                String amount, LocalDate start, LocalDate end, boolean autoPost) {
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
        schedule.setAutoPost(autoPost);
        schedule.setPostDay(1);
        return scheduleService.create(schedule);
    }

    private void fillCreateForm(String code, String name, ScheduleType type, String source, String target,
                                String amount, LocalDate start, LocalDate end, AmortizationFrequency frequency) {
        navigateTo("/amortization/new");
        waitForPageLoad();
        page.locator("input[name='code']").fill(code);
        page.locator("input[name='name']").fill(name);
        page.locator("select[name='scheduleType']").selectOption(type.name());
        page.locator("select[name='sourceAccountId']").selectOption(account(source).getId().toString());
        page.locator("select[name='targetAccountId']").selectOption(account(target).getId().toString());
        page.locator("input[name='totalAmount']").fill(amount);
        page.locator("input[name='startDate']").fill(start.toString());
        page.locator("input[name='endDate']").fill(end.toString());
        page.locator("select[name='frequency']").selectOption(frequency.name());
    }

    private APIResponse postForm(String path) {
        String csrf = page.locator("meta[name='_csrf']").getAttribute("content");
        return page.request().post(baseUrl() + path,
                RequestOptions.create().setForm(FormData.create().set("_csrf", csrf)));
    }

    private void assertListCodes(String query, AmortizationSchedule... expected) {
        navigateTo("/amortization" + query);
        waitForPageLoad();
        assertThat(scheduleRows()).hasCount(expected.length);
        for (AmortizationSchedule schedule : expected) {
            assertThat(scheduleRow(schedule)).containsText(schedule.getCode());
        }
    }

    private Locator scheduleRows() {
        return page.locator("[data-testid='schedule-table'] tbody tr[data-id]");
    }

    private Locator scheduleRow(AmortizationSchedule schedule) {
        return page.locator("[data-testid='schedule-table'] tbody tr[data-id='" + schedule.getId() + "']");
    }

    private Locator entryRows() {
        return page.locator("[data-testid='entry-table'] tbody tr[data-id]");
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
        return coaRepository.findByAccountCode(code)
                .orElseThrow(() -> new IllegalStateException("Seeded account missing: " + code));
    }

    private static String code() {
        return "AMT45-" + uid();
    }

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
