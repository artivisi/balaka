package com.artivisi.accountingfinance.ui.page;

import com.microsoft.playwright.Page;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

public class LoginPage extends BasePage {

    public LoginPage(Page page, String baseUrl) {
        super(page, baseUrl);
    }

    public void navigate() {
        page.navigate(baseUrl + "/login");
        waitForPageLoad();
    }

    public void fillUsername(String username) {
        fillById("username", username);
    }

    public void fillPassword(String password) {
        fillById("password", password);
    }

    public DashboardPage login(String username, String password) {
        fillUsername(username);
        fillPassword(password);
        clickById("btn-login");
        return new DashboardPage(page, baseUrl);
    }

    public void assertLoginFormVisible() {
        assertThat(page.locator("form")).isVisible();
        assertElementVisible("username");
        assertElementVisible("password");
        assertElementVisible("btn-login");
    }

    public void assertLoginErrorVisible() {
        assertThat(page.locator(".alert-danger")).isVisible();
    }
}
