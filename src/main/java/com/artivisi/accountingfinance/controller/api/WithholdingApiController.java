package com.artivisi.accountingfinance.controller.api;

import com.artivisi.accountingfinance.entity.Vendor;
import com.artivisi.accountingfinance.entity.WithholdingDeposit;
import com.artivisi.accountingfinance.entity.WithholdingPayment;
import com.artivisi.accountingfinance.enums.PayeeType;
import com.artivisi.accountingfinance.enums.PaymentMethod;
import com.artivisi.accountingfinance.enums.WithholdingTreatment;
import com.artivisi.accountingfinance.security.CurrentUser;
import com.artivisi.accountingfinance.service.VendorService;
import com.artivisi.accountingfinance.service.WithholdingDepositService;
import com.artivisi.accountingfinance.service.WithholdingPaymentService;
import com.artivisi.accountingfinance.service.WithholdingQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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

@RestController
@RequestMapping("/api/withholding")
@Tag(name = "Withholding", description = "Payees, payments with PPh 21 bukan pegawai / PPh 23 / PPh 26 withheld, "
        + "deposits per masa, and the report of service payments without a PPh tax detail. "
        + "Payments and deposits are DRAFT until posted.")
@RequiredArgsConstructor
public class WithholdingApiController {

    private static final String READ = "hasAuthority('SCOPE_withholding:read')";
    private static final String WRITE = "hasAuthority('SCOPE_withholding:write')";

    private final VendorService vendorService;
    private final WithholdingPaymentService paymentService;
    private final WithholdingDepositService depositService;
    private final WithholdingQueryService queryService;

    // ==================== payees ====================

    @GetMapping("/payees")
    @PreAuthorize(READ)
    @Operation(summary = "List vendors classified as payees. NPWP and NIK are masked.")
    public List<PayeeResponse> payees() {
        return queryService.findPayees().stream().map(PayeeResponse::from).toList();
    }

    @PostMapping("/payees")
    @PreAuthorize(WRITE + " and hasAuthority('VENDOR_CREATE')")
    @Operation(summary = "Create a payee")
    public ResponseEntity<PayeeResponse> createPayee(@Valid @RequestBody PayeeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(PayeeResponse.from(vendorService.create(toVendor(request))));
    }

    @PutMapping("/payees/{id}")
    @PreAuthorize(WRITE + " and hasAuthority('VENDOR_EDIT')")
    @Operation(summary = "Update a payee")
    public PayeeResponse updatePayee(@PathVariable UUID id, @Valid @RequestBody PayeeRequest request) {
        return PayeeResponse.from(vendorService.update(id, toVendor(request)));
    }

    // ==================== payments ====================

    @GetMapping("/payments")
    @PreAuthorize(READ)
    @Operation(summary = "List payments of a masa with every transaction status")
    public List<PaymentResponse> payments(@RequestParam int year, @RequestParam @Min(1) @Max(12) int month,
                                          @RequestParam(required = false) WithholdingTreatment treatment,
                                          @RequestParam(required = false) UUID vendorId) {
        return queryService.findPayments(year, month, treatment, vendorId).stream().map(PaymentResponse::from).toList();
    }

    @GetMapping("/payments/{id}")
    @PreAuthorize(READ)
    @Operation(summary = "Get one payment")
    public PaymentResponse payment(@PathVariable UUID id) {
        return PaymentResponse.from(queryService.findPayment(id));
    }

    @PostMapping("/payments")
    @PreAuthorize(WRITE + " and hasAuthority('TRANSACTION_CREATE')")
    @Operation(summary = "Compute gross, tax and net; create a DRAFT transaction with its tax detail")
    public ResponseEntity<PaymentResponse> createPayment(@Valid @RequestBody PaymentRequest request) {
        WithholdingPayment payment = paymentService.create(WithholdingPaymentService.CreatePayment.builder()
                .vendorId(request.vendorId())
                .treatment(request.treatment())
                .taxObjectCode(request.taxObjectCode())
                .paymentDate(request.paymentDate())
                .grossAmount(request.grossAmount())
                .expenseAccountId(request.expenseAccountId())
                .bankAccountId(request.bankAccountId())
                .billId(request.billId())
                .settledAmount(request.settledAmount())
                .paymentMethod(request.paymentMethod())
                .referenceNumber(request.referenceNumber())
                .description(request.description())
                .createdBy(CurrentUser.name())
                .build());
        return ResponseEntity.status(HttpStatus.CREATED).body(PaymentResponse.from(payment));
    }

