package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.entity.TaxDeadline;
import com.artivisi.accountingfinance.entity.TaxDeadlineCompletion;
import com.artivisi.accountingfinance.enums.TaxDeadlineType;
import com.artivisi.accountingfinance.repository.TaxDeadlineCompletionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Integration tests for TaxDeadlineService against the eight deadlines seeded by V800
 * (ids f0000000-...-01 to -08). Every test rolls back; completions use periods in 2067/2068.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "admin")
@DisplayName("TaxDeadlineService Integration Tests")
class TaxDeadlineServiceTest {

    private static final UUID PPH_21_PAYMENT_ID = UUID.fromString("f0000000-0000-0000-0000-000000000001");
    private static final UUID SPT_PPN_ID = UUID.fromString("f0000000-0000-0000-0000-000000000008");

    @Autowired private TaxDeadlineService taxDeadlineService;
    @Autowired private TaxDeadlineCompletionRepository completionRepository;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("Active deadlines are the eight seeded ones ordered by due day")
    void shouldListActiveDeadlinesByDueDay() {
        List<TaxDeadline> deadlines = taxDeadlineService.findAllActive();

        assertThat(deadlines).extracting(TaxDeadline::getDeadlineType).containsExactlyInAnyOrder(TaxDeadlineType.values());
        assertThat(deadlines).extracting(TaxDeadline::getDueDay).containsExactly(15, 15, 15, 15, 20, 20, 31, 31);
        assertThat(deadlines).filteredOn(TaxDeadline::getUseLastDayOfMonth)
                .extracting(TaxDeadline::getDeadlineType)
                .containsExactlyInAnyOrder(TaxDeadlineType.PPN_PAYMENT, TaxDeadlineType.SPT_PPN);
    }

    @Test
    @DisplayName("Lookup by type and by id return the seeded deadline; unknown id is rejected")
    void shouldFindDeadline() {
        TaxDeadline byType = taxDeadlineService.findByDeadlineType(TaxDeadlineType.PPH_21_PAYMENT)
                .orElseThrow(() -> new AssertionError("PPH_21_PAYMENT not seeded"));
        assertThat(byType.getId()).isEqualTo(PPH_21_PAYMENT_ID);
        assertThat(byType.getName()).isEqualTo("Setor PPh 21");
        assertThat(byType.getDueDay()).isEqualTo(15);
        assertThat(taxDeadlineService.findById(PPH_21_PAYMENT_ID).getDeadlineType()).isEqualTo(TaxDeadlineType.PPH_21_PAYMENT);

        UUID unknown = UUID.randomUUID();
        assertThatThrownBy(() -> taxDeadlineService.findById(unknown))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Tax deadline not found with id: " + unknown);
        assertThatThrownBy(() -> taxDeadlineService.findCompletionById(unknown))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Tax deadline completion not found with id: " + unknown);
    }

