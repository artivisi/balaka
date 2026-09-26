package com.artivisi.accountingfinance.functional.manufacturing;

import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.util.List;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Seeded coffee-shop inventory movements for January 2024 (industry-seed/coffee-shop
 * 40_inventory_transactions.csv, 41_inventory_balances.csv): purchases, production consumption/output
 * valued at average cost, and sales at the finished goods' unit cost. January 2024 is owned by the seed;
 * no test in the coffee-shop context posts into it.
 */
@DisplayName("Manufacturing - Costing & Valuation")
@Import(CoffeeTestDataInitializer.class)
class MfgCostingTest extends PlaywrightTestBase {

    private static final String JANUARY_2024 = "startDate=2024-01-01&endDate=2024-01-31";

    @Test
    @DisplayName("January 2024 transaction list holds the 29 seeded movements, paginated 20 per page")
    void shouldListJanuaryTransactions() {
        loginAsAdmin();
        navigateTo("/inventory/transactions?" + JANUARY_2024);
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Transaksi Persediaan");
        assertThat(page.locator("#start-date")).hasValue("2024-01-01");
        assertThat(page.locator("#end-date")).hasValue("2024-01-31");
        assertThat(page.locator("#transaction-table tbody tr")).hasCount(20);
        assertThat(page.locator("#transaction-table p.text-sm.text-gray-700"))
                .hasText("Menampilkan 1 - 20 dari 29 data");

        takeManualScreenshot("coffee/inventory-transactions-list");
    }

    @Test
    @DisplayName("Production output is recorded at the order's unit cost: croissant 24 @ 4,455, roti coklat 20 @ 4,388")
    void shouldRecordProductionOutputAtUnitCost() {
        assertThat(transactionRows("PRODUCTION_IN")).containsExactlyInAnyOrder(
                "08/01/2024 | Produksi Masuk | CROISSANT - Croissant | +24.00 pcs | 4,455.00 | 106,920 | PROD-001",
                "08/01/2024 | Produksi Masuk | ROTI-COKLAT - Roti Bakar Coklat | +20.00 pcs | 4,388.00 | 87,760 | PROD-002");
    }

    @Test
    @DisplayName("Production consumes each BOM component quantity at its average cost")
    void shouldConsumeBomComponentsAtAverageCost() {
        assertThat(transactionRows("PRODUCTION_OUT")).containsExactlyInAnyOrder(
                "08/01/2024 | Produksi Keluar | TEPUNG-TERIGU - Tepung Terigu | -3.00 kg | 12,000.00 | 36,000 | PROD-001",
                "08/01/2024 | Produksi Keluar | BUTTER - Butter | -1.50 kg | 35,000.00 | 52,500 | PROD-001",
                "08/01/2024 | Produksi Keluar | TELUR - Telur Ayam | -12.00 butir | 2,500.00 | 30,000 | PROD-001",
                "08/01/2024 | Produksi Keluar | RAGI - Ragi Instan | -10.00 gram | 3,000.00 | 30,000 | PROD-001",
                "08/01/2024 | Produksi Keluar | GARAM - Garam | -6.00 gram | 8,000.00 | 48,000 | PROD-001",
                "08/01/2024 | Produksi Keluar | SUSU-SEGAR - Susu Segar | -0.60 liter | 18,000.00 | 10,800 | PROD-001",
                "08/01/2024 | Produksi Keluar | TEPUNG-TERIGU - Tepung Terigu | -2.50 kg | 12,000.00 | 30,000 | PROD-002",
                "08/01/2024 | Produksi Keluar | BUTTER - Butter | -1.00 kg | 35,000.00 | 35,000 | PROD-002",
                "08/01/2024 | Produksi Keluar | TELUR - Telur Ayam | -10.00 butir | 2,500.00 | 25,000 | PROD-002",
                "08/01/2024 | Produksi Keluar | RAGI - Ragi Instan | -8.00 gram | 3,000.00 | 24,000 | PROD-002",
                "08/01/2024 | Produksi Keluar | GARAM - Garam | -5.00 gram | 8,000.00 | 40,000 | PROD-002",
                "08/01/2024 | Produksi Keluar | COKLAT - Coklat Blok | -0.40 kg | 85,000.00 | 34,000 | PROD-002");
    }

