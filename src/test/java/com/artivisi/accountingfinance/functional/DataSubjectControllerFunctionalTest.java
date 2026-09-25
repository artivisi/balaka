package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.Employee;
import com.artivisi.accountingfinance.entity.PayrollDetail;
import com.artivisi.accountingfinance.entity.PayrollRun;
import com.artivisi.accountingfinance.entity.PayrollStatus;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.AuditEventType;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.EmployeeRepository;
import com.artivisi.accountingfinance.repository.PayrollDetailRepository;
import com.artivisi.accountingfinance.repository.PayrollRunRepository;
import com.artivisi.accountingfinance.repository.SecurityAuditLogRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Data subject rights (Pengaturan → Subjek Data): employee lookup, retention status, DSAR export with
 * masked identifiers, and anonymization (right to erasure) that removes PII from the database while
 * keeping payroll records.
 *
 * PII encryption is enabled (same configuration as EmployeeControllerFunctionalTest, so the Spring
 * context is shared) to verify that encrypted columns are cleared, not just their plaintext view.
 * Every test works on employees it creates; the seeded EMP-001 is only read.
 */
@DisplayName("Data Subject Controller Tests")
@Import(ServiceTestDataInitializer.class)
@TestPropertySource(properties = "app.encryption.key=Kea3OzaaPR89QOebS1b427C8xxRoztOft8rlSEL3W7k=")
class DataSubjectControllerFunctionalTest extends PlaywrightTestBase {

    // From src/test/resources/testdata/service: EMP-001 has a payroll detail in the 2024-02 run
    private static final String SEEDED_EMPLOYEE_WITH_PAYROLL = "EMP-001";
    // Reserved for this class
    private static final String PAYROLL_PERIOD = "2065-01";

    private static final String NIK = "3201019876543210";
    private static final String NPWP = "01.987.654.3-210.000";
    private static final String BANK_ACCOUNT = "5566778899";
    private static final String BPJS_KES = "0009876543210";
    private static final String BPJS_TK = "BPU9876543210";

    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private PayrollRunRepository payrollRunRepository;
    @Autowired private PayrollDetailRepository payrollDetailRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SecurityAuditLogRepository securityAuditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Should open the data subject list from the settings sidebar")
    void shouldOpenFromSidebar() {
        loginAsAdmin();
        navigateTo("/dashboard");
        waitForPageLoad();

        page.locator("#nav-group-pengaturan").click();
        page.locator("#nav-data-subjects").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/settings/data-subjects");
        assertThat(page.locator("#page-title")).hasText("Hak Subjek Data (GDPR/UU PDP)");
        assertThat(page.locator("#nav-data-subjects")).hasClass(java.util.regex.Pattern.compile("bg-primary-600"));
    }

    @Test
    @DisplayName("Should find an employee by name with the live search")
    void shouldSearchEmployees() {
        Employee employee = createEmployee(uid());
        Employee other = createEmployee(uid());
        loginAsAdmin();
        navigateTo("/settings/data-subjects");
        waitForPageLoad();

        Locator input = page.locator("#search-input");
        input.fill(employee.getName());
        input.press("End");

        Locator rows = page.locator("#employee-table tbody tr");
        assertThat(rows).hasCount(1);
        Locator row = row(employee);
        assertThat(row).containsText(employee.getEmployeeId());
        assertThat(row).containsText(employee.getEmail());
        assertThat(row).containsText("Aktif");
        assertThat(row.locator("a[href='/settings/data-subjects/" + employee.getId() + "/anonymize']")).hasCount(1);
        assertThat(row(other)).hasCount(0);
    }

