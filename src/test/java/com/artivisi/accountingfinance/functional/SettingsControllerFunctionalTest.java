package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.CompanyBankAccount;
import com.artivisi.accountingfinance.entity.CompanyConfig;
import com.artivisi.accountingfinance.entity.DeviceToken;
import com.artivisi.accountingfinance.entity.TelegramUserLink;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.AuditEventType;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ApiClientRepository;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.CompanyBankAccountRepository;
import com.artivisi.accountingfinance.repository.CompanyConfigRepository;
import com.artivisi.accountingfinance.repository.DeviceTokenRepository;
import com.artivisi.accountingfinance.repository.SecurityAuditLogRepository;
import com.artivisi.accountingfinance.repository.TelegramUserLinkRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.service.CompanyBankAccountService;
import com.artivisi.accountingfinance.service.DeviceAuthService;
import com.artivisi.accountingfinance.service.DocumentStorageService;
import com.artivisi.accountingfinance.service.VersionInfoService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.FilePayload;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SettingsController: company settings, company logo, telegram link, device sessions,
 * API client validation, about/privacy pages and write authorization. Bank accounts and
 * audit log filtering are covered by ServiceSettingsComprehensiveTest.
 *
 * The company_config row is a singleton read by every other test class, so each test
 * restores it in {@link #restoreCompanyConfig()}. Telegram and device-session tests use
 * users created per test so the admin account's links and tokens stay untouched.
 */
@DisplayName("Settings Controller Tests")
@Import(ServiceTestDataInitializer.class)
class SettingsControllerFunctionalTest extends PlaywrightTestBase {

    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    @Autowired private CompanyConfigRepository companyConfigRepository;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private CompanyBankAccountRepository bankAccountRepository;
    @Autowired private CompanyBankAccountService bankAccountService;
    @Autowired private DocumentStorageService documentStorageService;
    @Autowired private TelegramUserLinkRepository telegramLinkRepository;
    @Autowired private DeviceAuthService deviceAuthService;
    @Autowired private DeviceTokenRepository deviceTokenRepository;
    @Autowired private ApiClientRepository apiClientRepository;
    @Autowired private SecurityAuditLogRepository auditLogRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private VersionInfoService versionInfoService;

    private CompanyConfigSnapshot originalConfig;
    private String csrfHeader;
    private String csrfToken;

    @BeforeEach
    void captureCompanyConfig() {
        originalConfig = CompanyConfigSnapshot.capture(companyConfigRepository);
    }

    @AfterEach
    void restoreCompanyConfig() throws IOException {
        String currentLogo = currentConfig().getCompanyLogoPath();
        if (currentLogo != null && !currentLogo.equals(originalConfig.companyLogoPath())) {
            documentStorageService.delete(currentLogo);
        }
        originalConfig.restore(companyConfigRepository, accountRepository);
    }

    // ==================== COMPANY SETTINGS ====================

    @Test
    @DisplayName("Should prefill the company form with the stored company config and link related settings")
    void shouldDisplayStoredCompanyConfig() {
        loginAsAdminWithCsrf();
        CompanyConfig config = currentConfig();

        navigateTo("/settings");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Pengaturan Perusahaan");
        assertThat(page.locator("#companyName")).hasValue(config.getCompanyName());
        assertThat(page.locator("#companyAddress")).hasValue(config.getCompanyAddress());
        assertThat(page.locator("#companyPhone")).hasValue(config.getCompanyPhone());
        assertThat(page.locator("#companyEmail")).hasValue(config.getCompanyEmail());
        assertThat(page.locator("#taxId")).hasValue(config.getTaxId());
        assertThat(page.locator("#npwp")).hasValue(config.getNpwp());
        assertThat(page.locator("#currencyCode")).hasValue(config.getCurrencyCode());
        assertThat(page.locator("#fiscalYearStartMonth")).hasValue(String.valueOf(config.getFiscalYearStartMonth()));
        assertThat(page.locator("#industry")).hasValue(config.getIndustry());
        assertThat(page.locator("#signingOfficerName")).hasValue(config.getSigningOfficerName());
        assertThat(page.locator("#signingOfficerTitle")).hasValue(config.getSigningOfficerTitle());
        assertThat(page.locator("#receivableAccountId")).hasValue(label(config.getReceivableAccountId()));
        assertThat(page.locator("#payableAccountId")).hasValue(label(config.getPayableAccountId()));
        assertThat(page.locator("#outputTaxAccountId")).hasValue(label(config.getOutputTaxAccountId()));
        assertThat(page.locator("#inputTaxAccountId")).hasValue(label(config.getInputTaxAccountId()));

        assertThat(page.locator("section a[href='/settings/telegram']")).isVisible();
        assertThat(page.locator("section a[href='/settings/about']")).isVisible();
        assertThat(page.locator("section a[href='/fiscal-periods']")).isVisible();
        assertThat(page.locator("section a[href='/settings/bank-accounts/new']")).hasText("+ Tambah Rekening");
    }

    @Test
    @DisplayName("Should save every company field and the receivable posting account, keeping other posting accounts")
    void shouldUpdateCompanySettings() {
        loginAsAdminWithCsrf();
        String suffix = shortId();
        ChartOfAccount employeeReceivable = accountRepository.findByAccountCode("1.1.11")
                .orElseThrow(() -> new IllegalStateException("Seeded account 1.1.11 missing"));

        navigateTo("/settings");
        waitForPageLoad();
        page.locator("#companyName").fill("PT Pengaturan " + suffix);
        page.locator("#companyAddress").fill("Jl. Pengujian " + suffix);
        page.locator("#companyPhone").fill("021-555-" + suffix.substring(0, 4));
        page.locator("#companyEmail").fill("stl-" + suffix + "@example.com");
        page.locator("#taxId").fill("TAX-" + suffix);
        page.locator("#npwp").fill("09.876.543.2-101.000");
        page.locator("#nitku").fill("0987654321010000000001");
        page.locator("#currencyCode").selectOption("USD");
        page.locator("#industry").selectOption("coffee-shop");
        page.locator("#fiscalYearStartMonth").selectOption("7");
        page.locator("#signingOfficerName").fill("Penanda " + suffix);
        page.locator("#signingOfficerTitle").fill("Komisaris " + suffix);
        page.locator("#receivableAccountId").fill("");
        page.locator("#receivableAccountId").pressSequentially("1.1.11");
        Locator result = page.locator("[data-testid='account-picker-result']:has-text('Piutang Karyawan')");
        result.click();
        assertThat(page.locator("#receivableAccountId")).hasValue("1.1.11 - Piutang Karyawan");
        page.locator("#btn-save-company").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/settings", page.url());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Pengaturan perusahaan berhasil disimpan");
        assertThat(page.locator("#companyName")).hasValue("PT Pengaturan " + suffix);
        assertThat(page.locator("#receivableAccountId")).hasValue("1.1.11 - Piutang Karyawan");

        CompanyConfig saved = currentConfig();
        assertEquals(originalConfig.id(), saved.getId());
        assertEquals("PT Pengaturan " + suffix, saved.getCompanyName());
        assertEquals("Jl. Pengujian " + suffix, saved.getCompanyAddress());
        assertEquals("021-555-" + suffix.substring(0, 4), saved.getCompanyPhone());
        assertEquals("stl-" + suffix + "@example.com", saved.getCompanyEmail());
        assertEquals("TAX-" + suffix, saved.getTaxId());
        assertEquals("09.876.543.2-101.000", saved.getNpwp());
        assertEquals("0987654321010000000001", saved.getNitku());
        assertEquals("USD", saved.getCurrencyCode());
        assertEquals("coffee-shop", saved.getIndustry());
        assertEquals(7, saved.getFiscalYearStartMonth());
        assertEquals("Penanda " + suffix, saved.getSigningOfficerName());
        assertEquals("Komisaris " + suffix, saved.getSigningOfficerTitle());
        assertEquals(employeeReceivable.getId(), saved.getReceivableAccountId());
        assertEquals(originalConfig.payableAccountId(), saved.getPayableAccountId());
        assertEquals(originalConfig.outputTaxAccountId(), saved.getOutputTaxAccountId());
        assertEquals(originalConfig.inputTaxAccountId(), saved.getInputTaxAccountId());
        assertEquals(originalConfig.establishedDate(), saved.getEstablishedDate());
        assertEquals(originalConfig.isPkp(), saved.getIsPkp());

        assertTrue(auditLogRepository.findByUsernameAndEventTypeAndTimestampAfter(
                        "admin", AuditEventType.SETTINGS_CHANGE, LocalDateTime.now().minusMinutes(5)).stream()
                .anyMatch(l -> ("Company settings updated: PT Pengaturan " + suffix).equals(l.getDetails())));
    }

    @Test
    @DisplayName("Should reject a blank company name and an over-long signing officer title without saving")
    void shouldRejectInvalidCompanySettings() {
        loginAsAdminWithCsrf();
        clearLogo();

        navigateTo("/settings");
        waitForPageLoad();
        page.locator("#logoFile").setInputFiles(new FilePayload("logo.png", "image/png", PNG));
        page.locator("#btn-upload-logo").click();
        waitForPageLoad();
        page.locator("#companyName").evaluate("el => el.removeAttribute('required')");
        page.locator("#companyName").fill("");
        page.locator("#signingOfficerTitle").fill("J".repeat(101));
        page.locator("#btn-save-company").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/settings/company", page.url());
        Locator errors = page.locator("form[action='/settings/company'] ul li");
        assertThat(errors).hasCount(2);
        assertThat(errors.filter(new Locator.FilterOptions().setHasText("Company name is required"))).hasCount(1);
        assertThat(errors.filter(new Locator.FilterOptions()
                .setHasText("Signing officer title must not exceed 100 characters"))).hasCount(1);
        assertThat(page.locator("#companyName")).hasClass(Pattern.compile(".*border-red-500.*"));
        assertThat(page.locator("#signingOfficerTitle")).hasValue("J".repeat(101));
        assertThat(page.locator("#company-logo-preview")).isVisible();

        CompanyConfig unchanged = currentConfig();
        assertEquals(originalConfig.companyName(), unchanged.getCompanyName());
        assertEquals(originalConfig.signingOfficerTitle(), unchanged.getSigningOfficerTitle());
    }

    // ==================== COMPANY LOGO ====================

    @Test
    @DisplayName("Should upload, serve and delete the company logo")
    void shouldUploadServeAndDeleteLogo() {
        loginAsAdminWithCsrf();
        clearLogo();

        navigateTo("/settings");
        waitForPageLoad();
        assertThat(page.locator("#company-logo-preview")).hasCount(0);
        assertEquals(404, page.request().get(baseUrl() + "/settings/company/logo").status());

        page.locator("#logoFile").setInputFiles(new FilePayload("logo.png", "image/png", PNG));
        page.locator("#btn-upload-logo").click();
        waitForPageLoad();

        assertThat(page.locator("[data-testid='flash-success']")).hasText("Logo perusahaan berhasil diupload");
        assertThat(page.locator("#company-logo-preview")).isVisible();
        String storedPath = currentConfig().getCompanyLogoPath();
        assertNotNull(storedPath);
        assertTrue(storedPath.endsWith(".png"), storedPath);
        APIResponse logo = page.request().get(baseUrl() + "/settings/company/logo");
        assertEquals(200, logo.status());
        assertEquals("image/png", logo.headers().get("content-type"));
        assertArrayEquals(PNG, logo.body());

        page.onDialog(dialog -> dialog.accept());
        page.locator("#btn-delete-logo").click();
        waitForPageLoad();

        assertThat(page.locator("[data-testid='flash-success']")).hasText("Logo perusahaan berhasil dihapus");
        assertThat(page.locator("#company-logo-preview")).hasCount(0);
        assertNull(currentConfig().getCompanyLogoPath());
        assertEquals(404, page.request().get(baseUrl() + "/settings/company/logo").status());
    }

    @Test
    @DisplayName("Should keep the uploaded logo when the company form is saved")
    void shouldKeepLogoWhenSavingCompanySettings() {
        loginAsAdminWithCsrf();
        clearLogo();

        navigateTo("/settings");
        waitForPageLoad();
        page.locator("#logoFile").setInputFiles(new FilePayload("logo.png", "image/png", PNG));
        page.locator("#btn-upload-logo").click();
        waitForPageLoad();
        String storedPath = currentConfig().getCompanyLogoPath();
        assertNotNull(storedPath);

        page.locator("#companyPhone").fill("021-777-" + shortId().substring(0, 4));
        page.locator("#btn-save-company").click();
        waitForPageLoad();

        assertThat(page.locator("[data-testid='flash-success']")).hasText("Pengaturan perusahaan berhasil disimpan");
        assertEquals(storedPath, currentConfig().getCompanyLogoPath());
        assertThat(page.locator("#company-logo-preview")).isVisible();
        assertEquals(200, page.request().get(baseUrl() + "/settings/company/logo").status());
    }

    @Test
    @DisplayName("Should reject a logo that is not an image, and an empty upload, keeping the stored logo")
    void shouldRejectInvalidLogoUpload() {
        loginAsAdminWithCsrf();
        clearLogo();

        navigateTo("/settings");
        waitForPageLoad();
        page.locator("#logoFile").setInputFiles(new FilePayload("logo.txt", "text/plain", "not an image".getBytes()));
        page.locator("#btn-upload-logo").click();
        waitForPageLoad();

        assertThat(page.locator("[data-testid='flash-error']"))
                .hasText("Format file tidak didukung. Gunakan PNG, JPG, GIF, atau WebP.");
        assertNull(currentConfig().getCompanyLogoPath());

        page.locator("#btn-upload-logo").click();
        waitForPageLoad();

        assertThat(page.locator("[data-testid='flash-error']")).hasText("File tidak boleh kosong");
        assertNull(currentConfig().getCompanyLogoPath());
        assertThat(page.locator("#company-logo-preview")).hasCount(0);
    }

    // ==================== TELEGRAM ====================

    @Test
    @DisplayName("Should show the disabled-bot notice and store a 6-digit verification code for the current user")
    void shouldGenerateTelegramVerificationCode() {
        User user = createUser(Role.ADMIN);
        loginAsWithCsrf(user);

        navigateTo("/settings/telegram");
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Pengaturan Telegram");
        assertThat(page.locator("#telegram-settings-content")).containsText("Telegram Bot Tidak Aktif");
        assertThat(page.locator("form[action='/settings/telegram/generate-code']")).hasCount(0);

        APIResponse response = post("/settings/telegram/generate-code", null);

        assertEquals(200, response.status());
        assertEquals(baseUrl() + "/settings/telegram", response.url());
        TelegramUserLink link = telegramLinkRepository.findByUser(user)
                .orElseThrow(() -> new AssertionError("No telegram link stored for " + user.getUsername()));
        assertTrue(link.getVerificationCode().matches("\\d{6}"), link.getVerificationCode());
        assertFalse(link.getIsActive());
        assertFalse(link.isLinked());
        assertTrue(link.getVerificationExpiresAt().isAfter(LocalDateTime.now().plusMinutes(14)));
        assertTrue(link.getVerificationExpiresAt().isBefore(LocalDateTime.now().plusMinutes(16)));
    }

    @Test
    @DisplayName("Should unlink the current user's telegram account")
    void shouldUnlinkTelegram() {
        User user = createUser(Role.ADMIN);
        TelegramUserLink link = new TelegramUserLink();
        link.setUser(user);
        link.setTelegramUserId(Math.abs(UUID.randomUUID().getMostSignificantBits()));
        link.setTelegramUsername("stl_" + shortId());
        link.setLinkedAt(LocalDateTime.now().minusDays(1));
        link.setIsActive(true);
        telegramLinkRepository.save(link);
        loginAsWithCsrf(user);

        APIResponse response = post("/settings/telegram/unlink", null);

        assertEquals(baseUrl() + "/settings/telegram", response.url());
        page.setContent(response.text());
        assertThat(page.locator("[data-testid='flash-success']")).containsText("Akun Telegram berhasil diputus");
        TelegramUserLink unlinked = telegramLinkRepository.findByUser(user).orElseThrow();
        assertFalse(unlinked.getIsActive());
        assertNull(unlinked.getTelegramUserId());
        assertNull(unlinked.getTelegramUsername());
        assertNull(unlinked.getLinkedAt());
    }

    // ==================== DEVICE SESSIONS ====================

    @Test
    @DisplayName("Should list the user's device session and revoke it so the token gets 401 on the API")
    void shouldRevokeDeviceSessionAndInvalidateToken() {
        User user = createUser(Role.ADMIN);
        String clientId = "stl-cli-" + shortId();
        DeviceToken token = deviceAuthService.createAccessToken(user, clientId, "Laptop " + clientId);
        assertEquals(200, apiStatus(token));
        loginAsWithCsrf(user);

        navigateTo("/settings/devices");
        waitForPageLoad();
        Locator row = page.locator("#device-tokens-table tbody tr");
        assertThat(row).hasCount(1);
        assertThat(row.locator("td").nth(0)).hasText("Laptop " + clientId);
        assertThat(row.locator("td").nth(1)).hasText(clientId);
        assertThat(row.locator("td").nth(3)).hasText("Aktif");

        page.onDialog(dialog -> dialog.accept());
        row.locator(".form-revoke-session button").click();
        waitForPageLoad();

        assertThat(page.locator("[data-testid='flash-success']")).containsText("Sesi perangkat berhasil dicabut");
        assertThat(page.locator("#empty-state")).hasText("Tidak ada sesi perangkat aktif");
        DeviceToken revoked = deviceTokenRepository.findById(token.getId()).orElseThrow();
        assertNotNull(revoked.getRevokedAt());
        assertEquals(user.getUsername(), revoked.getRevokedBy());
        assertEquals(401, apiStatus(token));
    }

    @Test
    @DisplayName("Should refuse to revoke another user's device session")
    void shouldNotRevokeAnotherUsersDeviceSession() {
        User owner = createUser(Role.ADMIN);
        DeviceToken foreign = deviceAuthService.createAccessToken(owner, "stl-cli-" + shortId(), "Owner device");
        loginAsWithCsrf(createUser(Role.ADMIN));

        APIResponse response = post("/settings/devices/" + foreign.getId() + "/revoke", null);

        assertEquals(baseUrl() + "/settings/devices", response.url());
        page.setContent(response.text());
        assertThat(page.locator("[data-testid='flash-error']")).containsText("Token tidak ditemukan");
        assertNull(deviceTokenRepository.findById(foreign.getId()).orElseThrow().getRevokedAt());
        assertEquals(200, apiStatus(foreign));
    }

    @Test
    @DisplayName("Should revoke all of the user's device sessions and leave other users' sessions valid")
    void shouldRevokeAllOwnDeviceSessions() {
        User user = createUser(Role.ADMIN);
        DeviceToken first = deviceAuthService.createAccessToken(user, "stl-cli-" + shortId(), "Laptop");
        DeviceToken second = deviceAuthService.createAccessToken(user, "stl-cli-" + shortId(), "Phone");
        DeviceToken other = deviceAuthService.createAccessToken(createUser(Role.ADMIN), "stl-cli-" + shortId(), "Other");
        loginAsWithCsrf(user);

        navigateTo("/settings/devices");
        waitForPageLoad();
        assertThat(page.locator("#device-tokens-table tbody tr")).hasCount(2);
        page.onDialog(dialog -> dialog.accept());
        page.locator("#form-revoke-all button").click();
        waitForPageLoad();

        assertThat(page.locator("[data-testid='flash-success']")).containsText("2 sesi perangkat berhasil dicabut");
        assertThat(page.locator("#empty-state")).isVisible();
        assertEquals(401, apiStatus(first));
        assertEquals(401, apiStatus(second));
        assertEquals(200, apiStatus(other));
    }

    // ==================== API CLIENTS ====================

    @Test
    @DisplayName("Should reject an API client with an unknown scope without creating it")
    void shouldRejectApiClientWithUnknownScope() {
        loginAsAdminWithCsrf();
        String name = "stl-client-" + shortId();
        User admin = userRepository.findByUsername("admin").orElseThrow();

        APIResponse response = post("/settings/api-clients", FormData.create()
                .set("name", name)
                .set("scopes", "transactions:post, bogus:scope")
                .set("userId", admin.getId().toString()));

        assertEquals(baseUrl() + "/settings/api-clients", response.url());
        page.setContent(response.text());
        assertThat(page.locator("[data-testid='flash-error']")).containsText("Scope tidak dikenal: bogus:scope");
        assertThat(page.locator("#new-credentials")).hasCount(0);
        assertTrue(apiClientRepository.findAll().stream().noneMatch(c -> name.equals(c.getName())));
    }

    // ==================== ABOUT / PRIVACY ====================

    @Test
    @DisplayName("Should show the running build's git commit and branch on the about page")
    void shouldDisplayVersionInfo() {
        loginAsAdminWithCsrf();

        navigateTo("/settings/about");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Tentang Aplikasi");
        assertThat(page.locator("#value-commit-id")).hasText(versionInfoService.getGitCommitShort());
        assertThat(page.locator("#btn-copy-commit")).hasAttribute("data-value", versionInfoService.getGitCommitId());
        assertThat(page.locator("#value-branch")).hasText(versionInfoService.getGitBranch());
        assertThat(page.locator("#value-build-date")).hasText(versionInfoService.getGitCommitDate());
        assertThat(page.locator("#tech-spring-boot")).isVisible();
        assertThat(page.locator("#tech-postgresql")).isVisible();
    }

    @Test
    @DisplayName("Should show every section of the privacy policy")
    void shouldDisplayPrivacyPolicy() {
        loginAsAdminWithCsrf();

        navigateTo("/settings/privacy");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Kebijakan Privasi");
        assertThat(page.locator("#privacy-page-content h2")).hasText(new String[]{
                "1. Pendahuluan", "2. Data yang Dikumpulkan", "3. Dasar Hukum Pemrosesan", "4. Penyimpanan Data",
                "5. Hak Anda", "6. Keamanan Data", "7. Pemberitahuan Pelanggaran Data", "8. Kontak"});
    }

    // ==================== AUTHORIZATION ====================

    @Test
    @DisplayName("Should refuse settings writes to a role with view-only settings access and hide the controls")
    void shouldRefuseSettingsWritesWithoutEditPermission() {
        String number = "STL" + System.nanoTime();
        CompanyBankAccount account = new CompanyBankAccount();
        account.setBankName("Bank Otorisasi " + number);
        account.setAccountNumber(number);
        account.setAccountName("PT Otorisasi");
        account = bankAccountService.create(account);
        bankAccountService.deactivate(account.getId());
        UUID accountId = account.getId();
        User admin = userRepository.findByUsername("admin").orElseThrow();
        long apiClientsBefore = apiClientRepository.count();
        loginAsWithCsrf(createUser(Role.ACCOUNTANT));

        try {
            navigateTo("/settings");
            waitForPageLoad();
            assertThat(page.locator("#companyName")).hasValue(originalConfig.companyName());
            assertThat(page.locator("#btn-save-company")).hasCount(0);
            assertThat(page.locator("#btn-upload-logo")).hasCount(0);
            assertThat(page.locator("a[href='/settings/bank-accounts/new']")).hasCount(0);
            assertThat(page.locator("form[action^='/settings/bank-accounts/']")).hasCount(0);

            FormData companyForm = FormData.create()
                    .set("id", originalConfig.id().toString())
                    .set("companyName", "PT Tanpa Izin")
                    .set("fiscalYearStartMonth", "1")
                    .set("currencyCode", "IDR");
            FormData bankForm = FormData.create()
                    .set("bankName", "Bank Tanpa Izin")
                    .set("accountNumber", number + "X")
                    .set("accountName", "PT Tanpa Izin");
            List<String> refused = List.of(
                    "/settings/company", "/settings/company/logo/delete", "/settings/bank-accounts/new",
                    "/settings/bank-accounts/" + accountId, "/settings/bank-accounts/" + accountId + "/set-default",
                    "/settings/bank-accounts/" + accountId + "/activate", "/settings/bank-accounts/" + accountId + "/delete",
                    "/settings/api-clients");
            for (String path : refused) {
                FormData form = path.equals("/settings/company") ? companyForm
                        : path.equals("/settings/api-clients") ? FormData.create().set("name", "x")
                                .set("scopes", "transactions:post").set("userId", admin.getId().toString())
                        : bankForm;
                assertEquals(403, postNoRedirect(path, form).status(), path);
            }
            APIResponse upload = page.request().post(baseUrl() + "/settings/company/logo", RequestOptions.create()
                    .setHeader(csrfHeader, csrfToken)
                    .setMaxRedirects(0)
                    .setMultipart(FormData.create().set("logoFile", new FilePayload("logo.png", "image/png", PNG))));
            assertEquals(403, upload.status());
            assertEquals(403, page.request().get(baseUrl() + "/settings/bank-accounts/new",
                    RequestOptions.create().setMaxRedirects(0)).status());
            assertEquals(403, page.request().get(baseUrl() + "/settings/api-clients",
                    RequestOptions.create().setMaxRedirects(0)).status());

            CompanyConfig config = currentConfig();
            assertEquals(originalConfig.companyName(), config.getCompanyName());
            assertEquals(originalConfig.companyLogoPath(), config.getCompanyLogoPath());
            CompanyBankAccount stored = bankAccountRepository.findById(accountId).orElseThrow();
            assertEquals("Bank Otorisasi " + number, stored.getBankName());
            assertFalse(stored.isActive());
            assertFalse(stored.isDefaultAccount());
            assertFalse(bankAccountRepository.existsByAccountNumber(number + "X"));
            assertEquals(apiClientsBefore, apiClientRepository.count());
        } finally {
            bankAccountService.delete(accountId);
        }
    }

    @Test
    @DisplayName("Should deny company settings to an auditor while allowing the audit log")
    void shouldDenyCompanySettingsToAuditor() {
        loginAsWithCsrf(createUser(Role.AUDITOR));

        assertEquals(403, page.request().get(baseUrl() + "/settings",
                RequestOptions.create().setMaxRedirects(0)).status());
        assertEquals(200, page.request().get(baseUrl() + "/settings/audit-logs",
                RequestOptions.create().setMaxRedirects(0)).status());
    }

    // ==================== helpers ====================

    private CompanyConfig currentConfig() {
        return companyConfigRepository.findById(originalConfig.id())
                .orElseThrow(() -> new IllegalStateException("Company config removed"));
    }

    private void clearLogo() {
        CompanyConfig config = currentConfig();
        config.setCompanyLogoPath(null);
        companyConfigRepository.save(config);
    }

    private String label(UUID accountId) {
        ChartOfAccount account = accountRepository.findById(Objects.requireNonNull(accountId, "posting account not set"))
                .orElseThrow();
        return account.getAccountCode() + " - " + account.getAccountName();
    }

    private void loginAsAdminWithCsrf() {
        loginAsAdmin();
        readCsrf();
    }

    private void loginAsWithCsrf(User user) {
        login(user.getUsername(), "password");
        readCsrf();
    }

    private void readCsrf() {
        csrfHeader = page.locator("meta[name='_csrf_header']").getAttribute("content");
        csrfToken = page.locator("meta[name='_csrf']").getAttribute("content");
    }

    private APIResponse post(String path, FormData form) {
        RequestOptions options = RequestOptions.create().setHeader(csrfHeader, csrfToken);
        if (form != null) {
            options.setForm(form);
        }
        return page.request().post(baseUrl() + path, options);
    }

    private APIResponse postNoRedirect(String path, FormData form) {
        return page.request().post(baseUrl() + path, RequestOptions.create()
                .setHeader(csrfHeader, csrfToken)
                .setMaxRedirects(0)
                .setForm(form));
    }

    private int apiStatus(DeviceToken token) {
        APIRequestContext api = playwright.request().newContext(new APIRequest.NewContextOptions().setBaseURL(baseUrl()));
        try {
            return api.get("/api/drafts/templates", RequestOptions.create()
                    .setHeader("Authorization", "Bearer " + token.getTokenHash())).status();
        } finally {
            api.dispose();
        }
    }

    private User createUser(Role role) {
        String id = shortId();
        User user = new User();
        user.setUsername("stl-" + role.name().toLowerCase() + "-" + id);
        user.setPassword(passwordEncoder.encode("password"));
        user.setFullName("Pengguna Pengaturan " + id);
        user.setEmail("stl-" + id + "@example.com");
        user.setActive(true);
        user.addRole(role, "system");
        return userRepository.save(user);
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
