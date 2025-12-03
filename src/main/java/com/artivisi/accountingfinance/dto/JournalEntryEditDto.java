package com.artivisi.accountingfinance.dto;

import com.artivisi.accountingfinance.entity.JournalEntry;

public record JournalEntryEditDto(
        String accountId,
        String journalDate,
        String referenceNumber,
        String description,
        Double debitAmount,
        Double creditAmount
) {
    public static JournalEntryEditDto fromEntity(JournalEntry entry) {
        return new JournalEntryEditDto(
                entry.getAccount() != null ? entry.getAccount().getId().toString() : null,
                entry.getEffectiveJournalDate() != null ? entry.getEffectiveJournalDate().toString() : null,
                entry.getEffectiveReferenceNumber(),
                entry.getEffectiveDescription(),
                entry.getDebitAmount() != null ? entry.getDebitAmount().doubleValue() : 0.0,
                entry.getCreditAmount() != null ? entry.getCreditAmount().doubleValue() : 0.0
        );
    }
}
