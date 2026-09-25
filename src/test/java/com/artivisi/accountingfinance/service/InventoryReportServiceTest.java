package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.entity.CostingMethod;
import com.artivisi.accountingfinance.entity.InventoryTransactionType;
import com.artivisi.accountingfinance.entity.Product;
import com.artivisi.accountingfinance.entity.ProductCategory;
import com.artivisi.accountingfinance.repository.ProductCategoryRepository;
import com.artivisi.accountingfinance.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * InventoryReportService against the V911 fixture (categories CAT-BAHAN/CAT-PRODUK, products PRD-TEST-001..003,
 * January 2024 movements) and against products each test records itself in 2048 (rolled back).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("InventoryReportService Integration Tests")
class InventoryReportServiceTest {

    private static final UUID CATEGORY_BAHAN_BAKU_ID = UUID.fromString("d0911001-0000-0000-0000-000000000001");
    private static final UUID CATEGORY_PRODUK_ID = UUID.fromString("d0911001-0000-0000-0000-000000000002");
    private static final UUID PRODUCT_TEPUNG_ID = UUID.fromString("d0911002-0000-0000-0000-000000000001");
    private static final UUID PRODUCT_KUE_BOLU_ID = UUID.fromString("d0911002-0000-0000-0000-000000000003");
    private static final LocalDate JAN_1_2024 = LocalDate.of(2024, 1, 1);
    private static final LocalDate JAN_31_2024 = LocalDate.of(2024, 1, 31);
    private static final LocalDate DAY = LocalDate.of(2048, 5, 10);

    @Autowired
    private InventoryReportService reportService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductCategoryRepository categoryRepository;

    @Nested
    @DisplayName("Stock Balance Report")
    class StockBalanceTests {

        @Test
        @DisplayName("Should list each product's quantity, average cost, value and minimum stock")
        void shouldListSeededBalances() {
            var report = reportService.generateStockBalanceReport(null, null);

            assertThat(balance(report, "PRD-TEST-001"))
                    .isEqualTo("Tepung Terigu|Bahan Baku|kg|50.0000|10000.0000|500000.00|10.0000|false");
            assertThat(balance(report, "PRD-TEST-002"))
                    .isEqualTo("Gula Pasir|Bahan Baku|kg|25.0000|15000.0000|375000.00|5.0000|false");
            assertThat(balance(report, "PRD-TEST-003"))
                    .isEqualTo("Kue Bolu|Produk Jadi|pcs|20.0000|30000.0000|600000.00|0.0000|false");
        }

        @Test
        @DisplayName("Should limit to a category, keep sold-out products, exclude inactive ones, flag low stock and total the rows")
        void shouldFilterByCategory() {
            Fixture f = fixture();

            var report = reportService.generateStockBalanceReport(f.category.getId(), null);

            assertThat(report.items()).extracting(InventoryReportService.StockBalanceItem::productCode)
                    .containsExactly(f.average.getCode(), f.fifo.getCode(), f.soldOut.getCode());
            assertThat(balance(report, f.average.getCode()))
                    .isEqualTo(f.average.getName() + "|" + f.category.getName() + "|kg|8.0000|11000.0000|88000.00|5.0000|false");
            assertThat(balance(report, f.fifo.getCode()))
                    .isEqualTo(f.fifo.getName() + "|" + f.category.getName() + "|pcs|5.0000|8000.0000|40000.00|10.0000|true");
            assertThat(balance(report, f.soldOut.getCode()))
                    .isEqualTo(f.soldOut.getName() + "|" + f.category.getName() + "|kg|0.0000|0.0000|0.00|0.0000|false");
            assertThat(report.totalQuantity()).isEqualByComparingTo("13");
            assertThat(report.totalValue()).isEqualByComparingTo("128000");
        }

