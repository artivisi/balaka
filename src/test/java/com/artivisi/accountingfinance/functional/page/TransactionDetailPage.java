package com.artivisi.accountingfinance.functional.page;

import com.microsoft.playwright.Page;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

public class TransactionDetailPage {
    private final Page page;
    private final String baseUrl;

    // Locators
    private static final String PAGE_TITLE = "#page-title";
    private static final String CONTENT = "#transaction-detail-content";
    private static final String TRANSACTION_NUMBER = "#transaction-number";
    private static final String TRANSACTION_STATUS = "#transaction-status";
    private static final String JOURNAL_ENTRIES = "#journal-entries";
    private static final String POST_BUTTON = "#btn-post";
    private static final String DELETE_BUTTON = "#btn-delete";
    private static final String VOID_LINK = "a:has-text('Void Transaksi')";
    private static final String EDIT_LINK = "a:has-text('Edit')";

    public TransactionDetailPage(Page page, String baseUrl) {
        this.page = page;
        this.baseUrl = baseUrl;
    }

    public TransactionDetailPage navigate(String transactionId) {
        page.navigate(baseUrl + "/transactions/" + transactionId);
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

    public void assertTransactionNumberVisible() {
        assertThat(page.locator(TRANSACTION_NUMBER)).isVisible();
    }

    public String getTransactionNumber() {
        return page.locator(TRANSACTION_NUMBER).textContent();
    }

    public void assertTransactionStatusText(String status) {
        assertThat(page.locator(TRANSACTION_STATUS)).containsText(status);
    }

    public void assertJournalEntriesVisible() {
        assertThat(page.locator(JOURNAL_ENTRIES)).isVisible();
    }

    public void assertDraftStatus() {
        assertThat(page.locator("[data-testid='status-draft']")).isVisible();
    }

    public void assertPostedStatus() {
        assertThat(page.locator("[data-testid='status-posted']")).isVisible();
    }

    public void assertVoidStatus() {
        assertThat(page.locator("[data-testid='status-void']")).isVisible();
    }

    public void assertEditButtonVisible() {
        assertThat(page.locator(EDIT_LINK)).isVisible();
    }

    public void assertEditButtonNotVisible() {
        assertThat(page.locator(EDIT_LINK)).not().isVisible();
    }

    public void assertPostButtonVisible() {
        assertThat(page.locator(POST_BUTTON)).isVisible();
    }

    public void assertPostButtonNotVisible() {
        assertThat(page.locator(POST_BUTTON)).not().isVisible();
    }

    public void assertDeleteButtonVisible() {
        assertThat(page.locator(DELETE_BUTTON)).isVisible();
    }

    public void assertDeleteButtonNotVisible() {
        assertThat(page.locator(DELETE_BUTTON)).not().isVisible();
    }

    public void assertVoidButtonVisible() {
        assertThat(page.locator(VOID_LINK)).isVisible();
    }

    public void assertVoidButtonNotVisible() {
        assertThat(page.locator(VOID_LINK)).not().isVisible();
    }

    public void clickEditButton() {
        page.click(EDIT_LINK);
        page.waitForLoadState();
    }

    public void clickPostButton() {
        // Handle confirm dialog
        page.onceDialog(dialog -> dialog.accept());
        page.click(POST_BUTTON);
        page.waitForLoadState();
    }

    public void clickDeleteButton() {
        // Handle confirm dialog
        page.onceDialog(dialog -> dialog.accept());
        page.click(DELETE_BUTTON);
        page.waitForLoadState();
    }

    public void clickVoidButton() {
        page.click(VOID_LINK);
        page.waitForLoadState();
    }

    public void assertAmountText(String amount) {
        assertThat(page.locator("text=Rp " + amount)).isVisible();
    }

    public void assertDescriptionText(String description) {
        assertThat(page.locator("text=" + description)).isVisible();
    }
}
