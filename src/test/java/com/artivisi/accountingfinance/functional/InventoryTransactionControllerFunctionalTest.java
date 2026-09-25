package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.CostingMethod;
import com.artivisi.accountingfinance.entity.InventoryBalance;
import com.artivisi.accountingfinance.entity.InventoryFifoLayer;
import com.artivisi.accountingfinance.entity.InventoryTransaction;
import com.artivisi.accountingfinance.entity.Product;
import com.artivisi.accountingfinance.entity.ProductCategory;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.functional.manufacturing.CoffeeTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.InventoryBalanceRepository;
import com.artivisi.accountingfinance.repository.InventoryFifoLayerRepository;
import com.artivisi.accountingfinance.repository.InventoryTransactionRepository;
import com.artivisi.accountingfinance.repository.ProductCategoryRepository;
import com.artivisi.accountingfinance.repository.ProductRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.service.InventoryService;
import com.artivisi.accountingfinance.service.TransactionService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * InventoryTransactionController: purchase, sale and adjustment forms (weighted-average and FIFO costing, DRAFT
 * journals from the coffee-shop inventory templates), server-side validation, transaction list filters and
 * pagination, stock list search/category filter/low-stock alert, stock card and permissions.
 *
 * Every test records movements on products it creates (unique codes), dated in 2050.
 */
@DisplayName("Inventory Transaction Controller Tests")
@Import(CoffeeTestDataInitializer.class)
class InventoryTransactionControllerFunctionalTest extends PlaywrightTestBase {

    private static final Pattern TRANSACTION_DETAIL_URL = Pattern.compile(".*/inventory/transactions/[0-9a-f-]{36}$");

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductCategoryRepository categoryRepository;

    @Autowired
    private ChartOfAccountRepository accountRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryBalanceRepository balanceRepository;

    @Autowired
    private InventoryTransactionRepository inventoryTransactionRepository;

    @Autowired
    private InventoryFifoLayerRepository fifoLayerRepository;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ==================== WEIGHTED AVERAGE FLOW ====================

