package com.artivisi.accountingfinance.controller.api;

import com.artivisi.accountingfinance.dto.ApproveDraftRequest;
import com.artivisi.accountingfinance.dto.CreateDraftRequest;
import com.artivisi.accountingfinance.dto.CreateFromReceiptRequest;
import com.artivisi.accountingfinance.dto.CreateFromTextRequest;
import com.artivisi.accountingfinance.dto.DraftResponse;
import com.artivisi.accountingfinance.dto.TransactionResponse;
import com.artivisi.accountingfinance.dto.UpdateDraftRequest;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.enums.AuditEventType;
import com.artivisi.accountingfinance.security.LogSanitizer;
import com.artivisi.accountingfinance.service.ChartOfAccountService;
import com.artivisi.accountingfinance.service.JournalTemplateService;
import com.artivisi.accountingfinance.service.SecurityAuditService;
import com.artivisi.accountingfinance.service.TransactionApiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST API for AI-assisted transaction posting.
 * Accepts parsed data from external AI assistants (Claude Code, Gemini, etc.)
 */
@RestController
@RequestMapping("/api/drafts")
@Tag(name = "Draft Transactions", description = "Create and manage AI-parsed transaction drafts")
@RequiredArgsConstructor
@Slf4j
public class DraftTransactionApiController {

    private static final String ATTR_SOURCE = "source";
    private static final String ATTR_AMOUNT = "amount";

    private final TransactionApiService transactionApiService;
    private final JournalTemplateService journalTemplateService;
    private final ChartOfAccountService chartOfAccountService;
    private final SecurityAuditService securityAuditService;

