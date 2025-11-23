package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.enums.AccountType;
import com.artivisi.accountingfinance.service.ChartOfAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
public class ChartOfAccountsController {

    private final ChartOfAccountService chartOfAccountService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("currentPage", "accounts");
        model.addAttribute("accounts", chartOfAccountService.findRootAccounts());
        return "accounts/list";
    }

    @GetMapping("/new")
    public String create(Model model) {
        model.addAttribute("currentPage", "accounts");
        model.addAttribute("account", new ChartOfAccount());
        model.addAttribute("accountTypes", AccountType.values());
        model.addAttribute("parentAccounts", chartOfAccountService.findAll());
        return "accounts/form";
    }

    @PostMapping("/new")
    public String save(@Valid @ModelAttribute("account") ChartOfAccount account,
                       BindingResult bindingResult,
                       @RequestParam(required = false) UUID parentId,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("currentPage", "accounts");
            model.addAttribute("accountTypes", AccountType.values());
            model.addAttribute("parentAccounts", chartOfAccountService.findAll());
            return "accounts/form";
        }

        if (parentId != null) {
            ChartOfAccount parent = chartOfAccountService.findById(parentId);
            account.setParent(parent);
        }

        chartOfAccountService.create(account);
        redirectAttributes.addFlashAttribute("successMessage", "Akun berhasil ditambahkan");
        return "redirect:/accounts";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, Model model) {
        model.addAttribute("currentPage", "accounts");
        model.addAttribute("account", chartOfAccountService.findById(id));
        model.addAttribute("accountTypes", AccountType.values());
        model.addAttribute("parentAccounts", chartOfAccountService.findAll());
        return "accounts/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable UUID id,
                         @Valid @ModelAttribute("account") ChartOfAccount account,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("currentPage", "accounts");
            model.addAttribute("accountTypes", AccountType.values());
            model.addAttribute("parentAccounts", chartOfAccountService.findAll());
            return "accounts/form";
        }

        chartOfAccountService.update(id, account);
        redirectAttributes.addFlashAttribute("successMessage", "Akun berhasil diperbarui");
        return "redirect:/accounts";
    }

    @PostMapping("/{id}/activate")
    public String activate(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        chartOfAccountService.activate(id);
        redirectAttributes.addFlashAttribute("successMessage", "Akun berhasil diaktifkan");
        return "redirect:/accounts";
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        chartOfAccountService.deactivate(id);
        redirectAttributes.addFlashAttribute("successMessage", "Akun berhasil dinonaktifkan");
        return "redirect:/accounts";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        chartOfAccountService.delete(id);
        redirectAttributes.addFlashAttribute("successMessage", "Akun berhasil dihapus");
        return "redirect:/accounts";
    }
}
