package com.artivisi.accountingfinance.security;

import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
public class AuthSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;

    public AuthSuccessHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                       HttpServletResponse response,
                                       Authentication authentication) throws IOException {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username).orElse(null);

        if (user != null && user.getMfaEnabled() && user.getMfaSecret() != null) {
            // MFA is enabled - don't fully authenticate yet
            // Store MFA pending state in session
            HttpSession session = request.getSession();
            session.setAttribute("MFA_PENDING_USER", username);
            session.setAttribute("MFA_PENDING_AUTH", authentication);
            
            // Clear the current authentication (force MFA verification)
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
            
            response.sendRedirect("/login");
            return;
        }

        // MFA not enabled - force MFA setup (show on login page)
        if (user != null && !user.getMfaEnabled()) {
            response.sendRedirect("/login");
            return;
        }

        // Check password age (90 days)
        if (user != null && user.getPasswordLastChanged() != null) {
            LocalDateTime passwordAge = user.getPasswordLastChanged().plusDays(90);
            if (LocalDateTime.now().isAfter(passwordAge)) {
                response.sendRedirect("/settings/change-password?expired=true");
                return;
            }
        }

        response.sendRedirect("/dashboard");
    }
}
