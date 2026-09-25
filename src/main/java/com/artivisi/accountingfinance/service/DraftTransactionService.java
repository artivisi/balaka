package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.DraftTransaction;
import com.artivisi.accountingfinance.entity.Document;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.MerchantMapping;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.repository.DraftTransactionRepository;
import com.artivisi.accountingfinance.repository.MerchantMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class DraftTransactionService {

    private static final Logger log = LoggerFactory.getLogger(DraftTransactionService.class);
    private static final BigDecimal AUTO_APPROVE_THRESHOLD = new BigDecimal("0.90");
    private static final String ERR_DRAFT_NOT_FOUND = "Draft not found: ";

    private final DraftTransactionRepository draftRepository;
    private final MerchantMappingRepository merchantMappingRepository;
    private final Optional<VisionOcrService> visionOcrService;
    private final ReceiptParserService receiptParserService;
    private final TransactionService transactionService;

    @Autowired
    public DraftTransactionService(
            DraftTransactionRepository draftRepository,
            MerchantMappingRepository merchantMappingRepository,
            Optional<VisionOcrService> visionOcrService,
            ReceiptParserService receiptParserService,
            TransactionService transactionService) {
        this.draftRepository = draftRepository;
        this.merchantMappingRepository = merchantMappingRepository;
        this.visionOcrService = visionOcrService;
        this.receiptParserService = receiptParserService;
        this.transactionService = transactionService;
    }

    public DraftTransaction processReceiptImage(byte[] imageBytes, Document document,
                                                 Long telegramChatId, Long telegramMessageId,
                                                 String username) {
        // 1. OCR extraction
        if (visionOcrService.isEmpty()) {
            log.error("VisionOcrService is not available");
            return createFailedDraft(document, telegramChatId, telegramMessageId, username, "OCR service is not configured");
        }
        VisionOcrService.OcrResult ocrResult = visionOcrService.get().extractText(imageBytes);
        if (!ocrResult.success()) {
            log.error("OCR failed: {}", ocrResult.errorMessage());
            return createFailedDraft(document, telegramChatId, telegramMessageId, username, ocrResult.errorMessage());
        }

        // 2. Parse receipt
        ReceiptParserService.ParsedReceipt parsed = receiptParserService.parse(ocrResult.text());
        if (parsed == null) {
            log.warn("Receipt parsing returned null for OCR text");
            return createFailedDraft(document, telegramChatId, telegramMessageId, username, "Failed to parse receipt");
        }

        // 3. Find merchant mapping
        MerchantMapping mapping = findMerchantMapping(parsed.merchantName());
        JournalTemplate suggestedTemplate = mapping != null ? mapping.getTemplate() : null;

        // 4. Create draft
        DraftTransaction draft = new DraftTransaction();
        draft.setSource(DraftTransaction.Source.TELEGRAM);
        draft.setTelegramChatId(telegramChatId);
        draft.setTelegramMessageId(telegramMessageId);
        draft.setDocument(document);
        draft.setCreatedBy(username);

        draft.setMerchantName(parsed.merchantName());
        draft.setAmount(parsed.amount());
        draft.setTransactionDate(parsed.transactionDate());
        draft.setCurrency("IDR");
        draft.setRawOcrText(parsed.rawText());
        draft.setReceiptType(parsed.receiptType());
        draft.setSourceReference(parsed.reference());

        draft.setMerchantConfidence(parsed.merchantConfidence());
        draft.setAmountConfidence(parsed.amountConfidence());
        draft.setDateConfidence(parsed.dateConfidence());
        draft.setOverallConfidence(parsed.overallConfidence());

        draft.setSuggestedTemplate(suggestedTemplate);
        draft.setMerchantMapping(mapping);

        draft.setStatus(DraftTransaction.Status.PENDING);

        DraftTransaction saved = draftRepository.save(draft);
        log.info("Created draft transaction {} from Telegram", saved.getId());

        return saved;
    }

    private DraftTransaction createFailedDraft(Document document, Long chatId, Long messageId,
                                                String username, String errorMessage) {
        DraftTransaction draft = new DraftTransaction();
        draft.setSource(DraftTransaction.Source.TELEGRAM);
        draft.setTelegramChatId(chatId);
        draft.setTelegramMessageId(messageId);
        draft.setDocument(document);
        draft.setCreatedBy(username);
        draft.setRawOcrText("Error: " + errorMessage);
        draft.setOverallConfidence(BigDecimal.ZERO);
        draft.setStatus(DraftTransaction.Status.PENDING);

        return draftRepository.save(draft);
    }

    private MerchantMapping findMerchantMapping(String merchantName) {
        if (merchantName == null || merchantName.isBlank()) {
            return null;
        }

        // Try exact match first
        List<MerchantMapping> exactMatches = merchantMappingRepository.findExactMatches(merchantName);
        if (!exactMatches.isEmpty()) {
            return exactMatches.getFirst();
        }

        // Try contains match
        List<MerchantMapping> containsMatches = merchantMappingRepository.findContainsMatches(merchantName);
        if (!containsMatches.isEmpty()) {
            return containsMatches.getFirst();
        }

        // Try regex matches
        List<MerchantMapping> regexMappings = merchantMappingRepository.findByMatchType(MerchantMapping.MatchType.REGEX);
        for (MerchantMapping mapping : regexMappings) {
            if (mapping.matches(merchantName)) {
                return mapping;
            }
        }

        return null;
    }

    public DraftTransaction findById(UUID id) {
        return draftRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(ERR_DRAFT_NOT_FOUND + id));
    }

    public Page<DraftTransaction> findPending(Pageable pageable) {
        return draftRepository.findByStatusOrderByCreatedAtDesc(DraftTransaction.Status.PENDING, pageable);
    }

    public Page<DraftTransaction> findByFilters(DraftTransaction.Status status, Pageable pageable) {
        if (status != null) {
            return draftRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        }
        return draftRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public void delete(UUID id) {
        DraftTransaction draft = findById(id);
        if (!draft.isPending()) {
            throw new IllegalStateException("Only pending drafts can be deleted");
        }
        draftRepository.delete(draft);
    }

    public Page<DraftTransaction> findByUser(String username, Pageable pageable) {
        return draftRepository.findByCreatedBy(username, pageable);
    }

    public Page<DraftTransaction> findPendingByUser(String username, Pageable pageable) {
        return draftRepository.findByStatusAndCreatedBy(DraftTransaction.Status.PENDING, username, pageable);
    }

    public long countPending() {
        return draftRepository.countByStatus(DraftTransaction.Status.PENDING);
    }

    public long countPendingByUser(String username) {
        return draftRepository.countByCreatedByAndStatus(username, DraftTransaction.Status.PENDING);
    }

    public DraftTransaction approve(UUID draftId, UUID templateId, String description,
                                     BigDecimal amount, String approvedBy) {
        DraftTransaction draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new IllegalArgumentException(ERR_DRAFT_NOT_FOUND + draftId));

        if (!draft.isPending()) {
            throw new IllegalStateException("Draft is not pending: " + draft.getStatus());
        }

        // Create actual transaction
        Transaction transaction = transactionService.createFromDraft(
                draft, templateId, description, amount, approvedBy);

        // Update draft
        draft.approve(approvedBy);
        draft.setTransaction(transaction);

        // Update merchant mapping usage count
        if (draft.getMerchantMapping() != null) {
            draft.getMerchantMapping().incrementMatchCount();
        }

        return draftRepository.save(draft);
    }

    public DraftTransaction reject(UUID draftId, String reason, String rejectedBy) {
        DraftTransaction draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new IllegalArgumentException(ERR_DRAFT_NOT_FOUND + draftId));

        if (!draft.isPending()) {
            throw new IllegalStateException("Draft is not pending: " + draft.getStatus());
        }

        draft.reject(rejectedBy, reason);
        return draftRepository.save(draft);
    }

    public DraftTransaction save(DraftTransaction draft) {
        return draftRepository.save(draft);
    }
}
