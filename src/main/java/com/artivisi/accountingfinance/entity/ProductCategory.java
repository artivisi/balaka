package com.artivisi.accountingfinance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


/**
 * Product category for organizing inventory items.
 * Supports hierarchical structure (parent-child).
 * Examples: Bahan Baku, Barang Jadi, Bahan Penolong, Packaging.
 */
@Entity
@Table(name = "product_categories")
@Getter
@Setter
@NoArgsConstructor
public class ProductCategory extends TimestampedEntity {

    @NotBlank(message = "Kode kategori wajib diisi")
    @Size(max = 20, message = "Kode kategori maksimal 20 karakter")
    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code;

    @NotBlank(message = "Nama kategori wajib diisi")
    @Size(max = 100, message = "Nama kategori maksimal 100 karakter")
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Size(max = 255, message = "Deskripsi maksimal 255 karakter")
    @Column(name = "description")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_parent")
    private ProductCategory parent;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /**
     * Get full category path (e.g., "Bahan Baku > Tepung")
     */
    public String getFullPath() {
        if (parent == null) {
            return name;
        }
        return parent.getFullPath() + " > " + name;
    }
}
