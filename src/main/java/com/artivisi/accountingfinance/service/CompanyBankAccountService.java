package com.artivisi.accountingfinance.service;

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
        if (bankAccountRepository.existsByAccountNumber(bankAccount.getAccountNumber())) {
            throw new IllegalArgumentException("Account number already exists: " + bankAccount.getAccountNumber());
        }

        // If this is the first account or marked as default, handle default flag
        if (Boolean.TRUE.equals(bankAccount.getIsDefault())) {
            clearDefaultFlag();
        }

        bankAccount.setActive(true);
        return bankAccountRepository.save(bankAccount);
    }

    @Transactional
    public CompanyBankAccount update(UUID id, CompanyBankAccount updatedAccount) {
        CompanyBankAccount existing = findById(id);

        // Check if account number is being changed and already exists
        if (!existing.getAccountNumber().equals(updatedAccount.getAccountNumber()) &&
                bankAccountRepository.existsByAccountNumber(updatedAccount.getAccountNumber())) {
            throw new IllegalArgumentException("Account number already exists: " + updatedAccount.getAccountNumber());
        }

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

        return bankAccountRepository.save(existing);
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
        bankAccountRepository.delete(account);
    }

    private void clearDefaultFlag() {
        bankAccountRepository.findByIsDefaultTrueAndActiveTrue()
                .ifPresent(account -> {
                    account.setIsDefault(false);
                    bankAccountRepository.save(account);
                });
    }
}
