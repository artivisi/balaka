package com.artivisi.accountingfinance.functional.service;

import com.artivisi.accountingfinance.entity.DeviceToken;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.AuditEventType;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.repository.DeviceTokenRepository;
import com.artivisi.accountingfinance.repository.SecurityAuditLogRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.service.DeviceAuthService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * User management (Pengaturan → Pengguna): list and search, create with role and password policy,
 * edit, change password, activate/deactivate, device session revocation and delete. Every test works
 * on users it creates; the seeded admin is only used to log in.
 */
@DisplayName("Service Industry - User Management")
@Import(ServiceTestDataInitializer.class)
class ServiceUserManagementTest extends PlaywrightTestBase {

    private static final String PASSWORD = "Rahasia#2045x";
    private static final String NEW_PASSWORD = "Baru#Rahasia2046";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired private UserRepository userRepository;
    @Autowired private DeviceTokenRepository deviceTokenRepository;
    @Autowired private DeviceAuthService deviceAuthService;
    @Autowired private SecurityAuditLogRepository securityAuditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private APIRequestContext api;

    @BeforeEach
    void setup() {
        // RateLimitFilter allows 10 login POSTs per client IP per minute; these tests log in several
        // users, so each test gets its own client address.
        context.setExtraHTTPHeaders(Map.of("X-Forwarded-For", randomIp()));
        api = playwright.request().newContext(new APIRequest.NewContextOptions().setBaseURL(baseUrl())
                .setExtraHTTPHeaders(Map.of("X-Forwarded-For", randomIp())));
        loginAsAdmin();
    }

    @AfterEach
    void tearDownApi() {
        api.dispose();
    }

    @Test
    @DisplayName("Should list a user with roles and status, and filter by search")
    void shouldListAndSearchUsers() {
        User user = createUser(Role.ACCOUNTANT);
        User other = createUser(Role.STAFF);

        navigateTo("/users");
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Kelola Pengguna");

        page.locator("#search-user").fill(user.getUsername());
        page.locator("#btn-search").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/users?search=" + user.getUsername());
        assertThat(page.locator("tbody tr")).hasCount(1);
        Locator row = page.locator("#user-" + user.getId());
        assertThat(row).containsText(user.getFullName());
        assertThat(row).containsText(user.getEmail());
        assertThat(row).containsText("Akuntan");
        assertThat(row).containsText("Aktif");
        assertThat(page.locator("#user-" + other.getId())).hasCount(0);

        navigateTo("/users?search=tidak-ada-" + uid());
        waitForPageLoad();
        assertThat(page.locator("tbody")).containsText("Tidak ada pengguna ditemukan");
    }

    @Test
    @DisplayName("Should create a user who can then log in, and audit it")
    void shouldCreateUser() {
        String username = "baru-" + uid();
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        openNewUserForm();
        page.locator("#username").fill(username);
        page.locator("#password").fill(PASSWORD);
        page.locator("#fullName").fill("Pengguna Baru " + username);
        page.locator("#email").fill(username + "@example.com");
        roleCheckbox(Role.STAFF).check();
        page.locator("#btn-save").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/users");
        assertThat(page.locator("main")).containsText("Pengguna berhasil dibuat: " + username);

        User created = userRepository.findByUsername(username)
                .orElseThrow(() -> new AssertionError("User not persisted: " + username));
        assertThat(created.getFullName()).isEqualTo("Pengguna Baru " + username);
        assertThat(created.getEmail()).isEqualTo(username + "@example.com");
        assertThat(created.getActive()).isTrue();
        assertThat(created.getRoles()).containsExactly(Role.STAFF);
        assertThat(passwordEncoder.matches(PASSWORD, created.getPassword())).isTrue();
        assertThat(securityAuditLogRepository.findByUsernameAndEventTypeAndTimestampAfter(
                "admin", AuditEventType.USER_CREATED, before))
                .anyMatch(l -> l.getDetails().equals("Created user: " + username + " with roles: [STAFF]"));

        assertThat(landingPageAfterLogin(username, PASSWORD)).endsWith("/dashboard");
    }

