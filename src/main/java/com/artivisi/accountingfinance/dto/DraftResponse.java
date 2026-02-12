package com.artivisi.accountingfinance.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO for draft transaction operations.
 * Returned by API after creating or updating a draft.
 */
public record DraftResponse(
        UUID draftId,
        String status,
        String merchant,
        BigDecimal amount,
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate transactionDate,
        TemplateSuggestion suggestedTemplate,
        BigDecimal confidence,
        boolean needsClarification,
        String clarificationQuestion
) {
}
