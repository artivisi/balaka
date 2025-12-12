package com.artivisi.accountingfinance.functional.manufacturing;

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
 * Test configuration specific to Coffee Shop (Manufacturing) industry tests.
 * Imports coffee shop seed data and test master data using DataImportService.
 *
 * This initializer is scoped to manufacturing industry tests only.
 */
@TestConfiguration
@Profile("functional")
@RequiredArgsConstructor
@Slf4j
public class CoffeeTestDataInitializer {

    private final DataImportService dataImportService;

    @PostConstruct
    public void importCoffeeTestData() {
        try {
            // Step 1: Import Coffee Shop industry seed pack (COA, Templates, Products, BOMs, Production Orders, Inventory)
            log.info("Importing Coffee Shop industry seed data...");
            byte[] seedZip = createZipFromDirectory("industry-seed/coffee-shop/seed-data");
            DataImportService.ImportResult seedResult = dataImportService.importAllData(seedZip);
            log.info("Coffee Shop seed imported: {} records in {}ms",
                seedResult.totalRecords(), seedResult.durationMs());

            // Step 2: Import coffee test master data (Company Config, Fiscal Periods, Employees)
            log.info("Importing coffee test master data...");
            byte[] testDataZip = createZipFromTestData("src/test/resources/testdata/coffee");
            DataImportService.ImportResult testResult = dataImportService.importAllData(testDataZip);
            log.info("Coffee test master data imported: {} records in {}ms",
                testResult.totalRecords(), testResult.durationMs());

        } catch (Exception e) {
            log.error("Failed to import coffee shop test data", e);
            throw new RuntimeException("Coffee shop test data initialization failed", e);
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
            addTestFileToZip(zos, testDir, "fiscal-periods.csv", "11_fiscal_periods.csv");
            addTestFileToZip(zos, testDir, "employees.csv", "15_employees.csv");
        }

        return baos.toByteArray();
    }

    private void addTestFileToZip(ZipOutputStream zos, Path testDir, String sourceFile, String zipEntry) throws IOException {
        Path filePath = testDir.resolve(sourceFile);
        if (Files.exists(filePath)) {
            ZipEntry entry = new ZipEntry(zipEntry);
            zos.putNextEntry(entry);
            Files.copy(filePath, zos);
            zos.closeEntry();
        }
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
