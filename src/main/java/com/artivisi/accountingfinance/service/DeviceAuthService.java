package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.DeviceCode;
import com.artivisi.accountingfinance.entity.DeviceToken;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.repository.DeviceCodeRepository;
import com.artivisi.accountingfinance.repository.DeviceTokenRepository;
import com.artivisi.accountingfinance.security.LogSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for OAuth 2.0 Device Authorization Flow.
 * Implements RFC 8628: https://tools.ietf.org/html/rfc8628
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class DeviceAuthService {

    private static final String USER_CODE_CHARS = "BCDFGHJKLMNPQRSTVWXYZ"; // No vowels to avoid words
    private static final int USER_CODE_LENGTH = 8; // Format: XXXX-XXXX
    private static final int DEVICE_CODE_LENGTH = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final DeviceCodeRepository deviceCodeRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${device.auth.code-expiry-minutes:15}")
    private int codeExpiryMinutes;

    @Value("${device.auth.token-expiry-days:30}")
    private int tokenExpiryDays;

    /**
     * Step 1: Generate device code and user code.
     */
    public DeviceCode createDeviceCode(String clientId, String baseUrl) {
        String deviceCode = generateSecureToken(DEVICE_CODE_LENGTH);
        String userCode = generateUserCode();
        String verificationUri = baseUrl + "/device";

        DeviceCode code = new DeviceCode();
        code.setDeviceCode(deviceCode);
        code.setUserCode(userCode);
        code.setVerificationUri(verificationUri);
        code.setClientId(clientId);
        code.setStatus(DeviceCode.Status.PENDING);
        code.setExpiresAt(LocalDateTime.now().plusMinutes(codeExpiryMinutes));

        DeviceCode saved = deviceCodeRepository.save(code);
        log.info("Created device code for client: {}, user code: {}", clientId, userCode);

        return saved;
    }

    /**
     * Step 2: Get device code by user code (for web UI).
     */
    @Transactional(readOnly = true)
    public Optional<DeviceCode> findByUserCode(String userCode) {
        return deviceCodeRepository.findByUserCode(userCode);
    }

    /**
     * Step 3: User authorizes the device.
     */
    public void authorizeDevice(String userCode, User user) {
        DeviceCode code = deviceCodeRepository.findByUserCode(userCode)
                .orElseThrow(() -> new IllegalArgumentException("Invalid user code"));

        if (!code.isPending()) {
            throw new IllegalStateException("Device code is not pending");
        }

        if (code.isExpired()) {
            code.expire();
            deviceCodeRepository.save(code);
            throw new IllegalStateException("Device code has expired");
        }

        code.authorize(user);
        deviceCodeRepository.save(code);

        log.info("User {} authorized device with code {}",
                LogSanitizer.username(user.getUsername()),
                LogSanitizer.sanitize(userCode));
    }

    /**
     * Step 4: Device polls for token.
     */
    public Optional<DeviceToken> pollForToken(String deviceCode) {
        DeviceCode code = deviceCodeRepository.findByDeviceCode(deviceCode)
                .orElseThrow(() -> new IllegalArgumentException("Invalid device code"));

        if (code.isExpired()) {
            code.expire();
            deviceCodeRepository.save(code);
            return Optional.empty();
        }

        if (code.getStatus() == DeviceCode.Status.AUTHORIZED && code.getUser() != null) {
            // Generate access token
            DeviceToken token = createAccessToken(code.getUser(), code.getClientId(), null);
            log.info("Issued access token for user {} via device code", code.getUser().getUsername());
            return Optional.of(token);
        }

        if (code.getStatus() == DeviceCode.Status.DENIED) {
            throw new IllegalStateException("Device authorization was denied");
        }

        // Still pending
        return Optional.empty();
    }

    /**
     * Create access token for user.
     */
    public DeviceToken createAccessToken(User user, String clientId, String deviceName) {
        // BCrypt has a 72-byte limit, so we use 32 bytes which gives us 64 hex chars
        String token = generateSecureToken(32);
        String tokenHash = passwordEncoder.encode(token);

        DeviceToken deviceToken = new DeviceToken();
        deviceToken.setUser(user);
        deviceToken.setTokenHash(tokenHash);
        deviceToken.setClientId(clientId);
        deviceToken.setDeviceName(deviceName);
        deviceToken.setScopes("drafts:create,drafts:approve,drafts:read,analysis:read");
        deviceToken.setExpiresAt(LocalDateTime.now().plusDays(tokenExpiryDays));
        deviceToken.setCreatedBy(user.getUsername());

        DeviceToken saved = deviceTokenRepository.save(deviceToken);

        log.info("Created access token for user {} (client: {})", user.getUsername(), clientId);

        // Return a detached copy with plaintext token (only returned once!)
        // We can't modify the saved entity because it's managed and would update the DB
        DeviceToken result = new DeviceToken();
        result.setId(saved.getId());
        result.setUser(saved.getUser());
        result.setTokenHash(token); // Plaintext for caller
        result.setClientId(saved.getClientId());
        result.setDeviceName(saved.getDeviceName());
        result.setScopes(saved.getScopes());
        result.setExpiresAt(saved.getExpiresAt());
        result.setCreatedAt(saved.getCreatedAt());
        result.setCreatedBy(saved.getCreatedBy());

        return result;
    }

    /**
     * Validate access token and return associated user.
     */
    @Transactional(readOnly = true)
    public Optional<DeviceToken> validateToken(String token) {
        // Find all active tokens with users eagerly fetched
        // (We can't query by token since we store hashes)
        List<DeviceToken> allTokens = deviceTokenRepository.findAllActiveWithUser();

        for (DeviceToken deviceToken : allTokens) {
            if (passwordEncoder.matches(token, deviceToken.getTokenHash())) {
                return Optional.of(deviceToken);
            }
        }

        return Optional.empty();
    }

    /**
     * Revoke device token.
     */
    public void revokeToken(UUID tokenId, String revokedBy) {
        DeviceToken token = deviceTokenRepository.findById(tokenId)
                .orElseThrow(() -> new IllegalArgumentException("Token not found"));

        token.revoke(revokedBy);
        deviceTokenRepository.save(token);

        log.info("Revoked device token {} by {}", tokenId, revokedBy);
    }

    /**
     * Get active tokens for user.
     */
    @Transactional(readOnly = true)
    public List<DeviceToken> getActiveTokens(User user) {
        return deviceTokenRepository.findActiveByUser(user);
    }

    /**
     * Generate secure random token.
     */
    private String generateSecureToken(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    /**
     * Generate user-friendly code (e.g., "WDJB-MJHT").
     */
    private String generateUserCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < USER_CODE_LENGTH; i++) {
            if (i == USER_CODE_LENGTH / 2) {
                code.append('-');
            }
            code.append(USER_CODE_CHARS.charAt(RANDOM.nextInt(USER_CODE_CHARS.length())));
        }
        return code.toString();
    }

    /**
     * Cleanup expired device codes (runs every hour).
     */
    @Scheduled(fixedRate = 3600000) // 1 hour
    public void cleanupExpiredCodes() {
        LocalDateTime now = LocalDateTime.now();

        // Expire pending codes
        int expired = deviceCodeRepository.expireOldCodes(now);
        if (expired > 0) {
            log.info("Expired {} old device codes", expired);
        }

        // Delete old codes (>24 hours)
        int deleted = deviceCodeRepository.deleteOldCodes(now.minusHours(24));
        if (deleted > 0) {
            log.info("Deleted {} old device codes", deleted);
        }
    }
}
