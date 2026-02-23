package com.artivisi.accountingfinance.controller.api;

import com.artivisi.accountingfinance.dto.CreateTemplateRequest;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.JournalTemplateLine;
import com.artivisi.accountingfinance.enums.TemplateType;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.security.LogSanitizer;
import com.artivisi.accountingfinance.service.JournalTemplateService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import java.util.List;
import java.util.UUID;

/**
 * REST API for journal template management.
 * Allows AI assistants to read and manage templates.
 */
@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
@Slf4j
public class TemplateApiController {

    private final JournalTemplateService journalTemplateService;
    private final ChartOfAccountRepository chartOfAccountRepository;

    /**
     * List all available journal templates with enhanced metadata.
     * GET /api/templates
     */
    @GetMapping
    public ResponseEntity<List<TemplateDto>> listTemplates() {
        log.info("API: List all templates");

        List<JournalTemplate> templates = journalTemplateService.findAllWithLines();
        List<TemplateDto> dtos = templates.stream()
                .map(this::toTemplateDto)
                .toList();

        return ResponseEntity.ok(dtos);
    }

    /**
     * Get single template by ID with enhanced metadata.
     * GET /api/templates/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<TemplateDto> getTemplate(@PathVariable UUID id) {
        log.info("API: Get template {}", LogSanitizer.sanitize(id.toString()));

        JournalTemplate template = journalTemplateService.findByIdWithLines(id);
        return ResponseEntity.ok(toTemplateDto(template));
    }

    /**
     * Create new journal template.
     * POST /api/templates
     */
    @PostMapping
    public ResponseEntity<TemplateDto> createTemplate(@Valid @RequestBody CreateTemplateRequest request) {
        String username = getCurrentUsername();
        log.info("API: Create template by {}", username);

        JournalTemplate template = buildTemplateFromRequest(request);
        template.setCreatedBy(username);
        addLinesToTemplate(template, request.lines());

        JournalTemplate created = journalTemplateService.create(template);
        return ResponseEntity.status(HttpStatus.CREATED).body(toTemplateDto(created));
    }

    /**
     * Update existing journal template.
     * PUT /api/templates/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<TemplateDto> updateTemplate(
            @PathVariable UUID id,
            @Valid @RequestBody CreateTemplateRequest request) {

        String username = getCurrentUsername();
        log.info("API: Update template {} by {}", id, username);

        JournalTemplate templateData = buildTemplateFromRequest(request);
        addLinesToTemplate(templateData, request.lines());

        JournalTemplate updated = journalTemplateService.update(id, templateData);
        return ResponseEntity.ok(toTemplateDto(updated));
    }

    /**
     * Delete journal template (soft delete).
     * DELETE /api/templates/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable UUID id) {
        String username = getCurrentUsername();
        log.info("API: Delete template {} by {}", id, username);

        journalTemplateService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private JournalTemplate buildTemplateFromRequest(CreateTemplateRequest request) {
        JournalTemplate template = new JournalTemplate();
        template.setTemplateName(request.templateName());
        template.setCategory(request.category());
        template.setCashFlowCategory(request.cashFlowCategory());
        template.setTemplateType(request.templateType() != null ? request.templateType() : TemplateType.SIMPLE);
        template.setDescription(request.description());
        template.setSemanticDescription(request.semanticDescription());
        template.setKeywords(request.keywords());
        template.setExampleMerchants(request.exampleMerchants());
        template.setTypicalAmountMin(request.typicalAmountMin());
        template.setTypicalAmountMax(request.typicalAmountMax());
        template.setMerchantPatterns(request.merchantPatterns());
        return template;
    }

    private void addLinesToTemplate(JournalTemplate template, List<CreateTemplateRequest.TemplateLine> lines) {
        for (CreateTemplateRequest.TemplateLine lineReq : lines) {
            JournalTemplateLine line = new JournalTemplateLine();
            line.setLineOrder(lineReq.lineOrder());
            line.setPosition(lineReq.position());
            line.setFormula(lineReq.formula());
            line.setAccountHint(lineReq.accountHint());
            line.setDescription(lineReq.description());

            if (lineReq.accountId() != null) {
                ChartOfAccount account = chartOfAccountRepository.findById(lineReq.accountId())
                        .orElseThrow(() -> new EntityNotFoundException(
                                "Account not found: " + lineReq.accountId()));
                line.setAccount(account);
            }

            template.addLine(line);
        }
    }

    /**
     * Convert JournalTemplate to TemplateDto with enhanced metadata.
     */
    private TemplateDto toTemplateDto(JournalTemplate t) {
        List<TemplateLineDto> lineDtos = List.of();
        if (t.getLines() != null) {
            lineDtos = t.getLines().stream()
                    .map(this::toTemplateLineDto)
                    .toList();
        }

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
                t.getMerchantPatterns() != null ? List.of(t.getMerchantPatterns()) : List.of(),
                lineDtos
        );
    }

    private TemplateLineDto toTemplateLineDto(JournalTemplateLine line) {
        ChartOfAccount account = line.getAccount();
        return new TemplateLineDto(
                line.getLineOrder(),
                line.getPosition().name(),
                account != null ? account.getId() : null,
                account != null ? account.getAccountCode() : null,
                account != null ? account.getAccountName() : null,
                line.getAccountHint(),
                line.getFormula(),
                line.getDescription()
        );
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
            List<String> merchantPatterns,
            List<TemplateLineDto> lines
    ) {}

    public record TemplateLineDto(
            Integer lineOrder,
            String position,
            UUID accountId,
            String accountCode,
            String accountName,
            String accountHint,
            String formula,
            String description
    ) {}
}
