package com.artivisi.accountingfinance.functional.manufacturing;

import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Manufacturing Production Order Tests
 * Tests production order viewing and management.
 * Loads coffee shop seed data via CoffeeTestDataInitializer.
 */
@DisplayName("Manufacturing - Production Orders")
@Import(CoffeeTestDataInitializer.class)
public class MfgProductionTest extends PlaywrightTestBase {

    @Test
    @DisplayName("Should display production order list")
    void shouldDisplayProductionOrderList() {
        loginAsAdmin();
        navigateTo("/inventory/production");
        waitForPageLoad();

        // Verify production order list page loads
        assertThat(page.locator("h1")).containsText("Production Order");

        // Take screenshot for user manual
        takeManualScreenshot("coffee/production-order-list");
    }

    @Test
    @DisplayName("Should display completed production orders")
    void shouldDisplayCompletedProductionOrders() {
        loginAsAdmin();
        navigateTo("/inventory/production");
        waitForPageLoad();

        // Verify production orders from V831 using data-testid
        assertThat(page.locator("[data-testid='production-order-row-PROD-001']")).isVisible();
        assertThat(page.locator("[data-testid='production-order-row-PROD-002']")).isVisible();
    }

    @Test
    @DisplayName("Should display production order detail - Croissant")
    void shouldDisplayProductionOrderDetailCroissant() {
        loginAsAdmin();
        navigateTo("/inventory/production");
        waitForPageLoad();

        // Click on PROD-001 using data-testid
        page.locator("[data-testid='order-detail-link-PROD-001']").click();
        waitForPageLoad();

        // Take screenshot for user manual
        takeManualScreenshot("coffee/production-order-detail-croissant");

        // Verify production order detail using data-testid
        assertThat(page.locator("[data-testid='order-number']")).containsText("PROD-001");
        assertThat(page.locator("[data-testid='product-name']")).containsText("Croissant");
        assertThat(page.locator("[data-testid='order-status-completed']")).isVisible();
    }

    @Test
    @DisplayName("Should display production order detail - Roti Bakar Coklat")
    void shouldDisplayProductionOrderDetailRotiCoklat() {
        loginAsAdmin();
        navigateTo("/inventory/production");
        waitForPageLoad();

        // Click on PROD-002 using data-testid
        page.locator("[data-testid='order-detail-link-PROD-002']").click();
        waitForPageLoad();

        // Verify production order detail using data-testid
        assertThat(page.locator("[data-testid='order-number']")).containsText("PROD-002");
        assertThat(page.locator("[data-testid='product-name']")).containsText("Roti Bakar Coklat");
        assertThat(page.locator("[data-testid='order-status-completed']")).isVisible();
    }

    @Test
    @DisplayName("Should show production quantity in order detail")
    void shouldShowProductionQuantityInOrderDetail() {
        loginAsAdmin();
        navigateTo("/inventory/production");
        waitForPageLoad();

        // Verify quantity is shown in list (24 for croissant production)
        assertThat(page.locator("[data-testid='order-quantity-PROD-001']")).isVisible();
        assertThat(page.locator("[data-testid='order-quantity-PROD-001']")).containsText("24");
    }

    @Test
    @DisplayName("Should filter production orders by status")
    void shouldFilterProductionOrdersByStatus() {
        loginAsAdmin();
        navigateTo("/inventory/production?status=COMPLETED");
        waitForPageLoad();

        // Verify completed orders are shown using data-testid
        assertThat(page.locator("[data-testid='production-order-row-PROD-001']")).isVisible();
        assertThat(page.locator("[data-testid='production-order-row-PROD-002']")).isVisible();
    }
}
