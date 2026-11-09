package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.dto.JournalEntryRequest;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.PayrollRun;
import com.artivisi.accountingfinance.enums.FiscalAdjustmentStatus;
import com.artivisi.accountingfinance.enums.FiscalTreatment;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.BenefitInKindRepository;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.EmployeeRepository;
import com.artivisi.accountingfinance.repository.FiscalAdjustmentRepository;
import com.artivisi.accountingfinance.service.ChartOfAccountService;
import com.artivisi.accountingfinance.service.PayrollService;
import com.artivisi.accountingfinance.service.TransactionApiService;
import com.artivisi.accountingfinance.service.TransactionService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
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

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Account fiscal treatment, reviewable fiscal adjustment drafts and benefit-in-kind recipients through the
 * web UI. Reserved test year 2130. EMP-002 is TK/0: base 10,000,000 + natura 2,000,000 falls in TER A 4%.
 */
@DisplayName("Fiscal treatment and benefit-in-kind UI")
@Import(ServiceTestDataInitializer.class)
class FiscalTreatmentFunctionalTest extends PlaywrightTestBase {

    private static final int YEAR = 2130;

    @Autowired private ChartOfAccountService chartOfAccountService;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private FiscalAdjustmentRepository adjustmentRepository;
    @Autowired private BenefitInKindRepository benefitInKindRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private PayrollService payrollService;
    @Autowired private TransactionService transactionService;
    @Autowired private TransactionApiService transactionApiService;

    @BeforeEach
    void login() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("Seed pack treatment shows on the account list; editing an expense account persists it")
    void accountFormSetsTreatment() {
        navigateTo("/accounts");
        waitForPageLoad();
        assertThat(page.locator("#fiscal-treatment-5-8-04")).hasText("Tidak dapat dikurangkan");
        assertThat(page.locator("#fiscal-treatment-5-8-03")).hasText("Bersyarat");
        assertThat(page.locator("#fiscal-treatment-5-8-01")).hasText("Dapat dikurangkan");

        ChartOfAccount account = expenseAccount();
        navigateTo("/accounts/" + account.getId() + "/edit");
        waitForPageLoad();
        assertThat(page.locator("#fiscalTreatment")).hasValue("");
        page.locator("#fiscalTreatment").selectOption("CONDITIONAL");
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        assertThat(accountRepository.findById(account.getId()).orElseThrow().getFiscalTreatment())
                .isEqualTo(FiscalTreatment.CONDITIONAL);
        navigateTo("/accounts/" + account.getId() + "/edit");
        waitForPageLoad();
        assertThat(page.locator("#fiscalTreatment")).hasValue("CONDITIONAL");
    }

    @Test
    @DisplayName("Treatment on a non-expense account is rejected with the reason")
    void nonExpenseAccountRejected() {
        ChartOfAccount asset = new ChartOfAccount();
        asset.setAccountCode("1.1.T" + uid());
        asset.setAccountName("Aset Uji Fiskal");
        asset.setParent(accountRepository.findByAccountCode("1.1").orElseThrow());
        chartOfAccountService.create(asset);

        navigateTo("/accounts/" + asset.getId() + "/edit");
        waitForPageLoad();
        page.locator("#fiscalTreatment").selectOption("DEDUCTIBLE");
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        assertThat(page.getByTestId("validation-errors")).containsText("Perlakuan fiskal hanya berlaku untuk akun beban");
        assertThat(accountRepository.findById(asset.getId()).orElseThrow().getFiscalTreatment()).isNull();
    }

    @Test
    @DisplayName("Drafts appear for review, stay out of PKP, and count only after confirmation")
    void reviewDraftsOnReconciliationPage() {
        post("5.8.04", "1000000");
        post("5.8.03", "700000");
        post("5.8.01", "5000000");
        page.onDialog(dialog -> dialog.accept());

        String reconciliation = "/reports/rekonsiliasi-fiskal?year=" + YEAR;
        navigateTo(reconciliation);
        waitForPageLoad();
        assertThat(page.locator("#no-drafts")).isVisible();
        page.locator("#btn-generate-drafts").click();
        waitForPageLoad();

        var nonDeductible = page.locator("[data-testid='draft-5.8.04']");
        var conditional = page.locator("[data-testid='draft-5.8.03']");
        assertThat(nonDeductible.locator("input[name='amount']")).hasValue("1000000.00");
        assertThat(conditional.locator("input[name='amount']")).hasValue("700000.00");
        assertThat(page.locator("[data-testid='draft-5.8.01']")).hasCount(0);
        assertThat(page.locator("#net-adjustment")).hasText("0");
        assertThat(page.locator("#adjustment-items")).not().containsText("5.8.04");

        navigateTo("/reports/spt-checklist?year=" + YEAR);
        waitForPageLoad();
        assertThat(page.locator("[data-testid='draft-adjustment-warning']"))
                .hasText("2 draf koreksi belum ditinjau dan belum masuk perhitungan");

        navigateTo(reconciliation);
        waitForPageLoad();
        nonDeductible.locator("input[name='amount']").fill("900000");
        nonDeductible.locator(".btn-confirm-draft").click();
        waitForPageLoad();
        assertThat(page.locator("#net-adjustment")).hasText("900.000");
        assertThat(page.locator("#adjustment-items")).containsText("5.8.04");
        assertThat(nonDeductible).hasCount(0);

        conditional.locator(".btn-reject-draft").click();
        waitForPageLoad();
        assertThat(conditional).hasCount(0);
        assertThat(page.locator("#net-adjustment")).hasText("900.000");

        var stored = adjustmentRepository.findByYearOrderByAdjustmentCategoryAscDescriptionAsc(YEAR);
        assertThat(stored).hasSize(1);
        assertThat(stored.getFirst().getStatus()).isEqualTo(FiscalAdjustmentStatus.CONFIRMED);
        assertThat(stored.getFirst().getAmount()).isEqualByComparingTo("900000");
    }

