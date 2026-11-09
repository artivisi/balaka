package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.dto.JournalEntryRequest;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.TaxTransactionDetail;
import com.artivisi.accountingfinance.enums.TaxType;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.TaxTransactionDetailRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.service.TransactionApiService;
import com.artivisi.accountingfinance.service.TransactionService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Read-only close checks API on the IT service pack. Reserved test years 2017 and 2018-01. */
@DisplayName("Close checks API")
@Import(ServiceTestDataInitializer.class)
class CloseCheckApiTest extends PlaywrightTestBase {

    private static final String PATH = "/api/close-checks";
    private static final String SHAREHOLDER = "2.1.50";
    private static final String BANK = "1.1.02";

    @Autowired private TransactionApiService transactionApiService;
    @Autowired private TransactionService transactionService;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private TaxTransactionDetailRepository taxDetailRepository;
    @Autowired private com.artivisi.accountingfinance.repository.JournalEntryRepository journalEntryRepository;

    private APIRequestContext api;
    private String token;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        api = playwright.request().newContext(new APIRequest.NewContextOptions().setBaseURL(baseUrl()));
        token = deviceToken();
    }

    @AfterEach
    void tearDown() {
        api.dispose();
    }

    @Test
    @DisplayName("Checklist lists the six checks in order; a December faktur posted in January is a cut-off finding")
    void checklistWithCutOff() throws Exception {
        ChartOfAccount expense = accountRepository.findByAccountCode("5.1.07").orElseThrow();
        UUID trx = journal(expense, accountRepository.findByAccountCode(BANK).orElseThrow(), LocalDate.of(2018, 1, 5),
                "Pembelian Desember", "330000");
        TaxTransactionDetail detail = new TaxTransactionDetail();
        detail.setTransaction(transactionRepository.findById(trx).orElseThrow());
        detail.setTaxType(TaxType.PPN_MASUKAN);
        detail.setFakturNumber("010.000-17.00000050");
        detail.setFakturDate(LocalDate.of(2017, 12, 28));
        detail.setTransactionCode("01");
        detail.setDpp(new BigDecimal("300000"));
        detail.setPpn(new BigDecimal("30000"));
        detail.setCounterpartyName("Pemasok API");
        taxDetailRepository.save(detail);
        String number = posted(trx);

        JsonNode checklist = read(get(PATH + "?from=2017-12-01&to=2017-12-31"), 200);
        assertThat(checklist.get("from").asText()).isEqualTo("2017-12-01");
        List<String> codes = new ArrayList<>();
        checklist.get("checks").forEach(c -> codes.add(c.get("code").asText()));
        assertThat(codes).containsExactly("TAX_CREDIT_UNCLEARED", "CLEARING_NOT_CREDITED", "LIABILITY_UNMOVED",
                "INVOICE_NOT_IN_RECEIVABLES", "CUT_OFF", "ASSET_PROCEEDS_PERSONAL");
        JsonNode cutOff = checklist.get("checks").get(4);
        assertThat(cutOff.get("status").asText()).isEqualTo("WARNING");
        JsonNode finding = null;
        for (JsonNode f : cutOff.get("findings")) {
            if (f.get("reference").asText().equals(number)) {
                finding = f;
            }
        }
        assertThat(finding).as("cut-off finding for %s", number).isNotNull();
        assertThat(finding.get("description").asText()).isEqualTo("Faktur pajak 010.000-17.00000050 tanggal 2017-12-28");
        assertThat(finding.get("transactionId").asText()).isEqualTo(trx.toString());
        JsonNode taxCredit = checklist.get("checks").get(0);
        assertThat(taxCredit.get("status").asText()).isEqualTo("NOT_APPLICABLE");
        assertThat(taxCredit.get("message").asText()).isEqualTo("SPT Tahunan PPh Badan 2016 belum tercatat dilaporkan per 2017-12-31.");

        JsonNode typed = read(get(PATH + "/cut-off?from=2017-12-01&to=2017-12-31"), 200);
        List<String> rows = new ArrayList<>();
        typed.forEach(c -> rows.add(c.get("transactionNumber").asText() + " " + c.get("source").asText() + " " + c.get("documentDate").asText()));
        assertThat(rows).contains(number + " FAKTUR 2017-12-28");
        assertThat(read(get(PATH + "/cut-off?from=2018-01-01&to=2018-01-31"), 200).findValuesAsText("transactionNumber"))
                .doesNotContain(number);

        assertThat(get(PATH + "?from=2017-12-31&to=2017-12-01").status()).isEqualTo(400);
        assertThat(get(PATH + "?from=2017-12-01").status()).isEqualTo(400);
    }

    @Test
    @DisplayName("Shareholder statement of the seeded 2.1.50: running balance; another account is refused with 400")
    void shareholderStatement() throws Exception {
        ChartOfAccount shareholder = accountRepository.findByAccountCode(SHAREHOLDER).orElseThrow();
        ChartOfAccount bank = accountRepository.findByAccountCode(BANK).orElseThrow();
        posted(journal(bank, shareholder, LocalDate.of(2017, 3, 1), "Pinjaman direktur", "10000000"));
        posted(journal(shareholder, bank, LocalDate.of(2017, 4, 1), "Pengembalian pinjaman direktur", "4000000"));

        JsonNode statement = read(get(PATH + "/shareholder-statement?accountId=" + shareholder.getId()
                + "&from=2017-02-01&to=2017-12-31"), 200);
        assertThat(statement.get("accountCode").asText()).isEqualTo(SHAREHOLDER);
        // 2.1.50 is shared with CloseCheckFunctionalTest (2016): figures are relative to the opening balance
        java.math.BigInteger opening = statement.get("openingBalance").decimalValue().toBigInteger();
        assertThat(opening).isEqualTo(java.math.BigInteger.valueOf(journalBalanceBefore(shareholder, LocalDate.of(2017, 2, 1))));
        List<String> lines = new ArrayList<>();
        statement.get("lines").forEach(l -> lines.add(l.get("date").asText() + " " + l.get("debit").decimalValue().toBigInteger()
                + " " + l.get("credit").decimalValue().toBigInteger() + " "
                + l.get("balance").decimalValue().toBigInteger().subtract(opening)));
        assertThat(lines).containsExactly("2017-03-01 0 10000000 10000000", "2017-04-01 4000000 0 6000000");
        assertThat(statement.get("closingBalance").decimalValue().toBigInteger().subtract(opening))
                .isEqualTo(java.math.BigInteger.valueOf(6000000));

        APIResponse refused = get(PATH + "/shareholder-statement?accountId=" + bank.getId() + "&from=2017-01-01&to=2017-12-31");
        assertThat(refused.status()).isEqualTo(400);
        assertThat(refused.text()).contains("bukan rekening koran pemegang saham/direksi");
    }

    @Test
    @DisplayName("Unmoved liabilities report the configured 6 months; typed check endpoints answer")
    void typedChecks() throws Exception {
        JsonNode unmoved = read(get(PATH + "/unmoved-liabilities?asOf=2017-12-31"), 200);
        assertThat(unmoved.get("months").asInt()).isEqualTo(6);
        assertThat(unmoved.get("asOf").asText()).isEqualTo("2017-12-31");
        JsonNode taxCredits = read(get(PATH + "/tax-credits?returnYear=2016&asOf=2017-12-31"), 200);
        assertThat(taxCredits.get("filedDate").isNull()).isTrue();
        assertThat(read(get(PATH + "/clearing?from=2017-01-01&to=2017-12-31"), 200).isArray()).isTrue();
        assertThat(read(get(PATH + "/invoices-outside-receivables?from=2017-01-01&to=2017-12-31"), 200).isArray()).isTrue();
        assertThat(read(get(PATH + "/asset-proceeds?from=2017-01-01&to=2017-12-31"), 200).isArray()).isTrue();
        assertThat(api.get(PATH + "?from=2017-01-01&to=2017-12-31").status()).isEqualTo(401);
    }

    private long journalBalanceBefore(ChartOfAccount account, LocalDate date) {
        return journalEntryRepository.sumCreditBeforeDate(account.getId(), date)
                .subtract(journalEntryRepository.sumDebitBeforeDate(account.getId(), date)).longValueExact();
    }

    private UUID journal(ChartOfAccount debit, ChartOfAccount credit, LocalDate date, String description, String amount) {
        return transactionApiService.createJournalEntry(new JournalEntryRequest(date, description, null, List.of(
                new JournalEntryRequest.JournalLineRequest(debit.getId(), new BigDecimal(amount), BigDecimal.ZERO),
                new JournalEntryRequest.JournalLineRequest(credit.getId(), BigDecimal.ZERO, new BigDecimal(amount)))), "admin")
                .transactionId();
    }

    private String posted(UUID id) {
        return transactionService.post(id, "admin").getTransactionNumber();
    }

    private JsonNode read(APIResponse response, int status) throws Exception {
        assertThat(response.status()).as(response.text()).isEqualTo(status);
        return mapper.readTree(response.text());
    }

    private APIResponse get(String path) {
        return api.get(path, RequestOptions.create().setHeader("Authorization", "Bearer " + token));
    }

    private String deviceToken() throws Exception {
        APIResponse code = api.post("/api/device/code", RequestOptions.create().setHeader("Content-Type", "application/json")
                .setData(Map.of("clientId", "close-check-api-test")));
        JsonNode codeData = mapper.readTree(code.text());
        loginAsAdmin();
        navigateTo("/device?code=" + codeData.get("userCode").asText());
        waitForPageLoad();
        page.locator("input[name='deviceName']").fill("Close Check API Test");
        page.locator("button[type='submit']:has-text('Otorisasi Perangkat')").click();
        waitForPageLoad();

        AtomicReference<String> result = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofSeconds(2)).until(() -> {
            APIResponse response = api.post("/api/device/token", RequestOptions.create().setHeader("Content-Type", "application/json")
                    .setData(Map.of("deviceCode", codeData.get("deviceCode").asText())));
            if (!response.ok()) {
                return false;
            }
            result.set(mapper.readTree(response.text()).get("accessToken").asText());
            return true;
        });
        return result.get();
    }
}
