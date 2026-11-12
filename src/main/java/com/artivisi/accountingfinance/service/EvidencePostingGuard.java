package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.CompanyConfig;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.EvidenceType;
import com.artivisi.accountingfinance.repository.CompanyConfigRepository;
import com.artivisi.accountingfinance.service.EvidenceQueryService.TransactionEvidence;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.stream.Collectors;

/**
 * Evidence at posting (issue #51, build step 1). When {@link CompanyConfig#getEvidenceRequiredFrom()} is set, a
 * transaction dated on or after it cannot be posted while an evidence rule (#48) reports a missing type. The check
 * runs inside the posting transaction after the journal lines exist, so account rules see the accounts being posted.
 */
@Service
@RequiredArgsConstructor
public class EvidencePostingGuard {

    private final CompanyConfigRepository companyConfigRepository;
    private final EvidenceQueryService evidenceQueryService;
    private final EntityManager entityManager;

    /** The configured start date, or null when posting does not check evidence. */
    public LocalDate requiredFrom() {
        return companyConfigRepository.findFirst().map(CompanyConfig::getEvidenceRequiredFrom).orElse(null);
    }

    /** Throws when the transaction falls under the policy and lacks a required evidence type. Runs in the caller's transaction. */
    public void check(Transaction transaction) {
        LocalDate from = requiredFrom();
        if (from == null || transaction.getTransactionDate().isBefore(from)) {
            return;
        }
        entityManager.flush();
        TransactionEvidence evidence = evidenceQueryService.evidenceFor(transaction.getId());
        if (evidence.complete()) {
            return;
        }
        String missing = evidence.missing().stream().map(EvidenceType::getIndonesianName).collect(Collectors.joining(", "));
        throw new IllegalStateException("Posting ditolak: bukti belum lengkap (" + missing + "). Transaksi bertanggal "
                + transaction.getTransactionDate() + " wajib berbukti lengkap sejak " + from
                + ". Lampirkan dan klasifikasikan dokumennya, lalu posting lagi.");
    }
}
