package com.artivisi.accountingfinance.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.Base64;

@Component
public class PiiEncryptionProductionGuard implements ApplicationRunner {

    static final String PRODUCTION_PROFILE = "production";
    static final int AES_256_KEY_BYTES = 32;

    private final Environment environment;
    private final String encryptionKey;

    public PiiEncryptionProductionGuard(Environment environment,
                                        @Value("${app.encryption.key:}") String encryptionKey) {
        this.environment = environment;
        this.encryptionKey = encryptionKey;
    }

    @Override
    public void run(ApplicationArguments args) {
        validate();
    }

    void validate() {
        if (!environment.acceptsProfiles(Profiles.of(PRODUCTION_PROFILE))) {
            return;
        }
        if (encryptionKey == null || encryptionKey.isBlank()) {
            throw new IllegalStateException(
                    "APP_ENCRYPTION_KEY is required in the production profile; refusing to start with PII encryption disabled");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encryptionKey.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "APP_ENCRYPTION_KEY must be valid Base64 in the production profile", e);
        }
        if (keyBytes.length != AES_256_KEY_BYTES) {
            throw new IllegalStateException(
                    "APP_ENCRYPTION_KEY must decode to exactly 32 bytes (AES-256) in the production profile");
        }
    }
}