    @Test
    @DisplayName("Should record weighted-average purchases, a sale and adjustments through the forms at the running average cost and draft their journals")
    void shouldRecordWeightedAverageMovements() {
        Product product = createProduct("WA", "kg", CostingMethod.WEIGHTED_AVERAGE, "5", null);
        loginAsAdmin();

        navigateTo("/dashboard");
        waitForPageLoad();
        assertThat(page.locator("#nav-inventory")).hasAttribute("href", "/inventory/transactions");
        page.locator("#nav-group-inventori").click();
        page.locator("#nav-inventory").click();
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/inventory/transactions");
        assertThat(page.locator("#page-title")).hasText("Transaksi Persediaan");

        Locator menu = page.locator("#new-transaction-menu");
        assertThat(menu).isHidden();
        page.locator("#btn-new-transaction").click();
        assertThat(menu).isVisible();
        page.locator("#page-title").click();
        assertThat(menu).isHidden();
        page.locator("#btn-new-transaction").click();
        assertThat(page.locator("#menu-sale")).hasAttribute("href", "/inventory/sale");
        assertThat(page.locator("#menu-adjustment")).hasAttribute("href", "/inventory/adjustment");
        page.locator("#menu-purchase").click();
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/inventory/purchase");
        assertThat(page.locator("#page-title")).hasText("Pembelian Persediaan");
        assertThat(page.locator("#transactionDate")).hasValue(LocalDate.now().toString());
        assertThat(page.locator("#productId option[value='" + product.getId() + "']"))
                .hasText(product.getCode() + " - " + product.getName() + " (kg)");

        submitPurchase(product, "2050-02-01", "10", "10000", "PO-" + product.getCode(), "Pembelian awal");
        assertThat(page.getByTestId("flash-success")).hasText("Pembelian berhasil dicatat");
        assertThat(page.locator("#page-title")).hasText("Detail Transaksi Persediaan");
        assertThat(page.getByTestId("transaction-type")).hasText("Pembelian");
        assertThat(page.locator("a:text-is('" + product.getCode() + " - " + product.getName() + "')").first())
                .hasAttribute("href", "/inventory/stock/" + product.getId());
        assertThat(info("Tanggal Transaksi")).hasText("01 Februari 2050");
        assertThat(info("Kuantitas")).hasText("+10.00 kg");
        assertThat(info("Harga Satuan")).hasText("Rp 10,000.00");
        assertThat(info("Total Biaya")).hasText("Rp 100,000");
        assertThat(info("Nomor Referensi")).hasText("PO-" + product.getCode());
        assertThat(info("Saldo Setelah Transaksi")).hasText("10.00 kg");
        assertThat(info("Catatan")).hasText("Pembelian awal");
        assertThat(page.locator("text=Dibuat oleh:")).hasText("Dibuat oleh: admin");
        assertThat(info("Harga Jual")).hasCount(0);
        InventoryTransaction purchase = latest(product);
        assertEquals("2050-02-01|PURCHASE|10.0000|10000.0000|100000.00|10.0000|100000.00|PO-" + product.getCode()
                + "|Pembelian awal|admin", movement(purchase));
        assertBalance(product, "10", "100000", "10000");
        assertJournal(purchase, "Pembelian Persediaan", "100000", "Pembelian " + product.getCode() + " - 10 x kg",
                List.of("DEBIT:1.1.20"), Map.of("amount", "100000", "cogsAmount", "100000"));

        navigateTo("/inventory/purchase");
        waitForPageLoad();
        submitPurchase(product, "2050-02-03", "5", "13000", "PO2-" + product.getCode(), "Pembelian kedua");
        assertThat(info("Saldo Setelah Transaksi")).hasText("15.00 kg");
        assertBalance(product, "15", "165000", "11000");

        navigateTo("/inventory/sale");
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Penjualan Persediaan");
        fillMovement(product, "2050-02-05", "6", "SO-" + product.getCode(), "Penjualan toko");
        page.locator("#unitPrice").fill("15000");
        page.locator("#btn-submit").click();
        page.waitForURL(TRANSACTION_DETAIL_URL);
        assertThat(page.getByTestId("flash-success")).hasText("Penjualan berhasil dicatat");
        assertThat(page.getByTestId("transaction-type")).hasText("Penjualan");
        assertThat(info("Kuantitas")).hasText("-6.00 kg");
        assertThat(info("Harga Satuan")).hasText("Rp 11,000.00");
        assertThat(info("Total Biaya")).hasText("Rp 66,000");
        assertThat(info("Harga Jual")).hasText("Rp 15,000.00");
        assertThat(info("Total Pendapatan")).hasText("Rp 90,000");
        assertThat(info("Margin")).hasText("Rp 24,000 (26.7%)");
        assertThat(info("Saldo Setelah Transaksi")).hasText("9.00 kg");
        InventoryTransaction sale = latest(product);
        assertEquals("2050-02-05|SALE|6.0000|11000.0000|66000.00|9.0000|99000.00|SO-" + product.getCode()
                + "|Penjualan toko|admin", movement(sale));
        assertEquals(0, new BigDecimal("15000").compareTo(sale.getUnitPrice()));
        assertBalance(product, "9", "99000", "11000");
        assertJournal(sale, "Penjualan Persediaan", "66000", "Penjualan " + product.getCode() + " - 6 x kg",
                List.of("DEBIT:5.1.01", "CREDIT:4.1.01", "CREDIT:1.1.20"),
                Map.of("amount", "66000", "cogsAmount", "66000", "revenueAmount", "90000"));

        navigateTo("/inventory/adjustment");
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Penyesuaian Persediaan");
        assertThat(page.locator("input[name='inbound'][value='true']")).isChecked();
        page.locator("input[name='inbound'][value='false']").check();
        fillMovement(product, "2050-02-07", "1", "ADJ-OUT-" + product.getCode(), "Rusak saat pengiriman");
        page.locator("#btn-submit").click();
        page.waitForURL(TRANSACTION_DETAIL_URL);
        assertThat(page.getByTestId("flash-success")).hasText("Penyesuaian berhasil dicatat");
        assertThat(page.getByTestId("transaction-type")).hasText("Penyesuaian Keluar");
        assertThat(info("Kuantitas")).hasText("-1.00 kg");
        assertThat(info("Harga Satuan")).hasText("Rp 11,000.00");
        assertThat(info("Total Biaya")).hasText("Rp 11,000");
        InventoryTransaction adjustmentOut = latest(product);
        assertEquals("2050-02-07|ADJUSTMENT_OUT|1.0000|11000.0000|11000.00|8.0000|88000.00|ADJ-OUT-" + product.getCode()
                + "|Rusak saat pengiriman|admin", movement(adjustmentOut));
        assertBalance(product, "8", "88000", "11000");
        assertJournal(adjustmentOut, "Penyesuaian Persediaan Keluar", "11000",
                "Penyesuaian Keluar " + product.getCode() + " - 1 x kg", List.of("CREDIT:1.1.20"),
                Map.of("amount", "11000", "cogsAmount", "11000"));

        navigateTo("/inventory/adjustment");
        waitForPageLoad();
        fillMovement(product, "2050-02-08", "2", "ADJ-IN-" + product.getCode(), "Selisih stock opname");
        page.locator("#unitCost").fill("12500");
        page.locator("#btn-submit").click();
        page.waitForURL(TRANSACTION_DETAIL_URL);
        assertThat(page.getByTestId("transaction-type")).hasText("Penyesuaian Masuk");
        assertThat(info("Kuantitas")).hasText("+2.00 kg");
        assertThat(info("Saldo Setelah Transaksi")).hasText("10.00 kg");
        InventoryTransaction adjustmentIn = latest(product);
        assertEquals("2050-02-08|ADJUSTMENT_IN|2.0000|12500.0000|25000.00|10.0000|113000.00|ADJ-IN-" + product.getCode()
                + "|Selisih stock opname|admin", movement(adjustmentIn));
        assertBalance(product, "10", "113000", "11300");
        assertJournal(adjustmentIn, "Penyesuaian Persediaan Masuk", "25000",
                "Penyesuaian Masuk " + product.getCode() + " - 2 x kg", List.of("DEBIT:1.1.20"),
                Map.of("amount", "25000", "cogsAmount", "25000"));

        transactionService.post(adjustmentOut.getTransaction().getId(), "admin");
        transactionService.post(adjustmentIn.getTransaction().getId(), "admin");
        assertEquals(List.of("DEBIT:8.1:11000.00", "CREDIT:1.1.20:11000.00"), journalEntries(adjustmentOut));
        assertEquals(List.of("DEBIT:1.1.20:25000.00", "CREDIT:7.1:25000.00"), journalEntries(adjustmentIn));
    }

