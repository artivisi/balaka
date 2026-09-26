package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;
import java.util.Optional;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests for the first-run setup wizard, which is only reachable while the users
 * table is empty. Each test truncates users (CASCADE) and loads a seed pack; neither can be
 * restored row-for-row, so the state is confined instead of snapshotted:
 * - the test.context property gives this class a Spring context cache key no other class has,
 *   so its PostgreSQL container is never handed to another class;
 * - AFTER_CLASS dirtying closes that context (and its container) once the class finishes.
 * Test classes run sequentially in one fork, so nothing else can observe the empty-users or
 * re-seeded state.
 */
@DisplayName("First-Run Setup Wizard")
@TestPropertySource(properties = "test.context=setup-wizard")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SetupWizardTest extends PlaywrightTestBase {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChartOfAccountRepository chartOfAccountRepository;

    @Autowired
    private JournalTemplateRepository journalTemplateRepository;

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

        // it-service seed pack loaded: its accounts and its own templates exist
        assertThat(chartOfAccountRepository.findByAccountCode("1.1.02")).isPresent();
        assertThat(journalTemplateRepository.findByTemplateNameAndIsCurrentVersionTrue("Pendapatan Jasa Konsultasi"))
                .as("it-service template must be loaded").isPresent();

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

        assertThat(journalTemplateRepository.findByTemplateNameAndIsCurrentVersionTrue("Pembelian Bahan Baku - Tunai"))
                .as("coffee-shop template must be loaded").isPresent();

        login("loginadmin", "loginpass123");
        assertThat(page.url()).endsWith("/dashboard");
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
        assertThat(journalTemplateRepository.findByTemplateNameAndIsCurrentVersionTrue("Penjualan Shopee"))
                .as("online-seller template must be loaded").isPresent();

        // Re-visiting /setup should now redirect to /login (not show the wizard form).
        page.navigate(baseUrl() + "/setup");
        page.waitForURL("**/login**");
        assertThat(page.url()).contains("/login");
        assertThat(page.locator("input[name='username']").isVisible()).isTrue();
        assertThat(userRepository.findByUsername("firstadmin")).isPresent();
        assertThat(page.locator("select[name='industryPack']").count())
                .as("wizard form must not be present")
                .isZero();
    }
    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "it-service,    1.1.10, 2.1.01, 2.1.03, 1.1.25",
            "online-seller, 1.1.10, 2.1.02, 2.1.03, 1.1.25",
            "coffee-shop,   1.1.10, 2.1.02, 2.1.03, 1.1.25",
            "campus,        1.1.10, 2.1.01,       ,       "
    })
    @DisplayName("Seed pack configures industry and posting accounts; the admin lands on a dashboard with AR/AP KPIs")
    void seedPackConfiguresPostingAccounts(String pack, String receivable, String payable,
                                           String outputTax, String inputTax) {
        runWizard("admin-" + pack, pack);

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM company_config", Long.class)).isEqualTo(1L);
        Map<String, Object> config = jdbcTemplate.queryForMap("""
                SELECT c.industry, ar.account_code AS receivable, ap.account_code AS payable,
                       ot.account_code AS output_tax, it.account_code AS input_tax
                FROM company_config c
                LEFT JOIN chart_of_accounts ar ON ar.id = c.id_receivable_account
                LEFT JOIN chart_of_accounts ap ON ap.id = c.id_payable_account
                LEFT JOIN chart_of_accounts ot ON ot.id = c.id_output_tax_account
                LEFT JOIN chart_of_accounts it ON it.id = c.id_input_tax_account""");
        assertThat(config.get("industry")).isEqualTo(pack);
        assertThat(config.get("receivable")).isEqualTo(receivable);
        assertThat(config.get("payable")).isEqualTo(payable);
        assertThat(config.get("output_tax")).isEqualTo(outputTax);
        assertThat(config.get("input_tax")).isEqualTo(inputTax);

        login("admin-" + pack, "wizardpass123");
        assertThat(kpiValue("Piutang Usaha")).hasText("Rp 0,0jt");
        assertThat(kpiValue("Hutang Usaha")).hasText("Rp 0,0jt");
        assertThat(page.locator("[data-testid='kpi-config-error']")).hasCount(0);
    }

    private void runWizard(String username, String pack) {
        page.navigate(baseUrl() + "/setup");
        page.fill("input[name='username']", username);
        page.fill("input[name='fullName']", "Admin " + pack);
        page.fill("input[name='password']", "wizardpass123");
        page.selectOption("select[name='industryPack']", pack);
        page.click("button#btn-setup");
        page.waitForURL("**/login**");
    }

    private Locator kpiValue(String label) {
        return page.locator("#dashboard-kpis p.text-xs.font-medium:text-is('" + label + "') + p");
    }
}
