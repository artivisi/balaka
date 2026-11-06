package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.FiscalPeriod;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.RecurringTransaction;
import com.artivisi.accountingfinance.entity.RecurringTransactionLog;
import com.artivisi.accountingfinance.enums.FiscalPeriodStatus;
import com.artivisi.accountingfinance.enums.RecurringFrequency;
import com.artivisi.accountingfinance.enums.RecurringLogStatus;
import com.artivisi.accountingfinance.enums.RecurringStatus;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.FiscalPeriodRepository;
import com.artivisi.accountingfinance.repository.RecurringTransactionLogRepository;
import com.artivisi.accountingfinance.repository.RecurringTransactionRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.service.JournalTemplateService;
import com.artivisi.accountingfinance.service.RecurringTransactionService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Recurring Transaction Scheduler Tests")
@Import(ServiceTestDataInitializer.class)
class RecurringSchedulerTest extends PlaywrightTestBase {

    @Autowired
    private RecurringTransactionRepository recurringTransactionRepository;

    @Autowired
    private RecurringTransactionLogRepository logRepository;

    @Autowired
    private RecurringTransactionService recurringTransactionService;

    @Autowired
    private JournalTemplateService journalTemplateService;

    @Autowired
    private FiscalPeriodRepository fiscalPeriodRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("Should create transaction for due recurring")
    void shouldProcessDueRecurring() {
        RecurringTransaction recurring = createTestRecurring("Scheduler Due Test", LocalDate.now());

        int processed = recurringTransactionService.processAllDue();

        assertThat(processed).isGreaterThanOrEqualTo(1);

        // Verify log was created
        List<RecurringTransactionLog> logs = logRepository
                .findByRecurringTransactionIdOrderByScheduledDateDesc(recurring.getId());
        assertThat(logs).isNotEmpty();
        assertThat(logs.getFirst().getStatus()).isEqualTo(RecurringLogStatus.SUCCESS);
        assertThat(logs.getFirst().getTransaction()).isNotNull();

        // Verify recurring was updated
        RecurringTransaction updated = recurringTransactionRepository.findById(recurring.getId()).orElseThrow();
        assertThat(updated.getTotalRuns()).isEqualTo(1);
        assertThat(updated.getLastRunDate()).isEqualTo(LocalDate.now());
        assertThat(updated.getNextRunDate()).isAfter(LocalDate.now());
    }

    @Test
    @DisplayName("A failing item is logged FAILED in its own transaction and the other items still commit")
    void failingItemDoesNotRollBackOthers() {
        // Reserved 2000-01: a closed period makes the auto-post of that item throw inside TransactionService.post
        assertThat(fiscalPeriodRepository.findByYearAndMonth(2000, 1)).isEmpty();
        FiscalPeriod closed = new FiscalPeriod();
        closed.setYear(2000);
        closed.setMonth(1);
        closed.setStatus(FiscalPeriodStatus.MONTH_CLOSED);
        closed = fiscalPeriodRepository.save(closed);
        RecurringTransaction failing = createTestRecurring("Scheduler Closed Period", LocalDate.of(2000, 1, 15));
        try {
            RecurringTransaction good = createTestRecurring("Scheduler Good Item", LocalDate.now());

            int processed = recurringTransactionService.processAllDue();

            assertThat(processed).isGreaterThanOrEqualTo(1);
            List<RecurringTransactionLog> failedLogs = logRepository
                    .findByRecurringTransactionIdOrderByScheduledDateDesc(failing.getId());
            assertThat(failedLogs).singleElement().satisfies(entry -> {
                assertThat(entry.getStatus()).isEqualTo(RecurringLogStatus.FAILED);
                assertThat(entry.getScheduledDate()).isEqualTo(LocalDate.of(2000, 1, 15));
                assertThat(entry.getErrorMessage()).startsWith("Cannot post journal entry. Fiscal period");
                assertThat(entry.getTransaction()).isNull();
            });
            RecurringTransaction failingAfter = recurringTransactionRepository.findById(failing.getId()).orElseThrow();
            assertThat(failingAfter.getTotalRuns()).isZero();
            assertThat(failingAfter.getNextRunDate()).isEqualTo(LocalDate.of(2000, 1, 15));

            List<RecurringTransactionLog> goodLogs = logRepository
                    .findByRecurringTransactionIdOrderByScheduledDateDesc(good.getId());
            assertThat(goodLogs).singleElement().satisfies(entry ->
                    assertThat(entry.getStatus()).isEqualTo(RecurringLogStatus.SUCCESS));
            assertThat(transactionRepository.findById(goodLogs.getFirst().getTransaction().getId()).orElseThrow().getStatus())
                    .isEqualTo(TransactionStatus.POSTED);
            assertThat(recurringTransactionRepository.findById(good.getId()).orElseThrow().getTotalRuns()).isEqualTo(1);
        } finally {
            RecurringTransaction cleanup = recurringTransactionRepository.findById(failing.getId()).orElseThrow();
            cleanup.setStatus(RecurringStatus.PAUSED);
            recurringTransactionRepository.save(cleanup);
            fiscalPeriodRepository.delete(closed);
        }
    }

