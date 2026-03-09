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
import java.time.LocalDate;

/**
 * FIFO layer for inventory costing.
 * Each purchase creates a new layer with its quantity and unit cost.
 * Sales consume from the oldest layers first.
 */
@Entity
@Table(name = "inventory_fifo_layers")
@Getter
@Setter
@NoArgsConstructor
public class InventoryFifoLayer extends TimestampedEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_product", nullable = false)
    private Product product;

    /**
     * Reference to the inventory transaction that created this layer.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_inventory_transaction", nullable = false)
    private InventoryTransaction inventoryTransaction;

    /**
     * Date when this layer was created (for FIFO ordering).
     */
    @NotNull
    @Column(name = "layer_date", nullable = false)
    private LocalDate layerDate;

    /**
     * Original quantity in this layer.
     */
    @NotNull
    @Column(name = "original_quantity", nullable = false, precision = 15, scale = 4)
    private BigDecimal originalQuantity;

    /**
     * Remaining quantity in this layer.
     * Decreases as inventory is consumed.
     */
    @NotNull
    @Column(name = "remaining_quantity", nullable = false, precision = 15, scale = 4)
    private BigDecimal remainingQuantity;

    /**
     * Unit cost for this layer.
     */
    @NotNull
    @Column(name = "unit_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitCost;

    /**
     * Whether this layer is fully consumed.
     */
    @Column(name = "fully_consumed", nullable = false)
    private boolean fullyConsumed = false;

    /**
     * Consume quantity from this layer.
     *
     * @param qty quantity to consume
     * @return actual quantity consumed (may be less than requested if layer has insufficient)
     */
    public BigDecimal consume(BigDecimal qty) {
        BigDecimal consumable = qty.min(remainingQuantity);
        remainingQuantity = remainingQuantity.subtract(consumable);
        if (remainingQuantity.compareTo(BigDecimal.ZERO) == 0) {
            fullyConsumed = true;
        }
        return consumable;
    }

    /**
     * Get total cost for a given quantity from this layer.
     */
    public BigDecimal getCostForQuantity(BigDecimal qty) {
        return qty.multiply(unitCost);
    }
}
