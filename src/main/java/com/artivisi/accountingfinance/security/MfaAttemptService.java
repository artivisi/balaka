package com.artivisi.accountingfinance.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Counts failed TOTP submissions per username. Without a limit the 6-digit code
 * is a million-value secret that a script can grind through inside the 30 second
 * window the code stays valid.
 */
@Service
public class MfaAttemptService {

    private final int maxAttempts;
    private final long windowSeconds;
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    public MfaAttemptService(
            @Value("${app.mfa.max-attempts:5}") int maxAttempts,
            @Value("${app.mfa.attempt-window-seconds:900}") long windowSeconds) {
        this.maxAttempts = maxAttempts;
        this.windowSeconds = windowSeconds;
    }

    public boolean isBlocked(String username) {
        Attempts entry = attempts.get(username);
        if (entry == null) {
            return false;
        }
        if (entry.windowStart.plusSeconds(windowSeconds).isBefore(Instant.now())) {
            attempts.remove(username);
            return false;
        }
        return entry.count >= maxAttempts;
    }

    public void failed(String username) {
        Instant now = Instant.now();
        attempts.compute(username, (key, entry) -> {
            if (entry == null || entry.windowStart.plusSeconds(windowSeconds).isBefore(now)) {
                return new Attempts(now, 1);
            }
            return new Attempts(entry.windowStart, entry.count + 1);
        });
    }

    public void succeeded(String username) {
        attempts.remove(username);
    }

    public int remainingAttempts(String username) {
        Attempts entry = attempts.get(username);
        if (entry == null || entry.windowStart.plusSeconds(windowSeconds).isBefore(Instant.now())) {
            return maxAttempts;
        }
        return Math.max(0, maxAttempts - entry.count);
    }

    public long windowMinutes() {
        return Math.max(1, windowSeconds / 60);
    }

    public void clear() {
        attempts.clear();
    }

    private record Attempts(Instant windowStart, int count) {
    }
}
