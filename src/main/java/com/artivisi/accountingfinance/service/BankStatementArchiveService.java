package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.BankStatementArchive;
import com.artivisi.accountingfinance.entity.CompanyBankAccount;
import com.artivisi.accountingfinance.entity.Document;
import com.artivisi.accountingfinance.entity.StatementArchiveStatus;
import com.artivisi.accountingfinance.repository.BankStatementArchiveRepository;
import com.artivisi.accountingfinance.repository.CompanyBankAccountRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Monthly bank statement archive per company bank account.
 *
 * <p>Expected months for an active account run from the earlier of its first posted journal line on the account's
 * GL account and its first archived month, up to the last month that ended more than {@code graceDays} ago. An
 * account with neither has no expected month. Inactive accounts are not checked.
 *
 * <p>Retention: the file is kept until the end of the statement month plus {@code app.evidence.retention-years}
 * years (UU KUP Pasal 28 ayat 11, unverified). Deletion before that date is refused; a replacement keeps the earlier
 * row and file as SUPERSEDED.
 */
@Service
@Slf4j
@Transactional(readOnly = true)
public class BankStatementArchiveService {

    private final BankStatementArchiveRepository archiveRepository;
    private final CompanyBankAccountRepository bankAccountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final DocumentService documentService;
    private final int retentionYears;

    public BankStatementArchiveService(BankStatementArchiveRepository archiveRepository,
                                       CompanyBankAccountRepository bankAccountRepository,
                                       JournalEntryRepository journalEntryRepository,
                                       DocumentService documentService,
                                       @Value("${app.evidence.retention-years}") int retentionYears) {
        this.archiveRepository = archiveRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.documentService = documentService;
        this.retentionYears = retentionYears;
    }

    @jakarta.annotation.PostConstruct
    void validateConfiguration() {
        if (retentionYears <= 0) {
            throw new IllegalStateException("app.evidence.retention-years must be positive, got " + retentionYears);
        }
    }

    public enum MonthStatus { ARCHIVED, MISSING, NOT_DUE, NOT_EXPECTED }

    public record MissingStatement(UUID bankAccountId, String bankName, String accountName, YearMonth month) {}

    public record MonthCell(YearMonth month, MonthStatus status, BankStatementArchive archive) {}

    public record AccountRow(CompanyBankAccount account, List<MonthCell> months) {}

    public int retentionYears() {
        return retentionYears;
    }

    public LocalDate retentionUntil(YearMonth month) {
        return month.atEndOfMonth().plusYears(retentionYears);
    }

    @Transactional
    public BankStatementArchive archive(UUID bankAccountId, YearMonth month, MultipartFile file, String username,
                                        LocalDate today) throws IOException {
        CompanyBankAccount account = bankAccountRepository.findById(bankAccountId)
                .orElseThrow(() -> new EntityNotFoundException("Rekening bank tidak ditemukan: " + bankAccountId));
        if (month == null) {
            throw new IllegalArgumentException("Bulan rekening koran wajib diisi");
        }
        if (!month.atEndOfMonth().isBefore(today)) {
            throw new IllegalArgumentException("Rekening koran " + month + " belum dapat diarsipkan: bulan belum berakhir");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File rekening koran wajib diunggah");
        }
        LocalDate period = month.atDay(1);
        Optional<BankStatementArchive> current = archiveRepository
                .findByBankAccountIdAndPeriodMonthAndStatus(bankAccountId, period, StatementArchiveStatus.ACTIVE);
        Document document = documentService.upload(file, username);
        current.ifPresent(previous -> {
            previous.setStatus(StatementArchiveStatus.SUPERSEDED);
            previous.setSupersededAt(LocalDateTime.now());
            archiveRepository.saveAndFlush(previous);
        });
        BankStatementArchive archive = new BankStatementArchive();
        archive.setBankAccount(account);
        archive.setPeriodMonth(period);
        archive.setDocument(document);
        archive.setRetentionUntil(retentionUntil(month));
        archive.setStatus(StatementArchiveStatus.ACTIVE);
        archive.setUploadedBy(username);
        BankStatementArchive saved = archiveRepository.save(archive);
        log.info("Bank statement archive {} stored{}", saved.getId(),
                current.isPresent() ? " (replaces an earlier file)" : "");
        return saved;
    }

