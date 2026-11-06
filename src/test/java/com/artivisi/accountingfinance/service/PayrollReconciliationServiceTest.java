package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.dto.JournalEntryRequest;
import com.artivisi.accountingfinance.entity.AlertEvent;
import com.artivisi.accountingfinance.entity.AlertRule;
import com.artivisi.accountingfinance.entity.Employee;
import com.artivisi.accountingfinance.entity.PayrollAdditionType;
import com.artivisi.accountingfinance.entity.PayrollRun;
import com.artivisi.accountingfinance.entity.PayrollStatus;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.AlertType;
import com.artivisi.accountingfinance.enums.PayrollReconciliationStatus;
import com.artivisi.accountingfinance.enums.VoidReason;
import com.artivisi.accountingfinance.repository.AlertEventRepository;
import com.artivisi.accountingfinance.repository.AlertRuleRepository;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.EmployeeRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Warnings both ways, per-period reconciliation and the unposted-run alert. Integration salary accounts are
 * 5.1.01, 5.8.01 and 5.8.02. Reserved test years 2163-2170 and 2009 (the alert needs an ended period).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("Salary expense against payroll")
class PayrollReconciliationServiceTest {

    private static final BigDecimal BASE = new BigDecimal("10000000");

    @Autowired private PayrollReconciliationService reconciliationService;
    @Autowired private PayrollService payrollService;
    @Autowired private TransactionService transactionService;
    @Autowired private TransactionApiService transactionApiService;
    @Autowired private DocumentPostingService documentPostingService;
    @Autowired private AlertService alertService;
    @Autowired private AlertRuleRepository alertRuleRepository;
    @Autowired private AlertEventRepository alertEventRepository;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("Journal debiting a salary account warns when a run exists, naming account, period, status and a mentioned employee")
    void salaryJournalWarns() {
        Employee employee = emp001();
        approvedRun(YearMonth.of(2163, 1));

        UUID plain = journal(LocalDate.of(2163, 1, 20), "5.1.01", "Gaji Januari", "1000000");
        List<String> warnings = reconciliationService.warningsForTransaction(plain);
        assertThat(warnings).singleElement().satisfies(w -> assertThat(w)
                .contains("5.1.01").contains("2163-01").contains("Approved").contains("karyawan")
                .doesNotContain("disebut"));

        UUID named = journal(LocalDate.of(2163, 1, 21), "5.8.01", "Bonus " + employee.getName(), "1000000");
        assertThat(reconciliationService.warningsForTransaction(named)).singleElement()
                .satisfies(w -> assertThat(w).contains("5.8.01").contains("disebut").contains(employee.getName()));

        // Still warned after posting: the response of POST /{id}/post reads the same method
        transactionService.post(plain, "test");
        flush();
        assertThat(reconciliationService.warningsForTransaction(plain)).hasSize(1);
    }

    @Test
    @DisplayName("A payroll template without fixed salary lines yields a warning naming the misconfiguration; posting and the warning calls do not fail")
    void misconfiguredTemplateWarns() {
        PayrollRun run = approvedRun(YearMonth.of(2200, 1));
        var template = reconciliationService.resolvePayrollTemplate();
        template.getLines().stream()
                .filter(l -> l.getPosition() == com.artivisi.accountingfinance.enums.JournalPosition.DEBIT)
                .filter(l -> l.getFormula().matches(".*\\b(grossSalary|bonus|thr)\\b.*"))
                .forEach(l -> l.setAccount(null));
        flush();

        String expected = "Pemeriksaan beban gaji terhadap payroll tidak dapat dijalankan: template payroll \"" + template.getTemplateName() + "\" "
                + "tidak punya baris debit berakun tetap dengan formula grossSalary, bonus, atau thr. "
                + "Perbaiki template payroll agar transaksi yang mendebit akun gaji terdeteksi.";
        UUID id = journal(LocalDate.of(2200, 1, 20), "5.1.01", "Gaji Januari", "1000000");
        assertThat(reconciliationService.warningsForTransaction(id)).containsExactly(expected);
        assertThat(transactionService.post(id, "test").getStatus()).isEqualTo(com.artivisi.accountingfinance.enums.TransactionStatus.POSTED);
        assertThat(reconciliationService.warningsForRun(run.getId())).containsExactly(expected);
    }

