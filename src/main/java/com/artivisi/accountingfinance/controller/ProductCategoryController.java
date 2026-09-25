package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.ProductCategory;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.ProductCategoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.BeanUtils;
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

import java.util.ArrayList;
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

    @Getter
    @Setter
    static class ProductCategoryForm {
        private UUID id;

        @NotBlank(message = "Kode kategori wajib diisi")
        @Size(max = 20, message = "Kode kategori maksimal 20 karakter")
        private String code;

        @NotBlank(message = "Nama kategori wajib diisi")
        @Size(max = 100, message = "Nama kategori maksimal 100 karakter")
        private String name;

        @Size(max = 255, message = "Deskripsi maksimal 255 karakter")
        private String description;

        private UUID parent;

        private boolean active;
    }

    private ProductCategory toEntity(ProductCategoryForm form) {
        ProductCategory category = new ProductCategory();
        BeanUtils.copyProperties(form, category, "id", "parent");
        if (form.getParent() != null) {
            ProductCategory parentCategory = new ProductCategory();
            parentCategory.setId(form.getParent());
            category.setParent(parentCategory);
        }
        return category;
    }

    private ProductCategoryForm toForm(ProductCategory category) {
        ProductCategoryForm form = new ProductCategoryForm();
        BeanUtils.copyProperties(category, form, "parent");
        if (category.getParent() != null) {
            form.setParent(category.getParent().getId());
        }
        return form;
    }

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
        ProductCategoryForm form = new ProductCategoryForm();
        form.setActive(true);

        model.addAttribute("category", form);
        addFormAttributes(model, null);
        return VIEW_FORM;
    }

    @PostMapping("/new")
    @PreAuthorize("hasAuthority('" + Permission.PRODUCT_CREATE + "')")
    public String create(
            @Valid @ModelAttribute("category") ProductCategoryForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            addFormAttributes(model, form.getParent());
            return VIEW_FORM;
        }

        try {
            ProductCategory category = toEntity(form);
            categoryService.create(category);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Kategori produk berhasil ditambahkan");
            return REDIRECT_PRODUCT_CATEGORIES;
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("Kode")) {
                bindingResult.rejectValue("code", "duplicate", e.getMessage());
            } else {
                bindingResult.reject("error", e.getMessage());
            }
            addFormAttributes(model, form.getParent());
            return VIEW_FORM;
        }
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('" + Permission.PRODUCT_EDIT + "')")
    public String editForm(@PathVariable UUID id, Model model) {
        ProductCategory category = categoryService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Kategori produk tidak ditemukan: " + id));

        ProductCategoryForm form = toForm(category);
        model.addAttribute("category", form);
        addFormAttributes(model, form.getParent());
        return VIEW_FORM;
    }

    @PostMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('" + Permission.PRODUCT_EDIT + "')")
    public String update(
            @PathVariable UUID id,
            @Valid @ModelAttribute("category") ProductCategoryForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            form.setId(id);
            addFormAttributes(model, form.getParent());
            return VIEW_FORM;
        }

        try {
            ProductCategory category = toEntity(form);
            categoryService.update(id, category);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Kategori produk berhasil diubah");
            return REDIRECT_PRODUCT_CATEGORIES;
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("Kode")) {
                bindingResult.rejectValue("code", "duplicate", e.getMessage());
            } else {
                bindingResult.reject("error", e.getMessage());
            }
            form.setId(id);
            addFormAttributes(model, form.getParent());
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

    private void addFormAttributes(Model model, UUID selectedParentId) {
        List<ProductCategory> parentCategories = new ArrayList<>(categoryService.findAllActive());
        if (selectedParentId != null && parentCategories.stream().noneMatch(c -> c.getId().equals(selectedParentId))) {
            categoryService.findById(selectedParentId).ifPresent(parentCategories::add);
        }
        model.addAttribute("parentCategories", parentCategories);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_PRODUCT_CATEGORIES);
    }
}