    // ==================== FIFO FLOW & STOCK CARD ====================

    @Test
    @DisplayName("Should cost a FIFO sale from the oldest layers and show layers, balance and history on the stock card")
    void shouldCostFifoSaleAndShowStockCard() {
        Product product = createProduct("FIFO", "pcs", CostingMethod.FIFO, "10", null);
        inventoryService.recordPurchase(product.getId(), LocalDate.of(2050, 3, 1), new BigDecimal("4"),
                new BigDecimal("7000"), "B1-" + product.getCode(), null);
        inventoryService.recordPurchase(product.getId(), LocalDate.of(2050, 3, 2), new BigDecimal("6"),
                new BigDecimal("8000"), "B2-" + product.getCode(), null);
        loginAsAdmin();

        navigateTo("/inventory/sale");
        waitForPageLoad();
        fillMovement(product, "2050-03-05", "5", "SO-" + product.getCode(), "Penjualan FIFO");
        page.locator("#unitPrice").fill("13000");
        page.locator("#btn-submit").click();
        page.waitForURL(TRANSACTION_DETAIL_URL);
        assertThat(info("Harga Satuan")).hasText("Rp 7,200.00");
        assertThat(info("Total Biaya")).hasText("Rp 36,000");
        assertThat(info("Total Pendapatan")).hasText("Rp 65,000");
        assertThat(info("Margin")).hasText("Rp 29,000 (44.6%)");
        InventoryTransaction sale = latest(product);
        assertEquals("2050-03-05|SALE|5.0000|7200.0000|36000.00|5.0000|40000.00|SO-" + product.getCode()
                + "|Penjualan FIFO|admin", movement(sale));
        assertBalance(product, "5", "40000", "8000");
        assertEquals(List.of("2050-03-01|4.0000|0.0000|7000.0000", "2050-03-02|6.0000|5.0000|8000.0000"),
                fifoLayerRepository.findByProductId(product.getId()).stream().map(this::layer).toList());
        assertJournal(sale, "Penjualan Persediaan", "36000", "Penjualan " + product.getCode() + " - 5 x pcs",
                List.of("DEBIT:5.1.01", "CREDIT:4.1.01", "CREDIT:1.1.20"),
                Map.of("amount", "36000", "cogsAmount", "36000", "revenueAmount", "65000"));

        page.locator("a:text-is('Lihat Kartu Stok')").click();
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/inventory/stock/" + product.getId());
        assertThat(page.locator("#page-title")).hasText("Kartu Stok");
        assertThat(page.getByTestId("stock-card-product-name")).hasText(product.getName());
        assertThat(card("Satuan")).hasText("pcs");
        assertThat(card("Metode Perhitungan")).hasText("FIFO (First In, First Out)");
        assertThat(card("Kategori")).hasText("-");
        assertThat(card("Stok Minimum")).hasText("10.00");
        assertThat(card("Stok Saat Ini")).hasText("5.00 pcs");
        assertThat(card("Biaya Rata-rata")).hasText("Rp 8,000.00");
        assertThat(card("Total Nilai")).hasText("Rp 40,000");
        assertThat(table("Layer FIFO").locator("tbody tr")).hasCount(2);
        assertThat(table("Layer FIFO").locator("tbody tr").nth(0).locator("td"))
                .hasText(new String[]{"01/03/2050", "4.00", "0.00", "7,000.00", "0", "Habis"});
        assertThat(table("Layer FIFO").locator("tbody tr").nth(1).locator("td"))
                .hasText(new String[]{"02/03/2050", "6.00", "5.00", "8,000.00", "40,000", "Tersedia"});
        Locator history = table("Riwayat Transaksi").locator("tbody tr");
        assertThat(history).hasCount(3);
        assertThat(history.nth(0).locator("td"))
                .hasText(new String[]{"05/03/2050", "Penjualan", "-5.00", "7,200.00", "36,000", "5.00"});
        assertThat(history.nth(1).locator("td"))
                .hasText(new String[]{"02/03/2050", "Pembelian", "+6.00", "8,000.00", "48,000", "10.00"});
        assertThat(history.nth(2).locator("td"))
                .hasText(new String[]{"01/03/2050", "Pembelian", "+4.00", "7,000.00", "28,000", "4.00"});
        assertThat(history.nth(0).locator("a")).hasAttribute("href", "/inventory/transactions/" + sale.getId());
        assertThat(page.locator("a:text-is('Lihat Semua')"))
                .hasAttribute("href", "/inventory/transactions?productId=" + product.getId());
        assertThat(page.locator("section a[href='/inventory/purchase']")).isVisible();
        assertThat(page.locator("section a[href='/inventory/sale']")).isVisible();
        assertThat(page.locator("section a[href='/inventory/adjustment']")).isVisible();

        Product average = createProduct("AVG", "kg", CostingMethod.WEIGHTED_AVERAGE, "0", null);
        inventoryService.recordPurchase(average.getId(), LocalDate.of(2050, 3, 1), new BigDecimal("2"),
                new BigDecimal("500"), null, null);
        navigateTo("/inventory/stock/" + average.getId());
        waitForPageLoad();
        assertThat(card("Metode Perhitungan")).hasText("Rata-rata Tertimbang");
        assertThat(page.locator("h3:text-is('Layer FIFO')")).hasCount(0);
        assertThat(card("Stok Saat Ini")).hasText("2.00 kg");
    }

