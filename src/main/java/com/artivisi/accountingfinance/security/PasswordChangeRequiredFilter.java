package com.artivisi.accountingfinance.security;

import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * While a user's password_change_required flag is set (an administrator created the account or set the
 * password), a web session reaches only the password-change page and logout. Every other page redirects
 * there; a session-authenticated /api request gets 403 {@code password_change_required}. Bearer-token
 * requests of such a user are refused in BearerTokenAuthenticationFilter.
 *
 * <p>Registered as a servlet filter after the Spring Security chain and after the MFA filter, so a
 * pending second-factor verification is answered first.
 */
@Component
@Order(3)
@RequiredArgsConstructor
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    public static final String CHANGE_PATH = "/account/password";

    static final String API_REJECTION =
            "{\"error\":\"password_change_required\",\"message\":\"Change the password in the web application first\"}";

    /**
     * The second-factor pages stay reachable: MfaEnforcementFilter runs first and sends a user who must
     * verify or enrol there, so redirecting them here would loop. Order for a new administrator: second
     * factor, then password change.
     */
    private static final List<String> ALLOWED_PATHS = List.of(CHANGE_PATH, "/logout", "/login", "/error", "/favicon.svg",
            "/mfa-verify", "/settings/mfa", "/settings/mfa/enable", "/settings/mfa/disable", "/settings/mfa/recovery-codes");
    private static final List<String> ALLOWED_PREFIXES = List.of("/css/", "/js/", "/img/", "/themes/", "/webjars/");
    /** Read-only paths the page layout loads; the write side of the same path stays blocked. */
    private static final List<String> ALLOWED_GET_PATHS = List.of("/settings/company/logo");

    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || isBearer(request) || isAllowed(path, request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        User user = (User) request.getAttribute(StaleSessionFilter.REQUEST_USER);
        if (user == null) {
            user = userRepository.findByUsername(authentication.getName()).orElse(null);
        }
        if (user == null || !Boolean.TRUE.equals(user.getPasswordChangeRequired())) {
            filterChain.doFilter(request, response);
            return;
        }

        if (path.startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write(API_REJECTION);
            return;
        }
        response.sendRedirect(request.getContextPath() + CHANGE_PATH);
    }

    private static boolean isAllowed(String path, String method) {
        return ALLOWED_PATHS.contains(path) || ALLOWED_PREFIXES.stream().anyMatch(path::startsWith)
                || ("GET".equals(method) && ALLOWED_GET_PATHS.contains(path));
    }

    private static boolean isBearer(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        return authorization != null && authorization.startsWith("Bearer ");
    }
}
