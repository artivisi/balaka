package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.BillOfMaterial;
import com.artivisi.accountingfinance.entity.BillOfMaterialLine;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.CostingMethod;
import com.artivisi.accountingfinance.entity.InventoryBalance;
import com.artivisi.accountingfinance.entity.InventoryTransaction;
import com.artivisi.accountingfinance.entity.InventoryTransactionType;
import com.artivisi.accountingfinance.entity.Product;
import com.artivisi.accountingfinance.entity.ProductionOrder;
import com.artivisi.accountingfinance.entity.ProductionOrderStatus;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.functional.manufacturing.CoffeeTestDataInitializer;
import com.artivisi.accountingfinance.repository.BillOfMaterialRepository;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.InventoryBalanceRepository;
import com.artivisi.accountingfinance.repository.InventoryFifoLayerRepository;
import com.artivisi.accountingfinance.repository.InventoryTransactionRepository;
import com.artivisi.accountingfinance.repository.ProductRepository;
import com.artivisi.accountingfinance.repository.ProductionOrderRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.service.BillOfMaterialService;
import com.artivisi.accountingfinance.service.InventoryService;
import com.artivisi.accountingfinance.service.ProductionOrderService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ProductionOrderController: list with status filter, create/edit form, detail with component requirements,
 * start (stock check), complete (component consumption at weighted-average/FIFO cost, finished goods at unit
 * cost, one DRAFT "Penyelesaian Produksi" journal per component), cancel, delete, state guards and permissions.
 *
 * Seeded coffee-shop orders, BOMs and stock are asserted by the manufacturing tests, so every order here
 * runs on products, stock and BOMs this class creates (unique codes).
 */
@DisplayName("Production Order Controller Tests")
@Import(CoffeeTestDataInitializer.class)
class ProductionOrderControllerFunctionalTest extends PlaywrightTestBase {

    private static final LocalDate ORDER_DATE = LocalDate.of(2026, 3, 16);
    private static final String SOURCE_TYPE = "PRODUCTION_ORDER";

    @Autowired
    private ProductionOrderRepository orderRepository;

    @Autowired
    private ProductionOrderService orderService;

    @Autowired
    private BillOfMaterialRepository bomRepository;

    @Autowired
    private BillOfMaterialService bomService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryBalanceRepository balanceRepository;

    @Autowired
    private InventoryFifoLayerRepository fifoLayerRepository;

    @Autowired
    private InventoryTransactionRepository inventoryTransactionRepository;

    @Autowired
    private ChartOfAccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ==================== LIST & FILTER ====================

    @Test
    @DisplayName("Should list orders with number, date, BOM/product, quantity, status and cost, linked from the sidebar")
    void shouldListOrders() {
        Fixture f = fixture();
        ProductionOrder draft = createOrder(f.bom, "8", "Draf daftar");
        ProductionOrder completed = createOrder(f.bom, "4", "Selesai daftar");
        orderService.start(completed.getId());
        orderService.complete(completed.getId());
        loginAsAdmin();

        navigateTo("/dashboard");
        waitForPageLoad();
        assertThat(page.locator("#nav-production")).hasAttribute("href", "/inventory/production");
        page.locator("#nav-group-inventori").click();
        page.locator("#nav-production").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/inventory/production");
        assertThat(page.locator("#page-title")).hasText("Production Orders");
        assertThat(page.locator("a[href='/inventory/production/create']")).hasText("Buat Order Produksi");

        Locator draftCells = orderRow(draft).locator("td");
        assertThat(draftCells.nth(0)).hasText(draft.getOrderNumber());
        assertThat(draftCells.nth(1)).hasText("16 Mar 2026");
        assertThat(draftCells.nth(2).locator("div")).hasText(new String[]{f.bom.getCode(), f.finished.getName()});
        assertThat(draftCells.nth(3)).hasText("8.00 pcs");
        assertThat(draftCells.nth(4)).hasText("Draft");
        assertThat(draftCells.nth(5)).hasText("-");
        assertThat(page.locator("[data-testid='order-detail-link-" + draft.getOrderNumber() + "']"))
                .hasAttribute("href", "/inventory/production/" + draft.getId());

        Locator completedCells = orderRow(completed).locator("td");
        assertThat(completedCells.nth(3)).hasText("4.00 pcs");
        assertThat(completedCells.nth(4)).hasText("Selesai");
        assertThat(completedCells.nth(5)).hasText("Rp 37,000");
    }

