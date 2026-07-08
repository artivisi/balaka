package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.AssetCategory;
import com.artivisi.accountingfinance.entity.AssetStatus;
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
public interface FixedAssetRepository extends JpaRepository<FixedAsset, UUID> {

    Optional<FixedAsset> findByAssetCode(String assetCode);

    boolean existsByAssetCode(String assetCode);

    List<FixedAsset> findByStatus(AssetStatus status);

    List<FixedAsset> findByCategory(AssetCategory category);

    @Query("SELECT a FROM FixedAsset a WHERE a.status = 'ACTIVE' ORDER BY a.assetCode")
    List<FixedAsset> findAllActive();

    @Query("SELECT a FROM FixedAsset a WHERE " +
           "a.status = 'ACTIVE' AND " +
           "(a.lastDepreciationDate IS NULL OR a.lastDepreciationDate < :periodEnd) AND " +
           "a.depreciationStartDate <= :periodEnd " +
           "ORDER BY a.assetCode")
    List<FixedAsset> findAssetsNeedingDepreciation(@Param("periodEnd") LocalDate periodEnd);

    @Query("SELECT a FROM FixedAsset a " +
           "LEFT JOIN FETCH a.category " +
           "WHERE a.id = :id")
    Optional<FixedAsset> findByIdWithCategory(@Param("id") UUID id);

    @Query("SELECT a FROM FixedAsset a " +
           "LEFT JOIN FETCH a.category " +
           "LEFT JOIN FETCH a.assetAccount " +
           "LEFT JOIN FETCH a.accumulatedDepreciationAccount " +
           "LEFT JOIN FETCH a.depreciationExpenseAccount " +
           "WHERE a.id = :id")
    Optional<FixedAsset> findByIdWithDetails(@Param("id") UUID id);

    @Query("SELECT a FROM FixedAsset a " +
           "LEFT JOIN FETCH a.category " +
           "WHERE " +
           "(LOWER(a.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(a.assetCode) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:status IS NULL OR a.status = :status) " +
           "AND (:categoryId IS NULL OR a.category.id = :categoryId) " +
           "ORDER BY a.assetCode")
    Page<FixedAsset> findBySearch(
            @Param("search") String search,
            @Param("status") AssetStatus status,
            @Param("categoryId") UUID categoryId,
            Pageable pageable);

    @Query("SELECT a FROM FixedAsset a " +
           "LEFT JOIN FETCH a.category " +
           "WHERE " +
           "(:status IS NULL OR a.status = :status) " +
           "AND (:categoryId IS NULL OR a.category.id = :categoryId) " +
           "ORDER BY a.assetCode")
    Page<FixedAsset> findByFilters(
            @Param("status") AssetStatus status,
            @Param("categoryId") UUID categoryId,
            Pageable pageable);

    // Date params are mandatory: PostgreSQL cannot type-infer ":param IS NULL" for date binds
    @Query("SELECT a FROM FixedAsset a " +
           "LEFT JOIN FETCH a.category " +
           "WHERE " +
           "(:status IS NULL OR a.status = :status) " +
           "AND (:categoryId IS NULL OR a.category.id = :categoryId) " +
           "AND a.purchaseDate BETWEEN :purchasedFrom AND :purchasedUntil " +
           "ORDER BY a.assetCode")
    Page<FixedAsset> findByFiltersAndPurchaseDateRange(
            @Param("status") AssetStatus status,
            @Param("categoryId") UUID categoryId,
            @Param("purchasedFrom") LocalDate purchasedFrom,
            @Param("purchasedUntil") LocalDate purchasedUntil,
            Pageable pageable);

    @Query("SELECT COUNT(a) FROM FixedAsset a WHERE a.status = 'ACTIVE'")
    long countActiveAssets();

    @Query("SELECT COUNT(a) FROM FixedAsset a WHERE a.status = 'FULLY_DEPRECIATED'")
    long countFullyDepreciatedAssets();

    @Query("SELECT SUM(a.bookValue) FROM FixedAsset a WHERE a.status IN ('ACTIVE', 'FULLY_DEPRECIATED')")
    java.math.BigDecimal sumBookValue();

    @Query("SELECT SUM(a.purchaseCost) FROM FixedAsset a WHERE a.status IN ('ACTIVE', 'FULLY_DEPRECIATED')")
    java.math.BigDecimal sumPurchaseCost();

    @Query("SELECT SUM(a.accumulatedDepreciation) FROM FixedAsset a WHERE a.status IN ('ACTIVE', 'FULLY_DEPRECIATED')")
    java.math.BigDecimal sumAccumulatedDepreciation();
}
