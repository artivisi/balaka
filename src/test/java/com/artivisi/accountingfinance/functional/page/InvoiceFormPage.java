package com.artivisi.accountingfinance.functional.page;

import com.microsoft.playwright.Page;

import static org.assertj.core.api.Assertions.assertThat;

public class InvoiceFormPage {
    private final Page page;
    private final String baseUrl;

    private static final String PAGE_TITLE = "#page-title";
    private static final String INVOICE_NUMBER_INPUT = "#invoiceNumber";
    private static final String CLIENT_SELECT = "#client";
    private static final String PROJECT_SELECT = "#project";
    private static final String INVOICE_DATE_INPUT = "#invoiceDate";
    private static final String DUE_DATE_INPUT = "#dueDate";
    private static final String AMOUNT_INPUT = "#amount";
    private static final String NOTES_INPUT = "#notes";
    private static final String SUBMIT_BUTTON = "#btn-simpan";

    public InvoiceFormPage(Page page, String baseUrl) {
        this.page = page;
        this.baseUrl = baseUrl;
    }

    public InvoiceFormPage navigateToNew() {
        page.navigate(baseUrl + "/invoices/new");
        return this;
    }

    public void assertPageTitleText(String expected) {
        assertThat(page.locator(PAGE_TITLE).textContent()).contains(expected);
    }

    public void fillInvoiceNumber(String number) {
        page.fill(INVOICE_NUMBER_INPUT, number);
    }

    public void selectClient(String clientName) {
        page.selectOption(CLIENT_SELECT, new com.microsoft.playwright.options.SelectOption().setLabel(clientName));
    }

    public void selectClientByIndex(int index) {
        page.locator(CLIENT_SELECT).selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(index));
    }

    public void selectProject(String projectName) {
        page.selectOption(PROJECT_SELECT, new com.microsoft.playwright.options.SelectOption().setLabel(projectName));
    }

    public void fillInvoiceDate(String date) {
        page.fill(INVOICE_DATE_INPUT, date);
    }

    public void fillDueDate(String date) {
        page.fill(DUE_DATE_INPUT, date);
    }

    public void fillAmount(String amount) {
        page.fill(AMOUNT_INPUT, amount);
    }

    public void fillNotes(String notes) {
        page.fill(NOTES_INPUT, notes);
    }

    public void clickSubmit() {
        page.click(SUBMIT_BUTTON);
        page.waitForLoadState();
    }
}
