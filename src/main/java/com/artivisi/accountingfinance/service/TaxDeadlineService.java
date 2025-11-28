package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.TaxDeadline;
import com.artivisi.accountingfinance.entity.TaxDeadlineCompletion;
import com.artivisi.accountingfinance.enums.TaxDeadlineType;
import com.artivisi.accountingfinance.repository.TaxDeadlineCompletionRepository;
import com.artivisi.accountingfinance.repository.TaxDeadlineRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaxDeadlineService {

    private final TaxDeadlineRepository taxDeadlineRepository;
    private final TaxDeadlineCompletionRepository taxDeadlineCompletionRepository;

    public record TaxDeadlineStatus(
            TaxDeadline deadline,
            int year,
            int month,
            LocalDate dueDate,
            TaxDeadlineCompletion completion,
            boolean isCompleted,
            boolean isOverdue,
            boolean isDueSoon,
            long daysUntilDue
    ) {}

    public TaxDeadline findById(UUID id) {
        return taxDeadlineRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Tax deadline not found with id: " + id));
    }

    public Optional<TaxDeadline> findByDeadlineType(TaxDeadlineType type) {
        return taxDeadlineRepository.findByDeadlineType(type);
    }

    public List<TaxDeadline> findAllActive() {
        return taxDeadlineRepository.findByActiveTrueOrderByDueDayAsc();
    }

    public List<TaxDeadline> findAll() {
        return taxDeadlineRepository.findAll();
    }

    @Transactional
    public TaxDeadline save(TaxDeadline deadline) {
        return taxDeadlineRepository.save(deadline);
    }

    @Transactional
    public void delete(UUID id) {
        taxDeadlineRepository.deleteById(id);
    }

    public List<TaxDeadlineStatus> getDeadlineStatusForPeriod(int year, int month) {
        LocalDate today = LocalDate.now();
        List<TaxDeadline> deadlines = findAllActive();
        List<TaxDeadlineCompletion> completions = taxDeadlineCompletionRepository
                .findByYearAndMonthWithDeadline(year, month);

        Map<UUID, TaxDeadlineCompletion> completionMap = completions.stream()
                .collect(Collectors.toMap(c -> c.getTaxDeadline().getId(), Function.identity()));

        return deadlines.stream()
                .map(deadline -> {
                    TaxDeadlineCompletion completion = completionMap.get(deadline.getId());
                    LocalDate dueDate = deadline.getDueDateForPeriod(year, month);
                    boolean isCompleted = completion != null;
                    boolean isOverdue = !isCompleted && deadline.isOverdue(year, month, today);
                    boolean isDueSoon = !isCompleted && !isOverdue && deadline.isDueSoon(year, month, today);
                    long daysUntilDue = deadline.getDaysUntilDue(year, month, today);

                    return new TaxDeadlineStatus(
                            deadline, year, month, dueDate, completion,
                            isCompleted, isOverdue, isDueSoon, daysUntilDue
                    );
                })
                .sorted(Comparator.comparing(TaxDeadlineStatus::dueDate))
                .toList();
    }

    public List<TaxDeadlineStatus> getUpcomingDeadlines() {
        LocalDate today = LocalDate.now();
        int currentYear = today.getYear();
        int currentMonth = today.getMonthValue();

        int taxPeriodYear = currentMonth == 1 ? currentYear - 1 : currentYear;
        int taxPeriodMonth = currentMonth == 1 ? 12 : currentMonth - 1;

        return getDeadlineStatusForPeriod(taxPeriodYear, taxPeriodMonth).stream()
                .filter(status -> !status.isCompleted())
                .toList();
    }

    public List<TaxDeadlineStatus> getOverdueDeadlines() {
        return getUpcomingDeadlines().stream()
                .filter(TaxDeadlineStatus::isOverdue)
                .toList();
    }

    public List<TaxDeadlineStatus> getDueSoonDeadlines() {
        return getUpcomingDeadlines().stream()
                .filter(TaxDeadlineStatus::isDueSoon)
                .toList();
    }

    @Transactional
    public TaxDeadlineCompletion markAsCompleted(UUID deadlineId, int year, int month,
                                                  LocalDate completedDate, String referenceNumber, String notes) {
        TaxDeadline deadline = findById(deadlineId);

        Optional<TaxDeadlineCompletion> existing = taxDeadlineCompletionRepository
                .findByTaxDeadlineAndYearAndMonth(deadline, year, month);

        if (existing.isPresent()) {
            throw new IllegalArgumentException("Tax deadline already completed for this period");
        }

        TaxDeadlineCompletion completion = new TaxDeadlineCompletion();
        completion.setTaxDeadline(deadline);
        completion.setYear(year);
        completion.setMonth(month);
        completion.setCompletedDate(completedDate);
        completion.setReferenceNumber(referenceNumber);
        completion.setNotes(notes);
        completion.setCompletedBy(getCurrentUsername());

        return taxDeadlineCompletionRepository.save(completion);
    }

    @Transactional
    public void removeCompletion(UUID completionId) {
        taxDeadlineCompletionRepository.deleteById(completionId);
    }

    public Optional<TaxDeadlineCompletion> findCompletion(UUID deadlineId, int year, int month) {
        return taxDeadlineCompletionRepository.findByDeadlineIdAndYearAndMonth(deadlineId, year, month);
    }

    public TaxDeadlineCompletion findCompletionById(UUID id) {
        return taxDeadlineCompletionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Tax deadline completion not found with id: " + id));
    }

    public record MonthlyChecklistSummary(
            int year,
            int month,
            int totalDeadlines,
            int completedCount,
            int overdueCount,
            int dueSoonCount,
            int pendingCount,
            List<TaxDeadlineStatus> deadlines
    ) {
        public int getCompletionPercentage() {
            return totalDeadlines > 0 ? (completedCount * 100) / totalDeadlines : 0;
        }

        public boolean isAllCompleted() {
            return completedCount == totalDeadlines;
        }
    }

    public MonthlyChecklistSummary getMonthlyChecklistSummary(int year, int month) {
        List<TaxDeadlineStatus> deadlines = getDeadlineStatusForPeriod(year, month);

        int totalDeadlines = deadlines.size();
        int completedCount = (int) deadlines.stream().filter(TaxDeadlineStatus::isCompleted).count();
        int overdueCount = (int) deadlines.stream().filter(TaxDeadlineStatus::isOverdue).count();
        int dueSoonCount = (int) deadlines.stream().filter(TaxDeadlineStatus::isDueSoon).count();
        int pendingCount = totalDeadlines - completedCount - overdueCount - dueSoonCount;

        return new MonthlyChecklistSummary(
                year, month, totalDeadlines, completedCount,
                overdueCount, dueSoonCount, pendingCount, deadlines
        );
    }

    public List<MonthlyChecklistSummary> getYearlyChecklistSummary(int year) {
        List<MonthlyChecklistSummary> summaries = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            summaries.add(getMonthlyChecklistSummary(year, month));
        }
        return summaries;
    }

    private String getCurrentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
