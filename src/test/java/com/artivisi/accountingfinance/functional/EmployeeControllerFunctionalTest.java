package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.Employee;
import com.artivisi.accountingfinance.entity.EmploymentStatus;
import com.artivisi.accountingfinance.entity.EmploymentType;
import com.artivisi.accountingfinance.entity.PtkpStatus;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.EmployeeRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.service.EmployeeService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EmployeeController: list with search, employment-status and active filters (HTMX and URL
 * parameters) and pagination, new/edit form, create/update with validation, detail page with
 * masked PII, and deactivate/activate.
 *
 * PII field encryption is enabled for this class so NIK KTP, NPWP, bank account and BPJS numbers
 * are verified to be stored as ciphertext and to round-trip through the form in clear text.
 *
 * Every test works on employees and users it creates, so the seeded EMP-001..003 used by payroll
 * tests are never read for assertions or changed.
 */
@DisplayName("Employee Controller Tests")
@Import(ServiceTestDataInitializer.class)
@TestPropertySource(properties = "app.encryption.key=Kea3OzaaPR89QOebS1b427C8xxRoztOft8rlSEL3W7k=")
class EmployeeControllerFunctionalTest extends PlaywrightTestBase {

    private static final LocalDate HIRE_DATE = LocalDate.of(2024, 1, 15);

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("Should list employee row with NIK, name, email, job title, department, PTKP and status")
    void shouldDisplayEmployeeListPage() {
        Employee employee = createEmployee("Daftar " + shortId(), EmploymentStatus.ACTIVE, true);

        navigateTo("/employees");
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Daftar Karyawan");
        assertThat(page.locator("#btn-new-employee")).hasAttribute("href", "/employees/new");
        assertThat(page.locator("#filter-status")).hasValue("");
        assertThat(page.locator("#filter-active")).hasValue("");
        takeManualScreenshot("employees-list");

        search(employee.getEmployeeId());
        assertThat(employeeRows()).hasCount(1);
        Locator cells = employeeRow(employee).locator("td");
        assertThat(cells.nth(0)).hasText(employee.getEmployeeId());
        assertThat(cells.nth(1).locator("div.font-medium")).hasText(employee.getName());
        assertThat(cells.nth(1).locator("div.text-gray-500")).hasText(employee.getEmail());
        assertThat(cells.nth(2)).hasText(employee.getJobTitle());
        assertThat(cells.nth(3)).hasText(employee.getDepartment());
        assertThat(cells.nth(4)).hasText("K/1");
        assertThat(cells.nth(5)).hasText("Aktif");
        assertThat(cells.nth(6).locator("a:text-is('Lihat')"))
                .hasAttribute("href", "/employees/" + employee.getEmployeeId());
    }

    @Test
    @DisplayName("Should filter list by employment status and active flag via HTMX selects")
    void shouldFilterEmployeesByStatus() {
        String token = shortId();
        Employee active = createEmployee("Aktif " + token, EmploymentStatus.ACTIVE, true);
        Employee resigned = createEmployee("Resign " + token, EmploymentStatus.RESIGNED, true);
        Employee terminated = createEmployee("Phk " + token, EmploymentStatus.TERMINATED, false);

        navigateTo("/employees");
        waitForPageLoad();
        search(token);
        assertThat(employeeRows()).hasCount(3);

        page.locator("#filter-status").selectOption("RESIGNED");
        assertThat(employeeRows()).hasCount(1);
        assertThat(employeeRow(resigned).locator("td").nth(5)).hasText("Resign");

        page.locator("#filter-status").selectOption("TERMINATED");
        assertThat(employeeRows()).hasCount(1);
        assertThat(employeeRow(terminated).locator("td").nth(5)).hasText("PHK Nonaktif");

        page.locator("#filter-status").selectOption("");
        assertThat(employeeRows()).hasCount(3);
        page.locator("#filter-active").selectOption("false");
        assertThat(employeeRows()).hasCount(1);
        assertThat(employeeRow(terminated)).hasCount(1);

        page.locator("#filter-active").selectOption("true");
        assertThat(employeeRows()).hasCount(2);
        assertThat(employeeRow(active)).hasCount(1);
        assertThat(employeeRow(resigned)).hasCount(1);

        page.locator("#filter-active").selectOption("");
        assertThat(employeeRows()).hasCount(3);
    }

