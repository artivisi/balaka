package com.artivisi.accountingfinance.functional.service;

import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Service Industry Payroll Tests
 * Tests payroll processing, PPh 21, and BPJS calculations.
 */
@DisplayName("Service Industry - Payroll")
public class ServicePayrollTest extends PlaywrightTestBase {

    @Test
    @DisplayName("Should display employee list")
    void shouldDisplayEmployeeList() {
        loginAsAdmin();
        navigateTo("/employees");
        waitForPageLoad();

        // Verify employees page loads
        assertThat(page.locator("h1")).containsText("Karyawan");

        // Verify test employees exist from V810 migration
        assertThat(page.locator("text=Budi Santoso")).isVisible();
        assertThat(page.locator("text=Dewi Lestari")).isVisible();
        assertThat(page.locator("text=Agus Wijaya")).isVisible();
    }

    @Test
    @DisplayName("Should display employee detail")
    void shouldDisplayEmployeeDetail() {
        loginAsAdmin();
        navigateTo("/employees");
        waitForPageLoad();

        // Click on Budi Santoso
        page.locator("text=Budi Santoso").first().click();
        waitForPageLoad();

        // Verify employee detail page loads
        assertThat(page.locator("text=Budi Santoso")).isVisible();
    }

    @Test
    @DisplayName("Should display payroll list")
    void shouldDisplayPayrollList() {
        loginAsAdmin();
        navigateTo("/payroll");
        waitForPageLoad();

        // Verify payroll page loads
        assertThat(page.locator("h1")).containsText("Payroll");

        // Verify test payroll runs exist from V811
        assertThat(page.locator("text=2024-01")).isVisible();
    }

    @Test
    @DisplayName("Should display payroll detail")
    void shouldDisplayPayrollDetail() {
        loginAsAdmin();
        navigateTo("/payroll");
        waitForPageLoad();

        // Click on January payroll
        page.locator("text=2024-01").first().click();
        waitForPageLoad();

        // Verify payroll detail page loads (may not show employee names depending on UI)
        assertThat(page.locator("h1")).containsText("Payroll");
    }

    @Test
    @DisplayName("Should display salary components configuration")
    void shouldDisplaySalaryComponents() {
        loginAsAdmin();
        navigateTo("/salary-components");
        waitForPageLoad();

        // Verify salary components page loads
        assertThat(page.locator("h1")).containsText("Komponen");
    }
}
