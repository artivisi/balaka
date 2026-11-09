package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.dto.JournalEntryRequest;
import com.artivisi.accountingfinance.entity.Employee;
import com.artivisi.accountingfinance.entity.PayrollDetail;
import com.artivisi.accountingfinance.entity.PayrollRun;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.EmployeeRepository;
import com.artivisi.accountingfinance.service.PayrollService;
import com.artivisi.accountingfinance.service.TransactionApiService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bonus/THR entry, salary-journal warnings and the reconciliation page in the browser on the IT service
 * seed pack. EMP-002 is TK/0, TER category A. Reserved test year 2014: above 2031 it would push
 * PayrollControllerFunctionalTest runs off the first page of the shared payroll list.
 */
@DisplayName("Payroll bonus and reconciliation UI")
@Import(ServiceTestDataInitializer.class)
class PayrollBonusFunctionalTest extends PlaywrightTestBase {

    private static final BigDecimal BASE = new BigDecimal("10000000");

    @Autowired private PayrollService payrollService;
    @Autowired private TransactionApiService transactionApiService;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private EmployeeRepository employeeRepository;

    @BeforeEach
    void login() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("Add bonus and THR on a calculated run: figures recalculate, payslip row columns show them, removal restores; approved run hides the form")
    void additionsOnRunPage() {
        Employee dewi = employeeRepository.findByEmployeeId("EMP-002").orElseThrow();
        PayrollRun run = payrollService.createPayrollRun(YearMonth.of(2014, 1));
        payrollService.calculatePayroll(run.getId(), BASE, 1);

        navigateTo("/payroll/" + run.getId());
        waitForPageLoad();
        Locator row = page.locator("[data-testid='detail-row-EMP-002']");
        assertThat(row.locator("[data-testid='detail-bonus']")).hasText("Rp 0");
        assertThat(row).containsText("200,000");

        addAddition(dewi, "BONUS", "3000000", "Kinerja");
        assertThat(page.locator("[data-testid='success-message']")).containsText("Bonus berhasil ditambahkan");
        addAddition(dewi, "THR", "2000000", "Idul Fitri");

        assertThat(page.locator("[data-testid='addition-row']")).hasCount(2);
        row = page.locator("[data-testid='detail-row-EMP-002']");
        assertThat(row.locator("[data-testid='detail-bonus']")).hasText("Rp 3,000,000");
        assertThat(row.locator("[data-testid='detail-thr']")).hasText("Rp 2,000,000");
        // 15,000,000 at 6%
        assertThat(row).containsText("15,000,000");
        assertThat(row).containsText("900,000");
        PayrollDetail stored = payrollService.getPayrollDetails(run.getId()).stream()
                .filter(d -> d.getEmployee().getId().equals(dewi.getId())).findFirst().orElseThrow();
        assertThat(stored.getPph21()).isEqualByComparingTo("900000");

        page.locator("[data-testid='addition-row']").filter(new Locator.FilterOptions().setHasText("Kinerja"))
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Hapus")).click();
        waitForPageLoad();
        assertThat(page.locator("[data-testid='addition-row']")).hasCount(1);
        row = page.locator("[data-testid='detail-row-EMP-002']");
        assertThat(row.locator("[data-testid='detail-bonus']")).hasText("Rp 0");
        // 12,000,000 at 4%
        assertThat(row).containsText("480,000");

        payrollService.approvePayroll(run.getId());
        navigateTo("/payroll/" + run.getId());
        waitForPageLoad();
        assertThat(page.locator("[data-testid='addition-form']")).hasCount(0);
        assertThat(page.locator("[data-testid='addition-row']")).hasCount(1);
    }

