package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.Document;
import com.artivisi.accountingfinance.entity.Invoice;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.repository.DocumentRepository;
import com.artivisi.accountingfinance.repository.InvoiceRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentStorageService storageService;
    private final TransactionRepository transactionRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final InvoiceRepository invoiceRepository;

    public Document findById(UUID id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Document not found with id: " + id));
    }

    public List<Document> findByTransactionId(UUID transactionId) {
        return documentRepository.findByTransactionIdOrderByCreatedAtDesc(transactionId);
    }

    public List<Document> findByJournalEntryId(UUID journalEntryId) {
        return documentRepository.findByJournalEntryIdOrderByCreatedAtDesc(journalEntryId);
    }

    public List<Document> findByInvoiceId(UUID invoiceId) {
        return documentRepository.findByInvoiceIdOrderByCreatedAtDesc(invoiceId);
    }

    public long countByTransactionId(UUID transactionId) {
        return documentRepository.countByTransactionId(transactionId);
    }

    @Transactional
    public Document uploadForTransaction(UUID transactionId, MultipartFile file, String uploadedBy) throws IOException {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found with id: " + transactionId));

        Document document = createDocument(file, uploadedBy);
        document.setTransaction(transaction);

        Document saved = documentRepository.save(document);
        log.info("Uploaded document {} for transaction {}", saved.getId(), transactionId);
        return saved;
    }

    @Transactional
    public Document uploadForJournalEntry(UUID journalEntryId, MultipartFile file, String uploadedBy) throws IOException {
        JournalEntry journalEntry = journalEntryRepository.findById(journalEntryId)
                .orElseThrow(() -> new EntityNotFoundException("Journal entry not found with id: " + journalEntryId));

        Document document = createDocument(file, uploadedBy);
        document.setJournalEntry(journalEntry);

        Document saved = documentRepository.save(document);
        log.info("Uploaded document {} for journal entry {}", saved.getId(), journalEntryId);
        return saved;
    }

    @Transactional
    public Document uploadForInvoice(UUID invoiceId, MultipartFile file, String uploadedBy) throws IOException {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found with id: " + invoiceId));

        Document document = createDocument(file, uploadedBy);
        document.setInvoice(invoice);

        Document saved = documentRepository.save(document);
        log.info("Uploaded document {} for invoice {}", saved.getId(), invoiceId);
        return saved;
    }

    @Transactional
    public void delete(UUID id) throws IOException {
        Document document = findById(id);

        // Delete file from storage
        storageService.delete(document.getStoragePath());

        // Soft delete the record
        document.softDelete();
        documentRepository.save(document);

        log.info("Deleted document {}", id);
    }

    public Resource loadAsResource(UUID id) {
        Document document = findById(id);
        return storageService.loadAsResource(document.getStoragePath());
    }

    private Document createDocument(MultipartFile file, String uploadedBy) throws IOException {
        // Validate and store file
        storageService.validateFile(file);
        String storagePath = storageService.store(file);
        String checksum = storageService.calculateChecksum(file);

        // Create document record
        Document document = new Document();
        document.setFilename(storagePath.substring(storagePath.lastIndexOf('/') + 1));
        document.setOriginalFilename(file.getOriginalFilename());
        document.setContentType(file.getContentType());
        document.setFileSize(file.getSize());
        document.setStoragePath(storagePath);
        document.setChecksumSha256(checksum);
        document.setUploadedBy(uploadedBy);

        return document;
    }
}
