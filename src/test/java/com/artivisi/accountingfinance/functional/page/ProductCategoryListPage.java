package com.artivisi.accountingfinance.functional.page;

import com.microsoft.playwright.Page;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Page Object for Product Category List (/products/categories).
 * Handles product category display and navigation.
 */
public class ProductCategoryListPage {

    private final Page page;
    private final String baseUrl;

    // Locators - using IDs
    private static final String PAGE_TITLE = "#page-title";
    private static final String CATEGORY_TABLE = "#category-table";
    private static final String BTN_NEW_CATEGORY = "#btn-new-category";

    public ProductCategoryListPage(Page page, String baseUrl) {
        this.page = page;
        this.baseUrl = baseUrl;
    }

    public ProductCategoryListPage navigate() {
        page.navigate(baseUrl + "/products/categories");
        page.waitForLoadState();
        return this;
    }

    public ProductCategoryListPage verifyPageTitle() {
        assertThat(page.locator(PAGE_TITLE)).containsText("Kategori");
        return this;
    }

    public ProductCategoryListPage verifyTableVisible() {
        assertThat(page.locator(CATEGORY_TABLE)).isVisible();
        return this;
    }

    public ProductCategoryListPage verifyCategoryCount(int expectedCount) {
        assertThat(page.locator(CATEGORY_TABLE + " tbody tr")).hasCount(expectedCount);
        return this;
    }

    /**
     * Verify category exists by code.
     */
    public ProductCategoryListPage verifyCategoryExists(String categoryCode) {
        assertThat(page.locator("[data-testid='category-row-" + categoryCode + "']")).isVisible();
        return this;
    }

    public ProductCategoryListPage takeScreenshot(String path) {
        page.screenshot(new Page.ScreenshotOptions().setPath(java.nio.file.Paths.get(path)));
        return this;
    }
}
