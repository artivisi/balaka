package com.artivisi.accountingfinance.functional.page;

import com.microsoft.playwright.Page;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Page Object for Product List (/products).
 * Handles product catalog display and navigation.
 */
public class ProductListPage {

    private final Page page;
    private final String baseUrl;

    // Locators - using IDs
    private static final String PAGE_TITLE = "#page-title";
    private static final String PRODUCT_TABLE = "#product-table";
    private static final String SEARCH_INPUT = "#search-input";
    private static final String BTN_NEW_PRODUCT = "#btn-new-product";
    private static final String CATEGORY_FILTER = "#category-filter";

    public ProductListPage(Page page, String baseUrl) {
        this.page = page;
        this.baseUrl = baseUrl;
    }

    public ProductListPage navigate() {
        page.navigate(baseUrl + "/products");
        page.waitForLoadState();
        return this;
    }

    public ProductListPage verifyPageTitle() {
        assertThat(page.locator(PAGE_TITLE)).containsText("Daftar Produk");
        return this;
    }

    public ProductListPage verifyTableVisible() {
        assertThat(page.locator(PRODUCT_TABLE)).isVisible();
        return this;
    }

    public ProductListPage verifyProductCount(int expectedCount) {
        assertThat(page.locator(PRODUCT_TABLE + " tbody tr")).hasCount(expectedCount);
        return this;
    }

    public ProductListPage verifyMinimumProductCount(int minCount) {
        int count = page.locator(PRODUCT_TABLE + " tbody tr").count();
        if (count < minCount) {
            throw new AssertionError("Expected at least " + minCount + " products, but found " + count);
        }
        return this;
    }

    /**
     * Verify product exists by code using ID-based row locator.
     */
    public ProductListPage verifyProductExists(String productCode) {
        assertThat(page.locator("[data-testid='product-row-" + productCode + "']")).isVisible();
        return this;
    }

    public ProductListPage search(String query) {
        page.locator(SEARCH_INPUT).fill(query);
        page.waitForTimeout(500); // Wait for search debounce
        return this;
    }

    public ProductListPage clickProduct(String productCode) {
        page.locator("[data-testid='product-row-" + productCode + "']").click();
        page.waitForLoadState();
        return this;
    }

    public ProductListPage takeScreenshot(String path) {
        page.screenshot(new Page.ScreenshotOptions().setPath(java.nio.file.Paths.get(path)));
        return this;
    }
}
