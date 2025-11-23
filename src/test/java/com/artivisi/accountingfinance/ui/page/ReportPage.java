package com.artivisi.accountingfinance.ui.page;

import com.microsoft.playwright.Page;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

public class ReportPage extends BasePage {

    public ReportPage(Page page, String baseUrl) {
        super(page, baseUrl);
    }

    public void navigateToTrialBalance() {
        page.navigate(baseUrl + "/reports/trial-balance");
        waitForPageLoad();
    }

    public void navigateToIncomeStatement() {
        page.navigate(baseUrl + "/reports/income-statement");
        waitForPageLoad();
    }

    public void navigateToBalanceSheet() {
        page.navigate(baseUrl + "/reports/balance-sheet");
        waitForPageLoad();
    }

    public void navigateToCashFlow() {
        page.navigate(baseUrl + "/reports/cash-flow");
        waitForPageLoad();
    }

    public void assertPageLoaded() {
        assertElementVisible("page-title");
        assertElementVisible("report-content");
    }

    public void selectPeriod(String year, String month) {
        selectOptionById("filter-year", year);
        selectOptionById("filter-month", month);
        clickById("btn-filter");
    }

    public void selectDateRange(String startDate, String endDate) {
        fillById("filter-start-date", startDate);
        fillById("filter-end-date", endDate);
        clickById("btn-filter");
    }

    public void exportToPdf() {
        clickById("btn-export-pdf");
    }

    public void exportToExcel() {
        clickById("btn-export-excel");
    }

    public void print() {
        clickById("btn-print");
    }

    public void assertReportTableVisible() {
        assertThat(page.locator("#report-content table")).isVisible();
    }

    public void assertTotalDebitEqualsCredit() {
        String debitTotal = page.locator("#total-debit").textContent();
        String creditTotal = page.locator("#total-credit").textContent();
        assertThat(page.locator("#total-debit")).hasText(debitTotal);
        assertThat(page.locator("#total-credit")).hasText(creditTotal);
    }

    public void assertReportContains(String text) {
        assertThat(page.locator("#report-content")).containsText(text);
    }
}
