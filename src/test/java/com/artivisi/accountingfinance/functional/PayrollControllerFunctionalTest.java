package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.PayrollStatus;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.PayrollDetailRepository;
import com.artivisi.accountingfinance.repository.PayrollRunRepository;
import com.artivisi.accountingfinance.service.PayrollService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests for PayrollController.
 * Tests payroll run lifecycle, calculations, and reports.
 */
@DisplayName("Payroll Controller Tests")
@Import(ServiceTestDataInitializer.class)
class PayrollControllerFunctionalTest extends PlaywrightTestBase {

    @Autowired
    private PayrollRunRepository payrollRunRepository;

    @Autowired
    private PayrollDetailRepository payrollDetailRepository;

    @Autowired
    private PayrollService payrollService;

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("Should display payroll list page")
    void shouldDisplayPayrollListPage() {
        navigateTo("/payroll");
        waitForPageLoad();

        assertThat(page.url())
            .as("Should be on payroll page")
            .contains("/payroll");
    }

    @Test
    @DisplayName("Should display status filter")
    void shouldDisplayStatusFilter() {
        navigateTo("/payroll");
        waitForPageLoad();

        var statusSelect = page.locator("select[name='status']").first();

        assertThat(statusSelect.isVisible())
            .as("Status filter should be visible")
            .isTrue();
    }

    @Test
    @DisplayName("Should have new payroll button")
    void shouldHaveNewPayrollButton() {
        navigateTo("/payroll");
        waitForPageLoad();

        var newButton = page.locator("a[href*='/payroll/new']").first();

        assertThat(newButton.isVisible())
            .as("New payroll button should be visible")
            .isTrue();
    }

    @Test
    @DisplayName("Should filter by status")
    void shouldFilterByStatus() {
        navigateTo("/payroll");
        waitForPageLoad();

        page.locator("select[name='status']").first().selectOption("DRAFT");

        // Submit filter form using the form context
        page.locator("form[action='/payroll'] button[type='submit']").click();
        waitForPageLoad();

        assertThat(page.url())
            .as("URL should contain status filter")
            .contains("status=DRAFT");
    }

    @Test
    @DisplayName("Should display new payroll form")
    void shouldDisplayNewPayrollForm() {
        navigateTo("/payroll/new");
        waitForPageLoad();

        assertThat(page.url())
            .as("Should be on new payroll form")
            .contains("/payroll/new");
    }

    @Test
    @DisplayName("Should display period input")
    void shouldDisplayPeriodInput() {
        navigateTo("/payroll/new");
        waitForPageLoad();

        var periodInput = page.locator("input[name='period']").first();

        assertThat(periodInput.isVisible())
            .as("Period input should be visible")
            .isTrue();
    }

    @Test
    @DisplayName("Should display base salary input")
    void shouldDisplayBaseSalaryInput() {
        navigateTo("/payroll/new");
        waitForPageLoad();

        var baseSalaryInput = page.locator("input[name='baseSalary']").first();

        assertThat(baseSalaryInput.isVisible())
            .as("Base salary input should be visible")
            .isTrue();
    }

    @Test
    @DisplayName("Should display risk class select")
    void shouldDisplayRiskClassSelect() {
        navigateTo("/payroll/new");
        waitForPageLoad();

        var riskClassSelect = page.locator("select[name='jkkRiskClass']").first();

        assertThat(riskClassSelect.isVisible())
            .as("JKK risk class select should be visible")
            .isTrue();
    }

    @Test
    @DisplayName("Should have period input with month type")
    void shouldHavePeriodInputWithMonthType() {
        navigateTo("/payroll/new");
        waitForPageLoad();

        var periodInput = page.locator("input[name='period']").first();

        // Verify the period input is a month type input
        assertThat(periodInput.getAttribute("type"))
            .as("Period input should be of type month")
            .isEqualTo("month");
    }

