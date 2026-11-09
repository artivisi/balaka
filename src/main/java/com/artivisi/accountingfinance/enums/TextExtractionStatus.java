package com.artivisi.accountingfinance.enums;

/** Result of extracting searchable text from an archived file. */
public enum TextExtractionStatus {
    EXTRACTED("Teks terbaca"),
    NO_TEXT("Tidak ada teks (hasil pindai)"),
    NOT_SUPPORTED("Format tanpa ekstraksi teks");

    private final String indonesianName;

    TextExtractionStatus(String indonesianName) {
        this.indonesianName = indonesianName;
    }

    public String getIndonesianName() {
        return indonesianName;
    }
}
