package com.artivisi.accountingfinance.functional.seller;

import com.artivisi.accountingfinance.service.DataImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Profile;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Test configuration specific to Online Seller industry tests.
 * Imports online seller seed data and test master data using DataImportService.
 * 
 * This initializer is scoped to seller industry tests only.
 */
@TestConfiguration
@Profile("functional")
@RequiredArgsConstructor
@Slf4j
public class SellerTestDataInitializer {

    private final DataImportService dataImportService;

    @PostConstruct
    public void importSellerTestData() {
        try {
            // Step 1: Import online seller industry seed pack (COA, Templates, etc.)
            log.info("Importing Online Seller industry seed data...");
            byte[] seedZip = createZipFromDirectory("industry-seed/online-seller/seed-data");
            DataImportService.ImportResult seedResult = dataImportService.importAllData(seedZip);
            log.info("Online Seller seed imported: {} records in {}ms", 
                seedResult.totalRecords(), seedResult.durationMs());
            
            // Step 2: Import seller test master data (Company Config, Fiscal Periods, Clients, etc.)
            log.info("Importing seller test master data...");
            byte[] testDataZip = createZipFromTestData("src/test/resources/testdata/seller");
            DataImportService.ImportResult testResult = dataImportService.importAllData(testDataZip);
            log.info("Seller test master data imported: {} records in {}ms", 
                testResult.totalRecords(), testResult.durationMs());
                
        } catch (Exception e) {
            log.error("Failed to import online seller test data", e);
            throw new RuntimeException("Online seller test data initialization failed", e);
        }
    }
    
    private byte[] createZipFromTestData(String testDataDir) throws IOException {
        Path testDir = Paths.get(testDataDir).toAbsolutePath();
        
        if (!Files.exists(testDir)) {
            throw new IOException("Test data directory not found: " + testDir);
        }
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Map test CSV files to expected import format
            addTestFileToZip(zos, testDir, "company-config.csv", "01_company_config.csv");
            addTestFileToZip(zos, testDir, "clients.csv", "07_clients.csv");
            addTestFileToZip(zos, testDir, "fiscal-periods.csv", "11_fiscal_periods.csv");
            addTestFileToZip(zos, testDir, "employees.csv", "15_employees.csv");
            addTestFileToZip(zos, testDir, "financial-transactions.csv", "18_transactions.csv");
            addTestFileToZip(zos, testDir, "financial-journal-entries.csv", "20_journal_entries.csv");
        }
        
        return baos.toByteArray();
    }
    
    private void addTestFileToZip(ZipOutputStream zos, Path testDir, String sourceFile, String zipEntry) throws IOException {
        Path filePath = testDir.resolve(sourceFile);
        if (!Files.exists(filePath)) {
            throw new IOException("Seller test data file not found: " + filePath);
        }
        zos.putNextEntry(new ZipEntry(zipEntry));
        Files.copy(filePath, zos);
        zos.closeEntry();
    }

    private byte[] createZipFromDirectory(String dirPath) throws IOException {
        Path seedDir = Paths.get(dirPath).toAbsolutePath();
        
        if (!Files.exists(seedDir)) {
            throw new IOException("Seed data directory not found: " + seedDir);
        }
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            Files.walk(seedDir)
                .filter(path -> !Files.isDirectory(path))
                .forEach(path -> {
                    try {
                        Path relativePath = seedDir.relativize(path);
                        ZipEntry entry = new ZipEntry(relativePath.toString().replace('\\', '/'));
                        zos.putNextEntry(entry);
                        Files.copy(path, zos);
                        zos.closeEntry();
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to zip file: " + path, e);
                    }
                });
        }
        
        return baos.toByteArray();
    }
}
