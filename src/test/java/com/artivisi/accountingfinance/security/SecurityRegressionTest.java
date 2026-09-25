package com.artivisi.accountingfinance.security;

import com.artivisi.accountingfinance.entity.Employee;
import com.artivisi.accountingfinance.entity.PayrollDetail;
import com.artivisi.accountingfinance.entity.PayrollRun;
import com.artivisi.accountingfinance.entity.PayrollStatus;
import com.artivisi.accountingfinance.entity.SecurityAuditLog;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.AuditEventType;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.DocumentRepository;
import com.artivisi.accountingfinance.repository.EmployeeRepository;
import com.artivisi.accountingfinance.repository.PayrollDetailRepository;
import com.artivisi.accountingfinance.repository.PayrollRunRepository;
import com.artivisi.accountingfinance.repository.SecurityAuditLogRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.service.DeviceAuthService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.FilePayload;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import com.microsoft.playwright.options.SameSiteAttribute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Security Regression Tests
 *
 * Tests security controls using Playwright against a running Spring Boot app.
 * These tests verify that security vulnerabilities identified in the audit
 * are properly mitigated. Every check executes unconditionally: a missing
 * page element or precondition fails the test.
 *
 * Run with: ./mvnw test -Dtest=SecurityRegressionTest
 *
 * Uses @ActiveProfiles("test") to load V800-V912 integration test data; the fixed
 * ids below come from those migrations. Users, employees and payroll the tests
 * need beyond that are created by the tests themselves.
 */
@DisplayName("Security Regression Tests")
@ActiveProfiles("test")
class SecurityRegressionTest extends PlaywrightTestBase {

    // V904
    private static final String DRAFT_TRANSACTION_ID = "a0000000-0000-0000-0000-000000000001";
    private static final String POSTED_TRANSACTION_ID = "a0000000-0000-0000-0000-000000000002";
    private static final String VOIDED_TRANSACTION_ID = "a0000000-0000-0000-0000-000000000003";
    // V800
    private static final String TEMPLATE_ID = "e0000000-0000-0000-0000-000000000001";
    private static final String CASH_ACCOUNT_CODE = "1.1.01";
    private static final String REVENUE_ACCOUNT_CODE = "4.1.01";
    // V908
    private static final String OTHER_EMPLOYEE_CODE = "EMP002";
    // Reserved for this class
    private static final String PAYROLL_PERIOD = "2066-01";

    private static final String PASSWORD = "Rahasia#2066x";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired private UserRepository userRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private PayrollRunRepository payrollRunRepository;
    @Autowired private PayrollDetailRepository payrollDetailRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private SecurityAuditLogRepository securityAuditLogRepository;
    @Autowired private DeviceAuthService deviceAuthService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    private String clientIp;

    @BeforeEach
    void setUp() {
        // RateLimitFilter counts logins per client IP; each test gets its own address so the
        // login-heavy tests below cannot exhaust a budget shared with the rest of the class.
        clientIp = randomIp();
        context.setExtraHTTPHeaders(Map.of("X-Forwarded-For", clientIp));
    }

    private void assertForbidden(String path) {
        Response response = page.navigate(baseUrl() + path);
        assertEquals(403, response.status(), "Expected 403 for " + path + ", got " + response.status());
        assertThat(page.locator("#access-denied-page")).isVisible();
    }

    private String csrfToken() {
        String token = page.locator("meta[name='_csrf']").getAttribute("content");
        assertNotNull(token, "CSRF meta tag missing on " + page.url());
        return token;
    }

    private RequestOptions withCsrf() {
        return RequestOptions.create().setHeader("X-CSRF-TOKEN", csrfToken());
    }

    private User createUser(Role role) {
        String id = uid();
        User user = new User();
        user.setUsername("secreg-" + role.name().toLowerCase() + "-" + id);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setFullName("Pengguna " + id);
        user.setEmail("secreg-" + id + "@example.com");
        user.setActive(true);
        user.addRole(role, "system");
        return userRepository.save(user);
    }

    private void submitLogin(String username, String password) {
        navigateTo("/login");
        waitForPageLoad();
        page.fill("input[name='username']", username);
        page.fill("input[name='password']", password);
        page.click("button[type='submit']");
        waitForPageLoad();
    }

    private Cookie sessionCookie() {
        return context.cookies().stream()
                .filter(c -> c.name.equals("JSESSIONID"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("JSESSIONID cookie missing, cookies: " + context.cookies()));
    }

    private int transactionCountByDescription(String description) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE description = ?", Integer.class, description);
        assertNotNull(count);
        return count;
    }

    private String transactionField(String id, String column) {
        return jdbcTemplate.queryForObject("SELECT " + column + " FROM transactions WHERE id = ?::uuid",
                String.class, id);
    }

    private static String randomIp() {
        return "10.99." + RANDOM.nextInt(256) + "." + RANDOM.nextInt(256);
    }

    private static String uid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    @Nested
    @DisplayName("Authentication Security")
    class AuthenticationSecurityTests {

        @Test
        @DisplayName("Should reject invalid credentials")
        void shouldRejectInvalidCredentials() {
            User user = createUser(Role.STAFF);

            submitLogin(user.getUsername(), PASSWORD + "salah");

            assertEquals(baseUrl() + "/login?error", page.url());
            assertThat(page.locator("text=Username atau password salah")).isVisible();
            navigateTo("/dashboard");
            assertEquals(baseUrl() + "/login", page.url());
        }

        @Test
        @DisplayName("Should lock the account after 5 failed logins, rejecting even the correct password")
        void shouldProtectAgainstBruteForce() {
            User user = createUser(Role.STAFF);
            LocalDateTime before = LocalDateTime.now().minusSeconds(1);

            for (int i = 0; i < 5; i++) {
                submitLogin(user.getUsername(), "salah-" + i);
                assertEquals(baseUrl() + "/login?error", page.url());
            }
            assertTrue(loginAttemptService.isBlocked(user.getUsername()), "Account not locked after 5 failures");

            submitLogin(user.getUsername(), PASSWORD);
            assertEquals(baseUrl() + "/login?error", page.url(), "Locked account accepted the correct password");
            assertTrue(securityAuditLogRepository.findByUsernameAndEventTypeAndTimestampAfter(
                            user.getUsername(), AuditEventType.LOGIN_FAILURE, before).stream()
                    .anyMatch(l -> "Account locked - login blocked".equals(l.getDetails())));

            loginAttemptService.resetAllAttempts();
            submitLogin(user.getUsername(), PASSWORD);
            assertEquals(baseUrl() + "/dashboard", page.url());
        }

