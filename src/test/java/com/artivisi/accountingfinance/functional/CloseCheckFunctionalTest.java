package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.dto.JournalEntryRequest;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.FiscalPeriod;
import com.artivisi.accountingfinance.entity.TaxTransactionDetail;
import com.artivisi.accountingfinance.enums.AccountType;
import com.artivisi.accountingfinance.enums.CloseCheckRole;
import com.artivisi.accountingfinance.enums.TaxType;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.TaxTransactionDetailRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.service.ChartOfAccountService;
import com.artivisi.accountingfinance.service.FiscalPeriodService;
import com.artivisi.accountingfinance.service.TransactionApiService;
import com.artivisi.accountingfinance.service.TransactionService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Close checklist on the fiscal period and year-end closing pages, the shareholder statement page, the account role
 * field and the disposal warning. IT service pack. Reserved test year 2016.
 */
@DisplayName("Period close checks UI")
@Import(ServiceTestDataInitializer.class)
class CloseCheckFunctionalTest extends PlaywrightTestBase {

    private static final String BANK = "1.1.02";

    @Autowired private TransactionApiService transactionApiService;
    @Autowired private TransactionService transactionService;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private ChartOfAccountService chartOfAccountService;
    @Autowired private TaxTransactionDetailRepository taxDetailRepository;
    @Autowired private FiscalPeriodService fiscalPeriodService;

    @BeforeEach
    void login() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("Fiscal period detail: a March faktur posted in April is a cut-off finding; the tax credit check is not applicable")
    void monthChecklist() {
        UUID trx = journal(account("5.1.07"), account(BANK), LocalDate.of(2016, 4, 2), "Pembelian Maret", "110000");
        TaxTransactionDetail detail = new TaxTransactionDetail();
        detail.setTransaction(transactionRepository.findById(trx).orElseThrow());
        detail.setTaxType(TaxType.PPN_MASUKAN);
        detail.setFakturNumber("010.000-16.00000030");
        detail.setFakturDate(LocalDate.of(2016, 3, 30));
        detail.setTransactionCode("01");
        detail.setDpp(new BigDecimal("100000"));
        detail.setPpn(new BigDecimal("10000"));
        detail.setCounterpartyName("Pemasok UI");
        taxDetailRepository.save(detail);
        String number = posted(trx);
        FiscalPeriod march = fiscalPeriodService.create(2016, 3);

        navigateTo("/fiscal-periods/" + march.getId());
        waitForPageLoad();

        Locator cutOff = page.getByTestId("close-check-CUT_OFF");
        assertThat(cutOff.getByTestId("close-check-status")).hasText("Ada temuan");
        Locator finding = cutOff.getByTestId("close-check-finding").filter(new Locator.FilterOptions().setHasText(number));
        assertThat(finding.getByTestId("finding-description")).hasText("Faktur pajak 010.000-16.00000030 tanggal 2016-03-30");
        assertThat(finding.getByTestId("finding-amount")).hasText("110,000");
        assertThat(finding.locator("a")).hasAttribute("href", "/transactions/" + trx);

        Locator taxCredit = page.getByTestId("close-check-TAX_CREDIT_UNCLEARED");
        assertThat(taxCredit.getByTestId("close-check-status")).hasText("Tidak berlaku");
        assertThat(taxCredit.getByTestId("close-check-message"))
                .hasText("SPT Tahunan PPh Badan 2015 belum tercatat dilaporkan per 2016-03-31.");

        FiscalPeriod april = fiscalPeriodService.create(2016, 4);
        navigateTo("/fiscal-periods/" + april.getId());
        waitForPageLoad();
        assertThat(page.getByTestId("close-check-CUT_OFF").getByTestId("close-check-finding")
                .filter(new Locator.FilterOptions().setHasText(number))).hasCount(0);
    }

    @Test
    @DisplayName("Year-end closing page: a liability credited in January and untouched since is listed for 2016")
    void yearChecklist() {
        ChartOfAccount liability = new ChartOfAccount();
        liability.setAccountCode("2.1.U" + UUID.randomUUID().toString().substring(0, 6));
        liability.setAccountName("Hutang Lama Uji");
        liability.setParent(accountRepository.findByAccountCode("2.1").orElseThrow());
        chartOfAccountService.create(liability);
        posted(journal(account(BANK), liability, LocalDate.of(2016, 1, 10), "Titipan pelanggan", "750000"));

        navigateTo("/reports/fiscal-closing?year=2016");
        waitForPageLoad();

        Locator check = page.getByTestId("close-check-LIABILITY_UNMOVED");
        assertThat(check.getByTestId("close-check-status")).hasText("Ada temuan");
        assertThat(check.getByTestId("close-check-message")).hasText("Batas 6 bulan tanpa mutasi per 2016-12-31.");
        Locator finding = check.getByTestId("close-check-finding")
                .filter(new Locator.FilterOptions().setHasText(liability.getAccountCode() + " Hutang Lama Uji"));
        assertThat(finding.getByTestId("finding-description")).hasText("Mutasi terakhir 2016-01-10");
        assertThat(finding.getByTestId("finding-amount")).hasText("750,000");
    }

