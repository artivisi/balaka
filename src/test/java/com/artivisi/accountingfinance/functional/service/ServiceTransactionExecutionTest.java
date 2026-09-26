package com.artivisi.accountingfinance.functional.service;

import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.functional.page.TransactionDetailPage;
import com.artivisi.accountingfinance.functional.page.TransactionFormPage;
import com.artivisi.accountingfinance.functional.util.CsvLoader;
import com.artivisi.accountingfinance.functional.util.TransactionRow;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Service industry transactions entered through the transaction form, one per row of
 * service/transactions.csv, each saved and posted and checked on its detail page and in the database.
 * The list test then finds each posted transaction in the transaction list.
 *
 * The CSV dates (2024) are kept because the form and result screenshots of the user manual show them.
 * Reports over these transactions are verified by ServiceReportsTest on its own year.
 */
@DisplayName("Service Industry - Transaction Execution")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Import(ServiceTestDataInitializer.class)
class ServiceTransactionExecutionTest extends PlaywrightTestBase {

    private static final int CSV_ROWS = 5;
    private static final Map<TransactionRow, UUID> POSTED = new LinkedHashMap<>();

    @Autowired private JournalTemplateRepository templateRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private JournalEntryRepository journalEntryRepository;

    @TestFactory
    @Order(1)
    @DisplayName("Execute transactions from CSV")
    Stream<DynamicTest> executeTransactionsFromCsv() {
        List<TransactionRow> transactions = CsvLoader.loadTransactions("service/transactions.csv");
        assertEquals(CSV_ROWS, transactions.size(), "service/transactions.csv row count");

        return transactions.stream()
            .sorted(Comparator.comparing(TransactionRow::sequence))
            .map(tx -> DynamicTest.dynamicTest(
                "Tx " + tx.sequence() + ": " + tx.description(),
                () -> executeTransaction(tx)
            ));
    }

    private void executeTransaction(TransactionRow tx) {
        loginAsAdmin();
        UUID templateId = templateRepository.findByTemplateNameAndIsCurrentVersionTrue(tx.templateName())
            .orElseThrow(() -> new IllegalStateException("Seeded template missing: " + tx.templateName()))
            .getId();
        TransactionFormPage form = new TransactionFormPage(page, baseUrl());

        form.navigateWithTemplate(templateId)
            .fillDate(tx.date())
            .fillInputs(tx.inputs())
            .fillDescription(tx.description())
            .fillReferenceNumber(tx.reference())
            .selectProject(tx.project());

        if (tx.screenshot()) {
            takeManualScreenshot("service/" + String.format("%02d", tx.sequence()) + "-" + slugify(tx.description()) + "-form");
        }

        TransactionDetailPage detail = form.saveAndPost();

        if (tx.screenshot()) {
            takeManualScreenshot("service/" + String.format("%02d", tx.sequence()) + "-" + slugify(tx.description()) + "-result");
        }

        detail.verifyStatusPosted()
            .verifyJournalEntryCount(2)
            .verifyJournalEntryAccountCode(0, tx.expectedDebitAccount())
            .verifyJournalEntryDebit(0, tx.expectedAmount())
            .verifyJournalEntryAccountCode(1, tx.expectedCreditAccount())
            .verifyJournalEntryCredit(1, tx.expectedAmount())
            .verifyTotalDebit(tx.expectedAmount())
            .verifyTotalCredit(tx.expectedAmount());

        UUID id = UUID.fromString(page.url().substring(page.url().lastIndexOf('/') + 1));
        BigDecimal amount = amount(tx);
        Transaction saved = transactionRepository.findById(id).orElseThrow();
        assertEquals(TransactionStatus.POSTED, saved.getStatus());
        assertEquals(LocalDate.parse(tx.date()), saved.getTransactionDate());
        assertEquals(tx.description(), saved.getDescription());
        assertEquals(tx.reference(), saved.getReferenceNumber());
        assertEquals(0, amount.compareTo(saved.getAmount()));
        assertEquals(templateId, saved.getJournalTemplate().getId());
        assertThat(page.locator("#transaction-number")).hasText(saved.getTransactionNumber());

        List<JournalEntry> entries = journalEntryRepository.findByTransactionIdWithAccount(id);
        assertEquals(2, entries.size());
        JournalEntry debit = entries.stream().filter(e -> e.getDebitAmount().signum() > 0).findFirst().orElseThrow();
        JournalEntry credit = entries.stream().filter(e -> e.getCreditAmount().signum() > 0).findFirst().orElseThrow();
        assertEquals(tx.expectedDebitAccount(), debit.getAccount().getAccountCode());
        assertEquals(0, amount.compareTo(debit.getDebitAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(debit.getCreditAmount()));
        assertEquals(tx.expectedCreditAccount(), credit.getAccount().getAccountCode());
        assertEquals(0, amount.compareTo(credit.getCreditAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(credit.getDebitAmount()));

        POSTED.put(tx, id);
    }

    @Test
    @Order(2)
    @DisplayName("Transaction list finds each posted CSV transaction by number with date, description, reference, amount and status")
    void verifyTransactionList() {
        assertEquals(CSV_ROWS, POSTED.size(), "CSV transactions not posted; run the whole class");
        loginAsAdmin();

        navigateTo("/transactions");
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Transaksi");
        takeManualScreenshot("service/transaction-list");

        DateTimeFormatter rowDate = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.of("id", "ID"));
        for (Map.Entry<TransactionRow, UUID> posted : POSTED.entrySet()) {
            TransactionRow tx = posted.getKey();
            Transaction saved = transactionRepository.findById(posted.getValue()).orElseThrow();
            String number = saved.getTransactionNumber();

            navigateTo("/transactions?search=" + number);
            waitForPageLoad();

            assertThat(page.locator("[data-testid^='trx-row-']")).hasCount(1);
            Locator row = page.locator("[data-testid='trx-row-" + number + "']");
            assertThat(row.locator("a[href='/transactions/" + saved.getId() + "']").first()).hasText(number);
            assertThat(row).containsText(LocalDate.parse(tx.date()).format(rowDate));
            assertThat(row).containsText(tx.description());
            assertThat(row).containsText(tx.reference());
            assertThat(row).containsText("Rp " + tx.expectedAmount());
            assertThat(row).containsText("POSTED");
        }
    }

    private static BigDecimal amount(TransactionRow tx) {
        for (String part : tx.inputs().split("\\|")) {
            String[] kv = part.split(":", 2);
            if (kv[0].equals("amount")) {
                return new BigDecimal(kv[1]);
            }
        }
        throw new IllegalStateException("No amount input in CSV row " + tx.sequence());
    }

    private String slugify(String text) {
        return text.toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-|-$", "")
            .substring(0, Math.min(30, text.replaceAll("[^a-z0-9]+", "-").length()));
    }
}
