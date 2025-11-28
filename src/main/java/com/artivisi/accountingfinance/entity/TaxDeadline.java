package com.artivisi.accountingfinance.entity;

import com.artivisi.accountingfinance.enums.TaxDeadlineType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.UUID;

@Entity
@Table(name = "tax_deadlines", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"deadline_type"})
})
@Getter
@Setter
@NoArgsConstructor
public class TaxDeadline {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotBlank(message = "Nama wajib diisi")
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @NotNull(message = "Tipe deadline wajib diisi")
    @Enumerated(EnumType.STRING)
    @Column(name = "deadline_type", nullable = false, length = 30, unique = true)
    private TaxDeadlineType deadlineType;

    @NotNull(message = "Hari jatuh tempo wajib diisi")
    @Min(value = 1, message = "Hari minimal 1")
    @Max(value = 31, message = "Hari maksimal 31")
    @Column(name = "due_day", nullable = false)
    private Integer dueDay;

    @Column(name = "use_last_day_of_month", nullable = false)
    private Boolean useLastDayOfMonth = false;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "reminder_days_before", nullable = false)
    private Integer reminderDaysBefore = 7;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDate getDueDateForPeriod(int year, int month) {
        YearMonth targetMonth = YearMonth.of(year, month).plusMonths(1);

        if (Boolean.TRUE.equals(useLastDayOfMonth)) {
            return targetMonth.atEndOfMonth();
        }

        int actualDueDay = Math.min(dueDay, targetMonth.lengthOfMonth());
        return targetMonth.atDay(actualDueDay);
    }

    public LocalDate getReminderDateForPeriod(int year, int month) {
        return getDueDateForPeriod(year, month).minusDays(reminderDaysBefore);
    }

    public boolean isOverdue(int year, int month, LocalDate today) {
        return today.isAfter(getDueDateForPeriod(year, month));
    }

    public boolean isDueSoon(int year, int month, LocalDate today) {
        LocalDate dueDate = getDueDateForPeriod(year, month);
        LocalDate reminderDate = getReminderDateForPeriod(year, month);
        return !today.isBefore(reminderDate) && !today.isAfter(dueDate);
    }

    public long getDaysUntilDue(int year, int month, LocalDate today) {
        return java.time.temporal.ChronoUnit.DAYS.between(today, getDueDateForPeriod(year, month));
    }
}
