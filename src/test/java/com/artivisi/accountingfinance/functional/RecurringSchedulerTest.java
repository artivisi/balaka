package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.RecurringTransaction;
import com.artivisi.accountingfinance.entity.RecurringTransactionLog;
import com.artivisi.accountingfinance.enums.RecurringFrequency;
import com.artivisi.accountingfinance.enums.RecurringLogStatus;
import com.artivisi.accountingfinance.enums.RecurringStatus;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.RecurringTransactionLogRepository;
import com.artivisi.accountingfinance.repository.RecurringTransactionRepository;
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
    @DisplayName("Should skip paused recurring")
    void shouldSkipPausedRecurring() {
        RecurringTransaction recurring = createTestRecurring("Scheduler Paused Test", LocalDate.now());
        recurring.setStatus(RecurringStatus.PAUSED);
        recurringTransactionRepository.save(recurring);

        int processed = recurringTransactionService.processAllDue();

        // The paused one should not be processed
        List<RecurringTransactionLog> logs = logRepository
                .findByRecurringTransactionIdOrderByScheduledDateDesc(recurring.getId());
        assertThat(logs).isEmpty();

        RecurringTransaction updated = recurringTransactionRepository.findById(recurring.getId()).orElseThrow();
        assertThat(updated.getTotalRuns()).isEqualTo(0);
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
        recurring.setDayOfMonth(nextRunDate.getDayOfMonth());
        recurring.setStartDate(nextRunDate);
        recurring.setNextRunDate(nextRunDate);
        recurring.setStatus(RecurringStatus.ACTIVE);
        recurring.setAutoPost(true);
        recurring.setCreatedBy("admin");

        return recurringTransactionRepository.save(recurring);
    }
}
