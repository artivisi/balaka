package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.dto.FormulaContext;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.DraftTransaction;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.JournalTemplateLine;
import com.artivisi.accountingfinance.entity.Project;
import com.artivisi.accountingfinance.entity.Tag;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.entity.TransactionAccountMapping;
import com.artivisi.accountingfinance.entity.TransactionSequence;
import com.artivisi.accountingfinance.entity.TransactionTag;
import com.artivisi.accountingfinance.entity.TransactionVariable;
import com.artivisi.accountingfinance.enums.JournalPosition;
import com.artivisi.accountingfinance.enums.TemplateCategory;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.enums.VoidReason;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.DraftTransactionRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.ProjectRepository;
import com.artivisi.accountingfinance.repository.TagRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.repository.TransactionSequenceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Validated
@Slf4j
@Transactional(readOnly = true)
public class TransactionService {

    private static final String ERR_TRANSACTION_NOT_FOUND = "Transaction not found with id: ";

    private final TransactionRepository transactionRepository;
    private final TransactionSequenceRepository transactionSequenceRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final ChartOfAccountRepository chartOfAccountRepository;
    private final DraftTransactionRepository draftTransactionRepository;
    private final ProjectRepository projectRepository;
    private final TagRepository tagRepository;
    private final JournalTemplateService journalTemplateService;
    private final FormulaEvaluator formulaEvaluator;
    private final TaxTransactionDetailService taxTransactionDetailService;
    private final FiscalPeriodService fiscalPeriodService;
    private final EntityManager entityManager;

    public List<Transaction> findAll() {
        return transactionRepository.findAll();
    }

    public List<Transaction> findByStatus(TransactionStatus status) {
        return transactionRepository.findByStatusOrderByTransactionDateDesc(status);
    }

    public long countByStatus(TransactionStatus status) {
        return transactionRepository.countByStatus(status);
    }

    public Page<Transaction> findByFilters(TransactionStatus status, TemplateCategory category,
                                           LocalDate startDate, LocalDate endDate, Pageable pageable) {
        return findByFilters(status, category, null, startDate, endDate, pageable);
    }

    public Page<Transaction> findByFilters(TransactionStatus status, TemplateCategory category, UUID projectId,
                                           LocalDate startDate, LocalDate endDate, Pageable pageable) {
        String statusName = status != null ? status.name() : null;
        String categoryName = category != null ? category.name() : null;
        return transactionRepository.findByFilters(statusName, categoryName, projectId, startDate, endDate, pageable);
    }

    public Page<Transaction> findByFilters(TransactionStatus status, TemplateCategory category, UUID projectId,
                                           UUID tagId, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        String statusName = status != null ? status.name() : null;
        String categoryName = category != null ? category.name() : null;
        return transactionRepository.findByFiltersWithTag(statusName, categoryName, projectId, tagId, startDate, endDate, pageable);
    }

    public Page<Transaction> search(String search, Pageable pageable) {
        return transactionRepository.searchTransactions(search, pageable);
    }

