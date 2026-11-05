package com.artivisi.accountingfinance.enums;

/**
 * How a payment to a payee is withheld. The payable account is resolved by name because its code differs
 * between seed packs (Hutang PPh 21 is 2.1.20, 2.1.14 or 2.1.03).
 */
public enum WithholdingTreatment {
    PPH_21_BUKAN_PEGAWAI("PPh 21 Bukan Pegawai", TaxType.PPH_21, TaxDeadlineType.PPH_21_PAYMENT, "Hutang PPh 21", PayeeType.ORANG_PRIBADI),
    PPH_23("PPh 23", TaxType.PPH_23, TaxDeadlineType.PPH_23_PAYMENT, "Hutang PPh 23", PayeeType.BADAN, PayeeType.ORANG_PRIBADI),
    PPH_26("PPh 26", TaxType.PPH_26, TaxDeadlineType.PPH_26_PAYMENT, "Hutang PPh 26", PayeeType.LUAR_NEGERI);

    private final String indonesianName;
    private final TaxType taxType;
    private final TaxDeadlineType deadlineType;
    private final String payableAccountName;
    private final java.util.Set<PayeeType> allowedPayeeTypes;

    WithholdingTreatment(String indonesianName, TaxType taxType, TaxDeadlineType deadlineType,
                         String payableAccountName, PayeeType... allowedPayeeTypes) {
        this.indonesianName = indonesianName;
        this.taxType = taxType;
        this.deadlineType = deadlineType;
        this.payableAccountName = payableAccountName;
        this.allowedPayeeTypes = java.util.Set.of(allowedPayeeTypes);
    }

    public String getIndonesianName() {
        return indonesianName;
    }

    public TaxType getTaxType() {
        return taxType;
    }

    public TaxDeadlineType getDeadlineType() {
        return deadlineType;
    }

    public String getPayableAccountName() {
        return payableAccountName;
    }

    public boolean allows(PayeeType payeeType) {
        return allowedPayeeTypes.contains(payeeType);
    }
}
