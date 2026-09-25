package com.artivisi.accountingfinance.functional.seller;

import com.artivisi.accountingfinance.functional.page.CashFlowPage;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Online seller marketplace flows: the seeded January 2024 marketplace sales on the transaction list, and a Shopee
 * sale net of the admin fee withdrawn to Bank Mandiri, posted by this class in {@link #YEAR} (no other class in the
 * shared seller context uses that year).
 */
@DisplayName("Online Seller - Marketplace")
@Import(SellerTestDataInitializer.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SellerMarketplaceTest extends SellerTestBase {

    private static final int YEAR = 2080;
    private static final long GROSS = 14_000_000L;
    private static final long ADMIN_FEE = 420_000L;
    private static final long NET = GROSS - ADMIN_FEE;

    private UUID saleId;
    private UUID withdrawId;

    @Test
    @Order(1)
    @DisplayName("Should list the seeded January 2024 Tokopedia and Shopee sales under the Pendapatan filter")
    void shouldListSeededMarketplaceSales() {
        loginAsAdmin();
        navigateTo("/transactions?startDate=2024-01-01&endDate=2024-01-31");
        waitForPageLoad();
        assertThat(page.locator("[id^='trx-row-']")).hasCount(6);

        navigateTo("/transactions?startDate=2024-01-01&endDate=2024-01-31&category=INCOME");
        waitForPageLoad();
        takeManualScreenshot("seller/marketplace-sales-list");
        assertThat(page.locator("[id^='trx-row-']")).hasCount(2);
        assertSeededSale("SLR-TRX-004", "+ Rp 95.000.000", "Penjualan iPhone 5 unit @ 19jt via Tokopedia");
        assertSeededSale("SLR-TRX-005", "+ Rp 112.000.000", "Penjualan Samsung 8 unit @ 14jt via Shopee");
    }

    @Test
    @Order(2)
    @DisplayName("Should post a Shopee sale as net Saldo Shopee plus admin fee against gross Shopee revenue")
    void shouldPostShopeeSale() {
        loginAsAdmin();
        openTransactionForm("Penjualan Shopee", LocalDate.of(YEAR, 4, 10), "Penjualan Samsung via Shopee " + YEAR,
                "SHOPEE-" + YEAR + "-" + runId);
        page.locator("#var_grossSales").fill(String.valueOf(GROSS));
        page.locator("#var_adminFee").fill(String.valueOf(ADMIN_FEE));
        takeManualScreenshot("seller/marketplace-shopee-form");
        saleId = postAndVerify("Penjualan Shopee", LocalDate.of(YEAR, 4, 10), "SHOPEE-" + YEAR + "-" + runId, GROSS,
                List.of(journal("DEBIT", "1.1.05", NET), journal("DEBIT", "5.2.02", ADMIN_FEE),
                        journal("CREDIT", "4.1.02", GROSS)));
    }

    @Test
    @Order(3)
    @DisplayName("Should withdraw the Shopee balance to Bank Mandiri, leaving Saldo Shopee at zero")
    void shouldWithdrawShopeeBalance() {
        loginAsAdmin();
        openTransactionForm("Withdraw Saldo Shopee", LocalDate.of(YEAR, 4, 15), "Pencairan saldo Shopee " + YEAR,
                "WD-SHOPEE-" + YEAR + "-" + runId);
        page.locator("#amount").fill(String.valueOf(NET));
        pickBank("Withdraw Saldo Shopee", "1.1.03");
        withdrawId = postAndVerify("Withdraw Saldo Shopee", LocalDate.of(YEAR, 4, 15), "WD-SHOPEE-" + YEAR + "-" + runId,
                NET, List.of(journal("DEBIT", "1.1.03", NET), journal("CREDIT", "1.1.05", NET)));

        assertEquals(0L, jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(je.debit_amount - je.credit_amount), 0)
                FROM journal_entries je JOIN chart_of_accounts coa ON coa.id = je.id_account
                WHERE coa.account_code = '1.1.05' AND je.id_transaction IN (?, ?)""",
                BigDecimal.class, saleId, withdrawId).longValueExact(), "Saldo Shopee after withdrawal");
    }

    @Test
    @Order(4)
    @DisplayName("Should report the Shopee sale as operating cash and the withdrawal as a cash-to-cash move")
    void shouldReportMarketplaceCashFlow() {
        loginAsAdmin();
        new CashFlowPage(page, baseUrl()).navigate(YEAR + "-01-01", YEAR + "-12-31")
                .verifyPageTitle();

        assertThat(cashFlowItem("Penjualan Shopee")).hasText("13.580.000");
        assertThat(cashFlowItem("Withdraw Saldo Shopee")).hasCount(0);
        assertThat(page.locator("#operating-total")).hasText("13.580.000");
        assertThat(page.locator("#investing-total")).hasText("0");
        assertThat(page.locator("#financing-total")).hasText("0");
        assertThat(page.locator("#net-cash-change")).hasText("13.580.000");
        assertEquals(amount(page.locator("#beginning-balance")) + NET, amount(page.locator("#ending-balance")));

        takeManualScreenshot("seller/marketplace-cash-flow");
    }

    private void assertSeededSale(String number, String amount, String description) {
        Locator row = page.getByTestId("trx-row-" + number);
        assertThat(row).containsText(number);
        assertThat(row).containsText("POSTED");
        assertThat(row).containsText(amount);
        assertThat(row).containsText(description);
        assertThat(row).containsText("Pendapatan");
    }
}
