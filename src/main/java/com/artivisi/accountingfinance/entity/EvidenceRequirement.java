package com.artivisi.accountingfinance.entity;

import com.artivisi.accountingfinance.enums.EvidenceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Evidence a transaction must carry. Exactly one target: an account, which also covers its sub-accounts, or a
 * template, which covers every version of that template. {@code minAmount} null means every amount.
 */
@Entity
@Table(name = "evidence_requirements")
@Getter
@Setter
@NoArgsConstructor
public class EvidenceRequirement extends TimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_account")
    private ChartOfAccount account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_journal_template")
    private JournalTemplate journalTemplate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false, length = 20)
    private EvidenceType evidenceType;

    @Column(name = "min_amount", precision = 19, scale = 2)
    private BigDecimal minAmount;

    @Column(name = "notes", length = 255)
    private String notes;

    @NotBlank
    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    /** Account code and name, or template name, for display and messages. */
    public String getTargetLabel() {
        if (account != null) {
            return "Akun " + account.getAccountCode() + " " + account.getAccountName();
        }
        return "Template " + journalTemplate.getTemplateName();
    }
}
