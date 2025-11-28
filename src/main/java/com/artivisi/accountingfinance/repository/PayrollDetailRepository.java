package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.PayrollDetail;
import com.artivisi.accountingfinance.entity.PayrollRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PayrollDetailRepository extends JpaRepository<PayrollDetail, UUID> {

    List<PayrollDetail> findByPayrollRunOrderByEmployeeEmployeeId(PayrollRun payrollRun);

    List<PayrollDetail> findByPayrollRunId(UUID payrollRunId);

    @Query("SELECT pd FROM PayrollDetail pd " +
           "JOIN FETCH pd.employee " +
           "WHERE pd.payrollRun.id = :payrollRunId " +
           "ORDER BY pd.employee.employeeId")
    List<PayrollDetail> findByPayrollRunIdWithEmployee(@Param("payrollRunId") UUID payrollRunId);

    void deleteByPayrollRun(PayrollRun payrollRun);

    boolean existsByPayrollRunAndEmployeeId(PayrollRun payrollRun, UUID employeeId);
}
