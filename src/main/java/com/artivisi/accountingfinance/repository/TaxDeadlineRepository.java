package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.TaxDeadline;
import com.artivisi.accountingfinance.enums.TaxDeadlineType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaxDeadlineRepository extends JpaRepository<TaxDeadline, UUID> {

    Optional<TaxDeadline> findByDeadlineType(TaxDeadlineType deadlineType);

    List<TaxDeadline> findByActiveTrue();

    List<TaxDeadline> findByActiveTrueOrderByDueDayAsc();

    boolean existsByDeadlineType(TaxDeadlineType deadlineType);
}
