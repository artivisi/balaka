package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.CompanyBankAccount;
import com.artivisi.accountingfinance.entity.CompanyConfig;
import com.artivisi.accountingfinance.entity.SecurityAuditLog;
import com.artivisi.accountingfinance.enums.AuditEventType;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.CompanyBankAccountRepository;
import com.artivisi.accountingfinance.repository.CompanyConfigRepository;
import com.artivisi.accountingfinance.service.CompanyBankAccountService;
import com.artivisi.accountingfinance.service.CompanyConfigService;
import com.artivisi.accountingfinance.service.SecurityAuditService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CompanyConfigService, CompanyBankAccountService and SecurityAuditService persistence.
 * UI flows for the same features are in SettingsControllerFunctionalTest and
 * ServiceSettingsComprehensiveTest. Company config and the default bank account are
 * shared by every test class, so both are restored after each test.
 */
@DisplayName("Settings - Persistence Tests")
@Import(ServiceTestDataInitializer.class)
class SettingsPersistenceTest extends PlaywrightTestBase {

    @Autowired private CompanyConfigService companyConfigService;
    @Autowired private CompanyConfigRepository companyConfigRepository;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private CompanyBankAccountService bankAccountService;
    @Autowired private CompanyBankAccountRepository bankAccountRepository;
    @Autowired private SecurityAuditService securityAuditService;

    private final List<UUID> createdAccounts = new ArrayList<>();
    private CompanyConfigSnapshot originalConfig;
    private Optional<UUID> originalDefault;

    @BeforeEach
    void captureSharedSettings() {
        originalConfig = CompanyConfigSnapshot.capture(companyConfigRepository);
        originalDefault = bankAccountRepository.findByIsDefaultTrueAndActiveTrue().map(CompanyBankAccount::getId);
    }

    @AfterEach
    void restoreSharedSettings() {
        createdAccounts.stream().filter(bankAccountRepository::existsById).forEach(bankAccountService::delete);
        originalDefault.ifPresent(bankAccountService::setAsDefault);
        originalConfig.restore(companyConfigRepository, accountRepository);
    }

    // ==================== Company config ====================

    @Test
    @DisplayName("Should return the single stored company config without creating another row")
    void shouldReturnSingletonCompanyConfig() {
        CompanyConfig first = companyConfigService.getConfig();
        CompanyConfig second = companyConfigService.getConfig();

        assertThat(companyConfigRepository.count()).isEqualTo(1);
        assertThat(first.getId()).isEqualTo(originalConfig.id()).isEqualTo(second.getId());
        assertThat(first.getCompanyName()).isEqualTo(originalConfig.companyName());
        assertThat(first.getNpwp()).isEqualTo(originalConfig.npwp());
    }

    @Test
    @DisplayName("Should persist every editable company field and leave the logo and PKP data alone")
    void shouldUpdateCompanyConfig() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        CompanyConfig changes = new CompanyConfig();
        changes.setCompanyName("PT Persisten " + suffix);
        changes.setCompanyAddress("Jl. Persisten " + suffix);
        changes.setCompanyPhone("021-" + suffix);
        changes.setCompanyEmail(suffix + "@example.com");
        changes.setTaxId("TAX-" + suffix);
        changes.setNpwp("02.345.678.9-012.000");
        changes.setNitku("0234567890120000000001");
        changes.setFiscalYearStartMonth(4);
        changes.setCurrencyCode("SGD");
        changes.setSigningOfficerName("Direktur " + suffix);
        changes.setSigningOfficerTitle("Direktur Utama");
        changes.setIndustry("online-seller");
        changes.setReceivableAccount(accountRepository.findById(originalConfig.receivableAccountId()).orElseThrow());
        CompanyConfig withLogo = companyConfigRepository.findById(originalConfig.id()).orElseThrow();
        withLogo.setCompanyLogoPath("logos/persisten-" + suffix + ".png");
        companyConfigRepository.save(withLogo);

        companyConfigService.update(originalConfig.id(), changes);

