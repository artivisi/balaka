package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.JournalEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

    Optional<JournalEntry> findByJournalNumber(String journalNumber);

    List<JournalEntry> findByTransactionIdOrderByJournalNumberAsc(UUID transactionId);

    @Query("SELECT j FROM JournalEntry j JOIN j.transaction t WHERE " +
           "j.account.id = :accountId AND " +
           "t.transactionDate BETWEEN :startDate AND :endDate " +
           "ORDER BY t.transactionDate, j.journalNumber")
    List<JournalEntry> findByAccountIdAndDateRange(
            @Param("accountId") UUID accountId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT j FROM JournalEntry j JOIN j.transaction t WHERE " +
           "j.account.id = :accountId AND t.status = 'POSTED' AND " +
           "t.transactionDate BETWEEN :startDate AND :endDate " +
           "ORDER BY t.transactionDate, j.journalNumber")
    List<JournalEntry> findPostedEntriesByAccountAndDateRange(
            @Param("accountId") UUID accountId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT j FROM JournalEntry j JOIN j.transaction t WHERE " +
           "j.account.id = :accountId AND t.status = 'POSTED' AND " +
           "t.transactionDate BETWEEN :startDate AND :endDate " +
           "ORDER BY t.transactionDate, j.journalNumber")
    Page<JournalEntry> findPostedEntriesByAccountAndDateRangePaged(
            @Param("accountId") UUID accountId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);

    @Query("SELECT j FROM JournalEntry j JOIN j.transaction t WHERE " +
           "j.account.id = :accountId AND t.status = 'POSTED' AND " +
           "t.transactionDate BETWEEN :startDate AND :endDate AND " +
           "(LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(j.journalNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(t.referenceNumber) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY t.transactionDate, j.journalNumber")
    Page<JournalEntry> findPostedEntriesByAccountAndDateRangeAndSearchPaged(
            @Param("accountId") UUID accountId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("search") String search,
            Pageable pageable);

    @Query("SELECT COUNT(j) FROM JournalEntry j JOIN j.transaction t WHERE " +
           "j.account.id = :accountId AND t.status = 'POSTED' AND " +
           "t.transactionDate BETWEEN :startDate AND :endDate")
    long countPostedEntriesByAccountAndDateRange(
            @Param("accountId") UUID accountId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT j FROM JournalEntry j JOIN j.transaction t WHERE " +
           "t.status = 'POSTED' AND t.transactionDate BETWEEN :startDate AND :endDate " +
           "ORDER BY t.transactionDate, j.journalNumber")
    Page<JournalEntry> findAllPostedEntriesByDateRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);

    @Query("SELECT COALESCE(SUM(j.debitAmount), 0) FROM JournalEntry j JOIN j.transaction t " +
           "WHERE j.account.id = :accountId AND t.status = 'POSTED' AND t.transactionDate < :date")
    BigDecimal sumDebitBeforeDate(@Param("accountId") UUID accountId, @Param("date") LocalDate date);

    @Query("SELECT COALESCE(SUM(j.creditAmount), 0) FROM JournalEntry j JOIN j.transaction t " +
           "WHERE j.account.id = :accountId AND t.status = 'POSTED' AND t.transactionDate < :date")
    BigDecimal sumCreditBeforeDate(@Param("accountId") UUID accountId, @Param("date") LocalDate date);

    @Query("SELECT COALESCE(SUM(j.debitAmount), 0) FROM JournalEntry j JOIN j.transaction t " +
           "WHERE j.account.id = :accountId AND t.status = 'POSTED' AND " +
           "t.transactionDate BETWEEN :startDate AND :endDate")
    BigDecimal sumDebitByAccountAndDateRange(
            @Param("accountId") UUID accountId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT COALESCE(SUM(j.creditAmount), 0) FROM JournalEntry j JOIN j.transaction t " +
           "WHERE j.account.id = :accountId AND t.status = 'POSTED' AND " +
           "t.transactionDate BETWEEN :startDate AND :endDate")
    BigDecimal sumCreditByAccountAndDateRange(
            @Param("accountId") UUID accountId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    List<JournalEntry> findAllByJournalNumberOrderByIdAsc(String journalNumber);

    @Query("SELECT j FROM JournalEntry j LEFT JOIN FETCH j.account WHERE j.journalNumber = :journalNumber ORDER BY j.id ASC")
    List<JournalEntry> findAllByJournalNumberWithAccount(@Param("journalNumber") String journalNumber);

    boolean existsByJournalNumber(String journalNumber);

    boolean existsByAccountId(UUID accountId);

    @Query(value = "SELECT MAX(CAST(SUBSTRING(journal_number, 9, 4) AS INTEGER)) FROM journal_entries " +
           "WHERE journal_number LIKE :prefix", nativeQuery = true)
    Integer findMaxSequenceByPrefix(@Param("prefix") String prefix);

    // Project profitability queries
    @Query("SELECT COALESCE(SUM(j.debitAmount), 0) FROM JournalEntry j JOIN j.transaction t " +
           "WHERE j.project.id = :projectId AND j.account.id = :accountId AND t.status = 'POSTED' AND " +
           "t.transactionDate BETWEEN :startDate AND :endDate")
    BigDecimal sumDebitByProjectAndAccountAndDateRange(
            @Param("projectId") UUID projectId,
            @Param("accountId") UUID accountId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT COALESCE(SUM(j.creditAmount), 0) FROM JournalEntry j JOIN j.transaction t " +
           "WHERE j.project.id = :projectId AND j.account.id = :accountId AND t.status = 'POSTED' AND " +
           "t.transactionDate BETWEEN :startDate AND :endDate")
    BigDecimal sumCreditByProjectAndAccountAndDateRange(
            @Param("projectId") UUID projectId,
            @Param("accountId") UUID accountId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // Fiscal year closing queries
    @Query("SELECT COUNT(j) FROM JournalEntry j JOIN j.transaction t WHERE t.referenceNumber LIKE :pattern AND t.status = 'POSTED'")
    long countByReferenceNumberLike(@Param("pattern") String pattern);

    @Query("SELECT j FROM JournalEntry j JOIN j.transaction t WHERE t.referenceNumber LIKE :pattern ORDER BY t.referenceNumber, j.id")
    List<JournalEntry> findByReferenceNumberLike(@Param("pattern") String pattern);
}
