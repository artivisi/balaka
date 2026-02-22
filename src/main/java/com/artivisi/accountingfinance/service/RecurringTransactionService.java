package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.JournalTemplateLine;
import com.artivisi.accountingfinance.entity.RecurringTransaction;
import com.artivisi.accountingfinance.entity.RecurringTransactionAccountMapping;
import com.artivisi.accountingfinance.entity.RecurringTransactionLog;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.RecurringFrequency;
import com.artivisi.accountingfinance.enums.RecurringLogStatus;
import com.artivisi.accountingfinance.enums.RecurringStatus;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.RecurringTransactionLogRepository;
import com.artivisi.accountingfinance.repository.RecurringTransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RecurringTransactionService {

    private static final String ERR_NOT_FOUND = "Transaksi berulang tidak ditemukan: ";

    private final RecurringTransactionRepository recurringTransactionRepository;
    private final RecurringTransactionLogRepository logRepository;
    private final ChartOfAccountRepository chartOfAccountRepository;
    private final JournalTemplateService journalTemplateService;
    private final TransactionService transactionService;

    public RecurringTransaction findById(UUID id) {
        RecurringTransaction result = recurringTransactionRepository.findByIdWithTemplate(id);
        if (result == null) {
            throw new EntityNotFoundException(ERR_NOT_FOUND + id);
        }
        return result;
    }

    public RecurringTransaction findByIdWithMappings(UUID id) {
        RecurringTransaction result = recurringTransactionRepository.findByIdWithMappings(id);
        if (result == null) {
            throw new EntityNotFoundException(ERR_NOT_FOUND + id);
        }
        return result;
    }

    public Page<RecurringTransaction> findAll(RecurringStatus status, Pageable pageable) {
        if (status != null) {
            return recurringTransactionRepository.findByStatus(status, pageable);
        }
        return recurringTransactionRepository.findAllWithTemplate(pageable);
    }

    public long countByStatus(RecurringStatus status) {
        return recurringTransactionRepository.countByStatus(status);
    }

    public List<RecurringTransaction> findUpcoming(LocalDate start, LocalDate end) {
        return recurringTransactionRepository.findUpcoming(RecurringStatus.ACTIVE, start, end);
    }

    @Transactional
    public RecurringTransaction create(RecurringTransaction entity, Map<UUID, UUID> accountMappings) {
        JournalTemplate template = journalTemplateService.findByIdWithLines(entity.getJournalTemplate().getId());
        entity.setJournalTemplate(template);
        entity.setStatus(RecurringStatus.ACTIVE);
        entity.setTotalRuns(0);
        entity.setNextRunDate(calculateNextRunDate(
                entity.getFrequency(), entity.getDayOfMonth(), entity.getDayOfWeek(),
                entity.getStartDate(), entity.isSkipWeekends()));

        if (accountMappings != null && !accountMappings.isEmpty()) {
            for (JournalTemplateLine line : template.getLines()) {
                UUID overrideAccountId = accountMappings.get(line.getId());
                if (overrideAccountId != null) {
                    ChartOfAccount account = chartOfAccountRepository.findById(overrideAccountId)
                            .orElseThrow(() -> new EntityNotFoundException("Akun tidak ditemukan"));
                    RecurringTransactionAccountMapping mapping = new RecurringTransactionAccountMapping();
                    mapping.setTemplateLine(line);
                    mapping.setAccount(account);
                    entity.addAccountMapping(mapping);
                }
            }
        }

        return recurringTransactionRepository.save(entity);
    }

    @Transactional
    public RecurringTransaction update(UUID id, RecurringTransaction data, Map<UUID, UUID> accountMappings) {
        RecurringTransaction existing = findByIdWithMappings(id);

        if (existing.isCompleted()) {
            throw new IllegalStateException("Transaksi berulang yang sudah selesai tidak bisa diedit");
        }

        existing.setName(data.getName());
        existing.setAmount(data.getAmount());
        existing.setDescription(data.getDescription());
        existing.setFrequency(data.getFrequency());
        existing.setDayOfMonth(data.getDayOfMonth());
        existing.setDayOfWeek(data.getDayOfWeek());
        existing.setStartDate(data.getStartDate());
        existing.setEndDate(data.getEndDate());
        existing.setSkipWeekends(data.isSkipWeekends());
        existing.setAutoPost(data.isAutoPost());
        existing.setMaxOccurrences(data.getMaxOccurrences());

        // Update template if changed
        if (!existing.getJournalTemplate().getId().equals(data.getJournalTemplate().getId())) {
            JournalTemplate template = journalTemplateService.findByIdWithLines(data.getJournalTemplate().getId());
            existing.setJournalTemplate(template);
        }

        // Rebuild account mappings
        existing.clearAccountMappings();
        if (accountMappings != null && !accountMappings.isEmpty()) {
            JournalTemplate template = journalTemplateService.findByIdWithLines(existing.getJournalTemplate().getId());
            for (JournalTemplateLine line : template.getLines()) {
                UUID overrideAccountId = accountMappings.get(line.getId());
                if (overrideAccountId != null) {
                    ChartOfAccount account = chartOfAccountRepository.findById(overrideAccountId)
                            .orElseThrow(() -> new EntityNotFoundException("Akun tidak ditemukan"));
                    RecurringTransactionAccountMapping mapping = new RecurringTransactionAccountMapping();
                    mapping.setTemplateLine(line);
                    mapping.setAccount(account);
                    existing.addAccountMapping(mapping);
                }
            }
        }

        // Recalculate next run date
        LocalDate fromDate = existing.getLastRunDate() != null ? existing.getLastRunDate().plusDays(1) : existing.getStartDate();
        existing.setNextRunDate(calculateNextRunDate(
                existing.getFrequency(), existing.getDayOfMonth(), existing.getDayOfWeek(),
                fromDate, existing.isSkipWeekends()));

        return recurringTransactionRepository.save(existing);
    }

    @Transactional
    public void delete(UUID id) {
        RecurringTransaction entity = findById(id);
        entity.softDelete();
        recurringTransactionRepository.save(entity);
    }

    @Transactional
    public RecurringTransaction pause(UUID id) {
        RecurringTransaction entity = findById(id);
        if (!entity.isActive()) {
            throw new IllegalStateException("Hanya transaksi berulang aktif yang bisa dijeda");
        }
        entity.setStatus(RecurringStatus.PAUSED);
        return recurringTransactionRepository.save(entity);
    }

    @Transactional
    public RecurringTransaction resume(UUID id) {
        RecurringTransaction entity = findById(id);
        if (!entity.isPaused()) {
            throw new IllegalStateException("Hanya transaksi berulang yang dijeda yang bisa dilanjutkan");
        }
        entity.setStatus(RecurringStatus.ACTIVE);
        // Recalculate next run date from today
        entity.setNextRunDate(calculateNextRunDate(
                entity.getFrequency(), entity.getDayOfMonth(), entity.getDayOfWeek(),
                LocalDate.now(), entity.isSkipWeekends()));
        return recurringTransactionRepository.save(entity);
    }

    @Transactional
    public RecurringTransaction complete(UUID id) {
        RecurringTransaction entity = findById(id);
        if (entity.isCompleted()) {
            throw new IllegalStateException("Transaksi berulang sudah selesai");
        }
        entity.setStatus(RecurringStatus.COMPLETED);
        entity.setNextRunDate(null);
        return recurringTransactionRepository.save(entity);
    }

    /**
     * Preview the next N occurrence dates for a recurring transaction configuration.
     */
    public List<LocalDate> previewOccurrences(RecurringFrequency frequency, Integer dayOfMonth,
                                               Integer dayOfWeek, LocalDate startDate,
                                               boolean skipWeekends, LocalDate endDate,
                                               Integer maxOccurrences, int count) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate current = startDate;

        for (int i = 0; i < count; i++) {
            LocalDate next = calculateNextRunDate(frequency, dayOfMonth, dayOfWeek, current, skipWeekends);
            if (next == null) {
                break;
            }
            if (endDate != null && next.isAfter(endDate)) {
                break;
            }
            if (maxOccurrences != null && i >= maxOccurrences) {
                break;
            }
            dates.add(next);
            current = next.plusDays(1);
        }

        return dates;
    }

    /**
     * Process all due recurring transactions. Called by the scheduler.
     * Returns the number of successfully processed transactions.
     */
    @Transactional
    public int processAllDue() {
        LocalDate today = LocalDate.now();
        List<RecurringTransaction> dueTransactions =
                recurringTransactionRepository.findByStatusAndNextRunDateLessThanEqual(RecurringStatus.ACTIVE, today);

        int successCount = 0;
        for (RecurringTransaction recurring : dueTransactions) {
            try {
                processOne(recurring, today);
                successCount++;
            } catch (Exception e) {
                log.error("Failed to process recurring transaction: {} ({})", recurring.getName(), recurring.getId(), e);
                RecurringTransactionLog logEntry = new RecurringTransactionLog();
                logEntry.setScheduledDate(recurring.getNextRunDate());
                logEntry.setExecutedAt(LocalDateTime.now());
                logEntry.setStatus(RecurringLogStatus.FAILED);
                logEntry.setErrorMessage(e.getMessage());
                recurring.addLog(logEntry);
                recurringTransactionRepository.save(recurring);
            }
        }
        return successCount;
    }

    private void processOne(RecurringTransaction recurring, LocalDate today) {
        LocalDate scheduledDate = recurring.getNextRunDate();

        // Check if weekend and skipWeekends is enabled
        if (recurring.isSkipWeekends() && isWeekend(scheduledDate)) {
            RecurringTransactionLog logEntry = new RecurringTransactionLog();
            logEntry.setScheduledDate(scheduledDate);
            logEntry.setExecutedAt(LocalDateTime.now());
            logEntry.setStatus(RecurringLogStatus.SKIPPED);
            logEntry.setErrorMessage("Dilewati karena jatuh pada akhir pekan");
            recurring.addLog(logEntry);

            // Advance to next Monday
            scheduledDate = advancePastWeekend(scheduledDate);
        }

        // Build account mappings from recurring's mappings
        Map<UUID, UUID> accountMappings = new HashMap<>();
        for (RecurringTransactionAccountMapping mapping : recurring.getAccountMappings()) {
            accountMappings.put(mapping.getTemplateLine().getId(), mapping.getAccount().getId());
        }

        // Create the transaction
        Transaction transaction = transactionService.createFromRecurring(
                recurring.getJournalTemplate().getId(),
                scheduledDate,
                recurring.getAmount(),
                recurring.getDescription(),
                "SYSTEM",
                accountMappings);

        // Auto-post if enabled
        if (recurring.isAutoPost()) {
            transactionService.post(transaction.getId(), "SYSTEM");
        }

        // Log success
        RecurringTransactionLog logEntry = new RecurringTransactionLog();
        logEntry.setScheduledDate(recurring.getNextRunDate());
        logEntry.setExecutedAt(LocalDateTime.now());
        logEntry.setTransaction(transaction);
        logEntry.setStatus(RecurringLogStatus.SUCCESS);
        recurring.addLog(logEntry);

        // Update recurring state
        recurring.setLastRunDate(today);
        recurring.setTotalRuns(recurring.getTotalRuns() + 1);

        // Calculate next run date
        LocalDate nextDate = calculateNextRunDate(
                recurring.getFrequency(), recurring.getDayOfMonth(), recurring.getDayOfWeek(),
                scheduledDate.plusDays(1), recurring.isSkipWeekends());

        // Check if completed
        boolean completed = false;
        if (recurring.getMaxOccurrences() != null && recurring.getTotalRuns() >= recurring.getMaxOccurrences()) {
            completed = true;
        }
        if (nextDate != null && recurring.getEndDate() != null && nextDate.isAfter(recurring.getEndDate())) {
            completed = true;
        }

        if (completed) {
            recurring.setStatus(RecurringStatus.COMPLETED);
            recurring.setNextRunDate(null);
        } else {
            recurring.setNextRunDate(nextDate);
        }

        recurringTransactionRepository.save(recurring);
    }

    /**
     * Calculate the next run date based on frequency and configuration.
     */
    public static LocalDate calculateNextRunDate(RecurringFrequency frequency, Integer dayOfMonth,
                                                  Integer dayOfWeek, LocalDate fromDate,
                                                  boolean skipWeekends) {
        if (fromDate == null) {
            return null;
        }

        LocalDate next = switch (frequency) {
            case DAILY -> fromDate;
            case WEEKLY -> {
                if (dayOfWeek != null) {
                    DayOfWeek target = DayOfWeek.of(dayOfWeek);
                    LocalDate d = fromDate;
                    while (d.getDayOfWeek() != target) {
                        d = d.plusDays(1);
                    }
                    yield d;
                }
                yield fromDate;
            }
            case MONTHLY -> {
                if (dayOfMonth != null) {
                    LocalDate d = fromDate.withDayOfMonth(Math.min(dayOfMonth, fromDate.lengthOfMonth()));
                    if (d.isBefore(fromDate)) {
                        d = d.plusMonths(1).withDayOfMonth(Math.min(dayOfMonth, d.plusMonths(1).lengthOfMonth()));
                    }
                    yield d;
                }
                yield fromDate;
            }
            case QUARTERLY -> {
                if (dayOfMonth != null) {
                    LocalDate d = fromDate.withDayOfMonth(Math.min(dayOfMonth, fromDate.lengthOfMonth()));
                    if (d.isBefore(fromDate)) {
                        d = d.plusMonths(3);
                        d = d.withDayOfMonth(Math.min(dayOfMonth, d.lengthOfMonth()));
                    }
                    // Align to quarter boundary (Jan, Apr, Jul, Oct)
                    int monthInQuarter = (d.getMonthValue() - 1) % 3;
                    if (monthInQuarter != 0) {
                        d = d.plusMonths(3 - monthInQuarter);
                        d = d.withDayOfMonth(Math.min(dayOfMonth, d.lengthOfMonth()));
                    }
                    yield d;
                }
                yield fromDate.plusMonths(3);
            }
            case YEARLY -> {
                if (dayOfMonth != null) {
                    LocalDate d = fromDate.withDayOfMonth(Math.min(dayOfMonth, fromDate.lengthOfMonth()));
                    if (d.isBefore(fromDate)) {
                        d = d.plusYears(1).withDayOfMonth(Math.min(dayOfMonth, d.plusYears(1).lengthOfMonth()));
                    }
                    yield d;
                }
                yield fromDate.plusYears(1);
            }
        };

        if (skipWeekends && next != null) {
            next = advancePastWeekend(next);
        }

        return next;
    }

    private static LocalDate advancePastWeekend(LocalDate date) {
        while (isWeekend(date)) {
            date = date.plusDays(1);
        }
        return date;
    }

    private static boolean isWeekend(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
    }

    public List<RecurringTransactionLog> findLogsByRecurringId(UUID recurringId) {
        return logRepository.findByRecurringTransactionIdOrderByScheduledDateDesc(recurringId);
    }
}
