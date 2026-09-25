package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.AssetCategory;
import com.artivisi.accountingfinance.entity.AssetStatus;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.DepreciationEntry;
import com.artivisi.accountingfinance.entity.DepreciationEntryStatus;
import com.artivisi.accountingfinance.entity.DepreciationMethod;
import com.artivisi.accountingfinance.entity.FixedAsset;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.AssetCategoryRepository;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.DepreciationEntryRepository;
import com.artivisi.accountingfinance.repository.FixedAssetRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.service.AssetCategoryService;
import com.artivisi.accountingfinance.service.FixedAssetService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * FixedAssetController: list filters/search/HTMX/pagination, create with category defaults and validation,
 * edit before and after depreciation (incl. a rejected code change), depreciation generate/post/skip/post-all
 * with exact amounts and journals, delete, dispose form and the AUDITOR write guard.
 *
 * Every asset is created by the test itself with a unique code and 2051 dates. Generating a 2051 period also
 * creates entries for other test classes' active assets (generation is period-wide by design); assertions
 * only look at this class's own assets and at deltas.
 */
@DisplayName("Fixed Asset Controller Tests")
@Import(ServiceTestDataInitializer.class)
class FixedAssetControllerFunctionalTest extends PlaywrightTestBase {

    private static final int YEAR = 2051;
    private static final String BANK = "1.1.02";
    private static final String COMPUTER_ASSET = "1.2.01";
    private static final String COMPUTER_ACCUM = "1.2.02";
    private static final String VEHICLE_ACCUM = "1.2.04";
    private static final String OFFICE_ASSET = "1.2.05";
    private static final String OFFICE_ACCUM = "1.2.06";
    private static final String DEPRECIATION_EXPENSE = "5.1.12";

    @Autowired
    private FixedAssetRepository assetRepository;

    @Autowired
    private AssetCategoryRepository categoryRepository;

    @Autowired
    private AssetCategoryService categoryService;

    @Autowired
    private DepreciationEntryRepository entryRepository;

    @Autowired
    private FixedAssetService fixedAssetService;

