package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.entity.InventoryBalance;
import com.artivisi.accountingfinance.entity.InventoryTransaction;
import com.artivisi.accountingfinance.entity.Product;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for InventoryService.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("InventoryService Integration Tests")
class InventoryServiceTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ChartOfAccountRepository accountRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Test product IDs from V911__inventory_report_test_data.sql
    private static final UUID TEST_PRODUCT_ID = UUID.fromString("d0911002-0000-0000-0000-000000000001");

    private Product testProduct;

    @BeforeEach
    void setup() {
        testProduct = productRepository.findById(TEST_PRODUCT_ID).orElse(null);
    }

    @Nested
    @DisplayName("Balance Operations")
    class BalanceOperationsTests {

        @Test
        @DisplayName("Should find balance by product ID - empty for non-existent")
        void shouldFindBalanceByProductIdEmptyForNonExistent() {
            UUID randomId = UUID.randomUUID();
            Optional<InventoryBalance> balance = inventoryService.findBalanceByProductId(randomId);
            assertThat(balance).isEmpty();
        }

        @Test
        @DisplayName("Should find balances with search")
        void shouldFindBalancesWithSearch() {
            Page<InventoryBalance> balances = inventoryService.findBalances("test", null, PageRequest.of(0, 10));
            assertThat(balances).isNotNull();
        }

        @Test
        @DisplayName("Should find balances without search")
        void shouldFindBalancesWithoutSearch() {
            Page<InventoryBalance> balances = inventoryService.findBalances(null, null, PageRequest.of(0, 10));
            assertThat(balances).isNotNull();
        }

        @Test
        @DisplayName("Should find low stock products")
        void shouldFindLowStockProducts() {
            List<InventoryBalance> lowStock = inventoryService.findLowStockProducts();
            assertThat(lowStock).isNotNull();
        }

        @Test
        @DisplayName("Should get current stock")
        void shouldGetCurrentStock() {
            if (testProduct == null) return;
            BigDecimal stock = inventoryService.getCurrentStock(testProduct.getId());
            assertThat(stock).isNotNull();
        }

        @Test
        @DisplayName("Should get current average cost")
        void shouldGetCurrentAverageCost() {
            if (testProduct == null) return;
            BigDecimal cost = inventoryService.getCurrentAverageCost(testProduct.getId());
            assertThat(cost).isNotNull();
        }
    }

    @Nested
    @DisplayName("Transaction History Operations")
    class TransactionHistoryTests {

        @Test
        @DisplayName("Should find transactions by product ID - empty for random ID")
        void shouldFindTransactionsByProductIdEmptyForRandomId() {
            UUID randomId = UUID.randomUUID();
            List<InventoryTransaction> transactions = inventoryService.findByProductId(randomId);
            assertThat(transactions).isEmpty();
        }

        @Test
        @DisplayName("Should find transaction by ID - empty for random ID")
        void shouldFindTransactionByIdEmptyForRandomId() {
            UUID randomId = UUID.randomUUID();
            Optional<InventoryTransaction> transaction = inventoryService.findById(randomId);
            assertThat(transaction).isEmpty();
        }

        @Test
        @DisplayName("Should find transactions with pagination")
        void shouldFindTransactionsWithPagination() {
            if (testProduct == null) return;

            Page<InventoryTransaction> transactions = inventoryService.findTransactions(
                    testProduct.getId(), null, null, null, PageRequest.of(0, 10));

            assertThat(transactions).isNotNull();
        }
    }

    @Nested
    @DisplayName("Purchase Operations")
    class PurchaseOperationsTests {

        @Test
        @WithMockUser(username = "admin")
        @DisplayName("Should throw exception for purchase of non-existent product")
        void shouldThrowExceptionForPurchaseOfNonExistentProduct() {
            UUID randomId = UUID.randomUUID();

            assertThatThrownBy(() -> inventoryService.recordPurchase(
                    randomId, LocalDate.now(), BigDecimal.TEN, new BigDecimal("10000"),
                    "REF-001", "Test purchase"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tidak ditemukan");
        }

        @Test
        @WithMockUser(username = "admin")
        @DisplayName("Should record purchase for existing product")
        void shouldRecordPurchaseForExistingProduct() {
            if (testProduct == null) return;

            InventoryTransaction transaction = inventoryService.recordPurchase(
                    testProduct.getId(), LocalDate.now(), BigDecimal.TEN, new BigDecimal("10000"),
                    "REF-TEST-001", "Test purchase");

            assertThat(transaction).isNotNull();
            assertThat(transaction.getId()).isNotNull();
            assertThat(transaction.getQuantity()).isEqualByComparingTo(BigDecimal.TEN);
        }
    }

    @Nested
    @DisplayName("Journal Template Resolution")
    class JournalTemplateResolutionTests {

        @Test
        @WithMockUser(username = "admin")
        @DisplayName("Should draft the purchase journal for a product with an inventory account")
        void shouldDraftPurchaseJournal() {
            Product product = productWithInventoryAccount();

            InventoryTransaction transaction = inventoryService.recordPurchase(product.getId(),
                    LocalDate.of(2048, 3, 2), new BigDecimal("4"), new BigDecimal("2500"), "REF-JT", "Jurnal");

            Transaction journal = transaction.getTransaction();
            assertThat(journal).isNotNull();
            assertThat(journal.getStatus()).isEqualTo(TransactionStatus.DRAFT);
            assertThat(journal.getJournalTemplate().getTemplateName()).isEqualTo("Pembelian Persediaan");
            assertThat(journal.getAmount()).isEqualByComparingTo("10000");
            assertThat(journal.getAccountMappings()).singleElement()
                    .satisfies(m -> assertThat(m.getAccount().getAccountCode()).isEqualTo("1.4.01"));
        }

        @Test
        @WithMockUser(username = "admin")
        @DisplayName("Should refuse a movement of a product with an inventory account when its journal template is missing")
        void shouldRefuseMovementWithoutJournalTemplate() {
            Product product = productWithInventoryAccount();
            jdbcTemplate.update("UPDATE journal_templates SET template_name = 'Pembelian Persediaan (lama)' "
                    + "WHERE template_name = 'Pembelian Persediaan'");

            assertThatThrownBy(() -> inventoryService.recordPurchase(product.getId(), LocalDate.of(2048, 3, 2),
                    new BigDecimal("4"), new BigDecimal("2500"), "REF-JT", "Jurnal"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Template jurnal 'Pembelian Persediaan' tidak ditemukan. Produk " + product.getCode()
                            + " memiliki akun persediaan, sehingga transaksi persediaan wajib dijurnal.");
        }

        private Product productWithInventoryAccount() {
            Product product = new Product();
            product.setCode("JT-" + UUID.randomUUID().toString().substring(0, 8));
            product.setName("Produk Jurnal");
            product.setUnit("pcs");
            product.setInventoryAccount(accountRepository.findByAccountCode("1.4.01").orElseThrow());
            return productRepository.save(product);
        }
    }

    @Nested
    @DisplayName("Sale Operations")
    class SaleOperationsTests {

        @Test
        @WithMockUser(username = "admin")
        @DisplayName("Should throw exception for sale of non-existent product")
        void shouldThrowExceptionForSaleOfNonExistentProduct() {
            UUID randomId = UUID.randomUUID();

            assertThatThrownBy(() -> inventoryService.recordSale(
                    randomId, LocalDate.now(), BigDecimal.ONE, new BigDecimal("15000"),
                    "SALE-001", "Test sale"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tidak ditemukan");
        }
    }

    @Nested
    @DisplayName("Adjustment Operations")
    class AdjustmentOperationsTests {

        @Test
        @WithMockUser(username = "admin")
        @DisplayName("Should throw exception for adjustment of non-existent product")
        void shouldThrowExceptionForAdjustmentOfNonExistentProduct() {
            UUID randomId = UUID.randomUUID();

            assertThatThrownBy(() -> inventoryService.recordAdjustmentIn(
                    randomId, LocalDate.now(), BigDecimal.ONE, new BigDecimal("10000"),
                    "ADJ-001", "Test adjustment"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tidak ditemukan");
        }

        @Test
        @WithMockUser(username = "admin")
        @DisplayName("Should record adjustment in for existing product")
        void shouldRecordAdjustmentInForExistingProduct() {
            if (testProduct == null) return;

            InventoryTransaction transaction = inventoryService.recordAdjustmentIn(
                    testProduct.getId(), LocalDate.now(), BigDecimal.ONE, new BigDecimal("10000"),
                    "ADJ-TEST-001", "Test adjustment");

            assertThat(transaction).isNotNull();
            assertThat(transaction.getId()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Production Operations")
    class ProductionOperationsTests {

        @Test
        @WithMockUser(username = "admin")
        @DisplayName("Should throw exception for production in of non-existent product")
        void shouldThrowExceptionForProductionInOfNonExistentProduct() {
            UUID randomId = UUID.randomUUID();

            assertThatThrownBy(() -> inventoryService.recordProductionIn(
                    randomId, LocalDate.now(), BigDecimal.ONE, new BigDecimal("10000"),
                    "PROD-001", "Test production"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tidak ditemukan");
        }

        @Test
        @WithMockUser(username = "admin")
        @DisplayName("Should throw exception for production out of non-existent product")
        void shouldThrowExceptionForProductionOutOfNonExistentProduct() {
            UUID randomId = UUID.randomUUID();

            assertThatThrownBy(() -> inventoryService.recordProductionOut(
                    randomId, LocalDate.now(), BigDecimal.ONE,
                    "PROD-001", "Test production"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tidak ditemukan");
        }
    }

    @Nested
    @DisplayName("FIFO Layer Operations")
    class FifoLayerOperationsTests {

        @Test
        @DisplayName("Should get FIFO layers for product - empty for random ID")
        void shouldGetFifoLayersForProductEmptyForRandomId() {
            if (testProduct == null) return;

            var layers = inventoryService.getFifoLayers(testProduct.getId());
            assertThat(layers).isNotNull();
        }
    }

    @Nested
    @DisplayName("Valuation Operations")
    class ValuationOperationsTests {

        @Test
        @DisplayName("Should get total inventory value")
        void shouldGetTotalInventoryValue() {
            BigDecimal totalValue = inventoryService.getTotalInventoryValue();
            assertThat(totalValue).isNotNull();
            assertThat(totalValue).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Should get available quantity")
        void shouldGetAvailableQuantity() {
            if (testProduct == null) return;
            BigDecimal available = inventoryService.getAvailableQuantity(testProduct.getId());
            assertThat(available).isNotNull();
        }

        @Test
        @DisplayName("Should calculate COGS")
        void shouldCalculateCogs() {
            if (testProduct == null) return;
            BigDecimal cogs = inventoryService.calculateCogs(testProduct.getId(), BigDecimal.ONE);
            assertThat(cogs).isNotNull();
        }
    }

    @Nested
    @DisplayName("Stock Adjustment Operations")
    @WithMockUser(username = "admin")
    class StockAdjustmentTests {

        @Test
        @DisplayName("Should record adjustment in and update balance")
        void shouldRecordAdjustmentInAndUpdateBalance() {
            if (testProduct == null) return;

            BigDecimal stockBefore = inventoryService.getCurrentStock(testProduct.getId());

            InventoryTransaction transaction = inventoryService.recordAdjustmentIn(
                    testProduct.getId(), LocalDate.now(),
                    new BigDecimal("5"), new BigDecimal("10000"),
                    "ADJ-IN-TEST", "Test adjustment in");

            assertThat(transaction).isNotNull();
            assertThat(transaction.getId()).isNotNull();

            BigDecimal stockAfter = inventoryService.getCurrentStock(testProduct.getId());
            assertThat(stockAfter).isEqualByComparingTo(stockBefore.add(new BigDecimal("5")));
        }

        @Test
        @DisplayName("Should record adjustment out and decrease balance")
        void shouldRecordAdjustmentOutAndDecreaseBalance() {
            if (testProduct == null) return;

            // First add stock
            inventoryService.recordAdjustmentIn(
                    testProduct.getId(), LocalDate.now(),
                    new BigDecimal("10"), new BigDecimal("10000"),
                    "ADJ-IN-PREP", "Prepare stock");

            BigDecimal stockBefore = inventoryService.getCurrentStock(testProduct.getId());

            InventoryTransaction transaction = inventoryService.recordAdjustmentOut(
                    testProduct.getId(), LocalDate.now(),
                    new BigDecimal("3"),
                    "ADJ-OUT-TEST", "Test adjustment out");

            assertThat(transaction).isNotNull();

            BigDecimal stockAfter = inventoryService.getCurrentStock(testProduct.getId());
            assertThat(stockAfter).isEqualByComparingTo(stockBefore.subtract(new BigDecimal("3")));
        }

        @Test
        @DisplayName("Should throw for adjustment out exceeding stock")
        void shouldThrowForAdjustmentOutExceedingStock() {
            if (testProduct == null) return;

            BigDecimal currentStock = inventoryService.getCurrentStock(testProduct.getId());
            BigDecimal excessQuantity = currentStock.add(new BigDecimal("999"));

            assertThatThrownBy(() -> inventoryService.recordAdjustmentOut(
                    testProduct.getId(), LocalDate.now(),
                    excessQuantity, "ADJ-EXCESS", "Should fail"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tidak mencukupi");
        }
    }

    @Nested
    @DisplayName("Sale Operations - Additional")
    @WithMockUser(username = "admin")
    class SaleAdditionalTests {

        @Test
        @DisplayName("Should record sale after purchase")
        void shouldRecordSaleAfterPurchase() {
            if (testProduct == null) return;

            // Purchase first
            inventoryService.recordPurchase(
                    testProduct.getId(), LocalDate.now(),
                    new BigDecimal("20"), new BigDecimal("10000"),
                    "SALE-PREP", "Prepare for sale");

            BigDecimal stockBefore = inventoryService.getCurrentStock(testProduct.getId());

            // Then sell
            InventoryTransaction sale = inventoryService.recordSale(
                    testProduct.getId(), LocalDate.now(),
                    new BigDecimal("5"), new BigDecimal("15000"),
                    "SALE-TEST", "Test sale");

            assertThat(sale).isNotNull();
            assertThat(sale.getUnitCost()).isNotNull();
            assertThat(sale.getTotalCost()).isNotNull();

            BigDecimal stockAfter = inventoryService.getCurrentStock(testProduct.getId());
            assertThat(stockAfter).isEqualByComparingTo(stockBefore.subtract(new BigDecimal("5")));
        }

        @Test
        @DisplayName("Should throw for sale exceeding available stock")
        void shouldThrowForSaleExceedingStock() {
            if (testProduct == null) return;

            BigDecimal currentStock = inventoryService.getCurrentStock(testProduct.getId());
            BigDecimal excessQuantity = currentStock.add(new BigDecimal("999"));

            assertThatThrownBy(() -> inventoryService.recordSale(
                    testProduct.getId(), LocalDate.now(),
                    excessQuantity, new BigDecimal("15000"),
                    "SALE-EXCESS", "Should fail"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tidak mencukupi");
        }
    }

    @Nested
    @DisplayName("Production Operations - Additional")
    @WithMockUser(username = "admin")
    class ProductionAdditionalTests {

        @Test
        @DisplayName("Should record production in and update balance")
        void shouldRecordProductionInAndUpdateBalance() {
            if (testProduct == null) return;

            BigDecimal stockBefore = inventoryService.getCurrentStock(testProduct.getId());

            InventoryTransaction transaction = inventoryService.recordProductionIn(
                    testProduct.getId(), LocalDate.now(),
                    new BigDecimal("10"), new BigDecimal("8000"),
                    "PROD-IN-TEST", "Test production in");

            assertThat(transaction).isNotNull();

            BigDecimal stockAfter = inventoryService.getCurrentStock(testProduct.getId());
            assertThat(stockAfter).isEqualByComparingTo(stockBefore.add(new BigDecimal("10")));
        }

        @Test
        @DisplayName("Should record production out after adding stock")
        void shouldRecordProductionOutAfterAddingStock() {
            if (testProduct == null) return;

            // Add stock first
            inventoryService.recordProductionIn(
                    testProduct.getId(), LocalDate.now(),
                    new BigDecimal("20"), new BigDecimal("8000"),
                    "PROD-PREP", "Prepare for production out");

            BigDecimal stockBefore = inventoryService.getCurrentStock(testProduct.getId());

            InventoryTransaction transaction = inventoryService.recordProductionOut(
                    testProduct.getId(), LocalDate.now(),
                    new BigDecimal("5"),
                    "PROD-OUT-TEST", "Test production out");

            assertThat(transaction).isNotNull();

            BigDecimal stockAfter = inventoryService.getCurrentStock(testProduct.getId());
            assertThat(stockAfter).isEqualByComparingTo(stockBefore.subtract(new BigDecimal("5")));
        }
    }

    @Nested
    @DisplayName("Transaction Filter Operations")
    class TransactionFilterTests {

        @Test
        @DisplayName("Should find transactions with type filter")
        void shouldFindTransactionsWithTypeFilter() {
            if (testProduct == null) return;

            Page<InventoryTransaction> transactions = inventoryService.findTransactions(
                    testProduct.getId(),
                    com.artivisi.accountingfinance.entity.InventoryTransactionType.PURCHASE,
                    null, null, PageRequest.of(0, 10));

            assertThat(transactions).isNotNull();
        }

        @Test
        @DisplayName("Should find transactions with date range filter")
        void shouldFindTransactionsWithDateRangeFilter() {
            if (testProduct == null) return;

            Page<InventoryTransaction> transactions = inventoryService.findTransactions(
                    testProduct.getId(), null,
                    LocalDate.now().minusMonths(1), LocalDate.now().plusDays(1),
                    PageRequest.of(0, 10));

            assertThat(transactions).isNotNull();
        }

        @Test
        @DisplayName("Should return zero stock for non-existent product")
        void shouldReturnZeroStockForNonExistentProduct() {
            BigDecimal stock = inventoryService.getCurrentStock(UUID.randomUUID());
            assertThat(stock).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Should return zero average cost for non-existent product")
        void shouldReturnZeroAverageCostForNonExistent() {
            BigDecimal cost = inventoryService.getCurrentAverageCost(UUID.randomUUID());
            assertThat(cost).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Should return zero available quantity for non-existent product")
        void shouldReturnZeroAvailableForNonExistent() {
            BigDecimal available = inventoryService.getAvailableQuantity(UUID.randomUUID());
            assertThat(available).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("Balance Operations - Additional")
    @WithMockUser(username = "admin")
    class BalanceAdditionalTests {

        @Test
        @DisplayName("Should get or create balance for product")
        void shouldGetOrCreateBalance() {
            if (testProduct == null) return;

            InventoryBalance balance = inventoryService.getOrCreateBalance(testProduct);

            assertThat(balance).isNotNull();
            assertThat(balance.getProduct().getId()).isEqualTo(testProduct.getId());
        }

        @Test
        @DisplayName("Should find balances with category filter")
        void shouldFindBalancesWithCategoryFilter() {
            Page<InventoryBalance> balances = inventoryService.findBalances(
                    null, UUID.randomUUID(), PageRequest.of(0, 10));
            assertThat(balances).isNotNull();
        }
    }
}
