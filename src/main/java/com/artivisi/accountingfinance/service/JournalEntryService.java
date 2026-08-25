package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.NormalBalance;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.repository.TransactionSequenceRepository;
import com.artivisi.accountingfinance.entity.TransactionSequence;
import com.artivisi.accountingfinance.security.CurrentUser;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JournalEntryService {

    // Manual Journal Entry template name (looked up dynamically)
    private static final String MANUAL_ENTRY_TEMPLATE_NAME = "Jurnal Manual";

    private final JournalEntryRepository journalEntryRepository;
    private final ChartOfAccountRepository chartOfAccountRepository;
    private final TransactionRepository transactionRepository;
    private final JournalTemplateRepository journalTemplateRepository;
    private final TransactionSequenceRepository transactionSequenceRepository;

    // Helper to calculate balance based on account's normal balance
    private BigDecimal calculateBalance(NormalBalance normalBalance, BigDecimal debit, BigDecimal credit) {
        return normalBalance == NormalBalance.DEBIT
                ? debit.subtract(credit)
                : credit.subtract(debit);
    }

    private BigDecimal updateBalance(NormalBalance normalBalance, BigDecimal balance, BigDecimal debit, BigDecimal credit) {
        return normalBalance == NormalBalance.DEBIT
                ? balance.add(debit).subtract(credit)
                : balance.subtract(debit).add(credit);
    }

    public JournalEntry findById(UUID id) {
        return journalEntryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Journal entry not found with id: " + id));
    }

    public JournalEntry findByJournalNumber(String journalNumber) {
        List<JournalEntry> entries = journalEntryRepository.findAllByJournalNumberOrderByIdAsc(journalNumber);
        if (entries.isEmpty()) {
            throw new EntityNotFoundException("Journal entry not found with number: " + journalNumber);
        }
        return entries.get(0);
    }

    public List<JournalEntry> findByTransactionId(UUID transactionId) {
        return journalEntryRepository.findByTransactionIdOrderByJournalNumberAsc(transactionId);
    }

    public Page<JournalEntry> findAllByDateRange(LocalDate startDate, LocalDate endDate, Pageable pageable) {
        return journalEntryRepository.findAllPostedEntriesByDateRange(startDate, endDate, pageable);
    }

    public List<JournalEntry> findByAccountAndDateRange(UUID accountId, LocalDate startDate, LocalDate endDate) {
        return journalEntryRepository.findPostedEntriesByAccountAndDateRange(accountId, startDate, endDate);
    }

    public GeneralLedgerData getGeneralLedger(UUID accountId, LocalDate startDate, LocalDate endDate) {
        ChartOfAccount account = chartOfAccountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found"));

        BigDecimal openingDebit = journalEntryRepository.sumDebitBeforeDate(accountId, startDate);
        BigDecimal openingCredit = journalEntryRepository.sumCreditBeforeDate(accountId, startDate);

        BigDecimal openingBalance;
        if (account.getNormalBalance() == NormalBalance.DEBIT) {
            openingBalance = openingDebit.subtract(openingCredit);
        } else {
            openingBalance = openingCredit.subtract(openingDebit);
        }

        List<JournalEntry> entries = journalEntryRepository
                .findPostedEntriesByAccountAndDateRange(accountId, startDate, endDate);

        BigDecimal runningBalance = openingBalance;
        List<LedgerLineItem> lineItems = new ArrayList<>();

        for (JournalEntry entry : entries) {
            if (account.getNormalBalance() == NormalBalance.DEBIT) {
                runningBalance = runningBalance.add(entry.getDebitAmount()).subtract(entry.getCreditAmount());
            } else {
                runningBalance = runningBalance.subtract(entry.getDebitAmount()).add(entry.getCreditAmount());
            }

            lineItems.add(new LedgerLineItem(entry, runningBalance));
        }

        BigDecimal totalDebit = journalEntryRepository.sumDebitByAccountAndDateRange(accountId, startDate, endDate);
        BigDecimal totalCredit = journalEntryRepository.sumCreditByAccountAndDateRange(accountId, startDate, endDate);

        return new GeneralLedgerData(
                account,
                openingBalance,
                totalDebit,
                totalCredit,
                runningBalance,
                lineItems
        );
    }

    public GeneralLedgerPagedData getGeneralLedgerPaged(
            UUID accountId, LocalDate startDate, LocalDate endDate,
            String search, Pageable pageable) {

        ChartOfAccount account = chartOfAccountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found"));
        NormalBalance normalBalance = account.getNormalBalance();

        BigDecimal openingDebit = journalEntryRepository.sumDebitBeforeDate(accountId, startDate);
        BigDecimal openingCredit = journalEntryRepository.sumCreditBeforeDate(accountId, startDate);
        BigDecimal openingBalance = calculateBalance(normalBalance, openingDebit, openingCredit);

        Page<JournalEntry> entriesPage = fetchEntriesPage(accountId, startDate, endDate, search, pageable);

        BigDecimal pageStartBalance = calculatePageStartBalance(accountId, startDate, endDate, pageable, normalBalance, openingBalance);

        List<LedgerLineItem> lineItems = buildLineItems(entriesPage, normalBalance, pageStartBalance);

        BigDecimal totalDebit = journalEntryRepository.sumDebitByAccountAndDateRange(accountId, startDate, endDate);
        BigDecimal totalCredit = journalEntryRepository.sumCreditByAccountAndDateRange(accountId, startDate, endDate);
        BigDecimal closingBalance = updateBalance(normalBalance, openingBalance, totalDebit, totalCredit);

        return new GeneralLedgerPagedData(
                account,
                openingBalance,
                totalDebit,
                totalCredit,
                closingBalance,
                lineItems,
                entriesPage.getNumber(),
                entriesPage.getTotalPages(),
                entriesPage.getTotalElements(),
                entriesPage.hasNext(),
                entriesPage.hasPrevious()
        );
    }

    private Page<JournalEntry> fetchEntriesPage(UUID accountId, LocalDate startDate, LocalDate endDate,
            String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return journalEntryRepository.findPostedEntriesByAccountAndDateRangeAndSearchPaged(
                    accountId, startDate, endDate, search, pageable);
        }
        return journalEntryRepository.findPostedEntriesByAccountAndDateRangePaged(
                accountId, startDate, endDate, pageable);
    }

    private BigDecimal calculatePageStartBalance(UUID accountId, LocalDate startDate, LocalDate endDate,
            Pageable pageable, NormalBalance normalBalance, BigDecimal openingBalance) {
        if (pageable.getPageNumber() == 0) {
            return openingBalance;
        }

        List<JournalEntry> priorEntries = journalEntryRepository
                .findPostedEntriesByAccountAndDateRange(accountId, startDate, endDate);

        BigDecimal balance = openingBalance;
        int skipCount = (int) pageable.getOffset();
        for (int i = 0; i < Math.min(skipCount, priorEntries.size()); i++) {
            JournalEntry entry = priorEntries.get(i);
            balance = updateBalance(normalBalance, balance, entry.getDebitAmount(), entry.getCreditAmount());
        }
        return balance;
    }

    private List<LedgerLineItem> buildLineItems(Page<JournalEntry> entriesPage, NormalBalance normalBalance,
            BigDecimal pageStartBalance) {
        List<LedgerLineItem> lineItems = new ArrayList<>();
        BigDecimal runningBalance = pageStartBalance;

        for (JournalEntry entry : entriesPage.getContent()) {
            runningBalance = updateBalance(normalBalance, runningBalance, entry.getDebitAmount(), entry.getCreditAmount());
            lineItems.add(new LedgerLineItem(entry, runningBalance));
        }
        return lineItems;
    }

    public record GeneralLedgerData(
            ChartOfAccount account,
            BigDecimal openingBalance,
            BigDecimal totalDebit,
            BigDecimal totalCredit,
            BigDecimal closingBalance,
            List<LedgerLineItem> entries
    ) {}

    public record GeneralLedgerPagedData(
            ChartOfAccount account,
            BigDecimal openingBalance,
            BigDecimal totalDebit,
            BigDecimal totalCredit,
            BigDecimal closingBalance,
            List<LedgerLineItem> entries,
            int currentPage,
            int totalPages,
            long totalElements,
            boolean hasNext,
            boolean hasPrevious
    ) {}

    public record LedgerLineItem(
            JournalEntry entry,
            BigDecimal runningBalance
    ) {}

    // ========== Account Impact Calculation ==========

    /**
     * Calculate the impact of a journal entry on account balances.
     * Shows before balance, movement (debit/credit), and after balance for each account.
     */
    public List<AccountImpact> calculateAccountImpact(List<JournalEntry> entries) {
        if (entries.isEmpty()) {
            return List.of();
        }

        JournalEntry firstEntry = entries.get(0);
        LocalDate journalDate = firstEntry.getJournalDate();
        boolean isPosted = firstEntry.isPosted() || firstEntry.isVoid();
        String journalNumber = firstEntry.getJournalNumber();

        List<AccountImpact> impacts = new ArrayList<>();
        for (JournalEntry entry : entries) {
            if (entry.getAccount() != null) {
                impacts.add(calculateSingleAccountImpact(entry, journalDate, isPosted, journalNumber));
            }
        }
        return impacts;
    }

    private AccountImpact calculateSingleAccountImpact(JournalEntry entry, LocalDate journalDate,
            boolean isPosted, String journalNumber) {
        ChartOfAccount account = entry.getAccount();
        NormalBalance normalBalance = account.getNormalBalance();

        BigDecimal debitBefore = journalEntryRepository.sumDebitBeforeDate(account.getId(), journalDate);
        BigDecimal creditBefore = journalEntryRepository.sumCreditBeforeDate(account.getId(), journalDate);

        BigDecimal[] sameDateTotals = calculateSameDateTotals(account.getId(), journalDate, isPosted, journalNumber);
        BigDecimal debitOnDate = sameDateTotals[0];
        BigDecimal creditOnDate = sameDateTotals[1];

        BigDecimal beforeBalance = calculateBalance(normalBalance,
                debitBefore.add(debitOnDate), creditBefore.add(creditOnDate));
        BigDecimal afterBalance = updateBalance(normalBalance, beforeBalance,
                entry.getDebitAmount(), entry.getCreditAmount());

        return new AccountImpact(account, beforeBalance, entry.getDebitAmount(), entry.getCreditAmount(), afterBalance);
    }

    private BigDecimal[] calculateSameDateTotals(UUID accountId, LocalDate journalDate,
            boolean isPosted, String excludeJournalNumber) {
        BigDecimal debitOnDate = BigDecimal.ZERO;
        BigDecimal creditOnDate = BigDecimal.ZERO;

        if (isPosted) {
            List<JournalEntry> entriesOnDate = journalEntryRepository
                    .findPostedEntriesByAccountAndDateRange(accountId, journalDate, journalDate);

            for (JournalEntry e : entriesOnDate) {
                if (!e.getJournalNumber().equals(excludeJournalNumber)) {
                    debitOnDate = debitOnDate.add(e.getDebitAmount());
                    creditOnDate = creditOnDate.add(e.getCreditAmount());
                }
            }
        }
        return new BigDecimal[]{debitOnDate, creditOnDate};
    }

    public record AccountImpact(
            ChartOfAccount account,
            BigDecimal beforeBalance,
            BigDecimal debitMovement,
            BigDecimal creditMovement,
            BigDecimal afterBalance
    ) {}

    // ========== Manual Journal Entry Operations ==========

    /**
     * Create manual journal entries via Transaction.
     * Transaction serves as the header containing date, description, reference.
     * All entries are created in DRAFT status.
     */
    @Transactional
    public Transaction create(Transaction transaction, List<JournalEntry> entries) {
        if (entries == null || entries.size() < 2) {
            throw new IllegalArgumentException("Journal entry must have at least 2 lines");
        }

        validateBalance(entries);

        // Calculate total debit as the transaction amount
        BigDecimal totalDebit = entries.stream()
                .map(JournalEntry::getDebitAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Set transaction properties
        transaction.setTransactionNumber(generateTransactionNumber());
        
        // Only set MANUAL_ENTRY template if no template was already provided
        // This allows TemplateExecutionEngine to pass its own template
        if (transaction.getJournalTemplate() == null) {
            JournalTemplate manualTemplate = journalTemplateRepository.findByTemplateNameAndIsCurrentVersionTrue(MANUAL_ENTRY_TEMPLATE_NAME)
                    .orElseThrow(() -> new IllegalStateException("Manual entry template '" + MANUAL_ENTRY_TEMPLATE_NAME + "' not found"));
            transaction.setJournalTemplate(manualTemplate);
        }
        
        transaction.setAmount(totalDebit);
        transaction.setStatus(TransactionStatus.DRAFT);
        transaction.setCreatedBy(getCurrentUsername());

        // Generate journal number and add entries to transaction
        String journalNumber = generateJournalNumber();
        int lineIndex = 0;

        for (JournalEntry entry : entries) {
            entry.setJournalNumber(journalNumber + "-" + String.format("%02d", ++lineIndex));
            transaction.addJournalEntry(entry);
        }

        return transactionRepository.save(transaction);
    }

    /**
     * Update a draft journal entry group.
     * Transaction contains updated header info (date, description, reference).
     */
    @Transactional
    public Transaction update(String journalNumber, Transaction updatedHeader, List<JournalEntry> updatedEntries) {
        // Extract base journal number (remove line suffix like "-01")
        String baseJournalNumber = journalNumber.contains("-")
                ? journalNumber.substring(0, journalNumber.lastIndexOf("-"))
                : journalNumber;

        List<JournalEntry> existingEntries = journalEntryRepository.findAllByJournalNumberOrderByIdAsc(journalNumber);

        // If not found with exact match, try finding by base number pattern
        if (existingEntries.isEmpty()) {
            existingEntries = journalEntryRepository.findByReferenceNumberLike(baseJournalNumber + "-%");
        }

        if (existingEntries.isEmpty()) {
            throw new EntityNotFoundException("Journal entry not found with number: " + journalNumber);
        }

        // Get the parent transaction
        Transaction transaction = existingEntries.get(0).getTransaction();

        if (!transaction.isDraft()) {
            throw new IllegalStateException("Cannot update journal entry with status: " + transaction.getStatus());
        }

        if (updatedEntries == null || updatedEntries.size() < 2) {
            throw new IllegalArgumentException("Journal entry must have at least 2 lines");
        }

        validateBalance(updatedEntries);

        // Clear existing entries
        transaction.getJournalEntries().clear();
        journalEntryRepository.deleteAll(existingEntries);

        // Calculate new total
        BigDecimal totalDebit = updatedEntries.stream()
                .map(JournalEntry::getDebitAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Update transaction header from provided transaction
        transaction.setTransactionDate(updatedHeader.getTransactionDate());
        transaction.setAmount(totalDebit);
        transaction.setDescription(updatedHeader.getDescription());
        transaction.setReferenceNumber(updatedHeader.getReferenceNumber());

        // Add new entries
        int lineIndex = 0;
        for (JournalEntry entry : updatedEntries) {
            entry.setJournalNumber(baseJournalNumber + "-" + String.format("%02d", ++lineIndex));
            transaction.addJournalEntry(entry);
        }

        return transactionRepository.save(transaction);
    }

    /**
     * Post a draft journal entry group.
     * Validates balance before posting.
     */
    @Transactional
    public List<JournalEntry> post(String journalNumber) {
        // First find one entry by the provided journal number
        JournalEntry firstEntry = journalEntryRepository.findByJournalNumber(journalNumber)
                .orElse(null);

        // If not found, try finding by base number pattern
        if (firstEntry == null) {
            String baseNumber = journalNumber.contains("-")
                    ? journalNumber.substring(0, journalNumber.lastIndexOf("-"))
                    : journalNumber;
            List<JournalEntry> patternMatches = journalEntryRepository.findByReferenceNumberLike(baseNumber + "-%");
            if (!patternMatches.isEmpty()) {
                firstEntry = patternMatches.get(0);
            }
        }

        if (firstEntry == null) {
            throw new EntityNotFoundException("Journal entry not found with number: " + journalNumber);
        }

        // Get the parent transaction
        Transaction transaction = firstEntry.getTransaction();
        if (transaction == null) {
            throw new IllegalStateException("Journal entry has no parent transaction");
        }

        if (!transaction.isDraft()) {
            throw new IllegalStateException("Cannot post journal entry with status: " + transaction.getStatus());
        }

        // Get ALL entries for this transaction (not just the one passed in)
        List<JournalEntry> entries = journalEntryRepository.findByTransactionIdOrderByJournalNumberAsc(transaction.getId());

        validateBalance(entries);

        LocalDateTime now = LocalDateTime.now();
        String username = getCurrentUsername();

        // Update transaction status
        transaction.setStatus(TransactionStatus.POSTED);
        transaction.setPostedAt(now);
        transaction.setPostedBy(username);

        // Update journal entries timestamps
        for (JournalEntry entry : entries) {
            entry.setPostedAt(now);
        }

        transactionRepository.save(transaction);
        return entries;
    }

    /**
     * Void a posted journal entry group.
     * Requires a reason for voiding.
     */
    @Transactional
    public List<JournalEntry> voidEntry(String journalNumber, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Void reason is required");
        }

        List<JournalEntry> entries = journalEntryRepository.findAllByJournalNumberOrderByIdAsc(journalNumber);

        // Try finding by pattern if exact match not found
        if (entries.isEmpty()) {
            String baseNumber = journalNumber.contains("-")
                    ? journalNumber.substring(0, journalNumber.lastIndexOf("-"))
                    : journalNumber;
            entries = journalEntryRepository.findByReferenceNumberLike(baseNumber + "-%");
        }

        if (entries.isEmpty()) {
            throw new EntityNotFoundException("Journal entry not found with number: " + journalNumber);
        }

        // Get the parent transaction
        Transaction transaction = entries.get(0).getTransaction();
        if (transaction == null) {
            throw new IllegalStateException("Journal entry has no parent transaction");
        }

        if (!transaction.isPosted()) {
            throw new IllegalStateException("Cannot void journal entry with status: " + transaction.getStatus());
        }

        LocalDateTime now = LocalDateTime.now();
        String username = getCurrentUsername();

        // Update transaction status (using VoidReason.OTHER since we have a custom reason)
        transaction.setStatus(TransactionStatus.VOID);
        transaction.setVoidedAt(now);
        transaction.setVoidedBy(username);
        transaction.setVoidNotes(reason);

        // Update journal entries timestamps
        for (JournalEntry entry : entries) {
            entry.setVoidedAt(now);
            entry.setVoidReason(reason);
        }

        transactionRepository.save(transaction);
        return entries;
    }

    /**
     * Validate that total debit equals total credit.
     */
    public void validateBalance(List<JournalEntry> entries) {
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        for (JournalEntry entry : entries) {
            totalDebit = totalDebit.add(entry.getDebitAmount());
            totalCredit = totalCredit.add(entry.getCreditAmount());
        }

        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new IllegalArgumentException(
                    String.format("Journal entry is not balanced. Debit: %s, Credit: %s, Difference: %s",
                            totalDebit, totalCredit, totalDebit.subtract(totalCredit).abs()));
        }
    }

    /**
     * Find all entries by journal number.
     */
    public List<JournalEntry> findAllByJournalNumber(String journalNumber) {
        return journalEntryRepository.findAllByJournalNumberOrderByIdAsc(journalNumber);
    }

    /**
     * Find all entries by journal number with account eagerly loaded.
     * Accepts either exact journal number (with suffix like JE-2025-0001-01)
     * or base journal number (without suffix like JE-2025-0001).
     * Returns all entries belonging to the same transaction.
     */
    public List<JournalEntry> findAllByJournalNumberWithAccount(String journalNumber) {
        // First try exact match
        List<JournalEntry> entries = journalEntryRepository.findAllByJournalNumberWithAccount(journalNumber);
        if (!entries.isEmpty()) {
            // Found entry, now get all entries for the same transaction
            JournalEntry firstEntry = entries.get(0);
            return journalEntryRepository.findByTransactionIdWithAccount(firstEntry.getTransaction().getId());
        }

        // Try pattern match (for base journal numbers like JE-2025-0001)
        entries = journalEntryRepository.findAllByJournalNumberPatternWithAccount(journalNumber + "-%");
        return entries;
    }

    /**
     * Generate next journal number in format JE-YYYY-NNNN
     */
    private String generateJournalNumber() {
        String year = String.valueOf(Year.now().getValue());
        String prefix = "JE-" + year + "-%";

        Integer maxSeq = journalEntryRepository.findMaxSequenceByPrefix(prefix);
        int nextSeq = (maxSeq == null) ? 1 : maxSeq + 1;

        return String.format("JE-%s-%04d", year, nextSeq);
    }

    /**
     * Generate next transaction number in format MJ-YYYY-NNNN (MJ = Manual Journal)
     */
    private String generateTransactionNumber() {
        int year = LocalDate.now().getYear();
        TransactionSequence sequence = transactionSequenceRepository
                .findBySequenceTypeAndYearForUpdate("MANUAL_JOURNAL", year)
                .orElseGet(() -> {
                    TransactionSequence newSeq = new TransactionSequence();
                    newSeq.setSequenceType("MANUAL_JOURNAL");
                    newSeq.setPrefix("MJ");
                    newSeq.setYear(year);
                    newSeq.setLastNumber(0);
                    return newSeq;
                });

        sequence.setLastNumber(sequence.getLastNumber() + 1);
        transactionSequenceRepository.save(sequence);

        return String.format("MJ-%d-%04d", year, sequence.getLastNumber());
    }

    /**
     * Scheduler-reachable: MonthlyJournalScheduler -> AmortizationBatchService ->
     * AmortizationEntryService.postEntry() lands here with no security context.
     */
    private String getCurrentUsername() {
        return CurrentUser.nameOrSystem();
    }
}
