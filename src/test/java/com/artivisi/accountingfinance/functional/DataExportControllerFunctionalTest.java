package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.Client;
import com.artivisi.accountingfinance.entity.Employee;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.AuditEventType;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.ClientRepository;
import com.artivisi.accountingfinance.repository.EmployeeRepository;
import com.artivisi.accountingfinance.repository.SecurityAuditLogRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full data export (Pengaturan → Ekspor Data): the statistics shown on the page, the ZIP produced by
 * "Ekspor Semua Data" (entries listed on the page, CSV rows matching the database), the audit trail of
 * the export, and the DATA_IMPORT permission guarding it.
 */
@DisplayName("Data Export Controller Tests")
@Import(ServiceTestDataInitializer.class)
class DataExportControllerFunctionalTest extends PlaywrightTestBase {

    // From industry-seed/it-service/seed-data/02_chart_of_accounts.csv
    private static final String SEEDED_ACCOUNT_CODE = "1.1.01";
    private static final String SEEDED_ACCOUNT_PARENT = "1.1";

    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SecurityAuditLogRepository securityAuditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("Should show record counts matching the database")
    void shouldShowRecordCountsMatchingDatabase() {
        loginAsAdmin();
        openExportPage();

        assertThat(page.locator("#stat-account p").first()).hasText(String.valueOf(accountRepository.count()));
        assertThat(page.locator("#stat-transaction p").first()).hasText(String.valueOf(transactionRepository.count()));
        assertThat(page.locator("#stat-client p").first()).hasText(String.valueOf(clientRepository.count()));
        assertThat(page.locator("#stat-employee p").first()).hasText(String.valueOf(employeeRepository.count()));
    }

