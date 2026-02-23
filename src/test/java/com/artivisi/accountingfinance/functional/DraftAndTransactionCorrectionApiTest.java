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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests for draft and transaction correction API endpoints.
 * Tests:
 * - PATCH /api/drafts/{id} (update PENDING draft)
 * - PUT /api/transactions/{id} (update DRAFT transaction)
 * - DELETE /api/transactions/{id} (delete DRAFT transaction)
 */
@Slf4j
@DisplayName("Draft & Transaction Correction API - Functional Tests")
@Import(ServiceTestDataInitializer.class)
class DraftAndTransactionCorrectionApiTest extends PlaywrightTestBase {

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

    @Nested
    @DisplayName("PATCH /api/drafts/{id}")
    class PatchDraft {

        @Test
        @DisplayName("Should update merchant name on PENDING draft")
        void shouldUpdateMerchantName() throws Exception {
            String draftId = createPendingDraft("Original Merchant", 50000);

            Map<String, Object> patch = Map.of("merchantName", "Corrected Merchant Name");

            APIResponse response = apiContext.patch("/api/drafts/" + draftId,
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Authorization", "Bearer " + accessToken)
                            .setData(patch));

            assertThat(response.ok()).isTrue();

            JsonNode body = objectMapper.readTree(response.text());
            assertThat(body.get("merchant").asText()).isEqualTo("Corrected Merchant Name");
            assertThat(body.get("status").asText()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("Should update amount on PENDING draft")
        void shouldUpdateAmount() throws Exception {
            String draftId = createPendingDraft("Amount Test Merchant", 50000);

            Map<String, Object> patch = Map.of("amount", 75000);

            APIResponse response = apiContext.patch("/api/drafts/" + draftId,
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Authorization", "Bearer " + accessToken)
                            .setData(patch));

            assertThat(response.ok()).isTrue();

            JsonNode body = objectMapper.readTree(response.text());
            assertThat(body.get("amount").asInt()).isEqualTo(75000);
        }

        @Test
        @DisplayName("Should update suggested template on PENDING draft")
        void shouldUpdateSuggestedTemplate() throws Exception {
            String draftId = createPendingDraft("Template Test Merchant", 50000);
            String templateId = getFirstTemplateId();

            Map<String, Object> patch = Map.of("suggestedTemplateId", templateId);

            APIResponse response = apiContext.patch("/api/drafts/" + draftId,
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Authorization", "Bearer " + accessToken)
                            .setData(patch));

            assertThat(response.ok()).isTrue();

            JsonNode body = objectMapper.readTree(response.text());
            assertThat(body.get("suggestedTemplate")).isNotNull();
            assertThat(body.get("suggestedTemplate").get("id").asText()).isEqualTo(templateId);
        }

        @Test
        @DisplayName("Should update multiple fields at once")
        void shouldUpdateMultipleFields() throws Exception {
            String draftId = createPendingDraft("Multi Update Merchant", 30000);

            Map<String, Object> patch = new HashMap<>();
            patch.put("merchantName", "Updated Merchant");
            patch.put("amount", 99000);
            patch.put("description", "Updated description");

            APIResponse response = apiContext.patch("/api/drafts/" + draftId,
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Authorization", "Bearer " + accessToken)
                            .setData(patch));

            assertThat(response.ok()).isTrue();

            JsonNode body = objectMapper.readTree(response.text());
            assertThat(body.get("merchant").asText()).isEqualTo("Updated Merchant");
            assertThat(body.get("amount").asInt()).isEqualTo(99000);
        }

        @Test
        @DisplayName("Should reject update on non-PENDING draft")
        void shouldRejectUpdateOnApprovedDraft() throws Exception {
            // Create and approve a draft
            String draftId = createPendingDraft("Approved Draft Merchant", 50000);
            String templateId = getFirstTemplateId();
            approveDraft(draftId, templateId);

            Map<String, Object> patch = Map.of("merchantName", "Should Not Work");

            APIResponse response = apiContext.patch("/api/drafts/" + draftId,
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Authorization", "Bearer " + accessToken)
                            .setData(patch));

            assertThat(response.status()).isGreaterThanOrEqualTo(400);
        }
    }

    @Nested
    @DisplayName("PUT /api/transactions/{id}")
    class PutTransaction {

        @Test
        @DisplayName("Should reclassify DRAFT transaction template")
        void shouldReclassifyTemplate() throws Exception {
            String transactionId = createDraftTransaction();
            String newTemplateId = getSecondTemplateId();

            Map<String, Object> update = Map.of("templateId", newTemplateId);

            APIResponse response = apiContext.put("/api/transactions/" + transactionId,
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Authorization", "Bearer " + accessToken)
                            .setData(update));

            assertThat(response.ok()).isTrue();

            JsonNode body = objectMapper.readTree(response.text());
            assertThat(body.get("status").asText()).isEqualTo("DRAFT");
        }

        @Test
        @DisplayName("Should update DRAFT transaction description")
        void shouldUpdateDescription() throws Exception {
            String transactionId = createDraftTransaction();

            Map<String, Object> update = Map.of("description", "Corrected: Bayar PPN Januari 2026");

            APIResponse response = apiContext.put("/api/transactions/" + transactionId,
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Authorization", "Bearer " + accessToken)
                            .setData(update));

            assertThat(response.ok()).isTrue();

            JsonNode body = objectMapper.readTree(response.text());
            assertThat(body.get("description").asText()).isEqualTo("Corrected: Bayar PPN Januari 2026");
        }

        @Test
        @DisplayName("Should update DRAFT transaction amount")
        void shouldUpdateAmount() throws Exception {
            String transactionId = createDraftTransaction();

            Map<String, Object> update = Map.of("amount", 150000);

            APIResponse response = apiContext.put("/api/transactions/" + transactionId,
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Authorization", "Bearer " + accessToken)
                            .setData(update));

            assertThat(response.ok()).isTrue();

            JsonNode body = objectMapper.readTree(response.text());
            assertThat(body.get("amount").asInt()).isEqualTo(150000);
        }

        @Test
        @DisplayName("Should update multiple DRAFT transaction fields")
        void shouldUpdateMultipleFields() throws Exception {
            String transactionId = createDraftTransaction();

            Map<String, Object> update = new HashMap<>();
            update.put("description", "Updated remarks");
            update.put("amount", 200000);
            update.put("transactionDate", "2026-02-15");

            APIResponse response = apiContext.put("/api/transactions/" + transactionId,
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Authorization", "Bearer " + accessToken)
                            .setData(update));

            assertThat(response.ok()).isTrue();

            JsonNode body = objectMapper.readTree(response.text());
            assertThat(body.get("description").asText()).isEqualTo("Updated remarks");
            assertThat(body.get("amount").asInt()).isEqualTo(200000);
        }

        @Test
        @DisplayName("Should reject update on POSTED transaction")
        void shouldRejectUpdateOnPostedTransaction() throws Exception {
            String transactionId = createAndPostTransaction();

            Map<String, Object> update = Map.of("description", "Should Not Work");

            APIResponse response = apiContext.put("/api/transactions/" + transactionId,
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Authorization", "Bearer " + accessToken)
                            .setData(update));

            assertThat(response.status()).isGreaterThanOrEqualTo(400);
        }

        @Test
        @DisplayName("Should update DRAFT transaction with lineAccountOverrides")
        void shouldUpdateWithLineAccountOverrides() throws Exception {
            String transactionId = createDraftTransaction();
            String accountId = getFirstAccountId();

            Map<String, Object> update = new HashMap<>();
            update.put("lineAccountOverrides", Map.of(2, accountId));

            APIResponse response = apiContext.put("/api/transactions/" + transactionId,
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Authorization", "Bearer " + accessToken)
                            .setData(update));

            assertThat(response.ok())
                    .as("Update with lineAccountOverrides failed: %d %s", response.status(), response.text())
                    .isTrue();
        }

        @Test
        @DisplayName("Should reclassify template with lineAccountOverrides")
        void shouldReclassifyWithLineAccountOverrides() throws Exception {
            String transactionId = createDraftTransaction();
            String templateId = getSecondTemplateId();
            String accountId = getFirstAccountId();

            Map<String, Object> update = new HashMap<>();
            update.put("templateId", templateId);
            update.put("lineAccountOverrides", Map.of(1, accountId, 2, accountId));

            APIResponse response = apiContext.put("/api/transactions/" + transactionId,
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Authorization", "Bearer " + accessToken)
                            .setData(update));

            assertThat(response.ok())
                    .as("Reclassify with overrides failed: %d %s", response.status(), response.text())
                    .isTrue();
        }

        @Test
        @DisplayName("Should reject future transaction date")
        void shouldRejectFutureDate() throws Exception {
            String transactionId = createDraftTransaction();

            Map<String, Object> update = Map.of("transactionDate", "2099-12-31");

            APIResponse response = apiContext.put("/api/transactions/" + transactionId,
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Authorization", "Bearer " + accessToken)
                            .setData(update));

            assertThat(response.status()).isGreaterThanOrEqualTo(400);
        }
    }

    @Nested
    @DisplayName("DELETE /api/transactions/{id}")
    class DeleteTransaction {

        @Test
        @DisplayName("Should delete DRAFT transaction")
        void shouldDeleteDraftTransaction() throws Exception {
            String transactionId = createDraftTransaction();

            APIResponse response = apiContext.delete("/api/transactions/" + transactionId,
                    RequestOptions.create()
                            .setHeader("Authorization", "Bearer " + accessToken));

            assertThat(response.status())
                    .as("Delete failed: %d %s", response.status(), response.text())
                    .isEqualTo(204);
        }

        @Test
        @DisplayName("Should create and post transaction with lineAccountOverrides")
        void shouldPostWithLineAccountOverrides() throws Exception {
            String templateId = getFirstTemplateId();
            String accountId = getFirstAccountId();

            Map<String, Object> request = new HashMap<>();
            request.put("templateId", templateId);
            request.put("merchant", "Override Test Merchant");
            request.put("amount", 75000);
            request.put("transactionDate", "2026-02-12");
            request.put("description", "Transaction with account overrides");
            request.put("source", "correction-test");
            request.put("userApproved", true);
            request.put("lineAccountOverrides", Map.of(1, accountId, 2, accountId));

            APIResponse response = apiContext.post("/api/transactions",
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Authorization", "Bearer " + accessToken)
                            .setData(request));

            assertThat(response.status())
                    .as("Create with overrides failed: %d %s", response.status(), response.text())
                    .isEqualTo(201);

            JsonNode body = objectMapper.readTree(response.text());
            assertThat(body.get("status").asText()).isEqualTo("POSTED");
        }

        @Test
        @DisplayName("Should reject delete on POSTED transaction")
        void shouldRejectDeleteOnPostedTransaction() throws Exception {
            String transactionId = createAndPostTransaction();

            APIResponse response = apiContext.delete("/api/transactions/" + transactionId,
                    RequestOptions.create()
                            .setHeader("Authorization", "Bearer " + accessToken));

            assertThat(response.status()).isGreaterThanOrEqualTo(400);
        }
    }

    // ========== Helper methods ==========

    private String createPendingDraft(String merchant, int amount) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("merchant", merchant);
        request.put("amount", amount);
        request.put("transactionDate", "2026-02-12");
        request.put("currency", "IDR");
        request.put("confidence", 0.95);
        request.put("source", "correction-test");

        APIResponse response = apiContext.post("/api/drafts/from-text",
                RequestOptions.create()
                        .setHeader("Content-Type", "application/json")
                        .setHeader("Authorization", "Bearer " + accessToken)
                        .setData(request));

        assertThat(response.status()).isEqualTo(201);

        JsonNode body = objectMapper.readTree(response.text());
        return body.get("draftId").asText();
    }

