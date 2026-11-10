package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.CompanyBankAccount;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.AccountType;
import com.artivisi.accountingfinance.enums.CashBankSource;
import com.artivisi.accountingfinance.enums.NormalBalance;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.exception.InvalidCashBankAccountException;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.CompanyBankAccountRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FAS-87: the explicit cash/bank flag must be maintained at runtime, not only
 * backfilled by a migration. Linking a company bank account flags its ASSET
 * leaf; unlinking releases only automatically derived flags; explicit Finance
 * Controller decisions and ZIP round-trips are durable; non-cash account types
 * and headers can never be flagged.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("Cash/bank flag maintenance (FAS-87)")
class CashBankFlagMaintenanceTest {

    private static final LocalDate AS_OF = LocalDate.of(2024, 6, 30);

    @Autowired
    private ChartOfAccountService chartOfAccountService;

    @Autowired
    private CompanyBankAccountService bankAccountService;

    @Autowired
    private ChartOfAccountRepository chartOfAccountRepository;

    @Autowired
    private CompanyBankAccountRepository bankAccountRepository;

    @Autowired
    private DataExportService dataExportService;

    @Autowired
    private DataImportService dataImportService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Autowired
    private JournalTemplateRepository journalTemplateRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    @DisplayName("linking a new non-canonical ASSET leaf makes it cash and the dashboard includes it")
    void linkingNewAssetLeafAddsItToTheCashUniverse() {
        ChartOfAccount leaf = createAssetLeaf("1.1.90", "Bank Mandiri Operasional");
        assertThat(leaf.getIsCashBank()).isFalse();

        CompanyBankAccount bank = createBankAccount("9000000090", leaf);

        ChartOfAccount linked = chartOfAccountRepository.findByAccountCode("1.1.90").orElseThrow();
        assertThat(linked.getIsCashBank()).isTrue();
        assertThat(linked.getCashBankSource()).isEqualTo(CashBankSource.BANK_LINK);

        assertThat(chartOfAccountRepository.findCashBankAccounts())
                .extracting(ChartOfAccount::getAccountCode)
                .contains("1.1.90");

        BigDecimal before = dashboardService.calculateCashBalance(AS_OF);
        postDebit(leaf, new BigDecimal("1250000.00"), "FAS-87 linked account balance");
        BigDecimal after = dashboardService.calculateCashBalance(AS_OF);

        assertThat(after).isEqualTo(before.add(new BigDecimal("1250000.00")));
    }

    @Test
    @DisplayName("reassigning a bank account releases the previous automatically flagged account")
    void reassigningBankAccountReleasesPreviousBankLinkedFlag() {
        ChartOfAccount first = createAssetLeaf("1.1.91", "Bank Lama");
        ChartOfAccount second = createAssetLeaf("1.1.92", "Bank Baru");
        CompanyBankAccount bank = createBankAccount("9000000091", first);

        CompanyBankAccount updated = new CompanyBankAccount();
        updated.setBankName(bank.getBankName());
        updated.setAccountNumber(bank.getAccountNumber());
        updated.setAccountName(bank.getAccountName());
        updated.setCurrencyCode("IDR");
        updated.setIsDefault(false);
        updated.setGlAccount(second);
        bankAccountService.update(bank.getId(), updated);

        ChartOfAccount released = chartOfAccountRepository.findByAccountCode("1.1.91").orElseThrow();
        assertThat(released.getIsCashBank()).isFalse();
        assertThat(released.getCashBankSource()).isNull();

        ChartOfAccount linked = chartOfAccountRepository.findByAccountCode("1.1.92").orElseThrow();
        assertThat(linked.getIsCashBank()).isTrue();
        assertThat(linked.getCashBankSource()).isEqualTo(CashBankSource.BANK_LINK);
    }

    @Test
    @DisplayName("deleting the bank account releases the automatically flagged account")
    void deletingBankAccountReleasesBankLinkedFlag() {
        ChartOfAccount leaf = createAssetLeaf("1.1.93", "Bank Sementara");
        CompanyBankAccount bank = createBankAccount("9000000093", leaf);

        bankAccountService.delete(bank.getId());

        ChartOfAccount released = chartOfAccountRepository.findByAccountCode("1.1.93").orElseThrow();
        assertThat(released.getIsCashBank()).isFalse();
        assertThat(released.getCashBankSource()).isNull();
    }

    @Test
    @DisplayName("an explicit cash flag survives unlinking and is audited as explicit")
    void explicitFlagSurvivesUnlink() {
        ChartOfAccount explicit = createAssetLeaf("1.1.94", "Kas Kecil", true);

        assertThat(explicit.getCashBankSource()).isEqualTo(CashBankSource.EXPLICIT);

        CompanyBankAccount bank = createBankAccount("9000000094", explicit);
        bankAccountService.delete(bank.getId());

        ChartOfAccount reloaded = chartOfAccountRepository.findByAccountCode("1.1.94").orElseThrow();
        assertThat(reloaded.getIsCashBank()).isTrue();
        assertThat(reloaded.getCashBankSource()).isEqualTo(CashBankSource.EXPLICIT);
    }

