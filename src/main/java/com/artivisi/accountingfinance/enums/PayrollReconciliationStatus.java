package com.artivisi.accountingfinance.enums;

/**
 * Outcome of comparing salary expense in the ledger with the payroll run of one period.
 */
public enum PayrollReconciliationStatus {
    /** No active run and no salary expense in the ledger. */
    NO_ACTIVITY("Tidak ada aktivitas"),
    /** Salary expense in the ledger without any active run: PPh 21 and 1721-A1 do not see this income. */
    LEDGER_WITHOUT_RUN("Beban gaji tanpa payroll"),
    /** Active run not posted yet. Any salary expense already in the ledger is outside the run. */
    RUN_NOT_POSTED("Payroll belum diposting"),
    /** Posted run and ledger differ: expense outside the run, or a voided or edited run journal. */
    DIFFERENCE("Ada selisih"),
    MATCHED("Sesuai");

    private final String indonesianName;

    PayrollReconciliationStatus(String indonesianName) {
        this.indonesianName = indonesianName;
    }

    public String getIndonesianName() {
        return indonesianName;
    }
}
