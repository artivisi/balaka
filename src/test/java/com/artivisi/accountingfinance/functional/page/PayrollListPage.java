package com.artivisi.accountingfinance.functional.page;

import com.microsoft.playwright.Page;

import static org.assertj.core.api.Assertions.assertThat;

public class PayrollListPage {
    private final Page page;
    private final String baseUrl;

    private static final String PAGE_TITLE = "#page-title";
    private static final String PAYROLL_TABLE = "table";
    private static final String NEW_PAYROLL_BUTTON = "#btn-new-payroll";
    private static final String STATUS_FILTER = "#status";

    public PayrollListPage(Page page, String baseUrl) {
        this.page = page;
        this.baseUrl = baseUrl;
    }

    public PayrollListPage navigate() {
        page.navigate(baseUrl + "/payroll");
        return this;
    }

    public void assertPageTitleVisible() {
        assertThat(page.locator(PAGE_TITLE).isVisible()).isTrue();
    }

    public void assertPageTitleText(String expected) {
        assertThat(page.locator(PAGE_TITLE).textContent()).contains(expected);
    }

    public void assertTableVisible() {
        assertThat(page.locator(PAYROLL_TABLE).isVisible()).isTrue();
    }

    public void clickNewPayrollButton() {
        page.click(NEW_PAYROLL_BUTTON);
        page.waitForLoadState();
    }

    public void filterByStatus(String status) {
        page.selectOption(STATUS_FILTER, status);
        page.click("button[type='submit']");
        page.waitForLoadState();
        page.waitForTimeout(300); // Allow filter to complete
    }

    public int getPayrollCount() {
        return page.locator(PAYROLL_TABLE + " tbody tr").count();
    }

    public boolean hasPayrollWithPeriod(String period) {
        return page.locator(PAYROLL_TABLE + " tbody tr:has-text('" + period + "')").count() > 0;
    }

    public void clickPayrollDetail(String period) {
        page.locator(PAYROLL_TABLE + " tbody tr:has-text('" + period + "') a:has-text('Detail')").click();
        page.waitForLoadState();
    }

    public boolean hasEmptyState() {
        return page.locator("text=Belum ada data payroll").isVisible();
    }
}
