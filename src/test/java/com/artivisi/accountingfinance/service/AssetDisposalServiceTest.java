package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.entity.AssetCategory;
import com.artivisi.accountingfinance.entity.AssetDisposal;
import com.artivisi.accountingfinance.entity.AssetDisposalStatus;
import com.artivisi.accountingfinance.entity.AssetStatus;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.DepreciationEntry;
import com.artivisi.accountingfinance.entity.DepreciationMethod;
import com.artivisi.accountingfinance.entity.DisposalType;
import com.artivisi.accountingfinance.entity.FixedAsset;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.enums.VoidReason;
import com.artivisi.accountingfinance.repository.AssetCategoryRepository;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.DepreciationEntryRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Disposal flow against the integration DB. V800 chart: Bank BCA 1.1.02, Peralatan 1.2.01, Akum. Penyusutan 1.2.02,
 * Laba 4.2.02, Rugi 5.2.02, Beban Tidak Dapat Dikurangkan 5.8.04. Reserved test years 2172-2179.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "tester")
@DisplayName("Asset disposal flow")
class AssetDisposalServiceTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final String BANK = "1.1.02";

    @Autowired private AssetDisposalService disposalService;
    @Autowired private FixedAssetService fixedAssetService;
    @Autowired private TransactionService transactionService;
    @Autowired private FiscalAdjustmentDraftService fiscalDraftService;
    @Autowired private AssetCategoryRepository categoryRepository;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private DepreciationEntryRepository entryRepository;
    @Autowired private JournalEntryRepository journalEntryRepository;
    @Autowired private JournalTemplateRepository templateRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("Sale above book value: a DRAFT leaves the asset and ledger alone; posting makes it DISPOSED with the exact journal")
    void saleWithGain() {
        FixedAsset asset = postedAsset("12000000", 24, YearMonth.of(2172, 1));
        depreciate(asset, YearMonth.of(2172, 1));

        AssetDisposal disposal = disposalService.create(sale(asset, "13000000", LocalDate.of(2172, 2, 15)));

        assertThat(disposal.getStatus()).isEqualTo(AssetDisposalStatus.DRAFT);
        assertThat(disposal.getRecordNumber()).matches("DSP-2172-\\d{4}");
        assertThat(disposal.getPurchaseCost()).isEqualByComparingTo("12000000");
        assertThat(disposal.getAccumulatedDepreciation()).isEqualByComparingTo("500000");
        assertThat(disposal.getBookValue()).isEqualByComparingTo("11500000");
        assertThat(disposal.getGainLoss()).isEqualByComparingTo("1500000");
        Transaction draft = disposal.getTransaction();
        assertThat(draft.getStatus()).isEqualTo(TransactionStatus.DRAFT);
        assertThat(draft.getJournalEntries()).isEmpty();
        assertThat(draft.getSourceDocumentType()).isEqualTo(AssetDisposalService.SOURCE_DOCUMENT_TYPE);
        assertThat(draft.getSourceDocumentId()).isEqualTo(disposal.getId());
        FixedAsset before = fixedAssetService.findById(asset.getId());
        assertThat(before.getStatus()).isEqualTo(AssetStatus.ACTIVE);
        assertThat(before.getDisposalDate()).isNull();

        transactionService.post(draft.getId(), "tester");
        entityManager.flush();

        FixedAsset after = fixedAssetService.findById(asset.getId());
        assertThat(after.getStatus()).isEqualTo(AssetStatus.DISPOSED);
        assertThat(after.getDisposalType()).isEqualTo(DisposalType.SOLD);
        assertThat(after.getDisposalDate()).isEqualTo(LocalDate.of(2172, 2, 15));
        assertThat(after.getDisposalProceeds()).isEqualByComparingTo("13000000");
        assertThat(after.getGainLossOnDisposal()).isEqualByComparingTo("1500000");
        assertThat(after.getDisposalNotes()).isEqualTo("Tidak terpakai");
        assertThat(after.getDisposalTransaction().getId()).isEqualTo(draft.getId());
        assertThat(disposalService.findById(disposal.getId()).getStatus()).isEqualTo(AssetDisposalStatus.POSTED);
        assertThat(lines(draft.getId())).containsExactlyInAnyOrder(
                "1.1.02 D13000000.00 C0.00", "1.2.02 D500000.00 C0.00",
                "1.2.01 D0.00 C12000000.00", "4.2.02 D0.00 C1500000.00");
        assertThat(disposalService.taxEffect(disposal).taxableIncomeEffect()).isEqualByComparingTo("1500000");
        assertThat(disposalService.taxEffect(disposal).estimatedPpn()).isEqualByComparingTo("1430000");
    }

    @Test
    @DisplayName("Sale below book value debits the loss account; no gain line")
    void saleWithLoss() {
        FixedAsset asset = postedAsset("12000000", 24, YearMonth.of(2172, 3));
        depreciate(asset, YearMonth.of(2172, 3));

        AssetDisposal disposal = disposalService.create(sale(asset, "10000000", LocalDate.of(2172, 4, 1)));
        transactionService.post(disposal.getTransaction().getId(), "tester");
        entityManager.flush();

        assertThat(disposal.getGainLoss()).isEqualByComparingTo("-1500000");
        assertThat(lines(disposal.getTransaction().getId())).containsExactlyInAnyOrder(
                "1.1.02 D10000000.00 C0.00", "1.2.02 D500000.00 C0.00", "5.2.02 D1500000.00 C0.00",
                "1.2.01 D0.00 C12000000.00");
    }

    @Test
    @DisplayName("Gift has no proceeds: the book value goes to the non-deductible account, and fiscal drafts propose that amount")
    void gift() {
        FixedAsset asset = postedAsset("12000000", 24, YearMonth.of(2173, 1));
        depreciate(asset, YearMonth.of(2173, 1));

        AssetDisposal disposal = disposalService.create(command(asset, DisposalType.GIFTED, null, null, "Yayasan Penerima",
                LocalDate.of(2173, 2, 1)));
        transactionService.post(disposal.getTransaction().getId(), "tester");
        entityManager.flush();

        assertThat(disposal.getProceeds()).isEqualByComparingTo("0");
        assertThat(disposal.getProceedsAccount()).isNull();
        assertThat(disposal.getGainLoss()).isEqualByComparingTo("-11500000");
        assertThat(lines(disposal.getTransaction().getId())).containsExactlyInAnyOrder(
                "1.2.02 D500000.00 C0.00", "5.8.04 D11500000.00 C0.00", "1.2.01 D0.00 C12000000.00");
        assertThat(disposalService.taxEffect(disposal).fiscalCorrection()).isEqualByComparingTo("11500000");
        assertThat(fiscalDraftService.generate(2173).drafts())
                .filteredOn(d -> "5.8.04".equals(d.getAccountCode())).singleElement()
                .satisfies(d -> assertThat(d.getAmount()).isEqualByComparingTo("11500000"));
    }

    @Test
    @DisplayName("Write-off and transfer debit the deductible loss account; a fully depreciated write-off has no loss line and needs no counterparty")
    void writeOffAndTransfer() {
        FixedAsset transferred = postedAsset("6000000", 12, YearMonth.of(2174, 1));
        AssetDisposal transfer = disposalService.create(command(transferred, DisposalType.TRANSFERRED, null, null, "Anak Perusahaan",
                LocalDate.of(2174, 1, 31)));
        transactionService.post(transfer.getTransaction().getId(), "tester");
        entityManager.flush();
        assertThat(lines(transfer.getTransaction().getId())).containsExactlyInAnyOrder(
                "5.2.02 D6000000.00 C0.00", "1.2.01 D0.00 C6000000.00");

        FixedAsset depreciated = postedAsset("1000000", 1, YearMonth.of(2174, 2));
        depreciate(depreciated, YearMonth.of(2174, 2));
        assertThat(fixedAssetService.findById(depreciated.getId()).getStatus()).isEqualTo(AssetStatus.FULLY_DEPRECIATED);
        AssetDisposal writeOff = disposalService.create(command(depreciated, DisposalType.WRITTEN_OFF, null, null, null,
                LocalDate.of(2175, 6, 1)));
        transactionService.post(writeOff.getTransaction().getId(), "tester");
        entityManager.flush();
        assertThat(writeOff.getGainLoss()).isEqualByComparingTo("-0");
        assertThat(lines(writeOff.getTransaction().getId())).containsExactlyInAnyOrder(
                "1.2.02 D1000000.00 C0.00", "1.2.01 D0.00 C1000000.00");
    }

    @Test
    @DisplayName("Voiding the posted journal returns the asset to the register and allows a new disposal; the record stays VOIDED")
    void voidRestores() {
        FixedAsset asset = postedAsset("12000000", 24, YearMonth.of(2176, 1));
        depreciate(asset, YearMonth.of(2176, 1));
        AssetDisposal disposal = disposalService.create(sale(asset, "13000000", LocalDate.of(2176, 2, 1)));
        transactionService.post(disposal.getTransaction().getId(), "tester");
        entityManager.flush();

        transactionService.voidTransaction(disposal.getTransaction().getId(), VoidReason.INPUT_ERROR, "Salah", "tester");
        entityManager.flush();

        FixedAsset restored = fixedAssetService.findById(asset.getId());
        assertThat(restored.getStatus()).isEqualTo(AssetStatus.ACTIVE);
        assertThat(restored.getDisposalDate()).isNull();
        assertThat(restored.getDisposalType()).isNull();
        assertThat(restored.getDisposalProceeds()).isNull();
        assertThat(restored.getGainLossOnDisposal()).isNull();
        assertThat(restored.getDisposalTransaction()).isNull();
        assertThat(disposalService.findById(disposal.getId()).getStatus()).isEqualTo(AssetDisposalStatus.VOIDED);

        AssetDisposal again = disposalService.create(sale(asset, "12500000", LocalDate.of(2176, 2, 2)));
        entityManager.flush();
        assertThat(again.getRecordNumber()).isNotEqualTo(disposal.getRecordNumber());
    }

    @Test
    @DisplayName("A voided disposal of a fully depreciated asset returns it to FULLY_DEPRECIATED")
    void voidRestoresFullyDepreciated() {
        FixedAsset asset = postedAsset("1000000", 1, YearMonth.of(2176, 3));
        depreciate(asset, YearMonth.of(2176, 3));
        AssetDisposal disposal = disposalService.create(command(asset, DisposalType.WRITTEN_OFF, null, null, null, LocalDate.of(2176, 4, 1)));
        transactionService.post(disposal.getTransaction().getId(), "tester");
        entityManager.flush();

        transactionService.voidTransaction(disposal.getTransaction().getId(), VoidReason.INPUT_ERROR, "Salah", "tester");
        entityManager.flush();

        assertThat(fixedAssetService.findById(asset.getId()).getStatus()).isEqualTo(AssetStatus.FULLY_DEPRECIATED);
    }

    @Test
    @DisplayName("Cancel removes the draft journal, keeps the record CANCELLED and unlocks the asset; a posted disposal cannot be cancelled")
    void cancel() {
        FixedAsset asset = postedAsset("12000000", 24, YearMonth.of(2177, 1));
        AssetDisposal disposal = disposalService.create(sale(asset, "13000000", LocalDate.of(2177, 1, 31)));
        UUID draftId = disposal.getTransaction().getId();
        entityManager.flush();

        disposalService.cancel(disposal.getId());
        entityManager.flush();

        assertThat(transactionRepository.findById(draftId)).isEmpty();
        assertThat(disposalService.findById(disposal.getId()).getStatus()).isEqualTo(AssetDisposalStatus.CANCELLED);
        assertThat(fixedAssetService.hasDraftDisposal(asset.getId())).isFalse();

        AssetDisposal second = disposalService.create(sale(asset, "13000000", LocalDate.of(2177, 1, 31)));
        transactionService.post(second.getTransaction().getId(), "tester");
        entityManager.flush();
        assertThatThrownBy(() -> disposalService.cancel(second.getId())).hasMessageContaining("void jurnalnya");
    }

    @Test
    @DisplayName("While a draft exists: no edit, no depreciation, no second disposal, and the generic screens cannot change the draft")
    void draftLocksTheAsset() {
        FixedAsset asset = postedAsset("12000000", 24, YearMonth.of(2177, 3));
        AssetDisposal disposal = disposalService.create(sale(asset, "13000000", LocalDate.of(2177, 3, 31)));
        entityManager.flush();
        UUID draftId = disposal.getTransaction().getId();

        assertThatThrownBy(() -> fixedAssetService.update(asset.getId(), asset)).hasMessageContaining("draf pelepasan");
        assertThatThrownBy(() -> disposalService.create(sale(asset, "1", LocalDate.of(2177, 3, 31)))).hasMessageContaining("draf pelepasan");
        assertThat(fixedAssetService.generatePeriodEntries(YearMonth.of(2177, 4)))
                .noneMatch(e -> e.getFixedAsset().getId().equals(asset.getId()));
        assertThatThrownBy(() -> transactionService.delete(draftId)).hasMessageContaining("menu Pelepasan Aset");
        Transaction draft = transactionService.findById(draftId);
        assertThatThrownBy(() -> transactionService.update(draft, draft)).hasMessageContaining("menu Pelepasan Aset");
        assertThatThrownBy(() -> transactionService.assertEditable(draftId)).hasMessageContaining("menu Pelepasan Aset");
    }

    @Test
    @DisplayName("A pending entry cannot post while a draft exists")
    void pendingEntryRefusedWhileDraft() {
        FixedAsset asset = postedAsset("12000000", 24, YearMonth.of(2177, 5));
        DepreciationEntry pending = fixedAssetService.generatePeriodEntries(YearMonth.of(2177, 5)).stream()
                .filter(e -> e.getFixedAsset().getId().equals(asset.getId())).findFirst().orElseThrow();
        fixedAssetService.skipDepreciationEntry(pending.getId());
        disposalService.create(sale(asset, "13000000", LocalDate.of(2177, 6, 1)));
        entityManager.flush();
        pending.setStatus(com.artivisi.accountingfinance.entity.DepreciationEntryStatus.PENDING);
        entityManager.flush();

        assertThatThrownBy(() -> fixedAssetService.postDepreciationEntry(pending.getId(), "tester"))
                .hasMessageContaining("draf pelepasan");
    }

    @Test
    @DisplayName("Book value must be settled: unposted purchase, pending entry, missing month, or a date before the last depreciation refuse")
    void preconditions() {
        FixedAsset unposted = newAsset("12000000", 24, YearMonth.of(2178, 1));
        assertThatThrownBy(() -> disposalService.create(sale(unposted, "1000", LocalDate.of(2178, 1, 31))))
                .hasMessageContaining("Jurnal pembelian aset masih draf");

        FixedAsset gap = postedAsset("12000000", 24, YearMonth.of(2178, 1));
        assertThatThrownBy(() -> disposalService.create(sale(gap, "1000", LocalDate.of(2178, 3, 1))))
                .hasMessageContaining("Penyusutan bulan 2178-01 belum dibuat");

        depreciate(gap, YearMonth.of(2178, 1));
        DepreciationEntry pending = fixedAssetService.generatePeriodEntries(YearMonth.of(2178, 2)).stream()
                .filter(e -> e.getFixedAsset().getId().equals(gap.getId())).findFirst().orElseThrow();
        assertThatThrownBy(() -> disposalService.create(sale(gap, "1000", LocalDate.of(2178, 3, 1))))
                .hasMessageContaining("PENDING");

        fixedAssetService.skipDepreciationEntry(pending.getId());
        assertThat(disposalService.create(sale(gap, "1000", LocalDate.of(2178, 3, 1))).getBookValue())
                .isEqualByComparingTo("11500000");

        FixedAsset depreciated = postedAsset("12000000", 24, YearMonth.of(2178, 4));
        depreciate(depreciated, YearMonth.of(2178, 4));
        assertThatThrownBy(() -> disposalService.create(sale(depreciated, "1000", LocalDate.of(2178, 4, 29))))
                .hasMessageContaining("sebelum penyusutan terakhir");
        assertThatThrownBy(() -> disposalService.create(sale(depreciated, "1000", LocalDate.of(2178, 4, 9))))
                .hasMessageContaining("sebelum tanggal pembelian");
        assertThatThrownBy(() -> disposalService.create(sale(depreciated, "1000", null)))
                .hasMessageContaining("Tanggal pelepasan wajib");
    }

    @Test
    @DisplayName("Input validation: reason, counterparty, proceeds and proceeds account")
    void inputValidation() {
        FixedAsset asset = postedAsset("12000000", 24, YearMonth.of(2178, 6));
        LocalDate date = LocalDate.of(2178, 6, 30);
        UUID bank = account(BANK).getId();
        var noReason = new AssetDisposalService.CreateDisposal(asset.getId(), DisposalType.SOLD, date, BigDecimal.TEN, bank, "Pembeli", "  ", "tester");
        assertThatThrownBy(() -> disposalService.create(noReason)).hasMessageContaining("Alasan pelepasan wajib");
        assertThatThrownBy(() -> disposalService.create(command(asset, DisposalType.SOLD, "10", bank, " ", date)))
                .hasMessageContaining("pembeli atau penerima wajib");
        assertThatThrownBy(() -> disposalService.create(command(asset, DisposalType.GIFTED, null, null, null, date)))
                .hasMessageContaining("pembeli atau penerima wajib");
        assertThatThrownBy(() -> disposalService.create(command(asset, DisposalType.SOLD, null, bank, "Pembeli", date)))
                .hasMessageContaining("Hasil penjualan wajib");
        assertThatThrownBy(() -> disposalService.create(command(asset, DisposalType.SOLD, "10", null, "Pembeli", date)))
                .hasMessageContaining("Akun penerimaan wajib");
        assertThatThrownBy(() -> disposalService.create(command(asset, DisposalType.GIFTED, "10", null, "Penerima", date)))
                .hasMessageContaining("hanya untuk tipe SOLD");
        assertThatThrownBy(() -> disposalService.create(command(asset, DisposalType.GIFTED, null, bank, "Penerima", date)))
                .hasMessageContaining("Akun penerimaan hanya untuk penjualan");
        assertThatThrownBy(() -> disposalService.create(command(asset, DisposalType.SOLD, "10", account("4.2.02").getId(), "Pembeli", date)))
                .hasMessageContaining("harus akun aset");
        assertThatThrownBy(() -> disposalService.create(command(asset, DisposalType.SOLD, "10", account("1.1").getId(), "Pembeli", date)))
                .hasMessageContaining("bukan akun induk");
    }

    @Test
    @DisplayName("A missing template or gain account name fails loudly")
    void missingConfiguration() {
        FixedAsset asset = postedAsset("12000000", 24, YearMonth.of(2179, 1));
        LocalDate date = LocalDate.of(2179, 1, 31);

        ChartOfAccount gain = account("4.2.02");
        gain.setAccountName("Laba Lain");
        entityManager.flush();
        assertThatThrownBy(() -> disposalService.create(sale(asset, "10", date)))
                .hasMessageContaining("tepat satu akun bernama \"Laba Penjualan Aset\", ditemukan 0");
        gain.setAccountName(AssetDisposalService.GAIN_ACCOUNT);
        entityManager.flush();

        JournalTemplate template = templateRepository.findByTemplateNameAndIsCurrentVersionTrue(AssetDisposalService.NON_SALE_TEMPLATE).orElseThrow();
        template.setTemplateName("Nama Lain");
        entityManager.flush();
        FixedAsset other = postedAsset("12000000", 24, YearMonth.of(2179, 1));
        assertThatThrownBy(() -> disposalService.create(command(other, DisposalType.WRITTEN_OFF, null, null, null, date)))
                .hasMessageContaining("Journal template not found: Penghapusan Aset Tetap");
    }

    @Test
    @DisplayName("Delete: a draft-only registration goes with its acquisition draft; posted purchase, depreciation or a disposal record refuse")
    void deleteRules() {
        FixedAsset mistake = newAsset("12000000", 24, YearMonth.of(2179, 3));
        UUID acquisitionId = acquisition(mistake).getId();
        assertThat(fixedAssetService.isDeletable(mistake)).isTrue();
        fixedAssetService.delete(mistake.getId());
        entityManager.flush();
        assertThat(transactionRepository.findById(acquisitionId)).isEmpty();

        FixedAsset posted = postedAsset("12000000", 24, YearMonth.of(2179, 3));
        assertThat(fixedAssetService.isDeletable(posted)).isFalse();
        assertThatThrownBy(() -> fixedAssetService.delete(posted.getId())).hasMessageContaining("Gunakan menu Lepas Aset");

        FixedAsset depreciated = newAsset("12000000", 24, YearMonth.of(2179, 3));
        depreciated.setDepreciationPeriodsCompleted(1);
        assertThat(fixedAssetService.isDeletable(depreciated)).isFalse();
        assertThatThrownBy(() -> fixedAssetService.delete(depreciated.getId())).hasMessageContaining("riwayat penyusutan");

        AssetDisposal cancelled = disposalService.create(sale(posted, "10", LocalDate.of(2179, 3, 31)));
        entityManager.flush();
        disposalService.cancel(cancelled.getId());
        transactionService.voidTransaction(acquisition(posted).getId(), VoidReason.INPUT_ERROR, "Salah", "tester");
        entityManager.flush();
        assertThatThrownBy(() -> fixedAssetService.delete(posted.getId())).hasMessageContaining("catatan pelepasan");
    }

    // ==================== helpers ====================

    private AssetDisposalService.CreateDisposal sale(FixedAsset asset, String proceeds, LocalDate date) {
        return command(asset, DisposalType.SOLD, proceeds, account(BANK).getId(), "Pembeli", date);
    }

    private AssetDisposalService.CreateDisposal command(FixedAsset asset, DisposalType type, String proceeds, UUID proceedsAccount,
                                                        String counterparty, LocalDate date) {
        return new AssetDisposalService.CreateDisposal(asset.getId(), type, date, proceeds == null ? null : new BigDecimal(proceeds),
                proceedsAccount, counterparty, "Tidak terpakai", "tester");
    }

    private FixedAsset postedAsset(String cost, int months, YearMonth start) {
        FixedAsset asset = newAsset(cost, months, start);
        transactionService.post(acquisition(asset).getId(), "tester");
        entityManager.flush();
        return asset;
    }

    private Transaction acquisition(FixedAsset asset) {
        return transactionRepository.findBySourceDocumentTypeAndSourceDocumentIdOrderByTransactionDateAsc(
                FixedAssetService.ACQUISITION_SOURCE_TYPE, asset.getId()).getFirst();
    }

    private FixedAsset newAsset(String cost, int months, YearMonth start) {
        AssetCategory category = categoryRepository.findByCode("KOMPUTER").orElseThrow();
        FixedAsset asset = new FixedAsset();
        asset.setAssetCode("DSP-" + UUID.randomUUID().toString().substring(0, 8) + "-" + SEQUENCE.incrementAndGet());
        asset.setName("Aset " + asset.getAssetCode());
        asset.setCategory(category);
        asset.setPurchaseDate(start.atDay(10));
        asset.setPurchaseCost(new BigDecimal(cost));
        asset.setResidualValue(BigDecimal.ZERO);
        asset.setUsefulLifeMonths(months);
        asset.setDepreciationMethod(DepreciationMethod.STRAIGHT_LINE);
        asset.setDepreciationStartDate(start.atDay(1));
        asset.setFundingAccount(account(BANK));
        return fixedAssetService.create(asset);
    }

    private void depreciate(FixedAsset asset, YearMonth period) {
        DepreciationEntry entry = fixedAssetService.generatePeriodEntries(period).stream()
                .filter(e -> e.getFixedAsset().getId().equals(asset.getId())).findFirst().orElseThrow();
        fixedAssetService.postDepreciationEntry(entry.getId(), "tester");
        entryRepository.flush();
    }

    private ChartOfAccount account(String code) {
        return accountRepository.findByAccountCode(code).orElseThrow();
    }

    private List<String> lines(UUID transactionId) {
        return journalEntryRepository.findByTransactionIdWithAccount(transactionId).stream()
                .map(l -> l.getAccount().getAccountCode()
                        + " D" + l.getDebitAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString()
                        + " C" + l.getCreditAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString())
                .toList();
    }
}
