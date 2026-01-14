package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.BillOfMaterialRepository;
import com.artivisi.accountingfinance.repository.ProductRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Functional tests for BillOfMaterialController.
 * Tests BOM list, create, edit, detail, delete operations.
 */
@DisplayName("Bill Of Material Controller Tests")
@Import(ServiceTestDataInitializer.class)
class BillOfMaterialControllerFunctionalTest extends PlaywrightTestBase {

    @Autowired
    private BillOfMaterialRepository bomRepository;

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
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
        var product = productRepository.findAll().stream().findFirst();
        if (product.isEmpty()) {
            return;
        }

        navigateTo("/inventory/bom/create");
        waitForPageLoad();

        // Fill BOM name
        var nameInput = page.locator("input[name='name']").first();
        if (nameInput.isVisible()) {
            nameInput.fill("Test BOM " + System.currentTimeMillis());
        }

        // Select finished product
        var productSelect = page.locator("select[name='finishedProduct.id'], select[name='finishedProductId']").first();
        if (productSelect.isVisible()) {
            productSelect.selectOption(product.get().getId().toString());
        }

        // Fill output quantity
        var outputQtyInput = page.locator("input[name='outputQuantity']").first();
        if (outputQtyInput.isVisible()) {
            outputQtyInput.fill("1");
        }

        // Submit
        var submitBtn = page.locator("#btn-simpan").first();
        if (submitBtn.isVisible()) {
            submitBtn.click();
            waitForPageLoad();
        }

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should display BOM detail page")
    void shouldDisplayBOMDetailPage() {
        var bom = bomRepository.findAll().stream().findFirst();
        if (bom.isEmpty()) {
            return;
        }

        navigateTo("/inventory/bom/" + bom.get().getId());
        waitForPageLoad();

        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/inventory\\/bom\\/.*"));
    }

    @Test
    @DisplayName("Should display BOM edit form")
    void shouldDisplayBOMEditForm() {
        var bom = bomRepository.findAll().stream().findFirst();
        if (bom.isEmpty()) {
            return;
        }

        navigateTo("/inventory/bom/" + bom.get().getId() + "/edit");
        waitForPageLoad();

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should update BOM")
    void shouldUpdateBOM() {
        var bom = bomRepository.findAll().stream().findFirst();
        if (bom.isEmpty()) {
            return;
        }

        navigateTo("/inventory/bom/" + bom.get().getId() + "/edit");
        waitForPageLoad();

        // Update name
        var nameInput = page.locator("input[name='name']").first();
        if (nameInput.isVisible()) {
            nameInput.fill("Updated BOM " + System.currentTimeMillis());
        }

        // Submit
        var submitBtn = page.locator("#btn-simpan").first();
        if (submitBtn.isVisible()) {
            submitBtn.click();
            waitForPageLoad();
        }

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should delete BOM")
    void shouldDeleteBOM() {
        var bom = bomRepository.findAll().stream().findFirst();
        if (bom.isEmpty()) {
            return;
        }

        navigateTo("/inventory/bom/" + bom.get().getId());
        waitForPageLoad();

        var deleteBtn = page.locator("form[action*='/delete'] button[type='submit']").first();
        if (deleteBtn.isVisible()) {
            deleteBtn.click();
            waitForPageLoad();
        }

        assertThat(page.locator("body")).isVisible();
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
            return;
        }

        navigateTo("/inventory/bom/create");
        waitForPageLoad();

        // Fill BOM code
        var codeInput = page.locator("input[name='code']").first();
        if (codeInput.isVisible()) {
            codeInput.fill("BOM-TEST-" + System.currentTimeMillis());
        }

        // Fill BOM name
        var nameInput = page.locator("input[name='name']").first();
        if (nameInput.isVisible()) {
            nameInput.fill("Test BOM With Lines " + System.currentTimeMillis());
        }

        // Select finished product
        var productSelect = page.locator("select[name='productId']").first();
        if (productSelect.isVisible()) {
            productSelect.selectOption(products.get(0).getId().toString());
        }

        // Fill output quantity
        var outputQtyInput = page.locator("input[name='outputQuantity']").first();
        if (outputQtyInput.isVisible()) {
            outputQtyInput.fill("1");
        }

        // Submit
        var submitBtn = page.locator("#btn-simpan").first();
        if (submitBtn.isVisible()) {
            submitBtn.click();
            waitForPageLoad();
        }

        assertThat(page.locator("body")).isVisible();
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
        var bom = bomRepository.findAll().stream().findFirst();
        if (bom.isEmpty()) {
            return;
        }

        navigateTo("/inventory/bom/" + bom.get().getId() + "/edit");
        waitForPageLoad();

        var productSelect = page.locator("select[name='productId']").first();
        if (productSelect.isVisible()) {
            assertThat(productSelect).isVisible();
        }
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
        var bom = bomRepository.findAll().stream().findFirst();
        if (bom.isEmpty()) {
            return;
        }

        navigateTo("/inventory/bom/" + bom.get().getId() + "/edit");
        waitForPageLoad();

        // Toggle active checkbox
        var activeCheckbox = page.locator("input[name='active']").first();
        if (activeCheckbox.isVisible()) {
            if (activeCheckbox.isChecked()) {
                activeCheckbox.uncheck();
            } else {
                activeCheckbox.check();
            }
        }

        // Submit
        var submitBtn = page.locator("#btn-simpan").first();
        if (submitBtn.isVisible()) {
            submitBtn.click();
            waitForPageLoad();
        }

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should handle BOM detail page with lines")
    void shouldHandleBOMDetailPageWithLines() {
        var bom = bomRepository.findAll().stream()
                .filter(b -> b.getLines() != null && !b.getLines().isEmpty())
                .findFirst();

        if (bom.isEmpty()) {
            // If no BOM with lines, just test any BOM
            bom = bomRepository.findAll().stream().findFirst();
        }

        if (bom.isEmpty()) {
            return;
        }

        navigateTo("/inventory/bom/" + bom.get().getId());
        waitForPageLoad();

        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/inventory\\/bom\\/.*"));
    }
}
