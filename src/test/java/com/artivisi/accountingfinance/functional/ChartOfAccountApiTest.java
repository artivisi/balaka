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

@Slf4j
@DisplayName("Chart of Accounts API")
@Import(ServiceTestDataInitializer.class)
class ChartOfAccountApiTest extends PlaywrightTestBase {

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
    @DisplayName("CRUD lifecycle: create, get, list, update, delete")
    void crudLifecycle() throws Exception {
        // CREATE
        Map<String, Object> request = new HashMap<>();
        request.put("accountCode", "2.1.98");
        request.put("accountName", "Hutang API Test");
        request.put("accountType", "LIABILITY");
        request.put("normalBalance", "CREDIT");
        request.put("isHeader", false);
        request.put("permanent", true);
        request.put("description", "Created via API test");

        APIResponse createResponse = post("/api/accounts", request);
        assertThat(createResponse.status()).isEqualTo(201);

        JsonNode created = parse(createResponse);
        String id = created.get("id").asText();
        assertThat(id).isNotBlank();
        assertThat(created.get("accountCode").asText()).isEqualTo("2.1.98");
        assertThat(created.get("accountName").asText()).isEqualTo("Hutang API Test");
        assertThat(created.get("accountType").asText()).isEqualTo("LIABILITY");
        assertThat(created.get("normalBalance").asText()).isEqualTo("CREDIT");
        assertThat(created.get("isHeader").asBoolean()).isFalse();
        assertThat(created.get("permanent").asBoolean()).isTrue();
        assertThat(created.get("active").asBoolean()).isTrue();
        log.info("Created account: id={}, code={}", id, "2.1.98");

        // GET by ID
        APIResponse getResponse = get("/api/accounts/" + id);
        assertThat(getResponse.status()).isEqualTo(200);

        JsonNode detail = parse(getResponse);
        assertThat(detail.get("accountCode").asText()).isEqualTo("2.1.98");
        assertThat(detail.get("description").asText()).isEqualTo("Created via API test");

        // LIST (paginated)
        APIResponse listResponse = get("/api/accounts?page=0&size=100");
        assertThat(listResponse.status()).isEqualTo(200);

        JsonNode listResult = parse(listResponse);
        JsonNode content = listResult.get("content");
        assertThat(content).isNotNull();
        assertThat(content.isArray()).isTrue();
        assertThat(content.size()).isGreaterThan(0);

        boolean found = false;
        for (JsonNode item : content) {
            if (item.get("id").asText().equals(id)) {
                found = true;
                break;
            }
        }
        assertThat(found).as("Created account should appear in list").isTrue();

        // LIST with type filter
        APIResponse filteredResponse = get("/api/accounts?type=LIABILITY&page=0&size=100");
        assertThat(filteredResponse.status()).isEqualTo(200);

        JsonNode filteredContent = parse(filteredResponse).get("content");
        for (JsonNode item : filteredContent) {
            assertThat(item.get("accountType").asText()).isEqualTo("LIABILITY");
        }

        // UPDATE
        Map<String, Object> updateRequest = new HashMap<>();
        updateRequest.put("accountCode", "2.1.98");
        updateRequest.put("accountName", "Hutang API Test (Updated)");
        updateRequest.put("accountType", "LIABILITY");
        updateRequest.put("normalBalance", "CREDIT");
        updateRequest.put("isHeader", false);
        updateRequest.put("permanent", true);
        updateRequest.put("description", "Updated via API test");

        APIResponse updateResponse = put("/api/accounts/" + id, updateRequest);
        assertThat(updateResponse.status()).isEqualTo(200);

        JsonNode updated = parse(updateResponse);
        assertThat(updated.get("accountName").asText()).isEqualTo("Hutang API Test (Updated)");
        assertThat(updated.get("description").asText()).isEqualTo("Updated via API test");
        log.info("Updated account: id={}", id);

        // DELETE
        APIResponse deleteResponse = delete("/api/accounts/" + id);
        assertThat(deleteResponse.status()).isEqualTo(204);
        log.info("Deleted account: id={}", id);

        // VERIFY DELETED — GET should return 404
        APIResponse getAfterDelete = get("/api/accounts/" + id);
        assertThat(getAfterDelete.status()).isEqualTo(404);
    }

