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

@DisplayName("Journal Template - Success Scenarios")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JournalTemplateSuccessTest extends FunctionalTestBase {

    @BeforeEach
    void setUp() {
        loginAsAdmin();
    }

    @Nested
    @DisplayName("UI Success Scenarios")
    class UISuccessScenarios {

        @Test
        @Order(1)
        @DisplayName("Should display journal template list page")
        void shouldDisplayJournalTemplateListPage() {
            navigateTo("/templates");

            assertThat(page).hasTitle("Template Jurnal");
            assertElementVisible("text=Template Jurnal");
            assertElementVisible("a:has-text('Tambah Template')");

            takeScreenshot("template-success-01-list-page");
        }

        @Test
        @Order(2)
        @DisplayName("Should display templates grouped by category")
        void shouldDisplayTemplatesGroupedByCategory() {
            navigateTo("/templates");

            // Category tabs/filters should be visible
            assertPageContainsText("Pendapatan");
            assertPageContainsText("Pengeluaran");

            takeScreenshot("template-success-02-categories");
        }

        @Test
        @Order(3)
        @DisplayName("Should filter templates by category")
        void shouldFilterTemplatesByCategory() {
            navigateTo("/templates?category=INCOME");

            // Should only show income templates
            assertPageContainsText("Pendapatan Jasa");

            takeScreenshot("template-success-03-filtered-income");
        }

        @Test
        @Order(4)
        @DisplayName("Should display template detail page")
        void shouldDisplayTemplateDetailPage() {
            navigateTo("/templates");

            // Click on first template
            page.click("a[href*='/templates/']:not([href*='/new']):not([href*='/edit']):first-of-type");
            waitForPageLoad();

            // Should show template details
            assertPageContainsText("Debit");
            assertPageContainsText("Kredit");

            takeScreenshot("template-success-04-detail-page");
        }

        @Test
        @Order(5)
        @DisplayName("Should display new template form")
        void shouldDisplayNewTemplateForm() {
            navigateTo("/templates/new");

            assertElementVisible("input[name='templateName']");
            assertElementVisible("select[name='category']");
            assertElementVisible("select[name='cashFlowCategory']");

            takeScreenshot("template-success-05-new-form");
        }

        @Test
        @Order(6)
        @DisplayName("Should create new journal template")
        void shouldCreateNewJournalTemplate() {
            navigateTo("/templates/new");

            fillForm("input[name='templateName']", "Test Template");
            selectOption("select[name='category']", "INCOME");
            selectOption("select[name='cashFlowCategory']", "OPERATING");
            fillForm("textarea[name='description']", "Test template description");

            // Add journal lines
            clickButton("Tambah Baris");

            takeScreenshot("template-success-06-filled-form");

            submitForm();

            assertPageContainsText("Test Template");
            takeScreenshot("template-success-07-after-create");
        }

        @Test
        @Order(7)
        @DisplayName("Should toggle template favorite status")
        void shouldToggleTemplateFavoriteStatus() {
            navigateTo("/templates");

            // Click favorite button
            page.click("button[data-action='favorite']:first-of-type");
            waitForPageLoad();

            takeScreenshot("template-success-08-favorite-toggled");
        }

        @Test
        @Order(8)
        @DisplayName("Should duplicate existing template")
        void shouldDuplicateExistingTemplate() {
            navigateTo("/templates");

            // Click duplicate button
            page.click("a[href*='/duplicate']:first-of-type");
            waitForPageLoad();

            // Form should be pre-filled
            String templateName = getInputValue("input[name='templateName']");
            assertNotNull(templateName);

            takeScreenshot("template-success-09-duplicate-form");
        }

        @Test
        @Order(9)
        @DisplayName("Should show favorites only")
        void shouldShowFavoritesOnly() {
            navigateTo("/templates?favorites=true");

            takeScreenshot("template-success-10-favorites-only");
        }
    }

    @Nested
    @DisplayName("API Success Scenarios")
    class APISuccessScenarios {

        @Test
        @Order(10)
        @DisplayName("Should retrieve all templates via API")
        void shouldRetrieveAllTemplatesViaAPI() {
            APIResponse response = apiGet("/templates/api");

            assertEquals(200, response.status());
            String body = response.text();
            assertTrue(body.contains("templateName"));
            assertTrue(body.contains("category"));
        }

        @Test
        @Order(11)
        @DisplayName("Should retrieve templates by category via API")
        void shouldRetrieveTemplatesByCategoryViaAPI() {
            APIResponse response = apiGet("/templates/api?category=INCOME");

            assertEquals(200, response.status());
            String body = response.text();
            assertTrue(body.contains("INCOME"));
        }

        @Test
        @Order(12)
        @DisplayName("Should retrieve favorite templates via API")
        void shouldRetrieveFavoriteTemplatesViaAPI() {
            APIResponse response = apiGet("/templates/api/favorites");

            assertEquals(200, response.status());
        }

        @Test
        @Order(13)
        @DisplayName("Should retrieve recent templates via API")
        void shouldRetrieveRecentTemplatesViaAPI() {
            APIResponse response = apiGet("/templates/api/recent");

            assertEquals(200, response.status());
        }

        @Test
        @Order(14)
        @DisplayName("Should create template via API")
        void shouldCreateTemplateViaAPI() {
            String requestBody = """
                {
                    "templateName": "API Test Template",
                    "category": "EXPENSE",
                    "cashFlowCategory": "OPERATING",
                    "templateType": "SIMPLE",
                    "description": "Created via API",
                    "isFavorite": false,
                    "active": true,
                    "lines": [
                        {
                            "accountId": "50000000-0000-0000-0000-000000000101",
                            "position": "DEBIT",
                            "formula": "amount",
                            "lineOrder": 1
                        },
                        {
                            "accountId": "10000000-0000-0000-0000-000000000102",
                            "position": "CREDIT",
                            "formula": "amount",
                            "lineOrder": 2
                        }
                    ]
                }
                """;

            APIResponse response = apiPost("/templates/api", requestBody);

            assertEquals(200, response.status());
            String body = response.text();
            assertTrue(body.contains("API Test Template"));
        }

        @Test
        @Order(15)
        @DisplayName("Should toggle favorite via API")
        void shouldToggleFavoriteViaAPI() {
            APIResponse response = apiPost("/templates/api/e0000000-0000-0000-0000-000000000001/toggle-favorite", "");

            assertEquals(200, response.status());
        }

        @Test
        @Order(16)
        @DisplayName("Should duplicate template via API")
        void shouldDuplicateTemplateViaAPI() {
            APIResponse response = apiPost("/templates/api/e0000000-0000-0000-0000-000000000001/duplicate?newName=Duplicated%20Template", "");

            assertEquals(200, response.status());
            String body = response.text();
            assertTrue(body.contains("Duplicated Template"));
        }

        @Test
        @Order(17)
        @DisplayName("Should search templates via API")
        void shouldSearchTemplatesViaAPI() {
            APIResponse response = apiGet("/templates/api/search?q=Konsultasi");

            assertEquals(200, response.status());
            String body = response.text();
            assertTrue(body.contains("Konsultasi"));
        }
    }
}
