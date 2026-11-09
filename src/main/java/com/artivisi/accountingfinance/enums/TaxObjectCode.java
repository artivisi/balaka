package com.artivisi.accountingfinance.enums;

import java.math.BigDecimal;

/**
 * Kode Objek Pajak for Coretax bukti potong.
 *
 * PPh 23, 4(2) and 26 codes follow the e-Bupot Unifikasi numbering of PER-24/PJ/2021, carried into
 * Coretax. PPh 21 bukan pegawai codes follow the BP21 list in Lampiran A angka 2 PER-11/PJ/2025.
 * Checked against the datacenter.ortax.org object-and-rate list on 2026-11-06. The list has no
 * catch-all service code: every PPh 23 service needs its own code.
 */
public enum TaxObjectCode {

    // ========================================
    // PPh Pasal 23 - Jasa (2%)
    // ========================================
    PPH23_JASA_TEKNIK("24-104-01", "Jasa Teknik", new BigDecimal("2.00"), TaxType.PPH_23),
    PPH23_JASA_MANAJEMEN("24-104-02", "Jasa Manajemen", new BigDecimal("2.00"), TaxType.PPH_23),
    PPH23_JASA_KONSULTAN("24-104-03", "Jasa Konsultan", new BigDecimal("2.00"), TaxType.PPH_23),
    PPH23_JASA_PENILAI("24-104-04", "Jasa Penilai (Appraisal)", new BigDecimal("2.00"), TaxType.PPH_23),
    PPH23_JASA_AKTUARIS("24-104-05", "Jasa Aktuaris", new BigDecimal("2.00"), TaxType.PPH_23),
    PPH23_JASA_AKUNTANSI("24-104-06", "Jasa Akuntansi, Pembukuan, dan Atestasi Laporan Keuangan", new BigDecimal("2.00"), TaxType.PPH_23),
    PPH23_JASA_HUKUM("24-104-07", "Jasa Hukum", new BigDecimal("2.00"), TaxType.PPH_23),
    PPH23_JASA_ARSITEKTUR("24-104-08", "Jasa Arsitektur", new BigDecimal("2.00"), TaxType.PPH_23),
    PPH23_JASA_DESAIN("24-104-10", "Jasa Perancang (Design)", new BigDecimal("2.00"), TaxType.PPH_23),
    PPH23_JASA_OUTSOURCING("24-104-17", "Jasa Penyedia Tenaga Kerja dan/atau Tenaga Ahli (Outsourcing)", new BigDecimal("2.00"), TaxType.PPH_23),
    PPH23_JASA_PERANTARA("24-104-18", "Jasa Perantara dan/atau Keagenan", new BigDecimal("2.00"), TaxType.PPH_23),
    PPH23_JASA_SOFTWARE("24-104-24", "Jasa Sehubungan dengan Software, Hardware, atau Sistem Komputer", new BigDecimal("2.00"), TaxType.PPH_23),
    PPH23_JASA_WEBSITE("24-104-25", "Jasa Pembuatan dan/atau Pengelolaan Website", new BigDecimal("2.00"), TaxType.PPH_23),
    PPH23_JASA_INTERNET("24-104-26", "Jasa Internet termasuk Sambungannya", new BigDecimal("2.00"), TaxType.PPH_23),
    PPH23_JASA_DATA("24-104-27", "Jasa Penyimpanan, Pengolahan dan/atau Penyaluran Data, Informasi, dan/atau Program", new BigDecimal("2.00"), TaxType.PPH_23),
    PPH23_JASA_INSTALASI("24-104-28", "Jasa Instalasi/Pemasangan Mesin, Peralatan, Listrik, Telepon, Air, Gas, AC dan/atau TV Kabel", new BigDecimal("2.00"), TaxType.PPH_23),
    PPH23_JASA_PEMELIHARAAN("24-104-29", "Jasa Perawatan/Perbaikan/Pemeliharaan Mesin, Peralatan, Listrik, Telepon, Air, Gas, AC dan/atau TV Kabel", new BigDecimal("2.00"), TaxType.PPH_23),
    PPH23_JASA_PERAWATAN_KENDARAAN("24-104-30", "Jasa Perawatan Kendaraan dan/atau Alat Transportasi", new BigDecimal("2.00"), TaxType.PPH_23),
    PPH23_JASA_MAKLON("24-104-31", "Jasa Maklon", new BigDecimal("2.00"), TaxType.PPH_23),
    PPH23_JASA_KEAMANAN("24-104-32", "Jasa Penyelidikan dan Keamanan", new BigDecimal("2.00"), TaxType.PPH_23),
    PPH23_JASA_EVENT_ORGANIZER("24-104-33", "Jasa Penyelenggara Kegiatan atau Event Organizer", new BigDecimal("2.00"), TaxType.PPH_23),
    PPH23_JASA_PERIKLANAN("24-104-34", "Jasa Penyediaan Tempat dan/atau Waktu dalam Media dan/atau Jasa Periklanan", new BigDecimal("2.00"), TaxType.PPH_23),
    PPH23_JASA_KEBERSIHAN("24-104-36", "Jasa Kebersihan atau Cleaning Service", new BigDecimal("2.00"), TaxType.PPH_23),
    PPH23_JASA_CATERING("24-104-39", "Jasa Katering atau Tata Boga", new BigDecimal("2.00"), TaxType.PPH_23),
    PPH23_JASA_PENCETAKAN("24-104-54", "Jasa Pencetakan/Penerbitan", new BigDecimal("2.00"), TaxType.PPH_23),
    PPH23_JASA_PENERJEMAHAN("24-104-55", "Jasa Penerjemahan", new BigDecimal("2.00"), TaxType.PPH_23),
    PPH23_JASA_PENGANGKUTAN("24-104-56", "Jasa Pengangkutan/Ekspedisi selain yang Diatur dalam Pasal 15 UU PPh", new BigDecimal("2.00"), TaxType.PPH_23),
    PPH23_JASA_PELATIHAN("24-104-60", "Jasa Pelatihan dan/atau Kursus", new BigDecimal("2.00"), TaxType.PPH_23),

