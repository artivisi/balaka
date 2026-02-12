package com.artivisi.accountingfinance.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request DTO for creating draft transaction from AI-parsed text.
 * Used by external AI assistants (Claude Code, Gemini, etc.) via REST API.
 */
public record CreateFromTextRequest(

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

        @Size(max = 100, message = "Category must not exceed 100 characters")
        String category,

        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        String description,

        @Min(value = 0, message = "Confidence must be between 0 and 1")
        @Max(value = 1, message = "Confidence must be between 0 and 1")
        BigDecimal confidence,

        @Size(max = 50, message = "Source must not exceed 50 characters")
        String source
) {

    /**
     * Constructor with default values for optional fields.
     */
    public CreateFromTextRequest {
        if (currency == null || currency.isBlank()) {
            currency = "IDR";
        }
        if (confidence == null) {
            confidence = BigDecimal.ZERO;
        }
        if (source == null || source.isBlank()) {
            source = "unknown";
        }
    }
}