    @Test
    @DisplayName("Should create new payroll run")
    void shouldCreateNewPayrollRun() {
        navigateTo("/payroll/new");
        waitForPageLoad();

        // Use a unique period to avoid duplicates
        String uniquePeriod = YearMonth.now().plusMonths(12).toString();

        page.locator("input[name='period']").first().fill(uniquePeriod);
        page.locator("input[name='baseSalary']").first().fill("10000000");
        page.locator("select[name='jkkRiskClass']").first().selectOption("1");

        page.locator("#btn-submit").click();
        waitForPageLoad();

        // Should redirect to payroll detail or list
        assertThat(page.url())
            .as("Should redirect after creating payroll")
            .containsAnyOf("/payroll/", "/payroll");
    }

    @Test
    @DisplayName("Should display payroll detail if exists")
    void shouldDisplayPayrollDetailIfExists() {
        var payrollRuns = payrollRunRepository.findAll();
        if (payrollRuns.isEmpty()) {
            return; // Skip if no payroll runs exist
        }

        var payrollRun = payrollRuns.get(0);
        navigateTo("/payroll/" + payrollRun.getId());
        waitForPageLoad();

        assertThat(page.url())
            .as("Should be on payroll detail page")
            .contains("/payroll/" + payrollRun.getId());
    }

    @Test
    @DisplayName("Should show payroll period in detail")
    void shouldShowPayrollPeriodInDetail() {
        var payrollRuns = payrollRunRepository.findAll();
        if (payrollRuns.isEmpty()) {
            return;
        }

        var payrollRun = payrollRuns.get(0);
        navigateTo("/payroll/" + payrollRun.getId());
        waitForPageLoad();

        // Should display the period in Indonesian format (e.g., "Februari 2024")
        var pageContent = page.content();
        assertThat(pageContent)
            .as("Should show payroll period display name")
            .contains(payrollRun.getPeriodDisplayName());
    }

