package com.artivisi.accountingfinance.functional.page;

import com.microsoft.playwright.Page;

import static org.assertj.core.api.Assertions.assertThat;

public class AmortizationDetailPage {
    private final Page page;
    private final String baseUrl;

    // Locators
    private static final String PAGE_TITLE = "#page-title";
    private static final String SCHEDULE_NAME = "[data-testid='amortization-detail'] h2";
    private static final String SCHEDULE_CODE = "[data-testid='amortization-detail'] p.font-mono";
    private static final String ENTRY_TABLE = "[data-testid='entry-table']";
    private static final String ENTRY_ROW = "[data-testid='entry-table'] tbody tr";
    private static final String POST_ENTRY_BUTTON = "button:has-text('Posting')";
    private static final String SKIP_ENTRY_BUTTON = "button:has-text('Lewati')";
    private static final String CANCEL_BUTTON = "button:has-text('Batalkan')";

    public AmortizationDetailPage(Page page, String baseUrl) {
        this.page = page;
        this.baseUrl = baseUrl;
    }

    public AmortizationDetailPage navigate(String scheduleId) {
        page.navigate(baseUrl + "/amortization/" + scheduleId);
        return this;
    }

    public void assertPageTitleVisible() {
        assertThat(page.locator(PAGE_TITLE).isVisible()).isTrue();
    }

    public void assertScheduleNameText(String expected) {
        assertThat(page.locator(SCHEDULE_NAME).textContent()).contains(expected);
    }

    public void assertScheduleCodeText(String expected) {
        assertThat(page.locator(SCHEDULE_CODE).textContent()).contains(expected);
    }

    public void assertEntryTableVisible() {
        assertThat(page.locator(ENTRY_TABLE).isVisible()).isTrue();
    }

    public int getEntryCount() {
        return page.locator(ENTRY_ROW + "[data-id]").count();
    }

    public void postFirstPendingEntry() {
        page.locator(ENTRY_ROW + ":has-text('Menunggu')").first().locator(POST_ENTRY_BUTTON).click();
        // Wait for network to settle
        page.waitForLoadState();
        page.waitForTimeout(1000);
    }

    public void skipFirstPendingEntry() {
        page.locator(ENTRY_ROW + ":has-text('Menunggu')").first().locator(SKIP_ENTRY_BUTTON).click();
        // Wait for network to settle
        page.waitForLoadState();
        page.waitForTimeout(1000);
    }

    public int getPendingEntryCount() {
        return page.locator(ENTRY_ROW + ":has-text('Menunggu')").count();
    }

    public int getPostedEntryCount() {
        return page.locator(ENTRY_ROW + ":has-text('Terposting')").count();
    }

    public int getSkippedEntryCount() {
        return page.locator(ENTRY_ROW + ":has-text('Dilewati')").count();
    }

    public void clickCancelButton() {
        // Set up dialog handler BEFORE clicking
        page.onceDialog(dialog -> dialog.accept());
        page.click(CANCEL_BUTTON);
        page.waitForLoadState();
        page.waitForTimeout(1000);
    }

    public void assertStatusText(String expected) {
        assertThat(page.locator("[data-testid='amortization-detail']").textContent()).contains(expected);
    }
}
