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
 * Functional tests for GET /api/capabilities.json (static resource).
 * Verifies the file is publicly accessible and contains expected structure.
 */
@Slf4j
@DisplayName("Capabilities API - Functional Tests")
@Import(ServiceTestDataInitializer.class)
class CapabilitiesApiTest extends PlaywrightTestBase {

    private static final String CAPABILITIES_URL = "/api/capabilities.json";

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
    @DisplayName("GET /api/capabilities.json returns 200 without authentication")
    void testPublicAccess() throws Exception {
        APIResponse response = apiContext.get(CAPABILITIES_URL);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.headers().get("content-type")).contains("application/json");

        log.info("Capabilities endpoint returned 200 without auth");
    }

    @Test
    @DisplayName("Response contains required top-level keys")
    void testTopLevelKeys() throws Exception {
        APIResponse response = apiContext.get(CAPABILITIES_URL);
        JsonNode body = objectMapper.readTree(response.text());

        assertThat(body.has("application")).isTrue();
        assertThat(body.has("description")).isTrue();
        assertThat(body.has("authentication")).isTrue();
        assertThat(body.has("endpointGroups")).isTrue();
        assertThat(body.has("workflows")).isTrue();
        assertThat(body.has("industries")).isTrue();
        assertThat(body.has("errorCodes")).isTrue();

        assertThat(body.get("application").asText()).isEqualTo("Aplikasi Akunting");

        log.info("All top-level keys present");
    }

    @Test
    @DisplayName("Authentication section describes device flow")
    void testAuthenticationSection() throws Exception {
        APIResponse response = apiContext.get(CAPABILITIES_URL);
        JsonNode body = objectMapper.readTree(response.text());

        JsonNode auth = body.get("authentication");
        assertThat(auth.get("type").asText()).contains("Device Authorization");
        assertThat(auth.get("steps").isArray()).isTrue();
        assertThat(auth.get("steps").size()).isEqualTo(3);
        assertThat(auth.has("scopes")).isTrue();
        assertThat(auth.get("scopes").isArray()).isTrue();
        assertThat(auth.get("scopes").size()).isGreaterThan(0);

        log.info("Authentication section: {} scopes", auth.get("scopes").size());
    }

    @Test
    @DisplayName("Endpoint groups contain expected categories")
    void testEndpointGroups() throws Exception {
        APIResponse response = apiContext.get(CAPABILITIES_URL);
        JsonNode body = objectMapper.readTree(response.text());

        JsonNode groups = body.get("endpointGroups");
        assertThat(groups.isArray()).isTrue();
        assertThat(groups.size()).isGreaterThanOrEqualTo(5);

        boolean hasDrafts = false;
        boolean hasTransactions = false;
        boolean hasAnalysis = false;
        boolean hasReports = false;
        boolean hasBankRecon = false;
        boolean hasDataImport = false;

        for (JsonNode group : groups) {
            assertThat(group.has("name")).isTrue();
            assertThat(group.has("description")).isTrue();
            assertThat(group.has("endpoints")).isTrue();
            assertThat(group.get("endpoints").isArray()).isTrue();
            assertThat(group.get("endpoints").size()).isGreaterThan(0);

            String name = group.get("name").asText();
            if (name.contains("Draft")) hasDrafts = true;
            if (name.contains("Transaction")) hasTransactions = true;
            if (name.contains("Financial Analysis")) hasAnalysis = true;
            if (name.contains("Analysis Report")) hasReports = true;
            if (name.contains("Bank Reconciliation")) hasBankRecon = true;
            if (name.contains("Data Import")) hasDataImport = true;

            // Verify endpoint structure
            JsonNode firstEndpoint = group.get("endpoints").get(0);
            assertThat(firstEndpoint.has("method")).isTrue();
            assertThat(firstEndpoint.has("path")).isTrue();
            assertThat(firstEndpoint.has("scope")).isTrue();
            assertThat(firstEndpoint.has("description")).isTrue();
            assertThat(firstEndpoint.has("response")).isTrue();
        }

        assertThat(hasDrafts).as("Draft Transactions group").isTrue();
        assertThat(hasTransactions).as("Transactions group").isTrue();
        assertThat(hasAnalysis).as("Financial Analysis group").isTrue();
        assertThat(hasReports).as("Analysis Reports group").isTrue();
        assertThat(hasBankRecon).as("Bank Reconciliation group").isTrue();
        assertThat(hasDataImport).as("Data Import group").isTrue();

        log.info("Endpoint groups verified: {} groups", groups.size());
    }

    @Test
    @DisplayName("Data Import group includes CSV file specs for onboarding")
    void testDataImportCsvSpecs() throws Exception {
        APIResponse response = apiContext.get(CAPABILITIES_URL);
        JsonNode body = objectMapper.readTree(response.text());

        JsonNode dataImportGroup = null;
        for (JsonNode group : body.get("endpointGroups")) {
            if ("Data Import".equals(group.get("name").asText())) {
                dataImportGroup = group;
                break;
            }
        }
        assertThat(dataImportGroup).as("Data Import group exists").isNotNull();

        // Description mentions PDF/XLS onboarding use case
        String description = dataImportGroup.get("description").asText();
        assertThat(description).contains("PDF/XLS");
        assertThat(description).contains("onboarding");

        // csvFiles array present with specs
        JsonNode csvFiles = dataImportGroup.get("csvFiles");
        assertThat(csvFiles).as("csvFiles array").isNotNull();
        assertThat(csvFiles.isArray()).isTrue();
        assertThat(csvFiles.size()).isGreaterThanOrEqualTo(4);

        // Essential files have column specs
        JsonNode companyConfig = csvFiles.get(0);
        assertThat(companyConfig.get("filename").asText()).isEqualTo("01_company_config.csv");
        assertThat(companyConfig.has("columns")).isTrue();

        JsonNode coa = csvFiles.get(1);
        assertThat(coa.get("filename").asText()).isEqualTo("02_chart_of_accounts.csv");
        assertThat(coa.has("columns")).isTrue();

        log.info("Data Import CSV specs verified: {} files", csvFiles.size());
    }

    @Test
    @DisplayName("Workflows include client onboarding from financial reports")
    void testOnboardingWorkflow() throws Exception {
        APIResponse response = apiContext.get(CAPABILITIES_URL);
        JsonNode body = objectMapper.readTree(response.text());

        JsonNode workflows = body.get("workflows");
        assertThat(workflows.isArray()).isTrue();
        assertThat(workflows.size()).isGreaterThanOrEqualTo(5);

        boolean hasOnboarding = false;
        for (JsonNode workflow : workflows) {
            assertThat(workflow.has("name")).isTrue();
            assertThat(workflow.has("steps")).isTrue();
            assertThat(workflow.get("steps").isArray()).isTrue();

            if (workflow.get("name").asText().contains("Onboarding")) {
                hasOnboarding = true;
                JsonNode steps = workflow.get("steps");
                assertThat(steps.size()).isGreaterThanOrEqualTo(4);
                // First step mentions PDF/XLS
                assertThat(steps.get(0).asText()).contains("PDF/XLS");
            }
        }

        assertThat(hasOnboarding).as("Client onboarding workflow").isTrue();

        log.info("Workflows verified: {} workflows", workflows.size());
    }

    @Test
    @DisplayName("Industries list is populated")
    void testIndustries() throws Exception {
        APIResponse response = apiContext.get(CAPABILITIES_URL);
        JsonNode body = objectMapper.readTree(response.text());

        JsonNode industries = body.get("industries");
        assertThat(industries.isArray()).isTrue();
        assertThat(industries.size()).isEqualTo(4);

        log.info("Industries: {}", industries);
    }
}
