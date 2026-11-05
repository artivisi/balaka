package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.dto.JournalEntryRequest;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.entity.Vendor;
import com.artivisi.accountingfinance.entity.WithholdingPayment;
import com.artivisi.accountingfinance.enums.AccountType;
import com.artivisi.accountingfinance.enums.NormalBalance;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.VendorRepository;
import com.artivisi.accountingfinance.repository.WithholdingPaymentRepository;
import com.artivisi.accountingfinance.service.ChartOfAccountService;
import com.artivisi.accountingfinance.service.TransactionApiService;
import com.artivisi.accountingfinance.service.TransactionService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Payee master, payment, deposit, report and export through the web UI on the IT service seed pack.
 * Reserved test year 2143.
 */
@DisplayName("Withholding UI")
@Import(ServiceTestDataInitializer.class)
class WithholdingFunctionalTest extends PlaywrightTestBase {

    private static final int YEAR = 2143;

    @Autowired private VendorRepository vendorRepository;
    @Autowired private WithholdingPaymentRepository paymentRepository;
    @Autowired private JournalEntryRepository journalEntryRepository;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private ChartOfAccountService chartOfAccountService;
    @Autowired private TransactionService transactionService;
    @Autowired private TransactionApiService transactionApiService;

    @BeforeEach
    void login() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("Create payee, pay without ID: draft shows 20% higher tax; post; report and deposit reflect it; export has the row")
    void fullFlow() throws IOException {
        String code = "UI-" + UUID.randomUUID().toString().substring(0, 8);
        String name = "Freelancer " + code;

        navigateTo("/vendors/new");
        waitForPageLoad();
        page.locator("#code").fill(code);
        page.locator("#name").fill(name);
        page.locator("#payeeType").selectOption("ORANG_PRIBADI");
        page.locator("#defaultWithholding").selectOption("PPH_21_BUKAN_PEGAWAI");
        page.locator("#defaultTaxObjectCode").selectOption("21-100-09");
        page.locator("#btn-simpan").click();
        waitForPageLoad();
        assertThat(page.locator("#vendor-payee-type")).hasText("Orang pribadi dalam negeri");
        assertThat(page.locator("#vendor-default-withholding")).hasText("PPh 21 Bukan Pegawai");
        Vendor payee = vendorRepository.findByCode(code).orElseThrow();

        navigateTo("/withholding/payments/new");
        waitForPageLoad();
        page.locator("#vendorId").selectOption(payee.getId().toString());
        page.locator("#paymentDate").fill(YEAR + "-02-10");
        page.locator("#grossAmount").fill("10000000");
        page.locator("#expenseAccountId").selectOption(accountId("5.1.22"));
        page.locator("#bankAccountId").selectOption(accountId("1.1.02"));
        page.locator("#description").fill("Honor desain");
        page.locator("#btn-save-payment").click();
        waitForPageLoad();

        assertThat(page.locator("#payment-status")).hasText("DRAFT");
        assertThat(page.locator("#payment-gross")).hasText("10.000.000");
        assertThat(page.locator("#payment-dpp")).hasText("5.000.000");
        assertThat(page.locator("#payment-tax")).hasText("300.000");
        assertThat(page.locator("#payment-net")).hasText("9.700.000");
        assertThat(page.locator("#payment-rate")).hasText("6.00%");
        assertThat(page.locator("#payment-basis")).containsText("lebih tinggi 20%");

        List<WithholdingPayment> rows = paymentRepository.findForPeriod(LocalDate.of(YEAR, 2, 1), LocalDate.of(YEAR, 2, 28), null, payee.getId());
        assertThat(rows).hasSize(1);
        UUID transactionId = rows.get(0).getTransaction().getId();
        assertThat(journalEntryRepository.findByTransactionIdWithAccount(transactionId)).isEmpty();

        navigateTo("/withholding?masa=" + YEAR + "-02");
        waitForPageLoad();
        Locator status = page.locator("#status-PPH_21_BUKAN_PEGAWAI");
        assertThat(status.locator("[data-col='withheld']")).hasText("0");
        assertThat(status.locator("[data-col='due']")).hasText("15/03/" + YEAR);

        navigateTo("/withholding/payments/" + rows.get(0).getId());
        waitForPageLoad();
        page.locator("#btn-post-payment").click();
        waitForPageLoad();
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Pembayaran berhasil diposting");
        assertThat(page.locator("#payment-status")).hasText("POSTED");
        assertThat(page.locator("#btn-post-payment")).hasCount(0);

        Map<String, BigDecimal> journal = journalEntryRepository.findByTransactionIdWithAccount(transactionId).stream()
                .collect(Collectors.toMap(e -> e.getAccount().getAccountCode(), e -> e.getDebitAmount().subtract(e.getCreditAmount()).setScale(0)));
        assertThat(journal).containsExactlyInAnyOrderEntriesOf(Map.of(
                "5.1.22", new BigDecimal("10000000"),
                "1.1.02", new BigDecimal("-9700000"),
                "2.1.20", new BigDecimal("-300000")));

        navigateTo("/withholding?masa=" + YEAR + "-02");
        waitForPageLoad();
        assertThat(status.locator("[data-col='withheld']")).hasText("300.000");
        assertThat(status.locator("[data-col='outstanding']")).hasText("300.000");
        assertThat(status.locator("[data-col='status']")).hasText("Belum jatuh tempo");

        page.locator("#deposit-treatment").selectOption("PPH_21_BUKAN_PEGAWAI");
        page.locator("#deposit-amount").fill("300000");
        page.locator("#deposit-date").fill(YEAR + "-03-10");
        page.locator("#deposit-bank").selectOption(accountId("1.1.02"));
        page.locator("#deposit-ntpn").fill("NTPN-UI");
        page.locator("#btn-save-deposit").click();
        waitForPageLoad();
        assertThat(page.locator("[data-testid='flash-success']")).containsText("Draf setoran tersimpan");
        assertThat(page.locator("[data-col='deposit-status']")).hasText("DRAFT");
        assertThat(status.locator("[data-col='deposited']")).hasText("0");

        page.locator(".btn-post-deposit").click();
        waitForPageLoad();
        assertThat(page.locator("[data-col='deposit-status']")).hasText("POSTED");
        assertThat(status.locator("[data-col='deposited']")).hasText("300.000");
        assertThat(status.locator("[data-col='outstanding']")).hasText("0");
        assertThat(status.locator("[data-col='status']")).hasText("Disetor tepat waktu");

        Download download = page.waitForDownload(() -> page.locator("#btn-export-bp21").click());
        try (InputStream in = Files.newInputStream(download.path()); Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheet("DATA");
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            assertThat(sheet.getRow(1).getCell(6).getStringCellValue()).isEqualTo(name);
            assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isEqualTo("NONE");
            assertThat(sheet.getRow(1).getCell(12).getNumericCellValue()).isEqualTo(300000d);
        }
    }

