package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.Employee;
import com.artivisi.accountingfinance.entity.PayrollDetail;
import com.artivisi.accountingfinance.entity.PayrollRun;
import com.artivisi.accountingfinance.entity.PayrollStatus;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.EmployeeRepository;
import com.artivisi.accountingfinance.repository.PayrollDetailRepository;
import com.artivisi.accountingfinance.repository.PayrollRunRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Employee self-service: own profile (view, edit of contact and bank fields only), own posted payslips
 * and 1721-A1 bukti potong. Each test logs in as an EMPLOYEE-role user linked to an employee it creates;
 * a second employee in the same payroll run is the target of cross-employee access attempts.
 */
@DisplayName("Self Service Controller Tests")
@Import(ServiceTestDataInitializer.class)
class SelfServiceControllerFunctionalTest extends PlaywrightTestBase {

    private static final String PASSWORD = "password";
    // Reserved for this class
    private static final int PAYROLL_YEAR = 2064;
    private static final String POSTED_PERIOD = "2064-01";
    private static final String APPROVED_PERIOD = "2064-02";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired private UserRepository userRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private PayrollRunRepository payrollRunRepository;
    @Autowired private PayrollDetailRepository payrollDetailRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Employee self;
    private Employee colleague;

    @BeforeEach
    void setUp() {
        // RateLimitFilter allows 10 login POSTs per client IP per minute
        context.setExtraHTTPHeaders(Map.of("X-Forwarded-For", "10.66." + RANDOM.nextInt(256) + "." + RANDOM.nextInt(256)));
        self = createLinkedEmployee();
        colleague = createLinkedEmployee();
    }

    // ==================== PROFILE ====================

    @Test
    @DisplayName("Should show the logged-in employee's own profile")
    void shouldShowOwnProfile() {
        loginAs(self);
        navigateTo("/self-service/profile");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Profil Saya");
        Locator main = page.locator("main");
        assertThat(main).containsText(self.getEmployeeId());
        assertThat(main).containsText(self.getName());
        assertThat(main).containsText(self.getEmail());
        assertThat(main).containsText(self.getPhone());
        assertThat(main).containsText(self.getBankAccountNumber());
        assertThat(main).not().containsText(colleague.getName());
        takeManualScreenshot("self-service-profile");
    }

    @Test
    @DisplayName("Should tell a user without a linked employee to contact the administrator")
    void shouldShowNoEmployeeMessage() {
        User unlinked = createEmployeeUser();
        login(unlinked.getUsername(), PASSWORD);

        for (String path : new String[] {"/self-service/profile", "/self-service/payslips", "/self-service/bukti-potong"}) {
            navigateTo(path);
            waitForPageLoad();
            assertThat(page.locator("main")).containsText(
                    "Akun Anda belum terhubung dengan data karyawan. Silakan hubungi administrator.");
        }

        navigateTo("/self-service/profile/edit");
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/self-service/profile");
        assertThat(page.request().get(baseUrl() + "/self-service/bukti-potong/" + PAYROLL_YEAR + "/pdf").status())
                .isEqualTo(404);
    }

    @Test
    @DisplayName("Should update contact and bank fields of the own profile")
    void shouldUpdateOwnProfile() {
        loginAs(self);
        navigateTo("/self-service/profile");
        waitForPageLoad();
        page.locator("a[href='/self-service/profile/edit']").first().click();
        waitForPageLoad();

        assertThat(page.locator("#phone")).hasValue(self.getPhone());
        page.locator("#phone").fill("081299990000");
        page.locator("#address").fill("Jl. Mandiri No. 45, Bogor");
        page.locator("#bankName").fill("BNI");
        page.locator("#bankAccountNumber").fill("1122334455");
        page.locator("#bankAccountName").fill("Rekening " + self.getName());
        page.locator("[data-testid='btn-save-profile']").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/self-service/profile");
        assertThat(page.locator("main")).containsText("Profil berhasil diperbarui");
        assertThat(page.locator("main")).containsText("Jl. Mandiri No. 45, Bogor");

        Employee updated = employeeRepository.findById(self.getId()).orElseThrow();
        assertThat(updated.getPhone()).isEqualTo("081299990000");
        assertThat(updated.getAddress()).isEqualTo("Jl. Mandiri No. 45, Bogor");
        assertThat(updated.getBankName()).isEqualTo("BNI");
        assertThat(updated.getBankAccountNumber()).isEqualTo("1122334455");
        assertThat(updated.getBankAccountName()).isEqualTo("Rekening " + self.getName());
        assertThat(updated.getName()).isEqualTo(self.getName());
        assertThat(updated.getNpwp()).isEqualTo(self.getNpwp());
    }

