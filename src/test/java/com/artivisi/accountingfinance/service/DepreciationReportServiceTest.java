package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.entity.AssetCategory;
import com.artivisi.accountingfinance.entity.DepreciationEntry;
import com.artivisi.accountingfinance.entity.DepreciationMethod;
import com.artivisi.accountingfinance.entity.DisposalType;
import com.artivisi.accountingfinance.entity.FixedAsset;
import com.artivisi.accountingfinance.repository.AssetCategoryRepository;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.DepreciationEntryRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.service.DepreciationReportService.DepreciationReport;
import com.artivisi.accountingfinance.service.DepreciationReportService.DepreciationReportItem;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DepreciationReportService (SPT Lampiran penyusutan) against assets each test creates with 2053 dates on the
 * V800 categories. Every test rolls back.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "admin")
@DisplayName("DepreciationReportService Integration Tests")
class DepreciationReportServiceTest {

    private static final int YEAR = 2053;
    private static final UUID KOMPUTER = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID MESIN = UUID.fromString("a0000000-0000-0000-0000-000000000004");

    @Autowired
    private DepreciationReportService reportService;

    @Autowired
    private FixedAssetService fixedAssetService;

    @Autowired
    private AssetCategoryRepository categoryRepository;

    @Autowired
    private DepreciationEntryRepository entryRepository;

