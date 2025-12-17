package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.dto.ExecuteTemplateDto;
import com.artivisi.accountingfinance.dto.FormulaPreviewRequest;
import com.artivisi.accountingfinance.dto.FormulaPreviewResponse;
import com.artivisi.accountingfinance.dto.JournalTemplateDto;
import com.artivisi.accountingfinance.dto.JournalTemplateLineDto;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.JournalTemplateLine;
import com.artivisi.accountingfinance.enums.CashFlowCategory;
import com.artivisi.accountingfinance.enums.TemplateCategory;
import com.artivisi.accountingfinance.enums.TemplateType;
import com.artivisi.accountingfinance.service.ChartOfAccountService;
import com.artivisi.accountingfinance.service.FormulaEvaluator;
import com.artivisi.accountingfinance.service.JournalTemplateService;
import com.artivisi.accountingfinance.service.TemplateExecutionEngine;
import com.artivisi.accountingfinance.service.UserTemplatePreferenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Controller
@RequestMapping("/templates")
@RequiredArgsConstructor
@org.springframework.security.access.prepost.PreAuthorize("hasAuthority('" + com.artivisi.accountingfinance.security.Permission.TEMPLATE_VIEW + "')")
public class JournalTemplateController {

    private final JournalTemplateService journalTemplateService;
    private final ChartOfAccountService chartOfAccountService;
    private final TemplateExecutionEngine templateExecutionEngine;
    private final FormulaEvaluator formulaEvaluator;
    private final UserTemplatePreferenceService userTemplatePreferenceService;

