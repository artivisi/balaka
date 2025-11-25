package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.TemplateCategory;
import com.artivisi.accountingfinance.enums.TransactionStatus;
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
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByTransactionNumber(String transactionNumber);

    List<Transaction> findByStatusOrderByTransactionDateDesc(TransactionStatus status);

    List<Transaction> findByTransactionDateBetweenOrderByTransactionDateDesc(LocalDate startDate, LocalDate endDate);

    @Query("SELECT t FROM Transaction t WHERE " +
           "(:status IS NULL OR t.status = :status) AND " +
           "(:category IS NULL OR t.journalTemplate.category = :category) AND " +
           "(:projectId IS NULL OR t.project.id = :projectId) AND " +
           "(:startDate IS NULL OR t.transactionDate >= :startDate) AND " +
           "(:endDate IS NULL OR t.transactionDate <= :endDate) " +
           "ORDER BY t.transactionDate DESC, t.transactionNumber DESC")
    Page<Transaction> findByFilters(
            @Param("status") TransactionStatus status,
            @Param("category") TemplateCategory category,
            @Param("projectId") UUID projectId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE " +
           "(LOWER(t.transactionNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(t.referenceNumber) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY t.transactionDate DESC")
    Page<Transaction> searchTransactions(@Param("search") String search, Pageable pageable);

    @Query("SELECT t FROM Transaction t LEFT JOIN FETCH t.journalEntries WHERE t.id = :id")
    Optional<Transaction> findByIdWithJournalEntries(@Param("id") UUID id);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.status = :status")
    long countByStatus(@Param("status") TransactionStatus status);

    @Query("SELECT t FROM Transaction t WHERE t.status = 'POSTED' AND " +
           "t.transactionDate BETWEEN :startDate AND :endDate ORDER BY t.transactionDate DESC")
    List<Transaction> findPostedTransactionsBetweenDates(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
