package com.artivisi.accountingfinance.functional.service;

import com.artivisi.accountingfinance.entity.Document;
import com.artivisi.accountingfinance.repository.DocumentRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.FilePayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Transaction document attachments, driven through the "Lampiran Dokumen" section of the
 * transaction detail page: upload, inline view, download, delete, and upload rejection.
 *
 * Every test uploads its own uniquely named file to the seeded transaction SVC-TRX-001,
 * so no test depends on documents left behind by another test.
 *
 * View and download are fetched with page.request() (shares the session cookie):
 * Content-Disposition: attachment makes page.navigate() fail with "Download is starting".
 */
@DisplayName("Service Industry - Document Management")
@Import(ServiceTestDataInitializer.class)
class ServiceDocumentTest extends PlaywrightTestBase {

    // From src/test/resources/testdata/service/financial-transactions.csv. DataImportService
    // regenerates transaction ids on import, so the transaction is resolved by number.
    private static final String TRANSACTION_NUMBER = "SVC-TRX-001";

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private UUID transactionId;

    @BeforeEach
    void setup() {
        transactionId = transactionRepository.findByTransactionNumber(TRANSACTION_NUMBER)
                .orElseThrow(() -> new IllegalStateException("Seeded transaction missing: " + TRANSACTION_NUMBER))
                .getId();
        loginAsAdmin();
    }

    @Test
    @DisplayName("Should upload PDF and list it on the transaction")
    void shouldUploadPdfAndListIt() {
        String filename = uniqueName("receipt", "pdf");
        byte[] content = pdfContent(filename);

        UUID documentId = uploadViaTransactionDetail(filename, "application/pdf", content);

        Document document = documentRepository.findById(documentId).orElseThrow();
        assertEquals(filename, document.getOriginalFilename());
        assertEquals("application/pdf", document.getContentType());
        assertEquals(content.length, document.getFileSize());
        assertTrue(documentRepository.findByTransactionIdOrderByCreatedAtDesc(transactionId).stream()
                        .anyMatch(d -> d.getId().equals(documentId)),
                "Uploaded document not linked to transaction " + transactionId);

        // Attachment survives a reload of the detail page
        openTransactionDetail();
        assertThat(documentItem(documentId)).containsText(filename);
    }

    @Test
    @DisplayName("Should view uploaded PDF inline with original content")
    void shouldViewPdfInline() {
        String filename = uniqueName("view", "pdf");
        byte[] content = pdfContent(filename);
        UUID documentId = uploadViaTransactionDetail(filename, "application/pdf", content);

        String href = page.locator("[data-testid='btn-view-document-" + documentId + "']").getAttribute("href");
        assertEquals("/documents/" + documentId + "/view", href);

        APIResponse response = page.request().get(baseUrl() + href);

        assertEquals(200, response.status());
        assertEquals("application/pdf", response.headers().get("content-type"));
        assertDisposition(response, "inline", filename);
        assertArrayEquals(content, response.body());
    }

    @Test
    @DisplayName("Should view uploaded PNG inline with original content")
    void shouldViewPngInline() {
        String filename = uniqueName("photo", "png");
        byte[] content = pngContent();
        UUID documentId = uploadViaTransactionDetail(filename, "image/png", content);

        APIResponse response = page.request().get(baseUrl() + "/documents/" + documentId + "/view");

        assertEquals(200, response.status());
        assertEquals("image/png", response.headers().get("content-type"));
        assertDisposition(response, "inline", filename);
        assertArrayEquals(content, response.body());
    }

    @Test
    @DisplayName("Should download uploaded document as attachment with original content")
    void shouldDownloadAsAttachment() {
        String filename = uniqueName("download", "pdf");
        byte[] content = pdfContent(filename);
        UUID documentId = uploadViaTransactionDetail(filename, "application/pdf", content);

        String href = page.locator("[data-testid='btn-download-document-" + documentId + "']").getAttribute("href");
        assertEquals("/documents/" + documentId + "/download", href);

        APIResponse response = page.request().get(baseUrl() + href);

        assertEquals(200, response.status());
        assertEquals("application/octet-stream", response.headers().get("content-type"));
        assertDisposition(response, "attachment", filename);
        assertArrayEquals(content, response.body());
    }

    @Test
    @DisplayName("Should delete document and stop serving it")
    void shouldDeleteDocument() {
        String filename = uniqueName("delete", "pdf");
        UUID documentId = uploadViaTransactionDetail(filename, "application/pdf", pdfContent(filename));

        page.onDialog(dialog -> dialog.accept()); // hx-confirm="Hapus dokumen ini?"
        page.locator("[data-testid='btn-delete-document-" + documentId + "']").click();

        assertThat(page.locator("[data-testid='document-upload-success']")).hasText("Dokumen berhasil dihapus");
        assertThat(documentItem(documentId)).hasCount(0);
        assertTrue(documentRepository.findById(documentId).isEmpty(),
                "Deleted document still visible in repository: " + documentId);
        assertEquals(404, page.request().get(baseUrl() + "/documents/" + documentId + "/view").status());
        assertEquals(404, page.request().get(baseUrl() + "/documents/" + documentId + "/download").status());
    }

