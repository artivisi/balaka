package com.artivisi.accountingfinance.enums;

/**
 * Role of an account in the tax-office self-test (issue #45). Null means the account takes part only in the lines
 * that select by account type (revenue, capital) or by another configuration (payroll template, asset categories).
 */
public enum SelfTestRole {
    /** Service, or rent of property other than land and buildings: base of PPh 23. */
    SERVICE_PPH23("Jasa/sewa objek PPh 23", AccountType.EXPENSE),
    /** Rent of land or buildings: base of PPh 4 ayat (2). */
    RENT_PPH42("Sewa tanah/bangunan (PPh 4(2))", AccountType.EXPENSE),
    /** "Other expenses": lines above the threshold without evidence are reported as unitemised. */
    OTHER_EXPENSE("Beban lain-lain", AccountType.EXPENSE),
    /** Asset not used in operations: investment, loan to a related party, asset held for a shareholder. */
    NON_OPERATING_ASSET("Aset non-operasional", AccountType.ASSET);

    private final String indonesianName;
    private final AccountType allowedType;

    SelfTestRole(String indonesianName, AccountType allowedType) {
        this.indonesianName = indonesianName;
        this.allowedType = allowedType;
    }

    public String getIndonesianName() {
        return indonesianName;
    }

    public boolean allows(AccountType type) {
        return allowedType == type;
    }
}
