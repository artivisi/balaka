package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.BillOfMaterial;
import com.artivisi.accountingfinance.entity.BillOfMaterialLine;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.CostingMethod;
import com.artivisi.accountingfinance.entity.Product;
import com.artivisi.accountingfinance.entity.ProductCategory;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.ProductCategoryRepository;
import com.artivisi.accountingfinance.repository.ProductRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.service.BillOfMaterialService;
import com.artivisi.accountingfinance.service.InventoryService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ProductController: list with search/category/status filters and pagination, detail, create/edit form,
 * validation, activate/deactivate, delete guards and permissions.
 *
 * Seeded products and categories feed the manufacturing and sales tests, so every record used here is
 * created by this class with a unique code.
 */
@DisplayName("Product Controller Tests")
@Import(ServiceTestDataInitializer.class)
class ProductControllerFunctionalTest extends PlaywrightTestBase {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductCategoryRepository categoryRepository;

    @Autowired
    private ChartOfAccountRepository accountRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private BillOfMaterialService bomService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ==================== LIST ====================

    @Test
    @DisplayName("Should list products with category, unit, price and status and filter them by search, category and status")
    void shouldListAndFilterProducts() {
        String u = uid();
        ProductCategory drinks = createCategory("Minuman", true);
        ProductCategory snacks = createCategory("Camilan", true);
        Product coffee = createProduct("PRD-" + u + "-A", "Kopi Uji " + u, drinks, "kg", new BigDecimal("15000"), true);
        Product tea = createProduct("PRD-" + u + "-B", "Teh Uji " + u, snacks, "pcs", null, false);
        loginAsAdmin();

        navigateTo("/products?search=" + u);
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Daftar Produk");
        page.locator("#nav-group-inventori").click();
        assertThat(page.locator("#nav-products")).isVisible();
        assertThat(page.locator("#nav-products")).hasAttribute("href", "/products");
        assertThat(page.locator("#nav-products")).hasClass(Pattern.compile(".*\\bbg-primary-600\\b.*"));
        assertThat(page.locator("[data-testid^='product-row-']")).hasCount(2);
        assertThat(page.getByTestId("product-code-" + coffee.getCode())).hasText(coffee.getCode());
        assertThat(page.getByTestId("product-code-" + coffee.getCode())).hasAttribute("href", "/products/" + coffee.getId());
        assertThat(page.getByTestId("product-name-" + coffee.getCode())).hasText(coffee.getName());
        assertThat(page.getByTestId("product-category-" + coffee.getCode())).hasText(drinks.getName());
        assertThat(page.getByTestId("product-unit-" + coffee.getCode())).hasText("kg");
        assertThat(page.getByTestId("product-price-" + coffee.getCode())).hasText("15,000");
        assertThat(page.getByTestId("product-status-" + coffee.getCode())).hasText("Aktif");
        assertThat(page.getByTestId("product-price-" + tea.getCode())).hasText("-");
        assertThat(page.getByTestId("product-status-" + tea.getCode())).hasText("Nonaktif");
        assertThat(page.getByTestId("product-page-info")).hasText("Menampilkan 1 - 2 dari 2 produk");

        page.locator("#category-filter").selectOption(drinks.getId().toString());
        assertThat(page.locator("[data-testid^='product-row-']")).hasCount(1);
        assertThat(page.getByTestId("product-row-" + coffee.getCode())).isVisible();

        page.locator("#category-filter").selectOption("");
        page.locator("#active-filter").selectOption("false");
        assertThat(page.locator("[data-testid^='product-row-']")).hasCount(1);
        assertThat(page.getByTestId("product-row-" + tea.getCode())).isVisible();

        page.locator("#active-filter").selectOption("true");
        assertThat(page.locator("[data-testid^='product-row-']")).hasCount(1);
        assertThat(page.getByTestId("product-row-" + coffee.getCode())).isVisible();

        page.locator("#active-filter").selectOption("");
        page.locator("#search-input").fill("");
        page.locator("#search-input").pressSequentially("teh uji " + u.toLowerCase());
        assertThat(page.locator("[data-testid^='product-row-']")).hasCount(1);
        assertThat(page.getByTestId("product-row-" + tea.getCode())).isVisible();

        navigateTo("/products?search=" + u + "&categoryId=" + snacks.getId() + "&active=true");
        waitForPageLoad();
        assertThat(page.locator("#category-filter")).hasValue(snacks.getId().toString());
        assertThat(page.locator("#active-filter")).hasValue("true");
        assertThat(page.locator("#search-input")).hasValue(u);
        assertThat(page.locator("[data-testid^='product-row-']")).hasCount(0);
        assertThat(page.locator("#product-table td:text-is('Tidak ada produk')")).isVisible();
    }

