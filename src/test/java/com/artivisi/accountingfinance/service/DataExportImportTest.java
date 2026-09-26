package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.entity.*;
import com.artivisi.accountingfinance.enums.*;
import com.artivisi.accountingfinance.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for DataExportService and DataImportService.
 * Verifies export of company config including tax profile fields,
 * and import of CSV data from ZIP archives.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@DisplayName("Data Export and Import Service Tests")
class DataExportImportTest {

    @Autowired
    private DataExportService dataExportService;

    @Autowired
    private DataImportService dataImportService;

    @Autowired
    private CompanyConfigRepository companyConfigRepository;

    @Autowired
    private ChartOfAccountRepository chartOfAccountRepository;

    @Autowired
    private SalaryComponentRepository salaryComponentRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private FiscalPeriodRepository fiscalPeriodRepository;

    @Autowired
    private TaxDeadlineRepository taxDeadlineRepository;

    @Autowired
    private CompanyBankAccountRepository companyBankAccountRepository;

    @Autowired
    private JournalTemplateRepository journalTemplateRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionSequenceRepository transactionSequenceRepository;

    @Autowired
    private AssetCategoryRepository assetCategoryRepository;

    @Autowired
    private ProductCategoryRepository productCategoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private DraftTransactionRepository draftTransactionRepository;

    @Autowired
    private InventoryBalanceRepository inventoryBalanceRepository;

    @Test
    @DisplayName("Should export company config with tax profile fields")
    @Transactional
    void exportCompanyConfigWithTaxProfileFields() throws IOException {
        // Given: Save company config with tax profile fields (will be rolled back)
        CompanyConfig config = companyConfigRepository.findFirst().orElse(new CompanyConfig());
        config.setCompanyName("PT Test Company");
        config.setNpwp("01.234.567.8-901.234");
        config.setEstablishedDate(LocalDate.of(2008, 5, 15));
        config.setIsPkp(true);
        config.setPkpSince(LocalDate.of(2010, 1, 1));
        companyConfigRepository.save(config);
        companyConfigRepository.flush();

        // When: Export all data
        byte[] exportedData = dataExportService.exportAllData();

        // Then: Verify the ZIP contains company config with tax profile fields
        String companyConfigCsv = extractFileFromZip(exportedData, "01_company_config.csv");

        assertThat(companyConfigCsv)
            .as("CSV should include tax profile fields with correct values")
            .contains("established_date,is_pkp,pkp_since")
            .contains("2008-05-15")
            .contains("true")
            .contains("2010-01-01");
    }

    @Test
    @DisplayName("Should handle null tax profile fields in export")
    @Transactional
    void handleNullTaxProfileFieldsInExport() throws IOException {
        // Given: Company config without tax profile fields
        CompanyConfig config = companyConfigRepository.findFirst().orElse(new CompanyConfig());
        config.setCompanyName("PT No Tax Profile");
        config.setNpwp("03.456.789.0-123.456");
        config.setEstablishedDate(null);
        config.setIsPkp(null);
        config.setPkpSince(null);
        companyConfigRepository.save(config);
        companyConfigRepository.flush();

        // When: Export
        byte[] exportedData = dataExportService.exportAllData();
        String companyConfigCsv = extractFileFromZip(exportedData, "01_company_config.csv");

        // Then: Should have empty values for tax profile fields
        assertThat(companyConfigCsv)
            .as("CSV should include tax profile fields and company name")
            .contains("established_date,is_pkp,pkp_since")
            .contains("PT No Tax Profile");
    }

    @Test
    @DisplayName("Export CSV should have correct column order for tax profile fields")
    @Transactional
    void exportCsvShouldHaveCorrectColumnOrder() throws IOException {
        // Given: Company config with tax profile fields
        CompanyConfig config = companyConfigRepository.findFirst().orElse(new CompanyConfig());
        config.setCompanyName("PT Column Order Test");
        config.setEstablishedDate(LocalDate.of(2015, 6, 20));
        config.setIsPkp(false);
        companyConfigRepository.save(config);
        companyConfigRepository.flush();

        // When: Export
        byte[] exportedData = dataExportService.exportAllData();
        String companyConfigCsv = extractFileFromZip(exportedData, "01_company_config.csv");

        // Then: Header should have tax profile fields at the end
        String[] lines = companyConfigCsv.split("\n");
        assertThat(lines).hasSizeGreaterThanOrEqualTo(2);

        String header = lines[0];
        assertThat(header)
            .as("Header should end with industry and the posting-account columns")
            .endsWith("established_date,is_pkp,pkp_since,industry,"
                    + "id_receivable_account,id_payable_account,id_output_tax_account,id_input_tax_account");

        // Data line should have the values; posting accounts are exported as COA codes
        String dataLine = lines[1];
        assertThat(dataLine)
            .as("Data should contain established date and isPkp=false")
            .contains("2015-06-20")
            .contains("false");
        assertThat(dataLine).endsWith(","
                + codeOf(config.getReceivableAccount()) + "," + codeOf(config.getPayableAccount()) + ","
                + codeOf(config.getOutputTaxAccount()) + "," + codeOf(config.getInputTaxAccount()));
        assertThat(config.getReceivableAccount()).as("integration seed configures the receivable account").isNotNull();
    }

    @Nested
    @DisplayName("Import Round-Trip Tests")
    class ImportRoundTripTests {

