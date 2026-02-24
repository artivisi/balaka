package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
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
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests for draft and transaction correction API endpoints.
 * Tests:
 * - PATCH /api/drafts/{id} (update PENDING draft)
 * - PUT /api/transactions/{id} (update DRAFT transaction)
 * - DELETE /api/transactions/{id} (delete DRAFT transaction)
 */
@Slf4j
@DisplayName("Draft & Transaction Correction API - Functional Tests")
@Import(ServiceTestDataInitializer.class)
class DraftAndTransactionCorrectionApiTest extends PlaywrightTestBase {

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

            Map<String, Object> patch = Map.of("merchantName", "Corrected Merchant Name");

            APIResponse response = apiContext.patch("/api/drafts/" + draftId,
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Authorization", "Bearer " + accessToken)
                            .setData(patch));

            assertThat(response.ok()).isTrue();

            JsonNode body = objectMapper.readTree(response.text());
            assertThat(body.get("merchant").asText()).isEqualTo("Corrected Merchant Name");
            assertThat(body.get("status").asText()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("Should update amount on PENDING draft")
        void shouldUpdateAmount() throws Exception {
            String draftId = createPendingDraft("Amount Test Merchant", 50000);

            Map<String, Object> patch = Map.of("amount", 75000);

            APIResponse response = apiContext.patch("/api/drafts/" + draftId,
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Authorization", "Bearer " + accessToken)
                            .setData(patch));

            assertThat(response.ok()).isTrue();

            JsonNode body = objectMapper.readTree(response.text());
            assertThat(body.get("amount").asInt()).isEqualTo(75000);
        }

        @Test
        @DisplayName("Should update suggested template on PENDING draft")
        void shouldUpdateSuggestedTemplate() throws Exception {
            String draftId = createPendingDraft("Template Test Merchant", 50000);
            String templateId = getFirstTemplateId();

            Map<String, Object> patch = Map.of("suggestedTemplateId", templateId);

            APIResponse response = apiContext.patch("/api/drafts/" + draftId,
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Authorization", "Bearer " + accessToken)
                            .setData(patch));

            assertThat(response.ok()).isTrue();

            JsonNode body = objectMapper.readTree(response.text());
            assertThat(body.get("suggestedTemplate")).isNotNull();
            assertThat(body.get("suggestedTemplate").get("id").asText()).isEqualTo(templateId);
        }

        @Test
        @DisplayName("Should update multiple fields at once")
        void shouldUpdateMultipleFields() throws Exception {
            String draftId = createPendingDraft("Multi Update Merchant", 30000);

            Map<String, Object> patch = new HashMap<>();
            patch.put("merchantName", "Updated Merchant");
            patch.put("amount", 99000);
            patch.put("description", "Updated description");

            APIResponse response = apiContext.patch("/api/drafts/" + draftId,
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Authorization", "Bearer " + accessToken)
                            .setData(patch));

            assertThat(response.ok()).isTrue();

            JsonNode body = objectMapper.readTree(response.text());
            assertThat(body.get("merchant").asText()).isEqualTo("Updated Merchant");
            assertThat(body.get("amount").asInt()).isEqualTo(99000);
        }

        @Test
        @DisplayName("Should reject update on non-PENDING draft")
        void shouldRejectUpdateOnApprovedDraft() throws Exception {
            // Create and approve a draft
            String draftId = createPendingDraft("Approved Draft Merchant", 50000);
            String templateId = getFirstTemplateId();
            approveDraft(draftId, templateId);

            Map<String, Object> patch = Map.of("merchantName", "Should Not Work");

            APIResponse response = apiContext.patch("/api/drafts/" + draftId,
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Authorization", "Bearer " + accessToken)
                            .setData(patch));

            assertThat(response.status()).isGreaterThanOrEqualTo(400);
        }
    }

    @Nested
    @DisplayName("PUT /api/transactions/{id}")
    class PutTransaction {

        @Test
        @DisplayName("Should reclassify DRAFT transaction template")
        void shouldReclassifyTemplate() throws Exception {
            String transactionId = createDraftTransaction();
            String newTemplateId = getSecondTemplateId();

            Map<String, Object> update = Map.of("templateId", newTemplateId);

            APIResponse response = apiContext.put("/api/transactions/" + transactionId,
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Authorization", "Bearer " + accessToken)
                            .setData(update));

            assertThat(response.ok()).isTrue();

            JsonNode body = objectMapper.readTree(response.text());
            assertThat(body.get("status").asText()).isEqualTo("DRAFT");
        }

