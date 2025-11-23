package com.artivisi.accountingfinance.functional.page;

import com.microsoft.playwright.Page;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

public class ChartOfAccountsPage {
    private final Page page;
    private final String baseUrl;

    // Locators - all ID-based
    private static final String PAGE_TITLE = "#page-title";
    private static final String ADD_ACCOUNT_BUTTON = "#btn-tambah-akun";
    private static final String ACCOUNTS_TABLE = "#accounts-table";

    public ChartOfAccountsPage(Page page, String baseUrl) {
        this.page = page;
        this.baseUrl = baseUrl;
    }

    public ChartOfAccountsPage navigate() {
        page.navigate(baseUrl + "/accounts");
        return this;
    }

    // Assertions
    public void assertPageTitleVisible() {
        assertThat(page.locator(PAGE_TITLE)).isVisible();
    }

    public void assertPageTitleText(String expectedText) {
        assertThat(page.locator(PAGE_TITLE)).hasText(expectedText);
    }

    public void assertAddButtonVisible() {
        assertThat(page.locator(ADD_ACCOUNT_BUTTON)).isVisible();
    }

    public void assertAccountsTableVisible() {
        assertThat(page.locator(ACCOUNTS_TABLE)).isVisible();
    }

    // Account row assertions - using dynamic IDs based on account code
    // Account code "1" becomes ID "account-row-1"
    // Account code "1.1" becomes ID "account-row-1-1"
    // Account code "1.1.01" becomes ID "account-row-1-1-01"

    public void assertAccountRowVisible(String accountCode) {
        String id = accountCodeToId(accountCode);
        assertThat(page.locator("#account-row-" + id)).isVisible();
    }

    public void assertAccountNameVisible(String accountCode, String expectedName) {
        String id = accountCodeToId(accountCode);
        assertThat(page.locator("#account-name-" + id)).isVisible();
        assertThat(page.locator("#account-name-" + id)).hasText(expectedName);
    }

    public void assertAccountCodeVisible(String accountCode) {
        String id = accountCodeToId(accountCode);
        assertThat(page.locator("#account-code-" + id)).isVisible();
    }

    public void assertAccountTypeVisible(String accountCode, String expectedType) {
        String id = accountCodeToId(accountCode);
        assertThat(page.locator("#account-type-" + id)).containsText(expectedType);
    }

    public void assertEditButtonVisible(String accountCode) {
        String id = accountCodeToId(accountCode);
        assertThat(page.locator("#btn-edit-" + id)).isVisible();
    }

    public void assertExpandButtonVisible(String accountCode) {
        String id = accountCodeToId(accountCode);
        assertThat(page.locator("#btn-expand-" + id)).isVisible();
    }

    // Root accounts verification
    public void assertAllRootAccountsVisible() {
        assertAccountRowVisible("1");
        assertAccountRowVisible("2");
        assertAccountRowVisible("3");
        assertAccountRowVisible("4");
        assertAccountRowVisible("5");
    }

    public void assertRootAccountsWithNames() {
        assertAccountNameVisible("1", "ASET");
        assertAccountNameVisible("2", "LIABILITAS");
        assertAccountNameVisible("3", "EKUITAS");
        assertAccountNameVisible("4", "PENDAPATAN");
        assertAccountNameVisible("5", "BEBAN");
    }

    public void assertRootAccountsWithTypes() {
        assertAccountTypeVisible("1", "Aset");
        assertAccountTypeVisible("2", "Liabilitas");
        assertAccountTypeVisible("3", "Ekuitas");
        assertAccountTypeVisible("4", "Pendapatan");
        assertAccountTypeVisible("5", "Beban");
    }

    // Actions
    public void clickAddAccount() {
        page.click(ADD_ACCOUNT_BUTTON);
    }

    public void clickExpandAccount(String accountCode) {
        String id = accountCodeToId(accountCode);
        page.click("#btn-expand-" + id);
    }

    public void clickEditAccount(String accountCode) {
        String id = accountCodeToId(accountCode);
        page.click("#btn-edit-" + id);
    }

    // Helper method to convert account code to ID format
    // "1.1.01" -> "1-1-01"
    private String accountCodeToId(String accountCode) {
        return accountCode.replace(".", "-");
    }
}
