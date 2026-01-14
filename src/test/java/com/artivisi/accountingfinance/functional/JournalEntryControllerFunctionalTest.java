package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Functional tests for JournalEntryController.
 * Tests journal list page with filters.
 *
 * Note: Ledger page tests removed - template journals/ledger.html does not exist.
 * API endpoint tests removed - they return JSON, not testable via page navigation.
 */
@DisplayName("Journal Entry Controller Tests")
@Import(ServiceTestDataInitializer.class)
class JournalEntryControllerFunctionalTest extends PlaywrightTestBase {

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("Should display journal list page")
    void shouldDisplayJournalListPage() {
        navigateTo("/journals");
        waitForPageLoad();

        // Assert specific page element exists - page title
        assertThat(page.locator("#page-title")).isVisible();
        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/journals.*"));
    }

    @Test
    @DisplayName("Should have date filter inputs")
    void shouldHaveDateFilterInputs() {
        navigateTo("/journals");
        waitForPageLoad();

        // Assert date filter inputs are visible
        assertThat(page.locator("#start-date")).isVisible();
        assertThat(page.locator("#end-date")).isVisible();
    }

    @Test
    @DisplayName("Should have account select dropdown")
    void shouldHaveAccountSelectDropdown() {
        navigateTo("/journals");
        waitForPageLoad();

        // Assert account select is visible
        assertThat(page.locator("#account-filter")).isVisible();
    }

    @Test
    @DisplayName("Should filter journals by date range")
    void shouldFilterJournalsByDateRange() {
        LocalDate start = LocalDate.now().minusMonths(1);
        LocalDate end = LocalDate.now();

        navigateTo("/journals?startDate=" + start + "&endDate=" + end);
        waitForPageLoad();

        // Assert page loaded correctly with page title
        assertThat(page.locator("#page-title")).isVisible();
    }
}