    @Test
    @DisplayName("No warning: other expense account, salary credit, another period, no run, cancelled run, closing entry, voided journal")
    void noFalseWarnings() {
        approvedRun(YearMonth.of(2164, 1));
        PayrollRun cancelled = approvedRun(YearMonth.of(2164, 2));
        payrollService.cancelPayroll(cancelled.getId(), "Batal");
        flush();

        assertThat(reconciliationService.warningsForTransaction(
                journal(LocalDate.of(2164, 1, 5), "5.8.03", "Natura", "1000"))).isEmpty();
        assertThat(reconciliationService.warningsForTransaction(
                journal(LocalDate.of(2164, 3, 5), "5.1.01", "Tanpa payroll", "1000"))).isEmpty();
        assertThat(reconciliationService.warningsForTransaction(
                journal(LocalDate.of(2164, 2, 5), "5.1.01", "Payroll batal", "1000"))).isEmpty();

        var salary = accountRepository.findByAccountCode("5.1.01").orElseThrow();
        var bank = accountRepository.findByAccountCode("1.1.02").orElseThrow();
        UUID credit = transactionApiService.createJournalEntry(new JournalEntryRequest(LocalDate.of(2164, 1, 6), "Koreksi kredit", null, List.of(
                new JournalEntryRequest.JournalLineRequest(bank.getId(), new BigDecimal("1000"), BigDecimal.ZERO),
                new JournalEntryRequest.JournalLineRequest(salary.getId(), BigDecimal.ZERO, new BigDecimal("1000")))), "test").transactionId();
        assertThat(reconciliationService.warningsForTransaction(credit)).isEmpty();

        UUID closing = transactionApiService.createJournalEntry(new JournalEntryRequest(LocalDate.of(2164, 1, 7), "Penutup", "CLOSING", List.of(
                new JournalEntryRequest.JournalLineRequest(salary.getId(), new BigDecimal("1000"), BigDecimal.ZERO),
                new JournalEntryRequest.JournalLineRequest(bank.getId(), BigDecimal.ZERO, new BigDecimal("1000")))), "test").transactionId();
        assertThat(reconciliationService.warningsForTransaction(closing)).isEmpty();

        UUID voided = journal(LocalDate.of(2164, 1, 8), "5.1.01", "Dibatalkan", "1000");
        transactionService.post(voided, "test");
        transactionService.voidTransaction(voided, VoidReason.INPUT_ERROR, "x", "test");
        flush();
        assertThat(reconciliationService.warningsForTransaction(voided)).isEmpty();
    }

    @Test
    @DisplayName("Draft from a template warns before posting: fixed account, and account chosen through a hint via DocumentPostingService")
    void templateDraftsWarn() {
        approvedRun(YearMonth.of(2165, 1));
        var salary = accountRepository.findByAccountCode("5.1.01").orElseThrow();
        var bank = accountRepository.findByAccountCode("1.1.02").orElseThrow();
        var hutang = accountRepository.findByAccountCode("2.1.20").orElseThrow();

        Transaction fixedDraft = new Transaction();
        fixedDraft.setJournalTemplate(reconciliationService.resolvePayrollTemplate());
        fixedDraft.setTransactionDate(LocalDate.of(2165, 1, 15));
        fixedDraft.setAmount(new BigDecimal("1000"));
        fixedDraft.setDescription("Draf memakai template payroll");
        UUID fixedId = transactionService.create(fixedDraft, null).getId();
        flush();
        assertThat(reconciliationService.warningsForTransaction(fixedId)).singleElement()
                .satisfies(w -> assertThat(w).contains("5.1.01"));

        Transaction mapped = documentPostingService.createDraftFromTemplate(DocumentPostingService.DraftRequest.builder()
                .templateName("Pembayaran dengan Pemotongan PPh")
                .date(LocalDate.of(2165, 1, 16))
                .description("Bayar gaji lewat dokumen")
                .amount(new BigDecimal("1000"))
                .hintToAccount(Map.of("BEBAN", salary.getId(), "BANK", bank.getId(), "HUTANG_PPH", hutang.getId()))
                .variables(Map.of("settledAmount", new BigDecimal("1000"), "netAmount", new BigDecimal("900"), "taxAmount", new BigDecimal("100")))
                .createdBy("test")
                .build());
        flush();
        assertThat(reconciliationService.warningsForTransaction(mapped.getId())).singleElement()
                .satisfies(w -> assertThat(w).contains("5.1.01"));
        // The same template with a non-salary expense does not warn
        Transaction other = documentPostingService.createDraftFromTemplate(DocumentPostingService.DraftRequest.builder()
                .templateName("Pembayaran dengan Pemotongan PPh")
                .date(LocalDate.of(2165, 1, 17))
                .description("Bayar jasa")
                .amount(new BigDecimal("1000"))
                .hintToAccount(Map.of("BEBAN", accountRepository.findByAccountCode("5.8.03").orElseThrow().getId(),
                        "BANK", bank.getId(), "HUTANG_PPH", hutang.getId()))
                .variables(Map.of("settledAmount", new BigDecimal("1000"), "netAmount", new BigDecimal("900"), "taxAmount", new BigDecimal("100")))
                .createdBy("test")
                .build());
        flush();
        assertThat(reconciliationService.warningsForTransaction(other.getId())).isEmpty();
    }