    @Test
    @DisplayName("Recipient recorded on a transaction reaches PPh 21 and the 1721-A1 list")
    void recipientFeedsPayrollAndBuktiPotong() {
        var employee = employeeRepository.findByEmployeeId("EMP-002")
                .orElseThrow(() -> new IllegalStateException("Seeded employee EMP-002 missing"));
        UUID transactionId = post("5.8.03", "2000000");

        navigateTo("/transactions/" + transactionId);
        waitForPageLoad();
        page.locator("#benefit-employee").selectOption(employee.getId().toString());
        page.locator("#benefit-amount").fill("2500000");
        page.locator("#benefit-description").fill("Fasilitas mobil");
        page.locator("#btn-add-benefit").click();
        waitForPageLoad();
        assertThat(page.locator("#benefit-error")).containsText("melebihi");
        assertThat(benefitInKindRepository.findByTransactionId(transactionId)).isEmpty();

        page.locator("#benefit-employee").selectOption(employee.getId().toString());
        page.locator("#benefit-amount").fill("2000000");
        page.locator("#benefit-description").fill("Fasilitas mobil");
        page.locator("#btn-add-benefit").click();
        waitForPageLoad();

        var row = page.locator("[data-testid='benefit-row-EMP-002']");
        assertThat(row).containsText("Dewi Lestari");
        assertThat(row).containsText("2.000.000");
        assertThat(row).containsText("Menunggu payroll");
        assertThat(page.getByText("Natura/Kenikmatan", new com.microsoft.playwright.Page.GetByTextOptions().setExact(true))).isVisible();

        PayrollRun run = payrollService.createPayrollRun(YearMonth.of(YEAR, 6));
        payrollService.calculatePayroll(run.getId(), new BigDecimal("10000000"), 1);
        var detail = payrollService.getPayrollDetails(run.getId()).stream()
                .filter(d -> d.getEmployee().getId().equals(employee.getId())).findFirst().orElseThrow();
        assertThat(detail.getPph21()).isEqualByComparingTo("480000");
        payrollService.approvePayroll(run.getId());
        payrollService.postPayroll(run.getId());

        navigateTo("/transactions/" + transactionId);
        waitForPageLoad();
        assertThat(row).containsText(YEAR + "-06");
        assertThat(row.locator("button")).hasCount(0);

        navigateTo("/payroll/bukti-potong?year=" + YEAR);
        waitForPageLoad();
        var buktiPotong = page.locator("[data-testid='bukti-potong-row-" + employee.getEmployeeId() + "']");
        assertThat(buktiPotong.locator("[data-testid='benefit-in-kind']")).hasText("Rp 2,000,000");
        assertThat(buktiPotong.locator("[data-testid='total-bruto']")).hasText("Rp 12,000,000");
    }

    private ChartOfAccount expenseAccount() {
        ChartOfAccount account = new ChartOfAccount();
        account.setAccountCode("5.2.T" + uid());
        account.setAccountName("Beban Uji Fiskal");
        account.setParent(accountRepository.findByAccountCode("5.2").orElseThrow());
        return chartOfAccountService.create(account);
    }

    private UUID post(String accountCode, String amount) {
        BigDecimal value = new BigDecimal(amount);
        UUID account = accountRepository.findByAccountCode(accountCode).orElseThrow().getId();
        UUID bank = accountRepository.findByAccountCode("1.1.02").orElseThrow().getId();
        UUID id = transactionApiService.createJournalEntry(new JournalEntryRequest(LocalDate.of(YEAR, 3, 1),
                "Uji fiskal " + accountCode, null, List.of(
                        new JournalEntryRequest.JournalLineRequest(account, value, BigDecimal.ZERO),
                        new JournalEntryRequest.JournalLineRequest(bank, BigDecimal.ZERO, value))), "admin").transactionId();
        transactionService.post(id, "admin");
        return id;
    }

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
