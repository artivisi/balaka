package com.artivisi.accountingfinance.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V007 adds journal_entries.line_order and backfills it per transaction: numbered lines by journal
 * number (numeric line suffix, so "-10" follows "-09"; reversal numbers follow the originals),
 * unnumbered lines by created_at. Rows are inserted out of order so physical order cannot
 * produce the expected result.
 */
@DisplayName("V007 journal entry line order migration")
class V007JournalEntryLineOrderMigrationTest {

    private static final UUID POSTED = UUID.randomUUID();
    private static final UUID DRAFT = UUID.randomUUID();
    private static final UUID LETTER_SUFFIX = UUID.randomUUID();

    private static PostgreSQLContainer<?> postgres;
    private static JdbcTemplate jdbc;
    private static DriverManagerDataSource dataSource;
    private static UUID account;

    @BeforeAll
    static void migrateToV006AndSeedJournals() {
        postgres = new PostgreSQLContainer<>("postgres:18-alpine");
        postgres.start();
        dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(dataSource);

        flyway("6").migrate();

        account = UUID.randomUUID();
        jdbc.update("INSERT INTO chart_of_accounts (id, account_code, account_name, account_type, normal_balance) "
                + "VALUES (?, '9.9.99', 'V007', 'ASSET', 'DEBIT')", account);
        UUID template = UUID.randomUUID();
        jdbc.update("INSERT INTO journal_templates (id, template_name, category, cash_flow_category) "
                + "VALUES (?, 'V007', 'EXPENSE', 'OPERATING')", template);
        for (UUID tx : List.of(POSTED, DRAFT, LETTER_SUFFIX)) {
            jdbc.update("INSERT INTO transactions (id, transaction_date, id_journal_template, amount, description) "
                    + "VALUES (?, DATE '2025-01-01', ?, 1, 'V007')", tx, template);
        }

        entry(POSTED, "JE-2025-0001-10", "2025-01-01 10:00:00", 4);
        entry(POSTED, "JE-2025-0003-02", "2025-01-01 09:00:00", 1);
        entry(POSTED, "JE-2025-0001-02", "2025-01-01 10:00:00", 2);
        entry(POSTED, "JE-2025-0001-09", "2025-01-01 10:00:00", 3);
        entry(POSTED, "JE-2025-0003-01", "2025-01-01 09:00:00", 5);
        entry(POSTED, "JE-2025-0001-01", "2025-01-01 10:00:00", 6);

        entry(DRAFT, null, "2025-01-01 10:00:03", 30);
        entry(DRAFT, null, "2025-01-01 10:00:01", 10);
        entry(DRAFT, null, "2025-01-01 10:00:02", 20);

        entry(LETTER_SUFFIX, "JRN-X-D", "2025-01-01 10:00:00", 2);
        entry(LETTER_SUFFIX, "JRN-X-C", "2025-01-01 10:00:00", 1);

        flyway("7").migrate();
    }

    @AfterAll
    static void stop() {
        postgres.stop();
    }

    @Test
    @DisplayName("Numbered lines are ordered by base number then numeric suffix; reversals follow originals")
    void numberedLinesOrderedByJournalNumber() {
        assertThat(lineOrderOf(POSTED, "journal_number")).containsExactly(
                "JE-2025-0001-01", "JE-2025-0001-02", "JE-2025-0001-09", "JE-2025-0001-10",
                "JE-2025-0003-01", "JE-2025-0003-02");
    }

    @Test
    @DisplayName("Unnumbered draft lines are ordered by creation time")
    void unnumberedLinesOrderedByCreation() {
        assertThat(lineOrderOf(DRAFT, "debit_amount::int::text")).containsExactly("10", "20", "30");
    }

    @Test
    @DisplayName("Numbers without a numeric suffix are ordered by journal number")
    void nonNumericSuffixOrderedByJournalNumber() {
        assertThat(lineOrderOf(LETTER_SUFFIX, "journal_number")).containsExactly("JRN-X-C", "JRN-X-D");
    }

    @Test
    @DisplayName("line_order is required, positive and unique within a transaction")
    void lineOrderConstrained() {
        assertThat(jdbc.queryForObject("SELECT is_nullable FROM information_schema.columns "
                + "WHERE table_name = 'journal_entries' AND column_name = 'line_order'", String.class)).isEqualTo("NO");
        assertThatThrownBy(() -> entryWithLineOrder(POSTED, null))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("line_order");
        assertThatThrownBy(() -> entryWithLineOrder(POSTED, 0))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("chk_je_line_order_positive");
        assertThatThrownBy(() -> entryWithLineOrder(POSTED, 1))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("uk_je_transaction_line_order");
    }

    private static Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(target)
                .load();
    }

    private static void entry(UUID transaction, String journalNumber, String createdAt, int debit) {
        jdbc.update("INSERT INTO journal_entries (id, journal_number, id_transaction, id_account, debit_amount, "
                        + "credit_amount, created_at) VALUES (?, ?, ?, ?, ?, 0, ?::timestamp)",
                UUID.randomUUID(), journalNumber, transaction, account, debit, createdAt);
    }

    private static void entryWithLineOrder(UUID transaction, Integer lineOrder) {
        jdbc.update("INSERT INTO journal_entries (id, id_transaction, id_account, debit_amount, credit_amount, "
                + "line_order) VALUES (?, ?, ?, 1, 0, ?)", UUID.randomUUID(), transaction, account, lineOrder);
    }

    private static List<String> lineOrderOf(UUID transaction, String column) {
        List<Integer> orders = jdbc.queryForList("SELECT line_order FROM journal_entries WHERE id_transaction = ? "
                + "ORDER BY line_order", Integer.class, transaction);
        assertThat(orders).isEqualTo(IntStream.rangeClosed(1, orders.size()).boxed().toList());
        return jdbc.queryForList("SELECT " + column + " FROM journal_entries WHERE id_transaction = ? "
                + "ORDER BY line_order", String.class, transaction);
    }
}
