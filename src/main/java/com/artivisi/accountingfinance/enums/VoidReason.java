package com.artivisi.accountingfinance.enums;

public enum VoidReason {
    INPUT_ERROR("Input Error", "Kesalahan Input"),
    DUPLICATE("Duplicate", "Duplikasi"),
    CANCELLED("Cancelled", "Batal Transaksi"),
    OTHER("Other", "Lainnya");

    private final String englishName;
    private final String indonesianName;

    VoidReason(String englishName, String indonesianName) {
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
