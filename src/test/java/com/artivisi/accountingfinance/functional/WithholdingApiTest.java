package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.RequestOptions;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Withholding API on the IT service seed pack: seed accounts 5.1.22, 1.1.02, Hutang PPh 21 2.1.20,
 * template and deadline from the pack. Reserved test years 2142 and 2008 (the journal-entry API rejects future dates).
 */
@DisplayName("Withholding API")
@Import(ServiceTestDataInitializer.class)
class WithholdingApiTest extends PlaywrightTestBase {

    private static final int YEAR = 2142;

    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private JournalEntryRepository journalEntryRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private APIRequestContext api;
    private String token;

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
    @DisplayName("Lifecycle: payee, draft payment, post, masa status, deposit, BP21 export; NIK is masked")
    void lifecycle() throws Exception {
        String code = "API-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> payee = new HashMap<>(Map.of("code", code, "name", "Freelancer " + code, "payeeType", "ORANG_PRIBADI",
                "defaultWithholding", "PPH_21_BUKAN_PEGAWAI", "defaultTaxObjectCode", "21-100-20", "nik", "3174012345678901"));
        JsonNode created = json(api.post("/api/withholding/payees", withBody(payee)), 201);
        String payeeId = created.get("id").asText();
        assertThat(created.get("nikMasked").asText()).isEqualTo("****8901");
        assertThat(created.has("nik")).isFalse();
        assertThat(created.get("hasTaxId").asBoolean()).isTrue();
        assertThat(api.get("/api/withholding/payees", auth()).text()).contains(code).doesNotContain("3174012345678901");

        JsonNode payment = json(api.post("/api/withholding/payments", withBody(paymentBody(payeeId, 3, 100000000))), 201);
        assertThat(payment.get("transactionStatus").asText()).isEqualTo("DRAFT");
        assertAmount(payment, "grossAmount", "100000000");
        assertAmount(payment, "dppAmount", "50000000");
        assertAmount(payment, "taxAmount", "2500000");
        assertAmount(payment, "netAmount", "97500000");
        String paymentId = payment.get("id").asText();

        JsonNode second = json(api.post("/api/withholding/payments", withBody(paymentBody(payeeId, 3, 100000000))), 201);
        assertAmount(second, "taxAmount", "2500000");  // taxed on its own gross, not on top of the first
        assertThat(api.delete("/api/withholding/payments/" + second.get("id").asText(), auth()).status()).isEqualTo(204);

        JsonNode beforePost = status(3);
        assertAmount(beforePost, "withheld", "0");
        assertAmount(beforePost, "draftWithheld", "2500000");

        JsonNode posted = json(api.post("/api/withholding/payments/" + paymentId + "/post", auth()), 200);
        assertThat(posted.get("transactionStatus").asText()).isEqualTo("POSTED");
        assertThat(api.delete("/api/withholding/payments/" + paymentId, auth()).status()).isEqualTo(409);

        assertThat(journalEntryRepository.findByTransactionIdWithAccount(UUID.fromString(posted.get("transactionId").asText())).stream()
                .filter(e -> e.getAccount().getAccountCode().equals("2.1.20")).toList())
                .singleElement().satisfies(e -> assertThat(e.getCreditAmount()).isEqualByComparingTo("2500000"));

        JsonNode afterPost = status(3);
        assertAmount(afterPost, "withheld", "2500000");
        assertThat(afterPost.get("dueDate").asText()).isEqualTo(YEAR + "-04-15");
        assertThat(afterPost.get("status").asText()).isEqualTo("OPEN");
        assertThat(json(api.get("/api/withholding/masa-status?year=" + YEAR + "&month=3&asOf=" + YEAR + "-04-16", auth()), 200)
                .get(0).get("status").asText()).isEqualTo("OVERDUE");

        Map<String, Object> deposit = Map.of("treatment", "PPH_21_BUKAN_PEGAWAI", "year", YEAR, "month", 3, "amount", 2500000,
                "depositDate", YEAR + "-04-10", "bankAccountId", accountId("1.1.02"), "ntpn", "NTPN-1");
        JsonNode draftDeposit = json(api.post("/api/withholding/deposits", withBody(deposit)), 201);
        assertThat(draftDeposit.get("transactionStatus").asText()).isEqualTo("DRAFT");
        assertAmount(status(3), "deposited", "0");
        assertThat(json(api.post("/api/withholding/deposits/" + draftDeposit.get("id").asText() + "/post", auth()), 200)
                .get("transactionStatus").asText()).isEqualTo("POSTED");
        JsonNode settled = status(3);
        assertAmount(settled, "deposited", "2500000");
        assertAmount(settled, "outstanding", "0");
        assertThat(settled.get("status").asText()).isEqualTo("ON_TIME");

        APIResponse export = api.get("/api/tax-export/bp21?startMonth=" + YEAR + "-03&endMonth=" + YEAR + "-03", auth());
        assertThat(export.status()).isEqualTo(200);
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(export.body()))) {
            Sheet sheet = workbook.getSheet("DATA");
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            assertThat(sheet.getRow(1).getCell(6).getStringCellValue()).isEqualTo("Freelancer " + code);
            assertThat(sheet.getRow(1).getCell(12).getNumericCellValue()).isEqualTo(2500000d);
        }
    }

    @Test
    @DisplayName("Validation and not-found map to 400/404; over-deposit is 400")
    void errors() throws Exception {
        assertThat(api.post("/api/withholding/payees", withBody(Map.of("code", "X"))).status()).isEqualTo(400);
        assertThat(api.post("/api/withholding/payments", withBody(paymentBody(UUID.randomUUID().toString(), 5, 1000))).status()).isEqualTo(404);
        assertThat(api.post("/api/withholding/payments/" + UUID.randomUUID() + "/post", auth()).status()).isEqualTo(404);
        APIResponse overDeposit = api.post("/api/withholding/deposits", withBody(Map.of("treatment", "PPH_23", "year", YEAR, "month", 11,
                "amount", 1000, "depositDate", YEAR + "-12-10", "bankAccountId", accountId("1.1.02"))));
        assertThat(overDeposit.status()).as(overDeposit.text()).isEqualTo(400);
    }

    @Test
    @DisplayName("Report over the API: service debit without PPh detail appears, with account and amount")
    void undocumentedReport() throws Exception {
        Map<String, Object> journal = Map.of("transactionDate", "2008-01-15", "description", "Jasa tanpa detail",
                "lines", java.util.List.of(
                        Map.of("accountId", accountId("5.1.22"), "debit", 1500000, "credit", 0),
                        Map.of("accountId", accountId("1.1.02"), "debit", 0, "credit", 1500000)));
        JsonNode draft = json(api.post("/api/transactions/journal-entry", withBody(journal)), 201);
        String id = draft.get("transactionId").asText();
        JsonNode report = json(api.get("/api/withholding/undocumented-service-payments?year=2008&month=1", auth()), 200);
        assertThat(report.findValuesAsText("transactionId")).doesNotContain(id);

        assertThat(api.post("/api/transactions/" + id + "/post", auth()).status()).isEqualTo(200);
        JsonNode after = json(api.get("/api/withholding/undocumented-service-payments?year=2008&month=1", auth()), 200);
        JsonNode row = null;
        for (JsonNode candidate : after.get("rows")) {
            if (candidate.get("transactionId").asText().equals(id)) {
                row = candidate;
            }
        }
        assertThat(row).isNotNull();
        assertThat(row.get("accountCode").asText()).isEqualTo("5.1.22");
        assertAmount(row, "amount", "1500000");
    }

    private Map<String, Object> paymentBody(String vendorId, int month, long gross) {
        return Map.of("vendorId", vendorId, "paymentDate", YEAR + String.format("-%02d-10", month), "grossAmount", gross,
                "expenseAccountId", accountId("5.1.22"), "bankAccountId", accountId("1.1.02"), "description", "Honor");
    }

    private JsonNode status(int month) throws Exception {
        return json(api.get("/api/withholding/masa-status?year=" + YEAR + "&month=" + month, auth()), 200).get(0);
    }

    private String accountId(String code) {
        return accountRepository.findByAccountCode(code)
                .orElseThrow(() -> new IllegalStateException("Seed account missing: " + code)).getId().toString();
    }

    private void assertAmount(JsonNode node, String field, String expected) {
        assertThat(new BigDecimal(node.get(field).asText())).as(field).isEqualByComparingTo(expected);
    }

    private RequestOptions auth() {
        return RequestOptions.create().setHeader("Authorization", "Bearer " + token);
    }

    private RequestOptions withBody(Object body) {
        return auth().setHeader("Content-Type", "application/json").setData(body);
    }

    private JsonNode json(APIResponse response, int status) throws Exception {
        assertThat(response.status()).as(response.text()).isEqualTo(status);
        return objectMapper.readTree(response.text());
    }

    private String deviceToken() throws Exception {
        APIResponse codeResponse = api.post("/api/device/code", RequestOptions.create()
                .setHeader("Content-Type", "application/json").setData(Map.of("clientId", "withholding-api-test")));
        JsonNode code = json(codeResponse, 200);

        loginAsAdmin();
        navigateTo("/device?code=" + code.get("userCode").asText());
        waitForPageLoad();
        page.locator("input[name='deviceName']").fill("Withholding API Test");
        page.locator("button[type='submit']:has-text('Otorisasi Perangkat')").click();
        waitForPageLoad();

        AtomicReference<String> ref = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofSeconds(2)).until(() -> {
            APIResponse tokenResponse = api.post("/api/device/token", RequestOptions.create()
                    .setHeader("Content-Type", "application/json").setData(Map.of("deviceCode", code.get("deviceCode").asText())));
            if (tokenResponse.ok()) {
                ref.set(objectMapper.readTree(tokenResponse.text()).get("accessToken").asText());
                return true;
            }
            return false;
        });
        return ref.get();
    }
}
