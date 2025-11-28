package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.PayrollRun;
import com.artivisi.accountingfinance.entity.PayrollStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayrollRunRepository extends JpaRepository<PayrollRun, UUID> {

    Optional<PayrollRun> findByPayrollPeriod(String payrollPeriod);

    boolean existsByPayrollPeriod(String payrollPeriod);

    Page<PayrollRun> findByStatus(PayrollStatus status, Pageable pageable);

    @Query("SELECT p FROM PayrollRun p WHERE " +
           "(:status IS NULL OR p.status = :status) " +
           "ORDER BY p.payrollPeriod DESC")
    Page<PayrollRun> findByStatusOptional(@Param("status") PayrollStatus status, Pageable pageable);

    @Query("SELECT p FROM PayrollRun p ORDER BY p.payrollPeriod DESC")
    Page<PayrollRun> findAllOrderByPeriodDesc(Pageable pageable);
}
