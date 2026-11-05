package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.Vendor;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.VendorRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import com.artivisi.accountingfinance.enums.PayeeType;
import com.artivisi.accountingfinance.enums.TaxObjectCode;
import com.artivisi.accountingfinance.enums.WithholdingTreatment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    public Vendor findOrCreateByName(String vendorName) {
        return vendorRepository.findByNameIgnoreCase(vendorName)
                .orElseGet(() -> {
                    Vendor vendor = new Vendor();
                    vendor.setName(vendorName);
                    vendor.setCode(generateVendorCode());
                    vendor.setActive(true);
                    return vendorRepository.save(vendor);
                });
    }

    private String generateVendorCode() {
        long count = vendorRepository.count();
        String code;
        do {
            count++;
            code = "VND-" + String.format("%04d", count);
        } while (vendorRepository.existsByCode(code));
        return code;
    }

    @Transactional
    public Vendor create(Vendor vendor) {
        validatePayee(vendor);
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
        validatePayee(updatedVendor);
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
        existing.setPayeeType(updatedVendor.getPayeeType());
        existing.setDefaultWithholding(updatedVendor.getDefaultWithholding());
        existing.setDefaultTaxObjectCode(updatedVendor.getDefaultTaxObjectCode());
        existing.setCountryCode(updatedVendor.getCountryCode());
        existing.setForeignTin(updatedVendor.getForeignTin());
        existing.setTreatyRate(updatedVendor.getTreatyRate());
        existing.setDgtFormNumber(updatedVendor.getDgtFormNumber());
        existing.setDgtValidUntil(updatedVendor.getDgtValidUntil());
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

    private void validatePayee(Vendor vendor) {
        requireDigits(vendor.getNik(), 16, 16, "NIK harus 16 digit");
        requireDigits(vendor.getNpwp(), 15, 16, "NPWP harus 15 atau 16 digit");

        PayeeType type = vendor.getPayeeType();
        WithholdingTreatment treatment = vendor.getDefaultWithholding();
        if (treatment != null && type == null) {
            throw new IllegalArgumentException("Jenis penerima wajib diisi jika perlakuan pajak diisi");
        }
        if (treatment != null && !treatment.allows(type)) {
            throw new IllegalArgumentException(treatment.getIndonesianName()
                    + " tidak berlaku untuk " + type.getIndonesianName());
        }
        String objectCode = vendor.getDefaultTaxObjectCode();
        if (objectCode != null && !objectCode.isBlank()) {
            TaxObjectCode code = TaxObjectCode.fromCode(objectCode);
            if (code == null || treatment == null || code.getTaxType() != treatment.getTaxType()) {
                throw new IllegalArgumentException("Kode objek pajak " + objectCode + " tidak sesuai perlakuan pajak penerima");
            }
        }
        if (type == PayeeType.LUAR_NEGERI && (vendor.getCountryCode() == null || !vendor.getCountryCode().matches("[A-Z]{2}"))) {
            throw new IllegalArgumentException("Kode negara (2 huruf kapital) wajib diisi untuk penerima luar negeri");
        }
        BigDecimal treatyRate = vendor.getTreatyRate();
        if (treatyRate != null) {
            if (type != PayeeType.LUAR_NEGERI) {
                throw new IllegalArgumentException("Tarif P3B hanya berlaku untuk penerima luar negeri");
            }
            if (treatyRate.signum() < 0 || treatyRate.compareTo(new BigDecimal("100")) > 0) {
                throw new IllegalArgumentException("Tarif P3B harus antara 0 dan 100");
            }
        }
    }

    private void requireDigits(String value, int min, int max, String message) {
        if (value == null || value.isBlank()) {
            return;
        }
        int digits = value.replaceAll("\\D", "").length();
        if (digits < min || digits > max) {
            throw new IllegalArgumentException(message);
        }
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
