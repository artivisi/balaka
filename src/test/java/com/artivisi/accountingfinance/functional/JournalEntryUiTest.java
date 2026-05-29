package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests for the free-form journal entry UI.
 * Tests: navigation, form validation, save draft, save & post.
 */
@Slf4j
@DisplayName("Free-Form Journal Entry UI - Functional Tests")
@Import(ServiceTestDataInitializer.class)
class JournalEntryUiTest extends PlaywrightTestBase {

    @Test
    @DisplayName("Should navigate to journal entry form from transaction list")
    void shouldNavigateToJournalEntryForm() {
        loginAsAdmin();
        navigateTo("/transactions");
        waitForPageLoad();

        // Click the "Jurnal Manual" button
        Locator journalButton = page.locator("[data-testid='btn-journal-entry']");
        assertThat(journalButton.isVisible()).isTrue();
        journalButton.click();
        waitForPageLoad();

        // Verify we're on the journal entry form
        assertThat(page.url()).contains("/transactions/journal-entry/new");
        assertThat(page.locator("#transactionDate").isVisible()).isTrue();
        assertThat(page.locator("#description").isVisible()).isTrue();
    }

    @Test
    @DisplayName("Should show validation errors for empty form")
    void shouldShowValidationErrors() {
        loginAsAdmin();
        navigateTo("/transactions/journal-entry/new");
        waitForPageLoad();

        // Click save without filling anything
        page.locator("#btn-save-draft").click();
        page.waitForTimeout(500);

        // Should show validation error
        Locator errorMessage = page.locator("text=Deskripsi wajib diisi");
        assertThat(errorMessage.isVisible()).isTrue();
    }

    @Test
    @DisplayName("Should create and save draft journal entry")
    void shouldCreateDraftJournalEntry() {
        loginAsAdmin();
        navigateTo("/transactions/journal-entry/new");
        waitForPageLoad();

        // Fill description (type to trigger x-model)
        page.locator("#description").click();
        page.locator("#description").type("Jurnal penyesuaian beban listrik");

        // Select category
        page.locator("#category").selectOption("ADJUSTING");

        // Select accounts by picking the first available option (index 1, since 0 is placeholder)
        selectAccountByIndex("[data-testid='account-select-0']", 1);
        page.locator("[data-testid='debit-input-0']").click();
        page.locator("[data-testid='debit-input-0']").type("500000");

        selectAccountByIndex("[data-testid='account-select-1']", 2);
        page.locator("[data-testid='credit-input-1']").click();
        page.locator("[data-testid='credit-input-1']").type("500000");

        page.waitForTimeout(500);

        // Save draft and wait for navigation
        page.locator("#btn-save-draft").click();

        // Wait for the page to navigate away from journal-entry/new
        page.waitForFunction("() => !window.location.href.includes('journal-entry')", null,
                new com.microsoft.playwright.Page.WaitForFunctionOptions().setTimeout(15000));

        // Should be on transaction detail page
        assertThat(page.url()).matches(".*\\/transactions\\/[0-9a-f-]+$");

        // Verify it's a DRAFT
        waitForPageLoad();
        Locator statusBadge = page.locator("text=DRAFT").first();
        assertThat(statusBadge.isVisible()).isTrue();
    }

    @Test
    @DisplayName("Should create and post journal entry")
    void shouldCreateAndPostJournalEntry() {
        loginAsAdmin();
        navigateTo("/transactions/journal-entry/new");
        waitForPageLoad();

        // Fill description (type to trigger x-model)
        page.locator("#description").click();
        page.locator("#description").type("Jurnal penutup pendapatan");

        // Select category
        page.locator("#category").selectOption("CLOSING");

        // Fill first line - debit
        selectAccountByIndex("[data-testid='account-select-0']", 1);
        page.locator("[data-testid='debit-input-0']").click();
        page.locator("[data-testid='debit-input-0']").type("750000");

        // Fill second line - credit
        selectAccountByIndex("[data-testid='account-select-1']", 2);
        page.locator("[data-testid='credit-input-1']").click();
        page.locator("[data-testid='credit-input-1']").type("750000");

        page.waitForTimeout(500);

        // Save & Post and wait for navigation
        page.locator("#btn-save-post").click();

        page.waitForFunction("() => !window.location.href.includes('journal-entry')", null,
                new com.microsoft.playwright.Page.WaitForFunctionOptions().setTimeout(15000));

        // Should be on transaction detail page
        assertThat(page.url()).matches(".*\\/transactions\\/[0-9a-f-]+$");

        // Verify it's POSTED
        waitForPageLoad();
        Locator statusBadge = page.locator("text=POSTED").first();
        assertThat(statusBadge.isVisible()).isTrue();
    }

