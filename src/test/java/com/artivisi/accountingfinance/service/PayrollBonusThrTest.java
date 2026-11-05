package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.dto.JournalEntryRequest;
import com.artivisi.accountingfinance.entity.Employee;
import com.artivisi.accountingfinance.entity.EmploymentStatus;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.PayrollAddition;
import com.artivisi.accountingfinance.entity.PayrollAdditionType;
import com.artivisi.accountingfinance.entity.PayrollDetail;
import com.artivisi.accountingfinance.entity.PayrollRun;
import com.artivisi.accountingfinance.entity.PtkpStatus;
import com.artivisi.accountingfinance.entity.Transaction;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bonus and THR in the payroll run. EMP001 is TK/0, TER category A, PTKP 54,000,000; base salary 10,000,000
 * is 2% (200,000). Rates from the lampiran of PP 58/2023, category A.
 * Integration salary account is 5.1.01, PPh 21 liability is 2.1.20. Reserved test years 2155-2162.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("Bonus and THR in payroll")
class PayrollBonusThrTest {

    private static final BigDecimal BASE = new BigDecimal("10000000");

    @Autowired private PayrollService payrollService;
    @Autowired private PayrollReportService payrollReportService;
    @Autowired private BpjsCalculationService bpjsCalculationService;
    @Autowired private BenefitInKindService benefitInKindService;
    @Autowired private TransactionService transactionService;
    @Autowired private TransactionApiService transactionApiService;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("TER rate is looked up on salary + bonus + THR: 15,000,000 at 6% = 900,000; BPJS stays on the base salary")
    void monthlyTerOnAllCashIncome() {
        Employee employee = emp001();
        PayrollRun run = payrollService.createPayrollRun(YearMonth.of(2155, 3));
        payrollService.addAddition(run.getId(), employee.getId(), PayrollAdditionType.BONUS, new BigDecimal("3000000"), "Kinerja");
        payrollService.addAddition(run.getId(), employee.getId(), PayrollAdditionType.THR, new BigDecimal("2000000"), "Idul Fitri");
        payrollService.calculatePayroll(run.getId(), BASE, 1);
        flush();

        PayrollDetail detail = detailOf(run, employee);
        var bpjs = bpjsCalculationService.calculate(BASE, 1);
        assertThat(detail.getBaseSalary()).isEqualByComparingTo(BASE);
        assertThat(detail.getBonus()).isEqualByComparingTo("3000000");
        assertThat(detail.getThr()).isEqualByComparingTo("2000000");
        assertThat(detail.getGrossSalary()).isEqualByComparingTo("15000000");
        assertThat(detail.getPph21()).isEqualByComparingTo("900000");
        assertThat(detail.getBpjsKesEmployee()).isEqualByComparingTo(bpjs.kesehatanEmployee());
        assertThat(detail.getBpjsJhtEmployee()).isEqualByComparingTo(bpjs.jhtEmployee());
        assertThat(detail.getBpjsJpEmployee()).isEqualByComparingTo(bpjs.jpEmployee());
        assertThat(detail.getNetPay()).isEqualByComparingTo(new BigDecimal("15000000")
                .subtract(detail.getTotalEmployeeBpjs()).subtract(new BigDecimal("900000")));
    }

    @Test
    @DisplayName("Natura adds to the TER base, not to cash: 15,000,000 cash + 2,000,000 natura at 8% = 1,360,000")
    void naturaWithBonus() {
        Employee employee = emp001();
        benefit(LocalDate.of(2156, 1, 15), employee, "2000000");
        PayrollRun run = payrollService.createPayrollRun(YearMonth.of(2156, 1));
        payrollService.addAddition(run.getId(), employee.getId(), PayrollAdditionType.BONUS, new BigDecimal("5000000"), null);
        payrollService.calculatePayroll(run.getId(), BASE, 1);
        flush();

        PayrollDetail detail = detailOf(run, employee);
        assertThat(detail.getTaxableGross()).isEqualByComparingTo("17000000");
        assertThat(detail.getGrossSalary()).isEqualByComparingTo("15000000");
        assertThat(detail.getPph21()).isEqualByComparingTo("1360000");
        assertThat(detail.getNetPay()).isEqualByComparingTo(new BigDecimal("15000000")
                .subtract(detail.getTotalEmployeeBpjs()).subtract(new BigDecimal("1360000")));
    }

