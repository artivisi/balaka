package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.CostingMethod;
import com.artivisi.accountingfinance.entity.Product;
import com.artivisi.accountingfinance.entity.ProductCategory;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ProductCategoryRepository;
import com.artivisi.accountingfinance.repository.ProductRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
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

import java.util.List;
import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ProductCategoryController: list with search and pagination, create/edit form with parent category,
 * validation (duplicate code, circular parent), delete guards and permissions.
 *
 * Seeded categories are referenced by seeded products, so every category used here is created by this
 * class with a unique code.
 */
@DisplayName("Product Category Controller Tests")
@Import(ServiceTestDataInitializer.class)
class ProductCategoryControllerFunctionalTest extends PlaywrightTestBase {

    @Autowired
    private ProductCategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ==================== LIST ====================

    @Test
    @DisplayName("Should list categories with parent and status and search them by code or name")
    void shouldListAndSearchCategories() {
        String u = uid();
        ProductCategory parent = createCategory("PC" + u + "A", "Bahan Baku " + u, null, true);
        ProductCategory child = createCategory("PC" + u + "B", "Bahan Kering " + u, parent, false);
        loginAsAdmin();

        navigateTo("/products");
        waitForPageLoad();
        page.locator("section a:text-is('Kategori')").click();
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/products/categories");
        assertThat(page.locator("#page-title")).hasText("Kategori Produk");

        page.locator("#search-input").pressSequentially(u);
        assertThat(page.locator("[data-testid^='category-row-']")).hasCount(2);
        assertThat(page.getByTestId("category-code-" + parent.getCode())).hasText(parent.getCode());
        assertThat(page.getByTestId("category-name-" + parent.getCode())).hasText(parent.getName());
        assertThat(page.getByTestId("category-parent-" + parent.getCode())).hasText("-");
        assertThat(page.getByTestId("category-status-" + parent.getCode())).hasText("Aktif");
        assertThat(page.getByTestId("category-parent-" + child.getCode())).hasText(parent.getName());
        assertThat(page.getByTestId("category-status-" + child.getCode())).hasText("Nonaktif");
        assertThat(page.getByTestId("category-page-info")).hasText("Menampilkan 1 - 2 dari 2 kategori");

        page.locator("#search-input").fill("");
        page.locator("#search-input").pressSequentially("bahan kering " + u.toLowerCase());
        assertThat(page.locator("[data-testid^='category-row-']")).hasCount(1);
        assertThat(page.getByTestId("category-row-" + child.getCode())).isVisible();

        navigateTo("/products/categories?search=" + child.getCode().toLowerCase());
        waitForPageLoad();
        assertThat(page.locator("#search-input")).hasValue(child.getCode().toLowerCase());
        assertThat(page.locator("[data-testid^='category-row-']")).hasCount(1);
        assertThat(page.getByTestId("category-row-" + child.getCode())).isVisible();

        navigateTo("/products/categories?search=tidak-ada-" + u);
        waitForPageLoad();
        assertThat(page.locator("[data-testid^='category-row-']")).hasCount(0);
        assertThat(page.locator("#category-table td:text-is('Tidak ada kategori produk')")).isVisible();
    }

    @Test
    @DisplayName("Should paginate the category list 20 per page ordered by code")
    void shouldPaginateCategories() {
        String prefix = "PG" + uid();
        for (int i = 1; i <= 21; i++) {
            createCategory(prefix + String.format("%02d", i), "Kategori Halaman " + i, null, true);
        }
        loginAsAdmin();

        navigateTo("/products/categories?search=" + prefix);
        waitForPageLoad();
        assertThat(page.locator("[data-testid^='category-row-']")).hasCount(20);
        assertThat(page.locator("[data-testid^='category-row-']").first()).hasAttribute("data-testid", "category-row-" + prefix + "01");
        assertThat(page.getByTestId("category-page-info")).hasText("Menampilkan 1 - 20 dari 21 kategori");

        page.locator("#category-table a:text-is('Selanjutnya')").click();
        waitForPageLoad();
        assertThat(page.locator("[data-testid^='category-row-']")).hasCount(1);
        assertThat(page.getByTestId("category-row-" + prefix + "21")).isVisible();
        assertThat(page.getByTestId("category-page-info")).hasText("Menampilkan 21 - 21 dari 21 kategori");
    }

