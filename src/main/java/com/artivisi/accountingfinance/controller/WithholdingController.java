package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.WithholdingPayment;
import com.artivisi.accountingfinance.enums.AccountType;
import com.artivisi.accountingfinance.enums.PaymentMethod;
import com.artivisi.accountingfinance.enums.TaxObjectCode;
import com.artivisi.accountingfinance.enums.TaxType;
import com.artivisi.accountingfinance.enums.WithholdingTreatment;
import com.artivisi.accountingfinance.security.CurrentUser;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.BillService;
import com.artivisi.accountingfinance.service.ChartOfAccountService;
import com.artivisi.accountingfinance.service.CoretaxExportService;
import com.artivisi.accountingfinance.service.WithholdingDepositService;
import com.artivisi.accountingfinance.service.WithholdingPaymentService;
import com.artivisi.accountingfinance.service.WithholdingQueryService;
import jakarta.persistence.EntityNotFoundException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static com.artivisi.accountingfinance.controller.ViewConstants.ATTR_CURRENT_PAGE;
import static com.artivisi.accountingfinance.controller.ViewConstants.PAGE_WITHHOLDING;

/**
 * Payment flow with withholding and the per-masa report. Reads need TAX_REPORT_VIEW; writes need the same
 * permission as the transaction operation they perform.
 */
@Controller
@RequestMapping("/withholding")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permission.TAX_REPORT_VIEW + "')")
public class WithholdingController {

    private static final String ATTR_SUCCESS = "successMessage";
    private static final String ATTR_ERROR = "errorMessage";
    private static final String REDIRECT = "redirect:/withholding";
    private static final String REDIRECT_PAYMENTS = "redirect:/withholding/payments/";

    private final WithholdingPaymentService paymentService;
    private final WithholdingDepositService depositService;
    private final WithholdingQueryService queryService;
    private final CoretaxExportService coretaxExportService;
    private final ChartOfAccountService chartOfAccountService;
    private final BillService billService;

    @Getter
    @Setter
    public static class PaymentForm {
        private UUID vendorId;
        private WithholdingTreatment treatment;
        private String taxObjectCode;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate paymentDate;
        private BigDecimal grossAmount;
        private UUID expenseAccountId;
        private UUID bankAccountId;
        private String billNumber;
        private BigDecimal settledAmount;
        private PaymentMethod paymentMethod;
        private String referenceNumber;
        private String description;
    }

    @Getter
    @Setter
    public static class DepositForm {
        private WithholdingTreatment treatment;
        private Integer year;
        private Integer month;
        private BigDecimal amount;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate depositDate;
        private UUID bankAccountId;
        private String billingCode;
        private String ntpn;
    }

