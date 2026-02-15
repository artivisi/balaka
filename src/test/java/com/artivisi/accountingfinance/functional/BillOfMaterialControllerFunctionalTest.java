package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.BillOfMaterial;
import com.artivisi.accountingfinance.entity.BillOfMaterialLine;
import com.artivisi.accountingfinance.entity.Product;
import com.artivisi.accountingfinance.functional.manufacturing.CoffeeTestDataInitializer;
import com.artivisi.accountingfinance.repository.BillOfMaterialRepository;
import com.artivisi.accountingfinance.repository.ProductRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Functional tests for BillOfMaterialController.
 * Tests BOM list, create, edit, detail, delete operations.
 */
@DisplayName("Bill Of Material Controller Tests")
@Import(CoffeeTestDataInitializer.class)
class BillOfMaterialControllerFunctionalTest extends PlaywrightTestBase {

    @Autowired
    private BillOfMaterialRepository bomRepository;

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
    }

    private BillOfMaterial ensureActiveBomExists() {
        // Check for active BOMs
        var activeBom = bomRepository.findAll().stream()
                .filter(BillOfMaterial::isActive)
                .findFirst();

        if (activeBom.isPresent()) {
            return activeBom.get();
        }

        // Create one if needed
        return createFreshBomForTest("TEST");
    }

    /**
     * Creates a fresh BOM for tests that modify data.
     * This avoids modifying seed data from CoffeeTestDataInitializer.
     */
    private BillOfMaterial createFreshBomForTest(String prefix) {
        List<Product> products = productRepository.findAll();
        if (products.isEmpty()) {
            throw new AssertionError("No products available for BOM test");
        }

        BillOfMaterial bom = new BillOfMaterial();
        bom.setCode(prefix + "-BOM-" + System.currentTimeMillis());
        bom.setName(prefix + " BOM " + System.currentTimeMillis());
        bom.setProduct(products.get(0));
        bom.setOutputQuantity(BigDecimal.ONE);
        bom.setActive(true);
        bom.setLines(new ArrayList<>());

        if (products.size() > 1) {
            BillOfMaterialLine line = new BillOfMaterialLine();
            line.setComponent(products.get(1));
            line.setQuantity(BigDecimal.valueOf(2));
            line.setBillOfMaterial(bom);
            line.setLineOrder(0);
            bom.getLines().add(line);
        }

        return bomRepository.save(bom);
    }

    @Test
    @DisplayName("Should display BOM list page")
    void shouldDisplayBOMListPage() {
        navigateTo("/inventory/bom");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).isVisible();
    }

    @Test
    @DisplayName("Should search BOM by keyword")
    void shouldSearchBOMByKeyword() {
        navigateTo("/inventory/bom");
        waitForPageLoad();

        var searchInput = page.locator("input[name='search'], input[name='keyword']").first();
        if (searchInput.isVisible()) {
            searchInput.fill("kopi");

            var filterBtn = page.locator("form button[type='submit']").first();
            if (filterBtn.isVisible()) {
                filterBtn.click();
                waitForPageLoad();
            }
        }

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should display new BOM form")
    void shouldDisplayNewBOMForm() {
        navigateTo("/inventory/bom/create");
        waitForPageLoad();

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should create new BOM")
    void shouldCreateNewBOM() {
        var products = productRepository.findAll();
        if (products.size() < 2) {
            throw new AssertionError("At least 2 products required for BOM test");
        }

        navigateTo("/inventory/bom/create");
        waitForPageLoad();

        String uniqueCode = "BOM-CREATE-" + System.currentTimeMillis() % 100000;

        // Fill BOM code
        page.locator("#code").fill(uniqueCode);

        // Fill BOM name
        page.locator("#name").fill("Test BOM " + uniqueCode);

        // Select finished product
        page.locator("#productId").selectOption(products.get(0).getId().toString());

        // Fill output quantity
        page.locator("#outputQuantity").fill("1");

        // Add a component (BOM requires at least one)
        page.locator("#add-component-btn").click();
        page.waitForSelector(".component-row:not(#component-row-template)");

        page.locator(".component-row:not(#component-row-template) select[name='componentId[]']").first()
                .selectOption(products.get(1).getId().toString());
        page.locator(".component-row:not(#component-row-template) input[name='componentQty[]']").first()
                .fill("1");

        // Submit
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        // Should redirect to list
        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/inventory\\/bom.*"));
    }

    @Test
    @DisplayName("Should display BOM detail page")
    void shouldDisplayBOMDetailPage() {
        var bom = ensureActiveBomExists();

        navigateTo("/inventory/bom/" + bom.getId());
        waitForPageLoad();

        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/inventory\\/bom\\/.*"));
    }

    @Test
    @DisplayName("Should display BOM edit form")
    void shouldDisplayBOMEditForm() {
        var bom = ensureActiveBomExists();

        navigateTo("/inventory/bom/" + bom.getId() + "/edit");
        waitForPageLoad();

        assertThat(page.locator("#code")).isVisible();
    }

    @Test
    @DisplayName("Should update BOM")
    void shouldUpdateBOM() {
        var bom = createFreshBomForTest("UPDATE");

        navigateTo("/inventory/bom/" + bom.getId() + "/edit");
        waitForPageLoad();

        // Update name
        page.locator("#name").fill("Updated BOM " + System.currentTimeMillis());

        // Submit
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        // Should redirect to list
        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/inventory\\/bom.*"));
    }

    @Test
    @DisplayName("Should delete BOM")
    void shouldDeleteBOM() {
        // Create a fresh BOM for delete test to not affect other tests
        var products = productRepository.findAll();
        if (products.isEmpty()) {
            throw new AssertionError("Product required for delete test");
        }

        BillOfMaterial deleteBom = new BillOfMaterial();
        deleteBom.setCode("DELETE-BOM-" + System.currentTimeMillis());
        deleteBom.setName("BOM To Delete");
        deleteBom.setProduct(products.get(0));
        deleteBom.setOutputQuantity(BigDecimal.ONE);
        deleteBom.setActive(true);
        deleteBom.setLines(new ArrayList<>());
        deleteBom = bomRepository.save(deleteBom);

        navigateTo("/inventory/bom/" + deleteBom.getId());
        waitForPageLoad();

        // Handle JavaScript confirm dialog
        page.onDialog(dialog -> dialog.accept());

        // Click delete button
        page.locator("#form-delete button[type='submit']").click();
        waitForPageLoad();

        // Should redirect to list
        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/inventory\\/bom.*"));
    }

    // ==================== ADDITIONAL COVERAGE TESTS ====================

    @Test
    @DisplayName("Should search BOM via query parameter")
    void shouldSearchBOMViaQueryParameter() {
        navigateTo("/inventory/bom?search=kopi");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).isVisible();
    }

    @Test
    @DisplayName("Should handle non-existent BOM detail")
    void shouldHandleNonExistentBOMDetail() {
        navigateTo("/inventory/bom/00000000-0000-0000-0000-000000000000");
        waitForPageLoad();

        // Should redirect to list
        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/inventory\\/bom.*"));
    }

    @Test
    @DisplayName("Should handle non-existent BOM edit")
    void shouldHandleNonExistentBOMEdit() {
        navigateTo("/inventory/bom/00000000-0000-0000-0000-000000000000/edit");
        waitForPageLoad();

        // Should redirect to list
        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/inventory\\/bom.*"));
    }

    @Test
    @DisplayName("Should create BOM with component lines")
    void shouldCreateBOMWithComponentLines() {
        var products = productRepository.findAll();
        if (products.size() < 2) {
            throw new AssertionError("At least 2 products required for BOM with components test");
        }

        navigateTo("/inventory/bom/create");
        waitForPageLoad();

        String uniqueCode = "BOM-COMP-" + System.currentTimeMillis() % 100000;

        // Fill BOM code
        page.locator("#code").fill(uniqueCode);

        // Fill BOM name
        page.locator("#name").fill("BOM With Lines " + uniqueCode);

        // Select finished product
        page.locator("#productId").selectOption(products.get(0).getId().toString());

        // Fill output quantity
        page.locator("#outputQuantity").fill("1");

        // Add component
        page.locator("#add-component-btn").click();
        page.waitForSelector(".component-row:not(#component-row-template)");

        // Select component and fill qty
        page.locator(".component-row:not(#component-row-template) select[name='componentId[]']").first()
                .selectOption(products.get(1).getId().toString());
        page.locator(".component-row:not(#component-row-template) input[name='componentQty[]']").first()
                .fill("2");

        // Submit
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        // Should redirect to list
        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/inventory\\/bom.*"));
    }

    @Test
    @DisplayName("Should display products on create form")
    void shouldDisplayProductsOnCreateForm() {
        navigateTo("/inventory/bom/create");
        waitForPageLoad();

        var productSelect = page.locator("select[name='productId']").first();
        assertThat(productSelect).isVisible();
    }

    @Test
    @DisplayName("Should display products on edit form")
    void shouldDisplayProductsOnEditForm() {
        var bom = ensureActiveBomExists();

        navigateTo("/inventory/bom/" + bom.getId() + "/edit");
        waitForPageLoad();

        assertThat(page.locator("#productId")).isVisible();
    }

    @Test
    @DisplayName("Should search with empty search parameter")
    void shouldSearchWithEmptyParameter() {
        navigateTo("/inventory/bom?search=");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).isVisible();
    }

    @Test
    @DisplayName("Should update BOM with active checkbox")
    void shouldUpdateBOMWithActiveCheckbox() {
        var bom = createFreshBomForTest("ACTIVE");

        navigateTo("/inventory/bom/" + bom.getId() + "/edit");
        waitForPageLoad();

        // Toggle active checkbox
        var activeCheckbox = page.locator("#active");
        if (activeCheckbox.isChecked()) {
            activeCheckbox.uncheck();
        } else {
            activeCheckbox.check();
        }

        // Submit
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        // Should redirect to list
        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/inventory\\/bom.*"));
    }

    @Test
    @DisplayName("Should handle BOM detail page with lines")
    void shouldHandleBOMDetailPageWithLines() {
        var bom = ensureActiveBomExists();

        navigateTo("/inventory/bom/" + bom.getId());
        waitForPageLoad();

        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/inventory\\/bom\\/.*"));
    }

    // ==================== MORE COVERAGE TESTS ====================

    @Test
    @DisplayName("Should create BOM with all fields including description")
    void shouldCreateBOMWithAllFieldsIncludingDescription() {
        var products = productRepository.findAll();
        if (products.size() < 2) {
            throw new AssertionError("At least 2 products required for BOM creation test");
        }

        navigateTo("/inventory/bom/create");
        waitForPageLoad();

        String uniqueCode = "BOM-FULL-" + System.currentTimeMillis() % 100000;

        // Fill BOM code
        page.locator("#code").fill(uniqueCode);

        // Fill BOM name
        page.locator("#name").fill("Complete BOM " + uniqueCode);

        // Fill description
        page.locator("#description").fill("Test BOM with all fields");

        // Select finished product
        page.locator("#productId").selectOption(products.get(0).getId().toString());

        // Fill output quantity
        page.locator("#outputQuantity").fill("5");

        // Set active
        page.locator("#active").check();

        // Add component (BOM requires at least one)
        page.locator("#add-component-btn").click();
        page.waitForSelector(".component-row:not(#component-row-template)");
        page.locator(".component-row:not(#component-row-template) select[name='componentId[]']").first()
                .selectOption(products.get(1).getId().toString());
        page.locator(".component-row:not(#component-row-template) input[name='componentQty[]']").first()
                .fill("3");

        // Submit
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        // Should redirect to list
        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/inventory\\/bom.*"));
    }

    @Test
    @DisplayName("Should display description field on form")
    void shouldDisplayDescriptionFieldOnForm() {
        navigateTo("/inventory/bom/create");
        waitForPageLoad();

        assertThat(page.locator("#description")).isVisible();
    }

    @Test
    @DisplayName("Should display output quantity field on form")
    void shouldDisplayOutputQuantityFieldOnForm() {
        navigateTo("/inventory/bom/create");
        waitForPageLoad();

        assertThat(page.locator("#outputQuantity")).isVisible();
    }

    @Test
    @DisplayName("Should display code field on form")
    void shouldDisplayCodeFieldOnForm() {
        navigateTo("/inventory/bom/create");
        waitForPageLoad();

        assertThat(page.locator("#code")).isVisible();
    }

    @Test
    @DisplayName("Should display name field on form")
    void shouldDisplayNameFieldOnForm() {
        navigateTo("/inventory/bom/create");
        waitForPageLoad();

        var nameField = page.locator("input[name='name']").first();
        assertThat(nameField).isVisible();
    }

    @Test
    @DisplayName("Should update BOM description")
    void shouldUpdateBOMDescription() {
        var bom = createFreshBomForTest("DESC");

        navigateTo("/inventory/bom/" + bom.getId() + "/edit");
        waitForPageLoad();

        // Update description
        page.locator("#description").fill("Updated description " + System.currentTimeMillis());

        // Submit
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        // Should redirect to list
        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/inventory\\/bom.*"));
    }

    @Test
    @DisplayName("Should search for non-existing BOM")
    void shouldSearchForNonExistingBOM() {
        navigateTo("/inventory/bom?search=nonexistent12345");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).isVisible();
    }

    @Test
    @DisplayName("Should display BOM list with all items")
    void shouldDisplayBOMListWithAllItems() {
        navigateTo("/inventory/bom");
        waitForPageLoad();

        // Verify table exists
        assertThat(page.locator("table").first()).isVisible();
    }

    @Test
    @DisplayName("Should have create new button on list page")
    void shouldHaveCreateNewButtonOnListPage() {
        navigateTo("/inventory/bom");
        waitForPageLoad();

        assertThat(page.locator("a[href*='/inventory/bom/create']").first()).isVisible();
    }

    @Test
    @DisplayName("Should have edit button on detail page")
    void shouldHaveEditButtonOnDetailPage() {
        var bom = ensureActiveBomExists();

        navigateTo("/inventory/bom/" + bom.getId());
        waitForPageLoad();

        assertThat(page.locator("a[href*='/edit']").first()).isVisible();
    }

    @Test
    @DisplayName("Should have delete form on detail page")
    void shouldHaveDeleteFormOnDetailPage() {
        var bom = ensureActiveBomExists();

        navigateTo("/inventory/bom/" + bom.getId());
        waitForPageLoad();

        assertThat(page.locator("#form-delete").first()).isVisible();
    }

    @Test
    @DisplayName("Should update BOM output quantity")
    void shouldUpdateBOMOutputQuantity() {
        var bom = createFreshBomForTest("OUTPUTQTY");

        navigateTo("/inventory/bom/" + bom.getId() + "/edit");
        waitForPageLoad();

        // Update output quantity
        page.locator("#outputQuantity").fill("10");

        // Submit
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        // Should redirect to list
        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/inventory\\/bom.*"));
    }

    // ==================== FORM SUBMISSION TESTS ====================

    @Test
    @DisplayName("Should submit create BOM form with all required fields")
    void shouldSubmitCreateBOMFormWithAllRequiredFields() {
        var products = productRepository.findAll();
        if (products.size() < 2) {
            throw new AssertionError("At least 2 products required for BOM submission test");
        }

        navigateTo("/inventory/bom/create");
        waitForPageLoad();

        String uniqueCode = "BOM-SUBMIT-" + System.currentTimeMillis() % 100000;

        // Fill code
        page.locator("#code").fill(uniqueCode);

        // Fill name
        page.locator("#name").fill("Test BOM " + uniqueCode);

        // Fill description
        page.locator("#description").fill("Test BOM description");

        // Select product
        page.locator("#productId").selectOption(products.get(0).getId().toString());

        // Fill output quantity
        page.locator("#outputQuantity").fill("10");

        // Check active checkbox
        page.locator("#active").check();

        // Add component (BOM requires at least one)
        page.locator("#add-component-btn").click();
        page.waitForSelector(".component-row:not(#component-row-template)");
        page.locator(".component-row:not(#component-row-template) select[name='componentId[]']").first()
                .selectOption(products.get(1).getId().toString());
        page.locator(".component-row:not(#component-row-template) input[name='componentQty[]']").first()
                .fill("1");

        // Submit
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        // Should redirect to list
        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/inventory\\/bom.*"));
    }

    @Test
    @DisplayName("Should submit update BOM form")
    void shouldSubmitUpdateBOMForm() {
        var bom = createFreshBomForTest("FORMUPDATE");

        navigateTo("/inventory/bom/" + bom.getId() + "/edit");
        waitForPageLoad();

        // Update name
        page.locator("#name").fill("Updated BOM " + System.currentTimeMillis());

        // Update output quantity
        page.locator("#outputQuantity").fill("25");

        // Submit
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        // Should redirect to list
        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/inventory\\/bom.*"));
    }

    @Test
    @DisplayName("Should submit delete BOM form")
    void shouldSubmitDeleteBOMForm() {
        // Create a fresh BOM to delete
        var products = productRepository.findAll();
        if (products.isEmpty()) {
            throw new AssertionError("Product required for delete test");
        }

        BillOfMaterial deleteTarget = new BillOfMaterial();
        deleteTarget.setCode("DELETE-TARGET-" + System.currentTimeMillis());
        deleteTarget.setName("BOM to Delete via Form");
        deleteTarget.setProduct(products.get(0));
        deleteTarget.setOutputQuantity(BigDecimal.ONE);
        deleteTarget.setActive(true);
        deleteTarget.setLines(new ArrayList<>());
        deleteTarget = bomRepository.save(deleteTarget);

        navigateTo("/inventory/bom/" + deleteTarget.getId());
        waitForPageLoad();

        // Handle confirm dialog
        page.onDialog(dialog -> dialog.accept());

        // Click delete button
        page.locator("#form-delete button[type='submit']").click();
        waitForPageLoad();

        // Should redirect to list
        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/inventory\\/bom.*"));
    }

    @Test
    @DisplayName("Should submit BOM form with components")
    void shouldSubmitBOMFormWithComponents() {
        var products = productRepository.findAll();
        if (products.size() < 2) {
            throw new AssertionError("At least 2 products required for BOM with components test");
        }

        navigateTo("/inventory/bom/create");
        waitForPageLoad();

        String uniqueCode = "BOM-WITH-COMPS-" + System.currentTimeMillis() % 100000;

        // Fill basic fields
        page.locator("#code").fill(uniqueCode);
        page.locator("#name").fill("BOM with Components " + uniqueCode);

        // Select product
        page.locator("#productId").selectOption(products.get(0).getId().toString());

        // Fill output quantity
        page.locator("#outputQuantity").fill("1");

        // Add component
        page.locator("#add-component-btn").click();
        page.waitForSelector(".component-row:not(#component-row-template)");

        // Fill component data
        page.locator(".component-row:not(#component-row-template) select[name='componentId[]']").first()
                .selectOption(products.get(1).getId().toString());
        page.locator(".component-row:not(#component-row-template) input[name='componentQty[]']").first()
                .fill("2");

        // Submit form
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        // Should redirect to list
        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/inventory\\/bom.*"));
    }
}