    @Test
    @DisplayName("Report shows a service payment without PPh detail; the account form sets the classification; unclassified accounts warn")
    void undocumentedServicePayment() {
        LocalDate date = LocalDate.of(YEAR, 6, 10);
        UUID undocumented = postJournal("5.1.22", "1500000", date);

        navigateTo("/withholding?masa=" + YEAR + "-06");
        waitForPageLoad();
        Locator table = page.locator("#table-undocumented");
        assertThat(table.locator("tbody tr")).hasCount(1);
        assertThat(table.locator("tbody tr")).containsText("5.1.22 Beban Jasa Profesional");
        assertThat(table.locator("tbody tr")).containsText("1.500.000");
        assertThat(page.locator("#unclassified-warning")).hasCount(0);
        assertThat(transactionService.findById(undocumented).getStatus()).isEqualTo(TransactionStatus.POSTED);

        // A new expense account starts unclassified: it warns, and classifying it through the form moves it into the table.
        String accountCode = "5.1.9" + UUID.randomUUID().toString().substring(0, 3);
        ChartOfAccount newAccount = new ChartOfAccount();
        newAccount.setAccountCode(accountCode);
        newAccount.setAccountName("Beban Baru " + accountCode);
        newAccount.setAccountType(AccountType.EXPENSE);
        newAccount.setNormalBalance(NormalBalance.DEBIT);
        newAccount.setIsHeader(false);
        chartOfAccountService.create(newAccount);
        postJournal(accountCode, "2000000", LocalDate.of(YEAR, 7, 10));

        navigateTo("/withholding?masa=" + YEAR + "-07");
        waitForPageLoad();
        assertThat(page.locator("#unclassified-warning")).containsText(accountCode);
        assertThat(page.locator("#table-undocumented")).hasCount(0);
        assertThat(page.locator("#undocumented-empty")).isVisible();

        navigateTo("/accounts/" + newAccount.getId() + "/edit");
        waitForPageLoad();
        assertThat(page.locator("#serviceExpense")).hasValue("");
        page.locator("#serviceExpense").selectOption("true");
        page.locator("#btn-simpan").click();
        waitForPageLoad();
        assertThat(accountRepository.findByAccountCode(accountCode).orElseThrow().getServiceExpense()).isTrue();

        navigateTo("/withholding?masa=" + YEAR + "-07");
        waitForPageLoad();
        assertThat(page.locator("#unclassified-warning")).hasCount(0);
        assertThat(page.locator("#table-undocumented tbody tr")).containsText(accountCode);
    }

