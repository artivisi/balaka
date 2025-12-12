package com.artivisi.accountingfinance.functional.manufacturing;

import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Manufacturing BOM Tests
 * Tests Bill of Materials viewing and management.
 * Loads coffee shop seed data via CoffeeTestDataInitializer.
 */
@DisplayName("Manufacturing - Bill of Materials")
@Import(CoffeeTestDataInitializer.class)
public class MfgBomTest extends PlaywrightTestBase {

    @Test
    @DisplayName("Should display BOM list")
    void shouldDisplayBomList() {
        loginAsAdmin();
        navigateTo("/inventory/bom");
        waitForPageLoad();

        // Verify BOM list page loads
        assertThat(page.locator("h1")).containsText("Bill of Materials");

        // Take screenshot for user manual
        takeManualScreenshot("coffee/bom-list");
    }

    @Test
    @DisplayName("Should display pastry BOMs")
    void shouldDisplayPastryBoms() {
        loginAsAdmin();
        navigateTo("/inventory/bom");
        waitForPageLoad();

        // Verify pastry BOMs using data-testid
        assertThat(page.locator("[data-testid='bom-product-BOM-CRS']")).containsText("Croissant");
        assertThat(page.locator("[data-testid='bom-product-BOM-RBC']")).containsText("Roti Bakar Coklat");
    }

    @Test
    @DisplayName("Should display BOM detail with components - Roti Bakar Coklat")
    void shouldDisplayBomDetailRotiCoklat() {
        loginAsAdmin();
        navigateTo("/inventory/bom");
        waitForPageLoad();

        // Click on Roti Bakar Coklat BOM using data-testid
        page.locator("[data-testid='bom-detail-link-BOM-RBC']").click();
        waitForPageLoad();

        // Verify BOM detail page shows components using data-testid
        assertThat(page.locator("[data-testid='component-name-TEPUNG-TERIGU']")).isVisible();
        assertThat(page.locator("[data-testid='component-name-BUTTER']")).isVisible();
        assertThat(page.locator("[data-testid='component-name-COKLAT']")).isVisible();
    }

    @Test
    @DisplayName("Should display BOM detail with components - Croissant")
    void shouldDisplayBomDetailCroissant() {
        loginAsAdmin();
        navigateTo("/inventory/bom");
        waitForPageLoad();

        // Click on Croissant BOM using data-testid
        page.locator("[data-testid='bom-detail-link-BOM-CRS']").click();
        waitForPageLoad();

        // Take screenshot for user manual
        takeManualScreenshot("coffee/bom-detail-croissant");

        // Verify BOM detail page shows components using data-testid
        assertThat(page.locator("[data-testid='component-name-TEPUNG-TERIGU']")).isVisible();
        assertThat(page.locator("[data-testid='component-name-BUTTER']")).isVisible();
        assertThat(page.locator("[data-testid='component-name-TELUR']")).isVisible();
    }

    @Test
    @DisplayName("Should show output quantity in BOM detail")
    void shouldShowOutputQuantityInBomDetail() {
        loginAsAdmin();
        navigateTo("/inventory/bom");
        waitForPageLoad();

        // Click on Croissant BOM using data-testid
        page.locator("[data-testid='bom-detail-link-BOM-CRS']").click();
        waitForPageLoad();

        // Verify output quantity is shown (24 for croissant batch) using data-testid
        assertThat(page.locator("[data-testid='bom-output-quantity']")).containsText("24");
    }
}
