package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.Invoice;
import com.artivisi.accountingfinance.enums.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    boolean existsByInvoiceNumber(String invoiceNumber);

    List<Invoice> findByClientId(UUID clientId);

    List<Invoice> findByProjectId(UUID projectId);

    List<Invoice> findByPaymentTermId(UUID paymentTermId);

    List<Invoice> findByStatus(InvoiceStatus status);

    Page<Invoice> findAllByOrderByInvoiceDateDesc(Pageable pageable);

    @Query("SELECT i FROM Invoice i WHERE " +
            "(:status IS NULL OR i.status = :status) AND " +
            "(:clientId IS NULL OR i.client.id = :clientId) AND " +
            "(:projectId IS NULL OR i.project.id = :projectId) " +
            "ORDER BY i.invoiceDate DESC")
    Page<Invoice> findByFilters(
            @Param("status") InvoiceStatus status,
            @Param("clientId") UUID clientId,
            @Param("projectId") UUID projectId,
            Pageable pageable);

    @Query("SELECT i FROM Invoice i WHERE " +
            "i.status = 'SENT' AND i.dueDate < :today")
    List<Invoice> findOverdueInvoices(@Param("today") LocalDate today);

    @Query("SELECT COALESCE(SUM(i.amount), 0) FROM Invoice i WHERE " +
            "i.client.id = :clientId AND i.status = 'PAID'")
    BigDecimal sumPaidAmountByClientId(@Param("clientId") UUID clientId);

    @Query("SELECT COALESCE(SUM(i.amount), 0) FROM Invoice i WHERE " +
            "i.project.id = :projectId AND i.status = 'PAID'")
    BigDecimal sumPaidAmountByProjectId(@Param("projectId") UUID projectId);

    long countByStatus(InvoiceStatus status);

    long countByClientId(UUID clientId);

    long countByProjectId(UUID projectId);

    @Query("SELECT MAX(CAST(SUBSTRING(i.invoiceNumber, LENGTH(:prefix) + 1) AS int)) " +
            "FROM Invoice i WHERE i.invoiceNumber LIKE :prefix")
    Integer findMaxSequenceByPrefix(@Param("prefix") String prefix);

    @Query("SELECT i FROM Invoice i JOIN FETCH i.client WHERE " +
            "i.status IN ('SENT', 'PARTIAL', 'OVERDUE')")
    List<Invoice> findOutstandingInvoices();

    @Query("SELECT i FROM Invoice i JOIN FETCH i.client WHERE " +
            "i.client.id = :clientId AND " +
            "i.invoiceDate >= :dateFrom AND i.invoiceDate <= :dateTo AND " +
            "i.status IN ('SENT', 'PARTIAL', 'OVERDUE', 'PAID') " +
            "ORDER BY i.invoiceDate ASC, i.invoiceNumber ASC")
    List<Invoice> findByClientIdAndDateRange(
            @Param("clientId") UUID clientId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo);

    @Query("SELECT COALESCE(SUM(i.amount + i.taxAmount), 0) FROM Invoice i WHERE " +
            "i.client.id = :clientId AND " +
            "i.invoiceDate < :date AND " +
            "i.status IN ('SENT', 'PARTIAL', 'OVERDUE', 'PAID')")
    BigDecimal sumInvoicesBeforeDate(
            @Param("clientId") UUID clientId,
            @Param("date") LocalDate date);

    /** Issued, not cancelled invoices with their payments: what the sales tax chain checks for faktur and bupot. */
    @Query("""
            SELECT DISTINCT i FROM Invoice i JOIN FETCH i.client LEFT JOIN FETCH i.payments
            WHERE i.status NOT IN (com.artivisi.accountingfinance.enums.InvoiceStatus.DRAFT,
                                   com.artivisi.accountingfinance.enums.InvoiceStatus.CANCELLED)
            ORDER BY i.invoiceDate ASC, i.invoiceNumber ASC""")
    List<Invoice> findIssuedWithPayments();

    @Query("""
            SELECT new com.artivisi.accountingfinance.repository.InvoiceRepository$PaymentTransactionLink(i.transaction.id, i.id)
            FROM Invoice i WHERE i.transaction IS NOT NULL""")
    List<PaymentTransactionLink> findPaymentTransactionLinks();

    /** invoices.id_transaction: the receipt transaction booked through the mark-paid screen. */
    record PaymentTransactionLink(UUID transactionId, UUID invoiceId) {}

    /** Invoices whose journal is one of these transactions through id_transaction. */
    @Query("SELECT i FROM Invoice i JOIN FETCH i.transaction t LEFT JOIN FETCH i.contract c LEFT JOIN FETCH c.document "
            + "WHERE t.id IN :transactionIds")
    List<Invoice> findByTransactionIdsWithContract(@Param("transactionIds") java.util.Collection<UUID> transactionIds);

    @Query("SELECT i FROM Invoice i LEFT JOIN FETCH i.contract c LEFT JOIN FETCH c.document WHERE i.id IN :ids")
    List<Invoice> findByIdsWithContract(@Param("ids") java.util.Collection<UUID> ids);

    @Query("SELECT i FROM Invoice i WHERE i.contract.id = :contractId ORDER BY i.invoiceDate, i.invoiceNumber")
    List<Invoice> findByContractId(@Param("contractId") UUID contractId);
}