    @Autowired
    private ChartOfAccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
    }

    // ==================== list ====================

    @Test
    @DisplayName("List search, status and category filters, HTMX refresh and pagination show exactly the matching assets")
    void shouldFilterSearchAndPaginateList() {
        String token = uid();
        FixedAsset computer = createAsset("LS" + token + "-A", "Daftar " + token + " Laptop", "KOMPUTER", "12000000", 48, month(1));
        FixedAsset vehicle = createAsset("LS" + token + "-B", "Daftar " + token + " Mobil", "KENDARAAN", "96000000", 96, month(1));
        FixedAsset depreciated = createAsset("LS" + token + "-C", "Daftar " + token + " Printer", "KOMPUTER", "3000000", 1, month(1));
        fixedAssetService.generateDepreciationEntries(month(1));
        fixedAssetService.postDepreciationEntry(entry(depreciated, 1).getId(), "admin");

        assertListCodes("?search=" + token, computer, vehicle, depreciated);
        Locator computerCells = assetRow(computer).locator("td");
        assertThat(computerCells.nth(1)).containsText(computer.getName());
        assertThat(computerCells.nth(2)).hasText("Peralatan Komputer");
        assertThat(computerCells.nth(3)).hasText("12,000,000");
        assertThat(computerCells.nth(4)).hasText("12,000,000");
        assertThat(computerCells.nth(5)).hasText("Aktif");
        assertThat(assetRow(vehicle).locator("td").nth(2)).hasText("Kendaraan");
        Locator depreciatedCells = assetRow(depreciated).locator("td");
        assertThat(depreciatedCells.nth(3)).hasText("3,000,000");
        assertThat(depreciatedCells.nth(4)).hasText("0");
        assertThat(depreciatedCells.nth(5)).hasText("Habis Penyusutan");

        UUID komputer = category("KOMPUTER").getId();
        UUID kendaraan = category("KENDARAAN").getId();
        assertListCodes("?search=" + token + "&status=FULLY_DEPRECIATED", depreciated);
        assertListCodes("?search=" + token + "&categoryId=" + kendaraan, vehicle);
        assertListCodes("?search=" + token + "&status=ACTIVE&categoryId=" + komputer, computer);
        assertListCodes("?search=" + token + "&status=DISPOSED");
        assertThat(page.locator("#empty-state")).hasText("Tidak ada aset ditemukan");

        navigateTo("/assets?search=" + token);
        waitForPageLoad();
        page.locator("#filter-category").selectOption(kendaraan.toString());
        assertThat(assetRows()).hasCount(1);
        assertThat(assetRow(vehicle)).isVisible();

        APIResponse fragment = page.request().get(baseUrl() + "/assets?search=" + token + "&status=ACTIVE",
                RequestOptions.create().setHeader("HX-Request", "true"));
        assertThat(fragment.status()).isEqualTo(200);
        assertThat(fragment.text()).contains("id=\"asset-table\"").contains(computer.getAssetCode())
                .contains(vehicle.getAssetCode()).doesNotContain(depreciated.getAssetCode())
                .doesNotContain("page-title").doesNotContain("<html");

        navigateTo("/assets?search=" + token + "&size=2");
        waitForPageLoad();
        assertThat(assetRows()).hasCount(2);
        assertThat(page.locator("#asset-table")).containsText("Menampilkan 1 - 2 dari 3 aset");
        page.getByText("Selanjutnya").click();
        waitForPageLoad();
        assertThat(assetRows()).hasCount(1);
        assertThat(assetRow(depreciated)).isVisible();
        assertThat(page.locator("#asset-table")).containsText("Menampilkan 3 - 3 dari 3 aset");
    }

    // ==================== create ====================

    @Test
    @DisplayName("New form defaults to straight line, zero residual, today's purchase date and first-of-month start")
    void shouldShowNewFormDefaults() {
        navigateTo("/assets/new");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Aset Baru");
        assertThat(page.locator("#depreciationMethod")).hasValue("STRAIGHT_LINE");
        assertThat(page.locator("#residualValue")).hasValue("0");
        assertThat(page.locator("#purchaseDate")).hasValue(LocalDate.now().toString());
        assertThat(page.locator("#depreciationStartDate")).hasValue(LocalDate.now().withDayOfMonth(1).toString());
        assertThat(page.locator("#autoPost")).not().isChecked();
        assertThat(page.locator("#category option[value='" + category("KOMPUTER").getId() + "']"))
                .hasText("Peralatan Komputer (4 tahun)");
        assertThat(page.locator("#category option[value='" + category("KENDARAAN").getId() + "']"))
                .hasText("Kendaraan (8 tahun)");
    }

    @Test
    @DisplayName("Creating an asset via the form persists it with the category's accounts and composes the acquisition DRAFT")
    void shouldCreateAssetWithAcquisitionDraft() {
        String code = "CR-" + uid();
        String name = "Laptop " + code;
        fillAssetForm(code, name, "KOMPUTER", LocalDate.of(YEAR, 1, 15), "24000000",
                LocalDate.of(YEAR, 1, 1), "STRAIGHT_LINE", "48", "0", "");
        page.locator("#supplier").fill("iBox");
        page.locator("#invoiceNumber").fill("INV-" + code);
        page.locator("#serialNumber").fill("SN-" + code);
        page.locator("#location").fill("Kantor Pusat");
        page.locator("#autoPost").check();
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        FixedAsset asset = asset(code);
        assertThat(page).hasURL(baseUrl() + "/assets/" + asset.getId());
        assertThat(page.getByTestId("flash-success")).hasText("Aset berhasil ditambahkan");

        assertThat(asset.getName()).isEqualTo(name);
        assertThat(asset.getCategory().getId()).isEqualTo(category("KOMPUTER").getId());
        assertThat(asset.getPurchaseDate()).isEqualTo(LocalDate.of(YEAR, 1, 15));
        assertThat(asset.getPurchaseCost()).isEqualByComparingTo("24000000");
        assertThat(asset.getDepreciationStartDate()).isEqualTo(LocalDate.of(YEAR, 1, 1));
        assertThat(asset.getDepreciationMethod()).isEqualTo(DepreciationMethod.STRAIGHT_LINE);
        assertThat(asset.getUsefulLifeMonths()).isEqualTo(48);
        assertThat(asset.getResidualValue()).isEqualByComparingTo("0");
        assertThat(asset.getBookValue()).isEqualByComparingTo("24000000");
        assertThat(asset.getAccumulatedDepreciation()).isEqualByComparingTo("0");
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.ACTIVE);
        assertThat(asset.isAutoPost()).isTrue();
        assertThat(asset.getSupplier()).isEqualTo("iBox");
        assertThat(asset.getSerialNumber()).isEqualTo("SN-" + code);
        assertThat(asset.getAssetAccount().getId()).isEqualTo(account(COMPUTER_ASSET).getId());
        assertThat(asset.getAccumulatedDepreciationAccount().getId()).isEqualTo(account(COMPUTER_ACCUM).getId());
        assertThat(asset.getDepreciationExpenseAccount().getId()).isEqualTo(account(DEPRECIATION_EXPENSE).getId());
        assertThat(asset.getFundingAccount().getId()).isEqualTo(account(BANK).getId());

        List<Transaction> drafts = transactionRepository
                .findBySourceDocumentTypeAndSourceDocumentIdOrderByTransactionDateAsc("ASSET", asset.getId());
        assertThat(drafts).hasSize(1);
        Transaction draft = drafts.get(0);
        assertThat(draft.getStatus()).isEqualTo(TransactionStatus.DRAFT);
        assertThat(draft.getTransactionDate()).isEqualTo(LocalDate.of(YEAR, 1, 15));
        assertThat(draft.getAmount()).isEqualByComparingTo("24000000");
        assertThat(draft.getDescription()).isEqualTo("Pembelian " + code + " - " + name);

        assertThat(page.locator("#asset-code")).hasText(code);
        assertThat(page.locator("#asset-name")).hasText(name);
        assertThat(page.locator("#asset-status")).hasText("Aktif");
        Locator section = page.locator("section");
        assertThat(section).containsText("Rp 24,000,000");
        assertThat(page.locator("#monthly-depreciation")).hasText("Rp 500,000");
        assertThat(section).containsText("48 bulan");
        assertThat(section).containsText("1.2.01 - Peralatan Komputer");
        assertThat(section).containsText("1.2.02 - Akum. Penyusutan Peralatan Komputer");
        assertThat(section).containsText("5.1.12 - Beban Penyusutan");
        assertThat(section).containsText("Belum ada riwayat penyusutan");
    }

    @Test
    @DisplayName("Declining balance asset left without a rate takes the category's rate and accounts; monthly amounts are exact")
    void shouldApplyCategoryDefaultsToDecliningBalanceAsset() {
        AssetCategory category = createCategory("DB" + uid(), DepreciationMethod.DECLINING_BALANCE, 60, new BigDecimal("50"));
        String code = "DB-" + uid();
        fillAssetForm(code, "Mesin " + code, category.getCode(), LocalDate.of(YEAR, 2, 10), "12000000",
                LocalDate.of(YEAR, 2, 1), "DECLINING_BALANCE", "24", "0", "");
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        FixedAsset asset = asset(code);
        assertThat(page).hasURL(baseUrl() + "/assets/" + asset.getId());
        assertThat(asset.getDepreciationMethod()).isEqualTo(DepreciationMethod.DECLINING_BALANCE);
        assertThat(asset.getDepreciationRate()).isEqualByComparingTo("50");
        assertThat(asset.getUsefulLifeMonths()).isEqualTo(24);
        assertThat(asset.getAssetAccount().getId()).isEqualTo(account(OFFICE_ASSET).getId());
        assertThat(asset.getAccumulatedDepreciationAccount().getId()).isEqualTo(account(OFFICE_ACCUM).getId());
        assertThat(asset.getDepreciationExpenseAccount().getId()).isEqualTo(account(DEPRECIATION_EXPENSE).getId());
        assertThat(page.locator("section")).containsText("Saldo Menurun");
        assertThat(page.locator("section")).containsText("50.00%");
        assertThat(page.locator("#monthly-depreciation")).hasText("Rp 500,000");

        fixedAssetService.generateDepreciationEntries(month(2));
        DepreciationEntry february = entry(asset, 1);
        assertThat(february.getDepreciationAmount()).isEqualByComparingTo("500000.00");
        fixedAssetService.postDepreciationEntry(february.getId(), "admin");
        assertThat(posting(february).lines()).containsExactlyInAnyOrder(
                "5.1.12 D500000.00 C0.00", "1.2.06 D0.00 C500000.00");

        fixedAssetService.generateDepreciationEntries(month(3));
        DepreciationEntry march = entry(asset, 2);
        assertThat(march.getDepreciationAmount()).isEqualByComparingTo("479166.67");
        assertThat(march.getAccumulatedDepreciation()).isEqualByComparingTo("979166.67");
        assertThat(march.getBookValue()).isEqualByComparingTo("11020833.33");
    }

    @Test
    @DisplayName("Create without a funding account re-renders the form with the service error and persists nothing")
    void shouldRejectCreateWithoutFundingAccount() {
        String code = "NF-" + uid();
        navigateTo("/assets/new");
        waitForPageLoad();
        page.locator("#assetCode").fill(code);
        page.locator("#name").fill("Tanpa Dana " + code);
        page.locator("#category").selectOption(category("KOMPUTER").getId().toString());
        page.locator("#purchaseDate").fill(LocalDate.of(YEAR, 1, 10).toString());
        page.locator("#purchaseCost").fill("5000000");
        page.locator("#depreciationStartDate").fill(LocalDate.of(YEAR, 1, 1).toString());
        page.locator("#usefulLifeMonths").fill("24");
        page.locator("form:has(#btn-simpan)").evaluate("f => f.noValidate = true");
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Aset Baru");
        assertThat(errorItems()).hasText(new String[]{
                "Akun pendanaan aset belum dipilih (bank/hutang yang dipakai untuk pembelian)"});
        assertThat(page.locator("#assetCode")).hasValue(code);
        assertThat(assetRepository.findByAssetCode(code)).isEmpty();
    }

    @Test
    @DisplayName("Duplicate code, residual not below cost and declining balance without any rate are rejected; a corrected resubmit succeeds")
    void shouldRejectInvalidAssetsAndAcceptCorrectedResubmit() {
        FixedAsset existing = createAsset("EX-" + uid(), "Sudah Ada", "KOMPUTER", "5000000", 24, month(1));
        String code = "IV-" + uid();

        fillAssetForm(existing.getAssetCode(), "Duplikat " + code, "KOMPUTER", LocalDate.of(YEAR, 1, 10), "5000000",
                LocalDate.of(YEAR, 1, 1), "STRAIGHT_LINE", "24", "0", "");
        page.locator("#btn-simpan").click();
        waitForPageLoad();
        assertThat(errorItems()).hasText(new String[]{"Kode aset " + existing.getAssetCode() + " sudah digunakan"});
        assertThat(page.locator("#fundingAccount")).hasValue("1.1.02 - Bank BCA");
        assertThat(page.locator("#purchaseDate")).hasValue(LocalDate.of(YEAR, 1, 10).toString());
        assertThat(asset(existing.getAssetCode()).getName()).isEqualTo("Sudah Ada");

        page.locator("#assetCode").fill(code);
        page.locator("#residualValue").fill("5000000");
        page.locator("#btn-simpan").click();
        waitForPageLoad();
        assertThat(errorItems()).hasText(new String[]{"Nilai residu harus lebih kecil dari nilai perolehan"});
        assertThat(assetRepository.findByAssetCode(code)).isEmpty();

        page.locator("#residualValue").fill("0");
        page.locator("#depreciationMethod").selectOption("DECLINING_BALANCE");
        page.locator("#btn-simpan").click();
        waitForPageLoad();
        assertThat(errorItems()).hasText(new String[]{"Tarif penyusutan wajib diisi untuk metode saldo menurun"});
        assertThat(assetRepository.findByAssetCode(code)).isEmpty();

        page.locator("#depreciationMethod").selectOption("STRAIGHT_LINE");
        page.locator("#btn-simpan").click();
        waitForPageLoad();
        FixedAsset created = asset(code);
        assertThat(page).hasURL(baseUrl() + "/assets/" + created.getId());
        assertThat(page.getByTestId("flash-success")).hasText("Aset berhasil ditambahkan");
        assertThat(created.getName()).isEqualTo("Duplikat " + code);
        assertThat(created.getFundingAccount().getId()).isEqualTo(account(BANK).getId());
    }

    // ==================== edit ====================

    @Test
    @DisplayName("Edit form is prefilled (dates, funding account) and saving before any depreciation updates the financial fields")
    void shouldEditAssetBeforeDepreciation() {
        FixedAsset asset = createAsset("ED-" + uid(), "Edit Awal", "KOMPUTER", "24000000", 48, month(1));

        navigateTo("/assets/" + asset.getId() + "/edit");
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Edit Aset");
        assertThat(page.locator("#assetCode")).hasValue(asset.getAssetCode());
        assertThat(page.locator("#category")).hasValue(category("KOMPUTER").getId().toString());
        assertThat(page.locator("#purchaseDate")).hasValue(LocalDate.of(YEAR, 1, 10).toString());
        assertThat(page.locator("#depreciationStartDate")).hasValue(LocalDate.of(YEAR, 1, 1).toString());
        assertThat(page.locator("#fundingAccount")).hasValue("1.1.02 - Bank BCA");
        assertThat(page.locator("#purchaseCost")).hasValue(Pattern.compile("24000000(\\.00)?"));
        assertThat(page.locator("#usefulLifeMonths")).hasValue("48");

        page.locator("#name").fill("Edit Akhir");
        page.locator("#purchaseCost").fill("36000000");
        page.locator("#usefulLifeMonths").fill("36");
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/assets/" + asset.getId());
        assertThat(page.getByTestId("flash-success")).hasText("Aset berhasil diperbarui");
        FixedAsset saved = asset(asset.getAssetCode());
        assertThat(saved.getName()).isEqualTo("Edit Akhir");
        assertThat(saved.getPurchaseCost()).isEqualByComparingTo("36000000");
        assertThat(saved.getBookValue()).isEqualByComparingTo("36000000");
        assertThat(saved.getUsefulLifeMonths()).isEqualTo(36);
        assertThat(saved.getPurchaseDate()).isEqualTo(LocalDate.of(YEAR, 1, 10));
        assertThat(saved.getDepreciationStartDate()).isEqualTo(LocalDate.of(YEAR, 1, 1));
        assertThat(page.locator("#monthly-depreciation")).hasText("Rp 1,000,000");
    }

    @Test
    @DisplayName("Changing the code to another asset's code is rejected; the form still targets the original and a corrected resubmit updates only it")
    void shouldRejectCodeOfAnotherAssetOnEdit() {
        String token = uid();
        FixedAsset original = createAsset("OR-" + token, "Asli " + token, "KOMPUTER", "6000000", 24, month(1));
        FixedAsset other = createAsset("OT-" + token, "Lain " + token, "KOMPUTER", "8000000", 24, month(1));

        navigateTo("/assets/" + original.getId() + "/edit");
        waitForPageLoad();
        page.locator("#assetCode").fill(other.getAssetCode());
        page.locator("#name").fill("Asli Diubah " + token);
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        assertThat(errorItems()).hasText(new String[]{"Kode aset " + other.getAssetCode() + " sudah digunakan"});
        assertThat(page.locator("form:has(#btn-simpan)")).hasAttribute("action", "/assets/" + original.getId());
        assertThat(page.locator("#assetCode")).hasValue(other.getAssetCode());
        assertThat(page.locator("#fundingAccount")).hasValue("1.1.02 - Bank BCA");
        assertThat(page.locator("#purchaseDate")).hasValue(LocalDate.of(YEAR, 1, 10).toString());
        assertThat(asset(original.getAssetCode()).getName()).isEqualTo("Asli " + token);

        String newCode = "OX-" + token;
        page.locator("#assetCode").fill(newCode);
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/assets/" + original.getId());
        assertThat(page.getByTestId("flash-success")).hasText("Aset berhasil diperbarui");
        FixedAsset updated = assetRepository.findById(original.getId()).orElseThrow();
        assertThat(updated.getAssetCode()).isEqualTo(newCode);
        assertThat(updated.getName()).isEqualTo("Asli Diubah " + token);
        FixedAsset untouched = assetRepository.findById(other.getId()).orElseThrow();
        assertThat(untouched.getAssetCode()).isEqualTo(other.getAssetCode());
        assertThat(untouched.getName()).isEqualTo("Lain " + token);
        assertThat(untouched.getPurchaseCost()).isEqualByComparingTo("8000000");
    }

    @Test
    @DisplayName("After a posted depreciation, editing changes only the descriptive fields")
    void shouldLockFinancialFieldsAfterDepreciation() {
        FixedAsset asset = createAsset("LK-" + uid(), "Terkunci", "KOMPUTER", "12000000", 24, month(1));
        fixedAssetService.generateDepreciationEntries(month(1));
        fixedAssetService.postDepreciationEntry(entry(asset, 1).getId(), "admin");

        navigateTo("/assets/" + asset.getId() + "/edit");
        waitForPageLoad();
        page.locator("#name").fill("Terkunci Diubah");
        page.locator("#location").fill("Gudang");
        page.locator("#purchaseCost").fill("99000000");
        page.locator("#usefulLifeMonths").fill("12");
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        assertThat(page.getByTestId("flash-success")).hasText("Aset berhasil diperbarui");
        FixedAsset saved = asset(asset.getAssetCode());
        assertThat(saved.getName()).isEqualTo("Terkunci Diubah");
        assertThat(saved.getLocation()).isEqualTo("Gudang");
        assertThat(saved.getPurchaseCost()).isEqualByComparingTo("12000000");
        assertThat(saved.getUsefulLifeMonths()).isEqualTo(24);
        assertThat(saved.getAccumulatedDepreciation()).isEqualByComparingTo("500000");
        assertThat(saved.getBookValue()).isEqualByComparingTo("11500000");
    }

    // ==================== depreciation ====================

    @Test
    @DisplayName("Straight line: generate and post each month via the UI; the last period absorbs the rounding remainder")
    void shouldDepreciateStraightLineWithRemainderOnLastPeriod() {
        FixedAsset asset = createAsset("SL-" + uid(), "Garis Lurus " + uid(), "KOMPUTER", "10000000", 3, month(1));
        String[][] expected = {
                {"3333333.33", "3333333.33", "6666666.67", "Januari"},
                {"3333333.33", "6666666.66", "3333333.34", "Februari"},
                {"3333333.34", "10000000.00", "0.00", "Maret"}};

        for (int m = 1; m <= 3; m++) {
            generateViaUi(month(m));
            DepreciationEntry entry = entry(asset, m);
            assertThat(entry.getStatus()).isEqualTo(DepreciationEntryStatus.PENDING);
            assertThat(entry.getPeriodStart()).isEqualTo(month(m).atDay(1));
            assertThat(entry.getPeriodEnd()).isEqualTo(month(m).atEndOfMonth());
            assertThat(entry.getDepreciationAmount()).isEqualByComparingTo(expected[m - 1][0]);
            assertThat(entry.getAccumulatedDepreciation()).isEqualByComparingTo(expected[m - 1][1]);
            assertThat(entry.getBookValue()).isEqualByComparingTo(expected[m - 1][2]);

            Locator row = entryRow(entry);
            assertThat(row.locator("td").nth(0)).hasText(asset.getAssetCode());
            assertThat(row.locator("td").nth(2)).hasText(expected[m - 1][3] + " " + YEAR);
            assertThat(row.locator("td").nth(3)).hasText("3,333,333");
            row.locator("button:has-text('Posting')").click();
            waitForPageLoad();
            assertThat(page.getByTestId("flash-success")).hasText("Penyusutan untuk " + asset.getName() + " berhasil di-posting");

            DepreciationEntry posted = entryRepository.findById(entry.getId()).orElseThrow();
            assertThat(posted.getStatus()).isEqualTo(DepreciationEntryStatus.POSTED);
            Posting journal = posting(posted);
            assertThat(journal.date()).isEqualTo(month(m).atEndOfMonth());
            assertThat(journal.reference()).isEqualTo("DEP-" + asset.getAssetCode() + "-" + m);
            assertThat(journal.description()).isEqualTo("Penyusutan " + asset.getName() + " - " + expected[m - 1][3] + " " + YEAR);
            assertThat(journal.status()).isEqualTo(TransactionStatus.POSTED);
            assertThat(journal.lines()).containsExactlyInAnyOrder(
                    "5.1.12 D" + expected[m - 1][0] + " C0.00", "1.2.02 D0.00 C" + expected[m - 1][0]);
        }

        FixedAsset done = asset(asset.getAssetCode());
        assertThat(done.getStatus()).isEqualTo(AssetStatus.FULLY_DEPRECIATED);
        assertThat(done.getAccumulatedDepreciation()).isEqualByComparingTo("10000000");
        assertThat(done.getBookValue()).isEqualByComparingTo("0");
        assertThat(done.getDepreciationPeriodsCompleted()).isEqualTo(3);
        assertThat(done.getLastDepreciationDate()).isEqualTo(month(3).atEndOfMonth());

        fixedAssetService.generateDepreciationEntries(month(4));
        assertThat(entryRepository.findByAssetIdAndPeriodEnd(asset.getId(), month(4).atEndOfMonth())).isEmpty();

        navigateTo("/assets/" + asset.getId());
        waitForPageLoad();
        assertThat(page.locator("#asset-status")).hasText("Habis Penyusutan");
        Locator history = page.locator("tr[data-period]");
        assertThat(history).hasCount(3);
        assertThat(history.nth(2).locator("td")).hasText(new String[]{"3", "Maret " + YEAR, "3,333,333", "10,000,000", "0", "Posted"});
        assertThat(page.locator("#monthly-depreciation")).hasText("Rp 0");
        assertThat(page.locator("#btn-delete")).hasCount(0);
    }

    @Test
    @DisplayName("Skip leaves no journal, post-all posts every pending entry up to the period, and state errors are reported")
    void shouldSkipAndPostAll() {
        FixedAsset asset = createAsset("SK-" + uid(), "Skip " + uid(), "KENDARAAN", "6000000", 6, month(1));

        generateViaUi(month(1));
        DepreciationEntry january = entry(asset, 1);
        entryRow(january).locator("button:has-text('Skip')").click();
        waitForPageLoad();
        assertThat(page.getByTestId("flash-success")).hasText("Entri penyusutan di-skip");
        DepreciationEntry skipped = entryRepository.findById(january.getId()).orElseThrow();
        assertThat(skipped.getStatus()).isEqualTo(DepreciationEntryStatus.SKIPPED);
        assertThat(skipped.getTransaction()).isNull();
        assertThat(asset(asset.getAssetCode()).getAccumulatedDepreciation()).isEqualByComparingTo("0");
        assertThat(entryRow(january)).hasCount(0);

        APIResponse skipAgain = postForm("/assets/depreciation/" + january.getId() + "/skip");
        assertThat(skipAgain.status()).isEqualTo(302);
        assertThat(skipAgain.headers().get("location")).isEqualTo(baseUrl() + "/assets/depreciation");
        navigateTo("/assets/depreciation");
        waitForPageLoad();
        assertThat(page.getByTestId("flash-error")).hasText("Hanya entri dengan status PENDING yang dapat di-skip");

        generateViaUi(month(2));
        DepreciationEntry february = entry(asset, 2);
        assertThat(february.getDepreciationAmount()).isEqualByComparingTo("1000000");
        long pendingUpToFebruary = entryRepository.findAllPending().stream()
                .filter(e -> !e.getPeriodEnd().isAfter(month(2).atEndOfMonth())).count();
        page.locator("#post-all-period").fill(month(2).toString());
        page.locator("form[action='/assets/depreciation/post-all'] button").click();
        waitForPageLoad();

        assertThat(page.getByTestId("flash-success")).hasText("Berhasil posting " + pendingUpToFebruary + " entri penyusutan");
        assertThat(entryRepository.findAllPending()).noneMatch(e -> !e.getPeriodEnd().isAfter(month(2).atEndOfMonth()));
        DepreciationEntry postedFebruary = entryRepository.findById(february.getId()).orElseThrow();
        assertThat(postedFebruary.getStatus()).isEqualTo(DepreciationEntryStatus.POSTED);
        assertThat(posting(postedFebruary).lines()).containsExactlyInAnyOrder(
                "5.1.12 D1000000.00 C0.00", VEHICLE_ACCUM + " D0.00 C1000000.00");
        assertThat(entryRepository.findById(january.getId()).orElseThrow().getStatus()).isEqualTo(DepreciationEntryStatus.SKIPPED);
        FixedAsset afterPostAll = asset(asset.getAssetCode());
        assertThat(afterPostAll.getAccumulatedDepreciation()).isEqualByComparingTo("1000000");
        assertThat(afterPostAll.getDepreciationPeriodsCompleted()).isEqualTo(1);

        APIResponse postAgain = postForm("/assets/depreciation/" + february.getId() + "/post");
        assertThat(postAgain.status()).isEqualTo(302);
        navigateTo("/assets/depreciation");
        waitForPageLoad();
        assertThat(page.getByTestId("flash-error")).hasText("Hanya entri dengan status PENDING yang dapat di-posting");

        APIResponse postAllEmpty = page.request().post(baseUrl() + "/assets/depreciation/post-all",
                RequestOptions.create().setForm(FormData.create().set("_csrf", csrf()).set("period", "2000-01"))
                        .setMaxRedirects(0));
        assertThat(postAllEmpty.status()).isEqualTo(302);
        navigateTo("/assets/depreciation");
        waitForPageLoad();
        assertThat(page.getByTestId("flash-info")).hasText("Tidak ada entri penyusutan yang perlu di-posting");

        page.locator("#period").fill("2000-01");
        page.locator("form[action='/assets/depreciation/generate'] button").click();
        waitForPageLoad();
        assertThat(page.getByTestId("flash-info")).hasText("Tidak ada aset yang perlu disusutkan untuk periode 2000-01");
    }

    // ==================== delete ====================

    @Test
    @DisplayName("An asset without depreciation history is deleted from its detail page together with its pending entries")
    void shouldDeleteAssetWithoutHistory() {
        FixedAsset asset = createAsset("DL-" + uid(), "Hapus " + uid(), "KOMPUTER", "4800000", 48, month(1));
        fixedAssetService.generateDepreciationEntries(month(1));
        DepreciationEntry pending = entry(asset, 1);

        navigateTo("/assets/" + asset.getId());
        waitForPageLoad();
        page.onceDialog(dialog -> dialog.accept());
        page.locator("#btn-delete").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/assets");
        assertThat(page.getByTestId("flash-success")).hasText("Aset berhasil dihapus");
        assertThat(assetRepository.findById(asset.getId())).isEmpty();
        assertThat(entryRepository.findById(pending.getId())).isEmpty();
    }

    @Test
    @DisplayName("An asset with posted depreciation offers no delete and a direct delete is refused")
    void shouldRefuseDeleteAfterDepreciation() {
        FixedAsset asset = createAsset("ND-" + uid(), "Tidak Dihapus " + uid(), "KOMPUTER", "4800000", 48, month(1));
        fixedAssetService.generateDepreciationEntries(month(1));
        fixedAssetService.postDepreciationEntry(entry(asset, 1).getId(), "admin");

        navigateTo("/assets/" + asset.getId());
        waitForPageLoad();
        assertThat(page.locator("#btn-delete")).hasCount(0);

        APIResponse response = postForm("/assets/" + asset.getId() + "/delete");
        assertThat(response.status()).isEqualTo(302);
        assertThat(response.headers().get("location")).isEqualTo(baseUrl() + "/assets/" + asset.getId());
        navigateTo("/assets/" + asset.getId());
        waitForPageLoad();
        assertThat(page.getByTestId("flash-error")).hasText("Aset dengan riwayat penyusutan tidak dapat dihapus");
        assertThat(assetRepository.findById(asset.getId())).isPresent();
    }

    // ==================== dispose ====================

    @Test
    @DisplayName("Dispose form shows the asset's values and previews gain/loss from the entered proceeds")
    void shouldShowDisposeFormWithGainLossPreview() {
        FixedAsset asset = createAsset("DF-" + uid(), "Dilepas " + uid(), "KOMPUTER", "12000000", 24, month(1));
        fixedAssetService.generateDepreciationEntries(month(1));
        fixedAssetService.postDepreciationEntry(entry(asset, 1).getId(), "admin");

        navigateTo("/assets/" + asset.getId());
        waitForPageLoad();
        page.locator("#btn-dispose").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/assets/" + asset.getId() + "/dispose");
        assertThat(page.locator("#page-title")).hasText("Lepas Aset");
        Locator section = page.locator("section");
        assertThat(section).containsText(asset.getAssetCode());
        assertThat(section).containsText("Rp 12,000,000");
        assertThat(section).containsText("Rp 500,000");
        assertThat(section).containsText("Rp 11,500,000");
        assertThat(page.locator("#disposalType option")).hasText(new String[]{
                "-- Pilih Tipe --", "Dijual", "Dihapuskan (Write-off)", "Dipindahkan"});
        assertThat(page.locator("#disposalDate")).hasValue(LocalDate.now().toString());

        page.locator("#proceeds").fill("13000000");
        assertThat(page.locator("#gainLossPreview")).hasText("Rp 1.500.000");
        assertThat(page.locator("#gainLossPreview")).hasClass(Pattern.compile(".*text-green-600.*"));
        page.locator("#proceeds").fill("10000000");
        assertThat(page.locator("#gainLossPreview")).hasText("Rp -1.500.000");
        assertThat(page.locator("#gainLossPreview")).hasClass(Pattern.compile(".*text-red-600.*"));
    }

    // ==================== authorization ====================

    @Test
    @DisplayName("AUDITOR sees assets without write controls and every write endpoint answers 403 without changes")
    void shouldRefuseWritesForAuditor() {
        FixedAsset asset = createAsset("AU-" + uid(), "Auditor " + uid(), "KOMPUTER", "4800000", 48, month(1));
        fixedAssetService.generateDepreciationEntries(month(1));
        DepreciationEntry pending = entry(asset, 1);
        long assetCount = assetRepository.count();

        User auditor = createUser(Role.AUDITOR);
        login(auditor.getUsername(), "password");
        navigateTo("/assets/" + asset.getId());
        waitForPageLoad();
        assertThat(page.locator("#asset-code")).hasText(asset.getAssetCode());
        assertThat(page.locator("#btn-edit, #btn-dispose, #btn-delete")).hasCount(0);
        navigateTo("/assets?search=" + asset.getAssetCode());
        waitForPageLoad();
        assertThat(assetRow(asset)).isVisible();
        assertThat(page.locator("#btn-new-asset")).hasCount(0);

        String base = "/assets/" + asset.getId();
        for (String path : List.of("/assets/new", base + "/edit", base + "/dispose", "/assets/depreciation")) {
            assertThat(page.request().get(baseUrl() + path).status()).as("GET " + path).isEqualTo(403);
        }
        FormData form = FormData.create().set("_csrf", csrf())
                .set("assetCode", "HACK-" + uid()).set("name", "Hack").set("category", category("KOMPUTER").getId().toString())
                .set("purchaseDate", "2051-01-10").set("purchaseCost", "1").set("depreciationMethod", "STRAIGHT_LINE")
                .set("usefulLifeMonths", "12").set("residualValue", "0").set("depreciationStartDate", "2051-01-01")
                .set("fundingAccount", account(BANK).getId().toString())
                .set("disposalType", "SOLD").set("disposalDate", "2051-02-01").set("period", "2051-01");
        for (String path : List.of("/assets/new", base, base + "/delete", base + "/dispose",
                "/assets/depreciation/generate", "/assets/depreciation/" + pending.getId() + "/post",
                "/assets/depreciation/" + pending.getId() + "/skip", "/assets/depreciation/post-all")) {
            APIResponse response = page.request().post(baseUrl() + path,
                    RequestOptions.create().setForm(form).setMaxRedirects(0));
            assertThat(response.status()).as("POST " + path).isEqualTo(403);
        }

        FixedAsset after = asset(asset.getAssetCode());
        assertThat(after.getName()).isEqualTo(asset.getName());
        assertThat(after.getStatus()).isEqualTo(AssetStatus.ACTIVE);
        assertThat(after.getPurchaseCost()).isEqualByComparingTo("4800000");
        assertThat(entryRepository.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo(DepreciationEntryStatus.PENDING);
        assertThat(assetRepository.count()).isEqualTo(assetCount);
    }

    // ==================== helpers ====================

    private record Posting(LocalDate date, String reference, String description, TransactionStatus status,
                           List<String> lines) {
    }

    private Posting posting(DepreciationEntry entry) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Transaction tx = entryRepository.findById(entry.getId()).orElseThrow().getTransaction();
            assertThat(tx).as("journal of period " + entry.getPeriodNumber()).isNotNull();
            List<String> lines = journalEntryRepository.findByTransactionIdWithAccount(tx.getId()).stream()
                    .map(l -> l.getAccount().getAccountCode()
                            + " D" + l.getDebitAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString()
                            + " C" + l.getCreditAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString())
                    .toList();
            return new Posting(tx.getTransactionDate(), tx.getReferenceNumber(), tx.getDescription(), tx.getStatus(), lines);
        });
    }

    private void generateViaUi(YearMonth period) {
        LocalDate periodEnd = period.atEndOfMonth();
        int before = entryRepository.findByPeriodEndAndOptionalStatus(periodEnd, null).size();
        navigateTo("/assets/depreciation");
        waitForPageLoad();
        page.locator("#period").fill(period.toString());
        page.locator("form[action='/assets/depreciation/generate'] button").click();
        waitForPageLoad();
        int generated = entryRepository.findByPeriodEndAndOptionalStatus(periodEnd, null).size() - before;
        assertThat(generated).isPositive();
        assertThat(page.getByTestId("flash-success"))
                .hasText("Berhasil generate " + generated + " entri penyusutan untuk periode " + period);
    }

    private void fillAssetForm(String code, String name, String categoryCode, LocalDate purchaseDate, String cost,
                               LocalDate startDate, String method, String months, String residual, String rate) {
        navigateTo("/assets/new");
        waitForPageLoad();
        page.locator("#assetCode").fill(code);
        page.locator("#name").fill(name);
        page.locator("#category").selectOption(category(categoryCode).getId().toString());
        page.locator("#fundingAccount").fill("");
        page.locator("#fundingAccount").pressSequentially(BANK);
        page.locator("[data-testid='account-picker-result']:has-text('Bank BCA')").click();
        assertThat(page.locator("#fundingAccount")).hasValue("1.1.02 - Bank BCA");
        page.locator("#purchaseDate").fill(purchaseDate.toString());
        page.locator("#purchaseCost").fill(cost);
        page.locator("#depreciationStartDate").fill(startDate.toString());
        page.locator("#depreciationMethod").selectOption(method);
        page.locator("#usefulLifeMonths").fill(months);
        page.locator("#residualValue").fill(residual);
        page.locator("#depreciationRate").fill(rate);
    }

    private FixedAsset createAsset(String code, String name, String categoryCode, String cost, int months,
                                   YearMonth start) {
        FixedAsset asset = new FixedAsset();
        asset.setAssetCode(code);
        asset.setName(name);
        asset.setCategory(category(categoryCode));
        asset.setPurchaseDate(start.atDay(10));
        asset.setPurchaseCost(new BigDecimal(cost));
        asset.setResidualValue(BigDecimal.ZERO);
        asset.setUsefulLifeMonths(months);
        asset.setDepreciationMethod(DepreciationMethod.STRAIGHT_LINE);
        asset.setDepreciationStartDate(start.atDay(1));
        asset.setFundingAccount(account(BANK));
        return fixedAssetService.create(asset);
    }

    private AssetCategory createCategory(String code, DepreciationMethod method, int months, BigDecimal rate) {
        AssetCategory category = new AssetCategory();
        category.setCode(code);
        category.setName("Kategori " + code);
        category.setDepreciationMethod(method);
        category.setUsefulLifeMonths(months);
        category.setDepreciationRate(rate);
        category.setAssetAccount(account(OFFICE_ASSET));
        category.setAccumulatedDepreciationAccount(account(OFFICE_ACCUM));
        category.setDepreciationExpenseAccount(account(DEPRECIATION_EXPENSE));
        return categoryService.create(category);
    }

    private User createUser(Role role) {
        String id = uid().toLowerCase();
        User user = new User();
        user.setUsername("fa-" + role.name().toLowerCase() + "-" + id);
        user.setPassword(passwordEncoder.encode("password"));
        user.setFullName("Pengguna " + id);
        user.setEmail("fa-" + id + "@example.com");
        user.setActive(true);
        user.addRole(role, "system");
        return userRepository.save(user);
    }

    private String csrf() {
        return page.locator("meta[name='_csrf']").getAttribute("content");
    }

    private APIResponse postForm(String path) {
        return page.request().post(baseUrl() + path,
                RequestOptions.create().setForm(FormData.create().set("_csrf", csrf())).setMaxRedirects(0));
    }

    private void assertListCodes(String query, FixedAsset... expected) {
        navigateTo("/assets" + query);
        waitForPageLoad();
        assertThat(assetRows()).hasCount(expected.length);
        for (int i = 0; i < expected.length; i++) {
            assertThat(assetRows().nth(i)).hasAttribute("data-code", expected[i].getAssetCode());
        }
    }

    private Locator assetRows() {
        return page.locator("#asset-table tbody tr[data-code]");
    }

    private Locator assetRow(FixedAsset asset) {
        return page.locator("#asset-table tbody tr[data-code='" + asset.getAssetCode() + "']");
    }

    private Locator entryRow(DepreciationEntry entry) {
        return page.locator("tr[data-id='" + entry.getId() + "']");
    }

    private Locator errorItems() {
        return page.locator("form:has(#btn-simpan) ul li");
    }

    private DepreciationEntry entry(FixedAsset asset, int periodNumber) {
        return entryRepository.findByFixedAssetIdOrderByPeriodNumberAsc(asset.getId()).stream()
                .filter(e -> e.getPeriodNumber() == periodNumber)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Depreciation entry " + periodNumber + " missing for " + asset.getAssetCode()));
    }

    private FixedAsset asset(String code) {
        return assetRepository.findByAssetCode(code)
                .orElseThrow(() -> new IllegalStateException("Fixed asset missing: " + code));
    }

    private AssetCategory category(String code) {
        return categoryRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Asset category missing: " + code));
    }

    private ChartOfAccount account(String code) {
        return accountRepository.findByAccountCode(code)
                .orElseThrow(() -> new IllegalStateException("Seeded account missing: " + code));
    }

    private static YearMonth month(int month) {
        return YearMonth.of(YEAR, month);
    }

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
