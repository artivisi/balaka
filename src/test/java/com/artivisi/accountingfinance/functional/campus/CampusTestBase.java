package com.artivisi.accountingfinance.functional.campus;

import com.artivisi.accountingfinance.functional.util.CsvLoader;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared helpers for campus tests: students/programs fixtures, template detail checks and posting a campus
 * template through the transaction form with its journal verified in the database and on the detail page.
 */
abstract class CampusTestBase extends PlaywrightTestBase {

    private static final Pattern TRANSACTION_DETAIL_URL = Pattern.compile(".*/transactions/([0-9a-f-]{36})$");
    private static final Locale ID = Locale.of("id", "ID");

    protected final String runId = UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    record Program(String code, String name, long tuition, long enrollmentFee, long labFee, long graduationFee) {
    }

    record Student(String nim, String name, Program program, int semester, int enrollmentYear,
                   String scholarshipType, int scholarshipPercentage) {
    }

    protected static Map<String, Program> programs() {
        List<Map<String, String>> rows = CsvLoader.loadRecords("campus/programs.csv");
        assertEquals(3, rows.size(), "campus/programs.csv rows");
        Map<String, Program> programs = new LinkedHashMap<>();
        for (Map<String, String> r : rows) {
            programs.put(r.get("program_code"), new Program(r.get("program_code"), r.get("program_name"),
                    Long.parseLong(r.get("tuition_per_semester")), Long.parseLong(r.get("enrollment_fee")),
                    Long.parseLong(r.get("lab_fee_per_semester")), Long.parseLong(r.get("graduation_fee"))));
        }
        return programs;
    }

    protected static List<Student> students() {
        Map<String, Program> programs = programs();
        List<Map<String, String>> rows = CsvLoader.loadRecords("campus/students.csv");
        assertEquals(6, rows.size(), "campus/students.csv rows");
        List<Student> students = new ArrayList<>();
        for (Map<String, String> r : rows) {
            Program program = programs.get(r.get("program_code"));
            if (program == null) {
                throw new IllegalStateException("Unknown program " + r.get("program_code") + " for " + r.get("nim"));
            }
            students.add(new Student(r.get("nim"), r.get("name"), program, Integer.parseInt(r.get("semester")),
                    Integer.parseInt(r.get("enrollment_year")), r.get("scholarship_type"),
                    Integer.parseInt(r.get("scholarship_percentage"))));
        }
        return students;
    }

