package com.artivisi.accountingfinance.functional.bankrecon;

import com.artivisi.accountingfinance.entity.BankStatement;
import com.artivisi.accountingfinance.entity.BankStatementParserConfig;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.CompanyBankAccount;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.CompanyBankAccountRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.service.BankStatementImportService;
import com.artivisi.accountingfinance.service.BankStatementParserConfigService;
import com.artivisi.accountingfinance.service.DataImportService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@TestConfiguration
@Profile("functional")
@RequiredArgsConstructor
@Slf4j
public class BankReconTestDataInitializer {

    private final DataImportService dataImportService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ChartOfAccountRepository chartOfAccountRepository;
    private final CompanyBankAccountRepository bankAccountRepository;
    private final BankStatementParserConfigService parserConfigService;
    private final BankStatementImportService importService;

    @PostConstruct
    public void importBankReconTestData() {
        try {
            // Step 1: Import IT service industry seed pack (COA, Templates, etc.)
            log.info("Importing IT Service industry seed data for bank reconciliation tests...");
            byte[] seedZip = createZipFromDirectory("industry-seed/it-service/seed-data");
            DataImportService.ImportResult seedResult = dataImportService.importAllData(seedZip);
            log.info("IT Service seed imported: {} records in {}ms",
                seedResult.totalRecords(), seedResult.durationMs());

            // Step 2: Import test master data (Company Config, Clients, Projects, Fiscal Periods, Employees, Payroll)
            log.info("Importing bank reconciliation test master data...");
            byte[] testDataZip = createZipFromTestData("src/test/resources/testdata/service");
            DataImportService.ImportResult testResult = dataImportService.importAllData(testDataZip);
            log.info("Bank recon test master data imported: {} records in {}ms",
                testResult.totalRecords(), testResult.durationMs());

            // Step 3: Create test users
            createTestUsers();

            // Step 4: Create bank account linked to GL account
            CompanyBankAccount bankAccount = createBankAccountWithGlLink();

            // Step 5: Import bank statement CSV programmatically
            importBankStatement(bankAccount);

        } catch (Exception e) {
            log.error("Failed to import bank reconciliation test data", e);
            throw new RuntimeException("Bank reconciliation test data initialization failed", e);
        }
    }

    private void createTestUsers() {
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

    private CompanyBankAccount createBankAccountWithGlLink() {
        // Find GL account 1.1.02 (Bank BCA) from IT service seed
        ChartOfAccount bankBcaAccount = chartOfAccountRepository.findByAccountCode("1.1.02")
                .orElseThrow(() -> new RuntimeException("GL account 1.1.02 (Bank BCA) not found in seed data"));

        // Create bank account linked to GL
        if (bankAccountRepository.findByActiveTrueOrderByBankNameAsc().isEmpty()) {
            CompanyBankAccount bankAccount = new CompanyBankAccount();
            bankAccount.setBankName("BCA");
            bankAccount.setBankBranch("KCU Sudirman");
            bankAccount.setAccountNumber("1234567890");
            bankAccount.setAccountName("PT ArtiVisi Intermedia");
            bankAccount.setCurrencyCode("IDR");
            bankAccount.setIsDefault(true);
            bankAccount.setActive(true);
            bankAccount.setGlAccount(bankBcaAccount);
            bankAccount = bankAccountRepository.save(bankAccount);
            log.info("Created bank account BCA linked to GL 1.1.02");
            return bankAccount;
        }
        return bankAccountRepository.findByActiveTrueOrderByBankNameAsc().getFirst();
    }

    private void importBankStatement(CompanyBankAccount bankAccount) throws IOException {
        // Find BCA parser config (seeded by V007)
        BankStatementParserConfig bcaConfig = parserConfigService.findActive().stream()
                .filter(c -> "BCA".equals(c.getBankType().name()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("BCA parser config not found"));

        // Load the test CSV file
        Path csvPath = Paths.get("src/test/resources/testdata/bank-reconciliation/bca-statement-202401.csv").toAbsolutePath();
        byte[] csvContent = Files.readAllBytes(csvPath);
        MockMultipartFile csvFile = new MockMultipartFile(
                "file", "bca-statement-202401.csv", "text/csv", csvContent);

        // Import the statement
        var importParams = new BankStatementImportService.BankStatementImportParams(
                bankAccount.getId(),
                bcaConfig.getId(),
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 31),
                new BigDecimal("500000000"),
                new BigDecimal("687305000"),
                csvFile,
                "admin");
        BankStatement statement = importService.importStatement(importParams);

        log.info("Imported bank statement with {} items, id: {}", statement.getTotalItems(), statement.getId());
    }

    private byte[] createZipFromTestData(String testDataDir) throws IOException {
        Path testDir = Paths.get(testDataDir).toAbsolutePath();
        if (!Files.exists(testDir)) {
            throw new IOException("Test data directory not found: " + testDir);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
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

    private void addTestFileToZip(ZipOutputStream zos, Path testDir, String sourceFile, String zipEntry)
            throws IOException {
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
