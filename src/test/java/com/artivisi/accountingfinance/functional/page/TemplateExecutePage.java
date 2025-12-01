package com.artivisi.accountingfinance.functional.page;

import com.microsoft.playwright.Page;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

public class TemplateExecutePage {
    private final Page page;
    private final String baseUrl;

    // Locators
    private static final String PAGE_TITLE = "#page-title";
    private static final String TEMPLATE_NAME = "#template-name";
    private static final String EXECUTE_CONTENT = "#execute-content";
    private static final String TRANSACTION_DATE = "#transactionDate";
    private static final String AMOUNT = "#amount";
    private static final String DESCRIPTION = "#description";
    private static final String PREVIEW_BUTTON = "#btn-preview";
    private static final String EXECUTE_BUTTON = "#btn-execute";
    private static final String PREVIEW_TABLE = "#preview-table";
    private static final String TOTAL_DEBIT = "#total-debit";
    private static final String TOTAL_CREDIT = "#total-credit";
    private static final String BALANCE_STATUS = "#balance-status";
    private static final String JOURNAL_NUMBER = "#journal-number";
    private static final String VIEW_JOURNAL_BUTTON = "#btn-view-journal";
    private static final String ERROR_MESSAGE = "#error-message";

    public TemplateExecutePage(Page page, String baseUrl) {
        this.page = page;
        this.baseUrl = baseUrl;
    }

    public TemplateExecutePage navigate(String templateId) {
        page.navigate(baseUrl + "/templates/" + templateId + "/execute");
        page.waitForLoadState();
        return this;
    }

    public void assertPageTitleVisible() {
        assertThat(page.locator(PAGE_TITLE)).isVisible();
    }

    public void assertPageTitleText(String expectedText) {
        assertThat(page.locator(PAGE_TITLE)).containsText(expectedText);
    }

    public void assertTemplateNameVisible() {
        assertThat(page.locator(TEMPLATE_NAME)).isVisible();
    }

    public void assertTemplateNameText(String expectedText) {
        assertThat(page.locator(TEMPLATE_NAME)).hasText(expectedText);
    }

    public void assertTransactionDateVisible() {
        assertThat(page.locator(TRANSACTION_DATE)).isVisible();
    }

    public void assertAmountInputVisible() {
        assertThat(page.locator(AMOUNT)).isVisible();
    }

    public void assertDescriptionInputVisible() {
        assertThat(page.locator(DESCRIPTION)).isVisible();
    }

    public void assertPreviewButtonVisible() {
        assertThat(page.locator(PREVIEW_BUTTON)).isVisible();
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

    public void clickPreviewButton() {
        page.click(PREVIEW_BUTTON);
        page.waitForTimeout(1500); // Wait for Alpine.js to process and fetch data
    }

    public void clickExecuteButton() {
        page.click(EXECUTE_BUTTON);
        page.waitForTimeout(1000); // Wait for Alpine.js to process
    }

    public void assertPreviewTableVisible() {
        assertThat(page.locator(PREVIEW_TABLE)).isVisible();
    }

    public void assertTotalDebitText(String expectedText) {
        assertThat(page.locator(TOTAL_DEBIT)).containsText(expectedText);
    }

    public void assertTotalCreditText(String expectedText) {
        assertThat(page.locator(TOTAL_CREDIT)).containsText(expectedText);
    }

    public void assertBalanceStatusVisible() {
        assertThat(page.locator(BALANCE_STATUS)).isVisible();
    }

    public void assertBalanced() {
        assertThat(page.locator(BALANCE_STATUS)).containsText("Jurnal Balance");
    }

    public void assertJournalNumberVisible() {
        assertThat(page.locator(JOURNAL_NUMBER)).isVisible();
    }

    public String getJournalNumber() {
        return page.locator(JOURNAL_NUMBER).textContent();
    }

    public void assertViewJournalButtonVisible() {
        assertThat(page.locator(VIEW_JOURNAL_BUTTON)).isVisible();
    }

    public void clickViewJournalButton() {
        page.click(VIEW_JOURNAL_BUTTON);
        page.waitForLoadState();
    }

    public void assertErrorMessageVisible() {
        assertThat(page.locator(ERROR_MESSAGE)).isVisible();
    }

    public void assertErrorMessageText(String expectedText) {
        assertThat(page.locator(ERROR_MESSAGE)).containsText(expectedText);
    }

    public int getPreviewRowCount() {
        // Wait for Alpine.js to render the preview table rows
        page.waitForTimeout(500);
        return page.locator(PREVIEW_TABLE + " tbody tr.preview-row").count();
    }

    public void assertAccountCodeVisible(int rowIndex) {
        // Use nth-of-type instead of nth-child to avoid counting the <template> element
        String selector = PREVIEW_TABLE + " tbody tr.preview-row:nth-of-type(" + (rowIndex + 1) + ") .account-code";
        // The account code should be present and have non-empty text
        assertThat(page.locator(selector)).not().isEmpty();
    }

    public void assertAccountNameVisible(int rowIndex) {
        // Use nth-of-type instead of nth-child to avoid counting the <template> element  
        String selector = PREVIEW_TABLE + " tbody tr.preview-row:nth-of-type(" + (rowIndex + 1) + ") .account-name";
        // The account name should be present and have non-empty text
        assertThat(page.locator(selector)).not().isEmpty();
    }

    public String getAccountCode(int rowIndex) {
        // Use nth-of-type instead of nth-child to avoid counting the <template> element
        String selector = PREVIEW_TABLE + " tbody tr.preview-row:nth-of-type(" + (rowIndex + 1) + ") .account-code";
        return page.locator(selector).textContent().trim();
    }

    public String getAccountName(int rowIndex) {
        // Use nth-of-type instead of nth-child to avoid counting the <template> element
        String selector = PREVIEW_TABLE + " tbody tr.preview-row:nth-of-type(" + (rowIndex + 1) + ") .account-name";
        return page.locator(selector).textContent().trim();
    }

    public void assertAccountCodeNotEmpty(int rowIndex) {
        String accountCode = getAccountCode(rowIndex);
        assertThat(accountCode).isNotEmpty();
    }

    public void assertAccountNameNotEmpty(int rowIndex) {
        String accountName = getAccountName(rowIndex);
        assertThat(accountName).isNotEmpty();
    }
}