        @Test
        @DisplayName("Should redirect to login when accessing protected page without auth")
        void shouldRedirectToLoginForProtectedPages() {
            context.clearCookies();

            for (String path : List.of("/dashboard", "/users", "/transactions", "/settings/export")) {
                navigateTo(path);
                assertEquals(baseUrl() + "/login", page.url(), "Unauthenticated access to " + path);
            }
        }

        @Test
        @DisplayName("Should invalidate session on logout")
        void shouldInvalidateSessionOnLogout() {
            loginAsAdmin();
            Cookie session = sessionCookie();

            page.locator("form[action='/logout'] button[title='Logout']").first().click();
            waitForPageLoad();
            assertEquals(baseUrl() + "/login?logout", page.url());
            assertThat(page.locator("text=Anda telah berhasil logout")).isVisible();

            navigateTo("/dashboard");
            assertEquals(baseUrl() + "/login", page.url());

            // Replaying the old session id from another browser must not be authenticated
            BrowserContext replay = browser.newContext();
            try {
                replay.addCookies(List.of(new Cookie(session.name, session.value)
                        .setDomain(session.domain).setPath(session.path)));
                Page replayPage = replay.newPage();
                replayPage.navigate(baseUrl() + "/dashboard");
                assertEquals(baseUrl() + "/login", replayPage.url());
            } finally {
                replay.close();
            }
        }
    }

    @Nested
    @DisplayName("CSRF Protection")
    class CsrfProtectionTests {

        @Test
        @DisplayName("Should include CSRF token in every POST form")
        void shouldIncludeCsrfTokenInForms() {
            loginAsAdmin();
            navigateTo("/accounts/new");
            waitForPageLoad();

            String token = csrfToken();
            assertFalse(token.isBlank());
            int postForms = page.locator("form[method='post']").count();
            assertTrue(postForms >= 2, "Expected the account form and the logout forms, got " + postForms);
            assertEquals(postForms, page.locator("form[method='post'] input[name='_csrf']").count(),
                    "Every POST form must carry the _csrf field");
            assertEquals(token, page.locator("form[action='/accounts/new'] input[name='_csrf']").getAttribute("value"));
        }

        @Test
        @DisplayName("Should expose the CSRF header name for HTMX and accept it on POST")
        void shouldConfigureCsrfForHtmx() {
            loginAsAdmin();
            navigateTo("/dashboard");
            waitForPageLoad();

            assertEquals("X-CSRF-TOKEN", page.locator("meta[name='_csrf_header']").getAttribute("content"));

            // Header-only token (as HTMX sends it) is accepted: the invalid form is re-rendered, not 403
            APIResponse response = page.request().post(baseUrl() + "/accounts/new", withCsrf()
                    .setForm(FormData.create().set("accountCode", "").set("accountName", "")));
            assertEquals(200, response.status());
            assertTrue(response.text().contains("Kode akun harus diisi"));
        }

        @Test
        @DisplayName("Should reject POST request without CSRF token")
        void shouldRejectPostWithoutCsrfToken() {
            loginAsAdmin();
            navigateTo("/accounts/new");
            waitForPageLoad();
            String code = "9.9." + RANDOM.nextInt(100_000);
            FormData form = FormData.create()
                    .set("accountCode", code)
                    .set("accountName", "CSRF " + code)
                    .set("accountType", "ASSET")
                    .set("normalBalance", "DEBIT");

            APIResponse rejected = page.request().post(baseUrl() + "/accounts/new",
                    RequestOptions.create().setForm(form).setMaxRedirects(0));
            assertEquals(403, rejected.status());
            assertTrue(accountRepository.findByAccountCode(code).isEmpty(), "Account created without CSRF token");

            APIResponse accepted = page.request().post(baseUrl() + "/accounts/new",
                    withCsrf().setForm(form).setMaxRedirects(0));
            assertEquals(302, accepted.status(), accepted.text());
            assertTrue(accountRepository.findByAccountCode(code).isPresent());
        }
    }

    @Nested
    @DisplayName("XSS Prevention")
    class XssPreventionTests {

        private static final List<String> XSS_PAYLOADS = List.of(
                "<script>window.__xss=1</script>",
                "<img src=x id=xss-probe onerror=window.__xss=1>",
                "<svg id=xss-probe onload=window.__xss=1>",
                "\"><img src=x id=xss-probe>",
                "'><img src=x id=xss-probe>",
                "javascript:window.__xss=1"
        );

        @Test
        @DisplayName("Should render search input echoed back as text, not markup")
        void shouldEscapeXssInSearch() {
            loginAsAdmin();
            AtomicBoolean dialog = new AtomicBoolean();
            page.onDialog(d -> {
                dialog.set(true);
                d.dismiss();
            });

            for (String payload : XSS_PAYLOADS) {
                navigateTo("/users");
                waitForPageLoad();
                page.locator("#search-user").fill(payload);
                page.locator("#btn-search").click();
                waitForPageLoad();

                assertThat(page.locator("#search-user")).hasValue(payload);
                assertThat(page.locator("#xss-probe")).hasCount(0);
                assertEquals(Boolean.TRUE, page.evaluate("() => window.__xss === undefined"), "Executed: " + payload);
            }
            assertFalse(dialog.get());
        }

