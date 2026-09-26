package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.FiscalAdjustment;
import com.artivisi.accountingfinance.entity.FiscalLossCarryforward;
import com.artivisi.accountingfinance.entity.FiscalPeriod;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.entity.AssetStatus;
import com.artivisi.accountingfinance.enums.FiscalAdjustmentCategory;
import com.artivisi.accountingfinance.enums.FiscalAdjustmentDirection;
import com.artivisi.accountingfinance.enums.FiscalPeriodStatus;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.functional.taxdetail.TaxDetailTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.FiscalAdjustmentRepository;
import com.artivisi.accountingfinance.repository.FiscalLossCarryforwardRepository;
import com.artivisi.accountingfinance.repository.FiscalPeriodRepository;
import com.artivisi.accountingfinance.repository.FixedAssetRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * SPT Tahunan Badan checklist page on data this class creates for {@link #YEAR} (reserved for it):
 *
 * - 02-10 Pendapatan Jasa + PPN: Dr 1.1.02 55.5M / Cr 4.1.01 50M / Cr 2.1.03 5.5M
 * - 05-10 Pembayaran Jasa dengan PPh 23: Dr 5.1.20 20M / Cr 1.1.02 19.6M / Cr 2.1.21 0.4M
 * - fiscal adjustments: +2M permanent, (1M) temporary -> PKP 31M, PPh Badan 3,410,000 (Pasal 31E)
 * - fiscal periods: January-October closed/filed, November-December open
 * - fiscal loss from {@link #LOSS_YEAR} of 7.5M, compensable from the following year
 * {@link #LOSS_YEAR} itself has no journals, adjustments or periods in this context.
 */
@DisplayName("SPT Tahunan Badan Checklist")
@Import(TaxDetailTestDataInitializer.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SptChecklistFunctionalTest extends PlaywrightTestBase {

    private static final int YEAR = 2095;
    private static final int LOSS_YEAR = 2094;

    @Autowired private TransactionRepository transactionRepository;
    @Autowired private JournalEntryRepository journalEntryRepository;
    @Autowired private JournalTemplateRepository templateRepository;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private FiscalAdjustmentRepository fiscalAdjustmentRepository;
    @Autowired private FiscalPeriodRepository fiscalPeriodRepository;
    @Autowired private FiscalLossCarryforwardRepository lossRepository;
    @Autowired private FixedAssetRepository fixedAssetRepository;

    private UUID lossId;

    @BeforeAll
    void createYearData() {
        for (int year : List.of(YEAR, LOSS_YEAR)) {
            assertThat(transactionRepository.findPostedTransactionsBetweenDates(
                    LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)))
                    .as("Another test posted into reserved SPT year " + year).isEmpty();
            assertThat(fiscalAdjustmentRepository.findByYearOrderByAdjustmentCategoryAscDescriptionAsc(year))
                    .as("Another test added fiscal adjustments to reserved SPT year " + year).isEmpty();
            assertThat(fiscalPeriodRepository.findByYear(year))
                    .as("Another test created fiscal periods in reserved SPT year " + year).isEmpty();
        }
        assertThat(lossRepository.findAllByOrderByOriginYearDesc())
                .as("Another test left a fiscal loss that is compensable in " + YEAR)
                .noneMatch(l -> l.getOriginYear() < YEAR && l.getExpiryYear() >= YEAR);

        post(LocalDate.of(YEAR, 2, 10), "Pendapatan Jasa + PPN",
                line("1.1.02", "55500000", "0"), line("4.1.01", "0", "50000000"), line("2.1.03", "0", "5500000"));
        post(LocalDate.of(YEAR, 5, 10), "Pembayaran Jasa dengan PPh 23",
                line("5.1.20", "20000000", "0"), line("1.1.02", "0", "19600000"), line("2.1.21", "0", "400000"));

        adjustment("Beban sumbangan", FiscalAdjustmentCategory.PERMANENT, FiscalAdjustmentDirection.POSITIVE, "2000000");
        adjustment("Selisih penyusutan", FiscalAdjustmentCategory.TEMPORARY, FiscalAdjustmentDirection.NEGATIVE, "1000000");

        for (int month = 1; month <= 12; month++) {
            FiscalPeriod period = new FiscalPeriod();
            period.setYear(YEAR);
            period.setMonth(month);
            if (month <= 10) {
                period.setStatus(month <= 6 ? FiscalPeriodStatus.TAX_FILED : FiscalPeriodStatus.MONTH_CLOSED);
                period.setMonthClosedAt(LocalDateTime.now());
                period.setMonthClosedBy("admin");
                if (month <= 6) {
                    period.setTaxFiledAt(LocalDateTime.now());
                    period.setTaxFiledBy("admin");
                }
            }
            fiscalPeriodRepository.save(period);
        }

        FiscalLossCarryforward loss = new FiscalLossCarryforward();
        loss.setOriginYear(LOSS_YEAR);
        loss.setOriginalAmount(new BigDecimal("7500000"));
        loss.setNotes("Rugi fiskal " + LOSS_YEAR);
        lossId = lossRepository.save(loss).getId();
    }

    @AfterAll
    void removeLoss() {
        lossRepository.deleteById(lossId);
    }

    @BeforeEach
    void login() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("Without a year the checklist shows the previous year, selectable among the last three")
    void shouldDefaultToPreviousYear() {
        navigateTo("/reports/spt-checklist");
        waitForPageLoad();

        int current = Year.now().getValue();
        assertThat(page.locator("#page-title")).hasText("Checklist SPT Tahunan Badan");
        assertThat(page.locator("#readiness-heading")).hasText("Kesiapan Data SPT Tahunan " + (current - 1));
        assertThat(page.locator("#year option").evaluateAll("os => os.map(o => o.value)"))
                .isEqualTo(List.of(String.valueOf(current - 1), String.valueOf(current - 2), String.valueOf(current - 3)));
        assertThat(page.locator("#year")).hasValue(String.valueOf(current - 1));
    }

    @Test
    @DisplayName("Checklist of a year reports revenue, net income, adjustments, PPh Badan, closed periods, assets, payroll and compensable losses")
    void shouldReportYearReadiness() {
        navigateTo("/reports/spt-checklist?year=" + YEAR);
        waitForPageLoad();

        assertThat(page.locator("#year")).hasValue(String.valueOf(YEAR));
        assertThat(page.locator("#readiness-heading")).hasText("Kesiapan Data SPT Tahunan " + YEAR);
        assertItem("#check-financial-statements", "bg-green-50", "Laporan Keuangan",
                "Pendapatan: Rp 50.000.000 — Laba Bersih: Rp 30.000.000");
        assertItem("#check-fiscal-adjustments", "bg-green-50", "Koreksi Fiskal", "2 item koreksi fiskal");
        assertItem("#check-pph-badan", "bg-green-50", "PPh Badan Terutang",
                "PPh Terutang: Rp 3.410.000 — PPh 29: Rp 3.410.000");
        assertItem("#check-fiscal-periods", "bg-yellow-50", "Periode Fiskal", "10/12 bulan ditutup");
        assertItem("#check-payroll", "bg-gray-50", "Payroll & PPh 21", "Tidak ada data payroll");
        assertItem("#check-loss-carryforward", "bg-blue-50", "Kompensasi Kerugian", "Sisa rugi fiskal: Rp 7.500.000");

        LocalDate yearEnd = LocalDate.of(YEAR, 12, 31);
        long assets = fixedAssetRepository.findAll().stream()
                .filter(a -> a.getStatus() != AssetStatus.DISPOSED
                        || (a.getDisposalDate() != null && a.getDisposalDate().getYear() == YEAR))
                .filter(a -> !a.getPurchaseDate().isAfter(yearEnd))
                .count();
        assertItem("#check-depreciation", assets > 0 ? "bg-green-50" : "bg-gray-50", "Penyusutan Aset Tetap",
                assets > 0 ? assets + " aset tercatat" : "Tidak ada aset tetap");

        takeManualScreenshot("spt-checklist/spt-checklist-page");
        page.locator("text=Download Lampiran SPT").scrollIntoViewIfNeeded();
        takeManualScreenshot("spt-checklist/spt-checklist-downloads");
    }

    @Test
    @DisplayName("A year without activity shows every item as missing, and a loss is not compensable in its own year")
    void shouldReportMissingDataForEmptyYear() {
        navigateTo("/reports/spt-checklist?year=" + LOSS_YEAR);
        waitForPageLoad();

        assertThat(page.locator("#readiness-heading")).hasText("Kesiapan Data SPT Tahunan " + LOSS_YEAR);
        assertItem("#check-financial-statements", "bg-yellow-50", "Laporan Keuangan", "Belum ada transaksi di tahun ini");
        assertItem("#check-fiscal-adjustments", "bg-yellow-50", "Koreksi Fiskal", "Belum ada koreksi fiskal");
        assertItem("#check-pph-badan", "bg-green-50", "PPh Badan Terutang", "PPh Terutang: Rp 0 — PPh 29: Rp 0");
        assertItem("#check-fiscal-periods", "bg-yellow-50", "Periode Fiskal", "0/12 bulan ditutup");
        assertItem("#check-payroll", "bg-gray-50", "Payroll & PPh 21", "Tidak ada data payroll");
        assertItem("#check-loss-carryforward", "bg-gray-50", "Kompensasi Kerugian",
                "Tidak ada rugi fiskal yang dapat dikompensasi");
    }

    @Test
    @DisplayName("Choosing another year in the form reloads the checklist for that year")
    void shouldChangeYearThroughForm() {
        navigateTo("/reports/spt-checklist?year=" + YEAR);
        waitForPageLoad();

        String twoYearsAgo = String.valueOf(Year.now().getValue() - 2);
        page.locator("#year").selectOption(twoYearsAgo);
        page.locator("button:has-text('Tampilkan')").click();
        page.waitForURL(url -> url.endsWith("/reports/spt-checklist?year=" + twoYearsAgo));

        assertThat(page.locator("#readiness-heading")).hasText("Kesiapan Data SPT Tahunan " + twoYearsAgo);
        assertThat(page.locator("#year")).hasValue(twoYearsAgo);
        assertThat(page.locator("#year option[value='" + YEAR + "']")).hasCount(0);
    }

    @Test
    @DisplayName("Sidebar entry under Laporan opens the checklist")
    void shouldNavigateFromSidebar() {
        navigateTo("/dashboard");
        waitForPageLoad();
        page.locator("#nav-group-laporan").click();
        page.locator("#nav-spt-checklist").click();
        page.waitForURL(url -> url.endsWith("/reports/spt-checklist"));

        assertThat(page.locator("#page-title")).hasText("Checklist SPT Tahunan Badan");
        assertThat(page.locator("#nav-spt-checklist")).hasClass(Pattern.compile(".*bg-primary-600.*"));
    }

    private void assertItem(String selector, String background, String title, String detail) {
        Locator item = page.locator(selector);
        assertThat(item).hasClass(Pattern.compile(".*\\b" + background + "\\b.*"));
        assertThat(item.locator("p.font-medium")).hasText(title);
        assertThat(item.locator("p.text-sm")).hasText(detail);
    }

    private record Line(String accountCode, BigDecimal debit, BigDecimal credit) {}

    private static Line line(String accountCode, String debit, String credit) {
        return new Line(accountCode, new BigDecimal(debit), new BigDecimal(credit));
    }

    private void post(LocalDate date, String templateName, Line... lines) {
        String number = "SPT-" + UUID.randomUUID().toString().substring(0, 8);
        Transaction tx = new Transaction();
        tx.setTransactionNumber(number);
        tx.setTransactionDate(date);
        tx.setDescription(templateName + " " + number);
        tx.setAmount(lines[0].debit());
        tx.setJournalTemplate(templateRepository.findByTemplateNameAndIsCurrentVersionTrue(templateName)
                .orElseThrow(() -> new IllegalStateException("Seeded template missing: " + templateName)));
        tx.setStatus(TransactionStatus.POSTED);
        tx.setPostedAt(LocalDateTime.now());
        tx.setPostedBy("admin");
        tx.setCreatedBy("admin");
        Transaction saved = transactionRepository.save(tx);

        int index = 0;
        for (Line l : lines) {
            JournalEntry entry = new JournalEntry();
            entry.setJournalNumber(number + "-" + String.format("%02d", ++index));
            entry.setTransaction(saved);
            entry.setAccount(account(l.accountCode()));
            entry.setDebitAmount(l.debit());
            entry.setCreditAmount(l.credit());
            entry.setPostedAt(saved.getPostedAt());
            entry.setCreatedBy("admin");
            journalEntryRepository.save(entry);
        }
    }

    private void adjustment(String description, FiscalAdjustmentCategory category,
                            FiscalAdjustmentDirection direction, String amount) {
        FiscalAdjustment adjustment = new FiscalAdjustment();
        adjustment.setYear(YEAR);
        adjustment.setDescription(description);
        adjustment.setAdjustmentCategory(category);
        adjustment.setAdjustmentDirection(direction);
        adjustment.setAmount(new BigDecimal(amount));
        fiscalAdjustmentRepository.save(adjustment);
    }

    private ChartOfAccount account(String code) {
        return accountRepository.findByAccountCode(code)
                .orElseThrow(() -> new IllegalStateException("Seeded account missing: " + code));
    }
}