    // ==================== REJECTIONS ====================

    @Test
    @DisplayName("Should reject a sale beyond the stock on hand and keep the entered values, recording nothing")
    void shouldRejectSaleBeyondStock() {
        Product product = createProduct("KURANG", "pcs", CostingMethod.WEIGHTED_AVERAGE, "0", null);
        inventoryService.recordPurchase(product.getId(), LocalDate.of(2050, 4, 1), new BigDecimal("3"),
                new BigDecimal("2000"), null, null);
        loginAsAdmin();

        navigateTo("/inventory/sale");
        waitForPageLoad();
        fillMovement(product, "2050-04-02", "5", "SO-" + product.getCode(), "Terlalu banyak");
        page.locator("#unitPrice").fill("3000");
        page.locator("#btn-submit").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/inventory/sale");
        assertThat(page.getByTestId("flash-error"))
                .hasText("Stok tidak mencukupi untuk " + product.getCode() + ". Stok saat ini: 3.0000, diminta: 5");
        assertThat(page.locator("#productId")).hasValue(product.getId().toString());
        assertThat(page.locator("#transactionDate")).hasValue("2050-04-02");
        assertThat(page.locator("#quantity")).hasValue("5");
        assertThat(page.locator("#unitPrice")).hasValue("3000");
        assertEquals(1, inventoryTransactionRepository.findByProductId(product.getId()).size());
        assertBalance(product, "3", "6000", "2000");
    }

    @Test
    @DisplayName("Should reject missing product/date, non-positive quantity, missing or negative prices and a missing adjustment reason server-side")
    void shouldRejectInvalidFormsServerSide() {
        Product product = createProduct("VALID", "pcs", CostingMethod.WEIGHTED_AVERAGE, "0", null);
        loginAsAdmin();
        navigateTo("/inventory/purchase");
        waitForPageLoad();
        String csrf = csrf();

        String blank = postForm("/inventory/purchase", FormData.create().set("_csrf", csrf).set("productId", "")
                .set("transactionDate", "").set("quantity", "").set("unitCost", "")).text();
        assertTrue(blank.contains("Produk wajib dipilih"), "product error");
        assertTrue(blank.contains("Tanggal transaksi wajib diisi"), "date error");
        assertTrue(blank.contains("Kuantitas wajib diisi"), "quantity error");
        assertTrue(blank.contains("Harga satuan wajib diisi"), "unit cost error");

        String negative = postForm("/inventory/purchase", movementForm(csrf, product, "-2")
                .set("unitCost", "-100")).text();
        assertTrue(negative.contains("Kuantitas harus lebih dari 0"), "negative quantity error");
        assertTrue(negative.contains("Harga satuan tidak boleh negatif"), "negative unit cost error");

        String zero = postForm("/inventory/adjustment", movementForm(csrf, product, "0")
                .set("inbound", "false").set("notes", "Nol")).text();
        assertTrue(zero.contains("Kuantitas harus lebih dari 0"), "zero quantity error");

        String salePrice = postForm("/inventory/sale", movementForm(csrf, product, "1")
                .set("unitPrice", "-5")).text();
        assertTrue(salePrice.contains("Harga jual tidak boleh negatif"), "negative price error");

        String adjustment = postForm("/inventory/adjustment", movementForm(csrf, product, "2")
                .set("inbound", "true").set("unitCost", "").set("notes", " ")).text();
        assertTrue(adjustment.contains("Harga satuan wajib diisi"), "adjustment unit cost error");
        assertTrue(adjustment.contains("Alasan penyesuaian wajib diisi"), "adjustment reason error");

        assertTrue(inventoryTransactionRepository.findByProductId(product.getId()).isEmpty());
        assertTrue(balanceRepository.findByProductId(product.getId()).isEmpty());
    }

