package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.enums.AccountType;
import com.artivisi.accountingfinance.enums.CloseCheckRole;
import com.artivisi.accountingfinance.enums.SelfTestRole;
import com.artivisi.accountingfinance.enums.FiscalTreatment;
import com.artivisi.accountingfinance.enums.CashBankSource;
import com.artivisi.accountingfinance.enums.NormalBalance;
import com.artivisi.accountingfinance.exception.InvalidCashBankAccountException;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.CompanyBankAccountRepository;
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
    private final CompanyBankAccountRepository companyBankAccountRepository;

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

    public List<ChartOfAccount> findAssetAccounts() {
        return chartOfAccountRepository.findAssetAccounts();
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
            if (Boolean.TRUE.equals(parent.getIsCashBank())) {
                throw new InvalidCashBankAccountException(
                        "Akun kas/bank " + parent.getAccountCode()
                                + " tidak boleh memiliki akun anak; hapus tanda kas/bank pada akun induk terlebih dahulu");
            }
            account.setLevel(parent.getLevel() + 1);
            account.setAccountType(parent.getAccountType());
            account.setNormalBalance(parent.getNormalBalance());
        } else if (account.getNormalBalance() == null && account.getAccountType() != null) {
            account.setNormalBalance(deriveNormalBalance(account.getAccountType()));
        }
        validateFiscalTreatment(account.getFiscalTreatment(), account.getAccountType(), account.getIsHeader());
        validateServiceExpense(account.getServiceExpense(), account.getAccountType(), account.getIsHeader());
        validateCloseCheckRole(account.getCloseCheckRole(), account.getAccountType(), account.getIsHeader());
        validateSelfTestRole(account.getSelfTestRole(), account.getAccountType(), account.getIsHeader());

        boolean cashBank = Boolean.TRUE.equals(account.getIsCashBank());
        validateCashBankEligibility(account.getAccountType(), Boolean.TRUE.equals(account.getIsHeader()),
                cashBank, false, account.getAccountCode());
        account.setIsCashBank(cashBank);
        account.setCashBankSource(cashBank ? CashBankSource.EXPLICIT : null);

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

    private void validateSelfTestRole(SelfTestRole role, AccountType type, Boolean isHeader) {
        if (role != null && (Boolean.TRUE.equals(isHeader) || !role.allows(type))) {
            throw new IllegalArgumentException("Peran uji mandiri pajak " + role.getIndonesianName()
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
        // Read the relationships before mutating the managed entity: a query afterwards
        // auto-flushes the half-built row and the database constraint fires before the
        // validation below can report a readable error.
        boolean hasChildAccounts = hasChildren(existing.getId());
        boolean linkedToBankAccount = isLinkedToBankAccount(existing.getId());

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
        validateSelfTestRole(accountData.getSelfTestRole(), existing.getAccountType(), existing.getIsHeader());
        existing.setSelfTestRole(accountData.getSelfTestRole());

        // isCashBank is part of the full-replace contract, like every other field of a
        // PUT: an omitted value means "not cash". Clearing a flag that a company bank
        // account depends on is still refused, so a link and its flag cannot diverge.
        applyExplicitCashBankFlag(existing, Boolean.TRUE.equals(accountData.getIsCashBank()),
                hasChildAccounts, linkedToBankAccount);

        // The caller may have changed type, header or children in the same request.
        // Validate the resulting state, not the pre-update one, so an account cannot be
        // saved into a state the chk_coa_cash_bank_asset constraint rejects at flush.
        if (Boolean.TRUE.equals(existing.getIsCashBank())) {
            validateCashBankEligibility(existing.getAccountType(), Boolean.TRUE.equals(existing.getIsHeader()),
                    true, hasChildAccounts, existing.getAccountCode());
        }

        return chartOfAccountRepository.save(existing);
    }

    /**
     * Marks an account as cash because a company bank account points at it.
     * Bank-linked accounts must be ASSET leaves; the caller is responsible for
     * persisting the bank account that establishes the link.
     */
    @Transactional
    public void applyBankLink(ChartOfAccount account) {
        if (account == null) {
            return;
        }
        validateCashBankEligibility(account.getAccountType(), Boolean.TRUE.equals(account.getIsHeader()),
                true, hasChildren(account.getId()), account.getAccountCode());

        if (!Boolean.TRUE.equals(account.getIsCashBank())) {
            account.setIsCashBank(true);
            account.setCashBankSource(CashBankSource.BANK_LINK);
            chartOfAccountRepository.save(account);
        } else if (account.getCashBankSource() == null) {
            // Legacy flag with no provenance (for example a canonical seed): keep
            // it durable so a later unlink cannot remove an existing cash account.
            account.setCashBankSource(CashBankSource.MIGRATED);
            chartOfAccountRepository.save(account);
        }
    }

    /**
     * Clears an automatically bank-linked cash flag when the last company bank
     * account stops pointing at the account. Explicit and migrated flags are kept.
     */
    @Transactional
    public void releaseBankLink(ChartOfAccount account) {
        if (account == null || !Boolean.TRUE.equals(account.getIsCashBank())) {
            return;
        }
        if (account.getCashBankSource() == CashBankSource.BANK_LINK
                && !companyBankAccountRepository.existsByGlAccount_Id(account.getId())) {
            account.setIsCashBank(false);
            account.setCashBankSource(null);
            chartOfAccountRepository.save(account);
        }
    }

    public boolean isLinkedToBankAccount(UUID id) {
        return companyBankAccountRepository.existsByGlAccount_Id(id);
    }

    private void applyExplicitCashBankFlag(ChartOfAccount existing, boolean desired,
                                           boolean hasChildAccounts, boolean linkedToBankAccount) {
        if (desired) {
            validateCashBankEligibility(existing.getAccountType(), Boolean.TRUE.equals(existing.getIsHeader()),
                    true, hasChildAccounts, existing.getAccountCode());
            if (!Boolean.TRUE.equals(existing.getIsCashBank())) {
                existing.setCashBankSource(CashBankSource.EXPLICIT);
            } else if (existing.getCashBankSource() == null) {
                existing.setCashBankSource(CashBankSource.MIGRATED);
            }
            existing.setIsCashBank(true);
            return;
        }

        if (Boolean.TRUE.equals(existing.getIsCashBank())) {
            if (linkedToBankAccount) {
                throw new InvalidCashBankAccountException(
                        "Akun " + existing.getAccountCode()
                                + " masih terhubung ke rekening bank; lepaskan tautan rekening terlebih dahulu");
            }
            existing.setIsCashBank(false);
            existing.setCashBankSource(null);
        }
    }

    private void validateCashBankEligibility(AccountType accountType, boolean isHeader, boolean cashBank,
                                             boolean hasChildren, String accountCode) {
        if (!cashBank) {
            return;
        }
        if (accountType != AccountType.ASSET) {
            throw new InvalidCashBankAccountException(
                    "Akun kas/bank " + accountCode + " harus bertipe ASSET");
        }
        if (isHeader) {
            throw new InvalidCashBankAccountException(
                    "Akun header " + accountCode + " tidak boleh ditandai sebagai kas/bank");
        }
        if (hasChildren) {
            throw new InvalidCashBankAccountException(
                    "Akun kas/bank " + accountCode + " harus berupa akun leaf (tanpa akun anak)");
        }
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
        if (isLinkedToBankAccount(id)) {
            throw new IllegalStateException(
                    "Cannot deactivate account linked to a company bank account; unlink the bank account first");
        }
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
        if (isLinkedToBankAccount(id)) {
            throw new IllegalStateException(
                    "Cannot delete account linked to a company bank account; unlink the bank account first");
        }
        account.softDelete();
        chartOfAccountRepository.save(account);
    }
}
