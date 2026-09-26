package com.artivisi.accountingfinance.controller.api;

import com.artivisi.accountingfinance.dto.BillCreateRequest;
import com.artivisi.accountingfinance.dto.BillLineRequest;
import com.artivisi.accountingfinance.dto.BillResponse;
import com.artivisi.accountingfinance.entity.Bill;
import com.artivisi.accountingfinance.entity.BillLine;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.Vendor;
import com.artivisi.accountingfinance.enums.BillStatus;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.service.BillService;
import com.artivisi.accountingfinance.service.VendorService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/bills")
@Tag(name = "Bills", description = "Vendor bill management (create, approve, mark paid)")
@RequiredArgsConstructor
@Slf4j
public class BillApiController {

    private final BillService billService;
    private final VendorService vendorService;
    private final ChartOfAccountRepository chartOfAccountRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_bills:read')")
    public ResponseEntity<Map<String, Object>> listBills(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID vendorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        BillStatus billStatus = null;
        if (status != null && !status.isBlank()) {
            billStatus = BillStatus.valueOf(status.toUpperCase());
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<Bill> bills = billService.findByFiltersWithDates(billStatus, vendorId, dateFrom, dateTo, pageable);

        List<BillResponse> items = bills.getContent().stream()
                .map(BillResponse::from)
                .toList();

        Map<String, Object> response = Map.of(
                "items", items,
                "totalElements", bills.getTotalElements(),
                "totalPages", bills.getTotalPages(),
                "currentPage", bills.getNumber(),
                "pageSize", bills.getSize()
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_bills:read')")
    public ResponseEntity<BillResponse> getBill(@PathVariable UUID id) {
        Bill bill = billService.findById(id);
        return ResponseEntity.ok(BillResponse.from(bill));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_bills:create')")
    public ResponseEntity<BillResponse> createBill(@Valid @RequestBody BillCreateRequest request) {
        String username = getCurrentUsername();
        log.info("API: Create bill - vendor={}, date={}, lines={}, user={}",
                request.vendorName(), request.billDate(), request.lines().size(), username);

        List<BillLine> lines = new ArrayList<>();
        for (BillLineRequest lineReq : request.lines()) {
            BillLine line = new BillLine();
            line.setDescription(lineReq.description());
            line.setQuantity(lineReq.quantity() != null ? lineReq.quantity() : BigDecimal.ONE);
            line.setUnitPrice(lineReq.unitPrice());
            line.setTaxRate(lineReq.taxRate());

            if (lineReq.expenseAccountCode() != null && !lineReq.expenseAccountCode().isBlank()) {
                ChartOfAccount account = chartOfAccountRepository
                        .findByAccountCode(lineReq.expenseAccountCode())
                        .orElseThrow(() -> new EntityNotFoundException(
                                "Akun beban tidak ditemukan: " + lineReq.expenseAccountCode()));
                line.setExpenseAccount(account);
            }

            lines.add(line);
        }

        Vendor vendor = vendorService.findOrCreateByName(request.vendorName());

        Bill bill = new Bill();
        bill.setVendor(vendor);
        bill.setBillDate(request.billDate());
        bill.setDueDate(request.dueDate());
        bill.setVendorInvoiceNumber(request.vendorInvoiceNumber());
        bill.setNotes(request.notes());

        Bill created = billService.create(bill, lines);
        log.info("API: Bill created - billNumber={}, vendor={}, amount={}",
                created.getBillNumber(), vendor.getName(), created.getAmount());

        return ResponseEntity.status(HttpStatus.CREATED).body(BillResponse.from(created));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('SCOPE_bills:approve') and hasAuthority('BILL_APPROVE')")
    public ResponseEntity<BillResponse> approveBill(@PathVariable UUID id) {
        String username = getCurrentUsername();
        log.info("API: Approve bill id={}, user={}", id, username);

        Bill approved = billService.approve(id);
        return ResponseEntity.ok(BillResponse.from(approved));
    }

    @PostMapping("/{id}/mark-paid")
    @PreAuthorize("hasAuthority('SCOPE_bills:approve') and hasAuthority('BILL_MARK_PAID')")
    public ResponseEntity<BillResponse> markBillPaid(@PathVariable UUID id) {
        String username = getCurrentUsername();
        log.info("API: Mark bill paid id={}, user={}", id, username);

        Bill paid = billService.markAsPaid(id);
        return ResponseEntity.ok(BillResponse.from(paid));
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return auth.getName();
        }
        return "API";
    }
}