    // ==================== TRANSACTION LIST ====================

    @Test
    @DisplayName("Should filter the transaction list by product, type and dates, prefill filters from the URL and paginate")
    void shouldFilterAndPaginateTransactions() {
        Product busy = createProduct("RAMAI", "pcs", CostingMethod.WEIGHTED_AVERAGE, "0", null);
        for (int day = 1; day <= 21; day++) {
            inventoryService.recordPurchase(busy.getId(), LocalDate.of(2050, 5, day), BigDecimal.ONE,
                    new BigDecimal("1000"), "R" + day, null);
        }
        Product product = createProduct("SARING", "pcs", CostingMethod.WEIGHTED_AVERAGE, "0", null);
        InventoryTransaction purchase = inventoryService.recordPurchase(product.getId(), LocalDate.of(2050, 6, 1),
                new BigDecimal("4"), new BigDecimal("2500"), "BELI-" + product.getCode(), null);
        InventoryTransaction sale = inventoryService.recordSale(product.getId(), LocalDate.of(2050, 6, 10),
                new BigDecimal("1.5"), new BigDecimal("4000"), "JUAL-" + product.getCode(), null);
        loginAsAdmin();

        navigateTo("/inventory/transactions?productId=" + busy.getId());
        waitForPageLoad();
        assertThat(page.locator("#product-filter")).hasValue(busy.getId().toString());
        assertThat(rows()).hasCount(20);
        assertThat(page.locator("#transaction-table p.text-sm")).hasText("Menampilkan 1 - 20 dari 21 data");
        assertThat(rows().first().locator("td").first()).hasText("21/05/2050");
        page.locator("#transaction-table a:text-is('Selanjutnya')").click();
        waitForPageLoad();
        assertThat(page).hasURL(Pattern.compile(".*page=1.*productId=" + busy.getId() + ".*"));
        assertThat(rows()).hasCount(1);
        assertThat(rows().first().locator("td").nth(6)).hasText("R1");

        navigateTo("/inventory/transactions");
        waitForPageLoad();
        page.locator("#product-filter").selectOption(product.getId().toString());
        assertThat(rows()).hasCount(2);
        assertThat(row(sale).locator("td")).hasText(new String[]{"10/06/2050", "Penjualan",
                product.getCode() + " - " + product.getName(), "-1.50 pcs", "2,500.00", "3,750",
                "JUAL-" + product.getCode(), ""});
        assertThat(row(purchase).locator("td")).hasText(new String[]{"01/06/2050", "Pembelian",
                product.getCode() + " - " + product.getName(), "+4.00 pcs", "2,500.00", "10,000",
                "BELI-" + product.getCode(), ""});
        assertThat(row(sale).locator("td").nth(2).locator("a")).hasAttribute("href", "/inventory/stock/" + product.getId());
        assertThat(row(sale).locator("td").nth(7).locator("a")).hasAttribute("href", "/inventory/transactions/" + sale.getId());

        page.locator("#transaction-type-filter").selectOption("SALE");
        assertThat(rows()).hasCount(1);
        assertThat(row(sale)).isVisible();

        page.locator("#transaction-type-filter").selectOption("");
        page.locator("#start-date").fill("2050-06-05");
        assertThat(rows()).hasCount(1);
        assertThat(row(sale)).isVisible();
        page.locator("#start-date").fill("2050-06-01");
        page.locator("#end-date").fill("2050-06-05");
        assertThat(rows()).hasCount(1);
        assertThat(row(purchase)).isVisible();

        page.locator("#transaction-type-filter").selectOption("ADJUSTMENT_IN");
        assertThat(rows()).hasCount(1);
        assertThat(rows().first()).hasText("Tidak ada transaksi");

        navigateTo("/inventory/transactions?productId=" + product.getId()
                + "&transactionType=SALE&startDate=2050-06-05&endDate=2050-06-30");
        waitForPageLoad();
        assertThat(page.locator("#product-filter")).hasValue(product.getId().toString());
        assertThat(page.locator("#transaction-type-filter")).hasValue("SALE");
        assertThat(page.locator("#start-date")).hasValue("2050-06-05");
        assertThat(page.locator("#end-date")).hasValue("2050-06-30");
        assertThat(rows()).hasCount(1);
        assertThat(row(sale)).isVisible();

        row(sale).locator("td").nth(7).locator("a").click();
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/inventory/transactions/" + sale.getId());
        assertThat(info("Kuantitas")).hasText("-1.50 pcs");
        assertThat(info("Saldo Setelah Transaksi")).hasText("2.50 pcs");
    }

