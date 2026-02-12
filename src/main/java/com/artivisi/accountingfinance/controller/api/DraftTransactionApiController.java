package com.artivisi.accountingfinance.controller.api;

import com.artivisi.accountingfinance.dto.ApproveDraftRequest;
import com.artivisi.accountingfinance.dto.CreateFromReceiptRequest;
import com.artivisi.accountingfinance.dto.CreateFromTextRequest;
import com.artivisi.accountingfinance.dto.DraftResponse;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.enums.AuditEventType;
import com.artivisi.accountingfinance.service.ChartOfAccountService;
import com.artivisi.accountingfinance.service.JournalTemplateService;
import com.artivisi.accountingfinance.service.SecurityAuditService;
import com.artivisi.accountingfinance.service.TransactionApiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST API for AI-assisted transaction posting.
 * Accepts parsed data from external AI assistants (Claude Code, Gemini, etc.)
 */
@RestController
@RequestMapping("/api/drafts")
@RequiredArgsConstructor
@Slf4j
public class DraftTransactionApiController {

    private final TransactionApiService transactionApiService;
    private final JournalTemplateService journalTemplateService;
    private final ChartOfAccountService chartOfAccountService;
    private final SecurityAuditService securityAuditService;

    /**
     * Create draft from AI-parsed receipt data.
     * POST /api/drafts/from-receipt
     */
    @PostMapping("/from-receipt")
    public ResponseEntity<DraftResponse> createFromReceipt(@Valid @RequestBody CreateFromReceiptRequest request) {
        log.info("API: Create draft from receipt - merchant={}, source={}",
                request.merchant(), request.source());

        auditApiCall("DRAFT_CREATED_FROM_RECEIPT", Map.of(
                "merchant", request.merchant(),
                "amount", request.amount().toString(),
                "source", request.source(),
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
    public ResponseEntity<DraftResponse> createFromText(@Valid @RequestBody CreateFromTextRequest request) {
        log.info("API: Create draft from text - merchant={}, source={}",
                request.merchant(), request.source());

        auditApiCall("DRAFT_CREATED_FROM_TEXT", Map.of(
                "merchant", request.merchant(),
                "amount", request.amount().toString(),
                "source", request.source(),
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
    public ResponseEntity<DraftResponse> getDraft(@PathVariable UUID id) {
        log.info("API: Get draft {}", id);

        DraftResponse response = transactionApiService.getDraft(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Approve draft and create transaction.
     * POST /api/drafts/{id}/approve
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<DraftResponse> approve(
            @PathVariable UUID id,
            @Valid @RequestBody ApproveDraftRequest request) {

        String username = getCurrentUsername();
        log.info("API: Approve draft {} by {}", id, username);

        auditApiCall("DRAFT_APPROVED_VIA_API", Map.of(
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
    public ResponseEntity<DraftResponse> reject(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {

        String username = getCurrentUsername();
        String reason = body.getOrDefault("reason", "Rejected via API");
        log.info("API: Reject draft {} by {}: {}", id, username, reason);

        auditApiCall("DRAFT_REJECTED_VIA_API", Map.of(
                "draftId", id.toString(),
                "rejectedBy", username,
                "reason", reason
        ));

        DraftResponse response = transactionApiService.reject(id, reason, username);
        return ResponseEntity.ok(response);
    }

    /**
     * List all available journal templates.
     * GET /api/templates
     */
    @GetMapping("/templates")
    public ResponseEntity<List<TemplateDto>> listTemplates() {
        log.info("API: List templates");

        List<JournalTemplate> templates = journalTemplateService.findAll();
        List<TemplateDto> dtos = templates.stream()
                .map(t -> new TemplateDto(
                        t.getId(),
                        t.getTemplateName(),
                        t.getCategory().name(),
                        t.getDescription()
                ))
                .toList();

        return ResponseEntity.ok(dtos);
    }

    /**
     * List chart of accounts.
     * GET /api/accounts
     */
    @GetMapping("/accounts")
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
    private void auditApiCall(String eventType, Map<String, String> details) {
        String username = getCurrentUsername();
        String detailsStr = String.format("API call from %s: %s",
                details.getOrDefault("source", "unknown"),
                details.toString());
        securityAuditService.log(AuditEventType.API_CALL, detailsStr, true);
    }

    /**
     * Template DTO for API response.
     */
    public record TemplateDto(
            UUID id,
            String name,
            String category,
            String description
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
