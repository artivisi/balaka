package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.WithholdingPayment;
import com.artivisi.accountingfinance.enums.TaxType;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.enums.WithholdingTreatment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WithholdingPaymentRepository extends JpaRepository<WithholdingPayment, UUID> {

    Optional<WithholdingPayment> findByTransactionId(UUID transactionId);

    @Query("""
            SELECT p FROM WithholdingPayment p
            JOIN FETCH p.vendor JOIN FETCH p.transaction LEFT JOIN FETCH p.bill
            WHERE p.paymentDate BETWEEN :start AND :end
              AND (:treatment IS NULL OR p.treatment = :treatment)
              AND (:vendorId IS NULL OR p.vendor.id = :vendorId)
            ORDER BY p.paymentDate ASC, p.createdAt ASC""")
    List<WithholdingPayment> findForPeriod(@Param("start") LocalDate start, @Param("end") LocalDate end,
                                           @Param("treatment") WithholdingTreatment treatment,
                                           @Param("vendorId") UUID vendorId);

    /** Non-void payments in processing order: POSTED first because their tax is fixed, then by creation. */
    @Query("""
            SELECT p FROM WithholdingPayment p JOIN FETCH p.transaction t
            WHERE p.vendor.id = :vendorId AND p.treatment = :treatment
              AND p.paymentDate BETWEEN :start AND :end AND t.status <> com.artivisi.accountingfinance.enums.TransactionStatus.VOID
            ORDER BY CASE WHEN t.status = com.artivisi.accountingfinance.enums.TransactionStatus.POSTED THEN 0 ELSE 1 END,
                     p.createdAt ASC""")
    List<WithholdingPayment> findActiveInMasa(@Param("vendorId") UUID vendorId,
                                              @Param("treatment") WithholdingTreatment treatment,
                                              @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("""
            SELECT COUNT(p) > 0 FROM WithholdingPayment p
            WHERE p.vendor.id = :vendorId AND p.treatment = :treatment
              AND p.paymentDate BETWEEN :start AND :end AND p.createdAt > :createdAt
              AND p.transaction.status <> com.artivisi.accountingfinance.enums.TransactionStatus.VOID""")
    boolean existsLaterInMasa(@Param("vendorId") UUID vendorId, @Param("treatment") WithholdingTreatment treatment,
                              @Param("start") LocalDate start, @Param("end") LocalDate end,
                              @Param("createdAt") LocalDateTime createdAt);

    /** A POSTED payment created after {@code createdAt}: its tax was fixed with the earlier one counted as prior gross. */
    @Query("""
            SELECT COUNT(p) > 0 FROM WithholdingPayment p
            WHERE p.vendor.id = :vendorId AND p.treatment = :treatment
              AND p.paymentDate BETWEEN :start AND :end AND p.createdAt > :createdAt
              AND p.transaction.status = com.artivisi.accountingfinance.enums.TransactionStatus.POSTED""")
    boolean existsLaterPostedInMasa(@Param("vendorId") UUID vendorId, @Param("treatment") WithholdingTreatment treatment,
                                    @Param("start") LocalDate start, @Param("end") LocalDate end,
                                    @Param("createdAt") LocalDateTime createdAt);

    @Query("""
            SELECT COALESCE(SUM(p.taxAmount), 0) FROM WithholdingPayment p
            WHERE p.treatment = :treatment AND p.paymentDate BETWEEN :start AND :end AND p.transaction.status = :status""")
    BigDecimal sumTax(@Param("treatment") WithholdingTreatment treatment, @Param("start") LocalDate start,
                      @Param("end") LocalDate end, @Param("status") TransactionStatus status);

    @Query("""
            SELECT COALESCE(SUM(p.grossAmount), 0) FROM WithholdingPayment p
            WHERE p.treatment = :treatment AND p.paymentDate BETWEEN :start AND :end AND p.transaction.status = :status""")
    BigDecimal sumGross(@Param("treatment") WithholdingTreatment treatment, @Param("start") LocalDate start,
                        @Param("end") LocalDate end, @Param("status") TransactionStatus status);

    @Query("""
            SELECT COALESCE(SUM(p.settledAmount), 0) FROM WithholdingPayment p
            WHERE p.bill.id = :billId AND p.transaction.status = com.artivisi.accountingfinance.enums.TransactionStatus.DRAFT""")
    BigDecimal sumDraftSettledOnBill(@Param("billId") UUID billId);

    /**
     * Posted debits on service accounts in the period on transactions with no PPh tax detail. A PPN detail
     * does not count. A bill's accrual is covered once the bill has a non-void withholding payment, because
     * the tax detail sits on the payment transaction.
     */
    @Query("""
            SELECT new com.artivisi.accountingfinance.repository.WithholdingPaymentRepository$UndocumentedRow(
                t.id, t.transactionNumber, t.transactionDate, t.description, a.accountCode, a.accountName,
                SUM(COALESCE(j.debitAmount, 0) - COALESCE(j.creditAmount, 0)))
            FROM JournalEntry j JOIN j.transaction t JOIN j.account a
            WHERE t.status = com.artivisi.accountingfinance.enums.TransactionStatus.POSTED
              AND t.transactionDate BETWEEN :start AND :end
              AND a.serviceExpense = TRUE
              AND NOT EXISTS (SELECT 1 FROM TaxTransactionDetail d WHERE d.transaction = t AND d.taxType IN :pphTypes)
              AND NOT EXISTS (SELECT 1 FROM WithholdingPayment p
                              WHERE t.sourceDocumentType = 'BILL' AND p.bill.id = t.sourceDocumentId
                                AND p.transaction.status <> com.artivisi.accountingfinance.enums.TransactionStatus.VOID)
            GROUP BY t.id, t.transactionNumber, t.transactionDate, t.description, a.accountCode, a.accountName
            HAVING SUM(COALESCE(j.debitAmount, 0) - COALESCE(j.creditAmount, 0)) > 0
            ORDER BY t.transactionDate ASC, t.transactionNumber ASC, a.accountCode ASC""")
    List<UndocumentedRow> findUndocumentedServicePayments(@Param("start") LocalDate start, @Param("end") LocalDate end,
                                                          @Param("pphTypes") Collection<TaxType> pphTypes);

    /** Expense accounts with a posted debit in the period and no service classification. */
    @Query("""
            SELECT DISTINCT a FROM JournalEntry j JOIN j.transaction t JOIN j.account a
            WHERE t.status = com.artivisi.accountingfinance.enums.TransactionStatus.POSTED
              AND t.transactionDate BETWEEN :start AND :end
              AND a.accountType = com.artivisi.accountingfinance.enums.AccountType.EXPENSE
              AND a.serviceExpense IS NULL AND COALESCE(j.debitAmount, 0) > 0
            ORDER BY a.accountCode""")
    List<ChartOfAccount> findUnclassifiedExpenseAccounts(@Param("start") LocalDate start, @Param("end") LocalDate end);

    record UndocumentedRow(UUID transactionId, String transactionNumber, LocalDate transactionDate, String description,
                           String accountCode, String accountName, BigDecimal amount) {}
}
