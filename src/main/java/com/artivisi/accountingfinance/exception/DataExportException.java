package com.artivisi.accountingfinance.exception;

/**
 * Exception thrown when data export operations fail.
 */
public class DataExportException extends RuntimeException {

    public DataExportException(String message) {
        super(message);
    }

    public DataExportException(String message, Throwable cause) {
        super(message, cause);
    }
}
