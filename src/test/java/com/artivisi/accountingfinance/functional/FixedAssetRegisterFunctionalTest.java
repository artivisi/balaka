package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.AssetDisposalStatus;
import com.artivisi.accountingfinance.entity.AssetStatus;
import com.artivisi.accountingfinance.entity.DepreciationMethod;
import com.artivisi.accountingfinance.entity.FixedAsset;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.AssetCategoryRepository;
import com.artivisi.accountingfinance.repository.AssetDisposalRepository;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.FixedAssetRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.service.AssetDisposalService;
import com.artivisi.accountingfinance.service.FixedAssetService;
import com.artivisi.accountingfinance.service.TransactionService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.FilePayload;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/** Register fields, photo, yearly verification and disposal through the web UI on the IT service seed pack. Reserved test year 2186. */
@DisplayName("Fixed asset register UI")
@Import(ServiceTestDataInitializer.class)
class FixedAssetRegisterFunctionalTest extends PlaywrightTestBase {

    private static final int YEAR = 2186;
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M/wHwAEBgIApD5fRAAAAABJRU5ErkJggg==");

    @Autowired private FixedAssetService fixedAssetService;
    @Autowired private TransactionService transactionService;
    @Autowired private FixedAssetRepository assetRepository;
    @Autowired private AssetCategoryRepository categoryRepository;
    @Autowired private AssetDisposalRepository disposalRepository;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private JournalEntryRepository journalEntryRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("Identity fields save from the form; changing custodian and condition after depreciation logs a movement")
    void identityFieldsAndMovement() {
        FixedAsset asset = asset("IDN-", "12000000", YearMonth.of(YEAR, 1));
        navigateTo("/assets/" + asset.getId() + "/edit");
        waitForPageLoad();
        page.locator("#brand").fill("Lenovo");
        page.locator("#model").fill("ThinkPad T14");
        page.locator("#serialNumber").fill("SN-" + asset.getAssetCode());
        page.locator("#custodian").fill("Budi Santoso");
        page.locator("#location").fill("Lantai 2");
        page.locator("#condition").selectOption("MINOR_DAMAGE");
        page.locator("button[type='submit']").last().click();
        waitForPageLoad();

        assertThat(page.getByTestId("flash-success")).hasText("Aset berhasil diperbarui");
        assertThat(page.locator("#asset-brand")).hasText("Lenovo");
        assertThat(page.locator("#asset-model")).hasText("ThinkPad T14");
        assertThat(page.locator("#asset-serial")).hasText("SN-" + asset.getAssetCode());
        assertThat(page.locator("#asset-custodian")).hasText("Budi Santoso");
        assertThat(page.locator("#asset-condition")).hasText("Rusak Ringan");
        assertThat(page.getByTestId("movement-row")).hasCount(1);
        assertThat(page.getByTestId("movement-row")).containsText("Pemegang - ke Budi Santoso");
        assertThat(page.getByTestId("movement-row")).containsText("Lokasi - ke Lantai 2");
        assertThat(page.getByTestId("movement-row")).containsText("Kondisi - ke Rusak Ringan");
        assertThat(page.getByTestId("movement-row")).containsText("(admin)");

        FixedAsset stored = assetRepository.findById(asset.getId()).orElseThrow();
        assertThat(stored.getBrand()).isEqualTo("Lenovo");
        assertThat(stored.getModel()).isEqualTo("ThinkPad T14");
        assertThat(stored.getCustodian()).isEqualTo("Budi Santoso");
    }