    @Test
    @DisplayName("Reverse: run warns for each salary journal outside it, posted or draft; not for its own journal, voided or other months")
    void runWarnsAboutJournalsOutside() {
        PayrollRun run = approvedRun(YearMonth.of(2166, 1));
        assertThat(reconciliationService.warningsForRun(run.getId())).isEmpty();

        UUID posted = journal(LocalDate.of(2166, 1, 10), "5.1.01", "Gaji manual", "7000000");
        Transaction postedTx = transactionService.post(posted, "test");
        journal(LocalDate.of(2166, 1, 11), "5.8.02", "THR manual", "2500000");
        UUID voided = journal(LocalDate.of(2166, 1, 12), "5.1.01", "Salah", "1000");
        transactionService.post(voided, "test");
        transactionService.voidTransaction(voided, VoidReason.INPUT_ERROR, "x", "test");
        journal(LocalDate.of(2166, 2, 1), "5.1.01", "Bulan lain", "1000");
        flush();

        List<String> warnings = reconciliationService.warningsForRun(run.getId());
        assertThat(warnings).hasSize(2);
        assertThat(warnings.get(0)).contains(postedTx.getTransactionNumber()).contains("2166-01-10")
                .contains("Gaji manual").contains("5.1.01").contains("7,000,000");
        assertThat(warnings.get(1)).contains("draf").contains("THR manual").contains("5.8.02").contains("2,500,000");

        PayrollRun postedRun = payrollService.postPayroll(run.getId());
        flush();
        assertThat(reconciliationService.warningsForRun(postedRun.getId())).hasSize(2);
        assertThat(reconciliationService.warningsForTransaction(postedRun.getTransaction().getId())).isEmpty();

        payrollService.cancelPayroll(approvedRun(YearMonth.of(2166, 3)).getId(), "Batal");
        flush();
        journal(LocalDate.of(2166, 3, 1), "5.1.01", "Setelah batal", "1000");
        flush();
        PayrollRun cancelled = payrollService.findByPeriod("2166-03").orElseThrow();
        assertThat(reconciliationService.warningsForRun(cancelled.getId())).isEmpty();
    }

    @Test
    @DisplayName("The issue's case: manual salary journal with an approved run stays visible as RUN_NOT_POSTED with the amount outside payroll")
    void manualJournalWithUnpostedRun() {
        PayrollRun run = approvedRun(YearMonth.of(2167, 1));
        UUID manual = journal(LocalDate.of(2167, 1, 25), "5.1.01", "Gaji manual", "9000000");
        transactionService.post(manual, "test");
        flush();

        var row = reconciliationService.reconcile(YearMonth.of(2167, 1));
        assertThat(row.runId()).isEqualTo(run.getId());
        assertThat(row.runStatus()).isEqualTo(PayrollStatus.APPROVED);
        assertThat(row.status()).isEqualTo(PayrollReconciliationStatus.RUN_NOT_POSTED);
        assertThat(row.ledgerSalaryExpense()).isEqualByComparingTo("9000000");
        assertThat(row.ledgerFromRun()).isEqualByComparingTo("0");
        assertThat(row.ledgerOutsideRun()).isEqualByComparingTo("9000000");
        assertThat(row.difference()).isEqualByComparingTo("9000000");
        assertThat(row.journalsOutsideRun()).singleElement()
                .satisfies(j -> assertThat(j.transactionId()).isEqualTo(manual));
    }

