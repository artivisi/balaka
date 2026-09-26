package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.Client;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.AssetCategoryRepository;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.ClientRepository;
import com.artivisi.accountingfinance.repository.CompanyConfigRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Data import through /settings/import, verified against the database.
 *
 * DataImportService truncates every table it imports into (with CASCADE), so this class
 * gets a Spring context (and PostgreSQL container) of its own via the test.context
 * property, and AFTER_CLASS dirtying discards it; no other class sees the imported state.
 */
@DisplayName("Data Import - Persistence Tests")
@Import(ServiceTestDataInitializer.class)
@TestPropertySource(properties = "test.context=data-import-persistence")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DataImportPersistenceTest extends PlaywrightTestBase {

    private static final String CLIENTS_HEADER = "code,name,contact_person,email,phone,address,npwp,nik,nitku,active,created_at\n";
    private static final String COMPANY_CONFIG_HEADER = "company_name,company_address,company_phone,company_email,"
            + "tax_id,npwp,nitku,fiscal_year_start_month,currency_code,signing_officer_name,signing_officer_title,"
            + "company_logo_path,established_date,is_pkp,pkp_since,industry,id_receivable_account,id_payable_account,"
            + "id_output_tax_account,id_input_tax_account\n";
    private static final String CONFIRM_MESSAGE =
            "PERINGATAN: Seluruh data akan dihapus dan diganti dengan data dari file. Lanjutkan?";

    @TempDir
    Path tempDir;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ChartOfAccountRepository accountRepository;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Autowired
    private AssetCategoryRepository assetCategoryRepository;

    @Autowired
    private CompanyConfigRepository companyConfigRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("Valid ZIP replaces the clients table with the CSV rows and leaves tables with header-only CSVs untouched")
    void validImportReplacesClientsAndSkipsHeaderOnlyFiles() throws IOException {
        long assetCategoriesBefore = assetCategoryRepository.count();
        assertThat(assetCategoriesBefore).isPositive();
        String prefix = "IMP-" + uid();

        Path zip = zip("valid.zip", Map.of(
                "07_clients.csv", CLIENTS_HEADER
                        + prefix + "-A,PT Impor Satu,Budi Impor,budi@impor.test,021-111,Jl. Impor 1,01.234.567.8-901.000,,,true,2024-01-01T08:00:00\n"
                        + prefix + "-B,CV Impor Dua,,,,,,3276012345678901,,false,2024-01-01T08:00:00\n",
                "34_asset_categories.csv", "code,name,description,depreciation_method,useful_life_months,depreciation_rate,"
                        + "asset_account_code,accumulated_depreciation_account_code,depreciation_expense_account_code,active\n"));

        String message = submitImport(zip, true);

        assertThat(message).isEqualTo(CONFIRM_MESSAGE);
        assertThat(page.locator("#import-error-message")).hasCount(0);
        assertThat(page.locator("#import-success-message"))
                .hasText(Pattern.compile("Import berhasil: 2 record data, 0 dokumen dalam \\d+ ms"));

        assertThat(clientRepository.findAll().stream().map(Client::getCode).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder(prefix + "-A", prefix + "-B");
        Client first = clientRepository.findByCode(prefix + "-A").orElseThrow();
        assertThat(first.getName()).isEqualTo("PT Impor Satu");
        assertThat(first.getContactPerson()).isEqualTo("Budi Impor");
        assertThat(first.getEmail()).isEqualTo("budi@impor.test");
        assertThat(first.getPhone()).isEqualTo("021-111");
        assertThat(first.getAddress()).isEqualTo("Jl. Impor 1");
        assertThat(first.getNpwp()).isEqualTo("01.234.567.8-901.000");
        assertThat(first.getActive()).isTrue();
        Client second = clientRepository.findByCode(prefix + "-B").orElseThrow();
        assertThat(second.getName()).isEqualTo("CV Impor Dua");
        assertThat(second.getNik()).isEqualTo("3276012345678901");
        assertThat(second.getActive()).isFalse();

        assertThat(assetCategoryRepository.count()).isEqualTo(assetCategoriesBefore);
    }

    @Test
    @DisplayName("A failing file rolls back the whole import: truncated and already-imported tables keep their previous rows")
    void failingFileRollsBackWholeImport() throws IOException {
        Set<String> clientsBefore = clientCodes();
        assertThat(clientsBefore).isNotEmpty();
        String code = "ROLLBACK-" + uid();

        Path zip = zip("partial.zip", Map.of(
                "07_clients.csv", CLIENTS_HEADER + code + ",PT Tidak Jadi,,,,,,,,true,2024-01-01T08:00:00\n",
                "08_projects.csv", "code,name,client_code,status,start_date,end_date,budget_amount,contract_value,description,created_at\n"
                        + "PRJ-" + code + ",Proyek Rusak," + code + ",BOGUS,2024-01-01,,,,,2024-01-01T08:00:00\n"));

        submitImport(zip, true);

        assertThat(page.locator("#import-success-message")).hasCount(0);
        assertThat(page.locator("#import-error-message")).hasText(
                "Error: Failed to import 08_projects.csv: No enum constant com.artivisi.accountingfinance.enums.ProjectStatus.BOGUS");
        assertThat(clientCodes()).isEqualTo(clientsBefore);
        assertThat(clientRepository.findByCode(code)).isEmpty();
    }

    @Test
    @DisplayName("A CSV with the wrong columns is rejected and the chart of accounts and journals stay intact")
    void wrongColumnsRejectedWithoutChanges() throws IOException {
        long accountsBefore = accountRepository.count();
        long journalEntriesBefore = journalEntryRepository.count();
        assertThat(accountRepository.findByAccountCode("1.1.02")).isPresent();

        Path zip = zip("wrong-columns.zip", Map.of(
                "02_chart_of_accounts.csv", "wrong_column1,wrong_column2\nval1,val2\n"));

        submitImport(zip, true);

        assertThat(page.locator("#import-error-message")).hasText(
                "Error: Failed to import 02_chart_of_accounts.csv: No enum constant com.artivisi.accountingfinance.enums.AccountType.");
        assertThat(accountRepository.count()).isEqualTo(accountsBefore);
        assertThat(journalEntryRepository.count()).isEqualTo(journalEntriesBefore);
        assertThat(accountRepository.findByAccountCode("1.1.02").orElseThrow().getAccountName()).isEqualTo("Bank BCA");
        assertThat(accountRepository.findByAccountCode("val1")).isEmpty();
    }

    @Test
    @DisplayName("Dismissing the overwrite confirmation submits nothing")
    void dismissingConfirmationImportsNothing() throws IOException {
        Set<String> clientsBefore = clientCodes();
        String code = "DISMISS-" + uid();
        Path zip = zip("dismissed.zip", Map.of(
                "07_clients.csv", CLIENTS_HEADER + code + ",PT Batal,,,,,,,,true,2024-01-01T08:00:00\n"));

        String message = submitImport(zip, false);

        assertThat(message).isEqualTo(CONFIRM_MESSAGE);
        assertThat(page.locator("#import-success-message")).hasCount(0);
        assertThat(page.locator("#import-error-message")).hasCount(0);
        assertThat(clientCodes()).isEqualTo(clientsBefore);
    }

    @Test
    @DisplayName("Zero-byte and non-ZIP uploads are rejected before anything is imported")
    void emptyAndNonZipFilesRejected() throws IOException {
        Set<String> clientsBefore = clientCodes();

        Path zeroByte = Files.createFile(tempDir.resolve("zero.zip"));
        submitImport(zeroByte, true);
        assertThat(page.locator("#import-error-message")).hasText("File tidak boleh kosong");

        Path text = Files.writeString(tempDir.resolve("clients.csv"), CLIENTS_HEADER + "X,Y,,,,,,,,true,\n");
        submitImport(text, true);
        assertThat(page.locator("#import-error-message"))
                .hasText("Format file tidak didukung. Gunakan file ZIP hasil ekspor.");

        assertThat(clientCodes()).isEqualTo(clientsBefore);
    }

    @Test
    @DisplayName("A company_config row without company_name keeps the company identity and sets industry and the given posting accounts by COA code")
    void settingsOnlyCompanyConfigRowUpdatesPostingAccounts() throws IOException {
        Map<String, Object> before = companyConfigRow();
        assertThat(before.get("company_name")).isEqualTo("PT ArtiVisi Intermedia");
        assertThat(before.get("output_tax")).isEqualTo("2.1.03");

        Path zip = zip("settings-only.zip", Map.of("01_company_config.csv",
                COMPANY_CONFIG_HEADER + ",,,,,,,,,,,,,,,online-seller,1.1.11,2.1.02,,\n"));

        submitImport(zip, true);

        assertThat(page.locator("#import-error-message")).hasCount(0);
        assertThat(page.locator("#import-success-message"))
                .hasText(Pattern.compile("Import berhasil: 1 record data, 0 dokumen dalam \\d+ ms"));
        Map<String, Object> after = companyConfigRow();
        assertThat(companyConfigRepository.count()).isEqualTo(1);
        assertThat(after.get("id")).isEqualTo(before.get("id"));
        assertThat(after.get("company_name")).isEqualTo("PT ArtiVisi Intermedia");
        assertThat(after.get("npwp")).isEqualTo(before.get("npwp"));
        assertThat(after.get("industry")).isEqualTo("online-seller");
        assertThat(after.get("receivable")).isEqualTo("1.1.11");
        assertThat(after.get("payable")).isEqualTo("2.1.02");
        assertThat(after.get("output_tax")).isEqualTo("2.1.03");
        assertThat(after.get("input_tax")).isEqualTo("1.1.25");
    }

    @Test
    @DisplayName("A posting-account code missing from the chart of accounts rejects the import and leaves the company config unchanged")
    void unknownPostingAccountCodeRejected() throws IOException {
        Map<String, Object> before = companyConfigRow();

        Path zip = zip("unknown-account.zip", Map.of("01_company_config.csv",
                COMPANY_CONFIG_HEADER + ",,,,,,,,,,,,,,,,9.9.99,,,\n"));

        submitImport(zip, true);

        assertThat(page.locator("#import-success-message")).hasCount(0);
        assertThat(page.locator("#import-error-message")).hasText(
                "Error: Failed to import 01_company_config.csv: receivable account 9.9.99 not found in chart of accounts");
        assertThat(companyConfigRow()).isEqualTo(before);
    }

    @Test
    @DisplayName("A company_config row without company_name but with identity columns is rejected")
    void companyConfigRowWithoutNameRejected() throws IOException {
        Map<String, Object> before = companyConfigRow();

        Path zip = zip("no-name.zip", Map.of("01_company_config.csv",
                COMPANY_CONFIG_HEADER + ",Jl. Tanpa Nama,,,,,,1,IDR,,,,,,,it-service,,,,\n"));

        submitImport(zip, true);

        assertThat(page.locator("#import-error-message")).hasText(
                "Error: Failed to import 01_company_config.csv: company_name is required when company identity columns are set");
        assertThat(companyConfigRow()).isEqualTo(before);
    }

    private Map<String, Object> companyConfigRow() {
        return jdbcTemplate.queryForMap("""
                SELECT c.id, c.company_name, c.npwp, c.industry, ar.account_code AS receivable,
                       ap.account_code AS payable, ot.account_code AS output_tax, it.account_code AS input_tax
                FROM company_config c
                LEFT JOIN chart_of_accounts ar ON ar.id = c.id_receivable_account
                LEFT JOIN chart_of_accounts ap ON ap.id = c.id_payable_account
                LEFT JOIN chart_of_accounts ot ON ot.id = c.id_output_tax_account
                LEFT JOIN chart_of_accounts it ON it.id = c.id_input_tax_account""");
    }

    private String submitImport(Path file, boolean accept) {
        navigateTo("/settings/import");
        waitForPageLoad();
        page.locator("#file").setInputFiles(file);
        AtomicReference<String> message = new AtomicReference<>();
        page.onceDialog(dialog -> {
            message.set(dialog.message());
            if (accept) {
                dialog.accept();
            } else {
                dialog.dismiss();
            }
        });
        page.locator("#btn-import").click();
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/settings/import");
        return message.get();
    }

    private Set<String> clientCodes() {
        return clientRepository.findAll().stream().map(Client::getCode).collect(Collectors.toSet());
    }

    private Path zip(String name, Map<String, String> entries) throws IOException {
        Path path = tempDir.resolve(name);
        try (OutputStream out = Files.newOutputStream(path); ZipOutputStream zos = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return path;
    }

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