    @Test
    @DisplayName("Should reject a new user without role, with weak password, duplicate username or blank username")
    void shouldRejectInvalidNewUser() {
        User existing = createUser(Role.STAFF);

        String noRole = "norole-" + uid();
        submitNewUser(noRole, PASSWORD, null);
        assertThat(formError()).hasText("At least one role must be selected");
        assertThat(page.locator("#username")).hasValue(noRole);

        String weak = "weak-" + uid();
        submitNewUser(weak, "weakpass", Role.STAFF);
        assertThat(formError()).hasText("Password minimal 12 karakter. Password harus mengandung minimal satu huruf besar. "
                + "Password harus mengandung minimal satu angka. "
                + "Password harus mengandung minimal satu karakter khusus (!@#$%^&* dll)");
        assertThat(roleCheckbox(Role.STAFF)).isChecked();

        submitNewUser(existing.getUsername(), PASSWORD, Role.STAFF);
        assertThat(formError()).hasText("Username already exists: " + existing.getUsername());

        openNewUserForm();
        page.locator("#username").evaluate("el => el.form.noValidate = true");
        page.locator("#password").fill(PASSWORD);
        page.locator("#fullName").fill("Tanpa Username");
        roleCheckbox(Role.STAFF).check();
        page.locator("#btn-save").click();
        waitForPageLoad();
        assertThat(page.locator("#username + p, #username ~ p").first()).containsText("Username is required");

        assertThat(userRepository.findByUsername(noRole)).isEmpty();
        assertThat(userRepository.findByUsername(weak)).isEmpty();
        assertThat(userRepository.findByUsername(existing.getUsername()).orElseThrow().getFullName())
                .isEqualTo(existing.getFullName());
    }

    @Test
    @DisplayName("Should show user detail with masked email and roles")
    void shouldShowUserDetail() {
        User user = createUser(Role.AUDITOR);

        navigateTo("/users/" + user.getId());
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText(user.getFullName());
        Locator main = page.locator("main");
        assertThat(main).containsText("@" + user.getUsername());
        String local = user.getEmail().substring(0, user.getEmail().indexOf('@'));
        assertThat(main).containsText(local.substring(0, 2) + "*".repeat(local.length() - 2) + "@example.com");
        assertThat(main).not().containsText(user.getEmail());
        assertThat(main).containsText("Read-only access to reports");
        assertThat(page.locator("#device-sessions")).containsText("Tidak ada sesi perangkat aktif");
    }

