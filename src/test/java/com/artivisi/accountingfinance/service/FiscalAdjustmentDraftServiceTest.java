package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.dto.JournalEntryRequest;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.Employee;
import com.artivisi.accountingfinance.entity.FiscalAdjustment;
import com.artivisi.accountingfinance.entity.Tag;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.AccountType;
import com.artivisi.accountingfinance.enums.FiscalAdjustmentCategory;
import com.artivisi.accountingfinance.enums.FiscalAdjustmentDirection;
import com.artivisi.accountingfinance.enums.FiscalAdjustmentSource;
import com.artivisi.accountingfinance.enums.FiscalAdjustmentStatus;
import com.artivisi.accountingfinance.enums.FiscalTreatment;
import com.artivisi.accountingfinance.enums.NormalBalance;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.EmployeeRepository;
import com.artivisi.accountingfinance.repository.FiscalAdjustmentRepository;
import com.artivisi.accountingfinance.repository.TagRepository;
import com.artivisi.accountingfinance.repository.TagTypeRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.repository.TransactionTagRepository;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fiscal adjustment drafts from account flags. Reserved test years 2122-2130; each test posts into its own year.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("Fiscal adjustment drafts")
class FiscalAdjustmentDraftServiceTest {

    private static final String NON_DEDUCTIBLE = "5.8.04";
    private static final String CONDITIONAL = "5.8.03";
    private static final String DEDUCTIBLE = "5.8.01";

    @Autowired private FiscalAdjustmentDraftService draftService;
    @Autowired private TaxReportDetailService taxReportDetailService;
    @Autowired private SptTahunanExportService sptTahunanExportService;
    @Autowired private ReportService reportService;
    @Autowired private BenefitInKindService benefitInKindService;
    @Autowired private TransactionService transactionService;
    @Autowired private TransactionApiService transactionApiService;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private FiscalAdjustmentRepository adjustmentRepository;
    @Autowired private TagRepository tagRepository;
    @Autowired private TagTypeRepository tagTypeRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private TransactionTagRepository transactionTagRepository;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("Non-deductible: full balance; deductible: none; conditional: balance minus documented natura")
    void draftsFollowFlags() {
        post(2122, NON_DEDUCTIBLE, "1000000");
        post(2122, NON_DEDUCTIBLE, "250000");
        post(2122, DEDUCTIBLE, "5000000");
        post(2122, CONDITIONAL, "400000");
        Transaction documented = post(2122, CONDITIONAL, "700000");
        benefitInKindService.add(documented.getId(), emp001().getId(), new BigDecimal("300000"), "Natura");
        flush();

        var result = draftService.generate(2122);

        assertThat(result.drafts()).extracting(FiscalAdjustment::getAccountCode)
                .containsExactlyInAnyOrder(NON_DEDUCTIBLE, CONDITIONAL);
        FiscalAdjustment nonDeductible = draft(result.drafts(), NON_DEDUCTIBLE);
        assertThat(nonDeductible.getAmount()).isEqualByComparingTo("1250000");
        assertThat(nonDeductible.getStatus()).isEqualTo(FiscalAdjustmentStatus.DRAFT);
        assertThat(nonDeductible.getSource()).isEqualTo(FiscalAdjustmentSource.GENERATED);
        assertThat(nonDeductible.getAdjustmentCategory()).isEqualTo(FiscalAdjustmentCategory.PERMANENT);
        assertThat(nonDeductible.getAdjustmentDirection()).isEqualTo(FiscalAdjustmentDirection.POSITIVE);
        assertThat(nonDeductible.getPasal()).isEqualTo(FiscalAdjustmentDraftService.PASAL_NON_DEDUCTIBLE);
        assertThat(draft(result.drafts(), CONDITIONAL).getAmount()).isEqualByComparingTo("800000");
    }

