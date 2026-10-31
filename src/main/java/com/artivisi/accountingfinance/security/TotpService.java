package com.artivisi.accountingfinance.security;

import org.apache.commons.codec.binary.Base32;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/**
 * TOTP (Time-based One-Time Password) service implementing RFC 6238.
 * Uses HMAC-SHA1 with 30-second time window and 6-digit codes.
 */
@Service
public class TotpService {

    private static final int SECRET_SIZE = 20; // 160 bits
    private static final int WINDOW_SIZE = 30; // 30 seconds
    private static final int DIGITS = 6;
    private static final int ALLOWED_DRIFT = 2; // ±2 windows (60s) for clock skew

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generate a new Base32-encoded TOTP secret.
     */
    public String generateSecret() {
        byte[] buffer = new byte[SECRET_SIZE];
        secureRandom.nextBytes(buffer);
        return new Base32().encodeToString(buffer).replace("=", "");
    }

    /**
     * Generate a TOTP code for the given secret and time.
     */
    public String generateCode(String secret, long time) {
        try {
            byte[] key = new Base32().decode(secret);
            long counter = time / WINDOW_SIZE;

            byte[] data = new byte[8];
            for (int i = 7; i >= 0; i--) {
                data[i] = (byte) (counter & 0xFF);
                counter >>= 8;
            }

            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            int otp = binary % (int) Math.pow(10, DIGITS);
            return String.format("%0" + DIGITS + "d", otp);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate TOTP code", e);
        }
    }

    /**
     * Verify a TOTP code against the secret.
     * Allows ±1 time window for clock skew.
     */
    public boolean verifyCode(String secret, String code) {
        if (secret == null || code == null || code.length() != DIGITS) {
            return false;
        }

        long currentTime = Instant.now().getEpochSecond();
        for (int i = -ALLOWED_DRIFT; i <= ALLOWED_DRIFT; i++) {
            long time = currentTime + (i * WINDOW_SIZE);
            String generated = generateCode(secret, time);
            if (generated.equals(code)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Generate the otpauth URI for QR code generation.
     */
    public String getOtpAuthUri(String secret, String accountName, String issuer) {
        return String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=%d&period=%d",
                issuer, accountName, secret, issuer, DIGITS, WINDOW_SIZE);
    }
}
