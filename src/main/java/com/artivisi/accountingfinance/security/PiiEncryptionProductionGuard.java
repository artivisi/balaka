package com.artivisi.accountingfinance.security;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

import java.util.Base64;

/**
 * Refuses to boot a deployment without a usable PII encryption key.
 *
 * <p>This is an {@link EnvironmentPostProcessor} on purpose: it runs while the
 * environment is being prepared, before the application context exists, so the
 * embedded web server never binds a port and {@code /login} can never answer with a
 * half-configured instance. An {@code ApplicationRunner} would run only after the
 * server is already serving.
 *
 * <p>It runs for every profile except the explicit development profiles listed in
 * {@link StartupGuardProfiles}, so a missing key fails closed instead of silently
 * disabling encryption.
 */
public class PiiEncryptionProductionGuard implements EnvironmentPostProcessor, Ordered {

    static final String ENCRYPTION_KEY_PROPERTY = "app.encryption.key";
    static final int AES_256_KEY_BYTES = 32;

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        validate(environment);
    }

    @Override
    public int getOrder() {
        // After ConfigDataEnvironmentPostProcessor, so profile files and env vars are loaded.
        return Ordered.LOWEST_PRECEDENCE;
    }

    void validate(Environment environment) {
        if (!StartupGuardProfiles.enforced(environment)) {
            return;
        }
        String encryptionKey;
        try {
            encryptionKey = environment.getProperty(ENCRYPTION_KEY_PROPERTY);
        } catch (IllegalArgumentException e) {
            // The property file points at APP_ENCRYPTION_KEY and the variable is unset.
            throw new IllegalStateException(
                    "APP_ENCRYPTION_KEY is required; refusing to start with PII encryption disabled", e);
        }
        if (encryptionKey == null || encryptionKey.isBlank()) {
            throw new IllegalStateException(
                    "APP_ENCRYPTION_KEY is required; refusing to start with PII encryption disabled");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encryptionKey.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "APP_ENCRYPTION_KEY must be valid Base64; generate one with: openssl rand -base64 32", e);
        }
        if (keyBytes.length != AES_256_KEY_BYTES) {
            throw new IllegalStateException(
                    "APP_ENCRYPTION_KEY must decode to exactly 32 bytes (AES-256); "
                            + "generate one with: openssl rand -base64 32");
        }
    }
}
