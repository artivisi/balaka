package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.BankReconciliation;
import com.artivisi.accountingfinance.entity.BankStatement;
import com.artivisi.accountingfinance.entity.BankStatementItem;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.CompanyBankAccount;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.ReconciliationItem;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.MatchType;
import com.artivisi.accountingfinance.enums.ReconciliationStatus;
import com.artivisi.accountingfinance.enums.StatementItemMatchStatus;
import com.artivisi.accountingfinance.security.LogSanitizer;
import com.artivisi.accountingfinance.repository.BankReconciliationRepository;
import com.artivisi.accountingfinance.repository.BankStatementItemRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.ReconciliationItemRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BankReconciliationService {

    private static final String ERR_RECON_COMPLETED = "Rekonsiliasi sudah selesai";
    private static final String ERR_STATEMENT_ITEM_NOT_FOUND = "Statement item not found: ";

    private final BankReconciliationRepository reconciliationRepository;
    private final ReconciliationItemRepository reconciliationItemRepository;
    private final BankStatementItemRepository statementItemRepository;
    private final BankStatementImportService importService;
    private final JournalEntryRepository journalEntryRepository;
    private final TransactionService transactionService;
    private final JournalTemplateService templateService;

    public List<BankReconciliation> findAll() {
        return reconciliationRepository.findAllWithRelations();
    }

    public BankReconciliation findById(UUID id) {
        return reconciliationRepository.findByIdWithRelations(id)
                .orElseThrow(() -> new EntityNotFoundException("Reconciliation not found with id: " + id));
    }

    public List<ReconciliationItem> findReconciliationItems(UUID reconciliationId) {
        return reconciliationItemRepository.findByReconciliationIdWithRelations(reconciliationId);
    }

    public List<JournalTemplate> findJournalTemplates() {
        return templateService.findAll();
    }

    @Transactional
    public BankReconciliation create(UUID bankStatementId, String notes, String username) {
        BankStatement statement = importService.findStatementById(bankStatementId);
        CompanyBankAccount bankAccount = statement.getBankAccount();

        if (bankAccount.getGlAccount() == null) {
            throw new IllegalArgumentException(
                    "Rekening bank '" + bankAccount.getBankName()
                            + "' belum dihubungkan dengan akun GL. Atur di Pengaturan > Rekening Bank.");
        }

        BankReconciliation recon = new BankReconciliation();
        recon.setBankAccount(bankAccount);
        recon.setBankStatement(statement);
        recon.setPeriodStart(statement.getStatementPeriodStart());
        recon.setPeriodEnd(statement.getStatementPeriodEnd());
        recon.setStatus(ReconciliationStatus.DRAFT);
        recon.setNotes(notes);
        recon.setCreatedBy(username);

        // Compute book balance from GL journal entries
        ChartOfAccount glAccount = bankAccount.getGlAccount();
        BigDecimal bookBalance = computeBookBalance(
                glAccount.getId(), statement.getStatementPeriodStart(), statement.getStatementPeriodEnd());
        recon.setBookBalance(bookBalance);

        // Set bank balance from statement closing balance
        recon.setBankBalance(statement.getClosingBalance());

        // Set counts
        long totalItems = statementItemRepository.findByBankStatementIdOrderByLineNumberAsc(bankStatementId).size();
        recon.setTotalStatementItems((int) totalItems);
        recon.setMatchedCount(0);
        recon.setUnmatchedBankCount((int) totalItems);
        recon.setUnmatchedBookCount(0);

        return reconciliationRepository.save(recon);
    }

    @Transactional
    public int autoMatch(UUID reconciliationId, String username) {
        BankReconciliation recon = findById(reconciliationId);
        if (recon.isCompleted()) {
            throw new IllegalStateException("Rekonsiliasi sudah selesai, tidak dapat melakukan auto-match");
        }

        recon.setStatus(ReconciliationStatus.IN_PROGRESS);

        ChartOfAccount glAccount = recon.getBankAccount().getGlAccount();
        List<JournalEntry> bookEntries = journalEntryRepository.findPostedEntriesByAccountAndDateRange(
                glAccount.getId(), recon.getPeriodStart(), recon.getPeriodEnd());

        Set<UUID> matchedTransactionIds = loadMatchedTransactionIds(reconciliationId);

        int matchCount = 0;

        var matchCtx = new MatchContext(recon, bookEntries, matchedTransactionIds, username);

        // Pass 1: Exact match (same date + same amount)
        matchCount += matchPass(matchCtx, getUnmatchedItems(recon),
                MatchType.EXACT, new BigDecimal("1.00"), 0);

        // Pass 2: Fuzzy date (same amount, date +/-1 day)
        matchCount += matchPass(matchCtx, getUnmatchedItems(recon),
                MatchType.FUZZY_DATE, new BigDecimal("0.90"), 1);

        // Pass 3: Keyword (same amount, description overlap, date +/-3 days)
        matchCount += keywordMatchPass(matchCtx, getUnmatchedItems(recon));

        updateReconciliationCounts(recon);
        reconciliationRepository.save(recon);

        log.info("Auto-match completed for reconciliation {}: {} matches",
                LogSanitizer.sanitize(reconciliationId.toString()), matchCount);
        return matchCount;
    }

    private List<BankStatementItem> getUnmatchedItems(BankReconciliation recon) {
        return statementItemRepository.findByBankStatementIdAndMatchStatusOrderByLineNumberAsc(
                recon.getBankStatement().getId(), StatementItemMatchStatus.UNMATCHED);
    }

    private Set<UUID> loadMatchedTransactionIds(UUID reconciliationId) {
        Set<UUID> ids = new HashSet<>();
        List<ReconciliationItem> existingItems = reconciliationItemRepository
                .findByReconciliationIdAndMatchStatus(reconciliationId, StatementItemMatchStatus.MATCHED);
        for (ReconciliationItem item : existingItems) {
            if (item.getTransaction() != null) {
                ids.add(item.getTransaction().getId());
            }
        }
        return ids;
    }

    /**
     * Shared context for matching passes to reduce parameter count.
     */
    private record MatchContext(
            BankReconciliation recon,
            List<JournalEntry> bookEntries,
            Set<UUID> matchedTransactionIds,
            String username
    ) {}

    private int matchPass(MatchContext ctx, List<BankStatementItem> unmatchedItems,
                          MatchType matchType, BigDecimal confidence, int dateToleranceDays) {
        int matchCount = 0;

        for (BankStatementItem item : unmatchedItems) {
            if (item.getMatchStatus() != StatementItemMatchStatus.UNMATCHED) {
                continue;
            }

            JournalEntry matchedEntry = findMatchingEntry(item, ctx, dateToleranceDays);
            if (matchedEntry != null) {
                createMatch(ctx.recon(), item, matchedEntry.getTransaction(), matchType, confidence, ctx.username());
                ctx.matchedTransactionIds().add(matchedEntry.getTransaction().getId());
                matchCount++;
            }
        }
        return matchCount;
    }

    /**
     * Find the first book entry that matches the given statement item by amount and date tolerance.
     */
    private JournalEntry findMatchingEntry(BankStatementItem item, MatchContext ctx, int dateToleranceDays) {
        BigDecimal itemAmount = item.getNetAmount();

        for (JournalEntry entry : ctx.bookEntries()) {
            if (isAmountAndDateMatch(entry, itemAmount, item.getTransactionDate(), dateToleranceDays, ctx)) {
                return entry;
            }
        }
        return null;
    }

    private boolean isAmountAndDateMatch(JournalEntry entry, BigDecimal itemAmount,
                                         LocalDate itemDate, int dateToleranceDays, MatchContext ctx) {
        if (ctx.matchedTransactionIds().contains(entry.getTransaction().getId())) {
            return false;
        }
        BigDecimal entryAmount = computeEntryNetAmount(entry);
        if (itemAmount.compareTo(entryAmount) != 0) {
            return false;
        }
        long daysDiff = Math.abs(itemDate.toEpochDay() - entry.getJournalDate().toEpochDay());
        return daysDiff <= dateToleranceDays;
    }

    private int keywordMatchPass(MatchContext ctx, List<BankStatementItem> unmatchedItems) {
        int matchCount = 0;

        for (BankStatementItem item : unmatchedItems) {
            if (item.getMatchStatus() != StatementItemMatchStatus.UNMATCHED) {
                continue;
            }

            JournalEntry matchedEntry = findKeywordMatchingEntry(item, ctx);
            if (matchedEntry != null) {
                createMatch(ctx.recon(), item, matchedEntry.getTransaction(),
                        MatchType.KEYWORD, new BigDecimal("0.80"), ctx.username());
                ctx.matchedTransactionIds().add(matchedEntry.getTransaction().getId());
                matchCount++;
            }
        }
        return matchCount;
    }

    /**
     * Find a book entry matching by amount, date within 3 days, and keyword overlap.
     */
    private JournalEntry findKeywordMatchingEntry(BankStatementItem item, MatchContext ctx) {
        BigDecimal itemAmount = item.getNetAmount();
        String itemDesc = item.getDescription() != null ? item.getDescription().toLowerCase() : "";

        for (JournalEntry entry : ctx.bookEntries()) {
            if (isKeywordMatch(entry, itemAmount, item.getTransactionDate(), itemDesc, ctx)) {
                return entry;
            }
        }
        return null;
    }

    private boolean isKeywordMatch(JournalEntry entry, BigDecimal itemAmount,
                                    LocalDate itemDate, String itemDesc, MatchContext ctx) {
        if (!isAmountAndDateMatch(entry, itemAmount, itemDate, 3, ctx)) {
            return false;
        }
        String entryDesc = entry.getDescription() != null ? entry.getDescription().toLowerCase() : "";
        return hasKeywordOverlap(itemDesc, entryDesc);
    }

    private boolean hasKeywordOverlap(String desc1, String desc2) {
        if (desc1.isEmpty() || desc2.isEmpty()) {
            return false;
        }

        String[] words1 = desc1.split("\\s+");
        for (String word : words1) {
            if (word.length() >= 4 && desc2.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal computeEntryNetAmount(JournalEntry entry) {
        BigDecimal debit = entry.getDebitAmount() != null ? entry.getDebitAmount() : BigDecimal.ZERO;
        BigDecimal credit = entry.getCreditAmount() != null ? entry.getCreditAmount() : BigDecimal.ZERO;
        // For a bank account (asset), debit increases balance, credit decreases
        // Bank statement: credit = money in, debit = money out
        // So: bank credit matches book debit, bank debit matches book credit
        // Net amount convention: positive = money in (credit on bank, debit on books)
        return debit.subtract(credit);
    }

    private void createMatch(BankReconciliation recon, BankStatementItem item, Transaction transaction,
                            MatchType matchType, BigDecimal confidence, String username) {
        // Update statement item
        item.setMatchStatus(StatementItemMatchStatus.MATCHED);
        item.setMatchType(matchType);
        item.setMatchedTransaction(transaction);
        item.setMatchedAt(LocalDateTime.now());
        item.setMatchedBy(username);
        statementItemRepository.save(item);

        // Create reconciliation item
        ReconciliationItem reconItem = new ReconciliationItem();
        reconItem.setReconciliation(recon);
        reconItem.setStatementItem(item);
        reconItem.setTransaction(transaction);
        reconItem.setMatchStatus(StatementItemMatchStatus.MATCHED);
        reconItem.setMatchType(matchType);
        reconItem.setMatchConfidence(confidence);
        reconciliationItemRepository.save(reconItem);
    }

    @Transactional
    public void manualMatch(UUID reconciliationId, UUID statementItemId, UUID transactionId, String username) {
        BankReconciliation recon = findById(reconciliationId);
        if (recon.isCompleted()) {
            throw new IllegalStateException(ERR_RECON_COMPLETED);
        }

        BankStatementItem item = findUnmatchedItemOfStatement(recon, statementItemId);
        Transaction transaction = findUnmatchedBookTransaction(recon, transactionId);

        recon.setStatus(ReconciliationStatus.IN_PROGRESS);
        createMatch(recon, item, transaction, MatchType.MANUAL, new BigDecimal("1.00"), username);
        updateReconciliationCounts(recon);
        reconciliationRepository.save(recon);
    }

    @Transactional
    public void markBankOnly(UUID reconciliationId, UUID statementItemId, String notes, String username) {
        BankReconciliation recon = findById(reconciliationId);
        if (recon.isCompleted()) {
            throw new IllegalStateException(ERR_RECON_COMPLETED);
        }

        BankStatementItem item = findUnmatchedItemOfStatement(recon, statementItemId);

        item.setMatchStatus(StatementItemMatchStatus.BANK_ONLY);
        item.setMatchedAt(LocalDateTime.now());
        item.setMatchedBy(username);
        statementItemRepository.save(item);

        ReconciliationItem reconItem = new ReconciliationItem();
        reconItem.setReconciliation(recon);
        reconItem.setStatementItem(item);
        reconItem.setMatchStatus(StatementItemMatchStatus.BANK_ONLY);
        reconItem.setNotes(notes);
        reconciliationItemRepository.save(reconItem);

        recon.setStatus(ReconciliationStatus.IN_PROGRESS);
        updateReconciliationCounts(recon);
        reconciliationRepository.save(recon);
    }

    @Transactional
    public void markBookOnly(UUID reconciliationId, UUID transactionId, String notes) {
        BankReconciliation recon = findById(reconciliationId);
        if (recon.isCompleted()) {
            throw new IllegalStateException(ERR_RECON_COMPLETED);
        }

        Transaction transaction = findUnmatchedBookTransaction(recon, transactionId);

        ReconciliationItem reconItem = new ReconciliationItem();
        reconItem.setReconciliation(recon);
        reconItem.setTransaction(transaction);
        reconItem.setMatchStatus(StatementItemMatchStatus.BOOK_ONLY);
        reconItem.setNotes(notes);
        reconciliationItemRepository.save(reconItem);

        recon.setStatus(ReconciliationStatus.IN_PROGRESS);
        updateReconciliationCounts(recon);
        reconciliationRepository.save(recon);
    }

    @Transactional
    public void unmatch(UUID reconciliationId, UUID reconciliationItemId) {
        BankReconciliation recon = findById(reconciliationId);
        if (recon.isCompleted()) {
            throw new IllegalStateException(ERR_RECON_COMPLETED);
        }

        ReconciliationItem reconItem = reconciliationItemRepository.findById(reconciliationItemId)
                .filter(ri -> ri.getReconciliation().getId().equals(reconciliationId))
                .orElseThrow(() -> new EntityNotFoundException("Reconciliation item not found: " + reconciliationItemId
                        + " in reconciliation " + reconciliationId));

        // Reset statement item if it was matched
        if (reconItem.getStatementItem() != null) {
            BankStatementItem stmtItem = reconItem.getStatementItem();
            stmtItem.setMatchStatus(StatementItemMatchStatus.UNMATCHED);
            stmtItem.setMatchType(null);
            stmtItem.setMatchedTransaction(null);
            stmtItem.setMatchedAt(null);
            stmtItem.setMatchedBy(null);
            statementItemRepository.save(stmtItem);
        }

        reconciliationItemRepository.delete(reconItem);
        updateReconciliationCounts(recon);
        reconciliationRepository.save(recon);
    }

    @Transactional
    public void createTransactionFromStatementItem(
            UUID reconciliationId, UUID statementItemId, UUID templateId,
            String description, String username) {

        BankReconciliation recon = findById(reconciliationId);
        if (recon.isCompleted()) {
            throw new IllegalStateException(ERR_RECON_COMPLETED);
        }

        BankStatementItem item = findUnmatchedItemOfStatement(recon, statementItemId);

        BigDecimal amount = item.getDebitAmount() != null && item.getDebitAmount().compareTo(BigDecimal.ZERO) > 0
                ? item.getDebitAmount()
                : item.getCreditAmount();

        String desc = description != null && !description.isBlank() ? description : item.getDescription();

        Transaction transaction = transactionService.createFromReconciliation(
                templateId, item.getTransactionDate(), amount, desc, username);

        // Auto-match the new transaction
        recon.setStatus(ReconciliationStatus.IN_PROGRESS);
        createMatch(recon, item, transaction, MatchType.MANUAL, new BigDecimal("1.00"), username);
        updateReconciliationCounts(recon);
        reconciliationRepository.save(recon);
    }

    @Transactional
    public void complete(UUID reconciliationId, String username) {
        BankReconciliation recon = findById(reconciliationId);

        long unmatchedCount = statementItemRepository
                .countByBankStatementIdAndMatchStatus(
                        recon.getBankStatement().getId(), StatementItemMatchStatus.UNMATCHED);

        if (unmatchedCount > 0) {
            throw new IllegalStateException(
                    "Masih ada " + unmatchedCount + " item yang belum dicocokkan. "
                            + "Cocokkan atau tandai sebagai bank-only/book-only terlebih dahulu.");
        }

        recon.setStatus(ReconciliationStatus.COMPLETED);
        recon.setCompletedAt(LocalDateTime.now());
        recon.setCompletedBy(username);
        updateReconciliationCounts(recon);
        reconciliationRepository.save(recon);
    }

    /**
     * Loads a statement item of the reconciliation's own bank statement that is still UNMATCHED.
     * An item of another statement is reported as not found, so one reconciliation cannot
     * change another one's items.
     */
    private BankStatementItem findUnmatchedItemOfStatement(BankReconciliation recon, UUID statementItemId) {
        BankStatementItem item = statementItemRepository.findById(statementItemId)
                .filter(i -> i.getBankStatement().getId().equals(recon.getBankStatement().getId()))
                .orElseThrow(() -> new EntityNotFoundException(ERR_STATEMENT_ITEM_NOT_FOUND + statementItemId
                        + " in bank statement " + recon.getBankStatement().getId()));
        if (item.getMatchStatus() != StatementItemMatchStatus.UNMATCHED) {
            throw new IllegalStateException("Item mutasi baris " + item.getLineNumber()
                    + " sudah berstatus " + item.getMatchStatus() + "; batalkan dulu sebelum mencocokkan ulang");
        }
        return item;
    }

    /**
     * Loads a transaction that posts to the bank's GL account within the reconciliation period
     * and is not yet matched or marked in this reconciliation.
     */
    private Transaction findUnmatchedBookTransaction(BankReconciliation recon, UUID transactionId) {
        Transaction transaction = transactionService.findById(transactionId);
        boolean candidate = findUnmatchedBookTransactions(recon.getId()).stream()
                .anyMatch(t -> t.getId().equals(transactionId));
        if (!candidate) {
            throw new IllegalArgumentException("Transaksi " + transaction.getTransactionNumber()
                    + " tidak bisa dipakai: tidak ada jurnal posted di akun "
                    + recon.getBankAccount().getGlAccount().getAccountCode()
                    + " pada periode " + recon.getPeriodStart() + " s.d. " + recon.getPeriodEnd()
                    + ", atau sudah dicocokkan di rekonsiliasi ini");
        }
        return transaction;
    }

    public List<Transaction> findUnmatchedBookTransactions(UUID reconciliationId) {
        BankReconciliation recon = findById(reconciliationId);
        ChartOfAccount glAccount = recon.getBankAccount().getGlAccount();
        if (glAccount == null) {
            return List.of();
        }

        // Get all posted journal entries for this account in the period
        List<JournalEntry> entries = journalEntryRepository.findPostedEntriesByAccountAndDateRange(
                glAccount.getId(), recon.getPeriodStart(), recon.getPeriodEnd());

        // Get matched transaction IDs
        Set<UUID> matchedTxnIds = new HashSet<>();
        List<ReconciliationItem> reconItems = reconciliationItemRepository
                .findByReconciliationIdOrderByMatchStatusAsc(reconciliationId);
        for (ReconciliationItem ri : reconItems) {
            if (ri.getTransaction() != null) {
                matchedTxnIds.add(ri.getTransaction().getId());
            }
        }

        // Also include transactions matched directly on statement items
        List<BankStatementItem> stmtItems = statementItemRepository
                .findByBankStatementIdOrderByLineNumberAsc(recon.getBankStatement().getId());
        for (BankStatementItem si : stmtItems) {
            if (si.getMatchedTransaction() != null) {
                matchedTxnIds.add(si.getMatchedTransaction().getId());
            }
        }

        // Return unmatched transactions
        List<Transaction> unmatched = new ArrayList<>();
        Set<UUID> seenTxnIds = new HashSet<>();
        for (JournalEntry entry : entries) {
            UUID txnId = entry.getTransaction().getId();
            if (!matchedTxnIds.contains(txnId) && seenTxnIds.add(txnId)) {
                unmatched.add(entry.getTransaction());
            }
        }
        return unmatched;
    }

    private BigDecimal computeBookBalance(UUID accountId, LocalDate periodStart, LocalDate periodEnd) {
        // Balance = sum(debit) - sum(credit) for the period
        // For asset accounts (bank), debit increases balance
        BigDecimal totalDebit = journalEntryRepository.sumDebitByAccountAndDateRange(
                accountId, periodStart, periodEnd);
        BigDecimal totalCredit = journalEntryRepository.sumCreditByAccountAndDateRange(
                accountId, periodStart, periodEnd);

        // Also include prior balance
        BigDecimal priorDebit = journalEntryRepository.sumDebitBeforeDate(accountId, periodStart);
        BigDecimal priorCredit = journalEntryRepository.sumCreditBeforeDate(accountId, periodStart);

        BigDecimal priorBalance = priorDebit.subtract(priorCredit);
        BigDecimal periodNet = totalDebit.subtract(totalCredit);

        return priorBalance.add(periodNet);
    }

    private void updateReconciliationCounts(BankReconciliation recon) {
        UUID statementId = recon.getBankStatement().getId();
        long matched = statementItemRepository.countByBankStatementIdAndMatchStatus(
                statementId, StatementItemMatchStatus.MATCHED);
        long unmatched = statementItemRepository.countByBankStatementIdAndMatchStatus(
                statementId, StatementItemMatchStatus.UNMATCHED);
        long bankOnly = statementItemRepository.countByBankStatementIdAndMatchStatus(
                statementId, StatementItemMatchStatus.BANK_ONLY);
        long bookOnly = reconciliationItemRepository.countByReconciliationIdAndMatchStatus(
                recon.getId(), StatementItemMatchStatus.BOOK_ONLY);

        recon.setMatchedCount((int) matched);
        recon.setUnmatchedBankCount((int) (unmatched + bankOnly));
        recon.setUnmatchedBookCount((int) bookOnly);
    }
}
