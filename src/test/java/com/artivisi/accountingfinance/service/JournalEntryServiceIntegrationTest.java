package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
 * Tests journal entry creation, posting, general ledger generation.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@DisplayName("Journal Entry Service Integration Tests")
@Transactional
class JournalEntryServiceIntegrationTest {

    @Autowired
    private JournalEntryService journalEntryService;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Autowired
    private ChartOfAccountRepository chartOfAccountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private ChartOfAccount cashAccount;
    private ChartOfAccount revenueAccount;
    private ChartOfAccount expenseAccount;

    @BeforeEach
    void setup() {
        cashAccount = seededAccount("1.1.01");
        revenueAccount = seededAccount("4.1.01");
        expenseAccount = seededAccount("5.1.01");
    }

    @Test
    @DisplayName("Should validate balanced journal entries")
    void shouldValidateBalancedJournalEntries() {
        List<JournalEntry> entries = new ArrayList<>();

        JournalEntry debitEntry = new JournalEntry();
        debitEntry.setAccount(cashAccount);
        debitEntry.setDebitAmount(BigDecimal.valueOf(1000000));
        debitEntry.setCreditAmount(BigDecimal.ZERO);
        entries.add(debitEntry);

        JournalEntry creditEntry = new JournalEntry();
        creditEntry.setAccount(revenueAccount);
        creditEntry.setDebitAmount(BigDecimal.ZERO);
        creditEntry.setCreditAmount(BigDecimal.valueOf(1000000));
        entries.add(creditEntry);

        // Should not throw exception
        journalEntryService.validateBalance(entries);
    }

    @Test
    @DisplayName("Should reject unbalanced journal entries")
    void shouldRejectUnbalancedJournalEntries() {
        List<JournalEntry> entries = new ArrayList<>();

        JournalEntry debitEntry = new JournalEntry();
        debitEntry.setAccount(cashAccount);
        debitEntry.setDebitAmount(BigDecimal.valueOf(1000000));
        debitEntry.setCreditAmount(BigDecimal.ZERO);
        entries.add(debitEntry);

        JournalEntry creditEntry = new JournalEntry();
        creditEntry.setAccount(revenueAccount);
        creditEntry.setDebitAmount(BigDecimal.ZERO);
        creditEntry.setCreditAmount(BigDecimal.valueOf(900000)); // Unbalanced
        entries.add(creditEntry);

        assertThat(entries).hasSize(2);
        assertThatThrownBy(() -> journalEntryService.validateBalance(entries))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not balanced");
    }

    @Test
    @WithMockUser(username = "admin")
    @DisplayName("Should create manual journal entry")
    void shouldCreateManualJournalEntry() {
        Transaction transaction = new Transaction();
        transaction.setTransactionDate(LocalDate.now());
        transaction.setDescription("Test manual journal entry");
        transaction.setReferenceNumber("TEST-001");

        List<JournalEntry> entries = new ArrayList<>();

        JournalEntry debitEntry = new JournalEntry();
        debitEntry.setAccount(cashAccount);
        debitEntry.setDebitAmount(BigDecimal.valueOf(500000));
        debitEntry.setCreditAmount(BigDecimal.ZERO);
        entries.add(debitEntry);

        JournalEntry creditEntry = new JournalEntry();
        creditEntry.setAccount(revenueAccount);
        creditEntry.setDebitAmount(BigDecimal.ZERO);
        creditEntry.setCreditAmount(BigDecimal.valueOf(500000));
        entries.add(creditEntry);

        Transaction created = journalEntryService.create(transaction, entries);

        assertThat(created).isNotNull();
        assertThat(created.getId()).isNotNull();
        assertThat(created.getTransactionNumber()).startsWith("MJ-");
        assertThat(created.isDraft()).isTrue();
        assertThat(created.getJournalEntries()).hasSize(2);
    }

    @Test
    @WithMockUser(username = "admin")
    @DisplayName("Should reject journal entry with less than 2 lines")
    void shouldRejectJournalEntryWithLessThanTwoLines() {
        Transaction transaction = new Transaction();
        transaction.setTransactionDate(LocalDate.now());
        transaction.setDescription("Invalid entry");

        List<JournalEntry> entries = new ArrayList<>();
        JournalEntry singleEntry = new JournalEntry();
        singleEntry.setAccount(cashAccount);
        singleEntry.setDebitAmount(BigDecimal.valueOf(100000));
        singleEntry.setCreditAmount(BigDecimal.ZERO);
        entries.add(singleEntry);

        assertThat(entries).hasSize(1);
        assertThatThrownBy(() -> journalEntryService.create(transaction, entries))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 2 lines");
    }

