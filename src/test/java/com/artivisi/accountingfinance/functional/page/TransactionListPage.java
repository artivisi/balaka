package com.artivisi.accountingfinance.functional.page;

import com.microsoft.playwright.Page;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

public class TransactionListPage {
    private final Page page;
    private final String baseUrl;

    // Locators
    private static final String PAGE_TITLE = "#page-title";
    private static final String CONTENT = "#transactions-list-content";
    private static final String TRANSACTION_TABLE = "#tabel-transaksi";
    private static final String NEW_TRANSACTION_BUTTON = "#btn-transaksi-baru";
    private static final String FILTER_STATUS = "#filter-status";
    private static final String FILTER_CATEGORY = "#filter-category";
    private static final String FILTER_PROJECT = "#filter-project";
    private static final String FILTER_BUTTON = "#btn-filter";
    private static final String SEARCH_INPUT = "#search-transaksi";
    private static final String TRANSACTION_ROW = "[data-testid^='trx-row-']";

    public TransactionListPage(Page page, String baseUrl) {
        this.page = page;
        this.baseUrl = baseUrl;
    }

    public TransactionListPage navigate() {
        page.navigate(baseUrl + "/transactions", new com.microsoft.playwright.Page.NavigateOptions().setTimeout(30000));
        page.waitForLoadState();
        return this;
    }

    public void assertPageLoaded() {
        assertThat(page.locator(PAGE_TITLE)).isVisible();
        assertThat(page.locator(CONTENT)).isVisible();
    }

    public void assertPageTitleVisible() {
        assertThat(page.locator(PAGE_TITLE)).isVisible();
    }

    public void assertPageTitleText(String expectedText) {
        assertThat(page.locator(PAGE_TITLE)).containsText(expectedText);
    }

    public void assertTransactionTableVisible() {
        assertThat(page.locator(TRANSACTION_TABLE)).isVisible();
    }

    public void assertNewTransactionButtonVisible() {
        assertThat(page.locator(NEW_TRANSACTION_BUTTON)).isVisible();
    }

    public void clickNewTransactionButton() {
        page.click(NEW_TRANSACTION_BUTTON);
        page.waitForLoadState();
    }

    public void selectTemplate(String templateName) {
        // Open dropdown
        page.click(NEW_TRANSACTION_BUTTON);
        page.waitForTimeout(200);
        // Click template link
        page.click("text=" + templateName);
        page.waitForLoadState();
    }

    public int getTransactionCount() {
        return page.locator(TRANSACTION_ROW).count();
    }

    public void assertTransactionInTable(String transactionNumber) {
        assertThat(page.locator("[data-testid='trx-row-" + transactionNumber + "']")).isVisible();
    }

    public void assertTransactionNotInTable(String transactionNumber) {
        assertThat(page.locator("[data-testid='trx-row-" + transactionNumber + "']")).not().isVisible();
    }

    public void clickTransaction(String transactionNumber) {
        page.click("[data-testid='trx-row-" + transactionNumber + "']");
        page.waitForLoadState();
    }

    public void filterByStatus(String status) {
        page.selectOption(FILTER_STATUS, status);
        page.click(FILTER_BUTTON);
        page.waitForLoadState();
    }

    public void filterByCategory(String category) {
        page.selectOption(FILTER_CATEGORY, category);
        page.click(FILTER_BUTTON);
        page.waitForLoadState();
    }

    public void filterByProject(String projectCode) {
        page.selectOption(FILTER_PROJECT, projectCode);
        page.click(FILTER_BUTTON);
        // Wait for URL to contain the projectCode parameter
        page.waitForURL("**/transactions**projectCode=" + projectCode + "**",
            new com.microsoft.playwright.Page.WaitForURLOptions().setTimeout(10000));
        page.waitForLoadState();
    }

    public boolean hasProjectFilter() {
        return page.locator(FILTER_PROJECT).count() > 0;
    }

    public void searchTransaction(String query) {
        page.fill(SEARCH_INPUT, query);
        page.click(FILTER_BUTTON);
        page.waitForLoadState();
    }

    public void assertFilterOptionsVisible() {
        assertThat(page.locator(FILTER_STATUS)).isVisible();
        assertThat(page.locator(FILTER_CATEGORY)).isVisible();
    }

    public void assertEmptyStateVisible() {
        assertThat(page.locator("text=Belum ada transaksi")).isVisible();
    }

    public void assertEmptyStateNotVisible() {
        assertThat(page.locator("text=Belum ada transaksi")).not().isVisible();
    }
}