    @Test
    @DisplayName("Should paginate the product list 20 per page ordered by code")
    void shouldPaginateProducts() {
        String prefix = "PG-" + uid();
        for (int i = 1; i <= 21; i++) {
            createProduct(prefix + "-" + String.format("%02d", i), "Produk Halaman " + i, null, "pcs", null, true);
        }
        loginAsAdmin();

        navigateTo("/products?search=" + prefix);
        waitForPageLoad();
        assertThat(page.locator("[data-testid^='product-row-']")).hasCount(20);
        assertThat(page.locator("[data-testid^='product-row-']").first()).hasAttribute("data-testid", "product-row-" + prefix + "-01");
        assertThat(page.getByTestId("product-page-info")).hasText("Menampilkan 1 - 20 dari 21 produk");

        page.locator("#product-table a:text-is('Selanjutnya')").click();
        waitForPageLoad();
        assertThat(page.locator("[data-testid^='product-row-']")).hasCount(1);
        assertThat(page.getByTestId("product-row-" + prefix + "-21")).isVisible();
        assertThat(page.getByTestId("product-page-info")).hasText("Menampilkan 21 - 21 dari 21 produk");
    }

    // ==================== CREATE ====================

    @Test
    @DisplayName("Should show the new product form with defaults, active categories only and account options")
    void shouldShowNewProductForm() {
        ProductCategory active = createCategory("Aktif", true);
        ProductCategory inactive = createCategory("Mati", false);
        loginAsAdmin();

        navigateTo("/products");
        waitForPageLoad();
        page.locator("#btn-new-product").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/products/new");
        assertThat(page.locator("#page-title")).hasText("Produk Baru");
        assertThat(page.locator("#code")).hasValue("");
        assertThat(page.locator("#costingMethod")).hasValue("WEIGHTED_AVERAGE");
        assertEquals(List.of("FIFO (First In, First Out)", "Rata-rata Tertimbang"),
                page.locator("#costingMethod option").allTextContents());
        assertThat(page.locator("#trackInventory")).isChecked();
        assertThat(page.locator("#active")).isChecked();
        assertThat(page.locator("#minimumStock")).hasValue("0");
        assertThat(page.locator("#category option[value='" + active.getId() + "']"))
                .hasText(active.getCode() + " - " + active.getName());
        assertThat(page.locator("#category option[value='" + inactive.getId() + "']")).hasCount(0);
        assertThat(page.locator("#inventoryAccount option[value='" + account("1.1.15").getId() + "']"))
                .hasText("1.1.15 - Beban Dibayar Dimuka");
        assertThat(page.locator("#cogsAccount option[value='" + account("5.1.09").getId() + "']"))
                .hasText("5.1.09 - Beban ATK");
        assertThat(page.locator("#salesAccount option[value='" + account("4.1.99").getId() + "']"))
                .hasText("4.1.99 - Pendapatan Usaha Lainnya");
        assertThat(page.locator("#salesAccount option[value='" + account("5.1.09").getId() + "']")).hasCount(0);
    }

