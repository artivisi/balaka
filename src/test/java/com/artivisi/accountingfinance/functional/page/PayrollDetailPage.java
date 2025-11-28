package com.artivisi.accountingfinance.functional.page;

import com.microsoft.playwright.Page;

import static org.assertj.core.api.Assertions.assertThat;

public class PayrollDetailPage {
    private final Page page;
    private final String baseUrl;

    private static final String PAGE_TITLE = "#page-title";
    private static final String APPROVE_BUTTON = "#btn-approve";
    private static final String CANCEL_BUTTON = "#btn-cancel";
    private static final String DELETE_BUTTON = "#btn-delete";
    private static final String EMPLOYEE_COUNT = "[data-testid='employee-count']";
    private static final String TOTAL_GROSS = "[data-testid='total-gross']";
    private static final String TOTAL_DEDUCTIONS = "[data-testid='total-deductions']";
    private static final String TOTAL_NET = "[data-testid='total-net']";
    private static final String SUCCESS_MESSAGE = "[data-testid='success-message']";
    private static final String STATUS_BADGE = "[data-testid='status-badge']";
    private static final String EMPLOYEE_DETAILS_TABLE = "[data-testid='employee-details-table']";

    public PayrollDetailPage(Page page, String baseUrl) {
        this.page = page;
        this.baseUrl = baseUrl;
    }

    public void assertPageTitleContains(String expected) {
        assertThat(page.locator(PAGE_TITLE).textContent()).contains(expected);
    }

    public void assertStatusBadgeText(String expected) {
        assertThat(page.locator(STATUS_BADGE).textContent()).contains(expected);
    }

    public String getEmployeeCount() {
        return page.locator(EMPLOYEE_COUNT).textContent();
    }

    public String getTotalGross() {
        return page.locator(TOTAL_GROSS).textContent();
    }

    public String getTotalDeductions() {
        return page.locator(TOTAL_DEDUCTIONS).textContent();
    }

    public String getTotalNet() {
        return page.locator(TOTAL_NET).textContent();
    }

    public boolean hasApproveButton() {
        return page.locator(APPROVE_BUTTON).isVisible();
    }

    public boolean hasCancelButton() {
        return page.locator(CANCEL_BUTTON).isVisible();
    }

    public boolean hasDeleteButton() {
        return page.locator(DELETE_BUTTON).isVisible();
    }

    public void clickApproveButton() {
        page.click(APPROVE_BUTTON);
        page.waitForLoadState();
    }

    public void clickCancelButton() {
        // Handle confirmation dialog
        page.onceDialog(dialog -> dialog.accept());
        page.click(CANCEL_BUTTON);
        page.waitForLoadState();
    }

    public void clickDeleteButton() {
        // Handle confirmation dialog
        page.onceDialog(dialog -> dialog.accept());
        page.click(DELETE_BUTTON);
        page.waitForLoadState();
    }

    public boolean hasSuccessMessage() {
        return page.locator(SUCCESS_MESSAGE).isVisible();
    }

    public String getSuccessMessage() {
        return page.locator(SUCCESS_MESSAGE).textContent();
    }

    public boolean hasEmployeeDetailsTable() {
        return page.locator(EMPLOYEE_DETAILS_TABLE).isVisible();
    }

    public int getEmployeeRowCount() {
        return page.locator(EMPLOYEE_DETAILS_TABLE + " tbody tr").count();
    }

    public boolean hasEmployeeWithId(String employeeId) {
        return page.locator(EMPLOYEE_DETAILS_TABLE + " tbody tr:has-text('" + employeeId + "')").count() > 0;
    }
}