    @Test
    @DisplayName("Should search list by name and NIK case-insensitively and show empty state")
    void shouldSearchEmployeesByKeyword() {
        String token = shortId();
        Employee first = createEmployee("Alpha " + token, EmploymentStatus.ACTIVE, true);
        Employee second = createEmployee("Beta " + token, EmploymentStatus.ACTIVE, true);

        navigateTo("/employees");
        waitForPageLoad();
        search(token.toUpperCase(Locale.ROOT));
        assertThat(employeeRows()).hasCount(2);
        assertThat(employeeRow(first)).hasCount(1);
        assertThat(employeeRow(second)).hasCount(1);

        search("beta " + token);
        assertThat(employeeRows()).hasCount(1);
        assertThat(employeeRow(second)).hasCount(1);

        search(first.getEmployeeId().toLowerCase(Locale.ROOT));
        assertThat(employeeRows()).hasCount(1);
        assertThat(employeeRow(first)).hasCount(1);

        search("tidak-ada-" + token);
        assertThat(employeeRows()).hasCount(1);
        assertThat(employeeRows().first()).hasText("Tidak ada data karyawan");
    }

    @Test
    @DisplayName("Should apply search, status and active filters from URL parameters on full page load")
    void shouldFilterEmployeesWithUrlParams() {
        String token = shortId();
        createEmployee("Aktif " + token, EmploymentStatus.ACTIVE, true);
        createEmployee("Resign Aktif " + token, EmploymentStatus.RESIGNED, true);
        Employee resignedInactive = createEmployee("Resign Nonaktif " + token, EmploymentStatus.RESIGNED, false);

        navigateTo("/employees?search=" + token + "&status=RESIGNED&active=false");
        waitForPageLoad();

        assertThat(page.locator("#search-input")).hasValue(token);
        assertThat(page.locator("#filter-status")).hasValue("RESIGNED");
        assertThat(page.locator("#filter-active")).hasValue("false");
        assertThat(employeeRows()).hasCount(1);
        assertThat(employeeRow(resignedInactive)).hasCount(1);
    }

    @Test
    @DisplayName("Should paginate filtered list keeping page size and filters in the page links")
    void shouldPaginateEmployeeList() {
        String token = shortId();
        Employee a = createEmployee("Hal A " + token, EmploymentStatus.ACTIVE, true);
        Employee b = createEmployee("Hal B " + token, EmploymentStatus.ACTIVE, true);
        Employee c = createEmployee("Hal C " + token, EmploymentStatus.ACTIVE, true);

        navigateTo("/employees?search=" + token + "&size=2");
        waitForPageLoad();

        assertThat(employeeRows()).hasCount(2);
        Locator table = page.locator("#employee-table");
        assertThat(table).containsText("Menampilkan 1 - 2 dari 3 karyawan");
        assertThat(table.locator("a:text-is('Sebelumnya')")).hasCount(0);

        table.locator("a:text-is('Selanjutnya')").click();
        waitForPageLoad();

        assertThat(employeeRows()).hasCount(1);
        assertThat(table).containsText("Menampilkan 3 - 3 dari 3 karyawan");
        assertThat(table.locator("a:text-is('Selanjutnya')")).hasCount(0);
        assertThat(table.locator("a:text-is('Sebelumnya')")).hasCount(1);

        String lastOnPageTwo = employeeRows().first().locator("td").nth(0).textContent().trim();
        String expectedLast = java.util.stream.Stream.of(a, b, c).map(Employee::getEmployeeId).sorted()
                .reduce((x, y) -> y).orElseThrow();
        assertEquals(expectedLast, lastOnPageTwo);
    }

    @Test
    @DisplayName("Should return only the employee table fragment for HTMX requests")
    void shouldHandleHtmxRequestForEmployeeList() {
        Employee employee = createEmployee("Htmx " + shortId(), EmploymentStatus.ACTIVE, true);

        APIResponse response = page.request().get(baseUrl() + "/employees?search=" + employee.getEmployeeId(),
                RequestOptions.create().setHeader("HX-Request", "true"));

        assertEquals(200, response.status());
        String body = response.text().trim();
        assertTrue(body.startsWith("<div id=\"employee-table\">"), "Expected table fragment, got: " + body);
        assertFalse(body.contains("<html"), "HTMX response contains full page layout");
        assertFalse(body.contains("page-title"), "HTMX response contains page header");
        assertTrue(body.contains(employee.getEmployeeId()), "HTMX response missing row for " + employee.getEmployeeId());
    }

