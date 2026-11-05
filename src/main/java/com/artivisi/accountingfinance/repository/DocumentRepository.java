package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByTransactionIdOrderByCreatedAtDesc(UUID transactionId);

    List<Document> findByJournalEntryIdOrderByCreatedAtDesc(UUID journalEntryId);

    List<Document> findByInvoiceIdOrderByCreatedAtDesc(UUID invoiceId);

    Optional<Document> findByChecksumSha256(String checksum);

    boolean existsByChecksumSha256(String checksum);

    long countByTransactionId(UUID transactionId);

    long countByJournalEntryId(UUID journalEntryId);

    long countByInvoiceId(UUID invoiceId);

    @Query("SELECT d FROM Document d JOIN FETCH d.transaction t WHERE t.id IN :ids ORDER BY d.uploadedAt")
    List<Document> findByTransactionIds(@Param("ids") Collection<UUID> ids);

    /** Rows of [transaction id, document] for files attached to a journal line of those transactions. */
    @Query("SELECT je.transaction.id, d FROM Document d JOIN d.journalEntry je WHERE je.transaction.id IN :ids ORDER BY d.uploadedAt")
    List<Object[]> findByJournalEntryTransactionIds(@Param("ids") Collection<UUID> ids);

    @Query("SELECT d FROM Document d JOIN FETCH d.invoice i WHERE i.id IN :ids ORDER BY d.uploadedAt")
    List<Document> findByInvoiceIds(@Param("ids") Collection<UUID> ids);
}
