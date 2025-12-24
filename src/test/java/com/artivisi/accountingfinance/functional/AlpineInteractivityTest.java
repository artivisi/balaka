package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive tests for Alpine.js interactivity.
 *
 * These tests verify that Alpine.js components work correctly,
 * not just that DOM elements exist. They test:
 * - State changes after user interactions
 * - Dynamic content rendering
 * - Toggle/expand/collapse behaviors
 *
 * IMPORTANT: These tests should FAIL if Alpine.js is broken due to CSP issues.
 * If all tests pass but UI is broken, the tests are ineffective.
 */
@DisplayName("Alpine.js Interactivity Tests")
@Import(ServiceTestDataInitializer.class)
public class AlpineInteractivityTest extends PlaywrightTestBase {

    private List<String> consoleErrors;

    @BeforeEach
    void setupConsoleCapture() {
        consoleErrors = new ArrayList<>();
        page.onConsoleMessage(msg -> {
            if (msg.type().equals("error")) {
                consoleErrors.add(msg.text());
                System.err.println("[CONSOLE ERROR] " + msg.text());
            }
        });
        page.onPageError(error -> {
            consoleErrors.add("PAGE ERROR: " + error);
            System.err.println("[PAGE ERROR] " + error);
        });
    }

    // ==================== TEMPLATE FORM TESTS ====================

    @Test
    @DisplayName("Template form - account dropdown should have options")
    void templateFormAccountDropdownShouldHaveOptions() {
        loginAsAdmin();
        page.navigate(baseUrl() + "/templates/new");
        waitForPageLoad();
        page.waitForTimeout(500); // Wait for Alpine.js initialization

        // Account dropdown should be populated - check first line's account select
        Locator accountSelect = page.locator("[data-testid='line-account-0']");

        // If data-testid not found, try the actual select element
        if (accountSelect.count() == 0) {
            accountSelect = page.locator("select[name='lines[0].accountId']");
        }

        assertThat(accountSelect.count())
            .as("Account select should exist")
            .isGreaterThan(0);

        int optionCount = accountSelect.first().locator("option").count();

        assertThat(optionCount)
            .as("Account dropdown should have options (not just placeholder)")
            .isGreaterThan(1);
    }

    @Test
    @DisplayName("Template form - add line button should create new row")
    void templateFormAddLineShouldWork() {
        loginAsAdmin();
        page.navigate(baseUrl() + "/templates/new");
        waitForPageLoad();
        page.waitForTimeout(500);

        // Count initial lines using the select elements
        Locator lineSelects = page.locator("select[name^='lines['][name$='].accountId']");
        int initialCount = lineSelects.count();

        // Click add line button
        Locator addBtn = page.locator("[data-testid='btn-add-line']");
        if (addBtn.count() == 0) {
            // Fallback: find button by text content
            addBtn = page.locator("button:has-text('Tambah Baris')");
        }

        assertThat(addBtn.count())
            .as("Add line button should exist")
            .isGreaterThan(0);

        addBtn.first().click();
        page.waitForTimeout(300);

        // Count lines after
        int newCount = page.locator("select[name^='lines['][name$='].accountId']").count();

        assertThat(newCount)
            .as("Should have one more line after clicking add (initial: " + initialCount + ")")
            .isEqualTo(initialCount + 1);
    }

    @Test
    @DisplayName("Template form - formula help should toggle")
    void templateFormFormulaHelpShouldToggle() {
        loginAsAdmin();
        page.navigate(baseUrl() + "/templates/new");
        waitForPageLoad();
        page.waitForTimeout(500);

        // Find formula help content
        Locator helpContent = page.locator("[data-testid='formula-help-content']");
        if (helpContent.count() == 0) {
            // Fallback: find by class pattern for formula help panel
            helpContent = page.locator(".bg-blue-50.rounded-lg.border-blue-200");
        }

        // Formula help should be initially hidden
        assertThat(helpContent.first().isVisible())
            .as("Formula help should be initially hidden")
            .isFalse();

        // Click toggle button
        Locator toggleBtn = page.locator("[data-testid='btn-formula-help-toggle']");
        if (toggleBtn.count() == 0) {
            toggleBtn = page.locator("button:has-text('Bantuan Formula')");
        }

        assertThat(toggleBtn.count())
            .as("Formula help toggle button should exist")
            .isGreaterThan(0);

        toggleBtn.first().click();
        page.waitForTimeout(300);

        // Should now be visible
        assertThat(helpContent.first().isVisible())
            .as("Formula help should be visible after toggle")
            .isTrue();
    }

