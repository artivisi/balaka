package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.DeviceToken;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.AuditEventType;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.SecurityAuditLogRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.service.DeviceAuthService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
 * Issue #61, parts 1 and 2: every user changes their own password at /account/password; a password
 * chosen by an administrator must be changed at the first login, and until then the account reaches
 * nothing else, web or API. Runs with password reset by email switched off (the default), which part 3
 * must then report instead of pretending.
 */
@DisplayName("Account password: self-service and forced change")
@Import(ServiceTestDataInitializer.class)
class AccountPasswordFunctionalTest extends PlaywrightTestBase {

    private static final String PASSWORD = "Awal#Rahasia2045";
    private static final String NEW_PASSWORD = "Baru#Rahasia2046";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired private UserRepository userRepository;
    @Autowired private DeviceAuthService deviceAuthService;
    @Autowired private SecurityAuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private APIRequestContext api;

    @BeforeEach
    void setUpClientAddress() {
        // RateLimitFilter allows 10 login POSTs per client IP per minute; each test gets its own address.
        context.setExtraHTTPHeaders(Map.of("X-Forwarded-For", randomIp()));
        api = playwright.request().newContext(new APIRequest.NewContextOptions().setBaseURL(baseUrl())
                .setExtraHTTPHeaders(Map.of("X-Forwarded-For", randomIp())));
    }

    @AfterEach
    void disposeApi() {
        api.dispose();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Role.class, names = {"AUDITOR", "EMPLOYEE"})
    @DisplayName("A user with only one read role changes their own password; other sessions and device tokens end")
    void selfServiceChange(Role role) {
        User user = createUser(role, false);
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        String token = deviceAuthService.createAccessToken(user, "acct-" + uid(), "Laptop").getTokenHash();
        // A read role may lack the templates permission (403); what matters is that the token authenticates.
        assertThat(apiStatus(token)).isNotEqualTo(401);

        BrowserContext other = newClientContext();
        Page otherPage = other.newPage();
        try {
            assertThat(submitLogin(otherPage, user.getUsername(), PASSWORD)).endsWith("/dashboard");

            assertThat(submitLogin(page, user.getUsername(), PASSWORD)).endsWith("/dashboard");
            page.locator("#nav-account-password").click();
            waitForPageLoad();
            assertThat(page).hasURL(baseUrl() + "/account/password");
            assertThat(page.locator("#notice-password-change-required")).hasCount(0);

            changePassword("Salah#Rahasia2045", NEW_PASSWORD, NEW_PASSWORD);
            assertThat(page.locator("#message-errors")).hasText("Password saat ini salah");

            changePassword(PASSWORD, NEW_PASSWORD, NEW_PASSWORD + "x");
            assertThat(page.locator("#message-errors")).hasText("Password tidak cocok");

            page.locator("#newPassword").evaluate("el => el.form.noValidate = true");
            changePassword(PASSWORD, "lemahsekali", "lemahsekali");
            assertThat(page.locator("#message-errors")).containsText("Password minimal 12 karakter");
            assertThat(page.locator("#message-errors")).containsText("huruf besar");
            assertThat(passwordEncoder.matches(PASSWORD, reload(user).getPassword())).isTrue();
            assertThat(apiStatus(token)).as("refused attempts change nothing").isNotEqualTo(401);

            changePassword(PASSWORD, NEW_PASSWORD, NEW_PASSWORD);
            assertThat(page).hasURL(baseUrl() + "/account/password");
            assertThat(page.locator("#message-success")).containsText("Password berhasil diubah");

            User updated = reload(user);
            assertThat(passwordEncoder.matches(NEW_PASSWORD, updated.getPassword())).isTrue();
            assertThat(updated.getCredentialsChangedAt()).isAfter(before);

            navigateTo("/dashboard");
            assertThat(page).hasURL(baseUrl() + "/dashboard");

            otherPage.navigate(baseUrl() + "/dashboard");
            otherPage.waitForLoadState(LoadState.NETWORKIDLE);
            assertThat(otherPage).hasURL(baseUrl() + "/login?expired");
            assertThat(otherPage.locator("#message-expired")).containsText("password akun ini diubah");

            assertThat(apiStatus(token)).as("device token revoked").isEqualTo(401);
            assertThat(landingAfterLogin(user.getUsername(), PASSWORD)).endsWith("/login?error");
            assertThat(landingAfterLogin(user.getUsername(), NEW_PASSWORD)).endsWith("/dashboard");

            assertThat(auditLogRepository.findByUsernameAndEventTypeAndTimestampAfter(
                    user.getUsername(), AuditEventType.PASSWORD_CHANGED, before))
                    .anyMatch(l -> Boolean.TRUE.equals(l.getSuccess())
                            && l.getDetails().equals("Self-service change by user: " + user.getUsername()
                                    + "; device tokens revoked: 1"))
                    .anyMatch(l -> !Boolean.TRUE.equals(l.getSuccess()))
                    .noneMatch(l -> l.getDetails().contains(NEW_PASSWORD) || l.getDetails().contains(PASSWORD));
        } finally {
            other.close();
        }
    }

