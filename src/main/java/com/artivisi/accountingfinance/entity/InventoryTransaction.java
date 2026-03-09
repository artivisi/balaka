package com.artivisi.accountingfinance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Records individual inventory movements (purchase, sale, adjustment, production).
 * Each transaction affects inventory balance and may generate journal entries.
 */
@Entity
@Table(name = "inventory_transactions")
@Getter
@Setter
@NoArgsConstructor
public class InventoryTransaction extends TimestampedEntity {

    @NotNull(message = "Produk wajib diisi")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_product", nullable = false)
    private Product product;

    @NotNull(message = "Tipe transaksi wajib diisi")
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private InventoryTransactionType transactionType;

    @NotNull(message = "Tanggal transaksi wajib diisi")
    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @NotNull(message = "Kuantitas wajib diisi")
    @Positive(message = "Kuantitas harus lebih dari 0")
    @Column(name = "quantity", nullable = false, precision = 15, scale = 4)
    private BigDecimal quantity;

    /**
     * Unit cost at time of transaction.
     * For purchases: the purchase price per unit.
     * For sales: the cost calculated based on costing method (FIFO or weighted average).
     * For adjustments: the current average cost or specified cost.
     */
    @NotNull(message = "Harga satuan wajib diisi")
    @Column(name = "unit_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitCost;

    /**
     * Total cost = quantity * unit_cost
     * Stored for performance and audit purposes.
     */
    @NotNull
    @Column(name = "total_cost", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalCost;

    /**
     * For sales: the selling price per unit.
     */
    @Column(name = "unit_price", precision = 19, scale = 4)
    private BigDecimal unitPrice;

    /**
     * Reference document number (e.g., PO number, Invoice number, etc.)
     */
    @Size(max = 100, message = "Nomor referensi maksimal 100 karakter")
    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    @Size(max = 500, message = "Catatan maksimal 500 karakter")
    @Column(name = "notes", length = 500)
    private String notes;

    /**
     * Link to financial transaction (if journal entry was generated)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_transaction")
    private Transaction transaction;

    /**
     * Running balance after this transaction.
     * Stored for quick balance lookup and audit trail.
     */
    @NotNull
    @Column(name = "balance_after", nullable = false, precision = 15, scale = 4)
    private BigDecimal balanceAfter;

    /**
     * Running total cost after this transaction.
     * Used for weighted average calculation.
     */
    @NotNull
    @Column(name = "total_cost_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalCostAfter;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @PrePersist
    protected void onCreateCalculateTotalCost() {
        if (totalCost == null && quantity != null && unitCost != null) {
            totalCost = quantity.multiply(unitCost);
        }
    }

    /**
     * Check if this is an inbound transaction (increases inventory).
     */
    public boolean isInbound() {
        return transactionType != null && transactionType.isInbound();
    }
}
