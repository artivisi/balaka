package com.artivisi.accountingfinance.functional.service;

import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.PayrollRun;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.functional.page.TransactionFormPage;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.PayrollRunRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.service.PayrollService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Settling payroll liabilities after posting, and the direct salary expense alternative.
 *
 * Posting a payroll run (covered by PayrollControllerFunctionalTest) credits Hutang Gaji (2.1.10),
 * Hutang BPJS (2.1.13) and Hutang PPh 21 (2.1.20). Here those three liabilities are paid from
 * Bank BCA (1.1.02) with the Bayar Hutang Gaji, Bayar Hutang BPJS and Setor PPh 21 templates,
 * and each liability must net to zero. The payroll period 2097-01 and the payment dates in 2097
 * are used by no other test.
 */
@DisplayName("Service Industry - Payroll Lifecycle")
@Import(ServiceTestDataInitializer.class)
class ServicePayrollLifecycleTest extends PlaywrightTestBase {

    private static final YearMonth PAYROLL_PERIOD = YearMonth.of(2097, 1);
    private static final String BANK = "1.1.02";
    private static final String SALARY_PAYABLE = "2.1.10";
    private static final String BPJS_PAYABLE = "2.1.13";
    private static final String PPH21_PAYABLE = "2.1.20";

    @Autowired private PayrollService payrollService;
    @Autowired private PayrollRunRepository payrollRunRepository;
    @Autowired private JournalTemplateRepository templateRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private JournalEntryRepository journalEntryRepository;

    private TransactionFormPage transactionForm;

    @BeforeEach
    void setup() {
        loginAsAdmin();
        transactionForm = new TransactionFormPage(page, baseUrl());
    }

    @Test
    @DisplayName("Should pay net salary, BPJS and PPh 21 of a posted payroll from the bank and clear each liability")
    void shouldSettlePayrollLiabilities() {
        UUID runId = payrollService.createPayrollRun(PAYROLL_PERIOD).getId();
        payrollService.calculatePayroll(runId, new BigDecimal("10000000"), 1);
        payrollService.approvePayroll(runId);
        payrollService.postPayroll(runId);
        PayrollRun run = payrollRunRepository.findById(runId).orElseThrow();
        UUID payrollTransactionId = run.getTransaction().getId();

        navigateTo("/payroll/" + runId);
        waitForPageLoad();
        assertThat(page.locator("[data-testid='status-badge']")).hasText("Posted");
        Transaction payrollTransaction = transactionRepository.findById(payrollTransactionId).orElseThrow();
        assertThat(page.locator("[data-testid='journal-reference'] a")).hasText(payrollTransaction.getTransactionNumber());
        takeManualScreenshot("payroll-lifecycle-detail");

        page.locator("[data-testid='journal-reference'] a").click();
        waitForPageLoad();
        assertThat(page.locator(".journal-entry-row")).hasCount(6);
        takeManualScreenshot("payroll-feature-journal-entry");

        Map<String, BigDecimal> payrollCredits = journalEntryRepository.findByTransactionIdWithAccount(payrollTransactionId)
            .stream().filter(e -> e.getCreditAmount().signum() > 0)
            .collect(Collectors.toMap(e -> e.getAccount().getAccountCode(), JournalEntry::getCreditAmount));
        BigDecimal netPay = payrollCredits.get(SALARY_PAYABLE);
        BigDecimal bpjs = payrollCredits.get(BPJS_PAYABLE);
        BigDecimal pph21 = payrollCredits.get(PPH21_PAYABLE);
        assertEquals(0, run.getTotalNetPay().compareTo(netPay));
        assertEquals(0, run.getTotalPph21().compareTo(pph21));
        // Hutang BPJS = company share + employee share (deductions other than PPh 21)
        assertEquals(0, run.getTotalCompanyBpjs().add(run.getTotalDeductions()).subtract(run.getTotalPph21()).compareTo(bpjs));

        List<UUID> payments = new ArrayList<>();
        payments.add(payFromBank("Bayar Hutang Gaji", "2097-02-01", netPay, "Transfer gaji Januari 2097 ke rekening karyawan",
            "TRF-GAJI-2097-01", SALARY_PAYABLE, "payroll-lifecycle-bayar-gaji"));
        payments.add(payFromBank("Bayar Hutang BPJS", "2097-02-10", bpjs, "Pembayaran BPJS Januari 2097",
            "BPJS-2097-01", BPJS_PAYABLE, "payroll-lifecycle-bayar-bpjs"));
        payments.add(payFromBank("Setor PPh 21", "2097-02-15", pph21, "Penyetoran PPh 21 Januari 2097",
            "PPH21-2097-01", PPH21_PAYABLE, "payroll-lifecycle-setor-pph21"));

        List<JournalEntry> lifecycle = new ArrayList<>(journalEntryRepository.findByTransactionIdWithAccount(payrollTransactionId));
        payments.forEach(id -> lifecycle.addAll(journalEntryRepository.findByTransactionIdWithAccount(id)));
        for (String liability : List.of(SALARY_PAYABLE, BPJS_PAYABLE, PPH21_PAYABLE)) {
            assertEquals(0, BigDecimal.ZERO.compareTo(creditMinusDebit(lifecycle, liability)),
                "Liability " + liability + " not cleared");
        }
        assertEquals(0, netPay.add(bpjs).add(pph21).compareTo(creditMinusDebit(lifecycle, BANK)));
    }