    @Test
    @WithMockUser(username = "admin")
    @DisplayName("Should post draft journal entry")
    void shouldPostDraftJournalEntry() {
        // Create draft entry first
        Transaction transaction = new Transaction();
        transaction.setTransactionDate(LocalDate.now());
        transaction.setDescription("Entry to post");

        List<JournalEntry> entries = new ArrayList<>();

        JournalEntry debitEntry = new JournalEntry();
        debitEntry.setAccount(cashAccount);
        debitEntry.setDebitAmount(BigDecimal.valueOf(250000));
        debitEntry.setCreditAmount(BigDecimal.ZERO);
        entries.add(debitEntry);

        JournalEntry creditEntry = new JournalEntry();
        creditEntry.setAccount(revenueAccount);
        creditEntry.setDebitAmount(BigDecimal.ZERO);
        creditEntry.setCreditAmount(BigDecimal.valueOf(250000));
        entries.add(creditEntry);

        Transaction created = journalEntryService.create(transaction, entries);
        String journalNumber = created.getJournalEntries().get(0).getJournalNumber();

        // Post the entry
        List<JournalEntry> postedEntries = journalEntryService.post(journalNumber);

        assertThat(postedEntries).hasSize(2);
        assertThat(postedEntries.get(0).getPostedAt()).isNotNull();

        // Verify transaction status
        Transaction postedTx = transactionRepository.findById(created.getId()).orElseThrow();
        assertThat(postedTx.isPosted()).isTrue();
    }

    @Test
    @DisplayName("Should find entries by transaction ID")
    void shouldFindEntriesByTransactionId() {
        UUID postedTransactionId = UUID.fromString("a0000000-0000-0000-0000-000000000002");
        List<JournalEntry> entries = journalEntryService.findByTransactionId(postedTransactionId);

        assertThat(entries).extracting(JournalEntry::getId).containsExactlyInAnyOrder(
                UUID.fromString("b0000000-0000-0000-0000-000000000001"),
                UUID.fromString("b0000000-0000-0000-0000-000000000002"));
        entries.forEach(entry -> assertThat(entry.getTransaction().getId()).isEqualTo(postedTransactionId));
    }

    @Test
    @DisplayName("Should find entries by date range")
    void shouldFindEntriesByDateRange() {
        LocalDate startDate = LocalDate.now().minusMonths(3);
        LocalDate endDate = LocalDate.now().plusMonths(1);

        Page<JournalEntry> entries = journalEntryService.findAllByDateRange(
                startDate, endDate, PageRequest.of(0, 10));

        assertThat(entries).isNotNull();
        // May have entries or not depending on test data
    }

    @Test
    @DisplayName("Should generate general ledger for account")
    void shouldGenerateGeneralLedger() {
        LocalDate startDate = LocalDate.now().minusMonths(6);
        LocalDate endDate = LocalDate.now().plusMonths(1);

        JournalEntryService.GeneralLedgerData ledger = journalEntryService.getGeneralLedger(
                cashAccount.getId(), startDate, endDate);

        assertThat(ledger).isNotNull();
        assertThat(ledger.account()).isEqualTo(cashAccount);
        assertThat(ledger.openingBalance()).isNotNull();
        assertThat(ledger.totalDebit()).isNotNull();
        assertThat(ledger.totalCredit()).isNotNull();
        assertThat(ledger.closingBalance()).isNotNull();
    }

