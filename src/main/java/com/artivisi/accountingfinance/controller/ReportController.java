package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.CompanyConfig;
import com.artivisi.accountingfinance.service.ClientService;
import com.artivisi.accountingfinance.service.CompanyConfigService;
import com.artivisi.accountingfinance.service.ProjectProfitabilityService;
import com.artivisi.accountingfinance.service.ProjectService;
import com.artivisi.accountingfinance.service.ReportExportService;
import com.artivisi.accountingfinance.service.DepreciationReportService;
import com.artivisi.accountingfinance.service.FiscalYearClosingService;
import com.artivisi.accountingfinance.service.ReportService;
import com.artivisi.accountingfinance.service.TaxReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static com.artivisi.accountingfinance.controller.ViewConstants.*;

@Controller
@RequestMapping("/reports")
@RequiredArgsConstructor
@org.springframework.security.access.prepost.PreAuthorize("hasAuthority('" + com.artivisi.accountingfinance.security.Permission.REPORT_VIEW + "')")
public class ReportController {

    private static final String ATTR_REPORT_TYPE = "reportType";
    private static final String ATTR_START_DATE = "startDate";
    private static final String ATTR_END_DATE = "endDate";
    private static final String ATTR_REPORT = "report";
    private static final String ATTACHMENT_FILENAME_PREFIX = "attachment; filename=\"";
    private static final String CONTENT_TYPE_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ReportService reportService;
    private final ReportExportService reportExportService;
    private final ProjectProfitabilityService profitabilityService;
    private final ProjectService projectService;
    private final ClientService clientService;
    private final CompanyConfigService companyConfigService;
    private final TaxReportService taxReportService;
    private final DepreciationReportService depreciationReportService;
    private final FiscalYearClosingService fiscalYearClosingService;

    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @GetMapping
    public String index(Model model) {
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_REPORTS);
        return "reports/index";
    }

    @GetMapping("/trial-balance")
    public String trialBalance(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) LocalDate asOfDate,
            Model model) {
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_REPORTS);
        model.addAttribute(ATTR_REPORT_TYPE, "trial-balance");
        model.addAttribute("period", period);

        LocalDate reportDate = asOfDate != null ? asOfDate : LocalDate.now();
        model.addAttribute("asOfDate", reportDate);
        model.addAttribute(ATTR_REPORT, reportService.generateTrialBalance(reportDate));

        return "reports/trial-balance";
    }

    @GetMapping("/income-statement")
    public String incomeStatement(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) String compareWith,
            Model model) {
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_REPORTS);
        model.addAttribute(ATTR_REPORT_TYPE, "income-statement");
        model.addAttribute("compareWith", compareWith);

        LocalDate start = startDate != null ? startDate : LocalDate.now().withDayOfMonth(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now();

        model.addAttribute(ATTR_START_DATE, start);
        model.addAttribute(ATTR_END_DATE, end);
        model.addAttribute(ATTR_REPORT, reportService.generateIncomeStatement(start, end));

        return "reports/income-statement";
    }

    @GetMapping("/balance-sheet")
    public String balanceSheet(
            @RequestParam(required = false) LocalDate asOfDate,
            @RequestParam(required = false) String compareWith,
            Model model) {
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_REPORTS);
        model.addAttribute(ATTR_REPORT_TYPE, "balance-sheet");
        model.addAttribute("compareWith", compareWith);

        LocalDate reportDate = asOfDate != null ? asOfDate : LocalDate.now();
        model.addAttribute("asOfDate", reportDate);
        model.addAttribute(ATTR_REPORT, reportService.generateBalanceSheet(reportDate));

        return "reports/balance-sheet";
    }

    @GetMapping("/cash-flow")
    public String cashFlow(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            Model model) {
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_REPORTS);
        model.addAttribute(ATTR_REPORT_TYPE, "cash-flow");

        LocalDate start = startDate != null ? startDate : LocalDate.now().withDayOfMonth(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now();

        model.addAttribute(ATTR_START_DATE, start);
        model.addAttribute(ATTR_END_DATE, end);
        model.addAttribute(ATTR_REPORT, reportService.generateCashFlowStatement(start, end));

        return "reports/cash-flow";
    }

    // REST API Endpoints

    @GetMapping("/api/trial-balance")
    @ResponseBody
    public ResponseEntity<ReportService.TrialBalanceReport> apiTrialBalance(
            @RequestParam(required = false) LocalDate asOfDate) {
        LocalDate reportDate = asOfDate != null ? asOfDate : LocalDate.now();
        return ResponseEntity.ok(reportService.generateTrialBalance(reportDate));
    }

    @GetMapping("/api/income-statement")
    @ResponseBody
    public ResponseEntity<ReportService.IncomeStatementReport> apiIncomeStatement(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        return ResponseEntity.ok(reportService.generateIncomeStatement(startDate, endDate));
    }

    @GetMapping("/api/balance-sheet")
    @ResponseBody
    public ResponseEntity<ReportService.BalanceSheetReport> apiBalanceSheet(
            @RequestParam(required = false) LocalDate asOfDate) {
        LocalDate reportDate = asOfDate != null ? asOfDate : LocalDate.now();
        return ResponseEntity.ok(reportService.generateBalanceSheet(reportDate));
    }

    // ==================== EXPORT ENDPOINTS ====================

    // Trial Balance Exports
    @GetMapping("/trial-balance/export/pdf")
    public ResponseEntity<byte[]> exportTrialBalanceToPdf(
            @RequestParam(required = false) LocalDate asOfDate) {
        LocalDate reportDate = asOfDate != null ? asOfDate : LocalDate.now();
        ReportService.TrialBalanceReport report = reportService.generateTrialBalance(reportDate);
        byte[] pdfBytes = reportExportService.exportTrialBalanceToPdf(report);

        String filename = "neraca-saldo-" + reportDate.format(FILE_DATE_FORMAT) + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ATTACHMENT_FILENAME_PREFIX + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @GetMapping("/trial-balance/export/excel")
    public ResponseEntity<byte[]> exportTrialBalanceToExcel(
            @RequestParam(required = false) LocalDate asOfDate) {
        LocalDate reportDate = asOfDate != null ? asOfDate : LocalDate.now();
        ReportService.TrialBalanceReport report = reportService.generateTrialBalance(reportDate);
        byte[] excelBytes = reportExportService.exportTrialBalanceToExcel(report);

        String filename = "neraca-saldo-" + reportDate.format(FILE_DATE_FORMAT) + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ATTACHMENT_FILENAME_PREFIX + filename + "\"")
                .contentType(MediaType.parseMediaType(CONTENT_TYPE_XLSX))
                .body(excelBytes);
    }

    // Balance Sheet Exports
    @GetMapping("/balance-sheet/export/pdf")
    public ResponseEntity<byte[]> exportBalanceSheetToPdf(
            @RequestParam(required = false) LocalDate asOfDate) {
        LocalDate reportDate = asOfDate != null ? asOfDate : LocalDate.now();
        ReportService.BalanceSheetReport report = reportService.generateBalanceSheet(reportDate);
        byte[] pdfBytes = reportExportService.exportBalanceSheetToPdf(report);

        String filename = "laporan-posisi-keuangan-" + reportDate.format(FILE_DATE_FORMAT) + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ATTACHMENT_FILENAME_PREFIX + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @GetMapping("/balance-sheet/export/excel")
    public ResponseEntity<byte[]> exportBalanceSheetToExcel(
            @RequestParam(required = false) LocalDate asOfDate) {
        LocalDate reportDate = asOfDate != null ? asOfDate : LocalDate.now();
        ReportService.BalanceSheetReport report = reportService.generateBalanceSheet(reportDate);
        byte[] excelBytes = reportExportService.exportBalanceSheetToExcel(report);

        String filename = "laporan-posisi-keuangan-" + reportDate.format(FILE_DATE_FORMAT) + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ATTACHMENT_FILENAME_PREFIX + filename + "\"")
                .contentType(MediaType.parseMediaType(CONTENT_TYPE_XLSX))
                .body(excelBytes);
    }

    // Income Statement Exports
    @GetMapping("/income-statement/export/pdf")
    public ResponseEntity<byte[]> exportIncomeStatementToPdf(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().withDayOfMonth(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        ReportService.IncomeStatementReport report = reportService.generateIncomeStatement(start, end);
        byte[] pdfBytes = reportExportService.exportIncomeStatementToPdf(report);

        String filename = "laporan-laba-rugi-" + start.format(FILE_DATE_FORMAT) + "-" + end.format(FILE_DATE_FORMAT) + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ATTACHMENT_FILENAME_PREFIX + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @GetMapping("/income-statement/export/excel")
    public ResponseEntity<byte[]> exportIncomeStatementToExcel(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().withDayOfMonth(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        ReportService.IncomeStatementReport report = reportService.generateIncomeStatement(start, end);
        byte[] excelBytes = reportExportService.exportIncomeStatementToExcel(report);

        String filename = "laporan-laba-rugi-" + start.format(FILE_DATE_FORMAT) + "-" + end.format(FILE_DATE_FORMAT) + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ATTACHMENT_FILENAME_PREFIX + filename + "\"")
                .contentType(MediaType.parseMediaType(CONTENT_TYPE_XLSX))
                .body(excelBytes);
    }

    // Cash Flow Exports
    @GetMapping("/cash-flow/export/pdf")
    public ResponseEntity<byte[]> exportCashFlowToPdf(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().withDayOfMonth(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        ReportService.CashFlowReport report = reportService.generateCashFlowStatement(start, end);
        byte[] pdfBytes = reportExportService.exportCashFlowToPdf(report);

        String filename = "laporan-arus-kas-" + start.format(FILE_DATE_FORMAT) + "-" + end.format(FILE_DATE_FORMAT) + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ATTACHMENT_FILENAME_PREFIX + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @GetMapping("/cash-flow/export/excel")
    public ResponseEntity<byte[]> exportCashFlowToExcel(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().withDayOfMonth(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        ReportService.CashFlowReport report = reportService.generateCashFlowStatement(start, end);
        byte[] excelBytes = reportExportService.exportCashFlowToExcel(report);

        String filename = "laporan-arus-kas-" + start.format(FILE_DATE_FORMAT) + "-" + end.format(FILE_DATE_FORMAT) + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ATTACHMENT_FILENAME_PREFIX + filename + "\"")
                .contentType(MediaType.parseMediaType(CONTENT_TYPE_XLSX))
                .body(excelBytes);
    }

    // ==================== PROFITABILITY REPORTS ====================

    @GetMapping("/project-profitability")
    public String projectProfitability(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            Model model) {
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_REPORTS);
        model.addAttribute(ATTR_REPORT_TYPE, "project-profitability");
        model.addAttribute("projects", projectService.findActiveProjects());

        LocalDate start = startDate != null ? startDate : LocalDate.now().withDayOfYear(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        model.addAttribute(ATTR_START_DATE, start);
        model.addAttribute(ATTR_END_DATE, end);
        model.addAttribute("selectedProjectId", projectId);

        if (projectId != null) {
            model.addAttribute(ATTR_REPORT, profitabilityService.calculateProjectProfitability(projectId, start, end));
            model.addAttribute("costOverrun", profitabilityService.calculateCostOverrun(projectId));
        }

        return "reports/project-profitability";
    }

    @GetMapping("/client-profitability")
    public String clientProfitability(
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            Model model) {
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_REPORTS);
        model.addAttribute(ATTR_REPORT_TYPE, "client-profitability");
        model.addAttribute("clients", clientService.findActiveClients());

        LocalDate start = startDate != null ? startDate : LocalDate.now().withDayOfYear(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        model.addAttribute(ATTR_START_DATE, start);
        model.addAttribute(ATTR_END_DATE, end);
        model.addAttribute("selectedClientId", clientId);

        if (clientId != null) {
            model.addAttribute(ATTR_REPORT, profitabilityService.calculateClientProfitability(clientId, start, end));
        }

        return "reports/client-profitability";
    }

    @GetMapping("/client-ranking")
    public String clientRanking(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "10") int limit,
            Model model) {
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_REPORTS);
        model.addAttribute(ATTR_REPORT_TYPE, "client-ranking");

        LocalDate start = startDate != null ? startDate : LocalDate.now().withDayOfYear(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        model.addAttribute(ATTR_START_DATE, start);
        model.addAttribute(ATTR_END_DATE, end);
        model.addAttribute("limit", limit);

        model.addAttribute("rankings", profitabilityService.getClientRanking(start, end, limit));

        return "reports/client-ranking";
    }

    // REST API Endpoints for profitability
    @GetMapping("/api/project-profitability")
    @ResponseBody
    public ResponseEntity<ProjectProfitabilityService.ProjectProfitabilityReport> apiProjectProfitability(
            @RequestParam UUID projectId,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        return ResponseEntity.ok(profitabilityService.calculateProjectProfitability(projectId, startDate, endDate));
    }

    @GetMapping("/api/client-profitability")
    @ResponseBody
    public ResponseEntity<ProjectProfitabilityService.ClientProfitabilityReport> apiClientProfitability(
            @RequestParam UUID clientId,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        return ResponseEntity.ok(profitabilityService.calculateClientProfitability(clientId, startDate, endDate));
    }

    @GetMapping("/api/cost-overrun")
    @ResponseBody
    public ResponseEntity<ProjectProfitabilityService.CostOverrunReport> apiCostOverrun(
            @RequestParam UUID projectId) {
        return ResponseEntity.ok(profitabilityService.calculateCostOverrun(projectId));
    }

    // ==================== PRINT ENDPOINTS ====================

    @GetMapping("/trial-balance/print")
    public String printTrialBalance(
            @RequestParam(required = false) LocalDate asOfDate,
            Model model) {
        LocalDate reportDate = asOfDate != null ? asOfDate : LocalDate.now();
        CompanyConfig company = companyConfigService.getConfig();

        model.addAttribute("asOfDate", reportDate);
        model.addAttribute(ATTR_REPORT, reportService.generateTrialBalance(reportDate));
        model.addAttribute("company", company);

        return "reports/trial-balance-print";
    }

    @GetMapping("/balance-sheet/print")
    public String printBalanceSheet(
            @RequestParam(required = false) LocalDate asOfDate,
            Model model) {
        LocalDate reportDate = asOfDate != null ? asOfDate : LocalDate.now();
        CompanyConfig company = companyConfigService.getConfig();

        model.addAttribute("asOfDate", reportDate);
        model.addAttribute(ATTR_REPORT, reportService.generateBalanceSheet(reportDate));
        model.addAttribute("company", company);

        return "reports/balance-sheet-print";
    }

    @GetMapping("/income-statement/print")
    public String printIncomeStatement(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            Model model) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().withDayOfMonth(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        CompanyConfig company = companyConfigService.getConfig();

        model.addAttribute(ATTR_START_DATE, start);
        model.addAttribute(ATTR_END_DATE, end);
        model.addAttribute(ATTR_REPORT, reportService.generateIncomeStatement(start, end));
        model.addAttribute("company", company);

        return "reports/income-statement-print";
    }

    @GetMapping("/cash-flow/print")
    public String printCashFlow(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            Model model) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().withDayOfMonth(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        CompanyConfig company = companyConfigService.getConfig();

        model.addAttribute(ATTR_START_DATE, start);
        model.addAttribute(ATTR_END_DATE, end);
        model.addAttribute(ATTR_REPORT, reportService.generateCashFlowStatement(start, end));
        model.addAttribute("company", company);

        return "reports/cash-flow-print";
    }

    // ==================== TAX REPORTS ====================

    @GetMapping("/ppn-summary")
    public String ppnSummary(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            Model model) {
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_REPORTS);
        model.addAttribute(ATTR_REPORT_TYPE, "ppn-summary");

        LocalDate start = startDate != null ? startDate : LocalDate.now().withDayOfMonth(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now();

        model.addAttribute(ATTR_START_DATE, start);
        model.addAttribute(ATTR_END_DATE, end);
        model.addAttribute(ATTR_REPORT, taxReportService.generatePPNSummary(start, end));

        return "reports/ppn-summary";
    }

    @GetMapping("/pph23-withholding")
    public String pph23Withholding(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            Model model) {
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_REPORTS);
        model.addAttribute(ATTR_REPORT_TYPE, "pph23-withholding");

        LocalDate start = startDate != null ? startDate : LocalDate.now().withDayOfMonth(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now();

        model.addAttribute(ATTR_START_DATE, start);
        model.addAttribute(ATTR_END_DATE, end);
        model.addAttribute(ATTR_REPORT, taxReportService.generatePPh23Withholding(start, end));

        return "reports/pph23-withholding";
    }

    @GetMapping("/tax-summary")
    public String taxSummary(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            Model model) {
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_REPORTS);
        model.addAttribute(ATTR_REPORT_TYPE, "tax-summary");

        LocalDate start = startDate != null ? startDate : LocalDate.now().withDayOfMonth(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now();

        model.addAttribute(ATTR_START_DATE, start);
        model.addAttribute(ATTR_END_DATE, end);
        model.addAttribute(ATTR_REPORT, taxReportService.generateTaxSummary(start, end));

        return "reports/tax-summary";
    }

    // Tax Reports API Endpoints

    @GetMapping("/api/ppn-summary")
    @ResponseBody
    public ResponseEntity<TaxReportService.PPNSummaryReport> apiPPNSummary(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        return ResponseEntity.ok(taxReportService.generatePPNSummary(startDate, endDate));
    }

    @GetMapping("/api/pph23-withholding")
    @ResponseBody
    public ResponseEntity<TaxReportService.PPh23WithholdingReport> apiPPh23Withholding(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        return ResponseEntity.ok(taxReportService.generatePPh23Withholding(startDate, endDate));
    }

    @GetMapping("/api/tax-summary")
    @ResponseBody
    public ResponseEntity<TaxReportService.TaxSummaryReport> apiTaxSummary(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        return ResponseEntity.ok(taxReportService.generateTaxSummary(startDate, endDate));
    }

    // Tax Reports Print Endpoints

    @GetMapping("/ppn-summary/print")
    public String printPPNSummary(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            Model model) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().withDayOfMonth(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        CompanyConfig company = companyConfigService.getConfig();

        model.addAttribute(ATTR_START_DATE, start);
        model.addAttribute(ATTR_END_DATE, end);
        model.addAttribute(ATTR_REPORT, taxReportService.generatePPNSummary(start, end));
        model.addAttribute("company", company);

        return "reports/ppn-summary-print";
    }

    @GetMapping("/pph23-withholding/print")
    public String printPPh23Withholding(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            Model model) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().withDayOfMonth(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        CompanyConfig company = companyConfigService.getConfig();

        model.addAttribute(ATTR_START_DATE, start);
        model.addAttribute(ATTR_END_DATE, end);
        model.addAttribute(ATTR_REPORT, taxReportService.generatePPh23Withholding(start, end));
        model.addAttribute("company", company);

        return "reports/pph23-withholding-print";
    }

    // ==================== DEPRECIATION REPORT ====================

    @GetMapping("/depreciation")
    public String depreciationReport(
            @RequestParam(required = false) Integer year,
            Model model) {
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_REPORTS);
        model.addAttribute(ATTR_REPORT_TYPE, "depreciation");

        int reportYear = year != null ? year : LocalDate.now().getYear();
        model.addAttribute("year", reportYear);
        model.addAttribute(ATTR_REPORT, depreciationReportService.generateReport(reportYear));

        return "reports/depreciation";
    }

    @GetMapping("/depreciation/print")
    public String printDepreciationReport(
            @RequestParam(required = false) Integer year,
            Model model) {
        int reportYear = year != null ? year : LocalDate.now().getYear();
        CompanyConfig company = companyConfigService.getConfig();

        model.addAttribute("year", reportYear);
        model.addAttribute(ATTR_REPORT, depreciationReportService.generateReport(reportYear));
        model.addAttribute("company", company);

        return "reports/depreciation-print";
    }

    @GetMapping("/api/depreciation")
    @ResponseBody
    public ResponseEntity<DepreciationReportService.DepreciationReport> apiDepreciationReport(
            @RequestParam(required = false) Integer year) {
        int reportYear = year != null ? year : LocalDate.now().getYear();
        return ResponseEntity.ok(depreciationReportService.generateReport(reportYear));
    }

    @GetMapping("/depreciation/export/pdf")
    public ResponseEntity<byte[]> exportDepreciationToPdf(
            @RequestParam(required = false) Integer year) {
        int reportYear = year != null ? year : LocalDate.now().getYear();
        DepreciationReportService.DepreciationReport report = depreciationReportService.generateReport(reportYear);
        byte[] pdfBytes = reportExportService.exportDepreciationToPdf(report);

        String filename = "laporan-penyusutan-" + reportYear + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ATTACHMENT_FILENAME_PREFIX + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @GetMapping("/depreciation/export/excel")
    public ResponseEntity<byte[]> exportDepreciationToExcel(
            @RequestParam(required = false) Integer year) {
        int reportYear = year != null ? year : LocalDate.now().getYear();
        DepreciationReportService.DepreciationReport report = depreciationReportService.generateReport(reportYear);
        byte[] excelBytes = reportExportService.exportDepreciationToExcel(report);

        String filename = "laporan-penyusutan-" + reportYear + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ATTACHMENT_FILENAME_PREFIX + filename + "\"")
                .contentType(MediaType.parseMediaType(CONTENT_TYPE_XLSX))
                .body(excelBytes);
    }

    // ==================== FISCAL YEAR CLOSING ====================

    @GetMapping("/fiscal-closing")
    public String fiscalClosing(
            @RequestParam(required = false) Integer year,
            Model model) {
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_REPORTS);
        model.addAttribute(ATTR_REPORT_TYPE, "fiscal-closing");

        int closingYear = year != null ? year : LocalDate.now().getYear() - 1;
        model.addAttribute("year", closingYear);
        model.addAttribute("preview", fiscalYearClosingService.previewClosing(closingYear));

        return "reports/fiscal-closing";
    }

    @PostMapping("/fiscal-closing/{year}/execute")
    public String executeFiscalClosing(
            @PathVariable int year,
            RedirectAttributes redirectAttributes) {
        try {
            var entries = fiscalYearClosingService.executeClosing(year);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Jurnal penutup berhasil dibuat: " + entries.size() + " jurnal");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/reports/fiscal-closing?year=" + year;
    }

    @PostMapping("/fiscal-closing/{year}/reverse")
    public String reverseFiscalClosing(
            @PathVariable int year,
            @RequestParam String reason,
            RedirectAttributes redirectAttributes) {
        try {
            int count = fiscalYearClosingService.reverseClosing(year, reason);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Jurnal penutup berhasil dibatalkan: " + count + " jurnal");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/reports/fiscal-closing?year=" + year;
    }

    @GetMapping("/api/fiscal-closing/preview")
    @ResponseBody
    public ResponseEntity<FiscalYearClosingService.ClosingPreview> apiFiscalClosingPreview(
            @RequestParam int year) {
        return ResponseEntity.ok(fiscalYearClosingService.previewClosing(year));
    }
}
