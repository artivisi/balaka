package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.TaxDocumentRepository;
import com.artivisi.accountingfinance.repository.TaxFilingRepository;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Functional tests for the tax filing register API (issue #35). Covers the CRUD
 * lifecycle, period-shape validation, the document chain (STP -> teguran), and
 * the filing status surfaced on /api/analysis/tax-summary.
 */
@Slf4j
@DisplayName("Tax Filing API - Functional Tests")
@Import(ServiceTestDataInitializer.class)
class TaxFilingApiTest extends PlaywrightTestBase {

    /** Test year kept clear of the seed packs so period keys never collide. */
    private static final int TEST_YEAR = 2039;

    private APIRequestContext apiContext;
    private ObjectMapper objectMapper;
    private String accessToken;

    @Autowired
    private TaxFilingRepository taxFilingRepository;

    @Autowired
    private TaxDocumentRepository taxDocumentRepository;

    @Autowired
    private ChartOfAccountRepository chartOfAccountRepository;

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
        // Filings registered by this class hold an FK to their payment transaction,
        // which would block later tests that wipe transactions
        List<com.artivisi.accountingfinance.entity.TaxFiling> filings =
                taxFilingRepository.findByFilters(null, TEST_YEAR);
        filings.forEach(f -> taxDocumentRepository.deleteAll(taxDocumentRepository.findByFilingId(f.getId())));
        taxFilingRepository.deleteAll(filings);
    }

    @Test
    @DisplayName("CRUD lifecycle: register a masa PPN, list, detail, update to FILED, delete")
    void crudLifecycle() throws Exception {
        Map<String, Object> request = filingRequest("PPN", TEST_YEAR + "-04", "NOT_FILED");

        APIResponse createResponse = post("/api/tax-filings", request);
        assertThat(createResponse.status())
                .as("Register tax filing: " + createResponse.text())
                .isEqualTo(201);

        JsonNode created = parse(createResponse).get("filing");
        String id = created.get("id").asText();
        assertThat(created.get("taxType").asText()).isEqualTo("PPN");
        assertThat(created.get("period").asText()).isEqualTo(TEST_YEAR + "-04");
        assertThat(created.get("pembetulanNo").asInt()).isZero();
        assertThat(created.get("status").asText()).isEqualTo("NOT_FILED");
        assertThat(created.get("nihil").asBoolean()).isFalse();

        // LIST — year filter matches, other year does not
        JsonNode listMatch = parse(get("/api/tax-filings?year=" + TEST_YEAR));
        assertThat(findByField(listMatch, "id", id))
                .as("Filing should appear in year=" + TEST_YEAR + " list")
                .isNotNull();
        assertThat(findByField(parse(get("/api/tax-filings?year=1999")), "id", id)).isNull();

        // LIST — taxType filter
        assertThat(findByField(parse(get("/api/tax-filings?taxType=PPN&year=" + TEST_YEAR)), "id", id)).isNotNull();
        assertThat(findByField(parse(get("/api/tax-filings?taxType=PPH21&year=" + TEST_YEAR)), "id", id)).isNull();

        // DETAIL — no documents yet
        JsonNode detail = parse(get("/api/tax-filings/" + id));
        assertThat(detail.get("filing").get("id").asText()).isEqualTo(id);
        assertThat(detail.get("documents").isArray()).isTrue();
        assertThat(detail.get("documents").size()).isZero();

        // UPDATE — record the BPE and the kurang bayar that was billed and paid
        Map<String, Object> update = filingRequest("PPN", TEST_YEAR + "-04", "FILED");
        update.put("filedDate", TEST_YEAR + "-05-28");
        update.put("bpeNumber", "BPE-" + TEST_YEAR + "-0401");
        update.put("bpeDate", TEST_YEAR + "-05-28");
        update.put("kurangBayar", 1250000);
        update.put("billingCode", "820250428001234");
        update.put("ntpn", "NTPN" + TEST_YEAR + "0428");
        update.put("paidDate", TEST_YEAR + "-05-27");

        APIResponse updateResponse = put("/api/tax-filings/" + id, update);
        assertThat(updateResponse.status())
                .as("Update tax filing: " + updateResponse.text())
                .isEqualTo(200);

        JsonNode updated = parse(updateResponse).get("filing");
        assertThat(updated.get("status").asText()).isEqualTo("FILED");
        assertThat(updated.get("bpeNumber").asText()).isEqualTo("BPE-" + TEST_YEAR + "-0401");
        assertThat(updated.get("kurangBayar").asDouble()).isEqualTo(1250000.0);
        assertThat(updated.get("ntpn").asText()).isEqualTo("NTPN" + TEST_YEAR + "0428");
        assertThat(updated.get("paidDate").asText()).isEqualTo(TEST_YEAR + "-05-27");

        // DELETE — allowed while no documents are attached
        assertThat(delete("/api/tax-filings/" + id).status()).isEqualTo(204);
        assertThat(get("/api/tax-filings/" + id).status()).isEqualTo(404);
    }

    @Test
    @DisplayName("Annual PPh Badan filing uses a YYYY period")
    void annualFiling() throws Exception {
        Map<String, Object> request = filingRequest("PPH_BADAN", String.valueOf(TEST_YEAR), "FILED");
        request.put("filedDate", (TEST_YEAR + 1) + "-04-28");
        request.put("bpeNumber", "BPE-BADAN-" + TEST_YEAR);

        APIResponse response = post("/api/tax-filings", request);
        assertThat(response.status())
                .as("Register annual filing: " + response.text())
                .isEqualTo(201);

        JsonNode filing = parse(response).get("filing");
        assertThat(filing.get("period").asText()).isEqualTo(String.valueOf(TEST_YEAR));
        assertThat(filing.get("taxType").asText()).isEqualTo("PPH_BADAN");
    }

    @Test
    @DisplayName("Masa tax type with a YYYY period returns 400")
    void masaTypeRejectsAnnualPeriod() throws Exception {
        APIResponse response = post("/api/tax-filings",
                filingRequest("PPN", String.valueOf(TEST_YEAR), "FILED"));
        assertThat(response.status()).isEqualTo(400);
    }

    @Test
    @DisplayName("PPh Badan with a YYYY-MM period returns 400")
    void annualTypeRejectsMasaPeriod() throws Exception {
        APIResponse response = post("/api/tax-filings",
                filingRequest("PPH_BADAN", TEST_YEAR + "-04", "FILED"));
        assertThat(response.status()).isEqualTo(400);
    }

    @Test
    @DisplayName("Malformed period returns 400")
    void malformedPeriodRejected() throws Exception {
        APIResponse response = post("/api/tax-filings",
                filingRequest("PPN", TEST_YEAR + "-13", "FILED"));
        assertThat(response.status()).isEqualTo(400);
    }

    @Test
    @DisplayName("Nihil filing with a kurang bayar returns 400")
    void nihilWithKurangBayarRejected() throws Exception {
        Map<String, Object> request = filingRequest("PPN", TEST_YEAR + "-07", "FILED");
        request.put("nihil", true);
        request.put("kurangBayar", 500000);

        APIResponse response = post("/api/tax-filings", request);
        assertThat(response.status()).isEqualTo(400);
    }

    @Test
    @DisplayName("Filing with both kurang bayar and lebih bayar returns 400")
    void kurangAndLebihBayarRejected() throws Exception {
        Map<String, Object> request = filingRequest("PPN", TEST_YEAR + "-08", "FILED");
        request.put("kurangBayar", 500000);
        request.put("lebihBayar", 250000);

        APIResponse response = post("/api/tax-filings", request);
        assertThat(response.status()).isEqualTo(400);
    }

    @Test
    @DisplayName("Duplicate (taxType, period, pembetulanNo) returns 409; a pembetulan is accepted")
    void duplicatePeriodKeyRejected() throws Exception {
        Map<String, Object> normal = filingRequest("PPH21", TEST_YEAR + "-02", "FILED");
        assertThat(post("/api/tax-filings", normal).status()).isEqualTo(201);

        APIResponse duplicate = post("/api/tax-filings", normal);
        assertThat(duplicate.status())
                .as("Same period key must be refused: " + duplicate.text())
                .isEqualTo(409);

        // pembetulan ke-1 for the same masa is a different filing
        Map<String, Object> pembetulan = filingRequest("PPH21", TEST_YEAR + "-02", "CORRECTED");
        pembetulan.put("pembetulanNo", 1);
        APIResponse response = post("/api/tax-filings", pembetulan);
        assertThat(response.status())
                .as("Pembetulan should be accepted: " + response.text())
                .isEqualTo(201);
        assertThat(parse(response).get("filing").get("pembetulanNo").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("PUT onto a period key another filing already holds returns 409")
    void updateOntoTakenPeriodKeyRejected() throws Exception {
        String firstId = parse(post("/api/tax-filings",
                filingRequest("PPN", TEST_YEAR + "-01", "FILED"))).get("filing").get("id").asText();
        String secondId = parse(post("/api/tax-filings",
                filingRequest("PPN", TEST_YEAR + "-02", "FILED"))).get("filing").get("id").asText();
        assertThat(firstId).isNotEqualTo(secondId);

        // Move the second filing onto the first one's masa
        APIResponse response = put("/api/tax-filings/" + secondId,
                filingRequest("PPN", TEST_YEAR + "-01", "FILED"));
        assertThat(response.status())
                .as("Moving a filing onto a taken period key must be refused: " + response.text())
                .isEqualTo(409);

        // Updating a filing in place, keeping its own key, still works
        Map<String, Object> sameKey = filingRequest("PPN", TEST_YEAR + "-02", "LATE");
        sameKey.put("bpeNumber", "BPE-SAMEKEY");
        APIResponse ok = put("/api/tax-filings/" + secondId, sameKey);
        assertThat(ok.status())
                .as("A filing keeping its own period key must still update: " + ok.text())
                .isEqualTo(200);
        assertThat(parse(ok).get("filing").get("status").asText()).isEqualTo("LATE");
    }

    @Test
    @DisplayName("Nihil filing with a lebih bayar returns 400")
    void nihilWithLebihBayarRejected() throws Exception {
        Map<String, Object> request = filingRequest("PPN", TEST_YEAR + "-10", "FILED");
        request.put("nihil", true);
        request.put("lebihBayar", 300000);

        assertThat(post("/api/tax-filings", request).status()).isEqualTo(400);
    }

    @Test
    @DisplayName("Filing links the setoran transaction that paid the kurang bayar")
    void linksPaymentTransaction() throws Exception {
        String taxPayableId = chartOfAccountRepository.findByAccountCode("2.1.03").orElseThrow().getId().toString();
        String bankId = chartOfAccountRepository.findByAccountCode("1.1.02").orElseThrow().getId().toString();

        Map<String, Object> journalRequest = new HashMap<>();
        journalRequest.put("transactionDate", TEST_YEAR + "-05-27");
        journalRequest.put("description", "Setoran PPN masa " + TEST_YEAR + "-04");
        journalRequest.put("lines", List.of(
                journalLine(taxPayableId, 1250000, 0),
                journalLine(bankId, 0, 1250000)));
        String transactionId = parse(post("/api/transactions/journal-entry", journalRequest))
                .get("transactionId").asText();

        Map<String, Object> request = filingRequest("PPN", TEST_YEAR + "-05", "FILED");
        request.put("kurangBayar", 1250000);
        request.put("paidDate", TEST_YEAR + "-05-27");
        request.put("paymentTransactionId", transactionId);

        APIResponse response = post("/api/tax-filings", request);
        assertThat(response.status())
                .as("Register filing with payment: " + response.text())
                .isEqualTo(201);

        JsonNode filing = parse(response).get("filing");
        assertThat(filing.get("paymentTransactionId").asText()).isEqualTo(transactionId);
        assertThat(filing.get("paymentTransactionNumber").asText()).isNotBlank();
    }

    @Test
    @DisplayName("Unknown payment transaction returns 404")
    void unknownPaymentTransactionRejected() throws Exception {
        Map<String, Object> request = filingRequest("PPN", TEST_YEAR + "-09", "FILED");
        request.put("paymentTransactionId", "00000000-0000-0000-0000-000000000000");

        assertThat(post("/api/tax-filings", request).status()).isEqualTo(404);
    }

    @Test
    @DisplayName("Document chain: SPT, then STP, then a teguran that references the STP")
    void documentChain() throws Exception {
        Map<String, Object> request = filingRequest("PPN", TEST_YEAR + "-06", "LATE");
        String filingId = parse(post("/api/tax-filings", request)).get("filing").get("id").asText();

        Path sptFile = Files.createTempFile("spt-", ".pdf");
        Files.write(sptFile, minimalPdf());
        APIResponse sptUpload = uploadDocument(filingId, sptFile, FormData.create()
                .set("docType", "SPT")
                .set("docNumber", "SPT-PPN-" + TEST_YEAR + "-06")
                .set("docDate", TEST_YEAR + "-07-31"));
        assertThat(sptUpload.status())
                .as("Attach SPT: " + sptUpload.text())
                .isEqualTo(201);

        JsonNode spt = parse(sptUpload);
        assertThat(spt.get("docType").asText()).isEqualTo("SPT");
        assertThat(spt.get("file").get("contentType").asText()).isEqualTo("application/pdf");
        assertThat(spt.get("file").get("checksumSha256").asText()).isNotBlank();

        Path stpFile = Files.createTempFile("stp-", ".pdf");
        Files.write(stpFile, minimalPdf());
        APIResponse stpUpload = uploadDocument(filingId, stpFile, FormData.create()
                .set("docType", "STP")
                .set("docNumber", "STP-07643")
                .set("docDate", TEST_YEAR + "-12-01")
                .set("dueDate", TEST_YEAR + "-12-31")
                .set("amount", "100000"));
        assertThat(stpUpload.status()).isEqualTo(201);
        assertThat(parse(stpUpload).get("amount").asDouble()).isEqualTo(100000.0);

        Path teguranFile = Files.createTempFile("teguran-", ".pdf");
        Files.write(teguranFile, minimalPdf());
        APIResponse teguranUpload = uploadDocument(filingId, teguranFile, FormData.create()
                .set("docType", "TEGURAN")
                .set("docNumber", "TEG-00112")
                .set("referenceNumber", "STP-07643"));
        assertThat(teguranUpload.status()).isEqualTo(201);

        JsonNode teguran = parse(teguranUpload);
        String teguranId = teguran.get("id").asText();
        assertThat(teguran.get("referenceNumber").asText())
                .as("Teguran must point back at the STP it chases")
                .isEqualTo("STP-07643");

        // LIST — all three visible on the filing
        JsonNode documents = parse(get("/api/tax-filings/" + filingId + "/documents"));
        assertThat(documents.size()).isEqualTo(3);
        assertThat(findByField(documents, "docNumber", "SPT-PPN-" + TEST_YEAR + "-06")).isNotNull();
        assertThat(findByField(documents, "docNumber", "STP-07643")).isNotNull();
        assertThat(findByField(documents, "docNumber", "TEG-00112")).isNotNull();

        // DETAIL — documents ride along with the filing
        assertThat(parse(get("/api/tax-filings/" + filingId)).get("documents").size()).isEqualTo(3);

        // DOWNLOAD — byte-identical to what was uploaded
        APIResponse download = get("/api/tax-filings/" + filingId + "/documents/" + teguranId + "/download");
        assertThat(download.status()).isEqualTo(200);
        assertThat(download.body()).isEqualTo(minimalPdf());

        // DELETE the filing while documents are attached — refused
        APIResponse deleteFiling = delete("/api/tax-filings/" + filingId);
        assertThat(deleteFiling.status())
                .as("A filing with attached documents must not be deletable: " + deleteFiling.text())
                .isEqualTo(409);

        // The generic document endpoint must not orphan the tax metadata
        String documentId = teguran.get("file").get("id").asText();
        assertThat(delete("/api/documents/" + documentId).status()).isEqualTo(409);

        // DELETE through the filing removes both the metadata and the file
        assertThat(delete("/api/tax-filings/" + filingId + "/documents/" + teguranId).status()).isEqualTo(204);
        assertThat(parse(get("/api/tax-filings/" + filingId + "/documents")).size()).isEqualTo(2);
        assertThat(get("/api/tax-filings/" + filingId + "/documents/" + teguranId + "/download").status())
                .isEqualTo(404);
    }

    @Test
    @DisplayName("Upload to an unknown filing returns 404")
    void uploadToUnknownFilingRejected() throws Exception {
        Path file = Files.createTempFile("orphan-", ".pdf");
        Files.write(file, minimalPdf());

        APIResponse response = uploadDocument("00000000-0000-0000-0000-000000000000", file,
                FormData.create().set("docType", "SPT"));
        assertThat(response.status()).isEqualTo(404);
    }

    @Test
    @DisplayName("GET and DELETE on a non-existent filing return 404")
    void unknownFilingReturns404() {
        assertThat(get("/api/tax-filings/00000000-0000-0000-0000-000000000000").status()).isEqualTo(404);
        assertThat(delete("/api/tax-filings/00000000-0000-0000-0000-000000000000").status()).isEqualTo(404);
    }

    @Test
    @DisplayName("tax-summary reports registered filings for the requested range")
    void taxSummaryIncludesFilingStatus() throws Exception {
        Map<String, Object> filed = filingRequest("PPN", TEST_YEAR + "-03", "FILED");
        filed.put("filedDate", TEST_YEAR + "-04-28");
        filed.put("bpeNumber", "BPE-" + TEST_YEAR + "-0301");
        filed.put("kurangBayar", 750000);
        assertThat(post("/api/tax-filings", filed).status()).isEqualTo(201);

        Map<String, Object> outside = filingRequest("PPN", TEST_YEAR + "-11", "NOT_FILED");
        assertThat(post("/api/tax-filings", outside).status()).isEqualTo(201);

        // The annual return spans Jan-Dec, so it overlaps any range inside its year even
        // though the range itself covers only part of it
        Map<String, Object> annual = filingRequest("PPH_BADAN", String.valueOf(TEST_YEAR), "FILED");
        annual.put("filedDate", (TEST_YEAR + 1) + "-04-28");
        assertThat(post("/api/tax-filings", annual).status()).isEqualTo(201);

        APIResponse response = get("/api/analysis/tax-summary?startDate=" + TEST_YEAR
                + "-01-01&endDate=" + TEST_YEAR + "-06-30");
        assertThat(response.status())
                .as("Tax summary: " + response.text())
                .isEqualTo(200);

        JsonNode filings = parse(response).get("data").get("filings");
        assertThat(filings.isArray()).isTrue();

        JsonNode march = findByField(filings, "period", TEST_YEAR + "-03");
        assertThat(march).as("March filing should be reported: " + response.text()).isNotNull();
        assertThat(march.get("taxType").asText()).isEqualTo("PPN");
        assertThat(march.get("status").asText()).isEqualTo("FILED");
        assertThat(march.get("bpeNumber").asText()).isEqualTo("BPE-" + TEST_YEAR + "-0301");
        assertThat(march.get("kurangBayar").asDouble()).isEqualTo(750000.0);

        assertThat(findByField(filings, "period", TEST_YEAR + "-11"))
                .as("November falls outside the requested range")
                .isNull();

        JsonNode badan = findByField(filings, "period", String.valueOf(TEST_YEAR));
        assertThat(badan)
                .as("Annual PPh Badan return spans the whole year, so a Jan-Jun range "
                        + "overlaps it: " + response.text())
                .isNotNull();
        assertThat(badan.get("taxType").asText()).isEqualTo("PPH_BADAN");
        assertThat(badan.get("status").asText()).isEqualTo("FILED");
    }

    @Test
    @DisplayName("tax-summary excludes an annual filing whose year does not overlap the range")
    void taxSummaryExcludesAnnualFilingOutsideRange() throws Exception {
        Map<String, Object> annual = filingRequest("PPH_BADAN", String.valueOf(TEST_YEAR), "FILED");
        assertThat(post("/api/tax-filings", annual).status()).isEqualTo(201);

        APIResponse response = get("/api/analysis/tax-summary?startDate=" + (TEST_YEAR - 1)
                + "-01-01&endDate=" + (TEST_YEAR - 1) + "-12-31");
        assertThat(response.status()).isEqualTo(200);

        assertThat(findByField(parse(response).get("data").get("filings"), "period", String.valueOf(TEST_YEAR)))
                .as("A filing for " + TEST_YEAR + " must not appear in the " + (TEST_YEAR - 1) + " summary")
                .isNull();
    }

    // ==================== HELPERS ====================

    private Map<String, Object> filingRequest(String taxType, String period, String status) {
        Map<String, Object> request = new HashMap<>();
        request.put("taxType", taxType);
        request.put("period", period);
        request.put("status", status);
        return request;
    }

    private Map<String, Object> journalLine(String accountId, double debit, double credit) {
        Map<String, Object> line = new HashMap<>();
        line.put("accountId", accountId);
        line.put("debit", debit);
        line.put("credit", credit);
        return line;
    }

    private byte[] minimalPdf() {
        return ("%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n")
                .getBytes(StandardCharsets.UTF_8);
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

    private APIResponse uploadDocument(String filingId, Path file, FormData metadata) {
        return apiContext.post("/api/tax-filings/" + filingId + "/documents",
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + accessToken)
                        .setMultipart(metadata.set("file", file)));
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
        codeRequest.put("clientId", "tax-filing-api-test");

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

        page.locator("input[name='deviceName']").fill("Tax Filing API Test Device");
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
