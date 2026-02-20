package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.CompanyBankAccount;
import com.artivisi.accountingfinance.entity.CompanyConfig;
import com.artivisi.accountingfinance.entity.DeviceToken;
import com.artivisi.accountingfinance.entity.SecurityAuditLog;
import com.artivisi.accountingfinance.entity.TelegramUserLink;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.AuditEventType;
import com.artivisi.accountingfinance.repository.TelegramUserLinkRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.service.ChartOfAccountService;
import com.artivisi.accountingfinance.service.CompanyBankAccountService;
import com.artivisi.accountingfinance.service.CompanyConfigService;
import com.artivisi.accountingfinance.service.DeviceAuthService;
import com.artivisi.accountingfinance.service.DocumentStorageService;
import com.artivisi.accountingfinance.service.SecurityAuditService;
import com.artivisi.accountingfinance.service.TelegramBotService;
import com.artivisi.accountingfinance.service.VersionInfoService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.artivisi.accountingfinance.controller.ViewConstants.*;

@Controller
@RequestMapping("/settings")
@RequiredArgsConstructor
@Slf4j
@org.springframework.security.access.prepost.PreAuthorize("hasAuthority('" + com.artivisi.accountingfinance.security.Permission.SETTINGS_VIEW + "')")
public class SettingsController {

