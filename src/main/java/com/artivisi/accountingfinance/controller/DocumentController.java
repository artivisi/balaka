package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.Document;
import com.artivisi.accountingfinance.enums.AuditEventType;
import com.artivisi.accountingfinance.service.DocumentService;
import com.artivisi.accountingfinance.service.SecurityAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentController {

    private static final String MSG_UPLOAD_FAILED = "Gagal mengunggah dokumen: ";
    private static final String MSG_DELETE_FAILED = "Gagal menghapus dokumen: ";
    private static final String MSG_UPLOAD_SUCCESS = "Dokumen berhasil diunggah";
    private static final String MSG_DELETE_SUCCESS = "Dokumen berhasil dihapus";
    private static final String AUDIT_UPLOADED_DOC = "Uploaded document: ";
    private static final String ATTR_MESSAGE = "message";
    private static final String ATTR_SUCCESS = "success";
    private static final String ATTR_DOCUMENTS = "documents";

    private final DocumentService documentService;
    private final SecurityAuditService securityAuditService;

    /**
     * Upload document for a transaction (HTMX endpoint).
     */
    @PostMapping("/transaction/{transactionId}")
    public String uploadForTransaction(
            @PathVariable UUID transactionId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication,
            Model model) {
        String username = authentication != null ? authentication.getName() : "system";

        try {
            Document document = documentService.uploadForTransaction(transactionId, file, username);
            securityAuditService.log(AuditEventType.DOCUMENT_UPLOAD,
                    AUDIT_UPLOADED_DOC + document.getOriginalFilename() + " for transaction: " + transactionId);
            model.addAttribute("document", document);
            model.addAttribute(ATTR_SUCCESS, true);
            model.addAttribute(ATTR_MESSAGE, MSG_UPLOAD_SUCCESS);
        } catch (IOException e) {
            log.error("Failed to upload document for transaction {}: {}", transactionId, e.getMessage());
            model.addAttribute(ATTR_SUCCESS, false);
            // nosemgrep: semgrep.missing-html-escape-in-model - using HtmlUtils.htmlEscape
            model.addAttribute(ATTR_MESSAGE, MSG_UPLOAD_FAILED + HtmlUtils.htmlEscape(e.getMessage()));
        } catch (IllegalArgumentException e) {
            model.addAttribute(ATTR_SUCCESS, false);
            model.addAttribute(ATTR_MESSAGE, HtmlUtils.htmlEscape(e.getMessage()));
        }

        // Return updated document list fragment
        List<Document> documents = documentService.findByTransactionId(transactionId);
        model.addAttribute(ATTR_DOCUMENTS, documents);
        model.addAttribute("transactionId", transactionId);
        return "fragments/document-list :: documentList";
    }

    /**
     * Upload document for a journal entry (HTMX endpoint).
     */
    @PostMapping("/journal-entry/{journalEntryId}")
    public String uploadForJournalEntry(
            @PathVariable UUID journalEntryId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication,
            Model model) {
        String username = authentication != null ? authentication.getName() : "system";

        try {
            Document document = documentService.uploadForJournalEntry(journalEntryId, file, username);
            securityAuditService.log(AuditEventType.DOCUMENT_UPLOAD,
                    AUDIT_UPLOADED_DOC + document.getOriginalFilename() + " for journal entry: " + journalEntryId);
            model.addAttribute("document", document);
            model.addAttribute(ATTR_SUCCESS, true);
            model.addAttribute(ATTR_MESSAGE, MSG_UPLOAD_SUCCESS);
        } catch (IOException e) {
            log.error("Failed to upload document for journal entry {}: {}", journalEntryId, e.getMessage());
            model.addAttribute(ATTR_SUCCESS, false);
            // nosemgrep: semgrep.missing-html-escape-in-model - using HtmlUtils.htmlEscape
            model.addAttribute(ATTR_MESSAGE, MSG_UPLOAD_FAILED + HtmlUtils.htmlEscape(e.getMessage()));
        } catch (IllegalArgumentException e) {
            model.addAttribute(ATTR_SUCCESS, false);
            model.addAttribute(ATTR_MESSAGE, HtmlUtils.htmlEscape(e.getMessage()));
        }

        List<Document> documents = documentService.findByJournalEntryId(journalEntryId);
        model.addAttribute(ATTR_DOCUMENTS, documents);
        model.addAttribute("journalEntryId", journalEntryId);
        return "fragments/document-list :: documentList";
    }

    /**
     * Upload document for an invoice (HTMX endpoint).
     */
    @PostMapping("/invoice/{invoiceId}")
    public String uploadForInvoice(
            @PathVariable UUID invoiceId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication,
            Model model) {
        String username = authentication != null ? authentication.getName() : "system";

        try {
            Document document = documentService.uploadForInvoice(invoiceId, file, username);
            securityAuditService.log(AuditEventType.DOCUMENT_UPLOAD,
                    AUDIT_UPLOADED_DOC + document.getOriginalFilename() + " for invoice: " + invoiceId);
            model.addAttribute("document", document);
            model.addAttribute(ATTR_SUCCESS, true);
            model.addAttribute(ATTR_MESSAGE, MSG_UPLOAD_SUCCESS);
        } catch (IOException e) {
            log.error("Failed to upload document for invoice {}: {}", invoiceId, e.getMessage());
            model.addAttribute(ATTR_SUCCESS, false);
            // nosemgrep: semgrep.missing-html-escape-in-model - using HtmlUtils.htmlEscape
            model.addAttribute(ATTR_MESSAGE, MSG_UPLOAD_FAILED + HtmlUtils.htmlEscape(e.getMessage()));
        } catch (IllegalArgumentException e) {
            model.addAttribute(ATTR_SUCCESS, false);
            model.addAttribute(ATTR_MESSAGE, HtmlUtils.htmlEscape(e.getMessage()));
        }

        List<Document> documents = documentService.findByInvoiceId(invoiceId);
        model.addAttribute(ATTR_DOCUMENTS, documents);
        model.addAttribute("invoiceId", invoiceId);
        return "fragments/document-list :: documentList";
    }

    /**
     * Get documents for a transaction (HTMX endpoint).
     */
    @GetMapping("/transaction/{transactionId}")
    public String getDocumentsForTransaction(
            @PathVariable UUID transactionId,
            Model model) {
        List<Document> documents = documentService.findByTransactionId(transactionId);
        model.addAttribute(ATTR_DOCUMENTS, documents);
        model.addAttribute("transactionId", transactionId);
        return "fragments/document-list :: documentList";
    }

    /**
     * View/download a document.
     * Uses RFC 6266 compliant Content-Disposition to prevent RFD attacks.
     */
    @GetMapping("/{id}/view")
    @ResponseBody
    public ResponseEntity<Resource> viewDocument(@PathVariable UUID id) {
        Document document = documentService.findById(id);
        Resource resource = documentService.loadAsResource(id);

        // For images and PDFs, display inline; otherwise download
        // Use ContentDisposition builder for proper RFC 6266 encoding
        ContentDisposition contentDisposition;
        if (document.isImage() || document.isPdf()) {
            contentDisposition = ContentDisposition.inline()
                    .filename(document.getOriginalFilename(), StandardCharsets.UTF_8)
                    .build();
        } else {
            contentDisposition = ContentDisposition.attachment()
                    .filename(document.getOriginalFilename(), StandardCharsets.UTF_8)
                    .build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(resource);
    }

    /**
     * Download a document.
     * Uses RFC 6266 compliant Content-Disposition to prevent RFD attacks.
     */
    @GetMapping("/{id}/download")
    @ResponseBody
    public ResponseEntity<Resource> downloadDocument(@PathVariable UUID id) {
        Document document = documentService.findById(id);
        Resource resource = documentService.loadAsResource(id);

        securityAuditService.log(AuditEventType.DOCUMENT_DOWNLOAD,
                "Downloaded document: " + document.getOriginalFilename() + " (id: " + id + ")");

        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(document.getOriginalFilename(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(resource);
    }

    /**
     * Delete a document (HTMX endpoint).
     */
    @DeleteMapping("/{id}")
    public String deleteDocument(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID transactionId,
            @RequestParam(required = false) UUID journalEntryId,
            @RequestParam(required = false) UUID invoiceId,
            Model model) {
        try {
            Document document = documentService.findById(id);
            String filename = document.getOriginalFilename();
            documentService.delete(id);
            securityAuditService.log(AuditEventType.DOCUMENT_DELETE,
                    "Deleted document: " + filename + " (id: " + id + ")");
            model.addAttribute(ATTR_SUCCESS, true);
            model.addAttribute(ATTR_MESSAGE, MSG_DELETE_SUCCESS);
        } catch (IOException e) {
            log.error("Failed to delete document {}: {}", id, e.getMessage());
            model.addAttribute(ATTR_SUCCESS, false);
            // nosemgrep: semgrep.missing-html-escape-in-model - using HtmlUtils.htmlEscape
            model.addAttribute(ATTR_MESSAGE, MSG_DELETE_FAILED + HtmlUtils.htmlEscape(e.getMessage()));
        }

        // Return updated document list based on context
        List<Document> documents;
        if (transactionId != null) {
            documents = documentService.findByTransactionId(transactionId);
            model.addAttribute("transactionId", transactionId);
        } else if (journalEntryId != null) {
            documents = documentService.findByJournalEntryId(journalEntryId);
            model.addAttribute("journalEntryId", journalEntryId);
        } else if (invoiceId != null) {
            documents = documentService.findByInvoiceId(invoiceId);
            model.addAttribute("invoiceId", invoiceId);
        } else {
            documents = List.of();
        }

        model.addAttribute(ATTR_DOCUMENTS, documents);
        return "fragments/document-list :: documentList";
    }

    /**
     * Get document metadata (API endpoint).
     */
    @GetMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<Document> getDocument(@PathVariable UUID id) {
        return ResponseEntity.ok(documentService.findById(id));
    }

    /**
     * Get documents for transaction (API endpoint).
     */
    @GetMapping("/api/transaction/{transactionId}")
    @ResponseBody
    public ResponseEntity<List<Document>> getDocumentsForTransactionApi(@PathVariable UUID transactionId) {
        return ResponseEntity.ok(documentService.findByTransactionId(transactionId));
    }
}
