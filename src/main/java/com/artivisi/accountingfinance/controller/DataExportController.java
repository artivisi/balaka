package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.service.DataExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Controller
@RequestMapping("/settings/export")
@RequiredArgsConstructor
@Slf4j
public class DataExportController {

    private final DataExportService dataExportService;

    @GetMapping
    public String exportPage(Model model) {
        DataExportService.ExportStatistics statistics = dataExportService.getExportStatistics();
        model.addAttribute("statistics", statistics);
        model.addAttribute("currentPage", "settings");
        return "settings/export";
    }

    @PostMapping("/download")
    public ResponseEntity<byte[]> downloadExport(RedirectAttributes redirectAttributes) {
        try {
            byte[] zipData = dataExportService.exportAllData();

            String filename = "export-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".zip";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(zipData.length)
                    .body(zipData);

        } catch (IOException e) {
            log.error("Failed to export data", e);
            throw new RuntimeException("Gagal mengekspor data: " + e.getMessage());
        }
    }
}
