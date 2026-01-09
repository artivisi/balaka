package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.CompanyConfig;
import com.artivisi.accountingfinance.repository.CompanyConfigRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanyConfigService {

    private final CompanyConfigRepository companyConfigRepository;

    @Transactional
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
        // Always fetch the existing config from database to ensure we're updating the right one
        CompanyConfig existing = companyConfigRepository.findById(id)
                .orElseGet(() ->
                    // If config with this ID doesn't exist, get or create the first one
                    companyConfigRepository.findFirst().orElseGet(this::createDefaultConfig)
                );

        existing.setCompanyName(updatedConfig.getCompanyName());
        existing.setCompanyAddress(updatedConfig.getCompanyAddress());
        existing.setCompanyPhone(updatedConfig.getCompanyPhone());
        existing.setCompanyEmail(updatedConfig.getCompanyEmail());
        existing.setTaxId(updatedConfig.getTaxId());
        existing.setNpwp(updatedConfig.getNpwp());
        existing.setNitku(updatedConfig.getNitku());
        existing.setFiscalYearStartMonth(updatedConfig.getFiscalYearStartMonth());
        existing.setCurrencyCode(updatedConfig.getCurrencyCode());
        existing.setSigningOfficerName(updatedConfig.getSigningOfficerName());
        existing.setSigningOfficerTitle(updatedConfig.getSigningOfficerTitle());
        existing.setCompanyLogoPath(updatedConfig.getCompanyLogoPath());

        return companyConfigRepository.save(existing);
    }

    @Transactional
    protected CompanyConfig createDefaultConfig() {
        // Check again inside transaction to avoid race condition
        Optional<CompanyConfig> existing = companyConfigRepository.findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }
        
        CompanyConfig config = new CompanyConfig();
        config.setCompanyName("My Company");
        config.setFiscalYearStartMonth(1);
        config.setCurrencyCode("IDR");
        return companyConfigRepository.save(config);
    }
}