    @Test
    @DisplayName("Should add and remove journal lines")
    void shouldAddAndRemoveLines() {
        loginAsAdmin();
        navigateTo("/transactions/journal-entry/new");
        waitForPageLoad();

        // Should start with 2 lines
        assertThat(page.locator(".journal-line").count()).isEqualTo(2);

        // Add a line
        page.locator("text=Tambah Baris").click();
        page.waitForTimeout(300);
        assertThat(page.locator(".journal-line").count()).isEqualTo(3);

        // Remove the added line
        page.locator("[data-testid='remove-line-2']").click();
        page.waitForTimeout(300);
        assertThat(page.locator(".journal-line").count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should show unbalanced warning")
    void shouldShowUnbalancedWarning() {
        loginAsAdmin();
        navigateTo("/transactions/journal-entry/new");
        waitForPageLoad();

        // Fill description
        page.locator("#description").click();
        page.locator("#description").type("Unbalanced test");

        // Fill first line - debit 100000
        selectAccountByIndex("[data-testid='account-select-0']", 1);
        page.locator("[data-testid='debit-input-0']").click();
        page.locator("[data-testid='debit-input-0']").type("100000");

        // Fill second line - credit 50000 (unbalanced)
        selectAccountByIndex("[data-testid='account-select-1']", 2);
        page.locator("[data-testid='credit-input-1']").click();
        page.locator("[data-testid='credit-input-1']").type("50000");

        page.waitForTimeout(300);

        // Should show unbalanced indicator
        Locator selisih = page.locator("text=Selisih:");
        assertThat(selisih.isVisible()).isTrue();

        // Try to save - should fail
        page.locator("#btn-save-draft").click();
        page.waitForTimeout(500);
        Locator error = page.locator("text=Jurnal tidak seimbang");
        assertThat(error.isVisible()).isTrue();
    }

    @Test
    @DisplayName("Should capture screenshots for user manual")
    void shouldCaptureScreenshotsForManual() {
        loginAsAdmin();
        navigateTo("/transactions/journal-entry/new");
        waitForPageLoad();

        // Screenshot 1: Empty form
        takeManualScreenshot("journal-entry/form-empty");

        // Fill the form with realistic data
        page.locator("#description").click();
        page.locator("#description").type("Penyesuaian sewa kantor bulan Januari 2026");
        page.locator("#category").selectOption("ADJUSTING");

        // Line 1: Debit Beban Sewa
        selectAccountByIndex("[data-testid='account-select-0']", 1);
        page.locator("[data-testid='debit-input-0']").click();
        page.locator("[data-testid='debit-input-0']").type("1000000");

        // Line 2: Credit Sewa Dibayar Dimuka
        selectAccountByIndex("[data-testid='account-select-1']", 2);
        page.locator("[data-testid='credit-input-1']").click();
        page.locator("[data-testid='credit-input-1']").type("1000000");

        page.waitForTimeout(500);

        // Screenshot 2: Filled form with balanced journal
        takeManualScreenshot("journal-entry/form-filled");

        // Save & Post
        page.locator("#btn-save-post").click();
        page.waitForFunction("() => !window.location.href.includes('journal-entry')", null,
                new com.microsoft.playwright.Page.WaitForFunctionOptions().setTimeout(15000));
        waitForPageLoad();

        // Screenshot 3: Posted result
        takeManualScreenshot("journal-entry/result-posted");

        // Verify the journal was posted (page should show posted status)
        assertThat(page.url()).as("Should redirect after posting").doesNotContain("journal-entry/new");
    }

    /**
     * Drive the accountPicker combobox by picking the Nth result of an empty
     * query. Server returns top-10 transactable accounts in code-ascending order,
     * so empty-query indexing matches the legacy &lt;option&gt; ordering with the
     * placeholder offset removed: legacy index 1 → combobox index 0, index 2 → 1.
     */
    private void selectAccountByIndex(String selector, int legacyIndex) {
        Locator input = page.locator(selector);
        input.waitFor();
        input.click();
        page.waitForTimeout(400); // debounce + fetch
        int comboboxIndex = legacyIndex - 1;
        Locator results = page.locator("[data-testid='account-picker-result']");
        results.nth(comboboxIndex).click();
    }
}
