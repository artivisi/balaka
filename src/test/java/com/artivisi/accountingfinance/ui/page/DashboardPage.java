package com.artivisi.accountingfinance.ui.page;

import com.microsoft.playwright.Page;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

public class DashboardPage extends BasePage {

    public DashboardPage(Page page, String baseUrl) {
        super(page, baseUrl);
    }

    public void navigate() {
        page.navigate(baseUrl + "/");
        waitForPageLoad();
    }

    public void assertSummaryCardsVisible() {
        assertElementVisible("card-pendapatan");
        assertElementVisible("card-pengeluaran");
        assertElementVisible("card-laba-bersih");
    }

    public void assertRecentTransactionsVisible() {
        assertElementVisible("recent-transactions");
    }

    public void assertAccountBalancesVisible() {
        assertElementVisible("account-balances");
    }

    public void assertQuickLinksVisible() {
        assertElementVisible("quick-links");
    }

    public void assertUserMenuVisible() {
        assertElementVisible("user-menu");
    }

    public AccountsListPage navigateToAccounts() {
        clickNavLink("akun");
        return new AccountsListPage(page, baseUrl);
    }

    public TransactionsListPage navigateToTransactions() {
        clickNavLink("transactions");
        return new TransactionsListPage(page, baseUrl);
    }

    public ReportPage navigateToTrialBalance() {
        clickNavLink("trial-balance");
        return new ReportPage(page, baseUrl);
    }

    public ReportPage navigateToIncomeStatement() {
        clickNavLink("income-statement");
        return new ReportPage(page, baseUrl);
    }

    public ReportPage navigateToBalanceSheet() {
        clickNavLink("balance-sheet");
        return new ReportPage(page, baseUrl);
    }

    public ReportPage navigateToCashFlow() {
        clickNavLink("cash-flow");
        return new ReportPage(page, baseUrl);
    }

    public void logout() {
        clickById("btn-logout");
    }

    public void assertPageTitle(String expectedTitle) {
        assertThat(page).hasTitle(expectedTitle);
    }
}
