package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.FiscalPeriod;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.FiscalPeriodStatus;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.FiscalPeriodRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.service.FiscalPeriodService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fiscal period list, creation and the OPEN -> MONTH_CLOSED -> TAX_FILED workflow.
 * Periods are created in years reserved for this class: {@link #GENERATED_YEAR} (generate-year),
 * {@link #YEAR} (single periods, one month per test) and {@link #FILTER_YEAR} (list filters).
 * Blocking of postings into a closed period is verified by TransactionPersistenceTest.
 */
@DisplayName("Fiscal Period Controller Tests")
@Import(ServiceTestDataInitializer.class)
class FiscalPeriodControllerFunctionalTest extends PlaywrightTestBase {

    private static final int GENERATED_YEAR = 2069;
    private static final int YEAR = 2070;
    private static final int FILTER_YEAR = 2071;

    @Autowired private FiscalPeriodRepository fiscalPeriodRepository;
    @Autowired private FiscalPeriodService fiscalPeriodService;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private JournalTemplateRepository templateRepository;

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("Generate year creates twelve open periods; generating again reports they already exist")
    void shouldGenerateYearPeriods() {
        navigateTo("/fiscal-periods");
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Periode Fiskal");

        page.locator("#generate-year-input").fill(String.valueOf(GENERATED_YEAR));
        page.locator("#btn-generate-year").click();
        page.waitForURL("**/fiscal-periods?year=" + GENERATED_YEAR);

        assertThat(page.locator("[data-testid='flash-success']"))
                .hasText("12 periode fiskal berhasil ditambahkan untuk tahun " + GENERATED_YEAR);
        List<FiscalPeriod> periods = fiscalPeriodRepository.findByYear(GENERATED_YEAR);
        assertThat(periods).hasSize(12);
        assertThat(periods).extracting(FiscalPeriod::getMonth).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
        assertThat(periods).allSatisfy(p -> assertThat(p.getStatus()).isEqualTo(FiscalPeriodStatus.OPEN));
        assertThat(periodRow(GENERATED_YEAR + "-02")).containsText("Februari " + GENERATED_YEAR);
        assertThat(periodRow(GENERATED_YEAR + "-02")).containsText("Terbuka");

        page.locator("#generate-year-input").fill(String.valueOf(GENERATED_YEAR));
        page.locator("#btn-generate-year").click();
        page.waitForURL("**/fiscal-periods?year=" + GENERATED_YEAR);
        assertThat(page.locator("[data-testid='flash-info']")).hasText("Semua periode untuk tahun " + GENERATED_YEAR + " sudah ada");
        assertThat(page.locator("[data-testid='flash-success']")).hasCount(0);
        assertThat(fiscalPeriodRepository.findByYear(GENERATED_YEAR)).hasSize(12);
    }

    @Test
    @DisplayName("New period form creates an open period and shows its dates; a duplicate is rejected on the form")
    void shouldCreatePeriodAndRejectDuplicate() {
        navigateTo("/fiscal-periods");
        waitForPageLoad();
        page.locator("#btn-new-period").click();
        page.waitForURL("**/fiscal-periods/new");
        assertThat(page.locator("#page-title")).hasText("Periode Fiskal Baru");
        assertThat(page.locator("#year")).hasValue(String.valueOf(LocalDate.now().getYear()));
        assertThat(page.locator("#month option")).hasCount(12);

        page.locator("#year").fill(String.valueOf(YEAR));
        page.locator("#month").selectOption("3");
        page.locator("#btn-simpan").click();
        page.waitForURL(url -> url.matches(".*/fiscal-periods/[0-9a-f-]{36}$"));

        FiscalPeriod created = period(YEAR, 3);
        assertThat(page.url()).endsWith("/fiscal-periods/" + created.getId());
        assertThat(created.getStatus()).isEqualTo(FiscalPeriodStatus.OPEN);
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Periode fiskal berhasil ditambahkan");
        assertThat(page.locator("[data-testid='period-name']")).hasText("Maret " + YEAR);
        assertThat(page.locator("[data-testid='period-code']")).hasText(YEAR + "-03");
        assertThat(page.locator("[data-testid='period-status']")).hasText("Terbuka");
        assertThat(page.locator("[data-testid='period-detail']")).containsText("01 Maret " + YEAR);
        assertThat(page.locator("[data-testid='period-detail']")).containsText("31 Maret " + YEAR);
        assertThat(page.locator("[data-testid='btn-close-month']")).isVisible();
        assertThat(page.locator("[data-testid='btn-file-tax']")).hasCount(0);
        assertThat(page.locator("[data-testid='btn-reopen']")).hasCount(0);

        navigateTo("/fiscal-periods/new");
        waitForPageLoad();
        page.locator("#year").fill(String.valueOf(YEAR));
        page.locator("#month").selectOption("3");
        page.locator("#btn-simpan").click();
        page.waitForURL("**/fiscal-periods/new");
        assertThat(page.locator("[data-testid='flash-error']")).hasText("Periode fiskal " + YEAR + "-03 sudah ada");
        assertThat(fiscalPeriodRepository.findByYear(YEAR)).filteredOn(p -> p.getMonth() == 3).hasSize(1);
    }

    @Test
    @DisplayName("Close month, reopen, close again and file tax move the period through its statuses with user and time")
    void shouldRunPeriodWorkflow() {
        FiscalPeriod period = fiscalPeriodService.create(YEAR, 4);
        navigateTo("/fiscal-periods/" + period.getId());
        waitForPageLoad();

        acceptNextDialog("Tutup bulan untuk periode ini? Jurnal tidak bisa diposting ke periode ini setelah ditutup.");
        page.locator("[data-testid='btn-close-month']").click();
        page.waitForURL("**/fiscal-periods/" + period.getId());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Bulan berhasil ditutup");
        assertThat(page.locator("[data-testid='period-status']")).hasText("Tutup Bulan");
        assertThat(page.locator("[data-testid='period-detail']")).containsText("oleh admin");
        assertThat(page.locator("[data-testid='btn-close-month']")).hasCount(0);
        assertThat(page.locator("[data-testid='btn-file-tax']")).isVisible();
        assertThat(page.locator("[data-testid='btn-reopen']")).isVisible();
        FiscalPeriod closed = period(YEAR, 4);
        assertThat(closed.getStatus()).isEqualTo(FiscalPeriodStatus.MONTH_CLOSED);
        assertThat(closed.getMonthClosedBy()).isEqualTo("admin");
        assertThat(closed.getMonthClosedAt()).isNotNull();
        assertThat(fiscalPeriodService.isPeriodOpenForPosting(LocalDate.of(YEAR, 4, 15))).isFalse();

        acceptNextDialog("Buka kembali periode ini?");
        page.locator("[data-testid='btn-reopen']").click();
        page.waitForURL("**/fiscal-periods/" + period.getId());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Periode berhasil dibuka kembali");
        assertThat(page.locator("[data-testid='period-status']")).hasText("Terbuka");
        FiscalPeriod reopened = period(YEAR, 4);
        assertThat(reopened.getStatus()).isEqualTo(FiscalPeriodStatus.OPEN);
        assertThat(reopened.getMonthClosedAt()).isNull();
        assertThat(reopened.getMonthClosedBy()).isNull();
        assertThat(fiscalPeriodService.isPeriodOpenForPosting(LocalDate.of(YEAR, 4, 15))).isTrue();

        acceptNextDialog("Tutup bulan untuk periode ini? Jurnal tidak bisa diposting ke periode ini setelah ditutup.");
        page.locator("[data-testid='btn-close-month']").click();
        page.waitForURL("**/fiscal-periods/" + period.getId());
        acceptNextDialog("Tandai SPT sudah dilaporkan? Periode tidak bisa dibuka kembali setelah ini.");
        page.locator("[data-testid='btn-file-tax']").click();
        page.waitForURL("**/fiscal-periods/" + period.getId());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("SPT berhasil dilaporkan");
        assertThat(page.locator("[data-testid='period-status']")).hasText("SPT Dilaporkan");
        assertThat(page.locator("[data-testid='btn-close-month']")).hasCount(0);
        assertThat(page.locator("[data-testid='btn-file-tax']")).hasCount(0);
        assertThat(page.locator("[data-testid='btn-reopen']")).hasCount(0);
        FiscalPeriod filed = period(YEAR, 4);
        assertThat(filed.getStatus()).isEqualTo(FiscalPeriodStatus.TAX_FILED);
        assertThat(filed.getTaxFiledBy()).isEqualTo("admin");
        assertThat(filed.getTaxFiledAt()).isNotNull();
    }

    @Test
    @DisplayName("Closing a month that still has a draft transaction is refused and the period stays open")
    void shouldRefuseClosingMonthWithDraft() {
        FiscalPeriod period = fiscalPeriodService.create(YEAR, 5);
        Transaction draft = new Transaction();
        draft.setTransactionNumber("FPD-" + UUID.randomUUID().toString().substring(0, 8));
        draft.setTransactionDate(LocalDate.of(YEAR, 5, 10));
        draft.setDescription("Draft yang belum diposting");
        draft.setAmount(new BigDecimal("1000000"));
        draft.setJournalTemplate(templateRepository.findByTemplateNameAndIsCurrentVersionTrue("Pendapatan Jasa Training")
                .orElseThrow(() -> new IllegalStateException("Seeded template missing: Pendapatan Jasa Training")));
        draft.setStatus(TransactionStatus.DRAFT);
        draft.setCreatedBy("admin");
        transactionRepository.save(draft);

        navigateTo("/fiscal-periods/" + period.getId());
        waitForPageLoad();
        acceptNextDialog("Tutup bulan untuk periode ini? Jurnal tidak bisa diposting ke periode ini setelah ditutup.");
        page.locator("[data-testid='btn-close-month']").click();
        page.waitForURL("**/fiscal-periods/" + period.getId());

        assertThat(page.locator("[data-testid='flash-error']")).hasText(
                "Tidak dapat menutup periode Mei " + YEAR + ". Masih ada 1 transaksi draft yang belum diposting.");
        assertThat(page.locator("[data-testid='period-status']")).hasText("Terbuka");
        assertThat(period(YEAR, 5).getStatus()).isEqualTo(FiscalPeriodStatus.OPEN);
        assertThat(period(YEAR, 5).getMonthClosedAt()).isNull();
    }

    @Test
    @DisplayName("Year and status filters reload the table with only the matching periods")
    void shouldFilterByYearAndStatus() {
        for (int month = 1; month <= 3; month++) {
            fiscalPeriodService.create(FILTER_YEAR, month);
        }
        FiscalPeriod february = period(FILTER_YEAR, 2);
        february.setStatus(FiscalPeriodStatus.MONTH_CLOSED);
        february.setMonthClosedAt(LocalDateTime.now());
        february.setMonthClosedBy("admin");
        fiscalPeriodRepository.save(february);

        navigateTo("/fiscal-periods");
        waitForPageLoad();
        page.locator("#filter-year").selectOption(String.valueOf(FILTER_YEAR));
        assertThat(page.locator("[data-testid='period-table'] tbody tr")).hasCount(3);
        assertThat(periodRow(FILTER_YEAR + "-01")).containsText("Terbuka");
        assertThat(periodRow(FILTER_YEAR + "-02")).containsText("Tutup Bulan");
        assertThat(periodRow(FILTER_YEAR + "-03")).containsText("Terbuka");

        page.locator("#filter-status").selectOption("MONTH_CLOSED");
        assertThat(page.locator("[data-testid='period-table'] tbody tr")).hasCount(1);
        assertThat(periodRow(FILTER_YEAR + "-02")).containsText("Februari " + FILTER_YEAR);

        page.locator("#filter-status").selectOption("TAX_FILED");
        assertThat(page.locator("[data-testid='period-table'] tbody tr")).hasCount(1);
        assertThat(page.locator("[data-testid='period-table']")).containsText("Belum ada periode fiskal");

        navigateTo("/fiscal-periods?year=" + FILTER_YEAR + "&status=OPEN");
        waitForPageLoad();
        assertThat(page.locator("#filter-year")).hasValue(String.valueOf(FILTER_YEAR));
        assertThat(page.locator("#filter-status")).hasValue("OPEN");
        assertThat(page.locator("[data-testid='period-table'] tbody tr")).hasCount(2);
        periodRow(FILTER_YEAR + "-03").locator("a").first().click();
        page.waitForURL("**/fiscal-periods/" + period(FILTER_YEAR, 3).getId());
        assertThat(page.locator("[data-testid='period-name']")).hasText("Maret " + FILTER_YEAR);
    }

    @Test
    @DisplayName("Master Data sidebar group links to the fiscal period list")
    void shouldShowSidebarLinkForAdmin() {
        navigateTo("/dashboard");
        waitForPageLoad();

        page.locator("#nav-group-master").click();
        Locator sidebarLink = page.locator("#nav-fiscal-periods");
        assertThat(sidebarLink).hasText("Periode Fiskal");
        sidebarLink.click();
        page.waitForURL("**/fiscal-periods");
        assertThat(page.locator("#page-title")).hasText("Periode Fiskal");
    }

    private void acceptNextDialog(String expectedMessage) {
        page.onceDialog(dialog -> {
            assertThat(dialog.message()).isEqualTo(expectedMessage);
            dialog.accept();
        });
    }

    private Locator periodRow(String periodName) {
        return page.locator("[data-testid='period-row-" + periodName + "']");
    }

    private FiscalPeriod period(int year, int month) {
        return fiscalPeriodRepository.findByYearAndMonth(year, month)
                .orElseThrow(() -> new AssertionError("Fiscal period missing: " + year + "-" + month));
    }
}
