package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.Client;
import com.artivisi.accountingfinance.entity.CompanyConfig;
import com.artivisi.accountingfinance.entity.Invoice;
import com.artivisi.accountingfinance.entity.InvoiceLine;
import com.artivisi.accountingfinance.entity.InvoicePayment;
import com.artivisi.accountingfinance.entity.Product;
import com.artivisi.accountingfinance.entity.Project;
import com.artivisi.accountingfinance.entity.ProjectPaymentTerm;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.InvoiceStatus;
import com.artivisi.accountingfinance.repository.ClientRepository;
import com.artivisi.accountingfinance.repository.InvoicePaymentRepository;
import com.artivisi.accountingfinance.repository.InvoiceRepository;
import com.artivisi.accountingfinance.repository.ProductRepository;
import com.artivisi.accountingfinance.repository.ProjectPaymentTermRepository;
import com.artivisi.accountingfinance.repository.ProjectRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final InvoicePaymentRepository invoicePaymentRepository;
    private final ClientRepository clientRepository;
    private final ProjectRepository projectRepository;
    private final ProjectPaymentTermRepository paymentTermRepository;
    private final ProductRepository productRepository;
    private final DocumentPostingService documentPostingService;
    private final CompanyConfigService companyConfigService;

    private final @Lazy InvoiceService self;

    public Invoice findById(UUID id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found with id: " + id));
    }

    public Invoice findByInvoiceNumber(String invoiceNumber) {
        return invoiceRepository.findByInvoiceNumber(invoiceNumber)
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found with number: " + invoiceNumber));
    }

    public Page<Invoice> findAll(Pageable pageable) {
        return invoiceRepository.findAllByOrderByInvoiceDateDesc(pageable);
    }

    public Page<Invoice> findByFilters(InvoiceStatus status, UUID clientId, UUID projectId, Pageable pageable) {
        return invoiceRepository.findByFilters(status, clientId, projectId, pageable);
    }

    public List<Invoice> findByClientId(UUID clientId) {
        return invoiceRepository.findByClientId(clientId);
    }

    public List<Invoice> findByProjectId(UUID projectId) {
        return invoiceRepository.findByProjectId(projectId);
    }

    public List<Invoice> findByPaymentTermId(UUID paymentTermId) {
        return invoiceRepository.findByPaymentTermId(paymentTermId);
    }

    public List<Invoice> findOverdueInvoices() {
        return invoiceRepository.findOverdueInvoices(LocalDate.now());
    }

    public Invoice create(Invoice invoice) {
        return self.create(invoice, List.of());
    }

    @Transactional
    public Invoice create(Invoice invoice, List<InvoiceLine> lines) {
        ensureInvoiceNumber(invoice);
        resolveClient(invoice);
        resolveProject(invoice);
        resolvePaymentTerm(invoice);

        invoice.setStatus(InvoiceStatus.DRAFT);
        attachLines(invoice, lines);

        return invoiceRepository.save(invoice);
    }

    @Transactional
    public Invoice createFromPaymentTerm(UUID paymentTermId) {
        ProjectPaymentTerm paymentTerm = paymentTermRepository.findById(paymentTermId)
                .orElseThrow(() -> new EntityNotFoundException("Payment term not found with id: " + paymentTermId));

        Project project = paymentTerm.getProject();
        Client client = project.getClient();

        if (client == null) {
            throw new IllegalStateException("Project must have a client to create invoice");
        }

        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(generateInvoiceNumber());
        invoice.setClient(client);
        invoice.setProject(project);
        invoice.setPaymentTerm(paymentTerm);
        invoice.setInvoiceDate(LocalDate.now());

        // Calculate due date based on trigger
        LocalDate dueDate = calculateDueDate(paymentTerm);
        invoice.setDueDate(dueDate);

        // Calculate amount
        BigDecimal amount = paymentTerm.getCalculatedAmount(project.getContractValue());
        invoice.setAmount(amount);

        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.setNotes("Generated from payment term: " + paymentTerm.getName());

        return invoiceRepository.save(invoice);
    }

    public Invoice update(UUID id, Invoice updatedInvoice) {
        return self.update(id, updatedInvoice, null);
    }

    @Transactional
    public Invoice update(UUID id, Invoice updatedInvoice, List<InvoiceLine> lines) {
        Invoice existing = findById(id);

        if (existing.getStatus() != InvoiceStatus.DRAFT) {
            throw new IllegalStateException("Only draft invoices can be edited");
        }

        // Check invoice number uniqueness if changed
        if (!existing.getInvoiceNumber().equals(updatedInvoice.getInvoiceNumber())) {
            if (invoiceRepository.existsByInvoiceNumber(updatedInvoice.getInvoiceNumber())) {
                throw new IllegalArgumentException("Invoice number already exists: " + updatedInvoice.getInvoiceNumber());
            }
            existing.setInvoiceNumber(updatedInvoice.getInvoiceNumber());
        }

        // Update client if changed
        if (updatedInvoice.getClient() != null && updatedInvoice.getClient().getId() != null) {
            Client client = clientRepository.findById(updatedInvoice.getClient().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Client not found"));
            existing.setClient(client);
        }

        // Update project if changed
        if (updatedInvoice.getProject() != null && updatedInvoice.getProject().getId() != null) {
            Project project = projectRepository.findById(updatedInvoice.getProject().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Project not found"));
            existing.setProject(project);
        } else {
            existing.setProject(null);
        }

        existing.setInvoiceDate(updatedInvoice.getInvoiceDate());
        existing.setDueDate(updatedInvoice.getDueDate());
        existing.setNotes(updatedInvoice.getNotes());

        // Handle line items
        existing.getLines().clear();
        if (lines != null && !lines.isEmpty()) {
            for (int i = 0; i < lines.size(); i++) {
                InvoiceLine line = lines.get(i);
                line.setInvoice(existing);
                line.setLineOrder(i);
                resolveLineProduct(line);
            }
            existing.getLines().addAll(lines);
            existing.recalculateFromLines();
        } else {
            existing.setAmount(updatedInvoice.getAmount());
        }

        return invoiceRepository.save(existing);
    }

    @Transactional
    public void delete(UUID id) {
        Invoice invoice = findById(id);

        if (invoice.getStatus() != InvoiceStatus.DRAFT && invoice.getStatus() != InvoiceStatus.CANCELLED) {
            throw new IllegalStateException("Only draft or cancelled invoices can be deleted");
        }

        invoiceRepository.delete(invoice);
    }

    @Transactional
    public Invoice send(UUID id) {
        Invoice invoice = findById(id);

        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new IllegalStateException("Only draft invoices can be sent");
        }

        // Recognize revenue: compose one DRAFT transaction per distinct revenue account
        // (R2) through the template engine. Accounting approves the DRAFT(s) to post.
        recognizeInvoiceRevenue(invoice);

        invoice.setStatus(InvoiceStatus.SENT);
        invoice.setSentAt(LocalDateTime.now());
        return invoiceRepository.save(invoice);
    }

    /**
     * Groups invoice lines by their product's sales account and, per group, composes a
     * DRAFT revenue-recognition transaction via the "Pengakuan Pendapatan Invoice" template.
     * Amounts come from the invoice's own line figures (no tax-rate assumption); AR and PPN
     * accounts come from CompanyConfig. Throws (no fallback) if any account is unconfigured.
     */
    private void recognizeInvoiceRevenue(Invoice invoice) {
        CompanyConfig config = companyConfigService.getConfig();
        UUID receivableAccountId = requireAccountId(config.getReceivableAccount(),
                "Akun Piutang Usaha belum dikonfigurasi di Pengaturan Perusahaan");
        UUID outputTaxAccountId = requireAccountId(config.getOutputTaxAccount(),
                "Akun PPN Keluaran belum dikonfigurasi di Pengaturan Perusahaan");

        Map<UUID, ChartOfAccount> revenueAccounts = new LinkedHashMap<>();
        Map<UUID, BigDecimal> revenueByAccount = new LinkedHashMap<>();
        Map<UUID, BigDecimal> taxByAccount = new LinkedHashMap<>();

        for (InvoiceLine line : invoice.getLines()) {
            Product product = line.getProduct();
            if (product == null) {
                throw new IllegalStateException("Baris invoice '" + line.getDescription()
                        + "' belum terkait produk/jasa; akun pendapatan tidak dapat ditentukan");
            }
            ChartOfAccount revenueAccount = product.getSalesAccount();
            if (revenueAccount == null) {
                throw new IllegalStateException("Produk/jasa '" + product.getName()
                        + "' belum memiliki Akun Penjualan");
            }
            UUID key = revenueAccount.getId();
            revenueAccounts.putIfAbsent(key, revenueAccount);
            revenueByAccount.merge(key, nz(line.getAmount()), BigDecimal::add);
            taxByAccount.merge(key, nz(line.getTaxAmount()), BigDecimal::add);
        }

        for (Map.Entry<UUID, ChartOfAccount> group : revenueAccounts.entrySet()) {
            UUID revenueAccountId = group.getKey();
            BigDecimal revenueAmount = revenueByAccount.get(revenueAccountId);
            BigDecimal ppnAmount = taxByAccount.getOrDefault(revenueAccountId, BigDecimal.ZERO);
            BigDecimal arAmount = revenueAmount.add(ppnAmount);

            Map<String, UUID> hints = new HashMap<>();
            hints.put("PIUTANG", receivableAccountId);
            hints.put("PENDAPATAN", revenueAccountId);
            hints.put("PPN_KELUARAN", outputTaxAccountId);

            Map<String, BigDecimal> variables = new HashMap<>();
            variables.put("revenueAmount", revenueAmount);
            variables.put("ppnAmount", ppnAmount);
            variables.put("arAmount", arAmount);

            documentPostingService.createDraftFromTemplate(
                    null, "Pengakuan Pendapatan Invoice", invoice.getInvoiceDate(),
                    "Invoice " + invoice.getInvoiceNumber() + " - " + group.getValue().getAccountName(),
                    arAmount, hints, variables, "system", "INVOICE", invoice.getId());
        }
    }

    private UUID requireAccountId(ChartOfAccount account, String message) {
        if (account == null || account.getId() == null) {
            throw new IllegalStateException(message);
        }
        return account.getId();
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    @Transactional
    public Invoice markAsPaid(UUID id) {
        Invoice invoice = findById(id);

        if (invoice.getStatus() != InvoiceStatus.SENT && invoice.getStatus() != InvoiceStatus.OVERDUE
                && invoice.getStatus() != InvoiceStatus.PARTIAL) {
            throw new IllegalStateException("Only sent, overdue, or partial invoices can be marked as paid");
        }

        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(LocalDateTime.now());
        return invoiceRepository.save(invoice);
    }

    @Transactional
    public Invoice linkTransactionAndMarkPaid(UUID invoiceId, Transaction transaction) {
        Invoice invoice = findById(invoiceId);

        if (invoice.getStatus() != InvoiceStatus.SENT && invoice.getStatus() != InvoiceStatus.OVERDUE
                && invoice.getStatus() != InvoiceStatus.PARTIAL) {
            throw new IllegalStateException("Only sent, overdue, or partial invoices can be marked as paid");
        }

        invoice.setTransaction(transaction);
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(LocalDateTime.now());
        return invoiceRepository.save(invoice);
    }

    @Transactional
    public Invoice recordPayment(UUID invoiceId, InvoicePayment payment) {
        Invoice invoice = findById(invoiceId);

        if (invoice.getStatus() != InvoiceStatus.SENT && invoice.getStatus() != InvoiceStatus.OVERDUE
                && invoice.getStatus() != InvoiceStatus.PARTIAL) {
            throw new IllegalStateException("Pembayaran hanya bisa dicatat untuk invoice berstatus Terkirim, Jatuh Tempo, atau Sebagian");
        }

        BigDecimal existingPayments = invoicePaymentRepository.sumPaymentsByInvoiceId(invoiceId);
        BigDecimal totalAfterPayment = existingPayments.add(payment.getAmount());
        BigDecimal totalAmount = invoice.getTotalAmount();

        if (totalAfterPayment.compareTo(totalAmount) > 0) {
            throw new IllegalArgumentException("Total pembayaran (Rp " + totalAfterPayment +
                    ") melebihi total invoice (Rp " + totalAmount + ")");
        }

        payment.setInvoice(invoice);
        invoicePaymentRepository.save(payment);

        if (totalAfterPayment.compareTo(totalAmount) == 0) {
            invoice.setStatus(InvoiceStatus.PAID);
            invoice.setPaidAt(LocalDateTime.now());
        } else {
            invoice.setStatus(InvoiceStatus.PARTIAL);
        }

        return invoiceRepository.save(invoice);
    }

    public List<InvoicePayment> findPaymentsByInvoiceId(UUID invoiceId) {
        return invoicePaymentRepository.findByInvoiceIdOrderByPaymentDateAsc(invoiceId);
    }

    @Transactional
    public Invoice cancel(UUID id) {
        Invoice invoice = findById(id);

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new IllegalStateException("Paid invoices cannot be cancelled");
        }

        invoice.setStatus(InvoiceStatus.CANCELLED);
        return invoiceRepository.save(invoice);
    }

    @Transactional
    public int updateOverdueInvoices() {
        List<Invoice> overdueInvoices = invoiceRepository.findOverdueInvoices(LocalDate.now());
        int count = 0;
        for (Invoice invoice : overdueInvoices) {
            invoice.setStatus(InvoiceStatus.OVERDUE);
            invoiceRepository.save(invoice);
            count++;
        }
        return count;
    }

    public BigDecimal sumPaidAmountByClientId(UUID clientId) {
        return invoiceRepository.sumPaidAmountByClientId(clientId);
    }

    public BigDecimal sumPaidAmountByProjectId(UUID projectId) {
        return invoiceRepository.sumPaidAmountByProjectId(projectId);
    }

    public long countByStatus(InvoiceStatus status) {
        return invoiceRepository.countByStatus(status);
    }

    private String generateInvoiceNumber() {
        String yearMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String prefix = "INV-" + yearMonth + "-";
        Integer maxSeq = invoiceRepository.findMaxSequenceByPrefix(prefix + "%");
        int nextSeq = (maxSeq == null ? 0 : maxSeq) + 1;
        return prefix + String.format("%04d", nextSeq);
    }

    private void ensureInvoiceNumber(Invoice invoice) {
        if (invoice.getInvoiceNumber() == null || invoice.getInvoiceNumber().isBlank()) {
            invoice.setInvoiceNumber(generateInvoiceNumber());
        } else if (invoiceRepository.existsByInvoiceNumber(invoice.getInvoiceNumber())) {
            throw new IllegalArgumentException("Invoice number already exists: " + invoice.getInvoiceNumber());
        }
    }

    private void resolveClient(Invoice invoice) {
        if (invoice.getClient() != null && invoice.getClient().getId() != null) {
            Client client = clientRepository.findById(invoice.getClient().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Client not found"));
            invoice.setClient(client);
        }
    }

    private void resolveProject(Invoice invoice) {
        if (invoice.getProject() != null && invoice.getProject().getId() != null) {
            Project project = projectRepository.findById(invoice.getProject().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Project not found"));
            invoice.setProject(project);
        } else {
            invoice.setProject(null);
        }
    }

    private void resolvePaymentTerm(Invoice invoice) {
        if (invoice.getPaymentTerm() != null && invoice.getPaymentTerm().getId() != null) {
            ProjectPaymentTerm paymentTerm = paymentTermRepository.findById(invoice.getPaymentTerm().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Payment term not found"));
            invoice.setPaymentTerm(paymentTerm);
        }
    }

    private void attachLines(Invoice invoice, List<InvoiceLine> lines) {
        if (lines != null && !lines.isEmpty()) {
            for (int i = 0; i < lines.size(); i++) {
                InvoiceLine line = lines.get(i);
                line.setInvoice(invoice);
                line.setLineOrder(i);
                resolveLineProduct(line);
            }
            invoice.getLines().addAll(lines);
            invoice.recalculateFromLines();
        }
    }

    private void resolveLineProduct(InvoiceLine line) {
        if (line.getProduct() != null && line.getProduct().getId() != null) {
            Product product = productRepository.findById(line.getProduct().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Product not found"));
            line.setProduct(product);
        } else {
            line.setProduct(null);
        }
    }

    private LocalDate calculateDueDate(ProjectPaymentTerm paymentTerm) {
        return switch (paymentTerm.getDueTrigger()) {
            case ON_SIGNING -> LocalDate.now().plusDays(7);
            case ON_MILESTONE -> {
                if (paymentTerm.getMilestone() != null && paymentTerm.getMilestone().getActualDate() != null) {
                    yield paymentTerm.getMilestone().getActualDate().plusDays(14);
                }
                yield LocalDate.now().plusDays(30);
            }
            case ON_COMPLETION -> LocalDate.now().plusDays(30);
            case FIXED_DATE -> paymentTerm.getDueDate() != null ? paymentTerm.getDueDate() : LocalDate.now().plusDays(30);
        };
    }
}
