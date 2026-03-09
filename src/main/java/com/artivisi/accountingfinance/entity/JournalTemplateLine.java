package com.artivisi.accountingfinance.entity;

import com.artivisi.accountingfinance.enums.JournalPosition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "journal_template_lines")
@Getter
@Setter
@NoArgsConstructor
public class JournalTemplateLine extends TimestampedEntity {

    @JsonIgnore
    @NotNull(message = "Journal template is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_journal_template", nullable = false)
    private JournalTemplate journalTemplate;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_account", nullable = true)
    private ChartOfAccount account;

    @Size(max = 100, message = "Account hint must not exceed 100 characters")
    @Column(name = "account_hint", length = 100)
    private String accountHint;

    @NotNull(message = "Position is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "position", nullable = false, length = 10)
    private JournalPosition position;

    @NotBlank(message = "Formula is required")
    @Size(max = 255, message = "Formula must not exceed 255 characters")
    @Column(name = "formula", nullable = false, length = 255)
    private String formula = "amount";

    @Min(value = 1, message = "Line order must be at least 1")
    @Column(name = "line_order", nullable = false)
    private Integer lineOrder;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
}