    @Test
    @DisplayName("Should filter orders by each status through the dropdown and show all with 'Semua Status'")
    void shouldFilterOrdersByStatus() {
        Fixture f = fixture();
        ProductionOrder draft = createOrder(f.bom, "4", null);
        ProductionOrder inProgress = createOrder(f.bom, "4", null);
        orderService.start(inProgress.getId());
        ProductionOrder completed = createOrder(f.bom, "4", null);
        orderService.start(completed.getId());
        orderService.complete(completed.getId());
        ProductionOrder cancelled = createOrder(f.bom, "4", null);
        orderService.cancel(cancelled.getId());
        List<ProductionOrder> mine = List.of(draft, inProgress, completed, cancelled);
        loginAsAdmin();

        navigateTo("/inventory/production");
        waitForPageLoad();
        assertThat(page.locator("#production-status-filter option")).hasText(new String[]{
                "Semua Status", "Draft", "Sedang Diproses", "Selesai", "Dibatalkan"});

        Map<String, ProductionOrder> byStatus = Map.of(
                "DRAFT", draft, "IN_PROGRESS", inProgress, "COMPLETED", completed, "CANCELLED", cancelled);
        for (var entry : byStatus.entrySet()) {
            page.locator("#production-status-filter").selectOption(entry.getKey());
            page.locator("form[action='/inventory/production'] button[type='submit']").click();
            waitForPageLoad();

            assertThat(page).hasURL(baseUrl() + "/inventory/production?status=" + entry.getKey());
            assertThat(page.locator("#production-status-filter")).hasValue(entry.getKey());
            for (ProductionOrder order : mine) {
                assertThat(orderRow(order)).hasCount(order == entry.getValue() ? 1 : 0);
            }
            // Seeded PROD-001 is COMPLETED; checked per filter because Map.of iteration order is unspecified
            assertThat(page.locator("[data-testid='production-order-row-PROD-001']"))
                    .hasCount("COMPLETED".equals(entry.getKey()) ? 1 : 0);
        }

        page.locator("#production-status-filter").selectOption("");
        page.locator("form[action='/inventory/production'] button[type='submit']").click();
        waitForPageLoad();
        for (ProductionOrder order : mine) {
            assertThat(orderRow(order)).hasCount(1);
        }
        assertThat(page.locator("[data-testid='production-order-row-PROD-001']")).hasCount(1);
    }

    // ==================== CREATE ====================

    @Test
    @DisplayName("Should create a DRAFT order through the form and show it with its component requirements")
    void shouldCreateOrder() {
        Fixture f = fixture();
        BillOfMaterial inactiveBom = createBom(f, "OFF");
        bomService.delete(inactiveBom.getId());
        loginAsAdmin();

        navigateTo("/inventory/production");
        waitForPageLoad();
        page.locator("a[href='/inventory/production/create']").click();
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Buat Production Order");
        assertThat(page.locator("#bomId")).hasValue("");
        assertThat(page.locator("#quantity")).hasValue("");
        assertThat(page.locator("#orderDate")).hasValue("");
        assertThat(page.locator("#bomId option[value='" + f.bom.getId() + "']"))
                .hasText(f.bom.getCode() + " - " + f.bom.getName() + " (" + f.finished.getName() + ")");
        assertThat(page.locator("#bomId option[value='" + inactiveBom.getId() + "']")).hasCount(0);

        page.locator("#bomId").selectOption(f.bom.getId().toString());
        page.locator("#quantity").fill("8");
        page.locator("#orderDate").fill(ORDER_DATE.toString());
        page.locator("#plannedCompletionDate").fill("2026-03-23");
        page.locator("#notes").fill("Batch pagi");
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        List<ProductionOrder> orders = orderRepository.findByBomId(f.bom.getId());
        assertEquals(1, orders.size());
        ProductionOrder order = orders.getFirst();
        assertThat(page).hasURL(baseUrl() + "/inventory/production");
        assertThat(page.getByTestId("flash-success"))
                .hasText("Production order " + order.getOrderNumber() + " berhasil dibuat");
        assertTrue(Pattern.matches("PO-" + Year.now().getValue() + "-\\d{4}", order.getOrderNumber()),
                order.getOrderNumber());
        assertEquals(ProductionOrderStatus.DRAFT, order.getStatus());
        assertEquals(0, new BigDecimal("8").compareTo(order.getQuantity()));
        assertEquals(ORDER_DATE, order.getOrderDate());
        assertEquals(LocalDate.of(2026, 3, 23), order.getPlannedCompletionDate());
        assertNull(order.getActualCompletionDate());
        assertEquals("Batch pagi", order.getNotes());
        assertEquals("admin", order.getCreatedBy());
        assertEquals(0, BigDecimal.ZERO.compareTo(order.getTotalComponentCost()));

        page.locator("[data-testid='order-detail-link-" + order.getOrderNumber() + "']").click();
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Production Order: " + order.getOrderNumber());
        assertThat(page.getByTestId("order-number")).hasText(order.getOrderNumber());
        assertThat(page.getByTestId("order-status-draft")).hasText("Draft");
        assertThat(page.getByTestId("order-date")).hasText("16 Maret 2026");
        assertThat(page.getByTestId("bom-name")).hasText(f.bom.getCode() + " - " + f.bom.getName());
        assertThat(page.getByTestId("product-name")).hasText(f.finished.getCode() + " - " + f.finished.getName());
        assertThat(info("Jumlah Produksi")).hasText("8.00 pcs");
        assertThat(info("Target Selesai")).hasText("23 Maret 2026");
        assertThat(info("Catatan")).hasText("Batch pagi");
        assertThat(info("Dibuat oleh")).hasText("admin");
        assertThat(page.locator("dt:text-is('Tanggal Selesai')")).hasCount(0);
        assertThat(page.getByTestId("order-total-cost")).hasCount(0);
        assertThat(requirementRows().nth(0).locator("td")).hasText(new String[]{
                "1", f.flour.getCode(), f.flour.getName(), "2.50", "5.00", "kg"});
        assertThat(requirementRows().nth(1).locator("td")).hasText(new String[]{
                "2", f.butter.getCode(), f.butter.getName(), "1.00", "2.00", "kg"});
        assertThat(page.locator("a:text-is('Edit')")).isVisible();
        assertThat(page.locator("#form-start button")).hasText("Mulai Produksi");
        assertThat(page.locator("#form-cancel button")).hasText("Batalkan");
        assertThat(page.locator("#form-delete button")).hasText("Hapus");
        assertThat(page.locator("#form-complete")).hasCount(0);
    }

