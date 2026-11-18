package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.AuditEventType;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.SecurityAuditLogRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.service.DeviceAuthService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.RequestOptions;
import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #61, part 3: reset by email. GreenMail is the SMTP server. The emailed link is built from the
 * configured base URL (never from the request's Host header), so the test checks that prefix and opens
 * the same path on the test server.
 */
@DisplayName("Password reset by email")
@Import(ServiceTestDataInitializer.class)
class PasswordResetFunctionalTest extends PlaywrightTestBase {

    private static final String BASE_URL = "https://balaka.example.test";
    private static final String PASSWORD = "Lama#Rahasia2045";
    private static final String NEW_PASSWORD = "Baru#Rahasia2046";
    private static final Pattern LINK = Pattern.compile(Pattern.quote(BASE_URL) + "(/reset-password\\?token=[A-Za-z0-9_-]+)");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final GreenMail SMTP = new GreenMail(ServerSetupTest.SMTP.dynamicPort());

    @DynamicPropertySource
    static void mailProperties(DynamicPropertyRegistry registry) {
        if (!SMTP.isRunning()) {
            SMTP.start();
        }
        registry.add("app.password-reset.enabled", () -> "true");
        registry.add("app.password-reset.base-url", () -> BASE_URL);
        registry.add("app.password-reset.from", () -> "noreply@balaka.example.test");
        registry.add("spring.mail.host", () -> "127.0.0.1");
        registry.add("spring.mail.port", () -> SMTP.getSmtp().getPort());
        registry.add("spring.mail.properties.mail.smtp.auth", () -> "false");
        registry.add("spring.mail.properties.mail.smtp.starttls.enable", () -> "false");
        registry.add("spring.mail.properties.mail.smtp.starttls.required", () -> "false");
    }

    @Autowired private UserRepository userRepository;
    @Autowired private DeviceAuthService deviceAuthService;
    @Autowired private SecurityAuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    private APIRequestContext api;

    @BeforeEach
    void setUp() throws Exception {
        SMTP.purgeEmailFromAllMailboxes();
        // Reset requests are limited per client address; each test gets its own.
        context.setExtraHTTPHeaders(Map.of("X-Forwarded-For", randomIp()));
        api = playwright.request().newContext(new APIRequest.NewContextOptions().setBaseURL(baseUrl())
                .setExtraHTTPHeaders(Map.of("X-Forwarded-For", randomIp())));
    }

    @AfterEach
    void disposeApi() {
        api.dispose();
    }

    @Test
    @DisplayName("Forgot password: email with a single-use link, new password set, sessions and device tokens ended")
    void resetHappyPath() throws Exception {
        User user = createUser(true);
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        String token = deviceAuthService.createAccessToken(user, "reset-" + uid(), "Laptop").getTokenHash();
        assertThat(apiStatus(token)).isEqualTo(200);

        BrowserContext other = newClientContext();
        Page otherPage = other.newPage();
        try {
            assertThat(submitLogin(otherPage, user.getUsername(), PASSWORD)).endsWith("/dashboard");

            navigateTo("/login");
            page.locator("#link-forgot-password").click();
            waitForPageLoad();
            assertThat(page).hasURL(baseUrl() + "/forgot-password");
            page.locator("#email").fill(user.getEmail().toUpperCase());
            page.locator("#btn-request-reset").click();
            waitForPageLoad();
            assertThat(page).hasURL(baseUrl() + "/forgot-password?sent");
            assertThat(page.locator("#message-sent")).containsText("Jika alamat tersebut terdaftar");

            MimeMessage mail = awaitSingleMail();
            assertThat(mail.getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo(user.getEmail());
            assertThat(mail.getFrom()[0].toString()).isEqualTo("noreply@balaka.example.test");
            assertThat(mail.getSubject()).isEqualTo("Reset password akun " + user.getUsername());
            String body = (String) mail.getContent();
            assertThat(body).contains("dalam 30 menit");
            String path = linkPath(body);
            String rawToken = path.substring(path.indexOf('=') + 1);

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT token_hash FROM password_reset_tokens WHERE id_user = ? AND used_at IS NULL", String.class, user.getId()))
                    .as("only the SHA-256 is stored").isEqualTo(sha256(rawToken)).isNotEqualTo(rawToken);

            navigateTo(path);
            page.locator("#newPassword").evaluate("el => el.form.noValidate = true");
            page.locator("#newPassword").fill("lemahsekali");
            page.locator("#confirmPassword").fill("lemahsekali");
            page.locator("#btn-reset-password").click();
            waitForPageLoad();
            assertThat(page.locator("#message-errors")).containsText("Password minimal 12 karakter");
            assertThat(passwordEncoder.matches(PASSWORD, reload(user).getPassword())).isTrue();

            page.locator("#newPassword").fill(NEW_PASSWORD);
            page.locator("#confirmPassword").fill(NEW_PASSWORD);
            page.locator("#btn-reset-password").click();
            waitForPageLoad();
            assertThat(page).hasURL(baseUrl() + "/login?reset");
            assertThat(page.locator("#message-reset")).containsText("Password berhasil diubah");

            User updated = reload(user);
            assertThat(passwordEncoder.matches(NEW_PASSWORD, updated.getPassword())).isTrue();
            assertThat(updated.getPasswordChangeRequired()).as("the user chose the password").isFalse();

            otherPage.navigate(baseUrl() + "/dashboard");
            otherPage.waitForLoadState(LoadState.NETWORKIDLE);
            assertThat(otherPage).hasURL(baseUrl() + "/login?expired");
            assertThat(apiStatus(token)).as("device token revoked").isEqualTo(401);
            assertThat(landingAfterLogin(user.getUsername(), PASSWORD)).endsWith("/login?error");
            assertThat(landingAfterLogin(user.getUsername(), NEW_PASSWORD)).endsWith("/dashboard");

            assertThat(auditLogRepository.findByUsernameAndEventTypeAndTimestampAfter(
                    user.getUsername(), AuditEventType.PASSWORD_RESET_REQUESTED, before))
                    .anyMatch(l -> l.getDetails().equals("Reset link issued for user: " + user.getUsername()
                            + " (requested by: self)"));
            assertThat(auditLogRepository.findByUsernameAndEventTypeAndTimestampAfter(
                    user.getUsername(), AuditEventType.PASSWORD_RESET_COMPLETED, before))
                    .anyMatch(l -> l.getDetails().equals("Completed for user: " + user.getUsername()
                            + "; device tokens revoked: 1"))
                    .noneMatch(l -> l.getDetails().contains(rawToken));
        } finally {
            other.close();
        }
    }

    @Test
    @DisplayName("A used link is refused on open and on submit")
    void reusedTokenRefused() throws Exception {
        User user = createUser(true);
        String path = requestAndReadLink(user);
        String rawToken = path.substring(path.indexOf('=') + 1);

        navigateTo(path);
        page.locator("#newPassword").fill(NEW_PASSWORD);
        page.locator("#confirmPassword").fill(NEW_PASSWORD);
        page.locator("#btn-reset-password").click();
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/login?reset");

        APIResponse reopened = api.get(path);
        assertThat(reopened.status()).isEqualTo(400);
        assertThat(reopened.text()).contains("Tautan reset ini sudah dipakai");

        navigateTo(path);
        assertThat(page.locator("#message-link-error")).containsText("sudah dipakai");
        assertThat(page.locator("#newPassword")).hasCount(0);

        String encoded = reload(user).getPassword();
        APIResponse resubmitted = postReset(rawToken, "Lain#Rahasia2047");
        assertThat(resubmitted.status()).isEqualTo(400);
        assertThat(resubmitted.text()).contains("sudah dipakai");
        assertThat(reload(user).getPassword()).isEqualTo(encoded);
    }

    @Test
    @DisplayName("An expired link is refused")
    void expiredTokenRefused() throws Exception {
        User user = createUser(true);
        String path = requestAndReadLink(user);
        jdbcTemplate.update("UPDATE password_reset_tokens SET expires_at = ? WHERE id_user = ?",
                LocalDateTime.now().minusMinutes(1), user.getId());

        navigateTo(path);
        assertThat(page.locator("#message-link-error")).containsText("sudah kedaluwarsa");
        assertThat(page.locator("#newPassword")).hasCount(0);

        APIResponse submitted = postReset(path.substring(path.indexOf('=') + 1), NEW_PASSWORD);
        assertThat(submitted.status()).isEqualTo(400);
        assertThat(passwordEncoder.matches(PASSWORD, reload(user).getPassword())).isTrue();
    }

    @Test
    @DisplayName("A newer request supersedes the earlier link")
    void newerTokenSupersedesOlder() throws Exception {
        User user = createUser(true);
        String first = requestAndReadLink(user);
        SMTP.purgeEmailFromAllMailboxes();
        String second = requestAndReadLink(user);
        assertThat(second).isNotEqualTo(first);

        navigateTo(first);
        assertThat(page.locator("#message-link-error")).containsText("digantikan oleh permintaan yang lebih baru");
        navigateTo(second);
        assertThat(page.locator("#btn-reset-password")).isVisible();
    }

    @Test
    @DisplayName("Unknown and inactive addresses get the same answer and no email")
    void unknownEmailAnsweredTheSame() throws Exception {
        User inactive = createUser(false);
        for (String address : new String[] {"tidak-ada-" + uid() + "@example.com", inactive.getEmail()}) {
            navigateTo("/forgot-password");
            page.locator("#email").fill(address);
            page.locator("#btn-request-reset").click();
            waitForPageLoad();
            assertThat(page).hasURL(baseUrl() + "/forgot-password?sent");
            assertThat(page.locator("#message-sent")).containsText("Jika alamat tersebut terdaftar");
        }
        assertThat(SMTP.waitForIncomingEmail(3000, 1)).as("no email for an unknown or inactive address").isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM password_reset_tokens WHERE id_user = ?", Integer.class, inactive.getId())).isZero();
    }

    @Test
    @DisplayName("An administrator sends the reset email from the user page")
    void administratorSendsResetEmail() throws Exception {
        User user = createUser(true);
        loginAsAdmin();
        navigateTo("/users/" + user.getId());
        page.locator("#btn-send-password-reset").click();
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/users/" + user.getId());
        assertThat(page.locator("main")).containsText("Email reset password dikirim ke " + user.getEmail());

        MimeMessage mail = awaitSingleMail();
        assertThat(mail.getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo(user.getEmail());
        linkPath((String) mail.getContent());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT requested_by FROM password_reset_tokens WHERE id_user = ?", String.class, user.getId()))
                .isEqualTo("admin");
    }

    @Test
    @DisplayName("The login page shows the reset link when reset by email is configured")
    void loginPageShowsResetLink() {
        navigateTo("/login");
        assertThat(page.locator("#link-forgot-password")).hasAttribute("href", "/forgot-password");
    }

    // ==================== helpers ====================

    private String requestAndReadLink(User user) throws Exception {
        navigateTo("/forgot-password");
        page.locator("#email").fill(user.getEmail());
        page.locator("#btn-request-reset").click();
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/forgot-password?sent");
        return linkPath((String) awaitSingleMail().getContent());
    }

    private MimeMessage awaitSingleMail() {
        assertThat(SMTP.waitForIncomingEmail(15000, 1)).as("reset email received").isTrue();
        MimeMessage[] received = SMTP.getReceivedMessages();
        assertThat(received).hasSize(1);
        return received[0];
    }

    private static String linkPath(String body) {
        Matcher matcher = LINK.matcher(body);
        assertThat(matcher.find()).as("link built from the configured base URL in: " + body).isTrue();
        return matcher.group(1);
    }

    /** Submits the reset form the way the page does, with the CSRF token of a freshly rendered form. */
    private APIResponse postReset(String rawToken, String password) {
        Page formPage = context.newPage();
        try {
            formPage.navigate(baseUrl() + "/forgot-password");
            String csrf = formPage.locator("input[name='_csrf']").getAttribute("value");
            return formPage.request().post(baseUrl() + "/reset-password", RequestOptions.create()
                    .setForm(com.microsoft.playwright.options.FormData.create()
                            .set("_csrf", csrf)
                            .set("token", rawToken)
                            .set("newPassword", password)
                            .set("confirmPassword", password))
                    .setMaxRedirects(0));
        } finally {
            formPage.close();
        }
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

    private User createUser(boolean active) {
        String id = uid();
        User user = new User();
        user.setUsername("reset-" + id);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setFullName("Pengguna Reset " + id);
        user.setEmail("reset-" + id + "@example.com");
        user.setActive(active);
        user.setRoles(Set.of(Role.STAFF), "system");
        return userRepository.save(user);
    }

    private User reload(User user) {
        return userRepository.findById(user.getId()).orElseThrow();
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static String randomIp() {
        return "10.62." + RANDOM.nextInt(256) + "." + RANDOM.nextInt(256);
    }

    private static String uid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
