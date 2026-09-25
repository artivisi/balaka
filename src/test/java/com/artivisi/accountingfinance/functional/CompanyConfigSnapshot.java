package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.CompanyConfig;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.CompanyConfigRepository;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Copy of the singleton company_config row. Tests that change company settings capture it
 * before and restore it after, because every other test class reads the same row
 * (invoice print, report headers, tax exports).
 */
public record CompanyConfigSnapshot(
        UUID id, String companyName, String companyAddress, String companyPhone, String companyEmail,
        String taxId, String npwp, String nitku, Integer fiscalYearStartMonth, String currencyCode,
        String signingOfficerName, String signingOfficerTitle, String companyLogoPath, String industry,
        LocalDate establishedDate, Boolean isPkp, LocalDate pkpSince,
        UUID receivableAccountId, UUID payableAccountId, UUID outputTaxAccountId, UUID inputTaxAccountId) {

    public static CompanyConfigSnapshot capture(CompanyConfigRepository repository) {
        CompanyConfig c = repository.findFirst()
                .orElseThrow(() -> new IllegalStateException("Seeded company config missing"));
        return new CompanyConfigSnapshot(
                c.getId(), c.getCompanyName(), c.getCompanyAddress(), c.getCompanyPhone(), c.getCompanyEmail(),
                c.getTaxId(), c.getNpwp(), c.getNitku(), c.getFiscalYearStartMonth(), c.getCurrencyCode(),
                c.getSigningOfficerName(), c.getSigningOfficerTitle(), c.getCompanyLogoPath(), c.getIndustry(),
                c.getEstablishedDate(), c.getIsPkp(), c.getPkpSince(),
                c.getReceivableAccountId(), c.getPayableAccountId(), c.getOutputTaxAccountId(), c.getInputTaxAccountId());
    }

    public void restore(CompanyConfigRepository repository, ChartOfAccountRepository accountRepository) {
        CompanyConfig c = repository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Company config " + id + " removed during test"));
        c.setCompanyName(companyName);
        c.setCompanyAddress(companyAddress);
        c.setCompanyPhone(companyPhone);
        c.setCompanyEmail(companyEmail);
        c.setTaxId(taxId);
        c.setNpwp(npwp);
        c.setNitku(nitku);
        c.setFiscalYearStartMonth(fiscalYearStartMonth);
        c.setCurrencyCode(currencyCode);
        c.setSigningOfficerName(signingOfficerName);
        c.setSigningOfficerTitle(signingOfficerTitle);
        c.setCompanyLogoPath(companyLogoPath);
        c.setIndustry(industry);
        c.setEstablishedDate(establishedDate);
        c.setIsPkp(isPkp);
        c.setPkpSince(pkpSince);
        c.setReceivableAccount(account(accountRepository, receivableAccountId));
        c.setPayableAccount(account(accountRepository, payableAccountId));
        c.setOutputTaxAccount(account(accountRepository, outputTaxAccountId));
        c.setInputTaxAccount(account(accountRepository, inputTaxAccountId));
        repository.save(c);
    }

    private static ChartOfAccount account(ChartOfAccountRepository accountRepository, UUID accountId) {
        if (accountId == null) {
            return null;
        }
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("Posting account " + accountId + " removed during test"));
    }
}
