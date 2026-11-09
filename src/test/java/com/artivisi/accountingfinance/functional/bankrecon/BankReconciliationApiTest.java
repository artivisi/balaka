package com.artivisi.accountingfinance.functional.bankrecon;

import com.artivisi.accountingfinance.entity.BankReconciliation;
import com.artivisi.accountingfinance.entity.BankStatementItem;
import com.artivisi.accountingfinance.entity.BankStatementParserConfig;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.CompanyBankAccount;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.JournalTemplateLine;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.MatchType;
import com.artivisi.accountingfinance.enums.ReconciliationStatus;
import com.artivisi.accountingfinance.enums.StatementItemMatchStatus;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.repository.BankReconciliationRepository;
import com.artivisi.accountingfinance.repository.BankStatementItemRepository;
import com.artivisi.accountingfinance.repository.BankStatementParserConfigRepository;
import com.artivisi.accountingfinance.repository.BankStatementRepository;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.ReconciliationItemRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.service.ChartOfAccountService;
import com.artivisi.accountingfinance.service.CompanyBankAccountService;
import com.artivisi.accountingfinance.service.DeviceAuthService;
import com.artivisi.accountingfinance.service.TransactionService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.FilePayload;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REST API for bank reconciliation (/api/bank-reconciliation, issue #38): parser configs, statement
 * import and items, reconciliation create, auto/manual match, bank-only / book-only, unmatch,
 * transaction from a statement item, completion, and the summary / statement / outstanding reports.
 *
 * Every test creates its own GL bank account and company bank account, posts its own book
 * transactions to that GL account and imports its own CSV, so balances and matches only involve
 * that test's data. Dates are in 2035 (reserved for this class). Scope denial per endpoint is
 * covered by ApiAuthorizationTest.
 */
@DisplayName("Bank Reconciliation API")
@Import(BankReconTestDataInitializer.class)
class BankReconciliationApiTest extends PlaywrightTestBase {

    private static final String BASE = "/api/bank-reconciliation";
    private static final String CSV_HEADER = "Tanggal,Keterangan,Cabang,Debit,Credit,Saldo\n";
    private static final String BCA_CONFIG = "BCA - CSV Standar";
    private static final String INCOME_TEMPLATE = "Pendapatan Jasa Training"; // BANK D / 4.1.01 C
    private static final String RENT_TEMPLATE = "Bayar Beban Sewa";           // 5.1.05 D / BANK C
    private static final String POWER_TEMPLATE = "Bayar Beban Listrik";       // 5.1.07 D / BANK C
    private static final String BANK_FEE_TEMPLATE = "Beban Admin Bank";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired private UserRepository userRepository;
    @Autowired private DeviceAuthService deviceAuthService;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private ChartOfAccountService accountService;
    @Autowired private CompanyBankAccountService bankAccountService;
    @Autowired private JournalTemplateRepository templateRepository;
    @Autowired private TransactionService transactionService;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private BankStatementParserConfigRepository parserConfigRepository;
    @Autowired private BankStatementRepository statementRepository;
    @Autowired private BankStatementItemRepository statementItemRepository;
    @Autowired private BankReconciliationRepository reconciliationRepository;
    @Autowired private ReconciliationItemRepository reconciliationItemRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private APIRequestContext api;
    private String token;

    private record Bank(String name, UUID id, UUID glAccountId) {}

    @BeforeEach
    void setUpApi() {
        // RateLimitFilter allows 100 /api requests per client IP per minute; each test gets its own address.
        api = playwright.request().newContext(new APIRequest.NewContextOptions().setBaseURL(baseUrl())
                .setExtraHTTPHeaders(Map.of("X-Forwarded-For",
                        "10.38." + RANDOM.nextInt(256) + "." + RANDOM.nextInt(256))));
        token = serviceToken("bank-recon:read,bank-recon:write");
    }

    @AfterEach
    void tearDownApi() {
        api.dispose();
    }

    // ==================== Parser configs ====================

    @Test
    @DisplayName("Custom parser config is created, listed, persisted with its columns and separators, and drives an import")
    void customParserConfigDrivesImport() {
        String name = "API Parser " + shortId();
        Map<String, Object> request = new HashMap<>();
        request.put("bankType", "CUSTOM");
        request.put("configName", name);
        request.put("description", "Titik koma, desimal koma");
        request.put("dateColumn", 0);
        request.put("descriptionColumn", 1);
        request.put("debitColumn", 2);
        request.put("creditColumn", 3);
        request.put("balanceColumn", 4);
        request.put("dateFormat", "yyyy-MM-dd");
        request.put("delimiter", ";");
        request.put("skipHeaderRows", 2);
        request.put("encoding", "UTF-8");
        request.put("decimalSeparator", ",");
        request.put("thousandSeparator", ".");

        APIResponse created = post(BASE + "/parser-configs", request);
        assertThat(created.status()).as(created.text()).isEqualTo(201);
        JsonNode body = read(created);
        UUID configId = UUID.fromString(body.get("id").asText());
        assertThat(body.get("bankType").asText()).isEqualTo("CUSTOM");
        assertThat(body.get("configName").asText()).isEqualTo(name);
        assertThat(body.get("description").asText()).isEqualTo("Titik koma, desimal koma");

        JsonNode listed = findById(read(get(BASE + "/parser-configs")), configId);
        assertThat(listed.get("configName").asText()).isEqualTo(name);
        assertThat(listed.get("bankType").asText()).isEqualTo("CUSTOM");

        BankStatementParserConfig saved = parserConfigRepository.findById(configId).orElseThrow();
        assertThat(saved.getDateColumn()).isZero();
        assertThat(saved.getDescriptionColumn()).isEqualTo(1);
        assertThat(saved.getDebitColumn()).isEqualTo(2);
        assertThat(saved.getCreditColumn()).isEqualTo(3);
        assertThat(saved.getBalanceColumn()).isEqualTo(4);
        assertThat(saved.getDateFormat()).isEqualTo("yyyy-MM-dd");
        assertThat(saved.getDelimiter()).isEqualTo(";");
        assertThat(saved.getSkipHeaderRows()).isEqualTo(2);
        assertThat(saved.getDecimalSeparator()).isEqualTo(",");
        assertThat(saved.getThousandSeparator()).isEqualTo(".");
        assertThat(saved.getIsSystem()).isFalse();
        assertThat(saved.getActive()).isTrue();

        Bank bank = newBank();
        String csv = "Rekening Koran Februari 2035\n"
                + "Tanggal;Uraian;Debet;Kredit;Saldo\n"
                + "2035-02-03;Setoran klien Gamma;;1.500.000,50;1.500.000,50\n"
                + "2035-02-14;Bayar vendor Delta;250.000,25;;1.250.000,25\n";
        APIResponse imported = importStatement(bank, configId, "2035-02-01", "2035-02-28",
                "0", "1250000.25", "custom.csv", csv);
        assertThat(imported.status()).as(imported.text()).isEqualTo(201);
        JsonNode statement = read(imported);
        assertThat(statement.get("totalItems").asInt()).isEqualTo(2);
        assertAmount(statement.get("totalCredit"), "1500000.50");
        assertAmount(statement.get("totalDebit"), "250000.25");

        JsonNode items = read(get(BASE + "/statements/" + statement.get("id").asText() + "/items"));
        assertThat(items.size()).isEqualTo(2);
        assertThat(items.get(0).get("lineNumber").asInt()).isEqualTo(3);
        assertThat(items.get(0).get("transactionDate").asText()).isEqualTo("2035-02-03");
        assertThat(items.get(0).get("description").asText()).isEqualTo("Setoran klien Gamma");
        assertThat(items.get(0).get("debitAmount").isNull()).isTrue();
        assertAmount(items.get(0).get("creditAmount"), "1500000.50");
        assertAmount(items.get(0).get("balance"), "1500000.50");
        assertThat(items.get(1).get("lineNumber").asInt()).isEqualTo(4);
        assertAmount(items.get(1).get("debitAmount"), "250000.25");
        assertThat(items.get(1).get("creditAmount").isNull()).isTrue();
        assertAmount(items.get(1).get("balance"), "1250000.25");
    }

    @Test
    @DisplayName("Parser config: bean validation, unknown bank type and duplicate name return 400 and store nothing")
    void parserConfigValidation() {
        Map<String, Object> invalid = new HashMap<>();
        invalid.put("bankType", "BCA");
        invalid.put("configName", " ");
        invalid.put("dateColumn", -1);
        invalid.put("descriptionColumn", 1);
        APIResponse response = post(BASE + "/parser-configs", invalid);
        assertThat(response.status()).as(response.text()).isEqualTo(400);
        JsonNode error = read(response);
        assertThat(error.get("error").asText()).isEqualTo("VALIDATION_ERROR");
        List<String> fields = new ArrayList<>();
        error.get("fieldErrors").fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("configName", "dateColumn", "dateFormat");

        String name = "API Parser " + shortId();
        APIResponse unknownType = post(BASE + "/parser-configs", Map.of("bankType", "BANK_ANTAH", "configName", name,
                "dateColumn", 0, "descriptionColumn", 1, "dateFormat", "dd/MM/yyyy"));
        assertThat(unknownType.status()).as(unknownType.text()).isEqualTo(400);
        assertThat(read(unknownType).get("error").asText()).isEqualTo("INVALID_REQUEST");
        assertThat(parserConfigRepository.existsByConfigName(name)).isFalse();

        Map<String, Object> valid = Map.of("bankType", "MANDIRI", "configName", name,
                "dateColumn", 0, "descriptionColumn", 1, "dateFormat", "dd/MM/yyyy");
        assertThat(post(BASE + "/parser-configs", valid).status()).isEqualTo(201);
        APIResponse duplicate = post(BASE + "/parser-configs", valid);
        assertThat(duplicate.status()).as(duplicate.text()).isEqualTo(400);
        assertThat(read(duplicate).get("message").asText()).isEqualTo("Nama konfigurasi sudah digunakan: " + name);
        assertThat(parserConfigRepository.findAllByOrderByConfigNameAsc())
                .filteredOn(c -> name.equals(c.getConfigName())).hasSize(1);
    }

    // ==================== Statements ====================

    @Test
    @DisplayName("Import with the seeded BCA config returns totals; statement and items read back, filtered by match status")
    void importAndReadStatement() {
        Bank bank = newBank();
        APIResponse response = importStatement(bank, bcaConfigId(), "2035-01-01", "2035-01-31",
                "5000000", "6185000", "bca-jan-2035.csv", CSV_HEADER
                        + "03/01/2035,\"Transfer Masuk Klien Alpha\",0316,,1500000.00,6500000.00\n"
                        + "10/01/2035,\"Transfer Keluar Hosting\",0316,300000.00,,6200000.00\n"
                        + "31/01/2035,\"Biaya Admin Bank\",0316,15000.00,,6185000.00\n");
        assertThat(response.status()).as(response.text()).isEqualTo(201);
        JsonNode created = read(response);
        UUID statementId = UUID.fromString(created.get("id").asText());
        assertStatement(created, bank);

        assertStatement(read(get(BASE + "/statements/" + statementId)), bank);
        assertThat(statementRepository.findByBankAccountIdWithRelations(bank.id())).hasSize(1);

        JsonNode items = read(get(BASE + "/statements/" + statementId + "/items"));
        assertThat(items.size()).isEqualTo(3);
        assertItem(items.get(0), 2, "2035-01-03", "Transfer Masuk Klien Alpha", null, "1500000.00", "6500000.00");
        assertItem(items.get(1), 3, "2035-01-10", "Transfer Keluar Hosting", "300000.00", null, "6200000.00");
        assertItem(items.get(2), 4, "2035-01-31", "Biaya Admin Bank", "15000.00", null, "6185000.00");
        for (JsonNode item : items) {
            assertThat(item.get("matchStatus").asText()).isEqualTo("UNMATCHED");
            assertThat(item.get("matchType").isNull()).isTrue();
        }

        assertThat(read(get(BASE + "/statements/" + statementId + "/items?matchStatus=UNMATCHED")).size()).isEqualTo(3);
        assertThat(read(get(BASE + "/statements/" + statementId + "/items?matchStatus=MATCHED")).size()).isZero();
        APIResponse badFilter = get(BASE + "/statements/" + statementId + "/items?matchStatus=SEBAGIAN");
        assertThat(badFilter.status()).as(badFilter.text()).isEqualTo(400);
        assertThat(read(badFilter).get("error").asText()).isEqualTo("INVALID_REQUEST");
    }

    @Test
    @DisplayName("Import errors: unknown bank account or parser 404, bad row / empty file / missing parameter 400, nothing stored")
    void importErrors() {
        Bank bank = newBank();
        UUID bca = bcaConfigId();
        String row = "03/01/2035,\"Transfer Masuk\",0316,,1000.00,1000.00\n";

        APIResponse unknownBank = importStatement(new Bank("x", UUID.randomUUID(), UUID.randomUUID()), bca,
                "2035-01-01", "2035-01-31", "0", "1000", "a.csv", CSV_HEADER + row);
        assertThat(unknownBank.status()).as(unknownBank.text()).isEqualTo(404);
        assertThat(read(unknownBank).get("error").asText()).isEqualTo("NOT_FOUND");

        UUID missingConfig = UUID.randomUUID();
        APIResponse unknownConfig = importStatement(bank, missingConfig,
                "2035-01-01", "2035-01-31", "0", "1000", "b.csv", CSV_HEADER + row);
        assertThat(unknownConfig.status()).as(unknownConfig.text()).isEqualTo(404);
        assertThat(read(unknownConfig).get("message").asText())
                .isEqualTo("Parser config not found with id: " + missingConfig);

        APIResponse badDate = importStatement(bank, bca, "2035-01-01", "2035-01-31", "0", "1000", "c.csv",
                CSV_HEADER + row + "2035-01-04,\"Tanggal salah\",0316,,1000.00,2000.00\n");
        assertThat(badDate.status()).as(badDate.text()).isEqualTo(400);
        assertThat(read(badDate).get("message").asText())
                .startsWith("Gagal parsing baris 3: Format tanggal tidak valid: '2035-01-04' (expected: dd/MM/yyyy)");

        APIResponse headerOnly = importStatement(bank, bca, "2035-01-01", "2035-01-31", "0", "0", "d.csv",
                CSV_HEADER);
        assertThat(headerOnly.status()).as(headerOnly.text()).isEqualTo(400);
        assertThat(read(headerOnly).get("message").asText()).isEqualTo("File CSV tidak mengandung data transaksi");

        APIResponse missingPeriodEnd = api.post(BASE + "/statements/import", bearer().setMultipart(FormData.create()
                .set("bankAccountId", bank.id().toString())
                .set("parserConfigId", bca.toString())
                .set("periodStart", "2035-01-01")
                .set("file", new FilePayload("e.csv", "text/csv", (CSV_HEADER + row).getBytes(StandardCharsets.UTF_8)))));
        assertThat(missingPeriodEnd.status()).as(missingPeriodEnd.text()).isEqualTo(400);
        assertThat(read(missingPeriodEnd).get("message").asText()).isEqualTo("Required parameter 'periodEnd' is missing");

        assertThat(statementRepository.findByBankAccountIdWithRelations(bank.id())).isEmpty();

        UUID missingStatement = UUID.randomUUID();
        APIResponse notFound = get(BASE + "/statements/" + missingStatement);
        assertThat(notFound.status()).isEqualTo(404);
        assertThat(read(notFound).get("message").asText()).isEqualTo("Bank statement not found with id: " + missingStatement);
    }

    // ==================== Reconciliations ====================

    @Test
    @DisplayName("Auto-match pairs exact, one-day-off and keyword items; unmatched-book lists only the leftover book transaction")
    void autoMatch() {
        Bank bank = newBank();
        UUID alpha = postBook(bank, INCOME_TEMPLATE, "2035-03-05", "1000000", "Pendapatan training Alpha");
        UUID hosting = postBook(bank, RENT_TEMPLATE, "2035-03-10", "250000", "Sewa server hosting");
        UUID power = postBook(bank, POWER_TEMPLATE, "2035-03-20", "375000", "Pembayaran listrik PLN Maret");
        UUID beta = postBook(bank, INCOME_TEMPLATE, "2035-03-25", "500000", "Pendapatan training Beta");
        UUID statementId = importOk(bank, "2035-03-01", "2035-03-31", "0", "360000", CSV_HEADER
                + "05/03/2035,\"Transfer Masuk Klien Alpha\",0316,,1000000.00,1000000.00\n"
                + "11/03/2035,\"Transfer Keluar Hosting\",0316,250000.00,,750000.00\n"
                + "22/03/2035,\"Tagihan PLN listrik kantor\",0316,375000.00,,375000.00\n"
                + "31/03/2035,\"Biaya Admin Bank\",0316,15000.00,,360000.00\n");

        APIResponse createResponse = post(BASE + "/reconciliations",
                Map.of("bankStatementId", statementId.toString(), "notes", "Rekon Maret 2035"));
        assertThat(createResponse.status()).as(createResponse.text()).isEqualTo(201);
        JsonNode recon = read(createResponse);
        UUID reconId = UUID.fromString(recon.get("id").asText());
        assertThat(recon.get("bankAccountId").asText()).isEqualTo(bank.id().toString());
        assertThat(recon.get("bankName").asText()).isEqualTo(bank.name());
        assertThat(recon.get("bankStatementId").asText()).isEqualTo(statementId.toString());
        assertThat(recon.get("periodStart").asText()).isEqualTo("2035-03-01");
        assertThat(recon.get("periodEnd").asText()).isEqualTo("2035-03-31");
        assertThat(recon.get("status").asText()).isEqualTo("DRAFT");
        // 1,000,000 - 250,000 - 375,000 + 500,000
        assertAmount(recon.get("bookBalance"), "875000");
        assertAmount(recon.get("bankBalance"), "360000");
        assertCounts(recon, 4, 0, 4, 0);
        assertThat(reconciliationRepository.findByIdWithRelations(reconId).orElseThrow().getNotes())
                .isEqualTo("Rekon Maret 2035");

        assertThat(ids(read(get(BASE + "/reconciliations/" + reconId + "/unmatched-book"))))
                .containsExactlyInAnyOrder(alpha, hosting, power, beta);

        APIResponse autoMatch = post(BASE + "/reconciliations/" + reconId + "/auto-match", Map.of());
        assertThat(autoMatch.status()).as(autoMatch.text()).isEqualTo(200);
        assertThat(read(autoMatch).get("matchCount").asInt()).isEqualTo(3);

        JsonNode after = read(get(BASE + "/reconciliations/" + reconId));
        assertThat(after.get("status").asText()).isEqualTo("IN_PROGRESS");
        assertCounts(after, 4, 3, 1, 0);

        JsonNode matched = read(get(BASE + "/statements/" + statementId + "/items?matchStatus=MATCHED"));
        assertThat(matched.size()).isEqualTo(3);
        assertThat(matched.get(0).get("description").asText()).isEqualTo("Transfer Masuk Klien Alpha");
        assertThat(matched.get(0).get("matchType").asText()).isEqualTo("EXACT");
        assertThat(matched.get(1).get("description").asText()).isEqualTo("Transfer Keluar Hosting");
        assertThat(matched.get(1).get("matchType").asText()).isEqualTo("FUZZY_DATE");
        assertThat(matched.get(2).get("description").asText()).isEqualTo("Tagihan PLN listrik kantor");
        assertThat(matched.get(2).get("matchType").asText()).isEqualTo("KEYWORD");
        JsonNode unmatched = read(get(BASE + "/statements/" + statementId + "/items?matchStatus=UNMATCHED"));
        assertThat(unmatched.size()).isEqualTo(1);
        assertThat(unmatched.get(0).get("description").asText()).isEqualTo("Biaya Admin Bank");

        List<BankStatementItem> items = statementItemRepository.findByBankStatementIdOrderByLineNumberAsc(statementId);
        assertThat(items.get(0).getMatchedTransaction().getId()).isEqualTo(alpha);
        assertThat(items.get(1).getMatchedTransaction().getId()).isEqualTo(hosting);
        assertThat(items.get(2).getMatchedTransaction().getId()).isEqualTo(power);
        assertThat(items.get(0).getMatchedBy()).isEqualTo("admin");

        JsonNode leftover = read(get(BASE + "/reconciliations/" + reconId + "/unmatched-book"));
        assertThat(leftover.size()).isEqualTo(1);
        assertThat(leftover.get(0).get("id").asText()).isEqualTo(beta.toString());
        assertThat(leftover.get(0).get("transactionDate").asText()).isEqualTo("2035-03-25");
        assertThat(leftover.get(0).get("description").asText()).isEqualTo("Pendapatan training Beta");
        assertAmount(leftover.get(0).get("amount"), "500000");
    }

    @Test
    @DisplayName("Manual match, bank-only, book-only and unmatch feed summary, statement and outstanding; completion then locks the reconciliation")
    void matchReportAndComplete() {
        Bank bank = newBank();
        UUID opening = postBook(bank, INCOME_TEMPLATE, "2035-04-02", "10000000", "Setoran awal April");
        UUID rentCheque = postBook(bank, RENT_TEMPLATE, "2035-04-29", "2000000", "Cek sewa gudang");
        UUID cashDeposit = postBook(bank, INCOME_TEMPLATE, "2035-04-30", "700000", "Setoran tunai akhir bulan");
        UUID statementId = importOk(bank, "2035-04-01", "2035-04-30", "0", "9990000", CSV_HEADER
                + "03/04/2035,\"Kliring masuk\",0316,,10000000.00,10000000.00\n"
                + "30/04/2035,\"Biaya Admin Bank\",0316,15000.00,,9985000.00\n"
                + "30/04/2035,\"Bunga Jasa Giro\",0316,,5000.00,9990000.00\n");
        UUID reconId = createRecon(statementId);
        List<BankStatementItem> items = statementItemRepository.findByBankStatementIdOrderByLineNumberAsc(statementId);
        UUID deposit = items.get(0).getId();
        UUID adminFee = items.get(1).getId();
        UUID interest = items.get(2).getId();
        String recon = BASE + "/reconciliations/" + reconId;

        APIResponse early = post(recon + "/complete", Map.of());
        assertThat(early.status()).as(early.text()).isEqualTo(409);
        assertThat(read(early).get("message").asText()).isEqualTo("Masih ada 3 item yang belum dicocokkan. "
                + "Cocokkan atau tandai sebagai bank-only/book-only terlebih dahulu.");
        assertThat(read(get(recon)).get("status").asText()).isEqualTo("DRAFT");

        // Manual match, then unmatch it by the id GET /items returns, and match again
        APIResponse match = post(recon + "/match",
                Map.of("statementItemId", deposit.toString(), "transactionId", opening.toString()));
        assertThat(match.status()).as(match.text()).isEqualTo(200);
        assertCounts(read(get(recon)), 3, 1, 2, 0);
        BankStatementItem matchedDeposit = statementItemRepository.findById(deposit).orElseThrow();
        assertThat(matchedDeposit.getMatchStatus()).isEqualTo(StatementItemMatchStatus.MATCHED);
        assertThat(matchedDeposit.getMatchType()).isEqualTo(MatchType.MANUAL);
        assertThat(matchedDeposit.getMatchedTransaction().getId()).isEqualTo(opening);
        assertThat(matchedDeposit.getMatchedBy()).isEqualTo("admin");

        JsonNode reconItems = read(get(recon + "/items"));
        assertThat(reconItems.size()).isEqualTo(1);
        JsonNode matchItem = reconItems.get(0);
        assertThat(matchItem.get("matchStatus").asText()).isEqualTo("MATCHED");
        assertThat(matchItem.get("matchType").asText()).isEqualTo("MANUAL");
        assertAmount(matchItem.get("matchConfidence"), "1.00");
        assertThat(matchItem.get("statementItemId").asText()).isEqualTo(deposit.toString());
        assertThat(matchItem.get("transactionId").asText()).isEqualTo(opening.toString());
        UUID matchItemId = UUID.fromString(matchItem.get("id").asText());
        APIResponse unmatch = delete(recon + "/items/" + matchItemId);
        assertThat(unmatch.status()).as(unmatch.text()).isEqualTo(204);
        assertCounts(read(get(recon)), 3, 0, 3, 0);
        BankStatementItem reset = statementItemRepository.findById(deposit).orElseThrow();
        assertThat(reset.getMatchStatus()).isEqualTo(StatementItemMatchStatus.UNMATCHED);
        assertThat(reset.getMatchType()).isNull();
        assertThat(reset.getMatchedTransaction()).isNull();
        assertThat(reset.getMatchedBy()).isNull();
        assertThat(reconciliationItemRepository.findById(matchItemId)).isEmpty();
        assertThat(ids(read(get(recon + "/unmatched-book")))).containsExactlyInAnyOrder(opening, rentCheque, cashDeposit);

        assertThat(post(recon + "/match",
                Map.of("statementItemId", deposit.toString(), "transactionId", opening.toString())).status())
                .isEqualTo(200);

        // Bank-only and book-only
        assertThat(post(recon + "/mark-bank-only",
                Map.of("statementItemId", adminFee.toString(), "notes", "Biaya admin belum dibukukan")).status())
                .isEqualTo(200);
        assertThat(post(recon + "/mark-bank-only",
                Map.of("statementItemId", interest.toString(), "notes", "Bunga belum dibukukan")).status())
                .isEqualTo(200);
        assertThat(post(recon + "/mark-book-only",
                Map.of("transactionId", rentCheque.toString(), "notes", "Cek belum dicairkan")).status())
                .isEqualTo(200);
        assertThat(post(recon + "/mark-book-only",
                Map.of("transactionId", cashDeposit.toString(), "notes", "Setoran dalam perjalanan")).status())
                .isEqualTo(200);
        assertThat(statementItemRepository.findById(adminFee).orElseThrow().getMatchStatus())
                .isEqualTo(StatementItemMatchStatus.BANK_ONLY);
        JsonNode marked = read(get(recon));
        assertThat(marked.get("status").asText()).isEqualTo("IN_PROGRESS");
        assertCounts(marked, 3, 1, 2, 2);
        assertThat(read(get(recon + "/unmatched-book")).size()).isZero();

        JsonNode outstanding = read(get(recon + "/outstanding"));
        assertThat(outstanding.size()).isEqualTo(4);
        Map<String, String> statusByNote = new HashMap<>();
        UUID interestItemId = null;
        for (JsonNode o : outstanding) {
            statusByNote.put(o.get("notes").asText(), o.get("matchStatus").asText());
            if ("Bunga belum dibukukan".equals(o.get("notes").asText())) {
                interestItemId = UUID.fromString(o.get("id").asText());
            }
        }
        assertThat(statusByNote).containsExactlyInAnyOrderEntriesOf(Map.of(
                "Biaya admin belum dibukukan", "BANK_ONLY",
                "Bunga belum dibukukan", "BANK_ONLY",
                "Cek belum dicairkan", "BOOK_ONLY",
                "Setoran dalam perjalanan", "BOOK_ONLY"));

        // Unmatch a bank-only item by the id the outstanding report returns, then mark it again
        assertThat(delete(recon + "/items/" + interestItemId).status()).isEqualTo(204);
        assertThat(statementItemRepository.findById(interest).orElseThrow().getMatchStatus())
                .isEqualTo(StatementItemMatchStatus.UNMATCHED);
        assertThat(read(get(recon + "/outstanding")).size()).isEqualTo(3);
        assertCounts(read(get(recon)), 3, 1, 2, 2);
        assertThat(post(recon + "/mark-bank-only",
                Map.of("statementItemId", interest.toString(), "notes", "Bunga belum dibukukan")).status())
                .isEqualTo(200);

        // Summary: book 10,000,000 - 2,000,000 + 700,000 = 8,700,000; bank-only |-15,000| + |5,000|
        JsonNode summary = read(get(recon + "/summary"));
        assertThat(summary.get("matchedCount").asInt()).isEqualTo(1);
        assertThat(summary.get("bankOnlyCount").asInt()).isEqualTo(2);
        assertThat(summary.get("bookOnlyCount").asInt()).isEqualTo(2);
        assertThat(summary.get("totalItems").asInt()).isEqualTo(3);
        assertAmount(summary.get("matchedAmount"), "10000000");
        assertAmount(summary.get("bankOnlyAmount"), "20000");
        assertAmount(summary.get("bookOnlyAmount"), "2700000");
        assertAmount(summary.get("bookBalance"), "8700000");
        assertAmount(summary.get("bankBalance"), "9990000");

        // Statement: 8,700,000 + 5,000 - 15,000 = 8,690,000 = 9,990,000 + 700,000 - 2,000,000
        JsonNode statement = read(get(recon + "/statement"));
        assertAmount(statement.get("bookBalance"), "8700000");
        assertAmount(statement.get("bankBalance"), "9990000");
        assertAmount(statement.get("bankOnlyDebitTotal"), "15000");
        assertAmount(statement.get("bankOnlyCreditTotal"), "5000");
        assertAmount(statement.get("bookOnlyDebitTotal"), "700000");
        assertAmount(statement.get("bookOnlyCreditTotal"), "2000000");
        assertAmount(statement.get("adjustedBookBalance"), "8690000");
        assertAmount(statement.get("adjustedBankBalance"), "8690000");
        assertAmount(statement.get("difference"), "0");

        APIResponse complete = post(recon + "/complete", Map.of());
        assertThat(complete.status()).as(complete.text()).isEqualTo(200);
        JsonNode completed = read(get(recon));
        assertThat(completed.get("status").asText()).isEqualTo("COMPLETED");
        assertCounts(completed, 3, 1, 2, 2);
        BankReconciliation stored = reconciliationRepository.findByIdWithRelations(reconId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(ReconciliationStatus.COMPLETED);
        assertThat(stored.getCompletedBy()).isEqualTo("admin");
        assertThat(stored.getCompletedAt()).isNotNull();

        // Every mutation of a completed reconciliation is a 409 and changes nothing
        UUID bankFeeTemplate = templateId(BANK_FEE_TEMPLATE);
        UUID bankOnlyItemId = reconciliationItemRepository.findByReconciliationIdAndStatementItemId(reconId, adminFee)
                .orElseThrow().getId();
        List<APIResponse> locked = List.of(
                post(recon + "/auto-match", Map.of()),
                post(recon + "/match", Map.of("statementItemId", adminFee.toString(), "transactionId", opening.toString())),
                post(recon + "/mark-bank-only", Map.of("statementItemId", deposit.toString())),
                post(recon + "/mark-book-only", Map.of("transactionId", opening.toString())),
                delete(recon + "/items/" + bankOnlyItemId),
                post(recon + "/create-transaction", Map.of("statementItemId", adminFee.toString(),
                        "templateId", bankFeeTemplate.toString())));
        for (APIResponse r : locked) {
            assertThat(r.status()).as(r.url() + ": " + r.text()).isEqualTo(409);
            assertThat(read(r).get("error").asText()).isEqualTo("CONFLICT");
        }
        assertThat(read(get(recon + "/outstanding")).size()).isEqualTo(4);
        assertCounts(read(get(recon)), 3, 1, 2, 2);
        assertThat(statementItemRepository.findById(adminFee).orElseThrow().getMatchStatus())
                .isEqualTo(StatementItemMatchStatus.BANK_ONLY);
        assertThat(statementItemRepository.findById(deposit).orElseThrow().getMatchedTransaction().getId())
                .isEqualTo(opening);
    }

    @Test
    @DisplayName("Create-transaction books a DRAFT from a statement item and matches it; description falls back to the bank text")
    void createTransactionFromStatementItem() {
        Bank bank = newBank();
        UUID statementId = importOk(bank, "2035-05-01", "2035-05-31", "100000", "80000", CSV_HEADER
                + "31/05/2035,\"Biaya Admin Bank\",0316,15000.00,,85000.00\n"
                + "31/05/2035,\"Biaya Materai Elektronik\",0316,5000.00,,80000.00\n");
        UUID reconId = createRecon(statementId);
        String recon = BASE + "/reconciliations/" + reconId;
        List<BankStatementItem> items = statementItemRepository.findByBankStatementIdOrderByLineNumberAsc(statementId);
        UUID template = templateId(BANK_FEE_TEMPLATE);

        APIResponse withDescription = post(recon + "/create-transaction", Map.of(
                "statementItemId", items.get(0).getId().toString(),
                "templateId", template.toString(),
                "description", "Admin bank Mei 2035"));
        assertThat(withDescription.status()).as(withDescription.text()).isEqualTo(200);
        APIResponse withoutDescription = post(recon + "/create-transaction", Map.of(
                "statementItemId", items.get(1).getId().toString(),
                "templateId", template.toString()));
        assertThat(withoutDescription.status()).as(withoutDescription.text()).isEqualTo(200);

        assertBookedFromItem(items.get(0).getId(), template, "15000", "Admin bank Mei 2035");
        assertBookedFromItem(items.get(1).getId(), template, "5000", "Biaya Materai Elektronik");

        JsonNode after = read(get(recon));
        assertThat(after.get("status").asText()).isEqualTo("IN_PROGRESS");
        assertCounts(after, 2, 2, 0, 0);
        JsonNode matched = read(get(BASE + "/statements/" + statementId + "/items?matchStatus=MATCHED"));
        assertThat(matched.size()).isEqualTo(2);
        assertThat(matched.get(0).get("matchType").asText()).isEqualTo("MANUAL");
        // DRAFT transactions have no journal entries on the bank GL account, so book balance stays empty
        assertThat(read(get(recon + "/unmatched-book")).size()).isZero();
        assertThat(post(recon + "/complete", Map.of()).status()).isEqualTo(200);
    }

    @Test
    @DisplayName("Reconciliation endpoints: missing fields 400, unknown ids 404, bank account without GL 400, state untouched")
    void reconciliationErrors() {
        Bank bank = newBank();
        UUID booked = postBook(bank, INCOME_TEMPLATE, "2035-06-05", "400000", "Pendapatan training Zeta");
        UUID statementId = importOk(bank, "2035-06-01", "2035-06-30", "0", "400000", CSV_HEADER
                + "05/06/2035,\"Transfer Masuk Zeta\",0316,,400000.00,400000.00\n");
        UUID reconId = createRecon(statementId);
        String recon = BASE + "/reconciliations/" + reconId;
        UUID itemId = statementItemRepository.findByBankStatementIdOrderByLineNumberAsc(statementId).getFirst().getId();
        UUID unknown = UUID.randomUUID();

        assertValidation(post(BASE + "/reconciliations", Map.of("notes", "tanpa statement")), "bankStatementId");
        assertValidation(post(recon + "/match", Map.of("statementItemId", itemId.toString())), "transactionId");
        assertValidation(post(recon + "/mark-bank-only", Map.of("notes", "x")), "statementItemId");
        assertValidation(post(recon + "/mark-book-only", Map.of("notes", "x")), "transactionId");
        assertValidation(post(recon + "/create-transaction", Map.of("statementItemId", itemId.toString())),
                "templateId");

        assertNotFound(post(BASE + "/reconciliations", Map.of("bankStatementId", unknown.toString())),
                "Bank statement not found with id: " + unknown);
        String missingRecon = "Reconciliation not found with id: " + unknown;
        String other = BASE + "/reconciliations/" + unknown;
        assertNotFound(get(other), missingRecon);
        assertNotFound(post(other + "/auto-match", Map.of()), missingRecon);
        assertNotFound(post(other + "/complete", Map.of()), missingRecon);
        assertNotFound(get(other + "/unmatched-book"), missingRecon);
        assertNotFound(get(other + "/summary"), missingRecon);
        assertNotFound(get(other + "/statement"), missingRecon);
        assertNotFound(post(other + "/match",
                Map.of("statementItemId", itemId.toString(), "transactionId", booked.toString())), missingRecon);
        assertNotFound(post(recon + "/match",
                Map.of("statementItemId", unknown.toString(), "transactionId", booked.toString())),
                "Statement item not found: " + unknown + " in bank statement " + statementId);
        assertNotFound(post(recon + "/match",
                Map.of("statementItemId", itemId.toString(), "transactionId", unknown.toString())),
                "Transaction not found with id: " + unknown);
        assertNotFound(post(recon + "/mark-bank-only", Map.of("statementItemId", unknown.toString())),
                "Statement item not found: " + unknown + " in bank statement " + statementId);
        assertNotFound(post(recon + "/mark-book-only", Map.of("transactionId", unknown.toString())),
                "Transaction not found with id: " + unknown);
        assertNotFound(delete(recon + "/items/" + unknown),
                "Reconciliation item not found: " + unknown + " in reconciliation " + reconId);
        assertNotFound(get(other + "/items"), missingRecon);
        assertNotFound(get(other + "/outstanding"), missingRecon);
        assertNotFound(get(BASE + "/statements/" + unknown + "/items"), "Bank statement not found with id: " + unknown);
        assertNotFound(post(recon + "/create-transaction",
                Map.of("statementItemId", itemId.toString(), "templateId", unknown.toString())),
                "Template not found with id: " + unknown);

        // None of the rejected calls touched the reconciliation
        JsonNode untouched = read(get(recon));
        assertThat(untouched.get("status").asText()).isEqualTo("DRAFT");
        assertCounts(untouched, 1, 0, 1, 0);
        assertThat(reconciliationItemRepository.findByReconciliationIdOrderByMatchStatusAsc(reconId)).isEmpty();
        assertThat(statementItemRepository.findById(itemId).orElseThrow().getMatchStatus())
                .isEqualTo(StatementItemMatchStatus.UNMATCHED);

        // A bank account not linked to a GL account cannot be reconciled
        CompanyBankAccount noGl = new CompanyBankAccount();
        String suffix = shortId();
        noGl.setBankName("Bank Tanpa GL " + suffix);
        noGl.setAccountNumber("NG" + suffix);
        noGl.setAccountName("PT ArtiVisi Intermedia");
        noGl.setCurrencyCode("IDR");
        noGl.setIsDefault(false);
        noGl = bankAccountService.create(noGl);
        UUID noGlStatement = importOk(new Bank(noGl.getBankName(), noGl.getId(), null), "2035-06-01", "2035-06-30",
                "0", "400000", CSV_HEADER + "05/06/2035,\"Transfer Masuk\",0316,,400000.00,400000.00\n");
        APIResponse rejected = post(BASE + "/reconciliations", Map.of("bankStatementId", noGlStatement.toString()));
        assertThat(rejected.status()).as(rejected.text()).isEqualTo(400);
        assertThat(read(rejected).get("message").asText()).isEqualTo("Rekening bank '" + noGl.getBankName()
                + "' belum dihubungkan dengan akun GL. Atur di Pengaturan > Rekening Bank.");
        assertThat(reconciliationRepository.findByBankStatementId(noGlStatement)).isEmpty();
    }

    @Test
    @DisplayName("Match, mark and unmatch only accept items and transactions of the reconciliation's own statement, account and period")
    void itemsBelongToTheirReconciliation() {
        Bank bankA = newBank();
        Bank bankB = newBank();
        UUID txnA = postBook(bankA, INCOME_TEMPLATE, "2035-08-05", "300000", "Pendapatan training Eta");
        UUID txnALate = postBook(bankA, INCOME_TEMPLATE, "2035-09-02", "300000", "Pendapatan training Eta September");
        UUID txnB = postBook(bankB, INCOME_TEMPLATE, "2035-08-06", "450000", "Pendapatan training Theta");
        UUID statementA = importOk(bankA, "2035-08-01", "2035-08-31", "0", "300000", CSV_HEADER
                + "05/08/2035,\"Transfer Masuk Eta\",0316,,300000.00,300000.00\n");
        UUID statementB = importOk(bankB, "2035-08-01", "2035-08-31", "0", "450000", CSV_HEADER
                + "06/08/2035,\"Transfer Masuk Theta\",0316,,450000.00,450000.00\n");
        UUID reconA = createRecon(statementA);
        UUID reconB = createRecon(statementB);
        String a = BASE + "/reconciliations/" + reconA;
        String b = BASE + "/reconciliations/" + reconB;
        UUID itemA = statementItemRepository.findByBankStatementIdOrderByLineNumberAsc(statementA).getFirst().getId();
        UUID itemB = statementItemRepository.findByBankStatementIdOrderByLineNumberAsc(statementB).getFirst().getId();

        // Another statement's item is not found through reconciliation A
        String itemBNotInA = "Statement item not found: " + itemB + " in bank statement " + statementA;
        assertNotFound(post(a + "/match", Map.of("statementItemId", itemB.toString(), "transactionId", txnA.toString())),
                itemBNotInA);
        assertNotFound(post(a + "/mark-bank-only", Map.of("statementItemId", itemB.toString(), "notes", "x")),
                itemBNotInA);
        assertNotFound(post(a + "/create-transaction", Map.of("statementItemId", itemB.toString(),
                "templateId", templateId(INCOME_TEMPLATE).toString())), itemBNotInA);

        // Another account's transaction, or one outside the period, is rejected
        for (UUID foreign : List.of(txnB, txnALate)) {
            for (APIResponse rejected : List.of(
                    post(a + "/match", Map.of("statementItemId", itemA.toString(), "transactionId", foreign.toString())),
                    post(a + "/mark-book-only", Map.of("transactionId", foreign.toString(), "notes", "x")))) {
                assertThat(rejected.status()).as(rejected.text()).isEqualTo(400);
                assertThat(read(rejected).get("message").asText())
                        .startsWith("Transaksi ")
                        .contains("pada periode 2035-08-01 s.d. 2035-08-31, atau sudah dicocokkan di rekonsiliasi ini");
            }
        }
        assertThat(statementItemRepository.findById(itemB).orElseThrow().getMatchStatus())
                .isEqualTo(StatementItemMatchStatus.UNMATCHED);
        assertThat(read(get(a + "/items")).size()).isZero();
        assertCounts(read(get(b)), 1, 0, 1, 0);

        // An item already matched cannot be matched or marked again
        assertThat(post(a + "/match", Map.of("statementItemId", itemA.toString(), "transactionId", txnA.toString()))
                .status()).isEqualTo(200);
        APIResponse again = post(a + "/match", Map.of("statementItemId", itemA.toString(), "transactionId", txnA.toString()));
        assertThat(again.status()).as(again.text()).isEqualTo(409);
        assertThat(read(again).get("message").asText())
                .isEqualTo("Item mutasi baris 2 sudah berstatus MATCHED; batalkan dulu sebelum mencocokkan ulang");
        assertThat(post(a + "/mark-bank-only", Map.of("statementItemId", itemA.toString(), "notes", "x")).status())
                .isEqualTo(409);
        APIResponse bookAgain = post(a + "/mark-book-only", Map.of("transactionId", txnA.toString(), "notes", "x"));
        assertThat(bookAgain.status()).as(bookAgain.text()).isEqualTo(400);
        assertThat(read(get(a + "/items")).size()).isEqualTo(1);
        assertCounts(read(get(a)), 1, 1, 0, 0);

        // Reconciliation B's item cannot be deleted through reconciliation A, even after B completes
        assertThat(post(b + "/match", Map.of("statementItemId", itemB.toString(), "transactionId", txnB.toString()))
                .status()).isEqualTo(200);
        assertThat(post(b + "/complete", Map.of()).status()).isEqualTo(200);
        JsonNode itemsB = read(get(b + "/items"));
        assertThat(itemsB.size()).isEqualTo(1);
        UUID reconItemB = UUID.fromString(itemsB.get(0).get("id").asText());
        assertNotFound(delete(a + "/items/" + reconItemB),
                "Reconciliation item not found: " + reconItemB + " in reconciliation " + reconA);
        assertThat(reconciliationItemRepository.findById(reconItemB)).isPresent();
        BankStatementItem stillMatched = statementItemRepository.findById(itemB).orElseThrow();
        assertThat(stillMatched.getMatchStatus()).isEqualTo(StatementItemMatchStatus.MATCHED);
        assertThat(stillMatched.getMatchedTransaction().getId()).isEqualTo(txnB);
        assertThat(read(get(b)).get("status").asText()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("No bearer token is 401; a bank-recon:read token reads but cannot import, and nothing is stored")
    void authentication() {
        Bank bank = newBank();
        UUID statementId = importOk(bank, "2035-07-01", "2035-07-31", "0", "1000", CSV_HEADER
                + "01/07/2035,\"Transfer Masuk\",0316,,1000.00,1000.00\n");

        APIResponse anonymous = api.get(BASE + "/statements/" + statementId);
        assertThat(anonymous.status()).isEqualTo(401);
        APIResponse anonymousImport = api.post(BASE + "/statements/import", RequestOptions.create()
                .setMultipart(importForm(bank, bcaConfigId(), "2035-07-01", "2035-07-31", "0", "1000", "anon.csv",
                        CSV_HEADER + "02/07/2035,\"Anon\",0316,,1.00,1.00\n")));
        assertThat(anonymousImport.status()).isEqualTo(401);

        token = serviceToken("bank-recon:read");
        JsonNode statement = read(get(BASE + "/statements/" + statementId));
        assertThat(statement.get("totalItems").asInt()).isEqualTo(1);
        APIResponse readOnlyImport = importStatement(bank, bcaConfigId(), "2035-07-01", "2035-07-31", "0", "1000",
                "readonly.csv", CSV_HEADER + "02/07/2035,\"Read only\",0316,,1.00,1.00\n");
        assertThat(readOnlyImport.status()).as(readOnlyImport.text()).isEqualTo(403);
        assertThat(statementRepository.findByBankAccountIdWithRelations(bank.id()))
                .extracting(s -> s.getId()).containsExactly(statementId);
    }

    // ==================== Assertions ====================

    private static void assertStatement(JsonNode s, Bank bank) {
        assertThat(s.get("bankAccountId").asText()).isEqualTo(bank.id().toString());
        assertThat(s.get("bankName").asText()).isEqualTo(bank.name());
        assertThat(s.get("periodStart").asText()).isEqualTo("2035-01-01");
        assertThat(s.get("periodEnd").asText()).isEqualTo("2035-01-31");
        assertAmount(s.get("openingBalance"), "5000000");
        assertAmount(s.get("closingBalance"), "6185000");
        assertThat(s.get("originalFilename").asText()).isEqualTo("bca-jan-2035.csv");
        assertThat(s.get("totalItems").asInt()).isEqualTo(3);
        assertAmount(s.get("totalDebit"), "315000");
        assertAmount(s.get("totalCredit"), "1500000");
    }

    private static void assertItem(JsonNode item, int line, String date, String description,
                                   String debit, String credit, String balance) {
        assertThat(item.get("lineNumber").asInt()).isEqualTo(line);
        assertThat(item.get("transactionDate").asText()).isEqualTo(date);
        assertThat(item.get("description").asText()).isEqualTo(description);
        if (debit == null) {
            assertThat(item.get("debitAmount").isNull()).as("debit line %d", line).isTrue();
        } else {
            assertAmount(item.get("debitAmount"), debit);
        }
        if (credit == null) {
            assertThat(item.get("creditAmount").isNull()).as("credit line %d", line).isTrue();
        } else {
            assertAmount(item.get("creditAmount"), credit);
        }
        assertAmount(item.get("balance"), balance);
    }

    private static void assertCounts(JsonNode recon, int total, int matched, int unmatchedBank, int unmatchedBook) {
        assertThat(recon.get("totalStatementItems").asInt()).as("totalStatementItems").isEqualTo(total);
        assertThat(recon.get("matchedCount").asInt()).as("matchedCount").isEqualTo(matched);
        assertThat(recon.get("unmatchedBankCount").asInt()).as("unmatchedBankCount").isEqualTo(unmatchedBank);
        assertThat(recon.get("unmatchedBookCount").asInt()).as("unmatchedBookCount").isEqualTo(unmatchedBook);
    }

    private static void assertAmount(JsonNode node, String expected) {
        assertThat(node.isNumber()).as("numeric amount, was %s", node).isTrue();
        assertThat(node.decimalValue()).isEqualByComparingTo(expected);
    }

    private void assertValidation(APIResponse response, String field) {
        assertThat(response.status()).as(response.url() + ": " + response.text()).isEqualTo(400);
        JsonNode body = read(response);
        assertThat(body.get("error").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.get("fieldErrors").has(field)).as(response.text()).isTrue();
    }

    private void assertNotFound(APIResponse response, String message) {
        assertThat(response.status()).as(response.url() + ": " + response.text()).isEqualTo(404);
        JsonNode body = read(response);
        assertThat(body.get("error").asText()).isEqualTo("NOT_FOUND");
        assertThat(body.get("message").asText()).isEqualTo(message);
    }

    private void assertBookedFromItem(UUID statementItemId, UUID templateId, String amount, String description) {
        BankStatementItem item = statementItemRepository.findById(statementItemId).orElseThrow();
        assertThat(item.getMatchStatus()).isEqualTo(StatementItemMatchStatus.MATCHED);
        assertThat(item.getMatchType()).isEqualTo(MatchType.MANUAL);
        assertThat(item.getMatchedBy()).isEqualTo("admin");
        Transaction txn = transactionRepository.findById(item.getMatchedTransaction().getId()).orElseThrow();
        assertThat(txn.getStatus()).isEqualTo(TransactionStatus.DRAFT);
        assertThat(txn.getTransactionDate()).isEqualTo(LocalDate.of(2035, 5, 31));
        assertThat(txn.getAmount()).isEqualByComparingTo(amount);
        assertThat(txn.getDescription()).isEqualTo(description);
        assertThat(txn.getCreatedBy()).isEqualTo("admin");
        assertThat(txn.getJournalTemplate().getId()).isEqualTo(templateId);
    }

    // ==================== Fixtures ====================

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** A GL bank account under 1.1 plus a company bank account linked to it, both unique to the caller. */
    private Bank newBank() {
        String suffix = shortId();
        ChartOfAccount parent = accountRepository.findByAccountCode("1.1")
                .orElseThrow(() -> new IllegalStateException("Seeded account 1.1 missing"));
        ChartOfAccount gl = new ChartOfAccount();
        gl.setAccountCode("1.1.A" + suffix);
        gl.setAccountName("Bank Rekon API " + suffix);
        gl.setParent(parent);
        gl = accountService.create(gl);

        CompanyBankAccount account = new CompanyBankAccount();
        account.setBankName("Bank Rekon API " + suffix);
        account.setAccountNumber("RA" + suffix);
        account.setAccountName("PT ArtiVisi Intermedia");
        account.setCurrencyCode("IDR");
        account.setIsDefault(false);
        account.setGlAccount(gl);
        account = bankAccountService.create(account);
        return new Bank(account.getBankName(), account.getId(), gl.getId());
    }

    /** Posts a transaction whose BANK line goes to the bank's GL account; returns its id. */
    private UUID postBook(Bank bank, String templateName, String date, String amount, String description) {
        JournalTemplate template = templateRepository.findByTemplateNameWithLines(templateName)
                .orElseThrow(() -> new IllegalStateException("Seeded template missing: " + templateName));
        JournalTemplateLine bankLine = template.getLines().stream()
                .filter(l -> "BANK".equals(l.getAccountHint())).findFirst()
                .orElseThrow(() -> new IllegalStateException("Template has no BANK line: " + templateName));

        Transaction transaction = new Transaction();
        transaction.setJournalTemplate(template);
        transaction.setTransactionDate(LocalDate.parse(date));
        transaction.setAmount(new BigDecimal(amount));
        transaction.setDescription(description);
        Transaction created = transactionService.create(transaction, Map.of(bankLine.getId(), bank.glAccountId()));
        return transactionService.post(created.getId(), "admin").getId();
    }

    private UUID templateId(String name) {
        return templateRepository.findByTemplateNameWithLines(name)
                .orElseThrow(() -> new IllegalStateException("Seeded template missing: " + name)).getId();
    }

    private UUID bcaConfigId() {
        return parserConfigRepository.findAllByOrderByConfigNameAsc().stream()
                .filter(c -> BCA_CONFIG.equals(c.getConfigName())).findFirst()
                .orElseThrow(() -> new IllegalStateException("Seeded parser config missing: " + BCA_CONFIG))
                .getId();
    }

    private UUID importOk(Bank bank, String start, String end, String opening, String closing, String csv) {
        APIResponse response = importStatement(bank, bcaConfigId(), start, end, opening, closing, "stmt.csv", csv);
        assertThat(response.status()).as(response.text()).isEqualTo(201);
        return UUID.fromString(read(response).get("id").asText());
    }

    private UUID createRecon(UUID statementId) {
        APIResponse response = post(BASE + "/reconciliations", Map.of("bankStatementId", statementId.toString()));
        assertThat(response.status()).as(response.text()).isEqualTo(201);
        return UUID.fromString(read(response).get("id").asText());
    }

    private String serviceToken(String scopes) {
        User admin = userRepository.findByUsername("admin")
                .orElseThrow(() -> new IllegalStateException("Seeded user missing: admin"));
        return deviceAuthService.createServiceToken(admin, "bank-recon-api-" + shortId(), "bank-recon-api-test",
                scopes, 60).getTokenHash();
    }

    // ==================== HTTP ====================

    private static FormData importForm(Bank bank, UUID parserConfigId, String start, String end,
                                       String opening, String closing, String filename, String csv) {
        return FormData.create()
                .set("bankAccountId", bank.id().toString())
                .set("parserConfigId", parserConfigId.toString())
                .set("periodStart", start)
                .set("periodEnd", end)
                .set("openingBalance", opening)
                .set("closingBalance", closing)
                .set("file", new FilePayload(filename, "text/csv", csv.getBytes(StandardCharsets.UTF_8)));
    }

    private APIResponse importStatement(Bank bank, UUID parserConfigId, String start, String end,
                                        String opening, String closing, String filename, String csv) {
        return api.post(BASE + "/statements/import",
                bearer().setMultipart(importForm(bank, parserConfigId, start, end, opening, closing, filename, csv)));
    }

    private RequestOptions bearer() {
        return RequestOptions.create().setHeader("Authorization", "Bearer " + token);
    }

    private APIResponse get(String path) {
        return api.get(path, bearer());
    }

    private APIResponse post(String path, Object body) {
        return api.post(path, bearer().setHeader("Content-Type", "application/json").setData(body));
    }

    private APIResponse delete(String path) {
        return api.delete(path, bearer());
    }

    private JsonNode read(APIResponse response) {
        try {
            return objectMapper.readTree(response.text());
        } catch (Exception e) {
            throw new IllegalStateException("Invalid JSON response (" + response.status() + "): " + response.text(), e);
        }
    }

    private static JsonNode findById(JsonNode array, UUID id) {
        for (JsonNode node : array) {
            if (id.toString().equals(node.get("id").asText())) {
                return node;
            }
        }
        throw new AssertionError("id " + id + " not in " + array);
    }

    private static List<UUID> ids(JsonNode array) {
        List<UUID> ids = new ArrayList<>();
        array.forEach(n -> ids.add(UUID.fromString(n.get("id").asText())));
        return ids;
    }
}
