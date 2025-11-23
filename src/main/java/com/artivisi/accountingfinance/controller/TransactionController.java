package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.dto.TransactionDto;
import com.artivisi.accountingfinance.dto.VoidTransactionDto;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.TemplateCategory;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.service.ChartOfAccountService;
import com.artivisi.accountingfinance.service.JournalTemplateService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.util.UUID;

@Controller
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final JournalTemplateService journalTemplateService;
    private final ChartOfAccountService chartOfAccountService;

    @GetMapping
    public String list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            Model model) {
        model.addAttribute("currentPage", "transactions");
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("searchQuery", search);
        model.addAttribute("statuses", TransactionStatus.values());
        model.addAttribute("categories", TemplateCategory.values());
        return "transactions/list";
    }

    @GetMapping("/new")
    public String create(@RequestParam(required = false) UUID templateId, Model model) {
        model.addAttribute("currentPage", "transactions");
        model.addAttribute("isEdit", false);
        model.addAttribute("templates", journalTemplateService.findAll());
        model.addAttribute("accounts", chartOfAccountService.findTransactableAccounts());

        if (templateId != null) {
            model.addAttribute("selectedTemplate", journalTemplateService.findByIdWithLines(templateId));
        }

        return "transactions/form";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        model.addAttribute("currentPage", "transactions");
        model.addAttribute("transaction", transactionService.findByIdWithJournalEntries(id));
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
        return "transactions/form";
    }

    @GetMapping("/{id}/void")
    public String voidForm(@PathVariable UUID id, Model model) {
        Transaction transaction = transactionService.findById(id);
        if (!transaction.isPosted()) {
            return "redirect:/transactions/" + id;
        }

        model.addAttribute("currentPage", "transactions");
        model.addAttribute("transaction", transaction);
        return "transactions/void";
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

        return transaction;
    }
}
