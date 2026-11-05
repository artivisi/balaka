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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** One change of custodian, location or condition. Fields that did not change hold the same value on both sides. */
@Entity
@Table(name = "asset_movements")
@Getter
@Setter
@NoArgsConstructor
public class AssetMovement extends TimestampedEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_fixed_asset", nullable = false)
    private FixedAsset fixedAsset;

    @NotNull
    @Column(name = "movement_date", nullable = false)
    private LocalDate movementDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private AssetMovementSource source;

    @Column(name = "from_custodian", length = 150)
    private String fromCustodian;

    @Column(name = "to_custodian", length = 150)
    private String toCustodian;

    @Column(name = "from_location", length = 100)
    private String fromLocation;

    @Column(name = "to_location", length = 100)
    private String toLocation;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_condition", length = 20)
    private AssetCondition fromCondition;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_condition", length = 20)
    private AssetCondition toCondition;

    @NotBlank
    @Column(name = "recorded_by", nullable = false, length = 100)
    private String recordedBy;
}
