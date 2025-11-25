package com.artivisi.accountingfinance.functional.page;

import com.microsoft.playwright.Page;

import static org.assertj.core.api.Assertions.assertThat;

public class InvoiceDetailPage {
    private final Page page;
    private final String baseUrl;

    private static final String PAGE_TITLE = "#page-title";
    private static final String INVOICE_DETAIL = "[data-testid='invoice-detail']";
    private static final String SEND_BUTTON = "button:has-text('Kirim')";
    private static final String MARK_PAID_BUTTON = "button:has-text('Tandai Lunas')";
    private static final String CANCEL_BUTTON = "button:has-text('Batalkan')";
    private static final String DELETE_BUTTON = "button:has-text('Hapus')";
    private static final String EDIT_LINK = "a:has-text('Edit')";

    public InvoiceDetailPage(Page page, String baseUrl) {
        this.page = page;
        this.baseUrl = baseUrl;
    }

    public InvoiceDetailPage navigate(String invoiceId) {
        page.navigate(baseUrl + "/invoices/" + invoiceId);
        return this;
    }

    public void assertPageTitleVisible() {
        assertThat(page.locator(PAGE_TITLE).isVisible()).isTrue();
    }

    public void assertInvoiceNumberText(String expected) {
        page.waitForLoadState();
        assertThat(page.locator(INVOICE_DETAIL + " h2").textContent()).contains(expected);
    }

    public void assertStatusText(String expected) {
        page.waitForLoadState();
        assertThat(page.locator(INVOICE_DETAIL).textContent()).contains(expected);
    }

    public void clickSendButton() {
        page.onceDialog(dialog -> dialog.accept());
        page.click(SEND_BUTTON);
        page.waitForLoadState();
    }

    public void clickMarkPaidButton() {
        page.onceDialog(dialog -> dialog.accept());
        page.click(MARK_PAID_BUTTON);
        page.waitForLoadState();
    }

    public void clickCancelButton() {
        page.onceDialog(dialog -> dialog.accept());
        page.click(CANCEL_BUTTON);
        page.waitForLoadState();
    }

    public void clickDeleteButton() {
        page.onceDialog(dialog -> dialog.accept());
        page.click(DELETE_BUTTON);
        page.waitForLoadState();
    }

    public boolean hasSendButton() {
        return page.locator(SEND_BUTTON).count() > 0;
    }

    public boolean hasMarkPaidButton() {
        return page.locator(MARK_PAID_BUTTON).count() > 0;
    }

    public boolean hasCancelButton() {
        return page.locator(CANCEL_BUTTON).count() > 0;
    }

    public boolean hasDeleteButton() {
        return page.locator(DELETE_BUTTON).count() > 0;
    }

    public boolean hasEditLink() {
        return page.locator(EDIT_LINK).count() > 0;
    }
}