    @Test
    @DisplayName("Form error stays on the form; a payment outside the payee's type is rejected; sidebar opens the page")
    void formRejectsInvalidTreatment() {
        String code = "UI-" + UUID.randomUUID().toString().substring(0, 8);
        Vendor vendor = new Vendor();
        vendor.setCode(code);
        vendor.setName("Badan " + code);
        vendor.setPayeeType(com.artivisi.accountingfinance.enums.PayeeType.BADAN);
        vendor.setActive(true);
        vendorRepository.save(vendor);

        navigateTo("/dashboard");
        waitForPageLoad();
        page.evaluate("document.querySelector('#nav-withholding').click()");
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Pemotongan PPh");

        page.locator("#btn-new-payment").click();
        waitForPageLoad();
        page.locator("#vendorId").selectOption(vendor.getId().toString());
        page.locator("#treatment").selectOption("PPH_26");
        page.locator("#taxObjectCode").selectOption("27-100-99");
        page.locator("#paymentDate").fill(YEAR + "-09-10");
        page.locator("#grossAmount").fill("1000000");
        page.locator("#expenseAccountId").selectOption(accountId("5.1.22"));
        page.locator("#bankAccountId").selectOption(accountId("1.1.02"));
        page.locator("#description").fill("Salah");
        page.locator("#btn-save-payment").click();
        waitForPageLoad();
        assertThat(page.locator("[data-testid='flash-error']")).containsText("PPh 26 tidak berlaku untuk Badan dalam negeri");
        assertThat(paymentRepository.findForPeriod(LocalDate.of(YEAR, 9, 1), LocalDate.of(YEAR, 9, 30), null, vendor.getId())).isEmpty();
    }

    private UUID postJournal(String code, String amount, LocalDate date) {
        BigDecimal value = new BigDecimal(amount);
        UUID id = transactionApiService.createJournalEntry(new JournalEntryRequest(date, "Jasa " + code, null, List.of(
                new JournalEntryRequest.JournalLineRequest(UUID.fromString(accountId(code)), value, BigDecimal.ZERO),
                new JournalEntryRequest.JournalLineRequest(UUID.fromString(accountId("1.1.02")), BigDecimal.ZERO, value))), "admin").transactionId();
        Transaction posted = transactionService.post(id, "admin");
        return posted.getId();
    }

    private String accountId(String code) {
        return accountRepository.findByAccountCode(code)
                .orElseThrow(() -> new IllegalStateException("Seed account missing: " + code)).getId().toString();
    }
}
