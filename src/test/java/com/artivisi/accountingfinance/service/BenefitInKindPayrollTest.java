package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.dto.JournalEntryRequest;
import com.artivisi.accountingfinance.entity.BenefitInKind;
import com.artivisi.accountingfinance.entity.Employee;
import com.artivisi.accountingfinance.entity.PayrollDetail;
import com.artivisi.accountingfinance.entity.PayrollRun;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.VoidReason;
import com.artivisi.accountingfinance.repository.BenefitInKindRepository;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.EmployeeRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Benefit-in-kind feeding PPh 21: EMP001 is TK/0, TER category A. Base salary 10,000,000 is 2% (200,000);
 * with 2,000,000 natura the base is 12,000,000 at 4% (480,000). Reserved test years 2113-2118.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("Benefit-in-kind in payroll")
class BenefitInKindPayrollTest {

    private static final BigDecimal BASE = new BigDecimal("10000000");

    @Autowired private BenefitInKindService benefitInKindService;
    @Autowired private BenefitInKindRepository benefitInKindRepository;
    @Autowired private PayrollService payrollService;
    @Autowired private PayrollReportService payrollReportService;
    @Autowired private TransactionService transactionService;
    @Autowired private TransactionApiService transactionApiService;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("Monthly TER is computed on gross plus natura; cash gross and net use cash only")
    void monthlyTerIncludesBenefit() {
        Employee employee = emp001();
        benefit(LocalDate.of(2113, 1, 15), employee, "2000000");

        PayrollDetail detail = calculate(YearMonth.of(2113, 1), employee);

        assertThat(detail.getBenefitInKind()).isEqualByComparingTo("2000000");
        assertThat(detail.getGrossSalary()).isEqualByComparingTo(BASE);
        assertThat(detail.getPph21()).isEqualByComparingTo("480000");
        assertThat(detail.getNetPay()).isEqualByComparingTo(
                BASE.subtract(detail.getTotalEmployeeBpjs()).subtract(new BigDecimal("480000")));
    }

    @Test
    @DisplayName("Without natura the same employee pays the base rate")
    void noBenefitKeepsBaseRate() {
        assertThat(calculate(YearMonth.of(2114, 1), emp001()).getPph21()).isEqualByComparingTo("200000");
    }

    @Test
    @DisplayName("Annual reconciliation counts natura: Jan-Nov 2,480,000 withheld, annual tax 3,210,000")
    void decemberReconciliationIncludesBenefit() {
        Employee employee = emp001();
        benefit(LocalDate.of(2115, 1, 15), employee, "2000000");
        benefit(LocalDate.of(2115, 12, 15), employee, "3000000");

        for (int month = 1; month <= 11; month++) {
            calculate(YearMonth.of(2115, month), employee);
        }
        // Annual base 125,000,000 - 6,000,000 biaya jabatan - 3,600,000 employee JHT + JP (12 x 300,000)
        // - 54,000,000 PTKP = 61,400,000 PKP: 60,000,000 x 5% + 1,400,000 x 15% = 3,210,000
        assertThat(calculate(YearMonth.of(2115, 12), employee).getPph21()).isEqualByComparingTo("730000");
    }

