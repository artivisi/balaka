package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.Document;
import com.artivisi.accountingfinance.entity.TaxDocument;
import com.artivisi.accountingfinance.entity.TaxFiling;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.TaxFilingType;
import com.artivisi.accountingfinance.repository.TaxDocumentRepository;
import com.artivisi.accountingfinance.repository.TaxFilingRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.security.LogSanitizer;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Register of period-linked tax records (issue #35). A masa pajak has no journal
 * entry of its own, so SPT/BPE/STP/SP2DK facts are kept here and joined back to
 * the ledger through the optional payment transaction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TaxFilingService {

    private final TaxFilingRepository taxFilingRepository;
    private final TaxDocumentRepository taxDocumentRepository;
    private final TransactionRepository transactionRepository;
    private final DocumentService documentService;

    public List<TaxFiling> findByFilters(TaxFilingType taxType, Integer year) {
        return taxFilingRepository.findByFilters(taxType, year);
    }

    public TaxFiling findById(UUID id) {
        return taxFilingRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Pelaporan pajak tidak ditemukan: " + id));
    }

    /**
     * Filings whose period overlaps the given date range. Used by the tax summary
     * to say which masa in the reported window have been filed.
     */
    public List<TaxFiling> findOverlapping(LocalDate startDate, LocalDate endDate) {
        return taxFilingRepository.findByYearRange(startDate.getYear(), endDate.getYear()).stream()
                .filter(f -> !f.getPeriodStart().isAfter(endDate) && !f.getPeriodEnd().isBefore(startDate))
                .toList();
    }

    @Transactional
    public TaxFiling create(TaxFiling filing, UUID paymentTransactionId) {
        validate(filing);
        taxFilingRepository.findByPeriodKey(filing.getTaxType(), filing.getPeriodYear(),
                        filing.getPeriodMonth(), filing.getPembetulanNo())
                .ifPresent(existing -> {
                    throw new IllegalStateException("Pelaporan " + filing.getTaxType()
                            + " masa " + filing.getPeriod() + " pembetulan ke-" + filing.getPembetulanNo()
                            + " sudah terdaftar dengan id " + existing.getId());
                });
        filing.setPaymentTransaction(resolvePaymentTransaction(paymentTransactionId));

        TaxFiling saved = taxFilingRepository.save(filing);
        log.info("Tax filing created - id={}, type={}, period={}, status={}",
                saved.getId(), saved.getTaxType(), saved.getPeriod(), saved.getStatus());
        return saved;
    }

    @Transactional
    public TaxFiling update(UUID id, TaxFiling changes, UUID paymentTransactionId) {
        TaxFiling existing = findById(id);
        validate(changes);

        // The period key identifies the filing; moving it would collide with another row
        taxFilingRepository.findByPeriodKey(changes.getTaxType(), changes.getPeriodYear(),
                        changes.getPeriodMonth(), changes.getPembetulanNo())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new IllegalStateException("Pelaporan " + changes.getTaxType()
                            + " masa " + changes.getPeriod() + " pembetulan ke-" + changes.getPembetulanNo()
                            + " sudah terdaftar dengan id " + other.getId());
                });

        existing.setTaxType(changes.getTaxType());
        existing.setPeriodYear(changes.getPeriodYear());
        existing.setPeriodMonth(changes.getPeriodMonth());
        existing.setPembetulanNo(changes.getPembetulanNo());
        existing.setStatus(changes.getStatus());
        existing.setFiledDate(changes.getFiledDate());
        existing.setBpeNumber(changes.getBpeNumber());
        existing.setBpeDate(changes.getBpeDate());
        existing.setKurangBayar(changes.getKurangBayar());
        existing.setLebihBayar(changes.getLebihBayar());
        existing.setNihil(changes.isNihil());
        existing.setBillingCode(changes.getBillingCode());
        existing.setNtpn(changes.getNtpn());
        existing.setPaidDate(changes.getPaidDate());
        existing.setNotes(changes.getNotes());
        existing.setPaymentTransaction(resolvePaymentTransaction(paymentTransactionId));

        TaxFiling saved = taxFilingRepository.save(existing);
        log.info("Tax filing updated - id={}, type={}, period={}, status={}",
                saved.getId(), saved.getTaxType(), saved.getPeriod(), saved.getStatus());
        return saved;
    }

    /**
     * Removes a filing. Refused while documents are attached: SPT/BPE/STP scans
     * carry a 10-year retention obligation, so they must be deleted deliberately
     * one by one rather than swept away with the parent record.
     */
    @Transactional
    public void delete(UUID id) {
        TaxFiling filing = findById(id);
        long attached = taxDocumentRepository.countByTaxFilingId(id);
        if (attached > 0) {
            throw new IllegalStateException("Pelaporan pajak masih memiliki " + attached
                    + " dokumen; hapus dokumennya lebih dulu");
        }
        taxFilingRepository.delete(filing);
        log.info("Tax filing deleted - id={}, type={}, period={}",
                id, filing.getTaxType(), filing.getPeriod());
    }

    // ==================== DOCUMENTS ====================

    public List<TaxDocument> findDocuments(UUID filingId) {
        findById(filingId);
        return taxDocumentRepository.findByFilingId(filingId);
    }

    public TaxDocument findDocument(UUID filingId, UUID taxDocumentId) {
        return taxDocumentRepository.findByIdAndFilingId(taxDocumentId, filingId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Dokumen pajak tidak ditemukan: " + taxDocumentId));
    }

    @Transactional
    public TaxDocument addDocument(UUID filingId, MultipartFile file, TaxDocument metadata, String uploadedBy)
            throws IOException {
        TaxFiling filing = findById(filingId);

        Document document = documentService.upload(file, uploadedBy);
        metadata.setTaxFiling(filing);
        metadata.setDocument(document);

        TaxDocument saved = taxDocumentRepository.save(metadata);
        log.info("Tax document attached - id={}, filingId={}, docType={}, docNumber={}",
                saved.getId(), filingId, saved.getDocType(),
                LogSanitizer.sanitize(saved.getDocNumber()));
        return saved;
    }

    public Resource loadDocument(UUID filingId, UUID taxDocumentId) {
        TaxDocument taxDocument = findDocument(filingId, taxDocumentId);
        return documentService.loadAsResource(taxDocument.getDocument().getId());
    }

    @Transactional
    public void deleteDocument(UUID filingId, UUID taxDocumentId) throws IOException {
        TaxDocument taxDocument = findDocument(filingId, taxDocumentId);
        UUID documentId = taxDocument.getDocument().getId();

        // Unlink first: DocumentService.delete refuses to orphan a linked tax document
        taxDocumentRepository.delete(taxDocument);
        taxDocumentRepository.flush();
        documentService.delete(documentId);

        log.info("Tax document deleted - id={}, filingId={}", taxDocumentId, filingId);
    }

    // ==================== VALIDATION ====================

    private void validate(TaxFiling filing) {
        if (filing.getTaxType().isMonthly() && filing.getPeriodMonth() == null) {
            throw new IllegalArgumentException(filing.getTaxType()
                    + " dilaporkan per masa; periode harus dalam format YYYY-MM");
        }
        if (!filing.getTaxType().isMonthly() && filing.getPeriodMonth() != null) {
            throw new IllegalArgumentException(filing.getTaxType()
                    + " dilaporkan per tahun; periode harus dalam format YYYY");
        }
        if (filing.isNihil() && (isPositive(filing.getKurangBayar()) || isPositive(filing.getLebihBayar()))) {
            throw new IllegalArgumentException(
                    "SPT nihil tidak boleh memiliki kurang bayar atau lebih bayar");
        }
        if (isPositive(filing.getKurangBayar()) && isPositive(filing.getLebihBayar())) {
            throw new IllegalArgumentException(
                    "Kurang bayar dan lebih bayar tidak dapat diisi bersamaan");
        }
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private Transaction resolvePaymentTransaction(UUID paymentTransactionId) {
        if (paymentTransactionId == null) {
            return null;
        }
        return transactionRepository.findById(paymentTransactionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Transaksi pembayaran tidak ditemukan: " + paymentTransactionId));
    }
}
