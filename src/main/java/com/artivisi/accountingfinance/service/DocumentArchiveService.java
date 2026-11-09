package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.ArchiveDocument;
import com.artivisi.accountingfinance.entity.ArchiveImportBatch;
import com.artivisi.accountingfinance.entity.Document;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.ArchiveDocumentType;
import com.artivisi.accountingfinance.enums.ArchiveSource;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.repository.ArchiveDocumentRepository;
import com.artivisi.accountingfinance.repository.ArchiveImportBatchRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.security.FileValidationService;
import jakarta.persistence.EntityNotFoundException;
import lombok.Getter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Historical document archive (issue #51, build steps 2 and 3): bulk import of copies from a ZIP with a manifest,
 * full-text search, and the link to a ledger line.
 *
 * <p>Import format: a ZIP holding {@value #MANIFEST} and the files it lists. Manifest columns (header row required,
 * order free): file, year, type, source, downloaded_on (required); doc_number, counterparty, amount, description,
 * transaction_number, account_code (optional). Every row is validated before anything is stored; one bad row rejects
 * the whole import with every error listed, and nothing is saved.
 */
@Service
@Transactional(readOnly = true)
public class DocumentArchiveService {

    public static final String MANIFEST = "manifest.csv";
    public static final List<String> REQUIRED_COLUMNS = List.of("file", "year", "type", "source", "downloaded_on");
    public static final List<String> OPTIONAL_COLUMNS = List.of("doc_number", "counterparty", "amount", "description",
            "transaction_number", "account_code");
    private static final int MIN_YEAR = 1983;
    private static final int MAX_YEAR = 2100;
    private static final int SEARCH_LIMIT = 200;
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "pdf", "application/pdf", "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "gif", "image/gif");

    private final ArchiveDocumentRepository archiveRepository;
    private final ArchiveImportBatchRepository batchRepository;
    private final TransactionRepository transactionRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final DocumentService documentService;
    private final DocumentStorageService storageService;
    private final FileValidationService fileValidationService;
    private final DocumentTextExtractor textExtractor;
    private final long maxFileBytes;
    private final long maxTotalBytes;
    private final int maxEntries;

    public DocumentArchiveService(ArchiveDocumentRepository archiveRepository,
                                  ArchiveImportBatchRepository batchRepository,
                                  TransactionRepository transactionRepository,
                                  JournalEntryRepository journalEntryRepository,
                                  DocumentService documentService,
                                  DocumentStorageService storageService,
                                  FileValidationService fileValidationService,
                                  DocumentTextExtractor textExtractor,
                                  @Value("${app.storage.documents.max-file-size}") long maxFileBytes,
                                  @Value("${app.archive-import.max-total-bytes}") long maxTotalBytes,
                                  @Value("${app.archive-import.max-entries}") int maxEntries) {
        this.archiveRepository = archiveRepository;
        this.batchRepository = batchRepository;
        this.transactionRepository = transactionRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.documentService = documentService;
        this.storageService = storageService;
        this.fileValidationService = fileValidationService;
        this.textExtractor = textExtractor;
        this.maxFileBytes = maxFileBytes;
        this.maxTotalBytes = maxTotalBytes;
        this.maxEntries = maxEntries;
    }

    /** A rejected import: every problem found, by manifest row (row 1 is the header). Nothing was saved. */
    @Getter
    public static class ArchiveImportException extends RuntimeException {
        private final transient List<String> errors;

        public ArchiveImportException(List<String> errors) {
            super("Impor arsip ditolak, tidak ada dokumen yang disimpan: " + String.join("; ", errors));
            this.errors = List.copyOf(errors);
        }
    }

    public record ImportResult(UUID batchId, int documentCount) {}

    public record SearchHit(ArchiveDocument document, String excerpt) {}

    private record ValidRow(int rowNo, String path, byte[] content, String contentType, int year, ArchiveDocumentType type,
                            ArchiveSource source, LocalDate downloadedOn, String docNumber, String counterparty,
                            BigDecimal amount, String description, Transaction transaction, JournalEntry journalEntry,
                            DocumentTextExtractor.Extraction extraction, String checksum) {}

    // ==================== import ====================

    @Transactional
    public ImportResult importZip(String originalFilename, InputStream zip, String username, LocalDate today) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Pengguna wajib diisi");
        }
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("Nama file ZIP wajib diisi");
        }
        Map<String, byte[]> entries = readZip(zip);
        byte[] manifest = entries.remove(MANIFEST);
        if (manifest == null) {
            throw new ArchiveImportException(List.of("File " + MANIFEST + " tidak ada di akar ZIP"));
        }
        List<String> errors = new ArrayList<>();
        List<ValidRow> rows = validate(manifest, entries, today, errors);
        if (!errors.isEmpty()) {
            throw new ArchiveImportException(errors);
        }
        if (rows.isEmpty()) {
            throw new ArchiveImportException(List.of(MANIFEST + " tidak berisi baris dokumen"));
        }

        ArchiveImportBatch batch = new ArchiveImportBatch();
        batch.setOriginalFilename(originalFilename);
        batch.setDocumentCount(rows.size());
        batch.setImportedBy(username);
        batch.setImportedAt(LocalDateTime.now());
        batch = batchRepository.save(batch);

        List<String> storedPaths = new ArrayList<>();
        try {
            for (ValidRow row : rows) {
                Document document = documentService.saveFromBytes(row.content(), fileName(row.path()), row.contentType(), username);
                storedPaths.add(document.getStoragePath());
                ArchiveDocument archive = new ArchiveDocument();
                archive.setDocument(document);
                archive.setImportBatch(batch);
                archive.setDocYear(row.year());
                archive.setDocType(row.type());
                archive.setSource(row.source());
                archive.setDownloadedOn(row.downloadedOn());
                archive.setDocNumber(row.docNumber());
                archive.setCounterparty(row.counterparty());
                archive.setAmount(row.amount());
                archive.setDescription(row.description());
                archive.setTransaction(row.transaction());
                archive.setJournalEntry(row.journalEntry());
                archive.setTextStatus(row.extraction().status());
                archive.setExtractedText(row.extraction().text());
                archive.setCreatedBy(username);
                archiveRepository.save(archive);
            }
            archiveRepository.flush();
        } catch (IOException | RuntimeException e) {
            for (String path : storedPaths) {
                try {
                    storageService.delete(path);
                } catch (IOException deleteError) {
                    e.addSuppressed(deleteError);
                }
            }
            throw new IllegalStateException("Impor arsip gagal saat menyimpan file, tidak ada dokumen yang disimpan: "
                    + e.getMessage(), e);
        }
        return new ImportResult(batch.getId(), rows.size());
    }

    private Map<String, byte[]> readZip(InputStream zip) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        long total = 0;
        try (ZipInputStream in = new ZipInputStream(zip, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = in.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory() || isMacMetadata(name)) {
                    continue;
                }
                if (name.startsWith("/") || name.contains("..") || name.contains("\\")) {
                    throw new ArchiveImportException(List.of("Nama file tidak sah di ZIP: " + name));
                }
                if (entries.size() >= maxEntries) {
                    throw new ArchiveImportException(List.of("ZIP berisi lebih dari " + maxEntries + " file"));
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int read;
                while ((read = in.read(buffer)) > 0) {
                    total += read;
                    if (total > maxTotalBytes) {
                        throw new ArchiveImportException(List.of("Isi ZIP melebihi " + maxTotalBytes + " byte"));
                    }
                    out.write(buffer, 0, read);
                }
                if (entries.put(name, out.toByteArray()) != null) {
                    throw new ArchiveImportException(List.of("File ganda di ZIP: " + name));
                }
            }
        } catch (IOException e) {
            throw new ArchiveImportException(List.of("ZIP tidak dapat dibaca: " + e.getMessage()));
        }
        if (entries.isEmpty()) {
            throw new ArchiveImportException(List.of("ZIP kosong atau bukan file ZIP"));
        }
        return entries;
    }

    private static boolean isMacMetadata(String name) {
        return name.startsWith("__MACOSX/") || name.equals(".DS_Store") || name.endsWith("/.DS_Store");
    }

    private List<ValidRow> validate(byte[] manifest, Map<String, byte[]> files, LocalDate today, List<String> errors) {
        String text = new String(manifest, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        List<ValidRow> rows = new ArrayList<>();
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true)
                .setIgnoreEmptyLines(true).get();
        try (CSVParser parser = CSVParser.parse(new StringReader(text), format)) {
            List<String> header = parser.getHeaderNames();
            for (String column : REQUIRED_COLUMNS) {
                if (!header.contains(column)) {
                    errors.add("Kolom wajib tidak ada di " + MANIFEST + ": " + column);
                }
            }
            for (String column : header) {
                if (!REQUIRED_COLUMNS.contains(column) && !OPTIONAL_COLUMNS.contains(column)) {
                    errors.add("Kolom tidak dikenal di " + MANIFEST + ": " + column);
                }
            }
            if (!errors.isEmpty()) {
                return rows;
            }
            Set<String> listed = new HashSet<>();
            Map<String, Integer> checksums = new HashMap<>();
            for (CSVRecord record : parser) {
                int rowNo = (int) record.getRecordNumber() + 1;
                List<String> rowErrors = new ArrayList<>();
                ValidRow row = validateRow(rowNo, record, files, today, listed, checksums, rowErrors);
                rowErrors.forEach(e -> errors.add("Baris " + rowNo + ": " + e));
                if (row != null && rowErrors.isEmpty()) {
                    rows.add(row);
                }
            }
            for (String name : files.keySet()) {
                if (!listed.contains(name)) {
                    errors.add("File di ZIP tidak tercantum di " + MANIFEST + ": " + name);
                }
            }
            if (!checksums.isEmpty()) {
                for (String existing : archiveRepository.findExistingChecksums(checksums.keySet())) {
                    errors.add("Baris " + checksums.get(existing) + ": file yang sama sudah ada di arsip");
                }
            }
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            errors.add(MANIFEST + " tidak dapat dibaca: " + e.getMessage());
        }
        return rows;
    }

    private ValidRow validateRow(int rowNo, CSVRecord record, Map<String, byte[]> files, LocalDate today,
                                 Set<String> listed, Map<String, Integer> checksums, List<String> errors) {
        String path = value(record, "file");
        byte[] content = null;
        String contentType = null;
        if (path == null) {
            errors.add("kolom file kosong");
        } else if (!listed.add(path)) {
            errors.add("file " + path + " tercantum lebih dari sekali");
        } else if (!files.containsKey(path)) {
            errors.add("file " + path + " tidak ada di ZIP");
        } else {
            content = files.get(path);
            contentType = CONTENT_TYPES.get(extension(path));
            if (contentType == null) {
                errors.add("jenis file " + path + " tidak didukung (PDF, JPG, PNG, GIF)");
            } else if (content.length == 0) {
                errors.add("file " + path + " kosong");
            } else if (content.length > maxFileBytes) {
                errors.add("file " + path + " melebihi " + maxFileBytes + " byte");
            } else if (!fileValidationService.validateMagicBytes(content, contentType)) {
                errors.add("isi file " + path + " tidak sesuai jenisnya (" + contentType + ")");
            }
        }

        Integer year = null;
        String yearText = value(record, "year");
        if (yearText == null) {
            errors.add("kolom year kosong");
        } else {
            try {
                year = Integer.parseInt(yearText);
                if (year < MIN_YEAR || year > MAX_YEAR || year > today.getYear()) {
                    errors.add("tahun " + yearText + " di luar " + MIN_YEAR + "-" + Math.min(MAX_YEAR, today.getYear()));
                    year = null;
                }
            } catch (NumberFormatException e) {
                errors.add("tahun bukan angka: " + yearText);
            }
        }
        ArchiveDocumentType type = enumValue(ArchiveDocumentType.class, value(record, "type"), "type", errors);
        ArchiveSource source = enumValue(ArchiveSource.class, value(record, "source"), "source", errors);

        LocalDate downloadedOn = null;
        String downloaded = value(record, "downloaded_on");
        if (downloaded == null) {
            errors.add("kolom downloaded_on kosong");
        } else {
            try {
                downloadedOn = LocalDate.parse(downloaded);
                if (downloadedOn.isAfter(today)) {
                    errors.add("downloaded_on " + downloaded + " setelah hari ini");
                }
            } catch (DateTimeParseException e) {
                errors.add("downloaded_on bukan tanggal YYYY-MM-DD: " + downloaded);
            }
        }

        BigDecimal amount = null;
        String amountText = value(record, "amount");
        if (amountText != null) {
            if (!amountText.matches("\\d+(\\.\\d{1,2})?")) {
                errors.add("amount harus angka tanpa pemisah ribuan, desimal dengan titik: " + amountText);
            } else {
                amount = new BigDecimal(amountText);
            }
        }

        String docNumber = value(record, "doc_number");
        if (docNumber != null && docNumber.length() > 100) {
            errors.add("doc_number lebih dari 100 karakter");
        }
        String counterparty = value(record, "counterparty");
        if (counterparty != null && counterparty.length() > 255) {
            errors.add("counterparty lebih dari 255 karakter");
        }

        Transaction transaction = null;
        JournalEntry line = null;
        String transactionNumber = value(record, "transaction_number");
        String accountCode = value(record, "account_code");
        if (transactionNumber != null) {
            try {
                transaction = postedTransaction(transactionNumber);
                if (accountCode != null) {
                    line = lineOnAccount(transaction, accountCode);
                }
            } catch (IllegalArgumentException e) {
                errors.add(e.getMessage());
            }
        } else if (accountCode != null) {
            errors.add("account_code diisi tanpa transaction_number");
        }

        DocumentTextExtractor.Extraction extraction = null;
        String checksum = null;
        if (errors.isEmpty()) {
            try {
                extraction = textExtractor.extract(content, contentType);
            } catch (IllegalArgumentException e) {
                errors.add(path + ": " + e.getMessage());
            }
            checksum = storageService.calculateChecksumFromBytes(content);
            Integer earlier = checksums.putIfAbsent(checksum, rowNo);
            if (earlier != null) {
                errors.add("isi file sama dengan baris " + earlier);
            }
        }
        if (!errors.isEmpty()) {
            return null;
        }
        return new ValidRow(rowNo, path, content, contentType, year, type, source, downloadedOn, docNumber, counterparty,
                amount, value(record, "description"), transaction, line, extraction, checksum);
    }

    private static String value(CSVRecord record, String column) {
        if (!record.isMapped(column) || !record.isSet(column)) {
            return null;
        }
        String v = record.get(column);
        return v == null || v.isBlank() ? null : v.strip();
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String raw, String column, List<String> errors) {
        if (raw == null) {
            errors.add("kolom " + column + " kosong");
            return null;
        }
        try {
            return Enum.valueOf(type, raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            List<String> allowed = java.util.Arrays.stream(type.getEnumConstants()).map(Enum::name).toList();
            errors.add(column + " tidak dikenal: " + raw + " (pilihan: " + String.join(", ", allowed) + ")");
            return null;
        }
    }

    private static String extension(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? "" : path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String fileName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    // ==================== ledger link ====================

    /** Links the document to a posted transaction, optionally to its line on accountCode. Blank number removes the link. */
    @Transactional
    public ArchiveDocument link(UUID archiveId, String transactionNumber, String accountCode) {
        ArchiveDocument archive = find(archiveId);
        if (transactionNumber == null || transactionNumber.isBlank()) {
            if (accountCode != null && !accountCode.isBlank()) {
                throw new IllegalArgumentException("Kode akun diisi tanpa nomor transaksi");
            }
            archive.setTransaction(null);
            archive.setJournalEntry(null);
            return archiveRepository.save(archive);
        }
        Transaction transaction = postedTransaction(transactionNumber.strip());
        archive.setTransaction(transaction);
        archive.setJournalEntry(accountCode == null || accountCode.isBlank() ? null : lineOnAccount(transaction, accountCode.strip()));
        return archiveRepository.save(archive);
    }

    private Transaction postedTransaction(String number) {
        Transaction transaction = transactionRepository.findByTransactionNumber(number)
                .orElseThrow(() -> new IllegalArgumentException("transaksi " + number + " tidak ditemukan"));
        if (transaction.getStatus() != TransactionStatus.POSTED) {
            throw new IllegalArgumentException("transaksi " + number + " berstatus " + transaction.getStatus()
                    + ", hanya transaksi terposting yang dapat ditautkan");
        }
        return transaction;
    }

    private JournalEntry lineOnAccount(Transaction transaction, String accountCode) {
        List<JournalEntry> lines = journalEntryRepository.findByTransactionIdWithAccount(transaction.getId()).stream()
                .filter(j -> accountCode.equals(j.getAccount().getAccountCode())).toList();
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("transaksi " + transaction.getTransactionNumber()
                    + " tidak punya baris jurnal pada akun " + accountCode);
        }
        if (lines.size() > 1) {
            throw new IllegalArgumentException("transaksi " + transaction.getTransactionNumber() + " punya " + lines.size()
                    + " baris jurnal pada akun " + accountCode + "; tautkan ke transaksinya saja");
        }
        return lines.getFirst();
    }

    // ==================== queries ====================

    public ArchiveDocument find(UUID id) {
        return archiveRepository.findWithLinks(id)
                .orElseThrow(() -> new EntityNotFoundException("Dokumen arsip tidak ditemukan: " + id));
    }

    public List<ArchiveDocument> forTransaction(UUID transactionId) {
        return archiveRepository.findByTransactionId(transactionId);
    }

    public long countForYear(int year) {
        return archiveRepository.countByDocYear(year);
    }

    /** Full-text search; any argument may be null. At most 200 hits. */
    public List<SearchHit> search(String query, Integer year, ArchiveDocumentType type, ArchiveSource source) {
        String q = query == null || query.isBlank() ? null : query.strip();
        List<UUID> ids = archiveRepository.search(q, year, type != null ? type.name() : null,
                source != null ? source.name() : null, SEARCH_LIMIT);
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, ArchiveDocument> byId = new HashMap<>();
        archiveRepository.findAllWithDocument(ids).forEach(a -> byId.put(a.getId(), a));
        List<SearchHit> hits = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            ArchiveDocument a = byId.get(id);
            String excerpt = q != null && a.getExtractedText() != null ? archiveRepository.headline(id, q) : null;
            hits.add(new SearchHit(a, excerpt));
        }
        return hits;
    }

    public Resource load(UUID archiveId) {
        return documentService.loadAsResource(find(archiveId).getDocument().getId());
    }
}
