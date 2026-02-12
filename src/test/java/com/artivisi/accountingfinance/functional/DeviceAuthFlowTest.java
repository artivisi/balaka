package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional test for OAuth 2.0 Device Authorization Flow.
 * Tests the complete flow from device code request to API usage with access token.
 */
@DisplayName("Device Authentication Flow - Functional Tests")
@Import(ServiceTestDataInitializer.class)
class DeviceAuthFlowTest extends PlaywrightTestBase {

    private APIRequestContext apiContext;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        // Create API request context
        apiContext = playwright.request().newContext(new APIRequest.NewContextOptions()
                .setBaseURL(baseUrl()));
    }

    @AfterEach
    void tearDown() {
        if (apiContext != null) {
            apiContext.dispose();
        }
    }

    @Test
    @DisplayName("Complete device flow: request code → authorize → get token → use API")
    void testCompleteDeviceAuthFlow() throws Exception {
        // ============================================
        // Step 1: Request device code
        // ============================================
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("clientId", "playwright-test");

        APIResponse codeResponse = apiContext.post("/api/device/code",
                RequestOptions.create()
                        .setHeader("Content-Type", "application/json")
                        .setData(requestBody));

        assertThat(codeResponse.ok()).isTrue();

        JsonNode codeData = objectMapper.readTree(codeResponse.text());
        String deviceCode = codeData.get("deviceCode").asText();
        String userCode = codeData.get("userCode").asText();
        String verificationUri = codeData.get("verificationUri").asText();
        int interval = codeData.get("interval").asInt();

        assertThat(deviceCode).isNotEmpty();
        assertThat(userCode).matches("[A-Z]{4}-[A-Z]{4}"); // Format: XXXX-XXXX
        assertThat(verificationUri).isEqualTo(baseUrl() + "/device");

        System.out.println("Device code: " + deviceCode);
        System.out.println("User code: " + userCode);
        System.out.println("Verification URI: " + verificationUri);

        // ============================================
        // Step 2: User authorizes via browser
        // ============================================

        // Login as admin user
        loginAsAdmin();

        // Navigate to device authorization page with code
        navigateTo("/device?code=" + userCode);
        waitForPageLoad();

        // Verify we're on the authorization page
        assertThat(page.content()).contains("Otorisasi Perangkat");
        assertThat(page.content()).contains(userCode);
        assertThat(page.content()).contains("playwright-test");

        // Fill device name (optional)
        page.locator("input[name='deviceName']").fill("Playwright Test Device");

        // Click authorize button and wait for navigation
        page.locator("button[type='submit']:has-text('Otorisasi Perangkat')").click();

        // Wait for navigation to complete
        waitForPageLoad();

        // Verify we're on success page
        assertThat(page.url()).contains("/device/success");
        assertThat(page.content()).contains("Otorisasi Berhasil");

        System.out.println("Device authorized successfully!");

        // ============================================
        // Step 3: Poll for access token
        // ============================================

        String accessToken = null;
        int maxAttempts = 10;
        int attempt = 0;

        Map<String, String> tokenRequestBody = new HashMap<>();
        tokenRequestBody.put("deviceCode", deviceCode);

        while (attempt < maxAttempts) {
            Thread.sleep(interval * 1000L); // Wait for polling interval

            APIResponse tokenResponse = apiContext.post("/api/device/token",
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setData(tokenRequestBody));

            if (tokenResponse.ok()) {
                // Success! Got the token
                JsonNode tokenData = objectMapper.readTree(tokenResponse.text());
                accessToken = tokenData.get("accessToken").asText();
                String tokenType = tokenData.get("tokenType").asText();
                int expiresIn = tokenData.get("expiresIn").asInt();

                assertThat(accessToken).isNotEmpty();
                assertThat(tokenType).isEqualTo("Bearer");
                assertThat(expiresIn).isGreaterThan(0);

                System.out.println("Access token received!");
                System.out.println("Token type: " + tokenType);
                System.out.println("Expires in: " + expiresIn + " seconds");
                break;
            }

            // Still pending or error
            JsonNode errorData = objectMapper.readTree(tokenResponse.text());
            String error = errorData.get("error").asText();
            String errorDesc = errorData.get("errorDescription") != null ?
                    errorData.get("errorDescription").asText() : "";

            System.out.println("Poll response - error: " + error + ", description: " + errorDesc);

            assertThat(error).isEqualTo("authorization_pending");

            System.out.println("Still pending, polling again... (attempt " + (attempt + 1) + ")");
            attempt++;
        }

        assertThat(accessToken).isNotNull().isNotEmpty();

        // ============================================
        // Step 4: Test API with access token
        // ============================================

        // Test 1: Create draft from text
        Map<String, Object> draftRequest = new HashMap<>();
        draftRequest.put("merchant", "Test Merchant via Device Token");
        draftRequest.put("amount", 150000);
        draftRequest.put("transactionDate", "2026-02-11");
        draftRequest.put("currency", "IDR");
        draftRequest.put("category", "Testing");
        draftRequest.put("description", "Testing device authentication");
        draftRequest.put("confidence", 0.95);
        draftRequest.put("source", "playwright-test");

        APIResponse createDraftResponse = apiContext.post("/api/drafts/from-text",
                RequestOptions.create()
                        .setHeader("Content-Type", "application/json")
                        .setHeader("Authorization", "Bearer " + accessToken)
                        .setData(draftRequest));

        System.out.println("Create draft response status: " + createDraftResponse.status());
        System.out.println("Create draft response body: " + createDraftResponse.text());

        assertThat(createDraftResponse.status()).isIn(200, 201); // Accept both OK and CREATED

        JsonNode draftData = objectMapper.readTree(createDraftResponse.text());
        String draftId = draftData.get("draftId").asText();
        String status = draftData.get("status").asText();

        assertThat(draftId).isNotEmpty();
        assertThat(status).isEqualTo("PENDING");
        assertThat(draftData.get("merchant").asText()).isEqualTo("Test Merchant via Device Token");
        assertThat(draftData.get("amount").asInt()).isEqualTo(150000);

        System.out.println("Created draft: " + draftId);

        // Test 2: Get draft by ID
        APIResponse getDraftResponse = apiContext.get("/api/drafts/" + draftId,
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + accessToken));

        assertThat(getDraftResponse.ok()).isTrue();

        JsonNode retrievedDraft = objectMapper.readTree(getDraftResponse.text());
        assertThat(retrievedDraft.get("draftId").asText()).isEqualTo(draftId);
        assertThat(retrievedDraft.get("merchant").asText()).isEqualTo("Test Merchant via Device Token");

        System.out.println("Retrieved draft successfully!");

        // Test 3: List templates
        APIResponse templatesResponse = apiContext.get("/api/drafts/templates",
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + accessToken));

        assertThat(templatesResponse.ok()).isTrue();

        JsonNode templates = objectMapper.readTree(templatesResponse.text());
        assertThat(templates.isArray()).isTrue();
        assertThat(templates.size()).isGreaterThan(0);

        System.out.println("Found " + templates.size() + " templates");

        // Test 4: List accounts
        APIResponse accountsResponse = apiContext.get("/api/drafts/accounts",
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + accessToken));

        assertThat(accountsResponse.ok()).isTrue();

        JsonNode accounts = objectMapper.readTree(accountsResponse.text());
        assertThat(accounts.isArray()).isTrue();
        assertThat(accounts.size()).isGreaterThan(0);

        System.out.println("Found " + accounts.size() + " accounts");

        // Test 5: Create draft from receipt
        Map<String, Object> receiptRequest = new HashMap<>();
        receiptRequest.put("merchant", "Starbucks via Device Token");
        receiptRequest.put("amount", 85000);
        receiptRequest.put("transactionDate", "2026-02-11");
        receiptRequest.put("currency", "IDR");
        receiptRequest.put("items", new String[]{"Latte", "Sandwich"});
        receiptRequest.put("category", "Food & Beverage");
        receiptRequest.put("confidence", 0.92);
        receiptRequest.put("source", "playwright-test");

        APIResponse createReceiptResponse = apiContext.post("/api/drafts/from-receipt",
                RequestOptions.create()
                        .setHeader("Content-Type", "application/json")
                        .setHeader("Authorization", "Bearer " + accessToken)
                        .setData(receiptRequest));

        assertThat(createReceiptResponse.status()).isEqualTo(201);

        JsonNode receiptDraft = objectMapper.readTree(createReceiptResponse.text());
        assertThat(receiptDraft.get("merchant").asText()).isEqualTo("Starbucks via Device Token");

        System.out.println("Created receipt draft successfully!");

        // ============================================
        // Step 5: Test unauthorized access (without token)
        // ============================================

        APIResponse unauthorizedResponse = apiContext.get("/api/drafts/" + draftId);

        // Should be unauthorized or redirect to login
        assertThat(unauthorizedResponse.status()).isIn(401, 302, 403);

        System.out.println("Unauthorized request correctly blocked!");

        // ============================================
        // Test Summary
        // ============================================

        System.out.println("\n=== Device Auth Flow Test Summary ===");
        System.out.println("✓ Device code requested successfully");
        System.out.println("✓ User authorized device via browser");
        System.out.println("✓ Access token received via polling");
        System.out.println("✓ API calls authenticated with token");
        System.out.println("✓ Created draft from text");
        System.out.println("✓ Created draft from receipt");
        System.out.println("✓ Retrieved draft by ID");
        System.out.println("✓ Listed templates");
        System.out.println("✓ Listed accounts");
        System.out.println("✓ Unauthorized access blocked");
        System.out.println("=====================================\n");
    }

    @Test
    @DisplayName("Should reject invalid device code")
    void testInvalidDeviceCode() throws Exception {
        Map<String, String> tokenRequestBody = new HashMap<>();
        tokenRequestBody.put("deviceCode", "invalid-device-code-123");

        APIResponse response = apiContext.post("/api/device/token",
                RequestOptions.create()
                        .setHeader("Content-Type", "application/json")
                        .setData(tokenRequestBody));

        assertThat(response.status()).isEqualTo(400);

        JsonNode errorData = objectMapper.readTree(response.text());
        assertThat(errorData.get("error").asText()).isEqualTo("invalid_request");
    }

    @Test
    @DisplayName("Should reject invalid user code in browser")
    void testInvalidUserCode() {
        // Login as admin
        loginAsAdmin();

        // Try invalid user code
        navigateTo("/device?code=INVALID-CODE");
        waitForPageLoad();

        assertThat(page.content()).contains("Kode perangkat tidak valid");
    }

    @Test
    @DisplayName("Should require authentication for device authorization page")
    void testAuthRequiredForDeviceAuth() {
        // Try to access device page without login
        navigateTo("/device?code=TEST-CODE");

        // Should redirect to login
        page.waitForURL("**/login**");
        assertThat(page.url()).contains("/login");
    }
}
