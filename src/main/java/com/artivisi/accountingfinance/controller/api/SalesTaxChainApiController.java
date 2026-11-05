package com.artivisi.accountingfinance.controller.api;

import com.artivisi.accountingfinance.entity.TaxTransactionDetail;
import com.artivisi.accountingfinance.enums.SalesTaxExceptionType;
import com.artivisi.accountingfinance.security.CurrentUser;
import com.artivisi.accountingfinance.service.InvoiceTaxLinkService;
import com.artivisi.accountingfinance.service.SalesTaxChainService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.format.annotation.DateTimeFormat;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/sales-tax-chain")
@Tag(name = "Sales Tax Chain", description = "Chain from invoice to faktur, receipt and bupot: per-invoice view, exception report, "
        + "monthly reconciliation of booked revenue to the DPP of SPT Masa PPN. A bupot is a DRAFT transaction until posted.")
@RequiredArgsConstructor
public class SalesTaxChainApiController {

    private static final String READ = "hasAuthority('SCOPE_sales-tax-chain:read')";
    private static final String WRITE = "hasAuthority('SCOPE_sales-tax-chain:write')";

    private final SalesTaxChainService chainService;
    private final InvoiceTaxLinkService linkService;

    @GetMapping("/invoices/{id}")
    @PreAuthorize(READ)
    @Operation(summary = "Chain of one invoice: transactions, faktur, receipts, bupot and open exceptions")
    public SalesTaxChainService.InvoiceChain chain(@PathVariable UUID id) {
        return chainService.getChain(id);
    }

    @GetMapping("/exceptions")
    @PreAuthorize(READ)
    @Operation(summary = "Open exceptions dated up to `to`; `from` and `type` narrow the list")
    public List<SalesTaxChainService.ChainIssue> exceptions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) SalesTaxExceptionType type) {
        return type == null ? chainService.findExceptions(from, to) : chainService.findExceptions(from, to, type);
    }

    @GetMapping("/reconciliation")
    @PreAuthorize(READ)
    @Operation(summary = "Booked revenue of a masa to the DPP of SPT Masa PPN, with PPN and PPh 23 effects")
    public SalesTaxChainService.Reconciliation reconciliation(@RequestParam int year, @RequestParam @Min(1) @Max(12) int month) {
        return chainService.getReconciliation(year, month);
    }

    @PostMapping("/invoices/{id}/faktur")
    @PreAuthorize(WRITE + " and hasAuthority('TAX_EXPORT') and hasAuthority('INVOICE_EDIT')")
    @Operation(summary = "Record the faktur of an invoice on its revenue transaction")
    public ResponseEntity<DetailResponse> recordFaktur(@PathVariable UUID id, @Valid @RequestBody FakturRequest request) {
        TaxTransactionDetail detail = linkService.recordFaktur(InvoiceTaxLinkService.RecordFaktur.builder()
                .invoiceId(id)
                .fakturNumber(request.fakturNumber())
                .fakturDate(request.fakturDate())
                .transactionCode(request.transactionCode())
                .dpp(request.dpp())
                .ppn(request.ppn())
                .build());
        return ResponseEntity.status(HttpStatus.CREATED).body(DetailResponse.from(detail));
    }

    @PostMapping("/invoices/{id}/bupot")
    @PreAuthorize(WRITE + " and hasAuthority('TAX_EXPORT') and hasAuthority('INVOICE_EDIT') and hasAuthority('TRANSACTION_CREATE')")
    @Operation(summary = "Record a customer's bupot: DRAFT Dr Kredit Pajak PPh 23, Cr Piutang, with its tax detail")
    public ResponseEntity<DetailResponse> recordBupot(@PathVariable UUID id, @Valid @RequestBody BupotRequest request) {
        TaxTransactionDetail detail = linkService.recordBupot(InvoiceTaxLinkService.RecordBupot.builder()
                .invoiceId(id)
                .paymentId(request.paymentId())
                .bupotNumber(request.bupotNumber())
                .bupotDate(request.bupotDate())
                .taxObjectCode(request.taxObjectCode())
                .grossAmount(request.grossAmount())
                .taxRate(request.taxRate())
                .taxAmount(request.taxAmount())
                .createdBy(CurrentUser.name())
                .build());
        return ResponseEntity.status(HttpStatus.CREATED).body(DetailResponse.from(detail));
    }

    public record FakturRequest(@NotBlank @Size(max = 20) String fakturNumber, @NotNull LocalDate fakturDate,
                                @NotBlank @Size(max = 10) String transactionCode, @NotNull @PositiveOrZero BigDecimal dpp,
                                @NotNull @PositiveOrZero BigDecimal ppn) {}

    public record BupotRequest(UUID paymentId, @NotBlank @Size(max = 30) String bupotNumber, @NotNull LocalDate bupotDate,
                               @NotBlank @Size(max = 20) String taxObjectCode, @NotNull @Positive BigDecimal grossAmount,
                               @NotNull @PositiveOrZero BigDecimal taxRate, @NotNull @Positive BigDecimal taxAmount) {}

    public record DetailResponse(UUID id, UUID transactionId, String transactionStatus) {
        static DetailResponse from(TaxTransactionDetail detail) {
            return new DetailResponse(detail.getId(), detail.getTransaction().getId(), detail.getTransaction().getStatus().name());
        }
    }
}
