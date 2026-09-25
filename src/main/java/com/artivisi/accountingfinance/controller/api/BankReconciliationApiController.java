package com.artivisi.accountingfinance.controller.api;

import com.artivisi.accountingfinance.entity.BankReconciliation;
import com.artivisi.accountingfinance.entity.BankStatement;
import com.artivisi.accountingfinance.entity.BankStatementItem;
import com.artivisi.accountingfinance.entity.BankStatementParserConfig;
import com.artivisi.accountingfinance.entity.ReconciliationItem;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.AuditEventType;
import com.artivisi.accountingfinance.enums.BankStatementParserType;
import com.artivisi.accountingfinance.enums.StatementItemMatchStatus;
import com.artivisi.accountingfinance.repository.BankStatementItemRepository;
import com.artivisi.accountingfinance.service.BankReconciliationReportService;
import com.artivisi.accountingfinance.service.BankReconciliationService;
import com.artivisi.accountingfinance.service.BankStatementImportService;
import com.artivisi.accountingfinance.service.BankStatementParserConfigService;
import com.artivisi.accountingfinance.service.SecurityAuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/bank-reconciliation")
@Tag(name = "Bank Reconciliation", description = "Bank statement import, matching, and reconciliation")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAuthority('SCOPE_bank-recon:read')")
public class BankReconciliationApiController {

    private final BankStatementParserConfigService parserConfigService;
    private final BankStatementImportService importService;
    private final BankReconciliationService reconciliationService;
    private final BankReconciliationReportService reportService;
    private final BankStatementItemRepository statementItemRepository;
    private final SecurityAuditService securityAuditService;

    // ==================== Parser Configs ====================

    @GetMapping("/parser-configs")
    public ResponseEntity<List<ParserConfigDto>> listParserConfigs() {
        List<ParserConfigDto> configs = parserConfigService.findActive().stream()
                .map(c -> new ParserConfigDto(c.getId(), c.getBankType().name(), c.getConfigName(), c.getDescription()))
                .toList();
        return ResponseEntity.ok(configs);
    }

