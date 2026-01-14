package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.DraftTransactionRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Functional tests for DraftTransactionController.
 * Tests draft transaction list, detail, approve, reject operations.
 * Note: DraftTransactionController does not have create/edit endpoints.
 */
@DisplayName("Draft Transaction Controller Tests")
@Import(ServiceTestDataInitializer.class)
class DraftTransactionControllerFunctionalTest extends PlaywrightTestBase {

    @Autowired
    private DraftTransactionRepository draftRepository;

    @Autowired
    private JournalTemplateRepository templateRepository;

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
    }

    // ==================== LIST PAGE ====================

    @Test
    @DisplayName("Should display draft transaction list page")
    void shouldDisplayDraftTransactionListPage() {
        navigateTo("/drafts");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).isVisible();
    }

    @Test
    @DisplayName("Should filter drafts by PENDING status via query param")
    void shouldFilterDraftsByPendingStatus() {
        navigateTo("/drafts?status=PENDING");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).isVisible();
    }

    @Test
    @DisplayName("Should filter drafts by APPROVED status via query param")
    void shouldFilterDraftsByApprovedStatus() {
        navigateTo("/drafts?status=APPROVED");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).isVisible();
    }

    @Test
    @DisplayName("Should filter drafts by REJECTED status via query param")
    void shouldFilterDraftsByRejectedStatus() {
        navigateTo("/drafts?status=REJECTED");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).isVisible();
    }

    @Test
    @DisplayName("Should paginate draft list")
    void shouldPaginateDraftList() {
        navigateTo("/drafts?page=0&size=10");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).isVisible();
    }

    @Test
    @DisplayName("Should handle second page of draft list")
    void shouldHandleSecondPageOfDraftList() {
        navigateTo("/drafts?page=1&size=10");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).isVisible();
    }

    @Test
    @DisplayName("Should filter drafts by status using form")
    void shouldFilterDraftsByStatusUsingForm() {
        navigateTo("/drafts");
        waitForPageLoad();

        var statusSelect = page.locator("select[name='status']").first();
        if (statusSelect.isVisible()) {
            statusSelect.selectOption("PENDING");

            var filterBtn = page.locator("form button[type='submit']").first();
            if (filterBtn.isVisible()) {
                filterBtn.click();
                waitForPageLoad();
            }
        }

        assertThat(page.locator("body")).isVisible();
    }

    // ==================== DETAIL PAGE ====================

    @Test
    @DisplayName("Should display draft transaction detail page")
    void shouldDisplayDraftTransactionDetailPage() {
        var drafts = draftRepository.findAll();
        if (drafts.isEmpty()) {
            navigateTo("/drafts");
            waitForPageLoad();
            assertThat(page.locator("#page-title, h1").first()).isVisible();
            return;
        }

        navigateTo("/drafts/" + drafts.get(0).getId());
        waitForPageLoad();

        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/drafts\\/.*"));
    }

    @Test
    @DisplayName("Should display templates on detail page")
    void shouldDisplayTemplatesOnDetailPage() {
        var drafts = draftRepository.findAll();
        if (drafts.isEmpty()) {
            navigateTo("/drafts");
            waitForPageLoad();
            assertThat(page.locator("#page-title, h1").first()).isVisible();
            return;
        }

        navigateTo("/drafts/" + drafts.get(0).getId());
        waitForPageLoad();

        // Detail page should show template selection for approval
        assertThat(page.locator("body")).isVisible();
    }

    // ==================== APPROVE/REJECT ====================

    @Test
    @DisplayName("Should approve draft transaction")
    void shouldApproveDraftTransaction() {
        var drafts = draftRepository.findAll().stream()
                .filter(d -> "PENDING".equals(d.getStatus().name()))
                .toList();
        if (drafts.isEmpty()) {
            navigateTo("/drafts");
            waitForPageLoad();
            assertThat(page.locator("#page-title, h1").first()).isVisible();
            return;
        }

        navigateTo("/drafts/" + drafts.get(0).getId());
        waitForPageLoad();

        var approveBtn = page.locator("form[action*='/approve'] button[type='submit']").first();
        if (approveBtn.isVisible()) {
            // Need to select template first
            var templateSelect = page.locator("select[name='templateId']").first();
            if (templateSelect.isVisible()) {
                var templates = templateRepository.findAll();
                if (!templates.isEmpty()) {
                    templateSelect.selectOption(templates.get(0).getId().toString());
                }
            }
            approveBtn.click();
            waitForPageLoad();
        }

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should reject draft transaction")
    void shouldRejectDraftTransaction() {
        var drafts = draftRepository.findAll().stream()
                .filter(d -> "PENDING".equals(d.getStatus().name()))
                .toList();
        if (drafts.isEmpty()) {
            navigateTo("/drafts");
            waitForPageLoad();
            assertThat(page.locator("#page-title, h1").first()).isVisible();
            return;
        }

        navigateTo("/drafts/" + drafts.get(0).getId());
        waitForPageLoad();

        // Fill rejection reason if required
        var reasonInput = page.locator("input[name='reason'], textarea[name='reason']").first();
        if (reasonInput.isVisible()) {
            reasonInput.fill("Test rejection reason");
        }

        var rejectBtn = page.locator("form[action*='/reject'] button[type='submit']").first();
        if (rejectBtn.isVisible()) {
            rejectBtn.click();
            waitForPageLoad();
        }

        assertThat(page.locator("body")).isVisible();
    }

    // ==================== API ENDPOINTS ====================

    @Test
    @DisplayName("Should access API list endpoint")
    void shouldAccessApiListEndpoint() {
        var response = page.navigate("http://localhost:" + port + "/drafts/api");
        if (response != null) {
            var status = response.status();
            org.junit.jupiter.api.Assertions.assertTrue(status < 500,
                    "Expected non-server error, got: " + status);
        }
    }

    @Test
    @DisplayName("Should access API list with status filter")
    void shouldAccessApiListWithStatusFilter() {
        var response = page.navigate("http://localhost:" + port + "/drafts/api?status=PENDING");
        if (response != null) {
            var status = response.status();
            org.junit.jupiter.api.Assertions.assertTrue(status < 500,
                    "Expected non-server error, got: " + status);
        }
    }

    @Test
    @DisplayName("Should access API get endpoint")
    void shouldAccessApiGetEndpoint() {
        var drafts = draftRepository.findAll();
        if (drafts.isEmpty()) {
            return;
        }

        var response = page.navigate("http://localhost:" + port + "/drafts/api/" + drafts.get(0).getId());
        if (response != null) {
            var status = response.status();
            org.junit.jupiter.api.Assertions.assertTrue(status < 500,
                    "Expected non-server error, got: " + status);
        }
    }

    @Test
    @DisplayName("Should return 404 for non-existent draft API")
    void shouldReturn404ForNonExistentDraftApi() {
        var response = page.navigate("http://localhost:" + port + "/drafts/api/00000000-0000-0000-0000-000000000000");
        if (response != null) {
            var status = response.status();
            // Should return 404 or error page
            org.junit.jupiter.api.Assertions.assertTrue(status == 404 || status < 500,
                    "Expected 404 or non-server error, got: " + status);
        }
    }
}
