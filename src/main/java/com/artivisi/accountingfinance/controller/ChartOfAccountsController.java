package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.enums.AccountType;
import com.artivisi.accountingfinance.enums.NormalBalance;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.ChartOfAccountService;
import com.artivisi.accountingfinance.util.FormUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.BeanUtils;
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

@Controller
@RequestMapping("/accounts")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permission.ACCOUNT_VIEW + "')")
public class ChartOfAccountsController {

    private static final String ATTR_SUCCESS_MESSAGE = "successMessage";
    private static final String ATTR_ERROR_MESSAGE = "errorMessage";
    private static final String ATTR_ACCOUNT_TYPES = "accountTypes";
    private static final String ATTR_PARENT_ACCOUNTS = "parentAccounts";
    private static final String ATTR_HAS_CHILDREN = "hasChildren";
    private static final String ATTR_HAS_PARENT = "hasParent";
    private static final String VIEW_FORM = "accounts/form";
    private static final String REDIRECT_ACCOUNTS = "redirect:/accounts";

    private final ChartOfAccountService chartOfAccountService;

    /**
     * Typeahead search for account pickers. Returns at most 10 active, non-header
     * accounts matching q (by code or name). Used by every form that needs to pick
     * a COA account — keeps each dropdown under 10 items per the UX rule.
     */
    @org.springframework.web.bind.annotation.GetMapping("/search")
    @org.springframework.web.bind.annotation.ResponseBody
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public java.util.List<java.util.Map<String, Object>> search(
            @org.springframework.web.bind.annotation.RequestParam(value = "q", required = false) String q) {
        String search = q == null ? "" : q.toLowerCase().trim();
        java.util.List<java.util.Map<String, Object>> results = new java.util.ArrayList<>();
        for (com.artivisi.accountingfinance.entity.ChartOfAccount a : chartOfAccountService.findTransactableAccounts()) {
            if (results.size() >= 10) break;
            boolean matches = search.isEmpty()
                    || a.getAccountCode().toLowerCase().contains(search)
                    || a.getAccountName().toLowerCase().contains(search);
            if (matches) {
                results.add(java.util.Map.of(
                        "id", a.getId().toString(),
                        "code", a.getAccountCode(),
                        "name", a.getAccountName()));
            }
        }
        return results;
    }

    @Getter
    @Setter
    static class ParentRef {
        private UUID id;
    }

    @Getter
    @Setter
    static class AccountForm {
        private UUID id;

        @NotBlank(message = "Kode akun harus diisi")
        @Size(max = 20, message = "Kode akun maksimal 20 karakter")
        private String accountCode;

        @NotBlank(message = "Nama akun harus diisi")
        @Size(max = 255, message = "Nama akun maksimal 255 karakter")
        private String accountName;

        private AccountType accountType;
        private NormalBalance normalBalance;
        private Boolean isHeader;
        private Boolean permanent;
        private Boolean active;
        private String description;

        // Used by Thymeleaf for dropdown pre-selection (account.parent.id)
        private ParentRef parent;
    }

    private ChartOfAccount toEntity(AccountForm form) {
        ChartOfAccount entity = new ChartOfAccount();
        BeanUtils.copyProperties(form, entity, "id", "parent", "isHeader", "permanent", "active");
        entity.setIsHeader(FormUtils.checkboxValue(form.getIsHeader()));
        entity.setPermanent(FormUtils.checkboxValue(form.getPermanent()));
        entity.setActive(FormUtils.checkboxValue(form.getActive()));
        return entity;
    }

