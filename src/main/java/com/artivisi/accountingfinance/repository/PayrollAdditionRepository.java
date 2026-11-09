package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.PayrollAddition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PayrollAdditionRepository extends JpaRepository<PayrollAddition, UUID> {

    @Query("SELECT a FROM PayrollAddition a JOIN FETCH a.employee e WHERE a.payrollRun.id = :runId "
            + "ORDER BY e.employeeId, a.additionType, a.createdAt")
    List<PayrollAddition> findByRunIdWithEmployee(@Param("runId") UUID runId);
}
