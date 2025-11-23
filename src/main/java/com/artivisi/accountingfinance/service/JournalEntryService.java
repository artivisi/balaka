package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.enums.NormalBalance;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JournalEntryService {

    private final JournalEntryRepository journalEntryRepository;
    private final ChartOfAccountRepository chartOfAccountRepository;

    public JournalEntry findById(UUID id) {
        return journalEntryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Journal entry not found with id: " + id));
    }

    public JournalEntry findByJournalNumber(String journalNumber) {
        return journalEntryRepository.findByJournalNumber(journalNumber)
                .orElseThrow(() -> new EntityNotFoundException("Journal entry not found with number: " + journalNumber));
    }

    public List<JournalEntry> findByTransactionId(UUID transactionId) {
        return journalEntryRepository.findByTransactionIdOrderByJournalNumberAsc(transactionId);
    }

    public Page<JournalEntry> findAllByDateRange(LocalDate startDate, LocalDate endDate, Pageable pageable) {
        return journalEntryRepository.findAllPostedEntriesByDateRange(startDate, endDate, pageable);
    }

    public List<JournalEntry> findByAccountAndDateRange(UUID accountId, LocalDate startDate, LocalDate endDate) {
        return journalEntryRepository.findPostedEntriesByAccountAndDateRange(accountId, startDate, endDate);
    }

    public GeneralLedgerData getGeneralLedger(UUID accountId, LocalDate startDate, LocalDate endDate) {
        ChartOfAccount account = chartOfAccountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found"));

        BigDecimal openingDebit = journalEntryRepository.sumDebitBeforeDate(accountId, startDate);
        BigDecimal openingCredit = journalEntryRepository.sumCreditBeforeDate(accountId, startDate);

        BigDecimal openingBalance;
        if (account.getNormalBalance() == NormalBalance.DEBIT) {
            openingBalance = openingDebit.subtract(openingCredit);
        } else {
            openingBalance = openingCredit.subtract(openingDebit);
        }

        List<JournalEntry> entries = journalEntryRepository
                .findPostedEntriesByAccountAndDateRange(accountId, startDate, endDate);

        BigDecimal runningBalance = openingBalance;
        List<LedgerLineItem> lineItems = new ArrayList<>();

        for (JournalEntry entry : entries) {
            if (account.getNormalBalance() == NormalBalance.DEBIT) {
                runningBalance = runningBalance.add(entry.getDebitAmount()).subtract(entry.getCreditAmount());
            } else {
                runningBalance = runningBalance.subtract(entry.getDebitAmount()).add(entry.getCreditAmount());
            }

            lineItems.add(new LedgerLineItem(entry, runningBalance));
        }

        BigDecimal totalDebit = journalEntryRepository.sumDebitByAccountAndDateRange(accountId, startDate, endDate);
        BigDecimal totalCredit = journalEntryRepository.sumCreditByAccountAndDateRange(accountId, startDate, endDate);

        return new GeneralLedgerData(
                account,
                openingBalance,
                totalDebit,
                totalCredit,
                runningBalance,
                lineItems
        );
    }

    public record GeneralLedgerData(
            ChartOfAccount account,
            BigDecimal openingBalance,
            BigDecimal totalDebit,
            BigDecimal totalCredit,
            BigDecimal closingBalance,
            List<LedgerLineItem> entries
    ) {}

    public record LedgerLineItem(
            JournalEntry entry,
            BigDecimal runningBalance
    ) {}
}
