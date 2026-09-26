package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.Client;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.Project;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.ClientRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.ProjectRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
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
import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tax report pages (PPN, PPh 23, tax summary) and profitability report pages (project, client,
 * client ranking), computed from journals this class posts into April {@link #YEAR} (reserved for
 * this class) for its own two clients and projects.
 *
 * - 04-03 Pendapatan Jasa + PPN (project A): Dr 1.1.02 55.5M / Cr 4.1.01 50M / Cr 2.1.03 5.5M
 * - 04-12 Pembayaran Jasa dengan PPh 23 (project A): Dr 5.1.20 10M / Cr 1.1.02 9.8M / Cr 2.1.21 0.2M
 * - 04-15 Pendapatan Jasa Training (project B): Dr 1.1.02 20M / Cr 4.1.01 20M
 * - 04-20 Pembelian dengan PPN: Dr 5.1.21 4M / Dr 1.1.25 0.44M / Cr 1.1.02 4.44M
 * - 04-28 Setor PPh 23: Dr 2.1.21 0.2M / Cr 1.1.02 0.2M
 *
 * The financial statements, depreciation, inventory, payroll, data export and audit log pages
 * are verified with exact values by ServiceReportsTest, ReportApiPersistenceTest,
 * InventoryReportControllerFunctionalTest, PayrollControllerFunctionalTest,
 * DataExportControllerFunctionalTest and SettingsControllerFunctionalTest.
 */
