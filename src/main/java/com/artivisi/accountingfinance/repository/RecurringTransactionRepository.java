package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.RecurringTransaction;
import com.artivisi.accountingfinance.enums.RecurringStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, UUID> {

    @Query("SELECT r FROM RecurringTransaction r JOIN FETCH r.journalTemplate WHERE r.status = :status")
    Page<RecurringTransaction> findByStatus(RecurringStatus status, Pageable pageable);

    @Query("SELECT r FROM RecurringTransaction r JOIN FETCH r.journalTemplate")
    Page<RecurringTransaction> findAllWithTemplate(Pageable pageable);

    List<RecurringTransaction> findByStatusAndNextRunDateLessThanEqual(RecurringStatus status, LocalDate date);

    long countByStatus(RecurringStatus status);

    @Query("SELECT r FROM RecurringTransaction r JOIN FETCH r.journalTemplate WHERE r.id = :id")
    RecurringTransaction findByIdWithTemplate(UUID id);

    @Query("SELECT r FROM RecurringTransaction r JOIN FETCH r.journalTemplate LEFT JOIN FETCH r.accountMappings WHERE r.id = :id")
    RecurringTransaction findByIdWithMappings(UUID id);

    @Query("SELECT r FROM RecurringTransaction r WHERE r.status = :status AND r.nextRunDate BETWEEN :start AND :end")
    List<RecurringTransaction> findUpcoming(RecurringStatus status, LocalDate start, LocalDate end);
}
