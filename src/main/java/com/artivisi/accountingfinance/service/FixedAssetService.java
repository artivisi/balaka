package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.dto.FormulaContext;
import com.artivisi.accountingfinance.entity.AssetCategory;
import com.artivisi.accountingfinance.entity.AssetStatus;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.DepreciationEntry;
import com.artivisi.accountingfinance.entity.DepreciationEntryStatus;
import com.artivisi.accountingfinance.entity.DepreciationMethod;
import com.artivisi.accountingfinance.entity.DisposalType;
import com.artivisi.accountingfinance.entity.FixedAsset;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.JournalTemplateLine;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.repository.AssetCategoryRepository;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.DepreciationEntryRepository;
import com.artivisi.accountingfinance.repository.FixedAssetRepository;
import com.artivisi.accountingfinance.security.LogSanitizer;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FixedAssetService {

    private static final Logger log = LoggerFactory.getLogger(FixedAssetService.class);

    // Template IDs from V004 seed data
    private static final UUID DEPRECIATION_TEMPLATE_ID = UUID.fromString("e0000000-0000-0000-0000-000000000016");
    private static final UUID DISPOSAL_TEMPLATE_ID = UUID.fromString("e0000000-0000-0000-0000-000000000017");

    private final FixedAssetRepository fixedAssetRepository;
    private final AssetCategoryRepository assetCategoryRepository;
    private final DepreciationEntryRepository depreciationEntryRepository;
    private final ChartOfAccountRepository chartOfAccountRepository;
    private final JournalTemplateService journalTemplateService;
    private final com.artivisi.accountingfinance.repository.JournalTemplateRepository journalTemplateRepository;
    private final TransactionService transactionService;
    private final DocumentPostingService documentPostingService;

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
            throw new IllegalArgumentException("Tarif penyusutan wajib diisi untuk metode saldo menurun");
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
        hints.put("ASET_TETAP", saved.getAssetAccount().getId());
        hints.put("BANK", saved.getFundingAccount().getId());
        Map<String, BigDecimal> variables = new HashMap<>();
        variables.put("assetCost", saved.getPurchaseCost());
        documentPostingService.createDraftFromTemplate(
                null, "Pembelian Aset Tetap", saved.getPurchaseDate(),
                "Pembelian " + saved.getAssetCode() + " - " + saved.getName(),
                saved.getPurchaseCost(), hints, variables, "system", "ASSET", saved.getId());

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

        // Only allow editing if no depreciation has been recorded
        if (existing.getDepreciationPeriodsCompleted() > 0) {
            // Limited updates for assets with depreciation history
            existing.setName(assetData.getName());
            existing.setDescription(assetData.getDescription());
            existing.setLocation(assetData.getLocation());
            existing.setSerialNumber(assetData.getSerialNumber());
            existing.setNotes(assetData.getNotes());
        } else {
            validateAsset(assetData, id);

            if (assetData.getDepreciationMethod() == DepreciationMethod.DECLINING_BALANCE
                    && (assetData.getDepreciationRate() == null
                            || assetData.getDepreciationRate().compareTo(BigDecimal.ZERO) <= 0)) {
                throw new IllegalArgumentException("Tarif penyusutan wajib diisi untuk metode saldo menurun");
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
            existing.setLocation(assetData.getLocation());
            existing.setSerialNumber(assetData.getSerialNumber());
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

        FixedAsset saved = fixedAssetRepository.save(existing);
        log.info("Updated fixed asset: {}", LogSanitizer.sanitize(saved.getAssetCode()));
        return saved;
    }

    @Transactional
    public void delete(UUID id) {
        FixedAsset asset = findById(id);

        if (asset.getDepreciationPeriodsCompleted() > 0) {
            throw new IllegalStateException("Aset dengan riwayat penyusutan tidak dapat dihapus");
        }

        if (asset.getPurchaseTransaction() != null) {
            throw new IllegalStateException("Aset dengan transaksi pembelian tidak dapat dihapus");
        }

        depreciationEntryRepository.deleteByFixedAsset(asset);
        fixedAssetRepository.delete(asset);
        log.info("Deleted fixed asset: {}", LogSanitizer.sanitize(asset.getAssetCode()));
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
                throw new IllegalStateException("Tarif penyusutan wajib diisi untuk metode saldo menurun");
            }
            BigDecimal monthlyRate = asset.getDepreciationRate().divide(BigDecimal.valueOf(1200), 6, RoundingMode.HALF_UP);
            BigDecimal calculated = asset.getBookValue().multiply(monthlyRate).setScale(2, RoundingMode.HALF_UP);

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
        LocalDate periodStart = period.atDay(1);
        LocalDate periodEnd = period.atEndOfMonth();

        List<FixedAsset> assets = fixedAssetRepository.findAssetsNeedingDepreciation(periodEnd);

        if (assets.isEmpty()) {
            log.info("No assets need depreciation for period {}", period);
            return List.of();
        }

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
                entry.setPeriodNumber(asset.getDepreciationPeriodsCompleted() + 1);
                entry.setPeriodStart(periodStart);
                entry.setPeriodEnd(periodEnd);
                entry.setDepreciationAmount(depreciationAmount);
                entry.setAccumulatedDepreciation(asset.getAccumulatedDepreciation().add(depreciationAmount));
                entry.setBookValue(asset.getBookValue().subtract(depreciationAmount));
                entry.setStatus(DepreciationEntryStatus.PENDING);
                entry.setGeneratedAt(LocalDateTime.now());

                depreciationEntryRepository.save(entry);
                log.debug("Generated depreciation entry for asset {}: {}", asset.getAssetCode(), depreciationAmount);
            }
        }

        return depreciationEntryRepository.findAllPendingWithDetails();
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

        // Get depreciation template with lines loaded, fall back to name lookup
        JournalTemplate template = journalTemplateRepository.findById(DEPRECIATION_TEMPLATE_ID)
                .map(t -> journalTemplateService.findByIdWithLines(t.getId()))
                .orElseGet(() -> journalTemplateRepository.findByTemplateNameAndIsCurrentVersionTrue("Penyusutan Aset")
                        .map(t -> journalTemplateService.findByIdWithLines(t.getId()))
                        .orElseThrow(() -> new IllegalStateException("Template penyusutan tidak ditemukan")));

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
                    case "ASET_TETAP":
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
        Transaction postedTransaction = transactionService.post(savedTransaction.getId(), postedBy, context);

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
    // Disposal Operations
    // ============================================

    /**
     * Dispose an asset (sell, write-off, or transfer).
     */
    @Transactional
    public FixedAsset disposeAsset(UUID assetId, DisposalType type, BigDecimal proceeds,
                                    String notes, LocalDate disposalDate, String disposedBy) {
        FixedAsset asset = findByIdWithDetails(assetId);

        if (asset.isDisposed()) {
            throw new IllegalStateException("Aset sudah dilepas sebelumnya");
        }

        // Dispose the asset
        asset.dispose(type, proceeds, notes, disposalDate);

        // Get disposal template with lines loaded
        JournalTemplate template = journalTemplateService.findByIdWithLines(DISPOSAL_TEMPLATE_ID);

        // Create FormulaContext with disposal variables
        BigDecimal safeProceeds = proceeds != null ? proceeds : BigDecimal.ZERO;
        FormulaContext context = FormulaContext.of(
                asset.getPurchaseCost(),
                Map.of(
                        "assetCost", asset.getPurchaseCost(),
                        "accumulatedDepreciation", asset.getAccumulatedDepreciation(),
                        "bookValue", asset.getBookValue(),
                        "disposalProceeds", safeProceeds,
                        "gainLoss", asset.getGainLossOnDisposal()
                )
        );

        // Create account mappings for dynamic accounts (lines with NULL account and account_hint)
        Map<UUID, UUID> accountMappings = new HashMap<>();
        for (JournalTemplateLine line : template.getLines()) {
            if (line.getAccount() == null && line.getAccountHint() != null) {
                switch (line.getAccountHint()) {
                    case "AKUM_PENYUSUTAN":
                        accountMappings.put(line.getId(), asset.getAccumulatedDepreciationAccount().getId());
                        break;
                    case "ASET_TETAP":
                        accountMappings.put(line.getId(), asset.getAssetAccount().getId());
                        break;
                    default:
                        log.warn("Unrecognized account hint '{}' in disposal template line {}",
                                LogSanitizer.sanitize(line.getAccountHint()), line.getId());
                        break;
                }
            }
        }

        // Create and post transaction
        Transaction transaction = new Transaction();
        transaction.setJournalTemplate(template);
        transaction.setTransactionDate(disposalDate);
        transaction.setAmount(asset.getPurchaseCost());
        transaction.setDescription("Pelepasan Aset: " + asset.getName());
        transaction.setReferenceNumber("DISP-" + asset.getAssetCode());

        Transaction savedTransaction = transactionService.create(transaction, accountMappings);
        Transaction postedTransaction = transactionService.post(savedTransaction.getId(), disposedBy, context);

        asset.setDisposalTransaction(postedTransaction);

        FixedAsset savedAsset = fixedAssetRepository.save(asset);
        log.info("Disposed asset {}: type={}, proceeds={}, gainLoss={}",
                LogSanitizer.sanitize(asset.getAssetCode()), type, safeProceeds, asset.getGainLossOnDisposal());

        return savedAsset;
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
