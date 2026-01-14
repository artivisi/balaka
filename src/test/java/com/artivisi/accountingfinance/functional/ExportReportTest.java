package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.PayrollRunRepository;
import com.artivisi.accountingfinance.repository.InvoiceRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.time.Year;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Tests for export and report endpoints.
 *
 * Note: Uses ".first()" on multi-element locators to avoid strict mode violations.
 * Tests only cover existing routes verified against controllers.
 * Download endpoints (PDF/Excel exports) cannot be tested via navigation.
 */
@DisplayName("Export and Report Tests")
@Import(ServiceTestDataInitializer.class)
class ExportReportTest extends PlaywrightTestBase {

    @Autowired
    private PayrollRunRepository payrollRunRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
    }

    // ==================== PAYROLL EXPORTS ====================

    @Test
    @DisplayName("Should access payroll export PDF endpoint")
    void shouldAccessPayrollExportPdf() {
        var payroll = payrollRunRepository.findAll().stream().findFirst();
        if (payroll.isEmpty()) {
            return; // No test data
        }

        navigateTo("/payroll/" + payroll.get().getId() + "/export/pdf");
        page.waitForTimeout(500);

        // Verify not on error page
        Assertions.assertThat(page.url()).doesNotContain("/error");
    }

    @Test
    @DisplayName("Should access payroll export Excel endpoint")
    void shouldAccessPayrollExportExcel() {
        var payroll = payrollRunRepository.findAll().stream().findFirst();
        if (payroll.isEmpty()) {
            return; // No test data
        }

        navigateTo("/payroll/" + payroll.get().getId() + "/export/excel");
        page.waitForTimeout(500);

        Assertions.assertThat(page.url()).doesNotContain("/error");
    }

    // Note: PPh21, BPJS, and Payslip export endpoints return file downloads
    // which cannot be tested via direct navigation (Playwright throws "Download is starting" error).
    // These endpoints are covered by unit/integration tests instead.

    @Test
    @DisplayName("Should display bukti potong page")
    void shouldDisplayBuktiPotongPage() {
        navigateTo("/payroll/bukti-potong");
        waitForPageLoad();

        Assertions.assertThat(page.url()).contains("/bukti-potong");
        // Use .first() to avoid strict mode violation when multiple h1/h2 elements exist
        assertThat(page.locator("h1, h2, .page-title").first()).isVisible();
    }

    @Test
    @DisplayName("Should display bukti potong page with year filter")
    void shouldDisplayBuktiPotongPageWithYearFilter() {
        int currentYear = Year.now().getValue();
        navigateTo("/payroll/bukti-potong?year=" + currentYear);
        waitForPageLoad();

        Assertions.assertThat(page.url()).contains("/bukti-potong");
        assertThat(page.locator("h1, h2, .page-title").first()).isVisible();
    }

    // Note: Bukti potong PDF endpoint returns file download,
    // cannot be tested via direct navigation.

    // ==================== FINANCIAL REPORTS ====================

    @Test
    @DisplayName("Should display income statement report")
    void shouldDisplayIncomeStatementReport() {
        navigateTo("/reports/income-statement");
        waitForPageLoad();

        Assertions.assertThat(page.url()).contains("/income-statement");
        assertThat(page.locator("h1, h2, .page-title").first()).isVisible();
    }

    @Test
    @DisplayName("Should display income statement with date range")
    void shouldDisplayIncomeStatementWithDateRange() {
        LocalDate start = LocalDate.now().minusMonths(1).withDayOfMonth(1);
        LocalDate end = LocalDate.now();
        navigateTo("/reports/income-statement?startDate=" + start + "&endDate=" + end);
        waitForPageLoad();

        Assertions.assertThat(page.url()).contains("/income-statement");
        assertThat(page.locator("h1, h2, .page-title").first()).isVisible();
    }

    @Test
    @DisplayName("Should display balance sheet report")
    void shouldDisplayBalanceSheetReport() {
        navigateTo("/reports/balance-sheet");
        waitForPageLoad();

        Assertions.assertThat(page.url()).contains("/balance-sheet");
        assertThat(page.locator("h1, h2, .page-title").first()).isVisible();
    }

    @Test
    @DisplayName("Should display balance sheet with date")
    void shouldDisplayBalanceSheetWithDate() {
        LocalDate date = LocalDate.now();
        navigateTo("/reports/balance-sheet?date=" + date);
        waitForPageLoad();

        Assertions.assertThat(page.url()).contains("/balance-sheet");
        assertThat(page.locator("h1, h2, .page-title").first()).isVisible();
    }

    @Test
    @DisplayName("Should display trial balance report")
    void shouldDisplayTrialBalanceReport() {
        navigateTo("/reports/trial-balance");
        waitForPageLoad();

        Assertions.assertThat(page.url()).contains("/trial-balance");
        assertThat(page.locator("h1, h2, .page-title").first()).isVisible();
    }

    @Test
    @DisplayName("Should display cash flow report")
    void shouldDisplayCashFlowReport() {
        navigateTo("/reports/cash-flow");
        waitForPageLoad();

        Assertions.assertThat(page.url()).contains("/cash-flow");
        assertThat(page.locator("h1, h2, .page-title").first()).isVisible();
    }

    // ==================== TAX REPORTS ====================

    @Test
    @DisplayName("Should display PPN summary report")
    void shouldDisplayPpnSummaryReport() {
        navigateTo("/reports/ppn-summary");
        waitForPageLoad();

        Assertions.assertThat(page.url()).contains("/ppn-summary");
        assertThat(page.locator("h1, h2, .page-title").first()).isVisible();
    }

    @Test
    @DisplayName("Should display PPh23 withholding report")
    void shouldDisplayPph23WithholdingReport() {
        navigateTo("/reports/pph23-withholding");
        waitForPageLoad();

        Assertions.assertThat(page.url()).contains("/pph23-withholding");
        assertThat(page.locator("h1, h2, .page-title").first()).isVisible();
    }

    @Test
    @DisplayName("Should display tax summary report")
    void shouldDisplayTaxSummaryReport() {
        navigateTo("/reports/tax-summary");
        waitForPageLoad();

        Assertions.assertThat(page.url()).contains("/tax-summary");
        assertThat(page.locator("h1, h2, .page-title").first()).isVisible();
    }

    // ==================== DEPRECIATION REPORTS ====================

    @Test
    @DisplayName("Should display depreciation report")
    void shouldDisplayDepreciationReport() {
        navigateTo("/reports/depreciation");
        waitForPageLoad();

        Assertions.assertThat(page.url()).contains("/depreciation");
        assertThat(page.locator("h1, h2, .page-title").first()).isVisible();
    }

    // ==================== INVOICE EXPORTS ====================

    @Test
    @DisplayName("Should access invoice PDF export")
    void shouldAccessInvoicePdfExport() {
        var invoice = invoiceRepository.findAll().stream().findFirst();
        if (invoice.isEmpty()) {
            return; // No test data
        }

        navigateTo("/invoices/" + invoice.get().getId() + "/pdf");
        page.waitForTimeout(500);

        Assertions.assertThat(page.url()).doesNotContain("/error");
    }

    // ==================== DATA EXPORT ====================

    @Test
    @DisplayName("Should display data export page")
    void shouldDisplayDataExportPage() {
        navigateTo("/settings/export");
        waitForPageLoad();

        Assertions.assertThat(page.url()).contains("/export");
        assertThat(page.locator("h1, h2, .page-title").first()).isVisible();
    }

    @Test
    @DisplayName("Should display audit logs page")
    void shouldDisplayAuditLogsPage() {
        navigateTo("/settings/audit-logs");
        waitForPageLoad();

        Assertions.assertThat(page.url()).contains("/audit-logs");
        assertThat(page.locator("h1, h2, .page-title").first()).isVisible();
    }

    @Test
    @DisplayName("Should display audit logs with date filter")
    void shouldDisplayAuditLogsWithDateFilter() {
        LocalDate start = LocalDate.now().minusDays(7);
        LocalDate end = LocalDate.now();
        navigateTo("/settings/audit-logs?startDate=" + start + "&endDate=" + end);
        waitForPageLoad();

        Assertions.assertThat(page.url()).contains("/audit-logs");
        assertThat(page.locator("h1, h2, .page-title").first()).isVisible();
    }

    // ==================== INVENTORY REPORTS ====================
    // Note: Inventory reports are at /inventory/reports (not /reports/inventory)

    @Test
    @DisplayName("Should display inventory stock balance report")
    void shouldDisplayStockBalanceReport() {
        navigateTo("/inventory/reports/stock-balance");
        waitForPageLoad();

        Assertions.assertThat(page.url()).contains("/stock-balance");
        assertThat(page.locator("h1, h2, .page-title").first()).isVisible();
    }

    @Test
    @DisplayName("Should display stock valuation report")
    void shouldDisplayStockValuationReport() {
        navigateTo("/inventory/reports/valuation");
        waitForPageLoad();

        Assertions.assertThat(page.url()).contains("/valuation");
        assertThat(page.locator("h1, h2, .page-title").first()).isVisible();
    }

    @Test
    @DisplayName("Should display product profitability report")
    void shouldDisplayProductProfitabilityReport() {
        navigateTo("/inventory/reports/profitability");
        waitForPageLoad();

        Assertions.assertThat(page.url()).contains("/profitability");
        assertThat(page.locator("h1, h2, .page-title").first()).isVisible();
    }

    @Test
    @DisplayName("Should display stock movement report")
    void shouldDisplayStockMovementReport() {
        navigateTo("/inventory/reports/stock-movement");
        waitForPageLoad();

        Assertions.assertThat(page.url()).contains("/stock-movement");
        assertThat(page.locator("h1, h2, .page-title").first()).isVisible();
    }

    // ==================== PROJECT REPORTS ====================

    @Test
    @DisplayName("Should display project profitability report")
    void shouldDisplayProjectProfitabilityReport() {
        navigateTo("/reports/project-profitability");
        waitForPageLoad();

        Assertions.assertThat(page.url()).contains("/project-profitability");
        assertThat(page.locator("h1, h2, .page-title").first()).isVisible();
    }

    @Test
    @DisplayName("Should display client profitability report")
    void shouldDisplayClientProfitabilityReport() {
        navigateTo("/reports/client-profitability");
        waitForPageLoad();

        Assertions.assertThat(page.url()).contains("/client-profitability");
        assertThat(page.locator("h1, h2, .page-title").first()).isVisible();
    }

    @Test
    @DisplayName("Should display client ranking report")
    void shouldDisplayClientRankingReport() {
        navigateTo("/reports/client-ranking");
        waitForPageLoad();

        Assertions.assertThat(page.url()).contains("/client-ranking");
        assertThat(page.locator("h1, h2, .page-title").first()).isVisible();
    }
}
