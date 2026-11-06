package com.artivisi.accountingfinance.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** V012 on a production-shaped database with an existing asset; an empty database gets no account or template. */
@DisplayName("V012 fixed asset register migration")
class V012FixedAssetRegisterMigrationTest {

    @Test
    @DisplayName("Existing asset keeps every value with new fields null; accounts and templates added; GIFTED allowed")
    void migratesExistingAsset() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = dataSource(postgres);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            flyway(dataSource, "11").migrate();
            seedChart(jdbc);
            UUID asset = seedAsset(jdbc);

            flyway(dataSource, "12").migrate();

            Map<String, Object> row = jdbc.queryForMap("SELECT * FROM fixed_assets WHERE id = ?", asset);
            assertThat(row.get("asset_code")).isEqualTo("OLD-1");
            assertThat(row.get("location")).isEqualTo("Kantor");
            assertThat(row.get("serial_number")).isEqualTo("SN-OLD");
            for (String column : List.of("brand", "model", "custodian", "asset_condition", "last_verified_date", "id_photo_document")) {
                assertThat(row.get(column)).as(column).isNull();
            }

            assertThat(jdbc.queryForObject("SELECT account_code FROM chart_of_accounts WHERE account_name = 'Laba Penjualan Aset'", String.class)).isEqualTo("4.2.01");
            assertThat(jdbc.queryForObject("SELECT fiscal_treatment FROM chart_of_accounts WHERE account_name = 'Rugi Penjualan Aset'", String.class)).isEqualTo("DEDUCTIBLE");
            assertThat(lines(jdbc, "Pelepasan Aset Tetap")).containsExactly(
                    "DEBIT:KAS_PENERIMAAN:disposalProceeds", "DEBIT:AKUM_PENYUSUTAN:accumulatedDepreciation",
                    "DEBIT:RUGI_PELEPASAN:lossOnDisposal", "CREDIT:ASET_TETAP:assetCost", "CREDIT:LABA_PELEPASAN:gainOnDisposal");
            assertThat(lines(jdbc, "Penghapusan Aset Tetap")).containsExactly(
                    "DEBIT:AKUM_PENYUSUTAN:accumulatedDepreciation", "DEBIT:RUGI_PELEPASAN:lossOnDisposal", "CREDIT:ASET_TETAP:assetCost");

