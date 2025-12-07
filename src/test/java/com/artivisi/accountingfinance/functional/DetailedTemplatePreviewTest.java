package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.page.LoginPage;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Functional test for DETAILED template preview functionality.
 * DETAILED templates use custom variables in formulas (e.g., "revenueAmount", "cogsAmount")
 * instead of the simple "amount" variable.
 *
 * This test ensures that:
 * 1. The execute page shows variable input fields for DETAILED templates
 * 2. Preview works correctly with custom variable values
 * 3. No formula evaluation errors occur
 */
@DisplayName("DETAILED Template Preview Functionality")
class DetailedTemplatePreviewTest extends PlaywrightTestBase {

    private LoginPage loginPage;

    // Template ID from V004 seed data - "Penjualan Persediaan" (DETAILED type)
    // Variables: revenueAmount (selling price), cogsAmount (cost of goods sold)
    private static final String INVENTORY_SALE_TEMPLATE_ID = "f5000000-0000-0000-0000-000000000002";

    @BeforeEach
    void setUp() {
        loginPage = new LoginPage(page, baseUrl());
        loginPage.navigate().loginAsAdmin();
    }

    @Test
    @DisplayName("DETAILED template execute page should show variable input fields")
    void detailedTemplateExecutePageShouldShowVariableInputs() {
        // Navigate to template execute page
        page.navigate(baseUrl() + "/templates/" + INVENTORY_SALE_TEMPLATE_ID + "/execute");
        page.waitForLoadState();

        // Wait for Alpine.js to initialize
        page.waitForSelector("[x-data]");

        // Should show template info
        assertThat(page.locator("#template-name")).containsText("Penjualan Persediaan");

        // For DETAILED template, should NOT show the single "Jumlah" input
        // Instead should show variable inputs section
        assertThat(page.locator("text=Saldo per Akun")).isVisible();

        // Should show variable input fields (use data-testid or specific selectors)
        assertThat(page.locator("[data-var-name]")).not().hasCount(0);

        // Should show inputs for specific variables from the template
        // revenueAmount and cogsAmount
        assertThat(page.locator("[data-var-name='revenueAmount']")).isVisible();
        assertThat(page.locator("[data-var-name='cogsAmount']")).isVisible();
    }

    @Test
    @DisplayName("DETAILED template preview should work without formula evaluation error")
    void detailedTemplatePreviewShouldWorkWithoutError() {
        // Navigate to template execute page
        page.navigate(baseUrl() + "/templates/" + INVENTORY_SALE_TEMPLATE_ID + "/execute");
        page.waitForLoadState();

        // Wait for Alpine.js to initialize
        page.waitForSelector("[x-data]");

        // Fill in required fields
        page.fill("#description", "Test penjualan persediaan");

        // Fill in variable values
        // For Penjualan Persediaan template:
        // - revenueAmount = 15,000,000 (selling price)
        // - cogsAmount = 10,000,000 (cost of goods sold)
        page.fill("[data-var-name='revenueAmount']", "15000000");
        page.fill("[data-var-name='cogsAmount']", "10000000");

        // Select dynamic accounts for template lines with NULL account:
        // Line 2 (lineOrder=2): HPP account - 50000000-0000-0000-0000-000000000131 (HPP Barang Dagangan)
        // Line 3 (lineOrder=3): Revenue account - 40000000-0000-0000-0000-000000000104 (Pendapatan Penjualan Barang)
        // Line 4 (lineOrder=4): Inventory account - 10000000-0000-0000-0000-000000000151 (Persediaan Barang Dagangan)
        page.selectOption("#account_2", "50000000-0000-0000-0000-000000000131");
        page.selectOption("#account_3", "40000000-0000-0000-0000-000000000104");
        page.selectOption("#account_4", "10000000-0000-0000-0000-000000000151");

        // Click preview button
        page.click("#btn-preview");

        // Wait for preview result
        page.waitForTimeout(1000);

        // Should show preview section (not error)
        assertThat(page.locator("text=Preview Jurnal")).isVisible();

        // Should NOT show formula evaluation error
        assertThat(page.locator("text=Formula evaluation error")).not().isVisible();
        assertThat(page.locator("text=EL1008E")).not().isVisible();
        assertThat(page.locator("text=cannot be found")).not().isVisible();

        // Preview table should have rows
        Locator previewRows = page.locator("#preview-table tbody tr");
        assertThat(previewRows).not().hasCount(0);

        // Should show the amounts in the preview
        assertThat(page.locator("#preview-table")).containsText("15.000.000");
        assertThat(page.locator("#preview-table")).containsText("10.000.000");
    }

    @Test
    @DisplayName("DETAILED template should validate at least one variable has value")
    void detailedTemplateShouldValidateVariables() {
        // Navigate to template execute page
        page.navigate(baseUrl() + "/templates/" + INVENTORY_SALE_TEMPLATE_ID + "/execute");
        page.waitForLoadState();

        // Wait for Alpine.js to initialize
        page.waitForSelector("[x-data]");

        // Fill only required non-variable fields
        page.fill("#description", "Test validation");

        // Leave all variable inputs empty (or 0)

        // Click preview button
        page.click("#btn-preview");

        // Should show validation error
        page.waitForTimeout(500);
        assertThat(page.locator("text=Minimal satu akun harus memiliki nilai")).isVisible();
    }

    @Test
    @DisplayName("SIMPLE template should still show single amount field")
    void simpleTemplateShouldShowSingleAmountField() {
        // Template ID from seed data - "Pendapatan Jasa Konsultasi" (SIMPLE type)
        String simpleTemplateId = "e0000000-0000-0000-0000-000000000001";

        // Navigate to template execute page
        page.navigate(baseUrl() + "/templates/" + simpleTemplateId + "/execute");
        page.waitForLoadState();

        // Wait for Alpine.js to initialize
        page.waitForSelector("[x-data]");

        // Should show template info
        assertThat(page.locator("#template-name")).containsText("Pendapatan Jasa Konsultasi");

        // For SIMPLE template, should show the single "Jumlah" input
        assertThat(page.locator("#label-amount")).isVisible();
        assertThat(page.locator("#amount")).isVisible();

        // Should NOT show variable inputs section
        assertThat(page.locator("text=Saldo per Akun")).not().isVisible();
        assertThat(page.locator("[data-var-name]")).hasCount(0);
    }
}