    protected static Student student(String nim) {
        return students().stream().filter(s -> s.nim().equals(nim)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Student missing from campus/students.csv: " + nim));
    }

    /**
     * Opens the template from the template list (search + detail link) and asserts every line as
     * "accountCode|accountName|position|formula"; a line without a fixed account shows "?|hint".
     */
    protected void assertTemplateLines(String templateName, String category, String... expectedLines) {
        navigateTo("/templates?search=" + templateName.replace(" ", "+"));
        waitForPageLoad();
        Locator card = page.getByTestId("template-card").filter(new Locator.FilterOptions()
                .setHas(page.locator("h3:text-is('" + templateName + "')")));
        assertThat(card).hasCount(1);
        card.locator("a[title='Lihat Detail']").click();
        page.waitForURL("**/templates/" + templateId(templateName));
        waitForPageLoad();

        assertThat(page.locator("#template-name")).hasText(templateName);
        assertThat(page.getByTestId("template-category-label")).hasText(category);
        Locator lines = page.getByTestId("template-line");
        assertThat(lines).hasCount(expectedLines.length);
        for (int i = 0; i < expectedLines.length; i++) {
            Locator line = lines.nth(i);
            assertEquals(expectedLines[i], String.join("|", line.locator("span.font-mono").textContent().trim(),
                    line.locator("p.font-medium").textContent().trim(),
                    line.locator("#position-badge-" + i).textContent().trim(),
                    line.locator("code").textContent().trim()), templateName + " line " + (i + 1));
        }
    }

    /** Posts a template whose accounts are all fixed. */
    protected UUID post(String templateName, LocalDate date, long amount, String description, String reference,
                        List<String> expectedJournal) {
        openForm(templateName, date, amount, description, reference);
        return submitAndVerify(templateName, date, amount, description, reference, expectedJournal);
    }

    /** Posts a template whose BANK line is mapped to {@code bankAccountCode} in the account picker. */
    protected UUID postToBank(String templateName, LocalDate date, long amount, String description, String reference,
                              String bankAccountCode, List<String> expectedJournal) {
        openForm(templateName, date, amount, description, reference);
        UUID lineId = jdbcTemplate.queryForObject("""
                SELECT id FROM journal_template_lines WHERE id_journal_template = ? AND account_hint = 'BANK'""",
                UUID.class, templateId(templateName));
        Locator input = page.locator("#accountMapping_" + lineId);
        input.click();
        input.fill(bankAccountCode);
        input.locator("xpath=..").locator("[data-testid='account-picker-result']")
                .filter(new Locator.FilterOptions().setHasText(bankAccountCode + " — ")).click();
        assertThat(page.locator("input[name='accountMapping[" + lineId + "]']")).hasValue(jdbcTemplate.queryForObject(
                "SELECT id::text FROM chart_of_accounts WHERE account_code = ?", String.class, bankAccountCode));
        return submitAndVerify(templateName, date, amount, description, reference, expectedJournal);
    }

    private void openForm(String templateName, LocalDate date, long amount, String description, String reference) {
        navigateTo("/transactions/new?templateId=" + templateId(templateName));
        waitForPageLoad();
        page.locator("#transactionDate").fill(date.toString());
        page.locator("#amount").fill(String.valueOf(amount));
        page.locator("#description").fill(description);
        page.locator("#referenceNumber").fill(reference);
    }

    private UUID submitAndVerify(String templateName, LocalDate date, long amount, String description,
                                 String reference, List<String> expectedJournal) {
        page.locator("#btn-simpan-posting").click();
        page.waitForURL(TRANSACTION_DETAIL_URL);
        waitForPageLoad();
        Matcher matcher = TRANSACTION_DETAIL_URL.matcher(page.url());
        assertTrue(matcher.matches(), page.url());
        UUID id = UUID.fromString(matcher.group(1));

        Map<String, Object> tx = jdbcTemplate.queryForMap("""
                SELECT t.status, t.transaction_number, t.transaction_date, t.amount, t.description, t.reference_number,
                       jt.template_name
                FROM transactions t JOIN journal_templates jt ON jt.id = t.id_journal_template WHERE t.id = ?""", id);
        assertEquals(String.join("|", "POSTED", templateName, date.toString(), amount + ".00", description, reference),
                String.join("|", (String) tx.get("status"), (String) tx.get("template_name"),
                        ((java.sql.Date) tx.get("transaction_date")).toLocalDate().toString(),
                        ((BigDecimal) tx.get("amount")).setScale(2).toPlainString(),
                        (String) tx.get("description"), (String) tx.get("reference_number")));
        assertEquals(expectedJournal, journalLines(id), templateName + " journal");

        assertThat(page.getByTestId("status-posted")).hasText("Transaksi Berhasil Diposting");
        assertThat(page.locator("#transaction-number")).hasText((String) tx.get("transaction_number"));
        assertThat(page.getByTestId("template-link")).hasText(templateName);
        List<String> expectedRows = expectedJournal.stream().map(line -> {
            String[] p = line.split(":");
            String money = rupiah(new BigDecimal(p[2]).longValueExact());
            return p[1] + "|" + (p[0].equals("DEBIT") ? money + "|-" : "-|" + money);
        }).sorted().toList();
        assertEquals(expectedRows, journalRows(), templateName + " journal rows on detail page");
        assertThat(page.locator("#total-debit")).hasText(rupiah(amount));
        assertThat(page.locator("#total-credit")).hasText(rupiah(amount));
        return id;
    }

    protected UUID templateId(String name) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM journal_templates WHERE template_name = ? AND is_current_version = true", UUID.class, name);
    }

    protected List<String> journalLines(UUID transactionId) {
        return jdbcTemplate.queryForList("""
                SELECT CASE WHEN je.debit_amount > 0 THEN 'DEBIT:' || coa.account_code || ':' || je.debit_amount
                            ELSE 'CREDIT:' || coa.account_code || ':' || je.credit_amount END
                FROM journal_entries je JOIN chart_of_accounts coa ON coa.id = je.id_account
                WHERE je.id_transaction = ? ORDER BY je.debit_amount = 0, coa.account_code""", String.class, transactionId);
    }

    /** Debit-positive balance of an account over the given posted transactions. */
    protected long balance(String accountCode, List<UUID> transactionIds) {
        long total = 0;
        for (UUID id : transactionIds) {
            total += jdbcTemplate.queryForObject("""
                    SELECT COALESCE(SUM(je.debit_amount - je.credit_amount), 0)
                    FROM journal_entries je JOIN chart_of_accounts coa ON coa.id = je.id_account
                    WHERE je.id_transaction = ? AND coa.account_code = ?""", BigDecimal.class, id, accountCode)
                    .longValueExact();
        }
        return total;
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

    protected static String journal(String position, String accountCode, long amount) {
        return position + ":" + accountCode + ":" + amount + ".00";
    }

    protected static String rupiah(long amount) {
        return String.format(ID, "Rp %,d", amount);
    }

    /** Parses a rendered amount ("850.820.000", "Rp 1.000", "(8.880.000)", "-"). */
    protected static long amount(Locator locator) {
        String text = locator.innerText().trim();
        String digits = text.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            if (text.equals("-")) {
                return 0L;
            }
            throw new IllegalStateException("Not an amount: '" + text + "'");
        }
        long value = Long.parseLong(digits);
        return text.startsWith("(") || text.startsWith("-") ? -value : value;
    }

    protected Locator reportRow(String rowClass, String accountName) {
        return page.locator("." + rowClass).filter(new Locator.FilterOptions()
                .setHas(page.locator(".account-name").getByText(accountName, new Locator.GetByTextOptions().setExact(true))));
    }
}
