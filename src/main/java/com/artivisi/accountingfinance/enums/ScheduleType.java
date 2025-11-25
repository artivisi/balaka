package com.artivisi.accountingfinance.enums;

public enum ScheduleType {
    PREPAID_EXPENSE("Prepaid Expense", "Beban Dibayar Dimuka"),
    UNEARNED_REVENUE("Unearned Revenue", "Pendapatan Diterima Dimuka"),
    INTANGIBLE_ASSET("Intangible Asset", "Aset Tak Berwujud"),
    ACCRUED_REVENUE("Accrued Revenue", "Pendapatan Akrual");

    private final String englishName;
    private final String indonesianName;

    ScheduleType(String englishName, String indonesianName) {
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