    @Test
    @DisplayName("Should expense a direct salary payment against the bank without creating payroll liabilities")
    void shouldExpenseDirectSalaryPayment() {
        UUID templateId = templateId("Bayar Beban Gaji");

        transactionForm.navigateWithTemplate(templateId)
            .fillDate("2097-03-25")
            .fillAmount("10000000")
            .fillDescription("Gaji kontraktor lepas - tidak ada BPJS/PPh 21")
            .fillReferenceNumber("GAJI-KONTRAKTOR-2097")
            .fillInputs("BANK:" + BANK);
        takeManualScreenshot("simple-salary-payment-form");

        transactionForm.saveAndPost()
            .verifyStatusPosted()
            .verifyJournalEntryCount(2)
            .verifyJournalEntryAccountCode(0, "5.1.01")
            .verifyJournalEntryDebit(0, "10.000.000")
            .verifyJournalEntryAccountCode(1, BANK)
            .verifyJournalEntryCredit(1, "10.000.000");
        takeManualScreenshot("simple-salary-payment-posted");

        Transaction saved = transactionRepository.findById(currentTransactionId()).orElseThrow();
        assertEquals(TransactionStatus.POSTED, saved.getStatus());
        assertEquals(LocalDate.of(2097, 3, 25), saved.getTransactionDate());
        assertEquals("GAJI-KONTRAKTOR-2097", saved.getReferenceNumber());
        List<JournalEntry> entries = journalEntryRepository.findByTransactionIdWithAccount(saved.getId());
        assertEquals(List.of("5.1.01 D10000000 C0", BANK + " D0 C10000000"), lines(entries));
    }

    private UUID payFromBank(String templateName, String date, BigDecimal amount, String description,
                             String reference, String liability, String screenshotPrefix) {
        UUID templateId = templateId(templateName);
        transactionForm.navigateWithTemplate(templateId)
            .fillDate(date)
            .fillAmount(amount.toBigIntegerExact().toString())
            .fillDescription(description)
            .fillReferenceNumber(reference)
            .fillInputs("BANK:" + BANK);
        takeManualScreenshot(screenshotPrefix + "-form");

        String formatted = String.format("%,d", amount.longValueExact()).replace(',', '.');
        transactionForm.saveAndPost()
            .verifyStatusPosted()
            .verifyJournalEntryCount(2)
            .verifyJournalEntryAccountCode(0, liability)
            .verifyJournalEntryDebit(0, formatted)
            .verifyJournalEntryAccountCode(1, BANK)
            .verifyJournalEntryCredit(1, formatted);
        takeManualScreenshot(screenshotPrefix + "-posted");

        Transaction saved = transactionRepository.findById(currentTransactionId()).orElseThrow();
        assertEquals(TransactionStatus.POSTED, saved.getStatus());
        assertEquals(LocalDate.parse(date), saved.getTransactionDate());
        assertEquals(reference, saved.getReferenceNumber());
        assertEquals(templateId, saved.getJournalTemplate().getId());
        long whole = amount.longValueExact();
        assertEquals(List.of(liability + " D" + whole + " C0", BANK + " D0 C" + whole),
            lines(journalEntryRepository.findByTransactionIdWithAccount(saved.getId())));
        return saved.getId();
    }

    private static BigDecimal creditMinusDebit(List<JournalEntry> entries, String accountCode) {
        return entries.stream().filter(e -> e.getAccount().getAccountCode().equals(accountCode))
            .map(e -> e.getCreditAmount().subtract(e.getDebitAmount()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static List<String> lines(List<JournalEntry> entries) {
        return entries.stream()
            .sorted((a, b) -> b.getDebitAmount().compareTo(a.getDebitAmount()))
            .map(e -> e.getAccount().getAccountCode() + " D" + e.getDebitAmount().toBigInteger()
                + " C" + e.getCreditAmount().toBigInteger())
            .toList();
    }

    private UUID currentTransactionId() {
        return UUID.fromString(page.url().substring(page.url().lastIndexOf('/') + 1));
    }

    private UUID templateId(String templateName) {
        return templateRepository.findByTemplateNameAndIsCurrentVersionTrue(templateName)
            .orElseThrow(() -> new IllegalStateException("Seeded template missing: " + templateName))
            .getId();
    }
}