    @Test
    @DisplayName("Should reject a non-positive quantity or unknown BOM with the service message and persist nothing")
    void shouldRejectInvalidOrder() {
        Fixture f = fixture();
        loginAsAdmin();
        navigateTo("/inventory/production/create");
        waitForPageLoad();
        String csrf = csrf();

        APIResponse zeroQty = postForm("/inventory/production/save",
                orderForm(csrf, null, f.bom.getId(), "0"));
        assertEquals(302, zeroQty.status());
        assertEquals(baseUrl() + "/inventory/production/create", zeroQty.headers().get("location"));
        navigateTo("/inventory/production/create");
        waitForPageLoad();
        assertThat(page.getByTestId("flash-error")).hasText("Jumlah produksi harus lebih dari 0");

        postForm("/inventory/production/save", orderForm(csrf, null, UUID.randomUUID(), "5"));
        navigateTo("/inventory/production/create");
        waitForPageLoad();
        assertThat(page.getByTestId("flash-error")).hasText("BOM tidak ditemukan");

        assertTrue(orderRepository.findByBomId(f.bom.getId()).isEmpty());
    }

    // ==================== EDIT ====================

    @Test
    @DisplayName("Should prefill the edit form of a DRAFT order and update BOM, quantity, dates and notes")
    void shouldUpdateDraftOrder() {
        Fixture f = fixture();
        BillOfMaterial otherBom = createBom(f, "ALT");
        ProductionOrder order = createOrder(f.bom, "8", "Catatan awal");
        loginAsAdmin();

        navigateTo("/inventory/production/" + order.getId());
        waitForPageLoad();
        page.locator("a:text-is('Edit')").click();
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Edit Production Order");
        assertThat(page.locator("input[name='id']")).hasValue(order.getId().toString());
        assertThat(page.locator("#bomId")).hasValue(f.bom.getId().toString());
        assertEquals(0, new BigDecimal("8").compareTo(new BigDecimal(page.locator("#quantity").inputValue())));
        assertThat(page.locator("#orderDate")).hasValue(ORDER_DATE.toString());
        assertThat(page.locator("#plannedCompletionDate")).hasValue("");
        assertThat(page.locator("#notes")).hasValue("Catatan awal");

        page.locator("#bomId").selectOption(otherBom.getId().toString());
        page.locator("#quantity").fill("12");
        page.locator("#orderDate").fill("2026-03-17");
        page.locator("#plannedCompletionDate").fill("2026-03-20");
        page.locator("#notes").fill("Ganti resep");
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/inventory/production");
        assertThat(page.getByTestId("flash-success")).hasText("Production order berhasil diperbarui");
        ProductionOrder updated = orderRepository.findByIdWithBom(order.getId()).orElseThrow();
        assertEquals(order.getOrderNumber(), updated.getOrderNumber());
        assertEquals(otherBom.getId(), updated.getBillOfMaterial().getId());
        assertEquals(0, new BigDecimal("12").compareTo(updated.getQuantity()));
        assertEquals(LocalDate.of(2026, 3, 17), updated.getOrderDate());
        assertEquals(LocalDate.of(2026, 3, 20), updated.getPlannedCompletionDate());
        assertEquals("Ganti resep", updated.getNotes());
        assertEquals(ProductionOrderStatus.DRAFT, updated.getStatus());
    }

