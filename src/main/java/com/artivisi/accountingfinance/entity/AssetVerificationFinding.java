package com.artivisi.accountingfinance.entity;

/** What the yearly verification report lists against an asset. */
public enum AssetVerificationFinding {
    NOT_VERIFIED("Belum diverifikasi tahun ini"),
    NOT_FOUND("Tidak ditemukan saat verifikasi"),
    MAJOR_DAMAGE("Rusak berat"),
    MISSING_BRAND_MODEL("Merek/model kosong"),
    MISSING_SERIAL("Nomor seri kosong"),
    MISSING_CUSTODIAN("Pemegang kosong"),
    MISSING_LOCATION("Lokasi kosong"),
    MISSING_CONDITION("Kondisi kosong"),
    MISSING_PHOTO("Foto kosong");

    private final String indonesianName;

    AssetVerificationFinding(String indonesianName) {
        this.indonesianName = indonesianName;
    }

    public String getIndonesianName() {
        return indonesianName;
    }
}
