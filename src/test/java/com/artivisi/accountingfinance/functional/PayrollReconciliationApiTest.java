package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.Employee;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.EmployeeRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Bonus/THR, warnings in API responses, reconciliation and 1721-A1 over HTTP on the IT service seed pack:
 * salary account 5.1.01, bonus 5.8.01, THR 5.8.02. EMP-002 is TK/0, category A.
 * Reserved test years 2013 and 2011: the transaction APIs reject future dates, and years above 2031 would push
 * PayrollControllerFunctionalTest runs off the first page of the shared payroll list.
 */
@DisplayName("Payroll reconciliation API")
@Import(ServiceTestDataInitializer.class)
class PayrollReconciliationApiTest extends PlaywrightTestBase {

    private static final int FULL_YEAR = 2013;
    private static final String WARNING_PERIOD = "2011-01";

    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private JournalTemplateRepository templateRepository;

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
    @DisplayName("Year with THR and bonus through the API: exact PPh 21, 1721-A1 totals, journal split and matched reconciliation")
    void fullYear() throws Exception {
        Employee dewi = employeeRepository.findByEmployeeId("EMP-002").orElseThrow();
        for (int month = 1; month <= 12; month++) {
            String runId = createRun(FULL_YEAR + "-" + String.format("%02d", month));
            if (month == 3) {
                json(api.post("/api/payroll/" + runId + "/additions", withBody(addition(dewi.getId(), "THR", "10000000"))), 201);
            }
            json(api.post("/api/payroll/" + runId + "/calculate", withBody(Map.of("baseSalary", 10000000, "jkkRiskClass", 1))), 200);
            if (month == 12) {
                // Added after calculation: the run recalculates at once
                json(api.post("/api/payroll/" + runId + "/additions", withBody(addition(dewi.getId(), "BONUS", "5000000"))), 201);
            }
            JsonNode detail = json(api.get("/api/payroll/" + runId, auth()), 200);
            JsonNode row = detailOf(detail, dewi.getId());
            if (month == 3) {
                // 20,000,000 at 9%
                assertAmount(row, "thr", "10000000");
                assertAmount(row, "grossSalary", "20000000");
                assertAmount(row, "pph21", "1800000");
                assertThat(detail.get("additions")).hasSize(1);
                assertThat(detail.get("additions").get(0).get("type").asText()).isEqualTo("THR");
            } else if (month == 12) {
                // Annual 135,000,000 - 6,000,000 - 54,000,000 = 75,000,000 PKP: tax 5,250,000.
                // Jan-Nov: ten months at 200,000 + 1,800,000 = 3,800,000. December 1,450,000.
                assertAmount(row, "bonus", "5000000");
                assertAmount(row, "grossSalary", "15000000");
                assertAmount(row, "pph21", "1450000");
            } else {
                assertAmount(row, "grossSalary", "10000000");
                assertAmount(row, "pph21", "200000");
            }
            json(api.post("/api/payroll/" + runId + "/approve", auth()), 200);
            JsonNode posted = json(api.post("/api/payroll/" + runId + "/post", auth()), 200);
            assertThat(posted.get("warnings")).isEmpty();
        }

        JsonNode a1 = json(api.get("/api/payroll/employees/" + dewi.getId() + "/1721-a1?year=" + FULL_YEAR, auth()), 200);
        JsonNode calc = a1.get("calculation");
        assertAmount(calc, "gajiDanTunjangan", "120000000");
        assertAmount(calc, "bonusDanThr", "15000000");
        assertAmount(calc, "natura", "0");
        assertAmount(calc, "penghasilanBruto", "135000000");
        assertAmount(calc, "biayaJabatan", "6000000");
        assertAmount(calc, "ptkp", "54000000");
        assertAmount(calc, "pkp", "75000000");
        assertAmount(calc, "pph21Terutang", "5250000");
        assertAmount(calc, "pph21Dipotong", "5250000");
        assertAmount(calc, "pph21KurangBayar", "0");
        assertThat(a1.get("monthlyBreakdown")).hasSize(12);
        assertAmount(a1.get("monthlyBreakdown").get(2), "thr", "10000000");
        assertAmount(a1.get("monthlyBreakdown").get(11), "bonus", "5000000");

        JsonNode year = json(api.get("/api/payroll/reconciliation?year=" + FULL_YEAR, auth()), 200);
        assertThat(year.get("periods")).hasSize(12);
        for (JsonNode period : year.get("periods")) {
            assertThat(period.get("status").asText()).as(period.get("period").asText()).isEqualTo("MATCHED");
            assertAmount(period, "difference", "0");
        }
        JsonNode march = year.get("periods").get(2);
        assertAmount(march, "runThr", "10000000");
        assertAmount(march, "ledgerSalaryExpense", march.get("runCashGross").asText());
        assertAmount(year, "difference", "0");
        assertAmount(year, "ledgerSalaryExpense", year.get("postedCashGross").asText());

        JsonNode journal = json(api.get("/api/analysis/transactions/" + runTransaction(FULL_YEAR + "-03"), auth()), 200);
        assertThat(journal.toString()).contains("5.8.02").doesNotContain("5.8.01");
    }

