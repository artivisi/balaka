package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests for Chart of Accounts feature.
 * Tests account list, creation, editing, and validation.
 */
@DisplayName("Chart of Accounts")
@Import(ServiceTestDataInitializer.class)
class ChartOfAccountsTest extends PlaywrightTestBase {

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
    }

    @Nested
    @DisplayName("List Page")
    class ListPageTests {

        @Test
        @DisplayName("Should display accounts list page with title")
        void shouldDisplayAccountsListPage() {
            navigateTo("/accounts");
            waitForPageLoad();

            assertThat(page.locator("#page-title")).isVisible();
            assertThat(page.locator("#page-title")).hasText("Bagan Akun");
        }

        @Test
        @DisplayName("Should display root accounts")
        void shouldDisplayRootAccounts() {
            navigateTo("/accounts");
            waitForPageLoad();

            // Should see major account categories
            assertThat(page.locator("#account-row-1")).isVisible();
        }

        @Test
        @DisplayName("Should expand account hierarchy")
        void shouldExpandAccountHierarchy() {
            navigateTo("/accounts");
            waitForPageLoad();

            // Click expand on account 5 (BEBAN)
            page.locator("#btn-expand-5").click();
            page.waitForTimeout(300);

            // Should now see children
            assertThat(page.locator("#account-row-5-1")).isVisible();
        }
    }

    @Nested
    @DisplayName("New Account Form")
    class NewAccountFormTests {

        @Test
        @DisplayName("Should display new account form with title")
        void shouldDisplayNewAccountForm() {
            navigateTo("/accounts/new");
            waitForPageLoad();

            assertThat(page.locator("#page-title")).isVisible();
            assertThat(page.locator("#page-title")).hasText("Tambah Akun");
        }

        @Test
        @DisplayName("Should display account code field")
        void shouldDisplayAccountCodeField() {
            navigateTo("/accounts/new");
            waitForPageLoad();

            assertThat(page.locator("#accountCode")).isVisible();
        }

        @Test
        @DisplayName("Should display account name field")
        void shouldDisplayAccountNameField() {
            navigateTo("/accounts/new");
            waitForPageLoad();

            assertThat(page.locator("#accountName")).isVisible();
        }

        @Test
        @DisplayName("Should display account type dropdown")
        void shouldDisplayAccountTypeDropdown() {
            navigateTo("/accounts/new");
            waitForPageLoad();

            assertThat(page.locator("#accountType")).isVisible();
        }

        @Test
        @DisplayName("Should display parent dropdown")
        void shouldDisplayParentDropdown() {
            navigateTo("/accounts/new");
            waitForPageLoad();

            assertThat(page.locator("#parentId")).isVisible();
        }

        @Test
        @DisplayName("Should display submit button")
        void shouldDisplaySubmitButton() {
            navigateTo("/accounts/new");
            waitForPageLoad();

            assertThat(page.locator("#btn-simpan")).isVisible();
        }

        @Test
        @DisplayName("Should auto-suggest permanent based on account type")
        void autoSuggestPermanentBasedOnAccountType() {
            navigateTo("/accounts/new");
            waitForPageLoad();

            // Select EXPENSE type - should auto-uncheck permanent
            page.getByTestId("account-type").selectOption("EXPENSE");
            page.waitForTimeout(100);

            assertThat(page.locator("#permanent").isChecked())
                .as("Permanent should be unchecked for EXPENSE type")
                .isFalse();

            // Select ASSET type - should auto-check permanent
            page.getByTestId("account-type").selectOption("ASSET");
            page.waitForTimeout(100);

            assertThat(page.locator("#permanent").isChecked())
                .as("Permanent should be checked for ASSET type")
                .isTrue();

            // Select REVENUE type - should auto-uncheck permanent
            page.getByTestId("account-type").selectOption("REVENUE");
            page.waitForTimeout(100);

            assertThat(page.locator("#permanent").isChecked())
                .as("Permanent should be unchecked for REVENUE type")
                .isFalse();

            // Select LIABILITY type - should auto-check permanent
            page.getByTestId("account-type").selectOption("LIABILITY");
            page.waitForTimeout(100);

            assertThat(page.locator("#permanent").isChecked())
                .as("Permanent should be checked for LIABILITY type")
                .isTrue();
        }
    }

    @Nested
    @DisplayName("Edit Account Form")
    class EditAccountFormTests {

        @Test
        @DisplayName("Should edit account with parent without validation error")
        void editAccountWithParentWithoutValidationError() {
            navigateTo("/accounts");
            waitForPageLoad();

            // Expand hierarchy to reach 5.9.01
            page.locator("#btn-expand-5").click();
            page.waitForTimeout(300);
            page.locator("#btn-expand-5-9").click();
            page.waitForTimeout(300);

            // Click edit on account 5.9.01 (has parent 5.9)
            page.locator("#btn-edit-5-9-01").click();
            waitForPageLoad();

            // Verify we're on the edit form
            assertThat(page.title())
                .as("Page title should contain 'Edit Akun'")
                .contains("Edit Akun");

            // Verify accountType is disabled (inherit from parent)
            assertThat(page.getByTestId("account-type").isDisabled())
                .as("Account type should be disabled when account has parent")
                .isTrue();

            // Modify the description
            page.getByTestId("description").fill("Updated description");

            // Submit the form
            page.getByTestId("btn-submit").click();
            waitForPageLoad();

            // Verify redirected back to accounts list
            assertThat(page.url())
                .as("Should redirect to accounts list after successful save")
                .contains("/accounts");
            assertThat(page.url())
                .as("Should not be on edit page")
                .doesNotContain("/edit");
        }

        @Test
        @DisplayName("Should show inherited values from parent when editing child account")
        void showInheritedValuesFromParentWhenEditingChildAccount() {
            navigateTo("/accounts");
            waitForPageLoad();

            // Expand hierarchy to reach 5.9.01
            page.locator("#btn-expand-5").click();
            page.waitForTimeout(300);
            page.locator("#btn-expand-5-9").click();
            page.waitForTimeout(300);

            // Click edit on account 5.9.01
            page.locator("#btn-edit-5-9-01").click();
            waitForPageLoad();

            // Verify account type is disabled
            assertThat(page.getByTestId("account-type").isDisabled())
                .as("Account type should be disabled")
                .isTrue();

            // The selected value should be EXPENSE
            String selectedType = page.getByTestId("account-type").inputValue();
            assertThat(selectedType)
                .as("Account type should show EXPENSE from parent")
                .isEqualTo("EXPENSE");

            // Verify help text shows that it follows parent
            assertThat(page.locator("text=Tipe akun mengikuti akun induk").isVisible())
                .as("Help text should indicate account type follows parent")
                .isTrue();
        }

        @Test
        @DisplayName("Should show cannot edit message for account with children")
        void shouldShowCannotEditMessageForAccountWithChildren() {
            navigateTo("/accounts");
            waitForPageLoad();

            // Click edit on root account "5 - BEBAN" (has children)
            page.locator("#btn-edit-5").click();
            waitForPageLoad();

            // Verify message about having children
            assertThat(page.locator("text=Tipe akun tidak dapat diubah karena memiliki akun anak").isVisible())
                .as("Help text should indicate account type cannot change due to children")
                .isTrue();
        }

        @Test
        @DisplayName("Should save permanent field change")
        void savePermanentFieldChange() {
            navigateTo("/accounts");
            waitForPageLoad();

            // Expand to reach 5.9.01
            page.locator("#btn-expand-5").click();
            page.waitForTimeout(300);
            page.locator("#btn-expand-5-9").click();
            page.waitForTimeout(300);

            // Click edit on account 5.9.01
            page.locator("#btn-edit-5-9-01").click();
            waitForPageLoad();

            // Get initial state of permanent checkbox
            boolean initiallyChecked = page.locator("#permanent").isChecked();

            // Toggle the permanent checkbox
            page.locator("#permanent").click();

            // Submit the form
            page.getByTestId("btn-submit").click();
            waitForPageLoad();

            // Verify success - redirected to list
            assertThat(page.url())
                .as("Should redirect to accounts list after successful save")
                .contains("/accounts");

            // Edit again to verify the change was saved
            page.locator("#btn-expand-5").click();
            page.waitForTimeout(300);
            page.locator("#btn-expand-5-9").click();
            page.waitForTimeout(300);
            page.locator("#btn-edit-5-9-01").click();
            waitForPageLoad();

            // Verify the permanent checkbox state changed
            boolean afterSaveChecked = page.locator("#permanent").isChecked();
            assertThat(afterSaveChecked)
                .as("Permanent checkbox state should have changed after save")
                .isNotEqualTo(initiallyChecked);
        }
    }
}
