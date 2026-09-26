package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for DataExportService.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("DataExportService Integration Tests")
class DataExportServiceTest {

    @Autowired
    private DataExportService dataExportService;

    @Nested
    @DisplayName("Export Statistics Operations")
    class ExportStatisticsTests {

        @Test
        @DisplayName("Should get export statistics")
        void shouldGetExportStatistics() {
            DataExportService.ExportStatistics stats = dataExportService.getExportStatistics();

            assertThat(stats).isNotNull();
            assertThat(stats.accountCount()).isGreaterThanOrEqualTo(0);
            assertThat(stats.journalEntryCount()).isGreaterThanOrEqualTo(0);
            assertThat(stats.transactionCount()).isGreaterThanOrEqualTo(0);
            assertThat(stats.clientCount()).isGreaterThanOrEqualTo(0);
            assertThat(stats.projectCount()).isGreaterThanOrEqualTo(0);
            assertThat(stats.invoiceCount()).isGreaterThanOrEqualTo(0);
            assertThat(stats.employeeCount()).isGreaterThanOrEqualTo(0);
            assertThat(stats.payrollRunCount()).isGreaterThanOrEqualTo(0);
            assertThat(stats.documentCount()).isGreaterThanOrEqualTo(0);
            assertThat(stats.auditLogCount()).isGreaterThanOrEqualTo(0);
            assertThat(stats.templateCount()).isGreaterThanOrEqualTo(0);
            assertThat(stats.userCount()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("Should calculate total records")
        void shouldCalculateTotalRecords() {
            DataExportService.ExportStatistics stats = dataExportService.getExportStatistics();

            long total = stats.totalRecords();
            long expected = stats.accountCount() + stats.journalEntryCount() + stats.transactionCount() +
                    stats.clientCount() + stats.projectCount() + stats.invoiceCount() +
                    stats.employeeCount() + stats.payrollRunCount() + stats.documentCount() +
                    stats.auditLogCount() + stats.templateCount() + stats.userCount();

            assertThat(total).isEqualTo(expected);
        }

        @Test
        @DisplayName("Should have non-zero account count from test data")
        void shouldHaveNonZeroAccountCountFromTestData() {
            DataExportService.ExportStatistics stats = dataExportService.getExportStatistics();
            // Test data should have some accounts
            assertThat(stats.accountCount()).isGreaterThan(0);
        }

        @Test
        @DisplayName("Should have non-zero template count from test data")
        void shouldHaveNonZeroTemplateCountFromTestData() {
            DataExportService.ExportStatistics stats = dataExportService.getExportStatistics();
            // Test data should have some templates
            assertThat(stats.templateCount()).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("Export All Data Operations")
    class ExportAllDataTests {

        @Test
        @DisplayName("Should export all data as ZIP")
        void shouldExportAllDataAsZip() throws IOException {
            byte[] zipData = dataExportService.exportAllData();

            assertThat(zipData).isNotNull();
            assertThat(zipData).hasSizeGreaterThan(0);
        }

        @Test
        @DisplayName("Should contain manifest file")
        void shouldContainManifestFile() throws IOException {
            byte[] zipData = dataExportService.exportAllData();
            Set<String> fileNames = getZipFileNames(zipData);

            assertThat(fileNames).contains("MANIFEST.md");
        }

        @Test
        @DisplayName("Should contain company config CSV")
        void shouldContainCompanyConfigCsv() throws IOException {
            byte[] zipData = dataExportService.exportAllData();
            Set<String> fileNames = getZipFileNames(zipData);

            assertThat(fileNames).contains("01_company_config.csv");
        }

        @Test
        @DisplayName("Should contain chart of accounts CSV")
        void shouldContainChartOfAccountsCsv() throws IOException {
            byte[] zipData = dataExportService.exportAllData();
            Set<String> fileNames = getZipFileNames(zipData);

            assertThat(fileNames).contains("02_chart_of_accounts.csv");
        }

        @Test
        @DisplayName("Should contain salary components CSV")
        void shouldContainSalaryComponentsCsv() throws IOException {
            byte[] zipData = dataExportService.exportAllData();
            Set<String> fileNames = getZipFileNames(zipData);

            assertThat(fileNames).contains("03_salary_components.csv");
        }

        @Test
        @DisplayName("Should contain journal templates CSV")
        void shouldContainJournalTemplatesCsv() throws IOException {
            byte[] zipData = dataExportService.exportAllData();
            Set<String> fileNames = getZipFileNames(zipData);

            assertThat(fileNames).contains("04_journal_templates.csv");
        }

        @Test
        @DisplayName("Should contain clients CSV")
        void shouldContainClientsCsv() throws IOException {
            byte[] zipData = dataExportService.exportAllData();
            Set<String> fileNames = getZipFileNames(zipData);

            assertThat(fileNames).contains("07_clients.csv");
        }

        @Test
        @DisplayName("Should contain projects CSV")
        void shouldContainProjectsCsv() throws IOException {
            byte[] zipData = dataExportService.exportAllData();
            Set<String> fileNames = getZipFileNames(zipData);

            assertThat(fileNames).contains("08_projects.csv");
        }

        @Test
        @DisplayName("Should contain employees CSV")
        void shouldContainEmployeesCsv() throws IOException {
            byte[] zipData = dataExportService.exportAllData();
            Set<String> fileNames = getZipFileNames(zipData);

            assertThat(fileNames).contains("15_employees.csv");
        }

        @Test
        @DisplayName("Should contain transactions CSV")
        void shouldContainTransactionsCsv() throws IOException {
            byte[] zipData = dataExportService.exportAllData();
            Set<String> fileNames = getZipFileNames(zipData);

            assertThat(fileNames).contains("18_transactions.csv");
        }

        @Test
        @DisplayName("Should contain journal entries CSV")
        void shouldContainJournalEntriesCsv() throws IOException {
            byte[] zipData = dataExportService.exportAllData();
            Set<String> fileNames = getZipFileNames(zipData);

            assertThat(fileNames).contains("20_journal_entries.csv");
        }

        @Test
        @DisplayName("Should contain users CSV")
        void shouldContainUsersCsv() throws IOException {
            byte[] zipData = dataExportService.exportAllData();
            Set<String> fileNames = getZipFileNames(zipData);

            assertThat(fileNames).contains("28_users.csv");
        }

        @Test
        @DisplayName("Should contain audit logs CSV")
        void shouldContainAuditLogsCsv() throws IOException {
            byte[] zipData = dataExportService.exportAllData();
            Set<String> fileNames = getZipFileNames(zipData);

            assertThat(fileNames).contains("32_audit_logs.csv");
        }

        @Test
        @DisplayName("Should contain document index CSV")
        void shouldContainDocumentIndexCsv() throws IOException {
            byte[] zipData = dataExportService.exportAllData();
            Set<String> fileNames = getZipFileNames(zipData);

            assertThat(fileNames).contains("documents/index.csv");
        }

        @Test
        @DisplayName("Should contain all expected CSV files")
        void shouldContainAllExpectedCsvFiles() throws IOException {
            byte[] zipData = dataExportService.exportAllData();
            Set<String> fileNames = getZipFileNames(zipData);

            // All numbered CSV files should be present
            assertThat(fileNames).contains(
                    "01_company_config.csv",
                    "02_chart_of_accounts.csv",
                    "03_salary_components.csv",
                    "04_journal_templates.csv",
                    "05_journal_template_lines.csv",
                    "06_journal_template_tags.csv",
                    "07_clients.csv",
                    "08_projects.csv",
                    "09_project_milestones.csv",
                    "10_project_payment_terms.csv",
                    "11_fiscal_periods.csv",
                    "12_tax_deadlines.csv",
                    "13_company_bank_accounts.csv",
                    "14_merchant_mappings.csv",
                    "15_employees.csv",
                    "16_employee_salary_components.csv",
                    "17_invoices.csv",
                    "18_transactions.csv",
                    "19_transaction_account_mappings.csv",
                    "19a_transaction_variables.csv",
                    "20_journal_entries.csv"
            );
        }

        @Test
        @DisplayName("Should produce valid ZIP format")
        void shouldProduceValidZipFormat() throws IOException {
            byte[] zipData = dataExportService.exportAllData();

            // Should not throw exception when reading as ZIP
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData))) {
                ZipEntry entry;
                int entryCount = 0;
                while ((entry = zis.getNextEntry()) != null) {
                    assertThat(entry.getName()).isNotEmpty();
                    entryCount++;
                    zis.closeEntry();
                }
                assertThat(entryCount).isGreaterThan(0);
            }
        }
    }

