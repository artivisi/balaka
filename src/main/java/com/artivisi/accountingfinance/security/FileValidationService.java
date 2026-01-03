package com.artivisi.accountingfinance.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import static com.artivisi.accountingfinance.security.LogSanitizer.filename;
import static com.artivisi.accountingfinance.security.LogSanitizer.sanitize;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/**
 * Service for validating file uploads using magic byte signatures.
 * Protects against content-type spoofing attacks where malicious files
 * are uploaded with incorrect MIME types.
 */
@Service
@Slf4j
public class FileValidationService {

    // Magic byte signatures for supported file types
    // Each entry maps MIME type to a set of valid magic byte signatures
    private static final Map<String, Set<byte[]>> MAGIC_BYTES = Map.ofEntries(
            // PDF: %PDF-
            Map.entry("application/pdf", Set.of(
                    new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D}
            )),
            // JPEG: FFD8FF
            Map.entry("image/jpeg", Set.of(
                    new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}
            )),
            // PNG: 89 50 4E 47 0D 0A 1A 0A
            Map.entry("image/png", Set.of(
                    new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}
            )),
            // GIF: GIF87a or GIF89a
            Map.entry("image/gif", Set.of(
                    "GIF87a".getBytes(StandardCharsets.US_ASCII),
                    "GIF89a".getBytes(StandardCharsets.US_ASCII)
            )),
            // WebP: RIFF....WEBP
            Map.entry("image/webp", Set.of(
                    new byte[]{0x52, 0x49, 0x46, 0x46}  // RIFF
            )),
            // ZIP: PK (50 4B 03 04) or PK (50 4B 05 06) for empty
            Map.entry("application/zip", Set.of(
                    new byte[]{0x50, 0x4B, 0x03, 0x04},
                    new byte[]{0x50, 0x4B, 0x05, 0x06}
            )),
            // XLSX: Same as ZIP (Office Open XML)
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", Set.of(
                    new byte[]{0x50, 0x4B, 0x03, 0x04}
            )),
            // DOCX: Same as ZIP (Office Open XML)
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", Set.of(
                    new byte[]{0x50, 0x4B, 0x03, 0x04}
            ))
    );

    // Maximum bytes to read for magic byte validation
    private static final int MAX_HEADER_BYTES = 12;

    /**
     * Validates that file content matches the declared Content-Type.
     *
     * @param file        the uploaded file
     * @param contentType the declared content type
     * @return true if content matches declared type, false otherwise
     * @throws IOException if file cannot be read
     */
    public boolean validateMagicBytes(MultipartFile file, String contentType) throws IOException {
        if (file == null || file.isEmpty()) {
            return false;
        }

        Set<byte[]> validSignatures = MAGIC_BYTES.get(contentType);
        if (validSignatures == null) {
            // No magic byte validation defined for this type
            // Allow but log warning
            log.warn("No magic byte validation for content type: {}", sanitize(contentType));
            return true;
        }

        byte[] fileHeader = readHeader(file, MAX_HEADER_BYTES);

        for (byte[] signature : validSignatures) {
            if (startsWith(fileHeader, signature)) {
                return true;
            }
        }

        log.warn("Magic byte validation failed for file '{}' with declared type '{}'",
                filename(file.getOriginalFilename()),
                sanitize(contentType));
        return false;
    }

    /**
     * Validates that file content matches the declared Content-Type.
     *
     * @param content     the file content as byte array
     * @param contentType the declared content type
     * @return true if content matches declared type, false otherwise
     */
    public boolean validateMagicBytes(byte[] content, String contentType) {
        if (content == null || content.length == 0) {
            return false;
        }

        Set<byte[]> validSignatures = MAGIC_BYTES.get(contentType);
        if (validSignatures == null) {
            log.warn("No magic byte validation for content type: {}", sanitize(contentType));
            return true;
        }

        byte[] fileHeader = Arrays.copyOf(content, Math.min(content.length, MAX_HEADER_BYTES));

        for (byte[] signature : validSignatures) {
            if (startsWith(fileHeader, signature)) {
                return true;
            }
        }

        log.warn("Magic byte validation failed for content with declared type '{}'",
                sanitize(contentType));
        return false;
    }

    /**
     * Reads the first n bytes from a MultipartFile.
     */
    private byte[] readHeader(MultipartFile file, int maxBytes) throws IOException {
        try (InputStream is = file.getInputStream()) {
            byte[] buffer = new byte[maxBytes];
            int bytesRead = is.read(buffer);
            if (bytesRead < maxBytes) {
                return Arrays.copyOf(buffer, bytesRead);
            }
            return buffer;
        }
    }

    /**
     * Checks if array starts with the given prefix.
     */
    private boolean startsWith(byte[] array, byte[] prefix) {
        if (array.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (array[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

}
