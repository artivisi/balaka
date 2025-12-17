package com.artivisi.accountingfinance.security;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.service.TransactionService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@DisplayName("Optimistic Locking Tests")
public class OptimisticLockingTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private JournalTemplateRepository templateRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionService transactionService;

    @Test
    @DisplayName("Should prevent double-submission of transaction creation")
    @Transactional
    void shouldPreventDoubleSubmissionOnCreate() {
        // Given: A valid transaction
        var template = templateRepository.findByTemplateName("Pendapatan Jasa Konsultasi")
                .orElseThrow(() -> new IllegalStateException("Test template not found"));

        Transaction tx1 = new Transaction();
        tx1.setJournalTemplate(template);
        tx1.setTransactionDate(LocalDate.now());
        tx1.setAmount(BigDecimal.valueOf(1000000));
        tx1.setDescription("Test Transaction");
        tx1.setStatus(TransactionStatus.DRAFT);

        // When: Transaction is saved (first submission)
        Transaction saved = transactionRepository.saveAndFlush(tx1);
        Long originalVersion = saved.getRowVersion();

        assertThat(originalVersion).isEqualTo(0L); // First save should be version 0

        // And when: We try to update it
        saved.setAmount(BigDecimal.valueOf(2000000));
        Transaction updated = transactionRepository.saveAndFlush(saved);

        assertThat(updated.getRowVersion()).isEqualTo(1L); // Version should increment

        // And when: We try to use the old version (simulating double-click/concurrent update)
        Transaction staleVersion = transactionRepository.findById(saved.getId()).orElseThrow();
        // Manually set to old version to simulate stale data
        entityManager.detach(staleVersion);
        staleVersion.setRowVersion(0L); // Pretend we have old version
        staleVersion.setAmount(BigDecimal.valueOf(3000000));

        // Then: Should throw OptimisticLockingFailureException
        assertThrows(OptimisticLockingFailureException.class, () -> {
            transactionRepository.save(staleVersion);
            entityManager.flush(); // Force the update to database
        });
    }

    @Test
    @DisplayName("Should prevent concurrent updates from different sessions")
    void shouldPreventConcurrentUpdates() {
        // Given: Create a test transaction
        var template = templateRepository.findByTemplateName("Pendapatan Jasa Konsultasi").orElseThrow();

        Transaction newTx = new Transaction();
        newTx.setJournalTemplate(template);
        newTx.setTransactionDate(LocalDate.now());
        newTx.setAmount(BigDecimal.valueOf(8000000));
        newTx.setDescription("Concurrent Test Transaction");
        newTx.setStatus(TransactionStatus.DRAFT);

        Transaction savedTx = transactionRepository.saveAndFlush(newTx);
        UUID transactionId = savedTx.getId();

        // Clear persistence context to simulate loading from different sessions
        entityManager.clear();

        // When: Two "users" load the same transaction (simulating concurrent access)
        Transaction user1Transaction = transactionRepository.findById(transactionId).orElseThrow();
        Transaction user2Transaction = transactionRepository.findById(transactionId).orElseThrow();

        assertThat(user1Transaction.getRowVersion())
                .isEqualTo(user2Transaction.getRowVersion())
                .isEqualTo(0L); // Both start with version 0

        // And when: User 1 updates and saves first
        user1Transaction.setAmount(BigDecimal.valueOf(9000000));
        Transaction user1Saved = transactionRepository.saveAndFlush(user1Transaction);

        assertThat(user1Saved.getRowVersion()).isEqualTo(1L); // Version incremented

        // And when: User 2 tries to save their changes (with stale version 0)
        user2Transaction.setAmount(BigDecimal.valueOf(9500000));

        // Then: Should throw OptimisticLockingFailureException
        assertThrows(OptimisticLockingFailureException.class, () -> {
            transactionRepository.saveAndFlush(user2Transaction);
        });

        // And: The database should still have User 1's update
        Transaction finalState = transactionRepository.findById(transactionId).orElseThrow();
        assertThat(finalState.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(9000000));
        assertThat(finalState.getRowVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Should increment version on successful update")
    @Transactional
    void shouldIncrementVersionOnUpdate() {
        // Given: A new transaction
        var template = templateRepository.findByTemplateName("Pendapatan Jasa Konsultasi").orElseThrow();

        Transaction tx = new Transaction();
        tx.setJournalTemplate(template);
        tx.setTransactionDate(LocalDate.now());
        tx.setAmount(BigDecimal.valueOf(1000000));
        tx.setDescription("Version Test");
        tx.setStatus(TransactionStatus.DRAFT);

        Transaction saved = transactionRepository.saveAndFlush(tx);
        assertThat(saved.getRowVersion()).isEqualTo(0L);

        // When: Multiple sequential updates
        for (int i = 1; i <= 5; i++) {
            Transaction current = transactionRepository.findById(saved.getId()).orElseThrow();
            current.setAmount(BigDecimal.valueOf(1000000 + (i * 100000)));
            current = transactionRepository.saveAndFlush(current);

            // Then: Version should increment each time
            assertThat(current.getRowVersion()).isEqualTo((long) i);
        }
    }
}
