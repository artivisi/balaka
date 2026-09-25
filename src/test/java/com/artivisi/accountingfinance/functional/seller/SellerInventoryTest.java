package com.artivisi.accountingfinance.functional.seller;

import com.artivisi.accountingfinance.entity.CostingMethod;
import com.artivisi.accountingfinance.entity.Product;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Online seller inventory: the helper's product master data (categories PHONE/ACC, product IP15PRO) on the product
 * pages, and a weighted-average product of this run bought twice and partly written off in {@link #YEAR} (no other
 * class in the shared seller context uses that year) on the inventory transaction list and the stock list.
 */
@DisplayName("Online Seller - Inventory")
@Import(SellerTestDataInitializer.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SellerInventoryTest extends SellerTestBase {

    private static final int YEAR = 2079;

    @BeforeAll
    void setupProductsAndCategories() {
        testDataHelper.setupProductsAndCategories();
    }

    @Test
    @DisplayName("Should open a product from the list and show its category, unit, costing method, price and GL accounts")
    void shouldShowProductDetail() {
        loginAsAdmin();
        navigateTo("/products?search=IP15PRO");
        waitForPageLoad();
        page.getByTestId("product-code-IP15PRO").click();
        page.waitForURL("**/products/" + productRepository.findByCode("IP15PRO")
                .orElseThrow(() -> new IllegalStateException("Helper product missing: IP15PRO")).getId());
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Detail Produk");
        assertThat(page.getByTestId("product-detail-heading")).hasText("iPhone 15 Pro");
        assertThat(page.getByTestId("product-detail-status")).hasText("Aktif");
        assertThat(page.getByTestId("product-detail-code")).hasText("IP15PRO");
        assertThat(page.getByTestId("product-detail-unit")).hasText("pcs");
        assertThat(page.getByTestId("product-detail-category")).hasText("Smartphone");
        assertThat(page.getByTestId("product-detail-description")).hasText("Apple iPhone 15 Pro 256GB");
        assertThat(page.getByTestId("product-detail-costing-method")).hasText("FIFO (First In, First Out)");
        assertThat(page.getByTestId("product-detail-selling-price")).hasText("Rp 19,000,000");
        assertThat(page.getByTestId("product-detail-inventory-account")).hasText("1.1.20 - Persediaan Barang Dagangan");
        assertThat(page.getByTestId("product-detail-cogs-account")).hasText("5.1.01 - HPP - Harga Pokok Penjualan");
        assertThat(page.getByTestId("product-detail-sales-account")).hasText("4.1.01 - Penjualan Tokopedia");

        takeManualScreenshot("seller/product-detail");
    }

    @Test
    @DisplayName("Should list the Smartphone and Accessories categories as active top-level categories")
    void shouldListProductCategories() {
        loginAsAdmin();
        for (String[] category : new String[][]{{"PHONE", "Smartphone"}, {"ACC", "Accessories"}}) {
            navigateTo("/products/categories?search=" + category[0]);
            waitForPageLoad();
            assertThat(page.getByTestId("category-code-" + category[0])).hasText(category[0]);
            assertThat(page.getByTestId("category-name-" + category[0])).hasText(category[1]);
            assertThat(page.getByTestId("category-parent-" + category[0])).hasText("-");
            assertThat(page.getByTestId("category-status-" + category[0])).hasText("Aktif");
        }
        takeManualScreenshot("seller/product-categories");
    }

    @Test
    @DisplayName("Should list two purchases and a write-off at the running weighted average, and show the remaining stock")
    void shouldTrackWeightedAverageStock() {
        String code = "INV-" + runId;
        String name = "Inventory WA " + runId;
        Product product = createProduct(code, name, CostingMethod.WEIGHTED_AVERAGE, "ACC", 40_000);
        loginAsAdmin();

        UUID first = recordPurchase(product, LocalDate.of(YEAR, 3, 1), 10, 20_000, "PO-" + code + "-1");
        UUID second = recordPurchase(product, LocalDate.of(YEAR, 3, 5), 10, 26_000, "PO-" + code + "-2");
        UUID writeOff = recordAdjustmentOut(product, LocalDate.of(YEAR, 3, 10), 4, "ADJ-" + code, "Barang rusak");

        navigateTo("/inventory/transactions?productId=" + product.getId());
        waitForPageLoad();
        takeManualScreenshot("seller/inventory-transactions");
        assertThat(page.locator("[data-testid^='transaction-row-']")).hasCount(3);
        assertMovement(first, "01/03/" + YEAR, "Pembelian", code + " - " + name, "+10.00 pcs", "20,000.00", "200,000", "PO-" + code + "-1");
        assertMovement(second, "05/03/" + YEAR, "Pembelian", code + " - " + name, "+10.00 pcs", "26,000.00", "260,000", "PO-" + code + "-2");
        assertMovement(writeOff, "10/03/" + YEAR, "Penyesuaian Keluar", code + " - " + name, "-4.00 pcs", "23,000.00", "92,000", "ADJ-" + code);

        navigateTo("/inventory/stock?search=" + code);
        waitForPageLoad();
        takeManualScreenshot("seller/inventory-stock");
        Locator stockRow = page.getByTestId("stock-row-" + code);
        assertThat(page.locator("[data-testid^='stock-row-']")).hasCount(1);
        assertThat(page.getByTestId("stock-product-name-" + code)).hasText(name);
        assertThat(stockRow.locator("td").nth(2)).hasText("Accessories");
        assertThat(page.getByTestId("stock-quantity-" + code)).hasText("16.00");
        assertThat(stockRow.locator("td").nth(4)).hasText("23,000.00");
        assertThat(stockRow.locator("td").nth(5)).hasText("368,000");
    }

    private void assertMovement(UUID id, String... cells) {
        Locator tds = page.getByTestId("transaction-row-" + id).locator("td");
        for (int i = 0; i < cells.length; i++) {
            assertThat(tds.nth(i)).hasText(cells[i]);
        }
    }
}
