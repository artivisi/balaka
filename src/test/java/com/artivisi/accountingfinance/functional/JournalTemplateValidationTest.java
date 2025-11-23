package com.artivisi.accountingfinance.functional;

import com.microsoft.playwright.APIResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Journal Template - Validation Tests (CSV Parameterized)")
class JournalTemplateValidationTest extends FunctionalTestBase {

    @BeforeEach
    void setUp() {
        loginAsAdmin();
    }

    @ParameterizedTest(name = "{0}: Should show validation error")
    @CsvFileSource(resources = "/testdata/journal_template_validation.csv", numLinesToSkip = 1)
    @DisplayName("API Validation")
    void shouldValidateJournalTemplateViaAPI(
            String testCase,
            String templateName,
            String category,
            String cashFlowCategory,
            String templateType,
            String description,
            String expectedError,
            String errorField) {

        String requestBody = buildRequestBody(testCase, templateName, category, cashFlowCategory, templateType, description);

        APIResponse response = apiPost("/templates/api", requestBody);

        // Should return 400 for validation errors
        assertEquals(400, response.status(),
                "Test case: " + testCase + " should return 400");

        String body = response.text();
        assertTrue(body.toLowerCase().contains("error") ||
                   body.toLowerCase().contains("validation") ||
                   body.toLowerCase().contains("required") ||
                   body.toLowerCase().contains("must"),
                "Test case: " + testCase + " - Response should contain error message");
    }

    @ParameterizedTest(name = "{0}: Should show form validation error")
    @CsvFileSource(resources = "/testdata/journal_template_validation.csv", numLinesToSkip = 1)
    @DisplayName("UI Form Validation")
    void shouldValidateJournalTemplateForm(
            String testCase,
            String templateName,
            String category,
            String cashFlowCategory,
            String templateType,
            String description,
            String expectedError,
            String errorField) {

        navigateTo("/templates/new");

        // Fill form with test data
        if (templateName != null) {
            fillForm("input[name='templateName']", templateName);
        }
        if (category != null && !category.isEmpty() && isValidCategory(category)) {
            selectOption("select[name='category']", category);
        }
        if (cashFlowCategory != null && !cashFlowCategory.isEmpty() && isValidCashFlowCategory(cashFlowCategory)) {
            selectOption("select[name='cashFlowCategory']", cashFlowCategory);
        }
        if (templateType != null && !templateType.isEmpty() && isValidTemplateType(templateType)) {
            selectOption("select[name='templateType']", templateType);
        }
        if (description != null && !description.isEmpty()) {
            fillForm("textarea[name='description']", description);
        }

        // Handle line-related test cases
        if (!testCase.contains("lines")) {
            // Add minimal valid lines for non-line-related tests
            // Don't add lines for tests specifically about missing lines
        }

        submitForm();

        // Verify validation error is shown
        boolean hasValidationError = page.locator("input:invalid, select:invalid, .error, .alert-danger, [role='alert']").count() > 0 ||
                                     page.content().toLowerCase().contains(expectedError.toLowerCase()) ||
                                     page.content().toLowerCase().contains("error") ||
                                     page.content().toLowerCase().contains("required");

        assertTrue(hasValidationError,
                "Test case: " + testCase + " - Should show validation error");

        takeScreenshot("template-validation-" + sanitizeTestName(testCase));
    }

    private String buildRequestBody(String testCase, String templateName, String category,
                                   String cashFlowCategory, String templateType, String description) {
        StringBuilder json = new StringBuilder("{");

        if (templateName != null) {
            json.append("\"templateName\":\"").append(escapeJson(templateName)).append("\",");
        }
        if (category != null && !category.isEmpty()) {
            json.append("\"category\":\"").append(category).append("\",");
        }
        if (cashFlowCategory != null && !cashFlowCategory.isEmpty()) {
            json.append("\"cashFlowCategory\":\"").append(cashFlowCategory).append("\",");
        }
        if (templateType != null && !templateType.isEmpty()) {
            json.append("\"templateType\":\"").append(templateType).append("\",");
        } else {
            json.append("\"templateType\":\"SIMPLE\",");
        }
        if (description != null && !description.isEmpty()) {
            json.append("\"description\":\"").append(escapeJson(description)).append("\",");
        }

        // Handle lines based on test case
        if (testCase.contains("No journal lines")) {
            json.append("\"lines\":[]");
        } else if (testCase.contains("Only debit lines")) {
            json.append("\"lines\":[")
                .append("{\"accountId\":\"10000000-0000-0000-0000-000000000102\",\"position\":\"DEBIT\",\"formula\":\"amount\",\"lineOrder\":1},")
                .append("{\"accountId\":\"10000000-0000-0000-0000-000000000101\",\"position\":\"DEBIT\",\"formula\":\"amount\",\"lineOrder\":2}")
                .append("]");
        } else if (testCase.contains("Only credit lines")) {
            json.append("\"lines\":[")
                .append("{\"accountId\":\"10000000-0000-0000-0000-000000000102\",\"position\":\"CREDIT\",\"formula\":\"amount\",\"lineOrder\":1},")
                .append("{\"accountId\":\"10000000-0000-0000-0000-000000000101\",\"position\":\"CREDIT\",\"formula\":\"amount\",\"lineOrder\":2}")
                .append("]");
        } else {
            // Add valid lines for other test cases
            json.append("\"lines\":[")
                .append("{\"accountId\":\"10000000-0000-0000-0000-000000000102\",\"position\":\"DEBIT\",\"formula\":\"amount\",\"lineOrder\":1},")
                .append("{\"accountId\":\"40000000-0000-0000-0000-000000000101\",\"position\":\"CREDIT\",\"formula\":\"amount\",\"lineOrder\":2}")
                .append("]");
        }

        json.append("}");
        return json.toString();
    }

    private boolean isValidCategory(String value) {
        return value != null && (value.equals("INCOME") || value.equals("EXPENSE") ||
                value.equals("PAYMENT") || value.equals("RECEIPT") || value.equals("TRANSFER"));
    }

    private boolean isValidCashFlowCategory(String value) {
        return value != null && (value.equals("OPERATING") || value.equals("INVESTING") || value.equals("FINANCING"));
    }

    private boolean isValidTemplateType(String value) {
        return value != null && (value.equals("SIMPLE") || value.equals("DETAILED"));
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
