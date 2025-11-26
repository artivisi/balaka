package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.CompanyConfig;
import com.artivisi.accountingfinance.repository.CompanyConfigRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanyConfigService {

    private final CompanyConfigRepository companyConfigRepository;

    public CompanyConfig getConfig() {
        return companyConfigRepository.findFirst()
                .orElseGet(this::createDefaultConfig);
    }

    public CompanyConfig findById(UUID id) {
        return companyConfigRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Company config not found with id: " + id));
    }

    @Transactional
    public CompanyConfig save(CompanyConfig config) {
        return companyConfigRepository.save(config);
    }

    @Transactional
    public CompanyConfig update(UUID id, CompanyConfig updatedConfig) {
        CompanyConfig existing = findById(id);

        existing.setCompanyName(updatedConfig.getCompanyName());
        existing.setCompanyAddress(updatedConfig.getCompanyAddress());
        existing.setCompanyPhone(updatedConfig.getCompanyPhone());
        existing.setCompanyEmail(updatedConfig.getCompanyEmail());
        existing.setTaxId(updatedConfig.getTaxId());
        existing.setFiscalYearStartMonth(updatedConfig.getFiscalYearStartMonth());
        existing.setCurrencyCode(updatedConfig.getCurrencyCode());
        existing.setSigningOfficerName(updatedConfig.getSigningOfficerName());
        existing.setSigningOfficerTitle(updatedConfig.getSigningOfficerTitle());
        existing.setCompanyLogoPath(updatedConfig.getCompanyLogoPath());

        return companyConfigRepository.save(existing);
    }

    @Transactional
    protected CompanyConfig createDefaultConfig() {
        CompanyConfig config = new CompanyConfig();
        config.setCompanyName("My Company");
        config.setFiscalYearStartMonth(1);
        config.setCurrencyCode("IDR");
        return companyConfigRepository.save(config);
    }
}
