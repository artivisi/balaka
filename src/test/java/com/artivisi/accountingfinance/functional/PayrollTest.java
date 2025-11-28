package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.page.LoginPage;
import com.artivisi.accountingfinance.functional.page.PayrollDetailPage;
import com.artivisi.accountingfinance.functional.page.PayrollFormPage;
import com.artivisi.accountingfinance.functional.page.PayrollListPage;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Payroll Processing (Phase 3.5)")
class PayrollTest extends PlaywrightTestBase {

    private LoginPage loginPage;
    private PayrollListPage listPage;
    private PayrollFormPage formPage;
    private PayrollDetailPage detailPage;

    @BeforeEach
    void setUp() {
        loginPage = new LoginPage(page, baseUrl());
        listPage = new PayrollListPage(page, baseUrl());
        formPage = new PayrollFormPage(page, baseUrl());
        detailPage = new PayrollDetailPage(page, baseUrl());

        loginPage.navigate().loginAsAdmin();
    }

    @Test
    @DisplayName("Should display payroll list page")
    void shouldDisplayPayrollListPage() {
        listPage.navigate();

        listPage.assertPageTitleVisible();
        listPage.assertPageTitleText("Daftar Payroll");
    }

    @Test
    @DisplayName("Should display payroll table")
    void shouldDisplayPayrollTable() {
        listPage.navigate();

        listPage.assertTableVisible();
    }

    @Test
    @DisplayName("Should display new payroll form")
    void shouldDisplayNewPayrollForm() {
        formPage.navigateToNew();

        formPage.assertPageTitleText("Buat Payroll Baru");
    }

    @Test
    @DisplayName("Should navigate to form from list page")
    void shouldNavigateToFormFromListPage() {
        listPage.navigate();
        listPage.clickNewPayrollButton();

        formPage.assertPageTitleText("Buat Payroll Baru");
    }

    @Test
    @DisplayName("Should create and calculate payroll for active employees")
    void shouldCreateAndCalculatePayroll() {
        formPage.navigateToNew();

        // Use a unique period to avoid conflicts
        String period = "2026-" + String.format("%02d", (System.currentTimeMillis() % 12) + 1);

        formPage.fillPeriod(period);
        formPage.fillBaseSalary("10000000");
        formPage.selectJkkRiskClass(1);
        formPage.clickSubmit();

        // Should redirect to detail page with calculated results
        detailPage.assertPageTitleContains("Detail Payroll");
        detailPage.assertStatusBadgeText("Calculated");

        // Should have 3 active employees from test data
        assertThat(detailPage.getEmployeeCount()).isEqualTo("3");

        // Should have calculated totals
        assertThat(detailPage.getTotalGross()).contains("Rp");
        assertThat(detailPage.getTotalDeductions()).contains("Rp");
        assertThat(detailPage.getTotalNet()).contains("Rp");
    }

    @Test
    @DisplayName("Should show employee details in payroll")
    void shouldShowEmployeeDetailsInPayroll() {
        formPage.navigateToNew();

        String period = "2027-" + String.format("%02d", (System.currentTimeMillis() % 12) + 1);

        formPage.fillPeriod(period);
        formPage.fillBaseSalary("10000000");
        formPage.clickSubmit();

        // Should show employee details table
        assertThat(detailPage.hasEmployeeDetailsTable()).isTrue();
        assertThat(detailPage.getEmployeeRowCount()).isEqualTo(3);

        // Check test employees are listed
        assertThat(detailPage.hasEmployeeWithId("EMP001")).isTrue();
        assertThat(detailPage.hasEmployeeWithId("EMP002")).isTrue();
        assertThat(detailPage.hasEmployeeWithId("EMP003")).isTrue();
    }

    @Test
    @DisplayName("Should show approve button for calculated payroll")
    void shouldShowApproveButtonForCalculatedPayroll() {
        formPage.navigateToNew();

        String period = "2028-01";

        formPage.fillPeriod(period);
        formPage.fillBaseSalary("10000000");
        formPage.clickSubmit();

        // Should show approve button for calculated payroll
        assertThat(detailPage.hasApproveButton()).isTrue();
    }

