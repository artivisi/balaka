package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.DeviceToken;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.DeviceTokenRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.service.DeviceAuthService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * UserController navigation between list, form, detail, edit and change-password pages, and the
 * device session table on the user detail page. User CRUD, validation, password rules,
 * activation, session revocation, delete and access control are verified by
 * ServiceUserManagementTest; tests here work on users they create.
 */
@DisplayName("User Controller Tests")
@Import(ServiceTestDataInitializer.class)
class UserControllerFunctionalTest extends PlaywrightTestBase {

    private static final DateTimeFormatter TABLE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Autowired private UserRepository userRepository;
    @Autowired private DeviceTokenRepository deviceTokenRepository;
    @Autowired private DeviceAuthService deviceAuthService;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("List opens an empty new-user form; detail opens a prefilled edit form and change password; cancel returns")
    void shouldNavigateBetweenUserPages() {
        User user = createUser();

        navigateTo("/users");
        waitForPageLoad();
        assertThat(page.locator("#btn-new-user")).hasAttribute("href", "/users/new");
        page.locator("#btn-new-user").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/users/new");
        assertThat(page.locator("#page-title")).hasText("Pengguna Baru");
        assertThat(page.locator("#username")).hasValue("");
        assertThat(page.locator("#password")).hasValue("");
        assertThat(page.locator("#fullName")).hasValue("");
        assertThat(page.locator("#email")).hasValue("");
        assertThat(page.locator("input[name='selectedRoles']:checked")).hasCount(0);

        navigateTo("/users/" + user.getId());
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText(user.getFullName());
        page.locator("a[href='/users/" + user.getId() + "/edit']").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/users/" + user.getId() + "/edit");
        assertThat(page.locator("#page-title")).hasText("Edit Pengguna");
        assertThat(page.locator("#username")).hasValue(user.getUsername());
        assertThat(page.locator("#fullName")).hasValue(user.getFullName());
        assertThat(page.locator("#email")).hasValue(user.getEmail());
        assertThat(page.locator("input[name='selectedRoles']:checked")).hasCount(1);
        assertThat(page.locator("input[name='selectedRoles'][value='ACCOUNTANT']")).isChecked();

        navigateTo("/users/" + user.getId());
        waitForPageLoad();
        page.locator("#link-change-password").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/users/" + user.getId() + "/change-password");
        assertThat(page.locator("#page-title")).hasText("Ubah Password");
        assertThat(page.locator("#newPassword")).hasValue("");
        assertThat(page.locator("#confirmPassword")).hasValue("");
        page.locator("#btn-cancel").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/users/" + user.getId());
        assertThat(userRepository.findById(user.getId()).orElseThrow().getPassword()).isEqualTo(user.getPassword());
    }

    @Test
    @DisplayName("Device session table lists each active token with device name, client id, status, creation and last use")
    void shouldListDeviceSessions() {
        User user = createUser();
        DeviceToken used = deviceAuthService.createAccessToken(user, "cli-" + uid(), "Laptop Kantor");
        DeviceToken idle = deviceAuthService.createAccessToken(user, "cli-" + uid(), "Ponsel");
        String bearer = used.getTokenHash();

        APIRequestContext api = playwright.request().newContext(new APIRequest.NewContextOptions().setBaseURL(baseUrl()));
        try {
            assertThat(api.get("/api/templates", RequestOptions.create()
                    .setHeader("Authorization", "Bearer " + bearer)
                    .setHeader("X-Forwarded-For", "10.77.1.2")).status()).isEqualTo(200);
        } finally {
            api.dispose();
        }
        DeviceToken usedAfter = deviceTokenRepository.findById(used.getId()).orElseThrow();
        assertThat(usedAfter.getLastUsedAt()).isNotNull();
        DeviceToken idleAfter = deviceTokenRepository.findById(idle.getId()).orElseThrow();

        navigateTo("/users/" + user.getId());
        waitForPageLoad();

        Locator rows = page.locator("#device-sessions tbody tr");
        assertThat(rows).hasCount(2);
        assertThat(sessionRow(user, used).locator("td")).hasText(new String[] {
                "Laptop Kantor", used.getClientId(), "Aktif", usedAfter.getCreatedAt().format(TABLE_TIME),
                usedAfter.getLastUsedAt().format(TABLE_TIME), usedAfter.getLastUsedIp(), "Cabut"});
        assertThat(sessionRow(user, idle).locator("td")).hasText(new String[] {
                "Ponsel", idle.getClientId(), "Aktif", idleAfter.getCreatedAt().format(TABLE_TIME), "-", "-", "Cabut"});
        assertThat(page.locator("#form-revoke-all")).hasCount(1);
        takeManualScreenshot("users/device-sessions");
    }

    private Locator sessionRow(User user, DeviceToken token) {
        return page.locator("#device-sessions tbody tr:has(form[action='/users/" + user.getId()
                + "/revoke-session/" + token.getId() + "'])");
    }

    private User createUser() {
        String id = uid();
        User user = new User();
        user.setUsername("usernav-" + id);
        user.setPassword(passwordEncoder.encode("Rahasia#2045x"));
        user.setFullName("Pengguna Navigasi " + id);
        user.setEmail("usernav-" + id + "@example.com");
        user.setActive(true);
        user.setRoles(Set.of(Role.ACCOUNTANT), "system");
        return userRepository.save(user);
    }

    private static String uid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