    @Test
    @DisplayName("Should show contact data and deletable retention status for an employee without payroll")
    void shouldShowDeletableRetentionStatus() {
        Employee employee = createEmployee(uid());
        loginAsAdmin();

        openDetail(employee);

        assertThat(page.locator("#employee-id")).hasText(employee.getEmployeeId());
        assertThat(page.locator("#employee-name")).hasText(employee.getName());
        assertThat(page.locator("main")).containsText(employee.getEmail());
        assertThat(page.locator("main")).containsText(employee.getPhone());
        assertThat(page.locator("#retention-status h3")).hasText("Data can be deleted");
        assertThat(page.locator("#retention-status")).containsText("10 tahun (sesuai UU KUP Art. 28)");
        assertThat(financialRecordsFlag()).hasText("Tidak");
        assertThat(page.locator("#btn-anonymize")).isVisible();
    }

    @Test
    @DisplayName("Should require retention for an employee with payroll records")
    void shouldRequireRetentionWithPayroll() {
        Employee employee = employeeRepository.findByEmployeeId(SEEDED_EMPLOYEE_WITH_PAYROLL)
                .orElseThrow(() -> new IllegalStateException("Seeded employee missing: " + SEEDED_EMPLOYEE_WITH_PAYROLL));
        loginAsAdmin();

        openDetail(employee);

        assertThat(page.locator("#retention-status h3"))
                .hasText("Data must be retained for 10 years after resignation");
        assertThat(financialRecordsFlag()).hasText("Ya");

        navigateTo("/settings/data-subjects/" + employee.getId() + "/anonymize");
        waitForPageLoad();
        assertThat(page.locator("main")).containsText("Catatan Periode Retensi");
    }

    @Test
    @DisplayName("Should export personal data with masked identifiers and audit the request")
    void shouldExportPersonalDataMasked() {
        Employee employee = createEmployee(uid());
        loginAsAdmin();
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        openDetail(employee);
        page.locator("#btn-export").click();
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Ekspor Data Pribadi (DSAR)");
        assertThat(page.locator("#export-name")).hasText(employee.getName());
        Locator data = page.locator("#exported-data");
        assertThat(data).containsText(employee.getEmployeeId());
        assertThat(data).containsText(employee.getEmail());
        assertThat(data).containsText(employee.getPhone());
        assertThat(page.locator("#masked-nik")).hasText("32****10");
        assertThat(page.locator("#masked-npwp")).hasText("01****00");
        assertThat(page.locator("#masked-bank")).hasText("55****99");
        String content = page.content();
        assertThat(content).doesNotContain(NIK).doesNotContain(NPWP).doesNotContain(BANK_ACCOUNT);

        assertThat(securityAuditLogRepository.findByUsernameAndEventTypeAndTimestampAfter(
                "admin", AuditEventType.DATA_EXPORT, before))
                .anyMatch(l -> l.getDetails().equals(
                        "Personal data exported for employee: " + employee.getName() + " (DSAR request)"));
    }

    @Test
    @DisplayName("Should anonymize PII in the database, keep payroll records and audit the erasure")
    void shouldAnonymizeEmployee() {
        String marker = uid();
        Employee employee = createEmployee(marker);
        PayrollDetail payslip = addPayslip(employee);
        String originalName = employee.getName();
        String reason = "Permintaan tertulis subjek data no. " + marker;
        loginAsAdmin();
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        openDetail(employee);
        assertThat(financialRecordsFlag()).hasText("Ya");
        page.locator("#btn-anonymize").click();
        waitForPageLoad();
        page.locator("#reason").fill(reason);
        page.locator("#confirm").check();
        page.locator("#btn-confirm-anonymize").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/settings/data-subjects");
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Data karyawan " + originalName
                + " berhasil dianonimisasi sesuai permintaan hak hapus data (GDPR Art. 17)");

