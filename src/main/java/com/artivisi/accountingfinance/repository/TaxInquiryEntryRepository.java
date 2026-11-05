package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.TaxInquiryEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TaxInquiryEntryRepository extends JpaRepository<TaxInquiryEntry, UUID> {

    @Query("SELECT e FROM TaxInquiryEntry e LEFT JOIN FETCH e.item LEFT JOIN FETCH e.corrects LEFT JOIN FETCH e.document "
            + "WHERE e.inquiryCase.id = :caseId ORDER BY e.createdAt, e.id")
    List<TaxInquiryEntry> findByCase(@Param("caseId") UUID caseId);
}
