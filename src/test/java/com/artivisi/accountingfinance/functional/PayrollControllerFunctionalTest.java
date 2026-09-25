package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.EmploymentStatus;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.PayrollDetail;
import com.artivisi.accountingfinance.entity.PayrollRun;
import com.artivisi.accountingfinance.entity.PayrollStatus;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.EmployeeRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.PayrollDetailRepository;
import com.artivisi.accountingfinance.repository.PayrollRunRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.service.PayrollService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Dialog;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Payroll web UI: list and filter, create-and-calculate form, run lifecycle actions
 * (approve, cancel, post, recalculate, delete), exports, payslips and 1721-A1.
 *
 * Every test works on payroll runs it creates for its own period (2031, 2033, 2034), so the seeded
 * 2024-02 run and runs created by other classes sharing this database are never mutated. Runs
 * cover every active employee, and other classes add employees, so per-employee amounts are asserted
 * for the seeded employees and run totals are asserted against the run's employee count.
 *
 * Amounts for a 10,000,000 base salary at JKK risk class 1 (non-December, TER method):
 * employee BPJS 400,000 (Kes 1% 100,000 + JHT 2% 200,000 + JP 1% 100,000);
 * company BPJS 1,024,000 (Kes 4% 400,000 + JKK 0.24% 24,000 + JKM 0.3% 30,000 + JHT 3.7% 370,000 + JP 2% 200,000);
 * PPh 21 TER category A (TK/0) 2% = 200,000, category B (K/2) 1.5% = 150,000.
 */
@DisplayName("Payroll Controller Tests")
@Import(ServiceTestDataInitializer.class)
class PayrollControllerFunctionalTest extends PlaywrightTestBase {

    private static final BigDecimal BASE_SALARY = new BigDecimal("10000000");
    private static final BigDecimal EMPLOYEE_BPJS = new BigDecimal("400000");
    private static final BigDecimal COMPANY_BPJS_KES = new BigDecimal("400000");
    private static final BigDecimal COMPANY_BPJS_TK = new BigDecimal("624000");
    private static final BigDecimal EMPLOYEE_BPJS_KES = new BigDecimal("100000");
    private static final BigDecimal EMPLOYEE_BPJS_TK = new BigDecimal("300000");

    // From src/test/resources/testdata/service/employees.csv
    private static final Expected BUDI = new Expected("EMP-001", "Budi Santoso", "12.345.678.9-012.000", "150000", "550000", "9450000");
    private static final Expected DEWI = new Expected("EMP-002", "Dewi Lestari", "12.345.678.9-013.000", "200000", "600000", "9400000");
    private static final Expected AGUS = new Expected("EMP-003", "Agus Wijaya", "12.345.678.9-014.000", "200000", "600000", "9400000");
    private static final List<Expected> SEEDED_EMPLOYEES = List.of(BUDI, DEWI, AGUS);

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final int BUKTI_POTONG_YEAR = 2033;
    private static final YearMonth EXPORT_PERIOD = YearMonth.of(2034, 1);

    @Autowired
    private PayrollRunRepository payrollRunRepository;

    @Autowired
    private PayrollDetailRepository payrollDetailRepository;

