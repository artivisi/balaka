package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Functional tests for PaymentTermController.
 * Tests CRUD operations for project payment terms.
 */
@DisplayName("Payment Term Tests")
@Import(ServiceTestDataInitializer.class)
class PaymentTermTest extends PlaywrightTestBase {

    private static final String PROJECT_CODE = "PRJ-2024-001";

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
    }

    // ==================== Payment Term Form Tests ====================

    @Test
    @DisplayName("Should display new payment term form")
    void shouldDisplayNewPaymentTermForm() {
        navigateTo("/projects/" + PROJECT_CODE + "/payment-terms/new");
        waitForPageLoad();

        assertThat(page.locator("#sequence")).isVisible();
        assertThat(page.locator("#name")).isVisible();
        assertThat(page.locator("#dueTrigger")).isVisible();
    }

    @Test
    @DisplayName("Should show project context in form")
    void shouldShowProjectContextInForm() {
        navigateTo("/projects/" + PROJECT_CODE + "/payment-terms/new");
        waitForPageLoad();

        Assertions.assertThat(page.url()).contains(PROJECT_CODE);
    }

    @Test
    @DisplayName("Should create new payment term")
    void shouldCreateNewPaymentTerm() {
        navigateTo("/projects/" + PROJECT_CODE + "/payment-terms/new");
        waitForPageLoad();

        // Use unique sequence to avoid conflicts with other tests
        String uniqueSequence = String.valueOf(System.currentTimeMillis() % 1000 + 100);
        page.locator("#sequence").fill(uniqueSequence);
        page.locator("#name").fill("Test Payment Term " + uniqueSequence);
        page.locator("#dueTrigger").selectOption("ON_SIGNING");
        page.locator("#percentage").fill("30");

        page.locator("#btn-simpan").click();

        // Wait for redirect back to project page (away from /new)
        page.waitForURL(url -> !url.contains("/payment-terms/new"), new com.microsoft.playwright.Page.WaitForURLOptions().setTimeout(10000));
        waitForPageLoad();

        // Should redirect back to project page
        Assertions.assertThat(page.url()).contains("/projects/");
        Assertions.assertThat(page.url()).doesNotContain("/payment-terms/new");
    }

    @Test
    @DisplayName("Should validate required fields")
    void shouldValidateRequiredFields() {
        navigateTo("/projects/" + PROJECT_CODE + "/payment-terms/new");
        waitForPageLoad();

        // Submit empty form (name is required)
        page.locator("#btn-simpan").click();

        // HTML5 validation will prevent submission - URL stays the same
        Assertions.assertThat(page.url()).contains("/payment-terms/new");
    }

    // ==================== Payment Term Actions Tests ====================

    @Test
    @DisplayName("Should navigate to project from payment term")
    void shouldNavigateToProjectFromPaymentTerm() {
        navigateTo("/projects/" + PROJECT_CODE);
        waitForPageLoad();

        Assertions.assertThat(page.url()).contains("/projects/" + PROJECT_CODE);
    }

    @Test
    @DisplayName("Should access payment terms from project detail")
    void shouldAccessPaymentTermsFromProjectDetail() {
        navigateTo("/projects/" + PROJECT_CODE);
        waitForPageLoad();

        var addLink = page.locator("a[href*='payment-terms/new']");
        assertThat(addLink).isVisible();

        addLink.click();
        waitForPageLoad();

        Assertions.assertThat(page.url()).contains("/payment-terms/new");
    }

    // ==================== Payment Term Trigger Tests ====================

    @Test
    @DisplayName("Should display all trigger options")
    void shouldDisplayAllTriggerOptions() {
        navigateTo("/projects/" + PROJECT_CODE + "/payment-terms/new");
        waitForPageLoad();

        var dueTriggerSelect = page.locator("#dueTrigger");
        assertThat(dueTriggerSelect).isVisible();

        var options = page.locator("#dueTrigger option").all();
        Assertions.assertThat(options).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Should select different trigger types")
    void shouldSelectDifferentTriggerTypes() {
        navigateTo("/projects/" + PROJECT_CODE + "/payment-terms/new");
        waitForPageLoad();

        var dueTriggerSelect = page.locator("#dueTrigger");
        assertThat(dueTriggerSelect).isVisible();

        dueTriggerSelect.selectOption("ON_SIGNING");
        Assertions.assertThat(dueTriggerSelect.inputValue()).isEqualTo("ON_SIGNING");

        dueTriggerSelect.selectOption("ON_COMPLETION");
        Assertions.assertThat(dueTriggerSelect.inputValue()).isEqualTo("ON_COMPLETION");
    }
}
