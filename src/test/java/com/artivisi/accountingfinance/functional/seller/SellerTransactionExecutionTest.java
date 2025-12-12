package com.artivisi.accountingfinance.functional.seller;

import com.artivisi.accountingfinance.entity.CostingMethod;
import com.artivisi.accountingfinance.entity.Product;
import com.artivisi.accountingfinance.entity.ProductCategory;
import com.artivisi.accountingfinance.functional.util.CsvLoader;
import com.artivisi.accountingfinance.functional.util.ExpectedInventoryRow;
import com.artivisi.accountingfinance.functional.util.InventoryTransactionRow;
import com.artivisi.accountingfinance.repository.ProductCategoryRepository;
import com.artivisi.accountingfinance.repository.ProductRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CSV-Driven Inventory Transaction Tests for Online Seller.
 *
 * This test suite:
 * 1. Reads inventory transaction scenarios from CSV file
 * 2. EXECUTES each transaction via the UI (testing transaction UI)
 * 3. Takes screenshots for user manual
 * 4. Verifies resulting inventory levels match expected-inventory.csv
 * 5. Verifies inventory reports
 *
 * Data from: src/test/resources/testdata/seller/
 */
@DisplayName("Online Seller - Transaction Execution")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Import(SellerTestDataInitializer.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SellerTransactionExecutionTest extends PlaywrightTestBase {

    @Autowired
    private ProductCategoryRepository categoryRepository;
    
    @Autowired
    private ProductRepository productRepository;

    @BeforeAll
    public void setupProductsAndCategories() {
        // Create categories (check if exists first to avoid duplicates)
        ProductCategory smartphone = categoryRepository.findByCode("PHONE")
            .orElseGet(() -> {
                ProductCategory cat = new ProductCategory();
                cat.setCode("PHONE");
                cat.setName("Smartphone");
                cat.setActive(true);
                return categoryRepository.save(cat);
            });

        ProductCategory accessories = categoryRepository.findByCode("ACC")
            .orElseGet(() -> {
                ProductCategory cat = new ProductCategory();
                cat.setCode("ACC");
                cat.setName("Accessories");
                cat.setActive(true);
                return categoryRepository.save(cat);
            });

        // Create products matching CSV transaction codes (check if exists first)
        if (productRepository.findByCode("IP15PRO").isEmpty()) {
            Product iphone = new Product();
            iphone.setCode("IP15PRO");
            iphone.setName("iPhone 15 Pro");
            iphone.setDescription("Apple iPhone 15 Pro 256GB");
            iphone.setUnit("pcs");
            iphone.setCostingMethod(CostingMethod.FIFO);
            iphone.setCategory(smartphone);
            iphone.setSellingPrice(BigDecimal.valueOf(19000000));
            iphone.setTrackInventory(true);
            iphone.setActive(true);
            productRepository.save(iphone);
        }

        if (productRepository.findByCode("SGS24").isEmpty()) {
            Product samsung = new Product();
            samsung.setCode("SGS24");
            samsung.setName("Samsung Galaxy S24");
            samsung.setDescription("Samsung Galaxy S24 Ultra 512GB");
            samsung.setUnit("pcs");
            samsung.setCostingMethod(CostingMethod.FIFO);
            samsung.setCategory(smartphone);
            samsung.setSellingPrice(BigDecimal.valueOf(14000000));
            samsung.setTrackInventory(true);
            samsung.setActive(true);
            productRepository.save(samsung);
        }

        if (productRepository.findByCode("USBC").isEmpty()) {
            Product usbCable = new Product();
            usbCable.setCode("USBC");
            usbCable.setName("USB Cable Type-C 1M");
            usbCable.setDescription("USB Type-C cable 1 meter");
            usbCable.setUnit("pcs");
            usbCable.setCostingMethod(CostingMethod.WEIGHTED_AVERAGE);
            usbCable.setCategory(accessories);
            usbCable.setSellingPrice(BigDecimal.valueOf(50000));
            usbCable.setTrackInventory(true);
            usbCable.setActive(true);
            productRepository.save(usbCable);
        }

        if (productRepository.findByCode("CASE").isEmpty()) {
            Product phoneCase = new Product();
            phoneCase.setCode("CASE");
            phoneCase.setName("Phone Case Universal");
            phoneCase.setDescription("Universal phone protective case");
            phoneCase.setUnit("pcs");
            phoneCase.setCostingMethod(CostingMethod.WEIGHTED_AVERAGE);
            phoneCase.setCategory(accessories);
            phoneCase.setSellingPrice(BigDecimal.valueOf(35000));
            phoneCase.setTrackInventory(true);
            phoneCase.setActive(true);
            productRepository.save(phoneCase);
        }
    }

    @Test
    @Order(1)
    @DisplayName("Should load inventory transactions CSV")
    void shouldLoadInventoryTransactionsCsv() {
        List<InventoryTransactionRow> transactions = CsvLoader.loadInventoryTransactions("seller/transactions.csv");

        // Verify CSV loads correctly
        assertEquals(9, transactions.size());
        assertEquals("PURCHASE", transactions.get(0).transactionType());
        assertEquals("IP15PRO", transactions.get(0).productCode());
    }

    @Test
    @Order(2)
    @DisplayName("Should load expected inventory CSV")
    void shouldLoadExpectedInventoryCsv() {
        List<ExpectedInventoryRow> inventory = CsvLoader.loadExpectedInventory("seller/expected-inventory.csv");

        // Verify CSV loads correctly
        assertEquals(4, inventory.size());
        assertTrue(inventory.stream().anyMatch(r -> r.productCode().equals("IP15PRO")));
    }

    /**
     * Verify products exist from @BeforeAll setup.
     */
    @Test
    @Order(3)
    @DisplayName("Verify 4 products exist")
    void verifyProductsExist() {
        loginAsAdmin();
        navigateTo("/products");
        waitForPageLoad();

        // Take screenshot for user manual
        takeManualScreenshot("seller/product-list");

        // Verify page title using ID
        assertThat(page.locator("#page-title")).containsText("Produk");

        // Verify product count (4 products from @BeforeAll)
        assertThat(page.locator("#product-table tbody tr")).hasCount(4);

        // Verify specific products exist by code using data-testid
        assertThat(page.locator("[data-testid='product-row-IP15PRO']")).isVisible();
        assertThat(page.locator("[data-testid='product-row-SGS24']")).isVisible();
    }

    /**
     * Execute inventory transactions from CSV via UI.
     * This tests the inventory transaction UI by executing each transaction.
     */
    @TestFactory
    @Order(4)
    @DisplayName("Execute inventory transactions from CSV")
    Stream<DynamicTest> executeInventoryTransactionsFromCsv() {
        List<InventoryTransactionRow> transactions = CsvLoader.loadInventoryTransactions("seller/transactions.csv");

        return transactions.stream()
            .map(tx -> DynamicTest.dynamicTest(
                "Tx " + tx.sequence() + ": " + tx.transactionType() + " " + tx.productCode(),
                () -> executeInventoryTransaction(tx)
            ));
    }

    /**
     * Execute a single inventory transaction via UI.
     * Each transaction type has its own form URL.
     */
    private void executeInventoryTransaction(InventoryTransactionRow tx) {
        loginAsAdmin();

        // Find product by code to get UUID
        Product product = productRepository.findByCode(tx.productCode())
            .orElseThrow(() -> new RuntimeException("Product not found: " + tx.productCode()));

        // Navigate to correct form based on transaction type
        String formUrl = switch (tx.transactionType()) {
            case "PURCHASE" -> "/inventory/purchase";
            case "SALE" -> "/inventory/sale";
            case "ADJUSTMENT_IN", "ADJUSTMENT_OUT" -> "/inventory/adjustment";
            default -> throw new IllegalArgumentException("Unknown transaction type: " + tx.transactionType());
        };
        
        navigateTo(formUrl);
        waitForPageLoad();

        // For ADJUSTMENT, select type (inbound=true for ADJUSTMENT_IN)
        if (tx.transactionType().equals("ADJUSTMENT_IN") || tx.transactionType().equals("ADJUSTMENT_OUT")) {
            boolean inbound = tx.transactionType().equals("ADJUSTMENT_IN");
            page.locator("input[name='inbound'][value='" + inbound + "']").check();
        }

        // Select product by UUID (dropdown uses product ID)
        page.locator("#productId").selectOption(product.getId().toString());

        // Enter transaction date
        page.locator("#transactionDate").fill(tx.date());

        // Enter quantity
        page.locator("#quantity").fill(String.valueOf(tx.quantity()));

        // Enter unit cost for PURCHASE and ADJUSTMENT_IN
        if (tx.transactionType().equals("PURCHASE") || tx.transactionType().equals("ADJUSTMENT_IN")) {
            page.locator("#unitCost").fill(String.valueOf(tx.unitCost()));
        }

        // Enter unit price for SALE
        if (tx.transactionType().equals("SALE")) {
            page.locator("#unitPrice").fill(String.valueOf(tx.unitPrice()));
        }

        // Enter reference number
        if (tx.reference() != null && !tx.reference().isEmpty()) {
            page.locator("#referenceNumber").fill(tx.reference());
        }

        // Enter notes
        if (tx.notes() != null && !tx.notes().isEmpty()) {
            page.locator("#notes").fill(tx.notes());
        }

        // Take screenshot before submit if requested
        if (tx.screenshot()) {
            takeManualScreenshot("seller/inv-" + String.format("%02d", tx.sequence()) + "-" + 
                tx.transactionType().toLowerCase() + "-form");
        }

        // Click submit button using ID
        page.locator("#btn-submit").click();

        // Wait for success redirect to transaction detail page
        page.waitForURL("**/inventory/transactions/*");
        waitForPageLoad();

        // Take screenshot of transaction list if requested
        if (tx.screenshot()) {
            takeManualScreenshot("seller/inv-" + String.format("%02d", tx.sequence()) + "-" + 
                tx.transactionType().toLowerCase() + "-result");
        }

        // Verify we're back on transaction list page
        assertThat(page.locator("#page-title")).containsText("Transaksi Persediaan");
    }

    /**
     * Verify inventory levels match expected values from CSV.
     * This runs after all transactions have been executed.
     */
    @TestFactory
    @Order(5)
    @DisplayName("Verify expected inventory levels")
    Stream<DynamicTest> verifyExpectedInventoryLevels() {
        List<ExpectedInventoryRow> expectedInventory = CsvLoader.loadExpectedInventory("seller/expected-inventory.csv");

        return expectedInventory.stream()
            .map(item -> DynamicTest.dynamicTest(
                "Stock: " + item.productName(),
                () -> verifyInventoryLevel(item)
            ));
    }

    private void verifyInventoryLevel(ExpectedInventoryRow expected) {
        loginAsAdmin();
        navigateTo("/inventory/stock");
        waitForPageLoad();

        // Verify page title using ID
        assertThat(page.locator("#page-title")).containsText("Stok");

        // Verify product stock exists and quantity matches using data-testid
        assertThat(page.locator("[data-testid='stock-row-" + expected.productCode() + "']")).isVisible();

        // Verify expected quantity
        assertThat(page.locator("[data-testid='stock-quantity-" + expected.productCode() + "']"))
            .containsText(String.valueOf(expected.expectedQuantity().intValue()));
    }

    /**
     * Verify Stock Balance Report.
     */
    @Test
    @Order(6)
    @DisplayName("Verify Stock Balance Report")
    void verifyStockBalanceReport() {
        loginAsAdmin();
        navigateTo("/inventory/reports/stock-balance");
        waitForPageLoad();

        // Take screenshot for user manual
        takeManualScreenshot("seller/report-stock-balance");

        // Verify report loads
        assertThat(page.locator("#page-title")).containsText("Saldo Stok");
    }

    /**
     * Verify Stock Movement Report.
     */
    @Test
    @Order(7)
    @DisplayName("Verify Stock Movement Report")
    void verifyStockMovementReport() {
        loginAsAdmin();
        navigateTo("/inventory/reports/stock-movement");
        waitForPageLoad();

        // Take screenshot for user manual
        takeManualScreenshot("seller/report-stock-movement");

        // Verify report loads
        assertThat(page.locator("#page-title")).containsText("Mutasi Stok");
    }

    /**
     * Verify Inventory Valuation Report.
     */
    @Test
    @Order(8)
    @DisplayName("Verify Inventory Valuation Report")
    void verifyInventoryValuationReport() {
        loginAsAdmin();
        navigateTo("/inventory/reports/valuation");
        waitForPageLoad();

        // Take screenshot for user manual
        takeManualScreenshot("seller/report-inventory-valuation");

        // Verify report loads
        assertThat(page.locator("#page-title")).containsText("Penilaian Persediaan");
    }

    /**
     * Verify Product Profitability Report.
     */
    @Test
    @Order(9)
    @DisplayName("Verify Product Profitability Report")
    void verifyProductProfitabilityReport() {
        loginAsAdmin();
        navigateTo("/inventory/reports/profitability");
        waitForPageLoad();

        // Take screenshot for user manual
        takeManualScreenshot("seller/report-product-profitability");

        // Verify report loads
        assertThat(page.locator("#page-title")).containsText("Profitabilitas Produk");
    }
}
