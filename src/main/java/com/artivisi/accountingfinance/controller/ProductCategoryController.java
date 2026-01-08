package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.ProductCategory;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.ProductCategoryService;
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
@RequestMapping("/products/categories")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permission.PRODUCT_VIEW + "')")
public class ProductCategoryController {

    private static final String ATTR_SUCCESS_MESSAGE = "successMessage";
    private static final String REDIRECT_PRODUCT_CATEGORIES = "redirect:/products/categories";
    private static final String VIEW_FORM = "products/categories/form";

    private final ProductCategoryService categoryService;

    @GetMapping
    public String list(
            @RequestParam(required = false) String search,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            @PageableDefault(size = 20) Pageable pageable,
            Model model) {

        Page<ProductCategory> categories = categoryService.findBySearch(search, pageable);

        model.addAttribute("categories", categories);
        model.addAttribute("search", search);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_PRODUCT_CATEGORIES);

        if ("true".equals(hxRequest)) {
            return "products/categories/fragments/category-table :: table";
        }

        return "products/categories/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('" + Permission.PRODUCT_CREATE + "')")
    public String newForm(Model model) {
        ProductCategory category = new ProductCategory();
        category.setActive(true);

        model.addAttribute("category", category);
        addFormAttributes(model);
        return VIEW_FORM;
    }

    @PostMapping("/new")
    @PreAuthorize("hasAuthority('" + Permission.PRODUCT_CREATE + "')")
    public String create(
            @Valid @ModelAttribute("category") ProductCategory category,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            addFormAttributes(model);
            return VIEW_FORM;
        }

        try {
            categoryService.create(category);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Kategori produk berhasil ditambahkan");
            return REDIRECT_PRODUCT_CATEGORIES;
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

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('" + Permission.PRODUCT_EDIT + "')")
    public String editForm(@PathVariable UUID id, Model model) {
        ProductCategory category = categoryService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Kategori produk tidak ditemukan: " + id));

        model.addAttribute("category", category);
        addFormAttributes(model);
        return VIEW_FORM;
    }

    @PostMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('" + Permission.PRODUCT_EDIT + "')")
    public String update(
            @PathVariable UUID id,
            @Valid @ModelAttribute("category") ProductCategory category,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            addFormAttributes(model);
            return VIEW_FORM;
        }

        try {
            categoryService.update(id, category);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Kategori produk berhasil diubah");
            return REDIRECT_PRODUCT_CATEGORIES;
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

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('" + Permission.PRODUCT_DELETE + "')")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            categoryService.delete(id);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Kategori produk berhasil dihapus");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return REDIRECT_PRODUCT_CATEGORIES;
    }

    private void addFormAttributes(Model model) {
        List<ProductCategory> parentCategories = categoryService.findAllActive();
        model.addAttribute("parentCategories", parentCategories);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_PRODUCT_CATEGORIES);
    }
}