    @Test
    @DisplayName("Should create a product with all fields and show them on the list and detail page")
    void shouldCreateProduct() {
        ProductCategory category = createCategory("Bahan", true);
        String code = "PRD-NEW-" + uid();
        loginAsAdmin();

        navigateTo("/products/new");
        waitForPageLoad();
        page.locator("#code").fill(code);
        page.locator("#name").fill("Gula Aren " + code);
        page.locator("#unit").fill("kg");
        page.locator("#category").selectOption(category.getId().toString());
        page.locator("#description").fill("Gula aren cair, kemasan 1 kg");
        page.locator("#costingMethod").selectOption("FIFO");
        page.locator("#trackInventory").uncheck();
        page.locator("#minimumStock").fill("0.5");
        page.locator("#sellingPrice").fill("15000");
        page.locator("#inventoryAccount").selectOption(account("1.1.15").getId().toString());
        page.locator("#cogsAccount").selectOption(account("5.1.09").getId().toString());
        page.locator("#salesAccount").selectOption(account("4.1.99").getId().toString());
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/products");
        assertThat(page.getByTestId("flash-success")).hasText("Produk berhasil ditambahkan");
        assertThat(page.getByTestId("product-name-" + code)).hasText("Gula Aren " + code);

        Product saved = productByCode(code);
        assertEquals("Gula Aren " + code, saved.getName());
        assertEquals("kg", saved.getUnit());
        assertEquals(category.getId(), saved.getCategory().getId());
        assertEquals("Gula aren cair, kemasan 1 kg", saved.getDescription());
        assertEquals(CostingMethod.FIFO, saved.getCostingMethod());
        assertFalse(saved.isTrackInventory());
        assertEquals(0, new BigDecimal("0.5").compareTo(saved.getMinimumStock()));
        assertEquals(0, new BigDecimal("15000").compareTo(saved.getSellingPrice()));
        assertEquals("1.1.15", saved.getInventoryAccount().getAccountCode());
        assertEquals("5.1.09", saved.getCogsAccount().getAccountCode());
        assertEquals("4.1.99", saved.getSalesAccount().getAccountCode());
        assertTrue(saved.isActive());

        page.getByTestId("product-code-" + code).click();
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/products/" + saved.getId());
        assertThat(page.getByTestId("product-detail-heading")).hasText("Gula Aren " + code);
        assertThat(page.getByTestId("product-detail-status")).hasText("Aktif");
        assertThat(page.getByTestId("product-detail-code")).hasText(code);
        assertThat(page.getByTestId("product-detail-unit")).hasText("kg");
        assertThat(page.getByTestId("product-detail-category")).hasText(category.getName());
        assertThat(page.getByTestId("product-detail-description")).hasText("Gula aren cair, kemasan 1 kg");
        assertThat(page.getByTestId("product-detail-track-inventory")).hasText("Tidak");
        assertThat(page.getByTestId("product-detail-costing-method")).hasText("FIFO (First In, First Out)");
        assertThat(page.getByTestId("product-detail-minimum-stock")).hasText("0.5000");
        assertThat(page.getByTestId("product-detail-selling-price")).hasText("Rp 15,000");
        assertThat(page.getByTestId("product-detail-inventory-account")).hasText("1.1.15 - Beban Dibayar Dimuka");
        assertThat(page.getByTestId("product-detail-cogs-account")).hasText("5.1.09 - Beban ATK");
        assertThat(page.getByTestId("product-detail-sales-account")).hasText("4.1.99 - Pendapatan Usaha Lainnya");
    }

