package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.dto.JournalEntryRequest;
import com.artivisi.accountingfinance.entity.AssetCategory;
import com.artivisi.accountingfinance.entity.AssetDisposal;
import com.artivisi.accountingfinance.entity.AssetStatus;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.DepreciationMethod;
import com.artivisi.accountingfinance.entity.DisposalType;
import com.artivisi.accountingfinance.entity.Document;
import com.artivisi.accountingfinance.entity.FixedAsset;
import com.artivisi.accountingfinance.entity.PayrollRun;
import com.artivisi.accountingfinance.entity.TaxTransactionDetail;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.AccountType;
import com.artivisi.accountingfinance.enums.EvidenceType;
import com.artivisi.accountingfinance.enums.NormalBalance;
import com.artivisi.accountingfinance.enums.SelfTestLine;
import com.artivisi.accountingfinance.enums.SelfTestRole;
import com.artivisi.accountingfinance.enums.TaxType;
import com.artivisi.accountingfinance.repository.AssetCategoryRepository;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.DocumentRepository;
import com.artivisi.accountingfinance.repository.FixedAssetRepository;
import com.artivisi.accountingfinance.repository.TaxTransactionDetailRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.service.SelfTestService.Line;
import com.artivisi.accountingfinance.service.SelfTestService.LineStatus;
import com.artivisi.accountingfinance.service.SelfTestService.Report;
import com.artivisi.accountingfinance.service.SelfTestService.Trace;
import com.artivisi.accountingfinance.service.SelfTestService.TraceRow;
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
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tax-office self-test (issue #45): every line with exact figures, a nonzero difference, and trace rows that add up
 * to the figures. Every test rolls back, so they share reserved year 2199 (integration DB).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("Tax-office self-test")
class SelfTestServiceTest {

    private static final int YEAR = 2199;
    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final String BANK = "1.1.02";

    @Autowired private SelfTestService selfTestService;
    @Autowired private PayrollService payrollService;
    @Autowired private PayrollReconciliationService payrollReconciliationService;
    @Autowired private TransactionService transactionService;
    @Autowired private TransactionApiService transactionApiService;
    @Autowired private FixedAssetService fixedAssetService;
    @Autowired private AssetDisposalService disposalService;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private TaxTransactionDetailRepository taxDetailRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private AssetCategoryRepository categoryRepository;
    @Autowired private FixedAssetRepository fixedAssetRepository;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("Revenue vs PPN DPP per masa: March revenue 10,000,000 with a 9,000,000 faktur differs by 1,000,000; April matches")
    void revenueVsPpnDpp() {
        ChartOfAccount revenue = accountRepository.findByAccountCode("4.1.01").orElseThrow();
        UUID march = posted(journal(LocalDate.of(YEAR, 3, 5), "Jasa Maret", bank(), revenue, "10000000"));
        faktur(march, "010.000-99.00000001", LocalDate.of(YEAR, 3, 5), "9000000");
        UUID april = posted(journal(LocalDate.of(YEAR, 4, 7), "Jasa April", bank(), revenue, "2000000"));
        faktur(april, "010.000-99.00000002", LocalDate.of(YEAR, 4, 7), "2000000");
        flush();

        Report report = selfTestService.report(YEAR);
        assertFigures(row(report, SelfTestLine.REVENUE_VS_PPN_DPP, 0), "12000000", "11000000", "1000000", LineStatus.DIFFERENCE);
        assertFigures(row(report, SelfTestLine.REVENUE_VS_PPN_DPP, 3), "10000000", "9000000", "1000000", LineStatus.DIFFERENCE);
        assertFigures(row(report, SelfTestLine.REVENUE_VS_PPN_DPP, 4), "2000000", "2000000", "0", LineStatus.MATCHED);
        assertFigures(row(report, SelfTestLine.REVENUE_VS_PPN_DPP, 5), "0", "0", "0", LineStatus.NO_ACTIVITY);
        assertThat(report.lines()).filteredOn(l -> l.line() == SelfTestLine.REVENUE_VS_PPN_DPP)
                .extracting(Line::month).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);

        Trace trace = selfTestService.trace(YEAR, SelfTestLine.REVENUE_VS_PPN_DPP, 3);
        assertThat(trace.bookRows()).singleElement().satisfies(r -> {
            assertThat(r.transactionId()).isEqualTo(march);
            assertThat(r.amount()).isEqualByComparingTo("10000000");
        });
        assertThat(trace.taxRows()).singleElement().satisfies(r -> {
            assertThat(r.reference()).isEqualTo("010.000-99.00000001");
            assertThat(r.source()).isEqualTo(SelfTestService.SOURCE_FAKTUR);
            assertThat(r.amount()).isEqualByComparingTo("9000000");
        });
        assertTraceAddsUp(report, SelfTestLine.REVENUE_VS_PPN_DPP);
    }

    @Test
    @DisplayName("Salary vs PPh 21 DPP per masa: a manual salary journal on top of a posted run is the difference; an unposted run adds no DPP")
    void salaryVsPph21Dpp() {
        PayrollRun march = payrollService.createPayrollRun(YearMonth.of(YEAR, 3));
        payrollService.calculatePayroll(march.getId(), new BigDecimal("10000000"), 1);
        payrollService.approvePayroll(march.getId());
        payrollService.postPayroll(march.getId());
        posted(journal(LocalDate.of(YEAR, 3, 28), "Gaji susulan", account("5.1.01"), bank(), "2500000"));
        PayrollRun april = payrollService.createPayrollRun(YearMonth.of(YEAR, 4));
        payrollService.calculatePayroll(april.getId(), new BigDecimal("10000000"), 1);
        payrollService.approvePayroll(april.getId());
        flush();

        var marchRecon = payrollReconciliationService.reconcile(YearMonth.of(YEAR, 3));
        BigDecimal runGross = new BigDecimal("10000000").multiply(BigDecimal.valueOf(marchRecon.employeeCount()));
        assertThat(marchRecon.employeeCount()).isPositive();
        assertThat(marchRecon.runTaxableIncome()).isEqualByComparingTo(runGross);

        Report report = selfTestService.report(YEAR);
        Line m3 = row(report, SelfTestLine.SALARY_VS_PPH21_DPP, 3);
        assertFigures(m3, runGross.add(new BigDecimal("2500000")).toPlainString(), runGross.toPlainString(), "2500000",
                LineStatus.DIFFERENCE);
        Line m4 = row(report, SelfTestLine.SALARY_VS_PPH21_DPP, 4);
        assertFigures(m4, "0", "0", "0", LineStatus.NO_ACTIVITY);
        assertThat(m4.message()).isEqualTo("Payroll " + YEAR + "-04 berstatus APPROVED: belum masuk DPP PPh 21");
        assertFigures(row(report, SelfTestLine.SALARY_VS_PPH21_DPP, 0), runGross.add(new BigDecimal("2500000")).toPlainString(),
                runGross.toPlainString(), "2500000", LineStatus.DIFFERENCE);

        Trace trace = selfTestService.trace(YEAR, SelfTestLine.SALARY_VS_PPH21_DPP, 3);
        assertThat(trace.taxRows()).hasSize(marchRecon.employeeCount())
                .allSatisfy(r -> assertThat(r.source()).isEqualTo(SelfTestService.SOURCE_PAYROLL_DETAIL));
        assertThat(trace.bookRows()).extracting(TraceRow::description).contains("Gaji susulan");
        assertThat(selfTestService.trace(YEAR, SelfTestLine.SALARY_VS_PPH21_DPP, 4).taxRows()).isEmpty();
        assertTraceAddsUp(report, SelfTestLine.SALARY_VS_PPH21_DPP);
    }

    @Test
    @DisplayName("Service and rent vs PPh 23 and 4(2): not configured without a role; an undocumented payment is the difference; customer bupot excluded")
    void serviceAndRentVsWithholding() {
        Report unconfigured = selfTestService.report(YEAR);
        for (SelfTestLine line : List.of(SelfTestLine.SERVICE_VS_PPH23_DPP, SelfTestLine.RENT_VS_PPH42_DPP,
                SelfTestLine.NON_OPERATING_ASSET_MOVEMENT, SelfTestLine.UNITEMISED_OTHER_EXPENSE)) {
            Line row = row(unconfigured, line, 0);
            assertThat(row.status()).as(line.name()).isEqualTo(LineStatus.NOT_CONFIGURED);
            assertThat(row.bookFigure()).isNull();
            assertThat(row.message()).startsWith("Belum ada akun dengan peran uji mandiri");
        }
        assertThatThrownBy(() -> selfTestService.trace(YEAR, SelfTestLine.SERVICE_VS_PPH23_DPP, 0))
                .isInstanceOf(IllegalStateException.class);

        ChartOfAccount service = roleAccount(AccountType.EXPENSE, SelfTestRole.SERVICE_PPH23);
        ChartOfAccount rent = roleAccount(AccountType.EXPENSE, SelfTestRole.RENT_PPH42);
        UUID withheld = posted(journal(LocalDate.of(YEAR, 5, 10), "Jasa konsultan", service, bank(), "4000000"));
        withholding(withheld, TaxType.PPH_23, "BP23-2199-1", "4000000");
        posted(journal(LocalDate.of(YEAR, 6, 10), "Jasa tanpa bupot", service, bank(), "1000000"));
        UUID rentTrx = posted(journal(LocalDate.of(YEAR, 7, 1), "Sewa kantor", rent, bank(), "6000000"));
        withholding(rentTrx, TaxType.PPH_42, "BP42-2199-1", "6000000");
        UUID customer = posted(journal(LocalDate.of(YEAR, 8, 1), "Bupot dari pelanggan", bank(), account("4.1.01"), "7000000"));
        Transaction customerTrx = transactionRepository.findById(customer).orElseThrow();
        customerTrx.setSourceDocumentType("INVOICE");
        customerTrx.setSourceDocumentId(UUID.randomUUID());
        transactionRepository.save(customerTrx);
        withholding(customer, TaxType.PPH_23, "BP23-CUST-2199", "7000000");
        flush();

        Report report = selfTestService.report(YEAR);
        assertFigures(row(report, SelfTestLine.SERVICE_VS_PPH23_DPP, 0), "5000000", "4000000", "1000000", LineStatus.DIFFERENCE);
        assertFigures(row(report, SelfTestLine.RENT_VS_PPH42_DPP, 0), "6000000", "6000000", "0", LineStatus.MATCHED);
        assertThat(selfTestService.trace(YEAR, SelfTestLine.SERVICE_VS_PPH23_DPP, 0).taxRows())
                .extracting(TraceRow::reference).containsExactly("BP23-2199-1");
        assertTraceAddsUp(report, SelfTestLine.SERVICE_VS_PPH23_DPP);
        assertTraceAddsUp(report, SelfTestLine.RENT_VS_PPH42_DPP);
    }

    @Test
    @DisplayName("Fixed assets: a journal on the cost account outside the register is the additions difference; disposal matches")
    void fixedAssetMovement() {
        FixedAsset asset = registeredAsset(LocalDate.of(YEAR, 2, 10), "12000000");
        ChartOfAccount costAccount = asset.getAssetAccount();
        posted(journal(LocalDate.of(YEAR, 2, 20), "Aksesori laptop", costAccount, bank(), "1500000"));
        AssetDisposal disposal = disposalService.create(new AssetDisposalService.CreateDisposal(asset.getId(), DisposalType.SOLD,
                LocalDate.of(YEAR, 3, 15), new BigDecimal("8000000"), bank().getId(), "Pembeli", "Diganti", "test"));
        transactionService.post(disposal.getTransaction().getId(), "test");
        flush();
        assertThat(fixedAssetRepository.findById(asset.getId()).orElseThrow().getStatus()).isEqualTo(AssetStatus.DISPOSED);

        Report report = selfTestService.report(YEAR);
        assertFigures(row(report, SelfTestLine.FIXED_ASSET_ADDITIONS, 0), "13500000", "12000000", "1500000", LineStatus.DIFFERENCE);
        assertFigures(row(report, SelfTestLine.FIXED_ASSET_DISPOSALS, 0), "12000000", "12000000", "0", LineStatus.MATCHED);
        Trace additions = selfTestService.trace(YEAR, SelfTestLine.FIXED_ASSET_ADDITIONS, 0);
        assertThat(additions.taxRows()).singleElement().satisfies(r -> {
            assertThat(r.reference()).isEqualTo(asset.getAssetCode());
            assertThat(r.source()).isEqualTo(SelfTestService.SOURCE_ASSET);
        });
        assertThat(additions.bookRows()).extracting(TraceRow::accountCode).containsOnly(costAccount.getAccountCode());
        assertTraceAddsUp(report, SelfTestLine.FIXED_ASSET_ADDITIONS);
        assertTraceAddsUp(report, SelfTestLine.FIXED_ASSET_DISPOSALS);
    }

    @Test
    @DisplayName("Capital and non-operating assets: ledger movement with no tax figure; closing entries excluded")
    void capitalAndNonOperatingAssets() {
        ChartOfAccount capital = account("3.1.01");
        ChartOfAccount drawings = roleAccount(AccountType.EQUITY, null);
        ChartOfAccount investment = roleAccount(AccountType.ASSET, SelfTestRole.NON_OPERATING_ASSET);
        posted(journal(LocalDate.of(YEAR, 6, 1), "Setoran modal", bank(), capital, "50000000"));
        posted(journal(LocalDate.of(YEAR, 6, 2), "Penarikan pemilik", drawings, bank(), "5000000"));
        UUID closing = posted(journal(LocalDate.of(YEAR, 12, 31), "Jurnal penutup", bank(), capital, "10000000"));
        Transaction closingTrx = transactionRepository.findById(closing).orElseThrow();
        closingTrx.setClosingEntry(true);
        transactionRepository.save(closingTrx);
        posted(journal(LocalDate.of(YEAR, 7, 1), "Pinjaman ke direksi", investment, bank(), "20000000"));
        posted(journal(LocalDate.of(YEAR, 9, 1), "Cicilan pinjaman direksi", bank(), investment, "5000000"));
        flush();

        Report report = selfTestService.report(YEAR);
        Line capitalRow = row(report, SelfTestLine.CAPITAL_MOVEMENT, 0);
        assertThat(capitalRow.taxFigure()).isNull();
        assertThat(capitalRow.taxBasis()).isNull();
        assertThat(capitalRow.bookFigure()).isEqualByComparingTo("45000000");
        assertThat(capitalRow.difference()).isEqualByComparingTo("45000000");
        assertThat(capitalRow.status()).isEqualTo(LineStatus.MOVEMENT);
        Line nonOperating = row(report, SelfTestLine.NON_OPERATING_ASSET_MOVEMENT, 0);
        assertThat(nonOperating.bookFigure()).isEqualByComparingTo("15000000");
        assertThat(nonOperating.taxFigure()).isNull();
        assertThat(nonOperating.status()).isEqualTo(LineStatus.MOVEMENT);
        assertThat(selfTestService.trace(YEAR, SelfTestLine.CAPITAL_MOVEMENT, 0).bookRows())
                .extracting(TraceRow::description).containsExactly("Setoran modal", "Penarikan pemilik");
        assertTraceAddsUp(report, SelfTestLine.CAPITAL_MOVEMENT);
        assertTraceAddsUp(report, SelfTestLine.NON_OPERATING_ASSET_MOVEMENT);
    }

    @Test
    @DisplayName("Unitemised other expenses: only a line at or above the threshold without evidence; an unclassified file is not evidence")
    void unitemisedOtherExpenses() {
        assertThat(selfTestService.unitemisedThreshold()).isEqualByComparingTo("5000000");
        ChartOfAccount other = roleAccount(AccountType.EXPENSE, SelfTestRole.OTHER_EXPENSE);
        UUID bare = posted(journal(LocalDate.of(YEAR, 4, 1), "Biaya lain-lain", other, bank(), "6000000"));
        document(bare, null);
        UUID documented = posted(journal(LocalDate.of(YEAR, 4, 2), "Biaya perizinan", other, bank(), "7000000"));
        document(documented, EvidenceType.INVOICE);
        posted(journal(LocalDate.of(YEAR, 4, 3), "Biaya kecil", other, bank(), "1000000"));
        UUID atThreshold = posted(journal(LocalDate.of(YEAR, 4, 4), "Biaya pas ambang", other, bank(), "5000000"));
        flush();

        Report report = selfTestService.report(YEAR);
        assertThat(report.unitemisedThreshold()).isEqualByComparingTo("5000000");
        assertFigures(row(report, SelfTestLine.UNITEMISED_OTHER_EXPENSE, 0), "19000000", "8000000", "11000000",
                LineStatus.DIFFERENCE);
        assertThat(selfTestService.unitemisedRows(YEAR)).extracting(TraceRow::transactionId)
                .containsExactly(bare, atThreshold);
        assertTraceAddsUp(report, SelfTestLine.UNITEMISED_OTHER_EXPENSE);
    }

    @Test
    @DisplayName("Explanations persist per year, line and month; blank removes; a month on a yearly line is refused")
    void explanations() {
        selfTestService.saveExplanation(YEAR, SelfTestLine.REVENUE_VS_PPN_DPP, 3, "  Faktur DP diterbitkan Februari  ", "auditor");
        selfTestService.saveExplanation(YEAR, SelfTestLine.CAPITAL_MOVEMENT, 0, "Setoran modal sesuai akta", "admin");
        selfTestService.saveExplanation(YEAR + 1, SelfTestLine.CAPITAL_MOVEMENT, 0, "Tahun lain", "admin");
        flush();

        Report report = selfTestService.report(YEAR);
        Line revenueMarch = row(report, SelfTestLine.REVENUE_VS_PPN_DPP, 3);
        assertThat(revenueMarch.explanation()).isEqualTo("Faktur DP diterbitkan Februari");
        assertThat(revenueMarch.explainedBy()).isEqualTo("auditor");
        assertThat(revenueMarch.explainedAt()).isNotNull();
        assertThat(row(report, SelfTestLine.REVENUE_VS_PPN_DPP, 0).explanation()).isNull();
        assertThat(row(report, SelfTestLine.CAPITAL_MOVEMENT, 0).explanation()).isEqualTo("Setoran modal sesuai akta");

        selfTestService.saveExplanation(YEAR, SelfTestLine.CAPITAL_MOVEMENT, 0, "Diubah", "owner");
        assertThat(selfTestService.saveExplanation(YEAR, SelfTestLine.REVENUE_VS_PPN_DPP, 3, " ", "auditor")).isNull();
        flush();
        Report updated = selfTestService.report(YEAR);
        assertThat(row(updated, SelfTestLine.CAPITAL_MOVEMENT, 0).explanation()).isEqualTo("Diubah");
        assertThat(row(updated, SelfTestLine.CAPITAL_MOVEMENT, 0).explainedBy()).isEqualTo("owner");
        assertThat(row(updated, SelfTestLine.REVENUE_VS_PPN_DPP, 3).explanation()).isNull();
        assertThat(row(selfTestService.report(YEAR + 1), SelfTestLine.CAPITAL_MOVEMENT, 0).explanation()).isEqualTo("Tahun lain");

        assertThatThrownBy(() -> selfTestService.saveExplanation(YEAR, SelfTestLine.CAPITAL_MOVEMENT, 3, "x", "admin"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> selfTestService.saveExplanation(YEAR, SelfTestLine.SALARY_VS_PPH21_DPP, 13, "x", "admin"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== helpers ====================

    private void assertTraceAddsUp(Report report, SelfTestLine line) {
        report.lines().stream().filter(l -> l.line() == line).forEach(l -> {
            Trace trace = selfTestService.trace(YEAR, line, l.month());
            BigDecimal book = trace.bookRows().stream().map(TraceRow::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(book).as("book rows of %s/%d", line, l.month()).isEqualByComparingTo(l.bookFigure());
            assertThat(trace.bookFigure()).isEqualByComparingTo(l.bookFigure());
            if (line.hasTaxFigure()) {
                BigDecimal tax = trace.taxRows().stream().map(TraceRow::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
                assertThat(tax).as("tax rows of %s/%d", line, l.month()).isEqualByComparingTo(l.taxFigure());
                assertThat(trace.taxFigure()).isEqualByComparingTo(l.taxFigure());
            } else {
                assertThat(trace.taxFigure()).isNull();
            }
        });
    }

    private static void assertFigures(Line line, String book, String tax, String difference, LineStatus status) {
        assertThat(line.bookFigure()).as("book %s/%d", line.line(), line.month()).isEqualByComparingTo(book);
        assertThat(line.taxFigure()).as("tax %s/%d", line.line(), line.month()).isEqualByComparingTo(tax);
        assertThat(line.difference()).as("difference %s/%d", line.line(), line.month()).isEqualByComparingTo(difference);
        assertThat(line.status()).as("status %s/%d", line.line(), line.month()).isEqualTo(status);
    }

    private static Line row(Report report, SelfTestLine line, int month) {
        return report.lines().stream().filter(l -> l.line() == line && l.month() == month).findFirst().orElseThrow();
    }

    private ChartOfAccount account(String code) {
        return accountRepository.findByAccountCode(code).orElseThrow();
    }

    private ChartOfAccount bank() {
        return account(BANK);
    }

    private ChartOfAccount roleAccount(AccountType type, SelfTestRole role) {
        ChartOfAccount a = new ChartOfAccount();
        a.setAccountCode("9.45." + SEQ.incrementAndGet() + "." + UUID.randomUUID().toString().substring(0, 6));
        a.setAccountName("Akun Uji Mandiri " + a.getAccountCode());
        a.setAccountType(type);
        a.setNormalBalance(type == AccountType.ASSET || type == AccountType.EXPENSE ? NormalBalance.DEBIT : NormalBalance.CREDIT);
        a.setLevel(1);
        a.setIsHeader(false);
        a.setActive(true);
        a.setSelfTestRole(role);
        return accountRepository.save(a);
    }

    private UUID journal(LocalDate date, String description, ChartOfAccount debit, ChartOfAccount credit, String amount) {
        BigDecimal value = new BigDecimal(amount);
        return transactionApiService.createJournalEntry(new JournalEntryRequest(date, description, null, List.of(
                new JournalEntryRequest.JournalLineRequest(debit.getId(), value, BigDecimal.ZERO),
                new JournalEntryRequest.JournalLineRequest(credit.getId(), BigDecimal.ZERO, value))), "test").transactionId();
    }

    private UUID posted(UUID transactionId) {
        transactionService.post(transactionId, "test");
        return transactionId;
    }

    private void faktur(UUID transactionId, String number, LocalDate date, String dpp) {
        TaxTransactionDetail d = new TaxTransactionDetail();
        d.setTransaction(transactionRepository.findById(transactionId).orElseThrow());
        d.setTaxType(TaxType.PPN_KELUARAN);
        d.setFakturNumber(number);
        d.setFakturDate(date);
        d.setTransactionCode("01");
        d.setDpp(new BigDecimal(dpp));
        d.setPpn(new BigDecimal(dpp).multiply(new BigDecimal("0.11")));
        d.setCounterpartyName("Pelanggan Uji Mandiri");
        taxDetailRepository.save(d);
    }

    private void withholding(UUID transactionId, TaxType type, String bupot, String gross) {
        TaxTransactionDetail d = new TaxTransactionDetail();
        d.setTransaction(transactionRepository.findById(transactionId).orElseThrow());
        d.setTaxType(type);
        d.setBupotNumber(bupot);
        d.setTaxObjectCode(type == TaxType.PPH_23 ? "24-104-01" : "28-403-01");
        d.setGrossAmount(new BigDecimal(gross));
        d.setTaxRate(new BigDecimal(type == TaxType.PPH_23 ? "2" : "10"));
        d.setTaxAmount(new BigDecimal(gross).multiply(d.getTaxRate()).divide(new BigDecimal("100")));
        d.setCounterpartyName("Vendor Uji Mandiri");
        taxDetailRepository.save(d);
    }

    private void document(UUID transactionId, EvidenceType type) {
        Document doc = new Document();
        doc.setFilename(UUID.randomUUID() + ".pdf");
        doc.setOriginalFilename("bukti.pdf");
        doc.setContentType("application/pdf");
        doc.setFileSize(100L);
        doc.setStoragePath("self-test/" + doc.getFilename());
        doc.setChecksumSha256("0".repeat(64));
        doc.setTransaction(transactionRepository.findById(transactionId).orElseThrow());
        doc.setUploadedBy("test");
        doc.setUploadedAt(LocalDateTime.now());
        doc.setEvidenceType(type);
        documentRepository.save(doc);
    }

    private FixedAsset registeredAsset(LocalDate purchaseDate, String cost) {
        AssetCategory category = categoryRepository.findByCode("KOMPUTER").orElseThrow();
        FixedAsset asset = new FixedAsset();
        asset.setAssetCode("ST-" + SEQ.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 6));
        asset.setName("Laptop " + asset.getAssetCode());
        asset.setCategory(category);
        asset.setPurchaseDate(purchaseDate);
        asset.setPurchaseCost(new BigDecimal(cost));
        asset.setResidualValue(BigDecimal.ZERO);
        asset.setUsefulLifeMonths(48);
        asset.setDepreciationMethod(DepreciationMethod.STRAIGHT_LINE);
        asset.setDepreciationStartDate(purchaseDate.withDayOfMonth(1).plusMonths(1));
        asset.setFundingAccount(bank());
        FixedAsset created = fixedAssetService.create(asset);
        Transaction acquisition = transactionRepository.findBySourceDocumentTypeAndSourceDocumentIdOrderByTransactionDateAsc(
                FixedAssetService.ACQUISITION_SOURCE_TYPE, created.getId()).getFirst();
        transactionService.post(acquisition.getId(), "test");
        entityManager.flush();
        return created;
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }
}
