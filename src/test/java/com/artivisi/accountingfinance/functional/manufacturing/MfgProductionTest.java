package com.artivisi.accountingfinance.functional.manufacturing;

import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.util.List;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Seeded coffee-shop production orders (industry-seed/coffee-shop 39_production_orders.csv).
 */
@DisplayName("Manufacturing - Production Orders")
@Import(CoffeeTestDataInitializer.class)
class MfgProductionTest extends PlaywrightTestBase {

    @Test
    @DisplayName("Production order list shows both seeded completed orders with quantity and total cost")
    void shouldListSeededProductionOrders() {
        loginAsAdmin();
        navigateTo("/inventory/production");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Production Orders");
        assertOrderRow("PROD-001", "BOM-CRS", "Croissant", "24.00 pcs", "Rp 106,920");
        assertOrderRow("PROD-002", "BOM-RBC", "Roti Bakar Coklat", "20.00 pcs", "Rp 87,760");

        takeManualScreenshot("coffee/production-order-list");
    }

    @Test
    @DisplayName("Croissant order detail shows header, cost summary and component requirements for 24 pcs")
    void shouldShowCroissantOrderDetail() {
        openOrder("PROD-001");

        assertThat(page.locator("#page-title")).hasText("Production Order: PROD-001");
        assertThat(page.locator("[data-testid='order-status-completed']")).hasText("Selesai");
        assertThat(page.locator("[data-testid='order-number']")).hasText("PROD-001");
        assertThat(page.locator("[data-testid='order-date']")).hasText("08 Januari 2024");
        assertThat(page.locator("[data-testid='bom-name']")).hasText("BOM-CRS - BOM Croissant");
        assertThat(page.locator("[data-testid='product-name']")).hasText("CROISSANT - Croissant");
        assertThat(info("Target Selesai")).hasText("08 Januari 2024");
        assertThat(info("Tanggal Selesai")).hasText("08 Januari 2024");
        assertThat(info("Catatan")).hasText("Produksi croissant batch pertama");
        assertThat(page.locator("[data-testid='order-total-cost']")).hasText("Rp 106,920");
        assertThat(page.locator("[data-testid='order-quantity']")).hasText("24.00 pcs");
        assertThat(page.locator("[data-testid='order-unit-cost']")).hasText("Rp 4,455.00");
        assertRequirements(List.of(
                new String[]{"1", "TEPUNG-TERIGU", "Tepung Terigu", "3.00", "3.00", "kg"},
                new String[]{"2", "BUTTER", "Butter", "1.50", "1.50", "kg"},
                new String[]{"3", "TELUR", "Telur Ayam", "12.00", "12.00", "butir"},
                new String[]{"4", "RAGI", "Ragi Instan", "10.00", "10.00", "gram"},
                new String[]{"5", "GARAM", "Garam", "6.00", "6.00", "gram"},
                new String[]{"6", "SUSU-SEGAR", "Susu Segar", "0.60", "0.60", "liter"}));

        takeManualScreenshot("coffee/production-order-detail-croissant");
    }

    @Test
    @DisplayName("Roti Bakar Coklat order detail shows header, cost summary and component requirements for 20 pcs")
    void shouldShowRotiCoklatOrderDetail() {
        openOrder("PROD-002");

        assertThat(page.locator("#page-title")).hasText("Production Order: PROD-002");
        assertThat(page.locator("[data-testid='order-status-completed']")).hasText("Selesai");
        assertThat(page.locator("[data-testid='order-date']")).hasText("08 Januari 2024");
        assertThat(page.locator("[data-testid='bom-name']")).hasText("BOM-RBC - BOM Roti Bakar Coklat");
        assertThat(page.locator("[data-testid='product-name']")).hasText("ROTI-COKLAT - Roti Bakar Coklat");
        assertThat(info("Catatan")).hasText("Produksi roti bakar coklat batch pertama");
        assertThat(page.locator("[data-testid='order-total-cost']")).hasText("Rp 87,760");
        assertThat(page.locator("[data-testid='order-quantity']")).hasText("20.00 pcs");
        assertThat(page.locator("[data-testid='order-unit-cost']")).hasText("Rp 4,388.00");
        assertRequirements(List.of(
                new String[]{"1", "TEPUNG-TERIGU", "Tepung Terigu", "2.50", "2.50", "kg"},
                new String[]{"2", "BUTTER", "Butter", "1.00", "1.00", "kg"},
                new String[]{"3", "TELUR", "Telur Ayam", "10.00", "10.00", "butir"},
                new String[]{"4", "RAGI", "Ragi Instan", "8.00", "8.00", "gram"},
                new String[]{"5", "GARAM", "Garam", "5.00", "5.00", "gram"},
                new String[]{"6", "COKLAT", "Coklat Blok", "0.40", "0.40", "kg"}));
    }

    @Test
    @DisplayName("Status filter lists the seeded orders under Selesai only")
    void shouldFilterSeededOrdersByStatus() {
        loginAsAdmin();

        navigateTo("/inventory/production?status=COMPLETED");
        waitForPageLoad();
        assertThat(page.locator("#production-status-filter")).hasValue("COMPLETED");
        assertThat(page.locator("[data-testid='order-status-PROD-001']")).hasText("Selesai");
        assertThat(page.locator("[data-testid='order-status-PROD-002']")).hasText("Selesai");

        for (String status : List.of("DRAFT", "IN_PROGRESS", "CANCELLED")) {
            navigateTo("/inventory/production?status=" + status);
            waitForPageLoad();
            assertThat(page.locator("#production-status-filter")).hasValue(status);
            assertThat(page.locator("[data-testid='production-order-row-PROD-001']")).hasCount(0);
            assertThat(page.locator("[data-testid='production-order-row-PROD-002']")).hasCount(0);
        }
    }

    private void assertOrderRow(String orderNumber, String bomCode, String productName, String quantity, String totalCost) {
        Locator row = page.locator("[data-testid='production-order-row-" + orderNumber + "']");
        assertThat(row.locator("td").nth(2).locator("div")).hasText(new String[]{bomCode, productName});
        assertThat(row.locator("td")).hasText(new String[]{
                orderNumber, "08 Jan 2024", bomCode + " " + productName, quantity, "Selesai", totalCost, "Detail"});
    }

    private void openOrder(String orderNumber) {
        loginAsAdmin();
        navigateTo("/inventory/production");
        waitForPageLoad();
        page.locator("[data-testid='order-detail-link-" + orderNumber + "']").click();
        waitForPageLoad();
    }

    private Locator info(String label) {
        return page.locator("dt:text-is('" + label + "') + dd").first();
    }

    private void assertRequirements(List<String[]> expected) {
        Locator rows = page.locator("h2:text-is('Komponen yang Dibutuhkan') + div tbody tr");
        assertThat(rows).hasCount(expected.size());
        for (int i = 0; i < expected.size(); i++) {
            assertThat(rows.nth(i).locator("td")).hasText(expected.get(i));
        }
    }
}