        String anonymizedName = "ANONYMIZED-" + employee.getId().toString().substring(0, 8);
        Map<String, Object> raw = jdbcTemplate.queryForMap(
                "SELECT employee_id, name, email, phone, address, nik_ktp, npwp, bank_name, bank_account_number, "
                        + "bank_account_name, bpjs_kesehatan_number, bpjs_ketenagakerjaan_number, notes, active "
                        + "FROM employees WHERE id = ?", employee.getId());
        assertThat(raw.get("employee_id")).isEqualTo(employee.getEmployeeId());
        assertThat(raw.get("name")).isEqualTo(anonymizedName);
        for (String column : new String[] {"email", "phone", "address", "nik_ktp", "npwp", "bank_name",
                "bank_account_number", "bank_account_name", "bpjs_kesehatan_number", "bpjs_ketenagakerjaan_number"}) {
            assertThat(raw.get(column)).as(column).isNull();
        }
        assertThat((String) raw.get("notes")).contains("Reason: " + reason).doesNotContain(originalName);
        assertThat(raw.get("active")).isEqualTo(false);

        PayrollDetail kept = payrollDetailRepository.findPostedByEmployeeIdAndYear(employee.getId(), "2065")
                .stream().filter(d -> d.getId().equals(payslip.getId())).findFirst()
                .orElseThrow(() -> new AssertionError("Payroll detail removed by anonymization"));
        assertThat(kept.getGrossSalary()).isEqualByComparingTo("8000000");
        assertThat(kept.getNetPay()).isEqualByComparingTo("7600000");

        assertThat(securityAuditLogRepository.findByUsernameAndEventTypeAndTimestampAfter(
                "admin", AuditEventType.USER_DELETE, before))
                .anyMatch(l -> l.getDetails().equals("Employee data anonymized (right to erasure): "
                        + originalName + " -> ANONYMIZED. Reason: " + reason));

        page.locator("#search-input").fill(anonymizedName);
        page.locator("#search-input").press("End");
        Locator row = row(employee);
        assertThat(row).containsText("Dianonimisasi");
        assertThat(row.locator("a[href$='/anonymize']")).hasCount(0);