    private void approveDraft(String draftId, String templateId) throws Exception {
        Map<String, Object> request = Map.of(
                "templateId", templateId,
                "description", "Approved via test",
                "amount", 50000
        );

        APIResponse response = apiContext.post("/api/drafts/" + draftId + "/approve",
                RequestOptions.create()
                        .setHeader("Content-Type", "application/json")
                        .setHeader("Authorization", "Bearer " + accessToken)
                        .setData(request));

        assertThat(response.ok())
                .as("Approve draft %s failed: %d %s", draftId, response.status(), response.text())
                .isTrue();
    }

    private String createDraftTransaction() throws Exception {
        // Create a draft via POST /api/drafts/from-text, approve it (creates Transaction in DRAFT),
        // but we need a DRAFT Transaction, not a POSTED one.
        // Use direct POST /api/transactions without userApproved to get a DRAFT.
        // Actually, POST /api/transactions posts immediately. Let's use the draft → approve flow
        // which creates a DRAFT Transaction, then post it separately.
        String draftId = createPendingDraft("Draft Transaction Merchant", 85000);
        String templateId = getFirstTemplateId();

        // Approve draft — this creates a Transaction in DRAFT status
        Map<String, Object> approveRequest = Map.of(
                "templateId", templateId,
                "description", "Draft transaction for correction test",
                "amount", 85000
        );
        APIResponse approveResponse = apiContext.post("/api/drafts/" + draftId + "/approve",
                RequestOptions.create()
                        .setHeader("Content-Type", "application/json")
                        .setHeader("Authorization", "Bearer " + accessToken)
                        .setData(approveRequest));

        assertThat(approveResponse.ok())
                .as("Approve draft failed: %d %s", approveResponse.status(), approveResponse.text())
                .isTrue();

        // Use analysis API to find the most recent DRAFT transaction
        APIResponse txListResponse = apiContext.get(
                "/api/analysis/transactions?status=DRAFT&size=1",
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + accessToken));

