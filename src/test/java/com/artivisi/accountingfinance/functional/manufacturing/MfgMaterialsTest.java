package com.artivisi.accountingfinance.functional.manufacturing;

import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Seeded coffee-shop product master (industry-seed/coffee-shop 35_product_categories.csv, 36_products.csv)
 * and raw material stock (41_inventory_balances.csv).
 */
@DisplayName("Manufacturing - Raw Materials")
@Import(CoffeeTestDataInitializer.class)
class MfgMaterialsTest extends PlaywrightTestBase {

    @Test
    @DisplayName("Product list shows the ten seeded raw materials with category, unit, zero selling price and status")
    void shouldDisplayCoffeeShopRawMaterials() {
        loginAsAdmin();
        navigateTo("/products");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Daftar Produk");
        takeManualScreenshot("coffee/product-list");

        assertProductRow("KOPI-ARABICA", "Biji Kopi Arabica", "Bahan Baku Kopi", "kg", "0");
        assertProductRow("SUSU-SEGAR", "Susu Segar", "Bahan Baku Kopi", "liter", "0");
        assertProductRow("GULA-AREN", "Gula Aren Cair", "Bahan Baku Kopi", "liter", "0");
        assertProductRow("ES-BATU", "Es Batu", "Bahan Baku Kopi", "kg", "0");
        assertProductRow("TEPUNG-TERIGU", "Tepung Terigu", "Bahan Baku Roti", "kg", "0");
        assertProductRow("BUTTER", "Butter", "Bahan Baku Roti", "kg", "0");
        assertProductRow("TELUR", "Telur Ayam", "Bahan Baku Roti", "butir", "0");
        assertProductRow("RAGI", "Ragi Instan", "Bahan Baku Roti", "gram", "0");
        assertProductRow("GARAM", "Garam", "Bahan Baku Roti", "gram", "0");
        assertProductRow("COKLAT", "Coklat Blok", "Bahan Baku Roti", "kg", "0");
    }

    @Test
    @DisplayName("Category list shows the seeded raw material and finished goods categories with their parents")
    void shouldDisplayRawMaterialCategories() {
        loginAsAdmin();
        navigateTo("/products/categories");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Kategori Produk");
        takeManualScreenshot("coffee/product-category-list");

        assertCategoryRow("CAT-BAHAN-KOPI", "Bahan Baku Kopi", "-");
        assertCategoryRow("CAT-BAHAN-ROTI", "Bahan Baku Roti", "-");
        assertCategoryRow("CAT-BARANG-JADI", "Barang Jadi", "-");
        assertCategoryRow("CAT-MINUMAN", "Minuman", "Barang Jadi");
        assertCategoryRow("CAT-ROTI", "Roti & Kue", "Barang Jadi");
    }

    @Test
    @DisplayName("Product list shows the seeded pastries with their selling prices")
    void shouldDisplayFinishedGoodsPastries() {
        loginAsAdmin();

        assertProductRow("CROISSANT", "Croissant", "Roti & Kue", "pcs", "25,000");
        assertProductRow("ROTI-COKLAT", "Roti Bakar Coklat", "Roti & Kue", "pcs", "20,000");
    }

    @Test
    @DisplayName("Stock list shows raw material balances and flags those below minimum stock")
    void shouldDisplayRawMaterialStockAfterPurchases() {
        loginAsAdmin();
        navigateTo("/inventory/stock");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Stok Barang");
        takeManualScreenshot("coffee/inventory-stock-list");

        assertStockRow("KOPI-ARABICA", "Biji Kopi Arabica", "Bahan Baku Kopi", "15.00 kg", "180,000.00", "2,700,000", "Normal");
        assertStockRow("SUSU-SEGAR", "Susu Segar", "Bahan Baku Kopi", "59.40 liter", "18,000.00", "1,069,200", "Normal");
        assertStockRow("TELUR", "Telur Ayam", "Bahan Baku Roti", "98.00 butir", "2,500.00", "245,000", "Stok Rendah");
        assertStockRow("RAGI", "Ragi Instan", "Bahan Baku Roti", "42.00 gram", "3,000.00", "126,000", "Stok Rendah");
        assertStockRow("GARAM", "Garam", "Bahan Baku Roti", "19.00 gram", "8,000.00", "152,000", "Stok Rendah");
        assertStockRow("COKLAT", "Coklat Blok", "Bahan Baku Roti", "9.60 kg", "85,000.00", "816,000", "Normal");
    }

    @Test
    @DisplayName("Product form offers the seeded categories and coffee-shop inventory, COGS and sales accounts")
    void shouldDisplayProductForm() {
        loginAsAdmin();
        navigateTo("/products/new");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Produk Baru");
        takeManualScreenshot("products-form");

        for (String option : new String[]{"CAT-BAHAN-KOPI - Bahan Baku Kopi", "CAT-BAHAN-ROTI - Bahan Baku Roti",
                "CAT-BARANG-JADI - Barang Jadi", "CAT-MINUMAN - Minuman", "CAT-ROTI - Roti & Kue"}) {
            assertThat(page.locator("#category option:text-is('" + option + "')")).hasCount(1);
        }
        assertThat(page.locator("#inventoryAccount option:text-is('1.1.20 - Persediaan Bahan Baku')")).hasCount(1);
        assertThat(page.locator("#inventoryAccount option:text-is('1.1.21 - Persediaan Barang Jadi')")).hasCount(1);
        assertThat(page.locator("#cogsAccount option:text-is('5.1.01 - HPP Kopi')")).hasCount(1);
        assertThat(page.locator("#cogsAccount option:text-is('5.2.01 - HPP Roti')")).hasCount(1);
        assertThat(page.locator("#salesAccount option:text-is('4.1.01 - Pendapatan Penjualan Minuman')")).hasCount(1);
        assertThat(page.locator("#salesAccount option:text-is('4.1.02 - Pendapatan Penjualan Roti & Kue')")).hasCount(1);
    }

    private void assertProductRow(String code, String name, String category, String unit, String price) {
        navigateTo("/products?search=" + code);
        waitForPageLoad();
        assertThat(page.locator("[data-testid='product-row-" + code + "'] td")).hasText(new String[]{
                code, name, category, unit, price, "Aktif", "Detail Edit"});
    }

    private void assertCategoryRow(String code, String name, String parent) {
        navigateTo("/products/categories?search=" + code);
        waitForPageLoad();
        assertThat(page.locator("[data-testid='category-name-" + code + "']")).hasText(name);
        assertThat(page.locator("[data-testid='category-parent-" + code + "']")).hasText(parent);
        assertThat(page.locator("[data-testid='category-status-" + code + "']")).hasText("Aktif");
    }

    private void assertStockRow(String code, String name, String category, String quantity,
                                String averageCost, String totalCost, String status) {
        navigateTo("/inventory/stock?search=" + code);
        waitForPageLoad();
        assertThat(page.locator("[data-testid='stock-row-" + code + "'] td")).hasText(new String[]{
                code, name, category, quantity, averageCost, totalCost, status, ""});
    }
}
