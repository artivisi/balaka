package com.artivisi.accountingfinance.enums;

public enum AlertType {
    CASH_LOW("Cash Low", "Kas Rendah"),
    RECEIVABLE_OVERDUE("Receivable Overdue", "Piutang Jatuh Tempo"),
    EXPENSE_SPIKE("Expense Spike", "Lonjakan Biaya"),
    PROJECT_COST_OVERRUN("Project Cost Overrun", "Proyek Melebihi Anggaran"),
    PROJECT_MARGIN_DROP("Project Margin Drop", "Margin Proyek Turun"),
    COLLECTION_SLOWDOWN("Collection Slowdown", "Penagihan Melambat"),
    CLIENT_CONCENTRATION("Client Concentration", "Konsentrasi Klien");

    private final String englishName;
    private final String indonesianName;

    AlertType(String englishName, String indonesianName) {
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
