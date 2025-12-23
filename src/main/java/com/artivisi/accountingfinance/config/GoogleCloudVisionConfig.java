package com.artivisi.accountingfinance.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.cloud.vision.v1.ImageAnnotatorSettings;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
@ConfigurationProperties(prefix = "google.cloud.vision")
@Getter
@Setter
public class GoogleCloudVisionConfig {

    private static final Logger log = LoggerFactory.getLogger(GoogleCloudVisionConfig.class);

    private boolean enabled;
    private String credentialsPath;

    @Bean
    @ConditionalOnProperty(name = "google.cloud.vision.enabled", havingValue = "true")
    @SuppressFBWarnings(
        value = "PATH_TRAVERSAL_IN",
        justification = "Credentials path is configured by system administrator in application.properties. " +
                        "Path is validated (normalized, checked for existence, readability, and file type) " +
                        "before use. This is intentional design to allow flexible credential file placement."
    )
    public ImageAnnotatorClient imageAnnotatorClient() throws IOException {
        if (credentialsPath == null || credentialsPath.isBlank()) {
            log.warn("Google Cloud Vision credentials path not configured, using default credentials");
            return ImageAnnotatorClient.create();
        }

        // Validate and normalize path to prevent path traversal attacks
        Path normalizedPath = Paths.get(credentialsPath).normalize().toAbsolutePath();

        // Ensure the file exists and is readable
        if (!Files.exists(normalizedPath)) {
            throw new IOException("Credentials file not found: " + normalizedPath);
        }
        if (!Files.isReadable(normalizedPath)) {
            throw new IOException("Credentials file not readable: " + normalizedPath);
        }
        if (!Files.isRegularFile(normalizedPath)) {
            throw new IOException("Credentials path is not a regular file: " + normalizedPath);
        }

        log.info("Initializing Google Cloud Vision with credentials from: {}", normalizedPath);
        GoogleCredentials credentials = GoogleCredentials.fromStream(Files.newInputStream(normalizedPath));
        ImageAnnotatorSettings settings = ImageAnnotatorSettings.newBuilder()
                .setCredentialsProvider(() -> credentials)
                .build();
        return ImageAnnotatorClient.create(settings);
    }
}