    @Test
    @DisplayName("A user created by an administrator reaches only password change and logout until the first change")
    void forcedChangeAfterAdministratorCreatesUser() {
        String username = "paksa-" + uid();
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        loginAsAdmin();
        navigateTo("/users/new");
        waitForPageLoad();
        page.locator("#username").fill(username);
        page.locator("#password").fill(PASSWORD);
        page.locator("#fullName").fill("Pengguna " + username);
        page.locator("#email").fill(username + "@example.com");
        page.locator("input[name='selectedRoles'][value='STAFF']").check();
        page.locator("#btn-save").click();
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/users");

        User created = userRepository.findByUsername(username).orElseThrow();
        assertThat(created.getPasswordChangeRequired()).isTrue();
        navigateTo("/users?search=" + username);
        assertThat(page.locator("#badge-password-change-required-" + username)).hasText("Wajib ganti password");
        navigateTo("/users/" + created.getId());
        assertThat(page.locator("#badge-password-change-required")).isVisible();
        assertThat(auditLogRepository.findByUsernameAndEventTypeAndTimestampAfter(
                "admin", AuditEventType.PASSWORD_CHANGE_REQUIRED_SET, before))
                .anyMatch(l -> l.getDetails().equals("Change at next login required for user: " + username));

        String token = deviceAuthService.createAccessToken(created, "paksa-" + uid(), "Laptop").getTokenHash();
        APIResponse refused = api.get("/api/templates", RequestOptions.create().setHeader("Authorization", "Bearer " + token));
        assertThat(refused.status()).isEqualTo(403);
        assertThat(refused.text()).contains("\"error\":\"password_change_required\"");

        BrowserContext userContext = newClientContext();
        Page userPage = userContext.newPage();
        try {
            assertThat(submitLogin(userPage, username, PASSWORD)).endsWith("/account/password");
            assertThat(userPage.locator("#notice-password-change-required")).isVisible();
            for (String path : new String[] {"/dashboard", "/transactions", "/reports/trial-balance", "/users"}) {
                userPage.navigate(baseUrl() + path);
                userPage.waitForLoadState(LoadState.NETWORKIDLE);
                assertThat(userPage).hasURL(baseUrl() + "/account/password");
            }
            APIResponse htmx = userPage.request().get(baseUrl() + "/transactions/quick/templates",
                    RequestOptions.create().setHeader("HX-Request", "true").setMaxRedirects(0));
            assertThat(htmx.status()).as("htmx requests are refused, not redirected into the page").isEqualTo(403);
            assertThat(htmx.headers()).containsEntry("x-password-change-required", "true");
            APIResponse sessionApi = userPage.request().get(baseUrl() + "/api/templates");
            assertThat(sessionApi.status()).isEqualTo(403);
            assertThat(sessionApi.text()).contains("\"error\":\"password_change_required\"");

            userPage.locator("#currentPassword").fill(PASSWORD);
            userPage.locator("#newPassword").fill(NEW_PASSWORD);
            userPage.locator("#confirmPassword").fill(NEW_PASSWORD);
            userPage.locator("#btn-change-password").click();
            userPage.waitForLoadState(LoadState.NETWORKIDLE);
            assertThat(userPage).hasURL(baseUrl() + "/dashboard");

            userPage.navigate(baseUrl() + "/transactions");
            userPage.waitForLoadState(LoadState.NETWORKIDLE);
            assertThat(userPage).hasURL(baseUrl() + "/transactions");
        } finally {
            userContext.close();
        }

        assertThat(reload(created).getPasswordChangeRequired()).isFalse();
        assertThat(api.get("/api/templates", RequestOptions.create().setHeader("Authorization", "Bearer " + token)).status())
                .as("tokens issued before the change are revoked by it").isEqualTo(401);
        String freshToken = deviceAuthService.createAccessToken(reload(created), "paksa-" + uid(), "Laptop").getTokenHash();
        assertThat(apiStatus(freshToken)).isEqualTo(200);

        navigateTo("/users/" + created.getId());
        assertThat(page.locator("#badge-password-change-required")).hasCount(0);
        assertThat(auditLogRepository.findByUsernameAndEventTypeAndTimestampAfter(
                username, AuditEventType.PASSWORD_CHANGE_REQUIRED_CLEARED, before))
                .anyMatch(l -> l.getDetails().equals("Change at next login no longer required for user: " + username));
    }

