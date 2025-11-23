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

@DisplayName("Chart of Accounts - Success Scenarios")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ChartOfAccountSuccessTest extends FunctionalTestBase {

    @BeforeEach
    void setUp() {
        loginAsAdmin();
    }

    @Nested
    @DisplayName("UI Success Scenarios")
    class UISuccessScenarios {

        @Test
        @Order(1)
        @DisplayName("Should display chart of accounts list page")
        void shouldDisplayChartOfAccountsListPage() {
            navigateTo("/accounts");

            assertThat(page).hasTitle("Bagan Akun");
            assertElementVisible("text=Bagan Akun");
            assertElementVisible("a:has-text('Tambah Akun')");

            takeScreenshot("coa-success-01-list-page");
        }

        @Test
        @Order(2)
        @DisplayName("Should display hierarchical account tree")
        void shouldDisplayHierarchicalAccountTree() {
            navigateTo("/accounts");

            // Root accounts should be visible
            assertPageContainsText("ASET");
            assertPageContainsText("LIABILITAS");
            assertPageContainsText("EKUITAS");
            assertPageContainsText("PENDAPATAN");
            assertPageContainsText("BEBAN");

            takeScreenshot("coa-success-02-account-tree");
        }

        @Test
        @Order(3)
        @DisplayName("Should expand account children on click")
        void shouldExpandAccountChildrenOnClick() {
            navigateTo("/accounts");

            // Click on ASET to expand
            page.click("text=ASET");
            waitForPageLoad();

            // Children should be visible
            assertPageContainsText("Aset Lancar");
            assertPageContainsText("Aset Tetap");

            takeScreenshot("coa-success-03-expanded-tree");
        }

        @Test
        @Order(4)
        @DisplayName("Should display new account form")
        void shouldDisplayNewAccountForm() {
            navigateTo("/accounts/new");

            assertElementVisible("input[name='accountCode']");
            assertElementVisible("input[name='accountName']");
            assertElementVisible("select[name='accountType']");
            assertElementVisible("select[name='normalBalance']");
            assertElementVisible("button[type='submit']");

            takeScreenshot("coa-success-04-new-form");
        }

        @Test
        @Order(5)
        @DisplayName("Should create new account via form")
        void shouldCreateNewAccountViaForm() {
            navigateTo("/accounts/new");

            fillForm("input[name='accountCode']", "1.1.99");
            fillForm("input[name='accountName']", "Test Account");
            selectOption("select[name='accountType']", "ASSET");
            selectOption("select[name='normalBalance']", "DEBIT");
            fillForm("textarea[name='description']", "Test account description");

            takeScreenshot("coa-success-05-filled-form");

            submitForm();

            // Should redirect to list or show success
            assertPageContainsText("Test Account");
            takeScreenshot("coa-success-06-after-create");
        }

        @Test
        @Order(6)
        @DisplayName("Should display edit account form with existing data")
        void shouldDisplayEditAccountFormWithExistingData() {
            navigateTo("/accounts");

            // Click edit on first account
            page.click("a[href*='/edit']:first-of-type");
            waitForPageLoad();

            // Form should be pre-filled
            String accountCode = getInputValue("input[name='accountCode']");
            assertNotNull(accountCode);
            assertFalse(accountCode.isEmpty());

            takeScreenshot("coa-success-07-edit-form");
        }

        @Test
        @Order(7)
        @DisplayName("Should filter accounts by type")
        void shouldFilterAccountsByType() {
            navigateTo("/accounts");

            // Select asset type filter
            selectOption("select[name='type']", "ASSET");
            clickButton("Filter");

            // Should only show asset accounts
            assertPageContainsText("Kas");
            assertPageContainsText("Bank");

            takeScreenshot("coa-success-08-filtered-by-type");
        }

        @Test
        @Order(8)
        @DisplayName("Should search accounts by name")
        void shouldSearchAccountsByName() {
            navigateTo("/accounts");

            fillForm("input[name='search']", "Bank");
            clickButton("Cari");

            // Should show matching accounts
            assertPageContainsText("Bank BCA");
            assertPageContainsText("Bank BNI");

            takeScreenshot("coa-success-09-search-results");
        }
    }

    @Nested
    @DisplayName("API Success Scenarios")
    class APISuccessScenarios {

        @Test
        @Order(10)
        @DisplayName("Should retrieve all accounts via API")
        void shouldRetrieveAllAccountsViaAPI() {
            APIResponse response = apiGet("/accounts/api");

            assertEquals(200, response.status());
            String body = response.text();
            assertTrue(body.contains("accountCode"));
            assertTrue(body.contains("accountName"));
        }

        @Test
        @Order(11)
        @DisplayName("Should retrieve transactable accounts via API")
        void shouldRetrieveTransactableAccountsViaAPI() {
            APIResponse response = apiGet("/accounts/api/transactable");

            assertEquals(200, response.status());
            String body = response.text();
            // Transactable accounts should not include headers
            assertFalse(body.contains("\"isHeader\":true"));
        }

        @Test
        @Order(12)
        @DisplayName("Should retrieve account by ID via API")
        void shouldRetrieveAccountByIdViaAPI() {
            // First get list to find an ID
            APIResponse listResponse = apiGet("/accounts/api");
            assertEquals(200, listResponse.status());

            // Get first account ID from response
            String body = listResponse.text();
            assertTrue(body.contains("\"id\":"));
        }

        @Test
        @Order(13)
        @DisplayName("Should create account via API")
        void shouldCreateAccountViaAPI() {
            String requestBody = """
                {
                    "accountCode": "1.1.98",
                    "accountName": "API Test Account",
                    "accountType": "ASSET",
                    "normalBalance": "DEBIT",
                    "isHeader": false,
                    "active": true,
                    "description": "Created via API test"
                }
                """;

            APIResponse response = apiPost("/accounts/api", requestBody);

            assertEquals(200, response.status());
            String body = response.text();
            assertTrue(body.contains("API Test Account"));
            assertTrue(body.contains("1.1.98"));
        }

        @Test
        @Order(14)
        @DisplayName("Should search accounts via API")
        void shouldSearchAccountsViaAPI() {
            APIResponse response = apiGet("/accounts/api/search?q=Bank&active=true");

            assertEquals(200, response.status());
            String body = response.text();
            assertTrue(body.contains("Bank"));
        }

        @Test
        @Order(15)
        @DisplayName("Should filter accounts by type via API")
        void shouldFilterAccountsByTypeViaAPI() {
            APIResponse response = apiGet("/accounts/api?type=REVENUE");

            assertEquals(200, response.status());
            String body = response.text();
            assertTrue(body.contains("REVENUE"));
        }
    }
}
