package com.artivisi.accountingfinance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Bill of Materials Line - a single component in the BOM recipe.
 */
@Entity
@Table(name = "bill_of_material_lines")
@Getter
@Setter
@NoArgsConstructor
public class BillOfMaterialLine extends TimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_bill_of_material", nullable = false)
    private BillOfMaterial billOfMaterial;

    @NotNull(message = "Komponen wajib dipilih")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_component", nullable = false)
    private Product component;

    @NotNull(message = "Jumlah komponen wajib diisi")
    @Column(name = "quantity", precision = 15, scale = 4, nullable = false)
    private BigDecimal quantity;

    @Size(max = 255, message = "Catatan maksimal 255 karakter")
    @Column(name = "notes", length = 255)
    private String notes;

    @Column(name = "line_order", nullable = false)
    private int lineOrder = 0;

}
