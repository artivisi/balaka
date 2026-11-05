package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.dto.JournalEntryRequest;
import com.artivisi.accountingfinance.entity.AlertEvent;
import com.artivisi.accountingfinance.entity.AlertRule;
import com.artivisi.accountingfinance.entity.BankStatement;
import com.artivisi.accountingfinance.entity.BankStatementArchive;
import com.artivisi.accountingfinance.entity.BankStatementItem;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.CompanyBankAccount;
import com.artivisi.accountingfinance.entity.Contract;
import com.artivisi.accountingfinance.entity.Invoice;
import com.artivisi.accountingfinance.entity.StatementArchiveStatus;
import com.artivisi.accountingfinance.entity.TaxTransactionDetail;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.AccountType;
import com.artivisi.accountingfinance.enums.AlertType;
import com.artivisi.accountingfinance.enums.EvidenceType;
import com.artivisi.accountingfinance.enums.NormalBalance;
import com.artivisi.accountingfinance.enums.StatementItemMatchStatus;
import com.artivisi.accountingfinance.enums.TaxType;
import com.artivisi.accountingfinance.enums.VoidReason;
import com.artivisi.accountingfinance.repository.AlertEventRepository;
import com.artivisi.accountingfinance.repository.AlertRuleRepository;
import com.artivisi.accountingfinance.repository.BankStatementItemRepository;
import com.artivisi.accountingfinance.repository.BankStatementParserConfigRepository;
import com.artivisi.accountingfinance.repository.BankStatementRepository;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.CompanyBankAccountRepository;
import com.artivisi.accountingfinance.repository.CompanyConfigRepository;
import com.artivisi.accountingfinance.repository.ContractRepository;
import com.artivisi.accountingfinance.repository.InvoiceRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.TaxTransactionDetailRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.service.EvidenceQueryService.TransactionEvidence;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Evidence requirements, gaps, sources, bank statement archive, missing-statement alert and the audit pack.
 * Reserved test years 2187-2190, and 2010-03 for the alert (it evaluates against today).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("Evidence completeness")
class EvidenceServiceTest {

    private static final String CONSULTING = "Pendapatan Jasa Konsultasi";
    private static final String DEVELOPMENT = "Pendapatan Jasa Development";

    @Autowired private EvidenceQueryService queryService;
    @Autowired private EvidenceRequirementService requirementService;
    @Autowired private ContractService contractService;
    @Autowired private BankStatementArchiveService archiveService;
    @Autowired private AuditPackService auditPackService;
    @Autowired private AlertService alertService;
    @Autowired private DocumentService documentService;
    @Autowired private TransactionService transactionService;
    @Autowired private TransactionApiService transactionApiService;
    @Autowired private ReportService reportService;
    @Autowired private ReportExportService reportExportService;
    @Autowired private DocumentStorageService storageService;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private JournalTemplateRepository templateRepository;
    @Autowired private JournalEntryRepository journalEntryRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private ContractRepository contractRepository;
    @Autowired private TaxTransactionDetailRepository taxDetailRepository;
    @Autowired private CompanyBankAccountRepository bankAccountRepository;
    @Autowired private BankStatementRepository statementRepository;
    @Autowired private BankStatementItemRepository statementItemRepository;
    @Autowired private BankStatementParserConfigRepository parserConfigRepository;
    @Autowired private CompanyConfigRepository companyConfigRepository;
    @Autowired private AlertRuleRepository alertRuleRepository;
    @Autowired private AlertEventRepository alertEventRepository;
    @Autowired private EntityManager entityManager;

    // ==================== requirements and gaps ====================

