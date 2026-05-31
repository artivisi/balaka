package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.Bill;
import com.artivisi.accountingfinance.entity.BillLine;
import com.artivisi.accountingfinance.entity.BillPayment;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.Vendor;
import com.artivisi.accountingfinance.enums.BillStatus;
import com.artivisi.accountingfinance.enums.PaymentMethod;
import com.artivisi.accountingfinance.service.BillService;
import com.artivisi.accountingfinance.service.VendorService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.artivisi.accountingfinance.controller.ViewConstants.*;
import static com.artivisi.accountingfinance.security.Permission.BILL_VIEW;

@Controller
@RequestMapping("/bills")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + BILL_VIEW + "')")
public class BillController {

    private static final String ATTR_BILL = "bill";
    private static final String ATTR_SUCCESS_MESSAGE = "successMessage";
    private static final String ATTR_ERROR_MESSAGE = "errorMessage";
    private static final String VIEW_FORM = "bills/form";

    private final BillService billService;
    private final VendorService vendorService;

    @Getter
    @Setter
    static class EntityRef {
        private UUID id;
    }

    @Getter
    @Setter
    static class BillForm {
        private UUID id;

        @Size(max = 50, message = "Nomor tagihan maksimal 50 karakter")
        private String billNumber;

        private EntityRef vendor = new EntityRef();
        // Carries the human label across POST validation re-renders so the combobox
        // can re-display the selected vendor without re-fetching.
        private String vendorLabel;

        @Size(max = 100, message = "Nomor faktur vendor maksimal 100 karakter")
        private String vendorInvoiceNumber;

        @NotNull(message = "Tanggal tagihan wajib diisi")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate billDate;

        @NotNull(message = "Tanggal jatuh tempo wajib diisi")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate dueDate;

        private String notes;

        // Used by Alpine.js template: /*[[${bill.lines}]]*/
        private List<BillLine> lines = new ArrayList<>();
    }

    private Bill toEntity(BillForm form) {
        Bill entity = new Bill();
        BeanUtils.copyProperties(form, entity, "id", "vendor");
        if (form.getVendor() != null && form.getVendor().getId() != null) {
            Vendor v = new Vendor();
            v.setId(form.getVendor().getId());
            entity.setVendor(v);
        }
        return entity;
    }

    @GetMapping
    public String list(
            @RequestParam(required = false) BillStatus status,
            @RequestParam(required = false) UUID vendorId,
            @PageableDefault(size = 20) Pageable pageable,
            Model model) {

        Page<Bill> bills = billService.findByFilters(status, vendorId, pageable);

        model.addAttribute("bills", bills);
        model.addAttribute("statuses", BillStatus.values());
        // Vendor list picker-driven now; resolve the selected vendor's label so
        // the combobox re-renders the active filter after submit.
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedVendorId", vendorId);
        model.addAttribute("selectedVendorLabel", vendorId == null ? "" : vendorLabel(vendorId));
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_BILLS);

        model.addAttribute("draftCount", billService.countByStatus(BillStatus.DRAFT));
        model.addAttribute("approvedCount", billService.countByStatus(BillStatus.APPROVED));
        model.addAttribute("overdueCount", billService.countByStatus(BillStatus.OVERDUE));
        model.addAttribute("paidCount", billService.countByStatus(BillStatus.PAID));