    @PostMapping("/payments/{id}/post")
    @PreAuthorize(WRITE + " and hasAuthority('TRANSACTION_POST')")
    @Operation(summary = "Post a DRAFT payment")
    public PaymentResponse postPayment(@PathVariable UUID id) {
        paymentService.post(id, CurrentUser.name());
        return PaymentResponse.from(queryService.findPayment(id));
    }

    @DeleteMapping("/payments/{id}")
    @PreAuthorize(WRITE + " and hasAuthority('TRANSACTION_DELETE')")
    @Operation(summary = "Cancel a DRAFT payment; later drafts in the masa are recomputed")
    public ResponseEntity<Void> cancelPayment(@PathVariable UUID id) {
        paymentService.cancel(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== deposits ====================

    @GetMapping("/deposits")
    @PreAuthorize(READ)
    @Operation(summary = "List deposits of a masa")
    public List<DepositResponse> deposits(@RequestParam int year, @RequestParam @Min(1) @Max(12) int month) {
        return queryService.findDeposits(year, month).stream().map(DepositResponse::from).toList();
    }

    @PostMapping("/deposits")
    @PreAuthorize(WRITE + " and hasAuthority('TRANSACTION_CREATE')")
    @Operation(summary = "Create a DRAFT deposit; the amount cannot exceed posted tax not yet deposited")
    public ResponseEntity<DepositResponse> createDeposit(@Valid @RequestBody DepositRequest request) {
        WithholdingDeposit deposit = depositService.create(WithholdingDepositService.CreateDeposit.builder()
                .treatment(request.treatment())
                .year(request.year())
                .month(request.month())
                .amount(request.amount())
                .depositDate(request.depositDate())
                .bankAccountId(request.bankAccountId())
                .billingCode(request.billingCode())
                .ntpn(request.ntpn())
                .createdBy(CurrentUser.name())
                .build());
        return ResponseEntity.status(HttpStatus.CREATED).body(DepositResponse.from(deposit));
    }

    @PostMapping("/deposits/{id}/post")
    @PreAuthorize(WRITE + " and hasAuthority('TRANSACTION_POST')")
    @Operation(summary = "Post a DRAFT deposit")
    public DepositResponse postDeposit(@PathVariable UUID id) {
        depositService.post(id, CurrentUser.name());
        return DepositResponse.from(queryService.findDeposit(id));
    }

    @DeleteMapping("/deposits/{id}")
    @PreAuthorize(WRITE + " and hasAuthority('TRANSACTION_DELETE')")
    @Operation(summary = "Cancel a DRAFT deposit")
    public ResponseEntity<Void> cancelDeposit(@PathVariable UUID id) {
        depositService.cancel(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== reports ====================

    @GetMapping("/masa-status")
    @PreAuthorize(READ)
    @Operation(summary = "Withheld, deposited, due date and status per tax for a masa",
            description = "asOf is the date overdue is judged against; omitted means today.")
    public List<WithholdingQueryService.MasaStatus> masaStatus(
            @RequestParam int year, @RequestParam @Min(1) @Max(12) int month,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return queryService.getMasaStatus(year, month, asOf != null ? asOf : LocalDate.now());
    }

    @GetMapping("/undocumented-service-payments")
    @PreAuthorize(READ)
    @Operation(summary = "Posted debits on service accounts with no PPh tax detail in a masa")
    public WithholdingQueryService.UndocumentedReport undocumented(@RequestParam int year, @RequestParam @Min(1) @Max(12) int month) {
        return queryService.findUndocumentedServicePayments(year, month);
    }

    private Vendor toVendor(PayeeRequest request) {
        Vendor vendor = new Vendor();
        vendor.setCode(request.code());
        vendor.setName(request.name());
        vendor.setAddress(request.address());
        vendor.setEmail(request.email());
        vendor.setPayeeType(request.payeeType());
        vendor.setDefaultWithholding(request.defaultWithholding());
        vendor.setDefaultTaxObjectCode(request.defaultTaxObjectCode());
        vendor.setNpwp(request.npwp());
        vendor.setNik(request.nik());
        vendor.setCountryCode(request.countryCode());
        vendor.setForeignTin(request.foreignTin());
        vendor.setTreatyRate(request.treatyRate());
        vendor.setDgtFormNumber(request.dgtFormNumber());
        vendor.setDgtValidUntil(request.dgtValidUntil());
        return vendor;
    }

    private static String mask(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return "****" + value.substring(Math.max(0, value.length() - 4));
    }

    public record PayeeRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 255) String name,
            String address,
            @Size(max = 255) String email,
            @NotNull PayeeType payeeType,
            WithholdingTreatment defaultWithholding,
            @Size(max = 20) String defaultTaxObjectCode,
            @Size(max = 20) String npwp,
            @Size(max = 16) String nik,
            @Size(max = 2) String countryCode,
            @Size(max = 50) String foreignTin,
            BigDecimal treatyRate,
            @Size(max = 50) String dgtFormNumber,
            LocalDate dgtValidUntil) {}

    public record PayeeResponse(UUID id, String code, String name, PayeeType payeeType, WithholdingTreatment defaultWithholding,
                                String defaultTaxObjectCode, String npwpMasked, String nikMasked, boolean hasTaxId,
                                String countryCode, String foreignTin, BigDecimal treatyRate, String dgtFormNumber,
                                LocalDate dgtValidUntil, boolean active) {
        static PayeeResponse from(Vendor v) {
            return new PayeeResponse(v.getId(), v.getCode(), v.getName(), v.getPayeeType(), v.getDefaultWithholding(),
                    v.getDefaultTaxObjectCode(), mask(v.getNpwp()), mask(v.getNik()), v.hasTaxId(), v.getCountryCode(),
                    v.getForeignTin(), v.getTreatyRate(), v.getDgtFormNumber(), v.getDgtValidUntil(), v.isActive());
        }
    }

    public record PaymentRequest(
            @NotNull UUID vendorId,
            WithholdingTreatment treatment,
            @Size(max = 20) String taxObjectCode,
            @NotNull LocalDate paymentDate,
            @NotNull @Positive BigDecimal grossAmount,
            UUID expenseAccountId,
            @NotNull UUID bankAccountId,
            UUID billId,
            BigDecimal settledAmount,
            PaymentMethod paymentMethod,
            @Size(max = 100) String referenceNumber,
            @NotBlank String description) {}

    public record PaymentResponse(UUID id, UUID vendorId, String vendorCode, WithholdingTreatment treatment, String taxObjectCode,
                                  LocalDate paymentDate, BigDecimal grossAmount, BigDecimal dppAmount, BigDecimal taxRate,
                                  BigDecimal taxAmount, BigDecimal settledAmount, BigDecimal netAmount, boolean hasTaxId,
                                  String rateBasis, UUID transactionId, String transactionStatus, UUID billId) {
        static PaymentResponse from(WithholdingPayment p) {
            return new PaymentResponse(p.getId(), p.getVendor().getId(), p.getVendor().getCode(), p.getTreatment(),
                    p.getTaxObjectCode(), p.getPaymentDate(), p.getGrossAmount(), p.getDppAmount(), p.getTaxRate(),
                    p.getTaxAmount(), p.getSettledAmount(), p.getNetAmount(), p.isHasTaxId(), p.getRateBasis().name(),
                    p.getTransaction().getId(), p.getTransaction().getStatus().name(),
                    p.getBill() != null ? p.getBill().getId() : null);
        }
    }

    public record DepositRequest(
            @NotNull WithholdingTreatment treatment,
            @NotNull Integer year,
            @NotNull @Min(1) @Max(12) Integer month,
            @NotNull @Positive BigDecimal amount,
            @NotNull LocalDate depositDate,
            @NotNull UUID bankAccountId,
            @Size(max = 50) String billingCode,
            @Size(max = 50) String ntpn) {}

    public record DepositResponse(UUID id, WithholdingTreatment treatment, int year, int month, BigDecimal amount,
                                  LocalDate depositDate, String billingCode, String ntpn, UUID transactionId, String transactionStatus) {
        static DepositResponse from(WithholdingDeposit d) {
            return new DepositResponse(d.getId(), d.getTreatment(), d.getPeriodYear(), d.getPeriodMonth(), d.getAmount(),
                    d.getDepositDate(), d.getBillingCode(), d.getNtpn(), d.getTransaction().getId(),
                    d.getTransaction().getStatus().name());
        }
    }
}
