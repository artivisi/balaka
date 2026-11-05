package com.artivisi.accountingfinance.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** V010 on a production-shaped database; an empty database gets no account or template: the seed pack supplies them. */
@DisplayName("V010 sales tax chain migration")
class V010SalesTaxChainMigrationTest {

    @Test
    @DisplayName("Existing data: receipts get zero withheld, account and template added, device tokens widened, API clients untouched")
    void migratesExistingData() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = dataSource(postgres);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            flyway(dataSource, "9").migrate();
            seedChart(jdbc);
            UUID payment = seedPayment(jdbc);
            insertToken(jdbc, "device", "tax-export:read,transactions:post");
            insertToken(jdbc, "narrow", "drafts:read");

            flyway(dataSource, "10").migrate();

            assertThat(jdbc.queryForObject("SELECT pph23_withheld FROM invoice_payments WHERE id = ?", BigDecimal.class, payment))
                    .isEqualByComparingTo("0");
            assertThat(jdbc.queryForObject("SELECT account_code FROM chart_of_accounts WHERE account_name = 'Kredit Pajak PPh 23'", String.class))
                    .isEqualTo("1.1.26");
            assertThat(jdbc.queryForList("""
                    SELECT l.position || ':' || l.account_hint || ':' || l.formula FROM journal_template_lines l
                    JOIN journal_templates t ON t.id = l.id_journal_template
                    WHERE t.template_name = 'Pemotongan PPh 23 oleh Pelanggan' ORDER BY l.line_order""", String.class))
                    .isEqualTo(List.of("DEBIT:KREDIT_PPH23:amount", "CREDIT:PIUTANG:amount"));
            assertThat(jdbc.queryForObject("SELECT scopes FROM device_tokens WHERE client_id = 'device'", String.class))
                    .isEqualTo("tax-export:read,transactions:post,sales-tax-chain:read,sales-tax-chain:write");
            assertThat(jdbc.queryForObject("SELECT scopes FROM device_tokens WHERE client_id = 'narrow'", String.class))
                    .isEqualTo("drafts:read");
            assertThatThrownBy(() -> jdbc.update("UPDATE invoice_payments SET pph23_withheld = amount + 1 WHERE id = ?", payment))
                    .hasMessageContaining("chk_invoice_payment_pph23");
        }
    }

    @Test
    @DisplayName("An existing account with that name is kept under its own code")
    void keepsExistingAccount() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = dataSource(postgres);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            flyway(dataSource, "9").migrate();
            seedChart(jdbc);
            jdbc.update("""
                    INSERT INTO chart_of_accounts (id, account_code, account_name, account_type, normal_balance, level, is_header, active, is_permanent)
                    VALUES (?, '1.1.77', 'Kredit Pajak PPh 23', 'ASSET', 'DEBIT', 3, FALSE, TRUE, TRUE)""", UUID.randomUUID());

            flyway(dataSource, "10").migrate();

            assertThat(jdbc.queryForList("SELECT account_code FROM chart_of_accounts WHERE account_name = 'Kredit Pajak PPh 23'", String.class))
                    .containsExactly("1.1.77");
        }
    }

    @Test
    @DisplayName("Code 1.1.26 used by another account, or the template name taken, fails the migration")
    void conflictsFail() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = dataSource(postgres);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            flyway(dataSource, "9").migrate();
            seedChart(jdbc);
            jdbc.update("""
                    INSERT INTO chart_of_accounts (id, account_code, account_name, account_type, normal_balance, level, is_header, active, is_permanent)
                    VALUES (?, '1.1.26', 'Akun Lain', 'ASSET', 'DEBIT', 3, FALSE, TRUE, TRUE)""", UUID.randomUUID());

            assertThatThrownBy(() -> flyway(dataSource, "10").migrate()).hasMessageContaining("1.1.26 is used by");
        }
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = dataSource(postgres);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            flyway(dataSource, "9").migrate();
            seedChart(jdbc);
            jdbc.update("""
                    INSERT INTO journal_templates (id, template_name, category, cash_flow_category)
                    VALUES (?, 'Pemotongan PPh 23 oleh Pelanggan', 'RECEIPT', 'OPERATING')""", UUID.randomUUID());

            assertThatThrownBy(() -> flyway(dataSource, "10").migrate()).hasMessageContaining("already in use");
        }
    }

    @Test
    @DisplayName("Empty database: no account or template inserted")
    void emptyDatabase() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = dataSource(postgres);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            flyway(dataSource, "10").migrate();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chart_of_accounts", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM journal_templates WHERE template_name = 'Pemotongan PPh 23 oleh Pelanggan'", Integer.class)).isZero();
        }
    }

    private void seedChart(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO chart_of_accounts (id, account_code, account_name, account_type, normal_balance, level, is_header, active, is_permanent)
                VALUES (?, '1.1', 'Aset Lancar', 'ASSET', 'DEBIT', 2, TRUE, TRUE, TRUE)""", UUID.randomUUID());
    }

    private UUID seedPayment(JdbcTemplate jdbc) {
        UUID client = UUID.randomUUID();
        UUID invoice = UUID.randomUUID();
        UUID payment = UUID.randomUUID();
        jdbc.update("INSERT INTO clients (id, code, name) VALUES (?, 'C-1', 'Klien')", client);
        jdbc.update("""
                INSERT INTO invoices (id, invoice_number, id_client, invoice_date, due_date, amount, status)
                VALUES (?, 'INV-1', ?, '2026-01-01', '2026-02-01', 1000, 'PARTIAL')""", invoice, client);
        jdbc.update("""
                INSERT INTO invoice_payments (id, id_invoice, payment_date, amount, payment_method)
                VALUES (?, ?, '2026-01-10', 500, 'TRANSFER')""", payment, invoice);
        return payment;
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
