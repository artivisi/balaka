package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.AmortizationSchedule;
import com.artivisi.accountingfinance.enums.ScheduleStatus;
import com.artivisi.accountingfinance.enums.ScheduleType;
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
public interface AmortizationScheduleRepository extends JpaRepository<AmortizationSchedule, UUID> {

    Optional<AmortizationSchedule> findByCode(String code);

    boolean existsByCode(String code);

    List<AmortizationSchedule> findByStatus(ScheduleStatus status);

    List<AmortizationSchedule> findByScheduleType(ScheduleType scheduleType);

    List<AmortizationSchedule> findByStatusAndScheduleType(ScheduleStatus status, ScheduleType scheduleType);

    @Query("SELECT s FROM AmortizationSchedule s WHERE s.status = 'ACTIVE' " +
           "AND s.startDate <= :date AND s.endDate >= :date")
    List<AmortizationSchedule> findActiveSchedulesForDate(@Param("date") LocalDate date);

    @Query("SELECT s FROM AmortizationSchedule s WHERE s.status = 'ACTIVE' AND s.autoPost = true " +
           "AND s.startDate <= :date AND s.endDate >= :date")
    List<AmortizationSchedule> findActiveAutoPostSchedulesForDate(@Param("date") LocalDate date);

    Page<AmortizationSchedule> findAllByOrderByCodeAsc(Pageable pageable);

    @Query("SELECT s FROM AmortizationSchedule s WHERE " +
           "(:status IS NULL OR s.status = :status) AND " +
           "(:scheduleType IS NULL OR s.scheduleType = :scheduleType) " +
           "ORDER BY s.code ASC")
    Page<AmortizationSchedule> findByFilters(
            @Param("status") ScheduleStatus status,
            @Param("scheduleType") ScheduleType scheduleType,
            Pageable pageable);

    @Query("SELECT s FROM AmortizationSchedule s WHERE " +
           "(:status IS NULL OR s.status = :status) AND " +
           "(:scheduleType IS NULL OR s.scheduleType = :scheduleType) AND " +
           "(LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(s.code) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY s.code ASC")
    Page<AmortizationSchedule> findByFiltersAndSearch(
            @Param("status") ScheduleStatus status,
            @Param("scheduleType") ScheduleType scheduleType,
            @Param("search") String search,
            Pageable pageable);

    @Query("SELECT COUNT(s) FROM AmortizationSchedule s WHERE s.status = 'ACTIVE'")
    long countActiveSchedules();

    @Query("SELECT COUNT(s) FROM AmortizationSchedule s WHERE s.status = :status")
    long countByStatus(@Param("status") ScheduleStatus status);
}
