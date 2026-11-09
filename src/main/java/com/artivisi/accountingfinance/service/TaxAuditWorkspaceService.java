package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.AlertRule;
import com.artivisi.accountingfinance.entity.ArchiveDocument;
import com.artivisi.accountingfinance.entity.BankStatementItem;
import com.artivisi.accountingfinance.entity.Invoice;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.PayrollDetail;
import com.artivisi.accountingfinance.entity.TaxDocument;
import com.artivisi.accountingfinance.entity.TaxFiling;
import com.artivisi.accountingfinance.entity.TaxInquiryCase;
import com.artivisi.accountingfinance.entity.TaxInquiryDisclosure;
import com.artivisi.accountingfinance.entity.TaxTransactionDetail;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.AlertType;
import com.artivisi.accountingfinance.enums.SelfTestLine;
import com.artivisi.accountingfinance.repository.AlertRuleRepository;
import com.artivisi.accountingfinance.repository.BankStatementItemRepository;
import com.artivisi.accountingfinance.repository.InvoiceRepository;
import com.artivisi.accountingfinance.repository.PayrollDetailRepository;
import com.artivisi.accountingfinance.repository.TaxDocumentRepository;
import com.artivisi.accountingfinance.repository.TaxFilingRepository;
import com.artivisi.accountingfinance.repository.TaxInquiryDisclosureRepository;
import com.artivisi.accountingfinance.repository.TaxTransactionDetailRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.service.AssetRegisterQueryService.CostMovement;
import com.artivisi.accountingfinance.service.BankStatementArchiveService.MissingStatement;
import com.artivisi.accountingfinance.service.CloseCheckService.CloseChecklist;
import com.artivisi.accountingfinance.service.EvidenceQueryService.ContractSummary;
import com.artivisi.accountingfinance.service.EvidenceQueryService.TransactionEvidence;
import com.artivisi.accountingfinance.service.PayrollReconciliationService.YearReconciliation;
import com.artivisi.accountingfinance.service.SalesTaxChainService.InvoiceChain;
import com.artivisi.accountingfinance.service.SelfTestService.Trace;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tax audit workspace (issue #51): one view per tax year and per self-test line, and the drill-down from a ledger line
 * to its invoice, faktur, bupot, contract, evidence, bank line, tax filing and inquiry cases. Read-only; every figure
 * comes from the service that owns it (#43-#50).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaxAuditWorkspaceService {

    private final SelfTestService selfTestService;
    private final EvidenceQueryService evidenceQueryService;
    private final BankStatementArchiveService bankStatementArchiveService;
    private final CloseCheckService closeCheckService;
    private final AssetRegisterQueryService assetRegisterQueryService;
    private final PayrollReconciliationService payrollReconciliationService;
    private final SalesTaxChainService salesTaxChainService;
    private final TaxFilingService taxFilingService;
    private final TaxInquiryService taxInquiryService;
    private final DocumentArchiveService documentArchiveService;
    private final TransactionRepository transactionRepository;
    private final InvoiceRepository invoiceRepository;
    private final TaxTransactionDetailRepository taxDetailRepository;
    private final BankStatementItemRepository statementItemRepository;
    private final TaxFilingRepository taxFilingRepository;
    private final TaxDocumentRepository taxDocumentRepository;
    private final TaxInquiryDisclosureRepository disclosureRepository;
    private final PayrollDetailRepository payrollDetailRepository;
    private final AlertRuleRepository alertRuleRepository;

    public record FilingView(TaxFiling filing, List<TaxDocument> documents) {}

    public record YearView(int year, SelfTestService.Report selfTest, long postedTransactions,
                           List<TransactionEvidence> evidenceGaps, List<MissingStatement> missingStatements,
                           CloseChecklist closeChecks, CostMovement assets, YearReconciliation payroll,
                           List<FilingView> filings, List<TaxInquiryCase> cases, long archiveCount) {}

    /** Trace rows of one self-test row, with each row's evidence state and the payroll run behind payroll rows. */
    public record TraceView(Trace trace, Map<UUID, TransactionEvidence> evidenceByTransaction,
                            Map<UUID, UUID> payrollRunByDetail) {

        public long gapCount() {
            return evidenceByTransaction.values().stream().filter(e -> !e.complete()).count();
        }
    }

    public record LedgerView(Transaction transaction, List<JournalEntry> lines, UUID selectedLineId,
                             TransactionEvidence evidence, List<InvoiceChain> invoices, List<TaxTransactionDetail> taxDetails,
                             List<ContractSummary> contracts, List<BankStatementItem> bankLines, List<FilingView> filings,
                             List<TaxInquiryCase> cases, List<ArchiveDocument> archiveDocuments,
                             List<TaxInquiryDisclosure> disclosures) {}

    // ==================== year ====================

    public YearView year(int year) {
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);
        List<TransactionEvidence> index = evidenceQueryService.index(from, to);
        List<MissingStatement> missing = bankStatementArchiveService.missingStatements(LocalDate.now(), statementGraceDays())
                .stream().filter(m -> m.month().getYear() == year).toList();
        return new YearView(year, selfTestService.report(year), index.size(),
                index.stream().filter(e -> !e.complete()).toList(), missing,
                closeCheckService.checklistForYear(year), assetRegisterQueryService.costMovement(year),
                payrollReconciliationService.reconcileYear(year), filings(taxFilingRepository.findByFilters(null, year)),
                taxInquiryService.findByYear(year), documentArchiveService.countForYear(year));
    }

    public TraceView trace(int year, SelfTestLine line, int month) {
        Trace trace = selfTestService.trace(year, line, month);
        Map<UUID, TransactionEvidence> evidence = new HashMap<>();
        for (TransactionEvidence e : evidenceQueryService.index(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))) {
            evidence.put(e.transactionId(), e);
        }
        Map<UUID, TransactionEvidence> relevant = new LinkedHashMap<>();
        List<UUID> payrollDetailIds = new ArrayList<>();
        for (SelfTestService.TraceRow row : concat(trace.bookRows(), trace.taxRows())) {
            if (row.transactionId() != null && evidence.containsKey(row.transactionId())) {
                relevant.put(row.transactionId(), evidence.get(row.transactionId()));
            }
            if (SelfTestService.SOURCE_PAYROLL_DETAIL.equals(row.source())) {
                payrollDetailIds.add(row.referenceId());
            }
        }
        Map<UUID, UUID> runs = new HashMap<>();
        for (PayrollDetail d : payrollDetailRepository.findAllById(payrollDetailIds)) {
            runs.put(d.getId(), d.getPayrollRun().getId());
        }
        return new TraceView(trace, relevant, runs);
    }

    // ==================== ledger line ====================

    public LedgerView ledger(UUID transactionId, UUID selectedLineId) {
        Transaction t = transactionRepository.findByIdWithJournalEntries(transactionId)
                .orElseThrow(() -> new EntityNotFoundException("Transaksi tidak ditemukan: " + transactionId));
        List<JournalEntry> lines = t.getJournalEntries().stream()
                .sorted(java.util.Comparator.comparing(JournalEntry::getLineOrder)).toList();
        if (selectedLineId != null && lines.stream().noneMatch(j -> j.getId().equals(selectedLineId))) {
            throw new IllegalArgumentException("Baris jurnal " + selectedLineId + " bukan milik transaksi ini");
        }
        TransactionEvidence evidence = evidenceQueryService.evidenceFor(transactionId);

        Map<UUID, Invoice> invoices = new LinkedHashMap<>();
        invoiceRepository.findByTransactionIdsWithContract(List.of(transactionId)).forEach(i -> invoices.put(i.getId(), i));
        if ("INVOICE".equals(t.getSourceDocumentType()) && t.getSourceDocumentId() != null) {
            invoiceRepository.findByIdsWithContract(List.of(t.getSourceDocumentId())).forEach(i -> invoices.putIfAbsent(i.getId(), i));
        }
        List<InvoiceChain> chains = invoices.keySet().stream().map(salesTaxChainService::getChain).toList();

        Map<UUID, ContractSummary> contracts = new LinkedHashMap<>();
        if (t.getContract() != null) {
            contracts.put(t.getContract().getId(), EvidenceQueryService.toSummary(t.getContract()));
        }
        invoices.values().stream().filter(i -> i.getContract() != null)
                .forEach(i -> contracts.putIfAbsent(i.getContract().getId(), EvidenceQueryService.toSummary(i.getContract())));

        Map<UUID, TaxFiling> filings = new LinkedHashMap<>();
        taxFilingService.findOverlapping(t.getTransactionDate(), t.getTransactionDate()).forEach(f -> filings.put(f.getId(), f));
        taxFilingRepository.findByPaymentTransactionId(transactionId).forEach(f -> filings.putIfAbsent(f.getId(), f));

        List<ArchiveDocument> archive = documentArchiveService.forTransaction(transactionId);
        List<UUID> documentIds = new ArrayList<>();
        evidence.files().forEach(f -> documentIds.add(f.documentId()));
        evidence.evidence().stream().filter(e -> e.documentId() != null).forEach(e -> documentIds.add(e.documentId()));
        archive.forEach(a -> documentIds.add(a.getDocument().getId()));

        return new LedgerView(t, lines, selectedLineId, evidence, chains,
                taxDetailRepository.findAllByTransactionIdOrderByTaxTypeAsc(transactionId), List.copyOf(contracts.values()),
                statementItemRepository.findMatchedToTransactions(List.of(transactionId)),
                filings(List.copyOf(filings.values())),
                taxInquiryService.findByTaxFilings(List.copyOf(filings.keySet())), archive,
                documentIds.isEmpty() ? List.of() : disclosureRepository.findByDocumentIds(documentIds));
    }

    // ==================== helpers ====================

    private List<FilingView> filings(List<TaxFiling> filings) {
        return filings.stream().map(f -> new FilingView(f, taxDocumentRepository.findByFilingId(f.getId()))).toList();
    }

    /** The grace period of the bank statement check is the BANK_STATEMENT_MISSING alert threshold (#48). */
    private int statementGraceDays() {
        AlertRule rule = alertRuleRepository.findByAlertType(AlertType.BANK_STATEMENT_MISSING)
                .orElseThrow(() -> new IllegalStateException("Aturan peringatan BANK_STATEMENT_MISSING tidak ditemukan"));
        return rule.getThreshold().intValue();
    }

    private static <T> List<T> concat(List<T> a, List<T> b) {
        List<T> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }
}
