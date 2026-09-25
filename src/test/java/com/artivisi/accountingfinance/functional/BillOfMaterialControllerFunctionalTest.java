package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.BillOfMaterial;
import com.artivisi.accountingfinance.entity.BillOfMaterialLine;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.CostingMethod;
import com.artivisi.accountingfinance.entity.Product;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.functional.manufacturing.CoffeeTestDataInitializer;
import com.artivisi.accountingfinance.repository.BillOfMaterialRepository;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.ProductRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.service.BillOfMaterialService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BillOfMaterialController: list/search of active BOMs, detail, create/edit form with component lines,
 * validation, deactivation and permissions.
 *
 * Seeded coffee-shop BOMs (BOM-CRS, BOM-RBC) feed the manufacturing tests, so they are only read here;
 * every change is made on products and BOMs this class creates (unique codes).
 */
@DisplayName("Bill Of Material Controller Tests")
@Import(CoffeeTestDataInitializer.class)
class BillOfMaterialControllerFunctionalTest extends PlaywrightTestBase {

    private static final String NEW_ROW = ".component-row:not(#component-row-template)";

    @Autowired
    private BillOfMaterialRepository bomRepository;

    @Autowired
    private BillOfMaterialService bomService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ChartOfAccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ==================== LIST & SEARCH ====================

    @Test
    @DisplayName("Should list active BOMs with code, name, product, output and status, hide inactive ones, and link from the sidebar")
    void shouldListActiveBoms() {
        Fixture f = fixture();
        BillOfMaterial bom = createBom(f, "LIST");
        BillOfMaterial inactive = createBom(f, "INACTIVE");
        bomService.delete(inactive.getId());
        loginAsAdmin();

        navigateTo("/dashboard");
        waitForPageLoad();
        assertThat(page.locator("#nav-bom")).hasAttribute("href", "/inventory/bom");
        page.locator("#nav-group-inventori").click();
        page.locator("#nav-bom").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/inventory/bom");
        assertThat(page.locator("#page-title")).hasText("Bill of Materials");
        assertThat(page.locator("a[href='/inventory/bom/create']")).hasText("Tambah BOM");
        Locator row = page.locator("[data-testid='bom-row-" + bom.getCode() + "']");
        assertThat(row.locator("td")).hasText(new String[]{
                bom.getCode(), bom.getName(), f.finished.getName(), "4.00 pcs", "Aktif", "Detail Edit"});
        assertThat(row.locator("a:text-is('Detail')")).hasAttribute("href", "/inventory/bom/" + bom.getId());
        assertThat(row.locator("a:text-is('Edit')")).hasAttribute("href", "/inventory/bom/" + bom.getId() + "/edit");
        assertThat(page.locator("[data-testid='bom-row-" + inactive.getCode() + "']")).hasCount(0);
        assertThat(page.locator("[data-testid='bom-product-BOM-CRS']")).hasText("Croissant");
    }

    @Test
    @DisplayName("Should search active BOMs by code, name or finished product name, case-insensitively")
    void shouldSearchBoms() {
        Fixture f = fixture();
        BillOfMaterial bom = createBom(f, "SEARCH");
        loginAsAdmin();

        navigateTo("/inventory/bom");
        waitForPageLoad();
        page.locator("#bom-search").fill(bom.getCode().toLowerCase());
        page.locator("form[action='/inventory/bom'] button[type='submit']").click();
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/inventory/bom?search=" + bom.getCode().toLowerCase());
        assertThat(page.locator("#bom-search")).hasValue(bom.getCode().toLowerCase());
        assertThat(page.locator("tr[data-testid^='bom-row-']")).hasCount(1);
        assertThat(page.locator("[data-testid='bom-row-" + bom.getCode() + "']")).isVisible();

        navigateTo("/inventory/bom?search=" + bom.getName().toUpperCase().replace(" ", "+"));
        waitForPageLoad();
        assertThat(page.locator("tr[data-testid^='bom-row-']")).hasCount(1);
        assertThat(page.locator("[data-testid='bom-row-" + bom.getCode() + "']")).isVisible();

        navigateTo("/inventory/bom?search=" + f.finished.getName().replace(" ", "+"));
        waitForPageLoad();
        assertThat(page.locator("tr[data-testid^='bom-row-']")).hasCount(1);
        assertThat(page.locator("[data-testid='bom-row-" + bom.getCode() + "']")).isVisible();

        navigateTo("/inventory/bom?search=croissant");
        waitForPageLoad();
        assertThat(page.locator("[data-testid='bom-row-BOM-CRS']")).isVisible();
        assertThat(page.locator("[data-testid='bom-row-" + bom.getCode() + "']")).hasCount(0);

        navigateTo("/inventory/bom?search=tidak-ada-" + uid());
        waitForPageLoad();
        assertThat(page.locator("tr[data-testid^='bom-row-']")).hasCount(0);
        assertThat(page.locator("td:text-is('Tidak ada data BOM')")).isVisible();

        navigateTo("/inventory/bom?search=");
        waitForPageLoad();
        assertThat(page.locator("[data-testid='bom-row-" + bom.getCode() + "']")).isVisible();
        assertThat(page.locator("[data-testid='bom-row-BOM-CRS']")).isVisible();
    }

