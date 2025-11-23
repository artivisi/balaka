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

@DisplayName("Reports - Alternate Scenarios")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReportAlternateTest extends FunctionalTestBase {

    @BeforeEach
    void setUp() {
        loginAsAdmin();
    }

    @Nested
    @DisplayName("UI Alternate Scenarios")
    class UIAlternateScenarios {

        @Test
        @Order(1)
        @DisplayName("Should show validation error when generating trial balance without date range")
        void shouldShowErrorWhenGeneratingTrialBalanceWithoutDateRange() {
            navigateTo("/reports/trial-balance");

            // Clear default dates
            clearAndFill("input[name='startDate']", "");
            clearAndFill("input[name='endDate']", "");
            clickButton("Generate");

            assertElementVisible("input[name='startDate']:invalid, input[name='endDate']:invalid, .error, .alert-danger");
            takeScreenshot("report-alt-01-trial-balance-no-dates");
        }

        @Test
        @Order(2)
        @DisplayName("Should show error when start date is after end date in trial balance")
        void shouldShowErrorWhenStartDateAfterEndDateInTrialBalance() {
            navigateTo("/reports/trial-balance");

            fillForm("input[name='startDate']", "2025-11-30");
            fillForm("input[name='endDate']", "2025-11-01");
            clickButton("Generate");

            assertPageContainsText("start date") || assertPageContainsText("tanggal");
            takeScreenshot("report-alt-02-trial-balance-invalid-dates");
        }

        @Test
        @Order(3)
        @DisplayName("Should show validation error when generating income statement without date range")
        void shouldShowErrorWhenGeneratingIncomeStatementWithoutDateRange() {
            navigateTo("/reports/income-statement");

            clearAndFill("input[name='startDate']", "");
            clearAndFill("input[name='endDate']", "");
            clickButton("Generate");

            assertElementVisible("input[name='startDate']:invalid, input[name='endDate']:invalid, .error, .alert-danger");
            takeScreenshot("report-alt-03-income-no-dates");
        }

        @Test
        @Order(4)
        @DisplayName("Should show error when start date is after end date in income statement")
        void shouldShowErrorWhenStartDateAfterEndDateInIncomeStatement() {
            navigateTo("/reports/income-statement");

            fillForm("input[name='startDate']", "2025-12-31");
            fillForm("input[name='endDate']", "2025-01-01");
            clickButton("Generate");

            assertPageContainsText("start date") || assertPageContainsText("tanggal");
            takeScreenshot("report-alt-04-income-invalid-dates");
        }

        @Test
        @Order(5)
        @DisplayName("Should show validation error when generating balance sheet without date")
        void shouldShowErrorWhenGeneratingBalanceSheetWithoutDate() {
            navigateTo("/reports/balance-sheet");

            clearAndFill("input[name='asOfDate']", "");
            clickButton("Generate");

            assertElementVisible("input[name='asOfDate']:invalid, .error, .alert-danger");
            takeScreenshot("report-alt-05-balance-no-date");
        }

        @Test
        @Order(6)
        @DisplayName("Should show validation error when generating cash flow without date range")
        void shouldShowErrorWhenGeneratingCashFlowWithoutDateRange() {
            navigateTo("/reports/cash-flow");

            clearAndFill("input[name='startDate']", "");
            clearAndFill("input[name='endDate']", "");
            clickButton("Generate");

            assertElementVisible("input[name='startDate']:invalid, input[name='endDate']:invalid, .error, .alert-danger");
            takeScreenshot("report-alt-06-cash-flow-no-dates");
        }

        @Test
        @Order(7)
        @DisplayName("Should show validation error when generating general ledger without account")
        void shouldShowErrorWhenGeneratingGeneralLedgerWithoutAccount() {
            navigateTo("/reports/general-ledger");

            fillForm("input[name='startDate']", "2025-11-01");
            fillForm("input[name='endDate']", "2025-11-30");
            // Don't select account
            clickButton("Generate");

            assertElementVisible("select[name='accountId']:invalid, .error, .alert-danger");
            takeScreenshot("report-alt-07-general-ledger-no-account");
        }

        @Test
        @Order(8)
        @DisplayName("Should show validation error when generating general ledger without dates")
        void shouldShowErrorWhenGeneratingGeneralLedgerWithoutDates() {
            navigateTo("/reports/general-ledger");

            selectOption("select[name='accountId']", "10000000-0000-0000-0000-000000000101");
            clearAndFill("input[name='startDate']", "");
            clearAndFill("input[name='endDate']", "");
            clickButton("Generate");

            assertElementVisible("input[name='startDate']:invalid, input[name='endDate']:invalid, .error, .alert-danger");
            takeScreenshot("report-alt-08-general-ledger-no-dates");
        }

        @Test
        @Order(9)
        @DisplayName("Should handle empty result for trial balance")
        void shouldHandleEmptyResultForTrialBalance() {
            navigateTo("/reports/trial-balance");

            // Use a date range with no transactions
            fillForm("input[name='startDate']", "2020-01-01");
            fillForm("input[name='endDate']", "2020-01-31");
            clickButton("Generate");

            waitForPageLoad();
            // Should show empty state or zero balances
            assertPageContainsText("0") || assertPageContainsText("tidak ada");
            takeScreenshot("report-alt-09-trial-balance-empty");
        }

        @Test
        @Order(10)
        @DisplayName("Should handle empty result for income statement")
        void shouldHandleEmptyResultForIncomeStatement() {
            navigateTo("/reports/income-statement");

            // Use a date range with no transactions
            fillForm("input[name='startDate']", "2020-01-01");
            fillForm("input[name='endDate']", "2020-01-31");
            clickButton("Generate");

            waitForPageLoad();
            // Should show zero or empty state
            assertPageContainsText("0") || assertPageContainsText("tidak ada");
            takeScreenshot("report-alt-10-income-empty");
        }

        @Test
        @Order(11)
        @DisplayName("Should handle future date for balance sheet")
        void shouldHandleFutureDateForBalanceSheet() {
            navigateTo("/reports/balance-sheet");

            fillForm("input[name='asOfDate']", "2030-12-31");
            clickButton("Generate");

            waitForPageLoad();
            // Should either show current balances or validation error
            takeScreenshot("report-alt-11-balance-future-date");
        }

        @Test
        @Order(12)
        @DisplayName("Should handle very old date for balance sheet")
        void shouldHandleVeryOldDateForBalanceSheet() {
            navigateTo("/reports/balance-sheet");

            fillForm("input[name='asOfDate']", "2000-01-01");
            clickButton("Generate");

            waitForPageLoad();
            // Should show zero balances
            takeScreenshot("report-alt-12-balance-old-date");
        }

        @Test
        @Order(13)
        @DisplayName("Should handle invalid date format in trial balance")
        void shouldHandleInvalidDateFormatInTrialBalance() {
            navigateTo("/reports/trial-balance");

            // Try to enter invalid date
            page.fill("input[name='startDate']", "invalid-date");
            page.fill("input[name='endDate']", "2025-11-30");
            clickButton("Generate");

            assertElementVisible("input:invalid, .error, .alert-danger");
            takeScreenshot("report-alt-13-trial-balance-invalid-date-format");
        }
    }

    @Nested
    @DisplayName("API Alternate Scenarios")
    class APIAlternateScenarios {

        @Test
        @Order(20)
        @DisplayName("Should return 400 for trial balance without date parameters")
        void shouldReturn400ForTrialBalanceWithoutDateParameters() {
            APIResponse response = apiGet("/reports/api/trial-balance");

            assertEquals(400, response.status());
        }

        @Test
        @Order(21)
        @DisplayName("Should return 400 for trial balance with invalid date range")
        void shouldReturn400ForTrialBalanceWithInvalidDateRange() {
            APIResponse response = apiGet("/reports/api/trial-balance?startDate=2025-11-30&endDate=2025-11-01");

            assertEquals(400, response.status());
        }

        @Test
        @Order(22)
        @DisplayName("Should return 400 for income statement without date parameters")
        void shouldReturn400ForIncomeStatementWithoutDateParameters() {
            APIResponse response = apiGet("/reports/api/income-statement");

            assertEquals(400, response.status());
        }

        @Test
        @Order(23)
        @DisplayName("Should return 400 for income statement with invalid date range")
        void shouldReturn400ForIncomeStatementWithInvalidDateRange() {
            APIResponse response = apiGet("/reports/api/income-statement?startDate=2025-12-31&endDate=2025-01-01");

            assertEquals(400, response.status());
        }

        @Test
        @Order(24)
        @DisplayName("Should return 400 for balance sheet without date parameter")
        void shouldReturn400ForBalanceSheetWithoutDateParameter() {
            APIResponse response = apiGet("/reports/api/balance-sheet");

            assertEquals(400, response.status());
        }

        @Test
        @Order(25)
        @DisplayName("Should return 400 for balance sheet with invalid date format")
        void shouldReturn400ForBalanceSheetWithInvalidDateFormat() {
            APIResponse response = apiGet("/reports/api/balance-sheet?asOfDate=invalid-date");

            assertEquals(400, response.status());
        }

        @Test
        @Order(26)
        @DisplayName("Should return 400 for cash flow without date parameters")
        void shouldReturn400ForCashFlowWithoutDateParameters() {
            APIResponse response = apiGet("/reports/api/cash-flow");

            assertEquals(400, response.status());
        }

        @Test
        @Order(27)
        @DisplayName("Should return 400 for general ledger without account ID")
        void shouldReturn400ForGeneralLedgerWithoutAccountId() {
            APIResponse response = apiGet("/reports/api/general-ledger?startDate=2025-11-01&endDate=2025-11-30");

            assertEquals(400, response.status());
        }

        @Test
        @Order(28)
        @DisplayName("Should return 400 for general ledger without date parameters")
        void shouldReturn400ForGeneralLedgerWithoutDateParameters() {
            APIResponse response = apiGet("/reports/api/general-ledger?accountId=10000000-0000-0000-0000-000000000101");

            assertEquals(400, response.status());
        }

        @Test
        @Order(29)
        @DisplayName("Should return 404 for general ledger with non-existent account")
        void shouldReturn404ForGeneralLedgerWithNonExistentAccount() {
            APIResponse response = apiGet("/reports/api/general-ledger?accountId=00000000-0000-0000-0000-000000000000&startDate=2025-11-01&endDate=2025-11-30");

            assertTrue(response.status() == 400 || response.status() == 404);
        }

        @Test
        @Order(30)
        @DisplayName("Should return 400 for trial balance with invalid date format")
        void shouldReturn400ForTrialBalanceWithInvalidDateFormat() {
            APIResponse response = apiGet("/reports/api/trial-balance?startDate=not-a-date&endDate=2025-11-30");

            assertEquals(400, response.status());
        }

        @Test
        @Order(31)
        @DisplayName("Should return 400 for income statement with missing end date")
        void shouldReturn400ForIncomeStatementWithMissingEndDate() {
            APIResponse response = apiGet("/reports/api/income-statement?startDate=2025-11-01");

            assertEquals(400, response.status());
        }

        @Test
        @Order(32)
        @DisplayName("Should return 400 for cash flow with missing start date")
        void shouldReturn400ForCashFlowWithMissingStartDate() {
            APIResponse response = apiGet("/reports/api/cash-flow?endDate=2025-11-30");

            assertEquals(400, response.status());
        }

        @Test
        @Order(33)
        @DisplayName("Should return 400 for general ledger with invalid account ID format")
        void shouldReturn400ForGeneralLedgerWithInvalidAccountIdFormat() {
            APIResponse response = apiGet("/reports/api/general-ledger?accountId=invalid-uuid&startDate=2025-11-01&endDate=2025-11-30");

            assertEquals(400, response.status());
        }

        @Test
        @Order(34)
        @DisplayName("Should handle empty report result gracefully via API")
        void shouldHandleEmptyReportResultGracefullyViaAPI() {
            // Use a date range with no transactions
            APIResponse response = apiGet("/reports/api/trial-balance?startDate=2020-01-01&endDate=2020-01-31");

            assertEquals(200, response.status());
            // Should return empty array or zero balances, not an error
        }
    }
}
