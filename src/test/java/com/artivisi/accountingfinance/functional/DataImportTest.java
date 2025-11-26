package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.page.DataImportPage;
import com.artivisi.accountingfinance.functional.page.LoginPage;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.jdbc.Sql;

import java.nio.file.Path;
import java.nio.file.Paths;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@DisplayName("Data Import - Functional Tests")
class DataImportTest extends PlaywrightTestBase {

    private LoginPage loginPage;
    private DataImportPage importPage;

    private static final Path COA_TEST_FILE = Paths.get("src/test/resources/import-test-data/coa-test.json");
    private static final Path TEMPLATE_TEST_FILE = Paths.get("src/test/resources/import-test-data/template-test.json");

    @BeforeEach
    void setUp() {
        loginPage = new LoginPage(page, baseUrl());
        importPage = new DataImportPage(page, baseUrl());

        loginPage.navigate().loginAsAdmin();
    }

    @Test
    @DisplayName("Should display import page with navigation link")
    void shouldDisplayImportPageWithNavigation() {
        importPage.navigate();

        importPage.assertPageTitleVisible();
        importPage.assertPageTitleText("Import Data");
        importPage.assertNavigationImportLinkVisible();
    }

    @Test
    @DisplayName("Should display COA and Template import links")
    void shouldDisplayImportLinks() {
        importPage.navigate();

        importPage.assertCOAImportLinkVisible();
        importPage.assertTemplateImportLinkVisible();
    }

    @Test
    @DisplayName("Should display download links for sample files")
    void shouldDisplayDownloadLinks() {
        importPage.navigate();

        importPage.assertCOASampleDownloadVisible();
        importPage.assertTemplateSampleDownloadVisible();
    }

    @Test
    @DisplayName("Should navigate to COA import page")
    void shouldNavigateToCOAImportPage() {
        importPage.navigate();
        importPage.clickCOAImportLink();

        importPage.assertPageTitleText("Import Bagan Akun");
    }

    @Test
    @DisplayName("Should navigate to Template import page")
    void shouldNavigateToTemplateImportPage() {
        importPage.navigate();
        importPage.clickTemplateImportLink();

        importPage.assertPageTitleText("Import Template Jurnal");
    }

    @Test
    @DisplayName("Should display COA import form elements")
    void shouldDisplayCOAImportFormElements() {
        importPage.navigateToCOAImport();

        importPage.assertFileInputVisible();
        importPage.assertPreviewButtonVisible();
    }

    @Test
    @DisplayName("Should display Template import form elements")
    void shouldDisplayTemplateImportFormElements() {
        importPage.navigateToTemplateImport();

        importPage.assertFileInputVisible();
        importPage.assertPreviewButtonVisible();
    }

    @Test
    @DisplayName("Should preview COA import file")
    void shouldPreviewCOAImportFile() {
        importPage.navigateToCOAImport();

        importPage.uploadFile(COA_TEST_FILE.toAbsolutePath());
        importPage.clickPreview();

        importPage.assertPreviewHasContent();
    }

    @Test
    @DisplayName("Should preview Template import file")
    void shouldPreviewTemplateImportFile() {
        importPage.navigateToTemplateImport();

        importPage.uploadFile(TEMPLATE_TEST_FILE.toAbsolutePath());
        importPage.clickPreview();

        importPage.assertPreviewHasContent();
    }

    @Test
    @DisplayName("Should clear existing COA and import new data")
    @Sql(scripts = "/db/testmigration/cleanup-for-clear-test.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void shouldClearExistingCOAAndImportNewData() {
        importPage.navigateToCOAImport();

        // Upload and preview
        importPage.uploadFile(COA_TEST_FILE.toAbsolutePath());
        importPage.clickPreview();
        importPage.assertPreviewHasContent();

        // Import with clear existing option
        importPage.uploadFileInPreview(COA_TEST_FILE.toAbsolutePath());
        importPage.checkClearExistingInPreview();
        importPage.clickImportNow();

        // Should redirect to result page with success
        importPage.assertResultPageVisible();
        importPage.assertImportSuccess();

        // Navigate to COA list and verify imported accounts exist
        page.navigate(baseUrl() + "/accounts");
        page.waitForLoadState();

        // The test accounts (9, 9.1) should exist
        assertThat(page.locator("text=TEST IMPORT")).isVisible();
    }

    @Test
    @DisplayName("Should clear existing Templates and import new data")
    @Sql(scripts = "/db/testmigration/cleanup-for-clear-test.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void shouldClearExistingTemplatesAndImportNewData() {
        importPage.navigateToTemplateImport();

        // Upload and preview
        importPage.uploadFile(TEMPLATE_TEST_FILE.toAbsolutePath());
        importPage.clickPreview();
        importPage.assertPreviewHasContent();

        // Import with clear existing option
        importPage.uploadFileInPreview(TEMPLATE_TEST_FILE.toAbsolutePath());
        importPage.checkClearExistingInPreview();
        importPage.clickImportNow();

        // Should redirect to result page with success
        importPage.assertResultPageVisible();
        importPage.assertImportSuccess();

        // Navigate to Templates list and verify imported template exists
        page.navigate(baseUrl() + "/templates");
        page.waitForLoadState();

        // The test template should exist
        assertThat(page.locator("text=Test Import Template")).isVisible();
    }
}
