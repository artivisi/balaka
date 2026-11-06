package com.artivisi.accountingfinance.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** V017 on a database holding the codes earlier releases stored. */
@DisplayName("V017 tax object code migration")
class V017TaxObjectCodeMigrationTest {

    /** Old code as stored by the app, mapped to the Coretax code with the same meaning. */
    private static final Map<String, String> REMAP = new LinkedHashMap<>();

    static {
        REMAP.put("24-104-04", "24-104-06");
        REMAP.put("24-104-05", "24-104-04");
        REMAP.put("24-104-06", "24-104-05");
        REMAP.put("24-104-07", "24-104-10");
        REMAP.put("24-104-10", "24-104-18");
        REMAP.put("24-104-14", "24-104-29");
        REMAP.put("24-104-16", "24-104-31");
        REMAP.put("24-104-21", "24-104-39");
        REMAP.put("24-104-22", "24-104-36");
        REMAP.put("24-104-34", "24-104-28");
        REMAP.put("24-100-09", "24-100-02");
        REMAP.put("28-409-01", "28-403-02");
        REMAP.put("28-409-07", "28-409-22");
        REMAP.put("28-409-08", "28-409-24");
        REMAP.put("28-409-09", "28-409-27");
        REMAP.put("27-100-99", "27-104-01");
        // Codes whose meaning did not change, and a value the app never offered
        REMAP.put("24-104-01", "24-104-01");
        REMAP.put("24-100-02", "24-100-02");
        REMAP.put("28-423-01", "28-423-01");
        REMAP.put("99-999-99", "99-999-99");
    }

    @Test
    @DisplayName("Every stored code maps once to its Coretax equivalent; unchanged and unknown codes stay")
    void remapsByMeaning() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = dataSource(postgres);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            flyway(dataSource, "16").migrate();
            UUID transaction = seedTransaction(jdbc);
            int n = 0;
            for (String old : REMAP.keySet()) {
                jdbc.update("INSERT INTO tax_transaction_details (id_transaction, tax_object_code) VALUES (?, ?)",
                        transaction, old);
                jdbc.update("INSERT INTO vendors (code, name, default_tax_object_code) VALUES (?, ?, ?)",
                        "V017-" + n, "Vendor " + old, old);
                n++;
            }
            jdbc.update("INSERT INTO vendors (code, name) VALUES ('V017-NULL', 'No code')");

            flyway(dataSource, "17").migrate();

            n = 0;
            for (Map.Entry<String, String> e : REMAP.entrySet()) {
                assertThat(jdbc.queryForObject("SELECT default_tax_object_code FROM vendors WHERE code = ?",
                        String.class, "V017-" + n)).as("vendor " + e.getKey()).isEqualTo(e.getValue());
                n++;
            }
            assertThat(jdbc.queryForList("SELECT tax_object_code FROM tax_transaction_details ORDER BY tax_object_code",
                    String.class)).containsExactlyInAnyOrderElementsOf(REMAP.values());
            assertThat(jdbc.queryForObject("SELECT default_tax_object_code FROM vendors WHERE code = 'V017-NULL'",
                    String.class)).isNull();
        }
    }

    @Test
    @DisplayName("Rows with 24-104-99 or 21-100-09 stop the migration with counts per table and change nothing")
    void catchAllCodesFailLoudly() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = dataSource(postgres);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            flyway(dataSource, "16").migrate();
            UUID transaction = seedTransaction(jdbc);
            jdbc.update("INSERT INTO tax_transaction_details (id_transaction, tax_object_code) VALUES (?, '24-104-99')", transaction);
            jdbc.update("INSERT INTO tax_transaction_details (id_transaction, tax_object_code) VALUES (?, '24-104-04')", transaction);
            jdbc.update("INSERT INTO vendors (code, name, default_tax_object_code) VALUES ('V017-A', 'A', '21-100-09')");
            jdbc.update("INSERT INTO vendors (code, name, default_tax_object_code) VALUES ('V017-B', 'B', '21-100-09')");

            assertThatThrownBy(() -> flyway(dataSource, "17").migrate())
                    .hasMessageContaining("tax_transaction_details: 1, vendors.default_tax_object_code: 2, withholding_payments: 0")
                    .hasMessageContaining("21-100-07/18/19/20");

            assertThat(jdbc.queryForList("SELECT tax_object_code FROM tax_transaction_details", String.class))
                    .containsExactlyInAnyOrder("24-104-99", "24-104-04");
            assertThat(jdbc.queryForObject("SELECT max(version::INT)::TEXT FROM flyway_schema_history WHERE success", String.class))
                    .isEqualTo("16");
        }
    }

    private static UUID seedTransaction(JdbcTemplate jdbc) {
        UUID template = UUID.randomUUID();
        jdbc.update("INSERT INTO journal_templates (id, template_name, category, cash_flow_category) "
                + "VALUES (?, 'V017', 'EXPENSE', 'OPERATING')", template);
        UUID transaction = UUID.randomUUID();
        jdbc.update("INSERT INTO transactions (id, transaction_date, id_journal_template, amount, description) "
                + "VALUES (?, DATE '2025-01-01', ?, 1, 'V017')", transaction, template);
        return transaction;
    }

    private static DriverManagerDataSource dataSource(PostgreSQLContainer<?> postgres) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        dataSource.setDriverClassName("org.postgresql.Driver");
        return dataSource;
    }

    private static Flyway flyway(DriverManagerDataSource dataSource, String target) {
        return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(target).load();
    }
}
