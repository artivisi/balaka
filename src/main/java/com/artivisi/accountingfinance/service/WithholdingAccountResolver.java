package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.enums.AccountType;
import com.artivisi.accountingfinance.enums.WithholdingTreatment;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** Finds tax accounts by name, because their codes differ between seed packs. */
@Component
@RequiredArgsConstructor
public class WithholdingAccountResolver {

    private static final String TAX_CREDIT_PPH_23 = "Kredit Pajak PPh 23";

    private final ChartOfAccountRepository accountRepository;

    public ChartOfAccount payableAccount(WithholdingTreatment treatment) {
        String name = treatment.getPayableAccountName();
        List<ChartOfAccount> matches = accountRepository.findByAccountName(name).stream()
                .filter(a -> a.getAccountType() == AccountType.LIABILITY && !Boolean.TRUE.equals(a.getIsHeader()))
                .toList();
        if (matches.size() != 1) {
            throw new IllegalStateException("Bagan akun harus memuat tepat satu akun kewajiban bernama '" + name
                    + "', ditemukan " + matches.size());
        }
        return matches.get(0);
    }

    /** Asset account holding PPh 23 withheld by customers. */
    public ChartOfAccount taxCreditPph23Account() {
        List<ChartOfAccount> matches = accountRepository.findByAccountName(TAX_CREDIT_PPH_23).stream()
                .filter(a -> a.getAccountType() == AccountType.ASSET && !Boolean.TRUE.equals(a.getIsHeader()))
                .toList();
        if (matches.size() != 1) {
            throw new IllegalStateException("Bagan akun harus memuat tepat satu akun aset bernama '" + TAX_CREDIT_PPH_23
                    + "', ditemukan " + matches.size());
        }
        return matches.get(0);
    }
}
