package com.artivisi.accountingfinance.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to track failed login attempts and implement account lockout.
 *
 * Security features:
 * - Locks account after MAX_ATTEMPTS failed login attempts
 * - Lockout duration: LOCK_TIME_MINUTES
 * - Tracks by username (not IP) to prevent account enumeration attacks via timing
 */
@Service
@Slf4j
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_TIME_MINUTES = 30;

    private final Map<String, FailedLoginInfo> attemptsCache = new ConcurrentHashMap<>();

    /**
     * Record a failed login attempt for a username.
     */
    public void loginFailed(String username) {
        if (username == null || username.isBlank()) {
            return;
        }

        String key = username.toLowerCase();
        FailedLoginInfo info = attemptsCache.computeIfAbsent(key, k -> new FailedLoginInfo());

        // If lockout has expired, reset counter
        if (info.lockedUntil != null && info.lockedUntil.isBefore(Instant.now())) {
            info.attempts = 0;
            info.lockedUntil = null;
        }

        info.attempts++;

        if (info.attempts >= MAX_ATTEMPTS) {
            info.lockedUntil = Instant.now().plus(Duration.ofMinutes(LOCK_TIME_MINUTES));
            log.warn("Account locked due to {} failed login attempts: {}",
                    info.attempts, LogSanitizer.username(username));
        } else {
            log.info("Failed login attempt {} of {} for user: {}",
                    info.attempts, MAX_ATTEMPTS, LogSanitizer.username(username));
        }
    }

    /**
     * Record a successful login, resetting the attempt counter.
     */
    public void loginSucceeded(String username) {
        if (username == null || username.isBlank()) {
            return;
        }

        String key = username.toLowerCase();
        attemptsCache.remove(key);
    }

    /**
     * Check if a user account is currently locked.
     */
    public boolean isBlocked(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }

        String key = username.toLowerCase();
        FailedLoginInfo info = attemptsCache.get(key);

        if (info == null) {
            return false;
        }

        if (info.lockedUntil == null) {
            return false;
        }

        // Check if lockout has expired
        if (info.lockedUntil.isBefore(Instant.now())) {
            // Reset on expiry
            info.attempts = 0;
            info.lockedUntil = null;
            return false;
        }

        return true;
    }

    /**
     * Get remaining lockout time in minutes.
     */
    public long getRemainingLockoutMinutes(String username) {
        if (username == null || username.isBlank()) {
            return 0;
        }

        String key = username.toLowerCase();
        FailedLoginInfo info = attemptsCache.get(key);

        if (info == null || info.lockedUntil == null) {
            return 0;
        }

        Instant now = Instant.now();
        if (info.lockedUntil.isBefore(now)) {
            return 0;
        }

        return Duration.between(now, info.lockedUntil).toMinutes() + 1;
    }

    /**
     * Get the number of failed attempts for a user.
     */
    public int getFailedAttempts(String username) {
        if (username == null || username.isBlank()) {
            return 0;
        }

        String key = username.toLowerCase();
        FailedLoginInfo info = attemptsCache.get(key);

        return info != null ? info.attempts : 0;
    }

    /**
     * Reset all login attempts. Used for testing.
     */
    public void resetAllAttempts() {
        attemptsCache.clear();
    }

    /**
     * Internal class to track failed login information.
     */
    private static class FailedLoginInfo {
        int attempts = 0;
        Instant lockedUntil;
    }
}
