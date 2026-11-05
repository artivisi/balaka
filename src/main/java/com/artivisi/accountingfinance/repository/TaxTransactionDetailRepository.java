package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.TaxTransactionDetail;
import com.artivisi.accountingfinance.enums.TaxType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface TaxTransactionDetailRepository extends JpaRepository<TaxTransactionDetail, UUID> {

    Optional<TaxTransactionDetail> findByTransactionId(UUID transactionId);

    List<TaxTransactionDetail> findAllByTransactionIdOrderByTaxTypeAsc(UUID transactionId);

    @Query("SELECT DISTINCT t.transaction.id FROM TaxTransactionDetail t WHERE t.transaction.id IN :ids")
    Set<UUID> findTransactionIdsWithDetails(@Param("ids") Collection<UUID> ids);

    List<TaxTransactionDetail> findByTaxType(TaxType taxType);

    @Query("SELECT t FROM TaxTransactionDetail t " +
            "JOIN t.transaction trx " +
            "WHERE t.taxType = :taxType " +
            "AND trx.transactionDate BETWEEN :startDate AND :endDate " +
            "AND trx.status = 'POSTED' " +
            "ORDER BY trx.transactionDate ASC")
    List<TaxTransactionDetail> findByTaxTypeAndDateRange(
            @Param("taxType") TaxType taxType,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT t FROM TaxTransactionDetail t " +
            "JOIN t.transaction trx " +
            "WHERE t.taxType IN :taxTypes " +
            "AND trx.transactionDate BETWEEN :startDate AND :endDate " +
            "AND trx.status = 'POSTED' " +
            "ORDER BY trx.transactionDate ASC")
    List<TaxTransactionDetail> findByTaxTypesAndDateRange(
            @Param("taxTypes") List<TaxType> taxTypes,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // For e-Faktur Keluaran (PPN from sales)
    @Query("SELECT t FROM TaxTransactionDetail t " +
            "JOIN t.transaction trx " +
            "WHERE t.taxType = 'PPN_KELUARAN' " +
            "AND trx.transactionDate BETWEEN :startDate AND :endDate " +
            "AND trx.status = 'POSTED' " +
            "ORDER BY t.fakturDate ASC, t.fakturNumber ASC")
    List<TaxTransactionDetail> findEFakturKeluaranByDateRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // For e-Faktur Masukan (PPN from purchases)
    @Query("SELECT t FROM TaxTransactionDetail t " +
            "JOIN t.transaction trx " +
            "WHERE t.taxType = 'PPN_MASUKAN' " +
            "AND trx.transactionDate BETWEEN :startDate AND :endDate " +
            "AND trx.status = 'POSTED' " +
            "ORDER BY t.fakturDate ASC, t.fakturNumber ASC")
    List<TaxTransactionDetail> findEFakturMasukanByDateRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // For e-Bupot Unifikasi (PPh withholding)
    @Query("SELECT t FROM TaxTransactionDetail t " +
            "JOIN t.transaction trx " +
            "WHERE t.taxType IN ('PPH_23', 'PPH_26', 'PPH_42') " +
            "AND trx.transactionDate BETWEEN :startDate AND :endDate " +
            "AND trx.status = 'POSTED' " +
            // A bupot received from a customer on an invoice is issued by the customer, not by this company.
            "AND (trx.sourceDocumentType IS NULL OR trx.sourceDocumentType <> 'INVOICE') " +
            "ORDER BY trx.transactionDate ASC, t.bupotNumber ASC")
    List<TaxTransactionDetail> findEBupotUnifikasiByDateRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // For PPh 23 detail report (per-bupot)
    @Query("SELECT t FROM TaxTransactionDetail t " +
            "JOIN t.transaction trx " +
            "WHERE t.taxType = 'PPH_23' " +
            "AND trx.transactionDate BETWEEN :startDate AND :endDate " +
            "AND trx.status = 'POSTED' " +
            "ORDER BY trx.transactionDate ASC, t.bupotNumber ASC")
    List<TaxTransactionDetail> findPPh23ByDateRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /** Every status, including VOID; the sales tax chain reads a voided faktur as one without revenue. */
    @Query("""
            SELECT t FROM TaxTransactionDetail t JOIN FETCH t.transaction LEFT JOIN FETCH t.invoicePayment
            WHERE t.taxType = :taxType""")
    List<TaxTransactionDetail> findAllByTaxTypeWithTransaction(@Param("taxType") TaxType taxType);

    boolean existsByFakturNumber(String fakturNumber);

    boolean existsByFakturNumberAndIdNot(String fakturNumber, UUID id);

    boolean existsByBupotNumber(String bupotNumber);

    boolean existsByBupotNumberAndIdNot(String bupotNumber, UUID id);

    @Query("SELECT t FROM TaxTransactionDetail t " +
            "JOIN t.transaction trx " +
            "WHERE t.counterpartyNpwp = :npwp " +
            "AND trx.status = 'POSTED' " +
            "ORDER BY trx.transactionDate DESC")
    List<TaxTransactionDetail> findByCounterpartyNpwp(@Param("npwp") String npwp);
}
