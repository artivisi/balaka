package com.artivisi.accountingfinance.controller.api;

import com.artivisi.accountingfinance.entity.BenefitInKind;
import com.artivisi.accountingfinance.service.BenefitInKindService;
import com.artivisi.accountingfinance.security.LogSanitizer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/benefits-in-kind")
@Tag(name = "Benefit in Kind", description = "Natura recipients on posted transactions, feeding PPh 21 and 1721-A1")
@PreAuthorize("hasAuthority('SCOPE_tax-export:read') and hasAuthority('PAYROLL_VIEW')")
@RequiredArgsConstructor
@Slf4j
public class BenefitInKindApiController {

    private final BenefitInKindService benefitInKindService;

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "List benefit-in-kind rows on posted transactions dated in the year")
    @ApiResponse(responseCode = "200", description = "List of rows; payrollPeriod is null until a payroll run counts the row")
    public ResponseEntity<List<BenefitInKindResponse>> list(@RequestParam int year) {
        return ResponseEntity.ok(benefitInKindService.findByYear(year).stream()
                .map(BenefitInKindResponse::from)
                .toList());
    }

    @PostMapping
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_payroll:write') and hasAuthority('PAYROLL_CREATE')")
    @Operation(summary = "Record a recipient on a POSTED transaction; adds the NATURA tag")
    @ApiResponse(responseCode = "201", description = "Recipient recorded")
    @ApiResponse(responseCode = "400", description = "Total exceeds the transaction's expense debit")
    @ApiResponse(responseCode = "404", description = "Transaction or employee not found")
    @ApiResponse(responseCode = "409", description = "Transaction is not POSTED")
    public ResponseEntity<BenefitInKindResponse> create(@Valid @RequestBody BenefitInKindRequest request) {
        log.info("API: Create benefit in kind - transaction={}, employee={}",
                LogSanitizer.sanitize(request.transactionId().toString()), LogSanitizer.sanitize(request.employeeId().toString()));
        BenefitInKind saved = benefitInKindService.add(
                request.transactionId(), request.employeeId(), request.amount(), request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(BenefitInKindResponse.from(saved));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_payroll:write') and hasAuthority('PAYROLL_CREATE')")
    @Operation(summary = "Delete a row not yet counted by a payroll run")
    @ApiResponse(responseCode = "204", description = "Row deleted")
    @ApiResponse(responseCode = "404", description = "Row not found")
    @ApiResponse(responseCode = "409", description = "Row is counted by a payroll run")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        benefitInKindService.delete(id);
        return ResponseEntity.noContent().build();
    }

    public record BenefitInKindRequest(
            @NotNull(message = "Transaksi wajib diisi")
            UUID transactionId,

            @NotNull(message = "Penerima wajib diisi")
            UUID employeeId,

            @NotNull(message = "Nilai natura wajib diisi")
            @Positive(message = "Nilai natura harus positif")
            BigDecimal amount,

            @NotBlank(message = "Keterangan natura wajib diisi")
            @Size(max = 255, message = "Keterangan natura maksimal 255 karakter")
            String description
    ) {}

    public record BenefitInKindResponse(
            UUID id,
            UUID transactionId,
            String transactionNumber,
            LocalDate transactionDate,
            UUID employeeId,
            String employeeCode,
            String employeeName,
            BigDecimal amount,
            String description,
            String payrollPeriod
    ) {
        static BenefitInKindResponse from(BenefitInKind b) {
            return new BenefitInKindResponse(
                    b.getId(),
                    b.getTransaction().getId(),
                    b.getTransaction().getTransactionNumber(),
                    b.getTransaction().getTransactionDate(),
                    b.getEmployee().getId(),
                    b.getEmployee().getEmployeeId(),
                    b.getEmployee().getName(),
                    b.getAmount(),
                    b.getDescription(),
                    b.getPayrollDetail() != null ? b.getPayrollDetail().getPayrollRun().getPayrollPeriod() : null
            );
        }
    }
}
