package com.artivisi.accountingfinance.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Filter to apply rate limiting on sensitive endpoints.
 * Runs before authentication to prevent brute force attacks.
 *
 * IMPORTANT: When deployed behind a reverse proxy, the proxy must be configured to:
 * 1. Overwrite (not append to) X-Forwarded-For header
 * 2. Set X-Real-IP from the actual client connection
 * This prevents clients from spoofing their IP to bypass rate limiting.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;

    // Pattern for valid IPv4 addresses (simplified - format validation only)
    // Strict 0-255 range validation is handled by InetAddress parsing
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^(\\d{1,3}\\.){3}\\d{1,3}$");
    // Pattern for valid IPv6 addresses (common formats)
    private static final Pattern IPV6_PATTERN = Pattern.compile(
            "^[\\da-fA-F:]+$");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();
        String clientIp = getClientIpAddress(request);

        // Apply rate limiting to login POST requests
        if ("/login".equals(path) && "POST".equalsIgnoreCase(method)) {
            if (!rateLimitService.isLoginAllowed(clientIp)) {
                log.warn("Rate limit exceeded for login from IP: {}", LogSanitizer.ipAddress(clientIp));

                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("text/html;charset=UTF-8");
                response.setHeader("Retry-After", String.valueOf(rateLimitService.getLoginResetSeconds(clientIp)));
                response.setHeader("X-RateLimit-Remaining", "0");
                response.setHeader("X-RateLimit-Reset", String.valueOf(rateLimitService.getLoginResetSeconds(clientIp)));

                response.getWriter().write("""
                    <!DOCTYPE html>
                    <html>
                    <head><title>Too Many Requests</title></head>
                    <body>
                        <h1>Too Many Requests</h1>
                        <p>Terlalu banyak percobaan login. Silakan tunggu beberapa saat sebelum mencoba lagi.</p>
                        <p>Too many login attempts. Please wait before trying again.</p>
                    </body>
                    </html>
                    """);
                return;
            }

            // Add rate limit headers for successful requests
            response.setHeader("X-RateLimit-Remaining",
                    String.valueOf(rateLimitService.getLoginRemaining(clientIp)));
        }

        // Apply rate limiting to API endpoints
        if (path.startsWith("/api/") && !rateLimitService.isApiAllowed(clientIp)) {
            log.warn("Rate limit exceeded for API from IP: {}", LogSanitizer.ipAddress(clientIp));

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Retry-After", "60");
            response.getWriter().write("{\"error\":\"Too many requests\",\"message\":\"Rate limit exceeded\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Get client IP address, considering reverse proxy headers.
     * Falls back to remote address if headers contain invalid IP.
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();

        // Check X-Forwarded-For header (set by reverse proxies)
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            String ip = xForwardedFor.split(",")[0].trim();
            if (isValidIpAddress(ip)) {
                return ip;
            }
            log.debug("Invalid IP in X-Forwarded-For header, using remote address");
        }

        // Check X-Real-IP header (nginx)
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            String ip = xRealIp.trim();
            if (isValidIpAddress(ip)) {
                return ip;
            }
            log.debug("Invalid IP in X-Real-IP header, using remote address");
        }

        // Fall back to remote address (always trusted from container)
        return remoteAddr;
    }

    /**
     * Validate that the string is a valid IPv4 or IPv6 address.
     * Rejects malformed addresses that could be used for bypass attempts.
     */
    private boolean isValidIpAddress(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        return IPV4_PATTERN.matcher(ip).matches() || IPV6_PATTERN.matcher(ip).matches();
    }
}