    @Test
    @DisplayName("Gaps list only posted, non-closing transactions that a rule hits and that lack the evidence")
    void gapsByAccountAndTemplate() throws Exception {
        rule(account("5.1"), null, EvidenceType.CONTRACT, "1000000");
        rule(null, template(CONSULTING), EvidenceType.INVOICE, null);

        String rentDue = posted(journal(LocalDate.of(2187, 1, 5), "5.1.05", "Sewa kantor Januari", "2000000"));
        posted(journal(LocalDate.of(2187, 1, 6), "5.1.05", "Sewa gudang kecil", "500000"));
        posted(journal(LocalDate.of(2187, 1, 7), "5.2.01", "Biaya bank", "2000000"));
        journal(LocalDate.of(2187, 1, 8), "5.1.05", "Sewa draf", "2000000");
        UUID voided = journal(LocalDate.of(2187, 1, 9), "5.1.05", "Sewa dibatalkan", "2000000");
        transactionService.post(voided, "test");
        transactionService.voidTransaction(voided, VoidReason.INPUT_ERROR, "salah", "test");
        UUID withContract = journal(LocalDate.of(2187, 1, 10), "5.1.05", "Sewa dengan kontrak", "2000000");
        documentService.uploadForTransaction(withContract, pdf("kontrak-sewa.pdf"), "test", EvidenceType.CONTRACT);
        posted(withContract);
        UUID unclassified = journal(LocalDate.of(2187, 1, 11), "5.1.05", "Sewa lampiran tanpa jenis", "2000000");
        documentService.uploadForTransaction(unclassified, pdf("scan.pdf"), "test", null);
        String unclassifiedNumber = posted(unclassified);
        String consulting = posted(templated(CONSULTING, LocalDate.of(2187, 1, 12), "3000000"));
        posted(templated(DEVELOPMENT, LocalDate.of(2187, 1, 13), "3000000"));
        UUID closing = transactionApiService.createJournalEntry(new JournalEntryRequest(LocalDate.of(2187, 12, 31),
                "Penutup", "CLOSING", List.of(line("5.1.05", "2000000", "0"), line("1.1.02", "0", "2000000"))), "test")
                .transactionId();
        posted(closing);
        flush();

        List<TransactionEvidence> gaps = queryService.gaps(LocalDate.of(2187, 1, 1), LocalDate.of(2187, 12, 31));
        assertThat(gaps).extracting(TransactionEvidence::transactionNumber)
                .containsExactly(rentDue, unclassifiedNumber, consulting);
        assertThat(gaps.get(0).missing()).containsExactly(EvidenceType.CONTRACT);
        assertThat(gaps.get(0).required()).singleElement()
                .satisfies(r -> assertThat(r.rules()).containsExactly("Akun 5.1 Beban Operasional"));
        assertThat(gaps.get(1).unclassifiedCount()).isEqualTo(1);
        assertThat(gaps.get(1).notes()).anyMatch(n -> n.contains("belum diklasifikasikan"));
        assertThat(gaps.get(2).missing()).containsExactly(EvidenceType.INVOICE);
        assertThat(gaps.get(2).required()).singleElement()
                .satisfies(r -> assertThat(r.rules()).containsExactly("Template " + CONSULTING));

        // index holds every posted, non-closing transaction of the year, complete or not
        List<TransactionEvidence> index = queryService.index(LocalDate.of(2187, 1, 1), LocalDate.of(2187, 12, 31));
        assertThat(index).hasSize(7);
        TransactionEvidence complete = queryService.evidenceFor(withContract);
        assertThat(complete.complete()).isTrue();
        assertThat(complete.evidence()).singleElement().satisfies(e -> {
            assertThat(e.evidenceType()).isEqualTo(EvidenceType.CONTRACT);
            assertThat(e.source()).isEqualTo(EvidenceQueryService.SOURCE_ATTACHMENT);
            assertThat(e.reference()).isEqualTo("kontrak-sewa.pdf");
        });

        // classifying the scan closes the gap
        UUID scanId = documentService.findByTransactionId(unclassified).getFirst().getId();
        documentService.classify(scanId, EvidenceType.CONTRACT);
        flush();
        assertThat(queryService.gaps(LocalDate.of(2187, 1, 1), LocalDate.of(2187, 12, 31)))
                .extracting(TransactionEvidence::transactionNumber).containsExactly(rentDue, consulting);
    }

