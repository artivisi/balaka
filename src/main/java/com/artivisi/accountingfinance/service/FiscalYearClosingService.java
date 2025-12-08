package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.entity.TransactionSequence;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.repository.TransactionSequenceRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for fiscal year closing operations.
 * Creates closing entries to transfer income/expense balances to retained earnings.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class FiscalYearClosingService {

    // Account codes - should match COA
    private static final String LABA_BERJALAN_CODE = "3.2.02";
    private static final String LABA_DITAHAN_CODE = "3.2.01";

    // Fiscal Year Closing template ID (from V004 seed data)
    private static final UUID CLOSING_TEMPLATE_ID = UUID.fromString("e0000000-0000-0000-0000-000000000098");

    private final JournalEntryRepository journalEntryRepository;
    private final ChartOfAccountRepository chartOfAccountRepository;
    private final ReportService reportService;
    private final TransactionRepository transactionRepository;
    private final JournalTemplateRepository journalTemplateRepository;
    private final TransactionSequenceRepository transactionSequenceRepository;

    /**
     * Check if closing entries already exist for a year.
     */
    public boolean hasClosingEntries(int year) {
        String referencePattern = "CLOSING-" + year + "-%";
        return journalEntryRepository.countByReferenceNumberLike(referencePattern) > 0;
    }

    /**
     * Get existing closing entries for a year.
     */
    public List<JournalEntry> getClosingEntries(int year) {
        String referencePattern = "CLOSING-" + year + "-%";
        return journalEntryRepository.findByReferenceNumberLike(referencePattern);
    }

    /**
     * Preview closing entries without saving.
     * Returns what would be created if executeClosing() is called.
     */
    public ClosingPreview previewClosing(int year) {
        LocalDate yearEnd = LocalDate.of(year, 12, 31);

        // Get income statement for the year
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        ReportService.IncomeStatementReport incomeStatement =
                reportService.generateIncomeStatement(yearStart, yearEnd);

        List<ClosingEntryPreview> entries = new ArrayList<>();

        // Entry 1: Close Revenue to Laba Berjalan
        if (incomeStatement.totalRevenue().compareTo(BigDecimal.ZERO) != 0) {
            entries.add(new ClosingEntryPreview(
                    "CLOSING-" + year + "-01",
                    "Tutup Pendapatan ke Laba Berjalan",
                    yearEnd,
                    createRevenueClosingLines(incomeStatement, year)
            ));
        }

        // Entry 2: Close Expenses to Laba Berjalan
        if (incomeStatement.totalExpense().compareTo(BigDecimal.ZERO) != 0) {
            entries.add(new ClosingEntryPreview(
                    "CLOSING-" + year + "-02",
                    "Tutup Beban ke Laba Berjalan",
                    yearEnd,
                    createExpenseClosingLines(incomeStatement, year)
            ));
        }

        // Entry 3: Transfer Laba Berjalan to Laba Ditahan
        BigDecimal netIncome = incomeStatement.netIncome();
        if (netIncome.compareTo(BigDecimal.ZERO) != 0) {
            entries.add(new ClosingEntryPreview(
                    "CLOSING-" + year + "-03",
                    "Transfer Laba Berjalan ke Laba Ditahan",
                    yearEnd,
                    createRetainedEarningsLines(netIncome, year)
            ));
        }

        return new ClosingPreview(
                year,
                incomeStatement.totalRevenue(),
                incomeStatement.totalExpense(),
                incomeStatement.netIncome(),
                entries,
                hasClosingEntries(year)
        );
    }

    /**
     * Execute year-end closing.
     * Creates closing journal entries to transfer income/expense to retained earnings.
     * Each closing entry group is backed by a Transaction.
     */
    @Transactional
    public List<JournalEntry> executeClosing(int year) {
        // Check for duplicate closing
        if (hasClosingEntries(year)) {
            throw new IllegalStateException(
                    "Jurnal penutup untuk tahun " + year + " sudah ada. " +
                    "Hapus jurnal penutup yang ada terlebih dahulu jika ingin membuat ulang.");
        }

        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        LocalDate yearStart = LocalDate.of(year, 1, 1);

        // Get income statement for the year
        ReportService.IncomeStatementReport incomeStatement =
                reportService.generateIncomeStatement(yearStart, yearEnd);

        List<JournalEntry> closingEntries = new ArrayList<>();
        String username = getCurrentUsername();

        // Get the closing template
        JournalTemplate closingTemplate = journalTemplateRepository.findById(CLOSING_TEMPLATE_ID)
                .orElseThrow(() -> new IllegalStateException("Fiscal year closing template not found"));

        // Get required accounts
        ChartOfAccount labaBerjalan = chartOfAccountRepository.findByAccountCode(LABA_BERJALAN_CODE)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Akun Laba Berjalan (" + LABA_BERJALAN_CODE + ") tidak ditemukan"));
        ChartOfAccount labaDitahan = chartOfAccountRepository.findByAccountCode(LABA_DITAHAN_CODE)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Akun Laba Ditahan (" + LABA_DITAHAN_CODE + ") tidak ditemukan"));

        // Entry 1: Close Revenue accounts to Laba Berjalan
        if (incomeStatement.totalRevenue().compareTo(BigDecimal.ZERO) != 0) {
            Transaction transaction = createClosingTransaction(
                    closingTemplate, yearEnd, year,
                    "Tutup Pendapatan ke Laba Berjalan - Tahun " + year,
                    "CLOSING-" + year + "-01",
                    incomeStatement.totalRevenue(),
                    username
            );

            String journalNumber = generateJournalNumber(yearEnd, "01");
            int lineIndex = 0;

            // Debit each revenue account (to zero it out)
            for (ReportService.IncomeStatementItem item : incomeStatement.revenueItems()) {
                if (item.balance().compareTo(BigDecimal.ZERO) != 0) {
                    JournalEntry entry = createEntry(
                            journalNumber + "-" + String.format("%02d", ++lineIndex),
                            item.account(),
                            item.balance(),
                            BigDecimal.ZERO,
                            username
                    );
                    transaction.addJournalEntry(entry);
                }
            }

            // Credit Laba Berjalan
            JournalEntry creditEntry = createEntry(
                    journalNumber + "-" + String.format("%02d", ++lineIndex),
                    labaBerjalan,
                    BigDecimal.ZERO,
                    incomeStatement.totalRevenue(),
                    username
            );
            transaction.addJournalEntry(creditEntry);

            Transaction savedTransaction = transactionRepository.save(transaction);
            closingEntries.addAll(savedTransaction.getJournalEntries());

            log.info("Created revenue closing entry for year {}: {}", year, incomeStatement.totalRevenue());
        }

        // Entry 2: Close Expense accounts to Laba Berjalan
        if (incomeStatement.totalExpense().compareTo(BigDecimal.ZERO) != 0) {
            Transaction transaction = createClosingTransaction(
                    closingTemplate, yearEnd, year,
                    "Tutup Beban ke Laba Berjalan - Tahun " + year,
                    "CLOSING-" + year + "-02",
                    incomeStatement.totalExpense(),
                    username
            );

            String journalNumber = generateJournalNumber(yearEnd, "02");
            int lineIndex = 0;

            // Credit each expense account (to zero it out)
            for (ReportService.IncomeStatementItem item : incomeStatement.expenseItems()) {
                if (item.balance().compareTo(BigDecimal.ZERO) != 0) {
                    JournalEntry entry = createEntry(
                            journalNumber + "-" + String.format("%02d", ++lineIndex),
                            item.account(),
                            BigDecimal.ZERO,
                            item.balance(),
                            username
                    );
                    transaction.addJournalEntry(entry);
                }
            }

            // Debit Laba Berjalan
            JournalEntry debitEntry = createEntry(
                    journalNumber + "-" + String.format("%02d", ++lineIndex),
                    labaBerjalan,
                    incomeStatement.totalExpense(),
                    BigDecimal.ZERO,
                    username
            );
            transaction.addJournalEntry(debitEntry);

            Transaction savedTransaction = transactionRepository.save(transaction);
            closingEntries.addAll(savedTransaction.getJournalEntries());

            log.info("Created expense closing entry for year {}: {}", year, incomeStatement.totalExpense());
        }

        // Entry 3: Transfer Laba Berjalan to Laba Ditahan
        BigDecimal netIncome = incomeStatement.netIncome();
        if (netIncome.compareTo(BigDecimal.ZERO) != 0) {
            String description = netIncome.compareTo(BigDecimal.ZERO) > 0
                    ? "Transfer Laba Berjalan ke Laba Ditahan - Tahun " + year
                    : "Transfer Rugi Berjalan ke Laba Ditahan - Tahun " + year;

            Transaction transaction = createClosingTransaction(
                    closingTemplate, yearEnd, year,
                    description,
                    "CLOSING-" + year + "-03",
                    netIncome.abs(),
                    username
            );

            String journalNumber = generateJournalNumber(yearEnd, "03");

            if (netIncome.compareTo(BigDecimal.ZERO) > 0) {
                // Profit: Debit Laba Berjalan, Credit Laba Ditahan
                JournalEntry debitEntry = createEntry(journalNumber + "-01", labaBerjalan, netIncome, BigDecimal.ZERO, username);
                JournalEntry creditEntry = createEntry(journalNumber + "-02", labaDitahan, BigDecimal.ZERO, netIncome, username);
                transaction.addJournalEntry(debitEntry);
                transaction.addJournalEntry(creditEntry);
            } else {
                // Loss: Credit Laba Berjalan, Debit Laba Ditahan
                BigDecimal loss = netIncome.abs();
                JournalEntry creditEntry = createEntry(journalNumber + "-01", labaBerjalan, BigDecimal.ZERO, loss, username);
                JournalEntry debitEntry = createEntry(journalNumber + "-02", labaDitahan, loss, BigDecimal.ZERO, username);
                transaction.addJournalEntry(creditEntry);
                transaction.addJournalEntry(debitEntry);
            }

            Transaction savedTransaction = transactionRepository.save(transaction);
            closingEntries.addAll(savedTransaction.getJournalEntries());

            log.info("Created retained earnings transfer for year {}: {}", year, netIncome);
        }

        log.info("Completed fiscal year closing for {}: {} entries created", year, closingEntries.size());
        return closingEntries;
    }

    /**
     * Reverse (void) closing entries for a year.
     * Voids the parent Transaction which cascades to journal entries.
     */
    @Transactional
    public int reverseClosing(int year, String reason) {
        List<JournalEntry> closingEntries = getClosingEntries(year);

        if (closingEntries.isEmpty()) {
            throw new IllegalStateException("Tidak ada jurnal penutup untuk tahun " + year);
        }

        String username = getCurrentUsername();
        LocalDateTime now = LocalDateTime.now();

        // Group entries by transaction and void the transactions
        List<Transaction> processedTransactions = new ArrayList<>();
        int count = 0;

        for (JournalEntry entry : closingEntries) {
            if (entry.isPosted()) {
                Transaction transaction = entry.getTransaction();

                // Only process each transaction once
                if (!processedTransactions.contains(transaction)) {
                    transaction.setStatus(TransactionStatus.VOID);
                    transaction.setVoidedAt(now);
                    transaction.setVoidedBy(username);
                    transaction.setVoidNotes("Pembatalan Jurnal Penutup: " + reason);

                    // Update void timestamps on entries
                    for (JournalEntry txEntry : transaction.getJournalEntries()) {
                        txEntry.setVoidedAt(now);
                        txEntry.setVoidReason("Pembatalan Jurnal Penutup: " + reason);
                        count++;
                    }

                    transactionRepository.save(transaction);
                    processedTransactions.add(transaction);
                }
            }
        }

        log.info("Reversed {} closing entries for year {}", count, year);
        return count;
    }

    // Helper methods

    private Transaction createClosingTransaction(JournalTemplate template, LocalDate date, int year,
            String description, String referenceNumber, BigDecimal amount, String username) {
        Transaction transaction = new Transaction();
        transaction.setTransactionNumber(generateTransactionNumber(year));
        transaction.setTransactionDate(date);
        transaction.setJournalTemplate(template);
        transaction.setAmount(amount);
        transaction.setDescription(description);
        transaction.setReferenceNumber(referenceNumber);
        transaction.setStatus(TransactionStatus.POSTED);
        transaction.setPostedAt(LocalDateTime.now());
        transaction.setPostedBy(username);
        transaction.setCreatedBy(username);
        return transaction;
    }

    private String generateTransactionNumber(int year) {
        TransactionSequence sequence = transactionSequenceRepository
                .findBySequenceTypeAndYearForUpdate("FISCAL_CLOSING", year)
                .orElseGet(() -> {
                    TransactionSequence newSeq = new TransactionSequence();
                    newSeq.setSequenceType("FISCAL_CLOSING");
                    newSeq.setPrefix("FC");
                    newSeq.setYear(year);
                    newSeq.setLastNumber(0);
                    return newSeq;
                });

        sequence.setLastNumber(sequence.getLastNumber() + 1);
        transactionSequenceRepository.save(sequence);

        return String.format("FC-%d-%04d", year, sequence.getLastNumber());
    }

    private String generateJournalNumber(LocalDate date, String suffix) {
        // Format: JV-YYYYMMDD-XXXX where XXXX is sequence
        String prefix = "JV-" + date.format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-";
        Integer maxSeq = journalEntryRepository.findMaxSequenceByPrefix(prefix + "%");
        int nextSeq = (maxSeq != null ? maxSeq : 0) + 1;
        return prefix + String.format("%04d", nextSeq);
    }

    private JournalEntry createEntry(String journalNumber, ChartOfAccount account,
            BigDecimal debit, BigDecimal credit, String username) {
        JournalEntry entry = new JournalEntry();
        entry.setJournalNumber(journalNumber);
        entry.setAccount(account);
        entry.setDebitAmount(debit);
        entry.setCreditAmount(credit);
        entry.setPostedAt(LocalDateTime.now());
        entry.setCreatedBy(username);
        return entry;
    }

    private List<ClosingLinePreview> createRevenueClosingLines(
            ReportService.IncomeStatementReport incomeStatement, int year) {
        List<ClosingLinePreview> lines = new ArrayList<>();

        for (ReportService.IncomeStatementItem item : incomeStatement.revenueItems()) {
            if (item.balance().compareTo(BigDecimal.ZERO) != 0) {
                lines.add(new ClosingLinePreview(
                        item.account().getAccountCode(),
                        item.account().getAccountName(),
                        item.balance(),
                        BigDecimal.ZERO,
                        "Tutup " + item.account().getAccountName()
                ));
            }
        }

        lines.add(new ClosingLinePreview(
                LABA_BERJALAN_CODE,
                "Laba Berjalan",
                BigDecimal.ZERO,
                incomeStatement.totalRevenue(),
                "Total Pendapatan Tahun " + year
        ));

        return lines;
    }

    private List<ClosingLinePreview> createExpenseClosingLines(
            ReportService.IncomeStatementReport incomeStatement, int year) {
        List<ClosingLinePreview> lines = new ArrayList<>();

        for (ReportService.IncomeStatementItem item : incomeStatement.expenseItems()) {
            if (item.balance().compareTo(BigDecimal.ZERO) != 0) {
                lines.add(new ClosingLinePreview(
                        item.account().getAccountCode(),
                        item.account().getAccountName(),
                        BigDecimal.ZERO,
                        item.balance(),
                        "Tutup " + item.account().getAccountName()
                ));
            }
        }

        lines.add(new ClosingLinePreview(
                LABA_BERJALAN_CODE,
                "Laba Berjalan",
                incomeStatement.totalExpense(),
                BigDecimal.ZERO,
                "Total Beban Tahun " + year
        ));

        return lines;
    }

    private List<ClosingLinePreview> createRetainedEarningsLines(BigDecimal netIncome, int year) {
        List<ClosingLinePreview> lines = new ArrayList<>();

        if (netIncome.compareTo(BigDecimal.ZERO) > 0) {
            lines.add(new ClosingLinePreview(
                    LABA_BERJALAN_CODE,
                    "Laba Berjalan",
                    netIncome,
                    BigDecimal.ZERO,
                    "Laba Bersih Tahun " + year
            ));
            lines.add(new ClosingLinePreview(
                    LABA_DITAHAN_CODE,
                    "Laba Ditahan",
                    BigDecimal.ZERO,
                    netIncome,
                    "Transfer ke Laba Ditahan"
            ));
        } else {
            BigDecimal loss = netIncome.abs();
            lines.add(new ClosingLinePreview(
                    LABA_BERJALAN_CODE,
                    "Laba Berjalan",
                    BigDecimal.ZERO,
                    loss,
                    "Rugi Bersih Tahun " + year
            ));
            lines.add(new ClosingLinePreview(
                    LABA_DITAHAN_CODE,
                    "Laba Ditahan",
                    loss,
                    BigDecimal.ZERO,
                    "Transfer Rugi ke Laba Ditahan"
            ));
        }

        return lines;
    }

    private String getCurrentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    // Record classes for preview data

    public record ClosingPreview(
            int year,
            BigDecimal totalRevenue,
            BigDecimal totalExpense,
            BigDecimal netIncome,
            List<ClosingEntryPreview> entries,
            boolean alreadyClosed
    ) {}

    public record ClosingEntryPreview(
            String referenceNumber,
            String description,
            LocalDate date,
            List<ClosingLinePreview> lines
    ) {}

    public record ClosingLinePreview(
            String accountCode,
            String accountName,
            BigDecimal debit,
            BigDecimal credit,
            String memo
    ) {}
}