    @Test
    @DisplayName("Should reject missing fields, blank minimum stock, negative amounts, duplicate code and unknown category without saving")
    void shouldRejectInvalidProduct() {
        Product existing = createProduct("PRD-DUP-" + uid(), "Produk Lama", null, "pcs", null, true);
        String code = "PRD-BAD-" + uid();
        loginAsAdmin();

        navigateTo("/products/new");
        waitForPageLoad();
        removeRequiredAttributes();
        page.locator("#minimumStock").fill("");
        page.locator("#btn-simpan").click();
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/products/new");
        assertThat(page.locator("#page-title")).hasText("Produk Baru");
        Locator errors = page.getByTestId("form-errors").locator("li");
        assertThat(errors).containsText(new String[]{"Kode produk wajib diisi"});
        assertThat(errors).containsText(new String[]{"Nama produk wajib diisi"});
        assertThat(errors).containsText(new String[]{"Satuan wajib diisi"});
        assertThat(errors).containsText(new String[]{"Stok minimum wajib diisi"});

        page.locator("#code").fill(code);
        page.locator("#name").fill("Produk Negatif");
        page.locator("#unit").fill("pcs");
        page.locator("#minimumStock").fill("-1");
        page.locator("#sellingPrice").fill("-5000");
        removeRequiredAttributes();
        page.evaluate("() => { document.getElementById('minimumStock').removeAttribute('min');"
                + " document.getElementById('sellingPrice').removeAttribute('min'); }");
        page.locator("#btn-simpan").click();
        waitForPageLoad();
        assertThat(errors).hasCount(2);
        assertThat(errors).containsText(new String[]{"Stok minimum tidak boleh negatif"});
        assertThat(errors).containsText(new String[]{"Harga jual tidak boleh negatif"});
        assertThat(page.locator("#code")).hasValue(code);
        assertTrue(productRepository.findByCode(code).isEmpty());

        page.locator("#code").fill(existing.getCode());
        page.locator("#minimumStock").fill("0");
        page.locator("#sellingPrice").fill("");
        page.locator("#btn-simpan").click();
        waitForPageLoad();
        assertThat(errors).hasText("Kode produk sudah digunakan: " + existing.getCode());
        assertEquals("Produk Lama", productRepository.findById(existing.getId()).orElseThrow().getName());

        String csrf = csrf();
        UUID unknownCategory = UUID.randomUUID();
        APIResponse response = postForm("/products/new", productForm(csrf, code, "Produk Tanpa Kategori")
                .set("category", unknownCategory.toString()));
        assertEquals(200, response.status());
        assertTrue(response.text().contains("Kategori produk tidak ditemukan: " + unknownCategory));
        assertTrue(productRepository.findByCode(code).isEmpty());
    }

    // ==================== EDIT ====================

    @Test
    @DisplayName("Should prefill the edit form and update the product in place")
    void shouldUpdateProduct() {
        ProductCategory oldCategory = createCategory("Lama", true);
        ProductCategory newCategory = createCategory("Baru", true);
        Product product = createProduct("PRD-UPD-" + uid(), "Produk Ubah", oldCategory, "pcs", new BigDecimal("2500"), true);
        product.setMinimumStock(new BigDecimal("0.75"));
        product.setInventoryAccount(account("1.1.15"));
        productRepository.save(product);
        String newCode = "PRD-UPD2-" + uid();
        loginAsAdmin();

        navigateTo("/products/" + product.getId());
        waitForPageLoad();
        assertThat(page.getByTestId("product-detail-minimum-stock")).hasText("0.7500");
        page.locator("section a:text-is('Edit')").first().click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/products/" + product.getId() + "/edit");
        assertThat(page.locator("#page-title")).hasText("Edit Produk");
        assertThat(page.locator("#code")).hasValue(product.getCode());
        assertThat(page.locator("#name")).hasValue("Produk Ubah");
        assertThat(page.locator("#unit")).hasValue("pcs");
        assertThat(page.locator("#category")).hasValue(oldCategory.getId().toString());
        assertThat(page.locator("#costingMethod")).hasValue("WEIGHTED_AVERAGE");
        assertThat(page.locator("#minimumStock")).hasValue("0.7500");
        assertThat(page.locator("#sellingPrice")).hasValue("2500.00");
        assertThat(page.locator("#inventoryAccount")).hasValue(account("1.1.15").getId().toString());
        assertThat(page.locator("#cogsAccount")).hasValue("");
        assertThat(page.locator("#trackInventory")).isChecked();
        assertThat(page.locator("#active")).isChecked();

        page.locator("#code").fill(newCode);
        page.locator("#name").fill("Produk Sudah Diubah");
        page.locator("#unit").fill("box");
        page.locator("#category").selectOption(newCategory.getId().toString());
        page.locator("#costingMethod").selectOption("FIFO");
        page.locator("#minimumStock").fill("3");
        page.locator("#sellingPrice").fill("4000");
        page.locator("#inventoryAccount").selectOption("");
        page.locator("#salesAccount").selectOption(account("4.1.99").getId().toString());
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/products");
        assertThat(page.getByTestId("flash-success")).hasText("Produk berhasil diubah");
        Product updated = productRepository.findByIdWithDetails(product.getId()).orElseThrow();
        assertEquals(newCode, updated.getCode());
        assertEquals("Produk Sudah Diubah", updated.getName());
        assertEquals("box", updated.getUnit());
        assertEquals(newCategory.getId(), updated.getCategory().getId());
        assertEquals(CostingMethod.FIFO, updated.getCostingMethod());
        assertEquals(0, new BigDecimal("3").compareTo(updated.getMinimumStock()));
        assertEquals(0, new BigDecimal("4000").compareTo(updated.getSellingPrice()));
        assertNull(updated.getInventoryAccount());
        assertEquals("4.1.99", updated.getSalesAccount().getAccountCode());
    }

