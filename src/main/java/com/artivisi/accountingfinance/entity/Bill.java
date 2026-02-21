package com.artivisi.accountingfinance.entity;

import com.artivisi.accountingfinance.enums.BillStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
@Table(name = "bills")
@Getter
@Setter
@NoArgsConstructor
public class Bill {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Size(max = 50, message = "Nomor tagihan maksimal 50 karakter")
    @Column(name = "bill_number", nullable = false, unique = true, length = 50)
    private String billNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_vendor", nullable = false)
    private Vendor vendor;

    @Size(max = 100, message = "Nomor faktur vendor maksimal 100 karakter")
    @Column(name = "vendor_invoice_number", length = 100)
    private String vendorInvoiceNumber;

    @NotNull(message = "Tanggal tagihan wajib diisi")
    @Column(name = "bill_date", nullable = false)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate billDate;

    @NotNull(message = "Tanggal jatuh tempo wajib diisi")
    @Column(name = "due_date", nullable = false)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dueDate;

    @NotNull(message = "Jumlah wajib diisi")
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BillStatus status = BillStatus.DRAFT;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Size(max = 100)
    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_transaction")
    private Transaction transaction;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineOrder ASC")
    private List<BillLine> lines = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isDraft() {
        return status == BillStatus.DRAFT;
    }

    public boolean isApproved() {
        return status == BillStatus.APPROVED;
    }

    public boolean isPaid() {
        return status == BillStatus.PAID;
    }

    public boolean isOverdue() {
        return status == BillStatus.OVERDUE ||
                (status == BillStatus.APPROVED && dueDate != null && LocalDate.now().isAfter(dueDate));
    }

    public boolean isCancelled() {
        return status == BillStatus.CANCELLED;
    }

    public void recalculateFromLines() {
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        for (BillLine line : lines) {
            line.calculateAmounts();
            totalAmount = totalAmount.add(line.getAmount());
            totalTax = totalTax.add(line.getTaxAmount());
        }
        this.amount = totalAmount;
        this.taxAmount = totalTax;
    }
}
