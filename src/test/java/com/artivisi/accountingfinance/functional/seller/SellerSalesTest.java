package com.artivisi.accountingfinance.functional.seller;

import com.artivisi.accountingfinance.entity.CostingMethod;
import com.artivisi.accountingfinance.entity.InventoryTransaction;
import com.artivisi.accountingfinance.entity.Product;
import com.artivisi.accountingfinance.functional.page.BalanceSheetPage;
import com.artivisi.accountingfinance.functional.page.CashFlowPage;
import com.artivisi.accountingfinance.functional.page.IncomeStatementPage;
import com.artivisi.accountingfinance.repository.InventoryTransactionRepository;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Online seller sales. Inventory sales are recorded by this class on its own FIFO product in {@link #YEAR} (no other
 * class in the shared seller context uses that year). The financial statements are asserted for January 2024, which
 * holds only the seeded testdata/seller/financial-*.csv postings (no seller class posts on or before 2024-01-31):
 * capital 200,000,000; purchases 150,000,000 + 240,000,000; Tokopedia sale 95,000,000 (admin 2,850,000, COGS
 * 75,000,000); Shopee sale 112,000,000 (admin 3,360,000, COGS 96,000,000); shipping 3,500,000.
 */
@DisplayName("Online Seller - Sales")
@Import(SellerTestDataInitializer.class)
class SellerSalesTest extends SellerTestBase {

    private static final int YEAR = 2078;

    @Autowired
    private InventoryTransactionRepository inventoryTransactionRepository;

    @Test
    @DisplayName("Should cost a sale spanning two FIFO layers and show it under the Penjualan filter and on the profitability report")
    void shouldRecordFifoSale() {
        String code = "SLS-" + runId;
        Product product = createProduct(code, "Sales FIFO " + runId, CostingMethod.FIFO, "PHONE", 150_000);
        loginAsAdmin();

        recordPurchase(product, LocalDate.of(YEAR, 2, 1), 5, 100_000, "PO-" + code + "-1");
        recordPurchase(product, LocalDate.of(YEAR, 2, 10), 5, 120_000, "PO-" + code + "-2");
        UUID saleId = recordSale(product, LocalDate.of(YEAR, 2, 20), 7, 150_000, "SO-" + code);

        InventoryTransaction sale = inventoryTransactionRepository.findById(saleId)
                .orElseThrow(() -> new AssertionError("Sale movement missing: " + saleId));
        assertEquals(0, new BigDecimal("740000").compareTo(sale.getTotalCost()), "FIFO cost 5 x 100,000 + 2 x 120,000");
        assertEquals(0, new BigDecimal("3").compareTo(sale.getBalanceAfter()));

        navigateTo("/inventory/transactions?productId=" + product.getId() + "&transactionType=SALE");
        waitForPageLoad();
        Locator rows = page.locator("[data-testid^='transaction-row-']");
        assertThat(rows).hasCount(1);
        Locator cells = page.getByTestId("transaction-row-" + saleId).locator("td");
        assertThat(cells.nth(0)).hasText("20/02/" + YEAR);
        assertThat(cells.nth(1)).hasText("Penjualan");
        assertThat(cells.nth(2)).hasText(code + " - Sales FIFO " + runId);
        assertThat(cells.nth(3)).hasText("-7.00 pcs");
        assertThat(cells.nth(5)).hasText("740,000");
        assertThat(cells.nth(6)).hasText("SO-" + code);
        takeManualScreenshot("seller/inventory-sales-list");

        navigateTo("/inventory/reports/profitability?startDate=" + YEAR + "-01-01&endDate=" + YEAR + "-12-31");
        waitForPageLoad();
        assertThat(page.locator("#report-table tbody tr")).hasCount(1);
        Locator row = page.locator("#report-table tbody tr").filter(new Locator.FilterOptions()
                .setHas(page.locator("td:nth-child(1):text-is('" + code + "')")));
        String[] expected = {code, "Sales FIFO " + runId, "Smartphone", "7.00 pcs", "Rp 1,050,000", "Rp 740,000",
                "Rp 310,000", "29.5%", "1"};
        for (int i = 0; i < expected.length; i++) {
            assertThat(row.locator("td").nth(i)).hasText(expected[i]);
        }
        assertThat(page.locator("#total-revenue")).hasText("Rp 1,050,000");
        assertThat(page.locator("#total-cogs")).hasText("Rp 740,000");
        assertThat(page.locator("#total-gross-profit")).hasText("Rp 310,000");
    }

