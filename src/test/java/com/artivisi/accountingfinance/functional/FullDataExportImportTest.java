package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.Client;
import com.artivisi.accountingfinance.enums.AccountType;
import com.artivisi.accountingfinance.enums.NormalBalance;
import com.artivisi.accountingfinance.functional.page.LoginPage;
import com.artivisi.accountingfinance.repository.*;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.Download;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full round-trip test for data export/import functionality.
 * Tests the complete workflow:
 * 1. Start from seed migration data
 * 2. Add custom data via repository (COA, client)
 * 3. Export all data
 * 4. Import (truncate + load)
 * 5. Verify all data preserved
 */
@DisplayName("Full Data Export/Import Round-Trip Test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FullDataExportImportTest extends PlaywrightTestBase {

    @Autowired
    private ChartOfAccountRepository accountRepository;
    @Autowired
    private JournalTemplateRepository templateRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private JournalEntryRepository journalEntryRepository;
    @Autowired
    private ClientRepository clientRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private LoginPage loginPage;

    // Store counts before export for verification
    private static int originalAccountCount;
    private static int originalTemplateCount;
    private static int originalTransactionCount;
    private static int originalJournalEntryCount;
    private static int originalClientCount;

    // Custom data identifiers for verification
    private static final String CUSTOM_ACCOUNT_CODE = "9999";
    private static final String CUSTOM_ACCOUNT_NAME = "Test Export Import Account";
    private static final String CUSTOM_CLIENT_CODE = "TEST-EXP-IMP";
    private static final String CUSTOM_CLIENT_NAME = "Test Export Import Client";

    // Downloaded export file
    private static Path exportedZipPath;

    @BeforeEach
    void setUp() {
        loginPage = new LoginPage(page, baseUrl());
        loginPage.navigate().loginAsAdmin();
    }

    @Test
    @Order(1)
    @DisplayName("Step 1: Verify initial seed data exists and add custom data")
    void step1_verifyInitialSeedDataAndAddCustomData() {
        // Verify seed data from migrations
        originalAccountCount = (int) accountRepository.count();
        originalTemplateCount = (int) templateRepository.count();
        originalTransactionCount = (int) transactionRepository.count();
        originalJournalEntryCount = (int) journalEntryRepository.count();
        originalClientCount = (int) clientRepository.count();

        assertThat(originalAccountCount).isGreaterThan(0);
        assertThat(originalTemplateCount).isGreaterThan(0);

        // Add custom account via repository
        ChartOfAccount customAccount = new ChartOfAccount();
        customAccount.setAccountCode(CUSTOM_ACCOUNT_CODE);
        customAccount.setAccountName(CUSTOM_ACCOUNT_NAME);
        customAccount.setAccountType(AccountType.ASSET);
        customAccount.setNormalBalance(NormalBalance.DEBIT);
        customAccount.setActive(true);
        accountRepository.save(customAccount);

        // Add custom client via repository
        Client customClient = new Client();
        customClient.setCode(CUSTOM_CLIENT_CODE);
        customClient.setName(CUSTOM_CLIENT_NAME);
        customClient.setEmail("test@example.com");
        customClient.setActive(true);
        clientRepository.save(customClient);

        // Verify custom data created
        assertThat(accountRepository.findByAccountCode(CUSTOM_ACCOUNT_CODE)).isPresent();
        assertThat(clientRepository.findByCode(CUSTOM_CLIENT_CODE)).isPresent();
    }

    @Test
    @Order(2)
    @DisplayName("Step 2: Export all data and verify ZIP contents")
    void step2_exportAllData() throws IOException {
        page.navigate(baseUrl() + "/settings/export");
        page.waitForLoadState();

        // Wait for download when clicking export button
        Download download = page.waitForDownload(() -> {
            page.locator("button:has-text('Ekspor Semua Data')").click();
        });

        // Save the download to a temp file
        exportedZipPath = Files.createTempFile("export-test-", ".zip");
        download.saveAs(exportedZipPath);

        // Verify it's a valid ZIP file with expected CSV files
        Set<String> entries = new HashSet<>();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(exportedZipPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.add(entry.getName());
                zis.closeEntry();
            }
        }

        // Check for numbered CSV files
        assertThat(entries).contains("MANIFEST.md");
        assertThat(entries).contains("01_company_config.csv");
        assertThat(entries).contains("02_chart_of_accounts.csv");
        assertThat(entries).contains("04_journal_templates.csv");
        assertThat(entries).contains("07_clients.csv");
    }

    @Test
    @Order(3)
    @DisplayName("Step 3: Import data (full replace)")
    void step3_importData() throws IOException {
        // Ensure we have the exported file
        assertThat(exportedZipPath).isNotNull();
        assertThat(Files.exists(exportedZipPath)).isTrue();

        page.navigate(baseUrl() + "/settings/import");
        page.waitForLoadState();

        // Verify import page displays
        assertThat(page.locator("h2:has-text('Import Data Lengkap')")).isVisible();

        // Upload the exported ZIP file
        page.locator("input[type='file']").setInputFiles(exportedZipPath.toAbsolutePath());

        // Click import button (with confirmation)
        page.onDialog(dialog -> dialog.accept());

        // Import can take a while, increase timeout
        page.locator("button:has-text('Import Data')").click(
            new com.microsoft.playwright.Locator.ClickOptions().setTimeout(60000)
        );

        // Wait for page to load after import (with longer timeout)
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE,
            new com.microsoft.playwright.Page.WaitForLoadStateOptions().setTimeout(60000));

        // Give it time to process
        page.waitForTimeout(2000);

        // Check for success or error message
        var successLocator = page.locator(".bg-green-100");
        var errorLocator = page.locator(".bg-red-100");

        if (errorLocator.count() > 0) {
            String errorText = errorLocator.textContent();
            System.err.println("Import error: " + errorText);
        }

        // Verify success message contains "Import berhasil" or similar
        assertThat(successLocator).hasCount(1, new com.microsoft.playwright.assertions.LocatorAssertions.HasCountOptions().setTimeout(30000));

        // Reset admin password after import (import sets random UUID password for security)
        // This is needed so subsequent tests can login with "admin"/"admin"
        userRepository.findByUsername("admin").ifPresent(admin -> {
            admin.setPassword(passwordEncoder.encode("admin"));
            userRepository.save(admin);
        });
    }

    @Test
    @Order(4)
    @DisplayName("Step 4: Verify data counts preserved after import")
    void step4_verifyDataCountsPreserved() {
        // Counts should be same as after adding custom data
        int accountCount = (int) accountRepository.count();
        int templateCount = (int) templateRepository.count();
        int clientCount = (int) clientRepository.count();

        // Account count should include original + 1 custom
        assertThat(accountCount).isEqualTo(originalAccountCount + 1);
        assertThat(templateCount).isEqualTo(originalTemplateCount);
        // Client count should include original + 1 custom
        assertThat(clientCount).isEqualTo(originalClientCount + 1);
    }

    @Test
    @Order(5)
    @DisplayName("Step 5: Verify custom account preserved after import")
    void step5_verifyCustomAccountPreserved() {
        var account = accountRepository.findByAccountCode(CUSTOM_ACCOUNT_CODE);
        assertThat(account).isPresent();
        assertThat(account.get().getAccountName()).isEqualTo(CUSTOM_ACCOUNT_NAME);
    }

    @Test
    @Order(6)
    @DisplayName("Step 6: Verify custom client preserved after import")
    void step6_verifyCustomClientPreserved() {
        var client = clientRepository.findByCode(CUSTOM_CLIENT_CODE);
        assertThat(client).isPresent();
        assertThat(client.get().getName()).isEqualTo(CUSTOM_CLIENT_NAME);
    }

    @Test
    @Order(7)
    @DisplayName("Step 7: Verify seed templates still exist after import")
    void step7_verifySeedTemplatesExist() {
        // Check some seed templates still exist
        var templates = templateRepository.findAll();
        assertThat(templates).isNotEmpty();
        assertThat(templates.size()).isEqualTo(originalTemplateCount);
    }

    @Test
    @Order(8)
    @DisplayName("Step 8: Verify user can still login after import")
    void step8_verifyUserCanStillLogin() {
        // Logout first
        page.navigate(baseUrl() + "/logout");
        page.waitForLoadState();

        // Login again
        loginPage.navigate().loginAsAdmin();

        // Should be on dashboard
        assertThat(page.locator("h1:has-text('Dashboard')")).isVisible();
    }

    @Nested
    @DisplayName("Import Page UI Tests")
    class ImportPageUITests {

        @Test
        @DisplayName("Should display import page correctly")
        void shouldDisplayImportPage() {
            page.navigate(baseUrl() + "/settings/import");
            page.waitForLoadState();

            assertThat(page.locator("h2:has-text('Import Data Lengkap')")).isVisible();
            assertThat(page.locator("input[type='file'][accept='.zip']")).isVisible();
            assertThat(page.locator("button:has-text('Import Data')")).isVisible();
        }

        @Test
        @DisplayName("Should display warning message")
        void shouldDisplayWarningMessage() {
            page.navigate(baseUrl() + "/settings/import");
            page.waitForLoadState();

            assertThat(page.locator("text=Peringatan")).isVisible();
            assertThat(page.locator("text=menghapus SELURUH data")).isVisible();
        }

        @Test
        @DisplayName("Should display instructions")
        void shouldDisplayInstructions() {
            page.navigate(baseUrl() + "/settings/import");
            page.waitForLoadState();

            assertThat(page.locator("h3:has-text('Petunjuk')")).isVisible();
            assertThat(page.locator("text=Format File")).isVisible();
            assertThat(page.locator("text=Proses Import")).isVisible();
        }

        @Test
        @DisplayName("Should have link back to export page")
        void shouldHaveLinkToExportPage() {
            page.navigate(baseUrl() + "/settings/import");
            page.waitForLoadState();

            assertThat(page.locator("a[href='/settings/export']")).isVisible();
        }
    }
}
