package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.dto.ApproveDraftRequest;
import com.artivisi.accountingfinance.dto.CreateFromReceiptRequest;
import com.artivisi.accountingfinance.dto.CreateFromTextRequest;
import com.artivisi.accountingfinance.dto.DraftResponse;
import com.artivisi.accountingfinance.entity.DraftTransaction;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.repository.DraftTransactionRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for TransactionApiService.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("TransactionApiService Integration Tests")
class TransactionApiServiceTest {

    @Autowired
    private TransactionApiService transactionApiService;

    @Autowired
    private DraftTransactionRepository draftTransactionRepository;

    @Autowired
    private JournalTemplateRepository journalTemplateRepository;

    @Nested
    @DisplayName("Create from Receipt")
    class CreateFromReceiptTests {

        @Test
        @DisplayName("Should create draft from valid receipt data")
        void shouldCreateDraftFromValidReceipt() {
            CreateFromReceiptRequest request = new CreateFromReceiptRequest(
                    "Starbucks Grand Indonesia",
                    new BigDecimal("75000"),
                    LocalDate.now(),
                    "IDR",
                    List.of("Caffe Latte", "Croissant"),
                    "Food & Beverage",
                    new BigDecimal("0.92"),
                    "claude-code",
                    null,
                    "STARBUCKS\nGrand Indonesia\nTotal: 75000"
            );

            DraftResponse response = transactionApiService.createFromReceipt(request);

            assertThat(response).isNotNull();
            assertThat(response.draftId()).isNotNull();
            assertThat(response.status()).isEqualTo("PENDING");
            assertThat(response.merchant()).isEqualTo("Starbucks Grand Indonesia");
            assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("75000"));
            assertThat(response.confidence()).isEqualByComparingTo(new BigDecimal("0.92"));
            assertThat(response.needsClarification()).isFalse();

            // Verify in database
            DraftTransaction draft = draftTransactionRepository.findById(response.draftId()).orElseThrow();
            assertThat(draft.getSource()).isEqualTo(DraftTransaction.Source.API);
            assertThat(draft.getApiSource()).isEqualTo("claude-code");
            assertThat(draft.getMetadata()).isNotNull();
            assertThat(draft.getMetadata()).containsKey("items");
            assertThat(draft.getMetadata()).containsKey("category");
        }

        @Test
        @DisplayName("Should require clarification for low confidence")
        void shouldRequireClarificationForLowConfidence() {
            CreateFromReceiptRequest request = new CreateFromReceiptRequest(
                    "Unknown Merchant",
                    new BigDecimal("50000"),
                    LocalDate.now(),
                    "IDR",
                    null,
                    null,
                    new BigDecimal("0.70"),
                    "test-client",
                    null,
                    null
            );

            DraftResponse response = transactionApiService.createFromReceipt(request);

            assertThat(response.needsClarification()).isTrue();
            assertThat(response.clarificationQuestion()).isNotNull();
            assertThat(response.clarificationQuestion()).contains("Unknown Merchant");
        }

