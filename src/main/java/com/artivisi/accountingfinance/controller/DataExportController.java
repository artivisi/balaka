package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.enums.AuditEventType;
import com.artivisi.accountingfinance.exception.DataExportException;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.DataExportService;
import com.artivisi.accountingfinance.service.SecurityAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static com.artivisi.accountingfinance.controller.ViewConstants.*;

@Controller
@RequestMapping("/settings/export")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAuthority('" + Permission.DATA_IMPORT + "')")
public class DataExportController {

    private final DataExportService dataExportService;
    private final SecurityAuditService securityAuditService;

    @GetMapping
    public String exportPage(Model model) {
        DataExportService.ExportStatistics statistics = dataExportService.getExportStatistics();
        model.addAttribute("statistics", statistics);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_SETTINGS);
        return "settings/export";
    }

    @PostMapping("/download")
    public ResponseEntity<byte[]> downloadExport() {
        try {
            byte[] zipData = dataExportService.exportAllData();

            String filename = "export-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".zip";
            securityAuditService.log(AuditEventType.DATA_EXPORT,
                    "Full data export: " + filename + " (" + zipData.length + " bytes)");

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(zipData.length)
                    .body(zipData);

        } catch (IOException e) {
            log.error("Failed to export data", e);
            throw new DataExportException("Gagal mengekspor data: " + e.getMessage(), e);
        }
    }
}