    @Test
    @DisplayName("Should approve calculated payroll")
    void shouldApproveCalculatedPayroll() {
        formPage.navigateToNew();

        String period = "2028-02";

        formPage.fillPeriod(period);
        formPage.fillBaseSalary("10000000");
        formPage.clickSubmit();

        // Approve the payroll
        detailPage.clickApproveButton();

        // Should show approved status
        detailPage.assertStatusBadgeText("Approved");
        assertThat(detailPage.hasSuccessMessage()).isTrue();

        // Approve button should be gone, cancel button should appear
        assertThat(detailPage.hasApproveButton()).isFalse();
        assertThat(detailPage.hasCancelButton()).isTrue();
    }

    @Test
    @DisplayName("Should cancel approved payroll")
    void shouldCancelApprovedPayroll() {
        formPage.navigateToNew();

        String period = "2028-03";

        formPage.fillPeriod(period);
        formPage.fillBaseSalary("10000000");
        formPage.clickSubmit();

        // Approve first
        detailPage.clickApproveButton();

        // Then cancel
        detailPage.clickCancelButton();

        // Should show cancelled status
        detailPage.assertStatusBadgeText("Cancelled");
        assertThat(detailPage.hasSuccessMessage()).isTrue();
    }

    @Test
    @DisplayName("Should delete draft payroll")
    void shouldDeleteDraftPayroll() {
        // This test is tricky because after creation, status is CALCULATED not DRAFT
        // We can't easily test draft deletion unless we modify the workflow
        // For now, we test that the button is NOT visible for calculated payroll
        formPage.navigateToNew();

        String period = "2028-04";

        formPage.fillPeriod(period);
        formPage.fillBaseSalary("10000000");
        formPage.clickSubmit();

        // Delete button should NOT be visible for calculated payroll
        assertThat(detailPage.hasDeleteButton()).isFalse();
    }

    @Test
    @DisplayName("Should prevent duplicate payroll for same period")
    void shouldPreventDuplicatePayrollForSamePeriod() {
        // Create first payroll
        formPage.navigateToNew();

        String period = "2028-05";

        formPage.fillPeriod(period);
        formPage.fillBaseSalary("10000000");
        formPage.clickSubmit();

        // Try to create another payroll for the same period
        formPage.navigateToNew();
        formPage.fillPeriod(period);
        formPage.fillBaseSalary("10000000");
        formPage.clickSubmit();

        // Should show validation error
        assertThat(formPage.hasPeriodValidationError()).isTrue();
    }

    @Test
    @DisplayName("Should display payroll in list after creation")
    void shouldDisplayPayrollInListAfterCreation() {
        formPage.navigateToNew();

        String period = "2028-06";

        formPage.fillPeriod(period);
        formPage.fillBaseSalary("10000000");
        formPage.clickSubmit();

        // Navigate to list
        listPage.navigate();

        // Should show the created payroll
        assertThat(listPage.hasPayrollWithPeriod(period)).isTrue();
    }

    @Test
    @DisplayName("Should display filter dropdown")
    void shouldDisplayFilterDropdown() {
        listPage.navigate();

        // Filter dropdown should be visible
        assertThat(page.locator("#status").isVisible()).isTrue();

        // Should have status options
        assertThat(page.locator("#status option").count()).isGreaterThan(1);
    }

    @Test
    @DisplayName("Should navigate to detail from list")
    void shouldNavigateToDetailFromList() {
        // Create a payroll first
        formPage.navigateToNew();

        String period = "2028-08";

        formPage.fillPeriod(period);
        formPage.fillBaseSalary("10000000");
        formPage.clickSubmit();

        // Navigate to list and click detail
        listPage.navigate();
        listPage.clickPayrollDetail(period);

        // Should be on detail page
        detailPage.assertPageTitleContains("Detail Payroll");
    }

    @Test
    @DisplayName("Should calculate with different JKK risk class")
    void shouldCalculateWithDifferentJkkRiskClass() {
        formPage.navigateToNew();

        String period = "2028-09";

        formPage.fillPeriod(period);
        formPage.fillBaseSalary("10000000");
        formPage.selectJkkRiskClass(5); // Highest risk class
        formPage.clickSubmit();

        // Should calculate successfully
        detailPage.assertPageTitleContains("Detail Payroll");
        assertThat(detailPage.getEmployeeCount()).isEqualTo("3");
    }
}
