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

    // Account names for error messages and descriptions
    private static final String LABA_BERJALAN = "Laba Berjalan";
    private static final String LABA_DITAHAN = "Laba Ditahan";
    private static final String CLOSING_REF_PREFIX = "CLOSING-";
    private static final String SEQ_TYPE_FISCAL_CLOSING = "FISCAL_CLOSING";

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
        String referencePattern = CLOSING_REF_PREFIX + year + "-%";
        return journalEntryRepository.countByReferenceNumberLike(referencePattern) > 0;
    }

    /**
     * Get existing closing entries for a year.
     */
    public List<JournalEntry> getClosingEntries(int year) {
        String referencePattern = CLOSING_REF_PREFIX + year + "-%";
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

        // Entry 1: Close Revenue to LABA_BERJALAN
        if (incomeStatement.totalRevenue().compareTo(BigDecimal.ZERO) != 0) {
            entries.add(new ClosingEntryPreview(
                    CLOSING_REF_PREFIX + year + "-01",
                    "Tutup Pendapatan ke LABA_BERJALAN",
                    yearEnd,
                    createRevenueClosingLines(incomeStatement, year)
            ));
        }

        // Entry 2: Close Expenses to LABA_BERJALAN
        if (incomeStatement.totalExpense().compareTo(BigDecimal.ZERO) != 0) {
            entries.add(new ClosingEntryPreview(
                    CLOSING_REF_PREFIX + year + "-02",
                    "Tutup Beban ke LABA_BERJALAN",
                    yearEnd,
                    createExpenseClosingLines(incomeStatement, year)
            ));
        }

        // Entry 3: Transfer LABA_BERJALAN to LABA_DITAHAN
        BigDecimal netIncome = incomeStatement.netIncome();
        if (netIncome.compareTo(BigDecimal.ZERO) != 0) {
            entries.add(new ClosingEntryPreview(
                    CLOSING_REF_PREFIX + year + "-03",
                    "Transfer LABA_BERJALAN ke LABA_DITAHAN",
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
        validateNoExistingClosing(year);

        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        LocalDate yearStart = LocalDate.of(year, 1, 1);

        ReportService.IncomeStatementReport incomeStatement =
                reportService.generateIncomeStatement(yearStart, yearEnd);

        ClosingContext ctx = new ClosingContext(
                year, yearEnd, getCurrentUsername(),
                getClosingTemplate(),
                getAccountByCode(LABA_BERJALAN_CODE, LABA_BERJALAN),
                getAccountByCode(LABA_DITAHAN_CODE, LABA_DITAHAN)
        );

        List<JournalEntry> closingEntries = new ArrayList<>();
        closingEntries.addAll(closeRevenueAccounts(ctx, incomeStatement));
        closingEntries.addAll(closeExpenseAccounts(ctx, incomeStatement));
        closingEntries.addAll(transferToRetainedEarnings(ctx, incomeStatement.netIncome()));

        log.info("Completed fiscal year closing for {}: {} entries created", year, closingEntries.size());
        return closingEntries;
    }

    private record ClosingContext(int year, LocalDate yearEnd, String username,
            JournalTemplate template, ChartOfAccount labaBerjalan, ChartOfAccount labaDitahan) {}

    private void validateNoExistingClosing(int year) {
        if (hasClosingEntries(year)) {
            throw new IllegalStateException(
                    "Jurnal penutup untuk tahun " + year + " sudah ada. " +
                    "Hapus jurnal penutup yang ada terlebih dahulu jika ingin membuat ulang.");
        }
    }

    private JournalTemplate getClosingTemplate() {
        return journalTemplateRepository.findById(CLOSING_TEMPLATE_ID)
                .orElseThrow(() -> new IllegalStateException("Fiscal year closing template not found"));
    }

    private ChartOfAccount getAccountByCode(String code, String name) {
        return chartOfAccountRepository.findByAccountCode(code)
                .orElseThrow(() -> new EntityNotFoundException("Akun " + name + " (" + code + ") tidak ditemukan"));
    }

    private List<JournalEntry> closeRevenueAccounts(ClosingContext ctx, ReportService.IncomeStatementReport report) {
        if (report.totalRevenue().compareTo(BigDecimal.ZERO) == 0) {
            return List.of();
        }

        Transaction transaction = createClosingTransaction(ctx.template, ctx.yearEnd, ctx.year,
                "Tutup Pendapatan ke LABA_BERJALAN - Tahun " + ctx.year,
                CLOSING_REF_PREFIX + ctx.year + "-01", report.totalRevenue(), ctx.username);

        String journalNumber = generateJournalNumber(ctx.yearEnd);
        int lineIndex = addItemEntries(transaction, report.revenueItems(), journalNumber, ctx.username, true);

        transaction.addJournalEntry(createEntry(
                journalNumber + "-" + String.format("%02d", lineIndex + 1),
                ctx.labaBerjalan, BigDecimal.ZERO, report.totalRevenue(), ctx.username));

        Transaction saved = transactionRepository.save(transaction);
        log.info("Created revenue closing entry for year {}: {}", ctx.year, report.totalRevenue());
        return new ArrayList<>(saved.getJournalEntries());
    }

    private List<JournalEntry> closeExpenseAccounts(ClosingContext ctx, ReportService.IncomeStatementReport report) {
        if (report.totalExpense().compareTo(BigDecimal.ZERO) == 0) {
            return List.of();
        }

        Transaction transaction = createClosingTransaction(ctx.template, ctx.yearEnd, ctx.year,
                "Tutup Beban ke LABA_BERJALAN - Tahun " + ctx.year,
                CLOSING_REF_PREFIX + ctx.year + "-02", report.totalExpense(), ctx.username);

        String journalNumber = generateJournalNumber(ctx.yearEnd);
        int lineIndex = addItemEntries(transaction, report.expenseItems(), journalNumber, ctx.username, false);

        transaction.addJournalEntry(createEntry(
                journalNumber + "-" + String.format("%02d", lineIndex + 1),
                ctx.labaBerjalan, report.totalExpense(), BigDecimal.ZERO, ctx.username));

        Transaction saved = transactionRepository.save(transaction);
        log.info("Created expense closing entry for year {}: {}", ctx.year, report.totalExpense());
        return new ArrayList<>(saved.getJournalEntries());
    }

    private int addItemEntries(Transaction transaction, List<ReportService.IncomeStatementItem> items,
            String journalNumber, String username, boolean isDebit) {
        int lineIndex = 0;
        for (ReportService.IncomeStatementItem item : items) {
            if (item.balance().compareTo(BigDecimal.ZERO) != 0) {
                BigDecimal debit = isDebit ? item.balance() : BigDecimal.ZERO;
                BigDecimal credit = isDebit ? BigDecimal.ZERO : item.balance();
                transaction.addJournalEntry(createEntry(
                        journalNumber + "-" + String.format("%02d", ++lineIndex),
                        item.account(), debit, credit, username));
            }
        }
        return lineIndex;
    }

    private List<JournalEntry> transferToRetainedEarnings(ClosingContext ctx, BigDecimal netIncome) {
        if (netIncome.compareTo(BigDecimal.ZERO) == 0) {
            return List.of();
        }

        boolean isProfit = netIncome.compareTo(BigDecimal.ZERO) > 0;
        String description = (isProfit ? "Transfer Laba" : "Transfer Rugi") +
                " Berjalan ke LABA_DITAHAN - Tahun " + ctx.year;

        Transaction transaction = createClosingTransaction(ctx.template, ctx.yearEnd, ctx.year,
                description, CLOSING_REF_PREFIX + ctx.year + "-03", netIncome.abs(), ctx.username);

        String journalNumber = generateJournalNumber(ctx.yearEnd);
        BigDecimal amount = netIncome.abs();

        if (isProfit) {
            transaction.addJournalEntry(createEntry(journalNumber + "-01", ctx.labaBerjalan, amount, BigDecimal.ZERO, ctx.username));
            transaction.addJournalEntry(createEntry(journalNumber + "-02", ctx.labaDitahan, BigDecimal.ZERO, amount, ctx.username));
        } else {
            transaction.addJournalEntry(createEntry(journalNumber + "-01", ctx.labaBerjalan, BigDecimal.ZERO, amount, ctx.username));
            transaction.addJournalEntry(createEntry(journalNumber + "-02", ctx.labaDitahan, amount, BigDecimal.ZERO, ctx.username));
        }

        Transaction saved = transactionRepository.save(transaction);
        log.info("Created retained earnings transfer for year {}: {}", ctx.year, netIncome);
        return new ArrayList<>(saved.getJournalEntries());
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
                .findBySequenceTypeAndYearForUpdate("SEQ_TYPE_FISCAL_CLOSING", year)
                .orElseGet(() -> {
                    TransactionSequence newSeq = new TransactionSequence();
                    newSeq.setSequenceType("SEQ_TYPE_FISCAL_CLOSING");
                    newSeq.setPrefix("FC");
                    newSeq.setYear(year);
                    newSeq.setLastNumber(0);
                    return newSeq;
                });

        sequence.setLastNumber(sequence.getLastNumber() + 1);
        transactionSequenceRepository.save(sequence);

        return String.format("FC-%d-%04d", year, sequence.getLastNumber());
    }

    private String generateJournalNumber(LocalDate date) {
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
                "LABA_BERJALAN",
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
                "LABA_BERJALAN",
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
                    "LABA_BERJALAN",
                    netIncome,
                    BigDecimal.ZERO,
                    "Laba Bersih Tahun " + year
            ));
            lines.add(new ClosingLinePreview(
                    LABA_DITAHAN_CODE,
                    "LABA_DITAHAN",
                    BigDecimal.ZERO,
                    netIncome,
                    "Transfer ke LABA_DITAHAN"
            ));
        } else {
            BigDecimal loss = netIncome.abs();
            lines.add(new ClosingLinePreview(
                    LABA_BERJALAN_CODE,
                    "LABA_BERJALAN",
                    BigDecimal.ZERO,
                    loss,
                    "Rugi Bersih Tahun " + year
            ));
            lines.add(new ClosingLinePreview(
                    LABA_DITAHAN_CODE,
                    "LABA_DITAHAN",
                    loss,
                    BigDecimal.ZERO,
                    "Transfer Rugi ke LABA_DITAHAN"
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