        @Test
        @DisplayName("Should update DRAFT transaction description")
        void shouldUpdateDescription() throws Exception {
            String transactionId = createDraftTransaction();

            Map<String, Object> update = Map.of("description", "Corrected: Bayar PPN Januari 2026");

            APIResponse response = apiContext.put("/api/transactions/" + transactionId,
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Authorization", "Bearer " + accessToken)
                            .setData(update));

            assertThat(response.ok()).isTrue();

            JsonNode body = objectMapper.readTree(response.text());
            assertThat(body.get("description").asText()).isEqualTo("Corrected: Bayar PPN Januari 2026");
        }

        @Test
        @DisplayName("Should update DRAFT transaction amount")
        void shouldUpdateAmount() throws Exception {
            String transactionId = createDraftTransaction();

            Map<String, Object> update = Map.of("amount", 150000);

            APIResponse response = apiContext.put("/api/transactions/" + transactionId,
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Authorization", "Bearer " + accessToken)
                            .setData(update));

            assertThat(response.ok()).isTrue();

            JsonNode body = objectMapper.readTree(response.text());
            assertThat(body.get("amount").asInt()).isEqualTo(150000);
        }

        @Test
        @DisplayName("Should update multiple DRAFT transaction fields")
        void shouldUpdateMultipleFields() throws Exception {
            String transactionId = createDraftTransaction();

            Map<String, Object> update = new HashMap<>();
            update.put("description", "Updated remarks");
            update.put("amount", 200000);
            update.put("transactionDate", "2026-02-15");

            APIResponse response = apiContext.put("/api/transactions/" + transactionId,
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Authorization", "Bearer " + accessToken)
                            .setData(update));

            assertThat(response.ok()).isTrue();

            JsonNode body = objectMapper.readTree(response.text());
            assertThat(body.get("description").asText()).isEqualTo("Updated remarks");
            assertThat(body.get("amount").asInt()).isEqualTo(200000);
        }

        @Test
        @DisplayName("Should reject update on POSTED transaction")
        void shouldRejectUpdateOnPostedTransaction() throws Exception {
            String transactionId = createAndPostTransaction();

            Map<String, Object> update = Map.of("description", "Should Not Work");

            APIResponse response = apiContext.put("/api/transactions/" + transactionId,
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Authorization", "Bearer " + accessToken)
                            .setData(update));

            assertThat(response.status()).isGreaterThanOrEqualTo(400);
        }

        @Test
        @DisplayName("Should update DRAFT transaction with accountSlots")
        void shouldUpdateWithLineAccountOverrides() throws Exception {
            String transactionId = createDraftTransaction();
            String accountId = getFirstAccountId();

            Map<String, Object> update = new HashMap<>();
            update.put("accountSlots", Map.of("BANK", accountId));

            APIResponse response = apiContext.put("/api/transactions/" + transactionId,
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Authorization", "Bearer " + accessToken)
                            .setData(update));

            assertThat(response.ok())
                    .as("Update with accountSlots failed: %d %s", response.status(), response.text())
                    .isTrue();
        }

        @Test
        @DisplayName("Should reclassify template with accountSlots")
        void shouldReclassifyWithAccountSlots() throws Exception {
            String transactionId = createDraftTransaction();
            String templateId = getSecondTemplateId();
            String accountId = getFirstAccountId();

            Map<String, Object> update = new HashMap<>();
            update.put("templateId", templateId);
            update.put("accountSlots", Map.of("BANK", accountId));

            APIResponse response = apiContext.put("/api/transactions/" + transactionId,
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Authorization", "Bearer " + accessToken)
                            .setData(update));

            assertThat(response.ok())
                    .as("Reclassify with overrides failed: %d %s", response.status(), response.text())
                    .isTrue();
        }

        @Test
        @DisplayName("Should re-apply accountSlots without 409 Conflict")
        void shouldReApplyAccountSlots() throws Exception {
            String transactionId = createDraftTransaction();
            String accountId = getFirstAccountId();

            Map<String, Object> update = new HashMap<>();
            update.put("accountSlots", Map.of("BANK", accountId));

            // First PUT
            APIResponse response1 = apiContext.put("/api/transactions/" + transactionId,
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Authorization", "Bearer " + accessToken)
                            .setData(update));

            assertThat(response1.ok())
                    .as("First PUT with accountSlots failed: %d %s", response1.status(), response1.text())
                    .isTrue();

            // Second PUT with same overrides — should NOT return 409
            APIResponse response2 = apiContext.put("/api/transactions/" + transactionId,
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Authorization", "Bearer " + accessToken)
                            .setData(update));

            assertThat(response2.ok())
                    .as("Second PUT with accountSlots returned %d (expected 200): %s",
                            response2.status(), response2.text())
                    .isTrue();
        }

