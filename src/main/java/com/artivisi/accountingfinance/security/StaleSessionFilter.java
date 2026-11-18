package com.artivisi.accountingfinance.security;

import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.service.PasswordChangeService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Invalidates a web session authenticated before the user's password last changed, so a change or
 * reset ends every other session of that user. Runs inside the Spring Security chain (SecurityConfig),
 * after the session's authentication is restored and before authorization.
 *
 * <p>The session records when it was authenticated in
 * {@link PasswordChangeService#SESSION_AUTHENTICATED_AT}, set by the login success handler. A session
 * without the attribute (authenticated before this filter was deployed) gets the current time on its
 * first request.
 *
 * <p>Bearer-token API requests are not session based and are skipped; their tokens are revoked by the
 * password change itself.
 */
@Slf4j
public class StaleSessionFilter extends OncePerRequestFilter {

    /** Request attribute carrying the authenticated {@link User}, read by PasswordChangeRequiredFilter. */
    public static final String REQUEST_USER = StaleSessionFilter.class.getName() + ".USER";

    private final UserRepository userRepository;

    public StaleSessionFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (session == null || authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken || isBearer(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<User> user = userRepository.findByUsername(authentication.getName());
        if (user.isEmpty()) {
            log.warn("Session of a user that no longer exists: {}", LogSanitizer.username(authentication.getName()));
            end(session, request, response);
            return;
        }

        LocalDateTime authenticatedAt = (LocalDateTime) session.getAttribute(PasswordChangeService.SESSION_AUTHENTICATED_AT);
        LocalDateTime changedAt = user.get().getCredentialsChangedAt();
        if (authenticatedAt == null) {
            session.setAttribute(PasswordChangeService.SESSION_AUTHENTICATED_AT,
                    LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
        } else if (changedAt != null && changedAt.isAfter(authenticatedAt)) {
            log.info("Ending session of {}: authenticated {} before the password changed {}",
                    LogSanitizer.username(authentication.getName()), authenticatedAt, changedAt);
            end(session, request, response);
            return;
        }

        request.setAttribute(REQUEST_USER, user.get());
        filterChain.doFilter(request, response);
    }

    private static void end(HttpSession session, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        session.invalidate();
        SecurityContextHolder.clearContext();
        if (request.getRequestURI().startsWith(request.getContextPath() + "/api/")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"session_expired\",\"message\":\"Password changed; sign in again\"}");
        } else {
            response.sendRedirect(request.getContextPath() + "/login?expired");
        }
    }

    private static boolean isBearer(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        return authorization != null && authorization.startsWith("Bearer ");
    }
}