    // ==================== DETAIL ====================

    @Test
    @DisplayName("Should show BOM detail with header fields, ordered component lines and actions")
    void shouldShowBomDetail() {
        Fixture f = fixture();
        BillOfMaterial bom = createBom(f, "DETAIL");
        loginAsAdmin();

        navigateTo("/inventory/bom");
        waitForPageLoad();
        page.locator("[data-testid='bom-detail-link-" + bom.getCode() + "']").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/inventory/bom/" + bom.getId());
        assertThat(page.locator("#page-title")).hasText("Detail BOM: " + bom.getCode());
        assertThat(page.getByTestId("bom-code")).hasText(bom.getCode());
        assertThat(page.getByTestId("bom-product")).hasText(f.finished.getCode() + " - " + f.finished.getName());
        assertThat(page.getByTestId("bom-output-quantity")).hasText("4.00 pcs");
        assertThat(page.locator("dt:text-is('Nama BOM') + dd")).hasText(bom.getName());
        assertThat(page.locator("dt:text-is('Deskripsi') + dd")).hasText("Resep " + bom.getCode());
        assertThat(componentRow(f.flour).locator("td")).hasText(new String[]{
                "1", f.flour.getCode(), f.flour.getName(), "2.50", "kg", "Ayak dulu"});
        assertThat(componentRow(f.butter).locator("td")).hasText(new String[]{
                "2", f.butter.getCode(), f.butter.getName(), "1.00", "kg", "-"});
        assertThat(page.locator("a:text-is('Edit')")).hasAttribute("href", "/inventory/bom/" + bom.getId() + "/edit");
        assertThat(page.locator("#form-delete button")).hasText("Nonaktifkan");
    }

    @Test
    @DisplayName("Should redirect to the list with an error for an unknown BOM id on detail and edit")
    void shouldRejectUnknownBom() {
        loginAsAdmin();
        UUID unknown = UUID.randomUUID();

        navigateTo("/inventory/bom/" + unknown);
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/inventory/bom");
        assertThat(page.getByTestId("flash-error")).hasText("BOM tidak ditemukan");

        navigateTo("/inventory/bom/" + unknown + "/edit");
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/inventory/bom");
        assertThat(page.getByTestId("flash-error")).hasText("BOM tidak ditemukan");
    }

    // ==================== CREATE ====================

    @Test
    @DisplayName("Should offer active products on the new form and add/remove component rows")
    void shouldShowNewForm() {
        Fixture f = fixture();
        Product inactiveProduct = createProduct("NONAKTIF", "pcs", "1.1.20", CostingMethod.WEIGHTED_AVERAGE);
        inactiveProduct.setActive(false);
        productRepository.save(inactiveProduct);
        loginAsAdmin();

        navigateTo("/inventory/bom");
        waitForPageLoad();
        page.locator("a[href='/inventory/bom/create']").click();
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Tambah Bill of Materials");
        assertThat(page.locator("#code")).hasValue("");
        assertThat(page.locator("#name")).hasValue("");
        assertThat(page.locator("#outputQuantity")).hasValue("1");
        assertThat(page.locator("#active")).isChecked();
        assertThat(page.locator("#productId option[value='" + f.finished.getId() + "']"))
                .hasText(f.finished.getCode() + " - " + f.finished.getName());
        assertThat(page.locator("#productId option[value='" + inactiveProduct.getId() + "']")).hasCount(0);
        assertThat(page.locator(NEW_ROW)).hasCount(0);
        assertThat(page.locator("#no-components-msg")).isVisible();

        page.locator("#add-component-btn").click();
        page.locator("#add-component-btn").click();
        assertThat(page.locator(NEW_ROW)).hasCount(2);
        assertThat(page.locator("#no-components-msg")).isHidden();
        assertThat(page.locator(NEW_ROW + " select[name='componentId[]'] option[value='" + f.flour.getId() + "']"))
                .hasCount(2);
        assertThat(page.locator(NEW_ROW + " input[name='componentQty[]']").first()).hasValue("1");

        page.locator(NEW_ROW + " .remove-line-btn").first().click();
        page.locator(NEW_ROW + " .remove-line-btn").first().click();
        assertThat(page.locator(NEW_ROW)).hasCount(0);
        assertThat(page.locator("#no-components-msg")).isVisible();
    }

