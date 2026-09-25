package com.artivisi.accountingfinance.functional.service;

import com.artivisi.accountingfinance.entity.PayrollDetail;
import com.artivisi.accountingfinance.entity.PayrollRun;
import com.artivisi.accountingfinance.functional.util.CsvLoader;
import com.artivisi.accountingfinance.repository.PayrollDetailRepository;
import com.artivisi.accountingfinance.repository.PayrollRunRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.Dialog;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Payroll end to end through the UI: the seeded run's detail page, and a new run taken from the
 * list through create, approve and post to its journal on the transaction page and its 1721-A1 row.
 *
 * Action-level behavior (validation, rejected transitions, recalculation, delete, exports) is
 * covered by PayrollControllerFunctionalTest. The lifecycle here uses period 2032-03, which no
 * other test creates, and never touches the seeded 2024-02 run beyond viewing it.
 */
@DisplayName("Service Industry - Payroll Comprehensive")
@Import(ServiceTestDataInitializer.class)
class ServicePayrollComprehensiveTest extends PlaywrightTestBase {

    private static final String DATA_DIR = "service/";
    private static final String LIFECYCLE_PERIOD = "2032-03";

    @Autowired
    private PayrollRunRepository payrollRunRepository;

    @Autowired
    private PayrollDetailRepository payrollDetailRepository;

    @BeforeEach
    void setup() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("Should show seeded payroll run with per-employee amounts from test data")
    void shouldDisplaySeededPayrollRun() {
        Map<String, String> csvRun = CsvLoader.loadRecords(DATA_DIR + "21_payroll_runs.csv").get(0);
        Map<String, String> names = CsvLoader.loadRecords(DATA_DIR + "employees.csv").stream()
            .collect(Collectors.toMap(e -> e.get("employee_id"), e -> e.get("name")));
        PayrollRun run = payrollRunRepository.findByPayrollPeriod(csvRun.get("payroll_period"))
            .orElseThrow(() -> new IllegalStateException("Seeded payroll run missing: " + csvRun.get("payroll_period")));

        navigateTo("/payroll?size=" + payrollRunRepository.count());
        waitForPageLoad();
        page.locator("#payroll-" + run.getId() + " a:text-is('Detail')").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/payroll/" + run.getId());
        assertThat(page.locator("#page-title")).hasText("Detail Payroll - Februari 2024");
        assertThat(page.locator("[data-testid='status-badge']")).hasText("Approved");
        assertThat(page.locator("[data-testid='employee-count']")).hasText(csvRun.get("employee_count"));
        assertThat(page.locator("[data-testid='total-gross']")).hasText(rupiah(csvRun.get("total_gross")));
        assertThat(page.locator("[data-testid='total-deductions']")).hasText(rupiah(csvRun.get("total_deductions")));
        assertThat(page.locator("[data-testid='total-net']")).hasText(rupiah(csvRun.get("total_net_pay")));
        assertThat(page.locator("#btn-post")).isVisible();
        assertThat(page.locator("#btn-cancel")).isVisible();
        assertThat(page.locator("#btn-approve")).hasCount(0);
        assertThat(page.locator("#btn-delete")).hasCount(0);

        List<Map<String, String>> csvDetails = CsvLoader.loadRecords(DATA_DIR + "22_payroll_details.csv");
        assertThat(page.locator("[data-testid^='detail-row-']")).hasCount(csvDetails.size());
        for (Map<String, String> d : csvDetails) {
            BigDecimal employeeBpjs = new BigDecimal(d.get("bpjs_kes_employee"))
                .add(new BigDecimal(d.get("bpjs_jht_employee")))
                .add(new BigDecimal(d.get("bpjs_jp_employee")));
            Locator cells = page.locator("[data-testid='detail-row-" + d.get("employee_id") + "'] td");
            assertThat(cells.nth(1)).hasText(names.get(d.get("employee_id")));
            assertThat(cells.nth(2)).hasText(rupiah(d.get("gross_salary")));
            assertThat(cells.nth(3)).hasText(rupiah(employeeBpjs));
            assertThat(cells.nth(4)).hasText(rupiah(d.get("pph21")));
            assertThat(cells.nth(5)).hasText("(" + rupiah(d.get("total_deductions")) + ")");
            assertThat(cells.nth(6)).hasText(rupiah(d.get("net_pay")));
        }
    }

