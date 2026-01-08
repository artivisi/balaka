package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.InventoryReportService;
import com.artivisi.accountingfinance.service.ProductCategoryService;
import com.artivisi.accountingfinance.service.ProductService;
import com.artivisi.accountingfinance.service.ReportExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Controller
@RequestMapping("/inventory/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permission.INVENTORY_VIEW + "')")
public class InventoryReportController {

    private final InventoryReportService reportService;
    private final ProductCategoryService categoryService;
    private final ProductService productService;
    private final ReportExportService reportExportService;

    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    // Model attribute constants
    private static final String ATTR_CURRENT_PAGE = "currentPage";
    private static final String ATTR_REPORT_TYPE = "reportType";
    private static final String ATTR_START_DATE = "startDate";
    private static final String ATTR_END_DATE = "endDate";
    private static final String ATTR_CATEGORY_ID = "categoryId";
    private static final String ATTR_PRODUCT_ID = "productId";
    private static final String ATTR_CATEGORIES = "categories";
    private static final String ATTR_REPORT = "report";
    private static final String ATTR_AS_OF_DATE = "asOfDate";

    // HTTP header constants
    private static final String ATTACHMENT_FILENAME_PREFIX = "attachment; filename=\"";
    private static final String XLSX_EXTENSION = ".xlsx";
    private static final String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    // Page constants
    private static final String PAGE_INVENTORY_REPORTS = "inventory-reports";

    @GetMapping
    public String index(Model model) {
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_INVENTORY_REPORTS);
        return "inventory/reports/index";
    }

    @GetMapping("/stock-balance")
    public String stockBalance(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String search,
            Model model) {

        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_INVENTORY_REPORTS);
        model.addAttribute(ATTR_REPORT_TYPE, "stock-balance");
        model.addAttribute(ATTR_CATEGORY_ID, categoryId);
        model.addAttribute("search", search);
        model.addAttribute(ATTR_CATEGORIES, categoryService.findAllActive());
        model.addAttribute(ATTR_REPORT, reportService.generateStockBalanceReport(categoryId, search));
        model.addAttribute(ATTR_AS_OF_DATE, LocalDate.now());

        return "inventory/reports/stock-balance";
    }

    @GetMapping("/stock-balance/print")
    public String stockBalancePrint(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String search,
            Model model) {

        model.addAttribute(ATTR_CATEGORY_ID, categoryId);
        model.addAttribute("search", search);
        model.addAttribute(ATTR_REPORT, reportService.generateStockBalanceReport(categoryId, search));
        model.addAttribute(ATTR_AS_OF_DATE, LocalDate.now());

        return "inventory/reports/stock-balance-print";
    }

    @GetMapping("/stock-movement")
    public String stockMovement(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID productId,
            Model model) {

        LocalDate start = startDate != null ? startDate : LocalDate.now().withDayOfMonth(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now();

        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_INVENTORY_REPORTS);
        model.addAttribute(ATTR_REPORT_TYPE, "stock-movement");
        model.addAttribute(ATTR_START_DATE, start);
        model.addAttribute(ATTR_END_DATE, end);
        model.addAttribute(ATTR_CATEGORY_ID, categoryId);
        model.addAttribute(ATTR_PRODUCT_ID, productId);
        model.addAttribute(ATTR_CATEGORIES, categoryService.findAllActive());
        model.addAttribute("products", productService.findAllActive());
        model.addAttribute(ATTR_REPORT, reportService.generateStockMovementReport(start, end, categoryId, productId));

        return "inventory/reports/stock-movement";
    }

    @GetMapping("/stock-movement/print")
    public String stockMovementPrint(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID productId,
            Model model) {

        LocalDate start = startDate != null ? startDate : LocalDate.now().withDayOfMonth(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now();

        model.addAttribute(ATTR_START_DATE, start);
        model.addAttribute(ATTR_END_DATE, end);
        model.addAttribute(ATTR_CATEGORY_ID, categoryId);
        model.addAttribute(ATTR_PRODUCT_ID, productId);
        model.addAttribute(ATTR_REPORT, reportService.generateStockMovementReport(start, end, categoryId, productId));

        return "inventory/reports/stock-movement-print";
    }

    @GetMapping("/valuation")
    public String inventoryValuation(
            @RequestParam(required = false) UUID categoryId,
            Model model) {

        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_INVENTORY_REPORTS);
        model.addAttribute(ATTR_REPORT_TYPE, "valuation");
        model.addAttribute(ATTR_CATEGORY_ID, categoryId);
        model.addAttribute(ATTR_CATEGORIES, categoryService.findAllActive());
        model.addAttribute(ATTR_REPORT, reportService.generateValuationReport(categoryId));
        model.addAttribute(ATTR_AS_OF_DATE, LocalDate.now());

        return "inventory/reports/valuation";
    }

    @GetMapping("/valuation/print")
    public String inventoryValuationPrint(
            @RequestParam(required = false) UUID categoryId,
            Model model) {

        model.addAttribute(ATTR_CATEGORY_ID, categoryId);
        model.addAttribute(ATTR_REPORT, reportService.generateValuationReport(categoryId));
        model.addAttribute(ATTR_AS_OF_DATE, LocalDate.now());

        return "inventory/reports/valuation-print";
    }

    @GetMapping("/profitability")
    public String productProfitability(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID productId,
            Model model) {

        LocalDate start = startDate != null ? startDate : LocalDate.now().withDayOfMonth(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now();

        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_INVENTORY_REPORTS);
        model.addAttribute(ATTR_REPORT_TYPE, "profitability");
        model.addAttribute(ATTR_START_DATE, start);
        model.addAttribute(ATTR_END_DATE, end);
        model.addAttribute(ATTR_CATEGORY_ID, categoryId);
        model.addAttribute(ATTR_PRODUCT_ID, productId);
        model.addAttribute(ATTR_CATEGORIES, categoryService.findAllActive());
        model.addAttribute("products", productService.findAllActive());
        model.addAttribute(ATTR_REPORT, reportService.generateProfitabilityReport(start, end, categoryId, productId));

        return "inventory/reports/profitability";
    }

    @GetMapping("/profitability/print")
    public String productProfitabilityPrint(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID productId,
            Model model) {

        LocalDate start = startDate != null ? startDate : LocalDate.now().withDayOfMonth(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now();

        model.addAttribute(ATTR_START_DATE, start);
        model.addAttribute(ATTR_END_DATE, end);
        model.addAttribute(ATTR_REPORT, reportService.generateProfitabilityReport(start, end, categoryId, productId));

        return "inventory/reports/profitability-print";
    }

    // ==================== EXPORT ENDPOINTS ====================

    // Stock Balance Exports
    @GetMapping("/stock-balance/export/pdf")
    public ResponseEntity<byte[]> exportStockBalanceToPdf(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String search) {
        LocalDate asOfDate = LocalDate.now();
        InventoryReportService.StockBalanceReport report = reportService.generateStockBalanceReport(categoryId, search);
        byte[] pdfBytes = reportExportService.exportStockBalanceToPdf(report, asOfDate);

        String filename = "saldo-stok-" + asOfDate.format(FILE_DATE_FORMAT) + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ATTACHMENT_FILENAME_PREFIX + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @GetMapping("/stock-balance/export/excel")
    public ResponseEntity<byte[]> exportStockBalanceToExcel(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String search) {
        LocalDate asOfDate = LocalDate.now();
        InventoryReportService.StockBalanceReport report = reportService.generateStockBalanceReport(categoryId, search);
        byte[] excelBytes = reportExportService.exportStockBalanceToExcel(report, asOfDate);

        String filename = "saldo-stok-" + asOfDate.format(FILE_DATE_FORMAT) + XLSX_EXTENSION;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ATTACHMENT_FILENAME_PREFIX + filename + "\"")
                .contentType(MediaType.parseMediaType(XLSX_CONTENT_TYPE))
                .body(excelBytes);
    }

    // Stock Movement Exports
    @GetMapping("/stock-movement/export/pdf")
    public ResponseEntity<byte[]> exportStockMovementToPdf(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID productId) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().withDayOfMonth(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        InventoryReportService.StockMovementReport report = reportService.generateStockMovementReport(start, end, categoryId, productId);
        byte[] pdfBytes = reportExportService.exportStockMovementToPdf(report);

        String filename = "mutasi-stok-" + start.format(FILE_DATE_FORMAT) + "-" + end.format(FILE_DATE_FORMAT) + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ATTACHMENT_FILENAME_PREFIX + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @GetMapping("/stock-movement/export/excel")
    public ResponseEntity<byte[]> exportStockMovementToExcel(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID productId) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().withDayOfMonth(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        InventoryReportService.StockMovementReport report = reportService.generateStockMovementReport(start, end, categoryId, productId);
        byte[] excelBytes = reportExportService.exportStockMovementToExcel(report);

        String filename = "mutasi-stok-" + start.format(FILE_DATE_FORMAT) + "-" + end.format(FILE_DATE_FORMAT) + XLSX_EXTENSION;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ATTACHMENT_FILENAME_PREFIX + filename + "\"")
                .contentType(MediaType.parseMediaType(XLSX_CONTENT_TYPE))
                .body(excelBytes);
    }

    // Valuation Exports
    @GetMapping("/valuation/export/pdf")
    public ResponseEntity<byte[]> exportValuationToPdf(
            @RequestParam(required = false) UUID categoryId) {
        LocalDate asOfDate = LocalDate.now();
        InventoryReportService.ValuationReport report = reportService.generateValuationReport(categoryId);
        byte[] pdfBytes = reportExportService.exportValuationToPdf(report, asOfDate);

        String filename = "penilaian-persediaan-" + asOfDate.format(FILE_DATE_FORMAT) + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ATTACHMENT_FILENAME_PREFIX + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @GetMapping("/valuation/export/excel")
    public ResponseEntity<byte[]> exportValuationToExcel(
            @RequestParam(required = false) UUID categoryId) {
        LocalDate asOfDate = LocalDate.now();
        InventoryReportService.ValuationReport report = reportService.generateValuationReport(categoryId);
        byte[] excelBytes = reportExportService.exportValuationToExcel(report, asOfDate);

        String filename = "penilaian-persediaan-" + asOfDate.format(FILE_DATE_FORMAT) + XLSX_EXTENSION;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ATTACHMENT_FILENAME_PREFIX + filename + "\"")
                .contentType(MediaType.parseMediaType(XLSX_CONTENT_TYPE))
                .body(excelBytes);
    }

    // Profitability Exports
    @GetMapping("/profitability/export/pdf")
    public ResponseEntity<byte[]> exportProfitabilityToPdf(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID productId) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().withDayOfMonth(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        InventoryReportService.ProfitabilityReport report = reportService.generateProfitabilityReport(start, end, categoryId, productId);
        byte[] pdfBytes = reportExportService.exportProductProfitabilityToPdf(report);

        String filename = "profitabilitas-produk-" + start.format(FILE_DATE_FORMAT) + "-" + end.format(FILE_DATE_FORMAT) + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ATTACHMENT_FILENAME_PREFIX + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @GetMapping("/profitability/export/excel")
    public ResponseEntity<byte[]> exportProfitabilityToExcel(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID productId) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().withDayOfMonth(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        InventoryReportService.ProfitabilityReport report = reportService.generateProfitabilityReport(start, end, categoryId, productId);
        byte[] excelBytes = reportExportService.exportProductProfitabilityToExcel(report);

        String filename = "profitabilitas-produk-" + start.format(FILE_DATE_FORMAT) + "-" + end.format(FILE_DATE_FORMAT) + XLSX_EXTENSION;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ATTACHMENT_FILENAME_PREFIX + filename + "\"")
                .contentType(MediaType.parseMediaType(XLSX_CONTENT_TYPE))
                .body(excelBytes);
    }
}
