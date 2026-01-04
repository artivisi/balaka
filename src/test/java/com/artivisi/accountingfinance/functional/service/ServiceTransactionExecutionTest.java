package com.artivisi.accountingfinance.functional.service;

import com.artivisi.accountingfinance.functional.page.BalanceSheetPage;
import com.artivisi.accountingfinance.functional.page.IncomeStatementPage;
import com.artivisi.accountingfinance.functional.page.TransactionDetailPage;
import com.artivisi.accountingfinance.functional.page.TransactionFormPage;
import com.artivisi.accountingfinance.functional.page.TransactionListPage;
import com.artivisi.accountingfinance.functional.util.CsvLoader;
import com.artivisi.accountingfinance.functional.util.TransactionRow;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * CSV-Driven Transaction Execution Tests for Service Industry.
 *
 * This test suite:
 * 1. Reads transaction scenarios from CSV file
 * 2. Executes each transaction via the UI using Page Objects
 * 3. Takes screenshots for user manual
 * 4. Verifies transaction detail page
 * 5. Verifies financial reports at the end
 *
 * Data from: src/test/resources/testdata/service/transactions.csv
 */
@DisplayName("Service Industry - Transaction Execution")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Import(ServiceTestDataInitializer.class)
public class ServiceTransactionExecutionTest extends PlaywrightTestBase {

    @Autowired
    private JournalTemplateRepository templateRepository;

    // Page Objects
    private TransactionFormPage transactionFormPage;
    private TransactionDetailPage transactionDetailPage;
    private IncomeStatementPage incomeStatementPage;
    private BalanceSheetPage balanceSheetPage;
    private TransactionListPage transactionListPage;

    private void initPageObjects() {
        String baseUrl = "http://localhost:" + port;
        transactionFormPage = new TransactionFormPage(page, baseUrl);
        transactionDetailPage = new TransactionDetailPage(page, baseUrl);
        incomeStatementPage = new IncomeStatementPage(page, baseUrl);
        balanceSheetPage = new BalanceSheetPage(page, baseUrl);
        transactionListPage = new TransactionListPage(page, baseUrl);
    }

    /**
     * Execute transactions from CSV and verify results.
     * Uses @TestFactory to create dynamic tests from CSV data.
     */
    @TestFactory
    @Order(1)
    @DisplayName("Execute transactions from CSV")
    Stream<DynamicTest> executeTransactionsFromCsv() {
        List<TransactionRow> transactions = CsvLoader.loadTransactions("service/transactions.csv");

        return transactions.stream()
            .sorted(Comparator.comparing(TransactionRow::sequence))
            .map(tx -> DynamicTest.dynamicTest(
                "Tx " + tx.sequence() + ": " + tx.description(),
                () -> executeTransaction(tx)
            ));
    }

    /**
     * Execute a single transaction via UI using Page Objects.
     */
    private void executeTransaction(TransactionRow tx) {
        loginAsAdmin();
        initPageObjects();

        // Find template ID by name
        var template = templateRepository.findByTemplateNameAndIsCurrentVersionTrue(tx.templateName());
        if (template.isEmpty()) {
            System.out.println("Template not found: " + tx.templateName() + " - skipping transaction");
            return;
        }
        UUID templateId = template.get().getId();

        // Navigate to form and fill data
        transactionFormPage.navigateWithTemplate(templateId)
            .fillDate(tx.date())
            .fillInputs(tx.inputs())
            .fillDescription(tx.description())
            .fillReferenceNumber(tx.reference())
            .selectProject(tx.project());

        // Take screenshot before submit if requested
        if (tx.screenshot()) {
            takeManualScreenshot("service/" + String.format("%02d", tx.sequence()) + "-" + slugify(tx.description()) + "-form");
        }

        // Save and post, get detail page
        transactionDetailPage = transactionFormPage.saveAndPost();

        // Take screenshot of result if requested
        if (tx.screenshot()) {
            takeManualScreenshot("service/" + String.format("%02d", tx.sequence()) + "-" + slugify(tx.description()) + "-result");
        }

        // Verify transaction is posted
        transactionDetailPage.verifyStatusPosted();

        // Verify journal entries are correct
        transactionDetailPage.verifyJournalEntriesVisible()
            .verifyJournalEntryCount(2)  // Simple templates have 2 lines
            .verifyJournalEntryAccountCode(0, tx.expectedDebitAccount())
            .verifyJournalEntryDebit(0, tx.expectedAmount())
            .verifyJournalEntryAccountCode(1, tx.expectedCreditAccount())
            .verifyJournalEntryCredit(1, tx.expectedAmount())
            .verifyJournalBalanced();
    }

