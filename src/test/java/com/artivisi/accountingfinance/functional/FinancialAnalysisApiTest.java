package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
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
import org.springframework.context.annotation.Import;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests for the Financial Analysis API.
 * Tests all 10 read-only analysis endpoints.
 */
@Slf4j
@DisplayName("Financial Analysis API - Functional Tests")
@Import(ServiceTestDataInitializer.class)
class FinancialAnalysisApiTest extends PlaywrightTestBase {

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
    @DisplayName("GET /api/analysis/snapshot - returns KPI snapshot for month")
    void testSnapshot() throws Exception {
        APIResponse response = get("/api/analysis/snapshot?month=2026-01");

        assertThat(response.status()).isEqualTo(200);

        JsonNode body = parse(response);
        assertThat(body.get("reportType").asText()).isEqualTo("snapshot");
        assertThat(body.get("generatedAt").asText()).isNotEmpty();
        assertThat(body.get("parameters").get("month").asText()).isEqualTo("2026-01");

        JsonNode data = body.get("data");
        assertThat(data.has("revenue")).isTrue();
        assertThat(data.has("expense")).isTrue();
        assertThat(data.has("netProfit")).isTrue();
        assertThat(data.has("cashBalance")).isTrue();
        assertThat(data.has("transactionCount")).isTrue();
        assertThat(data.has("cashBankItems")).isTrue();

        assertThat(body.get("metadata").has("currency")).isTrue();

        log.info("Snapshot test passed - revenue: {}, expense: {}, netProfit: {}",
                data.get("revenue"), data.get("expense"), data.get("netProfit"));
    }

    @Test
    @DisplayName("GET /api/analysis/trial-balance - returns trial balance")
    void testTrialBalance() throws Exception {
        APIResponse response = get("/api/analysis/trial-balance?asOfDate=2026-01-31");

        assertThat(response.status()).isEqualTo(200);

        JsonNode body = parse(response);
        assertThat(body.get("reportType").asText()).isEqualTo("trial-balance");

        JsonNode data = body.get("data");
        assertThat(data.has("items")).isTrue();
        assertThat(data.has("totalDebit")).isTrue();
        assertThat(data.has("totalCredit")).isTrue();

        JsonNode items = data.get("items");
        assertThat(items.isArray()).isTrue();

        if (items.size() > 0) {
            JsonNode first = items.get(0);
            assertThat(first.has("accountCode")).isTrue();
            assertThat(first.has("accountName")).isTrue();
            assertThat(first.has("accountType")).isTrue();
            assertThat(first.has("normalBalance")).isTrue();
            assertThat(first.has("debitBalance")).isTrue();
            assertThat(first.has("creditBalance")).isTrue();
        }

        log.info("Trial balance test passed - {} items, totalDebit: {}, totalCredit: {}",
                items.size(), data.get("totalDebit"), data.get("totalCredit"));
    }

    @Test
    @DisplayName("GET /api/analysis/income-statement - returns income statement")
    void testIncomeStatement() throws Exception {
        APIResponse response = get("/api/analysis/income-statement?startDate=2026-01-01&endDate=2026-01-31");

        assertThat(response.status()).isEqualTo(200);

        JsonNode body = parse(response);
        assertThat(body.get("reportType").asText()).isEqualTo("income-statement");

        JsonNode data = body.get("data");
        assertThat(data.has("revenueItems")).isTrue();
        assertThat(data.has("expenseItems")).isTrue();
        assertThat(data.has("totalRevenue")).isTrue();
        assertThat(data.has("totalExpense")).isTrue();
        assertThat(data.has("netIncome")).isTrue();

        JsonNode revenueItems = data.get("revenueItems");
        if (revenueItems.size() > 0) {
            JsonNode first = revenueItems.get(0);
            assertThat(first.has("accountCode")).isTrue();
            assertThat(first.has("accountName")).isTrue();
            assertThat(first.has("balance")).isTrue();
        }

        log.info("Income statement test passed - netIncome: {}", data.get("netIncome"));
    }

    @Test
    @DisplayName("GET /api/analysis/balance-sheet - returns balance sheet")
    void testBalanceSheet() throws Exception {
        APIResponse response = get("/api/analysis/balance-sheet?asOfDate=2026-01-31");

        assertThat(response.status()).isEqualTo(200);

        JsonNode body = parse(response);
        assertThat(body.get("reportType").asText()).isEqualTo("balance-sheet");

        JsonNode data = body.get("data");
        assertThat(data.has("assetItems")).isTrue();
        assertThat(data.has("liabilityItems")).isTrue();
        assertThat(data.has("equityItems")).isTrue();
        assertThat(data.has("totalAssets")).isTrue();
        assertThat(data.has("totalLiabilities")).isTrue();
        assertThat(data.has("totalEquity")).isTrue();
        assertThat(data.has("currentYearEarnings")).isTrue();

        log.info("Balance sheet test passed - totalAssets: {}, totalLiabilities: {}, totalEquity: {}",
                data.get("totalAssets"), data.get("totalLiabilities"), data.get("totalEquity"));
    }

