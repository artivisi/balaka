package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.AssetDisposal;
import com.artivisi.accountingfinance.entity.AssetDisposalStatus;
import com.artivisi.accountingfinance.entity.AssetMovement;
import com.artivisi.accountingfinance.entity.AssetStatus;
import com.artivisi.accountingfinance.entity.DepreciationEntry;
import com.artivisi.accountingfinance.entity.FixedAsset;
import com.artivisi.accountingfinance.repository.AssetDisposalRepository;
import com.artivisi.accountingfinance.repository.AssetMovementRepository;
import com.artivisi.accountingfinance.repository.DepreciationEntryRepository;
import com.artivisi.accountingfinance.repository.FixedAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/** Read API over asset movement and disposals, for #45 and #51. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssetRegisterQueryService {

    private final AssetDisposalRepository disposalRepository;
    private final AssetMovementRepository movementRepository;
    private final FixedAssetRepository fixedAssetRepository;
    private final DepreciationEntryRepository depreciationEntryRepository;

    /**
     * Yearly roll-forward of one category. Disposals count from the posted disposal date. Accumulated depreciation
     * sums POSTED entries only: an amount set on the asset without entries is not in this report.
     */
    public record CostMovementRow(String categoryCode, String categoryName,
                                  BigDecimal openingCost, BigDecimal additions, BigDecimal disposals, BigDecimal closingCost,
                                  BigDecimal openingAccumulated, BigDecimal depreciation, BigDecimal disposedAccumulated,
                                  BigDecimal closingAccumulated) {

        private static CostMovementRow empty(String code, String name) {
            return new CostMovementRow(code, name, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        private CostMovementRow plus(CostMovementRow o) {
            return new CostMovementRow(categoryCode, categoryName,
                    openingCost.add(o.openingCost), additions.add(o.additions), disposals.add(o.disposals), closingCost.add(o.closingCost),
                    openingAccumulated.add(o.openingAccumulated), depreciation.add(o.depreciation),
                    disposedAccumulated.add(o.disposedAccumulated), closingAccumulated.add(o.closingAccumulated));
        }
    }

    public record CostMovement(int year, List<CostMovementRow> rows, CostMovementRow total) {
    }

    /** Disposal records dated in the range. A null status returns all statuses. */
    public List<AssetDisposal> findDisposals(LocalDate from, LocalDate to, AssetDisposalStatus status) {
        return disposalRepository.findByDateRange(from, to, status);
    }

    public List<AssetDisposal> findDisposalsOfAsset(UUID assetId) {
        return disposalRepository.findByAssetId(assetId);
    }

    /** Custodian, location and condition changes dated in the range. */
    public List<AssetMovement> findMovements(LocalDate from, LocalDate to) {
        return movementRepository.findByDateRange(from, to);
    }

    public List<AssetMovement> findMovementsOfAsset(UUID assetId) {
        return movementRepository.findByAssetId(assetId);
    }

    public CostMovement costMovement(int year) {
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);
        Map<UUID, List<DepreciationEntry>> posted = depreciationEntryRepository.findAllPostedWithAsset().stream()
                .collect(Collectors.groupingBy(e -> e.getFixedAsset().getId()));

        Map<String, CostMovementRow> byCategory = new TreeMap<>();
        for (FixedAsset asset : fixedAssetRepository.findAllWithCategory()) {
            LocalDate disposedOn = asset.getStatus() == AssetStatus.DISPOSED ? asset.getDisposalDate() : null;
            if (asset.getPurchaseDate().isAfter(end) || (disposedOn != null && disposedOn.isBefore(start))) {
                continue;
            }
            CostMovementRow row = rowOf(asset, disposedOn, posted.getOrDefault(asset.getId(), List.of()), start, end);
            byCategory.merge(row.categoryCode(), row, CostMovementRow::plus);
        }
        CostMovementRow total = byCategory.values().stream()
                .reduce(CostMovementRow.empty(null, null), CostMovementRow::plus);
        return new CostMovement(year, new ArrayList<>(byCategory.values()), total);
    }

    private CostMovementRow rowOf(FixedAsset asset, LocalDate disposedOn, List<DepreciationEntry> entries,
                                  LocalDate start, LocalDate end) {
        BigDecimal cost = asset.getPurchaseCost();
        boolean bought = !asset.getPurchaseDate().isBefore(start);
        boolean disposed = disposedOn != null && !disposedOn.isAfter(end);

        BigDecimal opening = bought ? BigDecimal.ZERO : cost;
        BigDecimal additions = bought ? cost : BigDecimal.ZERO;
        BigDecimal disposals = disposed ? cost : BigDecimal.ZERO;

        BigDecimal openingAccumulated = sum(entries, null, start.minusDays(1));
        BigDecimal depreciation = sum(entries, start, end);
        BigDecimal disposedAccumulated = disposed ? openingAccumulated.add(depreciation) : BigDecimal.ZERO;

        return new CostMovementRow(asset.getCategory().getCode(), asset.getCategory().getName(),
                opening, additions, disposals, opening.add(additions).subtract(disposals),
                openingAccumulated, depreciation, disposedAccumulated,
                openingAccumulated.add(depreciation).subtract(disposedAccumulated));
    }

    private BigDecimal sum(List<DepreciationEntry> entries, LocalDate from, LocalDate to) {
        return entries.stream()
                .filter(e -> (from == null || !e.getPeriodEnd().isBefore(from)) && !e.getPeriodEnd().isAfter(to))
                .map(DepreciationEntry::getDepreciationAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