    @Test
    @DisplayName("Should ignore fields outside contact and bank data posted to the profile form")
    void shouldIgnoreProtectedProfileFields() {
        loginAs(self);
        navigateTo("/self-service/profile/edit");
        waitForPageLoad();
        String csrf = page.locator("meta[name='_csrf']").getAttribute("content");

        APIResponse response = page.request().post(baseUrl() + "/self-service/profile/edit",
                RequestOptions.create().setHeader("X-CSRF-TOKEN", csrf).setMaxRedirects(0)
                        .setForm(FormData.create()
                                .set("phone", "081211112222")
                                .set("address", "Alamat Baru")
                                .set("bankName", "BRI")
                                .set("bankAccountNumber", "9988776655")
                                .set("bankAccountName", "Nama Rekening")
                                .set("name", "Nama Palsu")
                                .set("employeeId", colleague.getEmployeeId())
                                .set("npwp", "99.999.999.9-999.999")
                                .set("nikKtp", "9999999999999999")
                                .set("jobTitle", "Direktur")
                                .set("id", colleague.getId().toString())));
        assertThat(response.status()).isEqualTo(302);

        Employee updated = employeeRepository.findById(self.getId()).orElseThrow();
        assertThat(updated.getPhone()).isEqualTo("081211112222");
        assertThat(updated.getBankAccountNumber()).isEqualTo("9988776655");
        assertThat(updated.getName()).isEqualTo(self.getName());
        assertThat(updated.getEmployeeId()).isEqualTo(self.getEmployeeId());
        assertThat(updated.getNpwp()).isEqualTo(self.getNpwp());
        assertThat(updated.getNikKtp()).isEqualTo(self.getNikKtp());
        assertThat(updated.getJobTitle()).isEqualTo(self.getJobTitle());

        Employee untouched = employeeRepository.findById(colleague.getId()).orElseThrow();
        assertThat(untouched.getPhone()).isEqualTo(colleague.getPhone());
        assertThat(untouched.getBankAccountNumber()).isEqualTo(colleague.getBankAccountNumber());
    }

    // ==================== PAYSLIPS ====================

    @Test
    @DisplayName("Should list only the employee's own posted payslips for the selected year")
    void shouldListOwnPostedPayslips() {
        addPayslip(postedRun(), self, "9000000", "450000");
        addPayslip(postedRun(), colleague, "7000000", "350000");
        addPayslip(approvedRun(), self, "9100000", "455000");
        loginAs(self);

        navigateTo("/self-service/payslips?year=" + PAYROLL_YEAR);
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Slip Gaji Saya");
        Locator rows = page.locator("main tbody tr");
        assertThat(rows).hasCount(1);
        assertThat(rows.first().locator("td").nth(0)).hasText("Januari " + PAYROLL_YEAR);
        assertThat(rows.first().locator("td").nth(1)).hasText("9,000,000");
        assertThat(rows.first().locator("td").nth(2)).hasText("450,000");
        assertThat(rows.first().locator("td").nth(3)).hasText("8,550,000");
        assertThat(page.locator("main")).not().containsText("7,000,000");
        takeManualScreenshot("self-service-payslips");

        navigateTo("/self-service/payslips?year=" + (PAYROLL_YEAR - 1));
        waitForPageLoad();
        assertThat(page.locator("main tbody tr")).hasCount(0);
        assertThat(page.locator("main")).containsText("Tidak ada slip gaji untuk tahun " + (PAYROLL_YEAR - 1));
    }

