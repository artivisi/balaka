package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.dto.JournalEntryRequest;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.enums.SelfTestLine;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.service.SelfTestService;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Read-only self-test API on the IT service pack (seeded self-test roles). Reserved test year 2020. */
@DisplayName("Tax self-test API")
@Import(ServiceTestDataInitializer.class)
class SelfTestApiTest extends PlaywrightTestBase {

    private static final String PATH = "/api/self-test";
    private static final int YEAR = 2020;

    @Autowired private TransactionApiService transactionApiService;
    @Autowired private TransactionService transactionService;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private SelfTestService selfTestService;

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
    @DisplayName("Report carries figures and explanations; trace rows add up; invalid month is 400")
    void reportAndTrace() throws Exception {
        ChartOfAccount bank = account("1.1.02");
        ChartOfAccount service = account("5.1.22");
        UUID revenue = posted(journal(bank, account("4.1.01"), LocalDate.of(YEAR, 2, 14), "Training tanpa faktur", "2000000"));
        UUID fee = posted(journal(service, bank, LocalDate.of(YEAR, 5, 20), "Jasa konsultan pajak", "3000000"));
        selfTestService.saveExplanation(YEAR, SelfTestLine.SERVICE_VS_PPH23_DPP, 0, "Vendor orang pribadi, dipotong PPh 21", "admin");

        JsonNode report = read(get(PATH + "?year=" + YEAR), 200);
        assertThat(report.get("year").asInt()).isEqualTo(YEAR);
        assertThat(report.get("unitemisedThreshold").decimalValue()).isEqualByComparingTo("5000000");
        JsonNode february = row(report, "REVENUE_VS_PPN_DPP", 2);
        assertThat(february.get("bookFigure").decimalValue()).isEqualByComparingTo("2000000");
        assertThat(february.get("taxFigure").decimalValue()).isEqualByComparingTo("0");
        assertThat(february.get("difference").decimalValue()).isEqualByComparingTo("2000000");
        assertThat(february.get("status").asText()).isEqualTo("DIFFERENCE");
        JsonNode serviceRow = row(report, "SERVICE_VS_PPH23_DPP", 0);
        assertThat(serviceRow.get("bookFigure").decimalValue()).isEqualByComparingTo("3000000");
        assertThat(serviceRow.get("taxFigure").decimalValue()).isEqualByComparingTo("0");
        assertThat(serviceRow.get("explanation").asText()).isEqualTo("Vendor orang pribadi, dipotong PPh 21");
        assertThat(serviceRow.get("explainedBy").asText()).isEqualTo("admin");
        assertThat(row(report, "CAPITAL_MOVEMENT", 0).get("taxFigure").isNull()).isTrue();
        assertThat(row(report, "RENT_VS_PPH42_DPP", 0).get("status").asText()).isEqualTo("NO_ACTIVITY");

        JsonNode trace = read(get(PATH + "/trace?year=" + YEAR + "&line=SERVICE_VS_PPH23_DPP&month=0"), 200);
        assertThat(trace.get("bookFigure").decimalValue()).isEqualByComparingTo("3000000");
        assertThat(trace.get("bookRows")).hasSize(1);
        JsonNode line = trace.get("bookRows").get(0);
        assertThat(line.get("source").asText()).isEqualTo("JOURNAL_LINE");
        assertThat(line.get("transactionId").asText()).isEqualTo(fee.toString());
        assertThat(line.get("accountCode").asText()).isEqualTo("5.1.22");
        assertThat(line.get("referenceId").asText()).isNotBlank();
        assertThat(trace.get("taxRows")).isEmpty();

        JsonNode revenueTrace = read(get(PATH + "/trace?year=" + YEAR + "&line=REVENUE_VS_PPN_DPP&month=2"), 200);
        assertThat(revenueTrace.get("bookRows")).singleElement()
                .satisfies(r -> assertThat(r.get("transactionId").asText()).isEqualTo(revenue.toString()));

        assertThat(get(PATH + "/trace?year=" + YEAR + "&line=CAPITAL_MOVEMENT&month=3").status()).isEqualTo(400);
        assertThat(get(PATH + "/trace?year=" + YEAR + "&line=NOPE&month=0").status()).isEqualTo(400);
        assertThat(get(PATH).status()).isEqualTo(400);
    }

    private JsonNode row(JsonNode report, String line, int month) {
        for (JsonNode l : report.get("lines")) {
            if (l.get("line").asText().equals(line) && l.get("month").asInt() == month) {
                return l;
            }
        }
        throw new AssertionError("row " + line + "/" + month + " missing");
    }

    private ChartOfAccount account(String code) {
        return accountRepository.findByAccountCode(code).orElseThrow();
    }

    private UUID journal(ChartOfAccount debit, ChartOfAccount credit, LocalDate date, String description, String amount) {
        return transactionApiService.createJournalEntry(new JournalEntryRequest(date, description, null, List.of(
                new JournalEntryRequest.JournalLineRequest(debit.getId(), new BigDecimal(amount), BigDecimal.ZERO),
                new JournalEntryRequest.JournalLineRequest(credit.getId(), BigDecimal.ZERO, new BigDecimal(amount)))), "admin")
                .transactionId();
    }

    private UUID posted(UUID id) {
        transactionService.post(id, "admin");
        return id;
    }

    private JsonNode read(APIResponse response, int status) throws Exception {
        assertThat(response.status()).as(response.text()).isEqualTo(status);
        return mapper.readTree(response.text());
    }

    private APIResponse get(String path) {
        return api.get(path, RequestOptions.create().setHeader("Authorization", "Bearer " + token));
    }

    private String deviceToken() throws Exception {
        APIResponse code = api.post("/api/device/code", RequestOptions.create().setHeader("Content-Type", "application/json")
                .setData(Map.of("clientId", "self-test-api-test")));
        JsonNode codeData = mapper.readTree(code.text());
        loginAsAdmin();
        navigateTo("/device?code=" + codeData.get("userCode").asText());
        waitForPageLoad();
        page.locator("input[name='deviceName']").fill("Self Test API Test");
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