    @Test
    @DisplayName("Warnings reach the caller on journal-entry, direct post, post, bulk-post and the payroll responses")
    void warningsBothWays() throws Exception {
        String runId = createRun(WARNING_PERIOD);
        json(api.post("/api/payroll/" + runId + "/calculate", withBody(Map.of("baseSalary", 10000000, "jkkRiskClass", 1))), 200);
        JsonNode approved = json(api.post("/api/payroll/" + runId + "/approve", auth()), 200);
        assertThat(approved.get("warnings")).isEmpty();

        JsonNode other = json(api.post("/api/transactions/journal-entry", withBody(journal("Beban lain", "5.8.03"))), 201);
        assertThat(other.get("warnings")).isEmpty();

        JsonNode draft = json(api.post("/api/transactions/journal-entry", withBody(journal("Gaji manual", "5.1.01"))), 201);
        assertThat(draft.get("status").asText()).isEqualTo("DRAFT");
        assertThat(draft.get("warnings")).hasSize(1);
        assertThat(draft.get("warnings").get(0).asText()).contains("5.1.01").contains(WARNING_PERIOD).contains("Approved");

        JsonNode postedDraft = json(api.post("/api/transactions/" + draft.get("transactionId").asText() + "/post", auth()), 200);
        assertThat(postedDraft.get("warnings")).hasSize(1);

        JsonNode bulkDraft = json(api.post("/api/transactions/journal-entry", withBody(journal("THR manual", "5.8.02"))), 201);
        JsonNode bulk = json(api.post("/api/transactions/bulk-post",
                withBody(Map.of("transactionIds", List.of(bulkDraft.get("transactionId").asText())))), 200);
        assertThat(bulk.get("results").get(0).get("warnings").get(0).asText()).contains("5.8.02");

        Map<String, Object> direct = new HashMap<>();
        direct.put("templateId", templateRepository.findByTemplateNameAndIsCurrentVersionTrue("Pembayaran dengan Pemotongan PPh").orElseThrow().getId());
        direct.put("merchant", "Gaji lewat template");
        direct.put("amount", 1000000);
        direct.put("transactionDate", WARNING_PERIOD + "-20");
        direct.put("description", "Gaji lewat template");
        direct.put("source", "test");
        direct.put("userApproved", true);
        direct.put("accountSlots", Map.of("BEBAN", accountId("5.1.01"), "BANK", accountId("1.1.02"), "HUTANG_PPH", accountId("2.1.20")));
        direct.put("variables", Map.of("settledAmount", 1000000, "netAmount", 900000, "taxAmount", 100000));
        JsonNode created = json(api.post("/api/transactions", withBody(direct)), 201);
        assertThat(created.get("status").asText()).isEqualTo("POSTED");
        assertThat(created.get("warnings").get(0).asText()).contains("5.1.01");

        JsonNode run = json(api.get("/api/payroll/" + runId, auth()), 200);
        assertThat(run.get("warnings")).hasSize(3);
        assertThat(run.get("warnings").toString()).contains("Gaji manual").contains("THR manual").contains("Gaji lewat template");

        JsonNode beforePost = json(api.get("/api/payroll/reconciliation/" + WARNING_PERIOD, auth()), 200);
        assertThat(beforePost.get("status").asText()).isEqualTo("RUN_NOT_POSTED");
        assertAmount(beforePost, "ledgerOutsideRun", "3000000");
        assertThat(beforePost.get("journalsOutsideRun")).hasSize(3);

        JsonNode postedRun = json(api.post("/api/payroll/" + runId + "/post", auth()), 200);
        assertThat(postedRun.get("status").asText()).isEqualTo("POSTED");
        assertThat(postedRun.get("warnings")).hasSize(3);

        JsonNode afterPost = json(api.get("/api/payroll/reconciliation/" + WARNING_PERIOD, auth()), 200);
        assertThat(afterPost.get("status").asText()).isEqualTo("DIFFERENCE");
        assertAmount(afterPost, "difference", "3000000");
        assertAmount(afterPost, "ledgerFromRun", afterPost.get("runCashGross").asText());
    }