    @Test
    @DisplayName("Due dates fall on the 15th, 20th or last day of the following month, rolling over December and leap February")
    void shouldComputeDueDatesPerPeriod() {
        List<TaxDeadlineService.TaxDeadlineStatus> january = taxDeadlineService.getDeadlineStatusForPeriod(2067, 1);

        assertThat(january).extracting(s -> s.deadline().getDeadlineType(), TaxDeadlineService.TaxDeadlineStatus::dueDate)
                .containsExactlyInAnyOrder(
                        tuple(TaxDeadlineType.PPH_21_PAYMENT, LocalDate.of(2067, 2, 15)),
                        tuple(TaxDeadlineType.PPH_23_PAYMENT, LocalDate.of(2067, 2, 15)),
                        tuple(TaxDeadlineType.PPH_42_PAYMENT, LocalDate.of(2067, 2, 15)),
                        tuple(TaxDeadlineType.PPH_25_PAYMENT, LocalDate.of(2067, 2, 15)),
                        tuple(TaxDeadlineType.SPT_PPH_21, LocalDate.of(2067, 2, 20)),
                        tuple(TaxDeadlineType.SPT_PPH_23, LocalDate.of(2067, 2, 20)),
                        tuple(TaxDeadlineType.PPN_PAYMENT, LocalDate.of(2067, 2, 28)),
                        tuple(TaxDeadlineType.SPT_PPN, LocalDate.of(2067, 2, 28)));
        assertThat(january).extracting(TaxDeadlineService.TaxDeadlineStatus::dueDate).isSorted();
        LocalDate today = LocalDate.now();
        assertThat(january).allSatisfy(s -> {
            assertThat(s.year()).isEqualTo(2067);
            assertThat(s.month()).isEqualTo(1);
            assertThat(s.isCompleted()).isFalse();
            assertThat(s.isOverdue()).isFalse();
            assertThat(s.isDueSoon()).isFalse();
            assertThat(s.daysUntilDue()).isEqualTo(ChronoUnit.DAYS.between(today, s.dueDate()));
        });

        assertThat(dueDate(2067, 12, TaxDeadlineType.PPH_25_PAYMENT)).isEqualTo(LocalDate.of(2068, 1, 15));
        assertThat(dueDate(2067, 12, TaxDeadlineType.SPT_PPN)).isEqualTo(LocalDate.of(2068, 1, 31));
        assertThat(dueDate(2068, 1, TaxDeadlineType.PPN_PAYMENT)).isEqualTo(LocalDate.of(2068, 2, 29));
        assertThat(dueDate(2067, 3, TaxDeadlineType.SPT_PPH_21)).isEqualTo(LocalDate.of(2067, 4, 20));
    }

    @Test
    @DisplayName("A deadline is due soon from 7 days before the due date through the due date, overdue the day after")
    void shouldClassifyDueSoonAndOverdue() {
        TaxDeadline pph21 = taxDeadlineService.findById(PPH_21_PAYMENT_ID);

        assertThat(pph21.isDueSoon(2067, 1, LocalDate.of(2067, 2, 7))).isFalse();
        assertThat(pph21.isDueSoon(2067, 1, LocalDate.of(2067, 2, 8))).isTrue();
        assertThat(pph21.isDueSoon(2067, 1, LocalDate.of(2067, 2, 15))).isTrue();
        assertThat(pph21.isOverdue(2067, 1, LocalDate.of(2067, 2, 15))).isFalse();
        assertThat(pph21.isOverdue(2067, 1, LocalDate.of(2067, 2, 16))).isTrue();
        assertThat(pph21.isDueSoon(2067, 1, LocalDate.of(2067, 2, 16))).isFalse();
        assertThat(pph21.getDaysUntilDue(2067, 1, LocalDate.of(2067, 2, 20))).isEqualTo(-5);
    }

    @Test
    @DisplayName("Past periods without completions are overdue; a completion clears the overdue flag")
    void shouldMarkPastPeriodOverdueUntilCompleted() {
        TaxDeadlineService.MonthlyChecklistSummary before = taxDeadlineService.getMonthlyChecklistSummary(2021, 7);
        assertThat(before.totalDeadlines()).isEqualTo(8);
        assertThat(before.overdueCount()).isEqualTo(8);
        assertThat(before.completedCount()).isZero();
        assertThat(before.pendingCount()).isZero();

        TaxDeadlineCompletion completion = taxDeadlineService.markAsCompleted(
                PPH_21_PAYMENT_ID, 2021, 7, LocalDate.of(2021, 8, 20), "NTPN-LATE", "Terlambat bayar");

        TaxDeadlineService.MonthlyChecklistSummary after = taxDeadlineService.getMonthlyChecklistSummary(2021, 7);
        assertThat(after.completedCount()).isEqualTo(1);
        assertThat(after.overdueCount()).isEqualTo(7);
        assertThat(after.getCompletionPercentage()).isEqualTo(12);
        assertThat(after.isAllCompleted()).isFalse();
        TaxDeadlineService.TaxDeadlineStatus status = after.deadlines().stream()
                .filter(s -> s.deadline().getId().equals(PPH_21_PAYMENT_ID))
                .findFirst().orElseThrow();
        assertThat(status.isCompleted()).isTrue();
        assertThat(status.isOverdue()).isFalse();
        assertThat(status.completion().getId()).isEqualTo(completion.getId());
    }