        assertThat(txListResponse.ok())
                .as("List transactions failed: %d %s", txListResponse.status(), txListResponse.text())
                .isTrue();

        JsonNode txList = objectMapper.readTree(txListResponse.text());
        // Response is wrapped: { "data": { "transactions": [...] } }
        JsonNode transactions = txList.get("data").get("transactions");
        assertThat(transactions.isArray()).isTrue();
        assertThat(transactions.size()).isGreaterThan(0);

        return transactions.get(0).get("id").asText();
    }

    private String createAndPostTransaction() throws Exception {
        String templateId = getFirstTemplateId();

        Map<String, Object> request = new HashMap<>();
        request.put("templateId", templateId);
        request.put("merchant", "Posted Transaction Merchant");
        request.put("amount", 100000);
        request.put("transactionDate", "2026-02-12");
        request.put("description", "Posted test transaction");
        request.put("source", "correction-test");
        request.put("userApproved", true);

        APIResponse response = apiContext.post("/api/transactions",
                RequestOptions.create()
                        .setHeader("Content-Type", "application/json")
                        .setHeader("Authorization", "Bearer " + accessToken)
                        .setData(request));

        assertThat(response.status()).isEqualTo(201);

        JsonNode body = objectMapper.readTree(response.text());
        return body.get("transactionId").asText();
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

    private String getFirstAccountId() throws Exception {
        APIResponse response = apiContext.get("/api/analysis/accounts",
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + accessToken));

        assertThat(response.ok()).isTrue();

        JsonNode body = objectMapper.readTree(response.text());
        JsonNode accounts = body.get("data").get("accounts");
        assertThat(accounts.isArray()).isTrue();
        assertThat(accounts.size()).isGreaterThan(0);

        return accounts.get(0).get("id").asText();
    }

    private String getSecondTemplateId() throws Exception {
        APIResponse response = apiContext.get("/api/templates",
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + accessToken));

        assertThat(response.ok()).isTrue();

        JsonNode templates = objectMapper.readTree(response.text());
        assertThat(templates.size()).isGreaterThan(1);

        return templates.get(1).get("id").asText();
    }

    private String authenticateViaDeviceFlow() throws Exception {
        Map<String, String> codeRequest = Map.of("clientId", "correction-test");

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

        page.locator("input[name='deviceName']").fill("Correction Test Device");
        page.locator("button[type='submit']:has-text('Otorisasi Perangkat')").click();
        waitForPageLoad();

        Map<String, String> tokenRequest = Map.of("deviceCode", deviceCode);

        for (int i = 0; i < 10; i++) {
            Thread.sleep(2000);

            APIResponse tokenResponse = apiContext.post("/api/device/token",
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setData(tokenRequest));

            if (tokenResponse.ok()) {
                JsonNode tokenData = objectMapper.readTree(tokenResponse.text());
                return tokenData.get("accessToken").asText();
            }
        }

        throw new RuntimeException("Failed to get access token");
    }
}