@DisplayName("Tax and Profitability Report Pages")
@Import(ServiceTestDataInitializer.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExportReportTest extends PlaywrightTestBase {

    private static final int YEAR = 2075;
    private static final String START = YEAR + "-04-01";
    private static final String END = YEAR + "-04-30";
    private static final String PERIOD = "Periode: 1 April " + YEAR + " - 30 April " + YEAR;

    @Autowired private TransactionRepository transactionRepository;
    @Autowired private JournalEntryRepository journalEntryRepository;
    @Autowired private JournalTemplateRepository templateRepository;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private ProjectRepository projectRepository;

    private Client clientA;
    private Client clientB;
    private Project projectA;
    private Project projectB;

    @BeforeAll
    void postReportData() {
        assertThat(transactionRepository.findPostedTransactionsBetweenDates(
                LocalDate.of(YEAR, 1, 1), LocalDate.of(YEAR, 12, 31)))
                .as("Another test posted into reserved report year " + YEAR)
                .isEmpty();

        String runId = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        clientA = client("EXP-A-" + runId, "Klien Ekspor A " + runId);
        clientB = client("EXP-B-" + runId, "Klien Ekspor B " + runId);
        projectA = project("EXP-PA-" + runId, "Proyek Ekspor A " + runId, clientA, "25000000", "60000000");
        projectB = project("EXP-PB-" + runId, "Proyek Ekspor B " + runId, clientB, null, null);

        post(LocalDate.of(YEAR, 4, 3), "Pendapatan Jasa + PPN", projectA,
                line("1.1.02", "55500000", "0"), line("4.1.01", "0", "50000000"), line("2.1.03", "0", "5500000"));
        post(LocalDate.of(YEAR, 4, 12), "Pembayaran Jasa dengan PPh 23", projectA,
                line("5.1.20", "10000000", "0"), line("1.1.02", "0", "9800000"), line("2.1.21", "0", "200000"));
        post(LocalDate.of(YEAR, 4, 15), "Pendapatan Jasa Training", projectB,
                line("1.1.02", "20000000", "0"), line("4.1.01", "0", "20000000"));
        post(LocalDate.of(YEAR, 4, 20), "Pembelian dengan PPN", null,
                line("5.1.21", "4000000", "0"), line("1.1.25", "440000", "0"), line("1.1.02", "0", "4440000"));
        post(LocalDate.of(YEAR, 4, 28), "Setor PPh 23", null,
                line("2.1.21", "200000", "0"), line("1.1.02", "0", "200000"));
    }

    @BeforeEach
    void login() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("PPN summary page shows output VAT, input VAT and the underpayment for the range chosen in the form")
    void shouldShowPpnSummary() {
        navigateTo("/reports/ppn-summary");
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Ringkasan PPN");
        submitRange("/reports/ppn-summary");

        assertThat(page.locator("#report-period")).hasText(PERIOD);
        assertThat(page.locator("#ppn-keluaran")).hasText("5.500.000");
        assertThat(page.locator("#ppn-masukan")).hasText("(440.000)");
        assertThat(page.locator("#net-ppn")).hasText("5.060.000");
        assertThat(page.locator("#ppn-summary-content")).containsText("PPN KURANG BAYAR");
        assertThat(page.locator("#ppn-message")).hasText("PPN harus disetor ke negara sebelum tanggal jatuh tempo");
        assertThat(page.locator("#btn-print")).hasAttribute("href",
                "/reports/ppn-summary/print?startDate=" + START + "&endDate=" + END);
        takeManualScreenshot("reports-ppn-summary");
    }

    @Test
    @DisplayName("PPh 23 page shows the withheld tax fully deposited")
    void shouldShowPph23Withholding() {
        navigateTo("/reports/pph23-withholding?startDate=" + START + "&endDate=" + END);
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Pemotongan PPh 23");
        assertThat(page.locator("#report-period")).hasText(PERIOD);
        assertThat(page.locator("#total-withheld")).hasText("200.000");
        assertThat(page.locator("#total-deposited")).hasText("(200.000)");
        assertThat(page.locator("#balance")).hasText("0");
        assertThat(page.locator("#pph23-content")).containsText("LUNAS");
        assertThat(page.locator("#pph23-message")).hasText("Semua PPh 23 sudah disetor");

        navigateTo("/reports/pph23-withholding?startDate=" + START + "&endDate=" + YEAR + "-04-20");
        waitForPageLoad();
        assertThat(page.locator("#total-deposited")).hasText("(0)");
        assertThat(page.locator("#balance")).hasText("200.000");
        assertThat(page.locator("#pph23-message")).hasText("Masih ada PPh 23 yang harus disetor ke negara");
    }

    @Test
    @DisplayName("Tax summary page lists each tax account's debit, credit and balance and links to the detail reports")
    void shouldShowTaxSummary() {
        navigateTo("/reports/tax-summary?startDate=" + START + "&endDate=" + END);
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Ringkasan Pajak");
        assertThat(page.locator("#report-period")).hasText(PERIOD);
        assertTaxRow("1.1.25", "PPN Masukan", "440.000", "0", "440.000");
        assertTaxRow("2.1.03", "Hutang PPN", "0", "5.500.000", "5.500.000");
        assertTaxRow("2.1.21", "Hutang PPh 23", "200.000", "200.000", "0");
        assertTaxRow("2.1.20", "Hutang PPh 21", "0", "0", "0");
        assertThat(page.locator("#tax-summary-content tfoot")).containsText("5.940.000");
        assertThat(page.locator("a[href='/reports/ppn-summary?startDate=" + START + "&endDate=" + END + "']")).hasCount(1);
        assertThat(page.locator("a[href='/reports/pph23-withholding?startDate=" + START + "&endDate=" + END + "']")).hasCount(1);
        takeManualScreenshot("reports-tax-summary");
    }

    @Test
    @DisplayName("Project profitability page shows the selected project's revenue, cost, margin and budget status")
    void shouldShowProjectProfitability() {
        navigateTo("/reports/project-profitability");
        waitForPageLoad();
        assertThat(page.locator("#no-project-selected")).isVisible();

        page.locator("#projectId").selectOption(projectA.getId().toString());
        submitRange("/reports/project-profitability");

        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*projectId=" + projectA.getId() + ".*"));
        assertThat(page.locator("#project-profitability-content")).containsText(projectA.getCode());
        assertThat(page.locator("#project-profitability-content")).containsText(clientA.getName());
        assertThat(page.locator("#project-profitability-content")).containsText("60.000.000");
        assertThat(page.locator("#profitability-report")).containsText(PERIOD);
        assertThat(page.locator("#total-revenue")).hasText("50.000.000");
        assertThat(page.locator("#total-expense")).hasText("(10.000.000)");
        assertThat(page.locator("#gross-profit")).hasText("40.000.000");
        assertThat(page.locator("#profit-label")).hasText("LABA KOTOR");
        assertThat(page.locator("#profit-margin")).hasText("80.00%");
        assertThat(page.locator("#cost-budget")).hasText("25.000.000");
        assertThat(page.locator("#cost-spent")).hasText("0");
        assertThat(page.locator("#progress-percent")).hasText("0%");
        assertThat(page.locator("#risk-level")).hasText("LOW");
    }

    @Test
    @DisplayName("Client profitability page sums the selected client's projects")
    void shouldShowClientProfitability() {
        navigateTo("/reports/client-profitability");
        waitForPageLoad();
        assertThat(page.locator("#no-client-selected")).isVisible();

        page.locator("#clientId").selectOption(clientA.getId().toString());
        submitRange("/reports/client-profitability");

        assertThat(page.locator("#client-name")).hasText(clientA.getName());
        assertThat(page.locator("#client-code")).hasText(clientA.getCode());
        assertThat(page.locator("#total-revenue")).hasText("50.000.000");
        assertThat(page.locator("#total-profit")).hasText("40.000.000");
        assertThat(page.locator("#overall-margin")).hasText("80.00%");
        assertThat(page.locator("#project-count")).hasText("1");
        Locator row = page.locator("#projects-table .project-row");
        assertThat(row).hasCount(1);
        assertThat(row).containsText(projectA.getName());
        assertThat(row).containsText("50.000.000");
        assertThat(row).containsText("40.000.000");
    }

    @Test
    @DisplayName("Client ranking orders clients by revenue with profit, margin and revenue share, and links to their profitability")
    void shouldShowClientRanking() {
        navigateTo("/reports/client-ranking?startDate=" + START + "&endDate=" + END + "&limit=0");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Peringkat Klien");
        assertThat(page.locator("#limit")).hasValue("0");
        Locator rows = page.locator("#ranking-table .ranking-row");
        assertThat(rows).hasCount(2);
        assertThat(rows.nth(0)).containsText(clientA.getName());
        assertThat(rows.nth(0)).containsText("50.000.000");
        assertThat(rows.nth(0)).containsText("40.000.000");
        assertThat(rows.nth(0)).containsText("80.00%");
        assertThat(rows.nth(0)).containsText("71.43%");
        assertThat(rows.nth(1)).containsText(clientB.getName());
        assertThat(rows.nth(1)).containsText("20.000.000");
        assertThat(rows.nth(1)).containsText("100.00%");
        assertThat(rows.nth(1)).containsText("28.57%");

        page.locator("#limit").selectOption("5");
        page.locator("#btn-generate").click();
        page.waitForURL(url -> url.contains("limit=5"));
        assertThat(rows).hasCount(2);

        rows.nth(1).locator("a").first().click();
        page.waitForURL(url -> url.contains("/reports/client-profitability?clientId=" + clientB.getId()));
        assertThat(page.locator("#client-name")).hasText(clientB.getName());
        assertThat(page.locator("#total-revenue")).hasText("20.000.000");
        assertThat(page.locator("#overall-margin")).hasText("100.00%");
    }

    private void submitRange(String path) {
        page.locator("#startDate").fill(START);
        page.locator("#endDate").fill(END);
        page.locator("#btn-generate").click();
        page.waitForURL(url -> url.contains(path + "?") && url.contains("startDate=" + START) && url.contains("endDate=" + END));
        waitForPageLoad();
    }

    private void assertTaxRow(String accountCode, String label, String debit, String credit, String balance) {
        Locator cells = page.locator("#tax-summary-content tbody tr").filter(new Locator.FilterOptions()
                .setHas(page.locator("td").getByText(accountCode, new Locator.GetByTextOptions().setExact(true))))
                .locator("td");
        assertThat(cells.nth(1)).hasText(label);
        assertThat(cells.nth(2)).hasText(debit);
        assertThat(cells.nth(3)).hasText(credit);
        assertThat(cells.nth(4)).hasText(balance);
    }

    private Client client(String code, String name) {
        Client c = new Client();
        c.setCode(code);
        c.setName(name);
        return clientRepository.save(c);
    }

    private Project project(String code, String name, Client owner, String budget, String contract) {
        Project p = new Project();
        p.setCode(code);
        p.setName(name);
        p.setClient(owner);
        p.setBudgetAmount(budget == null ? null : new BigDecimal(budget));
        p.setContractValue(contract == null ? null : new BigDecimal(contract));
        return projectRepository.save(p);
    }

    private record Line(String accountCode, BigDecimal debit, BigDecimal credit) {}

    private static Line line(String accountCode, String debit, String credit) {
        return new Line(accountCode, new BigDecimal(debit), new BigDecimal(credit));
    }

    private void post(LocalDate date, String templateName, Project journalProject, Line... lines) {
        String number = "EXP-" + UUID.randomUUID().toString().substring(0, 8);
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