    @Test
    @DisplayName("Should create a BOM with two component lines through the form and persist every field")
    void shouldCreateBomWithComponents() {
        Fixture f = fixture();
        String code = "BOM-C-" + uid();
        loginAsAdmin();

        navigateTo("/inventory/bom/create");
        waitForPageLoad();
        page.locator("#code").fill(code);
        page.locator("#name").fill("Resep Uji " + code);
        page.locator("#description").fill("Batch uji " + code);
        page.locator("#productId").selectOption(f.finished.getId().toString());
        page.locator("#outputQuantity").fill("12");
        addComponent(0, f.flour, "3.5", "Ayak dulu, jangan menggumpal");
        addComponent(1, f.butter, "0.75", "");
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/inventory/bom");
        assertThat(page.getByTestId("flash-success")).hasText("BOM berhasil dibuat");
        assertThat(page.locator("[data-testid='bom-output-" + code + "']")).hasText("12.00 pcs");

        BillOfMaterial saved = bomByCode(code);
        assertEquals("Resep Uji " + code, saved.getName());
        assertEquals("Batch uji " + code, saved.getDescription());
        assertEquals(f.finished.getId(), saved.getProduct().getId());
        assertEquals(0, new BigDecimal("12").compareTo(saved.getOutputQuantity()));
        assertTrue(saved.isActive());
        assertEquals(List.of(
                        f.flour.getCode() + "|3.5000|Ayak dulu, jangan menggumpal|0",
                        f.butter.getCode() + "|0.7500||1"),
                lines(saved));

        page.locator("[data-testid='bom-detail-link-" + code + "']").click();
        waitForPageLoad();
        assertThat(componentRow(f.flour).locator("td").nth(3)).hasText("3.50");
        assertThat(componentRow(f.flour).locator("td").nth(5)).hasText("Ayak dulu, jangan menggumpal");
        assertThat(componentRow(f.butter).locator("td").nth(3)).hasText("0.75");
    }

    @Test
    @DisplayName("Should keep a comma in the notes of a single-component BOM")
    void shouldKeepCommaInSingleComponentNotes() {
        Fixture f = fixture();
        String code = "BOM-N-" + uid();
        loginAsAdmin();

        navigateTo("/inventory/bom/create");
        waitForPageLoad();
        page.locator("#code").fill(code);
        page.locator("#name").fill("Resep " + code);
        page.locator("#productId").selectOption(f.finished.getId().toString());
        page.locator("#outputQuantity").fill("1");
        addComponent(0, f.flour, "2", "Ayak dulu, jangan menggumpal");
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        assertThat(page.getByTestId("flash-success")).hasText("BOM berhasil dibuat");
        assertEquals(List.of(f.flour.getCode() + "|2.0000|Ayak dulu, jangan menggumpal|0"), lines(bomByCode(code)));
    }

    @Test
    @DisplayName("Should reject a BOM without components, with a duplicate code, or using the finished product as component")
    void shouldRejectInvalidBom() {
        Fixture f = fixture();
        BillOfMaterial existing = createBom(f, "DUP");
        loginAsAdmin();

        String noComponents = "BOM-X-" + uid();
        fillHeader(noComponents, f.finished);
        page.locator("#btn-simpan").click();
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/inventory/bom/create");
        assertThat(page.getByTestId("flash-error")).hasText("BOM harus memiliki minimal 1 komponen");
        assertTrue(bomRepository.findByCode(noComponents).isEmpty());

        fillHeader(existing.getCode(), f.finished);
        addComponent(0, f.flour, "1", "");
        page.locator("#btn-simpan").click();
        waitForPageLoad();
        assertThat(page.getByTestId("flash-error")).hasText("Kode BOM '" + existing.getCode() + "' sudah digunakan");
        assertEquals(existing.getName(), bomByCode(existing.getCode()).getName());

        String selfComponent = "BOM-S-" + uid();
        fillHeader(selfComponent, f.finished);
        addComponent(0, f.finished, "1", "");
        page.locator("#btn-simpan").click();
        waitForPageLoad();
        assertThat(page.getByTestId("flash-error")).hasText("Produk jadi tidak boleh menjadi komponen dari dirinya sendiri");
        assertTrue(bomRepository.findByCode(selfComponent).isEmpty());
    }

