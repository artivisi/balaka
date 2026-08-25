package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.TaxDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaxDocumentRepository extends JpaRepository<TaxDocument, UUID> {

    @Query("""
            SELECT d FROM TaxDocument d
            JOIN FETCH d.document
            WHERE d.taxFiling.id = :filingId
            ORDER BY d.docDate DESC NULLS LAST, d.createdAt DESC
            """)
    List<TaxDocument> findByFilingId(UUID filingId);

    @Query("""
            SELECT d FROM TaxDocument d
            JOIN FETCH d.document
            WHERE d.id = :id AND d.taxFiling.id = :filingId
            """)
    Optional<TaxDocument> findByIdAndFilingId(UUID id, UUID filingId);

    long countByTaxFilingId(UUID filingId);

    long countByDocumentId(UUID documentId);
}
