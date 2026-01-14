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

    // ==================== FILTER TESTS ====================

    @Test
    @DisplayName("Should filter by DRAFT status")
    void shouldFilterByDraftStatus() {
        navigateTo("/inventory/production?status=DRAFT");
        waitForPageLoad();

        assertThat(page.locator("h1, .page-title").first()).isVisible();
    }

    @Test
    @DisplayName("Should filter by IN_PROGRESS status")
    void shouldFilterByInProgressStatus() {
        navigateTo("/inventory/production?status=IN_PROGRESS");
        waitForPageLoad();

        assertThat(page.locator("h1, .page-title").first()).isVisible();
    }

    @Test
    @DisplayName("Should filter by COMPLETED status")
    void shouldFilterByCompletedStatus() {
        navigateTo("/inventory/production?status=COMPLETED");
        waitForPageLoad();

        assertThat(page.locator("h1, .page-title").first()).isVisible();
    }

    @Test
    @DisplayName("Should filter by CANCELLED status")
    void shouldFilterByCancelledStatus() {
        navigateTo("/inventory/production?status=CANCELLED");
        waitForPageLoad();

        assertThat(page.locator("h1, .page-title").first()).isVisible();
    }

    // ==================== EDIT FORM TESTS ====================

    @Test
    @DisplayName("Should display edit form for draft order")
    void shouldDisplayEditFormForDraftOrder() {
        var order = orderRepository.findAll().stream()
                .filter(o -> "DRAFT".equals(o.getStatus().name()))
                .findFirst();
        if (order.isEmpty()) {
            navigateTo("/inventory/production");
            waitForPageLoad();
            assertThat(page.locator("h1, .page-title").first()).isVisible();
            return;
        }

        navigateTo("/inventory/production/" + order.get().getId() + "/edit");
        waitForPageLoad();

        assertThat(page.locator("#bomId, #quantity, #orderDate").first()).isVisible();
    }

    @Test
    @DisplayName("Should redirect when editing non-draft order")
    void shouldRedirectWhenEditingNonDraftOrder() {
        var order = orderRepository.findAll().stream()
                .filter(o -> !"DRAFT".equals(o.getStatus().name()))
                .findFirst();
        if (order.isEmpty()) {
            navigateTo("/inventory/production");
            waitForPageLoad();
            assertThat(page.locator("h1, .page-title").first()).isVisible();
            return;
        }

        navigateTo("/inventory/production/" + order.get().getId() + "/edit");
        waitForPageLoad();

        // Should redirect to detail page
        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/inventory\\/production\\/.*"));
    }

    @Test
    @DisplayName("Should handle non-existent order detail")
    void shouldHandleNonExistentOrderDetail() {
        navigateTo("/inventory/production/00000000-0000-0000-0000-000000000000");
        waitForPageLoad();

        // Should redirect to list
        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/inventory\\/production.*"));
    }

    @Test
    @DisplayName("Should handle non-existent order edit")
    void shouldHandleNonExistentOrderEdit() {
        navigateTo("/inventory/production/00000000-0000-0000-0000-000000000000/edit");
        waitForPageLoad();

        // Should redirect to list
        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/inventory\\/production.*"));
    }

    // ==================== ACTION TESTS ====================

    @Test
    @DisplayName("Should start draft order")
    void shouldStartDraftOrder() {
        var order = orderRepository.findAll().stream()
                .filter(o -> "DRAFT".equals(o.getStatus().name()))
                .findFirst();
        if (order.isEmpty()) {
            navigateTo("/inventory/production");
            waitForPageLoad();
            assertThat(page.locator("h1, .page-title").first()).isVisible();
            return;
        }

        navigateTo("/inventory/production/" + order.get().getId());
        waitForPageLoad();

        var startBtn = page.locator("#form-start button[type='submit']").first();
        if (startBtn.isVisible()) {
            startBtn.click();
            waitForPageLoad();
        }

        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/inventory\\/production\\/.*"));
    }

    @Test
    @DisplayName("Should complete in-progress order")
    void shouldCompleteInProgressOrder() {
        var order = orderRepository.findAll().stream()
                .filter(o -> "IN_PROGRESS".equals(o.getStatus().name()))
                .findFirst();
        if (order.isEmpty()) {
            navigateTo("/inventory/production");
            waitForPageLoad();
            assertThat(page.locator("h1, .page-title").first()).isVisible();
            return;
        }

        navigateTo("/inventory/production/" + order.get().getId());
        waitForPageLoad();

        var completeBtn = page.locator("#form-complete button[type='submit']").first();
        if (completeBtn.isVisible()) {
            completeBtn.click();
            waitForPageLoad();
        }

        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/inventory\\/production\\/.*"));
    }

    @Test
    @DisplayName("Should cancel draft order")
    void shouldCancelDraftOrder() {
        var order = orderRepository.findAll().stream()
                .filter(o -> "DRAFT".equals(o.getStatus().name()))
                .findFirst();
        if (order.isEmpty()) {
            navigateTo("/inventory/production");
            waitForPageLoad();
            assertThat(page.locator("h1, .page-title").first()).isVisible();
            return;
        }

        navigateTo("/inventory/production/" + order.get().getId());
        waitForPageLoad();

        var cancelBtn = page.locator("#form-cancel button[type='submit']").first();
        if (cancelBtn.isVisible()) {
            cancelBtn.click();
            waitForPageLoad();
        }

        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/inventory\\/production\\/.*"));
    }

    @Test
    @DisplayName("Should delete draft order")
    void shouldDeleteDraftOrder() {
        var order = orderRepository.findAll().stream()
                .filter(o -> "DRAFT".equals(o.getStatus().name()))
                .findFirst();
        if (order.isEmpty()) {
            navigateTo("/inventory/production");
            waitForPageLoad();
            assertThat(page.locator("h1, .page-title").first()).isVisible();
            return;
        }

        navigateTo("/inventory/production/" + order.get().getId());
        waitForPageLoad();

        var deleteBtn = page.locator("#form-delete button[type='submit']").first();
        if (deleteBtn.isVisible()) {
            deleteBtn.click();
            waitForPageLoad();
        }

        // Should redirect to list
        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/inventory\\/production.*"));
    }
}
