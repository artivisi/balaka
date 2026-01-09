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

    @Query("SELECT CASE WHEN COUNT(pd) > 0 THEN true ELSE false END FROM PayrollDetail pd WHERE pd.employee.id = :employeeId")
    boolean existsByEmployeeId(@Param("employeeId") UUID employeeId);

    @Query("SELECT pd FROM PayrollDetail pd " +
           "JOIN FETCH pd.employee " +
           "JOIN FETCH pd.payrollRun pr " +
           "WHERE pd.employee.id = :employeeId " +
           "AND pr.payrollPeriod LIKE :yearPrefix% " +
           "AND pr.status = 'POSTED' " +
           "ORDER BY pr.payrollPeriod")
    List<PayrollDetail> findPostedByEmployeeIdAndYear(@Param("employeeId") UUID employeeId, @Param("yearPrefix") String yearPrefix);

    @Query("SELECT DISTINCT pd.employee.id FROM PayrollDetail pd " +
           "JOIN pd.payrollRun pr " +
           "WHERE pr.payrollPeriod LIKE :yearPrefix% " +
           "AND pr.status = 'POSTED'")
    List<UUID> findEmployeeIdsWithPostedPayrollInYear(@Param("yearPrefix") String yearPrefix);

    @Query("SELECT pd FROM PayrollDetail pd " +
           "JOIN FETCH pd.payrollRun pr " +
           "WHERE pd.employee.id = :employeeId " +
           "AND pr.status = 'POSTED' " +
           "ORDER BY pr.payrollPeriod DESC")
    List<PayrollDetail> findPostedByEmployeeId(@Param("employeeId") UUID employeeId);
}
