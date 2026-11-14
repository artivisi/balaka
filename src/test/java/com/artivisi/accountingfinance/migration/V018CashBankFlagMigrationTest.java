package com.artivisi.accountingfinance.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V018 on a database built before the explicit flag existed. The migration must keep
 * every account the old selector counted as cash, otherwise existing installations
 * lose cash from the dashboard, the cash flow statement and SPT 8A.I.1 the moment
 * they upgrade.
 */
@DisplayName("V018 cash/bank flag migration")
class V018CashBankFlagMigrationTest {

    @Test
    @DisplayName("Backfill keeps the old selector: active non-header 1.1.0x assets and bank-linked accounts")
    void backfillKeepsTheOldSelector() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = dataSource(postgres);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            flyway(dataSource, "17").migrate();

            jdbc.update(insert(), UUID.randomUUID(), "1.1", "Aset Lancar", "ASSET", "DEBIT", 2, true, true);
            jdbc.update(insert(), UUID.randomUUID(), "1.1.01", "Kas", "ASSET", "DEBIT", 2, false, true);
            jdbc.update(insert(), UUID.randomUUID(), "1.1.04", "Piutang Usaha", "ASSET", "DEBIT", 3, false, true);
            jdbc.update(insert(), UUID.randomUUID(), "1.1.07", "Sewa Dibayar Dimuka", "ASSET", "DEBIT", 3, false, true);
            jdbc.update(insert(), UUID.randomUUID(), "1.1.10", "Piutang Lain", "ASSET", "DEBIT", 3, false, true);
            jdbc.update(insert(), UUID.randomUUID(), "1.1.11", "Bank Lain", "ASSET", "DEBIT", 3, false, false);
            jdbc.update(insert(), UUID.randomUUID(), "4.1", "Pendapatan", "REVENUE", "CREDIT", 2, false, true);

            UUID linkedId = UUID.randomUUID();
            jdbc.update(insert(), linkedId, "1.1.09", "Bank Lainnya", "ASSET", "DEBIT", 3, false, true);
            jdbc.update("""
                    INSERT INTO company_bank_accounts (id, bank_name, account_number, account_name, id_account)
                    VALUES (?, 'Bank Mandiri', '1234567890', 'PT Contoh', ?)""", UUID.randomUUID(), linkedId);

            flyway(dataSource, "18").migrate();

            // Every account the old prefix rule counted as cash is still cash.
            assertThat(flag(jdbc, "1.1.01")).isTrue();
            assertThat(flag(jdbc, "1.1.04")).isTrue();
            assertThat(flag(jdbc, "1.1.07")).isTrue();
            assertThat(flag(jdbc, "1.1.09")).isTrue();
            // Outside the old selector: 1.1.10 is not 1.1.0x, 1.1.11 is inactive, the header is a header.
            assertThat(flag(jdbc, "1.1.10")).isFalse();
            assertThat(flag(jdbc, "1.1.11")).isFalse();
            assertThat(flag(jdbc, "1.1")).isFalse();
            assertThat(flag(jdbc, "4.1")).isFalse();

            // Provenance is recorded, so unlinking the bank does not clear the flag later.
            assertThat(jdbc.queryForObject("SELECT cash_bank_source FROM chart_of_accounts WHERE account_code = '1.1.09'", String.class))
                    .isEqualTo("MIGRATED");
            assertThat(jdbc.queryForObject("SELECT cash_bank_source FROM chart_of_accounts WHERE account_code = '1.1.10'", String.class))
                    .isNull();

            // The constraint still refuses cash on a header or a non-asset.
            assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                    jdbc.update("UPDATE chart_of_accounts SET is_cash_bank = TRUE WHERE account_code = '1.1'")))
                    .hasMessageContaining("chk_coa_cash_bank_asset");
            assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                    jdbc.update("UPDATE chart_of_accounts SET is_cash_bank = TRUE WHERE account_code = '4.1'")))
                    .hasMessageContaining("chk_coa_cash_bank_asset");
        }
    }

    @Test
    @DisplayName("Empty chart migrates without error and without flagging anything")
    void emptyChart() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = dataSource(postgres);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            flyway(dataSource, "18").migrate();

            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chart_of_accounts WHERE is_cash_bank", Integer.class)).isZero();
        }
    }

    private String insert() {
        return """
                INSERT INTO chart_of_accounts (id, account_code, account_name, account_type, normal_balance, level, is_header, active, is_permanent)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE)""";
    }

    private Boolean flag(JdbcTemplate jdbc, String code) {
        return jdbc.queryForObject("SELECT is_cash_bank FROM chart_of_accounts WHERE account_code = ?", Boolean.class, code);
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
