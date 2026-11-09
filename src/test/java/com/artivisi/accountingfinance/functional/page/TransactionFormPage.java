package com.artivisi.accountingfinance.functional.page;

import com.microsoft.playwright.Page;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Page Object for Transaction Form (/transactions/new).
 * Handles creating new journal transactions via UI.
 */
public class TransactionFormPage {

    private final Page page;
    private final String baseUrl;

    // Locators - using IDs only
    private static final String TRANSACTION_DATE = "#transactionDate";
    private static final String AMOUNT = "#amount";
    private static final String DESCRIPTION = "#description";
    private static final String REFERENCE_NUMBER = "#referenceNumber";
    private static final String PROJECT = "#idProject";
    private static final String BTN_SAVE_POST = "#btn-simpan-posting";
    private static final String BTN_SAVE_DRAFT = "#btn-simpan-draft";

    public TransactionFormPage(Page page, String baseUrl) {
        this.page = page;
        this.baseUrl = baseUrl;
    }

    /**
     * Navigate to new transaction form with specific template.
     */
    public TransactionFormPage navigateWithTemplate(UUID templateId) {
        page.navigate(baseUrl + "/transactions/new?templateId=" + templateId);
        page.waitForLoadState();
        return this;
    }

    /**
     * Fill transaction date.
     */
    public TransactionFormPage fillDate(String date) {
        page.locator(TRANSACTION_DATE).fill(date);
        return this;
    }

    /**
     * Fill amount for SIMPLE template.
     */
    public TransactionFormPage fillAmount(String amount) {
        page.locator(AMOUNT).fill(amount);
        return this;
    }

    /**
     * Fill description.
     */
    public TransactionFormPage fillDescription(String description) {
        page.locator(DESCRIPTION).fill(description);
        return this;
    }

    /**
     * Fill reference number.
     */
    public TransactionFormPage fillReferenceNumber(String reference) {
        if (reference != null && !reference.isEmpty()) {
            page.locator(REFERENCE_NUMBER).fill(reference);
        }
        return this;
    }

    /**
     * Select project by ID.
     */
    public TransactionFormPage selectProject(String projectId) {
        if (projectId != null && !projectId.isEmpty()) {
            page.locator(PROJECT).selectOption(new String[]{projectId});
        }
        return this;
    }

    /**
     * Fill a DETAILED template variable by ID.
     */
    public TransactionFormPage fillVariable(String variableId, String value) {
        page.locator("#" + variableId).fill(value);
        return this;
    }

    /**
     * Select an account by exact code in the template-line picker with input id
     * accountMapping_<lineId>.
     */
    public TransactionFormPage selectAccountMapping(String mappingId, String accountCode) {
        AccountPicker.select(page, page.locator("#" + mappingId), accountCode);
        return this;
    }

    /**
     * Fill inputs from a pipe-separated string.
     * Format: "field1:value1|field2:value2|..."
     * Account hints: "BANK:1.1.02" selects account 1.1.02 in the picker whose label contains BANK.
     */
    public TransactionFormPage fillInputs(String inputs) {
        Map<String, String> inputMap = parseInputs(inputs);
        for (var entry : inputMap.entrySet()) {
            String fieldId = entry.getKey();
            String value = entry.getValue();

            if (fieldId.startsWith("accountMapping")) {
                selectAccountMapping(fieldId, value);
            } else if (fieldId.equals("amount")) {
                fillAmount(value);
            } else if (fieldId.startsWith("var_")) {
                page.locator("#" + fieldId).fill(value);
            } else if (isAccountHint(fieldId)) {
                selectAccountByHint(fieldId, value);
            } else {
                throw new IllegalArgumentException("Unknown transaction input '" + fieldId + "' in: " + inputs);
            }
        }
        return this;
    }

    private boolean isAccountHint(String fieldId) {
        return fieldId.equals("BANK") || fieldId.equals("PENDAPATAN") ||
               fieldId.equals("BEBAN") || fieldId.equals("PIUTANG") ||
               fieldId.equals("HUTANG");
    }

    /**
     * Select the account in the template-line picker whose label contains the hint.
     */
    private void selectAccountByHint(String hint, String accountCode) {
        for (var input : page.locator("input[id^='accountMapping_']").all()) {
            String inputId = input.getAttribute("id");
            String labelText = page.locator("label[for='" + inputId + "']").textContent();
            if (labelText.contains(hint)) {
                AccountPicker.select(page, input, accountCode);
                return;
            }
        }
        throw new IllegalStateException("No account picker labelled with hint '" + hint + "' on " + page.url());
    }

    /**
     * Click save and post button.
     * Returns TransactionDetailPage after redirect.
     */
    public TransactionDetailPage saveAndPost() {
        // Wait for Alpine.js to initialize and button to be enabled
        page.waitForTimeout(500);
        var btn = page.locator(BTN_SAVE_POST);
        btn.waitFor();
        btn.click();
        // Wait for redirect to detail page (UUID pattern, not /new)
        page.waitForURL(java.util.regex.Pattern.compile(".*/transactions/[0-9a-f]{8}-[0-9a-f]{4}-.*"));
        page.waitForLoadState();
        return new TransactionDetailPage(page, baseUrl);
    }

    /**
     * Click save as draft button.
     * Returns TransactionDetailPage after redirect.
     */
    public TransactionDetailPage saveAsDraft() {
        page.locator(BTN_SAVE_DRAFT).click();
        // Wait for redirect to detail page (UUID pattern, not /new)
        page.waitForURL(java.util.regex.Pattern.compile(".*/transactions/[0-9a-f]{8}-[0-9a-f]{4}-.*"));
        page.waitForLoadState();
        return new TransactionDetailPage(page, baseUrl);
    }

    /**
     * Take screenshot of the form.
     */
    public TransactionFormPage takeScreenshot(String path) {
        page.screenshot(new Page.ScreenshotOptions().setPath(java.nio.file.Paths.get(path)));
        return this;
    }

    /**
     * Parse inputs string from CSV into key-value map.
     */
    private Map<String, String> parseInputs(String inputs) {
        Map<String, String> result = new LinkedHashMap<>();
        if (inputs == null || inputs.isEmpty()) {
            return result;
        }

        for (String pair : inputs.split("\\|")) {
            String[] kv = pair.split(":", 2);
            if (kv.length != 2) {
                throw new IllegalArgumentException("Malformed transaction input '" + pair + "' in: " + inputs);
            }
            result.put(kv[0].trim(), kv[1].trim());
        }
        return result;
    }

    /**
     * Wait for preview to load after amount change.
     * HTMX loads the preview asynchronously.
     */
    public TransactionFormPage waitForPreviewUpdate() {
        // Wait for HTMX to finish loading
        page.waitForTimeout(1000);
        page.locator("#total-debit").waitFor();
        return this;
    }

    /**
     * Get the total debit amount from preview.
     * Returns the numeric value parsed from "Rp X.XXX.XXX" format.
     */
    public long getPreviewTotalDebit() {
        String text = page.locator("#total-debit").textContent();
        return parseRupiahAmount(text);
    }

    /**
     * Get the total credit amount from preview.
     * Returns the numeric value parsed from "Rp X.XXX.XXX" format.
     */
    public long getPreviewTotalCredit() {
        String text = page.locator("#total-credit").textContent();
        return parseRupiahAmount(text);
    }

    /**
     * Parse Rupiah formatted amount to long.
     * "Rp 3.333.333" -> 3333333
     */
    private long parseRupiahAmount(String text) {
        return Long.parseLong(text.replace("Rp", "").replace(".", "").trim());
    }
}
