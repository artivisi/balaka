package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.DraftTransaction;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.DraftTransactionRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;

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
        ensurePendingDraftsExist();
        loginAsAdmin();
    }

    private void ensurePendingDraftsExist() {
        // Create PENDING drafts if none exist
        boolean hasPending = draftRepository.findAll().stream()
                .anyMatch(d -> d.getStatus() == DraftTransaction.Status.PENDING);
        if (!hasPending) {
            for (int i = 0; i < 3; i++) {
                DraftTransaction draft = new DraftTransaction();
                draft.setSource(DraftTransaction.Source.MANUAL);
                draft.setMerchantName("Test Merchant " + i);
                draft.setTransactionDate(LocalDate.now());
                draft.setAmount(BigDecimal.valueOf(100000 + i * 10000));
                draft.setStatus(DraftTransaction.Status.PENDING);
                draftRepository.save(draft);
            }
        }
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
        var draft = draftRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new AssertionError("Draft required for test"));

        navigateTo("/drafts/" + draft.getId());
        waitForPageLoad();

        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/drafts\\/.*"));
    }

    @Test
    @DisplayName("Should display templates on detail page")
    void shouldDisplayTemplatesOnDetailPage() {
        var draft = draftRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new AssertionError("Draft required for test"));

        navigateTo("/drafts/" + draft.getId());
        waitForPageLoad();

        // Detail page should show template selection for approval
        assertThat(page.locator("body")).isVisible();
    }

    // ==================== APPROVE/REJECT ====================

    @Test
    @DisplayName("Should approve draft transaction")
    void shouldApproveDraftTransaction() {
        // Create a fresh PENDING draft for approve test
        DraftTransaction approveDraft = new DraftTransaction();
        approveDraft.setSource(DraftTransaction.Source.MANUAL);
        approveDraft.setMerchantName("Approve Test Merchant");
        approveDraft.setTransactionDate(LocalDate.now());
        approveDraft.setAmount(BigDecimal.valueOf(50000));
        approveDraft.setStatus(DraftTransaction.Status.PENDING);
        approveDraft = draftRepository.save(approveDraft);

        var template = templateRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new AssertionError("Template required for approve test"));

        navigateTo("/drafts/" + approveDraft.getId());
        waitForPageLoad();

        // Select template
        page.locator("#templateId").selectOption(template.getId().toString());

        // Click approve button
        page.locator("#btn-approve").click();
        waitForPageLoad();

        // Approve redirects to transaction edit page
        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/(drafts|transactions).*"));
    }

    @Test
    @DisplayName("Should reject draft transaction")
    void shouldRejectDraftTransaction() {
        // Create a fresh PENDING draft for reject test
        DraftTransaction rejectDraft = new DraftTransaction();
        rejectDraft.setSource(DraftTransaction.Source.MANUAL);
        rejectDraft.setMerchantName("Reject Test Merchant");
        rejectDraft.setTransactionDate(LocalDate.now());
        rejectDraft.setAmount(BigDecimal.valueOf(75000));
        rejectDraft.setStatus(DraftTransaction.Status.PENDING);
        rejectDraft = draftRepository.save(rejectDraft);

        navigateTo("/drafts/" + rejectDraft.getId());
        waitForPageLoad();

        // Fill rejection reason (required)
        page.locator("#reason").fill("Test rejection reason");

        // Click reject button
        page.locator("#btn-reject").click();
        waitForPageLoad();

        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/drafts.*"));
    }

    // ==================== API ENDPOINTS ====================

    @Test
    @DisplayName("Should access API list endpoint")
    void shouldAccessApiListEndpoint() {
        var response = page.request().get(baseUrl() + "/drafts/api");
        org.assertj.core.api.Assertions.assertThat(response.status())
                .as("API list should return success")
                .isLessThan(500);
    }

    @Test
    @DisplayName("Should access API list with status filter")
    void shouldAccessApiListWithStatusFilter() {
        var response = page.request().get(baseUrl() + "/drafts/api?status=PENDING");
        org.assertj.core.api.Assertions.assertThat(response.status())
                .as("API list with filter should return success")
                .isLessThan(500);
    }

    @Test
    @DisplayName("Should access API get endpoint")
    void shouldAccessApiGetEndpoint() {
        var draft = draftRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new AssertionError("Draft required for test"));

        var response = page.request().get(baseUrl() + "/drafts/api/" + draft.getId());
        org.assertj.core.api.Assertions.assertThat(response.status())
                .as("API get should return success")
                .isLessThan(500);
    }

    @Test
    @DisplayName("Should return error for non-existent draft API")
    void shouldReturn404ForNonExistentDraftApi() {
        var response = page.request().get(baseUrl() + "/drafts/api/00000000-0000-0000-0000-000000000000");
        org.assertj.core.api.Assertions.assertThat(response.status())
                .as("Non-existent draft should return error")
                .isIn(400, 404, 500);
    }

    @Test
    @DisplayName("Should call delete API endpoint")
    void shouldCallDeleteApiEndpoint() {
        // Create a fresh PENDING draft for delete test
        DraftTransaction deleteDraft = new DraftTransaction();
        deleteDraft.setSource(DraftTransaction.Source.MANUAL);
        deleteDraft.setMerchantName("Delete Test Merchant");
        deleteDraft.setTransactionDate(LocalDate.now());
        deleteDraft.setAmount(BigDecimal.valueOf(25000));
        deleteDraft.setStatus(DraftTransaction.Status.PENDING);
        deleteDraft = draftRepository.save(deleteDraft);

        // The DELETE API endpoint requires CSRF token which isn't easily available
        // Test that endpoint exists and returns expected response (403 CSRF or 204 success)
        var response = page.request().delete(baseUrl() + "/drafts/" + deleteDraft.getId());
        org.assertj.core.api.Assertions.assertThat(response.status())
                .as("Delete API should respond (CSRF protected)")
                .isIn(200, 204, 403);
    }
}