    // ==================== CREATE ====================

    @Test
    @DisplayName("Should create a sub-category with description from the form; only active categories are offered as parent")
    void shouldCreateCategoryWithParent() {
        ProductCategory parent = createCategory("PC" + uid(), "Induk Aktif", null, true);
        ProductCategory inactive = createCategory("PC" + uid(), "Induk Nonaktif", null, false);
        String code = "PC" + uid();
        loginAsAdmin();

        navigateTo("/products/categories");
        waitForPageLoad();
        page.locator("#btn-new-category").click();
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/products/categories/new");
        assertThat(page.locator("#page-title")).hasText("Kategori Produk Baru");
        assertThat(page.locator("#active")).isChecked();
        assertThat(page.locator("#parent")).hasValue("");
        assertThat(page.locator("#parent option[value='" + parent.getId() + "']"))
                .hasText(parent.getCode() + " - " + parent.getName());
        assertThat(page.locator("#parent option[value='" + inactive.getId() + "']")).hasCount(0);

        page.locator("#code").fill(code);
        page.locator("#name").fill("Sub Kategori " + code);
        page.locator("#description").fill("Kategori turunan untuk uji");
        page.locator("#parent").selectOption(parent.getId().toString());
        page.locator("#btn-save").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/products/categories");
        assertThat(page.getByTestId("flash-success")).hasText("Kategori produk berhasil ditambahkan");
        ProductCategory saved = categoryByCode(code);
        assertEquals("Sub Kategori " + code, saved.getName());
        assertEquals("Kategori turunan untuk uji", saved.getDescription());
        assertEquals(parent.getId(), saved.getParent().getId());
        assertTrue(saved.isActive());

        navigateTo("/products/categories?search=" + code);
        waitForPageLoad();
        assertThat(page.getByTestId("category-parent-" + code)).hasText("Induk Aktif");
        assertThat(page.getByTestId("category-status-" + code)).hasText("Aktif");
    }

    @Test
    @DisplayName("Should reject blank code/name, duplicate code and unknown parent without saving")
    void shouldRejectInvalidCategory() {
        ProductCategory existing = createCategory("PC" + uid(), "Kategori Lama", null, true);
        String code = "PC" + uid();
        loginAsAdmin();

        navigateTo("/products/categories/new");
        waitForPageLoad();
        page.locator("#btn-save").click();
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/products/categories/new");
        assertThat(page.getByTestId("error-code")).hasText("Kode kategori wajib diisi");
        assertThat(page.getByTestId("error-name")).hasText("Nama kategori wajib diisi");

        page.locator("#code").fill(existing.getCode());
        page.locator("#name").fill("Kategori Duplikat " + code);
        page.locator("#btn-save").click();
        waitForPageLoad();
        assertThat(page.getByTestId("error-code")).hasText("Kode kategori sudah digunakan: " + existing.getCode());
        assertThat(page.getByTestId("error-name")).hasCount(0);
        assertThat(page.locator("#name")).hasValue("Kategori Duplikat " + code);
        assertEquals("Kategori Lama", categoryRepository.findById(existing.getId()).orElseThrow().getName());

        APIResponse response = postForm("/products/categories/new", categoryForm(csrf(), code, "Anak Yatim")
                .set("parent", UUID.randomUUID().toString()));
        assertEquals(200, response.status());
        assertTrue(response.text().contains("Kategori induk tidak ditemukan"));
        assertTrue(categoryRepository.findByCode(code).isEmpty());
    }