    @PostMapping("/parser-configs")
    @PreAuthorize("hasAuthority('SCOPE_bank-recon:write') and hasAuthority('BANK_RECONCILIATION_CONFIG')")
    public ResponseEntity<ParserConfigDto> createParserConfig(@Valid @RequestBody CreateParserConfigRequest request) {
        BankStatementParserConfig config = new BankStatementParserConfig();
        config.setBankType(request.bankType());
        config.setConfigName(request.configName());
        config.setDescription(request.description());
        config.setDateColumn(request.dateColumn());
        config.setDescriptionColumn(request.descriptionColumn());
        config.setDebitColumn(request.debitColumn());
        config.setCreditColumn(request.creditColumn());
        config.setBalanceColumn(request.balanceColumn());
        config.setDateFormat(request.dateFormat());
        if (request.delimiter() != null) config.setDelimiter(request.delimiter());
        if (request.skipHeaderRows() != null) config.setSkipHeaderRows(request.skipHeaderRows());
        if (request.encoding() != null) config.setEncoding(request.encoding());
        if (request.decimalSeparator() != null) config.setDecimalSeparator(request.decimalSeparator());
        if (request.thousandSeparator() != null) config.setThousandSeparator(request.thousandSeparator());

        BankStatementParserConfig saved = parserConfigService.create(config);
        securityAuditService.log(AuditEventType.API_CALL, "API: Parser config created: " + saved.getConfigName());
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new ParserConfigDto(saved.getId(), saved.getBankType().name(), saved.getConfigName(), saved.getDescription()));
    }

    // ==================== Statements ====================

    @PostMapping("/statements/import")
    @PreAuthorize("hasAuthority('SCOPE_bank-recon:write') and hasAuthority('BANK_RECONCILIATION_IMPORT')")
    public ResponseEntity<StatementDto> importStatement(
            @RequestParam("bankAccountId") UUID bankAccountId,
            @RequestParam("parserConfigId") UUID parserConfigId,
            @RequestParam("periodStart") LocalDate periodStart,
            @RequestParam("periodEnd") LocalDate periodEnd,
            @RequestParam(value = "openingBalance", required = false) BigDecimal openingBalance,
            @RequestParam(value = "closingBalance", required = false) BigDecimal closingBalance,
            @RequestParam("file") MultipartFile file) {

        String username = getCurrentUsername();
        var importParams = new BankStatementImportService.BankStatementImportParams(
                bankAccountId, parserConfigId, periodStart, periodEnd,
                openingBalance, closingBalance, file, username);
        BankStatement stmt = importService.importStatement(importParams);

        securityAuditService.log(AuditEventType.API_CALL,
                "API: Bank statement imported: " + file.getOriginalFilename());

        return ResponseEntity.status(HttpStatus.CREATED).body(toStatementDto(stmt));
    }

    @GetMapping("/statements/{id}")
    public ResponseEntity<StatementDto> getStatement(@PathVariable UUID id) {
        BankStatement stmt = importService.findStatementById(id);
        return ResponseEntity.ok(toStatementDto(stmt));
    }

    @GetMapping("/statements/{id}/items")
    public ResponseEntity<List<StatementItemDto>> getStatementItems(
            @PathVariable UUID id,
            @RequestParam(value = "matchStatus", required = false) StatementItemMatchStatus matchStatus) {

        List<BankStatementItem> items;
        if (matchStatus != null) {
            items = statementItemRepository.findByBankStatementIdAndMatchStatusOrderByLineNumberAsc(id, matchStatus);
        } else {
            items = statementItemRepository.findByBankStatementIdOrderByLineNumberAsc(id);
        }

        return ResponseEntity.ok(items.stream().map(this::toStatementItemDto).toList());
    }

    // ==================== Reconciliations ====================

    @PostMapping("/reconciliations")
    @PreAuthorize("hasAuthority('SCOPE_bank-recon:write') and hasAuthority('BANK_RECONCILIATION_MATCH')")
    public ResponseEntity<ReconciliationDto> createReconciliation(@Valid @RequestBody CreateReconciliationRequest request) {
        String username = getCurrentUsername();
        BankReconciliation recon = reconciliationService.create(request.bankStatementId(), request.notes(), username);
        securityAuditService.log(AuditEventType.API_CALL,
                "API: Reconciliation created for statement: " + request.bankStatementId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toReconciliationDto(recon));
    }

    @GetMapping("/reconciliations/{id}")
    public ResponseEntity<ReconciliationDto> getReconciliation(@PathVariable UUID id) {
        BankReconciliation recon = reconciliationService.findById(id);
        return ResponseEntity.ok(toReconciliationDto(recon));
    }

    @PostMapping("/reconciliations/{id}/auto-match")
    @PreAuthorize("hasAuthority('SCOPE_bank-recon:write') and hasAuthority('BANK_RECONCILIATION_MATCH')")
    public ResponseEntity<Map<String, Object>> autoMatch(@PathVariable UUID id) {
        String username = getCurrentUsername();
        int matchCount = reconciliationService.autoMatch(id, username);
        return ResponseEntity.ok(Map.of("matchCount", matchCount));
    }

    @PostMapping("/reconciliations/{id}/match")
    @PreAuthorize("hasAuthority('SCOPE_bank-recon:write') and hasAuthority('BANK_RECONCILIATION_MATCH')")
    public ResponseEntity<Void> manualMatch(@PathVariable UUID id, @Valid @RequestBody ManualMatchRequest request) {
        String username = getCurrentUsername();
        reconciliationService.manualMatch(id, request.statementItemId(), request.transactionId(), username);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reconciliations/{id}/mark-bank-only")
    @PreAuthorize("hasAuthority('SCOPE_bank-recon:write') and hasAuthority('BANK_RECONCILIATION_MATCH')")
    public ResponseEntity<Void> markBankOnly(@PathVariable UUID id, @Valid @RequestBody MarkBankOnlyRequest request) {
        String username = getCurrentUsername();
        reconciliationService.markBankOnly(id, request.statementItemId(), request.notes(), username);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reconciliations/{id}/mark-book-only")
    @PreAuthorize("hasAuthority('SCOPE_bank-recon:write') and hasAuthority('BANK_RECONCILIATION_MATCH')")
    public ResponseEntity<Void> markBookOnly(@PathVariable UUID id, @Valid @RequestBody MarkBookOnlyRequest request) {
        reconciliationService.markBookOnly(id, request.transactionId(), request.notes());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/reconciliations/{id}/items/{itemId}")
    @PreAuthorize("hasAuthority('SCOPE_bank-recon:write') and hasAuthority('BANK_RECONCILIATION_MATCH')")
    public ResponseEntity<Void> unmatch(@PathVariable UUID id, @PathVariable UUID itemId) {
        reconciliationService.unmatch(id, itemId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reconciliations/{id}/create-transaction")
    @PreAuthorize("hasAuthority('SCOPE_bank-recon:write') and hasAuthority('BANK_RECONCILIATION_MATCH')")
    public ResponseEntity<Void> createTransaction(@PathVariable UUID id, @Valid @RequestBody CreateTransactionFromItemRequest request) {
        String username = getCurrentUsername();
        reconciliationService.createTransactionFromStatementItem(
                id, request.statementItemId(), request.templateId(), request.description(), username);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reconciliations/{id}/complete")
    @PreAuthorize("hasAuthority('SCOPE_bank-recon:write') and hasAuthority('BANK_RECONCILIATION_COMPLETE')")
    public ResponseEntity<Void> complete(@PathVariable UUID id) {
        String username = getCurrentUsername();
        reconciliationService.complete(id, username);
        securityAuditService.log(AuditEventType.API_CALL, "API: Reconciliation completed: " + id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/reconciliations/{id}/unmatched-book")
    public ResponseEntity<List<UnmatchedBookTxnDto>> getUnmatchedBookTransactions(@PathVariable UUID id) {
        List<Transaction> txns = reconciliationService.findUnmatchedBookTransactions(id);
        return ResponseEntity.ok(txns.stream()
                .map(t -> new UnmatchedBookTxnDto(t.getId(), t.getTransactionDate(), t.getDescription(), t.getAmount()))
                .toList());
    }

    // ==================== Reports ====================

    @GetMapping("/reconciliations/{id}/summary")
    public ResponseEntity<Map<String, Object>> getSummary(@PathVariable UUID id) {
        return ResponseEntity.ok(reportService.getSummary(id));
    }

    @GetMapping("/reconciliations/{id}/statement")
    public ResponseEntity<Map<String, Object>> getReconciliationStatement(@PathVariable UUID id) {
        return ResponseEntity.ok(reportService.getReconciliationStatement(id));
    }

    @GetMapping("/reconciliations/{id}/outstanding")
    public ResponseEntity<List<ReconciliationItem>> getOutstanding(@PathVariable UUID id) {
        return ResponseEntity.ok(reportService.getOutstandingItems(id));
    }

    // ==================== DTOs ====================

    record ParserConfigDto(UUID id, String bankType, String configName, String description) {}
    record StatementDto(UUID id, UUID bankAccountId, String bankName, LocalDate periodStart, LocalDate periodEnd,
                       BigDecimal openingBalance, BigDecimal closingBalance, String originalFilename,
                       Integer totalItems, BigDecimal totalDebit, BigDecimal totalCredit) {}
    record StatementItemDto(UUID id, Integer lineNumber, LocalDate transactionDate, String description,
                           BigDecimal debitAmount, BigDecimal creditAmount, BigDecimal balance,
                           String matchStatus, String matchType) {}
    record ReconciliationDto(UUID id, UUID bankAccountId, String bankName, UUID bankStatementId,
                            LocalDate periodStart, LocalDate periodEnd, String status,
                            BigDecimal bookBalance, BigDecimal bankBalance,
                            Integer totalStatementItems, Integer matchedCount,
                            Integer unmatchedBankCount, Integer unmatchedBookCount) {}
    record UnmatchedBookTxnDto(UUID id, LocalDate transactionDate, String description, BigDecimal amount) {}

    record CreateParserConfigRequest(
            @NotNull BankStatementParserType bankType,
            @NotBlank @Size(max = 100) String configName,
            @Size(max = 500) String description,
            @NotNull @Min(0) Integer dateColumn,
            @NotNull @Min(0) Integer descriptionColumn,
            @Min(0) Integer debitColumn,
            @Min(0) Integer creditColumn,
            @Min(0) Integer balanceColumn,
            @NotBlank @Size(max = 50) String dateFormat,
            @Size(max = 5) String delimiter,
            @Min(0) Integer skipHeaderRows,
            @Size(max = 20) String encoding,
            @Size(max = 5) String decimalSeparator,
            @Size(max = 5) String thousandSeparator
    ) {}
    record CreateReconciliationRequest(@NotNull UUID bankStatementId, String notes) {}
    record ManualMatchRequest(@NotNull UUID statementItemId, @NotNull UUID transactionId) {}
    record MarkBankOnlyRequest(@NotNull UUID statementItemId, String notes) {}
    record MarkBookOnlyRequest(@NotNull UUID transactionId, String notes) {}
    record CreateTransactionFromItemRequest(@NotNull UUID statementItemId, @NotNull UUID templateId, String description) {}

    // ==================== Helpers ====================

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return auth.getName();
        }
        return "API";
    }

    private StatementDto toStatementDto(BankStatement stmt) {
        return new StatementDto(stmt.getId(), stmt.getBankAccount().getId(), stmt.getBankAccount().getBankName(),
                stmt.getStatementPeriodStart(), stmt.getStatementPeriodEnd(),
                stmt.getOpeningBalance(), stmt.getClosingBalance(), stmt.getOriginalFilename(),
                stmt.getTotalItems(), stmt.getTotalDebit(), stmt.getTotalCredit());
    }

    private StatementItemDto toStatementItemDto(BankStatementItem item) {
        return new StatementItemDto(item.getId(), item.getLineNumber(), item.getTransactionDate(),
                item.getDescription(), item.getDebitAmount(), item.getCreditAmount(), item.getBalance(),
                item.getMatchStatus().name(), item.getMatchType() != null ? item.getMatchType().name() : null);
    }

    private ReconciliationDto toReconciliationDto(BankReconciliation recon) {
        return new ReconciliationDto(recon.getId(), recon.getBankAccount().getId(),
                recon.getBankAccount().getBankName(), recon.getBankStatement().getId(),
                recon.getPeriodStart(), recon.getPeriodEnd(), recon.getStatus().name(),
                recon.getBookBalance(), recon.getBankBalance(),
                recon.getTotalStatementItems(), recon.getMatchedCount(),
                recon.getUnmatchedBankCount(), recon.getUnmatchedBookCount());
    }
}