    // ==================== STOCK LIST ====================

    @Test
    @DisplayName("Should search the stock list and filter it by category, flag low stock and link to the stock card")
    void shouldSearchAndFilterStockList() {
        String id = uid().toUpperCase();
        ProductCategory category = new ProductCategory();
        category.setCode("STK-" + id);
        category.setName("Kategori Stok " + id);
        category = categoryRepository.save(category);
        Product normal = createProduct("STK-" + id + "-A", "Normal " + id, "kg", CostingMethod.WEIGHTED_AVERAGE, "5", category);
        Product low = createProduct("STK-" + id + "-B", "Rendah " + id, "pcs", CostingMethod.WEIGHTED_AVERAGE, "5", category);
        Product outside = createProduct("STK-" + id + "-C", "Luar " + id, "pcs", CostingMethod.WEIGHTED_AVERAGE, "0", null);
        inventoryService.recordPurchase(normal.getId(), LocalDate.of(2050, 7, 1), new BigDecimal("8"),
                new BigDecimal("1250"), null, null);
        inventoryService.recordPurchase(low.getId(), LocalDate.of(2050, 7, 1), new BigDecimal("3"),
                new BigDecimal("0.75"), null, null);
        inventoryService.recordPurchase(outside.getId(), LocalDate.of(2050, 7, 1), BigDecimal.ONE,
                new BigDecimal("100"), null, null);
        loginAsAdmin();

        navigateTo("/dashboard");
        waitForPageLoad();
        page.locator("#nav-group-inventori").click();
        page.locator("#nav-stock").click();
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/inventory/stock");
        assertThat(page.locator("#page-title")).hasText("Stok Barang");
        assertThat(page.getByTestId("low-stock-" + low.getCode()).locator("span"))
                .hasText(new String[]{low.getCode() + " - " + low.getName(), "3.00 pcs (min: 5.00)"});
        assertThat(page.getByTestId("low-stock-" + normal.getCode())).hasCount(0);

        page.locator("#search-input").pressSequentially("stk-" + id.toLowerCase());
        assertThat(stockRows()).hasCount(3);
        assertThat(stockRow(normal).locator("td")).hasText(new String[]{normal.getCode(), normal.getName(),
                category.getName(), "8.00 kg", "1,250.00", "10,000", "Normal", ""});
        assertThat(stockRow(low).locator("td")).hasText(new String[]{low.getCode(), low.getName(),
                category.getName(), "3.00 pcs", "0.75", "2", "Stok Rendah", ""});
        assertThat(stockRow(outside).locator("td").nth(2)).hasText("-");

        page.locator("#category-filter").selectOption(category.getId().toString());
        assertThat(stockRows()).hasCount(2);
        assertThat(stockRow(outside)).hasCount(0);

        page.locator("#search-input").fill("");
        page.locator("#search-input").pressSequentially("rendah " + id.toLowerCase());
        assertThat(stockRows()).hasCount(1);
        assertThat(stockRow(low)).isVisible();

        navigateTo("/inventory/stock?search=" + normal.getCode() + "&categoryId=" + category.getId());
        waitForPageLoad();
        assertThat(page.locator("#search-input")).hasValue(normal.getCode());
        assertThat(page.locator("#category-filter")).hasValue(category.getId().toString());
        assertThat(stockRows()).hasCount(1);
        stockRow(normal).locator("a").first().click();
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/inventory/stock/" + normal.getId());
        assertThat(card("Kategori")).hasText(category.getName());
        assertThat(card("Stok Saat Ini")).hasText("8.00 kg");
    }

    // ==================== PERMISSIONS ====================

