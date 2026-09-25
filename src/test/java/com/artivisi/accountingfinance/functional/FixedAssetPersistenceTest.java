package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.AssetCategory;
import com.artivisi.accountingfinance.entity.AssetStatus;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.DepreciationEntry;
import com.artivisi.accountingfinance.entity.DepreciationMethod;
import com.artivisi.accountingfinance.entity.FixedAsset;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.DepreciationEntryRepository;
import com.artivisi.accountingfinance.repository.FixedAssetRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.service.AssetCategoryService;
import com.artivisi.accountingfinance.service.FixedAssetService;
import com.artivisi.accountingfinance.service.TransactionService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FixedAssetService against the IT-service seed: category defaults on create, the acquisition DRAFT journal,
 * declining-balance amounts down to the residual, rejected creates leaving nothing behind, and the list-page
 * totals. Assets use 2052 dates; totals are asserted as deltas because the database is shared.
 */
@DisplayName("Fixed Asset - Persistence Tests")
@Import(ServiceTestDataInitializer.class)
class FixedAssetPersistenceTest extends PlaywrightTestBase {

    private static final int YEAR = 2052;
    private static final String BANK = "1.1.02";
    private static final String OFFICE_ASSET = "1.2.05";
    private static final String OFFICE_ACCUM = "1.2.06";
    private static final String DEPRECIATION_EXPENSE = "5.1.12";

    @Autowired
    private FixedAssetService fixedAssetService;

    @Autowired
    private AssetCategoryService categoryService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private FixedAssetRepository fixedAssetRepository;

    @Autowired
    private DepreciationEntryRepository entryRepository;

