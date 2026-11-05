package com.artivisi.accountingfinance.enums;

import java.math.BigDecimal;

/**
 * Indonesian Tax Object Codes (Kode Objek Pajak) for e-Bupot Unifikasi.
 * Based on KEP-143/PJ/2022 and PER-24/PJ/2021.
 */
public enum TaxObjectCode {

    // ========================================
    // PPh Pasal 23 - Jasa (2%)
    // ========================================
    PPH23_JASA_TEKNIK("24-104-01", "Jasa Teknik", new BigDecimal("2.00"), TaxType.PPH_23),
    PPH23_JASA_MANAJEMEN("24-104-02", "Jasa Manajemen", new BigDecimal("2.00"), TaxType.PPH_23),
    PPH23_JASA_KONSULTAN("24-104-03", "Jasa Konsultan", new BigDecimal("2.00"), TaxType.PPH_23),
    PPH23_JASA_AKUNTANSI("24-104-04", "Jasa Akuntansi, Pembukuan, Atestasi LK", new BigDecimal("2.00"), TaxType.PPH_23),
    PPH23_JASA_PENILAI("24-104-05", "Jasa Penilai", new BigDecimal("2.00"), TaxType.PPH_23),
    PPH23_JASA_AKTUARIS("24-104-06", "Jasa Aktuaris", new BigDecimal("2.00"), TaxType.PPH_23),
    PPH23_JASA_DESAIN("24-104-07", "Jasa Perencanaan/Desain", new BigDecimal("2.00"), TaxType.PPH_23),
    PPH23_JASA_PERANTARA("24-104-10", "Jasa Perantara/Keagenan", new BigDecimal("2.00"), TaxType.PPH_23),
    PPH23_JASA_PEMELIHARAAN("24-104-14", "Jasa Pemeliharaan/Perawatan/Perbaikan", new BigDecimal("2.00"), TaxType.PPH_23),
    PPH23_JASA_MAKLON("24-104-16", "Jasa Maklon", new BigDecimal("2.00"), TaxType.PPH_23),
    PPH23_JASA_CATERING("24-104-21", "Jasa Katering", new BigDecimal("2.00"), TaxType.PPH_23),
    PPH23_JASA_KEBERSIHAN("24-104-22", "Jasa Kebersihan/Cleaning Service", new BigDecimal("2.00"), TaxType.PPH_23),
    PPH23_JASA_IT("24-104-34", "Jasa Instalasi/Pemasangan", new BigDecimal("2.00"), TaxType.PPH_23),
    PPH23_JASA_LAIN("24-104-99", "Jasa Lainnya", new BigDecimal("2.00"), TaxType.PPH_23),

    // ========================================
    // PPh Pasal 23 - Sewa (2%)
    // ========================================
    PPH23_SEWA_KENDARAAN("24-100-02", "Sewa Kendaraan Angkutan Darat", new BigDecimal("2.00"), TaxType.PPH_23),
    PPH23_SEWA_ALAT("24-100-09", "Sewa Peralatan", new BigDecimal("2.00"), TaxType.PPH_23),

    // ========================================
    // PPh Pasal 4(2) - Final
    // ========================================
    PPH42_SEWA_TANAH_BANGUNAN("28-409-01", "Sewa Tanah dan/atau Bangunan", new BigDecimal("10.00"), TaxType.PPH_42),
    PPH42_JASA_KONSTRUKSI_KECIL("28-409-07", "Jasa Konstruksi - Pelaksana Kecil", new BigDecimal("1.75"), TaxType.PPH_42),
    PPH42_JASA_KONSTRUKSI_MENENGAH("28-409-08", "Jasa Konstruksi - Pelaksana Menengah/Besar", new BigDecimal("2.65"), TaxType.PPH_42),
    PPH42_JASA_KONSTRUKSI_KONSULTAN("28-409-09", "Jasa Konstruksi - Konsultan", new BigDecimal("3.50"), TaxType.PPH_42),
    PPH42_UMKM("28-423-01", "PPh Final UMKM (PP 55/2022)", new BigDecimal("0.50"), TaxType.PPH_42),

    // ========================================
    // PPh Pasal 26 - rate comes from WithholdingProperties or the payee's treaty rate.
    // Code unverified against the current KEP-143/PJ/2022 list.
    // ========================================
    PPH26_IMBALAN_JASA("27-100-99", "Imbalan Jasa Penerima Luar Negeri", new BigDecimal("20.00"), TaxType.PPH_26),

    // ========================================
    // PPh Pasal 21 - Bukan Pegawai
    // ========================================
    PPH21_HONORARIUM("21-100-09", "Honorarium/Imbalan Bukan Pegawai", new BigDecimal("0.00"), TaxType.PPH_21); // Progressive rate

    private final String code;
    private final String description;
    private final BigDecimal defaultRate;
    private final TaxType taxType;

    TaxObjectCode(String code, String description, BigDecimal defaultRate, TaxType taxType) {
        this.code = code;
        this.description = description;
        this.defaultRate = defaultRate;
        this.taxType = taxType;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getDefaultRate() {
        return defaultRate;
    }

    public TaxType getTaxType() {
        return taxType;
    }

    public String getDisplayName() {
        return code + " - " + description;
    }

    /**
     * Find TaxObjectCode by code string.
     * @param code The tax object code (e.g., "24-104-01")
     * @return The matching TaxObjectCode or null if not found
     */
    public static TaxObjectCode fromCode(String code) {
        if (code == null) return null;
        for (TaxObjectCode toc : values()) {
            if (toc.code.equals(code)) {
                return toc;
            }
        }
        return null;
    }

    /**
     * Get all codes for a specific tax type.
     */
    public static TaxObjectCode[] getByTaxType(TaxType taxType) {
        return java.util.Arrays.stream(values())
                .filter(toc -> toc.taxType == taxType)
                .toArray(TaxObjectCode[]::new);
    }
}