    @Test
    @DisplayName("Rules: exactly one target, requirable type, positive minimum, no duplicate; template rules attach to the family root")
    void ruleValidation() {
        UUID accountId = account("5.1").getId();
        assertThatThrownBy(() -> requirementService.create(new EvidenceRequirementService.NewRequirement(
                accountId, template(CONSULTING), EvidenceType.CONTRACT, null, null, "test")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tepat satu");
        assertThatThrownBy(() -> requirementService.create(new EvidenceRequirementService.NewRequirement(
                null, null, EvidenceType.CONTRACT, null, null, "test")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> requirementService.create(new EvidenceRequirementService.NewRequirement(
                accountId, null, EvidenceType.OTHER, null, null, "test")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tidak dapat diwajibkan");
        assertThatThrownBy(() -> requirementService.create(new EvidenceRequirementService.NewRequirement(
                accountId, null, EvidenceType.CONTRACT, BigDecimal.ZERO, null, "test")))
                .isInstanceOf(IllegalArgumentException.class);
        rule(account("5.1"), null, EvidenceType.CONTRACT, null);
        assertThatThrownBy(() -> rule(account("5.1"), null, EvidenceType.CONTRACT, "5"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("sudah ada");
    }

    // ==================== evidence sources ====================

    @Test
    @DisplayName("Invoice link, contract file via invoice, bupot number and bank match satisfy their types; a contract without file does not")
    void evidenceSources() throws Exception {
        UUID consultingTemplate = template(CONSULTING);
        rule(null, consultingTemplate, EvidenceType.INVOICE, null);
        rule(null, consultingTemplate, EvidenceType.CONTRACT, null);
        rule(null, consultingTemplate, EvidenceType.BUPOT, null);
        rule(account("5.1"), null, EvidenceType.PROOF_OF_PAYMENT, null);

        Invoice invoice = invoiceRepository.findByInvoiceNumber("INV-2024-001").orElseThrow();
        Contract signed = contractService.create(new ContractService.ContractData("K-2188-01", "Konsultasi 2188",
                invoice.getClient().getId(), null, LocalDate.of(2188, 1, 1), null, null, null), "test");
        contractService.attachFile(signed.getId(), pdf("k-2188-01.pdf"), "test");
        contractService.linkInvoice(signed.getId(), "INV-2024-001");

        UUID full = templated(CONSULTING, LocalDate.of(2188, 2, 1), "3000000");
        Transaction fullTrx = transactionRepository.findById(full).orElseThrow();
        fullTrx.setSourceDocumentType("INVOICE");
        fullTrx.setSourceDocumentId(invoice.getId());
        transactionRepository.save(fullTrx);
        TaxTransactionDetail bupot = new TaxTransactionDetail();
        bupot.setTransaction(fullTrx);
        bupot.setTaxType(TaxType.PPH_23);
        bupot.setBupotNumber("BP-2188-0001");
        taxDetailRepository.save(bupot);
        posted(full);

        Contract unsigned = contractService.create(new ContractService.ContractData("K-2188-02", "Belum ditandatangani",
                invoice.getClient().getId(), null, LocalDate.of(2188, 1, 1), LocalDate.of(2188, 12, 31), null, null), "test");
        UUID bare = templated(CONSULTING, LocalDate.of(2188, 2, 2), "3000000");
        posted(bare);
        contractService.linkTransaction(unsigned.getId(), transactionRepository.findById(bare).orElseThrow().getTransactionNumber());

        UUID matched = journal(LocalDate.of(2188, 2, 3), "5.1.05", "Sewa dibayar transfer", "2000000");
        posted(matched);
        UUID unmatched = journal(LocalDate.of(2188, 2, 4), "5.1.05", "Sewa tunai", "2000000");
        posted(unmatched);
        matchToStatement(matched, LocalDate.of(2188, 2, 3));
        flush();

        TransactionEvidence fullEvidence = queryService.evidenceFor(full);
        assertThat(fullEvidence.missing()).isEmpty();
        assertThat(fullEvidence.evidence()).extracting(e -> e.evidenceType() + ":" + e.source() + ":" + e.reference())
                .containsExactlyInAnyOrder("INVOICE:INVOICE:INV-2024-001", "CONTRACT:CONTRACT:K-2188-01",
                        "BUPOT:BUPOT:BP-2188-0001");

        TransactionEvidence bareEvidence = queryService.evidenceFor(bare);
        assertThat(bareEvidence.missing()).containsExactly(EvidenceType.CONTRACT, EvidenceType.INVOICE, EvidenceType.BUPOT);
        assertThat(bareEvidence.notes()).contains("Kontrak K-2188-02 belum memiliki dokumen");

        assertThat(queryService.evidenceFor(matched).missing()).isEmpty();
        assertThat(queryService.evidenceFor(matched).evidence()).singleElement()
                .satisfies(e -> assertThat(e.source()).isEqualTo(EvidenceQueryService.SOURCE_BANK_MATCH));
        assertThat(queryService.evidenceFor(unmatched).missing()).containsExactly(EvidenceType.PROOF_OF_PAYMENT);

        assertThat(queryService.gaps(LocalDate.of(2188, 1, 1), LocalDate.of(2188, 12, 31)))
                .extracting(TransactionEvidence::transactionId).containsExactly(bare, unmatched);

        // register query API for #51
        assertThat(queryService.contracts(invoice.getClient().getId(), null))
                .extracting(EvidenceQueryService.ContractSummary::contractNumber).contains("K-2188-01", "K-2188-02");
        assertThat(queryService.contractsActiveOn(LocalDate.of(2189, 1, 1)))
                .extracting(EvidenceQueryService.ContractSummary::contractNumber).contains("K-2188-01").doesNotContain("K-2188-02");
        assertThatThrownBy(() -> documentService.delete(contractRepository.findById(signed.getId()).orElseThrow().getDocument().getId()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("register kontrak");
    }

    // ==================== bank statement archive ====================

    @Test
    @DisplayName("Missing months run from the first posted line to the last due month; replace keeps history; delete waits for retention")
    void bankStatementArchive() throws Exception {
        CompanyBankAccount bank = bankAccount("1.1.87", "Bank Uji 2189");
        posted(journalOn(bank.getGlAccount(), LocalDate.of(2189, 1, 10), "2189"));
        flush();
        LocalDate asOf = LocalDate.of(2189, 5, 5);

        assertThat(missingFor(bank, asOf)).containsExactly("2189-01", "2189-02", "2189-03");

        BankStatementArchive feb = archiveService.archive(bank.getId(), YearMonth.of(2189, 2), pdf("feb.pdf"), "test", asOf);
        assertThat(feb.getRetentionUntil()).isEqualTo(LocalDate.of(2199, 2, 28));
        assertThat(missingFor(bank, asOf)).containsExactly("2189-01", "2189-03");

        BankStatementArchive febFixed = archiveService.archive(bank.getId(), YearMonth.of(2189, 2), pdf("feb-v2.pdf"), "test", asOf);
        flush();
        BankStatementArchive superseded = entityManager.find(BankStatementArchive.class, feb.getId());
        assertThat(superseded.getStatus()).isEqualTo(StatementArchiveStatus.SUPERSEDED);
        assertThat(superseded.getSupersededAt()).isNotNull();
        assertThat(febFixed.getStatus()).isEqualTo(StatementArchiveStatus.ACTIVE);
        assertThat(missingFor(bank, asOf)).containsExactly("2189-01", "2189-03");
        assertThatThrownBy(() -> documentService.delete(superseded.getDocument().getId()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("arsip rekening koran");

        assertThat(archiveService.yearGrid(2189, asOf, 10).stream().filter(r -> r.account().getId().equals(bank.getId()))
                .findFirst().orElseThrow().months())
                .extracting(c -> c.month().getMonthValue() + "=" + c.status())
                .containsExactly("1=MISSING", "2=ARCHIVED", "3=MISSING", "4=NOT_DUE", "5=NOT_DUE", "6=NOT_DUE",
                        "7=NOT_DUE", "8=NOT_DUE", "9=NOT_DUE", "10=NOT_DUE", "11=NOT_DUE", "12=NOT_DUE");
        assertThat(archiveService.yearGrid(2188, asOf, 10).stream().filter(r -> r.account().getId().equals(bank.getId()))
                .findFirst().orElseThrow().months()).allMatch(c -> c.status() == BankStatementArchiveService.MonthStatus.NOT_EXPECTED);

        assertThatThrownBy(() -> archiveService.archive(bank.getId(), YearMonth.of(2189, 5), pdf("mei.pdf"), "test", asOf))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("belum berakhir");

        assertThatThrownBy(() -> archiveService.delete(febFixed.getId(), LocalDate.of(2199, 2, 28)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("2199-02-28");
        archiveService.delete(febFixed.getId(), LocalDate.of(2199, 3, 1));
        flush();
        assertThat(missingFor(bank, asOf)).containsExactly("2189-01", "2189-02", "2189-03");
    }

    @Test
    @DisplayName("BANK_STATEMENT_MISSING alert names the account, the count and the oldest and latest missing month")
    void missingStatementAlert() throws Exception {
        CompanyBankAccount bank = bankAccount("1.1.88", "Bank Alert 2010");
        posted(journalOn(bank.getGlAccount(), LocalDate.of(2010, 3, 15), "2010"));
        archiveService.archive(bank.getId(), YearMonth.of(2010, 4), pdf("apr-2010.pdf"), "test", LocalDate.now());
        flush();

        AlertRule rule = null;
        for (AlertRule r : alertRuleRepository.findAll()) {
            r.setEnabled(r.getAlertType() == AlertType.BANK_STATEMENT_MISSING);
            alertRuleRepository.save(r);
            if (r.getAlertType() == AlertType.BANK_STATEMENT_MISSING) {
                rule = r;
            }
        }
        assertThat(rule).as("V013 seeds BANK_STATEMENT_MISSING").isNotNull();
        assertThat(rule.getThreshold()).isEqualByComparingTo("10");
        for (AlertEvent e : alertEventRepository.findByAcknowledgedAtIsNullOrderByTriggeredAtDesc()) {
            e.setAcknowledgedAt(LocalDateTime.now().minusDays(2));
            e.setTriggeredAt(LocalDateTime.now().minusDays(2));
            alertEventRepository.save(e);
        }
        flush();

        alertService.evaluateAllAlerts();
        flush();

        YearMonth lastDue = YearMonth.now();
        while (!lastDue.atEndOfMonth().plusDays(10).isBefore(LocalDate.now())) {
            lastDue = lastDue.minusMonths(1);
        }
        long expected = YearMonth.of(2010, 3).until(lastDue, java.time.temporal.ChronoUnit.MONTHS);
        UUID ruleId = rule.getId();
        AlertEvent event = alertEventRepository.findByAcknowledgedAtIsNullOrderByTriggeredAtDesc().stream()
                .filter(e -> e.getAlertRule().getId().equals(ruleId)).findFirst().orElseThrow();
        assertThat(event.getMessage()).contains("Bank Alert 2010 Rekening Bank Alert 2010: " + expected
                + " bulan, paling awal 2010-03, paling akhir " + lastDue);
    }

    // ==================== audit pack ====================

    @Test
    @DisplayName("Audit pack ZIP holds ledger, statements, evidence index with gaps, and the files within the size limit")
    void auditPack() throws Exception {
        rule(null, template(CONSULTING), EvidenceType.INVOICE, null);
        UUID withInvoice = templated(CONSULTING, LocalDate.of(2190, 3, 1), "3000000");
        UUID docId = documentService.uploadForTransaction(withInvoice, pdf("inv-2190.pdf"), "test", EvidenceType.INVOICE).getId();
        String withInvoiceNumber = posted(withInvoice);
        String gapNumber = posted(templated(CONSULTING, LocalDate.of(2190, 3, 2), "1250000"));
        flush();

        AuditPackService.AuditPackPlan plan = auditPackService.plan(2190, true);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        auditPackService.write(plan, "auditor", out);
        Map<String, byte[]> zip = unzip(out.toByteArray());

        String filePath = "dokumen/transaksi/" + withInvoiceNumber + "/" + docId + "-inv-2190.pdf";
        assertThat(zip.keySet()).containsExactlyInAnyOrder("README.txt", "buku-besar-2190.csv", "neraca-saldo-2190.xlsx",
                "laporan-keuangan-2190.pdf", "indeks-bukti-2190.csv", "arsip-rekening-koran-2190.csv",
                "register-kontrak-2190.csv", filePath);
        assertThat(new String(zip.get(filePath), StandardCharsets.UTF_8)).isEqualTo("%PDF-1.4 inv-2190.pdf");
        assertThat(new String(zip.get("laporan-keuangan-2190.pdf"), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");

        List<String> index = lines(zip.get("indeks-bukti-2190.csv"));
        assertThat(index).hasSize(3);
        assertThat(index.get(0)).isEqualTo("nomor_transaksi,tanggal,keterangan,nilai,template,bukti_wajib,bukti_ada,"
                + "bukti_kurang,dokumen,catatan");
        assertThat(index.get(1)).startsWith(withInvoiceNumber + ",2190-03-01,").contains(",3000000.00,")
                .contains(",INVOICE,INVOICE:ATTACHMENT:inv-2190.pdf,,").contains(filePath + " (INVOICE, sha256 ");
        assertThat(index.get(2)).startsWith(gapNumber + ",2190-03-02,").contains(",1250000.00,").contains(",INVOICE,,INVOICE,");

        List<String> ledger = lines(zip.get("buku-besar-2190.csv"));
        assertThat(ledger).hasSize(5);
        assertThat(ledger.stream().filter(l -> l.startsWith("1.1.02,")).map(l -> l.substring(l.lastIndexOf(',', l.lastIndexOf(',') - 1) + 1)))
                .containsExactlyInAnyOrder("3000000.00,0.00", "1250000.00,0.00");
        assertThat(new String(zip.get("README.txt"), StandardCharsets.UTF_8))
                .contains("Transaksi: 2, dengan bukti kurang: 1").contains("disertakan di folder dokumen/");

        AuditPackService tight = new AuditPackService(queryService, archiveService, contractRepository, journalEntryRepository,
                reportService, reportExportService, companyConfigRepository, storageService, 10);
        assertThatThrownBy(() -> tight.plan(2190, true)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("melebihi batas");
        ByteArrayOutputStream indexOnly = new ByteArrayOutputStream();
        tight.write(tight.plan(2190, false), "auditor", indexOnly);
        assertThat(unzip(indexOnly.toByteArray()).keySet()).noneMatch(k -> k.startsWith("dokumen/")).hasSize(7);
    }

    // ==================== helpers ====================

    private void rule(ChartOfAccount account, UUID templateId, EvidenceType type, String minAmount) {
        requirementService.create(new EvidenceRequirementService.NewRequirement(account != null ? account.getId() : null,
                templateId, type, minAmount != null ? new BigDecimal(minAmount) : null, null, "test"));
    }

    private ChartOfAccount account(String code) {
        return accountRepository.findByAccountCode(code).orElseThrow();
    }

    private UUID template(String name) {
        return templateRepository.findByTemplateNameAndIsCurrentVersionTrue(name).orElseThrow().getId();
    }

    private JournalEntryRequest.JournalLineRequest line(String code, String debit, String credit) {
        return new JournalEntryRequest.JournalLineRequest(account(code).getId(), new BigDecimal(debit), new BigDecimal(credit));
    }

    private UUID journal(LocalDate date, String debitAccount, String description, String amount) {
        return transactionApiService.createJournalEntry(new JournalEntryRequest(date, description, null,
                List.of(line(debitAccount, amount, "0"), line("1.1.02", "0", amount))), "test").transactionId();
    }

    private UUID journalOn(ChartOfAccount gl, LocalDate date, String label) {
        return transactionApiService.createJournalEntry(new JournalEntryRequest(date, "Setoran " + label, null, List.of(
                new JournalEntryRequest.JournalLineRequest(gl.getId(), new BigDecimal("100000"), BigDecimal.ZERO),
                line("1.1.02", "0", "100000"))), "test").transactionId();
    }

    private UUID templated(String templateName, LocalDate date, String amount) {
        Transaction trx = new Transaction();
        trx.setJournalTemplate(templateRepository.findById(template(templateName)).orElseThrow());
        trx.setTransactionDate(date);
        trx.setAmount(new BigDecimal(amount));
        trx.setDescription(templateName + " " + date);
        return transactionService.create(trx, null).getId();
    }

    private String posted(UUID id) {
        return transactionService.post(id, "test").getTransactionNumber();
    }

    private MockMultipartFile pdf(String name) {
        return new MockMultipartFile("file", name, "application/pdf", ("%PDF-1.4 " + name).getBytes(StandardCharsets.UTF_8));
    }

    private CompanyBankAccount bankAccount(String code, String name) {
        ChartOfAccount parent = account("1.1");
        ChartOfAccount gl = new ChartOfAccount();
        gl.setAccountCode(code);
        gl.setAccountName("Rekening " + name);
        gl.setAccountType(AccountType.ASSET);
        gl.setNormalBalance(NormalBalance.DEBIT);
        gl.setParent(parent);
        gl.setLevel(3);
        gl.setIsHeader(false);
        gl.setActive(true);
        accountRepository.save(gl);
        CompanyBankAccount bank = new CompanyBankAccount();
        bank.setBankName(name);
        bank.setAccountNumber("9900" + code.replace(".", ""));
        bank.setAccountName("Rekening " + name);
        bank.setGlAccount(gl);
        return bankAccountRepository.save(bank);
    }

    private List<String> missingFor(CompanyBankAccount bank, LocalDate asOf) {
        return archiveService.missingStatements(asOf, 10).stream().filter(m -> m.bankAccountId().equals(bank.getId()))
                .map(m -> m.month().toString()).toList();
    }

    private void matchToStatement(UUID transactionId, LocalDate date) {
        CompanyBankAccount bank = bankAccountRepository.findByActiveTrueOrderByBankNameAsc().getFirst();
        BankStatement statement = new BankStatement();
        statement.setBankAccount(bank);
        statement.setParserConfig(parserConfigRepository.findAll().getFirst());
        statement.setStatementPeriodStart(date.withDayOfMonth(1));
        statement.setStatementPeriodEnd(date.withDayOfMonth(date.lengthOfMonth()));
        statement.setOriginalFilename("mutasi.csv");
        statementRepository.save(statement);
        BankStatementItem item = new BankStatementItem();
        item.setBankStatement(statement);
        item.setLineNumber(1);
        item.setTransactionDate(date);
        item.setDebitAmount(new BigDecimal("2000000"));
        item.setMatchStatus(StatementItemMatchStatus.MATCHED);
        item.setMatchedTransaction(transactionRepository.findById(transactionId).orElseThrow());
        statementItemRepository.save(item);
    }

    private static Map<String, byte[]> unzip(byte[] bytes) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                entries.put(entry.getName(), zin.readAllBytes());
            }
        }
        return entries;
    }

    private static List<String> lines(byte[] csv) {
        return new String(csv, StandardCharsets.UTF_8).lines().toList();
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }
}