    private static final String ATTR_SUCCESS_MESSAGE = "successMessage";
    private static final String ATTR_ERROR_MESSAGE = "errorMessage";
    private static final String REDIRECT_SETTINGS = "redirect:/settings";
    private static final String VIEW_BANK_FORM = "settings/bank-form";
    private static final String ATTR_BANK_ACCOUNTS = "bankAccounts";
    private static final String ERR_USER_NOT_FOUND = "User not found";
    private static final Set<String> ALLOWED_LOGO_TYPES = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp"
    );
    private static final long MAX_LOGO_SIZE = 2L * 1024L * 1024L; // 2MB

    private final CompanyConfigService companyConfigService;
    private final CompanyBankAccountService bankAccountService;
    private final ChartOfAccountService chartOfAccountService;
    private final DeviceAuthService deviceAuthService;
    private final DocumentStorageService documentStorageService;
    private final TelegramBotService telegramBotService;
    private final TelegramUserLinkRepository telegramLinkRepository;
    private final UserRepository userRepository;
    private final VersionInfoService versionInfoService;
    private final SecurityAuditService securityAuditService;

    // ==================== Company Settings ====================

    @GetMapping
    public String companySettings(Model model) {
        CompanyConfig config = companyConfigService.getConfig();
        List<CompanyBankAccount> bankAccounts = bankAccountService.findAll();

        model.addAttribute("config", config);
        model.addAttribute(ATTR_BANK_ACCOUNTS, bankAccounts);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_SETTINGS);

        return "settings/company";
    }

    @PostMapping("/company")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('" + com.artivisi.accountingfinance.security.Permission.SETTINGS_EDIT + "')")
    public String updateCompany(
            @Valid @ModelAttribute("config") CompanyConfig config,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            List<CompanyBankAccount> bankAccounts = bankAccountService.findAll();
            model.addAttribute(ATTR_BANK_ACCOUNTS, bankAccounts);
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_SETTINGS);
            return "settings/company";
        }

        companyConfigService.update(config.getId(), config);
        securityAuditService.log(AuditEventType.SETTINGS_CHANGE,
                "Company settings updated: " + config.getCompanyName());
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Pengaturan perusahaan berhasil disimpan");
        return REDIRECT_SETTINGS;
    }

    // ==================== Company Logo ====================

    @PostMapping("/company/logo")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('" + com.artivisi.accountingfinance.security.Permission.SETTINGS_EDIT + "')")
    public String uploadCompanyLogo(
            @RequestParam("logoFile") MultipartFile logoFile,
            RedirectAttributes redirectAttributes) {

        if (logoFile.isEmpty()) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR_MESSAGE, "File tidak boleh kosong");
            return REDIRECT_SETTINGS;
        }

        String contentType = logoFile.getContentType();
        if (contentType == null || !ALLOWED_LOGO_TYPES.contains(contentType)) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR_MESSAGE,
                    "Format file tidak didukung. Gunakan PNG, JPG, GIF, atau WebP.");
            return REDIRECT_SETTINGS;
        }

        if (logoFile.getSize() > MAX_LOGO_SIZE) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR_MESSAGE,
                    "Ukuran file terlalu besar. Maksimal 2MB.");
            return REDIRECT_SETTINGS;
        }

        try {
            // Delete old logo if exists
            CompanyConfig config = companyConfigService.getConfig();
            if (config.getCompanyLogoPath() != null && !config.getCompanyLogoPath().isEmpty()) {
                tryDeleteOldLogo(config.getCompanyLogoPath());
            }

            // Store new logo
            String storedPath = documentStorageService.store(logoFile);
            config.setCompanyLogoPath(storedPath);
            companyConfigService.save(config);

            securityAuditService.log(AuditEventType.SETTINGS_CHANGE, "Company logo uploaded");
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Logo perusahaan berhasil diupload");
        } catch (IOException e) {
            log.error("Failed to upload company logo: {}", e.getMessage());
            redirectAttributes.addFlashAttribute(ATTR_ERROR_MESSAGE,
                    "Gagal mengupload logo: " + e.getMessage());
        }

        return REDIRECT_SETTINGS;
    }

    @GetMapping("/company/logo")
    @ResponseBody
    public ResponseEntity<Resource> getCompanyLogo() {
        CompanyConfig config = companyConfigService.getConfig();

        if (config.getCompanyLogoPath() == null || config.getCompanyLogoPath().isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        try {
            Resource resource = documentStorageService.loadAsResource(config.getCompanyLogoPath());

            // Determine content type from file extension
            String filename = config.getCompanyLogoPath();
            String contentType = determineContentType(filename);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                    .body(resource);
        } catch (Exception e) {
            log.error("Failed to load company logo: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/company/logo/delete")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('" + com.artivisi.accountingfinance.security.Permission.SETTINGS_EDIT + "')")
    public String deleteCompanyLogo(RedirectAttributes redirectAttributes) {
        CompanyConfig config = companyConfigService.getConfig();

        if (config.getCompanyLogoPath() != null && !config.getCompanyLogoPath().isEmpty()) {
            try {
                documentStorageService.delete(config.getCompanyLogoPath());
            } catch (IOException e) {
                log.warn("Failed to delete logo file: {}", e.getMessage());
            }

            config.setCompanyLogoPath(null);
            companyConfigService.save(config);
            securityAuditService.log(AuditEventType.SETTINGS_CHANGE, "Company logo deleted");
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Logo perusahaan berhasil dihapus");
        }

        return REDIRECT_SETTINGS;
    }

    private String determineContentType(String filename) {
        if (filename == null) return "application/octet-stream";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }

    /**
     * Mask bank account number for audit logging (show only last 4 digits).
     */
    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return "****";
        }
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }

    // ==================== Bank Accounts ====================

    @GetMapping("/bank-accounts")
    public String bankAccountsList(
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            Model model) {

        List<CompanyBankAccount> bankAccounts = bankAccountService.findAll();
        model.addAttribute(ATTR_BANK_ACCOUNTS, bankAccounts);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_SETTINGS);

        if ("true".equals(hxRequest)) {
            return "settings/fragments/bank-table :: table";
        }

        return "settings/bank-accounts";
    }

    @GetMapping("/bank-accounts/new")
    public String newBankAccountForm(Model model) {
        model.addAttribute("bankAccount", new CompanyBankAccount());
        model.addAttribute("glAccounts", chartOfAccountService.findTransactableAccounts());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_SETTINGS);
        return VIEW_BANK_FORM;
    }

    @PostMapping("/bank-accounts/new")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('" + com.artivisi.accountingfinance.security.Permission.SETTINGS_EDIT + "')")
    public String createBankAccount(
            @Valid @ModelAttribute("bankAccount") CompanyBankAccount bankAccount,
            BindingResult bindingResult,
            @RequestParam(value = "glAccountId", required = false) UUID glAccountId,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (glAccountId != null) {
            bankAccount.setGlAccount(chartOfAccountService.findById(glAccountId));
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("glAccounts", chartOfAccountService.findTransactableAccounts());
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_SETTINGS);
            return VIEW_BANK_FORM;
        }

        try {
            bankAccountService.create(bankAccount);
            securityAuditService.log(AuditEventType.SETTINGS_CHANGE,
                    "Bank account created: " + bankAccount.getBankName() + " - " + maskAccountNumber(bankAccount.getAccountNumber()));
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Rekening bank berhasil ditambahkan");
            return REDIRECT_SETTINGS;
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("accountNumber", "duplicate", e.getMessage());
            model.addAttribute("glAccounts", chartOfAccountService.findTransactableAccounts());
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_SETTINGS);
            return VIEW_BANK_FORM;
        }
    }

    @GetMapping("/bank-accounts/{id}/edit")
    public String editBankAccountForm(@PathVariable UUID id, Model model) {
        CompanyBankAccount bankAccount = bankAccountService.findById(id);
        model.addAttribute("bankAccount", bankAccount);
        model.addAttribute("glAccounts", chartOfAccountService.findTransactableAccounts());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_SETTINGS);
        return VIEW_BANK_FORM;
    }

    @PostMapping("/bank-accounts/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('" + com.artivisi.accountingfinance.security.Permission.SETTINGS_EDIT + "')")
    public String updateBankAccount(
            @PathVariable UUID id,
            @Valid @ModelAttribute("bankAccount") CompanyBankAccount bankAccount,
            BindingResult bindingResult,
            @RequestParam(value = "glAccountId", required = false) UUID glAccountId,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (glAccountId != null) {
            bankAccount.setGlAccount(chartOfAccountService.findById(glAccountId));
        } else {
            bankAccount.setGlAccount(null);
        }

        if (bindingResult.hasErrors()) {
            bankAccount.setId(id);
            model.addAttribute("glAccounts", chartOfAccountService.findTransactableAccounts());
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_SETTINGS);
            return VIEW_BANK_FORM;
        }

        try {
            bankAccountService.update(id, bankAccount);
            securityAuditService.log(AuditEventType.SETTINGS_CHANGE,
                    "Bank account updated: " + bankAccount.getBankName() + " - " + maskAccountNumber(bankAccount.getAccountNumber()));
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Rekening bank berhasil diperbarui");
            return REDIRECT_SETTINGS;
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("accountNumber", "duplicate", e.getMessage());
            bankAccount.setId(id);
            model.addAttribute("glAccounts", chartOfAccountService.findTransactableAccounts());
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_SETTINGS);
            return VIEW_BANK_FORM;
        }
    }

    @PostMapping("/bank-accounts/{id}/set-default")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('" + com.artivisi.accountingfinance.security.Permission.SETTINGS_EDIT + "')")
    public String setDefaultBankAccount(
            @PathVariable UUID id,
            RedirectAttributes redirectAttributes) {

        CompanyBankAccount bankAccount = bankAccountService.findById(id);
        bankAccountService.setAsDefault(id);
        securityAuditService.log(AuditEventType.SETTINGS_CHANGE,
                "Default bank account changed to: " + bankAccount.getBankName());
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Rekening utama berhasil diubah");
        return REDIRECT_SETTINGS;
    }

    @PostMapping("/bank-accounts/{id}/deactivate")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('" + com.artivisi.accountingfinance.security.Permission.SETTINGS_EDIT + "')")
    public String deactivateBankAccount(
            @PathVariable UUID id,
            RedirectAttributes redirectAttributes) {

        CompanyBankAccount bankAccount = bankAccountService.findById(id);
        bankAccountService.deactivate(id);
        securityAuditService.log(AuditEventType.SETTINGS_CHANGE,
                "Bank account deactivated: " + bankAccount.getBankName());
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Rekening bank berhasil dinonaktifkan");
        return REDIRECT_SETTINGS;
    }

    @PostMapping("/bank-accounts/{id}/activate")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('" + com.artivisi.accountingfinance.security.Permission.SETTINGS_EDIT + "')")
    public String activateBankAccount(
            @PathVariable UUID id,
            RedirectAttributes redirectAttributes) {

        CompanyBankAccount bankAccount = bankAccountService.findById(id);
        bankAccountService.activate(id);
        securityAuditService.log(AuditEventType.SETTINGS_CHANGE,
                "Bank account activated: " + bankAccount.getBankName());
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Rekening bank berhasil diaktifkan");
        return REDIRECT_SETTINGS;
    }

    @PostMapping("/bank-accounts/{id}/delete")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('" + com.artivisi.accountingfinance.security.Permission.SETTINGS_EDIT + "')")
    public String deleteBankAccount(
            @PathVariable UUID id,
            RedirectAttributes redirectAttributes) {

        CompanyBankAccount bankAccount = bankAccountService.findById(id);
        String bankName = bankAccount.getBankName();
        bankAccountService.delete(id);
        securityAuditService.log(AuditEventType.SETTINGS_CHANGE,
                "Bank account deleted: " + bankName);
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Rekening bank berhasil dihapus");
        return REDIRECT_SETTINGS;
    }

    // ==================== Telegram Settings ====================

    @GetMapping("/telegram")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('" + com.artivisi.accountingfinance.security.Permission.TELEGRAM_MANAGE + "')")
    public String telegramSettings(Authentication authentication, Model model) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException(ERR_USER_NOT_FOUND));

        Optional<TelegramUserLink> telegramLink = telegramLinkRepository.findByUser(user);
        model.addAttribute("telegramLink", telegramLink.orElse(null));
        model.addAttribute("telegramEnabled", telegramBotService.isEnabled());
        model.addAttribute("botUsername", telegramBotService.getBotUsername());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_SETTINGS);

        return "settings/telegram";
    }

    @PostMapping("/telegram/generate-code")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('" + com.artivisi.accountingfinance.security.Permission.TELEGRAM_MANAGE + "')")
    public String generateTelegramCode(
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException(ERR_USER_NOT_FOUND));

        String code = telegramBotService.generateVerificationCode(user);
        securityAuditService.log(AuditEventType.SETTINGS_CHANGE,
                "Telegram verification code generated");
        redirectAttributes.addFlashAttribute("verificationCode", code);
        redirectAttributes.addFlashAttribute("botUsername", telegramBotService.getBotUsername());

        return "redirect:/settings/telegram";
    }

    @PostMapping("/telegram/unlink")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('" + com.artivisi.accountingfinance.security.Permission.TELEGRAM_MANAGE + "')")
    public String unlinkTelegram(
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException(ERR_USER_NOT_FOUND));

        Optional<TelegramUserLink> link = telegramLinkRepository.findByUser(user);
        if (link.isPresent()) {
            TelegramUserLink telegramLink = link.get();
            telegramLink.setIsActive(false);
            telegramLink.setTelegramUserId(null);
            telegramLink.setTelegramUsername(null);
            telegramLink.setLinkedAt(null);
            telegramLinkRepository.save(telegramLink);
            securityAuditService.log(AuditEventType.SETTINGS_CHANGE,
                    "Telegram account unlinked");
        }

        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Akun Telegram berhasil diputus");
        return "redirect:/settings/telegram";
    }

    // ==================== Device Token Management ====================

    @GetMapping("/devices")
    public String deviceTokens(Authentication authentication, Model model) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException(ERR_USER_NOT_FOUND));

        List<DeviceToken> tokens = deviceAuthService.getActiveTokens(user);
        model.addAttribute("deviceTokens", tokens);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_DEVICES);

        return "settings/devices";
    }

    @PostMapping("/devices/{id}/revoke")
    public String revokeDeviceToken(
            @PathVariable UUID id,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException(ERR_USER_NOT_FOUND));

        List<DeviceToken> userTokens = deviceAuthService.getActiveTokens(user);
        boolean belongsToUser = userTokens.stream().anyMatch(t -> t.getId().equals(id));
        if (!belongsToUser) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR_MESSAGE, "Token tidak ditemukan");
            return "redirect:/settings/devices";
        }

        deviceAuthService.revokeToken(id, username);
        securityAuditService.log(AuditEventType.SETTINGS_CHANGE,
                "Device token revoked: " + id);
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Sesi perangkat berhasil dicabut");
        return "redirect:/settings/devices";
    }

    @PostMapping("/devices/revoke-all")
    public String revokeAllDeviceTokens(
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException(ERR_USER_NOT_FOUND));

        int count = deviceAuthService.revokeAllTokens(user, username);
        securityAuditService.log(AuditEventType.SETTINGS_CHANGE,
                "All device tokens revoked: " + count + " tokens");
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE,
                count + " sesi perangkat berhasil dicabut");
        return "redirect:/settings/devices";
    }

    // ==================== About ====================

    @GetMapping("/about")
    public String about(Model model) {
        model.addAttribute("gitCommitId", versionInfoService.getGitCommitId());
        model.addAttribute("gitCommitShort", versionInfoService.getGitCommitShort());
        model.addAttribute("gitTag", versionInfoService.getGitTag());
        model.addAttribute("gitBranch", versionInfoService.getGitBranch());
        model.addAttribute("gitCommitDate", versionInfoService.getGitCommitDate());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_SETTINGS);
        return "settings/about";
    }

    // ==================== Privacy Policy ====================

    @GetMapping("/privacy")
    public String privacy(Model model) {
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_SETTINGS);
        return "privacy";
    }

    // ==================== Security Audit Logs ====================

    @GetMapping("/audit-logs")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('" + com.artivisi.accountingfinance.security.Permission.AUDIT_LOG_VIEW + "')")
    public String auditLogs(
            @RequestParam(required = false) AuditEventType eventType,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            Model model) {

        // Convert LocalDate to LocalDateTime for query
        LocalDateTime startDateTime = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime endDateTime = endDate != null ? endDate.atTime(LocalTime.MAX) : null;

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        Page<SecurityAuditLog> auditLogs = securityAuditService.search(
                eventType, username, startDateTime, endDateTime, pageRequest);

        model.addAttribute("auditLogs", auditLogs);
        model.addAttribute("eventTypes", AuditEventType.values());
        model.addAttribute("selectedEventType", eventType);
        model.addAttribute("selectedUsername", username);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_SETTINGS);

        if ("true".equals(hxRequest)) {
            return "settings/fragments/audit-log-table :: table";
        }

        return "settings/audit-logs";
    }

    private void tryDeleteOldLogo(String logoPath) {
        try {
            documentStorageService.delete(logoPath);
        } catch (IOException e) {
            log.warn("Failed to delete old logo: {}", e.getMessage());
        }
    }
}
