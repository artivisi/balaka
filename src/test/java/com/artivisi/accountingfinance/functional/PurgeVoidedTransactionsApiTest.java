package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.RequestOptions;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Functional tests for DELETE /api/transactions/purge-voided endpoint.
 * Tests: create → post → void → purge lifecycle, ?before param, and safety checks.
 */
@Slf4j
@DisplayName("Purge Voided Transactions API - Functional Tests")
@Import(ServiceTestDataInitializer.class)
class PurgeVoidedTransactionsApiTest extends PlaywrightTestBase {

    private APIRequestContext apiContext;
    private ObjectMapper objectMapper;
    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        apiContext = playwright.request().newContext(new APIRequest.NewContextOptions()
                .setBaseURL(baseUrl()));

        accessToken = authenticateViaDeviceFlow();
    }

    @AfterEach
    void tearDown() {
        if (apiContext != null) {
            apiContext.dispose();
        }
    }

    @Test
    @DisplayName("Should purge voided transaction and return its details")
    void shouldPurgeVoidedTransaction() throws Exception {
        // Create and post a transaction
        String transactionId = createAndPostTransaction();

        // Void it
        voidTransaction(transactionId);

        // Purge voided transactions
        APIResponse purgeResponse = apiContext.delete("/api/transactions/purge-voided",
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + accessToken));

        assertThat(purgeResponse.ok())
                .as("Purge failed: %d %s", purgeResponse.status(), purgeResponse.text())
                .isTrue();

        JsonNode body = objectMapper.readTree(purgeResponse.text());
        assertThat(body.get("purgedCount").asInt()).isGreaterThanOrEqualTo(1);

        JsonNode purgedList = body.get("purgedTransactions");
        assertThat(purgedList.isArray()).isTrue();

        // Verify purged transaction details are returned
        boolean found = false;
        for (JsonNode purged : purgedList) {
            if (purged.get("id").asText().equals(transactionId)) {
                found = true;
                assertThat(purged.get("transactionNumber").asText()).isNotBlank();
                assertThat(purged.get("voidReason").asText()).isEqualTo("INPUT_ERROR");
                assertThat(purged.get("voidedBy").asText()).isNotBlank();
                break;
            }
        }
        assertThat(found).as("Purged transaction %s not found in response", transactionId).isTrue();
    }

    @Test
    @DisplayName("Should return empty list when no voided transactions exist")
    void shouldReturnEmptyWhenNoVoidedTransactions() throws Exception {
        // First purge any existing voided transactions
        apiContext.delete("/api/transactions/purge-voided",
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + accessToken));

        // Second purge should return empty
        APIResponse response = apiContext.delete("/api/transactions/purge-voided",
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + accessToken));

        assertThat(response.ok()).isTrue();

        JsonNode body = objectMapper.readTree(response.text());
        assertThat(body.get("purgedCount").asInt()).isEqualTo(0);
        assertThat(body.get("purgedTransactions").size()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should filter by before date parameter")
    void shouldFilterByBeforeDate() throws Exception {
        // Purge any existing voided first
        apiContext.delete("/api/transactions/purge-voided",
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + accessToken));

        // Create and void a transaction dated 2026-01-15
        String earlyId = createPostAndVoidTransaction("2026-01-15");

        // Create and void a transaction dated 2026-03-15
        String lateId = createPostAndVoidTransaction("2026-03-15");

        // Purge only transactions before 2026-02-01
        APIResponse purgeResponse = apiContext.delete("/api/transactions/purge-voided?before=2026-02-01",
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + accessToken));

        assertThat(purgeResponse.ok())
                .as("Purge with before param failed: %d %s", purgeResponse.status(), purgeResponse.text())
                .isTrue();

        JsonNode body = objectMapper.readTree(purgeResponse.text());
        JsonNode purgedList = body.get("purgedTransactions");

        // Early transaction should be purged
        boolean earlyFound = false;
        boolean lateFound = false;
        for (JsonNode purged : purgedList) {
            if (purged.get("id").asText().equals(earlyId)) earlyFound = true;
            if (purged.get("id").asText().equals(lateId)) lateFound = true;
        }
        assertThat(earlyFound).as("Early voided transaction should have been purged").isTrue();
        assertThat(lateFound).as("Late voided transaction should NOT have been purged").isFalse();

        // Clean up remaining voided transaction
        apiContext.delete("/api/transactions/purge-voided",
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + accessToken));
    }

    @Test
    @DisplayName("Should not affect posted or draft transactions")
    void shouldNotAffectNonVoidedTransactions() throws Exception {
        // Create a posted transaction (should survive purge)
        String postedId = createAndPostTransaction();

        // Create and void another transaction
        String voidedId = createAndPostTransaction();
        voidTransaction(voidedId);

        // Purge
        APIResponse purgeResponse = apiContext.delete("/api/transactions/purge-voided",
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + accessToken));

        assertThat(purgeResponse.ok()).isTrue();

        // Verify the posted transaction still exists via analysis API
        APIResponse txResponse = apiContext.get(
                "/api/analysis/transactions?status=POSTED&size=100",
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + accessToken));

        assertThat(txResponse.ok()).isTrue();

        JsonNode txBody = objectMapper.readTree(txResponse.text());
        JsonNode transactions = txBody.get("data").get("transactions");

        boolean postedStillExists = false;
        for (JsonNode tx : transactions) {
            if (tx.get("id").asText().equals(postedId)) {
                postedStillExists = true;
                break;
            }
        }
        assertThat(postedStillExists).as("Posted transaction %s should not be purged", postedId).isTrue();
    }

    // ========== Helper methods ==========

    private String createAndPostTransaction() throws Exception {
        return createAndPostTransaction("2026-02-12");
    }

    private String createAndPostTransaction(String transactionDate) throws Exception {
        String templateId = getFirstTemplateId();

        Map<String, Object> request = new HashMap<>();
        request.put("templateId", templateId);
        request.put("merchant", "Purge Test Merchant");
        request.put("amount", 100000);
        request.put("transactionDate", transactionDate);
        request.put("description", "Transaction for purge test");
        request.put("source", "purge-test");
        request.put("userApproved", true);

        APIResponse response = apiContext.post("/api/transactions",
                RequestOptions.create()
                        .setHeader("Content-Type", "application/json")
                        .setHeader("Authorization", "Bearer " + accessToken)
                        .setData(request));

        assertThat(response.status())
                .as("Create+post failed: %d %s", response.status(), response.text())
                .isEqualTo(201);

        JsonNode body = objectMapper.readTree(response.text());
        return body.get("transactionId").asText();
    }

    private void voidTransaction(String transactionId) throws Exception {
        Map<String, Object> voidRequest = Map.of(
                "reason", "INPUT_ERROR",
                "notes", "Voided for purge test"
        );

        APIResponse response = apiContext.post("/api/transactions/" + transactionId + "/void",
                RequestOptions.create()
                        .setHeader("Content-Type", "application/json")
                        .setHeader("Authorization", "Bearer " + accessToken)
                        .setData(voidRequest));

        assertThat(response.ok())
                .as("Void failed: %d %s", response.status(), response.text())
                .isTrue();
    }

    private String createPostAndVoidTransaction(String transactionDate) throws Exception {
        String id = createAndPostTransaction(transactionDate);
        voidTransaction(id);
        return id;
    }

    private String getFirstTemplateId() throws Exception {
        APIResponse response = apiContext.get("/api/templates",
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + accessToken));

        assertThat(response.ok()).isTrue();

        JsonNode templates = objectMapper.readTree(response.text());
        assertThat(templates.size()).isGreaterThan(0);

        return templates.get(0).get("id").asText();
    }

    private String authenticateViaDeviceFlow() throws Exception {
        Map<String, String> codeRequest = new HashMap<>();
        codeRequest.put("clientId", "playwright-test");

        APIResponse codeResponse = apiContext.post("/api/device/code",
                RequestOptions.create()
                        .setHeader("Content-Type", "application/json")
                        .setData(codeRequest));

        assertThat(codeResponse.ok()).isTrue();

        JsonNode codeData = objectMapper.readTree(codeResponse.text());
        String deviceCode = codeData.get("deviceCode").asText();
        String userCode = codeData.get("userCode").asText();

        loginAsAdmin();
        navigateTo("/device?code=" + userCode);
        waitForPageLoad();

        page.locator("input[name='deviceName']").fill("Purge Test Device");
        page.locator("button[type='submit']:has-text('Otorisasi Perangkat')").click();
        waitForPageLoad();

        Map<String, String> tokenRequest = new HashMap<>();
        tokenRequest.put("deviceCode", deviceCode);

        AtomicReference<String> tokenRef = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofSeconds(2)).until(() -> {
            APIResponse tokenResponse = apiContext.post("/api/device/token",
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setData(tokenRequest));
            if (tokenResponse.ok()) {
                JsonNode tokenData = objectMapper.readTree(tokenResponse.text());
                tokenRef.set(tokenData.get("accessToken").asText());
                return true;
            }
            return false;
        });

        return tokenRef.get();
    }
}
