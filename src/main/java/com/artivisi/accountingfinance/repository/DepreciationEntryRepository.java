package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.DepreciationEntry;
import com.artivisi.accountingfinance.entity.DepreciationEntryStatus;
import com.artivisi.accountingfinance.entity.FixedAsset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DepreciationEntryRepository extends JpaRepository<DepreciationEntry, UUID> {

    List<DepreciationEntry> findByFixedAsset(FixedAsset fixedAsset);

    List<DepreciationEntry> findByFixedAssetOrderByPeriodNumberAsc(FixedAsset fixedAsset);

    List<DepreciationEntry> findByFixedAssetIdOrderByPeriodNumberAsc(UUID fixedAssetId);

    @Query("SELECT e FROM DepreciationEntry e " +
           "LEFT JOIN FETCH e.fixedAsset " +
           "WHERE e.fixedAsset.id = :assetId " +
           "ORDER BY e.periodNumber ASC")
    List<DepreciationEntry> findByAssetIdWithAsset(@Param("assetId") UUID assetId);

    @Query("SELECT e FROM DepreciationEntry e " +
           "LEFT JOIN FETCH e.fixedAsset " +
           "WHERE e.status = :status " +
           "ORDER BY e.periodEnd ASC")
    List<DepreciationEntry> findByStatusWithAsset(@Param("status") DepreciationEntryStatus status);

    List<DepreciationEntry> findByStatus(DepreciationEntryStatus status);

    @Query("SELECT e FROM DepreciationEntry e WHERE e.status = 'PENDING' ORDER BY e.periodEnd ASC")
    List<DepreciationEntry> findAllPending();

    @Query("SELECT e FROM DepreciationEntry e " +
           "LEFT JOIN FETCH e.fixedAsset a " +
           "LEFT JOIN FETCH a.category " +
           "WHERE e.status = 'PENDING' " +
           "ORDER BY e.periodEnd ASC, a.assetCode ASC")
    List<DepreciationEntry> findAllPendingWithDetails();

    @Query("SELECT e FROM DepreciationEntry e WHERE " +
           "e.fixedAsset.id = :assetId AND " +
           "e.periodEnd = :periodEnd")
    Optional<DepreciationEntry> findByAssetIdAndPeriodEnd(
            @Param("assetId") UUID assetId,
            @Param("periodEnd") LocalDate periodEnd);

    @Query("SELECT e FROM DepreciationEntry e WHERE " +
           "e.periodEnd >= :startDate AND e.periodEnd <= :endDate " +
           "AND (:status IS NULL OR e.status = :status) " +
           "ORDER BY e.periodEnd ASC")
    Page<DepreciationEntry> findByPeriodRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("status") DepreciationEntryStatus status,
            Pageable pageable);

    @Query("SELECT e FROM DepreciationEntry e " +
           "JOIN FETCH e.fixedAsset " +
           "WHERE e.periodEnd = :periodEnd " +
           "AND (:status IS NULL OR e.status = :status) " +
           "ORDER BY e.periodEnd ASC, e.periodNumber ASC")
    List<DepreciationEntry> findByPeriodEndAndOptionalStatus(
            @Param("periodEnd") LocalDate periodEnd,
            @Param("status") DepreciationEntryStatus status);

    @Query("SELECT e FROM DepreciationEntry e " +
           "JOIN FETCH e.fixedAsset " +
           "WHERE (:status IS NULL OR e.status = :status) " +
           "ORDER BY e.periodEnd ASC, e.periodNumber ASC")
    List<DepreciationEntry> findAllByOptionalStatus(@Param("status") DepreciationEntryStatus status);

    @Query("SELECT COUNT(e) FROM DepreciationEntry e WHERE e.status = 'PENDING'")
    long countPending();

    @Query("SELECT SUM(e.depreciationAmount) FROM DepreciationEntry e " +
           "WHERE e.status = 'POSTED' AND e.periodEnd >= :startDate AND e.periodEnd <= :endDate")
    java.math.BigDecimal sumDepreciationForPeriod(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    void deleteByFixedAsset(FixedAsset fixedAsset);
}
