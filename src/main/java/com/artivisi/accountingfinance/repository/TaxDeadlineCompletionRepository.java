package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.TaxDeadline;
import com.artivisi.accountingfinance.entity.TaxDeadlineCompletion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaxDeadlineCompletionRepository extends JpaRepository<TaxDeadlineCompletion, UUID> {

    Optional<TaxDeadlineCompletion> findByTaxDeadlineAndYearAndMonth(
            TaxDeadline taxDeadline, Integer year, Integer month);

    List<TaxDeadlineCompletion> findByYearAndMonth(Integer year, Integer month);

    List<TaxDeadlineCompletion> findByYear(Integer year);

    boolean existsByTaxDeadlineAndYearAndMonth(TaxDeadline taxDeadline, Integer year, Integer month);

    @Query("SELECT c FROM TaxDeadlineCompletion c " +
            "WHERE c.taxDeadline.id = :deadlineId AND c.year = :year AND c.month = :month")
    Optional<TaxDeadlineCompletion> findByDeadlineIdAndYearAndMonth(
            @Param("deadlineId") UUID deadlineId,
            @Param("year") Integer year,
            @Param("month") Integer month);

    @Query("SELECT c FROM TaxDeadlineCompletion c " +
            "JOIN FETCH c.taxDeadline " +
            "WHERE c.year = :year AND c.month = :month")
    List<TaxDeadlineCompletion> findByYearAndMonthWithDeadline(
            @Param("year") Integer year,
            @Param("month") Integer month);

    @Query("SELECT DISTINCT c.year FROM TaxDeadlineCompletion c ORDER BY c.year DESC")
    List<Integer> findDistinctYears();

    long countByYearAndMonth(Integer year, Integer month);
}
