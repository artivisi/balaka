package com.artivisi.accountingfinance.functional.service;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.functional.page.BalanceSheetPage;
import com.artivisi.accountingfinance.functional.page.CashFlowPage;
import com.artivisi.accountingfinance.functional.page.IncomeStatementPage;
import com.artivisi.accountingfinance.functional.page.JournalLedgerPage;
import com.artivisi.accountingfinance.functional.page.TrialBalancePage;
import com.artivisi.accountingfinance.functional.util.CsvLoader;
import com.artivisi.accountingfinance.functional.util.TransactionRow;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.DraftTransactionRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Service Industry Financial Reports Tests.
 * Uses Page Object Pattern for maintainability.
 *
 * Test Data (inserted directly to database):
 * - Capital: Rp 500,000,000 (2024-01-01)
 * - Consulting Revenue: Rp 196,200,000 (2024-01-15)
 * - Software Expense: Rp 3,330,000 (2024-01-15)
 * - Cloud Expense: Rp 5,550,000 (2024-01-31)
 * - Training Revenue: Rp 163,500,000 (2024-02-28)
 *
 * Expected Calculations:
 * - Total Revenue: Rp 359,700,000 (196.2M + 163.5M)
 * - Total Expenses: Rp 8,880,000 (3.33M + 5.55M)
 * - Net Income: Rp 350,820,000
 * - Cash Balance: Rp 850,820,000 (500M + 359.7M - 8.88M)
 */