    @Test
    @DisplayName("Should show January 2024 marketplace revenue, COGS, fees and shipping on the income statement")
    void shouldShowIncomeStatement() {
        loginAsAdmin();
        new IncomeStatementPage(page, baseUrl()).navigate("2024-01-01", "2024-01-31")
                .verifyPageTitle()
                .verifyProfitStatus();

        assertThat(page.locator(".revenue-row")).hasCount(2);
        assertThat(reportRow("revenue-row", "Penjualan Tokopedia").locator(".revenue-balance")).hasText("95.000.000");
        assertThat(reportRow("revenue-row", "Penjualan Shopee").locator(".revenue-balance")).hasText("112.000.000");
        assertThat(page.locator("#total-revenue")).hasText("207.000.000");
        assertThat(page.locator(".expense-row")).hasCount(4);
        assertThat(reportRow("expense-row", "HPP - Harga Pokok Penjualan").locator(".expense-balance")).hasText("(171.000.000)");
        assertThat(reportRow("expense-row", "Biaya Admin Tokopedia").locator(".expense-balance")).hasText("(2.850.000)");
        assertThat(reportRow("expense-row", "Biaya Admin Shopee").locator(".expense-balance")).hasText("(3.360.000)");
        assertThat(reportRow("expense-row", "Ongkir Ditanggung Penjual").locator(".expense-balance")).hasText("(3.500.000)");
        assertThat(page.locator("#total-expense")).hasText("(180.710.000)");
        assertThat(page.locator("#net-income")).hasText("26.290.000");

        takeManualScreenshot("seller/report-income-statement");
    }

    @Test
    @DisplayName("Should show January 2024 bank, marketplace balances, inventory, capital and earnings on the balance sheet")
    void shouldShowBalanceSheet() {
        loginAsAdmin();
        new BalanceSheetPage(page, baseUrl()).navigate("2024-01-31")
                .verifyPageTitle()
                .verifyBalanced();

        assertThat(page.locator(".asset-row")).hasCount(4);
        assertThat(reportRow("asset-row", "Bank BCA").locator(".asset-balance")).hasText("-193.500.000");
        assertThat(reportRow("asset-row", "Saldo Tokopedia").locator(".asset-balance")).hasText("92.150.000");
        assertThat(reportRow("asset-row", "Saldo Shopee").locator(".asset-balance")).hasText("108.640.000");
        assertThat(reportRow("asset-row", "Persediaan Barang Dagangan").locator(".asset-balance")).hasText("219.000.000");
        assertThat(page.locator("#total-assets")).hasText("226.290.000");
        assertThat(page.locator(".liability-row")).hasCount(0);
        assertThat(reportRow("equity-row", "Modal Disetor").locator(".equity-balance")).hasText("200.000.000");
        assertThat(page.locator("#current-year-earnings")).hasText("26.290.000");
        assertThat(page.locator("#total-equity")).hasText("226.290.000");

        takeManualScreenshot("seller/report-balance-sheet");
    }

    @Test
    @DisplayName("Should split January 2024 cash (bank and marketplace balances) into operating flows per template and capital financing")
    void shouldShowCashFlowStatement() {
        loginAsAdmin();
        new CashFlowPage(page, baseUrl()).navigate("2024-01-01", "2024-01-31")
                .verifyPageTitle();

        assertThat(cashFlowItem("Pembelian Barang Dagangan")).hasText("(390.000.000)");
        assertThat(cashFlowItem("Penjualan Tokopedia")).hasText("92.150.000");
        assertThat(cashFlowItem("Penjualan Shopee")).hasText("108.640.000");
        assertThat(cashFlowItem("Ongkir Ditanggung Penjual")).hasText("(3.500.000)");
        assertThat(page.locator("#operating-total")).hasText("(192.710.000)");
        assertThat(cashFlowItem("Setoran Modal")).hasText("200.000.000");
        assertThat(page.locator("#financing-total")).hasText("200.000.000");
        assertThat(page.locator("#investing-total")).hasText("0");
        assertThat(page.locator("#net-cash-change")).hasText("7.290.000");
        assertThat(page.locator("#ending-balance")).hasText("Rp 7.290.000");

        takeManualScreenshot("seller/report-cash-flow");
    }

    @Test
    @DisplayName("Should show each January 2024 account balance on the trial balance, debits equal to credits")
    void shouldShowTrialBalance() {
        loginAsAdmin();
        navigateTo("/reports/trial-balance?asOfDate=2024-01-31");
        waitForPageLoad();
        assertThat(page.locator("#page-title")).containsText("Neraca Saldo");

        Map<String, Long> balances = new HashMap<>();
        Locator rows = page.locator("#trial-balance-table tr.trial-balance-row");
        for (int i = 0; i < rows.count(); i++) {
            Locator row = rows.nth(i);
            balances.put(row.locator(".account-code").innerText().trim(),
                    amount(row.locator(".debit-amount")) - amount(row.locator(".credit-amount")));
        }
        assertEquals(Map.ofEntries(
                Map.entry("1.1.02", -193_500_000L), Map.entry("1.1.04", 92_150_000L), Map.entry("1.1.05", 108_640_000L),
                Map.entry("1.1.20", 219_000_000L), Map.entry("3.1.01", -200_000_000L),
                Map.entry("4.1.01", -95_000_000L), Map.entry("4.1.02", -112_000_000L),
                Map.entry("5.1.01", 171_000_000L), Map.entry("5.2.01", 2_850_000L), Map.entry("5.2.02", 3_360_000L),
                Map.entry("5.3.01", 3_500_000L)), balances);
        assertThat(page.locator("#balance-status")).hasText("Balance");
        assertEquals(600_500_000L, amount(page.locator("#total-debit")));
        assertEquals(600_500_000L, amount(page.locator("#total-credit")));

        takeManualScreenshot("seller/report-trial-balance");
    }
}
