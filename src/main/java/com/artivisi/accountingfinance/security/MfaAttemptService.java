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
 *
 * <p>{@link #registerAttempt(String)} increments and decides in one atomic step:
 * checking with {@link #isBlocked(String)} and only then recording the failure
 * lets every concurrent request pass the check before any count is written.
 */
@Service
public class MfaAttemptService {

    private final int maxAttempts;
    private final long windowSeconds;
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    public MfaAttemptService(
            @Value("${app.mfa.max-attempts}") int maxAttempts,
            @Value("${app.mfa.attempt-window-seconds}") long windowSeconds) {
        this.maxAttempts = maxAttempts;
        this.windowSeconds = windowSeconds;
    }

    /**
     * Reserve one attempt for this user.
     *
     * @return true when the attempt is within the limit, false when the account is
     *         over it and the request must be refused without checking a code
     */
    public boolean registerAttempt(String username) {
        Instant now = Instant.now();
        Attempts updated = attempts.compute(username, (key, existing) ->
                existing == null || isExpired(existing, now)
                        ? new Attempts(now, 1)
                        : new Attempts(existing.windowStart, existing.count + 1));
        return updated.count <= maxAttempts;
    }

    public boolean isBlocked(String username) {
        Attempts entry = attempts.get(username);
        if (entry == null) {
            return false;
        }
        if (isExpired(entry, Instant.now())) {
            attempts.remove(username);
            return false;
        }
        return entry.count >= maxAttempts;
    }

    public void succeeded(String username) {
        attempts.remove(username);
    }

    public int remainingAttempts(String username) {
        Attempts entry = attempts.get(username);
        if (entry == null || isExpired(entry, Instant.now())) {
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

    private boolean isExpired(Attempts entry, Instant now) {
        return entry.windowStart.plusSeconds(windowSeconds).isBefore(now);
    }

    private record Attempts(Instant windowStart, int count) {
    }
}