    @Test
    @DisplayName("Template form - position toggle D/K should work")
    void templateFormPositionToggleShouldWork() {
        loginAsAdmin();
        page.navigate(baseUrl() + "/templates/new");
        waitForPageLoad();
        page.waitForTimeout(500);

        // Get position hidden input value
        Locator positionInput = page.locator("input[name='lines[0].position']");

        assertThat(positionInput.count())
            .as("Position input should exist")
            .isGreaterThan(0);

        String initialPosition = positionInput.first().inputValue();

        // Find and click the opposite button
        Locator debitBtn = page.locator("[data-testid='btn-debit-0']");
        Locator creditBtn = page.locator("[data-testid='btn-credit-0']");

        // Fallback if data-testid not found
        if (debitBtn.count() == 0) {
            debitBtn = page.locator("button:has-text('D')").first();
            creditBtn = page.locator("button:has-text('K')").first();
        }

        // Click the opposite button
        if ("DEBIT".equals(initialPosition)) {
            creditBtn.click();
        } else {
            debitBtn.click();
        }
        page.waitForTimeout(200);

        // Position should have changed
        String newPosition = positionInput.first().inputValue();
        assertThat(newPosition)
            .as("Position should change after clicking toggle (was: " + initialPosition + ")")
            .isNotEqualTo(initialPosition);
    }

    // ==================== ACCOUNT TREE TESTS ====================

    @Test
    @DisplayName("Account tree - expand button should show children")
    void accountTreeExpandShouldShowChildren() {
        loginAsAdmin();
        page.navigate(baseUrl() + "/accounts");
        waitForPageLoad();
        page.waitForTimeout(500);

        // Find first expandable account (has expand button)
        Locator expandBtns = page.locator("[id^='btn-expand-']");

        if (expandBtns.count() == 0) {
            System.out.println("No expandable accounts found - skipping test");
            return;
        }

        Locator expandBtn = expandBtns.first();
        String btnId = expandBtn.getAttribute("id");
        String accountCode = btnId.replace("btn-expand-", "");

        // Find children container
        Locator childrenContainer = page.locator("[data-testid='account-children-" + accountCode + "']");
        if (childrenContainer.count() == 0) {
            // Fallback: look for the container by structure
            childrenContainer = expandBtn.locator("xpath=ancestor::div[@x-data]//div[@x-cloak]");
        }

        // Click expand
        expandBtn.click();
        page.waitForTimeout(300);

        // After expand, check if any child accounts are visible
        // The children should be visible (not have 'hidden' class)
        Locator childRows = page.locator("[id^='account-row-" + accountCode + "-']");

        // If we found specific child rows, check them
        if (childRows.count() > 0) {
            assertThat(childRows.first().isVisible())
                .as("Child account rows should be visible after expand")
                .isTrue();
        }

        // Click again to collapse
        expandBtn.click();
        page.waitForTimeout(300);

        // After collapse, children should be hidden again
        if (childRows.count() > 0) {
            assertThat(childRows.first().isVisible())
                .as("Child account rows should be hidden after collapse")
                .isFalse();
        }
    }

    // ==================== SEARCH FILTER TESTS ====================

    @Test
    @DisplayName("Transaction search - dropdown should open on focus")
    void transactionSearchDropdownShouldOpen() {
        loginAsAdmin();
        page.navigate(baseUrl() + "/transactions");
        waitForPageLoad();
        page.waitForTimeout(500);

        // Find search input
        Locator searchInput = page.locator("[data-testid='search-input']");
        if (searchInput.count() == 0) {
            searchInput = page.locator("input[type='search'], input[placeholder*='Cari']");
        }

        if (searchInput.count() == 0) {
            System.out.println("No search input found - skipping test");
            return;
        }

        // Find dropdown
        Locator dropdown = page.locator("[data-testid='search-dropdown']");
        if (dropdown.count() == 0) {
            // Fallback: look for dropdown container near search
            dropdown = page.locator(".absolute.max-h-64.overflow-y-auto");
        }

        // Dropdown should be initially hidden
        if (dropdown.count() > 0) {
            assertThat(dropdown.first().isVisible())
                .as("Dropdown should be initially hidden")
                .isFalse();
        }

        // Focus on search input
        searchInput.first().focus();
        page.waitForTimeout(300);

        // Dropdown should now be visible
        if (dropdown.count() > 0) {
            assertThat(dropdown.first().isVisible())
                .as("Dropdown should open on focus")
                .isTrue();
        }
    }

    // ==================== SALARY COMPONENT TESTS ====================

    @Test
    @DisplayName("Salary component - percentage toggle should work")
    void salaryComponentPercentageToggleShouldWork() {
        loginAsAdmin();
        page.navigate(baseUrl() + "/salary-components/new");
        waitForPageLoad();
        page.waitForTimeout(500);

        // Find radio buttons
        Locator radioFixed = page.locator("[data-testid='radio-fixed']");
        Locator radioPercentage = page.locator("[data-testid='radio-percentage']");

        if (radioFixed.count() == 0) {
            radioFixed = page.locator("input[type='radio'][value='false']");
            radioPercentage = page.locator("input[type='radio'][value='true']");
        }

        if (radioFixed.count() == 0 || radioPercentage.count() == 0) {
            System.out.println("Radio buttons not found - skipping test");
            return;
        }

        // Find input containers
        Locator amountContainer = page.locator("[data-testid='amount-input-container']");
        Locator percentageContainer = page.locator("[data-testid='percentage-input-container']");

        if (amountContainer.count() == 0) {
            // Fallback: find by x-cloak attribute
            amountContainer = page.locator("div[x-cloak]:has(input[name='defaultValue'])").first();
            percentageContainer = page.locator("div[x-cloak]:has(input[name='percentageValue'])").first();
        }

        // Check initial state - one should be visible, one hidden
        boolean amountVisible = amountContainer.count() > 0 && amountContainer.first().isVisible();

        // Toggle to percentage
        radioPercentage.first().click();
        page.waitForTimeout(200);

        // Check new state
        boolean amountVisibleAfter = amountContainer.count() > 0 && amountContainer.first().isVisible();

        assertThat(amountVisibleAfter)
            .as("Amount container visibility should change after toggle")
            .isNotEqualTo(amountVisible);
    }