    @Test
    @DisplayName("Should keep an inactive category when editing a product that belongs to it")
    void shouldKeepInactiveCategoryOnEdit() {
        ProductCategory category = createCategory("Arsip", true);
        Product product = createProduct("PRD-ARS-" + uid(), "Produk Arsip", category, "pcs", null, true);
        category.setActive(false);
        categoryRepository.save(category);
        loginAsAdmin();

        navigateTo("/products/" + product.getId() + "/edit");
        waitForPageLoad();
        assertThat(page.locator("#category")).hasValue(category.getId().toString());
        page.locator("#name").fill("Produk Arsip Diubah");
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        assertThat(page.getByTestId("flash-success")).hasText("Produk berhasil diubah");
        Product updated = productRepository.findByIdWithDetails(product.getId()).orElseThrow();
        assertEquals("Produk Arsip Diubah", updated.getName());
        assertEquals(category.getId(), updated.getCategory().getId());
    }

    @Test
    @DisplayName("Should reject changing the code to another product's code, keep the form on the edited product, and resubmit only it")
    void shouldRejectDuplicateCodeOnUpdate() {
        Product product = createProduct("PRD-ORI-" + uid(), "Produk Asli", null, "pcs", null, true);
        Product other = createProduct("PRD-OTH-" + uid(), "Produk Lain", null, "kg", null, true);
        String fixedCode = "PRD-FIX-" + uid();
        loginAsAdmin();

        navigateTo("/products/" + product.getId() + "/edit");
        waitForPageLoad();
        page.locator("#code").fill(other.getCode());
        page.locator("#name").fill("Produk Asli Diubah");
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        assertThat(page.getByTestId("form-errors").locator("li")).hasText("Kode produk sudah digunakan: " + other.getCode());
        assertThat(page.locator("#page-title")).hasText("Edit Produk");
        assertThat(page.locator("section form[method='post']")).hasAttribute("action", "/products/" + product.getId() + "/edit");
        assertEquals(product.getCode(), productRepository.findById(product.getId()).orElseThrow().getCode());

        page.locator("#code").fill(fixedCode);
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        assertThat(page.getByTestId("flash-success")).hasText("Produk berhasil diubah");
        Product updated = productRepository.findById(product.getId()).orElseThrow();
        assertEquals(fixedCode, updated.getCode());
        assertEquals("Produk Asli Diubah", updated.getName());
        Product untouched = productRepository.findById(other.getId()).orElseThrow();
        assertEquals(other.getCode(), untouched.getCode());
        assertEquals("Produk Lain", untouched.getName());
    }

    // ==================== ACTIVATE / DEACTIVATE ====================

    @Test
    @DisplayName("Should deactivate after the confirm is accepted and activate again from the detail page")
    void shouldDeactivateAndActivateProduct() {
        Product product = createProduct("PRD-ACT-" + uid(), "Produk Status", null, "pcs", null, true);
        loginAsAdmin();

        navigateTo("/products/" + product.getId());
        waitForPageLoad();
        assertThat(page.locator("form[action$='/activate']")).hasCount(0);
        page.onceDialog(dialog -> {
            assertEquals("Nonaktifkan produk ini?", dialog.message());
            dialog.dismiss();
        });
        page.locator("#form-deactivate button").click();
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/products/" + product.getId());
        assertTrue(productRepository.findById(product.getId()).orElseThrow().isActive());

        page.onceDialog(dialog -> dialog.accept());
        page.locator("#form-deactivate button").click();
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/products");
        assertThat(page.getByTestId("flash-success")).hasText("Produk berhasil dinonaktifkan");
        assertFalse(productRepository.findById(product.getId()).orElseThrow().isActive());

        navigateTo("/products/" + product.getId());
        waitForPageLoad();
        assertThat(page.getByTestId("product-detail-status")).hasText("Nonaktif");
        assertThat(page.locator("#form-deactivate")).hasCount(0);
        page.locator("form[action$='/activate'] button").click();
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/products");
        assertThat(page.getByTestId("flash-success")).hasText("Produk berhasil diaktifkan");
        assertTrue(productRepository.findById(product.getId()).orElseThrow().isActive());
    }