        CompanyConfig saved = companyConfigRepository.findById(originalConfig.id()).orElseThrow();
        assertThat(saved.getCompanyName()).isEqualTo("PT Persisten " + suffix);
        assertThat(saved.getCompanyAddress()).isEqualTo("Jl. Persisten " + suffix);
        assertThat(saved.getCompanyPhone()).isEqualTo("021-" + suffix);
        assertThat(saved.getCompanyEmail()).isEqualTo(suffix + "@example.com");
        assertThat(saved.getTaxId()).isEqualTo("TAX-" + suffix);
        assertThat(saved.getNpwp()).isEqualTo("02.345.678.9-012.000");
        assertThat(saved.getNitku()).isEqualTo("0234567890120000000001");
        assertThat(saved.getFiscalYearStartMonth()).isEqualTo(4);
        assertThat(saved.getCurrencyCode()).isEqualTo("SGD");
        assertThat(saved.getSigningOfficerName()).isEqualTo("Direktur " + suffix);
        assertThat(saved.getSigningOfficerTitle()).isEqualTo("Direktur Utama");
        assertThat(saved.getIndustry()).isEqualTo("online-seller");
        assertThat(saved.getReceivableAccountId()).isEqualTo(originalConfig.receivableAccountId());
        assertThat(saved.getPayableAccountId()).isNull();
        assertThat(saved.getCompanyLogoPath()).isEqualTo("logos/persisten-" + suffix + ".png");
        assertThat(saved.getIsPkp()).isEqualTo(originalConfig.isPkp());
        assertThat(saved.getEstablishedDate()).isEqualTo(originalConfig.establishedDate());
        assertThat(companyConfigRepository.count()).isEqualTo(1);
    }

    // ==================== Bank accounts ====================

    @Test
    @DisplayName("Should create an active bank account and store every field")
    void shouldCreateBankAccount() {
        CompanyBankAccount account = newAccount("Bank Persisten");
        account.setBankBranch("001");
        account.setCurrencyCode("USD");
        account.setActive(false);

        CompanyBankAccount saved = create(account);

        CompanyBankAccount fromDb = bankAccountRepository.findById(saved.getId()).orElseThrow();
        assertThat(fromDb.getBankName()).isEqualTo("Bank Persisten");
        assertThat(fromDb.getAccountNumber()).isEqualTo(account.getAccountNumber());
        assertThat(fromDb.getAccountName()).isEqualTo("PT Persisten");
        assertThat(fromDb.getBankBranch()).isEqualTo("001");
        assertThat(fromDb.getCurrencyCode()).isEqualTo("USD");
        assertThat(fromDb.isActive()).isTrue();
        assertThat(fromDb.isDefaultAccount()).isFalse();
    }

    @Test
    @DisplayName("Should update bank account fields")
    void shouldUpdateBankAccount() {
        CompanyBankAccount saved = create(newAccount("Bank Awal"));
        CompanyBankAccount changes = newAccount("Bank Diubah");
        changes.setAccountNumber(saved.getAccountNumber());
        changes.setAccountName("PT Diubah");
        changes.setBankBranch("002");

        bankAccountService.update(saved.getId(), changes);

        CompanyBankAccount fromDb = bankAccountRepository.findById(saved.getId()).orElseThrow();
        assertThat(fromDb.getBankName()).isEqualTo("Bank Diubah");
        assertThat(fromDb.getAccountName()).isEqualTo("PT Diubah");
        assertThat(fromDb.getBankBranch()).isEqualTo("002");
    }

    @Test
    @DisplayName("Should reject a duplicate account number on create and on update")
    void shouldRejectDuplicateAccountNumber() {
        CompanyBankAccount first = create(newAccount("Bank Pertama"));
        CompanyBankAccount second = create(newAccount("Bank Kedua"));

        CompanyBankAccount duplicate = newAccount("Bank Duplikat");
        duplicate.setAccountNumber(first.getAccountNumber());
        assertThatThrownBy(() -> bankAccountService.create(duplicate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account number already exists: " + first.getAccountNumber());

        CompanyBankAccount renumber = newAccount("Bank Kedua");
        renumber.setAccountNumber(first.getAccountNumber());
        assertThatThrownBy(() -> bankAccountService.update(second.getId(), renumber))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account number already exists: " + first.getAccountNumber());

        assertThat(bankAccountRepository.findById(second.getId()).orElseThrow().getAccountNumber())
                .isEqualTo(second.getAccountNumber());
        assertThat(bankAccountRepository.findAll()).filteredOn(a -> first.getAccountNumber().equals(a.getAccountNumber()))
                .hasSize(1);
    }

    @Test
    @DisplayName("Should delete a bank account")
    void shouldDeleteBankAccount() {
        CompanyBankAccount saved = create(newAccount("Bank Dihapus"));

        bankAccountService.delete(saved.getId());

        assertThat(bankAccountRepository.findById(saved.getId())).isEmpty();
        assertThat(bankAccountService.findAll()).extracting(CompanyBankAccount::getId).doesNotContain(saved.getId());
    }

    @Test
    @DisplayName("Should keep a single default account when another account becomes default")
    void shouldSetDefaultBankAccount() {
        CompanyBankAccount first = create(newAccount("Bank Default Satu"));
        CompanyBankAccount second = create(newAccount("Bank Default Dua"));

        bankAccountService.setAsDefault(first.getId());
        assertThat(bankAccountService.findDefaultAccount()).map(CompanyBankAccount::getId).contains(first.getId());
        originalDefault.ifPresent(id ->
                assertThat(bankAccountRepository.findById(id).orElseThrow().isDefaultAccount()).isFalse());

        bankAccountService.setAsDefault(second.getId());
        assertThat(bankAccountService.findDefaultAccount()).map(CompanyBankAccount::getId).contains(second.getId());
        assertThat(bankAccountRepository.findById(first.getId()).orElseThrow().isDefaultAccount()).isFalse();
    }

    @Test
    @DisplayName("Should clear the default flag when a new account is created as default")
    void shouldCreateDefaultBankAccount() {
        CompanyBankAccount first = create(newAccount("Bank Default Lama"));
        bankAccountService.setAsDefault(first.getId());
        CompanyBankAccount second = newAccount("Bank Default Baru");
        second.setIsDefault(true);

        CompanyBankAccount saved = create(second);

        assertThat(bankAccountService.findDefaultAccount()).map(CompanyBankAccount::getId).contains(saved.getId());
        assertThat(bankAccountRepository.findById(first.getId()).orElseThrow().isDefaultAccount()).isFalse();
    }

    @Test
    @DisplayName("Should deactivate an account, dropping its default flag and hiding it from active accounts, then reactivate it")
    void shouldDeactivateAndActivateBankAccount() {
        CompanyBankAccount saved = create(newAccount("Bank Status"));
        bankAccountService.setAsDefault(saved.getId());

        bankAccountService.deactivate(saved.getId());

        CompanyBankAccount inactive = bankAccountRepository.findById(saved.getId()).orElseThrow();
        assertThat(inactive.isActive()).isFalse();
        assertThat(inactive.isDefaultAccount()).isFalse();
        assertThat(bankAccountService.findActive()).extracting(CompanyBankAccount::getId).doesNotContain(saved.getId());
        assertThat(bankAccountService.findDefaultAccount()).isEmpty();

        bankAccountService.activate(saved.getId());

        CompanyBankAccount active = bankAccountRepository.findById(saved.getId()).orElseThrow();
        assertThat(active.isActive()).isTrue();
        assertThat(active.isDefaultAccount()).isFalse();
        assertThat(bankAccountService.findActive()).extracting(CompanyBankAccount::getId).contains(saved.getId());
    }

    @Test
    @DisplayName("Should list all bank accounts ordered by bank name, including inactive ones")
    void shouldListBankAccountsByBankName() {
        String prefix = "ZZ Urut " + UUID.randomUUID().toString().substring(0, 6);
        CompanyBankAccount b = create(newAccount(prefix + " B"));
        CompanyBankAccount a = create(newAccount(prefix + " A"));
        bankAccountService.deactivate(b.getId());

        List<UUID> ours = bankAccountService.findAll().stream()
                .filter(acc -> acc.getBankName().startsWith(prefix))
                .map(CompanyBankAccount::getId)
                .toList();

        assertThat(ours).containsExactly(a.getId(), b.getId());
    }

    // ==================== Audit log ====================

    @Test
    @DisplayName("Should find login audit events by username, event type and time range")
    void shouldSearchAuditLogs() {
        String username = "stp-" + UUID.randomUUID().toString().substring(0, 8);
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        securityAuditService.logLogin(username, true, "Login dari pengujian");
        securityAuditService.logLogin(username, false, "Password salah");
        LocalDateTime after = LocalDateTime.now().plusSeconds(1);
        PageRequest page = PageRequest.of(0, 10);

        Page<SecurityAuditLog> all = securityAuditService.search(null, username, null, null, page);
        assertThat(all.getContent()).extracting(SecurityAuditLog::getEventType)
                .containsExactlyInAnyOrder(AuditEventType.LOGIN_SUCCESS, AuditEventType.LOGIN_FAILURE);

        Page<SecurityAuditLog> failures = securityAuditService.search(
                AuditEventType.LOGIN_FAILURE, username, null, null, page);
        assertThat(failures.getContent()).singleElement().satisfies(log -> {
            assertThat(log.getUsername()).isEqualTo(username);
            assertThat(log.getDetails()).isEqualTo("Password salah");
            assertThat(log.getSuccess()).isFalse();
        });

        assertThat(securityAuditService.search(null, username, before, after, page).getTotalElements()).isEqualTo(2);
        assertThat(securityAuditService.search(null, username, after, null, page).getTotalElements()).isZero();
        assertThat(securityAuditService.search(AuditEventType.SETTINGS_CHANGE, username, null, null, page)
                .getTotalElements()).isZero();
    }

    // ==================== helpers ====================

    private CompanyBankAccount newAccount(String bankName) {
        CompanyBankAccount account = new CompanyBankAccount();
        account.setBankName(bankName);
        account.setAccountNumber(String.valueOf(System.nanoTime()));
        account.setAccountName("PT Persisten");
        return account;
    }

    private CompanyBankAccount create(CompanyBankAccount account) {
        CompanyBankAccount saved = bankAccountService.create(account);
        createdAccounts.add(saved.getId());
        return saved;
    }
}
