package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.PayrollDetail;
import com.artivisi.accountingfinance.entity.PayrollStatus;
import com.artivisi.accountingfinance.entity.SelfTestExplanation;
import com.artivisi.accountingfinance.entity.TaxTransactionDetail;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.AccountType;
import com.artivisi.accountingfinance.enums.SelfTestLine;
import com.artivisi.accountingfinance.enums.SelfTestRole;
import com.artivisi.accountingfinance.enums.TaxType;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository.TransactionAmount;
import com.artivisi.accountingfinance.repository.SelfTestExplanationRepository;
import com.artivisi.accountingfinance.service.AssetRegisterQueryService.AssetCostMovement;
import com.artivisi.accountingfinance.service.AssetRegisterQueryService.CostMovementRow;
import com.artivisi.accountingfinance.service.PayrollReconciliationService.PeriodReconciliation;
import com.artivisi.accountingfinance.service.PayrollReconciliationService.YearReconciliation;
import com.artivisi.accountingfinance.service.SalesTaxChainService.FakturLink;
import com.artivisi.accountingfinance.service.SalesTaxChainService.Reconciliation;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Tax-office self-test (issue #45): yearly cross-checks a tax office runs from the annual return, each with a book
 * figure, a tax figure, the difference (book minus tax) and the user's explanation.
 *
 * <p>Figures come from the services that own them: {@link SalesTaxChainService} (revenue vs PPN DPP),
 * {@link PayrollReconciliationService} (salary vs PPh 21 base), {@link WithholdingQueryService} (PPh 23 and 4(2)
 * base), {@link AssetRegisterQueryService} (fixed assets) and {@link EvidenceQueryService} (unitemised other
 * expenses). {@link #trace} returns the rows behind each figure; the rows of a figure add up to it.
 *
 * <p>Definitions:
 * <ul>
 *   <li>Ledger figures count POSTED, non-closing journal lines dated in the period.</li>
 *   <li>Capital: every EQUITY account, credit minus debit. Non-operating assets: accounts with role
 *       {@link SelfTestRole#NON_OPERATING_ASSET}, debit minus credit. The return has no counterpart figure, so the
 *       tax figure is null and the difference is the whole movement.</li>
 *   <li>Unitemised: a debit line of at least the configured threshold on an {@link SelfTestRole#OTHER_EXPENSE}
 *       account whose transaction has a blank description or no evidence item (classified file, issued invoice,
 *       bupot, matched bank line, linked contract). The tax figure is the rest of the other expenses.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class SelfTestService {

    public static final String SOURCE_JOURNAL_LINE = "JOURNAL_LINE";
    public static final String SOURCE_REVENUE_TRANSACTION = "REVENUE_TRANSACTION";
    public static final String SOURCE_FAKTUR = "FAKTUR";
    public static final String SOURCE_PAYROLL_DETAIL = "PAYROLL_DETAIL";
    public static final String SOURCE_BUPOT = "BUPOT";
    public static final String SOURCE_ASSET = "ASSET";

    private final SalesTaxChainService salesTaxChainService;
    private final PayrollReconciliationService payrollReconciliationService;
    private final WithholdingQueryService withholdingQueryService;
    private final AssetRegisterQueryService assetRegisterQueryService;
    private final EvidenceQueryService evidenceQueryService;
    private final ChartOfAccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final SelfTestExplanationRepository explanationRepository;
    private final BigDecimal unitemisedThreshold;

    public SelfTestService(SalesTaxChainService salesTaxChainService,
                           PayrollReconciliationService payrollReconciliationService,
                           WithholdingQueryService withholdingQueryService,
                           AssetRegisterQueryService assetRegisterQueryService,
                           EvidenceQueryService evidenceQueryService,
                           ChartOfAccountRepository accountRepository,
                           JournalEntryRepository journalEntryRepository,
                           SelfTestExplanationRepository explanationRepository,
                           @Value("${app.self-test.unitemised-threshold}") BigDecimal unitemisedThreshold) {
        this.salesTaxChainService = salesTaxChainService;
        this.payrollReconciliationService = payrollReconciliationService;
        this.withholdingQueryService = withholdingQueryService;
        this.assetRegisterQueryService = assetRegisterQueryService;
        this.evidenceQueryService = evidenceQueryService;
        this.accountRepository = accountRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.explanationRepository = explanationRepository;
        this.unitemisedThreshold = unitemisedThreshold;
    }

    @PostConstruct
    void validateThreshold() {
        if (unitemisedThreshold.signum() <= 0) {
            throw new IllegalStateException("app.self-test.unitemised-threshold harus lebih dari nol: " + unitemisedThreshold);
        }
    }

    public enum LineStatus {
        /** Book and tax figures are equal and not zero. */
        MATCHED("Sesuai"),
        /** Book and tax figures differ. */
        DIFFERENCE("Ada selisih"),
        /** No tax figure exists and the book movement is not zero: the movement needs an explanation. */
        MOVEMENT("Ada mutasi"),
        /** Both figures zero. */
        NO_ACTIVITY("Tidak ada aktivitas"),
        /** No account carries the role the line needs; figures are null. */
        NOT_CONFIGURED("Belum dikonfigurasi");

        private final String label;

        LineStatus(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /**
     * One row. month 0 is the whole year; per-masa lines also have rows 1-12. taxFigure is null on lines without
     * a counterpart in the return and on NOT_CONFIGURED rows. difference = bookFigure - taxFigure, or bookFigure
     * when there is no tax figure.
     */
    public record Line(SelfTestLine line, int month, String title, String bookBasis, String taxBasis,
                       BigDecimal bookFigure, BigDecimal taxFigure, BigDecimal difference, LineStatus status,
                       String message, String explanation, String explainedBy, LocalDateTime explainedAt) {
    }

    public record Report(int year, BigDecimal unitemisedThreshold, List<Line> lines) {
    }

    /**
     * A row behind a figure. source tells what it is: JOURNAL_LINE (journalEntryId set), REVENUE_TRANSACTION
     * (revenue of one transaction), FAKTUR, BUPOT (tax detail id in referenceId), PAYROLL_DETAIL, ASSET.
     */
    public record TraceRow(String source, UUID referenceId, UUID transactionId, String transactionNumber, LocalDate date,
                           String accountCode, String reference, String description, BigDecimal amount) {
    }

    /** Rows behind one row of the report: bookRows add up to bookFigure, taxRows to taxFigure. */
    public record Trace(int year, SelfTestLine line, int month, BigDecimal bookFigure, BigDecimal taxFigure,
                        List<TraceRow> bookRows, List<TraceRow> taxRows) {
    }

    public BigDecimal unitemisedThreshold() {
        return unitemisedThreshold;
    }

    // ==================== report ====================

    public Report report(int year) {
        Map<String, SelfTestExplanation> explanations = explanationRepository.findByReportYear(year).stream()
                .collect(Collectors.toMap(e -> key(e.getLine(), e.getPeriodMonth()), Function.identity()));
        List<Line> lines = new ArrayList<>();
        Reconciliation[] revenue = IntStream.rangeClosed(1, 12)
                .mapToObj(m -> salesTaxChainService.getReconciliation(year, m)).toArray(Reconciliation[]::new);
        BigDecimal bookTotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;
        List<Line> months = new ArrayList<>();
        for (Reconciliation r : revenue) {
            months.add(line(SelfTestLine.REVENUE_VS_PPN_DPP, r.month(), r.bookedRevenue(), r.sptDpp(), null, explanations));
            bookTotal = bookTotal.add(r.bookedRevenue());
            taxTotal = taxTotal.add(r.sptDpp());
        }
        lines.add(line(SelfTestLine.REVENUE_VS_PPN_DPP, 0, bookTotal, taxTotal, null, explanations));
        lines.addAll(months);

        YearReconciliation payroll = payrollReconciliationService.reconcileYear(year);
        bookTotal = BigDecimal.ZERO;
        taxTotal = BigDecimal.ZERO;
        months = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            PeriodReconciliation p = payroll.periods().get(m - 1);
            BigDecimal tax = postedTaxableIncome(p);
            String message = p.runStatus() != null && p.runStatus() != PayrollStatus.POSTED
                    ? "Payroll " + p.period() + " berstatus " + p.runStatus() + ": belum masuk DPP PPh 21" : null;
            months.add(line(SelfTestLine.SALARY_VS_PPH21_DPP, m, p.ledgerSalaryExpense(), tax, message, explanations));
            bookTotal = bookTotal.add(p.ledgerSalaryExpense());
            taxTotal = taxTotal.add(tax);
        }
        lines.add(line(SelfTestLine.SALARY_VS_PPH21_DPP, 0, bookTotal, taxTotal, null, explanations));
        lines.addAll(months);

        for (SelfTestLine line : List.of(SelfTestLine.SERVICE_VS_PPH23_DPP, SelfTestLine.RENT_VS_PPH42_DPP,
                SelfTestLine.NON_OPERATING_ASSET_MOVEMENT, SelfTestLine.UNITEMISED_OTHER_EXPENSE)) {
            Optional<String> missing = missingRole(line);
            if (missing.isPresent()) {
                lines.add(notConfigured(line, missing.get(), explanations));
            } else {
                Trace trace = trace(year, line, 0);
                lines.add(line(line, 0, trace.bookFigure(), trace.taxFigure(), null, explanations));
            }
        }

        CostMovementRow register = assetRegisterQueryService.costMovement(year).total();
        Trace additions = ledgerAssetRows(year, true);
        lines.add(line(SelfTestLine.FIXED_ASSET_ADDITIONS, 0, sum(additions.bookRows()), register.additions(), null, explanations));
        Trace disposals = ledgerAssetRows(year, false);
        lines.add(line(SelfTestLine.FIXED_ASSET_DISPOSALS, 0, sum(disposals.bookRows()), register.disposals(), null, explanations));

        Trace capital = trace(year, SelfTestLine.CAPITAL_MOVEMENT, 0);
        lines.add(line(SelfTestLine.CAPITAL_MOVEMENT, 0, capital.bookFigure(), null, null, explanations));

        lines.sort((a, b) -> a.line() == b.line() ? Integer.compare(a.month(), b.month()) : a.line().compareTo(b.line()));
        return new Report(year, unitemisedThreshold, lines);
    }

    // ==================== trace ====================

    /** Rows behind one row of {@link #report(int)}. month 0 is the whole year; 1-12 only on per-masa lines. */
    public Trace trace(int year, SelfTestLine line, int month) {
        validateMonth(line, month);
        missingRole(line).ifPresent(message -> {
            throw new IllegalStateException(message);
        });
        List<TraceRow> book = new ArrayList<>();
        List<TraceRow> tax = new ArrayList<>();
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);
        switch (line) {
            case REVENUE_VS_PPN_DPP -> forMonths(month, m -> {
                salesTaxChainService.revenueRows(year, m).forEach(r -> book.add(revenueRow(r)));
                salesTaxChainService.sptFakturs(year, m).forEach(f -> tax.add(fakturRow(f)));
            });
            case SALARY_VS_PPH21_DPP -> forMonths(month, m -> {
                YearMonth period = YearMonth.of(year, m);
                payrollReconciliationService.salaryExpenseLines(period).forEach(j -> book.add(journalRow(j, debitMinusCredit(j))));
                PeriodReconciliation p = payrollReconciliationService.reconcile(period);
                if (p.runStatus() == PayrollStatus.POSTED) {
                    payrollReconciliationService.runDetails(period).forEach(d -> tax.add(payrollRow(d, period, p)));
                }
            });
            case SERVICE_VS_PPH23_DPP -> withholdingRows(SelfTestRole.SERVICE_PPH23, TaxType.PPH_23, start, end, book, tax);
            case RENT_VS_PPH42_DPP -> withholdingRows(SelfTestRole.RENT_PPH42, TaxType.PPH_42, start, end, book, tax);
            case FIXED_ASSET_ADDITIONS, FIXED_ASSET_DISPOSALS -> {
                boolean additions = line == SelfTestLine.FIXED_ASSET_ADDITIONS;
                book.addAll(ledgerAssetRows(year, additions).bookRows());
                for (AssetCostMovement a : assetRegisterQueryService.costMovementByAsset(year)) {
                    BigDecimal amount = additions ? a.movement().additions() : a.movement().disposals();
                    if (amount.signum() != 0) {
                        tax.add(new TraceRow(SOURCE_ASSET, a.assetId(), null, null,
                                additions ? a.purchaseDate() : a.disposalDate(), null, a.assetCode(), a.assetName(), amount));
                    }
                }
            }
            case CAPITAL_MOVEMENT -> journalEntryRepository.findPostedLinesByAccountTypeExcludingClosing(AccountType.EQUITY, start, end)
                    .forEach(j -> book.add(journalRow(j, debitMinusCredit(j).negate())));
            case NON_OPERATING_ASSET_MOVEMENT -> roleLines(SelfTestRole.NON_OPERATING_ASSET, start, end)
                    .forEach(j -> book.add(journalRow(j, debitMinusCredit(j))));
            case UNITEMISED_OTHER_EXPENSE -> {
                Map<UUID, Boolean> unitemisedTransactions = new HashMap<>();
                for (JournalEntry j : roleLines(SelfTestRole.OTHER_EXPENSE, start, end)) {
                    TraceRow row = journalRow(j, debitMinusCredit(j));
                    book.add(row);
                    boolean unitemised = j.getDebitAmount().compareTo(unitemisedThreshold) >= 0
                            && unitemisedTransactions.computeIfAbsent(j.getTransaction().getId(), id -> unitemised(j.getTransaction()));
                    if (!unitemised) {
                        tax.add(row);
                    }
                }
            }
        }
        BigDecimal taxFigure = line.hasTaxFigure() ? taxFigure(line, year, month, tax) : null;
        return new Trace(year, line, month, sum(book), taxFigure, book, tax);
    }

    /** Book-figure rows of the unitemised line that are reported as unitemised. */
    public List<TraceRow> unitemisedRows(int year) {
        Trace trace = trace(year, SelfTestLine.UNITEMISED_OTHER_EXPENSE, 0);
        List<TraceRow> itemised = trace.taxRows();
        return trace.bookRows().stream().filter(r -> !itemised.contains(r)).toList();
    }

    // ==================== explanations ====================

    /** Saves the explanation of one row. Blank text removes it. Returns the stored explanation, or null after removal. */
    @Transactional
    public SelfTestExplanation saveExplanation(int year, SelfTestLine line, int month, String text, String username) {
        validateMonth(line, month);
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Pengguna wajib diisi");
        }
        Optional<SelfTestExplanation> existing = explanationRepository.findByReportYearAndLineAndPeriodMonth(year, line, month);
        if (text == null || text.isBlank()) {
            existing.ifPresent(explanationRepository::delete);
            return null;
        }
        SelfTestExplanation explanation = existing.orElseGet(() -> {
            SelfTestExplanation e = new SelfTestExplanation();
            e.setReportYear(year);
            e.setLine(line);
            e.setPeriodMonth(month);
            return e;
        });
        explanation.setExplanation(text.strip());
        explanation.setUpdatedBy(username);
        return explanationRepository.save(explanation);
    }

    // ==================== helpers ====================

    private Line line(SelfTestLine line, int month, BigDecimal book, BigDecimal tax, String message,
                      Map<String, SelfTestExplanation> explanations) {
        BigDecimal difference = tax == null ? book : book.subtract(tax);
        LineStatus status;
        if (book.signum() == 0 && (tax == null || tax.signum() == 0)) {
            status = LineStatus.NO_ACTIVITY;
        } else if (tax == null) {
            status = LineStatus.MOVEMENT;
        } else {
            status = difference.signum() == 0 ? LineStatus.MATCHED : LineStatus.DIFFERENCE;
        }
        SelfTestExplanation e = explanations.get(key(line, month));
        return new Line(line, month, line.getTitle(), line.getBookBasis(), line.getTaxBasis(), book, tax, difference,
                status, message, e != null ? e.getExplanation() : null, e != null ? e.getUpdatedBy() : null,
                e != null ? e.getUpdatedAt() : null);
    }

    private Line notConfigured(SelfTestLine line, String message, Map<String, SelfTestExplanation> explanations) {
        SelfTestExplanation e = explanations.get(key(line, 0));
        return new Line(line, 0, line.getTitle(), line.getBookBasis(), line.getTaxBasis(), null, null, null,
                LineStatus.NOT_CONFIGURED, message, e != null ? e.getExplanation() : null,
                e != null ? e.getUpdatedBy() : null, e != null ? e.getUpdatedAt() : null);
    }

    private Optional<String> missingRole(SelfTestLine line) {
        SelfTestRole role = switch (line) {
            case SERVICE_VS_PPH23_DPP -> SelfTestRole.SERVICE_PPH23;
            case RENT_VS_PPH42_DPP -> SelfTestRole.RENT_PPH42;
            case NON_OPERATING_ASSET_MOVEMENT -> SelfTestRole.NON_OPERATING_ASSET;
            case UNITEMISED_OTHER_EXPENSE -> SelfTestRole.OTHER_EXPENSE;
            default -> null;
        };
        if (role == null || !accountRepository.findBySelfTestRoleOrderByAccountCodeAsc(role).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("Belum ada akun dengan peran uji mandiri \"" + role.getIndonesianName()
                + "\". Tetapkan perannya di Bagan Akun.");
    }

    private BigDecimal taxFigure(SelfTestLine line, int year, int month, List<TraceRow> taxRows) {
        if (line == SelfTestLine.FIXED_ASSET_ADDITIONS) {
            return assetRegisterQueryService.costMovement(year).total().additions();
        }
        if (line == SelfTestLine.FIXED_ASSET_DISPOSALS) {
            return assetRegisterQueryService.costMovement(year).total().disposals();
        }
        if (line == SelfTestLine.SALARY_VS_PPH21_DPP) {
            BigDecimal total = BigDecimal.ZERO;
            for (int m : months(month)) {
                total = total.add(postedTaxableIncome(payrollReconciliationService.reconcile(YearMonth.of(year, m))));
            }
            return total;
        }
        return sum(taxRows);
    }

    private static BigDecimal postedTaxableIncome(PeriodReconciliation p) {
        return p.runStatus() == PayrollStatus.POSTED ? p.runTaxableIncome() : BigDecimal.ZERO;
    }

    private Trace ledgerAssetRows(int year, boolean debits) {
        Map<UUID, ChartOfAccount> accounts = assetRegisterQueryService.costAccounts();
        List<TraceRow> rows = new ArrayList<>();
        if (!accounts.isEmpty()) {
            for (JournalEntry j : journalEntryRepository.findPostedLinesByAccountsExcludingClosing(accounts.keySet(),
                    LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))) {
                BigDecimal amount = debits ? j.getDebitAmount() : j.getCreditAmount();
                if (amount.signum() != 0) {
                    rows.add(journalRow(j, amount));
                }
            }
        }
        return new Trace(year, debits ? SelfTestLine.FIXED_ASSET_ADDITIONS : SelfTestLine.FIXED_ASSET_DISPOSALS, 0,
                sum(rows), null, rows, List.of());
    }

    private void withholdingRows(SelfTestRole role, TaxType taxType, LocalDate start, LocalDate end,
                                 List<TraceRow> book, List<TraceRow> tax) {
        roleLines(role, start, end).forEach(j -> book.add(journalRow(j, debitMinusCredit(j))));
        for (TaxTransactionDetail d : withholdingQueryService.findWithheldDetails(taxType, start, end)) {
            Transaction t = d.getTransaction();
            tax.add(new TraceRow(SOURCE_BUPOT, d.getId(), t.getId(), t.getTransactionNumber(), t.getTransactionDate(), null,
                    d.getBupotNumber(), d.getCounterpartyName(), d.getGrossAmount() == null ? BigDecimal.ZERO : d.getGrossAmount()));
        }
    }

    private List<JournalEntry> roleLines(SelfTestRole role, LocalDate start, LocalDate end) {
        List<UUID> ids = accountRepository.findBySelfTestRoleOrderByAccountCodeAsc(role).stream()
                .map(ChartOfAccount::getId).toList();
        return journalEntryRepository.findPostedLinesByAccountsExcludingClosing(ids, start, end);
    }

    private boolean unitemised(Transaction transaction) {
        if (transaction.getDescription() == null || transaction.getDescription().isBlank()) {
            return true;
        }
        return evidenceQueryService.evidenceFor(transaction.getId()).evidence().isEmpty();
    }

    private static TraceRow journalRow(JournalEntry j, BigDecimal amount) {
        Transaction t = j.getTransaction();
        return new TraceRow(SOURCE_JOURNAL_LINE, j.getId(), t.getId(), t.getTransactionNumber(), t.getTransactionDate(),
                j.getAccount().getAccountCode(), j.getJournalNumber(), t.getDescription(), amount);
    }

    private static TraceRow revenueRow(TransactionAmount r) {
        return new TraceRow(SOURCE_REVENUE_TRANSACTION, r.transactionId(), r.transactionId(), r.transactionNumber(),
                r.transactionDate(), null, r.transactionNumber(), null, r.amount());
    }

    private static TraceRow fakturRow(FakturLink f) {
        return new TraceRow(SOURCE_FAKTUR, f.detailId(), f.transactionId(), f.transactionNumber(), f.fakturDate(), null,
                f.fakturNumber(), null, f.dpp() == null ? BigDecimal.ZERO : f.dpp());
    }

    private static TraceRow payrollRow(PayrollDetail d, YearMonth period, PeriodReconciliation p) {
        return new TraceRow(SOURCE_PAYROLL_DETAIL, d.getId(), null, null, period.atEndOfMonth(), null,
                d.getEmployeeId(), d.getEmployeeName() + " (payroll " + p.period() + ")", d.getTaxableGross());
    }

    private static BigDecimal debitMinusCredit(JournalEntry j) {
        return j.getDebitAmount().subtract(j.getCreditAmount());
    }

    private static void validateMonth(SelfTestLine line, int month) {
        if (month < 0 || month > 12 || (!line.isPerMasa() && month != 0)) {
            throw new IllegalArgumentException("Bulan " + month + " tidak berlaku untuk baris " + line
                    + (line.isPerMasa() ? " (0-12)" : " (hanya 0)"));
        }
    }

    private static int[] months(int month) {
        return month == 0 ? IntStream.rangeClosed(1, 12).toArray() : new int[]{month};
    }

    private static void forMonths(int month, java.util.function.IntConsumer action) {
        for (int m : months(month)) {
            action.accept(m);
        }
    }

    private static String key(SelfTestLine line, int month) {
        return line.name() + "/" + month;
    }

    private static BigDecimal sum(List<TraceRow> rows) {
        return rows.stream().map(TraceRow::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
