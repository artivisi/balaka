package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.BankStatementArchive;
import com.artivisi.accountingfinance.entity.CompanyConfig;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.repository.CompanyConfigRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.service.EvidenceQueryService.AttachedFile;
import com.artivisi.accountingfinance.service.EvidenceQueryService.ContractSummary;
import com.artivisi.accountingfinance.service.EvidenceQueryService.TransactionEvidence;
import com.artivisi.accountingfinance.repository.ContractRepository;
import com.artivisi.accountingfinance.entity.Contract;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Yearly audit pack: one ZIP with the general ledger, trial balance, financial statements, the evidence index,
 * the bank statement archive index and the contract register. With {@code includeFiles}, the referenced files are
 * added under {@code dokumen/} when their total size is at most {@code app.audit-pack.max-document-bytes}; above
 * that the export is refused rather than cut short.
 */
@Service
@Slf4j
@Transactional(readOnly = true)
public class AuditPackService {

    static final String README = "README.txt";

    private final EvidenceQueryService evidenceQueryService;
    private final BankStatementArchiveService archiveService;
    private final ContractRepository contractRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final ReportService reportService;
    private final ReportExportService reportExportService;
    private final CompanyConfigRepository companyConfigRepository;
    private final DocumentStorageService storageService;
    private final long maxDocumentBytes;

    public AuditPackService(EvidenceQueryService evidenceQueryService, BankStatementArchiveService archiveService,
                            ContractRepository contractRepository, JournalEntryRepository journalEntryRepository,
                            ReportService reportService, ReportExportService reportExportService,
                            CompanyConfigRepository companyConfigRepository, DocumentStorageService storageService,
                            @Value("${app.audit-pack.max-document-bytes}") long maxDocumentBytes) {
        this.evidenceQueryService = evidenceQueryService;
        this.archiveService = archiveService;
        this.contractRepository = contractRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.reportService = reportService;
        this.reportExportService = reportExportService;
        this.companyConfigRepository = companyConfigRepository;
        this.storageService = storageService;
        this.maxDocumentBytes = maxDocumentBytes;
    }

    @jakarta.annotation.PostConstruct
    void validateConfiguration() {
        if (maxDocumentBytes <= 0) {
            throw new IllegalStateException("app.audit-pack.max-document-bytes must be positive, got " + maxDocumentBytes);
        }
    }

    /** What the pack for a year holds; the controller checks it before the download starts. */
    public record AuditPackPlan(int year, CompanyConfig company, List<TransactionEvidence> index,
                                List<BankStatementArchive> statements, List<ContractSummary> contracts,
                                Map<String, PackFile> files, long fileBytes, boolean includeFiles) {

        public long transactionsWithGaps() {
            return index.stream().filter(e -> !e.complete()).count();
        }
    }

    public record PackFile(UUID documentId, String storagePath, long size, String checksumSha256) {}

    public long maxDocumentBytes() {
        return maxDocumentBytes;
    }

    /** Builds the plan. Throws when files are requested and their total exceeds the configured limit. */
    public AuditPackPlan plan(int year, boolean includeFiles) {
        CompanyConfig company = companyConfigRepository.findFirst()
                .orElseThrow(() -> new IllegalStateException("Konfigurasi perusahaan belum diisi"));
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);
        List<TransactionEvidence> index = evidenceQueryService.index(from, to);
        List<BankStatementArchive> statements = archiveService.archivesForYear(year);
        List<ContractSummary> contracts = contractRepository.search(null, null).stream()
                .filter(c -> overlaps(c, from, to)).map(EvidenceQueryService::toSummary).toList();

