package com.artivisi.accountingfinance.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Service for encrypting and decrypting files using AES-256-GCM.
 *
 * Security features:
 * - AES-256-GCM authenticated encryption (confidentiality + integrity)
 * - Unique 12-byte IV per encryption (prepended to ciphertext)
 * - 128-bit authentication tag
 * - Streaming support for large files
 *
 * Configuration:
 * Set app.encryption.key property or APP_ENCRYPTION_KEY env var (32 bytes, Base64 encoded).
 * Generate with: openssl rand -base64 32
 */
@Service
@Slf4j
public class FileEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final byte[] MAGIC_HEADER = "ENCF".getBytes(); // Encrypted File marker
    private static final int VERSION = 1;

    private SecretKey secretKey;
    private boolean encryptionEnabled = false;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.encryption.key:}")
    private String encryptionKeyBase64;

    @PostConstruct
    public void init() {
        if (encryptionKeyBase64 == null || encryptionKeyBase64.isBlank()) {
            log.warn("Encryption key not configured - document files will NOT be encrypted. " +
                    "Set app.encryption.key property or APP_ENCRYPTION_KEY env var for production.");
            encryptionEnabled = false;
            return;
        }

        try {
            byte[] keyBytes = Base64.getDecoder().decode(encryptionKeyBase64);
            if (keyBytes.length != 32) {
                log.error("Encryption key must be exactly 32 bytes (256 bits) for AES-256. Got {} bytes", keyBytes.length);
                throw new IllegalArgumentException("Invalid encryption key length");
            }
            secretKey = new SecretKeySpec(keyBytes, "AES");
            encryptionEnabled = true;
            log.info("Document file encryption enabled");
        } catch (IllegalArgumentException e) {
            log.error("Invalid encryption key format (must be Base64): {}", e.getMessage());
            throw new IllegalStateException("Failed to initialize file encryption", e);
        }
    }

    /**
     * Encrypt file content.
     *
     * Format: MAGIC_HEADER (4 bytes) + VERSION (1 byte) + IV (12 bytes) + CIPHERTEXT
     *
     * @param plaintext The original file content
     * @return Encrypted content with header, or original content if encryption is disabled
     */
    public byte[] encrypt(byte[] plaintext) {
        if (plaintext == null || plaintext.length == 0) {
            return plaintext;
        }

        if (!encryptionEnabled) {
            return plaintext;
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
            byte[] ciphertext = cipher.doFinal(plaintext);

            // Build output: MAGIC + VERSION + IV + CIPHERTEXT
            ByteBuffer buffer = ByteBuffer.allocate(
                    MAGIC_HEADER.length + 1 + GCM_IV_LENGTH + ciphertext.length);
            buffer.put(MAGIC_HEADER);
            buffer.put((byte) VERSION);
            buffer.put(iv);
            buffer.put(ciphertext);

            return buffer.array();
        } catch (Exception e) {
            log.error("File encryption failed: {}", e.getMessage());
            throw new IllegalStateException("Failed to encrypt file", e);
        }
    }

    /**
     * Decrypt file content.
     *
     * @param encryptedData The encrypted file content with header
     * @return Decrypted content, or original content if not encrypted
     */
    public byte[] decrypt(byte[] encryptedData) {
        if (encryptedData == null || encryptedData.length == 0) {
            return encryptedData;
        }

        // Check if data is encrypted (has magic header)
        if (!isEncrypted(encryptedData)) {
            return encryptedData; // Not encrypted, return as-is
        }

        if (!encryptionEnabled) {
            log.warn("Encrypted file found but encryption key not configured");
            throw new IllegalStateException("Cannot decrypt file: encryption key not configured");
        }

        try {
            ByteBuffer buffer = ByteBuffer.wrap(encryptedData);

            // Skip magic header
            buffer.position(MAGIC_HEADER.length);

            // Read version
            byte version = buffer.get();
            if (version != VERSION) {
                throw new IllegalStateException("Unsupported encryption version: " + version);
            }

            // Extract IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);

            // Extract ciphertext
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            // Initialize cipher for decryption
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            // Decrypt
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            log.error("File decryption failed: {}", e.getMessage());
            throw new IllegalStateException("Failed to decrypt file", e);
        }
    }

    /**
     * Encrypt an input stream and return encrypted bytes.
     *
     * @param inputStream The input stream to encrypt
     * @return Encrypted content
     */
    public byte[] encrypt(InputStream inputStream) throws IOException {
        byte[] plaintext = inputStream.readAllBytes();
        return encrypt(plaintext);
    }

    /**
     * Decrypt encrypted data and return as input stream.
     *
     * @param encryptedData The encrypted data
     * @return Input stream of decrypted content
     */
    public InputStream decryptToStream(byte[] encryptedData) {
        byte[] decrypted = decrypt(encryptedData);
        return new ByteArrayInputStream(decrypted);
    }

    /**
     * Check if data is encrypted (has magic header).
     *
     * @param data The data to check
     * @return true if data appears to be encrypted
     */
    public boolean isEncrypted(byte[] data) {
        if (data == null || data.length < MAGIC_HEADER.length + 1 + GCM_IV_LENGTH) {
            return false;
        }

        for (int i = 0; i < MAGIC_HEADER.length; i++) {
            if (data[i] != MAGIC_HEADER[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if file encryption is enabled.
     */
    public boolean isEncryptionEnabled() {
        return encryptionEnabled;
    }

    /**
     * Get the overhead size added by encryption (header + IV + auth tag).
     * Useful for calculating encrypted file size.
     */
    public int getEncryptionOverhead() {
        // MAGIC (4) + VERSION (1) + IV (12) + AUTH_TAG (16)
        return MAGIC_HEADER.length + 1 + GCM_IV_LENGTH + (GCM_TAG_LENGTH / 8);
    }
}
