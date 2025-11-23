package com.artivisi.accountingfinance.functional.page;

import com.microsoft.playwright.Page;

public class LoginPage {
    private final Page page;
    private final String baseUrl;

    // Locators
    private static final String USERNAME_INPUT = "#username";
    private static final String PASSWORD_INPUT = "#password";
    private static final String LOGIN_BUTTON = "#btn-login";

    public LoginPage(Page page, String baseUrl) {
        this.page = page;
        this.baseUrl = baseUrl;
    }

    public LoginPage navigate() {
        page.navigate(baseUrl + "/login");
        return this;
    }

    public void loginAs(String username, String password) {
        page.fill(USERNAME_INPUT, username);
        page.fill(PASSWORD_INPUT, password);
        page.click(LOGIN_BUTTON);
        page.waitForURL("**/dashboard");
    }

    public void loginAsAdmin() {
        loginAs("admin", "admin");
    }
}
