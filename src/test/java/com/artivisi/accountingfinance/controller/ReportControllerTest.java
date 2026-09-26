package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.security.CspNonceFilter;
import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.service.CompanyConfigService;
import com.artivisi.accountingfinance.service.FiscalYearClosingService;
import com.artivisi.accountingfinance.service.ReportExportService;
import com.artivisi.accountingfinance.service.ReportService;
import com.artivisi.accountingfinance.service.TaxReportDetailService;
import com.artivisi.accountingfinance.service.TaxReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Integration tests for ReportController.
 * Covers export endpoints (PDF/Excel), fiscal closing, tax report pages, and print pages.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "admin", authorities = {"REPORT_VIEW", "REPORT_EXPORT", "TAX_REPORT_VIEW", "TAX_EXPORT",
        "TRANSACTION_VIEW", "SETTINGS_VIEW", "FISCAL_PERIOD_MANAGE"})
@DisplayName("ReportController Integration Tests")
class ReportControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private ReportService reportService;

    @Autowired
    private ReportExportService reportExportService;

    @Autowired
    private TaxReportService taxReportService;

    @Autowired
    private TaxReportDetailService taxReportDetailService;

    @Autowired
    private FiscalYearClosingService fiscalYearClosingService;

    @Autowired
    private CompanyConfigService companyConfigService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                // Servlet container registers CspNonceFilter ahead of Spring Security (WebMvcConfig);
                // MockMvc only adds filters it is given.
                .addFilters(webApplicationContext.getBean(CspNonceFilter.class))
                .apply(springSecurity())
                .build();
    }

    @Nested
    @DisplayName("Export Endpoints - PDF")
    class PdfExportTests {

        @Test
        @DisplayName("Should export trial balance to PDF with default date")
        void shouldExportTrialBalanceToPdfWithDefaultDate() throws Exception {
            var result = mockMvc.perform(get("/reports/trial-balance/export/pdf"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "application/pdf"))
                    .andReturn();

            assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty();
            assertThat(result.getResponse().getHeader("Content-Disposition")).contains("neraca-saldo-");
        }

        @Test
        @DisplayName("Should export trial balance to PDF with specific date")
        void shouldExportTrialBalanceToPdfWithSpecificDate() throws Exception {
            var result = mockMvc.perform(get("/reports/trial-balance/export/pdf")
                            .param("asOfDate", "2024-06-30"))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(result.getResponse().getHeader("Content-Disposition"))
                    .contains("neraca-saldo-20240630.pdf");
        }

        @Test
        @DisplayName("Should export balance sheet to PDF")
        void shouldExportBalanceSheetToPdf() throws Exception {
            var result = mockMvc.perform(get("/reports/balance-sheet/export/pdf")
                            .param("asOfDate", "2024-06-30"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "application/pdf"))
                    .andReturn();

            assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty();
            assertThat(result.getResponse().getHeader("Content-Disposition"))
                    .contains("laporan-posisi-keuangan-20240630.pdf");
        }

        @Test
        @DisplayName("Should export income statement to PDF")
        void shouldExportIncomeStatementToPdf() throws Exception {
            var result = mockMvc.perform(get("/reports/income-statement/export/pdf")
                            .param("startDate", "2024-01-01")
                            .param("endDate", "2024-06-30"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "application/pdf"))
                    .andReturn();

            assertThat(result.getResponse().getHeader("Content-Disposition"))
                    .contains("laporan-laba-rugi-20240101-20240630.pdf");
        }

        @Test
        @DisplayName("Should export income statement to PDF with default dates")
        void shouldExportIncomeStatementToPdfWithDefaultDates() throws Exception {
            var result = mockMvc.perform(get("/reports/income-statement/export/pdf"))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty();
        }

        @Test
        @DisplayName("Should export cash flow to PDF")
        void shouldExportCashFlowToPdf() throws Exception {
            var result = mockMvc.perform(get("/reports/cash-flow/export/pdf")
                            .param("startDate", "2024-01-01")
                            .param("endDate", "2024-06-30"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "application/pdf"))
                    .andReturn();

            assertThat(result.getResponse().getHeader("Content-Disposition"))
                    .contains("laporan-arus-kas-20240101-20240630.pdf");
        }

        @Test
        @DisplayName("Should export depreciation report to PDF")
        void shouldExportDepreciationToPdf() throws Exception {
            var result = mockMvc.perform(get("/reports/depreciation/export/pdf")
                            .param("year", "2024"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "application/pdf"))
                    .andReturn();

            assertThat(result.getResponse().getHeader("Content-Disposition"))
                    .contains("laporan-penyusutan-2024.pdf");
        }

        @Test
        @DisplayName("Should export PPN detail to PDF")
        void shouldExportPpnDetailToPdf() throws Exception {
            var result = mockMvc.perform(get("/reports/ppn-detail/export/pdf")
                            .param("startDate", "2024-01-01")
                            .param("endDate", "2024-06-30"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "application/pdf"))
                    .andReturn();

            assertThat(result.getResponse().getHeader("Content-Disposition"))
                    .contains("rincian-ppn-");
        }

        @Test
        @DisplayName("Should export PPh23 detail to PDF")
        void shouldExportPph23DetailToPdf() throws Exception {
            var result = mockMvc.perform(get("/reports/pph23-detail/export/pdf")
                            .param("startDate", "2024-01-01")
                            .param("endDate", "2024-06-30"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "application/pdf"))
                    .andReturn();

            assertThat(result.getResponse().getHeader("Content-Disposition"))
                    .contains("rincian-pph23-");
        }

        @Test
        @DisplayName("Should export PPN crosscheck to PDF")
        void shouldExportPpnCrosscheckToPdf() throws Exception {
            var result = mockMvc.perform(get("/reports/ppn-crosscheck/export/pdf"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "application/pdf"))
                    .andReturn();

            assertThat(result.getResponse().getHeader("Content-Disposition"))
                    .contains("crosscheck-ppn-");
        }

        @Test
        @DisplayName("Should export rekonsiliasi fiskal to PDF")
        void shouldExportRekonsiliasiFiskalToPdf() throws Exception {
            var result = mockMvc.perform(get("/reports/rekonsiliasi-fiskal/export/pdf")
                            .param("year", "2024"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "application/pdf"))
                    .andReturn();

            assertThat(result.getResponse().getHeader("Content-Disposition"))
                    .contains("rekonsiliasi-fiskal-2024.pdf");
        }
    }

    @Nested
    @DisplayName("Export Endpoints - Excel")
    class ExcelExportTests {

        @Test
        @DisplayName("Should export trial balance to Excel")
        void shouldExportTrialBalanceToExcel() throws Exception {
            var result = mockMvc.perform(get("/reports/trial-balance/export/excel")
                            .param("asOfDate", "2024-06-30"))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty();
            assertThat(result.getResponse().getHeader("Content-Disposition"))
                    .contains("neraca-saldo-20240630.xlsx");
        }

        @Test
        @DisplayName("Should export balance sheet to Excel")
        void shouldExportBalanceSheetToExcel() throws Exception {
            var result = mockMvc.perform(get("/reports/balance-sheet/export/excel"))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty();
            assertThat(result.getResponse().getHeader("Content-Disposition"))
                    .contains("laporan-posisi-keuangan-");
        }

        @Test
        @DisplayName("Should export income statement to Excel")
        void shouldExportIncomeStatementToExcel() throws Exception {
            var result = mockMvc.perform(get("/reports/income-statement/export/excel")
                            .param("startDate", "2024-01-01")
                            .param("endDate", "2024-06-30"))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(result.getResponse().getHeader("Content-Disposition"))
                    .contains("laporan-laba-rugi-20240101-20240630.xlsx");
        }

        @Test
        @DisplayName("Should export cash flow to Excel")
        void shouldExportCashFlowToExcel() throws Exception {
            var result = mockMvc.perform(get("/reports/cash-flow/export/excel"))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty();
        }

        @Test
        @DisplayName("Should export depreciation report to Excel")
        void shouldExportDepreciationToExcel() throws Exception {
            var result = mockMvc.perform(get("/reports/depreciation/export/excel")
                            .param("year", "2024"))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(result.getResponse().getHeader("Content-Disposition"))
                    .contains("laporan-penyusutan-2024.xlsx");
        }

        @Test
        @DisplayName("Should export PPN detail to Excel")
        void shouldExportPpnDetailToExcel() throws Exception {
            var result = mockMvc.perform(get("/reports/ppn-detail/export/excel"))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty();
        }

        @Test
        @DisplayName("Should export PPh23 detail to Excel")
        void shouldExportPph23DetailToExcel() throws Exception {
            var result = mockMvc.perform(get("/reports/pph23-detail/export/excel"))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty();
        }

        @Test
        @DisplayName("Should export PPN crosscheck to Excel")
        void shouldExportPpnCrosscheckToExcel() throws Exception {
            var result = mockMvc.perform(get("/reports/ppn-crosscheck/export/excel"))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty();
        }

        @Test
        @DisplayName("Should export rekonsiliasi fiskal to Excel")
        void shouldExportRekonsiliasiFiskalToExcel() throws Exception {
            var result = mockMvc.perform(get("/reports/rekonsiliasi-fiskal/export/excel")
                            .param("year", "2024"))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(result.getResponse().getHeader("Content-Disposition"))
                    .contains("rekonsiliasi-fiskal-2024.xlsx");
        }
    }

    @Nested
    @DisplayName("Tax Report Pages")
    class TaxReportPageTests {

        @Test
        @DisplayName("Should render PPN summary page with default dates")
        void shouldRenderPpnSummaryPageWithDefaultDates() throws Exception {
            mockMvc.perform(get("/reports/ppn-summary"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("reports/ppn-summary"))
                    .andExpect(model().attributeExists("report", "startDate", "endDate"));
        }

        @Test
        @DisplayName("Should render PPN summary page with specific dates")
        void shouldRenderPpnSummaryPageWithSpecificDates() throws Exception {
            mockMvc.perform(get("/reports/ppn-summary")
                            .param("startDate", "2024-01-01")
                            .param("endDate", "2024-06-30"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("reports/ppn-summary"));
        }

        @Test
        @DisplayName("Should render PPh23 withholding page")
        void shouldRenderPph23WithholdingPage() throws Exception {
            mockMvc.perform(get("/reports/pph23-withholding"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("reports/pph23-withholding"))
                    .andExpect(model().attributeExists("report"));
        }

        @Test
        @DisplayName("Should render tax summary page")
        void shouldRenderTaxSummaryPage() throws Exception {
            mockMvc.perform(get("/reports/tax-summary"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("reports/tax-summary"))
                    .andExpect(model().attributeExists("report"));
        }

        @Test
        @DisplayName("Should render PPN detail page")
        void shouldRenderPpnDetailPage() throws Exception {
            mockMvc.perform(get("/reports/ppn-detail"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("reports/ppn-detail"))
                    .andExpect(model().attributeExists("report"));
        }

        @Test
        @DisplayName("Should render PPh23 detail page")
        void shouldRenderPph23DetailPage() throws Exception {
            mockMvc.perform(get("/reports/pph23-detail"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("reports/pph23-detail"))
                    .andExpect(model().attributeExists("report"));
        }

        @Test
        @DisplayName("Should render PPN crosscheck page")
        void shouldRenderPpnCrosscheckPage() throws Exception {
            mockMvc.perform(get("/reports/ppn-crosscheck"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("reports/ppn-crosscheck"))
                    .andExpect(model().attributeExists("report"));
        }

        @Test
        @DisplayName("Should render rekonsiliasi fiskal page with default year")
        void shouldRenderRekonsiliasiFiskalPageWithDefaultYear() throws Exception {
            mockMvc.perform(get("/reports/rekonsiliasi-fiskal"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("reports/rekonsiliasi-fiskal"))
                    .andExpect(model().attributeExists("report", "year", "categories", "directions"));
        }

        @Test
        @DisplayName("Should render rekonsiliasi fiskal page with specific year")
        void shouldRenderRekonsiliasiFiskalPageWithSpecificYear() throws Exception {
            mockMvc.perform(get("/reports/rekonsiliasi-fiskal")
                            .param("year", "2024"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("reports/rekonsiliasi-fiskal"));
        }
    }

    @Nested
    @DisplayName("Print Endpoints")
    class PrintEndpointTests {

        @Test
        @DisplayName("Should render trial balance print view")
        void shouldRenderTrialBalancePrintView() throws Exception {
            mockMvc.perform(get("/reports/trial-balance/print"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("reports/trial-balance-print"))
                    .andExpect(model().attributeExists("report", "asOfDate", "company"));
        }

        @Test
        @DisplayName("Should render balance sheet print view")
        void shouldRenderBalanceSheetPrintView() throws Exception {
            mockMvc.perform(get("/reports/balance-sheet/print")
                            .param("asOfDate", "2024-06-30"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("reports/balance-sheet-print"))
                    .andExpect(model().attributeExists("report", "company"));
        }

        @Test
        @DisplayName("Should render income statement print view")
        void shouldRenderIncomeStatementPrintView() throws Exception {
            mockMvc.perform(get("/reports/income-statement/print"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("reports/income-statement-print"))
                    .andExpect(model().attributeExists("report", "company", "startDate", "endDate"));
        }

        @Test
        @DisplayName("Should render cash flow print view")
        void shouldRenderCashFlowPrintView() throws Exception {
            mockMvc.perform(get("/reports/cash-flow/print"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("reports/cash-flow-print"))
                    .andExpect(model().attributeExists("report", "company"));
        }

        @Test
        @DisplayName("Should render PPN summary print view")
        void shouldRenderPpnSummaryPrintView() throws Exception {
            mockMvc.perform(get("/reports/ppn-summary/print"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("reports/ppn-summary-print"))
                    .andExpect(model().attributeExists("report", "company"));
        }

        @Test
        @DisplayName("Should render PPh23 withholding print view")
        void shouldRenderPph23WithholdingPrintView() throws Exception {
            mockMvc.perform(get("/reports/pph23-withholding/print"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("reports/pph23-withholding-print"))
                    .andExpect(model().attributeExists("report", "company"));
        }

        @Test
        @DisplayName("Should render depreciation print view")
        void shouldRenderDepreciationPrintView() throws Exception {
            mockMvc.perform(get("/reports/depreciation/print")
                            .param("year", "2024"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("reports/depreciation-print"))
                    .andExpect(model().attributeExists("report", "company", "year"));
        }
    }

    @Nested
    @DisplayName("Fiscal Closing")
    class FiscalClosingTests {

        @Test
        @DisplayName("Should render fiscal closing page with default year")
        void shouldRenderFiscalClosingPageWithDefaultYear() throws Exception {
            mockMvc.perform(get("/reports/fiscal-closing"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("reports/fiscal-closing"))
                    .andExpect(model().attributeExists("year", "preview"));
        }

        @Test
        @DisplayName("Should render fiscal closing page with specific year")
        void shouldRenderFiscalClosingPageWithSpecificYear() throws Exception {
            mockMvc.perform(get("/reports/fiscal-closing")
                            .param("year", "2024"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("reports/fiscal-closing"))
                    .andExpect(model().attribute("year", 2024));
        }

        @Test
        @DisplayName("Should get fiscal closing preview via API")
        void shouldGetFiscalClosingPreviewViaApi() throws Exception {
            mockMvc.perform(get("/reports/api/fiscal-closing/preview")
                            .param("year", "2024"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should handle execute fiscal closing error gracefully")
        void shouldHandleExecuteFiscalClosingErrorGracefully() throws Exception {
            // Execute twice to trigger the "already exists" error
            mockMvc.perform(post("/reports/fiscal-closing/2098/execute").with(csrf()));

            // The second attempt should redirect with error message (year 2098 has no data so will succeed vacuously)
            mockMvc.perform(post("/reports/fiscal-closing/2098/execute").with(csrf()))
                    .andExpect(status().is3xxRedirection());
        }

        @Test
        @DisplayName("Should handle reverse fiscal closing error gracefully")
        void shouldHandleReverseFiscalClosingErrorGracefully() throws Exception {
            // Reversing a year without closing entries should redirect with error
            mockMvc.perform(post("/reports/fiscal-closing/1999/reverse").with(csrf())
                            .param("reason", "Test reversal"))
                    .andExpect(status().is3xxRedirection());
        }
    }

    @Nested
    @DisplayName("Report View Pages")
    class ReportViewTests {

        @Test
        @DisplayName("Should render report index page")
        void shouldRenderReportIndexPage() throws Exception {
            mockMvc.perform(get("/reports"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("reports/index"));
        }

        @Test
        @DisplayName("Should render depreciation report page")
        void shouldRenderDepreciationReportPage() throws Exception {
            mockMvc.perform(get("/reports/depreciation")
                            .param("year", "2024"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("reports/depreciation"))
                    .andExpect(model().attributeExists("report", "year"));
        }

        @Test
        @DisplayName("Should render depreciation report page with default year")
        void shouldRenderDepreciationReportPageWithDefaultYear() throws Exception {
            mockMvc.perform(get("/reports/depreciation"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("reports/depreciation"));
        }

        @Test
        @DisplayName("Should render project profitability page without project")
        void shouldRenderProjectProfitabilityPageWithoutProject() throws Exception {
            mockMvc.perform(get("/reports/project-profitability"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("reports/project-profitability"))
                    .andExpect(model().attributeExists("projects"));
        }

        @Test
        @DisplayName("Should render client ranking page")
        void shouldRenderClientRankingPage() throws Exception {
            mockMvc.perform(get("/reports/client-ranking"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("reports/client-ranking"))
                    .andExpect(model().attributeExists("rankings"));
        }
    }

    @Nested
    @DisplayName("Fiscal Adjustment Operations")
    class FiscalAdjustmentTests {

        @Test
        @DisplayName("Should add fiscal adjustment and redirect")
        void shouldAddFiscalAdjustmentAndRedirect() throws Exception {
            mockMvc.perform(post("/reports/rekonsiliasi-fiskal/adjustments").with(csrf())
                            .param("year", "2024")
                            .param("description", "Test adjustment")
                            .param("adjustmentCategory", "PERMANENT")
                            .param("adjustmentDirection", "POSITIVE")
                            .param("amount", "1000000"))
                    .andExpect(status().is3xxRedirection());
        }
    }

    @Nested
    @DisplayName("API Endpoints")
    class ApiEndpointTests {

        @Test
        @DisplayName("Should return depreciation report via API")
        void shouldReturnDepreciationReportViaApi() throws Exception {
            mockMvc.perform(get("/reports/api/depreciation")
                            .param("year", "2024"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should return depreciation report via API with default year")
        void shouldReturnDepreciationReportViaApiWithDefaultYear() throws Exception {
            mockMvc.perform(get("/reports/api/depreciation"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should return PPN summary via API")
        void shouldReturnPpnSummaryViaApi() throws Exception {
            mockMvc.perform(get("/reports/api/ppn-summary")
                            .param("startDate", "2024-01-01")
                            .param("endDate", "2024-06-30"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should return PPh23 withholding via API")
        void shouldReturnPph23WithholdingViaApi() throws Exception {
            mockMvc.perform(get("/reports/api/pph23-withholding")
                            .param("startDate", "2024-01-01")
                            .param("endDate", "2024-06-30"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should return tax summary via API")
        void shouldReturnTaxSummaryViaApi() throws Exception {
            mockMvc.perform(get("/reports/api/tax-summary")
                            .param("startDate", "2024-01-01")
                            .param("endDate", "2024-06-30"))
                    .andExpect(status().isOk());
        }
    }
}