        @Test
        @DisplayName("Should escape user input in displayed content")
        void shouldEscapeUserInputInDisplay() {
            loginAsAdmin();
            String username = "xss-" + uid();
            String fullName = "<img src=x id=xss-probe onerror=window.__xss=1>";

            navigateTo("/users/new");
            waitForPageLoad();
            page.locator("#username").fill(username);
            page.locator("#password").fill(PASSWORD);
            page.locator("#fullName").fill(fullName);
            page.locator("input[name='selectedRoles'][value='STAFF']").check();
            page.locator("#btn-save").click();
            waitForPageLoad();
            User created = userRepository.findByUsername(username).orElseThrow();
            assertEquals(fullName, created.getFullName());

            navigateTo("/users?search=" + username);
            waitForPageLoad();
            assertThat(page.locator("#user-" + created.getId())).containsText(fullName);
            assertThat(page.locator("#xss-probe")).hasCount(0);

            navigateTo("/users/" + created.getId());
            waitForPageLoad();
            assertThat(page.locator("#page-title")).hasText(fullName);
            assertThat(page.locator("#xss-probe")).hasCount(0);
            assertEquals(Boolean.TRUE, page.evaluate("() => window.__xss === undefined"));
        }
    }

    @Nested
    @DisplayName("Security Headers")
    class SecurityHeaderTests {

        @Test
        @DisplayName("Should include X-Content-Type-Options header")
        void shouldIncludeXContentTypeOptions() {
            Response response = page.navigate(baseUrl() + "/login");
            assertEquals("nosniff", response.headers().get("x-content-type-options"));
        }

        @Test
        @DisplayName("Should include X-Frame-Options header")
        void shouldIncludeXFrameOptions() {
            Response response = page.navigate(baseUrl() + "/login");
            assertEquals("DENY", response.headers().get("x-frame-options"));
        }

        @Test
        @DisplayName("Should include Content-Security-Policy header with a per-response nonce")
        void shouldIncludeContentSecurityPolicy() {
            String first = page.navigate(baseUrl() + "/login").headers().get("content-security-policy");
            String second = page.navigate(baseUrl() + "/login").headers().get("content-security-policy");

            assertNotNull(first, "Content-Security-Policy header missing");
            assertTrue(first.startsWith("default-src 'self'; script-src 'self' 'nonce-"), first);
            assertTrue(first.contains("frame-ancestors 'none'"), first);
            assertTrue(first.contains("form-action 'self'"), first);
            assertFalse(first.contains("'unsafe-inline'") || first.contains("'unsafe-eval'"), first);
            assertNotEquals(first, second, "CSP nonce must differ per response");
        }

        @Test
        @DisplayName("Should include Referrer-Policy header")
        void shouldIncludeReferrerPolicy() {
            Response response = page.navigate(baseUrl() + "/login");
            assertEquals("strict-origin-when-cross-origin", response.headers().get("referrer-policy"));
        }

        @Test
        @DisplayName("Should disable the legacy XSS auditor via X-XSS-Protection: 0")
        void shouldIncludeXssProtection() {
            Response response = page.navigate(baseUrl() + "/login");
            assertEquals("0", response.headers().get("x-xss-protection"));
        }
    }

    @Nested
    @DisplayName("Session Security")
    class SessionSecurityTests {

        @Test
        @DisplayName("Should use HttpOnly SameSite=Strict session cookies")
        void shouldUseSecureSessionCookies() {
            loginAsAdmin();

            Cookie session = sessionCookie();
            assertTrue(session.httpOnly, "Session cookie must be HttpOnly");
            assertEquals(SameSiteAttribute.STRICT, session.sameSite);
        }

        @Test
        @DisplayName("Should generate new session ID after login")
        void shouldRegenerateSessionAfterLogin() {
            navigateTo("/login");
            waitForPageLoad();
            String before = sessionCookie().value;

            loginAsAdmin();

            assertNotEquals(before, sessionCookie().value, "Session ID must be regenerated after login");
        }
    }

    @Nested
    @DisplayName("Authorization Controls")
    class AuthorizationTests {

        @Test
        @DisplayName("Admin should have access to user management")
        void adminShouldAccessUserManagement() {
            loginAsAdmin();
            User admin = userRepository.findByUsername("admin").orElseThrow();

            navigateTo("/users");
            waitForPageLoad();

            assertThat(page.locator("#page-title")).hasText("Kelola Pengguna");
            assertThat(page.locator("#user-" + admin.getId())).containsText("Administrator");

            takeManualScreenshot("users-list");
        }

        @Test
        @DisplayName("SECURITY: Staff should NOT have access to user management")
        void staffShouldNotAccessUserManagement() {
            login("staff", "admin");
            assertForbidden("/users");
            assertForbidden("/users/new");
        }

        @Test
        @DisplayName("SECURITY: Employee should NOT have access to payroll")
        void employeeShouldNotAccessPayroll() {
            login("employee", "admin");
            assertForbidden("/payroll");
            assertForbidden("/payroll/new");
        }

        @Test
        @DisplayName("SECURITY: Auditor should NOT be able to create transactions")
        void auditorShouldNotCreateTransactions() {
            login("auditor", "admin");
            assertForbidden("/transactions/new");

            navigateTo("/transactions/" + DRAFT_TRANSACTION_ID);
            String description = "Auditor " + uid();
            APIResponse response = page.request().post(baseUrl() + "/transactions/api", withCsrf()
                    .setData(Map.of("transactionDate", LocalDate.now().toString(), "templateId", TEMPLATE_ID,
                            "amount", 1000, "description", description)));
            assertEquals(403, response.status());
            assertEquals(0, transactionCountByDescription(description));
        }

        @Test
        @DisplayName("SECURITY: Staff should NOT see POST transaction button")
        void staffShouldNotPostTransaction() {
            loginAsAdmin();
            navigateTo("/transactions/" + DRAFT_TRANSACTION_ID);
            waitForPageLoad();
            assertThat(page.locator("[data-testid='status-draft']")).isVisible();
            assertThat(page.locator("#btn-post")).isVisible();

            login("staff", "admin");
            navigateTo("/transactions/" + DRAFT_TRANSACTION_ID);
            waitForPageLoad();

            assertThat(page.locator("#transaction-number")).hasText("TRX-TEST-0001");
            assertThat(page.locator("[data-testid='status-draft']")).isVisible();
            assertThat(page.locator("#btn-post")).hasCount(0);
            assertThat(page.locator("#btn-delete")).hasCount(0);
            assertThat(page.locator("a[href='/transactions/" + DRAFT_TRANSACTION_ID + "/edit']")).hasCount(0);

            APIResponse post = page.request().post(baseUrl() + "/transactions/api/" + DRAFT_TRANSACTION_ID + "/post",
                    withCsrf());
            assertEquals(403, post.status());
            assertEquals("DRAFT", transactionField(DRAFT_TRANSACTION_ID, "status"));
        }

