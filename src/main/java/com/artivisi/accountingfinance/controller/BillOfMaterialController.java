package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.dto.ProductOptionDto;
import com.artivisi.accountingfinance.entity.BillOfMaterial;
import com.artivisi.accountingfinance.entity.BillOfMaterialLine;
import com.artivisi.accountingfinance.entity.Product;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.BillOfMaterialService;
import com.artivisi.accountingfinance.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/inventory/bom")
@PreAuthorize("hasAuthority('" + Permission.INVENTORY_VIEW + "')")
@RequiredArgsConstructor
@Slf4j
public class BillOfMaterialController {

    private static final String ATTR_ERROR = "error";
    private static final String ATTR_SUCCESS = "success";
    private static final String REDIRECT_BOM_LIST = "redirect:/inventory/bom";

    private final BillOfMaterialService bomService;
    private final ProductService productService;

    @GetMapping
    public String list(@RequestParam(required = false) String search, Model model) {
        List<BillOfMaterial> boms = bomService.search(search);
        model.addAttribute("boms", boms);
        model.addAttribute("search", search);
        return "inventory/bom/list";
    }

    @GetMapping("/create")
    @PreAuthorize("hasAuthority('" + Permission.PRODUCT_CREATE + "')")
    public String createForm(Model model) {
        model.addAttribute("bom", new BillOfMaterial());
        model.addAttribute("products", getProductOptions());
        return "inventory/bom/form";
    }

    private List<ProductOptionDto> getProductOptions() {
        return productService.findAllActive().stream()
                .map(p -> new ProductOptionDto(p.getId(), p.getCode(), p.getName()))
                .toList();
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('" + Permission.PRODUCT_EDIT + "')")
    public String editForm(@PathVariable UUID id, Model model, RedirectAttributes redirectAttributes) {
        return bomService.findByIdWithLines(id)
                .map(bom -> {
                    model.addAttribute("bom", bom);
                    model.addAttribute("products", getProductOptions());
                    return "inventory/bom/form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute(ATTR_ERROR, "BOM tidak ditemukan");
                    return REDIRECT_BOM_LIST;
                });
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model, RedirectAttributes redirectAttributes) {
        return bomService.findByIdWithLines(id)
                .map(bom -> {
                    model.addAttribute("bom", bom);
                    return "inventory/bom/detail";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute(ATTR_ERROR, "BOM tidak ditemukan");
                    return REDIRECT_BOM_LIST;
                });
    }

    @PostMapping("/save")
    @PreAuthorize("#id == null ? hasAuthority('" + Permission.PRODUCT_CREATE + "') : hasAuthority('" + Permission.PRODUCT_EDIT + "')")
    public String save(
            @RequestParam(required = false) UUID id,
            @RequestParam String code,
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam UUID productId,
            @RequestParam BigDecimal outputQuantity,
            @RequestParam(required = false, defaultValue = "false") Boolean active,
            @RequestParam(name = "componentId[]", required = false) List<UUID> componentIds,
            @RequestParam(name = "componentQty[]", required = false) List<BigDecimal> componentQtys,
            WebRequest webRequest,
            RedirectAttributes redirectAttributes
    ) {
        // Read raw values: a single-valued List<String> @RequestParam is split on commas by the conversion service.
        String[] componentNotes = webRequest.getParameterValues("componentNotes[]");
        try {
            BillOfMaterial bom = new BillOfMaterial();
            bom.setCode(code);
            bom.setName(name);
            bom.setDescription(description);
            bom.setOutputQuantity(outputQuantity);
            bom.setActive(Boolean.TRUE.equals(active));

            // Set product
            Product product = new Product();
            product.setId(productId);
            bom.setProduct(product);

            // Build lines
            List<BillOfMaterialLine> lines = new ArrayList<>();
            if (componentIds != null) {
                for (int i = 0; i < componentIds.size(); i++) {
                    if (componentIds.get(i) == null) continue;

                    BillOfMaterialLine line = new BillOfMaterialLine();
                    Product component = new Product();
                    component.setId(componentIds.get(i));
                    line.setComponent(component);
                    line.setQuantity(componentQtys.get(i));
                    line.setNotes(componentNotes != null && i < componentNotes.length ? componentNotes[i] : null);
                    line.setLineOrder(i);
                    lines.add(line);
                }
            }
            bom.setLines(lines);

            if (id == null) {
                bomService.create(bom);
                redirectAttributes.addFlashAttribute(ATTR_SUCCESS, "BOM berhasil dibuat");
            } else {
                bomService.update(id, bom);
                redirectAttributes.addFlashAttribute(ATTR_SUCCESS, "BOM berhasil diperbarui");
            }

            return REDIRECT_BOM_LIST;
        } catch (Exception e) {
            log.error("Error saving BOM", e);
            redirectAttributes.addFlashAttribute(ATTR_ERROR, e.getMessage());
            return id == null ? "redirect:/inventory/bom/create" : "redirect:/inventory/bom/" + id + "/edit";
        }
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('" + Permission.PRODUCT_DELETE + "')")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            bomService.delete(id);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS, "BOM berhasil dinonaktifkan");
        } catch (Exception e) {
            log.error("Error deleting BOM", e);
            redirectAttributes.addFlashAttribute(ATTR_ERROR, e.getMessage());
        }
        return REDIRECT_BOM_LIST;
    }
}
