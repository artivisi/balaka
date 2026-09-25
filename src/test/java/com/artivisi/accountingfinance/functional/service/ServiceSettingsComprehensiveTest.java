package com.artivisi.accountingfinance.functional.service;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.CompanyBankAccount;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.CompanyBankAccountRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.service.CompanyBankAccountService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Company bank accounts (create, edit, validation, default, activate/deactivate, delete, list)
 * and the security audit log filters. Every test works on bank accounts it creates; the
 * default-account flag is shared state, so the original default is restored after each test.
 * Audit log tests filter on a user created per test, so rows from other classes never match.
 */
@DisplayName("Service Industry - Settings Comprehensive")
@Import(ServiceTestDataInitializer.class)
class ServiceSettingsComprehensiveTest extends PlaywrightTestBase {

    @Autowired private CompanyBankAccountRepository bankAccountRepository;
    @Autowired private CompanyBankAccountService bankAccountService;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final List<UUID> createdAccounts = new ArrayList<>();
    private Optional<UUID> originalDefault;
    private String csrfHeader;
    private String csrfToken;

    @BeforeEach
    void captureDefaultAccount() {
        originalDefault = bankAccountRepository.findByIsDefaultTrueAndActiveTrue().map(CompanyBankAccount::getId);
    }

    @AfterEach
    void cleanUpBankAccounts() {
        createdAccounts.stream().filter(bankAccountRepository::existsById).forEach(bankAccountService::delete);
        originalDefault.ifPresent(bankAccountService::setAsDefault);
    }

    // ==================== Bank accounts ====================

    @Test
    @DisplayName("Should create a bank account with branch, currency and GL account and list it on the settings page")
    void shouldCreateBankAccount() {
        loginAsAdminWithCsrf();
        String number = accountNumber();
        ChartOfAccount bca = account("1.1.02");

        navigateTo("/settings/bank-accounts/new");
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Rekening Bank Baru");
        page.locator("#bankName").fill("Bank Uji " + number);
        page.locator("#bankBranch").fill("KCP Uji");
        page.locator("#accountNumber").fill(number);
        page.locator("#accountName").fill("PT Uji Rekening");
        page.locator("#currencyCode").selectOption("USD");
        page.locator("#glAccount").selectOption(bca.getId().toString());
        page.locator("#btn-save-bank").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/settings", page.url());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Rekening bank berhasil ditambahkan");
        Locator row = settingsRow(number);
        assertThat(row.locator("td").nth(0)).containsText("Bank Uji " + number);
        assertThat(row.locator("td").nth(0)).containsText("KCP Uji");
        assertThat(row.locator("td").nth(2)).hasText("PT Uji Rekening");
        assertThat(row.locator("td").nth(3)).hasText("");

