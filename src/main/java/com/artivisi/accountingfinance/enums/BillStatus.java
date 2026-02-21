package com.artivisi.accountingfinance.enums;

public enum BillStatus {
    DRAFT("Draft", "Draf"),
    APPROVED("Approved", "Disetujui"),
    PARTIAL("Partial", "Sebagian"),
    PAID("Paid", "Lunas"),
    OVERDUE("Overdue", "Jatuh Tempo"),
    CANCELLED("Cancelled", "Dibatalkan");

    private final String englishName;
    private final String indonesianName;

    BillStatus(String englishName, String indonesianName) {
        this.englishName = englishName;
        this.indonesianName = indonesianName;
    }

    public String getEnglishName() {
        return englishName;
    }

    public String getIndonesianName() {
        return indonesianName;
    }
}
