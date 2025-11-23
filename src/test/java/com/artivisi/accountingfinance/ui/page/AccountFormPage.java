package com.artivisi.accountingfinance.ui.page;

import com.microsoft.playwright.Page;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

public class AccountFormPage extends BasePage {

    public AccountFormPage(Page page, String baseUrl) {
        super(page, baseUrl);
    }

    public void assertPageLoaded() {
        assertElementVisible("form-akun");
    }

    public void fillAccountCode(String code) {
        fillById("accountCode", code);
    }

    public void fillAccountName(String name) {
        fillById("accountName", name);
    }

    public void selectAccountType(String type) {
        selectOptionById("accountType", type);
    }

    public void selectParentAccount(String parentId) {
        selectOptionById("parentAccount", parentId);
    }

    public void fillDescription(String description) {
        fillById("description", description);
    }

    public AccountsListPage save() {
        clickById("btn-simpan");
        return new AccountsListPage(page, baseUrl);
    }

    public AccountsListPage cancel() {
        clickById("btn-batal");
        return new AccountsListPage(page, baseUrl);
    }

    public void assertValidationErrorVisible() {
        assertThat(page.locator(".invalid-feedback:visible")).isVisible();
    }

    public void assertSuccessMessageVisible() {
        assertThat(page.locator(".alert-success")).isVisible();
    }
}
