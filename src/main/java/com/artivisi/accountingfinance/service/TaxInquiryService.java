package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.ArchiveDocument;
import com.artivisi.accountingfinance.entity.Document;
import com.artivisi.accountingfinance.entity.TaxDocument;
import com.artivisi.accountingfinance.entity.TaxFiling;
import com.artivisi.accountingfinance.entity.TaxInquiryCase;
import com.artivisi.accountingfinance.entity.TaxInquiryDisclosure;
import com.artivisi.accountingfinance.entity.TaxInquiryEntry;
import com.artivisi.accountingfinance.entity.TaxInquiryItem;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.SelfTestLine;
import com.artivisi.accountingfinance.enums.TaxDocumentType;
import com.artivisi.accountingfinance.enums.TaxInquiryEntryType;
import com.artivisi.accountingfinance.enums.TaxInquiryStatus;
import com.artivisi.accountingfinance.enums.TaxInquiryType;
import com.artivisi.accountingfinance.repository.ArchiveDocumentRepository;
import com.artivisi.accountingfinance.repository.DocumentRepository;
import com.artivisi.accountingfinance.repository.TaxDocumentRepository;
import com.artivisi.accountingfinance.repository.TaxFilingRepository;
import com.artivisi.accountingfinance.repository.TaxInquiryCaseRepository;
import com.artivisi.accountingfinance.repository.TaxInquiryDisclosureRepository;
import com.artivisi.accountingfinance.repository.TaxInquiryEntryRepository;
import com.artivisi.accountingfinance.repository.TaxInquiryItemRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.service.EvidenceQueryService.AttachedFile;
import com.artivisi.accountingfinance.service.EvidenceQueryService.EvidenceItem;
import com.artivisi.accountingfinance.service.EvidenceQueryService.TransactionEvidence;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Inquiry case record (issue #51): an SP2DK or pemeriksaan with its letter, deadline, items asked, replies, meeting
 * notes, documents handed over and decision.
 *
 * <p>Items, entries and disclosures are append-only (database trigger). A mistake is fixed with a CORRECTION entry
 * pointing at the entry it corrects. Status, deadline and decision on the case change only after the matching entry
 * is appended, so the entries are the history. Each disclosed file is recorded with its date, the item it answers and
 * its SHA-256; the export of a disclosure contains exactly those files plus an index.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaxInquiryService {

    public static final String EXPORT_INDEX = "index.csv";
    private static final Set<TaxInquiryEntryType> FREE_ENTRY_TYPES =
            Set.of(TaxInquiryEntryType.REPLY, TaxInquiryEntryType.MEETING, TaxInquiryEntryType.NOTE);
    private static final Set<TaxDocumentType> LETTER_TYPES =
            Set.of(TaxDocumentType.SP2DK, TaxDocumentType.STP, TaxDocumentType.TEGURAN, TaxDocumentType.SURAT);

    private final TaxInquiryCaseRepository caseRepository;
    private final TaxInquiryItemRepository itemRepository;
    private final TaxInquiryEntryRepository entryRepository;
    private final TaxInquiryDisclosureRepository disclosureRepository;
    private final TaxFilingRepository taxFilingRepository;
    private final TaxDocumentRepository taxDocumentRepository;
    private final DocumentRepository documentRepository;
    private final ArchiveDocumentRepository archiveRepository;
    private final TransactionRepository transactionRepository;
    private final DocumentService documentService;
    private final DocumentStorageService storageService;
    private final EvidenceQueryService evidenceQueryService;
    private final DocumentArchiveService archiveService;

    public record NewCase(TaxInquiryType inquiryType, Integer taxYear, String letterNumber, LocalDate letterDate,
                          LocalDate receivedDate, String taxOffice, String subject, LocalDate deadline,
                          UUID taxFilingId, UUID taxDocumentId) {}

    /** An entry with the corrections later appended to it. */
    public record EntryView(TaxInquiryEntry entry, List<TaxInquiryEntry> corrections) {}

    public record CaseView(TaxInquiryCase inquiryCase, List<TaxInquiryItem> items, List<EntryView> entries,
                           Map<UUID, List<TaxInquiryDisclosure>> disclosuresByEntry, List<TaxInquiryDisclosure> disclosures) {}

    /** A file that can be handed over, with where it comes from and earlier disclosures of the same file. */
    public record Candidate(UUID documentId, String filename, String kind, String origin, UUID transactionId,
                            String transactionNumber, UUID archiveId, List<TaxInquiryDisclosure> earlierDisclosures) {}

    // ==================== case ====================

    @Transactional
    public TaxInquiryCase create(NewCase form, MultipartFile letterFile, String username) throws IOException {
        requireUser(username);
        if (form.inquiryType() == null || form.taxYear() == null || form.letterDate() == null
                || form.receivedDate() == null || form.deadline() == null) {
            throw new IllegalArgumentException("Jenis, tahun pajak, tanggal surat, tanggal terima dan tenggat wajib diisi");
        }
        String letterNumber = required(form.letterNumber(), "Nomor surat");
        String taxOffice = required(form.taxOffice(), "Kantor pajak");
        String subject = required(form.subject(), "Pokok permintaan");
        if (form.receivedDate().isBefore(form.letterDate())) {
            throw new IllegalArgumentException("Tanggal terima tidak boleh sebelum tanggal surat");
        }
        if (form.deadline().isBefore(form.letterDate())) {
            throw new IllegalArgumentException("Tenggat tidak boleh sebelum tanggal surat");
        }
        if (caseRepository.existsByInquiryTypeAndLetterNumber(form.inquiryType(), letterNumber)) {
            throw new IllegalArgumentException("Kasus " + form.inquiryType().getIndonesianName() + " " + letterNumber + " sudah ada");
        }
        boolean hasFile = letterFile != null && !letterFile.isEmpty();
        TaxFiling filing = form.taxFilingId() != null ? taxFilingRepository.findById(form.taxFilingId())
                .orElseThrow(() -> new EntityNotFoundException("Pelaporan pajak tidak ditemukan: " + form.taxFilingId())) : null;
        Document letter;
        TaxDocument taxDocument = null;
        if (form.taxDocumentId() != null) {
            if (hasFile) {
                throw new IllegalArgumentException("Pilih salah satu: surat dari register pelaporan pajak atau unggah file surat");
            }
            taxDocument = taxDocumentRepository.findById(form.taxDocumentId())
                    .orElseThrow(() -> new EntityNotFoundException("Dokumen pajak tidak ditemukan: " + form.taxDocumentId()));
            if (!LETTER_TYPES.contains(taxDocument.getDocType())) {
                throw new IllegalArgumentException("Dokumen pajak " + taxDocument.getDocType().getIndonesianName()
                        + " bukan surat dari kantor pajak");
            }
            if (filing != null && !filing.getId().equals(taxDocument.getTaxFiling().getId())) {
                throw new IllegalArgumentException("Dokumen pajak itu milik pelaporan lain");
            }
            filing = taxDocument.getTaxFiling();
            letter = taxDocument.getDocument();
        } else if (hasFile) {
            letter = documentService.upload(letterFile, username);
        } else {
            throw new IllegalArgumentException("Surat wajib dilampirkan: unggah filenya atau pilih dari register pelaporan pajak");
        }

        TaxInquiryCase c = new TaxInquiryCase();
        c.setInquiryType(form.inquiryType());
        c.setTaxYear(form.taxYear());
        c.setLetterNumber(letterNumber);
        c.setLetterDate(form.letterDate());
        c.setReceivedDate(form.receivedDate());
        c.setTaxOffice(taxOffice);
        c.setSubject(subject);
        c.setDeadline(form.deadline());
        c.setStatus(TaxInquiryStatus.OPEN);
        c.setLetterDocument(letter);
        c.setTaxFiling(filing);
        c.setTaxDocument(taxDocument);
        c.setCreatedBy(username);
        return caseRepository.save(c);
    }

    public TaxInquiryCase find(UUID id) {
        return caseRepository.findWithLinks(id).orElseThrow(() -> new EntityNotFoundException("Kasus tidak ditemukan: " + id));
    }

    public List<TaxInquiryCase> findByYear(Integer year) {
        return caseRepository.findByYear(year);
    }

    public List<TaxInquiryCase> findByTaxFilings(List<UUID> filingIds) {
        return filingIds.isEmpty() ? List.of() : caseRepository.findByTaxFilingIds(filingIds);
    }

    /** Letters from the tax office in the filing register that a case can be opened from. */
    public List<TaxDocument> letterDocuments(int year) {
        List<TaxDocument> letters = new ArrayList<>();
        for (TaxFiling filing : taxFilingRepository.findByFilters(null, year)) {
            taxDocumentRepository.findByFilingId(filing.getId()).stream()
                    .filter(d -> LETTER_TYPES.contains(d.getDocType())).forEach(letters::add);
        }
        return letters;
    }

    public CaseView view(UUID caseId) {
        TaxInquiryCase c = find(caseId);
        List<TaxInquiryEntry> all = entryRepository.findByCase(caseId);
        Map<UUID, List<TaxInquiryEntry>> corrections = new LinkedHashMap<>();
        for (TaxInquiryEntry e : all) {
            if (e.getCorrects() != null) {
                corrections.computeIfAbsent(e.getCorrects().getId(), k -> new ArrayList<>()).add(e);
            }
        }
        List<EntryView> entries = all.stream()
                .map(e -> new EntryView(e, corrections.getOrDefault(e.getId(), List.of()))).toList();
        List<TaxInquiryDisclosure> disclosures = disclosureRepository.findByCase(caseId);
        Map<UUID, List<TaxInquiryDisclosure>> byEntry = disclosures.stream()
                .collect(Collectors.groupingBy(d -> d.getEntry().getId(), LinkedHashMap::new, Collectors.toList()));
        return new CaseView(c, itemRepository.findByInquiryCaseIdOrderByItemNoAsc(caseId), entries, byEntry, disclosures);
    }

    // ==================== items and entries ====================

    @Transactional
    public TaxInquiryItem addItem(UUID caseId, String description, SelfTestLine line, Integer month, String username) {
        requireUser(username);
        TaxInquiryCase c = requireNotClosed(caseId);
        if ((line == null) != (month == null)) {
            throw new IllegalArgumentException("Baris uji mandiri dan bulannya diisi bersama");
        }
        if (line != null && (month < 0 || month > 12 || (!line.isPerMasa() && month != 0))) {
            throw new IllegalArgumentException("Bulan " + month + " tidak berlaku untuk " + line.getTitle());
        }
        TaxInquiryItem item = new TaxInquiryItem();
        item.setInquiryCase(c);
        item.setItemNo(itemRepository.maxItemNo(caseId) + 1);
        item.setDescription(required(description, "Uraian permintaan"));
        item.setSelfTestLine(line);
        item.setPeriodMonth(month);
        item.setCreatedBy(username);
        return itemRepository.save(item);
    }

    /** Reply sent, meeting note or note. A reply may carry the letter sent as a file and the item it answers. */
    @Transactional
    public TaxInquiryEntry addEntry(UUID caseId, TaxInquiryEntryType type, LocalDate date, String content, UUID itemId,
                                    MultipartFile file, String username) throws IOException {
        requireUser(username);
        if (type == null || !FREE_ENTRY_TYPES.contains(type)) {
            throw new IllegalArgumentException("Jenis entri harus Balasan, Catatan pertemuan atau Catatan");
        }
        TaxInquiryCase c = find(caseId);
        TaxInquiryEntry entry = newEntry(c, type, date, content, username);
        entry.setItem(itemId != null ? item(c, itemId) : null);
        if (file != null && !file.isEmpty()) {
            entry.setDocument(documentService.upload(file, username));
        }
        return entryRepository.save(entry);
    }

    @Transactional
    public TaxInquiryEntry correct(UUID caseId, UUID entryId, String content, String username) {
        requireUser(username);
        TaxInquiryCase c = find(caseId);
        TaxInquiryEntry target = entryRepository.findById(entryId)
                .filter(e -> e.getInquiryCase().getId().equals(caseId))
                .orElseThrow(() -> new EntityNotFoundException("Entri tidak ditemukan di kasus ini: " + entryId));
        TaxInquiryEntry entry = newEntry(c, TaxInquiryEntryType.CORRECTION, LocalDate.now(), content, username);
        entry.setCorrects(target);
        entry.setItem(target.getItem());
        return entryRepository.save(entry);
    }

    @Transactional
    public TaxInquiryEntry changeDeadline(UUID caseId, LocalDate newDeadline, String reason, String username) {
        requireUser(username);
        TaxInquiryCase c = requireNotClosed(caseId);
        if (newDeadline == null) {
            throw new IllegalArgumentException("Tenggat baru wajib diisi");
        }
        if (newDeadline.isBefore(c.getLetterDate())) {
            throw new IllegalArgumentException("Tenggat tidak boleh sebelum tanggal surat");
        }
        TaxInquiryEntry entry = newEntry(c, TaxInquiryEntryType.DEADLINE_CHANGE, LocalDate.now(),
                "Tenggat " + c.getDeadline() + " -> " + newDeadline + ": " + required(reason, "Alasan"), username);
        entry.setNewDeadline(newDeadline);
        entry = entryRepository.save(entry);
        c.setDeadline(newDeadline);
        caseRepository.save(c);
        return entry;
    }

    /** OPEN or RESPONDED. A case is closed only by {@link #recordDecision}. */
    @Transactional
    public TaxInquiryEntry changeStatus(UUID caseId, TaxInquiryStatus status, String reason, String username) {
        requireUser(username);
        TaxInquiryCase c = requireNotClosed(caseId);
        if (status == null || status == TaxInquiryStatus.CLOSED) {
            throw new IllegalArgumentException("Kasus ditutup dengan mencatat keputusan");
        }
        if (status == c.getStatus()) {
            throw new IllegalArgumentException("Status kasus sudah " + status.getIndonesianName());
        }
        TaxInquiryEntry entry = newEntry(c, TaxInquiryEntryType.STATUS_CHANGE, LocalDate.now(),
                c.getStatus().getIndonesianName() + " -> " + status.getIndonesianName() + ": " + required(reason, "Alasan"), username);
        entry.setNewStatus(status);
        entry = entryRepository.save(entry);
        c.setStatus(status);
        caseRepository.save(c);
        return entry;
    }

    /** Records the tax office's decision (e.g. SP2DK closed by LHP2DK, SKP issued) and closes the case. */
    @Transactional
    public TaxInquiryEntry recordDecision(UUID caseId, LocalDate decidedOn, String decision, String username) {
        requireUser(username);
        TaxInquiryCase c = requireNotClosed(caseId);
        if (decidedOn == null) {
            throw new IllegalArgumentException("Tanggal keputusan wajib diisi");
        }
        if (decidedOn.isBefore(c.getLetterDate()) || decidedOn.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Tanggal keputusan harus antara tanggal surat dan hari ini");
        }
        TaxInquiryEntry entry = newEntry(c, TaxInquiryEntryType.DECISION, decidedOn, decision, username);
        entry = entryRepository.save(entry);
        c.setDecision(entry.getContent());
        c.setClosedOn(decidedOn);
        c.setStatus(TaxInquiryStatus.CLOSED);
        caseRepository.save(c);
        return entry;
    }

    // ==================== disclosure and export ====================

    /** Files that can be handed over: those of the given transactions and archive hits of the query. */
    public List<Candidate> candidates(List<String> transactionNumbers, String archiveQuery, Integer archiveYear) {
        Map<UUID, Candidate> byDocument = new LinkedHashMap<>();
        for (String number : transactionNumbers) {
            Transaction t = transactionRepository.findByTransactionNumber(number)
                    .orElseThrow(() -> new IllegalArgumentException("Transaksi " + number + " tidak ditemukan"));
            TransactionEvidence evidence = evidenceQueryService.evidenceFor(t.getId());
            for (AttachedFile f : evidence.files()) {
                byDocument.putIfAbsent(f.documentId(), candidate(f.documentId(), f.filename(),
                        f.evidenceType() != null ? f.evidenceType().getIndonesianName() : "Belum diklasifikasikan",
                        "Lampiran transaksi", t, null));
            }
            for (EvidenceItem item : evidence.evidence()) {
                if (item.documentId() != null && !byDocument.containsKey(item.documentId())) {
                    Document d = documentRepository.findById(item.documentId()).orElseThrow();
                    byDocument.put(d.getId(), candidate(d.getId(), d.getOriginalFilename(), item.evidenceType().getIndonesianName(),
                            "Kontrak " + item.reference(), t, null));
                }
            }
            for (ArchiveDocument a : archiveService.forTransaction(t.getId())) {
                byDocument.putIfAbsent(a.getDocument().getId(), archiveCandidate(a));
            }
        }
        if ((archiveQuery != null && !archiveQuery.isBlank()) || archiveYear != null) {
            for (DocumentArchiveService.SearchHit hit : archiveService.search(archiveQuery, archiveYear, null, null)) {
                byDocument.putIfAbsent(hit.document().getDocument().getId(), archiveCandidate(hit.document()));
            }
        }
        Map<UUID, List<TaxInquiryDisclosure>> earlier = byDocument.isEmpty() ? Map.of()
                : disclosureRepository.findByDocumentIds(byDocument.keySet()).stream()
                        .collect(Collectors.groupingBy(d -> d.getDocument().getId()));
        return byDocument.values().stream().map(c -> new Candidate(c.documentId(), c.filename(), c.kind(), c.origin(),
                c.transactionId(), c.transactionNumber(), c.archiveId(), earlier.getOrDefault(c.documentId(), List.of()))).toList();
    }

    private Candidate candidate(UUID documentId, String filename, String kind, String origin, Transaction t, UUID archiveId) {
        return new Candidate(documentId, filename, kind, origin, t != null ? t.getId() : null,
                t != null ? t.getTransactionNumber() : null, archiveId, List.of());
    }

    private Candidate archiveCandidate(ArchiveDocument a) {
        String origin = "Arsip " + a.getDocYear() + ", " + a.getSource().getIndonesianName() + ", diunduh " + a.getDownloadedOn();
        return candidate(a.getDocument().getId(), a.getDocument().getOriginalFilename(), a.getDocType().getIndonesianName(),
                origin, a.getTransaction(), a.getId());
    }

    /**
     * Records the hand-over of the selected files: one DISCLOSURE entry and one disclosure row per file, each with
     * the item it answers. Returns the entry; its export is {@link #exportDisclosure}.
     */
    @Transactional
    public TaxInquiryEntry disclose(UUID caseId, Map<UUID, UUID> itemByDocument, LocalDate disclosedOn, String note,
                                    String username) {
        requireUser(username);
        TaxInquiryCase c = requireNotClosed(caseId);
        if (itemByDocument == null || itemByDocument.isEmpty()) {
            throw new IllegalArgumentException("Pilih minimal satu dokumen dan permintaan yang dijawabnya");
        }
        if (disclosedOn == null) {
            throw new IllegalArgumentException("Tanggal penyerahan wajib diisi");
        }
        if (disclosedOn.isBefore(c.getLetterDate()) || disclosedOn.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Tanggal penyerahan harus antara tanggal surat dan hari ini");
        }
        Map<UUID, TaxInquiryItem> items = new LinkedHashMap<>();
        List<Document> documents = new ArrayList<>();
        for (Map.Entry<UUID, UUID> selection : itemByDocument.entrySet()) {
            if (selection.getValue() == null) {
                throw new IllegalArgumentException("Setiap dokumen yang dipilih harus dikaitkan dengan satu permintaan");
            }
            items.computeIfAbsent(selection.getValue(), id -> item(c, id));
            documents.add(documentRepository.findById(selection.getKey())
                    .orElseThrow(() -> new EntityNotFoundException("Dokumen tidak ditemukan: " + selection.getKey())));
        }
        String content = note != null && !note.isBlank() ? note.strip()
                : documents.size() + " dokumen diserahkan untuk permintaan "
                + items.values().stream().map(i -> String.valueOf(i.getItemNo())).sorted().collect(Collectors.joining(", "));
        TaxInquiryEntry entry = entryRepository.save(newEntry(c, TaxInquiryEntryType.DISCLOSURE, disclosedOn, content, username));
        for (Document d : documents) {
            ArchiveDocument archive = archiveRepository.findByDocumentId(d.getId()).orElse(null);
            TaxInquiryDisclosure disclosure = new TaxInquiryDisclosure();
            disclosure.setInquiryCase(c);
            disclosure.setEntry(entry);
            disclosure.setItem(items.get(itemByDocument.get(d.getId())));
            disclosure.setDocument(d);
            disclosure.setArchiveDocument(archive);
            disclosure.setTransaction(owningTransaction(d, archive));
            disclosure.setDisclosedOn(disclosedOn);
            disclosure.setFilename(d.getOriginalFilename());
            disclosure.setChecksumSha256(d.getChecksumSha256());
            disclosure.setFileSize(d.getFileSize());
            disclosure.setCreatedBy(username);
            disclosureRepository.save(disclosure);
        }
        return entry;
    }

    private static Transaction owningTransaction(Document d, ArchiveDocument archive) {
        if (d.getTransaction() != null) {
            return d.getTransaction();
        }
        if (d.getJournalEntry() != null) {
            return d.getJournalEntry().getTransaction();
        }
        if (d.getInvoice() != null && d.getInvoice().getTransaction() != null) {
            return d.getInvoice().getTransaction();
        }
        return archive != null ? archive.getTransaction() : null;
    }

    /** A file of the case: its letter, a file attached to an entry, or a disclosed file. */
    public Document caseDocument(UUID caseId, UUID documentId) {
        TaxInquiryCase c = find(caseId);
        boolean member = c.getLetterDocument().getId().equals(documentId)
                || entryRepository.findByCase(caseId).stream().anyMatch(e -> e.getDocument() != null && e.getDocument().getId().equals(documentId))
                || disclosureRepository.findByCase(caseId).stream().anyMatch(d -> d.getDocument().getId().equals(documentId));
        if (!member) {
            throw new EntityNotFoundException("Dokumen " + documentId + " bukan bagian dari kasus ini");
        }
        return documentRepository.findById(documentId).orElseThrow();
    }

    public TaxInquiryEntry findDisclosureEntry(UUID caseId, UUID entryId) {
        return entryRepository.findById(entryId)
                .filter(e -> e.getInquiryCase().getId().equals(caseId) && e.getEntryType() == TaxInquiryEntryType.DISCLOSURE)
                .orElseThrow(() -> new EntityNotFoundException("Penyerahan tidak ditemukan di kasus ini: " + entryId));
    }

    /**
     * ZIP of one disclosure: {@value #EXPORT_INDEX} and the disclosed files under dokumen/. A file whose content no
     * longer matches the recorded SHA-256 fails the export.
     */
    public void exportDisclosure(UUID caseId, UUID entryId, OutputStream out) throws IOException {
        TaxInquiryCase c = find(caseId);
        TaxInquiryEntry entry = findDisclosureEntry(caseId, entryId);
        List<TaxInquiryDisclosure> rows = disclosureRepository.findByEntry(entryId);
        if (rows.isEmpty()) {
            throw new IllegalStateException("Penyerahan " + entry.getId() + " tidak memiliki dokumen");
        }
        Csv index = new Csv("no_permintaan", "permintaan", "file_zip", "nama_asli", "sha256", "ukuran", "asal",
                "nomor_transaksi", "tanggal_serah", "surat");
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (TaxInquiryDisclosure d : rows) {
            byte[] content;
            try (InputStream in = storageService.loadAsResource(d.getDocument().getStoragePath()).getInputStream()) {
                content = in.readAllBytes();
            }
            String checksum = storageService.calculateChecksumFromBytes(content);
            if (!checksum.equals(d.getChecksumSha256())) {
                throw new IllegalStateException("Isi file " + d.getFilename() + " berbeda dengan yang tercatat saat diserahkan");
            }
            String path = "dokumen/" + d.getItem().getItemNo() + "/" + d.getDocument().getId() + "-" + safe(d.getFilename());
            files.put(path, content);
            ArchiveDocument a = d.getArchiveDocument();
            String origin = a != null ? "Arsip " + a.getDocType().getIndonesianName() + " " + a.getDocYear() + " ("
                    + a.getSource().getIndonesianName() + ", diunduh " + a.getDownloadedOn() + ")" : "Lampiran transaksi";
            index.row(d.getItem().getItemNo(), d.getItem().getDescription(), path, d.getFilename(), d.getChecksumSha256(),
                    d.getFileSize(), origin, d.getTransaction() != null ? d.getTransaction().getTransactionNumber() : null,
                    d.getDisclosedOn(), c.getTitle());
        }
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(EXPORT_INDEX));
            zip.write(index.bytes());
            zip.closeEntry();
            for (Map.Entry<String, byte[]> f : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(f.getKey()));
                zip.write(f.getValue());
                zip.closeEntry();
            }
        }
    }

    public String exportFilename(UUID caseId, UUID entryId) {
        TaxInquiryCase c = find(caseId);
        TaxInquiryEntry e = findDisclosureEntry(caseId, entryId);
        return "penyerahan-" + safe(c.getLetterNumber()) + "-" + e.getEntryDate() + ".zip";
    }

    // ==================== helpers ====================

    private TaxInquiryEntry newEntry(TaxInquiryCase c, TaxInquiryEntryType type, LocalDate date, String content, String username) {
        if (date == null) {
            throw new IllegalArgumentException("Tanggal entri wajib diisi");
        }
        if (date.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Tanggal entri tidak boleh setelah hari ini");
        }
        TaxInquiryEntry e = new TaxInquiryEntry();
        e.setInquiryCase(c);
        e.setEntryType(type);
        e.setEntryDate(date);
        e.setContent(required(content, "Isi entri"));
        e.setCreatedBy(username);
        return e;
    }

    private TaxInquiryItem item(TaxInquiryCase c, UUID itemId) {
        return itemRepository.findById(itemId).filter(i -> i.getInquiryCase().getId().equals(c.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Permintaan tidak ditemukan di kasus ini: " + itemId));
    }

    private TaxInquiryCase requireNotClosed(UUID caseId) {
        TaxInquiryCase c = find(caseId);
        if (c.getStatus() == TaxInquiryStatus.CLOSED) {
            throw new IllegalStateException("Kasus " + c.getTitle() + " sudah selesai");
        }
        return c;
    }

    private static void requireUser(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Pengguna wajib diisi");
        }
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " wajib diisi");
        }
        return value.strip();
    }

    private static String safe(String name) {
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    /** Minimal RFC 4180 writer; text a spreadsheet would read as a formula is prefixed with a quote. */
    private static final class Csv {
        private final StringBuilder sb = new StringBuilder();

        Csv(String... header) {
            row((Object[]) header);
        }

        void row(Object... values) {
            List<String> cells = new ArrayList<>(values.length);
            for (Object v : values) {
                String s = v == null ? "" : v instanceof BigDecimal b ? b.toPlainString() : v.toString();
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