    @Test
    @DisplayName("Create account with parent inherits type and normal balance")
    void createWithParent() throws Exception {
        // First, find the parent account "2.1" from the list
        APIResponse listResponse = get("/api/accounts?search=2.1&page=0&size=100");
        assertThat(listResponse.status()).isEqualTo(200);

        JsonNode listResult = parse(listResponse);
        String parentId = null;
        for (JsonNode item : listResult.get("content")) {
            if ("2.1".equals(item.get("accountCode").asText())) {
                parentId = item.get("id").asText();
                break;
            }
        }
        assertThat(parentId).as("Parent account 2.1 should exist").isNotNull();

        // Create child account with parent
        Map<String, Object> request = new HashMap<>();
        request.put("accountCode", "2.1.97");
        request.put("accountName", "Hutang Child API Test");
        request.put("accountType", "LIABILITY");
        request.put("parentId", parentId);
        request.put("permanent", true);

        APIResponse createResponse = post("/api/accounts", request);
        assertThat(createResponse.status()).isEqualTo(201);

        JsonNode created = parse(createResponse);
        String id = created.get("id").asText();
        assertThat(created.get("parentId").asText()).isEqualTo(parentId);
        assertThat(created.get("parentCode").asText()).isEqualTo("2.1");
        // Level = parent.level + 1 (seed data imports set level=1 for all accounts)
        int parentLevel = parse(get("/api/accounts/" + parentId)).get("level").asInt();
        assertThat(created.get("level").asInt()).isEqualTo(parentLevel + 1);
        assertThat(created.get("accountType").asText()).isEqualTo("LIABILITY");
        assertThat(created.get("normalBalance").asText()).isEqualTo("CREDIT");

        // Cleanup
        delete("/api/accounts/" + id);
    }

    @Test
    @DisplayName("POST with missing required fields returns 400")
    void createWithInvalidData() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("accountCode", "");
        request.put("accountName", "");

        APIResponse response = post("/api/accounts", request);
        assertThat(response.status()).as(response.text()).isEqualTo(400);
        JsonNode body = parse(response);
        assertThat(body.get("error").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.get("fieldErrors").has("accountCode")).as(response.text()).isTrue();
        assertThat(body.get("fieldErrors").has("accountName")).as(response.text()).isTrue();
    }

    @Test
    @DisplayName("POST with duplicate account code returns 400")
    void createDuplicateCode() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("accountCode", "1.1.01");
        request.put("accountName", "Duplicate Test");
        request.put("accountType", "ASSET");
        request.put("normalBalance", "DEBIT");

        APIResponse response = post("/api/accounts", request);
        assertApiError(response, 400, "INVALID_REQUEST", "Account code already exists: 1.1.01");
    }

    @Test
    @DisplayName("GET non-existent ID returns 404")
    void getNonExistent() throws Exception {
        APIResponse response = get("/api/accounts/00000000-0000-0000-0000-000000000000");
        assertApiError(response, 404, "NOT_FOUND",
                "Account not found with id: 00000000-0000-0000-0000-000000000000");
    }

    @Test
    @DisplayName("DELETE account with children returns 409")
    void deleteAccountWithChildren() throws Exception {
        // Account "1" (ASET) is a root account with children — cannot be deleted
        APIResponse listResponse = get("/api/accounts?search=ASET&page=0&size=10");
        JsonNode listResult = parse(listResponse);
        String rootId = null;
        for (JsonNode item : listResult.get("content")) {
            if ("1".equals(item.get("accountCode").asText())) {
                rootId = item.get("id").asText();
                break;
            }
        }
        assertThat(rootId).as("Root ASET account should exist").isNotNull();

        APIResponse deleteResponse = delete("/api/accounts/" + rootId);
        assertApiError(deleteResponse, 409, "CONFLICT", "Cannot delete account with child accounts");
    }

    // ==================== HELPER METHODS ====================

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

    private APIResponse put(String path, Object data) {
        return apiContext.put(path,
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + accessToken)
                        .setHeader("Content-Type", "application/json")
                        .setData(data));
    }

    private APIResponse delete(String path) {
        return apiContext.delete(path,
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + accessToken));
    }

    private void assertApiError(APIResponse response, int status, String code, String message) throws Exception {
        assertThat(response.status()).as(response.text()).isEqualTo(status);
        JsonNode body = parse(response);
        assertThat(body.get("error").asText()).as(response.text()).isEqualTo(code);
        assertThat(body.get("message").asText()).as(response.text()).isEqualTo(message);
    }

    private JsonNode parse(APIResponse response) throws Exception {
        return objectMapper.readTree(response.text());
    }

    private String authenticateViaDeviceFlow() throws Exception {
        Map<String, String> codeRequest = new HashMap<>();
        codeRequest.put("clientId", "coa-api-test");

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

        page.locator("input[name='deviceName']").fill("CoA API Test Device");
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
