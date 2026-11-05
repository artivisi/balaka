package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.AssetMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AssetMovementRepository extends JpaRepository<AssetMovement, UUID> {

    @Query("SELECT m FROM AssetMovement m WHERE m.fixedAsset.id = :assetId ORDER BY m.movementDate DESC, m.createdAt DESC")
    List<AssetMovement> findByAssetId(@Param("assetId") UUID assetId);

    @Query("""
            SELECT m FROM AssetMovement m
            JOIN FETCH m.fixedAsset
            WHERE m.movementDate BETWEEN :from AND :to
            ORDER BY m.movementDate, m.createdAt""")
    List<AssetMovement> findByDateRange(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
