package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.CompanyBankAccount;
import com.artivisi.accountingfinance.entity.CompanyConfig;
import com.artivisi.accountingfinance.entity.TelegramUserLink;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.repository.TelegramUserLinkRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.service.CompanyBankAccountService;
import com.artivisi.accountingfinance.service.CompanyConfigService;
import com.artivisi.accountingfinance.service.TelegramBotService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Controller
@RequestMapping("/settings")
@RequiredArgsConstructor
public class SettingsController {

    private static final String ATTR_SUCCESS_MESSAGE = "successMessage";
    private static final String ATTR_CURRENT_PAGE = "currentPage";

    private final CompanyConfigService companyConfigService;
    private final CompanyBankAccountService bankAccountService;
    private final TelegramBotService telegramBotService;
    private final TelegramUserLinkRepository telegramLinkRepository;
    private final UserRepository userRepository;

    // ==================== Company Settings ====================

    @GetMapping
    public String companySettings(Model model) {
        CompanyConfig config = companyConfigService.getConfig();
        List<CompanyBankAccount> bankAccounts = bankAccountService.findAll();

        model.addAttribute("config", config);
        model.addAttribute("bankAccounts", bankAccounts);
        model.addAttribute(ATTR_CURRENT_PAGE, "settings");

        return "settings/company";
    }

    @PostMapping("/company")
    public String updateCompany(
            @Valid @ModelAttribute("config") CompanyConfig config,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            List<CompanyBankAccount> bankAccounts = bankAccountService.findAll();
            model.addAttribute("bankAccounts", bankAccounts);
            model.addAttribute(ATTR_CURRENT_PAGE, "settings");
            return "settings/company";
        }

        companyConfigService.update(config.getId(), config);
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Pengaturan perusahaan berhasil disimpan");
        return "redirect:/settings";
    }

    // ==================== Bank Accounts ====================

    @GetMapping("/bank-accounts")
    public String bankAccountsList(
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            Model model) {

        List<CompanyBankAccount> bankAccounts = bankAccountService.findAll();
        model.addAttribute("bankAccounts", bankAccounts);
        model.addAttribute(ATTR_CURRENT_PAGE, "settings");

        if ("true".equals(hxRequest)) {
            return "settings/fragments/bank-table :: table";
        }

        return "settings/bank-accounts";
    }

    @GetMapping("/bank-accounts/new")
    public String newBankAccountForm(Model model) {
        model.addAttribute("bankAccount", new CompanyBankAccount());
        model.addAttribute(ATTR_CURRENT_PAGE, "settings");
        return "settings/bank-form";
    }

    @PostMapping("/bank-accounts/new")
    public String createBankAccount(
            @Valid @ModelAttribute("bankAccount") CompanyBankAccount bankAccount,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            model.addAttribute(ATTR_CURRENT_PAGE, "settings");
            return "settings/bank-form";
        }

        try {
            bankAccountService.create(bankAccount);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Rekening bank berhasil ditambahkan");
            return "redirect:/settings";
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("accountNumber", "duplicate", e.getMessage());
            model.addAttribute(ATTR_CURRENT_PAGE, "settings");
            return "settings/bank-form";
        }
    }

    @GetMapping("/bank-accounts/{id}/edit")
    public String editBankAccountForm(@PathVariable UUID id, Model model) {
        CompanyBankAccount bankAccount = bankAccountService.findById(id);
        model.addAttribute("bankAccount", bankAccount);
        model.addAttribute(ATTR_CURRENT_PAGE, "settings");
        return "settings/bank-form";
    }

    @PostMapping("/bank-accounts/{id}")
    public String updateBankAccount(
            @PathVariable UUID id,
            @Valid @ModelAttribute("bankAccount") CompanyBankAccount bankAccount,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            bankAccount.setId(id);
            model.addAttribute(ATTR_CURRENT_PAGE, "settings");
            return "settings/bank-form";
        }

        try {
            bankAccountService.update(id, bankAccount);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Rekening bank berhasil diperbarui");
            return "redirect:/settings";
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("accountNumber", "duplicate", e.getMessage());
            bankAccount.setId(id);
            model.addAttribute(ATTR_CURRENT_PAGE, "settings");
            return "settings/bank-form";
        }
    }

    @PostMapping("/bank-accounts/{id}/set-default")
    public String setDefaultBankAccount(
            @PathVariable UUID id,
            RedirectAttributes redirectAttributes) {

        bankAccountService.setAsDefault(id);
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Rekening utama berhasil diubah");
        return "redirect:/settings";
    }

    @PostMapping("/bank-accounts/{id}/deactivate")
    public String deactivateBankAccount(
            @PathVariable UUID id,
            RedirectAttributes redirectAttributes) {

        bankAccountService.deactivate(id);
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Rekening bank berhasil dinonaktifkan");
        return "redirect:/settings";
    }

    @PostMapping("/bank-accounts/{id}/activate")
    public String activateBankAccount(
            @PathVariable UUID id,
            RedirectAttributes redirectAttributes) {

        bankAccountService.activate(id);
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Rekening bank berhasil diaktifkan");
        return "redirect:/settings";
    }

    @PostMapping("/bank-accounts/{id}/delete")
    public String deleteBankAccount(
            @PathVariable UUID id,
            RedirectAttributes redirectAttributes) {

        bankAccountService.delete(id);
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Rekening bank berhasil dihapus");
        return "redirect:/settings";
    }

    // ==================== Telegram Settings ====================

    @GetMapping("/telegram")
    public String telegramSettings(Authentication authentication, Model model) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        Optional<TelegramUserLink> telegramLink = telegramLinkRepository.findByUser(user);
        model.addAttribute("telegramLink", telegramLink.orElse(null));
        model.addAttribute("telegramEnabled", telegramBotService.isEnabled());
        model.addAttribute("botUsername", telegramBotService.getBotUsername());
        model.addAttribute(ATTR_CURRENT_PAGE, "settings");

        return "settings/telegram";
    }

    @PostMapping("/telegram/generate-code")
    public String generateTelegramCode(
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        String code = telegramBotService.generateVerificationCode(user);
        redirectAttributes.addFlashAttribute("verificationCode", code);
        redirectAttributes.addFlashAttribute("botUsername", telegramBotService.getBotUsername());

        return "redirect:/settings/telegram";
    }

    @PostMapping("/telegram/unlink")
    public String unlinkTelegram(
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        Optional<TelegramUserLink> link = telegramLinkRepository.findByUser(user);
        if (link.isPresent()) {
            TelegramUserLink telegramLink = link.get();
            telegramLink.setIsActive(false);
            telegramLink.setTelegramUserId(null);
            telegramLink.setTelegramUsername(null);
            telegramLink.setLinkedAt(null);
            telegramLinkRepository.save(telegramLink);
        }

        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Akun Telegram berhasil diputus");
        return "redirect:/settings/telegram";
    }
}
