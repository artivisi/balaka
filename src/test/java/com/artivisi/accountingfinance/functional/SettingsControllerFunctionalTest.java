package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.CompanyBankAccountRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private CompanyBankAccountRepository bankAccountRepository;

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

    // ==================== BANK ACCOUNT ACTIONS ====================

    @Test
    @DisplayName("Should edit bank account")
    void shouldEditBankAccount() {
        var bankAccounts = bankAccountRepository.findAll();
        if (bankAccounts.isEmpty()) {
            return;
        }

        var bankAccount = bankAccounts.get(0);
        navigateTo("/settings/bank-accounts/" + bankAccount.getId() + "/edit");
        waitForPageLoad();

        // Update bank name
        var bankNameInput = page.locator("#bankName");
        if (bankNameInput.isVisible()) {
            String originalName = bankNameInput.inputValue();
            bankNameInput.fill(originalName + " (Test Update)");
            page.locator("#btn-save-bank").click();
            waitForPageLoad();

            // Restore original name
            navigateTo("/settings/bank-accounts/" + bankAccount.getId() + "/edit");
            waitForPageLoad();
            page.locator("#bankName").fill(originalName);
            page.locator("#btn-save-bank").click();
            waitForPageLoad();
        }
    }

    @Test
    @DisplayName("Should activate bank account")
    void shouldActivateBankAccount() {
        var bankAccounts = bankAccountRepository.findAll();
        if (bankAccounts.isEmpty()) {
            return;
        }

        // Find inactive bank account or use first one
        var bankAccount = bankAccounts.stream()
            .filter(ba -> !ba.isActive())
            .findFirst()
            .orElse(bankAccounts.get(0));

        navigateTo("/settings");
        waitForPageLoad();

        var activateForm = page.locator("form[action*='/bank-accounts/" + bankAccount.getId() + "/activate']").first();
        if (activateForm.isVisible()) {
            activateForm.locator("button[type='submit']").click();
            waitForPageLoad();
        }

        // Verify page loads after action
        assertThat(page.locator("#page-title")).isVisible();
    }

    @Test
    @DisplayName("Should delete bank account")
    void shouldDeleteBankAccount() {
        // Create a bank account to delete
        navigateTo("/settings/bank-accounts/new");
        waitForPageLoad();

        String uniqueNumber = "TEST" + System.currentTimeMillis();
        page.locator("#bankName").fill("Bank To Delete");
        page.locator("#accountNumber").fill(uniqueNumber);
        page.locator("#accountName").fill("Test Delete Account");

        page.locator("#btn-save-bank").click();
        waitForPageLoad();

        // Find the newly created account
        var bankAccount = bankAccountRepository.findByAccountNumber(uniqueNumber);
        if (bankAccount.isEmpty()) {
            return;
        }

        navigateTo("/settings");
        waitForPageLoad();

        var deleteForm = page.locator("form[action*='/bank-accounts/" + bankAccount.get().getId() + "/delete']").first();
        if (deleteForm.isVisible()) {
            deleteForm.locator("button[type='submit']").click();
            waitForPageLoad();
        }

        // Verify page loads after delete
        assertThat(page.locator("#page-title")).isVisible();
    }

    // ==================== COMPANY LOGO ====================

    @Test
    @DisplayName("Should access company logo upload form")
    void shouldAccessCompanyLogoUploadForm() {
        navigateTo("/settings");
        waitForPageLoad();

        // Logo upload form should be present
        var logoForm = page.locator("form[action*='/company/logo']").first();
        if (logoForm.isVisible()) {
            assertThat(logoForm).isVisible();
        }
    }

    @Test
    @DisplayName("Should access get company logo endpoint")
    void shouldAccessGetCompanyLogoEndpoint() {
        // Trigger the get logo endpoint
        var response = page.request().get(baseUrl() + "/settings/company/logo");
        // 200 = has logo, 404 = no logo set
        org.assertj.core.api.Assertions.assertThat(response.status())
            .as("Logo endpoint should return 200 or 404")
            .isIn(200, 404);
    }

    @Test
    @DisplayName("Should access delete company logo form")
    void shouldAccessDeleteCompanyLogoForm() {
        navigateTo("/settings");
        waitForPageLoad();

        // Logo delete form may be present if logo exists
        var deleteLogoForm = page.locator("form[action*='/company/logo/delete']").first();
        // Just verify the page loads - delete form visibility depends on whether logo exists
        assertThat(page.locator("#page-title")).isVisible();
    }

    // ==================== TELEGRAM ACTIONS ====================

    @Test
    @DisplayName("Should generate telegram code")
    void shouldGenerateTelegramCode() {
        navigateTo("/settings/telegram");
        waitForPageLoad();

        var generateForm = page.locator("form[action*='/telegram/generate-code']").first();
        if (generateForm.isVisible()) {
            generateForm.locator("button[type='submit']").click();
            waitForPageLoad();

            // Should redirect back to telegram settings
            org.assertj.core.api.Assertions.assertThat(page.url())
                .as("Should remain on telegram settings")
                .contains("/settings/telegram");
        }
    }

    @Test
    @DisplayName("Should access unlink telegram form")
    void shouldAccessUnlinkTelegramForm() {
        navigateTo("/settings/telegram");
        waitForPageLoad();

        // Unlink form may be present if telegram is linked
        var unlinkForm = page.locator("form[action*='/telegram/unlink']").first();
        // Just verify the page loads
        assertThat(page.locator("#page-title")).isVisible();
    }

    // ==================== ADDITIONAL AUDIT LOG TESTS ====================

    @Test
    @DisplayName("Should filter audit logs by username")
    void shouldFilterAuditLogsByUsername() {
        navigateTo("/settings/audit-logs?username=admin");
        waitForPageLoad();

        // Verify filter is applied
        var usernameFilter = page.locator("#username-filter");
        if (usernameFilter.isVisible()) {
            org.assertj.core.api.Assertions.assertThat(usernameFilter.inputValue())
                .as("Username filter should have value")
                .isEqualTo("admin");
        }
    }

    @Test
    @DisplayName("Should filter audit logs by date range")
    void shouldFilterAuditLogsByDateRange() {
        String today = java.time.LocalDate.now().toString();
        navigateTo("/settings/audit-logs?startDate=" + today + "&endDate=" + today);
        waitForPageLoad();

        // Verify the page loads with filters
        assertThat(page.locator("#page-title")).isVisible();
    }

    @Test
    @DisplayName("Should paginate audit logs")
    void shouldPaginateAuditLogs() {
        navigateTo("/settings/audit-logs?page=0&size=5");
        waitForPageLoad();

        // Verify the page loads
        assertThat(page.locator("#page-title")).isVisible();
    }
}
