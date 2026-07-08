package com.artivisi.accountingfinance.controller.api;

import com.artivisi.accountingfinance.entity.AssetCategory;
import com.artivisi.accountingfinance.entity.AssetStatus;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.DepreciationMethod;
import com.artivisi.accountingfinance.entity.FixedAsset;
import com.artivisi.accountingfinance.security.LogSanitizer;
import com.artivisi.accountingfinance.service.AssetCategoryService;
import com.artivisi.accountingfinance.service.ChartOfAccountService;
import com.artivisi.accountingfinance.service.FixedAssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * REST API for fixed-asset registration (issue #30). Registered assets are picked
 * up by the monthly depreciation scheduler, so API-registered purchases get
 * auto-generated depreciation entries without direct DB inserts.
 */
@RestController
@RequestMapping("/api/fixed-assets")
@Tag(name = "Fixed Assets", description = "Register and manage depreciable fixed assets. "
        + "Depreciation settings default from the asset category (kelompok); registered "
        + "assets are picked up by the monthly depreciation scheduler.")
@RequiredArgsConstructor
@Slf4j
public class FixedAssetApiController {

    private final FixedAssetService fixedAssetService;
    private final AssetCategoryService assetCategoryService;
    private final ChartOfAccountService chartOfAccountService;

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_assets:read')")
    @Operation(summary = "List fixed assets",
            description = "Optional filters: purchase year, category, status. Paginated.")
    @ApiResponse(responseCode = "200", description = "Page of fixed assets")
    public ResponseEntity<Page<FixedAssetResponse>> list(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) AssetStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<FixedAssetResponse> page = fixedAssetService
                .findByApiFilters(year, categoryId, status, pageable)
                .map(FixedAssetResponse::from);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_assets:read')")
    @Operation(summary = "Get fixed asset detail")
    @ApiResponse(responseCode = "200", description = "Fixed asset detail")
    @ApiResponse(responseCode = "404", description = "Asset not found")
    public ResponseEntity<FixedAssetDetailResponse> detail(@PathVariable UUID id) {
        FixedAsset asset = fixedAssetService.findByIdWithDetails(id);
        return ResponseEntity.ok(FixedAssetDetailResponse.from(
                asset, fixedAssetService.calculateMonthlyDepreciation(asset)));
    }

    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('SCOPE_assets:read')")
    @Operation(summary = "List active asset categories",
            description = "Categories (kelompok) supply default depreciation method, useful life, "
                    + "rate, and the asset/accumulated-depreciation/expense accounts.")
    @ApiResponse(responseCode = "200", description = "Active asset categories")
    public ResponseEntity<List<AssetCategoryResponse>> categories() {
        List<AssetCategoryResponse> responses = assetCategoryService.findAllActive()
                .stream()
                .map(AssetCategoryResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_assets:write')")
    @Operation(summary = "Register a fixed asset",
            description = "Exactly one acquisition source is required: fundingAccountId/fundingAccountCode "
                    + "(composes an acquisition DRAFT journal: Dr asset / Cr funding) or purchaseTransactionId "
                    + "(links an already-posted purchase journal without creating a new one). "
                    + "depreciationMethod, usefulLifeMonths, and depreciationRate default from the category when omitted.")
    @ApiResponse(responseCode = "201", description = "Asset registered")
    @ApiResponse(responseCode = "400", description = "Validation error")
    public ResponseEntity<FixedAssetDetailResponse> create(@Valid @RequestBody FixedAssetRequest request) {
        log.info("API: Register fixed asset - code={}, name={}",
                LogSanitizer.sanitize(request.assetCode()), LogSanitizer.sanitize(request.name()));

        UUID fundingAccountId = resolveFundingAccountId(request);
        if (fundingAccountId == null && request.purchaseTransactionId() == null) {
            throw new IllegalArgumentException(
                    "Sumber perolehan wajib diisi: fundingAccountId/fundingAccountCode "
                            + "(membuat jurnal pembelian DRAFT) atau purchaseTransactionId "
                            + "(menautkan jurnal pembelian yang sudah ada)");
        }

        FixedAsset asset = toEntity(request, fundingAccountId);
        FixedAsset saved = fixedAssetService.create(asset, request.purchaseTransactionId());

        FixedAsset detail = fixedAssetService.findByIdWithDetails(saved.getId());
        log.info("API: Fixed asset registered - id={}, code={}", saved.getId(), LogSanitizer.sanitize(saved.getAssetCode()));
        return ResponseEntity.status(HttpStatus.CREATED).body(FixedAssetDetailResponse.from(
                detail, fixedAssetService.calculateMonthlyDepreciation(detail)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_assets:write')")
    @Operation(summary = "Update a fixed asset",
            description = "Assets with recorded depreciation only accept name, description, "
                    + "location, serialNumber, and notes changes. Funding/purchase linkage is immutable.")
    @ApiResponse(responseCode = "200", description = "Asset updated")
    @ApiResponse(responseCode = "404", description = "Asset not found")
    @ApiResponse(responseCode = "409", description = "Asset state does not allow the update")
    public ResponseEntity<FixedAssetDetailResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody FixedAssetRequest request) {
        log.info("API: Update fixed asset - id={}", id);

        FixedAsset saved = fixedAssetService.update(id, toEntity(request, null));

        FixedAsset detail = fixedAssetService.findByIdWithDetails(saved.getId());
        log.info("API: Fixed asset updated - id={}, code={}", saved.getId(), LogSanitizer.sanitize(saved.getAssetCode()));
        return ResponseEntity.ok(FixedAssetDetailResponse.from(
                detail, fixedAssetService.calculateMonthlyDepreciation(detail)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_assets:write')")
    @Operation(summary = "Delete a fixed asset",
            description = "Only allowed for assets without depreciation history and without a purchase transaction.")
    @ApiResponse(responseCode = "204", description = "Asset deleted")
    @ApiResponse(responseCode = "404", description = "Asset not found")
    @ApiResponse(responseCode = "409", description = "Asset state does not allow deletion")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        log.info("API: Delete fixed asset - id={}", id);
        fixedAssetService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== HELPERS ====================

    private UUID resolveFundingAccountId(FixedAssetRequest request) {
        if (request.fundingAccountId() != null && request.fundingAccountCode() != null) {
            throw new IllegalArgumentException("Pilih salah satu: fundingAccountId atau fundingAccountCode, tidak keduanya");
        }
        if (request.fundingAccountId() != null) {
            return request.fundingAccountId();
        }
        if (request.fundingAccountCode() != null) {
            return chartOfAccountService.findByAccountCode(request.fundingAccountCode()).getId();
        }
        return null;
    }

    private AssetCategory resolveCategory(FixedAssetRequest request) {
        if (request.categoryId() != null && request.categoryCode() != null) {
            throw new IllegalArgumentException("Pilih salah satu: categoryId atau categoryCode, tidak keduanya");
        }
        if (request.categoryId() != null) {
            return assetCategoryService.findById(request.categoryId());
        }
        if (request.categoryCode() != null) {
            return assetCategoryService.findByCode(request.categoryCode());
        }
        throw new IllegalArgumentException("Kategori aset wajib diisi: categoryId atau categoryCode");
    }

    private FixedAsset toEntity(FixedAssetRequest request, UUID fundingAccountId) {
        AssetCategory category = resolveCategory(request);

        FixedAsset asset = new FixedAsset();
        asset.setAssetCode(request.assetCode());
        asset.setName(request.name());
        asset.setDescription(request.description());
        asset.setCategory(category);
        asset.setPurchaseDate(request.purchaseDate());
        asset.setPurchaseCost(request.purchaseCost());
        asset.setSupplier(request.supplier());
        asset.setInvoiceNumber(request.invoiceNumber());
        asset.setSerialNumber(request.serialNumber());
        asset.setLocation(request.location());
        asset.setNotes(request.notes());
        // null means "take the category default" (resolved in FixedAsset.initializeFromCategory);
        // for updates the category default is applied here since update() does not re-initialize
        asset.setDepreciationMethod(request.depreciationMethod() != null
                ? request.depreciationMethod() : category.getDepreciationMethod());
        asset.setUsefulLifeMonths(request.usefulLifeMonths() != null
                ? request.usefulLifeMonths() : category.getUsefulLifeMonths());
        asset.setDepreciationRate(request.depreciationRate() != null
                ? request.depreciationRate() : category.getDepreciationRate());
        asset.setResidualValue(request.residualValue() != null ? request.residualValue() : BigDecimal.ZERO);
        asset.setDepreciationStartDate(request.depreciationStartDate());
        asset.setAutoPost(Boolean.TRUE.equals(request.autoPost()));
        if (fundingAccountId != null) {
            asset.setFundingAccount(chartOfAccountService.findById(fundingAccountId));
        }
        return asset;
    }

    // ==================== DTOs ====================

    public record FixedAssetRequest(
            @NotBlank(message = "Kode aset wajib diisi")
            @Size(max = 30, message = "Kode aset maksimal 30 karakter")
            String assetCode,

            @NotBlank(message = "Nama aset wajib diisi")
            @Size(max = 255, message = "Nama aset maksimal 255 karakter")
            String name,

            @Size(max = 500, message = "Deskripsi maksimal 500 karakter")
            String description,

            UUID categoryId,
            String categoryCode,

            @NotNull(message = "Tanggal pembelian wajib diisi")
            LocalDate purchaseDate,

            @NotNull(message = "Nilai perolehan wajib diisi")
            @Positive(message = "Nilai perolehan harus positif")
            BigDecimal purchaseCost,

            @Size(max = 100, message = "Supplier maksimal 100 karakter")
            String supplier,

            @Size(max = 100, message = "Nomor faktur maksimal 100 karakter")
            String invoiceNumber,

            @Size(max = 100, message = "Nomor seri maksimal 100 karakter")
            String serialNumber,

            @Size(max = 100, message = "Lokasi maksimal 100 karakter")
            String location,

            String notes,

            DepreciationMethod depreciationMethod,

            @Min(value = 1, message = "Umur ekonomis minimal 1 bulan")
            @Max(value = 600, message = "Umur ekonomis maksimal 600 bulan (50 tahun)")
            Integer usefulLifeMonths,

            @Positive(message = "Tarif penyusutan harus positif")
            BigDecimal depreciationRate,

            @PositiveOrZero(message = "Nilai residu tidak boleh negatif")
            BigDecimal residualValue,

            LocalDate depreciationStartDate,

            Boolean autoPost,

            UUID fundingAccountId,
            String fundingAccountCode,
            UUID purchaseTransactionId
    ) {}

    public record FixedAssetResponse(
            UUID id,
            String assetCode,
            String name,
            String categoryCode,
            String categoryName,
            LocalDate purchaseDate,
            BigDecimal purchaseCost,
            DepreciationMethod depreciationMethod,
            Integer usefulLifeMonths,
            BigDecimal depreciationRate,
            LocalDate depreciationStartDate,
            BigDecimal accumulatedDepreciation,
            BigDecimal bookValue,
            AssetStatus status
    ) {
        public static FixedAssetResponse from(FixedAsset asset) {
            return new FixedAssetResponse(
                    asset.getId(),
                    asset.getAssetCode(),
                    asset.getName(),
                    asset.getCategory().getCode(),
                    asset.getCategory().getName(),
                    asset.getPurchaseDate(),
                    asset.getPurchaseCost(),
                    asset.getDepreciationMethod(),
                    asset.getUsefulLifeMonths(),
                    asset.getDepreciationRate(),
                    asset.getDepreciationStartDate(),
                    asset.getAccumulatedDepreciation(),
                    asset.getBookValue(),
                    asset.getStatus()
            );
        }
    }

    public record FixedAssetDetailResponse(
            UUID id,
            String assetCode,
            String name,
            String description,
            UUID categoryId,
            String categoryCode,
            String categoryName,
            LocalDate purchaseDate,
            BigDecimal purchaseCost,
            String supplier,
            String invoiceNumber,
            String serialNumber,
            String location,
            String notes,
            DepreciationMethod depreciationMethod,
            Integer usefulLifeMonths,
            BigDecimal depreciationRate,
            BigDecimal residualValue,
            LocalDate depreciationStartDate,
            BigDecimal accumulatedDepreciation,
            BigDecimal bookValue,
            BigDecimal monthlyDepreciation,
            LocalDate lastDepreciationDate,
            Integer depreciationPeriodsCompleted,
            AssetStatus status,
            boolean autoPost,
            String assetAccountCode,
            String accumulatedDepreciationAccountCode,
            String depreciationExpenseAccountCode,
            UUID purchaseTransactionId
    ) {
        public static FixedAssetDetailResponse from(FixedAsset asset, BigDecimal monthlyDepreciation) {
            return new FixedAssetDetailResponse(
                    asset.getId(),
                    asset.getAssetCode(),
                    asset.getName(),
                    asset.getDescription(),
                    asset.getCategory().getId(),
                    asset.getCategory().getCode(),
                    asset.getCategory().getName(),
                    asset.getPurchaseDate(),
                    asset.getPurchaseCost(),
                    asset.getSupplier(),
                    asset.getInvoiceNumber(),
                    asset.getSerialNumber(),
                    asset.getLocation(),
                    asset.getNotes(),
                    asset.getDepreciationMethod(),
                    asset.getUsefulLifeMonths(),
                    asset.getDepreciationRate(),
                    asset.getResidualValue(),
                    asset.getDepreciationStartDate(),
                    asset.getAccumulatedDepreciation(),
                    asset.getBookValue(),
                    monthlyDepreciation,
                    asset.getLastDepreciationDate(),
                    asset.getDepreciationPeriodsCompleted(),
                    asset.getStatus(),
                    asset.isAutoPost(),
                    asset.getAssetAccount().getAccountCode(),
                    asset.getAccumulatedDepreciationAccount().getAccountCode(),
                    asset.getDepreciationExpenseAccount().getAccountCode(),
                    asset.getPurchaseTransaction() != null ? asset.getPurchaseTransaction().getId() : null
            );
        }
    }

    public record AssetCategoryResponse(
            UUID id,
            String code,
            String name,
            String description,
            DepreciationMethod depreciationMethod,
            Integer usefulLifeMonths,
            BigDecimal depreciationRate,
            String assetAccountCode,
            String accumulatedDepreciationAccountCode,
            String depreciationExpenseAccountCode
    ) {
        public static AssetCategoryResponse from(AssetCategory category) {
            return new AssetCategoryResponse(
                    category.getId(),
                    category.getCode(),
                    category.getName(),
                    category.getDescription(),
                    category.getDepreciationMethod(),
                    category.getUsefulLifeMonths(),
                    category.getDepreciationRate(),
                    accountCode(category.getAssetAccount()),
                    accountCode(category.getAccumulatedDepreciationAccount()),
                    accountCode(category.getDepreciationExpenseAccount())
            );
        }

        private static String accountCode(ChartOfAccount account) {
            return account != null ? account.getAccountCode() : null;
        }
    }
}
