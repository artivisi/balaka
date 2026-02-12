package com.artivisi.accountingfinance.controller.api;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.dto.CreateFromReceiptRequest;
import com.artivisi.accountingfinance.dto.CreateFromTextRequest;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for DraftTransactionApiController.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "testuser", roles = {"USER"})
@DisplayName("DraftTransactionApiController Integration Tests")
class DraftTransactionApiControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private JournalTemplateRepository journalTemplateRepository;

    private ObjectMapper objectMapper = new ObjectMapper();

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .build();
    }

    @Nested
    @DisplayName("POST /api/drafts/from-receipt")
    class CreateFromReceiptTests {

        @Test
        @DisplayName("Should create draft from valid receipt data")
        void shouldCreateDraftFromValidReceipt() throws Exception {
            String requestJson = String.format("""
                    {
                        "merchant": "Starbucks Grand Indonesia",
                        "amount": 75000,
                        "transactionDate": "%s",
                        "currency": "IDR",
                        "items": ["Caffe Latte", "Croissant"],
                        "category": "Food & Beverage",
                        "confidence": 0.92,
                        "source": "claude-code",
                        "rawText": "STARBUCKS\\nTotal: 75000"
                    }
                    """, LocalDate.now());

            mockMvc.perform(post("/api/drafts/from-receipt")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.draftId").exists())
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.merchant").value("Starbucks Grand Indonesia"))
                    .andExpect(jsonPath("$.amount").value(75000))
                    .andExpect(jsonPath("$.confidence").value(0.92))
                    .andExpect(jsonPath("$.needsClarification").value(false));
        }

        @Test
        @DisplayName("Should reject missing merchant")
        void shouldRejectMissingMerchant() throws Exception {
            String invalidRequest = """
                    {
                        "amount": 75000,
                        "transactionDate": "2026-02-11",
                        "currency": "IDR",
                        "confidence": 0.92,
                        "source": "test"
                    }
                    """;

            mockMvc.perform(post("/api/drafts/from-receipt")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidRequest))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").exists());
        }

        @Test
        @DisplayName("Should reject invalid amount")
        void shouldRejectInvalidAmount() throws Exception {
            String invalidRequest = """
                    {
                        "merchant": "Test",
                        "amount": -100,
                        "transactionDate": "2026-02-11",
                        "currency": "IDR",
                        "confidence": 0.92,
                        "source": "test"
                    }
                    """;

            mockMvc.perform(post("/api/drafts/from-receipt")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidRequest))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").exists());
        }

        @Test
        @DisplayName("Should reject future transaction date")
        void shouldRejectFutureDate() throws Exception {
            String requestJson = String.format("""
                    {
                        "merchant": "Test Merchant",
                        "amount": 100000,
                        "transactionDate": "%s",
                        "currency": "IDR",
                        "confidence": 0.90,
                        "source": "test"
                    }
                    """, LocalDate.now().plusDays(10));

            mockMvc.perform(post("/api/drafts/from-receipt")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").exists())
                    .andExpect(jsonPath("$.message").exists());
        }
    }

    @Nested
    @DisplayName("POST /api/drafts/from-text")
    class CreateFromTextTests {

        @Test
        @DisplayName("Should create draft from valid text")
        void shouldCreateDraftFromValidText() throws Exception {
            String requestJson = String.format("""
                    {
                        "merchant": "PLN",
                        "amount": 350000,
                        "transactionDate": "%s",
                        "currency": "IDR",
                        "category": "Utilities",
                        "description": "Bayar listrik bulan Januari 2026",
                        "confidence": 0.88,
                        "source": "claude-code"
                    }
                    """, LocalDate.now());

            mockMvc.perform(post("/api/drafts/from-text")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.draftId").exists())
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.merchant").value("PLN"))
                    .andExpect(jsonPath("$.amount").value(350000));
        }
    }

    @Nested
    @DisplayName("GET /api/drafts/{id}")
    class GetDraftTests {

        @Test
        @DisplayName("Should get draft by ID")
        void shouldGetDraftById() throws Exception {
            // Create draft first
            String requestJson = String.format("""
                    {
                        "merchant": "Test Merchant",
                        "amount": 100000,
                        "transactionDate": "%s",
                        "currency": "IDR",
                        "category": "Test",
                        "description": "Test transaction",
                        "confidence": 0.95,
                        "source": "test"
                    }
                    """, LocalDate.now());

            MvcResult createResult = mockMvc.perform(post("/api/drafts/from-text")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isCreated())
                    .andReturn();

            String draftId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                    .get("draftId").asText();

            // Get draft
            mockMvc.perform(get("/api/drafts/" + draftId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.draftId").value(draftId))
                    .andExpect(jsonPath("$.merchant").value("Test Merchant"));
        }

        @Test
        @DisplayName("Should return 400 for non-existent draft")
        void shouldReturn400ForNonExistentDraft() throws Exception {
            UUID randomId = UUID.randomUUID();

            mockMvc.perform(get("/api/drafts/" + randomId))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").exists());
        }
    }

    @Nested
    @DisplayName("POST /api/drafts/{id}/approve")
    class ApproveDraftTests {

        @Test
        @DisplayName("Should approve draft successfully")
        void shouldApproveDraft() throws Exception {
            // Create draft
            String requestJson = String.format("""
                    {
                        "merchant": "Test Merchant",
                        "amount": 100000,
                        "transactionDate": "%s",
                        "currency": "IDR",
                        "category": "Test",
                        "description": "Test transaction",
                        "confidence": 0.95,
                        "source": "test"
                    }
                    """, LocalDate.now());

            MvcResult createResult = mockMvc.perform(post("/api/drafts/from-text")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isCreated())
                    .andReturn();

            String draftId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                    .get("draftId").asText();

            // Find template
            List<JournalTemplate> templates = journalTemplateRepository
                    .findByActiveAndIsCurrentVersionTrueOrderByTemplateNameAsc(true);
            assertThat(templates).isNotEmpty();

            String approveRequest = String.format("""
                    {
                        "templateId": "%s",
                        "description": "Test approval",
                        "amount": 100000
                    }
                    """, templates.getFirst().getId());

            // Approve
            mockMvc.perform(post("/api/drafts/" + draftId + "/approve")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(approveRequest))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("APPROVED"));
        }
    }

    @Nested
    @DisplayName("POST /api/drafts/{id}/reject")
    class RejectDraftTests {

        @Test
        @DisplayName("Should reject draft successfully")
        void shouldRejectDraft() throws Exception {
            // Create draft
            String requestJson = String.format("""
                    {
                        "merchant": "Test Merchant",
                        "amount": 100000,
                        "transactionDate": "%s",
                        "currency": "IDR",
                        "category": "Test",
                        "description": "Test transaction",
                        "confidence": 0.95,
                        "source": "test"
                    }
                    """, LocalDate.now());

            MvcResult createResult = mockMvc.perform(post("/api/drafts/from-text")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isCreated())
                    .andReturn();

            String draftId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                    .get("draftId").asText();

            String rejectRequest = """
                    {
                        "reason": "Invalid merchant"
                    }
                    """;

            // Reject
            mockMvc.perform(post("/api/drafts/" + draftId + "/reject")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(rejectRequest))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("REJECTED"));
        }
    }

    @Nested
    @DisplayName("GET /api/drafts/templates")
    class ListTemplatesTests {

        @Test
        @DisplayName("Should list all templates")
        void shouldListTemplates() throws Exception {
            mockMvc.perform(get("/api/drafts/templates"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].id").exists())
                    .andExpect(jsonPath("$[0].name").exists())
                    .andExpect(jsonPath("$[0].category").exists());
        }
    }

    @Nested
    @DisplayName("GET /api/drafts/accounts")
    class ListAccountsTests {

        @Test
        @DisplayName("Should list all active accounts")
        void shouldListAccounts() throws Exception {
            mockMvc.perform(get("/api/drafts/accounts"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].id").exists())
                    .andExpect(jsonPath("$[0].code").exists())
                    .andExpect(jsonPath("$[0].name").exists())
                    .andExpect(jsonPath("$[0].type").exists());
        }
    }
}