        @Test
        @DisplayName("Should reject future transaction date")
        void shouldRejectFutureDate() throws Exception {
            String transactionId = createDraftTransaction();

            Map<String, Object> update = Map.of("transactionDate", "2099-12-31");

            APIResponse response = apiContext.put("/api/transactions/" + transactionId,
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Authorization", "Bearer " + accessToken)
                            .setData(update));

            assertThat(response.status()).isGreaterThanOrEqualTo(400);
        }
    }

    @Nested
    @DisplayName("DELETE /api/transactions/{id}")
    class DeleteTransaction {

        @Test
        @DisplayName("Should delete DRAFT transaction")
        void shouldDeleteDraftTransaction() throws Exception {
            String transactionId = createDraftTransaction();

            APIResponse response = apiContext.delete("/api/transactions/" + transactionId,
                    RequestOptions.create()
                            .setHeader("Authorization", "Bearer " + accessToken));

            assertThat(response.status())
                    .as("Delete failed: %d %s", response.status(), response.text())
                    .isEqualTo(204);
        }

        @Test
        @DisplayName("Should create and post transaction with accountSlots")
        void shouldPostWithAccountSlots() throws Exception {
            String templateId = getFirstTemplateId();
            String accountId = getFirstAccountId();

            Map<String, Object> request = new HashMap<>();
            request.put("templateId", templateId);
            request.put("merchant", "Override Test Merchant");
            request.put("amount", 75000);
            request.put("transactionDate", "2026-02-12");
            request.put("description", "Transaction with account overrides");
            request.put("source", "correction-test");
            request.put("userApproved", true);
            request.put("accountSlots", Map.of("BANK", accountId));

            APIResponse response = apiContext.post("/api/transactions",
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Authorization", "Bearer " + accessToken)
                            .setData(request));

            assertThat(response.status())
                    .as("Create with overrides failed: %d %s", response.status(), response.text())
                    .isEqualTo(201);

            JsonNode body = objectMapper.readTree(response.text());
            assertThat(body.get("status").asText()).isEqualTo("POSTED");
        }

        @Test
        @DisplayName("Should reject delete on POSTED transaction")
        void shouldRejectDeleteOnPostedTransaction() throws Exception {
            String transactionId = createAndPostTransaction();

            APIResponse response = apiContext.delete("/api/transactions/" + transactionId,
                    RequestOptions.create()
                            .setHeader("Authorization", "Bearer " + accessToken));

            assertThat(response.status()).isGreaterThanOrEqualTo(400);
        }
    }

    @Nested
    @DisplayName("POST /api/drafts (direct draft creation)")
    class PostDraftDirect {

