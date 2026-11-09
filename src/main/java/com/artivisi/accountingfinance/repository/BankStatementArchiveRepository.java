package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.BankStatementArchive;
import com.artivisi.accountingfinance.entity.StatementArchiveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BankStatementArchiveRepository extends JpaRepository<BankStatementArchive, UUID> {

    Optional<BankStatementArchive> findByBankAccountIdAndPeriodMonthAndStatus(
            UUID bankAccountId, LocalDate periodMonth, StatementArchiveStatus status);

    @Query("SELECT a FROM BankStatementArchive a JOIN FETCH a.bankAccount JOIN FETCH a.document "
            + "WHERE a.periodMonth BETWEEN :from AND :to ORDER BY a.periodMonth, a.createdAt")
    List<BankStatementArchive> findByPeriodBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT a FROM BankStatementArchive a JOIN FETCH a.bankAccount JOIN FETCH a.document "
            + "WHERE a.status = 'ACTIVE' AND a.bankAccount.id = :bankAccountId ORDER BY a.periodMonth")
    List<BankStatementArchive> findActiveByBankAccount(@Param("bankAccountId") UUID bankAccountId);

    @Query("SELECT MIN(a.periodMonth) FROM BankStatementArchive a WHERE a.bankAccount.id = :bankAccountId")
    Optional<LocalDate> findEarliestPeriod(@Param("bankAccountId") UUID bankAccountId);

    long countByDocumentId(UUID documentId);
}
