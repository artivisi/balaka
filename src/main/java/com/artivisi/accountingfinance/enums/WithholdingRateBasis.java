package com.artivisi.accountingfinance.enums;

public enum WithholdingRateBasis {
    PPH21_PASAL_17("Pasal 17 atas 50% bruto"),
    PPH21_PASAL_17_NO_ID("Pasal 17 atas 50% bruto, lebih tinggi 20% (tanpa NPWP/NIK)"),
    PPH23_STANDARD("Tarif PPh 23"),
    PPH23_NO_ID("Tarif PPh 23, lebih tinggi (tanpa NPWP/NIK)"),
    PPH26_STANDARD("Tarif umum PPh 26"),
    PPH26_TREATY("Tarif P3B dengan DGT berlaku"),
    PPH26_TREATY_DGT_INVALID("Tarif umum PPh 26: tarif P3B terisi, DGT tidak ada atau kedaluwarsa");

    private final String indonesianName;

    WithholdingRateBasis(String indonesianName) {
        this.indonesianName = indonesianName;
    }

    public String getIndonesianName() {
        return indonesianName;
    }
}
