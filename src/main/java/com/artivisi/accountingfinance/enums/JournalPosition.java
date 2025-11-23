package com.artivisi.accountingfinance.enums;

public enum JournalPosition {
    DEBIT("Debit"),
    CREDIT("Credit");

    private final String displayName;

    JournalPosition(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
