package com.artivisi.accountingfinance.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Replaces the seeded {@code admin} account when the container has no Ansible to do
 * it, so a fresh database can reach a state where
 * {@code DefaultAdminProductionGuard} lets the production profile start.
 *
 * <p>It only runs in the one-shot bootstrap mode
 * ({@code --spring.profiles.active=bootstrap --app.bootstrap.exit-after-migrate=true})
 * and only when {@code APP_BOOTSTRAP_ADMIN_USERNAME} and
 * {@code APP_BOOTSTRAP_ADMIN_PASSWORD} are set. The deployment sequence is:
 *
 * <pre>
 * java -jar app.jar --spring.profiles.active=bootstrap \
 *   --app.bootstrap.exit-after-migrate=true \
 *   --app.bootstrap.admin.username=ops --app.bootstrap.admin.password=...
 * java -jar app.jar   # production, guards enforced, real admin in place
 * </pre>
 *
 * <p>The account it writes is a normal user row: a BCrypt hash, an ADMIN role, and
 * no knowledge of the seed. Rerunning it with the same username resets that
 * account's password and roles instead of failing, so a repeated bootstrap is safe.
 */
@Component
@ConditionalOnProperty(name = "app.bootstrap.admin.username")
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class ContainerAdminProvisioner implements ApplicationRunner {

    static final UUID SEEDED_ADMIN_ID = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    static final String USERNAME = "app.bootstrap.admin.username";
    static final String PASSWORD = "app.bootstrap.admin.password";
    static final String FULL_NAME = "app.bootstrap.admin.full-name";
    static final String EMAIL = "app.bootstrap.admin.email";
    static final int MIN_PASSWORD_LENGTH = 12;

    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;

    public ContainerAdminProvisioner(JdbcTemplate jdbcTemplate, Environment environment) {
        this.jdbcTemplate = jdbcTemplate;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!environment.acceptsProfiles(Profiles.of("bootstrap"))) {
            throw new IllegalStateException(
                    "app.bootstrap.admin.username is only honoured in the bootstrap profile;"
                            + " remove it from the production environment");
        }

        String username = environment.getProperty(USERNAME, "").trim();
        String password = environment.getProperty(PASSWORD, "");
        String fullName = environment.getProperty(FULL_NAME, "Administrator");
        String email = environment.getProperty(EMAIL);

        if ("admin".equalsIgnoreCase(username)) {
            throw new IllegalStateException(
                    "app.bootstrap.admin.username must not be the seeded 'admin'; the guard exists"
                            + " so a published default password cannot reach production");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "app.bootstrap.admin.password must be at least " + MIN_PASSWORD_LENGTH
                            + " characters; refusing to create an admin with a weak password");
        }

        String hash = new BCryptPasswordEncoder().encode(password);

        // The seeded row and its roles go first: its presence is what the production
        // guard rejects, and leaving it behind would keep the deployment unstartable.
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", SEEDED_ADMIN_ID);

        Integer existing = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE username = ?", Integer.class, username);
        UUID userId;
        if (existing != null && existing > 0) {
            userId = jdbcTemplate.queryForObject(
                    "SELECT id FROM users WHERE username = ?", UUID.class, username);
            jdbcTemplate.update(
                    "UPDATE users SET password = ?, full_name = ?, email = COALESCE(?, email),"
                            + " active = TRUE, updated_at = NOW(), updated_by = 'bootstrap' WHERE id = ?",
                    hash, fullName, email, userId);
            jdbcTemplate.update("DELETE FROM user_roles WHERE id_user = ?", userId);
        } else {
            userId = UUID.randomUUID();
            jdbcTemplate.update(
                    "INSERT INTO users (id, username, password, full_name, email, active, created_by)"
                            + " VALUES (?, ?, ?, ?, ?, TRUE, 'bootstrap')",
                    userId, username, hash, fullName, email);
        }
        jdbcTemplate.update(
                "INSERT INTO user_roles (id, id_user, role, created_by) VALUES (?, ?, 'ADMIN', 'bootstrap')",
                UUID.randomUUID(), userId);

        log.info("Bootstrap created admin account '{}' and removed the seeded admin", username);
    }
}
