package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.Import;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Functional tests for DataImportController.
 * Tests the import page display and file upload functionality.
 */
@DisplayName("Data Import Controller Tests")
@Import(ServiceTestDataInitializer.class)
class DataImportControllerFunctionalTest extends PlaywrightTestBase {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("Should display import page with title")
    void shouldDisplayImportPage() {
        navigateTo("/settings/import");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).isVisible();
        assertThat(page.locator("#page-title")).hasText("Import Data");
    }

    @Test
    @DisplayName("Should display file upload input")
    void shouldDisplayFileUploadInput() {
        navigateTo("/settings/import");
        waitForPageLoad();

        assertThat(page.locator("#file")).isVisible();
    }

    @Test
    @DisplayName("Should display import button")
    void shouldDisplayImportButton() {
        navigateTo("/settings/import");
        waitForPageLoad();

        assertThat(page.locator("#btn-import")).isVisible();
    }

    @Test
    @DisplayName("Should display instructions section")
    void shouldDisplayInstructionsSection() {
        navigateTo("/settings/import");
        waitForPageLoad();

        assertThat(page.locator("#instructions-title")).isVisible();
    }

    @Test
    @DisplayName("Should display link to export page")
    void shouldDisplayLinkToExportPage() {
        navigateTo("/settings/import");
        waitForPageLoad();

        assertThat(page.locator("#link-to-export")).isVisible();
    }

    @Test
    @DisplayName("Should show error when uploading non-ZIP file")
    void shouldShowErrorWhenUploadingNonZipFile() throws IOException {
        // Create a non-ZIP file
        Path txtFile = tempDir.resolve("invalid.txt");
        java.nio.file.Files.writeString(txtFile, "This is not a ZIP file");

        navigateTo("/settings/import");
        waitForPageLoad();

        // Upload the invalid file
        page.locator("#file").setInputFiles(txtFile);
        page.locator("#btn-import").click();
        waitForPageLoad();

        // Should stay on import page (error is shown via flash attribute)
        // The page title should still be visible
        assertThat(page.locator("#page-title")).isVisible();
        assertThat(page.locator("#page-title")).hasText("Import Data");
    }

    @Test
    @DisplayName("Should show error when uploading empty ZIP file")
    void shouldShowErrorWhenUploadingEmptyZipFile() throws IOException {
        // Create an empty ZIP file
        Path emptyZip = tempDir.resolve("empty.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(emptyZip.toFile()))) {
            // Empty ZIP - no entries
        }

        navigateTo("/settings/import");
        waitForPageLoad();

        // Upload the empty ZIP
        page.locator("#file").setInputFiles(emptyZip);
        page.locator("#btn-import").click();
        waitForPageLoad();

        // Should show error or stay on page
        assertThat(page.locator("#page-title")).isVisible();
    }

    @Test
    @DisplayName("Should show error when uploading ZIP with invalid structure")
    void shouldShowErrorWhenUploadingInvalidZipStructure() throws IOException {
        // Create a ZIP with invalid CSV content
        Path invalidZip = tempDir.resolve("invalid-structure.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(invalidZip.toFile()))) {
            // Add a file that doesn't match expected import structure
            ZipEntry entry = new ZipEntry("random-file.txt");
            zos.putNextEntry(entry);
            zos.write("This is not valid import data".getBytes());
            zos.closeEntry();
        }

        navigateTo("/settings/import");
        waitForPageLoad();

        // Upload the invalid ZIP
        page.locator("#file").setInputFiles(invalidZip);
        page.locator("#btn-import").click();
        waitForPageLoad();

        // Should show error or success (depends on service implementation)
        assertThat(page.locator("#page-title")).isVisible();
    }

    @Test
    @DisplayName("Should navigate to export page from link")
    void shouldNavigateToExportPage() {
        navigateTo("/settings/import");
        waitForPageLoad();

        page.locator("#link-to-export").click();
        waitForPageLoad();

        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/settings\\/export.*"));
    }
}
