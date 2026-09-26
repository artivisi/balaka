package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.TaxTransactionDetail;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.TaxType;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.functional.taxdetail.TaxDetailTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.ProjectRepository;
import com.artivisi.accountingfinance.repository.TaxTransactionDetailRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.FilePayload;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Tax detail and document API. Every test works on transactions it posts itself, dated in
 * {@link #YEAR} (reserved for this class), so the seeded TAX-TRX-001..003 stay untouched.
 * Faktur and bupot numbers are random because both are unique table-wide.
 */
@DisplayName("Tax Detail & Document API - Functional Tests")
@Import(TaxDetailTestDataInitializer.class)
class TaxDetailApiTest extends PlaywrightTestBase {

    private static final int YEAR = 2004;

    @Autowired private TransactionRepository transactionRepository;
    @Autowired private JournalEntryRepository journalEntryRepository;
    @Autowired private JournalTemplateRepository templateRepository;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private TaxTransactionDetailRepository taxDetailRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private APIRequestContext apiContext;
    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        apiContext = playwright.request().newContext(new APIRequest.NewContextOptions().setBaseURL(baseUrl()));
        accessToken = authenticateViaDeviceFlow();
    }

    @AfterEach
    void tearDown() {
        if (apiContext != null) {
            apiContext.dispose();
        }
    }

    @Test
    @DisplayName("POST /api/transactions/{id}/tax-details creates a PPN Keluaran detail with every e-Faktur field")
    void testCreatePpnDetail() throws Exception {
        Transaction ppn = postedPpnTransaction();
        String faktur = faktur();

        APIResponse response = post("/api/transactions/" + ppn.getId() + "/tax-details", ppnRequest(faktur, "PT Klien Utama"));
        assertThat(response.status()).as(response.text()).isEqualTo(201);

        JsonNode body = parse(response);
        assertThat(body.get("transactionId").asText()).isEqualTo(ppn.getId().toString());
        assertThat(body.get("taxType").asText()).isEqualTo("PPN_KELUARAN");
        assertThat(body.get("fakturNumber").asText()).isEqualTo(faktur);
        assertThat(body.get("fakturDate").asText()).isEqualTo(YEAR + "-01-15");
        assertThat(body.get("transactionCode").asText()).isEqualTo("01");
        assertThat(body.get("dpp").decimalValue()).isEqualByComparingTo("10000000");
        assertThat(body.get("ppn").decimalValue()).isEqualByComparingTo("1100000");
        assertThat(body.get("counterpartyName").asText()).isEqualTo("PT Klien Utama");
        assertThat(body.get("counterpartyNpwp").asText()).isEqualTo("01.310.523.4-091.000");

        TaxTransactionDetail stored = taxDetailRepository.findById(UUID.fromString(body.get("id").asText()))
                .orElseThrow(() -> new AssertionError("Tax detail not persisted"));
        assertThat(stored.getTransaction().getId()).isEqualTo(ppn.getId());
        assertThat(stored.getFakturNumber()).isEqualTo(faktur);
        assertThat(stored.getDpp()).isEqualByComparingTo("10000000");
        assertThat(stored.getPpn()).isEqualByComparingTo("1100000");
    }

    @Test
    @DisplayName("GET /api/transactions/{id}/tax-details lists exactly the transaction's details, by tax type")
    void testListTaxDetails() throws Exception {
        Transaction ppn = postedPpnTransaction();
        String faktur = faktur();
        String bupot = bupot();
        assertThat(post("/api/transactions/" + ppn.getId() + "/tax-details", pph23Request(bupot, "PT Pemotong")).status())
                .isEqualTo(201);
        assertThat(post("/api/transactions/" + ppn.getId() + "/tax-details", ppnRequest(faktur, "PT Klien Utama")).status())
                .isEqualTo(201);

        APIResponse response = get("/api/transactions/" + ppn.getId() + "/tax-details");
        assertThat(response.status()).isEqualTo(200);

        JsonNode body = parse(response);
        assertThat(body).hasSize(2);
        assertThat(body.get(0).get("taxType").asText()).isEqualTo("PPH_23");
        assertThat(body.get(0).get("bupotNumber").asText()).isEqualTo(bupot);
        assertThat(body.get(0).get("taxAmount").decimalValue()).isEqualByComparingTo("100000");
        assertThat(body.get(1).get("taxType").asText()).isEqualTo("PPN_KELUARAN");
        assertThat(body.get(1).get("fakturNumber").asText()).isEqualTo(faktur);

        assertThat(parse(get("/api/transactions/" + postedNonTaxTransaction().getId() + "/tax-details"))).isEmpty();
    }

    @Test
    @DisplayName("GET /api/transactions/{id}/tax-details/{detailId} returns the detail; through another transaction it is rejected")
    void testGetTaxDetail() throws Exception {
        Transaction ppn = postedPpnTransaction();
        String faktur = faktur();
        String detailId = parse(post("/api/transactions/" + ppn.getId() + "/tax-details",
                ppnRequest(faktur, "PT Klien Utama"))).get("id").asText();

        APIResponse response = get("/api/transactions/" + ppn.getId() + "/tax-details/" + detailId);
        assertThat(response.status()).isEqualTo(200);
        JsonNode body = parse(response);
        assertThat(body.get("id").asText()).isEqualTo(detailId);
        assertThat(body.get("fakturNumber").asText()).isEqualTo(faktur);

        Transaction other = postedNonTaxTransaction();
        assertApiError(get("/api/transactions/" + other.getId() + "/tax-details/" + detailId), 400,
                "INVALID_REQUEST", "Tax detail does not belong to transaction " + other.getId());
        UUID missingId = UUID.randomUUID();
        assertApiError(get("/api/transactions/" + ppn.getId() + "/tax-details/" + missingId), 404,
                "NOT_FOUND", "Tax detail tidak ditemukan: " + missingId);
    }

    @Test
    @DisplayName("PUT /api/transactions/{id}/tax-details/{detailId} replaces the detail's fields")
    void testUpdateTaxDetail() throws Exception {
        Transaction ppn = postedPpnTransaction();
        String detailId = parse(post("/api/transactions/" + ppn.getId() + "/tax-details",
                ppnRequest(faktur(), "PT Klien Utama"))).get("id").asText();
        String newFaktur = faktur();
        Map<String, Object> update = ppnRequest(newFaktur, "PT Klien Baru");
        update.put("dpp", 12000000);
        update.put("ppn", 1320000);

        APIResponse response = put("/api/transactions/" + ppn.getId() + "/tax-details/" + detailId, update);
        assertThat(response.status()).as(response.text()).isEqualTo(200);
        assertThat(parse(response).get("counterpartyName").asText()).isEqualTo("PT Klien Baru");

        TaxTransactionDetail stored = taxDetailRepository.findById(UUID.fromString(detailId)).orElseThrow();
        assertThat(stored.getCounterpartyName()).isEqualTo("PT Klien Baru");
        assertThat(stored.getFakturNumber()).isEqualTo(newFaktur);
        assertThat(stored.getDpp()).isEqualByComparingTo("12000000");
        assertThat(stored.getPpn()).isEqualByComparingTo("1320000");
    }

    @Test
    @DisplayName("DELETE /api/transactions/{id}/tax-details/{detailId} removes the detail")
    void testDeleteTaxDetail() throws Exception {
        Transaction ppn = postedPpnTransaction();
        String detailId = parse(post("/api/transactions/" + ppn.getId() + "/tax-details",
                ppnRequest(faktur(), "PT Klien Utama"))).get("id").asText();

        APIResponse response = delete("/api/transactions/" + ppn.getId() + "/tax-details/" + detailId);
        assertThat(response.status()).isEqualTo(204);

        assertThat(parse(get("/api/transactions/" + ppn.getId() + "/tax-details"))).isEmpty();
        assertThat(taxDetailRepository.findById(UUID.fromString(detailId))).isEmpty();
    }

    @Test
    @DisplayName("PUT and DELETE through another transaction's URL are rejected and leave the detail unchanged")
    void testUpdateAndDeleteRequireOwningTransaction() throws Exception {
        Transaction ppn = postedPpnTransaction();
        Transaction other = postedNonTaxTransaction();
        String detailId = parse(post("/api/transactions/" + ppn.getId() + "/tax-details",
                ppnRequest(faktur(), "PT Klien Utama"))).get("id").asText();

        String notOwned = "Tax detail does not belong to transaction " + other.getId();
        assertApiError(put("/api/transactions/" + other.getId() + "/tax-details/" + detailId,
                ppnRequest(faktur(), "PT Penyusup")), 400, "INVALID_REQUEST", notOwned);
        assertApiError(delete("/api/transactions/" + other.getId() + "/tax-details/" + detailId), 400,
                "INVALID_REQUEST", notOwned);

        TaxTransactionDetail stored = taxDetailRepository.findById(UUID.fromString(detailId))
                .orElseThrow(() -> new AssertionError("Detail was deleted through another transaction"));
        assertThat(stored.getCounterpartyName()).isEqualTo("PT Klien Utama");
        assertThat(stored.getTransaction().getId()).isEqualTo(ppn.getId());
    }

    @Test
    @DisplayName("Invalid details are rejected with 400 and nothing is saved: missing name, missing DPP, duplicate faktur")
    void testRejectInvalidDetails() throws Exception {
        Transaction ppn = postedPpnTransaction();
        String faktur = faktur();
        assertThat(post("/api/transactions/" + ppn.getId() + "/tax-details", ppnRequest(faktur, "PT Klien Utama")).status())
                .isEqualTo(201);

        Map<String, Object> noName = ppnRequest(faktur(), "PT Klien Utama");
        noName.remove("counterpartyName");
        Map<String, Object> noDpp = ppnRequest(faktur(), "PT Klien Utama");
        noDpp.remove("dpp");
        Map<String, Object> duplicate = ppnRequest(faktur, "PT Klien Lain");

        String url = "/api/transactions/" + ppn.getId() + "/tax-details";
        assertApiError(post(url, noName), 400, "VALIDATION_ERROR", "Nama lawan transaksi wajib diisi");
        assertApiError(post(url, noDpp), 400, "INVALID_REQUEST", "DPP wajib diisi untuk e-Faktur");
        assertApiError(post(url, duplicate), 400, "INVALID_REQUEST", "Nomor faktur sudah digunakan: " + faktur);
        assertThat(taxDetailRepository.findAllByTransactionIdOrderByTaxTypeAsc(ppn.getId())).hasSize(1);
        assertThat(post("/api/transactions/" + UUID.randomUUID() + "/tax-details", ppnRequest(faktur(), "PT Klien Utama"))
                .status()).isEqualTo(404);
    }

    @Test
    @DisplayName("POST /api/tax-details/bulk saves valid items and reports the failing one with its reason")
    void testBulkCreate() throws Exception {
        Transaction ppn = postedPpnTransaction();
        Transaction pph23 = postedPph23Transaction();
        String faktur = faktur();
        String bupot = bupot();
        UUID missing = UUID.randomUUID();

        Map<String, Object> request = Map.of("items", List.of(
                Map.of("transactionId", ppn.getId().toString(), "detail", ppnRequest(faktur, "PT Bulk Client A")),
                Map.of("transactionId", pph23.getId().toString(), "detail", pph23Request(bupot, "PT Bulk Client B")),
                Map.of("transactionId", missing.toString(), "detail", ppnRequest(faktur(), "PT Tidak Ada"))));

        APIResponse response = post("/api/tax-details/bulk", request);
        assertThat(response.status()).as(response.text()).isEqualTo(200);

        JsonNode body = parse(response);
        assertThat(body.get("successCount").asInt()).isEqualTo(2);
        assertThat(body.get("failureCount").asInt()).isEqualTo(1);
        JsonNode results = body.get("results");
        assertThat(results).hasSize(3);
        assertThat(results.get(0).get("success").asBoolean()).isTrue();
        assertThat(results.get(1).get("success").asBoolean()).isTrue();
        assertThat(results.get(2).get("success").asBoolean()).isFalse();
        assertThat(results.get(2).get("transactionId").asText()).isEqualTo(missing.toString());
        assertThat(results.get(2).get("errorMessage").asText()).contains(missing.toString());

        assertThat(taxDetailRepository.findAllByTransactionIdOrderByTaxTypeAsc(ppn.getId()))
                .singleElement().satisfies(d -> assertThat(d.getFakturNumber()).isEqualTo(faktur));
        assertThat(taxDetailRepository.findAllByTransactionIdOrderByTaxTypeAsc(pph23.getId()))
                .singleElement().satisfies(d -> {
                    assertThat(d.getBupotNumber()).isEqualTo(bupot);
                    assertThat(d.getTaxAmount()).isEqualByComparingTo("100000");
                });
    }

    @Test
    @DisplayName("Uploading a document stores it on the transaction; list and download return the same file")
    void testUploadListAndDownloadDocument() throws Exception {
        Transaction pph23 = postedPph23Transaction();
        byte[] png = minimalPng();
        String filename = "bukti-potong-" + UUID.randomUUID().toString().substring(0, 8) + ".png";

        APIResponse upload = apiContext.post("/api/transactions/" + pph23.getId() + "/documents",
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + accessToken)
                        .setMultipart(FormData.create().set("file", new FilePayload(filename, "image/png", png))));
        assertThat(upload.status()).as(upload.text()).isEqualTo(201);
        JsonNode uploaded = parse(upload);
        String documentId = uploaded.get("id").asText();
        assertThat(uploaded.get("originalFilename").asText()).isEqualTo(filename);
        assertThat(uploaded.get("contentType").asText()).isEqualTo("image/png");
        assertThat(uploaded.get("fileSize").asLong()).isEqualTo(png.length);
        assertThat(uploaded.get("checksumSha256").asText())
                .isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(png)));

        JsonNode list = parse(get("/api/transactions/" + pph23.getId() + "/documents"));
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("id").asText()).isEqualTo(documentId);
        assertThat(list.get(0).get("originalFilename").asText()).isEqualTo(filename);

        APIResponse download = get("/api/documents/" + documentId);
        assertThat(download.status()).isEqualTo(200);
        assertThat(download.headers().get("content-disposition")).contains("attachment").contains(filename);
        assertThat(download.body()).isEqualTo(png);
    }

    @Test
    @DisplayName("Tax detail and document endpoints reject unauthenticated requests with 401")
    void testUnauthenticated() {
        Transaction ppn = postedPpnTransaction();

        assertThat(apiContext.get("/api/transactions/" + ppn.getId() + "/tax-details").status()).isEqualTo(401);
        assertThat(apiContext.get("/api/transactions/" + ppn.getId() + "/documents").status()).isEqualTo(401);
        assertThat(apiContext.post("/api/transactions/" + ppn.getId() + "/tax-details", RequestOptions.create()
                .setHeader("Content-Type", "application/json")
                .setData(ppnRequest(faktur(), "PT Klien Utama"))).status()).isEqualTo(401);
        assertThat(taxDetailRepository.findAllByTransactionIdOrderByTaxTypeAsc(ppn.getId())).isEmpty();
    }

    // --- Fixtures ---

    private Map<String, Object> ppnRequest(String faktur, String counterparty) {
        Map<String, Object> request = new HashMap<>();
        request.put("taxType", "PPN_KELUARAN");
        request.put("fakturNumber", faktur);
        request.put("fakturDate", YEAR + "-01-15");
        request.put("transactionCode", "01");
        request.put("dpp", 10000000);
        request.put("ppn", 1100000);
        request.put("counterpartyName", counterparty);
        request.put("counterpartyIdType", "TIN");
        request.put("counterpartyNpwp", "01.310.523.4-091.000");
        return request;
    }

    private Map<String, Object> pph23Request(String bupot, String counterparty) {
        Map<String, Object> request = new HashMap<>();
        request.put("taxType", "PPH_23");
        request.put("bupotNumber", bupot);
        request.put("taxObjectCode", "24-104-01");
        request.put("grossAmount", 5000000);
        request.put("taxRate", 2);
        request.put("taxAmount", 100000);
        request.put("counterpartyName", counterparty);
        return request;
    }

    private Transaction postedPpnTransaction() {
        return posted("Pendapatan Jasa + PPN", "PRJ-2024-001", "10000000",
                line("1.1.02", "11100000", "0"), line("4.1.01", "0", "10000000"), line("2.1.03", "0", "1100000"));
    }

    private Transaction postedPph23Transaction() {
        return posted("Pembayaran Jasa dengan PPh 23", "PRJ-2024-003", "5000000",
                line("5.1.01", "5000000", "0"), line("1.1.02", "0", "4900000"), line("2.1.21", "0", "100000"));
    }

    private Transaction postedNonTaxTransaction() {
        return posted("Pendapatan Jasa Training", null, "15000000",
                line("1.1.02", "15000000", "0"), line("4.1.01", "0", "15000000"));
    }

    private record Line(String accountCode, BigDecimal debit, BigDecimal credit) {}

    private static Line line(String accountCode, String debit, String credit) {
        return new Line(accountCode, new BigDecimal(debit), new BigDecimal(credit));
    }

    private Transaction posted(String templateName, String projectCode, String amount, Line... lines) {
        String number = "TDA-" + UUID.randomUUID().toString().substring(0, 8);
        Transaction tx = new Transaction();
        tx.setTransactionNumber(number);
        tx.setTransactionDate(LocalDate.of(YEAR, 1, 15));
        tx.setDescription(templateName + " " + number);
        tx.setAmount(new BigDecimal(amount));
        tx.setJournalTemplate(templateRepository.findByTemplateNameAndIsCurrentVersionTrue(templateName)
                .orElseThrow(() -> new IllegalStateException("Seeded template missing: " + templateName)));
        if (projectCode != null) {
            tx.setProject(projectRepository.findByCode(projectCode)
                    .orElseThrow(() -> new IllegalStateException("Seeded project missing: " + projectCode)));
        }
        tx.setStatus(TransactionStatus.POSTED);
        tx.setPostedAt(LocalDateTime.now());
        tx.setPostedBy("admin");
        tx.setCreatedBy("admin");
        Transaction saved = transactionRepository.save(tx);

        int index = 0;
        for (Line l : lines) {
            JournalEntry entry = new JournalEntry();
            entry.setJournalNumber(number + "-" + String.format("%02d", ++index));
            entry.setTransaction(saved);
            entry.setAccount(accountRepository.findByAccountCode(l.accountCode())
                    .orElseThrow(() -> new IllegalStateException("Seeded account missing: " + l.accountCode())));
            entry.setDebitAmount(l.debit());
            entry.setCreditAmount(l.credit());
            entry.setPostedAt(saved.getPostedAt());
            entry.setCreatedBy("admin");
            journalEntryRepository.save(entry);
        }
        return saved;
    }

    private static String faktur() {
        return "010." + digits(3) + "-04." + digits(8);
    }

    private static String bupot() {
        return "BP-" + YEAR + "-" + digits(8);
    }

    private static String digits(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(ThreadLocalRandom.current().nextInt(10));
        }
        return sb.toString();
    }

    private static byte[] minimalPng() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB),
                "png", out);
        return out.toByteArray();
    }

    // --- HTTP ---

    private APIResponse get(String path) {
        return apiContext.get(path, RequestOptions.create().setHeader("Authorization", "Bearer " + accessToken));
    }

    private APIResponse post(String path, Object data) {
        return apiContext.post(path, RequestOptions.create()
                .setHeader("Authorization", "Bearer " + accessToken)
                .setHeader("Content-Type", "application/json")
                .setData(data));
    }

    private APIResponse put(String path, Object data) {
        return apiContext.put(path, RequestOptions.create()
                .setHeader("Authorization", "Bearer " + accessToken)
                .setHeader("Content-Type", "application/json")
                .setData(data));
    }

    private APIResponse delete(String path) {
        return apiContext.delete(path, RequestOptions.create().setHeader("Authorization", "Bearer " + accessToken));
    }

    private void assertApiError(APIResponse response, int status, String code, String message) throws Exception {
        assertThat(response.status()).as(response.text()).isEqualTo(status);
        JsonNode body = parse(response);
        assertThat(body.get("error").asText()).as(response.text()).isEqualTo(code);
        assertThat(body.get("message").asText()).as(response.text()).isEqualTo(message);
    }

    private JsonNode parse(APIResponse response) throws Exception {
        return objectMapper.readTree(response.text());
    }

    private String authenticateViaDeviceFlow() throws Exception {
        APIResponse codeResponse = apiContext.post("/api/device/code", RequestOptions.create()
                .setHeader("Content-Type", "application/json")
                .setData(Map.of("clientId", "tax-detail-api-test")));
        assertThat(codeResponse.ok()).isTrue();

        JsonNode codeData = objectMapper.readTree(codeResponse.text());
        String deviceCode = codeData.get("deviceCode").asText();
        String userCode = codeData.get("userCode").asText();

        loginAsAdmin();
        navigateTo("/device?code=" + userCode);
        waitForPageLoad();
        page.locator("input[name='deviceName']").fill("Tax Detail API Test Device");
        page.locator("button[type='submit']:has-text('Otorisasi Perangkat')").click();
        waitForPageLoad();

        AtomicReference<String> tokenRef = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofSeconds(2)).until(() -> {
            APIResponse tokenResponse = apiContext.post("/api/device/token", RequestOptions.create()
                    .setHeader("Content-Type", "application/json")
                    .setData(Map.of("deviceCode", deviceCode)));
            if (tokenResponse.ok()) {
                tokenRef.set(objectMapper.readTree(tokenResponse.text()).get("accessToken").asText());
                return true;
            }
            return false;
        });
        return tokenRef.get();
    }
}
