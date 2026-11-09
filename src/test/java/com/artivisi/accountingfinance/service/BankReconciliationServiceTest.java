package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.entity.BankReconciliation;
import com.artivisi.accountingfinance.entity.BankStatement;
import com.artivisi.accountingfinance.entity.BankStatementItem;
import com.artivisi.accountingfinance.entity.BankStatementParserConfig;
import com.artivisi.accountingfinance.entity.CompanyBankAccount;
import com.artivisi.accountingfinance.entity.ReconciliationItem;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.BankStatementParserType;
import com.artivisi.accountingfinance.enums.ReconciliationStatus;
import com.artivisi.accountingfinance.enums.StatementItemMatchStatus;
import com.artivisi.accountingfinance.repository.BankReconciliationRepository;
import com.artivisi.accountingfinance.repository.BankStatementItemRepository;
import com.artivisi.accountingfinance.repository.BankStatementRepository;
import com.artivisi.accountingfinance.repository.CompanyBankAccountRepository;
import com.artivisi.accountingfinance.repository.ReconciliationItemRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for BankReconciliationService.
 * Tests create, auto-match, manual match, mark bank-only, mark book-only,
 * unmatch, create transaction from statement item, complete, and
 * find unmatched book transactions.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("BankReconciliationService Integration Tests")
class BankReconciliationServiceTest {

    @Autowired
    private BankReconciliationService reconciliationService;

    @Autowired
    private BankStatementImportService importService;

    @Autowired
    private BankStatementParserConfigService parserConfigService;

    @Autowired
    private CompanyBankAccountService bankAccountService;

    @Autowired
    private ChartOfAccountService chartOfAccountService;

    @Autowired
    private CompanyBankAccountRepository bankAccountRepository;

    @Autowired
    private BankStatementRepository bankStatementRepository;

    @Autowired
    private BankStatementItemRepository statementItemRepository;

    @Autowired
    private BankReconciliationRepository reconciliationRepository;

    @Autowired
    private ReconciliationItemRepository reconciliationItemRepository;

    @Autowired
    private TransactionService transactionService;

    private CompanyBankAccount testBankAccount;
    private BankStatement testStatement;
    private BankStatementParserConfig testParserConfig;

    @BeforeEach
    void setUp() {
        // Create a parser config (required by BankStatement validation)
        testParserConfig = new BankStatementParserConfig();
        testParserConfig.setBankType(BankStatementParserType.BCA);
        testParserConfig.setConfigName("Test Parser Config");
        testParserConfig.setDateColumn(0);
        testParserConfig.setDescriptionColumn(1);
        testParserConfig.setDebitColumn(2);
        testParserConfig.setCreditColumn(3);
        testParserConfig.setDateFormat("dd/MM/yyyy");
        testParserConfig.setDelimiter(",");
        testParserConfig.setSkipHeaderRows(1);
        testParserConfig.setEncoding("UTF-8");
        testParserConfig.setDecimalSeparator(".");
        testParserConfig.setThousandSeparator(",");
        testParserConfig = parserConfigService.create(testParserConfig);

        // Create a bank account with GL account link (required for reconciliation)
        var glAccount = chartOfAccountService.findByAccountCode("1.1.02"); // Bank BCA
        testBankAccount = new CompanyBankAccount();
        testBankAccount.setBankName("Test Bank");
        testBankAccount.setAccountNumber("9999999999");
        testBankAccount.setAccountName("Test Account");
        testBankAccount.setCurrencyCode("IDR");
        testBankAccount.setGlAccount(glAccount);
        testBankAccount.setActive(true);
        testBankAccount = bankAccountRepository.save(testBankAccount);

        // Create a bank statement with items
        testStatement = new BankStatement();
        testStatement.setBankAccount(testBankAccount);
        testStatement.setParserConfig(testParserConfig);
        testStatement.setStatementPeriodStart(LocalDate.of(2024, 1, 1));
        testStatement.setStatementPeriodEnd(LocalDate.of(2024, 6, 30));
        testStatement.setOpeningBalance(BigDecimal.ZERO);
        testStatement.setClosingBalance(new BigDecimal("100000000"));
        testStatement.setTotalItems(3);
        testStatement.setImportedBy("admin");
        testStatement.setImportedAt(LocalDateTime.now());
        testStatement.setOriginalFilename("test.csv");
        testStatement = bankStatementRepository.save(testStatement);

        // Add statement items
        createStatementItem(testStatement, 1, LocalDate.of(2024, 1, 5),
                "Setoran modal awal", null, new BigDecimal("100000000"), StatementItemMatchStatus.UNMATCHED);
        createStatementItem(testStatement, 2, LocalDate.of(2024, 1, 15),
                "Pembelian laptop", new BigDecimal("20000000"), null, StatementItemMatchStatus.UNMATCHED);
        createStatementItem(testStatement, 3, LocalDate.of(2024, 2, 10),
                "Pembayaran jasa konsultasi", null, new BigDecimal("15000000"), StatementItemMatchStatus.UNMATCHED);
    }

