package com.artivisi.accountingfinance.functional.seller;

import com.artivisi.accountingfinance.functional.page.InventoryStockPage;
import com.artivisi.accountingfinance.functional.page.InventoryTransactionListPage;
import com.artivisi.accountingfinance.functional.page.ProductCategoryListPage;
import com.artivisi.accountingfinance.functional.page.ProductListPage;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Online Seller Inventory Tests
 * Tests product catalog, categories, and inventory management.
 * Uses Page Object Pattern with ID-based locators.
 *
 * Test Data (from @BeforeAll):
 * - 2 Categories: PHONE (Smartphone), ACC (Accessories)
 * - 4 Products: IP15PRO, SGS24, USBC, CASE
 */
@DisplayName("Online Seller - Inventory")
@Import(SellerTestDataInitializer.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SellerInventoryTest extends PlaywrightTestBase {

    @Autowired
    private SellerTestDataHelper testDataHelper;

    // Page Objects
    private ProductListPage productListPage;
    private ProductCategoryListPage categoryListPage;
    private InventoryStockPage stockPage;
    private InventoryTransactionListPage transactionListPage;

    private void initPageObjects() {
        String baseUrl = "http://localhost:" + port;
        productListPage = new ProductListPage(page, baseUrl);
        categoryListPage = new ProductCategoryListPage(page, baseUrl);
        stockPage = new InventoryStockPage(page, baseUrl);
        transactionListPage = new InventoryTransactionListPage(page, baseUrl);
    }

    @BeforeAll
    void setupProductsAndCategories() {
        // Use shared helper to create products and categories
        testDataHelper.setupProductsAndCategories();
    }

    @Test
    @DisplayName("Should display product list with 4 products")
    void shouldDisplayProductList() {
        loginAsAdmin();
        initPageObjects();

        // Products from seed data (18) + @BeforeAll helper (4)
        productListPage.navigate()
            .verifyPageTitle()
            .verifyTableVisible()
            .verifyMinimumProductCount(4);
    }

    @Test
    @DisplayName("Should display product detail")
    void shouldDisplayProductDetail() {
        loginAsAdmin();
        initPageObjects();

        productListPage.navigate()
            .verifyPageTitle()
            .clickProduct("IP15PRO");

        // Verify product detail page loads
        assertThat(page.locator("#page-title")).containsText("Produk");
    }

    @Test
    @DisplayName("Should display product categories with 2 categories")
    void shouldDisplayProductCategories() {
        loginAsAdmin();
        initPageObjects();

        // 2 categories from @BeforeAll: PHONE, ACC
        categoryListPage.navigate()
            .verifyPageTitle()
            .verifyTableVisible()
            .verifyMinimumCategoryCount(2);
    }

    @Test
    @DisplayName("Should display inventory transactions")
    void shouldDisplayInventoryTransactions() {
        loginAsAdmin();
        initPageObjects();

        transactionListPage.navigate()
            .verifyPageTitle()
            .verifyTableVisible();
    }

    @Test
    @DisplayName("Should display stock balances page")
    void shouldDisplayStockBalances() {
        loginAsAdmin();
        initPageObjects();

        // Stock page loads (products may have 0 stock initially)
        stockPage.navigate()
            .verifyPageTitle()
            .verifyTableVisible();
    }
}