    @Test
    @DisplayName("Photo uploads and is served as an image, a non-image is refused, and removing it empties the card")
    void photo() {
        FixedAsset asset = asset("PHT-", "1000000", YearMonth.of(YEAR, 1));
        navigateTo("/assets/" + asset.getId());
        waitForPageLoad();
        assertThat(page.locator("#asset-photo-empty")).isVisible();

        page.locator("#photo-file").setInputFiles(new FilePayload("catatan.pdf", "application/pdf", "%PDF-1.4".getBytes()));
        page.evaluate("document.getElementById('photo-file').removeAttribute('accept')");
        page.locator("#btn-photo-upload").click();
        waitForPageLoad();
        assertThat(page.getByTestId("flash-error")).hasText("Foto aset harus berformat JPEG atau PNG");

        page.locator("#photo-file").setInputFiles(new FilePayload("aset.png", "image/png", PNG));
        page.locator("#btn-photo-upload").click();
        waitForPageLoad();
        assertThat(page.getByTestId("flash-success")).hasText("Foto aset tersimpan");
        assertThat(page.locator("#asset-photo")).isVisible();
        assertThat(page.locator("#asset-photo").evaluate("img => img.naturalWidth")).isEqualTo(1);
        APIResponse served = page.request().get(baseUrl() + "/assets/" + asset.getId() + "/photo");
        assertThat(served.status()).isEqualTo(200);
        assertThat(served.headers().get("content-type")).isEqualTo("image/png");
        assertThat(served.body()).isEqualTo(PNG);

        page.locator("#btn-photo-delete").click();
        waitForPageLoad();
        assertThat(page.getByTestId("flash-success")).hasText("Foto aset dihapus");
        assertThat(page.locator("#asset-photo-empty")).isVisible();
    }

    @Test
    @DisplayName("Checklist lists the asset with its exceptions; saving a result updates the register and removes those exceptions")
    void verificationChecklist() {
        FixedAsset asset = asset("VRF-", "1000000", YearMonth.of(YEAR, 2));
        assertThat(page.locator("#nav-asset-verification")).hasAttribute("href", "/assets/verification");
        navigateTo("/assets/verification?year=" + YEAR);
        waitForPageLoad();

        Locator exception = page.locator("[data-testid='exception-row'][data-code='" + asset.getAssetCode() + "'] td").nth(1);
        assertThat(exception).hasText("Belum diverifikasi tahun ini, Merek/model kosong, Nomor seri kosong, Pemegang kosong, Lokasi kosong, Kondisi kosong, Foto kosong");
        Locator card = page.locator("[data-testid='checklist-card'][data-code='" + asset.getAssetCode() + "']");
        assertThat(card.getByTestId("checklist-result")).hasText("Belum diperiksa");
        String id = asset.getId().toString();

        card.locator("#date-" + id).fill(YEAR + "-12-15");
        card.locator("#condition-" + id).selectOption("GOOD");
        card.locator("#custodian-" + id).fill("Siti");
        card.getByTestId("btn-verify").click();
        waitForPageLoad();
        assertThat(page.getByTestId("flash-error")).hasText("Aset yang ditemukan wajib diisi kondisi, lokasi, dan pemegangnya");
        assertThat(assetRepository.findById(asset.getId()).orElseThrow().getLastVerifiedDate()).isNull();

        card = page.locator("[data-testid='checklist-card'][data-code='" + asset.getAssetCode() + "']");
        card.locator("#date-" + id).fill(YEAR + "-12-15");
        card.locator("#condition-" + id).selectOption("GOOD");
        card.locator("#location-" + id).fill("Gudang");
        card.locator("#custodian-" + id).fill("Siti");
        card.getByTestId("btn-verify").click();
        waitForPageLoad();

        assertThat(page.getByTestId("flash-success")).hasText("Hasil verifikasi tersimpan");
        assertThat(page.locator("[data-testid='checklist-card'][data-code='" + asset.getAssetCode() + "']").getByTestId("checklist-result"))
                .hasText("Ditemukan");
        assertThat(page.locator("[data-testid='exception-row'][data-code='" + asset.getAssetCode() + "'] td").nth(1))
                .hasText("Merek/model kosong, Nomor seri kosong, Foto kosong");
        takeManualScreenshot("assets-verification");

        navigateTo("/assets/" + asset.getId());
        waitForPageLoad();
        assertThat(page.locator("#asset-custodian")).hasText("Siti");
        assertThat(page.locator("#asset-condition")).hasText("Baik");
        assertThat(page.locator("#asset-last-verified")).hasText("15 Des " + YEAR);
        assertThat(page.getByTestId("verification-row")).containsText("ditemukan (admin)");
    }

