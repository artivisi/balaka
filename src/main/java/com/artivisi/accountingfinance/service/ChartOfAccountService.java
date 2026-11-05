package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.enums.AccountType;
import com.artivisi.accountingfinance.enums.CloseCheckRole;
import com.artivisi.accountingfinance.enums.FiscalTreatment;
import com.artivisi.accountingfinance.enums.NormalBalance;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Validated
@Transactional(readOnly = true)
public class ChartOfAccountService {

    private final ChartOfAccountRepository chartOfAccountRepository;
    private final JournalEntryRepository journalEntryRepository;

    public List<ChartOfAccount> findAll() {
        return chartOfAccountRepository.findByActiveOrderByAccountCodeAsc(true);
    }

    public List<ChartOfAccount> findAllIncludingInactive() {
        return chartOfAccountRepository.findAll();
    }

    public List<ChartOfAccount> findRootAccounts() {
        return chartOfAccountRepository.findByParentIsNullOrderByAccountCodeAsc();
    }

    public List<ChartOfAccount> findByParentId(UUID parentId) {
        return chartOfAccountRepository.findByParentIdOrderByAccountCodeAsc(parentId);
    }

    public List<ChartOfAccount> findByAccountType(AccountType accountType) {
        return chartOfAccountRepository.findByAccountTypeAndActiveOrderByAccountCodeAsc(accountType, true);
    }

    public Page<ChartOfAccount> findAll(Pageable pageable) {
        return chartOfAccountRepository.findByActiveOrderByAccountCodeAsc(true, pageable);
    }

    public Page<ChartOfAccount> findByAccountType(AccountType accountType, Pageable pageable) {
        return chartOfAccountRepository.findByAccountTypeAndActiveOrderByAccountCodeAsc(accountType, true, pageable);
    }

    public List<ChartOfAccount> findTransactableAccounts() {
        return chartOfAccountRepository.findAllTransactableAccounts();
    }

    public Page<ChartOfAccount> search(String search, Boolean active, Pageable pageable) {
        return chartOfAccountRepository.searchAccounts(search, !Boolean.FALSE.equals(active), pageable);
    }

    public ChartOfAccount findById(UUID id) {
        return chartOfAccountRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Account not found with id: " + id));
    }

    public boolean hasChildren(UUID id) {
        return chartOfAccountRepository.countByParentId(id) > 0;
    }

    public boolean hasParent(UUID id) {
        return chartOfAccountRepository.findById(id)
                .map(account -> account.getParent() != null)
                .orElse(false);
    }

    public boolean hasJournalEntries(UUID id) {
        return journalEntryRepository.existsByAccountId(id);
    }

    public ChartOfAccount findByAccountCode(String accountCode) {
        return chartOfAccountRepository.findByAccountCode(accountCode)
                .orElseThrow(() -> new EntityNotFoundException("Account not found with code: " + accountCode));
    }

    @Transactional
    public ChartOfAccount create(@Valid ChartOfAccount account) {
        if (chartOfAccountRepository.existsByAccountCode(account.getAccountCode())) {
            throw new IllegalArgumentException("Account code already exists: " + account.getAccountCode());
        }

        if (account.getParent() != null) {
            ChartOfAccount parent = findById(account.getParent().getId());
            account.setLevel(parent.getLevel() + 1);
            account.setAccountType(parent.getAccountType());
            account.setNormalBalance(parent.getNormalBalance());
        } else if (account.getNormalBalance() == null && account.getAccountType() != null) {
            account.setNormalBalance(deriveNormalBalance(account.getAccountType()));
        }
        validateFiscalTreatment(account.getFiscalTreatment(), account.getAccountType(), account.getIsHeader());
        validateServiceExpense(account.getServiceExpense(), account.getAccountType(), account.getIsHeader());
        validateCloseCheckRole(account.getCloseCheckRole(), account.getAccountType(), account.getIsHeader());

        return chartOfAccountRepository.save(account);
    }