        Map<String, PackFile> files = new LinkedHashMap<>();
        for (TransactionEvidence e : index) {
            for (AttachedFile f : e.files()) {
                files.putIfAbsent(transactionFilePath(e, f), new PackFile(f.documentId(), f.storagePath(), f.fileSize(),
                        f.checksumSha256()));
            }
        }
        for (BankStatementArchive a : statements) {
            files.putIfAbsent(statementFilePath(a), new PackFile(a.getDocument().getId(), a.getDocument().getStoragePath(),
                    a.getDocument().getFileSize(), a.getDocument().getChecksumSha256()));
        }
        for (Contract c : contractRepository.search(null, null)) {
            if (c.getDocument() != null && overlaps(c, from, to)) {
                files.putIfAbsent(contractFilePath(c.getContractNumber(), c.getDocument().getId(),
                        c.getDocument().getOriginalFilename()), new PackFile(c.getDocument().getId(),
                        c.getDocument().getStoragePath(), c.getDocument().getFileSize(), c.getDocument().getChecksumSha256()));
            }
        }
        long bytes = files.values().stream().mapToLong(PackFile::size).sum();
        if (includeFiles && bytes > maxDocumentBytes) {
            throw new IllegalStateException("Total ukuran dokumen " + bytes + " byte melebihi batas paket audit "
                    + maxDocumentBytes + " byte. Unduh tanpa dokumen; indeks mencantumkan checksum setiap dokumen.");
        }
        return new AuditPackPlan(year, company, index, statements, contracts, files, bytes, includeFiles);
    }

    public void write(AuditPackPlan plan, String generatedBy, OutputStream out) throws IOException {
        int year = plan.year();
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);
        ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8);

        put(zip, README, readme(plan, generatedBy).getBytes(StandardCharsets.UTF_8));
        put(zip, "buku-besar-" + year + ".csv", ledger(from, to));
        put(zip, "neraca-saldo-" + year + ".xlsx",
                reportExportService.exportTrialBalanceToExcel(reportService.generateTrialBalance(to)));
        put(zip, "laporan-keuangan-" + year + ".pdf", reportExportService.exportFinancialStatementsPdf(
                plan.company().getCompanyName(), plan.company().getNpwp(), reportService.generateBalanceSheet(to),
                reportService.generateIncomeStatementExcludingClosing(from, to), year));
        put(zip, "indeks-bukti-" + year + ".csv", evidenceIndex(plan));
        put(zip, "arsip-rekening-koran-" + year + ".csv", statementIndex(plan));
        put(zip, "register-kontrak-" + year + ".csv", contractIndex(plan));

        if (plan.includeFiles()) {
            for (Map.Entry<String, PackFile> f : plan.files().entrySet()) {
                zip.putNextEntry(new ZipEntry(f.getKey()));
                try (InputStream in = storageService.loadAsResource(f.getValue().storagePath()).getInputStream()) {
                    in.transferTo(zip);
                }
                zip.closeEntry();
            }
        }
        zip.finish();
        log.info("Audit pack {} written: {} transactions, {} files included: {}", year, plan.index().size(),
                plan.files().size(), plan.includeFiles());
    }

    private String readme(AuditPackPlan plan, String generatedBy) {
        StringBuilder sb = new StringBuilder();
        sb.append("Paket audit tahun ").append(plan.year()).append('\n');
        sb.append("Perusahaan: ").append(plan.company().getCompanyName()).append('\n');
        sb.append("Dibuat: ").append(LocalDateTime.now().withNano(0)).append(" oleh ").append(generatedBy).append("\n\n");
        sb.append("Isi:\n");
        sb.append("- buku-besar-").append(plan.year()).append(".csv: baris jurnal terposting per akun\n");
        sb.append("- neraca-saldo-").append(plan.year()).append(".xlsx: neraca saldo per 31 Desember\n");
        sb.append("- laporan-keuangan-").append(plan.year()).append(".pdf: neraca dan laba rugi (tanpa jurnal penutup)\n");
        sb.append("- indeks-bukti-").append(plan.year()).append(".csv: setiap transaksi terposting dengan bukti wajib, ")
                .append("bukti yang ada, dan bukti yang kurang\n");
        sb.append("- arsip-rekening-koran-").append(plan.year()).append(".csv\n");
        sb.append("- register-kontrak-").append(plan.year()).append(".csv: kontrak yang berlaku pada tahun ini\n\n");
        sb.append("Transaksi: ").append(plan.index().size()).append(", dengan bukti kurang: ")
                .append(plan.transactionsWithGaps()).append('\n');
        sb.append("Dokumen: ").append(plan.files().size()).append(" (").append(plan.fileBytes()).append(" byte), ");
        sb.append(plan.includeFiles() ? "disertakan di folder dokumen/\n"
                : "tidak disertakan; cocokkan dokumen dengan checksum SHA-256 di indeks\n");
        return sb.toString();
    }

    private byte[] ledger(LocalDate from, LocalDate to) {
        Csv csv = new Csv("kode_akun", "nama_akun", "tanggal", "nomor_transaksi", "nomor_jurnal", "keterangan", "debit", "kredit");
        for (JournalEntry j : journalEntryRepository.findPostedLinesForLedger(from, to)) {
            csv.row(j.getAccount().getAccountCode(), j.getAccount().getAccountName(),
                    j.getTransaction().getTransactionDate(), j.getTransaction().getTransactionNumber(), j.getJournalNumber(),
                    j.getTransaction().getDescription(), j.getDebitAmount(), j.getCreditAmount());
        }
        return csv.bytes();
    }

    private byte[] evidenceIndex(AuditPackPlan plan) {
        Csv csv = new Csv("nomor_transaksi", "tanggal", "keterangan", "nilai", "template", "bukti_wajib", "bukti_ada",
                "bukti_kurang", "dokumen", "catatan");
        for (TransactionEvidence e : plan.index()) {
            csv.row(e.transactionNumber(), e.transactionDate(), e.description(), e.amount(), e.templateName(),
                    e.required().stream().map(r -> r.evidenceType().name()).collect(Collectors.joining(" ")),
                    e.evidence().stream().map(i -> i.evidenceType().name() + ":" + i.source() + ":" + i.reference())
                            .collect(Collectors.joining(" | ")),
                    e.missing().stream().map(Enum::name).collect(Collectors.joining(" ")),
                    e.files().stream().map(f -> transactionFilePath(e, f) + " ("
                                    + (f.evidenceType() == null ? "UNCLASSIFIED" : f.evidenceType().name())
                                    + ", sha256 " + f.checksumSha256() + ")")
                            .collect(Collectors.joining(" | ")),
                    String.join(" | ", e.notes()));
        }
        return csv.bytes();
    }

    private byte[] statementIndex(AuditPackPlan plan) {
        Csv csv = new Csv("bank", "nama_rekening", "bulan", "status", "dokumen", "sha256", "disimpan_sampai", "diunggah_oleh");
        for (BankStatementArchive a : plan.statements()) {
            csv.row(a.getBankAccount().getBankName(), a.getBankAccount().getAccountName(), a.getYearMonth(), a.getStatus(),
                    statementFilePath(a), a.getDocument().getChecksumSha256(), a.getRetentionUntil(), a.getUploadedBy());
        }
        return csv.bytes();
    }

    private byte[] contractIndex(AuditPackPlan plan) {
        Csv csv = new Csv("nomor_kontrak", "judul", "jenis_pihak", "pihak", "mulai", "berakhir", "nilai", "dokumen");
        for (ContractSummary c : plan.contracts()) {
            String file = plan.files().entrySet().stream()
                    .filter(f -> c.documentId() != null && f.getValue().documentId().equals(c.documentId())
                            && f.getKey().startsWith("dokumen/kontrak/"))
                    .map(Map.Entry::getKey).findFirst().orElse("");
            csv.row(c.contractNumber(), c.title(), c.counterpartyKind(), c.counterpartyName(), c.startDate(), c.endDate(),
                    c.contractValue(), file);
        }
        return csv.bytes();
    }

    private static boolean overlaps(Contract c, LocalDate from, LocalDate to) {
        return !c.getStartDate().isAfter(to) && (c.getEndDate() == null || !c.getEndDate().isBefore(from));
    }

    static String transactionFilePath(TransactionEvidence e, AttachedFile f) {
        return "dokumen/transaksi/" + safe(e.transactionNumber()) + "/" + f.documentId() + "-" + safe(f.filename());
    }

    static String statementFilePath(BankStatementArchive a) {
        return "dokumen/rekening-koran/" + safe(a.getBankAccount().getBankName() + "-" + a.getBankAccount().getAccountName())
                + "/" + a.getYearMonth() + "-" + a.getDocument().getId() + "-" + safe(a.getDocument().getOriginalFilename());
    }

    static String contractFilePath(String contractNumber, UUID documentId, String filename) {
        return "dokumen/kontrak/" + safe(contractNumber) + "/" + documentId + "-" + safe(filename);
    }

    private static String safe(String name) {
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static void put(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }

    /** Minimal RFC 4180 writer. */
    private static final class Csv {
        private final StringBuilder sb = new StringBuilder();

        Csv(String... header) {
            row((Object[]) header);
        }

        void row(Object... values) {
            List<String> cells = new ArrayList<>(values.length);
            for (Object v : values) {
                String s = v == null ? "" : v instanceof BigDecimal b ? b.toPlainString() : v.toString();
                // Text that a spreadsheet would read as a formula is prefixed so that it stays text.
                if (v instanceof String && !s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) {
                    s = "'" + s;
                }
                if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
                    s = "\"" + s.replace("\"", "\"\"") + "\"";
                }
                cells.add(s);
            }
            sb.append(String.join(",", cells)).append('\n');
        }

        byte[] bytes() {
            return sb.toString().getBytes(StandardCharsets.UTF_8);
        }
    }
}