        @Test
        @DisplayName("Should reject future transaction date")
        void shouldRejectFutureDate() {
            CreateFromReceiptRequest request = new CreateFromReceiptRequest(
                    "Merchant",
                    new BigDecimal("100000"),
                    LocalDate.now().plusDays(1),
                    "IDR",
                    null,
                    null,
                    new BigDecimal("0.90"),
                    "test",
                    null,
                    null
            );

            assertThatThrownBy(() -> transactionApiService.createFromReceipt(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("future");
        }
    }

    @Nested
    @DisplayName("Create from Text")
    class CreateFromTextTests {

        @Test
        @DisplayName("Should create draft from valid text")
        void shouldCreateDraftFromValidText() {
            CreateFromTextRequest request = new CreateFromTextRequest(
                    "PLN",
                    new BigDecimal("350000"),
                    LocalDate.now(),
                    "IDR",
                    "Utilities",
                    "Bayar listrik bulan Januari 2026",
                    new BigDecimal("0.88"),
                    "claude-code"
            );

            DraftResponse response = transactionApiService.createFromText(request);

            assertThat(response).isNotNull();
            assertThat(response.draftId()).isNotNull();
            assertThat(response.status()).isEqualTo("PENDING");
            assertThat(response.merchant()).isEqualTo("PLN");
            assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("350000"));
            assertThat(response.confidence()).isEqualByComparingTo(new BigDecimal("0.88"));

            // Verify in database
            DraftTransaction draft = draftTransactionRepository.findById(response.draftId()).orElseThrow();
            assertThat(draft.getSource()).isEqualTo(DraftTransaction.Source.API);
            assertThat(draft.getApiSource()).isEqualTo("claude-code");
            assertThat(draft.getMetadata()).containsKey("category");
            assertThat(draft.getMetadata()).containsKey("description");
        }

        @Test
        @DisplayName("Should reject future transaction date")
        void shouldRejectFutureDate() {
            CreateFromTextRequest request = new CreateFromTextRequest(
                    "Merchant",
                    new BigDecimal("100000"),
                    LocalDate.now().plusDays(1),
                    "IDR",
                    null,
                    "Test",
                    new BigDecimal("0.90"),
                    "test"
            );

            assertThatThrownBy(() -> transactionApiService.createFromText(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("future");
        }
    }

    @Nested
    @DisplayName("Get Draft")
    class GetDraftTests {

        @Test
        @DisplayName("Should get draft by ID")
        void shouldGetDraftById() {
            // Create draft first
            CreateFromTextRequest request = new CreateFromTextRequest(
                    "Test Merchant",
                    new BigDecimal("100000"),
                    LocalDate.now(),
                    "IDR",
                    "Test",
                    "Test transaction",
                    new BigDecimal("0.95"),
                    "test"
            );
            DraftResponse created = transactionApiService.createFromText(request);

            // Get draft
            DraftResponse retrieved = transactionApiService.getDraft(created.draftId());

            assertThat(retrieved).isNotNull();
            assertThat(retrieved.draftId()).isEqualTo(created.draftId());
            assertThat(retrieved.merchant()).isEqualTo("Test Merchant");
        }

        @Test
        @DisplayName("Should throw exception for non-existent draft")
        void shouldThrowExceptionForNonExistentDraft() {
            assertThatThrownBy(() -> transactionApiService.getDraft(java.util.UUID.randomUUID()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Draft not found");
        }
    }

    @Nested
    @DisplayName("Approve Draft")
    class ApproveDraftTests {

        @Test
        @DisplayName("Should approve draft and create transaction")
        void shouldApproveDraft() {
            // Create draft
            CreateFromTextRequest request = new CreateFromTextRequest(
                    "Test Merchant",
                    new BigDecimal("100000"),
                    LocalDate.now(),
                    "IDR",
                    "Test",
                    "Test transaction",
                    new BigDecimal("0.95"),
                    "test"
            );
            DraftResponse draft = transactionApiService.createFromText(request);

            // Find a template
            List<JournalTemplate> templates = journalTemplateRepository.findByActiveAndIsCurrentVersionTrueOrderByTemplateNameAsc(true);
            assertThat(templates).isNotEmpty();
            JournalTemplate template = templates.getFirst();

            // Approve
            ApproveDraftRequest approveRequest = new ApproveDraftRequest(
                    template.getId(),
                    "Test transaction approval",
                    new BigDecimal("100000")
            );

            DraftResponse approved = transactionApiService.approve(draft.draftId(), approveRequest, "testuser");

            assertThat(approved.status()).isEqualTo("APPROVED");
        }
    }

    @Nested
    @DisplayName("Reject Draft")
    class RejectDraftTests {

        @Test
        @DisplayName("Should reject draft")
        void shouldRejectDraft() {
            // Create draft
            CreateFromTextRequest request = new CreateFromTextRequest(
                    "Test Merchant",
                    new BigDecimal("100000"),
                    LocalDate.now(),
                    "IDR",
                    "Test",
                    "Test transaction",
                    new BigDecimal("0.95"),
                    "test"
            );
            DraftResponse draft = transactionApiService.createFromText(request);

            // Reject
            DraftResponse rejected = transactionApiService.reject(
                    draft.draftId(),
                    "Invalid merchant",
                    "testuser"
            );

            assertThat(rejected.status()).isEqualTo("REJECTED");

            // Verify in database
            DraftTransaction entity = draftTransactionRepository.findById(draft.draftId()).orElseThrow();
            assertThat(entity.getStatus()).isEqualTo(DraftTransaction.Status.REJECTED);
            assertThat(entity.getRejectionReason()).isEqualTo("Invalid merchant");
        }
    }
}
