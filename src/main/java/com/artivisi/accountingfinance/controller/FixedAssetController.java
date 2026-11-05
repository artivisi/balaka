package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.AssetCategory;
import com.artivisi.accountingfinance.entity.AssetCondition;
import com.artivisi.accountingfinance.entity.AssetStatus;
import com.artivisi.accountingfinance.entity.DepreciationEntry;
import com.artivisi.accountingfinance.entity.DepreciationMethod;
import com.artivisi.accountingfinance.entity.FixedAsset;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.AssetCategoryService;
import com.artivisi.accountingfinance.service.AssetRegisterQueryService;
import com.artivisi.accountingfinance.service.AssetVerificationService;
import com.artivisi.accountingfinance.service.FixedAssetService;
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
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static com.artivisi.accountingfinance.controller.ViewConstants.*;

@Controller
@RequestMapping("/assets")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permission.ASSET_VIEW + "')")
public class FixedAssetController {

    private static final String ATTR_ASSET = "asset";
    private static final String ATTR_ASSETS = "assets";
    private static final String ATTR_SUCCESS_MESSAGE = "successMessage";
    private static final String ATTR_ERROR_MESSAGE = "errorMessage";
    private static final String REDIRECT_ASSETS = "redirect:/assets";
    private static final String REDIRECT_ASSETS_DEPRECIATION = "redirect:/assets/depreciation";
    private static final String VIEW_FORM = "assets/form";

    private final FixedAssetService fixedAssetService;
    private final AssetCategoryService assetCategoryService;
    private final AssetRegisterQueryService assetRegisterQueryService;
    private final AssetVerificationService assetVerificationService;
    private final com.artivisi.accountingfinance.service.ChartOfAccountService chartOfAccountService;

    @Getter
    @Setter
    static class FixedAssetForm {
        private UUID id;

        @NotBlank(message = "Kode aset wajib diisi")
        @Size(max = 30, message = "Kode aset maksimal 30 karakter")
        private String assetCode;

        @NotBlank(message = "Nama aset wajib diisi")
        @Size(max = 255, message = "Nama aset maksimal 255 karakter")
        private String name;

        @Size(max = 500, message = "Deskripsi maksimal 500 karakter")
        private String description;

        @NotNull(message = "Kategori aset wajib diisi")
        private UUID category;

        @NotNull(message = "Tanggal pembelian wajib diisi")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate purchaseDate;

        @NotNull(message = "Nilai perolehan wajib diisi")
        private BigDecimal purchaseCost;

        @Size(max = 100, message = "Supplier maksimal 100 karakter")
        private String supplier;

        @Size(max = 100, message = "Nomor faktur maksimal 100 karakter")
        private String invoiceNumber;

        @NotNull(message = "Metode penyusutan wajib diisi")
        private DepreciationMethod depreciationMethod;

        @NotNull(message = "Umur ekonomis (bulan) wajib diisi")
        @Min(value = 1, message = "Umur ekonomis minimal 1 bulan")
        @Max(value = 600, message = "Umur ekonomis maksimal 600 bulan (50 tahun)")
        private Integer usefulLifeMonths;

        @NotNull(message = "Nilai residu wajib diisi")
        private BigDecimal residualValue;

        private BigDecimal depreciationRate;

        @NotNull(message = "Tanggal mulai penyusutan wajib diisi")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate depreciationStartDate;

        @Size(max = 100, message = "Lokasi maksimal 100 karakter")
        private String location;

        @Size(max = 100, message = "Nomor seri maksimal 100 karakter")
        private String serialNumber;

        @Size(max = 100, message = "Merek maksimal 100 karakter")
        private String brand;

        @Size(max = 100, message = "Model maksimal 100 karakter")
        private String model;

        @Size(max = 150, message = "Pemegang maksimal 150 karakter")
        private String custodian;

        private AssetCondition condition;

        private String notes;

        // Bank/AP credited when the acquisition DRAFT posts (required at create time).
        private UUID fundingAccount;
        private String fundingAccountLabel;

        // When true, the depreciation scheduler posts entries directly; else PENDING.
        private boolean autoPost;
    }

    private FixedAsset toEntity(FixedAssetForm form) {
        FixedAsset entity = new FixedAsset();
        BeanUtils.copyProperties(form, entity, "id", "category", "fundingAccount");
        if (form.getCategory() != null) {
            AssetCategory cat = new AssetCategory();
            cat.setId(form.getCategory());
            entity.setCategory(cat);
        }
        if (form.getFundingAccount() != null) {
            entity.setFundingAccount(chartOfAccountService.findById(form.getFundingAccount()));
        }
        return entity;
    }

    private FixedAssetForm toForm(FixedAsset entity) {
        FixedAssetForm form = new FixedAssetForm();
        BeanUtils.copyProperties(entity, form, "category", "fundingAccount");
        if (entity.getCategory() != null) {
            form.setCategory(entity.getCategory().getId());
        }
        if (entity.getFundingAccount() != null) {
            form.setFundingAccount(entity.getFundingAccount().getId());
            var acc = entity.getFundingAccount();
            form.setFundingAccountLabel(
                    (acc.getAccountCode() == null ? "" : acc.getAccountCode())
                            + " - " + (acc.getAccountName() == null ? "" : acc.getAccountName()));
        }
        return form;
    }

