package com.artivisi.accountingfinance.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Request DTO for creating transaction directly (bypassing draft workflow).
 * Used by AI assistants after user approval in client-side consultation flow.
 */
public record CreateTransactionRequest(

        @NotNull(message = "Template ID is required")
        UUID templateId,

        @NotBlank(message = "Merchant is required")
        @Size(max = 255, message = "Merchant name must not exceed 255 characters")
        String merchant,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        BigDecimal amount,

        @NotNull(message = "Transaction date is required")
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate transactionDate,

        @Size(max = 10, message = "Currency code must not exceed 10 characters")
        String currency,

        @NotBlank(message = "Description is required")
        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        String description,

        @Size(max = 100, message = "Category must not exceed 100 characters")
        String category,

        List<String> items,

        @Size(max = 50, message = "Source must not exceed 50 characters")
        String source,

        Boolean userApproved,

        /**
         * Map of template lineOrder to accountId.
         * Used to specify accounts for template lines that have accountHint instead of a fixed account.
         * Example: {"2": "e7e8f9a0-..."} sets the account for line 2.
         */
        Map<Integer, UUID> lineAccountOverrides
) {

    /**
     * Constructor with default values for optional fields.
     */
    public CreateTransactionRequest {
        if (currency == null || currency.isBlank()) {
            currency = "IDR";
        }
        if (source == null || source.isBlank()) {
            source = "unknown";
        }
        if (userApproved == null) {
            userApproved = false;
        }
    }
}
