package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.dto.ApproveDraftRequest;
import com.artivisi.accountingfinance.dto.CreateDraftRequest;
import com.artivisi.accountingfinance.dto.CreateFromReceiptRequest;
import com.artivisi.accountingfinance.dto.CreateFromTextRequest;
import com.artivisi.accountingfinance.dto.CreateTransactionRequest;
import com.artivisi.accountingfinance.dto.DraftResponse;
import com.artivisi.accountingfinance.dto.JournalEntryRequest;
import com.artivisi.accountingfinance.dto.TemplateSuggestion;
import com.artivisi.accountingfinance.dto.TransactionResponse;
import com.artivisi.accountingfinance.dto.UpdateDraftRequest;
import com.artivisi.accountingfinance.dto.UpdateTransactionRequest;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.Document;
import com.artivisi.accountingfinance.entity.DraftTransaction;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.JournalTemplateLine;
import com.artivisi.accountingfinance.entity.MerchantMapping;
import com.artivisi.accountingfinance.entity.Project;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.entity.TransactionAccountMapping;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.DocumentRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.MerchantMappingRepository;
import com.artivisi.accountingfinance.security.LogSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for AI-assisted transaction posting via REST API.
 * Accepts parsed data from external AI assistants (Claude Code, Gemini, etc.)
 * and creates draft transactions for approval.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class TransactionApiService {

    private static final BigDecimal CLARIFICATION_THRESHOLD = new BigDecimal("0.85");
    private static final String ERR_DRAFT_NOT_FOUND = "Draft not found: ";
    private static final String META_CATEGORY = "category";

    private static final String MANUAL_ENTRY_TEMPLATE_NAME = "Jurnal Manual";

    private final DraftTransactionService draftTransactionService;
    private final TransactionService transactionService;
    private final com.artivisi.accountingfinance.repository.TransactionRepository transactionRepository;
    private final JournalEntryService journalEntryService;
    private final JournalTemplateService journalTemplateService;
    private final TemplateExecutionEngine templateExecutionEngine;
    private final MerchantMappingRepository merchantMappingRepository;
    private final DocumentStorageService documentStorageService;
    private final DocumentRepository documentRepository;
    private final ChartOfAccountRepository chartOfAccountRepository;
    private final JournalTemplateRepository journalTemplateRepository;

    /**
     * Create draft transaction from AI-parsed receipt data.
     */
    public DraftResponse createFromReceipt(CreateFromReceiptRequest request) {
        log.info("Creating draft from receipt: merchant={}, amount={}, source={}",
                request.merchant(), request.amount(), request.source());

        // Validate future dates
        if (request.transactionDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Transaction date cannot be in the future");
        }

        // Find merchant mapping
        MerchantMapping mapping = findMerchantMapping(request.merchant());
        JournalTemplate suggestedTemplate = mapping != null ? mapping.getTemplate()
                : suggestTemplateByCategory(request.category());

        // Store image if provided
        Document document = null;
        if (request.imageBase64() != null && !request.imageBase64().isBlank()) {
            document = storeBase64Image(request.imageBase64(), request.merchant());
        }

        // Create draft
        DraftTransaction draft = new DraftTransaction();
        draft.setSource(DraftTransaction.Source.API);
        draft.setApiSource(request.source());
        draft.setSourceReference("API-" + UUID.randomUUID().toString().substring(0, 8));

        draft.setMerchantName(request.merchant());
        draft.setAmount(request.amount());
        draft.setTransactionDate(request.transactionDate());
        draft.setCurrency(request.currency());
        draft.setRawOcrText(request.rawText());

        draft.setOverallConfidence(request.confidence());
        draft.setSuggestedTemplate(suggestedTemplate);
        draft.setMerchantMapping(mapping);
        draft.setDocument(document);
        draft.setCreatedBy("API-" + request.source());

        // Store metadata
        Map<String, Object> metadata = new HashMap<>();
        if (request.items() != null && !request.items().isEmpty()) {
            metadata.put("items", request.items());
        }
        if (request.category() != null && !request.category().isBlank()) {
            metadata.put(META_CATEGORY, request.category());
        }
        draft.setMetadata(metadata);

        draft.setStatus(DraftTransaction.Status.PENDING);

        DraftTransaction saved = draftTransactionService.save(draft);
        log.info("Created draft {} from API source: {}", saved.getId(), request.source());

        return buildDraftResponse(saved);
    }

    /**
     * Create draft transaction from AI-parsed text.
     */
    public DraftResponse createFromText(CreateFromTextRequest request) {
        log.info("Creating draft from text: merchant={}, amount={}, source={}",
                request.merchant(), request.amount(), request.source());

        // Validate future dates
        if (request.transactionDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Transaction date cannot be in the future");
        }

        // Find merchant mapping
        MerchantMapping mapping = findMerchantMapping(request.merchant());
        JournalTemplate suggestedTemplate = mapping != null ? mapping.getTemplate()
                : suggestTemplateByCategory(request.category());

        // Create draft
        DraftTransaction draft = new DraftTransaction();
        draft.setSource(DraftTransaction.Source.API);
        draft.setApiSource(request.source());
        draft.setSourceReference("API-" + UUID.randomUUID().toString().substring(0, 8));

        draft.setMerchantName(request.merchant());
        draft.setAmount(request.amount());
        draft.setTransactionDate(request.transactionDate());
        draft.setCurrency(request.currency());
        draft.setRawOcrText(request.description());

        draft.setOverallConfidence(request.confidence());
        draft.setSuggestedTemplate(suggestedTemplate);
        draft.setMerchantMapping(mapping);
        draft.setCreatedBy("API-" + request.source());

        // Store metadata
        Map<String, Object> metadata = new HashMap<>();
        if (request.category() != null && !request.category().isBlank()) {
            metadata.put(META_CATEGORY, request.category());
        }
        if (request.description() != null && !request.description().isBlank()) {
            metadata.put("description", request.description());
        }
        draft.setMetadata(metadata);

        draft.setStatus(DraftTransaction.Status.PENDING);

        DraftTransaction saved = draftTransactionService.save(draft);
        log.info("Created draft {} from API source: {}", saved.getId(), request.source());

        return buildDraftResponse(saved);
    }

    /**
     * Get draft by ID.
     */
    @Transactional(readOnly = true)
    public DraftResponse getDraft(UUID draftId) {
        DraftTransaction draft = draftTransactionService.findById(draftId);
        return buildDraftResponse(draft);
    }

    /**
     * Approve draft and create transaction.
     */
    public DraftResponse approve(UUID draftId, ApproveDraftRequest request, String username) {
        log.info("Approving draft {} with template {}", draftId, request.templateId());

        DraftTransaction draft = draftTransactionService.approve(
                draftId,
                request.templateId(),
                request.description(),
                request.amount(),
                username
        );

        return buildDraftResponse(draft);
    }

    /**
     * Update a PENDING draft transaction fields.
     */
    public DraftResponse updateDraft(UUID draftId, UpdateDraftRequest request) {
        DraftTransaction draft = draftTransactionService.findById(draftId);

        if (!draft.isPending()) {
            throw new IllegalStateException("Only pending drafts can be updated: " + draft.getStatus());
        }

        if (request.merchantName() != null) {
            draft.setMerchantName(request.merchantName());
        }
        if (request.amount() != null) {
            draft.setAmount(request.amount());
        }
        if (request.description() != null) {
            draft.setRawOcrText(request.description());
        }
        if (request.suggestedTemplateId() != null) {
            JournalTemplate template = journalTemplateService.findByIdWithLines(request.suggestedTemplateId());
            draft.setSuggestedTemplate(template);
        }
        if (request.category() != null) {
            Map<String, Object> metadata = draft.getMetadata();
            if (metadata == null) {
                metadata = new HashMap<>();
            }
            metadata.put(META_CATEGORY, request.category());
            draft.setMetadata(metadata);
        }

        DraftTransaction saved = draftTransactionService.save(draft);
        log.info("Updated draft {} fields", saved.getId());

        return buildDraftResponse(saved);
    }

    /**
     * Update a DRAFT transaction (template, description, amount, date, account overrides).
     */
    public TransactionResponse updateTransaction(UUID transactionId, UpdateTransactionRequest request) {
        Transaction transaction = transactionService.findById(transactionId);

        if (!transaction.isDraft()) {
            throw new IllegalStateException("Only draft transactions can be updated: " + transaction.getStatus());
        }

        if (request.templateId() != null) {
            JournalTemplate template = journalTemplateService.findByIdWithLines(request.templateId());
            transaction.setJournalTemplate(template);
            journalTemplateService.recordUsage(template.getId());

            // Always replace account mappings when template changes — clears stale old mappings
            Map<UUID, UUID> accountMappings = resolveAccountSlots(template, request.accountSlots());
            transactionService.replaceAccountMappings(transaction, accountMappings);
        } else if (request.accountSlots() != null && !request.accountSlots().isEmpty()) {
            // Apply account slots to existing template
            JournalTemplate template = journalTemplateService.findByIdWithLines(
                    transaction.getJournalTemplate().getId());
            Map<UUID, UUID> accountMappings = resolveAccountSlots(template, request.accountSlots());
            if (!accountMappings.isEmpty()) {
                transactionService.replaceAccountMappings(transaction, accountMappings);
            }
        }
        if (request.description() != null) {
            transaction.setDescription(request.description());
        }
        if (request.amount() != null) {
            transaction.setAmount(request.amount());
        }
        if (request.transactionDate() != null) {
            if (request.transactionDate().isAfter(LocalDate.now())) {
                throw new IllegalArgumentException("Transaction date cannot be in the future");
            }
            transaction.setTransactionDate(request.transactionDate());
        }

        Transaction saved = transactionService.saveDirectly(transaction);
        log.info("Updated transaction {} via API", saved.getId());

        return buildTransactionResponse(saved);
    }

    /**
     * Delete a DRAFT transaction.
     */
    public void deleteTransaction(UUID transactionId) {
        transactionService.delete(transactionId);
        log.info("Deleted draft transaction {} via API", transactionId);
    }

    /**
     * Reject draft.
     */
    public DraftResponse reject(UUID draftId, String reason, String username) {
        log.info("Rejecting draft {}: {}",
                LogSanitizer.sanitize(draftId.toString()),
                LogSanitizer.sanitize(reason));

        DraftTransaction draft = draftTransactionService.reject(draftId, reason, username);
        return buildDraftResponse(draft);
    }

    /**
     * Preview journal entries for a DRAFT transaction using TemplateExecutionEngine.
     */
    @Transactional(readOnly = true)
    public TemplateExecutionEngine.PreviewResult previewJournalEntries(UUID transactionId) {
        Transaction transaction = transactionService.findByIdWithJournalEntries(transactionId);

        if (!transaction.isDraft()) {
            throw new IllegalStateException("Journal preview is only available for DRAFT transactions: " + transaction.getStatus());
        }

        // If transaction already has pre-created journal entries (free-form journal entry),
        // return them directly instead of running TemplateExecutionEngine
        if (!transaction.getJournalEntries().isEmpty()) {
            List<TemplateExecutionEngine.PreviewEntry> entries = transaction.getJournalEntries().stream()
                    .map(je -> new TemplateExecutionEngine.PreviewEntry(
                            je.getAccount().getAccountCode(),
                            je.getAccount().getAccountName(),
                            je.getDescription(),
                            je.getDebitAmount(),
                            je.getCreditAmount()))
                    .toList();
            BigDecimal totalDebit = entries.stream()
                    .map(TemplateExecutionEngine.PreviewEntry::debitAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalCredit = entries.stream()
                    .map(TemplateExecutionEngine.PreviewEntry::creditAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new TemplateExecutionEngine.PreviewResult(true, List.of(), entries, totalDebit, totalCredit);
        }

        // Traditional template-based preview
        transaction = transactionService.findByIdWithMappingsAndVariables(transactionId);
        JournalTemplate template = journalTemplateService.findByIdWithLines(transaction.getJournalTemplate().getId());

        // Build account mappings from stored TransactionAccountMappings (lineOrder → accountId)
        Map<String, String> accountMappings = new HashMap<>();
        for (TransactionAccountMapping mapping : transaction.getAccountMappings()) {
            int lineOrder = mapping.getTemplateLine().getLineOrder();
            accountMappings.put(String.valueOf(lineOrder), mapping.getAccount().getId().toString());
        }

        TemplateExecutionEngine.ExecutionContext context = new TemplateExecutionEngine.ExecutionContext(
                transaction.getTransactionDate(),
                transaction.getAmount(),
                transaction.getDescription(),
                transaction.getReferenceNumber(),
                Map.of(),
                accountMappings
        );

        return templateExecutionEngine.preview(template, context);
    }

    /**
     * Convert accountHint-based account slots to templateLineId-based mappings.
     * API accepts accountHint strings for clarity; internal code uses template line UUIDs.
     */
    private Map<UUID, UUID> resolveAccountSlots(JournalTemplate template, Map<String, UUID> accountSlots) {
        if (accountSlots == null || accountSlots.isEmpty()) {
            return Map.of();
        }

        Map<UUID, UUID> accountMappings = new HashMap<>();
        for (JournalTemplateLine line : template.getLines()) {
            if (line.getAccountHint() != null) {
                // Match by accountHint (e.g., "BANK") or by lineOrder (e.g., "2")
                UUID accountId = accountSlots.get(line.getAccountHint());
                if (accountId == null) {
                    accountId = accountSlots.get(String.valueOf(line.getLineOrder()));
                }
                if (accountId != null) {
                    accountMappings.put(line.getId(), accountId);
                }
            }
        }
        return accountMappings;
    }

    /**
     * Find merchant mapping by name.
     */
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

    /**
     * Suggest template based on category keyword.
     */
    private JournalTemplate suggestTemplateByCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }

        List<String> templateKeywords = findTemplateKeywords(category.toLowerCase());
        if (templateKeywords.isEmpty()) {
            return null;
        }

        List<JournalTemplate> allTemplates = journalTemplateService.findAll();
        for (JournalTemplate template : allTemplates) {
            String name = template.getTemplateName().toLowerCase();
            if (templateKeywords.stream().anyMatch(name::contains)) {
                return template;
            }
        }

        return null;
    }

    private List<String> findTemplateKeywords(String categoryLower) {
        if (containsAny(categoryLower, "food", "makan", "beverage")) {
            return List.of("meal", "makan");
        }
        if (containsAny(categoryLower, "utility", "listrik", "air")) {
            return List.of("utility", "utilitas");
        }
        if (containsAny(categoryLower, "transport", "travel", "perjalanan")) {
            return List.of("travel", "perjalanan");
        }
        if (containsAny(categoryLower, "office", "kantor", "supplies")) {
            return List.of("office", "kantor");
        }
        return List.of();
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Store base64-encoded image as document.
     */
    private Document storeBase64Image(String base64Data, String merchantName) {
        try {
            // Remove data URI prefix if present
            String base64Clean = base64Data;
            if (base64Data.contains(",")) {
                base64Clean = base64Data.substring(base64Data.indexOf(",") + 1);
            }

            byte[] imageBytes = Base64.getDecoder().decode(base64Clean);

            // Create simple MultipartFile wrapper
            String filename = "receipt-" + merchantName.replaceAll("[^a-zA-Z0-9]", "-") + ".jpg";

            var multipartFile = new org.springframework.web.multipart.MultipartFile() {
                @Override
                public String getName() {
                    return filename;
                }

                @Override
                public String getOriginalFilename() {
                    return filename;
                }

                @Override
                public String getContentType() {
                    return "image/jpeg";
                }

                @Override
                public boolean isEmpty() {
                    return imageBytes.length == 0;
                }

                @Override
                public long getSize() {
                    return imageBytes.length;
                }

                @Override
                public byte[] getBytes() {
                    return imageBytes;
                }

                @Override
                public java.io.InputStream getInputStream() {
                    return new java.io.ByteArrayInputStream(imageBytes);
                }

                @Override
                public void transferTo(java.io.File dest) throws java.io.IOException, IllegalStateException {
                    java.nio.file.Files.write(dest.toPath(), imageBytes);
                }
            };

            // Store via DocumentStorageService
            String storagePath = documentStorageService.store(multipartFile);

            // Create Document entity
            Document document = new Document();
            document.setFilename(filename);
            document.setOriginalFilename(filename);
            document.setStoragePath(storagePath);
            document.setFileSize((long) imageBytes.length);
            document.setContentType("image/jpeg");
            document.setUploadedBy("API");

            return documentRepository.save(document);

        } catch (IOException e) {
            log.error("Failed to store base64 image: {}", e.getMessage());
            throw new IllegalArgumentException("Failed to store image: " + e.getMessage(), e);
        }
    }

    /**
     * Build DraftResponse from DraftTransaction entity.
     */
    private DraftResponse buildDraftResponse(DraftTransaction draft) {
        TemplateSuggestion templateSuggestion = null;
        if (draft.getSuggestedTemplate() != null) {
            JournalTemplate template = draft.getSuggestedTemplate();
            templateSuggestion = new TemplateSuggestion(
                    template.getId(),
                    template.getTemplateName(),
                    template.getCategory().name()
            );
        }

        boolean needsClarification = draft.getOverallConfidence() != null
                && draft.getOverallConfidence().compareTo(CLARIFICATION_THRESHOLD) < 0;

        String clarificationQuestion = null;
        if (needsClarification) {
            clarificationQuestion = String.format(
                    "Apakah merchant '%s' dan jumlah %s sudah benar?",
                    draft.getMerchantName(),
                    draft.getAmountFormatted()
            );
        }

        UUID transactionId = draft.getTransaction() != null ? draft.getTransaction().getId() : null;

        return new DraftResponse(
                draft.getId(),
                draft.getStatus().name(),
                draft.getMerchantName(),
                draft.getAmount(),
                draft.getTransactionDate(),
                templateSuggestion,
                draft.getOverallConfidence(),
                needsClarification,
                clarificationQuestion,
                transactionId
        );
    }

    /**
     * Create a free-form journal entry (no template formulas).
     * Creates a DRAFT transaction with pre-created JournalEntry records.
     * The existing postWithContext() handles pre-created entries — it assigns
     * journal numbers and validates balance without running template formulas.
     */
    public TransactionResponse createJournalEntry(JournalEntryRequest request, String username) {
        log.info("Creating free-form journal entry: description={}, lines={}",
                request.description(), request.lines().size());

        // Validate each line: exactly one of debit/credit must be > 0
        for (int i = 0; i < request.lines().size(); i++) {
            JournalEntryRequest.JournalLineRequest line = request.lines().get(i);
            boolean hasDebit = line.debit().compareTo(BigDecimal.ZERO) > 0;
            boolean hasCredit = line.credit().compareTo(BigDecimal.ZERO) > 0;
            if (hasDebit && hasCredit) {
                throw new IllegalArgumentException(
                        "Line " + (i + 1) + ": cannot have both debit and credit > 0");
            }
            if (!hasDebit && !hasCredit) {
                throw new IllegalArgumentException(
                        "Line " + (i + 1) + ": must have either debit or credit > 0");
            }
        }

        // Validate balance: sum debits == sum credits
        BigDecimal totalDebits = request.lines().stream()
                .map(JournalEntryRequest.JournalLineRequest::debit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredits = request.lines().stream()
                .map(JournalEntryRequest.JournalLineRequest::credit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalDebits.compareTo(totalCredits) != 0) {
            throw new IllegalArgumentException(
                    "Journal entry is unbalanced: total debits=" + totalDebits + ", total credits=" + totalCredits);
        }

        // Validate accounts: each must exist, not be header, and be active
        List<ChartOfAccount> accounts = new ArrayList<>();
        for (int i = 0; i < request.lines().size(); i++) {
            JournalEntryRequest.JournalLineRequest line = request.lines().get(i);
            ChartOfAccount account = chartOfAccountRepository.findById(line.accountId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Account not found: " + line.accountId()));
            if (Boolean.TRUE.equals(account.getIsHeader())) {
                throw new IllegalArgumentException(
                        "Line " + (i + 1) + ": account '" + account.getAccountCode()
                                + "' is a header account and cannot be used in journal entries");
            }
            if (!Boolean.TRUE.equals(account.getActive())) {
                throw new IllegalArgumentException(
                        "Line " + (i + 1) + ": account '" + account.getAccountCode() + "' is inactive");
            }
            accounts.add(account);
        }

        // Look up "Jurnal Manual" template
        JournalTemplate template = journalTemplateRepository
                .findByTemplateNameAndIsCurrentVersionTrue(MANUAL_ENTRY_TEMPLATE_NAME)
                .orElseThrow(() -> new IllegalStateException(
                        "Manual entry template '" + MANUAL_ENTRY_TEMPLATE_NAME + "' not found"));

        // Create transaction
        Transaction transaction = new Transaction();
        transaction.setTransactionNumber(null); // deferred to posting
        transaction.setTransactionDate(request.transactionDate());
        transaction.setJournalTemplate(template);
        transaction.setAmount(totalDebits);
        transaction.setDescription(request.description());
        transaction.setNotes(request.category());
        transaction.setClosingEntry("CLOSING".equals(request.category()));
        transaction.setCreatedBy(username);

        // Create JournalEntry per line (pre-created, journalNumber deferred to posting)
        for (int i = 0; i < request.lines().size(); i++) {
            JournalEntryRequest.JournalLineRequest line = request.lines().get(i);
            JournalEntry entry = new JournalEntry();
            entry.setAccount(accounts.get(i));
            entry.setDebitAmount(line.debit());
            entry.setCreditAmount(line.credit());
            entry.setJournalNumber(null); // deferred to posting
            transaction.addJournalEntry(entry);
        }

        Transaction saved = transactionService.saveDirectly(transaction);
        log.info("Created free-form journal entry draft: id={}", saved.getId());

        return buildTransactionResponse(saved);
    }

    /**
     * Create a DRAFT transaction directly (bypassing the from-text/from-receipt draft workflow).
     * Creates a Transaction in DRAFT status with template + optional account overrides.
     */
    public TransactionResponse createDraft(CreateDraftRequest request, String username) {
        log.info("Creating draft directly: template={}, amount={}", request.templateId(), request.amount());

        if (request.transactionDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Transaction date cannot be in the future");
        }

        JournalTemplate template = journalTemplateService.findByIdWithLines(request.templateId());

        Transaction transaction = new Transaction();
        transaction.setTransactionNumber(null);
        transaction.setTransactionDate(request.transactionDate());
        transaction.setJournalTemplate(template);
        transaction.setAmount(request.amount());
        transaction.setDescription(request.description());
        transaction.setCreatedBy(username);

        if (request.projectId() != null) {
            Project project = new Project();
            project.setId(request.projectId());
            transaction.setProject(project);
        }

        Map<UUID, UUID> accountMappings = resolveAccountSlots(template, request.accountSlots());

        Transaction saved = transactionService.create(transaction, accountMappings, request.variables());

        log.info("Created draft transaction {} via direct API", saved.getId());

        return buildTransactionResponse(saved);
    }

    /**
     * Outcome of an idempotent direct post: the response plus whether an existing
     * transaction was replayed (HTTP 200) instead of created (HTTP 201).
     */
    public record IdempotentCreateResult(TransactionResponse response, boolean replayed) {}

    /**
     * Look up the transaction recorded for an Idempotency-Key, if any.
     * Used by the controller to resolve a concurrent-create race after the
     * unique index rejects the losing insert.
     */
    public Optional<TransactionResponse> findByIdempotencyKey(String idempotencyKey) {
        return transactionRepository.findByIdempotencyKeyWithJournalEntries(idempotencyKey)
                .map(this::buildTransactionResponse);
    }

    /**
     * Create and post transaction directly (bypass draft workflow).
     * Used after AI assistant has consulted user and received approval.
     *
     * When idempotencyKey is present and a transaction already carries it, the
     * original transaction is returned (replayed=true) and nothing is created.
     * The key is persisted with the transaction in the same database commit as
     * the posting, so a retry after a lost response cannot double-post.
     */
    public IdempotentCreateResult createTransactionDirect(
            CreateTransactionRequest request, String username, String idempotencyKey) {
        String key = idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey.trim() : null;
        if (key != null) {
            Optional<Transaction> existing = transactionRepository.findByIdempotencyKeyWithJournalEntries(key);
            if (existing.isPresent()) {
                log.info("Idempotency-Key replay: key={} -> transaction {}",
                        LogSanitizer.sanitize(key), existing.get().getId());
                return new IdempotentCreateResult(buildTransactionResponse(existing.get()), true);
            }
        }

        log.info("Creating transaction directly: merchant={}, amount={}, template={}, source={}",
                request.merchant(), request.amount(), request.templateId(), request.source());

        // Validate future dates
        if (request.transactionDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Transaction date cannot be in the future");
        }

        // Validate template exists
        JournalTemplate template = journalTemplateService.findByIdWithLines(request.templateId());

        // Create transaction
        Transaction transaction = new Transaction();
        transaction.setTransactionNumber(null); // Will be generated when posting
        transaction.setTransactionDate(request.transactionDate());
        transaction.setJournalTemplate(template);
        transaction.setAmount(request.amount());
        transaction.setDescription(request.description());
        transaction.setReferenceNumber("API-" + UUID.randomUUID().toString().substring(0, 8));
        transaction.setCreatedBy(username != null ? username : "API-" + request.source());
        transaction.setIdempotencyKey(key);

        // Record template usage
        journalTemplateService.recordUsage(template.getId());

        // Resolve accountHint-based slots to templateLineId-based mappings
        Map<UUID, UUID> accountMappings = resolveAccountSlots(template, request.accountSlots());

        // Save transaction (creates in DRAFT status)
        Transaction saved = transactionService.create(transaction, accountMappings, request.variables());

        // Post transaction immediately
        Transaction posted = transactionService.post(saved.getId(), username != null ? username : "API");

        // Load with journal entries
        Transaction complete = transactionService.findByIdWithJournalEntries(posted.getId());

        log.info("Created and posted transaction {} from API source: {}",
                complete.getTransactionNumber(), request.source());

        return new IdempotentCreateResult(buildTransactionResponse(complete), false);
    }

    /**
     * Build TransactionResponse from Transaction entity.
     */
    private TransactionResponse buildTransactionResponse(Transaction transaction) {
        List<TransactionResponse.JournalEntryDto> journalEntries = new ArrayList<>();

        if (transaction.getJournalEntries() != null) {
            for (JournalEntry entry : transaction.getJournalEntries()) {
                journalEntries.add(new TransactionResponse.JournalEntryDto(
                        entry.getJournalNumber(),
                        entry.getAccount().getAccountCode(),
                        entry.getAccount().getAccountName(),
                        entry.getDebitAmount(),
                        entry.getCreditAmount()
                ));
            }
        }

        return new TransactionResponse(
                transaction.getId(),
                transaction.getTransactionNumber(),
                transaction.getStatus().name(),
                transaction.getDescription(),
                transaction.getAmount(),
                transaction.getTransactionDate(),
                transaction.getDescription(),
                journalEntries
        );
    }
}