    @Test
    @DisplayName("Should update name, email and roles, and audit it")
    void shouldUpdateUser() {
        User user = createUser(Role.STAFF);
        String email = "diubah-" + uid() + "@example.com";
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        navigateTo("/users/" + user.getId() + "/edit");
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Edit Pengguna");
        assertThat(page.locator("#username")).hasValue(user.getUsername());
        assertThat(roleCheckbox(Role.STAFF)).isChecked();

        page.locator("#fullName").fill("Nama Diubah " + user.getUsername());
        page.locator("#email").fill(email);
        roleCheckbox(Role.STAFF).uncheck();
        roleCheckbox(Role.ACCOUNTANT).check();
        roleCheckbox(Role.AUDITOR).check();
        page.locator("#btn-save").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/users/" + user.getId());
        assertThat(page.locator("main")).containsText("Pengguna berhasil diperbarui: " + user.getUsername());
        assertThat(page.locator("#page-title")).hasText("Nama Diubah " + user.getUsername());

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updated.getFullName()).isEqualTo("Nama Diubah " + user.getUsername());
        assertThat(updated.getEmail()).isEqualTo(email);
        assertThat(updated.getRoles()).containsExactlyInAnyOrder(Role.ACCOUNTANT, Role.AUDITOR);
        assertThat(updated.getActive()).isTrue();
        assertThat(updated.getPassword()).isEqualTo(user.getPassword());
        assertThat(securityAuditLogRepository.findByUsernameAndEventTypeAndTimestampAfter(
                "admin", AuditEventType.USER_UPDATED, before))
                .anyMatch(l -> l.getDetails().startsWith("Updated user: " + user.getUsername() + " (id: " + user.getId() + ")"));
    }

    @Test
    @DisplayName("Should reject mismatched and weak passwords, then change the password")
    void shouldChangePassword() {
        User user = createUser(Role.STAFF);

        navigateTo("/users/" + user.getId());
        waitForPageLoad();
        page.locator("#link-change-password").click();
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Ubah Password");

        page.locator("#newPassword").fill(NEW_PASSWORD);
        page.locator("#confirmPassword").fill(NEW_PASSWORD + "x");
        page.locator("#btn-save-password").click();
        waitForPageLoad();
        assertThat(formError()).hasText("Password tidak cocok");

        page.locator("#newPassword").fill("lemahsekali");
        page.locator("#confirmPassword").fill("lemahsekali");
        page.locator("#btn-save-password").click();
        waitForPageLoad();
        assertThat(formError()).containsText("Password minimal 12 karakter");
        assertThat(userRepository.findById(user.getId()).orElseThrow().getPassword()).isEqualTo(user.getPassword());

        page.locator("#newPassword").fill(NEW_PASSWORD);
        page.locator("#confirmPassword").fill(NEW_PASSWORD);
        page.locator("#btn-save-password").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/users/" + user.getId());
        assertThat(page.locator("main")).containsText("Password berhasil diubah");
        assertThat(passwordEncoder.matches(NEW_PASSWORD, userRepository.findById(user.getId()).orElseThrow().getPassword()))
                .isTrue();
        assertThat(landingPageAfterLogin(user.getUsername(), PASSWORD)).endsWith("/login?error");
        assertThat(landingPageAfterLogin(user.getUsername(), NEW_PASSWORD)).endsWith("/dashboard");
    }

    @Test
    @DisplayName("Deactivated user cannot log in and their API tokens are rejected; reactivation restores login")
    void shouldDeactivateAndReactivateUser() {
        User user = createUser(Role.STAFF);
        String token = issueToken(user);
        assertThat(apiStatus(token)).isEqualTo(200);

        navigateTo("/users/" + user.getId());
        waitForPageLoad();
        page.locator("form[action$='/toggle-active'] button").click();
        waitForPageLoad();

        assertThat(page.locator("main")).containsText("Status pengguna berhasil diubah");
        assertThat(page.locator("form[action$='/toggle-active'] button")).hasText("Aktifkan");
        assertThat(userRepository.findById(user.getId()).orElseThrow().getActive()).isFalse();
        assertThat(landingPageAfterLogin(user.getUsername(), PASSWORD)).endsWith("/login?error");
        assertThat(apiStatus(token)).isEqualTo(401);

        page.locator("form[action$='/toggle-active'] button").click();
        waitForPageLoad();

        assertThat(page.locator("form[action$='/toggle-active'] button")).hasText("Nonaktifkan");
        assertThat(userRepository.findById(user.getId()).orElseThrow().getActive()).isTrue();
        assertThat(landingPageAfterLogin(user.getUsername(), PASSWORD)).endsWith("/dashboard");
        assertThat(apiStatus(token)).isEqualTo(200);
    }

    @Test
    @DisplayName("Should revoke one device session, then all remaining sessions")
    void shouldRevokeDeviceSessions() {
        User user = createUser(Role.STAFF);
        DeviceToken firstToken = deviceAuthService.createServiceToken(user, "usermgmt-" + uid(), "usermgmt",
                "templates:read", 60);
        String first = firstToken.getTokenHash();
        UUID firstId = firstToken.getId();
        String second = issueToken(user);
        String third = issueToken(user);

        navigateTo("/users/" + user.getId());
        waitForPageLoad();
        assertThat(page.locator("#device-sessions tbody tr")).hasCount(3);

        page.onDialog(dialog -> dialog.accept());
        page.locator("form[action='/users/" + user.getId() + "/revoke-session/" + firstId + "'] button").click();
        waitForPageLoad();

        assertThat(page.locator("main")).containsText("Sesi perangkat berhasil dicabut");
        assertThat(page.locator("#device-sessions tbody tr")).hasCount(2);
        assertThat(apiStatus(first)).isEqualTo(401);
        assertThat(apiStatus(second)).isEqualTo(200);

        page.locator("#form-revoke-all button").click();
        waitForPageLoad();

        assertThat(page.locator("main")).containsText("2 sesi perangkat berhasil dicabut");
        assertThat(page.locator("#device-sessions")).containsText("Tidak ada sesi perangkat aktif");
        assertThat(deviceTokenRepository.findActiveByUser(user)).isEmpty();
        assertThat(apiStatus(second)).isEqualTo(401);
        assertThat(apiStatus(third)).isEqualTo(401);
    }

    @Test
    @DisplayName("Should delete a user, and refuse deleting the logged-in account")
    void shouldDeleteUser() {
        User user = createUser(Role.STAFF);

        navigateTo("/users/" + user.getId());
        waitForPageLoad();
        page.onDialog(dialog -> dialog.accept());
        page.locator("#form-delete button").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/users");
        assertThat(page.locator("main")).containsText("Pengguna berhasil dihapus");
        assertThat(userRepository.findById(user.getId())).isEmpty();

        User selfAdmin = createUser(Role.ADMIN);
        login(selfAdmin.getUsername(), PASSWORD);
        navigateTo("/users/" + selfAdmin.getId());
        waitForPageLoad();
        page.locator("#form-delete button").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/users/" + selfAdmin.getId());
        assertThat(page.locator("main")).containsText("Cannot delete your own account");
        assertThat(userRepository.findById(selfAdmin.getId())).isPresent();
    }

    @Test
    @DisplayName("Should deny user management to a role without USER_VIEW")
    void shouldDenyWithoutUserPermissions() {
        User target = createUser(Role.STAFF);
        User auditor = createUser(Role.AUDITOR);
        login(auditor.getUsername(), PASSWORD);

        for (String path : new String[] {"/users", "/users/new", "/users/" + target.getId(),
                "/users/" + target.getId() + "/edit", "/users/" + target.getId() + "/change-password"}) {
            Response response = page.navigate(baseUrl() + path);
            assertThat(response.status()).as(path).isEqualTo(403);
        }

        navigateTo("/dashboard");
        String csrf = page.locator("meta[name='_csrf']").getAttribute("content");
        assertThat(page.request().post(baseUrl() + "/users/" + target.getId() + "/toggle-active",
                RequestOptions.create().setHeader("X-CSRF-TOKEN", csrf).setMaxRedirects(0)).status()).isEqualTo(403);
        assertThat(page.request().post(baseUrl() + "/users/" + target.getId() + "/delete",
                RequestOptions.create().setHeader("X-CSRF-TOKEN", csrf).setMaxRedirects(0)).status()).isEqualTo(403);

        User unchanged = userRepository.findById(target.getId()).orElseThrow();
        assertThat(unchanged.getActive()).isTrue();
    }

    // ==================== helpers ====================

    private void openNewUserForm() {
        navigateTo("/users/new");
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Pengguna Baru");
    }

    private void submitNewUser(String username, String password, Role role) {
        openNewUserForm();
        page.locator("#username").fill(username);
        page.locator("#password").fill(password);
        page.locator("#fullName").fill("Nama " + username);
        page.locator("#email").fill(username + "@example.com");
        if (role != null) {
            roleCheckbox(role).check();
        }
        page.locator("#btn-save").click();
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Pengguna Baru");
    }

    private Locator formError() {
        return page.locator("main .bg-red-50 span");
    }

    private Locator roleCheckbox(Role role) {
        return page.locator("input[name='selectedRoles'][value='" + role.name() + "']");
    }

    /** Logs in from a separate browser context and returns the URL the login form lands on. */
    private String landingPageAfterLogin(String username, String password) {
        BrowserContext other = browser.newContext();
        try {
            other.setExtraHTTPHeaders(Map.of("X-Forwarded-For", randomIp()));
            Page otherPage = other.newPage();
            otherPage.navigate(baseUrl() + "/login");
            otherPage.fill("input[name='username']", username);
            otherPage.fill("input[name='password']", password);
            otherPage.click("button[type='submit']");
            otherPage.waitForLoadState(LoadState.NETWORKIDLE);
            return otherPage.url();
        } finally {
            other.close();
        }
    }

    private String issueToken(User user) {
        DeviceToken token = deviceAuthService.createServiceToken(user, "usermgmt-" + uid(), "usermgmt",
                "templates:read", 60);
        return token.getTokenHash();
    }

    private int apiStatus(String token) {
        return api.get("/api/templates", RequestOptions.create().setHeader("Authorization", "Bearer " + token)).status();
    }

    private User createUser(Role role) {
        String id = uid();
        User user = new User();
        user.setUsername("usermgmt-" + role.name().toLowerCase() + "-" + id);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setFullName("Pengguna " + id);
        user.setEmail("usermgmt-" + id + "@example.com");
        user.setActive(true);
        user.setRoles(Set.of(role), "system");
        return userRepository.save(user);
    }

    private static String randomIp() {
        return "10.88." + RANDOM.nextInt(256) + "." + RANDOM.nextInt(256);
    }

    private static String uid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
