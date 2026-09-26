package com.artivisi.accountingfinance.functional.manufacturing;

import com.artivisi.accountingfinance.functional.util.CsvLoader;
import com.artivisi.accountingfinance.functional.util.ExpectedInventoryRow;
import com.artivisi.accountingfinance.functional.util.ProductionOrderRow;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Checks the seeded coffee-shop production orders and stock balances against
 * testdata/coffee/production-orders.csv and testdata/coffee/expected-inventory.csv.
 */
@DisplayName("Manufacturing - CSV-Driven Tests")
@Import(CoffeeTestDataInitializer.class)
class MfgCsvDrivenTest extends PlaywrightTestBase {

    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.of("id", "ID"));
    private static final DecimalFormatSymbols US = DecimalFormatSymbols.getInstance(Locale.US);

    @TestFactory
    @DisplayName("Seeded production orders match production-orders.csv")
    Stream<DynamicTest> verifyProductionOrdersFromCsv() {
        List<ProductionOrderRow> orders = CsvLoader.loadProductionOrders("coffee/production-orders.csv");
        assertEquals(2, orders.size(), "coffee/production-orders.csv rows");

        return orders.stream()
            .map(order -> DynamicTest.dynamicTest(
                "Production Order " + order.sequence() + ": " + order.bomCode() + " x" + order.quantity(),
                () -> verifyProductionOrder(order)
            ));
    }

    private void verifyProductionOrder(ProductionOrderRow order) {
        String orderNumber = String.format("PROD-%03d", order.sequence());
        loginAsAdmin();
        navigateTo("/inventory/production");
        waitForPageLoad();
        page.locator("[data-testid='order-detail-link-" + orderNumber + "']").click();
        waitForPageLoad();

        assertThat(page.locator("[data-testid='order-number']")).hasText(orderNumber);
        assertThat(page.locator("[data-testid='order-status-completed']")).hasText("Selesai");
        assertThat(page.locator("[data-testid='bom-name']")).containsText(order.bomCode() + " - ");
        assertThat(page.locator("[data-testid='order-quantity']")).hasText(order.quantity() + ".00 pcs");
        assertThat(page.locator("[data-testid='order-date']")).hasText(LocalDate.parse(order.orderDate()).format(LONG_DATE));
        assertThat(info("Target Selesai")).hasText(LocalDate.parse(order.plannedCompletionDate()).format(LONG_DATE));
        assertThat(info("Catatan")).hasText(order.notes());

        if (order.screenshot()) {
            takeManualScreenshot("coffee/production-" + order.bomCode().toLowerCase());
        }
    }

    @TestFactory
    @DisplayName("Seeded stock balances match expected-inventory.csv")
    Stream<DynamicTest> verifyInventoryLevelsFromCsv() {
        List<ExpectedInventoryRow> inventory = CsvLoader.loadExpectedInventory("coffee/expected-inventory.csv");
        assertEquals(12, inventory.size(), "coffee/expected-inventory.csv rows");

        return inventory.stream()
            .map(item -> DynamicTest.dynamicTest(
                "Inventory: " + item.productName() + " = " + item.expectedQuantity(),
                () -> verifyInventoryLevel(item)
            ));
    }

    private void verifyInventoryLevel(ExpectedInventoryRow expected) {
        loginAsAdmin();
        navigateTo("/inventory/stock?search=" + expected.productCode());
        waitForPageLoad();

        Locator cells = page.locator("[data-testid='stock-row-" + expected.productCode() + "'] td");
        assertThat(page.locator("[data-testid='stock-product-name-" + expected.productCode() + "']"))
                .hasText(expected.productName());
        assertThat(page.locator("[data-testid='stock-quantity-" + expected.productCode() + "']"))
                .hasText(format(expected.expectedQuantity(), "#,##0.00"));
        assertThat(cells.nth(4)).hasText(format(expected.expectedAverageCost(), "#,##0.00"));
        assertThat(cells.nth(5)).hasText(format(expected.expectedQuantity().multiply(expected.expectedAverageCost()), "#,##0"));
    }

    private Locator info(String label) {
        return page.locator("dt:text-is('" + label + "') + dd").first();
    }

    private static String format(BigDecimal value, String pattern) {
        return new DecimalFormat(pattern, US).format(value);
    }
}
