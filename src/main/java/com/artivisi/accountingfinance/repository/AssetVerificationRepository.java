package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.AssetVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetVerificationRepository extends JpaRepository<AssetVerification, UUID> {

    Optional<AssetVerification> findByFixedAssetIdAndVerificationYear(UUID fixedAssetId, Integer verificationYear);

    @Query("SELECT v FROM AssetVerification v WHERE v.verificationYear = :year")
    List<AssetVerification> findByYear(@Param("year") int year);

    @Query("SELECT v FROM AssetVerification v WHERE v.fixedAsset.id = :assetId ORDER BY v.verificationYear DESC")
    List<AssetVerification> findByAssetId(@Param("assetId") UUID assetId);
}
