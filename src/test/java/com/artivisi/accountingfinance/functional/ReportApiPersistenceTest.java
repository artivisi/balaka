package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.AssetCategory;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.Client;
import com.artivisi.accountingfinance.entity.FixedAsset;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.Project;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.AssetCategoryRepository;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.ClientRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.ProjectRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.service.CompanyConfigService;
import com.artivisi.accountingfinance.service.FixedAssetService;
import com.artivisi.accountingfinance.service.ReportService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Report JSON API and print pages, computed from journals this class posts into {@link #YEAR}
 * (reserved for this class) for its own client and project.
 *
 * Posted in Q1 {@link #YEAR}:
 * - 01-10 Setoran Modal: Dr 1.1.02 / Cr 3.1.01 200,000,000
 * - 02-05 Pendapatan Jasa + PPN (project): Dr 1.1.02 111M / Cr 4.1.01 100M / Cr 2.1.03 11M
 * - 02-20 Pembayaran Jasa dengan PPh 23 (project): Dr 5.1.20 20M / Cr 1.1.02 19.6M / Cr 2.1.21 0.4M
 * - 03-10 Pembelian dengan PPN: Dr 5.1.21 10M / Dr 1.1.25 1.1M / Cr 1.1.02 11.1M
 * - 03-25 Setor PPh 23: Dr 2.1.21 0.3M / Cr 1.1.02 0.3M
 * Revenue 100M, expense 30M, net income 70M, cash +280M (operating 80M, financing 200M).
 * One more project expense (5.1.20 30M) is dated {@link #PAST_PROJECT_COST_DATE}: cost overrun
 * sums project costs up to today, so it ignores the future-dated journals.
 */
@DisplayName("Report Controller - API and Print Tests")
@Import(ServiceTestDataInitializer.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportApiPersistenceTest extends PlaywrightTestBase {

    private static final int YEAR = 2074;
    private static final String START = YEAR + "-01-01";
    private static final String END = YEAR + "-03-31";
    private static final String BEFORE = (YEAR - 1) + "-12-31";
    private static final LocalDate PAST_PROJECT_COST_DATE = LocalDate.of(2003, 6, 15);

    @Autowired private TransactionRepository transactionRepository;
    @Autowired private JournalEntryRepository journalEntryRepository;
    @Autowired private JournalTemplateRepository templateRepository;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private AssetCategoryRepository assetCategoryRepository;
    @Autowired private FixedAssetService fixedAssetService;
    @Autowired private ReportService reportService;
    @Autowired private CompanyConfigService companyConfigService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Client client;
    private Project project;
    private String assetCode;
    private String assetName;

    @BeforeAll
    void postReportData() {
        assertThat(transactionRepository.findPostedTransactionsBetweenDates(
                LocalDate.of(YEAR, 1, 1), LocalDate.of(YEAR, 12, 31)))
                .as("Another test posted into reserved report year " + YEAR)
                .isEmpty();

        String runId = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        client = new Client();
        client.setCode("RPT-CL-" + runId);
        client.setName("Klien Laporan " + runId);
        client = clientRepository.save(client);

        project = new Project();
        project.setCode("RPT-PRJ-" + runId);
        project.setName("Proyek Laporan " + runId);
        project.setClient(client);
        project.setBudgetAmount(new BigDecimal("50000000"));
        project.setContractValue(new BigDecimal("150000000"));
        project = projectRepository.save(project);

        post(LocalDate.of(YEAR, 1, 10), "Setoran Modal", null,
                line("1.1.02", "200000000", "0"), line("3.1.01", "0", "200000000"));
        post(LocalDate.of(YEAR, 2, 5), "Pendapatan Jasa + PPN", project,
                line("1.1.02", "111000000", "0"), line("4.1.01", "0", "100000000"), line("2.1.03", "0", "11000000"));
        post(LocalDate.of(YEAR, 2, 20), "Pembayaran Jasa dengan PPh 23", project,
                line("5.1.20", "20000000", "0"), line("1.1.02", "0", "19600000"), line("2.1.21", "0", "400000"));
        post(LocalDate.of(YEAR, 3, 10), "Pembelian dengan PPN", null,
                line("5.1.21", "10000000", "0"), line("1.1.25", "1100000", "0"), line("1.1.02", "0", "11100000"));
        post(LocalDate.of(YEAR, 3, 25), "Setor PPh 23", null,
                line("2.1.21", "300000", "0"), line("1.1.02", "0", "300000"));
        post(PAST_PROJECT_COST_DATE, "Bayar Beban Cloud & Server", project,
                line("5.1.20", "30000000", "0"), line("1.1.02", "0", "30000000"));

        AssetCategory komputer = assetCategoryRepository.findByCode("KOMPUTER")
                .orElseThrow(() -> new IllegalStateException("Seeded asset category missing: KOMPUTER"));
        assetCode = "RPT-API-" + runId;
        assetName = "Server Laporan " + runId;
        FixedAsset asset = new FixedAsset();
        asset.setAssetCode(assetCode);
        asset.setName(assetName);
        asset.setCategory(komputer);
        asset.setPurchaseDate(LocalDate.of(YEAR, 1, 15));
        asset.setPurchaseCost(new BigDecimal("24000000"));
        asset.setFundingAccount(account("1.1.02"));
        fixedAssetService.create(asset);
    }

    @BeforeEach
    void login() {
        loginAsAdmin();
    }

    // ==================== FINANCIAL REPORT API ====================

    @Test
    @DisplayName("Trial balance API moves each account by exactly the posted amounts and stays balanced")
    void shouldReturnTrialBalance() throws Exception {
        JsonNode before = api("/reports/api/trial-balance?asOfDate=" + BEFORE);
        JsonNode after = api("/reports/api/trial-balance?asOfDate=" + END);

        assertThat(after.get("asOfDate").asText()).isEqualTo(END);
        Map<String, BigDecimal> start = trialBalance(before);
        Map<String, BigDecimal> end = trialBalance(after);
        assertDelta(start, end, "1.1.02", "280000000");
        assertDelta(start, end, "1.1.25", "1100000");
        assertDelta(start, end, "2.1.03", "-11000000");
        assertDelta(start, end, "2.1.21", "-100000");
        assertDelta(start, end, "3.1.01", "-200000000");
        assertDelta(start, end, "4.1.01", "-100000000");
        assertDelta(start, end, "5.1.20", "20000000");
        assertDelta(start, end, "5.1.21", "10000000");
        assertThat(after.get("totalDebit").decimalValue()).isEqualByComparingTo(after.get("totalCredit").decimalValue());
    }

    @Test
    @DisplayName("Income statement API returns the period's revenue and expense accounts and exact totals")
    void shouldReturnIncomeStatement() throws Exception {
        JsonNode report = api("/reports/api/income-statement?startDate=" + START + "&endDate=" + END);

        assertThat(amounts(report.get("revenueItems"))).containsExactlyEntriesOf(Map.of("4.1.01", new BigDecimal("100000000.00")));
        assertThat(amounts(report.get("expenseItems"))).containsOnly(
                Map.entry("5.1.20", new BigDecimal("20000000.00")), Map.entry("5.1.21", new BigDecimal("10000000.00")));
        assertThat(report.get("totalRevenue").decimalValue()).isEqualByComparingTo("100000000");
        assertThat(report.get("totalExpense").decimalValue()).isEqualByComparingTo("30000000");
        assertThat(report.get("netIncome").decimalValue()).isEqualByComparingTo("70000000");

        JsonNode february = api("/reports/api/income-statement?startDate=" + YEAR + "-02-01&endDate=" + YEAR + "-02-28");
        assertThat(february.get("totalRevenue").decimalValue()).isEqualByComparingTo("100000000");
        assertThat(february.get("totalExpense").decimalValue()).isEqualByComparingTo("20000000");
    }

    @Test
    @DisplayName("Balance sheet API grows assets, liabilities and equity by the posted amounts; current-year earnings are 70M")
    void shouldReturnBalanceSheet() throws Exception {
        JsonNode before = api("/reports/api/balance-sheet?asOfDate=" + BEFORE);
        JsonNode after = api("/reports/api/balance-sheet?asOfDate=" + END);

        assertThat(diff(before, after, "totalAssets")).isEqualByComparingTo("281100000");
        assertThat(diff(before, after, "totalLiabilities")).isEqualByComparingTo("11100000");
        assertThat(diff(before, after, "totalEquity")).isEqualByComparingTo("270000000");
        assertThat(after.get("currentYearEarnings").decimalValue()).isEqualByComparingTo("70000000");
        assertThat(after.get("totalAssets").decimalValue()).isEqualByComparingTo(
                after.get("totalLiabilities").decimalValue().add(after.get("totalEquity").decimalValue()));
    }

    @Test
    @DisplayName("Cash flow statement classifies the period's cash movements by template and activity")
    void shouldGenerateCashFlow() {
        ReportService.CashFlowReport report = reportService.generateCashFlowStatement(LocalDate.parse(START), LocalDate.parse(END));

        assertThat(report.operatingTotal()).isEqualByComparingTo("80000000");
        assertThat(report.investingTotal()).isEqualByComparingTo("0");
        assertThat(report.financingTotal()).isEqualByComparingTo("200000000");
        assertThat(report.netCashChange()).isEqualByComparingTo("280000000");
        assertThat(report.endingCashBalance().subtract(report.beginningCashBalance())).isEqualByComparingTo("280000000");
        assertThat(report.operatingItems()).extracting(ReportService.CashFlowItem::description)
                .containsExactly("Pendapatan Jasa + PPN", "Pembayaran Jasa dengan PPh 23", "Pembelian dengan PPN", "Setor PPh 23");
        assertThat(report.operatingItems()).extracting(ReportService.CashFlowItem::amount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("111000000"), new BigDecimal("-19600000"),
                        new BigDecimal("-11100000"), new BigDecimal("-300000"));
        assertThat(report.financingItems()).extracting(ReportService.CashFlowItem::description).containsExactly("Setoran Modal");
        assertThat(report.investingItems()).isEmpty();
    }

    // ==================== TAX REPORT API ====================

    @Test
    @DisplayName("PPN summary API nets output VAT 11M against input VAT 1.1M")
    void shouldReturnPpnSummary() throws Exception {
        JsonNode report = api("/reports/api/ppn-summary?startDate=" + START + "&endDate=" + END);

        assertThat(report.get("ppnKeluaran").decimalValue()).isEqualByComparingTo("11000000");
        assertThat(report.get("ppnMasukan").decimalValue()).isEqualByComparingTo("1100000");
        assertThat(report.get("netPPN").decimalValue()).isEqualByComparingTo("9900000");
    }

    @Test
    @DisplayName("PPh 23 API reports 400k withheld, 300k deposited and 100k outstanding")
    void shouldReturnPph23Withholding() throws Exception {
        JsonNode report = api("/reports/api/pph23-withholding?startDate=" + START + "&endDate=" + END);

        assertThat(report.get("totalWithheld").decimalValue()).isEqualByComparingTo("400000");
        assertThat(report.get("totalDeposited").decimalValue()).isEqualByComparingTo("300000");
        assertThat(report.get("balance").decimalValue()).isEqualByComparingTo("100000");
    }

    @Test
    @DisplayName("Tax summary API lists each tax account's period debit, credit and balance")
    void shouldReturnTaxSummary() throws Exception {
        JsonNode report = api("/reports/api/tax-summary?startDate=" + START + "&endDate=" + END);

        Map<String, JsonNode> items = new HashMap<>();
        report.get("items").forEach(item -> items.put(item.get("label").asText(), item));
        assertThat(items).containsOnlyKeys("PPN Masukan", "Hutang PPN", "Hutang PPh 21", "Hutang PPh 23",
                "Hutang PPh 4(2)", "Hutang PPh 25", "Hutang PPh 29");
        assertTaxItem(items.get("PPN Masukan"), "1.1.25", "1100000", "0", "1100000");
        assertTaxItem(items.get("Hutang PPN"), "2.1.03", "0", "11000000", "11000000");
        assertTaxItem(items.get("Hutang PPh 23"), "2.1.21", "300000", "400000", "100000");
        assertTaxItem(items.get("Hutang PPh 21"), "2.1.20", "0", "0", "0");
        assertThat(report.get("totalBalance").decimalValue()).isEqualByComparingTo("12200000");
    }

    // ==================== ASSET, PROFITABILITY AND CLOSING API ====================

    @Test
    @DisplayName("Depreciation API lists the asset bought in the year with 12 months of straight-line depreciation")
    void shouldReturnDepreciationReport() throws Exception {
        JsonNode report = api("/reports/api/depreciation?year=" + YEAR);

        assertThat(report.get("year").asInt()).isEqualTo(YEAR);
        JsonNode item = null;
        for (JsonNode candidate : report.get("items")) {
            if (candidate.get("assetCode").asText().equals(assetCode)) {
                item = candidate;
            }
        }
        assertThat(item).as("Asset " + assetCode + " missing from depreciation report").isNotNull();
        assertThat(item.get("assetName").asText()).isEqualTo(assetName);
        assertThat(item.get("purchaseDate").asText()).isEqualTo(YEAR + "-01-15");
        assertThat(item.get("purchaseCost").decimalValue()).isEqualByComparingTo("24000000");
        assertThat(item.get("usefulLifeYears").asInt()).isEqualTo(4);
        assertThat(item.get("depreciationMethod").asText()).isEqualTo("Garis Lurus");
        assertThat(item.get("depreciationThisYear").decimalValue()).isEqualByComparingTo("6000000");
        assertThat(item.get("accumulatedDepreciation").decimalValue()).isEqualByComparingTo("0");
        assertThat(item.get("bookValue").decimalValue()).isEqualByComparingTo("24000000");
    }

    @Test
    @DisplayName("Project profitability API attributes only the project's journals: revenue 100M, cost 20M, margin 80%")
    void shouldReturnProjectProfitability() throws Exception {
        JsonNode report = api("/reports/api/project-profitability?projectId=" + project.getId()
                + "&startDate=" + YEAR + "-01-01&endDate=" + YEAR + "-12-31");

        assertThat(report.get("project").get("code").asText()).isEqualTo(project.getCode());
        assertThat(profitabilityLines(report.get("revenueItems"))).containsOnly(Map.entry("4.1.01", new BigDecimal("100000000.00")));
        assertThat(profitabilityLines(report.get("expenseItems"))).containsOnly(Map.entry("5.1.20", new BigDecimal("20000000.00")));
        assertThat(report.get("totalRevenue").decimalValue()).isEqualByComparingTo("100000000");
        assertThat(report.get("totalExpense").decimalValue()).isEqualByComparingTo("20000000");
        assertThat(report.get("grossProfit").decimalValue()).isEqualByComparingTo("80000000");
        assertThat(report.get("profitMargin").decimalValue()).isEqualByComparingTo("80.00");
    }

    @Test
    @DisplayName("Client profitability API sums the client's projects")
    void shouldReturnClientProfitability() throws Exception {
        JsonNode report = api("/reports/api/client-profitability?clientId=" + client.getId()
                + "&startDate=" + YEAR + "-01-01&endDate=" + YEAR + "-12-31");

        assertThat(report.get("client").get("code").asText()).isEqualTo(client.getCode());
        assertThat(report.get("projects")).hasSize(1);
        JsonNode summary = report.get("projects").get(0);
        assertThat(summary.get("project").get("code").asText()).isEqualTo(project.getCode());
        assertThat(summary.get("revenue").decimalValue()).isEqualByComparingTo("100000000");
        assertThat(summary.get("profit").decimalValue()).isEqualByComparingTo("80000000");
        assertThat(report.get("totalRevenue").decimalValue()).isEqualByComparingTo("100000000");
        assertThat(report.get("totalProfit").decimalValue()).isEqualByComparingTo("80000000");
        assertThat(report.get("overallMargin").decimalValue()).isEqualByComparingTo("80.00");
    }

    @Test
    @DisplayName("Cost overrun API compares project costs to date with the budget: 30M of 50M, no progress, HIGH risk")
    void shouldReturnCostOverrun() throws Exception {
        JsonNode report = api("/reports/api/cost-overrun?projectId=" + project.getId());

        assertThat(report.get("budget").decimalValue()).isEqualByComparingTo("50000000");
        assertThat(report.get("spent").decimalValue()).isEqualByComparingTo("30000000");
        assertThat(report.get("progressPercent").asInt()).isZero();
        assertThat(report.get("budgetSpentPercent").decimalValue()).isEqualByComparingTo("60.00");
        assertThat(report.get("projectedFinalCost").decimalValue()).isEqualByComparingTo("30000000");
        assertThat(report.get("projectedOverrun").decimalValue()).isEqualByComparingTo("-20000000");
        assertThat(report.get("projectedLoss").decimalValue()).isEqualByComparingTo("-120000000");
        assertThat(report.get("riskLevel").asText()).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("Fiscal closing preview API lists the three closing journals for the year")
    void shouldReturnFiscalClosingPreview() throws Exception {
        JsonNode preview = api("/reports/api/fiscal-closing/preview?year=" + YEAR);

        assertThat(preview.get("totalRevenue").decimalValue()).isEqualByComparingTo("100000000");
        assertThat(preview.get("totalExpense").decimalValue()).isEqualByComparingTo("30000000");
        assertThat(preview.get("netIncome").decimalValue()).isEqualByComparingTo("70000000");
        assertThat(preview.get("alreadyClosed").asBoolean()).isFalse();
        JsonNode entries = preview.get("entries");
        assertThat(entries).hasSize(3);
        assertThat(entries.get(0).get("referenceNumber").asText()).isEqualTo("CLOSING-" + YEAR + "-01");
        assertThat(entries.get(0).get("date").asText()).isEqualTo(YEAR + "-12-31");
        assertThat(closingLines(entries.get(0))).containsExactly("4.1.01 D 100000000", "3.2.02 C 100000000");
        assertThat(closingLines(entries.get(1))).containsExactly("5.1.20 C 20000000", "5.1.21 C 10000000", "3.2.02 D 30000000");
        assertThat(closingLines(entries.get(2))).containsExactly("3.2.02 D 70000000", "3.2.01 C 70000000");
    }

    // ==================== PRINT PAGES ====================

    @Test
    @DisplayName("Trial balance print renders every account balance and the totals of the report")
    void shouldPrintTrialBalance() {
        ReportService.TrialBalanceReport report = reportService.generateTrialBalance(LocalDate.parse(END));
        openPrint("/reports/trial-balance/print?asOfDate=" + END);

        assertThat(page.locator(".report-period")).hasText("Per 31 Maret " + YEAR);
        assertThat(page.locator("tbody tr:has(td.account-code)")).hasCount(report.items().size());
        for (String code : new String[]{"1.1.02", "3.1.01", "4.1.01", "5.1.20"}) {
            ReportService.TrialBalanceItem item = report.items().stream()
                    .filter(i -> i.account().getAccountCode().equals(code)).findFirst()
                    .orElseThrow(() -> new AssertionError("Account " + code + " missing from trial balance"));
            Locator row = page.locator("tbody tr").filter(new Locator.FilterOptions()
                    .setHas(page.locator("td.account-code").getByText(code, new Locator.GetByTextOptions().setExact(true))));
            assertThat(row.locator("td").nth(2)).hasText(printAmountOrDash(item.debitBalance()));
            assertThat(row.locator("td").nth(3)).hasText(printAmountOrDash(item.creditBalance()));
        }
        assertThat(page.locator(".total-row td").nth(1)).hasText(printAmount(report.totalDebit()));
        assertThat(page.locator(".total-row td").nth(2)).hasText(printAmount(report.totalCredit()));
        assertThat(page.locator("body")).containsText("Balance - Total Debit = Total Kredit");
    }

    @Test
    @DisplayName("Balance sheet print renders the report totals and 70M current-year earnings")
    void shouldPrintBalanceSheet() {
        ReportService.BalanceSheetReport report = reportService.generateBalanceSheet(LocalDate.parse(END));
        openPrint("/reports/balance-sheet/print?asOfDate=" + END);

        assertThat(page.locator(".report-period")).hasText("Per 31 Maret " + YEAR);
        assertThat(page.locator(".earnings-row .item-value")).hasText("70.000.000");
        assertThat(page.locator(".total-row.assets")).containsText(printAmount(report.totalAssets()));
        assertThat(page.locator(".subtotal-row").filter(new Locator.FilterOptions().setHasText("Total Liabilitas")))
                .containsText(printAmount(report.totalLiabilities()));
        assertThat(page.locator(".subtotal-row").filter(new Locator.FilterOptions().setHasText("Total Ekuitas")))
                .containsText(printAmount(report.totalEquity()));
        assertThat(page.locator(".total-row.liabilities"))
                .containsText(printAmount(report.totalLiabilities().add(report.totalEquity())));
        assertThat(itemValue(account("1.1.02").getAccountName())).hasText(printAmount(balance(report, "1.1.02")));
        assertThat(page.locator("body")).containsText("Neraca Balance - Total Aset = Total Liabilitas + Ekuitas");
    }

    @Test
    @DisplayName("Income statement print shows the period's revenue, expenses and net income")
    void shouldPrintIncomeStatement() {
        openPrint("/reports/income-statement/print?startDate=" + START + "&endDate=" + END);

        assertThat(page.locator(".report-title")).hasText("LABA RUGI");
        assertThat(page.locator(".report-period")).hasText("Periode: 1 Januari " + YEAR + " - 31 Maret " + YEAR);
        assertThat(itemValue(account("4.1.01").getAccountName())).hasText("100.000.000");
        assertThat(itemValue(account("5.1.20").getAccountName())).hasText("(20.000.000)");
        assertThat(itemValue(account("5.1.21").getAccountName())).hasText("(10.000.000)");
        assertThat(page.locator(".total-row.revenue")).containsText("100.000.000");
        assertThat(page.locator(".total-row.expense")).containsText("(30.000.000)");
        assertThat(page.locator(".net-income-title")).hasText("LABA BERSIH");
        assertThat(page.locator(".net-income-value")).hasText("70.000.000");
    }

    @Test
    @DisplayName("Cash flow print shows each activity's items and totals and the ending cash balance")
    void shouldPrintCashFlow() {
        ReportService.CashFlowReport report = reportService.generateCashFlowStatement(LocalDate.parse(START), LocalDate.parse(END));
        openPrint("/reports/cash-flow/print?startDate=" + START + "&endDate=" + END);

        assertThat(itemValue("Pendapatan Jasa + PPN")).hasText("111.000.000");
        assertThat(itemValue("Pembayaran Jasa dengan PPh 23")).hasText("(19.600.000)");
        assertThat(itemValue("Setor PPh 23")).hasText("(300.000)");
        assertThat(itemValue("Setoran Modal")).hasText("200.000.000");
        assertThat(page.locator(".total-row.operating")).containsText("80.000.000");
        assertThat(page.locator(".total-row.investing")).containsText("0");
        assertThat(page.locator(".total-row.financing")).containsText("200.000.000");
        assertThat(page.locator(".summary-value")).hasText("Rp " + printAmount(report.endingCashBalance()));
    }

    @Test
    @DisplayName("PPN summary print shows output and input VAT and the 9.9M underpayment")
    void shouldPrintPpnSummary() {
        openPrint("/reports/ppn-summary/print?startDate=" + START + "&endDate=" + END);

        assertThat(page.locator(".box-value").nth(0)).hasText("11.000.000");
        assertThat(page.locator(".box-value").nth(1)).hasText("(1.100.000)");
        assertThat(page.locator(".result-title")).hasText("PPN KURANG BAYAR");
        assertThat(page.locator(".result-value")).hasText("9.900.000");
    }

    @Test
    @DisplayName("PPh 23 print shows withheld, deposited and the outstanding balance")
    void shouldPrintPph23Withholding() {
        openPrint("/reports/pph23-withholding/print?startDate=" + START + "&endDate=" + END);

        assertThat(page.locator(".box-value").nth(0)).hasText("400.000");
        assertThat(page.locator(".box-value").nth(1)).hasText("(300.000)");
        assertThat(page.locator(".result-title")).hasText("SALDO HUTANG PPh 23");
        assertThat(page.locator(".result-value")).hasText("100.000");
    }

    @Test
    @DisplayName("Depreciation print lists the asset with its cost, life, method and yearly depreciation")
    void shouldPrintDepreciation() {
        openPrint("/reports/depreciation/print?year=" + YEAR);

        assertThat(page.locator(".report-subtitle").first()).hasText("Tahun Pajak " + YEAR);
        Locator cells = page.locator("tbody tr").filter(new Locator.FilterOptions().setHasText(assetName)).locator("td");
        assertThat(cells.nth(2)).hasText("Peralatan Komputer");
        assertThat(cells.nth(3)).hasText("15/01/" + YEAR);
        assertThat(cells.nth(4)).hasText("24,000,000");
        assertThat(cells.nth(5)).hasText("4 th");
        assertThat(cells.nth(6)).hasText("Garis Lurus");
        assertThat(cells.nth(7)).hasText("6,000,000");
        assertThat(cells.nth(8)).hasText("0");
        assertThat(cells.nth(9)).hasText("24,000,000");
    }

    // ==================== HELPERS ====================

    private JsonNode api(String path) throws Exception {
        APIResponse response = page.request().get(baseUrl() + path,
                RequestOptions.create().setHeader("Accept", "application/json"));
        assertThat(response.status()).as(path).isEqualTo(200);
        assertThat(response.headers().get("content-type")).contains("application/json");
        return objectMapper.readTree(response.text());
    }

    private void openPrint(String path) {
        navigateTo(path);
        waitForPageLoad();
        assertThat(page.locator(".company-name")).hasText(companyConfigService.getConfig().getCompanyName());
    }

    private Locator itemValue(String itemName) {
        return page.locator(".item-row").filter(new Locator.FilterOptions()
                .setHas(page.locator(".item-name").getByText(itemName, new Locator.GetByTextOptions().setExact(true))))
                .locator(".item-value");
    }

    private static Map<String, BigDecimal> trialBalance(JsonNode report) {
        Map<String, BigDecimal> balances = new HashMap<>();
        report.get("items").forEach(item -> balances.put(item.get("account").get("accountCode").asText(),
                item.get("debitBalance").decimalValue().subtract(item.get("creditBalance").decimalValue())));
        return balances;
    }

    private static void assertDelta(Map<String, BigDecimal> before, Map<String, BigDecimal> after, String code, String expected) {
        BigDecimal delta = after.getOrDefault(code, BigDecimal.ZERO).subtract(before.getOrDefault(code, BigDecimal.ZERO));
        assertThat(delta).as("Trial balance movement of " + code).isEqualByComparingTo(expected);
    }

    private static BigDecimal diff(JsonNode before, JsonNode after, String field) {
        return after.get(field).decimalValue().subtract(before.get(field).decimalValue());
    }

    private static Map<String, BigDecimal> amounts(JsonNode items) {
        Map<String, BigDecimal> result = new HashMap<>();
        items.forEach(item -> result.put(item.get("account").get("accountCode").asText(),
                item.get("balance").decimalValue().setScale(2)));
        return result;
    }

    private static Map<String, BigDecimal> profitabilityLines(JsonNode items) {
        Map<String, BigDecimal> result = new HashMap<>();
        items.forEach(item -> result.put(item.get("account").get("accountCode").asText(),
                item.get("amount").decimalValue().setScale(2)));
        return result;
    }

    private static java.util.List<String> closingLines(JsonNode entry) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        entry.get("lines").forEach(line -> {
            BigDecimal debit = line.get("debit").decimalValue();
            lines.add(line.get("accountCode").asText() + (debit.signum() > 0
                    ? " D " + debit.toBigInteger()
                    : " C " + line.get("credit").decimalValue().toBigInteger()));
        });
        return lines;
    }

    private static void assertTaxItem(JsonNode item, String accountCode, String debit, String credit, String balance) {
        assertThat(item.get("account").get("accountCode").asText()).isEqualTo(accountCode);
        assertThat(item.get("debit").decimalValue()).isEqualByComparingTo(debit);
        assertThat(item.get("credit").decimalValue()).isEqualByComparingTo(credit);
        assertThat(item.get("balance").decimalValue()).isEqualByComparingTo(balance);
    }

    private static BigDecimal balance(ReportService.BalanceSheetReport report, String code) {
        return report.assetItems().stream()
                .filter(i -> i.account().getAccountCode().equals(code))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Account " + code + " missing from balance sheet"))
                .balance();
    }

    private static String printAmount(BigDecimal value) {
        return String.format(java.util.Locale.ROOT, "%,d", value.setScale(0, java.math.RoundingMode.HALF_UP).longValueExact())
                .replace(',', '.');
    }

    private static String printAmountOrDash(BigDecimal value) {
        return value.signum() == 0 ? "-" : printAmount(value);
    }

    private record Line(String accountCode, BigDecimal debit, BigDecimal credit) {}

    private static Line line(String accountCode, String debit, String credit) {
        return new Line(accountCode, new BigDecimal(debit), new BigDecimal(credit));
    }

    private void post(LocalDate date, String templateName, Project journalProject, Line... lines) {
        String number = "RPA-" + UUID.randomUUID().toString().substring(0, 8);
        Transaction tx = new Transaction();
        tx.setTransactionNumber(number);
        tx.setTransactionDate(date);
        tx.setDescription(templateName + " " + number);
        tx.setAmount(lines[0].debit());
        tx.setJournalTemplate(templateRepository.findByTemplateNameAndIsCurrentVersionTrue(templateName)
                .orElseThrow(() -> new IllegalStateException("Seeded template missing: " + templateName)));
        tx.setProject(journalProject);
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
            entry.setAccount(account(l.accountCode()));
            entry.setProject(journalProject);
            entry.setDebitAmount(l.debit());
            entry.setCreditAmount(l.credit());
            entry.setPostedAt(saved.getPostedAt());
            entry.setCreatedBy("admin");
            journalEntryRepository.save(entry);
        }
    }

    private ChartOfAccount account(String code) {
        return accountRepository.findByAccountCode(code)
                .orElseThrow(() -> new IllegalStateException("Seeded account missing: " + code));
    }
}