    @Test
    @DisplayName("Rejected requests: non-positive amount, posted run, bad period")
    void rejections() throws Exception {
        Employee dewi = employeeRepository.findByEmployeeId("EMP-002").orElseThrow();
        String runId = createRun("2011-02");
        assertThat(api.post("/api/payroll/" + runId + "/additions", withBody(addition(dewi.getId(), "BONUS", "0"))).status()).isEqualTo(400);
        assertThat(api.post("/api/payroll/" + runId + "/additions", withBody(Map.of("employeeId", dewi.getId(), "type", "GAJI", "amount", 100))).status()).isEqualTo(400);

        JsonNode row = json(api.post("/api/payroll/" + runId + "/additions", withBody(addition(dewi.getId(), "BONUS", "100000"))), 201);
        assertThat(json(api.get("/api/payroll/" + runId + "/additions", auth()), 200)).hasSize(1);
        assertThat(api.delete("/api/payroll/" + runId + "/additions/" + row.get("id").asText(), auth()).status()).isEqualTo(204);
        assertThat(json(api.get("/api/payroll/" + runId + "/additions", auth()), 200)).isEmpty();

        json(api.post("/api/payroll/" + runId + "/calculate", withBody(Map.of("baseSalary", 10000000, "jkkRiskClass", 1))), 200);
        json(api.post("/api/payroll/" + runId + "/approve", auth()), 200);
        int rejected = api.post("/api/payroll/" + runId + "/additions", withBody(addition(dewi.getId(), "BONUS", "100000"))).status();
        assertThat(rejected).isEqualTo(409);

        assertThat(api.get("/api/payroll/reconciliation/2011-13", auth()).status()).isEqualTo(400);
        assertThat(api.get("/api/payroll/reconciliation", auth()).status()).isEqualTo(400);
    }

    private String runTransaction(String period) throws Exception {
        JsonNode list = json(api.get("/api/payroll?year=" + period.substring(0, 4) + "&size=20", auth()), 200);
        for (JsonNode run : list.get("content")) {
            if (run.get("payrollPeriod").asText().equals(period)) {
                return run.get("transactionId").asText();
            }
        }
        throw new AssertionError("No run for " + period);
    }

    private JsonNode detailOf(JsonNode run, UUID employeeId) {
        for (JsonNode row : run.get("details")) {
            if (row.get("employeeId").asText().equals(employeeId.toString())) {
                return row;
            }
        }
        throw new AssertionError("No detail for " + employeeId);
    }

    private String createRun(String period) throws Exception {
        return json(api.post("/api/payroll", withBody(Map.of("payrollPeriod", period))), 201).get("id").asText();
    }

    private Map<String, Object> addition(UUID employeeId, String type, String amount) {
        return Map.of("employeeId", employeeId, "type", type, "amount", new BigDecimal(amount));
    }

    private Map<String, Object> journal(String description, String debitAccount) {
        return Map.of("transactionDate", WARNING_PERIOD + "-15", "description", description, "lines", List.of(
                Map.of("accountId", accountId(debitAccount), "debit", 1000000, "credit", 0),
                Map.of("accountId", accountId("1.1.02"), "debit", 0, "credit", 1000000)));
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
                .setHeader("Content-Type", "application/json").setData(Map.of("clientId", "payroll-reconciliation-test")));
        JsonNode code = json(codeResponse, 200);

        loginAsAdmin();
        navigateTo("/device?code=" + code.get("userCode").asText());
        waitForPageLoad();
        page.locator("input[name='deviceName']").fill("Payroll Reconciliation Test");
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