    @Test
    @DisplayName("Posted run alone matches; a manual journal on top shows the double expense as the difference")
    void postedRunMatchesThenDiffers() {
        Employee employee = emp001();
        benefitFreeBonusRun(YearMonth.of(2168, 3), employee);

        var matched = reconciliationService.reconcile(YearMonth.of(2168, 3));
        BigDecimal cashGross = matched.runCashGross();
        assertThat(matched.status()).isEqualTo(PayrollReconciliationStatus.MATCHED);
        assertThat(matched.runBonus()).isEqualByComparingTo("4000000");
        assertThat(matched.runThr()).isEqualByComparingTo("6000000");
        assertThat(matched.runSalary()).isEqualByComparingTo(BASE.multiply(BigDecimal.valueOf(matched.employeeCount())));
        assertThat(cashGross).isEqualByComparingTo(matched.runSalary().add(new BigDecimal("10000000")));
        assertThat(matched.ledgerSalaryExpense()).isEqualByComparingTo(cashGross);
        assertThat(matched.ledgerFromRun()).isEqualByComparingTo(cashGross);
        assertThat(matched.ledgerOutsideRun()).isEqualByComparingTo("0");
        assertThat(matched.difference()).isEqualByComparingTo("0");
        assertThat(matched.ledgerPph21FromRun()).isEqualByComparingTo(matched.runPph21());
        assertThat(matched.runTaxableIncome()).isEqualByComparingTo(cashGross);
        assertThat(matched.journalsOutsideRun()).isEmpty();

        transactionService.post(journal(LocalDate.of(2168, 3, 30), "5.1.01", "Gaji dobel", "5000000"), "test");
        flush();
        var doubled = reconciliationService.reconcile(YearMonth.of(2168, 3));
        assertThat(doubled.status()).isEqualTo(PayrollReconciliationStatus.DIFFERENCE);
        assertThat(doubled.difference()).isEqualByComparingTo("5000000");
        assertThat(doubled.ledgerOutsideRun()).isEqualByComparingTo("5000000");
        assertThat(doubled.ledgerFromRun()).isEqualByComparingTo(cashGross);
    }

    @Test
    @DisplayName("Period without a run, closing entries ignored, credits net off; year totals add up across twelve rows")
    void yearView() {
        // January: posted run. February: ledger only, with a 1,000,000 credit correction. Other months: nothing.
        benefitFreeBonusRun(YearMonth.of(2169, 1), emp001());
        transactionService.post(journal(LocalDate.of(2169, 2, 10), "5.1.01", "Gaji tanpa payroll", "8000000"), "test");
        var salary = accountRepository.findByAccountCode("5.1.01").orElseThrow();
        var bank = accountRepository.findByAccountCode("1.1.02").orElseThrow();
        UUID correction = transactionApiService.createJournalEntry(new JournalEntryRequest(LocalDate.of(2169, 2, 11), "Koreksi", null, List.of(
                new JournalEntryRequest.JournalLineRequest(bank.getId(), new BigDecimal("1000000"), BigDecimal.ZERO),
                new JournalEntryRequest.JournalLineRequest(salary.getId(), BigDecimal.ZERO, new BigDecimal("1000000")))), "test").transactionId();
        transactionService.post(correction, "test");
        UUID closing = transactionApiService.createJournalEntry(new JournalEntryRequest(LocalDate.of(2169, 3, 31), "Penutup", "CLOSING", List.of(
                new JournalEntryRequest.JournalLineRequest(bank.getId(), new BigDecimal("500"), BigDecimal.ZERO),
                new JournalEntryRequest.JournalLineRequest(salary.getId(), BigDecimal.ZERO, new BigDecimal("500")))), "test").transactionId();
        transactionService.post(closing, "test");
        flush();

        var year = reconciliationService.reconcileYear(2169);
        assertThat(year.periods()).hasSize(12);
        var january = year.periods().get(0);
        var february = year.periods().get(1);
        var march = year.periods().get(2);
        assertThat(january.status()).isEqualTo(PayrollReconciliationStatus.MATCHED);
        assertThat(february.status()).isEqualTo(PayrollReconciliationStatus.LEDGER_WITHOUT_RUN);
        assertThat(february.ledgerSalaryExpense()).isEqualByComparingTo("7000000");
        assertThat(february.difference()).isEqualByComparingTo("7000000");
        assertThat(february.runId()).isNull();
        assertThat(march.status()).isEqualTo(PayrollReconciliationStatus.NO_ACTIVITY);
        assertThat(march.ledgerSalaryExpense()).isEqualByComparingTo("0");

        assertThat(year.postedCashGross()).isEqualByComparingTo(january.runCashGross());
        assertThat(year.postedTaxableIncome()).isEqualByComparingTo(january.runTaxableIncome());
        assertThat(year.postedPph21()).isEqualByComparingTo(january.runPph21());
        assertThat(year.ledgerSalaryExpense()).isEqualByComparingTo(january.runCashGross().add(new BigDecimal("7000000")));
        assertThat(year.difference()).isEqualByComparingTo("7000000");
    }

