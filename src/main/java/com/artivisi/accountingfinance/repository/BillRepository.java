package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.Bill;
import com.artivisi.accountingfinance.enums.BillStatus;
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

public interface BillRepository extends JpaRepository<Bill, UUID> {

    Optional<Bill> findByBillNumber(String billNumber);

    boolean existsByBillNumber(String billNumber);

    List<Bill> findByVendorId(UUID vendorId);

    List<Bill> findByStatus(BillStatus status);

    Page<Bill> findAllByOrderByBillDateDesc(Pageable pageable);

    @Query("SELECT b FROM Bill b WHERE " +
            "(:status IS NULL OR b.status = :status) AND " +
            "(:vendorId IS NULL OR b.vendor.id = :vendorId) " +
            "ORDER BY b.billDate DESC")
    Page<Bill> findByFilters(
            @Param("status") BillStatus status,
            @Param("vendorId") UUID vendorId,
            Pageable pageable);

    @Query("SELECT b FROM Bill b WHERE " +
            "(:status IS NULL OR b.status = :status) AND " +
            "(:vendorId IS NULL OR b.vendor.id = :vendorId) AND " +
            "(CAST(:dateFrom AS date) IS NULL OR b.billDate >= :dateFrom) AND " +
            "(CAST(:dateTo AS date) IS NULL OR b.billDate <= :dateTo) " +
            "ORDER BY b.billDate DESC")
    Page<Bill> findByFiltersWithDates(
            @Param("status") BillStatus status,
            @Param("vendorId") UUID vendorId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            Pageable pageable);

    @Query("SELECT b FROM Bill b WHERE " +
            "b.status = 'APPROVED' AND b.dueDate < :today")
    List<Bill> findOverdueBills(@Param("today") LocalDate today);

    @Query("SELECT COALESCE(SUM(b.amount), 0) FROM Bill b WHERE " +
            "b.vendor.id = :vendorId AND b.status = 'PAID'")
    BigDecimal sumPaidAmountByVendorId(@Param("vendorId") UUID vendorId);

    long countByStatus(BillStatus status);

    long countByVendorId(UUID vendorId);

    @Query("SELECT MAX(CAST(SUBSTRING(b.billNumber, LENGTH(:prefix) + 1) AS int)) " +
            "FROM Bill b WHERE b.billNumber LIKE :prefix")
    Integer findMaxSequenceByPrefix(@Param("prefix") String prefix);

    @Query("SELECT b FROM Bill b JOIN FETCH b.vendor WHERE " +
            "b.status IN ('APPROVED', 'PARTIAL', 'OVERDUE')")
    List<Bill> findOutstandingBills();

    @Query("SELECT b FROM Bill b JOIN FETCH b.vendor WHERE " +
            "b.vendor.id = :vendorId AND " +
            "b.billDate >= :dateFrom AND b.billDate <= :dateTo AND " +
            "b.status IN ('APPROVED', 'PARTIAL', 'OVERDUE', 'PAID') " +
            "ORDER BY b.billDate ASC, b.billNumber ASC")
    List<Bill> findByVendorIdAndDateRange(
            @Param("vendorId") UUID vendorId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo);

    @Query("SELECT COALESCE(SUM(b.amount + b.taxAmount), 0) FROM Bill b WHERE " +
            "b.vendor.id = :vendorId AND " +
            "b.billDate < :date AND " +
            "b.status IN ('APPROVED', 'PARTIAL', 'OVERDUE', 'PAID')")
    BigDecimal sumBillsBeforeDate(
            @Param("vendorId") UUID vendorId,
            @Param("date") LocalDate date);
}
