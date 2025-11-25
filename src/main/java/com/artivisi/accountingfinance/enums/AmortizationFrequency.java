package com.artivisi.accountingfinance.enums;

public enum AmortizationFrequency {
    MONTHLY("Monthly", "Bulanan", 1),
    QUARTERLY("Quarterly", "Triwulanan", 3);

    private final String englishName;
    private final String indonesianName;
    private final int monthsPerPeriod;

    AmortizationFrequency(String englishName, String indonesianName, int monthsPerPeriod) {
        this.englishName = englishName;
        this.indonesianName = indonesianName;
        this.monthsPerPeriod = monthsPerPeriod;
    }

    public String getEnglishName() {
        return englishName;
    }

    public String getIndonesianName() {
        return indonesianName;
    }

    public int getMonthsPerPeriod() {
        return monthsPerPeriod;
    }
}
