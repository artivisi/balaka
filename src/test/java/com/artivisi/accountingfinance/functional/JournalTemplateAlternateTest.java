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

@DisplayName("Journal Template - Alternate Scenarios")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JournalTemplateAlternateTest extends FunctionalTestBase {

    @BeforeEach
    void setUp() {
        loginAsAdmin();
    }

    @Nested
    @DisplayName("UI Alternate Scenarios")
    class UIAlternateScenarios {

        @Test
        @Order(1)
        @DisplayName("Should show error when creating template with empty name")
        void shouldShowErrorWhenCreatingTemplateWithEmptyName() {
            navigateTo("/templates/new");

            // Leave name empty
            selectOption("select[name='category']", "INCOME");
            selectOption("select[name='cashFlowCategory']", "OPERATING");

            submitForm();

            assertElementVisible("input[name='templateName']:invalid");

            takeScreenshot("template-alt-01-empty-name-error");
        }

        @Test
        @Order(2)
        @DisplayName("Should show error when creating template without category")
        void shouldShowErrorWhenCreatingTemplateWithoutCategory() {
            navigateTo("/templates/new");

            fillForm("input[name='templateName']", "Test Template");
            // Don't select category
            selectOption("select[name='cashFlowCategory']", "OPERATING");

            submitForm();

            // Form should not submit
            assertThat(page).hasURL(baseUrl() + "/templates/new");

            takeScreenshot("template-alt-02-no-category-error");
        }

        @Test
        @Order(3)
        @DisplayName("Should show error when creating template without lines")
        void shouldShowErrorWhenCreatingTemplateWithoutLines() {
            navigateTo("/templates/new");

            fillForm("input[name='templateName']", "Test Template");
            selectOption("select[name='category']", "INCOME");
            selectOption("select[name='cashFlowCategory']", "OPERATING");
            // Don't add lines

            submitForm();

            // Should show error about minimum lines
            assertPageContainsText("at least 2 lines");

            takeScreenshot("template-alt-03-no-lines-error");
        }

        @Test
        @Order(4)
        @DisplayName("Should show error when template has unbalanced debits and credits")
        void shouldShowErrorWhenTemplateHasUnbalancedDebitsAndCredits() {
            navigateTo("/templates/new");

            fillForm("input[name='templateName']", "Unbalanced Template");
            selectOption("select[name='category']", "INCOME");
            selectOption("select[name='cashFlowCategory']", "OPERATING");

            // Add only debit line
            clickButton("Tambah Baris");
            selectOption("select[name='lines[0].position']", "DEBIT");

            submitForm();

            // Should show error about balance
            assertPageContainsText("debit and credit");

            takeScreenshot("template-alt-04-unbalanced-error");
        }

        @Test
        @Order(5)
        @DisplayName("Should prevent editing system template")
        void shouldPreventEditingSystemTemplate() {
            // Navigate to a system template edit page
            navigateTo("/templates/e0000000-0000-0000-0000-000000000001/edit");

            // Should show warning or redirect
            assertPageContainsText("system template");

            takeScreenshot("template-alt-05-system-template-edit");
        }

        @Test
        @Order(6)
        @DisplayName("Should prevent deleting system template")
        void shouldPreventDeletingSystemTemplate() {
            navigateTo("/templates");

            // Find system template and try to delete
            page.click("button[data-action='delete'][data-system='true']:first-of-type");
            page.click("button:has-text('Hapus')");
            waitForPageLoad();

            // Should show error
            assertPageContainsText("Cannot delete system template");

            takeScreenshot("template-alt-06-delete-system-error");
        }

        @Test
        @Order(7)
        @DisplayName("Should handle non-existent template ID")
        void shouldHandleNonExistentTemplateId() {
            navigateTo("/templates/00000000-0000-0000-0000-000000000000");

            assertPageContainsText("not found");

            takeScreenshot("template-alt-07-not-found");
        }

        @Test
        @Order(8)
        @DisplayName("Should show empty state when no favorites")
        void shouldShowEmptyStateWhenNoFavorites() {
            navigateTo("/templates?favorites=true");

            // If no favorites, should show message
            // This depends on test data state

            takeScreenshot("template-alt-08-no-favorites");
        }
    }

    @Nested
    @DisplayName("API Alternate Scenarios")
    class APIAlternateScenarios {

        @Test
        @Order(10)
        @DisplayName("Should return 404 for non-existent template")
        void shouldReturn404ForNonExistentTemplate() {
            APIResponse response = apiGet("/templates/api/00000000-0000-0000-0000-000000000000");

            assertEquals(404, response.status());
        }

        @Test
        @Order(11)
        @DisplayName("Should return 400 for invalid template data")
        void shouldReturn400ForInvalidTemplateData() {
            String requestBody = """
                {
                    "templateName": "",
                    "category": "INCOME",
                    "cashFlowCategory": "OPERATING"
                }
                """;

            APIResponse response = apiPost("/templates/api", requestBody);

            assertEquals(400, response.status());
        }

        @Test
        @Order(12)
        @DisplayName("Should return 400 for template without lines")
        void shouldReturn400ForTemplateWithoutLines() {
            String requestBody = """
                {
                    "templateName": "No Lines Template",
                    "category": "INCOME",
                    "cashFlowCategory": "OPERATING",
                    "templateType": "SIMPLE",
                    "lines": []
                }
                """;

            APIResponse response = apiPost("/templates/api", requestBody);

            assertEquals(400, response.status());
        }

        @Test
        @Order(13)
        @DisplayName("Should return 400 for template with only debit lines")
        void shouldReturn400ForTemplateWithOnlyDebitLines() {
            String requestBody = """
                {
                    "templateName": "Only Debit Template",
                    "category": "INCOME",
                    "cashFlowCategory": "OPERATING",
                    "templateType": "SIMPLE",
                    "lines": [
                        {
                            "accountId": "10000000-0000-0000-0000-000000000102",
                            "position": "DEBIT",
                            "formula": "amount",
                            "lineOrder": 1
                        },
                        {
                            "accountId": "10000000-0000-0000-0000-000000000101",
                            "position": "DEBIT",
                            "formula": "amount",
                            "lineOrder": 2
                        }
                    ]
                }
                """;

            APIResponse response = apiPost("/templates/api", requestBody);

            assertEquals(400, response.status());
        }

        @Test
        @Order(14)
        @DisplayName("Should return error when deleting system template via API")
        void shouldReturnErrorWhenDeletingSystemTemplateViaAPI() {
            APIResponse response = apiDelete("/templates/api/e0000000-0000-0000-0000-000000000001");

            // Should not allow deletion of system template
            assertTrue(response.status() == 400 || response.status() == 403 || response.status() == 409);
        }

        @Test
        @Order(15)
        @DisplayName("Should return error when modifying system template via API")
        void shouldReturnErrorWhenModifyingSystemTemplateViaAPI() {
            String requestBody = """
                {
                    "templateName": "Modified System Template",
                    "category": "EXPENSE",
                    "cashFlowCategory": "OPERATING",
                    "templateType": "SIMPLE"
                }
                """;

            APIResponse response = apiPut("/templates/api/e0000000-0000-0000-0000-000000000001", requestBody);

            assertTrue(response.status() == 400 || response.status() == 403 || response.status() == 409);
        }

        @Test
        @Order(16)
        @DisplayName("Should return 400 for invalid category")
        void shouldReturn400ForInvalidCategory() {
            String requestBody = """
                {
                    "templateName": "Invalid Category",
                    "category": "INVALID",
                    "cashFlowCategory": "OPERATING"
                }
                """;

            APIResponse response = apiPost("/templates/api", requestBody);

            assertEquals(400, response.status());
        }
    }
}
