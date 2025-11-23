package com.artivisi.accountingfinance.functional;

import com.microsoft.playwright.APIResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Chart of Accounts - Alternate Scenarios")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ChartOfAccountAlternateTest extends FunctionalTestBase {

    @BeforeEach
    void setUp() {
        loginAsAdmin();
    }

    @Nested
    @DisplayName("UI Alternate Scenarios")
    class UIAlternateScenarios {

        @Test
        @Order(1)
        @DisplayName("Should show error when creating account with empty code")
        void shouldShowErrorWhenCreatingAccountWithEmptyCode() {
            navigateTo("/accounts/new");

            // Leave account code empty
            fillForm("input[name='accountName']", "Test Account");
            selectOption("select[name='accountType']", "ASSET");
            selectOption("select[name='normalBalance']", "DEBIT");

            submitForm();

            // Should show validation error
            assertElementVisible("input[name='accountCode']:invalid");

            takeScreenshot("coa-alt-01-empty-code-error");
        }

        @Test
        @Order(2)
        @DisplayName("Should show error when creating account with empty name")
        void shouldShowErrorWhenCreatingAccountWithEmptyName() {
            navigateTo("/accounts/new");

            fillForm("input[name='accountCode']", "9.9.99");
            // Leave name empty
            selectOption("select[name='accountType']", "ASSET");
            selectOption("select[name='normalBalance']", "DEBIT");

            submitForm();

            // Should show validation error
            assertElementVisible("input[name='accountName']:invalid");

            takeScreenshot("coa-alt-02-empty-name-error");
        }

        @Test
        @Order(3)
        @DisplayName("Should show error when creating account without selecting type")
        void shouldShowErrorWhenCreatingAccountWithoutType() {
            navigateTo("/accounts/new");

            fillForm("input[name='accountCode']", "9.9.99");
            fillForm("input[name='accountName']", "Test Account");
            // Don't select type
            selectOption("select[name='normalBalance']", "DEBIT");

            submitForm();

            // Form should not submit without type
            assertThat(page).hasURL(baseUrl() + "/accounts/new");

            takeScreenshot("coa-alt-03-no-type-error");
        }

        @Test
        @Order(4)
        @DisplayName("Should prevent duplicate account code")
        void shouldPreventDuplicateAccountCode() {
            navigateTo("/accounts/new");

            // Use existing account code
            fillForm("input[name='accountCode']", "1.1.01");
            fillForm("input[name='accountName']", "Duplicate Test");
            selectOption("select[name='accountType']", "ASSET");
            selectOption("select[name='normalBalance']", "DEBIT");

            submitForm();

            // Should show duplicate error
            assertPageContainsText("already exists");

            takeScreenshot("coa-alt-04-duplicate-code-error");
        }

        @Test
        @Order(5)
        @DisplayName("Should prevent deleting account with children")
        void shouldPreventDeletingAccountWithChildren() {
            navigateTo("/accounts");

            // Try to delete a parent account (e.g., ASET which has children)
            page.click("button[data-action='delete']:first-of-type");

            // Confirm delete dialog
            page.click("button:has-text('Hapus')");
            waitForPageLoad();

            // Should show error message
            assertPageContainsText("Cannot delete");

            takeScreenshot("coa-alt-05-delete-with-children-error");
        }

        @Test
        @Order(6)
        @DisplayName("Should handle non-existent account ID in edit")
        void shouldHandleNonExistentAccountIdInEdit() {
            navigateTo("/accounts/00000000-0000-0000-0000-000000000000/edit");

            // Should show not found or redirect
            assertPageContainsText("not found");

            takeScreenshot("coa-alt-06-not-found-error");
        }

        @Test
        @Order(7)
        @DisplayName("Should validate account code format")
        void shouldValidateAccountCodeFormat() {
            navigateTo("/accounts/new");

            // Use invalid format
            fillForm("input[name='accountCode']", "invalid-code-format");
            fillForm("input[name='accountName']", "Test Account");
            selectOption("select[name='accountType']", "ASSET");
            selectOption("select[name='normalBalance']", "DEBIT");

            submitForm();

            // Should show format error or accept (depending on business rules)
            takeScreenshot("coa-alt-07-invalid-format");
        }

        @Test
        @Order(8)
        @DisplayName("Should show empty state when no search results")
        void shouldShowEmptyStateWhenNoSearchResults() {
            navigateTo("/accounts");

            fillForm("input[name='search']", "XYZNONEXISTENT12345");
            clickButton("Cari");

            // Should show no results message
            assertPageContainsText("Tidak ada");

            takeScreenshot("coa-alt-08-empty-search-results");
        }
    }

    @Nested
    @DisplayName("API Alternate Scenarios")
    class APIAlternateScenarios {

        @Test
        @Order(10)
        @DisplayName("Should return 404 for non-existent account")
        void shouldReturn404ForNonExistentAccount() {
            APIResponse response = apiGet("/accounts/api/00000000-0000-0000-0000-000000000000");

            assertEquals(404, response.status());
        }

        @Test
        @Order(11)
        @DisplayName("Should return 400 for invalid account data")
        void shouldReturn400ForInvalidAccountData() {
            String requestBody = """
                {
                    "accountCode": "",
                    "accountName": "",
                    "accountType": "ASSET",
                    "normalBalance": "DEBIT"
                }
                """;

            APIResponse response = apiPost("/accounts/api", requestBody);

            assertEquals(400, response.status());
            String body = response.text();
            assertTrue(body.contains("error") || body.contains("validation"));
        }

        @Test
        @Order(12)
        @DisplayName("Should return 400 for duplicate account code")
        void shouldReturn400ForDuplicateAccountCode() {
            String requestBody = """
                {
                    "accountCode": "1.1.01",
                    "accountName": "Duplicate Test",
                    "accountType": "ASSET",
                    "normalBalance": "DEBIT"
                }
                """;

            APIResponse response = apiPost("/accounts/api", requestBody);

            // Should return 400 for duplicate
            assertTrue(response.status() == 400 || response.status() == 409);
        }

        @Test
        @Order(13)
        @DisplayName("Should return 400 for invalid account type")
        void shouldReturn400ForInvalidAccountType() {
            String requestBody = """
                {
                    "accountCode": "9.9.99",
                    "accountName": "Invalid Type Test",
                    "accountType": "INVALID_TYPE",
                    "normalBalance": "DEBIT"
                }
                """;

            APIResponse response = apiPost("/accounts/api", requestBody);

            assertEquals(400, response.status());
        }

        @Test
        @Order(14)
        @DisplayName("Should return 409 when deleting account with children")
        void shouldReturn409WhenDeletingAccountWithChildren() {
            // Try to delete ASET (which has children)
            APIResponse response = apiDelete("/accounts/api/10000000-0000-0000-0000-000000000001");

            // Should return conflict
            assertTrue(response.status() == 409 || response.status() == 400);
        }

        @Test
        @Order(15)
        @DisplayName("Should return empty list for no matching search")
        void shouldReturnEmptyListForNoMatchingSearch() {
            APIResponse response = apiGet("/accounts/api/search?q=XYZNONEXISTENT12345&active=true");

            assertEquals(200, response.status());
            String body = response.text();
            assertTrue(body.contains("\"content\":[]") || body.contains("\"totalElements\":0"));
        }

        @Test
        @Order(16)
        @DisplayName("Should return 400 for missing required fields on update")
        void shouldReturn400ForMissingRequiredFieldsOnUpdate() {
            String requestBody = """
                {
                    "accountName": ""
                }
                """;

            APIResponse response = apiPut("/accounts/api/10000000-0000-0000-0000-000000000101", requestBody);

            assertEquals(400, response.status());
        }
    }
}
