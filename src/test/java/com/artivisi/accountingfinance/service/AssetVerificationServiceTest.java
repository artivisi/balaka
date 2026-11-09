package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.entity.AssetCategory;
import com.artivisi.accountingfinance.entity.AssetCondition;
import com.artivisi.accountingfinance.entity.AssetDisposal;
import com.artivisi.accountingfinance.entity.AssetDisposalStatus;
import com.artivisi.accountingfinance.entity.AssetMovement;
import com.artivisi.accountingfinance.entity.AssetMovementSource;
import com.artivisi.accountingfinance.entity.AssetVerification;
import com.artivisi.accountingfinance.entity.AssetVerificationFinding;
import com.artivisi.accountingfinance.entity.AssetVerificationResult;
import com.artivisi.accountingfinance.entity.DepreciationEntry;
import com.artivisi.accountingfinance.entity.DepreciationMethod;
import com.artivisi.accountingfinance.entity.DisposalType;
import com.artivisi.accountingfinance.entity.FixedAsset;
import com.artivisi.accountingfinance.repository.AssetCategoryRepository;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
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

/** Yearly verification, register movement and the read API for #45 and #51. Reserved test years 2180-2184. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "tester")
@DisplayName("Asset verification and register queries")
class AssetVerificationServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Autowired private AssetVerificationService verificationService;
    @Autowired private AssetRegisterQueryService queryService;
    @Autowired private AssetDisposalService disposalService;
    @Autowired private FixedAssetService fixedAssetService;
    @Autowired private TransactionService transactionService;
    @Autowired private AssetCategoryRepository categoryRepository;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("An old asset with no identity lists everything missing; filling the fields and verifying clears each exception")
    void exceptionsClearAsFieldsAreFilled() throws IOException {
        FixedAsset asset = asset("10000000", YearMonth.of(2180, 2));

        assertThat(exceptionsOf(asset, 2180)).containsExactly(
                AssetVerificationFinding.NOT_VERIFIED, AssetVerificationFinding.MISSING_BRAND_MODEL,
                AssetVerificationFinding.MISSING_SERIAL, AssetVerificationFinding.MISSING_CUSTODIAN,
                AssetVerificationFinding.MISSING_LOCATION, AssetVerificationFinding.MISSING_CONDITION,
                AssetVerificationFinding.MISSING_PHOTO);

        verify(asset, 2180, LocalDate.of(2180, 12, 1), AssetCondition.GOOD, "Gudang", "Budi");
        entityManager.flush();
        assertThat(exceptionsOf(asset, 2180)).containsExactly(
                AssetVerificationFinding.MISSING_BRAND_MODEL, AssetVerificationFinding.MISSING_SERIAL,
                AssetVerificationFinding.MISSING_PHOTO);

        entityManager.flush();
        FixedAsset data = fixedAssetService.findById(asset.getId());
        entityManager.detach(data);
        data.setBrand("Lenovo");
        fixedAssetService.update(asset.getId(), data);
        entityManager.flush();
        assertThat(exceptionsOf(asset, 2180)).contains(AssetVerificationFinding.MISSING_BRAND_MODEL);

        data.setModel("T14");
        data.setSerialNumber("SN-1");
        fixedAssetService.update(asset.getId(), data);
        fixedAssetService.attachPhoto(asset.getId(), new MockMultipartFile("file", "a.png", "image/png", PNG), "tester");
        entityManager.flush();
        assertThat(exceptionsOf(asset, 2180)).isEmpty();
        assertThat(verificationService.exceptions(2180)).noneMatch(r -> r.asset().getId().equals(asset.getId()));
    }

    @Test
    @DisplayName("Each year starts unverified; NOT_FOUND and major damage are exceptions until replaced")
    void perYearResults() {
        FixedAsset asset = asset("10000000", YearMonth.of(2180, 3));
        verify(asset, 2180, LocalDate.of(2180, 11, 1), AssetCondition.MAJOR_DAMAGE, "Gudang", "Budi");
        entityManager.flush();
        assertThat(exceptionsOf(asset, 2180)).contains(AssetVerificationFinding.MAJOR_DAMAGE);
        assertThat(exceptionsOf(asset, 2181)).contains(AssetVerificationFinding.NOT_VERIFIED);

        verificationService.record(new AssetVerificationService.RecordVerification(asset.getId(), 2181, LocalDate.of(2181, 11, 1),
                AssetVerificationResult.NOT_FOUND, null, null, null, "Hilang", "tester"));
        entityManager.flush();
        assertThat(exceptionsOf(asset, 2181)).contains(AssetVerificationFinding.NOT_FOUND);
        FixedAsset unchanged = fixedAssetService.findById(asset.getId());
        assertThat(unchanged.getLocation()).isEqualTo("Gudang");
        assertThat(unchanged.getLastVerifiedDate()).isEqualTo(LocalDate.of(2180, 11, 1));

        AssetVerification replaced = verify(asset, 2181, LocalDate.of(2181, 12, 1), AssetCondition.GOOD, "Gudang", "Budi");
        entityManager.flush();
        assertThat(exceptionsOf(asset, 2181)).doesNotContain(AssetVerificationFinding.NOT_FOUND, AssetVerificationFinding.NOT_VERIFIED);
        assertThat(verificationService.historyOf(asset.getId())).hasSize(2).first().satisfies(v -> {
            assertThat(v.getId()).isEqualTo(replaced.getId());
            assertThat(v.getVerificationYear()).isEqualTo(2181);
            assertThat(v.getResult()).isEqualTo(AssetVerificationResult.FOUND);
        });
    }

    @Test
    @DisplayName("FOUND writes condition, location, custodian and last verified date to the register and logs one movement; an earlier date does not move it back")
    void foundWritesRegister() {
        FixedAsset asset = asset("10000000", YearMonth.of(2180, 4));
        verify(asset, 2181, LocalDate.of(2181, 6, 15), AssetCondition.MINOR_DAMAGE, " Lantai 2 ", "Siti");
        entityManager.flush();

        FixedAsset after = fixedAssetService.findById(asset.getId());
        assertThat(after.getLocation()).isEqualTo("Lantai 2");
        assertThat(after.getCustodian()).isEqualTo("Siti");
        assertThat(after.getCondition()).isEqualTo(AssetCondition.MINOR_DAMAGE);
        assertThat(after.getLastVerifiedDate()).isEqualTo(LocalDate.of(2181, 6, 15));
        assertThat(queryService.findMovementsOfAsset(asset.getId())).singleElement().satisfies(m -> {
            assertThat(m.getSource()).isEqualTo(AssetMovementSource.VERIFICATION);
            assertThat(m.getMovementDate()).isEqualTo(LocalDate.of(2181, 6, 15));
            assertThat(m.getFromLocation()).isNull();
            assertThat(m.getToLocation()).isEqualTo("Lantai 2");
            assertThat(m.getToCustodian()).isEqualTo("Siti");
            assertThat(m.getToCondition()).isEqualTo(AssetCondition.MINOR_DAMAGE);
            assertThat(m.getRecordedBy()).isEqualTo("tester");
        });

        verify(asset, 2180, LocalDate.of(2180, 12, 1), AssetCondition.MINOR_DAMAGE, "Lantai 2", "Siti");
        entityManager.flush();
        assertThat(fixedAssetService.findById(asset.getId()).getLastVerifiedDate()).isEqualTo(LocalDate.of(2181, 6, 15));
        assertThat(queryService.findMovementsOfAsset(asset.getId())).as("same values, no new row").hasSize(1);
    }

    @Test
    @DisplayName("Edit logs a movement only when custodian, location or condition changes; blank equals absent")
    void editMovements() {
        FixedAsset asset = asset("10000000", YearMonth.of(2182, 1));
        entityManager.flush();
        FixedAsset data = fixedAssetService.findById(asset.getId());
        entityManager.detach(data);
        data.setLocation("  ");
        data.setBrand("Lenovo");
        fixedAssetService.update(asset.getId(), data);
        entityManager.flush();
        assertThat(queryService.findMovementsOfAsset(asset.getId())).isEmpty();

        data.setCustodian("Budi");
        fixedAssetService.update(asset.getId(), data);
        entityManager.flush();
        LocalDate today = LocalDate.now();
        List<AssetMovement> movements = queryService.findMovements(today, today);
        assertThat(movements).filteredOn(m -> m.getFixedAsset().getId().equals(asset.getId())).singleElement().satisfies(m -> {
            assertThat(m.getSource()).isEqualTo(AssetMovementSource.EDIT);
            assertThat(m.getFromCustodian()).isNull();
            assertThat(m.getToCustodian()).isEqualTo("Budi");
        });
    }

    @Test
    @DisplayName("Checklist holds assets bought by year end and not disposed by year end; a disposed asset refuses verification")
    void checklistScope() {
        FixedAsset later = asset("10000000", YearMonth.of(2183, 1));
        FixedAsset kept = asset("10000000", YearMonth.of(2182, 2));
        FixedAsset sold = postAcquisition(asset("12000000", YearMonth.of(2182, 3)));
        AssetDisposal disposal = disposalService.create(new AssetDisposalService.CreateDisposal(sold.getId(), DisposalType.SOLD,
                LocalDate.of(2182, 3, 31), new BigDecimal("5000000"), accountRepository.findByAccountCode("1.1.02").orElseThrow().getId(),
                "Pembeli", "Dijual", "tester"));
        entityManager.flush();

        assertThat(codes(2182)).contains(kept.getAssetCode(), sold.getAssetCode()).doesNotContain(later.getAssetCode());

        transactionService.post(disposal.getTransaction().getId(), "tester");
        entityManager.flush();
        assertThat(codes(2181)).doesNotContain(kept.getAssetCode(), sold.getAssetCode());
        assertThat(codes(2182)).contains(kept.getAssetCode()).doesNotContain(sold.getAssetCode());
        assertThat(codes(2183)).contains(kept.getAssetCode(), later.getAssetCode()).doesNotContain(sold.getAssetCode());

        assertThatThrownBy(() -> verify(sold, 2182, LocalDate.of(2182, 12, 1), AssetCondition.GOOD, "A", "B"))
                .hasMessageContaining("sudah dilepas");
    }

    @Test
    @DisplayName("Invalid input: date outside the year or before purchase, FOUND without condition, location or custodian")
    void validation() {
        FixedAsset asset = asset("10000000", YearMonth.of(2180, 5));
        assertThatThrownBy(() -> verify(asset, 2180, LocalDate.of(2181, 1, 1), AssetCondition.GOOD, "A", "B"))
                .hasMessageContaining("harus di tahun 2180");
        assertThatThrownBy(() -> verify(asset, 2180, LocalDate.of(2180, 1, 1), AssetCondition.GOOD, "A", "B"))
                .hasMessageContaining("sebelum tanggal pembelian");
        assertThatThrownBy(() -> verify(asset, 2180, LocalDate.of(2180, 12, 1), null, "A", "B")).hasMessageContaining("wajib diisi kondisi");
        assertThatThrownBy(() -> verify(asset, 2180, LocalDate.of(2180, 12, 1), AssetCondition.GOOD, " ", "B")).hasMessageContaining("wajib diisi kondisi");
        assertThatThrownBy(() -> verify(asset, 2180, LocalDate.of(2180, 12, 1), AssetCondition.GOOD, "A", null)).hasMessageContaining("wajib diisi kondisi");
    }

    @Test
    @DisplayName("Photo: only JPEG or PNG; replacing keeps one; removing clears it; loading an absent photo fails")
    void photo() throws IOException {
        FixedAsset asset = asset("10000000", YearMonth.of(2180, 6));
        assertThatThrownBy(() -> fixedAssetService.attachPhoto(asset.getId(),
                new MockMultipartFile("file", "a.pdf", "application/pdf", "%PDF-1.4".getBytes()), "tester"))
                .hasMessageContaining("JPEG atau PNG");
        assertThatThrownBy(() -> fixedAssetService.loadPhoto(asset.getId())).hasMessageContaining("belum punya foto");
        assertThatThrownBy(() -> fixedAssetService.removePhoto(asset.getId())).hasMessageContaining("belum punya foto");

        fixedAssetService.attachPhoto(asset.getId(), new MockMultipartFile("file", "a.png", "image/png", PNG), "tester");
        UUID first = fixedAssetService.findById(asset.getId()).getPhotoDocument().getId();
        fixedAssetService.attachPhoto(asset.getId(), new MockMultipartFile("file", "b.png", "image/png", PNG), "tester");
        entityManager.flush();
        FixedAsset replaced = fixedAssetService.findById(asset.getId());
        assertThat(replaced.getPhotoDocument().getId()).isNotEqualTo(first);
        assertThat(fixedAssetService.loadPhoto(asset.getId()).contentType()).isEqualTo("image/png");

        fixedAssetService.removePhoto(asset.getId());
        entityManager.flush();
        assertThat(fixedAssetService.findById(asset.getId()).getPhotoDocument()).isNull();
    }

    @Test
    @DisplayName("Cost and accumulated depreciation roll forward exactly per category; a disposal counts from its posted date; drafts do not")
    void costMovement() {
        AssetCategory category = newCategory();
        FixedAsset sold = postAcquisition(asset(category, "12000000", YearMonth.of(2184, 12).minusYears(1)));
        depreciate(sold, YearMonth.of(2183, 12));
        depreciate(sold, YearMonth.of(2184, 1));
        depreciate(sold, YearMonth.of(2184, 2));
        FixedAsset bought = postAcquisition(asset(category, "6000000", YearMonth.of(2184, 6)));
        asset(category, "3000000", YearMonth.of(2185, 1));

        AssetDisposal draft = disposalService.create(new AssetDisposalService.CreateDisposal(sold.getId(), DisposalType.SOLD,
                LocalDate.of(2184, 3, 15), new BigDecimal("5000000"), accountRepository.findByAccountCode("1.1.02").orElseThrow().getId(),
                "Pembeli", "Dijual", "tester"));
        entityManager.flush();

        var beforePosting = row(2184, category);
        assertThat(beforePosting.disposals()).isEqualByComparingTo("0");
        assertThat(beforePosting.closingCost()).isEqualByComparingTo("18000000");

        transactionService.post(draft.getTransaction().getId(), "tester");
        entityManager.flush();

        var movement = row(2184, category);
        assertThat(movement.openingCost()).isEqualByComparingTo("12000000");
        assertThat(movement.additions()).isEqualByComparingTo("6000000");
        assertThat(movement.disposals()).isEqualByComparingTo("12000000");
        assertThat(movement.closingCost()).isEqualByComparingTo("6000000");
        assertThat(movement.openingAccumulated()).isEqualByComparingTo("500000");
        assertThat(movement.depreciation()).isEqualByComparingTo("1000000");
        assertThat(movement.disposedAccumulated()).isEqualByComparingTo("1500000");
        assertThat(movement.closingAccumulated()).isEqualByComparingTo("0");

        var nextYear = row(2185, category);
        assertThat(nextYear.openingCost()).as("sold asset is gone").isEqualByComparingTo("6000000");
        assertThat(nextYear.additions()).isEqualByComparingTo("3000000");

        var previous = row(2183, category);
        assertThat(previous.additions()).isEqualByComparingTo("12000000");
        assertThat(previous.closingCost()).isEqualByComparingTo("12000000");
        assertThat(previous.closingAccumulated()).isEqualByComparingTo("500000");

        assertThat(queryService.findDisposals(LocalDate.of(2184, 1, 1), LocalDate.of(2184, 12, 31), AssetDisposalStatus.POSTED))
                .extracting(AssetDisposal::getId).containsExactly(draft.getId());
        assertThat(queryService.findDisposals(LocalDate.of(2184, 1, 1), LocalDate.of(2184, 12, 31), AssetDisposalStatus.DRAFT))
                .isEmpty();
        assertThat(queryService.findDisposals(LocalDate.of(2185, 1, 1), LocalDate.of(2185, 12, 31), null)).isEmpty();
        assertThat(queryService.findDisposalsOfAsset(sold.getId())).hasSize(1);
        assertThat(bought.getStatus().name()).isEqualTo("ACTIVE");
    }

    // ==================== helpers ====================

    private List<AssetVerificationFinding> exceptionsOf(FixedAsset asset, int year) {
        return verificationService.checklist(year).stream()
                .filter(r -> r.asset().getId().equals(asset.getId())).findFirst().orElseThrow().exceptions();
    }

    private List<String> codes(int year) {
        return verificationService.checklist(year).stream().map(r -> r.asset().getAssetCode()).toList();
    }

    private AssetVerification verify(FixedAsset asset, int year, LocalDate date, AssetCondition condition, String location, String custodian) {
        return verificationService.record(new AssetVerificationService.RecordVerification(asset.getId(), year, date,
                AssetVerificationResult.FOUND, condition, location, custodian, null, "tester"));
    }

    private AssetRegisterQueryService.CostMovementRow row(int year, AssetCategory category) {
        return queryService.costMovement(year).rows().stream()
                .filter(r -> r.categoryCode().equals(category.getCode())).findFirst().orElseThrow();
    }

    private FixedAsset postAcquisition(FixedAsset asset) {
        transactionRepository.findBySourceDocumentTypeAndSourceDocumentIdOrderByTransactionDateAsc(
                        FixedAssetService.ACQUISITION_SOURCE_TYPE, asset.getId())
                .forEach(t -> transactionService.post(t.getId(), "tester"));
        entityManager.flush();
        return asset;
    }

    private void depreciate(FixedAsset asset, YearMonth period) {
        DepreciationEntry entry = fixedAssetService.generatePeriodEntries(period).stream()
                .filter(e -> e.getFixedAsset().getId().equals(asset.getId())).findFirst().orElseThrow();
        fixedAssetService.postDepreciationEntry(entry.getId(), "tester");
        entityManager.flush();
    }

    private AssetCategory newCategory() {
        AssetCategory source = categoryRepository.findByCode("KOMPUTER").orElseThrow();
        AssetCategory category = new AssetCategory();
        category.setCode("VRF-" + UUID.randomUUID().toString().substring(0, 8));
        category.setName("Kategori " + category.getCode());
        category.setDepreciationMethod(DepreciationMethod.STRAIGHT_LINE);
        category.setUsefulLifeMonths(24);
        category.setAssetAccount(source.getAssetAccount());
        category.setAccumulatedDepreciationAccount(source.getAccumulatedDepreciationAccount());
        category.setDepreciationExpenseAccount(source.getDepreciationExpenseAccount());
        return categoryRepository.save(category);
    }

    private FixedAsset asset(String cost, YearMonth start) {
        return asset(categoryRepository.findByCode("KOMPUTER").orElseThrow(), cost, start);
    }

    private FixedAsset asset(AssetCategory category, String cost, YearMonth start) {
        FixedAsset asset = new FixedAsset();
        asset.setAssetCode("VRF-" + UUID.randomUUID().toString().substring(0, 13));
        asset.setName("Aset " + asset.getAssetCode());
        asset.setCategory(category);
        asset.setPurchaseDate(start.atDay(10));
        asset.setPurchaseCost(new BigDecimal(cost));
        asset.setResidualValue(BigDecimal.ZERO);
        asset.setUsefulLifeMonths(24);
        asset.setDepreciationMethod(DepreciationMethod.STRAIGHT_LINE);
        asset.setDepreciationStartDate(start.atDay(1));
        asset.setFundingAccount(accountRepository.findByAccountCode("1.1.02").orElseThrow());
        return fixedAssetService.create(asset);
    }
}