    // ==================== START ====================

    @Test
    @DisplayName("Should refuse to start when a component lacks stock and start (without moving stock) when it suffices")
    void shouldStartOrderAfterStockCheck() {
        Fixture f = fixture();
        ProductionOrder tooBig = createOrder(f.bom, "20", null);
        ProductionOrder order = createOrder(f.bom, "8", null);
        loginAsAdmin();

        navigateTo("/inventory/production/" + tooBig.getId());
        waitForPageLoad();
        page.onceDialog(dialog -> dialog.accept());
        page.locator("#form-start button").click();
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/inventory/production/" + tooBig.getId());
        assertThat(page.getByTestId("flash-error")).hasText("Stok komponen " + f.flour.getCode()
                + " tidak mencukupi. Dibutuhkan: 12.50, Tersedia: 10.00");
        assertThat(page.getByTestId("order-status-draft")).isVisible();
        assertEquals(ProductionOrderStatus.DRAFT, status(tooBig));

        navigateTo("/inventory/production/" + order.getId());
        waitForPageLoad();
        page.onceDialog(dialog -> {
            assertEquals("Mulai produksi? Pastikan semua komponen tersedia.", dialog.message());
            dialog.dismiss();
        });
        page.locator("#form-start button").click();
        waitForPageLoad();
        assertEquals(ProductionOrderStatus.DRAFT, status(order));

        page.onceDialog(dialog -> dialog.accept());
        page.locator("#form-start button").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/inventory/production/" + order.getId());
        assertThat(page.getByTestId("flash-success"))
                .hasText("Production order " + order.getOrderNumber() + " sedang diproses");
        assertThat(page.getByTestId("order-status-in-progress")).hasText("Sedang Diproses");
        assertThat(page.locator("#form-complete button")).hasText("Selesaikan Produksi");
        assertThat(page.locator("#form-cancel")).isVisible();
        assertThat(page.locator("#form-start")).hasCount(0);
        assertThat(page.locator("#form-delete")).hasCount(0);
        assertThat(page.locator("a:text-is('Edit')")).hasCount(0);
        assertEquals(ProductionOrderStatus.IN_PROGRESS, status(order));
        assertInitialStock(f);
    }

    // ==================== COMPLETE ====================

    @Test
    @DisplayName("Should complete an order: consume components at average/FIFO cost, receive finished goods at unit cost and draft one journal per component")
    void shouldCompleteOrder() {
        Fixture f = fixture();
        ProductionOrder order = createOrder(f.bom, "8", null);
        orderService.start(order.getId());
        loginAsAdmin();

        navigateTo("/inventory/production/" + order.getId());
        waitForPageLoad();
        page.onceDialog(dialog -> {
            assertEquals("Selesaikan produksi? Komponen akan dikurangi dari stok dan produk jadi akan ditambahkan.",
                    dialog.message());
            dialog.dismiss();
        });
        page.locator("#form-complete button").click();
        waitForPageLoad();
        assertEquals(ProductionOrderStatus.IN_PROGRESS, status(order));
        assertInitialStock(f);

        page.onceDialog(dialog -> dialog.accept());
        page.locator("#form-complete button").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/inventory/production/" + order.getId());
        assertThat(page.getByTestId("flash-success")).hasText("Production order " + order.getOrderNumber()
                + " selesai. Total biaya: Rp 75,000, Harga pokok per unit: Rp 9,375.00");
        assertThat(page.getByTestId("order-status-completed")).hasText("Selesai");
        assertThat(page.getByTestId("order-total-cost")).hasText("Rp 75,000");
        assertThat(page.getByTestId("order-quantity")).hasText("8.00 pcs");
        assertThat(page.getByTestId("order-unit-cost")).hasText("Rp 9,375.00");
        assertThat(info("Diselesaikan oleh")).hasText("admin");
        assertThat(page.locator("#form-start, #form-complete, #form-cancel, #form-delete")).hasCount(0);
        assertThat(page.locator("a:text-is('Edit')")).hasCount(0);

        ProductionOrder completed = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(ProductionOrderStatus.COMPLETED, completed.getStatus());
        assertEquals(0, new BigDecimal("75000").compareTo(completed.getTotalComponentCost()));
        assertEquals(0, new BigDecimal("9375").compareTo(completed.getUnitCost()));
        assertEquals(LocalDate.now(), completed.getActualCompletionDate());
        assertEquals("admin", completed.getCompletedBy());

        assertBalance(f.flour, "5", "60000", "12000");
        assertBalance(f.butter, "3", "24000", "8000");
        assertBalance(f.finished, "8", "75000", "9375");
        assertEquals(List.of("3.0000@8000.0000"), fifoLayers(f.butter));

        assertEquals(List.of("2026-03-16|PRODUCTION_OUT|5.0000|12000.0000|60000.00"),
                movements(f.flour, InventoryTransactionType.PRODUCTION_OUT, order.getOrderNumber()));
        assertEquals(List.of("2026-03-16|PRODUCTION_OUT|2.0000|7500.0000|15000.00"),
                movements(f.butter, InventoryTransactionType.PRODUCTION_OUT, order.getOrderNumber()));
        assertEquals(List.of("2026-03-16|PRODUCTION_IN|8.0000|9375.0000|75000.00"),
                movements(f.finished, InventoryTransactionType.PRODUCTION_IN, order.getOrderNumber()));

        List<Map<String, Object>> drafts = productionJournals(order.getId());
        assertEquals(2, drafts.size());
        assertJournal(drafts.get(0), order, f.flour, "60000");
        assertJournal(drafts.get(1), order, f.butter, "15000");
    }

