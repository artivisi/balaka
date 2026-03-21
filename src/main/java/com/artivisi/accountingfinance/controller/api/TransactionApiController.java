package com.artivisi.accountingfinance.controller.api;

import com.artivisi.accountingfinance.dto.CreateTransactionRequest;
import com.artivisi.accountingfinance.dto.JournalEntryRequest;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
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
import org.springframework.web.bind.annotation.RequestParam;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
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
@Tag(name = "Transactions", description = "Transaction creation, editing, posting, voiding, bulk operations, and free-form journal entries")
@RequiredArgsConstructor
@Slf4j
public class TransactionApiController {

    private static final String KEY_SOURCE = "source";
    private static final String KEY_ACTION = "action";
    private static final String KEY_TRANSACTION_ID = "transactionId";

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
     * Create a free-form journal entry (no template required).
     * POST /api/transactions/journal-entry
     */
    @PostMapping("/journal-entry")
    @PreAuthorize("hasAuthority('SCOPE_transactions:post')")
    @Operation(summary = "Create free-form journal entry (no template)",
               description = "Creates a DRAFT transaction with arbitrary debit/credit lines. "
                           + "No template required. Use for closing journals, adjusting entries, "
                           + "opening balances. Post via POST /api/transactions/{id}/post.")
    @ApiResponse(responseCode = "201", description = "Journal entry draft created")
    @ApiResponse(responseCode = "400", description = "Validation error (unbalanced, invalid lines, header account)")
    public ResponseEntity<TransactionResponse> createJournalEntry(
            @Valid @RequestBody JournalEntryRequest request) {

        String username = getCurrentUsername();
        log.info("API: Create journal entry - description={}, lines={}, user={}",
                request.description(), request.lines().size(), username);

        auditApiCall(Map.of(
                KEY_ACTION, "journal-entry",
                "description", request.description(),
                "lineCount", String.valueOf(request.lines().size()),
                KEY_SOURCE, "api"
        ));

        TransactionResponse response = transactionApiService.createJournalEntry(request, username);
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
                KEY_ACTION, "update",
                KEY_TRANSACTION_ID, id.toString(),
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
                KEY_ACTION, "delete",
                KEY_TRANSACTION_ID, id.toString(),
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
                KEY_ACTION, "post",
                KEY_TRANSACTION_ID, id.toString(),
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
                KEY_ACTION, "void",
                KEY_TRANSACTION_ID, id.toString(),
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
                KEY_ACTION, "bulk-post",
                "count", String.valueOf(request.transactionIds().size()),
                "success", String.valueOf(successCount),
                "failure", String.valueOf(failureCount),
                KEY_SOURCE, "api"
        ));

        return ResponseEntity.ok(new BulkPostResponse(results, successCount, failureCount));
    }

    /**
     * Purge (permanently delete) all voided transactions.
     * DELETE /api/transactions/purge-voided?before=YYYY-MM-DD
     */
    @DeleteMapping("/purge-voided")
    @PreAuthorize("hasAuthority('SCOPE_transactions:post')")
    @Operation(summary = "Purge voided transactions",
               description = "Permanently deletes all VOID transactions and their journal entries. "
                           + "Returns purged transaction details as a backup. "
                           + "Optionally limit scope with ?before=YYYY-MM-DD (exclusive).")
    @ApiResponse(responseCode = "200", description = "Purged transactions returned")
    public ResponseEntity<PurgeVoidedResponse> purgeVoidedTransactions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate before) {

        String username = getCurrentUsername();
        log.info("API: Purge voided transactions, before={}, user={}", before, username);

        List<TransactionService.PurgedTransaction> purged = transactionService.purgeVoidedTransactions(before);

        List<PurgedTransactionDto> purgedDtos = purged.stream()
                .map(p -> new PurgedTransactionDto(
                        p.id(),
                        p.transactionNumber(),
                        p.transactionDate(),
                        p.amount(),
                        p.description(),
                        p.voidReason() != null ? p.voidReason().name() : null,
                        p.voidedAt(),
                        p.voidedBy()))
                .toList();

        auditApiCall(Map.of(
                KEY_ACTION, "purge-voided",
                "before", before != null ? before.toString() : "all",
                "purgedCount", String.valueOf(purged.size()),
                KEY_SOURCE, "api"
        ));

        return ResponseEntity.ok(new PurgeVoidedResponse(purgedDtos, purgedDtos.size()));
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

    public record PurgeVoidedResponse(
            List<PurgedTransactionDto> purgedTransactions,
            int purgedCount
    ) {}

    public record PurgedTransactionDto(
            UUID id,
            String transactionNumber,
            LocalDate transactionDate,
            BigDecimal amount,
            String description,
            String voidReason,
            java.time.LocalDateTime voidedAt,
            String voidedBy
    ) {}
}
