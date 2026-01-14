package com.artivisi.accountingfinance.functional.service;

import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.service.DataImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Test configuration specific to IT Service industry tests.
 * Imports IT service seed data and test master data using DataImportService.
 *
 * This initializer is scoped to service industry tests only.
 * For other industries (online-seller, coffee-shop), create separate initializers.
 */
@TestConfiguration
@Profile("functional")
@RequiredArgsConstructor
@Slf4j
public class ServiceTestDataInitializer {

    private final DataImportService dataImportService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @PostConstruct
    public void importServiceTestData() {
        try {
            // Step 1: Import IT service industry seed pack (COA, Templates, Salary, Tax Deadlines, Asset Categories)
            log.info("Importing IT Service industry seed data...");
            byte[] seedZip = createZipFromDirectory("industry-seed/it-service/seed-data");
            DataImportService.ImportResult seedResult = dataImportService.importAllData(seedZip);
            log.info("IT Service seed imported: {} records in {}ms",
                seedResult.totalRecords(), seedResult.durationMs());

            // Step 2: Import service test master data (Company Config, Fiscal Periods, Clients, Projects, Employees)
            log.info("Importing service test master data...");
            byte[] testDataZip = createZipFromTestData("src/test/resources/testdata/service");
            DataImportService.ImportResult testResult = dataImportService.importAllData(testDataZip);
            log.info("Service test master data imported: {} records in {}ms",
                testResult.totalRecords(), testResult.durationMs());

            // Step 3: Create test users with known passwords
            createTestUsers();

        } catch (Exception e) {
            log.error("Failed to import IT service test data", e);
            throw new RuntimeException("IT service test data initialization failed", e);
        }
    }

    private void createTestUsers() {
        // Create staff user for testing user management
        if (userRepository.findByUsername("staff").isEmpty()) {
            User staff = new User();
            staff.setUsername("staff");
            staff.setPassword(passwordEncoder.encode("password"));
            staff.setFullName("Staff User");
            staff.setEmail("staff@example.com");
            staff.setActive(true);
            staff.addRole(Role.STAFF, "system");
            userRepository.save(staff);
            log.info("Created test user 'staff'");
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
            addTestFileToZip(zos, testDir, "projects.csv", "08_projects.csv");
            addTestFileToZip(zos, testDir, "fiscal-periods.csv", "11_fiscal_periods.csv");
            addTestFileToZip(zos, testDir, "employees.csv", "15_employees.csv");
            addTestFileToZip(zos, testDir, "21_payroll_runs.csv", "21_payroll_runs.csv");
            addTestFileToZip(zos, testDir, "22_payroll_details.csv", "22_payroll_details.csv");
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
