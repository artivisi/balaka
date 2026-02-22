package com.artivisi.accountingfinance.enums;

public enum RecurringLogStatus {
    SUCCESS("Success", "Berhasil"),
    FAILED("Failed", "Gagal"),
    SKIPPED("Skipped", "Dilewati");

    private final String englishName;
    private final String indonesianName;

    RecurringLogStatus(String englishName, String indonesianName) {
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