    @Test
    @DisplayName("Marking complete persists date, reference, notes and the current user for that period only")
    void shouldMarkDeadlineAsCompleted() {
        TaxDeadlineCompletion completion = taxDeadlineService.markAsCompleted(
                SPT_PPN_ID, 2067, 2, LocalDate.of(2067, 3, 25), "BPE-2067-02", "Lapor via Coretax");
        entityManager.flush();
        entityManager.clear();

        TaxDeadlineCompletion stored = taxDeadlineService.findCompletion(SPT_PPN_ID, 2067, 2)
                .orElseThrow(() -> new AssertionError("Completion not stored"));
        assertThat(stored.getId()).isEqualTo(completion.getId());
        assertThat(stored.getTaxDeadline().getDeadlineType()).isEqualTo(TaxDeadlineType.SPT_PPN);
        assertThat(stored.getCompletedDate()).isEqualTo(LocalDate.of(2067, 3, 25));
        assertThat(stored.getReferenceNumber()).isEqualTo("BPE-2067-02");
        assertThat(stored.getNotes()).isEqualTo("Lapor via Coretax");
        assertThat(stored.getCompletedBy()).isEqualTo("admin");
        assertThat(taxDeadlineService.findCompletionById(completion.getId()).getYear()).isEqualTo(2067);
        assertThat(taxDeadlineService.findCompletion(SPT_PPN_ID, 2067, 3)).isEmpty();
        assertThat(taxDeadlineService.findCompletion(PPH_21_PAYMENT_ID, 2067, 2)).isEmpty();
    }

