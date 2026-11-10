package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.security.MfaAttemptService;
import com.artivisi.accountingfinance.security.MfaEnforcementFilter;
import com.artivisi.accountingfinance.security.MfaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The second step of the login: an authenticated session that still has to
 * present a TOTP code or one of its recovery codes.
 */
@Controller
public class MfaVerifyController {

    private final MfaService mfaService;
    private final MfaAttemptService attemptService;
    private final UserRepository userRepository;

    public MfaVerifyController(MfaService mfaService,
                               MfaAttemptService attemptService,
                               UserRepository userRepository) {
        this.mfaService = mfaService;
        this.attemptService = attemptService;
        this.userRepository = userRepository;
    }

    @GetMapping("/mfa-verify")
    public String form(HttpServletRequest request, Model model) {
        User user = currentUser();
        if (user == null || !mfaService.mustVerify(user)) {
            return "redirect:/dashboard";
        }
        if (isVerified(request, user)) {
            return "redirect:/dashboard";
        }
        model.addAttribute("username", user.getUsername());
        return "mfa-verify";
    }

    @PostMapping("/mfa-verify")
    public String verify(@RequestParam(name = "code", required = false) String code,
                         @RequestParam(name = "recoveryCode", required = false) String recoveryCode,
                         HttpServletRequest request,
                         Model model) {
        User user = currentUser();
        if (user == null || !mfaService.mustVerify(user)) {
            return "redirect:/dashboard";
        }

        model.addAttribute("username", user.getUsername());

        if (attemptService.isBlocked(user.getUsername())) {
            model.addAttribute("errorMessage",
                    "Terlalu banyak percobaan. Coba lagi dalam " + attemptService.windowMinutes() + " menit.");
            return "mfa-verify";
        }

        boolean accepted;
        if (recoveryCode != null && !recoveryCode.isBlank()) {
            accepted = mfaService.verifyRecoveryCode(user, recoveryCode);
            if (!accepted) {
                model.addAttribute("errorMessage", "Kode pemulihan tidak valid atau sudah dipakai.");
            }
        } else {
            MfaService.VerifyOutcome outcome = mfaService.verify(user, code);
            accepted = outcome == MfaService.VerifyOutcome.OK;
            model.addAttribute("errorMessage", messageFor(outcome));
        }

        if (!accepted) {
            return "mfa-verify";
        }

        HttpSession session = request.getSession(true);
        session.setAttribute(MfaEnforcementFilter.VERIFIED_USER_SESSION_ATTRIBUTE, user.getUsername());
        return "redirect:/dashboard";
    }

    private String messageFor(MfaService.VerifyOutcome outcome) {
        return switch (outcome) {
            case OK -> null;
            case REPLAYED -> "Kode itu sudah dipakai. Tunggu kode berikutnya di aplikasi authenticator.";
            case LOCKED -> "Terlalu banyak percobaan. Coba lagi nanti.";
            case NOT_ENROLLED -> null;
            default -> "Kode OTP tidak valid. Periksa jam perangkat dan coba lagi.";
        };
    }

    private boolean isVerified(HttpServletRequest request, User user) {
        HttpSession session = request.getSession(false);
        return session != null
                && user.getUsername().equals(session.getAttribute(MfaEnforcementFilter.VERIFIED_USER_SESSION_ATTRIBUTE));
    }

    private User currentUser() {
        Authentication auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        return userRepository.findByUsername(auth.getName()).orElse(null);
    }
}