    // ==================== EDIT ====================

    @Test
    @DisplayName("Should prefill the edit form and update header fields and replace component lines")
    void shouldUpdateBom() {
        Fixture f = fixture();
        BillOfMaterial bom = createBom(f, "UPD");
        Product sugar = createProduct("GULA", "kg", "1.1.20", CostingMethod.WEIGHTED_AVERAGE);
        loginAsAdmin();

        navigateTo("/inventory/bom/" + bom.getId());
        waitForPageLoad();
        page.locator("a:text-is('Edit')").click();
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Edit Bill of Materials");
        assertThat(page.locator("input[name='id']")).hasValue(bom.getId().toString());
        assertThat(page.locator("#code")).hasValue(bom.getCode());
        assertThat(page.locator("#name")).hasValue(bom.getName());
        assertThat(page.locator("#description")).hasValue("Resep " + bom.getCode());
        assertThat(page.locator("#productId")).hasValue(f.finished.getId().toString());
        assertEquals(0, new BigDecimal("4").compareTo(new BigDecimal(page.locator("#outputQuantity").inputValue())));
        assertThat(page.locator("#active")).isChecked();
        Locator rows = page.locator(NEW_ROW);
        assertThat(rows).hasCount(2);
        assertThat(rows.nth(0).locator("select")).hasValue(f.flour.getId().toString());
        assertThat(rows.nth(0).locator("input[name='componentNotes[]']")).hasValue("Ayak dulu");
        assertThat(rows.nth(1).locator("select")).hasValue(f.butter.getId().toString());

        String newCode = bom.getCode() + "-V2";
        page.locator("#code").fill(newCode);
        page.locator("#name").fill("Resep Revisi " + bom.getCode());
        page.locator("#description").fill("Revisi takaran");
        page.locator("#outputQuantity").fill("6");
        rows.nth(0).locator("input[name='componentQty[]']").fill("3");
        rows.nth(1).locator(".remove-line-btn").click();
        addComponent(1, sugar, "0.5", "Gula halus");
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/inventory/bom");
        assertThat(page.getByTestId("flash-success")).hasText("BOM berhasil diperbarui");
        BillOfMaterial updated = bomRepository.findByIdWithLines(bom.getId()).orElseThrow();
        assertEquals(newCode, updated.getCode());
        assertEquals("Resep Revisi " + bom.getCode(), updated.getName());
        assertEquals("Revisi takaran", updated.getDescription());
        assertEquals(0, new BigDecimal("6").compareTo(updated.getOutputQuantity()));
        assertTrue(updated.isActive());
        assertEquals(List.of(
                        f.flour.getCode() + "|3.0000|Ayak dulu|0",
                        sugar.getCode() + "|0.5000|Gula halus|1"),
                lines(updated));
        assertTrue(bomRepository.findByCode(bom.getCode()).isEmpty());
    }

    @Test
    @DisplayName("Should reject changing the code to another BOM's code and keep the form bound to the edited BOM")
    void shouldRejectDuplicateCodeOnUpdate() {
        Fixture f = fixture();
        BillOfMaterial edited = createBom(f, "EDT");
        BillOfMaterial other = createBom(f, "OTH");
        loginAsAdmin();

        navigateTo("/inventory/bom/" + edited.getId() + "/edit");
        waitForPageLoad();
        page.locator("#code").fill(other.getCode());
        page.locator("#name").fill("Tidak tersimpan");
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/inventory/bom/" + edited.getId() + "/edit");
        assertThat(page.getByTestId("flash-error")).hasText("Kode BOM '" + other.getCode() + "' sudah digunakan");
        assertThat(page.locator("input[name='id']")).hasValue(edited.getId().toString());
        assertEquals(edited.getName(), bomRepository.findById(edited.getId()).orElseThrow().getName());
        assertEquals(other.getName(), bomRepository.findById(other.getId()).orElseThrow().getName());

        page.locator("#name").fill("Nama Baru " + edited.getCode());
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        assertThat(page.getByTestId("flash-success")).hasText("BOM berhasil diperbarui");
        BillOfMaterial reloaded = bomRepository.findById(edited.getId()).orElseThrow();
        assertEquals(edited.getCode(), reloaded.getCode());
        assertEquals("Nama Baru " + edited.getCode(), reloaded.getName());
        assertEquals(other.getName(), bomRepository.findById(other.getId()).orElseThrow().getName());
    }

