package com.artivisi.accountingfinance.functional.manufacturing;

import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Seeded coffee-shop BOMs (industry-seed/coffee-shop 37_bill_of_materials.csv, 38_bom_lines.csv).
 */
@DisplayName("Manufacturing - Bill of Materials")
@Import(CoffeeTestDataInitializer.class)
class MfgBomTest extends PlaywrightTestBase {

    @Test
    @DisplayName("BOM list shows the seeded croissant and roti bakar coklat recipes")
    void shouldListSeededBoms() {
        loginAsAdmin();
        navigateTo("/inventory/bom");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Bill of Materials");
        assertThat(page.locator("[data-testid='bom-row-BOM-CRS'] td")).hasText(new String[]{
                "BOM-CRS", "BOM Croissant", "Croissant", "24.00 pcs", "Aktif", "Detail Edit"});
        assertThat(page.locator("[data-testid='bom-row-BOM-RBC'] td")).hasText(new String[]{
                "BOM-RBC", "BOM Roti Bakar Coklat", "Roti Bakar Coklat", "20.00 pcs", "Aktif", "Detail Edit"});

        takeManualScreenshot("coffee/bom-list");
    }

    @Test
    @DisplayName("Roti Bakar Coklat BOM detail shows header and the six components with quantities")
    void shouldShowRotiCoklatBomDetail() {
        loginAsAdmin();
        navigateTo("/inventory/bom");
        waitForPageLoad();
        page.locator("[data-testid='bom-detail-link-BOM-RBC']").click();
        waitForPageLoad();

        assertThat(page.locator("[data-testid='bom-code']")).hasText("BOM-RBC");
        assertThat(page.locator("[data-testid='bom-product']")).hasText("ROTI-COKLAT - Roti Bakar Coklat");
        assertThat(page.locator("[data-testid='bom-output-quantity']")).hasText("20.00 pcs");
        assertThat(page.locator("dt:text-is('Deskripsi') + dd"))
                .hasText("Resep produksi roti bakar coklat batch 20 pcs");
        assertThat(page.locator("tr[data-testid^='bom-component-row-']")).hasCount(6);
        assertComponent(1, "TEPUNG-TERIGU", "Tepung Terigu", "2.50", "kg");
        assertComponent(2, "BUTTER", "Butter", "1.00", "kg");
        assertComponent(3, "TELUR", "Telur Ayam", "10.00", "butir");
        assertComponent(4, "RAGI", "Ragi Instan", "8.00", "gram");
        assertComponent(5, "GARAM", "Garam", "5.00", "gram");
        assertComponent(6, "COKLAT", "Coklat Blok", "0.40", "kg");
    }

    @Test
    @DisplayName("Croissant BOM detail shows header, 24 pcs output and the six components with quantities")
    void shouldShowCroissantBomDetail() {
        loginAsAdmin();
        navigateTo("/inventory/bom");
        waitForPageLoad();
        page.locator("[data-testid='bom-detail-link-BOM-CRS']").click();
        waitForPageLoad();

        assertThat(page.locator("[data-testid='bom-code']")).hasText("BOM-CRS");
        assertThat(page.locator("[data-testid='bom-product']")).hasText("CROISSANT - Croissant");
        assertThat(page.locator("[data-testid='bom-output-quantity']")).hasText("24.00 pcs");
        assertThat(page.locator("dt:text-is('Deskripsi') + dd"))
                .hasText("Resep produksi croissant batch 24 pcs");
        assertThat(page.locator("tr[data-testid^='bom-component-row-']")).hasCount(6);
        assertComponent(1, "TEPUNG-TERIGU", "Tepung Terigu", "3.00", "kg");
        assertComponent(2, "BUTTER", "Butter", "1.50", "kg");
        assertComponent(3, "TELUR", "Telur Ayam", "12.00", "butir");
        assertComponent(4, "RAGI", "Ragi Instan", "10.00", "gram");
        assertComponent(5, "GARAM", "Garam", "6.00", "gram");
        assertComponent(6, "SUSU-SEGAR", "Susu Segar", "0.60", "liter");

        takeManualScreenshot("coffee/bom-detail-croissant");
    }

    private void assertComponent(int no, String code, String name, String quantity, String unit) {
        assertThat(page.locator("[data-testid='bom-component-row-" + code + "'] td")).hasText(new String[]{
                String.valueOf(no), code, name, quantity, unit, "-"});
    }
}
