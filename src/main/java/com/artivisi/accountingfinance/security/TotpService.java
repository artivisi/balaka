package com.artivisi.accountingfinance.security;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * TOTP as defined by RFC 6238 (and RFC 4226 for HOTP), with the parameters the
 * common authenticator apps assume: HMAC-SHA1, 6 digits, a 30 second step.
 *
 * <p>HMAC-SHA1 is not a weakness here: TOTP security rests on the 160-bit random
 * secret and the 30 second window, not on collision resistance of the digest,
 * and every authenticator app supports it.
 */
@Service
// HMAC-SHA1 is the algorithm the authenticator ecosystem expects (RFC 6238 uses it
// for its reference vectors and Google Authenticator does not implement SHA-256).
// HMAC is not affected by the collision attacks that retired SHA-1 for signatures:
// this secret is 160 random bits, not an attacker-influenced message.
@SuppressWarnings("java:S4790")
public class TotpService {

    /** 160 bits, the size RFC 4226 recommends for a shared secret. */
    private static final int SECRET_BYTES = 20;
    private static final int STEP_SECONDS = 30;
    private static final int DIGITS = 6;
    /** Accept the previous and the next step, for clock skew only. */
    private static final int ALLOWED_DRIFT_STEPS = 1;
    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private final SecureRandom secureRandom = new SecureRandom();

    /** A fresh Base32 secret, without padding so it can be typed into an app. */
    public String generateSecret() {
        byte[] buffer = new byte[SECRET_BYTES];
        secureRandom.nextBytes(buffer);
        return base32Encode(buffer);
    }

    public long currentStep() {
        return Instant.now().getEpochSecond() / STEP_SECONDS;
    }

    /**
     * The time step a code belongs to, or -1 when the code does not match the
     * secret inside the allowed drift.
     */
    public long matchingStep(String secret, String code, long nowEpochSeconds) {
        if (secret == null || code == null) {
            return -1L;
        }
        String candidate = code.replace(" ", "").trim();
        if (candidate.length() != DIGITS || !candidate.chars().allMatch(Character::isDigit)) {
            return -1L;
        }

        byte[] key;
        try {
            key = base32Decode(secret);
        } catch (IllegalArgumentException _) {
            return -1L;
        }

        long currentStep = nowEpochSeconds / STEP_SECONDS;
        for (int drift = -ALLOWED_DRIFT_STEPS; drift <= ALLOWED_DRIFT_STEPS; drift++) {
            long step = currentStep + drift;
            if (step < 0) {
                continue;
            }
            if (MessageDigest.isEqual(
                    generateCode(key, step).getBytes(StandardCharsets.US_ASCII),
                    candidate.getBytes(StandardCharsets.US_ASCII))) {
                return step;
            }
        }
        return -1L;
    }

    public long matchingStep(String secret, String code) {
        return matchingStep(secret, code, Instant.now().getEpochSecond());
    }

    public String generateCode(String secret, long step) {
        return generateCode(base32Decode(secret), step);
    }

    private String generateCode(byte[] key, long step) {
        try {
            byte[] data = new byte[8];
            long counter = step;
            for (int i = 7; i >= 0; i--) {
                data[i] = (byte) (counter & 0xFF);
                counter >>= 8;
            }

            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(data);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            return String.format("%0" + DIGITS + "d", binary % 1_000_000);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot compute a TOTP code", e);
        }
    }

    /**
     * The otpauth URI an authenticator app can import. The algorithm and digit
     * parameters are explicit so an app that defaults to something else still
     * produces matching codes.
     */
    public String getOtpAuthUri(String secret, String accountName, String issuer) {
        return String.format(
                "otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=%d&period=%d",
                urlEncode(issuer), urlEncode(accountName), secret, urlEncode(issuer), DIGITS, STEP_SECONDS);
    }

    public int getDigits() {
        return DIGITS;
    }

    public int getStepSeconds() {
        return STEP_SECONDS;
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    static String base32Encode(byte[] data) {
        StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                out.append(BASE32_ALPHABET.charAt((buffer >> (bitsLeft - 5)) & 0x1F));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            out.append(BASE32_ALPHABET.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return out.toString();
    }

    static byte[] base32Decode(String value) {
        String normalized = value.replace("=", "").replace(" ", "").toUpperCase(java.util.Locale.ROOT);
        int bits = 0;
        int buffer = 0;
        byte[] out = new byte[normalized.length() * 5 / 8];
        int index = 0;
        for (int i = 0; i < normalized.length(); i++) {
            int decoded = BASE32_ALPHABET.indexOf(normalized.charAt(i));
            if (decoded < 0) {
                throw new IllegalArgumentException("Not a Base32 string");
            }
            buffer = (buffer << 5) | decoded;
            bits += 5;
            if (bits >= 8) {
                out[index++] = (byte) ((buffer >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        if (index == out.length) {
            return out;
        }
        byte[] trimmed = new byte[index];
        System.arraycopy(out, 0, trimmed, 0, index);
        return trimmed;
    }
}
