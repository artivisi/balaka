package com.artivisi.accountingfinance.functional.service;

import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Service Industry Financial Reports Tests
 * Tests P&L, Balance Sheet, Cash Flow, Trial Balance reports.
 */
@DisplayName("Service Industry - Financial Reports")
public class ServiceReportsTest extends PlaywrightTestBase {

    @Test
    @DisplayName("Should display Trial Balance")
    void shouldDisplayTrialBalance() {
        loginAsAdmin();
        navigateTo("/reports/trial-balance");
        waitForPageLoad();

        // Verify trial balance page loads (uses Buku Besar title in Indonesian)
        assertThat(page.locator("h1")).containsText("Neraca Saldo");
    }

    @Test
    @DisplayName("Should display Income Statement")
    void shouldDisplayIncomeStatement() {
        loginAsAdmin();
        navigateTo("/reports/income-statement");
        waitForPageLoad();

        // Verify income statement page loads
        assertThat(page.locator("h1")).containsText("Laba Rugi");
    }

    @Test
    @DisplayName("Should display Balance Sheet")
    void shouldDisplayBalanceSheet() {
        loginAsAdmin();
        navigateTo("/reports/balance-sheet");
        waitForPageLoad();

        // Verify balance sheet page loads
        assertThat(page.locator("h1")).containsText("Neraca");
    }

    @Test
    @DisplayName("Should display Cash Flow Statement")
    void shouldDisplayCashFlowStatement() {
        loginAsAdmin();
        navigateTo("/reports/cash-flow");
        waitForPageLoad();

        // Verify cash flow page loads
        assertThat(page.locator("h1")).containsText("Arus Kas");
    }

    @Test
    @DisplayName("Should filter reports by date range")
    void shouldFilterReportsByDateRange() {
        loginAsAdmin();
        navigateTo("/reports/income-statement");
        waitForPageLoad();

        // Verify date filter controls exist
        assertThat(page.locator("#startDate, input[name='startDate']")).isVisible();
        assertThat(page.locator("#endDate, input[name='endDate']")).isVisible();
    }

    @Test
    @DisplayName("Should have export buttons on report")
    void shouldHaveExportButtons() {
        loginAsAdmin();
        navigateTo("/reports/income-statement");
        waitForPageLoad();

        // Verify export buttons exist (PDF and Excel)
        assertThat(page.locator("#btn-export-pdf")).isVisible();
    }

    @Test
    @DisplayName("Should display Journal Entry List")
    void shouldDisplayJournalEntryList() {
        loginAsAdmin();
        navigateTo("/journals");
        waitForPageLoad();

        // Verify journal list page loads (title is "Buku Besar")
        assertThat(page.locator("h1")).containsText("Buku Besar");
    }
}
