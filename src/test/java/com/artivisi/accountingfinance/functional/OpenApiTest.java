package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.security.ApiScope;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
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
    @DisplayName("GET /v3/api-docs is public and describes Balaka API with bearer authentication")
    void testPublicSpecHeader() throws Exception {
        APIResponse response = apiContext.get(API_DOCS_URL);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.headers().get("content-type")).startsWith("application/json");
        JsonNode body = objectMapper.readTree(response.text());
        assertThat(body.get("openapi").asText()).startsWith("3.");
        assertThat(body.get("info").get("title").asText()).isEqualTo("Balaka API");
        assertThat(body.get("info").get("license").get("name").asText()).isEqualTo("Apache License, Version 2.0");
        assertThat(body.get("info").get("license").get("url").asText()).isEqualTo("https://www.apache.org/licenses/LICENSE-2.0");
        JsonNode bearer = body.get("components").get("securitySchemes").get("bearer");
        assertThat(bearer.get("type").asText()).isEqualTo("http");
        assertThat(bearer.get("scheme").asText()).isEqualTo("bearer");
        assertThat(bearer.get("bearerFormat").asText()).isEqualTo("opaque");
        assertThat(body.get("security")).hasSize(1);
        assertThat(body.get("security").get(0).has("bearer")).isTrue();
    }

    @Test
    @DisplayName("info.version and description reflect the build's git.properties")
    void testInfoVersionFromBuild() throws Exception {
        java.util.Properties git = new java.util.Properties();
        try (java.io.InputStream is = getClass().getResourceAsStream("/git.properties")) {
            assertThat(is).as("git.properties on classpath").isNotNull();
            git.load(is);
        }
        String expectedVersion = git.getProperty("git.build.version");
        String expectedCommit = git.getProperty("git.commit.id.abbrev");
        assertThat(expectedVersion).isNotBlank();
        assertThat(expectedCommit).isNotBlank();

        APIResponse response = apiContext.get(API_DOCS_URL);
        JsonNode info = objectMapper.readTree(response.text()).get("info");

        assertThat(info.get("version").asText()).isEqualTo(expectedVersion);
        assertThat(info.get("description").asText()).contains("commit " + expectedCommit);

        log.info("info.version={} commit={}", expectedVersion, expectedCommit);
    }

    @Test
    @DisplayName("paths contains one operation per API controller with its HTTP method")
    void testPathsContainEndpoints() throws Exception {
        APIResponse response = apiContext.get(API_DOCS_URL);
        JsonNode paths = objectMapper.readTree(response.text()).get("paths");

        String[][] operations = {
                {"/api/device/code", "post", "DeviceAuth"},
                {"/api/drafts", "post", "DraftTransaction"},
                {"/api/transactions", "post", "Transaction"},
                {"/api/transactions/{id}", "put", "Transaction"},
                {"/api/analysis/company", "get", "FinancialAnalysis"},
                {"/api/templates", "get", "Template"},
                {"/api/bank-reconciliation/parser-configs", "get", "BankRecon"},
                {"/api/bills", "post", "Bill"},
                {"/api/data-import", "post", "DataImport"},
                {"/api/tax-export/efaktur-keluaran", "get", "TaxExport"},
        };
        for (String[] op : operations) {
            assertThat(paths.path(op[0]).has(op[1])).as("%s %s %s", op[2], op[1].toUpperCase(), op[0]).isTrue();
        }
    }

    @Test
    @DisplayName("x-workflows extension present with named workflows")
    void testWorkflowsExtension() throws Exception {
        APIResponse response = apiContext.get(API_DOCS_URL);
        JsonNode body = objectMapper.readTree(response.text());

        JsonNode workflows = body.get("x-workflows");
        assertThat(workflows).as("x-workflows present").isNotNull();
        assertThat(workflows.isArray()).isTrue();

        // Named rather than counted: a bare count breaks on every workflow the API gains
        java.util.Set<String> names = new java.util.HashSet<>();
        workflows.forEach(workflow -> {
            assertThat(workflow.hasNonNull("name")).isTrue();
            assertThat(workflow.get("steps").isArray()).isTrue();
            names.add(workflow.get("name").asText());
        });
        assertThat(names).contains(
                "Receipt-Based Transaction", "Financial Analysis", "Bank Reconciliation",
                "Payroll Processing", "Tax Export for SPT Preparation",
                "Tax Filing Register (SPT / BPE / STP per masa)");

        log.info("x-workflows: {} workflows", names.size());
    }

    @Test
    @DisplayName("x-csv-files, x-industries, x-error-codes and x-workflows are served exactly as configured in openapi/extensions.json")
    void testStaticExtensionsMatchConfiguration() throws Exception {
        JsonNode configured;
        try (InputStream is = getClass().getResourceAsStream("/openapi/extensions.json")) {
            assertThat(is).as("openapi/extensions.json on classpath").isNotNull();
            configured = objectMapper.readTree(is);
        }
        JsonNode body = objectMapper.readTree(apiContext.get(API_DOCS_URL).text());

        for (String key : List.of("x-csv-files", "x-industries", "x-error-codes", "x-workflows")) {
            assertThat(body.get(key)).as(key).isEqualTo(configured.get(key));
        }
        assertThat(body.get("x-industries")).containsExactly(
                TextNode.valueOf("it-service"), TextNode.valueOf("online-seller"),
                TextNode.valueOf("coffee-shop"), TextNode.valueOf("campus"));
        assertThat(body.get("x-error-codes").get("UNAUTHORIZED").asText()).isEqualTo("401 - Missing or invalid Bearer token");
        assertThat(body.get("x-error-codes").get("VALIDATION_ERROR").asText()).isEqualTo("400 - Request body validation failed");
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

        // Named rather than counted: a bare count breaks on every scope the API gains
        java.util.Set<String> scopes = new java.util.HashSet<>();
        auth.get("scopes").forEach(scope -> {
            assertThat(scope.hasNonNull("scope")).isTrue();
            assertThat(scope.hasNonNull("description")).isTrue();
            scopes.add(scope.get("scope").asText());
        });
        assertThat(scopes).contains(
                "drafts:create", "drafts:approve", "drafts:read",
                "analysis:read", "analysis:write", "transactions:post", "data:import",
                "tax-export:read", "assets:read", "assets:write",
                "tax-filings:read", "tax-filings:write");

        List<String> served = new ArrayList<>();
        auth.get("scopes").forEach(scope -> served.add(scope.get("scope").asText() + " | "
                + scope.get("description").asText() + " | " + scope.get("grantedWithPermissions")));
        List<String> registry = Arrays.stream(ApiScope.values())
                .map(s -> s.scope() + " | " + s.description() + " | "
                        + objectMapper.valueToTree(s.grantingPermissions().stream().sorted().toList()))
                .toList();
        assertThat(served).containsExactlyElementsOf(registry);

        log.info("x-authentication: {} scopes", scopes.size());
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
    @DisplayName("Swagger UI renders the Balaka API spec with its operations")
    void testSwaggerUiRendersSpec() {
        navigateTo(SWAGGER_UI_URL);

        assertThat(page.locator(".swagger-ui .info .title")).containsText("Balaka API");
        assertThat(page.locator(".opblock-summary-path[data-path='/api/transactions']").first()).isVisible();
    }
}
