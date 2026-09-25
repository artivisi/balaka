package com.artivisi.accountingfinance.security;

import com.artivisi.accountingfinance.entity.DeviceToken;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.repository.DeviceTokenRepository;
import com.artivisi.accountingfinance.service.DeviceAuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Filter to authenticate API requests using Bearer tokens.
 * Validates device tokens issued via OAuth 2.0 Device Flow and client_credentials.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

    private final DeviceAuthService deviceAuthService;
    private final DeviceTokenRepository deviceTokenRepository;

    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // Only process API requests (excluding public endpoints)
        String requestUri = request.getRequestURI();
        if (!requestUri.startsWith("/api/") ||
            requestUri.startsWith("/api/device/") ||
            requestUri.startsWith("/api/oauth/") ||
            requestUri.startsWith("/api/telegram/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // A Bearer header always authenticates by token, never by a session cookie
        // sent alongside it. CSRF is waived only for such requests (SecurityConfig),
        // so a token request must never run with session authority.
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        Optional<DeviceToken> deviceToken = deviceAuthService.validateToken(token);
        if (deviceToken.isEmpty()) {
            reject(response, "Invalid or expired token");
            return;
        }

        DeviceToken dt = deviceToken.get();
        User user = dt.getUser();
        if (!Boolean.TRUE.equals(user.getActive())) {
            log.warn("Rejected API token {} of inactive user {}", dt.getId(), LogSanitizer.username(user.getUsername()));
            reject(response, "User is inactive");
            return;
        }

        dt.updateLastUsed(getClientIpAddress(request));
        deviceTokenRepository.save(dt);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user.getUsername(), null, buildAuthorities(dt, user));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        log.debug("Authenticated API request from user: {} using device token", user.getUsername());

        filterChain.doFilter(request, response);
    }

    /**
     * Effective authority = token scopes intersected with the user's current role
     * permissions, evaluated per request so role changes apply to issued tokens.
     * The user's permissions are granted as authorities too, so endpoints can
     * require the specific permission of an operation within a broad scope.
     */
    private List<SimpleGrantedAuthority> buildAuthorities(DeviceToken dt, User user) {
        Set<String> permissions = Permission.getPermissionsForRoles(user.getRoles());

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        permissions.forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));

        if (dt.getScopes() == null) {
            log.warn("Token {} has no scopes", dt.getId());
            return authorities;
        }
        for (String raw : dt.getScopes().split(",")) {
            String scope = raw.trim();
            Optional<ApiScope> apiScope = ApiScope.fromScope(scope);
            if (apiScope.isEmpty()) {
                log.warn("Token {} carries unknown scope '{}'", dt.getId(), LogSanitizer.sanitize(scope));
            } else if (apiScope.get().isGrantedTo(permissions)) {
                authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope));
            }
        }
        return authorities;
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"unauthorized\",\"message\":\"" + message + "\"}");
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
