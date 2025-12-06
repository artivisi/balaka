package com.artivisi.accountingfinance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * DTO for executing journal templates.
 *
 * For SIMPLE templates: use 'amount' field
 * For DETAILED templates: use 'variables' map with formula variable names as keys
 */
public record ExecuteTemplateDto(
        @NotNull(message = "Transaction date is required")
        LocalDate transactionDate,

        // Note: No validation here - for DETAILED templates, amount can be null/0
        // For SIMPLE templates, validation is done in TemplateExecutionEngine.validate()
        BigDecimal amount,

        @NotBlank(message = "Description is required")
        String description,

        /**
         * Variables for DETAILED templates.
         * Key: formula variable name (e.g., "kas", "bankBca")
         * Value: amount for that variable
         */
        Map<String, BigDecimal> variables
) {
    /**
     * Returns non-null variables map (empty if null).
     */
    public Map<String, BigDecimal> safeVariables() {
        return variables != null ? variables : Map.of();
    }
}