        @Test
        @DisplayName("SECURITY: Employee should NOT access dashboard")
        void employeeShouldNotAccessDashboard() {
            login("employee", "admin");
            assertForbidden("/dashboard");
        }

        @Test
        @DisplayName("SECURITY: Employee should NOT access other employee's profile")
        void employeeShouldNotAccessOtherEmployeeProfile() {
            Employee other = employeeRepository.findByEmployeeId(OTHER_EMPLOYEE_CODE)
                    .orElseThrow(() -> new IllegalStateException("Seeded employee missing: " + OTHER_EMPLOYEE_CODE));
            login("employee", "admin");

            assertForbidden("/employees/" + OTHER_EMPLOYEE_CODE);
            assertFalse(page.content().contains(other.getName()));
            assertForbidden("/employees/" + OTHER_EMPLOYEE_CODE + "/edit");
            assertForbidden("/settings/data-subjects/" + other.getId());
        }

        @Test
        @DisplayName("SECURITY: Employee accessing payslips with other userId should see own data only")
        void employeeShouldNotAccessOtherUserPayslips() {
            User ownUser = createUser(Role.EMPLOYEE);
            User otherUser = createUser(Role.EMPLOYEE);
            Employee own = createEmployee(ownUser);
            Employee other = createEmployee(otherUser);
            PayrollDetail ownPayslip = addPayslip(own, "6000000");
            PayrollDetail otherPayslip = addPayslip(other, "9500000");
            login(ownUser.getUsername(), PASSWORD);

            navigateTo("/self-service/payslips?year=2066&userId=" + otherUser.getId() + "&employeeId=" + other.getId());
            waitForPageLoad();

            assertThat(page.locator("main")).containsText(own.getName());
            assertThat(page.locator("main")).not().containsText(other.getName());
            assertThat(page.locator("main tbody tr")).hasCount(1);
            assertThat(page.locator("main tbody tr td").nth(1)).hasText("6,000,000");

            assertEquals(200, page.request().get(baseUrl() + "/self-service/payslips/" + ownPayslip.getId() + "/pdf").status());
            assertEquals(404, page.request().get(baseUrl() + "/self-service/payslips/" + otherPayslip.getId() + "/pdf").status());
        }

        @Test
        @DisplayName("SECURITY: Staff should NOT modify transactions")
        void staffShouldNotModifyOtherUserTransactions() {
            login("staff", "admin");
            String description = transactionField(DRAFT_TRANSACTION_ID, "description");

            assertForbidden("/transactions/" + DRAFT_TRANSACTION_ID + "/edit");

            navigateTo("/transactions/" + DRAFT_TRANSACTION_ID);
            APIResponse update = page.request().put(baseUrl() + "/transactions/api/" + DRAFT_TRANSACTION_ID, withCsrf()
                    .setData(Map.of("transactionDate", LocalDate.now().toString(), "templateId", TEMPLATE_ID,
                            "amount", 1, "description", "Diubah staff")));
            assertEquals(403, update.status());
            APIResponse delete = page.request().delete(baseUrl() + "/transactions/api/" + DRAFT_TRANSACTION_ID, withCsrf());
            assertEquals(403, delete.status());
            assertEquals(description, transactionField(DRAFT_TRANSACTION_ID, "description"));
            assertEquals("DRAFT", transactionField(DRAFT_TRANSACTION_ID, "status"));
        }

        private Employee createEmployee(User user) {
            String marker = uid();
            Employee employee = new Employee();
            employee.setEmployeeId("SR-" + marker);
            employee.setName("Karyawan " + marker);
            employee.setHireDate(LocalDate.of(2045, 1, 2));
            employee.setUser(user);
            return employeeRepository.save(employee);
        }

        private PayrollDetail addPayslip(Employee employee, String gross) {
            PayrollRun run = payrollRunRepository.findByPayrollPeriod(PAYROLL_PERIOD).orElseGet(() -> {
                PayrollRun created = new PayrollRun();
                created.setPayrollPeriod(PAYROLL_PERIOD);
                created.setPeriodStart(LocalDate.of(2066, 1, 1));
                created.setPeriodEnd(LocalDate.of(2066, 1, 31));
                created.setStatus(PayrollStatus.POSTED);
                created.setPostedAt(LocalDateTime.now());
                return payrollRunRepository.save(created);
            });
            PayrollDetail detail = new PayrollDetail();
            detail.setPayrollRun(run);
            detail.setEmployee(employee);
            detail.setBaseSalary(new BigDecimal(gross));
            detail.setGrossSalary(new BigDecimal(gross));
            detail.setNetPay(new BigDecimal(gross));
            return payrollDetailRepository.save(detail);
        }
    }

    @Nested
    @DisplayName("Input Validation")
    class InputValidationTests {

        @Test
        @DisplayName("Should validate required fields server-side")
        void shouldValidateRequiredFields() {
            loginAsAdmin();
            long accounts = accountRepository.count();
            navigateTo("/accounts/new");
            waitForPageLoad();

            // Bypass the browser's required-attribute check so the server validation is exercised
            page.locator("form[action='/accounts/new']").evaluate("f => f.noValidate = true");
            page.click("#btn-simpan");
            waitForPageLoad();

            assertEquals(baseUrl() + "/accounts/new", page.url());
            assertThat(page.locator("#validation-errors")).isVisible();
            assertThat(page.locator("#validation-errors")).containsText("Kode akun harus diisi");
            assertThat(page.locator("#validation-errors")).containsText("Nama akun harus diisi");
            assertThat(page.locator("#validation-errors")).containsText("Tipe akun harus diisi untuk akun tanpa induk");
            assertEquals(accounts, accountRepository.count());
        }

