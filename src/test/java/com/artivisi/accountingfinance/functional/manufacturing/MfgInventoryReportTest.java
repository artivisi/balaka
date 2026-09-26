package com.artivisi.accountingfinance.functional.manufacturing;

import com.artivisi.accountingfinance.entity.ProductCategory;
import com.artivisi.accountingfinance.repository.ProductCategoryRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Inventory reports over the seeded coffee-shop stock (industry-seed/coffee-shop 41_inventory_balances.csv) and
 * January 2024 sales (40_inventory_transactions.csv). Scoped by the seeded categories, which only seeded products use.
 */
@DisplayName("Manufacturing - Inventory Reports")
@Import(CoffeeTestDataInitializer.class)
class MfgInventoryReportTest extends PlaywrightTestBase {

    @Autowired
    private ProductCategoryRepository categoryRepository;

    @Test
    @DisplayName("Stock balance report for Bahan Baku Roti lists the six bakery materials by code with balances, low-stock status and total value")
    void shouldReportBakeryMaterialStockBalance() {
        UUID categoryId = categoryId("CAT-BAHAN-ROTI");
        loginAsAdmin();
        navigateTo("/inventory/reports/stock-balance?categoryId=" + categoryId);
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Laporan Saldo Stok");
        assertThat(page.locator("#categoryId")).hasValue(categoryId.toString());
        assertThat(reportRows()).containsExactly(
                "BUTTER | Butter | Bahan Baku Roti | 17.50 | kg | 35,000 | 612,500 | Normal",
                "COKLAT | Coklat Blok | Bahan Baku Roti | 9.60 | kg | 85,000 | 816,000 | Normal",
                "GARAM | Garam | Bahan Baku Roti | 19.00 | gram | 8,000 | 152,000 | Stok Rendah",
                "RAGI | Ragi Instan | Bahan Baku Roti | 42.00 | gram | 3,000 | 126,000 | Stok Rendah",
                "TELUR | Telur Ayam | Bahan Baku Roti | 98.00 | butir | 2,500 | 245,000 | Stok Rendah",
                "TEPUNG-TERIGU | Tepung Terigu | Bahan Baku Roti | 34.50 | kg | 12,000 | 414,000 | Normal");
        assertThat(page.locator("#total-product-count")).hasText("6");
        assertThat(page.locator("#total-stock-value")).hasText("Rp 2,365,500");

        takeManualScreenshot("coffee/report-stock-balance");
    }

    @Test
    @DisplayName("Valuation report for Bahan Baku Kopi values the four coffee materials at average cost")
    void shouldReportCoffeeMaterialValuation() {
        UUID categoryId = categoryId("CAT-BAHAN-KOPI");
        loginAsAdmin();
        navigateTo("/inventory/reports/valuation?categoryId=" + categoryId);
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Laporan Penilaian Persediaan");
        assertThat(page.locator("#categoryId")).hasValue(categoryId.toString());
        assertThat(reportRows()).containsExactlyInAnyOrder(
                "KOPI-ARABICA | Biji Kopi Arabica | Bahan Baku Kopi | 15.00 | kg | Rata-rata | 180,000 | 2,700,000",
                "SUSU-SEGAR | Susu Segar | Bahan Baku Kopi | 59.40 | liter | Rata-rata | 18,000 | 1,069,200",
                "GULA-AREN | Gula Aren Cair | Bahan Baku Kopi | 20.00 | liter | Rata-rata | 45,000 | 900,000",
                "ES-BATU | Es Batu | Bahan Baku Kopi | 50.00 | kg | Rata-rata | 5,000 | 250,000");
        assertThat(page.locator("#total-product-count")).hasText("4");
        assertThat(page.locator("#total-stock-value")).hasText("Rp 4,919,200");
        assertThat(page.locator("#report-table tfoot td")).hasText(new String[]{"Total Nilai Persediaan:", "Rp 4,919,200"});

        takeManualScreenshot("coffee/report-inventory-valuation");
    }

    @Test
    @DisplayName("Profitability report for January 2024 shows pastry revenue, COGS and margin per product")
    void shouldReportJanuaryPastryProfitability() {
        UUID categoryId = categoryId("CAT-ROTI");
        loginAsAdmin();
        navigateTo("/inventory/reports/profitability?startDate=2024-01-01&endDate=2024-01-31&categoryId=" + categoryId);
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Laporan Profitabilitas Produk");
        assertThat(page.locator("#start-date")).hasValue("2024-01-01");
        assertThat(page.locator("#end-date")).hasValue("2024-01-31");
        assertThat(page.locator("#categoryId")).hasValue(categoryId.toString());
        assertThat(page.locator("#report-table tbody tr").nth(0).locator("td")).hasText(new String[]{
                "CROISSANT", "Croissant", "Roti & Kue", "15.00 pcs", "Rp 375,000", "Rp 66,825", "Rp 308,175", "82.2%", "1"});
        assertThat(page.locator("#report-table tbody tr").nth(1).locator("td")).hasText(new String[]{
                "ROTI-COKLAT", "Roti Bakar Coklat", "Roti & Kue", "12.00 pcs", "Rp 240,000", "Rp 52,656", "Rp 187,344", "78.1%", "1"});
        assertThat(page.locator("#report-table tbody tr")).hasCount(2);
        assertThat(page.locator("#total-revenue")).hasText("Rp 615,000");
        assertThat(page.locator("#total-cogs")).hasText("Rp 119,481");
        assertThat(page.locator("#total-gross-profit")).hasText("Rp 495,519");
        assertThat(page.locator("#gross-margin-percent")).hasText("80.6%");

        takeManualScreenshot("coffee/report-product-profitability");
    }

    private UUID categoryId(String code) {
        ProductCategory category = categoryRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Seeded category missing: " + code));
        return category.getId();
    }

    private List<String> reportRows() {
        @SuppressWarnings("unchecked")
        List<String> rows = (List<String>) page.locator("#report-table tbody tr").evaluateAll(
                "rs => rs.map(r => [...r.cells].slice(1).map(c => c.textContent.replace(/\\s+/g, ' ').trim()).join(' | '))");
        return rows;
    }
}
