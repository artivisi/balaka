package com.artivisi.accountingfinance.controller.api;

import com.artivisi.accountingfinance.dto.DocumentResponse;
import com.artivisi.accountingfinance.entity.Document;
import com.artivisi.accountingfinance.service.DocumentService;
import com.artivisi.accountingfinance.security.LogSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Documents", description = "Document attachment management for transactions")
@RequiredArgsConstructor
@Slf4j
public class DocumentApiController {

    private final DocumentService documentService;

    @PostMapping("/api/transactions/{id}/documents")
    @PreAuthorize("hasAuthority('SCOPE_transactions:post') and hasAnyAuthority('TRANSACTION_CREATE', 'TRANSACTION_EDIT')")
    public ResponseEntity<DocumentResponse> uploadDocument(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) throws IOException {
        String username = getCurrentUsername();
        log.info("API: Upload document - transactionId={}, filename={}, user={}",
                id, LogSanitizer.sanitize(file.getOriginalFilename()), username);

        Document saved = documentService.uploadForTransaction(id, file, username);

        log.info("API: Document uploaded - id={}, transactionId={}", saved.getId(), id);
        return ResponseEntity.status(HttpStatus.CREATED).body(DocumentResponse.from(saved));
    }

    @GetMapping("/api/transactions/{id}/documents")
    @PreAuthorize("hasAuthority('SCOPE_transactions:post')")
    public ResponseEntity<List<DocumentResponse>> listDocuments(@PathVariable UUID id) {
        List<Document> documents = documentService.findByTransactionId(id);
        List<DocumentResponse> responses = documents.stream()
                .map(DocumentResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/api/documents/{docId}")
    @PreAuthorize("hasAuthority('SCOPE_transactions:post')")
    public ResponseEntity<Resource> downloadDocument(@PathVariable UUID docId) {
        Document document = documentService.findById(docId);
        Resource resource = documentService.loadAsResource(docId);

        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(document.getOriginalFilename(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(resource);
    }

    @DeleteMapping("/api/documents/{docId}")
    @PreAuthorize("hasAuthority('SCOPE_transactions:post') and hasAnyAuthority('TRANSACTION_EDIT', 'JOURNAL_EDIT', 'INVOICE_EDIT')")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID docId) throws IOException {
        String username = getCurrentUsername();
        log.info("API: Delete document - docId={}, user={}", docId, username);

        documentService.delete(docId);

        log.info("API: Document deleted - id={}", docId);
        return ResponseEntity.noContent().build();
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return auth.getName();
        }
        return "API";
    }
}
