package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.dto.FormulaContext;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.JournalTemplateLine;
import com.artivisi.accountingfinance.entity.Project;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.entity.TransactionAccountMapping;
import com.artivisi.accountingfinance.entity.TransactionSequence;
import com.artivisi.accountingfinance.enums.JournalPosition;
import com.artivisi.accountingfinance.enums.TemplateCategory;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.enums.VoidReason;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.ProjectRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.repository.TransactionSequenceRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@Transactional(readOnly = true)
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionSequenceRepository transactionSequenceRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final ChartOfAccountRepository chartOfAccountRepository;
    private final ProjectRepository projectRepository;
    private final JournalTemplateService journalTemplateService;
    private final FormulaEvaluator formulaEvaluator;

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
        return transactionRepository.findByFilters(status, category, null, startDate, endDate, pageable);
    }

    public Page<Transaction> findByFilters(TransactionStatus status, TemplateCategory category, UUID projectId,
                                           LocalDate startDate, LocalDate endDate, Pageable pageable) {
        return transactionRepository.findByFilters(status, category, projectId, startDate, endDate, pageable);
    }

    public Page<Transaction> search(String search, Pageable pageable) {
        return transactionRepository.searchTransactions(search, pageable);
    }

    public Transaction findById(UUID id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found with id: " + id));
    }

    public Transaction findByIdWithJournalEntries(UUID id) {
        return transactionRepository.findByIdWithJournalEntries(id)
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found with id: " + id));
    }

    @Transactional
    public Transaction create(Transaction transaction, Map<UUID, UUID> accountMappings) {
        String transactionNumber = generateTransactionNumber();
        transaction.setTransactionNumber(transactionNumber);
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

        journalTemplateService.recordUsage(template.getId());
        return transactionRepository.save(transaction);
    }

    @Transactional
    public Transaction update(UUID id, @Valid Transaction transactionData) {
        Transaction existing = findById(id);

        if (!existing.isDraft()) {
            throw new IllegalStateException("Only draft transactions can be edited");
        }

        existing.setTransactionDate(transactionData.getTransactionDate());
        existing.setAmount(transactionData.getAmount());
        existing.setDescription(transactionData.getDescription());
        existing.setReferenceNumber(transactionData.getReferenceNumber());
        existing.setNotes(transactionData.getNotes());

        return transactionRepository.save(existing);
    }

    @Transactional
    public Transaction post(UUID id, String postedBy) {
        Transaction transaction = findById(id);

        if (!transaction.isDraft()) {
            throw new IllegalStateException("Only draft transactions can be posted");
        }

        JournalTemplate template = journalTemplateService.findByIdWithLines(transaction.getJournalTemplate().getId());
        Map<UUID, ChartOfAccount> accountOverrides = new HashMap<>();
        for (TransactionAccountMapping mapping : transaction.getAccountMappings()) {
            accountOverrides.put(mapping.getTemplateLine().getId(), mapping.getAccount());
        }

        String journalNumber = generateJournalNumber();
        int lineIndex = 0;

        for (JournalTemplateLine line : template.getLines()) {
            ChartOfAccount account = accountOverrides.getOrDefault(line.getId(), line.getAccount());
            BigDecimal amount = calculateAmount(line.getFormula(), transaction.getAmount());

            JournalEntry entry = new JournalEntry();
            entry.setJournalNumber(journalNumber + "-" + String.format("%02d", ++lineIndex));
            entry.setJournalDate(transaction.getTransactionDate());
            entry.setAccount(account);
            entry.setDescription(transaction.getDescription());
            entry.setReferenceNumber(transaction.getReferenceNumber());

            // Assign project from transaction to journal entry
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
        }

        validateJournalBalance(transaction.getJournalEntries());

        transaction.setStatus(TransactionStatus.POSTED);
        transaction.setPostedAt(LocalDateTime.now());
        transaction.setPostedBy(postedBy);

        return transactionRepository.save(transaction);
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
            reversal.setJournalDate(LocalDate.now());
            reversal.setAccount(original.getAccount());
            reversal.setDescription("Reversal: " + original.getDescription());
            reversal.setReferenceNumber(original.getReferenceNumber());
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
    public void delete(UUID id) {
        Transaction transaction = findById(id);
        if (!transaction.isDraft()) {
            throw new IllegalStateException("Only draft transactions can be deleted");
        }
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
    private BigDecimal calculateAmount(String formula, BigDecimal baseAmount) {
        return formulaEvaluator.evaluate(formula, FormulaContext.of(baseAmount));
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
}
