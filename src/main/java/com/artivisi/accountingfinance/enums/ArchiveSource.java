package com.artivisi.accountingfinance.enums;

/** Where the original of an archived copy is kept. Balaka holds a copy only. */
public enum ArchiveSource {
    TAX_PORTAL("Portal pajak (Coretax/DJP Online)"),
    BANK("Bank"),
    OTHER("Lainnya");

    private final String indonesianName;

    ArchiveSource(String indonesianName) {
        this.indonesianName = indonesianName;
    }

    public String getIndonesianName() {
        return indonesianName;
    }
}
