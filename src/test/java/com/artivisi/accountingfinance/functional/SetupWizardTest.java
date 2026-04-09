package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests for the first-run setup wizard.
 *
 * Each test starts with an empty users table (the Flyway-seeded admin is wiped
 * via TRUNCATE...CASCADE in @BeforeEach). The Spring context is dirtied so this
 * destructive setup does not bleed into other test classes.
 */
@DisplayName("First-Run Setup Wizard")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SetupWizardTest extends PlaywrightTestBase {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChartOfAccountRepository chartOfAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void wipeUsers() {
        // Remove the Flyway-seeded admin so the setup wizard activates.
        // CASCADE clears user_roles and other dependent rows.
        jdbcTemplate.execute("TRUNCATE TABLE users CASCADE");
        assertThat(userRepository.count()).isZero();
    }

    @Test
    @DisplayName("Root URL redirects to /setup when no users exist")
    void rootRedirectsToSetup() {
        page.navigate(baseUrl() + "/");
        page.waitForURL("**/setup");
        assertThat(page.url()).endsWith("/setup");
        assertThat(page.title()).contains("Setup Awal");
    }

    @Test
    @DisplayName("Submitting the wizard creates admin user and loads seed pack")
    void submitWizardCreatesAdminAndLoadsSeed() {
        page.navigate(baseUrl() + "/setup");
        page.waitForSelector("form");

        page.fill("input[name='username']", "wizardadmin");
        page.fill("input[name='fullName']", "Wizard Admin");
        page.fill("input[name='email']", "wizard@example.test");
        page.fill("input[name='password']", "wizardpass123");
        page.selectOption("select[name='industryPack']", "it-service");

        page.click("button#btn-setup");
        page.waitForURL("**/login**");

        // Verify user persisted with correct attributes and role.
        Optional<User> created = userRepository.findByUsername("wizardadmin");
        assertThat(created).as("admin user must be persisted").isPresent();
        User u = created.get();
        assertThat(u.getFullName()).isEqualTo("Wizard Admin");
        assertThat(u.getEmail()).isEqualTo("wizard@example.test");
        assertThat(u.getActive()).isTrue();
        assertThat(u.getRoles()).containsExactly(Role.ADMIN);

        // Password must be BCrypt-encoded (not stored plaintext).
        assertThat(u.getPassword()).isNotEqualTo("wizardpass123");
        assertThat(passwordEncoder.matches("wizardpass123", u.getPassword())).isTrue();

        // Seed pack must have populated chart of accounts.
        assertThat(chartOfAccountRepository.count())
                .as("it-service seed pack should have loaded chart of accounts")
                .isGreaterThan(0);

        // Total users should be exactly one — the wizard admin.
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Newly-created admin can log in")
    void newAdminCanLogIn() {
        page.navigate(baseUrl() + "/setup");
        page.fill("input[name='username']", "loginadmin");
        page.fill("input[name='fullName']", "Login Admin");
        page.fill("input[name='password']", "loginpass123");
        page.selectOption("select[name='industryPack']", "coffee-shop");
        page.click("button#btn-setup");
        page.waitForURL("**/login**");

        login("loginadmin", "loginpass123");
        assertThat(page.url()).contains("/dashboard");
    }

    @Test
    @DisplayName("GET /setup redirects to /login when a user already exists")
    void setupRedirectsAfterCompletion() {
        // Run the wizard once.
        page.navigate(baseUrl() + "/setup");
        page.fill("input[name='username']", "firstadmin");
        page.fill("input[name='fullName']", "First Admin");
        page.fill("input[name='password']", "firstpass123");
        page.selectOption("select[name='industryPack']", "online-seller");
        page.click("button#btn-setup");
        page.waitForURL("**/login**");

        assertThat(userRepository.count()).isEqualTo(1);

        // Re-visiting /setup should now redirect to /login (not show the wizard form).
        page.navigate(baseUrl() + "/setup");
        page.waitForURL("**/login**");
        assertThat(page.url()).contains("/login");
        assertThat(page.locator("input[name='username']").isVisible()).isTrue();
        assertThat(page.locator("select[name='industryPack']").count())
                .as("wizard form must not be present")
                .isZero();
    }
}