        CompanyBankAccount saved = stored(number);
        createdAccounts.add(saved.getId());
        assertEquals("Bank Uji " + number, saved.getBankName());
        assertEquals("KCP Uji", saved.getBankBranch());
        assertEquals("PT Uji Rekening", saved.getAccountName());
        assertEquals("USD", saved.getCurrencyCode());
        assertEquals(bca.getId(), saved.getGlAccount().getId());
        assertTrue(saved.isActive());
        assertFalse(saved.isDefaultAccount());
    }

    @Test
    @DisplayName("Should show field errors for a blank bank name and save nothing")
    void shouldRejectBlankBankName() {
        loginAsAdminWithCsrf();
        String number = accountNumber();
        ChartOfAccount bca = account("1.1.02");

        navigateTo("/settings/bank-accounts/new");
        waitForPageLoad();
        page.locator("#bankName").evaluate("el => el.removeAttribute('required')");
        page.locator("#accountNumber").fill(number);
        page.locator("#accountName").fill("PT Tanpa Bank");
        page.locator("#glAccount").selectOption(bca.getId().toString());
        page.locator("#btn-save-bank").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/settings/bank-accounts/new", page.url());
        assertThat(page.locator("#bankName + p")).hasText("Nama bank wajib diisi");
        assertThat(page.locator("#accountNumber")).hasValue(number);
        assertThat(page.locator("#accountName")).hasValue("PT Tanpa Bank");
        assertThat(page.locator("#glAccount")).hasValue(bca.getId().toString());
        assertFalse(bankAccountRepository.existsByAccountNumber(number));
    }

    @Test
    @DisplayName("Should reject a duplicate account number on create and keep the entered values")
    void shouldRejectDuplicateAccountNumber() {
        CompanyBankAccount existing = createAccount("Bank Asal");
        loginAsAdminWithCsrf();

        navigateTo("/settings/bank-accounts/new");
        waitForPageLoad();
        page.locator("#bankName").fill("Bank Kembar");
        page.locator("#accountNumber").fill(existing.getAccountNumber());
        page.locator("#accountName").fill("PT Kembar");
        page.locator("#btn-save-bank").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/settings/bank-accounts/new", page.url());
        assertThat(page.locator("#accountNumber + p"))
                .hasText("Account number already exists: " + existing.getAccountNumber());
        assertThat(page.locator("#bankName")).hasValue("Bank Kembar");
        assertEquals("Bank Asal", stored(existing.getAccountNumber()).getBankName());
        assertEquals(1, bankAccountRepository.findAll().stream()
                .filter(a -> existing.getAccountNumber().equals(a.getAccountNumber())).count());
    }

    @Test
    @DisplayName("Should prefill the edit form and save changed fields including the GL account")
    void shouldEditBankAccount() {
        CompanyBankAccount existing = createAccount("Bank Lama");
        ChartOfAccount mandiri = account("1.1.03");
        loginAsAdminWithCsrf();

        navigateTo("/settings/bank-accounts/" + existing.getId() + "/edit");
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Edit Rekening Bank");
        assertThat(page.locator("#bankName")).hasValue("Bank Lama");
        assertThat(page.locator("#bankBranch")).hasValue("KCP Lama");
        assertThat(page.locator("#accountNumber")).hasValue(existing.getAccountNumber());
        assertThat(page.locator("#accountName")).hasValue("PT Rekening Uji");
        assertThat(page.locator("#currencyCode")).hasValue("IDR");
        assertThat(page.locator("#glAccount")).hasValue(account("1.1.02").getId().toString());
        assertThat(page.locator("#isDefault")).not().isChecked();

        page.locator("#bankName").fill("Bank Baru");
        page.locator("#bankBranch").fill("KCP Baru");
        page.locator("#glAccount").selectOption(mandiri.getId().toString());
        page.locator("#btn-save-bank").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/settings", page.url());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Rekening bank berhasil diperbarui");
        assertThat(settingsRow(existing.getAccountNumber()).locator("td").nth(0)).containsText("Bank Baru");
        CompanyBankAccount saved = stored(existing.getAccountNumber());
        assertEquals("Bank Baru", saved.getBankName());
        assertEquals("KCP Baru", saved.getBankBranch());
        assertEquals("PT Rekening Uji", saved.getAccountName());
        assertEquals(mandiri.getId(), saved.getGlAccount().getId());
    }

    @Test
    @DisplayName("Should reject changing an account number to another account's number and keep editing the original")
    void shouldRejectEditToExistingAccountNumber() {
        CompanyBankAccount target = createAccount("Bank Target");
        CompanyBankAccount other = createAccount("Bank Lain");
        loginAsAdminWithCsrf();

        navigateTo("/settings/bank-accounts/" + target.getId() + "/edit");
        waitForPageLoad();
        page.locator("#accountNumber").fill(other.getAccountNumber());
        page.locator("#btn-save-bank").click();
        waitForPageLoad();

        assertThat(page.locator("#accountNumber + p"))
                .hasText("Account number already exists: " + other.getAccountNumber());
        assertThat(page.locator("form[action='/settings/bank-accounts/" + target.getId() + "']")).hasCount(1);
        assertThat(page.locator("#page-title")).hasText("Edit Rekening Bank");

        String renumbered = accountNumber();
        page.locator("#accountNumber").fill(renumbered);
        page.locator("#btn-save-bank").click();
        waitForPageLoad();

        assertThat(page.locator("[data-testid='flash-success']")).hasText("Rekening bank berhasil diperbarui");
        assertEquals(renumbered, bankAccountRepository.findById(target.getId()).orElseThrow().getAccountNumber());
        CompanyBankAccount untouched = bankAccountRepository.findById(other.getId()).orElseThrow();
        assertEquals("Bank Lain", untouched.getBankName());
        assertEquals(other.getAccountNumber(), untouched.getAccountNumber());
    }

    @Test
    @DisplayName("Should make an account the default and clear the flag on the previous default")
    void shouldSetDefaultBankAccount() {
        CompanyBankAccount first = createAccount("Bank Utama Satu");
        CompanyBankAccount second = createAccount("Bank Utama Dua");
        loginAsAdminWithCsrf();

        navigateTo("/settings");
        waitForPageLoad();
        settingsRow(first.getAccountNumber()).locator("button:has-text('Jadikan Utama')").click();
        waitForPageLoad();

        assertThat(page.locator("[data-testid='flash-success']")).hasText("Rekening utama berhasil diubah");
        assertThat(settingsRow(first.getAccountNumber()).locator("td").nth(3)).hasText("Utama");
        assertThat(settingsRow(first.getAccountNumber()).locator("button:has-text('Jadikan Utama')")).hasCount(0);
        assertEquals(first.getId(), bankAccountRepository.findByIsDefaultTrueAndActiveTrue().orElseThrow().getId());
        originalDefault.ifPresent(id ->
                assertFalse(bankAccountRepository.findById(id).orElseThrow().isDefaultAccount()));

        settingsRow(second.getAccountNumber()).locator("button:has-text('Jadikan Utama')").click();
        waitForPageLoad();

        assertThat(settingsRow(second.getAccountNumber()).locator("td").nth(3)).hasText("Utama");
        assertThat(settingsRow(first.getAccountNumber()).locator("td").nth(3)).hasText("");
        assertFalse(bankAccountRepository.findById(first.getId()).orElseThrow().isDefaultAccount());
        assertTrue(bankAccountRepository.findById(second.getId()).orElseThrow().isDefaultAccount());
    }

    @Test
    @DisplayName("Should deactivate a default account, dropping its default flag, then reactivate it")
    void shouldDeactivateAndActivateBankAccount() {
        CompanyBankAccount account = createAccount("Bank Nonaktif");
        bankAccountService.setAsDefault(account.getId());
        loginAsAdminWithCsrf();

        navigateTo("/settings");
        waitForPageLoad();
        settingsRow(account.getAccountNumber()).locator("button:has-text('Nonaktifkan')").click();
        waitForPageLoad();

        assertThat(page.locator("[data-testid='flash-success']")).hasText("Rekening bank berhasil dinonaktifkan");
        assertThat(settingsRow(account.getAccountNumber()).locator("td").nth(3)).hasText("Nonaktif");
        CompanyBankAccount inactive = bankAccountRepository.findById(account.getId()).orElseThrow();
        assertFalse(inactive.isActive());
        assertFalse(inactive.isDefaultAccount());

        settingsRow(account.getAccountNumber()).locator("button:has-text('Aktifkan')").click();
        waitForPageLoad();

        assertThat(page.locator("[data-testid='flash-success']")).hasText("Rekening bank berhasil diaktifkan");
        assertThat(settingsRow(account.getAccountNumber()).locator("td").nth(3)).hasText("");
        assertThat(settingsRow(account.getAccountNumber()).locator("button:has-text('Nonaktifkan')")).hasCount(1);
        CompanyBankAccount active = bankAccountRepository.findById(account.getId()).orElseThrow();
        assertTrue(active.isActive());
        assertFalse(active.isDefaultAccount());
    }

    @Test
    @DisplayName("Should delete a bank account")
    void shouldDeleteBankAccount() {
        CompanyBankAccount account = createAccount("Bank Hapus");
        loginAsAdminWithCsrf();

        APIResponse response = page.request().post(baseUrl() + "/settings/bank-accounts/" + account.getId() + "/delete",
                RequestOptions.create().setHeader(csrfHeader, csrfToken));

        assertEquals(baseUrl() + "/settings", response.url());
        page.setContent(response.text());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Rekening bank berhasil dihapus");
        assertThat(page.locator("td:text-is('" + account.getAccountNumber() + "')")).hasCount(0);
        assertFalse(bankAccountRepository.existsById(account.getId()));
    }

    @Test
    @DisplayName("Should list bank accounts on the bank accounts page and as an HTMX table fragment")
    void shouldListBankAccounts() {
        CompanyBankAccount account = createAccount("Bank Daftar");
        loginAsAdminWithCsrf();

        navigateTo("/settings/bank-accounts");
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Rekening Bank");
        Locator row = page.locator("#bank-table tr:has(td:text-is('" + account.getAccountNumber() + "'))");
        assertThat(row.locator("td").nth(0)).containsText("Bank Daftar");
        assertThat(row.locator("td").nth(2)).hasText("PT Rekening Uji");
        assertThat(row.locator("a[href='/settings/bank-accounts/" + account.getId() + "/edit']")).hasText("Edit");

        APIResponse fragment = page.request().get(baseUrl() + "/settings/bank-accounts",
                RequestOptions.create().setHeader("HX-Request", "true"));
        assertEquals(200, fragment.status());
        String html = fragment.text();
        assertTrue(html.trim().startsWith("<div id=\"bank-table\""), html);
        assertTrue(html.contains(account.getAccountNumber()));
        assertFalse(html.contains("page-title"));
    }

    // ==================== Audit logs ====================

    @Test
    @DisplayName("Should filter audit logs by event type and username, masking the bank account number")
    void shouldFilterAuditLogsByEventTypeAndUsername() {
        AuditActor actor = auditActor();

        navigateTo("/settings/audit-logs");
        waitForPageLoad();
        page.locator("#event-type-filter").selectOption("SETTINGS_CHANGE");
        page.locator("#username-filter").fill(actor.username());
        page.locator("#btn-apply").click();
        waitForPageLoad();

        Locator rows = page.locator("[data-testid='audit-log-row']");
        assertThat(rows).hasCount(1);
        assertTrue(page.url().startsWith(baseUrl() + "/settings/audit-logs?eventType=SETTINGS_CHANGE&username="
                + actor.username()), page.url());
        assertThat(rows.first()).containsText("SETTINGS CHANGE");
        assertThat(rows.first()).containsText(actor.username());
        assertThat(rows.first()).containsText("Bank account created: " + actor.bankName() + " - ****"
                + actor.accountNumber().substring(actor.accountNumber().length() - 4));
        assertThat(rows.first()).not().containsText(actor.accountNumber());
        assertThat(page.locator("#event-type-filter")).hasValue("SETTINGS_CHANGE");
    }

    @Test
    @DisplayName("Should list a user's audit events newest first when filtering by username")
    void shouldFilterAuditLogsByUsername() {
        AuditActor actor = auditActor();

        navigateTo("/settings/audit-logs?username=" + actor.username());
        waitForPageLoad();

        Locator rows = page.locator("[data-testid='audit-log-row']");
        assertThat(rows).hasCount(2);
        assertThat(rows.nth(0)).containsText("SETTINGS CHANGE");
        assertThat(rows.nth(1)).containsText("LOGIN SUCCESS");
        assertThat(page.locator("#username-filter")).hasValue(actor.username());
    }

    @Test
    @DisplayName("Should include today's events in a date range covering today and none in a past range")
    void shouldFilterAuditLogsByDateRange() {
        AuditActor actor = auditActor();
        LocalDate today = LocalDate.now();
        LocalDate lastWeek = today.minusDays(7);

        navigateTo("/settings/audit-logs?username=" + actor.username() + "&startDate=" + today + "&endDate=" + today);
        waitForPageLoad();
        assertThat(page.locator("[data-testid='audit-log-row']")).hasCount(2);
        assertThat(page.locator("#start-date")).hasValue(today.toString());
        assertThat(page.locator("#end-date")).hasValue(today.toString());

        navigateTo("/settings/audit-logs?username=" + actor.username()
                + "&startDate=" + lastWeek + "&endDate=" + today.minusDays(1));
        waitForPageLoad();
        assertThat(page.locator("[data-testid='audit-log-row']")).hasCount(0);
        assertThat(page.locator("[data-testid='audit-log-empty']")).containsText("Tidak ada audit log yang ditemukan");
    }

    @Test
    @DisplayName("Should paginate filtered audit logs keeping the filter and page size")
    void shouldPaginateAuditLogs() {
        AuditActor actor = auditActor();

        navigateTo("/settings/audit-logs?username=" + actor.username() + "&size=1");
        waitForPageLoad();
        assertThat(page.locator("#total-entries")).hasText("2");
        assertThat(page.locator("#audit-log-table")).containsText("Halaman 1 dari 2");
        assertThat(page.locator("[data-testid='audit-log-row']")).hasCount(1);
        assertThat(page.locator("[data-testid='audit-log-row']")).containsText("SETTINGS CHANGE");

        page.locator("#btn-next-page").click();
        waitForPageLoad();

        assertThat(page.locator("#audit-log-table")).containsText("Halaman 2 dari 2");
        assertThat(page.locator("[data-testid='audit-log-row']")).hasCount(1);
        assertThat(page.locator("[data-testid='audit-log-row']")).containsText("LOGIN SUCCESS");
        assertThat(page.locator("#btn-prev-page")).isVisible();
    }

    @Test
    @DisplayName("Should return only the audit log table for HTMX requests")
    void shouldReturnAuditLogFragmentForHtmx() {
        AuditActor actor = auditActor();

        APIResponse fragment = page.request().get(baseUrl() + "/settings/audit-logs?username=" + actor.username(),
                RequestOptions.create().setHeader("HX-Request", "true"));

        assertEquals(200, fragment.status());
        String html = fragment.text();
        assertTrue(html.trim().startsWith("<div id=\"audit-log-table\""), html);
        assertTrue(html.contains("Bank account created: " + actor.bankName()));
        assertFalse(html.contains("page-title"));
        assertFalse(html.contains("filter-form"));
    }

    // ==================== helpers ====================

    private record AuditActor(String username, String bankName, String accountNumber) {}

    /**
     * Logs in as a fresh admin (LOGIN_SUCCESS) and creates a bank account through the UI
     * (SETTINGS_CHANGE), so the user owns exactly two audit rows.
     */
    private AuditActor auditActor() {
        User user = createUser();
        login(user.getUsername(), "password");
        readCsrf();
        String number = accountNumber();
        String bankName = "Bank Audit " + user.getUsername();

        navigateTo("/settings/bank-accounts/new");
        waitForPageLoad();
        page.locator("#bankName").fill(bankName);
        page.locator("#accountNumber").fill(number);
        page.locator("#accountName").fill("PT Audit");
        page.locator("#btn-save-bank").click();
        waitForPageLoad();
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Rekening bank berhasil ditambahkan");
        createdAccounts.add(stored(number).getId());
        return new AuditActor(user.getUsername(), bankName, number);
    }

    private CompanyBankAccount createAccount(String bankName) {
        CompanyBankAccount account = new CompanyBankAccount();
        account.setBankName(bankName);
        account.setBankBranch("KCP Lama");
        account.setAccountNumber(accountNumber());
        account.setAccountName("PT Rekening Uji");
        account.setGlAccount(account("1.1.02"));
        CompanyBankAccount saved = bankAccountService.create(account);
        createdAccounts.add(saved.getId());
        return saved;
    }

    private CompanyBankAccount stored(String accountNumber) {
        return bankAccountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AssertionError("Bank account " + accountNumber + " not persisted"));
    }

    private ChartOfAccount account(String code) {
        return accountRepository.findByAccountCode(code)
                .orElseThrow(() -> new IllegalStateException("Seeded account " + code + " missing"));
    }

    private Locator settingsRow(String accountNumber) {
        return page.locator("section tr:has(td:text-is('" + accountNumber + "'))");
    }

    private static String accountNumber() {
        return String.valueOf(System.nanoTime());
    }

    private User createUser() {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        User user = new User();
        user.setUsername("ssc-audit-" + id);
        user.setPassword(passwordEncoder.encode("password"));
        user.setFullName("Auditee " + id);
        user.setEmail("ssc-" + id + "@example.com");
        user.setActive(true);
        user.addRole(Role.ADMIN, "system");
        return userRepository.save(user);
    }

    private void loginAsAdminWithCsrf() {
        loginAsAdmin();
        readCsrf();
    }

    private void readCsrf() {
        csrfHeader = page.locator("meta[name='_csrf_header']").getAttribute("content");
        csrfToken = page.locator("meta[name='_csrf']").getAttribute("content");
    }
}
