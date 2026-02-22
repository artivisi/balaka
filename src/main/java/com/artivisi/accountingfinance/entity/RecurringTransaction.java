package com.artivisi.accountingfinance.entity;

import com.artivisi.accountingfinance.enums.RecurringFrequency;
import com.artivisi.accountingfinance.enums.RecurringStatus;
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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "recurring_transactions")
@Getter
@Setter
@NoArgsConstructor
@SQLRestriction("deleted_at IS NULL")
public class RecurringTransaction extends BaseEntity {

    @NotBlank(message = "Nama transaksi berulang harus diisi")
    @Size(max = 255, message = "Nama tidak boleh lebih dari 255 karakter")
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @NotNull(message = "Template jurnal harus dipilih")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_journal_template", nullable = false)
    private JournalTemplate journalTemplate;

    @NotNull(message = "Jumlah harus diisi")
    @Min(value = 0, message = "Jumlah tidak boleh negatif")
    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @NotBlank(message = "Deskripsi harus diisi")
    @Size(max = 500, message = "Deskripsi tidak boleh lebih dari 500 karakter")
    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @NotNull(message = "Frekuensi harus dipilih")
    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 20)
    private RecurringFrequency frequency;

    @Min(value = 1, message = "Tanggal harus antara 1-28")
    @Max(value = 28, message = "Tanggal harus antara 1-28")
    @Column(name = "day_of_month")
    private Integer dayOfMonth;

    @Min(value = 1, message = "Hari harus antara 1-7")
    @Max(value = 7, message = "Hari harus antara 1-7")
    @Column(name = "day_of_week")
    private Integer dayOfWeek;

    @NotNull(message = "Tanggal mulai harus diisi")
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "next_run_date")
    private LocalDate nextRunDate;

    @Column(name = "last_run_date")
    private LocalDate lastRunDate;

    @NotNull(message = "Status harus diisi")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RecurringStatus status = RecurringStatus.ACTIVE;

    @Column(name = "skip_weekends", nullable = false)
    private boolean skipWeekends;

    @Column(name = "auto_post", nullable = false)
    private boolean autoPost = true;

    @Column(name = "total_runs", nullable = false)
    private int totalRuns;

    @Min(value = 1, message = "Jumlah maksimum harus minimal 1")
    @Column(name = "max_occurrences")
    private Integer maxOccurrences;

    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @OneToMany(mappedBy = "recurringTransaction", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<RecurringTransactionAccountMapping> accountMappings = new ArrayList<>();

    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @OneToMany(mappedBy = "recurringTransaction", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("scheduledDate DESC")
    private List<RecurringTransactionLog> logs = new ArrayList<>();

    public List<RecurringTransactionAccountMapping> getAccountMappings() {
        return Collections.unmodifiableList(accountMappings);
    }

    public List<RecurringTransactionLog> getLogs() {
        return Collections.unmodifiableList(logs);
    }

    public void addAccountMapping(RecurringTransactionAccountMapping mapping) {
        accountMappings.add(mapping);
        mapping.setRecurringTransaction(this);
    }

    public void clearAccountMappings() {
        for (RecurringTransactionAccountMapping mapping : new ArrayList<>(accountMappings)) {
            mapping.setRecurringTransaction(null);
        }
        accountMappings.clear();
    }

    public void addLog(RecurringTransactionLog log) {
        logs.add(log);
        log.setRecurringTransaction(this);
    }

    public boolean isActive() {
        return status == RecurringStatus.ACTIVE;
    }

    public boolean isPaused() {
        return status == RecurringStatus.PAUSED;
    }

    public boolean isCompleted() {
        return status == RecurringStatus.COMPLETED;
    }
}
