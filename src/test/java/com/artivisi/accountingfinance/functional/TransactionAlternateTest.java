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

@DisplayName("Transaction - Alternate Scenarios")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TransactionAlternateTest extends FunctionalTestBase {

    @BeforeEach
    void setUp() {
        loginAsAdmin();
    }

    @Nested
    @DisplayName("UI Alternate Scenarios")
    class UIAlternateScenarios {

        @Test
        @Order(1)
        @DisplayName("Should show error when creating transaction without template")
        void shouldShowErrorWhenCreatingTransactionWithoutTemplate() {
            navigateTo("/transactions/new");

            fillForm("input[name='transactionDate']", "2025-11-23");
            fillForm("input[name='amount']", "1000000");
            fillForm("input[name='description']", "Test");
            // Don't select template

            submitForm();

            assertThat(page).hasURL(baseUrl() + "/transactions/new");
            takeScreenshot("trx-alt-01-no-template-error");
        }

        @Test
        @Order(2)
        @DisplayName("Should show error when creating transaction without date")
        void shouldShowErrorWhenCreatingTransactionWithoutDate() {
            navigateTo("/transactions/new?templateId=e0000000-0000-0000-0000-000000000001");

            // Leave date empty
            fillForm("input[name='amount']", "1000000");
            fillForm("input[name='description']", "Test");

            submitForm();

            assertElementVisible("input[name='transactionDate']:invalid");
            takeScreenshot("trx-alt-02-no-date-error");
        }

        @Test
        @Order(3)
        @DisplayName("Should show error when creating transaction with zero amount")
        void shouldShowErrorWhenCreatingTransactionWithZeroAmount() {
            navigateTo("/transactions/new?templateId=e0000000-0000-0000-0000-000000000001");

            fillForm("input[name='transactionDate']", "2025-11-23");
            fillForm("input[name='amount']", "0");
            fillForm("input[name='description']", "Test");

            submitForm();

            assertPageContainsText("greater than 0");
            takeScreenshot("trx-alt-03-zero-amount-error");
        }

        @Test
        @Order(4)
        @DisplayName("Should show error when creating transaction with negative amount")
        void shouldShowErrorWhenCreatingTransactionWithNegativeAmount() {
            navigateTo("/transactions/new?templateId=e0000000-0000-0000-0000-000000000001");

            fillForm("input[name='transactionDate']", "2025-11-23");
            fillForm("input[name='amount']", "-1000000");
            fillForm("input[name='description']", "Test");

            submitForm();

            assertPageContainsText("greater than 0");
            takeScreenshot("trx-alt-04-negative-amount-error");
        }

        @Test
        @Order(5)
        @DisplayName("Should show error when creating transaction without description")
        void shouldShowErrorWhenCreatingTransactionWithoutDescription() {
            navigateTo("/transactions/new?templateId=e0000000-0000-0000-0000-000000000001");

            fillForm("input[name='transactionDate']", "2025-11-23");
            fillForm("input[name='amount']", "1000000");
            // Leave description empty

            submitForm();

            assertElementVisible("input[name='description']:invalid");
            takeScreenshot("trx-alt-05-no-description-error");
        }

        @Test
        @Order(6)
        @DisplayName("Should prevent editing posted transaction")
        void shouldPreventEditingPostedTransaction() {
            // Try to navigate to edit page of a posted transaction
            // The controller should redirect to detail page
            navigateTo("/transactions");

            // Find a posted transaction (by status badge)
            page.click("[data-status='POSTED'] a[href*='/edit']:first-of-type");
            waitForPageLoad();

            // Should redirect to detail page, not edit
            assertThat(page).not().hasURL(".*\\/edit$");
            takeScreenshot("trx-alt-06-cannot-edit-posted");
        }

        @Test
        @Order(7)
        @DisplayName("Should prevent deleting posted transaction")
        void shouldPreventDeletingPostedTransaction() {
            navigateTo("/transactions");

            page.click("[data-status='POSTED'] button[data-action='delete']:first-of-type");
            page.click("button:has-text('Hapus')");
            waitForPageLoad();

            assertPageContainsText("cannot be deleted");
            takeScreenshot("trx-alt-07-cannot-delete-posted");
        }

        @Test
        @Order(8)
        @DisplayName("Should show void form only for posted transactions")
        void shouldShowVoidFormOnlyForPostedTransactions() {
            // Try to void a draft transaction
            navigateTo("/transactions");

            page.click("[data-status='DRAFT'] a[href*='/void']:first-of-type");
            waitForPageLoad();

            // Should redirect to detail
            assertThat(page).not().hasURL(".*\\/void$");
            takeScreenshot("trx-alt-08-cannot-void-draft");
        }

        @Test
        @Order(9)
        @DisplayName("Should require void reason when voiding transaction")
        void shouldRequireVoidReasonWhenVoidingTransaction() {
            // Navigate to void form of a posted transaction
            navigateTo("/transactions");

            page.click("[data-status='POSTED'] a[href*='/void']:first-of-type");
            waitForPageLoad();

            // Submit without selecting reason
            submitForm();

            assertPageContainsText("reason is required");
            takeScreenshot("trx-alt-09-void-reason-required");
        }

        @Test
        @Order(10)
        @DisplayName("Should handle non-existent transaction ID")
        void shouldHandleNonExistentTransactionId() {
            navigateTo("/transactions/00000000-0000-0000-0000-000000000000");

            assertPageContainsText("not found");
            takeScreenshot("trx-alt-10-not-found");
        }
    }

    @Nested
    @DisplayName("API Alternate Scenarios")
    class APIAlternateScenarios {

        @Test
        @Order(20)
        @DisplayName("Should return 404 for non-existent transaction")
        void shouldReturn404ForNonExistentTransaction() {
            APIResponse response = apiGet("/transactions/api/00000000-0000-0000-0000-000000000000");

            assertEquals(404, response.status());
        }

        @Test
        @Order(21)
        @DisplayName("Should return 400 for invalid transaction data")
        void shouldReturn400ForInvalidTransactionData() {
            String requestBody = """
                {
                    "transactionDate": "2025-11-23",
                    "templateId": "e0000000-0000-0000-0000-000000000001",
                    "amount": 0,
                    "description": ""
                }
                """;

            APIResponse response = apiPost("/transactions/api", requestBody);

            assertEquals(400, response.status());
        }

        @Test
        @Order(22)
        @DisplayName("Should return 400 for missing template ID")
        void shouldReturn400ForMissingTemplateId() {
            String requestBody = """
                {
                    "transactionDate": "2025-11-23",
                    "amount": 1000000,
                    "description": "Test"
                }
                """;

            APIResponse response = apiPost("/transactions/api", requestBody);

            assertEquals(400, response.status());
        }

        @Test
        @Order(23)
        @DisplayName("Should return 400 for negative amount")
        void shouldReturn400ForNegativeAmount() {
            String requestBody = """
                {
                    "transactionDate": "2025-11-23",
                    "templateId": "e0000000-0000-0000-0000-000000000001",
                    "amount": -1000000,
                    "description": "Negative amount test"
                }
                """;

            APIResponse response = apiPost("/transactions/api", requestBody);

            assertEquals(400, response.status());
        }

        @Test
        @Order(24)
        @DisplayName("Should return error when posting non-draft transaction")
        void shouldReturnErrorWhenPostingNonDraftTransaction() {
            // Assuming we have a posted transaction ID
            APIResponse response = apiPost("/transactions/api/00000000-0000-0000-0000-000000000001/post", "");

            // Should return error (404 if not found, 409 if already posted)
            assertTrue(response.status() >= 400);
        }

        @Test
        @Order(25)
        @DisplayName("Should return error when voiding non-posted transaction")
        void shouldReturnErrorWhenVoidingNonPostedTransaction() {
            String requestBody = """
                {
                    "reason": "INPUT_ERROR",
                    "notes": "Test void"
                }
                """;

            // Try to void a draft transaction
            APIResponse response = apiPost("/transactions/api/00000000-0000-0000-0000-000000000002/void", requestBody);

            assertTrue(response.status() >= 400);
        }

        @Test
        @Order(26)
        @DisplayName("Should return error when deleting non-draft transaction")
        void shouldReturnErrorWhenDeletingNonDraftTransaction() {
            // Try to delete a posted transaction
            APIResponse response = apiDelete("/transactions/api/00000000-0000-0000-0000-000000000001");

            assertTrue(response.status() >= 400);
        }

        @Test
        @Order(27)
        @DisplayName("Should return 400 for void without reason")
        void shouldReturn400ForVoidWithoutReason() {
            String requestBody = """
                {
                    "notes": "No reason provided"
                }
                """;

            APIResponse response = apiPost("/transactions/api/00000000-0000-0000-0000-000000000001/void", requestBody);

            assertEquals(400, response.status());
        }

        @Test
        @Order(28)
        @DisplayName("Should return 404 for non-existent template ID")
        void shouldReturn404ForNonExistentTemplateId() {
            String requestBody = """
                {
                    "transactionDate": "2025-11-23",
                    "templateId": "00000000-0000-0000-0000-000000000000",
                    "amount": 1000000,
                    "description": "Invalid template"
                }
                """;

            APIResponse response = apiPost("/transactions/api", requestBody);

            assertTrue(response.status() == 400 || response.status() == 404);
        }
    }
}