    @Nested
    @DisplayName("Export CSV Content Verification")
    class ExportCsvContentTests {

        @Test
        @DisplayName("Should contain payroll runs CSV")
        void shouldContainPayrollRunsCsv() throws IOException {
            byte[] zipData = dataExportService.exportAllData();
            Set<String> fileNames = getZipFileNames(zipData);

            assertThat(fileNames).contains("21_payroll_runs.csv");
        }

        @Test
        @DisplayName("Should contain payroll details CSV")
        void shouldContainPayrollDetailsCsv() throws IOException {
            byte[] zipData = dataExportService.exportAllData();
            Set<String> fileNames = getZipFileNames(zipData);

            assertThat(fileNames).contains("22_payroll_details.csv");
        }

        @Test
        @DisplayName("Should contain invoices CSV")
        void shouldContainInvoicesCsv() throws IOException {
            byte[] zipData = dataExportService.exportAllData();
            Set<String> fileNames = getZipFileNames(zipData);

            assertThat(fileNames).contains("17_invoices.csv");
        }

        @Test
        @DisplayName("Should contain fiscal periods CSV")
        void shouldContainFiscalPeriodsCsv() throws IOException {
            byte[] zipData = dataExportService.exportAllData();
            Set<String> fileNames = getZipFileNames(zipData);

            assertThat(fileNames).contains("11_fiscal_periods.csv");
        }