    @Test
    @DisplayName("December reconciliation: annual 135,000,000 gives tax 5,250,000; Jan-Nov withheld 3,800,000; December 1,450,000")
    void decemberReconciliationCountsBonusAndThr() {
        Employee employee = emp001();
        // Jan-Nov: ten months at 2% = 2,000,000; March 20,000,000 at 9% = 1,800,000
        for (int month = 1; month <= 11; month++) {
            run(2157, month, employee, month == 3 ? PayrollAdditionType.THR : null, "10000000");
        }
        // Annual 120,000,000 + 10,000,000 THR + 5,000,000 bonus = 135,000,000; biaya jabatan 6,000,000;
        // PTKP 54,000,000; PKP 75,000,000: 60,000,000 x 5% + 15,000,000 x 15% = 5,250,000
        PayrollRun december = run(2157, 12, employee, PayrollAdditionType.BONUS, "5000000");

        assertThat(detailOf(december, employee).getGrossSalary()).isEqualByComparingTo("15000000");
        assertThat(detailOf(december, employee).getPph21()).isEqualByComparingTo("1450000");
    }

    @Test
    @DisplayName("A large bonus over-withholds under TER: December returns 43,000,000 and the run journal still balances")
    void overWithheldDecemberPostsAsDebit() {
        Employee employee = emp001();
        // March 510,000,000 at 30% = 153,000,000; ten months at 200,000 = 2,000,000. Withheld 155,000,000.
        for (int month = 1; month <= 11; month++) {
            run(2158, month, employee, month == 3 ? PayrollAdditionType.BONUS : null, "500000000");
        }
        // Annual 620,000,000 - 6,000,000 - 54,000,000 = PKP 560,000,000:
        // 3,000,000 + 28,500,000 + 62,500,000 + 18,000,000 = 112,000,000
        PayrollRun december = run(2158, 12, employee, null, null);
        PayrollDetail detail = detailOf(december, employee);
        assertThat(detail.getPph21()).isEqualByComparingTo("-43000000");
        assertThat(detail.getNetPay()).isEqualByComparingTo(BASE
                .subtract(detail.getTotalEmployeeBpjs()).add(new BigDecimal("43000000")));

        PayrollRun reloaded = payrollService.findById(december.getId()).orElseThrow();
        BigDecimal totalPph21 = reloaded.getTotalPph21();
        assertThat(totalPph21.signum()).as("the refund must exceed the tax of all other employees").isNegative();

        payrollService.approvePayroll(december.getId());
        Transaction journal = payrollService.postPayroll(december.getId()).getTransaction();

        Map<String, BigDecimal[]> byAccount = entriesByAccount(journal);
        assertThat(byAccount.get("2.1.20")[0]).isEqualByComparingTo(totalPph21.negate());
        assertThat(byAccount.get("2.1.20")[1]).isEqualByComparingTo("0");
        assertBalanced(journal);
    }

