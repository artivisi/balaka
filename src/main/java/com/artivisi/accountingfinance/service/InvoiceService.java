package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.Client;
import com.artivisi.accountingfinance.entity.Invoice;
import com.artivisi.accountingfinance.entity.Project;
import com.artivisi.accountingfinance.entity.ProjectPaymentTerm;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.InvoiceStatus;
import com.artivisi.accountingfinance.repository.ClientRepository;
import com.artivisi.accountingfinance.repository.InvoiceRepository;
import com.artivisi.accountingfinance.repository.ProjectPaymentTermRepository;
import com.artivisi.accountingfinance.repository.ProjectRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final ClientRepository clientRepository;
    private final ProjectRepository projectRepository;
    private final ProjectPaymentTermRepository paymentTermRepository;

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

    @Transactional
    public Invoice create(Invoice invoice) {
        // Generate invoice number if not provided
        if (invoice.getInvoiceNumber() == null || invoice.getInvoiceNumber().isBlank()) {
            invoice.setInvoiceNumber(generateInvoiceNumber());
        } else {
            // Check for duplicate invoice number
            if (invoiceRepository.existsByInvoiceNumber(invoice.getInvoiceNumber())) {
                throw new IllegalArgumentException("Invoice number already exists: " + invoice.getInvoiceNumber());
            }
        }

        // Load and set client
        if (invoice.getClient() != null && invoice.getClient().getId() != null) {
            Client client = clientRepository.findById(invoice.getClient().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Client not found"));
            invoice.setClient(client);
        }

        // Load and set project if specified
        if (invoice.getProject() != null && invoice.getProject().getId() != null) {
            Project project = projectRepository.findById(invoice.getProject().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Project not found"));
            invoice.setProject(project);
        } else {
            invoice.setProject(null);
        }

        // Load and set payment term if specified
        if (invoice.getPaymentTerm() != null && invoice.getPaymentTerm().getId() != null) {
            ProjectPaymentTerm paymentTerm = paymentTermRepository.findById(invoice.getPaymentTerm().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Payment term not found"));
            invoice.setPaymentTerm(paymentTerm);
        }

        invoice.setStatus(InvoiceStatus.DRAFT);
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

    @Transactional
    public Invoice update(UUID id, Invoice updatedInvoice) {
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
        existing.setAmount(updatedInvoice.getAmount());
        existing.setNotes(updatedInvoice.getNotes());

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

        invoice.setStatus(InvoiceStatus.SENT);
        invoice.setSentAt(LocalDateTime.now());
        return invoiceRepository.save(invoice);
    }

    @Transactional
    public Invoice markAsPaid(UUID id) {
        Invoice invoice = findById(id);

        if (invoice.getStatus() != InvoiceStatus.SENT && invoice.getStatus() != InvoiceStatus.OVERDUE) {
            throw new IllegalStateException("Only sent or overdue invoices can be marked as paid");
        }

        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(LocalDateTime.now());
        return invoiceRepository.save(invoice);
    }

    @Transactional
    public Invoice linkTransactionAndMarkPaid(UUID invoiceId, Transaction transaction) {
        Invoice invoice = findById(invoiceId);

        if (invoice.getStatus() != InvoiceStatus.SENT && invoice.getStatus() != InvoiceStatus.OVERDUE) {
            throw new IllegalStateException("Only sent or overdue invoices can be marked as paid");
        }

        invoice.setTransaction(transaction);
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(LocalDateTime.now());
        return invoiceRepository.save(invoice);
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