    @Test
    @DisplayName("Should download a ZIP containing every file listed on the page")
    void shouldDownloadZipWithListedFiles() throws IOException {
        loginAsAdmin();
        openExportPage();

        List<String> listed = page.locator("#export-file-list strong").allTextContents();
        assertThat(listed).hasSize(12);

        Download download = page.waitForDownload(() -> page.locator("#btn-export").click());
        assertThat(download.suggestedFilename())
                .isEqualTo("export-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".zip");

        Map<String, String> zip = readZip(Files.readAllBytes(download.path()));
        assertThat(zip.keySet()).containsAll(listed);
        assertThat(zip.get("MANIFEST.md"))
                .contains("- Chart of Accounts: " + accountRepository.count() + " records");
    }

    @Test
    @DisplayName("Should export CSV rows matching database records, escaped")
    void shouldExportCsvRowsMatchingDatabase() throws IOException {
        String marker = uid();
        Client client = new Client();
        client.setCode("EXP-" + marker);
        client.setName("Klien, \"Ekspor\" " + marker);
        client.setEmail("ekspor-" + marker + "@example.com");
        client.setNpwp("01.234.567.8-901.000");
        clientRepository.save(client);

        Employee employee = new Employee();
        employee.setEmployeeId("EXP-" + marker);
        employee.setName("Karyawan Ekspor " + marker);
        employee.setEmail("karyawan-" + marker + "@example.com");
        employee.setNikKtp("3201012345678901");
        employee.setHireDate(LocalDate.of(2045, 1, 2));
        employee.setBankName("BCA");
        employee.setBankAccountNumber("9876543210");
        employeeRepository.save(employee);

        ChartOfAccount account = accountRepository.findByAccountCode(SEEDED_ACCOUNT_CODE)
                .orElseThrow(() -> new IllegalStateException("Seeded account missing: " + SEEDED_ACCOUNT_CODE));

        loginAsAdmin();
        Map<String, String> zip = downloadExport();

        List<String> accounts = zip.get("02_chart_of_accounts.csv").lines().toList();
        assertThat(accounts.getFirst())
                .isEqualTo("account_code,account_name,account_type,parent_code,normal_balance,active,created_at");
        assertThat(accounts).hasSize((int) accountRepository.count() + 1);
        assertThat(accounts).anyMatch(l -> l.startsWith(SEEDED_ACCOUNT_CODE + "," + account.getAccountName() + ","
                + account.getAccountType() + "," + SEEDED_ACCOUNT_PARENT + ","
                + account.getNormalBalance() + ",true,"));

        assertThat(zip.get("07_clients.csv").lines())
                .anyMatch(l -> l.startsWith("EXP-" + marker + ",\"Klien, \"\"Ekspor\"\" " + marker + "\",,ekspor-"
                        + marker + "@example.com,,,01.234.567.8-901.000,"));

        assertThat(zip.get("15_employees.csv").lines())
                .anyMatch(l -> l.startsWith("EXP-" + marker + ",Karyawan Ekspor " + marker + ",karyawan-" + marker
                        + "@example.com,3201012345678901,,TK_0,,,PERMANENT,2045-01-02,,BCA,9876543210,"));

        String users = zip.get("28_users.csv");
        assertThat(users.lines().findFirst().orElseThrow()).isEqualTo("username,full_name,email,active,created_at");
        assertThat(users.lines()).anyMatch(l -> l.startsWith("admin,"));
        String adminHash = userRepository.findByUsername("admin").orElseThrow().getPassword();
        assertThat(users).doesNotContain(adminHash).doesNotContain("$2a$");
    }

    @Test
    @DisplayName("Should record the export in the security audit log")
    void shouldAuditExport() {
        loginAsAdmin();
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        downloadExport();

        assertThat(securityAuditLogRepository.findByUsernameAndEventTypeAndTimestampAfter(
                "admin", AuditEventType.DATA_EXPORT, before))
                .anyMatch(l -> l.getDetails().startsWith("Full data export: export-"));
    }

    @Test
    @DisplayName("Should open the export page from company settings")
    void shouldOpenFromCompanySettings() {
        loginAsAdmin();
        navigateTo("/settings");
        waitForPageLoad();

        page.locator("a[href='/settings/export']").click();
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Ekspor Data");
        page.locator("#link-back-to-settings").click();
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/settings");
    }

    @Test
    @DisplayName("Should deny the export page and download to a user without DATA_IMPORT")
    void shouldDenyWithoutDataImportPermission() {
        User auditor = createUser(Role.AUDITOR);
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        login(auditor.getUsername(), "password");

        Response pageResponse = page.navigate(baseUrl() + "/settings/export");
        assertThat(pageResponse.status()).isEqualTo(403);
        assertThat(page.locator("#access-denied-page")).isVisible();

        navigateTo("/dashboard");
        String csrf = page.locator("meta[name='_csrf']").getAttribute("content");
        APIResponse download = page.request().post(baseUrl() + "/settings/export/download",
                RequestOptions.create().setHeader("X-CSRF-TOKEN", csrf));
        assertThat(download.status()).isEqualTo(403);
        assertThat(download.headers().get("content-disposition")).isNull();

        assertThat(securityAuditLogRepository.findByUsernameAndEventTypeAndTimestampAfter(
                auditor.getUsername(), AuditEventType.DATA_EXPORT, before)).isEmpty();
    }

    // ==================== helpers ====================

    private void openExportPage() {
        navigateTo("/settings/export");
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Ekspor Data");
    }

    private Map<String, String> downloadExport() {
        openExportPage();
        Download download = page.waitForDownload(() -> page.locator("#btn-export").click());
        try {
            return readZip(Files.readAllBytes(download.path()));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read downloaded export", e);
        }
    }

    private static Map<String, String> readZip(byte[] bytes) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zis.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        assertThat(entries).as("ZIP entries").isNotEmpty();
        return entries;
    }

    private User createUser(Role role) {
        String id = uid();
        User user = new User();
        user.setUsername("export-" + role.name().toLowerCase() + "-" + id);
        user.setPassword(passwordEncoder.encode("password"));
        user.setFullName("Pengguna Ekspor " + id);
        user.setEmail("export-" + id + "@example.com");
        user.setActive(true);
        user.addRole(role, "system");
        return userRepository.save(user);
    }

    private static String uid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