        @Test
        @DisplayName("Should treat SQL injection attempts in search as literal text")
        void shouldRejectSqlInjection() {
            loginAsAdmin();
            long users = userRepository.count();
            List<String> sqlPayloads = List.of(
                    "' OR '1'='1",
                    "'; DROP TABLE users; --",
                    "1; SELECT * FROM users",
                    "' UNION SELECT * FROM users --"
            );

            for (String payload : sqlPayloads) {
                navigateTo("/users?search=" + java.net.URLEncoder.encode(payload, StandardCharsets.UTF_8));
                waitForPageLoad();

                assertThat(page.locator("#page-title")).hasText("Kelola Pengguna");
                assertThat(page.locator("tbody")).containsText("Tidak ada pengguna ditemukan");
                String content = page.content().toLowerCase();
                assertFalse(content.contains("sqlexception") || content.contains("psqlexception")
                        || content.contains("syntax error"), "SQL error exposed for: " + payload);
            }

            assertEquals(users, userRepository.count());
            navigateTo("/users?search=admin");
            waitForPageLoad();
            assertThat(page.locator("#user-" + userRepository.findByUsername("admin").orElseThrow().getId())).isVisible();
        }

        @Test
        @DisplayName("SECURITY: Should render template expressions in user data literally")
        void shouldEscapeTemplateInjection() {
            loginAsAdmin();
            List<String> templatePayloads = List.of(
                    "${7*7}",
                    "[[${7*7}]]",
                    "__${7*7}__::.x",
                    "#{7*7}",
                    "*{7*7}",
                    "@{/malicious}",
                    "~{::script}",
                    "${T(java.lang.Runtime).getRuntime().exec('id')}",
                    "{{7*7}}",
                    "<#assign ex=\"freemarker.template.utility.Execute\"?new()>${ex(\"id\")}"
            );

            for (String payload : templatePayloads) {
                User user = new User();
                user.setUsername("ssti-" + uid());
                user.setPassword(passwordEncoder.encode(PASSWORD));
                user.setFullName(payload);
                user.setActive(true);
                user.addRole(Role.STAFF, "system");
                user = userRepository.save(user);

                Response response = page.navigate(baseUrl() + "/users/" + user.getId());
                assertEquals(200, response.status(), "Payload broke rendering: " + payload);
                assertThat(page.locator("#page-title")).hasText(payload);

                navigateTo("/users?search=" + java.net.URLEncoder.encode(payload, StandardCharsets.UTF_8));
                waitForPageLoad();
                assertThat(page.locator("#search-user")).hasValue(payload);
                assertThat(page.locator("#user-" + user.getId())).containsText(payload);
            }
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        private void assertNoInternals(String content) {
            String lower = content.toLowerCase();
            assertFalse(lower.contains("stacktrace") || lower.contains("exception"), "Exception details exposed");
            assertFalse(lower.contains("at com.artivisi") || lower.contains("com.artivisi."), "Package names exposed");
            assertFalse(lower.contains("hibernate") || lower.contains("jdbc") || lower.contains("postgresql"),
                    "Persistence details exposed");
        }

        @Test
        @DisplayName("Should show a generic 404 page for unknown paths")
        void shouldNotExposeStackTraces() {
            loginAsAdmin();

            Response response = page.navigate(baseUrl() + "/nonexistent-page-12345");

            assertEquals(404, response.status());
            assertThat(page.locator("#error-404-page")).isVisible();
            assertNoInternals(page.content());
        }

        @Test
        @DisplayName("Should show generic error messages for malformed ids")
        void shouldShowGenericErrorMessages() {
            loginAsAdmin();

            Response response = page.navigate(baseUrl() + "/accounts/nonexistent-uuid-12345/edit");

            assertEquals(400, response.status());
            assertNoInternals(page.content());
            assertFalse(page.content().contains("nonexistent-uuid-12345"), "Request value echoed in error");
        }

        @Test
        @DisplayName("Should not expose database query in error")
        void shouldNotExposeDatabaseQueryInError() {
            loginAsAdmin();

            Response response = page.navigate(baseUrl() + "/users?search="
                    + java.net.URLEncoder.encode("' OR 1=1 --", StandardCharsets.UTF_8) + "&page=-1");

            assertEquals(400, response.status());
            String content = page.content().toLowerCase();
            assertFalse(content.contains("select ") || content.contains(" where "), "SQL exposed");
            assertNoInternals(content);
        }

        @Test
        @DisplayName("Should reject path traversal URLs without exposing file paths")
        void shouldNotExposeFilePathsInError() {
            loginAsAdmin();

            APIResponse response = page.request().get(baseUrl() + "/documents/%2e%2e/%2e%2e/%2e%2e/etc/passwd");

            assertEquals(404, response.status());
            String content = response.text().toLowerCase();
            assertFalse(content.contains("root:"), "File content served");
            assertFalse(content.contains("/etc/") || content.contains("/var/") || content.contains("/home/")
                    || content.contains("/users/"), "File system path exposed");
        }
    }

    @Nested
    @DisplayName("File Upload Security")
    class FileUploadSecurityTests {

        private static final String TEST_TRANSACTION_ID = DRAFT_TRANSACTION_ID;
        private static final byte[] PDF = ("%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF")
                .getBytes(StandardCharsets.US_ASCII);

        private APIResponse upload(String filename, String contentType, byte[] content) {
            navigateTo("/transactions/" + TEST_TRANSACTION_ID);
            waitForPageLoad();
            return page.request().post(baseUrl() + "/documents/transaction/" + TEST_TRANSACTION_ID, withCsrf()
                    .setMultipart(FormData.create().set("file", new FilePayload(filename, contentType, content))));
        }

        private void assertRejected(APIResponse response, String escapedMessage, String filename) {
            assertEquals(200, response.status(), response.text());
            String body = response.text();
            assertTrue(body.contains("data-testid=\"document-upload-error\""), body);
            assertTrue(body.contains(escapedMessage), "Expected '" + escapedMessage + "' in: " + body);
            assertNotStored(filename);
        }

