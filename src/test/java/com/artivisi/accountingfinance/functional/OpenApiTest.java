package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests for the OpenAPI spec (springdoc-openapi).
 * Verifies /v3/api-docs is publicly accessible and contains expected structure.
 */
@Slf4j
@DisplayName("OpenAPI Spec - Functional Tests")
@Import(ServiceTestDataInitializer.class)
class OpenApiTest extends PlaywrightTestBase {

    private static final String API_DOCS_URL = "/v3/api-docs";
    private static final String SWAGGER_UI_URL = "/swagger-ui/index.html";

    private APIRequestContext apiContext;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
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
    @DisplayName("GET /v3/api-docs returns 200 without authentication")
    void testPublicAccess() throws Exception {
        APIResponse response = apiContext.get(API_DOCS_URL);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.headers().get("content-type")).contains("application/json");

        log.info("OpenAPI spec returned 200 without auth");
    }

    @Test
    @DisplayName("Response contains required OpenAPI keys")
    void testOpenApiKeys() throws Exception {
        APIResponse response = apiContext.get(API_DOCS_URL);
        JsonNode body = objectMapper.readTree(response.text());

        assertThat(body.has("openapi")).isTrue();
        assertThat(body.has("info")).isTrue();
        assertThat(body.has("paths")).isTrue();
        assertThat(body.has("components")).isTrue();

        log.info("OpenAPI spec has required top-level keys");
    }

    @Test
    @DisplayName("info.title is 'Balaka API'")
    void testInfoTitle() throws Exception {
        APIResponse response = apiContext.get(API_DOCS_URL);
        JsonNode body = objectMapper.readTree(response.text());

        assertThat(body.get("info").get("title").asText()).isEqualTo("Balaka API");

        log.info("Info title verified");
    }

    @Test
    @DisplayName("paths contains spot-check endpoints from each controller")
    void testPathsContainEndpoints() throws Exception {
        APIResponse response = apiContext.get(API_DOCS_URL);
        JsonNode paths = objectMapper.readTree(response.text()).get("paths");

        // Spot-check one endpoint per controller tag
        assertThat(paths.has("/api/device/code")).as("DeviceAuth").isTrue();
        assertThat(paths.has("/api/drafts")).as("DraftTransaction").isTrue();
        assertThat(paths.has("/api/transactions")).as("Transaction").isTrue();
        assertThat(paths.has("/api/analysis/company")).as("FinancialAnalysis").isTrue();
        assertThat(paths.has("/api/templates")).as("Template").isTrue();
        assertThat(paths.has("/api/bank-reconciliation/parser-configs")).as("BankRecon").isTrue();
        assertThat(paths.has("/api/bills")).as("Bill").isTrue();
        assertThat(paths.has("/api/data-import")).as("DataImport").isTrue();
        assertThat(paths.has("/api/tax-export/efaktur-keluaran")).as("TaxExport").isTrue();

        log.info("Spot-checked {} paths", paths.size());
    }

    @Test
    @DisplayName("x-workflows extension present with expected count")
    void testWorkflowsExtension() throws Exception {
        APIResponse response = apiContext.get(API_DOCS_URL);
        JsonNode body = objectMapper.readTree(response.text());

        JsonNode workflows = body.get("x-workflows");
        assertThat(workflows).as("x-workflows present").isNotNull();
        assertThat(workflows.isArray()).isTrue();
        assertThat(workflows.size()).isEqualTo(18);

        log.info("x-workflows: {} workflows", workflows.size());
    }

    @Test
    @DisplayName("x-csv-files extension present")
    void testCsvFilesExtension() throws Exception {
        APIResponse response = apiContext.get(API_DOCS_URL);
        JsonNode body = objectMapper.readTree(response.text());

        JsonNode csvFiles = body.get("x-csv-files");
        assertThat(csvFiles).as("x-csv-files present").isNotNull();
        assertThat(csvFiles.isArray()).isTrue();
        assertThat(csvFiles.size()).isEqualTo(16);

        log.info("x-csv-files: {} files", csvFiles.size());
    }

    @Test
    @DisplayName("x-industries has 4 items")
    void testIndustriesExtension() throws Exception {
        APIResponse response = apiContext.get(API_DOCS_URL);
        JsonNode body = objectMapper.readTree(response.text());

        JsonNode industries = body.get("x-industries");
        assertThat(industries).as("x-industries present").isNotNull();
        assertThat(industries.isArray()).isTrue();
        assertThat(industries.size()).isEqualTo(4);

        log.info("x-industries: {}", industries);
    }

    @Test
    @DisplayName("x-error-codes extension present")
    void testErrorCodesExtension() throws Exception {
        APIResponse response = apiContext.get(API_DOCS_URL);
        JsonNode body = objectMapper.readTree(response.text());

        JsonNode errorCodes = body.get("x-error-codes");
        assertThat(errorCodes).as("x-error-codes present").isNotNull();
        assertThat(errorCodes.isObject()).isTrue();
        assertThat(errorCodes.has("UNAUTHORIZED")).isTrue();
        assertThat(errorCodes.has("VALIDATION_ERROR")).isTrue();

        log.info("x-error-codes verified");
    }

    @Test
    @DisplayName("x-authentication extension present with scopes")
    void testAuthenticationExtension() throws Exception {
        APIResponse response = apiContext.get(API_DOCS_URL);
        JsonNode body = objectMapper.readTree(response.text());

        JsonNode auth = body.get("x-authentication");
        assertThat(auth).as("x-authentication present").isNotNull();
        assertThat(auth.get("type").asText()).contains("Device Authorization");
        assertThat(auth.has("scopes")).isTrue();
        assertThat(auth.get("scopes").isArray()).isTrue();
        assertThat(auth.get("scopes").size()).isEqualTo(8);

        log.info("x-authentication: {} scopes", auth.get("scopes").size());
    }

    @Test
    @DisplayName("MVC controllers are hidden from OpenAPI spec")
    void testMvcControllersHidden() throws Exception {
        APIResponse response = apiContext.get(API_DOCS_URL);
        JsonNode body = objectMapper.readTree(response.text());

        // Collect all tags used by endpoints
        java.util.Set<String> tags = new java.util.HashSet<>();
        body.get("paths").fields().forEachRemaining(pathEntry ->
            pathEntry.getValue().fields().forEachRemaining(methodEntry -> {
                JsonNode tagArray = methodEntry.getValue().get("tags");
                if (tagArray != null && tagArray.isArray()) {
                    tagArray.forEach(tag -> tags.add(tag.asText()));
                }
            })
        );

        // MVC controller auto-generated tags must NOT appear
        assertThat(tags).as("No MVC controller tags in spec")
                .doesNotContain(
                        "transaction-controller",
                        "journal-template-controller",
                        "report-controller",
                        "settings-controller",
                        "telegram-webhook-controller",
                        "document-controller",
                        "draft-transaction-controller",
                        "journal-entry-controller",
                        "tax-calendar-controller"
                );

        // No paths outside /api/** should appear
        java.util.List<String> nonApiPaths = new java.util.ArrayList<>();
        body.get("paths").fieldNames().forEachRemaining(path -> {
            if (!path.startsWith("/api/")) {
                nonApiPaths.add(path);
            }
        });
        assertThat(nonApiPaths).as("No non-API paths in spec").isEmpty();

        log.info("MVC controllers verified hidden. Tags present: {}", tags);
    }

    @Test
    @DisplayName("GET /swagger-ui/index.html returns 200")
    void testSwaggerUiAccess() {
        APIResponse response = apiContext.get(SWAGGER_UI_URL);

        assertThat(response.status()).isEqualTo(200);

        log.info("Swagger UI returned 200");
    }
}
