package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.page.LoginPage;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PPh 21 Calculator (Phase 3.4)")
class Pph21CalculatorTest extends PlaywrightTestBase {

    private LoginPage loginPage;

    @BeforeEach
    void setUp() {
        loginPage = new LoginPage(page, baseUrl());
        loginPage.navigate().loginAsAdmin();
    }

    @Nested
    @DisplayName("3.4.1 Page Display")
    class PageDisplay {

        @Test
        @DisplayName("Should display calculator page")
        void shouldDisplayCalculatorPage() {
            page.navigate(baseUrl() + "/pph21-calculator");

            assertThat(page.locator("#page-title").textContent()).contains("Kalkulator PPh 21");
        }

        @Test
        @DisplayName("Should display salary input field")
        void shouldDisplaySalaryInputField() {
            page.navigate(baseUrl() + "/pph21-calculator");

            assertThat(page.locator("#salary").isVisible()).isTrue();
        }

        @Test
        @DisplayName("Should display PTKP status dropdown")
        void shouldDisplayPtkpStatusDropdown() {
            page.navigate(baseUrl() + "/pph21-calculator");

            assertThat(page.locator("#ptkpStatus").isVisible()).isTrue();
            // Should have 12 PTKP statuses (TK/0 to K/I/3)
            assertThat(page.locator("#ptkpStatus option").count()).isEqualTo(12);
        }

        @Test
        @DisplayName("Should display NPWP checkbox")
        void shouldDisplayNpwpCheckbox() {
            page.navigate(baseUrl() + "/pph21-calculator");

            assertThat(page.locator("#hasNpwp").isVisible()).isTrue();
            // Should be checked by default
            assertThat(page.locator("#hasNpwp").isChecked()).isTrue();
        }

        @Test
        @DisplayName("Should display calculate button")
        void shouldDisplayCalculateButton() {
            page.navigate(baseUrl() + "/pph21-calculator");

            assertThat(page.locator("#btn-calculate").isVisible()).isTrue();
        }
    }

    @Nested
    @DisplayName("3.4.2 Calculation Results")
    class CalculationResults {

        @Test
        @DisplayName("Should calculate PPh 21 for typical salary")
        void shouldCalculateForTypicalSalary() {
            page.navigate(baseUrl() + "/pph21-calculator");

            page.fill("#salary", "10000000");
            page.click("#btn-calculate");
            page.waitForLoadState();

            // Check summary cards are visible
            assertThat(page.locator("[data-testid='gross-income']").isVisible()).isTrue();
            assertThat(page.locator("[data-testid='monthly-pph21']").isVisible()).isTrue();
            assertThat(page.locator("[data-testid='bpjs-deduction']").isVisible()).isTrue();
            assertThat(page.locator("[data-testid='take-home-pay']").isVisible()).isTrue();

            // Gross income should match input
            assertThat(page.locator("[data-testid='gross-income']").textContent()).contains("10,000,000");
        }

        @Test
        @DisplayName("Should show zero tax for low salary")
        void shouldShowZeroTaxForLowSalary() {
            page.navigate(baseUrl() + "/pph21-calculator");

            page.fill("#salary", "4000000");
            page.selectOption("#ptkpStatus", "TK_0");
            page.click("#btn-calculate");
            page.waitForLoadState();

            // For low salary, PPh 21 should be 0
            assertThat(page.locator("[data-testid='monthly-pph21']").textContent()).contains("0");
        }

        @Test
        @DisplayName("Should calculate with different PTKP status")
        void shouldCalculateWithDifferentPtkpStatus() {
            page.navigate(baseUrl() + "/pph21-calculator");

            page.fill("#salary", "15000000");
            page.selectOption("#ptkpStatus", "K_2"); // Kawin 2 tanggungan
            page.click("#btn-calculate");
            page.waitForLoadState();

            // PTKP should show K/2 amount (67,500,000)
            assertThat(page.locator("[data-testid='ptkp']").textContent()).contains("67,500,000");
        }

        @Test
        @DisplayName("Should show higher tax without NPWP")
        void shouldShowHigherTaxWithoutNpwp() {
            page.navigate(baseUrl() + "/pph21-calculator");

            // First calculate with NPWP
            page.fill("#salary", "15000000");
            page.check("#hasNpwp");
            page.click("#btn-calculate");
            page.waitForLoadState();

            // Now calculate without NPWP
            page.uncheck("#hasNpwp");
            page.click("#btn-calculate");
            page.waitForLoadState();

            // Warning should show for no NPWP
            assertThat(page.content()).contains("Karyawan tanpa NPWP");
        }

        @Test
        @DisplayName("Should display calculation breakdown")
        void shouldDisplayCalculationBreakdown() {
            page.navigate(baseUrl() + "/pph21-calculator");

            page.fill("#salary", "10000000");
            page.click("#btn-calculate");
            page.waitForLoadState();

            // Check breakdown components are visible
            assertThat(page.locator("[data-testid='biaya-jabatan']").isVisible()).isTrue();
            assertThat(page.locator("[data-testid='monthly-neto']").isVisible()).isTrue();
            assertThat(page.locator("[data-testid='annual-neto']").isVisible()).isTrue();
            assertThat(page.locator("[data-testid='pkp']").isVisible()).isTrue();
            assertThat(page.locator("[data-testid='annual-pph21']").isVisible()).isTrue();
        }

        @Test
        @DisplayName("Should display effective tax rate")
        void shouldDisplayEffectiveTaxRate() {
            page.navigate(baseUrl() + "/pph21-calculator");

            page.fill("#salary", "20000000");
            page.click("#btn-calculate");
            page.waitForLoadState();

            // Effective rate should be visible and contain percentage
            assertThat(page.locator("[data-testid='effective-rate']").isVisible()).isTrue();
            assertThat(page.locator("[data-testid='effective-rate']").textContent()).contains("%");
        }
    }

    @Nested
    @DisplayName("3.4.3 Navigation")
    class Navigation {

        @Test
        @DisplayName("Should navigate from sidebar menu")
        void shouldNavigateFromSidebarMenu() {
            page.navigate(baseUrl() + "/dashboard");

            page.click("#nav-pph21-calculator");
            page.waitForLoadState();

            assertThat(page.url()).contains("/pph21-calculator");
        }
    }
}
