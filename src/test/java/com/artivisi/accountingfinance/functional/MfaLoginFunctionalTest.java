package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.security.MfaAttemptService;
import com.artivisi.accountingfinance.security.MfaService;
import com.artivisi.accountingfinance.security.TotpService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The real filter chain with MFA enabled: the login form leads to the second
 * step, a wrong code keeps the session out, a recovery code gets in, and a role
 * that requires MFA but has not enrolled is sent to the setup page instead of the
 * dashboard.
 *
 * <p>The class disables nothing: the functional profile sets
 * {@code app.mfa.enabled=false} for every other test, and the properties here
 * turn it back on for a dedicated Spring context.
 *
 * <p>Enrolment refuses to run without {@code app.encryption.key}, since the secret
 * is stored through {@code EncryptedStringConverter}, so this context carries the
 * same test key the other encryption-aware functional classes use.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.mfa.enabled=true",
        "app.mfa.required-roles=ADMIN,OWNER"
})
@TestPropertySource(properties = "app.encryption.key=Kea3OzaaPR89QOebS1b427C8xxRoztOft8rlSEL3W7k=")
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("functional")
@DisplayName("MFA login flow")
class MfaLoginFunctionalTest extends PlaywrightTestBase {

    private static final String PASSWORD = "Rahasia123!";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TotpService totpService;

    @Autowired
    private MfaService mfaService;

    @Autowired
    private MfaAttemptService mfaAttemptService;

    @BeforeEach
    void clearMfaAttempts() {
        mfaAttemptService.clear();
    }

    @Test
    @DisplayName("a code from the authenticator app completes the login")
    void codeCompletesTheLogin() {
        User user = mfaUser(Role.STAFF);
        String secret = enableMfa(user);

        loginExpectingSecondFactor(user.getUsername());

        page.fill("#code", totpService.generateCode(secret, totpService.currentStep()));
        page.click("[data-testid='mfa-verify-submit']");

        page.waitForURL("**/dashboard", new com.microsoft.playwright.Page.WaitForURLOptions().setTimeout(30000));
        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*/dashboard"));
    }

    @Test
    @DisplayName("a wrong code leaves the session on the verification page")
    void wrongCodeKeepsTheSessionOut() {
        User user = mfaUser(Role.STAFF);
        enableMfa(user);

        loginExpectingSecondFactor(user.getUsername());

        page.fill("#code", "000000");
        page.click("[data-testid='mfa-verify-submit']");

        assertThat(page.locator("[data-testid='mfa-error']")).isVisible();
        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*/mfa-verify"));

        // The session still holds only the password, so the rest of the application must
        // stay behind the second factor, not just the page that happens to be rendered.
        APIResponse dashboard = page.request().get(baseUrl() + "/dashboard",
                RequestOptions.create().setMaxRedirects(0));
        assertEquals(302, dashboard.status(), "An unverified session reached /dashboard");
        assertTrue(dashboard.headers().get("location").endsWith("/mfa-verify"),
                "Expected /mfa-verify, got " + dashboard.headers().get("location"));
    }

    @Test
    @DisplayName("Keluar on the verification page ends the session")
    void logoutFromVerificationPageEndsTheSession() {
        User user = mfaUser(Role.STAFF);
        enableMfa(user);

        loginExpectingSecondFactor(user.getUsername());

        page.click("[data-testid='mfa-logout']");
        page.waitForURL(java.util.regex.Pattern.compile(".*/login\\?logout"),
                new com.microsoft.playwright.Page.WaitForURLOptions().setTimeout(30000));

        // The session is gone: the dashboard now lands on the login page, not on the
        // second factor, which is what a surviving half-authenticated session would show.
        navigateTo("/dashboard");
        waitForPageLoad();
        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*/login.*"));
    }

    @Test
    @DisplayName("a recovery code can be used instead of the authenticator")
    void recoveryCodeCompletesTheLogin() {
        User user = mfaUser(Role.STAFF);
        enableMfa(user);
        List<String> recoveryCodes = mfaService.regenerateRecoveryCodes(user, "test");

        loginExpectingSecondFactor(user.getUsername());

        page.click("summary");
        page.fill("#recoveryCode", recoveryCodes.get(0));
        page.click("[data-testid='mfa-recovery-submit']");

        page.waitForURL("**/dashboard", new com.microsoft.playwright.Page.WaitForURLOptions().setTimeout(30000));
        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*/dashboard"));
    }

