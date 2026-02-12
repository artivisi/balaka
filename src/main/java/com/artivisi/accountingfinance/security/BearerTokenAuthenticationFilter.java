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
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Filter to authenticate API requests using Bearer tokens.
 * Validates device tokens issued via OAuth 2.0 Device Flow.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

    private final DeviceAuthService deviceAuthService;
    private final DeviceTokenRepository deviceTokenRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // Only process API requests
        String requestUri = request.getRequestURI();
        if (!requestUri.startsWith("/api/drafts")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Skip if already authenticated
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extract Bearer token from Authorization header
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            Optional<DeviceToken> deviceToken = deviceAuthService.validateToken(token);

            if (deviceToken.isPresent()) {
                DeviceToken dt = deviceToken.get();
                User user = dt.getUser();

                // Update last used
                dt.updateLastUsed(getClientIpAddress(request));
                deviceTokenRepository.save(dt);

                // Create authentication
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                user.getUsername(),
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        );

                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("Authenticated API request from user: {} using device token", user.getUsername());
            }

        } catch (Exception e) {
            log.warn("Failed to authenticate Bearer token: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
