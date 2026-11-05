package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.dto.JournalEntryRequest;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.Client;
import com.artivisi.accountingfinance.entity.CompanyBankAccount;
import com.artivisi.accountingfinance.entity.EvidenceRequirement;
import com.artivisi.accountingfinance.enums.AccountType;
import com.artivisi.accountingfinance.enums.EvidenceType;
import com.artivisi.accountingfinance.enums.NormalBalance;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.ClientRepository;
import com.artivisi.accountingfinance.repository.CompanyBankAccountRepository;
import com.artivisi.accountingfinance.repository.EvidenceRequirementRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.service.ContractService;
import com.artivisi.accountingfinance.service.DocumentService;
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
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Read-only evidence API on the IT service pack. Reserved test year 2193. */
@DisplayName("Evidence API")
@Import(ServiceTestDataInitializer.class)
class EvidenceApiTest extends PlaywrightTestBase {

    private static final String PATH = "/api/evidence";
    private static final String ACCOUNT = "5.1.07";

    @Autowired private TransactionApiService transactionApiService;
    @Autowired private TransactionService transactionService;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private EvidenceRequirementRepository requirementRepository;
    @Autowired private DocumentService documentService;
    @Autowired private ContractService contractService;
    @Autowired private ClientRepository clientRepository;
    @Autowired private CompanyBankAccountRepository bankAccountRepository;

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
    @DisplayName("Requirements, gaps, index and one transaction's evidence carry exact types, sources and files")
    void gapsAndEvidence() throws Exception {
        ChartOfAccount electricity = accountRepository.findByAccountCode(ACCOUNT).orElseThrow();
        EvidenceRequirement rule = new EvidenceRequirement();
        rule.setAccount(electricity);
        rule.setEvidenceType(EvidenceType.PROOF_OF_PAYMENT);
        rule.setMinAmount(new BigDecimal("750000"));
        rule.setNotes("Listrik di atas 750 ribu");
        rule.setCreatedBy("admin");
        requirementRepository.save(rule);

        UUID gap = posted(expense(LocalDate.of(2193, 1, 5), "Listrik Januari", "900000"));
        UUID paid = expense(LocalDate.of(2193, 1, 6), "Listrik kantor cabang", "800000");
        UUID docId = documentService.uploadForTransaction(paid, new MockMultipartFile("file", "struk-pln.pdf",
                "application/pdf", "%PDF-1.4 pln".getBytes(StandardCharsets.UTF_8)), "admin", EvidenceType.PROOF_OF_PAYMENT).getId();
        posted(paid);
        posted(expense(LocalDate.of(2193, 1, 7), "Listrik pos jaga", "100000"));
        String gapNumber = transactionRepository.findById(gap).orElseThrow().getTransactionNumber();

        JsonNode requirements = read(get(PATH + "/requirements"), 200);
        JsonNode mine = null;
        for (JsonNode r : requirements) {
            if (r.get("id").asText().equals(rule.getId().toString())) {
                mine = r;
            }
        }
        assertThat(mine).isNotNull();
        assertThat(mine.get("evidenceType").asText()).isEqualTo("PROOF_OF_PAYMENT");
        assertThat(mine.get("accountCode").asText()).isEqualTo(ACCOUNT);
        assertThat(mine.get("templateId").isNull()).isTrue();
        assertThat(mine.get("minAmount").decimalValue()).isEqualByComparingTo("750000");
        assertThat(mine.get("label").asText()).isEqualTo("Akun 5.1.07 Beban Listrik");

        JsonNode gaps = read(get(PATH + "/gaps?from=2193-01-01&to=2193-01-31"), 200);
        assertThat(gaps).hasSize(1);
        JsonNode g = gaps.get(0);
        assertThat(g.get("transactionNumber").asText()).isEqualTo(gapNumber);
        assertThat(g.get("transactionDate").asText()).isEqualTo("2193-01-05");
        assertThat(g.get("amount").decimalValue()).isEqualByComparingTo("900000");
        assertThat(texts(g.get("missing"))).containsExactly("PROOF_OF_PAYMENT");
        assertThat(texts(g.get("required").get(0).get("rules"))).containsExactly("Akun 5.1.07 Beban Listrik");

        JsonNode index = read(get(PATH + "/index?from=2193-01-01&to=2193-01-31"), 200);
        assertThat(index).hasSize(3);
        assertThat(index.get(2).get("required")).isEmpty();

        JsonNode evidence = read(get(PATH + "/transactions/" + paid), 200);
        assertThat(evidence.get("missing")).isEmpty();
        assertThat(evidence.get("evidence")).hasSize(1);
        assertThat(evidence.get("evidence").get(0).get("source").asText()).isEqualTo("ATTACHMENT");
        assertThat(evidence.get("evidence").get(0).get("documentId").asText()).isEqualTo(docId.toString());
        JsonNode file = evidence.get("files").get(0);
        assertThat(file.get("filename").asText()).isEqualTo("struk-pln.pdf");
        assertThat(file.get("evidenceType").asText()).isEqualTo("PROOF_OF_PAYMENT");
        assertThat(file.get("checksumSha256").asText()).hasSize(64);
        assertThat(file.has("storagePath")).as("storage path stays internal").isFalse();

        assertThat(get(PATH + "/gaps?from=2193-02-01&to=2193-01-01").status()).isEqualTo(400);
        assertThat(get(PATH + "/transactions/" + UUID.randomUUID()).status()).isEqualTo(404);
    }

