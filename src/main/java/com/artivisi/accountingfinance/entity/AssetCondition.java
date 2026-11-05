package com.artivisi.accountingfinance.entity;

/** Physical condition recorded on the register and at verification. */
public enum AssetCondition {
    GOOD("Baik"),
    MINOR_DAMAGE("Rusak Ringan"),
    MAJOR_DAMAGE("Rusak Berat");

    private final String indonesianName;

    AssetCondition(String indonesianName) {
        this.indonesianName = indonesianName;
    }

    public String getIndonesianName() {
        return indonesianName;
    }
}
