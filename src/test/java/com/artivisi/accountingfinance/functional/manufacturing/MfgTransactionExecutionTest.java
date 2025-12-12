package com.artivisi.accountingfinance.functional.manufacturing;

import com.artivisi.accountingfinance.functional.util.CsvLoader;
import com.artivisi.accountingfinance.functional.util.ExpectedInventoryRow;
import com.artivisi.accountingfinance.functional.util.ProductionOrderRow;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.stream.Stream;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CSV-Driven Manufacturing Tests for Coffee Shop.
 *
 * This test suite:
 * 1. Loads coffee shop seed data via CoffeeTestDataInitializer
 * 2. Reads production order scenarios from CSV file
 * 3. Verifies pre-loaded BOM and production data
 * 4. Takes screenshots for user manual
 * 5. Verifies inventory and costing reports
 *
 * Data from:
 * - Seed: industry-seed/coffee-shop/seed-data/
 * - Test: src/test/resources/testdata/coffee/
 */
@DisplayName("Manufacturing - Production Execution")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Import(CoffeeTestDataInitializer.class)
public class MfgTransactionExecutionTest extends PlaywrightTestBase {

    @Test
    @Order(1)
    @DisplayName("Should load production orders CSV")
    void shouldLoadProductionOrdersCsv() {
        List<ProductionOrderRow> orders = CsvLoader.loadProductionOrders("coffee/production-orders.csv");

        // Verify CSV loads correctly
        assertEquals(2, orders.size());
        assertEquals("BOM-CRS", orders.get(0).bomCode());
        assertEquals(24, orders.get(0).quantity());
    }

    @Test
    @Order(2)
    @DisplayName("Should load expected inventory CSV")
    void shouldLoadExpectedInventoryCsv() {
        List<ExpectedInventoryRow> inventory = CsvLoader.loadExpectedInventory("coffee/expected-inventory.csv");

        // Verify CSV loads correctly
        assertTrue(inventory.size() > 0);
        assertTrue(inventory.stream().anyMatch(r -> r.productCode().equals("CROISSANT")));
    }

    /**
     * Verify BOMs exist from V830 migration.
     */
    @Test
    @Order(3)
    @DisplayName("Verify BOMs from seed data exist")
    void verifyBomsFromMigration() {
        loginAsAdmin();
        navigateTo("/inventory/bom");
        waitForPageLoad();

        // Take screenshot for user manual
        takeManualScreenshot("coffee/bom-list");

        // Verify BOMs from coffee shop seed data using data-testid
        assertThat(page.locator("[data-testid='bom-product-BOM-CRS']")).containsText("Croissant");
        assertThat(page.locator("[data-testid='bom-product-BOM-RBC']")).containsText("Roti Bakar Coklat");
    }

    /**
     * Verify production orders from CSV scenario.
     */
    @TestFactory
    @Order(4)
    @DisplayName("Verify production orders from CSV scenario")
    Stream<DynamicTest> verifyProductionOrdersFromCsv() {
        List<ProductionOrderRow> orders = CsvLoader.loadProductionOrders("coffee/production-orders.csv");

        return orders.stream()
            .map(order -> DynamicTest.dynamicTest(
                "Prod " + order.sequence() + ": " + order.bomCode() + " x" + order.quantity(),
                () -> verifyProductionOrderExists(order)
            ));
    }

    private void verifyProductionOrderExists(ProductionOrderRow order) {
        loginAsAdmin();
        navigateTo("/inventory/production");
        waitForPageLoad();

        // Verify production order list shows orders from V831 using data-testid
        String orderNumber = "PROD-00" + order.sequence();
        assertThat(page.locator("[data-testid='production-order-row-" + orderNumber + "']")).isVisible();

        if (order.screenshot()) {
            takeManualScreenshot("coffee/production-" + order.bomCode().toLowerCase());
        }
    }

    /**
     * Verify finished goods inventory.
     */
    @TestFactory
    @Order(5)
    @DisplayName("Verify finished goods inventory from CSV")
    Stream<DynamicTest> verifyFinishedGoodsInventory() {
        List<ExpectedInventoryRow> expectedInventory = CsvLoader.loadExpectedInventory("coffee/expected-inventory.csv");

        // Only verify finished goods (Croissant, Roti Bakar Coklat)
        return expectedInventory.stream()
            .filter(item -> item.productCode().equals("CROISSANT") || item.productCode().equals("ROTI-COKLAT"))
            .map(item -> DynamicTest.dynamicTest(
                "Stock: " + item.productName(),
                () -> verifyInventoryLevel(item)
            ));
    }

    private void verifyInventoryLevel(ExpectedInventoryRow expected) {
        loginAsAdmin();
        navigateTo("/inventory/stock");
        waitForPageLoad();

        // Verify product is visible in stock list using data-testid
        assertThat(page.locator("[data-testid='stock-product-name-" + expected.productCode() + "']")).containsText(expected.productName());
    }

    /**
     * Verify BOM list report.
     */
    @Test
    @Order(6)
    @DisplayName("Verify BOM list report")
    void verifyBomListReport() {
        loginAsAdmin();
        navigateTo("/inventory/bom");
        waitForPageLoad();

        // Take screenshot for user manual
        takeManualScreenshot("coffee/report-bom-list");

        // Verify report loads
        assertThat(page.locator("h1")).containsText("Bill of Materials");
    }

    /**
     * Verify Production Order list report.
     */
    @Test
    @Order(7)
    @DisplayName("Verify Production Order list report")
    void verifyProductionOrderListReport() {
        loginAsAdmin();
        navigateTo("/inventory/production");
        waitForPageLoad();

        // Take screenshot for user manual
        takeManualScreenshot("coffee/report-production-list");

        // Verify report loads
        assertThat(page.locator("h1")).containsText("Production Order");
    }

    /**
     * Verify Stock Balance Report.
     */
    @Test
    @Order(8)
    @DisplayName("Verify Stock Balance Report")
    void verifyStockBalanceReport() {
        loginAsAdmin();
        navigateTo("/inventory/reports/stock-balance");
        waitForPageLoad();

        // Take screenshot for user manual
        takeManualScreenshot("coffee/report-stock-balance");

        // Verify report loads
        assertThat(page.locator("h1")).containsText("Saldo Stok");
    }

    /**
     * Verify Inventory Valuation Report (for COGM).
     */
    @Test
    @Order(9)
    @DisplayName("Verify Inventory Valuation Report")
    void verifyInventoryValuationReport() {
        loginAsAdmin();
        navigateTo("/inventory/reports/valuation");
        waitForPageLoad();

        // Take screenshot for user manual
        takeManualScreenshot("coffee/report-inventory-valuation");

        // Verify report loads
        assertThat(page.locator("h1")).containsText("Penilaian Persediaan");
    }

    /**
     * Verify Product Profitability Report.
     */
    @Test
    @Order(10)
    @DisplayName("Verify Product Profitability Report")
    void verifyProductProfitabilityReport() {
        loginAsAdmin();
        navigateTo("/inventory/reports/profitability");
        waitForPageLoad();

        // Take screenshot for user manual
        takeManualScreenshot("coffee/report-product-profitability");

        // Verify report loads
        assertThat(page.locator("h1")).containsText("Profitabilitas Produk");
    }
}
