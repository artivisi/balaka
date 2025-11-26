package com.artivisi.accountingfinance.dto.dataimport;

public record ImportError(
        int lineNumber,
        String field,
        String value,
        String message
) {
    public ImportError(int lineNumber, String message) {
        this(lineNumber, null, null, message);
    }

    public ImportError(int lineNumber, String field, String message) {
        this(lineNumber, field, null, message);
    }
}
