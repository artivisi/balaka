package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.DeviceToken;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.DeviceTokenRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.security.ApiScope;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * /settings/devices lists only the logged-in user's sessions, so every test works as its own
 * freshly created admin. Sessions other classes create for "admin" never appear here, and
 * revoking here never touches them.
 */
@DisplayName("Device Token Management - Self-Service UI")
@Import(ServiceTestDataInitializer.class)
class DeviceTokenManagementTest extends PlaywrightTestBase {

    @Autowired
    private DeviceTokenRepository deviceTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private APIRequestContext apiContext;
    private ObjectMapper objectMapper;
    private User user;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        apiContext = playwright.request().newContext(new APIRequest.NewContextOptions()
                .setBaseURL(baseUrl()));
        user = createAdmin();
    }

    @AfterEach
    void tearDown() {
        if (apiContext != null) {
            apiContext.dispose();
        }
    }

    @Test
    @DisplayName("User without device sessions sees the empty state")
    void testEmptyState() {
        loginAsUser();
        navigateTo("/settings/devices");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Perangkat API");
        assertThat(page.locator("#empty-state")).hasText("Tidak ada sesi perangkat aktif");
        assertThat(page.locator("#device-tokens-table")).hasCount(0);
    }

    @Test
    @DisplayName("Token issued by the device flow is listed with client id, scopes, status and last use")
    void testTokenAppearsAfterAuth() throws Exception {
        String clientId = "dtm-client-" + shortId();
        String accessToken = completeDeviceAuthFlow(clientId);
        assertThat(apiStatus(accessToken)).isEqualTo(200);

        List<DeviceToken> tokens = deviceTokenRepository.findActiveByUserId(user.getId());
        assertThat(tokens).hasSize(1);
        DeviceToken token = tokens.getFirst();
        assertThat(token.getClientId()).isEqualTo(clientId);
        assertThat(token.getLastUsedAt()).isNotNull();

        navigateTo("/settings/devices");
        waitForPageLoad();

        Locator rows = page.locator("#device-tokens-table tbody tr");
        assertThat(rows).hasCount(1);
        takeManualScreenshot("settings/devices");

        Locator cells = rows.first().locator("td");
        assertThat(cells.nth(0)).hasText("-");
        assertThat(cells.nth(1)).hasText(clientId);
        assertThat(cells.nth(2)).hasText(ApiScope.allScopesJoined());
        assertThat(cells.nth(3)).hasText("Aktif");
        assertThat(cells.nth(5)).not().hasText("-");
        assertThat(cells.nth(6)).hasText(token.getLastUsedIp());
    }

    @Test
    @DisplayName("Revoking a session removes it from the table and the token is rejected by the API")
    void testRevokeSingleToken() throws Exception {
        String accessToken = completeDeviceAuthFlow("dtm-revoke-" + shortId());
        DeviceToken token = deviceTokenRepository.findActiveByUserId(user.getId()).getFirst();

        navigateTo("/settings/devices");
        waitForPageLoad();
        assertThat(page.locator("#device-tokens-table tbody tr")).hasCount(1);

        page.onDialog(dialog -> dialog.accept());
        page.locator(".form-revoke-session button[type='submit']").click();
        waitForPageLoad();

        assertThat(page.locator("[data-testid='flash-success']")).containsText("Sesi perangkat berhasil dicabut");
        assertThat(page.locator("#empty-state")).isVisible();
        DeviceToken revoked = deviceTokenRepository.findById(token.getId()).orElseThrow();
        assertThat(revoked.getRevokedAt()).isNotNull();
        assertThat(revoked.getRevokedBy()).isEqualTo(user.getUsername());
        assertThat(apiStatus(accessToken)).isEqualTo(401);
    }

    @Test
    @DisplayName("Revoke all revokes every session of the user and both tokens are rejected by the API")
    void testRevokeAllTokens() throws Exception {
        String first = completeDeviceAuthFlow("dtm-a-" + shortId());
        String second = completeDeviceAuthFlow("dtm-b-" + shortId());

        navigateTo("/settings/devices");
        waitForPageLoad();
        assertThat(page.locator("#device-tokens-table tbody tr")).hasCount(2);

        page.onDialog(dialog -> dialog.accept());
        page.locator("#form-revoke-all button[type='submit']").click();
        waitForPageLoad();

        assertThat(page.locator("[data-testid='flash-success']")).containsText("2 sesi perangkat berhasil dicabut");
        assertThat(page.locator("#empty-state")).isVisible();
        assertThat(deviceTokenRepository.findActiveByUserId(user.getId())).isEmpty();
        assertThat(apiStatus(first)).isEqualTo(401);
        assertThat(apiStatus(second)).isEqualTo(401);
    }

    @Test
    @DisplayName("Sidebar link to the device page is highlighted on that page")
    void testSidebarNavigation() {
        loginAsUser();
        navigateTo("/settings/devices");
        waitForPageLoad();

        Locator navDevices = page.locator("#nav-devices");
        assertThat(navDevices).hasAttribute("href", "/settings/devices");
        assertThat(navDevices).hasClass(Pattern.compile(".*bg-primary-600.*"));
        assertThat(page.locator("#page-title")).hasText("Perangkat API");
    }

    private void loginAsUser() {
        login(user.getUsername(), "password");
    }

    private int apiStatus(String accessToken) {
        return apiContext.get("/api/drafts/templates",
                RequestOptions.create().setHeader("Authorization", "Bearer " + accessToken)).status();
    }

    private User createAdmin() {
        String id = shortId();
        User admin = new User();
        admin.setUsername("dtm-admin-" + id);
        admin.setPassword(passwordEncoder.encode("password"));
        admin.setFullName("Pengguna Perangkat " + id);
        admin.setEmail("dtm-" + id + "@example.com");
        admin.setActive(true);
        admin.addRole(Role.ADMIN, "system");
        return userRepository.save(admin);
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    /**
     * Runs the device flow authorized in the browser by {@link #user}; leaves the browser logged in as that user.
     */
    private String completeDeviceAuthFlow(String clientId) throws Exception {
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

        loginAsUser();
        navigateTo("/device?code=" + userCode);
        waitForPageLoad();

        page.locator("button[type='submit']:has-text('Otorisasi Perangkat')").click();
        waitForPageLoad();

        Map<String, String> tokenRequestBody = new HashMap<>();
        tokenRequestBody.put("deviceCode", deviceCode);

        AtomicReference<String> tokenRef = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(interval * 10L)).pollInterval(Duration.ofSeconds(interval)).until(() -> {
            APIResponse tokenResponse = apiContext.post("/api/device/token",
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setData(tokenRequestBody));
            if (tokenResponse.ok()) {
                JsonNode tokenData = objectMapper.readTree(tokenResponse.text());
                tokenRef.set(tokenData.get("accessToken").asText());
                return true;
            }
            return false;
        });

        String accessToken = tokenRef.get();
        assertThat(accessToken).isNotNull().isNotEmpty();
        return accessToken;
    }
}
