package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.enums.AccountType;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.ChartOfAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

@Controller
@RequestMapping("/accounts")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permission.ACCOUNT_VIEW + "')")
public class ChartOfAccountsController {

    private static final String ATTR_SUCCESS_MESSAGE = "successMessage";
    private static final String ATTR_ERROR_MESSAGE = "errorMessage";
    private static final String ATTR_CURRENT_PAGE = "currentPage";

    private final ChartOfAccountService chartOfAccountService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute(ATTR_CURRENT_PAGE, "accounts");
        model.addAttribute("accounts", chartOfAccountService.findRootAccounts());
        return "accounts/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('" + Permission.ACCOUNT_CREATE + "')")
    public String create(Model model) {
        model.addAttribute(ATTR_CURRENT_PAGE, "accounts");
        model.addAttribute("account", new ChartOfAccount());
        model.addAttribute("accountTypes", AccountType.values());
        model.addAttribute("parentAccounts", chartOfAccountService.findAll());
        model.addAttribute("hasChildren", false);
        model.addAttribute("hasParent", false);
        return "accounts/form";
    }

    @PostMapping("/new")
    @PreAuthorize("hasAuthority('" + Permission.ACCOUNT_CREATE + "')")
    public String save(@Valid @ModelAttribute("account") ChartOfAccount account,
                       BindingResult bindingResult,
                       @RequestParam(required = false) UUID parentId,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute(ATTR_CURRENT_PAGE, "accounts");
            model.addAttribute("accountTypes", AccountType.values());
            model.addAttribute("parentAccounts", chartOfAccountService.findAll());
            model.addAttribute("hasChildren", false);
            model.addAttribute("hasParent", parentId != null);
            return "accounts/form";
        }

        if (parentId != null) {
            ChartOfAccount parent = chartOfAccountService.findById(parentId);
            account.setParent(parent);
        }

        try {
            chartOfAccountService.create(account);
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("accountCode", "duplicate", e.getMessage());
            model.addAttribute(ATTR_CURRENT_PAGE, "accounts");
            model.addAttribute("accountTypes", AccountType.values());
            model.addAttribute("parentAccounts", chartOfAccountService.findAll());
            model.addAttribute("hasChildren", false);
            model.addAttribute("hasParent", parentId != null);
            return "accounts/form";
        }

        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Akun berhasil ditambahkan");
        return "redirect:/accounts";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('" + Permission.ACCOUNT_EDIT + "')")
    public String edit(@PathVariable UUID id, Model model) {
        ChartOfAccount account = chartOfAccountService.findById(id);
        model.addAttribute(ATTR_CURRENT_PAGE, "accounts");
        model.addAttribute("account", account);
        model.addAttribute("accountTypes", AccountType.values());
        model.addAttribute("parentAccounts", chartOfAccountService.findAll());
        model.addAttribute("hasChildren", !account.getChildren().isEmpty());
        model.addAttribute("hasParent", account.getParent() != null);
        return "accounts/form";
    }

    @PostMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('" + Permission.ACCOUNT_EDIT + "')")
    public String update(@PathVariable UUID id,
                         @Valid @ModelAttribute("account") ChartOfAccount account,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        boolean hasChildren = chartOfAccountService.hasChildren(id);
        boolean hasParent = chartOfAccountService.hasParent(id);

        if (bindingResult.hasErrors()) {
            model.addAttribute(ATTR_CURRENT_PAGE, "accounts");
            model.addAttribute("accountTypes", AccountType.values());
            model.addAttribute("parentAccounts", chartOfAccountService.findAll());
            model.addAttribute("hasChildren", hasChildren);
            model.addAttribute("hasParent", hasParent);
            return "accounts/form";
        }

        try {
            chartOfAccountService.update(id, account);
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("accountCode", "duplicate", e.getMessage());
            model.addAttribute(ATTR_CURRENT_PAGE, "accounts");
            model.addAttribute("accountTypes", AccountType.values());
            model.addAttribute("parentAccounts", chartOfAccountService.findAll());
            model.addAttribute("hasChildren", hasChildren);
            model.addAttribute("hasParent", hasParent);
            return "accounts/form";
        } catch (IllegalStateException e) {
            bindingResult.rejectValue("accountType", "invalid", e.getMessage());
            model.addAttribute(ATTR_CURRENT_PAGE, "accounts");
            model.addAttribute("accountTypes", AccountType.values());
            model.addAttribute("parentAccounts", chartOfAccountService.findAll());
            model.addAttribute("hasChildren", hasChildren);
            model.addAttribute("hasParent", hasParent);
            return "accounts/form";
        }

        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Akun berhasil diperbarui");
        return "redirect:/accounts";
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('" + Permission.ACCOUNT_EDIT + "')")
    public String activate(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        chartOfAccountService.activate(id);
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Akun berhasil diaktifkan");
        return "redirect:/accounts";
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('" + Permission.ACCOUNT_EDIT + "')")
    public String deactivate(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        chartOfAccountService.deactivate(id);
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Akun berhasil dinonaktifkan");
        return "redirect:/accounts";
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
        return "redirect:/accounts";
    }
}