    @Test
    @DisplayName("Finished goods balance is production minus sales at unit cost: croissant 9 @ 4,455, roti coklat 8 @ 4,388")
    void shouldValueFinishedGoodsAfterProductionAndSales() {
        loginAsAdmin();
        assertStockRow("CROISSANT", "Croissant", "Roti & Kue", "9.00 pcs", "4,455.00", "40,095");
        assertStockRow("ROTI-COKLAT", "Roti Bakar Coklat", "Roti & Kue", "8.00 pcs", "4,388.00", "35,104");
    }

    @Test
    @DisplayName("Raw material purchases are recorded with quantity, unit cost and PO reference")
    void shouldRecordPurchases() {
        assertThat(transactionRows("PURCHASE")).containsExactlyInAnyOrder(
                "05/01/2024 | Pembelian | KOPI-ARABICA - Biji Kopi Arabica | +10.00 kg | 180,000.00 | 1,800,000 | PO-001",
                "05/01/2024 | Pembelian | SUSU-SEGAR - Susu Segar | +50.00 liter | 18,000.00 | 900,000 | PO-001",
                "05/01/2024 | Pembelian | GULA-AREN - Gula Aren Cair | +20.00 liter | 45,000.00 | 900,000 | PO-001",
                "05/01/2024 | Pembelian | ES-BATU - Es Batu | +50.00 kg | 5,000.00 | 250,000 | PO-001",
                "06/01/2024 | Pembelian | TEPUNG-TERIGU - Tepung Terigu | +30.00 kg | 12,000.00 | 360,000 | PO-002",
                "06/01/2024 | Pembelian | BUTTER - Butter | +20.00 kg | 35,000.00 | 700,000 | PO-002",
                "06/01/2024 | Pembelian | TELUR - Telur Ayam | +120.00 butir | 2,500.00 | 300,000 | PO-002",
                "06/01/2024 | Pembelian | RAGI - Ragi Instan | +60.00 gram | 3,000.00 | 180,000 | PO-002",
                "06/01/2024 | Pembelian | GARAM - Garam | +30.00 gram | 8,000.00 | 240,000 | PO-002",
                "06/01/2024 | Pembelian | COKLAT - Coklat Blok | +10.00 kg | 85,000.00 | 850,000 | PO-002",
                "15/01/2024 | Pembelian | SUSU-SEGAR - Susu Segar | +10.00 liter | 18,000.00 | 180,000 | PO-003",
                "15/01/2024 | Pembelian | KOPI-ARABICA - Biji Kopi Arabica | +5.00 kg | 180,000.00 | 900,000 | PO-003",
                "15/01/2024 | Pembelian | TEPUNG-TERIGU - Tepung Terigu | +10.00 kg | 12,000.00 | 120,000 | PO-003");
    }

    @Test
    @DisplayName("Sales carry COGS at the finished goods' unit cost")
    void shouldRecordSalesCogsAtUnitCost() {
        assertThat(transactionRows("SALE")).containsExactlyInAnyOrder(
                "10/01/2024 | Penjualan | CROISSANT - Croissant | -15.00 pcs | 4,455.00 | 66,825 | INV-001",
                "10/01/2024 | Penjualan | ROTI-COKLAT - Roti Bakar Coklat | -12.00 pcs | 4,388.00 | 52,656 | INV-002");
        assertThat(page.locator("#transaction-type-filter")).hasValue("SALE");
    }

    private List<String> transactionRows(String type) {
        loginAsAdmin();
        navigateTo("/inventory/transactions?transactionType=" + type + "&" + JANUARY_2024);
        waitForPageLoad();
        Locator rows = page.locator("#transaction-table tbody tr");
        @SuppressWarnings("unchecked")
        List<String> texts = (List<String>) rows.evaluateAll(
                "rs => rs.map(r => [...r.cells].slice(0, 7).map(c => c.textContent.replace(/\\s+/g, ' ').trim()).join(' | '))");
        return texts;
    }

    private void assertStockRow(String code, String name, String category, String quantity,
                                String averageCost, String totalCost) {
        navigateTo("/inventory/stock?search=" + code);
        waitForPageLoad();
        assertThat(page.locator("[data-testid='stock-row-" + code + "'] td")).hasText(new String[]{
                code, name, category, quantity, averageCost, totalCost, "Normal", ""});
    }
}
