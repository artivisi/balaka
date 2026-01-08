package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.dto.TransactionDto;
import com.artivisi.accountingfinance.dto.VoidTransactionDto;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.Invoice;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.JournalTemplateLine;
import com.artivisi.accountingfinance.entity.Project;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.TemplateCategory;
import com.artivisi.accountingfinance.enums.TemplateType;
import jakarta.persistence.EntityNotFoundException;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.ChartOfAccountService;
import com.artivisi.accountingfinance.service.InvoiceService;
import com.artivisi.accountingfinance.service.JournalTemplateService;
import com.artivisi.accountingfinance.service.ProjectService;
import com.artivisi.accountingfinance.service.TemplateExecutionEngine;
import com.artivisi.accountingfinance.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.artivisi.accountingfinance.controller.ViewConstants.*;

@Controller
@RequestMapping("/transactions")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permission.TRANSACTION_VIEW + "')")
public class TransactionController {

    private static final String ATTR_SELECTED_TEMPLATE = "selectedTemplate";
    private static final String ATTR_TRANSACTION = "transaction";
    private static final String ATTR_TEMPLATES = "templates";
    private static final String ATTR_PROJECTS = "projects";
    private static final String ATTR_IS_EDIT = "isEdit";
    private static final String ATTR_SUCCESS_MESSAGE = "successMessage";

    private final TransactionService transactionService;
    private final JournalTemplateService journalTemplateService;
    private final ChartOfAccountService chartOfAccountService;
    private final ProjectService projectService;
    private final InvoiceService invoiceService;
    private final TemplateExecutionEngine templateExecutionEngine;
    private final com.artivisi.accountingfinance.service.DashboardService dashboardService;

