package com.artivisi.accountingfinance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Current inventory balance per product.
 * Updated on each inventory transaction.
 * Used for weighted average cost calculation and stock queries.
 */
@Entity
@Table(name = "inventory_balances")
@Getter
@Setter
@NoArgsConstructor
public class InventoryBalance extends TimestampedEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_product", nullable = false, unique = true)
    private Product product;

    /**
     * Current quantity on hand.
     */
    @NotNull
    @Column(name = "quantity", nullable = false, precision = 15, scale = 4)
    private BigDecimal quantity = BigDecimal.ZERO;

    /**
     * Total cost of current inventory.
     * Used for weighted average calculation.
     */
    @NotNull
    @Column(name = "total_cost", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalCost = BigDecimal.ZERO;

    /**
     * Weighted average cost per unit.
     * Calculated as total_cost / quantity.
     */
    @NotNull
    @Column(name = "average_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal averageCost = BigDecimal.ZERO;

    /**
     * Last transaction date for this product.
     */
    @Column(name = "last_transaction_date")
    private LocalDateTime lastTransactionDate;

    /**
     * Add inventory (purchase, adjustment in, production in).
     * Updates quantity, total cost, and recalculates average cost.
     *
     * @param qty quantity to add
     * @param unitCost cost per unit
     */
    public void addInventory(BigDecimal qty, BigDecimal unitCost) {
        BigDecimal addedCost = qty.multiply(unitCost);
        this.quantity = this.quantity.add(qty);
        this.totalCost = this.totalCost.add(addedCost);
        recalculateAverageCost();
        this.lastTransactionDate = LocalDateTime.now();
    }

    /**
     * Remove inventory (sale, adjustment out, production out).
     * Updates quantity and total cost proportionally.
     *
     * @param qty quantity to remove
     * @return the cost of removed inventory (for COGS)
     */
    public BigDecimal removeInventory(BigDecimal qty) {
        if (this.quantity.compareTo(qty) < 0) {
            throw new IllegalArgumentException("Stok tidak mencukupi. Stok saat ini: " + this.quantity + ", diminta: " + qty);
        }

        BigDecimal costRemoved = qty.multiply(this.averageCost);
        this.quantity = this.quantity.subtract(qty);
        this.totalCost = this.totalCost.subtract(costRemoved);

        // Handle rounding - if quantity becomes zero, total cost should also be zero
        if (this.quantity.compareTo(BigDecimal.ZERO) == 0) {
            this.totalCost = BigDecimal.ZERO;
            this.averageCost = BigDecimal.ZERO;
        } else {
            recalculateAverageCost();
        }

        this.lastTransactionDate = LocalDateTime.now();
        return costRemoved;
    }

    /**
     * Recalculate average cost from total cost and quantity.
     */
    private void recalculateAverageCost() {
        if (this.quantity.compareTo(BigDecimal.ZERO) > 0) {
            this.averageCost = this.totalCost.divide(this.quantity, 4, RoundingMode.HALF_UP);
        } else {
            this.averageCost = BigDecimal.ZERO;
        }
    }

    /**
     * Check if stock is below minimum threshold.
     */
    public boolean isBelowMinimum() {
        if (product.getMinimumStock() == null) {
            return false;
        }
        return this.quantity.compareTo(product.getMinimumStock()) < 0;
    }
}