    public Transaction findById(UUID id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(ERR_TRANSACTION_NOT_FOUND + id));
    }

    public Transaction findByIdWithJournalEntries(UUID id) {
        return transactionRepository.findByIdWithJournalEntries(id)
                .orElseThrow(() -> new EntityNotFoundException(ERR_TRANSACTION_NOT_FOUND + id));
    }

    public Transaction findByIdWithMappingsAndVariables(UUID id) {
        return transactionRepository.findByIdWithMappingsAndVariables(id)
                .orElseThrow(() -> new EntityNotFoundException(ERR_TRANSACTION_NOT_FOUND + id));
    }

    // Delegates to the full create method which handles the transaction
    // No @Transactional needed - participates in caller's transaction or the delegated method's transaction
    // Internal call is intentional - this is a convenience overload, not a transactional boundary
    @SuppressWarnings("java:S6809")
    public Transaction create(Transaction transaction, Map<UUID, UUID> accountMappings) {
        return create(transaction, accountMappings, null);
    }

    /**
     * Create a transaction with optional variables for DETAILED templates.
     * Variables are stored in the transaction_variables table for use during posting.
     *
     * @param transaction the transaction entity
     * @param accountMappings map of template line ID to account ID for dynamic account selection
     * @param variables map of variable name to value for DETAILED templates (can be null)
     */
    @Transactional
    public Transaction create(Transaction transaction, Map<UUID, UUID> accountMappings,
                              Map<String, BigDecimal> variables) {
        // Transaction number is NOT generated here - it will be generated when posting
        // This avoids gaps in numbering when drafts are deleted
        transaction.setTransactionNumber(null);
        transaction.setStatus(TransactionStatus.DRAFT);

        JournalTemplate template = journalTemplateService.findByIdWithLines(transaction.getJournalTemplate().getId());
        transaction.setJournalTemplate(template);

        // Load and set project if specified
        if (transaction.getProject() != null && transaction.getProject().getId() != null) {
            Project project = projectRepository.findById(transaction.getProject().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Project not found"));
            transaction.setProject(project);
        } else {
            transaction.setProject(null);
        }

        if (accountMappings != null && !accountMappings.isEmpty()) {
            for (JournalTemplateLine line : template.getLines()) {
                UUID overrideAccountId = accountMappings.get(line.getId());
                if (overrideAccountId != null) {
                    ChartOfAccount account = chartOfAccountRepository.findById(overrideAccountId)
                            .orElseThrow(() -> new EntityNotFoundException("Account not found"));
                    TransactionAccountMapping mapping = new TransactionAccountMapping();
                    mapping.setTemplateLine(line);
                    mapping.setAccount(account);
                    transaction.addAccountMapping(mapping);
                }
            }
        }

        // Store variables for DETAILED templates (used during posting)
        if (variables != null && !variables.isEmpty()) {
            for (Map.Entry<String, BigDecimal> entry : variables.entrySet()) {
                TransactionVariable variable = new TransactionVariable(entry.getKey(), entry.getValue());
                transaction.addVariable(variable);
            }
        }

        journalTemplateService.recordUsage(template.getId());
        return transactionRepository.save(transaction);
    }

    @Transactional
    public Transaction update(@Valid Transaction existing, Transaction transactionData) {
        if (!existing.isDraft()) {
            throw new IllegalStateException("Only draft transactions can be edited");
        }

        // Update only the editable fields, preserve system-generated fields like transactionNumber
        // The existing entity already has transactionNumber from database, we don't touch it
        existing.setTransactionDate(transactionData.getTransactionDate());
        existing.setAmount(transactionData.getAmount());
        existing.setDescription(transactionData.getDescription());
        existing.setReferenceNumber(transactionData.getReferenceNumber());
        existing.setNotes(transactionData.getNotes());

        // Validation happens when saving the existing entity which has all required fields
        return transactionRepository.save(existing);
    }

    @Transactional
    public void assignTags(Transaction transaction, List<UUID> tagIds) {
        List<TransactionTag> newTags = new ArrayList<>();
        if (tagIds != null) {
            for (UUID tagId : tagIds) {
                Tag tag = tagRepository.findById(tagId)
                        .orElseThrow(() -> new EntityNotFoundException("Tag not found: " + tagId));
                TransactionTag tt = new TransactionTag();
                tt.setTransaction(transaction);
                tt.setTag(tag);
                newTags.add(tt);
            }
        }
        transaction.setTransactionTags(newTags);
        transactionRepository.save(transaction);
    }

    @Transactional
    public Transaction post(UUID id, String postedBy) {
        Transaction transaction = findById(id);

        // Build context from stored variables (for DETAILED templates) or use amount (for SIMPLE)
        FormulaContext context;
        if (!transaction.getVariables().isEmpty()) {
            // DETAILED template - use stored variables
            Map<String, BigDecimal> variables = new HashMap<>();
            for (TransactionVariable tv : transaction.getVariables()) {
                variables.put(tv.getVariableName(), tv.getVariableValue());
            }
            context = FormulaContext.of(transaction.getAmount(), variables);
        } else {
            // SIMPLE template - use transaction amount
            context = FormulaContext.of(transaction.getAmount());
        }

        return postWithContext(transaction, postedBy, context);
    }

    /**
     * Post a transaction with a custom FormulaContext.
     * Used by external modules (payroll, inventory, etc.) that need to provide
     * module-specific variables for formula evaluation.
     *
     * @param id the transaction ID
     * @param postedBy who is posting
     * @param context custom FormulaContext with extended variables
     * @return the posted transaction
     */
    @Transactional
    public Transaction post(UUID id, String postedBy, FormulaContext context) {
        Transaction transaction = findById(id);
        return postWithContext(transaction, postedBy, context);
    }

    private Transaction postWithContext(Transaction transaction, String postedBy, FormulaContext context) {
        if (!transaction.isDraft()) {
            throw new IllegalStateException("Only draft transactions can be posted");
        }

        fiscalPeriodService.validatePeriodOpenForPosting(transaction.getTransactionDate());

        // Generate transaction number at posting time (not at draft creation)
        // This avoids gaps in numbering when drafts are deleted
        if (transaction.getTransactionNumber() == null) {
            transaction.setTransactionNumber(generateTransactionNumber());
        }

        // Check if transaction already has journal entries (created via TemplateExecutionEngine)
        // In this case, we just need to validate, assign journal numbers, and update status
        if (!transaction.getJournalEntries().isEmpty()) {
            assignJournalNumbersIfNeeded(transaction);
        } else {
            // No existing journal entries - create them from template (traditional flow)
            createJournalEntriesFromTemplate(transaction, context);
        }

        validateJournalBalance(transaction.getJournalEntries());

        transaction.setStatus(TransactionStatus.POSTED);
        transaction.setPostedAt(LocalDateTime.now());
        transaction.setPostedBy(postedBy);

        Transaction saved = transactionRepository.save(transaction);
        taxTransactionDetailService.autoPopulateFromTransaction(saved);
        return saved;
    }

    private void assignJournalNumbersIfNeeded(Transaction transaction) {
        boolean needsJournalNumber = transaction.getJournalEntries().stream()
                .anyMatch(e -> e.getJournalNumber() == null);
        if (needsJournalNumber) {
            String journalNumber = generateJournalNumber();
            int lineIndex = 0;
            for (JournalEntry entry : transaction.getJournalEntries()) {
                entry.setJournalNumber(journalNumber + "-" + String.format("%02d", ++lineIndex));
            }
        }
    }

    private void createJournalEntriesFromTemplate(Transaction transaction, FormulaContext context) {
        JournalTemplate template = journalTemplateService.findByIdWithLines(transaction.getJournalTemplate().getId());
        Map<UUID, ChartOfAccount> accountOverrides = new HashMap<>();
        for (TransactionAccountMapping mapping : transaction.getAccountMappings()) {
            accountOverrides.put(mapping.getTemplateLine().getId(), mapping.getAccount());
        }

        String journalNumber = generateJournalNumber();
        int lineIndex = 0;

        List<JournalEntry> addedEntries = new ArrayList<>();
        List<String> addedFormulas = new ArrayList<>();

        for (JournalTemplateLine line : template.getLines()) {
            ChartOfAccount account = accountOverrides.getOrDefault(line.getId(), line.getAccount());
            if (account == null) {
                throw new IllegalArgumentException(
                        "Account not specified for template line: " + line.getAccountHint());
            }
            BigDecimal amount = calculateAmount(line.getFormula(), context);

            // Skip zero-amount lines (e.g., PPh21 = 0 for low-salary employees)
            if (amount.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            JournalEntry entry = new JournalEntry();
            entry.setJournalNumber(journalNumber + "-" + String.format("%02d", ++lineIndex));
            entry.setAccount(account);

            if (transaction.getProject() != null) {
                entry.setProject(transaction.getProject());
            }

            if (line.getPosition() == JournalPosition.DEBIT) {
                entry.setDebitAmount(amount);
                entry.setCreditAmount(BigDecimal.ZERO);
            } else {
                entry.setDebitAmount(BigDecimal.ZERO);
                entry.setCreditAmount(amount);
            }

            transaction.addJournalEntry(entry);
            addedEntries.add(entry);
            addedFormulas.add(line.getFormula());
        }

        JournalBalancer.absorbRoundingResidual(addedEntries, addedFormulas);
    }

    @Transactional
    public Transaction voidTransaction(UUID id, VoidReason reason, String notes, String voidedBy) {
        Transaction transaction = findById(id);

        if (!transaction.isPosted()) {
            throw new IllegalStateException("Only posted transactions can be voided");
        }

        String reversalJournalNumber = generateJournalNumber();
        int lineIndex = 0;

        // Copy to avoid ConcurrentModificationException when adding reversals
        List<JournalEntry> originalEntries = new ArrayList<>(transaction.getJournalEntries());
        for (JournalEntry original : originalEntries) {
            JournalEntry reversal = new JournalEntry();
            reversal.setJournalNumber(reversalJournalNumber + "-" + String.format("%02d", ++lineIndex));
            reversal.setAccount(original.getAccount());
            reversal.setDebitAmount(original.getCreditAmount());
            reversal.setCreditAmount(original.getDebitAmount());
            reversal.setIsReversal(true);
            reversal.setReversedEntry(original);

            transaction.addJournalEntry(reversal);
        }

        transaction.setStatus(TransactionStatus.VOID);
        transaction.setVoidReason(reason);
        transaction.setVoidNotes(notes);
        transaction.setVoidedAt(LocalDateTime.now());
        transaction.setVoidedBy(voidedBy);

        return transactionRepository.save(transaction);
    }

    @Transactional
    public Transaction saveDirectly(Transaction transaction) {
        return transactionRepository.save(transaction);
    }

    /**
     * Replace account mappings on a DRAFT transaction.
     * Clears existing mappings and creates new ones from the provided map.
     * @param transaction the transaction to update
     * @param accountMappings map of template line ID to account ID
     */
    @Transactional
    public void replaceAccountMappings(Transaction transaction, Map<UUID, UUID> accountMappings) {
        JournalTemplate template = journalTemplateService.findByIdWithLines(transaction.getJournalTemplate().getId());

        transaction.clearAccountMappings();
        transactionRepository.save(transaction);
        transactionRepository.flush();
        for (JournalTemplateLine line : template.getLines()) {
            UUID overrideAccountId = accountMappings.get(line.getId());
            if (overrideAccountId != null) {
                ChartOfAccount account = chartOfAccountRepository.findById(overrideAccountId)
                        .orElseThrow(() -> new EntityNotFoundException("Account not found: " + overrideAccountId));
                TransactionAccountMapping mapping = new TransactionAccountMapping();
                mapping.setTemplateLine(line);
                mapping.setAccount(account);
                transaction.addAccountMapping(mapping);
            }
        }
        transactionRepository.save(transaction);
    }

    /**
     * Purge (hard-delete) all voided transactions, optionally limited to those
     * with transaction_date before the given cutoff date.
     *
     * @param before optional cutoff date (exclusive); null means purge all voided
     * @return list of purged transactions with their details
     */
    @Transactional
    public List<PurgedTransaction> purgeVoidedTransactions(LocalDate before) {
        List<Transaction> voidedTransactions;
        if (before != null) {
            voidedTransactions = transactionRepository
                    .findByStatusAndTransactionDateBeforeOrderByTransactionDateAsc(TransactionStatus.VOID, before);
        } else {
            voidedTransactions = transactionRepository
                    .findByStatusOrderByTransactionDateDesc(TransactionStatus.VOID);
        }

        if (voidedTransactions.isEmpty()) {
            return List.of();
        }

        // Collect details before deletion
        List<PurgedTransaction> purged = voidedTransactions.stream()
                .map(tx -> new PurgedTransaction(
                        tx.getId(),
                        tx.getTransactionNumber(),
                        tx.getTransactionDate(),
                        tx.getAmount(),
                        tx.getDescription(),
                        tx.getVoidReason(),
                        tx.getVoidedAt(),
                        tx.getVoidedBy()))
                .toList();

        List<UUID> ids = voidedTransactions.stream().map(Transaction::getId).toList();
        log.info("Purging {} voided transactions: {}", ids.size(), ids);

        // Null out nullable FK references from other tables
        nullOutForeignKeyReferences(ids);

        // Clear self-referencing FK in journal_entries (reversed_entry)
        entityManager.createNativeQuery(
                "UPDATE journal_entries SET id_reversed_entry = NULL WHERE id_transaction IN :ids")
                .setParameter("ids", ids)
                .executeUpdate();

        // Delete from child tables without DB CASCADE
        entityManager.createNativeQuery(
                "DELETE FROM journal_entries WHERE id_transaction IN :ids")
                .setParameter("ids", ids)
                .executeUpdate();

        entityManager.createNativeQuery(
                "DELETE FROM transaction_tags WHERE id_transaction IN :ids")
                .setParameter("ids", ids)
                .executeUpdate();

        // Delete transactions (DB CASCADE handles: transaction_account_mappings,
        // transaction_variables, documents, tax_transaction_details)
        entityManager.createNativeQuery(
                "DELETE FROM transactions WHERE id IN :ids")
                .setParameter("ids", ids)
                .executeUpdate();

        log.info("Purged {} voided transactions", purged.size());
        return purged;
    }

    private void nullOutForeignKeyReferences(List<UUID> transactionIds) {
        String[] nullOutQueries = {
            "UPDATE invoices SET id_transaction = NULL WHERE id_transaction IN :ids",
            "UPDATE bills SET id_transaction = NULL WHERE id_transaction IN :ids",
            "UPDATE draft_transactions SET id_transaction = NULL WHERE id_transaction IN :ids",
            "UPDATE payroll_runs SET id_transaction = NULL WHERE id_transaction IN :ids",
            "UPDATE fixed_assets SET id_purchase_transaction = NULL WHERE id_purchase_transaction IN :ids",
            "UPDATE fixed_assets SET id_disposal_transaction = NULL WHERE id_disposal_transaction IN :ids",
            "UPDATE depreciation_entries SET id_transaction = NULL WHERE id_transaction IN :ids",
            "UPDATE inventory_transactions SET id_transaction = NULL WHERE id_transaction IN :ids",
            "UPDATE production_orders SET id_transaction = NULL WHERE id_transaction IN :ids",
            "UPDATE bank_statement_items SET id_matched_transaction = NULL WHERE id_matched_transaction IN :ids",
            "UPDATE reconciliation_items SET id_transaction = NULL WHERE id_transaction IN :ids",
            "UPDATE recurring_transaction_logs SET id_transaction = NULL WHERE id_transaction IN :ids",
        };

        for (String query : nullOutQueries) {
            entityManager.createNativeQuery(query)
                    .setParameter("ids", transactionIds)
                    .executeUpdate();
        }
    }

    public record PurgedTransaction(
            UUID id,
            String transactionNumber,
            LocalDate transactionDate,
            BigDecimal amount,
            String description,
            VoidReason voidReason,
            LocalDateTime voidedAt,
            String voidedBy
    ) {}

    @Transactional
    public void delete(UUID id) {
        Transaction transaction = findById(id);
        if (!transaction.isDraft()) {
            throw new IllegalStateException("Only draft transactions can be deleted");
        }

        // Clear DraftTransaction FK reference if this transaction was created from a draft
        draftTransactionRepository.findByTransactionId(id).ifPresent(draft -> {
            draft.setTransaction(null);
            draftTransactionRepository.save(draft);
        });

        transactionRepository.delete(transaction);
    }

    private String generateTransactionNumber() {
        int year = LocalDate.now().getYear();
        TransactionSequence sequence = transactionSequenceRepository
                .findBySequenceTypeAndYearForUpdate("TRANSACTION", year)
                .orElseGet(() -> {
                    TransactionSequence newSeq = new TransactionSequence();
                    newSeq.setSequenceType("TRANSACTION");
                    newSeq.setPrefix("TRX");
                    newSeq.setYear(year);
                    newSeq.setLastNumber(0);
                    return transactionSequenceRepository.save(newSeq);
                });

        String number = sequence.getNextNumber();
        transactionSequenceRepository.save(sequence);
        return number;
    }

    private String generateJournalNumber() {
        int year = LocalDate.now().getYear();
        TransactionSequence sequence = transactionSequenceRepository
                .findBySequenceTypeAndYearForUpdate("JOURNAL", year)
                .orElseGet(() -> {
                    TransactionSequence newSeq = new TransactionSequence();
                    newSeq.setSequenceType("JOURNAL");
                    newSeq.setPrefix("JE");
                    newSeq.setYear(year);
                    newSeq.setLastNumber(0);
                    return transactionSequenceRepository.save(newSeq);
                });

        String number = sequence.getNextNumber();
        transactionSequenceRepository.save(sequence);
        return number;
    }

    /**
     * Calculate amount using unified FormulaEvaluator.
     *
     * @see FormulaEvaluator
     */
    private BigDecimal calculateAmount(String formula, FormulaContext context) {
        return formulaEvaluator.evaluate(formula, context);
    }

    private void validateJournalBalance(List<JournalEntry> entries) {
        BigDecimal totalDebit = entries.stream()
                .map(JournalEntry::getDebitAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredit = entries.stream()
                .map(JournalEntry::getCreditAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new IllegalStateException(
                    String.format("Journal not balanced: Debit=%s, Credit=%s", totalDebit, totalCredit));
        }
    }

    @Transactional
    public Transaction createFromReconciliation(UUID templateId, LocalDate transactionDate,
                                                 BigDecimal amount, String description, String createdBy) {
        JournalTemplate template = journalTemplateService.findByIdWithLines(templateId);

        Transaction transaction = new Transaction();
        transaction.setTransactionNumber(null);
        transaction.setTransactionDate(transactionDate);
        transaction.setJournalTemplate(template);
        transaction.setAmount(amount);
        transaction.setDescription(description);
        transaction.setStatus(TransactionStatus.DRAFT);
        transaction.setCreatedBy(createdBy);

        journalTemplateService.recordUsage(template.getId());
        return transactionRepository.save(transaction);
    }

    @Transactional
    public Transaction createFromRecurring(UUID templateId, LocalDate transactionDate,
                                            BigDecimal amount, String description, String createdBy,
                                            Map<UUID, UUID> accountMappings) {
        JournalTemplate template = journalTemplateService.findByIdWithLines(templateId);

        Transaction transaction = new Transaction();
        transaction.setTransactionNumber(null);
        transaction.setTransactionDate(transactionDate);
        transaction.setJournalTemplate(template);
        transaction.setAmount(amount);
        transaction.setDescription(description);
        transaction.setStatus(TransactionStatus.DRAFT);
        transaction.setCreatedBy(createdBy);

        if (accountMappings != null && !accountMappings.isEmpty()) {
            for (JournalTemplateLine line : template.getLines()) {
                UUID overrideAccountId = accountMappings.get(line.getId());
                if (overrideAccountId != null) {
                    ChartOfAccount account = chartOfAccountRepository.findById(overrideAccountId)
                            .orElseThrow(() -> new EntityNotFoundException("Account not found"));
                    TransactionAccountMapping mapping = new TransactionAccountMapping();
                    mapping.setTemplateLine(line);
                    mapping.setAccount(account);
                    transaction.addAccountMapping(mapping);
                }
            }
        }

        journalTemplateService.recordUsage(template.getId());
        return transactionRepository.save(transaction);
    }

    @Transactional
    public Transaction createFromDraft(DraftTransaction draft, UUID templateId,
                                        String description, BigDecimal amount, String createdBy) {
        JournalTemplate template = journalTemplateService.findByIdWithLines(templateId);

        Transaction transaction = new Transaction();
        // Transaction number will be generated when posting
        transaction.setTransactionNumber(null);
        transaction.setTransactionDate(draft.getTransactionDate() != null ? draft.getTransactionDate() : LocalDate.now());
        transaction.setJournalTemplate(template);
        transaction.setAmount(amount != null ? amount : draft.getAmount());
        transaction.setDescription(description != null ? description : draft.getMerchantName());
        transaction.setReferenceNumber(draft.getSourceReference());
        transaction.setStatus(TransactionStatus.DRAFT);
        transaction.setCreatedBy(createdBy);

        journalTemplateService.recordUsage(template.getId());
        Transaction saved = transactionRepository.save(transaction);
        
        // Link the document from draft to transaction
        if (draft.getDocument() != null) {
            draft.getDocument().setTransaction(saved);
        }
        
        return saved;
    }
}
