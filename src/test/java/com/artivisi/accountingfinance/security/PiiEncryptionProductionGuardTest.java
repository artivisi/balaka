package com.artivisi.accountingfinance.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PiiEncryptionProductionGuardTest {

    private static MockEnvironment environment(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return environment;
    }

    private static String validKey() {
        return Base64.getEncoder().encodeToString(new byte[PiiEncryptionProductionGuard.AES_256_KEY_BYTES]);
    }

    @Test
    void rejectsBlankKeyInProduction() {
        var guard = new PiiEncryptionProductionGuard(environment("production"), "  ");

        var error = assertThrows(IllegalStateException.class, guard::validate);

        assertTrue(error.getMessage().contains("APP_ENCRYPTION_KEY is required"));
    }

    @Test
    void rejectsMissingKeyInProduction() {
        var guard = new PiiEncryptionProductionGuard(environment("production"), null);

        assertThrows(IllegalStateException.class, guard::validate);
    }

    @Test
    void rejectsNonBase64KeyInProduction() {
        var guard = new PiiEncryptionProductionGuard(environment("production"), "not-base64!!");

        var error = assertThrows(IllegalStateException.class, guard::validate);

        assertTrue(error.getMessage().contains("valid Base64"));
    }

    @Test
    void rejectsWrongLengthKeyInProduction() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        var guard = new PiiEncryptionProductionGuard(environment("production"), shortKey);

        var error = assertThrows(IllegalStateException.class, guard::validate);

        assertTrue(error.getMessage().contains("exactly 32 bytes"));
    }

    @Test
    void acceptsValidKeyInProduction() {
        var guard = new PiiEncryptionProductionGuard(environment("production"), validKey());

        assertDoesNotThrow(guard::validate);
    }

    @Test
    void acceptsKeyWithSurroundingWhitespaceInProduction() {
        var guard = new PiiEncryptionProductionGuard(environment("production"), "  " + validKey() + "\n");

        assertDoesNotThrow(guard::validate);
    }

    @Test
    void allowsBlankKeyOutsideProduction() {
        var guard = new PiiEncryptionProductionGuard(environment("test"), "");

        assertDoesNotThrow(guard::validate);
    }

    @Test
    void doesNotMatchSimilarlyNamedProfiles() {
        var guard = new PiiEncryptionProductionGuard(environment("production-eu"), "");

        assertDoesNotThrow(guard::validate);
    }
}
