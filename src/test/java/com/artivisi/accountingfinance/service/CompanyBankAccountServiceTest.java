package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.entity.CompanyBankAccount;
import com.artivisi.accountingfinance.repository.CompanyBankAccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Account numbers are stored encrypted (random IV), so uniqueness cannot be checked with an equality
 * query. Runs with an encryption key, as production does.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "app.encryption.key=Kea3OzaaPR89QOebS1b427C8xxRoztOft8rlSEL3W7k=")
@Transactional
@DisplayName("Company bank accounts with encrypted account numbers")
class CompanyBankAccountServiceTest {

    @Autowired
    private CompanyBankAccountService service;

    @Autowired
    private CompanyBankAccountRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("A 13-digit account number is stored as ciphertext and read back as entered")
    void storesCiphertext() {
        CompanyBankAccount saved = service.create(account("BCA", "7781234567890"));
        repository.flush();

        String stored = jdbcTemplate.queryForObject(
                "SELECT account_number FROM company_bank_accounts WHERE id = ?", String.class, saved.getId());
        assertThat(stored).startsWith("ENC:").doesNotContain("7781234567890");
        assertThat(service.findById(saved.getId()).getAccountNumber()).isEqualTo("7781234567890");
    }

    @Test
    @DisplayName("Creating an account whose number already exists, written with separators, is refused")
    void createRejectsDuplicate() {
        service.create(account("BCA", "7781234567891"));
        repository.flush();

        assertThatThrownBy(() -> service.create(account("BCA KCP", "778-123-456-7891")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account number already exists: 778-123-456-7891");
    }

    @Test
    @DisplayName("Updating to another account's number is refused; keeping its own number is allowed")
    void updateChecksOtherAccountsOnly() {
        service.create(account("BCA", "7781234567892"));
        CompanyBankAccount mandiri = service.create(account("Mandiri", "1230009876543"));
        repository.flush();

        assertThatThrownBy(() -> service.update(mandiri.getId(), account("Mandiri", "7781234567892")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account number already exists: 7781234567892");

        CompanyBankAccount renamed = service.update(mandiri.getId(), account("Mandiri Syariah", "1230009876543"));
        assertThat(renamed.getBankName()).isEqualTo("Mandiri Syariah");
        assertThat(renamed.getAccountNumber()).isEqualTo("1230009876543");
    }

    private static CompanyBankAccount account(String bankName, String accountNumber) {
        CompanyBankAccount account = new CompanyBankAccount();
        account.setBankName(bankName);
        account.setAccountNumber(accountNumber);
        account.setAccountName("PT Uji Coba");
        account.setCurrencyCode("IDR");
        account.setIsDefault(false);
        return account;
    }
}
