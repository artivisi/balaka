package com.artivisi.accountingfinance.controller.api;

import com.artivisi.accountingfinance.dto.CreateTransactionRequest;
import com.artivisi.accountingfinance.dto.TransactionResponse;
import com.artivisi.accountingfinance.dto.UpdateTransactionRequest;
import com.artivisi.accountingfinance.dto.VoidTransactionDto;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.AuditEventType;
import com.artivisi.accountingfinance.security.LogSanitizer;
import com.artivisi.accountingfinance.service.SecurityAuditService;
import com.artivisi.accountingfinance.service.TemplateExecutionEngine;
import com.artivisi.accountingfinance.service.TransactionApiService;
import com.artivisi.accountingfinance.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST API for direct transaction posting (bypassing draft workflow).
 * Used by AI assistants after user approval in client-side consultation flow.
 */
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionApiController {

    private static final String KEY_SOURCE = "source";

    private final TransactionApiService transactionApiService;
    private final TransactionService transactionService;
    private final SecurityAuditService securityAuditService;

    /**
     * Create and post transaction directly (bypass draft workflow).
     * POST /api/transactions
     */
    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(
            @Valid @RequestBody CreateTransactionRequest request) {

        String username = getCurrentUsername();
        log.info("API: Create transaction directly - merchant={}, template={}, source={}, user={}",
                request.merchant(), request.templateId(), request.source(), username);

        auditApiCall(Map.of(
                "merchant", request.merchant(),
                "amount", request.amount().toString(),
                KEY_SOURCE, request.source(),
                "templateId", request.templateId().toString(),
                "userApproved", request.userApproved().toString()
        ));

        TransactionResponse response = transactionApiService.createTransactionDirect(request, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Update a DRAFT transaction (reclassify template, fix description/amount/date).
     * PUT /api/transactions/{id}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_transactions:post')")
    public ResponseEntity<TransactionResponse> updateTransaction(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTransactionRequest request) {

        String username = getCurrentUsername();
        log.info("API: Update transaction id={}, user={}", id, username);

        auditApiCall(Map.of(
                "action", "update",
                "transactionId", id.toString(),
                KEY_SOURCE, "api"
        ));

        TransactionResponse response = transactionApiService.updateTransaction(id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete a DRAFT transaction.
     * DELETE /api/transactions/{id}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_transactions:post')")
    public ResponseEntity<Void> deleteTransaction(@PathVariable UUID id) {

        String username = getCurrentUsername();
        log.info("API: Delete transaction id={}, user={}", id, username);

        auditApiCall(Map.of(
                "action", "delete",
                "transactionId", id.toString(),
                KEY_SOURCE, "api"
        ));

        transactionApiService.deleteTransaction(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Post a single DRAFT transaction.
     * POST /api/transactions/{id}/post
     */
    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('SCOPE_transactions:post')")
    public ResponseEntity<TransactionResponse> postTransaction(@PathVariable UUID id) {

        String username = getCurrentUsername();
        log.info("API: Post transaction id={}, user={}", id, username);

        Transaction posted = transactionService.post(id, username);

        auditApiCall(Map.of(
                "action", "post",
                "transactionId", id.toString(),
                KEY_SOURCE, "api"
        ));

        return ResponseEntity.ok(toTransactionResponse(posted));
    }

    /**
     * Void a POSTED transaction.
     * POST /api/transactions/{id}/void
     */
    @PostMapping("/{id}/void")
    @PreAuthorize("hasAuthority('SCOPE_transactions:post')")
    public ResponseEntity<TransactionResponse> voidTransaction(
            @PathVariable UUID id,
            @Valid @RequestBody VoidTransactionDto request) {

        String username = getCurrentUsername();
        log.info("API: Void transaction id={}, reason={}, user={}", id, request.reason(), username);

        Transaction voided = transactionService.voidTransaction(id, request.reason(), request.notes(), username);

        auditApiCall(Map.of(
                "action", "void",
                "transactionId", id.toString(),
                "reason", request.reason().name(),
                KEY_SOURCE, "api"
        ));

        return ResponseEntity.ok(toTransactionResponse(voided));
    }

    /**
     * Preview journal entries for a DRAFT transaction.
     * GET /api/transactions/{id}/journal-preview
     */
    @GetMapping("/{id}/journal-preview")
    @PreAuthorize("hasAuthority('SCOPE_transactions:post')")
    public ResponseEntity<JournalPreviewResponse> getJournalPreview(@PathVariable UUID id) {
        log.info("API: Journal preview for transaction id={}", LogSanitizer.sanitize(id.toString()));

        TemplateExecutionEngine.PreviewResult preview = transactionApiService.previewJournalEntries(id);

        List<JournalPreviewEntry> entries = preview.entries().stream()
                .map(e -> new JournalPreviewEntry(
                        e.accountCode(),
                        e.accountName(),
                        e.debitAmount(),
                        e.creditAmount()))
                .toList();

        return ResponseEntity.ok(new JournalPreviewResponse(
                preview.valid(),
                preview.errors(),
                entries,
                preview.totalDebit(),
                preview.totalCredit()));
    }

    /**
     * Bulk post multiple DRAFT transactions.
     * POST /api/transactions/bulk-post
     */
    @PostMapping("/bulk-post")
    @PreAuthorize("hasAuthority('SCOPE_transactions:post')")
    public ResponseEntity<BulkPostResponse> bulkPostTransactions(
            @Valid @RequestBody BulkPostRequest request) {

        String username = getCurrentUsername();
        log.info("API: Bulk post {} transactions, user={}", request.transactionIds().size(), username);

        List<BulkPostResultDto> results = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;

        for (UUID txId : request.transactionIds()) {
            try {
                Transaction posted = transactionService.post(txId, username);
                results.add(new BulkPostResultDto(txId, true, posted.getTransactionNumber(), null));
                successCount++;
            } catch (Exception e) {
                log.warn("Bulk post failed for transaction {}: {}", txId, e.getMessage());
                results.add(new BulkPostResultDto(txId, false, null, e.getMessage()));
                failureCount++;
            }
        }

        auditApiCall(Map.of(
                "action", "bulk-post",
                "count", String.valueOf(request.transactionIds().size()),
                "success", String.valueOf(successCount),
                "failure", String.valueOf(failureCount),
                KEY_SOURCE, "api"
        ));

        return ResponseEntity.ok(new BulkPostResponse(results, successCount, failureCount));
    }

    private TransactionResponse toTransactionResponse(Transaction tx) {
        List<TransactionResponse.JournalEntryDto> journalEntries = tx.getJournalEntries().stream()
                .filter(je -> !Boolean.TRUE.equals(je.getIsReversal()))
                .map(je -> new TransactionResponse.JournalEntryDto(
                        je.getJournalNumber(),
                        je.getAccount().getAccountCode(),
                        je.getAccount().getAccountName(),
                        je.getDebitAmount(),
                        je.getCreditAmount()))
                .toList();

        return new TransactionResponse(
                tx.getId(),
                tx.getTransactionNumber(),
                tx.getStatus().name(),
                tx.getDescription(),
                tx.getAmount(),
                tx.getTransactionDate(),
                tx.getDescription(),
                journalEntries);
    }

    /**
     * Get current authenticated username.
     */
    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return auth.getName();
        }
        return "API";
    }

    /**
     * Audit API calls.
     */
    private void auditApiCall(Map<String, String> details) {
        String detailsStr = String.format("API call from %s: %s",
                details.getOrDefault(KEY_SOURCE, "unknown"),
                details.toString());
        securityAuditService.log(AuditEventType.API_CALL, detailsStr, true);
    }

    // --- DTOs ---

    public record BulkPostRequest(
            List<UUID> transactionIds
    ) {}

    public record BulkPostResponse(
            List<BulkPostResultDto> results,
            int successCount,
            int failureCount
    ) {}

    public record BulkPostResultDto(
            UUID transactionId,
            boolean success,
            String transactionNumber,
            String errorMessage
    ) {}

    public record JournalPreviewResponse(
            boolean valid,
            List<String> errors,
            List<JournalPreviewEntry> entries,
            BigDecimal totalDebit,
            BigDecimal totalCredit
    ) {}

    public record JournalPreviewEntry(
            String accountCode,
            String accountName,
            BigDecimal debitAmount,
            BigDecimal creditAmount
    ) {}
}
