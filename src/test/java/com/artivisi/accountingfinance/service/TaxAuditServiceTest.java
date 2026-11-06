package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.dto.JournalEntryRequest;
import com.artivisi.accountingfinance.entity.ArchiveDocument;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.Document;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.TaxFiling;
import com.artivisi.accountingfinance.entity.TaxInquiryCase;
import com.artivisi.accountingfinance.entity.TaxInquiryDisclosure;
import com.artivisi.accountingfinance.entity.TaxInquiryEntry;
import com.artivisi.accountingfinance.entity.TaxInquiryItem;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.ArchiveDocumentType;
import com.artivisi.accountingfinance.enums.ArchiveSource;
import com.artivisi.accountingfinance.enums.EvidenceType;
import com.artivisi.accountingfinance.enums.SelfTestLine;
import com.artivisi.accountingfinance.enums.TaxFilingStatus;
import com.artivisi.accountingfinance.enums.TaxFilingType;
import com.artivisi.accountingfinance.enums.TaxInquiryEntryType;
import com.artivisi.accountingfinance.enums.TaxInquiryStatus;
import com.artivisi.accountingfinance.enums.TaxInquiryType;
import com.artivisi.accountingfinance.enums.TextExtractionStatus;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.repository.ArchiveDocumentRepository;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.DocumentRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.TaxFilingRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.service.DocumentArchiveService.ArchiveImportException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tax audit workspace (issue #51): evidence at posting, archive import and search, inquiry case record, disclosure
 * export, ledger drill-down. Every test rolls back. Reserved test year 2099 (integration DB; tax filings stop at 2100).
 * Archive documents carry document years up to the current year; each test uses its own unique tokens.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("Tax audit workspace")
class TaxAuditServiceTest {

    private static final int YEAR = 2099;
    private static final LocalDate TODAY = LocalDate.now();
    private static final String HEADER = "file,year,type,source,downloaded_on,doc_number,counterparty,amount,description,transaction_number,account_code\n";

    @Autowired private EvidencePostingGuard postingGuard;
    @Autowired private EvidenceQueryService evidenceQueryService;
    @Autowired private CompanyConfigService companyConfigService;
    @Autowired private EvidenceRequirementService requirementService;
    @Autowired private DocumentArchiveService archiveService;
    @Autowired private TaxInquiryService inquiryService;
    @Autowired private TaxAuditWorkspaceService workspaceService;
    @Autowired private DocumentService documentService;
    @Autowired private TransactionService transactionService;
    @Autowired private TransactionApiService transactionApiService;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private JournalEntryRepository journalEntryRepository;
    @Autowired private ArchiveDocumentRepository archiveRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private TaxFilingRepository taxFilingRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    // ==================== evidence at posting ====================

    @Test
    @DisplayName("From the policy date a transaction missing a required type cannot be posted; earlier dates and complete evidence post")
    void evidenceAtPosting() throws Exception {
        requirementService.create(new EvidenceRequirementService.NewRequirement(account("5.1").getId(), null,
                EvidenceType.CONTRACT, new BigDecimal("1000000"), null, "test"));
        companyConfigService.setEvidenceRequiredFrom(LocalDate.of(YEAR, 3, 1));
        assertThat(postingGuard.requiredFrom()).isEqualTo(LocalDate.of(YEAR, 3, 1));

        UUID before = journal(LocalDate.of(YEAR, 2, 28), "5.1.05", "Sewa Februari", "2000000");
        assertThat(post(before).getStatus()).isEqualTo(TransactionStatus.POSTED);

        UUID small = journal(LocalDate.of(YEAR, 3, 2), "5.1.05", "Sewa kecil", "500000");
        assertThat(post(small).getStatus()).isEqualTo(TransactionStatus.POSTED);

        UUID due = journal(LocalDate.of(YEAR, 3, 1), "5.1.05", "Sewa Maret", "2000000");
        assertThatThrownBy(() -> transactionService.post(due, "test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Posting ditolak: bukti belum lengkap (Kontrak). Transaksi bertanggal " + YEAR + "-03-01 wajib "
                        + "berbukti lengkap sejak " + YEAR + "-03-01. Lampirkan dan klasifikasikan dokumennya, lalu posting lagi.");
        entityManager.clear();
        assertThat(transactionRepository.findById(due).orElseThrow().getStatus()).isEqualTo(TransactionStatus.DRAFT);

        documentService.uploadForTransaction(due, pdfFile("tanpa-jenis.pdf"), "test", null);
        assertThatThrownBy(() -> transactionService.post(due, "test")).isInstanceOf(IllegalStateException.class);
        entityManager.clear();
        documentService.uploadForTransaction(due, pdfFile("kontrak-sewa.pdf"), "test", EvidenceType.CONTRACT);
        assertThat(post(due).getStatus()).isEqualTo(TransactionStatus.POSTED);

        companyConfigService.setEvidenceRequiredFrom(null);
        UUID off = journal(LocalDate.of(YEAR, 4, 1), "5.1.05", "Sewa April", "2000000");
        assertThat(post(off).getStatus()).isEqualTo(TransactionStatus.POSTED);
    }

    @Test
    @DisplayName("Automatic and module postings are not blocked by the evidence policy but still appear in the missing-evidence report")
    void systemPostingsExemptButReported() {
        requirementService.create(new EvidenceRequirementService.NewRequirement(account("5.1").getId(), null,
                EvidenceType.CONTRACT, new BigDecimal("1000000"), null, "test"));
        companyConfigService.setEvidenceRequiredFrom(LocalDate.of(YEAR, 9, 1));

        UUID manual = journal(LocalDate.of(YEAR, 9, 2), "5.1.05", "Sewa manual", "2000000");
        assertThatThrownBy(() -> transactionService.post(manual, "test")).hasMessageStartingWith("Posting ditolak: bukti belum lengkap");
        entityManager.clear();

        UUID recurring = journal(LocalDate.of(YEAR, 9, 3), "5.1.05", "Sewa berulang", "2000000");
        assertThat(transactionService.postAutomatic(recurring, "SYSTEM").getStatus()).isEqualTo(TransactionStatus.POSTED);
        UUID module = journal(LocalDate.of(YEAR, 9, 4), "5.1.05", "Sewa dari modul", "2000000");
        assertThat(transactionService.postSystem(module, "system",
                com.artivisi.accountingfinance.dto.FormulaContext.of(new BigDecimal("2000000"))).getStatus())
                .isEqualTo(TransactionStatus.POSTED);
        entityManager.flush();

        assertThat(evidenceQueryService.gaps(LocalDate.of(YEAR, 9, 1), LocalDate.of(YEAR, 9, 30)))
                .extracting(EvidenceQueryService.TransactionEvidence::transactionId)
                .contains(recurring, module);
        companyConfigService.setEvidenceRequiredFrom(null);
    }

    // ==================== archive ====================

    @Test
    @DisplayName("A ZIP with manifest is imported with metadata, text, source and ledger link, and is found by full-text search")
    void importAndSearch() {
        String token = token();
        UUID trx = post(journal(LocalDate.of(YEAR, 5, 10), "5.1.05", "Sewa gudang " + token, "3500000")).getId();
        String number = transactionRepository.findById(trx).orElseThrow().getTransactionNumber();
        byte[] faktur = ArchiveTestFiles.pdf("Faktur Pajak 010.000-20.00000001 " + token, "Pembeli PT Sumber Makmur", "DPP 3.500.000");
        byte[] scan = ArchiveTestFiles.png();
        Map<String, Object> zip = new LinkedHashMap<>();
        zip.put("manifest.csv", HEADER
                + "2020/faktur/" + token + ".pdf,2020,faktur_pajak,TAX_PORTAL,2025-01-15,010-20-" + token + ",PT Sumber Makmur,3500000.00,Faktur sewa,"
                + number + ",5.1.05\n"
                + "2020/bank/scan-" + token + ".png,2020,BANK_STATEMENT,BANK,2025-01-16,,Bank Mandiri,,\"Rekening koran Januari, scan\",,\n");
        zip.put("2020/faktur/" + token + ".pdf", faktur);
        zip.put("2020/bank/scan-" + token + ".png", scan);
        zip.put("__MACOSX/2020/._x.pdf", new byte[]{1});

        DocumentArchiveService.ImportResult result = archiveService.importZip("arsip-2020.zip",
                new ByteArrayInputStream(ArchiveTestFiles.zip(zip)), "test", TODAY);
        assertThat(result.documentCount()).isEqualTo(2);

        List<DocumentArchiveService.SearchHit> hits = archiveService.search(token, null, null, null);
        assertThat(hits).hasSize(1);
        ArchiveDocument a = archiveService.find(hits.getFirst().document().getId());
        assertThat(a.getDocType()).isEqualTo(ArchiveDocumentType.FAKTUR_PAJAK);
        assertThat(a.getSource()).isEqualTo(ArchiveSource.TAX_PORTAL);
        assertThat(a.getDownloadedOn()).isEqualTo(LocalDate.of(2025, 1, 15));
        assertThat(a.getDocYear()).isEqualTo(2020);
        assertThat(a.getCounterparty()).isEqualTo("PT Sumber Makmur");
        assertThat(a.getAmount()).isEqualByComparingTo("3500000");
        assertThat(a.getTextStatus()).isEqualTo(TextExtractionStatus.EXTRACTED);
        assertThat(a.getExtractedText()).contains(token).contains("Pembeli PT Sumber Makmur");
        assertThat(a.getTransaction().getId()).isEqualTo(trx);
        assertThat(a.getJournalEntry().getAccount().getAccountCode()).isEqualTo("5.1.05");
        assertThat(a.getImportBatch().getId()).isEqualTo(result.batchId());
        assertThat(a.getDocument().getOriginalFilename()).isEqualTo(token + ".pdf");
        assertThat(a.getDocument().getChecksumSha256()).isEqualTo(sha(faktur));
        assertThat(hits.getFirst().excerpt()).contains("[" + token + "]");

        List<DocumentArchiveService.SearchHit> scans = archiveService.search("scan", 2020, ArchiveDocumentType.BANK_STATEMENT, ArchiveSource.BANK);
        assertThat(scans).extracting(h -> h.document().getDocument().getOriginalFilename()).contains("scan-" + token + ".png");
        ArchiveDocument png = scans.stream().filter(h -> h.document().getDocument().getOriginalFilename().equals("scan-" + token + ".png"))
                .findFirst().orElseThrow().document();
        assertThat(png.getTextStatus()).isEqualTo(TextExtractionStatus.NOT_SUPPORTED);
        assertThat(png.getDescription()).isEqualTo("Rekening koran Januari, scan");
        assertThat(png.getTransaction()).isNull();
        assertThat(archiveService.search("Makmur " + token, null, null, null)).hasSize(1);
        assertThat(archiveService.search(token, null, ArchiveDocumentType.BUPOT, null)).isEmpty();
        assertThat(archiveService.forTransaction(trx)).extracting(ArchiveDocument::getId).containsExactly(a.getId());

        assertThat(archiveService.link(png.getId(), number, null).getTransaction().getId()).isEqualTo(trx);
        assertThatThrownBy(() -> archiveService.link(png.getId(), number, "1.1.01"))
                .hasMessage("transaksi " + number + " tidak punya baris jurnal pada akun 1.1.01");
        assertThat(archiveService.link(png.getId(), " ", null).getTransaction()).isNull();
    }

    @Test
    @DisplayName("One bad row rejects the whole ZIP with every error listed and nothing stored")
    void importRejectsBadRows() {
        String token = token();
        byte[] good = ArchiveTestFiles.pdf("Bukti potong " + token);
        Map<String, Object> zip = new LinkedHashMap<>();
        zip.put("manifest.csv", HEADER
                + "ok.pdf,2021,BUPOT,TAX_PORTAL,2025-02-01,BP-" + token + ",,,,,\n"
                + "missing.pdf,2021,BUPOT,TAX_PORTAL,2025-02-01,,,,,,\n"
                + "ok.pdf,1970,KTP,PORTAL," + TODAY.plusDays(1) + ",,,1.000,,,\n"
                + "fake.pdf,2021,OTHER,OTHER,2025-02-01,,,,,TRX-" + token + ",\n"
                + "copy.pdf,2021,OTHER,OTHER,2025-02-01,,,,,,5.1.05\n");
        zip.put("ok.pdf", good);
        zip.put("fake.pdf", "not a pdf".getBytes(StandardCharsets.UTF_8));
        zip.put("copy.pdf", good);
        zip.put("extra.pdf", good);
        long documentsBefore = documentRepository.count();

        assertThatThrownBy(() -> archiveService.importZip("salah.zip", new ByteArrayInputStream(ArchiveTestFiles.zip(zip)), "test", TODAY))
                .isInstanceOfSatisfying(ArchiveImportException.class, e -> assertThat(e.getErrors()).containsExactly(
                        "Baris 3: file missing.pdf tidak ada di ZIP",
                        "Baris 4: file ok.pdf tercantum lebih dari sekali",
                        "Baris 4: tahun 1970 di luar 1983-" + TODAY.getYear(),
                        "Baris 4: type tidak dikenal: KTP (pilihan: FAKTUR_PAJAK, BUPOT, SPT, BPE, BANK_STATEMENT, INVOICE, RECEIPT, CONTRACT, TAX_LETTER, OTHER)",
                        "Baris 4: source tidak dikenal: PORTAL (pilihan: TAX_PORTAL, BANK, OTHER)",
                        "Baris 4: downloaded_on " + TODAY.plusDays(1) + " setelah hari ini",
                        "Baris 4: amount harus angka tanpa pemisah ribuan, desimal dengan titik: 1.000",
                        "Baris 5: isi file fake.pdf tidak sesuai jenisnya (application/pdf)",
                        "Baris 5: transaksi TRX-" + token + " tidak ditemukan",
                        "Baris 6: account_code diisi tanpa transaction_number",
                        "File di ZIP tidak tercantum di manifest.csv: extra.pdf"));
        assertThat(documentRepository.count()).isEqualTo(documentsBefore);
        assertThat(archiveService.search(token, null, null, null)).isEmpty();

        Map<String, Object> dup = new LinkedHashMap<>();
        dup.put("manifest.csv", HEADER + "a.pdf,2021,BUPOT,TAX_PORTAL,2025-02-01,,,,,,\nb.pdf,2021,BUPOT,TAX_PORTAL,2025-02-01,,,,,,\n");
        dup.put("a.pdf", good);
        dup.put("b.pdf", good);
        assertThatThrownBy(() -> archiveService.importZip("dup.zip", new ByteArrayInputStream(ArchiveTestFiles.zip(dup)), "test", TODAY))
                .isInstanceOfSatisfying(ArchiveImportException.class,
                        e -> assertThat(e.getErrors()).containsExactly("Baris 3: isi file sama dengan baris 2"));

        Map<String, Object> once = new LinkedHashMap<>();
        once.put("manifest.csv", HEADER + "a.pdf,2021,BUPOT,TAX_PORTAL,2025-02-01,BP-" + token + ",,,,,\n");
        once.put("a.pdf", good);
        archiveService.importZip("once.zip", new ByteArrayInputStream(ArchiveTestFiles.zip(once)), "test", TODAY);
        assertThatThrownBy(() -> archiveService.importZip("again.zip", new ByteArrayInputStream(ArchiveTestFiles.zip(once)), "test", TODAY))
                .isInstanceOfSatisfying(ArchiveImportException.class,
                        e -> assertThat(e.getErrors()).containsExactly("Baris 2: file yang sama sudah ada di arsip"));

        Map<String, Object> noManifest = Map.of("a.pdf", good);
        assertThatThrownBy(() -> archiveService.importZip("x.zip", new ByteArrayInputStream(ArchiveTestFiles.zip(noManifest)), "test", TODAY))
                .isInstanceOfSatisfying(ArchiveImportException.class,
                        e -> assertThat(e.getErrors()).containsExactly("File manifest.csv tidak ada di akar ZIP"));
    }

    // ==================== inquiry case ====================

    @Test
    @DisplayName("Case record: items, reply, correction, deadline, status, disclosure with export of exactly the selected files, decision")
    void caseLifecycleAndExport() throws Exception {
        String token = token();
        UUID trx = post(journal(LocalDate.of(YEAR, 7, 1), "5.1.05", "Sewa Juli " + token, "4000000")).getId();
        Document contract = documentService.uploadForTransaction(trx, pdfFile("kontrak-" + token + ".pdf"), "test", EvidenceType.CONTRACT);
        Document notGiven = documentService.uploadForTransaction(trx, pdfFile("internal-" + token + ".pdf"), "test", EvidenceType.OTHER);
        String number = transactionRepository.findById(trx).orElseThrow().getTransactionNumber();
        Map<String, Object> zip = new LinkedHashMap<>();
        zip.put("manifest.csv", HEADER + "rk.pdf,2020,BANK_STATEMENT,BANK,2025-03-01,RK-" + token + ",Bank BCA,,,,\n");
        zip.put("rk.pdf", ArchiveTestFiles.pdf("Rekening koran " + token));
        archiveService.importZip("rk.zip", new ByteArrayInputStream(ArchiveTestFiles.zip(zip)), "test", TODAY);
        ArchiveDocument statement = archiveService.search("RK-" + token, null, null, null).getFirst().document();

        TaxFiling ppn = filing(TaxFilingType.PPN, 7);
        TaxInquiryCase c = inquiryService.create(new TaxInquiryService.NewCase(TaxInquiryType.SP2DK, YEAR, "S-" + token,
                LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 7), "KPP Pratama Jakarta Setiabudi", "Penjelasan beban sewa",
                LocalDate.of(2026, 1, 19), ppn.getId(), null), pdfFile("sp2dk.pdf"), "test");
        assertThat(c.getStatus()).isEqualTo(TaxInquiryStatus.OPEN);
        assertThat(c.getTaxFiling().getId()).isEqualTo(ppn.getId());
        assertThatThrownBy(() -> inquiryService.create(new TaxInquiryService.NewCase(TaxInquiryType.SP2DK, YEAR, "S-" + token,
                LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 7), "KPP", "x", LocalDate.of(2026, 1, 19), null, null),
                pdfFile("sp2dk.pdf"), "test")).hasMessage("Kasus SP2DK S-" + token + " sudah ada");
        assertThatThrownBy(() -> inquiryService.create(new TaxInquiryService.NewCase(TaxInquiryType.PEMERIKSAAN, YEAR, "P-" + token,
                LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 7), "KPP", "x", LocalDate.of(2026, 1, 19), null, null),
                null, "test")).hasMessage("Surat wajib dilampirkan: unggah filenya atau pilih dari register pelaporan pajak");

        TaxInquiryItem rent = inquiryService.addItem(c.getId(), "Rincian beban sewa", SelfTestLine.RENT_VS_PPH42_DPP, 0, "test");
        TaxInquiryItem bank = inquiryService.addItem(c.getId(), "Rekening koran 2020", null, null, "test");
        assertThat(rent.getItemNo()).isEqualTo(1);
        assertThat(bank.getItemNo()).isEqualTo(2);
        assertThatThrownBy(() -> inquiryService.addItem(c.getId(), "x", SelfTestLine.RENT_VS_PPH42_DPP, 3, "test"))
                .hasMessage("Bulan 3 tidak berlaku untuk " + SelfTestLine.RENT_VS_PPH42_DPP.getTitle());

        TaxInquiryEntry reply = inquiryService.addEntry(c.getId(), TaxInquiryEntryType.REPLY, LocalDate.of(2026, 1, 15),
                "Surat jawaban nomor 01/2026", rent.getId(), pdfFile("jawaban.pdf"), "test");
        inquiryService.correct(c.getId(), reply.getId(), "Nomor surat jawaban yang benar 02/2026", "test");
        inquiryService.changeDeadline(c.getId(), LocalDate.of(2026, 2, 2), "Perpanjangan disetujui", "test");
        inquiryService.changeStatus(c.getId(), TaxInquiryStatus.RESPONDED, "Jawaban dikirim", "test");
        assertThatThrownBy(() -> inquiryService.changeStatus(c.getId(), TaxInquiryStatus.CLOSED, "x", "test"))
                .hasMessage("Kasus ditutup dengan mencatat keputusan");

        List<TaxInquiryService.Candidate> candidates = inquiryService.candidates(List.of(number), "RK-" + token, null);
        assertThat(candidates).extracting(TaxInquiryService.Candidate::filename)
                .containsExactlyInAnyOrder("kontrak-" + token + ".pdf", "internal-" + token + ".pdf", "rk.pdf");
        assertThat(candidates.getLast().archiveId()).isEqualTo(statement.getId());

        Map<UUID, UUID> selection = new LinkedHashMap<>();
        selection.put(contract.getId(), rent.getId());
        selection.put(statement.getDocument().getId(), bank.getId());
        TaxInquiryEntry disclosure = inquiryService.disclose(c.getId(), selection, LocalDate.of(2026, 1, 16), null, "test");
        assertThat(disclosure.getContent()).isEqualTo("2 dokumen diserahkan untuk permintaan 1, 2");

        TaxInquiryService.CaseView view = inquiryService.view(c.getId());
        assertThat(view.inquiryCase().getDeadline()).isEqualTo(LocalDate.of(2026, 2, 2));
        assertThat(view.inquiryCase().getStatus()).isEqualTo(TaxInquiryStatus.RESPONDED);
        assertThat(view.entries()).extracting(e -> e.entry().getEntryType()).containsExactly(TaxInquiryEntryType.REPLY,
                TaxInquiryEntryType.CORRECTION, TaxInquiryEntryType.DEADLINE_CHANGE, TaxInquiryEntryType.STATUS_CHANGE,
                TaxInquiryEntryType.DISCLOSURE);
        assertThat(view.entries().getFirst().corrections()).extracting(TaxInquiryEntry::getContent)
                .containsExactly("Nomor surat jawaban yang benar 02/2026");
        assertThat(view.entries().getFirst().entry().getContent()).isEqualTo("Surat jawaban nomor 01/2026");
        assertThat(view.entries().get(2).entry().getContent()).isEqualTo("Tenggat 2026-01-19 -> 2026-02-02: Perpanjangan disetujui");
        assertThat(view.disclosures()).extracting(TaxInquiryDisclosure::getFilename, d -> d.getItem().getItemNo(),
                        TaxInquiryDisclosure::getDisclosedOn, d -> d.getTransaction() != null ? d.getTransaction().getId() : null)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("kontrak-" + token + ".pdf", 1, LocalDate.of(2026, 1, 16), trx),
                        org.assertj.core.groups.Tuple.tuple("rk.pdf", 2, LocalDate.of(2026, 1, 16), null));
        assertThat(view.disclosures().get(1).getArchiveDocument().getId()).isEqualTo(statement.getId());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        inquiryService.exportDisclosure(c.getId(), disclosure.getId(), out);
        Map<String, byte[]> files = ArchiveTestFiles.unzip(out.toByteArray());
        String contractPath = "dokumen/1/" + contract.getId() + "-kontrak-" + token + ".pdf";
        String statementPath = "dokumen/2/" + statement.getDocument().getId() + "-rk.pdf";
        assertThat(files.keySet()).containsExactly("index.csv", contractPath, statementPath);
        assertThat(sha(files.get(contractPath))).isEqualTo(contract.getChecksumSha256());
        List<String> index = new String(files.get("index.csv"), StandardCharsets.UTF_8).lines().toList();
        assertThat(index).containsExactly(
                "no_permintaan,permintaan,file_zip,nama_asli,sha256,ukuran,asal,nomor_transaksi,tanggal_serah,surat",
                "1,Rincian beban sewa," + contractPath + ",kontrak-" + token + ".pdf," + contract.getChecksumSha256() + ","
                        + contract.getFileSize() + ",Lampiran transaksi," + number + ",2026-01-16,SP2DK S-" + token,
                "2,Rekening koran 2020," + statementPath + ",rk.pdf," + statement.getDocument().getChecksumSha256() + ","
                        + statement.getDocument().getFileSize() + ",\"Arsip Rekening Koran 2020 (Bank, diunduh 2025-03-01)\",,2026-01-16,SP2DK S-" + token);
        assertThat(files).doesNotContainKey("dokumen/1/" + notGiven.getId() + "-internal-" + token + ".pdf");

        TaxAuditWorkspaceService.LedgerView ledger = workspaceService.ledger(trx, null);
        assertThat(ledger.disclosures()).extracting(TaxInquiryDisclosure::getFilename).containsExactly("kontrak-" + token + ".pdf");
        assertThat(ledger.filings()).extracting(f -> f.filing().getId()).contains(ppn.getId());
        assertThat(ledger.cases()).extracting(TaxInquiryCase::getId).containsExactly(c.getId());
        assertThat(inquiryService.candidates(List.of(number), null, null).stream()
                .filter(cand -> cand.documentId().equals(contract.getId())).findFirst().orElseThrow().earlierDisclosures())
                .extracting(d -> d.getInquiryCase().getId()).containsExactly(c.getId());

        inquiryService.recordDecision(c.getId(), LocalDate.of(2026, 2, 10), "LHP2DK: penjelasan diterima", "test");
        TaxInquiryCase closed = inquiryService.find(c.getId());
        assertThat(closed.getStatus()).isEqualTo(TaxInquiryStatus.CLOSED);
        assertThat(closed.getDecision()).isEqualTo("LHP2DK: penjelasan diterima");
        assertThat(closed.getClosedOn()).isEqualTo(LocalDate.of(2026, 2, 10));
        assertThatThrownBy(() -> inquiryService.addItem(c.getId(), "lagi", null, null, "test"))
                .hasMessage("Kasus SP2DK S-" + token + " sudah selesai");
        assertThatThrownBy(() -> inquiryService.disclose(c.getId(), Map.of(notGiven.getId(), rent.getId()), LocalDate.of(2026, 2, 11), null, "test"))
                .hasMessage("Kasus SP2DK S-" + token + " sudah selesai");
    }

    @Test
    @DisplayName("Entries, items and disclosures cannot be updated or deleted in the database")
    void appendOnly() throws Exception {
        String token = token();
        TaxInquiryCase c = inquiryService.create(new TaxInquiryService.NewCase(TaxInquiryType.PEMERIKSAAN, YEAR, "PMR-" + token,
                LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 5), "KPP", "Pemeriksaan lengkap", LocalDate.of(2026, 3, 5),
                null, null), pdfFile("sp2.pdf"), "test");
        TaxInquiryEntry note = inquiryService.addEntry(c.getId(), TaxInquiryEntryType.MEETING, LocalDate.of(2026, 1, 20),
                "Pertemuan pertama", null, null, "test");
        entityManager.flush();
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE tax_inquiry_entries SET content = 'diubah' WHERE id = ?", note.getId()))
                .hasMessageContaining("Catatan pemeriksaan pajak tidak dapat diubah atau dihapus (tax_inquiry_entries)");
    }

    @Test
    @DisplayName("Trace view carries each row's evidence state; a transaction missing a required type is a gap")
    void traceFlagsGaps() throws Exception {
        ChartOfAccount other = account("5.1.05");
        other.setSelfTestRole(com.artivisi.accountingfinance.enums.SelfTestRole.RENT_PPH42);
        accountRepository.save(other);
        requirementService.create(new EvidenceRequirementService.NewRequirement(other.getId(), null,
                EvidenceType.CONTRACT, null, null, "test"));
        UUID gap = post(journal(LocalDate.of(YEAR, 8, 1), "5.1.05", "Sewa Agustus", "1500000")).getId();
        UUID covered = journal(LocalDate.of(YEAR, 8, 2), "5.1.05", "Sewa Agustus gudang", "2500000");
        documentService.uploadForTransaction(covered, pdfFile("kontrak-gudang.pdf"), "test", EvidenceType.CONTRACT);
        post(covered);

        TaxAuditWorkspaceService.TraceView view = workspaceService.trace(YEAR, SelfTestLine.RENT_VS_PPH42_DPP, 0);
        assertThat(view.trace().bookFigure()).isEqualByComparingTo("4000000");
        assertThat(view.evidenceByTransaction().get(gap).missing()).containsExactly(EvidenceType.CONTRACT);
        assertThat(view.evidenceByTransaction().get(covered).complete()).isTrue();
        assertThat(view.gapCount()).isEqualTo(1);

        JournalEntry line = journalEntryRepository.findByTransactionIdWithAccount(gap).stream()
                .filter(j -> j.getAccount().getAccountCode().equals("5.1.05")).findFirst().orElseThrow();
        TaxAuditWorkspaceService.LedgerView ledger = workspaceService.ledger(gap, line.getId());
        assertThat(ledger.selectedLineId()).isEqualTo(line.getId());
        assertThat(ledger.evidence().missing()).containsExactly(EvidenceType.CONTRACT);
        assertThatThrownBy(() -> workspaceService.ledger(covered, line.getId()))
                .hasMessage("Baris jurnal " + line.getId() + " bukan milik transaksi ini");
    }

    // ==================== helpers ====================

    private Transaction post(UUID id) {
        return transactionService.post(id, "test");
    }

    private UUID journal(LocalDate date, String debitAccount, String description, String amount) {
        BigDecimal value = new BigDecimal(amount);
        return transactionApiService.createJournalEntry(new JournalEntryRequest(date, description, null, List.of(
                new JournalEntryRequest.JournalLineRequest(account(debitAccount).getId(), value, BigDecimal.ZERO),
                new JournalEntryRequest.JournalLineRequest(account("1.1.02").getId(), BigDecimal.ZERO, value))), "test")
                .transactionId();
    }

    private ChartOfAccount account(String code) {
        return accountRepository.findByAccountCode(code).orElseThrow(() -> new IllegalStateException("Seeded account missing: " + code));
    }

    private TaxFiling filing(TaxFilingType type, int month) {
        TaxFiling f = new TaxFiling();
        f.setTaxType(type);
        f.setPeriodYear(YEAR);
        f.setPeriodMonth(month);
        f.setPembetulanNo(0);
        f.setStatus(TaxFilingStatus.FILED);
        f.setFiledDate(LocalDate.of(YEAR, month + 1, 20));
        return taxFilingRepository.save(f);
    }

    private static MockMultipartFile pdfFile(String name) {
        return new MockMultipartFile("file", name, "application/pdf", ArchiveTestFiles.pdf(name, UUID.randomUUID().toString()));
    }

    private static String token() {
        return "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private static String sha(byte[] bytes) throws RuntimeException {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