        @Test
        @DisplayName("Should create DRAFT transaction directly with template and post it")
        void shouldCreateDraftAndPost() throws Exception {
            String templateId = getFirstTemplateId();
            String accountId = getFirstAccountId();

            Map<String, Object> request = new HashMap<>();
            request.put("templateId", templateId);
            request.put("description", "Direct draft creation test");
            request.put("amount", 120000);
            request.put("transactionDate", "2026-02-15");
            request.put("accountSlots", Map.of("BANK", accountId));

            APIResponse createResponse = apiContext.post("/api/drafts",
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Authorization", "Bearer " + accessToken)
                            .setData(request));

            assertThat(createResponse.status())
                    .as("Create draft failed: %d %s", createResponse.status(), createResponse.text())
                    .isEqualTo(201);

            JsonNode createBody = objectMapper.readTree(createResponse.text());
            assertThat(createBody.get("status").asText()).isEqualTo("DRAFT");
            String transactionId = createBody.get("transactionId").asText();
            assertThat(transactionId).isNotNull();

            // Now post it
            APIResponse postResponse = apiContext.post("/api/transactions/" + transactionId + "/post",
                    RequestOptions.create()
                            .setHeader("Authorization", "Bearer " + accessToken));

            assertThat(postResponse.ok())
                    .as("Post failed: %d %s", postResponse.status(), postResponse.text())
                    .isTrue();

            JsonNode postBody = objectMapper.readTree(postResponse.text());
            assertThat(postBody.get("status").asText()).isEqualTo("POSTED");
            assertThat(postBody.get("journalEntries").size()).isGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("Should return 400 when posting draft without required accountSlots")
        void shouldReturn400WhenPostingWithoutAccountSlots() throws Exception {
            String templateId = getTemplateWithHintLine();

            // Create draft WITHOUT accountSlots — template has hint lines that need accounts
            Map<String, Object> request = new HashMap<>();
            request.put("templateId", templateId);
            request.put("description", "Missing account slots test");
            request.put("amount", 80000);
            request.put("transactionDate", "2026-02-15");

            APIResponse createResponse = apiContext.post("/api/drafts",
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Authorization", "Bearer " + accessToken)
                            .setData(request));

            assertThat(createResponse.status())
                    .as("Create draft failed: %d %s", createResponse.status(), createResponse.text())
                    .isEqualTo(201);

            JsonNode createBody = objectMapper.readTree(createResponse.text());
            String transactionId = createBody.get("transactionId").asText();

            // Attempt to post — should return 400 because hint line has no account
            APIResponse postResponse = apiContext.post("/api/transactions/" + transactionId + "/post",
                    RequestOptions.create()
                            .setHeader("Authorization", "Bearer " + accessToken));

            assertThat(postResponse.status())
                    .as("Expected 400 but got %d: %s", postResponse.status(), postResponse.text())
                    .isEqualTo(400);
        }

        @Test
        @DisplayName("Should reject direct draft with future date")
        void shouldRejectFutureDate() throws Exception {
            String templateId = getFirstTemplateId();

            Map<String, Object> request = new HashMap<>();
            request.put("templateId", templateId);
            request.put("description", "Future date test");
            request.put("amount", 50000);
            request.put("transactionDate", "2099-12-31");

            APIResponse response = apiContext.post("/api/drafts",
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Authorization", "Bearer " + accessToken)
                            .setData(request));

            assertThat(response.status()).isGreaterThanOrEqualTo(400);
        }
    }

    @Nested
    @DisplayName("POST /api/drafts/{id}/approve - transactionId")
    class ApproveTransactionId {

        @Test
        @DisplayName("Should return transactionId after approval")
        void shouldReturnTransactionIdAfterApproval() throws Exception {
            String draftId = createPendingDraft("TransactionId Test Merchant", 60000);
            String templateId = getFirstTemplateId();

            Map<String, Object> request = Map.of(
                    "templateId", templateId,
                    "description", "Approve with transactionId test",
                    "amount", 60000
            );

            APIResponse response = apiContext.post("/api/drafts/" + draftId + "/approve",
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Authorization", "Bearer " + accessToken)
                            .setData(request));

            assertThat(response.ok())
                    .as("Approve failed: %d %s", response.status(), response.text())
                    .isTrue();

            JsonNode body = objectMapper.readTree(response.text());
            assertThat(body.get("status").asText()).isEqualTo("APPROVED");
            assertThat(body.has("transactionId")).isTrue();
            assertThat(body.get("transactionId").isNull()).isFalse();
        }
    }

    @Nested
    @DisplayName("GET /api/transactions/{id}/journal-preview")
    class JournalPreview {

        @Test
        @DisplayName("Should return journal preview for DRAFT transaction")
        void shouldReturnJournalPreview() throws Exception {
            // Create a draft with account overrides so the preview can resolve accounts
            String templateId = getFirstTemplateId();
            String accountId = getFirstAccountId();

            Map<String, Object> request = new HashMap<>();
            request.put("templateId", templateId);
            request.put("description", "Journal preview test");
            request.put("amount", 95000);
            request.put("transactionDate", "2026-02-10");
            request.put("accountSlots", Map.of("BANK", accountId));

            APIResponse createResponse = apiContext.post("/api/drafts",
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Authorization", "Bearer " + accessToken)
                            .setData(request));

            assertThat(createResponse.status())
                    .as("Create draft failed: %d %s", createResponse.status(), createResponse.text())
                    .isEqualTo(201);

            JsonNode createBody = objectMapper.readTree(createResponse.text());
            String transactionId = createBody.get("transactionId").asText();

            // Get journal preview
            APIResponse previewResponse = apiContext.get("/api/transactions/" + transactionId + "/journal-preview",
                    RequestOptions.create()
                            .setHeader("Authorization", "Bearer " + accessToken));

            assertThat(previewResponse.ok())
                    .as("Journal preview failed: %d %s", previewResponse.status(), previewResponse.text())
                    .isTrue();

            JsonNode preview = objectMapper.readTree(previewResponse.text());
            assertThat(preview.get("valid").asBoolean()).isTrue();
            assertThat(preview.get("entries").isArray()).isTrue();
            assertThat(preview.get("entries").size()).isGreaterThanOrEqualTo(2);
            assertThat(preview.get("totalDebit").asDouble()).isGreaterThan(0);
            assertThat(preview.get("totalCredit").asDouble()).isGreaterThan(0);

            // Verify entries have account codes
            JsonNode firstEntry = preview.get("entries").get(0);
            assertThat(firstEntry.has("accountCode")).isTrue();
            assertThat(firstEntry.has("accountName")).isTrue();
            assertThat(firstEntry.has("debitAmount")).isTrue();
            assertThat(firstEntry.has("creditAmount")).isTrue();
        }
    }