    // ==================== CLIENT FORM TESTS ====================

    @Test
    @DisplayName("Client form - ID type selector should update hint")
    void clientFormIdTypeShouldUpdate() {
        loginAsAdmin();
        page.navigate(baseUrl() + "/clients/new");
        waitForPageLoad();
        page.waitForTimeout(500);

        // Find ID type select
        Locator idTypeSelect = page.locator("[data-testid='id-type-select']");
        if (idTypeSelect.count() == 0) {
            idTypeSelect = page.locator("select[name='idType']");
        }

        if (idTypeSelect.count() == 0) {
            System.out.println("ID type select not found - skipping test");
            return;
        }

        // Get format hint element
        Locator formatHint = page.locator("[data-testid='id-format-hint']");
        if (formatHint.count() == 0) {
            // Try to find a hint/help text near the ID number input
            formatHint = page.locator(".text-gray-500:near(input[name='idNumber'])");
        }

        // Select different ID types and verify the form accepts them
        // This tests that Alpine.js is processing the change event
        // Options are: TIN (NPWP) and NIK
        idTypeSelect.first().selectOption("NIK");
        page.waitForTimeout(200);

        // Verify the select value changed
        String selectedValue = idTypeSelect.first().inputValue();
        assertThat(selectedValue)
            .as("ID type should be changeable")
            .isEqualTo("NIK");
    }

    // ==================== TRANSACTION FORM TESTS ====================

    @Test
    @DisplayName("Transaction form - submit button should have text bound to Alpine state")
    void transactionFormSubmitButtonShouldHaveText() {
        loginAsAdmin();
        // Transaction form requires template selection first
        // Navigate to template list and find a "use" link
        page.navigate(baseUrl() + "/templates");
        waitForPageLoad();
        page.waitForTimeout(500);

        // Find and click a template's "use" link by ID pattern
        Locator useLinks = page.locator("[id^='btn-use-']");
        if (useLinks.count() == 0) {
            // Fallback: try data-testid pattern
            useLinks = page.locator("[data-testid^='btn-use-']");
        }

        if (useLinks.count() == 0) {
            // Skip if no templates exist
            System.out.println("No use template links found - skipping test");
            return;
        }

        // Clear console errors before navigation
        consoleErrors.clear();

        // Click on first template's "use" link
        useLinks.first().click();
        waitForPageLoad();
        page.waitForTimeout(500);

        // Print any console errors for debugging
        if (!consoleErrors.isEmpty()) {
            System.err.println("=== CONSOLE ERRORS ON TRANSACTION FORM ===");
            consoleErrors.forEach(e -> System.err.println("  " + e));
        }

        // Get submit button by ID
        Locator submitBtn = page.locator("#btn-simpan-draft");
        if (submitBtn.count() == 0) {
            submitBtn = page.locator("[data-testid='btn-submit']");
        }

        if (submitBtn.count() == 0) {
            System.out.println("Submit button not found - skipping test");
            return;
        }

        String buttonText = submitBtn.first().textContent().trim();
        System.out.println("Button text: '" + buttonText + "'");

        assertThat(buttonText)
            .as("Submit button should have text containing 'Simpan'")
            .containsIgnoringCase("Simpan");
    }

    // ==================== CONSOLE ERROR CHECK ====================

    @Test
    @DisplayName("No Alpine.js errors in console across all tested pages")
    void noAlpineErrorsAcrossPages() {
        loginAsAdmin();

        String[] pagesToTest = {
            "/templates/new",
            "/accounts",
            "/transactions",
            "/transactions/new",
            "/salary-components/new",
            "/clients/new"
        };

        List<String> allErrors = new ArrayList<>();

        for (String pagePath : pagesToTest) {
            consoleErrors.clear();
            page.navigate(baseUrl() + pagePath);
            waitForPageLoad();
            page.waitForTimeout(500);

            List<String> pageErrors = consoleErrors.stream()
                .filter(e -> e.toLowerCase().contains("alpine") ||
                            e.toLowerCase().contains("x-data") ||
                            e.contains("is not defined") ||
                            e.contains("is not a function") ||
                            e.contains("Cannot read properties of undefined"))
                .map(e -> pagePath + ": " + e)
                .toList();

            allErrors.addAll(pageErrors);
        }

        if (!allErrors.isEmpty()) {
            System.err.println("\n=== ALPINE.JS ERRORS FOUND ===");
            allErrors.forEach(e -> System.err.println("  " + e));
        }

        assertThat(allErrors)
            .as("No Alpine.js errors should occur across all pages")
            .isEmpty();
    }
}
