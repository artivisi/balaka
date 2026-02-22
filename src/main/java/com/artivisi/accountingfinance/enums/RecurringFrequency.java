package com.artivisi.accountingfinance.enums;

public enum RecurringFrequency {
    DAILY("Daily", "Harian"),
    WEEKLY("Weekly", "Mingguan"),
    MONTHLY("Monthly", "Bulanan"),
    QUARTERLY("Quarterly", "Triwulanan"),
    YEARLY("Yearly", "Tahunan");

    private final String englishName;
    private final String indonesianName;

    RecurringFrequency(String englishName, String indonesianName) {
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
