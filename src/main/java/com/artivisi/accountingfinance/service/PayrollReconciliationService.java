package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.JournalTemplateLine;
import com.artivisi.accountingfinance.entity.PayrollDetail;
import com.artivisi.accountingfinance.entity.PayrollRun;
import com.artivisi.accountingfinance.entity.PayrollStatus;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.entity.TransactionAccountMapping;
import com.artivisi.accountingfinance.enums.JournalPosition;
import com.artivisi.accountingfinance.enums.PayrollReconciliationStatus;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.PayrollDetailRepository;
import com.artivisi.accountingfinance.repository.PayrollRunRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Salary expense in the ledger against the payroll run of a period.
 *
 * <p>A salary account is any account on a debit line of the payroll template whose formula uses
 * grossSalary, bonus or thr. Deriving them from the template keeps this correct on every seed pack,
 * where the same expense sits on different codes.
 *
 * <p>Query API for other features: {@link #reconcile(YearMonth)} and {@link #reconcileYear(int)}.
 * {@code runTaxableIncome} and {@code runPph21} on a row are the PPh 21 base and tax of that masa.
 */
@Service
@Transactional(readOnly = true)
public class PayrollReconciliationService {

    static final String PAYROLL_TEMPLATE_NAME = "Post Gaji Bulanan";

    private static final Pattern SALARY_FORMULA = Pattern.compile("\\b(grossSalary|bonus|thr)\\b");
    private static final Pattern PPH21_FORMULA = Pattern.compile("\\bpph21\\b");

    @Value("${app.payroll.template-id}")
    private UUID payrollTemplateId;

    private final PayrollRunRepository payrollRunRepository;
    private final PayrollDetailRepository payrollDetailRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final JournalTemplateRepository journalTemplateRepository;
    private final TransactionRepository transactionRepository;

    public PayrollReconciliationService(PayrollRunRepository payrollRunRepository,
                                        PayrollDetailRepository payrollDetailRepository,
                                        JournalEntryRepository journalEntryRepository,
                                        JournalTemplateRepository journalTemplateRepository,
                                        TransactionRepository transactionRepository) {
        this.payrollRunRepository = payrollRunRepository;
        this.payrollDetailRepository = payrollDetailRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.journalTemplateRepository = journalTemplateRepository;
        this.transactionRepository = transactionRepository;
    }

    /** The template payroll posts through: the configured id, else the seed pack's template name. */
    public JournalTemplate resolvePayrollTemplate() {
        return journalTemplateRepository.findByIdWithLines(payrollTemplateId)
                .or(() -> journalTemplateRepository.findByTemplateNameWithLines(PAYROLL_TEMPLATE_NAME))
                .orElseThrow(() -> new IllegalStateException("Template payroll tidak ditemukan (id: "
                        + payrollTemplateId + ", name: " + PAYROLL_TEMPLATE_NAME + ")"));
    }

    // ==================== Warnings ====================

    /**
     * Warnings for a journal outside payroll that debits a salary account in a period with an active run.
     * Journal lines carry no employee, so the match is on period. An employee named in the description
     * or notes is reported by name.
     */
    public List<String> warningsForTransaction(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found: " + transactionId));
        if (transaction.getStatus() == TransactionStatus.VOID || Boolean.TRUE.equals(transaction.getClosingEntry())) {
            return List.of();
        }
        YearMonth period = YearMonth.from(transaction.getTransactionDate());
        Optional<PayrollRun> run = activeRun(period);
        if (run.isEmpty() || isRunJournal(run.get(), transaction)) {
            return List.of();
        }
        Map<UUID, ChartOfAccount> salaryAccounts = salaryAccounts();
        List<ChartOfAccount> debited = debitedAccounts(transaction).keySet().stream()
                .filter(a -> salaryAccounts.containsKey(a.getId()))
                .toList();
        if (debited.isEmpty()) {
            return List.of();
        }

        List<PayrollDetail> details = payrollDetailRepository.findByPayrollRunIdWithEmployee(run.get().getId());
        StringBuilder message = new StringBuilder("Transaksi ini mendebit akun gaji ")
                .append(describe(debited))
                .append(" pada periode ").append(period)
                .append(". Payroll periode ini sudah ada dengan status ").append(run.get().getStatus().getDisplayName());
        if (!details.isEmpty()) {
            message.append(" dan mencakup ").append(details.size()).append(" karyawan");
        }
        message.append(". Periksa rekonsiliasi gaji agar beban gaji tidak tercatat dua kali.");
        String named = employeesNamedIn(transaction, details);
        if (!named.isEmpty()) {
            message.append(" Karyawan yang disebut pada transaksi ini: ").append(named).append('.');
        }
        return List.of(message.toString());
    }

    /** One warning per journal outside the run that debits a salary account in the run's period. */
    public List<String> warningsForRun(UUID payrollRunId) {
        PayrollRun run = payrollRunRepository.findById(payrollRunId)
                .orElseThrow(() -> new IllegalArgumentException("Payroll run tidak ditemukan"));
        if (run.getStatus() == PayrollStatus.CANCELLED) {
            return List.of();
        }
        return journalsOutsideRun(run.getPeriod(), run, salaryAccounts()).stream()
                .map(j -> "Transaksi " + (j.transactionNumber() != null ? j.transactionNumber() : "draf")
                        + " tanggal " + j.transactionDate()
                        + " (" + j.description() + ") mendebit akun gaji " + String.join(", ", j.accounts())
                        + " sebesar Rp " + String.format(Locale.ROOT, "%,.0f", j.amount())
                        + " di luar payroll periode " + run.getPayrollPeriod()
                        + ". Periksa rekonsiliasi gaji agar beban gaji tidak tercatat dua kali.")
                .toList();
    }

    // ==================== Reconciliation ====================

    public PeriodReconciliation reconcile(YearMonth period) {
        return reconcile(period, salaryAccounts());
    }

    public YearReconciliation reconcileYear(int year) {
        Map<UUID, ChartOfAccount> salaryAccounts = salaryAccounts();
        List<PeriodReconciliation> periods = IntStream.rangeClosed(1, 12)
                .mapToObj(month -> reconcile(YearMonth.of(year, month), salaryAccounts))
                .toList();
        List<PeriodReconciliation> posted = periods.stream()
                .filter(p -> p.runStatus() == PayrollStatus.POSTED)
                .toList();
        return new YearReconciliation(
                year,
                periods,
                sum(periods, PeriodReconciliation::ledgerSalaryExpense),
                sum(posted, PeriodReconciliation::runCashGross),
                sum(posted, PeriodReconciliation::runTaxableIncome),
                sum(posted, PeriodReconciliation::runPph21),
                sum(periods, PeriodReconciliation::difference));
    }

    private PeriodReconciliation reconcile(YearMonth period, Map<UUID, ChartOfAccount> salaryAccounts) {
        LocalDate start = period.atDay(1);
        LocalDate end = period.atEndOfMonth();
        BigDecimal ledger = BigDecimal.ZERO;
        for (UUID accountId : salaryAccounts.keySet()) {
            ledger = ledger
                    .add(journalEntryRepository.sumDebitByAccountAndDateRangeExcludingClosing(accountId, start, end))
                    .subtract(journalEntryRepository.sumCreditByAccountAndDateRangeExcludingClosing(accountId, start, end));
        }

        Optional<PayrollRun> activeRun = activeRun(period);
        if (activeRun.isEmpty()) {
            PayrollReconciliationStatus status = ledger.signum() == 0
                    ? PayrollReconciliationStatus.NO_ACTIVITY : PayrollReconciliationStatus.LEDGER_WITHOUT_RUN;
            return new PeriodReconciliation(period.toString(), null, null, 0,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO,
                    ledger, BigDecimal.ZERO, ledger, BigDecimal.ZERO, ledger, status,
                    journalsOutsideRun(period, null, salaryAccounts));
        }

        PayrollRun run = activeRun.get();
        List<PayrollDetail> details = payrollDetailRepository.findByPayrollRunIdWithEmployee(run.getId());
        BigDecimal cashGross = sumDetails(details, PayrollDetail::getGrossSalary);

        BigDecimal ledgerFromRun = BigDecimal.ZERO;
        BigDecimal pph21FromRun = BigDecimal.ZERO;
        Transaction runJournal = run.getTransaction();
        if (runJournal != null && runJournal.getStatus() == TransactionStatus.POSTED) {
            Map<UUID, ChartOfAccount> pph21Accounts = pph21Accounts();
            for (JournalEntry entry : journalEntryRepository.findByTransactionIdWithAccount(runJournal.getId())) {
                if (salaryAccounts.containsKey(entry.getAccount().getId())) {
                    ledgerFromRun = ledgerFromRun.add(entry.getDebitAmount()).subtract(entry.getCreditAmount());
                }
                if (pph21Accounts.containsKey(entry.getAccount().getId())) {
                    pph21FromRun = pph21FromRun.add(entry.getCreditAmount()).subtract(entry.getDebitAmount());
                }
            }
        }

        boolean posted = run.getStatus() == PayrollStatus.POSTED;
        BigDecimal difference = posted ? ledger.subtract(cashGross) : ledger;
        PayrollReconciliationStatus status;
        if (!posted) {
            status = PayrollReconciliationStatus.RUN_NOT_POSTED;
        } else if (difference.signum() == 0) {
            status = PayrollReconciliationStatus.MATCHED;
        } else {
            status = PayrollReconciliationStatus.DIFFERENCE;
        }

        return new PeriodReconciliation(period.toString(), run.getId(), run.getStatus(), details.size(),
                sumDetails(details, PayrollDetail::getSalaryPortion),
                sumDetails(details, PayrollDetail::getBonus),
                sumDetails(details, PayrollDetail::getThr),
                cashGross,
                sumDetails(details, PayrollDetail::getBenefitInKind),
                sumDetails(details, PayrollDetail::getTaxableGross),
                sumDetails(details, PayrollDetail::getPph21),
                ledger, ledgerFromRun, ledger.subtract(ledgerFromRun), pph21FromRun, difference, status,
                journalsOutsideRun(period, run, salaryAccounts));
    }

    // ==================== Rows behind the figures ====================

    /** Lines behind {@link PeriodReconciliation#ledgerSalaryExpense()}: posted, non-closing, on salary accounts. */
    public List<JournalEntry> salaryExpenseLines(YearMonth period) {
        return journalEntryRepository.findPostedLinesByAccountsExcludingClosing(
                salaryAccounts().keySet(), period.atDay(1), period.atEndOfMonth());
    }

    /** Details behind {@link PeriodReconciliation#runTaxableIncome()}: the active run's rows, empty without a run. */
    public List<PayrollDetail> runDetails(YearMonth period) {
        return activeRun(period)
                .map(run -> payrollDetailRepository.findByPayrollRunIdWithEmployee(run.getId()))
                .orElse(List.of());
    }

    // ==================== Helpers ====================

    private Optional<PayrollRun> activeRun(YearMonth period) {
        return payrollRunRepository.findByPayrollPeriod(period.toString())
                .filter(r -> r.getStatus() != PayrollStatus.CANCELLED);
    }

    private boolean isRunJournal(PayrollRun run, Transaction transaction) {
        return run.getTransaction() != null && run.getTransaction().getId().equals(transaction.getId());
    }

    private Map<UUID, ChartOfAccount> salaryAccounts() {
        Map<UUID, ChartOfAccount> accounts = templateAccounts(JournalPosition.DEBIT, SALARY_FORMULA);
        if (accounts.isEmpty()) {
            throw new IllegalStateException("Template payroll tidak punya baris debit dengan formula grossSalary");
        }
        return accounts;
    }

    private Map<UUID, ChartOfAccount> pph21Accounts() {
        return templateAccounts(JournalPosition.CREDIT, PPH21_FORMULA);
    }

    private Map<UUID, ChartOfAccount> templateAccounts(JournalPosition position, Pattern formula) {
        Map<UUID, ChartOfAccount> accounts = new LinkedHashMap<>();
        for (JournalTemplateLine line : resolvePayrollTemplate().getLines()) {
            if (line.getPosition() == position && line.getAccount() != null
                    && formula.matcher(line.getFormula()).find()) {
                accounts.put(line.getAccount().getId(), line.getAccount());
            }
        }
        return accounts;
    }

    /**
     * Accounts a transaction debits, with the amount. Drafts from a template have no journal lines until
     * posted, so those come from the template lines and account mappings, with the transaction amount.
     */
    private Map<ChartOfAccount, BigDecimal> debitedAccounts(Transaction transaction) {
        Map<ChartOfAccount, BigDecimal> debited = new LinkedHashMap<>();
        if (!transaction.getJournalEntries().isEmpty()) {
            for (JournalEntry entry : transaction.getJournalEntries()) {
                if (entry.getDebitAmount().signum() > 0) {
                    debited.merge(entry.getAccount(), entry.getDebitAmount(), BigDecimal::add);
                }
            }
            return debited;
        }
        Map<UUID, ChartOfAccount> overrides = new HashMap<>();
        for (TransactionAccountMapping mapping : transaction.getAccountMappings()) {
            overrides.put(mapping.getTemplateLine().getId(), mapping.getAccount());
        }
        for (JournalTemplateLine line : transaction.getJournalTemplate().getLines()) {
            ChartOfAccount account = overrides.getOrDefault(line.getId(), line.getAccount());
            if (line.getPosition() == JournalPosition.DEBIT && account != null) {
                debited.put(account, transaction.getAmount());
            }
        }
        return debited;
    }

    private List<SalaryJournal> journalsOutsideRun(YearMonth period, PayrollRun run, Map<UUID, ChartOfAccount> salaryAccounts) {
        LocalDate start = period.atDay(1);
        LocalDate end = period.atEndOfMonth();
        List<Transaction> candidates = new ArrayList<>(
                transactionRepository.findPostedDebitingAccounts(salaryAccounts.keySet(), start, end));
        candidates.addAll(transactionRepository.findByStatusAndTransactionDateBetween(TransactionStatus.DRAFT, start, end));

        List<SalaryJournal> journals = new ArrayList<>();
        for (Transaction transaction : candidates) {
            if (Boolean.TRUE.equals(transaction.getClosingEntry()) || (run != null && isRunJournal(run, transaction))) {
                continue;
            }
            Map<ChartOfAccount, BigDecimal> hits = debitedAccounts(transaction).entrySet().stream()
                    .filter(e -> salaryAccounts.containsKey(e.getKey().getId()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, BigDecimal::add, LinkedHashMap::new));
            if (!hits.isEmpty()) {
                journals.add(new SalaryJournal(
                        transaction.getId(),
                        transaction.getTransactionNumber(),
                        transaction.getTransactionDate(),
                        transaction.getStatus().name(),
                        transaction.getDescription(),
                        hits.keySet().stream().map(ChartOfAccount::getAccountCode).sorted().toList(),
                        hits.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)));
            }
        }
        journals.sort(Comparator.comparing(SalaryJournal::transactionDate).thenComparing(SalaryJournal::transactionId));
        return journals;
    }

    private String describe(List<ChartOfAccount> accounts) {
        return accounts.stream()
                .map(a -> a.getAccountCode() + " " + a.getAccountName())
                .collect(Collectors.joining(", "));
    }

    private String employeesNamedIn(Transaction transaction, List<PayrollDetail> details) {
        String text = ((transaction.getDescription() == null ? "" : transaction.getDescription()) + " "
                + (transaction.getNotes() == null ? "" : transaction.getNotes())).toLowerCase(Locale.ROOT);
        return details.stream()
                .filter(d -> text.contains(d.getEmployeeName().toLowerCase(Locale.ROOT))
                        || text.contains(d.getEmployeeId().toLowerCase(Locale.ROOT)))
                .map(PayrollDetail::getEmployeeName)
                .collect(Collectors.joining(", "));
    }

    private static BigDecimal sumDetails(List<PayrollDetail> details, Function<PayrollDetail, BigDecimal> field) {
        return details.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal sum(List<PeriodReconciliation> rows, Function<PeriodReconciliation, BigDecimal> field) {
        return rows.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ==================== Results ====================

    /** A journal outside the run that debits a salary account. DRAFT rows are not in the ledger total yet. */
    public record SalaryJournal(
            UUID transactionId,
            String transactionNumber,
            LocalDate transactionDate,
            String status,
            String description,
            List<String> accounts,
            BigDecimal amount
    ) {}

    /**
     * One period.
     *
     * @param runId             active (not cancelled) run, null when the period has none
     * @param runStatus         null when the period has no active run
     * @param runSalary         regular salary in the run, excluding bonus and THR
     * @param runCashGross      runSalary + runBonus + runThr
     * @param runBenefitInKind  natura counted in the run: PPh 21 base, booked by its own journal, not on salary accounts
     * @param runTaxableIncome  PPh 21 base of the masa: runCashGross + runBenefitInKind
     * @param runPph21          PPh 21 of the masa; negative when December returns an excess
     * @param ledgerSalaryExpense net debit on salary accounts from POSTED journals, closing entries excluded
     * @param ledgerFromRun     the part from the run's own journal
     * @param ledgerOutsideRun  ledgerSalaryExpense - ledgerFromRun
     * @param ledgerPph21FromRun net credit on the PPh 21 account from the run's own journal
     * @param difference        ledgerSalaryExpense - runCashGross when POSTED, else ledgerSalaryExpense:
     *                          an unposted run has put nothing in the ledger yet
     * @param journalsOutsideRun journals behind ledgerOutsideRun, plus drafts that would add to it
     */
    public record PeriodReconciliation(
            String period,
            UUID runId,
            PayrollStatus runStatus,
            int employeeCount,
            BigDecimal runSalary,
            BigDecimal runBonus,
            BigDecimal runThr,
            BigDecimal runCashGross,
            BigDecimal runBenefitInKind,
            BigDecimal runTaxableIncome,
            BigDecimal runPph21,
            BigDecimal ledgerSalaryExpense,
            BigDecimal ledgerFromRun,
            BigDecimal ledgerOutsideRun,
            BigDecimal ledgerPph21FromRun,
            BigDecimal difference,
            PayrollReconciliationStatus status,
            List<SalaryJournal> journalsOutsideRun
    ) {}

    /** Twelve periods with totals. The posted* totals count POSTED runs only, the base of 1721-A1. */
    public record YearReconciliation(
            int year,
            List<PeriodReconciliation> periods,
            BigDecimal ledgerSalaryExpense,
            BigDecimal postedCashGross,
            BigDecimal postedTaxableIncome,
            BigDecimal postedPph21,
            BigDecimal difference
    ) {}
}
