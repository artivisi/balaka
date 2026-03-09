package com.artivisi.accountingfinance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;

@MappedSuperclass
@Getter
@Setter
public abstract class DocumentLine extends TimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_product")
    private Product product;

    @NotBlank(message = "Deskripsi wajib diisi")
    @Column(name = "description", columnDefinition = "TEXT", nullable = false)
    private String description;

    @NotNull(message = "Jumlah wajib diisi")
    @Column(name = "quantity", nullable = false, precision = 19, scale = 2)
    private BigDecimal quantity = BigDecimal.ONE;

    @NotNull(message = "Harga satuan wajib diisi")
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "tax_rate", precision = 5, scale = 2)
    private BigDecimal taxRate;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "line_order", nullable = false)
    private Integer lineOrder = 0;

    @PrePersist
    protected void onCreateCalculate() {
        calculateAmounts();
    }

    @PreUpdate
    protected void onUpdateCalculate() {
        calculateAmounts();
    }

    public void calculateAmounts() {
        if (quantity != null && unitPrice != null) {
            this.amount = quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
            if (taxRate != null && taxRate.compareTo(BigDecimal.ZERO) > 0) {
                this.taxAmount = amount.multiply(taxRate)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            } else {
                this.taxAmount = BigDecimal.ZERO;
            }
        }
    }
}
