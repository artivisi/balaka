package com.artivisi.accountingfinance.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Fixed asset entity for tracking depreciable assets.
 */
@Entity
@Table(name = "fixed_assets")
@Getter
@Setter
@NoArgsConstructor
public class FixedAsset extends TimestampedEntity {

    @NotBlank(message = "Kode aset wajib diisi")
    @Size(max = 30, message = "Kode aset maksimal 30 karakter")
    @Column(name = "asset_code", nullable = false, unique = true, length = 30)
    private String assetCode;

    @NotBlank(message = "Nama aset wajib diisi")
    @Size(max = 255, message = "Nama aset maksimal 255 karakter")
    @Column(name = "name", nullable = false)
    private String name;

    @Size(max = 500, message = "Deskripsi maksimal 500 karakter")
    @Column(name = "description", length = 500)
    private String description;

    @NotNull(message = "Kategori aset wajib diisi")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_category", nullable = false)
    private AssetCategory category;

    // Purchase information
    @NotNull(message = "Tanggal pembelian wajib diisi")
    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @NotNull(message = "Nilai perolehan wajib diisi")
    @Column(name = "purchase_cost", nullable = false, precision = 19, scale = 2)
    private BigDecimal purchaseCost;

    @Size(max = 100, message = "Supplier maksimal 100 karakter")
    @Column(name = "supplier", length = 100)
    private String supplier;

    @Size(max = 100, message = "Nomor faktur maksimal 100 karakter")
    @Column(name = "invoice_number", length = 100)
    private String invoiceNumber;

    // Depreciation settings (can override category defaults)
    @NotNull(message = "Metode penyusutan wajib diisi")
    @Enumerated(EnumType.STRING)
    @Column(name = "depreciation_method", nullable = false, length = 20)
    private DepreciationMethod depreciationMethod = DepreciationMethod.STRAIGHT_LINE;

    @NotNull(message = "Umur ekonomis (bulan) wajib diisi")
    @Min(value = 1, message = "Umur ekonomis minimal 1 bulan")
    @Max(value = 600, message = "Umur ekonomis maksimal 600 bulan (50 tahun)")
    @Column(name = "useful_life_months", nullable = false)
    private Integer usefulLifeMonths = 48;

