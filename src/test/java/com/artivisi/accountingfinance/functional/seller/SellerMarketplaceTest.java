package com.artivisi.accountingfinance.functional.seller;

import com.artivisi.accountingfinance.functional.page.CashFlowPage;
import com.artivisi.accountingfinance.functional.page.ClientListPage;
import com.artivisi.accountingfinance.functional.page.TransactionListPage;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Online Seller Marketplace Tests
 * Tests marketplace-specific clients (Tokopedia, Shopee) and suppliers.
 * Uses Page Object Pattern with ID-based locators.
 *
 * Expected Data (from SellerTestDataInitializer):
 * - Marketplace clients: Tokopedia, Shopee
 * - Supplier clients: Erajaya, Samsung Indonesia
 */
@DisplayName("Online Seller - Marketplace")
@Import(SellerTestDataInitializer.class)
public class SellerMarketplaceTest extends PlaywrightTestBase {

    // Page Objects
    private ClientListPage clientListPage;
    private TransactionListPage transactionListPage;
    private CashFlowPage cashFlowPage;

    private void initPageObjects() {
        String baseUrl = "http://localhost:" + port;
        clientListPage = new ClientListPage(page, baseUrl);
        transactionListPage = new TransactionListPage(page, baseUrl);
        cashFlowPage = new CashFlowPage(page, baseUrl);
    }

    @Test
    @DisplayName("Should display marketplace and supplier clients")
    void shouldDisplayMarketplaceClients() {
        loginAsAdmin();
        initPageObjects();

        clientListPage.navigate()
            .verifyPageTitle()
            .verifyTableVisible();

        // Verify marketplace clients exist using data-testid
        assertThat(page.locator("[data-testid='client-row-TOKPED']")).isVisible();
        assertThat(page.locator("[data-testid='client-row-SHOPEE']")).isVisible();
    }

    @Test
    @DisplayName("Should display suppliers")
    void shouldDisplaySuppliers() {
        loginAsAdmin();
        initPageObjects();

        clientListPage.navigate()
            .verifyPageTitle()
            .verifyTableVisible();

        // Verify supplier clients exist using data-testid
        assertThat(page.locator("[data-testid='client-row-ERAJAYA']")).isVisible();
        assertThat(page.locator("[data-testid='client-row-SAMSUNG']")).isVisible();
    }

    @Test
    @DisplayName("Should display transaction list")
    void shouldDisplayTransactionList() {
        loginAsAdmin();
        initPageObjects();

        transactionListPage.navigate()
            .verifyPageTitle()
            .verifyContentVisible();
    }

    @Test
    @DisplayName("Should display cash flow report")
    void shouldDisplayCashFlowReport() {
        loginAsAdmin();
        initPageObjects();

        cashFlowPage.navigate()
            .verifyPageTitle();
    }
}