    @Test
    @DisplayName("Salary journal entered through the form warns on its detail page and the run page; reconciliation lists it; posting from the list opens the warning")
    void warningsAndReconciliation() {
        PayrollRun run = payrollService.createPayrollRun(YearMonth.of(2014, 2));
        payrollService.calculatePayroll(run.getId(), BASE, 1);
        payrollService.approvePayroll(run.getId());

        String description = "Gaji manual " + UUID.randomUUID().toString().substring(0, 8);
        navigateTo("/transactions/journal-entry/new");
        waitForPageLoad();
        page.locator("#transactionDate").fill("2014-02-20");
        page.locator("#description").fill(description);
        page.locator("#category").selectOption("ADJUSTING");
        pickAccount(0, "5.1.01");
        page.locator("[data-testid='debit-input-0']").pressSequentially("7000000");
        pickAccount(1, "1.1.02");
        page.locator("[data-testid='credit-input-1']").pressSequentially("7000000");
        page.locator("#btn-save-draft").click();
        page.waitForURL(Pattern.compile(".*/transactions/[0-9a-f-]{36}$"));
        waitForPageLoad();
        String detailUrl = page.url();

        Locator warning = page.locator("[data-testid='salary-warning']");
        assertThat(warning).containsText("5.1.01");
        assertThat(warning).containsText("2014-02");
        assertThat(warning).containsText("Approved");

        navigateTo("/payroll/" + run.getId());
        waitForPageLoad();
        assertThat(page.locator("[data-testid='salary-warning']")).containsText(description);
        assertThat(page.locator("[data-testid='salary-warning']")).containsText("7,000,000");

        navigateTo("/payroll/reconciliation?year=2014");
        waitForPageLoad();
        Locator february = page.locator("[data-testid='period-2014-02']");
        assertThat(february.locator("[data-testid='result']")).hasText("Payroll belum diposting");
        assertThat(february.locator("[data-testid='ledger-outside']")).hasText("0");
        assertThat(page.locator("[data-testid='outside-2014-02']")).containsText(description);

        page.onDialog(dialog -> dialog.accept());
        navigateTo("/transactions?search=" + description.replace(" ", "+"));
        waitForPageLoad();
        page.locator("button[title='Post']:visible").first().click();
        page.waitForURL(detailUrl);
        waitForPageLoad();
        assertThat(page.locator("[data-testid='salary-warning']")).containsText("5.1.01");

        navigateTo("/payroll/reconciliation?year=2014");
        waitForPageLoad();
        february = page.locator("[data-testid='period-2014-02']");
        assertThat(february.locator("[data-testid='ledger-outside']")).hasText("7,000,000");
        assertThat(february.locator("[data-testid='difference']")).hasText("7,000,000");
    }

    @Test
    @DisplayName("Journal on a non-salary account shows no warning; sidebar opens the reconciliation page; posted run matches")
    void noWarningAndMatchedRun() {
        PayrollRun run = payrollService.createPayrollRun(YearMonth.of(2014, 3));
        payrollService.calculatePayroll(run.getId(), BASE, 1);
        payrollService.approvePayroll(run.getId());
        PayrollRun posted = payrollService.postPayroll(run.getId());

        var expense = accountRepository.findByAccountCode("5.8.03").orElseThrow();
        var bank = accountRepository.findByAccountCode("1.1.02").orElseThrow();
        BigDecimal value = new BigDecimal("500000");
        UUID other = transactionApiService.createJournalEntry(new JournalEntryRequest(LocalDate.of(2014, 3, 10), "Beban natura", null, List.of(
                new JournalEntryRequest.JournalLineRequest(expense.getId(), value, BigDecimal.ZERO),
                new JournalEntryRequest.JournalLineRequest(bank.getId(), BigDecimal.ZERO, value))), "admin").transactionId();

        navigateTo("/transactions/" + other);
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Detail Transaksi");
        assertThat(page.locator("[data-testid='salary-warning']")).hasCount(0);

        Transaction journal = posted.getTransaction();
        navigateTo("/transactions/" + journal.getId());
        waitForPageLoad();
        assertThat(page.locator("[data-testid='salary-warning']")).hasCount(0);

        navigateTo("/payroll/reconciliation?year=2014");
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Rekonsiliasi Gaji");
        Locator march = page.locator("[data-testid='period-2014-03']");
        assertThat(march.locator("[data-testid='result']")).hasText("Sesuai");
        assertThat(march.locator("[data-testid='difference']")).hasText("0");
        assertThat(page.locator("[data-testid='period-2014-12'] [data-testid='result']")).hasText("Tidak ada aktivitas");

        navigateTo("/payroll");
        waitForPageLoad();
        page.locator("#nav-group-payroll").click();
        page.locator("#nav-payroll-reconciliation").click();
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/payroll/reconciliation");
    }

    private void addAddition(Employee employee, String type, String amount, String description) {
        page.locator("#addition-employee").selectOption(employee.getId().toString());
        page.locator("#addition-type").selectOption(type);
        page.locator("#addition-amount").fill(amount);
        page.locator("#addition-description").fill(description);
        page.locator("#btn-add-addition").click();
        waitForPageLoad();
    }

    private void pickAccount(int line, String code) {
        Locator input = page.locator("[data-testid='account-select-" + line + "']");
        input.click();
        input.fill(code);
        page.locator("[data-testid='journal-line-" + line + "'] [data-testid='account-picker-result']")
                .filter(new Locator.FilterOptions().setHasText(Pattern.compile("^\\s*" + code.replace(".", "\\.") + "\\s+—")))
                .click();
    }
}