    @Nested
    @DisplayName("POST /api/transactions/{id}/void")
    class VoidTransaction {

        @Test
        @DisplayName("Should void a POSTED transaction")
        void shouldVoidPostedTransaction() throws Exception {
            String transactionId = createAndPostTransaction();

            Map<String, Object> voidRequest = Map.of(
                    "reason", "INPUT_ERROR",
                    "notes", "Wrong account used"
            );

            APIResponse response = apiContext.post("/api/transactions/" + transactionId + "/void",
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Authorization", "Bearer " + accessToken)
                            .setData(voidRequest));

            assertThat(response.ok())
                    .as("Void failed: %d %s", response.status(), response.text())
                    .isTrue();

            JsonNode body = objectMapper.readTree(response.text());
            assertThat(body.get("status").asText()).isEqualTo("VOID");
            assertThat(body.get("transactionId").asText()).isEqualTo(transactionId);
        }

        @Test
        @DisplayName("Should reject void on DRAFT transaction")
        void shouldRejectVoidOnDraftTransaction() throws Exception {
            String transactionId = createDraftTransaction();

            Map<String, Object> voidRequest = Map.of(
                    "reason", "DUPLICATE"
            );

            APIResponse response = apiContext.post("/api/transactions/" + transactionId + "/void",
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Authorization", "Bearer " + accessToken)
                            .setData(voidRequest));

            assertThat(response.status())
                    .as("Expected error but got %d: %s", response.status(), response.text())
                    .isGreaterThanOrEqualTo(400);
        }
    }

    @Nested
    @DisplayName("GET /api/templates")
    class GetTemplates {

        @Test
        @DisplayName("Should return template lines in GET /api/templates/{id}")
        void shouldReturnTemplateLinesInGetById() throws Exception {
            String templateId = getFirstTemplateId();

            APIResponse response = apiContext.get("/api/templates/" + templateId,
                    RequestOptions.create()
                            .setHeader("Authorization", "Bearer " + accessToken));

            assertThat(response.ok()).isTrue();

            JsonNode body = objectMapper.readTree(response.text());
            assertThat(body.has("lines")).isTrue();
            JsonNode lines = body.get("lines");
            assertThat(lines.isArray()).isTrue();
            assertThat(lines.size()).isGreaterThanOrEqualTo(2);

            // Verify line structure
            JsonNode firstLine = lines.get(0);
            assertThat(firstLine.has("lineOrder")).isTrue();
            assertThat(firstLine.has("position")).isTrue();
            assertThat(firstLine.has("formula")).isTrue();
        }

        @Test
        @DisplayName("Should return template lines in GET /api/templates list")
        void shouldReturnTemplateLinesInList() throws Exception {
            APIResponse response = apiContext.get("/api/templates",
                    RequestOptions.create()
                            .setHeader("Authorization", "Bearer " + accessToken));

            assertThat(response.ok()).isTrue();

            JsonNode templates = objectMapper.readTree(response.text());
            assertThat(templates.size()).isGreaterThan(0);

            JsonNode firstTemplate = templates.get(0);
            assertThat(firstTemplate.has("lines")).isTrue();
            assertThat(firstTemplate.get("lines").size()).isGreaterThanOrEqualTo(2);
        }
    }

    @Nested
    @DisplayName("DETAILED template (formula variables)")
    class DetailedTemplate {