    /**
     * Verify Income Statement (P&L) after all transactions.
     */
    @Test
    @Order(2)
    @DisplayName("Verify Income Statement report")
    void verifyIncomeStatement() {
        loginAsAdmin();
        initPageObjects();

        incomeStatementPage.navigate("2024-01-01", "2024-03-31")
            .verifyPageTitle()
            .verifyRevenueSectionVisible();

        // Take screenshot for user manual (after transactions are executed)
        takeManualScreenshot("service/report-income-statement");
    }

    /**
     * Verify Balance Sheet after all transactions.
     */
    @Test
    @Order(3)
    @DisplayName("Verify Balance Sheet report")
    void verifyBalanceSheet() {
        loginAsAdmin();
        initPageObjects();

        balanceSheetPage.navigate("2024-03-31")
            .verifyPageTitle()
            .verifyAssetSectionVisible()
            .verifyLiabilitySectionVisible()
            .verifyEquitySectionVisible();

        // Take screenshot for user manual (after transactions are executed)
        takeManualScreenshot("service/report-balance-sheet");
    }

    /**
     * Verify transaction list shows all executed transactions.
     */
    @Test
    @Order(4)
    @DisplayName("Verify transaction list")
    void verifyTransactionList() {
        loginAsAdmin();
        initPageObjects();

        transactionListPage.navigate()
            .verifyPageTitle()
            .verifyContentVisible();

        // Take screenshot for user manual (after transactions are executed)
        takeManualScreenshot("service/transaction-list");
    }

    /**
     * Capture list screenshots for IT Service industry manual and tutorial.
     * These screenshots are used in both 02-tutorial-akuntansi.md and 07-industri-jasa.md.
     * Taken AFTER all transactions are executed so screenshots have data.
     */
    @Test
    @Order(5)
    @DisplayName("Capture list and detail screenshots")
    void captureListScreenshots() {
        loginAsAdmin();

        // Dashboard (has widgets with data)
        page.navigate("http://localhost:" + port + "/dashboard");
        page.waitForLoadState();
        page.waitForTimeout(1000);
        takeManualScreenshot("service/dashboard");

        // Chart of Accounts list (from seed data)
        page.navigate("http://localhost:" + port + "/accounts");
        page.waitForLoadState();
        page.waitForSelector("#page-title", new com.microsoft.playwright.Page.WaitForSelectorOptions().setTimeout(5000));
        takeManualScreenshot("service/accounts-list");

        // Clients list (from test data)
        page.navigate("http://localhost:" + port + "/clients");
        page.waitForLoadState();
        page.waitForSelector("#page-title", new com.microsoft.playwright.Page.WaitForSelectorOptions().setTimeout(5000));
        takeManualScreenshot("service/clients-list");

        // Projects list (from test data)
        page.navigate("http://localhost:" + port + "/projects");
        page.waitForLoadState();
        page.waitForSelector("#page-title", new com.microsoft.playwright.Page.WaitForSelectorOptions().setTimeout(5000));
        takeManualScreenshot("service/projects-list");

        // Template list (from seed data)
        page.navigate("http://localhost:" + port + "/templates");
        page.waitForLoadState();
        page.waitForSelector("#page-title", new com.microsoft.playwright.Page.WaitForSelectorOptions().setTimeout(5000));
        takeManualScreenshot("service/templates-list");

        // Find first template for detail view
        var firstTemplate = templateRepository.findAll().stream()
            .filter(t -> t.getCategory().name().equals("INCOME"))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("No INCOME template found"));

        // Template detail
        page.navigate("http://localhost:" + port + "/templates/" + firstTemplate.getId());
        page.waitForLoadState();
        page.waitForSelector("#page-title", new com.microsoft.playwright.Page.WaitForSelectorOptions().setTimeout(5000));
        takeManualScreenshot("service/templates-detail");

        // Journals list (from executed transactions)
        page.navigate("http://localhost:" + port + "/journals");
        page.waitForLoadState();
        page.waitForSelector("#page-title", new com.microsoft.playwright.Page.WaitForSelectorOptions().setTimeout(5000));
        takeManualScreenshot("service/journals-list");

        // Trial Balance (after transactions)
        page.navigate("http://localhost:" + port + "/reports/trial-balance?startDate=2024-01-01&endDate=2024-03-31");
        page.waitForLoadState();
        page.waitForTimeout(2000); // Wait for report to render
        takeManualScreenshot("service/report-trial-balance");
    }

    /**
     * Convert text to URL-friendly slug.
     */
    private String slugify(String text) {
        return text.toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-|-$", "")
            .substring(0, Math.min(30, text.replaceAll("[^a-z0-9]+", "-").length()));
    }
}
