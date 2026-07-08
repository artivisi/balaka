package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.FixedAssetRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Slf4j
@DisplayName("Fixed Asset API - Functional Tests")
@Import(ServiceTestDataInitializer.class)
class FixedAssetApiTest extends PlaywrightTestBase {

    private APIRequestContext apiContext;
    private ObjectMapper objectMapper;
    private String accessToken;

    @Autowired
    private ChartOfAccountRepository chartOfAccountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private FixedAssetRepository fixedAssetRepository;

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
        // Remove assets registered by this class. Linked assets keep an FK to their
        // purchase transaction, which would break later tests that wipe transactions.
        fixedAssetRepository.deleteAll(fixedAssetRepository.findAll().stream()
                .filter(a -> a.getAssetCode().startsWith("API-"))
                .toList());
    }

    @Test
    @DisplayName("GET /categories lists active asset categories with depreciation defaults")
    void listCategories() throws Exception {
        APIResponse response = get("/api/fixed-assets/categories");
        assertThat(response.status()).isEqualTo(200);

        JsonNode categories = parse(response);
        assertThat(categories.isArray()).isTrue();
        assertThat(categories.size()).isGreaterThanOrEqualTo(1);

        JsonNode komputer = findByField(categories, "code", "KOMPUTER");
        assertThat(komputer).as("KOMPUTER category from it-service seed pack").isNotNull();
        assertThat(komputer.get("depreciationMethod").asText()).isEqualTo("STRAIGHT_LINE");
        assertThat(komputer.get("usefulLifeMonths").asInt()).isEqualTo(48);
        assertThat(komputer.get("assetAccountCode").asText()).isEqualTo("1.2.01");
        assertThat(komputer.get("accumulatedDepreciationAccountCode").asText()).isEqualTo("1.2.02");
    }

    @Test
    @DisplayName("CRUD lifecycle: register with funding account, list, detail, update, delete")
    void crudLifecycle() throws Exception {
        String assetCode = "API-AST-" + System.currentTimeMillis();

        // CREATE — funding account path composes an acquisition DRAFT
        Map<String, Object> request = new HashMap<>();
        request.put("assetCode", assetCode);
        request.put("name", "Laptop Uji API");
        request.put("categoryCode", "KOMPUTER");
        request.put("purchaseDate", "2026-06-10");
        request.put("purchaseCost", 24000000);
        request.put("supplier", "iBox");
        request.put("invoiceNumber", "SX1752606000155");
        request.put("serialNumber", "SJL9L0MVNCJ");
        request.put("fundingAccountCode", "1.1.02");

        APIResponse createResponse = post("/api/fixed-assets", request);
        assertThat(createResponse.status())
                .as("Create fixed asset: " + createResponse.text())
                .isEqualTo(201);

        JsonNode created = parse(createResponse);
        String id = created.get("id").asText();
        assertThat(id).isNotBlank();
        assertThat(created.get("assetCode").asText()).isEqualTo(assetCode);
        // Depreciation settings default from the KOMPUTER category
        assertThat(created.get("depreciationMethod").asText()).isEqualTo("STRAIGHT_LINE");
        assertThat(created.get("usefulLifeMonths").asInt()).isEqualTo(48);
        assertThat(created.get("residualValue").asDouble()).isEqualTo(0.0);
        assertThat(created.get("depreciationStartDate").asText()).isEqualTo("2026-06-01");
        assertThat(created.get("bookValue").asDouble()).isEqualTo(24000000.0);
        assertThat(created.get("monthlyDepreciation").asDouble()).isEqualTo(500000.0);
        assertThat(created.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(created.get("assetAccountCode").asText()).isEqualTo("1.2.01");
        log.info("Registered fixed asset: id={}", id);

        // LIST — year filter matches
        APIResponse listResponse = get("/api/fixed-assets?year=2026&status=ACTIVE");
        assertThat(listResponse.status())
                .as("List fixed assets: " + listResponse.text())
                .isEqualTo(200);
        JsonNode listMatch = parse(listResponse);
        assertThat(findByField(listMatch.get("content"), "assetCode", assetCode))
                .as("Asset should appear in year=2026 list: " + listResponse.text())
                .isNotNull();

        // LIST — different year excludes
        JsonNode listMiss = parse(get("/api/fixed-assets?year=1999"));
        assertThat(findByField(listMiss.get("content"), "assetCode", assetCode)).isNull();

        // DETAIL
        JsonNode detail = parse(get("/api/fixed-assets/" + id));
        assertThat(detail.get("serialNumber").asText()).isEqualTo("SJL9L0MVNCJ");
        assertThat(detail.get("accumulatedDepreciationAccountCode").asText()).isEqualTo("1.2.02");

        // UPDATE — override name and useful life (no depreciation recorded yet)
        request.put("name", "Laptop Uji API (Updated)");
        request.put("usefulLifeMonths", 24);
        request.remove("fundingAccountCode");
        APIResponse updateResponse = put("/api/fixed-assets/" + id, request);
        assertThat(updateResponse.status())
                .as("Update fixed asset: " + updateResponse.text())
                .isEqualTo(200);
        JsonNode updated = parse(updateResponse);
        assertThat(updated.get("name").asText()).isEqualTo("Laptop Uji API (Updated)");
        assertThat(updated.get("usefulLifeMonths").asInt()).isEqualTo(24);

        // DELETE — no depreciation history, no linked purchase transaction
        APIResponse deleteResponse = delete("/api/fixed-assets/" + id);
        assertThat(deleteResponse.status()).isEqualTo(204);

        APIResponse afterDelete = get("/api/fixed-assets/" + id);
        assertThat(afterDelete.status()).isEqualTo(404);
    }

    @Test
    @DisplayName("Register asset linked to an existing purchase transaction — no new journal composed")
    void registerWithExistingPurchaseTransaction() throws Exception {
        // The issue #30 scenario: purchase journal already exists (DR 1.2.01 / CR 1.1.02)
        String assetAccountId = chartOfAccountRepository.findByAccountCode("1.2.01").orElseThrow().getId().toString();
        String bankAccountId = chartOfAccountRepository.findByAccountCode("1.1.02").orElseThrow().getId().toString();

        Map<String, Object> journalRequest = new HashMap<>();
        journalRequest.put("transactionDate", "2026-06-10");
        journalRequest.put("description", "Pembelian MacBook Air 13 M5");
        journalRequest.put("lines", List.of(
                journalLine(assetAccountId, 24000000, 0),
                journalLine(bankAccountId, 0, 24000000)));

        APIResponse journalResponse = post("/api/transactions/journal-entry", journalRequest);
        assertThat(journalResponse.status())
                .as("Create purchase journal: " + journalResponse.text())
                .isEqualTo(201);
        String transactionId = parse(journalResponse).get("transactionId").asText();

        long transactionCountBefore = transactionRepository.count();

        Map<String, Object> request = new HashMap<>();
        request.put("assetCode", "API-MBA-" + System.currentTimeMillis());
        request.put("name", "MacBook Air 13 M5 16GB/1TB");
        request.put("categoryCode", "KOMPUTER");
        request.put("purchaseDate", "2026-06-10");
        request.put("purchaseCost", 24000000);
        request.put("serialNumber", "SJL9L0MVNCJ");
        request.put("purchaseTransactionId", transactionId);

        APIResponse createResponse = post("/api/fixed-assets", request);
        assertThat(createResponse.status())
                .as("Register linked asset: " + createResponse.text())
                .isEqualTo(201);

        JsonNode created = parse(createResponse);
        assertThat(created.get("purchaseTransactionId").asText()).isEqualTo(transactionId);

        // Linking must not compose an additional acquisition journal
        assertThat(transactionRepository.count()).isEqualTo(transactionCountBefore);
    }

    @Test
    @DisplayName("Register by categoryId/fundingAccountId with explicit depreciation overrides")
    void registerWithIdsAndExplicitOverrides() throws Exception {
        JsonNode categories = parse(get("/api/fixed-assets/categories"));
        JsonNode komputer = findByField(categories, "code", "KOMPUTER");
        assertThat(komputer).isNotNull();
        String categoryId = komputer.get("id").asText();
        String fundingAccountId = chartOfAccountRepository.findByAccountCode("1.1.02").orElseThrow().getId().toString();

        String assetCode = "API-OVR-" + System.currentTimeMillis();
        Map<String, Object> request = new HashMap<>();
        request.put("assetCode", assetCode);
        request.put("name", "Aset Override Penyusutan");
        request.put("categoryId", categoryId);
        request.put("purchaseDate", "2026-03-15");
        request.put("purchaseCost", 12000000);
        request.put("fundingAccountId", fundingAccountId);
        // Explicit overrides win over the category defaults (KOMPUTER: 48 months)
        request.put("depreciationMethod", "STRAIGHT_LINE");
        request.put("usefulLifeMonths", 24);
        request.put("residualValue", 1200000);
        request.put("depreciationStartDate", "2026-04-01");
        request.put("autoPost", true);

        APIResponse createResponse = post("/api/fixed-assets", request);
        assertThat(createResponse.status())
                .as("Create with overrides: " + createResponse.text())
                .isEqualTo(201);

        JsonNode created = parse(createResponse);
        assertThat(created.get("categoryCode").asText()).isEqualTo("KOMPUTER");
        assertThat(created.get("usefulLifeMonths").asInt()).isEqualTo(24);
        assertThat(created.get("residualValue").asDouble()).isEqualTo(1200000.0);
        assertThat(created.get("depreciationStartDate").asText()).isEqualTo("2026-04-01");
        assertThat(created.get("autoPost").asBoolean()).isTrue();
        // (12,000,000 - 1,200,000) / 24 = 450,000
        assertThat(created.get("monthlyDepreciation").asDouble()).isEqualTo(450000.0);

        // LIST without year filter still returns the asset
        APIResponse listAll = get("/api/fixed-assets");
        assertThat(listAll.status()).isEqualTo(200);
        assertThat(findByField(parse(listAll).get("content"), "assetCode", assetCode)).isNotNull();

        // LIST filtered by categoryId
        APIResponse listByCategory = get("/api/fixed-assets?categoryId=" + categoryId);
        assertThat(listByCategory.status()).isEqualTo(200);
        assertThat(findByField(parse(listByCategory).get("content"), "assetCode", assetCode)).isNotNull();
    }

    @Test
    @DisplayName("POST with both categoryId and categoryCode returns 400")
    void createWithBothCategoryFields() throws Exception {
        JsonNode categories = parse(get("/api/fixed-assets/categories"));
        String categoryId = categories.get(0).get("id").asText();

        Map<String, Object> request = new HashMap<>();
        request.put("assetCode", "API-2CAT-" + System.currentTimeMillis());
        request.put("name", "Aset Kategori Ganda");
        request.put("categoryId", categoryId);
        request.put("categoryCode", "KOMPUTER");
        request.put("purchaseDate", "2026-06-10");
        request.put("purchaseCost", 1000000);
        request.put("fundingAccountCode", "1.1.02");

        APIResponse response = post("/api/fixed-assets", request);
        assertThat(response.status()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST with both fundingAccountId and fundingAccountCode returns 400")
    void createWithBothFundingFields() {
        String fundingAccountId = chartOfAccountRepository.findByAccountCode("1.1.02").orElseThrow().getId().toString();

        Map<String, Object> request = new HashMap<>();
        request.put("assetCode", "API-2FUND-" + System.currentTimeMillis());
        request.put("name", "Aset Pendanaan Ganda");
        request.put("categoryCode", "KOMPUTER");
        request.put("purchaseDate", "2026-06-10");
        request.put("purchaseCost", 1000000);
        request.put("fundingAccountId", fundingAccountId);
        request.put("fundingAccountCode", "1.1.02");

        APIResponse response = post("/api/fixed-assets", request);
        assertThat(response.status()).isEqualTo(400);
    }

    @Test
    @DisplayName("GET detail on non-existent ID returns 404")
    void detailNonExistent() {
        APIResponse response = get("/api/fixed-assets/00000000-0000-0000-0000-000000000000");
        assertThat(response.status()).isEqualTo(404);
    }

    @Test
    @DisplayName("DELETE on non-existent ID returns 404")
    void deleteNonExistent() {
        APIResponse response = delete("/api/fixed-assets/00000000-0000-0000-0000-000000000000");
        assertThat(response.status()).isEqualTo(404);
    }

    @Test
    @DisplayName("DELETE on asset linked to a purchase transaction returns 409")
    void deleteLinkedAssetReturns409() throws Exception {
        String assetAccountId = chartOfAccountRepository.findByAccountCode("1.2.01").orElseThrow().getId().toString();
        String bankAccountId = chartOfAccountRepository.findByAccountCode("1.1.02").orElseThrow().getId().toString();

        Map<String, Object> journalRequest = new HashMap<>();
        journalRequest.put("transactionDate", "2026-05-05");
        journalRequest.put("description", "Pembelian aset untuk uji hapus");
        journalRequest.put("lines", List.of(
                journalLine(assetAccountId, 3000000, 0),
                journalLine(bankAccountId, 0, 3000000)));
        String transactionId = parse(post("/api/transactions/journal-entry", journalRequest))
                .get("transactionId").asText();

        Map<String, Object> request = new HashMap<>();
        request.put("assetCode", "API-DEL409-" + System.currentTimeMillis());
        request.put("name", "Aset Tertaut Jurnal");
        request.put("categoryCode", "KOMPUTER");
        request.put("purchaseDate", "2026-05-05");
        request.put("purchaseCost", 3000000);
        request.put("purchaseTransactionId", transactionId);

        APIResponse createResponse = post("/api/fixed-assets", request);
        assertThat(createResponse.status()).isEqualTo(201);
        String id = parse(createResponse).get("id").asText();

        APIResponse deleteResponse = delete("/api/fixed-assets/" + id);
        assertThat(deleteResponse.status()).isEqualTo(409);
    }

    @Test
    @DisplayName("POST without acquisition source returns 400")
    void createWithoutAcquisitionSource() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("assetCode", "API-NOSRC-" + System.currentTimeMillis());
        request.put("name", "Aset Tanpa Sumber Dana");
        request.put("categoryCode", "KOMPUTER");
        request.put("purchaseDate", "2026-06-10");
        request.put("purchaseCost", 1000000);

        // Detail messages are sanitized by RestExceptionHandler; only the status is asserted
        APIResponse response = post("/api/fixed-assets", request);
        assertThat(response.status()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST with both funding account and purchase transaction returns 400")
    void createWithConflictingAcquisitionSources() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("assetCode", "API-BOTH-" + System.currentTimeMillis());
        request.put("name", "Aset Sumber Ganda");
        request.put("categoryCode", "KOMPUTER");
        request.put("purchaseDate", "2026-06-10");
        request.put("purchaseCost", 1000000);
        request.put("fundingAccountCode", "1.1.02");
        request.put("purchaseTransactionId", "00000000-0000-0000-0000-000000000000");

        APIResponse response = post("/api/fixed-assets", request);
        assertThat(response.status()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST without category returns 400")
    void createWithoutCategory() {
        Map<String, Object> request = new HashMap<>();
        request.put("assetCode", "API-NOCAT-" + System.currentTimeMillis());
        request.put("name", "Aset Tanpa Kategori");
        request.put("purchaseDate", "2026-06-10");
        request.put("purchaseCost", 1000000);
        request.put("fundingAccountCode", "1.1.02");

        APIResponse response = post("/api/fixed-assets", request);
        assertThat(response.status()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST with duplicate asset code returns 400")
    void createWithDuplicateAssetCode() throws Exception {
        String assetCode = "API-DUP-" + System.currentTimeMillis();

        Map<String, Object> request = new HashMap<>();
        request.put("assetCode", assetCode);
        request.put("name", "Aset Duplikat");
        request.put("categoryCode", "KOMPUTER");
        request.put("purchaseDate", "2026-06-10");
        request.put("purchaseCost", 1000000);
        request.put("fundingAccountCode", "1.1.02");

        assertThat(post("/api/fixed-assets", request).status()).isEqualTo(201);

        // Detail messages are sanitized by RestExceptionHandler; only the status is asserted
        APIResponse duplicate = post("/api/fixed-assets", request);
        assertThat(duplicate.status()).isEqualTo(400);
    }

    @Test
    @DisplayName("PUT on non-existent ID returns 404")
    void updateNonExistent() {
        Map<String, Object> request = new HashMap<>();
        request.put("assetCode", "API-404");
        request.put("name", "Tidak Ada");
        request.put("categoryCode", "KOMPUTER");
        request.put("purchaseDate", "2026-06-10");
        request.put("purchaseCost", 1000000);

        APIResponse response = put("/api/fixed-assets/00000000-0000-0000-0000-000000000000", request);
        assertThat(response.status()).isEqualTo(404);
    }

    @Test
    @DisplayName("Depreciation lifecycle via API: catch-up generate, list, post, skip, post-all (#31)")
    void depreciationLifecycle() throws Exception {
        // Late registration: purchased and depreciating since three months ago
        String assetCode = "API-DEP-" + System.currentTimeMillis();
        java.time.LocalDate purchaseDate = java.time.LocalDate.now().minusMonths(3).withDayOfMonth(10);
        Map<String, Object> request = new HashMap<>();
        request.put("assetCode", assetCode);
        request.put("name", "Aset Susut Terlambat");
        request.put("categoryCode", "KOMPUTER");
        request.put("purchaseDate", purchaseDate.toString());
        request.put("purchaseCost", 24000000);
        request.put("usefulLifeMonths", 48);
        request.put("fundingAccountCode", "1.1.02");
        assertThat(post("/api/fixed-assets", request).status()).isEqualTo(201);

        // Catch-up generation (no period param): all owed months up to previous month
        APIResponse generateResponse = post("/api/fixed-assets/depreciation/generate", Map.of());
        assertThat(generateResponse.status())
                .as("Catch-up generate: " + generateResponse.text())
                .isEqualTo(200);
        JsonNode pending = parse(generateResponse);
        long ourEntries = countByAssetCode(pending, assetCode);
        assertThat(ourEntries)
                .as("Three owed months must be generated: " + generateResponse.text())
                .isEqualTo(3);

        // List with period + status filter
        String firstPeriod = java.time.YearMonth.from(purchaseDate.withDayOfMonth(1)).toString();
        JsonNode firstMonthList = parse(get(
                "/api/fixed-assets/depreciation?period=" + firstPeriod + "&status=PENDING"));
        JsonNode firstEntry = findByField(firstMonthList, "assetCode", assetCode);
        assertThat(firstEntry).isNotNull();
        assertThat(firstEntry.get("periodNumber").asInt()).isEqualTo(1);
        assertThat(firstEntry.get("depreciationAmount").asDouble()).isEqualTo(500000.0);

        // Post the first entry
        String firstEntryId = firstEntry.get("id").asText();
        APIResponse postEntry = post("/api/fixed-assets/depreciation/" + firstEntryId + "/post", Map.of());
        assertThat(postEntry.status())
                .as("Post entry: " + postEntry.text())
                .isEqualTo(200);
        JsonNode posted = parse(postEntry);
        assertThat(posted.get("status").asText()).isEqualTo("POSTED");
        assertThat(posted.hasNonNull("transactionId")).isTrue();

        // Skip the second entry
        String secondPeriod = java.time.YearMonth.from(purchaseDate).plusMonths(1).toString();
        JsonNode secondEntry = findByField(parse(get(
                "/api/fixed-assets/depreciation?period=" + secondPeriod + "&status=PENDING")),
                "assetCode", assetCode);
        assertThat(secondEntry).isNotNull();
        APIResponse skip = post("/api/fixed-assets/depreciation/" + secondEntry.get("id").asText() + "/skip", Map.of());
        assertThat(skip.status()).isEqualTo(204);

        // Post-all for the third month posts the remaining pending entry
        String thirdPeriod = java.time.YearMonth.from(purchaseDate).plusMonths(2).toString();
        APIResponse postAll = post("/api/fixed-assets/depreciation/post-all?period=" + thirdPeriod, Map.of());
        assertThat(postAll.status())
                .as("Post-all: " + postAll.text())
                .isEqualTo(200);
        assertThat(parse(postAll).get("postedCount").asInt()).isGreaterThanOrEqualTo(1);

        // Final state: 1 POSTED + 1 SKIPPED + 1 POSTED for this asset
        JsonNode allEntries = parse(get("/api/fixed-assets/depreciation"));
        long postedCount = 0;
        long skippedCount = 0;
        for (JsonNode entry : allEntries) {
            if (entry.get("assetCode").asText().equals(assetCode)) {
                if (entry.get("status").asText().equals("POSTED")) postedCount++;
                if (entry.get("status").asText().equals("SKIPPED")) skippedCount++;
            }
        }
        assertThat(postedCount).isEqualTo(2);
        assertThat(skippedCount).isEqualTo(1);
    }

        // ==================== HELPER METHODS ====================

    private long countByAssetCode(JsonNode array, String assetCode) {
        long count = 0;
        for (JsonNode item : array) {
            if (item.hasNonNull("assetCode") && item.get("assetCode").asText().equals(assetCode)) {
                count++;
            }
        }
        return count;
    }

    private JsonNode findByField(JsonNode array, String field, String value) {
        if (array == null || !array.isArray()) {
            return null;
        }
        for (JsonNode item : array) {
            if (item.hasNonNull(field) && item.get(field).asText().equals(value)) {
                return item;
            }
        }
        return null;
    }

    private Map<String, Object> journalLine(String accountId, double debit, double credit) {
        Map<String, Object> line = new HashMap<>();
        line.put("accountId", accountId);
        line.put("debit", debit);
        line.put("credit", credit);
        return line;
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

    private JsonNode parse(APIResponse response) throws Exception {
        return objectMapper.readTree(response.text());
    }

    private String authenticateViaDeviceFlow() throws Exception {
        Map<String, String> codeRequest = new HashMap<>();
        codeRequest.put("clientId", "fixed-asset-api-test");

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

        page.locator("input[name='deviceName']").fill("Fixed Asset API Test Device");
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
