package com.artivisi.accountingfinance.ui.page;

import com.microsoft.playwright.Page;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

public class TransactionsListPage extends BasePage {

    public TransactionsListPage(Page page, String baseUrl) {
        super(page, baseUrl);
    }

    public void navigate() {
        page.navigate(baseUrl + "/transactions");
        waitForPageLoad();
    }

    public void assertPageLoaded() {
        assertElementVisible("page-title");
        assertElementVisible("btn-transaksi-baru");
    }

    public void assertTransactionsTableVisible() {
        assertElementVisible("tabel-transaksi");
    }

    public TransactionFormPage clickNewTransaction() {
        clickById("btn-transaksi-baru");
        return new TransactionFormPage(page, baseUrl);
    }

    public void filterByStatus(String status) {
        selectOptionById("filter-status", status);
        clickById("btn-filter");
    }

    public void filterByTemplate(String templateId) {
        selectOptionById("filter-template", templateId);
        clickById("btn-filter");
    }

    public void filterByDateRange(String startDate, String endDate) {
        fillById("filter-start-date", startDate);
        fillById("filter-end-date", endDate);
        clickById("btn-filter");
    }

    public void searchTransaction(String keyword) {
        fillById("search-transaksi", keyword);
        clickById("btn-cari");
    }

    public void assertTransactionInTable(String transactionNumber) {
        assertThat(page.locator("table tbody")).containsText(transactionNumber);
    }

    public void assertTransactionNotInTable(String transactionNumber) {
        assertThat(page.locator("table tbody")).not().containsText(transactionNumber);
    }

    public TransactionFormPage clickEditTransaction(String transactionNumber) {
        page.locator("table tbody tr").filter(new com.microsoft.playwright.Locator.FilterOptions().setHasText(transactionNumber))
            .locator("a[href*='/edit']").click();
        waitForPageLoad();
        return new TransactionFormPage(page, baseUrl);
    }

    public TransactionFormPage clickViewTransaction(String transactionNumber) {
        page.locator("table tbody tr").filter(new com.microsoft.playwright.Locator.FilterOptions().setHasText(transactionNumber))
            .locator("a[href*='/view']").click();
        waitForPageLoad();
        return new TransactionFormPage(page, baseUrl);
    }

    public void assertFilterOptionsVisible() {
        assertElementVisible("filter-status");
        assertElementVisible("filter-template");
        assertElementVisible("btn-filter");
    }
}
