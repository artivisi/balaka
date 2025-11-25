package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.dto.TransactionDto;
import com.artivisi.accountingfinance.dto.VoidTransactionDto;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.Project;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.TemplateCategory;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.service.ChartOfAccountService;
import com.artivisi.accountingfinance.service.JournalTemplateService;
import com.artivisi.accountingfinance.service.ProjectService;
import com.artivisi.accountingfinance.service.TransactionService;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final JournalTemplateService journalTemplateService;
    private final ChartOfAccountService chartOfAccountService;
    private final ProjectService projectService;

    @GetMapping
    public String list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            Model model) {
        model.addAttribute("currentPage", "transactions");
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("searchQuery", search);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("statuses", TransactionStatus.values());
        model.addAttribute("categories", TemplateCategory.values());
        List<JournalTemplate> templates = journalTemplateService.findAll();
        model.addAttribute("templates", templates);
        // Group templates by category for dropdown
        Map<TemplateCategory, List<JournalTemplate>> templatesByCategory = templates.stream()
                .collect(Collectors.groupingBy(JournalTemplate::getCategory));
        model.addAttribute("templatesByCategory", templatesByCategory);
        model.addAttribute("voidReasons", com.artivisi.accountingfinance.enums.VoidReason.values());

        // Parse status and category if provided
        TransactionStatus statusEnum = status != null && !status.isEmpty() ? TransactionStatus.valueOf(status) : null;
        TemplateCategory categoryEnum = category != null && !category.isEmpty() ? TemplateCategory.valueOf(category) : null;

        // Get transactions
        Page<Transaction> transactionPage;
        Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);

        if (search != null && !search.isEmpty()) {
            transactionPage = transactionService.search(search, pageable);
        } else {
            transactionPage = transactionService.findByFilters(statusEnum, categoryEnum, startDate, endDate, pageable);
        }

        model.addAttribute("transactions", transactionPage.getContent());
        model.addAttribute("page", transactionPage);
        model.addAttribute("draftCount", transactionService.countByStatus(TransactionStatus.DRAFT));

        // Return fragment for HTMX requests, full page otherwise
        if ("true".equals(hxRequest)) {
            return "fragments/transaction-table :: table";
        }
        return "transactions/list";
    }

    @GetMapping("/new")
    public String create(@RequestParam(required = false) UUID templateId, Model model) {
        model.addAttribute("currentPage", "transactions");
        model.addAttribute("isEdit", false);
        model.addAttribute("templates", journalTemplateService.findAll());
        model.addAttribute("accounts", chartOfAccountService.findTransactableAccounts());
        model.addAttribute("projects", projectService.findActiveProjects());

        if (templateId != null) {
            model.addAttribute("selectedTemplate", journalTemplateService.findByIdWithLines(templateId));
        }

        return "transactions/form";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        Transaction transaction = transactionService.findByIdWithJournalEntries(id);
        model.addAttribute("currentPage", "transactions");
        model.addAttribute("transaction", transaction);

        // Calculate totals from journal entries
        java.math.BigDecimal totalDebit = transaction.getJournalEntries().stream()
                .map(e -> e.getDebitAmount() != null ? e.getDebitAmount() : java.math.BigDecimal.ZERO)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        java.math.BigDecimal totalCredit = transaction.getJournalEntries().stream()
                .map(e -> e.getCreditAmount() != null ? e.getCreditAmount() : java.math.BigDecimal.ZERO)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        model.addAttribute("totalDebit", totalDebit);
        model.addAttribute("totalCredit", totalCredit);

        return "transactions/detail";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, Model model) {
        Transaction transaction = transactionService.findById(id);
        if (!transaction.isDraft()) {
            return "redirect:/transactions/" + id;
        }

        model.addAttribute("currentPage", "transactions");
        model.addAttribute("isEdit", true);
        model.addAttribute("transaction", transaction);
        model.addAttribute("templates", journalTemplateService.findAll());
        model.addAttribute("accounts", chartOfAccountService.findTransactableAccounts());
        model.addAttribute("projects", projectService.findActiveProjects());
        return "transactions/form";
    }

    @GetMapping("/{id}/void")
    public String voidForm(@PathVariable UUID id, Model model) {
        Transaction transaction = transactionService.findByIdWithJournalEntries(id);
        if (!transaction.isPosted()) {
            return "redirect:/transactions/" + id;
        }

        model.addAttribute("currentPage", "transactions");
        model.addAttribute("transaction", transaction);
        return "transactions/void";
    }

    // HTMX Endpoints for inline actions

    @PostMapping("/{id}/post")
    public String htmxPost(@PathVariable UUID id, Authentication authentication, Model model) {
        String username = authentication != null ? authentication.getName() : "system";
        Transaction posted = transactionService.post(id, username);
        model.addAttribute("trx", posted);
        return "fragments/transaction-table :: row";
    }

    @DeleteMapping("/{id}")
    @ResponseBody
    public ResponseEntity<Void> htmxDelete(@PathVariable UUID id) {
        transactionService.delete(id);
        return ResponseEntity.ok().build();
    }

    // REST API Endpoints

    @GetMapping("/api")
    @ResponseBody
    public ResponseEntity<Page<Transaction>> apiList(
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) TemplateCategory category,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            Pageable pageable) {
        return ResponseEntity.ok(transactionService.findByFilters(status, category, startDate, endDate, pageable));
    }

    @GetMapping("/api/search")
    @ResponseBody
    public ResponseEntity<Page<Transaction>> apiSearch(@RequestParam String q, Pageable pageable) {
        return ResponseEntity.ok(transactionService.search(q, pageable));
    }

    @GetMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<Transaction> apiGet(@PathVariable UUID id) {
        return ResponseEntity.ok(transactionService.findByIdWithJournalEntries(id));
    }

    @PostMapping("/api")
    @ResponseBody
    public ResponseEntity<Transaction> apiCreate(@Valid @RequestBody TransactionDto dto) {
        Transaction transaction = mapDtoToEntity(dto);
        return ResponseEntity.ok(transactionService.create(transaction, dto.accountMappings()));
    }

    @PutMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<Transaction> apiUpdate(@PathVariable UUID id, @Valid @RequestBody TransactionDto dto) {
        Transaction transaction = mapDtoToEntity(dto);
        return ResponseEntity.ok(transactionService.update(id, transaction));
    }

    @PostMapping("/api/{id}/post")
    @ResponseBody
    public ResponseEntity<Transaction> apiPost(@PathVariable UUID id, Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "system";
        return ResponseEntity.ok(transactionService.post(id, username));
    }

    @PostMapping("/api/{id}/void")
    @ResponseBody
    public ResponseEntity<Transaction> apiVoid(
            @PathVariable UUID id,
            @Valid @RequestBody VoidTransactionDto dto,
            Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "system";
        return ResponseEntity.ok(transactionService.voidTransaction(id, dto.reason(), dto.notes(), username));
    }

    @DeleteMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<Void> apiDelete(@PathVariable UUID id) {
        transactionService.delete(id);
        return ResponseEntity.ok().build();
    }

    private Transaction mapDtoToEntity(TransactionDto dto) {
        Transaction transaction = new Transaction();
        transaction.setTransactionDate(dto.transactionDate());
        transaction.setAmount(dto.amount());
        transaction.setDescription(dto.description());
        transaction.setReferenceNumber(dto.referenceNumber());
        transaction.setNotes(dto.notes());

        JournalTemplate template = new JournalTemplate();
        template.setId(dto.templateId());
        transaction.setJournalTemplate(template);

        if (dto.projectId() != null) {
            Project project = new Project();
            project.setId(dto.projectId());
            transaction.setProject(project);
        }

        return transaction;
    }
}
