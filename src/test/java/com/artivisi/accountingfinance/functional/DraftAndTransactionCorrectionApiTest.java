package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Draft and transaction correction API:
 * PATCH /api/drafts/{id}, POST /api/drafts, POST /api/drafts/{id}/approve,
 * PUT/DELETE /api/transactions/{id}, POST /api/transactions/{id}/post and /void,
 * GET /api/transactions/{id}/journal-preview, GET /api/templates.
 *
 * Every test creates its own drafts and transactions dated in 2007 (reserved for this class;
 * the API rejects future dates) with seeded it-service templates resolved by name and accounts
 * by code. Persisted state is read back with SQL.
 */
@DisplayName("Draft & Transaction Correction API - Functional Tests")
@Import(ServiceTestDataInitializer.class)
class DraftAndTransactionCorrectionApiTest extends PlaywrightTestBase {

    private static final String DATE = "2007-03-12";
    private static final String ELECTRICITY = "Bayar Beban Listrik";   // 5.1.07 D / BANK C
    private static final String RENT = "Bayar Beban Sewa";             // 5.1.05 D / BANK C
    private static final String TRAINING = "Pendapatan Jasa Training"; // BANK D / 4.1.01 C
    private static final String FIXED_ASSET = "Pembelian Aset Tetap";  // ASET_TETAP D / BANK C, formula assetCost

    @Autowired private JournalTemplateRepository templateRepository;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private JdbcTemplate jdbc;

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

    @Nested
    @DisplayName("PATCH /api/drafts/{id}")
    class PatchDraft {

        @Test
        @DisplayName("Should update merchant name on PENDING draft")
        void shouldUpdateMerchantName() throws Exception {
            String draftId = createPendingDraft("Original Merchant", 50000);

            APIResponse response = patch("/api/drafts/" + draftId, Map.of("merchantName", "Corrected Merchant Name"));

            assertThat(response.status()).as(response.text()).isEqualTo(200);
            JsonNode body = json(response);
            assertThat(body.get("merchant").asText()).isEqualTo("Corrected Merchant Name");
            assertThat(body.get("status").asText()).isEqualTo("PENDING");
            assertThat(draftColumn(draftId, "merchant_name")).isEqualTo("Corrected Merchant Name");
        }

        @Test
        @DisplayName("Should update amount on PENDING draft")
        void shouldUpdateAmount() throws Exception {
            String draftId = createPendingDraft("Amount Test Merchant", 50000);

            APIResponse response = patch("/api/drafts/" + draftId, Map.of("amount", 75000));

            assertThat(response.status()).as(response.text()).isEqualTo(200);
            assertThat(new BigDecimal(json(response).get("amount").asText())).isEqualByComparingTo("75000");
            assertThat(new BigDecimal(draftColumn(draftId, "amount"))).isEqualByComparingTo("75000");
            assertThat(draftColumn(draftId, "merchant_name")).isEqualTo("Amount Test Merchant");
        }

        @Test
        @DisplayName("Should update suggested template on PENDING draft")
        void shouldUpdateSuggestedTemplate() throws Exception {
            String draftId = createPendingDraft("Template Test Merchant", 50000);
            String templateId = templateId(RENT);

            APIResponse response = patch("/api/drafts/" + draftId, Map.of("suggestedTemplateId", templateId));

            assertThat(response.status()).as(response.text()).isEqualTo(200);
            JsonNode suggestion = json(response).get("suggestedTemplate");
            assertThat(suggestion.get("id").asText()).isEqualTo(templateId);
            assertThat(suggestion.get("name").asText()).isEqualTo(RENT);
            assertThat(draftColumn(draftId, "id_suggested_template")).isEqualTo(templateId);
        }

