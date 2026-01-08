package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.service.CoretaxExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

@Controller
@RequestMapping("/reports/tax-export")
@RequiredArgsConstructor
@Slf4j
@org.springframework.security.access.prepost.PreAuthorize("hasAuthority('" + com.artivisi.accountingfinance.security.Permission.TAX_EXPORT + "')")
public class TaxExportController {

    private final CoretaxExportService coretaxExportService;

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter FILE_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");
    private static final String ATTACHMENT_FILENAME_PREFIX = "attachment; filename=\"";
    private static final String CONTENT_TYPE_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @GetMapping
    public String showExportPage(
            @RequestParam(required = false) String startMonth,
            @RequestParam(required = false) String endMonth,
            Model model) {

        // Default to current month if not specified
        YearMonth currentMonth = YearMonth.now();
        YearMonth start = startMonth != null ? YearMonth.parse(startMonth) : currentMonth;
        YearMonth end = endMonth != null ? YearMonth.parse(endMonth) : currentMonth;

        LocalDate startDate = start.atDay(1);
        LocalDate endDate = end.atEndOfMonth();

        // Get export statistics
        CoretaxExportService.ExportStatistics stats = coretaxExportService.getExportStatistics(startDate, endDate);

        model.addAttribute("startMonth", start.format(MONTH_FORMATTER));
        model.addAttribute("endMonth", end.format(MONTH_FORMATTER));
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("stats", stats);

        // Generate list of available months (last 24 months)
        model.addAttribute("availableMonths", generateAvailableMonths());

        return "reports/tax-export";
    }

    @GetMapping("/efaktur-keluaran")
    public ResponseEntity<byte[]> exportEFakturKeluaran(
            @RequestParam String startMonth,
            @RequestParam String endMonth) throws IOException {

        YearMonth start = YearMonth.parse(startMonth);
        YearMonth end = YearMonth.parse(endMonth);
        LocalDate startDate = start.atDay(1);
        LocalDate endDate = end.atEndOfMonth();

        byte[] excelData = coretaxExportService.exportEFakturKeluaran(startDate, endDate);

        String filename = String.format("efaktur-keluaran_%s_%s.xlsx",
                start.format(FILE_MONTH_FORMAT),
                end.format(FILE_MONTH_FORMAT));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ATTACHMENT_FILENAME_PREFIX + filename + "\"")
                .contentType(MediaType.parseMediaType(CONTENT_TYPE_XLSX))
                .body(excelData);
    }

    @GetMapping("/efaktur-masukan")
    public ResponseEntity<byte[]> exportEFakturMasukan(
            @RequestParam String startMonth,
            @RequestParam String endMonth) throws IOException {

        YearMonth start = YearMonth.parse(startMonth);
        YearMonth end = YearMonth.parse(endMonth);
        LocalDate startDate = start.atDay(1);
        LocalDate endDate = end.atEndOfMonth();

        byte[] excelData = coretaxExportService.exportEFakturMasukan(startDate, endDate);

        String filename = String.format("efaktur-masukan_%s_%s.xlsx",
                start.format(FILE_MONTH_FORMAT),
                end.format(FILE_MONTH_FORMAT));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ATTACHMENT_FILENAME_PREFIX + filename + "\"")
                .contentType(MediaType.parseMediaType(CONTENT_TYPE_XLSX))
                .body(excelData);
    }

    @GetMapping("/bupot-unifikasi")
    public ResponseEntity<byte[]> exportBupotUnifikasi(
            @RequestParam String startMonth,
            @RequestParam String endMonth) throws IOException {

        YearMonth start = YearMonth.parse(startMonth);
        YearMonth end = YearMonth.parse(endMonth);
        LocalDate startDate = start.atDay(1);
        LocalDate endDate = end.atEndOfMonth();

        byte[] excelData = coretaxExportService.exportBupotUnifikasi(startDate, endDate);

        String filename = String.format("bupot-unifikasi_%s_%s.xlsx",
                start.format(FILE_MONTH_FORMAT),
                end.format(FILE_MONTH_FORMAT));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ATTACHMENT_FILENAME_PREFIX + filename + "\"")
                .contentType(MediaType.parseMediaType(CONTENT_TYPE_XLSX))
                .body(excelData);
    }

    private java.util.List<YearMonth> generateAvailableMonths() {
        java.util.List<YearMonth> months = new java.util.ArrayList<>();
        YearMonth current = YearMonth.now();

        for (int i = 0; i < 24; i++) {
            months.add(current.minusMonths(i));
        }

        return months;
    }
}
