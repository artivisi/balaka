package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.BillOfMaterial;
import com.artivisi.accountingfinance.entity.ProductionOrder;
import com.artivisi.accountingfinance.entity.ProductionOrderStatus;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.BillOfMaterialService;
import com.artivisi.accountingfinance.service.ProductionOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

@Controller
@RequestMapping("/inventory/production")
@PreAuthorize("hasAuthority('" + Permission.INVENTORY_VIEW + "')")
@RequiredArgsConstructor
@Slf4j
public class ProductionOrderController {

    private static final String ATTR_ORDER = "order";
    private static final String ATTR_ERROR = "error";
    private static final String ATTR_SUCCESS = "success";
    private static final String REDIRECT_PRODUCTION_LIST = "redirect:/inventory/production";
    private static final String REDIRECT_PRODUCTION_DETAIL = "redirect:/inventory/production/";
    private static final String MSG_ORDER_NOT_FOUND = "Production order tidak ditemukan";
    private static final String MSG_PRODUCTION_ORDER = "Production order ";
    private static final String HAS_ADJUST = "hasAuthority('" + Permission.INVENTORY_ADJUST + "')";

    private final ProductionOrderService orderService;
    private final BillOfMaterialService bomService;

    @GetMapping
    public String list(@RequestParam(required = false) ProductionOrderStatus status, Model model) {
        if (status != null) {
            model.addAttribute("orders", orderService.findByStatus(status));
        } else {
            model.addAttribute("orders", orderService.findAll());
        }
        model.addAttribute("status", status);
        model.addAttribute("statuses", ProductionOrderStatus.values());
        return "inventory/production/list";
    }

    @GetMapping("/create")
    @PreAuthorize(HAS_ADJUST)
    public String createForm(Model model) {
        model.addAttribute(ATTR_ORDER, new ProductionOrder());
        model.addAttribute("boms", bomService.findAll());
        return "inventory/production/form";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize(HAS_ADJUST)
    public String editForm(@PathVariable UUID id, Model model, RedirectAttributes redirectAttributes) {
        return orderService.findById(id)
                .map(order -> {
                    if (!order.isDraft()) {
                        redirectAttributes.addFlashAttribute(ATTR_ERROR, "Hanya order dengan status DRAFT yang dapat diubah");
                        return REDIRECT_PRODUCTION_DETAIL + id;
                    }
                    model.addAttribute(ATTR_ORDER, order);
                    model.addAttribute("boms", bomService.findAll());
                    return "inventory/production/form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute(ATTR_ERROR, MSG_ORDER_NOT_FOUND);
                    return REDIRECT_PRODUCTION_LIST;
                });
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model, RedirectAttributes redirectAttributes) {
        return orderService.findByIdWithLines(id)
                .map(order -> {
                    model.addAttribute(ATTR_ORDER, order);
                    return "inventory/production/detail";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute(ATTR_ERROR, MSG_ORDER_NOT_FOUND);
                    return REDIRECT_PRODUCTION_LIST;
                });
    }

    @PostMapping("/save")
    @PreAuthorize(HAS_ADJUST)
    public String save(
            @RequestParam(required = false) UUID id,
            @RequestParam UUID bomId,
            @RequestParam BigDecimal quantity,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate orderDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate plannedCompletionDate,
            @RequestParam(required = false) String notes,
            RedirectAttributes redirectAttributes
    ) {
        try {
            ProductionOrder order = new ProductionOrder();
            BillOfMaterial bom = new BillOfMaterial();
            bom.setId(bomId);
            order.setBillOfMaterial(bom);
            order.setQuantity(quantity);
            order.setOrderDate(orderDate);
            order.setPlannedCompletionDate(plannedCompletionDate);
            order.setNotes(notes);

            if (id == null) {
                ProductionOrder created = orderService.create(order);
                redirectAttributes.addFlashAttribute(ATTR_SUCCESS, MSG_PRODUCTION_ORDER + created.getOrderNumber() + " berhasil dibuat");
            } else {
                orderService.update(id, order);
                redirectAttributes.addFlashAttribute(ATTR_SUCCESS, "Production order berhasil diperbarui");
            }

            return REDIRECT_PRODUCTION_LIST;
        } catch (Exception e) {
            log.error("Error saving production order", e);
            redirectAttributes.addFlashAttribute(ATTR_ERROR, e.getMessage());
            return id == null ? "redirect:/inventory/production/create" : REDIRECT_PRODUCTION_DETAIL + id + "/edit";
        }
    }

    @PostMapping("/{id}/start")
    @PreAuthorize(HAS_ADJUST)
    public String start(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            ProductionOrder order = orderService.start(id);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS, MSG_PRODUCTION_ORDER + order.getOrderNumber() + " sedang diproses");
        } catch (Exception e) {
            log.error("Error starting production order", e);
            redirectAttributes.addFlashAttribute(ATTR_ERROR, e.getMessage());
        }
        return REDIRECT_PRODUCTION_DETAIL + id;
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize(HAS_ADJUST)
    public String complete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            ProductionOrder order = orderService.complete(id);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS,
                    String.format(Locale.US, "Production order %s selesai. Total biaya: Rp %,.0f, Harga pokok per unit: Rp %,.2f",
                            order.getOrderNumber(), order.getTotalComponentCost(), order.getUnitCost()));
        } catch (Exception e) {
            log.error("Error completing production order", e);
            redirectAttributes.addFlashAttribute(ATTR_ERROR, e.getMessage());
        }
        return REDIRECT_PRODUCTION_DETAIL + id;
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(HAS_ADJUST)
    public String cancel(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            ProductionOrder order = orderService.cancel(id);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS, MSG_PRODUCTION_ORDER + order.getOrderNumber() + " dibatalkan");
        } catch (Exception e) {
            log.error("Error cancelling production order", e);
            redirectAttributes.addFlashAttribute(ATTR_ERROR, e.getMessage());
        }
        return REDIRECT_PRODUCTION_DETAIL + id;
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize(HAS_ADJUST)
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            orderService.delete(id);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS, "Production order berhasil dihapus");
        } catch (Exception e) {
            log.error("Error deleting production order", e);
            redirectAttributes.addFlashAttribute(ATTR_ERROR, e.getMessage());
        }
        return REDIRECT_PRODUCTION_LIST;
    }
}
