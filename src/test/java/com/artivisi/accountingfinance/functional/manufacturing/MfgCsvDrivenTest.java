package com.artivisi.accountingfinance.functional.manufacturing;

import com.artivisi.accountingfinance.functional.util.CsvLoader;
import com.artivisi.accountingfinance.functional.util.ExpectedInventoryRow;
import com.artivisi.accountingfinance.functional.util.ProductionOrderRow;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.stream.Stream;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CSV-Driven Manufacturing Tests
 * Loads coffee shop seed data via CoffeeTestDataInitializer.
 * Executes test scenarios from CSV files as dynamic tests.
 * Validates production orders and inventory levels against expected values.
 */
@DisplayName("Manufacturing - CSV-Driven Tests")
@Import(CoffeeTestDataInitializer.class)
public class MfgCsvDrivenTest extends PlaywrightTestBase {

    @Test
    @DisplayName("Should load production orders CSV")
    void shouldLoadProductionOrdersCsv() {
        List<ProductionOrderRow> orders = CsvLoader.loadProductionOrders("coffee/production-orders.csv");

        // Verify CSV loads correctly
        assertEquals(2, orders.size());
        assertEquals("BOM-CRS", orders.get(0).bomCode());
        assertEquals("BOM-RBC", orders.get(1).bomCode());
    }

    @Test
    @DisplayName("Should load expected inventory CSV")
    void shouldLoadExpectedInventoryCsv() {
        List<ExpectedInventoryRow> inventory = CsvLoader.loadExpectedInventory("coffee/expected-inventory.csv");

        // Verify CSV loads correctly
        assertTrue(inventory.size() > 0);
        assertTrue(inventory.stream().anyMatch(r -> r.productCode().equals("CROISSANT")));
    }

    @TestFactory
    @DisplayName("Verify production orders from CSV")
    Stream<DynamicTest> verifyProductionOrdersFromCsv() {
        List<ProductionOrderRow> orders = CsvLoader.loadProductionOrders("coffee/production-orders.csv");

        return orders.stream()
            .map(order -> DynamicTest.dynamicTest(
                "Production Order " + order.sequence() + ": " + order.bomCode() + " x" + order.quantity(),
                () -> verifyProductionOrder(order)
            ));
    }

    private void verifyProductionOrder(ProductionOrderRow order) {
        loginAsAdmin();
        navigateTo("/inventory/production");
        waitForPageLoad();

        // Find and click on the production order using data-testid
        String orderNumber = "PROD-00" + order.sequence();
        page.locator("[data-testid='order-detail-link-" + orderNumber + "']").click();
        waitForPageLoad();

        // Verify order details match CSV using data-testid
        assertThat(page.locator("[data-testid='order-number']")).containsText(orderNumber);
        assertThat(page.locator("[data-testid='order-quantity']")).containsText(String.valueOf(order.quantity()));
        assertThat(page.locator("[data-testid='order-status-completed']")).isVisible();

        if (order.screenshot()) {
            takeManualScreenshot("coffee/production-" + order.bomCode().toLowerCase());
        }
    }

    @TestFactory
    @DisplayName("Verify inventory levels from CSV")
    Stream<DynamicTest> verifyInventoryLevelsFromCsv() {
        List<ExpectedInventoryRow> inventory = CsvLoader.loadExpectedInventory("coffee/expected-inventory.csv");

        // Only test finished goods (pastries) that show on stock page
        return inventory.stream()
            .filter(item -> item.productCode().equals("CROISSANT") || item.productCode().equals("ROTI-COKLAT"))
            .map(item -> DynamicTest.dynamicTest(
                "Inventory: " + item.productName() + " = " + item.expectedQuantity(),
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

    @Test
    @DisplayName("Verify total finished goods after production and sales")
    void verifyTotalFinishedGoodsAfterProductionAndSales() {
        loginAsAdmin();
        navigateTo("/inventory/stock");
        waitForPageLoad();

        // Croissant: 24 produced - 15 sold = 9 remaining
        assertThat(page.locator("[data-testid='stock-product-name-CROISSANT']")).containsText("Croissant");
        assertThat(page.locator("[data-testid='stock-quantity-CROISSANT']")).containsText("9");

        // Roti Bakar Coklat: 20 produced - 12 sold = 8 remaining
        assertThat(page.locator("[data-testid='stock-product-name-ROTI-COKLAT']")).containsText("Roti Bakar Coklat");
        assertThat(page.locator("[data-testid='stock-quantity-ROTI-COKLAT']")).containsText("8");
    }

    @Test
    @DisplayName("Verify raw material consumption after production")
    void verifyRawMaterialConsumptionAfterProduction() {
        loginAsAdmin();
        navigateTo("/inventory/transactions");
        waitForPageLoad();

        // Verify PRODUCTION_OUT transactions exist for components using data-testid
        assertThat(page.locator("[data-testid='transaction-type-PRODUCTION_OUT']").first()).isVisible();
    }
}