    @Test
    @DisplayName("Should roll back the whole completion when a component ran out after start")
    void shouldRollBackCompletionOnInsufficientStock() {
        Fixture f = fixture();
        ProductionOrder order = createOrder(f.bom, "8", null);
        orderService.start(order.getId());
        inventoryService.recordAdjustmentOut(f.butter.getId(), ORDER_DATE, new BigDecimal("4"),
                "ADJ-" + f.butter.getCode(), "Rusak");
        loginAsAdmin();

        navigateTo("/inventory/production/" + order.getId());
        waitForPageLoad();
        page.onceDialog(dialog -> dialog.accept());
        page.locator("#form-complete button").click();
        waitForPageLoad();

        assertThat(page.getByTestId("flash-error")).containsText("Stok tidak mencukupi untuk " + f.butter.getCode());
        assertThat(page.getByTestId("order-status-in-progress")).isVisible();
        assertThat(page.getByTestId("order-total-cost")).hasCount(0);
        assertEquals(ProductionOrderStatus.IN_PROGRESS, status(order));
        assertBalance(f.flour, "10", "120000", "12000");
        assertBalance(f.butter, "1", "8000", "8000");
        assertTrue(balanceRepository.findByProductId(f.finished.getId()).isEmpty());
        assertTrue(movements(f.flour, InventoryTransactionType.PRODUCTION_OUT, order.getOrderNumber()).isEmpty());
        assertTrue(productionJournals(order.getId()).isEmpty());
    }

    @Test
    @DisplayName("Should refuse to edit, start, complete again, cancel or delete a completed order")
    void shouldGuardCompletedOrder() {
        Fixture f = fixture();
        ProductionOrder order = createOrder(f.bom, "4", null);
        orderService.start(order.getId());
        orderService.complete(order.getId());
        loginAsAdmin();

        navigateTo("/inventory/production/" + order.getId() + "/edit");
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/inventory/production/" + order.getId());
        assertThat(page.getByTestId("flash-error")).hasText("Hanya order dengan status DRAFT yang dapat diubah");
        String csrf = csrf();

        APIResponse save = postForm("/inventory/production/save", orderForm(csrf, order.getId(), f.bom.getId(), "99"));
        assertEquals(baseUrl() + "/inventory/production/" + order.getId() + "/edit", save.headers().get("location"));

        Map<String, String> actions = Map.of(
                "start", "Order tidak dapat dimulai dari status COMPLETED",
                "complete", "Order tidak dapat diselesaikan dari status COMPLETED",
                "cancel", "Order tidak dapat dibatalkan dari status COMPLETED");
        for (var action : actions.entrySet()) {
            APIResponse response = postForm("/inventory/production/" + order.getId() + "/" + action.getKey(),
                    FormData.create().set("_csrf", csrf));
            assertEquals(baseUrl() + "/inventory/production/" + order.getId(), response.headers().get("location"));
            navigateTo("/inventory/production/" + order.getId());
            waitForPageLoad();
            assertThat(page.getByTestId("flash-error")).hasText(action.getValue());
        }

        postForm("/inventory/production/" + order.getId() + "/delete", FormData.create().set("_csrf", csrf));
        navigateTo("/inventory/production");
        waitForPageLoad();
        assertThat(page.getByTestId("flash-error")).hasText("Hanya order dengan status DRAFT yang dapat dihapus");
        assertThat(orderRow(order)).hasCount(1);

        ProductionOrder unchanged = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(ProductionOrderStatus.COMPLETED, unchanged.getStatus());
        assertEquals(0, new BigDecimal("4").compareTo(unchanged.getQuantity()));
        assertBalance(f.finished, "4", "37000", "9250");
        assertBalance(f.flour, "7.5", "90000", "12000");
        assertEquals(2, productionJournals(order.getId()).size());
    }