        private void assertNotStored(String filename) {
            assertTrue(documentRepository.findByTransactionIdOrderByCreatedAtDesc(UUID.fromString(TEST_TRANSACTION_ID))
                            .stream().noneMatch(d -> filename.equals(d.getOriginalFilename())),
                    "Rejected file was stored: " + filename);
        }

        @Test
        @DisplayName("SECURITY: Should reject path traversal filename in upload")
        void shouldRejectPathTraversalFilename() {
            loginAsAdmin();
            String filename = "../../../etc/passwd-" + uid() + ".pdf";

            assertRejected(upload(filename, "application/pdf", PDF), "Invalid filename: " + filename, filename);
        }

        @Test
        @DisplayName("SECURITY: Should reject executable file upload")
        void shouldRejectExecutableFile() {
            loginAsAdmin();
            String filename = "malware-" + uid() + ".exe";

            assertRejected(upload(filename, "application/x-msdownload", new byte[] {0x4D, 0x5A, 0x00, 0x00}),
                    "File type &#39;application/x-msdownload&#39; is not allowed", filename);
        }

        @Test
        @DisplayName("SECURITY: Should reject content-type spoofing (EXE disguised as PDF)")
        void shouldRejectContentTypeSpoofing() {
            loginAsAdmin();
            String filename = "document-" + uid() + ".pdf";

            assertRejected(upload(filename, "application/pdf", new byte[] {0x4D, 0x5A, 0x00, 0x00}),
                    "File content does not match declared type. Possible content-type spoofing detected.", filename);
        }

        @Test
        @DisplayName("SECURITY: Should reject empty filename in upload")
        void shouldRejectEmptyFilename() {
            loginAsAdmin();

            assertRejected(upload("", "application/pdf", PDF), "Invalid filename: ", "");
        }

        @Test
        @DisplayName("Should accept valid PDF upload")
        void shouldAcceptValidPdfUpload() {
            loginAsAdmin();
            String filename = "valid-" + uid() + ".pdf";

            APIResponse response = upload(filename, "application/pdf", PDF);

            assertEquals(200, response.status());
            assertTrue(response.text().contains("Dokumen berhasil diunggah"), response.text());
            assertTrue(documentRepository.findByTransactionIdOrderByCreatedAtDesc(UUID.fromString(TEST_TRANSACTION_ID))
                    .stream().anyMatch(d -> filename.equals(d.getOriginalFilename())
                            && d.getFileSize() == PDF.length));
        }

        @Test
        @DisplayName("Should accept a PDF larger than 1MB and within the 10MB document limit")
        void shouldAcceptPdfWithinConfiguredLimit() {
            loginAsAdmin();
            String filename = "large-" + uid() + ".pdf";
            byte[] content = new byte[2 * 1024 * 1024];
            System.arraycopy(PDF, 0, content, 0, PDF.length);

            APIResponse response = upload(filename, "application/pdf", content);

            assertEquals(200, response.status());
            assertTrue(response.text().contains("Dokumen berhasil diunggah"), response.text());
            assertTrue(documentRepository.findByTransactionIdOrderByCreatedAtDesc(UUID.fromString(TEST_TRANSACTION_ID))
                    .stream().anyMatch(d -> filename.equals(d.getOriginalFilename())));
        }

        @Test
        @DisplayName("SECURITY: Should reject oversized file upload (>10MB)")
        void shouldRejectOversizedFile() {
            loginAsAdmin();
            String filename = "oversized-" + uid() + ".pdf";
            byte[] content = new byte[11 * 1024 * 1024];
            System.arraycopy(PDF, 0, content, 0, PDF.length);

            APIResponse response = upload(filename, "application/pdf", content);

            assertEquals(413, response.status());
            assertNotStored(filename);
        }
    }

    @Nested
    @DisplayName("Business Logic Security")
    class BusinessLogicSecurityTests {

        private Map<String, Object> transactionBody(String description, Object amount) {
            return Map.of("transactionDate", LocalDate.now().toString(), "templateId", TEMPLATE_ID,
                    "amount", amount, "description", description);
        }

        @Test
        @DisplayName("Should not allow editing posted transaction via direct URL or API")
        void shouldNotAllowEditingPostedTransaction() {
            loginAsAdmin();
            String description = transactionField(POSTED_TRANSACTION_ID, "description");

            navigateTo("/transactions/" + POSTED_TRANSACTION_ID + "/edit");
            waitForPageLoad();
            assertEquals(baseUrl() + "/transactions/" + POSTED_TRANSACTION_ID, page.url());
            assertThat(page.locator("[data-testid='status-posted']")).isVisible();

            APIResponse update = page.request().put(baseUrl() + "/transactions/api/" + POSTED_TRANSACTION_ID,
                    withCsrf().setData(transactionBody("Diubah setelah posting", 1)));
            assertEquals(409, update.status());
            assertEquals(description, transactionField(POSTED_TRANSACTION_ID, "description"));
            assertEquals("POSTED", transactionField(POSTED_TRANSACTION_ID, "status"));
        }

        @Test
        @DisplayName("Should not allow editing voided transaction via direct URL or API")
        void shouldNotAllowEditingVoidedTransaction() {
            loginAsAdmin();
            String description = transactionField(VOIDED_TRANSACTION_ID, "description");

            navigateTo("/transactions/" + VOIDED_TRANSACTION_ID + "/edit");
            waitForPageLoad();
            assertEquals(baseUrl() + "/transactions/" + VOIDED_TRANSACTION_ID, page.url());
            assertThat(page.locator("[data-testid='status-void']")).isVisible();

            APIResponse update = page.request().put(baseUrl() + "/transactions/api/" + VOIDED_TRANSACTION_ID,
                    withCsrf().setData(transactionBody("Diubah setelah void", 1)));
            assertEquals(409, update.status());
            assertEquals(description, transactionField(VOIDED_TRANSACTION_ID, "description"));
        }