    private AccountForm toForm(ChartOfAccount entity) {
        AccountForm form = new AccountForm();
        BeanUtils.copyProperties(entity, form, "parent");
        if (entity.getParent() != null) {
            ParentRef ref = new ParentRef();
            ref.setId(entity.getParent().getId());
            form.setParent(ref);
        }
        return form;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_ACCOUNTS);
        model.addAttribute("accounts", chartOfAccountService.findRootAccounts());
        return "accounts/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('" + Permission.ACCOUNT_CREATE + "')")
    public String create(Model model) {
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_ACCOUNTS);
        model.addAttribute("account", new AccountForm());
        model.addAttribute(ATTR_ACCOUNT_TYPES, AccountType.values());
        model.addAttribute(ATTR_PARENT_ACCOUNTS, chartOfAccountService.findAll());
        model.addAttribute(ATTR_HAS_CHILDREN, false);
        model.addAttribute(ATTR_HAS_PARENT, false);
        return VIEW_FORM;
    }

    @PostMapping("/new")
    @PreAuthorize("hasAuthority('" + Permission.ACCOUNT_CREATE + "')")
    public String save(@Valid @ModelAttribute("account") AccountForm form,
                       BindingResult bindingResult,
                       @RequestParam(required = false) UUID parentId,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_ACCOUNTS);
            model.addAttribute(ATTR_ACCOUNT_TYPES, AccountType.values());
            model.addAttribute(ATTR_PARENT_ACCOUNTS, chartOfAccountService.findAll());
            model.addAttribute(ATTR_HAS_CHILDREN, false);
            model.addAttribute(ATTR_HAS_PARENT, parentId != null);
            return VIEW_FORM;
        }

        try {
            ChartOfAccount account = toEntity(form);
            account.setActive(true); // new accounts are always active
            // Set parent - service will inherit accountType and normalBalance from parent
            if (parentId != null) {
                ChartOfAccount parent = chartOfAccountService.findById(parentId);
                account.setParent(parent);
            }
            chartOfAccountService.create(account);
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("accountCode", "duplicate", e.getMessage());
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_ACCOUNTS);
            model.addAttribute(ATTR_ACCOUNT_TYPES, AccountType.values());
            model.addAttribute(ATTR_PARENT_ACCOUNTS, chartOfAccountService.findAll());
            model.addAttribute(ATTR_HAS_CHILDREN, false);
            model.addAttribute(ATTR_HAS_PARENT, parentId != null);
            return VIEW_FORM;
        }

        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Akun berhasil ditambahkan");
        return REDIRECT_ACCOUNTS;
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('" + Permission.ACCOUNT_EDIT + "')")
    public String edit(@PathVariable UUID id, Model model) {
        ChartOfAccount existing = chartOfAccountService.findById(id);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_ACCOUNTS);
        model.addAttribute("account", toForm(existing));
        model.addAttribute(ATTR_ACCOUNT_TYPES, AccountType.values());
        model.addAttribute(ATTR_PARENT_ACCOUNTS, chartOfAccountService.findAll());
        model.addAttribute(ATTR_HAS_CHILDREN, !existing.getChildren().isEmpty());
        model.addAttribute(ATTR_HAS_PARENT, existing.getParent() != null);
        return VIEW_FORM;
    }

    @PostMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('" + Permission.ACCOUNT_EDIT + "')")
    public String update(@PathVariable UUID id,
                         @Valid @ModelAttribute("account") AccountForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        ChartOfAccount existing = chartOfAccountService.findById(id);
        boolean hasChildren = !existing.getChildren().isEmpty();
        boolean hasParent = existing.getParent() != null;

        if (bindingResult.hasErrors()) {
            form.setId(id);
            if (hasParent) {
                ParentRef ref = new ParentRef();
                ref.setId(existing.getParent().getId());
                form.setParent(ref);
            }
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_ACCOUNTS);
            model.addAttribute(ATTR_ACCOUNT_TYPES, AccountType.values());
            model.addAttribute(ATTR_PARENT_ACCOUNTS, chartOfAccountService.findAll());
            model.addAttribute(ATTR_HAS_CHILDREN, hasChildren);
            model.addAttribute(ATTR_HAS_PARENT, hasParent);
            return VIEW_FORM;
        }

        try {
            ChartOfAccount account = toEntity(form);
            // If account has parent, set parent reference for proper processing
            // Service will use existing parent's accountType and normalBalance
            if (hasParent) {
                account.setParent(existing.getParent());
            }
            chartOfAccountService.update(id, account);
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("accountCode", "duplicate", e.getMessage());
            form.setId(id);
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_ACCOUNTS);
            model.addAttribute(ATTR_ACCOUNT_TYPES, AccountType.values());
            model.addAttribute(ATTR_PARENT_ACCOUNTS, chartOfAccountService.findAll());
            model.addAttribute(ATTR_HAS_CHILDREN, hasChildren);
            model.addAttribute(ATTR_HAS_PARENT, hasParent);
            return VIEW_FORM;
        } catch (IllegalStateException e) {
            bindingResult.rejectValue("accountType", "invalid", e.getMessage());
            form.setId(id);
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_ACCOUNTS);
            model.addAttribute(ATTR_ACCOUNT_TYPES, AccountType.values());
            model.addAttribute(ATTR_PARENT_ACCOUNTS, chartOfAccountService.findAll());
            model.addAttribute(ATTR_HAS_CHILDREN, hasChildren);
            model.addAttribute(ATTR_HAS_PARENT, hasParent);
            return VIEW_FORM;
        }

        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Akun berhasil diperbarui");
        return REDIRECT_ACCOUNTS;
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('" + Permission.ACCOUNT_EDIT + "')")
    public String activate(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        chartOfAccountService.activate(id);
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Akun berhasil diaktifkan");
        return REDIRECT_ACCOUNTS;
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('" + Permission.ACCOUNT_EDIT + "')")
    public String deactivate(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        chartOfAccountService.deactivate(id);
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Akun berhasil dinonaktifkan");
        return REDIRECT_ACCOUNTS;
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('" + Permission.ACCOUNT_DELETE + "')")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            chartOfAccountService.delete(id);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Akun berhasil dihapus");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR_MESSAGE, e.getMessage());
        }
        return REDIRECT_ACCOUNTS;
    }
}
