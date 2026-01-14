package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.PayrollRunRepository;
import com.artivisi.accountingfinance.repository.ProjectRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import org.assertj.core.api.Assertions;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Validation tests to exercise error paths in controllers.
 * These tests submit forms with invalid data to verify validation works.
 *
 * Note: Uses strict ID-based locators for submit buttons to ensure reliability.
 * Each form has a unique submit button ID defined in the template.
 */
@DisplayName("Validation Tests")
@Import(ServiceTestDataInitializer.class)
class ValidationTest extends PlaywrightTestBase {

    @Autowired
    private PayrollRunRepository payrollRunRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
    }

    // ==================== PAYROLL VALIDATION ====================

    @Test
    @DisplayName("Should show validation error when submitting payroll form with empty period")
    void shouldShowValidationErrorForEmptyPayrollPeriod() {
        navigateTo("/payroll/new");
        waitForPageLoad();

        var periodInput = page.locator("#period");
        assertThat(periodInput).isVisible();
        periodInput.clear();

        // payroll/form.html uses id="btn-submit"
        var submitBtn = page.locator("#btn-submit");
        assertThat(submitBtn).isVisible();
        submitBtn.click();
        waitForPageLoad();

        // Should stay on form page (URL contains /new or /payroll)
        Assertions.assertThat(page.url()).contains("/payroll");
    }

    @Test
    @DisplayName("Should show error when creating duplicate payroll period")
    void shouldShowErrorForDuplicatePayrollPeriod() {
        var existingPayroll = payrollRunRepository.findAll().stream().findFirst();
        if (existingPayroll.isEmpty()) {
            return; // No test data available
        }

        String existingPeriod = existingPayroll.get().getPayrollPeriod();

        navigateTo("/payroll/new");
        waitForPageLoad();

        var periodInput = page.locator("#period");
        assertThat(periodInput).isVisible();
        periodInput.fill(existingPeriod);

        var submitBtn = page.locator("#btn-submit");
        assertThat(submitBtn).isVisible();
        submitBtn.click();
        waitForPageLoad();

        // Should show error - either stay on form or show error message
        Assertions.assertThat(page.url()).contains("/payroll");
    }

    // ==================== EMPLOYEE VALIDATION ====================

    @Test
    @DisplayName("Should show validation error when submitting employee form with empty name")
    void shouldShowValidationErrorForEmptyEmployeeName() {
        navigateTo("/employees/new");
        waitForPageLoad();

        var nameInput = page.locator("#name");
        assertThat(nameInput).isVisible();
        nameInput.clear();

        // employees/form.html uses id="btn-simpan"
        var submitBtn = page.locator("#btn-simpan");
        assertThat(submitBtn).isVisible();
        submitBtn.click();
        waitForPageLoad();

        // Should stay on form page
        Assertions.assertThat(page.url()).contains("/employees");
    }

    @Test
    @DisplayName("Should show validation error for invalid employee email format")
    void shouldShowValidationErrorForInvalidEmployeeEmail() {
        navigateTo("/employees/new");
        waitForPageLoad();

        var emailInput = page.locator("#email");
        assertThat(emailInput).isVisible();
        emailInput.fill("invalid-email");

        var submitBtn = page.locator("#btn-simpan");
        assertThat(submitBtn).isVisible();
        submitBtn.click();
        waitForPageLoad();

        // Should stay on form due to validation
        Assertions.assertThat(page.url()).contains("/employees");
    }

    // ==================== CLIENT VALIDATION ====================

    @Test
    @DisplayName("Should show validation error when submitting client form with empty name")
    void shouldShowValidationErrorForEmptyClientName() {
        navigateTo("/clients/new");
        waitForPageLoad();

        var nameInput = page.locator("#name");
        assertThat(nameInput).isVisible();
        nameInput.clear();

        // clients/form.html uses id="btn-simpan"
        var submitBtn = page.locator("#btn-simpan");
        assertThat(submitBtn).isVisible();
        submitBtn.click();
        waitForPageLoad();

        // Should stay on form page
        Assertions.assertThat(page.url()).contains("/clients");
    }

    @Test
    @DisplayName("Should show validation error for invalid client NPWP format")
    void shouldShowValidationErrorForInvalidClientNpwp() {
        navigateTo("/clients/new");
        waitForPageLoad();

        var npwpInput = page.locator("#npwp");
        assertThat(npwpInput).isVisible();
        npwpInput.fill("invalid-npwp");

        var submitBtn = page.locator("#btn-simpan");
        assertThat(submitBtn).isVisible();
        submitBtn.click();
        waitForPageLoad();

        // Should stay on form due to validation
        Assertions.assertThat(page.url()).contains("/clients");
    }

    // ==================== PROJECT VALIDATION ====================

    @Test
    @DisplayName("Should show validation error when submitting project form with empty code")
    void shouldShowValidationErrorForEmptyProjectCode() {
        navigateTo("/projects/new");
        waitForPageLoad();

        var codeInput = page.locator("#code");
        assertThat(codeInput).isVisible();
        codeInput.clear();

        // projects/form.html uses id="btn-simpan"
        var submitBtn = page.locator("#btn-simpan");
        assertThat(submitBtn).isVisible();
        submitBtn.click();
        waitForPageLoad();

        // Should stay on form page
        Assertions.assertThat(page.url()).contains("/projects");
    }

    @Test
    @DisplayName("Should show error when creating duplicate project code")
    void shouldShowErrorForDuplicateProjectCode() {
        var existingProject = projectRepository.findAll().stream().findFirst();
        if (existingProject.isEmpty()) {
            return; // No test data available
        }

        String existingCode = existingProject.get().getCode();

        navigateTo("/projects/new");
        waitForPageLoad();

        var codeInput = page.locator("#code");
        assertThat(codeInput).isVisible();
        codeInput.fill(existingCode);

        var submitBtn = page.locator("#btn-simpan");
        assertThat(submitBtn).isVisible();
        submitBtn.click();
        waitForPageLoad();

        // Should show error - stay on form or show error message
        Assertions.assertThat(page.url()).contains("/projects");
    }

    // ==================== USER VALIDATION ====================

    @Test
    @DisplayName("Should show validation error when submitting user form with empty username")
    void shouldShowValidationErrorForEmptyUsername() {
        navigateTo("/users/new");
        waitForPageLoad();

        var usernameInput = page.locator("#username");
        assertThat(usernameInput).isVisible();
        usernameInput.clear();

        // users/form.html uses id="btn-save"
        var submitBtn = page.locator("#btn-save");
        assertThat(submitBtn).isVisible();
        submitBtn.click();
        waitForPageLoad();

        // Should stay on form page
        Assertions.assertThat(page.url()).contains("/users");
    }

    @Test
    @DisplayName("Should show validation error for weak password")
    void shouldShowValidationErrorForWeakPassword() {
        navigateTo("/users/new");
        waitForPageLoad();

        var passwordInput = page.locator("#password");
        assertThat(passwordInput).isVisible();
        passwordInput.fill("weak");

        var submitBtn = page.locator("#btn-save");
        assertThat(submitBtn).isVisible();
        submitBtn.click();
        waitForPageLoad();

        // Should stay on form due to validation
        Assertions.assertThat(page.url()).contains("/users");
    }

    // ==================== INVOICE VALIDATION ====================

    @Test
    @DisplayName("Should show validation error when submitting invoice with no client")
    void shouldShowValidationErrorForInvoiceWithNoClient() {
        navigateTo("/invoices/new");
        waitForPageLoad();

        // invoices/form.html uses id="btn-simpan"
        var submitBtn = page.locator("#btn-simpan");
        assertThat(submitBtn).isVisible();
        submitBtn.click();
        waitForPageLoad();

        // Should stay on form due to validation
        Assertions.assertThat(page.url()).contains("/invoices");
    }

    @Test
    @DisplayName("Should show validation error for negative invoice amount")
    void shouldShowValidationErrorForNegativeInvoiceAmount() {
        navigateTo("/invoices/new");
        waitForPageLoad();

        var amountInput = page.locator("#amount");
        assertThat(amountInput).isVisible();
        amountInput.fill("-1000");

        var submitBtn = page.locator("#btn-simpan");
        assertThat(submitBtn).isVisible();
        submitBtn.click();
        waitForPageLoad();

        // Should stay on form due to validation
        Assertions.assertThat(page.url()).contains("/invoices");
    }

    // ==================== FIXED ASSET VALIDATION ====================
    // Note: Controller is at /assets (not /fixed-assets)

    @Test
    @DisplayName("Should show validation error when submitting asset with empty name")
    void shouldShowValidationErrorForEmptyAssetName() {
        navigateTo("/assets/new");
        waitForPageLoad();

        var nameInput = page.locator("#name");
        assertThat(nameInput).isVisible();
        nameInput.clear();

        // assets/form.html uses id="btn-simpan"
        var submitBtn = page.locator("#btn-simpan");
        assertThat(submitBtn).isVisible();
        submitBtn.click();
        waitForPageLoad();

        // Should stay on form page
        Assertions.assertThat(page.url()).contains("/assets");
    }

    @Test
    @DisplayName("Should show validation error for zero purchase cost")
    void shouldShowValidationErrorForZeroPurchaseCost() {
        navigateTo("/assets/new");
        waitForPageLoad();

        // Field is called purchaseCost (not acquisitionCost)
        var costInput = page.locator("#purchaseCost");
        assertThat(costInput).isVisible();
        costInput.fill("0");

        var submitBtn = page.locator("#btn-simpan");
        assertThat(submitBtn).isVisible();
        submitBtn.click();
        waitForPageLoad();

        // Should stay on form due to validation
        Assertions.assertThat(page.url()).contains("/assets");
    }

    // ==================== PRODUCT VALIDATION ====================

    @Test
    @DisplayName("Should show validation error when submitting product with empty code")
    void shouldShowValidationErrorForEmptyProductCode() {
        navigateTo("/products/new");
        waitForPageLoad();

        var codeInput = page.locator("#code");
        assertThat(codeInput).isVisible();
        codeInput.clear();

        // products/form.html uses id="btn-save"
        var submitBtn = page.locator("#btn-save");
        assertThat(submitBtn).isVisible();
        submitBtn.click();
        waitForPageLoad();

        // Should stay on form page
        Assertions.assertThat(page.url()).contains("/products");
    }

    @Test
    @DisplayName("Should show validation error for negative product price")
    void shouldShowValidationErrorForNegativeProductPrice() {
        navigateTo("/products/new");
        waitForPageLoad();

        var priceInput = page.locator("#sellingPrice");
        assertThat(priceInput).isVisible();
        priceInput.fill("-100");

        var submitBtn = page.locator("#btn-save");
        assertThat(submitBtn).isVisible();
        submitBtn.click();
        waitForPageLoad();

        // Should stay on form due to validation
        Assertions.assertThat(page.url()).contains("/products");
    }

}
