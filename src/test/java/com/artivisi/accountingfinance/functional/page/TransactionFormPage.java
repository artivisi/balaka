package com.artivisi.accountingfinance.functional.page;

import com.microsoft.playwright.Page;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

public class TransactionFormPage {
    private final Page page;
    private final String baseUrl;

    // Locators
    private static final String PAGE_TITLE = "#page-title";
    private static final String CONTENT = "#transaction-form-content";
    private static final String FORM = "#form-transaksi";
    private static final String TRANSACTION_DATE = "#transactionDate";
    private static final String AMOUNT = "#amount";
    private static final String DESCRIPTION = "#description";
    private static final String REFERENCE_NUMBER = "#referenceNumber";
    private static final String NOTES = "#notes";
    private static final String SAVE_DRAFT_BUTTON = "#btn-simpan-draft";
    private static final String SAVE_POST_BUTTON = "#btn-simpan-posting";
    private static final String JOURNAL_PREVIEW = "#journal-preview";

    public TransactionFormPage(Page page, String baseUrl) {
        this.page = page;
        this.baseUrl = baseUrl;
    }

    public TransactionFormPage navigate(String templateId) {
        page.navigate(baseUrl + "/transactions/new?templateId=" + templateId);
        page.waitForLoadState();
        return this;
    }

    public TransactionFormPage navigateToNew() {
        page.navigate(baseUrl + "/transactions/new");
        page.waitForLoadState();
        return this;
    }

    public TransactionFormPage navigateToEdit(String transactionId) {
        page.navigate(baseUrl + "/transactions/" + transactionId + "/edit");
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

    public void assertFormVisible() {
        assertThat(page.locator(FORM)).isVisible();
    }

    public void assertTemplateSelectionVisible() {
        // When no template is selected, show template selection
        assertThat(page.locator("h3:has-text('Pilih Template')")).isVisible();
    }

    public void selectTemplateFromList(String templateName) {
        page.click("text=" + templateName);
        page.waitForLoadState();
    }

    public void fillTransactionDate(String date) {
        page.fill(TRANSACTION_DATE, date);
    }

    public void fillAmount(String amount) {
        page.fill(AMOUNT, amount);
    }

    public void fillDescription(String description) {
        page.fill(DESCRIPTION, description);
    }

    public void fillReferenceNumber(String refNumber) {
        page.fill(REFERENCE_NUMBER, refNumber);
    }

    public void fillNotes(String notes) {
        page.fill(NOTES, notes);
    }

    public void assertJournalPreviewVisible() {
        assertThat(page.locator(JOURNAL_PREVIEW)).isVisible();
    }

    public void clickSaveDraft() {
        page.click(SAVE_DRAFT_BUTTON);
        // Wait for navigation to detail page by checking for detail page element
        page.waitForSelector("#transaction-detail-content",
            new com.microsoft.playwright.Page.WaitForSelectorOptions().setTimeout(30000));
    }

    public void clickSaveAndPost() {
        page.click(SAVE_POST_BUTTON);
        // Wait for navigation to detail page by checking for detail page element
        page.waitForSelector("#transaction-detail-content",
            new com.microsoft.playwright.Page.WaitForSelectorOptions().setTimeout(30000));
    }

    public void assertSaveDraftButtonVisible() {
        assertThat(page.locator(SAVE_DRAFT_BUTTON)).isVisible();
    }

    public void assertSavePostButtonVisible() {
        assertThat(page.locator(SAVE_POST_BUTTON)).isVisible();
    }

    public String getTransactionDateValue() {
        return page.inputValue(TRANSACTION_DATE);
    }

    public String getDescriptionValue() {
        return page.inputValue(DESCRIPTION);
    }
}
