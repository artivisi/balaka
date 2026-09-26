package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * DELETE /api/transactions/purge-voided?before=... on transactions this class creates in 2001
 * (reserved). Every purge passes a "before" bound so it only reaches this class's own voided
 * transactions; each test purges below a bound under which it leaves nothing, and the only
 * transaction left VOID (2001-11-10) lies above every bound. The unbounded purge (all VOID
 * transactions in the database) is not exercised: it would delete other classes' data.
 */
@DisplayName("Purge Voided Transactions API - Functional Tests")
@Import(ServiceTestDataInitializer.class)
class PurgeVoidedTransactionsApiTest extends PlaywrightTestBase {

    @Autowired private JournalTemplateRepository templateRepository;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private JdbcTemplate jdbc;

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
    @DisplayName("Purging deletes the voided transaction with its journal and returns its details")
    void shouldPurgeVoidedTransaction() throws Exception {
        JsonNode posted = createAndPost("2001-02-10", "Purge tunggal");
        String id = posted.get("transactionId").asText();
        voidTransaction(id);

        JsonNode body = purge("2001-03-01");

        assertThat(body.get("purgedCount").asInt()).isEqualTo(1);
        JsonNode purged = body.get("purgedTransactions").get(0);
        assertThat(purged.get("id").asText()).isEqualTo(id);
        assertThat(purged.get("transactionNumber").asText()).isEqualTo(posted.get("transactionNumber").asText());
        assertThat(purged.get("transactionDate").asText()).isEqualTo("2001-02-10");
        assertThat(new BigDecimal(purged.get("amount").asText())).isEqualByComparingTo("100000");
        assertThat(purged.get("description").asText()).isEqualTo("Purge tunggal");
        assertThat(purged.get("voidReason").asText()).isEqualTo("INPUT_ERROR");
        assertThat(purged.get("voidedBy").asText()).isEqualTo("admin");
        assertThat(count("SELECT COUNT(*) FROM transactions WHERE id = ?::uuid", id)).isZero();
        assertThat(count("SELECT COUNT(*) FROM journal_entries WHERE id_transaction = ?::uuid", id)).isZero();
    }

    @Test
    @DisplayName("A bound below every voided transaction purges nothing")
    void shouldReturnEmptyWhenNoVoidedTransactions() throws Exception {
        assertThat(count("SELECT COUNT(*) FROM transactions WHERE status = 'VOID' AND transaction_date < ?::date", "2001-01-01"))
                .as("voided transactions dated before 2001").isZero();

        JsonNode body = purge("2001-01-01");

        assertThat(body.get("purgedCount").asInt()).isZero();
        assertThat(body.get("purgedTransactions")).isEmpty();
    }

    @Test
    @DisplayName("Only voided transactions dated before the bound are purged")
    void shouldFilterByBeforeDate() throws Exception {
        String early = createAndPost("2001-04-10", "Purge awal").get("transactionId").asText();
        voidTransaction(early);
        String late = createAndPost("2001-11-10", "Purge akhir").get("transactionId").asText();
        voidTransaction(late);

        JsonNode body = purge("2001-05-01");

        assertThat(ids(body)).containsExactly(early);
        assertThat(count("SELECT COUNT(*) FROM transactions WHERE id = ?::uuid", early)).isZero();
        assertThat(status(late)).isEqualTo("VOID");
    }

    @Test
    @DisplayName("Posted and draft transactions before the bound are kept")
    void shouldNotAffectNonVoidedTransactions() throws Exception {
        String posted = createAndPost("2001-08-05", "Purge tetap posted").get("transactionId").asText();
        String voided = createAndPost("2001-08-06", "Purge dibatalkan").get("transactionId").asText();
        voidTransaction(voided);
        String draft = createDraft("2001-08-07", "Purge tetap draft");

        JsonNode body = purge("2001-09-01");

        assertThat(ids(body)).containsExactly(voided);
        assertThat(status(posted)).isEqualTo("POSTED");
        assertThat(count("SELECT COUNT(*) FROM journal_entries WHERE id_transaction = ?::uuid", posted)).isEqualTo(2);
        assertThat(status(draft)).isEqualTo("DRAFT");
    }

    // ========== Helper methods ==========

    private JsonNode purge(String before) throws Exception {
        APIResponse response = apiContext.delete("/api/transactions/purge-voided?before=" + before,
                RequestOptions.create().setHeader("Authorization", "Bearer " + accessToken));
        assertThat(response.status()).as("Purge failed: %s", response.text()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(response.text());
        assertThat(body.get("purgedCount").asInt()).isEqualTo(body.get("purgedTransactions").size());
        return body;
    }

    private static List<String> ids(JsonNode purgeBody) {
        List<String> ids = new ArrayList<>();
        purgeBody.get("purgedTransactions").forEach(p -> ids.add(p.get("id").asText()));
        return ids;
    }

    private long count(String sql, String param) {
        return jdbc.queryForObject(sql, Long.class, param);
    }

    private String status(String transactionId) {
        return jdbc.queryForObject("SELECT status FROM transactions WHERE id = ?::uuid", String.class, transactionId);
    }

    private Map<String, Object> request(String date, String description) {
        String template = "Bayar Beban Listrik";
        Map<String, Object> request = new HashMap<>();
        request.put("templateId", templateRepository.findByTemplateNameAndIsCurrentVersionTrue(template)
                .orElseThrow(() -> new IllegalStateException("Seeded template missing: " + template)).getId().toString());
        request.put("amount", 100000);
        request.put("transactionDate", date);
        request.put("description", description);
        request.put("accountSlots", Map.of("BANK", accountRepository.findByAccountCode("1.1.02")
                .orElseThrow(() -> new IllegalStateException("Seeded account missing: 1.1.02")).getId().toString()));
        return request;
    }

    private JsonNode createAndPost(String date, String description) throws Exception {
        Map<String, Object> request = request(date, description);
        request.put("merchant", "Purge Test Merchant");
        request.put("source", "purge-test");
        request.put("userApproved", true);

        APIResponse response = apiContext.post("/api/transactions", json(request));
        assertThat(response.status()).as("Create+post failed: %s", response.text()).isEqualTo(201);
        return objectMapper.readTree(response.text());
    }

    private String createDraft(String date, String description) throws Exception {
        APIResponse response = apiContext.post("/api/drafts", json(request(date, description)));
        assertThat(response.status()).as("Create draft failed: %s", response.text()).isEqualTo(201);
        return objectMapper.readTree(response.text()).get("transactionId").asText();
    }

    private void voidTransaction(String transactionId) {
        APIResponse response = apiContext.post("/api/transactions/" + transactionId + "/void",
                json(Map.of("reason", "INPUT_ERROR", "notes", "Voided for purge test")));
        assertThat(response.status()).as("Void failed: %s", response.text()).isEqualTo(200);
    }

    private RequestOptions json(Object data) {
        return RequestOptions.create()
                .setHeader("Content-Type", "application/json")
                .setHeader("Authorization", "Bearer " + accessToken)
                .setData(data);
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
