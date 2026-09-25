package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.AccountType;
import com.artivisi.accountingfinance.enums.NormalBalance;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import com.microsoft.playwright.options.SelectOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chart of accounts CRUD through /accounts. The chart is shared by every test class, so these
 * tests read seeded accounts but only create, edit, deactivate or delete accounts they create
 * themselves (codes prefixed "T"). Journal lines used for the in-use check are dated 2061.
 */
@DisplayName("Chart of Accounts")
@Import(ServiceTestDataInitializer.class)
class ChartOfAccountsTest extends PlaywrightTestBase {

    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String csrfHeader;
    private String csrfToken;

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
        readCsrf();
    }

    @Test
    @DisplayName("List shows the seeded hierarchy with type and normal balance; expanding a header reveals its children")
    void shouldShowSeededHierarchy() {
        navigateTo("/accounts");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Bagan Akun");
        assertThat(page.locator("#account-name-1")).hasText("ASET");
        assertThat(page.locator("#account-type-1")).hasText("Aset");
        assertThat(page.locator("#account-row-1")).containsText("Debit");
        assertThat(page.locator("#account-name-2")).hasText("LIABILITAS");
        assertThat(page.locator("#account-row-2")).containsText("Credit");
        assertThat(page.locator("#account-row-5-9")).isHidden();

        page.locator("#btn-expand-5").click();
        assertThat(page.locator("#account-name-5-9")).hasText("Beban Pajak");
        assertThat(page.locator("#account-row-5-9-01")).isHidden();
        page.locator("#btn-expand-5-9").click();
        assertThat(page.locator("#account-name-5-9-01")).hasText("Beban PPh 21 Ditanggung Perusahaan");
        assertThat(page.locator("#account-type-5-9-01")).hasText("Beban");
        assertThat(page.locator("#btn-delete-5")).hasCount(0);
    }

    @Test
    @DisplayName("New account form lists account types and active parents; the type suggests the permanent flag")
    void shouldOfferTypesParentsAndPermanentSuggestion() {
        navigateTo("/accounts/new");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Tambah Akun");
        assertThat(page.locator("#accountType option")).hasText(new String[]{
                "Pilih tipe akun", "Aset", "Liabilitas", "Ekuitas", "Pendapatan", "Beban"});
        assertThat(page.locator("#parentId option").first()).hasText("Tanpa induk (akun utama)");
        assertThat(page.locator("#parentId option", new com.microsoft.playwright.Page.LocatorOptions()
                .setHasText("2.1 - Liabilitas Jangka Pendek"))).hasCount(1);
        assertThat(page.locator("input[name='active']")).hasCount(0);

        page.getByTestId("account-type").selectOption("EXPENSE");
        assertThat(page.locator("#permanent")).not().isChecked();
        page.getByTestId("account-type").selectOption("ASSET");
        assertThat(page.locator("#permanent")).isChecked();
        page.getByTestId("account-type").selectOption("REVENUE");
        assertThat(page.locator("#permanent")).not().isChecked();
        page.getByTestId("account-type").selectOption("LIABILITY");
        assertThat(page.locator("#permanent")).isChecked();
    }

    @Test
    @DisplayName("Root account takes its normal balance from its type; a child inherits type and normal balance from its parent")
    void shouldCreateRootAndChildAccounts() {
        String rootCode = code();
        String childCode = rootCode + ".01";

        navigateTo("/accounts/new");
        waitForPageLoad();
        page.locator("#accountCode").fill(rootCode);
        page.locator("#accountName").fill("Pendapatan Uji " + rootCode);
        page.getByTestId("account-type").selectOption("REVENUE");
        page.locator("#isHeader").check();
        page.locator("#btn-simpan").click();
        page.waitForURL(baseUrl() + "/accounts");
        assertThat(page.locator("#success-message")).hasText("Akun berhasil ditambahkan");

        ChartOfAccount root = account(rootCode);
        assertThat(root.getAccountType()).isEqualTo(AccountType.REVENUE);
        assertThat(root.getNormalBalance()).isEqualTo(NormalBalance.CREDIT);
        assertThat(root.getLevel()).isEqualTo(1);
        assertThat(root.getIsHeader()).isTrue();
        assertThat(root.getPermanent()).isFalse();
        assertThat(root.getActive()).isTrue();

        navigateTo("/accounts/new");
        waitForPageLoad();
        page.locator("#accountCode").fill(childCode);
        page.locator("#accountName").fill("Pendapatan Anak " + childCode);
        page.getByTestId("account-type").selectOption("ASSET");
        page.locator("#parentId").selectOption(new SelectOption().setLabel(rootCode + " - Pendapatan Uji " + rootCode));
        page.locator("#btn-simpan").click();
        page.waitForURL(baseUrl() + "/accounts");

        ChartOfAccount child = account(childCode);
        assertThat(child.getAccountType()).isEqualTo(AccountType.REVENUE);
        assertThat(child.getNormalBalance()).isEqualTo(NormalBalance.CREDIT);
        assertThat(child.getLevel()).isEqualTo(2);
        assertThat(child.getIsHeader()).isFalse();
        assertThat(accountRepository.findByParentIdOrderByAccountCodeAsc(root.getId()))
                .extracting(ChartOfAccount::getAccountCode).containsExactly(childCode);

        page.locator("#btn-expand-" + rowId(rootCode)).click();
        assertThat(page.locator("#account-name-" + rowId(childCode))).hasText("Pendapatan Anak " + childCode);
        assertThat(page.locator("#account-type-" + rowId(childCode))).hasText("Pendapatan");
    }

    @Test
    @DisplayName("Create rejects a duplicate code and a root account without type, keeps the input and saves nothing")
    void shouldRejectInvalidNewAccounts() {
        String duplicateName = "Duplikat " + code();
        navigateTo("/accounts/new");
        waitForPageLoad();
        page.locator("#accountCode").fill("1.1.02");
        page.locator("#accountName").fill(duplicateName);
        page.getByTestId("account-type").selectOption("ASSET");
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/accounts/new");
        assertThat(page.getByTestId("validation-errors")).containsText("Account code already exists: 1.1.02");
        assertThat(page.locator("#accountName")).hasValue(duplicateName);
        assertThat(account("1.1.02").getAccountName()).isEqualTo("Bank BCA");

        String untyped = code();
        page.locator("#accountCode").fill(untyped);
        page.locator("#accountName").fill("Tanpa Tipe " + untyped);
        page.getByTestId("account-type").selectOption("");
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/accounts/new");
        assertThat(page.getByTestId("validation-errors")).containsText("Tipe akun harus diisi untuk akun tanpa induk");
        assertThat(page.locator("#accountCode")).hasValue(untyped);
        assertThat(accountRepository.findByAccountCode(untyped)).isEmpty();
    }

    @Test
    @DisplayName("Editing a child keeps the inherited type and saves description and permanent flag")
    void shouldEditChildAccount() {
        String rootCode = code();
        ChartOfAccount root = createAccount(rootCode, "Beban Uji", "EXPENSE", null, true);
        ChartOfAccount child = createAccount(rootCode + ".01", "Beban Anak", null, root.getId(), false);

        navigateTo("/accounts/" + child.getId() + "/edit");
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Edit Akun");
        assertThat(page.getByTestId("account-type")).isDisabled();
        assertThat(page.getByTestId("account-type")).hasValue("EXPENSE");
        assertThat(page.locator("#account-form-content")).containsText("Tipe akun mengikuti akun induk");
        assertThat(page.locator("#permanent")).not().isChecked();

        page.getByTestId("description").fill("Beban uji operasional");
        page.locator("#permanent").check();
        page.getByTestId("btn-submit").click();
        page.waitForURL(baseUrl() + "/accounts");
        assertThat(page.locator("#success-message")).hasText("Akun berhasil diperbarui");

        ChartOfAccount saved = account(child.getAccountCode());
        assertThat(saved.getDescription()).isEqualTo("Beban uji operasional");
        assertThat(saved.getPermanent()).isTrue();
        assertThat(saved.getAccountType()).isEqualTo(AccountType.EXPENSE);
        assertThat(saved.getNormalBalance()).isEqualTo(NormalBalance.DEBIT);
        assertThat(accountRepository.findByParentIdOrderByAccountCodeAsc(root.getId()))
                .extracting(ChartOfAccount::getId).containsExactly(child.getId());
    }

    @Test
    @DisplayName("Editing a root account that has children saves the change and keeps its type")
    void shouldEditRootAccountWithChildren() {
        String rootCode = code();
        ChartOfAccount root = createAccount(rootCode, "Aset Uji", "ASSET", null, true);
        createAccount(rootCode + ".01", "Aset Anak", null, root.getId(), false);

        navigateTo("/accounts/" + root.getId() + "/edit");
        waitForPageLoad();
        assertThat(page.getByTestId("account-type")).isDisabled();
        assertThat(page.locator("#account-form-content")).containsText("Tipe akun tidak dapat diubah karena memiliki akun anak");
        page.locator("#accountName").fill("Aset Uji Revisi");
        page.getByTestId("btn-submit").click();
        page.waitForURL(baseUrl() + "/accounts");
        assertThat(page.locator("#success-message")).hasText("Akun berhasil diperbarui");

        ChartOfAccount saved = account(rootCode);
        assertThat(saved.getAccountName()).isEqualTo("Aset Uji Revisi");
        assertThat(saved.getAccountType()).isEqualTo(AccountType.ASSET);
        assertThat(saved.getNormalBalance()).isEqualTo(NormalBalance.DEBIT);
    }

    @Test
    @DisplayName("Edit rejects another account's code; the form keeps targeting the edited account")
    void shouldKeepEditFormBoundAfterDuplicateCode() {
        ChartOfAccount first = createAccount(code(), "Kas Uji Satu", "ASSET", null, false);
        ChartOfAccount second = createAccount(code(), "Kas Uji Dua", "ASSET", null, false);

        navigateTo("/accounts/" + first.getId() + "/edit");
        waitForPageLoad();
        page.locator("#accountCode").fill(second.getAccountCode());
        page.getByTestId("btn-submit").click();
        waitForPageLoad();

        assertThat(page.getByTestId("validation-errors")).containsText("Account code already exists: " + second.getAccountCode());
        assertThat(page.locator("form[action='/accounts/" + first.getId() + "/edit']")).hasCount(1);

        String newCode = code();
        page.locator("#accountCode").fill(newCode);
        page.getByTestId("btn-submit").click();
        page.waitForURL(baseUrl() + "/accounts");

        assertThat(accountRepository.findById(first.getId()).orElseThrow().getAccountCode()).isEqualTo(newCode);
        ChartOfAccount untouched = accountRepository.findById(second.getId()).orElseThrow();
        assertThat(untouched.getAccountCode()).isEqualTo(second.getAccountCode());
        assertThat(untouched.getAccountName()).isEqualTo("Kas Uji Dua");
    }

    @Test
    @DisplayName("Deactivated accounts show as inactive and leave the account picker; activating restores them")
    void shouldDeactivateAndActivateAccount() {
        String rootCode = code();
        ChartOfAccount root = createAccount(rootCode, "Beban Aktif", "EXPENSE", null, true);
        ChartOfAccount leaf = createAccount(rootCode + ".01", "Beban Aktif Anak", null, root.getId(), false);
        assertThat(pickerCodes(leaf.getAccountCode())).containsExactly(leaf.getAccountCode());

        navigateTo("/accounts");
        waitForPageLoad();
        page.locator("#btn-expand-" + rowId(rootCode)).click();
        page.onceDialog(dialog -> dialog.accept());
        page.locator("#btn-deactivate-" + rowId(leaf.getAccountCode())).click();
        page.waitForURL(baseUrl() + "/accounts");
        assertThat(page.locator("#success-message")).hasText("Akun berhasil dinonaktifkan");
        assertThat(accountRepository.findById(leaf.getId()).orElseThrow().getActive()).isFalse();
        assertThat(pickerCodes(leaf.getAccountCode())).isEmpty();

        page.locator("#btn-expand-" + rowId(rootCode)).click();
        assertThat(page.locator("#account-row-" + rowId(leaf.getAccountCode()))).containsText("Non-aktif");
        page.locator("#btn-activate-" + rowId(leaf.getAccountCode())).click();
        page.waitForURL(baseUrl() + "/accounts");
        assertThat(page.locator("#success-message")).hasText("Akun berhasil diaktifkan");
        assertThat(accountRepository.findById(leaf.getId()).orElseThrow().getActive()).isTrue();
        assertThat(pickerCodes(leaf.getAccountCode())).containsExactly(leaf.getAccountCode());
    }

    @Test
    @DisplayName("Delete removes an unused leaf; accounts with children or journal lines are kept with an error")
    void shouldDeleteOnlyUnusedLeafAccounts() {
        String rootCode = code();
        ChartOfAccount root = createAccount(rootCode, "Beban Hapus", "EXPENSE", null, true);
        ChartOfAccount unused = createAccount(rootCode + ".01", "Beban Tak Dipakai", null, root.getId(), false);
        ChartOfAccount used = createAccount(rootCode + ".02", "Beban Dipakai", null, root.getId(), false);
        postJournal("2061-01-15", "Pemakaian akun " + used.getAccountCode(), used.getId(), account("1.1.02").getId(), 125000);

        navigateTo("/accounts");
        waitForPageLoad();
        assertThat(page.locator("#btn-delete-" + rowId(rootCode))).hasCount(0);
        page.locator("#btn-expand-" + rowId(rootCode)).click();
        page.onceDialog(dialog -> dialog.accept());
        page.locator("#btn-delete-" + rowId(unused.getAccountCode())).click();
        page.waitForURL(baseUrl() + "/accounts");
        assertThat(page.locator("#success-message")).hasText("Akun berhasil dihapus");
        assertThat(accountRepository.findByAccountCode(unused.getAccountCode())).isEmpty();

        page.locator("#btn-expand-" + rowId(rootCode)).click();
        page.onceDialog(dialog -> dialog.accept());
        page.locator("#btn-delete-" + rowId(used.getAccountCode())).click();
        page.waitForURL(baseUrl() + "/accounts");
        assertThat(page.getByTestId("error-message")).hasText("Cannot delete account with journal entries");
        assertThat(accountRepository.findByAccountCode(used.getAccountCode())).isPresent();

        assertThat(postForm("/accounts/" + root.getId() + "/delete", FormData.create()).status()).isEqualTo(302);
        navigateTo("/accounts");
        waitForPageLoad();
        assertThat(page.getByTestId("error-message")).hasText("Cannot delete account with child accounts");
        assertThat(accountRepository.findByAccountCode(rootCode)).isPresent();
    }

    @Test
    @DisplayName("AUDITOR cannot create, edit, activate, deactivate or delete accounts and sees no write controls")
    void shouldRefuseAccountWritesForAuditor() {
        String rootCode = code();
        ChartOfAccount root = createAccount(rootCode, "Beban Auditor", "EXPENSE", null, true);
        ChartOfAccount leaf = createAccount(rootCode + ".01", "Beban Auditor Anak", null, root.getId(), false);
        String newCode = code();
        Supplier<String> state = () -> accountRepository.findById(leaf.getId())
                .map(a -> a.getAccountCode() + "|" + a.getAccountName() + "|" + a.getActive()).orElse("deleted")
                + "|" + accountRepository.findByAccountCode(newCode).isPresent();
        String before = state.get();

        loginAsNewUser(Role.AUDITOR);

        assertThat(page.request().get(baseUrl() + "/accounts/new").status()).isEqualTo(403);
        assertThat(page.request().get(baseUrl() + "/accounts/" + leaf.getId() + "/edit").status()).isEqualTo(403);
        assertThat(postForm("/accounts/new", accountForm(newCode, "Ilegal", "EXPENSE", null, false)).status()).isEqualTo(403);
        assertThat(postForm("/accounts/" + leaf.getId() + "/edit",
                accountForm(leaf.getAccountCode(), "Ilegal", null, null, false)).status()).isEqualTo(403);
        assertThat(postForm("/accounts/" + leaf.getId() + "/deactivate", FormData.create()).status()).isEqualTo(403);
        assertThat(postForm("/accounts/" + leaf.getId() + "/activate", FormData.create()).status()).isEqualTo(403);
        assertThat(postForm("/accounts/" + leaf.getId() + "/delete", FormData.create()).status()).isEqualTo(403);
        assertThat(state.get()).isEqualTo(before);

        navigateTo("/accounts");
        waitForPageLoad();
        assertThat(page.locator("#account-row-" + rowId(rootCode))).isVisible();
        assertThat(page.locator("#btn-tambah-akun")).hasCount(0);
        assertThat(page.locator("[id^='btn-edit-'], [id^='btn-delete-'], [id^='btn-deactivate-'], [id^='btn-activate-']"))
                .hasCount(0);
    }

    // ==================== helpers ====================

    private ChartOfAccount createAccount(String code, String name, String type, UUID parentId, boolean header) {
        APIResponse response = page.request().post(baseUrl() + "/accounts/new",
                RequestOptions.create().setForm(accountForm(code, name, type, parentId, header).set("_csrf", csrfToken)));
        assertThat(response.status()).as("create account " + code).isEqualTo(200);
        assertThat(response.url()).isEqualTo(baseUrl() + "/accounts");
        assertThat(response.text()).contains("Akun berhasil ditambahkan");
        return account(code);
    }

    private FormData accountForm(String code, String name, String type, UUID parentId, boolean header) {
        FormData form = FormData.create().set("accountCode", code).set("accountName", name);
        if (type != null) {
            form.set("accountType", type);
        }
        if (parentId != null) {
            form.set("parentId", parentId.toString());
        }
        if (header) {
            form.set("isHeader", "true");
        }
        return form;
    }

    private void postJournal(String date, String description, UUID debitAccount, UUID creditAccount, long amount) {
        APIResponse created = page.request().post(baseUrl() + "/transactions/journal-entry", withCsrf().setData(Map.of(
                "transactionDate", date, "description", description,
                "lines", List.of(
                        Map.of("accountId", debitAccount.toString(), "debit", amount, "credit", 0),
                        Map.of("accountId", creditAccount.toString(), "debit", 0, "credit", amount)))));
        assertThat(created.status()).as(created.text()).isEqualTo(200);
        String transactionId;
        try {
            transactionId = new ObjectMapper().readTree(created.text()).get("transactionId").asText();
        } catch (Exception e) {
            throw new IllegalStateException("Unparseable journal entry response: " + created.text(), e);
        }
        APIResponse posted = page.request().post(baseUrl() + "/transactions/api/" + transactionId + "/post", withCsrf());
        assertThat(posted.status()).as(posted.text()).isEqualTo(200);
    }

    private List<String> pickerCodes(String query) {
        APIResponse response = page.request().get(baseUrl() + "/accounts/search?q=" + query);
        assertThat(response.status()).isEqualTo(200);
        return List.of(response.text().split("\"code\":\"")).stream().skip(1)
                .map(s -> s.substring(0, s.indexOf('"'))).toList();
    }

    private APIResponse postForm(String path, FormData form) {
        return page.request().post(baseUrl() + path,
                RequestOptions.create().setMaxRedirects(0).setForm(form.set("_csrf", csrfToken)));
    }

    private RequestOptions withCsrf() {
        return RequestOptions.create().setHeader(csrfHeader, csrfToken);
    }

    private void readCsrf() {
        csrfHeader = page.locator("meta[name='_csrf_header']").getAttribute("content");
        csrfToken = page.locator("meta[name='_csrf']").getAttribute("content");
    }

    private void loginAsNewUser(Role role) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setUsername("coa-" + role.name().toLowerCase() + "-" + id);
        user.setPassword(passwordEncoder.encode("password"));
        user.setFullName("Pengguna " + id);
        user.setEmail("coa-" + id + "@example.com");
        user.setActive(true);
        user.addRole(role, "system");
        userRepository.save(user);
        context.clearCookies();
        login(user.getUsername(), "password");
        readCsrf();
    }

    private ChartOfAccount account(String code) {
        return accountRepository.findByAccountCode(code)
                .orElseThrow(() -> new IllegalStateException("Account missing: " + code));
    }

    private static String rowId(String code) {
        return code.replace('.', '-');
    }

    private static String code() {
        return "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 7).toUpperCase();
    }
}
