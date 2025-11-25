package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.AmortizationEntry;
import com.artivisi.accountingfinance.enums.AmortizationEntryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AmortizationEntryRepository extends JpaRepository<AmortizationEntry, UUID> {

    List<AmortizationEntry> findByScheduleIdOrderByPeriodNumberAsc(UUID scheduleId);

    List<AmortizationEntry> findByScheduleIdAndStatus(UUID scheduleId, AmortizationEntryStatus status);

    Optional<AmortizationEntry> findByScheduleIdAndPeriodNumber(UUID scheduleId, Integer periodNumber);

    @Query("SELECT e FROM AmortizationEntry e JOIN e.schedule s WHERE " +
           "e.status = 'PENDING' AND e.periodEnd <= :date AND s.status = 'ACTIVE' " +
           "ORDER BY e.periodEnd ASC, s.code ASC")
    List<AmortizationEntry> findPendingEntriesDueByDate(@Param("date") LocalDate date);

    @Query("SELECT e FROM AmortizationEntry e JOIN e.schedule s WHERE " +
           "e.status = 'PENDING' AND e.periodEnd <= :date AND s.status = 'ACTIVE' AND s.autoPost = true " +
           "ORDER BY e.periodEnd ASC, s.code ASC")
    List<AmortizationEntry> findPendingAutoPostEntriesDueByDate(@Param("date") LocalDate date);

    @Query("SELECT COUNT(e) FROM AmortizationEntry e JOIN e.schedule s WHERE " +
           "e.status = 'PENDING' AND s.status = 'ACTIVE'")
    long countPendingEntries();

    @Query("SELECT COUNT(e) FROM AmortizationEntry e WHERE e.schedule.id = :scheduleId AND e.status = 'POSTED'")
    long countPostedEntriesByScheduleId(@Param("scheduleId") UUID scheduleId);

    @Query("SELECT COUNT(e) FROM AmortizationEntry e WHERE e.schedule.id = :scheduleId AND e.status = :status")
    long countByScheduleIdAndStatus(@Param("scheduleId") UUID scheduleId, @Param("status") AmortizationEntryStatus status);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM AmortizationEntry e WHERE e.schedule.id = :scheduleId AND e.status = 'POSTED'")
    java.math.BigDecimal sumPostedAmountByScheduleId(@Param("scheduleId") UUID scheduleId);

    void deleteByScheduleId(UUID scheduleId);
}
