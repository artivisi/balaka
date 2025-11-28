package com.artivisi.accountingfinance.functional.page;

import com.microsoft.playwright.Page;

import static org.assertj.core.api.Assertions.assertThat;

public class TaxCalendarListPage {
    private final Page page;
    private final String baseUrl;

    private static final String PAGE_TITLE = "#page-title";
    private static final String MONTH_SELECTOR = "#month-selector";
    private static final String YEAR_SELECTOR = "#year-selector";
    private static final String SUMMARY_CARDS = "[data-testid='summary-cards']";
    private static final String TOTAL_COUNT = "[data-testid='total-count']";
    private static final String COMPLETED_COUNT = "[data-testid='completed-count']";
    private static final String OVERDUE_COUNT = "[data-testid='overdue-count']";
    private static final String DUE_SOON_COUNT = "[data-testid='due-soon-count']";
    private static final String CHECKLIST_WRAPPER = "#checklist-wrapper";
    private static final String MARK_COMPLETE_BUTTON = ".btn-mark-complete";
    private static final String COMPLETE_MODAL = "#complete-modal";
    private static final String COMPLETE_FORM = "#complete-form";

    public TaxCalendarListPage(Page page, String baseUrl) {
        this.page = page;
        this.baseUrl = baseUrl;
    }

    public TaxCalendarListPage navigate() {
        page.navigate(baseUrl + "/tax-calendar");
        return this;
    }

    public TaxCalendarListPage navigateWithPeriod(int year, int month) {
        page.navigate(baseUrl + "/tax-calendar?year=" + year + "&month=" + month);
        return this;
    }

    public void assertPageTitleVisible() {
        assertThat(page.locator(PAGE_TITLE).isVisible()).isTrue();
    }

    public void assertPageTitleText(String expected) {
        assertThat(page.locator(PAGE_TITLE).textContent()).contains(expected);
    }

    public void assertSummaryCardsVisible() {
        assertThat(page.locator(SUMMARY_CARDS).isVisible()).isTrue();
    }

    public void assertChecklistVisible() {
        assertThat(page.locator(CHECKLIST_WRAPPER).isVisible()).isTrue();
    }

    public int getTotalCount() {
        String text = page.locator(TOTAL_COUNT).textContent();
        return Integer.parseInt(text.trim());
    }

    public int getCompletedCount() {
        String text = page.locator(COMPLETED_COUNT).textContent();
        return Integer.parseInt(text.trim());
    }

    public int getOverdueCount() {
        String text = page.locator(OVERDUE_COUNT).textContent();
        return Integer.parseInt(text.trim());
    }

    public int getDueSoonCount() {
        String text = page.locator(DUE_SOON_COUNT).textContent();
        return Integer.parseInt(text.trim());
    }

    public void selectMonth(int month) {
        page.selectOption(MONTH_SELECTOR, String.valueOf(month));
        // Wait for HTMX request to complete
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
    }

    public void selectYear(int year) {
        page.selectOption(YEAR_SELECTOR, String.valueOf(year));
        // Wait for HTMX request to complete
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
    }

    public boolean hasDeadlineWithName(String name) {
        return page.locator(CHECKLIST_WRAPPER).textContent().contains(name);
    }

    public void clickFirstMarkCompleteButton() {
        page.click(MARK_COMPLETE_BUTTON + ":first-child");
    }

    public void assertModalVisible() {
        assertThat(page.locator(COMPLETE_MODAL).isVisible()).isTrue();
    }

    public void assertModalHidden() {
        assertThat(page.locator(COMPLETE_MODAL).isHidden()).isTrue();
    }

    public void fillCompletedDate(String date) {
        page.fill(COMPLETE_FORM + " [name='completedDate']", date);
    }

    public void fillReferenceNumber(String reference) {
        page.fill(COMPLETE_FORM + " [name='referenceNumber']", reference);
    }

    public void submitCompleteForm() {
        page.click(COMPLETE_FORM + " button[type='submit']");
        page.waitForLoadState();
    }

    public String getChecklistContent() {
        return page.locator(CHECKLIST_WRAPPER).textContent();
    }
}
