package com.artivisi.accountingfinance.enums;

/**
 * Corporate income tax treatment of an expense account (UU PPh Pasal 6 and Pasal 9).
 */
public enum FiscalTreatment {
    DEDUCTIBLE("Dapat dikurangkan"),
    NON_DEDUCTIBLE("Tidak dapat dikurangkan"),
    CONDITIONAL("Bersyarat");

    private final String indonesianName;

    FiscalTreatment(String indonesianName) {
        this.indonesianName = indonesianName;
    }

    public String getIndonesianName() {
        return indonesianName;
    }
}
