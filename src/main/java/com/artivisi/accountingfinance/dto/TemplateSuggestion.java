package com.artivisi.accountingfinance.dto;

import java.util.UUID;

/**
 * Template suggestion for draft transaction.
 * Returned by API to suggest which journal template to use.
 */
public record TemplateSuggestion(
        UUID id,
        String name,
        String category
) {
}
