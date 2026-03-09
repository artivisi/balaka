package com.artivisi.accountingfinance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Asset category defines default depreciation settings for a group of assets.
 * Examples: Kendaraan, Peralatan Kantor, Komputer, Bangunan, Mesin.
 */
@Entity
@Table(name = "asset_categories")
@Getter
@Setter
@NoArgsConstructor
public class AssetCategory extends TimestampedEntity {

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

    // Default depreciation settings
    @NotNull(message = "Metode penyusutan wajib diisi")
    @Enumerated(EnumType.STRING)
    @Column(name = "depreciation_method", nullable = false, length = 20)
    private DepreciationMethod depreciationMethod = DepreciationMethod.STRAIGHT_LINE;

    @NotNull(message = "Umur ekonomis (bulan) wajib diisi")
    @Min(value = 1, message = "Umur ekonomis minimal 1 bulan")
    @Max(value = 600, message = "Umur ekonomis maksimal 600 bulan (50 tahun)")
    @Column(name = "useful_life_months", nullable = false)
    private Integer usefulLifeMonths = 48; // Default 4 years

    /**
     * Depreciation rate for declining balance method.
     * Stored as percentage (e.g., 25.00 for 25%).
     */
    @Column(name = "depreciation_rate", precision = 5, scale = 2)
    private BigDecimal depreciationRate;

    // Default accounts for this category
    @NotNull(message = "Akun aset wajib diisi")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_asset_account", nullable = false)
    private ChartOfAccount assetAccount;

    @NotNull(message = "Akun akumulasi penyusutan wajib diisi")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_accumulated_depreciation_account", nullable = false)
    private ChartOfAccount accumulatedDepreciationAccount;

    @NotNull(message = "Akun beban penyusutan wajib diisi")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_depreciation_expense_account", nullable = false)
    private ChartOfAccount depreciationExpenseAccount;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    public boolean isActive() {
        return Boolean.TRUE.equals(active);
    }

    public String getDisplayName() {
        return code + " - " + name;
    }

    /**
     * Get useful life in years (for display).
     */
    public int getUsefulLifeYears() {
        return usefulLifeMonths / 12;
    }

    /**
     * Set useful life from years.
     */
    public void setUsefulLifeYears(int years) {
        this.usefulLifeMonths = years * 12;
    }
}
