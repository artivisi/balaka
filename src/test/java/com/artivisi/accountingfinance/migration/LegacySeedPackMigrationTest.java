package com.artivisi.accountingfinance.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V008-V016 on a database that was migrated to V007 and then loaded from a seed pack as it was at commit 27b8d868
 * (chart of accounts, journal templates and template lines; the resources are {@code git show 27b8d868:...}).
 * No Spring context: Flyway runs the production migrations only, the pack rows are inserted the way
 * DataImportService stored them (header = referenced as a parent).
 */
@DisplayName("Migrations V008-V016 on databases loaded from the 27b8d868 seed packs")
class LegacySeedPackMigrationTest {

    private static final String RESOURCES = "/migration/seed-packs-27b8d868/";
    private static final AtomicInteger DATABASES = new AtomicInteger();
    private static PostgreSQLContainer<?> postgres;

    @BeforeAll
    static void start() {
        postgres = new PostgreSQLContainer<>("postgres:18-alpine");
        postgres.start();
    }

    @AfterAll
    static void stop() {
        postgres.stop();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "it-service,    4.2.02, 5.2.02",
            "online-seller, 4.2.04, 5.5.03",
            "coffee-shop,   7.1.02, 8.1.04",
            "campus,        4.2.04, 5.4.04"
    })
    @DisplayName("Each pack migrates to the latest version; disposal accounts land under the pack's other income/expense header")
    void packMigrates(String pack, String gainCode, String lossCode) throws Exception {
        String url = freshDatabaseAtV007();
        loadPack(url, pack, rows -> rows);

        flyway(url, null).migrate();

        try (Connection c = connect(url)) {
            assertThat(single(c, "SELECT max(version::INT)::TEXT FROM flyway_schema_history WHERE success")).isEqualTo("18");
            assertThat(single(c, "SELECT account_code FROM chart_of_accounts WHERE account_name = 'Laba Penjualan Aset'")).isEqualTo(gainCode);
            assertThat(single(c, "SELECT account_code FROM chart_of_accounts WHERE account_name = 'Rugi Penjualan Aset'")).isEqualTo(lossCode);
            assertThat(single(c, "SELECT account_code FROM chart_of_accounts WHERE account_name = 'Kredit Pajak PPh 23'")).isEqualTo("1.1.26");
            assertThat(single(c, "SELECT a.account_code || ':' || l.position FROM journal_template_lines l "
                    + "JOIN journal_templates t ON t.id = l.id_journal_template JOIN chart_of_accounts a ON a.id = l.id_account "
                    + "WHERE t.template_name = 'Post Gaji Bulanan' AND l.formula = 'bonus'")).isEqualTo("5.8.01:DEBIT");
            assertThat(single(c, "SELECT a.account_code || ':' || l.position FROM journal_template_lines l "
                    + "JOIN journal_templates t ON t.id = l.id_journal_template JOIN chart_of_accounts a ON a.id = l.id_account "
                    + "WHERE t.template_name = 'Post Gaji Bulanan' AND l.formula = 'thr'")).isEqualTo("5.8.02:DEBIT");
            assertThat(single(c, "SELECT count(*)::TEXT FROM journal_templates WHERE template_name IN "
                    + "('Pelepasan Aset Tetap', 'Penghapusan Aset Tetap', 'Pembayaran dengan Pemotongan PPh', "
                    + "'Setor PPh Dipotong', 'Pemotongan PPh 23 oleh Pelanggan')")).isEqualTo("5");
        }
    }

    @Test
    @DisplayName("V008 refuses a reused seed code whose name, header flag or normal balance differs, listing the codes")
    void v008RefusesMismatchedCode() throws Exception {
        String url = freshDatabaseAtV007();
        loadPack(url, "it-service", rows -> {
            List<String[]> changed = new ArrayList<>(rows);
            changed.add(new String[]{"5.8", "Beban Lembur", "EXPENSE", "5", "DEBIT", "true", "false"});
            changed.add(new String[]{"2.1.40", "Hutang Dividen", "LIABILITY", "2.1", "DEBIT", "true", "true"});
            return changed;
        });

        assertThatThrownBy(() -> flyway(url, null).migrate())
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("V008: account codes reserved for seed accounts are used by different accounts: "
                        + "2.1.40 (Hutang Dividen, LIABILITY, leaf, DEBIT; expected Hutang Dividen, LIABILITY, leaf, CREDIT), "
                        + "5.8 (Beban Lembur, EXPENSE, leaf, DEBIT; expected Beban Karyawan & Koreksi Fiskal, EXPENSE, header, DEBIT)");
    }

    @Test
    @DisplayName("V008 refuses to turn an existing leaf account into the parent of a seed account")
    void v008RefusesLeafBecomingParent() throws Exception {
        String url = freshDatabaseAtV007();
        // 3.2 without its children is a leaf; Dividen 3.2.03 would go under it
        loadPack(url, "it-service", rows -> rows.stream().filter(r -> !r[0].startsWith("3.2.")).toList());

        assertThatThrownBy(() -> flyway(url, null).migrate())
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("V008: leaf accounts would become parents of seed accounts: 3.2 (Laba)");
    }

    @Test
    @DisplayName("V008 keeps an identical existing seed account and V011 wires bonus to it")
    void v008KeepsIdenticalAccount() throws Exception {
        String url = freshDatabaseAtV007();
        loadPack(url, "it-service", rows -> {
            List<String[]> changed = new ArrayList<>(rows);
            changed.add(new String[]{"5.8", "Beban Karyawan & Koreksi Fiskal", "EXPENSE", "5", "DEBIT", "true", "false"});
            changed.add(new String[]{"5.8.01", "beban bonus", "EXPENSE", "5.8", "DEBIT", "true", "false"});
            return changed;
        });

        flyway(url, null).migrate();
        try (Connection c = connect(url)) {
            assertThat(single(c, "SELECT account_name FROM chart_of_accounts WHERE account_code = '5.8.01'")).isEqualTo("beban bonus");
            assertThat(single(c, "SELECT a.account_code FROM journal_template_lines l JOIN journal_templates t ON t.id = l.id_journal_template "
                    + "JOIN chart_of_accounts a ON a.id = l.id_account WHERE t.template_name = 'Post Gaji Bulanan' AND l.formula = 'bonus'"))
                    .isEqualTo("5.8.01");
        }
    }

    @Test
    @DisplayName("V012 fails with the candidates listed when no other-expense header can be chosen")
    void v012RefusesWithoutParent() throws Exception {
        String url = freshDatabaseAtV007();
        loadPack(url, "it-service", rows -> rows.stream()
                .map(r -> r[0].equals("5.2.02") ? new String[]{r[0], "Beban Administrasi", r[2], r[3], r[4], r[5], r[6]} : r)
                .map(r -> r[0].equals("5.2") ? new String[]{r[0], "Beban Non Operasional", r[2], r[3], r[4], r[5], r[6]} : r)
                .toList());

        assertThatThrownBy(() -> flyway(url, null).migrate())
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("V012: account \"Rugi Penjualan Aset\" needs one deepest EXPENSE header account named "
                        + "\"Beban Lain-lain\" as parent, found 0: -");
    }

    // ==================== helpers ====================

    private interface RowEdit {
        List<String[]> apply(List<String[]> rows);
    }

    private static String freshDatabaseAtV007() throws SQLException {
        String name = "legacy_pack_" + DATABASES.incrementAndGet();
        try (Connection c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE " + name);
        }
        String url = postgres.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + name + "$1");
        flyway(url, "7").migrate();
        return url;
    }

    private static Flyway flyway(String url, String target) {
        var config = Flyway.configure()
                .dataSource(url, postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration");
        if (target != null) {
            config.target(target);
        }
        return config.load();
    }

    private static Connection connect(String url) throws SQLException {
        return DriverManager.getConnection(url, postgres.getUsername(), postgres.getPassword());
    }

    private static String single(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            assertThat(rs.next()).as("row for: " + sql).isTrue();
            String value = rs.getString(1);
            assertThat(rs.next()).as("single row for: " + sql).isFalse();
            return value;
        }
    }

    private static void loadPack(String url, String pack, RowEdit coaEdit) throws Exception {
        List<String[]> accounts = coaEdit.apply(csv(pack, "02_chart_of_accounts.csv"));
        List<String[]> templates = csv(pack, "04_journal_templates.csv");
        List<String[]> lines = csv(pack, "05_journal_template_lines.csv");

        try (Connection c = connect(url)) {
            c.setAutoCommit(false);
            Set<String> parents = new HashSet<>();
            accounts.forEach(r -> parents.add(r[3]));
            Map<String, UUID> accountIds = new HashMap<>();
            List<String[]> byDepth = new ArrayList<>(accounts);
            byDepth.sort(Comparator.comparingInt(r -> r[0].split("\\.").length));
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO chart_of_accounts (id, account_code, account_name, "
                    + "account_type, normal_balance, id_parent, level, is_header, active, is_permanent) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                for (String[] r : byDepth) {
                    UUID id = UUID.randomUUID();
                    accountIds.put(r[0], id);
                    ps.setObject(1, id);
                    ps.setString(2, r[0]);
                    ps.setString(3, r[1]);
                    ps.setString(4, r[2]);
                    ps.setString(5, r[4]);
                    ps.setObject(6, r[3].isEmpty() ? null : required(accountIds, r[3]));
                    ps.setInt(7, r[0].split("\\.").length);
                    ps.setBoolean(8, parents.contains(r[0]));
                    ps.setBoolean(9, Boolean.parseBoolean(r[5]));
                    ps.setBoolean(10, Boolean.parseBoolean(r[6]));
                    ps.executeUpdate();
                }
            }
            Map<String, UUID> templateIds = new HashMap<>();
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO journal_templates (id, template_name, category, "
                    + "cash_flow_category, template_type, description, is_system, active) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                for (String[] r : templates) {
                    UUID id = UUID.randomUUID();
                    templateIds.put(r[0], id);
                    ps.setObject(1, id);
                    ps.setString(2, r[0]);
                    ps.setString(3, r[1]);
                    ps.setString(4, r[2]);
                    ps.setString(5, r[3]);
                    ps.setString(6, r[4]);
                    ps.setBoolean(7, Boolean.parseBoolean(r[5]));
                    ps.setBoolean(8, Boolean.parseBoolean(r[6]));
                    ps.executeUpdate();
                }
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO journal_template_lines (id, id_journal_template, "
                    + "line_order, id_account, account_hint, position, formula, description) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                for (String[] r : lines) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, required(templateIds, r[0]));
                    ps.setInt(3, Integer.parseInt(r[1]));
                    ps.setObject(4, r[2].isEmpty() ? null : required(accountIds, r[2]));
                    ps.setString(5, r[3].isEmpty() ? null : r[3]);
                    ps.setString(6, r[4]);
                    ps.setString(7, r[5]);
                    ps.setString(8, r[6]);
                    ps.executeUpdate();
                }
            }
            c.commit();
        }
    }

    private static UUID required(Map<String, UUID> ids, String key) {
        UUID id = ids.get(key);
        if (id == null) {
            throw new IllegalStateException("Seed pack row references unknown key " + key);
        }
        return id;
    }

    /** Rows without the header line; quoted fields may contain commas and doubled quotes. */
    private static List<String[]> csv(String pack, String file) throws IOException {
        String path = RESOURCES + pack + "/" + file;
        String content;
        try (InputStream in = LegacySeedPackMigrationTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource " + path);
            }
            content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        List<String[]> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (quoted) {
                if (ch == '"' && i + 1 < content.length() && content.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (ch == '"') {
                    quoted = false;
                } else {
                    field.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (ch == '\n') {
                row.add(field.toString());
                field.setLength(0);
                rows.add(row.toArray(String[]::new));
                row = new ArrayList<>();
            } else if (ch != '\r') {
                field.append(ch);
            }
        }
        if (!field.isEmpty() || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row.toArray(String[]::new));
        }
        return rows.subList(1, rows.size());
    }
}