    @Test
    @DisplayName("a bank-linked account cannot be un-flagged while the bank link exists")
    void bankLinkedAccountCannotBeUnflaggedDirectly() {
        ChartOfAccount leaf = createAssetLeaf("1.1.95", "Bank Terkait");
        createBankAccount("9000000095", leaf);

        ChartOfAccount linked = chartOfAccountService.findByAccountCode("1.1.95");

        assertThatThrownBy(() -> chartOfAccountService.update(linked.getId(), detachedCopy(linked, false)))
                .isInstanceOf(InvalidCashBankAccountException.class);
    }

    @Test
    @DisplayName("non-ASSET and header accounts cannot become cash")
    void nonAssetAndHeaderAccountsCannotBeFlagged() {
        ChartOfAccount liability = new ChartOfAccount();
        liability.setAccountCode("2.1.90");
        liability.setAccountName("Utang Uji");
        liability.setAccountType(AccountType.LIABILITY);
        liability.setNormalBalance(NormalBalance.CREDIT);
        liability.setIsHeader(false);
        liability.setActive(true);
        liability.setIsCashBank(true);

        assertThatThrownBy(() -> chartOfAccountService.create(liability))
                .isInstanceOf(InvalidCashBankAccountException.class);

        ChartOfAccount header = new ChartOfAccount();
        header.setAccountCode("1.1.96");
        header.setAccountName("Header Uji");
        header.setAccountType(AccountType.ASSET);
        header.setNormalBalance(NormalBalance.DEBIT);
        header.setIsHeader(true);
        header.setActive(true);
        header.setIsCashBank(true);

        assertThatThrownBy(() -> chartOfAccountService.create(header))
                .isInstanceOf(InvalidCashBankAccountException.class);
    }

    @Test
    @DisplayName("an update that omits isCashBank (REST API) keeps an explicit cash flag")
    void omittedCashFlagOnUpdateKeepsExplicitFlag() {
        ChartOfAccount explicit = createAssetLeaf("1.1.98", "Kas API", true);

        ChartOfAccount partial = detachedCopy(explicit, false);
        partial.setIsCashBank(null);
        chartOfAccountService.update(explicit.getId(), partial);

        ChartOfAccount reloaded = chartOfAccountRepository.findByAccountCode("1.1.98").orElseThrow();
        assertThat(reloaded.getIsCashBank()).isTrue();
        assertThat(reloaded.getCashBankSource()).isEqualTo(CashBankSource.EXPLICIT);
    }

    @Test
    @DisplayName("an update that omits isCashBank (REST API) keeps a bank-linked flag")
    void omittedCashFlagOnUpdateKeepsBankLink() {
        ChartOfAccount leaf = createAssetLeaf("1.1.99", "Bank API");
        createBankAccount("9000000099", leaf);

        ChartOfAccount linked = chartOfAccountRepository.findByAccountCode("1.1.99").orElseThrow();
        ChartOfAccount partial = detachedCopy(linked, false);
        partial.setIsCashBank(null);
        chartOfAccountService.update(linked.getId(), partial);

        ChartOfAccount reloaded = chartOfAccountRepository.findByAccountCode("1.1.99").orElseThrow();
        assertThat(reloaded.getIsCashBank()).isTrue();
        assertThat(reloaded.getCashBankSource()).isEqualTo(CashBankSource.BANK_LINK);
    }

    @Test
    @DisplayName("chart-of-accounts CSV export/import round-trip preserves the explicit cash flag")
    void csvRoundTripPreservesCashFlag() throws IOException {
        createAssetLeaf("1.1.97", "Bank Ekspor", true);

        byte[] exported = dataExportService.exportAllData();

        ChartOfAccount toClear = chartOfAccountService.findByAccountCode("1.1.97");
        chartOfAccountService.update(toClear.getId(), detachedCopy(toClear, false));
        assertThat(chartOfAccountRepository.findByAccountCode("1.1.97").orElseThrow().getIsCashBank()).isFalse();

        dataImportService.importAllData(exported);

        ChartOfAccount reimported = chartOfAccountRepository.findByAccountCode("1.1.97").orElseThrow();
        assertThat(reimported.getIsCashBank()).isTrue();
        assertThat(reimported.getCashBankSource()).isEqualTo(CashBankSource.EXPLICIT);
    }

