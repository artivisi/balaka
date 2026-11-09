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

/** V014 on a production-shaped chart, on an empty chart, and with 2.1.50 taken. */
@DisplayName("V014 period close checks migration")
class V014PeriodCloseChecksMigrationTest {

    @Test
    @DisplayName("Existing chart: 1.1.26 becomes TAX_CREDIT, 2.1.50 added under 2.1, alert rule seeded, role constraint enforced")
    void migratesExistingChart() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = dataSource(postgres);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            flyway(dataSource, "13").migrate();
            insertAccount(jdbc, "2.1", "Liabilitas Jangka Pendek", "LIABILITY", "CREDIT", 2, true);
            insertAccount(jdbc, "1.1.26", "Kredit Pajak PPh 23", "ASSET", "DEBIT", 3, false);
            insertAccount(jdbc, "1.1.25", "PPN Masukan", "ASSET", "DEBIT", 3, false);

            flyway(dataSource, "14").migrate();

            assertThat(role(jdbc, "1.1.26")).isEqualTo("TAX_CREDIT");
            assertThat(role(jdbc, "1.1.25")).isNull();
            Map<String, Object> shareholder = jdbc.queryForMap("""
                    SELECT c.account_name, c.account_type, c.normal_balance, c.level, c.close_check_role, p.account_code AS parent
                    FROM chart_of_accounts c JOIN chart_of_accounts p ON p.id = c.id_parent WHERE c.account_code = '2.1.50'""");
            assertThat(shareholder).containsEntry("account_name", "Rekening Koran Pemegang Saham/Direksi")
                    .containsEntry("account_type", "LIABILITY").containsEntry("normal_balance", "CREDIT")
                    .containsEntry("level", 3).containsEntry("close_check_role", "SHAREHOLDER_CURRENT")
                    .containsEntry("parent", "2.1");
            assertThat(jdbc.queryForObject("SELECT threshold FROM alert_rules WHERE alert_type = 'LIABILITY_UNMOVED'", String.class))
                    .isEqualTo("6.00");

            assertThatThrownBy(() -> jdbc.update("UPDATE chart_of_accounts SET close_check_role = 'TAX_CREDIT' WHERE account_code = '2.1.50'"))
                    .hasMessageContaining("chk_coa_close_check_role");
            assertThatThrownBy(() -> jdbc.update("UPDATE chart_of_accounts SET close_check_role = 'CLEARING' WHERE account_code = '2.1'"))
                    .hasMessageContaining("chk_coa_close_check_role");
        }
    }

    @Test
    @DisplayName("Empty chart: no account inserted, the alert rule still seeded")
    void emptyChart() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = dataSource(postgres);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            flyway(dataSource, "14").migrate();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chart_of_accounts", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM alert_rules WHERE alert_type = 'LIABILITY_UNMOVED'", Integer.class)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("2.1.50 used by another account fails the migration")
    void codeTaken() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = dataSource(postgres);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            flyway(dataSource, "13").migrate();
            insertAccount(jdbc, "2.1", "Liabilitas Jangka Pendek", "LIABILITY", "CREDIT", 2, true);
            insertAccount(jdbc, "2.1.50", "Hutang Lain", "LIABILITY", "CREDIT", 3, false);
            assertThatThrownBy(() -> flyway(dataSource, "14").migrate())
                    .hasMessageContaining("account code 2.1.50 is used by \"Hutang Lain\"");
        }
    }

    private String role(JdbcTemplate jdbc, String code) {
        return jdbc.queryForObject("SELECT close_check_role FROM chart_of_accounts WHERE account_code = ?", String.class, code);
    }

    private void insertAccount(JdbcTemplate jdbc, String code, String name, String type, String balance, int level, boolean header) {
        jdbc.update("""
                INSERT INTO chart_of_accounts (id, account_code, account_name, account_type, normal_balance, level, is_header, active, is_permanent)
                VALUES (?, ?, ?, ?, ?, ?, ?, TRUE, TRUE)""", UUID.randomUUID(), code, name, type, balance, level, header);
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
