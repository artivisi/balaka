package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.security.FileEncryptionService;
import com.artivisi.accountingfinance.security.FileValidationService;
import com.artivisi.accountingfinance.security.LogSanitizer;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentStorageService {

    private final FileValidationService fileValidationService;
    private final FileEncryptionService fileEncryptionService;

    @Value("${app.storage.documents.path}")
    private String storagePath;

    @Value("${app.storage.documents.max-file-size}")
    private long maxFileSize;

    @Value("${app.storage.documents.allowed-types}")
    private String allowedTypes;

    private Path rootLocation;
    private List<String> allowedContentTypes;

    @PostConstruct
    @SuppressFBWarnings(
        value = "PATH_TRAVERSAL_IN",
        justification = "Storage path is configured by system administrator in application.properties " +
                        "(app.storage.documents.path). Path is normalized and validated. All file operations " +
                        "(store, loadAsResource, delete) have path traversal protection via startsWith(rootLocation) checks. " +
                        "This is intentional design to allow flexible storage location configuration."
    )
    public void init() {
        this.rootLocation = Paths.get(storagePath).toAbsolutePath().normalize();
        this.allowedContentTypes = Arrays.asList(allowedTypes.split(","));

        try {
            Files.createDirectories(rootLocation);
            log.info("Document storage initialized at: {}", rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("Could not create document storage directory: " + rootLocation, e);
        }
    }

    /**
     * Store a file and return the storage path relative to root.
     * Files are encrypted before saving if encryption is enabled.
     */
    public String store(MultipartFile file) throws IOException {
        validateFile(file);

        String originalFilename = file.getOriginalFilename();
        String extension = getExtension(originalFilename);
        String storedFilename = UUID.randomUUID().toString() + extension;

        // Organize by year/month
        LocalDate today = LocalDate.now();
        String subPath = String.format("%d/%02d", today.getYear(), today.getMonthValue());
        Path targetDirectory = rootLocation.resolve(subPath);
        Files.createDirectories(targetDirectory);

        Path targetPath = targetDirectory.resolve(storedFilename).normalize();

        // Prevent path traversal attacks
        if (!targetPath.startsWith(rootLocation)) {
            throw new SecurityException("Access denied: path traversal attempt detected");
        }

        // Read file content and encrypt before saving
        byte[] fileContent = file.getBytes();
        byte[] contentToStore = fileEncryptionService.encrypt(fileContent);
        Files.write(targetPath, contentToStore);

        if (fileEncryptionService.isEncryptionEnabled()) {
            log.debug("Stored encrypted file: {} -> {}", LogSanitizer.filename(originalFilename), LogSanitizer.sanitize(targetPath.toString()));
        } else {
            log.debug("Stored file (unencrypted): {} -> {}", LogSanitizer.filename(originalFilename), LogSanitizer.sanitize(targetPath.toString()));
        }

        // Return relative path from root
        return subPath + "/" + storedFilename;
    }

    /**
     * Load a file as Resource.
     * Files are decrypted if they were encrypted.
     */
    public Resource loadAsResource(String relativePath) {
        try {
            Path filePath = rootLocation.resolve(relativePath).normalize();

            // Prevent path traversal attacks
            if (!filePath.startsWith(rootLocation)) {
                throw new SecurityException("Access denied: path traversal attempt detected");
            }

            if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
                throw new RuntimeException("Could not read file: " + relativePath);
            }

            // Read encrypted content and decrypt
            byte[] encryptedContent = Files.readAllBytes(filePath);
            byte[] decryptedContent = fileEncryptionService.decrypt(encryptedContent);

            // Return as ByteArrayResource
            return new ByteArrayResource(decryptedContent) {
                @Override
                public String getFilename() {
                    Path fileName = filePath.getFileName();
                    if (fileName == null) {
                        throw new IllegalStateException("Cannot get filename from path: " + filePath);
                    }
                    return fileName.toString();
                }
            };
        } catch (IOException e) {
            throw new RuntimeException("Could not read file: " + relativePath, e);
        }
    }

    /**
     * Delete a file.
     */
    public void delete(String relativePath) throws IOException {
        Path filePath = rootLocation.resolve(relativePath).normalize();

        // Prevent path traversal attacks
        if (!filePath.startsWith(rootLocation)) {
            throw new SecurityException("Access denied: path traversal attempt detected");
        }

        Files.deleteIfExists(filePath);
        log.debug("Deleted file: {}", LogSanitizer.filename(filePath.toString()));
    }

    /**
     * Calculate SHA-256 checksum of a file.
     */
    public String calculateChecksum(MultipartFile file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(file.getBytes());
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Validate file before storage.
     */
    public void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException(
                    String.format("File size %d exceeds maximum allowed size %d bytes",
                            file.getSize(), maxFileSize));
        }

        String contentType = file.getContentType();
        if (contentType == null || !allowedContentTypes.contains(contentType)) {
            throw new IllegalArgumentException(
                    String.format("File type '%s' is not allowed. Allowed types: %s",
                            contentType, allowedTypes));
        }

        String filename = file.getOriginalFilename();
        if (filename == null || filename.contains("..")) {
            throw new IllegalArgumentException("Invalid filename: " + filename);
        }

        // Magic byte validation to prevent content-type spoofing
        try {
            if (!fileValidationService.validateMagicBytes(file, contentType)) {
                throw new IllegalArgumentException(
                        "File content does not match declared type. Possible content-type spoofing detected.");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not validate file content: " + e.getMessage());
        }
    }

    /**
     * Check if file exists.
     */
    public boolean exists(String relativePath) {
        Path filePath = rootLocation.resolve(relativePath).normalize();

        // Prevent path traversal attacks
        if (!filePath.startsWith(rootLocation)) {
            return false;
        }

        return Files.exists(filePath);
    }

    /**
     * Get file extension including the dot.
     * Validates the extension to prevent path traversal attacks.
     */
    private String getExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0) {
            String extension = filename.substring(lastDot);
            // Validate extension to prevent path traversal
            if (extension.contains("/") || extension.contains("\\") || extension.contains("..")) {
                throw new IllegalArgumentException("Invalid file extension: " + extension);
            }
            return extension;
        }
        return "";
    }

    /**
     * Convert bytes to hex string.
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Get the root storage path.
     */
    public Path getRootLocation() {
        return rootLocation;
    }

    /**
     * Store file from byte array.
     * Files are encrypted before saving if encryption is enabled.
     */
    public String storeFromBytes(byte[] bytes, String filename, String contentType) throws IOException {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("File content is empty");
        }

        if (bytes.length > maxFileSize) {
            throw new IllegalArgumentException(
                    String.format("File size %d exceeds maximum allowed size %d bytes",
                            bytes.length, maxFileSize));
        }

        if (contentType == null || !allowedContentTypes.contains(contentType)) {
            throw new IllegalArgumentException(
                    String.format("File type '%s' is not allowed. Allowed types: %s",
                            contentType, allowedTypes));
        }

        // Magic byte validation to prevent content-type spoofing
        if (!fileValidationService.validateMagicBytes(bytes, contentType)) {
            throw new IllegalArgumentException(
                    "File content does not match declared type. Possible content-type spoofing detected.");
        }

        String extension = getExtension(filename);
        String storedFilename = UUID.randomUUID().toString() + extension;

        // Organize by year/month
        LocalDate today = LocalDate.now();
        String subPath = String.format("%d/%02d", today.getYear(), today.getMonthValue());
        Path targetDirectory = rootLocation.resolve(subPath);
        Files.createDirectories(targetDirectory);

        Path targetPath = targetDirectory.resolve(storedFilename).normalize();

        // Prevent path traversal attacks
        if (!targetPath.startsWith(rootLocation)) {
            throw new SecurityException("Access denied: path traversal attempt detected");
        }

        // Encrypt before saving
        byte[] contentToStore = fileEncryptionService.encrypt(bytes);
        Files.write(targetPath, contentToStore);

        if (fileEncryptionService.isEncryptionEnabled()) {
            log.debug("Stored encrypted file from bytes: {} -> {}", LogSanitizer.filename(filename), targetPath);
        } else {
            log.debug("Stored file from bytes (unencrypted): {} -> {}", LogSanitizer.filename(filename), targetPath);
        }

        return subPath + "/" + storedFilename;
    }

    /**
     * Calculate SHA-256 checksum from byte array.
     */
    public String calculateChecksumFromBytes(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Check if file encryption is enabled.
     */
    public boolean isEncryptionEnabled() {
        return fileEncryptionService.isEncryptionEnabled();
    }

    /**
     * Load raw file content (encrypted) without decryption.
     * Useful for checking if a file is encrypted.
     */
    public byte[] loadRawContent(String relativePath) throws IOException {
        Path filePath = rootLocation.resolve(relativePath).normalize();

        // Prevent path traversal attacks
        if (!filePath.startsWith(rootLocation)) {
            throw new SecurityException("Access denied: path traversal attempt detected");
        }

        return Files.readAllBytes(filePath);
    }

    /**
     * Check if the file at the given path is encrypted.
     */
    public boolean isFileEncrypted(String relativePath) throws IOException {
        byte[] rawContent = loadRawContent(relativePath);
        return fileEncryptionService.isEncrypted(rawContent);
    }
}
