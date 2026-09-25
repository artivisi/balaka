package com.artivisi.accountingfinance.functional.service;

import com.artivisi.accountingfinance.entity.EmploymentStatus;
import com.artivisi.accountingfinance.entity.EmploymentType;
import com.artivisi.accountingfinance.entity.PayrollRun;
import com.artivisi.accountingfinance.entity.PayrollStatus;
import com.artivisi.accountingfinance.entity.PtkpStatus;
import com.artivisi.accountingfinance.functional.util.CsvLoader;
import com.artivisi.accountingfinance.functional.util.TransactionRow;
import com.artivisi.accountingfinance.repository.PayrollRunRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CSV-driven checks that the service test data (src/test/resources/testdata/service/) is
 * visible through the UI: every client, project, employee, payroll run and transaction row in
 * the CSVs is looked up by its natural key and its rendered values compared to the CSV.
 */
@DisplayName("Service Industry - CSV-Driven Tests")
@Import(ServiceTestDataInitializer.class)
class ServiceCsvDrivenTest extends PlaywrightTestBase {

    private static final String DATA_DIR = "service/";

    @Autowired
    private PayrollRunRepository payrollRunRepository;

    @Test
    @DisplayName("Should load transactions CSV")
    void shouldLoadTransactionsCsv() {
        List<TransactionRow> transactions = CsvLoader.loadTransactions(DATA_DIR + "transactions.csv");

        assertEquals(5, transactions.size());
        assertEquals("Setoran Modal", transactions.get(0).templateName());
        assertEquals("Pendapatan Jasa Konsultasi", transactions.get(1).templateName());
    }

    @TestFactory
    @DisplayName("Verify transactions from CSV are posted with expected journal")
    Stream<DynamicTest> verifyTransactionsFromCsv() {
        return CsvLoader.loadTransactions(DATA_DIR + "transactions.csv").stream()
            .map(tx -> DynamicTest.dynamicTest(
                "Tx " + tx.sequence() + ": " + tx.description(),
                () -> verifyTransaction(tx)
            ));
    }

    private void verifyTransaction(TransactionRow tx) {
        String number = String.format("SVC-TRX-%03d", tx.sequence());
        String amount = "Rp " + tx.expectedAmount();
        loginAsAdmin();

        navigateTo("/transactions");
        waitForPageLoad();
        page.locator("#search-transaksi").fill(number);
        page.locator("#btn-filter").click();

        assertThat(page.locator("#transaction-table [data-testid^='trx-row-']")).hasCount(1);
        Locator row = page.locator("[data-testid='trx-row-" + number + "']");
        assertThat(row).containsText(tx.description());
        assertThat(row).containsText(tx.reference());
        assertThat(row).containsText(amount);
        assertThat(row).containsText("POSTED");

        row.locator(".hidden a:text-is('" + number + "')").first().click();
        waitForPageLoad();

        assertThat(page.locator("[data-testid='status-posted']")).isVisible();
        assertThat(page.locator("[data-testid='template-link']")).hasText(tx.templateName());
        Locator entries = page.locator(".journal-entry-row");
        assertThat(entries).hasCount(2);
        assertThat(journalLine(tx.expectedDebitAccount()).locator("[id^='entry-debit-']")).hasText(amount);
        assertThat(journalLine(tx.expectedCreditAccount()).locator("[id^='entry-credit-']")).hasText(amount);
        assertThat(page.locator("#total-debit")).hasText(amount);
        assertThat(page.locator("#total-credit")).hasText(amount);

        if (tx.screenshot()) {
            takeManualScreenshot("service/tx-" + tx.sequence() + "-" + slugify(tx.description()));
        }
    }

    @TestFactory
    @DisplayName("Verify clients from CSV on client detail page")
    Stream<DynamicTest> verifyClientsFromTestData() {
        return CsvLoader.loadRecords(DATA_DIR + "clients.csv").stream()
            .map(c -> DynamicTest.dynamicTest("Client " + c.get("code"), () -> {
                loginAsAdmin();
                navigateTo("/clients/" + c.get("code"));
                waitForPageLoad();

                Locator detail = page.locator("[data-testid='client-detail']");
                assertThat(detail.locator("h2").first()).hasText(c.get("name"));
                assertThat(detail.locator("[data-testid='client-code']")).hasText(c.get("code"));
                assertThat(infoValue("Kontak")).hasText(c.get("contact_person"));
                assertThat(infoValue("Email")).hasText(c.get("email"));
                assertThat(infoValue("Telepon")).hasText(c.get("phone"));
                assertThat(infoValue("Alamat")).hasText(c.get("address"));
                assertThat(infoValue("NPWP")).hasText(c.get("npwp"));
                assertThat(detail.locator("[data-testid='client-status']")).hasText("Aktif");
            }));
    }

    @TestFactory
    @DisplayName("Verify projects from CSV in project list with client, contract value and status")
    Stream<DynamicTest> verifyProjectsFromTestData() {
        Map<String, String> clientNames = CsvLoader.loadRecords(DATA_DIR + "clients.csv").stream()
            .collect(Collectors.toMap(c -> c.get("code"), c -> c.get("name")));
        Map<String, String> statusLabels = Map.of("ACTIVE", "Aktif", "COMPLETED", "Selesai", "ARCHIVED", "Diarsipkan");

        return CsvLoader.loadRecords(DATA_DIR + "projects.csv").stream()
            .map(p -> DynamicTest.dynamicTest("Project " + p.get("code"), () -> {
                String clientName = required(clientNames, p.get("client_code"), "client");
                String status = required(statusLabels, p.get("status"), "status");
                loginAsAdmin();
                navigateTo("/projects");
                waitForPageLoad();
                Locator search = page.locator("#search-input");
                search.fill(p.get("code"));
                search.press("End");

                Locator rows = page.locator("[data-testid='project-table'] tbody tr");
                assertThat(rows).hasCount(1);
                Locator cells = rows.first().locator("td");
                assertThat(cells.nth(0)).hasText(p.get("code"));
                assertThat(cells.nth(1)).hasText(p.get("name"));
                assertThat(cells.nth(2)).hasText(clientName);
                assertThat(cells.nth(3)).hasText(groupedThousands(p.get("contract_value")));
                assertThat(cells.nth(4)).hasText(status);
            }));
    }

