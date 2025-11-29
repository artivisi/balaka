package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.page.LoginPage;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.Download;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.zip.ZipInputStream;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@DisplayName("Data Export Feature (Production Readiness)")
class DataExportTest extends PlaywrightTestBase {

    private LoginPage loginPage;

    @BeforeEach
    void setUp() {
        loginPage = new LoginPage(page, baseUrl());
        loginPage.navigate().loginAsAdmin();
    }

    @Nested
    @DisplayName("Export Page")
    class ExportPageTests {

        @Test
        @DisplayName("Should display export page from settings")
        void shouldDisplayExportPageFromSettings() {
            page.navigate(baseUrl() + "/settings");

            // Click on export link
            page.locator("a[href='/settings/export']").click();

            // Should display export page
            assertThat(page.locator("h1")).containsText("Ekspor Data");
        }

        @Test
        @DisplayName("Should display export statistics")
        void shouldDisplayExportStatistics() {
            page.navigate(baseUrl() + "/settings/export");

            // Should display statistics section
            assertThat(page.locator("text=Data yang Akan Diekspor")).isVisible();

            // Should display stat boxes (use first() to get specific element)
            assertThat(page.locator("p.text-sm:has-text('Akun (COA)')").first()).isVisible();
            assertThat(page.locator("p.text-sm:has-text('Jurnal')").first()).isVisible();
            assertThat(page.locator("p.text-sm:has-text('Transaksi')").first()).isVisible();
        }

        @Test
        @DisplayName("Should display export button")
        void shouldDisplayExportButton() {
            page.navigate(baseUrl() + "/settings/export");

            assertThat(page.locator("button:has-text('Ekspor Semua Data')")).isVisible();
        }

        @Test
        @DisplayName("Should display export contents list")
        void shouldDisplayExportContentsList() {
            page.navigate(baseUrl() + "/settings/export");

            assertThat(page.locator("text=chart-of-accounts.csv")).isVisible();
            assertThat(page.locator("text=journal-entries.csv")).isVisible();
            assertThat(page.locator("text=transactions.csv")).isVisible();
            assertThat(page.locator("text=manifest.json")).isVisible();
        }
    }

    @Nested
    @DisplayName("Export Download")
    class ExportDownloadTests {

        @Test
        @DisplayName("Should download ZIP file when export button clicked")
        void shouldDownloadZipFileWhenExportButtonClicked() throws IOException {
            page.navigate(baseUrl() + "/settings/export");

            // Wait for download when clicking export button
            Download download = page.waitForDownload(() -> {
                page.locator("button:has-text('Ekspor Semua Data')").click();
            });

            // Verify download file name format
            String filename = download.suggestedFilename();
            org.assertj.core.api.Assertions.assertThat(filename).startsWith("export-");
            org.assertj.core.api.Assertions.assertThat(filename).endsWith(".zip");

            // Verify it's a valid ZIP file
            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(download.path()))) {
                var entry = zis.getNextEntry();
                org.assertj.core.api.Assertions.assertThat(entry).isNotNull();
            }
        }

        @Test
        @DisplayName("Should include manifest in export")
        void shouldIncludeManifestInExport() throws IOException {
            page.navigate(baseUrl() + "/settings/export");

            Download download = page.waitForDownload(() -> {
                page.locator("button:has-text('Ekspor Semua Data')").click();
            });

            // Check for manifest entry in ZIP
            boolean hasManifest = false;
            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(download.path()))) {
                var entry = zis.getNextEntry();
                while (entry != null) {
                    if (entry.getName().equals("MANIFEST.md")) {
                        hasManifest = true;
                        break;
                    }
                    entry = zis.getNextEntry();
                }
            }

            org.assertj.core.api.Assertions.assertThat(hasManifest).isTrue();
        }
    }

    @Nested
    @DisplayName("Navigation")
    class NavigationTests {

        @Test
        @DisplayName("Should navigate back to settings")
        void shouldNavigateBackToSettings() {
            page.navigate(baseUrl() + "/settings/export");

            page.locator("a:has-text('Kembali ke Pengaturan')").click();

            assertThat(page.locator("h1")).containsText("Pengaturan Perusahaan");
        }
    }
}
