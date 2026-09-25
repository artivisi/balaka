package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Integration tests for FiscalYearClosingService. Every test posts its own journals into a year
 * no other integration test uses ({@link #YEAR} profit, {@link #LOSS_YEAR} loss) and rolls back.
 *
 * Profit year: revenue 4.1.01 100M + 4.1.02 20M, expense 5.1.01 30M + 5.1.02 5M, net income 85M.
 * Loss year: revenue 4.1.01 10M, expense 5.1.01 25M, net loss 15M.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "admin")
@DisplayName("Fiscal Year Closing Service Integration Tests")
class FiscalYearClosingServiceTest {

    private static final int YEAR = 2068;
    private static final int LOSS_YEAR = 2072;
    private static final int EMPTY_YEAR = 2098;
    private static final UUID TEMPLATE_ID = UUID.fromString("e0000000-0000-0000-0000-000000000001");

    @Autowired private FiscalYearClosingService fiscalYearClosingService;
    @Autowired private ReportService reportService;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private JournalEntryRepository journalEntryRepository;
    @Autowired private JournalTemplateRepository templateRepository;
    @Autowired private ChartOfAccountRepository chartOfAccountRepository;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("Preview closes each revenue and expense account into Laba Berjalan and moves net profit to Laba Ditahan")
    void shouldPreviewProfitClosing() {
        postProfitYear();

        FiscalYearClosingService.ClosingPreview preview = fiscalYearClosingService.previewClosing(YEAR);

        assertThat(preview.year()).isEqualTo(YEAR);
        assertThat(preview.totalRevenue()).isEqualByComparingTo("120000000");
        assertThat(preview.totalExpense()).isEqualByComparingTo("35000000");
        assertThat(preview.netIncome()).isEqualByComparingTo("85000000");
        assertThat(preview.alreadyClosed()).isFalse();
        assertThat(preview.entries()).extracting(FiscalYearClosingService.ClosingEntryPreview::referenceNumber,
                        FiscalYearClosingService.ClosingEntryPreview::date)
                .containsExactly(
                        tuple("CLOSING-" + YEAR + "-01", LocalDate.of(YEAR, 12, 31)),
                        tuple("CLOSING-" + YEAR + "-02", LocalDate.of(YEAR, 12, 31)),
                        tuple("CLOSING-" + YEAR + "-03", LocalDate.of(YEAR, 12, 31)));

        assertLines(preview.entries().get(0).lines(),
                line("4.1.01", "100000000", "0"),
                line("4.1.02", "20000000", "0"),
                line("3.2.02", "0", "120000000"));
        assertLines(preview.entries().get(1).lines(),
                line("5.1.01", "0", "30000000"),
                line("5.1.02", "0", "5000000"),
                line("3.2.02", "35000000", "0"));
        assertLines(preview.entries().get(2).lines(),
                line("3.2.02", "85000000", "0"),
                line("3.2.01", "0", "85000000"));
        assertThat(preview.entries().get(0).lines().getFirst().memo()).isEqualTo("Tutup Pendapatan Jasa Konsultasi");
        assertThat(preview.entries().get(2).lines().getFirst().memo()).isEqualTo("Laba Bersih Tahun " + YEAR);

        assertThat(journalEntryRepository.findByReferenceNumberLike("CLOSING-" + YEAR + "-%")).isEmpty();
    }

    @Test
    @DisplayName("Preview of a loss year debits Laba Ditahan with the net loss")
    void shouldPreviewLossClosing() {
        post(LOSS_YEAR, "1.1.02", "4.1.01", "10000000");
        post(LOSS_YEAR, "5.1.01", "1.1.02", "25000000");

        FiscalYearClosingService.ClosingPreview preview = fiscalYearClosingService.previewClosing(LOSS_YEAR);

        assertThat(preview.netIncome()).isEqualByComparingTo("-15000000");
        assertThat(preview.entries()).hasSize(3);
        assertLines(preview.entries().get(2).lines(),
                line("3.2.02", "0", "15000000"),
                line("3.2.01", "15000000", "0"));
        assertThat(preview.entries().get(2).lines().getFirst().memo()).isEqualTo("Rugi Bersih Tahun " + LOSS_YEAR);
    }

    @Test
    @DisplayName("A year without revenue or expense previews and executes to nothing")
    void shouldCloseEmptyYearToNothing() {
        FiscalYearClosingService.ClosingPreview preview = fiscalYearClosingService.previewClosing(EMPTY_YEAR);

        assertThat(preview.totalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(preview.totalExpense()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(preview.netIncome()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(preview.entries()).isEmpty();

        assertThat(fiscalYearClosingService.executeClosing(EMPTY_YEAR)).isEmpty();
        assertThat(fiscalYearClosingService.hasClosingEntries(EMPTY_YEAR)).isFalse();
        assertThat(fiscalYearClosingService.getClosingEntries(EMPTY_YEAR)).isEmpty();
    }

    @Test
    @DisplayName("Executing closing posts three closing transactions that zero revenue/expense into retained earnings")
    void shouldExecuteClosing() {
        postProfitYear();
        BigDecimal retainedBefore = balance("3.2.01", LocalDate.of(YEAR, 12, 31));

        List<JournalEntry> entries = fiscalYearClosingService.executeClosing(YEAR);
        entityManager.flush();
        entityManager.clear();

        assertThat(entries).hasSize(8);
        List<Transaction> closing = closingTransactions(YEAR);
        assertThat(closing).extracting(Transaction::getReferenceNumber, Transaction::getDescription)
                .containsExactly(
                        tuple("CLOSING-" + YEAR + "-01", "Tutup Pendapatan ke LABA_BERJALAN - Tahun " + YEAR),
                        tuple("CLOSING-" + YEAR + "-02", "Tutup Beban ke LABA_BERJALAN - Tahun " + YEAR),
                        tuple("CLOSING-" + YEAR + "-03", "Transfer Laba Berjalan ke LABA_DITAHAN - Tahun " + YEAR));
        assertThat(closing).extracting(Transaction::getAmount).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("120000000"), new BigDecimal("35000000"), new BigDecimal("85000000"));
        assertThat(closing).allSatisfy(tx -> {
            assertThat(tx.getStatus()).isEqualTo(TransactionStatus.POSTED);
            assertThat(tx.getClosingEntry()).isTrue();
            assertThat(tx.getTransactionDate()).isEqualTo(LocalDate.of(YEAR, 12, 31));
            assertThat(tx.getPostedBy()).isEqualTo("admin");
            assertThat(tx.getTransactionNumber()).startsWith("FC-" + YEAR + "-");
        });

        Map<String, BigDecimal> netByAccount = fiscalYearClosingService.getClosingEntries(YEAR).stream()
                .collect(Collectors.groupingBy(e -> e.getAccount().getAccountCode(),
                        Collectors.reducing(BigDecimal.ZERO, e -> e.getDebitAmount().subtract(e.getCreditAmount()),
                                BigDecimal::add)));
        assertThat(netByAccount.get("4.1.01")).isEqualByComparingTo("100000000");
        assertThat(netByAccount.get("4.1.02")).isEqualByComparingTo("20000000");
        assertThat(netByAccount.get("5.1.01")).isEqualByComparingTo("-30000000");
        assertThat(netByAccount.get("5.1.02")).isEqualByComparingTo("-5000000");
        assertThat(netByAccount.get("3.2.02")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(netByAccount.get("3.2.01")).isEqualByComparingTo("-85000000");

        ReportService.IncomeStatementReport withClosing = reportService.generateIncomeStatement(
                LocalDate.of(YEAR, 1, 1), LocalDate.of(YEAR, 12, 31));
        assertThat(withClosing.totalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(withClosing.totalExpense()).isEqualByComparingTo(BigDecimal.ZERO);
        ReportService.IncomeStatementReport excludingClosing = reportService.generateIncomeStatementExcludingClosing(
                LocalDate.of(YEAR, 1, 1), LocalDate.of(YEAR, 12, 31));
        assertThat(excludingClosing.totalRevenue()).isEqualByComparingTo("120000000");
        assertThat(excludingClosing.totalExpense()).isEqualByComparingTo("35000000");
        assertThat(excludingClosing.netIncome()).isEqualByComparingTo("85000000");
        assertThat(balance("3.2.01", LocalDate.of(YEAR, 12, 31)).subtract(retainedBefore))
                .isEqualByComparingTo("85000000");

        assertThat(fiscalYearClosingService.hasClosingEntries(YEAR)).isTrue();
        assertThat(fiscalYearClosingService.previewClosing(YEAR).alreadyClosed()).isTrue();
    }

    @Test
    @DisplayName("Closing the same year twice is rejected and leaves one set of closing transactions")
    void shouldRejectDuplicateClosing() {
        postProfitYear();
        fiscalYearClosingService.executeClosing(YEAR);

        assertThatThrownBy(() -> fiscalYearClosingService.executeClosing(YEAR))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Jurnal penutup untuk tahun " + YEAR + " sudah ada. "
                        + "Hapus jurnal penutup yang ada terlebih dahulu jika ingin membuat ulang.");
        assertThat(closingTransactions(YEAR)).hasSize(3);
    }

    @Test
    @DisplayName("Reversing voids the closing transactions, restores the year's P&L and allows closing again")
    void shouldReverseClosing() {
        postProfitYear();
        fiscalYearClosingService.executeClosing(YEAR);

        int reversed = fiscalYearClosingService.reverseClosing(YEAR, "Koreksi pendapatan");
        entityManager.flush();
        entityManager.clear();

        assertThat(reversed).isEqualTo(8);
        assertThat(closingTransactions(YEAR)).hasSize(3).allSatisfy(tx -> {
            assertThat(tx.getStatus()).isEqualTo(TransactionStatus.VOID);
            assertThat(tx.getVoidedBy()).isEqualTo("admin");
            assertThat(tx.getVoidNotes()).isEqualTo("Pembatalan Jurnal Penutup: Koreksi pendapatan");
        });
        assertThat(fiscalYearClosingService.getClosingEntries(YEAR)).hasSize(8).allSatisfy(e -> {
            assertThat(e.getVoidedAt()).isNotNull();
            assertThat(e.getVoidReason()).isEqualTo("Pembatalan Jurnal Penutup: Koreksi pendapatan");
        });
        assertThat(fiscalYearClosingService.hasClosingEntries(YEAR)).isFalse();
        ReportService.IncomeStatementReport restored = reportService.generateIncomeStatement(
                LocalDate.of(YEAR, 1, 1), LocalDate.of(YEAR, 12, 31));
        assertThat(restored.totalRevenue()).isEqualByComparingTo("120000000");
        assertThat(restored.netIncome()).isEqualByComparingTo("85000000");

        assertThat(fiscalYearClosingService.executeClosing(YEAR)).hasSize(8);
        assertThat(closingTransactions(YEAR)).filteredOn(tx -> tx.getStatus() == TransactionStatus.POSTED).hasSize(3);
    }

    @Test
    @DisplayName("Reversing a year without closing entries is rejected")
    void shouldRejectReverseWithoutClosing() {
        assertThatThrownBy(() -> fiscalYearClosingService.reverseClosing(EMPTY_YEAR, "Test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Tidak ada jurnal penutup untuk tahun " + EMPTY_YEAR);
    }

    private record Line(String accountCode, BigDecimal debit, BigDecimal credit) {}

    private static Line line(String accountCode, String debit, String credit) {
        return new Line(accountCode, new BigDecimal(debit), new BigDecimal(credit));
    }

    private static void assertLines(List<FiscalYearClosingService.ClosingLinePreview> actual, Line... expected) {
        assertThat(actual).hasSize(expected.length);
        for (int i = 0; i < expected.length; i++) {
            assertThat(actual.get(i).accountCode()).isEqualTo(expected[i].accountCode());
            assertThat(actual.get(i).debit()).isEqualByComparingTo(expected[i].debit());
            assertThat(actual.get(i).credit()).isEqualByComparingTo(expected[i].credit());
        }
        BigDecimal debit = actual.stream().map(FiscalYearClosingService.ClosingLinePreview::debit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credit = actual.stream().map(FiscalYearClosingService.ClosingLinePreview::credit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(debit).isEqualByComparingTo(credit);
    }

    private void postProfitYear() {
        post(YEAR, "1.1.02", "4.1.01", "100000000");
        post(YEAR, "1.1.02", "4.1.02", "20000000");
        post(YEAR, "5.1.01", "1.1.02", "30000000");
        post(YEAR, "5.1.02", "1.1.02", "5000000");
    }

    private void post(int year, String debitAccount, String creditAccount, String amount) {
        String number = "FYC-" + UUID.randomUUID().toString().substring(0, 8);
        Transaction tx = new Transaction();
        tx.setTransactionNumber(number);
        tx.setTransactionDate(LocalDate.of(year, 6, 15));
        tx.setDescription("Uji penutupan " + number);
        tx.setAmount(new BigDecimal(amount));
        tx.setJournalTemplate(templateRepository.findById(TEMPLATE_ID)
                .orElseThrow(() -> new IllegalStateException("Seeded template missing: " + TEMPLATE_ID)));
        tx.setStatus(TransactionStatus.POSTED);
        tx.setPostedAt(LocalDateTime.now());
        tx.setPostedBy("admin");
        tx.setCreatedBy("admin");
        Transaction saved = transactionRepository.save(tx);
        entry(saved, number + "-01", debitAccount, new BigDecimal(amount), BigDecimal.ZERO);
        entry(saved, number + "-02", creditAccount, BigDecimal.ZERO, new BigDecimal(amount));
    }

    private void entry(Transaction tx, String journalNumber, String accountCode, BigDecimal debit, BigDecimal credit) {
        JournalEntry entry = new JournalEntry();
        entry.setJournalNumber(journalNumber);
        entry.setTransaction(tx);
        entry.setAccount(account(accountCode));
        entry.setDebitAmount(debit);
        entry.setCreditAmount(credit);
        entry.setPostedAt(tx.getPostedAt());
        entry.setCreatedBy("admin");
        journalEntryRepository.save(entry);
    }

    private ChartOfAccount account(String code) {
        return chartOfAccountRepository.findByAccountCode(code)
                .orElseThrow(() -> new IllegalStateException("Seeded account missing: " + code));
    }

    private BigDecimal balance(String accountCode, LocalDate asOf) {
        ChartOfAccount account = account(accountCode);
        return journalEntryRepository.sumCreditByAccountAndDateRange(account.getId(), LocalDate.of(1900, 1, 1), asOf)
                .subtract(journalEntryRepository.sumDebitByAccountAndDateRange(account.getId(), LocalDate.of(1900, 1, 1), asOf));
    }

    private List<Transaction> closingTransactions(int year) {
        return fiscalYearClosingService.getClosingEntries(year).stream()
                .map(JournalEntry::getTransaction)
                .distinct()
                .sorted(java.util.Comparator.comparing(Transaction::getReferenceNumber))
                .toList();
    }
}
