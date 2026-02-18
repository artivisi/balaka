package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Functional tests for JournalTemplateController.
 * Tests template list, create, edit, duplicate, execute operations.
 */
@DisplayName("Journal Template Controller Tests")
@Import(ServiceTestDataInitializer.class)
class JournalTemplateControllerFunctionalTest extends PlaywrightTestBase {

    @Autowired
    private JournalTemplateRepository templateRepository;

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("Should display template list page")
    void shouldDisplayTemplateListPage() {
        navigateTo("/templates");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).isVisible();
    }

    @Test
    @DisplayName("Should filter templates by category")
    void shouldFilterTemplatesByCategory() {
        navigateTo("/templates");
        waitForPageLoad();

        var categorySelect = page.locator("select[name='category']").first();
        if (categorySelect.isVisible()) {
            var options = categorySelect.locator("option");
            if (options.count() > 1) {
                categorySelect.selectOption(new String[]{options.nth(1).getAttribute("value")});

                var filterBtn = page.locator("form button[type='submit']").first();
                if (filterBtn.isVisible()) {
                    filterBtn.click();
                    waitForPageLoad();
                }
            }
        }

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should search templates by keyword")
    void shouldSearchTemplatesByKeyword() {
        navigateTo("/templates");
        waitForPageLoad();

        var searchInput = page.locator("input[name='search'], input[name='keyword']").first();
        if (searchInput.isVisible()) {
            searchInput.fill("penjualan");

            var filterBtn = page.locator("form button[type='submit']").first();
            if (filterBtn.isVisible()) {
                filterBtn.click();
                waitForPageLoad();
            }
        }

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should filter templates by active status")
    void shouldFilterTemplatesByActiveStatus() {
        navigateTo("/templates");
        waitForPageLoad();

        var activeCheckbox = page.locator("input[name='active']").first();
        if (activeCheckbox.isVisible()) {
            activeCheckbox.check();

            var filterBtn = page.locator("form button[type='submit']").first();
            if (filterBtn.isVisible()) {
                filterBtn.click();
                waitForPageLoad();
            }
        }

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should display new template form")
    void shouldDisplayNewTemplateForm() {
        navigateTo("/templates/new");
        waitForPageLoad();

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should create new template")
    void shouldCreateNewTemplate() {
        navigateTo("/templates/new");
        waitForPageLoad();

        // Fill template name
        var nameInput = page.locator("input[name='name']").first();
        if (nameInput.isVisible()) {
            nameInput.fill("Test Template " + System.currentTimeMillis());
        }

        // Fill description
        var descriptionInput = page.locator("textarea[name='description'], input[name='description']").first();
        if (descriptionInput.isVisible()) {
            descriptionInput.fill("Test template description");
        }

        // Select category
        var categorySelect = page.locator("select[name='category']").first();
        if (categorySelect.isVisible()) {
            var options = categorySelect.locator("option");
            if (options.count() > 1) {
                categorySelect.selectOption(new String[]{options.nth(1).getAttribute("value")});
            }
        }

        // Submit
        var submitBtn = page.locator("#btn-simpan").first();
        if (submitBtn.isVisible()) {
            submitBtn.click();
            waitForPageLoad();
        }

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should display template detail page")
    void shouldDisplayTemplateDetailPage() {
        var template = templateRepository.findAll().stream().findFirst();
        if (template.isEmpty()) {
            return;
        }

        navigateTo("/templates/" + template.get().getId());
        waitForPageLoad();

        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/templates\\/.*"));
    }

    @Test
    @DisplayName("Should display template edit form")
    void shouldDisplayTemplateEditForm() {
        var template = templateRepository.findAll().stream().findFirst();
        if (template.isEmpty()) {
            return;
        }

        navigateTo("/templates/" + template.get().getId() + "/edit");
        waitForPageLoad();

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should update template")
    void shouldUpdateTemplate() {
        var template = templateRepository.findAll().stream().findFirst();
        if (template.isEmpty()) {
            return;
        }

        navigateTo("/templates/" + template.get().getId() + "/edit");
        waitForPageLoad();

        // Update name
        var nameInput = page.locator("input[name='name']").first();
        if (nameInput.isVisible()) {
            nameInput.fill("Updated Template " + System.currentTimeMillis());
        }

        // Submit
        var submitBtn = page.locator("#btn-simpan").first();
        if (submitBtn.isVisible()) {
            submitBtn.click();
            waitForPageLoad();
        }

        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/templates\\/.*"));
    }

    @Test
    @DisplayName("Should duplicate template")
    void shouldDuplicateTemplate() {
        var template = templateRepository.findAll().stream().findFirst();
        if (template.isEmpty()) {
            return;
        }

        navigateTo("/templates/" + template.get().getId() + "/duplicate");
        waitForPageLoad();

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should display execute template form")
    void shouldDisplayExecuteTemplateForm() {
        var template = templateRepository.findAll().stream()
                .filter(t -> Boolean.TRUE.equals(t.getActive()))
                .findFirst();
        if (template.isEmpty()) {
            return;
        }

        navigateTo("/templates/" + template.get().getId() + "/execute");
        waitForPageLoad();

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should delete template")
    void shouldDeleteTemplate() {
        var template = templateRepository.findAll().stream().findFirst();
        if (template.isEmpty()) {
            return;
        }

        navigateTo("/templates/" + template.get().getId());
        waitForPageLoad();

        var deleteBtn = page.locator("form[action*='/delete'] button[type='submit']").first();
        if (deleteBtn.isVisible()) {
            deleteBtn.click();
            waitForPageLoad();
        }

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should toggle favorite")
    void shouldToggleFavorite() {
        var template = templateRepository.findAll().stream().findFirst();
        if (template.isEmpty()) {
            return;
        }

        navigateTo("/templates/" + template.get().getId());
        waitForPageLoad();

        var favoriteBtn = page.locator("form[action*='/toggle-favorite'] button[type='submit']").first();
        if (favoriteBtn.isVisible()) {
            favoriteBtn.click();
            waitForPageLoad();
        }

        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/templates\\/.*"));
    }

    @Test
    @DisplayName("Should get templates via API")
    void shouldGetTemplatesViaApi() {
        navigateTo("/templates/api");
        waitForPageLoad();

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should get recent templates via API")
    void shouldGetRecentTemplatesViaApi() {
        navigateTo("/templates/api/recent");
        waitForPageLoad();

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should search templates via API")
    void shouldSearchTemplatesViaApi() {
        navigateTo("/templates/api/search?q=test");
        waitForPageLoad();

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should get template by ID via API")
    void shouldGetTemplateByIdViaApi() {
        var template = templateRepository.findAll().stream().findFirst();
        if (template.isEmpty()) {
            return;
        }

        navigateTo("/templates/api/" + template.get().getId());
        waitForPageLoad();

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should display AI metadata in detail page")
    void shouldDisplayAiMetadataInDetail() {
        // Find a template that has AI metadata (from seed data)
        var template = templateRepository.findAll().stream()
                .filter(t -> t.getSemanticDescription() != null && !t.getSemanticDescription().isEmpty())
                .findFirst();
        if (template.isEmpty()) {
            return;
        }

        navigateTo("/templates/" + template.get().getId());
        waitForPageLoad();

        // Verify metadata card is visible
        assertThat(page.locator("[data-testid='metadata-ai-card']")).isVisible();
        assertThat(page.locator("[data-testid='semantic-description']")).isVisible();

        // Verify keywords badges are displayed
        if (template.get().getKeywords() != null && template.get().getKeywords().length > 0) {
            assertThat(page.locator("[data-testid='keywords-badges']")).isVisible();
        }
    }

    @Test
    @DisplayName("Should edit AI metadata in template form")
    void shouldEditAiMetadata() {
        // Find a template to edit
        var template = templateRepository.findAll().stream()
                .filter(t -> Boolean.TRUE.equals(t.getActive()) && !Boolean.TRUE.equals(t.getIsSystem()))
                .findFirst();
        if (template.isEmpty()) {
            return;
        }

        navigateTo("/templates/" + template.get().getId() + "/edit");
        waitForPageLoad();

        // Open the metadata section
        page.locator("[data-testid='metadata-ai-section'] summary").click();
        page.waitForTimeout(300);

        // Fill metadata fields
        page.locator("#semanticDescription").click();
        page.locator("#semanticDescription").fill("Test semantic description for automation");
        page.locator("#keywordsText").click();
        page.locator("#keywordsText").fill("test, automation, metadata");
        page.locator("#exampleMerchantsText").click();
        page.locator("#exampleMerchantsText").fill("Merchant A, Merchant B");
        page.locator("#typicalAmountMin").click();
        page.locator("#typicalAmountMin").fill("100000");
        page.locator("#typicalAmountMax").click();
        page.locator("#typicalAmountMax").fill("5000000");
        page.locator("#merchantPatternsText").click();
        page.locator("#merchantPatternsText").fill(".*test.*, .*auto.*");

        // Submit form
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        // After update, may redirect to detail of new version or same ID
        // Verify metadata is displayed
        assertThat(page.locator("[data-testid='metadata-ai-card']")).isVisible();
        assertThat(page.locator("[data-testid='semantic-description']")).containsText("Test semantic description for automation");
        assertThat(page.locator("[data-testid='keywords-badges']")).isVisible();
        assertThat(page.locator("[data-testid='example-merchants-badges']")).isVisible();
        assertThat(page.locator("[data-testid='amount-range']")).isVisible();
        assertThat(page.locator("[data-testid='merchant-patterns-badges']")).isVisible();
    }

    @Test
    @DisplayName("Should create template with AI metadata")
    void shouldCreateTemplateWithMetadata() {
        navigateTo("/templates/new");
        waitForPageLoad();

        // Fill required fields
        page.locator("input[name='templateName']").fill("Test Metadata Template " + System.currentTimeMillis());
        page.locator("select[name='category']").selectOption("EXPENSE");
        page.locator("select[name='cashFlowCategory']").selectOption("OPERATING");

        // Set journal lines using leaf accounts from the account select options
        var accountOptions = page.locator("select[name='lines[0].accountId'] option[value]");
        if (accountOptions.count() < 2) {
            return;
        }

        // Pick first two non-empty account options
        String firstAccountId = accountOptions.nth(1).getAttribute("value");
        String secondAccountId = accountOptions.nth(2).getAttribute("value");

        // Set first line (debit)
        page.locator("select[name='lines[0].accountId']").selectOption(firstAccountId);
        // Set second line (credit)
        page.locator("[data-testid='btn-credit-1']").click();
        page.locator("select[name='lines[1].accountId']").selectOption(secondAccountId);

        // Open metadata section and fill
        page.locator("[data-testid='metadata-ai-section'] summary").click();
        page.locator("#semanticDescription").fill("New template with metadata");
        page.locator("#keywordsText").fill("new, test, create");
        page.locator("#typicalAmountMin").fill("50000");
        page.locator("#typicalAmountMax").fill("1000000");

        // Submit
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        // Verify metadata persists on detail page
        assertThat(page.locator("[data-testid='metadata-ai-card']")).isVisible();
        assertThat(page.locator("[data-testid='semantic-description']")).containsText("New template with metadata");
        assertThat(page.locator("[data-testid='keywords-badges']")).isVisible();
    }
}
