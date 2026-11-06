package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.dto.FormulaContext;
import com.artivisi.accountingfinance.entity.AssetCategory;
import com.artivisi.accountingfinance.entity.AssetDisposalStatus;
import com.artivisi.accountingfinance.entity.AssetMovementSource;
import com.artivisi.accountingfinance.entity.AssetStatus;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.DepreciationEntry;
import com.artivisi.accountingfinance.entity.DepreciationEntryStatus;
import com.artivisi.accountingfinance.entity.DepreciationMethod;
import com.artivisi.accountingfinance.entity.Document;
import com.artivisi.accountingfinance.entity.FixedAsset;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.JournalTemplateLine;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.exception.MissingConfigurationException;
import com.artivisi.accountingfinance.repository.AssetCategoryRepository;
import com.artivisi.accountingfinance.repository.AssetDisposalRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.DepreciationEntryRepository;
import com.artivisi.accountingfinance.repository.FixedAssetRepository;
import com.artivisi.accountingfinance.security.CurrentUser;
import com.artivisi.accountingfinance.security.LogSanitizer;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FixedAssetService {

    private static final Logger log = LoggerFactory.getLogger(FixedAssetService.class);

    // Template IDs from V004 seed data
    private static final UUID DEPRECIATION_TEMPLATE_ID = UUID.fromString("e0000000-0000-0000-0000-000000000016");

    private static final String HINT_ASET_TETAP = "ASET_TETAP";
    private static final String ERR_DEPRECIATION_RATE_REQUIRED = "Tarif penyusutan wajib diisi untuk metode saldo menurun";
    private static final String ERR_DISPOSAL_DRAFT = "Aset masih punya draf pelepasan. Posting atau batalkan draf tersebut.";
    private static final Set<String> PHOTO_CONTENT_TYPES = Set.of("image/jpeg", "image/png");

    /** Source document type of the acquisition DRAFT composed by create(). */
    public static final String ACQUISITION_SOURCE_TYPE = "ASSET";

    private final FixedAssetRepository fixedAssetRepository;
    private final AssetCategoryRepository assetCategoryRepository;
    private final DepreciationEntryRepository depreciationEntryRepository;
    private final ChartOfAccountRepository chartOfAccountRepository;
    private final JournalTemplateService journalTemplateService;
    private final com.artivisi.accountingfinance.repository.JournalTemplateRepository journalTemplateRepository;
    private final TransactionService transactionService;
    private final DocumentPostingService documentPostingService;
    private final DocumentService documentService;
    private final AssetDisposalRepository assetDisposalRepository;
    private final AssetMovementRecorder movementRecorder;
    private final TransactionRepository transactionRepository;

    // ============================================
    // Asset CRUD Operations
    // ============================================

    public List<FixedAsset> findAll() {
        return fixedAssetRepository.findAll();
    }

    public List<FixedAsset> findAllActive() {
        return fixedAssetRepository.findAllActive();
    }

    public Page<FixedAsset> findByFilters(String search, AssetStatus status, UUID categoryId, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return fixedAssetRepository.findBySearch(search.trim(), status, categoryId, pageable);
        }
        return fixedAssetRepository.findByFilters(status, categoryId, pageable);
    }

    /**
     * Filtered listing for the REST API: optional purchase year, category, and status.
     */
    public Page<FixedAsset> findByApiFilters(Integer purchaseYear, UUID categoryId, AssetStatus status, Pageable pageable) {
        if (purchaseYear == null) {
            return fixedAssetRepository.findByFilters(status, categoryId, pageable);
        }
        return fixedAssetRepository.findByFiltersAndPurchaseDateRange(
                status, categoryId,
                LocalDate.of(purchaseYear, 1, 1), LocalDate.of(purchaseYear, 12, 31),
                pageable);
    }

    public FixedAsset findById(UUID id) {
        return fixedAssetRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Aset tidak ditemukan"));
    }

    public FixedAsset findByIdWithDetails(UUID id) {
        return fixedAssetRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new EntityNotFoundException("Aset tidak ditemukan"));
    }

    public FixedAsset findByAssetCode(String assetCode) {
        return fixedAssetRepository.findByAssetCode(assetCode)
                .orElseThrow(() -> new EntityNotFoundException("Aset dengan kode " + assetCode + " tidak ditemukan"));
    }

    @Transactional
    public FixedAsset create(FixedAsset asset) {
        return create(asset, null);
    }

    /**
     * Create a fixed asset. Exactly one acquisition source is required:
     * either a funding account (an acquisition DRAFT is composed: Dr asset / Cr funding)
     * or an existing purchase transaction to link (no new journal is created —
     * used when the purchase journal was already posted before registration).
     */
    @Transactional
    public FixedAsset create(FixedAsset asset, UUID purchaseTransactionId) {
        validateAsset(asset, null);
        asset.setBrand(AssetMovementRecorder.normalize(asset.getBrand()));
        asset.setModel(AssetMovementRecorder.normalize(asset.getModel()));
        asset.setSerialNumber(AssetMovementRecorder.normalize(asset.getSerialNumber()));
        asset.setCustodian(AssetMovementRecorder.normalize(asset.getCustodian()));
        asset.setLocation(AssetMovementRecorder.normalize(asset.getLocation()));

        // Load and set category
        AssetCategory category = assetCategoryRepository.findById(asset.getCategory().getId())
                .orElseThrow(() -> new EntityNotFoundException("Kategori aset tidak ditemukan"));

        // Fill unset depreciation settings from category defaults; accounts always from category
        asset.initializeFromCategory(category);

        // Validate accounts after category initialization
        validateAccountsInitialized(asset);

        // Validate depreciation settings after category defaults are applied
        if (asset.getDepreciationMethod() == DepreciationMethod.DECLINING_BALANCE
                && (asset.getDepreciationRate() == null || asset.getDepreciationRate().compareTo(BigDecimal.ZERO) <= 0)) {
            throw new IllegalArgumentException(ERR_DEPRECIATION_RATE_REQUIRED);
        }

        // Set book value to purchase cost initially
        asset.setBookValue(asset.getPurchaseCost());

        // Default depreciation start date to first of month after purchase
        if (asset.getDepreciationStartDate() == null) {
            asset.setDepreciationStartDate(asset.getPurchaseDate().withDayOfMonth(1));
        }

        if (purchaseTransactionId != null) {
            if (asset.getFundingAccount() != null) {
                throw new IllegalArgumentException(
                        "Pilih salah satu: akun pendanaan (membuat jurnal pembelian baru) "
                                + "atau transaksi pembelian yang sudah ada, tidak keduanya");
            }
            // Link the already-posted purchase journal; do not compose a new DRAFT
            Transaction purchase = transactionService.findById(purchaseTransactionId);
            asset.setPurchaseTransaction(purchase);
            FixedAsset saved = fixedAssetRepository.save(asset);
            log.info("Created fixed asset {} linked to purchase transaction {}",
                    LogSanitizer.sanitize(saved.getAssetCode()), purchaseTransactionId);
            return saved;
        }

        FixedAsset saved = fixedAssetRepository.save(asset);

        // Compose acquisition DRAFT via the template engine (Dr asset / Cr funding).
        // Funding account is required — error if unset (no fallback).
        if (saved.getFundingAccount() == null || saved.getFundingAccount().getId() == null) {
            throw new IllegalStateException(
                    "Akun pendanaan aset belum dipilih (bank/hutang yang dipakai untuk pembelian)");
        }
        Map<String, UUID> hints = new HashMap<>();
        hints.put(HINT_ASET_TETAP, saved.getAssetAccount().getId());
        hints.put("BANK", saved.getFundingAccount().getId());
        Map<String, BigDecimal> variables = new HashMap<>();
        variables.put("assetCost", saved.getPurchaseCost());
        documentPostingService.createDraftFromTemplate(DocumentPostingService.DraftRequest.builder()
                .templateName("Pembelian Aset Tetap")
                .date(saved.getPurchaseDate())
                .description("Pembelian " + saved.getAssetCode() + " - " + saved.getName())
                .amount(saved.getPurchaseCost())
                .hintToAccount(hints)
                .variables(variables)
                .createdBy("system")
                .sourceDocumentType(ACQUISITION_SOURCE_TYPE)
                .sourceDocumentId(saved.getId())
                .build());

        log.info("Created fixed asset: {} - {}", LogSanitizer.sanitize(saved.getAssetCode()), LogSanitizer.sanitize(saved.getName()));
        return saved;
    }

    private void validateAccountsInitialized(FixedAsset asset) {
        if (asset.getAssetAccount() == null) {
            throw new IllegalArgumentException("Akun aset wajib diisi (periksa konfigurasi kategori)");
        }
        if (asset.getAccumulatedDepreciationAccount() == null) {
            throw new IllegalArgumentException("Akun akumulasi penyusutan wajib diisi (periksa konfigurasi kategori)");
        }
        if (asset.getDepreciationExpenseAccount() == null) {
            throw new IllegalArgumentException("Akun beban penyusutan wajib diisi (periksa konfigurasi kategori)");
        }
    }

    @Transactional
    public FixedAsset update(UUID id, FixedAsset assetData) {
        FixedAsset existing = findById(id);

        if (existing.isDisposed()) {
            throw new IllegalStateException("Aset yang sudah dilepas tidak dapat diubah");
        }
        if (hasDraftDisposal(id)) {
            throw new IllegalStateException(ERR_DISPOSAL_DRAFT);
        }

        // Only allow editing if no depreciation has been recorded
        if (existing.getDepreciationPeriodsCompleted() > 0) {
            // Limited updates for assets with depreciation history
            existing.setName(assetData.getName());
            existing.setDescription(assetData.getDescription());
            existing.setNotes(assetData.getNotes());
        } else {
            validateAsset(assetData, id);

            if (assetData.getDepreciationMethod() == DepreciationMethod.DECLINING_BALANCE
                    && (assetData.getDepreciationRate() == null
                            || assetData.getDepreciationRate().compareTo(BigDecimal.ZERO) <= 0)) {
                throw new IllegalArgumentException(ERR_DEPRECIATION_RATE_REQUIRED);
            }

            existing.setAssetCode(assetData.getAssetCode());
            existing.setName(assetData.getName());
            existing.setDescription(assetData.getDescription());
            existing.setPurchaseDate(assetData.getPurchaseDate());
            existing.setPurchaseCost(assetData.getPurchaseCost());
            existing.setSupplier(assetData.getSupplier());
            existing.setInvoiceNumber(assetData.getInvoiceNumber());
            existing.setDepreciationMethod(assetData.getDepreciationMethod());
            existing.setUsefulLifeMonths(assetData.getUsefulLifeMonths());
            existing.setResidualValue(assetData.getResidualValue());
            existing.setDepreciationRate(assetData.getDepreciationRate());
            // Same derivation as create(): unset start date means first day of purchase month
            existing.setDepreciationStartDate(assetData.getDepreciationStartDate() != null
                    ? assetData.getDepreciationStartDate()
                    : assetData.getPurchaseDate().withDayOfMonth(1));
            existing.setNotes(assetData.getNotes());

            // Update book value
            existing.setBookValue(assetData.getPurchaseCost());

            // Update category if changed
            if (!existing.getCategory().getId().equals(assetData.getCategory().getId())) {
                AssetCategory newCategory = assetCategoryRepository.findById(assetData.getCategory().getId())
                        .orElseThrow(() -> new EntityNotFoundException("Kategori aset tidak ditemukan"));
                existing.setCategory(newCategory);
                existing.setAssetAccount(newCategory.getAssetAccount());
                existing.setAccumulatedDepreciationAccount(newCategory.getAccumulatedDepreciationAccount());
                existing.setDepreciationExpenseAccount(newCategory.getDepreciationExpenseAccount());
            }
        }

        // Identity is not a financial attribute: editable in both branches, also after depreciation.
        existing.setBrand(AssetMovementRecorder.normalize(assetData.getBrand()));
        existing.setModel(AssetMovementRecorder.normalize(assetData.getModel()));
        existing.setSerialNumber(AssetMovementRecorder.normalize(assetData.getSerialNumber()));
        movementRecorder.apply(existing, assetData.getCustodian(), assetData.getLocation(), assetData.getCondition(),
                LocalDate.now(), AssetMovementSource.EDIT, CurrentUser::name);

        // autoPost is an operational flag (scheduler posts vs. leaves PENDING), not a
        // financial attribute — togglable in both branches even after depreciation history.
        existing.setAutoPost(assetData.isAutoPost());

        FixedAsset saved = fixedAssetRepository.save(existing);
        log.info("Updated fixed asset: {}", LogSanitizer.sanitize(saved.getAssetCode()));
        return saved;
    }

    /**
     * Removing an asset is for a registration mistake that never reached the ledger. Anything else leaves through
     * the disposal flow: a linked purchase, a posted acquisition, depreciation history or any disposal record blocks it.
     * The acquisition DRAFT goes with the asset.
     */
    @Transactional
    public void delete(UUID id) {
        FixedAsset asset = findById(id);

        if (asset.getDepreciationPeriodsCompleted() > 0) {
            throw new IllegalStateException("Aset dengan riwayat penyusutan tidak dapat dihapus. Gunakan menu Lepas Aset.");
        }
        if (asset.getPurchaseTransaction() != null) {
            throw new IllegalStateException("Aset dengan transaksi pembelian tidak dapat dihapus. Gunakan menu Lepas Aset.");
        }
        if (assetDisposalRepository.existsByFixedAssetId(id)) {
            throw new IllegalStateException("Aset dengan catatan pelepasan tidak dapat dihapus.");
        }
        List<Transaction> acquisitions = acquisitionTransactions(id);
        if (acquisitions.stream().anyMatch(t -> t.getStatus() == TransactionStatus.POSTED)) {
            throw new IllegalStateException("Aset dengan jurnal pembelian yang sudah diposting tidak dapat dihapus. Gunakan menu Lepas Aset.");
        }

        if (asset.getPhotoDocument() != null) {
            deletePhotoDocument(asset);
        }
        depreciationEntryRepository.deleteByFixedAsset(asset);
        fixedAssetRepository.delete(asset);
        acquisitions.stream().filter(Transaction::isDraft).forEach(t -> transactionService.delete(t.getId()));
        log.info("Deleted fixed asset: {}", LogSanitizer.sanitize(asset.getAssetCode()));
    }

    public boolean isDeletable(FixedAsset asset) {
        return asset.getDepreciationPeriodsCompleted() == 0
                && asset.getPurchaseTransaction() == null
                && !assetDisposalRepository.existsByFixedAssetId(asset.getId())
                && acquisitionTransactions(asset.getId()).stream().noneMatch(t -> t.getStatus() == TransactionStatus.POSTED);
    }

    private List<Transaction> acquisitionTransactions(UUID assetId) {
        return transactionRepository.findBySourceDocumentTypeAndSourceDocumentIdOrderByTransactionDateAsc(
                ACQUISITION_SOURCE_TYPE, assetId);
    }

    public boolean hasDraftDisposal(UUID assetId) {
        return assetDisposalRepository.existsByFixedAssetIdAndStatus(assetId, AssetDisposalStatus.DRAFT);
    }

    // ============================================
    // Photo
    // ============================================

    /** Stores the photo through the document store and replaces any earlier one. */
    @Transactional
    public FixedAsset attachPhoto(UUID id, MultipartFile file, String uploadedBy) throws IOException {
        FixedAsset asset = findById(id);
        if (file == null || file.getContentType() == null || !PHOTO_CONTENT_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("Foto aset harus berformat JPEG atau PNG");
        }
        if (asset.getPhotoDocument() != null) {
            deletePhotoDocument(asset);
        }
        asset.setPhotoDocument(documentService.upload(file, uploadedBy));
        return fixedAssetRepository.save(asset);
    }

    @Transactional
    public void removePhoto(UUID id) throws IOException {
        FixedAsset asset = findById(id);
        if (asset.getPhotoDocument() == null) {
            throw new IllegalStateException("Aset belum punya foto");
        }
        deletePhotoDocument(asset);
        fixedAssetRepository.save(asset);
    }

    public record AssetPhoto(String contentType, Resource resource) {
    }

    public AssetPhoto loadPhoto(UUID id) {
        Document photo = findById(id).getPhotoDocument();
        if (photo == null) {
            throw new EntityNotFoundException("Aset belum punya foto");
        }
        return new AssetPhoto(photo.getContentType(), documentService.loadAsResource(photo.getId()));
    }

    private void deletePhotoDocument(FixedAsset asset) {
        UUID documentId = asset.getPhotoDocument().getId();
        asset.setPhotoDocument(null);
        fixedAssetRepository.flush();
        try {
            documentService.delete(documentId);
        } catch (IOException e) {
            throw new IllegalStateException("Gagal menghapus foto aset: " + e.getMessage(), e);
        }
    }

    public boolean existsByAssetCode(String assetCode) {
        return fixedAssetRepository.existsByAssetCode(assetCode);
    }

    // ============================================
    // Depreciation Operations
    // ============================================

    /**
     * Calculate monthly depreciation amount for an asset.
     */
    public BigDecimal calculateMonthlyDepreciation(FixedAsset asset) {
        if (asset.isFullyDepreciated()) {
            return BigDecimal.ZERO;
        }

        BigDecimal depreciableBase = asset.getDepreciableBase();

        if (asset.getDepreciationMethod() == DepreciationMethod.STRAIGHT_LINE) {
            // Straight-line: (Cost - Residual) / Useful Life
            return depreciableBase.divide(BigDecimal.valueOf(asset.getUsefulLifeMonths()), 2, RoundingMode.HALF_UP);
        } else {
            // Declining balance: Book Value * (Rate / 12)
            if (asset.getDepreciationRate() == null || asset.getDepreciationRate().compareTo(BigDecimal.ZERO) == 0) {
                throw new IllegalStateException(ERR_DEPRECIATION_RATE_REQUIRED);
            }
            BigDecimal calculated = asset.getBookValue().multiply(asset.getDepreciationRate())
                    .divide(BigDecimal.valueOf(1200), 2, RoundingMode.HALF_UP);

            // Ensure we don't depreciate below residual value
            BigDecimal remaining = asset.getRemainingDepreciation();
            return calculated.min(remaining);
        }
    }

    /**
     * Generate depreciation entries for a period.
     * Does not post to journal - entries are created as PENDING.
     */
    @Transactional
    public List<DepreciationEntry> generateDepreciationEntries(YearMonth period) {
        generatePeriodEntries(period);
        return depreciationEntryRepository.findAllPendingWithDetails();
    }

    /**
     * Generate depreciation entries for a period as PENDING.
     *
     * @return only the entries created by this call
     */
    @Transactional
    public List<DepreciationEntry> generatePeriodEntries(YearMonth period) {
        LocalDate periodStart = period.atDay(1);
        LocalDate periodEnd = period.atEndOfMonth();

        List<FixedAsset> assets = fixedAssetRepository.findAssetsNeedingDepreciation(periodEnd);

        if (assets.isEmpty()) {
            log.info("No assets need depreciation for period {}", period);
            return List.of();
        }

        List<DepreciationEntry> created = new ArrayList<>();

        log.info("Generating depreciation for {} assets in period {}", assets.size(), period);

        for (FixedAsset asset : assets) {
            // Check if entry already exists for this period
            if (depreciationEntryRepository.findByAssetIdAndPeriodEnd(asset.getId(), periodEnd).isPresent()) {
                log.debug("Depreciation entry already exists for asset {} in period {}",
                        LogSanitizer.sanitize(asset.getAssetCode()), period);
                continue;
            }

            BigDecimal depreciationAmount = calculateMonthlyDepreciation(asset);

            // Handle last period rounding
            int periodsRemaining = asset.getRemainingUsefulLifeMonths();
            if (periodsRemaining == 1) {
                depreciationAmount = asset.getRemainingDepreciation();
            }

            // Only create entry if depreciation amount is positive
            if (depreciationAmount.compareTo(BigDecimal.ZERO) > 0) {
                // Create depreciation entry
                DepreciationEntry entry = new DepreciationEntry();
                entry.setFixedAsset(asset);
                // Schedule-derived and independent of posting state: two months generated
                // back-to-back while the first is still PENDING must not collide (#32)
                entry.setPeriodNumber((int) java.time.temporal.ChronoUnit.MONTHS.between(
                        YearMonth.from(asset.getDepreciationStartDate()), period) + 1);
                entry.setPeriodStart(periodStart);
                entry.setPeriodEnd(periodEnd);
                entry.setDepreciationAmount(depreciationAmount);
                entry.setAccumulatedDepreciation(asset.getAccumulatedDepreciation().add(depreciationAmount));
                entry.setBookValue(asset.getBookValue().subtract(depreciationAmount));
                entry.setStatus(DepreciationEntryStatus.PENDING);
                entry.setGeneratedAt(LocalDateTime.now());

                created.add(depreciationEntryRepository.save(entry));
                log.debug("Generated depreciation entry for asset {}: {}", asset.getAssetCode(), depreciationAmount);
            }
        }

        return created;
    }

    /**
     * Generate depreciation entries for every month still owed, up to and
     * including the given period. Iterates from each asset's schedule position
     * (lastDepreciationDate + 1 month, or depreciationStartDate for assets that
     * never depreciated) so late-registered assets self-heal (#31). Generation
     * is idempotent per asset+month.
     *
     * @return all PENDING entries after generation
     */
    @Transactional
    public List<DepreciationEntry> generateDepreciationCatchUp(YearMonth upToPeriod) {
        LocalDate upToEnd = upToPeriod.atEndOfMonth();
        List<FixedAsset> assets = fixedAssetRepository.findAssetsNeedingDepreciation(upToEnd);

        YearMonth earliest = upToPeriod;
        for (FixedAsset asset : assets) {
            YearMonth firstNeeded = asset.getLastDepreciationDate() != null
                    ? YearMonth.from(asset.getLastDepreciationDate()).plusMonths(1)
                    : YearMonth.from(asset.getDepreciationStartDate());
            if (firstNeeded.isBefore(earliest)) {
                earliest = firstNeeded;
            }
        }

        if (earliest.isBefore(upToPeriod)) {
            log.info("Depreciation catch-up: generating {} through {}", earliest, upToPeriod);
        }
        for (YearMonth period = earliest; !period.isAfter(upToPeriod); period = period.plusMonths(1)) {
            generatePeriodEntries(period);
        }
        return depreciationEntryRepository.findAllPendingWithDetails();
    }

    /**
     * Filtered listing of depreciation entries for the REST API.
     */
    public List<DepreciationEntry> findDepreciationEntries(YearMonth period, DepreciationEntryStatus status) {
        if (period == null) {
            return depreciationEntryRepository.findAllByOptionalStatus(status);
        }
        return depreciationEntryRepository.findByPeriodEndAndOptionalStatus(period.atEndOfMonth(), status);
    }

    /**
     * Post a single depreciation entry to journal.
     */
    @Transactional
    public DepreciationEntry postDepreciationEntry(UUID entryId, String postedBy) {
        DepreciationEntry entry = depreciationEntryRepository.findById(entryId)
                .orElseThrow(() -> new EntityNotFoundException("Entri penyusutan tidak ditemukan"));
        return doPostDepreciationEntry(entry, postedBy);
    }

    private DepreciationEntry doPostDepreciationEntry(DepreciationEntry entry, String postedBy) {
        if (!entry.isPending()) {
            throw new IllegalStateException("Hanya entri dengan status PENDING yang dapat di-posting");
        }

        FixedAsset asset = entry.getFixedAsset();
        if (asset.isDisposed()) {
            throw new IllegalStateException("Aset sudah dilepas; entri penyusutan tidak dapat di-posting");
        }
        if (hasDraftDisposal(asset.getId())) {
            throw new IllegalStateException(ERR_DISPOSAL_DRAFT);
        }

        // Get depreciation template with lines loaded, fall back to name lookup
        JournalTemplate template = journalTemplateRepository.findById(DEPRECIATION_TEMPLATE_ID)
                .map(t -> journalTemplateService.findByIdWithLines(t.getId()))
                .orElseGet(() -> journalTemplateRepository.findByTemplateNameAndIsCurrentVersionTrue("Penyusutan Aset")
                        .map(t -> journalTemplateService.findByIdWithLines(t.getId()))
                        .orElseThrow(() -> new MissingConfigurationException(
                                "Template jurnal penyusutan tidak ditemukan (id " + DEPRECIATION_TEMPLATE_ID
                                        + " atau template current-version bernama 'Penyusutan Aset'). "
                                        + "Buat template sistem penyusutan dengan baris DEBIT hint BEBAN_PENYUSUTAN "
                                        + "dan CREDIT hint AKUM_PENYUSUTAN.")));

        // Create FormulaContext with depreciation variables
        FormulaContext context = FormulaContext.of(
                entry.getDepreciationAmount(),
                Map.of("depreciationAmount", entry.getDepreciationAmount())
        );

        // Create transaction
        Transaction transaction = new Transaction();
        transaction.setJournalTemplate(template);
        transaction.setTransactionDate(entry.getPeriodEnd());
        transaction.setAmount(entry.getDepreciationAmount());
        transaction.setDescription("Penyusutan " + asset.getName() + " - " + entry.getPeriodDisplayName());
        transaction.setReferenceNumber("DEP-" + asset.getAssetCode() + "-" + entry.getPeriodNumber());

        // Create account mappings for dynamic accounts (lines with NULL account and account_hint)
        Map<UUID, UUID> accountMappings = new HashMap<>();
        // Map template lines with account_hint to asset-specific accounts
        for (JournalTemplateLine line : template.getLines()) {
            if (line.getAccount() == null && line.getAccountHint() != null) {
                switch (line.getAccountHint()) {
                    case "AKUM_PENYUSUTAN":
                        accountMappings.put(line.getId(), asset.getAccumulatedDepreciationAccount().getId());
                        break;
                    case "BEBAN_PENYUSUTAN":
                        accountMappings.put(line.getId(), asset.getDepreciationExpenseAccount().getId());
                        break;
                    case HINT_ASET_TETAP:
                        accountMappings.put(line.getId(), asset.getAssetAccount().getId());
                        break;
                    default:
                        log.warn("Unrecognized account hint '{}' in depreciation template line {}",
                                LogSanitizer.sanitize(line.getAccountHint()), line.getId());
                        break;
                }
            }
        }

        Transaction savedTransaction = transactionService.create(transaction, accountMappings);
        Transaction postedTransaction = transactionService.postSystem(savedTransaction.getId(), postedBy, context);

        // Update entry
        entry.setStatus(DepreciationEntryStatus.POSTED);
        entry.setTransaction(postedTransaction);
        entry.setPostedAt(LocalDateTime.now());

        // Update asset
        asset.recordDepreciation(entry.getDepreciationAmount(), entry.getPeriodEnd());

        DepreciationEntry savedEntry = depreciationEntryRepository.save(entry);
        fixedAssetRepository.save(asset);

        log.info("Posted depreciation entry for asset {}: {} (period {})",
                asset.getAssetCode(), entry.getDepreciationAmount(), entry.getPeriodNumber());

        return savedEntry;
    }

    /**
     * Post all pending depreciation entries for a period.
     */
    @Transactional
    public int postAllPendingDepreciation(YearMonth period, String postedBy) {
        LocalDate periodEnd = period.atEndOfMonth();

        List<DepreciationEntry> pendingEntries = depreciationEntryRepository.findAllPending()
                .stream()
                .filter(e -> !e.getPeriodEnd().isAfter(periodEnd))
                .toList();

        int count = 0;
        for (DepreciationEntry entry : pendingEntries) {
            doPostDepreciationEntry(entry, postedBy);
            count++;
        }

        log.info("Posted {} depreciation entries for period up to {}", count, period);
        return count;
    }

    /**
     * Skip a depreciation entry.
     */
    @Transactional
    public void skipDepreciationEntry(UUID entryId) {
        DepreciationEntry entry = depreciationEntryRepository.findById(entryId)
                .orElseThrow(() -> new EntityNotFoundException("Entri penyusutan tidak ditemukan"));

        if (!entry.isPending()) {
            throw new IllegalStateException("Hanya entri dengan status PENDING yang dapat di-skip");
        }

        entry.setStatus(DepreciationEntryStatus.SKIPPED);
        depreciationEntryRepository.save(entry);

        log.info("Skipped depreciation entry {} for asset {}",
                entry.getId(), LogSanitizer.sanitize(entry.getFixedAsset().getAssetCode()));
    }

    // ============================================
    // Reporting
    // ============================================

    public List<DepreciationEntry> getDepreciationHistory(UUID assetId) {
        return depreciationEntryRepository.findByAssetIdWithAsset(assetId);
    }

    public List<DepreciationEntry> getPendingDepreciationEntries() {
        return depreciationEntryRepository.findAllPendingWithDetails();
    }

    public long countPendingDepreciationEntries() {
        return depreciationEntryRepository.countPending();
    }

    public BigDecimal getTotalBookValue() {
        BigDecimal result = fixedAssetRepository.sumBookValue();
        return result != null ? result : BigDecimal.ZERO;
    }

    public BigDecimal getTotalPurchaseCost() {
        BigDecimal result = fixedAssetRepository.sumPurchaseCost();
        return result != null ? result : BigDecimal.ZERO;
    }

    public BigDecimal getTotalAccumulatedDepreciation() {
        BigDecimal result = fixedAssetRepository.sumAccumulatedDepreciation();
        return result != null ? result : BigDecimal.ZERO;
    }

    // ============================================
    // Validation
    // ============================================

    private void validateAsset(FixedAsset asset, UUID excludeId) {
        // Check for duplicate asset code
        if (excludeId == null) {
            if (fixedAssetRepository.existsByAssetCode(asset.getAssetCode())) {
                throw new IllegalArgumentException("Kode aset " + asset.getAssetCode() + " sudah digunakan");
            }
        } else {
            fixedAssetRepository.findByAssetCode(asset.getAssetCode())
                    .ifPresent(existing -> {
                        if (!existing.getId().equals(excludeId)) {
                            throw new IllegalArgumentException("Kode aset " + asset.getAssetCode() + " sudah digunakan");
                        }
                    });
        }

        // Validate residual value
        if (asset.getResidualValue() != null && asset.getResidualValue().compareTo(asset.getPurchaseCost()) >= 0) {
            throw new IllegalArgumentException("Nilai residu harus lebih kecil dari nilai perolehan");
        }
    }
}
