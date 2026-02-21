package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.Vendor;
import com.artivisi.accountingfinance.enums.AccountType;
import com.artivisi.accountingfinance.service.ChartOfAccountService;
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

import static com.artivisi.accountingfinance.controller.ViewConstants.*;
import static com.artivisi.accountingfinance.security.Permission.VENDOR_VIEW;

@Controller
@RequestMapping("/vendors")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + VENDOR_VIEW + "')")
public class VendorController {

    private static final String ATTR_VENDOR = "vendor";
    private static final String ATTR_SUCCESS_MESSAGE = "successMessage";
    private static final String ATTR_ERROR_MESSAGE = "errorMessage";
    private static final String ATTR_EXPENSE_ACCOUNTS = "expenseAccounts";

    private final VendorService vendorService;
    private final ChartOfAccountService chartOfAccountService;

    @GetMapping
    public String list(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable,
            Model model) {

        Page<Vendor> vendors = vendorService.findByFilters(active, search, pageable);

        model.addAttribute("vendors", vendors);
        model.addAttribute("selectedActive", active);
        model.addAttribute("search", search);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_VENDORS);

        return VIEW_VENDORS_LIST;
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute(ATTR_VENDOR, new Vendor());
        model.addAttribute(ATTR_EXPENSE_ACCOUNTS, chartOfAccountService.findByAccountType(AccountType.EXPENSE));
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_VENDORS);
        return VIEW_VENDORS_FORM;
    }

    @PostMapping("/new")
    public String create(
            @Valid @ModelAttribute(ATTR_VENDOR) Vendor vendor,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            model.addAttribute(ATTR_EXPENSE_ACCOUNTS, chartOfAccountService.findByAccountType(AccountType.EXPENSE));
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_VENDORS);
            return VIEW_VENDORS_FORM;
        }

        try {
            Vendor saved = vendorService.create(vendor);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Vendor berhasil dibuat");
            return REDIRECT_VENDORS + saved.getCode();
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("code", "duplicate", e.getMessage());
            model.addAttribute(ATTR_EXPENSE_ACCOUNTS, chartOfAccountService.findByAccountType(AccountType.EXPENSE));
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_VENDORS);
            return VIEW_VENDORS_FORM;
        }
    }

    @GetMapping("/{code}")
    public String detail(@PathVariable String code, Model model) {
        Vendor vendor = vendorService.findByCode(code);

        model.addAttribute(ATTR_VENDOR, vendor);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_VENDORS);
        return VIEW_VENDORS_DETAIL;
    }

    @GetMapping("/{code}/edit")
    public String editForm(@PathVariable String code, Model model) {
        Vendor vendor = vendorService.findByCode(code);

        model.addAttribute(ATTR_VENDOR, vendor);
        model.addAttribute(ATTR_EXPENSE_ACCOUNTS, chartOfAccountService.findByAccountType(AccountType.EXPENSE));
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_VENDORS);
        model.addAttribute(ATTR_IS_EDIT, true);
        return VIEW_VENDORS_FORM;
    }

    @PostMapping("/{code}")
    public String update(
            @PathVariable String code,
            @Valid @ModelAttribute(ATTR_VENDOR) Vendor vendor,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            Vendor existing = vendorService.findByCode(code);
            vendor.setId(existing.getId());
            model.addAttribute(ATTR_EXPENSE_ACCOUNTS, chartOfAccountService.findByAccountType(AccountType.EXPENSE));
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_VENDORS);
            model.addAttribute(ATTR_IS_EDIT, true);
            return VIEW_VENDORS_FORM;
        }

        try {
            Vendor existing = vendorService.findByCode(code);
            vendorService.update(existing.getId(), vendor);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Vendor berhasil diperbarui");
            return REDIRECT_VENDORS + vendor.getCode();
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("code", "duplicate", e.getMessage());
            Vendor existing = vendorService.findByCode(code);
            vendor.setId(existing.getId());
            model.addAttribute(ATTR_EXPENSE_ACCOUNTS, chartOfAccountService.findByAccountType(AccountType.EXPENSE));
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_VENDORS);
            model.addAttribute(ATTR_IS_EDIT, true);
            return VIEW_VENDORS_FORM;
        }
    }

    @PostMapping("/{code}/deactivate")
    public String deactivate(@PathVariable String code, RedirectAttributes redirectAttributes) {
        Vendor vendor = vendorService.findByCode(code);
        vendorService.deactivate(vendor.getId());
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Vendor berhasil dinonaktifkan");
        return REDIRECT_VENDORS + code;
    }

    @PostMapping("/{code}/activate")
    public String activate(@PathVariable String code, RedirectAttributes redirectAttributes) {
        Vendor vendor = vendorService.findByCode(code);
        vendorService.activate(vendor.getId());
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Vendor berhasil diaktifkan");
        return REDIRECT_VENDORS + code;
    }
}