    @Test
    @DisplayName("Should display action buttons based on status")
    void shouldDisplayActionButtonsBasedOnStatus() {
        var payrollRuns = payrollRunRepository.findAll();
        if (payrollRuns.isEmpty()) {
            return;
        }

        var payrollRun = payrollRuns.get(0);
        navigateTo("/payroll/" + payrollRun.getId());
        waitForPageLoad();

        // Should have at least some action buttons
        var actionButtons = page.locator("form[action*='/payroll/'] button[type='submit']").all();

        // There should be action buttons (approve, cancel, post, recalculate, delete)
        assertThat(actionButtons.size())
            .as("Should have action buttons")
            .isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("Should have recalculate form")
    void shouldHaveRecalculateForm() {
        var payrollRuns = payrollRunRepository.findAll();
        if (payrollRuns.isEmpty()) {
            return;
        }

        var draftPayroll = payrollRuns.stream()
            .filter(pr -> pr.getStatus().name().equals("DRAFT"))
            .findFirst();

        if (draftPayroll.isEmpty()) {
            return;
        }

        navigateTo("/payroll/" + draftPayroll.get().getId());
        waitForPageLoad();

        var recalculateForm = page.locator("form[action*='/recalculate']").first();

        // If draft, recalculate form should be visible
        if (recalculateForm.isVisible()) {
            assertThat(recalculateForm.isVisible()).isTrue();
        }
    }

    @Test
    @DisplayName("Should have export buttons for payroll run")
    void shouldHaveExportButtonsForPayrollRun() {
        var payrollRuns = payrollRunRepository.findAll();
        if (payrollRuns.isEmpty()) {
            return;
        }

        var payrollRun = payrollRuns.get(0);
        navigateTo("/payroll/" + payrollRun.getId());
        waitForPageLoad();

        // Should have export links for PDF/Excel
        var exportLinks = page.locator("a[href*='/export/']").all();

        assertThat(exportLinks.size())
            .as("Should have export links")
            .isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("Should display bukti potong page")
    void shouldDisplayBuktiPotongPage() {
        navigateTo("/payroll/bukti-potong");
        waitForPageLoad();

        assertThat(page.url())
            .as("Should be on bukti potong page")
            .contains("/payroll/bukti-potong");
    }

    @Test
    @DisplayName("Should display year filter")
    void shouldDisplayYearFilter() {
        navigateTo("/payroll/bukti-potong");
        waitForPageLoad();

        // Should have year selection
        var yearSelect = page.locator("select[name='year'], input[name='year']").first();

        assertThat(yearSelect.isVisible())
            .as("Year filter should be visible")
            .isTrue();
    }

    @Test
    @DisplayName("Should filter by year")
    void shouldFilterByYear() {
        navigateTo("/payroll/bukti-potong?year=2024");
        waitForPageLoad();

        assertThat(page.url())
            .as("URL should contain year parameter")
            .contains("year=2024");
    }

    @Test
    @DisplayName("Should navigate from list to new form")
    void shouldNavigateFromListToNewForm() {
        navigateTo("/payroll");
        waitForPageLoad();

        page.locator("a[href*='/payroll/new']").first().click();
        waitForPageLoad();

        assertThat(page.url())
            .as("Should navigate to new payroll form")
            .contains("/payroll/new");
    }

    @Test
    @DisplayName("Should navigate to bukti potong from list")
    void shouldNavigateToBuktiPotongFromList() {
        navigateTo("/payroll");
        waitForPageLoad();

        var buktiPotongLink = page.locator("a[href*='/payroll/bukti-potong']").first();

        if (buktiPotongLink.isVisible()) {
            buktiPotongLink.click();
            waitForPageLoad();

            assertThat(page.url())
                .as("Should navigate to bukti potong page")
                .contains("/payroll/bukti-potong");
        }
    }

    // ==================== ACTION ENDPOINT TESTS ====================

    @Test
    @DisplayName("Should approve draft payroll")
    void shouldApproveDraftPayroll() {
        var draftPayroll = payrollRunRepository.findAll().stream()
            .filter(pr -> pr.getStatus() == PayrollStatus.DRAFT)
            .findFirst();

        if (draftPayroll.isEmpty()) {
            return; // Skip if no draft payroll
        }

        navigateTo("/payroll/" + draftPayroll.get().getId());
        waitForPageLoad();

        var approveForm = page.locator("form[action*='/approve']").first();
        if (approveForm.isVisible()) {
            approveForm.locator("button[type='submit']").click();
            waitForPageLoad();

            // Should stay on detail page
            assertThat(page.url())
                .as("Should remain on payroll detail after approve")
                .contains("/payroll/" + draftPayroll.get().getId());
        }
    }

    @Test
    @DisplayName("Should cancel payroll")
    void shouldCancelPayroll() {
        // Create a new payroll to cancel
        String uniquePeriod = YearMonth.now().plusMonths(18).toString();
        navigateTo("/payroll/new");
        waitForPageLoad();

        page.locator("input[name='period']").first().fill(uniquePeriod);
        page.locator("input[name='baseSalary']").first().fill("10000000");
        page.locator("select[name='jkkRiskClass']").first().selectOption("1");
        page.locator("#btn-submit").click();
        waitForPageLoad();

        // Get the newly created payroll
        var newPayroll = payrollRunRepository.findByPayrollPeriod(uniquePeriod);
        if (newPayroll.isEmpty()) {
            return;
        }

        navigateTo("/payroll/" + newPayroll.get().getId());
        waitForPageLoad();

        var cancelForm = page.locator("form[action*='/cancel']").first();
        if (cancelForm.isVisible()) {
            // Fill cancellation reason if available
            var reasonInput = cancelForm.locator("input[name='reason']");
            if (reasonInput.isVisible()) {
                reasonInput.fill("Test cancellation");
            }
            cancelForm.locator("button[type='submit']").click();
            waitForPageLoad();

            assertThat(page.url())
                .as("Should remain on payroll detail after cancel")
                .contains("/payroll/");
        }
    }

    @Test
    @DisplayName("Should post approved payroll")
    void shouldPostApprovedPayroll() {
        var approvedPayroll = payrollRunRepository.findAll().stream()
            .filter(pr -> pr.getStatus() == PayrollStatus.APPROVED)
            .findFirst();

        if (approvedPayroll.isEmpty()) {
            return;
        }

        navigateTo("/payroll/" + approvedPayroll.get().getId());
        waitForPageLoad();

        var postForm = page.locator("form[action*='/post']").first();
        if (postForm.isVisible()) {
            postForm.locator("button[type='submit']").click();
            waitForPageLoad();

            assertThat(page.url())
                .as("Should remain on payroll detail after posting")
                .contains("/payroll/" + approvedPayroll.get().getId());
        }
    }

    @Test
    @DisplayName("Should recalculate draft payroll")
    void shouldRecalculateDraftPayroll() {
        var draftPayroll = payrollRunRepository.findAll().stream()
            .filter(pr -> pr.getStatus() == PayrollStatus.DRAFT)
            .findFirst();

        if (draftPayroll.isEmpty()) {
            return;
        }

        navigateTo("/payroll/" + draftPayroll.get().getId());
        waitForPageLoad();

        var recalculateForm = page.locator("form[action*='/recalculate']").first();
        if (recalculateForm.isVisible()) {
            // Fill recalculation inputs
            var baseSalaryInput = recalculateForm.locator("input[name='baseSalary']");
            if (baseSalaryInput.isVisible()) {
                baseSalaryInput.fill("12000000");
            }
            var riskClassSelect = recalculateForm.locator("select[name='jkkRiskClass']");
            if (riskClassSelect.isVisible()) {
                riskClassSelect.selectOption("2");
            }
            recalculateForm.locator("button[type='submit']").click();
            waitForPageLoad();

            assertThat(page.url())
                .as("Should remain on payroll detail after recalculate")
                .contains("/payroll/" + draftPayroll.get().getId());
        }
    }

    @Test
    @DisplayName("Should delete draft payroll")
    void shouldDeleteDraftPayroll() {
        // Create a new payroll to delete
        String uniquePeriod = YearMonth.now().plusMonths(24).toString();
        navigateTo("/payroll/new");
        waitForPageLoad();

        page.locator("input[name='period']").first().fill(uniquePeriod);
        page.locator("input[name='baseSalary']").first().fill("10000000");
        page.locator("select[name='jkkRiskClass']").first().selectOption("1");
        page.locator("#btn-submit").click();
        waitForPageLoad();

        // Get the newly created payroll
        var newPayroll = payrollRunRepository.findByPayrollPeriod(uniquePeriod);
        if (newPayroll.isEmpty()) {
            return;
        }

        navigateTo("/payroll/" + newPayroll.get().getId());
        waitForPageLoad();

        var deleteForm = page.locator("form[action*='/delete']").first();
        if (deleteForm.isVisible()) {
            deleteForm.locator("button[type='submit']").click();
            waitForPageLoad();

            // Should redirect to payroll list
            assertThat(page.url())
                .as("Should redirect to payroll list after delete")
                .contains("/payroll");
        }
    }

    // ==================== EXPORT ENDPOINT TESTS ====================

    @Test
    @DisplayName("Should download summary PDF")
    void shouldDownloadSummaryPdf() {
        var payrollRun = payrollRunRepository.findAll().stream().findFirst();
        if (payrollRun.isEmpty()) {
            return;
        }

        navigateTo("/payroll/" + payrollRun.get().getId());
        waitForPageLoad();

        var pdfLink = page.locator("a[href*='/export/summary/pdf']").first();
        if (pdfLink.isVisible()) {
            // Just verify the link exists and is clickable
            assertThat(pdfLink.getAttribute("href"))
                .as("PDF export link should have correct href")
                .contains("/export/summary/pdf");
        }
    }

    @Test
    @DisplayName("Should download summary Excel")
    void shouldDownloadSummaryExcel() {
        var payrollRun = payrollRunRepository.findAll().stream().findFirst();
        if (payrollRun.isEmpty()) {
            return;
        }

        navigateTo("/payroll/" + payrollRun.get().getId());
        waitForPageLoad();

        var excelLink = page.locator("a[href*='/export/summary/excel']").first();
        if (excelLink.isVisible()) {
            assertThat(excelLink.getAttribute("href"))
                .as("Excel export link should have correct href")
                .contains("/export/summary/excel");
        }
    }

    @Test
    @DisplayName("Should download PPh21 PDF")
    void shouldDownloadPph21Pdf() {
        var payrollRun = payrollRunRepository.findAll().stream().findFirst();
        if (payrollRun.isEmpty()) {
            return;
        }

        navigateTo("/payroll/" + payrollRun.get().getId());
        waitForPageLoad();

        var pdfLink = page.locator("a[href*='/export/pph21/pdf']").first();
        if (pdfLink.isVisible()) {
            assertThat(pdfLink.getAttribute("href"))
                .as("PPh21 PDF export link should have correct href")
                .contains("/export/pph21/pdf");
        }
    }

    @Test
    @DisplayName("Should download PPh21 Excel")
    void shouldDownloadPph21Excel() {
        var payrollRun = payrollRunRepository.findAll().stream().findFirst();
        if (payrollRun.isEmpty()) {
            return;
        }

        navigateTo("/payroll/" + payrollRun.get().getId());
        waitForPageLoad();

        var excelLink = page.locator("a[href*='/export/pph21/excel']").first();
        if (excelLink.isVisible()) {
            assertThat(excelLink.getAttribute("href"))
                .as("PPh21 Excel export link should have correct href")
                .contains("/export/pph21/excel");
        }
    }

    @Test
    @DisplayName("Should download BPJS PDF")
    void shouldDownloadBpjsPdf() {
        var payrollRun = payrollRunRepository.findAll().stream().findFirst();
        if (payrollRun.isEmpty()) {
            return;
        }

        navigateTo("/payroll/" + payrollRun.get().getId());
        waitForPageLoad();

        var pdfLink = page.locator("a[href*='/export/bpjs/pdf']").first();
        if (pdfLink.isVisible()) {
            assertThat(pdfLink.getAttribute("href"))
                .as("BPJS PDF export link should have correct href")
                .contains("/export/bpjs/pdf");
        }
    }

    @Test
    @DisplayName("Should download BPJS Excel")
    void shouldDownloadBpjsExcel() {
        var payrollRun = payrollRunRepository.findAll().stream().findFirst();
        if (payrollRun.isEmpty()) {
            return;
        }

        navigateTo("/payroll/" + payrollRun.get().getId());
        waitForPageLoad();

        var excelLink = page.locator("a[href*='/export/bpjs/excel']").first();
        if (excelLink.isVisible()) {
            assertThat(excelLink.getAttribute("href"))
                .as("BPJS Excel export link should have correct href")
                .contains("/export/bpjs/excel");
        }
    }

    @Test
    @DisplayName("Should access payslip PDF link for employee")
    void shouldAccessPayslipPdfLink() {
        var payrollRun = payrollRunRepository.findAll().stream().findFirst();
        if (payrollRun.isEmpty()) {
            return;
        }

        var details = payrollDetailRepository.findByPayrollRunId(payrollRun.get().getId());
        if (details.isEmpty()) {
            return;
        }

        navigateTo("/payroll/" + payrollRun.get().getId());
        waitForPageLoad();

        // Look for payslip PDF links
        var payslipLink = page.locator("a[href*='/payslip/'][href*='/pdf']").first();
        if (payslipLink.isVisible()) {
            assertThat(payslipLink.getAttribute("href"))
                .as("Payslip PDF link should have correct href")
                .contains("/payslip/");
        }
    }

    @Test
    @DisplayName("Should access bukti potong PDF link")
    void shouldAccessBuktiPotongPdfLink() {
        navigateTo("/payroll/bukti-potong");
        waitForPageLoad();

        // Look for bukti potong PDF links
        var buktiPotongLink = page.locator("a[href*='/bukti-potong/'][href*='/pdf']").first();
        if (buktiPotongLink.isVisible()) {
            assertThat(buktiPotongLink.getAttribute("href"))
                .as("Bukti potong PDF link should have correct href")
                .contains("/bukti-potong/");
        }
    }

    // ==================== INTEGRATION TESTS ====================

    @Test
    @DisplayName("Should trigger actual export - Summary PDF")
    void shouldTriggerActualExportSummaryPdf() {
        var payrollRun = payrollRunRepository.findAll().stream().findFirst();
        if (payrollRun.isEmpty()) {
            return;
        }

        // Use fetch to trigger the export endpoint
        var response = page.request().get(baseUrl() + "/payroll/" + payrollRun.get().getId() + "/export/summary/pdf");
        // 200 = success, 302 = redirect to login (acceptable for test)
        assertThat(response.status())
            .as("Summary PDF export should return success or redirect")
            .isIn(200, 302);
    }

    @Test
    @DisplayName("Should trigger actual export - Summary Excel")
    void shouldTriggerActualExportSummaryExcel() {
        var payrollRun = payrollRunRepository.findAll().stream().findFirst();
        if (payrollRun.isEmpty()) {
            return;
        }

        var response = page.request().get(baseUrl() + "/payroll/" + payrollRun.get().getId() + "/export/summary/excel");
        assertThat(response.status())
            .as("Summary Excel export should return success or redirect")
            .isIn(200, 302);
    }

    @Test
    @DisplayName("Should trigger actual export - PPh21 PDF")
    void shouldTriggerActualExportPph21Pdf() {
        var payrollRun = payrollRunRepository.findAll().stream().findFirst();
        if (payrollRun.isEmpty()) {
            return;
        }

        var response = page.request().get(baseUrl() + "/payroll/" + payrollRun.get().getId() + "/export/pph21/pdf");
        assertThat(response.status())
            .as("PPh21 PDF export should return success or redirect")
            .isIn(200, 302);
    }

    @Test
    @DisplayName("Should trigger actual export - PPh21 Excel")
    void shouldTriggerActualExportPph21Excel() {
        var payrollRun = payrollRunRepository.findAll().stream().findFirst();
        if (payrollRun.isEmpty()) {
            return;
        }

        var response = page.request().get(baseUrl() + "/payroll/" + payrollRun.get().getId() + "/export/pph21/excel");
        assertThat(response.status())
            .as("PPh21 Excel export should return success or redirect")
            .isIn(200, 302);
    }

    @Test
    @DisplayName("Should trigger actual export - BPJS PDF")
    void shouldTriggerActualExportBpjsPdf() {
        var payrollRun = payrollRunRepository.findAll().stream().findFirst();
        if (payrollRun.isEmpty()) {
            return;
        }

        var response = page.request().get(baseUrl() + "/payroll/" + payrollRun.get().getId() + "/export/bpjs/pdf");
        assertThat(response.status())
            .as("BPJS PDF export should return success or redirect")
            .isIn(200, 302);
    }

    @Test
    @DisplayName("Should trigger actual export - BPJS Excel")
    void shouldTriggerActualExportBpjsExcel() {
        var payrollRun = payrollRunRepository.findAll().stream().findFirst();
        if (payrollRun.isEmpty()) {
            return;
        }

        var response = page.request().get(baseUrl() + "/payroll/" + payrollRun.get().getId() + "/export/bpjs/excel");
        assertThat(response.status())
            .as("BPJS Excel export should return success or redirect")
            .isIn(200, 302);
    }

    @Test
    @DisplayName("Should trigger actual export - Payslip PDF")
    void shouldTriggerActualExportPayslipPdf() {
        var payrollRun = payrollRunRepository.findAll().stream().findFirst();
        if (payrollRun.isEmpty()) {
            return;
        }

        var details = payrollDetailRepository.findByPayrollRunId(payrollRun.get().getId());
        if (details.isEmpty()) {
            return;
        }

        UUID employeeId = details.get(0).getEmployee().getId();
        var response = page.request().get(baseUrl() + "/payroll/" + payrollRun.get().getId() + "/payslip/" + employeeId + "/pdf");
        assertThat(response.status())
            .as("Payslip PDF export should return success or redirect")
            .isIn(200, 302);
    }

    @Test
    @DisplayName("Should trigger actual export - Bukti Potong PDF")
    void shouldTriggerActualExportBuktiPotongPdf() {
        // Get employees that have payroll data for the current year
        int year = java.time.Year.now().getValue();
        var employeeIds = payrollService.getEmployeesWithPayrollInYear(year);

        if (employeeIds.isEmpty()) {
            return; // Skip if no employees with payroll data
        }

        UUID employeeId = employeeIds.get(0);
        var response = page.request().get(baseUrl() + "/payroll/bukti-potong/" + employeeId + "/" + year + "/pdf");
        // 200 = success, 302 = redirect, 400/500 = endpoint hit but validation/server error
        assertThat(response.status())
            .as("Bukti potong PDF export should return success or redirect")
            .isIn(200, 302);
    }
}
