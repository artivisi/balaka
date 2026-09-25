package com.artivisi.accountingfinance.functional.seller;

import com.artivisi.accountingfinance.entity.InventoryBalance;
import com.artivisi.accountingfinance.entity.InventoryFifoLayer;
import com.artivisi.accountingfinance.entity.InventoryTransaction;
import com.artivisi.accountingfinance.entity.Product;
import com.artivisi.accountingfinance.functional.util.CsvLoader;
import com.artivisi.accountingfinance.functional.util.ExpectedInventoryRow;
import com.artivisi.accountingfinance.functional.util.InventoryTransactionRow;
import com.artivisi.accountingfinance.repository.InventoryBalanceRepository;
import com.artivisi.accountingfinance.repository.InventoryFifoLayerRepository;
import com.artivisi.accountingfinance.repository.InventoryTransactionRepository;
import com.artivisi.accountingfinance.repository.ProductRepository;
import com.artivisi.accountingfinance.service.TransactionService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Online seller inventory flow driven by testdata/seller/transactions.csv: every row is recorded through the
 * purchase/sale/adjustment forms and checked for its costing, stock balance and DRAFT journal; the resulting stock
 * levels are checked against expected-inventory.csv and the four inventory reports. A Tokopedia marketplace sale and
 * its settlement are posted through the transaction form.
 *
 * Runs in its own Spring context (own database). CSV month/day are kept, the year is moved to 2077.
 */
@DisplayName("Online Seller - Transaction Execution")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Import(SellerTestDataInitializer.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = "test.context.key=seller-transaction-execution")
class SellerTransactionExecutionTest extends PlaywrightTestBase {

