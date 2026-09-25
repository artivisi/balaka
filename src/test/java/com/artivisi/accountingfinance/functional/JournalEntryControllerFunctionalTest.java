package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Functional tests for JournalEntryController.
 * Tests journal list page with filters.
 *
 * Note: Ledger page tests removed - template journals/ledger.html does not exist.
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

    @Test
    @DisplayName("Journal API should return posted entries within the date range with their amounts")
    void journalApiShouldReturnPostedEntriesWithinDateRange() throws Exception {
        APIResponse response = page.request().get(baseUrl() + "/journals/api?startDate=2024-01-01&endDate=2024-01-15&size=500");
        assertEquals(200, response.status(), response.text());
        JsonNode body = new ObjectMapper().readTree(response.text());

        Map<String, JsonNode> byNumber = new HashMap<>();
        for (JsonNode entry : body.get("content")) {
            byNumber.put(entry.get("journalNumber").asText(), entry);
        }

        assertAmounts(byNumber, "JRN-SVC-001-D", "500000000", "0");
        assertAmounts(byNumber, "JRN-SVC-001-C", "0", "500000000");
        assertAmounts(byNumber, "JRN-SVC-002-D", "196200000", "0");
        assertAmounts(byNumber, "JRN-SVC-002-C", "0", "196200000");
        assertAmounts(byNumber, "JRN-SVC-003-D", "3330000", "0");
        assertAmounts(byNumber, "JRN-SVC-003-C", "0", "3330000");
        assertFalse(byNumber.containsKey("JRN-SVC-004-D"), "2024-01-31 entry must be outside the range");
        assertFalse(byNumber.containsKey("JRN-SVC-004-C"), "2024-01-31 entry must be outside the range");
    }

    private void assertAmounts(Map<String, JsonNode> byNumber, String journalNumber, String debit, String credit) {
        JsonNode entry = byNumber.get(journalNumber);
        if (entry == null) {
            throw new AssertionError("Seeded journal entry missing from API response: " + journalNumber);
        }
        assertEquals(0, new BigDecimal(debit).compareTo(entry.get("debitAmount").decimalValue()), journalNumber + " debit");
        assertEquals(0, new BigDecimal(credit).compareTo(entry.get("creditAmount").decimalValue()), journalNumber + " credit");
    }
}