    @GetMapping
    public String report(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth masa, Model model) {
        YearMonth period = masa != null ? masa : YearMonth.now();
        int year = period.getYear();
        int month = period.getMonthValue();
        model.addAttribute("masa", period);
        model.addAttribute("payments", queryService.findPayments(year, month, null, null));
        model.addAttribute("statuses", queryService.getMasaStatus(year, month, LocalDate.now()));
        model.addAttribute("deposits", queryService.findDeposits(year, month));
        model.addAttribute("undocumented", queryService.findUndocumentedServicePayments(year, month));
        model.addAttribute("treatments", WithholdingTreatment.values());
        model.addAttribute("bankAccounts", assetAccounts());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_WITHHOLDING);
        return "withholding/report";
    }

    @GetMapping("/payments/new")
    @PreAuthorize("hasAuthority('" + Permission.TRANSACTION_CREATE + "')")
    public String newPayment(Model model) {
        model.addAttribute("form", new PaymentForm());
        return paymentFormView(model);
    }

    @PostMapping("/payments/new")
    @PreAuthorize("hasAuthority('" + Permission.TRANSACTION_CREATE + "')")
    public String createPayment(@ModelAttribute("form") PaymentForm form, Model model, RedirectAttributes redirect) {
        try {
            UUID billId = form.getBillNumber() == null || form.getBillNumber().isBlank()
                    ? null : billService.findByBillNumber(form.getBillNumber().trim()).getId();
            WithholdingPayment payment = paymentService.create(WithholdingPaymentService.CreatePayment.builder()
                    .vendorId(form.getVendorId())
                    .treatment(form.getTreatment())
                    .taxObjectCode(form.getTaxObjectCode())
                    .paymentDate(form.getPaymentDate())
                    .grossAmount(form.getGrossAmount())
                    .expenseAccountId(billId != null ? null : form.getExpenseAccountId())
                    .bankAccountId(form.getBankAccountId())
                    .billId(billId)
                    .settledAmount(billId != null ? form.getSettledAmount() : null)
                    .paymentMethod(billId != null ? form.getPaymentMethod() : null)
                    .referenceNumber(form.getReferenceNumber())
                    .description(form.getDescription())
                    .createdBy(CurrentUser.name())
                    .build());
            redirect.addFlashAttribute(ATTR_SUCCESS, "Draf pembayaran tersimpan. Periksa nilainya, lalu posting.");
            return REDIRECT_PAYMENTS + payment.getId();
        } catch (IllegalArgumentException | IllegalStateException | EntityNotFoundException e) {
            model.addAttribute(ATTR_ERROR, e.getMessage());
            return paymentFormView(model);
        }
    }

    @GetMapping("/payments/{id}")
    public String paymentDetail(@PathVariable UUID id, Model model) {
        model.addAttribute("payment", queryService.findPayment(id));
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_WITHHOLDING);
        return "withholding/payment-detail";
    }

    @PostMapping("/payments/{id}/post")
    @PreAuthorize("hasAuthority('" + Permission.TRANSACTION_POST + "')")
    public String postPayment(@PathVariable UUID id, RedirectAttributes redirect) {
        try {
            paymentService.post(id, CurrentUser.name());
            redirect.addFlashAttribute(ATTR_SUCCESS, "Pembayaran berhasil diposting");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirect.addFlashAttribute(ATTR_ERROR, e.getMessage());
        }
        return REDIRECT_PAYMENTS + id;
    }

    @PostMapping("/payments/{id}/cancel")
    @PreAuthorize("hasAuthority('" + Permission.TRANSACTION_DELETE + "')")
    public String cancelPayment(@PathVariable UUID id, RedirectAttributes redirect) {
        try {
            paymentService.cancel(id);
            redirect.addFlashAttribute(ATTR_SUCCESS, "Draf pembayaran dibatalkan");
            return REDIRECT;
        } catch (IllegalStateException e) {
            redirect.addFlashAttribute(ATTR_ERROR, e.getMessage());
            return REDIRECT_PAYMENTS + id;
        }
    }

    @PostMapping("/deposits")
    @PreAuthorize("hasAuthority('" + Permission.TRANSACTION_CREATE + "')")
    public String createDeposit(@ModelAttribute DepositForm form, RedirectAttributes redirect) {
        String masaParam = form.getYear() + "-" + String.format("%02d", form.getMonth());
        try {
            depositService.create(WithholdingDepositService.CreateDeposit.builder()
                    .treatment(form.getTreatment())
                    .year(form.getYear())
                    .month(form.getMonth())
                    .amount(form.getAmount())
                    .depositDate(form.getDepositDate())
                    .bankAccountId(form.getBankAccountId())
                    .billingCode(form.getBillingCode())
                    .ntpn(form.getNtpn())
                    .createdBy(CurrentUser.name())
                    .build());
            redirect.addFlashAttribute(ATTR_SUCCESS, "Draf setoran tersimpan. Posting setelah diperiksa.");
        } catch (IllegalArgumentException | IllegalStateException | EntityNotFoundException e) {
            redirect.addFlashAttribute(ATTR_ERROR, e.getMessage());
        }
        return REDIRECT + "?masa=" + masaParam;
    }

    @PostMapping("/deposits/{id}/post")
    @PreAuthorize("hasAuthority('" + Permission.TRANSACTION_POST + "')")
    public String postDeposit(@PathVariable UUID id, RedirectAttributes redirect) {
        String masaParam = depositMasa(id);
        try {
            depositService.post(id, CurrentUser.name());
            redirect.addFlashAttribute(ATTR_SUCCESS, "Setoran berhasil diposting");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirect.addFlashAttribute(ATTR_ERROR, e.getMessage());
        }
        return REDIRECT + "?masa=" + masaParam;
    }

    @PostMapping("/deposits/{id}/cancel")
    @PreAuthorize("hasAuthority('" + Permission.TRANSACTION_DELETE + "')")
    public String cancelDeposit(@PathVariable UUID id, RedirectAttributes redirect) {
        String masaParam = depositMasa(id);
        try {
            depositService.cancel(id);
            redirect.addFlashAttribute(ATTR_SUCCESS, "Draf setoran dibatalkan");
        } catch (IllegalStateException e) {
            redirect.addFlashAttribute(ATTR_ERROR, e.getMessage());
        }
        return REDIRECT + "?masa=" + masaParam;
    }

    @GetMapping("/export/bp21")
    @PreAuthorize("hasAuthority('" + Permission.TAX_EXPORT + "')")
    public ResponseEntity<byte[]> exportBp21(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth masa) throws IOException {
        return xlsx("bp21-" + masa + ".xlsx", coretaxExportService.exportBp21(masa.atDay(1), masa.atEndOfMonth()));
    }

    @GetMapping("/export/bupot-unifikasi")
    @PreAuthorize("hasAuthority('" + Permission.TAX_EXPORT + "')")
    public ResponseEntity<byte[]> exportUnifikasi(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth masa) throws IOException {
        return xlsx("bupot-unifikasi-" + masa + ".xlsx", coretaxExportService.exportBupotUnifikasi(masa.atDay(1), masa.atEndOfMonth()));
    }

    private ResponseEntity<byte[]> xlsx(String filename, byte[] body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    private String depositMasa(UUID id) {
        var deposit = queryService.findDeposit(id);
        return deposit.getPeriodYear() + "-" + String.format("%02d", deposit.getPeriodMonth());
    }

    private String paymentFormView(Model model) {
        model.addAttribute("payees", queryService.findPayees());
        model.addAttribute("treatments", WithholdingTreatment.values());
        model.addAttribute("objectCodes", Arrays.stream(TaxObjectCode.values())
                .filter(c -> c.getTaxType() == TaxType.PPH_21 || c.getTaxType() == TaxType.PPH_23 || c.getTaxType() == TaxType.PPH_26)
                .toList());
        model.addAttribute("expenseAccounts", chartOfAccountService.findTransactableAccounts().stream()
                .filter(a -> a.getAccountType() == AccountType.EXPENSE).toList());
        model.addAttribute("bankAccounts", assetAccounts());
        model.addAttribute("paymentMethods", PaymentMethod.values());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_WITHHOLDING);
        return "withholding/payment-form";
    }

    private List<com.artivisi.accountingfinance.entity.ChartOfAccount> assetAccounts() {
        return chartOfAccountService.findTransactableAccounts().stream()
                .filter(a -> a.getAccountType() == AccountType.ASSET).toList();
    }
}
