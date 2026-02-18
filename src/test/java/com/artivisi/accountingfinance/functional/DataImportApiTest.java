package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.FilePayload;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@DisplayName("Data Import API - Functional Tests")
@Import(ServiceTestDataInitializer.class)
class DataImportApiTest extends PlaywrightTestBase {

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
    @DisplayName("POST /api/data-import - imports seed data ZIP successfully")
    void testImportSeedDataZip() throws Exception {
        byte[] zipBytes = createZipFromDirectory("industry-seed/it-service/seed-data");

        APIResponse response = post("/api/data-import",
                FormData.create().set("file",
                        new FilePayload("seed-data.zip", "application/zip", zipBytes)));

        assertThat(response.status()).isEqualTo(201);

        JsonNode body = parse(response);
        assertThat(body.get("totalRecords").asInt()).isGreaterThan(0);
        assertThat(body.get("durationMs").asLong()).isGreaterThan(0);

        log.info("Import test passed - totalRecords: {}, documentCount: {}, durationMs: {}",
                body.get("totalRecords"), body.get("documentCount"), body.get("durationMs"));
    }

    @Test
    @DisplayName("POST /api/data-import - rejects non-ZIP file with 400")
    void testRejectNonZipFile() throws Exception {
        byte[] textBytes = "this is not a zip file".getBytes(StandardCharsets.UTF_8);

        APIResponse response = post("/api/data-import",
                FormData.create().set("file",
                        new FilePayload("data.txt", "text/plain", textBytes)));

        assertThat(response.status()).isEqualTo(400);

        JsonNode body = parse(response);
        assertThat(body.has("error")).isTrue();

        log.info("Non-ZIP rejection test passed - status: {}, body: {}",
                response.status(), response.text());
    }

    @Test
    @DisplayName("POST /api/data-import - rejects unauthenticated request with 401")
    void testRejectUnauthenticated() throws Exception {
        byte[] zipBytes = createZipFromDirectory("industry-seed/it-service/seed-data");

        APIResponse response = apiContext.post("/api/data-import",
                RequestOptions.create()
                        .setMultipart(FormData.create().set("file",
                                new FilePayload("seed-data.zip", "application/zip", zipBytes))));

        assertThat(response.status()).isEqualTo(401);

        log.info("Unauthenticated test passed - status: {}", response.status());
    }

    // --- Helpers ---

    private APIResponse post(String path, FormData formData) {
        return apiContext.post(path,
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + accessToken)
                        .setMultipart(formData));
    }

    private JsonNode parse(APIResponse response) throws Exception {
        return objectMapper.readTree(response.text());
    }

    private byte[] createZipFromDirectory(String dirPath) throws IOException {
        Path seedDir = Paths.get(dirPath).toAbsolutePath();

        if (!Files.exists(seedDir)) {
            throw new IOException("Seed data directory not found: " + seedDir);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            Files.walk(seedDir)
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        try {
                            Path relativePath = seedDir.relativize(path);
                            ZipEntry entry = new ZipEntry(relativePath.toString().replace('\\', '/'));
                            zos.putNextEntry(entry);
                            Files.copy(path, zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to zip file: " + path, e);
                        }
                    });
        }

        return baos.toByteArray();
    }

    private String authenticateViaDeviceFlow() throws Exception {
        Map<String, String> codeRequest = new HashMap<>();
        codeRequest.put("clientId", "data-import-test");

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

        page.locator("input[name='deviceName']").fill("Data Import Test Device");
        page.locator("button[type='submit']:has-text('Otorisasi Perangkat')").click();
        waitForPageLoad();

        Map<String, String> tokenRequest = new HashMap<>();
        tokenRequest.put("deviceCode", deviceCode);

        for (int i = 0; i < 10; i++) {
            Thread.sleep(2000);

            APIResponse tokenResponse = apiContext.post("/api/device/token",
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setData(tokenRequest));

            if (tokenResponse.ok()) {
                JsonNode tokenData = objectMapper.readTree(tokenResponse.text());
                return tokenData.get("accessToken").asText();
            }
        }

        throw new RuntimeException("Failed to get access token");
    }
}
