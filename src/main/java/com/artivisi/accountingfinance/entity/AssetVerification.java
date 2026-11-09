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

/** Result of the yearly physical check of one asset. A second check in the same year replaces the first. */
@Entity
@Table(name = "asset_verifications")
@Getter
@Setter
@NoArgsConstructor
public class AssetVerification extends TimestampedEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_fixed_asset", nullable = false)
    private FixedAsset fixedAsset;

    @NotNull
    @Column(name = "verification_year", nullable = false)
    private Integer verificationYear;

    @NotNull
    @Column(name = "verified_date", nullable = false)
    private LocalDate verifiedDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 20)
    private AssetVerificationResult result;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_condition", length = 20)
    private AssetCondition condition;

    @Column(name = "location", length = 100)
    private String location;

    @Column(name = "custodian", length = 150)
    private String custodian;

    @Column(name = "notes", length = 500)
    private String notes;

    @NotBlank
    @Column(name = "verified_by", nullable = false, length = 100)
    private String verifiedBy;
}