    /**
     * Residual/salvage value at end of useful life.
     */
    @NotNull(message = "Nilai residu wajib diisi")
    @Column(name = "residual_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal residualValue = BigDecimal.ZERO;

    /**
     * Depreciation rate for declining balance method.
     * Stored as percentage (e.g., 25.00 for 25%).
     */
    @Column(name = "depreciation_rate", precision = 5, scale = 2)
    private BigDecimal depreciationRate;

    /**
     * Date when depreciation starts.
     * Usually the purchase date or first day of month after purchase.
     */
    @NotNull(message = "Tanggal mulai penyusutan wajib diisi")
    @Column(name = "depreciation_start_date", nullable = false)
    private LocalDate depreciationStartDate;

    // Current values (updated on each depreciation run)
    @Column(name = "accumulated_depreciation", nullable = false, precision = 19, scale = 2)
    private BigDecimal accumulatedDepreciation = BigDecimal.ZERO;

    @Column(name = "book_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal bookValue;

    @Column(name = "last_depreciation_date")
    private LocalDate lastDepreciationDate;

    @Column(name = "depreciation_periods_completed", nullable = false)
    private Integer depreciationPeriodsCompleted = 0;

    // Status
    @NotNull(message = "Status wajib diisi")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AssetStatus status = AssetStatus.ACTIVE;

    // Disposal information (filled when disposed)
    @Column(name = "disposal_date")
    private LocalDate disposalDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "disposal_type", length = 20)
    private DisposalType disposalType;

    @Column(name = "disposal_proceeds", precision = 19, scale = 2)
    private BigDecimal disposalProceeds;

    @Column(name = "gain_loss_on_disposal", precision = 19, scale = 2)
    private BigDecimal gainLossOnDisposal;

    @Size(max = 500, message = "Catatan disposal maksimal 500 karakter")
    @Column(name = "disposal_notes", length = 500)
    private String disposalNotes;

    // Account references (copied from category, can be overridden)
    // Note: These are set by initializeFromCategory() in FixedAssetService.create()
    // Validation is done in service after category initialization
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_asset_account", nullable = false)
    private ChartOfAccount assetAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_accumulated_depreciation_account", nullable = false)
    private ChartOfAccount accumulatedDepreciationAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_depreciation_expense_account", nullable = false)
    private ChartOfAccount depreciationExpenseAccount;

    // Reference to purchase transaction (optional)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_purchase_transaction")
    private Transaction purchaseTransaction;

    // Reference to disposal transaction (optional, filled when disposed)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_disposal_transaction")
    private Transaction disposalTransaction;

    // Depreciation entries history
    @OneToMany(mappedBy = "fixedAsset", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DepreciationEntry> depreciationEntries = new ArrayList<>();

    // Additional information
    @Size(max = 100, message = "Lokasi maksimal 100 karakter")
    @Column(name = "location", length = 100)
    private String location;

    @Size(max = 100, message = "Nomor seri maksimal 100 karakter")
    @Column(name = "serial_number", length = 100)
    private String serialNumber;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @PrePersist
    protected void onCreateInitBookValue() {
        if (this.bookValue == null) {
            this.bookValue = this.purchaseCost;
        }
    }

    /**
     * Initialize asset from category defaults.
     */
    public void initializeFromCategory(AssetCategory category) {
        this.category = category;
        this.depreciationMethod = category.getDepreciationMethod();
        this.usefulLifeMonths = category.getUsefulLifeMonths();
        this.depreciationRate = category.getDepreciationRate();
        this.assetAccount = category.getAssetAccount();
        this.accumulatedDepreciationAccount = category.getAccumulatedDepreciationAccount();
        this.depreciationExpenseAccount = category.getDepreciationExpenseAccount();
    }

    /**
     * Calculate monthly depreciation amount using straight-line method.
     */
    public BigDecimal calculateMonthlyDepreciation() {
        if (depreciationMethod == DepreciationMethod.STRAIGHT_LINE) {
            BigDecimal depreciableAmount = purchaseCost.subtract(residualValue);
            return depreciableAmount.divide(BigDecimal.valueOf(usefulLifeMonths), 2, java.math.RoundingMode.HALF_UP);
        } else {
            // Declining balance: annual rate / 12
            BigDecimal monthlyRate = depreciationRate.divide(BigDecimal.valueOf(1200), 6, java.math.RoundingMode.HALF_UP);
            return bookValue.multiply(monthlyRate).setScale(2, java.math.RoundingMode.HALF_UP);
        }
    }

    /**
     * Calculate remaining depreciation amount.
     */
    public BigDecimal getRemainingDepreciation() {
        return bookValue.subtract(residualValue).max(BigDecimal.ZERO);
    }

    /**
     * Calculate depreciable base (cost - residual value).
     */
    public BigDecimal getDepreciableBase() {
        return purchaseCost.subtract(residualValue);
    }

    /**
     * Get remaining useful life in months.
     */
    public int getRemainingUsefulLifeMonths() {
        return Math.max(0, usefulLifeMonths - depreciationPeriodsCompleted);
    }

    /**
     * Check if asset is fully depreciated.
     */
    public boolean isFullyDepreciated() {
        return bookValue.compareTo(residualValue) <= 0;
    }

    /**
     * Check if depreciation can be recorded for given period.
     */
    public boolean canRecordDepreciation(LocalDate periodEnd) {
        if (status != AssetStatus.ACTIVE) {
            return false;
        }
        if (isFullyDepreciated()) {
            return false;
        }
        if (lastDepreciationDate != null && !periodEnd.isAfter(lastDepreciationDate)) {
            return false;
        }
        return !periodEnd.isBefore(depreciationStartDate);
    }

    /**
     * Record depreciation for a period.
     */
    public void recordDepreciation(BigDecimal amount, LocalDate periodEnd) {
        this.accumulatedDepreciation = this.accumulatedDepreciation.add(amount);
        this.bookValue = this.purchaseCost.subtract(this.accumulatedDepreciation);
        this.lastDepreciationDate = periodEnd;
        this.depreciationPeriodsCompleted++;

        if (isFullyDepreciated() && status == AssetStatus.ACTIVE) {
            this.status = AssetStatus.FULLY_DEPRECIATED;
        }
    }

    /**
     * Dispose the asset.
     */
    public void dispose(DisposalType type, BigDecimal proceeds, String notes, LocalDate date) {
        this.disposalType = type;
        this.disposalProceeds = proceeds != null ? proceeds : BigDecimal.ZERO;
        this.disposalNotes = notes;
        this.disposalDate = date;
        this.status = AssetStatus.DISPOSED;

        // Calculate gain/loss: proceeds - book value
        this.gainLossOnDisposal = this.disposalProceeds.subtract(this.bookValue);
    }

    public boolean isActive() {
        return status == AssetStatus.ACTIVE;
    }

    public boolean isDisposed() {
        return status == AssetStatus.DISPOSED;
    }

    public String getDisplayName() {
        return assetCode + " - " + name;
    }

    public int getUsefulLifeYears() {
        return usefulLifeMonths / 12;
    }

    public void setUsefulLifeYears(int years) {
        this.usefulLifeMonths = years * 12;
    }

    public void addDepreciationEntry(DepreciationEntry entry) {
        depreciationEntries.add(entry);
        entry.setFixedAsset(this);
    }
}
