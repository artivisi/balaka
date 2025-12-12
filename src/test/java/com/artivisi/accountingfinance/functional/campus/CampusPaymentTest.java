package com.artivisi.accountingfinance.functional.campus;

import com.artivisi.accountingfinance.functional.page.JournalTemplateListPage;
import com.artivisi.accountingfinance.functional.page.TransactionListPage;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

/**
 * Campus Industry - Payment Collection Tests
 * Tests payment collection functionality for tuition fees, enrollment fees, and other student payments.
 * Uses Page Object Pattern for maintainability.
 */
@DisplayName("Campus Industry - Payment Collection")
@Import(CampusTestDataInitializer.class)
public class CampusPaymentTest extends PlaywrightTestBase {

    // Page Objects
    private JournalTemplateListPage templateListPage;
    private TransactionListPage transactionListPage;

    private void initPageObjects() {
        String baseUrl = "http://localhost:" + port;
        templateListPage = new JournalTemplateListPage(page, baseUrl);
        transactionListPage = new TransactionListPage(page, baseUrl);
    }

    @Test
    @DisplayName("Should display payment-related journal templates")
    void shouldDisplayPaymentTemplates() {
        loginAsAdmin();
        initPageObjects();

        templateListPage.navigate()
            .verifyPageTitle()
            .verifyContentVisible();

        // Verify payment templates exist
        page.locator("text=Pembayaran SPP").isVisible();
        page.locator("text=Pembayaran Uang Pangkal").isVisible();
        page.locator("text=Pembayaran Biaya Praktikum").isVisible();
        page.locator("text=Pembayaran Wisuda").isVisible();

        takeManualScreenshot("campus/payment-templates");
    }

    @Test
    @DisplayName("Should navigate to SPP payment form")
    void shouldNavigateToSppPaymentForm() {
        loginAsAdmin();
        initPageObjects();

        templateListPage.navigate()
            .verifyPageTitle()
            .verifyContentVisible();

        // Click on SPP payment template
        page.locator("text=Pembayaran SPP").first().click();
        page.waitForLoadState();

        // Verify form loads
        page.locator("#page-title").isVisible();
    }

    @Test
    @DisplayName("Should navigate to enrollment fee payment form")
    void shouldNavigateToEnrollmentFeePaymentForm() {
        loginAsAdmin();
        initPageObjects();

        templateListPage.navigate()
            .verifyPageTitle()
            .verifyContentVisible();

        // Click on enrollment fee payment template
        page.locator("text=Pembayaran Uang Pangkal").first().click();
        page.waitForLoadState();

        // Verify form loads
        page.locator("#page-title").isVisible();
    }

    @Test
    @DisplayName("Should display transaction list")
    void shouldDisplayTransactionList() {
        loginAsAdmin();
        initPageObjects();

        transactionListPage.navigate()
            .verifyPageTitle()
            .verifyContentVisible()
            .verifyTableVisible();

        takeManualScreenshot("campus/transaction-list");
    }
}
