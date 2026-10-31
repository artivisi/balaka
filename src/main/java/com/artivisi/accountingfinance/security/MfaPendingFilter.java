package com.artivisi.accountingfinance.security;

import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter to redirect users with pending MFA verification to the MFA verify page.
 * Also redirects authenticated users without MFA enabled to the MFA setup page.
 * This prevents bypassing MFA by directly navigating to protected pages.
 */
@Component
@RequiredArgsConstructor
@Order(2)
public class MfaPendingFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                   HttpServletResponse response,
                                   FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        HttpSession session = request.getSession(false);

        // Case 1: MFA verification is pending (enabled but not verified)
        if (session != null && session.getAttribute("MFA_PENDING_USER") != null) {
            if (!path.equals("/login") && !path.equals("/mfa-verify") && !path.equals("/settings/company/logo")
                    && !path.startsWith("/css/") && !path.startsWith("/js/")
                    && !path.startsWith("/img/") && !path.startsWith("/themes/") && !path.startsWith("/favicon") && !path.startsWith("/webjars/")) {
                response.sendRedirect("/login");
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }

        // Case 2: Authenticated user without MFA enabled → force setup
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && !(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
            // Skip for allowed paths
            if (path.equals("/login") || path.equals("/mfa-verify") || path.equals("/settings/mfa") || path.equals("/settings/mfa/enable")
                    || path.equals("/settings/company/logo") || path.startsWith("/css/")
                    || path.startsWith("/js/") || path.startsWith("/img/")
                    || path.startsWith("/themes/") || path.startsWith("/favicon") || path.startsWith("/webjars/") || path.equals("/logout")
                    || path.startsWith("/api/")) {
                filterChain.doFilter(request, response);
                return;
            }

            User user = userRepository.findByUsername(auth.getName()).orElse(null);
            if (user != null && !user.getMfaEnabled()) {
                response.sendRedirect("/login");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
