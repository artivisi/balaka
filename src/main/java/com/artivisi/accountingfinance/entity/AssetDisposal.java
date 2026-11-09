package com.artivisi.accountingfinance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Generated disposal record. Amounts are a snapshot at creation; the register stays locked until posted or cancelled. */
@Entity
@Table(name = "asset_disposals")
@Getter
@Setter
@NoArgsConstructor
public class AssetDisposal extends TimestampedEntity {

    @NotBlank
    @Column(name = "record_number", nullable = false, unique = true, length = 30)
    private String recordNumber;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_fixed_asset", nullable = false)
    private FixedAsset fixedAsset;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "disposal_type", nullable = false, length = 20)
    private DisposalType disposalType;

    @NotNull
    @Column(name = "disposal_date", nullable = false)
    private LocalDate disposalDate;

    @NotBlank
    @Size(max = 500, message = "Alasan maksimal 500 karakter")
    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Size(max = 255, message = "Nama pembeli/penerima maksimal 255 karakter")
    @Column(name = "counterparty")
    private String counterparty;

    @NotNull
    @Column(name = "proceeds", nullable = false, precision = 19, scale = 2)
    private BigDecimal proceeds;

    @NotNull
    @Column(name = "purchase_cost", nullable = false, precision = 19, scale = 2)
    private BigDecimal purchaseCost;

    @NotNull
    @Column(name = "accumulated_depreciation", nullable = false, precision = 19, scale = 2)
    private BigDecimal accumulatedDepreciation;

    @NotNull
    @Column(name = "book_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal bookValue;

    @NotNull
    @Column(name = "gain_loss", nullable = false, precision = 19, scale = 2)
    private BigDecimal gainLoss;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_proceeds_account")
    private ChartOfAccount proceedsAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_transaction")
    private Transaction transaction;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AssetDisposalStatus status;

    @NotBlank
    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    public boolean isDraft() {
        return status == AssetDisposalStatus.DRAFT;
    }
}
