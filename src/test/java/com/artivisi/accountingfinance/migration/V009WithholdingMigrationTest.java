package com.artivisi.accountingfinance.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V009 on a production-shaped database: existing vendors and accounts keep their data unclassified; deadline,
 * templates and token scopes are added. An empty database gets none of these: the seed pack supplies them.
 */
@DisplayName("V009 withholding migration")
class V009WithholdingMigrationTest {

    @Test
    @DisplayName("Existing data: vendors and accounts unclassified, plaintext kept, deadline and templates added, device tokens widened")
    void migratesExistingData() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = dataSource(postgres);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            flyway(dataSource, "8").migrate();

            seedExisting(jdbc);
            flyway(dataSource, "9").migrate();

            Map<String, Object> vendor = jdbc.queryForMap("SELECT npwp, nik, payee_type, default_withholding FROM vendors WHERE code = 'V-1'");
            assertThat(vendor).containsEntry("npwp", "0123456789012345").containsEntry("nik", "3174012345678901")
                    .containsEntry("payee_type", null).containsEntry("default_withholding", null);
            assertThat(jdbc.queryForObject("SELECT service_expense FROM chart_of_accounts WHERE account_code = '5.1'", Boolean.class)).isNull();

            assertThat(jdbc.queryForMap("SELECT name, due_day, use_last_day_of_month, active FROM tax_deadlines WHERE deadline_type = 'PPH_26_PAYMENT'"))
                    .containsEntry("name", "Setor PPh 26").containsEntry("due_day", 15)
                    .containsEntry("use_last_day_of_month", false).containsEntry("active", true);

            assertThat(jdbc.queryForList("""
                    SELECT l.position || ':' || l.account_hint || ':' || l.formula FROM journal_template_lines l
                    JOIN journal_templates t ON t.id = l.id_journal_template
                    WHERE t.template_name = 'Pembayaran dengan Pemotongan PPh' ORDER BY l.line_order""", String.class))
                    .containsExactly("DEBIT:BEBAN:settledAmount", "CREDIT:BANK:netAmount", "CREDIT:HUTANG_PPH:taxAmount");
            assertThat(jdbc.queryForObject("SELECT is_system FROM journal_templates WHERE template_name = 'Setor PPh Dipotong'", Boolean.class)).isFalse();

            assertThat(jdbc.queryForObject("SELECT scopes FROM device_tokens WHERE client_id = 'device'", String.class))
                    .isEqualTo("tax-export:read,transactions:post,withholding:read,withholding:write");
            assertThat(jdbc.queryForObject("SELECT scopes FROM device_tokens WHERE client_id = 'narrow'", String.class))
                    .isEqualTo("drafts:read");
        }
    }

    @Test
    @DisplayName("A template using a reserved name fails the migration")
    void reservedTemplateNameFails() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = dataSource(postgres);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            flyway(dataSource, "8").migrate();
            seedExisting(jdbc);
            jdbc.update("""
                    INSERT INTO journal_templates (id, template_name, category, cash_flow_category)
                    VALUES (?, 'Setor PPh Dipotong', 'PAYMENT', 'OPERATING')""", UUID.randomUUID());

            assertThatThrownBy(() -> flyway(dataSource, "9").migrate()).hasMessageContaining("reserved for withholding");
        }
    }

    @Test
    @DisplayName("Empty database: no deadline or templates inserted")
    void emptyDatabase() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = dataSource(postgres);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            flyway(dataSource, "9").migrate();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM journal_templates WHERE template_name = 'Setor PPh Dipotong'", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tax_deadlines WHERE deadline_type = 'PPH_26_PAYMENT'", Integer.class)).isZero();
        }
    }

    private void seedExisting(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO chart_of_accounts (id, account_code, account_name, account_type, normal_balance, level, is_header, active, is_permanent)
                VALUES (?, '5.1', 'Beban', 'EXPENSE', 'DEBIT', 2, FALSE, TRUE, FALSE)""", UUID.randomUUID());
        jdbc.update("INSERT INTO vendors (code, name, npwp, nik) VALUES ('V-1', 'Vendor', '0123456789012345', '3174012345678901')");
        Integer deadlines = jdbc.queryForObject("SELECT COUNT(*) FROM tax_deadlines", Integer.class);
        if (deadlines == 0) {
            jdbc.update("""
                    INSERT INTO tax_deadlines (name, deadline_type, due_day, use_last_day_of_month, reminder_days_before, active)
                    VALUES ('Setor PPh 23', 'PPH_23_PAYMENT', 15, FALSE, 7, TRUE)""");
        }
        insertToken(jdbc, "device", "tax-export:read,transactions:post");
        insertToken(jdbc, "narrow", "drafts:read");
    }

    private void insertToken(JdbcTemplate jdbc, String clientId, String scopes) {
        jdbc.update("""
                INSERT INTO device_tokens (id, token_hash, id_user, client_id, scopes, expires_at)
                VALUES (?, ?, (SELECT id FROM users LIMIT 1), ?, ?, NOW() + INTERVAL '1 day')""",
                UUID.randomUUID(), UUID.randomUUID().toString(), clientId, scopes);
    }

    private DriverManagerDataSource dataSource(PostgreSQLContainer<?> postgres) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        dataSource.setDriverClassName("org.postgresql.Driver");
        return dataSource;
    }

    private Flyway flyway(DriverManagerDataSource dataSource, String target) {
        return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(target).load();
    }
}