    @Test
    @DisplayName("Sale through the form: the effect previews, the record is generated, the asset stays locked until the draft journal posts, then it is DISPOSED with the exact journal")
    void saleFlow() {
        FixedAsset asset = postedAsset("SAL-", "12000000", YearMonth.of(YEAR, 3));
        navigateTo("/assets/" + asset.getId() + "/dispose");
        waitForPageLoad();
        page.locator("#disposalType").selectOption("SOLD");
        page.locator("#disposalDate").fill(YEAR + "-03-31");
        page.locator("#proceeds").fill("13000000");
        assertThat(page.locator("#effect-gain-loss")).hasText("Rp 1,000,000");
        assertThat(page.locator("#effect-book-value")).hasText("Rp 12,000,000");
        assertThat(page.locator("#effect-taxable")).hasText("Rp 1,000,000");
        assertThat(page.locator("#effect-correction")).hasText("Rp 0");
        assertThat(page.locator("#effect-ppn")).hasText("Rp 1,430,000");
        assertThat(page.locator("#effect-note")).containsText("belum diverifikasi");
        takeManualScreenshot("assets-disposal-form");

        page.locator("#proceedsAccount").pressSequentially("1.1.02");
        page.locator("[data-testid='account-picker-result']:has-text('Bank BCA')").click();
        page.locator("#counterparty").fill("PT Pembeli Aset");
        page.locator("#reason").fill("Diganti unit baru");
        page.locator("#btn-dispose").click();
        waitForPageLoad();

        var disposal = disposalRepository.findByAssetId(asset.getId()).getFirst();
        assertThat(page).hasURL(baseUrl() + "/assets/disposals/" + disposal.getId());
        assertThat(page.getByTestId("flash-success")).containsText(disposal.getRecordNumber());
        assertThat(page.getByTestId("flash-success")).containsText("setelah draf jurnalnya diposting");
        assertThat(page.locator("#disposal-status")).hasText("Draf");
        assertThat(page.locator("#record-type")).hasText("Dijual");
        assertThat(page.locator("#record-counterparty")).hasText("PT Pembeli Aset");
        assertThat(page.locator("#record-reason")).hasText("Diganti unit baru");
        assertThat(page.locator("#record-cost")).hasText("Rp 12,000,000");
        assertThat(page.locator("#record-proceeds-account")).hasText("1.1.02 - Bank BCA");
        assertThat(page.locator("#effect-gain-loss")).hasText("Rp 1,000,000");
        takeManualScreenshot("assets-disposal-record");

        navigateTo("/assets/" + asset.getId());
        waitForPageLoad();
        assertThat(page.locator("#asset-status")).hasText("Aktif");
        assertThat(page.locator("#disposal-draft-banner")).isVisible();
        assertThat(page.locator("#btn-edit, #btn-dispose, #btn-delete")).hasCount(0);
        navigateTo("/assets/" + asset.getId() + "/dispose");
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/assets/" + asset.getId());

        navigateTo("/assets/disposals/" + disposal.getId());
        waitForPageLoad();
        String transactionPath = page.locator("#record-transaction").getAttribute("href");
        page.locator("#record-transaction").click();
        waitForPageLoad();
        page.onDialog(dialog -> dialog.accept());
        page.waitForResponse(r -> r.url().endsWith(transactionPath) && "GET".equals(r.request().method()),
                () -> page.locator("#btn-post").click());
        waitForPageLoad();
        assertThat(page.locator("#btn-post")).hasCount(0);

        navigateTo("/assets/" + asset.getId());
        waitForPageLoad();
        assertThat(page.locator("#asset-status")).hasText("Dilepas");
        assertThat(page.locator("#disposal-draft-banner")).hasCount(0);
        assertThat(page.locator("section")).containsText("Rp 13,000,000");
        assertThat(page.locator("#btn-edit, #btn-dispose, #btn-delete")).hasCount(0);

        FixedAsset stored = assetRepository.findById(asset.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(AssetStatus.DISPOSED);
        assertThat(stored.getGainLossOnDisposal()).isEqualByComparingTo("1000000");
        var transactionId = disposalRepository.findById(disposal.getId()).orElseThrow();
        assertThat(transactionId.getStatus()).isEqualTo(AssetDisposalStatus.POSTED);
        List<String> lines = journalEntryRepository.findByTransactionIdWithAccount(
                        disposalRepository.findByAssetId(asset.getId()).getFirst().getTransaction().getId()).stream()
                .map(l -> l.getAccount().getAccountCode()
                        + " D" + l.getDebitAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString()
                        + " C" + l.getCreditAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString())
                .toList();
        assertThat(lines).containsExactlyInAnyOrder("1.1.02 D13000000.00 C0.00", "1.2.01 D0.00 C12000000.00", "4.2.02 D0.00 C1000000.00");

        navigateTo("/assets/disposals?year=" + YEAR);
        waitForPageLoad();
        Locator row = page.locator("[data-testid='disposal-row'][data-number='" + disposal.getRecordNumber() + "'] td");
        assertThat(row.nth(3)).hasText("Dijual");
        assertThat(row.nth(4)).hasText("13,000,000");
        assertThat(row.nth(6)).hasText("1,000,000");
        assertThat(row.nth(7)).hasText("Diposting");
    }

    @Test
    @DisplayName("Gift: effect shows the book value as a corrected loss; a missing recipient re-renders with the entries kept; cancel removes the draft and unlocks")
    void giftFlowAndCancel() {
        FixedAsset asset = postedAsset("GFT-", "6000000", YearMonth.of(YEAR, 4));
        navigateTo("/assets/" + asset.getId() + "/dispose");
        waitForPageLoad();
        page.locator("#disposalType").selectOption("GIFTED");
        assertThat(page.locator("#effect-proceeds")).hasText("Rp 0");
        assertThat(page.locator("#effect-gain-loss")).hasText("Rp -6,000,000");
        assertThat(page.locator("#effect-taxable")).hasText("Rp 0");
        assertThat(page.locator("#effect-correction")).hasText("Rp 6,000,000");
        assertThat(page.locator("#effect-ppn")).hasText("Tidak dihitung");
        page.locator("#disposalDate").fill(YEAR + "-04-30");
        page.locator("#reason").fill("Hibah ke yayasan");
        page.locator("#btn-dispose").click();
        waitForPageLoad();

        assertThat(page.getByTestId("flash-error")).hasText("Nama pembeli atau penerima wajib diisi");
        assertThat(page.locator("#disposalType")).hasValue("GIFTED");
        assertThat(page.locator("#disposalDate")).hasValue(YEAR + "-04-30");
        assertThat(page.locator("#reason")).hasValue("Hibah ke yayasan");
        assertThat(disposalRepository.existsByFixedAssetId(asset.getId())).isFalse();

        page.locator("#counterparty").fill("Yayasan Penerima");
        page.locator("#btn-dispose").click();
        waitForPageLoad();

        assertThat(page.locator("#record-type")).hasText("Dihibahkan");
        var disposal = disposalRepository.findByAssetId(asset.getId()).getFirst();
        UUID draftId = disposal.getTransaction().getId();

        page.locator("#btn-cancel-disposal").click();
        waitForPageLoad();
        assertThat(page.getByTestId("flash-success")).hasText("Pelepasan dibatalkan dan draf jurnalnya dihapus.");
        assertThat(page.locator("#disposal-status")).hasText("Dibatalkan");
        assertThat(page.locator("#btn-cancel-disposal")).hasCount(0);
        assertThat(transactionRepository.findById(draftId)).isEmpty();

        navigateTo("/assets/" + asset.getId());
        waitForPageLoad();
        assertThat(page.locator("#btn-edit")).isVisible();
        assertThat(page.locator("#btn-dispose")).isVisible();
        assertThat(page.getByTestId("asset-disposal-row")).containsText(disposal.getRecordNumber());
    }

    @Test
    @DisplayName("A posted acquisition offers no delete and a direct delete is refused; a draft-only registration deletes with its draft")
    void deleteClosed() {
        FixedAsset posted = postedAsset("DEL-", "1000000", YearMonth.of(YEAR, 5));
        navigateTo("/assets/" + posted.getId());
        waitForPageLoad();
        assertThat(page.locator("#btn-delete")).hasCount(0);
        assertThat(page.locator("#btn-dispose")).isVisible();
        String csrf = page.locator("meta[name='_csrf']").getAttribute("content");
        APIResponse refused = page.request().post(baseUrl() + "/assets/" + posted.getId() + "/delete",
                RequestOptions.create().setForm(FormData.create().set("_csrf", csrf)).setMaxRedirects(0));
        assertThat(refused.status()).isEqualTo(302);
        navigateTo("/assets/" + posted.getId());
        waitForPageLoad();
        assertThat(page.getByTestId("flash-error")).containsText("Gunakan menu Lepas Aset");
        assertThat(assetRepository.findById(posted.getId())).isPresent();

        FixedAsset mistake = asset("DEL-", "1000000", YearMonth.of(YEAR, 5));
        UUID draftId = acquisitionId(mistake);
        navigateTo("/assets/" + mistake.getId());
        waitForPageLoad();
        page.onDialog(dialog -> dialog.accept());
        page.locator("#btn-delete").click();
        waitForPageLoad();
        assertThat(page.getByTestId("flash-success")).hasText("Aset berhasil dihapus");
        assertThat(assetRepository.findById(mistake.getId())).isEmpty();
        assertThat(transactionRepository.findById(draftId)).isEmpty();
    }

    @Test
    @DisplayName("AUDITOR reads the checklist and record with no write controls; photo is readable")
    void auditorIsReadOnly() {
        FixedAsset asset = postedAsset("AUD-", "1000000", YearMonth.of(YEAR, 6));
        var disposal = disposalService.create(new AssetDisposalService.CreateDisposal(asset.getId(),
                com.artivisi.accountingfinance.entity.DisposalType.WRITTEN_OFF, java.time.LocalDate.of(YEAR, 6, 30), null, null, null, "Rusak", "admin"));

        User auditor = new User();
        String id = UUID.randomUUID().toString().substring(0, 8);
        auditor.setUsername("reg-aud-" + id);
        auditor.setPassword(passwordEncoder.encode("password"));
        auditor.setFullName("Auditor " + id);
        auditor.setEmail("reg-" + id + "@example.com");
        auditor.setActive(true);
        auditor.addRole(Role.AUDITOR, "system");
        userRepository.save(auditor);
        login(auditor.getUsername(), "password");

        navigateTo("/assets/verification?year=" + YEAR);
        waitForPageLoad();
        Locator card = page.locator("[data-testid='checklist-card'][data-code='" + asset.getAssetCode() + "']");
        assertThat(card).isVisible();
        assertThat(card.getByTestId("btn-verify")).hasCount(0);

        navigateTo("/assets/disposals/" + disposal.getId());
        waitForPageLoad();
        assertThat(page.locator("#record-type")).hasText("Dihapuskan");
        assertThat(page.locator("#btn-cancel-disposal")).hasCount(0);

        navigateTo("/assets/" + asset.getId());
        waitForPageLoad();
        assertThat(page.locator("#btn-photo-upload")).hasCount(0);
    }

    // ==================== helpers ====================

    @Autowired private AssetDisposalService disposalService;

    private FixedAsset postedAsset(String prefix, String cost, YearMonth start) {
        FixedAsset asset = asset(prefix, cost, start);
        transactionService.post(acquisitionId(asset), "admin");
        return asset;
    }

    private UUID acquisitionId(FixedAsset asset) {
        return transactionRepository.findBySourceDocumentTypeAndSourceDocumentIdOrderByTransactionDateAsc(
                FixedAssetService.ACQUISITION_SOURCE_TYPE, asset.getId()).getFirst().getId();
    }

    private FixedAsset asset(String prefix, String cost, YearMonth start) {
        FixedAsset asset = new FixedAsset();
        asset.setAssetCode(prefix + UUID.randomUUID().toString().substring(0, 8));
        asset.setName("Aset " + asset.getAssetCode());
        asset.setCategory(categoryRepository.findByCode("KOMPUTER").orElseThrow());
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
