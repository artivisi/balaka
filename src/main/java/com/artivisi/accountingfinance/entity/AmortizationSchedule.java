package com.artivisi.accountingfinance.entity;

import com.artivisi.accountingfinance.enums.AmortizationFrequency;
import com.artivisi.accountingfinance.enums.ScheduleStatus;
import com.artivisi.accountingfinance.enums.ScheduleType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "amortization_schedules")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
public class AmortizationSchedule extends BaseEntity {

    @NotBlank(message = "Code is required")
    @Size(max = 50, message = "Code must not exceed 50 characters")
    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @NotNull(message = "Schedule type is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type", nullable = false, length = 50)
    private ScheduleType scheduleType;

    @JsonIgnore
    @NotNull(message = "Source account is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_source_account", nullable = false)
    private ChartOfAccount sourceAccount;

    @JsonIgnore
    @NotNull(message = "Target account is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_target_account", nullable = false)
    private ChartOfAccount targetAccount;

    @NotNull(message = "Total amount is required")
    @DecimalMin(value = "0.01", message = "Total amount must be greater than zero")
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @NotNull(message = "Period amount is required")
    @DecimalMin(value = "0.01", message = "Period amount must be greater than zero")
    @Column(name = "period_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal periodAmount;

    @NotNull(message = "Start date is required")
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @NotNull(message = "Frequency is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 20)
    private AmortizationFrequency frequency = AmortizationFrequency.MONTHLY;

    @NotNull(message = "Total periods is required")
    @Min(value = 1, message = "Total periods must be at least 1")
    @Column(name = "total_periods", nullable = false)
    private Integer totalPeriods;

    @NotNull
    @Min(value = 0)
    @Column(name = "completed_periods", nullable = false)
    private Integer completedPeriods = 0;

    @NotNull
    @DecimalMin(value = "0.00")
    @Column(name = "amortized_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amortizedAmount = BigDecimal.ZERO;

    @NotNull
    @DecimalMin(value = "0.00")
    @Column(name = "remaining_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal remainingAmount;

    @Column(name = "auto_post", nullable = false)
    private Boolean autoPost = false;

    @Min(value = 1, message = "Post day must be between 1 and 28")
    @Max(value = 28, message = "Post day must be between 1 and 28")
    @Column(name = "post_day")
    private Integer postDay = 1;

    @NotNull(message = "Status is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ScheduleStatus status = ScheduleStatus.ACTIVE;

    @JsonIgnore
    @OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("periodNumber ASC")
    private List<AmortizationEntry> entries = new ArrayList<>();

    public boolean isActive() {
        return status == ScheduleStatus.ACTIVE;
    }

    public boolean isCompleted() {
        return status == ScheduleStatus.COMPLETED;
    }

    public boolean isCancelled() {
        return status == ScheduleStatus.CANCELLED;
    }

    public BigDecimal getProgressPercentage() {
        if (totalPeriods == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(completedPeriods)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalPeriods), 2, java.math.RoundingMode.HALF_UP);
    }
}