    @Test
    @DisplayName("GET /api/analysis/cash-flow - returns cash flow statement")
    void testCashFlow() throws Exception {
        APIResponse response = get("/api/analysis/cash-flow?startDate=2026-01-01&endDate=2026-01-31");

        assertThat(response.status()).isEqualTo(200);

        JsonNode body = parse(response);
        assertThat(body.get("reportType").asText()).isEqualTo("cash-flow");

        JsonNode data = body.get("data");
        assertThat(data.has("operatingItems")).isTrue();
        assertThat(data.has("investingItems")).isTrue();
        assertThat(data.has("financingItems")).isTrue();
        assertThat(data.has("operatingTotal")).isTrue();
        assertThat(data.has("investingTotal")).isTrue();
        assertThat(data.has("financingTotal")).isTrue();
        assertThat(data.has("netCashChange")).isTrue();
        assertThat(data.has("beginningCashBalance")).isTrue();
        assertThat(data.has("endingCashBalance")).isTrue();
        assertThat(data.has("cashAccountBalances")).isTrue();

        log.info("Cash flow test passed - netCashChange: {}", data.get("netCashChange"));
    }

    @Test
    @DisplayName("GET /api/analysis/tax-summary - returns tax summary")
    void testTaxSummary() throws Exception {
        APIResponse response = get("/api/analysis/tax-summary?startDate=2026-01-01&endDate=2026-01-31");

        assertThat(response.status()).isEqualTo(200);

        JsonNode body = parse(response);
        assertThat(body.get("reportType").asText()).isEqualTo("tax-summary");

        JsonNode data = body.get("data");
        assertThat(data.has("items")).isTrue();
        assertThat(data.has("totalBalance")).isTrue();

        JsonNode items = data.get("items");
        if (items.size() > 0) {
            JsonNode first = items.get(0);
            assertThat(first.has("accountCode")).isTrue();
            assertThat(first.has("label")).isTrue();
            assertThat(first.has("balance")).isTrue();
        }

        log.info("Tax summary test passed - {} items, totalBalance: {}",
                items.size(), data.get("totalBalance"));
    }

    @Test
    @DisplayName("GET /api/analysis/receivables - returns receivable accounts")
    void testReceivables() throws Exception {
        APIResponse response = get("/api/analysis/receivables?asOfDate=2026-01-31");

        assertThat(response.status()).isEqualTo(200);

        JsonNode body = parse(response);
        assertThat(body.get("reportType").asText()).isEqualTo("receivables");

        JsonNode data = body.get("data");
        assertThat(data.has("items")).isTrue();
        assertThat(data.has("totalBalance")).isTrue();

        log.info("Receivables test passed - {} items, totalBalance: {}",
                data.get("items").size(), data.get("totalBalance"));
    }

    @Test
    @DisplayName("GET /api/analysis/payables - returns payable accounts")
    void testPayables() throws Exception {
        APIResponse response = get("/api/analysis/payables?asOfDate=2026-01-31");

        assertThat(response.status()).isEqualTo(200);

        JsonNode body = parse(response);
        assertThat(body.get("reportType").asText()).isEqualTo("payables");

        JsonNode data = body.get("data");
        assertThat(data.has("items")).isTrue();
        assertThat(data.has("totalBalance")).isTrue();

        log.info("Payables test passed - {} items, totalBalance: {}",
                data.get("items").size(), data.get("totalBalance"));
    }

    @Test
    @DisplayName("GET /api/analysis/accounts - returns chart of accounts")
    void testAccounts() throws Exception {
        APIResponse response = get("/api/analysis/accounts");

        assertThat(response.status()).isEqualTo(200);

        JsonNode body = parse(response);
        assertThat(body.get("reportType").asText()).isEqualTo("accounts");

        JsonNode data = body.get("data");
        assertThat(data.has("accounts")).isTrue();

        JsonNode accounts = data.get("accounts");
        assertThat(accounts.isArray()).isTrue();
        assertThat(accounts.size()).isGreaterThan(0);

        JsonNode first = accounts.get(0);
        assertThat(first.has("id")).isTrue();
        assertThat(first.has("code")).isTrue();
        assertThat(first.has("name")).isTrue();
        assertThat(first.has("type")).isTrue();
        assertThat(first.has("normalBalance")).isTrue();

        log.info("Accounts test passed - {} accounts", accounts.size());
    }

    @Test
    @DisplayName("GET /api/analysis/drafts - returns pending drafts")
    void testDrafts() throws Exception {
        APIResponse response = get("/api/analysis/drafts");

        assertThat(response.status()).isEqualTo(200);

        JsonNode body = parse(response);
        assertThat(body.get("reportType").asText()).isEqualTo("drafts");

        JsonNode data = body.get("data");
        assertThat(data.has("items")).isTrue();
        assertThat(data.has("pendingCount")).isTrue();
        assertThat(data.get("items").isArray()).isTrue();

        log.info("Drafts test passed - {} pending drafts", data.get("pendingCount"));
    }

    @Test
    @DisplayName("GET /api/analysis/trial-balance without asOfDate returns 400")
    void testMissingRequiredParams() {
        APIResponse response = apiContext.get("/api/analysis/trial-balance",
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + accessToken));

        assertThat(response.status()).isEqualTo(400);

        log.info("Missing params test passed - status: {}", response.status());
    }

    @Test
    @DisplayName("GET /api/analysis/snapshot without auth returns 401")
    void testUnauthenticated() {
        APIResponse response = apiContext.get("/api/analysis/snapshot?month=2026-01");

        assertThat(response.status()).isEqualTo(401);

        log.info("Unauthenticated test passed - status: {}", response.status());
    }

    // --- Helpers ---

    private APIResponse get(String path) {
        return apiContext.get(path,
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + accessToken));
    }

    private JsonNode parse(APIResponse response) throws Exception {
        return objectMapper.readTree(response.text());
    }

    private String authenticateViaDeviceFlow() throws Exception {
        Map<String, String> codeRequest = new HashMap<>();
        codeRequest.put("clientId", "analysis-test");

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

        page.locator("input[name='deviceName']").fill("Analysis Test Device");
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
