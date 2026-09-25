package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.CostingMethod;
import com.artivisi.accountingfinance.entity.Product;
import com.artivisi.accountingfinance.entity.ProductCategory;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.ProductCategoryService;
import com.artivisi.accountingfinance.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
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

import java.math.BigDecimal;
import java.util.ArrayList;
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

    @Getter
    @Setter
    static class ProductForm {
        private UUID id;

        @NotBlank(message = "Kode produk wajib diisi")
        @Size(max = 50, message = "Kode produk maksimal 50 karakter")
        private String code;

        @NotBlank(message = "Nama produk wajib diisi")
        @Size(max = 200, message = "Nama produk maksimal 200 karakter")
        private String name;

        @Size(max = 500, message = "Deskripsi maksimal 500 karakter")
        private String description;

        @NotBlank(message = "Satuan wajib diisi")
        @Size(max = 20, message = "Satuan maksimal 20 karakter")
        private String unit;

        @NotNull(message = "Metode perhitungan biaya wajib diisi")
        private CostingMethod costingMethod;

        private boolean trackInventory;

        @NotNull(message = "Stok minimum wajib diisi")
        @PositiveOrZero(message = "Stok minimum tidak boleh negatif")
        private BigDecimal minimumStock;

        @PositiveOrZero(message = "Harga jual tidak boleh negatif")
        private BigDecimal sellingPrice;
        private boolean active;

        // Dropdown references — Thymeleaf uses th:field="*{category}" with th:value="${cat.id}"
        private UUID category;
        private UUID inventoryAccount;
        private UUID cogsAccount;
        private UUID salesAccount;
    }

    private Product toEntity(ProductForm form) {
        Product entity = new Product();
        BeanUtils.copyProperties(form, entity, "id", "category", "inventoryAccount", "cogsAccount", "salesAccount");
        if (form.getCategory() != null) {
            entity.setCategory(categoryService.findById(form.getCategory())
                    .orElseThrow(() -> new IllegalArgumentException("Kategori produk tidak ditemukan: " + form.getCategory())));
        }
        entity.setInventoryAccount(findAccount(form.getInventoryAccount()));
        entity.setCogsAccount(findAccount(form.getCogsAccount()));
        entity.setSalesAccount(findAccount(form.getSalesAccount()));
        return entity;
    }

    private ChartOfAccount findAccount(UUID id) {
        if (id == null) {
            return null;
        }
        return chartOfAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Akun tidak ditemukan: " + id));
    }

    private ProductForm toForm(Product entity) {
        ProductForm form = new ProductForm();
        BeanUtils.copyProperties(entity, form, "category", "inventoryAccount", "cogsAccount", "salesAccount");
        if (entity.getCategory() != null) {
            form.setCategory(entity.getCategory().getId());
        }
        if (entity.getInventoryAccount() != null) {
            form.setInventoryAccount(entity.getInventoryAccount().getId());
        }
        if (entity.getCogsAccount() != null) {
            form.setCogsAccount(entity.getCogsAccount().getId());
        }
        if (entity.getSalesAccount() != null) {
            form.setSalesAccount(entity.getSalesAccount().getId());
        }
        return form;
    }

    /**
     * Typeahead search endpoint for the invoice-line product picker.
     * Returns at most 10 active products (with a salesAccount configured) matching
     * the search term, so a dropdown never grows past a usable size.
     */
    @org.springframework.web.bind.annotation.GetMapping("/search")
    @org.springframework.web.bind.annotation.ResponseBody
    @PreAuthorize("isAuthenticated()")
    public java.util.List<java.util.Map<String, Object>> search(
            @RequestParam(value = "q", required = false) String q) {
        var page = productService.findByFilters(
                q == null ? "" : q, null, true,
                org.springframework.data.domain.PageRequest.of(0, 10,
                        org.springframework.data.domain.Sort.by("name")));
        java.util.List<java.util.Map<String, Object>> results = new java.util.ArrayList<>();
        for (Product p : page.getContent()) {
            if (p.getSalesAccount() == null) continue;
            results.add(java.util.Map.of(
                    "id", p.getId().toString(),
                    "code", p.getCode() == null ? "" : p.getCode(),
                    "name", p.getName() == null ? "" : p.getName(),
                    "sellingPrice", p.getSellingPrice() == null
                            ? java.math.BigDecimal.ZERO : p.getSellingPrice()));
        }
        return results;
    }

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
        ProductForm form = new ProductForm();
        form.setCostingMethod(CostingMethod.WEIGHTED_AVERAGE);
        form.setTrackInventory(true);
        form.setActive(true);
        form.setMinimumStock(BigDecimal.ZERO);

        model.addAttribute(ATTR_PRODUCT, form);
        addFormAttributes(model, null);
        return VIEW_FORM;
    }

    @PostMapping("/new")
    @PreAuthorize("hasAuthority('" + Permission.PRODUCT_CREATE + "')")
    public String create(
            @Valid @ModelAttribute("product") ProductForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            addFormAttributes(model, form.getCategory());
            return VIEW_FORM;
        }

        try {
            Product product = toEntity(form);
            productService.create(product);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Produk berhasil ditambahkan");
            return REDIRECT_PRODUCTS;
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("Kode")) {
                bindingResult.rejectValue("code", "duplicate", e.getMessage());
            } else {
                bindingResult.reject("error", e.getMessage());
            }
            addFormAttributes(model, form.getCategory());
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
        Product existing = productService.findByIdWithDetails(id)
                .orElseThrow(() -> new IllegalArgumentException("Produk tidak ditemukan: " + id));

        ProductForm form = toForm(existing);
        model.addAttribute(ATTR_PRODUCT, form);
        addFormAttributes(model, form.getCategory());
        return VIEW_FORM;
    }

    @PostMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('" + Permission.PRODUCT_EDIT + "')")
    public String update(
            @PathVariable UUID id,
            @Valid @ModelAttribute("product") ProductForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            form.setId(id);
            addFormAttributes(model, form.getCategory());
            return VIEW_FORM;
        }

        try {
            Product product = toEntity(form);
            productService.update(id, product);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Produk berhasil diubah");
            return REDIRECT_PRODUCTS;
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("Kode")) {
                bindingResult.rejectValue("code", "duplicate", e.getMessage());
            } else {
                bindingResult.reject("error", e.getMessage());
            }
            form.setId(id);
            addFormAttributes(model, form.getCategory());
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

    private void addFormAttributes(Model model, UUID selectedCategoryId) {
        List<ProductCategory> categories = new ArrayList<>(categoryService.findAllActive());
        if (selectedCategoryId != null && categories.stream().noneMatch(c -> c.getId().equals(selectedCategoryId))) {
            categoryService.findById(selectedCategoryId).ifPresent(categories::add);
        }
        model.addAttribute("categories", categories);
        model.addAttribute("costingMethods", CostingMethod.values());
        model.addAttribute("inventoryAccounts", chartOfAccountRepository.findAssetAccounts());
        model.addAttribute("cogsAccounts", chartOfAccountRepository.findExpenseAccounts());
        model.addAttribute("salesAccounts", chartOfAccountRepository.findRevenueAccounts());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_PRODUCTS);
    }
}
