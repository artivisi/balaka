package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.Vendor;
import com.artivisi.accountingfinance.enums.AccountType;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.ChartOfAccountService;
import com.artivisi.accountingfinance.service.VendorService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.BeanUtils;
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

import java.util.UUID;

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

    /**
     * Typeahead search for vendor pickers. Returns at most 10 active vendors
     * matching q (by code or name). Used by every form that picks a vendor so
     * the picker never grows past a usable size.
     */
    @GetMapping("/search")
    @org.springframework.web.bind.annotation.ResponseBody
    @PreAuthorize("isAuthenticated()")
    public java.util.List<java.util.Map<String, Object>> search(
            @RequestParam(value = "q", required = false) String q) {
        Page<Vendor> page = vendorService.findByFilters(
                Boolean.TRUE,
                q == null ? "" : q,
                org.springframework.data.domain.PageRequest.of(0, 10));
        java.util.List<java.util.Map<String, Object>> results = new java.util.ArrayList<>();
        for (Vendor v : page.getContent()) {
            results.add(java.util.Map.of(
                    "id", v.getId().toString(),
                    "code", v.getCode() == null ? "" : v.getCode(),
                    "name", v.getName() == null ? "" : v.getName()));
        }
        return results;
    }

    @Getter
    @Setter
    static class DefaultExpenseAccountRef {
        private UUID id;
    }

    @Getter
    @Setter
    static class VendorForm {
        private UUID id;

        @NotBlank(message = "Kode vendor wajib diisi")
        @Size(max = 50, message = "Kode vendor maksimal 50 karakter")
        private String code;

        @NotBlank(message = "Nama vendor wajib diisi")
        @Size(max = 255, message = "Nama vendor maksimal 255 karakter")
        private String name;

        @Size(max = 255, message = "Nama kontak maksimal 255 karakter")
        private String contactPerson;

        @Email(message = "Format email tidak valid")
        @Size(max = 255, message = "Email maksimal 255 karakter")
        private String email;

        @Size(max = 50, message = "Nomor telepon maksimal 50 karakter")
        private String phone;

        private String address;
        private String notes;

        @Size(max = 20, message = "NPWP maksimal 20 karakter")
        private String npwp;

        @Size(max = 22, message = "NITKU maksimal 22 karakter")
        private String nitku;

        @Size(max = 16, message = "NIK maksimal 16 karakter")
        private String nik;

        @Size(max = 10, message = "Tipe ID maksimal 10 karakter")
        private String idType;

        private Integer paymentTermDays;

        @Size(max = 100, message = "Nama bank maksimal 100 karakter")
        private String bankName;

        @Size(max = 50, message = "Nomor rekening maksimal 50 karakter")
        private String bankAccountNumber;

        @Size(max = 255, message = "Nama pemilik rekening maksimal 255 karakter")
        private String bankAccountName;

        private DefaultExpenseAccountRef defaultExpenseAccount = new DefaultExpenseAccountRef();
    }

    private Vendor toEntity(VendorForm form) {
        Vendor vendor = new Vendor();
        BeanUtils.copyProperties(form, vendor, "id", "defaultExpenseAccount");
        if (form.getDefaultExpenseAccount() != null && form.getDefaultExpenseAccount().getId() != null) {
            ChartOfAccount account = new ChartOfAccount();
            account.setId(form.getDefaultExpenseAccount().getId());
            vendor.setDefaultExpenseAccount(account);
        }
        return vendor;
    }

    private VendorForm toForm(Vendor vendor) {
        VendorForm form = new VendorForm();
        BeanUtils.copyProperties(vendor, form, "defaultExpenseAccount");
        if (vendor.getDefaultExpenseAccount() != null) {
            DefaultExpenseAccountRef ref = new DefaultExpenseAccountRef();
            ref.setId(vendor.getDefaultExpenseAccount().getId());
            form.setDefaultExpenseAccount(ref);
        }
        return form;
    }

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
    @PreAuthorize("hasAuthority('" + Permission.VENDOR_CREATE + "')")
    public String newForm(Model model) {
        model.addAttribute(ATTR_VENDOR, new VendorForm());
        model.addAttribute(ATTR_EXPENSE_ACCOUNTS, chartOfAccountService.findByAccountType(AccountType.EXPENSE));
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_VENDORS);
        return VIEW_VENDORS_FORM;
    }

    @PostMapping("/new")
    @PreAuthorize("hasAuthority('" + Permission.VENDOR_CREATE + "')")
    public String create(
            @Valid @ModelAttribute(ATTR_VENDOR) VendorForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            model.addAttribute(ATTR_EXPENSE_ACCOUNTS, chartOfAccountService.findByAccountType(AccountType.EXPENSE));
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_VENDORS);
            return VIEW_VENDORS_FORM;
        }

        try {
            Vendor vendor = toEntity(form);
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
    @PreAuthorize("hasAuthority('" + Permission.VENDOR_EDIT + "')")
    public String editForm(@PathVariable String code, Model model) {
        Vendor vendor = vendorService.findByCode(code);

        model.addAttribute(ATTR_VENDOR, toForm(vendor));
        model.addAttribute(ATTR_EXPENSE_ACCOUNTS, chartOfAccountService.findByAccountType(AccountType.EXPENSE));
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_VENDORS);
        model.addAttribute(ATTR_IS_EDIT, true);
        return VIEW_VENDORS_FORM;
    }

    @PostMapping("/{code}")
    @PreAuthorize("hasAuthority('" + Permission.VENDOR_EDIT + "')")
    public String update(
            @PathVariable String code,
            @Valid @ModelAttribute(ATTR_VENDOR) VendorForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            Vendor existing = vendorService.findByCode(code);
            form.setId(existing.getId());
            model.addAttribute(ATTR_EXPENSE_ACCOUNTS, chartOfAccountService.findByAccountType(AccountType.EXPENSE));
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_VENDORS);
            model.addAttribute(ATTR_IS_EDIT, true);
            return VIEW_VENDORS_FORM;
        }

        try {
            Vendor existing = vendorService.findByCode(code);
            Vendor vendor = toEntity(form);
            vendorService.update(existing.getId(), vendor);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Vendor berhasil diperbarui");
            return REDIRECT_VENDORS + form.getCode();
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("code", "duplicate", e.getMessage());
            Vendor existing = vendorService.findByCode(code);
            form.setId(existing.getId());
            model.addAttribute(ATTR_EXPENSE_ACCOUNTS, chartOfAccountService.findByAccountType(AccountType.EXPENSE));
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_VENDORS);
            model.addAttribute(ATTR_IS_EDIT, true);
            return VIEW_VENDORS_FORM;
        }
    }

    @PostMapping("/{code}/deactivate")
    @PreAuthorize("hasAuthority('" + Permission.VENDOR_DELETE + "')")
    public String deactivate(@PathVariable String code, RedirectAttributes redirectAttributes) {
        Vendor vendor = vendorService.findByCode(code);
        vendorService.deactivate(vendor.getId());
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Vendor berhasil dinonaktifkan");
        return REDIRECT_VENDORS + code;
    }

    @PostMapping("/{code}/activate")
    @PreAuthorize("hasAuthority('" + Permission.VENDOR_EDIT + "')")
    public String activate(@PathVariable String code, RedirectAttributes redirectAttributes) {
        Vendor vendor = vendorService.findByCode(code);
        vendorService.activate(vendor.getId());
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Vendor berhasil diaktifkan");
        return REDIRECT_VENDORS + code;
    }
}