    @Test
    @DisplayName("Contract register filters by client; missing statements list the months due by asOf")
    void contractsAndStatements() throws Exception {
        String marker = UUID.randomUUID().toString().substring(0, 6);
        Client client = new Client();
        client.setCode("EVA-" + marker);
        client.setName("Klien API " + marker);
        client.setActive(true);
        clientRepository.save(client);
        contractService.create(new ContractService.ContractData("KA-" + marker, "Retainer", client.getId(), null,
                LocalDate.of(2193, 1, 1), null, new BigDecimal("12000000"), null), "admin");

        JsonNode contracts = read(get(PATH + "/contracts?clientId=" + client.getId()), 200);
        assertThat(contracts).hasSize(1);
        assertThat(contracts.get(0).get("contractNumber").asText()).isEqualTo("KA-" + marker);
        assertThat(contracts.get(0).get("counterpartyName").asText()).isEqualTo("Klien API " + marker);
        assertThat(contracts.get(0).get("contractValue").decimalValue()).isEqualByComparingTo("12000000");
        assertThat(contracts.get(0).get("documentId").isNull()).isTrue();

        ChartOfAccount gl = new ChartOfAccount();
        gl.setAccountCode("1.1.9" + marker.substring(0, 3));
        gl.setAccountName("Bank API " + marker);
        gl.setAccountType(AccountType.ASSET);
        gl.setNormalBalance(NormalBalance.DEBIT);
        gl.setParent(accountRepository.findByAccountCode("1.1").orElseThrow());
        gl.setLevel(3);
        gl.setIsHeader(false);
        gl.setActive(true);
        accountRepository.save(gl);
        CompanyBankAccount bank = new CompanyBankAccount();
        bank.setBankName("Bank API " + marker);
        bank.setAccountNumber("8800" + marker);
        bank.setAccountName("Giro");
        bank.setGlAccount(gl);
        bankAccountRepository.save(bank);
        var cash = accountRepository.findByAccountCode("1.1.01").orElseThrow();
        posted(transactionApiService.createJournalEntry(new JournalEntryRequest(LocalDate.of(2193, 2, 10), "Setoran awal", null,
                List.of(new JournalEntryRequest.JournalLineRequest(gl.getId(), new BigDecimal("1000"), BigDecimal.ZERO),
                        new JournalEntryRequest.JournalLineRequest(cash.getId(), BigDecimal.ZERO, new BigDecimal("1000")))),
                "admin").transactionId());

        JsonNode missing = read(get(PATH + "/bank-statements/missing?asOf=2193-05-05&graceDays=10"), 200);
        List<String> months = new ArrayList<>();
        for (JsonNode m : missing) {
            if (m.get("bankAccountId").asText().equals(bank.getId().toString())) {
                months.add(m.get("month").asText());
            }
        }
        assertThat(months).containsExactly("2193-02", "2193-03");
        assertThat(get(PATH + "/bank-statements/missing?asOf=2193-05-05&graceDays=-1").status()).isEqualTo(400);
    }

    private UUID expense(LocalDate date, String description, String amount) {
        var debit = accountRepository.findByAccountCode(ACCOUNT).orElseThrow();
        var bank = accountRepository.findByAccountCode("1.1.02").orElseThrow();
        return transactionApiService.createJournalEntry(new JournalEntryRequest(date, description, null, List.of(
                new JournalEntryRequest.JournalLineRequest(debit.getId(), new BigDecimal(amount), BigDecimal.ZERO),
                new JournalEntryRequest.JournalLineRequest(bank.getId(), BigDecimal.ZERO, new BigDecimal(amount)))), "admin")
                .transactionId();
    }

    private UUID posted(UUID id) {
        transactionService.post(id, "admin");
        return id;
    }

    private static List<String> texts(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asText()));
        return out;
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
                .setData(Map.of("clientId", "evidence-api-test")));
        JsonNode codeData = mapper.readTree(code.text());
        loginAsAdmin();
        navigateTo("/device?code=" + codeData.get("userCode").asText());
        waitForPageLoad();
        page.locator("input[name='deviceName']").fill("Evidence API Test");
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
