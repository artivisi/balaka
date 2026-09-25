package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.RequestOptions;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

/**
 * Free-form journal entry API (Phase 20). Entries are dated 2057-12-31, a date reserved for this class.
 */
@Slf4j
@DisplayName("Journal Entry API - Functional Tests")
@Import(ServiceTestDataInitializer.class)
class JournalEntryApiTest extends PlaywrightTestBase {

    private static final String DATE = "2057-12-31";

    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private JournalEntryRepository journalEntryRepository;

    private APIRequestContext apiContext;
    private ObjectMapper objectMapper;
    private String accessToken;

    private String debitAccountId;
    private String creditAccountId;
    private String thirdAccountId;
    private String headerAccountId;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        apiContext = playwright.request().newContext(new APIRequest.NewContextOptions()
                .setBaseURL(baseUrl()));

        accessToken = authenticateViaDeviceFlow();
        resolveAccountIds();
    }

    @AfterEach
    void tearDown() {
        if (apiContext != null) {
            apiContext.dispose();
        }
    }

    private void resolveAccountIds() {
        debitAccountId = accountId("1.1.02");   // Bank BCA (asset)
        creditAccountId = accountId("4.1.02");  // Pendapatan Jasa Konsultasi (revenue)
        thirdAccountId = accountId("5.1.06");   // Beban Telekomunikasi (expense)
        headerAccountId = accountId("1.1");     // Aset Lancar (header)
    }

    private String accountId(String code) {
        return accountRepository.findByAccountCode(code)
                .orElseThrow(() -> new IllegalStateException("Seeded account missing: " + code))
                .getId().toString();
    }

    // ==================== CREATE JOURNAL ENTRY ====================

    @Nested
    @DisplayName("Create Journal Entry")
    class CreateJournalEntry {

        @Test
        @DisplayName("2-line entry is stored as a DRAFT with the given lines and no journal numbers")
        void happyPath() throws Exception {
            Map<String, Object> request = buildJournalEntryRequest(
                    DATE,
                    "Year-end closing entry",
                    null,
                    List.of(
                            line(debitAccountId, 1000000, 0),
                            line(creditAccountId, 0, 1000000)
                    )
            );

            APIResponse response = post("/api/transactions/journal-entry", request);
            assertThat(response.status())
                    .as("Create journal entry: " + response.text())
                    .isEqualTo(201);

            JsonNode body = parse(response);
            assertThat(body.get("status").asText()).isEqualTo("DRAFT");
            assertThat(body.get("amount").asDouble()).isEqualTo(1000000.0);
            assertThat(body.get("description").asText()).isEqualTo("Year-end closing entry");

            UUID id = UUID.fromString(body.get("transactionId").asText());
            Transaction saved = transactionRepository.findById(id).orElseThrow();
            assertThat(saved.getTransactionDate()).isEqualTo(LocalDate.parse(DATE));
            assertThat(saved.getTransactionNumber()).isNull();
            assertThat(saved.getClosingEntry()).isFalse();
            assertThat(journalEntryRepository.findByTransactionIdWithAccount(id))
                    .extracting(e -> e.getAccount().getAccountCode(), JournalEntry::getDebitAmount,
                            JournalEntry::getCreditAmount, JournalEntry::getJournalNumber)
                    .containsExactlyInAnyOrder(
                            tuple("1.1.02", new BigDecimal("1000000.00"), new BigDecimal("0.00"), null),
                            tuple("4.1.02", new BigDecimal("0.00"), new BigDecimal("1000000.00"), null));
        }

        @Test
        @DisplayName("Multi-line: 4 lines (2 debit, 2 credit) preserves all entries")
        void multiLine() throws Exception {
            Map<String, Object> request = buildJournalEntryRequest(
                    DATE,
                    "Multi-line closing journal",
                    null,
                    List.of(
                            line(debitAccountId, 500000, 0),
                            line(thirdAccountId, 500000, 0),
                            line(creditAccountId, 0, 700000),
                            line(creditAccountId, 0, 300000)
                    )
            );

            APIResponse response = post("/api/transactions/journal-entry", request);
            assertThat(response.status())
                    .as("Multi-line create: " + response.text())
                    .isEqualTo(201);

            JsonNode body = parse(response);
            assertThat(body.get("amount").asDouble()).isEqualTo(1000000.0);
            UUID id = UUID.fromString(body.get("transactionId").asText());
            assertThat(journalEntryRepository.findByTransactionIdWithAccount(id))
                    .extracting(e -> e.getAccount().getAccountCode(), JournalEntry::getDebitAmount, JournalEntry::getCreditAmount)
                    .containsExactlyInAnyOrder(
                            tuple("1.1.02", new BigDecimal("500000.00"), new BigDecimal("0.00")),
                            tuple("5.1.06", new BigDecimal("500000.00"), new BigDecimal("0.00")),
                            tuple("4.1.02", new BigDecimal("0.00"), new BigDecimal("700000.00")),
                            tuple("4.1.02", new BigDecimal("0.00"), new BigDecimal("300000.00")));
        }

        @Test
        @DisplayName("CLOSING category is stored in notes and marks the transaction as a closing entry")
        void categoryInNotes() throws Exception {
            Map<String, Object> request = buildJournalEntryRequest(
                    DATE,
                    "Closing accrued expenses",
                    "CLOSING",
                    List.of(
                            line(debitAccountId, 250000, 0),
                            line(creditAccountId, 0, 250000)
                    )
            );

            APIResponse response = post("/api/transactions/journal-entry", request);
            assertThat(response.status()).isEqualTo(201);
            Transaction saved = transactionRepository.findById(
                    UUID.fromString(parse(response).get("transactionId").asText())).orElseThrow();
            assertThat(saved.getNotes()).isEqualTo("CLOSING");
            assertThat(saved.getClosingEntry()).isTrue();
        }
    }

    // ==================== POST JOURNAL ENTRY ====================

    @Nested
    @DisplayName("Post Journal Entry")
    class PostJournalEntry {

        @Test
        @DisplayName("Posting assigns a transaction number and one journal number with per-line suffixes")
        void fullLifecycle() throws Exception {
            Map<String, Object> request = buildJournalEntryRequest(
                    DATE,
                    "Adjusting entry for depreciation",
                    null,
                    List.of(
                            line(debitAccountId, 750000, 0),
                            line(creditAccountId, 0, 750000)
                    )
            );

            APIResponse createResponse = post("/api/transactions/journal-entry", request);
            assertThat(createResponse.status()).isEqualTo(201);
            String transactionId = parse(createResponse).get("transactionId").asText();

            APIResponse postResponse = post("/api/transactions/" + transactionId + "/post", Map.of());
            assertThat(postResponse.status())
                    .as("Post journal entry: " + postResponse.text())
                    .isEqualTo(200);

            JsonNode posted = parse(postResponse);
            assertThat(posted.get("status").asText()).isEqualTo("POSTED");
            Transaction saved = transactionRepository.findById(UUID.fromString(transactionId)).orElseThrow();
            assertThat(saved.getTransactionNumber()).matches("TRX-\\d{4}-\\d{4}")
                    .isEqualTo(posted.get("transactionNumber").asText());

            List<JournalEntry> entries = journalEntryRepository.findByTransactionIdWithAccount(UUID.fromString(transactionId));
            assertThat(entries).hasSize(2);
            String base = entries.get(0).getJournalNumber().substring(0, entries.get(0).getJournalNumber().length() - 3);
            assertThat(base).matches("JE-\\d{4}-\\d{4}");
            assertThat(entries).extracting(JournalEntry::getJournalNumber, e -> e.getAccount().getAccountCode())
                    .containsExactly(tuple(base + "-01", "1.1.02"), tuple(base + "-02", "4.1.02"));
            assertThat(post("/api/transactions/" + transactionId + "/post", Map.of()).status()).isEqualTo(409);
        }
    }

    // ==================== JOURNAL PREVIEW ====================

    @Nested
    @DisplayName("Journal Preview")
    class JournalPreview {

        @Test
        @DisplayName("Preview freeform journal entry returns pre-created entries")
        void previewFreeform() throws Exception {
            Map<String, Object> request = buildJournalEntryRequest(
                    DATE,
                    "Opening balance entry",
                    null,
                    List.of(
                            line(debitAccountId, 2000000, 0),
                            line(creditAccountId, 0, 2000000)
                    )
            );

            APIResponse createResponse = post("/api/transactions/journal-entry", request);
            assertThat(createResponse.status()).isEqualTo(201);
            String transactionId = parse(createResponse).get("transactionId").asText();

            APIResponse previewResponse = get("/api/transactions/" + transactionId + "/journal-preview");
            assertThat(previewResponse.status())
                    .as("Preview: " + previewResponse.text())
                    .isEqualTo(200);

            JsonNode preview = parse(previewResponse);
            assertThat(preview.get("valid").asBoolean()).isTrue();
            assertThat(preview.get("entries").size()).isEqualTo(2);
            assertThat(preview.get("totalDebit").asDouble()).isEqualTo(2000000.0);
            assertThat(preview.get("totalCredit").asDouble()).isEqualTo(2000000.0);
        }
    }

    // ==================== VALIDATION ERRORS ====================

    @Nested
    @DisplayName("Validation Errors")
    class ValidationErrors {

        @Test
        @DisplayName("Unbalanced entry returns 400 and saves nothing")
        void unbalanced() {
            assertRejected(List.of(line(debitAccountId, 1000000, 0), line(creditAccountId, 0, 500000)));
        }

        @Test
        @DisplayName("Both debit and credit on same line returns 400 and saves nothing")
        void bothDebitAndCredit() {
            assertRejected(List.of(line(debitAccountId, 1000000, 500000), line(creditAccountId, 0, 500000)));
        }

        @Test
        @DisplayName("Neither debit nor credit returns 400 and saves nothing")
        void neitherDebitNorCredit() {
            assertRejected(List.of(line(debitAccountId, 0, 0), line(creditAccountId, 0, 0)));
        }

        @Test
        @DisplayName("Fewer than 2 lines returns 400 and saves nothing")
        void fewerThan2Lines() {
            assertRejected(List.of(line(debitAccountId, 1000000, 0)));
        }

        @Test
        @DisplayName("Header account returns 400 and saves nothing")
        void headerAccount() {
            assertRejected(List.of(line(headerAccountId, 1000000, 0), line(creditAccountId, 0, 1000000)));
        }

        @Test
        @DisplayName("Non-existent account returns 400 and saves nothing")
        void nonExistentAccount() {
            String fakeAccountId = UUID.randomUUID().toString();
            assertRejected(List.of(line(fakeAccountId, 1000000, 0), line(creditAccountId, 0, 1000000)));
        }

        private void assertRejected(List<Map<String, Object>> lines) {
            long before = transactionRepository.count();
            APIResponse response = post("/api/transactions/journal-entry",
                    buildJournalEntryRequest(DATE, "Rejected entry", null, lines));
            assertThat(response.status()).as(response.text()).isEqualTo(400);
            assertThat(transactionRepository.count()).isEqualTo(before);
        }
    }

    // ==================== HELPER METHODS ====================

    private Map<String, Object> buildJournalEntryRequest(String date, String description,
                                                          String category, List<Map<String, Object>> lines) {
        Map<String, Object> request = new HashMap<>();
        request.put("transactionDate", date);
        request.put("description", description);
        if (category != null) {
            request.put("category", category);
        }
        request.put("lines", lines);
        return request;
    }

    private Map<String, Object> line(String accountId, double debit, double credit) {
        Map<String, Object> line = new HashMap<>();
        line.put("accountId", accountId);
        line.put("debit", debit);
        line.put("credit", credit);
        return line;
    }

    private APIResponse get(String path) {
        return apiContext.get(path,
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + accessToken));
    }

    private APIResponse post(String path, Object data) {
        return apiContext.post(path,
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + accessToken)
                        .setHeader("Content-Type", "application/json")
                        .setData(data));
    }

    private JsonNode parse(APIResponse response) throws Exception {
        return objectMapper.readTree(response.text());
    }

    private String authenticateViaDeviceFlow() throws Exception {
        Map<String, String> codeRequest = new HashMap<>();
        codeRequest.put("clientId", "journal-entry-api-test");

        APIResponse codeResponse = apiContext.post("/api/device/code",
                RequestOptions.create()
                        .setHeader("Content-Type", "application/json")
                        .setData(codeRequest));

        assertThat(codeResponse.ok()).isTrue();

        JsonNode codeData = objectMapper.readTree(codeResponse.text());
        String deviceCode = codeData.get("deviceCode").asText();
        String userCode = codeData.get("userCode").asText();

        loginAsAdmin();
        navigateTo("/device?code=" + userCode);
        waitForPageLoad();

        page.locator("input[name='deviceName']").fill("Journal Entry API Test Device");
        page.locator("button[type='submit']:has-text('Otorisasi Perangkat')").click();
        waitForPageLoad();

        Map<String, String> tokenRequest = new HashMap<>();
        tokenRequest.put("deviceCode", deviceCode);

        AtomicReference<String> tokenRef = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofSeconds(2)).until(() -> {
            APIResponse tokenResponse = apiContext.post("/api/device/token",
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setData(tokenRequest));
            if (tokenResponse.ok()) {
                JsonNode tokenData = objectMapper.readTree(tokenResponse.text());
                tokenRef.set(tokenData.get("accessToken").asText());
                return true;
            }
            return false;
        });

        return tokenRef.get();
    }
}
