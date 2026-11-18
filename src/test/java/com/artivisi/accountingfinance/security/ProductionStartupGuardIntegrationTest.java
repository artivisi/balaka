package com.artivisi.accountingfinance.security;

import com.artivisi.accountingfinance.AccountingFinanceApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
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

    /** Obviously not a secret: the bootstrap path only checks the length. */
    private static final String CONTAINER_ADMIN_PASSWORD = "bootstrap-admin-password";

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
    @DisplayName("a bootstrap profile without the exit flag is still guarded")
    void bootstrapProfileWithoutExitFlagIsGuarded() {
        assertThatThrownBy(() -> new SpringApplicationBuilder(AccountingFinanceApplication.class)
                .profiles("bootstrap")
                .run("--app.setup.enabled=false",
                        "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "--spring.datasource.username=" + POSTGRES.getUsername(),
                        "--spring.datasource.password=" + POSTGRES.getPassword(),
                        "--spring.main.web-application-type=none"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_ENCRYPTION_KEY is required");
    }

    @Test
    @DisplayName("the container bootstrap replaces the admin and the production profile then starts")
    void containerBootstrapMakesTheProductionProfileStartable() {
        new SpringApplicationBuilder(AccountingFinanceApplication.class)
                .profiles("bootstrap")
                .run("--app.bootstrap.exit-after-migrate=true",
                        "--app.storage.documents.path=target/test-documents",
                        "--app.bootstrap.admin.username=ops",
                        "--app.bootstrap.admin.password=" + CONTAINER_ADMIN_PASSWORD,
                        "--app.encryption.key=" + VALID_KEY,
                        "--app.setup.enabled=false",
                        "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "--spring.datasource.username=" + POSTGRES.getUsername(),
                        "--spring.datasource.password=" + POSTGRES.getPassword(),
                        "--spring.jpa.hibernate.ddl-auto=validate",
                        "--spring.main.web-application-type=none");

        JdbcTemplate jdbc = new JdbcTemplate(
                new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ?",
                    Integer.class, DefaultAdminProductionGuard.SEEDED_ADMIN_ID)).isZero();
            String hash = jdbc.queryForObject("SELECT password FROM users WHERE username = 'ops'", String.class);
            assertThat(hash).startsWith("$2");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM user_roles ur JOIN users u ON u.id = ur.id_user"
                    + " WHERE u.username = 'ops' AND ur.role = 'ADMIN'", Integer.class)).isEqualTo(1);

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(AccountingFinanceApplication.class)
                .profiles("production")
                .run("--app.encryption.key=" + VALID_KEY,
                        "--app.setup.enabled=false",
                        "--app.storage.documents.path=target/test-documents",
                        "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "--spring.datasource.username=" + POSTGRES.getUsername(),
                        "--spring.datasource.password=" + POSTGRES.getPassword(),
                        "--spring.jpa.hibernate.ddl-auto=validate",
                        "--server.port=0")) {
            assertThat(context.isActive()).isTrue();
        }
    }

    @Test
    @DisplayName("the seeded admin stops startup even with a valid key")
    void seededAdminStopsStartup() {
        // The container test above deletes the seeded admin, and this class shares one
        // database; put the seed back so this test stands on its own.
        JdbcTemplate jdbc = new JdbcTemplate(
                new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        jdbc.update("INSERT INTO users (id, username, password, full_name, email, active)"
                + " VALUES (?, 'admin', '$2a$10$mMan.18CFTqJA/FVpkJr3OgCD0uTuhF9Enjf99QHm9tWPJH.nCj5S',"
                + " 'Administrator', 'admin@artivisi.com', TRUE) ON CONFLICT (id) DO NOTHING",
                DefaultAdminProductionGuard.SEEDED_ADMIN_ID);

        assertThatThrownBy(() -> new SpringApplicationBuilder(AccountingFinanceApplication.class)
                .profiles("production")
                .run("--app.encryption.key=" + VALID_KEY,
                        "--app.setup.enabled=false",
                        "--app.storage.documents.path=target/test-documents",
                        "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "--spring.datasource.username=" + POSTGRES.getUsername(),
                        "--spring.datasource.password=" + POSTGRES.getPassword(),
                        "--spring.jpa.hibernate.ddl-auto=validate",
                        "--server.port=0"))
                .hasStackTraceContaining("Default admin/admin credentials are present");
    }
}
