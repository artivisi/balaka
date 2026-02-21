package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.Vendor;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.VendorRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VendorService {

    private final VendorRepository vendorRepository;
    private final ChartOfAccountRepository chartOfAccountRepository;

    public Vendor findById(UUID id) {
        return vendorRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Vendor not found with id: " + id));
    }

    public Vendor findByCode(String code) {
        return vendorRepository.findByCode(code)
                .orElseThrow(() -> new EntityNotFoundException("Vendor not found with code: " + code));
    }

    public Page<Vendor> findAll(Pageable pageable) {
        return vendorRepository.findAllByOrderByNameAsc(pageable);
    }

    public Page<Vendor> findByFilters(Boolean active, String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return vendorRepository.findByFiltersAndSearch(active, search, pageable);
        }
        return vendorRepository.findByFilters(active, pageable);
    }

    public List<Vendor> findActiveVendors() {
        return vendorRepository.findByActiveTrueOrderByNameAsc();
    }

    @Transactional
    public Vendor create(Vendor vendor) {
        if (vendorRepository.existsByCode(vendor.getCode())) {
            throw new IllegalArgumentException("Kode vendor sudah digunakan: " + vendor.getCode());
        }

        if (vendor.getDefaultExpenseAccount() != null && vendor.getDefaultExpenseAccount().getId() != null) {
            ChartOfAccount account = chartOfAccountRepository.findById(vendor.getDefaultExpenseAccount().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Akun beban tidak ditemukan"));
            vendor.setDefaultExpenseAccount(account);
        } else {
            vendor.setDefaultExpenseAccount(null);
        }

        vendor.setActive(true);
        return vendorRepository.save(vendor);
    }

    @Transactional
    public Vendor update(UUID id, Vendor updatedVendor) {
        Vendor existing = findById(id);

        if (!existing.getCode().equals(updatedVendor.getCode()) &&
                vendorRepository.existsByCode(updatedVendor.getCode())) {
            throw new IllegalArgumentException("Kode vendor sudah digunakan: " + updatedVendor.getCode());
        }

        existing.setCode(updatedVendor.getCode());
        existing.setName(updatedVendor.getName());
        existing.setContactPerson(updatedVendor.getContactPerson());
        existing.setEmail(updatedVendor.getEmail());
        existing.setPhone(updatedVendor.getPhone());
        existing.setAddress(updatedVendor.getAddress());
        existing.setNotes(updatedVendor.getNotes());
        existing.setNpwp(updatedVendor.getNpwp());
        existing.setNitku(updatedVendor.getNitku());
        existing.setNik(updatedVendor.getNik());
        existing.setIdType(updatedVendor.getIdType());
        existing.setPaymentTermDays(updatedVendor.getPaymentTermDays());
        existing.setBankName(updatedVendor.getBankName());
        existing.setBankAccountNumber(updatedVendor.getBankAccountNumber());
        existing.setBankAccountName(updatedVendor.getBankAccountName());

        if (updatedVendor.getDefaultExpenseAccount() != null && updatedVendor.getDefaultExpenseAccount().getId() != null) {
            ChartOfAccount account = chartOfAccountRepository.findById(updatedVendor.getDefaultExpenseAccount().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Akun beban tidak ditemukan"));
            existing.setDefaultExpenseAccount(account);
        } else {
            existing.setDefaultExpenseAccount(null);
        }

        return vendorRepository.save(existing);
    }

    @Transactional
    public void deactivate(UUID id) {
        Vendor vendor = findById(id);
        vendor.setActive(false);
        vendorRepository.save(vendor);
    }

    @Transactional
    public void activate(UUID id) {
        Vendor vendor = findById(id);
        vendor.setActive(true);
        vendorRepository.save(vendor);
    }
}
