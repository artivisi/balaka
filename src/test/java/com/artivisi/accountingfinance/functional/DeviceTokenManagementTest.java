package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.DeviceCodeRepository;
import com.artivisi.accountingfinance.repository.DeviceTokenRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.HashMap;
import java.util.Map;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Device Token Management - Self-Service UI")
@Import(ServiceTestDataInitializer.class)
class DeviceTokenManagementTest extends PlaywrightTestBase {

    @Autowired
    private DeviceTokenRepository deviceTokenRepository;

    @Autowired
    private DeviceCodeRepository deviceCodeRepository;

    private APIRequestContext apiContext;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        apiContext = playwright.request().newContext(new APIRequest.NewContextOptions()
                .setBaseURL(baseUrl()));

        // Clean up device tokens and codes from previous tests
        deviceTokenRepository.deleteAll();
        deviceCodeRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        if (apiContext != null) {
            apiContext.dispose();
        }
    }

    @Test
    @DisplayName("Page loads with empty state when no tokens exist")
    void testEmptyState() {
        loginAsAdmin();
        navigateTo("/settings/devices");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Perangkat API");
        assertThat(page.locator("#empty-state")).isVisible();
        assertThat(page.locator("#empty-state")).hasText("Tidak ada sesi perangkat aktif");
    }

    @Test
    @DisplayName("Token appears in table after device auth flow")
    void testTokenAppearsAfterAuth() throws Exception {
        String accessToken = completeDeviceAuthFlow("test-client-1");

        // Use the token so lastUsedAt gets updated
        apiContext.get("/api/drafts/templates",
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + accessToken));

        // Navigate to device management page
        loginAsAdmin();
        navigateTo("/settings/devices");
        waitForPageLoad();

        // Verify token is in the table
        Locator table = page.locator("#device-tokens-table");
        assertThat(table).isVisible();
        assertThat(table.locator("tbody tr")).hasCount(1);

        // Verify clientId column
        Locator row = table.locator("tbody tr").first();
        assertThat(row.locator("td").nth(1)).hasText("test-client-1");

        // Verify status badge
        assertThat(row.locator("text=Aktif")).isVisible();
    }

    @Test
    @DisplayName("Revoke individual token removes it from table")
    void testRevokeSingleToken() throws Exception {
        completeDeviceAuthFlow("revoke-test");

        loginAsAdmin();
        navigateTo("/settings/devices");
        waitForPageLoad();

        // Verify token exists
        Locator table = page.locator("#device-tokens-table");
        assertThat(table).isVisible();
        assertThat(table.locator("tbody tr")).hasCount(1);

        // Accept confirm dialog
        page.onDialog(dialog -> dialog.accept());

        // Click revoke button
        page.locator(".form-revoke-session button[type='submit']").first().click();
        waitForPageLoad();

        // Verify success message and empty state
        assertThat(page.locator("text=Sesi perangkat berhasil dicabut")).isVisible();
        assertThat(page.locator("#empty-state")).isVisible();
    }

    @Test
    @DisplayName("Revoke all tokens clears the table")
    void testRevokeAllTokens() throws Exception {
        completeDeviceAuthFlow("client-a");
        completeDeviceAuthFlow("client-b");

        loginAsAdmin();
        navigateTo("/settings/devices");
        waitForPageLoad();

        // Verify both tokens exist
        Locator table = page.locator("#device-tokens-table");
        assertThat(table).isVisible();
        assertThat(table.locator("tbody tr")).hasCount(2);

        // Accept confirm dialog
        page.onDialog(dialog -> dialog.accept());

        // Click revoke all button
        page.locator("#form-revoke-all button[type='submit']").click();
        waitForPageLoad();

        // Verify success message and empty state
        assertThat(page.locator("text=2 sesi perangkat berhasil dicabut")).isVisible();
        assertThat(page.locator("#empty-state")).isVisible();
    }

    @Test
    @DisplayName("Sidebar link navigates to device management page")
    void testSidebarNavigation() {
        loginAsAdmin();
        navigateTo("/settings/devices");
        waitForPageLoad();

        // When on devices page, verify the sidebar link is highlighted
        Locator navDevices = page.locator("#nav-devices");
        assertThat(navDevices).hasClass(java.util.regex.Pattern.compile(".*bg-primary-600.*"));

        // Verify page title
        assertThat(page.locator("#page-title")).hasText("Perangkat API");
    }

    /**
     * Complete the device auth flow and return the access token.
     */
    private String completeDeviceAuthFlow(String clientId) throws Exception {
        // Step 1: Request device code
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("clientId", clientId);

        APIResponse codeResponse = apiContext.post("/api/device/code",
                RequestOptions.create()
                        .setHeader("Content-Type", "application/json")
                        .setData(requestBody));
        assertThat(codeResponse.ok()).isTrue();

        JsonNode codeData = objectMapper.readTree(codeResponse.text());
        String deviceCode = codeData.get("deviceCode").asText();
        String userCode = codeData.get("userCode").asText();
        int interval = codeData.get("interval").asInt();

        // Step 2: Authorize via browser
        loginAsAdmin();
        navigateTo("/device?code=" + userCode);
        waitForPageLoad();

        page.locator("button[type='submit']:has-text('Otorisasi Perangkat')").click();
        waitForPageLoad();

        // Step 3: Poll for token
        Map<String, String> tokenRequestBody = new HashMap<>();
        tokenRequestBody.put("deviceCode", deviceCode);

        String accessToken = null;
        for (int attempt = 0; attempt < 10; attempt++) {
            Thread.sleep(interval * 1000L);
            APIResponse tokenResponse = apiContext.post("/api/device/token",
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setData(tokenRequestBody));
            if (tokenResponse.ok()) {
                JsonNode tokenData = objectMapper.readTree(tokenResponse.text());
                accessToken = tokenData.get("accessToken").asText();
                break;
            }
        }

        assertThat(accessToken).isNotNull().isNotEmpty();
        return accessToken;
    }
}
