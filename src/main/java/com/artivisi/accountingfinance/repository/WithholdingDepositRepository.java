package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.WithholdingDeposit;
import com.artivisi.accountingfinance.enums.WithholdingTreatment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WithholdingDepositRepository extends JpaRepository<WithholdingDeposit, UUID> {

    Optional<WithholdingDeposit> findByTransactionId(UUID transactionId);

    @Query("""
            SELECT d FROM WithholdingDeposit d JOIN FETCH d.transaction
            WHERE d.periodYear = :year AND d.periodMonth = :month
            ORDER BY d.depositDate ASC, d.createdAt ASC""")
    List<WithholdingDeposit> findForPeriod(@Param("year") int year, @Param("month") int month);

    @Query("""
            SELECT COALESCE(SUM(d.amount), 0) FROM WithholdingDeposit d
            WHERE d.treatment = :treatment AND d.periodYear = :year AND d.periodMonth = :month
              AND d.transaction.status <> com.artivisi.accountingfinance.enums.TransactionStatus.VOID""")
    BigDecimal sumNonVoid(@Param("treatment") WithholdingTreatment treatment, @Param("year") int year, @Param("month") int month);
}
