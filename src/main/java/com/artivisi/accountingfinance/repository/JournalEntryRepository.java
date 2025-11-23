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

    List<JournalEntry> findByAccountIdAndJournalDateBetweenOrderByJournalDateAscJournalNumberAsc(
            UUID accountId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT j FROM JournalEntry j JOIN j.transaction t WHERE " +
           "j.account.id = :accountId AND t.status = 'POSTED' AND " +
           "j.journalDate BETWEEN :startDate AND :endDate " +
           "ORDER BY j.journalDate, j.journalNumber")
    List<JournalEntry> findPostedEntriesByAccountAndDateRange(
            @Param("accountId") UUID accountId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT j FROM JournalEntry j JOIN j.transaction t WHERE " +
           "t.status = 'POSTED' AND j.journalDate BETWEEN :startDate AND :endDate " +
           "ORDER BY j.journalDate, j.journalNumber")
    Page<JournalEntry> findAllPostedEntriesByDateRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);

    @Query("SELECT COALESCE(SUM(j.debitAmount), 0) FROM JournalEntry j JOIN j.transaction t " +
           "WHERE j.account.id = :accountId AND t.status = 'POSTED' AND j.journalDate < :date")
    BigDecimal sumDebitBeforeDate(@Param("accountId") UUID accountId, @Param("date") LocalDate date);

    @Query("SELECT COALESCE(SUM(j.creditAmount), 0) FROM JournalEntry j JOIN j.transaction t " +
           "WHERE j.account.id = :accountId AND t.status = 'POSTED' AND j.journalDate < :date")
    BigDecimal sumCreditBeforeDate(@Param("accountId") UUID accountId, @Param("date") LocalDate date);

    @Query("SELECT COALESCE(SUM(j.debitAmount), 0) FROM JournalEntry j JOIN j.transaction t " +
           "WHERE j.account.id = :accountId AND t.status = 'POSTED' AND " +
           "j.journalDate BETWEEN :startDate AND :endDate")
    BigDecimal sumDebitByAccountAndDateRange(
            @Param("accountId") UUID accountId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT COALESCE(SUM(j.creditAmount), 0) FROM JournalEntry j JOIN j.transaction t " +
           "WHERE j.account.id = :accountId AND t.status = 'POSTED' AND " +
           "j.journalDate BETWEEN :startDate AND :endDate")
    BigDecimal sumCreditByAccountAndDateRange(
            @Param("accountId") UUID accountId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
