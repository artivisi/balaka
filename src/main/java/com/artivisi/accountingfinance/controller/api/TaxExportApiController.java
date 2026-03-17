package com.artivisi.accountingfinance.controller.api;

import com.artivisi.accountingfinance.controller.api.AnalysisResponse;
import com.artivisi.accountingfinance.entity.FiscalAdjustment;
import com.artivisi.accountingfinance.entity.TaxTransactionDetail;
import com.artivisi.accountingfinance.enums.AuditEventType;
import com.artivisi.accountingfinance.service.CoretaxExportService;
import com.artivisi.accountingfinance.service.ReportExportService;
import com.artivisi.accountingfinance.service.SecurityAuditService;
import com.artivisi.accountingfinance.service.SptTahunanExportService;
import com.artivisi.accountingfinance.service.SptTahunanExportService.Bpa1Report;
import com.artivisi.accountingfinance.service.SptTahunanExportService.L1Report;
import com.artivisi.accountingfinance.service.SptTahunanExportService.L4Report;
import com.artivisi.accountingfinance.service.SptTahunanExportService.L9Report;
import com.artivisi.accountingfinance.service.SptTahunanExportService.SptLampiranReport;
import com.artivisi.accountingfinance.service.SptTahunanExportService.Transkrip8AReport;
import com.artivisi.accountingfinance.service.TaxReportDetailService;
import com.artivisi.accountingfinance.service.TaxReportDetailService.PPhBadanCalculation;
import com.artivisi.accountingfinance.service.TaxReportDetailService.PPNDetailReport;
import com.artivisi.accountingfinance.service.TaxReportDetailService.PPh23DetailReport;
import com.artivisi.accountingfinance.service.TaxReportDetailService.RekonsiliasiFiskalReport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tax-export")
@Tag(name = "Tax Export", description = "Export tax data for SPT preparation (e-Faktur, e-Bupot, PPN/PPh reports)")
@PreAuthorize("hasAuthority('SCOPE_tax-export:read')")
@RequiredArgsConstructor
@Slf4j
public class TaxExportApiController {

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String PARAM_START_MONTH = "startMonth";
    private static final String PARAM_END_MONTH = "endMonth";
    private static final String FILE_EXT_XLSX = ".xlsx";
    private static final String PARAM_START_DATE = "startDate";
    private static final String PARAM_END_DATE = "endDate";
    private static final String FORMAT_EXCEL = "excel";
    private static final String META_DESCRIPTION = "description";
    private static final String META_CURRENCY = "currency";

    private final CoretaxExportService coretaxExportService;
    private final TaxReportDetailService taxReportDetailService;
    private final ReportExportService reportExportService;
    private final SecurityAuditService securityAuditService;
    private final SptTahunanExportService sptTahunanExportService;

    // ==================== EXCEL EXPORT ENDPOINTS ====================

    @GetMapping("/efaktur-keluaran")
    @Operation(summary = "Export e-Faktur Keluaran (output VAT) to Coretax-compatible Excel")
    public ResponseEntity<byte[]> exportEfakturKeluaran(
            @Parameter(description = "Start month (yyyy-MM)") @RequestParam String startMonth,
            @Parameter(description = "End month (yyyy-MM)") @RequestParam String endMonth) throws IOException {

        LocalDate[] range = parseMonthRange(startMonth, endMonth);

        byte[] excel = coretaxExportService.exportEFakturKeluaran(range[0], range[1]);

        auditAccess("efaktur-keluaran", Map.of(PARAM_START_MONTH, startMonth, PARAM_END_MONTH, endMonth));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=efaktur-keluaran-" + startMonth + "-" + endMonth + FILE_EXT_XLSX)
                .contentType(MediaType.parseMediaType(XLSX_CONTENT_TYPE))
                .body(excel);
    }

    @GetMapping("/efaktur-masukan")
    @Operation(summary = "Export e-Faktur Masukan (input VAT) to Coretax-compatible Excel")
    public ResponseEntity<byte[]> exportEfakturMasukan(
            @Parameter(description = "Start month (yyyy-MM)") @RequestParam String startMonth,
            @Parameter(description = "End month (yyyy-MM)") @RequestParam String endMonth) throws IOException {

        LocalDate[] range = parseMonthRange(startMonth, endMonth);

        byte[] excel = coretaxExportService.exportEFakturMasukan(range[0], range[1]);

        auditAccess("efaktur-masukan", Map.of(PARAM_START_MONTH, startMonth, PARAM_END_MONTH, endMonth));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=efaktur-masukan-" + startMonth + "-" + endMonth + FILE_EXT_XLSX)
                .contentType(MediaType.parseMediaType(XLSX_CONTENT_TYPE))
                .body(excel);
    }

