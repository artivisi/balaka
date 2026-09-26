package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Free-form journal entry form (/transactions/journal-entry/new). Entries are dated
 * January 2105 (reserved for this class) with unique descriptions, and verified against the
 * stored transaction and journal lines.
 */
@DisplayName("Free-Form Journal Entry UI - Functional Tests")
@Import(ServiceTestDataInitializer.class)
class JournalEntryUiTest extends PlaywrightTestBase {

    @Autowired private TransactionRepository transactionRepository;
    @Autowired private JournalEntryRepository journalEntryRepository;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void login() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("Jurnal Manual button opens an empty form dated today with two lines")
    void shouldNavigateToJournalEntryForm() {
        navigateTo("/transactions");
        waitForPageLoad();

        page.locator("[data-testid='btn-journal-entry']").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/transactions/journal-entry/new");
        assertThat(page.locator("#page-title")).hasText("Jurnal Manual");
        assertThat(page.locator("#transactionDate")).hasValue(LocalDate.now().toString());
        assertThat(page.locator("#description")).hasValue("");
        assertThat(page.locator("#category")).hasValue("");
        assertThat(page.locator(".journal-line")).hasCount(2);
        assertThat(page.locator("#total-debit")).hasText("0");
        assertThat(page.locator("#total-credit")).hasText("0");
    }

    @Test
    @DisplayName("Missing description, account or amount is reported on the form and nothing is saved")
    void shouldShowValidationErrors() {
        String description = unique("Validasi");
        openForm();
        Locator error = page.locator("div[x-text='errorMessage']");

        page.locator("#btn-save-draft").click();
        assertThat(error).hasText("Deskripsi wajib diisi");

        page.locator("#description").fill(description);
        page.locator("#btn-save-draft").click();
        assertThat(error).hasText("Baris 1: pilih akun");

        pickAccount(0, "5.1.07");
        pickAccount(1, "1.1.02");
        page.locator("#btn-save-draft").click();
        assertThat(error).hasText("Baris 1: isi debit atau kredit");

        assertThat(page).hasURL(baseUrl() + "/transactions/journal-entry/new");
        assertThat(countByDescription(description)).isZero();
    }

    @Test
    @DisplayName("Saving a draft stores a DRAFT Jurnal Manual transaction with the entered lines, unnumbered")
    void shouldCreateDraftJournalEntry() {
        String description = unique("Penyesuaian listrik");
        openForm();
        fillHeader("2105-01-15", description, "ADJUSTING");
        pickAccount(0, "5.1.07");
        debit(0, "500000");
        pickAccount(1, "1.1.02");
        credit(1, "500000");
        assertThat(page.locator("#total-debit")).hasText("500.000");
        assertThat(page.locator("#total-credit")).hasText("500.000");

        page.locator("#btn-save-draft").click();
        UUID id = waitForDetail();

        assertThat(page.getByTestId("status-draft")).isVisible();
        Transaction draft = transactionRepository.findById(id).orElseThrow();
        assertThat(draft.getStatus()).isEqualTo(TransactionStatus.DRAFT);
        assertThat(draft.getTransactionNumber()).isNull();
        assertThat(draft.getDescription()).isEqualTo(description);
        assertThat(draft.getTransactionDate()).isEqualTo(LocalDate.of(2105, 1, 15));
        assertThat(draft.getAmount()).isEqualByComparingTo("500000");
        assertThat(draft.getNotes()).isEqualTo("ADJUSTING");
        assertThat(draft.getClosingEntry()).isFalse();
        assertThat(templateName(id)).isEqualTo("Jurnal Manual");
        assertThat(lines(id)).containsExactlyInAnyOrder("5.1.07 D500000", "1.1.02 C500000");
        assertThat(journalEntryRepository.findByTransactionIdOrderByJournalNumberAsc(id))
                .allSatisfy(je -> assertThat(je.getJournalNumber()).isNull());
    }

    @Test
    @DisplayName("Save & post stores a POSTED numbered closing entry with the entered lines")
    void shouldCreateAndPostJournalEntry() {
        String description = unique("Penutup pendapatan");
        openForm();
        takeManualScreenshot("journal-entry/form-empty");
        fillHeader("2105-01-31", description, "CLOSING");
        pickAccount(0, "4.1.01");
        debit(0, "750000");
        pickAccount(1, "3.2.02");
        credit(1, "750000");
        assertThat(page.getByText("Selisih:")).isHidden();
        takeManualScreenshot("journal-entry/form-filled");

        page.locator("#btn-save-post").click();
        UUID id = waitForDetail();

        assertThat(page.getByTestId("status-posted")).isVisible();
        takeManualScreenshot("journal-entry/result-posted");
        Transaction posted = transactionRepository.findById(id).orElseThrow();
        assertThat(posted.getStatus()).isEqualTo(TransactionStatus.POSTED);
        assertThat(posted.getTransactionNumber()).isNotBlank();
        assertThat(posted.getClosingEntry()).isTrue();
        assertThat(posted.getNotes()).isEqualTo("CLOSING");
        assertThat(lines(id)).containsExactlyInAnyOrder("4.1.01 D750000", "3.2.02 C750000");
        assertThat(journalEntryRepository.findByTransactionIdOrderByJournalNumberAsc(id))
                .allSatisfy(je -> assertThat(je.getJournalNumber()).isNotBlank());
    }