        openDetail(employee);
        assertThat(page.locator("#employee-name")).hasText(anonymizedName);
        assertThat(page.locator("#btn-anonymize")).hasCount(0);
        assertThat(page.locator("main")).not().containsText(originalName);
    }

    @Test
    @DisplayName("Should reject anonymization without a reason and keep the data")
    void shouldRejectBlankReason() {
        Employee employee = createEmployee(uid());
        loginAsAdmin();
        navigateTo("/settings/data-subjects/" + employee.getId() + "/anonymize");
        waitForPageLoad();
        String csrf = page.locator("meta[name='_csrf']").getAttribute("content");

        APIResponse response = page.request().post(baseUrl() + "/settings/data-subjects/" + employee.getId() + "/anonymize",
                RequestOptions.create().setHeader("X-CSRF-TOKEN", csrf)
                        .setForm(FormData.create().set("reason", "   ").set("confirm", "on"))
                        .setMaxRedirects(0));
        assertThat(response.status()).isEqualTo(302);
        assertThat(response.headers().get("location"))
                .isEqualTo(baseUrl() + "/settings/data-subjects/" + employee.getId() + "/anonymize");

        navigateTo("/settings/data-subjects/" + employee.getId() + "/anonymize");
        waitForPageLoad();
        assertThat(page.locator("[data-testid='flash-error']")).hasText("Alasan anonimisasi wajib diisi");

        Employee unchanged = employeeRepository.findById(employee.getId()).orElseThrow();
        assertThat(unchanged.getName()).isEqualTo(employee.getName());
        assertThat(unchanged.getNikKtp()).isEqualTo(NIK);
        assertThat(unchanged.getActive()).isTrue();
    }

    @Test
    @DisplayName("Should deny data subject pages and anonymization to a role without data subject permissions")
    void shouldDenyAuditor() {
        Employee employee = createEmployee(uid());
        User auditor = createUser(Role.AUDITOR);
        login(auditor.getUsername(), "password");

        for (String path : new String[] {"/settings/data-subjects", "/settings/data-subjects/" + employee.getId(),
                "/settings/data-subjects/" + employee.getId() + "/export",
                "/settings/data-subjects/" + employee.getId() + "/anonymize"}) {
            Response response = page.navigate(baseUrl() + path);
            assertThat(response.status()).as(path).isEqualTo(403);
        }
        assertThat(page.locator("#nav-data-subjects")).hasCount(0);

        navigateTo("/dashboard");
        String csrf = page.locator("meta[name='_csrf']").getAttribute("content");
        APIResponse response = page.request().post(baseUrl() + "/settings/data-subjects/" + employee.getId() + "/anonymize",
                RequestOptions.create().setHeader("X-CSRF-TOKEN", csrf)
                        .setForm(FormData.create().set("reason", "tidak berhak"))
                        .setMaxRedirects(0));
        assertThat(response.status()).isEqualTo(403);

        Employee unchanged = employeeRepository.findById(employee.getId()).orElseThrow();
        assertThat(unchanged.getName()).isEqualTo(employee.getName());
        assertThat(unchanged.getNikKtp()).isEqualTo(NIK);
    }

    // ==================== helpers ====================

    private void openDetail(Employee employee) {
        navigateTo("/settings/data-subjects/" + employee.getId());
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Detail Subjek Data");
    }

    private Locator financialRecordsFlag() {
        return page.locator("#retention-status div:has(> dt:text-is('Memiliki Catatan Keuangan:')) > dd");
    }

    private Locator row(Employee employee) {
        return page.locator("#row-" + employee.getId());
    }

    private Employee createEmployee(String marker) {
        Employee employee = new Employee();
        employee.setEmployeeId("DS-" + marker);
        employee.setName("Subjek Data " + marker);
        employee.setEmail("subjek-" + marker + "@example.com");
        employee.setPhone("0812" + marker.substring(0, 6).replaceAll("[a-f]", "7"));
        employee.setAddress("Jl. Privasi No. " + marker);
        employee.setNikKtp(NIK);
        employee.setNpwp(NPWP);
        employee.setBankName("Bank Mandiri");
        employee.setBankAccountNumber(BANK_ACCOUNT);
        employee.setBankAccountName("Subjek Data " + marker);
        employee.setBpjsKesehatanNumber(BPJS_KES);
        employee.setBpjsKetenagakerjaanNumber(BPJS_TK);
        employee.setJobTitle("Analis");
        employee.setDepartment("Keuangan");
        employee.setHireDate(LocalDate.of(2045, 1, 2));
        return employeeRepository.save(employee);
    }

    private PayrollDetail addPayslip(Employee employee) {
        PayrollRun run = payrollRunRepository.findByPayrollPeriod(PAYROLL_PERIOD).orElseGet(() -> {
            PayrollRun created = new PayrollRun();
            created.setPayrollPeriod(PAYROLL_PERIOD);
            created.setPeriodStart(LocalDate.of(2065, 1, 1));
            created.setPeriodEnd(LocalDate.of(2065, 1, 31));
            created.setStatus(PayrollStatus.POSTED);
            created.setPostedAt(LocalDateTime.now());
            return payrollRunRepository.save(created);
        });
        PayrollDetail detail = new PayrollDetail();
        detail.setPayrollRun(run);
        detail.setEmployee(employee);
        detail.setBaseSalary(new BigDecimal("8000000"));
        detail.setGrossSalary(new BigDecimal("8000000"));
        detail.setTotalDeductions(new BigDecimal("400000"));
        detail.setNetPay(new BigDecimal("7600000"));
        return payrollDetailRepository.save(detail);
    }

    private User createUser(Role role) {
        String id = uid();
        User user = new User();
        user.setUsername("ds-" + role.name().toLowerCase() + "-" + id);
        user.setPassword(passwordEncoder.encode("password"));
        user.setFullName("Pengguna " + id);
        user.setEmail("ds-" + id + "@example.com");
        user.setActive(true);
        user.addRole(role, "system");
        return userRepository.save(user);
    }

    private static String uid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
