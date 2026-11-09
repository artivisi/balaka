package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.AssetDisposal;
import com.artivisi.accountingfinance.entity.AssetDisposalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetDisposalRepository extends JpaRepository<AssetDisposal, UUID> {

    Optional<AssetDisposal> findByTransactionId(UUID transactionId);

    boolean existsByFixedAssetId(UUID fixedAssetId);

    boolean existsByFixedAssetIdAndStatus(UUID fixedAssetId, AssetDisposalStatus status);

    @Query("""
            SELECT d FROM AssetDisposal d
            JOIN FETCH d.fixedAsset a
            LEFT JOIN FETCH a.category
            LEFT JOIN FETCH d.proceedsAccount
            LEFT JOIN FETCH d.transaction
            WHERE d.id = :id""")
    Optional<AssetDisposal> findByIdWithDetails(@Param("id") UUID id);

    @Query("""
            SELECT d FROM AssetDisposal d
            LEFT JOIN FETCH d.transaction
            WHERE d.fixedAsset.id = :assetId
            ORDER BY d.createdAt DESC""")
    List<AssetDisposal> findByAssetId(@Param("assetId") UUID assetId);

    @Query("""
            SELECT d FROM AssetDisposal d
            JOIN FETCH d.fixedAsset a
            LEFT JOIN FETCH a.category
            LEFT JOIN FETCH d.proceedsAccount
            LEFT JOIN FETCH d.transaction
            WHERE d.disposalDate BETWEEN :from AND :to
              AND (:status IS NULL OR d.status = :status)
            ORDER BY d.disposalDate, d.recordNumber""")
    List<AssetDisposal> findByDateRange(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                        @Param("status") AssetDisposalStatus status);
}
