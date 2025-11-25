package com.artivisi.accountingfinance.functional.page;

import com.microsoft.playwright.Page;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

public class TransactionVoidPage {
    private final Page page;
    private final String baseUrl;

    // Locators
    private static final String PAGE_TITLE = "#page-title";
    private static final String CONTENT = "#void-form-content";
    private static final String VOID_FORM = "#void-form";
    private static final String VOID_NOTES = "#voidNotes";
    private static final String VOID_BUTTON = "#btn-void";
    private static final String CONFIRM_CHECKBOX = "input[name='confirmVoid']";

    public TransactionVoidPage(Page page, String baseUrl) {
        this.page = page;
        this.baseUrl = baseUrl;
    }

    public TransactionVoidPage navigate(String transactionId) {
        page.navigate(baseUrl + "/transactions/" + transactionId + "/void");
        page.waitForLoadState();
        return this;
    }

    public void assertPageLoaded() {
        assertThat(page.locator(PAGE_TITLE)).isVisible();
        assertThat(page.locator(CONTENT)).isVisible();
    }

    public void assertPageTitleText(String expectedText) {
        assertThat(page.locator(PAGE_TITLE)).containsText(expectedText);
    }

    public void assertVoidFormVisible() {
        assertThat(page.locator(VOID_FORM)).isVisible();
    }

    public void assertWarningBannerVisible() {
        assertThat(page.locator("text=Perhatian! Aksi ini tidak dapat dibatalkan")).isVisible();
    }

    public void selectVoidReason(String reason) {
        page.click("input[name='voidReason'][value='" + reason + "']");
    }

    public void fillVoidNotes(String notes) {
        page.fill(VOID_NOTES, notes);
    }

    public void checkConfirmation() {
        page.check(CONFIRM_CHECKBOX);
    }

    public void clickVoidButton() {
        // Handle confirm dialog
        page.onceDialog(dialog -> dialog.accept());
        page.click(VOID_BUTTON);
        // Wait for navigation back to detail page
        page.waitForSelector("[data-testid='status-void']",
            new com.microsoft.playwright.Page.WaitForSelectorOptions().setTimeout(30000));
    }

    public void assertVoidButtonDisabled() {
        assertThat(page.locator(VOID_BUTTON)).hasClass(".*opacity-50.*");
    }

    public void assertTransactionNumberText(String transactionNumber) {
        assertThat(page.locator("text=" + transactionNumber)).isVisible();
    }

    public void assertJournalEntryToBeCancelledVisible() {
        assertThat(page.locator("text=Jurnal yang akan dibatalkan")).isVisible();
    }
}
