package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.service.FixedAssetService;
import com.artivisi.accountingfinance.service.TransactionService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.RequestOptions;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Identity fields, disposal, verification and movement through the API on the IT service pack. Reserved test year 2185. */
@DisplayName("Fixed asset register API")
@Import(ServiceTestDataInitializer.class)
class AssetRegisterApiTest extends PlaywrightTestBase {

    private static final String PATH = "/api/fixed-assets";

    @Autowired private TransactionRepository transactionRepository;
    @Autowired private TransactionService transactionService;
    @Autowired private ChartOfAccountRepository accountRepository;

    private APIRequestContext api;
    private String token;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        api = playwright.request().newContext(new APIRequest.NewContextOptions().setBaseURL(baseUrl()));
        token = deviceToken();
    }

    @AfterEach
    void tearDown() {
        api.dispose();
    }

    @Test
    @DisplayName("Sale: preview, DRAFT keeps the asset ACTIVE, posting by the transaction API disposes it with the exact gain, void restores it")
    void saleLifecycle() throws Exception {
        String id = register("2185-01-10", "12000000", Map.of());
        postAcquisition(id);

        JsonNode preview = read(get(PATH + "/" + id + "/disposal/preview?type=SOLD&proceeds=13000000"), 200);
        assertThat(preview.get("gainLoss").decimalValue()).isEqualByComparingTo("1000000");
        assertThat(preview.get("taxableIncomeEffect").decimalValue()).isEqualByComparingTo("1000000");
        assertThat(preview.get("estimatedPpn").decimalValue()).isEqualByComparingTo("1430000");
        assertThat(get(PATH + "/" + id + "/disposal/preview?type=SOLD").status()).isEqualTo(400);
        assertThat(get(PATH + "/" + id + "/disposal/preview?type=GIFTED&proceeds=5").status()).isEqualTo(400);

        Map<String, Object> sale = new HashMap<>(Map.of("type", "SOLD", "date", "2185-01-31", "proceeds", 13000000,
                "proceedsAccountId", accountRepository.findByAccountCode("1.1.02").orElseThrow().getId().toString(),
                "counterparty", "PT Pembeli"));
        assertThat(post(PATH + "/" + id + "/disposal", sale).status()).as("reason required").isEqualTo(400);
        sale.put("reason", "Diganti unit baru");

        JsonNode disposal = read(post(PATH + "/" + id + "/disposal", sale), 201);
        String disposalId = disposal.get("id").asText();
        String transactionId = disposal.get("transactionId").asText();
        assertThat(disposal.get("status").asText()).isEqualTo("DRAFT");
        assertThat(disposal.get("transactionStatus").asText()).isEqualTo("DRAFT");
        assertThat(disposal.get("recordNumber").asText()).matches("DSP-2185-\\d{4}");
        assertThat(disposal.get("bookValue").decimalValue()).isEqualByComparingTo("12000000");
        assertThat(disposal.get("gainLoss").decimalValue()).isEqualByComparingTo("1000000");
        assertThat(disposal.get("proceedsAccountCode").asText()).isEqualTo("1.1.02");
        assertThat(disposal.get("taxEffect").get("note").asText()).contains("belum diverifikasi");

        assertThat(read(get(PATH + "/" + id), 200).get("status").asText()).isEqualTo("ACTIVE");
        assertThat(post(PATH + "/" + id + "/disposal", sale).status()).as("second draft").isEqualTo(409);
        assertThat(api.delete(PATH + "/" + id, auth()).status()).as("delete").isEqualTo(409);
        assertThat(api.put(PATH + "/" + id, json(request("2185-01-10", "12000000", Map.of(), "same"))).status()).as("edit").isEqualTo(409);

        assertThat(post("/api/transactions/" + transactionId + "/post", Map.of()).status()).isEqualTo(200);

        JsonNode asset = read(get(PATH + "/" + id), 200);
        assertThat(asset.get("status").asText()).isEqualTo("DISPOSED");
        assertThat(asset.get("disposalType").asText()).isEqualTo("SOLD");
        assertThat(asset.get("disposalDate").asText()).isEqualTo("2185-01-31");
        assertThat(asset.get("gainLossOnDisposal").decimalValue()).isEqualByComparingTo("1000000");
        assertThat(read(get(PATH + "/disposals/" + disposalId), 200).get("status").asText()).isEqualTo("POSTED");
        assertThat(post(PATH + "/disposals/" + disposalId + "/cancel", Map.of()).status()).isEqualTo(409);

        JsonNode posted = read(get(PATH + "/disposals?year=2185&status=POSTED"), 200);
        assertThat(ids(posted)).contains(disposalId);
        assertThat(ids(read(get(PATH + "/disposals?year=2185&status=DRAFT"), 200))).doesNotContain(disposalId);

        JsonNode rollForward = read(get(PATH + "/movement?year=2185"), 200);
        assertThat(rollForward.get("total").get("disposals").decimalValue()).isGreaterThanOrEqualTo(new java.math.BigDecimal("12000000"));

        transactionService.voidTransaction(UUID.fromString(transactionId), com.artivisi.accountingfinance.enums.VoidReason.INPUT_ERROR, "Salah", "admin");
        assertThat(read(get(PATH + "/" + id), 200).get("status").asText()).isEqualTo("ACTIVE");
        assertThat(read(get(PATH + "/disposals/" + disposalId), 200).get("status").asText()).isEqualTo("VOIDED");
    }

    @Test
    @DisplayName("Gift: proceeds refused, cancel removes the draft and unlocks the asset; unposted acquisition blocks disposal")
    void giftAndCancel() throws Exception {
        String id = register("2185-02-10", "6000000", Map.of());
        Map<String, Object> gift = Map.of("type", "GIFTED", "date", "2185-02-28", "counterparty", "Yayasan", "reason", "Hibah");
        assertThat(post(PATH + "/" + id + "/disposal", gift).status()).as("draft acquisition").isEqualTo(409);

        postAcquisition(id);
        Map<String, Object> withProceeds = new HashMap<>(gift);
        withProceeds.put("proceeds", 100);
        assertThat(post(PATH + "/" + id + "/disposal", withProceeds).status()).isEqualTo(400);

        JsonNode disposal = read(post(PATH + "/" + id + "/disposal", gift), 201);
        assertThat(disposal.get("proceeds").decimalValue()).isEqualByComparingTo("0");
        assertThat(disposal.get("gainLoss").decimalValue()).isEqualByComparingTo("-6000000");
        assertThat(disposal.get("taxEffect").get("fiscalCorrection").decimalValue()).isEqualByComparingTo("6000000");
        assertThat(disposal.get("taxEffect").get("taxableIncomeEffect").decimalValue()).isEqualByComparingTo("0");

        UUID draft = UUID.fromString(disposal.get("transactionId").asText());
        assertThat(api.delete("/api/transactions/" + draft, auth()).status()).as("generic delete").isEqualTo(409);

        JsonNode cancelled = read(post(PATH + "/disposals/" + disposal.get("id").asText() + "/cancel", Map.of()), 200);
        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.get("transactionId").isNull()).isTrue();
        assertThat(transactionRepository.findById(draft)).isEmpty();
        assertThat(api.put(PATH + "/" + id, json(request("2185-02-10", "6000000", Map.of(), "same"))).status()).isEqualTo(200);
    }

    @Test
    @DisplayName("Identity fields round-trip; verification writes the register, appears in the checklist and clears exceptions; movement is listed")
    void identityAndVerification() throws Exception {
        String id = register("2185-03-10", "3000000", Map.of("brand", "Lenovo", "model", "T14", "serialNumber", "SN-API",
                "custodian", "Budi", "location", "Lantai 1", "condition", "GOOD"));

        JsonNode detail = read(get(PATH + "/" + id), 200);
        assertThat(detail.get("brand").asText()).isEqualTo("Lenovo");
        assertThat(detail.get("model").asText()).isEqualTo("T14");
        assertThat(detail.get("serialNumber").asText()).isEqualTo("SN-API");
        assertThat(detail.get("custodian").asText()).isEqualTo("Budi");
        assertThat(detail.get("condition").asText()).isEqualTo("GOOD");
        assertThat(detail.get("hasPhoto").asBoolean()).isFalse();
        assertThat(detail.get("lastVerifiedDate").isNull()).isTrue();

        JsonNode before = row(read(get(PATH + "/verification/exceptions?year=2185"), 200), id);
        assertThat(before.get("exceptions").toString()).isEqualTo("[\"NOT_VERIFIED\",\"MISSING_PHOTO\"]");

        Map<String, Object> incomplete = Map.of("year", 2185, "date", "2185-12-01", "result", "FOUND");
        assertThat(post(PATH + "/" + id + "/verification", incomplete).status()).isEqualTo(400);
        assertThat(post(PATH + "/" + id + "/verification", Map.of("year", 2185, "date", "2186-01-01", "result", "NOT_FOUND")).status()).isEqualTo(400);

        JsonNode verified = read(post(PATH + "/" + id + "/verification", Map.of("year", 2185, "date", "2185-12-01",
                "result", "FOUND", "condition", "MINOR_DAMAGE", "location", "Lantai 2", "custodian", "Siti")), 200);
        assertThat(verified.get("verifiedBy").asText()).isEqualTo("admin");

        JsonNode after = read(get(PATH + "/" + id), 200);
        assertThat(after.get("custodian").asText()).isEqualTo("Siti");
        assertThat(after.get("location").asText()).isEqualTo("Lantai 2");
        assertThat(after.get("condition").asText()).isEqualTo("MINOR_DAMAGE");
        assertThat(after.get("lastVerifiedDate").asText()).isEqualTo("2185-12-01");

        JsonNode checklistRow = row(read(get(PATH + "/verification?year=2185"), 200), id);
        assertThat(checklistRow.get("verification").get("result").asText()).isEqualTo("FOUND");
        assertThat(checklistRow.get("exceptions").toString()).isEqualTo("[\"MISSING_PHOTO\"]");

        JsonNode movements = read(get(PATH + "/register-movements?year=2185"), 200);
        JsonNode movement = row(movements, id);
        assertThat(movement.get("source").asText()).isEqualTo("VERIFICATION");
        assertThat(movement.get("fromCustodian").asText()).isEqualTo("Budi");
        assertThat(movement.get("toCustodian").asText()).isEqualTo("Siti");
        assertThat(movement.get("fromLocation").asText()).isEqualTo("Lantai 1");
        assertThat(movement.get("toCondition").asText()).isEqualTo("MINOR_DAMAGE");
    }

    @Test
    @DisplayName("Delete: an unposted registration goes with its draft; a posted acquisition answers 409")
    void deleteRules() throws Exception {
        String mistake = register("2185-04-10", "1000000", Map.of());
        Transaction draft = acquisition(mistake);
        assertThat(api.delete(PATH + "/" + mistake, auth()).status()).isEqualTo(204);
        assertThat(transactionRepository.findById(draft.getId())).isEmpty();

        String posted = register("2185-04-11", "1000000", Map.of());
        postAcquisition(posted);
        APIResponse refused = api.delete(PATH + "/" + posted, auth());
        assertThat(refused.status()).isEqualTo(409);
        assertThat(refused.text()).contains("Lepas Aset");
        assertThat(get(PATH + "/" + posted).status()).isEqualTo(200);
    }

    // ==================== helpers ====================

    private String register(String purchaseDate, String cost, Map<String, Object> identity) throws Exception {
        return read(post(PATH, request(purchaseDate, cost, identity, "FA-" + UUID.randomUUID().toString().substring(0, 8))), 201).get("id").asText();
    }

    private Map<String, Object> request(String purchaseDate, String cost, Map<String, Object> identity, String code) {
        Map<String, Object> body = new HashMap<>(identity);
        body.put("assetCode", code);
        body.put("name", "Aset API " + code);
        body.put("categoryCode", "KOMPUTER");
        body.put("purchaseDate", purchaseDate);
        body.put("purchaseCost", new java.math.BigDecimal(cost));
        body.put("usefulLifeMonths", 24);
        body.put("depreciationStartDate", purchaseDate.substring(0, 8) + "01");
        body.put("fundingAccountCode", "1.1.02");
        return body;
    }

    private Transaction acquisition(String assetId) {
        return transactionRepository.findBySourceDocumentTypeAndSourceDocumentIdOrderByTransactionDateAsc(
                FixedAssetService.ACQUISITION_SOURCE_TYPE, UUID.fromString(assetId)).getFirst();
    }

    private void postAcquisition(String assetId) {
        transactionService.post(acquisition(assetId).getId(), "admin");
    }

    private JsonNode row(JsonNode rows, String assetId) {
        for (JsonNode node : rows) {
            if (assetId.equals(node.get("assetId").asText())) {
                return node;
            }
        }
        throw new AssertionError("No row for asset " + assetId + " in " + rows);
    }

    private List<String> ids(JsonNode rows) {
        List<String> ids = new java.util.ArrayList<>();
        rows.forEach(n -> ids.add(n.get("id").asText()));
        return ids;
    }

    private JsonNode read(APIResponse response, int status) throws Exception {
        assertThat(response.status()).as(response.text()).isEqualTo(status);
        return mapper.readTree(response.text());
    }

    private RequestOptions auth() {
        return RequestOptions.create().setHeader("Authorization", "Bearer " + token);
    }

    private RequestOptions json(Object data) {
        return auth().setHeader("Content-Type", "application/json").setData(data);
    }

    private APIResponse get(String path) {
        return api.get(path, auth());
    }

    private APIResponse post(String path, Object data) {
        return api.post(path, json(data));
    }

    private String deviceToken() throws Exception {
        APIResponse code = api.post("/api/device/code", RequestOptions.create().setHeader("Content-Type", "application/json")
                .setData(Map.of("clientId", "asset-register-api-test")));
        JsonNode codeData = mapper.readTree(code.text());
        loginAsAdmin();
        navigateTo("/device?code=" + codeData.get("userCode").asText());
        waitForPageLoad();
        page.locator("input[name='deviceName']").fill("Asset Register API Test");
        page.locator("button[type='submit']:has-text('Otorisasi Perangkat')").click();
        waitForPageLoad();

        AtomicReference<String> result = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofSeconds(2)).until(() -> {
            APIResponse response = api.post("/api/device/token", RequestOptions.create().setHeader("Content-Type", "application/json")
                    .setData(Map.of("deviceCode", codeData.get("deviceCode").asText())));
            if (!response.ok()) {
                return false;
            }
            result.set(mapper.readTree(response.text()).get("accessToken").asText());
            return true;
        });
        return result.get();
    }
}
