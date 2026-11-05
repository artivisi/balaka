package com.artivisi.accountingfinance.enums;

public enum PayeeType {
    BADAN("Badan dalam negeri"),
    ORANG_PRIBADI("Orang pribadi dalam negeri"),
    LUAR_NEGERI("Penerima luar negeri");

    private final String indonesianName;

    PayeeType(String indonesianName) {
        this.indonesianName = indonesianName;
    }

    public String getIndonesianName() {
        return indonesianName;
    }
}