    @Test
    @DisplayName("Should take a new payroll run from the list through approve and post to its journal and 1721-A1")
    void shouldRunPayrollLifecycleThroughUi() {
        navigateTo("/payroll");
        waitForPageLoad();
        page.locator("#btn-new-payroll").click();
        waitForPageLoad();

        page.locator("input#period").fill(LIFECYCLE_PERIOD);
        page.locator("input#baseSalary").fill("10000000");
        page.locator("select#jkkRiskClass").selectOption("1");
        page.locator("#btn-submit").click();
        waitForPageLoad();

        PayrollRun run = payrollRunRepository.findByPayrollPeriod(LIFECYCLE_PERIOD)
            .orElseThrow(() -> new AssertionError("Payroll run not created for " + LIFECYCLE_PERIOD));
        assertThat(page).hasURL(baseUrl() + "/payroll/" + run.getId());
        assertThat(page.locator("[data-testid='status-badge']")).hasText("Calculated");

        page.locator("#btn-approve").click();
        waitForPageLoad();
        assertThat(page.locator("[data-testid='success-message']")).hasText("Payroll periode 2032-03 berhasil di-approve");
        assertThat(page.locator("[data-testid='status-badge']")).hasText("Approved");

        page.onDialog(Dialog::accept); // "Posting payroll akan membuat jurnal. Lanjutkan?"
        page.locator("#btn-post").click();
        waitForPageLoad();
        assertThat(page.locator("[data-testid='success-message']")).hasText("Payroll periode 2032-03 berhasil di-posting ke jurnal");
        assertThat(page.locator("[data-testid='status-badge']")).hasText("Posted");

        List<PayrollDetail> details = payrollDetailRepository.findByPayrollRunIdWithEmployee(run.getId());
        BigDecimal n = BigDecimal.valueOf(details.size());
        BigDecimal gross = new BigDecimal("10000000").multiply(n);
        BigDecimal pph21 = details.stream().map(PayrollDetail::getPph21).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal netPay = gross.subtract(new BigDecimal("400000").multiply(n)).subtract(pph21);
        Map<String, PayrollDetail> byNik = details.stream()
            .collect(Collectors.toMap(PayrollDetail::getEmployeeId, Function.identity()));
        // TER category A (TK/0) 2% and B (K/2) 1.5% of 10,000,000
        assertThat(byNik.get("EMP-001").getPph21()).isEqualByComparingTo("150000");
        assertThat(byNik.get("EMP-002").getPph21()).isEqualByComparingTo("200000");
        assertThat(byNik.get("EMP-003").getPph21()).isEqualByComparingTo("200000");

        page.locator("[data-testid='journal-reference'] a").click();
        waitForPageLoad();

        assertThat(page.locator("[data-testid='status-posted']")).isVisible();
        assertThat(page.locator("[data-testid='template-link']")).hasText("Post Gaji Bulanan");
        assertThat(page.locator(".journal-entry-row")).hasCount(6);
        assertThat(journalLine("5.1.01").locator("[id^='entry-debit-']")).hasText(dotted(gross));
        assertThat(journalLine("5.1.02").locator("[id^='entry-debit-']")).hasText(dotted(new BigDecimal("400000").multiply(n)));
        assertThat(journalLine("5.1.03").locator("[id^='entry-debit-']")).hasText(dotted(new BigDecimal("624000").multiply(n)));
        assertThat(journalLine("2.1.10").locator("[id^='entry-credit-']")).hasText(dotted(netPay));
        assertThat(journalLine("2.1.13").locator("[id^='entry-credit-']")).hasText(dotted(new BigDecimal("1424000").multiply(n)));
        assertThat(journalLine("2.1.20").locator("[id^='entry-credit-']")).hasText(dotted(pph21));
        String total = dotted(gross.add(new BigDecimal("1024000").multiply(n)));
        assertThat(page.locator("#total-debit")).hasText(total);
        assertThat(page.locator("#total-credit")).hasText(total);

        navigateTo("/payroll/bukti-potong?year=2032");
        waitForPageLoad();
        Locator agus = page.locator("[data-testid='bukti-potong-row-EMP-003'] td");
        assertThat(agus.nth(1)).hasText("Agus Wijaya");
        assertThat(agus.nth(3)).hasText("1");
        assertThat(agus.nth(4)).hasText("Rp 10,000,000");
        assertThat(agus.nth(5)).hasText("Rp 200,000");
    }

    private Locator journalLine(String accountCode) {
        return page.locator(".journal-entry-row").filter(new Locator.FilterOptions()
            .setHas(page.locator("[id^='entry-account-code-']:text-is('" + accountCode + "')")));
    }

    private static String rupiah(String amount) {
        return rupiah(new BigDecimal(amount));
    }

    private static String rupiah(BigDecimal amount) {
        return "Rp " + String.format(Locale.US, "%,d", amount.longValueExact());
    }

    /** Transaction detail formats amounts with '.' grouping. */
    private static String dotted(BigDecimal amount) {
        return "Rp " + String.format(Locale.US, "%,d", amount.longValueExact()).replace(',', '.');
    }
}