    @Test
    @DisplayName("Should open new employee form with defaults and active user accounts to link")
    void shouldDisplayNewEmployeeForm() {
        User user = createUser();

        navigateTo("/employees");
        waitForPageLoad();
        page.locator("#btn-new-employee").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/employees/new", page.url());
        assertThat(page.locator("#page-title")).hasText("Karyawan Baru");
        assertThat(form()).hasAttribute("action", "/employees/new");
        for (String field : new String[]{"employeeId", "name", "email", "phone", "address", "nikKtp", "npwp",
                "hireDate", "resignDate", "jobTitle", "department", "bankName", "bankAccountNumber",
                "bankAccountName", "bpjsKesehatanNumber", "bpjsKetenagakerjaanNumber", "notes"}) {
            assertThat(page.locator("#" + field)).hasValue("");
        }
        assertThat(page.locator("#ptkpStatus")).hasValue("TK_0");
        assertThat(page.locator("#employmentType")).hasValue("PERMANENT");
        assertThat(page.locator("#employmentStatus")).hasValue("ACTIVE");
        assertThat(page.locator("#user")).hasValue("");
        assertThat(page.locator("#user option[value='" + user.getId() + "']"))
                .hasText(user.getUsername() + " (" + user.getFullName() + ")");
        assertThat(form().locator("a:text-is('Batal')")).hasAttribute("href", "/employees");
        takeManualScreenshot("employees-form");
    }

    @Test
    @DisplayName("Should create employee with all fields, store PII encrypted and show it masked on detail")
    void shouldCreateEmployeeWithFullDetails() {
        User user = createUser();
        String employeeId = "EC-" + shortId();
        String name = "Karyawan Lengkap " + employeeId;
        String email = employeeId.toLowerCase(Locale.ROOT) + "@example.com";
        String nik = uniqueDigits(16);
        String npwp = uniqueNpwp();
        String bankAccount = uniqueDigits(10);
        String bpjsKes = uniqueDigits(13);
        String bpjsTk = "BPU" + uniqueDigits(10);

        navigateTo("/employees/new");
        waitForPageLoad();
        page.locator("#employeeId").fill(employeeId);
        page.locator("#name").fill(name);
        page.locator("#email").fill(email);
        page.locator("#phone").fill("081234567890");
        page.locator("#address").fill("Jl. Karyawan No. 7 Bandung");
        page.locator("#user").selectOption(user.getId().toString());
        page.locator("#nikKtp").fill(nik);
        page.locator("#npwp").fill(npwp);
        page.locator("#ptkpStatus").selectOption("K_2");
        page.locator("#hireDate").fill(HIRE_DATE.toString());
        page.locator("#employmentType").selectOption("CONTRACT");
        page.locator("#jobTitle").fill("Software Engineer");
        page.locator("#department").fill("Engineering");
        page.locator("#bankName").fill("BCA");
        page.locator("#bankAccountNumber").fill(bankAccount);
        page.locator("#bankAccountName").fill(name);
        page.locator("#bpjsKesehatanNumber").fill(bpjsKes);
        page.locator("#bpjsKetenagakerjaanNumber").fill(bpjsTk);
        page.locator("#notes").fill("Catatan " + employeeId);
        submit();

        assertEquals(baseUrl() + "/employees/" + employeeId, page.url());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Karyawan berhasil ditambahkan");

        Employee saved = employeeRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new AssertionError("Employee not persisted: " + employeeId));
        assertEquals(name, saved.getName());
        assertEquals(email, saved.getEmail());
        assertEquals("081234567890", saved.getPhone());
        assertEquals("Jl. Karyawan No. 7 Bandung", saved.getAddress());
        assertEquals(user.getId(), saved.getUser().getId());
        assertEquals(nik, saved.getNikKtp());
        assertEquals(npwp, saved.getNpwp());
        assertEquals(PtkpStatus.K_2, saved.getPtkpStatus());
        assertEquals(HIRE_DATE, saved.getHireDate());
        assertNull(saved.getResignDate());
        assertEquals(EmploymentType.CONTRACT, saved.getEmploymentType());
        assertEquals(EmploymentStatus.ACTIVE, saved.getEmploymentStatus());
        assertEquals("Software Engineer", saved.getJobTitle());
        assertEquals("Engineering", saved.getDepartment());
        assertEquals("BCA", saved.getBankName());
        assertEquals(bankAccount, saved.getBankAccountNumber());
        assertEquals(name, saved.getBankAccountName());
        assertEquals(bpjsKes, saved.getBpjsKesehatanNumber());
        assertEquals(bpjsTk, saved.getBpjsKetenagakerjaanNumber());
        assertEquals("Catatan " + employeeId, saved.getNotes());
        assertTrue(saved.getActive());

