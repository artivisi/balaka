package com.artivisi.accountingfinance.security;

import com.artivisi.accountingfinance.AccountingFinanceApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Boots the real application so the guards are exercised the way a deployment runs
 * them: no mocks, real Flyway migrations, real servlet container.
 */
@Testcontainers
@DisplayName("Production startup guards")
class ProductionStartupGuardIntegrationTest {

    /** 32 zero bytes in Base64: structurally valid, obviously not a secret. */
    private static final String VALID_KEY =
            java.util.Base64.getEncoder().encodeToString(new byte[PiiEncryptionProductionGuard.AES_256_KEY_BYTES]);

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine");

    @Test
    @DisplayName("a deployment with a malformed encryption key fails before the context starts")
    void malformedEncryptionKeyStopsStartup() {
        assertThatThrownBy(() -> new SpringApplicationBuilder(AccountingFinanceApplication.class)
                .profiles("production")
                .run("--app.encryption.key=not-base64!!",
                        "--app.setup.enabled=false",
                        "--spring.main.web-application-type=none"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_ENCRYPTION_KEY must be valid Base64");
    }

    @Test
    @DisplayName("a deployment without any encryption key fails before the context starts")
    void missingEncryptionKeyStopsStartup() {
        assertThatThrownBy(() -> new SpringApplicationBuilder(AccountingFinanceApplication.class)
                .profiles("production")
                .run("--app.encryption.key=",
                        "--app.setup.enabled=false",
                        "--spring.main.web-application-type=none"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_ENCRYPTION_KEY is required");
    }

    @Test
    @DisplayName("the seeded admin stops startup even with a valid key")
    void seededAdminStopsStartup() {
        assertThatThrownBy(() -> new SpringApplicationBuilder(AccountingFinanceApplication.class)
                .profiles("production")
                .run("--app.encryption.key=" + VALID_KEY,
                        "--app.setup.enabled=false",
                        "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "--spring.datasource.username=" + POSTGRES.getUsername(),
                        "--spring.datasource.password=" + POSTGRES.getPassword(),
                        "--spring.jpa.hibernate.ddl-auto=validate",
                        "--server.port=0"))
                .hasStackTraceContaining("Default admin/admin credentials are present");
    }
}