        @Test
        @DisplayName("Should update merchant, amount and description at once")
        void shouldUpdateMultipleFields() throws Exception {
            String draftId = createPendingDraft("Multi Update Merchant", 30000);

            Map<String, Object> patch = new HashMap<>();
            patch.put("merchantName", "Updated Merchant");
            patch.put("amount", 99000);
            patch.put("description", "Updated description");
            APIResponse response = patch("/api/drafts/" + draftId, patch);

            assertThat(response.status()).as(response.text()).isEqualTo(200);
            JsonNode body = json(response);
            assertThat(body.get("merchant").asText()).isEqualTo("Updated Merchant");
            assertThat(new BigDecimal(body.get("amount").asText())).isEqualByComparingTo("99000");
            assertThat(draftColumn(draftId, "merchant_name")).isEqualTo("Updated Merchant");
            assertThat(new BigDecimal(draftColumn(draftId, "amount"))).isEqualByComparingTo("99000");
            assertThat(draftColumn(draftId, "raw_ocr_text")).isEqualTo("Updated description");
        }

        @Test
        @DisplayName("Should reject update on approved draft and leave it unchanged")
        void shouldRejectUpdateOnApprovedDraft() throws Exception {
            String draftId = createPendingDraft("Approved Draft Merchant", 50000);
            approveDraft(draftId, ELECTRICITY, "Approved via test", 50000);

            APIResponse response = patch("/api/drafts/" + draftId, Map.of("merchantName", "Should Not Work"));

            assertApiError(response, 409, "CONFLICT", "Only pending drafts can be updated: APPROVED");
            assertThat(draftColumn(draftId, "merchant_name")).isEqualTo("Approved Draft Merchant");
            assertThat(draftColumn(draftId, "status")).isEqualTo("APPROVED");
        }
    }

    @Nested
    @DisplayName("POST /api/drafts/{id}/approve")
    class ApproveDraft {

        @Test
        @DisplayName("Should create a DRAFT transaction with the chosen template and return its id")
        void shouldReturnTransactionIdAfterApproval() throws Exception {
            String draftId = createPendingDraft("TransactionId Test Merchant", 60000);

            JsonNode body = approveDraft(draftId, ELECTRICITY, "Approve with transactionId test", 60000);

            assertThat(body.get("status").asText()).isEqualTo("APPROVED");
            String transactionId = body.get("transactionId").asText();
            assertThat(transactionColumn(transactionId, "status")).isEqualTo("DRAFT");
            assertThat(transactionTemplateName(transactionId)).isEqualTo(ELECTRICITY);
            assertThat(transactionColumn(transactionId, "description")).isEqualTo("Approve with transactionId test");
            assertThat(new BigDecimal(transactionColumn(transactionId, "amount"))).isEqualByComparingTo("60000");
            assertThat(transactionColumn(transactionId, "transaction_date")).isEqualTo(DATE);
            assertThat(draftColumn(draftId, "status")).isEqualTo("APPROVED");
            assertThat(draftColumn(draftId, "id_transaction")).isEqualTo(transactionId);
        }
    }

    @Nested
    @DisplayName("PUT /api/transactions/{id}")
    class PutTransaction {

        @Test
        @DisplayName("Should reclassify DRAFT transaction to another template")
        void shouldReclassifyTemplate() throws Exception {
            String transactionId = createDraftTransaction();

            APIResponse response = put(transactionId, Map.of("templateId", templateId(RENT)));

            assertThat(response.status()).as(response.text()).isEqualTo(200);
            assertThat(json(response).get("status").asText()).isEqualTo("DRAFT");
            assertThat(transactionTemplateName(transactionId)).isEqualTo(RENT);
            assertThat(transactionColumn(transactionId, "status")).isEqualTo("DRAFT");
        }

        @Test
        @DisplayName("Should update DRAFT transaction description")
        void shouldUpdateDescription() throws Exception {
            String transactionId = createDraftTransaction();

            APIResponse response = put(transactionId, Map.of("description", "Corrected: Bayar listrik Maret"));

            assertThat(response.status()).as(response.text()).isEqualTo(200);
            assertThat(json(response).get("description").asText()).isEqualTo("Corrected: Bayar listrik Maret");
            assertThat(transactionColumn(transactionId, "description")).isEqualTo("Corrected: Bayar listrik Maret");
            assertThat(new BigDecimal(transactionColumn(transactionId, "amount"))).isEqualByComparingTo("85000");
        }