    // ==================== DELETE ====================

    @Test
    @DisplayName("Should keep the product when the confirm is dismissed and delete it when accepted")
    void shouldDeleteProduct() {
        Product product = createProduct("PRD-DEL-" + uid(), "Produk Hapus", null, "pcs", null, true);
        loginAsAdmin();

        navigateTo("/products/" + product.getId());
        waitForPageLoad();
        page.onceDialog(dialog -> {
            assertEquals("Hapus produk ini? Tindakan ini tidak dapat dibatalkan.", dialog.message());
            dialog.dismiss();
        });
        page.locator("#form-delete button").click();
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/products/" + product.getId());
        assertTrue(productRepository.findById(product.getId()).isPresent());

        page.onceDialog(dialog -> dialog.accept());
        page.locator("#form-delete button").click();
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/products");
        assertThat(page.getByTestId("flash-success")).hasText("Produk berhasil dihapus");
        assertTrue(productRepository.findById(product.getId()).isEmpty());
    }

    @Test
    @DisplayName("Should refuse to delete a product with inventory transactions or used in a BOM")
    void shouldRefuseToDeleteProductInUse() {
        Product stocked = createProduct("PRD-STK-" + uid(), "Produk Berstok", null, "kg", null, true);
        stocked.setInventoryAccount(account("1.1.15"));
        productRepository.save(stocked);
        inventoryService.recordPurchase(stocked.getId(), LocalDate.of(2030, 6, 15), new BigDecimal("2"),
                new BigDecimal("10000"), "REF-" + stocked.getCode(), "Pembelian uji");
        Product finished = createProduct("PRD-FIN-" + uid(), "Produk Jadi", null, "pcs", null, true);
        Product component = createProduct("PRD-CMP-" + uid(), "Komponen", null, "kg", null, true);
        createBom(finished, component);
        loginAsAdmin();

        for (Product product : List.of(stocked, finished, component)) {
            navigateTo("/products/" + product.getId());
            waitForPageLoad();
            page.onceDialog(dialog -> dialog.accept());
            page.locator("#form-delete button").click();
            waitForPageLoad();
            assertThat(page).hasURL(baseUrl() + "/products");
            assertThat(page.getByTestId("flash-success")).hasCount(0);
            assertThat(page.getByTestId("flash-error")).hasText(product == stocked
                    ? "Tidak dapat menghapus produk yang memiliki transaksi inventori"
                    : "Tidak dapat menghapus produk yang digunakan di Bill of Materials");
            assertTrue(productRepository.findById(product.getId()).isPresent());
        }
    }

    // ==================== PERMISSIONS ====================

