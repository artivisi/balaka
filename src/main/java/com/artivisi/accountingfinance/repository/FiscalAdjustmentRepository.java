package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.FiscalAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FiscalAdjustmentRepository extends JpaRepository<FiscalAdjustment, UUID> {

    List<FiscalAdjustment> findByYearOrderByAdjustmentCategoryAscDescriptionAsc(int year);
}
