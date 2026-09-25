package com.artivisi.accountingfinance.functional.service;

import com.artivisi.accountingfinance.entity.AssetCategory;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.FixedAsset;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.functional.page.BalanceSheetPage;
import com.artivisi.accountingfinance.functional.page.CashFlowPage;
import com.artivisi.accountingfinance.functional.page.IncomeStatementPage;
import com.artivisi.accountingfinance.functional.page.JournalLedgerPage;
import com.artivisi.accountingfinance.functional.util.CsvLoader;
import com.artivisi.accountingfinance.functional.util.TransactionRow;
import com.artivisi.accountingfinance.repository.AssetCategoryRepository;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.service.FixedAssetService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Service industry financial reports, computed from the five transactions in
 * service/transactions.csv posted into {@link #YEAR} — a year no other test class posts into.
 *
 * Period reports (income statement, cash-flow movements, ledger movements, fiscal-closing
 * preview) are scoped to that year and asserted as exact amounts. Cumulative reports
 * (trial balance, balance sheet, opening/ending balances) include every other class's
 * postings in the shared database, so they are asserted as the difference between the
 * report on {@link #BEFORE} and on {@link #END}.
 *
 * Expected, from the CSV:
 * - Revenue 359,700,000 (4.1.02 196.2M on 01-15, 4.1.01 163.5M on 02-28)
 * - Expense 8,880,000 (5.1.21 3.33M on 01-15, 5.1.20 5.55M on 01-31)
 * - Net income 350,820,000; capital 500,000,000; cash (1.1.02) +850,820,000
 */
@DisplayName("Service Industry - Financial Reports")
@Import(ServiceTestDataInitializer.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ServiceReportsTest extends PlaywrightTestBase {

    private static final int YEAR = 2043;
    private static final String START = YEAR + "-01-01";
    private static final String END = YEAR + "-02-28";
    private static final String BEFORE = (YEAR - 1) + "-12-31";

    @Autowired private TransactionRepository transactionRepository;
    @Autowired private JournalEntryRepository journalEntryRepository;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private JournalTemplateRepository templateRepository;
    @Autowired private AssetCategoryRepository assetCategoryRepository;
    @Autowired private FixedAssetService fixedAssetService;

    private final List<String> transactionNumbers = new ArrayList<>();

    @BeforeAll
    void postReportTransactions() {
        assertEquals(0, transactionRepository.findPostedTransactionsBetweenDates(
                        LocalDate.of(YEAR, 1, 1), LocalDate.of(YEAR, 12, 31)).size(),
                "Another test posted into reserved report year " + YEAR);

        String runId = UUID.randomUUID().toString().substring(0, 6);
        List<TransactionRow> rows = CsvLoader.loadTransactions("service/transactions.csv");
        for (TransactionRow row : rows) {
            JournalTemplate template = templateRepository.findByTemplateNameAndIsCurrentVersionTrue(row.templateName())
                    .orElseThrow(() -> new IllegalStateException("Seeded template missing: " + row.templateName()));
            BigDecimal amount = parseAmount(row.inputs());
            LocalDate date = LocalDate.parse(row.date()).withYear(YEAR);
            String number = "RPT" + YEAR + "-" + runId + "-" + row.sequence();

            Transaction tx = new Transaction();
            tx.setTransactionNumber(number);
            tx.setTransactionDate(date);
            tx.setDescription(row.description());
            tx.setReferenceNumber(row.reference());
            tx.setAmount(amount);
            tx.setJournalTemplate(template);
            tx.setStatus(TransactionStatus.POSTED);
            tx.setPostedAt(LocalDateTime.now());
            tx.setPostedBy("admin");
            tx.setCreatedBy("admin");
            tx = transactionRepository.save(tx);

            saveEntry(tx, account(row.expectedDebitAccount()), amount, BigDecimal.ZERO, number + "-01");
            saveEntry(tx, account(row.expectedCreditAccount()), BigDecimal.ZERO, amount, number + "-02");
            transactionNumbers.add(number);
        }
        assertEquals(5, transactionNumbers.size(), "service/transactions.csv must hold the five report transactions");
    }

    @Test
    @DisplayName("Trial balance moves each account by exactly the posted amounts and stays balanced")
    void shouldDisplayTrialBalance() {
        loginAsAdmin();

        Map<String, Long> before = trialBalance(BEFORE);
        Map<String, Long> after = trialBalance(END);

        // Debit-positive balance change per account
        assertEquals(850_820_000L, delta(before, after, "1.1.02"));
        assertEquals(-500_000_000L, delta(before, after, "3.1.01"));
        assertEquals(-196_200_000L, delta(before, after, "4.1.02"));
        assertEquals(-163_500_000L, delta(before, after, "4.1.01"));
        assertEquals(3_330_000L, delta(before, after, "5.1.21"));
        assertEquals(5_550_000L, delta(before, after, "5.1.20"));
        assertThat(page.locator("#balance-status")).hasText("Balance");
        assertEquals(amount(page.locator("#total-debit")), amount(page.locator("#total-credit")));

        takeManualScreenshot("service/report-trial-balance");
    }

    @Test
    @DisplayName("Income statement shows each revenue/expense account and the exact totals for the period")
    void shouldDisplayIncomeStatement() {
        loginAsAdmin();

        new IncomeStatementPage(page, baseUrl()).navigate(START, END)
                .verifyPageTitle()
                .verifyProfitStatus();

        assertThat(page.locator("#total-revenue")).hasText("359.700.000");
        assertThat(page.locator("#total-expense")).hasText("(8.880.000)");
        assertThat(page.locator("#net-income")).hasText("350.820.000");
        assertThat(page.locator(".revenue-row")).hasCount(2);
        assertThat(revenueRow("4.1.02").locator(".revenue-balance")).hasText("196.200.000");
        assertThat(revenueRow("4.1.01").locator(".revenue-balance")).hasText("163.500.000");
        assertThat(page.locator(".expense-row")).hasCount(2);
        assertThat(expenseRow("5.1.21").locator(".expense-balance")).hasText("(3.330.000)");
        assertThat(expenseRow("5.1.20").locator(".expense-balance")).hasText("(5.550.000)");

        takeManualScreenshot("service/report-income-statement");
    }

    @Test
    @DisplayName("Balance sheet grows assets and equity by exactly the posted amounts; current-year earnings equal net income")
    void shouldDisplayBalanceSheet() {
        loginAsAdmin();
        BalanceSheetPage balanceSheet = new BalanceSheetPage(page, baseUrl());

        balanceSheet.navigate(BEFORE).verifyBalanced();
        long assetsBefore = amount(page.locator("#total-assets"));
        long equityBefore = amount(page.locator("#total-equity"));
        long liabilitiesBefore = amount(page.locator("#total-liabilities"));

        balanceSheet.navigate(END).verifyPageTitle().verifyBalanced();
        assertEquals(850_820_000L, amount(page.locator("#total-assets")) - assetsBefore);
        assertEquals(850_820_000L, amount(page.locator("#total-equity")) - equityBefore);
        assertEquals(liabilitiesBefore, amount(page.locator("#total-liabilities")));
        assertThat(page.locator("#current-year-earnings")).hasText("350.820.000");

        takeManualScreenshot("service/report-balance-sheet");
    }

    @Test
    @DisplayName("Cash flow classifies the period's cash movements by activity and reconciles beginning to ending balance")
    void shouldDisplayCashFlowStatement() {
        loginAsAdmin();

        new CashFlowPage(page, baseUrl()).navigate(START, END)
                .verifyPageTitle()
                .verifyContentVisible();

        assertThat(page.locator("#operating-total")).hasText("350.820.000");
        assertThat(page.locator("#investing-total")).hasText("0");
        assertThat(page.locator("#financing-total")).hasText("500.000.000");
        assertThat(page.locator("#net-cash-change")).hasText("850.820.000");
        assertEquals(amount(page.locator("#beginning-balance")) + 850_820_000L,
                amount(page.locator("#ending-balance")));

        takeManualScreenshot("service/report-cash-flow");
    }

    @Test
    @DisplayName("Changing the date range in the filter form recomputes the income statement for that range")
    void shouldFilterReportsByDateRange() {
        loginAsAdmin();
        new IncomeStatementPage(page, baseUrl()).navigate(START, END);

        submitRange(YEAR + "-01-01", YEAR + "-01-31");
        assertThat(page.locator("#total-revenue")).hasText("196.200.000");
        assertThat(page.locator("#total-expense")).hasText("(8.880.000)");
        assertThat(page.locator("#net-income")).hasText("187.320.000");

        submitRange(YEAR + "-02-01", YEAR + "-02-28");
        assertThat(page.locator("#total-revenue")).hasText("163.500.000");
        assertThat(page.locator("#total-expense")).hasText("(0)");
        assertThat(page.locator("#net-income")).hasText("163.500.000");
    }

    @Test
    @DisplayName("Income statement export links download a PDF and an Excel file for the displayed range")
    void shouldExportIncomeStatement() {
        loginAsAdmin();
        new IncomeStatementPage(page, baseUrl()).navigate(START, END);

        String pdfHref = page.locator("#btn-export-pdf").getAttribute("href");
        assertEquals("/reports/income-statement/export/pdf?startDate=" + START + "&endDate=" + END, pdfHref);
        APIResponse pdf = page.request().get(baseUrl() + pdfHref);
        assertEquals(200, pdf.status());
        assertEquals("application/pdf", pdf.headers().get("content-type"));
        assertEquals("%PDF", new String(pdf.body(), 0, 4, StandardCharsets.US_ASCII));

        String excelHref = page.locator("#btn-export-excel").getAttribute("href");
        assertEquals("/reports/income-statement/export/excel?startDate=" + START + "&endDate=" + END, excelHref);
        APIResponse excel = page.request().get(baseUrl() + excelHref);
        assertEquals(200, excel.status());
        assertTrue(excel.headers().get("content-type").startsWith(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"), excel.headers().toString());
        assertEquals("PK", new String(excel.body(), 0, 2, StandardCharsets.US_ASCII));
    }

    @Test
    @DisplayName("Bank ledger lists the five posted entries with period totals and closing = opening + net movement")
    void shouldDisplayJournalEntryList() {
        loginAsAdmin();
        ChartOfAccount bank = account("1.1.02");

        new JournalLedgerPage(page, baseUrl()).navigate(bank.getId(), START, END)
                .verifyPageTitle()
                .verifyAccountNameVisible("1.1.02", bank.getAccountName());

        assertThat(page.locator("#entry-count-debit")).hasText("5 entri");
        assertThat(page.locator("#total-debit")).hasText("859,700,000");
        assertThat(page.locator("#total-credit")).hasText("8,880,000");
        for (String number : transactionNumbers) {
            assertThat(page.locator("#entries-content")).containsText(number);
        }
        assertEquals(amount(page.locator("#opening-balance")) + 850_820_000L,
                amount(page.locator("#closing-balance")));

        takeManualScreenshot("service/journals-list");
    }

    @Test
    @DisplayName("Fiscal closing preview for the year shows its revenue, expense and net income, year still open")
    void shouldDisplayFiscalClosingReport() {
        loginAsAdmin();
        navigateTo("/reports/fiscal-closing?year=" + YEAR);
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Penutupan Tahun Buku");
        assertThat(page.locator("#status-open")).containsText("Tahun " + YEAR + " belum ditutup");
        assertThat(page.locator("#card-revenue")).containsText("359,700,000");
        assertThat(page.locator("#card-expense")).containsText("8,880,000");
        assertThat(page.locator("#card-net-income")).containsText("Laba Bersih");
        assertThat(page.locator("#card-net-income")).containsText("350,820,000");
        assertThat(page.locator("#preview-section")).containsText("4.1.02");
        assertThat(page.locator("#preview-section")).containsText("5.1.20");

        takeManualScreenshot("reports-fiscal-closing");
    }

    @Test
    @DisplayName("Depreciation report lists an asset bought in the year with 12 months of straight-line depreciation")
    void shouldDisplayDepreciationReport() {
        AssetCategory komputer = assetCategoryRepository.findByCode("KOMPUTER")
                .orElseThrow(() -> new IllegalStateException("Seeded asset category missing: KOMPUTER"));
        String assetCode = "RPT-DEP-" + UUID.randomUUID().toString().substring(0, 8);
        FixedAsset asset = new FixedAsset();
        asset.setAssetCode(assetCode);
        asset.setName("Laptop Laporan " + assetCode);
        asset.setCategory(komputer);
        asset.setPurchaseDate(LocalDate.of(YEAR, 1, 15));
        asset.setPurchaseCost(new BigDecimal("24000000"));
        asset.setFundingAccount(account("1.1.02"));
        fixedAssetService.create(asset);

        loginAsAdmin();
        navigateTo("/reports/depreciation?year=" + YEAR);
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Laporan Penyusutan");
        Locator row = page.locator("#depreciation-table tbody tr").filter(new Locator.FilterOptions().setHasText(assetCode));
        assertThat(row).hasCount(1);
        Locator cells = row.locator("td");
        assertThat(cells.nth(2)).hasText(komputer.getName());
        assertThat(cells.nth(3)).hasText("15/01/" + YEAR);
        assertThat(cells.nth(4)).hasText("24,000,000");
        assertThat(cells.nth(5)).hasText("4 tahun");
        assertThat(cells.nth(6)).hasText("Garis Lurus");
        assertThat(cells.nth(7)).hasText("6,000,000");
        assertThat(cells.nth(8)).hasText("0");
        assertThat(cells.nth(9)).hasText("24,000,000");

        takeManualScreenshot("reports-depreciation");
    }

    private void submitRange(String startDate, String endDate) {
        page.locator("#startDate").fill(startDate);
        page.locator("#endDate").fill(endDate);
        page.locator("#btn-generate").click();
        page.waitForURL("**/reports/income-statement?startDate=" + startDate + "&endDate=" + endDate);
        waitForPageLoad();
    }

    private Map<String, Long> trialBalance(String asOfDate) {
        navigateTo("/reports/trial-balance?asOfDate=" + asOfDate);
        waitForPageLoad();
        assertThat(page.locator("#page-title")).containsText("Neraca Saldo");
        Map<String, Long> balances = new HashMap<>();
        Locator rows = page.locator("#trial-balance-table tr.trial-balance-row");
        for (int i = 0; i < rows.count(); i++) {
            Locator row = rows.nth(i);
            balances.put(row.locator(".account-code").innerText().trim(),
                    amount(row.locator(".debit-amount")) - amount(row.locator(".credit-amount")));
        }
        return balances;
    }

    private static long delta(Map<String, Long> before, Map<String, Long> after, String accountCode) {
        return after.getOrDefault(accountCode, 0L) - before.getOrDefault(accountCode, 0L);
    }

    private Locator revenueRow(String accountCode) {
        return page.locator(".revenue-row").filter(new Locator.FilterOptions()
                .setHas(page.locator(".account-name").getByText(account(accountCode).getAccountName(),
                        new Locator.GetByTextOptions().setExact(true))));
    }

    private Locator expenseRow(String accountCode) {
        return page.locator(".expense-row").filter(new Locator.FilterOptions()
                .setHas(page.locator(".account-name").getByText(account(accountCode).getAccountName(),
                        new Locator.GetByTextOptions().setExact(true))));
    }

    /** Parses a rendered amount ("850.820.000", "8,880,000", "Rp 1.000", "(8.880.000)", "-"). */
    private static long amount(Locator locator) {
        String text = locator.innerText().trim();
        String digits = text.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            if (text.equals("-")) {
                return 0L;
            }
            throw new IllegalStateException("Not an amount: '" + text + "'");
        }
        long value = Long.parseLong(digits);
        return text.startsWith("(") || text.startsWith("-") ? -value : value;
    }

    private ChartOfAccount account(String code) {
        return accountRepository.findByAccountCode(code)
                .orElseThrow(() -> new IllegalStateException("Seeded account missing: " + code));
    }

    private void saveEntry(Transaction transaction, ChartOfAccount account, BigDecimal debit, BigDecimal credit,
                           String journalNumber) {
        JournalEntry entry = new JournalEntry();
        entry.setJournalNumber(journalNumber);
        entry.setTransaction(transaction);
        entry.setAccount(account);
        entry.setDebitAmount(debit);
        entry.setCreditAmount(credit);
        entry.setPostedAt(transaction.getPostedAt());
        entry.setCreatedBy("admin");
        journalEntryRepository.save(entry);
    }

    private static BigDecimal parseAmount(String inputs) {
        for (String part : inputs.split("\\|")) {
            String[] kv = part.split(":", 2);
            if (kv.length == 2 && kv[0].trim().equals("amount")) {
                return new BigDecimal(kv[1].trim());
            }
        }
        throw new IllegalStateException("Cannot parse amount from inputs: " + inputs);
    }
}
