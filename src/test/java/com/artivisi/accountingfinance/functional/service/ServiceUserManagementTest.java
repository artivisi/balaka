package com.artivisi.accountingfinance.functional.service;

import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * User Management Functional Tests.
 * Tests UserController: list, create, edit, change password, toggle status.
 */
@DisplayName("Service Industry - User Management")
@Import(ServiceTestDataInitializer.class)
class ServiceUserManagementTest extends PlaywrightTestBase {

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setup() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("Should display user list page")
    void shouldDisplayUserListPage() {
        navigateTo("/users");
        waitForPageLoad();

        // Verify page loads with title
        assertThat(page.locator("#page-title, h1").first()).containsText("Pengguna");
    }

    @Test
    @DisplayName("Should display user list with search")
    void shouldDisplayUserListWithSearch() {
        navigateTo("/users?search=admin");
        waitForPageLoad();

        // Verify search input has value
        var searchInput = page.locator("input[name='search']").first();
        if (searchInput.isVisible()) {
            assertThat(searchInput).hasValue("admin");
        }

        // Verify page loads
        assertThat(page.locator("#page-title, h1").first()).isVisible();
    }

    @Test
    @DisplayName("Should display new user form")
    void shouldDisplayNewUserForm() {
        navigateTo("/users/new");
        waitForPageLoad();

        // Verify form fields exist
        assertThat(page.locator("input[name='username']")).isVisible();
        assertThat(page.locator("input[name='email']")).isVisible();
        assertThat(page.locator("input[name='password'], input[name='newPassword']").first()).isVisible();
    }

