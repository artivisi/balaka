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
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Enforces the second factor for an authenticated session.
 *
 * <p>It reads the {@link Authentication} that the Spring Security chain already
 * established, whichever mechanism produced it, so a session restored by
 * remember-me is challenged as well: an MFA decision taken only inside the login
 * success handler is skipped when the cookie authenticates the request.
 *
 * <p>Paths that must stay reachable while a verification is pending are
 * allowlisted; {@code /api/**} is excluded because those endpoints authenticate
 * with bearer tokens, which this factor does not apply to.
 */
@Component
@Order(2)
@RequiredArgsConstructor
public class MfaEnforcementFilter extends OncePerRequestFilter {

    /** The session attribute set once /mfa-verify accepted a code. */
    public static final String VERIFIED_USER_SESSION_ATTRIBUTE = "MFA_VERIFIED_USER";

    private static final List<String> ALWAYS_ALLOWED_PREFIXES = List.of(
            "/css/", "/js/", "/img/", "/themes/", "/webjars/", "/favicon", "/api/", "/actuator/");

    private static final List<String> ALWAYS_ALLOWED_PATHS = List.of(
            "/login", "/logout", "/error", "/mfa-verify", "/settings/mfa", "/settings/mfa/enable",
            "/settings/mfa/disable", "/settings/mfa/recovery-codes", "/settings/company/logo");

    private final UserRepository userRepository;
    private final MfaService mfaService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!mfaService.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI().substring(request.getContextPath().length());
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            filterChain.doFilter(request, response);
            return;
        }

        User user = userRepository.findByUsername(auth.getName()).orElse(null);
        if (user == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (isAllowed(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (mfaService.mustVerify(user) && !isVerifiedSession(request, user)) {
            response.sendRedirect(request.getContextPath() + "/mfa-verify");
            return;
        }

        if (mfaService.mustEnroll(user)) {
            response.sendRedirect(request.getContextPath() + "/settings/mfa?required=true");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isVerifiedSession(HttpServletRequest request, User user) {
        HttpSession session = request.getSession(false);
        return session != null && user.getUsername().equals(session.getAttribute(VERIFIED_USER_SESSION_ATTRIBUTE));
    }

    private boolean isAllowed(String path) {
        return ALWAYS_ALLOWED_PATHS.contains(path)
                || ALWAYS_ALLOWED_PREFIXES.stream().anyMatch(path::startsWith);
    }
}
