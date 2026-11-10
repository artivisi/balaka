package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.CompanyBankAccount;
import com.artivisi.accountingfinance.repository.CompanyBankAccountRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanyBankAccountService {

    private final CompanyBankAccountRepository bankAccountRepository;
    private final ChartOfAccountService chartOfAccountService;

    public List<CompanyBankAccount> findAll() {
        return bankAccountRepository.findAllByOrderByBankNameAsc();
    }

    public List<CompanyBankAccount> findActive() {
        return bankAccountRepository.findByActiveTrueOrderByBankNameAsc();
    }

    public CompanyBankAccount findById(UUID id) {
        return bankAccountRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Bank account not found with id: " + id));
    }

    public Optional<CompanyBankAccount> findDefaultAccount() {
        return bankAccountRepository.findByIsDefaultTrueAndActiveTrue();
    }

    @Transactional
    public CompanyBankAccount create(CompanyBankAccount bankAccount) {
        rejectDuplicateAccountNumber(bankAccount.getAccountNumber(), null);

        // If this is the first account or marked as default, handle default flag
        if (Boolean.TRUE.equals(bankAccount.getIsDefault())) {
            clearDefaultFlag();
        }

        bankAccount.setActive(true);
        CompanyBankAccount saved = bankAccountRepository.save(bankAccount);
        chartOfAccountService.applyBankLink(saved.getGlAccount());
        return saved;
    }

    @Transactional
    public CompanyBankAccount update(UUID id, CompanyBankAccount updatedAccount) {
        CompanyBankAccount existing = findById(id);

        rejectDuplicateAccountNumber(updatedAccount.getAccountNumber(), id);

        // Handle default flag
        if (Boolean.TRUE.equals(updatedAccount.getIsDefault()) && !Boolean.TRUE.equals(existing.getIsDefault())) {
            clearDefaultFlag();
        }

        existing.setBankName(updatedAccount.getBankName());
        existing.setBankBranch(updatedAccount.getBankBranch());
        existing.setAccountNumber(updatedAccount.getAccountNumber());
        existing.setAccountName(updatedAccount.getAccountName());
        existing.setCurrencyCode(updatedAccount.getCurrencyCode());
        existing.setIsDefault(updatedAccount.getIsDefault());

        ChartOfAccount previousGl = existing.getGlAccount();
        ChartOfAccount newGl = updatedAccount.getGlAccount();
        existing.setGlAccount(newGl);

        CompanyBankAccount saved = bankAccountRepository.save(existing);

        if (previousGl != null && (newGl == null || !previousGl.getId().equals(newGl.getId()))) {
            chartOfAccountService.releaseBankLink(previousGl);
        }
        chartOfAccountService.applyBankLink(newGl);

        return saved;
    }

    @Transactional
    public void deactivate(UUID id) {
        CompanyBankAccount account = findById(id);
        account.setActive(false);
        account.setIsDefault(false);
        bankAccountRepository.save(account);
    }

    @Transactional
    public void activate(UUID id) {
        CompanyBankAccount account = findById(id);
        account.setActive(true);
        bankAccountRepository.save(account);
    }

    @Transactional
    public void setAsDefault(UUID id) {
        clearDefaultFlag();
        CompanyBankAccount account = findById(id);
        account.setIsDefault(true);
        account.setActive(true);
        bankAccountRepository.save(account);
    }

    @Transactional
    public void delete(UUID id) {
        CompanyBankAccount account = findById(id);
        ChartOfAccount glAccount = account.getGlAccount();
        bankAccountRepository.delete(account);
        bankAccountRepository.flush();
        chartOfAccountService.releaseBankLink(glAccount);
    }

    /**
     * account_number is stored through EncryptedStringConverter with a random IV, so an equality query
     * encrypts the parameter differently and never matches a stored row. Compare decrypted values instead.
     */
    private void rejectDuplicateAccountNumber(String accountNumber, UUID excludeId) {
        String normalized = normalizeAccountNumber(accountNumber);
        for (CompanyBankAccount other : bankAccountRepository.findAll()) {
            if (!other.getId().equals(excludeId) && normalizeAccountNumber(other.getAccountNumber()).equals(normalized)) {
                throw new IllegalArgumentException("Account number already exists: " + accountNumber);
            }
        }
    }

    private static String normalizeAccountNumber(String accountNumber) {
        return accountNumber.replaceAll("[\\s.\\-]", "");
    }

    private void clearDefaultFlag() {
        bankAccountRepository.findByIsDefaultTrueAndActiveTrue()
                .ifPresent(account -> {
                    account.setIsDefault(false);
                    bankAccountRepository.save(account);
                });
    }
}
