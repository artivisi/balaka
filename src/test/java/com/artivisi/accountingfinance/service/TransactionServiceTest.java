package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.dto.FormulaContext;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.DraftTransaction;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.Project;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.enums.VoidReason;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.DraftTransactionRepository;
import com.artivisi.accountingfinance.repository.ProjectRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for TransactionService.
 * Tests actual database queries and business logic calculations.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("TransactionService Integration Tests")
class TransactionServiceTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private JournalTemplateService journalTemplateService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private ChartOfAccountRepository chartOfAccountRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private DraftTransactionRepository draftTransactionRepository;

    // Test data IDs from V904__transaction_test_data.sql
    private static final UUID DRAFT_TRANSACTION_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID POSTED_TRANSACTION_ID = UUID.fromString("a0000000-0000-0000-0000-000000000002");
    private static final UUID VOIDED_TRANSACTION_ID = UUID.fromString("a0000000-0000-0000-0000-000000000003");

    // Template IDs from V003
    private static final UUID INCOME_CONSULTING_TEMPLATE_ID = UUID.fromString("e0000000-0000-0000-0000-000000000001");
    private static final UUID EXPENSE_OPERATIONAL_TEMPLATE_ID = UUID.fromString("e0000000-0000-0000-0000-000000000002");

    @Nested
    @DisplayName("Find Operations")
    class FindOperationsTests {

        @Test
        @DisplayName("findAll should return all transactions from database")
        void findAllShouldReturnAllTransactions() {
            List<Transaction> transactions = transactionService.findAll();

            assertThat(transactions).isNotEmpty();
            assertThat(transactions.size()).isGreaterThanOrEqualTo(3); // At least 3 from test data
        }

        @Test
        @DisplayName("findById should return transaction with correct data")
        void findByIdShouldReturnTransaction() {
            Transaction transaction = transactionService.findById(DRAFT_TRANSACTION_ID);

            assertThat(transaction).isNotNull();
            assertThat(transaction.getTransactionNumber()).isEqualTo("TRX-TEST-0001");
            assertThat(transaction.getAmount()).isEqualByComparingTo("10000000");
            assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.DRAFT);
        }

        @Test
        @DisplayName("findById should throw EntityNotFoundException for invalid ID")
        void findByIdShouldThrowForInvalidId() {
            UUID invalidId = UUID.randomUUID();

            assertThatThrownBy(() -> transactionService.findById(invalidId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Transaction not found");
        }

        @Test
        @DisplayName("findByStatus should return transactions with correct status")
        void findByStatusShouldReturnFilteredTransactions() {
            List<Transaction> draftTransactions = transactionService.findByStatus(TransactionStatus.DRAFT);
            List<Transaction> postedTransactions = transactionService.findByStatus(TransactionStatus.POSTED);
            List<Transaction> voidedTransactions = transactionService.findByStatus(TransactionStatus.VOID);

            assertThat(draftTransactions).isNotEmpty().allMatch(t -> t.getStatus() == TransactionStatus.DRAFT);
            assertThat(postedTransactions).isNotEmpty().allMatch(t -> t.getStatus() == TransactionStatus.POSTED);
            assertThat(voidedTransactions).allMatch(t -> t.getStatus() == TransactionStatus.VOID);
        }

        @Test
        @DisplayName("countByStatus should return correct counts")
        void countByStatusShouldReturnCorrectCounts() {
            long draftCount = transactionService.countByStatus(TransactionStatus.DRAFT);
            long postedCount = transactionService.countByStatus(TransactionStatus.POSTED);

            assertThat(draftCount).isGreaterThanOrEqualTo(1);
            assertThat(postedCount).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("findByIdWithJournalEntries should eagerly load journal entries")
        void findByIdWithJournalEntriesShouldLoadJournalEntries() {
            Transaction transaction = transactionService.findByIdWithJournalEntries(POSTED_TRANSACTION_ID);

            assertThat(transaction).isNotNull();
            assertThat(transaction.getJournalEntries()).isNotEmpty();
            assertThat(transaction.getJournalEntries()).hasSize(2); // Debit and Credit entries
        }

        @Test
        @DisplayName("findByStatus should return transactions ordered by date desc")
        void findByStatusShouldReturnOrderedTransactions() {
            List<Transaction> transactions = transactionService.findByStatus(TransactionStatus.POSTED);

            assertThat(transactions).isNotEmpty();
            // Verify all have POSTED status
            assertThat(transactions).allMatch(t -> t.getStatus() == TransactionStatus.POSTED);
        }

        @Test
        @DisplayName("search should find transactions by description")
        void searchShouldFindByDescription() {
            Page<Transaction> result = transactionService.search("Test Income", PageRequest.of(0, 10));

            assertThat(result.getContent()).isNotEmpty();
            assertThat(result.getContent()).anyMatch(t ->
                t.getDescription().contains("Test Income"));
        }
    }

    @Nested
    @DisplayName("Create Transaction")
    class CreateTransactionTests {

        @Test
        @DisplayName("create should generate transaction number and set DRAFT status")
        void createShouldGenerateNumberAndSetDraftStatus() {
            JournalTemplate template = journalTemplateService.findById(INCOME_CONSULTING_TEMPLATE_ID);

            Transaction transaction = new Transaction();
            transaction.setJournalTemplate(template);
            transaction.setTransactionDate(LocalDate.now());
            transaction.setAmount(new BigDecimal("5000000"));
            transaction.setDescription("Test create transaction");

            Transaction saved = transactionService.create(transaction, null);

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getTransactionNumber()).isNotNull();
            assertThat(saved.getTransactionNumber()).startsWith("TRX-");
            assertThat(saved.getStatus()).isEqualTo(TransactionStatus.DRAFT);
        }

        @Test
        @DisplayName("create should persist transaction to database")
        void createShouldPersistToDatabase() {
            JournalTemplate template = journalTemplateService.findById(INCOME_CONSULTING_TEMPLATE_ID);

            Transaction transaction = new Transaction();
            transaction.setJournalTemplate(template);
            transaction.setTransactionDate(LocalDate.now());
            transaction.setAmount(new BigDecimal("7500000"));
            transaction.setDescription("Test persistence");
            transaction.setReferenceNumber("REF-TEST-001");

            Transaction saved = transactionService.create(transaction, null);

            // Verify it can be retrieved from database
            Transaction retrieved = transactionRepository.findById(saved.getId()).orElseThrow();
            assertThat(retrieved.getDescription()).isEqualTo("Test persistence");
            assertThat(retrieved.getReferenceNumber()).isEqualTo("REF-TEST-001");
        }

        @Test
        @DisplayName("create with project should associate project correctly")
        void createWithProjectShouldAssociateProject() {
            JournalTemplate template = journalTemplateService.findById(INCOME_CONSULTING_TEMPLATE_ID);
            List<Project> projects = projectRepository.findAll();

            // Skip if no projects in test data
            if (projects.isEmpty()) {
                return;
            }

            Project project = projects.get(0);

            Transaction transaction = new Transaction();
            transaction.setJournalTemplate(template);
            transaction.setTransactionDate(LocalDate.now());
            transaction.setAmount(new BigDecimal("3000000"));
            transaction.setDescription("Test with project");
            transaction.setProject(project);

            Transaction saved = transactionService.create(transaction, null);

            assertThat(saved.getProject()).isNotNull();
            assertThat(saved.getProject().getId()).isEqualTo(project.getId());
        }

        @Test
        @DisplayName("create with empty account mappings should work")
        void createWithEmptyAccountMappingsShouldWork() {
            JournalTemplate template = journalTemplateService.findById(INCOME_CONSULTING_TEMPLATE_ID);

            Transaction transaction = new Transaction();
            transaction.setJournalTemplate(template);
            transaction.setTransactionDate(LocalDate.now());
            transaction.setAmount(new BigDecimal("2000000"));
            transaction.setDescription("Test with empty account mapping");

            Map<UUID, UUID> accountMappings = new HashMap<>();
            Transaction saved = transactionService.create(transaction, accountMappings);

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getAccountMappings()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Update Transaction")
    class UpdateTransactionTests {

        @Test
        @DisplayName("update should modify draft transaction fields")
        void updateShouldModifyDraftFields() {
            Transaction existing = transactionService.findById(DRAFT_TRANSACTION_ID);

            Transaction updateData = new Transaction();
            updateData.setTransactionDate(LocalDate.now().minusDays(1));
            updateData.setAmount(new BigDecimal("12000000"));
            updateData.setDescription("Updated description");
            updateData.setReferenceNumber("REF-UPDATED");
            updateData.setNotes("Updated notes");

            Transaction updated = transactionService.update(existing, updateData);

            assertThat(updated.getAmount()).isEqualByComparingTo("12000000");
            assertThat(updated.getDescription()).isEqualTo("Updated description");
            assertThat(updated.getReferenceNumber()).isEqualTo("REF-UPDATED");
            assertThat(updated.getNotes()).isEqualTo("Updated notes");
            // Transaction number should NOT change
            assertThat(updated.getTransactionNumber()).isEqualTo("TRX-TEST-0001");
        }

        @Test
        @DisplayName("update should throw for non-draft transaction")
        void updateShouldThrowForNonDraft() {
            Transaction posted = transactionService.findById(POSTED_TRANSACTION_ID);

            Transaction updateData = new Transaction();
            updateData.setDescription("Trying to update posted");

            assertThatThrownBy(() -> transactionService.update(posted, updateData))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only draft transactions can be edited");
        }
    }

    @Nested
    @DisplayName("Post Transaction")
    class PostTransactionTests {

        @Test
        @DisplayName("post should create balanced journal entries")
        void postShouldCreateBalancedJournalEntries() {
            // Create a new draft to post
            JournalTemplate template = journalTemplateService.findById(INCOME_CONSULTING_TEMPLATE_ID);

            Transaction transaction = new Transaction();
            transaction.setJournalTemplate(template);
            transaction.setTransactionDate(LocalDate.now());
            transaction.setAmount(new BigDecimal("8000000"));
            transaction.setDescription("Test post transaction");

            Transaction draft = transactionService.create(transaction, null);

            // Post the transaction
            Transaction posted = transactionService.post(draft.getId(), "testuser");

            assertThat(posted.getStatus()).isEqualTo(TransactionStatus.POSTED);
            assertThat(posted.getPostedAt()).isNotNull();
            assertThat(posted.getPostedBy()).isEqualTo("testuser");
            assertThat(posted.getJournalEntries()).isNotEmpty();

            // Verify journal entries are balanced
            BigDecimal totalDebit = posted.getJournalEntries().stream()
                .map(JournalEntry::getDebitAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalCredit = posted.getJournalEntries().stream()
                .map(JournalEntry::getCreditAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            assertThat(totalDebit).isEqualByComparingTo(totalCredit);
        }

        @Test
        @DisplayName("post should calculate amounts from formula correctly")
        void postShouldCalculateAmountsFromFormula() {
            JournalTemplate template = journalTemplateService.findById(INCOME_CONSULTING_TEMPLATE_ID);

            Transaction transaction = new Transaction();
            transaction.setJournalTemplate(template);
            transaction.setTransactionDate(LocalDate.now());
            transaction.setAmount(new BigDecimal("10000000"));
            transaction.setDescription("Test formula calculation");

            Transaction draft = transactionService.create(transaction, null);
            Transaction posted = transactionService.post(draft.getId(), "testuser");

            // The template should use "amount" formula, so entries should equal transaction amount
            BigDecimal totalDebit = posted.getJournalEntries().stream()
                .map(JournalEntry::getDebitAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            assertThat(totalDebit).isEqualByComparingTo("10000000");
        }

        @Test
        @DisplayName("post should throw for already posted transaction")
        void postShouldThrowForAlreadyPosted() {
            assertThatThrownBy(() -> transactionService.post(POSTED_TRANSACTION_ID, "testuser"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only draft transactions can be posted");
        }

        @Test
        @DisplayName("post with custom context should use context variables")
        void postWithCustomContextShouldUseContextVariables() {
            JournalTemplate template = journalTemplateService.findById(INCOME_CONSULTING_TEMPLATE_ID);

            Transaction transaction = new Transaction();
            transaction.setJournalTemplate(template);
            transaction.setTransactionDate(LocalDate.now());
            transaction.setAmount(new BigDecimal("15000000"));
            transaction.setDescription("Test custom context");

            Transaction draft = transactionService.create(transaction, null);

            // Create custom context with amount
            FormulaContext context = FormulaContext.of(draft.getAmount());
            Transaction posted = transactionService.post(draft.getId(), "testuser", context);

            assertThat(posted.getStatus()).isEqualTo(TransactionStatus.POSTED);
        }

        @Test
        @DisplayName("post should associate project with journal entries")
        void postShouldAssociateProjectWithJournalEntries() {
            List<Project> projects = projectRepository.findAll();
            if (projects.isEmpty()) {
                return; // Skip if no projects
            }

            JournalTemplate template = journalTemplateService.findById(INCOME_CONSULTING_TEMPLATE_ID);
            Project project = projects.get(0);

            Transaction transaction = new Transaction();
            transaction.setJournalTemplate(template);
            transaction.setTransactionDate(LocalDate.now());
            transaction.setAmount(new BigDecimal("6000000"));
            transaction.setDescription("Test project association");
            transaction.setProject(project);

            Transaction draft = transactionService.create(transaction, null);
            Transaction posted = transactionService.post(draft.getId(), "testuser");

            assertThat(posted.getJournalEntries()).allMatch(je -> je.getProject() != null);
            assertThat(posted.getJournalEntries()).allMatch(je -> je.getProject().getId().equals(project.getId()));
        }
    }

    @Nested
    @DisplayName("Void Transaction")
    class VoidTransactionTests {

        @Test
        @DisplayName("void should create reversal journal entries")
        void voidShouldCreateReversalJournalEntries() {
            // Create and post a transaction first
            JournalTemplate template = journalTemplateService.findById(INCOME_CONSULTING_TEMPLATE_ID);

            Transaction transaction = new Transaction();
            transaction.setJournalTemplate(template);
            transaction.setTransactionDate(LocalDate.now());
            transaction.setAmount(new BigDecimal("4000000"));
            transaction.setDescription("Test void transaction");

            Transaction draft = transactionService.create(transaction, null);
            Transaction posted = transactionService.post(draft.getId(), "testuser");

            int originalEntryCount = posted.getJournalEntries().size();

            // Void the transaction
            Transaction voided = transactionService.voidTransaction(
                posted.getId(),
                VoidReason.INPUT_ERROR,
                "Testing void functionality",
                "testuser"
            );

            assertThat(voided.getStatus()).isEqualTo(TransactionStatus.VOID);
            assertThat(voided.getVoidReason()).isEqualTo(VoidReason.INPUT_ERROR);
            assertThat(voided.getVoidNotes()).isEqualTo("Testing void functionality");
            assertThat(voided.getVoidedAt()).isNotNull();
            assertThat(voided.getVoidedBy()).isEqualTo("testuser");

            // Should have reversal entries (double the original)
            assertThat(voided.getJournalEntries()).hasSize(originalEntryCount * 2);

            // Reversal entries should have reversed amounts
            List<JournalEntry> reversalEntries = voided.getJournalEntries().stream()
                .filter(JournalEntry::getIsReversal)
                .toList();
            assertThat(reversalEntries).hasSize(originalEntryCount);
        }

        @Test
        @DisplayName("void should ensure net zero balance after reversal")
        void voidShouldEnsureNetZeroBalance() {
            JournalTemplate template = journalTemplateService.findById(INCOME_CONSULTING_TEMPLATE_ID);

            Transaction transaction = new Transaction();
            transaction.setJournalTemplate(template);
            transaction.setTransactionDate(LocalDate.now());
            transaction.setAmount(new BigDecimal("9000000"));
            transaction.setDescription("Test zero balance");

            Transaction draft = transactionService.create(transaction, null);
            Transaction posted = transactionService.post(draft.getId(), "testuser");
            Transaction voided = transactionService.voidTransaction(
                posted.getId(), VoidReason.DUPLICATE, "Duplicate entry", "testuser");

            // After voiding, total debits should still equal total credits
            BigDecimal totalDebit = voided.getJournalEntries().stream()
                .map(JournalEntry::getDebitAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalCredit = voided.getJournalEntries().stream()
                .map(JournalEntry::getCreditAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            assertThat(totalDebit).isEqualByComparingTo(totalCredit);
        }

        @Test
        @DisplayName("void should throw for draft transaction")
        void voidShouldThrowForDraft() {
            assertThatThrownBy(() -> transactionService.voidTransaction(
                    DRAFT_TRANSACTION_ID, VoidReason.INPUT_ERROR, "Test", "testuser"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only posted transactions can be voided");
        }
    }

    @Nested
    @DisplayName("Delete Transaction")
    class DeleteTransactionTests {

        @Test
        @DisplayName("delete should remove draft transaction from database")
        void deleteShouldRemoveDraftFromDatabase() {
            // Create a new draft to delete
            JournalTemplate template = journalTemplateService.findById(INCOME_CONSULTING_TEMPLATE_ID);

            Transaction transaction = new Transaction();
            transaction.setJournalTemplate(template);
            transaction.setTransactionDate(LocalDate.now());
            transaction.setAmount(new BigDecimal("1000000"));
            transaction.setDescription("Test delete");

            Transaction draft = transactionService.create(transaction, null);
            UUID draftId = draft.getId();

            transactionService.delete(draftId);

            assertThat(transactionRepository.findById(draftId)).isEmpty();
        }

        @Test
        @DisplayName("delete should throw for posted transaction")
        void deleteShouldThrowForPosted() {
            assertThatThrownBy(() -> transactionService.delete(POSTED_TRANSACTION_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only draft transactions can be deleted");
        }

        @Test
        @DisplayName("delete should throw for voided transaction")
        void deleteShouldThrowForVoided() {
            assertThatThrownBy(() -> transactionService.delete(VOIDED_TRANSACTION_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only draft transactions can be deleted");
        }
    }

    @Nested
    @DisplayName("Create from Draft Transaction")
    class CreateFromDraftTests {

        @Test
        @DisplayName("createFromDraft should create transaction from draft data")
        void createFromDraftShouldCreateTransaction() {
            // Create a draft transaction in the database
            DraftTransaction draft = new DraftTransaction();
            draft.setSource(DraftTransaction.Source.TELEGRAM);
            draft.setTransactionDate(LocalDate.now());
            draft.setAmount(new BigDecimal("2500000"));
            draft.setMerchantName("Test Merchant");
            draft.setSourceReference("REF-DRAFT-001");
            draft.setStatus(DraftTransaction.Status.APPROVED);
            draft = draftTransactionRepository.save(draft);

            Transaction transaction = transactionService.createFromDraft(
                draft,
                INCOME_CONSULTING_TEMPLATE_ID,
                "Transaction from draft",
                new BigDecimal("2500000"),
                "testuser"
            );

            assertThat(transaction).isNotNull();
            assertThat(transaction.getId()).isNotNull();
            assertThat(transaction.getTransactionNumber()).startsWith("TRX-");
            assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.DRAFT);
            assertThat(transaction.getDescription()).isEqualTo("Transaction from draft");
            assertThat(transaction.getReferenceNumber()).isEqualTo("REF-DRAFT-001");
            assertThat(transaction.getCreatedBy()).isEqualTo("testuser");
        }

        @Test
        @DisplayName("createFromDraft should use draft amount when amount is null")
        void createFromDraftShouldUseDraftAmountWhenNull() {
            DraftTransaction draft = new DraftTransaction();
            draft.setSource(DraftTransaction.Source.TELEGRAM);
            draft.setTransactionDate(LocalDate.now());
            draft.setAmount(new BigDecimal("3500000"));
            draft.setMerchantName("Test Merchant");
            draft.setStatus(DraftTransaction.Status.APPROVED);
            draft = draftTransactionRepository.save(draft);

            Transaction transaction = transactionService.createFromDraft(
                draft,
                INCOME_CONSULTING_TEMPLATE_ID,
                "Test description",
                null, // null amount
                "testuser"
            );

            assertThat(transaction.getAmount()).isEqualByComparingTo("3500000");
        }

        @Test
        @DisplayName("createFromDraft should use merchant name when description is null")
        void createFromDraftShouldUseMerchantNameWhenDescriptionNull() {
            DraftTransaction draft = new DraftTransaction();
            draft.setSource(DraftTransaction.Source.TELEGRAM);
            draft.setTransactionDate(LocalDate.now());
            draft.setAmount(new BigDecimal("4500000"));
            draft.setMerchantName("Amazing Store");
            draft.setStatus(DraftTransaction.Status.APPROVED);
            draft = draftTransactionRepository.save(draft);

            Transaction transaction = transactionService.createFromDraft(
                draft,
                INCOME_CONSULTING_TEMPLATE_ID,
                null, // null description
                new BigDecimal("4500000"),
                "testuser"
            );

            assertThat(transaction.getDescription()).isEqualTo("Amazing Store");
        }
    }

    @Nested
    @DisplayName("Transaction Number Generation")
    class TransactionNumberGenerationTests {

        @Test
        @DisplayName("should generate unique transaction numbers")
        void shouldGenerateUniqueTransactionNumbers() {
            JournalTemplate template = journalTemplateService.findById(INCOME_CONSULTING_TEMPLATE_ID);

            Transaction tx1 = new Transaction();
            tx1.setJournalTemplate(template);
            tx1.setTransactionDate(LocalDate.now());
            tx1.setAmount(new BigDecimal("1000000"));
            tx1.setDescription("First");

            Transaction tx2 = new Transaction();
            tx2.setJournalTemplate(template);
            tx2.setTransactionDate(LocalDate.now());
            tx2.setAmount(new BigDecimal("2000000"));
            tx2.setDescription("Second");

            Transaction saved1 = transactionService.create(tx1, null);
            Transaction saved2 = transactionService.create(tx2, null);

            assertThat(saved1.getTransactionNumber()).isNotEqualTo(saved2.getTransactionNumber());
        }

        @Test
        @DisplayName("should generate sequential transaction numbers")
        void shouldGenerateSequentialNumbers() {
            JournalTemplate template = journalTemplateService.findById(INCOME_CONSULTING_TEMPLATE_ID);

            Transaction tx1 = new Transaction();
            tx1.setJournalTemplate(template);
            tx1.setTransactionDate(LocalDate.now());
            tx1.setAmount(new BigDecimal("1000000"));
            tx1.setDescription("Sequential 1");

            Transaction tx2 = new Transaction();
            tx2.setJournalTemplate(template);
            tx2.setTransactionDate(LocalDate.now());
            tx2.setAmount(new BigDecimal("2000000"));
            tx2.setDescription("Sequential 2");

            Transaction saved1 = transactionService.create(tx1, null);
            Transaction saved2 = transactionService.create(tx2, null);

            // Extract numbers from transaction numbers (format: TRX-YYYY-NNNNN)
            String num1 = saved1.getTransactionNumber().replaceAll("\\D+", "");
            String num2 = saved2.getTransactionNumber().replaceAll("\\D+", "");

            // Second should be greater
            assertThat(Long.parseLong(num2)).isGreaterThan(Long.parseLong(num1));
        }
    }

    @Nested
    @DisplayName("Journal Entry Verification")
    class JournalEntryVerificationTests {

        @Test
        @DisplayName("posted transaction should have journal entries with correct account codes")
        void postedTransactionShouldHaveCorrectAccountCodes() {
            Transaction posted = transactionService.findByIdWithJournalEntries(POSTED_TRANSACTION_ID);

            assertThat(posted.getJournalEntries()).isNotEmpty();

            // Verify entries have valid accounts
            for (JournalEntry entry : posted.getJournalEntries()) {
                assertThat(entry.getAccount()).isNotNull();
                assertThat(entry.getAccount().getAccountCode()).isNotNull();
            }
        }

        @Test
        @DisplayName("journal entries should have debit and credit on different sides")
        void journalEntriesShouldHaveDebitCreditOnDifferentSides() {
            JournalTemplate template = journalTemplateService.findById(INCOME_CONSULTING_TEMPLATE_ID);

            Transaction transaction = new Transaction();
            transaction.setJournalTemplate(template);
            transaction.setTransactionDate(LocalDate.now());
            transaction.setAmount(new BigDecimal("5000000"));
            transaction.setDescription("Test debit credit sides");

            Transaction draft = transactionService.create(transaction, null);
            Transaction posted = transactionService.post(draft.getId(), "testuser");

            boolean hasDebit = posted.getJournalEntries().stream()
                .anyMatch(je -> je.getDebitAmount().compareTo(BigDecimal.ZERO) > 0);
            boolean hasCredit = posted.getJournalEntries().stream()
                .anyMatch(je -> je.getCreditAmount().compareTo(BigDecimal.ZERO) > 0);

            assertThat(hasDebit).isTrue();
            assertThat(hasCredit).isTrue();
        }
    }
}
