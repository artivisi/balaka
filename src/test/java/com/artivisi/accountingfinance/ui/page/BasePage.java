package com.artivisi.accountingfinance.ui.page;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

public abstract class BasePage {

    protected final Page page;
    protected final String baseUrl;

    public BasePage(Page page, String baseUrl) {
        this.page = page;
        this.baseUrl = baseUrl;
    }

    public void waitForPageLoad() {
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    public void clickById(String id) {
        page.click("#" + id);
        waitForPageLoad();
    }

    public void fillById(String id, String value) {
        page.fill("#" + id, value);
    }

    public void selectOption(String selector, String value) {
        page.selectOption(selector, value);
    }

    public void selectOptionById(String id, String value) {
        page.selectOption("#" + id, value);
    }

    public Locator locatorById(String id) {
        return page.locator("#" + id);
    }

    public void assertElementVisible(String id) {
        assertThat(page.locator("#" + id)).isVisible();
    }

    public void assertElementNotVisible(String id) {
        assertThat(page.locator("#" + id)).not().isVisible();
    }

    public void assertElementContainsText(String id, String text) {
        assertThat(page.locator("#" + id)).containsText(text);
    }

    public void clickNavLink(String navId) {
        page.click("#nav-" + navId);
        waitForPageLoad();
    }

    public String getCurrentUrl() {
        return page.url();
    }
}