    // ==================== CANCEL & DELETE ====================

    @Test
    @DisplayName("Should cancel DRAFT and IN_PROGRESS orders without moving stock")
    void shouldCancelOrders() {
        Fixture f = fixture();
        ProductionOrder draft = createOrder(f.bom, "4", null);
        ProductionOrder inProgress = createOrder(f.bom, "4", null);
        orderService.start(inProgress.getId());
        loginAsAdmin();

        navigateTo("/inventory/production/" + draft.getId());
        waitForPageLoad();
        page.onceDialog(dialog -> {
            assertEquals("Yakin ingin membatalkan order ini?", dialog.message());
            dialog.dismiss();
        });
        page.locator("#form-cancel button").click();
        waitForPageLoad();
        assertEquals(ProductionOrderStatus.DRAFT, status(draft));

        for (ProductionOrder order : List.of(draft, inProgress)) {
            navigateTo("/inventory/production/" + order.getId());
            waitForPageLoad();
            page.onceDialog(dialog -> dialog.accept());
            page.locator("#form-cancel button").click();
            waitForPageLoad();

            assertThat(page).hasURL(baseUrl() + "/inventory/production/" + order.getId());
            assertThat(page.getByTestId("flash-success"))
                    .hasText("Production order " + order.getOrderNumber() + " dibatalkan");
            assertThat(page.getByTestId("order-status-cancelled")).hasText("Dibatalkan");
            assertThat(page.locator("#form-start, #form-complete, #form-cancel, #form-delete")).hasCount(0);
            assertThat(page.locator("a:text-is('Edit')")).hasCount(0);
            assertEquals(ProductionOrderStatus.CANCELLED, status(order));
        }
        assertInitialStock(f);
    }

    @Test
    @DisplayName("Should delete a DRAFT order after confirmation and refuse to delete an IN_PROGRESS order")
    void shouldDeleteDraftOrder() {
        Fixture f = fixture();
        ProductionOrder draft = createOrder(f.bom, "4", null);
        ProductionOrder inProgress = createOrder(f.bom, "4", null);
        orderService.start(inProgress.getId());
        loginAsAdmin();

        navigateTo("/inventory/production/" + draft.getId());
        waitForPageLoad();
        page.onceDialog(dialog -> {
            assertEquals("Yakin ingin menghapus order ini?", dialog.message());
            dialog.dismiss();
        });
        page.locator("#form-delete button").click();
        waitForPageLoad();
        assertTrue(orderRepository.findById(draft.getId()).isPresent());

        page.onceDialog(dialog -> dialog.accept());
        page.locator("#form-delete button").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/inventory/production");
        assertThat(page.getByTestId("flash-success")).hasText("Production order berhasil dihapus");
        assertThat(orderRow(draft)).hasCount(0);
        assertTrue(orderRepository.findById(draft.getId()).isEmpty());

        postForm("/inventory/production/" + inProgress.getId() + "/delete", FormData.create().set("_csrf", csrf()));
        navigateTo("/inventory/production");
        waitForPageLoad();
        assertThat(page.getByTestId("flash-error")).hasText("Hanya order dengan status DRAFT yang dapat dihapus");
        assertEquals(ProductionOrderStatus.IN_PROGRESS, status(inProgress));
    }

    @Test
    @DisplayName("Should redirect to the list with an error for an unknown order id on detail and edit")
    void shouldRejectUnknownOrder() {
        loginAsAdmin();
        UUID unknown = UUID.randomUUID();

        for (String path : List.of("/inventory/production/" + unknown, "/inventory/production/" + unknown + "/edit")) {
            navigateTo(path);
            waitForPageLoad();
            assertThat(page).hasURL(baseUrl() + "/inventory/production");
            assertThat(page.getByTestId("flash-error")).hasText("Production order tidak ditemukan");
        }
    }

    // ==================== PERMISSIONS ====================