        @Test
        @DisplayName("Should update DRAFT transaction amount")
        void shouldUpdateAmount() throws Exception {
            String transactionId = createDraftTransaction();

            APIResponse response = put(transactionId, Map.of("amount", 150000));

            assertThat(response.status()).as(response.text()).isEqualTo(200);
            assertThat(new BigDecimal(json(response).get("amount").asText())).isEqualByComparingTo("150000");
            assertThat(new BigDecimal(transactionColumn(transactionId, "amount"))).isEqualByComparingTo("150000");
        }

        @Test
        @DisplayName("Should update description, amount and date at once")
        void shouldUpdateMultipleFields() throws Exception {
            String transactionId = createDraftTransaction();

            Map<String, Object> update = new HashMap<>();
            update.put("description", "Updated remarks");
            update.put("amount", 200000);
            update.put("transactionDate", "2007-02-15");
            APIResponse response = put(transactionId, update);

            assertThat(response.status()).as(response.text()).isEqualTo(200);
            JsonNode body = json(response);
            assertThat(body.get("description").asText()).isEqualTo("Updated remarks");
            assertThat(new BigDecimal(body.get("amount").asText())).isEqualByComparingTo("200000");
            assertThat(body.get("transactionDate").asText()).isEqualTo("2007-02-15");
            assertThat(transactionColumn(transactionId, "description")).isEqualTo("Updated remarks");
            assertThat(new BigDecimal(transactionColumn(transactionId, "amount"))).isEqualByComparingTo("200000");
            assertThat(transactionColumn(transactionId, "transaction_date")).isEqualTo("2007-02-15");
        }

        @Test
        @DisplayName("Should reject update on POSTED transaction and leave it unchanged")
        void shouldRejectUpdateOnPostedTransaction() throws Exception {
            String transactionId = createAndPostTransaction("Posted not editable");

            APIResponse response = put(transactionId, Map.of("description", "Should Not Work"));

            assertApiError(response, 409, "CONFLICT", "Only draft transactions can be updated: POSTED");
            assertThat(transactionColumn(transactionId, "description")).isEqualTo("Posted not editable");
            assertThat(transactionColumn(transactionId, "status")).isEqualTo("POSTED");
        }

        @Test
        @DisplayName("Should map the BANK slot on the existing template so posting credits that account")
        void shouldUpdateWithAccountSlots() throws Exception {
            String transactionId = createDraftTransaction();

            APIResponse response = put(transactionId, Map.of("accountSlots", Map.of("BANK", accountId("1.1.03"))));

            assertThat(response.status()).as(response.text()).isEqualTo(200);
            assertThat(mappedAccountCodes(transactionId)).containsExactly("1.1.03");
            assertThat(entries(post(transactionId))).containsExactlyInAnyOrder(
                    entry("5.1.07", "85000", "0"), entry("1.1.03", "0", "85000"));
        }

        @Test
        @DisplayName("Should reclassify template and map its BANK slot in one update")
        void shouldReclassifyWithAccountSlots() throws Exception {
            String transactionId = createDraftTransaction();

            Map<String, Object> update = new HashMap<>();
            update.put("templateId", templateId(TRAINING));
            update.put("accountSlots", Map.of("BANK", accountId("1.1.02")));
            APIResponse response = put(transactionId, update);

            assertThat(response.status()).as(response.text()).isEqualTo(200);
            assertThat(transactionTemplateName(transactionId)).isEqualTo(TRAINING);
            assertThat(mappedAccountCodes(transactionId)).containsExactly("1.1.02");
            assertThat(entries(post(transactionId))).containsExactlyInAnyOrder(
                    entry("1.1.02", "85000", "0"), entry("4.1.01", "0", "85000"));
        }

