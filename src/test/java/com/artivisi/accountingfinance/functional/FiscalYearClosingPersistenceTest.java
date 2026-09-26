package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.JournalTemplateLine;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.service.FiscalYearClosingService;
import com.artivisi.accountingfinance.service.TransactionService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fiscal year closing executed and reversed through /reports/fiscal-closing, verified against
 * the persisted closing journals. Preview math is covered by FiscalYearClosingServiceTest, the
 * preview page by ServiceReportsTest and the preview API by ReportApiPersistenceTest.
 *
 * Each test posts its own revenue (4.1.01) and expense (5.1.07) into a year reserved for it:
 * {@link #CLOSED_YEAR} stays closed, {@link #REVERSED_YEAR} is closed and reversed,
 * {@link #EMPTY_YEAR} has no postings.
 */
@DisplayName("Fiscal Year Closing - Persistence Tests")
@Import(ServiceTestDataInitializer.class)
class FiscalYearClosingPersistenceTest extends PlaywrightTestBase {

    private static final int CLOSED_YEAR = 2101;
    private static final int REVERSED_YEAR = 2102;
    private static final int EMPTY_YEAR = 2103;

    @Autowired private FiscalYearClosingService fiscalYearClosingService;
    @Autowired private JournalEntryRepository journalEntryRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private JournalTemplateRepository templateRepository;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private TransactionService transactionService;

    @Test
    @DisplayName("Executing from the page posts three closing journals that zero revenue and expense into retained earnings")
    void shouldExecuteClosingFromPage() {
        postYear(CLOSED_YEAR, "50000000", "12000000");
        loginAsAdmin();
        openYear(CLOSED_YEAR);

        assertThat(page.locator("#year")).hasValue(String.valueOf(CLOSED_YEAR));
        assertThat(page.locator("#status-open")).hasText("Tahun " + CLOSED_YEAR + " belum ditutup");
        assertThat(page.locator("#card-revenue div").nth(1)).hasText("50,000,000");
        assertThat(page.locator("#card-expense div").nth(1)).hasText("12,000,000");
        assertThat(page.locator("#card-net-income div").nth(1)).hasText("38,000,000");

        AtomicReference<String> confirm = new AtomicReference<>();
        page.onceDialog(dialog -> {
            confirm.set(dialog.message());
            dialog.accept();
        });
        page.locator("#btn-execute").click();
        waitForPageLoad();

        assertThat(confirm.get()).isEqualTo("Apakah Anda yakin ingin menutup tahun buku " + CLOSED_YEAR + "?");
        assertThat(page).hasURL(baseUrl() + "/reports/fiscal-closing?year=" + CLOSED_YEAR);
        assertThat(page.locator("#success-message")).hasText("Jurnal penutup berhasil dibuat: 6 jurnal");
        assertThat(page.locator("#status-closed")).hasText("Tahun " + CLOSED_YEAR + " sudah ditutup");
        assertThat(page.locator("#btn-execute")).hasCount(0);
        assertThat(page.locator("#btn-reverse")).isVisible();

        assertThat(closingLines(CLOSED_YEAR)).containsExactlyInAnyOrder(
                "CLOSING-" + CLOSED_YEAR + "-01 4.1.01 D50000000",
                "CLOSING-" + CLOSED_YEAR + "-01 3.2.02 C50000000",
                "CLOSING-" + CLOSED_YEAR + "-02 5.1.07 C12000000",
                "CLOSING-" + CLOSED_YEAR + "-02 3.2.02 D12000000",
                "CLOSING-" + CLOSED_YEAR + "-03 3.2.02 D38000000",
                "CLOSING-" + CLOSED_YEAR + "-03 3.2.01 C38000000");
        List<Transaction> closings = closingTransactions(CLOSED_YEAR);
        assertThat(closings).hasSize(3).allSatisfy(t -> {
            assertThat(t.getStatus()).isEqualTo(TransactionStatus.POSTED);
            assertThat(t.getTransactionDate()).isEqualTo(LocalDate.of(CLOSED_YEAR, 12, 31));
            assertThat(t.getClosingEntry()).isTrue();
        });

        String csrf = page.locator("meta[name='_csrf']").getAttribute("content");
        APIResponse again = page.request().post(baseUrl() + "/reports/fiscal-closing/" + CLOSED_YEAR + "/execute",
                RequestOptions.create().setHeader("X-CSRF-TOKEN", csrf));
        assertThat(again.status()).isEqualTo(200);
        assertThat(again.url()).isEqualTo(baseUrl() + "/reports/fiscal-closing?year=" + CLOSED_YEAR);
        assertThat(closingTransactions(CLOSED_YEAR)).hasSize(3);
        assertThat(again.text()).contains("Jurnal penutup untuk tahun " + CLOSED_YEAR + " sudah ada. "
                + "Hapus jurnal penutup yang ada terlebih dahulu jika ingin membuat ulang.");
    }

    @Test
    @DisplayName("Reversing from the page voids the closing journals with the given reason and reopens the year")
    void shouldReverseClosingFromPage() {
        postYear(REVERSED_YEAR, "20000000", "5000000");
        loginAsAdmin();
        openYear(REVERSED_YEAR);
        page.onceDialog(dialog -> dialog.accept());
        page.locator("#btn-execute").click();
        waitForPageLoad();
        assertThat(page.locator("#status-closed")).isVisible();
        Set<UUID> closingIds = closingTransactions(REVERSED_YEAR).stream().map(Transaction::getId).collect(Collectors.toSet());
        assertThat(closingIds).hasSize(3);

        assertThat(page.locator("#reverseModal")).isHidden();
        page.locator("#btn-reverse").click();
        assertThat(page.locator("#reverseModal")).isVisible();
        page.locator("#reason").fill("Koreksi pendapatan " + REVERSED_YEAR);
        page.locator("#btn-confirm-reverse").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/reports/fiscal-closing?year=" + REVERSED_YEAR);
        assertThat(page.locator("#success-message")).hasText("Jurnal penutup berhasil dibatalkan: 6 jurnal");
        assertThat(page.locator("#status-open")).hasText("Tahun " + REVERSED_YEAR + " belum ditutup");
        assertThat(page.locator("#card-net-income div").nth(1)).hasText("15,000,000");
        assertThat(page.locator("#btn-execute")).isEnabled();

        for (UUID id : closingIds) {
            Transaction voided = transactionRepository.findById(id).orElseThrow();
            assertThat(voided.getStatus()).isEqualTo(TransactionStatus.VOID);
            assertThat(voided.getVoidNotes()).isEqualTo("Pembatalan Jurnal Penutup: Koreksi pendapatan " + REVERSED_YEAR);
            assertThat(voided.getVoidedBy()).isEqualTo("admin");
            assertThat(journalEntryRepository.findByTransactionIdOrderByJournalNumberAsc(id))
                    .hasSize(2)
                    .allSatisfy(je -> assertThat(je.getVoidReason())
                            .isEqualTo("Pembatalan Jurnal Penutup: Koreksi pendapatan " + REVERSED_YEAR));
        }
        assertThat(fiscalYearClosingService.hasClosingEntries(REVERSED_YEAR)).isFalse();
    }

    @Test
    @DisplayName("A year without postings shows no closing journals and a disabled execute button")
    void shouldShowNothingToCloseForEmptyYear() {
        assertThat(transactionRepository.findByTransactionDateBetweenOrderByTransactionDateDesc(
                LocalDate.of(EMPTY_YEAR, 1, 1), LocalDate.of(EMPTY_YEAR, 12, 31)))
                .as("Another test posted into reserved empty year " + EMPTY_YEAR).isEmpty();
        loginAsAdmin();
        openYear(EMPTY_YEAR);

        assertThat(page.locator("#status-open")).hasText("Tahun " + EMPTY_YEAR + " belum ditutup");
        assertThat(page.locator("#card-revenue div").nth(1)).hasText("0");
        assertThat(page.locator("#card-net-income div").nth(1)).hasText("0");
        assertThat(page.locator("#preview-section"))
                .containsText("Tidak ada jurnal penutup yang diperlukan (tidak ada transaksi pada tahun ini)");
        assertThat(page.locator("#btn-execute")).isDisabled();
    }

    @Test
    @DisplayName("Reversing a year that was never closed shows the error and changes nothing")
    void shouldRejectReverseWithoutClosing() {
        loginAsAdmin();
        openYear(EMPTY_YEAR);
        String csrf = page.locator("meta[name='_csrf']").getAttribute("content");

        APIResponse response = page.request().post(baseUrl() + "/reports/fiscal-closing/" + EMPTY_YEAR + "/reverse",
                RequestOptions.create().setHeader("X-CSRF-TOKEN", csrf)
                        .setForm(FormData.create().set("reason", "tidak ada")));

        assertThat(response.url()).isEqualTo(baseUrl() + "/reports/fiscal-closing?year=" + EMPTY_YEAR);
        assertThat(response.text()).contains("Tidak ada jurnal penutup untuk tahun " + EMPTY_YEAR);
        assertThat(fiscalYearClosingService.hasClosingEntries(EMPTY_YEAR)).isFalse();
    }

    private void openYear(int year) {
        navigateTo("/reports/fiscal-closing?year=" + year);
        waitForPageLoad();
    }

    /** Revenue on 03-10 and expense on 06-15 of the year, both through bank 1.1.02. */
    private void postYear(int year, String revenue, String expense) {
        assertThat(transactionRepository.findByTransactionDateBetweenOrderByTransactionDateDesc(
                LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)))
                .as("Another test posted into reserved closing year " + year).isEmpty();
        post("Pendapatan Jasa Training", LocalDate.of(year, 3, 10), revenue, "Pendapatan training " + year);
        post("Bayar Beban Listrik", LocalDate.of(year, 6, 15), expense, "Listrik " + year);
    }

    private void post(String templateName, LocalDate date, String amount, String description) {
        JournalTemplate template = templateRepository.findByTemplateNameWithLines(templateName)
                .orElseThrow(() -> new IllegalStateException("Seeded template missing: " + templateName));
        JournalTemplateLine bankLine = template.getLines().stream()
                .filter(l -> "BANK".equals(l.getAccountHint())).findFirst()
                .orElseThrow(() -> new IllegalStateException("Template has no BANK line: " + templateName));
        UUID bank = accountRepository.findByAccountCode("1.1.02")
                .orElseThrow(() -> new IllegalStateException("Seeded account 1.1.02 missing")).getId();

        Transaction transaction = new Transaction();
        transaction.setJournalTemplate(template);
        transaction.setTransactionDate(date);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setDescription(description);
        Transaction created = transactionService.create(transaction, Map.of(bankLine.getId(), bank));
        transactionService.post(created.getId(), "admin");
    }

    private List<String> closingLines(int year) {
        return closingTransactions(year).stream()
                .flatMap(t -> journalEntryRepository.findByTransactionIdWithAccount(t.getId()).stream()
                        .map(je -> t.getReferenceNumber() + " " + je.getAccount().getAccountCode() + " "
                                + (je.getDebitAmount().signum() > 0
                                        ? "D" + je.getDebitAmount().stripTrailingZeros().toPlainString()
                                        : "C" + je.getCreditAmount().stripTrailingZeros().toPlainString())))
                .toList();
    }

    private List<Transaction> closingTransactions(int year) {
        return fiscalYearClosingService.getClosingEntries(year).stream()
                .map(je -> je.getTransaction().getId()).distinct()
                .map(id -> transactionRepository.findById(id).orElseThrow())
                .toList();
    }
}
