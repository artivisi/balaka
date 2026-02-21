package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.BillLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BillLineRepository extends JpaRepository<BillLine, UUID> {

    List<BillLine> findByBillIdOrderByLineOrder(UUID billId);

    void deleteByBillId(UUID billId);
}
