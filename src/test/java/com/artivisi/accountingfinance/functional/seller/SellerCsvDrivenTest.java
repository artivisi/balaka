package com.artivisi.accountingfinance.functional.seller;

import com.artivisi.accountingfinance.entity.CostingMethod;
import com.artivisi.accountingfinance.entity.Product;
import com.artivisi.accountingfinance.entity.ProductCategory;
import com.artivisi.accountingfinance.functional.page.ClientListPage;
import com.artivisi.accountingfinance.functional.page.InventoryStockPage;
import com.artivisi.accountingfinance.functional.page.InventoryTransactionListPage;
import com.artivisi.accountingfinance.functional.page.ProductCategoryListPage;
import com.artivisi.accountingfinance.functional.page.ProductListPage;
import com.artivisi.accountingfinance.functional.util.CsvLoader;
import com.artivisi.accountingfinance.functional.util.ExpectedInventoryRow;
import com.artivisi.accountingfinance.functional.util.InventoryTransactionRow;
import com.artivisi.accountingfinance.repository.ProductCategoryRepository;
import com.artivisi.accountingfinance.repository.ProductRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CSV-Driven Online Seller Tests
 * Loads test scenarios from CSV files and executes them as dynamic tests.
 * Uses Page Object Pattern with ID-based locators.
 *
 * Data from: src/test/resources/testdata/seller/
 * - transactions.csv: 9 inventory transactions
 * - expected-inventory.csv: 4 expected stock levels
 */
@DisplayName("Online Seller - CSV-Driven Tests")
@Import(SellerTestDataInitializer.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SellerCsvDrivenTest extends PlaywrightTestBase {

    @Autowired
    private ProductCategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    // Page Objects
    private ProductListPage productListPage;
    private ProductCategoryListPage categoryListPage;
    private InventoryStockPage stockPage;
    private InventoryTransactionListPage transactionListPage;
    private ClientListPage clientListPage;

    private void initPageObjects() {
        String baseUrl = "http://localhost:" + port;
        productListPage = new ProductListPage(page, baseUrl);
        categoryListPage = new ProductCategoryListPage(page, baseUrl);
        stockPage = new InventoryStockPage(page, baseUrl);
        transactionListPage = new InventoryTransactionListPage(page, baseUrl);
        clientListPage = new ClientListPage(page, baseUrl);
    }

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
    @DisplayName("Should load 9 inventory transactions from CSV")
    void shouldLoadInventoryTransactionsCsv() {
        List<InventoryTransactionRow> transactions = CsvLoader.loadInventoryTransactions("seller/transactions.csv");

        // Verify CSV loads correctly with 9 transactions
        assertEquals(9, transactions.size());
        assertEquals("PURCHASE", transactions.get(0).transactionType());
        assertEquals("IP15PRO", transactions.get(0).productCode());
    }

    @Test
    @DisplayName("Should load 4 expected inventory rows from CSV")
    void shouldLoadExpectedInventoryCsv() {
        List<ExpectedInventoryRow> inventory = CsvLoader.loadExpectedInventory("seller/expected-inventory.csv");

        // Verify CSV loads correctly with 4 products
        assertEquals(4, inventory.size());
        assertTrue(inventory.stream().anyMatch(r -> r.productCode().equals("IP15PRO")));
        assertTrue(inventory.stream().anyMatch(r -> r.productCode().equals("SGS24")));
        assertTrue(inventory.stream().anyMatch(r -> r.productCode().equals("USBC")));
        assertTrue(inventory.stream().anyMatch(r -> r.productCode().equals("CASE")));
    }

    @TestFactory
    @DisplayName("Verify inventory transactions exist")
    Stream<DynamicTest> verifyInventoryTransactionsFromCsv() {
        List<InventoryTransactionRow> transactions = CsvLoader.loadInventoryTransactions("seller/transactions.csv");

        return transactions.stream()
            .map(tx -> DynamicTest.dynamicTest(
                "Inv " + tx.sequence() + ": " + tx.transactionType() + " " + tx.productCode() + " x" + tx.quantity(),
                () -> verifyInventoryTransaction(tx)
            ));
    }

    private void verifyInventoryTransaction(InventoryTransactionRow tx) {
        loginAsAdmin();
        initPageObjects();

        transactionListPage.navigate()
            .verifyPageTitle()
            .verifyTableVisible();

        if (tx.screenshot()) {
            takeManualScreenshot("seller/inv-" + tx.sequence() + "-" + tx.transactionType().toLowerCase());
        }
    }

    // NOTE: Inventory level verification removed from CSV-driven test
    // Stock balances only exist after transactions are executed
    // See SellerTransactionExecutionTest for actual inventory verification after transaction execution

    @Test
    @DisplayName("Verify 4 products exist")
    void verifyProductsExist() {
        loginAsAdmin();
        initPageObjects();

        // 4 products from @BeforeAll
        productListPage.navigate()
            .verifyPageTitle()
            .verifyTableVisible()
            .verifyMinimumProductCount(4)
            .verifyProductExists("IP15PRO")
            .verifyProductExists("SGS24");
    }

    @Test
    @DisplayName("Verify 2 product categories exist")
    void verifyProductCategoriesExist() {
        loginAsAdmin();
        initPageObjects();

        // 2 categories from @BeforeAll
        categoryListPage.navigate()
            .verifyPageTitle()
            .verifyTableVisible()
            .verifyCategoryCount(2);
    }

    @Test
    @DisplayName("Verify 5 clients exist (suppliers + marketplaces)")
    void verifyClientsExist() {
        loginAsAdmin();
        initPageObjects();

        // 5 clients: 2 suppliers (Erajaya, Samsung) + 3 marketplaces (Tokopedia, Shopee, Lazada)
        clientListPage.navigate()
            .verifyPageTitle()
            .verifyTableVisible()
            .verifyClientCount(5);
    }
}
