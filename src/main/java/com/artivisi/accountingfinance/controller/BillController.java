package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.Bill;
import com.artivisi.accountingfinance.entity.BillLine;
import com.artivisi.accountingfinance.enums.AccountType;
import com.artivisi.accountingfinance.enums.BillStatus;
import com.artivisi.accountingfinance.service.BillService;
import com.artivisi.accountingfinance.service.ChartOfAccountService;
import com.artivisi.accountingfinance.service.ProductService;
import com.artivisi.accountingfinance.service.VendorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
    private static final String ATTR_VENDORS = "vendors";
    private static final String ATTR_EXPENSE_ACCOUNTS = "expenseAccounts";
    private static final String VIEW_FORM = "bills/form";

    private final BillService billService;
    private final VendorService vendorService;
    private final ChartOfAccountService chartOfAccountService;
    private final ProductService productService;

    @GetMapping
    public String list(
            @RequestParam(required = false) BillStatus status,
            @RequestParam(required = false) UUID vendorId,
            @PageableDefault(size = 20) Pageable pageable,
            Model model) {

        Page<Bill> bills = billService.findByFilters(status, vendorId, pageable);

        model.addAttribute("bills", bills);
        model.addAttribute("statuses", BillStatus.values());
        model.addAttribute(ATTR_VENDORS, vendorService.findActiveVendors());
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedVendorId", vendorId);
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

        Bill bill = new Bill();
        if (vendorId != null) {
            bill.setVendor(vendorService.findById(vendorId));
        }

        model.addAttribute(ATTR_BILL, bill);
        populateFormModel(model);
        return VIEW_FORM;
    }

    @PostMapping("/new")
    public String create(
            @Valid @ModelAttribute(ATTR_BILL) Bill bill,
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
            @Valid @ModelAttribute(ATTR_BILL) Bill bill,
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
            bill.setId(existing.getId());
            populateFormModel(model);
            return VIEW_FORM;
        }

        try {
            Bill existing = billService.findByBillNumber(billNumber);
            List<BillLine> lines = buildLines(descriptions, quantities, unitPrices, taxRates, expenseAccountIds);
            billService.update(existing.getId(), bill, lines);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Tagihan berhasil diperbarui");
            return REDIRECT_BILLS + bill.getBillNumber();
        } catch (IllegalArgumentException | IllegalStateException e) {
            if (e.getMessage().contains("sudah digunakan")) {
                bindingResult.rejectValue("billNumber", "duplicate", e.getMessage());
            } else {
                bindingResult.reject("error", e.getMessage());
            }
            Bill existing = billService.findByBillNumber(billNumber);
            bill.setId(existing.getId());
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
        model.addAttribute(ATTR_VENDORS, vendorService.findActiveVendors());
        model.addAttribute(ATTR_EXPENSE_ACCOUNTS, chartOfAccountService.findByAccountType(AccountType.EXPENSE));
        model.addAttribute(ATTR_PRODUCTS, productService.findAllActive());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_BILLS);
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
            line.setQuantity(quantities != null && i < quantities.size() && quantities.get(i) != null
                    ? quantities.get(i) : BigDecimal.ONE);
            line.setUnitPrice(unitPrices != null && i < unitPrices.size() && unitPrices.get(i) != null
                    ? unitPrices.get(i) : BigDecimal.ZERO);
            line.setTaxRate(taxRates != null && i < taxRates.size() ? taxRates.get(i) : null);

            if (expenseAccountIds != null && i < expenseAccountIds.size() && expenseAccountIds.get(i) != null) {
                com.artivisi.accountingfinance.entity.ChartOfAccount account = new com.artivisi.accountingfinance.entity.ChartOfAccount();
                account.setId(expenseAccountIds.get(i));
                line.setExpenseAccount(account);
            }

            line.calculateAmounts();
            lines.add(line);
        }

        return lines;
    }
}
