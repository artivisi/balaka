package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.TaxFiling;
import com.artivisi.accountingfinance.enums.TaxFilingType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaxFilingRepository extends JpaRepository<TaxFiling, UUID> {

    @Query("""
            SELECT f FROM TaxFiling f
            WHERE (:taxType IS NULL OR f.taxType = :taxType)
              AND (:year IS NULL OR f.periodYear = :year)
            ORDER BY f.periodYear DESC, f.periodMonth DESC NULLS LAST,
                     f.taxType ASC, f.pembetulanNo DESC
            """)
    List<TaxFiling> findByFilters(@Param("taxType") TaxFilingType taxType, @Param("year") Integer year);

    @Query("""
            SELECT f FROM TaxFiling f
            WHERE f.taxType = :taxType
              AND f.periodYear = :periodYear
              AND ((:periodMonth IS NULL AND f.periodMonth IS NULL) OR f.periodMonth = :periodMonth)
              AND f.pembetulanNo = :pembetulanNo
            """)
    Optional<TaxFiling> findByPeriodKey(@Param("taxType") TaxFilingType taxType,
                                        @Param("periodYear") Integer periodYear,
                                        @Param("periodMonth") Integer periodMonth,
                                        @Param("pembetulanNo") Integer pembetulanNo);

    @Query("""
            SELECT f FROM TaxFiling f
            WHERE f.periodYear BETWEEN :startYear AND :endYear
            ORDER BY f.periodYear ASC, f.periodMonth ASC NULLS FIRST,
                     f.taxType ASC, f.pembetulanNo ASC
            """)
    List<TaxFiling> findByYearRange(@Param("startYear") int startYear, @Param("endYear") int endYear);

    List<TaxFiling> findByPaymentTransactionId(UUID transactionId);
}
