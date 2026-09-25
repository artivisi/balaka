package com.artivisi.accountingfinance.controller.api;

import com.artivisi.accountingfinance.dto.TaxDetailRequest;
import com.artivisi.accountingfinance.dto.TaxDetailResponse;
import com.artivisi.accountingfinance.entity.TaxTransactionDetail;
import com.artivisi.accountingfinance.service.TaxTransactionDetailService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Tax Details", description = "CRUD and bulk operations for tax transaction details (e-Faktur, e-Bupot)")
@RequiredArgsConstructor
@Slf4j
public class TaxDetailApiController {

    private final TaxTransactionDetailService taxDetailService;

    @GetMapping("/api/transactions/{id}/tax-details")
    @PreAuthorize("hasAuthority('SCOPE_transactions:post') and hasAuthority('TAX_EXPORT')")
    public ResponseEntity<List<TaxDetailResponse>> listTaxDetails(@PathVariable UUID id) {
        List<TaxTransactionDetail> details = taxDetailService.findByTransactionId(id);
        List<TaxDetailResponse> responses = details.stream()
                .map(TaxDetailResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/api/transactions/{id}/tax-details/{detailId}")
    @PreAuthorize("hasAuthority('SCOPE_transactions:post') and hasAuthority('TAX_EXPORT')")
    public ResponseEntity<TaxDetailResponse> getTaxDetail(
            @PathVariable UUID id,
            @PathVariable UUID detailId) {
        return ResponseEntity.ok(TaxDetailResponse.from(findOwnedDetail(id, detailId)));
    }

    @PostMapping("/api/transactions/{id}/tax-details")
    @PreAuthorize("hasAuthority('SCOPE_transactions:post') and hasAuthority('TAX_EXPORT') and hasAuthority('TRANSACTION_EDIT')")
    public ResponseEntity<TaxDetailResponse> createTaxDetail(
            @PathVariable UUID id,
            @Valid @RequestBody TaxDetailRequest request) {
        String username = getCurrentUsername();
        log.info("API: Create tax detail - transactionId={}, taxType={}, user={}",
                id, request.taxType(), username);

        TaxTransactionDetail detail = toEntity(request);
        TaxTransactionDetail saved = taxDetailService.save(id, detail);

        log.info("API: Tax detail created - id={}, transactionId={}", saved.getId(), id);
        return ResponseEntity.status(HttpStatus.CREATED).body(TaxDetailResponse.from(saved));
    }

    @PutMapping("/api/transactions/{id}/tax-details/{detailId}")
    @PreAuthorize("hasAuthority('SCOPE_transactions:post') and hasAuthority('TAX_EXPORT') and hasAuthority('TRANSACTION_EDIT')")
    public ResponseEntity<TaxDetailResponse> updateTaxDetail(
            @PathVariable UUID id,
            @PathVariable UUID detailId,
            @Valid @RequestBody TaxDetailRequest request) {
        String username = getCurrentUsername();
        log.info("API: Update tax detail - detailId={}, transactionId={}, user={}",
                detailId, id, username);

        findOwnedDetail(id, detailId);
        TaxTransactionDetail updated = toEntity(request);
        TaxTransactionDetail saved = taxDetailService.update(detailId, updated);

        log.info("API: Tax detail updated - id={}", saved.getId());
        return ResponseEntity.ok(TaxDetailResponse.from(saved));
    }

    @DeleteMapping("/api/transactions/{id}/tax-details/{detailId}")
    @PreAuthorize("hasAuthority('SCOPE_transactions:post') and hasAuthority('TAX_EXPORT') and hasAuthority('TRANSACTION_EDIT')")
    public ResponseEntity<Void> deleteTaxDetail(
            @PathVariable UUID id,
            @PathVariable UUID detailId) {
        String username = getCurrentUsername();
        log.info("API: Delete tax detail - detailId={}, transactionId={}, user={}",
                detailId, id, username);

        findOwnedDetail(id, detailId);
        taxDetailService.delete(detailId);

        log.info("API: Tax detail deleted - id={}", detailId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/tax-details/bulk")
    @PreAuthorize("hasAuthority('SCOPE_transactions:post') and hasAuthority('TAX_EXPORT') and hasAuthority('TRANSACTION_EDIT')")
    public ResponseEntity<BulkTaxDetailResponse> bulkCreate(
            @Valid @RequestBody BulkTaxDetailRequest request) {
        String username = getCurrentUsername();
        log.info("API: Bulk create tax details - count={}, user={}", request.items().size(), username);

        List<BulkResultItem> results = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;

        for (BulkTaxDetailItem item : request.items()) {
            try {
                TaxTransactionDetail detail = toEntity(item.detail());
                TaxTransactionDetail saved = taxDetailService.save(item.transactionId(), detail);
                results.add(new BulkResultItem(
                        item.transactionId(), saved.getId(), true, null));
                successCount++;
            } catch (Exception e) {
                results.add(new BulkResultItem(
                        item.transactionId(), null, false, e.getMessage()));
                failureCount++;
                log.warn("API: Bulk create failed for transactionId={}: {}",
                        item.transactionId(), e.getMessage());
            }
        }

        log.info("API: Bulk create completed - success={}, failure={}", successCount, failureCount);
        return ResponseEntity.ok(new BulkTaxDetailResponse(results, successCount, failureCount));
    }

    private TaxTransactionDetail findOwnedDetail(UUID transactionId, UUID detailId) {
        TaxTransactionDetail detail = taxDetailService.findById(detailId);
        if (!detail.getTransaction().getId().equals(transactionId)) {
            throw new IllegalArgumentException("Tax detail does not belong to transaction " + transactionId);
        }
        return detail;
    }

    private TaxTransactionDetail toEntity(TaxDetailRequest request) {
        TaxTransactionDetail detail = new TaxTransactionDetail();
        detail.setTaxType(request.taxType());
        detail.setCounterpartyName(request.counterpartyName());
        detail.setFakturNumber(request.fakturNumber());
        detail.setFakturDate(request.fakturDate());
        detail.setTransactionCode(request.transactionCode());
        detail.setDpp(request.dpp());
        detail.setPpn(request.ppn());
        detail.setPpnbm(request.ppnbm());
        detail.setBupotNumber(request.bupotNumber());
        detail.setTaxObjectCode(request.taxObjectCode());
        detail.setGrossAmount(request.grossAmount());
        detail.setTaxRate(request.taxRate());
        detail.setTaxAmount(request.taxAmount());
        detail.setCounterpartyNpwp(request.counterpartyNpwp());
        detail.setCounterpartyNitku(request.counterpartyNitku());
        detail.setCounterpartyNik(request.counterpartyNik());
        detail.setCounterpartyIdType(request.counterpartyIdType());
        detail.setCounterpartyAddress(request.counterpartyAddress());
        return detail;
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return auth.getName();
        }
        return "API";
    }

    // --- Nested DTOs for bulk operations ---

    public record BulkTaxDetailRequest(
            @NotEmpty(message = "Items wajib diisi")
            List<@Valid BulkTaxDetailItem> items
    ) {}

    public record BulkTaxDetailItem(
            @NotNull(message = "Transaction ID wajib diisi")
            UUID transactionId,
            @NotNull(message = "Detail wajib diisi")
            @Valid
            TaxDetailRequest detail
    ) {}

    public record BulkTaxDetailResponse(
            List<BulkResultItem> results,
            int successCount,
            int failureCount
    ) {}

    public record BulkResultItem(
            UUID transactionId,
            UUID detailId,
            boolean success,
            String errorMessage
    ) {}
}
