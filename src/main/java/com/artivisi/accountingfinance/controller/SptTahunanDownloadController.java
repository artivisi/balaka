package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.enums.AuditEventType;
import com.artivisi.accountingfinance.security.HttpSecurityUtil;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.CoretaxExportService;
import com.artivisi.accountingfinance.service.ReportExportService;
import com.artivisi.accountingfinance.service.SecurityAuditService;
import com.artivisi.accountingfinance.service.SptTahunanExportService;
import com.artivisi.accountingfinance.service.TaxReportDetailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;

/**
 * SPT Tahunan Badan attachment downloads for browser sessions (the "Download Lampiran SPT"
 * links on the SPT checklist). Same workbooks as the bearer-only /api/tax-export endpoints.
 */
@Controller
@RequestMapping("/reports/spt-tahunan")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permission.TAX_EXPORT + "')")
public class SptTahunanDownloadController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final SptTahunanExportService sptTahunanExportService;
    private final CoretaxExportService coretaxExportService;
    private final TaxReportDetailService taxReportDetailService;
    private final ReportExportService reportExportService;
    private final SecurityAuditService securityAuditService;

    @GetMapping("/l1")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> l1(@RequestParam int year) throws IOException {
        return xlsx("spt-l1-rekonsiliasi-fiskal-" + year,
                coretaxExportService.exportL1ToExcel(sptTahunanExportService.generateL1(year)));
    }

    @GetMapping("/l4")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> l4(@RequestParam int year) throws IOException {
        return xlsx("spt-l4-penghasilan-final-" + year,
                coretaxExportService.exportL4ToExcel(sptTahunanExportService.generateL4(year)));
    }

    @GetMapping("/l9")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> l9(@RequestParam int year) throws IOException {
        return xlsx("spt-l9-penyusutan-" + year,
                coretaxExportService.exportL9ToExcel(sptTahunanExportService.generateL9(year)));
    }

    @GetMapping("/transkrip-8a")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> transkrip8A(@RequestParam int year) throws IOException {
        return xlsx("spt-transkrip-8a-" + year,
                coretaxExportService.exportTranskrip8AToExcel(sptTahunanExportService.generateTranskrip8A(year)));
    }

    @GetMapping("/ebupot-pph21")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> ebupotPph21(@RequestParam int year) throws IOException {
        return xlsx("ebupot-pph21-1721a1-" + year,
                coretaxExportService.exportBpa1ToExcel(sptTahunanExportService.generateBpa1(year)));
    }

    @GetMapping("/rekonsiliasi-fiskal")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> rekonsiliasiFiskal(@RequestParam int year) {
        return xlsx("rekonsiliasi-fiskal-" + year,
                reportExportService.exportRekonsiliasiFiskalToExcel(taxReportDetailService.generateRekonsiliasiFiskal(year)));
    }

    private ResponseEntity<byte[]> xlsx(String name, byte[] content) {
        String filename = name + ".xlsx";
        securityAuditService.logAsync(AuditEventType.DATA_EXPORT, "SPT Tahunan download: " + filename);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, HttpSecurityUtil.attachmentContentDisposition(filename))
                .contentType(XLSX)
                .body(content);
    }
}
