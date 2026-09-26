package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.FilePayload;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * /settings/import page content, navigation and access control. Nothing here submits an
 * import that reaches DataImportService (it truncates the tables it loads); import behaviour
 * is verified in DataImportPersistenceTest, which runs in its own Spring context.
 */
@DisplayName("Data Import Controller Tests")
@Import(ServiceTestDataInitializer.class)
class DataImportControllerFunctionalTest extends PlaywrightTestBase {

    @Test
    @DisplayName("Import page offers a ZIP upload and explains that tables are truncated and replaced")
    void shouldDisplayImportPage() {
        loginAsAdmin();
        navigateTo("/settings/import");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Import Data");
        assertThat(page.locator("#import-title")).hasText("Import Data Lengkap");
        assertThat(page.locator("#file")).hasAttribute("type", "file");
        assertThat(page.locator("#file")).hasAttribute("accept", ".zip");
        assertThat(page.locator("#file")).hasAttribute("name", "file");
        assertThat(page.locator("#btn-import")).hasAttribute("type", "submit");
        assertThat(page.locator("#instructions-title")).hasText("Petunjuk");
        assertThat(page.locator("section ol li")).hasText(new String[] {
                "Semua tabel akan dikosongkan (truncate)",
                "Data dari CSV diimpor sesuai urutan dependensi",
                "File dokumen dikembalikan ke folder storage"});
        assertThat(page.locator("#import-success-message")).hasCount(0);
        assertThat(page.locator("#import-error-message")).hasCount(0);
    }

    @Test
    @DisplayName("Back link opens the data export page")
    void shouldNavigateToExportPage() {
        loginAsAdmin();
        navigateTo("/settings/import");
        waitForPageLoad();

        assertThat(page.locator("#link-to-export")).hasAttribute("href", "/settings/export");
        page.locator("#link-to-export").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/settings/export");
        assertThat(page.locator("#page-title")).hasText("Ekspor Data");
    }

    @Test
    @DisplayName("A user without DATA_IMPORT gets 403 on the import page and the upload")
    void shouldDenyWithoutDataImportPermission() throws IOException {
        login("staff", "password");

        Response pageResponse = page.navigate(baseUrl() + "/settings/import");
        assertEquals(403, pageResponse.status());
        assertThat(page.locator("#access-denied-page")).isVisible();

        navigateTo("/dashboard");
        String csrf = page.locator("meta[name='_csrf']").getAttribute("content");
        // Header-only CSV: even if the endpoint were reachable, nothing would be truncated.
        APIResponse upload = page.request().post(baseUrl() + "/settings/import",
                RequestOptions.create().setHeader("X-CSRF-TOKEN", csrf).setMaxRedirects(0)
                        .setMultipart(FormData.create().set("file", new FilePayload(
                                "denied.zip", "application/zip", headerOnlyZip()))));
        assertEquals(403, upload.status());
    }

    private static byte[] headerOnlyZip() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bytes)) {
            zos.putNextEntry(new ZipEntry("07_clients.csv"));
            zos.write("code,name\n".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return bytes.toByteArray();
    }
}
