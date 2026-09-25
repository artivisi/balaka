package com.artivisi.accountingfinance.controller.api;

import com.artivisi.accountingfinance.entity.DraftTransaction;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.AuditEventType;
import com.artivisi.accountingfinance.repository.DraftTransactionRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.service.SecurityAuditService;
import com.artivisi.accountingfinance.service.TemplateExecutionEngine;
import com.artivisi.accountingfinance.service.TransactionApiService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Analysis API for transactions and draft transactions.
 */
@RestController
@RequestMapping("/api/analysis")
@Tag(name = "Financial Analysis", description = "Read-only financial data for AI analysis (reports, snapshots, ledgers)")
@PreAuthorize("hasAuthority('SCOPE_analysis:read')")
@RequiredArgsConstructor
@Slf4j
public class AnalysisTransactionApiController {

    private static final String META_CURRENCY = "currency";
    private static final String META_CURRENCY_IDR = "IDR";
    private static final String META_DESCRIPTION = "description";
    private static final String PARAM_START_DATE = "startDate";
    private static final String PARAM_END_DATE = "endDate";

    private final TransactionRepository transactionRepository;
    private final DraftTransactionRepository draftTransactionRepository;
    private final TransactionApiService transactionApiService;
    private final SecurityAuditService securityAuditService;