    /**
     * Create a DRAFT transaction directly with template + account overrides.
     * POST /api/drafts
     */
    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_drafts:create')")
    public ResponseEntity<TransactionResponse> createDraft(@Valid @RequestBody CreateDraftRequest request) {
        String username = getCurrentUsername();
        log.info("API: Create draft directly - template={}, amount={}, user={}",
                request.templateId(), request.amount(), username);

        auditApiCall(Map.of(
                "action", "create-draft",
                "templateId", request.templateId().toString(),
                ATTR_AMOUNT, request.amount().toString(),
                ATTR_SOURCE, "api"
        ));

        TransactionResponse response = transactionApiService.createDraft(request, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Create draft from AI-parsed receipt data.
     * POST /api/drafts/from-receipt
     */
    @PostMapping("/from-receipt")
    @PreAuthorize("hasAuthority('SCOPE_drafts:create')")
    public ResponseEntity<DraftResponse> createFromReceipt(@Valid @RequestBody CreateFromReceiptRequest request) {
        log.info("API: Create draft from receipt - merchant={}, source={}",
                request.merchant(), request.source());

        auditApiCall(Map.of(
                "merchant", request.merchant(),
                ATTR_AMOUNT, request.amount().toString(),
                ATTR_SOURCE, request.source(),
                "confidence", request.confidence().toString()
        ));

        DraftResponse response = transactionApiService.createFromReceipt(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Create draft from AI-parsed text.
     * POST /api/drafts/from-text
     */
    @PostMapping("/from-text")
    @PreAuthorize("hasAuthority('SCOPE_drafts:create')")
    public ResponseEntity<DraftResponse> createFromText(@Valid @RequestBody CreateFromTextRequest request) {
        log.info("API: Create draft from text - merchant={}, source={}",
                request.merchant(), request.source());

        auditApiCall(Map.of(
                "merchant", request.merchant(),
                ATTR_AMOUNT, request.amount().toString(),
                ATTR_SOURCE, request.source(),
                "confidence", request.confidence().toString()
        ));

        DraftResponse response = transactionApiService.createFromText(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get draft by ID.
     * GET /api/drafts/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_drafts:read')")
    public ResponseEntity<DraftResponse> getDraft(@PathVariable UUID id) {
        log.info("API: Get draft {}", LogSanitizer.sanitize(id.toString()));

        DraftResponse response = transactionApiService.getDraft(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Update a PENDING draft transaction.
     * PATCH /api/drafts/{id}
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_drafts:create')")
    public ResponseEntity<DraftResponse> updateDraft(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDraftRequest request) {

        log.info("API: Update draft {}", LogSanitizer.sanitize(id.toString()));

        DraftResponse response = transactionApiService.updateDraft(id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Approve draft and create transaction.
     * POST /api/drafts/{id}/approve
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('SCOPE_drafts:approve') and hasAuthority('DRAFT_APPROVE')")
    public ResponseEntity<DraftResponse> approve(
            @PathVariable UUID id,
            @Valid @RequestBody ApproveDraftRequest request) {

        String username = getCurrentUsername();
        log.info("API: Approve draft {} by {}", id, username);

        auditApiCall(Map.of(
                "draftId", id.toString(),
                "templateId", request.templateId().toString(),
                "approvedBy", username
        ));

        DraftResponse response = transactionApiService.approve(id, request, username);
        return ResponseEntity.ok(response);
    }

    /**
     * Reject draft.
     * POST /api/drafts/{id}/reject
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('SCOPE_drafts:approve') and hasAuthority('DRAFT_REJECT')")
    public ResponseEntity<DraftResponse> reject(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {

        String username = getCurrentUsername();
        String reason = body.getOrDefault("reason", "Rejected via API");
        log.info("API: Reject draft {} by {}: {}",
                LogSanitizer.sanitize(id.toString()),
                LogSanitizer.username(username),
                LogSanitizer.sanitize(reason));

        auditApiCall(Map.of(
                "draftId", id.toString(),
                "rejectedBy", username,
                "reason", reason
        ));

        DraftResponse response = transactionApiService.reject(id, reason, username);
        return ResponseEntity.ok(response);
    }

    /**
     * List all available journal templates with enhanced metadata.
     * GET /api/drafts/templates
     */
    @GetMapping("/templates")
    @PreAuthorize("hasAuthority('SCOPE_templates:read')")
    public ResponseEntity<List<TemplateDto>> listTemplates() {
        log.info("API: List templates");

        List<JournalTemplate> templates = journalTemplateService.findAll();
        List<TemplateDto> dtos = templates.stream()
                .map(this::toTemplateDto)
                .toList();

        return ResponseEntity.ok(dtos);
    }

    /**
     * Convert JournalTemplate to TemplateDto with enhanced metadata.
     */
    private TemplateDto toTemplateDto(JournalTemplate t) {
        return new TemplateDto(
                t.getId(),
                t.getTemplateName(),
                t.getCategory().name(),
                t.getDescription(),
                t.getSemanticDescription(),
                t.getKeywords() != null ? List.of(t.getKeywords()) : List.of(),
                t.getExampleMerchants() != null ? List.of(t.getExampleMerchants()) : List.of(),
                t.getTypicalAmountMin(),
                t.getTypicalAmountMax(),
                t.getMerchantPatterns() != null ? List.of(t.getMerchantPatterns()) : List.of()
        );
    }

    /**
     * List chart of accounts.
     * GET /api/accounts
     */
    @GetMapping("/accounts")
    @PreAuthorize("hasAuthority('SCOPE_accounts:read')")
    public ResponseEntity<List<AccountDto>> listAccounts() {
        log.info("API: List accounts");

        List<ChartOfAccount> accounts = chartOfAccountService.findAll();
        List<AccountDto> dtos = accounts.stream()
                .filter(a -> !a.getIsHeader()) // Only leaf accounts
                .map(a -> new AccountDto(
                        a.getId(),
                        a.getAccountCode(),
                        a.getAccountName(),
                        a.getAccountType().name()
                ))
                .toList();

        return ResponseEntity.ok(dtos);
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
                details.getOrDefault(ATTR_SOURCE, "unknown"),
                details.toString());
        securityAuditService.log(AuditEventType.API_CALL, detailsStr, true);
    }

    /**
     * Template DTO for API response with enhanced AI-friendly metadata.
     */
    public record TemplateDto(
            UUID id,
            String name,
            String category,
            String description,
            String semanticDescription,
            List<String> keywords,
            List<String> exampleMerchants,
            BigDecimal typicalAmountMin,
            BigDecimal typicalAmountMax,
            List<String> merchantPatterns
    ) {}

    /**
     * Account DTO for API response.
     */
    public record AccountDto(
            UUID id,
            String code,
            String name,
            String type
    ) {}
}