    @GetMapping
    public String list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) AssetStatus status,
            @RequestParam(required = false) UUID categoryId,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            @PageableDefault(size = 20) Pageable pageable,
            Model model) {

        Page<FixedAsset> assets = fixedAssetService.findByFilters(search, status, categoryId, pageable);

        model.addAttribute(ATTR_ASSETS, assets);
        model.addAttribute("search", search);
        model.addAttribute("status", status);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("statuses", AssetStatus.values());
        model.addAttribute("categories", assetCategoryService.findAllActive());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_ASSETS);

        // Summary stats
        model.addAttribute("totalBookValue", fixedAssetService.getTotalBookValue());
        model.addAttribute("totalPurchaseCost", fixedAssetService.getTotalPurchaseCost());
        model.addAttribute("totalAccumulatedDepreciation", fixedAssetService.getTotalAccumulatedDepreciation());
        model.addAttribute("pendingDepreciationCount", fixedAssetService.countPendingDepreciationEntries());

        if ("true".equals(hxRequest)) {
            return "assets/fragments/asset-table :: table";
        }

        return "assets/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('" + Permission.ASSET_CREATE + "')")
    public String newForm(Model model) {
        FixedAssetForm form = new FixedAssetForm();
        form.setDepreciationMethod(DepreciationMethod.STRAIGHT_LINE);
        form.setResidualValue(BigDecimal.ZERO);
        form.setPurchaseDate(LocalDate.now());
        form.setDepreciationStartDate(LocalDate.now().withDayOfMonth(1));

        model.addAttribute(ATTR_ASSET, form);
        addFormAttributes(model);
        return VIEW_FORM;
    }

    @PostMapping("/new")
    @PreAuthorize("hasAuthority('" + Permission.ASSET_CREATE + "')")
    public String create(
            @Valid @ModelAttribute("asset") FixedAssetForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            restoreFundingAccountLabel(form);
            addFormAttributes(model);
            return VIEW_FORM;
        }

        try {
            FixedAsset asset = toEntity(form);
            FixedAsset saved = fixedAssetService.create(asset);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Aset berhasil ditambahkan");
            return REDIRECT_ASSETS + "/" + saved.getId();
        } catch (IllegalArgumentException | IllegalStateException e) {
            if (e.getMessage().contains("Kode aset")) {
                bindingResult.rejectValue("assetCode", "duplicate", e.getMessage());
            } else {
                bindingResult.reject("error", e.getMessage());
            }
            restoreFundingAccountLabel(form);
            addFormAttributes(model);
            return VIEW_FORM;
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        FixedAsset asset = fixedAssetService.findByIdWithDetails(id);
        List<DepreciationEntry> depreciationHistory = fixedAssetService.getDepreciationHistory(id);

        model.addAttribute(ATTR_ASSET, asset);
        model.addAttribute("depreciationHistory", depreciationHistory);
        model.addAttribute("monthlyDepreciation", fixedAssetService.calculateMonthlyDepreciation(asset));
        model.addAttribute("deletable", !asset.isDisposed() && fixedAssetService.isDeletable(asset));
        model.addAttribute("disposals", assetRegisterQueryService.findDisposalsOfAsset(id));
        model.addAttribute("draftDisposal", fixedAssetService.hasDraftDisposal(id));
        model.addAttribute("movements", assetRegisterQueryService.findMovementsOfAsset(id));
        model.addAttribute("verifications", assetVerificationService.historyOf(id));
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_ASSETS);
        return "assets/detail";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('" + Permission.ASSET_EDIT + "')")
    public String editForm(@PathVariable UUID id, Model model) {
        FixedAsset asset = fixedAssetService.findByIdWithDetails(id);
        model.addAttribute(ATTR_ASSET, toForm(asset));
        addFormAttributes(model);
        return VIEW_FORM;
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permission.ASSET_EDIT + "')")
    public String update(
            @PathVariable UUID id,
            @Valid @ModelAttribute("asset") FixedAssetForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            form.setId(id);
            restoreFundingAccountLabel(form);
            addFormAttributes(model);
            return VIEW_FORM;
        }

        try {
            FixedAsset asset = toEntity(form);
            fixedAssetService.update(id, asset);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Aset berhasil diperbarui");
            return REDIRECT_ASSETS + "/" + id;
        } catch (IllegalArgumentException | IllegalStateException e) {
            bindingResult.reject("error", e.getMessage());
            form.setId(id);
            restoreFundingAccountLabel(form);
            addFormAttributes(model);
            return VIEW_FORM;
        }
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('" + Permission.ASSET_DELETE + "')")
    public String delete(
            @PathVariable UUID id,
            RedirectAttributes redirectAttributes) {

        try {
            fixedAssetService.delete(id);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Aset berhasil dihapus");
            return REDIRECT_ASSETS;
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR_MESSAGE, e.getMessage());
            return REDIRECT_ASSETS + "/" + id;
        }
    }

    // Depreciation Management

    @GetMapping("/depreciation")
    @PreAuthorize("hasAuthority('" + Permission.ASSET_DEPRECIATE + "')")
    public String depreciationList(Model model) {
        List<DepreciationEntry> pendingEntries = fixedAssetService.getPendingDepreciationEntries();

        model.addAttribute("pendingEntries", pendingEntries);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_ASSETS);
        return "assets/depreciation";
    }

    @PostMapping("/depreciation/generate")
    @PreAuthorize("hasAuthority('" + Permission.ASSET_DEPRECIATE + "')")
    public String generateDepreciation(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth period,
            RedirectAttributes redirectAttributes) {

        List<DepreciationEntry> entries = fixedAssetService.generatePeriodEntries(period);

        if (entries.isEmpty()) {
            redirectAttributes.addFlashAttribute("infoMessage", "Tidak ada aset yang perlu disusutkan untuk periode " + period);
        } else {
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE,
                    "Berhasil generate " + entries.size() + " entri penyusutan untuk periode " + period);
        }
        return REDIRECT_ASSETS_DEPRECIATION;
    }

    @PostMapping("/depreciation/{entryId}/post")
    @PreAuthorize("hasAuthority('" + Permission.ASSET_DEPRECIATE + "')")
    public String postDepreciationEntry(
            @PathVariable UUID entryId,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        try {
            DepreciationEntry entry = fixedAssetService.postDepreciationEntry(entryId, authentication.getName());
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE,
                    "Penyusutan untuk " + entry.getFixedAsset().getName() + " berhasil di-posting");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR_MESSAGE, e.getMessage());
        }
        return REDIRECT_ASSETS_DEPRECIATION;
    }

    @PostMapping("/depreciation/{entryId}/skip")
    @PreAuthorize("hasAuthority('" + Permission.ASSET_DEPRECIATE + "')")
    public String skipDepreciationEntry(
            @PathVariable UUID entryId,
            RedirectAttributes redirectAttributes) {

        try {
            fixedAssetService.skipDepreciationEntry(entryId);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Entri penyusutan di-skip");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR_MESSAGE, e.getMessage());
        }
        return REDIRECT_ASSETS_DEPRECIATION;
    }

    @PostMapping("/depreciation/post-all")
    @PreAuthorize("hasAuthority('" + Permission.ASSET_DEPRECIATE + "')")
    public String postAllDepreciation(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth period,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        int count = fixedAssetService.postAllPendingDepreciation(period, authentication.getName());

        if (count == 0) {
            redirectAttributes.addFlashAttribute("infoMessage", "Tidak ada entri penyusutan yang perlu di-posting");
        } else {
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE,
                    "Berhasil posting " + count + " entri penyusutan");
        }
        return REDIRECT_ASSETS_DEPRECIATION;
    }

    // Photo

    @GetMapping("/{id}/photo")
    @ResponseBody
    public ResponseEntity<Resource> photo(@PathVariable UUID id) {
        FixedAssetService.AssetPhoto photo = fixedAssetService.loadPhoto(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .body(photo.resource());
    }

    @PostMapping("/{id}/photo")
    @PreAuthorize("hasAuthority('" + Permission.ASSET_EDIT + "')")
    public String uploadPhoto(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            Authentication authentication,
            RedirectAttributes redirectAttributes) throws IOException {
        try {
            fixedAssetService.attachPhoto(id, file, authentication.getName());
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Foto aset tersimpan");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR_MESSAGE, e.getMessage());
        }
        return REDIRECT_ASSETS + "/" + id;
    }

    @PostMapping("/{id}/photo/delete")
    @PreAuthorize("hasAuthority('" + Permission.ASSET_EDIT + "')")
    public String deletePhoto(@PathVariable UUID id, RedirectAttributes redirectAttributes) throws IOException {
        try {
            fixedAssetService.removePhoto(id);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Foto aset dihapus");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR_MESSAGE, e.getMessage());
        }
        return REDIRECT_ASSETS + "/" + id;
    }

    // The picker's visible label is not submitted; rebuild it so a re-rendered form keeps the selection.
    private void restoreFundingAccountLabel(FixedAssetForm form) {
        if (form.getFundingAccount() == null) {
            return;
        }
        var account = chartOfAccountService.findById(form.getFundingAccount());
        form.setFundingAccountLabel(account.getAccountCode() + " - " + account.getAccountName());
    }

    private void addFormAttributes(Model model) {
        model.addAttribute("categories", assetCategoryService.findAllActive());
        model.addAttribute("depreciationMethods", DepreciationMethod.values());
        model.addAttribute("conditions", AssetCondition.values());
        // Funding-account picker fetches via GET /accounts/search (combobox; ≤10 results).
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_ASSETS);
    }
}
