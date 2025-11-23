package com.artivisi.accountingfinance.ui.page;

import com.microsoft.playwright.Page;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

public class AccountsListPage extends BasePage {

    public AccountsListPage(Page page, String baseUrl) {
        super(page, baseUrl);
    }

    public void navigate() {
        page.navigate(baseUrl + "/accounts");
        waitForPageLoad();
    }

    public void assertPageLoaded() {
        assertElementVisible("page-title");
        assertElementVisible("btn-tambah-akun");
    }

    public void assertAccountsTableVisible() {
        assertElementVisible("tabel-akun");
    }

    public AccountFormPage clickAddAccount() {
        clickById("btn-tambah-akun");
        return new AccountFormPage(page, baseUrl);
    }

    public void filterByType(String type) {
        selectOptionById("filter-type", type);
        clickById("btn-filter");
    }

    public void searchAccount(String keyword) {
        fillById("search-akun", keyword);
        clickById("btn-cari");
    }

    public void clearSearch() {
        fillById("search-akun", "");
        clickById("btn-cari");
    }

    public void assertAccountInTable(String accountCode) {
        assertThat(page.locator("table tbody")).containsText(accountCode);
    }

    public void assertAccountNotInTable(String accountCode) {
        assertThat(page.locator("table tbody")).not().containsText(accountCode);
    }

    public AccountFormPage clickEditAccount(String accountCode) {
        page.locator("table tbody tr").filter(new com.microsoft.playwright.Locator.FilterOptions().setHasText(accountCode))
            .locator("a[href*='/edit']").click();
        waitForPageLoad();
        return new AccountFormPage(page, baseUrl);
    }

    public void assertFilterOptionsVisible() {
        assertElementVisible("filter-type");
        assertElementVisible("btn-filter");
    }

    public void assertSearchVisible() {
        assertElementVisible("search-akun");
        assertElementVisible("btn-cari");
    }
}
