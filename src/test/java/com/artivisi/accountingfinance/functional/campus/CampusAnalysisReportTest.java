package com.artivisi.accountingfinance.functional.campus;

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
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Campus analysis report: reads the July 2024 figures seeded from campus/financial-*.csv through the analysis API
 * (no campus test class posts into July 2024), computes the education KPIs, publishes the report and checks it on
 * the report list and detail pages.
 *
 * Expected July 2024: revenue 720,000,000 (SPP 540,000,000 + uang pangkal 180,000,000), expense 253,000,000
 * (dosen 150,000,000, karyawan 60,000,000, listrik 25,000,000, beasiswa 18,000,000), surplus 467,000,000,
 * cash 1,467,000,000.
 */
@DisplayName("Analysis Report - Campus")
@Import(CampusTestDataInitializer.class)
class CampusAnalysisReportTest extends PlaywrightTestBase {

    private static final Locale ID = Locale.of("id", "ID");

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
    @DisplayName("Should compute education KPIs from the July 2024 figures, publish them and show them on the report pages")
    void shouldPublishEducationKpisFromJuly2024() throws Exception {
        JsonNode company = getApi("/api/analysis/company").get("data");
        assertEquals("STMIK Tazkia", company.get("companyName").asText());
        assertEquals("campus", company.get("industry").asText());

        JsonNode snapshot = getApi("/api/analysis/snapshot?month=2024-07").get("data");
        assertAmount(720_000_000L, snapshot.get("revenue"));
        assertAmount(253_000_000L, snapshot.get("expense"));
        assertAmount(467_000_000L, snapshot.get("netProfit"));
        assertEquals(0, new BigDecimal("64.86").compareTo(snapshot.get("profitMargin").decimalValue()));
        assertAmount(1_467_000_000L, snapshot.get("cashBalance"));

        JsonNode income = getApi("/api/analysis/income-statement?startDate=2024-07-01&endDate=2024-07-31").get("data");
        Map<String, BigDecimal> revenueItems = items(income.get("revenueItems"));
        Map<String, BigDecimal> expenseItems = items(income.get("expenseItems"));
        assertEquals(Map.of("4.1.01", 540_000_000L, "4.1.02", 180_000_000L), longs(revenueItems));
        assertEquals(Map.of("5.1.01", 150_000_000L, "5.2.01", 60_000_000L, "5.2.11", 25_000_000L,
                "5.3.01", 18_000_000L), longs(expenseItems));
        BigDecimal totalRevenue = income.get("totalRevenue").decimalValue();
        BigDecimal netProfit = income.get("netIncome").decimalValue();
        assertAmount(720_000_000L, income.get("totalRevenue"));
        assertAmount(467_000_000L, income.get("netIncome"));

        BigDecimal sppRevenue = revenueItems.get("4.1.01");
        BigDecimal otherRevenue = totalRevenue.subtract(sppRevenue);
        BigDecimal facultyCost = sumByPrefix(expenseItems, "5.1");
        BigDecimal adminCost = sumByPrefix(expenseItems, "5.2");
        BigDecimal scholarshipCost = sumByPrefix(expenseItems, "5.3");
        BigDecimal facultyCostRatio = percent(facultyCost, totalRevenue);
        BigDecimal scholarshipRatio = percent(scholarshipCost, totalRevenue);
        BigDecimal sppConcentration = percent(sppRevenue, totalRevenue);
        BigDecimal operatingMargin = percent(netProfit, totalRevenue);
        assertEquals("20.8|2.5|75.0|64.9", String.join("|", facultyCostRatio.toPlainString(),
                scholarshipRatio.toPlainString(), sppConcentration.toPlainString(), operatingMargin.toPlainString()));

        List<Map<String, String>> metrics = List.of(
                metric("Pendapatan Total", formatCurrency(totalRevenue), "info"),
                metric("Pendapatan SPP", formatCurrency(sppRevenue),
                        sppConcentration.compareTo(BigDecimal.valueOf(80)) < 0 ? "positive" : "warning"),
                metric("Biaya Dosen & Akademik", formatCurrency(facultyCost),
                        facultyCostRatio.compareTo(BigDecimal.valueOf(40)) <= 0 ? "positive" : "warning"),
                metric("Rasio Beasiswa", scholarshipRatio + "%",
                        scholarshipRatio.compareTo(BigDecimal.valueOf(5)) >= 0 ? "positive" : "info"),
                metric("Surplus Operasional", formatCurrency(netProfit),
                        netProfit.signum() > 0 ? "positive" : "negative"),
                metric("Margin Operasional", operatingMargin + "%",
                        operatingMargin.compareTo(BigDecimal.valueOf(10)) >= 0 ? "positive" : "warning"));
        assertEquals(List.of(
                "Pendapatan Total=Rp 720.000.000/info", "Pendapatan SPP=Rp 540.000.000/positive",
                "Biaya Dosen & Akademik=Rp 150.000.000/positive", "Rasio Beasiswa=2.5%/info",
                "Surplus Operasional=Rp 467.000.000/positive", "Margin Operasional=64.9%/positive"),
                metrics.stream().map(m -> m.get("name") + "=" + m.get("value") + "/" + m.get("status")).toList());

        List<Map<String, String>> findings = List.of(
                Map.of("category", "pendapatan", "severity", "info",
                        "description", "Pendapatan SPP " + formatCurrency(sppRevenue) + " (" + sppConcentration
                                + "% dari total). Pendapatan lain " + formatCurrency(otherRevenue)
                                + ". Diversifikasi pendapatan cukup baik."),
                Map.of("category", "biaya-akademik", "severity", "info",
                        "description", "Biaya dosen & akademik " + formatCurrency(facultyCost) + " (" + facultyCostRatio
                                + "% pendapatan). Biaya administrasi " + formatCurrency(adminCost)
                                + ". Benchmark: rasio dosen 30-40% dari pendapatan."),
                Map.of("category", "beasiswa", "severity", "info",
                        "description", "Dana beasiswa " + formatCurrency(scholarshipCost) + " (" + scholarshipRatio
                                + "% dari pendapatan)."));
        List<Map<String, String>> recommendations = List.of(
                Map.of("priority", "medium",
                        "description", "Optimalisasi rasio dosen tetap vs tidak tetap. Biaya akademik saat ini "
                                + formatCurrency(facultyCost) + " (" + facultyCostRatio + "%).",
                        "impact", "Efisiensi biaya akademik tanpa mengurangi kualitas"),
                Map.of("priority", "medium",
                        "description", "Dengan surplus " + formatCurrency(netProfit)
                                + ", alokasikan untuk pengembangan fasilitas lab atau akreditasi program studi.",
                        "impact", "Peningkatan daya saing dan akreditasi"));
        List<Map<String, String>> risks = List.of(
                Map.of("severity", "high",
                        "description", "Penurunan jumlah mahasiswa baru. SPP berkontribusi " + sppConcentration + "% pendapatan.",
                        "mitigation", "Perkuat program studi unggulan dan kerjasama industri."),
                Map.of("severity", "medium",
                        "description", "Regulasi pendidikan dapat memaksa peningkatan biaya. Rasio biaya dosen saat ini "
                                + facultyCostRatio + "%.",
                        "mitigation", "Rencana rekrutmen dosen tetap bertahap."));
        String title = "Review Keuangan Juli 2024 - STMIK Tazkia";
        String summary = "Review keuangan STMIK Tazkia periode Juli 2024. Pendapatan " + formatCurrency(totalRevenue)
                + " (SPP " + sppConcentration + "%). Surplus operasional " + formatCurrency(netProfit)
                + " (margin " + operatingMargin + "%).";
        assertEquals("Review keuangan STMIK Tazkia periode Juli 2024. Pendapatan Rp 720.000.000 (SPP 75.0%). "
                + "Surplus operasional Rp 467.000.000 (margin 64.9%).", summary);

        Map<String, Object> request = new HashMap<>();
        request.put("title", title);
        request.put("reportType", "semester-review");
        request.put("industry", "campus");
        request.put("periodStart", "2024-07-01");
        request.put("periodEnd", "2024-07-31");
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
        assertEquals(String.join("|", title, "semester-review", "campus", "2024-07-01", "2024-07-31", "admin"),
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
        assertThat(card.getByTestId("report-industry")).hasText("Kampus");
        assertThat(card.getByTestId("report-type")).hasText("semester-review");
        assertThat(card).containsText("6 metrik");
        assertThat(card).containsText("3 temuan");
        takeManualScreenshot("analysis-reports/campus-list");

        card.click();
        page.waitForURL("**/analysis-reports/" + reportId);
        waitForPageLoad();
        assertThat(page.getByTestId("detail-title")).hasText(title);
        assertThat(page.getByTestId("detail-industry")).hasText("Kampus");
        assertThat(page.getByTestId("detail-report-type")).hasText("semester-review");
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
        takeManualScreenshot("analysis-reports/campus-detail");
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

    private static void assertAmount(long expected, JsonNode actual) {
        assertEquals(0, BigDecimal.valueOf(expected).compareTo(actual.decimalValue()),
                "expected " + expected + " but was " + actual);
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
                .setData(Map.of("clientId", "campus-analysis-test")));
        assertEquals(200, codeResponse.status(), codeResponse.text());
        JsonNode codeData = readTree(codeResponse.text());

        loginAsAdmin();
        navigateTo("/device?code=" + codeData.get("userCode").asText());
        waitForPageLoad();
        page.locator("input[name='deviceName']").fill("Campus Analysis Test");
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
