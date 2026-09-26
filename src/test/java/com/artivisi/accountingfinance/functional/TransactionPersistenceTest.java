package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.FiscalPeriod;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.JournalTemplateLine;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.FiscalPeriodStatus;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.enums.VoidReason;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.FiscalPeriodRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.service.JournalEntryService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Transaction lifecycle through the web UI and the session JSON endpoints: template-driven
 * journals, numbering, void reversals, state rules, closed fiscal periods, list filters.
 * All transactions are dated in 2058, which is reserved for this class.
 */
@DisplayName("Transaction - Persistence Tests")
@Import(ServiceTestDataInitializer.class)
class TransactionPersistenceTest extends PlaywrightTestBase {

    private static final String TPL_PPN_PPH23 = "Pendapatan Jasa + PPN + PPh 23";
    private static final String TPL_TELEKOM = "Bayar Beban Telekomunikasi";
    private static final String TPL_TRAINING = "Pendapatan Jasa Training";
    private static final Pattern TRX_NUMBER = Pattern.compile("TRX-(\\d{4})-(\\d{4})");
    private static final Pattern JOURNAL_NUMBER = Pattern.compile("(JE-\\d{4})-(\\d{4})-(\\d{2})");

    @Autowired private TransactionRepository transactionRepository;
    @Autowired private JournalTemplateRepository templateRepository;
    @Autowired private JournalEntryRepository journalEntryRepository;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private FiscalPeriodRepository fiscalPeriodRepository;
    @Autowired private JournalEntryService journalEntryService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PlatformTransactionManager transactionManager;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private String csrfHeader;
    private String csrfToken;

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
        readCsrf();
    }

    @Test
    @DisplayName("Draft created in the form previews the template formulas; posting writes balanced journal lines with those amounts")
    void shouldCreateDraftInFormAndPostTemplateFormulas() {
        JournalTemplate template = template(TPL_PPN_PPH23);
        String description = "Jasa PPN PPh23 " + uid();

        navigateTo("/transactions/new?templateId=" + template.getId());
        waitForPageLoad();
        page.locator("#transactionDate").fill("2058-01-10");
        page.locator("#amount").fill("10000000");
        page.locator("#description").fill(description);
        pickAccount(lineId(template, "BANK"), "1.1.02");
        pickAccount(lineId(template, "PENDAPATAN"), "4.1.02");
        page.locator("#btn-simpan-draft").click();
        page.waitForURL(Pattern.compile(".*/transactions/[0-9a-f-]{36}$"));
        UUID id = UUID.fromString(page.url().substring(page.url().lastIndexOf('/') + 1));

        assertThat(page.getByTestId("status-draft")).isVisible();
        Transaction draft = transactionRepository.findById(id).orElseThrow();
        assertThat(draft.getStatus()).isEqualTo(TransactionStatus.DRAFT);
        assertThat(draft.getTransactionNumber()).isNull();
        assertThat(draft.getDescription()).isEqualTo(description);
        assertThat(draft.getTransactionDate()).isEqualTo(LocalDate.of(2058, 1, 10));
        assertThat(draft.getAmount()).isEqualByComparingTo("10000000");
        assertThat(journalEntryRepository.findByTransactionIdOrderByJournalNumberAsc(id)).isEmpty();
        assertThat(mappedAccountCodes(id)).containsExactlyInAnyOrder("1.1.02", "4.1.02");

        Locator preview = page.locator("#journal-preview-content");
        assertThat(preview.locator("#entry-account-code-0")).hasText("1.1.02");
        assertThat(preview.locator("#entry-debit-0")).containsText("10.900.000");
        assertThat(preview.locator("#entry-account-code-1")).hasText("1.1.26");
        assertThat(preview.locator("#entry-debit-1")).containsText("200.000");
        assertThat(preview.locator("#entry-account-code-2")).hasText("4.1.02");
        assertThat(preview.locator("#entry-credit-2")).containsText("10.000.000");
        assertThat(preview.locator("#entry-account-code-3")).hasText("2.1.03");
        assertThat(preview.locator("#entry-credit-3")).containsText("1.100.000");
        assertThat(preview.locator("#total-debit")).containsText("11.100.000");
        assertThat(preview.locator("#total-credit")).containsText("11.100.000");

        page.onceDialog(dialog -> dialog.accept());
        page.locator("#btn-post").click();
        assertThat(page.getByTestId("status-posted")).isVisible();

        Transaction posted = transactionRepository.findById(id).orElseThrow();
        assertThat(posted.getStatus()).isEqualTo(TransactionStatus.POSTED);
        assertThat(posted.getPostedBy()).isEqualTo("admin");
        assertThat(posted.getTransactionNumber()).matches(TRX_NUMBER);
        assertThat(page.locator("#transaction-number")).hasText(posted.getTransactionNumber());

        List<JournalEntry> entries = journalEntryRepository.findByTransactionIdWithAccount(id);
        assertThat(entries).extracting(e -> e.getAccount().getAccountCode(), JournalEntry::getDebitAmount, JournalEntry::getCreditAmount)
                .containsExactly(
                        tuple("1.1.02", new BigDecimal("10900000.00"), new BigDecimal("0.00")),
                        tuple("1.1.26", new BigDecimal("200000.00"), new BigDecimal("0.00")),
                        tuple("4.1.02", new BigDecimal("0.00"), new BigDecimal("10000000.00")),
                        tuple("2.1.03", new BigDecimal("0.00"), new BigDecimal("1100000.00")));
        String base = journalBase(entries.get(0).getJournalNumber());
        assertThat(entries).extracting(JournalEntry::getJournalNumber)
                .containsExactly(base + "-01", base + "-02", base + "-03", base + "-04");
        assertThat(page.locator("#journal-totals #total-debit")).hasText("Rp 11.100.000");
        assertThat(page.locator("#journal-totals #total-credit")).hasText("Rp 11.100.000");
        assertThat(page.locator("#entry-account-code-0")).hasText("1.1.02");
        assertThat(page.locator("#entry-account-code-3")).hasText("2.1.03");
    }

    @Test
    @DisplayName("Consecutive postings get consecutive transaction and journal numbers")
    void shouldNumberPostingsSequentially() {
        UUID first = createDraft(TPL_TELEKOM, "2058-01-15", 120000, "Telepon A " + uid(), Map.of("BANK", "1.1.02"));
        UUID second = createDraft(TPL_TELEKOM, "2058-01-15", 130000, "Telepon B " + uid(), Map.of("BANK", "1.1.02"));

        assertThat(post(first).status()).isEqualTo(200);
        assertThat(post(second).status()).isEqualTo(200);

        Matcher firstTrx = matcher(TRX_NUMBER, transactionRepository.findById(first).orElseThrow().getTransactionNumber());
        Matcher secondTrx = matcher(TRX_NUMBER, transactionRepository.findById(second).orElseThrow().getTransactionNumber());
        assertThat(secondTrx.group(1)).isEqualTo(firstTrx.group(1));
        assertThat(Integer.parseInt(secondTrx.group(2))).isEqualTo(Integer.parseInt(firstTrx.group(2)) + 1);

        Matcher firstJe = matcher(JOURNAL_NUMBER, journalEntryRepository.findByTransactionIdOrderByJournalNumberAsc(first).get(0).getJournalNumber());
        Matcher secondJe = matcher(JOURNAL_NUMBER, journalEntryRepository.findByTransactionIdOrderByJournalNumberAsc(second).get(0).getJournalNumber());
        assertThat(secondJe.group(1)).isEqualTo(firstJe.group(1));
        assertThat(Integer.parseInt(secondJe.group(2))).isEqualTo(Integer.parseInt(firstJe.group(2)) + 1);
    }

    @Test
    @DisplayName("Void from the void page adds reversal lines, marks the transaction VOID and drops it from the ledger")
    void shouldVoidWithReversalAndExcludeFromLedger() {
        UUID id = createDraft(TPL_TELEKOM, "2058-02-05", 750000, "Internet Feb " + uid(), Map.of("BANK", "1.1.02"));
        assertThat(post(id).status()).isEqualTo(200);
        UUID expenseAccount = account("5.1.06");
        LocalDate start = LocalDate.of(2058, 2, 1);
        LocalDate end = LocalDate.of(2058, 2, 28);
        assertThat(journalEntryService.getGeneralLedger(expenseAccount, start, end).totalDebit()).isEqualByComparingTo("750000");

        navigateTo("/transactions/" + id);
        waitForPageLoad();
        page.locator("a[href='/transactions/" + id + "/void']").click();
        waitForPageLoad();
        page.locator("input[name='voidReason'][value='INPUT_ERROR']").check();
        page.locator("#voidNotes").fill("Salah input nominal");
        page.locator("input[name='confirmVoid']").check();
        page.onceDialog(dialog -> dialog.accept());
        page.locator("#btn-void").click();
        assertThat(page.getByTestId("status-void")).isVisible();
        assertThat(page.locator("#transaction-detail-content")).containsText("Alasan: Kesalahan Input");

        Transaction voided = transactionRepository.findById(id).orElseThrow();
        assertThat(voided.getStatus()).isEqualTo(TransactionStatus.VOID);
        assertThat(voided.getVoidReason()).isEqualTo(VoidReason.INPUT_ERROR);
        assertThat(voided.getVoidNotes()).isEqualTo("Salah input nominal");
        assertThat(voided.getVoidedBy()).isEqualTo("admin");

        inTx(() -> {
            List<JournalEntry> entries = journalEntryRepository.findByTransactionIdWithAccount(id);
            assertThat(entries).hasSize(4);
            List<JournalEntry> originals = entries.stream().filter(e -> !e.getIsReversal()).toList();
            List<JournalEntry> reversals = entries.stream().filter(JournalEntry::getIsReversal).toList();
            assertThat(originals).extracting(e -> e.getAccount().getAccountCode(), JournalEntry::getDebitAmount, JournalEntry::getCreditAmount)
                    .containsExactlyInAnyOrder(
                            tuple("5.1.06", new BigDecimal("750000.00"), new BigDecimal("0.00")),
                            tuple("1.1.02", new BigDecimal("0.00"), new BigDecimal("750000.00")));
            assertThat(reversals).extracting(e -> e.getAccount().getAccountCode(), JournalEntry::getDebitAmount, JournalEntry::getCreditAmount)
                    .containsExactlyInAnyOrder(
                            tuple("5.1.06", new BigDecimal("0.00"), new BigDecimal("750000.00")),
                            tuple("1.1.02", new BigDecimal("750000.00"), new BigDecimal("0.00")));
            for (JournalEntry reversal : reversals) {
                assertThat(reversal.getReversedEntry().getAccount().getId()).isEqualTo(reversal.getAccount().getId());
                assertThat(originals).contains(reversal.getReversedEntry());
            }
            return null;
        });

        var ledger = journalEntryService.getGeneralLedger(expenseAccount, start, end);
        assertThat(ledger.totalDebit()).isEqualByComparingTo("0");
        assertThat(ledger.entries()).isEmpty();
    }

    @Test
    @DisplayName("Only drafts can be posted, edited or deleted; only posted transactions can be voided")
    void shouldEnforceStatusRules() {
        UUID draft = createDraft(TPL_TELEKOM, "2058-01-22", 90000, "Status draft " + uid(), Map.of("BANK", "1.1.02"));
        UUID posted = createDraft(TPL_TELEKOM, "2058-01-22", 95000, "Status posted " + uid(), Map.of("BANK", "1.1.02"));
        assertThat(post(posted).status()).isEqualTo(200);
        String postedNumber = transactionRepository.findById(posted).orElseThrow().getTransactionNumber();

        assertThat(voidTransaction(draft).status()).isEqualTo(409);
        assertThat(transactionRepository.findById(draft).orElseThrow().getStatus()).isEqualTo(TransactionStatus.DRAFT);

        assertThat(post(posted).status()).isEqualTo(409);
        assertThat(page.request().delete(baseUrl() + "/transactions/api/" + posted, withCsrf()).status()).isEqualTo(409);
        assertThat(jsonPut("/transactions/api/" + posted, transactionBody(TPL_TELEKOM, "2058-01-22", 1, "Ubah", Map.of("BANK", "1.1.02"))).status())
                .isEqualTo(409);
        Transaction afterRejected = transactionRepository.findById(posted).orElseThrow();
        assertThat(afterRejected.getStatus()).isEqualTo(TransactionStatus.POSTED);
        assertThat(afterRejected.getTransactionNumber()).isEqualTo(postedNumber);
        assertThat(afterRejected.getAmount()).isEqualByComparingTo("95000");
        assertThat(journalEntryRepository.findByTransactionIdOrderByJournalNumberAsc(posted)).hasSize(2);

        navigateTo("/transactions/" + posted + "/edit");
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/transactions/" + posted);
        assertThat(page.locator("#btn-post")).hasCount(0);
        assertThat(page.locator("#btn-delete")).hasCount(0);

        navigateTo("/transactions/" + draft + "/void");
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/transactions/" + draft);

        assertThat(voidTransaction(posted).status()).isEqualTo(200);
        assertThat(voidTransaction(posted).status()).isEqualTo(409);
        assertThat(journalEntryRepository.findByTransactionIdOrderByJournalNumberAsc(posted)).hasSize(4);
    }

    @Test
    @DisplayName("Deleting a draft from its detail page removes it")
    void shouldDeleteDraftFromDetailPage() {
        UUID id = createDraft(TPL_TELEKOM, "2058-01-25", 45000, "Hapus draft " + uid(), Map.of("BANK", "1.1.02"));

        navigateTo("/transactions/" + id);
        waitForPageLoad();
        page.onceDialog(dialog -> dialog.accept());
        page.locator("#btn-delete").click();
        page.waitForURL(baseUrl() + "/transactions");

        assertThat(transactionRepository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("Editing a draft saves amount, description and a changed account; posting uses the new account")
    void shouldEditDraftIncludingAccountMapping() {
        JournalTemplate template = template(TPL_TELEKOM);
        UUID id = createDraft(TPL_TELEKOM, "2058-01-20", 500000, "Telepon Jan " + uid(), Map.of("BANK", "1.1.02"));
        String newDescription = "Telepon Jan revisi " + uid();

        navigateTo("/transactions/" + id + "/edit");
        waitForPageLoad();
        assertThat(page.locator("#amount")).hasValue("500.000");
        page.locator("#amount").fill("650000");
        page.locator("#description").fill(newDescription);
        pickAccount(lineId(template, "BANK"), "1.1.03");
        page.locator("#btn-simpan-draft").click();
        page.waitForURL(baseUrl() + "/transactions/" + id);

        Transaction edited = transactionRepository.findById(id).orElseThrow();
        assertThat(edited.getStatus()).isEqualTo(TransactionStatus.DRAFT);
        assertThat(edited.getAmount()).isEqualByComparingTo("650000");
        assertThat(edited.getDescription()).isEqualTo(newDescription);
        assertThat(mappedAccountCodes(id)).containsExactly("1.1.03");

        assertThat(post(id).status()).isEqualTo(200);
        assertThat(journalEntryRepository.findByTransactionIdWithAccount(id))
                .extracting(e -> e.getAccount().getAccountCode(), JournalEntry::getDebitAmount, JournalEntry::getCreditAmount)
                .containsExactly(
                        tuple("5.1.06", new BigDecimal("650000.00"), new BigDecimal("0.00")),
                        tuple("1.1.03", new BigDecimal("0.00"), new BigDecimal("650000.00")));
    }

    @Test
    @DisplayName("A closed fiscal period blocks posting; reopening it allows the same draft to post")
    void shouldBlockPostingInClosedFiscalPeriod() throws Exception {
        assertThat(postForm("/fiscal-periods/new", FormData.create().set("year", "2058").set("month", "6")).status())
                .isEqualTo(302);
        FiscalPeriod period = fiscalPeriodRepository.findByYearAndMonth(2058, 6)
                .orElseThrow(() -> new IllegalStateException("Fiscal period 2058-06 was not created"));
        assertThat(postForm("/fiscal-periods/" + period.getId() + "/close-month", FormData.create()).status()).isEqualTo(302);
        assertThat(fiscalPeriodRepository.findById(period.getId()).orElseThrow().getStatus()).isEqualTo(FiscalPeriodStatus.MONTH_CLOSED);

        UUID id = createDraft(TPL_TELEKOM, "2058-06-15", 210000, "Periode tutup " + uid(), Map.of("BANK", "1.1.02"));
        String closedMessage = "Cannot post journal entry. Fiscal period Juni 2058 is Tutup Bulan";
        APIResponse rejected = post(id);
        assertThat(rejected.status()).isEqualTo(409);
        assertThat(objectMapper.readTree(rejected.text()).get("message").asText()).isEqualTo(closedMessage);

        List<String> dialogs = new java.util.concurrent.CopyOnWriteArrayList<>();
        page.onDialog(dialog -> {
            dialogs.add(dialog.message());
            dialog.accept();
        });
        navigateTo("/transactions/" + id);
        waitForPageLoad();
        page.locator("#btn-post").click();
        page.waitForCondition(() -> dialogs.size() == 2);
        assertThat(dialogs).containsExactly(
                "Posting transaksi ini? Setelah diposting tidak dapat diedit.", "Gagal posting: " + closedMessage);
        Transaction blocked = transactionRepository.findById(id).orElseThrow();
        assertThat(blocked.getStatus()).isEqualTo(TransactionStatus.DRAFT);
        assertThat(blocked.getTransactionNumber()).isNull();
        assertThat(journalEntryRepository.findByTransactionIdOrderByJournalNumberAsc(id)).isEmpty();

        assertThat(postForm("/fiscal-periods/" + period.getId() + "/reopen", FormData.create().set("reason", "Koreksi")).status())
                .isEqualTo(302);
        assertThat(fiscalPeriodRepository.findById(period.getId()).orElseThrow().getStatus()).isEqualTo(FiscalPeriodStatus.OPEN);
        assertThat(post(id).status()).isEqualTo(200);
        assertThat(transactionRepository.findById(id).orElseThrow().getStatus()).isEqualTo(TransactionStatus.POSTED);
    }

    @Test
    @DisplayName("List page and /transactions/api filter by date range, status, category and search")
    void shouldFilterTransactionListAndApi() throws Exception {
        String marker = "FLT" + uid();
        UUID income = createDraft(TPL_TRAINING, "2058-03-03", 2000000, marker + " training", Map.of("BANK", "1.1.02"));
        assertThat(post(income).status()).isEqualTo(200);
        UUID expense = createDraft(TPL_TELEKOM, "2058-03-04", 300000, marker + " telepon", Map.of("BANK", "1.1.02"));
        UUID april = createDraft(TPL_TELEKOM, "2058-04-01", 310000, marker + " april", Map.of("BANK", "1.1.02"));
        String march = "startDate=2058-03-01&endDate=2058-03-31";

        navigateTo("/transactions?" + march);
        waitForPageLoad();
        assertRows(Set.of(income, expense), Set.of(april));

        page.locator("#filter-status").selectOption("DRAFT");
        assertThat(page.locator("#trx-row-" + income)).hasCount(0);
        assertThat(page.locator("#trx-row-" + expense)).isVisible();

        navigateTo("/transactions?" + march + "&status=POSTED");
        waitForPageLoad();
        assertRows(Set.of(income), Set.of(expense, april));

        navigateTo("/transactions?" + march + "&category=EXPENSE");
        waitForPageLoad();
        assertRows(Set.of(expense), Set.of(income, april));

        navigateTo("/transactions?search=" + marker);
        waitForPageLoad();
        assertRows(Set.of(income, expense, april), Set.of());

        assertThat(apiIds("/transactions/api?" + march)).containsExactlyInAnyOrder(income, expense);
        assertThat(apiIds("/transactions/api?" + march + "&status=POSTED")).containsExactly(income);
        assertThat(apiIds("/transactions/api?" + march + "&category=EXPENSE")).containsExactly(expense);
        assertThat(apiIds("/transactions/api?startDate=2058-04-01&endDate=2058-04-30&status=DRAFT")).containsExactly(april);
        assertThat(apiIds("/transactions/api/search?q=" + marker)).containsExactlyInAnyOrder(income, expense, april);

        APIResponse single = page.request().get(baseUrl() + "/transactions/api/" + income);
        assertThat(single.status()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(single.text());
        assertThat(body.get("description").asText()).isEqualTo(marker + " training");
        assertThat(body.get("status").asText()).isEqualTo("POSTED");
        assertThat(body.get("amount").decimalValue()).isEqualByComparingTo("2000000");
        assertThat(body.get("transactionNumber").asText())
                .isEqualTo(transactionRepository.findById(income).orElseThrow().getTransactionNumber());
    }

    @Test
    @DisplayName("Template search returns matching templates only")
    void shouldSearchTemplatesByName() {
        APIResponse response = page.request().get(baseUrl() + "/transactions/templates/search?q=PPN%20%2B%20PPh%2023");
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.text())
                .contains("data-testid=\"template-search-result-" + template(TPL_PPN_PPH23).getId() + "\"")
                .doesNotContain("template-search-result-" + template(TPL_TELEKOM).getId());
    }

    @Test
    @DisplayName("AUDITOR cannot create, edit, post, void or delete transactions and sees no write controls")
    void shouldRefuseTransactionWritesForAuditor() {
        UUID draft = createDraft(TPL_TELEKOM, "2058-01-28", 60000, "Auditor draft " + uid(), Map.of("BANK", "1.1.02"));
        UUID posted = createDraft(TPL_TELEKOM, "2058-01-28", 61000, "Auditor posted " + uid(), Map.of("BANK", "1.1.02"));
        assertThat(post(posted).status()).isEqualTo(200);
        String marker = "Auditor baru " + uid();
        Map<String, Object> createBody = transactionBody(TPL_TELEKOM, "2058-01-28", 1000, marker, Map.of("BANK", "1.1.02"));
        Supplier<String> state = () -> transactionRepository.findById(draft).map(t -> t.getStatus() + "|" + t.getDescription()).orElse("deleted")
                + "|" + transactionRepository.findById(posted).map(t -> t.getStatus().name()).orElse("deleted")
                + "|" + transactionRepository.count();
        String before = state.get();

        loginAsNewUser(Role.AUDITOR);

        assertThat(jsonPost("/transactions/api", createBody).status()).isEqualTo(403);
        assertThat(jsonPut("/transactions/api/" + draft, createBody).status()).isEqualTo(403);
        assertThat(post(draft).status()).isEqualTo(403);
        assertThat(page.request().post(baseUrl() + "/transactions/" + draft + "/post", withCsrf()).status()).isEqualTo(403);
        assertThat(voidTransaction(posted).status()).isEqualTo(403);
        assertThat(page.request().delete(baseUrl() + "/transactions/api/" + draft, withCsrf()).status()).isEqualTo(403);
        assertThat(page.request().delete(baseUrl() + "/transactions/" + draft, withCsrf()).status()).isEqualTo(403);
        assertThat(jsonPost("/transactions/journal-entry", Map.of("transactionDate", "2058-01-28", "description", marker,
                "lines", List.of(Map.of("accountId", account("1.1.02").toString(), "debit", 1000, "credit", 0),
                        Map.of("accountId", account("4.1.02").toString(), "debit", 0, "credit", 1000)))).status())
                .isEqualTo(403);
        assertThat(postForm("/transactions/quick", FormData.create().set("templateId", template(TPL_TELEKOM).getId().toString())
                .set("amount", "1000").set("description", marker)).status()).isEqualTo(403);
        assertThat(state.get()).isEqualTo(before);

        navigateTo("/transactions/" + draft);
        waitForPageLoad();
        assertThat(page.getByTestId("status-draft")).isVisible();
        assertThat(page.locator("#btn-post")).hasCount(0);
        assertThat(page.locator("#btn-delete")).hasCount(0);
        assertThat(page.locator("a[href='/transactions/" + draft + "/edit']")).hasCount(0);
        navigateTo("/transactions/" + posted);
        waitForPageLoad();
        assertThat(page.locator("a[href='/transactions/" + posted + "/void']")).hasCount(0);
    }

    // ==================== helpers ====================

    private void assertRows(Set<UUID> present, Set<UUID> absent) {
        for (UUID id : present) {
            assertThat(page.locator("#trx-row-" + id)).isVisible();
        }
        for (UUID id : absent) {
            assertThat(page.locator("#trx-row-" + id)).hasCount(0);
        }
    }

    private Set<UUID> apiIds(String path) throws Exception {
        APIResponse response = page.request().get(baseUrl() + path + "&size=100");
        assertThat(response.status()).as(path + ": " + response.text()).isEqualTo(200);
        Set<UUID> ids = new HashSet<>();
        for (JsonNode node : objectMapper.readTree(response.text()).get("content")) {
            ids.add(UUID.fromString(node.get("id").asText()));
        }
        return ids;
    }

    private UUID createDraft(String templateName, String date, long amount, String description, Map<String, String> accountsByHint) {
        APIResponse response = jsonPost("/transactions/api", transactionBody(templateName, date, amount, description, accountsByHint));
        assertThat(response.status()).as("create " + description + ": " + response.text()).isEqualTo(200);
        try {
            return UUID.fromString(objectMapper.readTree(response.text()).get("id").asText());
        } catch (Exception e) {
            throw new IllegalStateException("Unparseable create response: " + response.text(), e);
        }
    }

    private Map<String, Object> transactionBody(String templateName, String date, long amount, String description,
                                                Map<String, String> accountsByHint) {
        JournalTemplate template = template(templateName);
        Map<String, String> mappings = new HashMap<>();
        accountsByHint.forEach((hint, code) -> mappings.put(lineId(template, hint).toString(), account(code).toString()));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("templateId", template.getId().toString());
        body.put("transactionDate", date);
        body.put("amount", amount);
        body.put("description", description);
        body.put("accountMappings", mappings);
        return body;
    }

    private APIResponse post(UUID id) {
        return page.request().post(baseUrl() + "/transactions/api/" + id + "/post", withCsrf());
    }

    private APIResponse voidTransaction(UUID id) {
        return page.request().post(baseUrl() + "/transactions/api/" + id + "/void",
                withCsrf().setData(Map.of("reason", "INPUT_ERROR", "notes", "Salah input")));
    }

    private APIResponse jsonPost(String path, Object body) {
        return page.request().post(baseUrl() + path, withCsrf().setData(body));
    }

    private APIResponse jsonPut(String path, Object body) {
        return page.request().put(baseUrl() + path, withCsrf().setData(body));
    }

    private APIResponse postForm(String path, FormData form) {
        return page.request().post(baseUrl() + path,
                RequestOptions.create().setMaxRedirects(0).setForm(form.set("_csrf", csrfToken)));
    }

    private RequestOptions withCsrf() {
        return RequestOptions.create().setHeader(csrfHeader, csrfToken);
    }

    private void readCsrf() {
        csrfHeader = page.locator("meta[name='_csrf_header']").getAttribute("content");
        csrfToken = page.locator("meta[name='_csrf']").getAttribute("content");
    }

    private void loginAsNewUser(Role role) {
        String id = uid();
        User user = new User();
        user.setUsername("trx-" + role.name().toLowerCase() + "-" + id);
        user.setPassword(passwordEncoder.encode("password"));
        user.setFullName("Pengguna " + id);
        user.setEmail("trx-" + id + "@example.com");
        user.setActive(true);
        user.addRole(role, "system");
        userRepository.save(user);
        context.clearCookies();
        login(user.getUsername(), "password");
        readCsrf();
    }

    private void pickAccount(UUID lineId, String code) {
        Locator input = page.locator("#accountMapping_" + lineId);
        input.click();
        input.fill(code);
        input.locator("xpath=..").locator("[data-testid='account-picker-result']")
                .filter(new Locator.FilterOptions().setHasText(code + " — ")).click();
        assertThat(page.locator("input[name='accountMapping[" + lineId + "]']")).hasValue(account(code).toString());
    }

    private List<String> mappedAccountCodes(UUID transactionId) {
        return inTx(() -> transactionRepository.findById(transactionId).orElseThrow().getAccountMappings().stream()
                .map(m -> m.getAccount().getAccountCode()).toList());
    }

    private JournalTemplate template(String name) {
        JournalTemplate template = templateRepository.findByTemplateNameAndIsCurrentVersionTrue(name)
                .orElseThrow(() -> new IllegalStateException("Seeded template missing: " + name));
        return templateRepository.findByIdWithLines(template.getId()).orElseThrow();
    }

    private UUID lineId(JournalTemplate template, String hint) {
        return template.getLines().stream()
                .filter(l -> hint.equals(l.getAccountHint()))
                .map(JournalTemplateLine::getId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Template " + template.getTemplateName() + " has no line with hint " + hint));
    }

    private UUID account(String code) {
        return accountRepository.findByAccountCode(code)
                .orElseThrow(() -> new IllegalStateException("Seeded account missing: " + code)).getId();
    }

    private String journalBase(String journalNumber) {
        return matcher(JOURNAL_NUMBER, journalNumber).group(1) + "-" + matcher(JOURNAL_NUMBER, journalNumber).group(2);
    }

    private Matcher matcher(Pattern pattern, String value) {
        Matcher m = pattern.matcher(value);
        if (!m.matches()) {
            throw new AssertionError(value + " does not match " + pattern);
        }
        return m;
    }

    private <T> T inTx(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
