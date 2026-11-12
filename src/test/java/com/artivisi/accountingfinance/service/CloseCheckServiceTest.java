package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.dto.JournalEntryRequest;
import com.artivisi.accountingfinance.entity.AlertEvent;
import com.artivisi.accountingfinance.entity.AlertRule;
import com.artivisi.accountingfinance.entity.AssetCategory;
import com.artivisi.accountingfinance.entity.AssetDisposal;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.Client;
import com.artivisi.accountingfinance.entity.CompanyBankAccount;
import com.artivisi.accountingfinance.entity.DisposalType;
import com.artivisi.accountingfinance.entity.FixedAsset;
import com.artivisi.accountingfinance.entity.Invoice;
import com.artivisi.accountingfinance.entity.TaxFiling;
import com.artivisi.accountingfinance.entity.TaxTransactionDetail;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.entity.DepreciationMethod;
import com.artivisi.accountingfinance.enums.AccountType;
import com.artivisi.accountingfinance.enums.AlertType;
import com.artivisi.accountingfinance.enums.CloseCheckRole;
import com.artivisi.accountingfinance.enums.InvoiceStatus;
import com.artivisi.accountingfinance.enums.NormalBalance;
import com.artivisi.accountingfinance.enums.TaxFilingStatus;
import com.artivisi.accountingfinance.enums.TaxFilingType;
import com.artivisi.accountingfinance.enums.TaxType;
import com.artivisi.accountingfinance.repository.AlertEventRepository;
import com.artivisi.accountingfinance.repository.AlertRuleRepository;
import com.artivisi.accountingfinance.repository.AssetCategoryRepository;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.ClientRepository;
import com.artivisi.accountingfinance.repository.CompanyBankAccountRepository;
import com.artivisi.accountingfinance.repository.InvoiceRepository;
import com.artivisi.accountingfinance.repository.TaxFilingRepository;
import com.artivisi.accountingfinance.repository.TaxTransactionDetailRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.service.CloseCheckService.CheckCode;
import com.artivisi.accountingfinance.service.CloseCheckService.CheckResult;
import com.artivisi.accountingfinance.service.CloseCheckService.CheckStatus;
import com.artivisi.accountingfinance.service.CloseCheckService.CutOffFinding;
import com.artivisi.accountingfinance.service.CloseCheckService.CutOffSource;
import com.artivisi.accountingfinance.service.CloseCheckService.ProceedsIssue;
import com.artivisi.accountingfinance.service.CloseCheckService.ProceedsWarning;
import com.artivisi.accountingfinance.service.CloseCheckService.TaxCreditCheck;
import com.artivisi.accountingfinance.service.StatementService.CurrentAccountStatement;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Period close checks (issue #50). Every check gets data that must and must not trigger it.
 * Reserved test years (integration DB): 2018-2019 (tax filings stop at 2100), 2194-2198, and 2012 for the alert
 * (it evaluates against today).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("Period close checks")
class CloseCheckServiceTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final String BANK = "1.1.02";

    @Autowired private CloseCheckService closeCheckService;
    @Autowired private StatementService statementService;
    @Autowired private AlertService alertService;
    @Autowired private TransactionService transactionService;
    @Autowired private TransactionApiService transactionApiService;
    @Autowired private FixedAssetService fixedAssetService;
    @Autowired private AssetDisposalService disposalService;
    @Autowired private CompanyConfigService companyConfigService;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private TaxFilingRepository taxFilingRepository;
    @Autowired private TaxTransactionDetailRepository taxDetailRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private CompanyBankAccountRepository bankAccountRepository;
    @Autowired private AssetCategoryRepository categoryRepository;
    @Autowired private AlertRuleRepository alertRuleRepository;
    @Autowired private AlertEventRepository alertEventRepository;
    @Autowired private EntityManager entityManager;

    // ==================== tax credits ====================

    @Test
    @DisplayName("Tax credits: after the 2018 return is filed, a balance not offset by later credits is reported with the remainder")
    void taxCreditsAfterAnnualReturn() {
        ChartOfAccount notCleared = account(AccountType.ASSET, CloseCheckRole.TAX_CREDIT);
        ChartOfAccount partly = account(AccountType.ASSET, CloseCheckRole.TAX_CREDIT);
        ChartOfAccount cleared = account(AccountType.ASSET, CloseCheckRole.TAX_CREDIT);
        ChartOfAccount unclassified = account(AccountType.ASSET, null);
        posted(debit(notCleared, LocalDate.of(2018, 5, 10), "1000000"));
        posted(debit(partly, LocalDate.of(2018, 6, 10), "800000"));
        posted(credit(partly, LocalDate.of(2019, 5, 2), "300000"));
        posted(debit(cleared, LocalDate.of(2018, 7, 10), "500000"));
        posted(credit(cleared, LocalDate.of(2019, 4, 30), "500000"));
        posted(debit(unclassified, LocalDate.of(2018, 7, 10), "700000"));
        credit(notCleared, LocalDate.of(2019, 6, 1), "1000000"); // draft: does not clear
        annualReturn(2018, TaxFilingStatus.FILED, LocalDate.of(2019, 4, 20));
        flush();

        TaxCreditCheck check = closeCheckService.taxCredits(2018, LocalDate.of(2019, 12, 31));
        assertThat(check.filedDate()).isEqualTo(LocalDate.of(2019, 4, 20));
        assertThat(check.uncleared()).filteredOn(b -> mine(b.accountId(), notCleared, partly, cleared, unclassified))
                .extracting(b -> b.accountCode() + " " + b.balanceAtYearEnd().toPlainString() + " "
                        + b.clearedAfterYearEnd().toPlainString() + " " + b.uncleared().toPlainString())
                .containsExactlyInAnyOrder(
                        notCleared.getAccountCode() + " 1000000.00 0 1000000.00",
                        partly.getAccountCode() + " 800000.00 300000.00 500000.00");

        TaxCreditCheck beforeFiling = closeCheckService.taxCredits(2018, LocalDate.of(2019, 4, 19));
        assertThat(beforeFiling.filedDate()).isNull();
        assertThat(beforeFiling.uncleared()).isEmpty();

        CheckResult result = check(closeCheckService.checklistForYear(2019), CheckCode.TAX_CREDIT_UNCLEARED);
        assertThat(result.status()).isEqualTo(CheckStatus.WARNING);
        assertThat(result.message()).isEqualTo("SPT Tahunan PPh Badan 2018 dilaporkan 2019-04-20.");
        assertThat(result.findings()).filteredOn(f -> f.reference().startsWith(notCleared.getAccountCode() + " "))
                .singleElement().satisfies(f -> assertThat(f.amount()).isEqualByComparingTo("1000000"));
    }

    @Test
    @DisplayName("Tax credits: a return not reported (NOT_FILED) leaves the check not applicable")
    void taxCreditsWithoutReportedReturn() {
        ChartOfAccount credit = account(AccountType.ASSET, CloseCheckRole.TAX_CREDIT);
        posted(debit(credit, LocalDate.of(2018, 5, 10), "1000000"));
        annualReturn(2018, TaxFilingStatus.NOT_FILED, null);
        flush();

        assertThat(closeCheckService.taxCredits(2018, LocalDate.of(2019, 12, 31)).filedDate()).isNull();
        CheckResult result = check(closeCheckService.checklistForYear(2019), CheckCode.TAX_CREDIT_UNCLEARED);
        assertThat(result.status()).isEqualTo(CheckStatus.NOT_APPLICABLE);
        assertThat(result.message()).isEqualTo("SPT Tahunan PPh Badan 2018 belum tercatat dilaporkan per 2019-12-31.");
    }

    @Test
    @DisplayName("Tax credits: a return year not before the asOf year is rejected")
    void taxCreditsRejectReturnYearNotBeforeAsOf() {
        assertThatThrownBy(() -> closeCheckService.taxCredits(2019, LocalDate.of(2019, 12, 31)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tahun SPT 2019 harus sebelum tahun asOf 2019");
        assertThatThrownBy(() -> closeCheckService.taxCredits(Integer.MAX_VALUE, LocalDate.of(2019, 12, 31)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== clearing ====================

    @Test
    @DisplayName("Clearing: a balance with no reducing movement in the period is reported; partly or fully cleared ones are not")
    void clearingAccounts() {
        ChartOfAccount stale = account(AccountType.ASSET, CloseCheckRole.CLEARING);
        ChartOfAccount partly = account(AccountType.ASSET, CloseCheckRole.CLEARING);
        ChartOfAccount cleared = account(AccountType.ASSET, CloseCheckRole.CLEARING);
        ChartOfAccount creditStale = account(AccountType.LIABILITY, CloseCheckRole.CLEARING);
        ChartOfAccount notClearing = account(AccountType.ASSET, null);
        posted(debit(stale, LocalDate.of(2194, 1, 10), "400000"));
        posted(debit(stale, LocalDate.of(2194, 3, 10), "100000"));
        posted(debit(partly, LocalDate.of(2194, 2, 10), "400000"));
        posted(credit(partly, LocalDate.of(2194, 3, 10), "100000"));
        posted(debit(cleared, LocalDate.of(2194, 2, 10), "400000"));
        posted(credit(cleared, LocalDate.of(2194, 2, 20), "400000"));
        posted(credit(creditStale, LocalDate.of(2194, 4, 10), "250000"));
        posted(debit(notClearing, LocalDate.of(2194, 4, 10), "250000"));
        flush();

        assertThat(closeCheckService.staleClearingBalances(LocalDate.of(2194, 1, 1), LocalDate.of(2194, 12, 31)))
                .filteredOn(c -> mine(c.accountId(), stale, partly, cleared, creditStale, notClearing))
                .extracting(c -> c.accountCode() + " " + c.balance().toPlainString() + " " + c.lastMovement())
                .containsExactly(
                        stale.getAccountCode() + " 500000.00 2194-03-10",
                        creditStale.getAccountCode() + " -250000.00 2194-04-10");

        // March alone: partly has its credit in March, stale still has none
        assertThat(closeCheckService.staleClearingBalances(LocalDate.of(2194, 3, 1), LocalDate.of(2194, 3, 31)))
                .filteredOn(c -> mine(c.accountId(), stale, partly))
                .extracting(c -> c.accountCode()).containsExactly(stale.getAccountCode());
        // February alone: partly had no credit yet in February
        assertThat(closeCheckService.staleClearingBalances(LocalDate.of(2194, 2, 1), LocalDate.of(2194, 2, 28)))
                .filteredOn(c -> mine(c.accountId(), stale, partly, cleared))
                .extracting(c -> c.accountCode()).containsExactlyInAnyOrder(stale.getAccountCode(), partly.getAccountCode());
    }

    // ==================== unmoved liabilities ====================

    @Test
    @DisplayName("Unmoved liabilities: a balance with no posted line for 6 months is reported; moved or zero ones are not")
    void unmovedLiabilities() {
        assertThat(closeCheckService.configuredUnmovedMonths()).as("V014 seeds LIABILITY_UNMOVED = 6").isEqualTo(6);
        ChartOfAccount unmoved = account(AccountType.LIABILITY, null);
        ChartOfAccount moved = account(AccountType.LIABILITY, null);
        ChartOfAccount settled = account(AccountType.LIABILITY, null);
        posted(credit(unmoved, LocalDate.of(2195, 1, 15), "900000"));
        posted(credit(moved, LocalDate.of(2195, 1, 15), "900000"));
        posted(debit(moved, LocalDate.of(2195, 8, 1), "100000"));
        posted(credit(settled, LocalDate.of(2195, 1, 15), "900000"));
        posted(debit(settled, LocalDate.of(2195, 2, 1), "900000"));
        flush();

        assertThat(closeCheckService.unmovedLiabilities(LocalDate.of(2195, 12, 31), 6))
                .filteredOn(l -> mine(l.accountId(), unmoved, moved, settled))
                .extracting(l -> l.accountCode() + " " + l.balance().toPlainString() + " " + l.lastMovement())
                .containsExactly(unmoved.getAccountCode() + " 900000.00 2195-01-15");
        assertThat(closeCheckService.unmovedLiabilities(LocalDate.of(2195, 7, 14), 6))
                .as("2195-07-14 minus 6 months is 2195-01-14, before the last movement")
                .filteredOn(l -> mine(l.accountId(), unmoved)).isEmpty();
        assertThat(closeCheckService.unmovedLiabilities(LocalDate.of(2195, 7, 15), 6))
                .filteredOn(l -> mine(l.accountId(), unmoved)).hasSize(1);

        CheckResult result = check(closeCheckService.checklistForYear(2195), CheckCode.LIABILITY_UNMOVED);
        assertThat(result.status()).isEqualTo(CheckStatus.WARNING);
        assertThat(result.message()).isEqualTo("Batas 6 bulan tanpa mutasi per 2195-12-31.");
        assertThat(result.findings()).extracting(CloseCheckService.Finding::reference)
                .contains(unmoved.getAccountCode() + " " + unmoved.getAccountName())
                .doesNotContain(moved.getAccountCode() + " " + moved.getAccountName());
    }

    @Test
    @DisplayName("Unmoved liabilities: a fractional or missing threshold is an error, not a default")
    void unmovedMonthsConfiguration() {
        AlertRule rule = alertRuleRepository.findByAlertType(AlertType.LIABILITY_UNMOVED).orElseThrow();
        rule.setThreshold(new BigDecimal("2.50"));
        alertRuleRepository.save(rule);
        flush();
        assertThatThrownBy(() -> closeCheckService.configuredUnmovedMonths())
                .hasMessage("Ambang LIABILITY_UNMOVED harus bilangan bulat bulan minimal 1, tercatat 2.50");
        CheckResult result = check(closeCheckService.checklistForYear(2195), CheckCode.LIABILITY_UNMOVED);
        assertThat(result.status()).isEqualTo(CheckStatus.ERROR);

        alertRuleRepository.delete(alertRuleRepository.findByAlertType(AlertType.LIABILITY_UNMOVED).orElseThrow());
        flush();
        assertThatThrownBy(() -> closeCheckService.configuredUnmovedMonths())
                .hasMessageContaining("LIABILITY_UNMOVED tidak ditemukan");
    }

    @Test
    @DisplayName("LIABILITY_UNMOVED alert names the unmoved account and its last movement")
    void unmovedLiabilityAlert() {
        ChartOfAccount unmoved = account(AccountType.LIABILITY, null);
        posted(credit(unmoved, LocalDate.of(2012, 3, 15), "450000"));
        for (AlertRule r : alertRuleRepository.findAll()) {
            r.setEnabled(r.getAlertType() == AlertType.LIABILITY_UNMOVED);
            alertRuleRepository.save(r);
        }
        for (AlertEvent e : alertEventRepository.findByAcknowledgedAtIsNullOrderByTriggeredAtDesc()) {
            e.setAcknowledgedAt(LocalDateTime.now().minusDays(2));
            e.setTriggeredAt(LocalDateTime.now().minusDays(2));
            alertEventRepository.save(e);
        }
        flush();

        alertService.evaluateAllAlerts();
        flush();

        AlertEvent event = alertEventRepository.findByAcknowledgedAtIsNullOrderByTriggeredAtDesc().stream()
                .filter(e -> e.getAlertRule().getAlertType() == AlertType.LIABILITY_UNMOVED).findFirst()
                .orElseThrow(() -> new AssertionError("LIABILITY_UNMOVED did not fire"));
        assertThat(event.getMessage()).startsWith("Liabilitas tidak bergerak 6 bulan. ")
                .contains(unmoved.getAccountCode() + " " + unmoved.getAccountName() + " saldo 450000.00, mutasi terakhir 2012-03-15");
    }

    // ==================== invoices outside receivables ====================

    @Test
    @DisplayName("Invoices: issued ones not fully debited to receivables by a posted invoice journal are reported")
    void invoicesOutsideReceivables() {
        ChartOfAccount receivable = companyConfigService.getConfig().getReceivableAccount();
        assertThat(receivable).as("integration company config has a receivable account").isNotNull();
        Client client = client();
        Invoice booked = invoice(client, LocalDate.of(2196, 3, 10), InvoiceStatus.SENT);
        Invoice cashBasis = invoice(client, LocalDate.of(2196, 3, 11), InvoiceStatus.PAID);
        Invoice draftJournal = invoice(client, LocalDate.of(2196, 3, 12), InvoiceStatus.SENT);
        Invoice partly = invoice(client, LocalDate.of(2196, 3, 13), InvoiceStatus.PARTIAL);
        Invoice notIssued = invoice(client, LocalDate.of(2196, 3, 14), InvoiceStatus.DRAFT);
        Invoice cancelled = invoice(client, LocalDate.of(2196, 3, 15), InvoiceStatus.CANCELLED);
        Invoice nextPeriod = invoice(client, LocalDate.of(2196, 4, 1), InvoiceStatus.SENT);
        posted(invoiceJournal(booked, receivable, booked.getInvoiceDate(), "1110000"));
        invoiceJournal(draftJournal, receivable, draftJournal.getInvoiceDate(), "1110000");
        posted(invoiceJournal(partly, receivable, partly.getInvoiceDate(), "1000000"));
        flush();

        List<CloseCheckService.InvoiceOutsideReceivables> found =
                closeCheckService.invoicesOutsideReceivables(LocalDate.of(2196, 3, 1), LocalDate.of(2196, 3, 31));
        assertThat(found).filteredOn(i -> i.clientName().equals(client.getName()))
                .extracting(i -> i.invoiceNumber() + " " + i.invoiceTotal().toPlainString() + " " + i.postedToReceivable().toPlainString())
                .containsExactly(
                        cashBasis.getInvoiceNumber() + " 1110000.00 0",
                        draftJournal.getInvoiceNumber() + " 1110000.00 0",
                        partly.getInvoiceNumber() + " 1110000.00 1000000.00");
        assertThat(found).extracting(CloseCheckService.InvoiceOutsideReceivables::invoiceNumber)
                .doesNotContain(booked.getInvoiceNumber(), notIssued.getInvoiceNumber(), cancelled.getInvoiceNumber(),
                        nextPeriod.getInvoiceNumber());
    }

    // ==================== cut-off ====================

    @Test
    @DisplayName("Cut-off: an invoice journal or faktur of January posted in February is reported for January only")
    void cutOff() {
        ChartOfAccount receivable = companyConfigService.getConfig().getReceivableAccount();
        Client client = client();
        Invoice late = invoice(client, LocalDate.of(2197, 1, 20), InvoiceStatus.SENT);
        Invoice onTime = invoice(client, LocalDate.of(2197, 1, 25), InvoiceStatus.SENT);
        Invoice draftLate = invoice(client, LocalDate.of(2197, 1, 26), InvoiceStatus.SENT);
        String lateNumber = posted(invoiceJournal(late, receivable, LocalDate.of(2197, 2, 3), "1110000"));
        posted(invoiceJournal(onTime, receivable, LocalDate.of(2197, 1, 25), "1110000"));
        invoiceJournal(draftLate, receivable, LocalDate.of(2197, 2, 4), "1110000");
        UUID fakturTrx = debit(account(AccountType.ASSET, null), LocalDate.of(2197, 2, 2), "220000");
        fakturDetail(fakturTrx, "010.000-97.00000001", LocalDate.of(2197, 1, 28));
        String fakturNumber = posted(fakturTrx);
        UUID sameMonth = debit(account(AccountType.ASSET, null), LocalDate.of(2197, 1, 29), "220000");
        fakturDetail(sameMonth, "010.000-97.00000002", LocalDate.of(2197, 1, 28));
        posted(sameMonth);
        flush();

        List<CutOffFinding> january = closeCheckService.cutOffExceptions(LocalDate.of(2197, 1, 1), LocalDate.of(2197, 1, 31));
        assertThat(january).filteredOn(c -> c.transactionDate().getYear() == 2197)
                .extracting(c -> c.transactionNumber() + " " + c.source() + " " + c.documentNumber() + " " + c.documentDate())
                .containsExactly(
                        fakturNumber + " " + CutOffSource.FAKTUR + " 010.000-97.00000001 2197-01-28",
                        lateNumber + " " + CutOffSource.INVOICE + " " + late.getInvoiceNumber() + " 2197-01-20");
        assertThat(closeCheckService.cutOffExceptions(LocalDate.of(2197, 2, 1), LocalDate.of(2197, 2, 28)))
                .filteredOn(c -> c.transactionDate().getYear() == 2197).isEmpty();

        CheckResult result = check(closeCheckService.checklistForMonth(java.time.YearMonth.of(2197, 1)), CheckCode.CUT_OFF);
        assertThat(result.status()).isEqualTo(CheckStatus.WARNING);
        assertThat(result.findings()).extracting(CloseCheckService.Finding::description)
                .contains("Invoice " + late.getInvoiceNumber() + " tanggal 2197-01-20", "Faktur pajak 010.000-97.00000001 tanggal 2197-01-28");
    }

    // ==================== asset proceeds ====================

    @Test
    @DisplayName("Asset proceeds: to a shareholder current account or a non-bank account warn; to a company bank account does not")
    void assetProceeds() {
        CompanyBankAccount bank = companyBank();
        ChartOfAccount shareholder = account(AccountType.LIABILITY, CloseCheckRole.SHAREHOLDER_CURRENT);
        ChartOfAccount cash = accountRepository.findByAccountCode("1.1.01").orElseThrow();
        AssetDisposal toBank = sale(bank.getGlAccount(), LocalDate.of(2194, 6, 15));
        AssetDisposal toShareholder = sale(shareholder, LocalDate.of(2194, 6, 16));
        AssetDisposal toCash = sale(cash, LocalDate.of(2194, 6, 17));
        AssetDisposal cancelledToShareholder = sale(shareholder, LocalDate.of(2194, 6, 18));
        disposalService.cancel(cancelledToShareholder.getId());
        flush();

        List<ProceedsWarning> warnings = closeCheckService.assetProceedsWarnings(LocalDate.of(2194, 6, 1), LocalDate.of(2194, 6, 30));
        assertThat(warnings).filteredOn(w -> mineDisposal(w.disposalId(), toBank, toShareholder, toCash, cancelledToShareholder))
                .extracting(w -> w.recordNumber() + " " + w.accountCode() + " " + w.issue() + " " + w.proceeds().toPlainString())
                .containsExactly(
                        toShareholder.getRecordNumber() + " " + shareholder.getAccountCode() + " " + ProceedsIssue.SHAREHOLDER_ACCOUNT + " 3000000.00",
                        toCash.getRecordNumber() + " 1.1.01 " + ProceedsIssue.NOT_COMPANY_BANK_ACCOUNT + " 3000000.00");
        assertThat(closeCheckService.proceedsWarning(disposalService.findById(toBank.getId()))).isEmpty();
        assertThat(closeCheckService.proceedsWarning(disposalService.findById(toShareholder.getId())))
                .get().extracting(ProceedsWarning::issueText).isEqualTo("rekening koran pemegang saham/direksi");
    }

    // ==================== shareholder statement ====================

    @Test
    @DisplayName("Shareholder statement: opening balance, running balance per posted line, totals; drafts excluded")
    void shareholderStatement() {
        ChartOfAccount shareholder = account(AccountType.LIABILITY, CloseCheckRole.SHAREHOLDER_CURRENT);
        posted(credit(shareholder, LocalDate.of(2198, 1, 5), "5000000"));
        posted(debit(shareholder, LocalDate.of(2198, 2, 10), "2000000"));
        debit(shareholder, LocalDate.of(2198, 2, 20), "700000"); // draft
        posted(debit(shareholder, LocalDate.of(2198, 3, 1), "4000000"));
        posted(credit(shareholder, LocalDate.of(2198, 4, 1), "999"));
        flush();

        CurrentAccountStatement statement = statementService.generateShareholderStatement(
                shareholder.getId(), LocalDate.of(2198, 2, 1), LocalDate.of(2198, 3, 31));
        assertThat(statement.openingBalance()).isEqualByComparingTo("5000000");
        assertThat(statement.lines()).extracting(l -> l.date() + " " + l.debit().toPlainString() + " " + l.credit().toPlainString()
                        + " " + l.balance().toPlainString())
                .containsExactly("2198-02-10 2000000.00 0.00 3000000.00", "2198-03-01 4000000.00 0.00 -1000000.00");
        assertThat(statement.totalDebit()).isEqualByComparingTo("6000000");
        assertThat(statement.totalCredit()).isEqualByComparingTo("0");
        assertThat(statement.closingBalance()).isEqualByComparingTo("-1000000");

        ChartOfAccount other = account(AccountType.LIABILITY, null);
        assertThatThrownBy(() -> statementService.generateShareholderStatement(other.getId(), LocalDate.of(2198, 1, 1),
                LocalDate.of(2198, 12, 31))).hasMessageContaining("bukan rekening koran pemegang saham/direksi");
    }

    @Test
    @DisplayName("Account roles: refused on header accounts and on account types the role does not allow")
    void roleValidation() {
        ChartOfAccountService chartService = this.chartService;
        ChartOfAccount liability = newAccount(AccountType.LIABILITY, CloseCheckRole.TAX_CREDIT);
        assertThatThrownBy(() -> chartService.create(liability)).hasMessageContaining("Kredit pajak");
        ChartOfAccount header = newAccount(AccountType.ASSET, CloseCheckRole.CLEARING);
        header.setIsHeader(true);
        assertThatThrownBy(() -> chartService.create(header)).hasMessageContaining("Akun antara");
        ChartOfAccount ok = chartService.create(newAccount(AccountType.ASSET, CloseCheckRole.CLEARING));
        assertThat(accountRepository.findById(ok.getId()).orElseThrow().getCloseCheckRole()).isEqualTo(CloseCheckRole.CLEARING);
    }

    @Autowired private ChartOfAccountService chartService;

    // ==================== helpers ====================

    private CheckResult check(CloseCheckService.CloseChecklist checklist, CheckCode code) {
        return checklist.checks().stream().filter(c -> c.code() == code).findFirst().orElseThrow();
    }

    private static boolean mine(UUID id, ChartOfAccount... accounts) {
        for (ChartOfAccount a : accounts) {
            if (a.getId().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static boolean mineDisposal(UUID id, AssetDisposal... disposals) {
        for (AssetDisposal d : disposals) {
            if (d.getId().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private ChartOfAccount newAccount(AccountType type, CloseCheckRole role) {
        ChartOfAccount a = new ChartOfAccount();
        a.setAccountCode("9.50." + SEQ.incrementAndGet() + "." + UUID.randomUUID().toString().substring(0, 6));
        a.setAccountName("Akun Uji " + a.getAccountCode());
        a.setAccountType(type);
        a.setNormalBalance(type == AccountType.ASSET ? NormalBalance.DEBIT : NormalBalance.CREDIT);
        a.setLevel(1);
        a.setIsHeader(false);
        a.setActive(true);
        a.setCloseCheckRole(role);
        return a;
    }

    private ChartOfAccount account(AccountType type, CloseCheckRole role) {
        return accountRepository.save(newAccount(type, role));
    }

    private JournalEntryRequest.JournalLineRequest line(ChartOfAccount account, String debit, String credit) {
        return new JournalEntryRequest.JournalLineRequest(account.getId(), new BigDecimal(debit), new BigDecimal(credit));
    }

    private UUID debit(ChartOfAccount account, LocalDate date, String amount) {
        return transactionApiService.createJournalEntry(new JournalEntryRequest(date, "Debit " + account.getAccountCode(), null,
                List.of(line(account, amount, "0"), line(bankGl(), "0", amount))), "test").transactionId();
    }

    private UUID credit(ChartOfAccount account, LocalDate date, String amount) {
        return transactionApiService.createJournalEntry(new JournalEntryRequest(date, "Kredit " + account.getAccountCode(), null,
                List.of(line(bankGl(), amount, "0"), line(account, "0", amount))), "test").transactionId();
    }

    private ChartOfAccount bankGl() {
        return accountRepository.findByAccountCode(BANK).orElseThrow();
    }

    private String posted(UUID id) {
        return transactionService.post(id, "test").getTransactionNumber();
    }

    private void annualReturn(int year, TaxFilingStatus status, LocalDate filedDate) {
        TaxFiling filing = new TaxFiling();
        filing.setTaxType(TaxFilingType.PPH_BADAN);
        filing.setPeriodYear(year);
        filing.setStatus(status);
        filing.setFiledDate(filedDate);
        taxFilingRepository.save(filing);
    }

    private Client client() {
        Client client = new Client();
        client.setCode("CC-" + SEQ.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 6));
        client.setName("Klien Tutup Buku " + client.getCode());
        return clientRepository.save(client);
    }

    private Invoice invoice(Client client, LocalDate date, InvoiceStatus status) {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("INV-CC-" + SEQ.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 6));
        invoice.setClient(client);
        invoice.setInvoiceDate(date);
        invoice.setDueDate(date.plusDays(30));
        invoice.setAmount(new BigDecimal("1000000"));
        invoice.setTaxAmount(new BigDecimal("110000"));
        invoice.setStatus(status);
        return invoiceRepository.save(invoice);
    }

    private UUID invoiceJournal(Invoice invoice, ChartOfAccount receivable, LocalDate date, String amount) {
        UUID id = transactionApiService.createJournalEntry(new JournalEntryRequest(date, "Invoice " + invoice.getInvoiceNumber(), null,
                List.of(line(receivable, amount, "0"), line(account(AccountType.REVENUE, null), "0", amount))), "test").transactionId();
        Transaction trx = transactionRepository.findById(id).orElseThrow();
        trx.setSourceDocumentType("INVOICE");
        trx.setSourceDocumentId(invoice.getId());
        transactionRepository.save(trx);
        return id;
    }

    private void fakturDetail(UUID transactionId, String fakturNumber, LocalDate fakturDate) {
        TaxTransactionDetail detail = new TaxTransactionDetail();
        detail.setTransaction(transactionRepository.findById(transactionId).orElseThrow());
        detail.setTaxType(TaxType.PPN_MASUKAN);
        detail.setFakturNumber(fakturNumber);
        detail.setFakturDate(fakturDate);
        detail.setTransactionCode("01");
        detail.setDpp(new BigDecimal("200000"));
        detail.setPpn(new BigDecimal("22000"));
        detail.setCounterpartyName("Pemasok Uji");
        taxDetailRepository.save(detail);
    }

    private CompanyBankAccount companyBank() {
        ChartOfAccount gl = account(AccountType.ASSET, null);
        CompanyBankAccount bank = new CompanyBankAccount();
        bank.setBankName("Bank Tutup Buku");
        bank.setAccountNumber("9950" + SEQ.incrementAndGet() + UUID.randomUUID().toString().substring(0, 4));
        bank.setAccountName("Rekening Perusahaan");
        bank.setGlAccount(gl);
        return bankAccountRepository.save(bank);
    }

    private AssetDisposal sale(ChartOfAccount proceedsAccount, LocalDate date) {
        AssetCategory category = categoryRepository.findByCode("KOMPUTER").orElseThrow();
        FixedAsset asset = new FixedAsset();
        asset.setAssetCode("CC-" + SEQ.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 6));
        asset.setName("Aset " + asset.getAssetCode());
        asset.setCategory(category);
        asset.setPurchaseDate(LocalDate.of(2194, 5, 10));
        asset.setPurchaseCost(new BigDecimal("12000000"));
        asset.setResidualValue(BigDecimal.ZERO);
        asset.setUsefulLifeMonths(48);
        asset.setDepreciationMethod(DepreciationMethod.STRAIGHT_LINE);
        asset.setDepreciationStartDate(LocalDate.of(2194, 6, 1));
        asset.setFundingAccount(bankGl());
        FixedAsset created = fixedAssetService.create(asset);
        Transaction acquisition = transactionRepository.findBySourceDocumentTypeAndSourceDocumentIdOrderByTransactionDateAsc(
                FixedAssetService.ACQUISITION_SOURCE_TYPE, created.getId()).getFirst();
        transactionService.post(acquisition.getId(), "test");
        entityManager.flush();
        return disposalService.create(new AssetDisposalService.CreateDisposal(created.getId(), DisposalType.SOLD, date,
                new BigDecimal("3000000"), proceedsAccount.getId(), "Pembeli", "Tidak terpakai", "test"));
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }
}
