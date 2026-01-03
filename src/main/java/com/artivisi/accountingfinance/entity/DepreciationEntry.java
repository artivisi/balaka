package com.artivisi.accountingfinance.entity;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Records each depreciation entry for a fixed asset.
 */
@Entity
@Table(name = "depreciation_entries")
@Getter
@Setter
@NoArgsConstructor
public class DepreciationEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull(message = "Aset wajib diisi")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_fixed_asset", nullable = false)
    private FixedAsset fixedAsset;

    @NotNull(message = "Nomor periode wajib diisi")
    @Column(name = "period_number", nullable = false)
    private Integer periodNumber;

    @NotNull(message = "Tanggal mulai periode wajib diisi")
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @NotNull(message = "Tanggal akhir periode wajib diisi")
    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @NotNull(message = "Jumlah penyusutan wajib diisi")
    @Column(name = "depreciation_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal depreciationAmount;

    /**
     * Accumulated depreciation after this entry.
     */
    @NotNull
    @Column(name = "accumulated_depreciation", nullable = false, precision = 19, scale = 2)
    private BigDecimal accumulatedDepreciation;

    /**
     * Book value after this entry.
     */
    @NotNull
    @Column(name = "book_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal bookValue;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DepreciationEntryStatus status = DepreciationEntryStatus.PENDING;

    // Reference to transaction created when posted
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_transaction")
    private Transaction transaction;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @Column(name = "posted_at")
    private LocalDateTime postedAt;

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

    public boolean isPending() {
        return status == DepreciationEntryStatus.PENDING;
    }

    public boolean isPosted() {
        return status == DepreciationEntryStatus.POSTED;
    }

    public boolean isSkipped() {
        return status == DepreciationEntryStatus.SKIPPED;
    }

    /**
     * Get period display name (e.g., "Januari 2025").
     */
    public String getPeriodDisplayName() {
        return periodEnd.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.of("id"))
                + " " + periodEnd.getYear();
    }
}
