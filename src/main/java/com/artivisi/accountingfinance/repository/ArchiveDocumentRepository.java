package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.ArchiveDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArchiveDocumentRepository extends JpaRepository<ArchiveDocument, UUID> {

    @Query("SELECT a FROM ArchiveDocument a JOIN FETCH a.document LEFT JOIN FETCH a.transaction LEFT JOIN FETCH a.journalEntry j "
            + "LEFT JOIN FETCH j.account WHERE a.id = :id")
    Optional<ArchiveDocument> findWithLinks(@Param("id") UUID id);

    @Query("SELECT a FROM ArchiveDocument a JOIN FETCH a.document WHERE a.document.id = :documentId")
    Optional<ArchiveDocument> findByDocumentId(@Param("documentId") UUID documentId);

    @Query("SELECT a FROM ArchiveDocument a JOIN FETCH a.document LEFT JOIN FETCH a.journalEntry j LEFT JOIN FETCH j.account "
            + "WHERE a.transaction.id = :transactionId ORDER BY a.docYear, a.docType, a.docNumber")
    List<ArchiveDocument> findByTransactionId(@Param("transactionId") UUID transactionId);

    @Query("SELECT a FROM ArchiveDocument a JOIN FETCH a.document LEFT JOIN FETCH a.transaction WHERE a.id IN :ids")
    List<ArchiveDocument> findAllWithDocument(@Param("ids") Collection<UUID> ids);

    @Query("SELECT a.document.checksumSha256 FROM ArchiveDocument a WHERE a.document.checksumSha256 IN :checksums")
    List<String> findExistingChecksums(@Param("checksums") Collection<String> checksums);

    long countByDocYear(int docYear);

    /**
     * Full-text search over number, counterparty, description and extracted text ('simple' configuration: no
     * stemming, Indonesian has no PostgreSQL dictionary), with optional year, type and source filters. A blank query
     * lists by filters only. Ranked by relevance, then year descending.
     */
    @Query(value = "SELECT a.id FROM archive_documents a "
            + "WHERE (CAST(:q AS TEXT) IS NULL OR a.search_vector @@ websearch_to_tsquery('simple', CAST(:q AS TEXT))) "
            + "AND (CAST(:year AS INTEGER) IS NULL OR a.doc_year = CAST(:year AS INTEGER)) "
            + "AND (CAST(:docType AS TEXT) IS NULL OR a.doc_type = CAST(:docType AS TEXT)) "
            + "AND (CAST(:source AS TEXT) IS NULL OR a.source = CAST(:source AS TEXT)) "
            + "ORDER BY CASE WHEN CAST(:q AS TEXT) IS NULL THEN 0 "
            + "ELSE ts_rank(a.search_vector, websearch_to_tsquery('simple', CAST(:q AS TEXT))) END DESC, "
            + "a.doc_year DESC, a.doc_type, a.doc_number, a.id LIMIT :limit", nativeQuery = true)
    List<UUID> search(@Param("q") String q, @Param("year") Integer year, @Param("docType") String docType,
                      @Param("source") String source, @Param("limit") int limit);

    /** Highlighted excerpt of the extracted text around the query terms. */
    @Query(value = "SELECT ts_headline('simple', coalesce(a.extracted_text, ''), websearch_to_tsquery('simple', :q), "
            + "'MaxWords=25, MinWords=10, StartSel=[, StopSel=]') FROM archive_documents a WHERE a.id = :id", nativeQuery = true)
    String headline(@Param("id") UUID id, @Param("q") String q);
}
