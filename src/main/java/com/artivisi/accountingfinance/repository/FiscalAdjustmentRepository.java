package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.FiscalAdjustment;
import com.artivisi.accountingfinance.enums.FiscalAdjustmentSource;
import com.artivisi.accountingfinance.enums.FiscalAdjustmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FiscalAdjustmentRepository extends JpaRepository<FiscalAdjustment, UUID> {

    List<FiscalAdjustment> findByYearOrderByAdjustmentCategoryAscDescriptionAsc(int year);

    List<FiscalAdjustment> findByYearAndStatusOrderByAdjustmentCategoryAscDescriptionAsc(int year, FiscalAdjustmentStatus status);

    void deleteByYearAndStatusAndSource(int year, FiscalAdjustmentStatus status, FiscalAdjustmentSource source);
}