    @Test
    @DisplayName("A voided run journal shows as a negative difference")
    void voidedRunJournal() {
        PayrollRun run = approvedRun(YearMonth.of(2170, 1));
        Transaction journal = payrollService.postPayroll(run.getId()).getTransaction();
        flush();
        transactionService.voidTransaction(journal.getId(), VoidReason.INPUT_ERROR, "x", "test");
        flush();

        var row = reconciliationService.reconcile(YearMonth.of(2170, 1));
        assertThat(row.status()).isEqualTo(PayrollReconciliationStatus.DIFFERENCE);
        assertThat(row.ledgerFromRun()).isEqualByComparingTo("0");
        assertThat(row.difference()).isEqualByComparingTo(row.runCashGross().negate());
    }

    @Test
    @DisplayName("Alert names approved runs past their period; not future periods, not posted runs; threshold shifts the cutoff")
    void unpostedRunAlert() {
        AlertRule rule = onlyRule();
        PayrollRun approved = approvedRun(YearMonth.of(2009, 1));
        PayrollRun posted = approvedRun(YearMonth.of(2009, 2));
        payrollService.postPayroll(posted.getId());
        approvedRun(YearMonth.of(2171, 1));
        flush();

        assertThat(alertService.evaluateAllAlerts()).isEqualTo(1);
        List<AlertEvent> events = alertEventRepository.findByAcknowledgedAtIsNullOrderByTriggeredAtDesc();
        assertThat(events).singleElement().satisfies(e -> {
            assertThat(e.getAlertRule().getId()).isEqualTo(rule.getId());
            assertThat(e.getMessage()).contains(approved.getPayrollPeriod())
                    .doesNotContain(posted.getPayrollPeriod()).doesNotContain("2171-01");
        });

        // After posting, nothing is left of this class's runs
        payrollService.postPayroll(approved.getId());
        alertEventRepository.deleteAll(events);
        flush();
        alertService.evaluateAllAlerts();
        assertThat(alertEventRepository.findByAcknowledgedAtIsNullOrderByTriggeredAtDesc())
                .allSatisfy(e -> assertThat(e.getMessage()).doesNotContain("2009-01"));
    }

    @Test
    @DisplayName("A threshold longer than the time since the period end keeps the alert silent")
    void unpostedRunAlertThreshold() {
        AlertRule rule = onlyRule();
        approvedRun(YearMonth.of(2009, 3));
        rule.setThreshold(new BigDecimal("1000000"));
        alertRuleRepository.save(rule);
        flush();

        assertThat(alertService.evaluateAllAlerts()).isZero();
    }

    private AlertRule onlyRule() {
        AlertRule keep = null;
        for (AlertRule r : alertRuleRepository.findAll()) {
            r.setEnabled(r.getAlertType() == AlertType.PAYROLL_UNPOSTED);
            alertRuleRepository.save(r);
            if (r.getAlertType() == AlertType.PAYROLL_UNPOSTED) {
                keep = r;
            }
        }
        assertThat(keep).as("V011 seeds PAYROLL_UNPOSTED").isNotNull();
        assertThat(keep.getThreshold()).isEqualByComparingTo("0");
        return keep;
    }

    private void benefitFreeBonusRun(YearMonth period, Employee employee) {
        PayrollRun run = payrollService.createPayrollRun(period);
        payrollService.addAddition(run.getId(), employee.getId(), PayrollAdditionType.BONUS, new BigDecimal("4000000"), null);
        payrollService.addAddition(run.getId(), employee.getId(), PayrollAdditionType.THR, new BigDecimal("6000000"), null);
        payrollService.calculatePayroll(run.getId(), BASE, 1);
        payrollService.approvePayroll(run.getId());
        payrollService.postPayroll(run.getId());
        flush();
    }

    private PayrollRun approvedRun(YearMonth period) {
        PayrollRun run = payrollService.createPayrollRun(period);
        payrollService.calculatePayroll(run.getId(), BASE, 1);
        PayrollRun approved = payrollService.approvePayroll(run.getId());
        flush();
        return approved;
    }

    private UUID journal(LocalDate date, String debitAccount, String description, String amount) {
        BigDecimal value = new BigDecimal(amount);
        var debit = accountRepository.findByAccountCode(debitAccount).orElseThrow();
        var bank = accountRepository.findByAccountCode("1.1.02").orElseThrow();
        UUID id = transactionApiService.createJournalEntry(new JournalEntryRequest(date, description, null, List.of(
                new JournalEntryRequest.JournalLineRequest(debit.getId(), value, BigDecimal.ZERO),
                new JournalEntryRequest.JournalLineRequest(bank.getId(), BigDecimal.ZERO, value))), "test").transactionId();
        flush();
        return id;
    }

    private Employee emp001() {
        return employeeRepository.findByEmployeeId("EMP001")
                .orElseThrow(() -> new IllegalStateException("Seeded employee EMP001 missing"));
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }
}
