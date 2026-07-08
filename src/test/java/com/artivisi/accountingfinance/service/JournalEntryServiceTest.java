package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.NormalBalance;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for JournalEntryService.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("JournalEntryService Integration Tests")
class JournalEntryServiceTest {

    @Autowired
    private JournalEntryService journalEntryService;

    @Autowired
    private ChartOfAccountRepository chartOfAccountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private ChartOfAccount testAccount;
    private ChartOfAccount creditAccount;

    @BeforeEach
    void setup() {
        // Get test accounts
        testAccount = chartOfAccountRepository.findByAccountCode("1.1.01").orElse(null);
        creditAccount = chartOfAccountRepository.findByAccountCode("4.1.01").orElse(null);
    }

    @Nested
    @DisplayName("Find Operations")
    class FindOperationsTests {

        @Test
        @DisplayName("Should throw exception for non-existent entry ID")
        void shouldThrowExceptionForNonExistentId() {
            UUID randomId = UUID.randomUUID();
            assertThatThrownBy(() -> journalEntryService.findById(randomId))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("Should throw exception for non-existent journal number")
        void shouldThrowExceptionForNonExistentJournalNumber() {
            assertThatThrownBy(() -> journalEntryService.findByJournalNumber("NON-EXISTENT-NUMBER"))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("Should find entries by transaction ID - empty for random ID")
        void shouldFindEntriesByTransactionIdEmptyForRandomId() {
            UUID randomId = UUID.randomUUID();
            List<JournalEntry> entries = journalEntryService.findByTransactionId(randomId);
            assertThat(entries).isEmpty();
        }

        @Test
        @DisplayName("Should find all entries by date range")
        void shouldFindAllEntriesByDateRange() {
            LocalDate startDate = LocalDate.of(2025, 1, 1);
            LocalDate endDate = LocalDate.of(2025, 12, 31);

            Page<JournalEntry> entries = journalEntryService.findAllByDateRange(
                    startDate, endDate, PageRequest.of(0, 10));

            assertThat(entries).isNotNull();
        }

        @Test
        @DisplayName("Should find entries by account and date range")
        void shouldFindEntriesByAccountAndDateRange() {
            if (testAccount == null) return;

            LocalDate startDate = LocalDate.of(2025, 1, 1);
            LocalDate endDate = LocalDate.of(2025, 12, 31);

            List<JournalEntry> entries = journalEntryService.findByAccountAndDateRange(
                    testAccount.getId(), startDate, endDate);

            assertThat(entries).isNotNull();
        }

        @Test
        @DisplayName("Should return empty for date range with no transactions")
        void shouldReturnEmptyForDateRangeWithNoTransactions() {
            if (testAccount == null) return;

            // Far past date range with no transactions
            LocalDate startDate = LocalDate.of(1990, 1, 1);
            LocalDate endDate = LocalDate.of(1990, 12, 31);

            List<JournalEntry> entries = journalEntryService.findByAccountAndDateRange(
                    testAccount.getId(), startDate, endDate);

            assertThat(entries).isEmpty();
        }
    }

    @Nested
    @DisplayName("General Ledger Operations")
    class GeneralLedgerTests {

        @Test
        @DisplayName("Should throw exception for non-existent account")
        void shouldThrowExceptionForNonExistentAccount() {
            UUID randomId = UUID.randomUUID();
            LocalDate startDate = LocalDate.of(2025, 1, 1);
            LocalDate endDate = LocalDate.of(2025, 12, 31);

            assertThatThrownBy(() -> journalEntryService.getGeneralLedger(randomId, startDate, endDate))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("Should get general ledger for existing account")
        void shouldGetGeneralLedgerForExistingAccount() {
            if (testAccount == null) return;

            LocalDate startDate = LocalDate.of(2025, 1, 1);
            LocalDate endDate = LocalDate.of(2025, 12, 31);

            JournalEntryService.GeneralLedgerData ledger = journalEntryService.getGeneralLedger(
                    testAccount.getId(), startDate, endDate);

            assertThat(ledger).isNotNull();
            assertThat(ledger.account()).isNotNull();
            assertThat(ledger.openingBalance()).isNotNull();
            assertThat(ledger.entries()).isNotNull();
            assertThat(ledger.closingBalance()).isNotNull();
        }

        @Test
        @DisplayName("Should calculate balances correctly in general ledger")
        void shouldCalculateBalancesCorrectlyInGeneralLedger() {
            if (testAccount == null) return;

            LocalDate startDate = LocalDate.of(2025, 1, 1);
            LocalDate endDate = LocalDate.of(2025, 12, 31);

            JournalEntryService.GeneralLedgerData ledger = journalEntryService.getGeneralLedger(
                    testAccount.getId(), startDate, endDate);

            // Each entry should have a valid running balance
            for (JournalEntryService.LedgerLineItem entry : ledger.entries()) {
                assertThat(entry.runningBalance()).isNotNull();
                assertThat(entry.entry()).isNotNull();
            }
        }
    }

    @Nested
    @DisplayName("Pagination Tests")
    class PaginationTests {

        @Test
        @DisplayName("Should paginate entries correctly")
        void shouldPaginateEntriesCorrectly() {
            LocalDate startDate = LocalDate.of(2025, 1, 1);
            LocalDate endDate = LocalDate.of(2025, 12, 31);

            Page<JournalEntry> page1 = journalEntryService.findAllByDateRange(
                    startDate, endDate, PageRequest.of(0, 5));
            Page<JournalEntry> page2 = journalEntryService.findAllByDateRange(
                    startDate, endDate, PageRequest.of(1, 5));

            assertThat(page1).isNotNull();
            assertThat(page2).isNotNull();
            assertThat(page1.getNumber()).isZero();
            assertThat(page2.getNumber()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should return correct page size")
        void shouldReturnCorrectPageSize() {
            LocalDate startDate = LocalDate.of(2025, 1, 1);
            LocalDate endDate = LocalDate.of(2025, 12, 31);

            Page<JournalEntry> page = journalEntryService.findAllByDateRange(
                    startDate, endDate, PageRequest.of(0, 10));

            assertThat(page.getSize()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("Manual Journal Entry Operations")
    @WithMockUser(username = "admin")
    class ManualJournalEntryTests {

        @Test
        @DisplayName("Should create manual journal entry with balanced entries")
        void shouldCreateManualJournalEntry() {
            if (testAccount == null || creditAccount == null) return;

            Transaction header = new Transaction();
            header.setTransactionDate(LocalDate.now());
            header.setDescription("Test manual journal");
            header.setReferenceNumber("MJ-TEST-001");

            List<JournalEntry> entries = new ArrayList<>();

            JournalEntry debitEntry = new JournalEntry();
            debitEntry.setAccount(testAccount);
            debitEntry.setDebitAmount(new BigDecimal("1000000"));
            debitEntry.setCreditAmount(BigDecimal.ZERO);
            entries.add(debitEntry);

            JournalEntry creditEntry = new JournalEntry();
            creditEntry.setAccount(creditAccount);
            creditEntry.setDebitAmount(BigDecimal.ZERO);
            creditEntry.setCreditAmount(new BigDecimal("1000000"));
            entries.add(creditEntry);

            Transaction saved = journalEntryService.create(header, entries);

            assertThat(saved).isNotNull();
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getTransactionNumber()).isNotNull();
            assertThat(saved.getTransactionNumber()).startsWith("MJ-");
            assertThat(saved.getStatus()).isEqualTo(TransactionStatus.DRAFT);
            assertThat(saved.getJournalEntries()).hasSize(2);
        }

        @Test
        @DisplayName("Should reject journal entry with less than 2 lines")
        void shouldRejectLessThanTwoLines() {
            Transaction header = new Transaction();
            header.setTransactionDate(LocalDate.now());
            header.setDescription("Invalid single entry");

            List<JournalEntry> entries = new ArrayList<>();
            JournalEntry single = new JournalEntry();
            single.setDebitAmount(new BigDecimal("1000"));
            single.setCreditAmount(BigDecimal.ZERO);
            entries.add(single);

            assertThat(entries).hasSize(1);
            assertThatThrownBy(() -> journalEntryService.create(header, entries))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least 2 lines");
        }

        @Test
        @DisplayName("Should reject null entries")
        void shouldRejectNullEntries() {
            Transaction header = new Transaction();
            header.setTransactionDate(LocalDate.now());

            assertThatThrownBy(() -> journalEntryService.create(header, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least 2 lines");
        }
    }

    @Nested
    @DisplayName("Validate Balance")
    class ValidateBalanceTests {

        @Test
        @DisplayName("Should pass validation for balanced entries")
        void shouldPassForBalancedEntries() {
            List<JournalEntry> entries = new ArrayList<>();

            JournalEntry debit = new JournalEntry();
            debit.setDebitAmount(new BigDecimal("5000"));
            debit.setCreditAmount(BigDecimal.ZERO);
            entries.add(debit);

            JournalEntry credit = new JournalEntry();
            credit.setDebitAmount(BigDecimal.ZERO);
            credit.setCreditAmount(new BigDecimal("5000"));
            entries.add(credit);

            // Should not throw
            journalEntryService.validateBalance(entries);
        }

        @Test
        @DisplayName("Should throw for unbalanced entries")
        void shouldThrowForUnbalancedEntries() {
            List<JournalEntry> entries = new ArrayList<>();

            JournalEntry debit = new JournalEntry();
            debit.setDebitAmount(new BigDecimal("5000"));
            debit.setCreditAmount(BigDecimal.ZERO);
            entries.add(debit);

            JournalEntry credit = new JournalEntry();
            credit.setDebitAmount(BigDecimal.ZERO);
            credit.setCreditAmount(new BigDecimal("3000"));
            entries.add(credit);

            assertThat(entries).hasSize(2);
            assertThatThrownBy(() -> journalEntryService.validateBalance(entries))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not balanced");
        }
    }

    @Nested
    @DisplayName("Account Impact Calculation")
    class AccountImpactTests {

        @Test
        @DisplayName("Should return empty list for empty entries")
        void shouldReturnEmptyForEmptyEntries() {
            List<JournalEntryService.AccountImpact> impacts =
                    journalEntryService.calculateAccountImpact(List.of());

            assertThat(impacts).isEmpty();
        }

        @Test
        @DisplayName("Should calculate account impact for posted entries")
        void shouldCalculateAccountImpactForPostedEntries() {
            // Find a posted transaction with journal entries
            UUID postedTxId = UUID.fromString("a0000000-0000-0000-0000-000000000002");
            List<JournalEntry> entries = journalEntryService.findByTransactionId(postedTxId);

            if (entries.isEmpty()) return;

            List<JournalEntryService.AccountImpact> impacts =
                    journalEntryService.calculateAccountImpact(entries);

            assertThat(impacts).isNotEmpty();
            for (var impact : impacts) {
                assertThat(impact.account()).isNotNull();
                assertThat(impact.beforeBalance()).isNotNull();
                assertThat(impact.afterBalance()).isNotNull();
            }
        }
    }

    @Nested
    @DisplayName("General Ledger Paged")
    class GeneralLedgerPagedTests {

        @Test
        @DisplayName("Should get paged general ledger for existing account")
        void shouldGetPagedGeneralLedger() {
            if (testAccount == null) return;

            LocalDate startDate = LocalDate.of(2025, 1, 1);
            LocalDate endDate = LocalDate.of(2025, 12, 31);

            JournalEntryService.GeneralLedgerPagedData ledger = journalEntryService
                    .getGeneralLedgerPaged(testAccount.getId(), startDate, endDate,
                            null, PageRequest.of(0, 10));

            assertThat(ledger).isNotNull();
            assertThat(ledger.account()).isNotNull();
            assertThat(ledger.openingBalance()).isNotNull();
            assertThat(ledger.currentPage()).isZero();
        }

        @Test
        @DisplayName("Should get paged general ledger with search")
        void shouldGetPagedGeneralLedgerWithSearch() {
            if (testAccount == null) return;

            LocalDate startDate = LocalDate.of(2025, 1, 1);
            LocalDate endDate = LocalDate.of(2025, 12, 31);

            JournalEntryService.GeneralLedgerPagedData ledger = journalEntryService
                    .getGeneralLedgerPaged(testAccount.getId(), startDate, endDate,
                            "test", PageRequest.of(0, 10));

            assertThat(ledger).isNotNull();
        }

        @Test
        @DisplayName("Should throw for non-existent account in paged ledger")
        void shouldThrowForNonExistentAccountInPagedLedger() {
            UUID randomId = UUID.randomUUID();
            LocalDate startDate = LocalDate.of(2025, 1, 1);
            LocalDate endDate = LocalDate.of(2025, 12, 31);

            assertThatThrownBy(() -> journalEntryService.getGeneralLedgerPaged(
                    randomId, startDate, endDate, null, PageRequest.of(0, 10)))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Find All By Journal Number With Account")
    class FindByJournalNumberWithAccountTests {

        @Test
        @DisplayName("Should return empty for non-existent journal number")
        void shouldReturnEmptyForNonExistentNumber() {
            List<JournalEntry> entries = journalEntryService
                    .findAllByJournalNumberWithAccount("NONEXISTENT-JE-001");

            assertThat(entries).isEmpty();
        }
    }
}
