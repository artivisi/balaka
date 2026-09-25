package com.artivisi.accountingfinance.functional.taxdetail;

import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.TaxTransactionDetail;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.TaxType;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.ProjectRepository;
import com.artivisi.accountingfinance.repository.TaxTransactionDetailRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tax detail entry on the transaction detail page (HTMX section). Every test posts its own
 * transactions (dated {@link #YEAR}, reserved for this class) that mirror the seeded
 * TAX-TRX-001..003 journals, so tax details added here never touch the seeded transactions
 * that TaxDetailApiTest, TaxExportApiTest and the SPT tests share. Faktur and bupot numbers
 * are random because both are unique across the whole table.
 */
@DisplayName("Tax Detail Entry Tests")
@Import(TaxDetailTestDataInitializer.class)
class TaxDetailEntryTest extends PlaywrightTestBase {

    private static final int YEAR = 2044;

    @Autowired private TransactionRepository transactionRepository;
    @Autowired private JournalEntryRepository journalEntryRepository;
    @Autowired private JournalTemplateRepository templateRepository;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private TaxTransactionDetailRepository taxTransactionDetailRepository;

    @BeforeEach
    void login() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("Posted transaction without tax details shows the empty section with an add button")
    void shouldShowTaxDetailSectionOnPostedTransaction() {
        Transaction ppn = postedPpnTransaction();

        openTransaction(ppn);

        assertThat(page.locator("[data-testid='tax-detail-section-card']")).isVisible();
        assertThat(page.locator("[data-testid='tax-detail-empty']")).containsText("Belum ada detail pajak");
        assertThat(page.locator("[data-testid='btn-add-tax-detail']")).hasText("Tambah Detail Pajak");
    }

    @Test
    @DisplayName("Adding a PPN Keluaran detail persists the e-Faktur fields and shows them on the card")
    void shouldAddPpnKeluaranDetail() {
        Transaction ppn = postedPpnTransaction();
        String faktur = fakturNumber();

        openAddForm(ppn);
        page.locator("[data-testid='tax-type-select']").selectOption("PPN_KELUARAN");
        page.locator("[data-testid='faktur-number']").fill(faktur);
        page.locator("[data-testid='faktur-date']").fill(YEAR + "-01-15");
        page.locator("[data-testid='transaction-code']").selectOption("01");
        page.locator("[data-testid='dpp']").fill("10000000");
        page.locator("[data-testid='ppn']").fill("1100000");
        page.locator("[data-testid='counterparty-name']").fill("PT Client Test");
        page.locator("[data-testid='btn-save-tax-detail']").click();

        assertThat(page.locator("[data-testid='tax-detail-section']")).containsText("Detail pajak berhasil disimpan");
        TaxTransactionDetail saved = onlyDetail(ppn);
        assertThat(saved.getTaxType()).isEqualTo(TaxType.PPN_KELUARAN);
        assertThat(saved.getFakturNumber()).isEqualTo(faktur);
        assertThat(saved.getFakturDate()).isEqualTo(LocalDate.of(YEAR, 1, 15));
        assertThat(saved.getTransactionCode()).isEqualTo("01");
        assertThat(saved.getDpp()).isEqualByComparingTo("10000000");
        assertThat(saved.getPpn()).isEqualByComparingTo("1100000");
        assertThat(saved.getCounterpartyName()).isEqualTo("PT Client Test");

        Locator card = card(saved);
        assertThat(card).containsText("PPN Keluaran");
        assertThat(card).containsText("No. Faktur: " + faktur);
        assertThat(card).containsText("Rp 10.000.000");
        assertThat(card).containsText("Rp 1.100.000");
        assertThat(card).containsText("15/01/" + YEAR);
        assertThat(card).containsText("PT Client Test");
    }

    @Test
    @DisplayName("Adding a PPh 23 detail persists the e-Bupot fields and shows them on the card")
    void shouldAddPph23Detail() {
        Transaction pph23 = postedPph23Transaction();
        String bupot = "BP-" + YEAR + "-" + randomDigits(8);

        openAddForm(pph23);
        page.locator("[data-testid='tax-type-select']").selectOption("PPH_23");
        page.locator("[data-testid='bupot-number']").fill(bupot);
        page.locator("[data-testid='tax-object-code']").fill("24-104-01");
        page.locator("[data-testid='gross-amount']").fill("5000000");
        page.locator("[data-testid='tax-rate']").fill("2.00");
        page.locator("[data-testid='tax-amount']").fill("100000");
        page.locator("[data-testid='counterparty-name']").fill("CV Konsultan Test");
        page.locator("[data-testid='btn-save-tax-detail']").click();

        assertThat(page.locator("[data-testid='tax-detail-section']")).containsText("Detail pajak berhasil disimpan");
        TaxTransactionDetail saved = onlyDetail(pph23);
        assertThat(saved.getTaxType()).isEqualTo(TaxType.PPH_23);
        assertThat(saved.getBupotNumber()).isEqualTo(bupot);
        assertThat(saved.getTaxObjectCode()).isEqualTo("24-104-01");
        assertThat(saved.getGrossAmount()).isEqualByComparingTo("5000000");
        assertThat(saved.getTaxRate()).isEqualByComparingTo("2.00");
        assertThat(saved.getTaxAmount()).isEqualByComparingTo("100000");
        assertThat(saved.getCounterpartyName()).isEqualTo("CV Konsultan Test");

        Locator card = card(saved);
        assertThat(card).containsText("PPh 23");
        assertThat(card).containsText("No. Bupot: " + bupot);
        assertThat(card).containsText("Rp 5.000.000");
        assertThat(card).containsText("Rp 100.000");
        assertThat(card).containsText("24-104-01");
    }

    @Test
    @DisplayName("Add form pre-fills the counterparty from the project's client")
    void shouldAutoPopulateFromClient() {
        Transaction ppn = postedPpnTransaction();

        openAddForm(ppn);

        assertThat(page.locator("[data-testid='tax-type-select']")).hasValue("PPN_KELUARAN");
        assertThat(page.locator("[data-testid='counterparty-name']")).hasValue("PT Bank Mandiri Tbk");
        assertThat(page.locator("[data-testid='counterparty-npwp']")).hasValue("01.310.523.4-091.000");
        assertThat(page.locator("[data-testid='counterparty-address']"))
                .hasValue("Jl. Jend. Gatot Subroto Kav. 36-38 Jakarta");

        page.locator("[data-testid='btn-cancel-tax-detail']").click();
        assertThat(page.locator("[data-testid='tax-detail-empty']")).isVisible();
        assertThat(taxTransactionDetailRepository.findAllByTransactionIdOrderByTaxTypeAsc(ppn.getId())).isEmpty();
    }

    @Test
    @DisplayName("Add form pre-fills DPP from the transaction amount and PPN from the output-VAT journal line")
    void shouldAutoPopulateDppPpnFromJournalEntries() {
        Transaction ppn = postedPpnTransaction();

        openAddForm(ppn);

        assertThat(page.locator("[data-testid='dpp']")).hasValue("10000000.00");
        assertThat(page.locator("[data-testid='ppn']")).hasValue("1100000.00");
    }

    @Test
    @DisplayName("A faktur number already used is rejected with an error and only the first detail is saved")
    void shouldValidateFakturNumberUniqueness() {
        Transaction first = postedPpnTransaction();
        Transaction second = postedPpnTransaction();
        String faktur = fakturNumber();

        openAddForm(first);
        fillPpn(faktur, "PT Duplicate Test");
        page.locator("[data-testid='btn-save-tax-detail']").click();
        assertThat(page.locator("[data-testid='tax-detail-section']")).containsText("Detail pajak berhasil disimpan");

        openAddForm(second);
        fillPpn(faktur, "PT Duplicate Test 2");
        page.locator("[data-testid='btn-save-tax-detail']").click();

        assertThat(page.locator("[data-testid='tax-detail-error']")).hasText("Nomor faktur sudah digunakan: " + faktur);
        assertThat(page.locator("[data-testid='tax-detail-form']")).isVisible();
        assertThat(taxTransactionDetailRepository.findAllByTransactionIdOrderByTaxTypeAsc(first.getId()))
                .extracting(TaxTransactionDetail::getFakturNumber).containsExactly(faktur);
        assertThat(taxTransactionDetailRepository.findAllByTransactionIdOrderByTaxTypeAsc(second.getId())).isEmpty();
    }

    @Test
    @DisplayName("Editing a detail changes the stored counterparty and the card after reload")
    void shouldEditTaxDetail() {
        Transaction pph23 = postedPph23Transaction();
        openAddForm(pph23);
        page.locator("[data-testid='tax-type-select']").selectOption("PPH_23");
        page.locator("[data-testid='gross-amount']").fill("5000000");
        page.locator("[data-testid='tax-rate']").fill("2.00");
        page.locator("[data-testid='tax-amount']").fill("100000");
        page.locator("[data-testid='counterparty-name']").fill("PT Edit Test");
        page.locator("[data-testid='btn-save-tax-detail']").click();
        TaxTransactionDetail created = onlyDetail(pph23);

        card(created).locator("button[title='Edit']").click();
        Locator name = page.locator("[data-testid='counterparty-name']");
        assertThat(name).hasValue("PT Edit Test");
        name.fill("PT Edit Test Updated");
        page.locator("[data-testid='btn-save-tax-detail']").click();

        assertThat(page.locator("[data-testid='tax-detail-section']")).containsText("Detail pajak berhasil diperbarui");
        TaxTransactionDetail updated = onlyDetail(pph23);
        assertThat(updated.getId()).isEqualTo(created.getId());
        assertThat(updated.getCounterpartyName()).isEqualTo("PT Edit Test Updated");
        assertThat(updated.getGrossAmount()).isEqualByComparingTo("5000000");

        openTransaction(pph23);
        assertThat(card(updated)).containsText("PT Edit Test Updated");
    }

    @Test
    @DisplayName("Deleting the only detail removes it from the database and shows the empty state")
    void shouldDeleteTaxDetail() {
        Transaction ppn = postedPpnTransaction();
        openAddForm(ppn);
        fillPpn(fakturNumber(), "PT Delete Test");
        page.locator("[data-testid='btn-save-tax-detail']").click();
        TaxTransactionDetail created = onlyDetail(ppn);

        page.onDialog(dialog -> dialog.accept());
        card(created).locator("button[title='Hapus']").click();

        assertThat(page.locator("[data-testid='tax-detail-section']")).containsText("Detail pajak berhasil dihapus");
        assertThat(page.locator("[data-testid='tax-detail-empty']")).isVisible();
        assertThat(taxTransactionDetailRepository.findById(created.getId())).isEmpty();
    }

    @Test
    @DisplayName("Transaction list marks only transactions that have tax details")
    void shouldShowIndicatorOnTransactionList() {
        String marker = "TDE-IND-" + randomDigits(6);
        Transaction withDetail = postedPpnTransaction(marker + " dengan detail");
        Transaction withoutDetail = postedPpnTransaction(marker + " tanpa detail");

        openAddForm(withDetail);
        fillPpn(fakturNumber(), "PT Indicator Test");
        page.locator("[data-testid='btn-save-tax-detail']").click();
        onlyDetail(withDetail);

        navigateTo("/transactions?search=" + marker);
        waitForPageLoad();

        assertThat(page.locator("#trx-row-" + withDetail.getId() + " [data-testid='tax-indicator']")).hasCount(1);
        assertThat(page.locator("#trx-row-" + withoutDetail.getId())).isVisible();
        assertThat(page.locator("#trx-row-" + withoutDetail.getId() + " [data-testid='tax-indicator']")).hasCount(0);
    }

    @Test
    @DisplayName("Bulk entry lists posted tax transactions in the range that still lack details, with suggestions")
    void shouldShowBulkEntryPage() {
        Transaction pending = postedPpnTransaction();
        Transaction done = postedPpnTransaction();
        Transaction nonTax = postedNonTaxTransaction();
        openAddForm(done);
        fillPpn(fakturNumber(), "PT Bulk Done");
        page.locator("[data-testid='btn-save-tax-detail']").click();
        onlyDetail(done);

        navigateTo("/transactions/tax-details/bulk?startDate=" + YEAR + "-01-01&endDate=" + YEAR + "-12-31");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Input Detail Pajak");
        assertThat(page.locator("#startDate")).hasValue(YEAR + "-01-01");
        assertThat(page.locator("#endDate")).hasValue(YEAR + "-12-31");
        Locator pendingRow = page.locator("[data-testid='bulk-row-" + pending.getId() + "']");
        assertThat(pendingRow).isVisible();
        assertThat(pendingRow).containsText(pending.getTransactionNumber());
        assertThat(pendingRow.locator("[data-testid='bulk-tax-type']")).hasValue("PPN_KELUARAN");
        assertThat(pendingRow.locator("[data-testid='bulk-counterparty']")).hasValue("PT Bank Mandiri Tbk");
        assertThat(page.locator("[data-testid='bulk-row-" + done.getId() + "']")).hasCount(0);
        assertThat(page.locator("[data-testid='bulk-row-" + nonTax.getId() + "']")).hasCount(0);
    }

    private void openTransaction(Transaction transaction) {
        navigateTo("/transactions/" + transaction.getId());
        waitForPageLoad();
        assertThat(page.locator("[data-testid='tax-detail-section']")).isVisible();
    }

    private void openAddForm(Transaction transaction) {
        openTransaction(transaction);
        page.locator("[data-testid='btn-add-tax-detail']").click();
        assertThat(page.locator("[data-testid='tax-detail-form']")).isVisible();
    }

    private void fillPpn(String faktur, String counterparty) {
        page.locator("[data-testid='tax-type-select']").selectOption("PPN_KELUARAN");
        page.locator("[data-testid='faktur-number']").fill(faktur);
        page.locator("[data-testid='dpp']").fill("10000000");
        page.locator("[data-testid='ppn']").fill("1100000");
        page.locator("[data-testid='counterparty-name']").fill(counterparty);
    }

    private Locator card(TaxTransactionDetail detail) {
        return page.locator("[data-testid='tax-detail-card-" + detail.getId() + "']");
    }

    private TaxTransactionDetail onlyDetail(Transaction transaction) {
        assertThat(page.locator("[data-testid='tax-detail-list']")).isVisible();
        List<TaxTransactionDetail> details =
                taxTransactionDetailRepository.findAllByTransactionIdOrderByTaxTypeAsc(transaction.getId());
        assertThat(details).hasSize(1);
        return details.getFirst();
    }

    private static String fakturNumber() {
        return "010.000-" + (YEAR % 100) + "." + randomDigits(8);
    }

    private static String randomDigits(int length) {
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < length; i++) {
            digits.append(ThreadLocalRandom.current().nextInt(10));
        }
        return digits.toString();
    }

    // Mirrors seeded TAX-TRX-001: Dr bank 11.1M / Cr revenue 10M / Cr PPN keluaran 1.1M, project of client MANDIRI
    private Transaction postedPpnTransaction() {
        return postedPpnTransaction("Uji PPN Keluaran " + randomDigits(6));
    }

    private Transaction postedPpnTransaction(String description) {
        return posted("Pendapatan Jasa + PPN", "PRJ-2024-001", "10000000", description,
                line("1.1.02", "11100000", "0"), line("4.1.01", "0", "10000000"), line("2.1.03", "0", "1100000"));
    }

    // Mirrors seeded TAX-TRX-002: Dr expense 5M / Cr bank 4.9M / Cr PPh 23 payable 100k
    private Transaction postedPph23Transaction() {
        return posted("Pembayaran Jasa dengan PPh 23", "PRJ-2024-003", "5000000", "Uji PPh 23 " + randomDigits(6),
                line("5.1.01", "5000000", "0"), line("1.1.02", "0", "4900000"), line("2.1.21", "0", "100000"));
    }

    private Transaction postedNonTaxTransaction() {
        return posted("Pendapatan Jasa Training", null, "15000000", "Uji Non-Pajak " + randomDigits(6),
                line("1.1.02", "15000000", "0"), line("4.1.01", "0", "15000000"));
    }

    private record Line(String accountCode, BigDecimal debit, BigDecimal credit) {}

    private static Line line(String accountCode, String debit, String credit) {
        return new Line(accountCode, new BigDecimal(debit), new BigDecimal(credit));
    }

    private Transaction posted(String templateName, String projectCode, String amount, String description,
                               Line... lines) {
        String number = "TDE-" + UUID.randomUUID().toString().substring(0, 8);
        Transaction tx = new Transaction();
        tx.setTransactionNumber(number);
        tx.setTransactionDate(LocalDate.of(YEAR, 1, 15));
        tx.setDescription(description);
        tx.setReferenceNumber("REF-" + number);
        tx.setAmount(new BigDecimal(amount));
        tx.setJournalTemplate(templateRepository.findByTemplateNameAndIsCurrentVersionTrue(templateName)
                .orElseThrow(() -> new IllegalStateException("Seeded template missing: " + templateName)));
        if (projectCode != null) {
            tx.setProject(projectRepository.findByCode(projectCode)
                    .orElseThrow(() -> new IllegalStateException("Seeded project missing: " + projectCode)));
        }
        tx.setStatus(TransactionStatus.POSTED);
        tx.setPostedAt(LocalDateTime.now());
        tx.setPostedBy("admin");
        tx.setCreatedBy("admin");
        Transaction saved = transactionRepository.save(tx);

        int index = 0;
        for (Line l : lines) {
            JournalEntry entry = new JournalEntry();
            entry.setJournalNumber(number + "-" + String.format("%02d", ++index));
            entry.setTransaction(saved);
            entry.setAccount(accountRepository.findByAccountCode(l.accountCode())
                    .orElseThrow(() -> new IllegalStateException("Seeded account missing: " + l.accountCode())));
            entry.setDebitAmount(l.debit());
            entry.setCreditAmount(l.credit());
            entry.setPostedAt(saved.getPostedAt());
            entry.setCreatedBy("admin");
            journalEntryRepository.save(entry);
        }
        return saved;
    }
}