    @GetMapping("/bupot-unifikasi")
    @Operation(summary = "Export e-Bupot Unifikasi (PPh withholding) to Coretax-compatible Excel")
    public ResponseEntity<byte[]> exportBupotUnifikasi(
            @Parameter(description = "Start month (yyyy-MM)") @RequestParam String startMonth,
            @Parameter(description = "End month (yyyy-MM)") @RequestParam String endMonth) throws IOException {

        LocalDate[] range = parseMonthRange(startMonth, endMonth);

        byte[] excel = coretaxExportService.exportBupotUnifikasi(range[0], range[1]);

        auditAccess("bupot-unifikasi", Map.of(PARAM_START_MONTH, startMonth, PARAM_END_MONTH, endMonth));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=bupot-unifikasi-" + startMonth + "-" + endMonth + FILE_EXT_XLSX)
                .contentType(MediaType.parseMediaType(XLSX_CONTENT_TYPE))
                .body(excel);
    }

    // ==================== JSON / EXCEL ENDPOINTS ====================

    @GetMapping("/ppn-detail")
    @Transactional(readOnly = true)
    @Operation(summary = "PPN detail report (JSON or Excel)",
            description = "PPN detail with Faktur Keluaran/Masukan breakdown. Add ?format=excel for XLSX.")
    @ApiResponse(responseCode = "200", description = "JSON report",
            content = @Content(schema = @Schema(implementation = PPNDetailData.class)))
    @ApiResponse(responseCode = "200", description = "Excel download (when format=excel)",
            content = @Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
    public ResponseEntity<Object> getPpnDetail(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @Parameter(description = "Set to 'excel' for XLSX download") @RequestParam(required = false) String format) {

        LocalDate start = parseDate(startDate);
        LocalDate end = parseDate(endDate);

        PPNDetailReport report = taxReportDetailService.generatePPNDetailReport(start, end);

        auditAccess("ppn-detail", Map.of(PARAM_START_DATE, startDate, PARAM_END_DATE, endDate));

        if (FORMAT_EXCEL.equals(format)) {
            byte[] excel = reportExportService.exportPpnDetailToExcel(report);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=ppn-detail-" + startDate + "-" + endDate + FILE_EXT_XLSX)
                    .contentType(MediaType.parseMediaType(XLSX_CONTENT_TYPE))
                    .body(excel);
        }

        PPNDetailData data = toPPNDetailData(report);

        return ResponseEntity.ok(new AnalysisResponse<>(
                "ppn-detail", LocalDateTime.now(),
                Map.of(PARAM_START_DATE, startDate, PARAM_END_DATE, endDate),
                data,
                Map.of(META_DESCRIPTION, "PPN detail report with Faktur Keluaran and Masukan breakdown",
                        META_CURRENCY, "IDR")));
    }

    @GetMapping("/pph23-detail")
    @Transactional(readOnly = true)
    @Operation(summary = "PPh 23 withholding tax detail report (JSON or Excel)",
            description = "PPh 23 detail report. Add ?format=excel for XLSX.")
    @ApiResponse(responseCode = "200", description = "JSON report",
            content = @Content(schema = @Schema(implementation = PPh23DetailData.class)))
    @ApiResponse(responseCode = "200", description = "Excel download (when format=excel)",
            content = @Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
    public ResponseEntity<Object> getPph23Detail(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @Parameter(description = "Set to 'excel' for XLSX download") @RequestParam(required = false) String format) {

        LocalDate start = parseDate(startDate);
        LocalDate end = parseDate(endDate);

        PPh23DetailReport report = taxReportDetailService.generatePPh23DetailReport(start, end);

        auditAccess("pph23-detail", Map.of(PARAM_START_DATE, startDate, PARAM_END_DATE, endDate));

        if (FORMAT_EXCEL.equals(format)) {
            byte[] excel = reportExportService.exportPph23DetailToExcel(report);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=pph23-detail-" + startDate + "-" + endDate + FILE_EXT_XLSX)
                    .contentType(MediaType.parseMediaType(XLSX_CONTENT_TYPE))
                    .body(excel);
        }

        PPh23DetailData data = toPPh23DetailData(report);

        return ResponseEntity.ok(new AnalysisResponse<>(
                "pph23-detail", LocalDateTime.now(),
                Map.of(PARAM_START_DATE, startDate, PARAM_END_DATE, endDate),
                data,
                Map.of(META_DESCRIPTION, "PPh 23 withholding tax detail report",
                        META_CURRENCY, "IDR")));
    }

    @GetMapping("/rekonsiliasi-fiskal")
    @Transactional(readOnly = true)
    @Operation(summary = "Fiscal reconciliation: commercial income to taxable income (JSON or Excel)",
            description = "Fiscal reconciliation report. Add ?format=excel for XLSX.")
    @ApiResponse(responseCode = "200", description = "JSON report",
            content = @Content(schema = @Schema(implementation = RekonsiliasiFiskalData.class)))
    @ApiResponse(responseCode = "200", description = "Excel download (when format=excel)",
            content = @Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
    public ResponseEntity<Object> getRekonsiliasiFiskal(
            @RequestParam int year,
            @Parameter(description = "Set to 'excel' for XLSX download") @RequestParam(required = false) String format) {

        RekonsiliasiFiskalReport report = taxReportDetailService.generateRekonsiliasiFiskal(year);

        auditAccess("rekonsiliasi-fiskal", Map.of("year", String.valueOf(year)));

        if (FORMAT_EXCEL.equals(format)) {
            byte[] excel = reportExportService.exportRekonsiliasiFiskalToExcel(report);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=rekonsiliasi-fiskal-" + year + FILE_EXT_XLSX)
                    .contentType(MediaType.parseMediaType(XLSX_CONTENT_TYPE))
                    .body(excel);
        }

        RekonsiliasiFiskalData data = toRekonsiliasiFiskalData(report);

        return ResponseEntity.ok(new AnalysisResponse<>(
                "rekonsiliasi-fiskal", LocalDateTime.now(),
                Map.of("year", String.valueOf(year)),
                data,
                Map.of(META_DESCRIPTION, "Fiscal reconciliation: commercial income \u2192 taxable income (PKP)",
                        META_CURRENCY, "IDR")));
    }

    @GetMapping("/pph-badan")
    @Transactional(readOnly = true)
    public ResponseEntity<AnalysisResponse<PPhBadanData>> getPphBadan(
            @RequestParam int year) {

        RekonsiliasiFiskalReport rekonsiliasi = taxReportDetailService.generateRekonsiliasiFiskal(year);
        PPhBadanCalculation calc = rekonsiliasi.pphBadan();

        auditAccess("pph-badan", Map.of("year", String.valueOf(year)));

        PPhBadanData data = new PPhBadanData(
                calc.pkp(), calc.totalRevenue(), calc.pphTerutang(),
                calc.calculationMethod(),
                calc.kreditPajakPPh23(), calc.kreditPajakPPh25(),
                calc.totalKreditPajak(), calc.pph29());

        return ResponseEntity.ok(new AnalysisResponse<>(
                "pph-badan", LocalDateTime.now(),
                Map.of("year", String.valueOf(year)),
                data,
                Map.of(META_DESCRIPTION, "PPh Badan corporate income tax calculation with Pasal 31E facility",
                        META_CURRENCY, "IDR")));
    }

    // ==================== SPT TAHUNAN BADAN EXPORTS ====================

    @GetMapping("/spt-tahunan/l1")
    @Transactional(readOnly = true)
    @Operation(summary = "L1 Rekonsiliasi Fiskal (JSON or Excel)",
            description = "Fiscal reconciliation in Coretax L1 layout. Add ?format=excel for XLSX.")
    public ResponseEntity<Object> getSptL1(
            @RequestParam int year,
            @Parameter(description = "Set to 'excel' for XLSX download") @RequestParam(required = false) String format)
            throws IOException {

        L1Report report = sptTahunanExportService.generateL1(year);
        auditAccess("spt-tahunan-l1", Map.of("year", String.valueOf(year)));

        if (FORMAT_EXCEL.equals(format)) {
            byte[] excel = coretaxExportService.exportL1ToExcel(report);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=spt-l1-rekonsiliasi-fiskal-" + year + FILE_EXT_XLSX)
                    .contentType(MediaType.parseMediaType(XLSX_CONTENT_TYPE))
                    .body(excel);
        }

        return ResponseEntity.ok(new AnalysisResponse<>(
                "spt-tahunan-l1", LocalDateTime.now(),
                Map.of("year", String.valueOf(year)),
                report,
                Map.of(META_DESCRIPTION, "Lampiran I - Rekonsiliasi Fiskal (Coretax layout)",
                        META_CURRENCY, "IDR")));
    }

    @GetMapping("/spt-tahunan/l4")
    @Transactional(readOnly = true)
    @Operation(summary = "L4 Penghasilan Final (JSON or Excel)",
            description = "PPh 4(2) final income summary. Add ?format=excel for XLSX.")
    public ResponseEntity<Object> getSptL4(
            @RequestParam int year,
            @Parameter(description = "Set to 'excel' for XLSX download") @RequestParam(required = false) String format)
            throws IOException {

        L4Report report = sptTahunanExportService.generateL4(year);
        auditAccess("spt-tahunan-l4", Map.of("year", String.valueOf(year)));

        if (FORMAT_EXCEL.equals(format)) {
            byte[] excel = coretaxExportService.exportL4ToExcel(report);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=spt-l4-penghasilan-final-" + year + FILE_EXT_XLSX)
                    .contentType(MediaType.parseMediaType(XLSX_CONTENT_TYPE))
                    .body(excel);
        }

        return ResponseEntity.ok(new AnalysisResponse<>(
                "spt-tahunan-l4", LocalDateTime.now(),
                Map.of("year", String.valueOf(year)),
                report,
                Map.of(META_DESCRIPTION, "Lampiran IV - Penghasilan yang Dikenakan PPh Final",
                        META_CURRENCY, "IDR")));
    }

    @GetMapping("/spt-tahunan/transkrip-8a")
    @Transactional(readOnly = true)
    @Operation(summary = "Transkrip 8A Laporan Keuangan (JSON or Excel)",
            description = "Balance sheet + income statement in Coretax 8A layout. Add ?format=excel for XLSX.")
    public ResponseEntity<Object> getSptTranskrip8A(
            @RequestParam int year,
            @Parameter(description = "Set to 'excel' for XLSX download") @RequestParam(required = false) String format)
            throws IOException {

        Transkrip8AReport report = sptTahunanExportService.generateTranskrip8A(year);
        auditAccess("spt-tahunan-transkrip-8a", Map.of("year", String.valueOf(year)));

        if (FORMAT_EXCEL.equals(format)) {
            byte[] excel = coretaxExportService.exportTranskrip8AToExcel(report);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=spt-transkrip-8a-" + year + FILE_EXT_XLSX)
                    .contentType(MediaType.parseMediaType(XLSX_CONTENT_TYPE))
                    .body(excel);
        }

        return ResponseEntity.ok(new AnalysisResponse<>(
                "spt-tahunan-transkrip-8a", LocalDateTime.now(),
                Map.of("year", String.valueOf(year)),
                report,
                Map.of(META_DESCRIPTION, "Transkrip Kutipan Elemen Laporan Keuangan (8A-Jasa)",
                        META_CURRENCY, "IDR")));
    }

    @GetMapping("/spt-tahunan/l9")
    @Transactional(readOnly = true)
    @Operation(summary = "L9 Penyusutan & Amortisasi (JSON or Excel)",
            description = "Fixed asset depreciation in DJP converter format. Add ?format=excel for XLSX.")
    public ResponseEntity<Object> getSptL9(
            @RequestParam int year,
            @Parameter(description = "Set to 'excel' for XLSX download") @RequestParam(required = false) String format)
            throws IOException {

        L9Report report = sptTahunanExportService.generateL9(year);
        auditAccess("spt-tahunan-l9", Map.of("year", String.valueOf(year)));

        if (FORMAT_EXCEL.equals(format)) {
            byte[] excel = coretaxExportService.exportL9ToExcel(report);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=spt-l9-penyusutan-" + year + FILE_EXT_XLSX)
                    .contentType(MediaType.parseMediaType(XLSX_CONTENT_TYPE))
                    .body(excel);
        }

        return ResponseEntity.ok(new AnalysisResponse<>(
                "spt-tahunan-l9", LocalDateTime.now(),
                Map.of("year", String.valueOf(year)),
                report,
                Map.of(META_DESCRIPTION, "Lampiran 9 - Daftar Penyusutan & Amortisasi",
                        META_CURRENCY, "IDR")));
    }

    @GetMapping("/ebupot-pph21")
    @Transactional(readOnly = true)
    @Operation(summary = "e-Bupot PPh 21 Annual / 1721-A1 bulk (JSON or Excel)",
            description = "Annual PPh 21 reconciliation for all employees. Add ?format=excel for DJP BPA1 converter format.")
    public ResponseEntity<Object> getEbupotPph21(
            @RequestParam int year,
            @Parameter(description = "Set to 'excel' for XLSX download") @RequestParam(required = false) String format)
            throws IOException {

        Bpa1Report report = sptTahunanExportService.generateBpa1(year);
        auditAccess("ebupot-pph21", Map.of("year", String.valueOf(year)));

        if (FORMAT_EXCEL.equals(format)) {
            byte[] excel = coretaxExportService.exportBpa1ToExcel(report);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=ebupot-pph21-1721a1-" + year + FILE_EXT_XLSX)
                    .contentType(MediaType.parseMediaType(XLSX_CONTENT_TYPE))
                    .body(excel);
        }

        return ResponseEntity.ok(new AnalysisResponse<>(
                "ebupot-pph21", LocalDateTime.now(),
                Map.of("year", String.valueOf(year)),
                report,
                Map.of(META_DESCRIPTION, "e-Bupot PPh 21 Annual (1721-A1) - all employees",
                        META_CURRENCY, "IDR")));
    }

    // ==================== CONSOLIDATED LAMPIRAN ====================

    @GetMapping("/spt-tahunan/lampiran")
    @Transactional(readOnly = true)
    @Operation(summary = "Consolidated SPT Tahunan Badan lampiran (all sections)",
            description = "Returns all lampiran data mapped to Coretax field numbers in a single response.")
    public ResponseEntity<AnalysisResponse<SptLampiranReport>> getSptLampiran(
            @RequestParam int year) {

        SptLampiranReport report = sptTahunanExportService.generateConsolidatedLampiran(year);
        auditAccess("spt-tahunan-lampiran", Map.of("year", String.valueOf(year)));

        return ResponseEntity.ok(new AnalysisResponse<>(
                "spt-tahunan-lampiran", LocalDateTime.now(),
                Map.of("year", String.valueOf(year)),
                report,
                Map.of(META_DESCRIPTION, "Consolidated SPT Tahunan Badan lampiran (Coretax-ready)",
                        META_CURRENCY, "IDR")));
    }

    // ==================== PARSING HELPERS ====================

    private LocalDate[] parseMonthRange(String startMonth, String endMonth) {
        try {
            YearMonth start = YearMonth.parse(startMonth);
            YearMonth end = YearMonth.parse(endMonth);
            return new LocalDate[]{start.atDay(1), end.atEndOfMonth()};
        } catch (DateTimeParseException _) {
            throw new IllegalArgumentException(
                    "Invalid month format. Expected yyyy-MM, got: " + startMonth + ", " + endMonth);
        }
    }

    private LocalDate parseDate(String date) {
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException _) {
            throw new IllegalArgumentException(
                    "Invalid date format. Expected yyyy-MM-dd, got: " + date);
        }
    }

    // ==================== MAPPING HELPERS ====================

    private PPNDetailData toPPNDetailData(PPNDetailReport report) {
        List<TaxDetailItem> keluaranItems = report.keluaranItems().stream()
                .map(this::toTaxDetailItem)
                .toList();
        List<TaxDetailItem> masukanItems = report.masukanItems().stream()
                .map(this::toTaxDetailItem)
                .toList();

        PPNTotals totals = new PPNTotals(
                report.totalDppKeluaran(), report.totalPpnKeluaran(),
                report.totalDppMasukan(), report.totalPpnMasukan());

        return new PPNDetailData(keluaranItems, masukanItems, totals);
    }

    private PPh23DetailData toPPh23DetailData(PPh23DetailReport report) {
        List<TaxDetailItem> items = report.items().stream()
                .map(this::toTaxDetailItem)
                .toList();

        PPh23Totals totals = new PPh23Totals(report.totalGross(), report.totalTax());

        return new PPh23DetailData(items, totals);
    }

    private RekonsiliasiFiskalData toRekonsiliasiFiskalData(RekonsiliasiFiskalReport report) {
        List<FiscalAdjustmentItem> adjustmentItems = report.adjustments().stream()
                .map(a -> new FiscalAdjustmentItem(
                        a.getDescription(),
                        a.getAdjustmentCategory().name(),
                        a.getAdjustmentDirection().name(),
                        a.getAmount()))
                .toList();

        PPhBadanCalculation calc = report.pphBadan();
        PPhBadanData pphBadan = new PPhBadanData(
                calc.pkp(), calc.totalRevenue(), calc.pphTerutang(),
                calc.calculationMethod(),
                calc.kreditPajakPPh23(), calc.kreditPajakPPh25(),
                calc.totalKreditPajak(), calc.pph29());

        return new RekonsiliasiFiskalData(
                report.year(),
                report.commercialNetIncome(),
                report.totalPositiveAdjustment(),
                report.totalNegativeAdjustment(),
                report.netAdjustment(),
                adjustmentItems,
                report.pkp(),
                pphBadan);
    }

    private TaxDetailItem toTaxDetailItem(TaxTransactionDetail d) {
        return new TaxDetailItem(
                d.getTransaction().getTransactionNumber(),
                d.getTransaction().getTransactionDate(),
                d.getCounterpartyName(),
                d.getCounterpartyNpwp(),
                d.getTaxType().name(),
                d.getFakturNumber(),
                d.getTransactionCode(),
                d.getDpp(),
                d.getPpn(),
                d.getBupotNumber(),
                d.getTaxObjectCode(),
                d.getGrossAmount(),
                d.getTaxRate(),
                d.getTaxAmount());
    }

    // ==================== AUDIT ====================

    private void auditAccess(String endpoint, Map<String, String> params) {
        securityAuditService.logAsync(AuditEventType.API_CALL,
                "Tax export API: " + endpoint + " " + params);
    }

    // ==================== DTOs ====================

    public record TaxDetailItem(
            String transactionNumber,
            LocalDate transactionDate,
            String counterpartyName,
            String counterpartyNpwp,
            String taxType,
            String fakturNumber,
            String transactionCode,
            BigDecimal dpp,
            BigDecimal ppn,
            String bupotNumber,
            String taxObjectCode,
            BigDecimal grossAmount,
            BigDecimal taxRate,
            BigDecimal taxAmount
    ) {}

    public record PPNTotals(
            BigDecimal totalDppKeluaran,
            BigDecimal totalPpnKeluaran,
            BigDecimal totalDppMasukan,
            BigDecimal totalPpnMasukan
    ) {}

    public record PPNDetailData(
            List<TaxDetailItem> keluaranItems,
            List<TaxDetailItem> masukanItems,
            PPNTotals totals
    ) {}

    public record PPh23Totals(
            BigDecimal totalGross,
            BigDecimal totalTax
    ) {}

    public record PPh23DetailData(
            List<TaxDetailItem> items,
            PPh23Totals totals
    ) {}

    public record FiscalAdjustmentItem(
            String description,
            String category,
            String direction,
            BigDecimal amount
    ) {}

    public record RekonsiliasiFiskalData(
            int year,
            BigDecimal commercialNetIncome,
            BigDecimal totalPositiveAdjustment,
            BigDecimal totalNegativeAdjustment,
            BigDecimal netAdjustment,
            List<FiscalAdjustmentItem> adjustments,
            BigDecimal pkp,
            PPhBadanData pphBadan
    ) {}

    public record PPhBadanData(
            BigDecimal pkp,
            BigDecimal totalRevenue,
            BigDecimal pphTerutang,
            String calculationMethod,
            BigDecimal kreditPajakPPh23,
            BigDecimal kreditPajakPPh25,
            BigDecimal totalKreditPajak,
            BigDecimal pph29
    ) {}
}
