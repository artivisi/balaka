package com.artivisi.accountingfinance.controller.api;

import com.artivisi.accountingfinance.entity.AssetCondition;
import com.artivisi.accountingfinance.entity.AssetDisposal;
import com.artivisi.accountingfinance.entity.AssetDisposalStatus;
import com.artivisi.accountingfinance.entity.AssetMovement;
import com.artivisi.accountingfinance.entity.AssetMovementSource;
import com.artivisi.accountingfinance.entity.AssetVerification;
import com.artivisi.accountingfinance.entity.AssetVerificationFinding;
import com.artivisi.accountingfinance.entity.AssetVerificationResult;
import com.artivisi.accountingfinance.entity.DisposalType;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.service.AssetDisposalService;
import com.artivisi.accountingfinance.service.AssetRegisterQueryService;
import com.artivisi.accountingfinance.service.AssetVerificationService;
import com.artivisi.accountingfinance.service.AssetVerificationService.ChecklistRow;
import com.artivisi.accountingfinance.service.DisposalTaxCalculator.DisposalTaxEffect;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Disposal flow, yearly verification and register movement (issue #49). Same scopes as the asset API. */
@RestController
@RequestMapping("/api/fixed-assets")
@Tag(name = "Fixed Assets")
@RequiredArgsConstructor
public class AssetRegisterApiController {

    private static final String READ = "hasAuthority('SCOPE_assets:read')";
    private static final String DISPOSE = "hasAuthority('SCOPE_assets:write') and hasAuthority('ASSET_DISPOSE')";
    private static final String VERIFY = "hasAuthority('SCOPE_assets:write') and hasAuthority('ASSET_EDIT')";

    private final AssetDisposalService disposalService;
    private final AssetVerificationService verificationService;
    private final AssetRegisterQueryService queryService;

    // ==================== DISPOSAL ====================

    @GetMapping("/{id}/disposal/preview")
    @PreAuthorize(READ)
    @Operation(summary = "Preview gain or loss and tax effect of a disposal",
            description = "Nothing is stored. proceeds applies to SOLD only.")
    @ApiResponse(responseCode = "200", description = "Tax effect")
    @ApiResponse(responseCode = "400", description = "SOLD without proceeds, or proceeds on another type")
    public ResponseEntity<DisposalTaxEffect> preview(@PathVariable UUID id,
                                                     @RequestParam DisposalType type,
                                                     @RequestParam(required = false) BigDecimal proceeds) {
        return ResponseEntity.ok(disposalService.preview(id, type, proceeds));
    }

    @PostMapping("/{id}/disposal")
    @PreAuthorize(DISPOSE)
    @Operation(summary = "Create a disposal record and its DRAFT journal",
            description = "The asset becomes DISPOSED when the journal is posted through POST /api/transactions/{id}/post, "
                    + "and returns to the register when that journal is voided. Until then edits and depreciation are refused. "
                    + "Requires a posted acquisition, no PENDING depreciation, and an entry for every month before the disposal month.")
    @ApiResponse(responseCode = "201", description = "Record created; transactionId is the DRAFT journal")
    @ApiResponse(responseCode = "400", description = "Validation error")
    @ApiResponse(responseCode = "409", description = "Asset state does not allow disposal")
    public ResponseEntity<DisposalResponse> dispose(@PathVariable UUID id, @Valid @RequestBody DisposalRequest request,
                                                    Principal principal) {
        AssetDisposal created = disposalService.create(new AssetDisposalService.CreateDisposal(
                id, request.type(), request.date(), request.proceeds(), request.proceedsAccountId(),
                request.counterparty(), request.reason(), principal.getName()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(disposalService.findById(created.getId())));
    }

    @PostMapping("/disposals/{disposalId}/cancel")
    @PreAuthorize(DISPOSE)
    @Operation(summary = "Cancel a draft disposal", description = "Removes the DRAFT journal and keeps the record as CANCELLED.")
    @ApiResponse(responseCode = "200", description = "Cancelled")
    @ApiResponse(responseCode = "404", description = "Record not found")
    @ApiResponse(responseCode = "409", description = "Record is not a draft")
    public ResponseEntity<DisposalResponse> cancel(@PathVariable UUID disposalId) {
        disposalService.cancel(disposalId);
        return ResponseEntity.ok(toResponse(disposalService.findById(disposalId)));
    }

    @GetMapping("/disposals/{disposalId}")
    @PreAuthorize(READ)
    @Operation(summary = "Get a disposal record")
    @ApiResponse(responseCode = "200", description = "Disposal record")
    @ApiResponse(responseCode = "404", description = "Record not found")
    public ResponseEntity<DisposalResponse> disposal(@PathVariable UUID disposalId) {
        return ResponseEntity.ok(toResponse(disposalService.findById(disposalId)));
    }

    @GetMapping("/disposals")
    @PreAuthorize(READ)
    @Operation(summary = "List disposal records dated in a year", description = "Optional status filter.")
    @ApiResponse(responseCode = "200", description = "Disposal records")
    public ResponseEntity<List<DisposalResponse>> disposals(@RequestParam int year,
                                                            @RequestParam(required = false) AssetDisposalStatus status) {
        return ResponseEntity.ok(queryService.findDisposals(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), status)
                .stream().map(this::toResponse).toList());
    }

    // ==================== VERIFICATION ====================

    @PostMapping("/{id}/verification")
    @PreAuthorize(VERIFY)
    @Operation(summary = "Record the yearly check of an asset",
            description = "FOUND requires condition, location and custodian and writes them to the register. "
                    + "NOT_FOUND leaves the register unchanged. A second call for the same year replaces the result.")
    @ApiResponse(responseCode = "200", description = "Recorded")
    @ApiResponse(responseCode = "400", description = "Validation error")
    @ApiResponse(responseCode = "409", description = "Asset is disposed")
    public ResponseEntity<VerificationResponse> verify(@PathVariable UUID id, @Valid @RequestBody VerificationRequest request,
                                                       Principal principal) {
        AssetVerification saved = verificationService.record(new AssetVerificationService.RecordVerification(
                id, request.year(), request.date(), request.result(), request.condition(), request.location(),
                request.custodian(), request.notes(), principal.getName()));
        return ResponseEntity.ok(VerificationResponse.from(saved));
    }

    @GetMapping("/verification")
    @PreAuthorize(READ)
    @Operation(summary = "Yearly checklist", description = "Every asset bought by year end and not disposed by year end.")
    @ApiResponse(responseCode = "200", description = "Checklist rows")
    public ResponseEntity<List<ChecklistResponse>> checklist(@RequestParam int year) {
        return ResponseEntity.ok(verificationService.checklist(year).stream().map(ChecklistResponse::from).toList());
    }

    @GetMapping("/verification/exceptions")
    @PreAuthorize(READ)
    @Operation(summary = "Yearly exceptions", description = "Checklist rows with an open exception: not verified, "
            + "not found, major damage, or a missing identity field.")
    @ApiResponse(responseCode = "200", description = "Rows with exceptions")
    public ResponseEntity<List<ChecklistResponse>> exceptions(@RequestParam int year) {
        return ResponseEntity.ok(verificationService.exceptions(year).stream().map(ChecklistResponse::from).toList());
    }

    // ==================== MOVEMENT ====================

    @GetMapping("/movement")
    @PreAuthorize(READ)
    @Operation(summary = "Yearly cost and accumulated depreciation roll-forward per category")
    @ApiResponse(responseCode = "200", description = "Roll-forward")
    public ResponseEntity<AssetRegisterQueryService.CostMovement> movement(@RequestParam int year) {
        return ResponseEntity.ok(queryService.costMovement(year));
    }

    @GetMapping("/register-movements")
    @PreAuthorize(READ)
    @Operation(summary = "Custodian, location and condition changes dated in a year")
    @ApiResponse(responseCode = "200", description = "Changes")
    public ResponseEntity<List<RegisterMovementResponse>> registerMovements(@RequestParam int year) {
        return ResponseEntity.ok(queryService.findMovements(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
                .stream().map(RegisterMovementResponse::from).toList());
    }

    private DisposalResponse toResponse(AssetDisposal d) {
        return new DisposalResponse(d.getId(), d.getRecordNumber(), d.getFixedAsset().getId(), d.getFixedAsset().getAssetCode(),
                d.getDisposalType(), d.getDisposalDate(), d.getReason(), d.getCounterparty(), d.getProceeds(),
                d.getPurchaseCost(), d.getAccumulatedDepreciation(), d.getBookValue(), d.getGainLoss(),
                d.getProceedsAccount() != null ? d.getProceedsAccount().getAccountCode() : null,
                d.getTransaction() != null ? d.getTransaction().getId() : null,
                d.getTransaction() != null ? d.getTransaction().getStatus() : null,
                d.getStatus(), d.getCreatedBy(), disposalService.taxEffect(d));
    }

    // ==================== DTOs ====================

    public record DisposalRequest(
            @NotNull(message = "Tipe pelepasan wajib diisi") DisposalType type,
            @NotNull(message = "Tanggal pelepasan wajib diisi") LocalDate date,
            BigDecimal proceeds,
            UUID proceedsAccountId,
            @Size(max = 255, message = "Nama pembeli/penerima maksimal 255 karakter") String counterparty,
            @NotBlank(message = "Alasan pelepasan wajib diisi")
            @Size(max = 500, message = "Alasan maksimal 500 karakter") String reason) {
    }

    public record DisposalResponse(UUID id, String recordNumber, UUID assetId, String assetCode, DisposalType type,
                                   LocalDate date, String reason, String counterparty, BigDecimal proceeds,
                                   BigDecimal purchaseCost, BigDecimal accumulatedDepreciation, BigDecimal bookValue,
                                   BigDecimal gainLoss, String proceedsAccountCode, UUID transactionId,
                                   TransactionStatus transactionStatus, AssetDisposalStatus status, String createdBy,
                                   DisposalTaxEffect taxEffect) {
    }

    public record VerificationRequest(
            @NotNull(message = "Tahun wajib diisi") Integer year,
            @NotNull(message = "Tanggal verifikasi wajib diisi") LocalDate date,
            @NotNull(message = "Hasil verifikasi wajib diisi") AssetVerificationResult result,
            AssetCondition condition,
            @Size(max = 100, message = "Lokasi maksimal 100 karakter") String location,
            @Size(max = 150, message = "Pemegang maksimal 150 karakter") String custodian,
            @Size(max = 500, message = "Catatan maksimal 500 karakter") String notes) {
    }

    public record VerificationResponse(int year, LocalDate date, AssetVerificationResult result, AssetCondition condition,
                                       String location, String custodian, String notes, String verifiedBy) {
        static VerificationResponse from(AssetVerification v) {
            return new VerificationResponse(v.getVerificationYear(), v.getVerifiedDate(), v.getResult(), v.getCondition(),
                    v.getLocation(), v.getCustodian(), v.getNotes(), v.getVerifiedBy());
        }
    }

    public record ChecklistResponse(UUID assetId, String assetCode, String name, String brand, String model, String serialNumber,
                                    String custodian, String location, AssetCondition condition, LocalDate lastVerifiedDate,
                                    VerificationResponse verification, List<AssetVerificationFinding> exceptions) {
        static ChecklistResponse from(ChecklistRow row) {
            var a = row.asset();
            return new ChecklistResponse(a.getId(), a.getAssetCode(), a.getName(), a.getBrand(), a.getModel(), a.getSerialNumber(),
                    a.getCustodian(), a.getLocation(), a.getCondition(), a.getLastVerifiedDate(),
                    row.verification() != null ? VerificationResponse.from(row.verification()) : null, row.exceptions());
        }
    }

    public record RegisterMovementResponse(UUID assetId, String assetCode, LocalDate date, AssetMovementSource source,
                                           String fromCustodian, String toCustodian, String fromLocation, String toLocation,
                                           AssetCondition fromCondition, AssetCondition toCondition, String recordedBy) {
        static RegisterMovementResponse from(AssetMovement m) {
            return new RegisterMovementResponse(m.getFixedAsset().getId(), m.getFixedAsset().getAssetCode(), m.getMovementDate(),
                    m.getSource(), m.getFromCustodian(), m.getToCustodian(), m.getFromLocation(), m.getToLocation(),
                    m.getFromCondition(), m.getToCondition(), m.getRecordedBy());
        }
    }
}
