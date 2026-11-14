package com.artivisi.accountingfinance.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PiiEncryptionProductionGuardTest {

    private final PiiEncryptionProductionGuard guard = new PiiEncryptionProductionGuard();

    private static MockEnvironment environment(String key, String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        if (key != null) {
            environment.setProperty(PiiEncryptionProductionGuard.ENCRYPTION_KEY_PROPERTY, key);
        }
        return environment;
    }

    private static String validKey() {
        return Base64.getEncoder().encodeToString(new byte[PiiEncryptionProductionGuard.AES_256_KEY_BYTES]);
    }

    @Test
    void rejectsBlankKey() {
        var error = assertThrows(IllegalStateException.class,
                () -> guard.validate(environment("  ", "production")));

        assertTrue(error.getMessage().contains("APP_ENCRYPTION_KEY is required"));
    }

    @Test
    void rejectsMissingKey() {
        assertThrows(IllegalStateException.class, () -> guard.validate(environment(null, "production")));
    }

    @Test
    void rejectsMissingKeyWithNoProfileAtAll() {
        // Fail closed: forgetting --spring.profiles.active must not disable the guard.
        assertThrows(IllegalStateException.class, () -> guard.validate(environment(null)));
    }

    @Test
    void rejectsNonBase64Key() {
        var error = assertThrows(IllegalStateException.class,
                () -> guard.validate(environment("not-base64!!", "production")));

        assertTrue(error.getMessage().contains("valid Base64"));
    }

    @Test
    void rejectsWrongLengthKey() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        var error = assertThrows(IllegalStateException.class,
                () -> guard.validate(environment(shortKey, "production")));

        assertTrue(error.getMessage().contains("exactly 32 bytes"));
    }

    @Test
    void acceptsValidKey() {
        assertDoesNotThrow(() -> guard.validate(environment(validKey(), "production")));
    }

    @Test
    void acceptsKeyWithSurroundingWhitespace() {
        assertDoesNotThrow(() -> guard.validate(environment("  " + validKey() + "\n", "production")));
    }

    @Test
    void allowsBlankKeyInDevelopmentProfiles() {
        for (String profile : new String[]{"dev", "test", "functional"}) {
            assertDoesNotThrow(() -> guard.validate(environment("", profile)), profile);
        }
    }

    @Test
    void enforcesTheKeyInBootstrapUnlessTheRunExitsAfterMigrating() {
        // The profile name alone no longer buys an exemption: a bootstrap that is
        // not the one-shot migration run would otherwise serve without a key.
        assertThrows(IllegalStateException.class, () -> guard.validate(environment("", "bootstrap")));

        MockEnvironment exitingRun = environment("", "bootstrap");
        exitingRun.setProperty("app.bootstrap.exit-after-migrate", "true");
        assertDoesNotThrow(() -> guard.validate(exitingRun));
    }

    @Test
    void enforcesForAnyOtherProfileName() {
        // A profile called production-eu is still a deployment, not a development run.
        assertThrows(IllegalStateException.class, () -> guard.validate(environment(null, "production-eu")));
    }
}
