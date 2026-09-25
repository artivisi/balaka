package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.DraftTransaction;
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
public interface DraftTransactionRepository extends JpaRepository<DraftTransaction, UUID> {

    List<DraftTransaction> findByStatus(DraftTransaction.Status status);

    Page<DraftTransaction> findByStatusOrderByCreatedAtDesc(DraftTransaction.Status status, Pageable pageable);

    List<DraftTransaction> findByCreatedBy(String createdBy);

    Page<DraftTransaction> findByCreatedBy(String createdBy, Pageable pageable);

    @Query("SELECT d FROM DraftTransaction d WHERE d.status = :status AND d.createdBy = :createdBy")
    Page<DraftTransaction> findByStatusAndCreatedBy(
            @Param("status") DraftTransaction.Status status,
            @Param("createdBy") String createdBy,
            Pageable pageable);

    Optional<DraftTransaction> findByTelegramChatIdAndTelegramMessageId(Long chatId, Long messageId);

    long countByStatus(DraftTransaction.Status status);

    long countByCreatedByAndStatus(String createdBy, DraftTransaction.Status status);

    Page<DraftTransaction> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT d FROM DraftTransaction d WHERE d.status = 'PENDING' " +
           "AND d.transactionDate BETWEEN :startDate AND :endDate " +
           "ORDER BY d.createdAt DESC")
    List<DraftTransaction> findPendingByDateRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT d FROM DraftTransaction d WHERE d.status = 'PENDING' " +
           "AND d.overallConfidence >= :minConfidence " +
           "AND d.suggestedTemplate IS NOT NULL " +
           "ORDER BY d.overallConfidence DESC")
    List<DraftTransaction> findAutoApproveCandidates(@Param("minConfidence") java.math.BigDecimal minConfidence);

    Optional<DraftTransaction> findByTransactionId(UUID transactionId);
}
