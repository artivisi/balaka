package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.AssetCategory;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.DepreciationMethod;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.AssetCategoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
import java.util.UUID;

import static com.artivisi.accountingfinance.controller.ViewConstants.*;

@Controller
@RequestMapping("/assets/categories")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permission.ASSET_VIEW + "')")
public class AssetCategoryController {

    private static final String ATTR_SUCCESS_MESSAGE = "successMessage";
    private static final String REDIRECT_ASSET_CATEGORIES = "redirect:/assets/categories";
    private static final String VIEW_FORM = "assets/categories/form";

    private final AssetCategoryService assetCategoryService;
    private final ChartOfAccountRepository chartOfAccountRepository;

    @Getter
    @Setter
    static class AssetCategoryForm {
        private UUID id;

        @NotBlank(message = "Kode kategori wajib diisi")
        @Size(max = 20, message = "Kode kategori maksimal 20 karakter")
        private String code;

        @NotBlank(message = "Nama kategori wajib diisi")
        @Size(max = 100, message = "Nama kategori maksimal 100 karakter")
        private String name;

        @Size(max = 255, message = "Deskripsi maksimal 255 karakter")
        private String description;

        @NotNull(message = "Metode penyusutan wajib diisi")
        private DepreciationMethod depreciationMethod;

        @NotNull(message = "Umur ekonomis (bulan) wajib diisi")
        @Min(value = 1, message = "Umur ekonomis minimal 1 bulan")
        @Max(value = 600, message = "Umur ekonomis maksimal 600 bulan (50 tahun)")
        private Integer usefulLifeMonths;

        private BigDecimal depreciationRate;

        @NotNull(message = "Akun aset wajib diisi")
        private UUID assetAccount;

        @NotNull(message = "Akun akumulasi penyusutan wajib diisi")
        private UUID accumulatedDepreciationAccount;

        @NotNull(message = "Akun beban penyusutan wajib diisi")
        private UUID depreciationExpenseAccount;

        private Boolean active;
    }

    private AssetCategory toEntity(AssetCategoryForm form) {
        AssetCategory category = new AssetCategory();
        BeanUtils.copyProperties(form, category, "id", "active", "assetAccount", "accumulatedDepreciationAccount", "depreciationExpenseAccount");

        ChartOfAccount assetAcct = new ChartOfAccount();
        assetAcct.setId(form.getAssetAccount());
        category.setAssetAccount(assetAcct);

        ChartOfAccount accumAcct = new ChartOfAccount();
        accumAcct.setId(form.getAccumulatedDepreciationAccount());
        category.setAccumulatedDepreciationAccount(accumAcct);

        ChartOfAccount deprAcct = new ChartOfAccount();
        deprAcct.setId(form.getDepreciationExpenseAccount());
        category.setDepreciationExpenseAccount(deprAcct);

        return category;
    }

    private AssetCategoryForm toForm(AssetCategory category) {
        AssetCategoryForm form = new AssetCategoryForm();
        BeanUtils.copyProperties(category, form, "assetAccount", "accumulatedDepreciationAccount", "depreciationExpenseAccount");
        if (category.getAssetAccount() != null) {
            form.setAssetAccount(category.getAssetAccount().getId());
        }
        if (category.getAccumulatedDepreciationAccount() != null) {
            form.setAccumulatedDepreciationAccount(category.getAccumulatedDepreciationAccount().getId());
        }
        if (category.getDepreciationExpenseAccount() != null) {
            form.setDepreciationExpenseAccount(category.getDepreciationExpenseAccount().getId());
        }
        return form;
    }

    @GetMapping
    public String list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            @PageableDefault(size = 20) Pageable pageable,
            Model model) {

        Page<AssetCategory> categories = assetCategoryService.findByFilters(search, active, pageable);

        model.addAttribute("categories", categories);
        model.addAttribute("search", search);
        model.addAttribute("active", active);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_ASSETS);

        if ("true".equals(hxRequest)) {
            return "assets/categories/fragments/category-table :: table";
        }

        return "assets/categories/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('" + Permission.ASSET_CREATE + "')")
    public String newForm(Model model) {
        AssetCategoryForm form = new AssetCategoryForm();
        form.setDepreciationMethod(DepreciationMethod.STRAIGHT_LINE);
        form.setUsefulLifeMonths(48);
        form.setActive(true);

        model.addAttribute("category", form);
        addFormAttributes(model);
        return VIEW_FORM;
    }

    @PostMapping("/new")
    @PreAuthorize("hasAuthority('" + Permission.ASSET_CREATE + "')")
    public String create(
            @Valid @ModelAttribute("category") AssetCategoryForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            addFormAttributes(model);
            return VIEW_FORM;
        }

        try {
            AssetCategory category = toEntity(form);
            assetCategoryService.create(category);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Kategori aset berhasil ditambahkan");
            return REDIRECT_ASSET_CATEGORIES;
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
    @PreAuthorize("hasAuthority('" + Permission.ASSET_EDIT + "')")
    public String editForm(@PathVariable UUID id, Model model) {
        AssetCategory category = assetCategoryService.findById(id);
        model.addAttribute("category", toForm(category));
        addFormAttributes(model);
        return VIEW_FORM;
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permission.ASSET_EDIT + "')")
    public String update(
            @PathVariable UUID id,
            @Valid @ModelAttribute("category") AssetCategoryForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            form.setId(id);
            addFormAttributes(model);
            return VIEW_FORM;
        }

        try {
            AssetCategory category = toEntity(form);
            assetCategoryService.update(id, category);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Kategori aset berhasil diperbarui");
            return REDIRECT_ASSET_CATEGORIES;
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("Kode")) {
                bindingResult.rejectValue("code", "duplicate", e.getMessage());
            } else {
                bindingResult.reject("error", e.getMessage());
            }
            form.setId(id);
            addFormAttributes(model);
            return VIEW_FORM;
        }
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('" + Permission.ASSET_EDIT + "')")
    public String activate(
            @PathVariable UUID id,
            RedirectAttributes redirectAttributes) {

        assetCategoryService.activate(id);
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Kategori aset berhasil diaktifkan");
        return REDIRECT_ASSET_CATEGORIES;
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('" + Permission.ASSET_EDIT + "')")
    public String deactivate(
            @PathVariable UUID id,
            RedirectAttributes redirectAttributes) {

        try {
            assetCategoryService.deactivate(id);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Kategori aset berhasil dinonaktifkan");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return REDIRECT_ASSET_CATEGORIES;
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('" + Permission.ASSET_DELETE + "')")
    public String delete(
            @PathVariable UUID id,
            RedirectAttributes redirectAttributes) {

        try {
            assetCategoryService.delete(id);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Kategori aset berhasil dihapus");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return REDIRECT_ASSET_CATEGORIES;
    }

    private void addFormAttributes(Model model) {
        model.addAttribute("depreciationMethods", DepreciationMethod.values());
        model.addAttribute("assetAccounts", chartOfAccountRepository.findAssetAccounts());
        model.addAttribute("expenseAccounts", chartOfAccountRepository.findExpenseAccounts());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_ASSETS);
    }
}
