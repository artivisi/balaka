package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.AmortizationEntry;
import com.artivisi.accountingfinance.entity.AmortizationSchedule;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.enums.AmortizationEntryStatus;
import com.artivisi.accountingfinance.enums.ScheduleType;
import com.artivisi.accountingfinance.repository.AmortizationEntryRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AmortizationEntryService {

    private final AmortizationEntryRepository entryRepository;
    private final AmortizationScheduleService scheduleService;
    private final JournalEntryService journalEntryService;

    public AmortizationEntry findById(UUID id) {
        return entryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Amortization entry not found with id: " + id));
    }

    public List<AmortizationEntry> findByScheduleId(UUID scheduleId) {
        return entryRepository.findByScheduleIdOrderByPeriodNumberAsc(scheduleId);
    }

    public List<AmortizationEntry> findPendingByScheduleId(UUID scheduleId) {
        return entryRepository.findByScheduleIdAndStatus(scheduleId, AmortizationEntryStatus.PENDING);
    }

    public List<AmortizationEntry> findPendingEntriesDueByDate(LocalDate date) {
        return entryRepository.findPendingEntriesDueByDate(date);
    }

    public List<AmortizationEntry> findPendingAutoPostEntriesDueByDate(LocalDate date) {
        return entryRepository.findPendingAutoPostEntriesDueByDate(date);
    }

    @Transactional
    public AmortizationEntry postEntry(UUID entryId) {
        AmortizationEntry entry = findById(entryId);

        if (!entry.isPending()) {
            throw new IllegalStateException("Cannot post entry with status: " + entry.getStatus());
        }

        AmortizationSchedule schedule = entry.getSchedule();
        if (!schedule.isActive()) {
            throw new IllegalStateException("Cannot post entry for schedule with status: " + schedule.getStatus());
        }

        // Create and post journal entry
        List<JournalEntry> journalEntries = createJournalEntries(entry);
        List<JournalEntry> savedEntries = journalEntryService.create(journalEntries);
        journalEntryService.post(savedEntries.get(0).getJournalNumber());

        // Update amortization entry
        entry.setJournalEntryId(savedEntries.get(0).getId());
        entry.setStatus(AmortizationEntryStatus.POSTED);
        entry.setPostedAt(LocalDateTime.now());
        entry.setGeneratedAt(LocalDateTime.now());

        AmortizationEntry savedEntry = entryRepository.save(entry);

        // Update schedule counters
        scheduleService.updateScheduleCounters(schedule.getId());

        return savedEntry;
    }

    @Transactional
    public AmortizationEntry skipEntry(UUID entryId) {
        AmortizationEntry entry = findById(entryId);

        if (!entry.isPending()) {
            throw new IllegalStateException("Cannot skip entry with status: " + entry.getStatus());
        }

        entry.setStatus(AmortizationEntryStatus.SKIPPED);
        return entryRepository.save(entry);
    }

    @Transactional
    public List<AmortizationEntry> postAllPending(UUID scheduleId) {
        List<AmortizationEntry> pendingEntries = findPendingByScheduleId(scheduleId);

        for (AmortizationEntry entry : pendingEntries) {
            postEntry(entry.getId());
        }

        return findByScheduleId(scheduleId);
    }

    private List<JournalEntry> createJournalEntries(AmortizationEntry entry) {
        AmortizationSchedule schedule = entry.getSchedule();
        ScheduleType type = schedule.getScheduleType();

        String description = String.format("%s - %s (%s)",
                getTypeDescription(type),
                schedule.getName(),
                entry.getPeriodLabel());

        JournalEntry debitEntry = new JournalEntry();
        debitEntry.setJournalDate(entry.getPeriodEnd());
        debitEntry.setDescription(description);
        debitEntry.setReferenceNumber(schedule.getCode() + "-" + entry.getPeriodNumber());

        JournalEntry creditEntry = new JournalEntry();
        creditEntry.setJournalDate(entry.getPeriodEnd());
        creditEntry.setDescription(description);
        creditEntry.setReferenceNumber(schedule.getCode() + "-" + entry.getPeriodNumber());

        // Set accounts and amounts based on schedule type
        switch (type) {
            case PREPAID_EXPENSE:
                // Debit: Expense (target), Credit: Prepaid Asset (source)
                debitEntry.setAccount(schedule.getTargetAccount());
                debitEntry.setDebitAmount(entry.getAmount());
                debitEntry.setCreditAmount(BigDecimal.ZERO);

                creditEntry.setAccount(schedule.getSourceAccount());
                creditEntry.setDebitAmount(BigDecimal.ZERO);
                creditEntry.setCreditAmount(entry.getAmount());
                break;

            case UNEARNED_REVENUE:
                // Debit: Unearned Revenue (source), Credit: Revenue (target)
                debitEntry.setAccount(schedule.getSourceAccount());
                debitEntry.setDebitAmount(entry.getAmount());
                debitEntry.setCreditAmount(BigDecimal.ZERO);

                creditEntry.setAccount(schedule.getTargetAccount());
                creditEntry.setDebitAmount(BigDecimal.ZERO);
                creditEntry.setCreditAmount(entry.getAmount());
                break;

            case INTANGIBLE_ASSET:
                // Debit: Amortization Expense (target), Credit: Accumulated Amortization (source)
                debitEntry.setAccount(schedule.getTargetAccount());
                debitEntry.setDebitAmount(entry.getAmount());
                debitEntry.setCreditAmount(BigDecimal.ZERO);

                creditEntry.setAccount(schedule.getSourceAccount());
                creditEntry.setDebitAmount(BigDecimal.ZERO);
                creditEntry.setCreditAmount(entry.getAmount());
                break;

            case ACCRUED_REVENUE:
                // Debit: Accrued Revenue Receivable (source), Credit: Revenue (target)
                debitEntry.setAccount(schedule.getSourceAccount());
                debitEntry.setDebitAmount(entry.getAmount());
                debitEntry.setCreditAmount(BigDecimal.ZERO);

                creditEntry.setAccount(schedule.getTargetAccount());
                creditEntry.setDebitAmount(BigDecimal.ZERO);
                creditEntry.setCreditAmount(entry.getAmount());
                break;
        }

        return List.of(debitEntry, creditEntry);
    }

    private String getTypeDescription(ScheduleType type) {
        return switch (type) {
            case PREPAID_EXPENSE -> "Amortisasi Beban Dibayar Dimuka";
            case UNEARNED_REVENUE -> "Pengakuan Pendapatan Diterima Dimuka";
            case INTANGIBLE_ASSET -> "Amortisasi Aset Tak Berwujud";
            case ACCRUED_REVENUE -> "Pengakuan Pendapatan Akrual";
        };
    }

    public long countPendingEntries() {
        return entryRepository.countPendingEntries();
    }
}
