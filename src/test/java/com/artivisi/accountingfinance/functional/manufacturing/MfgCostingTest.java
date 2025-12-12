package com.artivisi.accountingfinance.functional.manufacturing;

import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Manufacturing Costing Tests
 * Tests COGM (Cost of Goods Manufactured) and inventory valuation.
 * Loads coffee shop seed data via CoffeeTestDataInitializer.
 */
@DisplayName("Manufacturing - Costing & Valuation")
@Import(CoffeeTestDataInitializer.class)
public class MfgCostingTest extends PlaywrightTestBase {

    @Test
    @DisplayName("Should display inventory transactions page")
    void shouldDisplayInventoryTransactionsPage() {
        loginAsAdmin();
        navigateTo("/inventory/transactions");
        waitForPageLoad();

        // Verify inventory transactions page loads
        assertThat(page.locator("h1")).containsText("Transaksi Persediaan");

        // Take screenshot for user manual
        takeManualScreenshot("coffee/inventory-transactions-list");
    }

    @Test
    @DisplayName("Should display production output transactions")
    void shouldDisplayProductionOutputTransactions() {
        loginAsAdmin();
        navigateTo("/inventory/transactions");
        waitForPageLoad();

        // Verify PRODUCTION_IN transactions using data-testid
        assertThat(page.locator("[data-testid='transaction-type-PRODUCTION_IN']").first()).isVisible();
    }

    @Test
    @DisplayName("Should display production component consumption")
    void shouldDisplayProductionComponentConsumption() {
        loginAsAdmin();
        navigateTo("/inventory/transactions");
        waitForPageLoad();

        // Verify PRODUCTION_OUT transactions using data-testid
        assertThat(page.locator("[data-testid='transaction-type-PRODUCTION_OUT']").first()).isVisible();
    }

    @Test
    @DisplayName("Should display finished goods inventory after production")
    void shouldDisplayFinishedGoodsInventoryAfterProduction() {
        loginAsAdmin();
        navigateTo("/inventory/stock");
        waitForPageLoad();

        // Verify finished goods have stock after production using data-testid
        // Croissant: 24 produced, 15 sold = 9 remaining
        assertThat(page.locator("[data-testid='stock-product-name-CROISSANT']")).containsText("Croissant");
        assertThat(page.locator("[data-testid='stock-quantity-CROISSANT']")).containsText("9");

        // Roti Bakar Coklat: 20 produced, 12 sold = 8 remaining
        assertThat(page.locator("[data-testid='stock-product-name-ROTI-COKLAT']")).containsText("Roti Bakar Coklat");
        assertThat(page.locator("[data-testid='stock-quantity-ROTI-COKLAT']")).containsText("8");
    }

    @Test
    @DisplayName("Should display purchase transactions")
    void shouldDisplayPurchaseTransactions() {
        loginAsAdmin();
        navigateTo("/inventory/transactions");
        waitForPageLoad();

        // Verify PURCHASE transactions using data-testid
        assertThat(page.locator("[data-testid='transaction-type-PURCHASE']").first()).isVisible();
    }

    @Test
    @DisplayName("Should display sales transactions with COGS")
    void shouldDisplaySalesTransactionsWithCogs() {
        loginAsAdmin();
        navigateTo("/inventory/transactions");
        waitForPageLoad();

        // Verify SALE transactions using data-testid
        assertThat(page.locator("[data-testid='transaction-type-SALE']").first()).isVisible();
    }

    @Test
    @DisplayName("Should show unit cost in production order detail")
    void shouldShowUnitCostInProductionOrderDetail() {
        loginAsAdmin();
        navigateTo("/inventory/production");
        waitForPageLoad();

        // Click on PROD-001 to see unit cost using data-testid
        page.locator("[data-testid='order-detail-link-PROD-001']").click();
        waitForPageLoad();

        // Verify unit cost is displayed (4,455 per croissant) using data-testid
        assertThat(page.locator("[data-testid='order-unit-cost']")).containsText("4,455");
    }
}
