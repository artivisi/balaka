package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.enums.AccountType;
import com.artivisi.accountingfinance.enums.WithholdingTreatment;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** Finds the Hutang PPh account by name, because its code differs between seed packs. */
@Component
@RequiredArgsConstructor
public class WithholdingAccountResolver {

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
}