    @Test
    @DisplayName("A forced-change user can log out")
    void forcedChangeUserCanLogOut() {
        User user = createUser(Role.STAFF, true);
        assertThat(submitLogin(page, user.getUsername(), PASSWORD)).endsWith("/account/password");

        page.locator("form[action='/logout'] button").first().click();
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/login?logout");
        navigateTo("/account/password");
        assertThat(page).hasURL(baseUrl() + "/login");
    }

    @Test
    @DisplayName("An administrator opening their own change-password page is sent to the self-service page")
    void administratorOwnPasswordGoesToSelfService() {
        loginAsAdmin();
        User admin = userRepository.findByUsername("admin").orElseThrow();
        navigateTo("/users/" + admin.getId() + "/change-password");
        assertThat(page).hasURL(baseUrl() + "/account/password");
    }

    @Test
    @DisplayName("With reset by email not configured, the login page has no reset link and the reset pages say so")
    void resetNotConfigured() {
        navigateTo("/login");
        assertThat(page.locator("#btn-login")).isVisible();
        assertThat(page.locator("#link-forgot-password")).hasCount(0);

        for (String path : new String[] {"/forgot-password", "/reset-password?token=abc"}) {
            APIResponse response = api.get(path);
            assertThat(response.status()).as(path).isEqualTo(503);
            assertThat(response.text()).as(path).contains("Reset password melalui email belum dikonfigurasi");
        }
        navigateTo("/forgot-password");
        assertThat(page.locator("#message-unavailable")).containsText("belum dikonfigurasi");
        assertThat(page.locator("#email")).hasCount(0);
    }

    // ==================== helpers ====================

    private void changePassword(String current, String next, String confirm) {
        page.locator("#currentPassword").fill(current);
        page.locator("#newPassword").fill(next);
        page.locator("#confirmPassword").fill(confirm);
        page.locator("#btn-change-password").click();
        waitForPageLoad();
    }

    private String submitLogin(Page target, String username, String password) {
        target.navigate(baseUrl() + "/login");
        target.fill("input[name='username']", username);
        target.fill("input[name='password']", password);
        target.click("#btn-login");
        target.waitForLoadState(LoadState.NETWORKIDLE);
        return target.url();
    }

    private String landingAfterLogin(String username, String password) {
        BrowserContext fresh = newClientContext();
        try {
            return submitLogin(fresh.newPage(), username, password);
        } finally {
            fresh.close();
        }
    }

    private BrowserContext newClientContext() {
        BrowserContext fresh = browser.newContext();
        fresh.setExtraHTTPHeaders(Map.of("X-Forwarded-For", randomIp()));
        return fresh;
    }

    private int apiStatus(String token) {
        return api.get("/api/templates", RequestOptions.create().setHeader("Authorization", "Bearer " + token)).status();
    }

    private User createUser(Role role, boolean changeRequired) {
        String id = uid();
        User user = new User();
        user.setUsername("akun-" + role.name().toLowerCase() + "-" + id);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setFullName("Pengguna " + id);
        user.setEmail("akun-" + id + "@example.com");
        user.setActive(true);
        user.setPasswordChangeRequired(changeRequired);
        user.setRoles(Set.of(role), "system");
        return userRepository.save(user);
    }

    private User reload(User user) {
        return userRepository.findById(user.getId()).orElseThrow();
    }

    private static String randomIp() {
        return "10.61." + RANDOM.nextInt(256) + "." + RANDOM.nextInt(256);
    }

    private static String uid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
