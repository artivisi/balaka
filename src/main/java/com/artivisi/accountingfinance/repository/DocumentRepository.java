package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
