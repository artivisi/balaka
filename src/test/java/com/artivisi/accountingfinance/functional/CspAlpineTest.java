package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for CSP (Content Security Policy) compatibility with Alpine.js.
 * Verifies that sidebar navigation and other Alpine.js components work
 * without CSP violations in the browser console.
 *
 * ISSUE FOUND: Alpine.js x-collapse plugin applies inline styles (height)
 * which violates style-src CSP directive. The sidebar submenus cannot expand.
 */
@DisplayName("CSP Alpine.js Compatibility")
@Import(ServiceTestDataInitializer.class)
public class CspAlpineTest extends PlaywrightTestBase {

    private List<String> consoleErrors;
    private List<String> consoleWarnings;
    private List<String> consoleLogs;

    @BeforeEach
    void setupConsoleCapture() {
        consoleErrors = new ArrayList<>();
        consoleWarnings = new ArrayList<>();
        consoleLogs = new ArrayList<>();

        // Capture all console messages
        page.onConsoleMessage(msg -> {
            String text = msg.text();
            if (msg.type().equals("error")) {
                consoleErrors.add(text);
                System.err.println("[CONSOLE ERROR] " + text);
            } else if (msg.type().equals("warning")) {
                consoleWarnings.add(text);
                System.err.println("[CONSOLE WARNING] " + text);
            } else if (msg.type().equals("log")) {
                consoleLogs.add(text);
                System.out.println("[CONSOLE LOG] " + text);
            }
        });

        // Also capture page errors (uncaught exceptions)
        page.onPageError(error -> {
            consoleErrors.add("PAGE ERROR: " + error);
            System.err.println("[PAGE ERROR] " + error);
        });
    }

    @Test
    @DisplayName("Sidebar submenu expand should not cause CSP errors")
    void sidebarExpandShouldNotCauseCspErrors() {
        // Login
        loginAsAdmin();
        waitForPageLoad();

        // Wait for Alpine.js to initialize
        page.waitForTimeout(500);

        // Clear any errors from page load
        consoleErrors.clear();
        consoleWarnings.clear();

        // Click to expand "Laporan" submenu (initially closed)
        page.click("#nav-group-laporan");
        page.waitForTimeout(300);

        // Click to expand "Proyek" submenu
        page.click("#nav-group-proyek");
        page.waitForTimeout(300);

        // Click to expand "Inventori" submenu
        page.click("#nav-group-inventori");
        page.waitForTimeout(300);

        // Click to expand "Payroll" submenu
        page.click("#nav-group-payroll");
        page.waitForTimeout(300);

        // Click to expand "Master Data" submenu
        page.click("#nav-group-master");
        page.waitForTimeout(300);

        // Check for CSP-related errors (including style-src violations)
        List<String> cspErrors = consoleErrors.stream()
                .filter(e -> e.toLowerCase().contains("csp") ||
                            e.toLowerCase().contains("content security policy") ||
                            e.toLowerCase().contains("unsafe-eval") ||
                            e.toLowerCase().contains("unsafe-inline") ||
                            e.toLowerCase().contains("script-src") ||
                            e.toLowerCase().contains("style-src") ||
                            e.toLowerCase().contains("inline style") ||
                            e.toLowerCase().contains("eval") ||
                            e.toLowerCase().contains("function constructor"))
                .toList();

        // Check for Alpine.js errors
        List<String> alpineErrors = consoleErrors.stream()
                .filter(e -> e.toLowerCase().contains("alpine"))
                .toList();

        // Print all errors for debugging
        if (!consoleErrors.isEmpty()) {
            System.out.println("\n=== ALL CONSOLE ERRORS ===");
            consoleErrors.forEach(e -> System.out.println("  - " + e));
        }

        if (!consoleWarnings.isEmpty()) {
            System.out.println("\n=== ALL CONSOLE WARNINGS ===");
            consoleWarnings.forEach(w -> System.out.println("  - " + w));
        }

        // Assertions
        assertThat(cspErrors)
                .as("Should not have CSP-related errors when expanding sidebar")
                .isEmpty();

        assertThat(alpineErrors)
                .as("Should not have Alpine.js errors when expanding sidebar")
                .isEmpty();
    }

