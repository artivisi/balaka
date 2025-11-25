package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.AmortizationEntry;
import com.artivisi.accountingfinance.entity.AmortizationSchedule;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.enums.AmortizationEntryStatus;
import com.artivisi.accountingfinance.enums.AmortizationFrequency;
import com.artivisi.accountingfinance.enums.ScheduleStatus;
import com.artivisi.accountingfinance.enums.ScheduleType;
import com.artivisi.accountingfinance.repository.AmortizationEntryRepository;
import com.artivisi.accountingfinance.repository.AmortizationScheduleRepository;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AmortizationScheduleService {

    private final AmortizationScheduleRepository scheduleRepository;
    private final AmortizationEntryRepository entryRepository;
    private final ChartOfAccountRepository chartOfAccountRepository;

    public AmortizationSchedule findById(UUID id) {
        return scheduleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Amortization schedule not found with id: " + id));
    }

    public AmortizationSchedule findByCode(String code) {
        return scheduleRepository.findByCode(code)
                .orElseThrow(() -> new EntityNotFoundException("Amortization schedule not found with code: " + code));
    }

    public Page<AmortizationSchedule> findAll(Pageable pageable) {
        return scheduleRepository.findAllByOrderByCodeAsc(pageable);
    }

    public Page<AmortizationSchedule> findByFilters(ScheduleStatus status, ScheduleType scheduleType,
                                                     String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return scheduleRepository.findByFiltersAndSearch(status, scheduleType, search, pageable);
        }
        return scheduleRepository.findByFilters(status, scheduleType, pageable);
    }

    public List<AmortizationSchedule> findByStatus(ScheduleStatus status) {
        return scheduleRepository.findByStatus(status);
    }

    public List<AmortizationSchedule> findActiveSchedulesForDate(LocalDate date) {
        return scheduleRepository.findActiveSchedulesForDate(date);
    }

    @Transactional
    public AmortizationSchedule create(AmortizationSchedule schedule) {
        validateSchedule(schedule);

        if (scheduleRepository.existsByCode(schedule.getCode())) {
            throw new IllegalArgumentException("Schedule code already exists: " + schedule.getCode());
        }

        // Calculate periods and amounts
        int totalPeriods = calculateTotalPeriods(
                schedule.getStartDate(),
                schedule.getEndDate(),
                schedule.getFrequency());

        BigDecimal periodAmount = schedule.getTotalAmount()
                .divide(BigDecimal.valueOf(totalPeriods), 2, RoundingMode.HALF_UP);

        schedule.setTotalPeriods(totalPeriods);
        schedule.setPeriodAmount(periodAmount);
        schedule.setRemainingAmount(schedule.getTotalAmount());
        schedule.setAmortizedAmount(BigDecimal.ZERO);
        schedule.setCompletedPeriods(0);
        schedule.setStatus(ScheduleStatus.ACTIVE);

        AmortizationSchedule savedSchedule = scheduleRepository.save(schedule);

        // Generate all entries
        generateEntries(savedSchedule);

        return savedSchedule;
    }

    @Transactional
    public AmortizationSchedule update(UUID id, AmortizationSchedule updatedSchedule) {
        AmortizationSchedule existing = findById(id);

        if (!existing.isActive()) {
            throw new IllegalStateException("Cannot update schedule with status: " + existing.getStatus());
        }

        // Check if any entries are posted
        long postedCount = entryRepository.countPostedEntriesByScheduleId(id);
        if (postedCount > 0) {
            throw new IllegalStateException("Cannot update schedule with posted entries");
        }

        // Update allowed fields
        existing.setName(updatedSchedule.getName());
        existing.setDescription(updatedSchedule.getDescription());
        existing.setAutoPost(updatedSchedule.getAutoPost());
        existing.setPostDay(updatedSchedule.getPostDay());

        return scheduleRepository.save(existing);
    }

    @Transactional
    public void cancel(UUID id) {
        AmortizationSchedule schedule = findById(id);

        if (!schedule.isActive()) {
            throw new IllegalStateException("Cannot cancel schedule with status: " + schedule.getStatus());
        }

        schedule.setStatus(ScheduleStatus.CANCELLED);
        scheduleRepository.save(schedule);
    }

    @Transactional
    public void delete(UUID id) {
        AmortizationSchedule schedule = findById(id);

        // Check if any entries are posted
        long postedCount = entryRepository.countPostedEntriesByScheduleId(id);
        if (postedCount > 0) {
            throw new IllegalStateException("Cannot delete schedule with posted entries");
        }

        entryRepository.deleteByScheduleId(id);
        scheduleRepository.delete(schedule);
    }

    @Transactional
    public void updateScheduleCounters(UUID scheduleId) {
        AmortizationSchedule schedule = findById(scheduleId);

        long completedPeriods = entryRepository.countPostedEntriesByScheduleId(scheduleId);
        BigDecimal amortizedAmount = entryRepository.sumPostedAmountByScheduleId(scheduleId);

        schedule.setCompletedPeriods((int) completedPeriods);
        schedule.setAmortizedAmount(amortizedAmount);
        schedule.setRemainingAmount(schedule.getTotalAmount().subtract(amortizedAmount));

        // Check if completed
        if (completedPeriods >= schedule.getTotalPeriods()) {
            schedule.setStatus(ScheduleStatus.COMPLETED);
        }

        scheduleRepository.save(schedule);
    }

    public int calculateTotalPeriods(LocalDate startDate, LocalDate endDate, AmortizationFrequency frequency) {
        long totalMonths = ChronoUnit.MONTHS.between(startDate, endDate) + 1;
        return (int) Math.ceil((double) totalMonths / frequency.getMonthsPerPeriod());
    }

    private void generateEntries(AmortizationSchedule schedule) {
        List<AmortizationEntry> entries = new ArrayList<>();
        LocalDate periodStart = schedule.getStartDate();
        BigDecimal totalAllocated = BigDecimal.ZERO;

        for (int i = 1; i <= schedule.getTotalPeriods(); i++) {
            AmortizationEntry entry = new AmortizationEntry();
            entry.setSchedule(schedule);
            entry.setPeriodNumber(i);
            entry.setPeriodStart(periodStart);

            // Calculate period end
            LocalDate periodEnd;
            if (schedule.getFrequency() == AmortizationFrequency.MONTHLY) {
                periodEnd = periodStart.plusMonths(1).minusDays(1);
            } else {
                periodEnd = periodStart.plusMonths(3).minusDays(1);
            }

            // Ensure last period doesn't exceed schedule end date
            if (periodEnd.isAfter(schedule.getEndDate())) {
                periodEnd = schedule.getEndDate();
            }
            entry.setPeriodEnd(periodEnd);

            // Calculate amount (last period gets the remainder for rounding)
            BigDecimal amount;
            if (i == schedule.getTotalPeriods()) {
                amount = schedule.getTotalAmount().subtract(totalAllocated);
            } else {
                amount = schedule.getPeriodAmount();
            }
            entry.setAmount(amount);
            totalAllocated = totalAllocated.add(amount);

            entry.setStatus(AmortizationEntryStatus.PENDING);
            entries.add(entry);

            // Move to next period
            periodStart = periodEnd.plusDays(1);
        }

        entryRepository.saveAll(entries);
    }

    private void validateSchedule(AmortizationSchedule schedule) {
        if (schedule.getStartDate() == null) {
            throw new IllegalArgumentException("Start date is required");
        }
        if (schedule.getEndDate() == null) {
            throw new IllegalArgumentException("End date is required");
        }
        if (schedule.getStartDate().isAfter(schedule.getEndDate())) {
            throw new IllegalArgumentException("Start date must be before end date");
        }
        if (schedule.getTotalAmount() == null || schedule.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Total amount must be greater than zero");
        }
        if (schedule.getSourceAccount() == null) {
            throw new IllegalArgumentException("Source account is required");
        }
        if (schedule.getTargetAccount() == null) {
            throw new IllegalArgumentException("Target account is required");
        }
        if (schedule.getSourceAccount().getId().equals(schedule.getTargetAccount().getId())) {
            throw new IllegalArgumentException("Source and target accounts must be different");
        }
    }

    public long countActiveSchedules() {
        return scheduleRepository.countActiveSchedules();
    }

    public long countByStatus(ScheduleStatus status) {
        return scheduleRepository.countByStatus(status);
    }
}