    // ==================== EDIT ====================

    @Test
    @DisplayName("Should prefill the edit form and update code, name, description, parent and status in place")
    void shouldUpdateCategory() {
        ProductCategory oldParent = createCategory("PC" + uid(), "Induk Lama", null, true);
        ProductCategory newParent = createCategory("PC" + uid(), "Induk Baru", null, true);
        ProductCategory category = createCategory("PC" + uid(), "Kategori Ubah", oldParent, true);
        category.setDescription("Deskripsi lama");
        categoryRepository.save(category);
        String newCode = "PC" + uid();
        loginAsAdmin();

        navigateTo("/products/categories?search=" + category.getCode());
        waitForPageLoad();
        page.getByTestId("category-row-" + category.getCode()).locator("a:text-is('Edit')").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/products/categories/" + category.getId() + "/edit");
        assertThat(page.locator("#page-title")).hasText("Edit Kategori Produk");
        assertThat(page.locator("#code")).hasValue(category.getCode());
        assertThat(page.locator("#name")).hasValue("Kategori Ubah");
        assertThat(page.locator("#description")).hasValue("Deskripsi lama");
        assertThat(page.locator("#parent")).hasValue(oldParent.getId().toString());
        assertThat(page.locator("#parent option[value='" + category.getId() + "']")).hasCount(0);
        assertThat(page.locator("#active")).isChecked();

        page.locator("#code").fill(newCode);
        page.locator("#name").fill("Kategori Sudah Diubah");
        page.locator("#description").fill("Deskripsi baru");
        page.locator("#parent").selectOption(newParent.getId().toString());
        page.locator("#active").uncheck();
        page.locator("#btn-save").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/products/categories");
        assertThat(page.getByTestId("flash-success")).hasText("Kategori produk berhasil diubah");
        ProductCategory updated = categoryByCode(newCode);
        assertEquals(category.getId(), updated.getId());
        assertEquals("Kategori Sudah Diubah", updated.getName());
        assertEquals("Deskripsi baru", updated.getDescription());
        assertEquals(newParent.getId(), updated.getParent().getId());
        assertFalse(updated.isActive());

        navigateTo("/products/categories/" + category.getId() + "/edit");
        waitForPageLoad();
        page.locator("#parent").selectOption("");
        page.locator("#btn-save").click();
        waitForPageLoad();
        assertThat(page.getByTestId("flash-success")).hasText("Kategori produk berhasil diubah");
        assertNull(categoryByCode(newCode).getParent());
    }

    @Test
    @DisplayName("Should keep an inactive parent when editing a sub-category")
    void shouldKeepInactiveParentOnEdit() {
        ProductCategory parent = createCategory("PC" + uid(), "Induk Arsip", null, false);
        ProductCategory category = createCategory("PC" + uid(), "Anak Arsip", parent, true);
        loginAsAdmin();

        navigateTo("/products/categories/" + category.getId() + "/edit");
        waitForPageLoad();
        assertThat(page.locator("#parent")).hasValue(parent.getId().toString());
        page.locator("#name").fill("Anak Arsip Diubah");
        page.locator("#btn-save").click();
        waitForPageLoad();

        assertThat(page.getByTestId("flash-success")).hasText("Kategori produk berhasil diubah");
        ProductCategory updated = categoryByCode(category.getCode());
        assertEquals("Anak Arsip Diubah", updated.getName());
        assertEquals(parent.getId(), updated.getParent().getId());
    }

