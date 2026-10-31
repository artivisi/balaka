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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class MfaVerifyController {

    private final TotpService totpService;
    private final UserRepository userRepository;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public MfaVerifyController(TotpService totpService, UserRepository userRepository) {
        this.totpService = totpService;
        this.userRepository = userRepository;
    }

    @GetMapping("/mfa-verify")
    public String mfaVerifyPage(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("MFA_PENDING_USER") == null) {
            return "redirect:/login";
        }
        return "mfa-verify";
    }

    @PostMapping("/mfa-verify")
    public String mfaVerifySubmit(@RequestParam("code") String code,
                                 HttpServletRequest request,
                                 HttpServletResponse response,
                                 RedirectAttributes redirectAttributes) throws Exception {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("MFA_PENDING_USER") == null) {
            return "redirect:/login";
        }

        String username = (String) session.getAttribute("MFA_PENDING_USER");
        Authentication pendingAuth = (Authentication) session.getAttribute("MFA_PENDING_AUTH");

        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null || !user.getMfaEnabled()) {
            session.removeAttribute("MFA_PENDING_USER");
            session.removeAttribute("MFA_PENDING_AUTH");
            return "redirect:/dashboard";
        }

        if (totpService.verifyCode(user.getMfaSecret(), code.trim())) {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(pendingAuth);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);

            session.removeAttribute("MFA_PENDING_USER");
            session.removeAttribute("MFA_PENDING_AUTH");

            if (user.getPasswordLastChanged() != null) {
                java.time.LocalDateTime passwordAge = user.getPasswordLastChanged().plusDays(90);
                if (java.time.LocalDateTime.now().isAfter(passwordAge)) {
                    return "redirect:/settings/change-password?expired=true";
                }
            }

            return "redirect:/dashboard";
        }

        request.getSession().setAttribute("MFA_ERROR_MESSAGE", "Kode OTP tidak valid. Silakan coba lagi.");
        return "redirect:/login";
    }
}
