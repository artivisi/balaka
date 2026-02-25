package com.artivisi.accountingfinance.entity;

import com.artivisi.accountingfinance.enums.FiscalAdjustmentCategory;
import com.artivisi.accountingfinance.enums.FiscalAdjustmentDirection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "fiscal_adjustments")
@Getter
@Setter
@NoArgsConstructor
public class FiscalAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull(message = "Tahun wajib diisi")
    @Column(name = "year", nullable = false)
    private Integer year;

    @NotBlank(message = "Deskripsi wajib diisi")
    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @NotNull(message = "Kategori koreksi wajib diisi")
    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_category", nullable = false, length = 20)
    private FiscalAdjustmentCategory adjustmentCategory;

    @NotNull(message = "Arah koreksi wajib diisi")
    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_direction", nullable = false, length = 20)
    private FiscalAdjustmentDirection adjustmentDirection;

    @NotNull(message = "Jumlah wajib diisi")
    @Positive(message = "Jumlah harus positif")
    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "account_code", length = 20)
    private String accountCode;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

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
}
