package com.artivisi.accountingfinance.enums;

import java.util.Set;

/**
 * Role of an account in the period close checks (issue #50). Null on an account means it takes part only in the
 * checks that select by account type (unmoved liabilities).
 */
public enum CloseCheckRole {
    /** Income tax prepaid or withheld by others (PPh 22/23/25), credited in the annual return. */
    TAX_CREDIT("Kredit pajak", Set.of(AccountType.ASSET)),
    /** Clearing or suspense account whose balance must be moved out. */
    CLEARING("Akun antara", Set.of(AccountType.ASSET, AccountType.LIABILITY)),
    /** Current account between the company and one shareholder or director. */
    SHAREHOLDER_CURRENT("Rekening koran pemegang saham/direksi", Set.of(AccountType.ASSET, AccountType.LIABILITY));

    private final String indonesianName;
    private final Set<AccountType> allowedTypes;

    CloseCheckRole(String indonesianName, Set<AccountType> allowedTypes) {
        this.indonesianName = indonesianName;
        this.allowedTypes = allowedTypes;
    }

    public String getIndonesianName() {
        return indonesianName;
    }

    public boolean allows(AccountType type) {
        return allowedTypes.contains(type);
    }
}
