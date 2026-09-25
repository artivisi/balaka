package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.InventoryBalance;
import com.artivisi.accountingfinance.entity.InventoryTransaction;
import com.artivisi.accountingfinance.entity.InventoryTransactionType;
import com.artivisi.accountingfinance.entity.Product;
import com.artivisi.accountingfinance.repository.InventoryBalanceRepository;
import com.artivisi.accountingfinance.repository.InventoryTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service for generating inventory reports.
 * Provides stock balance, stock movement, and inventory valuation reports.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryReportService {

    private final InventoryBalanceRepository balanceRepository;
    private final InventoryTransactionRepository transactionRepository;

    /**
     * Generate stock balance report showing current stock levels.
     * Can be filtered by category and search text.
     */
    public StockBalanceReport generateStockBalanceReport(UUID categoryId, String search) {
        List<InventoryBalance> balances;

        if (categoryId != null && search != null && !search.isBlank()) {
            balances = balanceRepository.findByProductCategoryIdAndSearch(categoryId, search);
        } else if (categoryId != null) {
            balances = balanceRepository.findByProductCategoryId(categoryId);
        } else if (search != null && !search.isBlank()) {
            balances = balanceRepository.findBySearch(search);
        } else {
            balances = balanceRepository.findAllWithProduct();
        }

        List<StockBalanceItem> items = balances.stream()
                .filter(b -> b.getProduct().isActive())
                .map(this::createStockBalanceItem)
                .toList();

        BigDecimal totalQuantity = items.stream()
                .map(StockBalanceItem::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalValue = items.stream()
                .map(StockBalanceItem::totalValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new StockBalanceReport(items, totalQuantity, totalValue);
    }

    private StockBalanceItem createStockBalanceItem(InventoryBalance balance) {
        Product product = balance.getProduct();
        String categoryName = product.getCategory() != null ? product.getCategory().getName() : "-";

        return new StockBalanceItem(
                product.getCode(),
                product.getName(),
                categoryName,
                product.getUnit(),
                balance.getQuantity(),
                balance.getAverageCost(),
                balance.getTotalCost(),
                product.getMinimumStock(),
                balance.getQuantity().compareTo(product.getMinimumStock()) < 0
        );
    }

    /**
     * Generate stock movement report for a date range.
     * Shows inbound, outbound quantities and values.
     */
    public StockMovementReport generateStockMovementReport(
            LocalDate startDate, LocalDate endDate,
            UUID categoryId, UUID productId) {

        List<InventoryTransaction> transactions;

        if (productId != null) {
            transactions = transactionRepository.findByProductIdAndDateRange(productId, startDate, endDate);
        } else if (categoryId != null) {
            transactions = transactionRepository.findByCategoryIdAndDateRange(categoryId, startDate, endDate);
        } else {
            transactions = transactionRepository.findByDateRange(startDate, endDate);
        }

        List<StockMovementItem> items = transactions.stream()
                .map(this::createStockMovementItem)
                .toList();

        // Calculate totals
        BigDecimal totalInboundQty = items.stream()
                .filter(item -> isInbound(item.transactionType()))
                .map(StockMovementItem::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalOutboundQty = items.stream()
                .filter(item -> isOutbound(item.transactionType()))
                .map(StockMovementItem::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalInboundValue = items.stream()
                .filter(item -> isInbound(item.transactionType()))
                .map(StockMovementItem::totalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalOutboundValue = items.stream()
                .filter(item -> isOutbound(item.transactionType()))
                .map(StockMovementItem::totalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new StockMovementReport(
                startDate, endDate, items,
                totalInboundQty, totalOutboundQty,
                totalInboundValue, totalOutboundValue
        );
    }

    private StockMovementItem createStockMovementItem(InventoryTransaction tx) {
        Product product = tx.getProduct();
        String categoryName = product.getCategory() != null ? product.getCategory().getName() : "-";

        return new StockMovementItem(
                tx.getTransactionDate(),
                product.getCode(),
                product.getName(),
                categoryName,
                tx.getTransactionType(),
                getTransactionTypeLabel(tx.getTransactionType()),
                tx.getQuantity(),
                tx.getUnitCost(),
                tx.getTotalCost(),
                tx.getReferenceNumber(),
                tx.getBalanceAfter()
        );
    }

    private boolean isInbound(InventoryTransactionType type) {
        return type == InventoryTransactionType.PURCHASE ||
               type == InventoryTransactionType.ADJUSTMENT_IN ||
               type == InventoryTransactionType.PRODUCTION_IN ||
               type == InventoryTransactionType.TRANSFER_IN;
    }

    private boolean isOutbound(InventoryTransactionType type) {
        return type == InventoryTransactionType.SALE ||
               type == InventoryTransactionType.ADJUSTMENT_OUT ||
               type == InventoryTransactionType.PRODUCTION_OUT ||
               type == InventoryTransactionType.TRANSFER_OUT;
    }

    private String getTransactionTypeLabel(InventoryTransactionType type) {
        return switch (type) {
            case PURCHASE -> "Pembelian";
            case SALE -> "Penjualan";
            case ADJUSTMENT_IN -> "Penyesuaian Masuk";
            case ADJUSTMENT_OUT -> "Penyesuaian Keluar";
            case PRODUCTION_IN -> "Produksi Masuk";
            case PRODUCTION_OUT -> "Produksi Keluar";
            case TRANSFER_IN -> "Transfer Masuk";
            case TRANSFER_OUT -> "Transfer Keluar";
        };
    }

    /**
     * Generate inventory valuation report.
     * Shows total value by category and costing method.
     */
    public ValuationReport generateValuationReport(UUID categoryId) {
        List<InventoryBalance> balances;

        if (categoryId != null) {
            balances = balanceRepository.findByProductCategoryId(categoryId);
        } else {
            balances = balanceRepository.findAllWithProduct();
        }

        List<ValuationItem> items = balances.stream()
                .filter(b -> b.getProduct().isActive())
                .filter(b -> b.getQuantity().compareTo(BigDecimal.ZERO) > 0)
                .map(this::createValuationItem)
                .toList();

        BigDecimal totalValue = items.stream()
                .map(ValuationItem::totalValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ValuationReport(items, totalValue);
    }

    private ValuationItem createValuationItem(InventoryBalance balance) {
        Product product = balance.getProduct();
        String categoryName = product.getCategory() != null ? product.getCategory().getName() : "-";
        String costingMethod = product.getCostingMethod().name().equals("FIFO") ? "FIFO" : "Rata-rata";

        return new ValuationItem(
                product.getCode(),
                product.getName(),
                categoryName,
                product.getUnit(),
                balance.getQuantity(),
                balance.getAverageCost(),
                balance.getTotalCost(),
                costingMethod
        );
    }

    // Record classes for report data

    public record StockBalanceReport(
            List<StockBalanceItem> items,
            BigDecimal totalQuantity,
            BigDecimal totalValue
    ) {}

    public record StockBalanceItem(
            String productCode,
            String productName,
            String categoryName,
            String unit,
            BigDecimal quantity,
            BigDecimal averageCost,
            BigDecimal totalValue,
            BigDecimal minimumStock,
            boolean belowMinimum
    ) {}

    public record StockMovementReport(
            LocalDate startDate,
            LocalDate endDate,
            List<StockMovementItem> items,
            BigDecimal totalInboundQty,
            BigDecimal totalOutboundQty,
            BigDecimal totalInboundValue,
            BigDecimal totalOutboundValue
    ) {}

    public record StockMovementItem(
            LocalDate transactionDate,
            String productCode,
            String productName,
            String categoryName,
            InventoryTransactionType transactionType,
            String transactionTypeLabel,
            BigDecimal quantity,
            BigDecimal unitCost,
            BigDecimal totalCost,
            String referenceNumber,
            BigDecimal balanceAfter
    ) {}

    public record ValuationReport(
            List<ValuationItem> items,
            BigDecimal totalValue
    ) {}

    public record ValuationItem(
            String productCode,
            String productName,
            String categoryName,
            String unit,
            BigDecimal quantity,
            BigDecimal averageCost,
            BigDecimal totalValue,
            String costingMethod
    ) {}

    /**
     * Generate product profitability report.
     * Shows sales revenue, COGS, and margin per product.
     */
    public ProfitabilityReport generateProfitabilityReport(
            LocalDate startDate, LocalDate endDate,
            UUID categoryId, UUID productId) {

        List<InventoryTransaction> salesTransactions;

        if (productId != null) {
            salesTransactions = transactionRepository.findByProductIdAndTypeAndDateRange(
                    productId, InventoryTransactionType.SALE, startDate, endDate);
        } else if (categoryId != null) {
            salesTransactions = transactionRepository.findByCategoryIdAndTypeAndDateRange(
                    categoryId, InventoryTransactionType.SALE, startDate, endDate);
        } else {
            salesTransactions = transactionRepository.findByTypeAndDateRange(
                    InventoryTransactionType.SALE, startDate, endDate);
        }

        // Group by product and calculate profitability
        var productProfitability = salesTransactions.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        tx -> tx.getProduct().getId(),
                        java.util.stream.Collectors.toList()
                ));

        List<ProfitabilityItem> items = productProfitability.entrySet().stream()
                .map(entry -> createProfitabilityItem(entry.getValue()))
                .sorted((a, b) -> b.margin().compareTo(a.margin()))
                .toList();

        BigDecimal totalRevenue = items.stream()
                .map(ProfitabilityItem::revenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCogs = items.stream()
                .map(ProfitabilityItem::cogs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalMargin = items.stream()
                .map(ProfitabilityItem::margin)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalQuantitySold = items.stream()
                .map(ProfitabilityItem::quantitySold)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ProfitabilityReport(startDate, endDate, items,
                totalRevenue, totalCogs, totalMargin, totalQuantitySold);
    }

    private ProfitabilityItem createProfitabilityItem(List<InventoryTransaction> transactions) {
        if (transactions.isEmpty()) {
            throw new IllegalArgumentException("Cannot create profitability item from empty transactions");
        }

        Product product = transactions.getFirst().getProduct();
        String categoryName = product.getCategory() != null ? product.getCategory().getName() : "-";

        BigDecimal quantitySold = transactions.stream()
                .map(InventoryTransaction::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal cogs = transactions.stream()
                .map(InventoryTransaction::getTotalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal revenue = transactions.stream()
                .map(tx -> tx.getQuantity().multiply(
                        tx.getUnitPrice() != null ? tx.getUnitPrice() : BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal margin = revenue.subtract(cogs);
        BigDecimal marginPercent = revenue.compareTo(BigDecimal.ZERO) > 0
                ? margin.multiply(BigDecimal.valueOf(100)).divide(revenue, 4, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new ProfitabilityItem(
                product.getCode(),
                product.getName(),
                categoryName,
                product.getUnit(),
                quantitySold,
                revenue,
                cogs,
                margin,
                marginPercent,
                transactions.size()
        );
    }

    public record ProfitabilityReport(
            LocalDate startDate,
            LocalDate endDate,
            List<ProfitabilityItem> items,
            BigDecimal totalRevenue,
            BigDecimal totalCogs,
            BigDecimal totalMargin,
            BigDecimal totalQuantitySold
    ) {
        public BigDecimal getTotalMarginPercent() {
            if (totalRevenue.compareTo(BigDecimal.ZERO) > 0) {
                return totalMargin.multiply(BigDecimal.valueOf(100))
                        .divide(totalRevenue, 4, java.math.RoundingMode.HALF_UP);
            }
            return BigDecimal.ZERO;
        }
    }

    public record ProfitabilityItem(
            String productCode,
            String productName,
            String categoryName,
            String unit,
            BigDecimal quantitySold,
            BigDecimal revenue,
            BigDecimal cogs,
            BigDecimal margin,
            BigDecimal marginPercent,
            int transactionCount
    ) {}
}
