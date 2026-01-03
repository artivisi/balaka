package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.AssetCategory;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.repository.AssetCategoryRepository;
import com.artivisi.accountingfinance.security.LogSanitizer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.FixedAssetRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssetCategoryService {

    private static final Logger log = LoggerFactory.getLogger(AssetCategoryService.class);

    private final AssetCategoryRepository assetCategoryRepository;
    private final ChartOfAccountRepository chartOfAccountRepository;
    private final FixedAssetRepository fixedAssetRepository;

    public List<AssetCategory> findAll() {
        return assetCategoryRepository.findAll();
    }

    public List<AssetCategory> findAllActive() {
        return assetCategoryRepository.findAllActive();
    }

    public Page<AssetCategory> findByFilters(String search, Boolean active, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return assetCategoryRepository.findBySearch(search.trim(), active, pageable);
        }
        return assetCategoryRepository.findByFilters(active, pageable);
    }

    public AssetCategory findById(UUID id) {
        return assetCategoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Kategori aset tidak ditemukan"));
    }

    public AssetCategory findByCode(String code) {
        return assetCategoryRepository.findByCode(code)
                .orElseThrow(() -> new EntityNotFoundException("Kategori aset dengan kode " + code + " tidak ditemukan"));
    }

    @Transactional
    public AssetCategory create(AssetCategory category) {
        validateCategory(category, null);
        loadAccounts(category);
        AssetCategory saved = assetCategoryRepository.save(category);
        log.info("Created asset category: {}", LogSanitizer.sanitize(saved.getCode()));
        return saved;
    }

    @Transactional
    public AssetCategory update(UUID id, AssetCategory categoryData) {
        AssetCategory existing = findById(id);
        validateCategory(categoryData, id);

        existing.setCode(categoryData.getCode());
        existing.setName(categoryData.getName());
        existing.setDescription(categoryData.getDescription());
        existing.setDepreciationMethod(categoryData.getDepreciationMethod());
        existing.setUsefulLifeMonths(categoryData.getUsefulLifeMonths());
        existing.setDepreciationRate(categoryData.getDepreciationRate());

        // Update accounts
        existing.setAssetAccount(loadAccount(categoryData.getAssetAccount().getId()));
        existing.setAccumulatedDepreciationAccount(loadAccount(categoryData.getAccumulatedDepreciationAccount().getId()));
        existing.setDepreciationExpenseAccount(loadAccount(categoryData.getDepreciationExpenseAccount().getId()));

        AssetCategory saved = assetCategoryRepository.save(existing);
        log.info("Updated asset category: {}", LogSanitizer.sanitize(saved.getCode()));
        return saved;
    }

    @Transactional
    public void activate(UUID id) {
        AssetCategory category = findById(id);
        category.setActive(true);
        assetCategoryRepository.save(category);
        log.info("Activated asset category: {}", category.getCode());
    }

    @Transactional
    public void deactivate(UUID id) {
        AssetCategory category = findById(id);

        // Check if category is in use by active assets
        long assetCount = fixedAssetRepository.findByCategory(category).size();
        if (assetCount > 0) {
            throw new IllegalStateException("Kategori tidak dapat dinonaktifkan karena masih digunakan oleh " + assetCount + " aset");
        }

        category.setActive(false);
        assetCategoryRepository.save(category);
        log.info("Deactivated asset category: {}", category.getCode());
    }

    @Transactional
    public void delete(UUID id) {
        AssetCategory category = findById(id);

        // Check if category is in use
        long assetCount = fixedAssetRepository.findByCategory(category).size();
        if (assetCount > 0) {
            throw new IllegalStateException("Kategori tidak dapat dihapus karena masih digunakan oleh " + assetCount + " aset");
        }

        assetCategoryRepository.delete(category);
        log.info("Deleted asset category: {}", category.getCode());
    }

    public boolean existsByCode(String code) {
        return assetCategoryRepository.existsByCode(code);
    }

    private void validateCategory(AssetCategory category, UUID excludeId) {
        // Check for duplicate code
        if (excludeId == null) {
            if (assetCategoryRepository.existsByCode(category.getCode())) {
                throw new IllegalArgumentException("Kode kategori " + category.getCode() + " sudah digunakan");
            }
        } else {
            assetCategoryRepository.findByCode(category.getCode())
                    .ifPresent(existing -> {
                        if (!existing.getId().equals(excludeId)) {
                            throw new IllegalArgumentException("Kode kategori " + category.getCode() + " sudah digunakan");
                        }
                    });
        }
    }

    private void loadAccounts(AssetCategory category) {
        category.setAssetAccount(loadAccount(category.getAssetAccount().getId()));
        category.setAccumulatedDepreciationAccount(loadAccount(category.getAccumulatedDepreciationAccount().getId()));
        category.setDepreciationExpenseAccount(loadAccount(category.getDepreciationExpenseAccount().getId()));
    }

    private ChartOfAccount loadAccount(UUID accountId) {
        return chartOfAccountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Akun tidak ditemukan"));
    }
}
