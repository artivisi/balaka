package com.artivisi.accountingfinance.enums;

public enum NormalBalance {
    DEBIT("Debit"),
    CREDIT("Credit");

    private final String displayName;

    NormalBalance(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
