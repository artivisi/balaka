package com.artivisi.accountingfinance.functional.seller;

import com.artivisi.accountingfinance.dto.JournalEntryRequest;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.service.TransactionApiService;
import com.artivisi.accountingfinance.service.TransactionService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Online seller analysis report: reads the January 2024 figures seeded from testdata/seller/financial-*.csv through
 * the analysis API (no seller class posts into January 2024), computes the e-commerce KPIs, publishes the report and
 * checks it on the report list and detail pages.
 *
 * Expected January 2024: revenue 207,000,000 (Tokopedia 95,000,000 + Shopee 112,000,000), COGS 171,000,000,
 * marketplace fees 6,210,000, shipping 3,500,000, net profit 26,290,000, cash and marketplace balances 7,290,000.
 */
@DisplayName("Analysis Report - Online Seller")
@Import(SellerTestDataInitializer.class)
class SellerAnalysisReportTest extends PlaywrightTestBase {

    private static final Locale ID = Locale.of("id", "ID");
    private static final int RECEIVABLE_YEAR = 2110;

    @Autowired private TransactionApiService transactionApiService;
    @Autowired private TransactionService transactionService;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private ChartOfAccountRepository accountRepository;

    private APIRequestContext apiContext;
    private ObjectMapper objectMapper;
    private String accessToken;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        // Device flow auth requires browser login during setUp — increase navigation timeout
        page.setDefaultNavigationTimeout(15000);

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
    @DisplayName("Should compute e-commerce KPIs from the January 2024 figures, publish them and show them on the report pages")
    void shouldPublishEcommerceKpisFromJanuary2024() throws Exception {
        JsonNode company = getApi("/api/analysis/company").get("data");
        assertEquals("Toko Online Maju Bersama", company.get("companyName").asText());
        assertEquals("online-seller", company.get("industry").asText());

        JsonNode snapshot = getApi("/api/analysis/snapshot?month=2024-01").get("data");
        assertAmount(207_000_000L, snapshot.get("revenue"));
        assertAmount(180_710_000L, snapshot.get("expense"));
        assertAmount(26_290_000L, snapshot.get("netProfit"));
        assertEquals(0, new BigDecimal("12.70").compareTo(snapshot.get("profitMargin").decimalValue()));
        assertAmount(7_290_000L, snapshot.get("cashBalance"));

        JsonNode income = getApi("/api/analysis/income-statement?startDate=2024-01-01&endDate=2024-01-31").get("data");
        Map<String, BigDecimal> revenueItems = items(income.get("revenueItems"));
        Map<String, BigDecimal> expenseItems = items(income.get("expenseItems"));
        assertEquals(Map.of("4.1.01", 95_000_000L, "4.1.02", 112_000_000L), longs(revenueItems));
        assertEquals(Map.of("5.1.01", 171_000_000L, "5.2.01", 2_850_000L, "5.2.02", 3_360_000L,
                "5.3.01", 3_500_000L), longs(expenseItems));
        BigDecimal totalRevenue = income.get("totalRevenue").decimalValue();
        BigDecimal netProfit = income.get("netIncome").decimalValue();
        assertAmount(207_000_000L, income.get("totalRevenue"));
        assertAmount(26_290_000L, income.get("netIncome"));

        BigDecimal cogs = sumByPrefix(expenseItems, "5.1");
        BigDecimal marketplaceFees = sumByPrefix(expenseItems, "5.2");
        BigDecimal shippingCost = sumByPrefix(expenseItems, "5.3");
        BigDecimal grossProfit = totalRevenue.subtract(cogs);
        BigDecimal grossMargin = percent(grossProfit, totalRevenue);
        BigDecimal cogsRatio = percent(cogs, totalRevenue);
        BigDecimal feeRatio = percent(marketplaceFees, totalRevenue);
        assertEquals("17.4|82.6|3.0", String.join("|", grossMargin.toPlainString(), cogsRatio.toPlainString(),
                feeRatio.toPlainString()));

        List<Map<String, String>> metrics = List.of(
                metric("Penjualan Bersih", formatCurrency(totalRevenue), "info"),
                metric("HPP (Harga Pokok)", formatCurrency(cogs), "info"),
                metric("Laba Kotor", formatCurrency(grossProfit), grossProfit.signum() > 0 ? "positive" : "negative"),
                metric("Gross Margin", grossMargin + "%",
                        grossMargin.compareTo(BigDecimal.valueOf(15)) >= 0 ? "positive" : "warning"),
                metric("Biaya Marketplace", formatCurrency(marketplaceFees),
                        feeRatio.compareTo(BigDecimal.valueOf(5)) <= 0 ? "positive" : "warning"),
                metric("Biaya Pengiriman", formatCurrency(shippingCost), "info"));
        assertEquals(List.of(
                "Penjualan Bersih=Rp 207.000.000/info", "HPP (Harga Pokok)=Rp 171.000.000/info",
                "Laba Kotor=Rp 36.000.000/positive", "Gross Margin=17.4%/positive",
                "Biaya Marketplace=Rp 6.210.000/positive", "Biaya Pengiriman=Rp 3.500.000/info"),
                metrics.stream().map(m -> m.get("name") + "=" + m.get("value") + "/" + m.get("status")).toList());

        List<Map<String, String>> findings = List.of(
                Map.of("category", "profitabilitas", "severity", "info",
                        "description", "Gross margin " + grossMargin + "% (HPP " + formatCurrency(cogs)
                                + " dari penjualan " + formatCurrency(totalRevenue) + "). Benchmark e-commerce: 15-30%."),
                Map.of("category", "biaya-marketplace", "severity", "info",
                        "description", "Total biaya marketplace " + formatCurrency(marketplaceFees) + " (" + feeRatio
                                + "% dari penjualan). Termasuk admin fee per platform."),
                Map.of("category", "logistik", "severity", "info",
                        "description", "Biaya pengiriman subsidi penjual " + formatCurrency(shippingCost) + "."));
        List<Map<String, String>> recommendations = List.of(
                Map.of("priority", "high",
                        "description", "Optimalkan HPP melalui negosiasi volume dengan supplier. COGS ratio saat ini "
                                + cogsRatio + "%. Target: di bawah 80%.",
                        "impact", "Peningkatan gross margin"),
                Map.of("priority", "medium",
                        "description", "Evaluasi performa per platform marketplace.",
                        "impact", "Efisiensi biaya marketplace"));
        List<Map<String, String>> risks = List.of(
                Map.of("severity", "high",
                        "description", "Ketergantungan pada platform marketplace.",
                        "mitigation", "Bangun channel penjualan sendiri sebagai diversifikasi."),
                Map.of("severity", "medium",
                        "description", "Fluktuasi harga dari supplier. COGS ratio " + cogsRatio + "%.",
                        "mitigation", "Kontrak harga tetap dengan supplier utama."));
        String title = "Review Keuangan Januari 2024 - Toko Online Maju Bersama";
        String summary = "Review keuangan Toko Online Maju Bersama periode Januari 2024. Penjualan "
                + formatCurrency(totalRevenue) + " dengan HPP " + formatCurrency(cogs) + " menghasilkan gross margin "
                + grossMargin + "%. Laba bersih " + formatCurrency(netProfit) + ".";
        assertEquals("Review keuangan Toko Online Maju Bersama periode Januari 2024. Penjualan Rp 207.000.000 dengan "
                + "HPP Rp 171.000.000 menghasilkan gross margin 17.4%. Laba bersih Rp 26.290.000.", summary);

        Map<String, Object> request = new HashMap<>();
        request.put("title", title);
        request.put("reportType", "monthly-review");
        request.put("industry", "online-seller");
        request.put("periodStart", "2024-01-01");
        request.put("periodEnd", "2024-01-31");
        request.put("aiSource", "claude-code");
        request.put("aiModel", "claude-opus-4-6");
        request.put("executiveSummary", summary);
        request.put("metrics", metrics);
        request.put("findings", findings);
        request.put("recommendations", recommendations);
        request.put("risks", risks);

        APIResponse publish = apiContext.post("/api/analysis/reports", RequestOptions.create()
                .setHeader("Authorization", "Bearer " + accessToken)
                .setHeader("Content-Type", "application/json")
                .setData(request));
        assertEquals(201, publish.status(), publish.text());
        JsonNode report = objectMapper.readTree(publish.text()).get("data");
        String reportId = report.get("id").asText();
        assertEquals(String.join("|", title, "monthly-review", "online-seller", "2024-01-01", "2024-01-31", "admin"),
                String.join("|", report.get("title").asText(), report.get("reportType").asText(),
                        report.get("industry").asText(), report.get("periodStart").asText(),
                        report.get("periodEnd").asText(), report.get("createdBy").asText()));
        assertEquals(metrics, objectMapper.convertValue(report.get("metrics"),
                objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class)));

