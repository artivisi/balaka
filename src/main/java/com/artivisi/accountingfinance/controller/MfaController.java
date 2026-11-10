package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.security.MfaEnforcementFilter;
import com.artivisi.accountingfinance.security.MfaService;
import com.artivisi.accountingfinance.security.TotpService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Self-service enrolment and management of the second factor.
 *
 * <p>The secret offered on the setup page is generated here and kept in the
 * session until the confirmation POST. The enable form therefore carries only
 * the code: a submitted {@code secret} parameter is ignored by design, because a
 * client-chosen secret is a secret the client already knows.
 */
@Controller
@RequestMapping("/settings/mfa")
public class MfaController {

    private static final String PENDING_SECRET = "MFA_PENDING_SECRET";

    private final MfaService mfaService;
    private final TotpService totpService;
    private final UserRepository userRepository;

    public MfaController(MfaService mfaService, TotpService totpService, UserRepository userRepository) {
        this.mfaService = mfaService;
        this.totpService = totpService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String page(@RequestParam(name = "required", required = false) boolean required,
                       HttpServletRequest request,
                       Model model) {
        User user = currentUser();
        if (user == null) {
            return "redirect:/login";
        }

        model.addAttribute("username", user.getUsername());
        model.addAttribute("mfaEnabled", Boolean.TRUE.equals(user.getMfaEnabled()));
        model.addAttribute("mfaAvailable", mfaService.isEnabled());
        model.addAttribute("mfaRequired", mfaService.isRequiredFor(user));
        model.addAttribute("enrollmentRequired", required && !Boolean.TRUE.equals(user.getMfaEnabled()));

        if (!mfaService.isEnabled() || Boolean.TRUE.equals(user.getMfaEnabled())) {
            if (Boolean.TRUE.equals(user.getMfaEnabled())) {
                model.addAttribute("remainingRecoveryCodes", mfaService.remainingRecoveryCodes(user));
            }
            return "settings/mfa";
        }

        HttpSession session = request.getSession(true);
        String secret = (String) session.getAttribute(PENDING_SECRET);
        if (secret == null) {
            secret = mfaService.startEnrollment(user);
            session.setAttribute(PENDING_SECRET, secret);
        }
        model.addAttribute("secret", secret);
        model.addAttribute("otpAuthUri", totpService.getOtpAuthUri(secret, user.getUsername(), "Balaka"));
        model.addAttribute("digits", totpService.getDigits());
        model.addAttribute("period", totpService.getStepSeconds());
        return "settings/mfa";
    }

    @PostMapping("/enable")
    public String enable(@RequestParam("code") String code,
                         HttpServletRequest request,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        User user = currentUser();
        if (user == null) {
            return "redirect:/login";
        }

        HttpSession session = request.getSession(false);
        String pendingSecret = session == null ? null : (String) session.getAttribute(PENDING_SECRET);
        if (pendingSecret == null) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Sesi setup sudah berakhir. Muat ulang halaman untuk mendapatkan kunci baru.");
            return "redirect:/settings/mfa";
        }

        model.addAttribute("username", user.getUsername());
        model.addAttribute("mfaAvailable", mfaService.isEnabled());
        model.addAttribute("mfaRequired", mfaService.isRequiredFor(user));

        try {
            List<String> recoveryCodes = mfaService.completeEnrollment(user, pendingSecret, code, user.getUsername());
            session.removeAttribute(PENDING_SECRET);
            session.setAttribute(MfaEnforcementFilter.VERIFIED_USER_SESSION_ATTRIBUTE, user.getUsername());

            model.addAttribute("mfaEnabled", true);
            model.addAttribute("recoveryCodes", recoveryCodes);
            model.addAttribute("successMessage", "MFA berhasil diaktifkan. Simpan kode pemulihan di tempat aman.");
            return "settings/mfa";
        } catch (IllegalArgumentException e) {
            model.addAttribute("mfaEnabled", false);
            model.addAttribute("secret", pendingSecret);
            model.addAttribute("otpAuthUri", totpService.getOtpAuthUri(pendingSecret, user.getUsername(), "Balaka"));
            model.addAttribute("digits", totpService.getDigits());
            model.addAttribute("period", totpService.getStepSeconds());
            model.addAttribute("errorMessage", e.getMessage());
            return "settings/mfa";
        }
    }

    @PostMapping("/recovery-codes")
    public String regenerateRecoveryCodes(@RequestParam("code") String code,
                                          Model model) {
        User user = currentUser();
        if (user == null) {
            return "redirect:/login";
        }
        if (mfaService.verify(user, code) != MfaService.VerifyOutcome.OK) {
            model.addAttribute("username", user.getUsername());
            model.addAttribute("mfaEnabled", true);
            model.addAttribute("mfaAvailable", mfaService.isEnabled());
            model.addAttribute("mfaRequired", mfaService.isRequiredFor(user));
            model.addAttribute("remainingRecoveryCodes", mfaService.remainingRecoveryCodes(user));
            model.addAttribute("errorMessage", "Kode OTP tidak valid, kode pemulihan tidak diubah.");
            return "settings/mfa";
        }

        List<String> recoveryCodes = mfaService.regenerateRecoveryCodes(user, user.getUsername());
        model.addAttribute("username", user.getUsername());
        model.addAttribute("mfaEnabled", true);
        model.addAttribute("mfaAvailable", mfaService.isEnabled());
        model.addAttribute("mfaRequired", mfaService.isRequiredFor(user));
        model.addAttribute("recoveryCodes", recoveryCodes);
        model.addAttribute("successMessage", "Kode pemulihan baru dibuat. Kode lama tidak berlaku lagi.");
        return "settings/mfa";
    }

    @PostMapping("/disable")
    public String disable(@RequestParam("code") String code,
                          Model model,
                          RedirectAttributes redirectAttributes) {
        User user = currentUser();
        if (user == null) {
            return "redirect:/login";
        }

        if (mfaService.verify(user, code) != MfaService.VerifyOutcome.OK) {
            redirectAttributes.addFlashAttribute("errorMessage", "Kode OTP tidak valid, MFA tetap aktif.");
            return "redirect:/settings/mfa";
        }

        try {
            mfaService.disable(user, user.getUsername());
            redirectAttributes.addFlashAttribute("successMessage", "MFA dinonaktifkan.");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/settings/mfa";
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        return userRepository.findByUsername(auth.getName()).orElse(null);
    }
}
