package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.BankStatementItem;
import com.artivisi.accountingfinance.enums.StatementItemMatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BankStatementItemRepository extends JpaRepository<BankStatementItem, UUID> {

    List<BankStatementItem> findByBankStatementIdOrderByLineNumberAsc(UUID bankStatementId);

    List<BankStatementItem> findByBankStatementIdAndMatchStatusOrderByLineNumberAsc(
            UUID bankStatementId, StatementItemMatchStatus matchStatus);

    @Query("SELECT bsi FROM BankStatementItem bsi LEFT JOIN FETCH bsi.matchedTransaction " +
           "WHERE bsi.bankStatement.id = :bankStatementId ORDER BY bsi.lineNumber ASC")
    List<BankStatementItem> findByBankStatementIdWithTransaction(@Param("bankStatementId") UUID bankStatementId);

    long countByBankStatementIdAndMatchStatus(UUID bankStatementId, StatementItemMatchStatus matchStatus);

    void deleteByBankStatementId(UUID bankStatementId);

    /** Statement lines matched to these transactions, with statement and bank account for the label. */
    @Query("SELECT i FROM BankStatementItem i JOIN FETCH i.matchedTransaction t JOIN FETCH i.bankStatement s "
            + "JOIN FETCH s.bankAccount WHERE t.id IN :ids "
            + "AND i.matchStatus = com.artivisi.accountingfinance.enums.StatementItemMatchStatus.MATCHED")
    List<BankStatementItem> findMatchedToTransactions(@Param("ids") java.util.Collection<UUID> ids);
}