        @Test
        @DisplayName("Should contain tax deadlines CSV")
        void shouldContainTaxDeadlinesCsv() throws IOException {
            byte[] zipData = dataExportService.exportAllData();
            Set<String> fileNames = getZipFileNames(zipData);

            assertThat(fileNames).contains("12_tax_deadlines.csv");
        }

        @Test
        @DisplayName("Should contain company bank accounts CSV")
        void shouldContainCompanyBankAccountsCsv() throws IOException {
            byte[] zipData = dataExportService.exportAllData();
            Set<String> fileNames = getZipFileNames(zipData);

            assertThat(fileNames).contains("13_company_bank_accounts.csv");
        }

        @Test
        @DisplayName("Should contain merchant mappings CSV")
        void shouldContainMerchantMappingsCsv() throws IOException {
            byte[] zipData = dataExportService.exportAllData();
            Set<String> fileNames = getZipFileNames(zipData);

            assertThat(fileNames).contains("14_merchant_mappings.csv");
        }

        @Test
        @DisplayName("Should contain tax transaction details CSV")
        void shouldContainTaxTransactionDetailsCsv() throws IOException {
            byte[] zipData = dataExportService.exportAllData();
            Set<String> fileNames = getZipFileNames(zipData);

            assertThat(fileNames).contains("25_tax_transaction_details.csv");
        }

        @Test
        @DisplayName("Should contain draft transactions CSV")
        void shouldContainDraftTransactionsCsv() throws IOException {
            byte[] zipData = dataExportService.exportAllData();
            Set<String> fileNames = getZipFileNames(zipData);

            assertThat(fileNames).contains("27_draft_transactions.csv");
        }

        @Test
        @DisplayName("Should have CSV content in chart of accounts file")
        void shouldHaveCsvContentInChartOfAccounts() throws IOException {
            byte[] zipData = dataExportService.exportAllData();

            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if ("02_chart_of_accounts.csv".equals(entry.getName())) {
                        byte[] content = zis.readAllBytes();
                        String csv = new String(content);
                        // CSV should have a header line
                        assertThat(csv).contains("account_code");
                        assertThat(csv).contains("account_name");
                        zis.closeEntry();
                        return;
                    }
                    zis.closeEntry();
                }
            }
        }

        @Test
        @DisplayName("Should have MANIFEST.md with export contents")
        void shouldHaveManifestWithContents() throws IOException {
            byte[] zipData = dataExportService.exportAllData();

            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if ("MANIFEST.md".equals(entry.getName())) {
                        byte[] content = zis.readAllBytes();
                        String manifest = new String(content);
                        assertThat(manifest).contains("Export Contents");
                        assertThat(manifest).contains("Chart of Accounts");
                        assertThat(manifest).contains("records");
                        zis.closeEntry();
                        return;
                    }
                    zis.closeEntry();
                }
            }
        }

        @Test
        @DisplayName("Should contain all additional CSV files")
        void shouldContainAllAdditionalCsvFiles() throws IOException {
            byte[] zipData = dataExportService.exportAllData();
            Set<String> fileNames = getZipFileNames(zipData);

            assertThat(fileNames).contains(
                    "16_employee_salary_components.csv",
                    "23_amortization_schedules.csv",
                    "24_amortization_entries.csv",
                    "26_tax_deadline_completions.csv",
                    "29_user_roles.csv",
                    "30_user_template_preferences.csv",
                    "31_telegram_user_links.csv",
                    "33_transaction_sequences.csv",
                    "34_asset_categories.csv"
            );
        }
    }

    private Set<String> getZipFileNames(byte[] zipData) throws IOException {
        Set<String> names = new HashSet<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                names.add(entry.getName());
                zis.closeEntry();
            }
        }
        return names;
    }

    @Test
    @DisplayName("Industry seed 07_clients.csv uses the column order of the export and of DataImportService.importClients")
    void industrySeedClientHeadersMatchImportFormat() throws IOException {
        String importOrder = "code,name,contact_person,email,phone,address,npwp,nik,nitku,active,created_at";
        String exportHeader = null;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(dataExportService.exportAllData()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("07_clients.csv".equals(entry.getName())) {
                    exportHeader = new String(zis.readAllBytes(), StandardCharsets.UTF_8).lines().findFirst().orElseThrow();
                }
                zis.closeEntry();
            }
        }
        assertThat(exportHeader).isEqualTo(importOrder);

        List<Path> seedFiles;
        try (Stream<Path> files = Files.list(Path.of("industry-seed"))) {
            seedFiles = files.map(pack -> pack.resolve("seed-data/07_clients.csv")).filter(Files::exists).sorted().toList();
        }
        assertThat(seedFiles).extracting(Path::toString).containsExactly(
                "industry-seed/it-service/seed-data/07_clients.csv",
                "industry-seed/online-seller/seed-data/07_clients.csv");
        for (Path csv : seedFiles) {
            assertThat(Files.readAllLines(csv, StandardCharsets.UTF_8).getFirst()).as(csv.toString()).isEqualTo(importOrder);
        }
    }
}