    @Autowired
    private ChartOfAccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("Create fills unset method, life, rate and start date from the category; the acquisition DRAFT posts Dr asset / Cr funding")
    void shouldApplyCategoryDefaultsAndComposeAcquisitionDraft() {
        AssetCategory category = createCategory(DepreciationMethod.DECLINING_BALANCE, 60, new BigDecimal("40"));
        FixedAsset asset = newAsset(category, "15000000", LocalDate.of(YEAR, 3, 20));
        asset.setDepreciationMethod(null);
        asset.setUsefulLifeMonths(null);

        FixedAsset saved = fixedAssetService.create(asset);

        FixedAsset fromDb = asset(saved.getAssetCode());
        assertThat(fromDb.getDepreciationMethod()).isEqualTo(DepreciationMethod.DECLINING_BALANCE);
        assertThat(fromDb.getUsefulLifeMonths()).isEqualTo(60);
        assertThat(fromDb.getDepreciationRate()).isEqualByComparingTo("40");
        assertThat(fromDb.getDepreciationStartDate()).isEqualTo(LocalDate.of(YEAR, 3, 1));
        assertThat(fromDb.getBookValue()).isEqualByComparingTo("15000000");
        assertThat(fromDb.getAccumulatedDepreciation()).isEqualByComparingTo("0");
        assertThat(fromDb.getStatus()).isEqualTo(AssetStatus.ACTIVE);
        assertThat(fromDb.getAssetAccount().getId()).isEqualTo(account(OFFICE_ASSET).getId());
        assertThat(fromDb.getAccumulatedDepreciationAccount().getId()).isEqualTo(account(OFFICE_ACCUM).getId());
        assertThat(fromDb.getDepreciationExpenseAccount().getId()).isEqualTo(account(DEPRECIATION_EXPENSE).getId());

        List<Transaction> drafts = transactionRepository
                .findBySourceDocumentTypeAndSourceDocumentIdOrderByTransactionDateAsc("ASSET", saved.getId());
        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).getStatus()).isEqualTo(TransactionStatus.DRAFT);
        Transaction posted = transactionService.post(drafts.get(0).getId(), "admin");
        assertThat(posted.getStatus()).isEqualTo(TransactionStatus.POSTED);
        assertThat(posted.getTransactionDate()).isEqualTo(LocalDate.of(YEAR, 3, 20));
        assertThat(lines(posted.getId())).containsExactlyInAnyOrder(
                OFFICE_ASSET + " D15000000.00 C0.00", BANK + " D0.00 C15000000.00");

        FixedAsset overridden = newAsset(category, "8000000", LocalDate.of(YEAR, 4, 5));
        overridden.setDepreciationMethod(DepreciationMethod.STRAIGHT_LINE);
        overridden.setUsefulLifeMonths(24);
        overridden.setDepreciationStartDate(LocalDate.of(YEAR, 5, 1));
        FixedAsset savedOverride = fixedAssetService.create(overridden);
        FixedAsset overrideFromDb = asset(savedOverride.getAssetCode());
        assertThat(overrideFromDb.getDepreciationMethod()).isEqualTo(DepreciationMethod.STRAIGHT_LINE);
        assertThat(overrideFromDb.getUsefulLifeMonths()).isEqualTo(24);
        assertThat(overrideFromDb.getDepreciationStartDate()).isEqualTo(LocalDate.of(YEAR, 5, 1));
        assertThat(fixedAssetService.calculateMonthlyDepreciation(overrideFromDb)).isEqualByComparingTo("333333.33");
    }

    @Test
    @DisplayName("Declining balance: each month is book value x rate / 12 to the cent and the last period depreciates down to the residual")
    void shouldDepreciateDecliningBalanceDownToResidual() {
        AssetCategory category = createCategory(DepreciationMethod.DECLINING_BALANCE, 48, new BigDecimal("50"));
        FixedAsset asset = newAsset(category, "12000000", LocalDate.of(YEAR, 1, 10));
        asset.setDepreciationMethod(DepreciationMethod.DECLINING_BALANCE);
        asset.setUsefulLifeMonths(2);
        asset.setResidualValue(new BigDecimal("1000000"));
        FixedAsset saved = fixedAssetService.create(asset);
        assertThat(fixedAssetService.calculateMonthlyDepreciation(saved)).isEqualByComparingTo("500000.00");

        fixedAssetService.generateDepreciationEntries(YearMonth.of(YEAR, 1));
        DepreciationEntry first = entry(saved, 1);
        assertThat(first.getDepreciationAmount()).isEqualByComparingTo("500000.00");
        assertThat(first.getAccumulatedDepreciation()).isEqualByComparingTo("500000.00");
        assertThat(first.getBookValue()).isEqualByComparingTo("11500000.00");
        fixedAssetService.postDepreciationEntry(first.getId(), "admin");
        assertThat(postedLines(first)).containsExactlyInAnyOrder(
                DEPRECIATION_EXPENSE + " D500000.00 C0.00", OFFICE_ACCUM + " D0.00 C500000.00");
        assertThat(fixedAssetService.calculateMonthlyDepreciation(asset(saved.getAssetCode())))
                .isEqualByComparingTo("479166.67");

        fixedAssetService.generateDepreciationEntries(YearMonth.of(YEAR, 2));
        DepreciationEntry last = entry(saved, 2);
        assertThat(last.getDepreciationAmount()).isEqualByComparingTo("10500000.00");
        assertThat(last.getBookValue()).isEqualByComparingTo("1000000.00");
        fixedAssetService.postDepreciationEntry(last.getId(), "admin");
        assertThat(postedLines(last)).containsExactlyInAnyOrder(
                DEPRECIATION_EXPENSE + " D10500000.00 C0.00", OFFICE_ACCUM + " D0.00 C10500000.00");

        FixedAsset done = asset(saved.getAssetCode());
        assertThat(done.getStatus()).isEqualTo(AssetStatus.FULLY_DEPRECIATED);
        assertThat(done.getAccumulatedDepreciation()).isEqualByComparingTo("11000000");
        assertThat(done.getBookValue()).isEqualByComparingTo("1000000");
        assertThat(fixedAssetService.calculateMonthlyDepreciation(done)).isEqualByComparingTo("0");

        fixedAssetService.generateDepreciationEntries(YearMonth.of(YEAR, 3));
        assertThat(entryRepository.findByAssetIdAndPeriodEnd(saved.getId(), LocalDate.of(YEAR, 3, 31))).isEmpty();
    }

    @Test
    @DisplayName("Duplicate code, residual not below cost, declining balance without rate and missing funding account are rejected and leave nothing behind")
    void shouldRejectInvalidAssetsWithoutPersisting() {
        AssetCategory straightLine = createCategory(DepreciationMethod.STRAIGHT_LINE, 48, null);
        FixedAsset existing = fixedAssetService.create(newAsset(straightLine, "5000000", LocalDate.of(YEAR, 1, 10)));

        FixedAsset duplicate = newAsset(straightLine, "7000000", LocalDate.of(YEAR, 1, 10));
        duplicate.setAssetCode(existing.getAssetCode());
        duplicate.setName("Duplikat");
        assertThatThrownBy(() -> fixedAssetService.create(duplicate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Kode aset " + existing.getAssetCode() + " sudah digunakan");
        assertThat(asset(existing.getAssetCode()).getName()).isEqualTo(existing.getName());
        assertThat(asset(existing.getAssetCode()).getPurchaseCost()).isEqualByComparingTo("5000000");

        FixedAsset residual = newAsset(straightLine, "5000000", LocalDate.of(YEAR, 1, 10));
        residual.setResidualValue(new BigDecimal("5000000"));
        assertThatThrownBy(() -> fixedAssetService.create(residual))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Nilai residu harus lebih kecil dari nilai perolehan");
        assertThat(fixedAssetRepository.existsByAssetCode(residual.getAssetCode())).isFalse();

        FixedAsset noRate = newAsset(straightLine, "5000000", LocalDate.of(YEAR, 1, 10));
        noRate.setDepreciationMethod(DepreciationMethod.DECLINING_BALANCE);
        assertThatThrownBy(() -> fixedAssetService.create(noRate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tarif penyusutan wajib diisi untuk metode saldo menurun");
        assertThat(fixedAssetRepository.existsByAssetCode(noRate.getAssetCode())).isFalse();

        FixedAsset noFunding = newAsset(straightLine, "5000000", LocalDate.of(YEAR, 1, 10));
        noFunding.setFundingAccount(null);
        assertThatThrownBy(() -> fixedAssetService.create(noFunding))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Akun pendanaan aset belum dipilih (bank/hutang yang dipakai untuk pembelian)");
        assertThat(fixedAssetRepository.existsByAssetCode(noFunding.getAssetCode())).isFalse();
    }

    @Test
    @DisplayName("List totals and the pending counter move by exactly the new asset's cost and its posted depreciation; generation is idempotent")
    void shouldMoveTotalsByCostAndPostedDepreciation() {
        AssetCategory category = createCategory(DepreciationMethod.STRAIGHT_LINE, 48, null);
        BigDecimal costBefore = fixedAssetService.getTotalPurchaseCost();
        BigDecimal bookBefore = fixedAssetService.getTotalBookValue();
        BigDecimal accumBefore = fixedAssetService.getTotalAccumulatedDepreciation();

        FixedAsset asset = newAsset(category, "6000000", LocalDate.of(YEAR, 6, 10));
        asset.setUsefulLifeMonths(12);
        FixedAsset saved = fixedAssetService.create(asset);
        assertThat(fixedAssetService.getTotalPurchaseCost().subtract(costBefore)).isEqualByComparingTo("6000000");
        assertThat(fixedAssetService.getTotalBookValue().subtract(bookBefore)).isEqualByComparingTo("6000000");
        assertThat(fixedAssetService.getTotalAccumulatedDepreciation()).isEqualByComparingTo(accumBefore);

        YearMonth june = YearMonth.of(YEAR, 6);
        long pendingBefore = fixedAssetService.countPendingDepreciationEntries();
        int juneBefore = entryRepository.findByPeriodEndAndOptionalStatus(june.atEndOfMonth(), null).size();
        fixedAssetService.generateDepreciationEntries(june);
        int generated = entryRepository.findByPeriodEndAndOptionalStatus(june.atEndOfMonth(), null).size() - juneBefore;
        assertThat(generated).isPositive();
        assertThat(fixedAssetService.countPendingDepreciationEntries() - pendingBefore).isEqualTo(generated);

        fixedAssetService.generateDepreciationEntries(june);
        assertThat(entryRepository.findByPeriodEndAndOptionalStatus(june.atEndOfMonth(), null)).hasSize(juneBefore + generated);
        assertThat(entryRepository.findByFixedAssetIdOrderByPeriodNumberAsc(saved.getId())).hasSize(1);

        long pendingAfterGenerate = fixedAssetService.countPendingDepreciationEntries();
        fixedAssetService.postDepreciationEntry(entry(saved, 1).getId(), "admin");
        assertThat(fixedAssetService.countPendingDepreciationEntries()).isEqualTo(pendingAfterGenerate - 1);
        assertThat(fixedAssetService.getTotalAccumulatedDepreciation().subtract(accumBefore)).isEqualByComparingTo("500000");
        assertThat(fixedAssetService.getTotalBookValue().subtract(bookBefore)).isEqualByComparingTo("5500000");
        assertThat(fixedAssetService.getTotalPurchaseCost().subtract(costBefore)).isEqualByComparingTo("6000000");
    }

    // ==================== helpers ====================

    private AssetCategory createCategory(DepreciationMethod method, int months, BigDecimal rate) {
        String code = "PC" + uid();
        AssetCategory category = new AssetCategory();
        category.setCode(code);
        category.setName("Kategori " + code);
        category.setDepreciationMethod(method);
        category.setUsefulLifeMonths(months);
        category.setDepreciationRate(rate);
        category.setAssetAccount(account(OFFICE_ASSET));
        category.setAccumulatedDepreciationAccount(account(OFFICE_ACCUM));
        category.setDepreciationExpenseAccount(account(DEPRECIATION_EXPENSE));
        return categoryService.create(category);
    }

    private FixedAsset newAsset(AssetCategory category, String cost, LocalDate purchaseDate) {
        String code = "PS-" + uid();
        FixedAsset asset = new FixedAsset();
        asset.setAssetCode(code);
        asset.setName("Aset " + code);
        asset.setCategory(category);
        asset.setPurchaseDate(purchaseDate);
        asset.setPurchaseCost(new BigDecimal(cost));
        asset.setFundingAccount(account(BANK));
        return asset;
    }

    private List<String> postedLines(DepreciationEntry entry) {
        UUID transactionId = new TransactionTemplate(transactionManager).execute(status ->
                entryRepository.findById(entry.getId()).orElseThrow().getTransaction().getId());
        return lines(transactionId);
    }

    private List<String> lines(UUID transactionId) {
        return journalEntryRepository.findByTransactionIdWithAccount(transactionId).stream()
                .map(l -> l.getAccount().getAccountCode()
                        + " D" + l.getDebitAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString()
                        + " C" + l.getCreditAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString())
                .toList();
    }

    private DepreciationEntry entry(FixedAsset asset, int periodNumber) {
        return entryRepository.findByFixedAssetIdOrderByPeriodNumberAsc(asset.getId()).stream()
                .filter(e -> e.getPeriodNumber() == periodNumber)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Depreciation entry " + periodNumber + " missing for " + asset.getAssetCode()));
    }

    private FixedAsset asset(String code) {
        return fixedAssetRepository.findByAssetCode(code)
                .orElseThrow(() -> new IllegalStateException("Fixed asset missing: " + code));
    }

    private ChartOfAccount account(String code) {
        return accountRepository.findByAccountCode(code)
                .orElseThrow(() -> new IllegalStateException("Seeded account missing: " + code));
    }

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