    @TestFactory
    @DisplayName("Verify employees from CSV in employee list")
    Stream<DynamicTest> verifyEmployeesFromTestData() {
        return CsvLoader.loadRecords(DATA_DIR + "employees.csv").stream()
            .map(e -> DynamicTest.dynamicTest("Employee " + e.get("employee_id"), () -> {
                loginAsAdmin();
                navigateTo("/employees");
                waitForPageLoad();
                Locator search = page.locator("#search-input");
                search.fill(e.get("employee_id"));
                search.press("End");

                Locator row = page.locator("#employee-table tbody tr:has(span.font-mono:text-is('"
                    + e.get("employee_id") + "'))");
                assertThat(row).hasCount(1);
                Locator cells = row.locator("td");
                assertThat(cells.nth(1).locator("div.font-medium")).hasText(e.get("name"));
                assertThat(cells.nth(1).locator("div.text-gray-500")).hasText(e.get("email"));
                assertThat(cells.nth(2)).hasText(e.get("job_title"));
                assertThat(cells.nth(3)).hasText(e.get("department"));
                PtkpStatus ptkp = PtkpStatus.valueOf(e.get("ptkp_status"));
                assertThat(cells.nth(4)).hasText(ptkp.getCode());
                assertThat(cells.nth(5)).hasText(EmploymentStatus.valueOf(e.get("employment_status")).getDisplayName());

                cells.nth(6).locator("a:text-is('Lihat')").click();
                waitForPageLoad();
                assertThat(employeeInfo("Tanggal Bergabung")).hasText(LocalDate.parse(e.get("hire_date"))
                    .format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.forLanguageTag("id-ID"))));
                assertThat(employeeInfo("Tipe Kepegawaian"))
                    .hasText(EmploymentType.valueOf(e.get("employment_type")).getDisplayName());
                assertThat(employeeInfo("Status PTKP")).hasText(ptkp.getDisplayName());
                assertThat(employeeInfo("NIK KTP")).hasText(mask(e.get("nik_ktp"), 4, 4));
                assertThat(employeeInfo("NPWP")).hasText(mask(e.get("npwp"), 4, 3));
                assertThat(employeeInfo("Bank")).hasText(e.get("bank_name"));
                assertThat(employeeInfo("Nomor Rekening")).hasText(mask(e.get("bank_account"), 3, 3));
                assertThat(employeeInfo("BPJS Kesehatan")).hasText(mask(e.get("bpjs_kesehatan_number"), 3, 3));
                assertThat(employeeInfo("BPJS Ketenagakerjaan"))
                    .hasText(mask(e.get("bpjs_ketenagakerjaan_number"), 3, 3));
            }));
    }

    @Test
    @DisplayName("Verify payroll run from CSV in payroll list")
    void verifyPayrollFromTestData() {
        List<Map<String, String>> runs = CsvLoader.loadRecords(DATA_DIR + "21_payroll_runs.csv");
        assertEquals(1, runs.size());
        Map<String, String> csv = runs.get(0);
        PayrollRun run = payrollRunRepository.findByPayrollPeriod(csv.get("payroll_period"))
            .orElseThrow(() -> new IllegalStateException("Seeded payroll run missing: " + csv.get("payroll_period")));

        loginAsAdmin();
        navigateTo("/payroll?size=" + payrollRunRepository.count());
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Daftar Payroll");
        Locator cells = page.locator("#payroll-" + run.getId() + " td");
        assertThat(cells.nth(0)).containsText("Februari 2024");
        assertThat(cells.nth(0)).containsText(csv.get("payroll_period"));
        assertThat(cells.nth(1)).hasText(csv.get("employee_count"));
        assertThat(cells.nth(2)).hasText("Rp " + groupedThousands(csv.get("total_gross")));
        assertThat(cells.nth(3)).hasText("Rp " + groupedThousands(csv.get("total_net_pay")));
        assertThat(cells.nth(4)).hasText(PayrollStatus.valueOf(csv.get("status")).getDisplayName());
    }

    private Locator infoValue(String label) {
        return page.locator("[data-testid='client-detail'] div:has(> p.text-gray-500:text-is('" + label + "')) > p.font-medium");
    }

    private Locator employeeInfo(String label) {
        return page.locator("[data-testid='employee-detail'] div:has(> p.text-gray-500:text-is('" + label + "')) > p.font-medium");
    }

    private static String mask(String value, int showFirst, int showLast) {
        return value.substring(0, showFirst) + "*".repeat(value.length() - showFirst - showLast)
            + value.substring(value.length() - showLast);
    }

    private Locator journalLine(String accountCode) {
        return page.locator(".journal-entry-row").filter(new Locator.FilterOptions()
            .setHas(page.locator("[id^='entry-account-code-']:text-is('" + accountCode + "')")));
    }

    private static String required(Map<String, String> map, String key, String what) {
        return Optional.ofNullable(map.get(key))
            .orElseThrow(() -> new IllegalStateException("Unknown " + what + " in CSV: " + key));
    }

    private static String groupedThousands(String value) {
        return new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US)).format(new BigDecimal(value));
    }

    private String slugify(String text) {
        return text.toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-|-$", "")
            .substring(0, Math.min(30, text.length()));
    }
}
