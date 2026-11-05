package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.TaxInquiryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TaxInquiryItemRepository extends JpaRepository<TaxInquiryItem, UUID> {

    List<TaxInquiryItem> findByInquiryCaseIdOrderByItemNoAsc(UUID caseId);

    @Query("SELECT COALESCE(MAX(i.itemNo), 0) FROM TaxInquiryItem i WHERE i.inquiryCase.id = :caseId")
    int maxItemNo(@Param("caseId") UUID caseId);
}