            jdbc.update("UPDATE fixed_assets SET disposal_type = 'GIFTED' WHERE id = ?", asset);
            assertThatThrownBy(() -> jdbc.update("UPDATE fixed_assets SET disposal_type = 'NOPE' WHERE id = ?", asset))
                    .hasMessageContaining("chk_disposal_type");
            assertThatThrownBy(() -> jdbc.update("UPDATE fixed_assets SET asset_condition = 'NOPE' WHERE id = ?", asset))
                    .hasMessageContaining("chk_asset_condition");
        }
    }

    @Test
    @DisplayName("Second active disposal, gain not equal to proceeds minus book value, and a sale without account are refused by the schema")
    void disposalConstraints() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = dataSource(postgres);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            flyway(dataSource, "11").migrate();
            seedChart(jdbc);
            UUID asset = seedAsset(jdbc);
            flyway(dataSource, "12").migrate();

            insertDisposal(jdbc, asset, "D-1", "WRITTEN_OFF", "0", "100", "-100", null, "DRAFT");
            assertThatThrownBy(() -> insertDisposal(jdbc, asset, "D-2", "WRITTEN_OFF", "0", "100", "-100", null, "POSTED"))
                    .hasMessageContaining("uk_asset_disposal_active");
            assertThatThrownBy(() -> insertDisposal(jdbc, asset, "D-3", "WRITTEN_OFF", "0", "100", "-99", null, "CANCELLED"))
                    .hasMessageContaining("chk_asset_disposal_gain_loss");
            assertThatThrownBy(() -> insertDisposal(jdbc, asset, "D-4", "SOLD", "10", "100", "-90", null, "CANCELLED"))
                    .hasMessageContaining("chk_asset_disposal_sale");
            assertThatThrownBy(() -> insertDisposal(jdbc, asset, "D-5", "GIFTED", "10", "100", "-90", null, "CANCELLED"))
                    .hasMessageContaining("chk_asset_disposal_sale");
            insertDisposal(jdbc, asset, "D-6", "WRITTEN_OFF", "0", "100", "-100", null, "CANCELLED");
        }
    }

    @Test
    @DisplayName("Gain and loss accounts take the next free code under their parent header")
    void takesNextFreeCode() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = dataSource(postgres);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            flyway(dataSource, "11").migrate();
            seedChart(jdbc);
            insertAccount(jdbc, "4.2.01", "Pendapatan Bunga", "REVENUE", "CREDIT");
            insertAccount(jdbc, "4.2.02", "Akun Lain", "REVENUE", "CREDIT");
            insertAccount(jdbc, "5.2.09", "Beban Bank", "EXPENSE", "DEBIT");

            flyway(dataSource, "12").migrate();

            assertThat(jdbc.queryForObject("SELECT account_code FROM chart_of_accounts WHERE account_name = 'Laba Penjualan Aset'", String.class)).isEqualTo("4.2.03");
            assertThat(jdbc.queryForObject("SELECT account_code FROM chart_of_accounts WHERE account_name = 'Rugi Penjualan Aset'", String.class)).isEqualTo("5.2.10");
            assertThat(jdbc.queryForObject("SELECT account_name FROM chart_of_accounts WHERE account_code = '4.2.02'", String.class)).isEqualTo("Akun Lain");
        }
    }

    @Test
    @DisplayName("Existing accounts with those names keep their codes")
    void keepsExistingAccounts() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = dataSource(postgres);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            flyway(dataSource, "11").migrate();
            seedChart(jdbc);
            insertAccount(jdbc, "7.1.09", "Laba Penjualan Aset", "REVENUE", "CREDIT");
            insertAccount(jdbc, "8.1.09", "Rugi Penjualan Aset", "EXPENSE", "DEBIT");

            flyway(dataSource, "12").migrate();

            assertThat(jdbc.queryForList("SELECT account_code FROM chart_of_accounts WHERE account_name = 'Laba Penjualan Aset'", String.class)).containsExactly("7.1.09");
            assertThat(jdbc.queryForList("SELECT account_code FROM chart_of_accounts WHERE account_name = 'Rugi Penjualan Aset'", String.class)).containsExactly("8.1.09");
        }
    }

    @Test
    @DisplayName("Code conflict, taken template name, missing parent or missing non-deductible account fail the migration")
    void conflictsFail() {
        assertFails(jdbc -> jdbc.update("DELETE FROM chart_of_accounts WHERE account_code = '4.2'"),
                "needs one deepest REVENUE header account named \"Pendapatan Lain-lain\" as parent, found 0");
        assertFails(jdbc -> insertHeader(jdbc, "7.2", "Pendapatan Lain-Lain", "REVENUE", "CREDIT"),
                "found 2: 4.2 Pendapatan Lain-lain, 7.2 Pendapatan Lain-Lain");
        assertFails(jdbc -> jdbc.update("""
                INSERT INTO journal_templates (id, template_name, category, cash_flow_category)
                VALUES (?, 'Pelepasan Aset Tetap', 'INCOME', 'INVESTING')""", UUID.randomUUID()), "already in use");
        assertFails(jdbc -> jdbc.update("DELETE FROM chart_of_accounts WHERE account_code = '5.2'"),
                "needs one deepest EXPENSE header account named \"Beban Lain-lain\" as parent, found 0");
        assertFails(jdbc -> jdbc.update("DELETE FROM chart_of_accounts WHERE account_code = '5.8.04'"), "Beban Tidak Dapat Dikurangkan");
    }

    @Test
    @DisplayName("Empty database: no account or template inserted")
    void emptyDatabase() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = dataSource(postgres);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            flyway(dataSource, "12").migrate();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chart_of_accounts", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM journal_templates WHERE template_name IN ('Pelepasan Aset Tetap', 'Penghapusan Aset Tetap')", Integer.class)).isZero();
        }
    }

    private void assertFails(java.util.function.Consumer<JdbcTemplate> change, String message) {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = dataSource(postgres);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            flyway(dataSource, "11").migrate();
            seedChart(jdbc);
            change.accept(jdbc);
            assertThatThrownBy(() -> flyway(dataSource, "12").migrate()).hasMessageContaining(message);
        }
    }

    private List<String> lines(JdbcTemplate jdbc, String template) {
        return jdbc.queryForList("""
                SELECT l.position || ':' || l.account_hint || ':' || l.formula FROM journal_template_lines l
                JOIN journal_templates t ON t.id = l.id_journal_template
                WHERE t.template_name = ? ORDER BY l.line_order""", String.class, template);
    }

    private void seedChart(JdbcTemplate jdbc) {
        insertHeader(jdbc, "4.2", "Pendapatan Lain-lain", "REVENUE", "CREDIT");
        insertHeader(jdbc, "5.2", "Beban Lain-lain", "EXPENSE", "DEBIT");
        insertAccount(jdbc, "5.8.04", "Beban Tidak Dapat Dikurangkan", "EXPENSE", "DEBIT");
        insertAccount(jdbc, "1.2.01", "Peralatan", "ASSET", "DEBIT");
        insertAccount(jdbc, "1.2.02", "Akum", "ASSET", "CREDIT");
        insertAccount(jdbc, "5.1.12", "Beban Penyusutan", "EXPENSE", "DEBIT");
    }

    private void insertHeader(JdbcTemplate jdbc, String code, String name, String type, String balance) {
        jdbc.update("""
                INSERT INTO chart_of_accounts (id, account_code, account_name, account_type, normal_balance, level, is_header, active, is_permanent)
                VALUES (?, ?, ?, ?, ?, 2, TRUE, TRUE, TRUE)""", UUID.randomUUID(), code, name, type, balance);
    }

    private void insertAccount(JdbcTemplate jdbc, String code, String name, String type, String balance) {
        jdbc.update("""
                INSERT INTO chart_of_accounts (id, account_code, account_name, account_type, normal_balance, level, is_header, active, is_permanent)
                VALUES (?, ?, ?, ?, ?, 2, FALSE, TRUE, TRUE)""", UUID.randomUUID(), code, name, type, balance);
    }

    private UUID accountId(JdbcTemplate jdbc, String code) {
        return jdbc.queryForObject("SELECT id FROM chart_of_accounts WHERE account_code = ?", UUID.class, code);
    }

    private UUID seedAsset(JdbcTemplate jdbc) {
        UUID category = UUID.randomUUID();
        UUID asset = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO asset_categories (id, code, name, depreciation_method, useful_life_months, id_asset_account,
                    id_accumulated_depreciation_account, id_depreciation_expense_account)
                VALUES (?, 'K', 'Komputer', 'STRAIGHT_LINE', 48, ?, ?, ?)""",
                category, accountId(jdbc, "1.2.01"), accountId(jdbc, "1.2.02"), accountId(jdbc, "5.1.12"));
        jdbc.update("""
                INSERT INTO fixed_assets (id, asset_code, name, id_category, purchase_date, purchase_cost, depreciation_start_date,
                    book_value, id_asset_account, id_accumulated_depreciation_account, id_depreciation_expense_account, location, serial_number)
                VALUES (?, 'OLD-1', 'Lama', ?, '2020-01-01', 1000, '2020-01-01', 1000, ?, ?, ?, 'Kantor', 'SN-OLD')""",
                asset, category, accountId(jdbc, "1.2.01"), accountId(jdbc, "1.2.02"), accountId(jdbc, "5.1.12"));
        return asset;
    }

    private void insertDisposal(JdbcTemplate jdbc, UUID asset, String number, String type, String proceeds, String book, String gainLoss,
                                UUID account, String status) {
        jdbc.update("""
                INSERT INTO asset_disposals (id, record_number, id_fixed_asset, disposal_type, disposal_date, reason, proceeds,
                    purchase_cost, accumulated_depreciation, book_value, gain_loss, id_proceeds_account, status, created_by)
                VALUES (?, ?, ?, ?, '2026-01-01', 'r', ?::numeric, 100, 0, ?::numeric, ?::numeric, ?, ?, 'tester')""",
                UUID.randomUUID(), number, asset, type, proceeds, book, gainLoss, account, status);
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