        @Test
        @DisplayName("Should import exported data round-trip")
        @Transactional
        void shouldImportExportedDataRoundTrip() throws IOException {
            // Given: Export all existing data
            byte[] exportedData = dataExportService.exportAllData();

            // When: Import the exported data back
            DataImportService.ImportResult result = dataImportService.importAllData(exportedData);

            // Then: Should complete without errors
            assertThat(result).isNotNull();
            assertThat(result.totalRecords()).isGreaterThanOrEqualTo(0);
            assertThat(result.durationMs()).isGreaterThanOrEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Import Company Config Tests")
    class ImportCompanyConfigTests {

        @Test
        @DisplayName("Should import company config with all fields")
        @Transactional
        void shouldImportCompanyConfigWithAllFields() throws IOException {
            String csv = "company_name,company_address,company_phone,company_email,tax_id,npwp,nitku,fiscal_year_start_month,currency_code,signing_officer_name,signing_officer_title,company_logo_path,established_date,is_pkp,pkp_since,industry\n"
                    + "PT Import Test,Jl Test 123,021-1234567,test@example.com,12345,01.234.567.8-901.234,1234567890,1,IDR,John Doe,Director,,2010-05-15,true,2012-01-01,IT_SERVICE\n";

            byte[] zipData = buildZipWithFiles("01_company_config.csv", csv);
            DataImportService.ImportResult result = dataImportService.importAllData(zipData);

            assertThat(result.totalRecords()).isEqualTo(1);

            CompanyConfig config = companyConfigRepository.findFirst().orElse(null);
            assertThat(config).isNotNull();
            assertThat(config.getCompanyName()).isEqualTo("PT Import Test");
            assertThat(config.getNpwp()).isEqualTo("01.234.567.8-901.234");
            assertThat(config.getEstablishedDate()).isEqualTo(LocalDate.of(2010, 5, 15));
            assertThat(config.getIsPkp()).isTrue();
            assertThat(config.getPkpSince()).isEqualTo(LocalDate.of(2012, 1, 1));
            assertThat(config.getIndustry()).isEqualTo("IT_SERVICE");
            assertThat(config.getFiscalYearStartMonth()).isEqualTo(1);
            assertThat(config.getCurrencyCode()).isEqualTo("IDR");
        }

        @Test
        @DisplayName("Should import company config with optional fields empty")
        @Transactional
        void shouldImportCompanyConfigWithOptionalFieldsEmpty() throws IOException {
            String csv = "company_name,company_address,company_phone,company_email,tax_id,npwp,nitku,fiscal_year_start_month,currency_code,signing_officer_name,signing_officer_title,company_logo_path,established_date,is_pkp,pkp_since,industry\n"
                    + "PT Minimal,,,,,,,1,IDR,,,,,,\n";

            byte[] zipData = buildZipWithFiles("01_company_config.csv", csv);
            DataImportService.ImportResult result = dataImportService.importAllData(zipData);

            assertThat(result.totalRecords()).isEqualTo(1);

            CompanyConfig config = companyConfigRepository.findFirst().orElse(null);
            assertThat(config).isNotNull();
            assertThat(config.getCompanyName()).isEqualTo("PT Minimal");
            assertThat(config.getEstablishedDate()).isNull();
            assertThat(config.getPkpSince()).isNull();
        }
    }

    @Nested
    @DisplayName("Import Chart of Accounts Tests")
    class ImportChartOfAccountsTests {

        @Test
        @DisplayName("Should import chart of accounts with parent references")
        @Transactional
        void shouldImportChartOfAccountsWithParentReferences() throws IOException {
            String csv = "account_code,account_name,account_type,parent_code,normal_balance,active,is_permanent\n"
                    + "9000,Test Asset Parent,ASSET,,DEBIT,true,false\n"
                    + "9001,Test Asset Child,ASSET,9000,DEBIT,true,false\n";

            byte[] zipData = buildZipWithFiles("02_chart_of_accounts.csv", csv);
            DataImportService.ImportResult result = dataImportService.importAllData(zipData);

            assertThat(result.totalRecords()).isEqualTo(2);

            List<ChartOfAccount> accounts = chartOfAccountRepository.findAll();
            ChartOfAccount parent = accounts.stream()
                    .filter(a -> "9000".equals(a.getAccountCode()))
                    .findFirst().orElse(null);
            ChartOfAccount child = accounts.stream()
                    .filter(a -> "9001".equals(a.getAccountCode()))
                    .findFirst().orElse(null);

            assertThat(parent).isNotNull();
            assertThat(parent.getAccountName()).isEqualTo("Test Asset Parent");
            assertThat(child).isNotNull();
            assertThat(child.getParent()).isNotNull();
            assertThat(child.getParent().getAccountCode()).isEqualTo("9000");
        }

        @Test
        @DisplayName("Should import account with is_permanent field")
        @Transactional
        void shouldImportAccountWithIsPermanentField() throws IOException {
            String csv = "account_code,account_name,account_type,parent_code,normal_balance,active,is_permanent\n"
                    + "9100,Retained Earnings,EQUITY,,CREDIT,true,true\n";

            byte[] zipData = buildZipWithFiles("02_chart_of_accounts.csv", csv);
            dataImportService.importAllData(zipData);

            ChartOfAccount account = chartOfAccountRepository.findAll().stream()
                    .filter(a -> "9100".equals(a.getAccountCode()))
                    .findFirst().orElse(null);

            assertThat(account).isNotNull();
            assertThat(account.getPermanent()).isTrue();
        }
    }

    @Nested
    @DisplayName("Import Salary Components Tests")
    class ImportSalaryComponentsTests {

        @Test
        @DisplayName("Should import salary components")
        @Transactional
        void shouldImportSalaryComponents() throws IOException {
            String csv = "code,name,description,component_type,is_percentage,default_rate,default_amount,is_system,display_order,active,is_taxable,bpjs_category\n"
                    + "TEST-SC,Test Component,A test salary component,EARNING,false,,5000000,false,1,true,true,\n";

            byte[] zipData = buildZipWithFiles("03_salary_components.csv", csv);
            DataImportService.ImportResult result = dataImportService.importAllData(zipData);

            assertThat(result.totalRecords()).isEqualTo(1);

            SalaryComponent sc = salaryComponentRepository.findAll().stream()
                    .filter(s -> "TEST-SC".equals(s.getCode()))
                    .findFirst().orElse(null);
            assertThat(sc).isNotNull();
            assertThat(sc.getName()).isEqualTo("Test Component");
            assertThat(sc.getComponentType()).isEqualTo(SalaryComponentType.EARNING);
        }
    }

    @Nested
    @DisplayName("Import Clients Tests")
    class ImportClientsTests {

        @Test
        @DisplayName("Should import clients with all fields")
        @Transactional
        void shouldImportClientsWithAllFields() throws IOException {
            String csv = "code,name,contact_person,email,phone,address,npwp,nik,nitku,active,created_at\n"
                    + "CLI-TEST,Test Client,John Doe,john@test.com,021-555,Jl Test,01.234.567.8-901.234,1234567890123456,NITKU123,true,2025-01-01 00:00:00\n";

            byte[] zipData = buildZipWithFiles("07_clients.csv", csv);
            DataImportService.ImportResult result = dataImportService.importAllData(zipData);

            assertThat(result.totalRecords()).isEqualTo(1);

            Client client = clientRepository.findAll().stream()
                    .filter(c -> "CLI-TEST".equals(c.getCode()))
                    .findFirst().orElse(null);
            assertThat(client).isNotNull();
            assertThat(client.getName()).isEqualTo("Test Client");
            assertThat(client.getEmail()).isEqualTo("john@test.com");
            assertThat(client.getNpwp()).isEqualTo("01.234.567.8-901.234");
        }
    }

    @Nested
    @DisplayName("Import Projects Tests")
    class ImportProjectsTests {

        @Test
        @DisplayName("Should import projects with client reference")
        @Transactional
        void shouldImportProjectsWithClientReference() throws IOException {
            String clientCsv = "code,name,contact_person,email,phone,address,npwp,nik,nitku,active,created_at\n"
                    + "CLI-PROJ,Project Client,Contact,email@test.com,021,,,,,,\n";
            String projectCsv = "code,name,client_code,status,start_date,end_date,budget_amount,contract_value,description,created_at\n"
                    + "PRJ-TEST,Test Project,CLI-PROJ,ACTIVE,2025-01-01,2025-12-31,100000000,150000000,A test project,\n";

            byte[] zipData = buildZipWithMultipleFiles(
                    "07_clients.csv", clientCsv,
                    "08_projects.csv", projectCsv
            );
            DataImportService.ImportResult result = dataImportService.importAllData(zipData);

            assertThat(result.totalRecords()).isEqualTo(2);

            Project project = projectRepository.findAll().stream()
                    .filter(p -> "PRJ-TEST".equals(p.getCode()))
                    .findFirst().orElse(null);
            assertThat(project).isNotNull();
            assertThat(project.getName()).isEqualTo("Test Project");
            assertThat(project.getClient()).isNotNull();
            assertThat(project.getClient().getCode()).isEqualTo("CLI-PROJ");
        }
    }

    @Nested
    @DisplayName("Import Fiscal Periods Tests")
    class ImportFiscalPeriodsTests {

        @Test
        @DisplayName("Should import fiscal periods")
        @Transactional
        void shouldImportFiscalPeriods() throws IOException {
            String csv = "year,month,status,month_closed_at,month_closed_by,tax_filed_at,tax_filed_by\n"
                    + "2025,1,OPEN,,,,\n"
                    + "2025,2,MONTH_CLOSED,2025-03-05 10:00:00,admin,,\n";

            byte[] zipData = buildZipWithFiles("11_fiscal_periods.csv", csv);
            DataImportService.ImportResult result = dataImportService.importAllData(zipData);

            assertThat(result.totalRecords()).isEqualTo(2);

            List<FiscalPeriod> periods = fiscalPeriodRepository.findAll();
            assertThat(periods).hasSizeGreaterThanOrEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Import Tax Deadlines Tests")
    class ImportTaxDeadlinesTests {

        @Test
        @DisplayName("Should import tax deadlines")
        @Transactional
        void shouldImportTaxDeadlines() throws IOException {
            String csv = "deadline_type,name,description,due_day,use_last_day_of_month,reminder_days_before,active\n"
                    + "PPH_21_PAYMENT,Setor PPh 21,Pembayaran PPh 21,10,false,5,true\n";

            byte[] zipData = buildZipWithFiles("12_tax_deadlines.csv", csv);
            DataImportService.ImportResult result = dataImportService.importAllData(zipData);

            assertThat(result.totalRecords()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Import Company Bank Accounts Tests")
    class ImportCompanyBankAccountsTests {

        @Test
        @DisplayName("Should import company bank accounts")
        @Transactional
        void shouldImportCompanyBankAccounts() throws IOException {
            String csv = "bank_name,account_number,account_name,bank_branch,is_default,active\n"
                    + "BCA,1234567890,PT Test Company,KCU Jakarta,true,true\n"
                    + "Mandiri,0987654321,PT Test Company,KCP Bandung,false,true\n";

            byte[] zipData = buildZipWithFiles("13_company_bank_accounts.csv", csv);
            DataImportService.ImportResult result = dataImportService.importAllData(zipData);

            assertThat(result.totalRecords()).isEqualTo(2);

            List<CompanyBankAccount> accounts = companyBankAccountRepository.findAll();
            assertThat(accounts).hasSizeGreaterThanOrEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Import Employees Tests")
    class ImportEmployeesTests {

        @Test
        @DisplayName("Should import employees")
        @Transactional
        void shouldImportEmployees() throws IOException {
            String csv = "employee_id,name,email,nik_ktp,npwp,ptkp_status,job_title,department,employment_type,hire_date,resign_date,bank_name,bank_account_number,bpjs_kesehatan_number,bpjs_ketenagakerjaan_number,employment_status,username\n"
                    + "EMP-TEST-001,Test Employee,test@emp.com,1234567890123456,01.234.567.8-901.234,TK_0,Developer,Engineering,PERMANENT,2023-01-01,,BCA,1234567890,BK001,BT001,ACTIVE,\n";

            byte[] zipData = buildZipWithFiles("15_employees.csv", csv);
            DataImportService.ImportResult result = dataImportService.importAllData(zipData);

            assertThat(result.totalRecords()).isEqualTo(1);

            Employee emp = employeeRepository.findAll().stream()
                    .filter(e -> "EMP-TEST-001".equals(e.getEmployeeId()))
                    .findFirst().orElse(null);
            assertThat(emp).isNotNull();
            assertThat(emp.getName()).isEqualTo("Test Employee");
            assertThat(emp.getPtkpStatus()).isEqualTo(PtkpStatus.TK_0);
            assertThat(emp.getEmploymentType()).isEqualTo(EmploymentType.PERMANENT);
        }
    }

    @Nested
    @DisplayName("Import Users Tests")
    class ImportUsersTests {

        @Test
        @DisplayName("Should import users with generated passwords")
        @Transactional
        void shouldImportUsersWithGeneratedPasswords() throws IOException {
            String csv = "username,full_name,email,active,created_at\n"
                    + "testuser_import,Test User Import,testuser@import.com,true,\n";

            byte[] zipData = buildZipWithFiles("28_users.csv", csv);
            DataImportService.ImportResult result = dataImportService.importAllData(zipData);

            assertThat(result.totalRecords()).isEqualTo(1);

            User user = userRepository.findAll().stream()
                    .filter(u -> "testuser_import".equals(u.getUsername()))
                    .findFirst().orElse(null);
            assertThat(user).isNotNull();
            assertThat(user.getFullName()).isEqualTo("Test User Import");
            assertThat(user.getPassword()).isNotEmpty();
            assertThat(user.getActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("Import Transaction Sequences Tests")
    class ImportTransactionSequencesTests {

        @Test
        @DisplayName("Should import transaction sequences")
        @Transactional
        void shouldImportTransactionSequences() throws IOException {
            String csv = "sequence_type,prefix,year,last_number\n"
                    + "JOURNAL,JRN,2025,150\n";

            byte[] zipData = buildZipWithFiles("33_transaction_sequences.csv", csv);
            DataImportService.ImportResult result = dataImportService.importAllData(zipData);

            assertThat(result.totalRecords()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Import Asset Categories Tests")
    class ImportAssetCategoriesTests {

        @Test
        @DisplayName("Should import asset categories with account references")
        @Transactional
        void shouldImportAssetCategoriesWithAccountReferences() throws IOException {
            // Need accounts first
            String coaCsv = "account_code,account_name,account_type,parent_code,normal_balance,active,is_permanent\n"
                    + "9200,Asset Account,ASSET,,DEBIT,true,false\n"
                    + "9201,Accumulated Depr,ASSET,,CREDIT,true,false\n"
                    + "9202,Depr Expense,EXPENSE,,DEBIT,true,false\n";
            String assetCsv = "code,name,description,depreciation_method,useful_life_months,depreciation_rate,asset_account_code,accumulated_depreciation_account_code,depreciation_expense_account_code,active\n"
                    + "AC-TEST,Test Category,Test asset category,STRAIGHT_LINE,60,20.00,9200,9201,9202,true\n";

            byte[] zipData = buildZipWithMultipleFiles(
                    "02_chart_of_accounts.csv", coaCsv,
                    "34_asset_categories.csv", assetCsv
            );
            DataImportService.ImportResult result = dataImportService.importAllData(zipData);

            assertThat(result.totalRecords()).isGreaterThanOrEqualTo(4);

            AssetCategory category = assetCategoryRepository.findAll().stream()
                    .filter(ac -> "AC-TEST".equals(ac.getCode()))
                    .findFirst().orElse(null);
            assertThat(category).isNotNull();
            assertThat(category.getName()).isEqualTo("Test Category");
            assertThat(category.getDepreciationMethod()).isEqualTo(DepreciationMethod.STRAIGHT_LINE);
            assertThat(category.getUsefulLifeMonths()).isEqualTo(60);
        }
    }

    @Nested
    @DisplayName("Import Product Categories Tests")
    class ImportProductCategoriesTests {

        @Test
        @DisplayName("Should import product categories with parent reference")
        @Transactional
        void shouldImportProductCategoriesWithParentReference() throws IOException {
            String csv = "code,name,description,parent_code,active\n"
                    + "PCAT-PARENT,Parent Category,Parent desc,,true\n"
                    + "PCAT-CHILD,Child Category,Child desc,PCAT-PARENT,true\n";

            byte[] zipData = buildZipWithFiles("35_product_categories.csv", csv);
            DataImportService.ImportResult result = dataImportService.importAllData(zipData);

            assertThat(result.totalRecords()).isEqualTo(2);

            ProductCategory child = productCategoryRepository.findAll().stream()
                    .filter(pc -> "PCAT-CHILD".equals(pc.getCode()))
                    .findFirst().orElse(null);
            assertThat(child).isNotNull();
            assertThat(child.getParent()).isNotNull();
            assertThat(child.getParent().getCode()).isEqualTo("PCAT-PARENT");
        }
    }

    @Nested
    @DisplayName("Import Products Tests")
    class ImportProductsTests {

        @Test
        @DisplayName("Should import products with category and account references")
        @Transactional
        void shouldImportProductsWithCategoryAndAccountReferences() throws IOException {
            String coaCsv = "account_code,account_name,account_type,parent_code,normal_balance,active,is_permanent\n"
                    + "9300,Inventory,ASSET,,DEBIT,true,false\n"
                    + "9301,COGS,EXPENSE,,DEBIT,true,false\n"
                    + "9302,Sales,REVENUE,,CREDIT,true,false\n";
            String catCsv = "code,name,description,parent_code,active\n"
                    + "PCAT-PROD,Product Category,,,true\n";
            String prodCsv = "code,name,description,unit,category_code,costing_method,track_inventory,minimum_stock,selling_price,inventory_account_code,cogs_account_code,sales_account_code,active\n"
                    + "PROD-TEST,Test Product,A test product,PCS,PCAT-PROD,WEIGHTED_AVERAGE,true,10,50000,9300,9301,9302,true\n";

            byte[] zipData = buildZipWithMultipleFiles(
                    "02_chart_of_accounts.csv", coaCsv,
                    "35_product_categories.csv", catCsv,
                    "36_products.csv", prodCsv
            );
            DataImportService.ImportResult result = dataImportService.importAllData(zipData);

            assertThat(result.totalRecords()).isGreaterThanOrEqualTo(5);

            Product product = productRepository.findAll().stream()
                    .filter(p -> "PROD-TEST".equals(p.getCode()))
                    .findFirst().orElse(null);
            assertThat(product).isNotNull();
            assertThat(product.getName()).isEqualTo("Test Product");
            assertThat(product.getUnit()).isEqualTo("PCS");
            assertThat(product.isTrackInventory()).isTrue();
            assertThat(product.getCategory()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Import Draft Transactions Tests")
    class ImportDraftTransactionsTests {

        @Test
        @DisplayName("Should import draft transactions")
        @Transactional
        void shouldImportDraftTransactions() throws IOException {
            String csv = "source,status,merchant_name,transaction_date,amount,suggested_template_name,merchant_confidence,date_confidence,amount_confidence,raw_ocr_text,processed_at,processed_by,rejection_reason\n"
                    + "MANUAL,PENDING,Toko ABC,2025-06-01,150000,,,,,,,,\n";

            byte[] zipData = buildZipWithFiles("27_draft_transactions.csv", csv);
            DataImportService.ImportResult result = dataImportService.importAllData(zipData);

            assertThat(result.totalRecords()).isEqualTo(1);

            List<DraftTransaction> drafts = draftTransactionRepository.findAll();
            assertThat(drafts).hasSizeGreaterThanOrEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Import Inventory Balances Tests")
    class ImportInventoryBalancesTests {

        @Test
        @DisplayName("Should import inventory balances")
        @Transactional
        void shouldImportInventoryBalances() throws IOException {
            String catCsv = "code,name,description,parent_code,active\n"
                    + "INVCAT,Inv Category,,,true\n";
            String prodCsv = "code,name,description,unit,category_code,costing_method,track_inventory,minimum_stock,selling_price,inventory_account_code,cogs_account_code,sales_account_code,active\n"
                    + "INV-PROD,Inv Product,,PCS,INVCAT,WEIGHTED_AVERAGE,true,5,10000,,,,true\n";
            String balCsv = "product_code,quantity,total_cost,average_cost\n"
                    + "INV-PROD,100,1000000,10000\n";

            byte[] zipData = buildZipWithMultipleFiles(
                    "35_product_categories.csv", catCsv,
                    "36_products.csv", prodCsv,
                    "41_inventory_balances.csv", balCsv
            );
            DataImportService.ImportResult result = dataImportService.importAllData(zipData);

            assertThat(result.totalRecords()).isGreaterThanOrEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Import Edge Cases Tests")
    class ImportEdgeCasesTests {

        @Test
        @DisplayName("Should handle empty ZIP archive")
        @Transactional
        void shouldHandleEmptyZipArchive() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream ignored = new ZipOutputStream(baos)) {
                // empty ZIP
            }

            DataImportService.ImportResult result = dataImportService.importAllData(baos.toByteArray());
            assertThat(result.totalRecords()).isEqualTo(0);
            assertThat(result.documentCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should skip CSV files with header only (no data)")
        @Transactional
        void shouldSkipCsvFilesWithHeaderOnly() throws IOException {
            String csv = "company_name,company_address,company_phone,company_email,tax_id,npwp,nitku,fiscal_year_start_month,currency_code,signing_officer_name,signing_officer_title,company_logo_path,established_date,is_pkp,pkp_since,industry\n";

            byte[] zipData = buildZipWithFiles("01_company_config.csv", csv);
            DataImportService.ImportResult result = dataImportService.importAllData(zipData);

            // No data rows, so nothing imported
            assertThat(result.totalRecords()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should skip MANIFEST.md file during import")
        @Transactional
        void shouldSkipManifestFile() throws IOException {
            String manifest = "# Export Manifest\nSome content here.";
            byte[] zipData = buildZipWithFiles("MANIFEST.md", manifest);
            DataImportService.ImportResult result = dataImportService.importAllData(zipData);

            assertThat(result.totalRecords()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should skip documents/index.csv during CSV import")
        @Transactional
        void shouldSkipDocumentsIndexCsv() throws IOException {
            String indexCsv = "filename,original_name,content_type,file_size,uploaded_at\n"
                    + "abc.pdf,test.pdf,application/pdf,1234,2025-01-01 00:00:00\n";

            byte[] zipData = buildZipWithFiles("documents/index.csv", indexCsv);
            DataImportService.ImportResult result = dataImportService.importAllData(zipData);

            assertThat(result.totalRecords()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should reject ZIP entries with path traversal")
        @Transactional
        void shouldRejectZipEntriesWithPathTraversal() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                // Add a malicious entry with path traversal
                zos.putNextEntry(new ZipEntry("../etc/passwd"));
                zos.write("malicious content".getBytes());
                zos.closeEntry();
            }

            DataImportService.ImportResult result = dataImportService.importAllData(baos.toByteArray());
            // Path traversal entry is rejected, nothing imported
            assertThat(result.totalRecords()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should reject ZIP entries starting with absolute path")
        @Transactional
        void shouldRejectAbsolutePathEntries() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                zos.putNextEntry(new ZipEntry("/etc/passwd"));
                zos.write("malicious content".getBytes());
                zos.closeEntry();
            }

            DataImportService.ImportResult result = dataImportService.importAllData(baos.toByteArray());
            assertThat(result.totalRecords()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should skip directory entries in ZIP")
        @Transactional
        void shouldSkipDirectoryEntries() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                zos.putNextEntry(new ZipEntry("documents/"));
                zos.closeEntry();
            }

            DataImportService.ImportResult result = dataImportService.importAllData(baos.toByteArray());
            assertThat(result.totalRecords()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should handle unknown file in ZIP gracefully")
        @Transactional
        void shouldHandleUnknownFileInZip() throws IOException {
            String csv = "col1,col2\nval1,val2\n";
            byte[] zipData = buildZipWithFiles("99_unknown_table.csv", csv);
            // Unknown CSV files are logged as warnings but do not fail
            DataImportService.ImportResult result = dataImportService.importAllData(zipData);
            assertThat(result.totalRecords()).isZero();
        }

        @Test
        @DisplayName("Should handle CSV with quoted fields containing commas")
        @Transactional
        void shouldHandleCsvWithQuotedFieldsContainingCommas() throws IOException {
            String csv = "company_name,company_address,company_phone,company_email,tax_id,npwp,nitku,fiscal_year_start_month,currency_code,signing_officer_name,signing_officer_title,company_logo_path,established_date,is_pkp,pkp_since,industry\n"
                    + "\"PT Test, Inc.\",\"Jl. Raya No. 1, Jakarta\",,,,,,1,IDR,,,,,,,\n";

            byte[] zipData = buildZipWithFiles("01_company_config.csv", csv);
            DataImportService.ImportResult result = dataImportService.importAllData(zipData);

            assertThat(result.totalRecords()).isEqualTo(1);

            CompanyConfig config = companyConfigRepository.findFirst().orElse(null);
            assertThat(config).isNotNull();
            assertThat(config.getCompanyName()).isEqualTo("PT Test, Inc.");
            assertThat(config.getCompanyAddress()).isEqualTo("Jl. Raya No. 1, Jakarta");
        }

        @Test
        @DisplayName("Should handle CSV with escaped quotes")
        @Transactional
        void shouldHandleCsvWithEscapedQuotes() throws IOException {
            String csv = "company_name,company_address,company_phone,company_email,tax_id,npwp,nitku,fiscal_year_start_month,currency_code,signing_officer_name,signing_officer_title,company_logo_path,established_date,is_pkp,pkp_since,industry\n"
                    + "\"PT \"\"Quoted\"\" Name\",,,,,,,1,IDR,,,,,,\n";

            byte[] zipData = buildZipWithFiles("01_company_config.csv", csv);
            DataImportService.ImportResult result = dataImportService.importAllData(zipData);

            assertThat(result.totalRecords()).isEqualTo(1);

            CompanyConfig config = companyConfigRepository.findFirst().orElse(null);
            assertThat(config).isNotNull();
            assertThat(config.getCompanyName()).isEqualTo("PT \"Quoted\" Name");
        }

        @Test
        @DisplayName("Should handle CSV with CRLF line endings")
        @Transactional
        void shouldHandleCsvWithCrlfLineEndings() throws IOException {
            String csv = "company_name,company_address,company_phone,company_email,tax_id,npwp,nitku,fiscal_year_start_month,currency_code,signing_officer_name,signing_officer_title,company_logo_path,established_date,is_pkp,pkp_since,industry\r\n"
                    + "PT CRLF Test,,,,,,,1,IDR,,,,,,\r\n";

            byte[] zipData = buildZipWithFiles("01_company_config.csv", csv);
            DataImportService.ImportResult result = dataImportService.importAllData(zipData);

            assertThat(result.totalRecords()).isEqualTo(1);

            CompanyConfig config = companyConfigRepository.findFirst().orElse(null);
            assertThat(config).isNotNull();
            assertThat(config.getCompanyName()).isEqualTo("PT CRLF Test");
        }

        @Test
        @DisplayName("Should throw on invalid date format in CSV")
        @Transactional
        void shouldThrowOnInvalidDateFormat() throws IOException {
            String csv = "year,month,status,month_closed_at,month_closed_by,tax_filed_at,tax_filed_by\n"
                    + "2025,1,OPEN,not-a-date,admin,,\n";

            byte[] zipData = buildZipWithFiles("11_fiscal_periods.csv", csv);
            assertThatThrownBy(() -> dataImportService.importAllData(zipData))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Failed to import");
        }

        @Test
        @DisplayName("Should throw on invalid enum value in CSV")
        @Transactional
        void shouldThrowOnInvalidEnumValue() throws IOException {
            String csv = "account_code,account_name,account_type,parent_code,normal_balance,active,is_permanent\n"
                    + "9999,Bad Account,INVALID_TYPE,,DEBIT,true,false\n";

            byte[] zipData = buildZipWithFiles("02_chart_of_accounts.csv", csv);
            assertThatThrownBy(() -> dataImportService.importAllData(zipData))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Failed to import");
        }

        @Test
        @DisplayName("Should throw on invalid decimal value in CSV")
        @Transactional
        void shouldThrowOnInvalidDecimalValue() throws IOException {
            String csv = "code,name,description,component_type,is_percentage,default_rate,default_amount,is_system,display_order,active,is_taxable,bpjs_category\n"
                    + "BAD-SC,Bad Component,,EARNING,false,,not_a_number,false,1,true,true,\n";

            byte[] zipData = buildZipWithFiles("03_salary_components.csv", csv);
            assertThatThrownBy(() -> dataImportService.importAllData(zipData))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Failed to import");
        }
    }

    @Nested
    @DisplayName("Import Multiple Files Tests")
    class ImportMultipleFilesTests {

        @Test
        @DisplayName("Should import multiple entity types in correct order")
        @Transactional
        void shouldImportMultipleEntityTypesInCorrectOrder() throws IOException {
            String companyCsv = "company_name,company_address,company_phone,company_email,tax_id,npwp,nitku,fiscal_year_start_month,currency_code,signing_officer_name,signing_officer_title,company_logo_path,established_date,is_pkp,pkp_since,industry\n"
                    + "PT Multi Import,,,,,,,1,IDR,,,,,,\n";
            String coaCsv = "account_code,account_name,account_type,parent_code,normal_balance,active,is_permanent\n"
                    + "9400,Cash,ASSET,,DEBIT,true,false\n"
                    + "9401,Revenue,REVENUE,,CREDIT,true,false\n";
            String clientCsv = "code,name,contact_person,email,phone,address,npwp,nik,nitku,active,created_at\n"
                    + "CLI-MULTI,Multi Client,,,,,,,,true,\n";
            String empCsv = "employee_id,name,email,nik_ktp,npwp,ptkp_status,job_title,department,employment_type,hire_date,resign_date,bank_name,bank_account_number,bpjs_kesehatan_number,bpjs_ketenagakerjaan_number,employment_status,username\n"
                    + "EMP-MULTI,Multi Employee,multi@emp.com,1234567890123456,,TK_0,,,PERMANENT,2024-01-01,,,,,,ACTIVE,\n";

            byte[] zipData = buildZipWithMultipleFiles(
                    "01_company_config.csv", companyCsv,
                    "02_chart_of_accounts.csv", coaCsv,
                    "07_clients.csv", clientCsv,
                    "15_employees.csv", empCsv
            );
            DataImportService.ImportResult result = dataImportService.importAllData(zipData);

            assertThat(result.totalRecords()).isGreaterThanOrEqualTo(5);

            // Verify each entity type was imported
            assertThat(companyConfigRepository.findFirst()).isPresent();
            assertThat(chartOfAccountRepository.findAll().stream()
                    .anyMatch(a -> "9400".equals(a.getAccountCode()))).isTrue();
            assertThat(clientRepository.findAll().stream()
                    .anyMatch(c -> "CLI-MULTI".equals(c.getCode()))).isTrue();
            assertThat(employeeRepository.findAll().stream()
                    .anyMatch(e -> "EMP-MULTI".equals(e.getEmployeeId()))).isTrue();
        }
    }

    @Nested
    @DisplayName("Import Journal Templates Tests")
    class ImportJournalTemplatesTests {

        @Test
        @DisplayName("Should import non-system journal templates")
        @Transactional
        void shouldImportNonSystemJournalTemplates() throws IOException {
            String csv = "template_name,category,cash_flow_category,template_type,description,is_system,active,version,usage_count,last_used_at,semantic_description,keywords,example_merchants,typical_amount_min,typical_amount_max,merchant_patterns\n"
                    + "Test Template Import,EXPENSE,OPERATING,SIMPLE,A test template,false,true,1,0,,,,,,\n";

            byte[] zipData = buildZipWithFiles("04_journal_templates.csv", csv);
            DataImportService.ImportResult result = dataImportService.importAllData(zipData);

            assertThat(result.totalRecords()).isEqualTo(1);

            JournalTemplate template = journalTemplateRepository.findAll().stream()
                    .filter(t -> "Test Template Import".equals(t.getTemplateName()))
                    .findFirst().orElse(null);
            assertThat(template).isNotNull();
            assertThat(template.getCategory()).isEqualTo(TemplateCategory.EXPENSE);
            assertThat(template.getTemplateType()).isEqualTo(TemplateType.SIMPLE);
        }

        @Test
        @DisplayName("Should import template with semantic metadata")
        @Transactional
        void shouldImportTemplateWithSemanticMetadata() throws IOException {
            String csv = "template_name,category,cash_flow_category,template_type,description,is_system,active,version,usage_count,last_used_at,semantic_description,keywords,example_merchants,typical_amount_min,typical_amount_max,merchant_patterns\n"
                    + "Semantic Template,INCOME,OPERATING,SIMPLE,Template with metadata,false,true,1,5,2025-06-01 10:30:00,Records service income,income|service,Client A|Client B,100000,5000000,pattern1|pattern2\n";

            byte[] zipData = buildZipWithFiles("04_journal_templates.csv", csv);
            dataImportService.importAllData(zipData);

            JournalTemplate template = journalTemplateRepository.findAll().stream()
                    .filter(t -> "Semantic Template".equals(t.getTemplateName()))
                    .findFirst().orElse(null);
            assertThat(template).isNotNull();
            assertThat(template.getSemanticDescription()).isEqualTo("Records service income");
            assertThat(template.getKeywords()).containsExactly("income", "service");
            assertThat(template.getExampleMerchants()).containsExactly("Client A", "Client B");
            assertThat(template.getMerchantPatterns()).containsExactly("pattern1", "pattern2");
        }
    }

    @Nested
    @DisplayName("Import Invoices Tests")
    class ImportInvoicesTests {

        @Test
        @DisplayName("Should import invoices with client and project references")
        @Transactional
        void shouldImportInvoicesWithClientAndProjectReferences() throws IOException {
            String clientCsv = "code,name,contact_person,email,phone,address,npwp,nik,nitku,active,created_at\n"
                    + "CLI-INV,Invoice Client,,,,,,,,true,\n";
            String projectCsv = "code,name,client_code,status,start_date,end_date,budget_amount,contract_value,description,created_at\n"
                    + "PRJ-INV,Invoice Project,CLI-INV,ACTIVE,2025-01-01,,,,,\n";
            String invoiceCsv = "invoice_number,invoice_date,due_date,client_code,project_code,status,amount,notes,created_at\n"
                    + "INV-2025-001,2025-06-01,2025-07-01,CLI-INV,PRJ-INV,DRAFT,10000000,Test invoice,\n";

            byte[] zipData = buildZipWithMultipleFiles(
                    "07_clients.csv", clientCsv,
                    "08_projects.csv", projectCsv,
                    "17_invoices.csv", invoiceCsv
            );
            DataImportService.ImportResult result = dataImportService.importAllData(zipData);

            assertThat(result.totalRecords()).isGreaterThanOrEqualTo(3);

            Invoice invoice = invoiceRepository.findAll().stream()
                    .filter(inv -> "INV-2025-001".equals(inv.getInvoiceNumber()))
                    .findFirst().orElse(null);
            assertThat(invoice).isNotNull();
            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.DRAFT);
            assertThat(invoice.getClient()).isNotNull();
            assertThat(invoice.getProject()).isNotNull();
        }
    }

    // ============================================
    // Helper Methods
    // ============================================

    private String extractFileFromZip(byte[] zipData, String filename) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(filename)) {
                    return new String(zis.readAllBytes());
                }
            }
        }
        throw new IllegalArgumentException("File not found in ZIP: " + filename);
    }

    private byte[] buildZipWithFiles(String filename, String content) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(filename));
            zos.write(content.getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private byte[] buildZipWithMultipleFiles(String... filenameContentPairs) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (int i = 0; i < filenameContentPairs.length; i += 2) {
                zos.putNextEntry(new ZipEntry(filenameContentPairs[i]));
                zos.write(filenameContentPairs[i + 1].getBytes());
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    private static String codeOf(com.artivisi.accountingfinance.entity.ChartOfAccount account) {
        return account == null ? "" : account.getAccountCode();
    }
}
