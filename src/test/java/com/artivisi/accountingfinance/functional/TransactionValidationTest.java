package com.artivisi.accountingfinance.functional;

import com.microsoft.playwright.APIResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Transaction - Validation Tests (CSV Parameterized)")
class TransactionValidationTest extends FunctionalTestBase {

    @BeforeEach
    void setUp() {
        loginAsAdmin();
    }

    @ParameterizedTest(name = "{0}: Should show validation error")
    @CsvFileSource(resources = "/testdata/transaction_validation.csv", numLinesToSkip = 1)
    @DisplayName("API Validation")
    void shouldValidateTransactionViaAPI(
            String testCase,
            String transactionDate,
            String templateId,
            String amount,
            String description,
            String referenceNumber,
            String expectedError,
            String errorField) {

        String requestBody = buildRequestBody(transactionDate, templateId, amount, description, referenceNumber);

        APIResponse response = apiPost("/transactions/api", requestBody);

        // Should return 400 for validation errors or 404 for not found
        assertTrue(response.status() >= 400,
                "Test case: " + testCase + " should return error status");

        String body = response.text();
        assertTrue(body.toLowerCase().contains("error") ||
                   body.toLowerCase().contains("validation") ||
                   body.toLowerCase().contains("not found") ||
                   body.toLowerCase().contains("required"),
                "Test case: " + testCase + " - Response should contain error message");
    }

    @ParameterizedTest(name = "{0}: Should show form validation error")
    @CsvFileSource(resources = "/testdata/transaction_validation.csv", numLinesToSkip = 1)
    @DisplayName("UI Form Validation")
    void shouldValidateTransactionForm(
            String testCase,
            String transactionDate,
            String templateId,
            String amount,
            String description,
            String referenceNumber,
            String expectedError,
            String errorField) {

        // Skip test cases that involve invalid/non-existent template IDs for UI test
        if (templateId == null || templateId.isEmpty() || templateId.equals("00000000-0000-0000-0000-000000000000")) {
            navigateTo("/transactions/new");
        } else {
            navigateTo("/transactions/new?templateId=" + templateId);
        }

        // Fill form with test data
        if (transactionDate != null && !transactionDate.trim().isEmpty()) {
            clearAndFill("input[name='transactionDate']", transactionDate);
        }
        if (amount != null && !amount.trim().isEmpty()) {
            clearAndFill("input[name='amount']", amount);
        }
        if (description != null) {
            clearAndFill("input[name='description']", description);
        }
        if (referenceNumber != null && !referenceNumber.trim().isEmpty()) {
            fillForm("input[name='referenceNumber']", referenceNumber);
        }

        submitForm();

        // Verify validation error is shown
        boolean hasValidationError = page.locator("input:invalid, select:invalid, .error, .alert-danger, [role='alert']").count() > 0 ||
                                     page.content().toLowerCase().contains(expectedError.toLowerCase()) ||
                                     page.content().toLowerCase().contains("error") ||
                                     page.content().toLowerCase().contains("required");

        assertTrue(hasValidationError,
                "Test case: " + testCase + " - Should show validation error");

        takeScreenshot("trx-validation-" + sanitizeTestName(testCase));
    }

    private String buildRequestBody(String transactionDate, String templateId, String amount,
                                   String description, String referenceNumber) {
        StringBuilder json = new StringBuilder("{");

        if (transactionDate != null && !transactionDate.isEmpty()) {
            json.append("\"transactionDate\":\"").append(transactionDate).append("\",");
        }
        if (templateId != null && !templateId.isEmpty()) {
            json.append("\"templateId\":\"").append(templateId).append("\",");
        }
        if (amount != null && !amount.isEmpty()) {
            json.append("\"amount\":").append(amount).append(",");
        }
        if (description != null) {
            json.append("\"description\":\"").append(escapeJson(description)).append("\",");
        }
        if (referenceNumber != null && !referenceNumber.isEmpty()) {
            json.append("\"referenceNumber\":\"").append(escapeJson(referenceNumber)).append("\"");
        }

        // Remove trailing comma if present
        String result = json.toString();
        if (result.endsWith(",")) {
            result = result.substring(0, result.length() - 1);
        }
        result += "}";

        return result;
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    private String sanitizeTestName(String testCase) {
        return testCase.toLowerCase()
                      .replaceAll("[^a-z0-9]", "-")
                      .replaceAll("-+", "-")
                      .replaceAll("^-|-$", "");
    }
}