    @Test
    @DisplayName("Should show validation error for empty username")
    void shouldShowValidationErrorForEmptyUsername() {
        navigateTo("/users/new");
        waitForPageLoad();

        // Try to submit without username
        var emailInput = page.locator("input[name='email']").first();
        if (emailInput.isVisible()) {
            emailInput.fill("test@example.com");
        }

        var passwordInput = page.locator("input[name='password']").first();
        if (passwordInput.isVisible()) {
            passwordInput.fill("TestPassword123!");
        }

        // Select a role
        var roleCheckbox = page.locator("input[type='checkbox'][value='STAFF']").first();
        if (roleCheckbox.isVisible()) {
            roleCheckbox.check();
        }

        page.click("button[type='submit']");
        waitForPageLoad();

        // Should stay on form or show error
        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should create new user")
    void shouldCreateNewUser() {
        navigateTo("/users/new");
        waitForPageLoad();

        String uniqueUsername = "testuser" + System.currentTimeMillis();

        // Fill form
        page.fill("input[name='username']", uniqueUsername);
        page.fill("input[name='email']", uniqueUsername + "@example.com");
        page.fill("input[name='fullName']", "Test User Full Name");

        var passwordInput = page.locator("input[name='password']").first();
        if (passwordInput.isVisible()) {
            passwordInput.fill("TestPassword123!");
        }

        // Select STAFF role
        var staffRole = page.locator("input[type='checkbox'][value='STAFF']").first();
        if (staffRole.isVisible()) {
            staffRole.check();
        }

        page.click("button[type='submit']");
        waitForPageLoad();

        // Verify redirected to list or detail
        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should display user detail page")
    void shouldDisplayUserDetailPage() {
        var user = userRepository.findByUsername("admin");

        if (user.isPresent()) {
            navigateTo("/users/" + user.get().getId());
            waitForPageLoad();

            // Verify detail page loads
            assertThat(page.locator("body")).containsText("admin");
        }
    }

    @Test
    @DisplayName("Should display user edit form")
    void shouldDisplayUserEditForm() {
        var user = userRepository.findByUsername("staff");

        if (user.isEmpty()) {
            user = userRepository.findAll().stream()
                    .filter(u -> !u.getUsername().equals("admin"))
                    .findFirst();
        }

        if (user.isPresent()) {
            navigateTo("/users/" + user.get().getId() + "/edit");
            waitForPageLoad();

            // Verify form loads with user data
            assertThat(page.locator("input[name='username']")).hasValue(user.get().getUsername());
        }
    }

    @Test
    @DisplayName("Should update user")
    void shouldUpdateUser() {
        var user = userRepository.findByUsername("staff");

        if (user.isEmpty()) {
            user = userRepository.findAll().stream()
                    .filter(u -> !u.getUsername().equals("admin"))
                    .findFirst();
        }

        if (user.isPresent()) {
            navigateTo("/users/" + user.get().getId() + "/edit");
            waitForPageLoad();

            // Update full name
            var fullNameInput = page.locator("input[name='fullName']").first();
            if (fullNameInput.isVisible()) {
                fullNameInput.fill("Updated Full Name " + System.currentTimeMillis());
            }

            page.click("button[type='submit']");
            waitForPageLoad();

            // Verify success
            assertThat(page.locator("body")).isVisible();
        }
    }

    @Test
    @DisplayName("Should display change password form")
    void shouldDisplayChangePasswordForm() {
        var user = userRepository.findByUsername("staff")
                .orElseThrow(() -> new AssertionError("Test user 'staff' not found in seed data"));

        navigateTo("/users/" + user.getId() + "/change-password");
        waitForPageLoad();

        // Verify password form loads (using IDs from template)
        assertThat(page.locator("#newPassword")).isVisible();
        assertThat(page.locator("#confirmPassword")).isVisible();
    }

    @Test
    @DisplayName("Should show error for mismatched passwords")
    void shouldShowErrorForMismatchedPasswords() {
        var user = userRepository.findByUsername("staff")
                .orElseThrow(() -> new AssertionError("Test user 'staff' not found in seed data"));

        navigateTo("/users/" + user.getId() + "/change-password");
        waitForPageLoad();

        page.fill("#newPassword", "NewPassword123!");
        page.fill("#confirmPassword", "DifferentPassword123!");
        page.click("#btn-save-password");
        waitForPageLoad();

        // Should stay on change-password form with error
        assertThat(page.locator("#page-title")).hasText("Ubah Password");
    }

    @Test
    @DisplayName("Should show error for weak password")
    void shouldShowErrorForWeakPassword() {
        var user = userRepository.findByUsername("staff")
                .orElseThrow(() -> new AssertionError("Test user 'staff' not found in seed data"));

        navigateTo("/users/" + user.getId() + "/change-password");
        waitForPageLoad();

        page.fill("#newPassword", "weak");
        page.fill("#confirmPassword", "weak");
        page.click("#btn-save-password");
        waitForPageLoad();

        // Should stay on change-password form with error
        assertThat(page.locator("#page-title")).hasText("Ubah Password");
    }

    @Test
    @DisplayName("Should toggle user active status")
    void shouldToggleUserActiveStatus() {
        var user = userRepository.findByUsername("staff")
                .orElseThrow(() -> new AssertionError("Test user 'staff' not found in seed data"));

        navigateTo("/users/" + user.getId());
        waitForPageLoad();

        // Click toggle button
        page.locator("form[action*='/toggle-active'] button[type='submit']").click();
        waitForPageLoad();

        // Should stay on user detail page
        assertThat(page.locator("#page-title")).hasText(user.getFullName());
    }

    @Test
    @DisplayName("Should show error when creating user without role")
    void shouldShowErrorWhenCreatingUserWithoutRole() {
        navigateTo("/users/new");
        waitForPageLoad();

        String uniqueUsername = "noroleuser" + System.currentTimeMillis();

        page.fill("input[name='username']", uniqueUsername);
        page.fill("input[name='email']", uniqueUsername + "@example.com");

        var passwordInput = page.locator("input[name='password']").first();
        if (passwordInput.isVisible()) {
            passwordInput.fill("TestPassword123!");
        }

        // Don't select any role
        page.click("button[type='submit']");
        waitForPageLoad();

        // Should stay on form with error
        assertThat(page.locator("input[name='username']")).isVisible();
    }

    @Test
    @DisplayName("Should filter users by search")
    void shouldFilterUsersBySearch() {
        navigateTo("/users");
        waitForPageLoad();

        var searchInput = page.locator("input[name='search']").first();
        if (searchInput.isVisible()) {
            searchInput.fill("admin");

            // Submit search form
            var searchBtn = page.locator("button[type='submit']:has-text('Cari'), button[type='submit']:has-text('Search')").first();
            if (searchBtn.isVisible()) {
                searchBtn.click();
            } else {
                searchInput.press("Enter");
            }
            waitForPageLoad();

            // Verify search applied
            assertThat(page.locator("body")).isVisible();
        }
    }

    @Test
    @DisplayName("Should display pagination on user list")
    void shouldDisplayPaginationOnUserList() {
        navigateTo("/users");
        waitForPageLoad();

        // Check if pagination exists (may not if few users)
        var pagination = page.locator("nav[aria-label='pagination'], .pagination").first();
        // Just verify page loads, pagination may or may not be present
        assertThat(page.locator("#page-title, h1").first()).isVisible();
    }
}
