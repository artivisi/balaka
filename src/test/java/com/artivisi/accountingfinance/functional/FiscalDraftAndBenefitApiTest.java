package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.dto.JournalEntryRequest;
import com.artivisi.accountingfinance.entity.PayrollRun;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.EmployeeRepository;
import com.artivisi.accountingfinance.service.PayrollService;
import com.artivisi.accountingfinance.service.TransactionApiService;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Fiscal adjustment generation/confirmation, benefit-in-kind endpoints and account fiscalTreatment over the API.
 * Reserved test year 2131. EMP-002 is TK/0.
 */
@DisplayName("Fiscal drafts and benefit-in-kind API")
@Import(ServiceTestDataInitializer.class)
class FiscalDraftAndBenefitApiTest extends PlaywrightTestBase {

    private static final int YEAR = 2131;

    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private PayrollService payrollService;
    @Autowired private TransactionService transactionService;
    @Autowired private TransactionApiService transactionApiService;

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
    @DisplayName("Generate, filter by status, confirm; only confirmed rows reach rekonsiliasi-fiskal")
    void generateAndConfirm() throws Exception {
        post("5.8.04", "1000000", LocalDate.of(YEAR, 3, 1));

        JsonNode generated = json(api.post("/api/fiscal-adjustments/generate?year=" + YEAR, auth()), 200);
        JsonNode draft = onlyDraftFor(generated.get("drafts"), "5.8.04");
        assertThat(draft.get("status").asText()).isEqualTo("DRAFT");
        assertThat(draft.get("source").asText()).isEqualTo("GENERATED");
        assertThat(new BigDecimal(draft.get("amount").asText())).isEqualByComparingTo("1000000");
        String id = draft.get("id").asText();

        JsonNode drafts = json(api.get("/api/fiscal-adjustments?status=DRAFT&year=" + YEAR, auth()), 200);
        assertThat(drafts.findValuesAsText("id")).contains(id);
        JsonNode confirmedBefore = json(api.get("/api/fiscal-adjustments?status=CONFIRMED&year=" + YEAR, auth()), 200);
        assertThat(confirmedBefore.findValuesAsText("id")).doesNotContain(id);

        JsonNode reconBefore = json(api.get("/api/tax-export/rekonsiliasi-fiskal?year=" + YEAR, auth()), 200).get("data");
        assertThat(new BigDecimal(reconBefore.get("totalPositiveAdjustment").asText())).isEqualByComparingTo("0");
        assertThat(reconBefore.get("pendingDraftAdjustments").asInt()).isGreaterThanOrEqualTo(1);

        JsonNode confirmed = json(api.post("/api/fiscal-adjustments/" + id + "/confirm", auth()), 200);
        assertThat(confirmed.get("status").asText()).isEqualTo("CONFIRMED");

        JsonNode reconAfter = json(api.get("/api/tax-export/rekonsiliasi-fiskal?year=" + YEAR, auth()), 200).get("data");
        assertThat(new BigDecimal(reconAfter.get("totalPositiveAdjustment").asText())).isEqualByComparingTo("1000000");

        assertThat(api.post("/api/fiscal-adjustments/" + id + "/confirm", auth()).status()).isEqualTo(409);
        assertThat(api.post("/api/fiscal-adjustments/" + UUID.randomUUID() + "/confirm", auth()).status()).isEqualTo(404);
    }