    @Test
    @DisplayName("1721-A1: salary 120,000,000, bonus and THR 15,000,000, natura 2,000,000, bruto 137,000,000, PPh 21 5,550,000")
    void buktiPotongTotals() throws IOException {
        Employee employee = emp001();
        benefit(LocalDate.of(2159, 1, 15), employee, "2000000");
        // Jan 12,000,000 at 4% = 480,000; March 20,000,000 at 9% = 1,800,000; nine months at 200,000 = 1,800,000
        // Withheld Jan-Nov 4,080,000. Annual 137,000,000 - 6,000,000 - 54,000,000 = PKP 77,000,000:
        // 3,000,000 + 17,000,000 x 15% = 5,550,000. December 1,470,000.
        for (int month = 1; month <= 12; month++) {
            PayrollAdditionType type = month == 3 ? PayrollAdditionType.THR : month == 12 ? PayrollAdditionType.BONUS : null;
            PayrollRun run = run(2159, month, employee, type, month == 3 ? "10000000" : "5000000");
            payrollService.approvePayroll(run.getId());
            payrollService.postPayroll(run.getId());
            flush();
        }

        var summary = payrollService.getYearlyPayrollSummary(employee.getId(), 2159);
        assertThat(summary.getTotalSalary()).isEqualByComparingTo("120000000");
        assertThat(summary.totalBonusAndThr()).isEqualByComparingTo("15000000");
        assertThat(summary.totalGross()).isEqualByComparingTo("135000000");
        assertThat(summary.totalBenefitInKind()).isEqualByComparingTo("2000000");
        assertThat(summary.getTotalBruto()).isEqualByComparingTo("137000000");
        assertThat(summary.totalPph21()).isEqualByComparingTo("5550000");

        try (PdfReader reader = new PdfReader(payrollReportService.generateBuktiPotong1721A1(summary))) {
            String text = new PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(text).contains("8a. Gaji dan TunjanganRp 120.000.000");
            assertThat(text).contains("8b. Natura dan KenikmatanRp 2.000.000");
            assertThat(text).contains("8c. Tantiem, Bonus, Gratifikasi, Jasa Produksi dan THRRp 15.000.000");
            assertThat(text).contains("9. Penghasilan Bruto (8a + 8b + 8c)Rp 137.000.000");
            assertThat(text).contains("16. PPh PASAL 21 YANG TELAH DIPOTONGRp 5.550.000");
        }
    }

    @Test
    @DisplayName("Run journal: salary on 5.1.01, bonus on 5.8.01, THR on 5.8.02, balanced; a run without them has no such lines")
    void runJournalSplitsExpense() {
        Employee employee = emp001();
        PayrollRun plain = payrollService.createPayrollRun(YearMonth.of(2160, 1));
        payrollService.calculatePayroll(plain.getId(), BASE, 1);
        payrollService.approvePayroll(plain.getId());
        Transaction plainJournal = payrollService.postPayroll(plain.getId()).getTransaction();
        flush();
        assertThat(entriesByAccount(plainJournal)).doesNotContainKeys("5.8.01", "5.8.02");
        assertBalanced(plainJournal);

        PayrollRun run = payrollService.createPayrollRun(YearMonth.of(2160, 3));
        payrollService.addAddition(run.getId(), employee.getId(), PayrollAdditionType.BONUS, new BigDecimal("4000000"), null);
        payrollService.addAddition(run.getId(), employee.getId(), PayrollAdditionType.THR, new BigDecimal("6000000"), null);
        payrollService.calculatePayroll(run.getId(), BASE, 1);
        payrollService.approvePayroll(run.getId());
        PayrollRun posted = payrollService.postPayroll(run.getId());
        flush();

        Map<String, BigDecimal[]> byAccount = entriesByAccount(posted.getTransaction());
        BigDecimal salary = posted.getTotalGross().subtract(new BigDecimal("10000000"));
        assertThat(salary).isEqualByComparingTo(BASE.multiply(BigDecimal.valueOf(posted.getEmployeeCount())));
        assertThat(byAccount.get("5.1.01")[0]).isEqualByComparingTo(salary);
        assertThat(byAccount.get("5.8.01")[0]).isEqualByComparingTo("4000000");
        assertThat(byAccount.get("5.8.02")[0]).isEqualByComparingTo("6000000");
        assertThat(byAccount.get("2.1.20")[1]).isEqualByComparingTo(posted.getTotalPph21());
        assertThat(posted.getTransaction().getAmount()).isEqualByComparingTo(posted.getTotalGross());
        assertBalanced(posted.getTransaction());
    }