    @Autowired
    private PayrollService payrollService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    private String csrfHeader;
    private String csrfToken;

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
        csrfHeader = page.locator("meta[name='_csrf_header']").getAttribute("content");
        csrfToken = page.locator("meta[name='_csrf']").getAttribute("content");
    }

    // ==================== LIST ====================

    @Test
    @DisplayName("Should show payroll list with status filter options and links to new form and bukti potong")
    void shouldShowListWithFilterAndNavigation() {
        navigateTo("/payroll");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Daftar Payroll");
        Locator options = page.locator("select#status option");
        assertThat(options).hasText(new String[] {"Semua Status", "Draft", "Calculated", "Approved", "Posted", "Cancelled"});
        assertThat(options.nth(0)).hasAttribute("value", "");
        assertThat(options.nth(3)).hasAttribute("value", "APPROVED");

        page.locator("#btn-new-payroll").click();
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/payroll/new");
        assertThat(page.locator("#page-title")).hasText("Buat Payroll Baru");

        navigateTo("/payroll");
        page.locator("#btn-bukti-potong").click();
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/payroll/bukti-potong");
        assertThat(page.locator("#page-title")).hasText("Bukti Potong PPh 21 (1721-A1)");
    }

    @Test
    @DisplayName("Should filter payroll list by status")
    void shouldFilterByStatus() {
        UUID approvedId = approvedRun(YearMonth.of(2031, 10));
        UUID calculatedId = calculatedRun(YearMonth.of(2031, 11));

        navigateTo("/payroll");
        waitForPageLoad();
        assertThat(page.locator("#payroll-" + calculatedId)).hasCount(1);

        page.locator("select#status").selectOption("APPROVED");
        page.locator("form[action='/payroll'] button[type='submit']").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/payroll?status=APPROVED");
        assertThat(page.locator("select#status")).hasValue("APPROVED");
        assertThat(page.locator("#payroll-" + approvedId + " td").nth(0)).containsText("Oktober 2031");
        assertThat(page.locator("#payroll-" + approvedId + " td").nth(4)).hasText("Approved");
        assertThat(page.locator("#payroll-" + calculatedId)).hasCount(0);
        Locator statusCells = page.locator("tr[id^='payroll-'] td:nth-child(5)");
        for (int i = 0; i < statusCells.count(); i++) {
            assertThat(statusCells.nth(i)).hasText("Approved");
        }
    }

    // ==================== CREATE ====================

    @Test
    @DisplayName("Should render new payroll form with period, base salary and JKK risk classes")
    void shouldRenderNewPayrollForm() {
        navigateTo("/payroll/new");
        waitForPageLoad();

        Locator period = page.locator("input#period");
        assertThat(period).hasAttribute("type", "month");
        assertThat(period.getAttribute("required")).isNotNull();
        assertThat(page.locator("input#baseSalary")).hasValue("10000000");

        Locator riskClasses = page.locator("select#jkkRiskClass option");
        assertThat(riskClasses).hasText(new String[] {
            "Kelas 1 - Sangat Rendah (0.24%) - IT, Jasa",
            "Kelas 2 - Rendah (0.54%) - Retail, Perdagangan",
            "Kelas 3 - Sedang (0.89%) - Manufaktur Ringan",
            "Kelas 4 - Tinggi (1.27%) - Konstruksi",
            "Kelas 5 - Sangat Tinggi (1.74%) - Pertambangan"
        });
        for (int i = 0; i < 5; i++) {
            assertThat(riskClasses.nth(i)).hasAttribute("value", String.valueOf(i + 1));
        }
        assertThat(page.locator("select#jkkRiskClass")).hasValue("1");
        assertThat(page.locator("#btn-submit")).hasText("Buat & Kalkulasi");
    }

    @Test
    @DisplayName("Should create payroll run and calculate BPJS, PPh 21 and net pay for every active employee")
    void shouldCreateAndCalculatePayrollRun() {
        String period = "2031-01";
        navigateTo("/payroll/new");
        waitForPageLoad();

        page.locator("input#period").fill(period);
        page.locator("input#baseSalary").fill("10000000");
        page.locator("select#jkkRiskClass").selectOption("1");
        page.locator("#btn-submit").click();
        waitForPageLoad();

        PayrollRun run = payrollRunRepository.findByPayrollPeriod(period)
            .orElseThrow(() -> new AssertionError("Payroll run not created for " + period));
        assertThat(page).hasURL(baseUrl() + "/payroll/" + run.getId());
        assertThat(page.locator("[data-testid='success-message']"))
            .hasText("Payroll untuk periode 2031-01 berhasil dibuat dan dikalkulasi");
        assertThat(page.locator("#page-title")).hasText("Detail Payroll - Januari 2031");
        assertThat(page.locator("[data-testid='status-badge']")).hasText("Calculated");

        List<PayrollDetail> details = payrollDetailRepository.findByPayrollRunIdWithEmployee(run.getId());
        assertThat(run.getStatus()).isEqualTo(PayrollStatus.CALCULATED);
        assertThat(details).hasSize(employeeRepository.findByActiveTrueAndEmploymentStatus(EmploymentStatus.ACTIVE).size());
        assertThat(run.getEmployeeCount()).isEqualTo(details.size());
        assertThat(details.stream().map(PayrollDetail::getEmployeeId))
            .contains(BUDI.nik(), DEWI.nik(), AGUS.nik())
            .doesNotHaveDuplicates();

        Map<String, PayrollDetail> byNik = byNik(details);
        for (Expected e : SEEDED_EMPLOYEES) {
            PayrollDetail d = byNik.get(e.nik());
            assertThat(d.getGrossSalary()).isEqualByComparingTo(BASE_SALARY);
            assertThat(d.getBpjsKesEmployee()).isEqualByComparingTo("100000");
            assertThat(d.getBpjsJhtEmployee()).isEqualByComparingTo("200000");
            assertThat(d.getBpjsJpEmployee()).isEqualByComparingTo("100000");
            assertThat(d.getBpjsKesCompany()).isEqualByComparingTo("400000");
            assertThat(d.getBpjsJkk()).isEqualByComparingTo("24000");
            assertThat(d.getBpjsJkm()).isEqualByComparingTo("30000");
            assertThat(d.getBpjsJhtCompany()).isEqualByComparingTo("370000");
            assertThat(d.getBpjsJpCompany()).isEqualByComparingTo("200000");
            assertThat(d.getPph21()).isEqualByComparingTo(e.pph21());
            assertThat(d.getTotalDeductions()).isEqualByComparingTo(e.deductions());
            assertThat(d.getNetPay()).isEqualByComparingTo(e.netPay());

            Locator cells = page.locator("[data-testid='detail-row-" + e.nik() + "'] td");
            assertThat(cells.nth(1)).hasText(e.name());
            assertThat(cells.nth(2)).hasText("Rp 10,000,000");
            assertThat(cells.nth(3)).hasText("Rp 400,000");
            assertThat(cells.nth(4)).hasText(rupiah(e.pph21()));
            assertThat(cells.nth(5)).hasText("(" + rupiah(e.deductions()) + ")");
            assertThat(cells.nth(6)).hasText(rupiah(e.netPay()));
        }

        int n = details.size();
        BigDecimal totalPph21 = sum(details, PayrollDetail::getPph21);
        assertThat(run.getTotalGross()).isEqualByComparingTo(BASE_SALARY.multiply(BigDecimal.valueOf(n)));
        assertThat(run.getTotalCompanyBpjs()).isEqualByComparingTo(new BigDecimal("1024000").multiply(BigDecimal.valueOf(n)));
        assertThat(run.getTotalPph21()).isEqualByComparingTo(totalPph21);
        assertThat(run.getTotalDeductions()).isEqualByComparingTo(EMPLOYEE_BPJS.multiply(BigDecimal.valueOf(n)).add(totalPph21));
        assertThat(run.getTotalNetPay()).isEqualByComparingTo(run.getTotalGross().subtract(run.getTotalDeductions()));

        assertThat(page.locator("[data-testid='employee-count']")).hasText(String.valueOf(n));
        assertThat(page.locator("[data-testid='total-gross']")).hasText(rupiah(run.getTotalGross()));
        assertThat(page.locator("[data-testid='total-deductions']")).hasText(rupiah(run.getTotalDeductions()));
        assertThat(page.locator("[data-testid='total-net']")).hasText(rupiah(run.getTotalNetPay()));
    }

    @Test
    @DisplayName("Should reject blank period on the server without creating a payroll run")
    void shouldRejectBlankPeriod() {
        long runsBefore = payrollRunRepository.count();
        navigateTo("/payroll/new");
        waitForPageLoad();

        // bypass the browser's required check to exercise server-side validation
        page.locator("input#period").evaluate("el => el.removeAttribute('required')");
        page.locator("input#period").fill("");
        page.locator("#btn-submit").click();
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Buat Payroll Baru");
        assertThat(page.locator("[data-testid='period-error']")).hasText("Periode wajib diisi");
        assertThat(page.locator("[data-testid='flash-error']")).hasCount(0);
        assertThat(payrollRunRepository.count()).isEqualTo(runsBefore);
    }

    @Test
    @DisplayName("Should reject a second payroll run for an existing period")
    void shouldRejectDuplicatePeriod() {
        YearMonth period = YearMonth.of(2031, 2);
        UUID existingId = calculatedRun(period);
        long runsBefore = payrollRunRepository.count();

        navigateTo("/payroll/new");
        waitForPageLoad();
        page.locator("input#period").fill(period.toString());
        page.locator("input#baseSalary").fill("12000000");
        page.locator("#btn-submit").click();
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Buat Payroll Baru");
        assertThat(page.locator("[data-testid='period-error']")).hasText("Payroll untuk periode ini sudah ada");
        assertThat(payrollRunRepository.count()).isEqualTo(runsBefore);
        PayrollRun existing = payrollRunRepository.findById(existingId).orElseThrow();
        assertThat(existing.getStatus()).isEqualTo(PayrollStatus.CALCULATED);
        assertThat(existing.getTotalGross()).isEqualByComparingTo(BASE_SALARY.multiply(BigDecimal.valueOf(existing.getEmployeeCount())));
    }

    // ==================== LIFECYCLE ACTIONS ====================

    @Test
    @DisplayName("Should approve calculated payroll and reject approving it again")
    void shouldApproveCalculatedPayroll() {
        UUID id = calculatedRun(YearMonth.of(2031, 3));
        openDetail(id);
        assertThat(page.locator("#btn-post")).hasCount(0);

        page.locator("#btn-approve").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/payroll/" + id);
        assertThat(page.locator("[data-testid='success-message']")).hasText("Payroll periode 2031-03 berhasil di-approve");
        assertThat(page.locator("[data-testid='status-badge']")).hasText("Approved");
        assertThat(page.locator("#btn-approve")).hasCount(0);
        assertThat(page.locator("#btn-delete")).hasCount(0);
        assertThat(page.locator("#btn-post")).isVisible();
        assertThat(page.locator("#btn-cancel")).isVisible();
        assertThat(status(id)).isEqualTo(PayrollStatus.APPROVED);

        APIResponse again = postAction(id, "approve", FormData.create());
        assertThat(again.url()).isEqualTo(baseUrl() + "/payroll/" + id);
        assertThat(again.text()).contains("Payroll harus dalam status CALCULATED untuk di-approve");
        assertThat(status(id)).isEqualTo(PayrollStatus.APPROVED);
    }

    @Test
    @DisplayName("Should cancel payroll after confirmation")
    void shouldCancelPayroll() {
        UUID id = calculatedRun(YearMonth.of(2031, 4));
        openDetail(id);

        page.onDialog(Dialog::accept); // "Yakin ingin membatalkan payroll ini?"
        page.locator("#btn-cancel").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/payroll/" + id);
        assertThat(page.locator("[data-testid='success-message']")).hasText("Payroll periode 2031-04 berhasil dibatalkan");
        assertThat(page.locator("[data-testid='status-badge']")).hasText("Cancelled");
        for (String button : List.of("#btn-approve", "#btn-post", "#btn-cancel", "#btn-delete", "#btn-export")) {
            assertThat(page.locator(button)).hasCount(0);
        }
        PayrollRun run = payrollRunRepository.findById(id).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(PayrollStatus.CANCELLED);
        assertThat(run.getCancelledAt()).isNotNull();
        assertThat(run.getTransaction()).isNull();
    }

    @Test
    @DisplayName("Should post approved payroll as a balanced journal of gross, BPJS, PPh 21 and net pay")
    void shouldPostApprovedPayrollToJournal() {
        UUID id = approvedRun(YearMonth.of(2031, 5));
        openDetail(id);

        page.onDialog(Dialog::accept); // "Posting payroll akan membuat jurnal. Lanjutkan?"
        page.locator("#btn-post").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/payroll/" + id);
        assertThat(page.locator("[data-testid='success-message']")).hasText("Payroll periode 2031-05 berhasil di-posting ke jurnal");
        assertThat(page.locator("[data-testid='status-badge']")).hasText("Posted");
        assertThat(page.locator("#btn-post")).hasCount(0);
        assertThat(page.locator("#btn-cancel")).hasCount(0);

        PayrollRun run = payrollRunRepository.findById(id).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(PayrollStatus.POSTED);
        assertThat(run.getPostedAt()).isNotNull();
        Transaction trx = transactionRepository.findById(run.getTransaction().getId()).orElseThrow();
        assertThat(trx.getStatus()).isEqualTo(TransactionStatus.POSTED);
        assertThat(trx.getTransactionDate()).isEqualTo(LocalDate.of(2031, 5, 31));
        assertThat(trx.getReferenceNumber()).isEqualTo("PAYROLL-2031-05");
        assertThat(trx.getDescription()).isEqualTo("Payroll Mei 2031");
        assertThat(trx.getAmount()).isEqualByComparingTo(run.getTotalGross());

        Locator reference = page.locator("[data-testid='journal-reference'] a");
        assertThat(reference).hasText(trx.getTransactionNumber());
        assertThat(reference).hasAttribute("href", "/transactions/" + trx.getId());

        BigDecimal n = BigDecimal.valueOf(run.getEmployeeCount());
        BigDecimal totalPph21 = sum(payrollDetailRepository.findByPayrollRunIdWithEmployee(id), PayrollDetail::getPph21);
        BigDecimal gross = BASE_SALARY.multiply(n);
        BigDecimal netPay = gross.subtract(EMPLOYEE_BPJS.multiply(n)).subtract(totalPph21);
        BigDecimal totalBpjs = COMPANY_BPJS_KES.add(COMPANY_BPJS_TK).add(EMPLOYEE_BPJS_KES).add(EMPLOYEE_BPJS_TK).multiply(n);

        List<JournalEntry> entries = journalEntryRepository.findByTransactionIdWithAccount(trx.getId());
        assertThat(entries).hasSize(6);
        Map<String, JournalEntry> byAccount = entries.stream()
            .collect(Collectors.toMap(e -> e.getAccount().getAccountCode(), Function.identity()));
        assertJournal(byAccount, "5.1.01", gross, BigDecimal.ZERO);
        assertJournal(byAccount, "5.1.02", COMPANY_BPJS_KES.multiply(n), BigDecimal.ZERO);
        assertJournal(byAccount, "5.1.03", COMPANY_BPJS_TK.multiply(n), BigDecimal.ZERO);
        assertJournal(byAccount, "2.1.10", BigDecimal.ZERO, netPay);
        assertJournal(byAccount, "2.1.13", BigDecimal.ZERO, totalBpjs);
        assertJournal(byAccount, "2.1.20", BigDecimal.ZERO, totalPph21);
        assertThat(netPay).isEqualByComparingTo(run.getTotalNetPay());
        assertThat(sum(entries, JournalEntry::getDebitAmount)).isEqualByComparingTo(sum(entries, JournalEntry::getCreditAmount));
    }

    @Test
    @DisplayName("Should reject posting payroll that is not approved")
    void shouldRejectPostingUnapprovedPayroll() {
        UUID id = calculatedRun(YearMonth.of(2031, 6));
        openDetail(id);
        assertThat(page.locator("#btn-post")).hasCount(0);

        APIResponse response = postAction(id, "post", FormData.create());

        assertThat(response.url()).isEqualTo(baseUrl() + "/payroll/" + id);
        assertThat(response.text()).contains("Payroll harus dalam status APPROVED untuk di-posting");
        PayrollRun run = payrollRunRepository.findById(id).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(PayrollStatus.CALCULATED);
        assertThat(run.getTransaction()).isNull();
    }

    @Test
    @DisplayName("Should recalculate calculated payroll with new base salary and risk class, and refuse once approved")
    void shouldRecalculatePayroll() {
        UUID id = calculatedRun(YearMonth.of(2031, 7));
        int employeeCount = payrollRunRepository.findById(id).orElseThrow().getEmployeeCount();

        APIResponse response = postAction(id, "recalculate",
            FormData.create().set("baseSalary", "12000000").set("jkkRiskClass", "2"));
        assertThat(response.url()).isEqualTo(baseUrl() + "/payroll/" + id);
        assertThat(response.text()).contains("Payroll berhasil dikalkulasi ulang");

        List<PayrollDetail> details = payrollDetailRepository.findByPayrollRunIdWithEmployee(id);
        assertThat(details).hasSize(employeeCount);
        assertThat(details.stream().map(PayrollDetail::getEmployeeId)).doesNotHaveDuplicates();
        Map<String, PayrollDetail> byNik = byNik(details);
        PayrollDetail dewi = byNik.get(DEWI.nik());
        assertThat(dewi.getGrossSalary()).isEqualByComparingTo("12000000");
        assertThat(dewi.getJkkRiskClass()).isEqualTo(2);
        assertThat(dewi.getBpjsKesCompany()).isEqualByComparingTo("480000");
        assertThat(dewi.getBpjsKesEmployee()).isEqualByComparingTo("120000");
        assertThat(dewi.getBpjsJkk()).isEqualByComparingTo("64800");
        assertThat(dewi.getBpjsJkm()).isEqualByComparingTo("36000");
        assertThat(dewi.getBpjsJhtCompany()).isEqualByComparingTo("444000");
        assertThat(dewi.getBpjsJhtEmployee()).isEqualByComparingTo("240000");
        // JP is capped at the 10,042,300 wage ceiling
        assertThat(dewi.getBpjsJpCompany()).isEqualByComparingTo("200846");
        assertThat(dewi.getBpjsJpEmployee()).isEqualByComparingTo("100423");
        assertThat(dewi.getPph21()).isEqualByComparingTo("480000");
        assertThat(dewi.getNetPay()).isEqualByComparingTo("11059577");
        PayrollDetail budi = byNik.get(BUDI.nik());
        assertThat(budi.getPph21()).isEqualByComparingTo("360000");
        assertThat(budi.getNetPay()).isEqualByComparingTo("11179577");

        PayrollRun run = payrollRunRepository.findById(id).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(PayrollStatus.CALCULATED);
        assertThat(run.getTotalGross()).isEqualByComparingTo(new BigDecimal("12000000").multiply(BigDecimal.valueOf(employeeCount)));
        openDetail(id);
        assertThat(page.locator("[data-testid='detail-row-EMP-002'] td").nth(6)).hasText("Rp 11,059,577");

        payrollService.approvePayroll(id);
        APIResponse rejected = postAction(id, "recalculate",
            FormData.create().set("baseSalary", "15000000").set("jkkRiskClass", "1"));
        assertThat(rejected.text()).contains("Payroll tidak dapat dikalkulasi karena status: APPROVED");
        assertThat(byNik(payrollDetailRepository.findByPayrollRunIdWithEmployee(id)).get(DEWI.nik()).getGrossSalary())
            .isEqualByComparingTo("12000000");
    }

    @Test
    @DisplayName("Should delete draft payroll after confirmation")
    void shouldDeleteDraftPayroll() {
        YearMonth period = YearMonth.of(2031, 8);
        UUID id = payrollService.createPayrollRun(period).getId();
        openDetail(id);
        assertThat(page.locator("[data-testid='status-badge']")).hasText("Draft");
        assertThat(page.locator("#btn-export")).hasCount(0);

        page.onDialog(Dialog::accept); // "Yakin ingin menghapus payroll ini?"
        page.locator("#btn-delete").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/payroll");
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Payroll berhasil dihapus");
        assertThat(page.locator("#payroll-" + id)).hasCount(0);
        assertThat(payrollRunRepository.findByPayrollPeriod(period.toString())).isEmpty();
    }

    @Test
    @DisplayName("Should refuse to delete payroll that is not a draft")
    void shouldRejectDeletingNonDraftPayroll() {
        UUID id = calculatedRun(YearMonth.of(2031, 9));
        openDetail(id);
        assertThat(page.locator("#btn-delete")).hasCount(0);

        APIResponse response = postAction(id, "delete", FormData.create());

        assertThat(response.url()).isEqualTo(baseUrl() + "/payroll");
        assertThat(response.text()).contains("Hanya payroll dengan status DRAFT yang dapat dihapus");
        assertThat(status(id)).isEqualTo(PayrollStatus.CALCULATED);
        assertThat(payrollDetailRepository.findByPayrollRunId(id)).isNotEmpty();
    }

    // ==================== EXPORTS ====================

    @Test
    @DisplayName("Should list summary, PPh 21 and BPJS exports in the export menu")
    void shouldListExportLinks() {
        UUID id = exportRun();
        openDetail(id);

        List<String> exports = List.of("summary/pdf", "summary/excel", "pph21/pdf", "pph21/excel", "bpjs/pdf", "bpjs/excel");
        page.locator("#btn-export").click();
        for (String export : exports) {
            assertThat(page.locator("a[href='/payroll/" + id + "/export/" + export + "']")).isVisible();
        }

        page.locator("#page-title").click();
        for (String export : exports) {
            assertThat(page.locator("a[href='/payroll/" + id + "/export/" + export + "']")).isHidden();
        }
        assertThat(page.locator("[data-testid='detail-row-EMP-002'] a"))
            .hasAttribute("href", "/payroll/" + id + "/payslip/" + employeeUuid(DEWI) + "/pdf");
    }

    @Test
    @DisplayName("Should export payroll summary PDF with every employee's net pay")
    void shouldExportSummaryPdf() {
        UUID id = exportRun();
        String text = pdfText(download(id, "export/summary/pdf", "application/pdf", "rekap-gaji-2034-01.pdf"));

        assertThat(text).contains("Januari 2034");
        for (Expected e : SEEDED_EMPLOYEES) {
            assertThat(text).contains(e.name(), e.nik(), dotted(e.netPay()));
        }
    }

    @Test
    @DisplayName("Should export payroll summary Excel with per-employee deductions and net pay")
    void shouldExportSummaryExcel() {
        UUID id = exportRun();
        Sheet sheet = excelSheet(download(id, "export/summary/excel", XLSX, "rekap-gaji-2034-01.xlsx"), "Rekap Gaji");

        for (Expected e : SEEDED_EMPLOYEES) {
            Row row = rowWithText(sheet, 1, e.name());
            assertThat(row.getCell(2).getStringCellValue()).isEqualTo(e.nik());
            assertThat(row.getCell(3).getNumericCellValue()).isEqualTo(10_000_000d);
            assertThat(row.getCell(4).getNumericCellValue()).isEqualTo(400_000d);
            assertThat(row.getCell(5).getNumericCellValue()).isEqualTo(e.pph21().doubleValue());
            assertThat(row.getCell(6).getNumericCellValue()).isEqualTo(e.deductions().doubleValue());
            assertThat(row.getCell(7).getNumericCellValue()).isEqualTo(e.netPay().doubleValue());
        }
    }

    @Test
    @DisplayName("Should export PPh 21 PDF with every employee's withholding")
    void shouldExportPph21Pdf() {
        UUID id = exportRun();
        String text = pdfText(download(id, "export/pph21/pdf", "application/pdf", "pph21-2034-01.pdf"));

        assertThat(text).contains("Januari 2034");
        for (Expected e : SEEDED_EMPLOYEES) {
            assertThat(text).contains(e.name(), dotted(e.pph21()));
        }
    }

    @Test
    @DisplayName("Should export PPh 21 Excel with NPWP, gross, withholding and PTKP status")
    void shouldExportPph21Excel() {
        UUID id = exportRun();
        Sheet sheet = excelSheet(download(id, "export/pph21/excel", XLSX, "pph21-2034-01.xlsx"), "PPh 21");

        for (Expected e : SEEDED_EMPLOYEES) {
            Row row = rowWithText(sheet, 1, e.name());
            assertThat(row.getCell(2).getStringCellValue()).isEqualTo(e.npwp());
            assertThat(row.getCell(3).getNumericCellValue()).isEqualTo(10_000_000d);
            assertThat(row.getCell(4).getNumericCellValue()).isEqualTo(e.pph21().doubleValue());
        }
        assertThat(rowWithText(sheet, 1, BUDI.name()).getCell(5).getStringCellValue()).isEqualTo("K_2");
        assertThat(rowWithText(sheet, 1, DEWI.name()).getCell(5).getStringCellValue()).isEqualTo("TK_0");
    }

    @Test
    @DisplayName("Should export BPJS PDF with Kesehatan and Ketenagakerjaan contributions")
    void shouldExportBpjsPdf() {
        UUID id = exportRun();
        String text = pdfText(download(id, "export/bpjs/pdf", "application/pdf", "bpjs-2034-01.pdf"));

        assertThat(text).contains("Januari 2034", DEWI.name(), "400.000", "100.000", "24.000", "30.000", "370.000", "200.000");
    }

    @Test
    @DisplayName("Should export BPJS Excel with Kesehatan and Ketenagakerjaan sheets")
    void shouldExportBpjsExcel() {
        UUID id = exportRun();
        byte[] body = download(id, "export/bpjs/excel", XLSX, "bpjs-2034-01.xlsx");

        Row kes = rowWithText(excelSheet(body, "BPJS Kesehatan"), 1, DEWI.name());
        assertThat(kes.getCell(2).getNumericCellValue()).isEqualTo(10_000_000d);
        assertThat(kes.getCell(3).getNumericCellValue()).isEqualTo(400_000d);
        assertThat(kes.getCell(4).getNumericCellValue()).isEqualTo(100_000d);

        Row tk = rowWithText(excelSheet(body, "BPJS Ketenagakerjaan"), 1, DEWI.name());
        assertThat(tk.getCell(3).getNumericCellValue()).isEqualTo(24_000d);
        assertThat(tk.getCell(4).getNumericCellValue()).isEqualTo(30_000d);
        assertThat(tk.getCell(5).getNumericCellValue()).isEqualTo(370_000d);
        assertThat(tk.getCell(6).getNumericCellValue()).isEqualTo(200_000d);
        assertThat(tk.getCell(7).getNumericCellValue()).isEqualTo(200_000d);
        assertThat(tk.getCell(8).getNumericCellValue()).isEqualTo(100_000d);
    }

    @Test
    @DisplayName("Should export payslip PDF for one employee of the run")
    void shouldExportPayslipPdf() {
        UUID id = exportRun();
        String text = pdfText(download(id, "payslip/" + employeeUuid(DEWI) + "/pdf", "application/pdf",
            "slip-gaji-EMP-002-2034-01.pdf"));

        assertThat(text).contains(DEWI.name(), DEWI.nik(), "Januari 2034", "10.000.000", dotted(DEWI.netPay()));
        assertThat(text).doesNotContain(BUDI.name());
    }

    @Test
    @DisplayName("Should reject payslip for an employee not in the run")
    void shouldRejectPayslipForEmployeeNotInRun() {
        UUID id = exportRun();

        APIResponse response = page.request().get(baseUrl() + "/payroll/" + id + "/payslip/" + UUID.randomUUID() + "/pdf");

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.headers().get("content-type")).isEqualTo("application/json");
        assertThat(response.headers()).doesNotContainKey("content-disposition");
    }

    // ==================== BUKTI POTONG 1721-A1 ====================

    @Test
    @DisplayName("Should list yearly PPh 21 totals per employee from posted payroll")
    void shouldListBuktiPotongForPostedPayroll() {
        postedBuktiPotongYear();

        navigateTo("/payroll/bukti-potong?year=" + BUKTI_POTONG_YEAR);
        waitForPageLoad();

        assertThat(page.locator("h3:has-text('Daftar Karyawan')")).hasText("Daftar Karyawan - Tahun " + BUKTI_POTONG_YEAR);
        for (Expected e : SEEDED_EMPLOYEES) {
            Locator cells = page.locator("[data-testid='bukti-potong-row-" + e.nik() + "'] td");
            assertThat(cells.nth(1)).hasText(e.name());
            assertThat(cells.nth(2)).hasText(e.npwp());
            assertThat(cells.nth(3)).hasText("2");
            assertThat(cells.nth(4)).hasText("Rp 20,000,000");
            assertThat(cells.nth(5)).hasText(rupiah(e.pph21().multiply(BigDecimal.TWO)));
            assertThat(cells.nth(6).locator("a"))
                .hasAttribute("href", "/payroll/bukti-potong/" + employeeUuid(e) + "/" + BUKTI_POTONG_YEAR + "/pdf");
        }
    }

    @Test
    @DisplayName("Should download 1721-A1 PDF with the employee's yearly totals")
    void shouldDownloadBuktiPotongPdf() {
        postedBuktiPotongYear();

        APIResponse response = page.request().get(
            baseUrl() + "/payroll/bukti-potong/" + employeeUuid(DEWI) + "/" + BUKTI_POTONG_YEAR + "/pdf");

        assertAttachment(response, "application/pdf", "1721-A1-EMP-002-" + BUKTI_POTONG_YEAR + ".pdf");
        String text = pdfText(response.body());
        assertThat(text).contains(DEWI.name(), DEWI.nik(), String.valueOf(BUKTI_POTONG_YEAR), "20.000.000", "400.000");
    }

    @Test
    @DisplayName("Should show empty state and refuse 1721-A1 for a year without posted payroll")
    void shouldShowEmptyBuktiPotongYear() {
        navigateTo("/payroll/bukti-potong?year=2099");
        waitForPageLoad();

        assertThat(page.locator("[data-testid='bukti-potong-table']")).hasCount(0);
        assertThat(page.getByText("Tidak ada payroll yang di-posting untuk tahun 2099")).isVisible();

        APIResponse response = page.request().get(
            baseUrl() + "/payroll/bukti-potong/" + employeeUuid(DEWI) + "/2099/pdf");
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.headers()).doesNotContainKey("content-disposition");
    }

    @Test
    @DisplayName("Should default bukti potong to the current year and switch year from the selector")
    void shouldSwitchBuktiPotongYear() {
        int currentYear = Year.now().getValue();
        String previousYear = String.valueOf(currentYear - 1);
        navigateTo("/payroll/bukti-potong");
        waitForPageLoad();

        Locator yearSelect = page.locator("select#year-select");
        assertThat(yearSelect).hasValue(String.valueOf(currentYear));
        assertThat(yearSelect.locator("option")).hasText(new String[] {
            String.valueOf(currentYear), previousYear, String.valueOf(currentYear - 2)
        });

        yearSelect.selectOption(previousYear);
        page.waitForURL(baseUrl() + "/payroll/bukti-potong?year=" + previousYear);
        waitForPageLoad();

        assertThat(page.locator("select#year-select")).hasValue(previousYear);
    }

    // ==================== helpers ====================

    private record Expected(String nik, String name, String npwp, BigDecimal pph21, BigDecimal deductions, BigDecimal netPay) {
        Expected(String nik, String name, String npwp, String pph21, String deductions, String netPay) {
            this(nik, name, npwp, new BigDecimal(pph21), new BigDecimal(deductions), new BigDecimal(netPay));
        }
    }

    private UUID calculatedRun(YearMonth period) {
        UUID id = payrollService.createPayrollRun(period).getId();
        payrollService.calculatePayroll(id, BASE_SALARY, 1);
        return id;
    }

    private UUID approvedRun(YearMonth period) {
        UUID id = calculatedRun(period);
        payrollService.approvePayroll(id);
        return id;
    }

    /** Export tests share one calculated run; created by whichever export test runs first. */
    private UUID exportRun() {
        return payrollRunRepository.findByPayrollPeriod(EXPORT_PERIOD.toString())
            .map(PayrollRun::getId)
            .orElseGet(() -> calculatedRun(EXPORT_PERIOD));
    }

    /** Two posted months in BUKTI_POTONG_YEAR; created by whichever bukti potong test runs first. */
    private void postedBuktiPotongYear() {
        for (int month = 1; month <= 2; month++) {
            YearMonth period = YearMonth.of(BUKTI_POTONG_YEAR, month);
            if (payrollRunRepository.findByPayrollPeriod(period.toString()).isEmpty()) {
                UUID id = approvedRun(period);
                payrollService.postPayroll(id);
            }
        }
    }

    private void openDetail(UUID id) {
        navigateTo("/payroll/" + id);
        waitForPageLoad();
        assertThat(page.locator("#page-title")).containsText("Detail Payroll");
    }

    private PayrollStatus status(UUID id) {
        return payrollRunRepository.findById(id).orElseThrow().getStatus();
    }

    private UUID employeeUuid(Expected e) {
        return employeeRepository.findByEmployeeId(e.nik())
            .orElseThrow(() -> new IllegalStateException("Seeded employee missing: " + e.nik()))
            .getId();
    }

    private APIResponse postAction(UUID id, String action, FormData form) {
        return page.request().post(baseUrl() + "/payroll/" + id + "/" + action,
            RequestOptions.create().setHeader(csrfHeader, csrfToken).setForm(form));
    }

    private byte[] download(UUID id, String path, String contentType, String filename) {
        APIResponse response = page.request().get(baseUrl() + "/payroll/" + id + "/" + path);
        assertAttachment(response, contentType, filename);
        return response.body();
    }

    private static void assertAttachment(APIResponse response, String contentType, String filename) {
        assertThat(response.status()).as(response.url()).isEqualTo(200);
        assertThat(response.headers().get("content-type")).isEqualTo(contentType);
        assertThat(response.headers().get("content-disposition")).isEqualTo("attachment; filename=\"" + filename + "\"");
    }

    private static String pdfText(byte[] pdf) {
        try (PdfReader reader = new PdfReader(pdf)) {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder text = new StringBuilder();
            for (int p = 1; p <= reader.getNumberOfPages(); p++) {
                text.append(extractor.getTextFromPage(p)).append('\n');
            }
            return text.toString();
        } catch (IOException e) {
            throw new IllegalStateException("Unreadable PDF", e);
        }
    }

    private static Sheet excelSheet(byte[] xlsx, String name) {
        try {
            XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx));
            Sheet sheet = workbook.getSheet(name);
            if (sheet == null) {
                throw new AssertionError("Sheet '" + name + "' missing");
            }
            return sheet;
        } catch (IOException e) {
            throw new IllegalStateException("Unreadable workbook", e);
        }
    }

    private static Row rowWithText(Sheet sheet, int column, String text) {
        for (Row row : sheet) {
            Cell cell = row.getCell(column);
            if (cell != null && cell.getCellType() == CellType.STRING
                && text.equals(cell.getStringCellValue())) {
                return row;
            }
        }
        throw new AssertionError("No row with '" + text + "' in sheet " + sheet.getSheetName());
    }

    private static void assertJournal(Map<String, JournalEntry> byAccount, String accountCode, BigDecimal debit, BigDecimal credit) {
        JournalEntry entry = byAccount.get(accountCode);
        assertThat(entry).as("Journal line for account " + accountCode).isNotNull();
        assertThat(entry.getDebitAmount()).as("Debit " + accountCode).isEqualByComparingTo(debit);
        assertThat(entry.getCreditAmount()).as("Credit " + accountCode).isEqualByComparingTo(credit);
    }

    private static Map<String, PayrollDetail> byNik(List<PayrollDetail> details) {
        return details.stream().collect(Collectors.toMap(PayrollDetail::getEmployeeId, Function.identity()));
    }

    private static <T> BigDecimal sum(List<T> items, Function<T, BigDecimal> amount) {
        return items.stream().map(amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String rupiah(BigDecimal amount) {
        return "Rp " + String.format(Locale.US, "%,d", amount.longValueExact());
    }

    private static String dotted(BigDecimal amount) {
        return String.format(Locale.US, "%,d", amount.longValueExact()).replace(',', '.');
    }
}
