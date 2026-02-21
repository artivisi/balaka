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
}