    @Test
    @DisplayName("Drafts stay out of the reconciliation until confirmed; confirmed amount can be edited")
    void draftsDoNotCountUntilConfirmed() {
        post(2123, NON_DEDUCTIBLE, "1000000");

        var draft = draft(draftService.generate(2123).drafts(), NON_DEDUCTIBLE);
        flush();
        var before = taxReportDetailService.generateRekonsiliasiFiskal(2123);
        assertThat(before.adjustments()).isEmpty();
        assertThat(before.totalPositiveAdjustment()).isEqualByComparingTo("0");
        assertThat(before.pkp()).isEqualByComparingTo(before.commercialNetIncome());
        assertThat(sptTahunanExportService.generateL1(2123).positiveAdjustments()).isEmpty();
        assertThat(taxReportDetailService.findDraftAdjustmentsByYear(2123)).hasSize(1);

        draftService.confirm(draft.getId(), new BigDecimal("900000"));
        flush();

        var after = taxReportDetailService.generateRekonsiliasiFiskal(2123);
        assertThat(after.totalPositiveAdjustment()).isEqualByComparingTo("900000");
        assertThat(after.pkp()).isEqualByComparingTo(after.commercialNetIncome().add(new BigDecimal("900000")));
        assertThat(sptTahunanExportService.generateL1(2123).positiveAdjustments()).hasSize(1);
        assertThat(taxReportDetailService.findDraftAdjustmentsByYear(2123)).isEmpty();

        assertThatThrownBy(() -> draftService.confirm(draft.getId(), null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("sudah dikonfirmasi");
    }

    @Test
    @DisplayName("Generating again replaces generated drafts, keeps manual rows, and nets confirmed amounts")
    void regenerationIsIdempotent() {
        post(2124, NON_DEDUCTIBLE, "1000000");
        post(2124, CONDITIONAL, "600000");

        var first = draftService.generate(2124);
        flush();
        draftService.confirm(draft(first.drafts(), NON_DEDUCTIBLE).getId(), new BigDecimal("400000"));
        FiscalAdjustment manual = manual(2124, "Koreksi tangan", CONDITIONAL, "100000", FiscalAdjustmentDirection.POSITIVE);
        flush();

        var second = draftService.generate(2124);
        flush();

        // 1,000,000 - 400,000 confirmed; 600,000 - 100,000 manual
        assertThat(draft(second.drafts(), NON_DEDUCTIBLE).getAmount()).isEqualByComparingTo("600000");
        assertThat(draft(second.drafts(), CONDITIONAL).getAmount()).isEqualByComparingTo("500000");
        List<FiscalAdjustment> all = adjustmentRepository.findByYearOrderByAdjustmentCategoryAscDescriptionAsc(2124);
        assertThat(all).hasSize(4);
        assertThat(all).filteredOn(a -> a.getStatus() == FiscalAdjustmentStatus.DRAFT).hasSize(2);
        assertThat(all).extracting(FiscalAdjustment::getId).contains(manual.getId());

        // Fully confirmed account yields no further draft
        draftService.confirm(draft(second.drafts(), NON_DEDUCTIBLE).getId(), null);
        flush();
        assertThat(draftService.generate(2124).drafts()).extracting(FiscalAdjustment::getAccountCode)
                .containsExactly(CONDITIONAL);
    }

    @Test
    @DisplayName("Rejected draft (deleted) has no effect on the reconciliation")
    void rejectedDraftIsGone() {
        post(2125, NON_DEDUCTIBLE, "1000000");
        var draft = draft(draftService.generate(2125).drafts(), NON_DEDUCTIBLE);
        flush();

        taxReportDetailService.deleteAdjustment(draft.getId());
        flush();

        assertThat(adjustmentRepository.findByYearOrderByAdjustmentCategoryAscDescriptionAsc(2125)).isEmpty();
        assertThat(taxReportDetailService.generateRekonsiliasiFiskal(2125).totalPositiveAdjustment()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Reports unclassified accounts with a balance and NATURA-tagged transactions without recipient")
    void reportsWhatItCannotDecide() {
        ChartOfAccount unclassified = new ChartOfAccount();
        unclassified.setAccountCode("5.8.91");
        unclassified.setAccountName("Beban Tanpa Klasifikasi");
        unclassified.setAccountType(AccountType.EXPENSE);
        unclassified.setNormalBalance(NormalBalance.DEBIT);
        unclassified.setLevel(3);
        accountRepository.save(unclassified);
        post(2126, "5.8.91", "100000");

        Transaction tagged = post(2126, DEDUCTIBLE, "100000");
        var tagType = tagTypeRepository.findByCode("NATURA").orElseThrow();
        Tag tag = tagRepository.findByTagTypeIdAndCode(tagType.getId(), "NATURA").orElseThrow();
        var link = new com.artivisi.accountingfinance.entity.TransactionTag();
        link.setTransaction(tagged);
        link.setTag(tag);
        transactionTagRepository.save(link);
        flush();

        var result = draftService.generate(2126);

        assertThat(result.drafts()).isEmpty();
        assertThat(result.unclassifiedAccounts()).containsExactly("5.8.91 Beban Tanpa Klasifikasi");
        assertThat(result.taggedTransactionsWithoutRecipient())
                .containsExactly(transactionRepository.findById(tagged.getId()).orElseThrow().getTransactionNumber());
    }

    @Test
    @DisplayName("Draft transactions and closing-free balance: only posted amounts count")
    void draftTransactionsDoNotCount() {
        createDraft(2127, NON_DEDUCTIBLE, "1000000");
        assertThat(draftService.generate(2127).drafts()).isEmpty();
    }

    @Test
    @DisplayName("5.8 employee costs are operating expenses in L1")
    void employeeCostsAreOperatingInL1() {
        post(2128, DEDUCTIBLE, "5000000");
        flush();

        var l1 = sptTahunanExportService.generateL1(2128);

        assertThat(l1.operatingExpenses()).extracting(i -> i.accountCode()).contains(DEDUCTIBLE);
        assertThat(l1.totalOperatingExpenses()).isEqualByComparingTo("5000000");
    }

    @Test
    @DisplayName("Dividen (debit-normal equity) reduces total equity by the declared amount")
    void dividendReducesEquity() {
        LocalDate asOf = LocalDate.of(2129, 12, 31);
        var before = reportService.generateBalanceSheet(asOf);

        UUID dividen = accountRepository.findByAccountCode("3.2.03").orElseThrow().getId();
        UUID hutang = accountRepository.findByAccountCode("2.1.40").orElseThrow().getId();
        BigDecimal value = new BigDecimal("750000");
        UUID id = transactionApiService.createJournalEntry(new JournalEntryRequest(LocalDate.of(2129, 6, 1), "Dividen", null, List.of(
                new JournalEntryRequest.JournalLineRequest(dividen, value, BigDecimal.ZERO),
                new JournalEntryRequest.JournalLineRequest(hutang, BigDecimal.ZERO, value))), "test").transactionId();
        transactionService.post(id, "test");
        flush();

        var after = reportService.generateBalanceSheet(asOf);
        assertThat(after.totalEquity().subtract(before.totalEquity())).isEqualByComparingTo("-750000");
        assertThat(after.totalLiabilities().subtract(before.totalLiabilities())).isEqualByComparingTo("750000");
        assertThat(after.totalAssets().subtract(before.totalAssets())).isEqualByComparingTo("0");

        // The line carries the same sign as its effect on the total, in the report and the Coretax neraca
        assertThat(equityLine(after, "3.2.03").subtract(equityLine(before, "3.2.03"))).isEqualByComparingTo("-750000");
        BigDecimal lineSum = after.equityItems().stream().map(ReportService.BalanceSheetItem::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal lineSumBefore = before.equityItems().stream().map(ReportService.BalanceSheetItem::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(lineSum.subtract(lineSumBefore)).isEqualByComparingTo(after.totalEquity().subtract(before.totalEquity()));
        assertThat(sptTahunanExportService.generateCoretaxExport(2129).l1dNeraca().pasiva().stream()
                .filter(i -> i.kodeAkun().equals("3.2.03")).findFirst().orElseThrow().jumlah())
                .isEqualByComparingTo(equityLine(after, "3.2.03"));
    }

    private static BigDecimal equityLine(ReportService.BalanceSheetReport report, String code) {
        // A zero balance has no line
        return report.equityItems().stream().filter(i -> i.account().getAccountCode().equals(code))
                .map(ReportService.BalanceSheetItem::balance).findFirst().orElse(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Treatment rejected on non-expense and header accounts")
    void treatmentOnlyOnExpenseLeaf(@Autowired ChartOfAccountService chartOfAccountService) {
        ChartOfAccount asset = new ChartOfAccount();
        asset.setAccountCode("1.9.91");
        asset.setAccountName("Aset");
        asset.setAccountType(AccountType.ASSET);
        asset.setNormalBalance(NormalBalance.DEBIT);
        asset.setFiscalTreatment(FiscalTreatment.DEDUCTIBLE);
        assertThatThrownBy(() -> chartOfAccountService.create(asset))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("akun beban");

        ChartOfAccount header = new ChartOfAccount();
        header.setAccountCode("5.91");
        header.setAccountName("Header");
        header.setAccountType(AccountType.EXPENSE);
        header.setNormalBalance(NormalBalance.DEBIT);
        header.setIsHeader(true);
        header.setFiscalTreatment(FiscalTreatment.CONDITIONAL);
        assertThatThrownBy(() -> chartOfAccountService.create(header))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("akun beban");
    }

    private FiscalAdjustment manual(int year, String description, String code, String amount, FiscalAdjustmentDirection direction) {
        FiscalAdjustment adjustment = new FiscalAdjustment();
        adjustment.setYear(year);
        adjustment.setDescription(description);
        adjustment.setAdjustmentCategory(FiscalAdjustmentCategory.PERMANENT);
        adjustment.setAdjustmentDirection(direction);
        adjustment.setAmount(new BigDecimal(amount));
        adjustment.setAccountCode(code);
        return taxReportDetailService.saveAdjustment(adjustment);
    }

    private FiscalAdjustment draft(List<FiscalAdjustment> drafts, String code) {
        return drafts.stream().filter(d -> code.equals(d.getAccountCode())).findFirst()
                .orElseThrow(() -> new AssertionError("No draft for " + code + " in " + drafts));
    }

    private Employee emp001() {
        return employeeRepository.findByEmployeeId("EMP001")
                .orElseThrow(() -> new IllegalStateException("Seeded employee EMP001 missing"));
    }

    private Transaction post(int year, String accountCode, String amount) {
        Transaction posted = transactionService.post(createDraft(year, accountCode, amount), "test");
        flush();
        return posted;
    }

    private UUID createDraft(int year, String accountCode, String amount) {
        BigDecimal value = new BigDecimal(amount);
        var account = accountRepository.findByAccountCode(accountCode).orElseThrow();
        var bank = accountRepository.findByAccountCode("1.1.02").orElseThrow();
        return transactionApiService.createJournalEntry(new JournalEntryRequest(LocalDate.of(year, 3, 1), "Beban " + accountCode, null, List.of(
                new JournalEntryRequest.JournalLineRequest(account.getId(), value, BigDecimal.ZERO),
                new JournalEntryRequest.JournalLineRequest(bank.getId(), BigDecimal.ZERO, value))), "test").transactionId();
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }
}
