package com.artivisi.accountingfinance.ui.page;

import com.microsoft.playwright.Page;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

public class TransactionFormPage extends BasePage {

    public TransactionFormPage(Page page, String baseUrl) {
        super(page, baseUrl);
    }

    public void assertPageLoaded() {
        assertElementVisible("form-transaksi");
    }

    public void selectTemplate(String templateId) {
        selectOptionById("journalTemplate", templateId);
    }

    public void fillTransactionDate(String date) {
        fillById("transactionDate", date);
    }

    public void fillAmount(String amount) {
        fillById("amount", amount);
    }

    public void fillDescription(String description) {
        fillById("description", description);
    }

    public void fillReferenceNumber(String refNumber) {
        fillById("referenceNumber", refNumber);
    }

    public void fillNotes(String notes) {
        fillById("notes", notes);
    }

    public void selectAccount(String lineId, String accountId) {
        selectOptionById("account-" + lineId, accountId);
    }

    public void fillLineAmount(String lineId, String amount) {
        fillById("amount-" + lineId, amount);
    }

    public TransactionsListPage saveDraft() {
        clickById("btn-simpan-draft");
        return new TransactionsListPage(page, baseUrl);
    }

    public TransactionsListPage saveAndPost() {
        clickById("btn-simpan-post");
        return new TransactionsListPage(page, baseUrl);
    }

    public TransactionsListPage cancel() {
        clickById("btn-batal");
        return new TransactionsListPage(page, baseUrl);
    }

    public void postTransaction() {
        clickById("btn-post");
    }

    public void voidTransaction() {
        clickById("btn-void");
    }

    public void fillVoidReason(String reason) {
        selectOptionById("void-reason", reason);
    }

    public void fillVoidNotes(String notes) {
        fillById("void-notes", notes);
    }

    public void confirmVoid() {
        clickById("btn-confirm-void");
    }

    public void assertValidationErrorVisible() {
        assertThat(page.locator(".invalid-feedback:visible")).isVisible();
    }

    public void assertSuccessMessageVisible() {
        assertThat(page.locator(".alert-success")).isVisible();
    }

    public void assertJournalEntriesVisible() {
        assertElementVisible("journal-entries");
    }

    public void assertTransactionStatus(String status) {
        assertThat(page.locator("#transaction-status")).containsText(status);
    }
}
