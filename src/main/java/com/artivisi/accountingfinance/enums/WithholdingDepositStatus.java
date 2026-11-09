package com.artivisi.accountingfinance.enums;

public enum WithholdingDepositStatus {
    NOTHING_DUE("Tidak ada kewajiban"),
    OPEN("Belum jatuh tempo"),
    OVERDUE("Lewat jatuh tempo"),
    ON_TIME("Disetor tepat waktu"),
    LATE("Disetor terlambat"),
    OVERDEPOSITED("Setoran melebihi potongan");

    private final String indonesianName;

    WithholdingDepositStatus(String indonesianName) {
        this.indonesianName = indonesianName;
    }

    public String getIndonesianName() {
        return indonesianName;
    }
}
