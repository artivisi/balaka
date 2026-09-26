package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.FiscalLossCarryforward;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FiscalLossCarryforwardRepository extends JpaRepository<FiscalLossCarryforward, UUID> {

    Optional<FiscalLossCarryforward> findByOriginYear(Integer originYear);

    /**
     * Find all active (non-expired, with remaining balance) losses for a given tax year.
     * Ordered by origin year ASC (oldest used first — FIFO).
     */
    @Query("SELECT f FROM FiscalLossCarryforward f " +
            "WHERE f.originYear < :year " +
            "AND f.expiryYear >= :year " +
            "AND f.remainingAmount > 0 " +
            "ORDER BY f.originYear ASC")
    List<FiscalLossCarryforward> findActiveLossesForYear(@Param("year") int year);

    List<FiscalLossCarryforward> findAllByOrderByOriginYearDesc();
}
