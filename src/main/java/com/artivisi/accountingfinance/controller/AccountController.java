package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.security.PasswordValidator;
import com.artivisi.accountingfinance.service.PasswordChangeException;
import com.artivisi.accountingfinance.service.PasswordChangeService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;

/**
 * The authenticated user's own password, reachable by every role (issue #61). While the account is
 * flagged for a forced change, PasswordChangeRequiredFilter sends every other page here.
 */
@Controller
@RequestMapping("/account/password")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AccountController {

    private static final String VIEW = "account/password";

    private final UserRepository userRepository;
    private final PasswordChangeService passwordChangeService;

    @GetMapping
    public String form(Authentication authentication, Model model) {
        model.addAttribute("forced", isForced(authentication));
        model.addAttribute("minLength", PasswordValidator.MIN_LENGTH);
        return VIEW;
    }

    @PostMapping
    public String change(@RequestParam String currentPassword,
                         @RequestParam String newPassword,
                         @RequestParam String confirmPassword,
                         Authentication authentication,
                         HttpSession session,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        boolean forced = isForced(authentication);
        try {
            LocalDateTime changedAt = passwordChangeService.changeOwnPassword(
                    authentication.getName(), currentPassword, newPassword, confirmPassword);
            // This session stays valid; every session authenticated earlier is ended by StaleSessionFilter.
            session.setAttribute(PasswordChangeService.SESSION_AUTHENTICATED_AT, changedAt);
        } catch (PasswordChangeException e) {
            model.addAttribute("forced", forced);
            model.addAttribute("minLength", PasswordValidator.MIN_LENGTH);
            model.addAttribute("errors", e.getErrors());
            return VIEW;
        }
        redirectAttributes.addFlashAttribute("successMessage",
                "Password berhasil diubah. Sesi lain dan token perangkat akun ini telah diakhiri.");
        return forced ? "redirect:/dashboard" : "redirect:/account/password";
    }

    private boolean isForced(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .map(User::getPasswordChangeRequired)
                .map(Boolean.TRUE::equals)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + authentication.getName()));
    }
}
