package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.CompanyConfig;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.CompanyConfigRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tax profile page (/tax-profile): established date and PKP status stored on the singleton
 * company_config row, and the tax classification derived from them. The row is shared with every
 * other test class, so each test restores it afterwards.
 */
@DisplayName("Tax Profile")
@Import(ServiceTestDataInitializer.class)
class TaxProfileTest extends PlaywrightTestBase {

    private static final String SAVED = "Profil pajak berhasil disimpan";
    private static final String PPH_FINAL = "Eligible untuk PPh Final UMKM 0,5% (berdasarkan usia perusahaan)";
    private static final String PPH_BADAN = "PPh Badan dengan fasilitas Pasal 31E (tarif efektif 11% untuk omzet s.d. 4,8M)";

    @Autowired private CompanyConfigRepository companyConfigRepository;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private CompanyConfigSnapshot originalConfig;

    @BeforeEach
    void setUp() {
        originalConfig = CompanyConfigSnapshot.capture(companyConfigRepository);
    }

    @AfterEach
    void restoreConfig() {
        originalConfig.restore(companyConfigRepository, accountRepository);
    }

    @Test
    @DisplayName("Form is prefilled from the stored profile and shows the derived classification")
    void shouldPrefillStoredProfile() {
        LocalDate established = LocalDate.now().minusYears(6).minusMonths(3);
        LocalDate pkpSince = LocalDate.now().minusYears(2);
        storeProfile(established, true, pkpSince);
        loginAsAdmin();

        navigateTo("/tax-profile");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Profil Pajak");
        assertThat(page.getByTestId("established-date")).hasValue(established.toString());
        assertThat(page.getByTestId("pkp-yes")).isChecked();
        assertThat(page.getByTestId("pkp-no")).not().isChecked();
        assertThat(page.getByTestId("pkp-since")).isVisible();
        assertThat(page.getByTestId("pkp-since")).hasValue(pkpSince.toString());
        assertThat(page.getByTestId("company-age")).hasText("6 tahun 3 bulan");
        assertThat(page.getByTestId("pph-regime")).hasText(PPH_BADAN);
        assertThat(page.getByTestId("ppn-status")).hasText("Wajib pungut PPN 11%");
        assertThat(page.locator("main")).containsText("PKP selama 2 tahun");
    }

    @Test
    @DisplayName("Saving a company younger than four years that is not PKP persists it and shows PPh Final eligibility")
    void shouldSaveYoungNonPkpCompany() {
        storeProfile(null, true, LocalDate.now().minusYears(1));
        loginAsAdmin();
        navigateTo("/tax-profile");
        waitForPageLoad();
        assertThat(page.locator("main")).containsText("Belum ada data profil pajak");

        LocalDate established = LocalDate.now().minusYears(2);
        page.getByTestId("established-date").fill(established.toString());
        page.getByTestId("pkp-no").check();
        assertThat(page.getByTestId("pkp-since")).isHidden();
        save();

        assertThat(page.locator("main")).containsText(SAVED);
        assertThat(page.getByTestId("company-age")).hasText("2 tahun");
        assertThat(page.getByTestId("pph-regime")).hasText(PPH_FINAL);
        assertThat(page.getByTestId("ppn-status")).hasText("Belum PKP - tidak wajib pungut PPN");
        assertThat(page.locator("main")).not().containsText("PKP selama");
        assertThat(page.getByTestId("pkp-no")).isChecked();

        CompanyConfig stored = config();
        assertThat(stored.getEstablishedDate()).isEqualTo(established);
        assertThat(stored.getIsPkp()).isFalse();
        assertThat(stored.getPkpSince()).isNull();
    }

    @Test
    @DisplayName("Choosing PKP reveals the PKP-since field; saving persists both and shows the PPN obligation")
    void shouldSavePkpCompany() {
        storeProfile(LocalDate.now().minusYears(5), false, null);
        loginAsAdmin();
        navigateTo("/tax-profile");
        waitForPageLoad();
        assertThat(page.getByTestId("pkp-no")).isChecked();
        assertThat(page.getByTestId("pkp-since")).isHidden();
        assertThat(page.getByTestId("pph-regime")).hasText(PPH_BADAN);

        page.getByTestId("pkp-yes").check();
        assertThat(page.getByTestId("pkp-since")).isVisible();
        LocalDate pkpSince = LocalDate.now().minusYears(1).minusMonths(4);
        page.getByTestId("pkp-since").fill(pkpSince.toString());
        save();

        assertThat(page.locator("main")).containsText(SAVED);
        assertThat(page.getByTestId("ppn-status")).hasText("Wajib pungut PPN 11%");
        assertThat(page.locator("main")).containsText("PKP selama 1 tahun 4 bulan");
        CompanyConfig stored = config();
        assertThat(stored.getIsPkp()).isTrue();
        assertThat(stored.getPkpSince()).isEqualTo(pkpSince);
        assertThat(stored.getEstablishedDate()).isEqualTo(LocalDate.now().minusYears(5));
    }