    @Test
    @DisplayName("Rows survive recalculation; changing them on a CALCULATED run recalculates; APPROVED rejects")
    void additionLifecycle() {
        Employee employee = emp001();
        PayrollRun run = payrollService.createPayrollRun(YearMonth.of(2161, 1));
        payrollService.calculatePayroll(run.getId(), BASE, 2);
        flush();
        assertThat(detailOf(run, employee).getPph21()).isEqualByComparingTo("200000");

        PayrollAddition first = payrollService.addAddition(run.getId(), employee.getId(), PayrollAdditionType.BONUS, new BigDecimal("2000000"), " Target ");
        flush();
        assertThat(first.getDescription()).isEqualTo("Target");
        PayrollDetail recalculated = detailOf(run, employee);
        assertThat(recalculated.getBonus()).isEqualByComparingTo("2000000");
        assertThat(recalculated.getPph21()).isEqualByComparingTo("480000");
        assertThat(recalculated.getJkkRiskClass()).isEqualTo(2);

        payrollService.addAddition(run.getId(), employee.getId(), PayrollAdditionType.BONUS, new BigDecimal("3000000"), null);
        flush();
        assertThat(detailOf(run, employee).getBonus()).isEqualByComparingTo("5000000");

        payrollService.calculatePayroll(run.getId(), BASE, 1);
        flush();
        assertThat(detailOf(run, employee).getBonus()).isEqualByComparingTo("5000000");
        assertThat(payrollService.getAdditions(run.getId())).hasSize(2);

        payrollService.removeAddition(run.getId(), first.getId());
        flush();
        assertThat(detailOf(run, employee).getBonus()).isEqualByComparingTo("3000000");
        assertThat(detailOf(run, employee).getGrossSalary()).isEqualByComparingTo("13000000");

        payrollService.approvePayroll(run.getId());
        assertThatThrownBy(() -> payrollService.addAddition(run.getId(), employee.getId(), PayrollAdditionType.THR, BigDecimal.TEN, null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("APPROVED");
        UUID remaining = payrollService.getAdditions(run.getId()).getFirst().getId();
        assertThatThrownBy(() -> payrollService.removeAddition(run.getId(), remaining))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("APPROVED");
    }

    @Test
    @DisplayName("Rejected input: non-positive amount, inactive employee, a row of another run; income of an employee who became inactive stops the calculation")
    void rejectedInput() {
        Employee employee = emp001();
        PayrollRun run = payrollService.createPayrollRun(YearMonth.of(2161, 2));
        assertThatThrownBy(() -> payrollService.addAddition(run.getId(), employee.getId(), PayrollAdditionType.BONUS, BigDecimal.ZERO, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("lebih dari nol");

        Employee leaver = newEmployee();
        PayrollAddition row = payrollService.addAddition(run.getId(), leaver.getId(), PayrollAdditionType.THR, new BigDecimal("1000000"), null);
        flush();

        PayrollRun other = payrollService.createPayrollRun(YearMonth.of(2161, 3));
        assertThatThrownBy(() -> payrollService.removeAddition(other.getId(), row.getId()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tidak ditemukan");

        Employee resigned = employeeRepository.findById(leaver.getId()).orElseThrow();
        resigned.setEmploymentStatus(EmploymentStatus.RESIGNED);
        employeeRepository.save(resigned);
        flush();
        assertThatThrownBy(() -> payrollService.calculatePayroll(run.getId(), BASE, 1))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(leaver.getName());
        assertThatThrownBy(() -> payrollService.addAddition(run.getId(), leaver.getId(), PayrollAdditionType.BONUS, BigDecimal.TEN, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tidak aktif");
    }

    @Test
    @DisplayName("Posting a bonus through a template without a bonus line fails with the formula name")
    void templateWithoutBonusLineFailsLoudly() {
        Employee employee = emp001();
        PayrollRun run = payrollService.createPayrollRun(YearMonth.of(2162, 1));
        payrollService.addAddition(run.getId(), employee.getId(), PayrollAdditionType.BONUS, new BigDecimal("1000000"), null);
        payrollService.calculatePayroll(run.getId(), BASE, 1);
        payrollService.approvePayroll(run.getId());
        flush();
        entityManager.createNativeQuery("DELETE FROM journal_template_lines WHERE formula = 'bonus'").executeUpdate();
        entityManager.clear();

        assertThatThrownBy(() -> payrollService.postPayroll(run.getId()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("formula bonus");
    }

    private PayrollRun run(int year, int month, Employee employee, PayrollAdditionType type, String amount) {
        PayrollRun run = payrollService.createPayrollRun(YearMonth.of(year, month));
        if (type != null) {
            payrollService.addAddition(run.getId(), employee.getId(), type, new BigDecimal(amount), null);
        }
        payrollService.calculatePayroll(run.getId(), BASE, 1);
        flush();
        return run;
    }

    private Map<String, BigDecimal[]> entriesByAccount(Transaction journal) {
        Map<String, BigDecimal[]> byAccount = new HashMap<>();
        for (JournalEntry entry : transactionService.findByIdWithJournalEntries(journal.getId()).getJournalEntries()) {
            assertThat(entry.getDebitAmount().signum()).isNotNegative();
            assertThat(entry.getCreditAmount().signum()).isNotNegative();
            BigDecimal[] totals = byAccount.computeIfAbsent(entry.getAccount().getAccountCode(),
                    _ -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            totals[0] = totals[0].add(entry.getDebitAmount());
            totals[1] = totals[1].add(entry.getCreditAmount());
        }
        return byAccount;
    }

    private void assertBalanced(Transaction journal) {
        List<JournalEntry> entries = transactionService.findByIdWithJournalEntries(journal.getId()).getJournalEntries();
        assertThat(entries.stream().map(JournalEntry::getDebitAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(entries.stream().map(JournalEntry::getCreditAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private Employee emp001() {
        return employeeRepository.findByEmployeeId("EMP001")
                .orElseThrow(() -> new IllegalStateException("Seeded employee EMP001 missing"));
    }

    private Employee newEmployee() {
        String id = "BT" + UUID.randomUUID().toString().substring(0, 8);
        Employee employee = new Employee();
        employee.setEmployeeId(id);
        employee.setName("Karyawan " + id);
        employee.setHireDate(LocalDate.of(2020, 1, 1));
        employee.setNpwp("123456789012345");
        employee.setPtkpStatus(PtkpStatus.TK_0);
        return employeeRepository.save(employee);
    }

    private void benefit(LocalDate date, Employee employee, String amount) {
        BigDecimal value = new BigDecimal(amount);
        var natura = accountRepository.findByAccountCode("5.8.03").orElseThrow();
        var bank = accountRepository.findByAccountCode("1.1.02").orElseThrow();
        UUID draft = transactionApiService.createJournalEntry(new JournalEntryRequest(date, "Natura " + date, null, List.of(
                new JournalEntryRequest.JournalLineRequest(natura.getId(), value, BigDecimal.ZERO),
                new JournalEntryRequest.JournalLineRequest(bank.getId(), BigDecimal.ZERO, value))), "test").transactionId();
        Transaction posted = transactionService.post(draft, "test");
        benefitInKindService.add(posted.getId(), employee.getId(), value, "Natura");
        flush();
    }

    private PayrollDetail detailOf(PayrollRun run, Employee employee) {
        return payrollService.getPayrollDetails(run.getId()).stream()
                .filter(d -> d.getEmployee().getId().equals(employee.getId()))
                .findFirst().orElseThrow();
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }
}
