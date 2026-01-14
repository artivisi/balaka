package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.BillOfMaterialRepository;
import com.artivisi.accountingfinance.repository.ProductionOrderRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Functional tests for ProductionOrderController.
 * Tests production order list, create, detail, start, complete, cancel operations.
 */
@DisplayName("Production Order Controller Tests")
@Import(ServiceTestDataInitializer.class)
class ProductionOrderControllerFunctionalTest extends PlaywrightTestBase {

    @Autowired
    private ProductionOrderRepository orderRepository;

    @Autowired
    private BillOfMaterialRepository bomRepository;

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
    }

    // ==================== LIST PAGE ====================

    @Test
    @DisplayName("Should display production order list page")
    void shouldDisplayProductionOrderListPage() {
        navigateTo("/inventory/production");
        waitForPageLoad();

        assertThat(page.locator("h1, .page-title").first()).isVisible();
    }

    @Test
    @DisplayName("Should display new order button")
    void shouldDisplayNewOrderButton() {
        navigateTo("/inventory/production");
        waitForPageLoad();

        assertThat(page.locator("a[href*='/inventory/production/create']").first()).isVisible();
    }

    // ==================== NEW ORDER FORM ====================

    @Test
    @DisplayName("Should display new production order form")
    void shouldDisplayNewProductionOrderForm() {
        navigateTo("/inventory/production/create");
        waitForPageLoad();

        assertThat(page.locator("#bomId")).isVisible();
        assertThat(page.locator("#quantity")).isVisible();
        assertThat(page.locator("#orderDate")).isVisible();
    }

    @Test
    @DisplayName("Should display BOM selection dropdown")
    void shouldDisplayBomSelectionDropdown() {
        navigateTo("/inventory/production/create");
        waitForPageLoad();

        assertThat(page.locator("#bomId")).isVisible();
    }

    @Test
    @DisplayName("Should display quantity input")
    void shouldDisplayQuantityInput() {
        navigateTo("/inventory/production/create");
        waitForPageLoad();

        assertThat(page.locator("#quantity")).isVisible();
    }

    @Test
    @DisplayName("Should display order date input")
    void shouldDisplayOrderDateInput() {
        navigateTo("/inventory/production/create");
        waitForPageLoad();

        assertThat(page.locator("#orderDate")).isVisible();
    }

    @Test
    @DisplayName("Should display notes textarea")
    void shouldDisplayNotesTextarea() {
        navigateTo("/inventory/production/create");
        waitForPageLoad();

        assertThat(page.locator("#notes")).isVisible();
    }

    @Test
    @DisplayName("Should create new production order with valid data")
    void shouldCreateNewProductionOrder() {
        var bom = bomRepository.findAll().stream().findFirst();
        if (bom.isEmpty()) return;

        navigateTo("/inventory/production/create");
        waitForPageLoad();

        // Select BOM
        page.locator("#bomId").selectOption(bom.get().getId().toString());

        // Fill quantity
        page.locator("#quantity").fill("10");

        // Fill order date
        page.locator("#orderDate").fill(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));

        // Submit
        page.locator("button[type='submit']").first().click();
        waitForPageLoad();

        // Should redirect to list or detail
        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/inventory\\/production.*"));
    }

    // ==================== DETAIL PAGE ====================

    @Test
    @DisplayName("Should display production order detail page")
    void shouldDisplayProductionOrderDetailPage() {
        var order = orderRepository.findAll().stream().findFirst();
        if (order.isEmpty()) return;

        navigateTo("/inventory/production/" + order.get().getId());
        waitForPageLoad();

        assertThat(page.getByTestId("order-number")).isVisible();
    }

    @Test
    @DisplayName("Should display order quantity on detail page")
    void shouldDisplayOrderQuantity() {
        var order = orderRepository.findAll().stream().findFirst();
        if (order.isEmpty()) return;

        navigateTo("/inventory/production/" + order.get().getId());
        waitForPageLoad();

        assertThat(page.getByTestId("order-quantity")).isVisible();
    }

    @Test
    @DisplayName("Should display product name on detail page")
    void shouldDisplayProductName() {
        var order = orderRepository.findAll().stream().findFirst();
        if (order.isEmpty()) return;

        navigateTo("/inventory/production/" + order.get().getId());
        waitForPageLoad();

        assertThat(page.getByTestId("product-name")).isVisible();
    }

    // ==================== ORDER ACTIONS ====================

    @Test
    @DisplayName("Should display start button for draft order")
    void shouldDisplayStartButtonForDraftOrder() {
        var order = orderRepository.findAll().stream()
                .filter(o -> "DRAFT".equals(o.getStatus().name()))
                .findFirst();
        if (order.isEmpty()) return;

        navigateTo("/inventory/production/" + order.get().getId());
        waitForPageLoad();

        assertThat(page.locator("#form-start")).isVisible();
    }

    @Test
    @DisplayName("Should display complete button for in-progress order")
    void shouldDisplayCompleteButtonForInProgressOrder() {
        var order = orderRepository.findAll().stream()
                .filter(o -> "IN_PROGRESS".equals(o.getStatus().name()))
                .findFirst();
        if (order.isEmpty()) return;

        navigateTo("/inventory/production/" + order.get().getId());
        waitForPageLoad();

        assertThat(page.locator("#form-complete")).isVisible();
    }

    @Test
    @DisplayName("Should display cancel button for draft order")
    void shouldDisplayCancelButtonForDraftOrder() {
        var order = orderRepository.findAll().stream()
                .filter(o -> "DRAFT".equals(o.getStatus().name()))
                .findFirst();
        if (order.isEmpty()) return;

        navigateTo("/inventory/production/" + order.get().getId());
        waitForPageLoad();

        assertThat(page.locator("#form-cancel")).isVisible();
    }

    @Test
    @DisplayName("Should display delete button for draft order")
    void shouldDisplayDeleteButtonForDraftOrder() {
        var order = orderRepository.findAll().stream()
                .filter(o -> "DRAFT".equals(o.getStatus().name()))
                .findFirst();
        if (order.isEmpty()) return;

        navigateTo("/inventory/production/" + order.get().getId());
        waitForPageLoad();

        assertThat(page.locator("#form-delete")).isVisible();
    }

    @Test
    @DisplayName("Should display completed status badge")
    void shouldDisplayCompletedStatusBadge() {
        var order = orderRepository.findAll().stream()
                .filter(o -> "COMPLETED".equals(o.getStatus().name()))
                .findFirst();
        if (order.isEmpty()) return;

        navigateTo("/inventory/production/" + order.get().getId());
        waitForPageLoad();

        assertThat(page.getByTestId("order-status-completed")).isVisible();
    }
}
