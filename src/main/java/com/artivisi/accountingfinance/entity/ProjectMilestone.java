package com.artivisi.accountingfinance.entity;

import com.artivisi.accountingfinance.enums.MilestoneStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "project_milestones",
        uniqueConstraints = @UniqueConstraint(columnNames = {"id_project", "sequence"}))
@Getter
@Setter
@NoArgsConstructor
public class ProjectMilestone extends TimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_project", nullable = false)
    private Project project;

    @Min(value = 1, message = "Urutan minimal 1")
    @Column(name = "sequence", nullable = false)
    private Integer sequence;

    @NotBlank(message = "Nama milestone wajib diisi")
    @Size(max = 255, message = "Nama milestone maksimal 255 karakter")
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Min(value = 0, message = "Bobot minimal 0")
    @Column(name = "weight_percent", nullable = false)
    private Integer weightPercent = 0;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "actual_date")
    private LocalDate actualDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MilestoneStatus status = MilestoneStatus.PENDING;

    public boolean isPending() {
        return status == MilestoneStatus.PENDING;
    }

    public boolean isInProgress() {
        return status == MilestoneStatus.IN_PROGRESS;
    }

    public boolean isCompleted() {
        return status == MilestoneStatus.COMPLETED;
    }

    public boolean isOverdue() {
        return targetDate != null &&
               status != MilestoneStatus.COMPLETED &&
               LocalDate.now().isAfter(targetDate);
    }
}