    @Test
    @DisplayName("legacy seven-column seed import still flags the three canonical cash accounts")
    void legacySeedStillFlagsCanonicalCashAccounts() throws IOException {
        String csv = "account_code,account_name,account_type,parent_code,normal_balance,active,is_permanent\n"
                + "1.1,Aset Lancar,ASSET,,DEBIT,true,true\n"
                + "1.1.01,Kas,ASSET,1.1,DEBIT,true,false\n"
                + "1.1.02,Bank BCA,ASSET,1.1,DEBIT,true,false\n"
                + "1.1.04,Piutang Usaha,ASSET,1.1,DEBIT,true,false\n";

        dataImportService.importAllData(buildZipWithFiles("02_chart_of_accounts.csv", csv));

        assertThat(chartOfAccountRepository.findByAccountCode("1.1.01").orElseThrow().getIsCashBank()).isTrue();
        assertThat(chartOfAccountRepository.findByAccountCode("1.1.02").orElseThrow().getIsCashBank()).isTrue();
        assertThat(chartOfAccountRepository.findByAccountCode("1.1.04").orElseThrow().getIsCashBank()).isFalse();
        assertThat(chartOfAccountRepository.findCashBankAccounts())
                .extracting(ChartOfAccount::getAccountCode)
                .containsExactly("1.1.01", "1.1.02");
    }

    @Test
    @DisplayName("import of a flagged non-ASSET row is rejected, not silently dropped")
    void importRejectsFlaggedNonAssetRow() {
        String csv = "account_code,account_name,account_type,parent_code,normal_balance,active,is_permanent,"
                + "fiscal_treatment,service_expense,close_check_role,self_test_role,is_cash_bank\n"
                + "2.1.91,Utang Salah,LIABILITY,,CREDIT,true,true,,,,,true\n";

        assertThatThrownBy(() -> dataImportService.importAllData(
                buildZipWithFiles("02_chart_of_accounts.csv", csv)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is_cash_bank requires a non-header ASSET account");
    }

    private ChartOfAccount createAssetLeaf(String code, String name) {
        return createAssetLeaf(code, name, false);
    }

    private ChartOfAccount createAssetLeaf(String code, String name, boolean cashBank) {
        ChartOfAccount account = new ChartOfAccount();
        account.setAccountCode(code);
        account.setAccountName(name);
        account.setAccountType(AccountType.ASSET);
        account.setNormalBalance(NormalBalance.DEBIT);
        account.setIsHeader(false);
        account.setActive(true);
        account.setPermanent(true);
        account.setIsCashBank(cashBank);
        return chartOfAccountService.create(account);
    }

    private ChartOfAccount detachedCopy(ChartOfAccount source, boolean cashBank) {
        ChartOfAccount copy = new ChartOfAccount();
        copy.setAccountCode(source.getAccountCode());
        copy.setAccountName(source.getAccountName());
        copy.setAccountType(source.getAccountType());
        copy.setNormalBalance(source.getNormalBalance());
        copy.setIsHeader(source.getIsHeader());
        copy.setActive(source.getActive());
        copy.setPermanent(source.getPermanent());
        copy.setIsCashBank(cashBank);
        return copy;
    }

    private CompanyBankAccount createBankAccount(String number, ChartOfAccount glAccount) {
        CompanyBankAccount bank = new CompanyBankAccount();
        bank.setBankName("Bank Uji " + number);
        bank.setAccountNumber(number);
        bank.setAccountName("PT FASTERNET Uji");
        bank.setCurrencyCode("IDR");
        bank.setIsDefault(false);
        bank.setGlAccount(glAccount);
        return bankAccountService.create(bank);
    }

    private void postDebit(ChartOfAccount account, BigDecimal amount, String description) {
        ChartOfAccount revenue = chartOfAccountRepository.findByAccountCode("4.1.01").orElseThrow();
        JournalTemplate template = journalTemplateRepository.findAll().stream().findFirst().orElseThrow();
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        Transaction transaction = new Transaction();
        transaction.setTransactionNumber("TRX-FAS87-" + suffix);
        transaction.setTransactionDate(LocalDate.of(2024, 6, 15));
        transaction.setJournalTemplate(template);
        transaction.setAmount(amount);
        transaction.setDescription(description);
        transaction.setStatus(TransactionStatus.POSTED);
        transaction = transactionRepository.save(transaction);

        JournalEntry debit = new JournalEntry();
        debit.setJournalNumber("JRN-FAS87-D-" + suffix);
        debit.setTransaction(transaction);
        debit.setAccount(account);
        debit.setDebitAmount(amount);
        debit.setCreditAmount(BigDecimal.ZERO);
        debit.setLineOrder(1);

        JournalEntry credit = new JournalEntry();
        credit.setJournalNumber("JRN-FAS87-C-" + suffix);
        credit.setTransaction(transaction);
        credit.setAccount(revenue);
        credit.setDebitAmount(BigDecimal.ZERO);
        credit.setCreditAmount(amount);
        credit.setLineOrder(2);

        journalEntryRepository.saveAll(List.of(debit, credit));
    }

    private byte[] buildZipWithFiles(String filename, String content) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(filename));
            zos.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
}