        @Test
        @DisplayName("Should search code and name case-insensitively, alone and within a category")
        void shouldSearch() {
            Fixture f = fixture();

            assertThat(codes(reportService.generateStockBalanceReport(null, "tepung terigu")))
                    .contains("PRD-TEST-001").doesNotContain("PRD-TEST-002", "PRD-TEST-003");
            assertThat(codes(reportService.generateStockBalanceReport(null, "prd-test-00")))
                    .contains("PRD-TEST-001", "PRD-TEST-002", "PRD-TEST-003");
            assertThat(codes(reportService.generateStockBalanceReport(f.category.getId(), f.fifo.getName().toUpperCase())))
                    .containsExactly(f.fifo.getCode());
            assertThat(codes(reportService.generateStockBalanceReport(CATEGORY_BAHAN_BAKU_ID, "gula")))
                    .containsExactly("PRD-TEST-002");
        }

        @Test
        @DisplayName("Should return an empty report with zero totals for an unknown category")
        void shouldReturnEmptyReportForUnknownCategory() {
            var report = reportService.generateStockBalanceReport(UUID.randomUUID(), null);

            assertThat(report.items()).isEmpty();
            assertThat(report.totalQuantity()).isEqualByComparingTo("0");
            assertThat(report.totalValue()).isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("Stock Movement Report")
    class StockMovementTests {

        @Test
        @DisplayName("Should list a product's movements in date order with inbound and outbound totals")
        void shouldListProductMovements() {
            var report = reportService.generateStockMovementReport(JAN_1_2024, JAN_31_2024, null, PRODUCT_TEPUNG_ID);

            assertThat(report.startDate()).isEqualTo(JAN_1_2024);
            assertThat(report.endDate()).isEqualTo(JAN_31_2024);
            assertThat(report.items()).extracting(InventoryReportServiceTest::movement).containsExactly(
                    "2024-01-05|PRD-TEST-001|Tepung Terigu|Bahan Baku|Pembelian|30.0000|10000.0000|300000.00|PO-001|30.0000",
                    "2024-01-15|PRD-TEST-001|Tepung Terigu|Bahan Baku|Pembelian|40.0000|10000.0000|400000.00|PO-002|70.0000",
                    "2024-01-20|PRD-TEST-001|Tepung Terigu|Bahan Baku|Produksi Keluar|20.0000|10000.0000|200000.00|PROD-001|50.0000");
            assertThat(report.totalInboundQty()).isEqualByComparingTo("70");
            assertThat(report.totalOutboundQty()).isEqualByComparingTo("20");
            assertThat(report.totalInboundValue()).isEqualByComparingTo("700000");
            assertThat(report.totalOutboundValue()).isEqualByComparingTo("200000");
        }

        @Test
        @DisplayName("Should limit to a category and to the inclusive date range")
        void shouldFilterByCategoryAndDates() {
            var january = reportService.generateStockMovementReport(JAN_1_2024, JAN_31_2024, CATEGORY_BAHAN_BAKU_ID, null);
            assertThat(january.items()).extracting(InventoryReportService.StockMovementItem::referenceNumber)
                    .containsExactlyInAnyOrder("PO-001", "PO-002", "PROD-001", "PO-003", "PO-004", "PROD-001");
            assertThat(january.totalInboundValue()).isEqualByComparingTo("1225000");
            assertThat(january.totalOutboundValue()).isEqualByComparingTo("350000");

            var middle = reportService.generateStockMovementReport(
                    LocalDate.of(2024, 1, 6), LocalDate.of(2024, 1, 18), CATEGORY_BAHAN_BAKU_ID, null);
            assertThat(middle.items()).extracting(i -> i.transactionDate() + "|" + i.referenceNumber())
                    .containsExactly("2024-01-06|PO-003", "2024-01-15|PO-002", "2024-01-18|PO-004");
            assertThat(middle.totalOutboundQty()).isEqualByComparingTo("0");

            var products = reportService.generateStockMovementReport(JAN_1_2024, JAN_31_2024, CATEGORY_PRODUK_ID, null);
            assertThat(products.items()).extracting(InventoryReportService.StockMovementItem::transactionType)
                    .containsExactly(InventoryTransactionType.PRODUCTION_IN, InventoryTransactionType.SALE);
            assertThat(products.totalInboundQty()).isEqualByComparingTo("30");
            assertThat(products.totalOutboundQty()).isEqualByComparingTo("10");
        }

        @Test
        @DisplayName("Should label sales and adjustments and count them as outbound/inbound")
        void shouldLabelRecordedMovements() {
            Fixture f = fixture();

            var report = reportService.generateStockMovementReport(DAY, DAY, null, f.average.getId());

            assertThat(report.items()).extracting(InventoryReportServiceTest::movement).containsExactly(
                    DAY + "|" + f.average.getCode() + "|" + f.average.getName() + "|" + f.category.getName()
                            + "|Pembelian|10.0000|10000.0000|100000.00|BELI-1|10.0000",
                    DAY + "|" + f.average.getCode() + "|" + f.average.getName() + "|" + f.category.getName()
                            + "|Pembelian|5.0000|13000.0000|65000.00|BELI-2|15.0000",
                    DAY + "|" + f.average.getCode() + "|" + f.average.getName() + "|" + f.category.getName()
                            + "|Penjualan|6.0000|11000.0000|66000.00|JUAL-1|9.0000",
                    DAY + "|" + f.average.getCode() + "|" + f.average.getName() + "|" + f.category.getName()
                            + "|Penyesuaian Keluar|1.0000|11000.0000|11000.00|ADJ-1|8.0000");
            assertThat(report.totalInboundQty()).isEqualByComparingTo("15");
            assertThat(report.totalOutboundQty()).isEqualByComparingTo("7");
            assertThat(report.totalInboundValue()).isEqualByComparingTo("165000");
            assertThat(report.totalOutboundValue()).isEqualByComparingTo("77000");
        }

        @Test
        @DisplayName("Should return an empty report with zero totals for a period without movements")
        void shouldReturnEmptyReportForEmptyPeriod() {
            var report = reportService.generateStockMovementReport(
                    LocalDate.of(2099, 1, 1), LocalDate.of(2099, 12, 31), null, null);

            assertThat(report.items()).isEmpty();
            assertThat(report.totalInboundQty()).isEqualByComparingTo("0");
            assertThat(report.totalOutboundValue()).isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("Valuation Report")
    class ValuationTests {

        @Test
        @DisplayName("Should value products in stock with their costing method")
        void shouldValueSeededProducts() {
            var report = reportService.generateValuationReport(CATEGORY_BAHAN_BAKU_ID);

            assertThat(report.items()).extracting(InventoryReportServiceTest::valuation).containsExactly(
                    "PRD-TEST-001|Tepung Terigu|Bahan Baku|kg|50.0000|10000.0000|500000.00|Rata-rata",
                    "PRD-TEST-002|Gula Pasir|Bahan Baku|kg|25.0000|15000.0000|375000.00|Rata-rata");
            assertThat(report.totalValue()).isEqualByComparingTo("875000");
        }

        @Test
        @DisplayName("Should skip products without stock and inactive products, and label FIFO products")
        void shouldSkipEmptyAndInactiveProducts() {
            Fixture f = fixture();

            var report = reportService.generateValuationReport(f.category.getId());

            assertThat(report.items()).extracting(InventoryReportServiceTest::valuation).containsExactly(
                    f.average.getCode() + "|" + f.average.getName() + "|" + f.category.getName() + "|kg|8.0000|11000.0000|88000.00|Rata-rata",
                    f.fifo.getCode() + "|" + f.fifo.getName() + "|" + f.category.getName() + "|pcs|5.0000|8000.0000|40000.00|FIFO");
            assertThat(report.totalValue()).isEqualByComparingTo("128000");
            assertThat(reportService.generateValuationReport(null).items())
                    .extracting(InventoryReportService.ValuationItem::productCode)
                    .contains(f.average.getCode(), f.fifo.getCode())
                    .doesNotContain(f.soldOut.getCode(), f.inactive.getCode());
        }
    }

    @Nested
    @DisplayName("Profitability Report")
    class ProfitabilityTests {

        @Test
        @DisplayName("Should compute revenue, COGS and margin of a product's sales")
        void shouldComputeSeededProductProfitability() {
            var report = reportService.generateProfitabilityReport(JAN_1_2024, JAN_31_2024, null, PRODUCT_KUE_BOLU_ID);

            assertThat(report.items()).extracting(InventoryReportServiceTest::profitability)
                    .containsExactly("PRD-TEST-003|Kue Bolu|Produk Jadi|pcs|10.0000|500000.0000|300000.00|200000.0000|40.00|1");
            assertThat(report.totalRevenue()).isEqualByComparingTo("500000");
            assertThat(report.totalCogs()).isEqualByComparingTo("300000");
            assertThat(report.totalMargin()).isEqualByComparingTo("200000");
            assertThat(report.totalQuantitySold()).isEqualByComparingTo("10");
            assertThat(report.getTotalMarginPercent()).isEqualByComparingTo("40.00");
        }

        @Test
        @DisplayName("Should limit to a category's sales within the period")
        void shouldFilterByCategoryAndPeriod() {
            assertThat(reportService.generateProfitabilityReport(JAN_1_2024, JAN_31_2024, CATEGORY_PRODUK_ID, null).items())
                    .extracting(InventoryReportService.ProfitabilityItem::productCode).containsExactly("PRD-TEST-003");
            assertThat(reportService.generateProfitabilityReport(JAN_1_2024, JAN_31_2024, CATEGORY_BAHAN_BAKU_ID, null).items())
                    .isEmpty();
            assertThat(reportService.generateProfitabilityReport(
                    LocalDate.of(2024, 1, 26), JAN_31_2024, CATEGORY_PRODUK_ID, null).items()).isEmpty();
        }

        @Test
        @DisplayName("Should group sales per product, sort by margin and treat a sale without price as zero revenue")
        void shouldGroupAndSortRecordedSales() {
            Fixture f = fixture();

            var report = reportService.generateProfitabilityReport(DAY, DAY, f.category.getId(), null);

            assertThat(report.items()).extracting(InventoryReportServiceTest::profitability).containsExactly(
                    f.fifo.getCode() + "|" + f.fifo.getName() + "|" + f.category.getName() + "|pcs|5.0000|65000.0000|36000.00|29000.0000|44.62|1",
                    f.average.getCode() + "|" + f.average.getName() + "|" + f.category.getName() + "|kg|6.0000|90000.0000|66000.00|24000.0000|26.67|1",
                    f.soldOut.getCode() + "|" + f.soldOut.getName() + "|" + f.category.getName() + "|kg|2.0000|0.0000|6000.00|-6000.0000|0|1");
            assertThat(report.totalRevenue()).isEqualByComparingTo("155000");
            assertThat(report.totalCogs()).isEqualByComparingTo("108000");
            assertThat(report.totalMargin()).isEqualByComparingTo("47000");
            assertThat(report.totalQuantitySold()).isEqualByComparingTo("13");
            assertThat(report.getTotalMarginPercent()).isEqualByComparingTo("30.32");
        }

        @Test
        @DisplayName("Should return zero totals and zero margin percent for a period without sales")
        void shouldReturnZeroForEmptyPeriod() {
            var report = reportService.generateProfitabilityReport(
                    LocalDate.of(2099, 1, 1), LocalDate.of(2099, 12, 31), null, null);

            assertThat(report.items()).isEmpty();
            assertThat(report.totalRevenue()).isEqualByComparingTo("0");
            assertThat(report.getTotalMarginPercent()).isEqualByComparingTo("0");
        }
    }

    /**
     * Own category with: weighted-average product bought 10 @ 10,000 and 5 @ 13,000, sold 6 @ 15,000, adjusted out 1
     * (8 left @ 11,000); FIFO product (minimum 10) bought 4 @ 7,000 and 6 @ 8,000, sold 5 @ 13,000 (5 left @ 8,000);
     * a product bought 2 @ 3,000 and sold out without a price; an inactive product with 3 in stock.
     */
    private record Fixture(ProductCategory category, Product average, Product fifo, Product soldOut, Product inactive) {
    }

    private Fixture fixture() {
        String id = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        ProductCategory category = new ProductCategory();
        category.setCode("RPT-" + id);
        category.setName("Kategori Laporan " + id);
        category = categoryRepository.save(category);

        Product average = product(category, "RPT-" + id + "-A", "Rata " + id, "kg", CostingMethod.WEIGHTED_AVERAGE, "5");
        Product fifo = product(category, "RPT-" + id + "-B", "Fifo " + id, "pcs", CostingMethod.FIFO, "10");
        Product soldOut = product(category, "RPT-" + id + "-C", "Habis " + id, "kg", CostingMethod.WEIGHTED_AVERAGE, "0");
        Product inactive = product(category, "RPT-" + id + "-D", "Nonaktif " + id, "kg", CostingMethod.WEIGHTED_AVERAGE, "0");

        inventoryService.recordPurchase(average.getId(), DAY, new BigDecimal("10"), new BigDecimal("10000"), "BELI-1", null);
        inventoryService.recordPurchase(average.getId(), DAY, new BigDecimal("5"), new BigDecimal("13000"), "BELI-2", null);
        inventoryService.recordSale(average.getId(), DAY, new BigDecimal("6"), new BigDecimal("15000"), "JUAL-1", null);
        inventoryService.recordAdjustmentOut(average.getId(), DAY, new BigDecimal("1"), "ADJ-1", "Rusak");

        inventoryService.recordPurchase(fifo.getId(), DAY, new BigDecimal("4"), new BigDecimal("7000"), "BELI-1", null);
        inventoryService.recordPurchase(fifo.getId(), DAY, new BigDecimal("6"), new BigDecimal("8000"), "BELI-2", null);
        inventoryService.recordSale(fifo.getId(), DAY, new BigDecimal("5"), new BigDecimal("13000"), "JUAL-1", null);

        inventoryService.recordPurchase(soldOut.getId(), DAY, new BigDecimal("2"), new BigDecimal("3000"), "BELI-1", null);
        inventoryService.recordSale(soldOut.getId(), DAY, new BigDecimal("2"), null, "JUAL-1", null);

        inventoryService.recordPurchase(inactive.getId(), DAY, new BigDecimal("3"), new BigDecimal("1000"), "BELI-1", null);
        inactive.setActive(false);
        productRepository.save(inactive);

        return new Fixture(category, average, fifo, soldOut, inactive);
    }

    private Product product(ProductCategory category, String code, String name, String unit,
                            CostingMethod costingMethod, String minimumStock) {
        Product product = new Product();
        product.setCode(code);
        product.setName(name);
        product.setUnit(unit);
        product.setCategory(category);
        product.setCostingMethod(costingMethod);
        product.setMinimumStock(new BigDecimal(minimumStock));
        return productRepository.save(product);
    }

    private static List<String> codes(InventoryReportService.StockBalanceReport report) {
        return report.items().stream().map(InventoryReportService.StockBalanceItem::productCode).toList();
    }

    private static String balance(InventoryReportService.StockBalanceReport report, String code) {
        var item = report.items().stream().filter(i -> i.productCode().equals(code)).findFirst()
                .orElseThrow(() -> new AssertionError("No stock balance row for " + code));
        return String.join("|", item.productName(), item.categoryName(), item.unit(), scaled(item.quantity(), 4),
                scaled(item.averageCost(), 4), scaled(item.totalValue(), 2), scaled(item.minimumStock(), 4),
                String.valueOf(item.belowMinimum()));
    }

    private static String movement(InventoryReportService.StockMovementItem i) {
        return String.join("|", i.transactionDate().toString(), i.productCode(), i.productName(), i.categoryName(),
                i.transactionTypeLabel(), scaled(i.quantity(), 4), scaled(i.unitCost(), 4), scaled(i.totalCost(), 2),
                i.referenceNumber(), scaled(i.balanceAfter(), 4));
    }

    private static String valuation(InventoryReportService.ValuationItem i) {
        return String.join("|", i.productCode(), i.productName(), i.categoryName(), i.unit(), scaled(i.quantity(), 4),
                scaled(i.averageCost(), 4), scaled(i.totalValue(), 2), i.costingMethod());
    }

    private static String profitability(InventoryReportService.ProfitabilityItem i) {
        return String.join("|", i.productCode(), i.productName(), i.categoryName(), i.unit(), scaled(i.quantitySold(), 4),
                scaled(i.revenue(), 4), scaled(i.cogs(), 2), scaled(i.margin(), 4), i.marginPercent().toPlainString(),
                String.valueOf(i.transactionCount()));
    }

    private static String scaled(BigDecimal value, int scale) {
        return value.setScale(scale, java.math.RoundingMode.UNNECESSARY).toPlainString();
    }
}
