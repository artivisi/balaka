package com.artivisi.accountingfinance.functional.service;

import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Service Industry Accounting Tests
 * Tests COA, templates, and transaction functionality for IT Services / PKP company.
 */
@DisplayName("Service Industry - Accounting")
public class ServiceAccountingTest extends PlaywrightTestBase {

    @Test
    @DisplayName("Should display Chart of Accounts")
    void shouldDisplayChartOfAccounts() {
        loginAsAdmin();
        navigateTo("/accounts");
        waitForPageLoad();

        // Verify COA page loads
        assertThat(page.locator("h1")).containsText("Bagan Akun");

        // Verify base accounts exist (header rows from seed data)
        // Use exact match to avoid matching navigation menu items
        assertThat(page.getByText("ASET", new com.microsoft.playwright.Page.GetByTextOptions().setExact(true))).isVisible();
        assertThat(page.getByText("LIABILITAS", new com.microsoft.playwright.Page.GetByTextOptions().setExact(true))).isVisible();
        assertThat(page.getByText("EKUITAS", new com.microsoft.playwright.Page.GetByTextOptions().setExact(true))).isVisible();
        assertThat(page.getByText("PENDAPATAN", new com.microsoft.playwright.Page.GetByTextOptions().setExact(true))).isVisible();
        assertThat(page.getByText("BEBAN", new com.microsoft.playwright.Page.GetByTextOptions().setExact(true))).isVisible();
    }

    @Test
    @DisplayName("Should display Journal Templates")
    void shouldDisplayJournalTemplates() {
        loginAsAdmin();
        navigateTo("/templates");
        waitForPageLoad();

        // Verify templates page loads
        assertThat(page.locator("h1")).containsText("Template");

        // Verify templates exist from seed data (using data-testid)
        assertThat(page.locator("[data-testid='template-card'], [data-testid='template-list']").first()).isVisible();
    }

    @Test
    @DisplayName("Should display Transaction List")
    void shouldDisplayTransactionList() {
        loginAsAdmin();
        navigateTo("/transactions");
        waitForPageLoad();

        // Verify transactions page loads
        assertThat(page.locator("h1")).containsText("Transaksi");

        // Verify test transactions from V811 exist
        assertThat(page.locator("text=TRX-2024-0001")).isVisible();
    }

    @Test
    @DisplayName("Should display Income Statement Report")
    void shouldDisplayIncomeStatement() {
        loginAsAdmin();
        navigateTo("/reports/income-statement");
        waitForPageLoad();

        // Verify income statement page loads
        assertThat(page.locator("h1")).containsText("Laba Rugi");
    }

    @Test
    @DisplayName("Should display Balance Sheet Report")
    void shouldDisplayBalanceSheet() {
        loginAsAdmin();
        navigateTo("/reports/balance-sheet");
        waitForPageLoad();

        // Verify balance sheet page loads
        assertThat(page.locator("h1")).containsText("Neraca");
    }

    @Test
    @DisplayName("Should display Trial Balance Report")
    void shouldDisplayTrialBalance() {
        loginAsAdmin();
        navigateTo("/reports/trial-balance");
        waitForPageLoad();

        // Verify trial balance page loads
        assertThat(page.locator("h1")).containsText("Neraca Saldo");
    }
}
