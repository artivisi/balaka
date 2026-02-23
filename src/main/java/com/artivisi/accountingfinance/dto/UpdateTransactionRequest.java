package com.artivisi.accountingfinance.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Request DTO for updating a DRAFT transaction.
 * All fields are optional — only provided fields are updated.
 */
public record UpdateTransactionRequest(

        UUID templateId,

        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        String description,

        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        BigDecimal amount,

        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate transactionDate,

        /**
         * Map of template lineOrder to accountId.
         * Used to specify accounts for template lines that have accountHint instead of a fixed account.
         * Example: {"2": "e7e8f9a0-..."} sets the account for line 2.
         */
        Map<Integer, UUID> lineAccountOverrides
) {}
