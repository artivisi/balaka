package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.Project;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.functional.taxdetail.TaxDetailTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.ProjectRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Tax detail auto-population when a draft is posted through the API. Every test creates and posts
 * its own draft dated {@link #TRANSACTION_DATE}; 2006 is reserved for this class in the tax-detail
 * test context (the draft API rejects future dates).
 */
@DisplayName("Tax Detail Auto-Populate - Functional Tests")
@Import(TaxDetailTestDataInitializer.class)
class TaxDetailAutoPopulateTest extends PlaywrightTestBase {

    private static final String TRANSACTION_DATE = "2006-02-20";

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ChartOfAccountRepository chartOfAccountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    private APIRequestContext apiContext;
    private ObjectMapper objectMapper;
    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        apiContext = playwright.request().newContext(new APIRequest.NewContextOptions()
                .setBaseURL(baseUrl()));

        accessToken = authenticateViaDeviceFlow();
    }

    @AfterEach
    void tearDown() {
        if (apiContext != null) {
            apiContext.dispose();
        }
    }

    @Test
    @DisplayName("Posting a PPN sale auto-populates one PPN Keluaran detail: DPP = amount, PPN from the journal, client as buyer")
    void testPpnAutoPopulates() throws Exception {
        String transactionId = createAndPostDraft("Pendapatan Jasa + PPN", 10000000, "Auto PPN", "PRJ-2024-001");

        JsonNode taxDetails = getTaxDetails(transactionId);
        assertThat(taxDetails).hasSize(1);
        JsonNode detail = taxDetails.get(0);
        assertThat(detail.get("taxType").asText()).isEqualTo("PPN_KELUARAN");
        assertThat(detail.get("transactionCode").asText()).isEqualTo("01");
        assertThat(detail.get("dpp").decimalValue()).isEqualByComparingTo("10000000");
        assertThat(detail.get("ppn").decimalValue()).isEqualByComparingTo(creditOn(transactionId, "2.1.03"));
        assertThat(detail.get("ppn").decimalValue()).isEqualByComparingTo("1100000");
        assertThat(detail.get("counterpartyName").asText()).isEqualTo("PT Bank Mandiri Tbk");
        assertThat(detail.get("counterpartyNpwp").asText()).isEqualTo("01.310.523.4-091.000");
        assertThat(detail.get("fakturNumber").isNull()).isTrue();
    }

    @Test
    @DisplayName("Posting a PPh 23 payment auto-populates one PPh 23 detail: gross = amount, 2%, tax from the journal")
    void testPph23AutoPopulates() throws Exception {
        String transactionId = createAndPostDraft("Pembayaran Jasa dengan PPh 23", 5000000, "Auto PPh 23", "PRJ-2024-003");

        JsonNode taxDetails = getTaxDetails(transactionId);
        assertThat(taxDetails).hasSize(1);
        JsonNode detail = taxDetails.get(0);
        assertThat(detail.get("taxType").asText()).isEqualTo("PPH_23");
        assertThat(detail.get("grossAmount").decimalValue()).isEqualByComparingTo("5000000");
        assertThat(detail.get("taxRate").decimalValue()).isEqualByComparingTo("2.00");
        assertThat(detail.get("taxAmount").decimalValue()).isEqualByComparingTo(creditOn(transactionId, "2.1.21"));
        assertThat(detail.get("taxAmount").decimalValue()).isEqualByComparingTo("100000");
    }

    @Test
    @DisplayName("Posting with a template without tax creates no tax details")
    void testNonTaxTemplateNoDetails() throws Exception {
        String transactionId = createAndPostDraft("Pendapatan Jasa Training", 15000000, "Non-tax", "PRJ-2024-001");

        assertThat(getTaxDetails(transactionId)).isEmpty();
    }

    @Test
    @DisplayName("A PPN sale without a project has no counterparty, so no detail is auto-populated")
    void testNoDetailWithoutCounterparty() throws Exception {
        String transactionId = createAndPostDraft("Pendapatan Jasa + PPN", 4000000, "PPN tanpa proyek", null);

        assertThat(getTaxDetails(transactionId)).isEmpty();
        assertThat(transactionRepository.findById(UUID.fromString(transactionId)).orElseThrow().getStatus())
                .isEqualTo(TransactionStatus.POSTED);
    }

    @Test
    @DisplayName("A detail entered on the draft before posting is kept and no second detail is auto-populated")
    void testNoDuplicates() throws Exception {
        String transactionId = createDraft("Pendapatan Jasa + PPN", 20000000, "Manual sebelum posting", "PRJ-2024-001");

        Map<String, Object> manualDetail = new HashMap<>();
        manualDetail.put("taxType", "PPN_KELUARAN");
        manualDetail.put("counterpartyName", "PT Manual Entry");
        manualDetail.put("dpp", 20000000);
        manualDetail.put("ppn", 2200000);
        manualDetail.put("transactionCode", "04");
        APIResponse createResponse = post("/api/transactions/" + transactionId + "/tax-details", manualDetail);
        assertThat(createResponse.status()).as(createResponse.text()).isEqualTo(201);

        postDraft(transactionId);

        JsonNode taxDetails = getTaxDetails(transactionId);
        assertThat(taxDetails).hasSize(1);
        assertThat(taxDetails.get(0).get("counterpartyName").asText()).isEqualTo("PT Manual Entry");
        assertThat(taxDetails.get(0).get("transactionCode").asText()).isEqualTo("04");
    }

    // --- Helper methods ---

    private String createAndPostDraft(String templateName, int amount, String description, String projectCode) throws Exception {
        String transactionId = createDraft(templateName, amount, description, projectCode);
        postDraft(transactionId);
        return transactionId;
    }

    private String createDraft(String templateName, int amount, String description, String projectCode) throws Exception {
        String templateId = findTemplateIdByName(templateName);
        Map<String, Object> request = new HashMap<>();
        request.put("templateId", templateId);
        request.put("description", description + " " + UUID.randomUUID().toString().substring(0, 8));
        request.put("amount", amount);
        request.put("transactionDate", TRANSACTION_DATE);
        if (projectCode != null) {
            request.put("projectId", findProjectIdByCode(projectCode));
        }
        Map<String, String> accountSlots = resolveAccountSlots(templateId);
        if (!accountSlots.isEmpty()) {
            request.put("accountSlots", accountSlots);
        }

        APIResponse createResponse = post("/api/drafts", request);
        assertThat(createResponse.status())
                .as("Create draft failed: %d %s", createResponse.status(), createResponse.text())
                .isEqualTo(201);
        return objectMapper.readTree(createResponse.text()).get("transactionId").asText();
    }

    private void postDraft(String transactionId) {
        APIResponse postResponse = apiContext.post("/api/transactions/" + transactionId + "/post",
                RequestOptions.create().setHeader("Authorization", "Bearer " + accessToken));
        assertThat(postResponse.ok())
                .as("Post failed: %d %s", postResponse.status(), postResponse.text())
                .isTrue();
    }

    private BigDecimal creditOn(String transactionId, String accountCode) {
        UUID accountId = chartOfAccountRepository.findByAccountCode(accountCode)
                .orElseThrow(() -> new IllegalStateException("Seeded account missing: " + accountCode)).getId();
        return journalEntryRepository.findByTransactionIdOrderByJournalNumberAsc(UUID.fromString(transactionId)).stream()
                .filter(e -> e.getAccount().getId().equals(accountId))
                .map(JournalEntry::getCreditAmount)
                .reduce(BigDecimal::add)
                .orElseThrow(() -> new AssertionError("No journal line on " + accountCode));
    }

    private Map<String, String> resolveAccountSlots(String templateId) throws Exception {
        APIResponse templateResponse = get("/api/templates/" + templateId);
        assertThat(templateResponse.ok()).isTrue();

        JsonNode template = objectMapper.readTree(templateResponse.text());
        JsonNode lines = template.get("lines");

        Map<String, String> slots = new HashMap<>();
        for (JsonNode line : lines) {
            if (line.has("accountHint") && !line.get("accountHint").isNull()
                    && !line.get("accountHint").asText().isBlank()) {
                String hint = line.get("accountHint").asText();
                if (!slots.containsKey(hint)) {
                    String accountId = findAccountForHint(hint);
                    slots.put(hint, accountId);
                }
            }
        }
        return slots;
    }

    private String findAccountForHint(String hint) {
        String hintUpper = hint.toUpperCase();
        return switch (hintUpper) {
            case "BANK" -> chartOfAccountRepository.findByAccountCode("1.1.02")
                    .orElseThrow(() -> new RuntimeException("Bank account 1.1.02 not found"))
                    .getId().toString();
            case "PENDAPATAN" -> chartOfAccountRepository.findByAccountCode("4.1.01")
                    .orElseThrow(() -> new RuntimeException("Revenue account 4.1.01 not found"))
                    .getId().toString();
            case "BEBAN" -> chartOfAccountRepository.findByAccountCode("5.1.01")
                    .orElseThrow(() -> new RuntimeException("Expense account 5.1.01 not found"))
                    .getId().toString();
            default -> throw new RuntimeException("Unknown account hint: " + hint);
        };
    }

    private JsonNode getTaxDetails(String transactionId) throws Exception {
        APIResponse response = get("/api/transactions/" + transactionId + "/tax-details");
        assertThat(response.status()).isEqualTo(200);
        return objectMapper.readTree(response.text());
    }

    private String findTemplateIdByName(String name) throws Exception {
        APIResponse response = get("/api/templates");
        assertThat(response.ok()).isTrue();

        JsonNode templates = objectMapper.readTree(response.text());
        for (JsonNode template : templates) {
            if (name.equals(template.get("name").asText())) {
                return template.get("id").asText();
            }
        }
        throw new RuntimeException("Template not found: " + name);
    }

    private String findProjectIdByCode(String projectCode) {
        Project project = projectRepository.findByCode(projectCode)
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectCode));
        return project.getId().toString();
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

    private String authenticateViaDeviceFlow() throws Exception {
        Map<String, String> codeRequest = Map.of("clientId", "tax-auto-populate-test");

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

        page.locator("input[name='deviceName']").fill("Tax Auto-Populate Test Device");
        page.locator("button[type='submit']:has-text('Otorisasi Perangkat')").click();
        waitForPageLoad();

        Map<String, String> tokenRequest = Map.of("deviceCode", deviceCode);

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