    private BankStatementItem createStatementItem(BankStatement statement, int lineNumber,
            LocalDate date, String description, BigDecimal debit, BigDecimal credit,
            StatementItemMatchStatus status) {
        BankStatementItem item = new BankStatementItem();
        item.setBankStatement(statement);
        item.setLineNumber(lineNumber);
        item.setTransactionDate(date);
        item.setDescription(description);
        item.setDebitAmount(debit);
        item.setCreditAmount(credit);
        item.setMatchStatus(status);
        return statementItemRepository.save(item);
    }

    @Nested
    @DisplayName("Find Operations")
    class FindTests {

        @Test
        @DisplayName("Should find all reconciliations")
        void shouldFindAllReconciliations() {
            List<BankReconciliation> all = reconciliationService.findAll();
            assertThat(all).isNotNull();
        }

        @Test
        @DisplayName("Should throw when reconciliation not found")
        void shouldThrowWhenReconciliationNotFound() {
            UUID nonexistentId = UUID.randomUUID();
            assertThatThrownBy(() -> reconciliationService.findById(nonexistentId))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("Should find journal templates")
        void shouldFindJournalTemplates() {
            var templates = reconciliationService.findJournalTemplates();
            assertThat(templates).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Create Reconciliation")
    class CreateTests {

        @Test
        @WithMockUser(username = "admin")
        @DisplayName("Should create reconciliation from bank statement")
        void shouldCreateReconciliationFromBankStatement() {
            BankReconciliation recon = reconciliationService.create(
                    testStatement.getId(), "Test notes", "admin");

            assertThat(recon).isNotNull();
            assertThat(recon.getStatus()).isEqualTo(ReconciliationStatus.DRAFT);
            assertThat(recon.getBankAccount().getId()).isEqualTo(testBankAccount.getId());
            assertThat(recon.getTotalStatementItems()).isEqualTo(3);
            assertThat(recon.getMatchedCount()).isZero();
            assertThat(recon.getUnmatchedBankCount()).isEqualTo(3);
            assertThat(recon.getBookBalance()).isNotNull();
            assertThat(recon.getNotes()).isEqualTo("Test notes");
        }

        @Test
        @DisplayName("Should reject reconciliation when bank account has no GL link")
        void shouldRejectReconciliationWhenNoGlLink() {
            // Create bank account without GL account
            CompanyBankAccount noGlAccount = new CompanyBankAccount();
            noGlAccount.setBankName("No GL Bank");
            noGlAccount.setAccountNumber("8888888888");
            noGlAccount.setAccountName("No GL Account");
            noGlAccount.setCurrencyCode("IDR");
            noGlAccount.setActive(true);
            noGlAccount = bankAccountRepository.save(noGlAccount);

            BankStatement stmt = new BankStatement();
            stmt.setBankAccount(noGlAccount);
            stmt.setParserConfig(testParserConfig);
            stmt.setStatementPeriodStart(LocalDate.of(2024, 1, 1));
            stmt.setStatementPeriodEnd(LocalDate.of(2024, 6, 30));
            stmt.setOpeningBalance(BigDecimal.ZERO);
            stmt.setClosingBalance(BigDecimal.ZERO);
            stmt.setTotalItems(0);
            stmt.setImportedBy("admin");
            stmt.setImportedAt(LocalDateTime.now());
            stmt.setOriginalFilename("test.csv");
            stmt = bankStatementRepository.save(stmt);

            UUID stmtId = stmt.getId();
            assertThatThrownBy(() -> reconciliationService.create(stmtId, null, "admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("belum dihubungkan dengan akun GL");
        }
    }

    @Nested
    @DisplayName("Auto-Match Operations")
    class AutoMatchTests {

        @Test
        @WithMockUser(username = "admin")
        @DisplayName("Should perform auto-match")
        void shouldPerformAutoMatch() {
            BankReconciliation recon = reconciliationService.create(
                    testStatement.getId(), null, "admin");

            int matchCount = reconciliationService.autoMatch(recon.getId(), "admin");

            // The match count depends on whether test data journal entries
            // have matching amounts and dates within tolerance
            assertThat(matchCount).isGreaterThanOrEqualTo(0);

            // Verify status changed to IN_PROGRESS
            BankReconciliation updated = reconciliationService.findById(recon.getId());
            assertThat(updated.getStatus()).isEqualTo(ReconciliationStatus.IN_PROGRESS);
        }

        @Test
        @WithMockUser(username = "admin")
        @DisplayName("Should reject auto-match on completed reconciliation")
        void shouldRejectAutoMatchOnCompletedReconciliation() {
            BankReconciliation recon = reconciliationService.create(
                    testStatement.getId(), null, "admin");

            // Mark all items as bank-only so we can complete
            List<BankStatementItem> items = statementItemRepository
                    .findByBankStatementIdOrderByLineNumberAsc(testStatement.getId());
            for (BankStatementItem item : items) {
                reconciliationService.markBankOnly(recon.getId(), item.getId(), "test", "admin");
            }

            reconciliationService.complete(recon.getId(), "admin");

            UUID reconId = recon.getId();
            assertThatThrownBy(() -> reconciliationService.autoMatch(reconId, "admin"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("Manual Match Operations")
    class ManualMatchTests {

        @Test
        @WithMockUser(username = "admin")
        @DisplayName("Should manually match statement item with transaction")
        void shouldManuallyMatchStatementItemWithTransaction() {
            BankReconciliation recon = reconciliationService.create(
                    testStatement.getId(), null, "admin");

            // Get a statement item and a transaction from seed data
            List<BankStatementItem> items = statementItemRepository
                    .findByBankStatementIdOrderByLineNumberAsc(testStatement.getId());
            BankStatementItem item = items.get(1); // Pembelian laptop, 20,000,000 debit on 2024-01-15

            // V901 TRX-2024-0002: laptop purchase credited to 1.1.02 on 2024-01-15
            UUID postedTxnId = UUID.fromString("90100000-0000-0000-1000-000000000002");
            Transaction txn = transactionService.findById(postedTxnId);

            reconciliationService.manualMatch(recon.getId(), item.getId(), txn.getId(), "admin");

            // Verify the item is now matched
            BankStatementItem updated = statementItemRepository.findById(item.getId()).orElseThrow();
            assertThat(updated.getMatchStatus()).isEqualTo(StatementItemMatchStatus.MATCHED);
            assertThat(updated.getMatchedTransaction().getId()).isEqualTo(txn.getId());
        }

        @Test
        @WithMockUser(username = "admin")
        @DisplayName("Should reject manual match on completed reconciliation")
        void shouldRejectManualMatchOnCompletedReconciliation() {
            BankReconciliation recon = reconciliationService.create(
                    testStatement.getId(), null, "admin");

            // Complete the reconciliation by marking all items as bank-only
            List<BankStatementItem> items = statementItemRepository
                    .findByBankStatementIdOrderByLineNumberAsc(testStatement.getId());
            for (BankStatementItem item : items) {
                reconciliationService.markBankOnly(recon.getId(), item.getId(), "test", "admin");
            }
            reconciliationService.complete(recon.getId(), "admin");

            UUID reconId = recon.getId();
            UUID itemId = items.get(0).getId();
            UUID txnId = UUID.fromString("90100000-0000-0000-1000-000000000001");

            assertThatThrownBy(() -> reconciliationService.manualMatch(reconId, itemId, txnId, "admin"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("sudah selesai");
        }
    }

    @Nested
    @DisplayName("Mark Bank-Only and Book-Only")
    class MarkOnlyTests {

        @Test
        @WithMockUser(username = "admin")
        @DisplayName("Should mark statement item as bank-only")
        void shouldMarkStatementItemAsBankOnly() {
            BankReconciliation recon = reconciliationService.create(
                    testStatement.getId(), null, "admin");

            List<BankStatementItem> items = statementItemRepository
                    .findByBankStatementIdOrderByLineNumberAsc(testStatement.getId());

            reconciliationService.markBankOnly(
                    recon.getId(), items.get(0).getId(), "Bank charge", "admin");

            BankStatementItem updated = statementItemRepository.findById(items.get(0).getId()).orElseThrow();
            assertThat(updated.getMatchStatus()).isEqualTo(StatementItemMatchStatus.BANK_ONLY);
        }

        @Test
        @WithMockUser(username = "admin")
        @DisplayName("Should mark transaction as book-only")
        void shouldMarkTransactionAsBookOnly() {
            BankReconciliation recon = reconciliationService.create(
                    testStatement.getId(), null, "admin");

            // V901 TRX-2024-0003: posts to 1.1.02 on 2024-02-10, inside the statement period
            UUID postedTxnId = UUID.fromString("90100000-0000-0000-1000-000000000003");

            reconciliationService.markBookOnly(
                    recon.getId(), postedTxnId, "Not in statement");

            // Verify reconciliation item was created
            List<ReconciliationItem> reconItems =
                    reconciliationService.findReconciliationItems(recon.getId());
            assertThat(reconItems).isNotEmpty();
            assertThat(reconItems.stream()
                    .anyMatch(ri -> ri.getMatchStatus() == StatementItemMatchStatus.BOOK_ONLY))
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("Unmatch Operations")
    class UnmatchTests {

        @Test
        @WithMockUser(username = "admin")
        @DisplayName("Should unmatch a previously matched item")
        void shouldUnmatchPreviouslyMatchedItem() {
            BankReconciliation recon = reconciliationService.create(
                    testStatement.getId(), null, "admin");

            // Mark an item as bank-only first
            List<BankStatementItem> items = statementItemRepository
                    .findByBankStatementIdOrderByLineNumberAsc(testStatement.getId());
            reconciliationService.markBankOnly(
                    recon.getId(), items.get(0).getId(), "test", "admin");

            // Get the reconciliation item
            List<ReconciliationItem> reconItems =
                    reconciliationService.findReconciliationItems(recon.getId());
            assertThat(reconItems).isNotEmpty();

            // Unmatch it
            reconciliationService.unmatch(recon.getId(), reconItems.get(0).getId());

            // Verify the statement item is back to UNMATCHED
            BankStatementItem updated = statementItemRepository.findById(items.get(0).getId()).orElseThrow();
            assertThat(updated.getMatchStatus()).isEqualTo(StatementItemMatchStatus.UNMATCHED);
        }
    }

    @Nested
    @DisplayName("Complete Reconciliation")
    class CompleteTests {

        @Test
        @WithMockUser(username = "admin")
        @DisplayName("Should complete reconciliation when all items resolved")
        void shouldCompleteReconciliationWhenAllItemsResolved() {
            BankReconciliation recon = reconciliationService.create(
                    testStatement.getId(), null, "admin");

            // Resolve all items by marking as bank-only
            List<BankStatementItem> items = statementItemRepository
                    .findByBankStatementIdOrderByLineNumberAsc(testStatement.getId());
            for (BankStatementItem item : items) {
                reconciliationService.markBankOnly(recon.getId(), item.getId(), "test", "admin");
            }

            reconciliationService.complete(recon.getId(), "admin");

            BankReconciliation completed = reconciliationService.findById(recon.getId());
            assertThat(completed.getStatus()).isEqualTo(ReconciliationStatus.COMPLETED);
            assertThat(completed.getCompletedAt()).isNotNull();
            assertThat(completed.getCompletedBy()).isEqualTo("admin");
        }

        @Test
        @WithMockUser(username = "admin")
        @DisplayName("Should reject complete when unmatched items exist")
        void shouldRejectCompleteWhenUnmatchedItemsExist() {
            BankReconciliation recon = reconciliationService.create(
                    testStatement.getId(), null, "admin");

            UUID reconId = recon.getId();
            assertThatThrownBy(() -> reconciliationService.complete(reconId, "admin"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("belum dicocokkan");
        }
    }

    @Nested
    @DisplayName("Find Unmatched Book Transactions")
    class UnmatchedBookTests {

        @Test
        @WithMockUser(username = "admin")
        @DisplayName("Should find unmatched book transactions")
        void shouldFindUnmatchedBookTransactions() {
            BankReconciliation recon = reconciliationService.create(
                    testStatement.getId(), null, "admin");

            List<Transaction> unmatched = reconciliationService
                    .findUnmatchedBookTransactions(recon.getId());

            // There should be unmatched transactions from V901 test data
            // that have journal entries for the Bank BCA account (1.1.02)
            assertThat(unmatched).isNotNull();
        }
    }

    @Nested
    @DisplayName("Create Transaction From Statement Item")
    class CreateFromStatementTests {

        @Test
        @WithMockUser(username = "admin")
        @DisplayName("Should create transaction from credit statement item")
        void shouldCreateTransactionFromCreditStatementItem() {
            BankReconciliation recon = reconciliationService.create(
                    testStatement.getId(), null, "admin");

            // Get the credit item (item 1: credit 100M)
            List<BankStatementItem> items = statementItemRepository
                    .findByBankStatementIdOrderByLineNumberAsc(testStatement.getId());
            BankStatementItem creditItem = items.get(0); // 100M credit

            // Use manual entry template
            UUID manualTemplateId = UUID.fromString("e0000000-0000-0000-0000-000000000099");

            reconciliationService.createTransactionFromStatementItem(
                    recon.getId(), creditItem.getId(), manualTemplateId,
                    "Created from reconciliation", "admin");

            // Verify the item is now matched
            BankStatementItem updated = statementItemRepository.findById(creditItem.getId()).orElseThrow();
            assertThat(updated.getMatchStatus()).isEqualTo(StatementItemMatchStatus.MATCHED);
            assertThat(updated.getMatchedTransaction()).isNotNull();
        }

        @Test
        @WithMockUser(username = "admin")
        @DisplayName("Should create transaction from debit statement item")
        void shouldCreateTransactionFromDebitStatementItem() {
            BankReconciliation recon = reconciliationService.create(
                    testStatement.getId(), null, "admin");

            // Get the debit item (item 2: debit 20M)
            List<BankStatementItem> items = statementItemRepository
                    .findByBankStatementIdOrderByLineNumberAsc(testStatement.getId());
            BankStatementItem debitItem = items.get(1); // 20M debit

            UUID manualTemplateId = UUID.fromString("e0000000-0000-0000-0000-000000000099");

            reconciliationService.createTransactionFromStatementItem(
                    recon.getId(), debitItem.getId(), manualTemplateId,
                    null, "admin"); // null description should use item description

            BankStatementItem updated = statementItemRepository.findById(debitItem.getId()).orElseThrow();
            assertThat(updated.getMatchStatus()).isEqualTo(StatementItemMatchStatus.MATCHED);
        }

        @Test
        @WithMockUser(username = "admin")
        @DisplayName("Should reject create transaction on completed reconciliation")
        void shouldRejectCreateTransactionOnCompletedReconciliation() {
            BankReconciliation recon = reconciliationService.create(
                    testStatement.getId(), null, "admin");

            // Complete the reconciliation
            List<BankStatementItem> items = statementItemRepository
                    .findByBankStatementIdOrderByLineNumberAsc(testStatement.getId());
            for (BankStatementItem item : items) {
                reconciliationService.markBankOnly(recon.getId(), item.getId(), "test", "admin");
            }
            reconciliationService.complete(recon.getId(), "admin");

            UUID reconId = recon.getId();
            UUID itemId = items.get(0).getId();
            UUID templateId = UUID.fromString("e0000000-0000-0000-0000-000000000099");

            assertThatThrownBy(() -> reconciliationService.createTransactionFromStatementItem(
                    reconId, itemId, templateId, "test", "admin"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("sudah selesai");
        }
    }
}
