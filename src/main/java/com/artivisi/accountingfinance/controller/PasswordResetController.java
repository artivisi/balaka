package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.security.PasswordValidator;
import com.artivisi.accountingfinance.service.PasswordChangeException;
import com.artivisi.accountingfinance.service.PasswordResetRateLimitedException;
import com.artivisi.accountingfinance.service.PasswordResetService;
import com.artivisi.accountingfinance.service.PasswordResetTokenException;
import com.artivisi.accountingfinance.service.PasswordResetUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * "Forgot password" from the login page and the reset link from the email (issue #61). Both pages are
 * public. When reset is not configured they answer 503 with that message rather than pretending.
 */
@Controller
@RequiredArgsConstructor
@PreAuthorize("permitAll()")
public class PasswordResetController {

    private static final String VIEW_REQUEST = "auth/forgot-password";
    private static final String VIEW_RESET = "auth/reset-password";
    private static final String ATTR_ERROR = "errorMessage";

    private final PasswordResetService passwordResetService;

    @GetMapping("/forgot-password")
    public String requestForm(HttpServletResponse response, Model model) {
        if (!passwordResetService.isEnabled()) {
            return unavailable(response, model, VIEW_REQUEST);
        }
        return VIEW_REQUEST;
    }

    @PostMapping("/forgot-password")
    public String request(@RequestParam String email, HttpServletRequest request,
                          HttpServletResponse response, Model model) {
        try {
            passwordResetService.requestReset(email, request.getRemoteAddr());
        } catch (PasswordResetUnavailableException e) {
            return unavailable(response, model, VIEW_REQUEST);
        } catch (PasswordResetRateLimitedException e) {
            response.setStatus(429);
            model.addAttribute(ATTR_ERROR, e.getMessage());
            return VIEW_REQUEST;
        }
        return "redirect:/forgot-password?sent";
    }

    @GetMapping("/reset-password")
    public String resetForm(@RequestParam(required = false) String token,
                            HttpServletResponse response, Model model) {
        if (!passwordResetService.isEnabled()) {
            return unavailable(response, model, VIEW_RESET);
        }
        PasswordResetService.TokenState state = passwordResetService.check(token);
        if (state != PasswordResetService.TokenState.VALID) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("linkError", PasswordResetTokenException.messageFor(state));
            return VIEW_RESET;
        }
        model.addAttribute("token", token);
        model.addAttribute("minLength", PasswordValidator.MIN_LENGTH);
        return VIEW_RESET;
    }

    @PostMapping("/reset-password")
    public String reset(@RequestParam String token,
                        @RequestParam String newPassword,
                        @RequestParam String confirmPassword,
                        HttpServletResponse response, Model model) {
        try {
            passwordResetService.completeReset(token, newPassword, confirmPassword);
        } catch (PasswordResetUnavailableException e) {
            return unavailable(response, model, VIEW_RESET);
        } catch (PasswordResetTokenException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("linkError", e.getMessage());
            return VIEW_RESET;
        } catch (PasswordChangeException e) {
            model.addAttribute("token", token);
            model.addAttribute("minLength", PasswordValidator.MIN_LENGTH);
            model.addAttribute("errors", e.getErrors());
            return VIEW_RESET;
        }
        return "redirect:/login?reset";
    }

    private static String unavailable(HttpServletResponse response, Model model, String view) {
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        model.addAttribute("unavailable", new PasswordResetUnavailableException().getMessage());
        return view;
    }
}
