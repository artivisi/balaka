package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.dto.JournalTemplateDto;
import com.artivisi.accountingfinance.dto.JournalTemplateLineDto;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.JournalTemplateLine;
import com.artivisi.accountingfinance.enums.CashFlowCategory;
import com.artivisi.accountingfinance.enums.TemplateCategory;
import com.artivisi.accountingfinance.enums.TemplateType;
import com.artivisi.accountingfinance.service.ChartOfAccountService;
import com.artivisi.accountingfinance.service.JournalTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/templates")
@RequiredArgsConstructor
public class JournalTemplateController {

    private final JournalTemplateService journalTemplateService;
    private final ChartOfAccountService chartOfAccountService;

    @GetMapping
    public String list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean favorites,
            Model model) {
        model.addAttribute("currentPage", "templates");
        model.addAttribute("selectedCategory", category);
        model.addAttribute("searchQuery", search);
        model.addAttribute("showFavorites", favorites);
        model.addAttribute("categories", TemplateCategory.values());

        List<JournalTemplate> templates;
        if (Boolean.TRUE.equals(favorites)) {
            templates = journalTemplateService.findFavorites();
        } else if (category != null && !category.isBlank()) {
            templates = journalTemplateService.findByCategory(TemplateCategory.valueOf(category.toUpperCase()));
        } else {
            templates = journalTemplateService.findAll();
        }
        model.addAttribute("templates", templates);

        return "templates/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        model.addAttribute("currentPage", "templates");
        model.addAttribute("template", journalTemplateService.findByIdWithLines(id));
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

    // REST API Endpoints

    @GetMapping("/api")
    @ResponseBody
    public ResponseEntity<List<JournalTemplate>> apiList(
            @RequestParam(required = false) TemplateCategory category) {
        return ResponseEntity.ok(journalTemplateService.findByCategory(category));
    }

    @GetMapping("/api/favorites")
    @ResponseBody
    public ResponseEntity<List<JournalTemplate>> apiFavorites() {
        return ResponseEntity.ok(journalTemplateService.findFavorites());
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

    @PostMapping("/api/{id}/toggle-favorite")
    @ResponseBody
    public ResponseEntity<Void> apiToggleFavorite(@PathVariable UUID id) {
        journalTemplateService.toggleFavorite(id);
        return ResponseEntity.ok().build();
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

    private JournalTemplate mapDtoToEntity(JournalTemplateDto dto) {
        JournalTemplate template = new JournalTemplate();
        template.setTemplateName(dto.templateName());
        template.setCategory(dto.category());
        template.setCashFlowCategory(dto.cashFlowCategory());
        template.setTemplateType(dto.templateType());
        template.setDescription(dto.description());
        template.setIsFavorite(dto.isFavorite() != null ? dto.isFavorite() : false);
        template.setActive(dto.active() != null ? dto.active() : true);

        if (dto.lines() != null) {
            int order = 1;
            for (JournalTemplateLineDto lineDto : dto.lines()) {
                JournalTemplateLine line = new JournalTemplateLine();
                line.setPosition(lineDto.position());
                line.setFormula(lineDto.formula());
                line.setLineOrder(lineDto.lineOrder() != null ? lineDto.lineOrder() : order++);
                line.setDescription(lineDto.description());

                ChartOfAccount account = new ChartOfAccount();
                account.setId(lineDto.accountId());
                line.setAccount(account);

                template.addLine(line);
            }
        }

        return template;
    }
}
