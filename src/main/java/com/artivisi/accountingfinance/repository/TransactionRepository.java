package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.Transaction;
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

    @Query(value = "SELECT t.* FROM transactions t " +
           "JOIN journal_templates jt ON jt.id = t.id_journal_template " +
           "WHERE (CAST(:status AS VARCHAR) IS NULL OR t.status = CAST(:status AS VARCHAR)) " +
           "AND (CAST(:category AS VARCHAR) IS NULL OR jt.category = CAST(:category AS VARCHAR)) " +
           "AND (CAST(:projectId AS UUID) IS NULL OR t.id_project = CAST(:projectId AS UUID)) " +
           "AND (CAST(:startDate AS DATE) IS NULL OR t.transaction_date >= CAST(:startDate AS DATE)) " +
           "AND (CAST(:endDate AS DATE) IS NULL OR t.transaction_date <= CAST(:endDate AS DATE)) " +
           "ORDER BY t.transaction_date DESC, t.created_at DESC",
           countQuery = "SELECT COUNT(*) FROM transactions t " +
           "JOIN journal_templates jt ON jt.id = t.id_journal_template " +
           "WHERE (CAST(:status AS VARCHAR) IS NULL OR t.status = CAST(:status AS VARCHAR)) " +
           "AND (CAST(:category AS VARCHAR) IS NULL OR jt.category = CAST(:category AS VARCHAR)) " +
           "AND (CAST(:projectId AS UUID) IS NULL OR t.id_project = CAST(:projectId AS UUID)) " +
           "AND (CAST(:startDate AS DATE) IS NULL OR t.transaction_date >= CAST(:startDate AS DATE)) " +
           "AND (CAST(:endDate AS DATE) IS NULL OR t.transaction_date <= CAST(:endDate AS DATE))",
           nativeQuery = true)
    Page<Transaction> findByFilters(
            @Param("status") String status,
            @Param("category") String category,
            @Param("projectId") UUID projectId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);

    @Query(value = "SELECT DISTINCT t.* FROM transactions t " +
           "JOIN journal_templates jt ON jt.id = t.id_journal_template " +
           "LEFT JOIN transaction_tags tt ON tt.id_transaction = t.id " +
           "WHERE (CAST(:status AS VARCHAR) IS NULL OR t.status = CAST(:status AS VARCHAR)) " +
           "AND (CAST(:category AS VARCHAR) IS NULL OR jt.category = CAST(:category AS VARCHAR)) " +
           "AND (CAST(:projectId AS UUID) IS NULL OR t.id_project = CAST(:projectId AS UUID)) " +
           "AND (CAST(:tagId AS UUID) IS NULL OR tt.id_tag = CAST(:tagId AS UUID)) " +
           "AND (CAST(:startDate AS DATE) IS NULL OR t.transaction_date >= CAST(:startDate AS DATE)) " +
           "AND (CAST(:endDate AS DATE) IS NULL OR t.transaction_date <= CAST(:endDate AS DATE)) " +
           "ORDER BY t.transaction_date DESC, t.created_at DESC",
           countQuery = "SELECT COUNT(DISTINCT t.id) FROM transactions t " +
           "JOIN journal_templates jt ON jt.id = t.id_journal_template " +
           "LEFT JOIN transaction_tags tt ON tt.id_transaction = t.id " +
           "WHERE (CAST(:status AS VARCHAR) IS NULL OR t.status = CAST(:status AS VARCHAR)) " +
           "AND (CAST(:category AS VARCHAR) IS NULL OR jt.category = CAST(:category AS VARCHAR)) " +
           "AND (CAST(:projectId AS UUID) IS NULL OR t.id_project = CAST(:projectId AS UUID)) " +
           "AND (CAST(:tagId AS UUID) IS NULL OR tt.id_tag = CAST(:tagId AS UUID)) " +
           "AND (CAST(:startDate AS DATE) IS NULL OR t.transaction_date >= CAST(:startDate AS DATE)) " +
           "AND (CAST(:endDate AS DATE) IS NULL OR t.transaction_date <= CAST(:endDate AS DATE))",
           nativeQuery = true)
    Page<Transaction> findByFiltersWithTag(
            @Param("status") String status,
            @Param("category") String category,
            @Param("projectId") UUID projectId,
            @Param("tagId") UUID tagId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE " +
           "(LOWER(t.transactionNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(t.referenceNumber) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY t.transactionDate DESC, t.createdAt DESC")
    Page<Transaction> searchTransactions(@Param("search") String search, Pageable pageable);

    @Query("SELECT t FROM Transaction t LEFT JOIN FETCH t.journalEntries WHERE t.id = :id")
    Optional<Transaction> findByIdWithJournalEntries(@Param("id") UUID id);

    @Query("SELECT DISTINCT t FROM Transaction t " +
           "LEFT JOIN FETCH t.accountMappings am " +
           "LEFT JOIN FETCH am.templateLine " +
           "LEFT JOIN FETCH am.account " +
           "WHERE t.id = :id")
    Optional<Transaction> findByIdWithMappingsAndVariables(@Param("id") UUID id);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.status = :status")
    long countByStatus(@Param("status") TransactionStatus status);

    long countByTransactionDateBetween(LocalDate startDate, LocalDate endDate);

    @Query("SELECT t FROM Transaction t WHERE t.status = 'POSTED' AND " +
           "t.transactionDate BETWEEN :startDate AND :endDate ORDER BY t.transactionDate DESC")
    List<Transaction> findPostedTransactionsBetweenDates(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    boolean existsByJournalTemplateId(UUID templateId);

    long countByStatusAndTransactionDateBetween(TransactionStatus status, LocalDate startDate, LocalDate endDate);

    List<Transaction> findByStatusAndTransactionDateBeforeOrderByTransactionDateAsc(
            TransactionStatus status, LocalDate before);
}