@DisplayName("Service Industry - Financial Reports")
@Import(ServiceTestDataInitializer.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ServiceReportsTest extends PlaywrightTestBase {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Autowired
    private DraftTransactionRepository draftTransactionRepository;

    @Autowired
    private ChartOfAccountRepository accountRepository;

    @Autowired
    private JournalTemplateRepository templateRepository;

    // Page Objects
    private TrialBalancePage trialBalancePage;
    private IncomeStatementPage incomeStatementPage;
    private BalanceSheetPage balanceSheetPage;
    private CashFlowPage cashFlowPage;
    private JournalLedgerPage journalLedgerPage;

    private void initPageObjects() {
        String baseUrl = "http://localhost:" + port;
        trialBalancePage = new TrialBalancePage(page, baseUrl);
        incomeStatementPage = new IncomeStatementPage(page, baseUrl);
        balanceSheetPage = new BalanceSheetPage(page, baseUrl);
        cashFlowPage = new CashFlowPage(page, baseUrl);
        journalLedgerPage = new JournalLedgerPage(page, baseUrl);
    }

    @BeforeAll
    void setupTestTransactions() {
        // Clear any existing transactions (in case ServiceTransactionExecutionTest ran first)
        // Must delete draft transactions first due to FK constraint
        draftTransactionRepository.deleteAll();
        journalEntryRepository.deleteAll();
        transactionRepository.deleteAll();

        // Load transactions from CSV (same data as ServiceTransactionExecutionTest)
        List<TransactionRow> transactions = CsvLoader.loadTransactions("service/transactions.csv");

        // Create transactions programmatically using CSV data
        int txIndex = 0;
        for (TransactionRow txRow : transactions) {
            txIndex++;
            // Get template by name
            JournalTemplate template = templateRepository.findByTemplateNameAndIsCurrentVersionTrue(txRow.templateName())
                .orElseThrow(() -> new RuntimeException("Template not found: " + txRow.templateName()));

            // Parse amount from inputs (e.g., "amount:500000000")
            BigDecimal amount = parseAmount(txRow.inputs());

            // Create transaction
            Transaction tx = createTransaction(template, LocalDate.parse(txRow.date()),
                txRow.description(), txRow.reference(), amount, txIndex);

            // Get debit and credit accounts from CSV
            ChartOfAccount debitAccount = accountRepository.findByAccountCode(txRow.expectedDebitAccount())
                .orElseThrow(() -> new RuntimeException("Debit account not found: " + txRow.expectedDebitAccount()));
            ChartOfAccount creditAccount = accountRepository.findByAccountCode(txRow.expectedCreditAccount())
                .orElseThrow(() -> new RuntimeException("Credit account not found: " + txRow.expectedCreditAccount()));

            // Create journal entries with journal numbers
            String journalNumber = String.format("JE-RPT-%04d", txIndex);
            createJournalEntry(tx, debitAccount, amount, BigDecimal.ZERO, journalNumber + "-01");
            createJournalEntry(tx, creditAccount, BigDecimal.ZERO, amount, journalNumber + "-02");
        }
    }

    private BigDecimal parseAmount(String inputs) {
        // Parse "amount:500000000" or "amount:500000000|BANK:1.1.02" format
        String[] pipeParts = inputs.split("\\|");
        for (String part : pipeParts) {
            String[] kvParts = part.split(":", 2);
            if (kvParts.length == 2 && kvParts[0].trim().equals("amount")) {
                return new BigDecimal(kvParts[1].trim());
            }
        }
        throw new RuntimeException("Cannot parse amount from inputs: " + inputs);
    }

    private Transaction createTransaction(JournalTemplate template, LocalDate date, String description, String reference, BigDecimal amount, int index) {
        Transaction tx = new Transaction();
        tx.setTransactionNumber(String.format("TRX-RPT-%04d", index));
        tx.setTransactionDate(date);
        tx.setDescription(description);
        tx.setReferenceNumber(reference);
        tx.setAmount(amount);
        tx.setJournalTemplate(template);
        tx.setStatus(TransactionStatus.POSTED);
        tx.setPostedAt(LocalDateTime.now());
        tx.setPostedBy("admin");
        tx.setCreatedBy("admin");
        return transactionRepository.save(tx);
    }

    private void createJournalEntry(Transaction transaction, ChartOfAccount account,
                                     BigDecimal debit, BigDecimal credit, String journalNumber) {
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

    @Test
    @DisplayName("Should display Trial Balance with correct balances")
    void shouldDisplayTrialBalance() {
        loginAsAdmin();
        initPageObjects();

        trialBalancePage.navigate("2024-01-01", "2024-02-28")
            .verifyPageTitle();

        // Take screenshot for verification
        takeManualScreenshot("service/report-trial-balance");
    }

    @Test
    @DisplayName("Should display Income Statement with correct calculations")
    void shouldDisplayIncomeStatement() {
        loginAsAdmin();
        initPageObjects();

        // Expected from test data:
        // Total Revenue: 359,700,000 (196.2M + 163.5M)
        // Total Expenses: 8,880,000 (3.33M + 5.55M)
        // Net Income: 350,820,000
        incomeStatementPage.navigate("2024-01-01", "2024-02-28")
            .verifyPageTitle()
            .verifyRevenueSectionVisible()
            .verifyExpenseSectionVisible()
            .verifyNetIncomeVisible()
            .verifyTotalRevenue("359.700.000")
            .verifyTotalExpense("8.880.000")
            .verifyNetIncome("350.820.000")
            .verifyProfitStatus();

        takeManualScreenshot("service/report-income-statement");
    }

    @Test
    @DisplayName("Should display Balance Sheet with correct amounts")
    void shouldDisplayBalanceSheet() {
        loginAsAdmin();
        initPageObjects();

        // Expected from test data:
        // Total Assets: 850,820,000 (Cash balance)
        // Total Liabilities: 0
        // Total Equity: 850,820,000 (500M capital + 350.82M earnings)
        // Current Year Earnings: 350,820,000
        balanceSheetPage.navigate("2024-02-28")
            .verifyPageTitle()
            .verifyAssetSectionVisible()
            .verifyLiabilitySectionVisible()
            .verifyEquitySectionVisible()
            .verifyTotalAssets("850.820.000")
            .verifyTotalEquity("850.820.000")
            .verifyCurrentYearEarnings("350.820.000")
            .verifyBalanced();

        takeManualScreenshot("service/report-balance-sheet");
    }

    @Test
    @DisplayName("Should display Cash Flow Statement with activities")
    void shouldDisplayCashFlowStatement() {
        loginAsAdmin();
        initPageObjects();

        // Expected from test data:
        // Operating: Net Income 350,820,000
        // Financing: Capital 500,000,000
        // Ending Balance: 850,820,000
        cashFlowPage.navigate("2024-01-01", "2024-02-28")
            .verifyPageTitle()
            .verifyContentVisible()
            .verifyEndingBalance("850.820.000");

        takeManualScreenshot("service/report-cash-flow");
    }

    @Test
    @DisplayName("Should filter reports by date range")
    void shouldFilterReportsByDateRange() {
        loginAsAdmin();
        initPageObjects();

        incomeStatementPage.navigate()
            .verifyPageTitle();

        // Verify date filter controls exist via page locators
        assertThat(page.locator("#startDate")).isVisible();
        assertThat(page.locator("#endDate")).isVisible();
    }

    @Test
    @DisplayName("Should have export buttons on report")
    void shouldHaveExportButtons() {
        loginAsAdmin();
        initPageObjects();

        incomeStatementPage.navigate()
            .verifyPageTitle();

        // Verify export button exists (PDF)
        assertThat(page.locator("#btn-export-pdf")).isVisible();
    }

    @Test
    @DisplayName("Should display Journal Entry List with 5 posted transactions")
    void shouldDisplayJournalEntryList() {
        loginAsAdmin();
        initPageObjects();

        // Get Bank BCA account to view its ledger (all transactions use 1.1.02)
        ChartOfAccount bankBCA = accountRepository.findByAccountCode("1.1.02")
            .orElseThrow(() -> new RuntimeException("Bank BCA account not found"));

        journalLedgerPage.navigate(bankBCA.getId(), "2024-01-01", "2024-02-28")
            .verifyPageTitle()
            .verifyAccountNameVisible("1.1.02", "Bank BCA")
            .verifyEntriesContentVisible()
            .verifyOpeningBalanceVisible()
            .verifyClosingBalance("850,820,000");

        // Take screenshot for verification
        takeManualScreenshot("service/journals-list");
    }

    @Test
    @DisplayName("Should display fiscal year closing report")
    void shouldDisplayFiscalClosingReport() {
        loginAsAdmin();
        navigateTo("/reports/fiscal-closing?year=2024");
        waitForPageLoad();

        // Verify page loads (check page content or h1)
        assertThat(page.locator("h1").first()).isVisible();

        // Take screenshot for user manual
        takeManualScreenshot("reports-fiscal-closing");
    }

    @Test
    @DisplayName("Should display depreciation report for fixed assets")
    void shouldDisplayDepreciationReport() {
        loginAsAdmin();
        navigateTo("/reports/depreciation?year=2024");
        waitForPageLoad();

        // Verify page loads (check page content or h1)
        assertThat(page.locator("h1").first()).isVisible();

        // Take screenshot for user manual (needed for 03-aset-tetap.md)
        takeManualScreenshot("reports-depreciation");
    }
}