    @Test
    @DisplayName("A row tagged after its month enters the next run once; recalculation does not double it")
    void lateRowEntersNextRunOnce() {
        Employee employee = emp001();
        PayrollDetail january = calculate(YearMonth.of(2116, 1), employee);
        assertThat(january.getBenefitInKind()).isEqualByComparingTo("0");

        BenefitInKind row = benefit(LocalDate.of(2116, 1, 20), employee, "2000000");

        PayrollRun february = payrollService.createPayrollRun(YearMonth.of(2116, 2));
        payrollService.calculatePayroll(february.getId(), BASE, 1);
        flush();
        PayrollDetail first = detailOf(february.getId(), employee);
        assertThat(first.getBenefitInKind()).isEqualByComparingTo("2000000");
        assertThat(benefitInKindRepository.findById(row.getId()).orElseThrow().getPayrollDetail().getId()).isEqualTo(first.getId());

        payrollService.calculatePayroll(february.getId(), BASE, 1);
        flush();
        PayrollDetail second = detailOf(february.getId(), employee);
        assertThat(second.getBenefitInKind()).isEqualByComparingTo("2000000");
        assertThat(benefitInKindRepository.findById(row.getId()).orElseThrow().getPayrollDetail().getId()).isEqualTo(second.getId());

        PayrollRun march = payrollService.createPayrollRun(YearMonth.of(2116, 3));
        payrollService.calculatePayroll(march.getId(), BASE, 1);
        flush();
        assertThat(detailOf(march.getId(), employee).getBenefitInKind()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("A row dated after the period, or in another year, does not enter the run")
    void rowOutsidePeriodStaysPending() {
        Employee employee = emp001();
        benefit(LocalDate.of(2117, 2, 1), employee, "2000000");
        benefit(LocalDate.of(2116, 12, 31), employee, "700000");

        assertThat(calculate(YearMonth.of(2117, 1), employee).getBenefitInKind()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Counted row and its transaction cannot be removed; cancelling the run releases them")
    void countedRowIsGuardedUntilRunCancelled() {
        Employee employee = emp001();
        Transaction transaction = posted(LocalDate.of(2118, 1, 15), "2000000");
        BenefitInKind row = benefitInKindService.add(transaction.getId(), employee.getId(), new BigDecimal("2000000"), "Mobil dinas");
        PayrollRun run = payrollService.createPayrollRun(YearMonth.of(2118, 1));
        payrollService.calculatePayroll(run.getId(), BASE, 1);
        flush();

        assertThatThrownBy(() -> benefitInKindService.delete(row.getId()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("2118-01");
        assertThatThrownBy(() -> transactionService.voidTransaction(transaction.getId(), VoidReason.INPUT_ERROR, "x", "test"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("natura");

        payrollService.cancelPayroll(run.getId(), "Salah hitung");
        flush();
        assertThat(benefitInKindRepository.findById(row.getId()).orElseThrow().getPayrollDetail()).isNull();

        benefitInKindService.delete(row.getId());
        flush();
        assertThat(benefitInKindRepository.findById(row.getId())).isEmpty();
        Transaction reloaded = transactionService.findByIdWithJournalEntries(transaction.getId());
        assertThat(reloaded.getTransactionTags()).isEmpty();
    }

    @Test
    @DisplayName("Voided transaction's unprocessed row no longer enters payroll")
    void voidedRowIsExcluded() {
        Employee employee = emp001();
        Transaction transaction = posted(LocalDate.of(2119, 1, 15), "2000000");
        benefitInKindService.add(transaction.getId(), employee.getId(), new BigDecimal("2000000"), "Voucher");
        transactionService.voidTransaction(transaction.getId(), VoidReason.INPUT_ERROR, "x", "test");
        flush();

        assertThat(calculate(YearMonth.of(2119, 1), employee).getBenefitInKind()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Recording: requires POSTED, cannot exceed the expense debit, adds the NATURA tag once")
    void recordingRules() {
        Employee employee = emp001();
        Transaction transaction = posted(LocalDate.of(2120, 1, 15), "1000000");

        benefitInKindService.add(transaction.getId(), employee.getId(), new BigDecimal("600000"), "Pulsa");
        benefitInKindService.add(transaction.getId(), employee.getId(), new BigDecimal("400000"), "Paket data");
        flush();
        Transaction reloaded = transactionService.findByIdWithJournalEntries(transaction.getId());
        assertThat(reloaded.getTransactionTags()).hasSize(1);
        assertThat(reloaded.getTransactionTags().getFirst().getTag().getCode()).isEqualTo("NATURA");
        assertThat(reloaded.getTransactionTags().getFirst().getTag().getTagType().getCode()).isEqualTo("NATURA");

        assertThatThrownBy(() -> benefitInKindService.add(transaction.getId(), employee.getId(), BigDecimal.ONE, "Lebih"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("melebihi");

        var draft = draft(LocalDate.of(2120, 1, 16), "1000000");
        assertThatThrownBy(() -> benefitInKindService.add(draft, employee.getId(), BigDecimal.ONE, "Draf"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("POSTED");
    }

    @Test
    @DisplayName("1721-A1: bruto includes natura of posted runs; natura outside posted runs is flagged on the PDF")
    void buktiPotongShowsBenefit() throws IOException {
        Employee employee = emp001();
        benefit(LocalDate.of(2121, 1, 15), employee, "2000000");
        PayrollRun run = payrollService.createPayrollRun(YearMonth.of(2121, 1));
        payrollService.calculatePayroll(run.getId(), BASE, 1);
        payrollService.approvePayroll(run.getId());
        payrollService.postPayroll(run.getId());
        benefit(LocalDate.of(2121, 2, 15), employee, "500000");
        flush();

        var summary = payrollService.getYearlyPayrollSummary(employee.getId(), 2121);
        assertThat(summary.totalGross()).isEqualByComparingTo(BASE);
        assertThat(summary.totalBenefitInKind()).isEqualByComparingTo("2000000");
        assertThat(summary.getTotalBruto()).isEqualByComparingTo("12000000");
        assertThat(summary.totalPph21()).isEqualByComparingTo("480000");
        assertThat(summary.unprocessedBenefitInKind()).isEqualByComparingTo("500000");

        byte[] pdf = payrollReportService.generateBuktiPotong1721A1(summary);
        try (PdfReader reader = new PdfReader(pdf)) {
            String text = new PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(text).contains("8b. Natura dan Kenikmatan").contains("9. Penghasilan Bruto");
            assertThat(text).contains("12.000.000");
            assertThat(text).contains("2.000.000");
            assertThat(text).contains("500.000 belum masuk payroll");
        }
    }

    private Employee emp001() {
        return employeeRepository.findByEmployeeId("EMP001")
                .orElseThrow(() -> new IllegalStateException("Seeded employee EMP001 missing"));
    }

    private BenefitInKind benefit(LocalDate date, Employee employee, String amount) {
        Transaction transaction = posted(date, amount);
        BenefitInKind row = benefitInKindService.add(transaction.getId(), employee.getId(), new BigDecimal(amount), "Natura " + date);
        flush();
        return row;
    }

    private Transaction posted(LocalDate date, String amount) {
        return transactionService.post(draft(date, amount), "test");
    }

    private UUID draft(LocalDate date, String amount) {
        BigDecimal value = new BigDecimal(amount);
        var natura = accountRepository.findByAccountCode("5.8.03").orElseThrow();
        var bank = accountRepository.findByAccountCode("1.1.02").orElseThrow();
        return transactionApiService.createJournalEntry(new JournalEntryRequest(date, "Natura " + date, null, List.of(
                new JournalEntryRequest.JournalLineRequest(natura.getId(), value, BigDecimal.ZERO),
                new JournalEntryRequest.JournalLineRequest(bank.getId(), BigDecimal.ZERO, value))), "test").transactionId();
    }

    private PayrollDetail calculate(YearMonth period, Employee employee) {
        PayrollRun run = payrollService.createPayrollRun(period);
        payrollService.calculatePayroll(run.getId(), BASE, 1);
        flush();
        return detailOf(run.getId(), employee);
    }

    private PayrollDetail detailOf(UUID runId, Employee employee) {
        return payrollService.getPayrollDetails(runId).stream()
                .filter(d -> d.getEmployee().getId().equals(employee.getId()))
                .findFirst().orElseThrow();
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }
}
