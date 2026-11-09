package com.artivisi.accountingfinance.entity;

import com.artivisi.accountingfinance.enums.InvoiceStatus;
import com.artivisi.accountingfinance.util.DisplayLabels;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
public class Invoice extends TimestampedEntity {

    @Size(max = 50, message = "Nomor invoice maksimal 50 karakter")
    @Column(name = "invoice_number", nullable = false, unique = true, length = 50)
    private String invoiceNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_client", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_project")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_payment_term")
    private ProjectPaymentTerm paymentTerm;

    @NotNull(message = "Tanggal invoice wajib diisi")
    @Column(name = "invoice_date", nullable = false)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate invoiceDate;

    @NotNull(message = "Tanggal jatuh tempo wajib diisi")
    @Column(name = "due_date", nullable = false)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dueDate;

    @NotNull(message = "Jumlah wajib diisi")
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "id_journal_entry")
    private UUID journalEntryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_transaction")
    private Transaction transaction;

    /** Contract this invoice bills under; its file is the invoice journal's CONTRACT evidence. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_contract")
    private Contract contract;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineOrder ASC")
    private List<InvoiceLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("paymentDate ASC")
    private List<InvoicePayment> payments = new ArrayList<>();

    public boolean isDraft() {
        return status == InvoiceStatus.DRAFT;
    }

    public boolean isSent() {
        return status == InvoiceStatus.SENT;
    }

    public boolean isPartial() {
        return status == InvoiceStatus.PARTIAL;
    }

    public boolean isPaid() {
        return status == InvoiceStatus.PAID;
    }

    public boolean isOverdue() {
        return status == InvoiceStatus.OVERDUE ||
                ((status == InvoiceStatus.SENT || status == InvoiceStatus.PARTIAL) && dueDate != null && LocalDate.now().isAfter(dueDate));
    }

    public boolean isCancelled() {
        return status == InvoiceStatus.CANCELLED;
    }

    public BigDecimal getTotalAmount() {
        return amount.add(taxAmount);
    }

    public BigDecimal getPaidAmount() {
        return payments.stream()
                .map(InvoicePayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getBalanceDue() {
        return getTotalAmount().subtract(getPaidAmount());
    }

    /**
     * Combobox label for the bound client. Used by the invoice form combobox
     * (clientPicker) so the data-initial-label can be rendered uniformly whether
     * the model attribute is the entity (GET) or the InvoiceForm (POST re-render).
     */
    public String getClientLabel() {
        if (client == null) return "";
        return DisplayLabels.codeName(client.getCode(), client.getName());
    }

    public void recalculateFromLines() {
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        for (InvoiceLine line : lines) {
            line.calculateAmounts();
            totalAmount = totalAmount.add(line.getAmount());
            totalTax = totalTax.add(line.getTaxAmount());
        }
        this.amount = totalAmount;
        this.taxAmount = totalTax;
    }
}