        loginAsAdmin();
        navigateTo("/analysis-reports");
        waitForPageLoad();
        Locator card = page.getByTestId("report-" + reportId);
        assertThat(card.getByTestId("report-title")).hasText(title);
        assertThat(card.getByTestId("report-industry")).hasText("Toko Online");
        assertThat(card.getByTestId("report-type")).hasText("monthly-review");
        assertThat(card).containsText("6 metrik");
        assertThat(card).containsText("3 temuan");
        takeManualScreenshot("analysis-reports/seller-list");

        card.click();
        page.waitForURL("**/analysis-reports/" + reportId);
        waitForPageLoad();
        assertThat(page.getByTestId("detail-title")).hasText(title);
        assertThat(page.getByTestId("detail-industry")).hasText("Toko Online");
        assertThat(page.getByTestId("detail-report-type")).hasText("monthly-review");
        assertThat(page.getByTestId("detail-ai-source")).hasText("claude-code");
        assertThat(page.getByTestId("detail-ai-model")).hasText("claude-opus-4-6");
        assertThat(page.getByTestId("executive-summary").locator("p")).hasText(summary);
        for (Map<String, String> m : metrics) {
            assertThat(page.getByTestId("metric-" + m.get("name")).locator("span.text-xl")).hasText(m.get("value"));
        }
        assertThat(page.getByTestId("findings-section").locator("tbody tr")).hasCount(3);
        assertThat(page.getByTestId("findings-section").locator("tbody tr").nth(1).locator("td").nth(2))
                .hasText(findings.get(1).get("description"));
        assertThat(page.getByTestId("recommendations-section").locator("p.text-gray-800")).hasText(new String[]{
                recommendations.get(0).get("description"), recommendations.get(1).get("description")});
        assertThat(page.getByTestId("risks-section").locator("p.text-gray-800")).hasText(new String[]{
                risks.get(0).get("description"), risks.get(1).get("description")});
        takeManualScreenshot("analysis-reports/seller-detail");
    }

    // --- Helpers ---

    private static Map<String, String> metric(String name, String value, String status) {
        Map<String, String> metric = new LinkedHashMap<>();
        metric.put("name", name);
        metric.put("value", value);
        metric.put("status", status);
        return metric;
    }

    private static Map<String, BigDecimal> items(JsonNode items) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        items.forEach(item -> result.put(item.get("accountCode").asText(), item.get("balance").decimalValue()));
        return result;
    }

    private static Map<String, Long> longs(Map<String, BigDecimal> items) {
        Map<String, Long> result = new HashMap<>();
        items.forEach((code, balance) -> result.put(code, balance.longValueExact()));
        return result;
    }

    private static BigDecimal sumByPrefix(Map<String, BigDecimal> items, String prefix) {
        return items.entrySet().stream().filter(e -> e.getKey().startsWith(prefix))
                .map(Map.Entry::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal percent(BigDecimal part, BigDecimal total) {
        return part.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
    }

    private static void assertAmount(long expected, BigDecimal actual) {
        assertEquals(0, BigDecimal.valueOf(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    private static void assertAmount(long expected, JsonNode actual) {
        assertEquals(0, BigDecimal.valueOf(expected).compareTo(actual.decimalValue()),
                "expected " + expected + " but was " + actual);
    }

    @Test
    @DisplayName("Receivables come from the configured receivable account (1.1.10 Piutang Usaha), not from 1.1.04 Saldo Tokopedia")
    void shouldReportConfiguredReceivableAccountOnly() throws Exception {
        assertEquals(List.of(), transactionRepository.findByTransactionDateBetweenOrderByTransactionDateDesc(
                LocalDate.of(RECEIVABLE_YEAR, 1, 1), LocalDate.of(RECEIVABLE_YEAR, 12, 31)),
                "Another test posted into reserved seller receivable year " + RECEIVABLE_YEAR);
        String before = (RECEIVABLE_YEAR - 1) + "-12-31";
        String after = RECEIVABLE_YEAR + "-01-31";
        JsonNode receivablesBefore = getApi("/api/analysis/receivables?asOfDate=" + before).get("data");
        JsonNode snapshotBefore = getApi("/api/analysis/snapshot?month=" + (RECEIVABLE_YEAR - 1) + "-12").get("data");

        postJournal("Saldo Tokopedia dicairkan belum", "1.1.04", "4.1.01", "3000000");
        postJournal("Penjualan offline tempo", "1.1.10", "4.1.06", "2000000");

        JsonNode receivables = getApi("/api/analysis/receivables?asOfDate=" + after).get("data");
        JsonNode snapshot = getApi("/api/analysis/snapshot?month=" + RECEIVABLE_YEAR + "-01").get("data");
        List<String> accounts = new ArrayList<>();
        receivables.get("items").forEach(i -> accounts.add(i.get("accountCode").asText() + " " + i.get("accountName").asText()));
        assertEquals(List.of("1.1.10 Piutang Usaha"), accounts);
        assertAmount(2_000_000L, receivables.get("totalBalance").decimalValue()
                .subtract(receivablesBefore.get("totalBalance").decimalValue()));
        assertAmount(2_000_000L, snapshot.get("receivablesBalance").decimalValue()
                .subtract(snapshotBefore.get("receivablesBalance").decimalValue()));
        assertEquals(0, receivables.get("totalBalance").decimalValue()
                .compareTo(snapshot.get("receivablesBalance").decimalValue()));
    }

    private void postJournal(String description, String debitCode, String creditCode, String amount) {
        BigDecimal value = new BigDecimal(amount);
        var created = transactionApiService.createJournalEntry(new JournalEntryRequest(
                LocalDate.of(RECEIVABLE_YEAR, 1, 15), description, null, List.of(
                        new JournalEntryRequest.JournalLineRequest(accountId(debitCode), value, BigDecimal.ZERO),
                        new JournalEntryRequest.JournalLineRequest(accountId(creditCode), BigDecimal.ZERO, value))),
                "admin");
        transactionService.post(created.transactionId(), "admin");
    }

    private UUID accountId(String code) {
        return accountRepository.findByAccountCode(code)
                .orElseThrow(() -> new IllegalStateException("Seeded account missing: " + code)).getId();
    }

    private JsonNode getApi(String path) throws Exception {
        APIResponse response = apiContext.get(path,
                RequestOptions.create().setHeader("Authorization", "Bearer " + accessToken));
        assertEquals(200, response.status(), path + ": " + response.text());
        return objectMapper.readTree(response.text());
    }

    private static String formatCurrency(BigDecimal amount) {
        return String.format(ID, "Rp %,.0f", amount);
    }

    private String authenticateViaDeviceFlow() {
        APIResponse codeResponse = apiContext.post("/api/device/code", RequestOptions.create()
                .setHeader("Content-Type", "application/json")
                .setData(Map.of("clientId", "seller-analysis-test")));
        assertEquals(200, codeResponse.status(), codeResponse.text());
        JsonNode codeData = readTree(codeResponse.text());

        loginAsAdmin();
        navigateTo("/device?code=" + codeData.get("userCode").asText());
        waitForPageLoad();
        page.locator("input[name='deviceName']").fill("Seller Analysis Test");
        page.locator("button[type='submit']:has-text('Otorisasi Perangkat')").click();
        waitForPageLoad();

        Map<String, String> tokenRequest = Map.of("deviceCode", codeData.get("deviceCode").asText());
        AtomicReference<String> tokenRef = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofSeconds(2)).until(() -> {
            APIResponse tokenResponse = apiContext.post("/api/device/token", RequestOptions.create()
                    .setHeader("Content-Type", "application/json")
                    .setData(tokenRequest));
            if (tokenResponse.ok()) {
                tokenRef.set(readTree(tokenResponse.text()).get("accessToken").asText());
                return true;
            }
            return false;
        });
        return tokenRef.get();
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid JSON: " + json, e);
        }
    }
}
