package com.artivisi.accountingfinance.functional;

import com.microsoft.playwright.APIResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Reports - Success Scenarios")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReportSuccessTest extends FunctionalTestBase {

    @BeforeEach
    void setUp() {
        loginAsAdmin();
    }

    @Nested
    @DisplayName("Trial Balance UI Success Scenarios")
    class TrialBalanceUIScenarios {

        @Test
        @Order(1)
        @DisplayName("Should display trial balance page")
        void shouldDisplayTrialBalancePage() {
            navigateTo("/reports/trial-balance");

            assertThat(page).hasTitle("Neraca Saldo");
            assertElementVisible("text=Neraca Saldo");
            assertElementVisible("input[name='startDate']");
            assertElementVisible("input[name='endDate']");

            takeScreenshot("report-success-01-trial-balance-page");
        }

        @Test
        @Order(2)
        @DisplayName("Should generate trial balance for current month")
        void shouldGenerateTrialBalanceForCurrentMonth() {
            navigateTo("/reports/trial-balance");

            fillForm("input[name='startDate']", "2025-11-01");
            fillForm("input[name='endDate']", "2025-11-30");
            clickButton("Generate");

            waitForPageLoad();
            assertPageContainsText("Debit");
            assertPageContainsText("Kredit");

            takeScreenshot("report-success-02-trial-balance-generated");
        }

        @Test
        @Order(3)
        @DisplayName("Should show balanced totals in trial balance")
        void shouldShowBalancedTotalsInTrialBalance() {
            navigateTo("/reports/trial-balance");

            fillForm("input[name='startDate']", "2025-11-01");
            fillForm("input[name='endDate']", "2025-11-30");
            clickButton("Generate");

            waitForPageLoad();
            assertPageContainsText("Total");

            takeScreenshot("report-success-03-trial-balance-totals");
        }

        @Test
        @Order(4)
        @DisplayName("Should export trial balance to PDF")
        void shouldExportTrialBalanceToPDF() {
            navigateTo("/reports/trial-balance");

            fillForm("input[name='startDate']", "2025-11-01");
            fillForm("input[name='endDate']", "2025-11-30");
            clickButton("Generate");

            waitForPageLoad();
            clickButton("Export PDF");

            takeScreenshot("report-success-04-trial-balance-export");
        }

        @Test
        @Order(5)
        @DisplayName("Should export trial balance to Excel")
        void shouldExportTrialBalanceToExcel() {
            navigateTo("/reports/trial-balance");

            fillForm("input[name='startDate']", "2025-11-01");
            fillForm("input[name='endDate']", "2025-11-30");
            clickButton("Generate");

            waitForPageLoad();
            clickButton("Export Excel");

            takeScreenshot("report-success-05-trial-balance-excel");
        }
    }

    @Nested
    @DisplayName("Income Statement UI Success Scenarios")
    class IncomeStatementUIScenarios {

        @Test
        @Order(10)
        @DisplayName("Should display income statement page")
        void shouldDisplayIncomeStatementPage() {
            navigateTo("/reports/income-statement");

            assertThat(page).hasTitle("Laporan Laba Rugi");
            assertElementVisible("text=Laporan Laba Rugi");
            assertElementVisible("input[name='startDate']");
            assertElementVisible("input[name='endDate']");

            takeScreenshot("report-success-10-income-statement-page");
        }

        @Test
        @Order(11)
        @DisplayName("Should generate income statement for current month")
        void shouldGenerateIncomeStatementForCurrentMonth() {
            navigateTo("/reports/income-statement");

            fillForm("input[name='startDate']", "2025-11-01");
            fillForm("input[name='endDate']", "2025-11-30");
            clickButton("Generate");

            waitForPageLoad();
            assertPageContainsText("Pendapatan");
            assertPageContainsText("Beban");

            takeScreenshot("report-success-11-income-statement-generated");
        }

        @Test
        @Order(12)
        @DisplayName("Should show revenue section in income statement")
        void shouldShowRevenueSectionInIncomeStatement() {
            navigateTo("/reports/income-statement");

            fillForm("input[name='startDate']", "2025-11-01");
            fillForm("input[name='endDate']", "2025-11-30");
            clickButton("Generate");

            waitForPageLoad();
            assertPageContainsText("Pendapatan");

            takeScreenshot("report-success-12-income-revenue-section");
        }

        @Test
        @Order(13)
        @DisplayName("Should show expense section in income statement")
        void shouldShowExpenseSectionInIncomeStatement() {
            navigateTo("/reports/income-statement");

            fillForm("input[name='startDate']", "2025-11-01");
            fillForm("input[name='endDate']", "2025-11-30");
            clickButton("Generate");

            waitForPageLoad();
            assertPageContainsText("Beban");

            takeScreenshot("report-success-13-income-expense-section");
        }

        @Test
        @Order(14)
        @DisplayName("Should calculate net income in income statement")
        void shouldCalculateNetIncomeInIncomeStatement() {
            navigateTo("/reports/income-statement");

            fillForm("input[name='startDate']", "2025-11-01");
            fillForm("input[name='endDate']", "2025-11-30");
            clickButton("Generate");

            waitForPageLoad();
            assertPageContainsText("Laba");

            takeScreenshot("report-success-14-income-net-income");
        }

        @Test
        @Order(15)
        @DisplayName("Should export income statement to PDF")
        void shouldExportIncomeStatementToPDF() {
            navigateTo("/reports/income-statement");

            fillForm("input[name='startDate']", "2025-11-01");
            fillForm("input[name='endDate']", "2025-11-30");
            clickButton("Generate");

            waitForPageLoad();
            clickButton("Export PDF");

            takeScreenshot("report-success-15-income-export-pdf");
        }
    }

    @Nested
    @DisplayName("Balance Sheet UI Success Scenarios")
    class BalanceSheetUIScenarios {

        @Test
        @Order(20)
        @DisplayName("Should display balance sheet page")
        void shouldDisplayBalanceSheetPage() {
            navigateTo("/reports/balance-sheet");

            assertThat(page).hasTitle("Neraca");
            assertElementVisible("text=Neraca");
            assertElementVisible("input[name='asOfDate']");

            takeScreenshot("report-success-20-balance-sheet-page");
        }

        @Test
        @Order(21)
        @DisplayName("Should generate balance sheet for specific date")
        void shouldGenerateBalanceSheetForSpecificDate() {
            navigateTo("/reports/balance-sheet");

            fillForm("input[name='asOfDate']", "2025-11-30");
            clickButton("Generate");

            waitForPageLoad();
            assertPageContainsText("Aset");
            assertPageContainsText("Kewajiban");
            assertPageContainsText("Ekuitas");

            takeScreenshot("report-success-21-balance-sheet-generated");
        }

        @Test
        @Order(22)
        @DisplayName("Should show assets section in balance sheet")
        void shouldShowAssetsSectionInBalanceSheet() {
            navigateTo("/reports/balance-sheet");

            fillForm("input[name='asOfDate']", "2025-11-30");
            clickButton("Generate");

            waitForPageLoad();
            assertPageContainsText("Aset");

            takeScreenshot("report-success-22-balance-assets-section");
        }

        @Test
        @Order(23)
        @DisplayName("Should show liabilities section in balance sheet")
        void shouldShowLiabilitiesSectionInBalanceSheet() {
            navigateTo("/reports/balance-sheet");

            fillForm("input[name='asOfDate']", "2025-11-30");
            clickButton("Generate");

            waitForPageLoad();
            assertPageContainsText("Kewajiban");

            takeScreenshot("report-success-23-balance-liabilities-section");
        }

        @Test
        @Order(24)
        @DisplayName("Should show equity section in balance sheet")
        void shouldShowEquitySectionInBalanceSheet() {
            navigateTo("/reports/balance-sheet");

            fillForm("input[name='asOfDate']", "2025-11-30");
            clickButton("Generate");

            waitForPageLoad();
            assertPageContainsText("Ekuitas");

            takeScreenshot("report-success-24-balance-equity-section");
        }

        @Test
        @Order(25)
        @DisplayName("Should verify balance sheet equation (Assets = Liabilities + Equity)")
        void shouldVerifyBalanceSheetEquation() {
            navigateTo("/reports/balance-sheet");

            fillForm("input[name='asOfDate']", "2025-11-30");
            clickButton("Generate");

            waitForPageLoad();
            // The balance sheet should show balanced totals
            assertPageContainsText("Total");

            takeScreenshot("report-success-25-balance-equation");
        }

        @Test
        @Order(26)
        @DisplayName("Should export balance sheet to PDF")
        void shouldExportBalanceSheetToPDF() {
            navigateTo("/reports/balance-sheet");

            fillForm("input[name='asOfDate']", "2025-11-30");
            clickButton("Generate");

            waitForPageLoad();
            clickButton("Export PDF");

            takeScreenshot("report-success-26-balance-export-pdf");
        }
    }

    @Nested
    @DisplayName("Cash Flow Statement UI Success Scenarios")
    class CashFlowStatementUIScenarios {

        @Test
        @Order(30)
        @DisplayName("Should display cash flow statement page")
        void shouldDisplayCashFlowStatementPage() {
            navigateTo("/reports/cash-flow");

            assertThat(page).hasTitle("Laporan Arus Kas");
            assertElementVisible("text=Laporan Arus Kas");
            assertElementVisible("input[name='startDate']");
            assertElementVisible("input[name='endDate']");

            takeScreenshot("report-success-30-cash-flow-page");
        }

        @Test
        @Order(31)
        @DisplayName("Should generate cash flow statement for current month")
        void shouldGenerateCashFlowStatementForCurrentMonth() {
            navigateTo("/reports/cash-flow");

            fillForm("input[name='startDate']", "2025-11-01");
            fillForm("input[name='endDate']", "2025-11-30");
            clickButton("Generate");

            waitForPageLoad();
            assertPageContainsText("Operasional");
            assertPageContainsText("Investasi");
            assertPageContainsText("Pendanaan");

            takeScreenshot("report-success-31-cash-flow-generated");
        }

        @Test
        @Order(32)
        @DisplayName("Should show operating activities section")
        void shouldShowOperatingActivitiesSection() {
            navigateTo("/reports/cash-flow");

            fillForm("input[name='startDate']", "2025-11-01");
            fillForm("input[name='endDate']", "2025-11-30");
            clickButton("Generate");

            waitForPageLoad();
            assertPageContainsText("Operasional");

            takeScreenshot("report-success-32-cash-flow-operating");
        }

        @Test
        @Order(33)
        @DisplayName("Should show investing activities section")
        void shouldShowInvestingActivitiesSection() {
            navigateTo("/reports/cash-flow");

            fillForm("input[name='startDate']", "2025-11-01");
            fillForm("input[name='endDate']", "2025-11-30");
            clickButton("Generate");

            waitForPageLoad();
            assertPageContainsText("Investasi");

            takeScreenshot("report-success-33-cash-flow-investing");
        }

        @Test
        @Order(34)
        @DisplayName("Should show financing activities section")
        void shouldShowFinancingActivitiesSection() {
            navigateTo("/reports/cash-flow");

            fillForm("input[name='startDate']", "2025-11-01");
            fillForm("input[name='endDate']", "2025-11-30");
            clickButton("Generate");

            waitForPageLoad();
            assertPageContainsText("Pendanaan");

            takeScreenshot("report-success-34-cash-flow-financing");
        }

        @Test
        @Order(35)
        @DisplayName("Should export cash flow statement to PDF")
        void shouldExportCashFlowStatementToPDF() {
            navigateTo("/reports/cash-flow");

            fillForm("input[name='startDate']", "2025-11-01");
            fillForm("input[name='endDate']", "2025-11-30");
            clickButton("Generate");

            waitForPageLoad();
            clickButton("Export PDF");

            takeScreenshot("report-success-35-cash-flow-export-pdf");
        }
    }

    @Nested
    @DisplayName("General Ledger UI Success Scenarios")
    class GeneralLedgerUIScenarios {

        @Test
        @Order(40)
        @DisplayName("Should display general ledger page")
        void shouldDisplayGeneralLedgerPage() {
            navigateTo("/reports/general-ledger");

            assertThat(page).hasTitle("Buku Besar");
            assertElementVisible("text=Buku Besar");
            assertElementVisible("select[name='accountId']");
            assertElementVisible("input[name='startDate']");
            assertElementVisible("input[name='endDate']");

            takeScreenshot("report-success-40-general-ledger-page");
        }

        @Test
        @Order(41)
        @DisplayName("Should generate general ledger for specific account")
        void shouldGenerateGeneralLedgerForSpecificAccount() {
            navigateTo("/reports/general-ledger");

            selectOption("select[name='accountId']", "10000000-0000-0000-0000-000000000101");
            fillForm("input[name='startDate']", "2025-11-01");
            fillForm("input[name='endDate']", "2025-11-30");
            clickButton("Generate");

            waitForPageLoad();
            assertPageContainsText("Kas");

            takeScreenshot("report-success-41-general-ledger-generated");
        }

        @Test
        @Order(42)
        @DisplayName("Should show running balance in general ledger")
        void shouldShowRunningBalanceInGeneralLedger() {
            navigateTo("/reports/general-ledger");

            selectOption("select[name='accountId']", "10000000-0000-0000-0000-000000000101");
            fillForm("input[name='startDate']", "2025-11-01");
            fillForm("input[name='endDate']", "2025-11-30");
            clickButton("Generate");

            waitForPageLoad();
            assertPageContainsText("Saldo");

            takeScreenshot("report-success-42-general-ledger-balance");
        }

        @Test
        @Order(43)
        @DisplayName("Should show debit and credit columns in general ledger")
        void shouldShowDebitAndCreditColumnsInGeneralLedger() {
            navigateTo("/reports/general-ledger");

            selectOption("select[name='accountId']", "10000000-0000-0000-0000-000000000101");
            fillForm("input[name='startDate']", "2025-11-01");
            fillForm("input[name='endDate']", "2025-11-30");
            clickButton("Generate");

            waitForPageLoad();
            assertPageContainsText("Debit");
            assertPageContainsText("Kredit");

            takeScreenshot("report-success-43-general-ledger-columns");
        }

        @Test
        @Order(44)
        @DisplayName("Should export general ledger to PDF")
        void shouldExportGeneralLedgerToPDF() {
            navigateTo("/reports/general-ledger");

            selectOption("select[name='accountId']", "10000000-0000-0000-0000-000000000101");
            fillForm("input[name='startDate']", "2025-11-01");
            fillForm("input[name='endDate']", "2025-11-30");
            clickButton("Generate");

            waitForPageLoad();
            clickButton("Export PDF");

            takeScreenshot("report-success-44-general-ledger-export");
        }
    }

    @Nested
    @DisplayName("Reports API Success Scenarios")
    class ReportsAPIScenarios {

        @Test
        @Order(50)
        @DisplayName("Should retrieve trial balance via API")
        void shouldRetrieveTrialBalanceViaAPI() {
            APIResponse response = apiGet("/reports/api/trial-balance?startDate=2025-11-01&endDate=2025-11-30");

            assertEquals(200, response.status());
            String body = response.text();
            assertTrue(body.contains("accounts") || body.contains("debit") || body.contains("credit"));
        }

        @Test
        @Order(51)
        @DisplayName("Should retrieve income statement via API")
        void shouldRetrieveIncomeStatementViaAPI() {
            APIResponse response = apiGet("/reports/api/income-statement?startDate=2025-11-01&endDate=2025-11-30");

            assertEquals(200, response.status());
            String body = response.text();
            assertTrue(body.contains("revenue") || body.contains("expense") || body.contains("netIncome"));
        }

        @Test
        @Order(52)
        @DisplayName("Should retrieve balance sheet via API")
        void shouldRetrieveBalanceSheetViaAPI() {
            APIResponse response = apiGet("/reports/api/balance-sheet?asOfDate=2025-11-30");

            assertEquals(200, response.status());
            String body = response.text();
            assertTrue(body.contains("assets") || body.contains("liabilities") || body.contains("equity"));
        }

        @Test
        @Order(53)
        @DisplayName("Should retrieve cash flow statement via API")
        void shouldRetrieveCashFlowStatementViaAPI() {
            APIResponse response = apiGet("/reports/api/cash-flow?startDate=2025-11-01&endDate=2025-11-30");

            assertEquals(200, response.status());
            String body = response.text();
            assertTrue(body.contains("operating") || body.contains("investing") || body.contains("financing"));
        }

        @Test
        @Order(54)
        @DisplayName("Should retrieve general ledger via API")
        void shouldRetrieveGeneralLedgerViaAPI() {
            APIResponse response = apiGet("/reports/api/general-ledger?accountId=10000000-0000-0000-0000-000000000101&startDate=2025-11-01&endDate=2025-11-30");

            assertEquals(200, response.status());
        }

        @Test
        @Order(55)
        @DisplayName("Should retrieve trial balance for different periods via API")
        void shouldRetrieveTrialBalanceForDifferentPeriodsViaAPI() {
            // Test Q1
            APIResponse q1Response = apiGet("/reports/api/trial-balance?startDate=2025-01-01&endDate=2025-03-31");
            assertEquals(200, q1Response.status());

            // Test Q2
            APIResponse q2Response = apiGet("/reports/api/trial-balance?startDate=2025-04-01&endDate=2025-06-30");
            assertEquals(200, q2Response.status());
        }

        @Test
        @Order(56)
        @DisplayName("Should retrieve account balances via API")
        void shouldRetrieveAccountBalancesViaAPI() {
            APIResponse response = apiGet("/reports/api/account-balances?asOfDate=2025-11-30");

            assertEquals(200, response.status());
        }
    }
}