        @Test
        @DisplayName("Should offer only void, not delete, for posted transaction")
        void shouldNotDisplayDeleteButtonForPostedTransaction() {
            loginAsAdmin();

            navigateTo("/transactions/" + POSTED_TRANSACTION_ID);
            waitForPageLoad();

            assertThat(page.locator("#transaction-number")).hasText("TRX-TEST-0002");
            assertThat(page.locator("a[href='/transactions/" + POSTED_TRANSACTION_ID + "/void']")).isVisible();
            assertThat(page.locator("#btn-delete")).hasCount(0);
            assertThat(page.locator("#btn-post")).hasCount(0);

            APIResponse delete = page.request().delete(baseUrl() + "/transactions/api/" + POSTED_TRANSACTION_ID, withCsrf());
            assertEquals(409, delete.status());
            assertEquals("POSTED", transactionField(POSTED_TRANSACTION_ID, "status"));
        }

        @Test
        @DisplayName("Should not allow voiding an already voided transaction")
        void shouldNotAllowVoidingVoidedTransaction() {
            loginAsAdmin();
            String voidReason = transactionField(VOIDED_TRANSACTION_ID, "void_reason");

            navigateTo("/transactions/" + VOIDED_TRANSACTION_ID + "/void");
            waitForPageLoad();
            assertEquals(baseUrl() + "/transactions/" + VOIDED_TRANSACTION_ID, page.url());

            APIResponse response = page.request().post(baseUrl() + "/transactions/api/" + VOIDED_TRANSACTION_ID + "/void",
                    withCsrf().setData(Map.of("reason", "OTHER", "notes", "void ulang")));
            assertEquals(409, response.status(), response.text());
            assertEquals("VOID", transactionField(VOIDED_TRANSACTION_ID, "status"));
            assertEquals(voidReason, transactionField(VOIDED_TRANSACTION_ID, "void_reason"));
        }

        @Test
        @DisplayName("Should reject negative amounts in transaction via API")
        void shouldRejectNegativeAmounts() {
            loginAsAdmin();
            navigateTo("/transactions/" + DRAFT_TRANSACTION_ID);
            String description = "Negatif " + uid();

            APIResponse response = page.request().post(baseUrl() + "/transactions/api",
                    withCsrf().setData(transactionBody(description, -1000)));

            assertEquals(400, response.status());
            assertTrue(response.text().contains("Amount must be non-negative"), response.text());
            assertEquals(0, transactionCountByDescription(description));
        }

        @Test
        @DisplayName("Should reject unbalanced journal entry and accept a balanced one")
        void shouldRejectUnbalancedJournalEntry() {
            loginAsAdmin();
            navigateTo("/transactions/journal-entry/new");
            waitForPageLoad();
            String cash = accountRepository.findByAccountCode(CASH_ACCOUNT_CODE).orElseThrow().getId().toString();
            String revenue = accountRepository.findByAccountCode(REVENUE_ACCOUNT_CODE).orElseThrow().getId().toString();
            String unbalanced = "Tidak seimbang " + uid();
            String balanced = "Seimbang " + uid();

            APIResponse rejected = page.request().post(baseUrl() + "/transactions/journal-entry", withCsrf()
                    .setData(Map.of("transactionDate", LocalDate.now().toString(), "description", unbalanced,
                            "lines", List.of(
                                    Map.of("accountId", cash, "debit", 1000000, "credit", 0),
                                    Map.of("accountId", revenue, "debit", 0, "credit", 900000)))));
            assertEquals(400, rejected.status(), rejected.text());
            assertEquals(0, transactionCountByDescription(unbalanced));

            APIResponse accepted = page.request().post(baseUrl() + "/transactions/journal-entry", withCsrf()
                    .setData(Map.of("transactionDate", LocalDate.now().toString(), "description", balanced,
                            "lines", List.of(
                                    Map.of("accountId", cash, "debit", 1000000, "credit", 0),
                                    Map.of("accountId", revenue, "debit", 0, "credit", 1000000)))));
            assertEquals(200, accepted.status(), accepted.text());
            assertEquals(1, transactionCountByDescription(balanced));
        }
    }

    @Nested
    @DisplayName("Audit Logging")
    class AuditLoggingTests {

        @Test
        @DisplayName("Should log failed login attempts with IP")
        void shouldLogFailedLoginWithIp() {
            context.clearCookies();
            String username = "nonexistent-" + uid();
            LocalDateTime before = LocalDateTime.now().minusSeconds(1);

            submitLogin(username, "wrongpassword");

            assertEquals(baseUrl() + "/login?error", page.url());
            List<SecurityAuditLog> logs = securityAuditLogRepository.findByUsernameAndEventTypeAndTimestampAfter(
                    username, AuditEventType.LOGIN_FAILURE, before);
            assertEquals(1, logs.size(), "Expected one LOGIN_FAILURE record for " + username);
            assertEquals(clientIp, logs.getFirst().getIpAddress());
            assertFalse(logs.getFirst().getSuccess());
            assertEquals("Failed attempt 1 of 5", logs.getFirst().getDetails());
        }

        @Test
        @DisplayName("Should log successful login")
        void shouldLogSuccessfulLogin() {
            User user = createUser(Role.STAFF);
            LocalDateTime before = LocalDateTime.now().minusSeconds(1);

            login(user.getUsername(), PASSWORD);

            List<SecurityAuditLog> logs = securityAuditLogRepository.findByUsernameAndEventTypeAndTimestampAfter(
                    user.getUsername(), AuditEventType.LOGIN_SUCCESS, before);
            assertEquals(1, logs.size());
            assertEquals(clientIp, logs.getFirst().getIpAddress());
            assertTrue(logs.getFirst().getSuccess());
        }

        @Test
        @DisplayName("Should log data export operations")
        void shouldLogDataExportOperations() {
            loginAsAdmin();
            navigateTo("/settings/export");
            waitForPageLoad();
            LocalDateTime before = LocalDateTime.now().minusSeconds(1);

            APIResponse response = page.request().post(baseUrl() + "/settings/export/download", withCsrf());
            assertEquals(200, response.status());

            List<SecurityAuditLog> logs = securityAuditLogRepository.findByUsernameAndEventTypeAndTimestampAfter(
                    "admin", AuditEventType.DATA_EXPORT, before);
            assertEquals(1, logs.size());
            assertEquals(clientIp, logs.getFirst().getIpAddress());
            assertTrue(logs.getFirst().getDetails().startsWith("Full data export: export-"), logs.getFirst().getDetails());
        }
    }

