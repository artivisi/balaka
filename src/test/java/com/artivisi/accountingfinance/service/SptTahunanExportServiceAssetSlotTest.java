package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.enums.NormalBalance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Transkrip 8A.I.1 must follow the explicit cash/bank flag, not the account code.
 * The fixture rows below are the exact case the prefix rule got wrong: 1.1.04 was
 * cash by code alone, while 1.1.07 is prepaid and 1.1.08 is a receivable.
 */
@DisplayName("Transkrip 8A asset slots")
class SptTahunanExportServiceAssetSlotTest {

    private final SptTahunanExportService service =
            new SptTahunanExportService(null, null, null, null, null, null, null, null);

    @Test
    @DisplayName("a flagged account is cash and equivalents, whatever its code")
    void flaggedAccountIsCash() {
        assertThat(service.mapAssetCodeToField(account("1.1.04", true, NormalBalance.DEBIT))).isEqualTo("8A.I.1");
        assertThat(service.mapAssetCodeToField(account("1.1.09", true, NormalBalance.DEBIT))).isEqualTo("8A.I.1");
    }

    @Test
    @DisplayName("an unflagged 1.1.0x account falls to its own line, not to cash")
    void unflaggedPrefixAccountIsNotCash() {
        assertThat(service.mapAssetCodeToField(account("1.1.04", false, NormalBalance.DEBIT))).isEqualTo("8A.I.10");
        assertThat(service.mapAssetCodeToField(account("1.1.07", false, NormalBalance.DEBIT))).isEqualTo("8A.I.10");
        assertThat(service.mapAssetCodeToField(account("1.1.08", false, NormalBalance.DEBIT))).isEqualTo("8A.I.10");
    }

    @Test
    @DisplayName("the established receivable and prepaid lines keep their slots")
    void existingLinesKeepTheirSlots() {
        assertThat(service.mapAssetCodeToField(account("1.1.10", false, NormalBalance.DEBIT))).isEqualTo("8A.I.2");
        assertThat(service.mapAssetCodeToField(account("1.1.15", false, NormalBalance.DEBIT))).isEqualTo("8A.I.5");
        assertThat(service.mapAssetCodeToField(account("1.1.25", false, NormalBalance.DEBIT))).isEqualTo("8A.I.7");
    }

    private ChartOfAccount account(String code, boolean cashBank, NormalBalance normalBalance) {
        ChartOfAccount account = new ChartOfAccount();
        account.setAccountCode(code);
        account.setNormalBalance(normalBalance);
        account.setIsCashBank(cashBank);
        return account;
    }
}
