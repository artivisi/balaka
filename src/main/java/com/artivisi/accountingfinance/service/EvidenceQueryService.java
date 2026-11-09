package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.BankStatementItem;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.Contract;
import com.artivisi.accountingfinance.entity.Document;
import com.artivisi.accountingfinance.entity.EvidenceRequirement;
import com.artivisi.accountingfinance.entity.Invoice;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.EvidenceType;
import com.artivisi.accountingfinance.repository.BankStatementItemRepository;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.ContractRepository;
import com.artivisi.accountingfinance.repository.DocumentRepository;
import com.artivisi.accountingfinance.repository.EvidenceRequirementRepository;
import com.artivisi.accountingfinance.repository.InvoiceRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.TaxTransactionDetailRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Read API over evidence: which documents a transaction needs, what it has, and what is missing. #51 reads its
 * evidence layer from here.
 *
 * <p>A requirement applies to a posted, non-closing transaction when the transaction amount is at least the rule's
 * minimum and either a journal line hits the rule's account or one of its sub-accounts, or the transaction's template
 * belongs to the rule's template family (any version). Evidence comes from:
 * <ul>
 *   <li>a classified file attached to the transaction, to one of its journal lines, or to its invoice;</li>
 *   <li>INVOICE: an invoice issued in the app whose journal this is;</li>
 *   <li>CONTRACT: a contract linked to the transaction or its invoice, once the contract file is attached;</li>
 *   <li>BUPOT: a bupot number in the transaction's tax detail;</li>
 *   <li>PROOF_OF_PAYMENT: a bank statement line matched to the transaction in bank reconciliation.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvidenceQueryService {

    public static final String SOURCE_ATTACHMENT = "ATTACHMENT";
    public static final String SOURCE_INVOICE = "INVOICE";
    public static final String SOURCE_CONTRACT = "CONTRACT";
    public static final String SOURCE_BUPOT = "BUPOT";
    public static final String SOURCE_BANK_MATCH = "BANK_MATCH";

    private static final String INVOICE_SOURCE_TYPE = "INVOICE";
    private static final int CHUNK = 1000;

    private final EvidenceRequirementRepository requirementRepository;
    private final ChartOfAccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final DocumentRepository documentRepository;
    private final InvoiceRepository invoiceRepository;
    private final TaxTransactionDetailRepository taxDetailRepository;
    private final BankStatementItemRepository statementItemRepository;
    private final ContractRepository contractRepository;

    /** One configured rule. Exactly one of accountId and templateId is set. */
    public record Requirement(UUID id, EvidenceType evidenceType, UUID accountId, String accountCode, String accountName,
                              UUID templateId, String templateName, BigDecimal minAmount, String notes, String label) {}

    /** A required type and the rules that ask for it. */
    public record RequiredEvidence(EvidenceType evidenceType, List<String> rules) {}

    /** One piece of evidence. documentId is set when a file backs it. */
    public record EvidenceItem(EvidenceType evidenceType, String source, String reference, UUID documentId) {}

    /** A file attached to the transaction, its journal lines or its invoice. evidenceType null means unclassified. */
    public record AttachedFile(UUID documentId, String filename, EvidenceType evidenceType, long fileSize,
                               String checksumSha256,
                               @com.fasterxml.jackson.annotation.JsonIgnore String storagePath) {}

    public record TransactionEvidence(UUID transactionId, String transactionNumber, LocalDate transactionDate,
                                      String description, BigDecimal amount, String templateName,
                                      List<RequiredEvidence> required, List<EvidenceItem> evidence,
                                      List<EvidenceType> missing, List<AttachedFile> files, List<String> notes) {

        public boolean complete() {
            return missing.isEmpty();
        }

        public long unclassifiedCount() {
            return files.stream().filter(f -> f.evidenceType() == null).count();
        }
    }

    public record ContractSummary(UUID id, String contractNumber, String title, String counterpartyKind,
                                  UUID clientId, UUID vendorId, String counterpartyName, LocalDate startDate,
                                  LocalDate endDate, BigDecimal contractValue, UUID documentId) {

        public boolean hasFile() {
            return documentId != null;
        }
    }

    // ==================== requirements ====================

    public List<Requirement> requirements() {
        return requirementRepository.findAllWithTargets().stream().map(EvidenceQueryService::toRequirement).toList();
    }

    // ==================== per transaction ====================

    public TransactionEvidence evidenceFor(UUID transactionId) {
        Transaction transaction = transactionRepository.findForEvidence(transactionId)
                .orElseThrow(() -> new EntityNotFoundException("Transaksi tidak ditemukan: " + transactionId));
        return evaluate(List.of(transaction)).getFirst();
    }

    /** Every posted, non-closing transaction in the period, complete or not. The audit pack's evidence index. */
    public List<TransactionEvidence> index(LocalDate from, LocalDate to) {
        requirePeriod(from, to);
        return evaluate(transactionRepository.findPostedForEvidence(from, to));
    }

    /** Transactions in the period with at least one required type missing. */
    public List<TransactionEvidence> gaps(LocalDate from, LocalDate to) {
        return index(from, to).stream().filter(e -> !e.complete()).toList();
    }

    // ==================== contracts ====================

    /** Contract register, optionally for one client or one vendor. */
    public List<ContractSummary> contracts(UUID clientId, UUID vendorId) {
        return contractRepository.search(clientId, vendorId).stream().map(EvidenceQueryService::toSummary).toList();
    }

    public List<ContractSummary> contractsActiveOn(LocalDate date) {
        return contractRepository.search(null, null).stream().filter(c -> c.isActiveOn(date))
                .map(EvidenceQueryService::toSummary).toList();
    }

    public static ContractSummary toSummary(Contract c) {
        return new ContractSummary(c.getId(), c.getContractNumber(), c.getTitle(), c.getCounterpartyKind(),
                c.getClient() != null ? c.getClient().getId() : null, c.getVendor() != null ? c.getVendor().getId() : null,
                c.getCounterpartyName(), c.getStartDate(), c.getEndDate(), c.getContractValue(),
                c.getDocument() != null ? c.getDocument().getId() : null);
    }

    // ==================== evaluation ====================

    private List<TransactionEvidence> evaluate(List<Transaction> transactions) {
        if (transactions.isEmpty()) {
            return List.of();
        }
        List<EvidenceRequirement> rules = requirementRepository.findAllWithTargets();
        Map<UUID, Set<UUID>> coveredAccounts = coveredAccountsByRule(rules);
        List<UUID> ids = transactions.stream().map(Transaction::getId).toList();

        Map<UUID, Set<UUID>> accountsByTransaction = new HashMap<>();
        for (Object[] row : chunked(ids, journalEntryRepository::findTransactionAccountPairs)) {
            accountsByTransaction.computeIfAbsent((UUID) row[0], k -> new HashSet<>()).add((UUID) row[1]);
        }

        Map<UUID, List<Document>> filesByTransaction = new HashMap<>();
        for (Document d : chunked(ids, documentRepository::findByTransactionIds)) {
            filesByTransaction.computeIfAbsent(d.getTransaction().getId(), k -> new ArrayList<>()).add(d);
        }
        for (Object[] row : chunked(ids, documentRepository::findByJournalEntryTransactionIds)) {
            filesByTransaction.computeIfAbsent((UUID) row[0], k -> new ArrayList<>()).add((Document) row[1]);
        }

        Map<UUID, List<Invoice>> invoicesByTransaction = linkedInvoices(transactions, ids);
        List<UUID> invoiceIds = invoicesByTransaction.values().stream().flatMap(List::stream).map(Invoice::getId)
                .distinct().toList();
        Map<UUID, List<Document>> filesByInvoice = new HashMap<>();
        for (Document d : chunked(invoiceIds, documentRepository::findByInvoiceIds)) {
            filesByInvoice.computeIfAbsent(d.getInvoice().getId(), k -> new ArrayList<>()).add(d);
        }

        Map<UUID, List<String>> bupotsByTransaction = new HashMap<>();
        for (Object[] row : chunked(ids, taxDetailRepository::findBupotNumbersByTransactionIds)) {
            bupotsByTransaction.computeIfAbsent((UUID) row[0], k -> new ArrayList<>()).add((String) row[1]);
        }

        Map<UUID, List<BankStatementItem>> matchesByTransaction = new HashMap<>();
        for (BankStatementItem item : chunked(ids, statementItemRepository::findMatchedToTransactions)) {
            matchesByTransaction.computeIfAbsent(item.getMatchedTransaction().getId(), k -> new ArrayList<>()).add(item);
        }

        List<TransactionEvidence> result = new ArrayList<>(transactions.size());
        for (Transaction t : transactions) {
            Map<EvidenceType, List<String>> required = requiredTypes(t, rules, coveredAccounts,
                    accountsByTransaction.getOrDefault(t.getId(), Set.of()));

            List<EvidenceItem> evidence = new ArrayList<>();
            List<AttachedFile> files = new ArrayList<>();
            List<String> notes = new ArrayList<>();
            Set<UUID> seenFiles = new HashSet<>();
            List<Document> attached = new ArrayList<>(filesByTransaction.getOrDefault(t.getId(), List.of()));
            for (Invoice invoice : invoicesByTransaction.getOrDefault(t.getId(), List.of())) {
                attached.addAll(filesByInvoice.getOrDefault(invoice.getId(), List.of()));
            }
            for (Document d : attached) {
                if (!seenFiles.add(d.getId())) {
                    continue;
                }
                files.add(new AttachedFile(d.getId(), d.getOriginalFilename(), d.getEvidenceType(), d.getFileSize(),
                        d.getChecksumSha256(), d.getStoragePath()));
                if (d.getEvidenceType() != null) {
                    evidence.add(new EvidenceItem(d.getEvidenceType(), SOURCE_ATTACHMENT, d.getOriginalFilename(), d.getId()));
                }
            }
            for (Invoice invoice : invoicesByTransaction.getOrDefault(t.getId(), List.of())) {
                evidence.add(new EvidenceItem(EvidenceType.INVOICE, SOURCE_INVOICE, invoice.getInvoiceNumber(), null));
                addContract(invoice.getContract(), evidence, notes);
            }
            addContract(t.getContract(), evidence, notes);
            for (String bupot : bupotsByTransaction.getOrDefault(t.getId(), List.of())) {
                evidence.add(new EvidenceItem(EvidenceType.BUPOT, SOURCE_BUPOT, bupot, null));
            }
            for (BankStatementItem item : matchesByTransaction.getOrDefault(t.getId(), List.of())) {
                evidence.add(new EvidenceItem(EvidenceType.PROOF_OF_PAYMENT, SOURCE_BANK_MATCH,
                        item.getBankStatement().getBankAccount().getBankName() + " " + item.getTransactionDate()
                                + " baris " + item.getLineNumber(), null));
            }

            Set<EvidenceType> present = new HashSet<>();
            evidence.forEach(e -> present.add(e.evidenceType()));
            List<EvidenceType> missing = required.keySet().stream().filter(type -> !present.contains(type)).toList();
            if (!missing.isEmpty() && files.stream().anyMatch(f -> f.evidenceType() == null)) {
                notes.add("Ada dokumen yang belum diklasifikasikan; klasifikasikan agar terhitung sebagai bukti");
            }

            result.add(new TransactionEvidence(t.getId(), t.getTransactionNumber(), t.getTransactionDate(),
                    t.getDescription(), t.getAmount(), t.getJournalTemplate().getTemplateName(),
                    required.entrySet().stream().map(e -> new RequiredEvidence(e.getKey(), List.copyOf(e.getValue()))).toList(),
                    List.copyOf(evidence), missing, List.copyOf(files), List.copyOf(notes)));
        }
        return result;
    }

    private static void addContract(Contract contract, List<EvidenceItem> evidence, List<String> notes) {
        if (contract == null) {
            return;
        }
        if (contract.getDocument() != null) {
            boolean listed = evidence.stream().anyMatch(e -> SOURCE_CONTRACT.equals(e.source())
                    && contract.getContractNumber().equals(e.reference()));
            if (!listed) {
                evidence.add(new EvidenceItem(EvidenceType.CONTRACT, SOURCE_CONTRACT, contract.getContractNumber(),
                        contract.getDocument().getId()));
            }
        } else {
            String note = "Kontrak " + contract.getContractNumber() + " belum memiliki dokumen";
            if (!notes.contains(note)) {
                notes.add(note);
            }
        }
    }

    private Map<UUID, List<Invoice>> linkedInvoices(List<Transaction> transactions, List<UUID> ids) {
        Map<UUID, List<Invoice>> byTransaction = new HashMap<>();
        Set<UUID> seen = new HashSet<>();
        for (Invoice invoice : chunked(ids, invoiceRepository::findByTransactionIdsWithContract)) {
            byTransaction.computeIfAbsent(invoice.getTransaction().getId(), k -> new ArrayList<>()).add(invoice);
            seen.add(invoice.getId());
        }
        Map<UUID, UUID> transactionBySourceInvoice = new HashMap<>();
        for (Transaction t : transactions) {
            if (INVOICE_SOURCE_TYPE.equals(t.getSourceDocumentType()) && t.getSourceDocumentId() != null
                    && !seen.contains(t.getSourceDocumentId())) {
                transactionBySourceInvoice.put(t.getSourceDocumentId(), t.getId());
            }
        }
        for (Invoice invoice : chunked(List.copyOf(transactionBySourceInvoice.keySet()), invoiceRepository::findByIdsWithContract)) {
            byTransaction.computeIfAbsent(transactionBySourceInvoice.get(invoice.getId()), k -> new ArrayList<>()).add(invoice);
        }
        return byTransaction;
    }

    private static Map<EvidenceType, List<String>> requiredTypes(Transaction t, List<EvidenceRequirement> rules,
                                                                 Map<UUID, Set<UUID>> coveredAccounts,
                                                                 Set<UUID> transactionAccounts) {
        Map<EvidenceType, List<String>> required = new EnumMap<>(EvidenceType.class);
        UUID family = templateFamily(t.getJournalTemplate());
        for (EvidenceRequirement rule : rules) {
            if (rule.getMinAmount() != null && t.getAmount().compareTo(rule.getMinAmount()) < 0) {
                continue;
            }
            boolean applies;
            if (rule.getAccount() != null) {
                Set<UUID> covered = coveredAccounts.get(rule.getId());
                applies = transactionAccounts.stream().anyMatch(covered::contains);
            } else {
                applies = family.equals(templateFamily(rule.getJournalTemplate()));
            }
            if (applies) {
                required.computeIfAbsent(rule.getEvidenceType(), k -> new ArrayList<>()).add(rule.getTargetLabel());
            }
        }
        return required;
    }

    /** Versions of a template point at the first version; the first version is its own family. */
    private static UUID templateFamily(JournalTemplate template) {
        return template.getOriginalTemplate() != null ? template.getOriginalTemplate().getId() : template.getId();
    }

    private Map<UUID, Set<UUID>> coveredAccountsByRule(List<EvidenceRequirement> rules) {
        if (rules.stream().noneMatch(r -> r.getAccount() != null)) {
            return Map.of();
        }
        Map<UUID, List<UUID>> children = new HashMap<>();
        for (ChartOfAccount a : accountRepository.findAll()) {
            if (a.getParent() != null) {
                children.computeIfAbsent(a.getParent().getId(), k -> new ArrayList<>()).add(a.getId());
            }
        }
        Map<UUID, Set<UUID>> covered = new LinkedHashMap<>();
        for (EvidenceRequirement rule : rules) {
            if (rule.getAccount() == null) {
                continue;
            }
            Set<UUID> ids = new HashSet<>();
            Deque<UUID> queue = new ArrayDeque<>(List.of(rule.getAccount().getId()));
            while (!queue.isEmpty()) {
                UUID id = queue.pop();
                if (ids.add(id)) {
                    queue.addAll(children.getOrDefault(id, List.of()));
                }
            }
            covered.put(rule.getId(), ids);
        }
        return covered;
    }

    private static <T> List<T> chunked(List<UUID> ids, Function<Collection<UUID>, List<T>> query) {
        List<T> out = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += CHUNK) {
            out.addAll(query.apply(ids.subList(i, Math.min(ids.size(), i + CHUNK))));
        }
        return out;
    }

    private static void requirePeriod(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Periode awal dan akhir wajib diisi");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("Tanggal akhir sebelum tanggal awal");
        }
    }

    static Requirement toRequirement(EvidenceRequirement r) {
        return new Requirement(r.getId(), r.getEvidenceType(),
                r.getAccount() != null ? r.getAccount().getId() : null,
                r.getAccount() != null ? r.getAccount().getAccountCode() : null,
                r.getAccount() != null ? r.getAccount().getAccountName() : null,
                r.getJournalTemplate() != null ? r.getJournalTemplate().getId() : null,
                r.getJournalTemplate() != null ? r.getJournalTemplate().getTemplateName() : null,
                r.getMinAmount(), r.getNotes(), r.getTargetLabel());
    }
}