    @GetMapping
    public String list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean favorites,
            @RequestParam(required = false) String tag,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            Authentication authentication,
            Model model) {
        model.addAttribute("currentPage", "templates");
        model.addAttribute("selectedCategory", category);
        model.addAttribute("searchQuery", search);
        model.addAttribute("showFavorites", favorites);
        model.addAttribute("selectedTag", tag);
        model.addAttribute("categories", TemplateCategory.values());
        model.addAttribute("allTags", journalTemplateService.getDistinctTags());

        String username = authentication != null ? authentication.getName() : null;

        List<JournalTemplate> templates;
        if (Boolean.TRUE.equals(favorites) && username != null) {
            templates = userTemplatePreferenceService.getFavorites(username);
        } else if (tag != null && !tag.isBlank()) {
            templates = journalTemplateService.findByTag(tag);
        } else if (search != null && !search.isBlank()) {
            templates = journalTemplateService.search(search, Pageable.unpaged()).getContent();
        } else if (category != null && !category.isBlank()) {
            templates = journalTemplateService.findByCategory(TemplateCategory.valueOf(category.toUpperCase()));
        } else {
            templates = journalTemplateService.findAll();
        }
        model.addAttribute("templates", templates);

        // Get user's favorite template IDs for highlighting
        Set<UUID> userFavoriteIds = Set.of();
        if (username != null) {
            userFavoriteIds = userTemplatePreferenceService.getFavoriteTemplateIds(username);
        }
        model.addAttribute("userFavoriteIds", userFavoriteIds);

        // Get recently used templates for the sidebar
        List<JournalTemplate> recentlyUsed = List.of();
        if (username != null) {
            recentlyUsed = userTemplatePreferenceService.getRecentlyUsed(username, 5);
        }
        model.addAttribute("recentlyUsed", recentlyUsed);

        // Return fragment for HTMX requests, full page otherwise
        if ("true".equals(hxRequest)) {
            return "fragments/template-grid :: grid";
        }
        return "templates/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Authentication authentication, Model model) {
        model.addAttribute("currentPage", "templates");
        JournalTemplate template = journalTemplateService.findByIdWithLines(id);
        model.addAttribute("template", template);
        model.addAttribute("templateTags", journalTemplateService.getTagsForTemplate(id));
        model.addAttribute("allTags", journalTemplateService.getDistinctTags());

        // Check if user has this template as favorite
        if (authentication != null) {
            boolean isFavorite = userTemplatePreferenceService.isFavorite(authentication.getName(), id);
            model.addAttribute("userFavorite", isFavorite);
        } else {
            model.addAttribute("userFavorite", false);
        }

        return "templates/detail";
    }

    @GetMapping("/new")
    public String create(Model model) {
        model.addAttribute("currentPage", "templates");
        model.addAttribute("isEdit", false);
        model.addAttribute("isDuplicate", false);
        model.addAttribute("categories", TemplateCategory.values());
        model.addAttribute("cashFlowCategories", CashFlowCategory.values());
        model.addAttribute("templateTypes", TemplateType.values());
        model.addAttribute("accounts", chartOfAccountService.findTransactableAccounts());
        return "templates/form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, Model model) {
        model.addAttribute("currentPage", "templates");
        model.addAttribute("isEdit", true);
        model.addAttribute("isDuplicate", false);
        model.addAttribute("template", journalTemplateService.findByIdWithLines(id));
        model.addAttribute("categories", TemplateCategory.values());
        model.addAttribute("cashFlowCategories", CashFlowCategory.values());
        model.addAttribute("templateTypes", TemplateType.values());
        model.addAttribute("accounts", chartOfAccountService.findTransactableAccounts());
        return "templates/form";
    }

    @GetMapping("/{id}/duplicate")
    public String duplicate(@PathVariable UUID id, Model model) {
        model.addAttribute("currentPage", "templates");
        model.addAttribute("isEdit", false);
        model.addAttribute("isDuplicate", true);
        model.addAttribute("sourceTemplate", journalTemplateService.findByIdWithLines(id));
        model.addAttribute("categories", TemplateCategory.values());
        model.addAttribute("cashFlowCategories", CashFlowCategory.values());
        model.addAttribute("templateTypes", TemplateType.values());
        model.addAttribute("accounts", chartOfAccountService.findTransactableAccounts());
        return "templates/form";
    }

    /**
     * Redirect to consolidated transaction form.
     * The template execute functionality has been merged into the transaction form.
     */
    @GetMapping("/{id}/execute")
    public String executePage(@PathVariable UUID id) {
        return "redirect:/transactions/new?templateId=" + id;
    }

    // Form POST Endpoints

    @PostMapping
    public String create(@Valid JournalTemplateDto dto, RedirectAttributes redirectAttributes) {
        JournalTemplate template = mapDtoToEntity(dto);
        JournalTemplate saved = journalTemplateService.create(template);
        redirectAttributes.addFlashAttribute("successMessage", "Template berhasil dibuat");
        return "redirect:/templates/" + saved.getId();
    }

    @PostMapping("/{id}")
    public String update(@PathVariable UUID id, @Valid JournalTemplateDto dto, RedirectAttributes redirectAttributes) {
        JournalTemplate template = mapDtoToEntity(dto);
        JournalTemplate updated = journalTemplateService.update(id, template);
        redirectAttributes.addFlashAttribute("successMessage", "Template berhasil diperbarui (versi " + updated.getVersion() + ")");
        return "redirect:/templates/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        journalTemplateService.delete(id);
        redirectAttributes.addFlashAttribute("successMessage", "Template berhasil dihapus");
        return "redirect:/templates";
    }

    // REST API Endpoints

    @GetMapping("/api")
    @ResponseBody
    public ResponseEntity<List<JournalTemplate>> apiList(
            @RequestParam(required = false) TemplateCategory category) {
        return ResponseEntity.ok(journalTemplateService.findByCategory(category));
    }

    @GetMapping("/api/recent")
    @ResponseBody
    public ResponseEntity<List<JournalTemplate>> apiRecent() {
        return ResponseEntity.ok(journalTemplateService.findRecentlyUsed());
    }

    @GetMapping("/api/search")
    @ResponseBody
    public ResponseEntity<Page<JournalTemplate>> apiSearch(
            @RequestParam String q,
            Pageable pageable) {
        return ResponseEntity.ok(journalTemplateService.search(q, pageable));
    }

    @GetMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<JournalTemplate> apiGet(@PathVariable UUID id) {
        return ResponseEntity.ok(journalTemplateService.findByIdWithLines(id));
    }

    @PostMapping("/api")
    @ResponseBody
    public ResponseEntity<JournalTemplate> apiCreate(@Valid @RequestBody JournalTemplateDto dto) {
        JournalTemplate template = mapDtoToEntity(dto);
        return ResponseEntity.ok(journalTemplateService.create(template));
    }

    @PutMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<JournalTemplate> apiUpdate(@PathVariable UUID id, @Valid @RequestBody JournalTemplateDto dto) {
        JournalTemplate template = mapDtoToEntity(dto);
        return ResponseEntity.ok(journalTemplateService.update(id, template));
    }

    @PostMapping("/api/{id}/duplicate")
    @ResponseBody
    public ResponseEntity<JournalTemplate> apiDuplicate(@PathVariable UUID id, @RequestParam String newName) {
        return ResponseEntity.ok(journalTemplateService.duplicate(id, newName));
    }

    @PostMapping("/api/{id}/activate")
    @ResponseBody
    public ResponseEntity<Void> apiActivate(@PathVariable UUID id) {
        journalTemplateService.activate(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/{id}/deactivate")
    @ResponseBody
    public ResponseEntity<Void> apiDeactivate(@PathVariable UUID id) {
        journalTemplateService.deactivate(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<Void> apiDelete(@PathVariable UUID id) {
        journalTemplateService.delete(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/formula/preview")
    @ResponseBody
    public ResponseEntity<FormulaPreviewResponse> apiFormulaPreview(@RequestBody FormulaPreviewRequest request) {
        java.util.List<String> errors = formulaEvaluator.validate(request.formula());
        if (!errors.isEmpty()) {
            return ResponseEntity.ok(FormulaPreviewResponse.error(errors));
        }

        java.math.BigDecimal result = formulaEvaluator.preview(request.formula(), request.amount());
        if (result == null) {
            return ResponseEntity.ok(FormulaPreviewResponse.error(java.util.List.of("Formula evaluation failed")));
        }

        String formatted = formatCurrency(result);
        return ResponseEntity.ok(FormulaPreviewResponse.success(result, formatted));
    }

    private String formatCurrency(java.math.BigDecimal amount) {
        java.text.NumberFormat formatter = java.text.NumberFormat.getInstance(new java.util.Locale("id", "ID"));
        return formatter.format(amount.longValue());
    }

    @PostMapping("/api/{id}/preview")
    @ResponseBody
    public ResponseEntity<TemplateExecutionEngine.PreviewResult> apiPreview(
            @PathVariable UUID id,
            @Valid @RequestBody ExecuteTemplateDto dto) {
        JournalTemplate template = journalTemplateService.findByIdWithLines(id);
        TemplateExecutionEngine.ExecutionContext context = new TemplateExecutionEngine.ExecutionContext(
                dto.transactionDate(),
                dto.amount() != null ? dto.amount() : java.math.BigDecimal.ZERO,
                dto.description(),
                null,
                dto.safeVariables(),
                dto.safeAccountMappings()
        );
        return ResponseEntity.ok(templateExecutionEngine.preview(template, context));
    }

    @PostMapping("/api/{id}/execute")
    @ResponseBody
    public ResponseEntity<TemplateExecutionEngine.ExecutionResult> apiExecute(
            @PathVariable UUID id,
            @Valid @RequestBody ExecuteTemplateDto dto,
            Authentication authentication) {
        JournalTemplate template = journalTemplateService.findByIdWithLines(id);
        TemplateExecutionEngine.ExecutionContext context = new TemplateExecutionEngine.ExecutionContext(
                dto.transactionDate(),
                dto.amount() != null ? dto.amount() : java.math.BigDecimal.ZERO,
                dto.description(),
                null,
                dto.safeVariables(),
                dto.safeAccountMappings()
        );

        // Record user usage
        if (authentication != null) {
            userTemplatePreferenceService.recordUsage(authentication.getName(), id);
        }

        return ResponseEntity.ok(templateExecutionEngine.execute(template, context));
    }

    // HTMX Endpoints for Tags and Favorites (return HTML fragments)

    @PostMapping("/{id}/toggle-favorite")
    public String toggleFavorite(@PathVariable UUID id, Authentication authentication, Model model) {
        if (authentication == null) {
            throw new IllegalStateException("Authentication required");
        }
        boolean isFavorite = userTemplatePreferenceService.toggleFavorite(authentication.getName(), id);
        model.addAttribute("templateId", id);
        model.addAttribute("isFavorite", isFavorite);
        return "fragments/template-favorite-button :: favorite-button";
    }

    @PostMapping("/{id}/tags")
    public String addTag(@PathVariable UUID id, @RequestParam String tag, Model model) {
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("Tag is required");
        }
        journalTemplateService.addTag(id, tag);
        model.addAttribute("templateId", id);
        model.addAttribute("templateTags", journalTemplateService.getTagsForTemplate(id));
        model.addAttribute("allTags", journalTemplateService.getDistinctTags());
        return "fragments/template-tags :: tag-list";
    }

    @PostMapping("/{id}/tags/{tag}/delete")
    public String removeTag(@PathVariable UUID id, @PathVariable String tag, Model model) {
        journalTemplateService.removeTag(id, tag);
        model.addAttribute("templateId", id);
        model.addAttribute("templateTags", journalTemplateService.getTagsForTemplate(id));
        model.addAttribute("allTags", journalTemplateService.getDistinctTags());
        return "fragments/template-tags :: tag-list";
    }

    private JournalTemplate mapDtoToEntity(JournalTemplateDto dto) {
        JournalTemplate template = new JournalTemplate();
        template.setTemplateName(dto.templateName());
        template.setCategory(dto.category());
        template.setCashFlowCategory(dto.cashFlowCategory());
        template.setTemplateType(dto.templateType());
        template.setDescription(dto.description());
        template.setActive(dto.active() != null ? dto.active() : true);

        if (dto.lines() != null) {
            int order = 1;
            for (JournalTemplateLineDto lineDto : dto.lines()) {
                JournalTemplateLine line = new JournalTemplateLine();
                line.setPosition(lineDto.position());
                line.setFormula(lineDto.formula());
                line.setLineOrder(lineDto.lineOrder() != null ? lineDto.lineOrder() : order++);
                line.setDescription(lineDto.description());
                line.setAccountHint(lineDto.accountHint());

                // Only set account if accountId is provided (nullable for dynamic selection)
                if (lineDto.accountId() != null) {
                    // Load account from database to avoid detached entity with null version
                    ChartOfAccount account = chartOfAccountService.findById(lineDto.accountId());
                    line.setAccount(account);
                }

                template.addLine(line);
            }
        }

        return template;
    }
}