    @GetMapping
    public String list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String projectCode,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            Model model) {
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_TRANSACTIONS);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("selectedProjectCode", projectCode);
        model.addAttribute("searchQuery", search);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("statuses", TransactionStatus.values());
        model.addAttribute("categories", TemplateCategory.values());
        model.addAttribute(ATTR_PROJECTS, projectService.findActiveProjects());
        List<JournalTemplate> templates = journalTemplateService.findAll();
        model.addAttribute(ATTR_TEMPLATES, templates);
        // Group templates by category for dropdown
        Map<TemplateCategory, List<JournalTemplate>> templatesByCategory = templates.stream()
                .collect(Collectors.groupingBy(JournalTemplate::getCategory));
        model.addAttribute("templatesByCategory", templatesByCategory);
        model.addAttribute("voidReasons", com.artivisi.accountingfinance.enums.VoidReason.values());

        // Parse status and category if provided
        TransactionStatus statusEnum = status != null && !status.isEmpty() ? TransactionStatus.valueOf(status) : null;
        TemplateCategory categoryEnum = category != null && !category.isEmpty() ? TemplateCategory.valueOf(category) : null;

        // Resolve project code to UUID if provided
        UUID projectId = null;
        if (projectCode != null && !projectCode.isBlank()) {
            try {
                Project project = projectService.findByCode(projectCode);
                projectId = project.getId();
                model.addAttribute("selectedProjectId", projectId);
            } catch (EntityNotFoundException e) {
                // Project not found, ignore filter
            }
        }

        // Get transactions
        Page<Transaction> transactionPage;
        Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);

        if (search != null && !search.isEmpty()) {
            transactionPage = transactionService.search(search, pageable);
        } else {
            transactionPage = transactionService.findByFilters(statusEnum, categoryEnum, projectId, startDate, endDate, pageable);
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
    @PreAuthorize("hasAuthority('" + Permission.TRANSACTION_CREATE + "')")
    public String create(
            @RequestParam(required = false) UUID templateId,
            @RequestParam(required = false) UUID invoiceId,
            Model model) {
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_TRANSACTIONS);
        model.addAttribute(ATTR_IS_EDIT, false);
        model.addAttribute(ATTR_TEMPLATES, journalTemplateService.findAllWithLines());
        model.addAttribute(ATTR_ACCOUNTS, chartOfAccountService.findTransactableAccounts());
        model.addAttribute(ATTR_PROJECTS, projectService.findActiveProjects());

        if (templateId != null) {
            JournalTemplate template = journalTemplateService.findByIdWithLines(templateId);
            model.addAttribute(ATTR_SELECTED_TEMPLATE, template);
            addDetailedTemplateAttributes(template, model);
        }

        // Pre-fill from invoice if provided (for invoice payment flow)
        if (invoiceId != null) {
            Invoice invoice = invoiceService.findById(invoiceId);
            model.addAttribute("invoice", invoice);
            model.addAttribute("prefillAmount", invoice.getAmount());
            model.addAttribute("prefillDescription", "Pembayaran invoice " + invoice.getInvoiceNumber());
            model.addAttribute("prefillReference", invoice.getInvoiceNumber());
            if (invoice.getProject() != null) {
                model.addAttribute("prefillProjectId", invoice.getProject().getId());
            }
        }

        return "transactions/form";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id,
                         @RequestParam(required = false) Boolean created,
                         Model model) {
        Transaction transaction = transactionService.findByIdWithJournalEntries(id);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_TRANSACTIONS);
        model.addAttribute(ATTR_TRANSACTION, transaction);

        // Show success message if redirected from template execution
        if (Boolean.TRUE.equals(created)) {
            model.addAttribute(ATTR_SUCCESS_MESSAGE, "Transaksi berhasil dibuat dari template");
        }

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
    @PreAuthorize("hasAuthority('" + Permission.TRANSACTION_EDIT + "')")
    public String edit(@PathVariable UUID id, Model model) {
        Transaction transaction = transactionService.findByIdWithMappingsAndVariables(id);
        if (!transaction.isDraft()) {
            return REDIRECT_TRANSACTIONS + id;
        }

        // Load the template with lines for preview
        JournalTemplate template = journalTemplateService.findByIdWithLines(transaction.getJournalTemplate().getId());

        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_TRANSACTIONS);
        model.addAttribute(ATTR_IS_EDIT, true);
        model.addAttribute(ATTR_TRANSACTION, transaction);
        model.addAttribute(ATTR_SELECTED_TEMPLATE, template);
        model.addAttribute(ATTR_TEMPLATES, journalTemplateService.findAll());
        model.addAttribute(ATTR_ACCOUNTS, chartOfAccountService.findTransactableAccounts());
        model.addAttribute(ATTR_PROJECTS, projectService.findActiveProjects());

        // Add initial values for Alpine.js
        model.addAttribute("initialAmount", transaction.getAmount() != null ? transaction.getAmount() : BigDecimal.ZERO);
        model.addAttribute("initialDescription", transaction.getDescription() != null ? transaction.getDescription() : "");

        // Add DETAILED template support
        addDetailedTemplateAttributes(template, model);

        return "transactions/form";
    }

    @GetMapping("/{id}/void")
    @PreAuthorize("hasAuthority('" + Permission.TRANSACTION_VOID + "')")
    public String voidForm(@PathVariable UUID id, Model model) {
        Transaction transaction = transactionService.findByIdWithJournalEntries(id);
        if (!transaction.isPosted()) {
            return REDIRECT_TRANSACTIONS + id;
        }

        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_TRANSACTIONS);
        model.addAttribute(ATTR_TRANSACTION, transaction);
        return "transactions/void";
    }

    // HTMX Endpoints for inline actions

    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('" + Permission.TRANSACTION_POST + "')")
    public String htmxPost(@PathVariable UUID id, Authentication authentication, Model model) {
        String username = authentication != null ? authentication.getName() : "system";
        Transaction posted = transactionService.post(id, username);
        model.addAttribute("trx", posted);
        return "fragments/transaction-table :: row";
    }

    @DeleteMapping("/{id}")
    @ResponseBody
    @PreAuthorize("hasAuthority('" + Permission.TRANSACTION_DELETE + "')")
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

    @GetMapping("/preview")
    public String preview(
            @RequestParam UUID templateId,
            @RequestParam(required = false, defaultValue = "0") BigDecimal amount,
            @RequestParam(required = false) MultiValueMap<String, String> params,
            Model model) {
        JournalTemplate template = journalTemplateService.findByIdWithLines(templateId);

        Map<String, String> accountMapping = extractAccountMapping(params);
        Map<String, BigDecimal> variables = extractVariables(params);

        var context = new TemplateExecutionEngine.ExecutionContext(
                java.time.LocalDate.now(),
                amount,
                "Preview",
                null,
                variables.isEmpty() ? java.util.Map.of() : variables,
                accountMapping.isEmpty() ? java.util.Map.of() : accountMapping
        );

        var previewResult = templateExecutionEngine.preview(template, context);
        var entries = applyAccountMappings(template, previewResult, accountMapping);

        model.addAttribute("entries", entries);
        model.addAttribute("totalDebit", previewResult.totalDebit());
        model.addAttribute("totalCredit", previewResult.totalCredit());

        return "fragments/transaction-preview";
    }

    private Map<String, String> extractAccountMapping(MultiValueMap<String, String> params) {
        Map<String, String> accountMapping = new java.util.HashMap<>();
        for (String key : params.keySet()) {
            if (key.startsWith("accountMapping[") && key.endsWith("]")) {
                String lineId = key.substring(15, key.length() - 1);
                String accountId = params.getFirst(key);
                if (accountId != null && !accountId.isEmpty()) {
                    accountMapping.put(lineId, accountId);
                }
            }
        }
        return accountMapping;
    }

    private Map<String, BigDecimal> extractVariables(MultiValueMap<String, String> params) {
        Map<String, BigDecimal> variables = new java.util.HashMap<>();
        for (String key : params.keySet()) {
            if (key.startsWith("var_")) {
                String varName = key.substring(4);
                String value = params.getFirst(key);
                if (value != null && !value.isEmpty()) {
                    String cleanValue = value.replaceAll("\\D", "");
                    if (!cleanValue.isEmpty()) {
                        variables.put(varName, new BigDecimal(cleanValue));
                    }
                }
            }
        }
        return variables;
    }

    private java.util.List<TemplateExecutionEngine.PreviewEntry> applyAccountMappings(
            JournalTemplate template,
            TemplateExecutionEngine.PreviewResult previewResult,
            Map<String, String> accountMapping) {
        if (accountMapping.isEmpty()) {
            return previewResult.entries();
        }

        java.util.List<TemplateExecutionEngine.PreviewEntry> mappedEntries = new java.util.ArrayList<>();
        int limit = Math.min(template.getLines().size(), previewResult.entries().size());

        for (int i = 0; i < limit; i++) {
            var templateLine = template.getLines().get(i);
            var entry = previewResult.entries().get(i);

            if (templateLine.getAccount() == null) {
                entry = applyMappingToEntry(templateLine, entry, accountMapping);
            }
            mappedEntries.add(entry);
        }
        return mappedEntries;
    }

    private TemplateExecutionEngine.PreviewEntry applyMappingToEntry(
            JournalTemplateLine templateLine,
            TemplateExecutionEngine.PreviewEntry entry,
            Map<String, String> accountMapping) {
        String mappedAccountId = accountMapping.get(templateLine.getId().toString());
        if (mappedAccountId == null || mappedAccountId.isEmpty()) {
            return entry;
        }
        ChartOfAccount mappedAccount = chartOfAccountService.findById(UUID.fromString(mappedAccountId));
        return new TemplateExecutionEngine.PreviewEntry(
                mappedAccount.getAccountCode(),
                mappedAccount.getAccountName(),
                entry.description(),
                entry.debitAmount(),
                entry.creditAmount()
        );
    }

    @PostMapping("/api")
    @ResponseBody
    @PreAuthorize("hasAuthority('" + Permission.TRANSACTION_CREATE + "')")
    public ResponseEntity<Transaction> apiCreate(@Valid @RequestBody TransactionDto dto) {
        Transaction transaction = mapDtoToEntity(dto);

        Transaction saved = transactionService.create(transaction, dto.accountMappings(), dto.variables());

        // If this is an invoice payment, link transaction to invoice and mark as paid
        if (dto.invoiceId() != null) {
            invoiceService.linkTransactionAndMarkPaid(dto.invoiceId(), saved);
        }

        return ResponseEntity.ok(saved);
    }

    @PutMapping("/api/{id}")
    @ResponseBody
    @PreAuthorize("hasAuthority('" + Permission.TRANSACTION_EDIT + "')")
    public ResponseEntity<Transaction> apiUpdate(
            @PathVariable("id") Transaction existing,
            @Valid @RequestBody TransactionDto dto) {
        Transaction transactionData = mapDtoToEntity(dto);
        return ResponseEntity.ok(transactionService.update(existing, transactionData));
    }

    @PostMapping("/api/{id}/post")
    @ResponseBody
    @PreAuthorize("hasAuthority('" + Permission.TRANSACTION_POST + "')")
    public ResponseEntity<Transaction> apiPost(
            @PathVariable("id") Transaction transaction,
            Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "system";
        return ResponseEntity.ok(transactionService.post(transaction.getId(), username));
    }

    @PostMapping("/api/{id}/void")
    @ResponseBody
    @PreAuthorize("hasAuthority('" + Permission.TRANSACTION_VOID + "')")
    public ResponseEntity<Transaction> apiVoid(
            @PathVariable("id") Transaction transaction,
            @Valid @RequestBody VoidTransactionDto dto,
            Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "system";
        return ResponseEntity.ok(transactionService.voidTransaction(transaction.getId(), dto.reason(), dto.notes(), username));
    }

    @DeleteMapping("/api/{id}")
    @ResponseBody
    @PreAuthorize("hasAuthority('" + Permission.TRANSACTION_DELETE + "')")
    public ResponseEntity<Void> apiDelete(@PathVariable("id") Transaction transaction) {
        transactionService.delete(transaction.getId());
        return ResponseEntity.ok().build();
    }

    private Transaction mapDtoToEntity(TransactionDto dto) {
        Transaction transaction = new Transaction();

        // Set transactionNumber if provided (for updates - it comes from existing record)
        // For creates, this will be null and generated by the service
        if (dto.transactionNumber() != null) {
            transaction.setTransactionNumber(dto.transactionNumber());
        }

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

    /**
     * Add attributes for DETAILED template support (formula variables).
     */
    private void addDetailedTemplateAttributes(JournalTemplate template, Model model) {
        boolean isDetailedTemplate = template.getTemplateType() == TemplateType.DETAILED;
        model.addAttribute("isDetailedTemplate", isDetailedTemplate);
        model.addAttribute("formulaVariables", isDetailedTemplate
                ? extractFormulaVariables(template)
                : java.util.List.of());
    }

    private java.util.List<FormulaVariable> extractFormulaVariables(JournalTemplate template) {
        java.util.Map<String, FormulaVariable> uniqueVariables = new java.util.LinkedHashMap<>();
        for (JournalTemplateLine line : template.getLines()) {
            String formula = line.getFormula();
            if (isFormulaVariable(formula)) {
                String varName = formula.trim();
                uniqueVariables.computeIfAbsent(varName, k -> new FormulaVariable(k, getVariableLabel(line, k)));
            }
        }
        return new java.util.ArrayList<>(uniqueVariables.values());
    }

    private boolean isFormulaVariable(String formula) {
        return isSimpleVariable(formula) && !"amount".equalsIgnoreCase(formula);
    }

    private String getVariableLabel(JournalTemplateLine line, String defaultLabel) {
        if (line.getDescription() != null) {
            return line.getDescription();
        }
        return line.getAccount() != null ? line.getAccount().getAccountName() : defaultLabel;
    }

    private boolean isSimpleVariable(String formula) {
        if (formula == null || formula.isBlank()) {
            return false;
        }
        String trimmed = formula.trim();
        return !trimmed.isEmpty() && isValidIdentifier(trimmed);
    }

    private boolean isValidIdentifier(String str) {
        char first = str.charAt(0);
        if (!Character.isLetter(first) && first != '_') {
            return false;
        }
        for (int i = 1; i < str.length(); i++) {
            char c = str.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return false;
            }
        }
        return true;
    }

    public record FormulaVariable(String name, String label) {}

    // ========== Quick Transaction Endpoints ==========

    /**
     * Get template picker for quick transaction modal.
     * Returns frequent and recent templates.
     */
    @GetMapping("/quick/templates")
    @PreAuthorize("hasAuthority('" + Permission.TRANSACTION_CREATE + "')")
    public String quickTemplates(Model model) {
        var frequentTemplates = dashboardService.getFrequentTemplates(6);
        var recentTemplates = dashboardService.getRecentTemplates(5);

        model.addAttribute("frequentTemplates", frequentTemplates);
        model.addAttribute("recentTemplates", recentTemplates);

        return "fragments/quick-transaction-templates :: templates";
    }

    /**
     * Get quick transaction form for selected template.
     * Loads a minimal form with the most common fields.
     */
    @GetMapping("/quick/form")
    @PreAuthorize("hasAuthority('" + Permission.TRANSACTION_CREATE + "')")
    public String quickForm(@RequestParam UUID templateId, Model model) {
        JournalTemplate template = journalTemplateService.findByIdWithLines(templateId);

        model.addAttribute(ATTR_SELECTED_TEMPLATE, template);
        model.addAttribute(ATTR_ACCOUNTS, chartOfAccountService.findTransactableAccounts());
        model.addAttribute(ATTR_PROJECTS, projectService.findActiveProjects());

        // Add DETAILED template support
        addDetailedTemplateAttributes(template, model);

        return "fragments/quick-transaction-form :: form";
    }

    /**
     * Handle quick transaction form submission.
     * Creates a transaction from the simplified quick form.
     */
    @PostMapping("/quick")
    @PreAuthorize("hasAuthority('" + Permission.TRANSACTION_CREATE + "')")
    public String quickCreate(
            @RequestParam UUID templateId,
            @RequestParam BigDecimal amount,
            @RequestParam String description,
            @RequestParam(required = false) LocalDate transactionDate,
            @RequestParam(required = false) String referenceNumber,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) Map<String, String> accountMapping,
            RedirectAttributes redirectAttributes) {

        // Create transaction from template
        Transaction transaction = new Transaction();
        JournalTemplate template = journalTemplateService.findByIdWithLines(templateId);
        transaction.setJournalTemplate(template);
        transaction.setAmount(amount);
        transaction.setDescription(description);
        transaction.setTransactionDate(transactionDate != null ? transactionDate : LocalDate.now());
        transaction.setReferenceNumber(referenceNumber);
        transaction.setNotes(notes);

        // Convert account mappings to UUID map
        Map<UUID, UUID> accountMappings = null;
        if (accountMapping != null && !accountMapping.isEmpty()) {
            accountMappings = new java.util.HashMap<>();
            for (var entry : accountMapping.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                    accountMappings.put(UUID.fromString(entry.getKey()), UUID.fromString(entry.getValue()));
                }
            }
        }

        Transaction saved = transactionService.create(transaction, accountMappings, null);

        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Transaksi berhasil dibuat");
        return REDIRECT_TRANSACTIONS + saved.getId();
    }

    /**
     * Search templates for autocomplete.
     * Returns matching templates as HTML fragment.
     */
    @GetMapping("/templates/search")
    @PreAuthorize("hasAuthority('" + Permission.TRANSACTION_CREATE + "')")
    public String searchTemplates(
            @RequestParam(required = false, defaultValue = "") String q,
            Model model) {

        String query = q.trim().toLowerCase();

        // If query is empty, return recent/frequent templates
        if (query.isEmpty()) {
            var recentTemplates = dashboardService.getRecentTemplates(8);
            model.addAttribute(ATTR_TEMPLATES, recentTemplates);
            model.addAttribute("showRecent", true);
        } else {
            // Search templates by name
            List<JournalTemplate> allTemplates = journalTemplateService.findAll();
            List<JournalTemplate> matchingTemplates = allTemplates.stream()
                    .filter(t -> t.getTemplateName().toLowerCase().contains(query))
                    .limit(8)
                    .toList();

            model.addAttribute(ATTR_TEMPLATES, matchingTemplates);
            model.addAttribute("showRecent", false);
        }

        return "fragments/template-search-results :: results";
    }
}
