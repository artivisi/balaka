package com.artivisi.accountingfinance.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V008 on a database that already holds a chart of accounts: seed accounts are added under the existing
 * parents, existing accounts stay unclassified, and existing fiscal adjustments keep counting.
 * On an empty chart nothing is inserted: the seed pack supplies the accounts.
 */
@DisplayName("V008 fiscal treatment migration")
class V008FiscalTreatmentMigrationTest {

    @Test
    @DisplayName("Existing chart: seed accounts added, existing rows untouched, adjustments stay confirmed")
    void migratesExistingChart() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = dataSource(postgres);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            flyway(dataSource, "7").migrate();

            account(jdbc, "2.1", "Liabilitas Jangka Pendek", "LIABILITY", "CREDIT", 2);
            account(jdbc, "3.2", "Laba", "EQUITY", "CREDIT", 2);
            account(jdbc, "5", "BEBAN", "EXPENSE", "DEBIT", 1);
            account(jdbc, "5.1.01", "Beban Gaji", "EXPENSE", "DEBIT", 3);
            // Owner already created this one: kept, not overwritten
            account(jdbc, "5.8.04", "Denda Milik Pemilik", "EXPENSE", "DEBIT", 3);
            jdbc.update("INSERT INTO fiscal_adjustments (year, description, adjustment_category, adjustment_direction, amount) "
                    + "VALUES (2025, 'Denda', 'PERMANENT', 'POSITIVE', 1000)");

            flyway(dataSource, "8").migrate();

            Map<String, Object> pph26 = jdbc.queryForMap("""
                    SELECT c.account_name, c.account_type, c.normal_balance, c.level, c.is_header, c.fiscal_treatment, p.account_code AS parent
                    FROM chart_of_accounts c JOIN chart_of_accounts p ON p.id = c.id_parent WHERE c.account_code = '2.1.25'""");
            assertThat(pph26).containsEntry("account_name", "Hutang PPh 26").containsEntry("account_type", "LIABILITY")
                    .containsEntry("normal_balance", "CREDIT").containsEntry("level", 3).containsEntry("is_header", false)
                    .containsEntry("parent", "2.1").containsEntry("fiscal_treatment", null);
            assertThat(treatment(jdbc, "5.8.01")).isEqualTo("DEDUCTIBLE");
            assertThat(treatment(jdbc, "5.8.02")).isEqualTo("DEDUCTIBLE");
            assertThat(treatment(jdbc, "5.8.03")).isEqualTo("CONDITIONAL");
            assertThat(jdbc.queryForObject("SELECT p.account_code FROM chart_of_accounts c JOIN chart_of_accounts p ON p.id = c.id_parent "
                    + "WHERE c.account_code = '5.8.03'", String.class)).isEqualTo("5.8");
            assertThat(jdbc.queryForObject("SELECT is_header FROM chart_of_accounts WHERE account_code = '5.8'", Boolean.class)).isTrue();
            assertThat(jdbc.queryForObject("SELECT normal_balance FROM chart_of_accounts WHERE account_code = '3.2.03'", String.class)).isEqualTo("DEBIT");
            assertThat(jdbc.queryForObject("SELECT account_code FROM chart_of_accounts WHERE account_code = '2.1.40'", String.class)).isEqualTo("2.1.40");

            assertThat(treatment(jdbc, "5.1.01")).isNull();
            assertThat(jdbc.queryForObject("SELECT account_name FROM chart_of_accounts WHERE account_code = '5.8.04'", String.class))
                    .isEqualTo("Denda Milik Pemilik");
            assertThat(treatment(jdbc, "5.8.04")).isNull();

            Map<String, Object> adjustment = jdbc.queryForMap("SELECT status, source, amount FROM fiscal_adjustments WHERE year = 2025");
            assertThat(adjustment).containsEntry("status", "CONFIRMED").containsEntry("source", "MANUAL");
            assertThat((BigDecimal) adjustment.get("amount")).isEqualByComparingTo("1000");

            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tags t JOIN tag_types y ON y.id = t.id_tag_type "
                    + "WHERE t.code = 'NATURA' AND y.code = 'NATURA'", Integer.class)).isEqualTo(1);
            assertThatThrownBy(() -> jdbc.update("UPDATE chart_of_accounts SET fiscal_treatment = 'DEDUCTIBLE' WHERE account_code = '2.1'"))
                    .hasMessageContaining("chk_coa_fiscal_treatment_expense");
        }
    }

    @Test
    @DisplayName("Reserved code used with another account type fails the migration")
    void conflictingCodeFailsLoudly() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = dataSource(postgres);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            flyway(dataSource, "7").migrate();
            account(jdbc, "2.1.25", "Kas Kecil", "ASSET", "DEBIT", 3);

            assertThatThrownBy(() -> flyway(dataSource, "8").migrate())
                    .isInstanceOf(FlywayException.class)
                    .hasMessageContaining("2.1.25 (Kas Kecil, ASSET)");
        }
    }

    @Test
    @DisplayName("Existing chart without a required parent fails the migration")
    void missingParentFailsLoudly() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = dataSource(postgres);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            flyway(dataSource, "7").migrate();
            account(jdbc, "2.1", "Liabilitas", "LIABILITY", "CREDIT", 2);
            account(jdbc, "5", "BEBAN", "EXPENSE", "DEBIT", 1);

            assertThatThrownBy(() -> flyway(dataSource, "8").migrate())
                    .isInstanceOf(FlywayException.class)
                    .hasMessageContaining("parent accounts for the seed accounts are missing: 3.2");
        }
    }

    @Test
    @DisplayName("Empty chart: no accounts inserted")
    void emptyChartInsertsNothing() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = dataSource(postgres);
            flyway(dataSource, "8").migrate();
            assertThat(new JdbcTemplate(dataSource).queryForObject("SELECT COUNT(*) FROM chart_of_accounts", Integer.class)).isZero();
        }
    }

    private static DriverManagerDataSource dataSource(PostgreSQLContainer<?> postgres) {
        return new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static Flyway flyway(DriverManagerDataSource dataSource, String target) {
        return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(target).load();
    }

    private static void account(JdbcTemplate jdbc, String code, String name, String type, String balance, int level) {
        jdbc.update("INSERT INTO chart_of_accounts (id, account_code, account_name, account_type, normal_balance, level) "
                + "VALUES (?, ?, ?, ?, ?, ?)", UUID.randomUUID(), code, name, type, balance, level);
    }

    private static String treatment(JdbcTemplate jdbc, String code) {
        return jdbc.queryForObject("SELECT fiscal_treatment FROM chart_of_accounts WHERE account_code = ?", String.class, code);
    }
}
