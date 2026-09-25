package com.artivisi.accountingfinance.functional.seller;

import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Online seller data loaded by SellerTestDataInitializer, verified row by row against its source CSVs by natural
 * key: seed product categories and products (industry-seed/online-seller), test clients, and the imported POSTED
 * financial transactions with their journal lines (capital, inventory purchases, Tokopedia/Shopee sales net of
 * the marketplace admin fee with COGS, seller-paid shipping). Each record is checked in the database and on its
 * list or detail page.
 */
@DisplayName("Online Seller - CSV-Driven Tests")
@Import(SellerTestDataInitializer.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SellerCsvDrivenTest extends PlaywrightTestBase {

    private static final Path SEED_DIR = Path.of("industry-seed/online-seller/seed-data");
    private static final Path TEST_DATA_DIR = Path.of("src/test/resources/testdata/seller");
    private static final DateTimeFormatter DETAIL_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.of("id", "ID"));

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @TestFactory
    @DisplayName("Should import each product category of the seed pack")
    Stream<DynamicTest> shouldImportProductCategories() {
        List<Map<String, String>> rows = readCsv(SEED_DIR.resolve("35_product_categories.csv"));
        assertEquals(5, rows.size(), "35_product_categories.csv rows");
        return rows.stream().map(row -> DynamicTest.dynamicTest("Category " + row.get("code"), () -> {
            String code = row.get("code");
            assertEquals(List.of(row.get("name") + "|" + row.get("description") + "|" + row.get("parent_code") + "|" + row.get("active")),
                    jdbcTemplate.queryForList("""
                            SELECT c.name || '|' || COALESCE(c.description, '') || '|' || COALESCE(p.code, '') || '|' || c.active
                            FROM product_categories c LEFT JOIN product_categories p ON p.id = c.id_parent
                            WHERE c.code = ?""", String.class, code));

            loginAsAdmin();
            navigateTo("/products/categories?search=" + encode(code));
            waitForPageLoad();
            assertThat(page.getByTestId("category-name-" + code)).hasText(row.get("name"));
            assertThat(page.getByTestId("category-parent-" + code)).hasText("-");
            assertThat(page.getByTestId("category-status-" + code)).hasText("Aktif");
        }));
    }

    @TestFactory
    @DisplayName("Should import each product of the seed pack with its category, costing method, price and accounts")
    Stream<DynamicTest> shouldImportProducts() {
        Map<String, String> categoryNames = readCsv(SEED_DIR.resolve("35_product_categories.csv")).stream()
                .collect(Collectors.toMap(r -> r.get("code"), r -> r.get("name")));
        List<Map<String, String>> rows = readCsv(SEED_DIR.resolve("36_products.csv"));
        assertEquals(18, rows.size(), "36_products.csv rows");
        return rows.stream().map(row -> DynamicTest.dynamicTest("Product " + row.get("code"), () -> {
            String code = row.get("code");
            assertEquals(List.of(String.join("|", row.get("name"), row.get("description"), row.get("unit"),
                            row.get("category_code"), row.get("costing_method"), row.get("track_inventory"),
                            plain(row.get("minimum_stock")), plain(row.get("selling_price")),
                            row.get("inventory_account_code"), row.get("cogs_account_code"),
                            row.get("sales_account_code"), row.get("active"))),
                    jdbcTemplate.queryForList("""
                            SELECT p.name, p.description, p.unit, c.code AS category, p.costing_method, p.track_inventory,
                                   p.minimum_stock, p.selling_price, ia.account_code AS inv, ca.account_code AS cogs,
                                   sa.account_code AS sales, p.active
                            FROM products p
                            JOIN product_categories c ON c.id = p.id_category
                            JOIN chart_of_accounts ia ON ia.id = p.id_inventory_account
                            JOIN chart_of_accounts ca ON ca.id = p.id_cogs_account
                            JOIN chart_of_accounts sa ON sa.id = p.id_sales_account
                            WHERE p.code = ?""", code).stream()
                            .map(r -> String.join("|", (String) r.get("name"), (String) r.get("description"),
                                    (String) r.get("unit"), (String) r.get("category"), (String) r.get("costing_method"),
                                    String.valueOf(r.get("track_inventory")), plain((BigDecimal) r.get("minimum_stock")),
                                    plain((BigDecimal) r.get("selling_price")), (String) r.get("inv"),
                                    (String) r.get("cogs"), (String) r.get("sales"), String.valueOf(r.get("active"))))
                            .toList());

            loginAsAdmin();
            navigateTo("/products?search=" + encode(code));
            waitForPageLoad();
            assertThat(page.getByTestId("product-name-" + code)).hasText(row.get("name"));
            assertThat(page.getByTestId("product-category-" + code)).hasText(categoryNames.get(row.get("category_code")));
            assertThat(page.getByTestId("product-unit-" + code)).hasText(row.get("unit"));
            assertThat(page.getByTestId("product-price-" + code))
                    .hasText(String.format(Locale.US, "%,d", new BigDecimal(row.get("selling_price")).longValueExact()));
            assertThat(page.getByTestId("product-status-" + code)).hasText("Aktif");
        }));
    }

    @TestFactory
    @DisplayName("Should import each marketplace and supplier client")
    Stream<DynamicTest> shouldImportClients() {
        List<Map<String, String>> rows = readCsv(TEST_DATA_DIR.resolve("clients.csv"));
        assertEquals(5, rows.size(), "clients.csv rows");
        return rows.stream().map(row -> DynamicTest.dynamicTest("Client " + row.get("code"), () -> {
            String code = row.get("code");
            assertEquals(List.of(String.join("|", row.get("name"), row.get("contact_person"), row.get("email"),
                            row.get("phone"), row.get("address"), row.get("npwp"), row.get("active"))),
                    jdbcTemplate.queryForList("""
                            SELECT name || '|' || contact_person || '|' || email || '|' || phone || '|' || address
                                   || '|' || npwp || '|' || active
                            FROM clients WHERE code = ?""", String.class, code));

            loginAsAdmin();
            navigateTo("/clients?search=" + encode(code));
            waitForPageLoad();
            Locator clientRow = page.getByTestId("client-row-" + code);
            assertThat(page.getByTestId("client-name-" + code)).hasText(row.get("name"));
            assertThat(clientRow.locator("td").nth(2)).hasText(row.get("contact_person"));
            assertThat(clientRow.locator("td").nth(3)).hasText(row.get("email"));
            assertThat(clientRow.locator("td").nth(4)).hasText("Aktif");
        }));
    }

    @TestFactory
    @DisplayName("Should import each POSTED seller transaction with its template, amount and journal lines")
    Stream<DynamicTest> shouldImportFinancialTransactions() {
        List<Map<String, String>> transactions = readCsv(TEST_DATA_DIR.resolve("financial-transactions.csv"));
        List<Map<String, String>> journalLines = readCsv(TEST_DATA_DIR.resolve("financial-journal-entries.csv"));
        assertEquals(6, transactions.size(), "financial-transactions.csv rows");
        assertEquals(18, journalLines.size(), "financial-journal-entries.csv rows");
        Map<String, List<Map<String, String>>> linesByTransaction = new HashMap<>();
        journalLines.forEach(l -> linesByTransaction.computeIfAbsent(l.get("transaction_number"), k -> new ArrayList<>()).add(l));
        assertEquals(transactions.stream().map(t -> t.get("transaction_number")).sorted().toList(),
                linesByTransaction.keySet().stream().sorted().toList(), "every transaction has journal lines");

        return transactions.stream().map(row -> DynamicTest.dynamicTest("Transaction " + row.get("transaction_number"), () -> {
            String number = row.get("transaction_number");
            List<Map<String, String>> lines = linesByTransaction.get(number);
            assertFalse(lines.isEmpty());

            Map<String, Object> stored = jdbcTemplate.queryForMap("""
                    SELECT t.id, t.transaction_date, jt.template_name, t.amount, t.description, t.reference_number,
                           t.status, t.posted_by
                    FROM transactions t JOIN journal_templates jt ON jt.id = t.id_journal_template
                    WHERE t.transaction_number = ?""", number);
            assertEquals(String.join("|", row.get("transaction_date"), row.get("template_name"), plain(row.get("amount")),
                            row.get("description"), row.get("reference_number"), row.get("status"), row.get("posted_by")),
                    String.join("|", stored.get("transaction_date").toString(), (String) stored.get("template_name"),
                            plain((BigDecimal) stored.get("amount")), (String) stored.get("description"),
                            (String) stored.get("reference_number"), (String) stored.get("status"),
                            (String) stored.get("posted_by")));
            UUID id = (UUID) stored.get("id");

            assertEquals(lines.stream().map(l -> String.join("|", l.get("journal_number"), l.get("account_code"), plain(l.get("debit_amount")), plain(l.get("credit_amount"))))
                            .sorted().toList(),
                    jdbcTemplate.queryForList("""
                            SELECT je.journal_number, coa.account_code, je.debit_amount, je.credit_amount
                            FROM journal_entries je JOIN chart_of_accounts coa ON coa.id = je.id_account
                            WHERE je.id_transaction = ?""", id).stream()
                            .map(r -> String.join("|", (String) r.get("journal_number"), (String) r.get("account_code"), plain((BigDecimal) r.get("debit_amount")),
                                    plain((BigDecimal) r.get("credit_amount"))))
                            .sorted().toList());
            BigDecimal debit = lines.stream().map(l -> new BigDecimal(l.get("debit_amount"))).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal credit = lines.stream().map(l -> new BigDecimal(l.get("credit_amount"))).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(0, debit.compareTo(credit), "journal of " + number + " is balanced");

            loginAsAdmin();
            navigateTo("/transactions/" + id);
            waitForPageLoad();
            assertThat(page.locator("#transaction-number")).hasText(number);
            assertThat(page.getByTestId("status-posted")).hasText("Transaksi Berhasil Diposting");
            assertThat(page.getByTestId("template-link")).hasText(row.get("template_name"));
            assertThat(page.locator("p:text-is('Tanggal') + p"))
                    .hasText(LocalDate.parse(row.get("transaction_date")).format(DETAIL_DATE));
            assertThat(page.locator("p:text-is('No. Referensi') + p")).hasText(row.get("reference_number"));
            assertEquals(lines.stream().map(l -> l.get("account_code") + "|" + rupiah(l.get("debit_amount")) + "|"
                    + rupiah(l.get("credit_amount"))).sorted().toList(), journalRows());
            assertThat(page.locator("#total-debit")).hasText(rupiah(debit.toPlainString()));
            assertThat(page.locator("#total-credit")).hasText(rupiah(credit.toPlainString()));
        }));
    }

    private List<String> journalRows() {
        Locator rows = page.locator(".journal-entry-row");
        List<String> result = new ArrayList<>();
        for (int i = 0; i < rows.count(); i++) {
            Locator cells = rows.nth(i).locator("> div");
            result.add(page.locator("#entry-account-code-" + i).textContent().trim() + "|"
                    + cells.nth(1).textContent().trim() + "|" + cells.nth(2).textContent().trim());
        }
        return result.stream().sorted().toList();
    }

    private static List<Map<String, String>> readCsv(Path file) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            String[] header = lines.getFirst().split(",", -1);
            List<Map<String, String>> rows = new ArrayList<>();
            for (String line : lines.subList(1, lines.size())) {
                if (line.isBlank()) {
                    continue;
                }
                String[] values = line.split(",", -1);
                assertEquals(header.length, values.length, file + " column count: " + line);
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < header.length; i++) {
                    row.put(header[i], values[i]);
                }
                rows.add(row);
            }
            return rows;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String plain(String value) {
        return plain(new BigDecimal(value));
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String rupiah(String value) {
        BigDecimal amount = new BigDecimal(value);
        if (amount.signum() == 0) {
            return "-";
        }
        return "Rp " + String.format(Locale.US, "%,d", amount.longValueExact()).replace(',', '.');
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
