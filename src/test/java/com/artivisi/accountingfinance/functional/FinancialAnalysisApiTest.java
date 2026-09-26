package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.dto.JournalEntryRequest;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.CompanyConfig;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.JournalTemplateLine;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.CompanyConfigRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Financial Analysis API (/api/analysis/**) against transactions this class posts into
 * {@link #YEAR} (reserved for this class). Period reports (snapshot revenue/expense, income
 * statement, cash flow, tax summary, ledger movements) are asserted exactly; cumulative figures
 * (cash, trial balance, balance sheet, payables) as the change between the end of February and
 * the end of March, since other classes post into earlier years.
 *
 * March postings (bank 1.1.02):
 * - 03-05 Setoran Modal 100,000,000 (3.1.01)
 * - 03-10 Pendapatan Jasa + PPN, DPP 20,000,000: bank 22,200,000, 4.1.01 20,000,000, 2.1.03 2,200,000
 * - 03-20 Bayar Beban Listrik 3,000,000 (5.1.07)
 * - 03-25 Tagihan Vendor: 5.1.05 5,000,000 + 1.1.25 550,000 / 2.1.01 5,550,000 (no bank)
 * Later: 04-05 Bayar Beban Sewa 1,000,000; 12-31 CLOSING journal 4.1.01 D / 3.2.02 C 20,000,000.
 */
@DisplayName("Financial Analysis API - Functional Tests")
@Import(ServiceTestDataInitializer.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FinancialAnalysisApiTest extends PlaywrightTestBase {

    private static final int YEAR = 2107;
    private static final String FEB_END = YEAR + "-02-28";
    private static final String MAR_START = YEAR + "-03-01";
    private static final String MAR_END = YEAR + "-03-31";

    @Autowired private TransactionRepository transactionRepository;
    @Autowired private TransactionService transactionService;
    @Autowired private TransactionApiService transactionApiService;
    @Autowired private JournalTemplateRepository templateRepository;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private CompanyConfigRepository companyConfigRepository;

    private APIRequestContext apiContext;
    private ObjectMapper objectMapper;
    private String accessToken;

    @BeforeAll
    void postAnalysisTransactions() {
        assertThat(transactionRepository.findByTransactionDateBetweenOrderByTransactionDateDesc(
                LocalDate.of(YEAR, 1, 1), LocalDate.of(YEAR, 12, 31)))
                .as("Another test posted into reserved analysis year " + YEAR).isEmpty();

        post("Setoran Modal", "03-05", "100000000", Map.of("BANK", "1.1.02"), null);
        post("Pendapatan Jasa + PPN", "03-10", "20000000", Map.of("BANK", "1.1.02", "PENDAPATAN", "4.1.01"), null);
        post("Bayar Beban Listrik", "03-20", "3000000", Map.of("BANK", "1.1.02"), null);
        post("Tagihan Vendor", "03-25", "5550000", Map.of("BEBAN", "5.1.05", "PPN_MASUKAN", "1.1.25", "HUTANG", "2.1.01"),
                Map.of("expenseAmount", new BigDecimal("5000000"), "ppnAmount", new BigDecimal("550000"),
                        "apAmount", new BigDecimal("5550000")));
        post("Bayar Beban Sewa", "04-05", "1000000", Map.of("BANK", "1.1.02"), null);

        UUID closing = transactionApiService.createJournalEntry(new JournalEntryRequest(
                LocalDate.of(YEAR, 12, 31), "Jurnal penutup analisis " + YEAR, "CLOSING", List.of(
                        new JournalEntryRequest.JournalLineRequest(account("4.1.01").getId(), new BigDecimal("20000000"), BigDecimal.ZERO),
                        new JournalEntryRequest.JournalLineRequest(account("3.2.02").getId(), BigDecimal.ZERO, new BigDecimal("20000000")))),
                "admin").transactionId();
        transactionService.post(closing, "admin");
    }

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
    @DisplayName("GET /api/analysis/company - returns the stored company configuration")
    void testCompany() throws Exception {
        CompanyConfig config = companyConfigRepository.findFirst()
                .orElseThrow(() -> new IllegalStateException("Seeded company config missing"));

        JsonNode body = ok("/api/analysis/company");

        assertThat(body.get("reportType").asText()).isEqualTo("company");
        JsonNode data = body.get("data");
        assertThat(data.get("companyName").asText()).isEqualTo(config.getCompanyName());
        assertThat(data.get("industry").asText()).isEqualTo("it-service");
        assertThat(data.get("currencyCode").asText()).isEqualTo("IDR");
        assertThat(data.get("fiscalYearStartMonth").asInt()).isEqualTo(config.getFiscalYearStartMonth());
        assertThat(data.get("npwp").asText()).isEqualTo(config.getNpwp());
    }

    @Test
    @DisplayName("GET /api/analysis/snapshot - March revenue, expense, profit and count; cash and payables moved by March's postings")
    void testSnapshot() throws Exception {
        JsonNode body = ok("/api/analysis/snapshot?month=" + YEAR + "-03");
        JsonNode february = ok("/api/analysis/snapshot?month=" + YEAR + "-02").get("data");

        assertThat(body.get("reportType").asText()).isEqualTo("snapshot");
        assertThat(body.get("parameters").get("month").asText()).isEqualTo(YEAR + "-03");
        assertThat(body.get("metadata").get("currency").asText()).isEqualTo("IDR");
        JsonNode data = body.get("data");
        assertThat(data.get("month").asText()).isEqualTo(YEAR + "-03");
        assertAmount(data.get("revenue"), "20000000");
        assertAmount(data.get("expense"), "8000000");
        assertAmount(data.get("netProfit"), "12000000");
        assertThat(data.get("transactionCount").asLong()).isEqualTo(4);
        assertAmount(february.get("revenue"), "0");
        assertThat(february.get("transactionCount").asLong()).isZero();
        assertAmount(delta(data.get("cashBalance"), february.get("cashBalance")), "119200000");
        assertAmount(delta(data.get("payablesBalance"), february.get("payablesBalance")), "5550000");
    }

    @Test
    @DisplayName("GET /api/analysis/snapshot with separate month and year params reports the same month")
    void testSnapshotWithSeparateMonthYear() throws Exception {
        JsonNode body = ok("/api/analysis/snapshot?month=3&year=" + YEAR);

        assertThat(body.get("parameters").get("month").asText()).isEqualTo("3");
        JsonNode data = body.get("data");
        assertThat(data.get("month").asText()).isEqualTo(YEAR + "-03");
        assertAmount(data.get("revenue"), "20000000");
        assertAmount(data.get("expense"), "8000000");
        assertThat(data.get("transactionCount").asLong()).isEqualTo(4);
    }

    @Test
    @DisplayName("GET /api/analysis/trial-balance - each account moves by March's postings and stays balanced")
    void testTrialBalance() throws Exception {
        JsonNode march = ok("/api/analysis/trial-balance?asOfDate=" + MAR_END).get("data");
        JsonNode february = ok("/api/analysis/trial-balance?asOfDate=" + FEB_END).get("data");

        assertAmount(march.get("totalDebit"), march.get("totalCredit").asText());
        Map<String, BigDecimal> before = netByAccount(february.get("items"));
        Map<String, BigDecimal> after = netByAccount(march.get("items"));
        Map<String, String> expected = Map.of(
                "1.1.02", "119200000", "1.1.25", "550000", "5.1.07", "3000000", "5.1.05", "5000000",
                "3.1.01", "-100000000", "4.1.01", "-20000000", "2.1.03", "-2200000", "2.1.01", "-5550000");
        expected.forEach((code, change) -> assertThat(after.getOrDefault(code, BigDecimal.ZERO)
                .subtract(before.getOrDefault(code, BigDecimal.ZERO)))
                .as("debit - credit change of %s", code).isEqualByComparingTo(change));
        JsonNode bank = item(march.get("items"), "accountCode", "1.1.02");
        assertThat(bank.get("accountName").asText()).isEqualTo("Bank BCA");
        assertThat(bank.get("accountType").asText()).isEqualTo("ASSET");
        assertThat(bank.get("normalBalance").asText()).isEqualTo("DEBIT");
    }

    @Test
    @DisplayName("GET /api/analysis/income-statement - March lines and totals; the full year with and without the closing journal")
    void testIncomeStatement() throws Exception {
        JsonNode data = ok("/api/analysis/income-statement?startDate=" + MAR_START + "&endDate=" + MAR_END).get("data");

        assertThat(lines(data.get("revenueItems"))).containsExactly("4.1.01 Pendapatan Jasa Training 20000000");
        assertThat(lines(data.get("expenseItems"))).containsExactlyInAnyOrder(
                "5.1.05 Beban Sewa 5000000", "5.1.07 Beban Listrik 3000000");
        assertAmount(data.get("totalRevenue"), "20000000");
        assertAmount(data.get("totalExpense"), "8000000");
        assertAmount(data.get("netIncome"), "12000000");

        String year = "/api/analysis/income-statement?startDate=" + YEAR + "-01-01&endDate=" + YEAR + "-12-31";
        JsonNode withClosing = ok(year).get("data");
        assertAmount(withClosing.get("totalRevenue"), "0");
        assertAmount(withClosing.get("totalExpense"), "9000000");
        JsonNode excludingClosing = ok(year + "&excludeClosing=true").get("data");
        assertAmount(excludingClosing.get("totalRevenue"), "20000000");
        assertAmount(excludingClosing.get("totalExpense"), "9000000");
        assertAmount(excludingClosing.get("netIncome"), "11000000");
    }

    @Test
    @DisplayName("GET /api/analysis/balance-sheet - balances at each date; March adds its assets, liabilities, capital and profit")
    void testBalanceSheet() throws Exception {
        JsonNode march = ok("/api/analysis/balance-sheet?asOfDate=" + MAR_END).get("data");
        JsonNode february = ok("/api/analysis/balance-sheet?asOfDate=" + FEB_END).get("data");

        for (JsonNode sheet : List.of(march, february)) {
            assertAmount(sheet.get("totalAssets"),
                    new BigDecimal(sheet.get("totalLiabilities").asText()).add(new BigDecimal(sheet.get("totalEquity").asText()))
                            .toPlainString());
        }
        assertAmount(march.get("currentYearEarnings"), "12000000");
        assertAmount(february.get("currentYearEarnings"), "0");
        assertAmount(delta(march.get("totalAssets"), february.get("totalAssets")), "119750000");
        assertAmount(delta(march.get("totalLiabilities"), february.get("totalLiabilities")), "7750000");
        assertAmount(delta(march.get("totalEquity"), february.get("totalEquity")), "112000000");
    }

    @Test
    @DisplayName("GET /api/analysis/cash-flow - March cash movements per template and section")
    void testCashFlow() throws Exception {
        JsonNode data = ok("/api/analysis/cash-flow?startDate=" + MAR_START + "&endDate=" + MAR_END).get("data");

        assertThat(cashFlowLines(data.get("operatingItems"))).containsExactly(
                "Pendapatan Jasa + PPN 22200000", "Bayar Beban Listrik -3000000");
        assertThat(data.get("investingItems")).isEmpty();
        assertThat(cashFlowLines(data.get("financingItems"))).containsExactly("Setoran Modal 100000000");
        assertAmount(data.get("operatingTotal"), "19200000");
        assertAmount(data.get("investingTotal"), "0");
        assertAmount(data.get("financingTotal"), "100000000");
        assertAmount(data.get("netCashChange"), "119200000");
        assertAmount(delta(data.get("endingCashBalance"), data.get("beginningCashBalance")), "119200000");
    }

    @Test
    @DisplayName("GET /api/analysis/tax-summary - March PPN in and out per tax account, no filings registered")
    void testTaxSummary() throws Exception {
        JsonNode data = ok("/api/analysis/tax-summary?startDate=" + MAR_START + "&endDate=" + MAR_END).get("data");

        List<String> items = new ArrayList<>();
        data.get("items").forEach(i -> items.add(i.get("accountCode").asText() + " " + i.get("label").asText() + " D"
                + plain(i.get("debit")) + " C" + plain(i.get("credit")) + " = " + plain(i.get("balance"))));
        assertThat(items).containsExactly(
                "1.1.25 PPN Masukan D550000 C0 = 550000",
                "2.1.03 Hutang PPN D0 C2200000 = 2200000",
                "2.1.20 Hutang PPh 21 D0 C0 = 0",
                "2.1.21 Hutang PPh 23 D0 C0 = 0",
                "2.1.22 Hutang PPh 4(2) D0 C0 = 0",
                "2.1.23 Hutang PPh 25 D0 C0 = 0",
                "2.1.24 Hutang PPh 29 D0 C0 = 0");
        assertThat(data.get("filings")).isEmpty();
    }

    @Test
    @DisplayName("GET /api/analysis/payables - Hutang Usaha grows by the March vendor bill")
    void testPayables() throws Exception {
        JsonNode march = ok("/api/analysis/payables?asOfDate=" + MAR_END).get("data");
        JsonNode february = ok("/api/analysis/payables?asOfDate=" + FEB_END).get("data");

        JsonNode item = item(march.get("items"), "accountCode", "2.1.01");
        assertThat(item.get("accountName").asText()).isEqualTo("Hutang Usaha");
        assertThat(march.get("items")).allSatisfy(i -> assertThat(i.get("accountCode").asText()).startsWith("2.1.01"));
        assertAmount(delta(march.get("totalBalance"), february.get("totalBalance")), "5550000");
    }

    @Test
    @DisplayName("GET /api/analysis/accounts - lists transactable accounts with id, type and normal balance")
    void testAccounts() throws Exception {
        JsonNode accounts = ok("/api/analysis/accounts").get("data").get("accounts");

        ChartOfAccount bank = account("1.1.02");
        JsonNode listed = item(accounts, "code", "1.1.02");
        assertThat(listed.get("id").asText()).isEqualTo(bank.getId().toString());
        assertThat(listed.get("name").asText()).isEqualTo("Bank BCA");
        assertThat(listed.get("type").asText()).isEqualTo("ASSET");
        assertThat(listed.get("normalBalance").asText()).isEqualTo("DEBIT");
        JsonNode revenue = item(accounts, "code", "4.1.01");
        assertThat(revenue.get("type").asText()).isEqualTo("REVENUE");
        assertThat(revenue.get("normalBalance").asText()).isEqualTo("CREDIT");
        List<String> codes = new ArrayList<>();
        accounts.forEach(a -> codes.add(a.get("code").asText()));
        assertThat(codes).doesNotContain("1.1", "4.1").doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("GET /api/analysis/drafts - lists a pending draft created through the API")
    void testDrafts() throws Exception {
        String merchant = "Analisis Draft " + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> draft = new HashMap<>();
        draft.put("merchant", merchant);
        draft.put("amount", 45000);
        draft.put("transactionDate", "2001-01-15");
        draft.put("currency", "IDR");
        draft.put("confidence", 0.9);
        draft.put("source", "analysis-test");
        APIResponse created = apiContext.post("/api/drafts/from-text", RequestOptions.create()
                .setHeader("Content-Type", "application/json")
                .setHeader("Authorization", "Bearer " + accessToken)
                .setData(draft));
        assertThat(created.status()).as(created.text()).isEqualTo(201);
        String draftId = parse(created).get("draftId").asText();

        JsonNode data = ok("/api/analysis/drafts?size=100").get("data");

        assertThat(data.get("currentPage").asInt()).isZero();
        assertThat(data.get("pageSize").asInt()).isEqualTo(100);
        JsonNode item = item(data.get("items"), "id", draftId);
        assertThat(item.get("status").asText()).isEqualTo("PENDING");
        assertThat(item.get("merchantName").asText()).isEqualTo(merchant);
        assertAmount(item.get("amount"), "45000");
        assertThat(item.get("transactionDate").asText()).isEqualTo("2001-01-15");
        assertThat(item.get("source").asText()).isEqualTo("API");
        assertThat(item.get("apiSource").asText()).isEqualTo("analysis-test");
        assertThat(data.get("items")).allSatisfy(i -> assertThat(i.get("status").asText()).isEqualTo("PENDING"));
    }

    @Test
    @DisplayName("GET /api/analysis/accounts/{id}/ledger - March bank entries with running balance from the opening balance")
    void testAccountLedger() throws Exception {
        String bankId = account("1.1.02").getId().toString();

        JsonNode data = ok("/api/analysis/accounts/" + bankId + "/ledger?startDate=" + MAR_START + "&endDate=" + MAR_END)
                .get("data");

        assertThat(data.get("accountCode").asText()).isEqualTo("1.1.02");
        assertThat(data.get("accountName").asText()).isEqualTo("Bank BCA");
        assertThat(data.get("normalBalance").asText()).isEqualTo("DEBIT");
        assertAmount(data.get("totalDebit"), "122200000");
        assertAmount(data.get("totalCredit"), "3000000");
        BigDecimal opening = new BigDecimal(data.get("openingBalance").asText());
        assertAmount(data.get("closingBalance"), opening.add(new BigDecimal("119200000")).toPlainString());
        JsonNode entries = data.get("entries");
        assertThat(entries).hasSize(3);
        String[][] expected = {
                {YEAR + "-03-05", "100000000", "0", "100000000"},
                {YEAR + "-03-10", "22200000", "0", "122200000"},
                {YEAR + "-03-20", "0", "3000000", "119200000"}};
        for (int i = 0; i < expected.length; i++) {
            JsonNode e = entries.get(i);
            assertThat(e.get("transactionDate").asText()).isEqualTo(expected[i][0]);
            assertAmount(e.get("debitAmount"), expected[i][1]);
            assertAmount(e.get("creditAmount"), expected[i][2]);
            assertAmount(e.get("runningBalance"), opening.add(new BigDecimal(expected[i][3])).toPlainString());
            assertThat(e.get("journalNumber").asText()).isNotBlank();
        }
    }

    @Test
    @DisplayName("GET /api/analysis/trial-balance without asOfDate returns 400")
    void testMissingRequiredParams() {
        APIResponse response = apiContext.get("/api/analysis/trial-balance",
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + accessToken));

        assertThat(response.status()).isEqualTo(400);
    }

    @Test
    @DisplayName("GET /api/analysis/snapshot without auth returns 401 and no data")
    void testUnauthenticated() {
        APIResponse response = apiContext.get("/api/analysis/snapshot?month=" + YEAR + "-03");

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.text()).doesNotContain("\"reportType\"");
    }

    // --- Helpers ---

    private APIResponse get(String path) {
        return apiContext.get(path,
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + accessToken));
    }

    private JsonNode ok(String path) throws Exception {
        APIResponse response = get(path);
        assertThat(response.status()).as("%s: %s", path, response.text()).isEqualTo(200);
        return parse(response);
    }

    private JsonNode parse(APIResponse response) throws Exception {
        return objectMapper.readTree(response.text());
    }

    private static String plain(JsonNode amount) {
        return new BigDecimal(amount.asText()).stripTrailingZeros().toPlainString();
    }

    private static void assertAmount(JsonNode actual, String expected) {
        assertThat(new BigDecimal(actual.asText())).isEqualByComparingTo(expected);
    }

    private static void assertAmount(BigDecimal actual, String expected) {
        assertThat(actual).isEqualByComparingTo(expected);
    }

    private static BigDecimal delta(JsonNode after, JsonNode before) {
        return new BigDecimal(after.asText()).subtract(new BigDecimal(before.asText()));
    }

    private static JsonNode item(JsonNode items, String field, String value) {
        for (JsonNode i : items) {
            if (i.get(field).asText().equals(value)) {
                return i;
            }
        }
        throw new AssertionError("No item with " + field + "=" + value + " in " + items);
    }

    private static Map<String, BigDecimal> netByAccount(JsonNode items) {
        Map<String, BigDecimal> net = new LinkedHashMap<>();
        items.forEach(i -> net.put(i.get("accountCode").asText(),
                new BigDecimal(i.get("debitBalance").asText()).subtract(new BigDecimal(i.get("creditBalance").asText()))));
        return net;
    }

    private static List<String> lines(JsonNode items) {
        List<String> lines = new ArrayList<>();
        items.forEach(i -> lines.add(i.get("accountCode").asText() + " " + i.get("accountName").asText() + " " + plain(i.get("balance"))));
        return lines;
    }

    private static List<String> cashFlowLines(JsonNode items) {
        List<String> lines = new ArrayList<>();
        items.forEach(i -> lines.add(i.get("description").asText() + " " + plain(i.get("amount"))));
        return lines;
    }

    private ChartOfAccount account(String code) {
        return accountRepository.findByAccountCode(code)
                .orElseThrow(() -> new IllegalStateException("Seeded account missing: " + code));
    }

    private void post(String templateName, String monthDay, String amount, Map<String, String> hints,
                      Map<String, BigDecimal> variables) {
        JournalTemplate template = templateRepository.findByTemplateNameWithLines(templateName)
                .orElseThrow(() -> new IllegalStateException("Seeded template missing: " + templateName));
        Map<UUID, UUID> mappings = new HashMap<>();
        hints.forEach((hint, code) -> {
            JournalTemplateLine line = template.getLines().stream()
                    .filter(l -> hint.equals(l.getAccountHint())).findFirst()
                    .orElseThrow(() -> new IllegalStateException(templateName + " has no " + hint + " line"));
            mappings.put(line.getId(), account(code).getId());
        });
        Transaction transaction = new Transaction();
        transaction.setJournalTemplate(template);
        transaction.setTransactionDate(LocalDate.parse(YEAR + "-" + monthDay));
        transaction.setAmount(new BigDecimal(amount));
        transaction.setDescription(templateName + " analisis");
        Transaction created = transactionService.create(transaction, mappings, variables);
        transactionService.post(created.getId(), "admin");
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