        @Test
        @DisplayName("Should create draft with DETAILED template variables and post successfully")
        void shouldCreateDraftWithVariablesAndPost() throws Exception {
            // Find "Pembelian Aset Tetap" template (DETAILED, formula: assetCost)
            String templateId = getDetailedTemplateId();
            Map<String, String> accountSlotIds = getAccountSlotsForDetailedTemplate(templateId);

            Map<String, Object> request = new HashMap<>();
            request.put("templateId", templateId);
            request.put("description", "Pembelian laptop Lenovo untuk operasional kantor");
            request.put("amount", 3681200);
            request.put("transactionDate", "2026-02-15");
            request.put("accountSlots", accountSlotIds);
            request.put("variables", Map.of("assetCost", 3681200));

            // Create draft
            APIResponse createResponse = apiContext.post("/api/drafts",
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Authorization", "Bearer " + accessToken)
                            .setData(request));

            assertThat(createResponse.status())
                    .as("Create draft failed: %d %s", createResponse.status(), createResponse.text())
                    .isEqualTo(201);

            JsonNode createBody = objectMapper.readTree(createResponse.text());
            assertThat(createBody.get("status").asText()).isEqualTo("DRAFT");
            String transactionId = createBody.get("transactionId").asText();

            // Post the draft
            APIResponse postResponse = apiContext.post("/api/transactions/" + transactionId + "/post",
                    RequestOptions.create()
                            .setHeader("Authorization", "Bearer " + accessToken));

            assertThat(postResponse.ok())
                    .as("Post failed: %d %s", postResponse.status(), postResponse.text())
                    .isTrue();

            JsonNode postBody = objectMapper.readTree(postResponse.text());
            assertThat(postBody.get("status").asText()).isEqualTo("POSTED");
            assertThat(postBody.get("journalEntries").size()).isEqualTo(2);

            // Verify journal entries have correct amounts
            for (JsonNode entry : postBody.get("journalEntries")) {
                BigDecimal debit = new BigDecimal(entry.get("debitAmount").asText());
                BigDecimal credit = new BigDecimal(entry.get("creditAmount").asText());
                // One entry should be debit 3681200, the other credit 3681200
                if (debit.compareTo(BigDecimal.ZERO) > 0) {
                    assertThat(debit).isEqualByComparingTo(new BigDecimal("3681200"));
                } else {
                    assertThat(credit).isEqualByComparingTo(new BigDecimal("3681200"));
                }
            }
        }

        @Test
        @DisplayName("Should create and post transaction directly with DETAILED template variables")
        void shouldPostDirectlyWithVariables() throws Exception {
            String templateId = getDetailedTemplateId();
            Map<String, String> accountSlotIds = getAccountSlotsForDetailedTemplate(templateId);

            Map<String, Object> request = new HashMap<>();
            request.put("templateId", templateId);
            request.put("merchant", "Enterkomputer");
            request.put("amount", 5500000);
            request.put("transactionDate", "2026-02-12");
            request.put("description", "Pembelian monitor Dell 27 inch");
            request.put("source", "correction-test");
            request.put("userApproved", true);
            request.put("accountSlots", accountSlotIds);
            request.put("variables", Map.of("assetCost", 5500000));

            APIResponse response = apiContext.post("/api/transactions",
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Authorization", "Bearer " + accessToken)
                            .setData(request));

            assertThat(response.status())
                    .as("Create with variables failed: %d %s", response.status(), response.text())
                    .isEqualTo(201);

            JsonNode body = objectMapper.readTree(response.text());
            assertThat(body.get("status").asText()).isEqualTo("POSTED");
            assertThat(body.get("journalEntries").size()).isEqualTo(2);
        }
    }

    // ========== Helper methods ==========

    private String createPendingDraft(String merchant, int amount) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("merchant", merchant);
        request.put("amount", amount);
        request.put("transactionDate", "2026-02-12");
        request.put("currency", "IDR");
        request.put("confidence", 0.95);
        request.put("source", "correction-test");

        APIResponse response = apiContext.post("/api/drafts/from-text",
                RequestOptions.create()
                        .setHeader("Content-Type", "application/json")
                        .setHeader("Authorization", "Bearer " + accessToken)
                        .setData(request));

        assertThat(response.status()).isEqualTo(201);

        JsonNode body = objectMapper.readTree(response.text());
        return body.get("draftId").asText();
    }

