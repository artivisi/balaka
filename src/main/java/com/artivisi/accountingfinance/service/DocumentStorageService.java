package com.artivisi.accountingfinance.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class DocumentStorageService {

    @Value("${app.storage.documents.path}")
    private String storagePath;

    @Value("${app.storage.documents.max-file-size}")
    private long maxFileSize;

    @Value("${app.storage.documents.allowed-types}")
    private String allowedTypes;

    private Path rootLocation;
    private List<String> allowedContentTypes;

    @PostConstruct
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

        Path targetPath = targetDirectory.resolve(storedFilename);

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }

        log.debug("Stored file: {} -> {}", originalFilename, targetPath);

        // Return relative path from root
        return subPath + "/" + storedFilename;
    }

    /**
     * Load a file as Resource.
     */
    public Resource loadAsResource(String relativePath) {
        try {
            Path filePath = rootLocation.resolve(relativePath).normalize();

            // Prevent path traversal attacks
            if (!filePath.startsWith(rootLocation)) {
                throw new SecurityException("Access denied: path traversal attempt detected");
            }

            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("Could not read file: " + relativePath);
            }
        } catch (MalformedURLException e) {
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
        log.debug("Deleted file: {}", filePath);
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
     */
    private String getExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0) {
            return filename.substring(lastDot);
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

        String extension = getExtension(filename);
        String storedFilename = UUID.randomUUID().toString() + extension;

        // Organize by year/month
        LocalDate today = LocalDate.now();
        String subPath = String.format("%d/%02d", today.getYear(), today.getMonthValue());
        Path targetDirectory = rootLocation.resolve(subPath);
        Files.createDirectories(targetDirectory);

        Path targetPath = targetDirectory.resolve(storedFilename);
        Files.write(targetPath, bytes);

        log.debug("Stored file from bytes: {} -> {}", filename, targetPath);

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
}
