package com.artivisi.accountingfinance.enums;

public enum AccountType {
    ASSET("Asset", "Aset"),
    LIABILITY("Liability", "Liabilitas"),
    EQUITY("Equity", "Ekuitas"),
    REVENUE("Revenue", "Pendapatan"),
    EXPENSE("Expense", "Beban");

    private final String englishName;
    private final String indonesianName;

    AccountType(String englishName, String indonesianName) {
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
