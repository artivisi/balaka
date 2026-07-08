package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Functional tests for the Idempotency-Key header on POST /api/transactions (issue #29).
 * Simulates the account-receivable outbox dispatcher retrying after a lost response.
 */
@Slf4j
@DisplayName("Transaction Idempotency-Key API - Functional Tests")
@Import(ServiceTestDataInitializer.class)
class TransactionIdempotencyApiTest extends PlaywrightTestBase {

    private APIRequestContext apiContext;
    private ObjectMapper objectMapper;
    private String accessToken;

    @Autowired
    private ChartOfAccountRepository chartOfAccountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

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
    @DisplayName("Retry with same Idempotency-Key returns original transaction, no double post")
    void retryWithSameKeyReturnsOriginal() throws Exception {
        String key = "outbox-" + UUID.randomUUID();
        Map<String, Object> request = transactionRequest("Idempotent post", 150000);

        long countBefore = transactionRepository.count();

        APIResponse first = post("/api/transactions", request, key);
        assertThat(first.status())
                .as("First post: " + first.text())
                .isEqualTo(201);
        JsonNode created = parse(first);
        String transactionId = created.get("transactionId").asText();
        assertThat(created.get("status").asText()).isEqualTo("POSTED");

        // Simulated dispatcher retry after a lost response
        APIResponse retry = post("/api/transactions", request, key);
        assertThat(retry.status())
                .as("Retry should replay, not recreate: " + retry.text())
                .isEqualTo(200);
        JsonNode replayed = parse(retry);
        assertThat(replayed.get("transactionId").asText()).isEqualTo(transactionId);
        assertThat(replayed.get("transactionNumber").asText())
                .isEqualTo(created.get("transactionNumber").asText());
        assertThat(replayed.get("journalEntries").size())
                .isEqualTo(created.get("journalEntries").size());

        assertThat(transactionRepository.count())
                .as("Retry must not create a second transaction")
                .isEqualTo(countBefore + 1);
    }

    @Test
    @DisplayName("Different Idempotency-Key creates a separate transaction")
    void differentKeyCreatesNewTransaction() throws Exception {
        Map<String, Object> request = transactionRequest("Distinct keys", 80000);

        JsonNode firstBody = parse(post("/api/transactions", request, "key-" + UUID.randomUUID()));
        APIResponse second = post("/api/transactions", request, "key-" + UUID.randomUUID());

        assertThat(second.status()).isEqualTo(201);
        assertThat(parse(second).get("transactionId").asText())
                .isNotEqualTo(firstBody.get("transactionId").asText());
    }

    @Test
    @DisplayName("Without Idempotency-Key each post creates a transaction (legacy behavior)")
    void withoutKeyEachPostCreates() throws Exception {
        Map<String, Object> request = transactionRequest("No key", 60000);

        APIResponse first = post("/api/transactions", request, null);
        APIResponse second = post("/api/transactions", request, null);

        assertThat(first.status()).isEqualTo(201);
        assertThat(second.status()).isEqualTo(201);
        assertThat(parse(second).get("transactionId").asText())
                .isNotEqualTo(parse(first).get("transactionId").asText());
    }

    // ==================== HELPER METHODS ====================

    private Map<String, Object> transactionRequest(String description, int amount) throws Exception {
        String templateId = firstTemplateId();
        String bankAccountId = chartOfAccountRepository.findByAccountCode("1.1.02").orElseThrow().getId().toString();

        Map<String, Object> request = new HashMap<>();
        request.put("templateId", templateId);
        request.put("merchant", "AR Outbox");
        request.put("amount", amount);
        request.put("transactionDate", "2026-02-10");
        request.put("description", description);
        request.put("source", "idempotency-test");
        request.put("userApproved", true);
        request.put("accountSlots", Map.of("BANK", bankAccountId));
        return request;
    }

    private String firstTemplateId() throws Exception {
        APIResponse response = apiContext.get("/api/templates",
                RequestOptions.create().setHeader("Authorization", "Bearer " + accessToken));
        assertThat(response.ok()).isTrue();
        JsonNode templates = parse(response);
        assertThat(templates.size()).isGreaterThan(0);
        return templates.get(0).get("id").asText();
    }

    private APIResponse post(String path, Object data, String idempotencyKey) {
        RequestOptions options = RequestOptions.create()
                .setHeader("Authorization", "Bearer " + accessToken)
                .setHeader("Content-Type", "application/json")
                .setData(data);
        if (idempotencyKey != null) {
            options.setHeader("Idempotency-Key", idempotencyKey);
        }
        return apiContext.post(path, options);
    }

    private JsonNode parse(APIResponse response) throws Exception {
        return objectMapper.readTree(response.text());
    }

    private String authenticateViaDeviceFlow() throws Exception {
        Map<String, String> codeRequest = new HashMap<>();
        codeRequest.put("clientId", "idempotency-api-test");

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

        page.locator("input[name='deviceName']").fill("Idempotency API Test Device");
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
