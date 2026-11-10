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
    private static final String LOGIN_REDIRECT = "redirect:/login";
    private static final String VIEW_MFA = "settings/mfa";
    private static final String REDIRECT_MFA = "redirect:/settings/mfa";
    private static final String ATTR_USERNAME = "username";
    private static final String ATTR_MFA_ENABLED = "mfaEnabled";
    private static final String ATTR_MFA_AVAILABLE = "mfaAvailable";
    private static final String ATTR_MFA_REQUIRED = "mfaRequired";
    private static final String ATTR_ERROR = "errorMessage";
    private static final String ATTR_SUCCESS = "successMessage";

    private final MfaService mfaService;
    private final TotpService totpService;
    private final UserRepository userRepository;

    public MfaController(MfaService mfaService, TotpService totpService, UserRepository userRepository) {
        this.mfaService = mfaService;
        this.totpService = totpService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String page(@RequestParam(name = "required", required = false, defaultValue = "false") boolean required,
                       HttpServletRequest request,
                       Model model) {
        User user = currentUser();
        if (user == null) {
            return LOGIN_REDIRECT;
        }

        model.addAttribute(ATTR_USERNAME, user.getUsername());
        model.addAttribute(ATTR_MFA_ENABLED, Boolean.TRUE.equals(user.getMfaEnabled()));
        model.addAttribute(ATTR_MFA_AVAILABLE, mfaService.isEnabled());
        model.addAttribute(ATTR_MFA_REQUIRED, mfaService.isRequiredFor(user));
        model.addAttribute("enrollmentRequired", required && !Boolean.TRUE.equals(user.getMfaEnabled()));

        if (!mfaService.isEnabled() || Boolean.TRUE.equals(user.getMfaEnabled())) {
            if (Boolean.TRUE.equals(user.getMfaEnabled())) {
                model.addAttribute("remainingRecoveryCodes", mfaService.remainingRecoveryCodes(user));
            }
            return VIEW_MFA;
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
        return VIEW_MFA;
    }

    @PostMapping("/enable")
    public String enable(@RequestParam("code") String code,
                         HttpServletRequest request,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        User user = currentUser();
        if (user == null) {
            return LOGIN_REDIRECT;
        }

        HttpSession session = request.getSession(false);
        String pendingSecret = session == null ? null : (String) session.getAttribute(PENDING_SECRET);
        if (pendingSecret == null) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR,
                    "Sesi setup sudah berakhir. Muat ulang halaman untuk mendapatkan kunci baru.");
            return REDIRECT_MFA;
        }

        model.addAttribute(ATTR_USERNAME, user.getUsername());
        model.addAttribute(ATTR_MFA_AVAILABLE, mfaService.isEnabled());
        model.addAttribute(ATTR_MFA_REQUIRED, mfaService.isRequiredFor(user));

        try {
            List<String> recoveryCodes = mfaService.completeEnrollment(user, pendingSecret, code, user.getUsername());
            session.removeAttribute(PENDING_SECRET);
            if (request.getSession(false) != null) {
                request.changeSessionId();
            }
            request.getSession(true).setAttribute(
                    MfaEnforcementFilter.VERIFIED_USER_SESSION_ATTRIBUTE, user.getUsername());

            model.addAttribute(ATTR_MFA_ENABLED, true);
            model.addAttribute("recoveryCodes", recoveryCodes);
            model.addAttribute(ATTR_SUCCESS, "MFA berhasil diaktifkan. Simpan kode pemulihan di tempat aman.");
            return VIEW_MFA;
        } catch (IllegalArgumentException e) {
            model.addAttribute(ATTR_MFA_ENABLED, false);
            model.addAttribute("secret", pendingSecret);
            model.addAttribute("otpAuthUri", totpService.getOtpAuthUri(pendingSecret, user.getUsername(), "Balaka"));
            model.addAttribute("digits", totpService.getDigits());
            model.addAttribute("period", totpService.getStepSeconds());
            model.addAttribute(ATTR_ERROR, e.getMessage());
            return VIEW_MFA;
        }
    }

    @PostMapping("/recovery-codes")
    public String regenerateRecoveryCodes(@RequestParam("code") String code,
                                          Model model) {
        User user = currentUser();
        if (user == null) {
            return LOGIN_REDIRECT;
        }
        if (mfaService.verify(user, code) != MfaService.VerifyOutcome.OK) {
            model.addAttribute(ATTR_USERNAME, user.getUsername());
            model.addAttribute(ATTR_MFA_ENABLED, true);
            model.addAttribute(ATTR_MFA_AVAILABLE, mfaService.isEnabled());
            model.addAttribute(ATTR_MFA_REQUIRED, mfaService.isRequiredFor(user));
            model.addAttribute("remainingRecoveryCodes", mfaService.remainingRecoveryCodes(user));
            model.addAttribute(ATTR_ERROR, "Kode OTP tidak valid, kode pemulihan tidak diubah.");
            return VIEW_MFA;
        }

        List<String> recoveryCodes = mfaService.regenerateRecoveryCodes(user, user.getUsername());
        model.addAttribute(ATTR_USERNAME, user.getUsername());
        model.addAttribute(ATTR_MFA_ENABLED, true);
        model.addAttribute(ATTR_MFA_AVAILABLE, mfaService.isEnabled());
        model.addAttribute(ATTR_MFA_REQUIRED, mfaService.isRequiredFor(user));
        model.addAttribute("recoveryCodes", recoveryCodes);
        model.addAttribute(ATTR_SUCCESS, "Kode pemulihan baru dibuat. Kode lama tidak berlaku lagi.");
        return VIEW_MFA;
    }

    @PostMapping("/disable")
    public String disable(@RequestParam("code") String code,
                          Model model,
                          RedirectAttributes redirectAttributes) {
        User user = currentUser();
        if (user == null) {
            return LOGIN_REDIRECT;
        }

        if (mfaService.verify(user, code) != MfaService.VerifyOutcome.OK) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR, "Kode OTP tidak valid, MFA tetap aktif.");
            return REDIRECT_MFA;
        }

        try {
            mfaService.disable(user, user.getUsername());
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS, "MFA dinonaktifkan.");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR, e.getMessage());
        }
        return REDIRECT_MFA;
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        return userRepository.findByUsername(auth.getName()).orElse(null);
    }
}
