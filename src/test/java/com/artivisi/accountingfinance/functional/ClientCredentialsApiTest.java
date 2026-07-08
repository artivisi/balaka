package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests for the OAuth2 client_credentials grant (issue #28):
 * admin registers an API client in the settings UI, a headless service
 * exchanges the credentials for a bearer token and posts to the GL.
 */
@Slf4j
@DisplayName("OAuth2 Client Credentials - Functional Tests")
@Import(ServiceTestDataInitializer.class)
class ClientCredentialsApiTest extends PlaywrightTestBase {

    private APIRequestContext apiContext;
    private ObjectMapper objectMapper;

    @Autowired
    private ChartOfAccountRepository chartOfAccountRepository;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
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
    @DisplayName("Full flow: register client in UI, obtain token, post journal entry")
    void fullClientCredentialsFlow() throws Exception {
        String[] credentials = createClientViaUi("ar-outbox-" + System.currentTimeMillis(), "transactions:post");
        String clientId = credentials[0];
        String clientSecret = credentials[1];

        // Token via form parameters (RFC 6749 §4.4)
        APIResponse tokenResponse = tokenRequest(Map.of(
                "grant_type", "client_credentials",
                "client_id", clientId,
                "client_secret", clientSecret));
        assertThat(tokenResponse.status())
                .as("Token issuance: " + tokenResponse.text())
                .isEqualTo(200);

        JsonNode token = parse(tokenResponse);
        String accessToken = token.get("access_token").asText();
        assertThat(accessToken).isNotBlank();
        assertThat(token.get("token_type").asText()).isEqualTo("Bearer");
        assertThat(token.get("expires_in").asLong()).isGreaterThan(0);
        assertThat(token.get("scope").asText()).contains("transactions:post");

        // Use the token for an unattended GL posting (scope transactions:post)
        String debitAccountId = chartOfAccountRepository.findByAccountCode("1.1.02").orElseThrow().getId().toString();
        String creditAccountId = chartOfAccountRepository.findByAccountCode("1.1.01").orElseThrow().getId().toString();

        Map<String, Object> journalRequest = Map.of(
                "transactionDate", "2026-03-01",
                "description", "Posting layanan client_credentials",
                "lines", List.of(
                        Map.of("accountId", debitAccountId, "debit", 100000, "credit", 0),
                        Map.of("accountId", creditAccountId, "debit", 0, "credit", 100000)));

        APIResponse postResponse = apiContext.post("/api/transactions/journal-entry",
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + accessToken)
                        .setHeader("Content-Type", "application/json")
                        .setData(journalRequest));
        assertThat(postResponse.status())
                .as("Service posting: " + postResponse.text())
                .isEqualTo(201);

        // Scope restriction: token lacks assets:read
        APIResponse forbidden = apiContext.get("/api/fixed-assets",
                RequestOptions.create().setHeader("Authorization", "Bearer " + accessToken));
        assertThat(forbidden.status()).isEqualTo(403);
    }

    @Test
    @DisplayName("Token via HTTP Basic authentication")
    void tokenViaBasicAuth() throws Exception {
        String[] credentials = createClientViaUi("basic-auth-" + System.currentTimeMillis(), "transactions:post");
        String basic = Base64.getEncoder().encodeToString(
                (credentials[0] + ":" + credentials[1]).getBytes(StandardCharsets.UTF_8));

        APIResponse response = apiContext.post("/api/oauth/token",
                RequestOptions.create()
                        .setHeader("Authorization", "Basic " + basic)
                        .setForm(FormData.create().set("grant_type", "client_credentials")));

        assertThat(response.status())
                .as("Basic auth token: " + response.text())
                .isEqualTo(200);
        assertThat(parse(response).get("access_token").asText()).isNotBlank();
    }

    @Test
    @DisplayName("Wrong secret returns 401 invalid_client")
    void wrongSecretReturns401() throws Exception {
        String[] credentials = createClientViaUi("wrong-secret-" + System.currentTimeMillis(), "transactions:post");

        APIResponse response = tokenRequest(Map.of(
                "grant_type", "client_credentials",
                "client_id", credentials[0],
                "client_secret", "definitely-wrong"));

        assertThat(response.status()).isEqualTo(401);
        assertThat(parse(response).get("error").asText()).isEqualTo("invalid_client");
    }

    @Test
    @DisplayName("Unsupported grant type returns 400")
    void unsupportedGrantTypeReturns400() throws Exception {
        APIResponse response = tokenRequest(Map.of(
                "grant_type", "password",
                "client_id", "whatever",
                "client_secret", "whatever"));

        assertThat(response.status()).isEqualTo(400);
        assertThat(parse(response).get("error").asText()).isEqualTo("unsupported_grant_type");
    }

    @Test
    @DisplayName("Deactivated client can no longer obtain tokens")
    void deactivatedClientReturns401() throws Exception {
        String name = "deact-" + System.currentTimeMillis();
        String[] credentials = createClientViaUi(name, "transactions:post");

        // Deactivate through the UI
        navigateTo("/settings/api-clients");
        waitForPageLoad();
        page.locator("#api-client-" + credentials[0] + " button:has-text('Nonaktifkan')").click();
        waitForPageLoad();

        APIResponse response = tokenRequest(Map.of(
                "grant_type", "client_credentials",
                "client_id", credentials[0],
                "client_secret", credentials[1]));

        assertThat(response.status()).isEqualTo(401);
        assertThat(parse(response).get("error").asText()).isEqualTo("invalid_client");
    }

    // ==================== HELPER METHODS ====================

    /**
     * Registers an API client through the settings UI and returns
     * {clientId, clientSecret} from the one-time credentials panel.
     */
    private String[] createClientViaUi(String name, String scopes) {
        loginAsAdmin();
        navigateTo("/settings/api-clients");
        waitForPageLoad();

        page.locator("#client-name").fill(name);
        page.locator("#client-scopes").fill(scopes);
        page.locator("#client-user").selectOption(new com.microsoft.playwright.options.SelectOption().setLabel("admin"));
        page.locator("#btn-create-api-client").click();
        waitForPageLoad();

        String clientId = page.locator("#new-client-id").textContent().trim();
        String clientSecret = page.locator("#new-client-secret").textContent().trim();
        assertThat(clientId).isNotBlank();
        assertThat(clientSecret).isNotBlank();
        log.info("Created API client via UI: {}", clientId);
        return new String[]{clientId, clientSecret};
    }

    private APIResponse tokenRequest(Map<String, String> params) {
        FormData form = FormData.create();
        params.forEach(form::set);
        return apiContext.post("/api/oauth/token", RequestOptions.create().setForm(form));
    }

    private JsonNode parse(APIResponse response) throws Exception {
        return objectMapper.readTree(response.text());
    }
}
