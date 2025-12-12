package com.artivisi.accountingfinance.functional.manufacturing;

import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Manufacturing Materials Tests
 * Tests raw material management for coffee shop.
 * Loads coffee shop seed data via CoffeeTestDataInitializer.
 */
@DisplayName("Manufacturing - Raw Materials")
@Import(CoffeeTestDataInitializer.class)
public class MfgMaterialsTest extends PlaywrightTestBase {

    @Test
    @DisplayName("Should display coffee shop raw materials")
    void shouldDisplayCoffeeShopRawMaterials() {
        loginAsAdmin();
        navigateTo("/products");
        waitForPageLoad();

        // Verify products page loads
        assertThat(page.locator("h1")).containsText("Produk");

        // Take screenshot for user manual
        takeManualScreenshot("coffee/product-list");

        // Verify coffee raw materials using data-testid
        assertThat(page.locator("[data-testid='product-name-KOPI-ARABICA']")).containsText("Biji Kopi Arabica");
        assertThat(page.locator("[data-testid='product-name-SUSU-SEGAR']")).containsText("Susu Segar");
    }

    @Test
    @DisplayName("Should display raw material categories")
    void shouldDisplayRawMaterialCategories() {
        loginAsAdmin();
        navigateTo("/products/categories");
        waitForPageLoad();

        // Verify categories page loads
        assertThat(page.locator("h1")).containsText("Kategori");

        // Take screenshot for user manual
        takeManualScreenshot("coffee/product-category-list");

        // Verify coffee shop categories using data-testid
        assertThat(page.locator("[data-testid='category-name-CAT-BAHAN-KOPI']")).containsText("Bahan Baku Kopi");
        assertThat(page.locator("[data-testid='category-name-CAT-BAHAN-ROTI']")).containsText("Bahan Baku Roti");
    }

    @Test
    @DisplayName("Should display all coffee shop raw materials")
    void shouldDisplayAllRawMaterials() {
        loginAsAdmin();
        navigateTo("/products");
        waitForPageLoad();

        // Verify all raw material categories are visible
        // Coffee raw materials
        assertThat(page.locator("[data-testid='product-name-KOPI-ARABICA']")).containsText("Biji Kopi Arabica");
        assertThat(page.locator("[data-testid='product-name-SUSU-SEGAR']")).containsText("Susu Segar");
        // Pastry raw materials
        assertThat(page.locator("[data-testid='product-name-TEPUNG-TERIGU']")).containsText("Tepung Terigu");
        assertThat(page.locator("[data-testid='product-name-BUTTER']")).containsText("Butter");
    }

    @Test
    @DisplayName("Should display finished goods - pastries")
    void shouldDisplayFinishedGoodsPastries() {
        loginAsAdmin();
        navigateTo("/products");
        waitForPageLoad();

        // Verify finished goods pastries using data-testid
        assertThat(page.locator("[data-testid='product-name-CROISSANT']")).containsText("Croissant");
        assertThat(page.locator("[data-testid='product-name-ROTI-COKLAT']")).containsText("Roti Bakar Coklat");
    }

    @Test
    @DisplayName("Should display raw material stock after purchases")
    void shouldDisplayRawMaterialStockAfterPurchases() {
        loginAsAdmin();
        navigateTo("/inventory/stock");
        waitForPageLoad();

        // Verify stock page loads
        assertThat(page.locator("h1")).containsText("Stok Barang");

        // Take screenshot for user manual
        takeManualScreenshot("coffee/inventory-stock-list");

        // Verify raw materials have stock from purchases using data-testid
        assertThat(page.locator("[data-testid='stock-product-name-KOPI-ARABICA']")).containsText("Biji Kopi Arabica");
    }
}
