package com.artivisi.accountingfinance.enums;

/**
 * What an attached file proves. OTHER classifies a file that satisfies no requirement, so that it no
 * longer shows as unclassified.
 */
public enum EvidenceType {
    CONTRACT("Kontrak", true),
    INVOICE("Invoice / Faktur", true),
    PROOF_OF_PAYMENT("Bukti Pembayaran", true),
    BUPOT("Bukti Potong", true),
    OTHER("Lainnya", false);

    private final String indonesianName;
    private final boolean requirable;

    EvidenceType(String indonesianName, boolean requirable) {
        this.indonesianName = indonesianName;
        this.requirable = requirable;
    }

    public String getIndonesianName() {
        return indonesianName;
    }

    /** Whether a requirement rule may ask for this type. */
    public boolean isRequirable() {
        return requirable;
    }

    public static java.util.List<EvidenceType> requirableValues() {
        return java.util.Arrays.stream(values()).filter(EvidenceType::isRequirable).toList();
    }
}