    @Test
    @DisplayName("a role that requires MFA without enrolment is sent to the setup page, which stays put and enrols from its QR code")
    void requiredRoleWithoutEnrolmentIsSentToSetup() {
        User admin = mfaUser(Role.ADMIN);
        List<String> navigations = new java.util.concurrent.CopyOnWriteArrayList<>();
        page.onFrameNavigated(frame -> {
            if (frame == page.mainFrame()) {
                navigations.add(frame.url());
            }
        });

        navigateTo("/login");
        waitForPageLoad();
        page.fill("input[name='username']", admin.getUsername());
        page.fill("input[name='password']", PASSWORD);
        page.click("button[type='submit']");

        page.waitForURL("**/settings/mfa?required=true",
                new com.microsoft.playwright.Page.WaitForURLOptions().setTimeout(30000));
        assertThat(page.locator("[data-testid='mfa-required-notice']")).isVisible();
        assertThat(page.locator("[data-testid='mfa-secret']")).isVisible();

        // The layout loads fragments with hx-trigger="load"; refused with 403, they must not reload the page.
        int settled = navigations.size();
        page.waitForTimeout(3000);
        assertEquals(settled, navigations.size(), "setup page reloaded: " + navigations);

        Locator qr = page.locator("[data-testid='mfa-qr']");
        assertTrue(qr.getAttribute("src").startsWith("data:image/svg+xml;base64,"));
        assertTrue(((Number) qr.evaluate("img => img.complete ? img.naturalWidth : 0")).intValue() > 0,
                "QR image did not render");

        String secret = page.locator("[data-testid='mfa-secret']").textContent().trim();
        long step = System.currentTimeMillis() / 1000 / totpService.getStepSeconds();
        page.locator("[data-testid='mfa-enable-code']").fill(totpService.generateCode(secret, step));
        // Enter and then the button: the second submit must be cancelled. A listener added after
        // submit-once.js records whether each submit arrives cancelled and holds the page in place.
        Object prevented = page.evaluate("""
                () => {
                    const form = document.querySelector("[data-testid='mfa-enable-code']").form;
                    const seen = [];
                    const probe = e => { seen.push(e.defaultPrevented); e.preventDefault(); };
                    document.addEventListener('submit', probe);
                    form.requestSubmit();
                    form.requestSubmit();
                    document.removeEventListener('submit', probe);
                    delete form.dataset.submitted;
                    form.querySelectorAll('button').forEach(b => b.disabled = false);
                    return seen;
                }""");
        assertEquals(List.of(false, true), prevented, "second submit was not cancelled");
        page.locator("[data-testid='mfa-enable-submit']").click();
        page.waitForURL("**/settings/mfa/enable");
        waitForPageLoad();
        assertThat(page.locator("[data-testid='mfa-recovery-codes']")).isVisible();
        assertTrue(userRepository.findByUsername(admin.getUsername()).orElseThrow().getMfaEnabled());
    }

    private void loginExpectingSecondFactor(String username) {
        navigateTo("/login");
        waitForPageLoad();
        page.fill("input[name='username']", username);
        page.fill("input[name='password']", PASSWORD);
        page.click("button[type='submit']");
        page.waitForURL("**/mfa-verify", new com.microsoft.playwright.Page.WaitForURLOptions().setTimeout(30000));
    }

    /**
     * Writes the secret straight onto the row so the tests control the counter
     * themselves: the enrolment flow is covered by MfaServiceTest, here the login
     * chain is the subject.
     */
    private String enableMfa(User user) {
        String secret = totpService.generateSecret();
        user.setMfaSecret(secret);
        user.setMfaEnabled(true);
        user.setMfaLastUsedStep(null);
        userRepository.save(user);
        return secret;
    }

    private User mfaUser(Role role) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setUsername("mfa-it-" + suffix);
        user.setFullName("MFA IT " + suffix);
        user.setEmail("mfa-it-" + suffix + "@example.test");
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setActive(true);
        user.addRole(role, "test");
        return userRepository.save(user);
    }
}
