package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.page.LoginPage;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BPJS Calculator (Phase 3.3)")
class BpjsCalculatorTest extends PlaywrightTestBase {

    private LoginPage loginPage;

    @BeforeEach
    void setUp() {
        loginPage = new LoginPage(page, baseUrl());
        loginPage.navigate().loginAsAdmin();
    }

    @Nested
    @DisplayName("3.3.1 Page Display")
    class PageDisplay {

        @Test
        @DisplayName("Should display calculator page")
        void shouldDisplayCalculatorPage() {
            page.navigate(baseUrl() + "/bpjs-calculator");

            assertThat(page.locator("#page-title").textContent()).contains("Kalkulator BPJS");
        }

        @Test
        @DisplayName("Should display salary input field")
        void shouldDisplaySalaryInputField() {
            page.navigate(baseUrl() + "/bpjs-calculator");

            assertThat(page.locator("#salary").isVisible()).isTrue();
        }

        @Test
        @DisplayName("Should display risk class dropdown")
        void shouldDisplayRiskClassDropdown() {
            page.navigate(baseUrl() + "/bpjs-calculator");

            assertThat(page.locator("#riskClass").isVisible()).isTrue();
            // Should have 5 risk classes
            assertThat(page.locator("#riskClass option").count()).isEqualTo(5);
        }

        @Test
        @DisplayName("Should display calculate button")
        void shouldDisplayCalculateButton() {
            page.navigate(baseUrl() + "/bpjs-calculator");

            assertThat(page.locator("#btn-calculate").isVisible()).isTrue();
        }
    }

    @Nested
    @DisplayName("3.3.2 Calculation Results")
    class CalculationResults {

        @Test
        @DisplayName("Should calculate BPJS for typical salary")
        void shouldCalculateForTypicalSalary() {
            page.navigate(baseUrl() + "/bpjs-calculator");

            page.fill("#salary", "10000000");
            page.click("#btn-calculate");
            page.waitForLoadState();

            // Check summary cards are visible
            assertThat(page.locator("[data-testid='total-company']").isVisible()).isTrue();
            assertThat(page.locator("[data-testid='total-employee']").isVisible()).isTrue();
            assertThat(page.locator("[data-testid='grand-total']").isVisible()).isTrue();

            // Check detailed breakdown
            assertThat(page.locator("[data-testid='kesehatan-company']").textContent()).contains("400,000");
            assertThat(page.locator("[data-testid='kesehatan-employee']").textContent()).contains("100,000");
        }

        @Test
        @DisplayName("Should show ceiling warning for high salary")
        void shouldShowCeilingWarningForHighSalary() {
            page.navigate(baseUrl() + "/bpjs-calculator");

            page.fill("#salary", "20000000"); // Exceeds both ceilings
            page.click("#btn-calculate");
            page.waitForLoadState();

            // Should show warning about ceilings
            assertThat(page.content()).contains("Catatan Batas Upah");
            assertThat(page.content()).contains("12,000,000"); // Kesehatan ceiling
        }

        @Test
        @DisplayName("Should not show ceiling warning for low salary")
        void shouldNotShowCeilingWarningForLowSalary() {
            page.navigate(baseUrl() + "/bpjs-calculator");

            page.fill("#salary", "5000000"); // Below all ceilings
            page.click("#btn-calculate");
            page.waitForLoadState();

            // Should not show ceiling warning
            assertThat(page.locator("text=Catatan Batas Upah").count()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should calculate with different risk class")
        void shouldCalculateWithDifferentRiskClass() {
            page.navigate(baseUrl() + "/bpjs-calculator");

            page.fill("#salary", "10000000");
            page.selectOption("#riskClass", "3"); // Medium risk (0.89%)
            page.click("#btn-calculate");
            page.waitForLoadState();

            // JKK for class 3 should be 0.89% of 10M = 89,000
            assertThat(page.locator("[data-testid='jkk']").textContent()).contains("89,000");
        }
    }

    @Nested
    @DisplayName("3.3.3 Navigation")
    class Navigation {

        @Test
        @DisplayName("Should navigate from sidebar menu")
        void shouldNavigateFromSidebarMenu() {
            page.navigate(baseUrl() + "/dashboard");

            page.click("#nav-bpjs-calculator");
            page.waitForLoadState();

            assertThat(page.url()).contains("/bpjs-calculator");
        }
    }
}