    /** Removes an archived statement and its file. Refused while the retention date has not passed. */
    @Transactional
    public void delete(UUID archiveId, LocalDate today) throws IOException {
        BankStatementArchive archive = archiveRepository.findById(archiveId)
                .orElseThrow(() -> new EntityNotFoundException("Arsip rekening koran tidak ditemukan: " + archiveId));
        if (archive.isRetainedOn(today)) {
            throw new IllegalStateException("Rekening koran " + archive.getYearMonth() + " wajib disimpan sampai "
                    + archive.getRetentionUntil() + "; tidak dapat dihapus sebelum tanggal tersebut");
        }
        UUID documentId = archive.getDocument().getId();
        archiveRepository.delete(archive);
        archiveRepository.flush();
        documentService.delete(documentId);
        log.info("Bank statement archive {} deleted after retention", archive.getId());
    }

    public List<BankStatementArchive> archivesForYear(int year) {
        return archiveRepository.findByPeriodBetween(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 1));
    }

    /** Months that should have a statement by {@code asOf} and have none, oldest first per account. */
    public List<MissingStatement> missingStatements(LocalDate asOf, int graceDays) {
        if (graceDays < 0) {
            throw new IllegalArgumentException("Tenggang hari tidak boleh negatif");
        }
        YearMonth lastDue = lastDueMonth(asOf, graceDays);
        List<MissingStatement> missing = new ArrayList<>();
        for (CompanyBankAccount account : bankAccountRepository.findByActiveTrueOrderByBankNameAsc()) {
            Optional<YearMonth> first = firstExpectedMonth(account);
            if (first.isEmpty()) {
                continue;
            }
            Map<YearMonth, BankStatementArchive> archived = activeByMonth(account.getId());
            for (YearMonth m = first.get(); !m.isAfter(lastDue); m = m.plusMonths(1)) {
                if (!archived.containsKey(m)) {
                    missing.add(new MissingStatement(account.getId(), account.getBankName(), account.getAccountName(), m));
                }
            }
        }
        return missing;
    }

    /** Status of each month of {@code year} for every active account. */
    public List<AccountRow> yearGrid(int year, LocalDate asOf, int graceDays) {
        YearMonth lastDue = lastDueMonth(asOf, graceDays);
        List<AccountRow> rows = new ArrayList<>();
        for (CompanyBankAccount account : bankAccountRepository.findByActiveTrueOrderByBankNameAsc()) {
            Optional<YearMonth> first = firstExpectedMonth(account);
            Map<YearMonth, BankStatementArchive> archived = activeByMonth(account.getId());
            List<MonthCell> cells = new ArrayList<>();
            for (int m = 1; m <= 12; m++) {
                YearMonth month = YearMonth.of(year, m);
                BankStatementArchive archive = archived.get(month);
                MonthStatus status;
                if (archive != null) {
                    status = MonthStatus.ARCHIVED;
                } else if (first.isEmpty() || month.isBefore(first.get())) {
                    status = MonthStatus.NOT_EXPECTED;
                } else if (month.isAfter(lastDue)) {
                    status = MonthStatus.NOT_DUE;
                } else {
                    status = MonthStatus.MISSING;
                }
                cells.add(new MonthCell(month, status, archive));
            }
            rows.add(new AccountRow(account, cells));
        }
        return rows;
    }

    private static YearMonth lastDueMonth(LocalDate asOf, int graceDays) {
        if (asOf == null) {
            throw new IllegalArgumentException("Tanggal acuan wajib diisi");
        }
        YearMonth month = YearMonth.from(asOf);
        while (!month.atEndOfMonth().plusDays(graceDays).isBefore(asOf)) {
            month = month.minusMonths(1);
        }
        return month;
    }

    private Optional<YearMonth> firstExpectedMonth(CompanyBankAccount account) {
        Optional<LocalDate> firstJournal = account.getGlAccount() == null ? Optional.empty()
                : journalEntryRepository.findFirstPostedDateOnAccount(account.getGlAccount().getId());
        Optional<LocalDate> firstArchive = archiveRepository.findEarliestPeriod(account.getId());
        return firstJournal.or(() -> firstArchive)
                .map(d -> firstArchive.filter(a -> a.isBefore(d)).orElse(d))
                .map(YearMonth::from);
    }

    private Map<YearMonth, BankStatementArchive> activeByMonth(UUID accountId) {
        Map<YearMonth, BankStatementArchive> map = new HashMap<>();
        for (BankStatementArchive a : archiveRepository.findActiveByBankAccount(accountId)) {
            map.put(a.getYearMonth(), a);
        }
        return map;
    }
}