    // ========================================
    // PPh Pasal 23 - Sewa (2%)
    // ========================================
    PPH23_SEWA_HARTA("24-100-02", "Sewa dan Penghasilan Lain Sehubungan dengan Penggunaan Harta selain Tanah dan/atau Bangunan", new BigDecimal("2.00"), TaxType.PPH_23),

    // ========================================
    // PPh Pasal 4(2) - Final, dipotong oleh pemotong (setor-sendiri codes are not offered)
    // ========================================
    PPH42_SEWA_TANAH_BANGUNAN("28-403-02", "Persewaan Tanah dan/atau Bangunan", new BigDecimal("10.00"), TaxType.PPH_42),
    PPH42_JASA_KONSTRUKSI_KECIL("28-409-22", "Pekerjaan Konstruksi - Sertifikat Badan Usaha Kualifikasi Kecil atau Sertifikat Kompetensi Kerja", new BigDecimal("1.75"), TaxType.PPH_42),
    PPH42_JASA_KONSTRUKSI_TANPA_SERTIFIKAT("28-409-23", "Pekerjaan Konstruksi - Tanpa Sertifikat", new BigDecimal("4.00"), TaxType.PPH_42),
    PPH42_JASA_KONSTRUKSI_MENENGAH("28-409-24", "Pekerjaan Konstruksi - Sertifikat selain Kualifikasi Kecil", new BigDecimal("2.65"), TaxType.PPH_42),
    PPH42_JASA_KONSTRUKSI_TERINTEGRASI("28-409-25", "Pekerjaan Konstruksi Terintegrasi - Bersertifikat Badan Usaha", new BigDecimal("2.65"), TaxType.PPH_42),
    PPH42_JASA_KONSTRUKSI_TERINTEGRASI_TANPA_SERTIFIKAT("28-409-26", "Pekerjaan Konstruksi Terintegrasi - Tanpa Sertifikat Badan Usaha", new BigDecimal("4.00"), TaxType.PPH_42),
    PPH42_JASA_KONSTRUKSI_KONSULTAN("28-409-27", "Konsultansi Konstruksi - Bersertifikat", new BigDecimal("3.50"), TaxType.PPH_42),
    PPH42_JASA_KONSTRUKSI_KONSULTAN_TANPA_SERTIFIKAT("28-409-28", "Konsultansi Konstruksi - Tanpa Sertifikat", new BigDecimal("6.00"), TaxType.PPH_42),
    PPH42_UMKM("28-423-01", "PPh Final UMKM (PP 55/2022 jo. PP 20/2026)", new BigDecimal("0.50"), TaxType.PPH_42),

    // ========================================
    // PPh Pasal 26 - rate comes from WithholdingProperties or the payee's treaty rate.
    // ========================================
    PPH26_SEWA("27-100-01", "Sewa dan Penghasilan Lain Sehubungan dengan Penggunaan Harta", new BigDecimal("20.00"), TaxType.PPH_26),
    PPH26_DIVIDEN("27-101-01", "Dividen", new BigDecimal("20.00"), TaxType.PPH_26),
    PPH26_BUNGA("27-102-01", "Bunga selain Bunga Obligasi", new BigDecimal("20.00"), TaxType.PPH_26),
    PPH26_ROYALTI("27-103-01", "Royalti", new BigDecimal("20.00"), TaxType.PPH_26),
    PPH26_IMBALAN_JASA("27-104-01", "Imbalan Sehubungan dengan Jasa, Pekerjaan, dan Kegiatan", new BigDecimal("20.00"), TaxType.PPH_26),

    // ========================================
    // PPh Pasal 21 - Bukan Pegawai (rate: 50% of gross at Pasal 17 rates)
    // ========================================
    PPH21_TENAGA_AHLI("21-100-07", "Tenaga Ahli (Pengacara, Akuntan, Arsitek, Dokter, Konsultan, Notaris, Penilai, Aktuaris)", new BigDecimal("0.00"), TaxType.PPH_21),
    PPH21_PENASIHAT_PENGAJAR("21-100-18", "Penasihat, Pengajar, Pelatih, Penceramah, Penyuluh, dan Moderator", new BigDecimal("0.00"), TaxType.PPH_21),
    PPH21_PENGARANG_PENELITI("21-100-19", "Pengarang, Peneliti, dan Penerjemah", new BigDecimal("0.00"), TaxType.PPH_21),
    PPH21_PEMBERI_JASA("21-100-20", "Pemberi Jasa dalam Segala Bidang", new BigDecimal("0.00"), TaxType.PPH_21);

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