    @Test
    @DisplayName("Should deactivate a BOM by unchecking Aktif on the edit form")
    void shouldDeactivateThroughEditForm() {
        Fixture f = fixture();
        BillOfMaterial bom = createBom(f, "OFF");
        loginAsAdmin();

        navigateTo("/inventory/bom/" + bom.getId() + "/edit");
        waitForPageLoad();
        page.locator("#active").uncheck();
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        assertThat(page.getByTestId("flash-success")).hasText("BOM berhasil diperbarui");
        assertThat(page.locator("[data-testid='bom-row-" + bom.getCode() + "']")).hasCount(0);
        assertFalse(bomRepository.findById(bom.getId()).orElseThrow().isActive());

        navigateTo("/inventory/bom/" + bom.getId());
        waitForPageLoad();
        assertThat(page.locator("span:text-is('Nonaktif')")).isVisible();
        assertThat(page.locator("a:text-is('Edit')")).hasCount(0);
        assertThat(page.locator("#form-delete")).hasCount(0);
    }

    // ==================== DEACTIVATE ====================

    @Test
    @DisplayName("Should keep the BOM when the confirm is dismissed and deactivate it (lines kept) when accepted")
    void shouldDeactivateBom() {
        Fixture f = fixture();
        BillOfMaterial bom = createBom(f, "DEL");
        loginAsAdmin();

        navigateTo("/inventory/bom/" + bom.getId());
        waitForPageLoad();
        page.onceDialog(dialog -> {
            assertEquals("Yakin ingin menonaktifkan BOM ini?", dialog.message());
            dialog.dismiss();
        });
        page.locator("#form-delete button").click();
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/inventory/bom/" + bom.getId());
        assertTrue(bomRepository.findById(bom.getId()).orElseThrow().isActive());

        page.onceDialog(dialog -> dialog.accept());
        page.locator("#form-delete button").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/inventory/bom");
        assertThat(page.getByTestId("flash-success")).hasText("BOM berhasil dinonaktifkan");
        assertThat(page.locator("[data-testid='bom-row-" + bom.getCode() + "']")).hasCount(0);
        BillOfMaterial reloaded = bomRepository.findByIdWithLines(bom.getId()).orElseThrow();
        assertFalse(reloaded.isActive());
        assertEquals(2, reloaded.getLines().size());
    }

    // ==================== PERMISSIONS ====================

    @Test
    @DisplayName("Should let a view-only user browse BOMs but not create, edit or deactivate them")
    void shouldDenyChangesWithoutProductPermissions() {
        Fixture f = fixture();
        BillOfMaterial bom = createBom(f, "AUD");
        User auditor = createUser(Role.AUDITOR);
        login(auditor.getUsername(), "password");

        navigateTo("/inventory/bom");
        waitForPageLoad();
        Locator row = page.locator("[data-testid='bom-row-" + bom.getCode() + "']");
        assertThat(row).isVisible();
        assertThat(row.locator("a:text-is('Edit')")).hasCount(0);
        assertThat(page.locator("a[href='/inventory/bom/create']")).hasCount(0);

        navigateTo("/inventory/bom/" + bom.getId());
        waitForPageLoad();
        assertThat(page.getByTestId("bom-code")).hasText(bom.getCode());
        assertThat(page.locator("a:text-is('Edit')")).hasCount(0);
        assertThat(page.locator("#form-delete")).hasCount(0);
        String csrf = csrf();

        for (String path : List.of("/inventory/bom/create", "/inventory/bom/" + bom.getId() + "/edit")) {
            navigateTo(path);
            waitForPageLoad();
            assertThat(page.locator("#access-denied-page")).isVisible();
        }

        String newCode = "BOM-ILG-" + uid();
        assertEquals(403, postForm("/inventory/bom/save", bomForm(csrf, null, newCode, f)).status());
        assertEquals(403, postForm("/inventory/bom/save", bomForm(csrf, bom.getId(), bom.getCode(), f)).status());
        assertEquals(403, postForm("/inventory/bom/" + bom.getId() + "/delete",
                FormData.create().set("_csrf", csrf)).status());

        assertTrue(bomRepository.findByCode(newCode).isEmpty());
        BillOfMaterial unchanged = bomRepository.findByIdWithLines(bom.getId()).orElseThrow();
        assertEquals(bom.getName(), unchanged.getName());
        assertTrue(unchanged.isActive());
        assertEquals(2, unchanged.getLines().size());
    }

