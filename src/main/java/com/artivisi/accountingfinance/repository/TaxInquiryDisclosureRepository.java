package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.TaxInquiryDisclosure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TaxInquiryDisclosureRepository extends JpaRepository<TaxInquiryDisclosure, UUID> {

    @Query("SELECT d FROM TaxInquiryDisclosure d JOIN FETCH d.item JOIN FETCH d.document LEFT JOIN FETCH d.transaction "
            + "LEFT JOIN FETCH d.archiveDocument WHERE d.inquiryCase.id = :caseId ORDER BY d.disclosedOn, d.item.itemNo, d.filename")
    List<TaxInquiryDisclosure> findByCase(@Param("caseId") UUID caseId);

    @Query("SELECT d FROM TaxInquiryDisclosure d JOIN FETCH d.item JOIN FETCH d.document LEFT JOIN FETCH d.transaction "
            + "LEFT JOIN FETCH d.archiveDocument WHERE d.entry.id = :entryId ORDER BY d.item.itemNo, d.filename")
    List<TaxInquiryDisclosure> findByEntry(@Param("entryId") UUID entryId);

    @Query("SELECT d FROM TaxInquiryDisclosure d JOIN FETCH d.inquiryCase JOIN FETCH d.item WHERE d.document.id IN :documentIds "
            + "ORDER BY d.disclosedOn")
    List<TaxInquiryDisclosure> findByDocumentIds(@Param("documentIds") Collection<UUID> documentIds);
}