    @GetMapping("/transactions")
    public ResponseEntity<AnalysisResponse<TransactionsDto>> getTransactions(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {

        Map<String, String> params = buildParamsMap(status, category, startDate, endDate, search, page, size);

        Page<Transaction> txPage = queryTransactions(status, category, startDate, endDate, search, page, size);

        List<TransactionItemDto> items = txPage.getContent().stream()
                .map(this::toTransactionItemDto)
                .toList();

        TransactionsDto data = new TransactionsDto(
                items, txPage.getTotalElements(), txPage.getTotalPages(), page, size);

        auditAccess("transactions", params);

        return ResponseEntity.ok(new AnalysisResponse<>(
                "transactions", LocalDateTime.now(), params, data,
                Map.of(META_CURRENCY, META_CURRENCY_IDR)));
    }

    @GetMapping("/transactions/{id}")
    public ResponseEntity<AnalysisResponse<TransactionDetailDto>> getTransactionDetail(
            @PathVariable UUID id) {

        Transaction tx = transactionRepository.findByIdWithJournalEntries(id)
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found: " + id));

        List<JournalEntryItemDto> journalEntries;
        if (tx.isDraft() && tx.getJournalEntries().isEmpty()) {
            // Generate preview entries for DRAFT transactions
            try {
                TemplateExecutionEngine.PreviewResult preview =
                        transactionApiService.previewJournalEntries(tx.getId());
                journalEntries = preview.entries().stream()
                        .map(e -> new JournalEntryItemDto(
                                e.accountCode(),
                                e.accountName(),
                                e.debitAmount(),
                                e.creditAmount()))
                        .toList();
            } catch (Exception e) {
                log.debug("Could not generate journal preview for DRAFT {}: {}", id, e.getMessage());
                journalEntries = List.of();
            }
        } else {
            journalEntries = tx.getJournalEntries().stream()
                    .map(je -> new JournalEntryItemDto(
                            je.getAccount().getAccountCode(),
                            je.getAccount().getAccountName(),
                            je.getDebitAmount(),
                            je.getCreditAmount()))
                    .toList();
        }

        TransactionDetailDto data = new TransactionDetailDto(
                tx.getId(),
                tx.getTransactionNumber(),
                tx.getTransactionDate(),
                tx.getDescription(),
                tx.getAmount(),
                tx.getStatus().name(),
                tx.getReferenceNumber(),
                tx.getNotes(),
                tx.getJournalTemplate() != null ? tx.getJournalTemplate().getTemplateName() : null,
                tx.getJournalTemplate() != null ? tx.getJournalTemplate().getCategory().name() : null,
                journalEntries,
                tx.getPostedAt(),
                tx.getPostedBy(),
                tx.getCreatedAt(),
                tx.getCreatedBy());

        auditAccess("transaction-detail", Map.of("id", id.toString()));

        return ResponseEntity.ok(new AnalysisResponse<>(
                "transaction-detail", LocalDateTime.now(),
                Map.of("id", id.toString()), data,
                Map.of(META_CURRENCY, META_CURRENCY_IDR)));
    }

    @GetMapping("/drafts")
    public ResponseEntity<AnalysisResponse<DraftsDto>> getDrafts(
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {

        Page<DraftTransaction> draftsPage = draftTransactionRepository
                .findByStatusOrderByCreatedAtDesc(DraftTransaction.Status.PENDING, PageRequest.of(page, size));

        List<DraftItemDto> items = draftsPage.getContent().stream()
                .map(d -> new DraftItemDto(
                        d.getId(),
                        d.getStatus().name(),
                        d.getMerchantName(),
                        d.getAmount(),
                        d.getCurrency(),
                        d.getTransactionDate(),
                        d.getSource() != null ? d.getSource().name() : null,
                        d.getApiSource(),
                        d.getOverallConfidence(),
                        d.getSuggestedTemplate() != null ? d.getSuggestedTemplate().getTemplateName() : null,
                        d.getCreatedBy(),
                        d.getCreatedAt()))
                .toList();

        DraftsDto data = new DraftsDto(items,
                draftsPage.getTotalElements(), draftsPage.getTotalPages(), page, size);

        auditAccess("drafts", Map.of("page", String.valueOf(page), "size", String.valueOf(size)));

        return ResponseEntity.ok(new AnalysisResponse<>(
                "drafts", LocalDateTime.now(),
                Map.of("page", String.valueOf(page), "size", String.valueOf(size)),
                data,
                Map.of(META_CURRENCY, META_CURRENCY_IDR,
                        META_DESCRIPTION, "Pending draft transactions awaiting review. "
                                + "Higher confidence scores indicate more reliable AI extraction.")));
    }

    private Map<String, String> buildParamsMap(String status, String category,
                                                String startDate, String endDate,
                                                String search, int page, int size) {
        Map<String, String> params = new HashMap<>();
        if (status != null) params.put("status", status);
        if (category != null) params.put("category", category);
        if (startDate != null) params.put(PARAM_START_DATE, startDate);
        if (endDate != null) params.put(PARAM_END_DATE, endDate);
        if (search != null) params.put("search", search);
        params.put("page", String.valueOf(page));
        params.put("size", String.valueOf(size));
        return params;
    }

    private Page<Transaction> queryTransactions(String status, String category,
                                                 String startDate, String endDate,
                                                 String search, int page, int size) {
        if (search != null && !search.isBlank()) {
            return transactionRepository.searchTransactions(search, PageRequest.of(page, size));
        }
        return transactionRepository.findByFilters(
                status, category,
                null,
                startDate != null ? LocalDate.parse(startDate) : null,
                endDate != null ? LocalDate.parse(endDate) : null,
                PageRequest.of(page, size));
    }

    private TransactionItemDto toTransactionItemDto(Transaction tx) {
        return new TransactionItemDto(
                tx.getId(),
                tx.getTransactionNumber(),
                tx.getTransactionDate(),
                tx.getDescription(),
                tx.getAmount(),
                tx.getStatus().name(),
                tx.getJournalTemplate() != null ? tx.getJournalTemplate().getTemplateName() : null,
                tx.getJournalTemplate() != null ? tx.getJournalTemplate().getCategory().name() : null,
                tx.getCreatedBy(),
                tx.getCreatedAt());
    }

    private void auditAccess(String reportType, Map<String, String> params) {
        securityAuditService.logAsync(AuditEventType.API_CALL,
                "Analysis API: " + reportType + " " + params);
    }

    // --- DTOs ---

    public record TransactionsDto(
            List<TransactionItemDto> transactions,
            long totalElements,
            int totalPages,
            int currentPage,
            int pageSize
    ) {}

    public record TransactionItemDto(
            UUID id,
            String transactionNumber,
            LocalDate transactionDate,
            String description,
            BigDecimal amount,
            String status,
            String templateName,
            String category,
            String createdBy,
            LocalDateTime createdAt
    ) {}

    public record TransactionDetailDto(
            UUID id,
            String transactionNumber,
            LocalDate transactionDate,
            String description,
            BigDecimal amount,
            String status,
            String referenceNumber,
            String notes,
            String templateName,
            String category,
            List<JournalEntryItemDto> journalEntries,
            LocalDateTime postedAt,
            String postedBy,
            LocalDateTime createdAt,
            String createdBy
    ) {}

    public record JournalEntryItemDto(
            String accountCode,
            String accountName,
            BigDecimal debitAmount,
            BigDecimal creditAmount
    ) {}

    public record DraftsDto(
            List<DraftItemDto> items,
            long totalElements,
            int totalPages,
            int currentPage,
            int pageSize
    ) {}

    public record DraftItemDto(
            UUID id,
            String status,
            String merchantName,
            BigDecimal amount,
            String currency,
            LocalDate transactionDate,
            String source,
            String apiSource,
            BigDecimal overallConfidence,
            String suggestedTemplateName,
            String createdBy,
            LocalDateTime createdAt
    ) {}
}
