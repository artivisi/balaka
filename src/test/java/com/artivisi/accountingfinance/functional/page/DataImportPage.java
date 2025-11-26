package com.artivisi.accountingfinance.functional.page;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;

import java.nio.file.Path;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

public class DataImportPage {
    private final Page page;
    private final String baseUrl;

    // Index page locators
    private static final String PAGE_TITLE = "#page-title";
    private static final String COA_IMPORT_LINK = "a[href='/import/coa']";
    private static final String TEMPLATE_IMPORT_LINK = "a[href='/import/templates']";
    private static final String COA_SAMPLE_DOWNLOAD = "a[href='/import/download/coa-sample']";
    private static final String TEMPLATE_SAMPLE_DOWNLOAD = "a[href='/import/download/template-sample']";
    private static final String SUCCESS_MESSAGE = ".bg-green-100";
    private static final String ERROR_MESSAGE = ".bg-red-100";

    // COA Import page locators
    private static final String FILE_INPUT = "input[type='file'][name='file']";
    private static final String CLEAR_EXISTING_CHECKBOX = "input[name='clearExisting']";
    private static final String PREVIEW_BUTTON = "form button[type='submit']:has-text('Preview')";
    private static final String PREVIEW_AREA = "#preview-area";
    private static final String IMPORT_BUTTON = "button[type='submit']:has-text('Import Sekarang')";

    // Preview fragment locators
    private static final String PREVIEW_FILE_NAME = ".bg-gray-50 .font-medium.text-gray-900";
    private static final String PREVIEW_RECORD_COUNT = ".font-medium.text-gray-900";
    private static final String PREVIEW_NEW_COUNT = ".font-medium.text-green-600";
    private static final String PREVIEW_EXISTING_COUNT = ".font-medium.text-yellow-600";
    private static final String PREVIEW_ERRORS = ".bg-red-50";

    // Result page locators
    private static final String RESULT_SUCCESS = "[data-testid='import-success']";
    private static final String RESULT_FAILURE = "[data-testid='import-failure']";
    private static final String RESULT_TOTAL_COUNT = ".bg-gray-50 .text-2xl";
    private static final String RESULT_SUCCESS_COUNT = ".bg-green-50 .text-2xl";
    private static final String RESULT_ERROR_COUNT = ".bg-red-50 .text-2xl";

    public DataImportPage(Page page, String baseUrl) {
        this.page = page;
        this.baseUrl = baseUrl;
    }

    public DataImportPage navigate() {
        page.navigate(baseUrl + "/import");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        return this;
    }

    public DataImportPage navigateToCOAImport() {
        page.navigate(baseUrl + "/import/coa");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        return this;
    }

    public DataImportPage navigateToTemplateImport() {
        page.navigate(baseUrl + "/import/templates");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        return this;
    }

    public void assertPageTitleVisible() {
        assertThat(page.locator(PAGE_TITLE)).isVisible();
    }

    public void assertPageTitleText(String expectedTitle) {
        assertThat(page.locator(PAGE_TITLE)).hasText(expectedTitle);
    }

    public void assertCOAImportLinkVisible() {
        assertThat(page.locator(COA_IMPORT_LINK)).isVisible();
    }

    public void assertTemplateImportLinkVisible() {
        assertThat(page.locator(TEMPLATE_IMPORT_LINK)).isVisible();
    }

    public void assertCOASampleDownloadVisible() {
        assertThat(page.locator(COA_SAMPLE_DOWNLOAD)).isVisible();
    }

    public void assertTemplateSampleDownloadVisible() {
        assertThat(page.locator(TEMPLATE_SAMPLE_DOWNLOAD)).isVisible();
    }

    public void clickCOAImportLink() {
        page.click(COA_IMPORT_LINK);
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    public void clickTemplateImportLink() {
        page.click(TEMPLATE_IMPORT_LINK);
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    // COA Import page methods

    public void assertFileInputVisible() {
        assertThat(page.locator(FILE_INPUT)).isAttached();
    }

    public void assertPreviewButtonVisible() {
        assertThat(page.locator(PREVIEW_BUTTON)).isVisible();
    }

    public void uploadFile(Path filePath) {
        page.locator(FILE_INPUT).setInputFiles(filePath);
    }

    public void clickPreview() {
        page.locator(PREVIEW_BUTTON).click();
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    public void assertPreviewAreaVisible() {
        assertThat(page.locator(PREVIEW_AREA)).isVisible();
    }

    public void assertPreviewHasContent() {
        // Wait for HTMX to load the preview
        page.waitForSelector(PREVIEW_AREA + " .bg-white", new Page.WaitForSelectorOptions().setTimeout(10000));
        Locator previewContent = page.locator(PREVIEW_AREA + " .bg-white");
        assertThat(previewContent).isVisible();
    }

    public void assertPreviewShowsRecordCount(int expectedCount) {
        Locator recordCountElement = page.locator(PREVIEW_AREA + " span:has-text('" + expectedCount + "')");
        assertThat(recordCountElement).isVisible();
    }

    public void checkClearExisting() {
        page.locator(PREVIEW_AREA + " " + CLEAR_EXISTING_CHECKBOX).check();
    }

    public void uploadFileInPreview(Path filePath) {
        page.locator(PREVIEW_AREA + " input[type='file'][name='file']").setInputFiles(filePath);
    }

    public void checkClearExistingInPreview() {
        page.locator(PREVIEW_AREA + " input[name='clearExisting']").check();
    }

    public void clickImportNow() {
        // Click the submit button in the preview form
        page.locator(PREVIEW_AREA + " button[type='submit']:has-text('Import Sekarang')").click();
        // Wait for redirect to result page
        page.waitForURL("**/import/result**");
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    // Result page methods

    public void assertResultPageVisible() {
        // Wait for the result page to load
        page.waitForSelector("h1:has-text('Hasil Import')", new Page.WaitForSelectorOptions().setTimeout(10000));
        assertThat(page.locator("h1:has-text('Hasil Import')")).isVisible();
    }

    public void assertImportSuccess() {
        // Wait for the success element to appear
        page.waitForSelector(RESULT_SUCCESS, new Page.WaitForSelectorOptions().setTimeout(10000));
        assertThat(page.locator(RESULT_SUCCESS)).isVisible();
    }

    public void assertImportFailed() {
        page.waitForSelector(RESULT_FAILURE, new Page.WaitForSelectorOptions().setTimeout(10000));
        assertThat(page.locator(RESULT_FAILURE)).isVisible();
    }

    public void assertSuccessMessage(String expectedMessage) {
        assertThat(page.locator(SUCCESS_MESSAGE)).containsText(expectedMessage);
    }

    public void assertErrorMessage(String expectedMessage) {
        assertThat(page.locator(ERROR_MESSAGE)).containsText(expectedMessage);
    }

    // Navigation helper
    public void assertNavigationImportLinkVisible() {
        assertThat(page.locator("#nav-import")).isVisible();
    }
}
