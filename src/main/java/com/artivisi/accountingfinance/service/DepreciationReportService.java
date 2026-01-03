package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.AssetStatus;
import com.artivisi.accountingfinance.entity.DepreciationMethod;
import com.artivisi.accountingfinance.entity.FixedAsset;
import com.artivisi.accountingfinance.repository.FixedAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Service for generating depreciation reports for tax purposes.
 * Generates Laporan Penyusutan for SPT Tahunan (Lampiran Khusus 1A format).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepreciationReportService {

    private final FixedAssetRepository fixedAssetRepository;

    /**
     * Generate depreciation report for a specific year.
     * This is used as attachment for SPT Tahunan Badan (Lampiran Khusus 1A).
     */
    public DepreciationReport generateReport(int year) {
        LocalDate yearEnd = LocalDate.of(year, 12, 31);

        List<FixedAsset> assets = fixedAssetRepository.findAll()
                .stream()
                .filter(a -> a.getStatus() != AssetStatus.DISPOSED ||
                            (a.getDisposalDate() != null && a.getDisposalDate().getYear() == year))
                .filter(a -> !a.getPurchaseDate().isAfter(yearEnd))
                .toList();

        List<DepreciationReportItem> items = assets.stream()
                .map(asset -> createReportItem(asset, year))
                .toList();

        BigDecimal totalPurchaseCost = items.stream()
                .map(DepreciationReportItem::purchaseCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDepreciationThisYear = items.stream()
                .map(DepreciationReportItem::depreciationThisYear)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalAccumulatedDepreciation = items.stream()
                .map(DepreciationReportItem::accumulatedDepreciation)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalBookValue = items.stream()
                .map(DepreciationReportItem::bookValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new DepreciationReport(
                year,
                items,
                totalPurchaseCost,
                totalDepreciationThisYear,
                totalAccumulatedDepreciation,
                totalBookValue
        );
    }

    private DepreciationReportItem createReportItem(FixedAsset asset, int year) {
        // Calculate depreciation for this year
        BigDecimal depreciationThisYear = calculateYearlyDepreciation(asset, year);

        // Get method name in Indonesian
        String methodName = asset.getDepreciationMethod() == DepreciationMethod.STRAIGHT_LINE
                ? "Garis Lurus"
                : "Saldo Menurun";

        return new DepreciationReportItem(
                asset.getAssetCode(),
                asset.getName(),
                asset.getCategory().getName(),
                asset.getPurchaseDate(),
                asset.getPurchaseCost(),
                asset.getUsefulLifeMonths() / 12,
                methodName,
                depreciationThisYear,
                asset.getAccumulatedDepreciation(),
                asset.getBookValue(),
                asset.getStatus().name()
        );
    }

    /**
     * Calculate depreciation amount for a specific year.
     */
    private BigDecimal calculateYearlyDepreciation(FixedAsset asset, int year) {
        // Count depreciation entries for this year
        return asset.getDepreciationEntries().stream()
                .filter(entry -> entry.getPeriodEnd().getYear() == year)
                .map(entry -> entry.getDepreciationAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Record classes for report data
    public record DepreciationReport(
            int year,
            List<DepreciationReportItem> items,
            BigDecimal totalPurchaseCost,
            BigDecimal totalDepreciationThisYear,
            BigDecimal totalAccumulatedDepreciation,
            BigDecimal totalBookValue
    ) {}

    public record DepreciationReportItem(
            String assetCode,
            String assetName,
            String categoryName,
            LocalDate purchaseDate,
            BigDecimal purchaseCost,
            int usefulLifeYears,
            String depreciationMethod,
            BigDecimal depreciationThisYear,
            BigDecimal accumulatedDepreciation,
            BigDecimal bookValue,
            String status
    ) {}
}
