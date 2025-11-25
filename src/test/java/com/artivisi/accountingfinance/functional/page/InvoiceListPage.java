package com.artivisi.accountingfinance.functional.page;

import com.microsoft.playwright.Page;

import static org.assertj.core.api.Assertions.assertThat;

public class InvoiceListPage {
    private final Page page;
    private final String baseUrl;

    private static final String PAGE_TITLE = "#page-title";
    private static final String INVOICE_LIST = "[data-testid='invoice-list']";
    private static final String INVOICE_TABLE = "[data-testid='invoice-table']";
    private static final String NEW_INVOICE_BUTTON = "#btn-new-invoice";

    public InvoiceListPage(Page page, String baseUrl) {
        this.page = page;
        this.baseUrl = baseUrl;
    }

    public InvoiceListPage navigate() {
        page.navigate(baseUrl + "/invoices");
        return this;
    }

    public void assertPageTitleVisible() {
        assertThat(page.locator(PAGE_TITLE).isVisible()).isTrue();
    }

    public void assertPageTitleText(String expected) {
        assertThat(page.locator(PAGE_TITLE).textContent()).contains(expected);
    }

    public void assertTableVisible() {
        assertThat(page.locator(INVOICE_TABLE).isVisible()).isTrue();
    }

    public void clickNewInvoiceButton() {
        page.click(NEW_INVOICE_BUTTON);
        page.waitForLoadState();
    }

    public boolean hasInvoiceWithNumber(String number) {
        page.waitForLoadState();
        return page.getByText(number).count() > 0;
    }

    public void clickInvoiceLink(String number) {
        page.click("a:has-text('" + number + "')");
        page.waitForLoadState();
    }

    public int getInvoiceCount() {
        return page.locator(INVOICE_TABLE + " tbody tr").count();
    }
}
