package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.Invoice;
import com.artivisi.accountingfinance.entity.InvoicePayment;
import com.artivisi.accountingfinance.entity.TaxFiling;
import com.artivisi.accountingfinance.entity.TaxTransactionDetail;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.InvoiceStatus;
import com.artivisi.accountingfinance.enums.PaymentMethod;
import com.artivisi.accountingfinance.enums.SalesTaxExceptionType;
import com.artivisi.accountingfinance.enums.SalesTaxReconLine;
import com.artivisi.accountingfinance.enums.TaxFilingStatus;
import com.artivisi.accountingfinance.enums.TaxFilingType;
import com.artivisi.accountingfinance.enums.TaxType;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.repository.InvoiceRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository.TransactionAmount;
import com.artivisi.accountingfinance.repository.TaxFilingRepository;
import com.artivisi.accountingfinance.repository.TaxTransactionDetailRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Read API over the chain from invoice to faktur, receipt and bupot. Other features (self-test, workspace) read it
 * here rather than through repositories.
 *
 * <p>Source of truth: faktur and bupot numbers, dates and amounts live on {@link TaxTransactionDetail} only. A detail
 * belongs to an invoice through its transaction: {@code transactions.source_document_type = 'INVOICE'} with
 * {@code source_document_id}, or {@code invoices.id_transaction}. A detail with no number is not a faktur or bupot.
 *
 * <p>DPP of SPT Masa PPN is the sum over numbered PPN Keluaran details on POSTED transactions whose faktur date falls
 * in the masa. The e-Faktur export selects by transaction date and includes unnumbered rows;
 * {@link Reconciliation#exportDifference()} shows the gap between the two.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesTaxChainService {

    private static final String INVOICE_SOURCE = "INVOICE";
    private static final String TRANSACTION_CODE_DPP_NILAI_LAIN = "04";

    private final InvoiceRepository invoiceRepository;
    private final TaxTransactionDetailRepository taxDetailRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final TransactionRepository transactionRepository;
    private final TaxFilingRepository taxFilingRepository;
    private final TaxReportDetailService taxReportDetailService;
    private final CompanyConfigService companyConfigService;
    private final WithholdingAccountResolver accountResolver;

    public record FakturLink(UUID detailId, UUID transactionId, String transactionNumber, TransactionStatus transactionStatus,
                             String fakturNumber, LocalDate fakturDate, String transactionCode, BigDecimal dpp, BigDecimal ppn) {}

    public record BupotLink(UUID detailId, UUID transactionId, String transactionNumber, TransactionStatus transactionStatus,
                            UUID paymentId, String bupotNumber, String taxObjectCode, BigDecimal grossAmount,
                            BigDecimal taxRate, BigDecimal taxAmount) {}

    /** amount settles the receivable and includes pph23Withheld; bupotOutstanding is withheld with no bupot yet. */
    public record ReceiptLink(UUID paymentId, LocalDate paymentDate, BigDecimal amount, BigDecimal cashAmount,
                              PaymentMethod paymentMethod, String referenceNumber, BigDecimal pph23Withheld,
                              BigDecimal bupotOutstanding) {}

    public record ChainTransaction(UUID transactionId, String transactionNumber, LocalDate transactionDate,
                                   TransactionStatus status, BigDecimal postedRevenue) {}

    public record InvoiceChain(UUID invoiceId, String invoiceNumber, LocalDate invoiceDate, String clientName,
                               InvoiceStatus status, BigDecimal amount, BigDecimal taxAmount, BigDecimal paidAmount,
                               BigDecimal bookedRevenue, List<ChainTransaction> transactions, List<FakturLink> fakturs,
                               List<ReceiptLink> receipts, List<BupotLink> bupots, List<ChainIssue> exceptions) {}

    /** invoiceId is null for a transaction outside any invoice; transactionId is null for an invoice-level exception. */
    public record ChainIssue(SalesTaxExceptionType type, LocalDate date, UUID invoiceId, String invoiceNumber,
                                 UUID transactionId, String transactionNumber, String reference, BigDecimal amount) {}

    public record ReconItem(UUID invoiceId, String invoiceNumber, UUID transactionId, String transactionNumber,
                            String fakturNumbers, BigDecimal revenue, BigDecimal dpp, BigDecimal amount) {}

    public record ReconLine(SalesTaxReconLine type, BigDecimal amount, List<ReconItem> items) {}

    /**
     * Effects of PPN and PPh 23 on what the customer owes. invoiceValue = DPP + PPN per faktur;
     * netOfPph23 = invoiceValue less PPh 23 per bupot. Ledger differences are per-document minus ledger.
     */
    public record TaxEffects(BigDecimal ppnPerFaktur, BigDecimal ppnPerLedger, BigDecimal ppnDifference,
                             BigDecimal invoiceValue, BigDecimal pph23PerReceipts, BigDecimal pph23PerBupot,
                             BigDecimal pph23PerLedger, BigDecimal pph23Difference, BigDecimal netOfPph23) {}

    public record FilingRow(int pembetulanNo, TaxFilingStatus status, LocalDate filedDate, String bpeNumber) {}

    /** bookedRevenue plus every line equals sptDpp; unreconciled reports any gap and is zero unless the code is wrong. */
    public record Reconciliation(int year, int month, BigDecimal bookedRevenue, List<ReconLine> lines, BigDecimal sptDpp,
                                 BigDecimal unreconciled, TaxEffects taxEffects, BigDecimal exportedDpp,
                                 BigDecimal exportDifference, List<FilingRow> filings) {}

    public InvoiceChain getChain(UUID invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new EntityNotFoundException("Invoice tidak ditemukan: " + invoiceId));
        ChainData data = load();
        List<TaxTransactionDetail> fakturs = data.issuedFakturs(invoiceId);
        List<TaxTransactionDetail> bupots = data.issuedBupots(invoiceId);
        Map<UUID, BigDecimal> outstanding = outstandingBupot(invoice, bupots);

        List<ReceiptLink> receipts = invoice.getPayments().stream()
                .map(p -> new ReceiptLink(p.getId(), p.getPaymentDate(), p.getAmount(), p.getCashAmount(), p.getPaymentMethod(),
                        p.getReferenceNumber(), p.getPph23Withheld(), outstanding.get(p.getId())))
                .toList();
        List<ChainIssue> exceptions = invoiceExceptions(data).stream()
                .filter(e -> invoiceId.equals(e.invoiceId()))
                .toList();

        return new InvoiceChain(invoice.getId(), invoice.getInvoiceNumber(), invoice.getInvoiceDate(), invoice.getClient().getName(),
                invoice.getStatus(), invoice.getAmount(), invoice.getTaxAmount(), invoice.getPaidAmount(),
                data.postedRevenue(invoiceId), chainTransactions(invoice, data),
                fakturs.stream().map(this::fakturLink).toList(), receipts, bupots.stream().map(this::bupotLink).toList(), exceptions);
    }

    /** Open exceptions dated in the range, oldest first. from may be null for no lower bound. */
    public List<ChainIssue> findExceptions(LocalDate from, LocalDate to) {
        if (to == null) {
            throw new IllegalArgumentException("Tanggal akhir wajib diisi");
        }
        ChainData data = load();
        List<ChainIssue> all = new ArrayList<>(invoiceExceptions(data));
        all.addAll(creditsWithoutBupot(data));
        return all.stream()
                .filter(e -> !e.date().isAfter(to) && (from == null || !e.date().isBefore(from)))
                .sorted(Comparator.comparing(ChainIssue::date).thenComparing(ChainIssue::type))
                .toList();
    }

    public List<ChainIssue> findExceptions(LocalDate from, LocalDate to, SalesTaxExceptionType type) {
        return findExceptions(from, to).stream().filter(e -> e.type() == type).toList();
    }

    public Reconciliation getReconciliation(int year, int month) {
        YearMonth masa = YearMonth.of(year, month);
        LocalDate start = masa.atDay(1);
        LocalDate end = masa.atEndOfMonth();
        ChainData data = load();

        Map<UUID, Unit> units = new LinkedHashMap<>();
        BigDecimal bookedRevenue = BigDecimal.ZERO;
        for (TransactionAmount revenue : data.revenue) {
            if (!revenue.transactionDate().isBefore(start) && !revenue.transactionDate().isAfter(end)) {
                bookedRevenue = bookedRevenue.add(revenue.amount());
                units.computeIfAbsent(data.unitKey(revenue), k -> new Unit(k, data.invoiceIdOf(revenue)))
                        .addRevenue(revenue);
            }
        }
        BigDecimal sptDpp = BigDecimal.ZERO;
        BigDecimal sptPpn = BigDecimal.ZERO;
        for (TaxTransactionDetail faktur : data.sptFakturs()) {
            if (!faktur.getFakturDate().isBefore(start) && !faktur.getFakturDate().isAfter(end)) {
                sptDpp = sptDpp.add(nz(faktur.getDpp()));
                sptPpn = sptPpn.add(nz(faktur.getPpn()));
                Transaction trx = faktur.getTransaction();
                units.computeIfAbsent(data.unitKey(trx), k -> new Unit(k, data.invoiceIdOf(trx))).addFaktur(faktur);
            }
        }

        Map<SalesTaxReconLine, List<ReconItem>> items = new EnumMap<>(SalesTaxReconLine.class);
        Arrays.stream(SalesTaxReconLine.values()).forEach(line -> items.put(line, new ArrayList<>()));
        units.values().forEach(unit -> classify(unit, data, start, end, items));

        List<ReconLine> lines = Arrays.stream(SalesTaxReconLine.values())
                .map(line -> new ReconLine(line, sum(items.get(line).stream().map(ReconItem::amount).toList()), items.get(line)))
                .toList();
        BigDecimal explained = bookedRevenue.add(sum(lines.stream().map(ReconLine::amount).toList()));

        BigDecimal exportedDpp = taxReportDetailService.generatePPNDetailReport(start, end).totalDppKeluaran();
        List<FilingRow> filings = taxFilingRepository.findByFilters(TaxFilingType.PPN, year).stream()
                .filter(f -> Objects.equals(f.getPeriodMonth(), month))
                .sorted(Comparator.comparing(TaxFiling::getPembetulanNo))
                .map(f -> new FilingRow(f.getPembetulanNo(), f.getStatus(), f.getFiledDate(), f.getBpeNumber()))
                .toList();

        return new Reconciliation(year, month, bookedRevenue, lines, sptDpp, sptDpp.subtract(explained),
                taxEffects(data, start, end, sptDpp, sptPpn), exportedDpp, exportedDpp.subtract(sptDpp), filings);
    }

    private void classify(Unit unit, ChainData data, LocalDate start, LocalDate end, Map<SalesTaxReconLine, List<ReconItem>> items) {
        BigDecimal revenue = unit.revenue;
        BigDecimal dpp = unit.dpp;
        if (unit.fakturs.isEmpty()) {
            boolean fakturElsewhere = data.sptFakturs().stream()
                    .anyMatch(f -> data.unitKey(f.getTransaction()).equals(unit.key)
                            && (f.getFakturDate().isBefore(start) || f.getFakturDate().isAfter(end)));
            add(items, fakturElsewhere ? SalesTaxReconLine.FAKTUR_IN_OTHER_MASA : SalesTaxReconLine.REVENUE_NO_FAKTUR,
                    unit, data, revenue.negate());
            return;
        }
        if (unit.transactionId == null) {
            boolean revenueElsewhere = data.postedRevenue(unit.key).signum() != 0;
            add(items, revenueElsewhere ? SalesTaxReconLine.REVENUE_IN_OTHER_MASA : SalesTaxReconLine.FAKTUR_NO_REVENUE,
                    unit, data, dpp);
            return;
        }
        BigDecimal difference = dpp.subtract(revenue);
        if (difference.signum() == 0) {
            return;
        }
        if (unit.fakturs.stream().anyMatch(f -> TRANSACTION_CODE_DPP_NILAI_LAIN.equals(f.getTransactionCode()))) {
            add(items, SalesTaxReconLine.DPP_NILAI_LAIN, unit, data, difference);
            return;
        }
        BigDecimal ppn = unit.ppn;
        BigDecimal pph23 = data.pph23OnUnit(unit.key);
        boolean ppnExplains = ppn.signum() != 0 && difference.compareTo(ppn.negate()) == 0;
        boolean pph23Explains = pph23.signum() != 0 && difference.compareTo(pph23) == 0;
        boolean bothExplain = ppn.signum() != 0 && pph23.signum() != 0 && difference.compareTo(pph23.subtract(ppn)) == 0;
        if (ppnExplains || bothExplain) {
            add(items, SalesTaxReconLine.PPN_IN_REVENUE, unit, data, ppn.negate());
        }
        if (pph23Explains || bothExplain) {
            add(items, SalesTaxReconLine.PPH23_NETTED_FROM_REVENUE, unit, data, pph23);
        }
        if (!ppnExplains && !pph23Explains && !bothExplain) {
            add(items, SalesTaxReconLine.OTHER_DIFFERENCE, unit, data, difference);
        }
    }

    private void add(Map<SalesTaxReconLine, List<ReconItem>> items, SalesTaxReconLine line, Unit unit, ChainData data, BigDecimal amount) {
        Invoice invoice = unit.invoiceId == null ? null : data.invoice(unit.invoiceId);
        UUID transactionId = unit.transactionId != null ? unit.transactionId : unit.fakturs.getFirst().getTransaction().getId();
        String transactionNumber = unit.transactionId != null ? unit.transactionNumber
                : unit.fakturs.getFirst().getTransaction().getTransactionNumber();
        String numbers = unit.fakturs.stream().map(TaxTransactionDetail::getFakturNumber).collect(Collectors.joining(", "));
        items.get(line).add(new ReconItem(unit.invoiceId, invoice == null ? null : invoice.getInvoiceNumber(), transactionId,
                transactionNumber, numbers.isEmpty() ? null : numbers, unit.revenue, unit.dpp, amount));
    }

    private TaxEffects taxEffects(ChainData data, LocalDate start, LocalDate end, BigDecimal sptDpp, BigDecimal sptPpn) {
        ChartOfAccount outputTax = companyConfigService.getConfig().getOutputTaxAccount();
        if (outputTax == null) {
            throw new IllegalStateException("Akun PPN Keluaran belum dikonfigurasi di Pengaturan Perusahaan");
        }
        BigDecimal ppnLedger = journalEntryRepository.sumCreditByAccountAndDateRange(outputTax.getId(), start, end)
                .subtract(journalEntryRepository.sumDebitByAccountAndDateRange(outputTax.getId(), start, end));

        ChartOfAccount taxCredit = accountResolver.taxCreditPph23Account();
        BigDecimal pph23Ledger = journalEntryRepository.sumDebitByAccountAndDateRange(taxCredit.getId(), start, end)
                .subtract(journalEntryRepository.sumCreditByAccountAndDateRange(taxCredit.getId(), start, end));

        BigDecimal pph23Receipts = sum(data.issuedInvoices.stream()
                .flatMap(i -> i.getPayments().stream())
                .filter(p -> !p.getPaymentDate().isBefore(start) && !p.getPaymentDate().isAfter(end))
                .map(InvoicePayment::getPph23Withheld)
                .toList());
        BigDecimal pph23Bupot = sum(data.customerBupots().stream()
                .filter(d -> d.getTransaction().getStatus() == TransactionStatus.POSTED)
                .filter(d -> !d.getTransaction().getTransactionDate().isBefore(start) && !d.getTransaction().getTransactionDate().isAfter(end))
                .map(d -> nz(d.getTaxAmount()))
                .toList());

        BigDecimal invoiceValue = sptDpp.add(sptPpn);
        return new TaxEffects(sptPpn, ppnLedger, sptPpn.subtract(ppnLedger), invoiceValue, pph23Receipts, pph23Bupot,
                pph23Ledger, pph23Bupot.subtract(pph23Ledger), invoiceValue.subtract(pph23Bupot));
    }

    /** Exceptions that belong to one invoice, or to a faktur or PPh 23 row. Needs no account configuration. */
    private List<ChainIssue> invoiceExceptions(ChainData data) {
        List<ChainIssue> result = new ArrayList<>();
        for (Invoice invoice : data.issuedInvoices) {
            UUID id = invoice.getId();
            List<TaxTransactionDetail> fakturs = data.issuedFakturs(id);
            if (invoice.getTaxAmount().signum() > 0 && fakturs.isEmpty() && isPaidAtAll(invoice)) {
                result.add(new ChainIssue(SalesTaxExceptionType.PAID_NO_FAKTUR, firstPaidDate(invoice), id,
                        invoice.getInvoiceNumber(), null, null, null, invoice.getTotalAmount()));
            }
            if (fakturs.size() > 1) {
                LocalDate latest = fakturs.stream().map(this::fakturEventDate).max(LocalDate::compareTo).orElseThrow();
                result.add(new ChainIssue(SalesTaxExceptionType.DUPLICATE_FAKTUR, latest, id, invoice.getInvoiceNumber(), null, null,
                        fakturs.stream().map(TaxTransactionDetail::getFakturNumber).sorted().collect(Collectors.joining(", ")),
                        sum(fakturs.stream().map(f -> nz(f.getDpp())).toList())));
            }
            result.addAll(missingBupotOnReceipts(invoice, data));
        }
        for (TaxTransactionDetail faktur : data.numberedFakturs) {
            Transaction trx = faktur.getTransaction();
            if (data.postedRevenue(data.unitKey(trx)).signum() <= 0) {
                UUID invoiceId = data.invoiceIdOf(trx);
                result.add(new ChainIssue(SalesTaxExceptionType.FAKTUR_NO_REVENUE, fakturEventDate(faktur), invoiceId,
                        invoiceId == null ? null : data.invoiceNumber(invoiceId), trx.getId(), trx.getTransactionNumber(),
                        faktur.getFakturNumber(), nz(faktur.getDpp())));
            }
        }
        for (TaxTransactionDetail row : data.customerBupots()) {
            Transaction trx = row.getTransaction();
            if (trx.getStatus() == TransactionStatus.POSTED && isBlank(row.getBupotNumber())) {
                UUID invoiceId = data.invoiceIdOf(trx);
                result.add(new ChainIssue(SalesTaxExceptionType.MISSING_BUPOT, trx.getTransactionDate(), invoiceId,
                        invoiceId == null ? null : data.invoiceNumber(invoiceId), trx.getId(), trx.getTransactionNumber(),
                        null, nz(row.getTaxAmount())));
            }
        }
        return result;
    }

    private List<ChainIssue> missingBupotOnReceipts(Invoice invoice, ChainData data) {
        Map<UUID, BigDecimal> outstanding = outstandingBupot(invoice, data.issuedBupots(invoice.getId()));
        return invoice.getPayments().stream()
                .filter(p -> outstanding.get(p.getId()).signum() > 0)
                .map(p -> new ChainIssue(SalesTaxExceptionType.MISSING_BUPOT, p.getPaymentDate(), invoice.getId(),
                        invoice.getInvoiceNumber(), null, null, p.getReferenceNumber(), outstanding.get(p.getId())))
                .toList();
    }

    /** Posted debits on Kredit Pajak PPh 23 with no PPh 23 row at all. Fails when the account is not configured. */
    private List<ChainIssue> creditsWithoutBupot(ChainData data) {
        UUID account = accountResolver.taxCreditPph23Account().getId();
        return journalEntryRepository.findPostedDebitsWithoutTaxDetail(account, TaxType.PPH_23).stream()
                .map(row -> {
                    UUID invoiceId = data.invoiceIdOf(row);
                    return new ChainIssue(SalesTaxExceptionType.MISSING_BUPOT, row.transactionDate(), invoiceId,
                            invoiceId == null ? null : data.invoiceNumber(invoiceId), row.transactionId(), row.transactionNumber(),
                            null, row.amount());
                })
                .toList();
    }

    /**
     * Withheld amount per receipt with no bupot yet. Bupots linked to a receipt count there first; a bupot on the
     * invoice with no receipt link covers the oldest receipts.
     */
    private Map<UUID, BigDecimal> outstandingBupot(Invoice invoice, List<TaxTransactionDetail> bupots) {
        Map<UUID, BigDecimal> linked = new HashMap<>();
        BigDecimal unlinked = BigDecimal.ZERO;
        for (TaxTransactionDetail bupot : bupots) {
            if (bupot.getInvoicePayment() == null) {
                unlinked = unlinked.add(nz(bupot.getTaxAmount()));
            } else {
                linked.merge(bupot.getInvoicePayment().getId(), nz(bupot.getTaxAmount()), BigDecimal::add);
            }
        }
        Map<UUID, BigDecimal> outstanding = new LinkedHashMap<>();
        for (InvoicePayment payment : invoice.getPayments().stream().sorted(Comparator.comparing(InvoicePayment::getPaymentDate)).toList()) {
            BigDecimal open = payment.getPph23Withheld().subtract(linked.getOrDefault(payment.getId(), BigDecimal.ZERO)).max(BigDecimal.ZERO);
            BigDecimal covered = open.min(unlinked);
            unlinked = unlinked.subtract(covered);
            outstanding.put(payment.getId(), open.subtract(covered));
        }
        return outstanding;
    }

    private List<ChainTransaction> chainTransactions(Invoice invoice, ChainData data) {
        List<Transaction> transactions = new ArrayList<>(transactionRepository
                .findBySourceDocumentTypeAndSourceDocumentIdOrderByTransactionDateAsc(INVOICE_SOURCE, invoice.getId()));
        if (invoice.getTransaction() != null && transactions.stream().noneMatch(t -> t.getId().equals(invoice.getTransaction().getId()))) {
            transactions.add(invoice.getTransaction());
        }
        return transactions.stream()
                .map(t -> new ChainTransaction(t.getId(), t.getTransactionNumber(), t.getTransactionDate(), t.getStatus(),
                        data.revenueOfTransaction(t.getId())))
                .toList();
    }

    /** Rows behind {@link Reconciliation#bookedRevenue()} of a masa: posted, non-closing revenue per transaction. */
    public List<TransactionAmount> revenueRows(int year, int month) {
        YearMonth masa = YearMonth.of(year, month);
        return journalEntryRepository.findPostedRevenueByTransaction().stream()
                .filter(r -> !r.transactionDate().isBefore(masa.atDay(1)) && !r.transactionDate().isAfter(masa.atEndOfMonth()))
                .sorted(Comparator.comparing(TransactionAmount::transactionDate).thenComparing(TransactionAmount::transactionId))
                .toList();
    }

    /** Fakturs behind {@link Reconciliation#sptDpp()} of a masa: numbered, on POSTED transactions, by faktur date. */
    public List<FakturLink> sptFakturs(int year, int month) {
        YearMonth masa = YearMonth.of(year, month);
        return load().sptFakturs().stream()
                .filter(f -> !f.getFakturDate().isBefore(masa.atDay(1)) && !f.getFakturDate().isAfter(masa.atEndOfMonth()))
                .sorted(Comparator.comparing(TaxTransactionDetail::getFakturDate).thenComparing(TaxTransactionDetail::getFakturNumber))
                .map(this::fakturLink)
                .toList();
    }

    private FakturLink fakturLink(TaxTransactionDetail d) {
        Transaction t = d.getTransaction();
        return new FakturLink(d.getId(), t.getId(), t.getTransactionNumber(), t.getStatus(), d.getFakturNumber(), d.getFakturDate(),
                d.getTransactionCode(), d.getDpp(), d.getPpn());
    }

    private BupotLink bupotLink(TaxTransactionDetail d) {
        Transaction t = d.getTransaction();
        return new BupotLink(d.getId(), t.getId(), t.getTransactionNumber(), t.getStatus(),
                d.getInvoicePayment() == null ? null : d.getInvoicePayment().getId(), d.getBupotNumber(), d.getTaxObjectCode(),
                d.getGrossAmount(), d.getTaxRate(), d.getTaxAmount());
    }

    private boolean isPaidAtAll(Invoice invoice) {
        return !invoice.getPayments().isEmpty() || invoice.getStatus() == InvoiceStatus.PAID;
    }

    /** The first receipt; a mark-paid invoice has no payment row, so its paid date; imported data may have neither. */
    private LocalDate firstPaidDate(Invoice invoice) {
        return invoice.getPayments().stream().map(InvoicePayment::getPaymentDate).min(LocalDate::compareTo)
                .orElseGet(() -> invoice.getPaidAt() != null ? invoice.getPaidAt().toLocalDate() : invoice.getInvoiceDate());
    }

    private LocalDate fakturEventDate(TaxTransactionDetail faktur) {
        return faktur.getFakturDate() != null ? faktur.getFakturDate() : faktur.getTransaction().getTransactionDate();
    }

    private ChainData load() {
        return new ChainData(
                invoiceRepository.findIssuedWithPayments(),
                invoiceRepository.findPaymentTransactionLinks().stream()
                        .collect(Collectors.toMap(InvoiceRepository.PaymentTransactionLink::transactionId,
                                InvoiceRepository.PaymentTransactionLink::invoiceId, (a, b) -> a)),
                taxDetailRepository.findAllByTaxTypeWithTransaction(TaxType.PPN_KELUARAN),
                taxDetailRepository.findAllByTaxTypeWithTransaction(TaxType.PPH_23),
                journalEntryRepository.findPostedRevenueByTransaction(),
                invoiceRepository);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** What one invoice, or one transaction outside any invoice, contributes to a masa. */
    private static final class Unit {
        private final UUID key;
        private final UUID invoiceId;
        private UUID transactionId;
        private String transactionNumber;
        private BigDecimal revenue = BigDecimal.ZERO;
        private BigDecimal dpp = BigDecimal.ZERO;
        private BigDecimal ppn = BigDecimal.ZERO;
        private final List<TaxTransactionDetail> fakturs = new ArrayList<>();

        private Unit(UUID key, UUID invoiceId) {
            this.key = key;
            this.invoiceId = invoiceId;
        }

        private void addRevenue(TransactionAmount row) {
            revenue = revenue.add(row.amount());
            if (transactionId == null) {
                transactionId = row.transactionId();
                transactionNumber = row.transactionNumber();
            }
        }

        private void addFaktur(TaxTransactionDetail faktur) {
            fakturs.add(faktur);
            dpp = dpp.add(nz(faktur.getDpp()));
            ppn = ppn.add(nz(faktur.getPpn()));
        }
    }

    /** One read of the chain's tables. Single-tenant volumes: rows per year, read whole and matched in memory. */
    private static final class ChainData {
        private final List<Invoice> issuedInvoices;
        private final Map<UUID, UUID> paymentTransactionInvoice;
        private final List<TaxTransactionDetail> numberedFakturs;
        private final List<TaxTransactionDetail> pph23Rows;
        private final List<TransactionAmount> revenue;
        private final InvoiceRepository invoiceRepository;
        private final Map<UUID, BigDecimal> revenueByUnit = new HashMap<>();
        private final Map<UUID, BigDecimal> revenueByTransaction = new HashMap<>();
        private final Map<UUID, Invoice> invoices = new HashMap<>();

        private ChainData(List<Invoice> issuedInvoices, Map<UUID, UUID> paymentTransactionInvoice, List<TaxTransactionDetail> ppnRows,
                          List<TaxTransactionDetail> pph23Rows, List<TransactionAmount> revenue, InvoiceRepository invoiceRepository) {
            this.issuedInvoices = issuedInvoices;
            this.paymentTransactionInvoice = paymentTransactionInvoice;
            this.numberedFakturs = ppnRows.stream().filter(d -> !isBlank(d.getFakturNumber())).toList();
            this.pph23Rows = pph23Rows;
            this.revenue = revenue;
            this.invoiceRepository = invoiceRepository;
            issuedInvoices.forEach(i -> invoices.put(i.getId(), i));
            for (TransactionAmount row : revenue) {
                revenueByUnit.merge(unitKey(row), row.amount(), BigDecimal::add);
                revenueByTransaction.put(row.transactionId(), row.amount());
            }
        }

        private UUID invoiceIdOf(Transaction trx) {
            return INVOICE_SOURCE.equals(trx.getSourceDocumentType()) ? trx.getSourceDocumentId()
                    : paymentTransactionInvoice.get(trx.getId());
        }

        private UUID invoiceIdOf(TransactionAmount row) {
            return INVOICE_SOURCE.equals(row.sourceDocumentType()) ? row.sourceDocumentId()
                    : paymentTransactionInvoice.get(row.transactionId());
        }

        private UUID unitKey(Transaction trx) {
            UUID invoiceId = invoiceIdOf(trx);
            return invoiceId != null ? invoiceId : trx.getId();
        }

        private UUID unitKey(TransactionAmount row) {
            UUID invoiceId = invoiceIdOf(row);
            return invoiceId != null ? invoiceId : row.transactionId();
        }

        private BigDecimal postedRevenue(UUID unitKey) {
            return revenueByUnit.getOrDefault(unitKey, BigDecimal.ZERO);
        }

        private BigDecimal revenueOfTransaction(UUID transactionId) {
            return revenueByTransaction.getOrDefault(transactionId, BigDecimal.ZERO);
        }

        /** Numbered fakturs of an invoice, on any transaction that is not void. */
        private List<TaxTransactionDetail> issuedFakturs(UUID invoiceId) {
            return numberedFakturs.stream()
                    .filter(d -> d.getTransaction().getStatus() != TransactionStatus.VOID)
                    .filter(d -> invoiceId.equals(invoiceIdOf(d.getTransaction())))
                    .toList();
        }

        private List<TaxTransactionDetail> issuedBupots(UUID invoiceId) {
            return pph23Rows.stream()
                    .filter(d -> !isBlank(d.getBupotNumber()) && d.getTransaction().getStatus() != TransactionStatus.VOID)
                    .filter(d -> invoiceId.equals(invoiceIdOf(d.getTransaction())))
                    .toList();
        }

        /** What SPT Masa PPN reports: numbered, dated, on POSTED transactions. */
        private List<TaxTransactionDetail> sptFakturs() {
            return numberedFakturs.stream()
                    .filter(d -> d.getFakturDate() != null && d.getTransaction().getStatus() == TransactionStatus.POSTED)
                    .toList();
        }

        /** PPh 23 withheld by customers: on an invoice's transaction or on a revenue transaction, not on a vendor payment. */
        private List<TaxTransactionDetail> customerBupots() {
            return pph23Rows.stream()
                    .filter(d -> d.getTransaction().getStatus() != TransactionStatus.VOID)
                    .filter(d -> invoiceIdOf(d.getTransaction()) != null || revenueOfTransaction(d.getTransaction().getId()).signum() != 0)
                    .toList();
        }

        private BigDecimal pph23OnUnit(UUID unitKey) {
            return sum(customerBupots().stream()
                    .filter(d -> unitKey(d.getTransaction()).equals(unitKey))
                    .map(d -> nz(d.getTaxAmount()))
                    .toList());
        }

        /** Null when the invoice no longer exists: a deleted invoice leaves its transactions behind. */
        private Invoice invoice(UUID invoiceId) {
            return invoices.computeIfAbsent(invoiceId, id -> invoiceRepository.findById(id).orElse(null));
        }

        private String invoiceNumber(UUID invoiceId) {
            Invoice invoice = invoice(invoiceId);
            return invoice == null ? null : invoice.getInvoiceNumber();
        }
    }
}