    @Nested
    @DisplayName("Rate Limiting")
    class RateLimitingTests {

        @Test
        @DisplayName("Should rate limit bulk API requests per client IP")
        void shouldRateLimitBulkApiRequests() {
            User admin = userRepository.findByUsername("admin").orElseThrow();
            String token = deviceAuthService.createServiceToken(admin, "secreg-" + uid(), "secreg",
                    "templates:read", 60).getTokenHash();
            APIRequestContext api = playwright.request().newContext(new APIRequest.NewContextOptions()
                    .setBaseURL(baseUrl())
                    .setExtraHTTPHeaders(Map.of("X-Forwarded-For", randomIp(), "Authorization", "Bearer " + token)));
            try {
                for (int i = 1; i <= 100; i++) {
                    assertEquals(200, api.get("/api/templates").status(), "API request " + i);
                }
                APIResponse limited = api.get("/api/templates");
                assertEquals(429, limited.status());
                assertEquals("60", limited.headers().get("retry-after"));
            } finally {
                api.dispose();
            }
        }
    }

    @Nested
    @DisplayName("Data Protection")
    class DataProtectionTests {

        private static final String NIK = "3175012345678901";
        private static final String NPWP = "09.876.543.2-109.000";
        private static final String BANK_ACCOUNT = "7788990011";
        private static final String BPJS = "0001122334455";

        private Employee createEmployeeWithPii() {
            String marker = uid();
            Employee employee = new Employee();
            employee.setEmployeeId("DP-" + marker);
            employee.setName("Karyawan PII " + marker);
            employee.setEmail("pii-" + marker + "@example.com");
            employee.setPhone("081377778888");
            employee.setNikKtp(NIK);
            employee.setNpwp(NPWP);
            employee.setBankName("BCA");
            employee.setBankAccountNumber(BANK_ACCOUNT);
            employee.setBpjsKesehatanNumber(BPJS);
            employee.setHireDate(LocalDate.of(2045, 1, 2));
            return employeeRepository.save(employee);
        }

        @Test
        @DisplayName("Should not show full identity or bank numbers in employee list")
        void shouldMaskBankAccountInEmployeeList() {
            Employee employee = createEmployeeWithPii();
            loginAsAdmin();

            navigateTo("/employees?search=" + employee.getEmployeeId());
            waitForPageLoad();

            assertThat(page.locator("main")).containsText(employee.getName());
            String content = page.content();
            for (String pii : List.of(NIK, NPWP, BANK_ACCOUNT, BPJS)) {
                assertFalse(content.contains(pii), "Unmasked value in employee list: " + pii);
            }
        }

        @Test
        @DisplayName("Should not include credentials in URLs")
        void shouldNotHaveSensitiveDataInUrl() {
            navigateTo("/login");
            waitForPageLoad();
            assertEquals("post", page.locator("form:has(input[name='password'])").getAttribute("method"));

            User user = createUser(Role.STAFF);
            submitLogin(user.getUsername(), PASSWORD + "salah");
            assertEquals(baseUrl() + "/login?error", page.url());
            submitLogin(user.getUsername(), PASSWORD);
            assertEquals(baseUrl() + "/dashboard", page.url());

            loginAsAdmin();
            navigateTo("/users/" + user.getId() + "/change-password");
            waitForPageLoad();
            assertEquals("post", page.locator("form:has(#newPassword)").getAttribute("method"));
        }

        @Test
        @DisplayName("Should mask NPWP, NIK, bank account and BPJS numbers in employee detail")
        void shouldMaskNpwpNikInEmployeeDetail() {
            Employee employee = createEmployeeWithPii();
            loginAsAdmin();

            navigateTo("/employees/" + employee.getEmployeeId());
            waitForPageLoad();

            assertThat(page.locator("main")).containsText(employee.getName());
            assertThat(page.locator("main")).containsText(DataMaskingUtil.maskNik(NIK));
            assertThat(page.locator("main")).containsText(DataMaskingUtil.maskNpwp(NPWP));
            assertThat(page.locator("main")).containsText(DataMaskingUtil.maskBankAccount(BANK_ACCOUNT));
            assertThat(page.locator("main")).containsText(DataMaskingUtil.maskBpjsNumber(BPJS));
            String content = page.content();
            for (String pii : List.of(NIK, NPWP, BANK_ACCOUNT, BPJS)) {
                assertFalse(content.contains(pii), "Unmasked value in employee detail: " + pii);
            }
        }
    }

    @Nested
    @DisplayName("User Manual Screenshots")
    class UserManualScreenshots {

        @Test
        @DisplayName("Should capture user form screenshot")
        void shouldCaptureUserForm() {
            loginAsAdmin();
            navigateTo("/users/new");
            waitForPageLoad();

            assertThat(page.locator("#page-title")).hasText("Pengguna Baru");
            takeManualScreenshot("users-form");
        }

        @Test
        @DisplayName("Should capture audit logs screenshot")
        void shouldCaptureAuditLogs() {
            loginAsAdmin();
            navigateTo("/settings/audit-logs");
            waitForPageLoad();

            assertThat(page.locator("#page-title")).hasText("Security Audit Log");
            takeManualScreenshot("settings-audit-logs");
        }

        @Test
        @DisplayName("Should capture data subjects screenshot")
        void shouldCaptureDataSubjects() {
            loginAsAdmin();
            navigateTo("/settings/data-subjects");
            waitForPageLoad();

            assertThat(page.locator("#page-title")).hasText("Hak Subjek Data (GDPR/UU PDP)");
            takeManualScreenshot("settings-data-subjects");
        }

        @Test
        @DisplayName("Should capture privacy settings screenshot")
        void shouldCapturePrivacySettings() {
            loginAsAdmin();
            navigateTo("/settings/privacy");
            waitForPageLoad();

            assertThat(page.locator("#page-title")).hasText("Kebijakan Privasi");
            takeManualScreenshot("settings-privacy");
        }
    }
}