        return VIEW_BILLS_LIST;
    }

    @GetMapping("/new")
    public String newForm(
            @RequestParam(required = false) UUID vendorId,
            Model model) {

        BillForm form = new BillForm();
        if (vendorId != null) {
            EntityRef vendorRef = new EntityRef();
            vendorRef.setId(vendorId);
            form.setVendor(vendorRef);
        }

        model.addAttribute(ATTR_BILL, form);
        populateFormModel(model);
        return VIEW_FORM;
    }

    @PostMapping("/new")
    public String create(
            @Valid @ModelAttribute(ATTR_BILL) BillForm form,
            BindingResult bindingResult,
            @RequestParam(value = "lineDescription", required = false) List<String> descriptions,
            @RequestParam(value = "lineQuantity", required = false) List<BigDecimal> quantities,
            @RequestParam(value = "lineUnitPrice", required = false) List<BigDecimal> unitPrices,
            @RequestParam(value = "lineTaxRate", required = false) List<BigDecimal> taxRates,
            @RequestParam(value = "lineExpenseAccountId", required = false) List<UUID> expenseAccountIds,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            populateFormModel(model);
            return VIEW_FORM;
        }

        try {
            Bill bill = toEntity(form);
            List<BillLine> lines = buildLines(descriptions, quantities, unitPrices, taxRates, expenseAccountIds);
            Bill saved = billService.create(bill, lines);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Tagihan berhasil dibuat");
            return REDIRECT_BILLS + saved.getBillNumber();
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("billNumber", "duplicate", e.getMessage());
            populateFormModel(model);
            return VIEW_FORM;
        }
    }

    @GetMapping("/{billNumber}")
    public String detail(@PathVariable String billNumber, Model model) {
        Bill bill = billService.findByBillNumber(billNumber);

        model.addAttribute(ATTR_BILL, bill);
        model.addAttribute("payments", billService.findPaymentsByBillId(bill.getId()));
        model.addAttribute("paymentMethods", PaymentMethod.values());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_BILLS);
        return VIEW_BILLS_DETAIL;
    }

    @GetMapping("/{billNumber}/edit")
    public String editForm(@PathVariable String billNumber, Model model) {
        Bill bill = billService.findByBillNumber(billNumber);

        if (bill.getStatus() != BillStatus.DRAFT) {
            return REDIRECT_BILLS + billNumber;
        }

        model.addAttribute(ATTR_BILL, bill);
        populateFormModel(model);
        return VIEW_FORM;
    }

    @PostMapping("/{billNumber}")
    public String update(
            @PathVariable String billNumber,
            @Valid @ModelAttribute(ATTR_BILL) BillForm form,
            BindingResult bindingResult,
            @RequestParam(value = "lineDescription", required = false) List<String> descriptions,
            @RequestParam(value = "lineQuantity", required = false) List<BigDecimal> quantities,
            @RequestParam(value = "lineUnitPrice", required = false) List<BigDecimal> unitPrices,
            @RequestParam(value = "lineTaxRate", required = false) List<BigDecimal> taxRates,
            @RequestParam(value = "lineExpenseAccountId", required = false) List<UUID> expenseAccountIds,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            Bill existing = billService.findByBillNumber(billNumber);
            form.setId(existing.getId());
            populateFormModel(model);
            return VIEW_FORM;
        }

        try {
            Bill existing = billService.findByBillNumber(billNumber);
            Bill bill = toEntity(form);
            List<BillLine> lines = buildLines(descriptions, quantities, unitPrices, taxRates, expenseAccountIds);
            billService.update(existing.getId(), bill, lines);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Tagihan berhasil diperbarui");
            return REDIRECT_BILLS + form.getBillNumber();
        } catch (IllegalArgumentException | IllegalStateException e) {
            if (e.getMessage().contains("sudah digunakan")) {
                bindingResult.rejectValue("billNumber", "duplicate", e.getMessage());
            } else {
                bindingResult.reject("error", e.getMessage());
            }
            Bill existing = billService.findByBillNumber(billNumber);
            form.setId(existing.getId());
            populateFormModel(model);
            return VIEW_FORM;
        }
    }

    @PostMapping("/{billNumber}/approve")
    public String approve(@PathVariable String billNumber, RedirectAttributes redirectAttributes) {
        try {
            Bill bill = billService.findByBillNumber(billNumber);
            billService.approve(bill.getId());
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Tagihan berhasil disetujui");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR_MESSAGE, e.getMessage());
        }
        return REDIRECT_BILLS + billNumber;
    }

    @PostMapping("/{billNumber}/mark-paid")
    public String markPaid(@PathVariable String billNumber, RedirectAttributes redirectAttributes) {
        try {
            Bill bill = billService.findByBillNumber(billNumber);
            billService.markAsPaid(bill.getId());
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Tagihan ditandai sudah dibayar");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR_MESSAGE, e.getMessage());
        }
        return REDIRECT_BILLS + billNumber;
    }

    @PostMapping("/{billNumber}/payments")
    public String recordPayment(
            @PathVariable String billNumber,
            @RequestParam LocalDate paymentDate,
            @RequestParam BigDecimal amount,
            @RequestParam PaymentMethod paymentMethod,
            @RequestParam(required = false) String referenceNumber,
            @RequestParam(required = false) String paymentNotes,
            RedirectAttributes redirectAttributes) {
        try {
            Bill bill = billService.findByBillNumber(billNumber);
            BillPayment payment = new BillPayment();
            payment.setPaymentDate(paymentDate);
            payment.setAmount(amount);
            payment.setPaymentMethod(paymentMethod);
            payment.setReferenceNumber(referenceNumber);
            payment.setNotes(paymentNotes);

            billService.recordPayment(bill.getId(), payment);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Pembayaran berhasil dicatat");
        } catch (IllegalStateException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR_MESSAGE, e.getMessage());
        }
        return REDIRECT_BILLS + billNumber;
    }

    @PostMapping("/{billNumber}/cancel")
    public String cancel(@PathVariable String billNumber, RedirectAttributes redirectAttributes) {
        try {
            Bill bill = billService.findByBillNumber(billNumber);
            billService.cancel(bill.getId());
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Tagihan dibatalkan");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR_MESSAGE, e.getMessage());
        }
        return REDIRECT_BILLS + billNumber;
    }

    @PostMapping("/{billNumber}/delete")
    public String delete(@PathVariable String billNumber, RedirectAttributes redirectAttributes) {
        try {
            Bill bill = billService.findByBillNumber(billNumber);
            billService.delete(bill.getId());
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Tagihan berhasil dihapus");
            return "redirect:/bills";
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR_MESSAGE, e.getMessage());
            return REDIRECT_BILLS + billNumber;
        }
    }

    private void populateFormModel(Model model) {
        // Vendors fetched on-demand via GET /vendors/search by the combobox.
        // Expense accounts fetched on-demand via GET /accounts/search per line.
        // Products fetched on-demand via GET /products/search if/when used.
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_BILLS);
    }

    /**
     * Resolve a vendor id to its "code - name" label for the picker's
     * data-initial-label when the list filter is pre-applied.
     */
    private String vendorLabel(UUID vendorId) {
        try {
            Vendor v = vendorService.findById(vendorId);
            return v.getCode() + " - " + v.getName();
        } catch (jakarta.persistence.EntityNotFoundException e) {
            return vendorId.toString();
        }
    }

    private List<BillLine> buildLines(
            List<String> descriptions,
            List<BigDecimal> quantities,
            List<BigDecimal> unitPrices,
            List<BigDecimal> taxRates,
            List<UUID> expenseAccountIds) {

        List<BillLine> lines = new ArrayList<>();
        if (descriptions == null || descriptions.isEmpty()) {
            return lines;
        }

        for (int i = 0; i < descriptions.size(); i++) {
            String desc = descriptions.get(i);
            if (desc == null || desc.isBlank()) continue;

            BillLine line = new BillLine();
            line.setDescription(desc);
            line.setQuantity(getListValue(quantities, i, BigDecimal.ONE));
            line.setUnitPrice(getListValue(unitPrices, i, BigDecimal.ZERO));
            line.setTaxRate(getListValue(taxRates, i, null));
            setExpenseAccount(line, expenseAccountIds, i);
            line.calculateAmounts();
            lines.add(line);
        }

        return lines;
    }

    private <T> T getListValue(List<T> list, int index, T defaultValue) {
        if (list != null && index < list.size() && list.get(index) != null) {
            return list.get(index);
        }
        return defaultValue;
    }

    private void setExpenseAccount(BillLine line, List<UUID> expenseAccountIds, int index) {
        UUID accountId = getListValue(expenseAccountIds, index, null);
        if (accountId != null) {
            ChartOfAccount account = new ChartOfAccount();
            account.setId(accountId);
            line.setExpenseAccount(account);
        }
    }
}
