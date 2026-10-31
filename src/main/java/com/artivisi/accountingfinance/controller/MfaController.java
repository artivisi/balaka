package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.security.TotpService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/settings/mfa")
public class MfaController {

    private final TotpService totpService;
    private final UserRepository userRepository;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public MfaController(TotpService totpService, UserRepository userRepository) {
        this.totpService = totpService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String mfaSetup(HttpServletRequest request, Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return "redirect:/login";
        }

        User user = userRepository.findByUsername(auth.getName()).orElse(null);
        if (user == null) {
            return "redirect:/login";
        }

        // Read MFA error message from session
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("MFA_ERROR_MESSAGE") != null) {
            model.addAttribute("errorMessage", session.getAttribute("MFA_ERROR_MESSAGE"));
            session.removeAttribute("MFA_ERROR_MESSAGE");
        }

        if (user.getMfaEnabled()) {
            model.addAttribute("mfaEnabled", true);
            model.addAttribute("username", user.getUsername());
            return "settings/mfa-status";
        }

        String secret = totpService.generateSecret();
        String otpAuthUri = totpService.getOtpAuthUri(secret, user.getUsername(), "Balaka");
        model.addAttribute("secret", secret);
        model.addAttribute("otpAuthUri", otpAuthUri);
        model.addAttribute("username", user.getUsername());
        model.addAttribute("mfaEnabled", false);

        return "settings/mfa-setup";
    }

    @PostMapping("/enable")
    public String enableMfa(@RequestParam("secret") String secret,
                           @RequestParam("code") String code,
                           HttpServletRequest request,
                           RedirectAttributes redirectAttributes) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return "redirect:/login";
        }

        User user = userRepository.findByUsername(auth.getName()).orElse(null);
        if (user == null) {
            return "redirect:/login";
        }

        if (totpService.verifyCode(secret, code)) {
            user.setMfaSecret(secret);
            user.setMfaEnabled(true);
            userRepository.save(user);
            redirectAttributes.addFlashAttribute("successMessage", "MFA berhasil diaktifkan!");
            return "redirect:/dashboard";
        }

        if (request.getSession(false) != null) {
            request.getSession().setAttribute("MFA_ERROR_MESSAGE", "Kode OTP tidak valid. Silakan coba lagi.");
        }
        return "redirect:/login";
    }

    @PostMapping("/disable")
    public String disableMfa(@RequestParam("code") String code,
                            HttpServletRequest request,
                            RedirectAttributes redirectAttributes) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return "redirect:/login";
        }

        User user = userRepository.findByUsername(auth.getName()).orElse(null);
        if (user == null || !user.getMfaEnabled()) {
            return "redirect:/settings/mfa";
        }

        if (totpService.verifyCode(user.getMfaSecret(), code)) {
            user.setMfaSecret(null);
            user.setMfaEnabled(false);
            userRepository.save(user);
            redirectAttributes.addFlashAttribute("successMessage", "MFA berhasil dinonaktifkan!");
            return "redirect:/settings/mfa";
        }

        if (request.getSession(false) != null) {
            request.getSession().setAttribute("MFA_ERROR_MESSAGE", "Kode OTP tidak valid. Silakan coba lagi.");
        }
        return "redirect:/settings/mfa";
    }

}
