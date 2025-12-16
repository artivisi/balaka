package com.artivisi.accountingfinance.functional.page;

import com.microsoft.playwright.Page;

/**
 * Page Object for Dashboard page.
 */
public class DashboardPage {
    private final Page page;
    private final String baseUrl;

    public DashboardPage(Page page, String baseUrl) {
        this.page = page;
        this.baseUrl = baseUrl;
    }

    /**
     * Navigate to dashboard page.
     */
    public void navigate() {
        page.navigate(baseUrl + "/dashboard");
        page.waitForLoadState();
    }

    /**
     * Check if on dashboard page.
     */
    public boolean isOnDashboard() {
        return page.locator("#page-title").textContent().contains("Dashboard");
    }
}