    @Autowired
    private ChartOfAccountRepository accountRepository;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Depreciation this year counts only the posted entries of that year, not skipped or pending ones")
    void shouldSumOnlyPostedEntriesOfTheYear() {
        FixedAsset asset = createAsset(KOMPUTER, "12000000", 24, LocalDate.of(YEAR, 3, 15));
        for (int month = 3; month <= 5; month++) {
            fixedAssetService.postDepreciationEntry(generate(asset, month).getId(), "admin");
        }
        fixedAssetService.skipDepreciationEntry(generate(asset, 6).getId());
        generate(asset, 7);

        DepreciationReportItem item = item(report(YEAR), asset);

        assertThat(item.assetName()).isEqualTo(asset.getName());
        assertThat(item.categoryName()).isEqualTo("Peralatan Komputer");
        assertThat(item.purchaseDate()).isEqualTo(LocalDate.of(YEAR, 3, 15));
        assertThat(item.purchaseCost()).isEqualByComparingTo("12000000");
        assertThat(item.usefulLifeYears()).isEqualTo(2);
        assertThat(item.depreciationMethod()).isEqualTo("Garis Lurus");
        assertThat(item.depreciationThisYear()).isEqualByComparingTo("1500000");
        assertThat(item.accumulatedDepreciation()).isEqualByComparingTo("1500000");
        assertThat(item.bookValue()).isEqualByComparingTo("10500000");
        assertThat(item.status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("Without entries the straight-line estimate covers exactly the months of the useful life in each year")
    void shouldEstimateMonthsWithinUsefulLife() {
        FixedAsset asset = createAsset(KOMPUTER, "12000000", 24, LocalDate.of(YEAR, 3, 15));

        BigDecimal first = item(report(YEAR), asset).depreciationThisYear();
        BigDecimal second = item(report(YEAR + 1), asset).depreciationThisYear();
        BigDecimal third = item(report(YEAR + 2), asset).depreciationThisYear();
        BigDecimal fourth = item(report(YEAR + 3), asset).depreciationThisYear();

        assertThat(first).isEqualByComparingTo("5000000");
        assertThat(second).isEqualByComparingTo("6000000");
        assertThat(third).isEqualByComparingTo("1000000");
        assertThat(fourth).isEqualByComparingTo("0");
        assertThat(first.add(second).add(third)).isEqualByComparingTo(asset.getPurchaseCost());
    }

    @Test
    @DisplayName("Declining balance asset is reported as Saldo Menurun with the category rate applied exactly")
    void shouldReportDecliningBalanceWithCategoryRate() {
        FixedAsset asset = newAsset(MESIN, "9600000", LocalDate.of(YEAR, 1, 10));
        asset.setDepreciationMethod(DepreciationMethod.DECLINING_BALANCE);
        asset.setUsefulLifeMonths(null);
        FixedAsset saved = fixedAssetService.create(asset);
        assertThat(saved.getDepreciationRate()).isEqualByComparingTo("25");
        assertThat(saved.getUsefulLifeMonths()).isEqualTo(96);

        DepreciationReportItem item = item(report(YEAR), saved);

        assertThat(item.categoryName()).isEqualTo("Mesin");
        assertThat(item.depreciationMethod()).isEqualTo("Saldo Menurun");
        assertThat(item.usefulLifeYears()).isEqualTo(8);
        assertThat(item.depreciationThisYear()).isEqualByComparingTo("2400000");
        assertThat(item.bookValue()).isEqualByComparingTo("9600000");
    }

    @Test
    @DisplayName("Assets appear from their purchase year; a disposed asset only in its disposal year, after a disposal journal with the gain or loss")
    void shouldScopeItemsToPurchaseAndDisposalYears() {
        FixedAsset sold = createAsset(KOMPUTER, "12000000", 24, LocalDate.of(YEAR, 1, 10));
        fixedAssetService.postDepreciationEntry(generate(sold, 1).getId(), "admin");
        FixedAsset soldDisposed = fixedAssetService.disposeAsset(sold.getId(), DisposalType.SOLD,
                new BigDecimal("13000000"), "Dijual", LocalDate.of(YEAR, 2, 15), "admin");
        assertThat(soldDisposed.getGainLossOnDisposal()).isEqualByComparingTo("1500000");
        assertThat(lines(soldDisposed.getDisposalTransaction().getId())).containsExactlyInAnyOrder(
                "1.1.02 D13000000.00 C0.00", "1.2.02 D500000.00 C0.00",
                "1.2.01 D0.00 C12000000.00", "4.2.02 D0.00 C1500000.00");

        FixedAsset scrapped = createAsset(KOMPUTER, "6000000", 12, LocalDate.of(YEAR, 1, 20));
        FixedAsset scrappedDisposed = fixedAssetService.disposeAsset(scrapped.getId(), DisposalType.WRITTEN_OFF,
                null, "Rusak", LocalDate.of(YEAR, 3, 1), "admin");
        assertThat(scrappedDisposed.getGainLossOnDisposal()).isEqualByComparingTo("-6000000");
        assertThat(lines(scrappedDisposed.getDisposalTransaction().getId())).containsExactlyInAnyOrder(
                "5.2.02 D6000000.00 C0.00", "1.2.01 D0.00 C6000000.00");

        FixedAsset later = createAsset(KOMPUTER, "3000000", 12, LocalDate.of(YEAR, 6, 1));

        DepreciationReport disposalYear = report(YEAR);
        DepreciationReportItem soldItem = item(disposalYear, sold);
        assertThat(soldItem.status()).isEqualTo("DISPOSED");
        assertThat(soldItem.depreciationThisYear()).isEqualByComparingTo("500000");
        assertThat(soldItem.accumulatedDepreciation()).isEqualByComparingTo("500000");
        assertThat(soldItem.bookValue()).isEqualByComparingTo("11500000");
        DepreciationReportItem scrappedItem = item(disposalYear, scrapped);
        assertThat(scrappedItem.status()).isEqualTo("DISPOSED");
        assertThat(scrappedItem.depreciationThisYear()).isEqualByComparingTo("0");
        assertThat(codes(disposalYear)).contains(later.getAssetCode());

        assertThat(codes(report(YEAR - 1))).doesNotContain(sold.getAssetCode(), scrapped.getAssetCode(), later.getAssetCode());
        List<String> nextYear = codes(report(YEAR + 1));
        assertThat(nextYear).contains(later.getAssetCode());
        assertThat(nextYear).doesNotContain(sold.getAssetCode(), scrapped.getAssetCode());
    }

    @Test
    @DisplayName("Report totals are the sums of the item columns")
    void shouldTotalTheItemColumns() {
        FixedAsset posted = createAsset(KOMPUTER, "12000000", 24, LocalDate.of(YEAR, 1, 10));
        fixedAssetService.postDepreciationEntry(generate(posted, 1).getId(), "admin");
        createAsset(MESIN, "4800000", 96, LocalDate.of(YEAR, 4, 1));

        DepreciationReport report = report(YEAR);

        assertThat(report.year()).isEqualTo(YEAR);
        assertThat(codes(report)).contains(posted.getAssetCode());
        assertThat(report.totalPurchaseCost()).isEqualByComparingTo(sum(report, DepreciationReportItem::purchaseCost));
        assertThat(report.totalDepreciationThisYear()).isEqualByComparingTo(sum(report, DepreciationReportItem::depreciationThisYear));
        assertThat(report.totalAccumulatedDepreciation()).isEqualByComparingTo(sum(report, DepreciationReportItem::accumulatedDepreciation));
        assertThat(report.totalBookValue()).isEqualByComparingTo(sum(report, DepreciationReportItem::bookValue));
    }

    // ==================== helpers ====================

    private DepreciationReport report(int year) {
        entityManager.flush();
        entityManager.clear();
        return reportService.generateReport(year);
    }

    private DepreciationEntry generate(FixedAsset asset, int month) {
        fixedAssetService.generateDepreciationEntries(YearMonth.of(YEAR, month));
        return entryRepository.findByAssetIdAndPeriodEnd(asset.getId(), YearMonth.of(YEAR, month).atEndOfMonth())
                .orElseThrow(() -> new IllegalStateException(
                        "Depreciation entry " + YEAR + "-" + month + " missing for " + asset.getAssetCode()));
    }

    private FixedAsset createAsset(UUID categoryId, String cost, int months, LocalDate purchaseDate) {
        FixedAsset asset = newAsset(categoryId, cost, purchaseDate);
        asset.setDepreciationMethod(DepreciationMethod.STRAIGHT_LINE);
        asset.setUsefulLifeMonths(months);
        return fixedAssetService.create(asset);
    }

    private FixedAsset newAsset(UUID categoryId, String cost, LocalDate purchaseDate) {
        AssetCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalStateException("V800 asset category missing: " + categoryId));
        String code = "RPT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        FixedAsset asset = new FixedAsset();
        asset.setAssetCode(code);
        asset.setName("Aset Laporan " + code);
        asset.setCategory(category);
        asset.setPurchaseDate(purchaseDate);
        asset.setPurchaseCost(new BigDecimal(cost));
        asset.setResidualValue(BigDecimal.ZERO);
        asset.setFundingAccount(accountRepository.findByAccountCode("1.1.02")
                .orElseThrow(() -> new IllegalStateException("V800 account missing: 1.1.02")));
        return asset;
    }

    private DepreciationReportItem item(DepreciationReport report, FixedAsset asset) {
        return report.items().stream()
                .filter(i -> i.assetCode().equals(asset.getAssetCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(asset.getAssetCode() + " missing from " + report.year() + " report"));
    }

    private List<String> codes(DepreciationReport report) {
        return report.items().stream().map(DepreciationReportItem::assetCode).toList();
    }

    private BigDecimal sum(DepreciationReport report, Function<DepreciationReportItem, BigDecimal> column) {
        return report.items().stream().map(column).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<String> lines(UUID transactionId) {
        return journalEntryRepository.findByTransactionIdWithAccount(transactionId).stream()
                .map(l -> l.getAccount().getAccountCode()
                        + " D" + l.getDebitAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString()
                        + " C" + l.getCreditAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString())
                .toList();
    }
}
