package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.Document;
import com.artivisi.accountingfinance.entity.Invoice;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ClientRepository;
import com.artivisi.accountingfinance.repository.DocumentRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.service.InvoiceService;
import com.artivisi.accountingfinance.service.TransactionService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.FilePayload;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DocumentController endpoints not covered by ServiceDocumentTest (which drives transaction
 * attachments through the transaction detail page): metadata JSON API, 404 handling, the HTMX
 * list fragment, invoice and journal-entry upload/delete, delete without context, and the
 * document section of the draft transaction edit form.
 *
 * Invoice and journal-entry detail pages do not render the document fragment, so those
 * endpoints are called with page.request() (shares the session cookie) and the returned
 * fragment is rendered with page.setContent() for assertions.
 */
@DisplayName("Document Controller Tests")
@Import(ServiceTestDataInitializer.class)
class DocumentControllerFunctionalTest extends PlaywrightTestBase {

    // From src/test/resources/testdata/service/financial-journal-entries.csv
    private static final String JOURNAL_NUMBER = "JRN-SVC-001-D";
    // From src/test/resources/testdata/service/clients.csv
    private static final String CLIENT_CODE = "MANDIRI";
    // From industry-seed/it-service/seed-data journal templates
    private static final String DRAFT_TEMPLATE = "Setoran Modal";
    private static final String MISSING_ID = "00000000-0000-0000-0000-000000000000";

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Autowired
    private JournalTemplateRepository journalTemplateRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private InvoiceService invoiceService;

