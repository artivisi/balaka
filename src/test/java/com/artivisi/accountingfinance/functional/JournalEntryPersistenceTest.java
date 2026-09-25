package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.service.JournalEntryService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.RequestOptions;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Journal entry lookups, general ledger (service, /journals page, /journals/api) on posted
 * free-form journal entries. Each test uses its own month of 2059, which is reserved for this class.
 */
@DisplayName("Journal Entry - Persistence Tests")
@Import(ServiceTestDataInitializer.class)
class JournalEntryPersistenceTest extends PlaywrightTestBase {

    private static final String BANK = "1.1.02";
    private static final String REVENUE = "4.1.02";
    private static final String TELECOM = "5.1.06";

    @Autowired private JournalEntryService journalEntryService;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private String csrfHeader;
    private String csrfToken;

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
        csrfHeader = page.locator("meta[name='_csrf_header']").getAttribute("content");
        csrfToken = page.locator("meta[name='_csrf']").getAttribute("content");
    }

    @Test
    @DisplayName("Seeded journal lines are found by id, journal number and transaction; unknown keys fail")
    void shouldFindSeededJournalLines() {
        UUID transactionId = transactionRepository.findByTransactionNumber("SVC-TRX-002")
                .orElseThrow(() -> new IllegalStateException("Seeded transaction missing: SVC-TRX-002")).getId();

        inTx(() -> {
            JournalEntry credit = journalEntryService.findByJournalNumber("JRN-SVC-002-C");
            assertThat(credit.getAccount().getAccountCode()).isEqualTo(REVENUE);
            assertThat(credit.getCreditAmount()).isEqualByComparingTo("196200000");
            assertThat(credit.getDebitAmount()).isEqualByComparingTo("0");
            assertThat(credit.getTransaction().getId()).isEqualTo(transactionId);
            assertThat(journalEntryService.findById(credit.getId()).getJournalNumber()).isEqualTo("JRN-SVC-002-C");

            assertThat(journalEntryService.findAllByJournalNumber("JRN-SVC-002-D"))
                    .extracting(e -> e.getAccount().getAccountCode(), JournalEntry::getDebitAmount)
                    .containsExactly(tuple(BANK, new BigDecimal("196200000.00")));
            assertThat(journalEntryService.findByTransactionId(transactionId))
                    .extracting(JournalEntry::getJournalNumber)
                    .containsExactly("JRN-SVC-002-C", "JRN-SVC-002-D");
            return null;
        });

        assertThatThrownBy(() -> journalEntryService.findById(UUID.randomUUID()))
                .isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> journalEntryService.findByJournalNumber("JE-9999-9999"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Journal entry not found with number: JE-9999-9999");
        assertThat(journalEntryService.findByTransactionId(UUID.randomUUID())).isEmpty();
        assertThatThrownBy(() -> journalEntryService.getGeneralLedger(UUID.randomUUID(), LocalDate.of(2059, 1, 1), LocalDate.of(2059, 1, 31)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("General ledger carries the opening balance, sums posted lines in range and excludes drafts, voided and out-of-range entries")
    void shouldBuildGeneralLedgerFromPostedEntries() {
        String marker = "GL" + uid();
        UUID a = postJournal("2059-01-05", marker + " A", BANK, REVENUE, 3_000_000);
        UUID b = postJournal("2059-01-20", marker + " B", TELECOM, BANK, 400_000);
        postJournal("2059-02-03", marker + " C", BANK, REVENUE, 100_000);
        createJournal("2059-01-10", marker + " draft", BANK, REVENUE, 999_000);
        UUID voided = postJournal("2059-01-12", marker + " void", BANK, REVENUE, 777_000);
        assertThat(page.request().post(baseUrl() + "/transactions/api/" + voided + "/void",
                withCsrf().setData(Map.of("reason", "DUPLICATE", "notes", "Dobel"))).status()).isEqualTo(200);

        LocalDate janStart = LocalDate.of(2059, 1, 1);
        LocalDate janEnd = LocalDate.of(2059, 1, 31);
        UUID bank = account(BANK);
        var before = journalEntryService.getGeneralLedger(bank, LocalDate.of(1900, 1, 1), LocalDate.of(2058, 12, 31));
        assertThat(before.openingBalance()).isEqualByComparingTo("0");

        var jan = journalEntryService.getGeneralLedger(bank, janStart, janEnd);
        BigDecimal opening = before.closingBalance();
        assertThat(jan.openingBalance()).isEqualByComparingTo(opening);
        assertThat(jan.totalDebit()).isEqualByComparingTo("3000000");
        assertThat(jan.totalCredit()).isEqualByComparingTo("400000");
        assertThat(jan.entries()).extracting(i -> i.entry().getTransaction().getId(), i -> i.entry().getDebitAmount(),
                        i -> i.entry().getCreditAmount(), i -> i.runningBalance())
                .containsExactly(
                        tuple(a, new BigDecimal("3000000.00"), new BigDecimal("0.00"), opening.add(new BigDecimal("3000000"))),
                        tuple(b, new BigDecimal("0.00"), new BigDecimal("400000.00"), opening.add(new BigDecimal("2600000"))));
        assertThat(jan.closingBalance()).isEqualByComparingTo(opening.add(new BigDecimal("2600000")));
        assertThat(journalEntryService.getGeneralLedger(bank, LocalDate.of(2059, 2, 1), LocalDate.of(2059, 2, 28)).openingBalance())
                .isEqualByComparingTo(jan.closingBalance());

        var revenue = journalEntryService.getGeneralLedger(account(REVENUE), janStart, janEnd);
        assertThat(revenue.account().getNormalBalance().name()).isEqualTo("CREDIT");
        assertThat(revenue.totalCredit()).isEqualByComparingTo("3000000");
        assertThat(revenue.closingBalance()).isEqualByComparingTo(revenue.openingBalance().add(new BigDecimal("3000000")));

        assertThat(journalEntryService.findByAccountAndDateRange(bank, janStart, janEnd))
                .extracting(JournalEntry::getDebitAmount).containsExactly(new BigDecimal("3000000.00"), new BigDecimal("0.00"));
        assertThat(journalEntryService.findAllByDateRange(janStart, janEnd, PageRequest.of(0, 50)).getContent())
                .extracting(JournalEntry::getDebitAmount, JournalEntry::getCreditAmount)
                .containsExactly(
                        tuple(new BigDecimal("3000000.00"), new BigDecimal("0.00")),
                        tuple(new BigDecimal("0.00"), new BigDecimal("3000000.00")),
                        tuple(new BigDecimal("400000.00"), new BigDecimal("0.00")),
                        tuple(new BigDecimal("0.00"), new BigDecimal("400000.00")));
    }

    @Test
    @DisplayName("Ledger page shows totals, balances and rows linking to their transactions; paging and search keep the running balance")
    void shouldShowLedgerPageWithPagingAndSearch() {
        String marker = "LP" + uid();
        UUID one = postJournal("2059-03-01", marker + " satu", BANK, REVENUE, 100_000);
        UUID two = postJournal("2059-03-02", marker + " dua", BANK, REVENUE, 200_000);
        UUID three = postJournal("2059-03-03", marker + " tiga", BANK, REVENUE, 300_000);
        UUID bank = account(BANK);
        LocalDate start = LocalDate.of(2059, 3, 1);
        LocalDate end = LocalDate.of(2059, 3, 31);
        BigDecimal opening = journalEntryService.getGeneralLedger(bank, start, end).openingBalance();

        var secondPage = journalEntryService.getGeneralLedgerPaged(bank, start, end, null, PageRequest.of(1, 1));
        assertThat(secondPage.totalElements()).isEqualTo(3);
        assertThat(secondPage.totalPages()).isEqualTo(3);
        assertThat(secondPage.hasNext()).isTrue();
        assertThat(secondPage.hasPrevious()).isTrue();
        assertThat(secondPage.entries()).extracting(i -> i.entry().getTransaction().getId(), i -> i.runningBalance())
                .containsExactly(tuple(two, opening.add(new BigDecimal("300000"))));
        assertThat(secondPage.closingBalance()).isEqualByComparingTo(opening.add(new BigDecimal("600000")));

        var searched = journalEntryService.getGeneralLedgerPaged(bank, start, end, marker + " dua", PageRequest.of(0, 20));
        assertThat(searched.entries()).extracting(i -> i.entry().getTransaction().getId()).containsExactly(two);

        navigateTo("/journals?startDate=2059-03-01&endDate=2059-03-31");
        waitForPageLoad();
        assertThat(page.locator("#no-account-message")).isVisible();
        page.locator("#account-filter").selectOption(bank.toString());
        assertThat(page.locator("#account-name")).hasText("1.1.02 - Bank BCA");
        assertThat(page.locator("#opening-balance")).hasText(format(opening));
        assertThat(page.locator("#total-debit")).hasText("600,000");
        assertThat(page.locator("#total-credit")).hasText("0");
        assertThat(page.locator("#entry-count-debit")).hasText("3 entri");
        assertThat(page.locator("#closing-balance")).hasText(format(opening.add(new BigDecimal("600000"))));
        for (UUID id : List.of(one, two, three)) {
            assertThat(page.locator("#entries-content a[href='/transactions/" + id + "']")).hasCount(1);
        }

        page.locator("#search-input").fill(marker + " dua");
        page.locator("#btn-apply").click();
        assertThat(page.locator("#entries-content a[href^='/transactions/']")).hasCount(1);
        page.locator("#entries-content a[href='/transactions/" + two + "']").click();
        page.waitForURL(baseUrl() + "/transactions/" + two);
        assertThat(page.getByTestId("status-posted")).isVisible();
        assertThat(page.locator("#transaction-detail-content")).containsText(marker + " dua");

        navigateTo("/journals");
        waitForPageLoad();
        page.locator("#btn-add-journal").click();
        page.waitForURL(baseUrl() + "/transactions/journal-entry/new");
    }

    @Test
    @DisplayName("/journals/api returns a transaction's journal lines and an account ledger")
    void shouldServeJournalApiByTransactionAndLedger() throws Exception {
        String marker = "JA" + uid();
        UUID posted = postJournal("2059-04-07", marker + " posted", BANK, REVENUE, 1_250_000);
        UUID draft = createJournal("2059-04-08", marker + " draft", BANK, REVENUE, 50_000);
        UUID bank = account(BANK);

        JsonNode lines = getJson("/journals/api/by-transaction/" + posted);
        assertThat(lines).hasSize(2);
        String base = lines.get(0).get("journalNumber").asText().replaceAll("-01$", "");
        assertThat(lines.get(0).get("journalNumber").asText()).isEqualTo(base + "-01");
        assertThat(lines.get(0).get("debitAmount").decimalValue()).isEqualByComparingTo("1250000");
        assertThat(lines.get(1).get("journalNumber").asText()).isEqualTo(base + "-02");
        assertThat(lines.get(1).get("creditAmount").decimalValue()).isEqualByComparingTo("1250000");
        assertThat(getJson("/journals/api/by-transaction/" + draft)).hasSize(2);
        assertThat(getJson("/journals/api/by-transaction/" + draft).get(0).get("journalNumber").isNull()).isTrue();

        JsonNode ledger = getJson("/journals/api/ledger/" + bank + "?startDate=2059-04-01&endDate=2059-04-30");
        assertThat(ledger.get("account").get("accountCode").asText()).isEqualTo(BANK);
        assertThat(ledger.get("totalDebit").decimalValue()).isEqualByComparingTo("1250000");
        assertThat(ledger.get("totalCredit").decimalValue()).isEqualByComparingTo("0");
        assertThat(ledger.get("entries")).hasSize(1);
        assertThat(ledger.get("entries").get(0).get("entry").get("journalNumber").asText()).isEqualTo(base + "-01");
        assertThat(ledger.get("closingBalance").decimalValue())
                .isEqualByComparingTo(ledger.get("openingBalance").decimalValue().add(new BigDecimal("1250000")));
        assertThat(ledger.get("entries").get(0).get("runningBalance").decimalValue())
                .isEqualByComparingTo(ledger.get("closingBalance").decimalValue());

        assertThat(page.request().get(baseUrl() + "/journals/api/ledger/" + UUID.randomUUID()
                + "?startDate=2059-04-01&endDate=2059-04-30").status()).isEqualTo(404);
    }

    // ==================== helpers ====================

    private UUID postJournal(String date, String description, String debitCode, String creditCode, long amount) {
        UUID id = createJournal(date, description, debitCode, creditCode, amount);
        APIResponse response = page.request().post(baseUrl() + "/transactions/api/" + id + "/post", withCsrf());
        assertThat(response.status()).as("post " + description + ": " + response.text()).isEqualTo(200);
        assertThat(transactionRepository.findById(id).orElseThrow().getStatus()).isEqualTo(TransactionStatus.POSTED);
        return id;
    }

    private UUID createJournal(String date, String description, String debitCode, String creditCode, long amount) {
        List<Map<String, Object>> lines = new ArrayList<>();
        lines.add(Map.of("accountId", account(debitCode).toString(), "debit", amount, "credit", 0));
        lines.add(Map.of("accountId", account(creditCode).toString(), "debit", 0, "credit", amount));
        APIResponse response = page.request().post(baseUrl() + "/transactions/journal-entry",
                withCsrf().setData(Map.of("transactionDate", date, "description", description, "lines", lines)));
        assertThat(response.status()).as("create " + description + ": " + response.text()).isEqualTo(200);
        try {
            return UUID.fromString(objectMapper.readTree(response.text()).get("transactionId").asText());
        } catch (Exception e) {
            throw new IllegalStateException("Unparseable journal entry response: " + response.text(), e);
        }
    }

    private JsonNode getJson(String path) throws Exception {
        APIResponse response = page.request().get(baseUrl() + path);
        assertThat(response.status()).as(path + ": " + response.text()).isEqualTo(200);
        return objectMapper.readTree(response.text());
    }

    private RequestOptions withCsrf() {
        return RequestOptions.create().setHeader(csrfHeader, csrfToken);
    }

    private UUID account(String code) {
        return accountRepository.findByAccountCode(code)
                .orElseThrow(() -> new IllegalStateException("Seeded account missing: " + code)).getId();
    }

    private static String format(BigDecimal amount) {
        return new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US)).format(amount);
    }

    private <T> T inTx(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