    private void approveDraft(String draftId, String templateId) throws Exception {
        Map<String, Object> request = Map.of(
                "templateId", templateId,
                "description", "Approved via test",
                "amount", 50000
        );

        APIResponse response = apiContext.post("/api/drafts/" + draftId + "/approve",
                RequestOptions.create()
                        .setHeader("Content-Type", "application/json")
                        .setHeader("Authorization", "Bearer " + accessToken)
                        .setData(request));

        assertThat(response.ok())
                .as("Approve draft %s failed: %d %s", draftId, response.status(), response.text())
                .isTrue();
    }

    private String createDraftTransaction() throws Exception {
        // Create a draft via POST /api/drafts/from-text, approve it (creates Transaction in DRAFT),
        // but we need a DRAFT Transaction, not a POSTED one.
        // Use direct POST /api/transactions without userApproved to get a DRAFT.
        // Actually, POST /api/transactions posts immediately. Let's use the draft → approve flow
        // which creates a DRAFT Transaction, then post it separately.
        String draftId = createPendingDraft("Draft Transaction Merchant", 85000);
        String templateId = getFirstTemplateId();

        // Approve draft — this creates a Transaction in DRAFT status
        Map<String, Object> approveRequest = Map.of(
                "templateId", templateId,
                "description", "Draft transaction for correction test",
                "amount", 85000
        );
        APIResponse approveResponse = apiContext.post("/api/drafts/" + draftId + "/approve",
                RequestOptions.create()
                        .setHeader("Content-Type", "application/json")
                        .setHeader("Authorization", "Bearer " + accessToken)
                        .setData(approveRequest));

        assertThat(approveResponse.ok())
                .as("Approve draft failed: %d %s", approveResponse.status(), approveResponse.text())
                .isTrue();

        // Use analysis API to find the most recent DRAFT transaction
        APIResponse txListResponse = apiContext.get(
                "/api/analysis/transactions?status=DRAFT&size=1",
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + accessToken));

        assertThat(txListResponse.ok())
                .as("List transactions failed: %d %s", txListResponse.status(), txListResponse.text())
                .isTrue();

        JsonNode txList = objectMapper.readTree(txListResponse.text());
        // Response is wrapped: { "data": { "transactions": [...] } }
        JsonNode transactions = txList.get("data").get("transactions");
        assertThat(transactions.isArray()).isTrue();
        assertThat(transactions.size()).isGreaterThan(0);