    private String csrfHeader;
    private String csrfToken;

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
        navigateTo("/dashboard");
        waitForPageLoad();
        csrfHeader = page.locator("meta[name='_csrf_header']").getAttribute("content");
        csrfToken = page.locator("meta[name='_csrf']").getAttribute("content");
    }

    // ==================== Metadata API ====================

    @Test
    @DisplayName("Should return document metadata as JSON")
    void shouldReturnDocumentMetadataAsJson() throws Exception {
        UUID transactionId = createDraftTransaction();
        String filename = uniqueName("meta", "pdf");
        byte[] content = pdfContent(filename);
        UUID documentId = uploadAndGetId("/documents/transaction/" + transactionId, filename, "application/pdf", content);

        APIResponse response = page.request().get(baseUrl() + "/documents/api/" + documentId);

        assertEquals(200, response.status());
        assertTrue(response.headers().get("content-type").startsWith("application/json"),
                "Unexpected content type: " + response.headers().get("content-type"));
        JsonNode json = JSON.readTree(response.text());
        assertEquals(documentId.toString(), json.get("id").asText());
        assertEquals(filename, json.get("originalFilename").asText());
        assertEquals("application/pdf", json.get("contentType").asText());
        assertEquals(content.length, json.get("fileSize").asLong());
        assertEquals(sha256(content), json.get("checksumSha256").asText());
        assertEquals("admin", json.get("uploadedBy").asText());
        assertTrue(json.get("pdf").asBoolean());
        assertFalse(json.has("transaction"), "Lazy transaction association must not be serialized");
    }

    @Test
    @DisplayName("Should list exactly the transaction's documents as JSON")
    void shouldListTransactionDocumentsAsJson() throws Exception {
        UUID transactionId = createDraftTransaction();
        String first = uniqueName("first", "pdf");
        String second = uniqueName("second", "png");
        UUID firstId = uploadAndGetId("/documents/transaction/" + transactionId, first, "application/pdf", pdfContent(first));
        UUID secondId = uploadAndGetId("/documents/transaction/" + transactionId, second, "image/png", pngContent());

        APIResponse response = page.request().get(baseUrl() + "/documents/api/transaction/" + transactionId);

        assertEquals(200, response.status());
        JsonNode json = JSON.readTree(response.text());
        assertEquals(2, json.size(), "Expected exactly two documents, got: " + json);
        // Ordered by createdAt descending
        assertEquals(secondId.toString(), json.get(0).get("id").asText());
        assertEquals(second, json.get(0).get("originalFilename").asText());
        assertEquals(firstId.toString(), json.get(1).get("id").asText());
        assertEquals(first, json.get(1).get("originalFilename").asText());
    }

    @Test
    @DisplayName("Should return empty JSON list for transaction without documents")
    void shouldReturnEmptyListForTransactionWithoutDocuments() throws Exception {
        UUID transactionId = createDraftTransaction();

        APIResponse response = page.request().get(baseUrl() + "/documents/api/transaction/" + transactionId);

        assertEquals(200, response.status());
        JsonNode json = JSON.readTree(response.text());
        assertTrue(json.isArray() && json.isEmpty(), "Expected empty array, got: " + json);
    }

    // ==================== 404 handling ====================

    @Test
    @DisplayName("Should return 404 for metadata, view and download of non-existent document")
    void shouldReturn404ForNonExistentDocument() throws Exception {
        for (String path : List.of("/documents/api/" + MISSING_ID,
                "/documents/" + MISSING_ID + "/view",
                "/documents/" + MISSING_ID + "/download")) {
            APIResponse response = page.request().get(baseUrl() + path);
            assertEquals(404, response.status(), "Status for " + path);
            assertEquals("Resource tidak ditemukan.", JSON.readTree(response.text()).get("message").asText(),
                    "Error body for " + path);
        }
    }

    @Test
    @DisplayName("Should render 404 page when browser opens non-existent document")
    void shouldRender404PageInBrowser() {
        var response = page.navigate(baseUrl() + "/documents/" + MISSING_ID + "/view");

        assertEquals(404, response.status());
        assertThat(page.locator("body")).containsText("Halaman Tidak Ditemukan");
    }

    @Test
    @DisplayName("Should return 404 when deleting non-existent document")
    void shouldReturn404WhenDeletingNonExistentDocument() {
        APIResponse response = page.request().delete(baseUrl() + "/documents/" + MISSING_ID, csrf());

        assertEquals(404, response.status());
    }

    @Test
    @DisplayName("Should return 404 and store nothing when uploading to non-existent invoice or journal entry")
    void shouldReturn404WhenUploadingToMissingOwner() {
        for (String owner : List.of("invoice", "journal-entry")) {
            String filename = uniqueName("orphan-" + owner, "pdf");

            APIResponse response = upload("/documents/" + owner + "/" + MISSING_ID, filename, "application/pdf", pdfContent(filename));

            assertEquals(404, response.status(), "Status for " + owner);
            assertTrue(documentRepository.findAll().stream().noneMatch(d -> filename.equals(d.getOriginalFilename())),
                    "Document stored for missing " + owner);
        }
    }

    // ==================== HTMX list fragment ====================

    @Test
    @DisplayName("Should render empty-state list fragment with upload form for transaction")
    void shouldRenderEmptyListFragment() {
        UUID transactionId = createDraftTransaction();

        APIResponse response = page.request().get(baseUrl() + "/documents/transaction/" + transactionId);

        assertEquals(200, response.status());
        page.setContent(response.text());
        assertThat(page.locator("[data-testid='document-upload-form']"))
                .hasAttribute("hx-post", "/documents/transaction/" + transactionId);
        assertThat(page.locator("#documents-empty-state")).hasText("Belum ada dokumen terlampir");
        assertThat(page.locator("[data-testid^='document-item-']")).hasCount(0);
        assertThat(page.locator("[data-testid='document-upload-success'], [data-testid='document-upload-error']")).hasCount(0);
    }

    @Test
    @DisplayName("Should render uploaded document with view, download and delete links in list fragment")
    void shouldRenderDocumentInListFragment() {
        UUID transactionId = createDraftTransaction();
        String filename = uniqueName("listed", "pdf");
        UUID documentId = uploadAndGetId("/documents/transaction/" + transactionId, filename, "application/pdf", pdfContent(filename));

        APIResponse response = page.request().get(baseUrl() + "/documents/transaction/" + transactionId);

        assertEquals(200, response.status());
        page.setContent(response.text());
        assertThat(page.locator("[data-testid^='document-item-']")).hasCount(1);
        assertThat(documentItem(documentId)).containsText(filename);
        assertThat(documentItem(documentId)).containsText("admin");
        assertThat(page.locator("[data-testid='btn-view-document-" + documentId + "']"))
                .hasAttribute("href", "/documents/" + documentId + "/view");
        assertThat(page.locator("[data-testid='btn-download-document-" + documentId + "']"))
                .hasAttribute("href", "/documents/" + documentId + "/download");
        assertDeleteTargets(documentId, "transactionId", transactionId);
        assertThat(page.locator("#documents-empty-state")).hasCount(0);
    }

    // ==================== Invoice context ====================

    @Test
    @DisplayName("Should upload document for invoice and link it to the invoice")
    void shouldUploadDocumentForInvoice() {
        UUID invoiceId = createInvoice();
        String filename = uniqueName("invoice", "pdf");
        byte[] content = pdfContent(filename);

        APIResponse response = upload("/documents/invoice/" + invoiceId, filename, "application/pdf", content);

        assertEquals(200, response.status());
        page.setContent(response.text());
        assertThat(page.locator("[data-testid='document-upload-success']")).hasText("Dokumen berhasil diunggah");
        // The upload form is transaction-only; the invoice fragment has none
        assertThat(page.locator("[data-testid='document-upload-form']")).hasCount(0);

        List<Document> documents = documentRepository.findByInvoiceIdOrderByCreatedAtDesc(invoiceId);
        assertEquals(1, documents.size());
        Document document = documents.get(0);
        assertEquals(filename, document.getOriginalFilename());
        assertEquals(content.length, document.getFileSize());
        assertThat(documentItem(document.getId())).containsText(filename);
        assertDeleteTargets(document.getId(), "invoiceId", invoiceId);

        APIResponse view = page.request().get(baseUrl() + "/documents/" + document.getId() + "/view");
        assertEquals(200, view.status());
        assertEquals(new String(content, StandardCharsets.UTF_8), view.text());
    }

    @Test
    @DisplayName("Should reject disallowed file type for invoice without storing it")
    void shouldRejectDisallowedFileTypeForInvoice() {
        UUID invoiceId = createInvoice();
        String filename = uniqueName("invoice-notes", "txt");

        APIResponse response = upload("/documents/invoice/" + invoiceId, filename, "text/plain",
                "plain text".getBytes(StandardCharsets.UTF_8));

        assertEquals(200, response.status());
        page.setContent(response.text());
        assertThat(page.locator("[data-testid='document-upload-error']"))
                .containsText("File type 'text/plain' is not allowed");
        assertThat(page.locator("#documents-empty-state")).isVisible();
        assertTrue(documentRepository.findByInvoiceIdOrderByCreatedAtDesc(invoiceId).isEmpty(),
                "Rejected file stored for invoice " + invoiceId);
    }

    @Test
    @DisplayName("Should delete invoice document and return the invoice's remaining documents")
    void shouldDeleteInvoiceDocument() {
        UUID invoiceId = createInvoice();
        String kept = uniqueName("invoice-kept", "pdf");
        String deleted = uniqueName("invoice-deleted", "pdf");
        UUID keptId = uploadAndGetId("/documents/invoice/" + invoiceId, kept, "application/pdf", pdfContent(kept));
        UUID deletedId = uploadAndGetId("/documents/invoice/" + invoiceId, deleted, "application/pdf", pdfContent(deleted));

        APIResponse response = page.request().delete(
                baseUrl() + "/documents/" + deletedId + "?invoiceId=" + invoiceId, csrf());

        assertEquals(200, response.status());
        page.setContent(response.text());
        assertThat(page.locator("[data-testid='document-upload-success']")).hasText("Dokumen berhasil dihapus");
        assertThat(page.locator("[data-testid^='document-item-']")).hasCount(1);
        assertThat(documentItem(keptId)).containsText(kept);
        assertTrue(documentRepository.findById(deletedId).isEmpty(), "Deleted document still present: " + deletedId);
        assertEquals(List.of(keptId), documentRepository.findByInvoiceIdOrderByCreatedAtDesc(invoiceId).stream()
                .map(Document::getId).toList());
        assertEquals(404, page.request().get(baseUrl() + "/documents/" + deletedId + "/view").status());
    }

    // ==================== Journal entry context ====================

    @Test
    @DisplayName("Should upload document for journal entry and link it to the journal entry")
    void shouldUploadDocumentForJournalEntry() {
        UUID journalEntryId = seededJournalEntryId();
        String filename = uniqueName("journal", "png");
        byte[] content = pngContent();

        APIResponse response = upload("/documents/journal-entry/" + journalEntryId, filename, "image/png", content);

        assertEquals(200, response.status());
        page.setContent(response.text());
        assertThat(page.locator("[data-testid='document-upload-success']")).hasText("Dokumen berhasil diunggah");
        Locator item = itemByFilename(filename);
        assertThat(item).hasCount(1);
        UUID documentId = idOf(item);
        assertDeleteTargets(documentId, "journalEntryId", journalEntryId);

        Document document = documentRepository.findById(documentId).orElseThrow();
        assertEquals("image/png", document.getContentType());
        assertEquals(content.length, document.getFileSize());
        assertTrue(documentRepository.findByJournalEntryIdOrderByCreatedAtDesc(journalEntryId).stream()
                        .anyMatch(d -> d.getId().equals(documentId)),
                "Uploaded document not linked to journal entry " + journalEntryId);
    }

    @Test
    @DisplayName("Should delete journal entry document and drop it from the journal entry's list")
    void shouldDeleteJournalEntryDocument() {
        UUID journalEntryId = seededJournalEntryId();
        String filename = uniqueName("journal-delete", "pdf");
        UUID documentId = uploadAndGetId("/documents/journal-entry/" + journalEntryId, filename, "application/pdf", pdfContent(filename));

        APIResponse response = page.request().delete(
                baseUrl() + "/documents/" + documentId + "?journalEntryId=" + journalEntryId, csrf());

        assertEquals(200, response.status());
        page.setContent(response.text());
        assertThat(page.locator("[data-testid='document-upload-success']")).hasText("Dokumen berhasil dihapus");
        assertThat(documentItem(documentId)).hasCount(0);
        assertTrue(documentRepository.findByJournalEntryIdOrderByCreatedAtDesc(journalEntryId).stream()
                        .noneMatch(d -> d.getId().equals(documentId)),
                "Deleted document still linked to journal entry " + journalEntryId);
    }

    // ==================== Delete without context ====================

    @Test
    @DisplayName("Should delete document without owner context and return empty list")
    void shouldDeleteDocumentWithoutContext() {
        UUID transactionId = createDraftTransaction();
        String filename = uniqueName("no-context", "pdf");
        UUID documentId = uploadAndGetId("/documents/transaction/" + transactionId, filename, "application/pdf", pdfContent(filename));

        APIResponse response = page.request().delete(baseUrl() + "/documents/" + documentId, csrf());

        assertEquals(200, response.status());
        page.setContent(response.text());
        assertThat(page.locator("[data-testid='document-upload-success']")).hasText("Dokumen berhasil dihapus");
        assertThat(page.locator("#documents-empty-state")).isVisible();
        assertThat(page.locator("[data-testid='document-upload-form']")).hasCount(0);
        assertTrue(documentRepository.findById(documentId).isEmpty(), "Deleted document still present: " + documentId);
        assertTrue(documentRepository.findByTransactionIdOrderByCreatedAtDesc(transactionId).isEmpty());
    }

    // ==================== Transaction edit form ====================

    @Test
    @DisplayName("Should upload document from draft transaction edit form")
    void shouldUploadDocumentFromEditForm() {
        UUID transactionId = createDraftTransaction();
        String filename = uniqueName("edit-form", "pdf");

        navigateTo("/transactions/" + transactionId + "/edit");
        waitForPageLoad();

        assertThat(page.locator("#document-section h2")).hasText("Lampiran Dokumen");
        assertThat(page.locator("#document-placeholder")).hasCount(0);
        // Fragment is swapped in by hx-trigger="load"
        assertThat(page.locator("[data-testid='document-upload-form']"))
                .hasAttribute("hx-post", "/documents/transaction/" + transactionId);
        assertThat(page.locator("#documents-empty-state")).isVisible();

        page.locator("[data-testid='document-file-input']")
                .setInputFiles(new FilePayload(filename, "application/pdf", pdfContent(filename)));
        page.locator("[data-testid='btn-upload-document']").click();

        assertThat(page.locator("[data-testid='document-upload-success']")).hasText("Dokumen berhasil diunggah");
        assertThat(itemByFilename(filename)).hasCount(1);
        assertThat(page).hasURL(baseUrl() + "/transactions/" + transactionId + "/edit");
        List<Document> documents = documentRepository.findByTransactionIdOrderByCreatedAtDesc(transactionId);
        assertEquals(1, documents.size());
        assertEquals(filename, documents.get(0).getOriginalFilename());
    }

    // ==================== helpers ====================

    private UUID createDraftTransaction() {
        var template = journalTemplateRepository.findByTemplateNameAndIsCurrentVersionTrue(DRAFT_TEMPLATE)
                .orElseThrow(() -> new IllegalStateException("Seeded template missing: " + DRAFT_TEMPLATE));
        Transaction transaction = new Transaction();
        transaction.setTransactionDate(LocalDate.now());
        transaction.setAmount(new BigDecimal("1500000"));
        transaction.setDescription("Document test draft " + UUID.randomUUID());
        transaction.setJournalTemplate(template);
        return transactionService.create(transaction, Map.of()).getId();
    }

    private UUID createInvoice() {
        var client = clientRepository.findByCode(CLIENT_CODE)
                .orElseThrow(() -> new IllegalStateException("Seeded client missing: " + CLIENT_CODE));
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("DOC-TEST-" + UUID.randomUUID().toString().substring(0, 8));
        invoice.setClient(client);
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setDueDate(LocalDate.now().plusDays(30));
        invoice.setAmount(new BigDecimal("2500000"));
        return invoiceService.create(invoice).getId();
    }

    private UUID seededJournalEntryId() {
        return journalEntryRepository.findByJournalNumber(JOURNAL_NUMBER)
                .orElseThrow(() -> new IllegalStateException("Seeded journal entry missing: " + JOURNAL_NUMBER))
                .getId();
    }

    /** The delete button must carry the owner context so the refreshed list is the owner's. */
    private void assertDeleteTargets(UUID documentId, String ownerParam, UUID ownerId) {
        assertThat(page.locator("[data-testid='btn-delete-document-" + documentId + "']"))
                .hasAttribute("hx-delete", Pattern.compile(
                        "^/documents/" + documentId + "\\?(.*&)?" + ownerParam + "=" + ownerId + "(&.*)?$"));
    }

    private RequestOptions csrf() {
        return RequestOptions.create().setHeader(csrfHeader, csrfToken);
    }

    private APIResponse upload(String path, String filename, String contentType, byte[] content) {
        return page.request().post(baseUrl() + path, csrf()
                .setMultipart(FormData.create().set("file", new FilePayload(filename, contentType, content))));
    }

    private UUID uploadAndGetId(String path, String filename, String contentType, byte[] content) {
        APIResponse response = upload(path, filename, contentType, content);
        assertEquals(200, response.status(), "Upload to " + path);
        page.setContent(response.text());
        assertThat(page.locator("[data-testid='document-upload-success']")).hasText("Dokumen berhasil diunggah");
        Locator item = itemByFilename(filename);
        assertThat(item).hasCount(1);
        return idOf(item);
    }

    private Locator itemByFilename(String filename) {
        return page.locator("[data-testid^='document-item-']").filter(new Locator.FilterOptions().setHasText(filename));
    }

    private static UUID idOf(Locator item) {
        return UUID.fromString(item.getAttribute("data-testid").substring("document-item-".length()));
    }

    private Locator documentItem(UUID documentId) {
        return page.locator("[data-testid='document-item-" + documentId + "']");
    }

    private static String uniqueName(String prefix, String extension) {
        return prefix + "-" + UUID.randomUUID() + "." + extension;
    }

    private static String sha256(byte[] content) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
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