    @Test
    @DisplayName("Page load should not have CSP errors - detects inline style violations")
    void pageLoadShouldNotHaveCspErrors() {
        // Login
        loginAsAdmin();
        waitForPageLoad();

        // Wait for page to settle
        page.waitForTimeout(1000);

        // Check for CSP-related errors (including style-src violations)
        // Exclude test infrastructure errors (sha256-bsV5J... is from PlaywrightTestBase animation disable)
        List<String> cspErrors = consoleErrors.stream()
                .filter(e -> e.toLowerCase().contains("csp") ||
                            e.toLowerCase().contains("content security policy") ||
                            e.toLowerCase().contains("unsafe-eval") ||
                            e.toLowerCase().contains("script-src") ||
                            e.toLowerCase().contains("eval") ||
                            e.toLowerCase().contains("function constructor"))
                // Exclude inline style errors - these come from test infrastructure (animation disable)
                // The hash sha256-bsV5JivYxvGywDAZ22EZJKBFip65Ng9xoJVLbBg7bdo= is from PlaywrightTestBase
                .filter(e -> !e.contains("sha256-bsV5J"))
                .toList();

        // Print all errors for debugging
        if (!consoleErrors.isEmpty()) {
            System.out.println("\n=== ALL CONSOLE ERRORS ON PAGE LOAD ===");
            consoleErrors.forEach(e -> System.out.println("  - " + e));
        }

        assertThat(cspErrors)
                .as("Should not have CSP-related errors on page load (excluding test infrastructure)")
                .isEmpty();
    }

    @Test
    @DisplayName("Sidebar submenu should be visible after clicking expand button")
    void sidebarSubmenuShouldExpandProperly() {
        // Login
        loginAsAdmin();
        waitForPageLoad();

        // Wait for Alpine.js to initialize
        page.waitForTimeout(500);

        // Akuntansi is open by default, check if Transaksi link is visible
        boolean transaksiVisible = page.locator("#nav-transaksi").isVisible();

        // Print visibility status
        System.out.println("\n=== SIDEBAR SUBMENU VISIBILITY ===");
        System.out.println("Transaksi link visible (Akuntansi menu): " + transaksiVisible);

        // Try to click Laporan to expand it
        page.click("#nav-group-laporan");
        page.waitForTimeout(500);

        // Check if Laporan submenu items are visible
        boolean laporanVisible = page.locator("#nav-laporan").isVisible();
        System.out.println("Laporan Keuangan link visible (Laporan menu): " + laporanVisible);

        // Print any CSP errors that might explain why menu isn't expanding
        if (!consoleErrors.isEmpty()) {
            System.out.println("\n=== CONSOLE ERRORS (may explain visibility issues) ===");
            consoleErrors.forEach(e -> System.out.println("  - " + e));
        }

        // Assert that submenu items are visible (this will fail if CSP blocks x-collapse)
        assertThat(transaksiVisible)
                .as("Transaksi link should be visible when Akuntansi menu is expanded")
                .isTrue();

        assertThat(laporanVisible)
                .as("Laporan Keuangan link should be visible after clicking Laporan menu")
                .isTrue();
    }

    @Test
    @DisplayName("Quick transaction form Alpine should initialize after HTMX load")
    void quickTransactionFormAlpineShouldInitialize() {
        // Login
        loginAsAdmin();
        waitForPageLoad();

        // Wait for Alpine.js to initialize
        page.waitForTimeout(500);
        consoleLogs.clear();
        consoleErrors.clear();

        // Click FAB button to open modal
        page.click("#quick-transaction-fab");
        page.waitForTimeout(1000);

        // Wait for templates to load via HTMX
        page.waitForTimeout(1000);

        // Click on first template if available
        var templates = page.locator("[data-testid^='template-frequent-'], [data-testid^='template-recent-']");
        if (templates.count() > 0) {
            templates.first().click();
            page.waitForTimeout(1000);

            // Check if form is visible
            boolean formVisible = page.locator("#quick-transaction-form").isVisible();
            System.out.println("\n=== QUICK TRANSACTION FORM DEBUG ===");
            System.out.println("Form visible: " + formVisible);

            // Check Alpine initialization
            var formElement = page.locator("#quick-transaction-form");
            System.out.println("Form x-data: " + formElement.getAttribute("x-data"));

            // Print all console logs
            if (!consoleLogs.isEmpty()) {
                System.out.println("\n=== CONSOLE LOGS ===");
                consoleLogs.forEach(l -> System.out.println("  " + l));
            }

            if (!consoleErrors.isEmpty()) {
                System.out.println("\n=== CONSOLE ERRORS ===");
                consoleErrors.forEach(e -> System.out.println("  " + e));
            }

            // Assert that form loaded without CSP errors
            List<String> cspErrors = consoleErrors.stream()
                    .filter(e -> e.toLowerCase().contains("csp") ||
                                e.toLowerCase().contains("content security policy"))
                    .toList();
            assertThat(cspErrors)
                    .as("Quick transaction form should load without CSP errors")
                    .isEmpty();
        } else {
            // No templates - verify modal at least opened without errors
            assertThat(consoleErrors.stream()
                    .filter(e -> e.toLowerCase().contains("alpine"))
                    .toList())
                    .as("Modal should open without Alpine.js errors")
                    .isEmpty();
        }
    }
}
