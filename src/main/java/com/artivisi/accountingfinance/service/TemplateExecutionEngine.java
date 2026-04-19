package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.dto.FormulaContext;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.JournalTemplateLine;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.JournalPosition;
import com.artivisi.accountingfinance.enums.TemplateType;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TemplateExecutionEngine {

    private final JournalEntryService journalEntryService;
    private final JournalTemplateService journalTemplateService;
    private final FormulaEvaluator formulaEvaluator;
    private final ChartOfAccountRepository chartOfAccountRepository;

    /**
     * Execute a template and create journal entries via Transaction.
     * Creates entries in DRAFT status.
     */
    @Transactional
    public ExecutionResult execute(JournalTemplate template, ExecutionContext context) {
        List<String> validationErrors = validate(template, context);
        if (!validationErrors.isEmpty()) {
            throw new IllegalArgumentException("Template validation failed: " + String.join(", ", validationErrors));
        }

        List<JournalEntry> entries = buildJournalEntries(template, context);
        List<String> formulas = template.getLines().stream()
                .map(JournalTemplateLine::getFormula)
                .toList();
        JournalBalancer.absorbRoundingResidual(entries, formulas);

        // Create Transaction as header
        Transaction transaction = new Transaction();
        transaction.setTransactionDate(context.transactionDate());
        transaction.setDescription(context.description());
        transaction.setReferenceNumber(context.referenceNumber());
        transaction.setJournalTemplate(template);

        Transaction saved = journalEntryService.create(transaction, entries);

        // Record usage
        journalTemplateService.recordUsage(template.getId());

        return new ExecutionResult(
                saved.getId(),
                saved.getJournalEntries()
        );
    }

    /**
     * Preview template execution without saving.
     */
    public PreviewResult preview(JournalTemplate template, ExecutionContext context) {
        List<String> validationErrors = validate(template, context);
        if (!validationErrors.isEmpty()) {
            return new PreviewResult(false, validationErrors, List.of(), BigDecimal.ZERO, BigDecimal.ZERO);
        }

        List<PreviewEntry> previewEntries = new ArrayList<>();
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        for (JournalTemplateLine line : template.getLines()) {
            BigDecimal lineAmount = evaluateFormula(line.getFormula(), context);
            BigDecimal debit = line.getPosition() == JournalPosition.DEBIT ? lineAmount : BigDecimal.ZERO;
            BigDecimal credit = line.getPosition() == JournalPosition.CREDIT ? lineAmount : BigDecimal.ZERO;

            AccountInfo accountInfo = resolveAccountInfo(line, context);

            previewEntries.add(new PreviewEntry(
                    accountInfo.code(),
                    accountInfo.name(),
                    context.description(),
                    debit,
                    credit
            ));

            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);
        }

        List<String> formulas = template.getLines().stream()
                .map(JournalTemplateLine::getFormula)
                .toList();
        List<PreviewEntry> balanced = JournalBalancer.absorbPreviewResidual(previewEntries, formulas);
        BigDecimal balancedDebit = balanced.stream()
                .map(PreviewEntry::debitAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal balancedCredit = balanced.stream()
                .map(PreviewEntry::creditAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PreviewResult(true, List.of(), balanced, balancedDebit, balancedCredit);
    }

    private record AccountInfo(String code, String name) {}

    private AccountInfo resolveAccountInfo(JournalTemplateLine line, ExecutionContext context) {
        if (line.getAccount() != null) {
            return new AccountInfo(line.getAccount().getAccountCode(), line.getAccount().getAccountName());
        }
        return resolveMappedAccount(line, context);
    }

    private AccountInfo resolveMappedAccount(JournalTemplateLine line, ExecutionContext context) {
        UUID mappedAccountId = context.getAccountIdForLine(line.getLineOrder());
        if (mappedAccountId != null) {
            ChartOfAccount mappedAccount = chartOfAccountRepository.findById(mappedAccountId).orElse(null);
            if (mappedAccount != null) {
                return new AccountInfo(mappedAccount.getAccountCode(), mappedAccount.getAccountName());
            }
        }
        String hint = line.getAccountHint() != null ? line.getAccountHint() : "Pilih saat transaksi";
        return new AccountInfo("?", hint);
    }

    /**
     * Validate template and execution context.
     */
    public List<String> validate(JournalTemplate template, ExecutionContext context) {
        List<String> errors = new ArrayList<>();

        if (template == null) {
            errors.add("Template is required");
            return errors;
        }

        validateTemplate(template, errors);

        if (context == null) {
            errors.add("Execution context is required");
            return errors;
        }

        validateContext(context, template.getTemplateType() == TemplateType.DETAILED, errors);
        return errors;
    }

    private void validateTemplate(JournalTemplate template, List<String> errors) {
        if (!template.getActive()) {
            errors.add("Template is not active");
        }
        if (template.getLines() == null || template.getLines().size() < 2) {
            errors.add("Template must have at least 2 lines");
        }
    }

    private void validateContext(ExecutionContext context, boolean isDetailedTemplate, List<String> errors) {
        if (context.transactionDate() == null) {
            errors.add("Transaction date is required");
        }

        if (isDetailedTemplate) {
            validateDetailedTemplateVariables(context, errors);
        } else {
            validateSimpleTemplateAmount(context, errors);
        }

        if (context.description() == null || context.description().isBlank()) {
            errors.add("Description is required");
        }
    }

    private void validateDetailedTemplateVariables(ExecutionContext context, List<String> errors) {
        boolean hasValidVariable = context.variables() != null &&
                context.variables().values().stream()
                        .anyMatch(v -> v != null && v.compareTo(BigDecimal.ZERO) > 0);
        if (!hasValidVariable) {
            errors.add("At least one variable must have a positive value");
        }
    }

    private void validateSimpleTemplateAmount(ExecutionContext context, List<String> errors) {
        if (context.amount() == null) {
            errors.add("Amount is required");
        } else if (context.amount().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Amount must be positive");
        }
    }

    private List<JournalEntry> buildJournalEntries(JournalTemplate template, ExecutionContext context) {
        List<JournalEntry> entries = new ArrayList<>();

        for (JournalTemplateLine line : template.getLines()) {
            JournalEntry entry = new JournalEntry();

            // Use template's account or mapped account for dynamic selection
            if (line.getAccount() != null) {
                entry.setAccount(line.getAccount());
            } else {
                UUID mappedAccountId = context.getAccountIdForLine(line.getLineOrder());
                if (mappedAccountId != null) {
                    ChartOfAccount mappedAccount = chartOfAccountRepository.findById(mappedAccountId)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Account not found for line " + line.getLineOrder()));
                    entry.setAccount(mappedAccount);
                } else {
                    throw new IllegalArgumentException(
                            "Account must be selected for line " + line.getLineOrder());
                }
            }

            // Evaluate formula to get amount
            BigDecimal lineAmount = evaluateFormula(line.getFormula(), context);

            // Set debit or credit based on position
            if (line.getPosition() == JournalPosition.DEBIT) {
                entry.setDebitAmount(lineAmount);
                entry.setCreditAmount(BigDecimal.ZERO);
            } else {
                entry.setDebitAmount(BigDecimal.ZERO);
                entry.setCreditAmount(lineAmount);
            }

            entries.add(entry);
        }

        return entries;
    }

    /**
     * Evaluate formula expressions using FormulaEvaluator.
     * Delegates to unified SpEL-based evaluation.
     * Supports both SIMPLE templates (using 'amount') and DETAILED templates (using custom variables).
     *
     * @see FormulaEvaluator
     */
    BigDecimal evaluateFormula(String formula, ExecutionContext context) {
        FormulaContext formulaContext = FormulaContext.of(context.amount(), context.variables());
        return formulaEvaluator.evaluate(formula, formulaContext);
    }

    // Records for input/output

    public record ExecutionContext(
            LocalDate transactionDate,
            BigDecimal amount,
            String description,
            String referenceNumber,
            Map<String, BigDecimal> variables,
            Map<String, String> accountMappings
    ) {
        // Constructor without referenceNumber and variables for backward compatibility (SIMPLE templates)
        public ExecutionContext(LocalDate transactionDate, BigDecimal amount, String description) {
            this(transactionDate, amount, description, null, Map.of(), Map.of());
        }

        // Constructor with referenceNumber but no variables
        public ExecutionContext(LocalDate transactionDate, BigDecimal amount, String description, String referenceNumber) {
            this(transactionDate, amount, description, referenceNumber, Map.of(), Map.of());
        }

        // Constructor with variables but no account mappings (backward compatibility)
        public ExecutionContext(LocalDate transactionDate, BigDecimal amount, String description, String referenceNumber, Map<String, BigDecimal> variables) {
            this(transactionDate, amount, description, referenceNumber, variables, Map.of());
        }

        /**
         * Get account ID for a specific line order from account mappings.
         * @param lineOrder the line order number
         * @return UUID of the selected account, or null if not mapped
         */
        public UUID getAccountIdForLine(int lineOrder) {
            if (accountMappings == null) return null;
            String accountIdStr = accountMappings.get(String.valueOf(lineOrder));
            if (accountIdStr == null || accountIdStr.isBlank()) return null;
            return UUID.fromString(accountIdStr);
        }
    }

    public record ExecutionResult(
            UUID transactionId,
            List<JournalEntry> entries
    ) {}

    public record PreviewEntry(
            String accountCode,
            String accountName,
            String description,
            BigDecimal debitAmount,
            BigDecimal creditAmount
    ) {}

    public record PreviewResult(
            boolean valid,
            List<String> errors,
            List<PreviewEntry> entries,
            BigDecimal totalDebit,
            BigDecimal totalCredit
    ) {}
}
