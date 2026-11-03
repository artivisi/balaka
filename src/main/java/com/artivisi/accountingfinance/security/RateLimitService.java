package com.artivisi.accountingfinance.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory rate limiting service.
 * Limits requests per IP address using a sliding window algorithm.
 *
 * Security features:
 * - Configurable rate limits per endpoint type
 * - IP-based tracking (works behind reverse proxy with X-Forwarded-For)
 * - Automatic cleanup of expired entries
 */
@Service
@Slf4j
public class RateLimitService {

    // Rate limit configuration: max requests per window
    private static final int LOGIN_MAX_REQUESTS = 10;
    private static final int LOGIN_WINDOW_SECONDS = 60;

    private static final int API_MAX_REQUESTS = 100;
    private static final int API_WINDOW_SECONDS = 60;

    private static final int GENERAL_MAX_REQUESTS = 300;
    private static final int GENERAL_WINDOW_SECONDS = 60;

    private final Map<String, RateLimitInfo> loginLimits = new ConcurrentHashMap<>();
    private final Map<String, RateLimitInfo> apiLimits = new ConcurrentHashMap<>();
    private final Map<String, RateLimitInfo> generalLimits = new ConcurrentHashMap<>();

    /**
     * Check if login request is allowed for the given IP.
     *
     * @param ipAddress the client IP address
     * @return true if request is allowed, false if rate limited
     */
    public boolean isLoginAllowed(String ipAddress) {
        return isAllowed(loginLimits, ipAddress, LOGIN_MAX_REQUESTS, LOGIN_WINDOW_SECONDS, "LOGIN");
    }

    /**
     * Check if API request is allowed for the given IP.
     */
    public boolean isApiAllowed(String ipAddress) {
        return isAllowed(apiLimits, ipAddress, API_MAX_REQUESTS, API_WINDOW_SECONDS, "API");
    }

    /**
     * Check if general request is allowed for the given IP.
     */
    public boolean isGeneralAllowed(String ipAddress) {
        return isAllowed(generalLimits, ipAddress, GENERAL_MAX_REQUESTS, GENERAL_WINDOW_SECONDS, "GENERAL");
    }

    /**
     * Get remaining requests for login from the given IP.
     */
    public int getLoginRemaining(String ipAddress) {
        return getRemaining(loginLimits, ipAddress, LOGIN_MAX_REQUESTS, LOGIN_WINDOW_SECONDS);
    }

    /**
     * Get seconds until rate limit resets for login.
     */
    public long getLoginResetSeconds(String ipAddress) {
        return getResetSeconds(loginLimits, ipAddress, LOGIN_WINDOW_SECONDS);
    }

    private boolean isAllowed(Map<String, RateLimitInfo> limits, String ipAddress,
                              int maxRequests, int windowSeconds, String type) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return true;
        }

        String key = normalizeIp(ipAddress);
        Instant now = Instant.now();

        RateLimitInfo info = limits.compute(key, (k, existing) -> {
            if (existing == null || existing.isExpired(now, windowSeconds)) {
                return new RateLimitInfo(now, 1);
            }
            existing.incrementCount();
            return existing;
        });

        if (info.getCount() > maxRequests) {
            log.warn("Rate limit exceeded for {} endpoint from IP: {} ({} requests in {}s)",
                    type, LogSanitizer.ipAddress(ipAddress), info.getCount(), windowSeconds);
            return false;
        }

        return true;
    }

    private int getRemaining(Map<String, RateLimitInfo> limits, String ipAddress,
                             int maxRequests, int windowSeconds) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return maxRequests;
        }

        String key = normalizeIp(ipAddress);
        RateLimitInfo info = limits.get(key);

        if (info == null || info.isExpired(Instant.now(), windowSeconds)) {
            return maxRequests;
        }

        return Math.max(0, maxRequests - info.getCount());
    }

    private long getResetSeconds(Map<String, RateLimitInfo> limits, String ipAddress, int windowSeconds) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return 0;
        }

        String key = normalizeIp(ipAddress);
        RateLimitInfo info = limits.get(key);

        if (info == null) {
            return 0;
        }

        Instant resetTime = info.getWindowStart().plusSeconds(windowSeconds);
        long remaining = resetTime.getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(0, remaining);
    }

    private String normalizeIp(String ipAddress) {
        // Handle X-Forwarded-For format (take first IP)
        if (ipAddress.contains(",")) {
            return ipAddress.split(",")[0].trim().toLowerCase();
        }
        return ipAddress.toLowerCase();
    }

    /**
     * Clean up expired entries (can be called periodically by a scheduled job).
     */
    public void cleanup() {
        Instant now = Instant.now();
        loginLimits.entrySet().removeIf(e -> e.getValue().isExpired(now, LOGIN_WINDOW_SECONDS));
        apiLimits.entrySet().removeIf(e -> e.getValue().isExpired(now, API_WINDOW_SECONDS));
        generalLimits.entrySet().removeIf(e -> e.getValue().isExpired(now, GENERAL_WINDOW_SECONDS));
    }
    
    /**
     * Scheduled cleanup of expired rate limit entries (every 10 minutes).
     */
    @Scheduled(fixedRate = 600000)
    public void scheduledCleanup() {
        cleanup();
    }

    /**
     * Internal class to track rate limit information per IP.
     */
    private static class RateLimitInfo {
        private final Instant windowStart;
        private int count;

        RateLimitInfo(Instant windowStart, int count) {
            this.windowStart = windowStart;
            this.count = count;
        }

        Instant getWindowStart() {
            return windowStart;
        }

        int getCount() {
            return count;
        }

        void incrementCount() {
            this.count++;
        }

        boolean isExpired(Instant now, int windowSeconds) {
            return windowStart.plusSeconds(windowSeconds).isBefore(now);
        }
    }
}
