package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.dto.JournalEntryRequest;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.JournalTemplateLine;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.service.FiscalPeriodService;
import com.artivisi.accountingfinance.service.TransactionApiService;
import com.artivisi.accountingfinance.service.TransactionService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Period report (/reports/period): income statement for a date range without closing entries,
 * plus the balance sheet at the range end.
 *
 * Posts its own transactions into {@link #YEAR} (reserved for this class; fiscal periods stop at 2100) and opens one fiscal
 * period there so the year's quick presets appear:
 * - 01-10 Pendapatan Jasa Training 40,000,000 (4.1.01)
 * - 01-20 Bayar Beban Listrik 3,000,000 (5.1.07)
 * - 02-15 Bayar Beban Sewa 5,000,000 (5.1.05)
 * - 04-05 Pendapatan Jasa Training 10,000,000 (4.1.01)
 * - 12-31 closing journal 4.1.01 D / 3.2.02 C 50,000,000 (must not reduce revenue)
 */
@DisplayName("Period Report Tests")
@Import(ServiceTestDataInitializer.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PeriodReportTest extends PlaywrightTestBase {

    private static final int YEAR = 2063;

    @Autowired private TransactionRepository transactionRepository;
    @Autowired private TransactionService transactionService;
    @Autowired private TransactionApiService transactionApiService;
    @Autowired private JournalTemplateRepository templateRepository;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private FiscalPeriodService fiscalPeriodService;

    @BeforeAll
    void postPeriodTransactions() {
        assertThat(transactionRepository.findByTransactionDateBetweenOrderByTransactionDateDesc(
                LocalDate.of(YEAR, 1, 1), LocalDate.of(YEAR, 12, 31)))
                .as("Another test posted into reserved period report year " + YEAR).isEmpty();
        fiscalPeriodService.getOrCreate(YEAR, 1);

        post("Pendapatan Jasa Training", LocalDate.of(YEAR, 1, 10), "40000000");
        post("Bayar Beban Listrik", LocalDate.of(YEAR, 1, 20), "3000000");
        post("Bayar Beban Sewa", LocalDate.of(YEAR, 2, 15), "5000000");
        post("Pendapatan Jasa Training", LocalDate.of(YEAR, 4, 5), "10000000");

        UUID closing = transactionApiService.createJournalEntry(new JournalEntryRequest(
                LocalDate.of(YEAR, 12, 31), "Jurnal penutup " + YEAR, "CLOSING", List.of(
                        new JournalEntryRequest.JournalLineRequest(account("4.1.01"), new BigDecimal("50000000"), BigDecimal.ZERO),
                        new JournalEntryRequest.JournalLineRequest(account("3.2.02"), BigDecimal.ZERO, new BigDecimal("50000000")))),
                "admin").transactionId();
        transactionService.post(closing, "admin");
    }

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("Without a period the page offers the year's presets and asks for a period")
    void shouldDisplayPeriodSelector() {
        navigateTo("/reports/period");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Laporan Periode");
        assertThat(yearRow().locator("button.period-preset")).hasText(new String[] {"Tahun", "Q1", "Q2", "Q3", "Q4"});
        assertThat(page.locator("#startDate")).hasValue("");
        assertThat(page.locator("#endDate")).hasValue("");
        assertThat(page.getByText("Pilih periode untuk menampilkan laporan keuangan")).isVisible();
        assertThat(page.getByText("LAPORAN LABA RUGI")).hasCount(0);
    }

    @Test
    @DisplayName("Tahun preset reports the full year's revenue and expenses without the closing journal")
    void shouldGenerateYearlyReportViaPreset() {
        navigateTo("/reports/period");
        waitForPageLoad();

        yearRow().locator("button.period-preset:text-is('Tahun')").click();
        waitForPageLoad();

        assertThat(page.locator("#startDate")).hasValue(YEAR + "-01-01");
        assertThat(page.locator("#endDate")).hasValue(YEAR + "-12-31");
        assertIncomeStatement(Map.of("4.1.01", "50.000.000"), Map.of("5.1.07", "(3.000.000)", "5.1.05", "(5.000.000)"),
                "50.000.000", "(8.000.000)", "LABA BERSIH", "42.000.000");
        assertThat(page.getByText("Per 31 Desember " + YEAR)).isVisible();
        assertThat(page.locator("[data-testid='total-liabilities-equity']"))
                .hasText(page.locator("[data-testid='total-assets']").textContent());
    }

    @Test
    @DisplayName("Q1 preset reports January to March")
    void shouldGenerateQuarterlyReportViaPreset() {
        navigateTo("/reports/period");
        waitForPageLoad();

        yearRow().locator("button.period-preset:text-is('Q1')").click();
        waitForPageLoad();

        assertThat(page.locator("#startDate")).hasValue(YEAR + "-01-01");
        assertThat(page.locator("#endDate")).hasValue(YEAR + "-03-31");
        assertIncomeStatement(Map.of("4.1.01", "40.000.000"), Map.of("5.1.07", "(3.000.000)", "5.1.05", "(5.000.000)"),
                "40.000.000", "(8.000.000)", "LABA BERSIH", "32.000.000");
    }

    @Test
    @DisplayName("Month toggle reveals the months; Jan reports January only")
    void shouldGenerateMonthlyReportViaPreset() {
        navigateTo("/reports/period");
        waitForPageLoad();
        Locator months = page.locator("#months-" + YEAR);
        assertThat(months).isHidden();

        yearRow().locator("button.month-toggle").click();
        assertThat(months).isVisible();
        months.locator("button.period-preset:text-is('Jan')").click();
        waitForPageLoad();

        assertThat(page.locator("#startDate")).hasValue(YEAR + "-01-01");
        assertThat(page.locator("#endDate")).hasValue(YEAR + "-01-31");
        assertIncomeStatement(Map.of("4.1.01", "40.000.000"), Map.of("5.1.07", "(3.000.000)"),
                "40.000.000", "(3.000.000)", "LABA BERSIH", "37.000.000");
    }

    @Test
    @DisplayName("Dates typed in the form report that range; a month with only expenses shows a loss")
    void shouldGenerateReportViaManualInput() {
        navigateTo("/reports/period");
        waitForPageLoad();

        page.locator("#startDate").fill(YEAR + "-02-01");
        page.locator("#endDate").fill(YEAR + "-02-28");
        page.locator("#btn-generate").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/reports/period?startDate=" + YEAR + "-02-01&endDate=" + YEAR + "-02-28");
        assertThat(page.getByText("Tidak ada pendapatan")).isVisible();
        assertIncomeStatement(Map.of(), Map.of("5.1.05", "(5.000.000)"),
                "0", "(5.000.000)", "RUGI BERSIH", "-5.000.000");
    }

    @Test
    @DisplayName("A range without postings shows no revenue, no expense and zero result")
    void shouldShowEmptyReportForPeriodWithNoData() {
        navigateTo("/reports/period?startDate=" + YEAR + "-06-01&endDate=" + YEAR + "-06-30");
        waitForPageLoad();

        assertThat(page.getByText("Tidak ada pendapatan")).isVisible();
        assertThat(page.getByText("Tidak ada beban")).isVisible();
        assertIncomeStatement(Map.of(), Map.of(), "0", "(0)", "LABA BERSIH", "0");
    }

    private Locator yearRow() {
        return page.locator("div.flex:has(> span.text-xs:text-is('" + YEAR + "'))");
    }

    private void assertIncomeStatement(Map<String, String> revenue, Map<String, String> expense,
                                       String totalRevenue, String totalExpense, String label, String net) {
        assertThat(page.locator("[data-testid^='revenue-']")).hasCount(revenue.size());
        revenue.forEach((code, amount) ->
                assertThat(page.locator("[data-testid='revenue-" + code + "'] span.font-mono")).hasText(amount));
        assertThat(page.locator("[data-testid^='expense-']")).hasCount(expense.size());
        expense.forEach((code, amount) ->
                assertThat(page.locator("[data-testid='expense-" + code + "'] span.font-mono")).hasText(amount));
        assertThat(page.locator("[data-testid='total-revenue']")).hasText(totalRevenue);
        assertThat(page.locator("[data-testid='total-expense']")).hasText(totalExpense);
        assertThat(page.locator("[data-testid='net-income-label']")).hasText(label);
        assertThat(page.locator("[data-testid='net-income']")).hasText(net);
    }

    private void post(String templateName, LocalDate date, String amount) {
        JournalTemplate template = templateRepository.findByTemplateNameWithLines(templateName)
                .orElseThrow(() -> new IllegalStateException("Seeded template missing: " + templateName));
        JournalTemplateLine bankLine = template.getLines().stream()
                .filter(l -> "BANK".equals(l.getAccountHint())).findFirst()
                .orElseThrow(() -> new IllegalStateException("Template has no BANK line: " + templateName));
        Transaction transaction = new Transaction();
        transaction.setJournalTemplate(template);
        transaction.setTransactionDate(date);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setDescription(templateName + " " + date);
        Transaction created = transactionService.create(transaction, Map.of(bankLine.getId(), account("1.1.02")));
        transactionService.post(created.getId(), "admin");
    }

    private UUID account(String code) {
        return accountRepository.findByAccountCode(code)
                .orElseThrow(() -> new IllegalStateException("Seeded account missing: " + code)).getId();
    }
}