        @Test
        @DisplayName("Should re-apply the same accountSlots without 409 and keep one mapping per slot")
        void shouldReApplyAccountSlots() throws Exception {
            String transactionId = createDraftTransaction();
            Map<String, Object> update = Map.of("accountSlots", Map.of("BANK", accountId("1.1.03")));

            APIResponse first = put(transactionId, update);
            assertThat(first.status()).as(first.text()).isEqualTo(200);

            APIResponse second = put(transactionId, update);
            assertThat(second.status()).as(second.text()).isEqualTo(200);

            assertThat(mappedAccountCodes(transactionId)).containsExactly("1.1.03");
        }

        @Test
        @DisplayName("Should reject a future transaction date and keep the original date")
        void shouldRejectFutureDate() throws Exception {
            String transactionId = createDraftTransaction();

            APIResponse response = put(transactionId,
                    Map.of("transactionDate", LocalDate.now().plusDays(1).toString()));

            assertApiError(response, 400, "INVALID_REQUEST", "Transaction date cannot be in the future");
            assertThat(transactionColumn(transactionId, "transaction_date")).isEqualTo(DATE);
        }
    }

    @Nested
    @DisplayName("DELETE /api/transactions/{id}")
    class DeleteTransaction {

        @Test
        @DisplayName("Should delete DRAFT transaction and unlink its source draft")
        void shouldDeleteDraftTransaction() throws Exception {
            String draftId = createPendingDraft("Delete Merchant", 85000);
            String transactionId = approveDraft(draftId, ELECTRICITY, "To be deleted", 85000)
                    .get("transactionId").asText();

            APIResponse response = apiContext.delete("/api/transactions/" + transactionId, auth());

            assertThat(response.status()).as(response.text()).isEqualTo(204);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE id = ?::uuid",
                    Long.class, transactionId)).isZero();
            assertThat(draftColumn(draftId, "id_transaction")).isNull();
        }

        @Test
        @DisplayName("Should reject delete on POSTED transaction and keep it with its journal")
        void shouldRejectDeleteOnPostedTransaction() throws Exception {
            String transactionId = createAndPostTransaction("Posted not deletable");

            APIResponse response = apiContext.delete("/api/transactions/" + transactionId, auth());

            assertApiError(response, 409, "CONFLICT", "Only draft transactions can be deleted");
            assertThat(transactionColumn(transactionId, "status")).isEqualTo("POSTED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM journal_entries WHERE id_transaction = ?::uuid",
                    Long.class, transactionId)).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("POST /api/transactions (direct post)")
    class PostTransactionDirect {

        @Test
        @DisplayName("Should create and post transaction with accountSlots into the chosen bank account")
        void shouldPostWithAccountSlots() throws Exception {
            Map<String, Object> request = directRequest(TRAINING, 75000, "Transaction with account overrides");
            request.put("accountSlots", Map.of("BANK", accountId("1.1.03")));

            APIResponse response = apiContext.post("/api/transactions", jsonBody(request));

            assertThat(response.status()).as(response.text()).isEqualTo(201);
            JsonNode body = json(response);
            assertThat(body.get("status").asText()).isEqualTo("POSTED");
            assertThat(body.get("transactionDate").asText()).isEqualTo(DATE);
            assertThat(entries(body)).containsExactlyInAnyOrder(
                    entry("1.1.03", "75000", "0"), entry("4.1.01", "0", "75000"));
            assertThat(transactionColumn(body.get("transactionId").asText(), "status")).isEqualTo("POSTED");
        }
    }

    @Nested
    @DisplayName("POST /api/drafts (direct draft creation)")
    class PostDraftDirect {

        @Test
        @DisplayName("Should create DRAFT transaction without journal, then post it into the mapped accounts")
        void shouldCreateDraftAndPost() throws Exception {
            APIResponse createResponse = apiContext.post("/api/drafts", jsonBody(draftRequest(
                    ELECTRICITY, 120000, "Direct draft creation test", Map.of("BANK", accountId("1.1.02")))));

            assertThat(createResponse.status()).as(createResponse.text()).isEqualTo(201);
            JsonNode createBody = json(createResponse);
            assertThat(createBody.get("status").asText()).isEqualTo("DRAFT");
            String transactionId = createBody.get("transactionId").asText();
            assertThat(transactionColumn(transactionId, "status")).isEqualTo("DRAFT");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM journal_entries WHERE id_transaction = ?::uuid",
                    Long.class, transactionId)).isZero();

            JsonNode postBody = post(transactionId);

            assertThat(postBody.get("status").asText()).isEqualTo("POSTED");
            assertThat(entries(postBody)).containsExactlyInAnyOrder(
                    entry("5.1.07", "120000", "0"), entry("1.1.02", "0", "120000"));
            assertThat(transactionColumn(transactionId, "status")).isEqualTo("POSTED");
        }

        @Test
        @DisplayName("Should return 400 when posting a draft whose BANK slot has no account, leaving it DRAFT")
        void shouldReturn400WhenPostingWithoutAccountSlots() throws Exception {
            APIResponse createResponse = apiContext.post("/api/drafts",
                    jsonBody(draftRequest(ELECTRICITY, 80000, "Missing account slots test", null)));
            assertThat(createResponse.status()).as(createResponse.text()).isEqualTo(201);
            String transactionId = json(createResponse).get("transactionId").asText();

            APIResponse postResponse = apiContext.post("/api/transactions/" + transactionId + "/post", auth());

            assertApiError(postResponse, 400, "INVALID_REQUEST", "Account not specified for template line: BANK");
            assertThat(transactionColumn(transactionId, "status")).isEqualTo("DRAFT");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM journal_entries WHERE id_transaction = ?::uuid",
                    Long.class, transactionId)).isZero();
        }

        @Test
        @DisplayName("Should reject direct draft with future date without creating a transaction")
        void shouldRejectFutureDate() throws Exception {
            String description = "Future date draft " + UUID.randomUUID();
            Map<String, Object> request = draftRequest(ELECTRICITY, 50000, description, null);
            request.put("transactionDate", LocalDate.now().plusDays(1).toString());

            APIResponse response = apiContext.post("/api/drafts", jsonBody(request));

            assertApiError(response, 400, "INVALID_REQUEST", "Transaction date cannot be in the future");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE description = ?",
                    Long.class, description)).isZero();
        }
    }

    @Nested
    @DisplayName("GET /api/transactions/{id}/journal-preview")
    class JournalPreview {

        @Test
        @DisplayName("Should preview the journal lines of a DRAFT transaction without posting it")
        void shouldReturnJournalPreview() throws Exception {
            APIResponse createResponse = apiContext.post("/api/drafts", jsonBody(draftRequest(
                    ELECTRICITY, 95000, "Journal preview test", Map.of("BANK", accountId("1.1.02")))));
            assertThat(createResponse.status()).as(createResponse.text()).isEqualTo(201);
            String transactionId = json(createResponse).get("transactionId").asText();

            APIResponse previewResponse = apiContext.get(
                    "/api/transactions/" + transactionId + "/journal-preview", auth());

            assertThat(previewResponse.status()).as(previewResponse.text()).isEqualTo(200);
            JsonNode preview = json(previewResponse);
            assertThat(preview.get("valid").asBoolean()).isTrue();
            assertThat(preview.get("errors")).isEmpty();
            List<String> lines = new ArrayList<>();
            for (JsonNode e : preview.get("entries")) {
                lines.add(e.get("accountCode").asText() + " " + e.get("accountName").asText() + " "
                        + amount(e.get("debitAmount")) + "/" + amount(e.get("creditAmount")));
            }
            assertThat(lines).containsExactlyInAnyOrder(
                    "5.1.07 Beban Listrik 95000/0", "1.1.02 Bank BCA 0/95000");
            assertThat(amount(preview.get("totalDebit"))).isEqualTo("95000");
            assertThat(amount(preview.get("totalCredit"))).isEqualTo("95000");
            assertThat(transactionColumn(transactionId, "status")).isEqualTo("DRAFT");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM journal_entries WHERE id_transaction = ?::uuid",
                    Long.class, transactionId)).isZero();
        }
    }

    @Nested
    @DisplayName("POST /api/transactions/{id}/void")
    class VoidTransaction {

        @Test
        @DisplayName("Should void a POSTED transaction and record reason and notes")
        void shouldVoidPostedTransaction() throws Exception {
            String transactionId = createAndPostTransaction("To be voided");

            APIResponse response = apiContext.post("/api/transactions/" + transactionId + "/void",
                    jsonBody(Map.of("reason", "INPUT_ERROR", "notes", "Wrong account used")));

            assertThat(response.status()).as(response.text()).isEqualTo(200);
            JsonNode body = json(response);
            assertThat(body.get("status").asText()).isEqualTo("VOID");
            assertThat(body.get("transactionId").asText()).isEqualTo(transactionId);
            assertThat(transactionColumn(transactionId, "status")).isEqualTo("VOID");
            assertThat(transactionColumn(transactionId, "void_reason")).isEqualTo("INPUT_ERROR");
            assertThat(transactionColumn(transactionId, "void_notes")).isEqualTo("Wrong account used");
        }

        @Test
        @DisplayName("Should reject void on DRAFT transaction and leave it DRAFT")
        void shouldRejectVoidOnDraftTransaction() throws Exception {
            String transactionId = createDraftTransaction();

            APIResponse response = apiContext.post("/api/transactions/" + transactionId + "/void",
                    jsonBody(Map.of("reason", "DUPLICATE")));

            assertApiError(response, 409, "CONFLICT", "Only posted transactions can be voided");
            assertThat(transactionColumn(transactionId, "status")).isEqualTo("DRAFT");
            assertThat(transactionColumn(transactionId, "void_reason")).isNull();
        }
    }

    @Nested
    @DisplayName("GET /api/templates")
    class GetTemplates {

        @Test
        @DisplayName("Should return the seeded lines of a template by id")
        void shouldReturnTemplateLinesInGetById() throws Exception {
            String templateId = templateId(FIXED_ASSET);

            APIResponse response = apiContext.get("/api/templates/" + templateId, auth());

            assertThat(response.status()).as(response.text()).isEqualTo(200);
            JsonNode body = json(response);
            assertThat(body.get("name").asText()).isEqualTo(FIXED_ASSET);
            assertThat(templateLines(body)).containsExactly(
                    "1 DEBIT - ASET_TETAP assetCost", "2 CREDIT - BANK assetCost");
        }

        @Test
        @DisplayName("Should include each template's lines in the template list")
        void shouldReturnTemplateLinesInList() throws Exception {
            APIResponse response = apiContext.get("/api/templates", auth());

            assertThat(response.status()).as(response.text()).isEqualTo(200);
            JsonNode electricity = null;
            for (JsonNode t : json(response)) {
                if (t.get("name").asText().equals(ELECTRICITY)) {
                    electricity = t;
                }
            }
            assertThat(electricity).as("Template %s missing from /api/templates", ELECTRICITY).isNotNull();
            assertThat(electricity.get("id").asText()).isEqualTo(templateId(ELECTRICITY));
            assertThat(templateLines(electricity)).containsExactly(
                    "1 DEBIT 5.1.07 - amount", "2 CREDIT - BANK amount");
        }
    }

    @Nested
    @DisplayName("DETAILED template (formula variables)")
    class DetailedTemplate {

        @Test
        @DisplayName("Should create draft with assetCost variable and post it at that amount")
        void shouldCreateDraftWithVariablesAndPost() throws Exception {
            Map<String, Object> request = draftRequest(FIXED_ASSET, 3681200,
                    "Pembelian laptop Lenovo untuk operasional kantor", fixedAssetSlots());
            request.put("variables", Map.of("assetCost", 3681200));

            APIResponse createResponse = apiContext.post("/api/drafts", jsonBody(request));

            assertThat(createResponse.status()).as(createResponse.text()).isEqualTo(201);
            JsonNode createBody = json(createResponse);
            assertThat(createBody.get("status").asText()).isEqualTo("DRAFT");

            JsonNode postBody = post(createBody.get("transactionId").asText());

            assertThat(postBody.get("status").asText()).isEqualTo("POSTED");
            assertThat(entries(postBody)).containsExactlyInAnyOrder(
                    entry("1.2.01", "3681200", "0"), entry("1.1.02", "0", "3681200"));
        }

        @Test
        @DisplayName("Should create and post directly with assetCost variable")
        void shouldPostDirectlyWithVariables() throws Exception {
            Map<String, Object> request = directRequest(FIXED_ASSET, 5500000, "Pembelian monitor Dell 27 inch");
            request.put("accountSlots", fixedAssetSlots());
            request.put("variables", Map.of("assetCost", 5500000));

            APIResponse response = apiContext.post("/api/transactions", jsonBody(request));

            assertThat(response.status()).as(response.text()).isEqualTo(201);
            JsonNode body = json(response);
            assertThat(body.get("status").asText()).isEqualTo("POSTED");
            assertThat(entries(body)).containsExactlyInAnyOrder(
                    entry("1.2.01", "5500000", "0"), entry("1.1.02", "0", "5500000"));
        }
    }

    // ========== Helpers ==========

    private RequestOptions auth() {
        return RequestOptions.create().setHeader("Authorization", "Bearer " + accessToken);
    }

    private RequestOptions jsonBody(Object data) {
        return RequestOptions.create()
                .setHeader("Content-Type", "application/json")
                .setHeader("Authorization", "Bearer " + accessToken)
                .setData(data);
    }

    private APIResponse patch(String path, Map<String, Object> data) {
        return apiContext.patch(path, jsonBody(data));
    }

    private APIResponse put(String transactionId, Map<String, Object> data) {
        return apiContext.put("/api/transactions/" + transactionId, jsonBody(data));
    }

    private JsonNode post(String transactionId) throws Exception {
        APIResponse response = apiContext.post("/api/transactions/" + transactionId + "/post", auth());
        assertThat(response.status()).as("Post failed: %s", response.text()).isEqualTo(200);
        return json(response);
    }

    private void assertApiError(APIResponse response, int status, String code, String message) throws Exception {
        assertThat(response.status()).as(response.text()).isEqualTo(status);
        JsonNode body = json(response);
        assertThat(body.get("error").asText()).as(response.text()).isEqualTo(code);
        assertThat(body.get("message").asText()).as(response.text()).isEqualTo(message);
    }

    private JsonNode json(APIResponse response) throws Exception {
        return objectMapper.readTree(response.text());
    }

    private static String amount(JsonNode node) {
        return new BigDecimal(node.asText()).stripTrailingZeros().toPlainString();
    }

    private static String entry(String accountCode, String debit, String credit) {
        return accountCode + " D" + debit + " C" + credit;
    }

    private static List<String> entries(JsonNode transactionBody) {
        List<String> result = new ArrayList<>();
        for (JsonNode e : transactionBody.get("journalEntries")) {
            result.add(entry(e.get("accountCode").asText(), amount(e.get("debitAmount")), amount(e.get("creditAmount"))));
        }
        return result;
    }

    private static List<String> templateLines(JsonNode template) {
        List<String> result = new ArrayList<>();
        for (JsonNode l : template.get("lines")) {
            result.add(l.get("lineOrder").asInt() + " " + l.get("position").asText() + " "
                    + orDash(l.get("accountCode")) + " " + orDash(l.get("accountHint")) + " "
                    + l.get("formula").asText());
        }
        return result;
    }

    private static String orDash(JsonNode node) {
        return node.isNull() || node.asText().isEmpty() ? "-" : node.asText();
    }

    private String templateId(String name) {
        return templateRepository.findByTemplateNameAndIsCurrentVersionTrue(name)
                .orElseThrow(() -> new IllegalStateException("Seeded template missing: " + name))
                .getId().toString();
    }

    private String accountId(String code) {
        return accountRepository.findByAccountCode(code)
                .orElseThrow(() -> new IllegalStateException("Seeded account missing: " + code))
                .getId().toString();
    }

    private Map<String, String> fixedAssetSlots() {
        return Map.of("ASET_TETAP", accountId("1.2.01"), "BANK", accountId("1.1.02"));
    }

    private String draftColumn(String draftId, String column) {
        return jdbc.queryForObject("SELECT " + column + "::text FROM draft_transactions WHERE id = ?::uuid",
                String.class, draftId);
    }

    private String transactionColumn(String transactionId, String column) {
        return jdbc.queryForObject("SELECT " + column + "::text FROM transactions WHERE id = ?::uuid",
                String.class, transactionId);
    }

    private String transactionTemplateName(String transactionId) {
        return jdbc.queryForObject("SELECT jt.template_name FROM transactions t "
                + "JOIN journal_templates jt ON jt.id = t.id_journal_template WHERE t.id = ?::uuid",
                String.class, transactionId);
    }

    private List<String> mappedAccountCodes(String transactionId) {
        return jdbc.queryForList("SELECT a.account_code FROM transaction_account_mappings m "
                + "JOIN chart_of_accounts a ON a.id = m.id_account WHERE m.id_transaction = ?::uuid",
                String.class, transactionId);
    }

    private Map<String, Object> directRequest(String template, int amount, String description) {
        Map<String, Object> request = new HashMap<>();
        request.put("templateId", templateId(template));
        request.put("merchant", "Correction Test Merchant");
        request.put("amount", amount);
        request.put("transactionDate", DATE);
        request.put("description", description);
        request.put("source", "correction-test");
        request.put("userApproved", true);
        return request;
    }

    private Map<String, Object> draftRequest(String template, int amount, String description,
                                             Map<String, String> accountSlots) {
        Map<String, Object> request = new HashMap<>();
        request.put("templateId", templateId(template));
        request.put("description", description);
        request.put("amount", amount);
        request.put("transactionDate", DATE);
        if (accountSlots != null) {
            request.put("accountSlots", accountSlots);
        }
        return request;
    }

    private String createPendingDraft(String merchant, int amount) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("merchant", merchant);
        request.put("amount", amount);
        request.put("transactionDate", DATE);
        request.put("currency", "IDR");
        request.put("confidence", 0.95);
        request.put("source", "correction-test");

        APIResponse response = apiContext.post("/api/drafts/from-text", jsonBody(request));
        assertThat(response.status()).as(response.text()).isEqualTo(201);
        return json(response).get("draftId").asText();
    }

    private JsonNode approveDraft(String draftId, String template, String description, int amount) throws Exception {
        APIResponse response = apiContext.post("/api/drafts/" + draftId + "/approve", jsonBody(Map.of(
                "templateId", templateId(template),
                "description", description,
                "amount", amount)));
        assertThat(response.status()).as("Approve draft %s failed: %s", draftId, response.text()).isEqualTo(200);
        return json(response);
    }

    /** DRAFT transaction for {@value #ELECTRICITY}, amount 85000, dated {@value #DATE}, no account mappings. */
    private String createDraftTransaction() throws Exception {
        String draftId = createPendingDraft("Draft Transaction Merchant", 85000);
        return approveDraft(draftId, ELECTRICITY, "Draft transaction for correction test", 85000)
                .get("transactionId").asText();
    }

    private String createAndPostTransaction(String description) throws Exception {
        Map<String, Object> request = directRequest(ELECTRICITY, 100000, description);
        request.put("accountSlots", Map.of("BANK", accountId("1.1.02")));

        APIResponse response = apiContext.post("/api/transactions", jsonBody(request));
        assertThat(response.status()).as(response.text()).isEqualTo(201);
        return json(response).get("transactionId").asText();
    }

    private String authenticateViaDeviceFlow() throws Exception {
        APIResponse codeResponse = apiContext.post("/api/device/code",
                RequestOptions.create()
                        .setHeader("Content-Type", "application/json")
                        .setData(Map.of("clientId", "correction-test")));

        assertThat(codeResponse.ok()).isTrue();

        JsonNode codeData = objectMapper.readTree(codeResponse.text());
        String deviceCode = codeData.get("deviceCode").asText();
        String userCode = codeData.get("userCode").asText();

        loginAsAdmin();
        navigateTo("/device?code=" + userCode);
        waitForPageLoad();

        page.locator("input[name='deviceName']").fill("Correction Test Device");
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
