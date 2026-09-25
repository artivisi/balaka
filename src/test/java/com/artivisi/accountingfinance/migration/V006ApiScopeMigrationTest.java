package com.artivisi.accountingfinance.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V006 widens the scope columns and backfills the new scopes onto tokens issued before it:
 * device-flow tokens get the scopes covering calls they could already make, API clients only
 * the template/account read scopes their posting flows need, and client_credentials tokens
 * stay as issued.
 */
@DisplayName("V006 API scope migration")
class V006ApiScopeMigrationTest {

    private static final String OLD_DEVICE_SCOPES = "drafts:create,drafts:approve,drafts:read,analysis:read,"
            + "analysis:write,transactions:post,data:import,bills:read,bills:create,bills:approve,tax-export:read,"
            + "accounts:read,accounts:write,assets:read,assets:write,tax-filings:read,tax-filings:write";

    private static PostgreSQLContainer<?> postgres;
    private static JdbcTemplate jdbc;
    private static DriverManagerDataSource dataSource;

    @BeforeAll
    static void migrateToV005AndSeedTokens() {
        postgres = new PostgreSQLContainer<>("postgres:18-alpine");
        postgres.start();
        dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(dataSource);

        flyway("5").migrate();

        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, username, password, full_name) VALUES (?, 'v006-user', 'x', 'V006')", userId);
        jdbc.update("INSERT INTO api_clients (client_id, client_secret_hash, name, scopes, id_user) VALUES "
                + "('poster-1', 'x', 'poster', 'transactions:post', ?), "
                + "('drafter-1', 'x', 'drafter', 'drafts:create,accounts:read', ?), "
                + "('reader-1', 'x', 'reader', 'analysis:read', ?)", userId, userId, userId);
        insertToken(userId, "device-all", OLD_DEVICE_SCOPES);
        insertToken(userId, "device-tax", "tax-export:read");
        insertToken(userId, "device-analysis", "analysis:read");
        insertToken(userId, "poster-1", "transactions:post");

        flyway("6").migrate();
    }

    @AfterAll
    static void stop() {
        postgres.stop();
    }

    @Test
    @DisplayName("Scope columns hold the full scope list")
    void scopeColumnsWidened() {
        List<Integer> lengths = jdbc.queryForList("SELECT character_maximum_length FROM information_schema.columns "
                + "WHERE column_name = 'scopes' AND table_name IN ('device_tokens', 'api_clients')", Integer.class);
        assertThat(lengths).containsExactly(2000, 2000);
    }

    @Test
    @DisplayName("Device-flow tokens gain the new scopes covering calls they could already make")
    void deviceTokensBackfilled() {
        assertThat(tokenScopes("device-all")).containsExactlyInAnyOrderElementsOf(concat(OLD_DEVICE_SCOPES,
                "payroll:write,tax-export:write,templates:read,templates:write,bank-recon:read,bank-recon:write"));
        assertThat(tokenScopes("device-tax"))
                .containsExactlyInAnyOrder("tax-export:read", "payroll:write", "tax-export:write");
        assertThat(tokenScopes("device-analysis")).containsExactly("analysis:read");
    }

    @Test
    @DisplayName("API clients gain only template/account reads; their issued tokens are unchanged")
    void apiClientsNotGrantedWrites() {
        assertThat(clientScopes("poster-1"))
                .containsExactlyInAnyOrder("transactions:post", "templates:read", "accounts:read");
        assertThat(clientScopes("drafter-1"))
                .containsExactlyInAnyOrder("drafts:create", "accounts:read", "templates:read");
        assertThat(clientScopes("reader-1")).containsExactly("analysis:read");
        assertThat(tokenScopes("poster-1")).containsExactly("transactions:post");
    }

    private static Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(target)
                .load();
    }

    private static void insertToken(UUID userId, String clientId, String scopes) {
        jdbc.update("INSERT INTO device_tokens (id_user, token_hash, client_id, scopes) VALUES (?, 'x', ?, ?)",
                userId, clientId, scopes);
    }

    private static List<String> tokenScopes(String clientId) {
        return List.of(jdbc.queryForObject("SELECT scopes FROM device_tokens WHERE client_id = ?", String.class,
                clientId).split(","));
    }

    private static List<String> clientScopes(String clientId) {
        return List.of(jdbc.queryForObject("SELECT scopes FROM api_clients WHERE client_id = ?", String.class,
                clientId).split(","));
    }

    private static List<String> concat(String a, String b) {
        return List.of((a + "," + b).split(","));
    }
}