    @Test
    @DisplayName("Benefit endpoints: create, validate, list, delete; 1721-A1 bruto includes natura")
    void benefitLifecycle() throws Exception {
        UUID employee = employeeRepository.findByEmployeeId("EMP-002")
                .orElseThrow(() -> new IllegalStateException("Seeded employee EMP-002 missing")).getId();
        UUID transaction = post("5.8.03", "2000000", LocalDate.of(YEAR, 6, 1));

        assertThat(api.post("/api/benefits-in-kind", withBody(benefit(transaction, employee, 2500000))).status()).isEqualTo(400);
        assertThat(api.post("/api/benefits-in-kind", withBody(benefit(UUID.randomUUID(), employee, 1000))).status()).isEqualTo(404);

        JsonNode created = json(api.post("/api/benefits-in-kind", withBody(benefit(transaction, employee, 500000))), 201);
        assertThat(created.get("employeeCode").asText()).isEqualTo("EMP-002");
        assertThat(created.get("payrollPeriod").isNull()).isTrue();
        String removable = created.get("id").asText();
        assertThat(api.delete("/api/benefits-in-kind/" + removable, auth()).status()).isEqualTo(204);
        assertThat(json(api.get("/api/benefits-in-kind?year=" + YEAR, auth()), 200).findValuesAsText("id")).doesNotContain(removable);

        String counted = json(api.post("/api/benefits-in-kind", withBody(benefit(transaction, employee, 2000000))), 201).get("id").asText();

        PayrollRun run = payrollService.createPayrollRun(YearMonth.of(YEAR, 6));
        payrollService.calculatePayroll(run.getId(), new BigDecimal("10000000"), 1);
        payrollService.approvePayroll(run.getId());
        payrollService.postPayroll(run.getId());

        JsonNode rows = json(api.get("/api/benefits-in-kind?year=" + YEAR, auth()), 200);
        JsonNode row = null;
        for (JsonNode r : rows) {
            if (r.get("id").asText().equals(counted)) {
                row = r;
            }
        }
        assertThat(row).isNotNull();
        assertThat(row.get("payrollPeriod").asText()).isEqualTo(YEAR + "-06");
        assertThat(api.delete("/api/benefits-in-kind/" + counted, auth()).status()).isEqualTo(409);

        JsonNode a1 = json(api.get("/api/payroll/employees/" + employee + "/1721-a1?year=" + YEAR, auth()), 200);
        assertThat(new BigDecimal(a1.get("calculation").get("penghasilanBruto").asText())).isEqualByComparingTo("12000000");
        assertThat(new BigDecimal(a1.get("calculation").get("pph21Dipotong").asText())).isEqualByComparingTo("480000");
    }

    @Test
    @DisplayName("Account API exposes fiscalTreatment; non-expense value is rejected")
    void accountApi() throws Exception {
        UUID natura = accountRepository.findByAccountCode("5.8.03").orElseThrow().getId();
        assertThat(json(api.get("/api/accounts/" + natura, auth()), 200).get("fiscalTreatment").asText()).isEqualTo("CONDITIONAL");

        String code = "1.1.A" + UUID.randomUUID().toString().substring(0, 8);
        APIResponse rejected = api.post("/api/accounts", withBody(Map.of("accountCode", code, "accountName", "Aset Uji",
                "accountType", "ASSET", "fiscalTreatment", "DEDUCTIBLE")));
        assertThat(rejected.status()).isEqualTo(400);
        assertThat(accountRepository.findByAccountCode(code)).isEmpty();
    }

    private Map<String, Object> benefit(UUID transaction, UUID employee, long amount) {
        return Map.of("transactionId", transaction, "employeeId", employee, "amount", amount, "description", "Natura API");
    }

    private JsonNode onlyDraftFor(JsonNode drafts, String code) {
        for (JsonNode d : drafts) {
            if (code.equals(d.get("accountCode").asText())) {
                return d;
            }
        }
        throw new AssertionError("No draft for " + code + " in " + drafts);
    }

    private UUID post(String accountCode, String amount, LocalDate date) {
        BigDecimal value = new BigDecimal(amount);
        UUID account = accountRepository.findByAccountCode(accountCode).orElseThrow().getId();
        UUID bank = accountRepository.findByAccountCode("1.1.02").orElseThrow().getId();
        UUID id = transactionApiService.createJournalEntry(new JournalEntryRequest(date, "Uji API " + accountCode, null, List.of(
                new JournalEntryRequest.JournalLineRequest(account, value, BigDecimal.ZERO),
                new JournalEntryRequest.JournalLineRequest(bank, BigDecimal.ZERO, value))), "admin").transactionId();
        transactionService.post(id, "admin");
        return id;
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
                .setHeader("Content-Type", "application/json").setData(Map.of("clientId", "fiscal-draft-api-test")));
        JsonNode code = json(codeResponse, 200);

        loginAsAdmin();
        navigateTo("/device?code=" + code.get("userCode").asText());
        waitForPageLoad();
        page.locator("input[name='deviceName']").fill("Fiscal Draft API Test");
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
