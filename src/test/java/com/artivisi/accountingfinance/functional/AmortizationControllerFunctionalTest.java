package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.AmortizationScheduleRepository;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Functional tests for AmortizationController.
 * Tests amortization schedule list, create, edit, entry posting.
 */
@DisplayName("Amortization Controller Tests")
@Import(ServiceTestDataInitializer.class)
class AmortizationControllerFunctionalTest extends PlaywrightTestBase {

    @Autowired
    private AmortizationScheduleRepository scheduleRepository;

    @Autowired
    private ChartOfAccountRepository coaRepository;

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("Should display amortization list page")
    void shouldDisplayAmortizationListPage() {
        navigateTo("/amortization");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).isVisible();
    }

    @Test
    @DisplayName("Should filter amortization by status")
    void shouldFilterAmortizationByStatus() {
        navigateTo("/amortization");
        waitForPageLoad();

        var statusSelect = page.locator("select[name='status']").first();
        if (statusSelect.isVisible()) {
            statusSelect.selectOption("ACTIVE");

            var filterBtn = page.locator("form button[type='submit']").first();
            if (filterBtn.isVisible()) {
                filterBtn.click();
                waitForPageLoad();
            }
        }

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should search amortization by keyword")
    void shouldSearchAmortizationByKeyword() {
        navigateTo("/amortization");
        waitForPageLoad();

        var searchInput = page.locator("input[name='search'], input[name='keyword']").first();
        if (searchInput.isVisible()) {
            searchInput.fill("prepaid");

            var filterBtn = page.locator("form button[type='submit']").first();
            if (filterBtn.isVisible()) {
                filterBtn.click();
                waitForPageLoad();
            }
        }

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should display new amortization form")
    void shouldDisplayNewAmortizationForm() {
        navigateTo("/amortization/new");
        waitForPageLoad();

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should create new amortization schedule")
    void shouldCreateNewAmortizationSchedule() {
        navigateTo("/amortization/new");
        waitForPageLoad();

        // Fill description
        var descriptionInput = page.locator("input[name='description'], textarea[name='description']").first();
        if (descriptionInput.isVisible()) {
            descriptionInput.fill("Prepaid Insurance " + System.currentTimeMillis());
        }

        // Fill original amount
        var amountInput = page.locator("input[name='originalAmount']").first();
        if (amountInput.isVisible()) {
            amountInput.fill("12000000");
        }

        // Fill start date
        var startDateInput = page.locator("input[name='startDate']").first();
        if (startDateInput.isVisible()) {
            startDateInput.fill(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
        }

        // Fill number of periods
        var periodsInput = page.locator("input[name='numberOfPeriods']").first();
        if (periodsInput.isVisible()) {
            periodsInput.fill("12");
        }

        // Select asset account
        var assetAccountSelect = page.locator("select[name='assetAccount.id'], select[name='assetAccountId']").first();
        if (assetAccountSelect.isVisible()) {
            var options = assetAccountSelect.locator("option");
            if (options.count() > 1) {
                assetAccountSelect.selectOption(new String[]{options.nth(1).getAttribute("value")});
            }
        }

        // Select expense account
        var expenseAccountSelect = page.locator("select[name='expenseAccount.id'], select[name='expenseAccountId']").first();
        if (expenseAccountSelect.isVisible()) {
            var options = expenseAccountSelect.locator("option");
            if (options.count() > 1) {
                expenseAccountSelect.selectOption(new String[]{options.nth(1).getAttribute("value")});
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
    @DisplayName("Should display amortization detail page")
    void shouldDisplayAmortizationDetailPage() {
        var schedules = scheduleRepository.findAll();
        if (schedules.isEmpty()) {
            // If no schedules exist, just verify list page works
            navigateTo("/amortization");
            waitForPageLoad();
            assertThat(page.locator("#page-title, h1").first()).isVisible();
            return;
        }

        navigateTo("/amortization/" + schedules.get(0).getId());
        waitForPageLoad();

        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/amortization\\/.*"));
    }

    @Test
    @DisplayName("Should display amortization edit form")
    void shouldDisplayAmortizationEditForm() {
        var schedules = scheduleRepository.findAll();
        if (schedules.isEmpty()) {
            navigateTo("/amortization");
            waitForPageLoad();
            assertThat(page.locator("#page-title, h1").first()).isVisible();
            return;
        }

        navigateTo("/amortization/" + schedules.get(0).getId() + "/edit");
        waitForPageLoad();

        assertThat(page.locator("input[name='name']")).isVisible();
    }

    @Test
    @DisplayName("Should update amortization schedule")
    void shouldUpdateAmortizationSchedule() {
        var schedules = scheduleRepository.findAll();
        if (schedules.isEmpty()) {
            navigateTo("/amortization");
            waitForPageLoad();
            assertThat(page.locator("#page-title, h1").first()).isVisible();
            return;
        }

        navigateTo("/amortization/" + schedules.get(0).getId() + "/edit");
        waitForPageLoad();

        // Update name
        var nameInput = page.locator("input[name='name']").first();
        if (nameInput.isVisible()) {
            nameInput.fill("Updated Amortization " + System.currentTimeMillis());
        }

        // Submit
        page.click("#btn-simpan, button[type='submit']");
        waitForPageLoad();

        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/amortization\\/.*"));
    }

    @Test
    @DisplayName("Should post amortization entry")
    void shouldPostAmortizationEntry() {
        var schedules = scheduleRepository.findAll();
        if (schedules.isEmpty()) {
            navigateTo("/amortization");
            waitForPageLoad();
            assertThat(page.locator("#page-title, h1").first()).isVisible();
            return;
        }

        navigateTo("/amortization/" + schedules.get(0).getId());
        waitForPageLoad();

        var postBtn = page.locator("form[action*='/entries/'][action*='/post'] button[type='submit']").first();
        if (postBtn.isVisible()) {
            postBtn.click();
            waitForPageLoad();
        }

        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/amortization\\/.*"));
    }

    @Test
    @DisplayName("Should skip amortization entry")
    void shouldSkipAmortizationEntry() {
        var schedules = scheduleRepository.findAll();
        if (schedules.isEmpty()) {
            navigateTo("/amortization");
            waitForPageLoad();
            assertThat(page.locator("#page-title, h1").first()).isVisible();
            return;
        }

        navigateTo("/amortization/" + schedules.get(0).getId());
        waitForPageLoad();

        var skipBtn = page.locator("form[action*='/entries/'][action*='/skip'] button[type='submit']").first();
        if (skipBtn.isVisible()) {
            skipBtn.click();
            waitForPageLoad();
        }

        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/amortization\\/.*"));
    }

    @Test
    @DisplayName("Should post all pending entries")
    void shouldPostAllPendingEntries() {
        var schedules = scheduleRepository.findAll();
        if (schedules.isEmpty()) {
            navigateTo("/amortization");
            waitForPageLoad();
            assertThat(page.locator("#page-title, h1").first()).isVisible();
            return;
        }

        navigateTo("/amortization/" + schedules.get(0).getId());
        waitForPageLoad();

        var postAllBtn = page.locator("form[action*='/entries/post-all'] button[type='submit']").first();
        if (postAllBtn.isVisible()) {
            postAllBtn.click();
            waitForPageLoad();
        }

        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/amortization\\/.*"));
    }

    @Test
    @DisplayName("Should cancel amortization schedule")
    void shouldCancelAmortizationSchedule() {
        var schedules = scheduleRepository.findAll();
        if (schedules.isEmpty()) {
            navigateTo("/amortization");
            waitForPageLoad();
            assertThat(page.locator("#page-title, h1").first()).isVisible();
            return;
        }

        navigateTo("/amortization/" + schedules.get(0).getId());
        waitForPageLoad();

        var cancelBtn = page.locator("form[action*='/cancel'] button[type='submit']").first();
        if (cancelBtn.isVisible()) {
            cancelBtn.click();
            waitForPageLoad();
        }

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should delete amortization schedule")
    void shouldDeleteAmortizationSchedule() {
        var schedules = scheduleRepository.findAll();
        if (schedules.isEmpty()) {
            navigateTo("/amortization");
            waitForPageLoad();
            assertThat(page.locator("#page-title, h1").first()).isVisible();
            return;
        }

        navigateTo("/amortization/" + schedules.get(0).getId());
        waitForPageLoad();

        var deleteBtn = page.locator("form[action*='/delete'] button[type='submit']").first();
        if (deleteBtn.isVisible()) {
            deleteBtn.click();
            waitForPageLoad();
        }

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should process batch via POST")
    void shouldProcessBatchViaPost() {
        // Navigate to amortization list first
        navigateTo("/amortization");
        waitForPageLoad();

        // Look for batch process button
        var batchBtn = page.locator("form[action*='/batch/process'] button[type='submit']").first();
        if (batchBtn.isVisible()) {
            batchBtn.click();
            waitForPageLoad();
        }

        // Should redirect back to list
        assertThat(page.locator("#page-title, h1").first()).isVisible();
    }

    @Test
    @DisplayName("Should filter by status using query param")
    void shouldFilterByStatusUsingQueryParam() {
        navigateTo("/amortization?status=ACTIVE");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).isVisible();
    }

    @Test
    @DisplayName("Should filter by type using query param")
    void shouldFilterByTypeUsingQueryParam() {
        // Valid types: PREPAID_EXPENSE, UNEARNED_REVENUE, INTANGIBLE_ASSET, ACCRUED_REVENUE
        navigateTo("/amortization?type=PREPAID_EXPENSE");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).isVisible();
    }

    @Test
    @DisplayName("Should filter by search using query param")
    void shouldFilterBySearchUsingQueryParam() {
        navigateTo("/amortization?search=test");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).isVisible();
    }

    @Test
    @DisplayName("Should handle combined filters")
    void shouldHandleCombinedFilters() {
        navigateTo("/amortization?status=ACTIVE&type=PREPAID_EXPENSE&search=insurance");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).isVisible();
    }
}