        return transactions.get(0).get("id").asText();
    }

    private String createAndPostTransaction() throws Exception {
        String templateId = getFirstTemplateId();

        Map<String, Object> request = new HashMap<>();
        request.put("templateId", templateId);
        request.put("merchant", "Posted Transaction Merchant");
        request.put("amount", 100000);
        request.put("transactionDate", "2026-02-12");
        request.put("description", "Posted test transaction");
        request.put("source", "correction-test");
        request.put("userApproved", true);

        APIResponse response = apiContext.post("/api/transactions",
                RequestOptions.create()
                        .setHeader("Content-Type", "application/json")
                        .setHeader("Authorization", "Bearer " + accessToken)
                        .setData(request));

        assertThat(response.status()).isEqualTo(201);

        JsonNode body = objectMapper.readTree(response.text());
        return body.get("transactionId").asText();
    }

    private String getFirstTemplateId() throws Exception {
        APIResponse response = apiContext.get("/api/templates",
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + accessToken));

        assertThat(response.ok()).isTrue();

        JsonNode templates = objectMapper.readTree(response.text());
        assertThat(templates.size()).isGreaterThan(0);

        return templates.get(0).get("id").asText();
    }

    private String getFirstAccountId() throws Exception {
        APIResponse response = apiContext.get("/api/analysis/accounts",
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + accessToken));

        assertThat(response.ok()).isTrue();

        JsonNode body = objectMapper.readTree(response.text());
        JsonNode accounts = body.get("data").get("accounts");
        assertThat(accounts.isArray()).isTrue();
        assertThat(accounts.size()).isGreaterThan(0);

        return accounts.get(0).get("id").asText();
    }

    private String getSecondTemplateId() throws Exception {
        APIResponse response = apiContext.get("/api/templates",
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + accessToken));

        assertThat(response.ok()).isTrue();

        JsonNode templates = objectMapper.readTree(response.text());
        assertThat(templates.size()).isGreaterThan(1);

        return templates.get(1).get("id").asText();
    }

    /**
     * Find the DETAILED template (Pembelian Aset Tetap) that uses non-standard formula variables.
     */
    private String getDetailedTemplateId() throws Exception {
        APIResponse response = apiContext.get("/api/templates",
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + accessToken));

        assertThat(response.ok()).isTrue();

        JsonNode templates = objectMapper.readTree(response.text());
        for (JsonNode template : templates) {
            String name = template.get("name").asText();
            if (name.contains("Pembelian Aset Tetap")) {
                return template.get("id").asText();
            }
        }
        throw new RuntimeException("DETAILED template 'Pembelian Aset Tetap' not found in seed data");
    }

    /**
     * Find account IDs to fill the accountSlots for a DETAILED template.
     * Looks at template lines with accountHint and finds matching accounts.
     */
    private Map<String, String> getAccountSlotsForDetailedTemplate(String templateId) throws Exception {
        // Get template with lines
        APIResponse templateResponse = apiContext.get("/api/templates/" + templateId,
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + accessToken));
        assertThat(templateResponse.ok()).isTrue();

        JsonNode template = objectMapper.readTree(templateResponse.text());
        JsonNode lines = template.get("lines");

        // Get all accounts
        APIResponse accountsResponse = apiContext.get("/api/analysis/accounts",
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + accessToken));
        assertThat(accountsResponse.ok()).isTrue();

        JsonNode accountsBody = objectMapper.readTree(accountsResponse.text());
        JsonNode accounts = accountsBody.get("data").get("accounts");

        Map<String, String> slots = new HashMap<>();
        for (JsonNode line : lines) {
            if (line.has("accountHint") && !line.get("accountHint").isNull()
                    && !line.get("accountHint").asText().isBlank()) {
                String hint = line.get("accountHint").asText();
                // Find a suitable account based on hint
                String accountId = findAccountForHint(hint, accounts);
                slots.put(hint, accountId);
            }
        }

        assertThat(slots).as("No accountHint slots found in template").isNotEmpty();
        return slots;
    }

    private String findAccountForHint(String hint, JsonNode accounts) {
        // For ASET_TETAP, find an asset account; for BANK, find a bank/cash account
        String hintLower = hint.toLowerCase();
        for (JsonNode account : accounts) {
            String type = account.get("type").asText();
            String name = account.get("name").asText().toLowerCase();
            if (hintLower.contains("aset") && type.equals("ASSET")
                    && (name.contains("peralatan") || name.contains("aset tetap") || name.contains("komputer"))) {
                return account.get("id").asText();
            }
            if (hintLower.contains("bank") && type.equals("ASSET")
                    && (name.contains("bank") || name.contains("kas"))) {
                return account.get("id").asText();
            }
        }
        // Fallback: return first account of matching type
        for (JsonNode account : accounts) {
            String type = account.get("type").asText();
            if (hintLower.contains("aset") && type.equals("ASSET")) {
                return account.get("id").asText();
            }
            if (hintLower.contains("bank") && type.equals("ASSET")) {
                return account.get("id").asText();
            }
        }
        // Last resort: return any account
        return accounts.get(0).get("id").asText();
    }

    private String getTemplateWithHintLine() throws Exception {
        APIResponse response = apiContext.get("/api/templates",
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + accessToken));

        assertThat(response.ok()).isTrue();

        JsonNode templates = objectMapper.readTree(response.text());
        for (JsonNode template : templates) {
            JsonNode lines = template.get("lines");
            if (lines != null) {
                for (JsonNode line : lines) {
                    if (line.has("accountHint") && !line.get("accountHint").isNull()
                            && !line.get("accountHint").asText().isBlank()) {
                        return template.get("id").asText();
                    }
                }
            }
        }
        throw new RuntimeException("No template with accountHint found in seed data");
    }

    private String authenticateViaDeviceFlow() throws Exception {
        Map<String, String> codeRequest = Map.of("clientId", "correction-test");

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

        page.locator("input[name='deviceName']").fill("Correction Test Device");
        page.locator("button[type='submit']:has-text('Otorisasi Perangkat')").click();
        waitForPageLoad();

        Map<String, String> tokenRequest = Map.of("deviceCode", deviceCode);

        for (int i = 0; i < 10; i++) {
            Thread.sleep(2000);

            APIResponse tokenResponse = apiContext.post("/api/device/token",
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setData(tokenRequest));

            if (tokenResponse.ok()) {
                JsonNode tokenData = objectMapper.readTree(tokenResponse.text());
                return tokenData.get("accessToken").asText();
            }
        }

        throw new RuntimeException("Failed to get access token");
    }
}
