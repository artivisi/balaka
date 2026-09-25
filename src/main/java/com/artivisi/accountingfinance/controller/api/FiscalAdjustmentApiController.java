package com.artivisi.accountingfinance.controller.api;

import com.artivisi.accountingfinance.entity.FiscalAdjustment;
import com.artivisi.accountingfinance.entity.FiscalLossCarryforward;
import com.artivisi.accountingfinance.enums.FiscalAdjustmentCategory;
import com.artivisi.accountingfinance.enums.FiscalAdjustmentDirection;
import com.artivisi.accountingfinance.service.SptTahunanExportService;
import com.artivisi.accountingfinance.service.SptTahunanExportService.LossCarryforwardReport;
import com.artivisi.accountingfinance.service.TaxReportDetailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/fiscal-adjustments")
@Tag(name = "Fiscal Adjustments", description = "CRUD for fiscal adjustment entries (koreksi fiskal)")
@PreAuthorize("hasAuthority('SCOPE_tax-export:read')")
@RequiredArgsConstructor
@Slf4j
public class FiscalAdjustmentApiController {

    private final TaxReportDetailService taxReportDetailService;
    private final SptTahunanExportService sptTahunanExportService;

    @GetMapping
    @Operation(summary = "List fiscal adjustments by year")
    @ApiResponse(responseCode = "200", description = "List of adjustments")
    public ResponseEntity<List<FiscalAdjustmentResponse>> list(@RequestParam int year) {
        List<FiscalAdjustmentResponse> responses = taxReportDetailService
                .findAdjustmentsByYear(year)
                .stream()
                .map(FiscalAdjustmentResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_tax-export:write')")
    @Operation(summary = "Create a fiscal adjustment")
    @ApiResponse(responseCode = "201", description = "Adjustment created")
    public ResponseEntity<FiscalAdjustmentResponse> create(
            @Valid @RequestBody FiscalAdjustmentRequest request) {
        log.info("API: Create fiscal adjustment - year={}, description={}", request.year(), request.description());

        FiscalAdjustment entity = toEntity(request);
        FiscalAdjustment saved = taxReportDetailService.saveAdjustment(entity);

        log.info("API: Fiscal adjustment created - id={}", saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(FiscalAdjustmentResponse.from(saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_tax-export:write')")
    @Operation(summary = "Update a fiscal adjustment")
    @ApiResponse(responseCode = "200", description = "Adjustment updated")
    @ApiResponse(responseCode = "404", description = "Adjustment not found")
    public ResponseEntity<FiscalAdjustmentResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody FiscalAdjustmentRequest request) {
        log.info("API: Update fiscal adjustment - id={}", id);

        FiscalAdjustment existing = taxReportDetailService.findAdjustmentById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }

        existing.setYear(request.year());
        existing.setDescription(request.description());
        existing.setAdjustmentCategory(request.adjustmentCategory());
        existing.setAdjustmentDirection(request.adjustmentDirection());
        existing.setAmount(request.amount());
        existing.setAccountCode(request.accountCode());
        existing.setPasal(request.pasal());
        existing.setNotes(request.notes());

        FiscalAdjustment saved = taxReportDetailService.saveAdjustment(existing);

        log.info("API: Fiscal adjustment updated - id={}", saved.getId());
        return ResponseEntity.ok(FiscalAdjustmentResponse.from(saved));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_tax-export:write')")
    @Operation(summary = "Delete a fiscal adjustment")
    @ApiResponse(responseCode = "204", description = "Adjustment deleted")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        log.info("API: Delete fiscal adjustment - id={}", id);

        taxReportDetailService.deleteAdjustment(id);

        log.info("API: Fiscal adjustment deleted - id={}", id);
        return ResponseEntity.noContent().build();
    }

    // ==================== LOSS CARRYFORWARD ====================

    @GetMapping("/loss-carryforward")
    @Operation(summary = "List fiscal loss carryforwards with active balances for a year")
    @ApiResponse(responseCode = "200", description = "Loss carryforward report")
    public ResponseEntity<LossCarryforwardReport> getLossCarryforward(@RequestParam int year) {
        return ResponseEntity.ok(sptTahunanExportService.generateLossCarryforward(year));
    }

    @PostMapping("/loss-carryforward")
    @PreAuthorize("hasAuthority('SCOPE_tax-export:write')")
    @Operation(summary = "Create a fiscal loss carryforward entry")
    @ApiResponse(responseCode = "201", description = "Loss carryforward created")
    public ResponseEntity<LossCarryforwardResponse> createLossCarryforward(
            @Valid @RequestBody LossCarryforwardRequest request) {
        log.info("API: Create loss carryforward - originYear={}, amount={}", request.originYear(), request.originalAmount());

        FiscalLossCarryforward entity = new FiscalLossCarryforward();
        entity.setOriginYear(request.originYear());
        entity.setOriginalAmount(request.originalAmount());
        entity.setNotes(request.notes());

        FiscalLossCarryforward saved = sptTahunanExportService.saveLossCarryforward(entity);

        log.info("API: Loss carryforward created - id={}", saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(LossCarryforwardResponse.from(saved));
    }

    @DeleteMapping("/loss-carryforward/{id}")
    @PreAuthorize("hasAuthority('SCOPE_tax-export:write')")
    @Operation(summary = "Delete a fiscal loss carryforward entry")
    @ApiResponse(responseCode = "204", description = "Loss carryforward deleted")
    public ResponseEntity<Void> deleteLossCarryforward(@PathVariable UUID id) {
        log.info("API: Delete loss carryforward - id={}", id);
        sptTahunanExportService.deleteLossCarryforward(id);
        log.info("API: Loss carryforward deleted - id={}", id);
        return ResponseEntity.noContent().build();
    }

    // ==================== HELPERS ====================

    private FiscalAdjustment toEntity(FiscalAdjustmentRequest request) {
        FiscalAdjustment entity = new FiscalAdjustment();
        entity.setYear(request.year());
        entity.setDescription(request.description());
        entity.setAdjustmentCategory(request.adjustmentCategory());
        entity.setAdjustmentDirection(request.adjustmentDirection());
        entity.setAmount(request.amount());
        entity.setAccountCode(request.accountCode());
        entity.setPasal(request.pasal());
        entity.setNotes(request.notes());
        return entity;
    }

    public record FiscalAdjustmentRequest(
            @NotNull(message = "Tahun wajib diisi")
            Integer year,

            @NotBlank(message = "Deskripsi wajib diisi")
            String description,

            @NotNull(message = "Kategori koreksi wajib diisi")
            FiscalAdjustmentCategory adjustmentCategory,

            @NotNull(message = "Arah koreksi wajib diisi")
            FiscalAdjustmentDirection adjustmentDirection,

            @NotNull(message = "Jumlah wajib diisi")
            @Positive(message = "Jumlah harus positif")
            BigDecimal amount,

            String accountCode,
            String pasal,
            String notes
    ) {}

    public record LossCarryforwardRequest(
            @NotNull(message = "Tahun asal rugi wajib diisi")
            Integer originYear,

            @NotNull(message = "Jumlah rugi wajib diisi")
            @Positive(message = "Jumlah rugi harus positif")
            BigDecimal originalAmount,

            String notes
    ) {}

    public record LossCarryforwardResponse(
            UUID id,
            int originYear,
            BigDecimal originalAmount,
            BigDecimal usedAmount,
            BigDecimal remainingAmount,
            int expiryYear,
            String notes
    ) {
        public static LossCarryforwardResponse from(FiscalLossCarryforward entity) {
            return new LossCarryforwardResponse(
                    entity.getId(),
                    entity.getOriginYear(),
                    entity.getOriginalAmount(),
                    entity.getUsedAmount(),
                    entity.getRemainingAmount(),
                    entity.getExpiryYear(),
                    entity.getNotes()
            );
        }
    }

    public record FiscalAdjustmentResponse(
            UUID id,
            int year,
            String description,
            FiscalAdjustmentCategory adjustmentCategory,
            FiscalAdjustmentDirection adjustmentDirection,
            BigDecimal amount,
            String accountCode,
            String pasal,
            String notes
    ) {
        public static FiscalAdjustmentResponse from(FiscalAdjustment entity) {
            return new FiscalAdjustmentResponse(
                    entity.getId(),
                    entity.getYear(),
                    entity.getDescription(),
                    entity.getAdjustmentCategory(),
                    entity.getAdjustmentDirection(),
                    entity.getAmount(),
                    entity.getAccountCode(),
                    entity.getPasal(),
                    entity.getNotes()
            );
        }
    }
}
