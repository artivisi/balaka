package com.artivisi.accountingfinance.controller.api;

import com.artivisi.accountingfinance.enums.AuditEventType;
import com.artivisi.accountingfinance.service.DataImportService;
import com.artivisi.accountingfinance.service.SecurityAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/data-import")
@PreAuthorize("hasAuthority('SCOPE_data:import')")
@RequiredArgsConstructor
@Slf4j
public class DataImportApiController {

    private final DataImportService dataImportService;
    private final SecurityAuditService securityAuditService;

    @PostMapping
    public ResponseEntity<ImportResultDto> importData(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".zip")) {
            throw new IllegalArgumentException("Only ZIP files are supported");
        }

        DataImportService.ImportResult result = dataImportService.importAllData(file.getBytes());

        securityAuditService.log(AuditEventType.API_CALL,
                "API: Data import completed: " + result.totalRecords() + " records, "
                        + result.documentCount() + " documents in " + result.durationMs() + "ms");

        log.info("API data import completed: {} records, {} documents in {}ms",
                result.totalRecords(), result.documentCount(), result.durationMs());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ImportResultDto(result.totalRecords(), result.documentCount(), result.durationMs()));
    }

    record ImportResultDto(int totalRecords, int documentCount, long durationMs) {}
}