    // ==================== helpers ====================

    private record Fixture(Product finished, Product flour, Product butter) {
    }

    private Fixture fixture() {
        return new Fixture(
                createProduct("ROTI", "pcs", "1.1.21", CostingMethod.WEIGHTED_AVERAGE),
                createProduct("TEPUNG", "kg", "1.1.20", CostingMethod.WEIGHTED_AVERAGE),
                createProduct("MENTEGA", "kg", "1.1.20", CostingMethod.WEIGHTED_AVERAGE));
    }

    private Product createProduct(String prefix, String unit, String inventoryAccountCode, CostingMethod costingMethod) {
        String id = uid();
        Product product = new Product();
        product.setCode(prefix + "-" + id.toUpperCase());
        product.setName(prefix.charAt(0) + prefix.substring(1).toLowerCase() + " Uji " + id);
        product.setUnit(unit);
        product.setCostingMethod(costingMethod);
        product.setInventoryAccount(account(inventoryAccountCode));
        return productRepository.save(product);
    }

    private BillOfMaterial createBom(Fixture f, String prefix) {
        String code = "BOM-" + prefix + "-" + uid();
        BillOfMaterial bom = new BillOfMaterial();
        bom.setCode(code);
        bom.setName("Resep " + prefix + " " + code);
        bom.setDescription("Resep " + code);
        bom.setProduct(f.finished);
        bom.setOutputQuantity(new BigDecimal("4"));
        bom.setActive(true);
        bom.setLines(new ArrayList<>());
        bom.getLines().add(line(f.flour, "2.5", "Ayak dulu", 0));
        bom.getLines().add(line(f.butter, "1", null, 1));
        return bomService.create(bom);
    }

    private BillOfMaterialLine line(Product component, String quantity, String notes, int order) {
        BillOfMaterialLine line = new BillOfMaterialLine();
        line.setComponent(component);
        line.setQuantity(new BigDecimal(quantity));
        line.setNotes(notes);
        line.setLineOrder(order);
        return line;
    }

    private ChartOfAccount account(String code) {
        return accountRepository.findByAccountCode(code)
                .orElseThrow(() -> new IllegalStateException("Seeded account missing: " + code));
    }

    private BillOfMaterial bomByCode(String code) {
        UUID id = bomRepository.findByCode(code)
                .orElseThrow(() -> new AssertionError("BOM not saved: " + code)).getId();
        return bomRepository.findByIdWithLines(id).orElseThrow();
    }

    private List<String> lines(BillOfMaterial bom) {
        return bom.getLines().stream()
                .map(l -> l.getComponent().getCode() + "|" + l.getQuantity().setScale(4) + "|"
                        + (l.getNotes() == null ? "" : l.getNotes()) + "|" + l.getLineOrder())
                .toList();
    }

    private void fillHeader(String code, Product finished) {
        navigateTo("/inventory/bom/create");
        waitForPageLoad();
        page.locator("#code").fill(code);
        page.locator("#name").fill("Resep " + code);
        page.locator("#productId").selectOption(finished.getId().toString());
        page.locator("#outputQuantity").fill("1");
    }

    private void addComponent(int index, Product component, String quantity, String notes) {
        page.locator("#add-component-btn").click();
        Locator row = page.locator(NEW_ROW).nth(index);
        row.locator("select[name='componentId[]']").selectOption(component.getId().toString());
        row.locator("input[name='componentQty[]']").fill(quantity);
        row.locator("input[name='componentNotes[]']").fill(notes);
    }

    private Locator componentRow(Product component) {
        return page.locator("[data-testid='bom-component-row-" + component.getCode() + "']");
    }

    private FormData bomForm(String csrf, UUID id, String code, Fixture f) {
        FormData form = FormData.create().set("_csrf", csrf).set("code", code).set("name", "Ilegal " + code)
                .set("productId", f.finished.getId().toString()).set("outputQuantity", "1").set("active", "true")
                .set("componentId[]", f.flour.getId().toString()).set("componentQty[]", "1");
        return id == null ? form : form.set("id", id.toString());
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
        user.setUsername("bom-user-" + id);
        user.setPassword(passwordEncoder.encode("password"));
        user.setFullName("Pengguna " + id);
        user.setEmail("bom-user-" + id + "@example.com");
        user.setActive(true);
        user.addRole(role, "system");
        return userRepository.save(user);
    }

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