    @Test
    @DisplayName("Should let a view-only user browse orders but not create, edit, start, complete, cancel or delete them")
    void shouldDenyChangesWithoutInventoryAdjustPermission() {
        Fixture f = fixture();
        ProductionOrder draft = createOrder(f.bom, "4", "Draf auditor");
        ProductionOrder inProgress = createOrder(f.bom, "4", null);
        orderService.start(inProgress.getId());
        User auditor = createUser(Role.AUDITOR);
        login(auditor.getUsername(), "password");

        navigateTo("/inventory/production");
        waitForPageLoad();
        assertThat(orderRow(draft)).isVisible();
        assertThat(page.locator("a[href='/inventory/production/create']")).hasCount(0);

        for (ProductionOrder order : List.of(draft, inProgress)) {
            navigateTo("/inventory/production/" + order.getId());
            waitForPageLoad();
            assertThat(page.getByTestId("order-number")).hasText(order.getOrderNumber());
            assertThat(page.locator("#form-start, #form-complete, #form-cancel, #form-delete")).hasCount(0);
            assertThat(page.locator("a:text-is('Edit')")).hasCount(0);
        }
        String csrf = csrf();

        for (String path : List.of("/inventory/production/create", "/inventory/production/" + draft.getId() + "/edit")) {
            navigateTo(path);
            waitForPageLoad();
            assertThat(page.locator("#access-denied-page")).isVisible();
        }

        assertEquals(403, postForm("/inventory/production/save", orderForm(csrf, null, f.bom.getId(), "4")).status());
        assertEquals(403, postForm("/inventory/production/save", orderForm(csrf, draft.getId(), f.bom.getId(), "9")).status());
        FormData token = FormData.create().set("_csrf", csrf);
        assertEquals(403, postForm("/inventory/production/" + draft.getId() + "/start", token).status());
        assertEquals(403, postForm("/inventory/production/" + inProgress.getId() + "/complete", token).status());
        assertEquals(403, postForm("/inventory/production/" + draft.getId() + "/cancel", token).status());
        assertEquals(403, postForm("/inventory/production/" + draft.getId() + "/delete", token).status());

        assertEquals(2, orderRepository.findByBomId(f.bom.getId()).size());
        ProductionOrder unchangedDraft = orderRepository.findById(draft.getId()).orElseThrow();
        assertEquals(ProductionOrderStatus.DRAFT, unchangedDraft.getStatus());
        assertEquals(0, new BigDecimal("4").compareTo(unchangedDraft.getQuantity()));
        assertEquals(ProductionOrderStatus.IN_PROGRESS, status(inProgress));
        assertInitialStock(f);
    }

    // ==================== helpers ====================

    /**
     * Finished good (weighted average), flour 10 kg @ 12,000 (weighted average) and butter 1 kg @ 7,000 +
     * 4 kg @ 8,000 (FIFO); the BOM yields 4 pcs from 2.5 kg flour and 1 kg butter.
     */
    private record Fixture(Product finished, Product flour, Product butter, BillOfMaterial bom) {
    }

    private Fixture fixture() {
        Product finished = createProduct("ROTI", "pcs", "1.1.21", CostingMethod.WEIGHTED_AVERAGE);
        Product flour = createProduct("TEPUNG", "kg", "1.1.20", CostingMethod.WEIGHTED_AVERAGE);
        Product butter = createProduct("MENTEGA", "kg", "1.1.20", CostingMethod.FIFO);
        inventoryService.recordPurchase(flour.getId(), ORDER_DATE.minusDays(5), new BigDecimal("10"),
                new BigDecimal("12000"), "BELI-" + flour.getCode(), "Stok awal");
        inventoryService.recordPurchase(butter.getId(), ORDER_DATE.minusDays(5), new BigDecimal("1"),
                new BigDecimal("7000"), "BELI1-" + butter.getCode(), "Stok awal");
        inventoryService.recordPurchase(butter.getId(), ORDER_DATE.minusDays(4), new BigDecimal("4"),
                new BigDecimal("8000"), "BELI2-" + butter.getCode(), "Stok kedua");
        Fixture partial = new Fixture(finished, flour, butter, null);
        return new Fixture(finished, flour, butter, createBom(partial, "PRD"));
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
        bom.setName("Resep " + code);
        bom.setProduct(f.finished);
        bom.setOutputQuantity(new BigDecimal("4"));
        bom.setActive(true);
        bom.setLines(new ArrayList<>());
        bom.getLines().add(line(f.flour, "2.5", 0));
        bom.getLines().add(line(f.butter, "1", 1));
        return bomService.create(bom);
    }

    private BillOfMaterialLine line(Product component, String quantity, int order) {
        BillOfMaterialLine line = new BillOfMaterialLine();
        line.setComponent(component);
        line.setQuantity(new BigDecimal(quantity));
        line.setLineOrder(order);
        return line;
    }

    private ProductionOrder createOrder(BillOfMaterial bom, String quantity, String notes) {
        ProductionOrder order = new ProductionOrder();
        order.setBillOfMaterial(bom);
        order.setQuantity(new BigDecimal(quantity));
        order.setOrderDate(ORDER_DATE);
        order.setNotes(notes);
        return orderService.create(order);
    }

    private ChartOfAccount account(String code) {
        return accountRepository.findByAccountCode(code)
                .orElseThrow(() -> new IllegalStateException("Seeded account missing: " + code));
    }

