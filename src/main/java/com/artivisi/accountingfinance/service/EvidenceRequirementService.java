package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.EvidenceRequirement;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.enums.EvidenceType;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.EvidenceRequirementRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/** Maintains evidence requirement rules. Reads go through {@link EvidenceQueryService}. */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class EvidenceRequirementService {

    private final EvidenceRequirementRepository requirementRepository;
    private final ChartOfAccountRepository accountRepository;
    private final JournalTemplateRepository templateRepository;

    public record NewRequirement(UUID accountId, UUID templateId, EvidenceType evidenceType, BigDecimal minAmount,
                                 String notes, String createdBy) {}

    public EvidenceRequirement create(NewRequirement request) {
        if ((request.accountId() == null) == (request.templateId() == null)) {
            throw new IllegalArgumentException("Pilih tepat satu: akun atau template");
        }
        if (request.evidenceType() == null) {
            throw new IllegalArgumentException("Jenis bukti wajib diisi");
        }
        if (!request.evidenceType().isRequirable()) {
            throw new IllegalArgumentException("Jenis bukti " + request.evidenceType().getIndonesianName()
                    + " tidak dapat diwajibkan");
        }
        if (request.minAmount() != null && request.minAmount().signum() <= 0) {
            throw new IllegalArgumentException("Nilai minimum harus lebih dari nol, atau kosongkan untuk semua nilai");
        }
        EvidenceRequirement rule = new EvidenceRequirement();
        if (request.accountId() != null) {
            ChartOfAccount account = accountRepository.findById(request.accountId())
                    .orElseThrow(() -> new EntityNotFoundException("Akun tidak ditemukan: " + request.accountId()));
            if (requirementRepository.existsByAccountIdAndEvidenceType(account.getId(), request.evidenceType())) {
                throw new IllegalStateException("Aturan " + request.evidenceType().getIndonesianName() + " untuk akun "
                        + account.getAccountCode() + " sudah ada");
            }
            rule.setAccount(account);
        } else {
            JournalTemplate template = templateRepository.findById(request.templateId())
                    .orElseThrow(() -> new EntityNotFoundException("Template tidak ditemukan: " + request.templateId()));
            // Rules attach to the family root, so a later template version cannot hold a second copy of the rule.
            JournalTemplate root = template.getOriginalTemplate() != null ? template.getOriginalTemplate() : template;
            if (requirementRepository.existsByJournalTemplateIdAndEvidenceType(root.getId(), request.evidenceType())) {
                throw new IllegalStateException("Aturan " + request.evidenceType().getIndonesianName() + " untuk template "
                        + root.getTemplateName() + " sudah ada");
            }
            rule.setJournalTemplate(root);
        }
        rule.setEvidenceType(request.evidenceType());
        rule.setMinAmount(request.minAmount());
        rule.setNotes(request.notes() == null || request.notes().isBlank() ? null : request.notes().trim());
        rule.setCreatedBy(request.createdBy());
        EvidenceRequirement saved = requirementRepository.save(rule);
        log.info("Evidence requirement {} created: {} {}", saved.getId(), saved.getEvidenceType(),
                com.artivisi.accountingfinance.security.LogSanitizer.sanitize(saved.getTargetLabel()));
        return saved;
    }

    public void delete(UUID id) {
        EvidenceRequirement rule = requirementRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Aturan bukti tidak ditemukan: " + id));
        requirementRepository.delete(rule);
    }
}