    @Test
    @DisplayName("Should let a view-only user browse products but not create, edit, (de)activate or delete them")
    void shouldDenyChangesWithoutProductPermissions() {
        Product product = createProduct("PRD-AUD-" + uid(), "Produk Auditor", null, "pcs", null, true);
        User auditor = createUser(Role.AUDITOR);
        login(auditor.getUsername(), "password");

        navigateTo("/products?search=" + product.getCode());
        waitForPageLoad();
        Locator row = page.getByTestId("product-row-" + product.getCode());
        assertThat(row).isVisible();
        assertThat(row.locator("a:text-is('Edit')")).hasCount(0);
        assertThat(page.locator("#btn-new-product")).hasCount(0);

        navigateTo("/products/" + product.getId());
        waitForPageLoad();
        assertThat(page.getByTestId("product-detail-code")).hasText(product.getCode());
        assertThat(page.locator("section a:text-is('Edit')")).hasCount(0);
        assertThat(page.locator("#form-deactivate")).hasCount(0);
        assertThat(page.locator("#form-delete")).hasCount(0);
        String csrf = csrf();

        for (String path : List.of("/products/new", "/products/" + product.getId() + "/edit")) {
            navigateTo(path);
            waitForPageLoad();
            assertThat(page.locator("#access-denied-page")).isVisible();
        }

        String newCode = "PRD-ILG-" + uid();
        assertEquals(403, postForm("/products/new", productForm(csrf, newCode, "Ilegal")).status());
        assertEquals(403, postForm("/products/" + product.getId() + "/edit",
                productForm(csrf, product.getCode(), "Diubah Auditor")).status());
        for (String action : List.of("deactivate", "activate", "delete")) {
            assertEquals(403, postForm("/products/" + product.getId() + "/" + action,
                    FormData.create().set("_csrf", csrf)).status());
        }

        assertTrue(productRepository.findByCode(newCode).isEmpty());
        Product unchanged = productRepository.findById(product.getId()).orElseThrow();
        assertEquals("Produk Auditor", unchanged.getName());
        assertTrue(unchanged.isActive());
    }

    // ==================== helpers ====================

    private ProductCategory createCategory(String name, boolean active) {
        String code = "PC-" + uid();
        ProductCategory category = new ProductCategory();
        category.setCode(code);
        category.setName(name + " " + code);
        category.setActive(active);
        return categoryRepository.save(category);
    }

    private Product createProduct(String code, String name, ProductCategory category, String unit,
                                  BigDecimal sellingPrice, boolean active) {
        Product product = new Product();
        product.setCode(code);
        product.setName(name);
        product.setCategory(category);
        product.setUnit(unit);
        product.setCostingMethod(CostingMethod.WEIGHTED_AVERAGE);
        product.setSellingPrice(sellingPrice);
        product.setActive(active);
        return productRepository.save(product);
    }

    private void createBom(Product finished, Product component) {
        BillOfMaterial bom = new BillOfMaterial();
        bom.setCode("BOM-" + uid());
        bom.setName("Resep " + finished.getName());
        bom.setProduct(finished);
        bom.setOutputQuantity(BigDecimal.ONE);
        bom.setActive(true);
        bom.setLines(new ArrayList<>());
        BillOfMaterialLine line = new BillOfMaterialLine();
        line.setComponent(component);
        line.setQuantity(new BigDecimal("0.25"));
        line.setLineOrder(0);
        bom.getLines().add(line);
        bomService.create(bom);
    }

    private ChartOfAccount account(String code) {
        return accountRepository.findByAccountCode(code)
                .orElseThrow(() -> new IllegalStateException("Seeded account missing: " + code));
    }

    private Product productByCode(String code) {
        UUID id = productRepository.findByCode(code)
                .orElseThrow(() -> new AssertionError("Product not saved: " + code)).getId();
        return productRepository.findByIdWithDetails(id).orElseThrow();
    }

    private void removeRequiredAttributes() {
        page.evaluate("() => document.querySelectorAll('[required]').forEach(e => e.removeAttribute('required'))");
    }

    private FormData productForm(String csrf, String code, String name) {
        return FormData.create().set("_csrf", csrf).set("code", code).set("name", name).set("unit", "pcs")
                .set("costingMethod", "WEIGHTED_AVERAGE").set("minimumStock", "0").set("active", "true");
    }

    private String csrf() {
        return page.locator("meta[name='_csrf']").getAttribute("content");
    }

    private APIResponse postForm(String path, FormData form) {
        return page.request().post(baseUrl() + path, RequestOptions.create().setMaxRedirects(0).setForm(form));
    }

    private User createUser(Role role) {
        String id = uid();
        User user = new User();
        user.setUsername("prd-user-" + id);
        user.setPassword(passwordEncoder.encode("password"));
        user.setFullName("Pengguna " + id);
        user.setEmail("prd-user-" + id + "@example.com");
        user.setActive(true);
        user.addRole(role, "system");
        return userRepository.save(user);
    }

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
