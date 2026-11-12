package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.AlertRule;
import com.artivisi.accountingfinance.entity.AssetDisposal;
import com.artivisi.accountingfinance.entity.AssetDisposalStatus;
import com.artivisi.accountingfinance.entity.Bill;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.CompanyBankAccount;
import com.artivisi.accountingfinance.entity.CompanyConfig;
import com.artivisi.accountingfinance.entity.Invoice;
import com.artivisi.accountingfinance.entity.TaxFiling;
import com.artivisi.accountingfinance.entity.TaxTransactionDetail;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.AccountType;
import com.artivisi.accountingfinance.enums.AlertType;
import com.artivisi.accountingfinance.enums.CloseCheckRole;
import com.artivisi.accountingfinance.enums.TaxFilingStatus;
import com.artivisi.accountingfinance.enums.TaxFilingType;
import com.artivisi.accountingfinance.repository.AlertRuleRepository;
import com.artivisi.accountingfinance.repository.AssetDisposalRepository;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.CompanyBankAccountRepository;
import com.artivisi.accountingfinance.repository.InvoiceRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.TaxFilingRepository;
import com.artivisi.accountingfinance.repository.TaxTransactionDetailRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Period close checks (issue #50). Each check is a query method with typed results; {@link #checklist} runs them
 * all for one period. Only posted journals count. Rules are recorded in docs/feature-requests-2026-11.md.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CloseCheckService {

    private static final Set<TaxFilingStatus> REPORTED =
            Set.of(TaxFilingStatus.FILED, TaxFilingStatus.LATE, TaxFilingStatus.CORRECTED);

    private final ChartOfAccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final TaxFilingRepository taxFilingRepository;
    private final AlertRuleRepository alertRuleRepository;
    private final InvoiceRepository invoiceRepository;
    private final TransactionRepository transactionRepository;
    private final TaxTransactionDetailRepository taxDetailRepository;
    private final AssetDisposalRepository disposalRepository;
    private final CompanyBankAccountRepository bankAccountRepository;
    private final CompanyConfigService companyConfigService;

    // ==================== result types ====================

    public enum CheckCode {
        TAX_CREDIT_UNCLEARED("Kredit pajak dikompensasikan setelah SPT Tahunan"),
        CLEARING_NOT_CREDITED("Akun antara tanpa mutasi pengurang"),
        LIABILITY_UNMOVED("Liabilitas tidak bergerak"),
        INVOICE_NOT_IN_RECEIVABLES("Invoice terbit belum tercatat di piutang"),
        CUT_OFF("Jurnal periode berikutnya untuk dokumen periode ini"),
        ASSET_PROCEEDS_PERSONAL("Hasil pelepasan aset ke rekening bukan perusahaan");

        private final String title;

        CheckCode(String title) {
            this.title = title;
        }

        public String getTitle() {
            return title;
        }
    }

    public enum CheckStatus { PASS, WARNING, NOT_APPLICABLE, ERROR }

    public record Finding(String reference, LocalDate date, String description, BigDecimal amount, UUID transactionId) {}

    public record CheckResult(CheckCode code, String title, CheckStatus status, String message, List<Finding> findings) {}

    public record CloseChecklist(LocalDate from, LocalDate to, List<CheckResult> checks) {
        public long warningCount() {
            return checks.stream().filter(c -> c.status() == CheckStatus.WARNING || c.status() == CheckStatus.ERROR).count();
        }
    }

    public record TaxCreditBalance(UUID accountId, String accountCode, String accountName,
                                   BigDecimal balanceAtYearEnd, BigDecimal clearedAfterYearEnd, BigDecimal uncleared) {}

    /** filedDate null: no reported PPh Badan return for returnYear on or before asOf; balances are then empty. */
    public record TaxCreditCheck(int returnYear, LocalDate asOf, LocalDate filedDate, List<TaxCreditBalance> uncleared) {}

    public record StaleClearing(UUID accountId, String accountCode, String accountName, BigDecimal balance,
                                LocalDate lastMovement) {}

    public record UnmovedLiability(UUID accountId, String accountCode, String accountName, BigDecimal balance,
                                   LocalDate lastMovement) {}

    public record InvoiceOutsideReceivables(UUID invoiceId, String invoiceNumber, LocalDate invoiceDate, String clientName,
                                            BigDecimal invoiceTotal, BigDecimal postedToReceivable) {}

    public enum CutOffSource { INVOICE, BILL, FAKTUR }

    public record CutOffFinding(UUID transactionId, String transactionNumber, LocalDate transactionDate,
                                  CutOffSource source, String documentNumber, LocalDate documentDate, BigDecimal amount) {}

    public enum ProceedsIssue { SHAREHOLDER_ACCOUNT, NOT_COMPANY_BANK_ACCOUNT }

    public record ProceedsWarning(UUID disposalId, String recordNumber, LocalDate disposalDate, String assetName,
                                  BigDecimal proceeds, String accountCode, String accountName, ProceedsIssue issue) {
        public String issueText() {
            return issueLabel(issue);
        }
    }

    // ==================== checklist ====================

    public CloseChecklist checklistForMonth(YearMonth month) {
        return checklist(month.atDay(1), month.atEndOfMonth());
    }

    public CloseChecklist checklistForYear(int year) {
        return checklist(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }

    /**
     * Every check for the period [from, to]. The tax credit check covers the annual return of the year before
     * {@code from}; the liability check uses the LIABILITY_UNMOVED alert threshold as N months.
     */
    public CloseChecklist checklist(LocalDate from, LocalDate to) {
        requirePeriod(from, to);
        List<CheckResult> checks = new ArrayList<>();
        checks.add(taxCreditResult(from, to));
        checks.add(clearingResult(from, to));
        checks.add(liabilityResult(to));
        checks.add(invoiceResult(from, to));
        checks.add(cutOffResult(from, to));
        checks.add(proceedsResult(from, to));
        return new CloseChecklist(from, to, checks);
    }

    // ==================== tax credits ====================

    /**
     * TAX_CREDIT accounts must be offset once the PPh Badan return of {@code returnYear} is reported: the balance at
     * the end of that year less the credits posted after it up to {@code asOf} must be zero. The earliest filed
     * date among the reported returns of that year (original or correction) counts.
     */
    public TaxCreditCheck taxCredits(int returnYear, LocalDate asOf) {
        Objects.requireNonNull(asOf, "asOf wajib diisi");
        if (returnYear >= asOf.getYear()) {
            throw new IllegalArgumentException("Tahun SPT " + returnYear + " harus sebelum tahun asOf " + asOf.getYear());
        }
        Optional<LocalDate> filed = taxFilingRepository.findByFilters(TaxFilingType.PPH_BADAN, returnYear).stream()
                .filter(f -> REPORTED.contains(f.getStatus()) && f.getFiledDate() != null)
                .map(TaxFiling::getFiledDate)
                .filter(d -> !d.isAfter(asOf))
                .min(Comparator.naturalOrder());
        if (filed.isEmpty()) {
            return new TaxCreditCheck(returnYear, asOf, null, List.of());
        }
        LocalDate nextYear = LocalDate.of(returnYear + 1, 1, 1);
        List<TaxCreditBalance> uncleared = new ArrayList<>();
        for (ChartOfAccount a : accountRepository.findByCloseCheckRoleOrderByAccountCodeAsc(CloseCheckRole.TAX_CREDIT)) {
            BigDecimal balance = journalEntryRepository.sumDebitBeforeDate(a.getId(), nextYear)
                    .subtract(journalEntryRepository.sumCreditBeforeDate(a.getId(), nextYear));
            if (balance.signum() <= 0) {
                continue;
            }
            BigDecimal cleared = asOf.isBefore(nextYear) ? BigDecimal.ZERO
                    : journalEntryRepository.sumCreditByAccountAndDateRange(a.getId(), nextYear, asOf);
            BigDecimal remaining = balance.subtract(cleared);
            if (remaining.signum() > 0) {
                uncleared.add(new TaxCreditBalance(a.getId(), a.getAccountCode(), a.getAccountName(), balance, cleared, remaining));
            }
        }
        return new TaxCreditCheck(returnYear, asOf, filed.get(), uncleared);
    }

    private CheckResult taxCreditResult(LocalDate from, LocalDate to) {
        int returnYear = from.getYear() - 1;
        CheckCode code = CheckCode.TAX_CREDIT_UNCLEARED;
        if (accountRepository.findByCloseCheckRoleOrderByAccountCodeAsc(CloseCheckRole.TAX_CREDIT).isEmpty()) {
            return new CheckResult(code, code.getTitle(), CheckStatus.ERROR,
                    "Belum ada akun dengan peran Kredit pajak. Tandai akun kredit pajak di Bagan Akun.", List.of());
        }
        TaxCreditCheck check = taxCredits(returnYear, to);
        if (check.filedDate() == null) {
            return new CheckResult(code, code.getTitle(), CheckStatus.NOT_APPLICABLE,
                    "SPT Tahunan PPh Badan " + returnYear + " belum tercatat dilaporkan per " + to + ".", List.of());
        }
        List<Finding> findings = check.uncleared().stream()
                .map(b -> new Finding(b.accountCode() + " " + b.accountName(), LocalDate.of(returnYear, 12, 31),
                        "Saldo akhir " + returnYear + " " + b.balanceAtYearEnd().toPlainString()
                                + ", dikompensasikan " + b.clearedAfterYearEnd().toPlainString(),
                        b.uncleared(), null))
                .toList();
        String message = "SPT Tahunan PPh Badan " + returnYear + " dilaporkan " + check.filedDate() + ".";
        return result(code, findings, message);
    }

    // ==================== clearing accounts ====================

    /**
     * CLEARING accounts with a balance at {@code to} and no posted movement on the side that reduces that balance
     * (credit for a debit balance, debit for a credit balance) within [from, to].
     */
    public List<StaleClearing> staleClearingBalances(LocalDate from, LocalDate to) {
        requirePeriod(from, to);
        LocalDate after = to.plusDays(1);
        List<StaleClearing> result = new ArrayList<>();
        for (ChartOfAccount a : accountRepository.findByCloseCheckRoleOrderByAccountCodeAsc(CloseCheckRole.CLEARING)) {
            BigDecimal debitMinusCredit = journalEntryRepository.sumDebitBeforeDate(a.getId(), after)
                    .subtract(journalEntryRepository.sumCreditBeforeDate(a.getId(), after));
            if (debitMinusCredit.signum() == 0) {
                continue;
            }
            BigDecimal reducing = debitMinusCredit.signum() > 0
                    ? journalEntryRepository.sumCreditByAccountAndDateRange(a.getId(), from, to)
                    : journalEntryRepository.sumDebitByAccountAndDateRange(a.getId(), from, to);
            if (reducing.signum() == 0) {
                LocalDate last = journalEntryRepository.findLastPostedDateOnAccount(a.getId(), to)
                        .orElseThrow(() -> new IllegalStateException("Saldo tanpa jurnal pada akun " + a.getAccountCode()));
                result.add(new StaleClearing(a.getId(), a.getAccountCode(), a.getAccountName(), debitMinusCredit, last));
            }
        }
        return result;
    }

    private CheckResult clearingResult(LocalDate from, LocalDate to) {
        CheckCode code = CheckCode.CLEARING_NOT_CREDITED;
        if (accountRepository.findByCloseCheckRoleOrderByAccountCodeAsc(CloseCheckRole.CLEARING).isEmpty()) {
            return new CheckResult(code, code.getTitle(), CheckStatus.NOT_APPLICABLE,
                    "Tidak ada akun dengan peran Akun antara.", List.of());
        }
        List<Finding> findings = staleClearingBalances(from, to).stream()
                .map(c -> new Finding(c.accountCode() + " " + c.accountName(), c.lastMovement(),
                        "Saldo per " + to + " tanpa mutasi pengurang dalam periode", c.balance().abs(), null))
                .toList();
        return result(code, findings, null);
    }

    // ==================== unmoved liabilities ====================

    /** N months for the liability check: the LIABILITY_UNMOVED alert rule threshold, a whole number of at least 1. */
    public int configuredUnmovedMonths() {
        AlertRule rule = alertRuleRepository.findByAlertType(AlertType.LIABILITY_UNMOVED)
                .orElseThrow(() -> new IllegalStateException(
                        "Aturan peringatan LIABILITY_UNMOVED tidak ditemukan; jumlah bulan liabilitas tidak bergerak belum dikonfigurasi"));
        BigDecimal threshold = rule.getThreshold();
        if (threshold.signum() <= 0 || threshold.stripTrailingZeros().scale() > 0) {
            throw new IllegalStateException("Ambang LIABILITY_UNMOVED harus bilangan bulat bulan minimal 1, tercatat "
                    + threshold.toPlainString());
        }
        return threshold.intValueExact();
    }

    /** LIABILITY accounts with a non-zero balance at {@code asOf} whose last posted line is on or before asOf minus N months. */
    public List<UnmovedLiability> unmovedLiabilities(LocalDate asOf, int months) {
        Objects.requireNonNull(asOf, "asOf wajib diisi");
        if (months < 1) {
            throw new IllegalArgumentException("Jumlah bulan minimal 1");
        }
        LocalDate cutoff = asOf.minusMonths(months);
        Map<UUID, Object[]> totals = new HashMap<>();
        for (Object[] row : journalEntryRepository.findPostedTotalsByAccountTypeUpTo(AccountType.LIABILITY, asOf)) {
            BigDecimal balance = ((BigDecimal) row[2]).subtract((BigDecimal) row[1]);
            LocalDate last = (LocalDate) row[3];
            if (balance.signum() != 0 && !last.isAfter(cutoff)) {
                totals.put((UUID) row[0], new Object[]{balance, last});
            }
        }
        if (totals.isEmpty()) {
            return List.of();
        }
        return accountRepository.findAllById(totals.keySet()).stream()
                .sorted(Comparator.comparing(ChartOfAccount::getAccountCode))
                .map(a -> new UnmovedLiability(a.getId(), a.getAccountCode(), a.getAccountName(),
                        (BigDecimal) totals.get(a.getId())[0], (LocalDate) totals.get(a.getId())[1]))
                .toList();
    }

    private CheckResult liabilityResult(LocalDate to) {
        CheckCode code = CheckCode.LIABILITY_UNMOVED;
        int months;
        try {
            months = configuredUnmovedMonths();
        } catch (IllegalStateException e) {
            return new CheckResult(code, code.getTitle(), CheckStatus.ERROR, e.getMessage(), List.of());
        }
        List<Finding> findings = unmovedLiabilities(to, months).stream()
                .map(l -> new Finding(l.accountCode() + " " + l.accountName(), l.lastMovement(),
                        "Mutasi terakhir " + l.lastMovement(), l.balance(), null))
                .toList();
        return result(code, findings, "Batas " + months + " bulan tanpa mutasi per " + to + ".");
    }

    // ==================== invoices outside receivables ====================

    /**
     * Issued invoices (sent, partial, paid, overdue) dated in the period whose posted journals with the invoice as
     * source document debit the configured receivable account by less than the invoice total, as of {@code to}.
     * A cash-basis invoice (revenue booked only on payment) shows here.
     */
    public List<InvoiceOutsideReceivables> invoicesOutsideReceivables(LocalDate from, LocalDate to) {
        requirePeriod(from, to);
        CompanyConfig config = companyConfigService.getConfig();
        ChartOfAccount receivable = config.getReceivableAccount();
        if (receivable == null) {
            throw new IllegalStateException("Akun Piutang Usaha belum dikonfigurasi di Pengaturan Perusahaan");
        }
        List<Invoice> invoices = invoiceRepository.findIssuedInPeriod(from, to);
        if (invoices.isEmpty()) {
            return List.of();
        }
        Map<UUID, BigDecimal> posted = journalEntryRepository
                .sumPostedInvoiceDebits(invoices.stream().map(Invoice::getId).toList(), receivable.getId(), to).stream()
                .collect(Collectors.toMap(r -> (UUID) r[0], r -> (BigDecimal) r[1]));
        List<InvoiceOutsideReceivables> result = new ArrayList<>();
        for (Invoice invoice : invoices) {
            BigDecimal booked = posted.getOrDefault(invoice.getId(), BigDecimal.ZERO);
            if (booked.compareTo(invoice.getTotalAmount()) < 0) {
                result.add(new InvoiceOutsideReceivables(invoice.getId(), invoice.getInvoiceNumber(), invoice.getInvoiceDate(),
                        invoice.getClient().getName(), invoice.getTotalAmount(), booked));
            }
        }
        return result;
    }

    private CheckResult invoiceResult(LocalDate from, LocalDate to) {
        CheckCode code = CheckCode.INVOICE_NOT_IN_RECEIVABLES;
        List<InvoiceOutsideReceivables> found;
        try {
            found = invoicesOutsideReceivables(from, to);
        } catch (IllegalStateException e) {
            return new CheckResult(code, code.getTitle(), CheckStatus.ERROR, e.getMessage(), List.of());
        }
        List<Finding> findings = found.stream()
                .map(i -> new Finding(i.invoiceNumber(), i.invoiceDate(),
                        i.clientName() + ": total " + i.invoiceTotal().toPlainString() + ", tercatat di piutang "
                                + i.postedToReceivable().toPlainString(),
                        i.invoiceTotal().subtract(i.postedToReceivable()), null))
                .toList();
        return result(code, findings, null);
    }

    // ==================== cut-off ====================

    /**
     * Posted journals dated after {@code to} for documents dated in [from, to]: invoice journals by invoice date,
     * bill journals by bill date, tax details by faktur date.
     */
    public List<CutOffFinding> cutOffExceptions(LocalDate from, LocalDate to) {
        requirePeriod(from, to);
        List<CutOffFinding> result = new ArrayList<>();
        for (Object[] row : transactionRepository.findInvoiceJournalsAfterPeriod(from, to)) {
            Transaction t = (Transaction) row[0];
            Invoice i = (Invoice) row[1];
            result.add(new CutOffFinding(t.getId(), t.getTransactionNumber(), t.getTransactionDate(), CutOffSource.INVOICE,
                    i.getInvoiceNumber(), i.getInvoiceDate(), t.getAmount()));
        }
        for (Object[] row : transactionRepository.findBillJournalsAfterPeriod(from, to)) {
            Transaction t = (Transaction) row[0];
            Bill b = (Bill) row[1];
            result.add(new CutOffFinding(t.getId(), t.getTransactionNumber(), t.getTransactionDate(), CutOffSource.BILL,
                    b.getBillNumber(), b.getBillDate(), t.getAmount()));
        }
        for (TaxTransactionDetail d : taxDetailRepository.findFakturInPeriodPostedAfter(from, to)) {
            Transaction t = d.getTransaction();
            result.add(new CutOffFinding(t.getId(), t.getTransactionNumber(), t.getTransactionDate(), CutOffSource.FAKTUR,
                    d.getFakturNumber(), d.getFakturDate(), t.getAmount()));
        }
        result.sort(Comparator.comparing(CutOffFinding::transactionDate).thenComparing(CutOffFinding::transactionNumber));
        return result;
    }

    private CheckResult cutOffResult(LocalDate from, LocalDate to) {
        CheckCode code = CheckCode.CUT_OFF;
        List<Finding> findings = cutOffExceptions(from, to).stream()
                .map(c -> new Finding(c.transactionNumber(), c.transactionDate(),
                        sourceLabel(c.source()) + " " + c.documentNumber() + " tanggal " + c.documentDate(),
                        c.amount(), c.transactionId()))
                .toList();
        return result(code, findings, null);
    }

    private static String sourceLabel(CutOffSource source) {
        return switch (source) {
            case INVOICE -> "Invoice";
            case BILL -> "Tagihan vendor";
            case FAKTUR -> "Faktur pajak";
        };
    }

    // ==================== asset proceeds ====================

    /**
     * Disposals in the period (not cancelled) with proceeds booked to a SHAREHOLDER_CURRENT account, or to an
     * account that is not the GL account of an active company bank account.
     */
    public List<ProceedsWarning> assetProceedsWarnings(LocalDate from, LocalDate to) {
        requirePeriod(from, to);
        Set<UUID> companyBankGl = companyBankGlAccounts();
        return disposalRepository.findByDateRange(from, to, null).stream()
                .map(d -> proceedsWarning(d, companyBankGl))
                .flatMap(Optional::stream)
                .toList();
    }

    /** The warning for one disposal, empty when its proceeds go to a company bank account or there are none. */
    public Optional<ProceedsWarning> proceedsWarning(AssetDisposal disposal) {
        return proceedsWarning(disposal, companyBankGlAccounts());
    }

    private Optional<ProceedsWarning> proceedsWarning(AssetDisposal d, Set<UUID> companyBankGl) {
        if (d.getStatus() == AssetDisposalStatus.CANCELLED || d.getProceeds().signum() <= 0) {
            return Optional.empty();
        }
        ChartOfAccount account = d.getProceedsAccount();
        if (account == null) {
            throw new IllegalStateException("Pelepasan " + d.getRecordNumber() + " memiliki hasil tanpa akun penerimaan");
        }
        ProceedsIssue issue;
        if (account.getCloseCheckRole() == CloseCheckRole.SHAREHOLDER_CURRENT) {
            issue = ProceedsIssue.SHAREHOLDER_ACCOUNT;
        } else if (!companyBankGl.contains(account.getId())) {
            issue = ProceedsIssue.NOT_COMPANY_BANK_ACCOUNT;
        } else {
            return Optional.empty();
        }
        return Optional.of(new ProceedsWarning(d.getId(), d.getRecordNumber(), d.getDisposalDate(),
                d.getFixedAsset().getName(), d.getProceeds(), account.getAccountCode(), account.getAccountName(), issue));
    }

    private Set<UUID> companyBankGlAccounts() {
        return bankAccountRepository.findByActiveTrueOrderByBankNameAsc().stream()
                .map(CompanyBankAccount::getGlAccount)
                .filter(Objects::nonNull)
                .map(ChartOfAccount::getId)
                .collect(Collectors.toSet());
    }

    private CheckResult proceedsResult(LocalDate from, LocalDate to) {
        CheckCode code = CheckCode.ASSET_PROCEEDS_PERSONAL;
        List<Finding> findings = assetProceedsWarnings(from, to).stream()
                .map(w -> new Finding(w.recordNumber(), w.disposalDate(),
                        w.assetName() + " ke " + w.accountCode() + " " + w.accountName() + ": " + w.issueText(),
                        w.proceeds(), null))
                .toList();
        return result(code, findings, null);
    }

    public static String issueLabel(ProceedsIssue issue) {
        return switch (issue) {
            case SHAREHOLDER_ACCOUNT -> "rekening koran pemegang saham/direksi";
            case NOT_COMPANY_BANK_ACCOUNT -> "bukan rekening bank perusahaan yang terdaftar";
        };
    }

    // ==================== helpers ====================

    private static CheckResult result(CheckCode code, List<Finding> findings, String message) {
        return new CheckResult(code, code.getTitle(), findings.isEmpty() ? CheckStatus.PASS : CheckStatus.WARNING,
                message, findings);
    }

    private static void requirePeriod(LocalDate from, LocalDate to) {
        Objects.requireNonNull(from, "from wajib diisi");
        Objects.requireNonNull(to, "to wajib diisi");
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to sebelum from");
        }
    }
}
