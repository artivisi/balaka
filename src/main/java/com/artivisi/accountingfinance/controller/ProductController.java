package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.CostingMethod;
import com.artivisi.accountingfinance.entity.Product;
import com.artivisi.accountingfinance.entity.ProductCategory;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.ProductCategoryService;
import com.artivisi.accountingfinance.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.UUID;

import static com.artivisi.accountingfinance.controller.ViewConstants.*;

@Controller
@RequestMapping("/products")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permission.PRODUCT_VIEW + "')")
public class ProductController {

    private static final String ATTR_PRODUCT = "product";
    private static final String ATTR_PRODUCTS = "products";
    private static final String ATTR_SUCCESS_MESSAGE = "successMessage";
    private static final String REDIRECT_PRODUCTS = "redirect:/products";
    private static final String VIEW_FORM = "products/form";

    private final ProductService productService;
    private final ProductCategoryService categoryService;
    private final ChartOfAccountRepository chartOfAccountRepository;

    @GetMapping
    public String list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) Boolean active,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            @PageableDefault(size = 20) Pageable pageable,
            Model model) {

        Page<Product> products = productService.findByFilters(search, categoryId, active, pageable);

        model.addAttribute(ATTR_PRODUCTS, products);
        model.addAttribute("search", search);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("active", active);
        model.addAttribute("categories", categoryService.findAllActive());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_PRODUCTS);

        if ("true".equals(hxRequest)) {
            return "products/fragments/product-table :: table";
        }

        return "products/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('" + Permission.PRODUCT_CREATE + "')")
    public String newForm(Model model) {
        Product product = new Product();
        product.setCostingMethod(CostingMethod.WEIGHTED_AVERAGE);
        product.setTrackInventory(true);
        product.setActive(true);

        model.addAttribute(ATTR_PRODUCT, product);
        addFormAttributes(model);
        return VIEW_FORM;
    }

    @PostMapping("/new")
    @PreAuthorize("hasAuthority('" + Permission.PRODUCT_CREATE + "')")
    public String create(
            @Valid @ModelAttribute("product") Product product,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            addFormAttributes(model);
            return VIEW_FORM;
        }

        try {
            productService.create(product);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Produk berhasil ditambahkan");
            return REDIRECT_PRODUCTS;
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("Kode")) {
                bindingResult.rejectValue("code", "duplicate", e.getMessage());
            } else {
                bindingResult.reject("error", e.getMessage());
            }
            addFormAttributes(model);
            return VIEW_FORM;
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        Product product = productService.findByIdWithDetails(id)
                .orElseThrow(() -> new IllegalArgumentException("Produk tidak ditemukan: " + id));

        model.addAttribute(ATTR_PRODUCT, product);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_PRODUCTS);
        return "products/detail";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('" + Permission.PRODUCT_EDIT + "')")
    public String editForm(@PathVariable UUID id, Model model) {
        Product product = productService.findByIdWithDetails(id)
                .orElseThrow(() -> new IllegalArgumentException("Produk tidak ditemukan: " + id));

        model.addAttribute(ATTR_PRODUCT, product);
        addFormAttributes(model);
        return VIEW_FORM;
    }

    @PostMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('" + Permission.PRODUCT_EDIT + "')")
    public String update(
            @PathVariable UUID id,
            @Valid @ModelAttribute("product") Product product,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            addFormAttributes(model);
            return VIEW_FORM;
        }

        try {
            productService.update(id, product);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Produk berhasil diubah");
            return REDIRECT_PRODUCTS;
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("Kode")) {
                bindingResult.rejectValue("code", "duplicate", e.getMessage());
            } else {
                bindingResult.reject("error", e.getMessage());
            }
            addFormAttributes(model);
            return VIEW_FORM;
        }
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('" + Permission.PRODUCT_EDIT + "')")
    public String activate(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        productService.activate(id);
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Produk berhasil diaktifkan");
        return REDIRECT_PRODUCTS;
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('" + Permission.PRODUCT_EDIT + "')")
    public String deactivate(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        productService.deactivate(id);
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Produk berhasil dinonaktifkan");
        return REDIRECT_PRODUCTS;
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('" + Permission.PRODUCT_DELETE + "')")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            productService.delete(id);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Produk berhasil dihapus");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return REDIRECT_PRODUCTS;
    }

    private void addFormAttributes(Model model) {
        List<ProductCategory> categories = categoryService.findAllActive();
        model.addAttribute("categories", categories);
        model.addAttribute("costingMethods", CostingMethod.values());
        model.addAttribute("inventoryAccounts", chartOfAccountRepository.findAssetAccounts());
        model.addAttribute("cogsAccounts", chartOfAccountRepository.findExpenseAccounts());
        model.addAttribute("salesAccounts", chartOfAccountRepository.findRevenueAccounts());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_PRODUCTS);
    }
}
