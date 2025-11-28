package com.artivisi.accountingfinance.functional.page;

import com.microsoft.playwright.Page;

import static org.assertj.core.api.Assertions.assertThat;

public class PayrollFormPage {
    private final Page page;
    private final String baseUrl;

    private static final String PAGE_TITLE = "#page-title";
    private static final String PERIOD_INPUT = "#period";
    private static final String BASE_SALARY_INPUT = "#baseSalary";
    private static final String JKK_RISK_CLASS_SELECT = "#jkkRiskClass";
    private static final String SUBMIT_BUTTON = "#btn-submit";

    public PayrollFormPage(Page page, String baseUrl) {
        this.page = page;
        this.baseUrl = baseUrl;
    }

    public PayrollFormPage navigateToNew() {
        page.navigate(baseUrl + "/payroll/new");
        return this;
    }

    public void assertPageTitleText(String expected) {
        assertThat(page.locator(PAGE_TITLE).textContent()).contains(expected);
    }

    public void fillPeriod(String period) {
        page.fill(PERIOD_INPUT, period);
    }

    public void fillBaseSalary(String salary) {
        page.fill(BASE_SALARY_INPUT, salary);
    }

    public void selectJkkRiskClass(int riskClass) {
        page.selectOption(JKK_RISK_CLASS_SELECT, String.valueOf(riskClass));
    }

    public void clickSubmit() {
        page.click(SUBMIT_BUTTON);
        page.waitForLoadState();
    }

    public boolean hasErrorMessage() {
        return page.locator(".bg-red-50").isVisible();
    }

    public String getErrorMessage() {
        return page.locator(".bg-red-50").textContent();
    }

    public boolean hasPeriodValidationError() {
        return page.locator("text=Payroll untuk periode ini sudah ada").isVisible();
    }
}
