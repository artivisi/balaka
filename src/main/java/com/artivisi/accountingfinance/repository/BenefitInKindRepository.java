package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.BenefitInKind;
import com.artivisi.accountingfinance.entity.PayrollRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface BenefitInKindRepository extends JpaRepository<BenefitInKind, UUID> {

    @Query("""
            SELECT b FROM BenefitInKind b
            JOIN FETCH b.employee
            LEFT JOIN FETCH b.payrollDetail d
            LEFT JOIN FETCH d.payrollRun
            WHERE b.transaction.id = :transactionId
            ORDER BY b.createdAt
            """)
    List<BenefitInKind> findByTransactionId(@Param("transactionId") UUID transactionId);

    @Query("""
            SELECT b FROM BenefitInKind b
            JOIN FETCH b.employee
            JOIN FETCH b.transaction t
            LEFT JOIN FETCH b.payrollDetail d
            LEFT JOIN FETCH d.payrollRun
            WHERE t.status = com.artivisi.accountingfinance.enums.TransactionStatus.POSTED
              AND t.transactionDate BETWEEN :start AND :end
            ORDER BY t.transactionDate, b.createdAt
            """)
    List<BenefitInKind> findPostedBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /** Rows waiting to enter a payroll run of this employee, oldest first. */
    @Query("""
            SELECT b FROM BenefitInKind b
            JOIN FETCH b.transaction t
            WHERE b.employee.id = :employeeId
              AND b.payrollDetail IS NULL
              AND t.status = com.artivisi.accountingfinance.enums.TransactionStatus.POSTED
              AND t.transactionDate BETWEEN :start AND :end
            ORDER BY t.transactionDate
            """)
    List<BenefitInKind> findPendingForPayroll(@Param("employeeId") UUID employeeId,
                                              @Param("start") LocalDate start,
                                              @Param("end") LocalDate end);

    List<BenefitInKind> findByPayrollDetailPayrollRun(PayrollRun payrollRun);

    boolean existsByTransactionIdAndPayrollDetailIsNotNull(UUID transactionId);

    /** Value given in the year that has not reached a POSTED payroll run, so no PPh 21 was withheld yet. */
    @Query("""
            SELECT COALESCE(SUM(b.amount), 0) FROM BenefitInKind b
            JOIN b.transaction t
            LEFT JOIN b.payrollDetail d
            LEFT JOIN d.payrollRun r
            WHERE b.employee.id = :employeeId
              AND t.status = com.artivisi.accountingfinance.enums.TransactionStatus.POSTED
              AND t.transactionDate BETWEEN :start AND :end
              AND (d IS NULL OR r.status <> com.artivisi.accountingfinance.entity.PayrollStatus.POSTED)
            """)
    BigDecimal sumNotInPostedPayroll(@Param("employeeId") UUID employeeId,
                                     @Param("start") LocalDate start,
                                     @Param("end") LocalDate end);
}