    @Test
    @DisplayName("Shareholder statement page shows the seeded 2.1.50 lines with running balance")
    void shareholderStatementPage() {
        ChartOfAccount shareholder = account("2.1.50");
        posted(journal(account(BANK), shareholder, LocalDate.of(2016, 2, 1), "Setoran direktur", "3000000"));
        posted(journal(shareholder, account(BANK), LocalDate.of(2016, 5, 1), "Pengembalian ke direktur", "1000000"));

        navigateTo("/statements/shareholder");
        waitForPageLoad();
        page.getByTestId("shareholder-account").selectOption(shareholder.getId().toString());
        page.getByTestId("date-from").fill("2016-01-01");
        page.getByTestId("date-to").fill("2016-12-31");
        page.getByTestId("btn-show-statement").click();
        waitForPageLoad();

        assertThat(page.getByTestId("statement-account-name")).hasText("Rekening Koran Pemegang Saham/Direksi");
        assertThat(page.getByTestId("opening-amount")).hasText("0");
        Locator lines = page.getByTestId("statement-line");
        assertThat(lines).hasCount(2);
        assertThat(lines.nth(0).getByTestId("line-credit")).hasText("3,000,000");
        assertThat(lines.nth(0).getByTestId("line-balance")).hasText("3,000,000");
        assertThat(lines.nth(1).getByTestId("line-debit")).hasText("1,000,000");
        assertThat(lines.nth(1).getByTestId("line-balance")).hasText("2,000,000");
        assertThat(page.getByTestId("closing-amount")).hasText("2,000,000");
    }

    @Test
    @DisplayName("Account form sets the close check role; a role the account type does not allow is rejected")
    void accountRole() {
        ChartOfAccount asset = new ChartOfAccount();
        asset.setAccountCode("1.1.C" + UUID.randomUUID().toString().substring(0, 6));
        asset.setAccountName("Akun Antara Uji");
        asset.setParent(accountRepository.findByAccountCode("1.1").orElseThrow());
        chartOfAccountService.create(asset);

        navigateTo("/accounts/" + asset.getId() + "/edit");
        waitForPageLoad();
        assertThat(page.locator("#closeCheckRole")).hasValue("");
        page.locator("#closeCheckRole").selectOption("CLEARING");
        page.locator("#btn-simpan").click();
        waitForPageLoad();
        assertThat(accountRepository.findById(asset.getId()).orElseThrow().getCloseCheckRole()).isEqualTo(CloseCheckRole.CLEARING);

        ChartOfAccount liability = new ChartOfAccount();
        liability.setAccountCode("2.1.C" + UUID.randomUUID().toString().substring(0, 6));
        liability.setAccountName("Liabilitas Uji Peran");
        liability.setParent(accountRepository.findByAccountCode("2.1").orElseThrow());
        chartOfAccountService.create(liability);
        assertThat(liability.getAccountType()).isEqualTo(AccountType.LIABILITY);
        navigateTo("/accounts/" + liability.getId() + "/edit");
        waitForPageLoad();
        page.locator("#closeCheckRole").selectOption("TAX_CREDIT");
        page.locator("#btn-simpan").click();
        waitForPageLoad();
        assertThat(page.getByTestId("validation-errors")).containsText("Peran pemeriksaan tutup buku Kredit pajak tidak berlaku");
        assertThat(accountRepository.findById(liability.getId()).orElseThrow().getCloseCheckRole()).isNull();
    }

    private ChartOfAccount account(String code) {
        return accountRepository.findByAccountCode(code).orElseThrow(() -> new IllegalStateException("Seeded account missing: " + code));
    }

    private UUID journal(ChartOfAccount debit, ChartOfAccount credit, LocalDate date, String description, String amount) {
        return transactionApiService.createJournalEntry(new JournalEntryRequest(date, description, null, List.of(
                new JournalEntryRequest.JournalLineRequest(debit.getId(), new BigDecimal(amount), BigDecimal.ZERO),
                new JournalEntryRequest.JournalLineRequest(credit.getId(), BigDecimal.ZERO, new BigDecimal(amount)))), "admin")
                .transactionId();
    }

    private String posted(UUID id) {
        return transactionService.post(id, "admin").getTransactionNumber();
    }
}
