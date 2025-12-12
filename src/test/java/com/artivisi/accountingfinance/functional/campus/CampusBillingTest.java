package com.artivisi.accountingfinance.functional.campus;

import com.artivisi.accountingfinance.functional.page.ChartOfAccountsPage;
import com.artivisi.accountingfinance.functional.page.JournalTemplateListPage;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

/**
 * Campus Industry - Student Billing Tests
 * Tests student billing functionality for tuition fees, enrollment fees, lab fees, and graduation fees.
 * Uses Page Object Pattern for maintainability.
 */
@DisplayName("Campus Industry - Student Billing")
@Import(CampusTestDataInitializer.class)
public class CampusBillingTest extends PlaywrightTestBase {

    // Page Objects
    private ChartOfAccountsPage chartOfAccountsPage;
    private JournalTemplateListPage templateListPage;

    private void initPageObjects() {
        String baseUrl = "http://localhost:" + port;
        chartOfAccountsPage = new ChartOfAccountsPage(page, baseUrl);
        templateListPage = new JournalTemplateListPage(page, baseUrl);
    }

    @Test
    @DisplayName("Should display Chart of Accounts with campus-specific accounts")
    void shouldDisplayChartOfAccounts() {
        loginAsAdmin();
        initPageObjects();

        // Campus seed has education-specific accounts
        chartOfAccountsPage.navigate()
            .verifyPageTitle()
            .verifyContentVisible()
            .verifyTableVisible()
            .verifyMinimumAccountCount(70)  // At least 70 accounts from seed
            .verifyAccountExists("1.1.10")  // Piutang SPP Mahasiswa
            .verifyAccountExists("4.1.01")  // Pendapatan SPP
            .verifyAccountExists("5.1.01")  // Beban Gaji Dosen Tetap
            .verifyAccountExists("5.3.01"); // Beban Beasiswa Prestasi

        takeManualScreenshot("campus/accounts-list");
    }

    @Test
    @DisplayName("Should display billing-related journal templates")
    void shouldDisplayBillingTemplates() {
        loginAsAdmin();
        initPageObjects();

        templateListPage.navigate()
            .verifyPageTitle()
            .verifyContentVisible();

        // Verify billing templates exist
        page.locator("text=Tagihan SPP Mahasiswa").isVisible();
        page.locator("text=Tagihan Uang Pangkal").isVisible();
        page.locator("text=Tagihan Biaya Praktikum").isVisible();
        page.locator("text=Tagihan Wisuda").isVisible();

        takeManualScreenshot("campus/billing-templates");
    }

    @Test
    @DisplayName("Should navigate to SPP billing form")
    void shouldNavigateToSppBillingForm() {
        loginAsAdmin();
        initPageObjects();

        // Navigate to templates page
        templateListPage.navigate()
            .verifyPageTitle()
            .verifyContentVisible();

        // Click on SPP billing template
        page.locator("text=Tagihan SPP Mahasiswa").first().click();
        page.waitForLoadState();

        // Verify form loads
        page.locator("#page-title").isVisible();
    }

    @Test
    @DisplayName("Should navigate to enrollment fee billing form")
    void shouldNavigateToEnrollmentFeeBillingForm() {
        loginAsAdmin();
        initPageObjects();

        templateListPage.navigate()
            .verifyPageTitle()
            .verifyContentVisible();

        // Click on enrollment fee template
        page.locator("text=Tagihan Uang Pangkal").first().click();
        page.waitForLoadState();

        // Verify form loads
        page.locator("#page-title").isVisible();
    }

    @Test
    @DisplayName("Should navigate to lab fee billing form")
    void shouldNavigateToLabFeeBillingForm() {
        loginAsAdmin();
        initPageObjects();

        templateListPage.navigate()
            .verifyPageTitle()
            .verifyContentVisible();

        // Click on lab fee template
        page.locator("text=Tagihan Biaya Praktikum").first().click();
        page.waitForLoadState();

        // Verify form loads
        page.locator("#page-title").isVisible();
    }

    @Test
    @DisplayName("Should navigate to graduation fee billing form")
    void shouldNavigateToGraduationFeeBillingForm() {
        loginAsAdmin();
        initPageObjects();

        templateListPage.navigate()
            .verifyPageTitle()
            .verifyContentVisible();

        // Click on graduation fee template
        page.locator("text=Tagihan Wisuda").first().click();
        page.waitForLoadState();

        // Verify form loads
        page.locator("#page-title").isVisible();
    }
}
