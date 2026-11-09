package com.artivisi.accountingfinance.functional;

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

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Functional tests for {@code BankReconciliationApiController}.
 *
 * <p>These exercise the HTTP layer: authentication, bean validation and the
 * not-found path. They deliberately avoid the import and matching flows, which
 * need a statement fixture and belong in their own test.
 */
@Slf4j
@DisplayName("Bank Reconciliation API - Functional Tests")
class BankReconciliationApiTest extends PlaywrightTestBase {

    private static final String BASE_PATH = "/api/bank-reconciliation";

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
    @DisplayName("request without a token is rejected before reaching the controller")
    void rejectsUnauthenticatedRequest() {
        APIResponse response = apiContext.get(BASE_PATH + "/parser-configs");

        assertThat(response.status())
                .as("Anonymous request must not see parser configuration")
                .isEqualTo(401);
    }

    @Test
    @DisplayName("GET /parser-configs returns a JSON array")
    void listsParserConfigs() throws Exception {
        APIResponse response = get(BASE_PATH + "/parser-configs");

        assertThat(response.status())
                .as("List parser configs: " + response.text())
                .isEqualTo(200);

        JsonNode body = parse(response);
        assertThat(body.isArray()).as("Response must be an array, not an object").isTrue();
    }

    @Test
    @DisplayName("POST /parser-configs rejects a body without the required columns")
    void rejectsIncompleteParserConfig() throws Exception {
        APIResponse response = post(BASE_PATH + "/parser-configs", Map.of());

        assertThat(response.status())
                .as("Bean validation must reject an empty parser config")
                .isEqualTo(400);

        JsonNode body = parse(response);
        assertThat(body.has("message") || body.has("error") || body.has("errors"))
                .as("Error body should explain the rejection: " + response.text())
                .isTrue();
    }

    @Test
    @DisplayName("POST /parser-configs rejects a negative column index")
    void rejectsNegativeColumnIndex() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("bankType", "BCA");
        request.put("configName", "Uji kolom negatif");
        request.put("dateColumn", -1);
        request.put("descriptionColumn", 1);
        request.put("dateFormat", "dd/MM/yyyy");

        APIResponse response = post(BASE_PATH + "/parser-configs", request);

        assertThat(response.status())
                .as("Column indexes are @Min(0): " + response.text())
                .isEqualTo(400);
    }

    @Test
    @DisplayName("GET /statements/{id} returns 404 for an unknown statement")
    void returnsNotFoundForUnknownStatement() throws Exception {
        UUID unknown = UUID.randomUUID();

        APIResponse response = get(BASE_PATH + "/statements/" + unknown);

        assertThat(response.status())
                .as("Unknown statement must be 404, not 500: " + response.text())
                .isEqualTo(404);
    }

    @Test
    @DisplayName("GET /reconciliations/{id} returns 404 for an unknown reconciliation")
    void returnsNotFoundForUnknownReconciliation() throws Exception {
        UUID unknown = UUID.randomUUID();

        APIResponse response = get(BASE_PATH + "/reconciliations/" + unknown);

        assertThat(response.status())
                .as("Unknown reconciliation must be 404, not 500: " + response.text())
                .isEqualTo(404);
    }

    private APIResponse get(String path) {
        return apiContext.get(path,
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + accessToken));
    }

    private APIResponse post(String path, Object data) {
        return apiContext.post(path,
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + accessToken)
                        .setHeader("Content-Type", "application/json")
                        .setData(data));
    }

    private JsonNode parse(APIResponse response) throws Exception {
        return objectMapper.readTree(response.text());
    }

    private String authenticateViaDeviceFlow() throws Exception {
        Map<String, String> codeRequest = new HashMap<>();
        codeRequest.put("clientId", "bank-recon-api-test");

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

        page.locator("input[name='deviceName']").fill("Bank Recon API Test Device");
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
