package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.entity.DraftTransaction;
import com.artivisi.accountingfinance.repository.DraftTransactionRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for DraftTransactionService.
 * Tests CRUD operations, filtering, and workflow (approve/reject).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("DraftTransaction Service Integration Tests")
class DraftTransactionServiceIntegrationTest {

    @Autowired
    private DraftTransactionService draftTransactionService;

    @Autowired
    private DraftTransactionRepository draftTransactionRepository;

    @Autowired
    private JournalTemplateRepository journalTemplateRepository;

    private DraftTransaction testDraft;

    @BeforeEach
    void setup() {
        // Create a test draft transaction
        testDraft = new DraftTransaction();
        testDraft.setSource(DraftTransaction.Source.MANUAL);
        testDraft.setMerchantName("Test Merchant");
        testDraft.setAmount(new BigDecimal("100000"));
        testDraft.setTransactionDate(LocalDate.now());
        testDraft.setCurrency("IDR");
        testDraft.setStatus(DraftTransaction.Status.PENDING);
        testDraft.setCreatedBy("testuser");
        testDraft.setOverallConfidence(BigDecimal.ONE);
        testDraft = draftTransactionRepository.save(testDraft);
    }

    // ==================== Find Operations ====================

    @Test
    @DisplayName("Should find draft by ID")
    void shouldFindDraftById() {
        DraftTransaction found = draftTransactionService.findById(testDraft.getId());

        assertThat(found).isNotNull();
        assertThat(found.getMerchantName()).isEqualTo("Test Merchant");
    }

