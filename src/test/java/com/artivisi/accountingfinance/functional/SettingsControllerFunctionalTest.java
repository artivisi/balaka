package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Functional tests for SettingsController.
 * Tests company settings, bank account form, telegram, about, privacy, and audit logs.
 *
 * Note: Bank accounts list page (/settings/bank-accounts) removed - template does not exist.
 * Only the bank account form (/settings/bank-accounts/new) is testable.
 */
@DisplayName("Settings Controller Tests")
@Import(ServiceTestDataInitializer.class)
class SettingsControllerFunctionalTest extends PlaywrightTestBase {

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
    }

    // ==================== COMPANY SETTINGS ====================

    @Test
    @DisplayName("Should display company settings page")
    void shouldDisplayCompanySettingsPage() {
        navigateTo("/settings");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).isVisible();
        assertThat(page.locator("#companyName")).isVisible();
    }

    @Test
    @DisplayName("Should display company name field")
    void shouldDisplayCompanyNameField() {
        navigateTo("/settings");
        waitForPageLoad();

        assertThat(page.locator("#companyName")).isVisible();
    }

    @Test
    @DisplayName("Should display NPWP field")
    void shouldDisplayNpwpField() {
        navigateTo("/settings");
        waitForPageLoad();

        assertThat(page.locator("#npwp")).isVisible();
    }

    @Test
    @DisplayName("Should have add bank account button")
    void shouldHaveAddBankAccountButton() {
        navigateTo("/settings");
        waitForPageLoad();

        // The add bank account link exists on company page
        assertThat(page.locator("a[href*='/bank-accounts/new']").first()).isVisible();
    }

    @Test
    @DisplayName("Should update company settings")
    void shouldUpdateCompanySettings() {
        navigateTo("/settings");
        waitForPageLoad();

        // Update company name
        var companyNameInput = page.locator("#companyName");
        assertThat(companyNameInput).isVisible();
        String originalName = companyNameInput.inputValue();

        companyNameInput.fill(originalName + " Updated");
        page.locator("#btn-save-company").click();
        waitForPageLoad();

        // Verify success - page should reload with updated name
        assertThat(page.locator("#page-title")).isVisible();

        // Restore original name
        navigateTo("/settings");
        waitForPageLoad();
        page.locator("#companyName").fill(originalName);
        page.locator("#btn-save-company").click();
        waitForPageLoad();
    }

    // ==================== BANK ACCOUNT FORM ====================

    @Test
    @DisplayName("Should display new bank account form")
    void shouldDisplayNewBankAccountForm() {
        navigateTo("/settings/bank-accounts/new");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).isVisible();
        assertThat(page.locator("#bankName")).isVisible();
        assertThat(page.locator("#accountNumber")).isVisible();
        assertThat(page.locator("#accountName")).isVisible();
    }

    @Test
    @DisplayName("Should create new bank account")
    void shouldCreateNewBankAccount() {
        navigateTo("/settings/bank-accounts/new");
        waitForPageLoad();

        // Fill the form
        page.locator("#bankName").fill("Bank Test");
        page.locator("#accountNumber").fill("1234567890123");
        page.locator("#accountName").fill("PT Test Company");

        page.locator("#btn-save-bank").click();
        waitForPageLoad();

        // Should redirect to settings page (which shows bank accounts list)
        assertThat(page.locator("#page-title")).isVisible();
    }

    // ==================== TELEGRAM SETTINGS ====================

    @Test
    @DisplayName("Should display telegram settings page")
    void shouldDisplayTelegramSettingsPage() {
        navigateTo("/settings/telegram");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).isVisible();
        assertThat(page.locator("#telegram-settings-content")).isVisible();
    }

    // ==================== ABOUT PAGE ====================

    @Test
    @DisplayName("Should display about page")
    void shouldDisplayAboutPage() {
        navigateTo("/settings/about");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).isVisible();
    }

    @Test
    @DisplayName("Should show git commit info")
    void shouldShowGitCommitInfo() {
        navigateTo("/settings/about");
        waitForPageLoad();

        assertThat(page.locator("#value-commit-id")).isVisible();
    }

    @Test
    @DisplayName("Should have link to about page from settings")
    void shouldHaveLinkToAboutPage() {
        navigateTo("/settings");
        waitForPageLoad();

        assertThat(page.locator("a[href*='/settings/about']").first()).isVisible();
    }

    // ==================== PRIVACY PAGE ====================

    @Test
    @DisplayName("Should display privacy policy page")
    void shouldDisplayPrivacyPolicyPage() {
        navigateTo("/settings/privacy");
        waitForPageLoad();

        assertThat(page.locator("#privacy-page-content")).isVisible();
    }

    // ==================== AUDIT LOGS ====================

    @Test
    @DisplayName("Should display audit logs page")
    void shouldDisplayAuditLogsPage() {
        navigateTo("/settings/audit-logs");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).isVisible();
    }

    @Test
    @DisplayName("Should display event type filter")
    void shouldDisplayEventTypeFilter() {
        navigateTo("/settings/audit-logs");
        waitForPageLoad();

        assertThat(page.locator("#event-type-filter")).isVisible();
    }

    @Test
    @DisplayName("Should display date range filters")
    void shouldDisplayDateRangeFilters() {
        navigateTo("/settings/audit-logs");
        waitForPageLoad();

        assertThat(page.locator("#start-date")).isVisible();
        assertThat(page.locator("#end-date")).isVisible();
    }

    @Test
    @DisplayName("Should display username filter")
    void shouldDisplayUsernameFilter() {
        navigateTo("/settings/audit-logs");
        waitForPageLoad();

        assertThat(page.locator("#username-filter")).isVisible();
    }

    @Test
    @DisplayName("Should filter audit logs by event type")
    void shouldFilterAuditLogsByEventType() {
        // Navigate directly with filter parameter to test server-side filtering
        navigateTo("/settings/audit-logs?eventType=LOGIN_SUCCESS");
        waitForPageLoad();

        // Verify the filter is applied (select should have the value selected)
        assertThat(page.locator("#event-type-filter")).hasValue("LOGIN_SUCCESS");
    }

    @Test
    @DisplayName("Should show audit log table")
    void shouldShowAuditLogTable() {
        navigateTo("/settings/audit-logs");
        waitForPageLoad();

        // The page should have the audit log table
        assertThat(page.locator("#audit-log-table")).isVisible();
    }
}