    private ProductionOrderStatus status(ProductionOrder order) {
        return orderRepository.findById(order.getId()).orElseThrow().getStatus();
    }

    private void assertInitialStock(Fixture f) {
        assertBalance(f.flour, "10", "120000", "12000");
        assertBalance(f.butter, "5", "39000", "7800");
        assertTrue(balanceRepository.findByProductId(f.finished.getId()).isEmpty());
    }

    private void assertBalance(Product product, String quantity, String totalCost, String averageCost) {
        InventoryBalance balance = balanceRepository.findByProductId(product.getId())
                .orElseThrow(() -> new AssertionError("No inventory balance for " + product.getCode()));
        assertEquals(0, new BigDecimal(quantity).compareTo(balance.getQuantity()), product.getCode() + " quantity");
        assertEquals(0, new BigDecimal(totalCost).compareTo(balance.getTotalCost()), product.getCode() + " total cost");
        assertEquals(0, new BigDecimal(averageCost).compareTo(balance.getAverageCost()), product.getCode() + " average cost");
    }

    private List<String> fifoLayers(Product product) {
        return fifoLayerRepository.findAvailableLayers(product.getId()).stream()
                .map(l -> l.getRemainingQuantity().setScale(4) + "@" + l.getUnitCost().setScale(4))
                .toList();
    }

    private List<String> movements(Product product, InventoryTransactionType type, String reference) {
        return inventoryTransactionRepository.findByProductId(product.getId()).stream()
                .filter(t -> t.getTransactionType() == type && reference.equals(t.getReferenceNumber()))
                .map(this::movement)
                .toList();
    }

    private String movement(InventoryTransaction t) {
        return t.getTransactionDate() + "|" + t.getTransactionType() + "|" + t.getQuantity().setScale(4) + "|" + t.getUnitCost().setScale(4)
                + "|" + t.getTotalCost().setScale(2);
    }

    private List<Map<String, Object>> productionJournals(UUID orderId) {
        return jdbcTemplate.queryForList("""
                SELECT t.id, t.status, t.amount, t.description, t.transaction_date, t.transaction_number,
                       jt.template_name
                FROM transactions t JOIN journal_templates jt ON jt.id = t.id_journal_template
                WHERE t.source_document_type = ? AND t.source_document_id = ?
                ORDER BY t.amount DESC""", SOURCE_TYPE, orderId);
    }

    private void assertJournal(Map<String, Object> journal, ProductionOrder order, Product component, String amount) {
        assertEquals("DRAFT", journal.get("status"));
        assertNull(journal.get("transaction_number"));
        assertEquals("Penyelesaian Produksi", journal.get("template_name"));
        assertEquals(0, new BigDecimal(amount).compareTo((BigDecimal) journal.get("amount")));
        assertEquals("Produksi " + order.getOrderNumber() + " - " + component.getName(), journal.get("description"));
        assertEquals(ORDER_DATE, ((java.sql.Date) journal.get("transaction_date")).toLocalDate());
        UUID id = (UUID) journal.get("id");
        assertEquals(List.of("DEBIT:1.1.21", "CREDIT:1.1.20"), jdbcTemplate.queryForList("""
                SELECT jtl.position || ':' || coa.account_code
                FROM transaction_account_mappings m
                JOIN journal_template_lines jtl ON jtl.id = m.id_template_line
                JOIN chart_of_accounts coa ON coa.id = m.id_account
                WHERE m.id_transaction = ? ORDER BY jtl.line_order""", String.class, id));
        BigDecimal componentCost = jdbcTemplate.queryForObject(
                "SELECT variable_value FROM transaction_variables WHERE id_transaction = ? AND variable_name = 'componentCost'",
                BigDecimal.class, id);
        assertEquals(0, new BigDecimal(amount).compareTo(componentCost));
    }

    private Locator orderRow(ProductionOrder order) {
        return page.locator("[data-testid='production-order-row-" + order.getOrderNumber() + "']");
    }

    private Locator info(String label) {
        return page.locator("dt:text-is('" + label + "') + dd").first();
    }

    private Locator requirementRows() {
        return page.locator("h2:text-is('Komponen yang Dibutuhkan') + div tbody tr");
    }

    private FormData orderForm(String csrf, UUID id, UUID bomId, String quantity) {
        FormData form = FormData.create().set("_csrf", csrf).set("bomId", bomId.toString())
                .set("quantity", quantity).set("orderDate", ORDER_DATE.toString());
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
        user.setUsername("po-user-" + id);
        user.setPassword(passwordEncoder.encode("password"));
        user.setFullName("Pengguna " + id);
        user.setEmail("po-user-" + id + "@example.com");
        user.setActive(true);
        user.addRole(role, "system");
        return userRepository.save(user);
    }

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
