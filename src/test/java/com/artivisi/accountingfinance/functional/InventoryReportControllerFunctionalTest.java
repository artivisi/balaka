package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.CostingMethod;
import com.artivisi.accountingfinance.entity.Product;
import com.artivisi.accountingfinance.entity.ProductCategory;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.functional.manufacturing.CoffeeTestDataInitializer;
import com.artivisi.accountingfinance.repository.ProductCategoryRepository;
import com.artivisi.accountingfinance.repository.ProductRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.service.InventoryService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * InventoryReportController: stock balance, stock movement, valuation and product profitability pages with their
 * filters, print views and PDF/Excel exports, and access control.
 *
 * Each test builds its own category (see {@link #fixture()}) with movements in March 2049, and asserts exact rows by
 * filtering on that category.
 */
@DisplayName("Inventory Report Controller Tests")
@Import(CoffeeTestDataInitializer.class)
class InventoryReportControllerFunctionalTest extends PlaywrightTestBase {

    private static final String MARCH_START = "2049-03-01";
    private static final String MARCH_END = "2049-03-31";
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductCategoryRepository categoryRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ==================== INDEX ====================

    @Test
    @DisplayName("Should open the reports index from the sidebar and link to the four reports")
    void shouldShowReportsIndex() {
        loginAsAdmin();
        navigateTo("/dashboard");
        waitForPageLoad();
        page.locator("#nav-group-inventori").click();
        page.locator("#nav-inventory-reports").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/inventory/reports");
        assertThat(page.locator("#page-title")).hasText("Laporan Persediaan");
        assertThat(page.locator("#nav-inventory-reports")).hasClass(java.util.regex.Pattern.compile("(^|.* )bg-primary-600( .*|$)"));
        assertThat(page.locator("section a h3")).hasText(new String[]{
                "Saldo Stok", "Mutasi Stok", "Penilaian Persediaan", "Profitabilitas Produk"});
        assertThat(page.locator("section a:has(h3)")).hasCount(4);
        List<String> hrefs = List.of("/inventory/reports/stock-balance", "/inventory/reports/stock-movement",
                "/inventory/reports/valuation", "/inventory/reports/profitability");
        for (int i = 0; i < hrefs.size(); i++) {
            assertThat(page.locator("section a:has(h3)").nth(i)).hasAttribute("href", hrefs.get(i));
        }

        page.locator("section a:has(h3:text-is('Mutasi Stok'))").click();
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/inventory/reports/stock-movement");
        assertThat(page.locator("#page-title")).hasText("Laporan Mutasi Stok");
    }

    // ==================== STOCK BALANCE ====================

    @Test
    @DisplayName("Should show stock balances of a category with low-stock status, search within it, print and export")
    void shouldReportStockBalance() {
        Fixture f = fixture();
        loginAsAdmin();

        navigateTo("/inventory/reports/stock-balance");
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Laporan Saldo Stok");
        assertThat(page.locator("section p.text-gray-600")).hasText("Per tanggal " + today());
        page.locator("#categoryId").selectOption(f.category.getId().toString());
        page.locator("button:text-is('Tampilkan')").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/inventory/reports/stock-balance?categoryId=" + f.category.getId() + "&search=");
        assertThat(page.locator("#categoryId")).hasValue(f.category.getId().toString());
        Locator rows = page.locator("#report-table tbody tr");
        assertThat(rows).hasCount(3);
        assertThat(rows.nth(0).locator("td")).hasText(new String[]{"1", f.average.getCode(), f.average.getName(),
                f.category.getName(), "8.00", "kg", "11,000", "88,000", "Normal"});
        assertThat(rows.nth(1).locator("td")).hasText(new String[]{"2", f.fifo.getCode(), f.fifo.getName(),
                f.category.getName(), "5.00", "pcs", "8,000", "40,000", "Stok Rendah"});
        assertThat(rows.nth(2).locator("td")).hasText(new String[]{"3", f.soldOut.getCode(), f.soldOut.getName(),
                f.category.getName(), "0.00", "kg", "0", "0", "Normal"});
        assertThat(page.locator("#total-product-count")).hasText("3");
        assertThat(page.locator("#total-stock-value")).hasText("Rp 128,000");
        assertThat(page.locator("#report-table tfoot td")).hasText(new String[]{"Total:", "128,000", ""});

        page.locator("#product-search").fill(f.fifo.getName().toLowerCase());
        page.locator("button:text-is('Tampilkan')").click();
        waitForPageLoad();
        assertThat(rows).hasCount(1);
        assertThat(rows.nth(0).locator("td").nth(1)).hasText(f.fifo.getCode());
        assertThat(page.locator("#product-search")).hasValue(f.fifo.getName().toLowerCase());
        String query = "categoryId=" + f.category.getId() + "&search=" + f.fifo.getName().toLowerCase().replace(" ", "%20");
        assertThat(page.locator("a:text-is('PDF')")).hasAttribute("href", "/inventory/reports/stock-balance/export/pdf?" + query);
        assertThat(page.locator("a:text-is('Excel')")).hasAttribute("href", "/inventory/reports/stock-balance/export/excel?" + query);
        assertThat(page.locator("a:text-is('Cetak')")).hasAttribute("href", "/inventory/reports/stock-balance/print?" + query);

        navigateTo("/inventory/reports/stock-balance?search=" + f.id.toLowerCase());
        waitForPageLoad();
        assertThat(page.locator("#report-table tbody td:nth-child(2)"))
                .hasText(new String[]{f.average.getCode(), f.fifo.getCode(), f.soldOut.getCode()});

        navigateTo("/inventory/reports/stock-balance/print?categoryId=" + f.category.getId());
        waitForPageLoad();
        assertThat(page.locator("h1")).hasText("LAPORAN SALDO STOK");
        assertThat(page.locator("tbody tr").nth(1).locator("td")).hasText(new String[]{"2", f.fifo.getCode(),
                f.fifo.getName(), f.category.getName(), "5.00", "pcs", "8,000", "40,000", "Stok Rendah"});
        assertThat(page.locator("tfoot td")).hasText(new String[]{"Total:", "128,000", ""});

        String stamp = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String pdf = pdfText(export("/inventory/reports/stock-balance/export/pdf?categoryId=" + f.category.getId(),
                "application/pdf", "saldo-stok-" + stamp + ".pdf"));
        assertTrue(pdf.contains("LAPORAN SALDO STOK"), pdf);
        assertTrue(pdf.contains(f.average.getCode()) && pdf.contains(f.fifo.getCode()), pdf);
        assertTrue(pdf.contains("88.000") && pdf.contains("40.000") && pdf.contains("128.000"), pdf);
        assertFalse(pdf.contains(f.inactive.getCode()), pdf);

        Sheet sheet = sheet(export("/inventory/reports/stock-balance/export/excel?categoryId=" + f.category.getId(),
                XLSX, "saldo-stok-" + stamp + ".xlsx"), "Saldo Stok");
        assertEquals(List.of(f.average.getCode() + "|" + f.average.getName() + "|" + f.category.getName() + "|kg|8|11000|88000",
                f.fifo.getCode() + "|" + f.fifo.getName() + "|" + f.category.getName() + "|pcs|5|8000|40000",
                f.soldOut.getCode() + "|" + f.soldOut.getName() + "|" + f.category.getName() + "|kg|||"),
                dataRows(sheet, 1, 7));
        assertEquals(128000, rowWith(sheet, 4, "TOTAL").orElseThrow().getCell(7).getNumericCellValue());
    }

    // ==================== STOCK MOVEMENT ====================

    @Test
    @DisplayName("Should list a category's movements for a period with inbound/outbound totals, filter by product and dates, print and export")
    void shouldReportStockMovement() {
        Fixture f = fixture();
        loginAsAdmin();

        navigateTo("/inventory/reports/stock-movement");
        waitForPageLoad();
        assertThat(page.locator("#start-date")).hasValue(LocalDate.now().withDayOfMonth(1).toString());
        assertThat(page.locator("#end-date")).hasValue(LocalDate.now().toString());
        page.locator("#start-date").fill(MARCH_START);
        page.locator("#end-date").fill(MARCH_END);
        page.locator("#categoryId").selectOption(f.category.getId().toString());
        page.locator("button:text-is('Tampilkan')").click();
        waitForPageLoad();

        assertThat(page.locator("section p.text-gray-600")).hasText("Periode 01 Mar 2049 - 31 Mar 2049");
        Locator rows = page.locator("#report-table tbody tr");
        assertThat(rows).hasCount(10);
        assertThat(rows.nth(0).locator("td")).hasText(new String[]{"02/03/2049", f.average.getCode(),
                f.average.getName(), "Pembelian", "10.00", "10,000", "100,000", "BELI-A1", "10.00"});
        assertThat(rows.nth(1).locator("td")).hasText(new String[]{"02/03/2049", f.inactive.getCode(),
                f.inactive.getName(), "Pembelian", "3.00", "1,000", "3,000", "BELI-C1", "3.00"});
        assertThat(rows.nth(5).locator("td")).hasText(new String[]{"05/03/2049", f.fifo.getCode(),
                f.fifo.getName(), "Pembelian", "6.00", "8,000", "48,000", "BELI-B2", "10.00"});
        assertThat(rows.nth(6).locator("td")).hasText(new String[]{"06/03/2049", f.average.getCode(),
                f.average.getName(), "Penjualan", "6.00", "11,000", "66,000", "JUAL-A1", "9.00"});
        assertThat(rows.nth(7).locator("td")).hasText(new String[]{"07/03/2049", f.fifo.getCode(),
                f.fifo.getName(), "Penjualan", "5.00", "7,200", "36,000", "JUAL-B1", "5.00"});
        assertThat(rows.nth(8).locator("td")).hasText(new String[]{"08/03/2049", f.average.getCode(),
                f.average.getName(), "Penyesuaian Keluar", "1.00", "11,000", "11,000", "ADJ-A1", "8.00"});
        assertThat(rows.nth(9).locator("td")).hasText(new String[]{"09/03/2049", f.soldOut.getCode(),
                f.soldOut.getName(), "Penjualan", "2.00", "3,000", "6,000", "JUAL-D1", "0.00"});
        assertThat(summary("Total Transaksi")).hasText("10");
        assertThat(summary("Total Masuk")).hasText("Rp 250,000");
        assertThat(summary("Total Keluar")).hasText("Rp 119,000");
        assertThat(summary("Selisih")).hasText("Rp 131,000");

        page.locator("#start-date").fill("2049-03-04");
        page.locator("#end-date").fill("2049-03-06");
        page.locator("button:text-is('Tampilkan')").click();
        waitForPageLoad();
        assertThat(page.locator("#report-table tbody td:nth-child(7)")).hasText(new String[]{"65,000", "48,000", "66,000"});
        assertThat(summary("Total Keluar")).hasText("Rp 66,000");

        String query = "startDate=" + MARCH_START + "&endDate=" + MARCH_END + "&categoryId=" + f.category.getId()
                + "&productId=" + f.fifo.getId();
        navigateTo("/inventory/reports/stock-movement?" + query);
        waitForPageLoad();
        assertThat(page.locator("#productId")).hasValue(f.fifo.getId().toString());
        assertThat(page.locator("#report-table tbody td:nth-child(8)")).hasText(new String[]{"BELI-B1", "BELI-B2", "JUAL-B1"});
        assertThat(summary("Total Masuk")).hasText("Rp 76,000");
        assertThat(summary("Total Keluar")).hasText("Rp 36,000");
        assertThat(page.locator("a:text-is('PDF')")).hasAttribute("href", "/inventory/reports/stock-movement/export/pdf?" + query);

        String categoryQuery = "startDate=" + MARCH_START + "&endDate=" + MARCH_END + "&categoryId=" + f.category.getId();
        navigateTo("/inventory/reports/stock-movement/print?" + categoryQuery);
        waitForPageLoad();
        assertThat(page.locator("h1")).hasText("LAPORAN MUTASI STOK");
        assertThat(page.locator("tbody tr")).hasCount(10);

        String pdf = pdfText(export("/inventory/reports/stock-movement/export/pdf?" + categoryQuery,
                "application/pdf", "mutasi-stok-20490301-20490331.pdf"));
        assertTrue(pdf.contains("LAPORAN MUTASI STOK"), pdf);
        assertTrue(pdf.contains("JUAL-") || pdf.contains(f.soldOut.getCode()), pdf);
        assertTrue(pdf.contains("250.000") && pdf.contains("119.000"), pdf);

        Sheet sheet = sheet(export("/inventory/reports/stock-movement/export/excel?" + categoryQuery,
                XLSX, "mutasi-stok-20490301-20490331.xlsx"), "Mutasi Stok");
        List<String> movements = new ArrayList<>();
        for (Row row : sheet) {
            if (row.getCell(2) != null && row.getCell(2).toString().startsWith("RPT-" + f.id)) {
                movements.add(row.getCell(2).getStringCellValue() + "|" + row.getCell(4).getStringCellValue()
                        + "|" + (long) row.getCell(7).getNumericCellValue());
            }
        }
        assertEquals(10, movements.size());
        assertEquals(f.fifo.getCode() + "|Penjualan|36000", movements.get(7));
        assertEquals(250000, rowWith(sheet, 0, "Total Masuk (Nilai)").orElseThrow().getCell(1).getNumericCellValue());
        assertEquals(119000, rowWith(sheet, 0, "Total Keluar (Nilai)").orElseThrow().getCell(1).getNumericCellValue());
    }

    // ==================== VALUATION ====================

    @Test
    @DisplayName("Should value a category's stock by costing method, skipping sold-out and inactive products, print and export")
    void shouldReportValuation() {
        Fixture f = fixture();
        loginAsAdmin();

        navigateTo("/inventory/reports/valuation");
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Laporan Penilaian Persediaan");
        page.locator("#categoryId").selectOption(f.category.getId().toString());
        page.locator("button:text-is('Tampilkan')").click();
        waitForPageLoad();

        Locator rows = page.locator("#report-table tbody tr");
        assertThat(rows).hasCount(2);
        assertThat(rows.nth(0).locator("td")).hasText(new String[]{"1", f.average.getCode(), f.average.getName(),
                f.category.getName(), "8.00", "kg", "Rata-rata", "11,000", "88,000"});
        assertThat(rows.nth(1).locator("td")).hasText(new String[]{"2", f.fifo.getCode(), f.fifo.getName(),
                f.category.getName(), "5.00", "pcs", "FIFO", "8,000", "40,000"});
        assertThat(page.locator("#total-product-count")).hasText("2");
        assertThat(page.locator("#total-stock-value")).hasText("Rp 128,000");

        navigateTo("/inventory/reports/valuation/print?categoryId=" + f.category.getId());
        waitForPageLoad();
        assertThat(page.locator("h1")).hasText("LAPORAN PENILAIAN PERSEDIAAN");
        assertThat(page.locator("tbody tr")).hasCount(2);
        assertThat(page.locator("tfoot td").last()).hasText("Rp 128,000");

        String stamp = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String pdf = pdfText(export("/inventory/reports/valuation/export/pdf?categoryId=" + f.category.getId(),
                "application/pdf", "penilaian-persediaan-" + stamp + ".pdf"));
        assertTrue(pdf.contains(f.average.getCode()) && pdf.contains(f.fifo.getCode()) && pdf.contains("128.000"), pdf);
        assertFalse(pdf.contains(f.soldOut.getCode()) || pdf.contains(f.inactive.getCode()), pdf);

        Sheet sheet = sheet(export("/inventory/reports/valuation/export/excel?categoryId=" + f.category.getId(),
                XLSX, "penilaian-persediaan-" + stamp + ".xlsx"), "Penilaian Persediaan");
        assertEquals(List.of(f.average.getCode() + "|" + f.average.getName() + "|" + f.category.getName() + "|kg|8|11000|88000",
                f.fifo.getCode() + "|" + f.fifo.getName() + "|" + f.category.getName() + "|pcs|5|8000|40000"),
                dataRows(sheet, 1, 7));
    }

    // ==================== PROFITABILITY ====================

    @Test
    @DisplayName("Should rank a category's products by sales margin for a period, print and export")
    void shouldReportProfitability() {
        Fixture f = fixture();
        loginAsAdmin();

        String query = "startDate=" + MARCH_START + "&endDate=" + MARCH_END + "&categoryId=" + f.category.getId();
        navigateTo("/inventory/reports/profitability?" + query);
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Laporan Profitabilitas Produk");
        assertThat(page.locator("#categoryId")).hasValue(f.category.getId().toString());

        Locator rows = page.locator("#report-table tbody tr");
        assertThat(rows).hasCount(3);
        assertThat(rows.nth(0).locator("td")).hasText(new String[]{f.fifo.getCode(), f.fifo.getName(),
                f.category.getName(), "5.00 pcs", "Rp 65,000", "Rp 36,000", "Rp 29,000", "44.6%", "1"});
        assertThat(rows.nth(1).locator("td")).hasText(new String[]{f.average.getCode(), f.average.getName(),
                f.category.getName(), "6.00 kg", "Rp 90,000", "Rp 66,000", "Rp 24,000", "26.7%", "1"});
        assertThat(rows.nth(2).locator("td")).hasText(new String[]{f.soldOut.getCode(), f.soldOut.getName(),
                f.category.getName(), "2.00 kg", "Rp 0", "Rp 6,000", "Rp -6,000", "0.0%", "1"});
        assertThat(page.locator("#total-revenue")).hasText("Rp 155,000");
        assertThat(page.locator("#total-cogs")).hasText("Rp 108,000");
        assertThat(page.locator("#total-gross-profit")).hasText("Rp 47,000");
        assertThat(page.locator("#gross-margin-percent")).hasText("30.3%");

        page.locator("#productId").selectOption(f.average.getId().toString());
        page.locator("button:text-is('Tampilkan')").click();
        waitForPageLoad();
        assertThat(rows).hasCount(1);
        assertThat(page.locator("#gross-margin-percent")).hasText("26.7%");

        page.locator("#productId").selectOption("");
        page.locator("#start-date").fill("2049-03-07");
        page.locator("#end-date").fill("2049-03-08");
        page.locator("button:text-is('Tampilkan')").click();
        waitForPageLoad();
        assertThat(page.locator("#report-table tbody td:nth-child(1)")).hasText(new String[]{f.fifo.getCode()});

        navigateTo("/inventory/reports/profitability/print?" + query);
        waitForPageLoad();
        assertThat(page.locator("h1")).hasText("LAPORAN PROFITABILITAS PRODUK");
        assertThat(page.locator("tbody tr")).hasCount(3);
        assertThat(page.locator("tfoot td")).hasText(new String[]{"Total:", "13.00", "Rp 155,000", "Rp 108,000",
                "Rp 47,000", "30.3%", "3"});

        String pdf = pdfText(export("/inventory/reports/profitability/export/pdf?" + query,
                "application/pdf", "profitabilitas-produk-20490301-20490331.pdf"));
        assertTrue(pdf.contains("LAPORAN PROFITABILITAS PRODUK"), pdf);
        assertTrue(pdf.contains("155.000") && pdf.contains("108.000") && pdf.contains("47.000"), pdf);

        Sheet sheet = sheet(export("/inventory/reports/profitability/export/excel?" + query,
                XLSX, "profitabilitas-produk-20490301-20490331.xlsx"), "Profitabilitas Produk");
        assertEquals(List.of(f.fifo.getCode() + "|" + f.fifo.getName() + "|" + f.category.getName() + "|5|65000|36000|29000",
                f.average.getCode() + "|" + f.average.getName() + "|" + f.category.getName() + "|6|90000|66000|24000",
                f.soldOut.getCode() + "|" + f.soldOut.getName() + "|" + f.category.getName() + "|2||6000|-6000"),
                dataRows(sheet, 1, 7));
    }

    // ==================== PERMISSIONS ====================

    @Test
    @DisplayName("Should refuse reports and exports to a user without inventory access")
    void shouldDenyReportsWithoutInventoryPermission() {
        User employee = createUser(Role.EMPLOYEE);
        login(employee.getUsername(), "password");

        for (String path : List.of("/inventory/reports", "/inventory/reports/stock-balance",
                "/inventory/reports/stock-movement/export/excel", "/inventory/reports/valuation/export/pdf",
                "/inventory/reports/profitability/print")) {
            APIResponse response = page.request().get(baseUrl() + path);
            assertEquals(403, response.status(), path);
            assertFalse(response.headers().getOrDefault("content-disposition", "").contains("attachment"), path);
        }
    }

    // ==================== helpers ====================

    /**
     * Own category with, in March 2049: weighted-average A (kg, minimum 5) bought 10 @ 10,000 and 5 @ 13,000, sold
     * 6 @ 15,000, adjusted out 1 (8 left @ 11,000); FIFO B (pcs, minimum 10) bought 4 @ 7,000 and 6 @ 8,000, sold
     * 5 @ 13,000 (5 left @ 8,000); inactive C bought 3 @ 1,000; D bought 2 @ 3,000 and sold out without a price.
     */
    private record Fixture(String id, ProductCategory category, Product average, Product fifo, Product inactive,
                           Product soldOut) {
    }

    private Fixture fixture() {
        String id = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        ProductCategory category = new ProductCategory();
        category.setCode("RPT-" + id);
        category.setName("Kategori Laporan " + id);
        category = categoryRepository.save(category);
        Product average = product(category, id, "A", "Rata Rata " + id, "kg", CostingMethod.WEIGHTED_AVERAGE, "5");
        Product fifo = product(category, id, "B", "Fifo Barang " + id, "pcs", CostingMethod.FIFO, "10");
        Product inactive = product(category, id, "C", "Nonaktif " + id, "kg", CostingMethod.WEIGHTED_AVERAGE, "0");
        Product soldOut = product(category, id, "D", "Habis " + id, "kg", CostingMethod.WEIGHTED_AVERAGE, "0");

        purchase(average, 2, "10", "10000", "BELI-A1");
        purchase(inactive, 2, "3", "1000", "BELI-C1");
        purchase(soldOut, 2, "2", "3000", "BELI-D1");
        purchase(fifo, 3, "4", "7000", "BELI-B1");
        purchase(average, 4, "5", "13000", "BELI-A2");
        purchase(fifo, 5, "6", "8000", "BELI-B2");
        inventoryService.recordSale(average.getId(), march(6), new BigDecimal("6"), new BigDecimal("15000"), "JUAL-A1", null);
        inventoryService.recordSale(fifo.getId(), march(7), new BigDecimal("5"), new BigDecimal("13000"), "JUAL-B1", null);
        inventoryService.recordAdjustmentOut(average.getId(), march(8), BigDecimal.ONE, "ADJ-A1", "Rusak");
        inventoryService.recordSale(soldOut.getId(), march(9), new BigDecimal("2"), null, "JUAL-D1", null);
        inactive.setActive(false);
        productRepository.save(inactive);
        return new Fixture(id, category, average, fifo, inactive, soldOut);
    }

    private Product product(ProductCategory category, String id, String suffix, String name, String unit,
                            CostingMethod costingMethod, String minimumStock) {
        Product product = new Product();
        product.setCode("RPT-" + id + "-" + suffix);
        product.setName(name);
        product.setUnit(unit);
        product.setCategory(category);
        product.setCostingMethod(costingMethod);
        product.setMinimumStock(new BigDecimal(minimumStock));
        return productRepository.save(product);
    }

    private void purchase(Product product, int day, String quantity, String unitCost, String reference) {
        inventoryService.recordPurchase(product.getId(), march(day), new BigDecimal(quantity), new BigDecimal(unitCost),
                reference, null);
    }

    private static LocalDate march(int day) {
        return LocalDate.of(2049, 3, day);
    }

    private static String today() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.of("id", "ID")));
    }

    private Locator summary(String label) {
        return page.locator("section div.text-gray-500:text-is('" + label + "') + div");
    }

    private byte[] export(String path, String contentType, String filename) {
        APIResponse response = page.request().get(baseUrl() + path);
        assertEquals(200, response.status(), path);
        assertEquals(contentType, response.headers().get("content-type"), path);
        assertEquals("attachment; filename=\"" + filename + "\"", response.headers().get("content-disposition"), path);
        return response.body();
    }

    private static String pdfText(byte[] pdf) {
        try (PdfReader reader = new PdfReader(pdf)) {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder text = new StringBuilder();
            for (int p = 1; p <= reader.getNumberOfPages(); p++) {
                text.append(extractor.getTextFromPage(p)).append('\n');
            }
            return text.toString();
        } catch (IOException e) {
            throw new IllegalStateException("Unreadable PDF", e);
        }
    }

    private static Sheet sheet(byte[] xlsx, String name) {
        try {
            XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx));
            Sheet sheet = workbook.getSheet(name);
            if (sheet == null) {
                throw new AssertionError("Sheet '" + name + "' missing");
            }
            return sheet;
        } catch (IOException e) {
            throw new IllegalStateException("Unreadable workbook", e);
        }
    }

    /**
     * Rows whose code column holds a report fixture code: cells from the code column up to {@code lastColumn}, joined
     * with "|" (zero amounts are exported as blank cells).
     */
    private static List<String> dataRows(Sheet sheet, int codeColumn, int lastColumn) {
        List<String> result = new ArrayList<>();
        for (Row row : sheet) {
            var cell = row.getCell(codeColumn);
            if (cell == null || !cell.toString().startsWith("RPT-")) {
                continue;
            }
            List<String> values = new ArrayList<>();
            for (int c = codeColumn; c <= lastColumn; c++) {
                var value = row.getCell(c);
                values.add(switch (value.getCellType()) {
                    case NUMERIC -> BigDecimal.valueOf(value.getNumericCellValue()).stripTrailingZeros().toPlainString();
                    default -> value.getStringCellValue();
                });
            }
            result.add(String.join("|", values));
        }
        return result;
    }

    private static java.util.Optional<Row> rowWith(Sheet sheet, int column, String text) {
        for (Row row : sheet) {
            var cell = row.getCell(column);
            if (cell != null && text.equals(cell.toString())) {
                return java.util.Optional.of(row);
            }
        }
        return java.util.Optional.empty();
    }

    private User createUser(Role role) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setUsername("rpt-user-" + id);
        user.setPassword(passwordEncoder.encode("password"));
        user.setFullName("Pengguna " + id);
        user.setEmail("rpt-user-" + id + "@example.com");
        user.setActive(true);
        user.addRole(role, "system");
        return userRepository.save(user);
    }
}