    @Test
    @DisplayName("Should download own payslip PDF but not another employee's")
    void shouldDownloadOnlyOwnPayslip() {
        PayrollDetail own = addPayslip(postedRun(), self, "9000000", "450000");
        PayrollDetail others = addPayslip(postedRun(), colleague, "7000000", "350000");
        loginAs(self);
        navigateTo("/self-service/payslips?year=" + PAYROLL_YEAR);
        waitForPageLoad();

        String href = page.locator("main tbody tr a").getAttribute("href");
        assertThat(href).isEqualTo("/self-service/payslips/" + own.getId() + "/pdf");
        APIResponse pdf = page.request().get(baseUrl() + href);
        assertThat(pdf.status()).isEqualTo(200);
        assertThat(pdf.headers().get("content-type")).isEqualTo("application/pdf");
        assertThat(pdf.headers().get("content-disposition"))
                .isEqualTo("attachment; filename=\"slip-gaji-" + POSTED_PERIOD + "-" + self.getEmployeeId() + ".pdf\"");
        assertThat(new String(Arrays.copyOf(pdf.body(), 5), StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        assertThat(page.request().get(baseUrl() + "/self-service/payslips/" + others.getId() + "/pdf").status())
                .isEqualTo(404);
        assertThat(page.request().get(baseUrl() + "/self-service/payslips/" + UUID.randomUUID() + "/pdf").status())
                .isEqualTo(404);
    }

    @Test
    @DisplayName("Should ignore a userId parameter and list only the logged-in employee's payslips")
    void shouldIgnoreUserIdParameter() {
        addPayslip(postedRun(), self, "9000000", "450000");
        addPayslip(postedRun(), colleague, "7000000", "350000");
        loginAs(self);

        navigateTo("/self-service/payslips?year=" + PAYROLL_YEAR + "&userId=" + colleague.getUser().getId()
                + "&employeeId=" + colleague.getId());
        waitForPageLoad();

        assertThat(page.locator("main")).containsText(self.getName());
        assertThat(page.locator("main")).not().containsText(colleague.getName());
        assertThat(page.locator("main tbody tr")).hasCount(1);
        assertThat(page.locator("main tbody tr td").nth(1)).hasText("9,000,000");
    }

    // ==================== BUKTI POTONG ====================

    @Test
    @DisplayName("Should offer the 1721-A1 PDF for a year with posted payroll and 404 for a year without")
    void shouldDownloadBuktiPotong() {
        addPayslip(postedRun(), self, "9000000", "450000");
        loginAs(self);

        navigateTo("/self-service/bukti-potong?year=" + PAYROLL_YEAR);
        waitForPageLoad();
        assertThat(page.locator("main h3")).hasText("Bukti Potong 1721-A1 Tahun " + PAYROLL_YEAR);
        assertThat(page.locator("main")).containsText("NPWP: " + self.getNpwp());
        String href = page.locator("main a:has-text('Download PDF')").getAttribute("href");
        assertThat(href).isEqualTo("/self-service/bukti-potong/" + PAYROLL_YEAR + "/pdf");
        takeManualScreenshot("self-service-bukti-potong");

        APIResponse pdf = page.request().get(baseUrl() + href);
        assertThat(pdf.status()).isEqualTo(200);
        assertThat(pdf.headers().get("content-disposition")).isEqualTo(
                "attachment; filename=\"bukti-potong-1721-A1-" + PAYROLL_YEAR + "-" + self.getEmployeeId() + ".pdf\"");
        assertThat(new String(Arrays.copyOf(pdf.body(), 5), StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        int emptyYear = PAYROLL_YEAR - 1;
        navigateTo("/self-service/bukti-potong?year=" + emptyYear);
        waitForPageLoad();
        assertThat(page.locator("main")).containsText("Tidak ada data payroll untuk tahun " + emptyYear);
        assertThat(page.locator("main a:has-text('Download PDF')")).hasCount(0);
        assertThat(page.request().get(baseUrl() + "/self-service/bukti-potong/" + emptyYear + "/pdf").status())
                .isEqualTo(404);
    }

    @Test
    @DisplayName("Should not count another employee's or unposted payroll in the bukti potong")
    void shouldNotOfferBuktiPotongFromOtherPayroll() {
        addPayslip(postedRun(), colleague, "7000000", "350000");
        addPayslip(approvedRun(), self, "9100000", "455000");
        loginAs(self);

        navigateTo("/self-service/bukti-potong?year=" + PAYROLL_YEAR);
        waitForPageLoad();
        assertThat(page.locator("main")).containsText("Tidak ada data payroll untuk tahun " + PAYROLL_YEAR);
        assertThat(page.request().get(baseUrl() + "/self-service/bukti-potong/" + PAYROLL_YEAR + "/pdf").status())
                .isEqualTo(404);
    }

    @Test
    @DisplayName("Should deny an employee access to the employee administration pages")
    void shouldDenyEmployeeAdministration() {
        loginAs(self);

        for (String path : new String[] {"/employees", "/employees/" + colleague.getEmployeeId(),
                "/employees/" + self.getEmployeeId() + "/edit", "/payroll"}) {
            Response response = page.navigate(baseUrl() + path);
            assertThat(response.status()).as(path).isEqualTo(403);
            assertThat(page.locator("main, body").first()).not().containsText(colleague.getName());
        }
    }

    // ==================== helpers ====================

    private void loginAs(Employee employee) {
        login(employee.getUser().getUsername(), PASSWORD);
    }

    private Employee createLinkedEmployee() {
        User user = createEmployeeUser();
        String marker = uid();
        Employee employee = new Employee();
        employee.setEmployeeId("SS-" + marker);
        employee.setName("Karyawan Mandiri " + marker);
        employee.setEmail("mandiri-" + marker + "@example.com");
        employee.setPhone("0813" + String.format("%08d", RANDOM.nextInt(100_000_000)));
        employee.setAddress("Jl. Awal No. 1");
        employee.setNpwp("02.345.678.9-" + String.format("%03d", RANDOM.nextInt(1000)) + ".000");
        employee.setNikKtp("32010" + String.format("%011d", RANDOM.nextLong(100_000_000_000L)));
        employee.setBankName("BCA");
        employee.setBankAccountNumber(String.format("%010d", RANDOM.nextLong(10_000_000_000L)));
        employee.setBankAccountName("Karyawan Mandiri " + marker);
        employee.setJobTitle("Staf Operasional");
        employee.setDepartment("Operasional");
        employee.setHireDate(LocalDate.of(2045, 1, 2));
        employee.setUser(user);
        return employeeRepository.save(employee);
    }

    private User createEmployeeUser() {
        String id = uid();
        User user = new User();
        user.setUsername("selfsvc-" + id);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setFullName("Pengguna Mandiri " + id);
        user.setEmail("selfsvc-" + id + "@example.com");
        user.setActive(true);
        user.addRole(Role.EMPLOYEE, "system");
        return userRepository.save(user);
    }

    private PayrollRun postedRun() {
        return run(POSTED_PERIOD, PayrollStatus.POSTED);
    }

    private PayrollRun approvedRun() {
        return run(APPROVED_PERIOD, PayrollStatus.APPROVED);
    }

    private PayrollRun run(String period, PayrollStatus status) {
        return payrollRunRepository.findByPayrollPeriod(period).orElseGet(() -> {
            LocalDate start = LocalDate.parse(period + "-01");
            PayrollRun run = new PayrollRun();
            run.setPayrollPeriod(period);
            run.setPeriodStart(start);
            run.setPeriodEnd(start.withDayOfMonth(start.lengthOfMonth()));
            run.setStatus(status);
            if (status == PayrollStatus.POSTED) {
                run.setPostedAt(LocalDateTime.now());
            }
            return payrollRunRepository.save(run);
        });
    }

    private PayrollDetail addPayslip(PayrollRun run, Employee employee, String gross, String deductions) {
        PayrollDetail detail = new PayrollDetail();
        detail.setPayrollRun(run);
        detail.setEmployee(employee);
        detail.setBaseSalary(new BigDecimal(gross));
        detail.setGrossSalary(new BigDecimal(gross));
        detail.setTotalDeductions(new BigDecimal(deductions));
        detail.setNetPay(new BigDecimal(gross).subtract(new BigDecimal(deductions)));
        detail.setPph21(new BigDecimal(deductions));
        return payrollDetailRepository.save(detail);
    }

    private static String uid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