    @Test
    @DisplayName("Should let a view-only user browse inventory but not open or submit purchase, sale or adjustment forms")
    void shouldDenyMovementsWithoutPermission() {
        Product product = createProduct("AUD", "pcs", CostingMethod.WEIGHTED_AVERAGE, "0", null);
        InventoryTransaction purchase = inventoryService.recordPurchase(product.getId(), LocalDate.of(2050, 8, 1),
                new BigDecimal("2"), new BigDecimal("1000"), null, null);
        User auditor = createUser(Role.AUDITOR);
        login(auditor.getUsername(), "password");

        navigateTo("/inventory/transactions?productId=" + product.getId());
        waitForPageLoad();
        assertThat(row(purchase)).isVisible();
        assertThat(page.locator("#btn-new-transaction")).hasCount(0);
        navigateTo("/inventory/transactions/" + purchase.getId());
        waitForPageLoad();
        assertThat(info("Kuantitas")).hasText("+2.00 pcs");
        navigateTo("/inventory/stock/" + product.getId());
        waitForPageLoad();
        assertThat(card("Stok Saat Ini")).hasText("2.00 pcs");
        assertThat(page.locator("section a[href='/inventory/purchase']")).hasCount(0);
        assertThat(page.locator("section a[href='/inventory/sale']")).hasCount(0);
        assertThat(page.locator("section a[href='/inventory/adjustment']")).hasCount(0);

        for (String path : List.of("/inventory/purchase", "/inventory/sale", "/inventory/adjustment")) {
            assertEquals(403, page.request().get(baseUrl() + path).status(), path);
        }
        String csrf = csrf();
        assertEquals(403, postForm("/inventory/purchase", movementForm(csrf, product, "1").set("unitCost", "1000")).status());
        assertEquals(403, postForm("/inventory/sale", movementForm(csrf, product, "1").set("unitPrice", "1500")).status());
        assertEquals(403, postForm("/inventory/adjustment", movementForm(csrf, product, "1")
                .set("inbound", "false").set("notes", "Hilang")).status());
        assertEquals(1, inventoryTransactionRepository.findByProductId(product.getId()).size());
        assertBalance(product, "2", "2000", "1000");
    }

    // ==================== helpers ====================

    private Product createProduct(String prefix, String unit, CostingMethod costingMethod, String minimumStock,
                                  ProductCategory category) {
        String code = "INV-" + prefix + "-" + uid().toUpperCase();
        return createProduct(code, prefix.charAt(0) + prefix.substring(1).toLowerCase() + " Uji " + code, unit,
                costingMethod, minimumStock, category);
    }

    private Product createProduct(String code, String name, String unit, CostingMethod costingMethod,
                                  String minimumStock, ProductCategory category) {
        Product product = new Product();
        product.setCode(code);
        product.setName(name);
        product.setUnit(unit);
        product.setCostingMethod(costingMethod);
        product.setMinimumStock(new BigDecimal(minimumStock));
        product.setCategory(category);
        product.setInventoryAccount(account("1.1.20"));
        product.setCogsAccount(account("5.1.01"));
        product.setSalesAccount(account("4.1.01"));
        return productRepository.save(product);
    }

    private void submitPurchase(Product product, String date, String quantity, String unitCost, String reference,
                                String notes) {
        fillMovement(product, date, quantity, reference, notes);
        page.locator("#unitCost").fill(unitCost);
        page.locator("#btn-submit").click();
        page.waitForURL(TRANSACTION_DETAIL_URL);
    }

    private void fillMovement(Product product, String date, String quantity, String reference, String notes) {
        page.locator("#productId").selectOption(product.getId().toString());
        page.locator("#transactionDate").fill(date);
        page.locator("#quantity").fill(quantity);
        page.locator("#referenceNumber").fill(reference);
        page.locator("#notes").fill(notes);
    }

    private InventoryTransaction latest(Product product) {
        return inventoryTransactionRepository.findByProductId(product.getId()).getFirst();
    }

    private String movement(InventoryTransaction t) {
        return String.join("|", t.getTransactionDate().toString(), t.getTransactionType().name(),
                t.getQuantity().setScale(4).toPlainString(), t.getUnitCost().setScale(4).toPlainString(),
                t.getTotalCost().setScale(2).toPlainString(), t.getBalanceAfter().setScale(4).toPlainString(),
                t.getTotalCostAfter().setScale(2).toPlainString(), t.getReferenceNumber(), t.getNotes(),
                t.getCreatedBy());
    }

    private String layer(InventoryFifoLayer l) {
        return String.join("|", l.getLayerDate().toString(), l.getOriginalQuantity().setScale(4).toPlainString(),
                l.getRemainingQuantity().setScale(4).toPlainString(), l.getUnitCost().setScale(4).toPlainString());
    }

