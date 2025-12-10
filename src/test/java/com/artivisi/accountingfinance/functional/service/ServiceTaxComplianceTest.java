package com.artivisi.accountingfinance.functional.service;

import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Service Industry Tax Compliance Tests
 * Tests PKP tax compliance: PPN, PPh 21, PPh 23, tax calendar.
 */
@DisplayName("Service Industry - Tax Compliance")
public class ServiceTaxComplianceTest extends PlaywrightTestBase {

    @Test
    @DisplayName("Should display tax calendar")
    void shouldDisplayTaxCalendar() {
        loginAsAdmin();
        navigateTo("/tax-calendar");
        waitForPageLoad();

        // Verify tax calendar page loads
        assertThat(page.locator("h1")).containsText("Kalender Pajak");
    }

    @Test
    @DisplayName("Should display PPN summary report")
    void shouldDisplayPpnSummaryReport() {
        loginAsAdmin();
        navigateTo("/reports/ppn-summary");
        waitForPageLoad();

        // Verify PPN report page loads
        assertThat(page.locator("h1")).containsText("PPN");
    }

    @Test
    @DisplayName("Should display PPh 23 withholding report")
    void shouldDisplayPph23WithholdingReport() {
        loginAsAdmin();
        navigateTo("/reports/pph23-withholding");
        waitForPageLoad();

        // Verify PPh 23 report page loads
        assertThat(page.locator("h1")).containsText("PPh 23");
    }

    @Test
    @DisplayName("Should display tax export page")
    void shouldDisplayTaxExportPage() {
        loginAsAdmin();
        navigateTo("/reports/tax-export");
        waitForPageLoad();

        // Verify tax export page loads
        assertThat(page.locator("h1")).containsText("Export");
    }

    @Test
    @DisplayName("Should display tax summary report")
    void shouldDisplayTaxSummaryReport() {
        loginAsAdmin();
        navigateTo("/reports/tax-summary");
        waitForPageLoad();

        // Verify tax summary page loads
        assertThat(page.locator("h1")).containsText("Pajak");
    }
}