    @Test
    @DisplayName("Should reject disallowed file type")
    void shouldRejectDisallowedFileType() {
        String filename = uniqueName("notes", "txt");

        submitUpload(filename, "text/plain", "plain text".getBytes(StandardCharsets.UTF_8));

        assertThat(page.locator("[data-testid='document-upload-error']"))
                .containsText("File type 'text/plain' is not allowed");
        assertNotStored(filename);
    }

    @Test
    @DisplayName("Should reject file whose content does not match declared type")
    void shouldRejectSpoofedContentType() {
        String filename = uniqueName("spoofed", "pdf");

        submitUpload(filename, "application/pdf", "not a pdf".getBytes(StandardCharsets.UTF_8));

        assertThat(page.locator("[data-testid='document-upload-error']"))
                .containsText("File content does not match declared type");
        assertNotStored(filename);
    }

    @Test
    @DisplayName("Should render filename echoed in upload error as text, not markup")
    void shouldRenderEchoedFilenameAsText() {
        // ".." fails filename validation; the error message echoes the filename back
        String filename = "..<img src=x id=xss-probe>.pdf";

        submitUpload(filename, "application/pdf", pdfContent("xss"));

        assertThat(page.locator("[data-testid='document-upload-error']"))
                .hasText("Invalid filename: " + filename);
        assertThat(page.locator("#xss-probe")).hasCount(0);
        assertNotStored(filename);
    }

    // ==================== helpers ====================

    private void openTransactionDetail() {
        navigateTo("/transactions/" + transactionId);
        waitForPageLoad();
        // document-list-container is swapped in by hx-trigger="load"; the form only exists after the swap
        assertThat(page.locator("[data-testid='document-upload-form']")).isVisible();
    }

    private void submitUpload(String filename, String contentType, byte[] content) {
        openTransactionDetail();
        page.locator("[data-testid='document-file-input']")
                .setInputFiles(new FilePayload(filename, contentType, content));
        page.locator("[data-testid='btn-upload-document']").click();
    }

    private UUID uploadViaTransactionDetail(String filename, String contentType, byte[] content) {
        submitUpload(filename, contentType, content);

        assertThat(page.locator("[data-testid='document-upload-success']")).hasText("Dokumen berhasil diunggah");
        Locator item = page.locator("[data-testid^='document-item-']").filter(new Locator.FilterOptions().setHasText(filename));
        assertThat(item).hasCount(1);
        return UUID.fromString(item.getAttribute("data-testid").substring("document-item-".length()));
    }

    private Locator documentItem(UUID documentId) {
        return page.locator("[data-testid='document-item-" + documentId + "']");
    }

    private void assertNotStored(String filename) {
        assertThat(page.locator("[data-testid^='document-item-']").filter(new Locator.FilterOptions().setHasText(filename)))
                .hasCount(0);
        assertTrue(documentRepository.findByTransactionIdOrderByCreatedAtDesc(transactionId).stream()
                        .noneMatch(d -> filename.equals(d.getOriginalFilename())),
                "Rejected file was stored: " + filename);
    }

    private static void assertDisposition(APIResponse response, String type, String filename) {
        String disposition = response.headers().get("content-disposition");
        assertTrue(disposition != null && disposition.startsWith(type) && disposition.contains(filename),
                "Expected Content-Disposition " + type + " with " + filename + ", got: " + disposition);
    }

    private static String uniqueName(String prefix, String extension) {
        return prefix + "-" + UUID.randomUUID() + "." + extension;
    }

    /** Minimal PDF; the comment line makes each file's bytes (and checksum) distinct. */
    private static byte[] pdfContent(String marker) {
        String pdf = "%PDF-1.4\n% " + marker + "\n" + """
                1 0 obj
                << /Type /Catalog /Pages 2 0 R >>
                endobj
                2 0 obj
                << /Type /Pages /Kids [3 0 R] /Count 1 >>
                endobj
                3 0 obj
                << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>
                endobj
                trailer
                << /Size 4 /Root 1 0 R >>
                %%EOF""";
        return pdf.getBytes(StandardCharsets.UTF_8);
    }

    /** Minimal 1x1 PNG. */
    private static byte[] pngContent() {
        return new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x02, 0x00, 0x00, 0x00, (byte) 0x90, 0x77, 0x53, (byte) 0xDE,
            0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41, 0x54,
            0x08, (byte) 0xD7, 0x63, (byte) 0xF8, 0x0F, 0x00, 0x00, 0x01, 0x01, 0x00, 0x05, (byte) 0xFE, (byte) 0xCD,
            (byte) 0xCE,
            0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44,
            (byte) 0xAE, 0x42, 0x60, (byte) 0x82
        };
    }
}
