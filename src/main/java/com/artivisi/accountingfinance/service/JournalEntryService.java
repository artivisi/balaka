package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.JournalEntryStatus;
import com.artivisi.accountingfinance.enums.NormalBalance;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.repository.TransactionSequenceRepository;
import com.artivisi.accountingfinance.entity.TransactionSequence;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
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

    // Manual Journal Entry template ID (from V004 seed data)
    private static final UUID MANUAL_ENTRY_TEMPLATE_ID = UUID.fromString("e0000000-0000-0000-0000-000000000099");

    private final JournalEntryRepository journalEntryRepository;
    private final ChartOfAccountRepository chartOfAccountRepository;
    private final TransactionRepository transactionRepository;
    private final JournalTemplateRepository journalTemplateRepository;
    private final TransactionSequenceRepository transactionSequenceRepository;

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

        // Calculate opening balance (same as before)
        BigDecimal openingDebit = journalEntryRepository.sumDebitBeforeDate(accountId, startDate);
        BigDecimal openingCredit = journalEntryRepository.sumCreditBeforeDate(accountId, startDate);

        BigDecimal openingBalance;
        if (account.getNormalBalance() == NormalBalance.DEBIT) {
            openingBalance = openingDebit.subtract(openingCredit);
        } else {
            openingBalance = openingCredit.subtract(openingDebit);
        }

        // Get paginated entries
        Page<JournalEntry> entriesPage;
        if (search != null && !search.isBlank()) {
            entriesPage = journalEntryRepository.findPostedEntriesByAccountAndDateRangeAndSearchPaged(
                    accountId, startDate, endDate, search, pageable);
        } else {
            entriesPage = journalEntryRepository.findPostedEntriesByAccountAndDateRangePaged(
                    accountId, startDate, endDate, pageable);
        }

        // For running balance, we need to calculate the balance up to the start of this page
        // Get all entries before the current page to calculate starting balance
        BigDecimal pageStartBalance = openingBalance;
        if (pageable.getPageNumber() > 0) {
            // Get all entries before the current page
            List<JournalEntry> priorEntries = journalEntryRepository
                    .findPostedEntriesByAccountAndDateRange(accountId, startDate, endDate);

            int skipCount = (int) pageable.getOffset();
            for (int i = 0; i < Math.min(skipCount, priorEntries.size()); i++) {
                JournalEntry entry = priorEntries.get(i);
                if (account.getNormalBalance() == NormalBalance.DEBIT) {
                    pageStartBalance = pageStartBalance.add(entry.getDebitAmount()).subtract(entry.getCreditAmount());
                } else {
                    pageStartBalance = pageStartBalance.subtract(entry.getDebitAmount()).add(entry.getCreditAmount());
                }
            }
        }

        // Build line items with running balance
        BigDecimal runningBalance = pageStartBalance;
        List<LedgerLineItem> lineItems = new ArrayList<>();

        for (JournalEntry entry : entriesPage.getContent()) {
            if (account.getNormalBalance() == NormalBalance.DEBIT) {
                runningBalance = runningBalance.add(entry.getDebitAmount()).subtract(entry.getCreditAmount());
            } else {
                runningBalance = runningBalance.subtract(entry.getDebitAmount()).add(entry.getCreditAmount());
            }
            lineItems.add(new LedgerLineItem(entry, runningBalance));
        }

        // Calculate totals for the entire date range (not just current page)
        BigDecimal totalDebit = journalEntryRepository.sumDebitByAccountAndDateRange(accountId, startDate, endDate);
        BigDecimal totalCredit = journalEntryRepository.sumCreditByAccountAndDateRange(accountId, startDate, endDate);

        BigDecimal closingBalance;
        if (account.getNormalBalance() == NormalBalance.DEBIT) {
            closingBalance = openingBalance.add(totalDebit).subtract(totalCredit);
        } else {
            closingBalance = openingBalance.subtract(totalDebit).add(totalCredit);
        }

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
        LocalDate journalDate = firstEntry.getEffectiveJournalDate();
        boolean isPosted = firstEntry.isPosted() || firstEntry.isVoid();

        List<AccountImpact> impacts = new ArrayList<>();

        for (JournalEntry entry : entries) {
            ChartOfAccount account = entry.getAccount();
            if (account == null) continue;

            // Calculate balance before this journal entry
            BigDecimal debitBefore = journalEntryRepository.sumDebitBeforeDate(account.getId(), journalDate);
            BigDecimal creditBefore = journalEntryRepository.sumCreditBeforeDate(account.getId(), journalDate);

            // If this entry is posted, we need to exclude its own amounts from "after" calculation
            // because sumDebitBeforeDate only gets entries BEFORE the date, not on the date
            // For entries on the same date, we need to include all posted entries except this one
            BigDecimal debitOnDate = BigDecimal.ZERO;
            BigDecimal creditOnDate = BigDecimal.ZERO;

            if (isPosted) {
                // Get totals for all posted entries on this date BEFORE this journal number
                List<JournalEntry> entriesOnDate = journalEntryRepository
                        .findPostedEntriesByAccountAndDateRange(account.getId(), journalDate, journalDate);

                for (JournalEntry e : entriesOnDate) {
                    // Exclude entries from this journal
                    if (!e.getJournalNumber().equals(firstEntry.getJournalNumber())) {
                        debitOnDate = debitOnDate.add(e.getDebitAmount());
                        creditOnDate = creditOnDate.add(e.getCreditAmount());
                    }
                }
            }

            BigDecimal beforeBalance;
            if (account.getNormalBalance() == NormalBalance.DEBIT) {
                beforeBalance = debitBefore.add(debitOnDate).subtract(creditBefore).subtract(creditOnDate);
            } else {
                beforeBalance = creditBefore.add(creditOnDate).subtract(debitBefore).subtract(debitOnDate);
            }

            // Calculate movement
            BigDecimal debitMovement = entry.getDebitAmount();
            BigDecimal creditMovement = entry.getCreditAmount();

            // Calculate after balance
            BigDecimal afterBalance;
            if (account.getNormalBalance() == NormalBalance.DEBIT) {
                afterBalance = beforeBalance.add(debitMovement).subtract(creditMovement);
            } else {
                afterBalance = beforeBalance.subtract(debitMovement).add(creditMovement);
            }

            impacts.add(new AccountImpact(
                    account,
                    beforeBalance,
                    debitMovement,
                    creditMovement,
                    afterBalance
            ));
        }

        return impacts;
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
     * Create manual journal entries (multiple lines with same journal number).
     * Creates a Transaction with MANUAL_ENTRY template to serve as the header.
     * All entries are created in DRAFT status.
     */
    @Transactional
    public List<JournalEntry> create(List<JournalEntry> entries) {
        if (entries == null || entries.size() < 2) {
            throw new IllegalArgumentException("Journal entry must have at least 2 lines");
        }

        validateBalance(entries);

        // Get the first entry to extract header information
        JournalEntry firstEntry = entries.get(0);

        // Calculate total debit as the transaction amount
        BigDecimal totalDebit = entries.stream()
                .map(JournalEntry::getDebitAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Get the MANUAL_ENTRY template
        JournalTemplate manualTemplate = journalTemplateRepository.findById(MANUAL_ENTRY_TEMPLATE_ID)
                .orElseThrow(() -> new IllegalStateException("Manual entry template not found"));

        // Create a Transaction as the header
        Transaction transaction = new Transaction();
        transaction.setTransactionNumber(generateTransactionNumber());
        transaction.setTransactionDate(firstEntry.getJournalDate());
        transaction.setJournalTemplate(manualTemplate);
        transaction.setAmount(totalDebit);
        transaction.setDescription(firstEntry.getDescription());
        transaction.setReferenceNumber(firstEntry.getReferenceNumber());
        transaction.setStatus(TransactionStatus.DRAFT);
        transaction.setCreatedBy(getCurrentUsername());

        // Generate journal number and add entries to transaction
        String journalNumber = generateJournalNumber();
        int lineIndex = 0;

        for (JournalEntry entry : entries) {
            entry.setJournalNumber(journalNumber + "-" + String.format("%02d", ++lineIndex));
            entry.setStatus(JournalEntryStatus.DRAFT);
            transaction.addJournalEntry(entry);
        }

        // Save the transaction (cascades to journal entries)
        Transaction savedTransaction = transactionRepository.save(transaction);

        return savedTransaction.getJournalEntries();
    }

    /**
     * Update a draft journal entry group.
     * Only draft entries can be updated.
     */
    @Transactional
    public List<JournalEntry> update(String journalNumber, List<JournalEntry> updatedEntries) {
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
        if (transaction == null) {
            throw new IllegalStateException("Journal entry has no parent transaction");
        }

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

        // Update transaction header from first entry
        JournalEntry firstEntry = updatedEntries.get(0);
        transaction.setTransactionDate(firstEntry.getJournalDate());
        transaction.setAmount(totalDebit);
        transaction.setDescription(firstEntry.getDescription());
        transaction.setReferenceNumber(firstEntry.getReferenceNumber());

        // Add new entries
        int lineIndex = 0;
        for (JournalEntry entry : updatedEntries) {
            entry.setJournalNumber(baseJournalNumber + "-" + String.format("%02d", ++lineIndex));
            entry.setStatus(JournalEntryStatus.DRAFT);
            transaction.addJournalEntry(entry);
        }

        Transaction savedTransaction = transactionRepository.save(transaction);
        return savedTransaction.getJournalEntries();
    }

    /**
     * Post a draft journal entry group.
     * Validates balance before posting.
     */
    @Transactional
    public List<JournalEntry> post(String journalNumber) {
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

        if (!transaction.isDraft()) {
            throw new IllegalStateException("Cannot post journal entry with status: " + transaction.getStatus());
        }

        validateBalance(entries);

        LocalDateTime now = LocalDateTime.now();
        String username = getCurrentUsername();

        // Update transaction status
        transaction.setStatus(TransactionStatus.POSTED);
        transaction.setPostedAt(now);
        transaction.setPostedBy(username);

        // Update journal entries status
        for (JournalEntry entry : entries) {
            entry.setStatus(JournalEntryStatus.POSTED);
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

        // Update journal entries status
        for (JournalEntry entry : entries) {
            entry.setStatus(JournalEntryStatus.VOID);
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
     */
    public List<JournalEntry> findAllByJournalNumberWithAccount(String journalNumber) {
        return journalEntryRepository.findAllByJournalNumberWithAccount(journalNumber);
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
                    newSeq.setYear(year);
                    newSeq.setLastSequence(0);
                    return newSeq;
                });

        sequence.setLastSequence(sequence.getLastSequence() + 1);
        transactionSequenceRepository.save(sequence);

        return String.format("MJ-%d-%04d", year, sequence.getLastSequence());
    }

    private String getCurrentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
