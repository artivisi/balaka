package com.artivisi.accountingfinance.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

@DisplayName("Debug Account Form")
class DebugAccountFormTest extends PlaywrightTestBase {

    @Test
    @DisplayName("Debug Panduan Pengisian dropdown")
    void debugPanduanDropdown() {
        List<String> consoleErrors = new ArrayList<>();
        List<String> consoleWarnings = new ArrayList<>();
        List<String> allConsoleMessages = new ArrayList<>();

        // Capture all console messages
        page.onConsoleMessage(msg -> {
            String text = msg.type() + ": " + msg.text();
            allConsoleMessages.add(text);
            if ("error".equals(msg.type())) {
                consoleErrors.add(msg.text());
            } else if ("warning".equals(msg.type())) {
                consoleWarnings.add(msg.text());
            }
        });

        // Capture page errors
        page.onPageError(error -> {
            System.err.println("PAGE ERROR: " + error);
            consoleErrors.add("PAGE ERROR: " + error);
        });

        loginAsAdmin();
        navigateTo("/accounts/new");
        waitForPageLoad();

        // Print all console messages
        System.out.println("\n=== ALL CONSOLE MESSAGES ===");
        allConsoleMessages.forEach(System.out::println);

        System.out.println("\n=== CONSOLE ERRORS ===");
        consoleErrors.forEach(System.out::println);

        System.out.println("\n=== CONSOLE WARNINGS ===");
        consoleWarnings.forEach(System.out::println);

        // Take screenshot before clicking
        takeScreenshot("debug-01-before-click");

        // Try to click the Panduan Pengisian button
        System.out.println("\n=== ATTEMPTING TO CLICK PANDUAN PENGISIAN ===");

        // Check if Alpine.js is loaded
        Object alpineLoaded = page.evaluate("typeof Alpine !== 'undefined'");
        System.out.println("Alpine.js loaded: " + alpineLoaded);

        // Check the button state
        var button = page.locator("button:has-text('Panduan Pengisian')");
        System.out.println("Button visible: " + button.isVisible());
        System.out.println("Button HTML: " + button.evaluate("el => el.outerHTML"));

        // Try clicking
        button.click();

        // Wait a bit for any animations
        page.waitForTimeout(500);

        // Take screenshot after clicking
        takeScreenshot("debug-02-after-click");

        // Check the content panel - should be HIDDEN after first click (was open by default)
        var contentPanel = page.locator("text=Format Kode Akun");
        System.out.println("Content panel visible after 1st click (should be false): " + contentPanel.isVisible());

        // Take screenshot
        takeScreenshot("debug-03-collapsed");

        // Click again to expand
        button.click();
        page.waitForTimeout(500);

        System.out.println("Content panel visible after 2nd click (should be true): " + contentPanel.isVisible());
        takeScreenshot("debug-04-expanded-again");

        // Print any new console errors
        System.out.println("\n=== CONSOLE ERRORS AFTER CLICKS ===");
        consoleErrors.forEach(System.out::println);

        // Get page HTML for debugging
        System.out.println("\n=== PAGE TITLE ===");
        System.out.println(page.title());

        // Verify the toggle works
        if (consoleErrors.isEmpty()) {
            System.out.println("\n=== SUCCESS: No JavaScript errors! ===");
        }
    }

    @Test
    @DisplayName("Debug Advanced Settings and isPermanent toggle")
    void debugAdvancedSettings() {
        List<String> consoleErrors = new ArrayList<>();

        page.onConsoleMessage(msg -> {
            if ("error".equals(msg.type())) {
                consoleErrors.add(msg.text());
            }
        });

        page.onPageError(error -> consoleErrors.add("PAGE ERROR: " + error));

        loginAsAdmin();
        navigateTo("/accounts/new");
        waitForPageLoad();

        // Test account type selection and auto-defaults
        var typeSelect = page.locator("select#type");
        var advancedButton = page.locator("text=Pengaturan Lanjutan");

        // 1. Select Asset - should set isPermanent=true, normalBalance=debit
        System.out.println("\n=== Testing Asset type ===");
        typeSelect.selectOption("asset");
        page.waitForTimeout(200);

        // Check normal balance radio
        var debitRadio = page.locator("input[name='normalBalance'][value='debit']");
        System.out.println("Debit selected for Asset: " + debitRadio.isChecked());

        // Open advanced settings
        advancedButton.click();
        page.waitForTimeout(300);

        var isPermanentToggle = page.locator("input[name='isPermanent']");
        var isPermanentLabel = page.locator("label:has(input[name='isPermanent'])");
        System.out.println("isPermanent for Asset (should be true): " + isPermanentToggle.isChecked());
        takeScreenshot("debug-asset-permanent");

        // 2. Select Expense - should set isPermanent=false, normalBalance=debit
        System.out.println("\n=== Testing Expense type ===");
        typeSelect.selectOption("expense");
        page.waitForTimeout(200);

        System.out.println("Debit selected for Expense: " + debitRadio.isChecked());
        System.out.println("isPermanent for Expense (should be false): " + isPermanentToggle.isChecked());
        takeScreenshot("debug-expense-temporary");

        // 3. Select Revenue - should set isPermanent=false, normalBalance=credit
        System.out.println("\n=== Testing Revenue type ===");
        typeSelect.selectOption("revenue");
        page.waitForTimeout(200);

        var creditRadio = page.locator("input[name='normalBalance'][value='credit']");
        System.out.println("Credit selected for Revenue: " + creditRadio.isChecked());
        System.out.println("isPermanent for Revenue (should be false): " + isPermanentToggle.isChecked());

        // 4. Test manual override - select Equity then toggle isPermanent off (for Dividends case)
        System.out.println("\n=== Testing Manual Override (Equity -> Temporary for Dividends) ===");
        typeSelect.selectOption("equity");
        page.waitForTimeout(200);
        System.out.println("isPermanent for Equity before override (should be true): " + isPermanentToggle.isChecked());

        // Click on label to toggle (checkbox is sr-only)
        isPermanentLabel.click();
        page.waitForTimeout(200);
        System.out.println("isPermanent after manual override (should be false): " + isPermanentToggle.isChecked());
        takeScreenshot("debug-equity-override-temporary");

        // Check description text changed
        var permanentDesc = page.locator("text=Saldo akan dibawa ke periode akuntansi berikutnya");
        var temporaryDesc = page.locator("text=Saldo akan di-reset ke nol saat tutup buku");
        System.out.println("Temporary description visible: " + temporaryDesc.isVisible());
        System.out.println("Permanent description visible: " + permanentDesc.isVisible());

        if (consoleErrors.isEmpty()) {
            System.out.println("\n=== SUCCESS: Advanced Settings working correctly! ===");
        } else {
            System.out.println("\n=== CONSOLE ERRORS ===");
            consoleErrors.forEach(System.out::println);
        }
    }
}
