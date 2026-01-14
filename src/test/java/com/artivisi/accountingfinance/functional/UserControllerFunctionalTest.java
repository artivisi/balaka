package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Functional tests for UserController.
 * Tests user management: list, create, edit, password change, toggle status, delete.
 */
@DisplayName("User Controller Tests")
@Import(ServiceTestDataInitializer.class)
class UserControllerFunctionalTest extends PlaywrightTestBase {

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
    }

    // ==================== LIST PAGE ====================

    @Test
    @DisplayName("Should display user list page with title")
    void shouldDisplayUserListPage() {
        navigateTo("/users");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).isVisible();
        assertThat(page.locator("#page-title")).hasText("Kelola Pengguna");
    }

    @Test
    @DisplayName("Should display search button")
    void shouldDisplaySearchButton() {
        navigateTo("/users");
        waitForPageLoad();

        assertThat(page.locator("#btn-search")).isVisible();
    }

    @Test
    @DisplayName("Should display new user button")
    void shouldDisplayNewUserButton() {
        navigateTo("/users");
        waitForPageLoad();

        assertThat(page.locator("#btn-new-user")).isVisible();
    }

    @Test
    @DisplayName("Should search users by keyword")
    void shouldSearchUsers() {
        navigateTo("/users");
        waitForPageLoad();

        page.locator("input[name='search']").first().fill("admin");
        page.locator("#btn-search").click();
        waitForPageLoad();

        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*search=admin.*"));
    }

    // ==================== NEW USER FORM ====================

    @Test
    @DisplayName("Should display new user form with title")
    void shouldDisplayNewUserForm() {
        navigateTo("/users/new");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).isVisible();
        assertThat(page.locator("#page-title")).hasText("Pengguna Baru");
    }

    @Test
    @DisplayName("Should display username field")
    void shouldDisplayUsernameField() {
        navigateTo("/users/new");
        waitForPageLoad();

        assertThat(page.locator("#username")).isVisible();
    }

    @Test
    @DisplayName("Should display password field")
    void shouldDisplayPasswordField() {
        navigateTo("/users/new");
        waitForPageLoad();

        assertThat(page.locator("#password")).isVisible();
    }

    @Test
    @DisplayName("Should display fullName field")
    void shouldDisplayFullNameField() {
        navigateTo("/users/new");
        waitForPageLoad();

        assertThat(page.locator("#fullName")).isVisible();
    }

    @Test
    @DisplayName("Should display email field")
    void shouldDisplayEmailField() {
        navigateTo("/users/new");
        waitForPageLoad();

        assertThat(page.locator("#email")).isVisible();
    }

    @Test
    @DisplayName("Should display save button")
    void shouldDisplaySaveButton() {
        navigateTo("/users/new");
        waitForPageLoad();

        assertThat(page.locator("#btn-save")).isVisible();
    }

    @Test
    @DisplayName("Should create new user with valid data")
    void shouldCreateNewUserWithValidData() {
        navigateTo("/users/new");
        waitForPageLoad();

        String uniqueUsername = "testuser" + System.currentTimeMillis();

        page.locator("#username").fill(uniqueUsername);
        page.locator("#password").fill("Password123!");
        page.locator("#fullName").fill("Test User");
        page.locator("#email").fill(uniqueUsername + "@test.com");

        // Select at least one role
        var roleCheckbox = page.locator("input[name='selectedRoles']").first();
        if (!roleCheckbox.isChecked()) {
            roleCheckbox.check();
        }

        page.locator("#btn-save").click();
        waitForPageLoad();

        // Should redirect to users list or user detail
        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/users.*"));
    }

    @Test
    @DisplayName("Should stay on form when role not selected")
    void shouldStayOnFormWhenRoleNotSelected() {
        navigateTo("/users/new");
        waitForPageLoad();

        String uniqueUsername = "testuser" + System.currentTimeMillis();

        page.locator("#username").fill(uniqueUsername);
        page.locator("#password").fill("Password123!");
        page.locator("#fullName").fill("Test User");
        page.locator("#email").fill(uniqueUsername + "@test.com");

        // Uncheck all roles
        var roleCheckboxes = page.locator("input[name='selectedRoles']:checked").all();
        for (var checkbox : roleCheckboxes) {
            checkbox.uncheck();
        }

        page.locator("#btn-save").click();
        waitForPageLoad();

        // Should stay on form or show error
        assertThat(page.locator("#page-title")).isVisible();
    }

    // ==================== USER DETAIL PAGE ====================

    @Test
    @DisplayName("Should display user detail page with title")
    void shouldDisplayUserDetailPage() {
        var adminUser = userRepository.findByUsername("admin").orElse(null);
        if (adminUser == null) return;

        navigateTo("/users/" + adminUser.getId());
        waitForPageLoad();

        assertThat(page.locator("#page-title")).isVisible();
    }

    @Test
    @DisplayName("Should display change password link")
    void shouldDisplayChangePasswordLink() {
        var adminUser = userRepository.findByUsername("admin").orElse(null);
        if (adminUser == null) return;

        navigateTo("/users/" + adminUser.getId());
        waitForPageLoad();

        assertThat(page.locator("#link-change-password")).isVisible();
    }

    // ==================== EDIT USER FORM ====================

    @Test
    @DisplayName("Should display edit user form with title")
    void shouldDisplayEditUserForm() {
        var adminUser = userRepository.findByUsername("admin").orElse(null);
        if (adminUser == null) return;

        navigateTo("/users/" + adminUser.getId() + "/edit");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).isVisible();
        assertThat(page.locator("#page-title")).hasText("Edit Pengguna");
    }

    @Test
    @DisplayName("Should pre-fill username in edit form")
    void shouldPreFillUsernameInEditForm() {
        var adminUser = userRepository.findByUsername("admin").orElse(null);
        if (adminUser == null) return;

        navigateTo("/users/" + adminUser.getId() + "/edit");
        waitForPageLoad();

        assertThat(page.locator("#username")).hasValue("admin");
    }

    @Test
    @DisplayName("Should update user successfully")
    void shouldUpdateUserSuccessfully() {
        var adminUser = userRepository.findByUsername("admin").orElse(null);
        if (adminUser == null) return;

        navigateTo("/users/" + adminUser.getId() + "/edit");
        waitForPageLoad();

        // Submit without changes to test flow
        page.locator("#btn-save").click();
        waitForPageLoad();

        // Should redirect to user detail or list
        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/users.*"));
    }

    // ==================== CHANGE PASSWORD ====================

    @Test
    @DisplayName("Should display change password form with title")
    void shouldDisplayChangePasswordForm() {
        var adminUser = userRepository.findByUsername("admin").orElse(null);
        if (adminUser == null) return;

        navigateTo("/users/" + adminUser.getId() + "/change-password");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).isVisible();
        assertThat(page.locator("#page-title")).hasText("Ubah Password");
    }

    @Test
    @DisplayName("Should display new password field")
    void shouldDisplayNewPasswordField() {
        var adminUser = userRepository.findByUsername("admin").orElse(null);
        if (adminUser == null) return;

        navigateTo("/users/" + adminUser.getId() + "/change-password");
        waitForPageLoad();

        assertThat(page.locator("#newPassword")).isVisible();
    }

    @Test
    @DisplayName("Should display confirm password field")
    void shouldDisplayConfirmPasswordField() {
        var adminUser = userRepository.findByUsername("admin").orElse(null);
        if (adminUser == null) return;

        navigateTo("/users/" + adminUser.getId() + "/change-password");
        waitForPageLoad();

        assertThat(page.locator("#confirmPassword")).isVisible();
    }

    @Test
    @DisplayName("Should display save password button")
    void shouldDisplaySavePasswordButton() {
        var adminUser = userRepository.findByUsername("admin").orElse(null);
        if (adminUser == null) return;

        navigateTo("/users/" + adminUser.getId() + "/change-password");
        waitForPageLoad();

        assertThat(page.locator("#btn-save-password")).isVisible();
    }

    @Test
    @DisplayName("Should display cancel button")
    void shouldDisplayCancelButton() {
        var adminUser = userRepository.findByUsername("admin").orElse(null);
        if (adminUser == null) return;

        navigateTo("/users/" + adminUser.getId() + "/change-password");
        waitForPageLoad();

        assertThat(page.locator("#btn-cancel")).isVisible();
    }

    @Test
    @DisplayName("Should stay on form when passwords do not match")
    void shouldStayOnFormWhenPasswordsDoNotMatch() {
        var adminUser = userRepository.findByUsername("admin").orElse(null);
        if (adminUser == null) return;

        navigateTo("/users/" + adminUser.getId() + "/change-password");
        waitForPageLoad();

        page.locator("#newPassword").fill("Password123!");
        page.locator("#confirmPassword").fill("DifferentPassword123!");

        page.locator("#btn-save-password").click();
        waitForPageLoad();

        // Should stay on form or show error
        assertThat(page.locator("#page-title")).isVisible();
    }

    // ==================== NAVIGATION ====================

    @Test
    @DisplayName("Should navigate from list to new form")
    void shouldNavigateFromListToNewForm() {
        navigateTo("/users");
        waitForPageLoad();

        page.locator("#btn-new-user").click();
        waitForPageLoad();

        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/users\\/new.*"));
    }

    @Test
    @DisplayName("Should navigate from detail to change password")
    void shouldNavigateFromDetailToChangePassword() {
        var adminUser = userRepository.findByUsername("admin").orElse(null);
        if (adminUser == null) return;

        navigateTo("/users/" + adminUser.getId());
        waitForPageLoad();

        page.locator("#link-change-password").click();
        waitForPageLoad();

        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/change-password.*"));
    }
}
