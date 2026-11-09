package com.artivisi.accountingfinance.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for transaction creation.
 * Returns transaction details with journal entries.
 */
public record TransactionResponse(
        UUID transactionId,
        String transactionNumber,
        String status,
        String merchant,
        BigDecimal amount,
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate transactionDate,
        String description,
        List<JournalEntryDto> journalEntries,
        List<String> warnings
) {

    /**
     * Journal entry details in response.
     */
    public record JournalEntryDto(
            String journalNumber,
            String accountCode,
            String accountName,
            BigDecimal debitAmount,
            BigDecimal creditAmount
    ) {}
}