    @Test
    @DisplayName("Should reject changing the code to another category's code, keep the form on the edited category, and resubmit only it")
    void shouldRejectDuplicateCodeOnUpdate() {
        ProductCategory category = createCategory("PC" + uid(), "Kategori Asli", null, true);
        ProductCategory other = createCategory("PC" + uid(), "Kategori Lain", null, true);
        String fixedCode = "PC" + uid();
        loginAsAdmin();

        navigateTo("/products/categories/" + category.getId() + "/edit");
        waitForPageLoad();
        page.locator("#code").fill(other.getCode());
        page.locator("#name").fill("Kategori Asli Diubah");
        page.locator("#btn-save").click();
        waitForPageLoad();

        assertThat(page.getByTestId("error-code")).hasText("Kode kategori sudah digunakan: " + other.getCode());
        assertThat(page.locator("#page-title")).hasText("Edit Kategori Produk");
        assertThat(page.locator("section form[method='post']"))
                .hasAttribute("action", "/products/categories/" + category.getId() + "/edit");
        assertEquals(category.getCode(), categoryRepository.findById(category.getId()).orElseThrow().getCode());

        page.locator("#code").fill(fixedCode);
        page.locator("#btn-save").click();
        waitForPageLoad();

        assertThat(page.getByTestId("flash-success")).hasText("Kategori produk berhasil diubah");
        ProductCategory updated = categoryRepository.findById(category.getId()).orElseThrow();
        assertEquals(fixedCode, updated.getCode());
        assertEquals("Kategori Asli Diubah", updated.getName());
        ProductCategory untouched = categoryRepository.findById(other.getId()).orElseThrow();
        assertEquals(other.getCode(), untouched.getCode());
        assertEquals("Kategori Lain", untouched.getName());
    }

    @Test
    @DisplayName("Should reject making a category the child of its own sub-category")
    void shouldRejectCircularParent() {
        ProductCategory root = createCategory("PC" + uid(), "Akar", null, true);
        ProductCategory child = createCategory("PC" + uid(), "Cabang", root, true);
        ProductCategory grandchild = createCategory("PC" + uid(), "Ranting", child, true);
        loginAsAdmin();

        navigateTo("/products/categories/" + root.getId() + "/edit");
        waitForPageLoad();
        page.locator("#parent").selectOption(grandchild.getId().toString());
        page.locator("#btn-save").click();
        waitForPageLoad();

        assertThat(page.getByTestId("form-global-errors"))
                .hasText("Referensi sirkuler terdeteksi: kategori tidak dapat menjadi anak dari dirinya sendiri");
        assertThat(page.locator("section form[method='post']"))
                .hasAttribute("action", "/products/categories/" + root.getId() + "/edit");
        assertNull(categoryRepository.findById(root.getId()).orElseThrow().getParent());
    }

    // ==================== DELETE ====================

    @Test
    @DisplayName("Should keep the category when the confirm is dismissed and delete it when accepted")
    void shouldDeleteCategory() {
        ProductCategory category = createCategory("PC" + uid(), "Kategori Hapus", null, true);
        loginAsAdmin();

        navigateTo("/products/categories?search=" + category.getCode());
        waitForPageLoad();
        Locator deleteButton = page.getByTestId("category-row-" + category.getCode()).locator("form button");
        page.onceDialog(dialog -> {
            assertEquals("Hapus kategori ini?", dialog.message());
            dialog.dismiss();
        });
        deleteButton.click();
        waitForPageLoad();
        assertTrue(categoryRepository.findById(category.getId()).isPresent());

        page.onceDialog(dialog -> dialog.accept());
        deleteButton.click();
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/products/categories");
        assertThat(page.getByTestId("flash-success")).hasText("Kategori produk berhasil dihapus");
        assertThat(page.getByTestId("category-row-" + category.getCode())).hasCount(0);
        assertTrue(categoryRepository.findById(category.getId()).isEmpty());
    }

