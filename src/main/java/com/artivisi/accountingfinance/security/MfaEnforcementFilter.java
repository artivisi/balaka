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
 * established, whichever mechanism produced it, so a session restored from the
 * server-side session is challenged as well: an MFA decision taken only inside the
 * login success handler is skipped when an existing session authenticates the request.
 *
 * <p>Paths that must stay reachable while a verification is pending are
 * allowlisted and tested before any database read; {@code /api/**} is excluded
 * because those endpoints authenticate with bearer tokens, which this factor does
 * not apply to. A path that is only needed to render the waiting pages is
 * allowlisted for {@code GET} alone, so its write handler stays behind the factor.
 */
@Component
@Order(2)
@RequiredArgsConstructor
public class MfaEnforcementFilter extends OncePerRequestFilter {

    /** The session attribute set once /mfa-verify accepted a code. */
    public static final String VERIFIED_USER_SESSION_ATTRIBUTE = "MFA_VERIFIED_USER";

    static final String HX_REQUEST_HEADER = "HX-Request";
    static final String HX_REDIRECT_HEADER = "HX-Redirect";

    private static final List<String> ALWAYS_ALLOWED_PREFIXES = List.of(
            "/css/", "/js/", "/img/", "/themes/", "/webjars/", "/favicon", "/api/", "/actuator/");

    private static final List<String> ALWAYS_ALLOWED_PATHS = List.of(
            "/login", "/logout", "/error", "/mfa-verify", "/settings/mfa", "/settings/mfa/enable",
            "/settings/mfa/disable", "/settings/mfa/recovery-codes");

    /**
     * Only the read side of these paths is allowed while a verification is pending: the
     * waiting pages render the company logo, but an upload to the same path changes data
     * and has to pass the second factor.
     */
    private static final List<String> ALLOWED_GET_PATHS = List.of("/settings/company/logo");

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
        if (isAllowed(path, request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

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

        if (mfaService.mustVerify(user) && !isVerifiedSession(request, user)) {
            redirect(request, response, "/mfa-verify");
            return;
        }

        if (mfaService.mustEnroll(user)) {
            redirect(request, response, "/settings/mfa?required=true");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * An HTMX request follows a 302 by swapping the redirect target's body into the
     * current fragment; HX-Redirect makes the browser navigate instead.
     */
    private void redirect(HttpServletRequest request, HttpServletResponse response, String target)
            throws IOException {
        String location = request.getContextPath() + target;
        if (request.getHeader(HX_REQUEST_HEADER) != null) {
            response.setHeader(HX_REDIRECT_HEADER, location);
            response.setStatus(HttpServletResponse.SC_OK);
        } else {
            response.sendRedirect(location);
        }
    }

    private boolean isVerifiedSession(HttpServletRequest request, User user) {
        HttpSession session = request.getSession(false);
        return session != null && user.getUsername().equals(session.getAttribute(VERIFIED_USER_SESSION_ATTRIBUTE));
    }

    private boolean isAllowed(String path, String method) {
        return ALWAYS_ALLOWED_PATHS.contains(path)
                || ALWAYS_ALLOWED_PREFIXES.stream().anyMatch(path::startsWith)
                || ("GET".equals(method) && ALLOWED_GET_PATHS.contains(path));
    }
}