    @Test
    @DisplayName("Lines can be added and removed down to two, and a three-line entry is stored with all lines")
    void shouldAddAndRemoveLines() {
        String description = unique("Tiga baris");
        openForm();
        assertThat(page.locator("[data-testid='remove-line-0']")).isHidden();

        page.getByText("Tambah Baris").click();
        page.getByText("Tambah Baris").click();
        assertThat(page.locator(".journal-line")).hasCount(4);
        page.locator("[data-testid='remove-line-3']").click();
        assertThat(page.locator(".journal-line")).hasCount(3);

        fillHeader("2105-01-20", description, "CORRECTION");
        pickAccount(0, "5.1.07");
        debit(0, "300000");
        pickAccount(1, "5.1.05");
        debit(1, "200000");
        pickAccount(2, "1.1.02");
        credit(2, "500000");
        page.locator("#btn-save-draft").click();
        UUID id = waitForDetail();

        assertThat(lines(id)).containsExactlyInAnyOrder("5.1.07 D300000", "5.1.05 D200000", "1.1.02 C500000");
        assertThat(transactionRepository.findById(id).orElseThrow().getAmount()).isEqualByComparingTo("500000");
    }

    @Test
    @DisplayName("An unbalanced entry shows the difference, is refused on save and stores nothing")
    void shouldShowUnbalancedWarning() {
        String description = unique("Tidak seimbang");
        openForm();
        fillHeader("2105-01-10", description, "");
        pickAccount(0, "5.1.07");
        debit(0, "100000");
        pickAccount(1, "1.1.02");
        credit(1, "50000");

        assertThat(page.getByText("Selisih:")).hasText("Selisih: 50.000");
        page.locator("#btn-save-draft").click();

        assertThat(page.locator("div[x-text='errorMessage']"))
                .hasText("Jurnal tidak seimbang. Total debit harus sama dengan total kredit.");
        assertThat(page).hasURL(baseUrl() + "/transactions/journal-entry/new");
        assertThat(countByDescription(description)).isZero();
    }

    private void openForm() {
        navigateTo("/transactions/journal-entry/new");
        waitForPageLoad();
    }

    private void fillHeader(String date, String description, String category) {
        page.locator("#transactionDate").fill(date);
        page.locator("#description").fill(description);
        page.locator("#category").selectOption(category);
    }

    private void pickAccount(int line, String code) {
        Locator input = page.locator("[data-testid='account-select-" + line + "']");
        input.click();
        input.fill(code);
        page.locator("[data-testid='journal-line-" + line + "'] [data-testid='account-picker-result']")
                .filter(new Locator.FilterOptions().setHasText(Pattern.compile("^\\s*" + code.replace(".", "\\.") + "\\s+—")))
                .click();
        assertThat(input).hasValue(Pattern.compile("^" + code.replace(".", "\\.") + " - .+"));
    }

    private void debit(int line, String amount) {
        page.locator("[data-testid='debit-input-" + line + "']").pressSequentially(amount);
    }

    private void credit(int line, String amount) {
        page.locator("[data-testid='credit-input-" + line + "']").pressSequentially(amount);
    }

    private UUID waitForDetail() {
        page.waitForURL(Pattern.compile(".*/transactions/[0-9a-f-]{36}$"));
        waitForPageLoad();
        return UUID.fromString(page.url().substring(page.url().lastIndexOf('/') + 1));
    }

    private List<String> lines(UUID transactionId) {
        return journalEntryRepository.findByTransactionIdWithAccount(transactionId).stream()
                .map(je -> je.getAccount().getAccountCode() + " "
                        + (je.getDebitAmount().signum() > 0
                                ? "D" + je.getDebitAmount().stripTrailingZeros().toPlainString()
                                : "C" + je.getCreditAmount().stripTrailingZeros().toPlainString()))
                .toList();
    }

    private String templateName(UUID transactionId) {
        return jdbc.queryForObject("SELECT jt.template_name FROM transactions t "
                + "JOIN journal_templates jt ON jt.id = t.id_journal_template WHERE t.id = ?", String.class, transactionId);
    }

    private long countByDescription(String description) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE description = ?", Long.class, description);
    }

    private static String unique(String prefix) {
        return prefix + " " + UUID.randomUUID().toString().substring(0, 8);
    }
}