    private void assertBalance(Product product, String quantity, String totalCost, String averageCost) {
        InventoryBalance balance = balanceRepository.findByProductId(product.getId())
                .orElseThrow(() -> new AssertionError("No balance for " + product.getCode()));
        assertEquals(0, new BigDecimal(quantity).compareTo(balance.getQuantity()), "quantity");
        assertEquals(0, new BigDecimal(totalCost).compareTo(balance.getTotalCost()), "total cost");
        assertEquals(0, new BigDecimal(averageCost).compareTo(balance.getAverageCost()), "average cost");
    }

    private void assertJournal(InventoryTransaction movement, String templateName, String amount, String description,
                               List<String> mappings, Map<String, String> variables) {
        UUID id = movement.getTransaction().getId();
        Map<String, Object> journal = jdbcTemplate.queryForMap("""
                SELECT t.status, t.amount, t.description, t.transaction_date, t.reference_number,
                       t.transaction_number, jt.template_name
                FROM transactions t JOIN journal_templates jt ON jt.id = t.id_journal_template
                WHERE t.id = ?""", id);
        assertEquals("DRAFT", journal.get("status"));
        assertNull(journal.get("transaction_number"));
        assertEquals(templateName, journal.get("template_name"));
        assertEquals(0, new BigDecimal(amount).compareTo((BigDecimal) journal.get("amount")));
        assertEquals(description, journal.get("description"));
        assertEquals(movement.getTransactionDate(), ((java.sql.Date) journal.get("transaction_date")).toLocalDate());
        assertEquals(movement.getReferenceNumber(), journal.get("reference_number"));
        assertEquals(mappings, jdbcTemplate.queryForList("""
                SELECT jtl.position || ':' || coa.account_code
                FROM transaction_account_mappings m
                JOIN journal_template_lines jtl ON jtl.id = m.id_template_line
                JOIN chart_of_accounts coa ON coa.id = m.id_account
                WHERE m.id_transaction = ? ORDER BY jtl.line_order""", String.class, id));
        Map<String, String> stored = new TreeMap<>();
        jdbcTemplate.queryForList("SELECT variable_name, variable_value FROM transaction_variables WHERE id_transaction = ?", id)
                .forEach(v -> stored.put((String) v.get("variable_name"),
                        ((BigDecimal) v.get("variable_value")).stripTrailingZeros().toPlainString()));
        assertEquals(new TreeMap<>(variables), stored);
    }

    private List<String> journalEntries(InventoryTransaction movement) {
        return jdbcTemplate.queryForList("""
                SELECT CASE WHEN je.debit_amount > 0 THEN 'DEBIT:' || coa.account_code || ':' || je.debit_amount
                            ELSE 'CREDIT:' || coa.account_code || ':' || je.credit_amount END
                FROM journal_entries je JOIN chart_of_accounts coa ON coa.id = je.id_account
                WHERE je.id_transaction = ? ORDER BY je.journal_number""", String.class,
                movement.getTransaction().getId());
    }

    private Locator info(String label) {
        return page.locator("section h3:text-is('" + label + "') + p");
    }

    private Locator card(String label) {
        return page.locator("section div.text-gray-500:text-is('" + label + "') + div");
    }

    private Locator table(String heading) {
        return page.locator("section div.bg-white:has(> div > h3:text-is('" + heading + "')) table");
    }

    private Locator rows() {
        return page.locator("#transaction-table tbody tr");
    }

    private Locator row(InventoryTransaction transaction) {
        return page.getByTestId("transaction-row-" + transaction.getId());
    }

    private Locator stockRows() {
        return page.locator("#stock-table tbody tr");
    }

    private Locator stockRow(Product product) {
        return page.getByTestId("stock-row-" + product.getCode());
    }

    private FormData movementForm(String csrf, Product product, String quantity) {
        return FormData.create().set("_csrf", csrf).set("productId", product.getId().toString())
                .set("transactionDate", "2050-09-01").set("quantity", quantity);
    }

    private String csrf() {
        return page.locator("meta[name='_csrf']").getAttribute("content");
    }

    private APIResponse postForm(String path, FormData form) {
        return page.request().post(baseUrl() + path, RequestOptions.create().setMaxRedirects(0).setForm(form));
    }

    private ChartOfAccount account(String code) {
        return accountRepository.findByAccountCode(code)
                .orElseThrow(() -> new IllegalStateException("Seeded account missing: " + code));
    }

    private User createUser(Role role) {
        String id = uid();
        User user = new User();
        user.setUsername("inv-user-" + id);
        user.setPassword(passwordEncoder.encode("password"));
        user.setFullName("Pengguna " + id);
        user.setEmail("inv-user-" + id + "@example.com");
        user.setActive(true);
        user.addRole(role, "system");
        return userRepository.save(user);
    }

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