    private void validateFiscalTreatment(FiscalTreatment treatment, AccountType type, Boolean isHeader) {
        if (treatment != null && (type != AccountType.EXPENSE || Boolean.TRUE.equals(isHeader))) {
            throw new IllegalArgumentException("Perlakuan fiskal hanya berlaku untuk akun beban yang bukan header");
        }
    }

    private void validateServiceExpense(Boolean serviceExpense, AccountType type, Boolean isHeader) {
        if (serviceExpense != null && (type != AccountType.EXPENSE || Boolean.TRUE.equals(isHeader))) {
            throw new IllegalArgumentException("Klasifikasi akun jasa hanya berlaku untuk akun beban yang bukan header");
        }
    }

    private void validateCloseCheckRole(CloseCheckRole role, AccountType type, Boolean isHeader) {
        if (role != null && (Boolean.TRUE.equals(isHeader) || !role.allows(type))) {
            throw new IllegalArgumentException("Peran pemeriksaan tutup buku " + role.getIndonesianName()
                    + " tidak berlaku untuk akun header atau akun bertipe " + type);
        }
    }

    private NormalBalance deriveNormalBalance(AccountType accountType) {
        return switch (accountType) {
            case ASSET, EXPENSE -> NormalBalance.DEBIT;
            case LIABILITY, EQUITY, REVENUE -> NormalBalance.CREDIT;
        };
    }

    @Transactional
    public ChartOfAccount update(UUID id, @Valid ChartOfAccount accountData) {
        ChartOfAccount existing = findById(id);

        if (!existing.getAccountCode().equals(accountData.getAccountCode()) &&
            chartOfAccountRepository.existsByAccountCode(accountData.getAccountCode())) {
            throw new IllegalArgumentException("Account code already exists: " + accountData.getAccountCode());
        }

        existing.setAccountCode(accountData.getAccountCode());
        existing.setAccountName(accountData.getAccountName());
        existing.setDescription(accountData.getDescription());
        existing.setPermanent(accountData.getPermanent());
        existing.setIsHeader(accountData.getIsHeader());
        existing.setActive(accountData.getActive());

        if (existing.getParent() == null) {
            // Check if account type is being changed
            if (existing.getAccountType() != accountData.getAccountType() && hasJournalEntries(id)) {
                throw new IllegalStateException("Cannot change account type: account has journal entries");
            }
            existing.setAccountType(accountData.getAccountType());
            existing.setNormalBalance(accountData.getNormalBalance());
        }
        validateFiscalTreatment(accountData.getFiscalTreatment(), existing.getAccountType(), existing.getIsHeader());
        existing.setFiscalTreatment(accountData.getFiscalTreatment());
        validateServiceExpense(accountData.getServiceExpense(), existing.getAccountType(), existing.getIsHeader());
        existing.setServiceExpense(accountData.getServiceExpense());
        validateCloseCheckRole(accountData.getCloseCheckRole(), existing.getAccountType(), existing.getIsHeader());
        existing.setCloseCheckRole(accountData.getCloseCheckRole());

        return chartOfAccountRepository.save(existing);
    }

    @Transactional
    public void activate(UUID id) {
        ChartOfAccount account = findById(id);
        account.setActive(true);
        chartOfAccountRepository.save(account);
    }

    @Transactional
    public void deactivate(UUID id) {
        ChartOfAccount account = findById(id);
        account.setActive(false);
        chartOfAccountRepository.save(account);
    }

    @Transactional
    public void delete(UUID id) {
        ChartOfAccount account = findById(id);
        if (!account.getChildren().isEmpty()) {
            throw new IllegalStateException("Cannot delete account with child accounts");
        }
        if (hasJournalEntries(id)) {
            throw new IllegalStateException("Cannot delete account with journal entries");
        }
        account.softDelete();
        chartOfAccountRepository.save(account);
    }
}
