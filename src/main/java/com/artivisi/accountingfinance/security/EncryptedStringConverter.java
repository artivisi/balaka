package com.artivisi.accountingfinance.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * JPA AttributeConverter that encrypts sensitive String fields using AES-256-GCM.
 *
 * Security features:
 * - AES-256-GCM authenticated encryption (confidentiality + integrity)
 * - Unique 12-byte IV per encryption (prepended to ciphertext)
 * - 128-bit authentication tag
 *
 * Usage:
 * Add @Convert(converter = EncryptedStringConverter.class) to entity fields.
 *
 * Configuration:
 * Set app.encryption.key property or APP_ENCRYPTION_KEY env var (32 bytes, Base64 encoded).
 * Generate with: openssl rand -base64 32
 */
@Converter
@Component
@Slf4j
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final String PREFIX = "ENC:";

    private static SecretKey secretKey;
    private static boolean encryptionEnabled = false;
    private static final SecureRandom secureRandom = new SecureRandom();

    /**
     * Initialize encryption key from environment variable.
     * If no key is configured, encryption is disabled (passthrough mode).
     */
    @Value("${app.encryption.key:}")
    public void setEncryptionKey(String keyBase64) {
        if (keyBase64 == null || keyBase64.isBlank()) {
            log.warn("Encryption key not configured - PII fields will NOT be encrypted. " +
                    "Set app.encryption.key property or APP_ENCRYPTION_KEY env var for production.");
            encryptionEnabled = false;
            return;
        }

        try {
            byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
            if (keyBytes.length != 32) {
                log.error("Encryption key must be exactly 32 bytes (256 bits) for AES-256. Got {} bytes", keyBytes.length);
                throw new IllegalArgumentException("Invalid encryption key length");
            }
            secretKey = new SecretKeySpec(keyBytes, "AES");
            encryptionEnabled = true;
            log.info("PII field encryption enabled");
        } catch (IllegalArgumentException e) {
            log.error("Invalid encryption key format (must be Base64): {}", e.getMessage());
            throw new IllegalStateException("Failed to initialize encryption", e);
        }
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isBlank()) {
            return attribute;
        }

        if (!encryptionEnabled) {
            return attribute;
        }

        try {
            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            // Encrypt
            byte[] ciphertext = cipher.doFinal(attribute.getBytes());

            // Combine IV + ciphertext
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            byteBuffer.put(iv);
            byteBuffer.put(ciphertext);

            // Encode as Base64 with prefix
            return PREFIX + Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            log.error("Encryption failed: {}", e.getMessage());
            throw new IllegalStateException("Failed to encrypt data", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return dbData;
        }

        // Check if data is encrypted (has prefix)
        if (!dbData.startsWith(PREFIX)) {
            // Data is not encrypted (legacy data or encryption disabled)
            return dbData;
        }

        if (!encryptionEnabled) {
            log.warn("Encrypted data found but encryption key not configured");
            return dbData; // Return encrypted data as-is (application must handle)
        }

        try {
            // Remove prefix and decode
            String base64Data = dbData.substring(PREFIX.length());
            byte[] encryptedData = Base64.getDecoder().decode(base64Data);

            // Extract IV and ciphertext
            ByteBuffer byteBuffer = ByteBuffer.wrap(encryptedData);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] ciphertext = new byte[byteBuffer.remaining()];
            byteBuffer.get(ciphertext);

            // Initialize cipher for decryption
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            // Decrypt
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext);
        } catch (Exception e) {
            log.error("Decryption failed: {}", e.getMessage());
            throw new IllegalStateException("Failed to decrypt data", e);
        }
    }

    /**
     * Check if encryption is enabled.
     */
    public static boolean isEncryptionEnabled() {
        return encryptionEnabled;
    }
}
