package com.artivisi.accountingfinance.entity;

/**
 * Non-recurring cash income entered per employee on a payroll run.
 */
public enum PayrollAdditionType {
    BONUS("Bonus"),
    THR("THR");

    private final String displayName;

    PayrollAdditionType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