    @Test
    @DisplayName("A second completion of the same deadline and period is rejected and the first one kept")
    void shouldRejectDuplicateCompletion() {
        taxDeadlineService.markAsCompleted(PPH_21_PAYMENT_ID, 2067, 4, LocalDate.of(2067, 5, 10), "REF-002", null);

        assertThatThrownBy(() -> taxDeadlineService.markAsCompleted(
                PPH_21_PAYMENT_ID, 2067, 4, LocalDate.of(2067, 5, 12), "REF-003", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Kewajiban pajak sudah ditandai selesai untuk periode ini");

        assertThat(completionRepository.findByYearAndMonth(2067, 4))
                .singleElement()
                .extracting(TaxDeadlineCompletion::getReferenceNumber)
                .isEqualTo("REF-002");
    }

    @Test
    @DisplayName("Removing a completion makes the deadline pending again")
    void shouldRemoveCompletion() {
        TaxDeadlineCompletion completion = taxDeadlineService.markAsCompleted(
                PPH_21_PAYMENT_ID, 2067, 5, LocalDate.of(2067, 6, 10), null, null);
        assertThat(taxDeadlineService.getMonthlyChecklistSummary(2067, 5).completedCount()).isEqualTo(1);

        taxDeadlineService.removeCompletion(completion.getId());
        entityManager.flush();

        assertThat(taxDeadlineService.findCompletion(PPH_21_PAYMENT_ID, 2067, 5)).isEmpty();
        TaxDeadlineService.MonthlyChecklistSummary summary = taxDeadlineService.getMonthlyChecklistSummary(2067, 5);
        assertThat(summary.completedCount()).isZero();
        assertThat(summary.pendingCount()).isEqualTo(8);
    }

    @Test
    @DisplayName("Completing all eight deadlines reports 100% and all-completed")
    void shouldReportAllCompleted() {
        for (TaxDeadline deadline : taxDeadlineService.findAllActive()) {
            taxDeadlineService.markAsCompleted(deadline.getId(), 2067, 6, LocalDate.of(2067, 7, 10), null, null);
        }

        TaxDeadlineService.MonthlyChecklistSummary summary = taxDeadlineService.getMonthlyChecklistSummary(2067, 6);
        assertThat(summary.completedCount()).isEqualTo(8);
        assertThat(summary.getCompletionPercentage()).isEqualTo(100);
        assertThat(summary.isAllCompleted()).isTrue();
        assertThat(summary.pendingCount()).isZero();
    }

    @Test
    @DisplayName("Yearly summary has twelve months and counts a completion only in its own month")
    void shouldSummarizeYear() {
        taxDeadlineService.markAsCompleted(SPT_PPN_ID, 2067, 9, LocalDate.of(2067, 10, 31), null, null);

        List<TaxDeadlineService.MonthlyChecklistSummary> year = taxDeadlineService.getYearlyChecklistSummary(2067);

        assertThat(year).extracting(TaxDeadlineService.MonthlyChecklistSummary::month)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
        assertThat(year).allSatisfy(m -> {
            assertThat(m.year()).isEqualTo(2067);
            assertThat(m.totalDeadlines()).isEqualTo(8);
        });
        assertThat(year.get(8).completedCount()).isEqualTo(1);
        assertThat(year).filteredOn(m -> m.month() != 9).allSatisfy(m -> assertThat(m.completedCount()).isZero());
    }

    @Test
    @DisplayName("Upcoming deadlines are the open ones of the previous month; overdue and due-soon are subsets by date")
    void shouldListUpcomingForCurrentTaxPeriod() {
        YearMonth period = YearMonth.now().minusMonths(1);
        taxDeadlineService.markAsCompleted(PPH_21_PAYMENT_ID, period.getYear(), period.getMonthValue(),
                LocalDate.now(), null, null);
        LocalDate today = LocalDate.now();

        List<TaxDeadlineService.TaxDeadlineStatus> upcoming = taxDeadlineService.getUpcomingDeadlines();

        assertThat(upcoming).hasSize(7);
        assertThat(upcoming).extracting(s -> s.deadline().getId()).doesNotContain(PPH_21_PAYMENT_ID);
        assertThat(upcoming).allSatisfy(s -> {
            assertThat(s.year()).isEqualTo(period.getYear());
            assertThat(s.month()).isEqualTo(period.getMonthValue());
        });
        assertThat(taxDeadlineService.getOverdueDeadlines()).extracting(TaxDeadlineService.TaxDeadlineStatus::dueDate)
                .containsExactlyElementsOf(upcoming.stream().map(TaxDeadlineService.TaxDeadlineStatus::dueDate)
                        .filter(today::isAfter).toList());
        assertThat(taxDeadlineService.getDueSoonDeadlines()).extracting(TaxDeadlineService.TaxDeadlineStatus::dueDate)
                .containsExactlyElementsOf(upcoming.stream().map(TaxDeadlineService.TaxDeadlineStatus::dueDate)
                        .filter(due -> !today.isAfter(due) && !today.isBefore(due.minusDays(7))).toList());
    }

    @Test
    @DisplayName("Saving a deadline persists its changed reminder window, which drives due-soon")
    void shouldSaveDeadline() {
        TaxDeadline sptPpn = taxDeadlineService.findById(SPT_PPN_ID);
        sptPpn.setReminderDaysBefore(3);
        taxDeadlineService.save(sptPpn);
        entityManager.flush();
        entityManager.clear();

        TaxDeadline stored = taxDeadlineService.findById(SPT_PPN_ID);
        assertThat(stored.getReminderDaysBefore()).isEqualTo(3);
        assertThat(stored.getReminderDateForPeriod(2067, 1)).isEqualTo(LocalDate.of(2067, 2, 25));
        assertThat(stored.isDueSoon(2067, 1, LocalDate.of(2067, 2, 24))).isFalse();
        assertThat(stored.isDueSoon(2067, 1, LocalDate.of(2067, 2, 25))).isTrue();
    }

    private LocalDate dueDate(int year, int month, TaxDeadlineType type) {
        return taxDeadlineService.getDeadlineStatusForPeriod(year, month).stream()
                .filter(s -> s.deadline().getDeadlineType() == type)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing deadline " + type))
                .dueDate();
    }
}
