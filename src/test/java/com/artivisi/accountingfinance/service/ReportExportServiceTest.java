package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.FiscalAdjustment;
import com.artivisi.accountingfinance.entity.InventoryTransactionType;
import com.artivisi.accountingfinance.entity.TaxTransactionDetail;
import com.artivisi.accountingfinance.enums.AccountType;
import com.artivisi.accountingfinance.enums.FiscalAdjustmentCategory;
import com.artivisi.accountingfinance.enums.FiscalAdjustmentDirection;
import com.artivisi.accountingfinance.enums.NormalBalance;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;

/**
 * Integration tests for ReportExportService.
 * Tests PDF and Excel export for financial reports using actual database data.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("ReportExportService - Financial Report Export")
class ReportExportServiceTest {

    @Autowired
    private ReportExportService reportExportService;

    @Autowired
    private ReportService reportService;

    @Autowired
    private DepreciationReportService depreciationReportService;

    @Autowired
    private InventoryReportService inventoryReportService;

    @Autowired
    private TaxReportDetailService taxReportDetailService;

    // ==================== Trial Balance ====================

    @Test
    @DisplayName("Should export trial balance to PDF")
    void shouldExportTrialBalanceToPdf() {
        var report = reportService.generateTrialBalance(LocalDate.now());
        byte[] pdf = reportExportService.exportTrialBalanceToPdf(report);

        assertThat(pdf).isNotNull();
        assertThat(pdf).hasSizeGreaterThan(0);
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("Should export trial balance to Excel")
    void shouldExportTrialBalanceToExcel() throws Exception {
        var report = reportService.generateTrialBalance(LocalDate.now());
        byte[] excel = reportExportService.exportTrialBalanceToExcel(report);

        assertThat(excel).isNotNull();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
        }
    }

    // ==================== Balance Sheet ====================

    @Test
    @DisplayName("Should export balance sheet to PDF")
    void shouldExportBalanceSheetToPdf() {
        var report = reportService.generateBalanceSheet(LocalDate.now());
        byte[] pdf = reportExportService.exportBalanceSheetToPdf(report);

        assertThat(pdf).isNotNull();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("Should export balance sheet to Excel")
    void shouldExportBalanceSheetToExcel() throws Exception {
        var report = reportService.generateBalanceSheet(LocalDate.now());
        byte[] excel = reportExportService.exportBalanceSheetToExcel(report);

        assertThat(excel).isNotNull();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
        }
    }

    // ==================== Income Statement ====================

    @Test
    @DisplayName("Should export income statement to PDF")
    void shouldExportIncomeStatementToPdf() {
        var report = reportService.generateIncomeStatement(
                LocalDate.now().minusMonths(1), LocalDate.now());
        byte[] pdf = reportExportService.exportIncomeStatementToPdf(report);

        assertThat(pdf).isNotNull();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("Should export income statement to Excel")
    void shouldExportIncomeStatementToExcel() throws Exception {
        var report = reportService.generateIncomeStatement(
                LocalDate.now().minusMonths(1), LocalDate.now());
        byte[] excel = reportExportService.exportIncomeStatementToExcel(report);

        assertThat(excel).isNotNull();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
        }
    }

    // ==================== Cash Flow ====================

    @Test
    @DisplayName("Should export cash flow to PDF")
    void shouldExportCashFlowToPdf() {
        var report = reportService.generateCashFlowStatement(
                LocalDate.now().minusMonths(1), LocalDate.now());
        byte[] pdf = reportExportService.exportCashFlowToPdf(report);

        assertThat(pdf).isNotNull();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("Should export cash flow to Excel")
    void shouldExportCashFlowToExcel() throws Exception {
        var report = reportService.generateCashFlowStatement(
                LocalDate.now().minusMonths(1), LocalDate.now());
        byte[] excel = reportExportService.exportCashFlowToExcel(report);

        assertThat(excel).isNotNull();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
        }
    }

    // ==================== Depreciation ====================

    @Test
    @DisplayName("Should export depreciation to PDF")
    void shouldExportDepreciationToPdf() {
        var report = depreciationReportService.generateReport(LocalDate.now().getYear());
        byte[] pdf = reportExportService.exportDepreciationToPdf(report);

        assertThat(pdf).isNotNull();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("Should export depreciation to Excel")
    void shouldExportDepreciationToExcel() throws Exception {
        var report = depreciationReportService.generateReport(LocalDate.now().getYear());
        byte[] excel = reportExportService.exportDepreciationToExcel(report);

        assertThat(excel).isNotNull();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
        }
    }

    // ==================== Stock Balance ====================

    @Test
    @DisplayName("Should export stock balance to PDF")
    void shouldExportStockBalanceToPdf() {
        var report = inventoryReportService.generateStockBalanceReport(null, null);
        byte[] pdf = reportExportService.exportStockBalanceToPdf(report, LocalDate.now());

        assertThat(pdf).isNotNull();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("Should export stock balance to Excel")
    void shouldExportStockBalanceToExcel() throws Exception {
        var report = inventoryReportService.generateStockBalanceReport(null, null);
        byte[] excel = reportExportService.exportStockBalanceToExcel(report, LocalDate.now());

        assertThat(excel).isNotNull();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
        }
    }

    // ==================== Stock Movement ====================

    @Test
    @DisplayName("Should export stock movement to PDF")
    void shouldExportStockMovementToPdf() {
        var report = inventoryReportService.generateStockMovementReport(
                LocalDate.now().minusMonths(1), LocalDate.now(), null, null);
        byte[] pdf = reportExportService.exportStockMovementToPdf(report);

        assertThat(pdf).isNotNull();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("Should export stock movement to Excel")
    void shouldExportStockMovementToExcel() throws Exception {
        var report = inventoryReportService.generateStockMovementReport(
                LocalDate.now().minusMonths(1), LocalDate.now(), null, null);
        byte[] excel = reportExportService.exportStockMovementToExcel(report);

        assertThat(excel).isNotNull();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
        }
    }

    // ==================== Valuation ====================

    @Test
    @DisplayName("Should export valuation to PDF")
    void shouldExportValuationToPdf() {
        var report = inventoryReportService.generateValuationReport(null);
        byte[] pdf = reportExportService.exportValuationToPdf(report, LocalDate.now());

        assertThat(pdf).isNotNull();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("Should export valuation to Excel")
    void shouldExportValuationToExcel() throws Exception {
        var report = inventoryReportService.generateValuationReport(null);
        byte[] excel = reportExportService.exportValuationToExcel(report, LocalDate.now());

        assertThat(excel).isNotNull();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
        }
    }

    // ==================== Product Profitability ====================

    @Test
    @DisplayName("Should export product profitability to PDF")
    void shouldExportProductProfitabilityToPdf() {
        var report = inventoryReportService.generateProfitabilityReport(
                LocalDate.now().minusMonths(1), LocalDate.now(), null, null);
        byte[] pdf = reportExportService.exportProductProfitabilityToPdf(report);

        assertThat(pdf).isNotNull();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("Should export product profitability to Excel")
    void shouldExportProductProfitabilityToExcel() throws Exception {
        var report = inventoryReportService.generateProfitabilityReport(
                LocalDate.now().minusMonths(1), LocalDate.now(), null, null);
        byte[] excel = reportExportService.exportProductProfitabilityToExcel(report);

        assertThat(excel).isNotNull();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
        }
    }

    // ==================== PPN Detail ====================

    @Nested
    @DisplayName("Tax Report Exports")
    class TaxReportExportTests {

        @Test
        @DisplayName("Should export PPN detail to PDF")
        void shouldExportPpnDetailToPdf() {
            var report = taxReportDetailService.generatePPNDetailReport(
                    LocalDate.now().minusMonths(1), LocalDate.now());
            byte[] pdf = reportExportService.exportPpnDetailToPdf(report);

            assertThat(pdf).isNotNull();
            assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        }

        @Test
        @DisplayName("Should export PPN detail to Excel")
        void shouldExportPpnDetailToExcel() throws Exception {
            var report = taxReportDetailService.generatePPNDetailReport(
                    LocalDate.now().minusMonths(1), LocalDate.now());
            byte[] excel = reportExportService.exportPpnDetailToExcel(report);

            assertThat(excel).isNotNull();
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
                assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            }
        }

        @Test
        @DisplayName("Should export PPh 23 detail to PDF")
        void shouldExportPph23DetailToPdf() {
            var report = taxReportDetailService.generatePPh23DetailReport(
                    LocalDate.now().minusMonths(1), LocalDate.now());
            byte[] pdf = reportExportService.exportPph23DetailToPdf(report);

            assertThat(pdf).isNotNull();
            assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        }

        @Test
        @DisplayName("Should export PPh 23 detail to Excel")
        void shouldExportPph23DetailToExcel() throws Exception {
            var report = taxReportDetailService.generatePPh23DetailReport(
                    LocalDate.now().minusMonths(1), LocalDate.now());
            byte[] excel = reportExportService.exportPph23DetailToExcel(report);

            assertThat(excel).isNotNull();
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
                assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            }
        }

        @Test
        @DisplayName("Should export PPN crosscheck to PDF")
        void shouldExportPpnCrosscheckToPdf() {
            var report = taxReportDetailService.generatePPNCrossCheckReport(
                    LocalDate.now().minusMonths(1), LocalDate.now());
            byte[] pdf = reportExportService.exportPpnCrosscheckToPdf(report);

            assertThat(pdf).isNotNull();
            assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        }

        @Test
        @DisplayName("Should export PPN crosscheck to Excel")
        void shouldExportPpnCrosscheckToExcel() throws Exception {
            var report = taxReportDetailService.generatePPNCrossCheckReport(
                    LocalDate.now().minusMonths(1), LocalDate.now());
            byte[] excel = reportExportService.exportPpnCrosscheckToExcel(report);

            assertThat(excel).isNotNull();
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
                assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            }
        }

        @Test
        @DisplayName("Should export rekonsiliasi fiskal to PDF")
        void shouldExportRekonsiliasiFiskalToPdf() {
            var report = taxReportDetailService.generateRekonsiliasiFiskal(LocalDate.now().getYear());
            byte[] pdf = reportExportService.exportRekonsiliasiFiskalToPdf(report);

            assertThat(pdf).isNotNull();
            assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        }

        @Test
        @DisplayName("Should export rekonsiliasi fiskal to Excel")
        void shouldExportRekonsiliasiFiskalToExcel() throws Exception {
            var report = taxReportDetailService.generateRekonsiliasiFiskal(LocalDate.now().getYear());
            byte[] excel = reportExportService.exportRekonsiliasiFiskalToExcel(report);

            assertThat(excel).isNotNull();
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
                assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            }
        }
    }

    // ==================== Tests with populated data ====================

    @Nested
    @DisplayName("Exports with populated report data")
    class PopulatedDataExportTests {

        private ChartOfAccount createAccount(String code, String name, AccountType type, NormalBalance normalBalance) {
            ChartOfAccount account = new ChartOfAccount();
            account.setAccountCode(code);
            account.setAccountName(name);
            account.setAccountType(type);
            account.setNormalBalance(normalBalance);
            return account;
        }

        private TaxTransactionDetail createPpnDetail(String fakturNumber, LocalDate fakturDate,
                                                      String transactionCode, String counterpartyName,
                                                      String counterpartyNpwp, BigDecimal dpp, BigDecimal ppn) {
            TaxTransactionDetail detail = new TaxTransactionDetail();
            detail.setFakturNumber(fakturNumber);
            detail.setFakturDate(fakturDate);
            detail.setTransactionCode(transactionCode);
            detail.setCounterpartyName(counterpartyName);
            detail.setCounterpartyNpwp(counterpartyNpwp);
            detail.setDpp(dpp);
            detail.setPpn(ppn);
            return detail;
        }

        private TaxTransactionDetail createPph23Detail(String bupotNumber, String taxObjectCode,
                                                        String counterpartyName, String counterpartyNpwp,
                                                        BigDecimal grossAmount, BigDecimal taxRate, BigDecimal taxAmount) {
            TaxTransactionDetail detail = new TaxTransactionDetail();
            detail.setBupotNumber(bupotNumber);
            detail.setTaxObjectCode(taxObjectCode);
            detail.setCounterpartyName(counterpartyName);
            detail.setCounterpartyNpwp(counterpartyNpwp);
            detail.setGrossAmount(grossAmount);
            detail.setTaxRate(taxRate);
            detail.setTaxAmount(taxAmount);
            return detail;
        }

        // ==================== Trial Balance with items ====================

        @Test
        @DisplayName("Should export trial balance with items to PDF")
        void shouldExportTrialBalanceWithItemsToPdf() {
            var items = List.of(
                    new ReportService.TrialBalanceItem(
                            createAccount("1101", "Kas", AccountType.ASSET, NormalBalance.DEBIT),
                            new BigDecimal("50000000"), BigDecimal.ZERO),
                    new ReportService.TrialBalanceItem(
                            createAccount("4101", "Pendapatan Jasa", AccountType.REVENUE, NormalBalance.CREDIT),
                            BigDecimal.ZERO, new BigDecimal("50000000"))
            );
            var report = new ReportService.TrialBalanceReport(
                    LocalDate.now(), items, new BigDecimal("50000000"), new BigDecimal("50000000"));

            byte[] pdf = reportExportService.exportTrialBalanceToPdf(report);

            assertThat(pdf).isNotNull().hasSizeGreaterThan(100);
            assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        }

        @Test
        @DisplayName("Should export trial balance with items to Excel")
        void shouldExportTrialBalanceWithItemsToExcel() throws Exception {
            var items = List.of(
                    new ReportService.TrialBalanceItem(
                            createAccount("1101", "Kas", AccountType.ASSET, NormalBalance.DEBIT),
                            new BigDecimal("50000000"), BigDecimal.ZERO),
                    new ReportService.TrialBalanceItem(
                            createAccount("4101", "Pendapatan Jasa", AccountType.REVENUE, NormalBalance.CREDIT),
                            BigDecimal.ZERO, new BigDecimal("50000000"))
            );
            var report = new ReportService.TrialBalanceReport(
                    LocalDate.now(), items, new BigDecimal("50000000"), new BigDecimal("50000000"));

            byte[] excel = reportExportService.exportTrialBalanceToExcel(report);

            assertThat(excel).isNotNull();
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
                Sheet sheet = workbook.getSheetAt(0);
                assertThat(sheet.getSheetName()).isEqualTo("Neraca Saldo");
                // Header rows (4) + 2 data rows + 1 total row
                assertThat(sheet.getLastRowNum()).isGreaterThanOrEqualTo(6);
            }
        }

        // ==================== Balance Sheet with items ====================

        @Test
        @DisplayName("Should export balance sheet with items to PDF")
        void shouldExportBalanceSheetWithItemsToPdf() {
            var assetItems = List.of(
                    new ReportService.BalanceSheetItem(
                            createAccount("1101", "Kas", AccountType.ASSET, NormalBalance.DEBIT),
                            new BigDecimal("30000000")));
            var liabilityItems = List.of(
                    new ReportService.BalanceSheetItem(
                            createAccount("2101", "Hutang Dagang", AccountType.LIABILITY, NormalBalance.CREDIT),
                            new BigDecimal("10000000")));
            var equityItems = List.of(
                    new ReportService.BalanceSheetItem(
                            createAccount("3101", "Modal Pemilik", AccountType.EQUITY, NormalBalance.CREDIT),
                            new BigDecimal("15000000")));
            var report = new ReportService.BalanceSheetReport(
                    LocalDate.now(), assetItems, liabilityItems, equityItems,
                    new BigDecimal("30000000"), new BigDecimal("10000000"),
                    new BigDecimal("20000000"), new BigDecimal("5000000"));

            byte[] pdf = reportExportService.exportBalanceSheetToPdf(report);

            assertThat(pdf).isNotNull().hasSizeGreaterThan(100);
            assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        }

        @Test
        @DisplayName("Should export balance sheet with items to Excel")
        void shouldExportBalanceSheetWithItemsToExcel() throws Exception {
            var assetItems = List.of(
                    new ReportService.BalanceSheetItem(
                            createAccount("1101", "Kas", AccountType.ASSET, NormalBalance.DEBIT),
                            new BigDecimal("30000000")));
            var liabilityItems = List.of(
                    new ReportService.BalanceSheetItem(
                            createAccount("2101", "Hutang Dagang", AccountType.LIABILITY, NormalBalance.CREDIT),
                            new BigDecimal("10000000")));
            var equityItems = List.of(
                    new ReportService.BalanceSheetItem(
                            createAccount("3101", "Modal Pemilik", AccountType.EQUITY, NormalBalance.CREDIT),
                            new BigDecimal("15000000")));
            var report = new ReportService.BalanceSheetReport(
                    LocalDate.now(), assetItems, liabilityItems, equityItems,
                    new BigDecimal("30000000"), new BigDecimal("10000000"),
                    new BigDecimal("20000000"), new BigDecimal("5000000"));

            byte[] excel = reportExportService.exportBalanceSheetToExcel(report);

            assertThat(excel).isNotNull();
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
                Sheet sheet = workbook.getSheetAt(0);
                assertThat(sheet.getSheetName()).isEqualTo("Laporan Posisi Keuangan");
                assertThat(sheet.getLastRowNum()).isGreaterThanOrEqualTo(10);
            }
        }

        // ==================== Income Statement with items ====================

        @Test
        @DisplayName("Should export income statement with revenue and expense items to PDF")
        void shouldExportIncomeStatementWithItemsToPdf() {
            var revenueItems = List.of(
                    new ReportService.IncomeStatementItem(
                            createAccount("4101", "Pendapatan Jasa", AccountType.REVENUE, NormalBalance.CREDIT),
                            new BigDecimal("80000000")));
            var expenseItems = List.of(
                    new ReportService.IncomeStatementItem(
                            createAccount("5101", "Beban Gaji", AccountType.EXPENSE, NormalBalance.DEBIT),
                            new BigDecimal("30000000")));
            var report = new ReportService.IncomeStatementReport(
                    LocalDate.now().minusMonths(1), LocalDate.now(),
                    revenueItems, expenseItems,
                    new BigDecimal("80000000"), new BigDecimal("30000000"), new BigDecimal("50000000"));

            byte[] pdf = reportExportService.exportIncomeStatementToPdf(report);

            assertThat(pdf).isNotNull().hasSizeGreaterThan(100);
            assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        }

        @Test
        @DisplayName("Should export income statement with net loss to PDF")
        void shouldExportIncomeStatementWithNetLossToPdf() {
            var revenueItems = List.of(
                    new ReportService.IncomeStatementItem(
                            createAccount("4101", "Pendapatan Jasa", AccountType.REVENUE, NormalBalance.CREDIT),
                            new BigDecimal("20000000")));
            var expenseItems = List.of(
                    new ReportService.IncomeStatementItem(
                            createAccount("5101", "Beban Gaji", AccountType.EXPENSE, NormalBalance.DEBIT),
                            new BigDecimal("50000000")));
            var report = new ReportService.IncomeStatementReport(
                    LocalDate.now().minusMonths(1), LocalDate.now(),
                    revenueItems, expenseItems,
                    new BigDecimal("20000000"), new BigDecimal("50000000"), new BigDecimal("-30000000"));

            byte[] pdf = reportExportService.exportIncomeStatementToPdf(report);

            assertThat(pdf).isNotNull().hasSizeGreaterThan(100);
            assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        }

        @Test
        @DisplayName("Should export income statement with items to Excel")
        void shouldExportIncomeStatementWithItemsToExcel() throws Exception {
            var revenueItems = List.of(
                    new ReportService.IncomeStatementItem(
                            createAccount("4101", "Pendapatan Jasa", AccountType.REVENUE, NormalBalance.CREDIT),
                            new BigDecimal("80000000")));
            var expenseItems = List.of(
                    new ReportService.IncomeStatementItem(
                            createAccount("5101", "Beban Gaji", AccountType.EXPENSE, NormalBalance.DEBIT),
                            new BigDecimal("30000000")));
            var report = new ReportService.IncomeStatementReport(
                    LocalDate.now().minusMonths(1), LocalDate.now(),
                    revenueItems, expenseItems,
                    new BigDecimal("80000000"), new BigDecimal("30000000"), new BigDecimal("50000000"));

            byte[] excel = reportExportService.exportIncomeStatementToExcel(report);

            assertThat(excel).isNotNull();
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
                Sheet sheet = workbook.getSheetAt(0);
                assertThat(sheet.getSheetName()).isEqualTo("Laporan Laba Rugi");
                assertThat(sheet.getLastRowNum()).isGreaterThanOrEqualTo(8);
            }
        }

        @Test
        @DisplayName("Should export income statement with net loss to Excel")
        void shouldExportIncomeStatementWithNetLossToExcel() throws Exception {
            var revenueItems = List.of(
                    new ReportService.IncomeStatementItem(
                            createAccount("4101", "Pendapatan Jasa", AccountType.REVENUE, NormalBalance.CREDIT),
                            new BigDecimal("20000000")));
            var expenseItems = List.of(
                    new ReportService.IncomeStatementItem(
                            createAccount("5101", "Beban Gaji", AccountType.EXPENSE, NormalBalance.DEBIT),
                            new BigDecimal("50000000")));
            var report = new ReportService.IncomeStatementReport(
                    LocalDate.now().minusMonths(1), LocalDate.now(),
                    revenueItems, expenseItems,
                    new BigDecimal("20000000"), new BigDecimal("50000000"), new BigDecimal("-30000000"));

            byte[] excel = reportExportService.exportIncomeStatementToExcel(report);

            assertThat(excel).isNotNull();
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
                assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            }
        }

        // ==================== Cash Flow with items ====================

        @Test
        @DisplayName("Should export cash flow with items to PDF")
        void shouldExportCashFlowWithItemsToPdf() {
            var operatingItems = List.of(
                    new ReportService.CashFlowItem("Pendapatan Jasa", new BigDecimal("80000000")),
                    new ReportService.CashFlowItem("Pembayaran Gaji", new BigDecimal("-30000000")));
            var investingItems = List.of(
                    new ReportService.CashFlowItem("Pembelian Peralatan", new BigDecimal("-15000000")));
            var financingItems = List.of(
                    new ReportService.CashFlowItem("Setoran Modal", new BigDecimal("20000000")));
            var cashBalances = List.of(
                    new ReportService.CashAccountBalance("Kas", new BigDecimal("55000000")));
            var report = new ReportService.CashFlowReport(
                    LocalDate.now().minusMonths(1), LocalDate.now(),
                    operatingItems, investingItems, financingItems,
                    new BigDecimal("50000000"), new BigDecimal("-15000000"), new BigDecimal("20000000"),
                    new BigDecimal("55000000"), BigDecimal.ZERO, new BigDecimal("55000000"),
                    cashBalances);

            byte[] pdf = reportExportService.exportCashFlowToPdf(report);

            assertThat(pdf).isNotNull().hasSizeGreaterThan(100);
            assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        }

        @Test
        @DisplayName("Should export cash flow with items to Excel")
        void shouldExportCashFlowWithItemsToExcel() throws Exception {
            var operatingItems = List.of(
                    new ReportService.CashFlowItem("Pendapatan Jasa", new BigDecimal("80000000")),
                    new ReportService.CashFlowItem("Pembayaran Gaji", new BigDecimal("-30000000")));
            var investingItems = List.of(
                    new ReportService.CashFlowItem("Pembelian Peralatan", new BigDecimal("-15000000")));
            var financingItems = List.of(
                    new ReportService.CashFlowItem("Setoran Modal", new BigDecimal("20000000")));
            var cashBalances = List.of(
                    new ReportService.CashAccountBalance("Kas", new BigDecimal("55000000")));
            var report = new ReportService.CashFlowReport(
                    LocalDate.now().minusMonths(1), LocalDate.now(),
                    operatingItems, investingItems, financingItems,
                    new BigDecimal("50000000"), new BigDecimal("-15000000"), new BigDecimal("20000000"),
                    new BigDecimal("55000000"), BigDecimal.ZERO, new BigDecimal("55000000"),
                    cashBalances);

            byte[] excel = reportExportService.exportCashFlowToExcel(report);

            assertThat(excel).isNotNull();
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
                Sheet sheet = workbook.getSheetAt(0);
                assertThat(sheet.getSheetName()).isEqualTo("Laporan Arus Kas");
                assertThat(sheet.getLastRowNum()).isGreaterThanOrEqualTo(12);
            }
        }

        // ==================== Depreciation with items ====================

        @Test
        @DisplayName("Should export depreciation report with items to PDF")
        void shouldExportDepreciationWithItemsToPdf() {
            var items = List.of(
                    new DepreciationReportService.DepreciationReportItem(
                            "FA-001", "Laptop Dell", "Peralatan Kantor",
                            LocalDate.of(2024, 3, 15), new BigDecimal("15000000"),
                            4, "Garis Lurus",
                            new BigDecimal("3750000"), new BigDecimal("7500000"),
                            new BigDecimal("7500000"), "ACTIVE"));
            var report = new DepreciationReportService.DepreciationReport(
                    2025, items,
                    new BigDecimal("15000000"), new BigDecimal("3750000"),
                    new BigDecimal("7500000"), new BigDecimal("7500000"));

            byte[] pdf = reportExportService.exportDepreciationToPdf(report);

            assertThat(pdf).isNotNull().hasSizeGreaterThan(100);
            assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        }

        @Test
        @DisplayName("Should export depreciation report with items to Excel")
        void shouldExportDepreciationWithItemsToExcel() throws Exception {
            var items = List.of(
                    new DepreciationReportService.DepreciationReportItem(
                            "FA-001", "Laptop Dell", "Peralatan Kantor",
                            LocalDate.of(2024, 3, 15), new BigDecimal("15000000"),
                            4, "Garis Lurus",
                            new BigDecimal("3750000"), new BigDecimal("7500000"),
                            new BigDecimal("7500000"), "ACTIVE"));
            var report = new DepreciationReportService.DepreciationReport(
                    2025, items,
                    new BigDecimal("15000000"), new BigDecimal("3750000"),
                    new BigDecimal("7500000"), new BigDecimal("7500000"));

            byte[] excel = reportExportService.exportDepreciationToExcel(report);

            assertThat(excel).isNotNull();
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
                Sheet sheet = workbook.getSheetAt(0);
                assertThat(sheet.getSheetName()).isEqualTo("Laporan Penyusutan");
                assertThat(sheet.getLastRowNum()).isGreaterThanOrEqualTo(5);
            }
        }

        // ==================== Stock Balance with items ====================

        @Test
        @DisplayName("Should export stock balance with items to PDF")
        void shouldExportStockBalanceWithItemsToPdf() {
            var items = List.of(
                    new InventoryReportService.StockBalanceItem(
                            "PRD-001", "Kopi Arabika", "Bahan Baku", "kg",
                            new BigDecimal("100"), new BigDecimal("150000"),
                            new BigDecimal("15000000"), new BigDecimal("50"), false),
                    new InventoryReportService.StockBalanceItem(
                            "PRD-002", "Gula Pasir", "Bahan Baku", "kg",
                            new BigDecimal("30"), new BigDecimal("20000"),
                            new BigDecimal("600000"), new BigDecimal("50"), true));
            var report = new InventoryReportService.StockBalanceReport(
                    items, new BigDecimal("130"), new BigDecimal("15600000"));

            byte[] pdf = reportExportService.exportStockBalanceToPdf(report, LocalDate.now());

            assertThat(pdf).isNotNull().hasSizeGreaterThan(100);
            assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        }

        @Test
        @DisplayName("Should export stock balance with items to Excel")
        void shouldExportStockBalanceWithItemsToExcel() throws Exception {
            var items = List.of(
                    new InventoryReportService.StockBalanceItem(
                            "PRD-001", "Kopi Arabika", "Bahan Baku", "kg",
                            new BigDecimal("100"), new BigDecimal("150000"),
                            new BigDecimal("15000000"), new BigDecimal("50"), false));
            var report = new InventoryReportService.StockBalanceReport(
                    items, new BigDecimal("100"), new BigDecimal("15000000"));

            byte[] excel = reportExportService.exportStockBalanceToExcel(report, LocalDate.now());

            assertThat(excel).isNotNull();
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
                Sheet sheet = workbook.getSheetAt(0);
                assertThat(sheet.getSheetName()).isEqualTo("Saldo Stok");
                assertThat(sheet.getLastRowNum()).isGreaterThanOrEqualTo(5);
            }
        }

        // ==================== Stock Movement with items ====================

        @Test
        @DisplayName("Should export stock movement with items to PDF")
        void shouldExportStockMovementWithItemsToPdf() {
            var items = List.of(
                    new InventoryReportService.StockMovementItem(
                            LocalDate.now().minusDays(5), "PRD-001", "Kopi Arabika", "Bahan Baku",
                            InventoryTransactionType.PURCHASE, "Pembelian",
                            new BigDecimal("50"), new BigDecimal("150000"),
                            new BigDecimal("7500000"), "PO-001", new BigDecimal("50")),
                    new InventoryReportService.StockMovementItem(
                            LocalDate.now().minusDays(2), "PRD-001", "Kopi Arabika", "Bahan Baku",
                            InventoryTransactionType.SALE, "Penjualan",
                            new BigDecimal("-10"), new BigDecimal("150000"),
                            new BigDecimal("1500000"), "SO-001", new BigDecimal("40")));
            var report = new InventoryReportService.StockMovementReport(
                    LocalDate.now().minusMonths(1), LocalDate.now(), items,
                    new BigDecimal("50"), new BigDecimal("10"),
                    new BigDecimal("7500000"), new BigDecimal("1500000"));

            byte[] pdf = reportExportService.exportStockMovementToPdf(report);

            assertThat(pdf).isNotNull().hasSizeGreaterThan(100);
            assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        }

        @Test
        @DisplayName("Should export stock movement with items to Excel")
        void shouldExportStockMovementWithItemsToExcel() throws Exception {
            var items = List.of(
                    new InventoryReportService.StockMovementItem(
                            LocalDate.now().minusDays(5), "PRD-001", "Kopi Arabika", "Bahan Baku",
                            InventoryTransactionType.PURCHASE, "Pembelian",
                            new BigDecimal("50"), new BigDecimal("150000"),
                            new BigDecimal("7500000"), "PO-001", new BigDecimal("50")));
            var report = new InventoryReportService.StockMovementReport(
                    LocalDate.now().minusMonths(1), LocalDate.now(), items,
                    new BigDecimal("50"), BigDecimal.ZERO,
                    new BigDecimal("7500000"), BigDecimal.ZERO);

            byte[] excel = reportExportService.exportStockMovementToExcel(report);

            assertThat(excel).isNotNull();
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
                Sheet sheet = workbook.getSheetAt(0);
                assertThat(sheet.getSheetName()).isEqualTo("Mutasi Stok");
                assertThat(sheet.getLastRowNum()).isGreaterThanOrEqualTo(7);
            }
        }

        // ==================== Valuation with items ====================

        @Test
        @DisplayName("Should export valuation report with items to PDF")
        void shouldExportValuationWithItemsToPdf() {
            var items = List.of(
                    new InventoryReportService.ValuationItem(
                            "PRD-001", "Kopi Arabika", "Bahan Baku", "kg",
                            new BigDecimal("100"), new BigDecimal("150000"),
                            new BigDecimal("15000000"), "Average Cost"));
            var report = new InventoryReportService.ValuationReport(
                    items, new BigDecimal("15000000"));

            byte[] pdf = reportExportService.exportValuationToPdf(report, LocalDate.now());

            assertThat(pdf).isNotNull().hasSizeGreaterThan(100);
            assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        }

        @Test
        @DisplayName("Should export valuation report with items to Excel")
        void shouldExportValuationWithItemsToExcel() throws Exception {
            var items = List.of(
                    new InventoryReportService.ValuationItem(
                            "PRD-001", "Kopi Arabika", "Bahan Baku", "kg",
                            new BigDecimal("100"), new BigDecimal("150000"),
                            new BigDecimal("15000000"), "Average Cost"));
            var report = new InventoryReportService.ValuationReport(
                    items, new BigDecimal("15000000"));

            byte[] excel = reportExportService.exportValuationToExcel(report, LocalDate.now());

            assertThat(excel).isNotNull();
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
                Sheet sheet = workbook.getSheetAt(0);
                assertThat(sheet.getSheetName()).isEqualTo("Penilaian Persediaan");
                assertThat(sheet.getLastRowNum()).isGreaterThanOrEqualTo(5);
            }
        }

        // ==================== Product Profitability with items ====================

        @Test
        @DisplayName("Should export product profitability with items to PDF")
        void shouldExportProductProfitabilityWithItemsToPdf() {
            var items = List.of(
                    new InventoryReportService.ProfitabilityItem(
                            "PRD-001", "Kopi Arabika", "Bahan Baku", "kg",
                            new BigDecimal("200"), new BigDecimal("40000000"),
                            new BigDecimal("30000000"), new BigDecimal("10000000"),
                            new BigDecimal("25.00"), 15));
            var report = new InventoryReportService.ProfitabilityReport(
                    LocalDate.now().minusMonths(1), LocalDate.now(), items,
                    new BigDecimal("40000000"), new BigDecimal("30000000"),
                    new BigDecimal("10000000"), new BigDecimal("200"));

            byte[] pdf = reportExportService.exportProductProfitabilityToPdf(report);

            assertThat(pdf).isNotNull().hasSizeGreaterThan(100);
            assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        }

        @Test
        @DisplayName("Should export product profitability with items to Excel")
        void shouldExportProductProfitabilityWithItemsToExcel() throws Exception {
            var items = List.of(
                    new InventoryReportService.ProfitabilityItem(
                            "PRD-001", "Kopi Arabika", "Bahan Baku", "kg",
                            new BigDecimal("200"), new BigDecimal("40000000"),
                            new BigDecimal("30000000"), new BigDecimal("10000000"),
                            new BigDecimal("25.00"), 15));
            var report = new InventoryReportService.ProfitabilityReport(
                    LocalDate.now().minusMonths(1), LocalDate.now(), items,
                    new BigDecimal("40000000"), new BigDecimal("30000000"),
                    new BigDecimal("10000000"), new BigDecimal("200"));

            byte[] excel = reportExportService.exportProductProfitabilityToExcel(report);

            assertThat(excel).isNotNull();
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
                Sheet sheet = workbook.getSheetAt(0);
                assertThat(sheet.getSheetName()).isEqualTo("Profitabilitas Produk");
                assertThat(sheet.getLastRowNum()).isGreaterThanOrEqualTo(5);
            }
        }

        // ==================== PPN Detail with items ====================

        @Test
        @DisplayName("Should export PPN detail with items to PDF")
        void shouldExportPpnDetailWithItemsToPdf() {
            var keluaranItems = List.of(
                    createPpnDetail("010.000-24.00000001", LocalDate.now().minusDays(10),
                            "01", "PT Pelanggan A", "01.234.567.8-901.000",
                            new BigDecimal("10000000"), new BigDecimal("1100000")));
            var masukanItems = List.of(
                    createPpnDetail("020.000-24.00000001", LocalDate.now().minusDays(5),
                            "01", "PT Supplier B", "02.345.678.9-012.000",
                            new BigDecimal("5000000"), new BigDecimal("550000")));
            var report = new TaxReportDetailService.PPNDetailReport(
                    LocalDate.now().minusMonths(1), LocalDate.now(),
                    keluaranItems, masukanItems,
                    new BigDecimal("10000000"), new BigDecimal("1100000"),
                    new BigDecimal("5000000"), new BigDecimal("550000"));

            byte[] pdf = reportExportService.exportPpnDetailToPdf(report);

            assertThat(pdf).isNotNull().hasSizeGreaterThan(100);
            assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        }

        @Test
        @DisplayName("Should export PPN detail with null fields to PDF")
        void shouldExportPpnDetailWithNullFieldsToPdf() {
            var keluaranItems = List.of(
                    createPpnDetail(null, null, null, null, null,
                            new BigDecimal("10000000"), new BigDecimal("1100000")));
            var report = new TaxReportDetailService.PPNDetailReport(
                    LocalDate.now().minusMonths(1), LocalDate.now(),
                    keluaranItems, List.of(),
                    new BigDecimal("10000000"), new BigDecimal("1100000"),
                    BigDecimal.ZERO, BigDecimal.ZERO);

            byte[] pdf = reportExportService.exportPpnDetailToPdf(report);

            assertThat(pdf).isNotNull().hasSizeGreaterThan(100);
            assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        }

        @Test
        @DisplayName("Should export PPN detail with items to Excel")
        void shouldExportPpnDetailWithItemsToExcel() throws Exception {
            var keluaranItems = List.of(
                    createPpnDetail("010.000-24.00000001", LocalDate.now().minusDays(10),
                            "01", "PT Pelanggan A", "01.234.567.8-901.000",
                            new BigDecimal("10000000"), new BigDecimal("1100000")));
            var masukanItems = List.of(
                    createPpnDetail("020.000-24.00000001", LocalDate.now().minusDays(5),
                            "01", "PT Supplier B", "02.345.678.9-012.000",
                            new BigDecimal("5000000"), new BigDecimal("550000")));
            var report = new TaxReportDetailService.PPNDetailReport(
                    LocalDate.now().minusMonths(1), LocalDate.now(),
                    keluaranItems, masukanItems,
                    new BigDecimal("10000000"), new BigDecimal("1100000"),
                    new BigDecimal("5000000"), new BigDecimal("550000"));

            byte[] excel = reportExportService.exportPpnDetailToExcel(report);

            assertThat(excel).isNotNull();
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
                Sheet sheet = workbook.getSheetAt(0);
                assertThat(sheet.getSheetName()).isEqualTo("Rincian PPN");
                assertThat(sheet.getLastRowNum()).isGreaterThanOrEqualTo(8);
            }
        }

        @Test
        @DisplayName("Should export PPN detail with null fields to Excel")
        void shouldExportPpnDetailWithNullFieldsToExcel() throws Exception {
            var keluaranItems = List.of(
                    createPpnDetail(null, null, null, null, null,
                            new BigDecimal("10000000"), new BigDecimal("1100000")));
            var report = new TaxReportDetailService.PPNDetailReport(
                    LocalDate.now().minusMonths(1), LocalDate.now(),
                    keluaranItems, List.of(),
                    new BigDecimal("10000000"), new BigDecimal("1100000"),
                    BigDecimal.ZERO, BigDecimal.ZERO);

            byte[] excel = reportExportService.exportPpnDetailToExcel(report);

            assertThat(excel).isNotNull();
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
                assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            }
        }

        // ==================== PPh 23 Detail with items ====================

        @Test
        @DisplayName("Should export PPh 23 detail with items to PDF")
        void shouldExportPph23DetailWithItemsToPdf() {
            var items = List.of(
                    createPph23Detail("BP-001/2025", "24-104-01",
                            "PT Vendor C", "03.456.789.0-123.000",
                            new BigDecimal("20000000"), new BigDecimal("2"),
                            new BigDecimal("400000")));
            var report = new TaxReportDetailService.PPh23DetailReport(
                    LocalDate.now().minusMonths(1), LocalDate.now(),
                    items, new BigDecimal("20000000"), new BigDecimal("400000"));

            byte[] pdf = reportExportService.exportPph23DetailToPdf(report);

            assertThat(pdf).isNotNull().hasSizeGreaterThan(100);
            assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        }

        @Test
        @DisplayName("Should export PPh 23 detail with null fields to PDF")
        void shouldExportPph23DetailWithNullFieldsToPdf() {
            var items = List.of(
                    createPph23Detail(null, null, null, null,
                            new BigDecimal("20000000"), null,
                            new BigDecimal("400000")));
            var report = new TaxReportDetailService.PPh23DetailReport(
                    LocalDate.now().minusMonths(1), LocalDate.now(),
                    items, new BigDecimal("20000000"), new BigDecimal("400000"));

            byte[] pdf = reportExportService.exportPph23DetailToPdf(report);

            assertThat(pdf).isNotNull().hasSizeGreaterThan(100);
            assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        }

        @Test
        @DisplayName("Should export PPh 23 detail with items to Excel")
        void shouldExportPph23DetailWithItemsToExcel() throws Exception {
            var items = List.of(
                    createPph23Detail("BP-001/2025", "24-104-01",
                            "PT Vendor C", "03.456.789.0-123.000",
                            new BigDecimal("20000000"), new BigDecimal("2"),
                            new BigDecimal("400000")));
            var report = new TaxReportDetailService.PPh23DetailReport(
                    LocalDate.now().minusMonths(1), LocalDate.now(),
                    items, new BigDecimal("20000000"), new BigDecimal("400000"));

            byte[] excel = reportExportService.exportPph23DetailToExcel(report);

            assertThat(excel).isNotNull();
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
                Sheet sheet = workbook.getSheetAt(0);
                assertThat(sheet.getSheetName()).isEqualTo("Rincian PPh 23");
                assertThat(sheet.getLastRowNum()).isGreaterThanOrEqualTo(5);
            }
        }

        @Test
        @DisplayName("Should export PPh 23 detail with null fields to Excel")
        void shouldExportPph23DetailWithNullFieldsToExcel() throws Exception {
            var items = List.of(
                    createPph23Detail(null, null, null, null,
                            new BigDecimal("20000000"), null,
                            new BigDecimal("400000")));
            var report = new TaxReportDetailService.PPh23DetailReport(
                    LocalDate.now().minusMonths(1), LocalDate.now(),
                    items, new BigDecimal("20000000"), new BigDecimal("400000"));

            byte[] excel = reportExportService.exportPph23DetailToExcel(report);

            assertThat(excel).isNotNull();
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
                assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            }
        }

        // ==================== PPN Crosscheck with non-zero values ====================

        @Test
        @DisplayName("Should export PPN crosscheck with non-zero differences to PDF")
        void shouldExportPpnCrosscheckWithNonZeroDifferencesToPdf() {
            var report = new TaxReportDetailService.PPNCrossCheckReport(
                    LocalDate.now().minusMonths(1), LocalDate.now(),
                    new BigDecimal("1100000"), new BigDecimal("1050000"), new BigDecimal("50000"),
                    new BigDecimal("550000"), new BigDecimal("550000"), BigDecimal.ZERO);

            byte[] pdf = reportExportService.exportPpnCrosscheckToPdf(report);

            assertThat(pdf).isNotNull().hasSizeGreaterThan(100);
            assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        }

        @Test
        @DisplayName("Should export PPN crosscheck with non-zero differences to Excel")
        void shouldExportPpnCrosscheckWithNonZeroDifferencesToExcel() throws Exception {
            var report = new TaxReportDetailService.PPNCrossCheckReport(
                    LocalDate.now().minusMonths(1), LocalDate.now(),
                    new BigDecimal("1100000"), new BigDecimal("1050000"), new BigDecimal("50000"),
                    new BigDecimal("550000"), new BigDecimal("550000"), BigDecimal.ZERO);

            byte[] excel = reportExportService.exportPpnCrosscheckToExcel(report);

            assertThat(excel).isNotNull();
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
                Sheet sheet = workbook.getSheetAt(0);
                assertThat(sheet.getSheetName()).isEqualTo("Cross-check PPN");
                assertThat(sheet.getLastRowNum()).isGreaterThanOrEqualTo(5);
            }
        }

        // ==================== Rekonsiliasi Fiskal with adjustments ====================

        @Test
        @DisplayName("Should export rekonsiliasi fiskal with adjustments to PDF")
        void shouldExportRekonsiliasiFiskalWithAdjustmentsToPdf() {
            var revenueItems = List.of(
                    new ReportService.IncomeStatementItem(
                            createAccount("4101", "Pendapatan Jasa", AccountType.REVENUE, NormalBalance.CREDIT),
                            new BigDecimal("100000000")));
            var expenseItems = List.of(
                    new ReportService.IncomeStatementItem(
                            createAccount("5101", "Beban Gaji", AccountType.EXPENSE, NormalBalance.DEBIT),
                            new BigDecimal("60000000")));
            var incomeStatement = new ReportService.IncomeStatementReport(
                    LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31),
                    revenueItems, expenseItems,
                    new BigDecimal("100000000"), new BigDecimal("60000000"), new BigDecimal("40000000"));

            FiscalAdjustment positiveAdj = new FiscalAdjustment();
            positiveAdj.setDescription("Beban Entertain Tanpa Daftar Nominatif");
            positiveAdj.setAdjustmentCategory(FiscalAdjustmentCategory.PERMANENT);
            positiveAdj.setAdjustmentDirection(FiscalAdjustmentDirection.POSITIVE);
            positiveAdj.setAmount(new BigDecimal("5000000"));

            FiscalAdjustment negativeAdj = new FiscalAdjustment();
            negativeAdj.setDescription("Pendapatan Dividen dari PT Anak");
            negativeAdj.setAdjustmentCategory(FiscalAdjustmentCategory.TEMPORARY);
            negativeAdj.setAdjustmentDirection(FiscalAdjustmentDirection.NEGATIVE);
            negativeAdj.setAmount(new BigDecimal("2000000"));

            var pphBadan = new TaxReportDetailService.PPhBadanCalculation(
                    new BigDecimal("43000000"), new BigDecimal("43000000"),
                    new BigDecimal("100000000"),
                    new BigDecimal("9460000"), "Tarif Normal 22%",
                    new BigDecimal("400000"), new BigDecimal("3000000"),
                    new BigDecimal("3400000"), new BigDecimal("6060000"));

            var report = new TaxReportDetailService.RekonsiliasiFiskalReport(
                    2025, incomeStatement, List.of(positiveAdj, negativeAdj),
                    new BigDecimal("5000000"), new BigDecimal("2000000"),
                    new BigDecimal("3000000"), new BigDecimal("40000000"),
                    new BigDecimal("43000000"), List.of(), BigDecimal.ZERO,
                    new BigDecimal("43000000"), pphBadan);

            byte[] pdf = reportExportService.exportRekonsiliasiFiskalToPdf(report);

            assertThat(pdf).isNotNull().hasSizeGreaterThan(100);
            assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        }

        @Test
        @DisplayName("Should export rekonsiliasi fiskal with adjustments to Excel")
        void shouldExportRekonsiliasiFiskalWithAdjustmentsToExcel() throws Exception {
            var revenueItems = List.of(
                    new ReportService.IncomeStatementItem(
                            createAccount("4101", "Pendapatan Jasa", AccountType.REVENUE, NormalBalance.CREDIT),
                            new BigDecimal("100000000")));
            var expenseItems = List.of(
                    new ReportService.IncomeStatementItem(
                            createAccount("5101", "Beban Gaji", AccountType.EXPENSE, NormalBalance.DEBIT),
                            new BigDecimal("60000000")));
            var incomeStatement = new ReportService.IncomeStatementReport(
                    LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31),
                    revenueItems, expenseItems,
                    new BigDecimal("100000000"), new BigDecimal("60000000"), new BigDecimal("40000000"));

            FiscalAdjustment positiveAdj = new FiscalAdjustment();
            positiveAdj.setDescription("Beban Entertain Tanpa Daftar Nominatif");
            positiveAdj.setAdjustmentCategory(FiscalAdjustmentCategory.PERMANENT);
            positiveAdj.setAdjustmentDirection(FiscalAdjustmentDirection.POSITIVE);
            positiveAdj.setAmount(new BigDecimal("5000000"));

            FiscalAdjustment negativeAdj = new FiscalAdjustment();
            negativeAdj.setDescription("Pendapatan Dividen dari PT Anak");
            negativeAdj.setAdjustmentCategory(FiscalAdjustmentCategory.TEMPORARY);
            negativeAdj.setAdjustmentDirection(FiscalAdjustmentDirection.NEGATIVE);
            negativeAdj.setAmount(new BigDecimal("2000000"));

            var pphBadan = new TaxReportDetailService.PPhBadanCalculation(
                    new BigDecimal("43000000"), new BigDecimal("43000000"),
                    new BigDecimal("100000000"),
                    new BigDecimal("9460000"), "Tarif Normal 22%",
                    new BigDecimal("400000"), new BigDecimal("3000000"),
                    new BigDecimal("3400000"), new BigDecimal("6060000"));

            var report = new TaxReportDetailService.RekonsiliasiFiskalReport(
                    2025, incomeStatement, List.of(positiveAdj, negativeAdj),
                    new BigDecimal("5000000"), new BigDecimal("2000000"),
                    new BigDecimal("3000000"), new BigDecimal("40000000"),
                    new BigDecimal("43000000"), List.of(), BigDecimal.ZERO,
                    new BigDecimal("43000000"), pphBadan);

            byte[] excel = reportExportService.exportRekonsiliasiFiskalToExcel(report);

            assertThat(excel).isNotNull();
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
                Sheet sheet = workbook.getSheetAt(0);
                assertThat(sheet.getSheetName()).isEqualTo("Rekonsiliasi Fiskal");
                assertThat(sheet.getLastRowNum()).isGreaterThanOrEqualTo(12);
            }
        }
    }
}