    private static final int YEAR = 2077;
    private static final Pattern INVENTORY_DETAIL_URL = Pattern.compile(".*/inventory/transactions/([0-9a-f-]{36})$");
    private static final Pattern TRANSACTION_DETAIL_URL = Pattern.compile(".*/transactions/([0-9a-f-]{36})$");
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.of("id", "ID"));
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Autowired
    private SellerTestDataHelper testDataHelper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryTransactionRepository inventoryTransactionRepository;

    @Autowired
    private InventoryBalanceRepository balanceRepository;

    @Autowired
    private InventoryFifoLayerRepository fifoLayerRepository;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Map<Integer, UUID> movementIds = new HashMap<>();

    @BeforeAll
    void setupProducts() {
        testDataHelper.setupProductsAndCategories();
    }

    @Test
    @Order(1)
    @DisplayName("Should list the four seller products with category, unit, selling price and inventory accounts")
    void shouldListSellerProducts() {
        loginAsAdmin();
        navigateTo("/products");
        waitForPageLoad();
        takeManualScreenshot("seller/product-list");
        assertThat(page.locator("#page-title")).hasText("Daftar Produk");

        assertProduct("IP15PRO", "iPhone 15 Pro", "Smartphone", "19,000,000", "FIFO");
        assertProduct("SGS24", "Samsung Galaxy S24", "Smartphone", "14,000,000", "FIFO");
        assertProduct("USBC", "USB Cable Type-C 1M", "Accessories", "50,000", "WEIGHTED_AVERAGE");
        assertProduct("CASE", "Phone Case Silicone", "Accessories", "35,000", "WEIGHTED_AVERAGE");
    }

    @TestFactory
    @Order(2)
    @DisplayName("Should record each CSV movement through its form at the expected cost, balance and DRAFT journal")
    Stream<DynamicTest> shouldRecordCsvMovements() {
        List<InventoryTransactionRow> rows = CsvLoader.loadInventoryTransactions("seller/transactions.csv");
        assertEquals(9, rows.size(), "seller/transactions.csv rows");

        Map<String, BigDecimal> quantities = new HashMap<>();
        Map<String, BigDecimal> values = new HashMap<>();
        List<DynamicTest> tests = new ArrayList<>();
        for (InventoryTransactionRow row : rows) {
            BigDecimal quantity = BigDecimal.valueOf(row.quantity());
            BigDecimal totalCost = quantity.multiply(row.unitCost());
            boolean inbound = !row.transactionType().equals("SALE");
            BigDecimal balanceAfter = quantities.merge(row.productCode(), inbound ? quantity : quantity.negate(), BigDecimal::add);
            BigDecimal valueAfter = values.merge(row.productCode(), inbound ? totalCost : totalCost.negate(), BigDecimal::add);
            tests.add(DynamicTest.dynamicTest(
                    "Tx " + row.sequence() + ": " + row.transactionType() + " " + row.productCode(),
                    () -> recordMovement(row, totalCost, balanceAfter, valueAfter)));
        }
        return tests.stream();
    }

    @TestFactory
    @Order(3)
    @DisplayName("Should show the expected stock level, average cost and value per product on the stock list")
    Stream<DynamicTest> shouldShowExpectedStockLevels() {
        List<ExpectedInventoryRow> rows = CsvLoader.loadExpectedInventory("seller/expected-inventory.csv");
        assertEquals(4, rows.size(), "seller/expected-inventory.csv rows");
        return rows.stream().map(row -> DynamicTest.dynamicTest("Stock: " + row.productCode(), () -> {
            BigDecimal value = row.expectedQuantity().multiply(row.expectedAverageCost());
            InventoryBalance balance = balanceRepository.findByProductId(product(row.productCode()).getId())
                    .orElseThrow(() -> new AssertionError("No balance for " + row.productCode()));
            assertEquals(0, row.expectedQuantity().compareTo(balance.getQuantity()), "quantity " + row.productCode());
            assertEquals(0, row.expectedAverageCost().compareTo(balance.getAverageCost()), "average cost " + row.productCode());
            assertEquals(0, value.compareTo(balance.getTotalCost()), "total cost " + row.productCode());

            loginAsAdmin();
            navigateTo("/inventory/stock?search=" + row.productCode());
            waitForPageLoad();
            assertThat(page.locator("#page-title")).containsText("Stok");
            Locator stockRow = page.getByTestId("stock-row-" + row.productCode());
            assertThat(page.getByTestId("stock-product-name-" + row.productCode())).hasText(row.productName());
            assertThat(page.getByTestId("stock-quantity-" + row.productCode())).hasText(decimal(row.expectedQuantity(), 2));
            assertThat(stockRow.locator("td").nth(4)).hasText(decimal(row.expectedAverageCost(), 2));
            assertThat(stockRow.locator("td").nth(5)).hasText(decimal(value, 0));
        }));
    }

    @Test
    @Order(4)
    @DisplayName("Should keep the unsold FIFO layers and post the stock-opname draft to Persediaan / Pendapatan Lain-lain")
    void shouldKeepFifoLayersAndPostAdjustmentDraft() {
        assertEquals(List.of("2077-01-05|10.0000|5.0000|15000000.0000"), layers("IP15PRO"));
        assertEquals(List.of("2077-01-08|20.0000|12.0000|12000000.0000"), layers("SGS24"));

        InventoryTransaction purchase = movement(1);
        loginAsAdmin();
        navigateTo("/transactions/" + purchase.getTransaction().getId());
        waitForPageLoad();
        assertThat(page.getByTestId("status-draft")).hasText("Transaksi dalam Draft");
        assertThat(page.getByTestId("template-link")).hasText("Pembelian Persediaan");

        InventoryTransaction adjustment = movement(9);
        UUID journalId = adjustment.getTransaction().getId();
        transactionService.post(journalId, "admin");
        assertEquals(List.of("DEBIT:1.1.20:125000.00", "CREDIT:4.2.02:125000.00"), journalLines(journalId));

        navigateTo("/transactions/" + journalId);
        waitForPageLoad();
        assertThat(page.getByTestId("status-posted")).hasText("Transaksi Berhasil Diposting");
        assertThat(page.locator("#transaction-number")).hasText(transactionNumber(journalId));
        assertEquals(List.of("1.1.20|Rp 125.000|-", "4.2.02|-|Rp 125.000"), journalRows());
        assertThat(page.locator("#total-debit")).hasText("Rp 125.000");
        assertThat(page.locator("#total-credit")).hasText("Rp 125.000");
    }

    @Test
    @Order(5)
    @DisplayName("Should show quantity, average cost and value per product on the stock balance report")
    void shouldShowStockBalanceReport() {
        loginAsAdmin();
        navigateTo("/inventory/reports/stock-balance");
        waitForPageLoad();
        takeManualScreenshot("seller/report-stock-balance");
        assertThat(page.locator("#page-title")).hasText("Laporan Saldo Stok");

        assertReportRow("IP15PRO", 1, "iPhone 15 Pro", "Smartphone", "5.00", "pcs", "15,000,000", "75,000,000", "Normal");
        assertReportRow("SGS24", 1, "Samsung Galaxy S24", "Smartphone", "12.00", "pcs", "12,000,000", "144,000,000", "Normal");
        assertReportRow("USBC", 1, "USB Cable Type-C 1M", "Accessories", "75.00", "pcs", "25,000", "1,875,000", "Normal");
        assertReportRow("CASE", 1, "Phone Case Silicone", "Accessories", "150.00", "pcs", "15,000", "2,250,000", "Normal");
    }

    @Test
    @Order(6)
    @DisplayName("Should list every CSV movement of January 2077 on the stock movement report with inbound and outbound totals")
    void shouldShowStockMovementReport() {
        loginAsAdmin();
        navigateTo("/inventory/reports/stock-movement?startDate=2077-01-01&endDate=2077-01-31");
        waitForPageLoad();
        takeManualScreenshot("seller/report-stock-movement");
        assertThat(page.locator("#page-title")).hasText("Laporan Mutasi Stok");

        assertThat(page.locator("#report-table tbody tr")).hasCount(9);
        assertThat(summary("Total Transaksi")).hasText("9");
        assertThat(summary("Total Masuk")).hasText("Rp 395,625,000");
        assertThat(summary("Total Keluar")).hasText("Rp 172,500,000");
        assertThat(summary("Selisih")).hasText("Rp 223,125,000");

        assertMovementRow("PO-001", "05/01/2077", "IP15PRO", "iPhone 15 Pro", "Pembelian", "10.00", "15,000,000", "150,000,000", "10.00");
        assertMovementRow("PO-002", "08/01/2077", "SGS24", "Samsung Galaxy S24", "Pembelian", "20.00", "12,000,000", "240,000,000", "20.00");
        assertMovementRow("PO-003", "10/01/2077", "USBC", "USB Cable Type-C 1M", "Pembelian", "100.00", "25,000", "2,500,000", "100.00");
        assertMovementRow("PO-004", "10/01/2077", "CASE", "Phone Case Silicone", "Pembelian", "200.00", "15,000", "3,000,000", "200.00");
        assertMovementRow("TOPED-001", "15/01/2077", "IP15PRO", "iPhone 15 Pro", "Penjualan", "5.00", "15,000,000", "75,000,000", "5.00");
        assertMovementRow("SHOPEE-001", "20/01/2077", "SGS24", "Samsung Galaxy S24", "Penjualan", "8.00", "12,000,000", "96,000,000", "12.00");
        assertMovementRow("TOPED-002", "22/01/2077", "USBC", "USB Cable Type-C 1M", "Penjualan", "30.00", "25,000", "750,000", "70.00");
        assertMovementRow("SHOPEE-002", "25/01/2077", "CASE", "Phone Case Silicone", "Penjualan", "50.00", "15,000", "750,000", "150.00");
        assertMovementRow("SO-001", "28/01/2077", "USBC", "USB Cable Type-C 1M", "Penyesuaian Masuk", "5.00", "25,000", "125,000", "75.00");
    }

    @Test
    @Order(7)
    @DisplayName("Should value each product by its costing method on the inventory valuation report")
    void shouldShowValuationReport() {
        loginAsAdmin();
        navigateTo("/inventory/reports/valuation");
        waitForPageLoad();
        takeManualScreenshot("seller/report-inventory-valuation");
        assertThat(page.locator("#page-title")).hasText("Laporan Penilaian Persediaan");

        assertReportRow("IP15PRO", 1, "iPhone 15 Pro", "Smartphone", "5.00", "pcs", "FIFO", "15,000,000", "75,000,000");
        assertReportRow("SGS24", 1, "Samsung Galaxy S24", "Smartphone", "12.00", "pcs", "FIFO", "12,000,000", "144,000,000");
        assertReportRow("USBC", 1, "USB Cable Type-C 1M", "Accessories", "75.00", "pcs", "Rata-rata", "25,000", "1,875,000");
        assertReportRow("CASE", 1, "Phone Case Silicone", "Accessories", "150.00", "pcs", "Rata-rata", "15,000", "2,250,000");
    }

    @Test
    @Order(8)
    @DisplayName("Should show revenue, COGS and margin of the January 2077 sales per product on the profitability report")
    void shouldShowProfitabilityReport() {
        loginAsAdmin();
        navigateTo("/inventory/reports/profitability?startDate=2077-01-01&endDate=2077-01-31");
        waitForPageLoad();
        takeManualScreenshot("seller/report-product-profitability");
        assertThat(page.locator("#page-title")).hasText("Laporan Profitabilitas Produk");

        assertThat(page.locator("#report-table tbody tr")).hasCount(4);
        assertThat(page.locator("#total-revenue")).hasText("Rp 210,250,000");
        assertThat(page.locator("#total-cogs")).hasText("Rp 172,500,000");
        assertThat(page.locator("#total-gross-profit")).hasText("Rp 37,750,000");
        assertThat(page.locator("#gross-margin-percent")).hasText("18.0%");

        assertReportRow("IP15PRO", 0, "iPhone 15 Pro", "Smartphone", "5.00 pcs", "Rp 95,000,000", "Rp 75,000,000", "Rp 20,000,000", "21.1%", "1");
        assertReportRow("SGS24", 0, "Samsung Galaxy S24", "Smartphone", "8.00 pcs", "Rp 112,000,000", "Rp 96,000,000", "Rp 16,000,000", "14.3%", "1");
        assertReportRow("USBC", 0, "USB Cable Type-C 1M", "Accessories", "30.00 pcs", "Rp 1,500,000", "Rp 750,000", "Rp 750,000", "50.0%", "1");
        assertReportRow("CASE", 0, "Phone Case Silicone", "Accessories", "50.00 pcs", "Rp 1,750,000", "Rp 750,000", "Rp 1,000,000", "57.1%", "1");
    }

    @Test
    @Order(9)
    @DisplayName("Should post a Tokopedia sale net of the admin fee and settle the Tokopedia balance to Bank BCA")
    void shouldPostTokopediaSaleAndSettlement() {
        loginAsAdmin();
        navigateTo("/templates");
        waitForPageLoad();
        takeManualScreenshot("seller/templates-list");

        UUID saleTemplate = templateId("Penjualan Tokopedia");
        navigateTo("/templates/" + saleTemplate);
        waitForPageLoad();
        takeManualScreenshot("seller/templates-detail");
        assertThat(page.locator("#template-name")).hasText("Penjualan Tokopedia");
        Locator lines = page.getByTestId("template-line");
        assertThat(lines).hasCount(3);
        assertTemplateLine(lines.nth(0), "1.1.04", "Debit", "grossSales - adminFee");
        assertTemplateLine(lines.nth(1), "5.2.01", "Debit", "adminFee");
        assertTemplateLine(lines.nth(2), "4.1.01", "Kredit", "grossSales");

        navigateTo("/transactions/new?templateId=" + saleTemplate);
        waitForPageLoad();
        page.locator("#transactionDate").fill("2077-02-15");
        page.locator("#var_grossSales").fill("19000000");
        page.locator("#var_adminFee").fill("570000");
        page.locator("#description").fill("Penjualan iPhone via Tokopedia 2077");
        page.locator("#referenceNumber").fill("TOPED-2077-001");
        page.locator("#btn-simpan-posting").click();
        UUID saleId = waitForTransactionDetail();

        assertPosted(saleId, "Penjualan Tokopedia", "2077-02-15", "TOPED-2077-001");
        assertEquals(List.of("DEBIT:1.1.04:18430000.00", "DEBIT:5.2.01:570000.00", "CREDIT:4.1.01:19000000.00"),
                journalLines(saleId));
        assertEquals(List.of("1.1.04|Rp 18.430.000|-", "4.1.01|-|Rp 19.000.000", "5.2.01|Rp 570.000|-"), journalRows());
        assertThat(page.locator("#total-debit")).hasText("Rp 19.000.000");
        assertThat(page.locator("#total-credit")).hasText("Rp 19.000.000");

        UUID withdrawTemplate = templateId("Withdraw Saldo Tokopedia");
        navigateTo("/transactions/new?templateId=" + withdrawTemplate);
        waitForPageLoad();
        page.locator("#transactionDate").fill("2077-02-20");
        page.locator("#amount").fill("18430000");
        page.locator("#description").fill("Pencairan saldo Tokopedia 2077");
        page.locator("#referenceNumber").fill("WD-TOPED-2077-001");
        pickAccount(bankLine(withdrawTemplate), "1.1.02");
        page.locator("#btn-simpan-posting").click();
        UUID withdrawId = waitForTransactionDetail();

        assertPosted(withdrawId, "Withdraw Saldo Tokopedia", "2077-02-20", "WD-TOPED-2077-001");
        assertEquals(List.of("DEBIT:1.1.02:18430000.00", "CREDIT:1.1.04:18430000.00"), journalLines(withdrawId));
        assertEquals(List.of("1.1.02|Rp 18.430.000|-", "1.1.04|-|Rp 18.430.000"), journalRows());

        assertEquals(0, BigDecimal.ZERO.compareTo(jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(je.debit_amount - je.credit_amount), 0)
                FROM journal_entries je
                JOIN chart_of_accounts coa ON coa.id = je.id_account
                JOIN transactions t ON t.id = je.id_transaction
                WHERE coa.account_code = '1.1.04' AND t.status = 'POSTED'
                  AND t.transaction_date BETWEEN '2077-01-01' AND '2077-12-31'""", BigDecimal.class)),
                "Saldo Tokopedia after settlement");
    }

    // ==================== movement recording ====================

    private void recordMovement(InventoryTransactionRow row, BigDecimal totalCost, BigDecimal balanceAfter,
                                BigDecimal valueAfter) {
        Product product = product(row.productCode());
        LocalDate date = LocalDate.parse(row.date()).withYear(YEAR);
        String type = row.transactionType();
        boolean sale = type.equals("SALE");
        loginAsAdmin();

        navigateTo(switch (type) {
            case "PURCHASE" -> "/inventory/purchase";
            case "SALE" -> "/inventory/sale";
            case "ADJUSTMENT_IN" -> "/inventory/adjustment";
            default -> throw new IllegalArgumentException("Unsupported transaction type in CSV: " + type);
        });
        waitForPageLoad();
        if (type.equals("ADJUSTMENT_IN")) {
            page.locator("input[name='inbound'][value='true']").check();
        }
        page.locator("#productId").selectOption(product.getId().toString());
        page.locator("#transactionDate").fill(date.toString());
        page.locator("#quantity").fill(String.valueOf(row.quantity()));
        if (sale) {
            page.locator("#unitPrice").fill(row.unitPrice().toPlainString());
        } else {
            page.locator("#unitCost").fill(row.unitCost().toPlainString());
        }
        page.locator("#referenceNumber").fill(row.reference());
        page.locator("#notes").fill(row.notes());
        if (row.screenshot()) {
            takeManualScreenshot("seller/inv-" + String.format("%02d", row.sequence()) + "-" + type.toLowerCase() + "-form");
        }
        page.locator("#btn-submit").click();
        page.waitForURL(INVENTORY_DETAIL_URL);
        waitForPageLoad();
        Matcher matcher = INVENTORY_DETAIL_URL.matcher(page.url());
        assertTrue(matcher.matches(), page.url());
        UUID id = UUID.fromString(matcher.group(1));
        movementIds.put(row.sequence(), id);
        if (row.screenshot()) {
            takeManualScreenshot("seller/inv-" + String.format("%02d", row.sequence()) + "-" + type.toLowerCase() + "-result");
        }

        String typeLabel = switch (type) {
            case "PURCHASE" -> "Pembelian";
            case "SALE" -> "Penjualan";
            default -> "Penyesuaian Masuk";
        };
        assertThat(page.getByTestId("flash-success")).hasText(switch (type) {
            case "PURCHASE" -> "Pembelian berhasil dicatat";
            case "SALE" -> "Penjualan berhasil dicatat";
            default -> "Penyesuaian berhasil dicatat";
        });
        assertThat(page.locator("#page-title")).hasText("Detail Transaksi Persediaan");
        assertThat(page.getByTestId("transaction-type")).hasText(typeLabel);
        assertThat(info("Tanggal Transaksi")).hasText(date.format(LONG_DATE));
        assertThat(info("Kuantitas")).hasText((sale ? "-" : "+") + decimal(BigDecimal.valueOf(row.quantity()), 2) + " pcs");
        assertThat(info("Harga Satuan")).hasText("Rp " + decimal(row.unitCost(), 2));
        assertThat(info("Total Biaya")).hasText("Rp " + decimal(totalCost, 0));
        assertThat(info("Nomor Referensi")).hasText(row.reference());
        assertThat(info("Saldo Setelah Transaksi")).hasText(decimal(balanceAfter, 2) + " pcs");
        assertThat(info("Catatan")).hasText(row.notes());
        BigDecimal revenue = BigDecimal.valueOf(row.quantity()).multiply(row.unitPrice());
        if (sale) {
            BigDecimal margin = revenue.subtract(totalCost);
            assertThat(info("Harga Jual")).hasText("Rp " + decimal(row.unitPrice(), 2));
            assertThat(info("Total Pendapatan")).hasText("Rp " + decimal(revenue, 0));
            assertThat(info("Margin")).hasText("Rp " + decimal(margin, 0) + " ("
                    + margin.multiply(BigDecimal.valueOf(100)).divide(revenue, 1, RoundingMode.HALF_UP).toPlainString() + "%)");
        } else {
            assertThat(info("Harga Jual")).hasCount(0);
        }

        InventoryTransaction movement = movement(row.sequence());
        assertEquals(String.join("|", date.toString(), type, scaled(BigDecimal.valueOf(row.quantity()), 4),
                        scaled(row.unitCost(), 4), scaled(totalCost, 2), scaled(balanceAfter, 4), scaled(valueAfter, 2),
                        row.reference(), row.notes(), "admin"),
                String.join("|", movement.getTransactionDate().toString(), movement.getTransactionType().name(),
                        scaled(movement.getQuantity(), 4), scaled(movement.getUnitCost(), 4),
                        scaled(movement.getTotalCost(), 2), scaled(movement.getBalanceAfter(), 4),
                        scaled(movement.getTotalCostAfter(), 2), movement.getReferenceNumber(), movement.getNotes(),
                        movement.getCreatedBy()));

        String description = typeLabel + " " + row.productCode() + " - " + row.quantity() + " x pcs";
        Map<String, String> variables = new TreeMap<>(Map.of(
                "amount", totalCost.toPlainString(), "cogsAmount", totalCost.toPlainString()));
        if (sale) {
            variables.put("revenueAmount", revenue.stripTrailingZeros().toPlainString());
        }
        assertDraftJournal(movement, switch (type) {
            case "PURCHASE" -> "Pembelian Persediaan";
            case "SALE" -> "Penjualan Persediaan";
            default -> "Penyesuaian Persediaan Masuk";
        }, totalCost, description, switch (type) {
            case "PURCHASE" -> List.of("DEBIT:1.1.20");
            case "SALE" -> List.of("DEBIT:5.1.01", "CREDIT:4.1.01", "CREDIT:1.1.20");
            default -> List.of();
        }, variables);
    }

    private void assertDraftJournal(InventoryTransaction movement, String templateName, BigDecimal amount,
                                    String description, List<String> mappings, Map<String, String> variables) {
        assertNotNull(movement.getTransaction(), "journal for movement " + movement.getReferenceNumber());
        UUID id = movement.getTransaction().getId();
        Map<String, Object> journal = jdbcTemplate.queryForMap("""
                SELECT t.status, t.amount, t.description, t.transaction_date, t.reference_number,
                       t.transaction_number, jt.template_name
                FROM transactions t JOIN journal_templates jt ON jt.id = t.id_journal_template
                WHERE t.id = ?""", id);
        assertEquals("DRAFT", journal.get("status"));
        assertNull(journal.get("transaction_number"));
        assertEquals(templateName, journal.get("template_name"));
        assertEquals(0, amount.compareTo((BigDecimal) journal.get("amount")));
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
        Map<String, String> expected = new TreeMap<>();
        variables.forEach((k, v) -> expected.put(k, new BigDecimal(v).stripTrailingZeros().toPlainString()));
        assertEquals(expected, stored);
        assertEquals(List.of(), journalLines(id), "DRAFT journal has no entries yet");
    }

    // ==================== helpers ====================

    private void assertProduct(String code, String name, String category, String price, String costingMethod) {
        navigateTo("/products?search=" + code);
        waitForPageLoad();
        assertThat(page.getByTestId("product-name-" + code)).hasText(name);
        assertThat(page.getByTestId("product-category-" + code)).hasText(category);
        assertThat(page.getByTestId("product-unit-" + code)).hasText("pcs");
        assertThat(page.getByTestId("product-price-" + code)).hasText(price);
        assertThat(page.getByTestId("product-status-" + code)).hasText("Aktif");
        assertEquals(costingMethod + "|1.1.20|5.1.01|4.1.01", jdbcTemplate.queryForObject("""
                SELECT p.costing_method || '|' || ia.account_code || '|' || ca.account_code || '|' || sa.account_code
                FROM products p
                JOIN chart_of_accounts ia ON ia.id = p.id_inventory_account
                JOIN chart_of_accounts ca ON ca.id = p.id_cogs_account
                JOIN chart_of_accounts sa ON sa.id = p.id_sales_account
                WHERE p.code = ?""", String.class, code));
    }

    private void assertReportRow(String code, int codeColumn, String... cells) {
        Locator row = page.locator("#report-table tbody tr")
                .filter(new Locator.FilterOptions().setHas(page.locator("td:nth-child(" + (codeColumn + 1) + "):text-is('" + code + "')")));
        assertThat(row).hasCount(1);
        Locator tds = row.locator("td");
        for (int i = 0; i < cells.length; i++) {
            assertThat(tds.nth(codeColumn + 1 + i)).hasText(cells[i]);
        }
    }

    private void assertMovementRow(String reference, String date, String code, String name, String type,
                                   String quantity, String unitCost, String total, String balance) {
        Locator row = page.locator("#report-table tbody tr")
                .filter(new Locator.FilterOptions().setHas(page.locator("td:nth-child(8):text-is('" + reference + "')")));
        assertThat(row).hasCount(1);
        Locator tds = row.locator("td");
        String[] expected = {date, code, name, type, quantity, unitCost, total, reference, balance};
        for (int i = 0; i < expected.length; i++) {
            assertThat(tds.nth(i)).hasText(expected[i]);
        }
    }

    private void assertTemplateLine(Locator line, String accountCode, String position, String formula) {
        assertThat(line.locator("span.font-mono")).hasText(accountCode);
        assertThat(line.locator("[id^='position-badge-']")).hasText(position);
        assertThat(line.locator("code")).hasText(formula);
    }

    private void assertPosted(UUID id, String templateName, String date, String reference) {
        Map<String, Object> journal = jdbcTemplate.queryForMap("""
                SELECT t.status, t.transaction_date, t.reference_number, t.transaction_number, jt.template_name
                FROM transactions t JOIN journal_templates jt ON jt.id = t.id_journal_template
                WHERE t.id = ?""", id);
        assertEquals("POSTED", journal.get("status"));
        assertEquals(templateName, journal.get("template_name"));
        assertEquals(LocalDate.parse(date), ((java.sql.Date) journal.get("transaction_date")).toLocalDate());
        assertEquals(reference, journal.get("reference_number"));
        assertNotNull(journal.get("transaction_number"));
        assertThat(page.getByTestId("status-posted")).hasText("Transaksi Berhasil Diposting");
        assertThat(page.locator("#transaction-number")).hasText((String) journal.get("transaction_number"));
        assertThat(page.getByTestId("template-link")).hasText(templateName);
    }

    private UUID waitForTransactionDetail() {
        page.waitForURL(TRANSACTION_DETAIL_URL);
        waitForPageLoad();
        Matcher matcher = TRANSACTION_DETAIL_URL.matcher(page.url());
        assertTrue(matcher.matches(), page.url());
        return UUID.fromString(matcher.group(1));
    }

    private void pickAccount(UUID lineId, String code) {
        Locator input = page.locator("#accountMapping_" + lineId);
        input.click();
        input.fill(code);
        input.locator("xpath=..").locator("[data-testid='account-picker-result']")
                .filter(new Locator.FilterOptions().setHasText(code + " — ")).click();
        assertThat(page.locator("input[name='accountMapping[" + lineId + "]']")).hasValue(jdbcTemplate.queryForObject(
                "SELECT id::text FROM chart_of_accounts WHERE account_code = ?", String.class, code));
    }

    private UUID templateId(String name) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM journal_templates WHERE template_name = ? AND is_current_version = true", UUID.class, name);
    }

    private UUID bankLine(UUID templateId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM journal_template_lines WHERE id_journal_template = ? AND account_hint = 'BANK'",
                UUID.class, templateId);
    }

    private List<String> journalLines(UUID transactionId) {
        return jdbcTemplate.queryForList("""
                SELECT CASE WHEN je.debit_amount > 0 THEN 'DEBIT:' || coa.account_code || ':' || je.debit_amount
                            ELSE 'CREDIT:' || coa.account_code || ':' || je.credit_amount END
                FROM journal_entries je JOIN chart_of_accounts coa ON coa.id = je.id_account
                WHERE je.id_transaction = ? ORDER BY je.debit_amount = 0, coa.account_code""", String.class, transactionId);
    }

    private List<String> journalRows() {
        Locator rows = page.locator(".journal-entry-row");
        List<String> result = new ArrayList<>();
        for (int i = 0; i < rows.count(); i++) {
            Locator cells = rows.nth(i).locator("> div");
            result.add(page.locator("#entry-account-code-" + i).textContent().trim() + "|"
                    + cells.nth(1).textContent().trim() + "|" + cells.nth(2).textContent().trim());
        }
        return result.stream().sorted().toList();
    }

    private String transactionNumber(UUID id) {
        return jdbcTemplate.queryForObject("SELECT transaction_number FROM transactions WHERE id = ?", String.class, id);
    }

    private List<String> layers(String productCode) {
        return fifoLayerRepository.findByProductId(product(productCode).getId()).stream()
                .map((InventoryFifoLayer l) -> String.join("|", l.getLayerDate().toString(),
                        scaled(l.getOriginalQuantity(), 4), scaled(l.getRemainingQuantity(), 4), scaled(l.getUnitCost(), 4)))
                .toList();
    }

    private InventoryTransaction movement(int sequence) {
        UUID id = movementIds.get(sequence);
        assertNotNull(id, "CSV movement " + sequence + " was not recorded");
        return inventoryTransactionRepository.findById(id)
                .orElseThrow(() -> new AssertionError("Inventory transaction missing: " + id));
    }

    private Product product(String code) {
        return productRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Seller test product missing: " + code));
    }

    private Locator info(String label) {
        return page.locator("section h3:text-is('" + label + "') + p");
    }

    private Locator summary(String label) {
        return page.locator("div.text-sm.text-gray-500:text-is('" + label + "') + div");
    }

    private static String decimal(BigDecimal value, int scale) {
        return String.format(Locale.US, "%,." + scale + "f", value);
    }

    private static String scaled(BigDecimal value, int scale) {
        return value.setScale(scale, RoundingMode.UNNECESSARY).toPlainString();
    }
}
