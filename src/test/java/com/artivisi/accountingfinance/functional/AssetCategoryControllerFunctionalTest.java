package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.AssetCategory;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.DepreciationMethod;
import com.artivisi.accountingfinance.entity.FixedAsset;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.AssetCategoryRepository;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * AssetCategoryController: list (seeded defaults, search, active filter, HTMX, pagination), create with
 * server-side validation, edit including a rejected code change, activate/deactivate, delete, and the
 * in-use guards. Every category a test changes is created by that test with a unique code.
 */
@DisplayName("Asset Category Controller Tests")
@Import(ServiceTestDataInitializer.class)
class AssetCategoryControllerFunctionalTest extends PlaywrightTestBase {

    private static final String COMPUTER_ASSET = "1.2.01";
    private static final String COMPUTER_ACCUM = "1.2.02";
    private static final String OFFICE_ASSET = "1.2.05";
    private static final String OFFICE_ACCUM = "1.2.06";
    private static final String DEPRECIATION_EXPENSE = "5.1.12";
    private static final String MAINTENANCE_EXPENSE = "5.1.11";

    @Autowired
    private AssetCategoryRepository categoryRepository;

    @Autowired
    private AssetCategoryService categoryService;

    @Autowired
    private FixedAssetService fixedAssetService;

    @Autowired
    private ChartOfAccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
    }

    // ==================== list ====================

    @Test
    @DisplayName("List shows the seeded categories with their depreciation defaults")
    void shouldListSeededCategoriesWithDefaults() {
        navigateTo("/assets/categories?search=KENDARAAN");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Kategori Aset");
        assertThat(rows()).hasCount(1);
        Locator cells = row("KENDARAAN").locator("td");
        assertThat(cells.nth(0)).hasText("KENDARAAN");
        assertThat(cells.nth(1)).containsText("Kendaraan");
        assertThat(cells.nth(1)).containsText("Mobil motor dan alat transportasi perusahaan");
        assertThat(cells.nth(2)).hasText("Garis Lurus");
        assertThat(cells.nth(3)).hasText("8 tahun");
        assertThat(cells.nth(4)).hasText("Aktif");

        navigateTo("/assets/categories?search=KOMPUTER");
        waitForPageLoad();
        assertThat(rows()).hasCount(1);
        assertThat(row("KOMPUTER").locator("td").nth(3)).hasText("4 tahun");
    }

    @Test
    @DisplayName("Search and active filter narrow the list; the filter select refreshes the table via HTMX")
    void shouldFilterBySearchAndActive() {
        String token = uid();
        AssetCategory active = createCategory("FA" + token, "Filter " + token + " A", DepreciationMethod.STRAIGHT_LINE, 48, null);
        AssetCategory inactive = createCategory("FB" + token, "Filter " + token + " B", DepreciationMethod.STRAIGHT_LINE, 48, null);
        categoryService.deactivate(inactive.getId());

        assertListCodes("?search=" + token, active.getCode(), inactive.getCode());
        assertListCodes("?search=" + token + "&active=true", active.getCode());
        assertListCodes("?search=" + token + "&active=false", inactive.getCode());
        assertThat(row(inactive.getCode()).locator("td").nth(4)).hasText("Nonaktif");

        navigateTo("/assets/categories?search=" + token);
        waitForPageLoad();
        page.locator("#filter-active").selectOption("false");
        assertThat(rows()).hasCount(1);
        assertThat(row(inactive.getCode())).isVisible();

        APIResponse fragment = page.request().get(baseUrl() + "/assets/categories?search=" + token + "&active=true",
                RequestOptions.create().setHeader("HX-Request", "true"));
        assertThat(fragment.status()).isEqualTo(200);
        String html = fragment.text();
        assertThat(html).contains("id=\"category-table\"").contains(active.getCode())
                .doesNotContain(inactive.getCode()).doesNotContain("page-title").doesNotContain("<html");
    }

    @Test
    @DisplayName("Pagination keeps the page size and the search in its links")
    void shouldPaginateKeepingSizeAndSearch() {
        String token = uid();
        AssetCategory first = createCategory("PA" + token, "Halaman " + token + " A", DepreciationMethod.STRAIGHT_LINE, 48, null);
        AssetCategory second = createCategory("PB" + token, "Halaman " + token + " B", DepreciationMethod.STRAIGHT_LINE, 48, null);

        navigateTo("/assets/categories?search=" + token + "&size=1");
        waitForPageLoad();
        assertThat(rows()).hasCount(1);
        assertThat(row(first.getCode())).isVisible();
        assertThat(page.locator("#category-table")).containsText("Menampilkan 1 - 1 dari 2 kategori");

        page.getByText("Selanjutnya").click();
        waitForPageLoad();
        assertThat(rows()).hasCount(1);
        assertThat(row(second.getCode())).isVisible();
        assertThat(page.locator("#category-table")).containsText("Menampilkan 2 - 2 dari 2 kategori");
        assertThat(page.getByText("Sebelumnya")).isVisible();
    }

    // ==================== create ====================

    @Test
    @DisplayName("New form preselects straight line and 48 months and offers asset and expense accounts")
    void shouldShowNewFormDefaults() {
        navigateTo("/assets/categories/new");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Kategori Aset Baru");
        assertThat(page.locator("#depreciationMethod")).hasValue("STRAIGHT_LINE");
        assertThat(page.locator("#usefulLifeMonths")).hasValue("48");
        assertThat(page.locator("#depreciationRate")).hasValue("");
        assertThat(page.locator("#assetAccount option[value='" + account(COMPUTER_ASSET).getId() + "']"))
                .hasText("1.2.01 - Peralatan Komputer");
        assertThat(page.locator("#accumulatedDepreciationAccount option[value='" + account(COMPUTER_ACCUM).getId() + "']"))
                .hasText("1.2.02 - Akum. Penyusutan Peralatan Komputer");
        assertThat(page.locator("#depreciationExpenseAccount option[value='" + account(DEPRECIATION_EXPENSE).getId() + "']"))
                .hasText("5.1.12 - Beban Penyusutan");
        assertThat(page.locator("#assetAccount option[value='" + account(DEPRECIATION_EXPENSE).getId() + "']")).hasCount(0);
        assertThat(page.locator("#depreciationExpenseAccount option[value='" + account(COMPUTER_ASSET).getId() + "']")).hasCount(0);
    }

    @Test
    @DisplayName("Creating a category via the form persists every field and lists it")
    void shouldCreateCategory() {
        String code = "NEW" + uid();
        navigateTo("/assets/categories/new");
        waitForPageLoad();
        page.locator("#code").fill(code);
        page.locator("#name").fill("Mesin " + code);
        page.locator("#description").fill("Mesin produksi " + code);
        page.locator("#depreciationMethod").selectOption("DECLINING_BALANCE");
        page.locator("#usefulLifeMonths").fill("60");
        page.locator("#depreciationRate").fill("25");
        page.locator("#assetAccount").selectOption(account(OFFICE_ASSET).getId().toString());
        page.locator("#accumulatedDepreciationAccount").selectOption(account(OFFICE_ACCUM).getId().toString());
        page.locator("#depreciationExpenseAccount").selectOption(account(DEPRECIATION_EXPENSE).getId().toString());
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/assets/categories");
        assertThat(page.getByTestId("flash-success")).hasText("Kategori aset berhasil ditambahkan");

        AssetCategory saved = category(code);
        assertThat(saved.getName()).isEqualTo("Mesin " + code);
        assertThat(saved.getDescription()).isEqualTo("Mesin produksi " + code);
        assertThat(saved.getDepreciationMethod()).isEqualTo(DepreciationMethod.DECLINING_BALANCE);
        assertThat(saved.getUsefulLifeMonths()).isEqualTo(60);
        assertThat(saved.getDepreciationRate()).isEqualByComparingTo("25");
        assertThat(saved.getAssetAccount().getId()).isEqualTo(account(OFFICE_ASSET).getId());
        assertThat(saved.getAccumulatedDepreciationAccount().getId()).isEqualTo(account(OFFICE_ACCUM).getId());
        assertThat(saved.getDepreciationExpenseAccount().getId()).isEqualTo(account(DEPRECIATION_EXPENSE).getId());
        assertThat(saved.isActive()).isTrue();

        navigateTo("/assets/categories?search=" + code);
        waitForPageLoad();
        Locator cells = row(code).locator("td");
        assertThat(cells.nth(2)).hasText("Saldo Menurun");
        assertThat(cells.nth(3)).hasText("5 tahun");
        assertThat(cells.nth(4)).hasText("Aktif");
    }

    @Test
    @DisplayName("Server-side validation lists every missing required field and persists nothing")
    void shouldRejectEmptyForm() {
        long before = categoryRepository.count();
        navigateTo("/assets/categories/new");
        waitForPageLoad();
        page.locator("#usefulLifeMonths").fill("");
        page.locator("form:has(#btn-simpan)").evaluate("f => f.noValidate = true");
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Kategori Aset Baru");
        assertThat(errorItems().allTextContents()).containsExactlyInAnyOrder(
                "Kode kategori wajib diisi", "Nama kategori wajib diisi", "Umur ekonomis (bulan) wajib diisi",
                "Akun aset wajib diisi", "Akun akumulasi penyusutan wajib diisi", "Akun beban penyusutan wajib diisi");
        assertThat(categoryRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("Creating with an existing code shows the duplicate error, keeps the input and persists nothing")
    void shouldRejectDuplicateCodeOnCreate() {
        String name = "Duplikat " + uid();
        navigateTo("/assets/categories/new");
        waitForPageLoad();
        page.locator("#code").fill("KOMPUTER");
        page.locator("#name").fill(name);
        page.locator("#assetAccount").selectOption(account(COMPUTER_ASSET).getId().toString());
        page.locator("#accumulatedDepreciationAccount").selectOption(account(COMPUTER_ACCUM).getId().toString());
        page.locator("#depreciationExpenseAccount").selectOption(account(DEPRECIATION_EXPENSE).getId().toString());
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        assertThat(errorItems()).hasText(new String[]{"Kode kategori KOMPUTER sudah digunakan"});
        assertThat(page.locator("#name")).hasValue(name);
        assertThat(categoryRepository.findAll()).noneMatch(c -> name.equals(c.getName()));
        assertThat(category("KOMPUTER").getName()).isEqualTo("Peralatan Komputer");
    }

    // ==================== edit ====================

    @Test
    @DisplayName("Edit form is prefilled and saving updates every field")
    void shouldEditCategory() {
        AssetCategory cat = createCategory("ED" + uid(), "Edit Awal", DepreciationMethod.DECLINING_BALANCE, 60, new BigDecimal("25"));

        navigateTo("/assets/categories/" + cat.getId() + "/edit");
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Edit Kategori Aset");
        assertThat(page.locator("#code")).hasValue(cat.getCode());
        assertThat(page.locator("#name")).hasValue("Edit Awal");
        assertThat(page.locator("#depreciationMethod")).hasValue("DECLINING_BALANCE");
        assertThat(page.locator("#usefulLifeMonths")).hasValue("60");
        assertThat(page.locator("#depreciationRate")).hasValue("25.00");
        assertThat(page.locator("#assetAccount")).hasValue(account(COMPUTER_ASSET).getId().toString());
        assertThat(page.locator("#depreciationExpenseAccount")).hasValue(account(DEPRECIATION_EXPENSE).getId().toString());

        page.locator("#name").fill("Edit Akhir");
        page.locator("#description").fill("Deskripsi baru");
        page.locator("#depreciationMethod").selectOption("STRAIGHT_LINE");
        page.locator("#usefulLifeMonths").fill("36");
        page.locator("#depreciationRate").fill("");
        page.locator("#assetAccount").selectOption(account(OFFICE_ASSET).getId().toString());
        page.locator("#accumulatedDepreciationAccount").selectOption(account(OFFICE_ACCUM).getId().toString());
        page.locator("#depreciationExpenseAccount").selectOption(account(MAINTENANCE_EXPENSE).getId().toString());
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/assets/categories");
        assertThat(page.getByTestId("flash-success")).hasText("Kategori aset berhasil diperbarui");
        AssetCategory saved = category(cat.getCode());
        assertThat(saved.getId()).isEqualTo(cat.getId());
        assertThat(saved.getName()).isEqualTo("Edit Akhir");
        assertThat(saved.getDescription()).isEqualTo("Deskripsi baru");
        assertThat(saved.getDepreciationMethod()).isEqualTo(DepreciationMethod.STRAIGHT_LINE);
        assertThat(saved.getUsefulLifeMonths()).isEqualTo(36);
        assertThat(saved.getDepreciationRate()).isNull();
        assertThat(saved.getAssetAccount().getId()).isEqualTo(account(OFFICE_ASSET).getId());
        assertThat(saved.getAccumulatedDepreciationAccount().getId()).isEqualTo(account(OFFICE_ACCUM).getId());
        assertThat(saved.getDepreciationExpenseAccount().getId()).isEqualTo(account(MAINTENANCE_EXPENSE).getId());
    }

    @Test
    @DisplayName("Changing the code to another category's code is rejected; the form still targets the original and a corrected resubmit updates only it")
    void shouldRejectCodeOfAnotherCategoryOnEdit() {
        String token = uid();
        AssetCategory original = createCategory("OR" + token, "Asli " + token, DepreciationMethod.STRAIGHT_LINE, 48, null);
        AssetCategory other = createCategory("OT" + token, "Lain " + token, DepreciationMethod.STRAIGHT_LINE, 96, null);

        navigateTo("/assets/categories/" + original.getId() + "/edit");
        waitForPageLoad();
        page.locator("#code").fill(other.getCode());
        page.locator("#name").fill("Asli Diubah " + token);
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        assertThat(errorItems()).hasText(new String[]{"Kode kategori " + other.getCode() + " sudah digunakan"});
        assertThat(page.locator("#code")).hasValue(other.getCode());
        assertThat(page.locator("#name")).hasValue("Asli Diubah " + token);
        assertThat(page.locator("form:has(#btn-simpan)"))
                .hasAttribute("action", "/assets/categories/" + original.getId());
        assertThat(category(original.getCode()).getName()).isEqualTo("Asli " + token);

        String newCode = "OX" + token;
        page.locator("#code").fill(newCode);
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        assertThat(page.getByTestId("flash-success")).hasText("Kategori aset berhasil diperbarui");
        AssetCategory updated = categoryRepository.findById(original.getId()).orElseThrow();
        assertThat(updated.getCode()).isEqualTo(newCode);
        assertThat(updated.getName()).isEqualTo("Asli Diubah " + token);
        AssetCategory untouched = categoryRepository.findById(other.getId()).orElseThrow();
        assertThat(untouched.getCode()).isEqualTo(other.getCode());
        assertThat(untouched.getName()).isEqualTo("Lain " + token);
        assertThat(untouched.getUsefulLifeMonths()).isEqualTo(96);
    }

    // ==================== activate / deactivate / delete ====================

    @Test
    @DisplayName("Deactivate and activate buttons on the list toggle the status")
    void shouldDeactivateAndActivateFromList() {
        AssetCategory cat = createCategory("AC" + uid(), "Status " + uid(), DepreciationMethod.STRAIGHT_LINE, 48, null);

        navigateTo("/assets/categories?search=" + cat.getCode());
        waitForPageLoad();
        row(cat.getCode()).locator("button[title='Nonaktifkan']").click();
        waitForPageLoad();

        assertThat(page.getByTestId("flash-success")).hasText("Kategori aset berhasil dinonaktifkan");
        assertThat(category(cat.getCode()).isActive()).isFalse();

        navigateTo("/assets/categories?search=" + cat.getCode());
        waitForPageLoad();
        assertThat(row(cat.getCode()).locator("td").nth(4)).hasText("Nonaktif");
        assertThat(row(cat.getCode()).locator("button[title='Nonaktifkan']")).hasCount(0);
        row(cat.getCode()).locator("button[title='Aktifkan']").click();
        waitForPageLoad();

        assertThat(page.getByTestId("flash-success")).hasText("Kategori aset berhasil diaktifkan");
        assertThat(category(cat.getCode()).isActive()).isTrue();
    }

    @Test
    @DisplayName("Deleting an unused category from the list removes it")
    void shouldDeleteUnusedCategory() {
        AssetCategory cat = createCategory("DL" + uid(), "Hapus " + uid(), DepreciationMethod.STRAIGHT_LINE, 48, null);

        navigateTo("/assets/categories?search=" + cat.getCode());
        waitForPageLoad();
        page.onceDialog(dialog -> dialog.accept());
        row(cat.getCode()).locator("button[title='Hapus']").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/assets/categories");
        assertThat(page.getByTestId("flash-success")).hasText("Kategori aset berhasil dihapus");
        assertThat(categoryRepository.findById(cat.getId())).isEmpty();
    }

    @Test
    @DisplayName("A category used by an asset can be neither deactivated nor deleted")
    void shouldRefuseDeactivateAndDeleteWhenInUse() {
        AssetCategory cat = createCategory("IU" + uid(), "Dipakai " + uid(), DepreciationMethod.STRAIGHT_LINE, 48, null);
        createAsset(cat);

        navigateTo("/assets/categories?search=" + cat.getCode());
        waitForPageLoad();
        row(cat.getCode()).locator("button[title='Nonaktifkan']").click();
        waitForPageLoad();
        assertThat(page.getByTestId("flash-error"))
                .hasText("Kategori tidak dapat dinonaktifkan karena masih digunakan oleh 1 aset");
        assertThat(category(cat.getCode()).isActive()).isTrue();

        navigateTo("/assets/categories?search=" + cat.getCode());
        waitForPageLoad();
        page.onceDialog(dialog -> dialog.accept());
        row(cat.getCode()).locator("button[title='Hapus']").click();
        waitForPageLoad();
        assertThat(page.getByTestId("flash-error"))
                .hasText("Kategori tidak dapat dihapus karena masih digunakan oleh 1 aset");
        assertThat(categoryRepository.findById(cat.getId())).isPresent();
    }

    // ==================== authorization ====================

    @Test
    @DisplayName("AUDITOR sees the list without write controls and every write endpoint answers 403 without changes")
    void shouldRefuseWritesForAuditor() {
        AssetCategory cat = createCategory("AU" + uid(), "Auditor " + uid(), DepreciationMethod.STRAIGHT_LINE, 48, null);
        long before = categoryRepository.count();

        User auditor = createUser(Role.AUDITOR);
        login(auditor.getUsername(), "password");
        navigateTo("/assets/categories?search=" + cat.getCode());
        waitForPageLoad();
        assertThat(row(cat.getCode())).isVisible();
        assertThat(page.locator("#btn-new-category")).hasCount(0);
        assertThat(row(cat.getCode()).locator("a[title='Edit'], button")).hasCount(0);

        String csrfHeader = page.locator("meta[name='_csrf_header']").getAttribute("content");
        String csrfToken = page.locator("meta[name='_csrf']").getAttribute("content");
        String base = "/assets/categories/" + cat.getId();
        for (String path : List.of("/assets/categories/new", base + "/edit")) {
            assertThat(page.request().get(baseUrl() + path).status()).as("GET " + path).isEqualTo(403);
        }
        for (String path : List.of("/assets/categories/new", base, base + "/deactivate", base + "/activate", base + "/delete")) {
            APIResponse response = page.request().post(baseUrl() + path, RequestOptions.create()
                    .setHeader(csrfHeader, csrfToken)
                    .setForm(FormData.create()
                            .set("code", "HACK" + uid()).set("name", "Hack")
                            .set("depreciationMethod", "STRAIGHT_LINE").set("usefulLifeMonths", "12")
                            .set("assetAccount", account(COMPUTER_ASSET).getId().toString())
                            .set("accumulatedDepreciationAccount", account(COMPUTER_ACCUM).getId().toString())
                            .set("depreciationExpenseAccount", account(DEPRECIATION_EXPENSE).getId().toString()))
                    .setMaxRedirects(0));
            assertThat(response.status()).as("POST " + path).isEqualTo(403);
        }

        AssetCategory after = category(cat.getCode());
        assertThat(after.getName()).isEqualTo(cat.getName());
        assertThat(after.isActive()).isTrue();
        assertThat(categoryRepository.count()).isEqualTo(before);
    }

    // ==================== helpers ====================

    private AssetCategory createCategory(String code, String name, DepreciationMethod method, int months, BigDecimal rate) {
        AssetCategory category = new AssetCategory();
        category.setCode(code);
        category.setName(name);
        category.setDepreciationMethod(method);
        category.setUsefulLifeMonths(months);
        category.setDepreciationRate(rate);
        category.setAssetAccount(account(COMPUTER_ASSET));
        category.setAccumulatedDepreciationAccount(account(COMPUTER_ACCUM));
        category.setDepreciationExpenseAccount(account(DEPRECIATION_EXPENSE));
        return categoryService.create(category);
    }

    private void createAsset(AssetCategory category) {
        FixedAsset asset = new FixedAsset();
        asset.setAssetCode("CATUSE-" + uid());
        asset.setName("Aset Kategori " + category.getCode());
        asset.setCategory(category);
        asset.setPurchaseDate(LocalDate.of(2051, 1, 10));
        asset.setPurchaseCost(new BigDecimal("4800000"));
        asset.setFundingAccount(account("1.1.02"));
        fixedAssetService.create(asset);
    }

    private User createUser(Role role) {
        String id = uid().toLowerCase();
        User user = new User();
        user.setUsername("acat-" + role.name().toLowerCase() + "-" + id);
        user.setPassword(passwordEncoder.encode("password"));
        user.setFullName("Pengguna " + id);
        user.setEmail("acat-" + id + "@example.com");
        user.setActive(true);
        user.addRole(role, "system");
        return userRepository.save(user);
    }

    private void assertListCodes(String query, String... codes) {
        navigateTo("/assets/categories" + query);
        waitForPageLoad();
        assertThat(rows()).hasCount(codes.length);
        for (int i = 0; i < codes.length; i++) {
            assertThat(rows().nth(i)).hasAttribute("data-code", codes[i]);
        }
    }

    private Locator rows() {
        return page.locator("#category-table tbody tr[data-code]");
    }

    private Locator row(String code) {
        return page.locator("#category-table tbody tr[data-code='" + code + "']");
    }

    private Locator errorItems() {
        return page.locator("form:has(#btn-simpan) ul li");
    }

    private AssetCategory category(String code) {
        return categoryRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Asset category missing: " + code));
    }

    private ChartOfAccount account(String code) {
        return accountRepository.findByAccountCode(code)
                .orElseThrow(() -> new IllegalStateException("Seeded account missing: " + code));
    }

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
