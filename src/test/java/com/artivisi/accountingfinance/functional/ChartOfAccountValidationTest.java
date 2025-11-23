package com.artivisi.accountingfinance.functional;

import com.microsoft.playwright.APIResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Chart of Accounts - Validation Tests (CSV Parameterized)")
class ChartOfAccountValidationTest extends FunctionalTestBase {

    @BeforeEach
    void setUp() {
        loginAsAdmin();
    }

    @ParameterizedTest(name = "{0}: Should show validation error")
    @CsvFileSource(resources = "/testdata/chart_of_account_validation.csv", numLinesToSkip = 1)
    @DisplayName("API Validation")
    void shouldValidateChartOfAccountViaAPI(
            String testCase,
            String accountCode,
            String accountName,
            String accountType,
            String normalBalance,
            String expectedError,
            String errorField) {

        String requestBody = buildRequestBody(accountCode, accountName, accountType, normalBalance);

        APIResponse response = apiPost("/accounts/api", requestBody);

        // Should return 400 for validation errors
        assertEquals(400, response.status(),
                "Test case: " + testCase + " should return 400");

        String body = response.text();
        assertTrue(body.toLowerCase().contains(expectedError.toLowerCase()) ||
                   body.toLowerCase().contains("error") ||
                   body.toLowerCase().contains("validation"),
                "Test case: " + testCase + " - Response should contain error message");
    }

    @ParameterizedTest(name = "{0}: Should show form validation error")
    @CsvFileSource(resources = "/testdata/chart_of_account_validation.csv", numLinesToSkip = 1)
    @DisplayName("UI Form Validation")
    void shouldValidateChartOfAccountForm(
            String testCase,
            String accountCode,
            String accountName,
            String accountType,
            String normalBalance,
            String expectedError,
            String errorField) {

        navigateTo("/accounts/new");

        // Fill form with test data
        if (accountCode != null && !accountCode.trim().isEmpty()) {
            fillForm("input[name='accountCode']", accountCode);
        }
        if (accountName != null && !accountName.trim().isEmpty()) {
            fillForm("input[name='accountName']", accountName);
        }
        if (accountType != null && !accountType.trim().isEmpty() && isValidEnum(accountType)) {
            selectOption("select[name='accountType']", accountType);
        }
        if (normalBalance != null && !normalBalance.trim().isEmpty() && isValidNormalBalance(normalBalance)) {
            selectOption("select[name='normalBalance']", normalBalance);
        }

        submitForm();

        // Verify validation error is shown
        // Either the field shows :invalid state or error message is displayed
        boolean hasValidationError = page.locator("input:invalid, select:invalid, .error, .alert-danger").count() > 0 ||
                                     page.content().toLowerCase().contains(expectedError.toLowerCase());

        assertTrue(hasValidationError,
                "Test case: " + testCase + " - Should show validation error");

        takeScreenshot("coa-validation-" + sanitizeTestName(testCase));
    }

    private String buildRequestBody(String accountCode, String accountName, String accountType, String normalBalance) {
        StringBuilder json = new StringBuilder("{");

        if (accountCode != null) {
            json.append("\"accountCode\":\"").append(escapeJson(accountCode)).append("\",");
        }
        if (accountName != null) {
            json.append("\"accountName\":\"").append(escapeJson(accountName)).append("\",");
        }
        if (accountType != null && !accountType.isEmpty()) {
            json.append("\"accountType\":\"").append(accountType).append("\",");
        }
        if (normalBalance != null && !normalBalance.isEmpty()) {
            json.append("\"normalBalance\":\"").append(normalBalance).append("\",");
        }

        json.append("\"isHeader\":false,");
        json.append("\"active\":true");
        json.append("}");

        return json.toString();
    }

    private boolean isValidEnum(String value) {
        return value != null && (value.equals("ASSET") || value.equals("LIABILITY") ||
                value.equals("EQUITY") || value.equals("REVENUE") || value.equals("EXPENSE"));
    }

    private boolean isValidNormalBalance(String value) {
        return value != null && (value.equals("DEBIT") || value.equals("CREDIT"));
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