    @Test
    @DisplayName("Should skip paused recurring")
    void shouldSkipPausedRecurring() {
        RecurringTransaction recurring = createTestRecurring("Scheduler Paused Test", LocalDate.now());
        recurring.setStatus(RecurringStatus.PAUSED);
        recurringTransactionRepository.save(recurring);

        recurringTransactionService.processAllDue();

        // The paused one should not be processed
        List<RecurringTransactionLog> logs = logRepository
                .findByRecurringTransactionIdOrderByScheduledDateDesc(recurring.getId());
        assertThat(logs).isEmpty();

        RecurringTransaction updated = recurringTransactionRepository.findById(recurring.getId()).orElseThrow();
        assertThat(updated.getTotalRuns()).isZero();
    }

    @Test
    @DisplayName("Should handle weekend skip")
    void shouldSkipWeekend() {
        // Find the next Saturday
        LocalDate nextSaturday = LocalDate.now();
        while (nextSaturday.getDayOfWeek().getValue() != 6) {
            nextSaturday = nextSaturday.plusDays(1);
        }

        RecurringTransaction recurring = createTestRecurring("Weekend Skip Test", nextSaturday);
        recurring.setSkipWeekends(true);
        recurringTransactionRepository.save(recurring);

        // If the next Saturday is today, process it
        if (nextSaturday.isEqual(LocalDate.now()) || nextSaturday.isBefore(LocalDate.now())) {
            int processed = recurringTransactionService.processAllDue();
            assertThat(processed).isGreaterThanOrEqualTo(0);
        }
        // Test the skip logic directly
        LocalDate result = RecurringTransactionService.calculateNextRunDate(
                RecurringFrequency.DAILY, null, null, nextSaturday, true);
        // Should advance to Monday
        assertThat(result.getDayOfWeek().getValue()).isEqualTo(1); // Monday
    }

    @Test
    @DisplayName("Should mark completed when maxOccurrences reached")
    void shouldCompleteWhenMaxOccurrencesReached() {
        RecurringTransaction recurring = createTestRecurring("Max Occurrences Test", LocalDate.now());
        recurring.setMaxOccurrences(1);
        recurringTransactionRepository.save(recurring);

        recurringTransactionService.processAllDue();

        RecurringTransaction updated = recurringTransactionRepository.findById(recurring.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(RecurringStatus.COMPLETED);
        assertThat(updated.getTotalRuns()).isEqualTo(1);
        assertThat(updated.getNextRunDate()).isNull();
    }

    private RecurringTransaction createTestRecurring(String name, LocalDate nextRunDate) {
        List<JournalTemplate> templates = journalTemplateService.findAll();
        JournalTemplate template = templates.getFirst();

        RecurringTransaction recurring = new RecurringTransaction();
        recurring.setName(name);
        recurring.setJournalTemplate(template);
        recurring.setAmount(new BigDecimal("1000000"));
        recurring.setDescription("Scheduler test: " + name);
        recurring.setFrequency(RecurringFrequency.MONTHLY);
        // dayOfMonth is constrained to 1-28 on the entity; clamp so the test is
        // date-independent (nextRunDate can fall on the 29th-31st).
        recurring.setDayOfMonth(Math.min(nextRunDate.getDayOfMonth(), 28));
        recurring.setStartDate(nextRunDate);
        recurring.setNextRunDate(nextRunDate);
        recurring.setStatus(RecurringStatus.ACTIVE);
        recurring.setAutoPost(true);
        recurring.setCreatedBy("admin");

        return recurringTransactionRepository.save(recurring);
    }
}
