package com.artivisi.accountingfinance.entity;

/**
 * Status for payroll run workflow.
 */
public enum PayrollStatus {
    DRAFT("Draft", "Payroll run created, not yet calculated"),
    CALCULATED("Calculated", "Payroll calculated, pending review"),
    APPROVED("Approved", "Payroll approved, ready to post"),
    POSTED("Posted", "Journal entries created"),
    CANCELLED("Cancelled", "Payroll run cancelled");

    private final String displayName;
    private final String description;

    PayrollStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
