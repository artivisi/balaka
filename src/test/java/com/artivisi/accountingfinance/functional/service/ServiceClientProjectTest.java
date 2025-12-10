package com.artivisi.accountingfinance.functional.service;

import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Service Industry Client and Project Tests
 * Tests client list, project list, and milestone functionality.
 */
@DisplayName("Service Industry - Clients & Projects")
public class ServiceClientProjectTest extends PlaywrightTestBase {

    @Test
    @DisplayName("Should display Client List")
    void shouldDisplayClientList() {
        loginAsAdmin();
        navigateTo("/clients");
        waitForPageLoad();

        // Verify clients page loads
        assertThat(page.locator("h1")).containsText("Klien");

        // Verify test clients from V810 exist
        assertThat(page.locator("text=PT Bank Mandiri")).isVisible();
        assertThat(page.locator("text=PT Telkom Indonesia")).isVisible();
    }

    @Test
    @DisplayName("Should display Client Detail")
    void shouldDisplayClientDetail() {
        loginAsAdmin();
        navigateTo("/clients");
        waitForPageLoad();

        // Click on Bank Mandiri
        page.locator("text=PT Bank Mandiri").first().click();
        waitForPageLoad();

        // Verify detail page shows client info
        assertThat(page.locator("text=PT Bank Mandiri")).isVisible();
    }

    @Test
    @DisplayName("Should display Project List")
    void shouldDisplayProjectList() {
        loginAsAdmin();
        navigateTo("/projects");
        waitForPageLoad();

        // Verify projects page loads
        assertThat(page.locator("h1")).containsText("Proyek");

        // Verify test projects from V810 exist
        assertThat(page.locator("text=Core Banking System")).isVisible();
    }

    @Test
    @DisplayName("Should display Project Detail")
    void shouldDisplayProjectDetail() {
        loginAsAdmin();
        navigateTo("/projects");
        waitForPageLoad();

        // Click on Core Banking project
        page.locator("text=Core Banking").first().click();
        waitForPageLoad();

        // Verify detail page shows project info - use first() to avoid strict mode violation
        assertThat(page.locator("text=Core Banking").first()).isVisible();
    }

    @Test
    @DisplayName("Should display Project Milestones")
    void shouldDisplayProjectMilestones() {
        loginAsAdmin();
        navigateTo("/projects");
        waitForPageLoad();

        // Click on Core Banking project
        page.locator("text=Core Banking").first().click();
        waitForPageLoad();

        // Verify milestones are visible
        assertThat(page.locator("text=Requirement Analysis")).isVisible();
    }
}