    @Test
    @DisplayName("Should throw exception for non-existent draft")
    void shouldThrowExceptionForNonExistentDraft() {
        var nonExistentId = java.util.UUID.randomUUID();

        assertThatThrownBy(() -> draftTransactionService.findById(nonExistentId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Draft not found");
    }

    @Test
    @DisplayName("Should find pending drafts")
    void shouldFindPendingDrafts() {
        Page<DraftTransaction> pending = draftTransactionService.findPending(PageRequest.of(0, 10));

        assertThat(pending).isNotNull();
        assertThat(pending.getContent()).isNotEmpty();
        assertThat(pending.getContent()).allMatch(d -> d.getStatus() == DraftTransaction.Status.PENDING);
    }

    @Test
    @DisplayName("Should find drafts by status filter")
    void shouldFindDraftsByStatusFilter() {
        Page<DraftTransaction> drafts = draftTransactionService.findByFilters(
                DraftTransaction.Status.PENDING, PageRequest.of(0, 10));

        assertThat(drafts).isNotNull();
        assertThat(drafts.getContent()).allMatch(d -> d.getStatus() == DraftTransaction.Status.PENDING);
    }

    @Test
    @DisplayName("Should find all drafts when status filter is null")
    void shouldFindAllDraftsWhenStatusFilterIsNull() {
        Page<DraftTransaction> drafts = draftTransactionService.findByFilters(null, PageRequest.of(0, 10));

        assertThat(drafts).isNotNull();
    }

    @Test
    @DisplayName("Should find drafts by user")
    void shouldFindDraftsByUser() {
        Page<DraftTransaction> drafts = draftTransactionService.findByUser("testuser", PageRequest.of(0, 10));

        assertThat(drafts).isNotNull();
        assertThat(drafts.getContent()).allMatch(d -> "testuser".equals(d.getCreatedBy()));
    }

    @Test
    @DisplayName("Should find pending drafts by user")
    void shouldFindPendingDraftsByUser() {
        Page<DraftTransaction> drafts = draftTransactionService.findPendingByUser("testuser", PageRequest.of(0, 10));

        assertThat(drafts).isNotNull();
        assertThat(drafts.getContent()).allMatch(d ->
                d.getStatus() == DraftTransaction.Status.PENDING && "testuser".equals(d.getCreatedBy()));
    }

    // ==================== Count Operations ====================

    @Test
    @DisplayName("Should count pending drafts")
    void shouldCountPendingDrafts() {
        long count = draftTransactionService.countPending();

        assertThat(count).isPositive();
    }

    @Test
    @DisplayName("Should count pending drafts by user")
    void shouldCountPendingDraftsByUser() {
        long count = draftTransactionService.countPendingByUser("testuser");

        assertThat(count).isPositive();
    }

    // ==================== Delete Operations ====================

    @Test
    @DisplayName("Should delete pending draft")
    void shouldDeletePendingDraft() {
        var draftId = testDraft.getId();

        draftTransactionService.delete(draftId);

        assertThatThrownBy(() -> draftTransactionService.findById(draftId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should not delete non-pending draft")
    void shouldNotDeleteNonPendingDraft() {
        // Change status to approved
        testDraft.setStatus(DraftTransaction.Status.APPROVED);
        draftTransactionRepository.save(testDraft);

        assertThatThrownBy(() -> draftTransactionService.delete(testDraft.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only pending drafts can be deleted");
    }

    // ==================== Reject Operations ====================

    @Test
    @DisplayName("Should reject pending draft")
    void shouldRejectPendingDraft() {
        DraftTransaction rejected = draftTransactionService.reject(
                testDraft.getId(), "Test rejection reason", "admin");

        assertThat(rejected.getStatus()).isEqualTo(DraftTransaction.Status.REJECTED);
        assertThat(rejected.getRejectionReason()).isEqualTo("Test rejection reason");
    }

    @Test
    @DisplayName("Should not reject non-pending draft")
    void shouldNotRejectNonPendingDraft() {
        testDraft.setStatus(DraftTransaction.Status.APPROVED);
        draftTransactionRepository.save(testDraft);

        assertThatThrownBy(() -> draftTransactionService.reject(testDraft.getId(), "reason", "admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not pending");
    }

    // ==================== Save Operations ====================

    @Test
    @DisplayName("Should save draft transaction")
    void shouldSaveDraftTransaction() {
        DraftTransaction newDraft = new DraftTransaction();
        newDraft.setSource(DraftTransaction.Source.MANUAL);
        newDraft.setMerchantName("New Merchant");
        newDraft.setAmount(new BigDecimal("250000"));
        newDraft.setTransactionDate(LocalDate.now());
        newDraft.setCurrency("IDR");
        newDraft.setStatus(DraftTransaction.Status.PENDING);
        newDraft.setCreatedBy("anotheruser");
        newDraft.setOverallConfidence(BigDecimal.ONE);

        DraftTransaction saved = draftTransactionService.save(newDraft);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getMerchantName()).isEqualTo("New Merchant");
    }

    // ==================== Multiple Draft Scenarios ====================

    @Test
    @DisplayName("Should handle multiple drafts with different statuses")
    void shouldHandleMultipleDraftsWithDifferentStatuses() {
        // Create approved draft
        DraftTransaction approvedDraft = new DraftTransaction();
        approvedDraft.setSource(DraftTransaction.Source.MANUAL);
        approvedDraft.setMerchantName("Approved Merchant");
        approvedDraft.setAmount(new BigDecimal("500000"));
        approvedDraft.setTransactionDate(LocalDate.now());
        approvedDraft.setCurrency("IDR");
        approvedDraft.setStatus(DraftTransaction.Status.APPROVED);
        approvedDraft.setCreatedBy("testuser");
        approvedDraft.setOverallConfidence(BigDecimal.ONE);
        draftTransactionRepository.save(approvedDraft);

        // Create rejected draft
        DraftTransaction rejectedDraft = new DraftTransaction();
        rejectedDraft.setSource(DraftTransaction.Source.MANUAL);
        rejectedDraft.setMerchantName("Rejected Merchant");
        rejectedDraft.setAmount(new BigDecimal("750000"));
        rejectedDraft.setTransactionDate(LocalDate.now());
        rejectedDraft.setCurrency("IDR");
        rejectedDraft.setStatus(DraftTransaction.Status.REJECTED);
        rejectedDraft.setCreatedBy("testuser");
        rejectedDraft.setOverallConfidence(BigDecimal.ONE);
        draftTransactionRepository.save(rejectedDraft);

        // Verify filtering
        Page<DraftTransaction> pendingDrafts = draftTransactionService.findByFilters(
                DraftTransaction.Status.PENDING, PageRequest.of(0, 10));
        Page<DraftTransaction> approvedDrafts = draftTransactionService.findByFilters(
                DraftTransaction.Status.APPROVED, PageRequest.of(0, 10));
        Page<DraftTransaction> rejectedDrafts = draftTransactionService.findByFilters(
                DraftTransaction.Status.REJECTED, PageRequest.of(0, 10));

        assertThat(pendingDrafts.getContent()).allMatch(d -> d.getStatus() == DraftTransaction.Status.PENDING);
        assertThat(approvedDrafts.getContent()).allMatch(d -> d.getStatus() == DraftTransaction.Status.APPROVED);
        assertThat(rejectedDrafts.getContent()).allMatch(d -> d.getStatus() == DraftTransaction.Status.REJECTED);
    }

    @Test
    @DisplayName("Should handle draft with suggested template")
    void shouldHandleDraftWithSuggestedTemplate() {
        UUID templateId = UUID.fromString("e0000000-0000-0000-0000-000000000001");
        var template = journalTemplateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalStateException("Seeded template missing: " + templateId));

        testDraft.setSuggestedTemplate(template);
        DraftTransaction saved = draftTransactionService.save(testDraft);

        DraftTransaction stored = draftTransactionRepository.findById(saved.getId()).orElseThrow();
        assertThat(stored.getSuggestedTemplate().getId()).isEqualTo(templateId);
        assertThat(stored.getSuggestedTemplate().getTemplateName()).isEqualTo("Pendapatan Jasa Konsultasi");
    }
}
