package com.artivisi.accountingfinance.functional.page;

import com.microsoft.playwright.Page;

import static org.assertj.core.api.Assertions.assertThat;

public class AmortizationListPage {
    private final Page page;
    private final String baseUrl;

    // Locators
    private static final String PAGE_TITLE = "#page-title";
    private static final String SCHEDULE_TABLE = "[data-testid='schedule-table']";
    private static final String NEW_SCHEDULE_BUTTON = "[data-testid='btn-new-schedule']";
    private static final String SEARCH_INPUT = "[data-testid='search-input']";
    private static final String SEARCH_BUTTON = "[data-testid='search-button']";
    private static final String SCHEDULE_ROW = "[data-testid='schedule-table'] tbody tr";

    public AmortizationListPage(Page page, String baseUrl) {
        this.page = page;
        this.baseUrl = baseUrl;
    }

    public AmortizationListPage navigate() {
        page.navigate(baseUrl + "/amortization");
        return this;
    }

    public void assertPageTitleVisible() {
        assertThat(page.locator(PAGE_TITLE).isVisible()).isTrue();
    }

    public void assertPageTitleText(String expected) {
        assertThat(page.locator(PAGE_TITLE).textContent()).contains(expected);
    }

    public void assertTableVisible() {
        assertThat(page.locator(SCHEDULE_TABLE).isVisible()).isTrue();
    }

    public int getScheduleCount() {
        return page.locator(SCHEDULE_ROW + "[data-id]").count();
    }

    public void clickNewScheduleButton() {
        page.click(NEW_SCHEDULE_BUTTON);
    }

    public void search(String query) {
        page.fill(SEARCH_INPUT, query);
        page.click(SEARCH_BUTTON);
        page.waitForLoadState();
    }

    public void clickSchedule(String code) {
        page.locator(SCHEDULE_ROW + ":has-text('" + code + "')").locator("a:has-text('Detail')").click();
    }

    public void assertScheduleVisible(String code) {
        assertThat(page.locator(SCHEDULE_ROW + ":has-text('" + code + "')").isVisible()).isTrue();
    }

    public void assertScheduleNotVisible(String code) {
        assertThat(page.locator(SCHEDULE_ROW + ":has-text('" + code + "')").count()).isZero();
    }
}