    @Test
    @DisplayName("Should generate paged general ledger")
    void shouldGeneratePagedGeneralLedger() {
        LocalDate startDate = LocalDate.now().minusMonths(6);
        LocalDate endDate = LocalDate.now().plusMonths(1);

        JournalEntryService.GeneralLedgerPagedData ledger = journalEntryService.getGeneralLedgerPaged(
                cashAccount.getId(), startDate, endDate, null, PageRequest.of(0, 10));

        assertThat(ledger).isNotNull();
        assertThat(ledger.account()).isEqualTo(cashAccount);
        assertThat(ledger.currentPage()).isZero();
        assertThat(ledger.totalElements()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("Should generate paged general ledger with search")
    void shouldGeneratePagedGeneralLedgerWithSearch() {
        LocalDate startDate = LocalDate.now().minusMonths(6);
        LocalDate endDate = LocalDate.now().plusMonths(1);

        // Search with a term that may or may not exist
        JournalEntryService.GeneralLedgerPagedData ledger = journalEntryService.getGeneralLedgerPaged(
                cashAccount.getId(), startDate, endDate, "test", PageRequest.of(0, 10));

        assertThat(ledger).isNotNull();
        assertThat(ledger.account()).isEqualTo(cashAccount);
    }

    @Test
    @DisplayName("Should throw exception for invalid account in general ledger")
    void shouldThrowExceptionForInvalidAccount() {
        assertThatThrownBy(() -> journalEntryService.getGeneralLedger(
                java.util.UUID.randomUUID(), LocalDate.now().minusMonths(1), LocalDate.now()))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Account not found");
    }

    @Test
    @WithMockUser(username = "admin")
    @DisplayName("Should void posted journal entry")
    void shouldVoidPostedJournalEntry() {
        // Create and post an entry
        Transaction transaction = new Transaction();
        transaction.setTransactionDate(LocalDate.now());
        transaction.setDescription("Entry to void");

        List<JournalEntry> entries = new ArrayList<>();

        JournalEntry debitEntry = new JournalEntry();
        debitEntry.setAccount(expenseAccount);
        debitEntry.setDebitAmount(BigDecimal.valueOf(100000));
        debitEntry.setCreditAmount(BigDecimal.ZERO);
        entries.add(debitEntry);

        JournalEntry creditEntry = new JournalEntry();
        creditEntry.setAccount(cashAccount);
        creditEntry.setDebitAmount(BigDecimal.ZERO);
        creditEntry.setCreditAmount(BigDecimal.valueOf(100000));
        entries.add(creditEntry);

        Transaction created = journalEntryService.create(transaction, entries);
        String journalNumber = created.getJournalEntries().get(0).getJournalNumber();

        // Post first
        journalEntryService.post(journalNumber);

        // Now void
        List<JournalEntry> voidedEntries = journalEntryService.voidEntry(journalNumber, "Test void reason");

        // Should have at least the entries we created
        assertThat(voidedEntries).isNotEmpty();
        // All returned entries should be voided
        voidedEntries.forEach(entry -> {
            assertThat(entry.getVoidedAt()).isNotNull();
            assertThat(entry.getVoidReason()).isEqualTo("Test void reason");
        });
    }

    @Test
    @WithMockUser(username = "admin")
    @DisplayName("Should reject void without reason")
    void shouldRejectVoidWithoutReason() {
        // Create and post an entry
        Transaction transaction = new Transaction();
        transaction.setTransactionDate(LocalDate.now());
        transaction.setDescription("Entry for void test");

        List<JournalEntry> entries = new ArrayList<>();

        JournalEntry debitEntry = new JournalEntry();
        debitEntry.setAccount(cashAccount);
        debitEntry.setDebitAmount(BigDecimal.valueOf(50000));
        debitEntry.setCreditAmount(BigDecimal.ZERO);
        entries.add(debitEntry);

        JournalEntry creditEntry = new JournalEntry();
        creditEntry.setAccount(revenueAccount);
        creditEntry.setDebitAmount(BigDecimal.ZERO);
        creditEntry.setCreditAmount(BigDecimal.valueOf(50000));
        entries.add(creditEntry);

        Transaction created = journalEntryService.create(transaction, entries);
        String journalNumber = created.getJournalEntries().get(0).getJournalNumber();
        journalEntryService.post(journalNumber);

        assertThatThrownBy(() -> journalEntryService.voidEntry(journalNumber, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason is required");
    }

    @Test
    @DisplayName("Should calculate account impact")
    void shouldCalculateAccountImpact() {
        List<JournalEntry> existingEntries = journalEntryService.findByTransactionId(
                UUID.fromString("a0000000-0000-0000-0000-000000000002"));
        assertThat(existingEntries).as("V904 journal entries of TRX-TEST-0002").hasSize(2);

        List<JournalEntryService.AccountImpact> impacts = journalEntryService.calculateAccountImpact(existingEntries);

        assertThat(impacts).extracting(i -> i.account().getAccountCode()).containsExactlyInAnyOrder("1.1.02", "4.1.01");
        impacts.forEach(impact -> assertThat(impact.afterBalance().subtract(impact.beforeBalance()))
                .as("balance change of %s", impact.account().getAccountCode())
                .isEqualByComparingTo("15000000"));
    }

    @Test
    @DisplayName("Should return empty list for empty entries in account impact")
    void shouldReturnEmptyListForEmptyEntries() {
        List<JournalEntryService.AccountImpact> impacts = journalEntryService.calculateAccountImpact(List.of());
        assertThat(impacts).isEmpty();
    }

    // Note: Update draft test removed due to collection handling complexity in service layer
    // The update functionality is covered via functional tests through the UI

    @Test
    @DisplayName("Should find entries by account and date range")
    void shouldFindEntriesByAccountAndDateRange() {
        LocalDate startDate = LocalDate.now().minusMonths(6);
        LocalDate endDate = LocalDate.now().plusMonths(1);

        List<JournalEntry> entries = journalEntryService.findByAccountAndDateRange(
                cashAccount.getId(), startDate, endDate);

        assertThat(entries).isNotNull();
        // All returned entries should be for the specified account
        entries.forEach(entry -> assertThat(entry.getAccount().getId()).isEqualTo(cashAccount.getId()));
    }

    private ChartOfAccount seededAccount(String code) {
        return chartOfAccountRepository.findByAccountCode(code)
                .orElseThrow(() -> new IllegalStateException("Seeded account missing: " + code));
    }
}