    @Test
    @DisplayName("Switching back to non-PKP and clearing the established date clears PKP-since and shows the empty state")
    void shouldClearProfile() {
        storeProfile(LocalDate.now().minusYears(3), true, LocalDate.now().minusYears(1));
        loginAsAdmin();
        navigateTo("/tax-profile");
        waitForPageLoad();

        page.getByTestId("established-date").fill("");
        page.getByTestId("pkp-no").check();
        save();

        assertThat(page.locator("main")).containsText(SAVED);
        assertThat(page.locator("main")).containsText("Belum ada data profil pajak");
        assertThat(page.getByTestId("company-age")).hasCount(0);
        CompanyConfig stored = config();
        assertThat(stored.getEstablishedDate()).isNull();
        assertThat(stored.getIsPkp()).isFalse();
        assertThat(stored.getPkpSince()).isNull();
    }

    @Test
    @DisplayName("Sidebar entry under Pengaturan opens the page; related-page links resolve")
    void shouldNavigateFromSidebarAndLinkRelatedPages() {
        loginAsAdmin();
        navigateTo("/dashboard");
        waitForPageLoad();
        page.locator("#nav-group-pengaturan").click();
        page.locator("#nav-tax-profile").click();
        page.waitForURL(url -> url.endsWith("/tax-profile"));
        assertThat(page.locator("#page-title")).hasText("Profil Pajak");

        for (String href : new String[]{"/settings", "/fiscal-periods", "/tax-calendar"}) {
            assertThat(page.locator("main a[href='" + href + "']")).hasCount(1);
            assertThat(page.request().get(baseUrl() + href).status()).as(href).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("A role with view-only settings access sees the profile without a save button and gets 403 on save")
    void shouldRefuseSaveWithoutEditPermission() {
        LocalDate established = LocalDate.now().minusYears(7);
        storeProfile(established, false, null);
        login(createUser(Role.ACCOUNTANT).getUsername(), "password");

        navigateTo("/tax-profile");
        waitForPageLoad();
        assertThat(page.getByTestId("established-date")).hasValue(established.toString());
        assertThat(page.getByTestId("btn-save")).hasCount(0);

        String csrfHeader = page.locator("meta[name='_csrf_header']").getAttribute("content");
        String csrfToken = page.locator("meta[name='_csrf']").getAttribute("content");
        APIResponse response = page.request().post(baseUrl() + "/tax-profile", RequestOptions.create()
                .setHeader(csrfHeader, csrfToken)
                .setMaxRedirects(0)
                .setForm(FormData.create().set("establishedDate", "2020-01-01").set("isPkp", "true")
                        .set("pkpSince", "2021-01-01")));
        assertThat(response.status()).isEqualTo(403);

        CompanyConfig stored = config();
        assertThat(stored.getEstablishedDate()).isEqualTo(established);
        assertThat(stored.getIsPkp()).isFalse();
        assertThat(stored.getPkpSince()).isNull();
    }

    private void save() {
        page.getByTestId("btn-save").click();
        page.waitForURL(url -> url.endsWith("/tax-profile"));
        waitForPageLoad();
    }

    private void storeProfile(LocalDate established, boolean pkp, LocalDate pkpSince) {
        CompanyConfig c = config();
        c.setEstablishedDate(established);
        c.setIsPkp(pkp);
        c.setPkpSince(pkpSince);
        companyConfigRepository.save(c);
    }

    private CompanyConfig config() {
        return companyConfigRepository.findById(originalConfig.id())
                .orElseThrow(() -> new IllegalStateException("Company config " + originalConfig.id() + " missing"));
    }

    private User createUser(Role role) {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        User user = new User();
        user.setUsername("txp-" + role.name().toLowerCase() + "-" + id);
        user.setPassword(passwordEncoder.encode("password"));
        user.setFullName("Pengguna Profil Pajak " + id);
        user.setEmail("txp-" + id + "@example.com");
        user.setActive(true);
        user.addRole(role, "system");
        return userRepository.save(user);
    }
}