    @Test
    @DisplayName("Should refuse to delete a category that still has products or sub-categories")
    void shouldRefuseToDeleteCategoryInUse() {
        ProductCategory withProduct = createCategory("PC" + uid(), "Kategori Berproduk", null, true);
        createProduct(withProduct);
        ProductCategory withChild = createCategory("PC" + uid(), "Kategori Berinduk", null, true);
        createCategory("PC" + uid(), "Sub Kategori", withChild, true);
        loginAsAdmin();

        deleteFromList(withProduct);
        assertThat(page.getByTestId("flash-error")).hasText("Kategori tidak dapat dihapus karena masih memiliki 1 produk");
        assertThat(page.getByTestId("flash-success")).hasCount(0);
        assertTrue(categoryRepository.findById(withProduct.getId()).isPresent());

        deleteFromList(withChild);
        assertThat(page.getByTestId("flash-error")).hasText("Kategori tidak dapat dihapus karena masih memiliki sub-kategori");
        assertTrue(categoryRepository.findById(withChild.getId()).isPresent());
    }

    // ==================== PERMISSIONS ====================

    @Test
    @DisplayName("Should let a view-only user browse categories but not create, edit or delete them")
    void shouldDenyChangesWithoutProductPermissions() {
        ProductCategory category = createCategory("PC" + uid(), "Kategori Auditor", null, true);
        User auditor = createUser(Role.AUDITOR);
        login(auditor.getUsername(), "password");

        navigateTo("/products/categories?search=" + category.getCode());
        waitForPageLoad();
        Locator row = page.getByTestId("category-row-" + category.getCode());
        assertThat(row).isVisible();
        assertThat(row.locator("a:text-is('Edit')")).hasCount(0);
        assertThat(row.locator("form")).hasCount(0);
        assertThat(page.locator("#btn-new-category")).hasCount(0);
        String csrf = csrf();

        for (String path : List.of("/products/categories/new", "/products/categories/" + category.getId() + "/edit")) {
            navigateTo(path);
            waitForPageLoad();
            assertThat(page.locator("#access-denied-page")).isVisible();
        }

        String newCode = "PC" + uid();
        assertEquals(403, postForm("/products/categories/new", categoryForm(csrf, newCode, "Ilegal")).status());
        assertEquals(403, postForm("/products/categories/" + category.getId() + "/edit",
                categoryForm(csrf, category.getCode(), "Diubah Auditor")).status());
        assertEquals(403, postForm("/products/categories/" + category.getId() + "/delete",
                FormData.create().set("_csrf", csrf)).status());

        assertTrue(categoryRepository.findByCode(newCode).isEmpty());
        assertEquals("Kategori Auditor", categoryRepository.findById(category.getId()).orElseThrow().getName());
    }

    // ==================== helpers ====================

    private ProductCategory createCategory(String code, String name, ProductCategory parent, boolean active) {
        ProductCategory category = new ProductCategory();
        category.setCode(code);
        category.setName(name);
        category.setParent(parent);
        category.setActive(active);
        return categoryRepository.save(category);
    }

    private void createProduct(ProductCategory category) {
        Product product = new Product();
        product.setCode("PRD-" + uid());
        product.setName("Produk " + category.getName());
        product.setUnit("pcs");
        product.setCostingMethod(CostingMethod.WEIGHTED_AVERAGE);
        product.setCategory(category);
        productRepository.save(product);
    }

    private ProductCategory categoryByCode(String code) {
        return categoryRepository.findByCode(code)
                .orElseThrow(() -> new AssertionError("Category not saved: " + code));
    }

    private void deleteFromList(ProductCategory category) {
        navigateTo("/products/categories?search=" + category.getCode());
        waitForPageLoad();
        page.onceDialog(dialog -> dialog.accept());
        page.getByTestId("category-row-" + category.getCode()).locator("form button").click();
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/products/categories");
    }

    private FormData categoryForm(String csrf, String code, String name) {
        return FormData.create().set("_csrf", csrf).set("code", code).set("name", name).set("active", "true");
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
        user.setUsername("cat-user-" + id);
        user.setPassword(passwordEncoder.encode("password"));
        user.setFullName("Pengguna " + id);
        user.setEmail("cat-user-" + id + "@example.com");
        user.setActive(true);
        user.addRole(role, "system");
        return userRepository.save(user);
    }

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