        assertStoredEncrypted(employeeId, Map.of("nik_ktp", nik, "npwp", npwp, "bank_account_number", bankAccount,
                "bpjs_kesehatan_number", bpjsKes, "bpjs_ketenagakerjaan_number", bpjsTk));

        Locator detail = page.locator("[data-testid='employee-detail']");
        assertThat(detail.locator("h2")).hasText(name);
        assertThat(detail.locator("[data-testid='employee-id']")).hasText(employeeId);
        assertThat(detail.locator("[data-testid='active-status']")).hasText("Aktif");
        assertThat(detail.locator("[data-testid='employment-status']")).hasText("Aktif");
        assertThat(infoValue("Email")).hasText(mask(email.substring(0, email.indexOf('@')), 2, 0)
                + email.substring(email.indexOf('@')));
        assertThat(infoValue("Telepon")).hasText("0812*****890");
        assertThat(infoValue("Tanggal Bergabung")).hasText("15 Jan 2024");
        assertThat(infoValue("Alamat")).hasText("Jl. Karyawan No. 7 Bandung");
        assertThat(infoValue("Akun Pengguna (Self-Service)"))
                .hasText(user.getUsername() + " (" + user.getFullName() + ")");
        assertThat(infoValue("Tipe Kepegawaian")).hasText("Kontrak");
        assertThat(infoValue("Jabatan")).hasText("Software Engineer");
        assertThat(infoValue("Departemen")).hasText("Engineering");
        assertThat(infoValue("Status PTKP")).hasText("K/2 - Kawin 2 tanggungan");
        assertThat(infoValue("PTKP Tahunan")).hasText("67,500,000");
        assertThat(infoValue("NIK KTP")).hasText(mask(nik, 4, 4));
        assertThat(infoValue("NPWP")).hasText(mask(npwp, 4, 3));
        assertThat(infoValue("Bank")).hasText("BCA");
        assertThat(infoValue("Nomor Rekening")).hasText(mask(bankAccount, 3, 3));
        assertThat(infoValue("Nama Pemilik")).hasText(name);
        assertThat(infoValue("BPJS Kesehatan")).hasText(mask(bpjsKes, 3, 3));
        assertThat(infoValue("BPJS Ketenagakerjaan")).hasText(mask(bpjsTk, 3, 3));
        assertThat(detail).containsText("Catatan " + employeeId);
        assertThat(detail).not().containsText(nik);
        assertThat(detail).not().containsText(bankAccount);
    }

    @Test
    @DisplayName("Should reject blank name and persist nothing")
    void shouldShowValidationErrorForEmptyName() {
        String employeeId = "EC-" + shortId();

        navigateTo("/employees/new");
        waitForPageLoad();
        page.locator("#btn-simpan").evaluate("b => b.form.noValidate = true");
        page.locator("#employeeId").fill(employeeId);
        page.locator("#hireDate").fill(HIRE_DATE.toString());
        submit();

        assertThat(page.locator("#page-title")).hasText("Karyawan Baru");
        assertThat(page.locator("#name + p.text-red-500")).hasText("Nama karyawan wajib diisi");
        assertThat(page.locator("#employeeId")).hasValue(employeeId);
        assertThat(page.locator("#hireDate")).hasValue(HIRE_DATE.toString());
        assertFalse(employeeRepository.existsByEmployeeId(employeeId), "Employee persisted despite blank name");
    }

    @Test
    @DisplayName("Should reject malformed NIK KTP and NPWP and persist nothing")
    void shouldRejectInvalidNikAndNpwp() {
        String employeeId = "EC-" + shortId();

        navigateTo("/employees/new");
        waitForPageLoad();
        page.locator("#employeeId").fill(employeeId);
        page.locator("#name").fill("Karyawan Invalid " + employeeId);
        page.locator("#hireDate").fill(HIRE_DATE.toString());
        page.locator("#nikKtp").fill("32760123456");
        page.locator("#npwp").fill("12-abc");
        submit();

        assertThat(page.locator("#page-title")).hasText("Karyawan Baru");
        assertThat(page.locator("#nikKtp ~ p.text-red-500")).hasText("NIK KTP harus 16 digit angka");
        assertThat(page.locator("#npwp ~ p.text-red-500")).hasText("NPWP harus 15-20 digit");
        assertThat(page.locator("#nikKtp")).hasValue("32760123456");
        assertFalse(employeeRepository.existsByEmployeeId(employeeId), "Employee persisted despite invalid NIK/NPWP");
    }

    @Test
    @DisplayName("Should reject duplicate employee NIK on create and keep existing employee unchanged")
    void shouldRejectDuplicateEmployeeIdOnCreate() {
        Employee existing = createEmployee("Asli " + shortId(), EmploymentStatus.ACTIVE, true);
        String name = "Duplikat " + shortId();

        navigateTo("/employees/new");
        waitForPageLoad();
        page.locator("#employeeId").fill(existing.getEmployeeId());
        page.locator("#name").fill(name);
        page.locator("#hireDate").fill(HIRE_DATE.toString());
        submit();

        assertThat(page.locator("#page-title")).hasText("Karyawan Baru");
        assertThat(page.locator("#employeeId + p.text-red-500"))
                .hasText("NIK karyawan sudah digunakan: " + existing.getEmployeeId());
        assertThat(page.locator("#name")).hasValue(name);
        assertEquals(existing.getName(),
                employeeRepository.findByEmployeeId(existing.getEmployeeId()).orElseThrow().getName());
        assertFalse(employeeRepository.findAll().stream().anyMatch(e -> name.equals(e.getName())),
                "Employee persisted despite duplicate NIK");
    }

    @Test
    @DisplayName("Should show employee details, masked PII and actions on detail page")
    void shouldDisplayEmployeeDetailPage() {
        Employee employee = createEmployee("Detail " + shortId(), EmploymentStatus.ACTIVE, true);

        navigateTo("/employees");
        waitForPageLoad();
        search(employee.getEmployeeId());
        employeeRow(employee).locator("a:text-is('Lihat')").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/employees/" + employee.getEmployeeId(), page.url());
        assertThat(page.locator("#page-title")).hasText("Detail Karyawan");
        Locator detail = page.locator("[data-testid='employee-detail']");
        assertThat(detail.locator("h2")).hasText(employee.getName());
        assertThat(detail.locator("[data-testid='employee-id']")).hasText(employee.getEmployeeId());
        assertThat(detail.locator("[data-testid='active-status']")).hasText("Aktif");
        assertThat(infoValue("Akun Pengguna (Self-Service)")).hasText("Tidak dikaitkan");
        assertThat(infoValue("Tipe Kepegawaian")).hasText("Tetap");
        assertThat(infoValue("Status PTKP")).hasText("K/1 - Kawin 1 tanggungan");
        assertThat(infoValue("PTKP Tahunan")).hasText("63,000,000");
        assertThat(infoValue("NIK KTP")).hasText(mask(employee.getNikKtp(), 4, 4));
        assertThat(infoValue("NPWP")).hasText(mask(employee.getNpwp(), 4, 3));
        assertThat(infoValue("Nomor Rekening")).hasText(mask(employee.getBankAccountNumber(), 3, 3));
        assertThat(detail.locator("a:text-is('Edit')"))
                .hasAttribute("href", "/employees/" + employee.getEmployeeId() + "/edit");
        assertThat(page.locator("#form-deactivate")).hasCount(1);
        assertThat(page.locator("form[action$='/activate']")).hasCount(0);
    }

    @Test
    @DisplayName("Should open edit form prefilled with decrypted PII, hire date and linked user")
    void shouldDisplayEmployeeEditForm() {
        User user = createUser();
        Employee employee = createEmployee("Edit " + shortId(), EmploymentStatus.ACTIVE, true);
        employee.setUser(user);
        employee.setResignDate(LocalDate.of(2025, 6, 30));
        employeeRepository.save(employee);

        navigateTo("/employees/" + employee.getEmployeeId());
        waitForPageLoad();
        page.locator("[data-testid='employee-detail'] a:text-is('Edit')").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/employees/" + employee.getEmployeeId() + "/edit", page.url());
        assertThat(page.locator("#page-title")).hasText("Edit Karyawan");
        assertThat(form()).hasAttribute("action", "/employees/" + employee.getEmployeeId());
        assertThat(page.locator("#employeeId")).hasValue(employee.getEmployeeId());
        assertThat(page.locator("#name")).hasValue(employee.getName());
        assertThat(page.locator("#email")).hasValue(employee.getEmail());
        assertThat(page.locator("#user")).hasValue(user.getId().toString());
        assertThat(page.locator("#nikKtp")).hasValue(employee.getNikKtp());
        assertThat(page.locator("#npwp")).hasValue(employee.getNpwp());
        assertThat(page.locator("#ptkpStatus")).hasValue("K_1");
        assertThat(page.locator("#hireDate")).hasValue(HIRE_DATE.toString());
        assertThat(page.locator("#resignDate")).hasValue("2025-06-30");
        assertThat(page.locator("#employmentType")).hasValue("PERMANENT");
        assertThat(page.locator("#employmentStatus")).hasValue("ACTIVE");
        assertThat(page.locator("#jobTitle")).hasValue(employee.getJobTitle());
        assertThat(page.locator("#department")).hasValue(employee.getDepartment());
        assertThat(page.locator("#bankName")).hasValue("Mandiri");
        assertThat(page.locator("#bankAccountNumber")).hasValue(employee.getBankAccountNumber());
        assertThat(page.locator("#bpjsKesehatanNumber")).hasValue(employee.getBpjsKesehatanNumber());
        assertThat(form().locator("a:text-is('Batal')"))
                .hasAttribute("href", "/employees/" + employee.getEmployeeId());
    }

    @Test
    @DisplayName("Should update fields, PII, user link and NIK, redirecting to the new NIK")
    void shouldUpdateEmployee() {
        User user = createUser();
        Employee employee = createEmployee("Ubah " + shortId(), EmploymentStatus.ACTIVE, true);
        String newEmployeeId = "EC-" + shortId();
        String newName = "Diperbarui " + newEmployeeId;
        String newNik = uniqueDigits(16);
        String newNpwp = uniqueNpwp();
        String newBankAccount = uniqueDigits(12);

        navigateTo("/employees/" + employee.getEmployeeId() + "/edit");
        waitForPageLoad();
        page.locator("#employeeId").fill(newEmployeeId);
        page.locator("#name").fill(newName);
        page.locator("#user").selectOption(user.getId().toString());
        page.locator("#nikKtp").fill(newNik);
        page.locator("#npwp").fill(newNpwp);
        page.locator("#ptkpStatus").selectOption("TK_0");
        page.locator("#employmentStatus").selectOption("RESIGNED");
        page.locator("#resignDate").fill("2025-12-31");
        page.locator("#jobTitle").fill("Senior Engineer");
        page.locator("#department").fill("R&D");
        page.locator("#bankAccountNumber").fill(newBankAccount);
        submit();

        assertEquals(baseUrl() + "/employees/" + newEmployeeId, page.url());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Karyawan berhasil diperbarui");
        assertThat(page.locator("[data-testid='employee-detail'] h2")).hasText(newName);
        assertThat(page.locator("[data-testid='employment-status']")).hasText("Resign");
        assertThat(infoValue("Tanggal Berhenti")).hasText("31 Des 2025");

        Employee updated = employeeRepository.findByEmployeeId(newEmployeeId)
                .orElseThrow(() -> new AssertionError("Employee not found under new NIK " + newEmployeeId));
        assertEquals(employee.getId(), updated.getId());
        assertEquals(newName, updated.getName());
        assertEquals(user.getId(), updated.getUser().getId());
        assertEquals(newNik, updated.getNikKtp());
        assertEquals(newNpwp, updated.getNpwp());
        assertEquals(PtkpStatus.TK_0, updated.getPtkpStatus());
        assertEquals(EmploymentStatus.RESIGNED, updated.getEmploymentStatus());
        assertEquals(LocalDate.of(2025, 12, 31), updated.getResignDate());
        assertEquals(HIRE_DATE, updated.getHireDate());
        assertEquals("Senior Engineer", updated.getJobTitle());
        assertEquals("R&D", updated.getDepartment());
        assertEquals(newBankAccount, updated.getBankAccountNumber());
        assertEquals(employee.getEmail(), updated.getEmail());
        assertEquals(employee.getBpjsKesehatanNumber(), updated.getBpjsKesehatanNumber());
        assertFalse(employeeRepository.existsByEmployeeId(employee.getEmployeeId()), "Old NIK still present");
        assertStoredEncrypted(newEmployeeId, Map.of("nik_ktp", newNik, "npwp", newNpwp,
                "bank_account_number", newBankAccount));
    }

    @Test
    @DisplayName("Should reject update to another employee's NIK and keep the form bound to the edited employee")
    void shouldRejectUpdateToDuplicateEmployeeId() {
        Employee employee = createEmployee("Ubah " + shortId(), EmploymentStatus.ACTIVE, true);
        Employee other = createEmployee("Lain " + shortId(), EmploymentStatus.ACTIVE, true);
        String fixedEmployeeId = "EC-" + shortId();

        navigateTo("/employees/" + employee.getEmployeeId() + "/edit");
        waitForPageLoad();
        page.locator("#employeeId").fill(other.getEmployeeId());
        page.locator("#jobTitle").fill("Jabatan Baru");
        submit();

        assertThat(page.locator("#page-title")).hasText("Edit Karyawan");
        assertThat(page.locator("#employeeId + p.text-red-500"))
                .hasText("NIK karyawan sudah digunakan: " + other.getEmployeeId());
        assertThat(page.locator("#employeeId")).hasValue(other.getEmployeeId());
        assertThat(form()).hasAttribute("action", "/employees/" + employee.getEmployeeId());
        assertThat(form().locator("a:text-is('Batal')"))
                .hasAttribute("href", "/employees/" + employee.getEmployeeId());

        page.locator("#employeeId").fill(fixedEmployeeId);
        submit();

        assertEquals(baseUrl() + "/employees/" + fixedEmployeeId, page.url());
        Employee updated = employeeRepository.findByEmployeeId(fixedEmployeeId).orElseThrow();
        assertEquals(employee.getId(), updated.getId());
        assertEquals("Jabatan Baru", updated.getJobTitle());
        Employee untouched = employeeRepository.findByEmployeeId(other.getEmployeeId()).orElseThrow();
        assertEquals(other.getId(), untouched.getId());
        assertEquals(other.getName(), untouched.getName());
        assertEquals(other.getJobTitle(), untouched.getJobTitle());
    }

    @Test
    @DisplayName("Should keep employee active when deactivate confirmation is dismissed")
    void shouldKeepEmployeeActiveWhenDeactivateDismissed() {
        Employee employee = createEmployee("Batal Nonaktif " + shortId(), EmploymentStatus.ACTIVE, true);

        navigateTo("/employees/" + employee.getEmployeeId());
        waitForPageLoad();
        page.onceDialog(dialog -> {
            assertEquals("Nonaktifkan karyawan ini?", dialog.message());
            dialog.dismiss();
        });
        page.locator("#form-deactivate button[type='submit']").click();

        assertThat(page.locator("[data-testid='active-status']")).hasText("Aktif");
        assertEquals(baseUrl() + "/employees/" + employee.getEmployeeId(), page.url());
        assertTrue(employeeRepository.findByEmployeeId(employee.getEmployeeId()).orElseThrow().getActive());
    }

    @Test
    @DisplayName("Should deactivate employee after confirmation")
    void shouldDeactivateEmployee() {
        Employee employee = createEmployee("Nonaktif " + shortId(), EmploymentStatus.ACTIVE, true);

        navigateTo("/employees/" + employee.getEmployeeId());
        waitForPageLoad();
        page.onceDialog(dialog -> dialog.accept());
        page.locator("#form-deactivate button[type='submit']").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/employees/" + employee.getEmployeeId(), page.url());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Karyawan berhasil dinonaktifkan");
        assertThat(page.locator("[data-testid='active-status']")).hasText("Nonaktif");
        assertThat(page.locator("#form-deactivate")).hasCount(0);
        assertThat(page.locator("form[action$='/activate']")).hasCount(1);
        Employee saved = employeeRepository.findByEmployeeId(employee.getEmployeeId()).orElseThrow();
        assertFalse(saved.getActive());
        assertEquals(EmploymentStatus.ACTIVE, saved.getEmploymentStatus());
    }

    @Test
    @DisplayName("Should activate inactive employee")
    void shouldActivateEmployee() {
        Employee employee = createEmployee("Aktifkan " + shortId(), EmploymentStatus.ACTIVE, false);

        navigateTo("/employees/" + employee.getEmployeeId());
        waitForPageLoad();
        assertThat(page.locator("[data-testid='active-status']")).hasText("Nonaktif");
        page.locator("form[action$='/activate'] button[type='submit']").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/employees/" + employee.getEmployeeId(), page.url());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Karyawan berhasil diaktifkan");
        assertThat(page.locator("[data-testid='active-status']")).hasText("Aktif");
        assertThat(page.locator("#form-deactivate")).hasCount(1);
        assertTrue(employeeRepository.findByEmployeeId(employee.getEmployeeId()).orElseThrow().getActive());
    }

    // ==================== helpers ====================

    private Employee createEmployee(String name, EmploymentStatus status, boolean active) {
        String id = shortId();
        Employee e = new Employee();
        e.setEmployeeId("EC-" + id);
        e.setName(name);
        e.setEmail("emp-" + id + "@example.com");
        e.setPhone("0813" + uniqueDigits(8));
        e.setNikKtp(uniqueDigits(16));
        e.setNpwp(uniqueNpwp());
        e.setPtkpStatus(PtkpStatus.K_1);
        e.setHireDate(HIRE_DATE);
        e.setEmploymentType(EmploymentType.PERMANENT);
        e.setEmploymentStatus(status);
        e.setJobTitle("Jabatan " + id);
        e.setDepartment("Dept " + id);
        e.setBankName("Mandiri");
        e.setBankAccountNumber(uniqueDigits(10));
        e.setBpjsKesehatanNumber(uniqueDigits(13));
        Employee saved = employeeService.create(e);
        if (!active) {
            employeeService.deactivate(saved.getId());
            saved.setActive(false);
        }
        return saved;
    }

    private User createUser() {
        String id = shortId();
        User user = new User();
        user.setUsername("emp-user-" + id);
        user.setPassword(passwordEncoder.encode("password"));
        user.setFullName("Pengguna " + id);
        user.setEmail("emp-user-" + id + "@example.com");
        user.setActive(true);
        user.addRole(Role.EMPLOYEE, "system");
        return userRepository.save(user);
    }

    private void assertStoredEncrypted(String employeeId, Map<String, String> plainByColumn) {
        String columns = String.join(", ", plainByColumn.keySet());
        Map<String, Object> raw = jdbcTemplate.queryForMap(
                "SELECT " + columns + " FROM employees WHERE employee_id = ?", employeeId);
        plainByColumn.forEach((column, plain) -> {
            String stored = (String) raw.get(column);
            assertTrue(stored.startsWith("ENC:"), column + " not encrypted: " + stored);
            assertFalse(stored.contains(plain), column + " contains plaintext");
        });
    }

    private void search(String keyword) {
        Locator input = page.locator("#search-input");
        input.fill(keyword);
        input.press("End");
    }

    private Locator employeeRows() {
        return page.locator("#employee-table tbody tr");
    }

    private Locator employeeRow(Employee employee) {
        return page.locator("#employee-table tbody tr:has(td:first-child span:text-is('"
                + employee.getEmployeeId() + "'))");
    }

    private Locator infoValue(String label) {
        return page.locator("[data-testid='employee-detail'] div:has(> p.text-gray-500:text-is('" + label
                + "')) > p.font-medium:visible");
    }

    private Locator form() {
        return page.locator("form:has(#btn-simpan)");
    }

    private void submit() {
        page.locator("#btn-simpan").click();
        waitForPageLoad();
    }

    private static String mask(String value, int showFirst, int showLast) {
        return value.substring(0, showFirst) + "*".repeat(value.length() - showFirst - showLast)
                + value.substring(value.length() - showLast);
    }

    private static String uniqueDigits(int length) {
        StringBuilder sb = new StringBuilder(length);
        sb.append(ThreadLocalRandom.current().nextInt(1, 10));
        while (sb.length() < length) {
            sb.append(ThreadLocalRandom.current().nextInt(10));
        }
        return sb.toString();
    }

    private static String uniqueNpwp() {
        String d = uniqueDigits(15);
        return d.substring(0, 2) + "." + d.substring(2, 5) + "." + d.substring(5, 8) + "." + d.charAt(8)
                + "-" + d.substring(9, 12) + "." + d.substring(12, 15);
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
