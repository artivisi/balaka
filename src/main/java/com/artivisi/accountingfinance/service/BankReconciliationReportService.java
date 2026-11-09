package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.BankReconciliation;
import com.artivisi.accountingfinance.entity.BankStatementItem;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.ReconciliationItem;
import com.artivisi.accountingfinance.enums.StatementItemMatchStatus;
import com.artivisi.accountingfinance.repository.BankStatementItemRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.ReconciliationItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BankReconciliationReportService {

    private final BankReconciliationService reconciliationService;
    private final BankStatementItemRepository statementItemRepository;
    private final ReconciliationItemRepository reconciliationItemRepository;
    private final JournalEntryRepository journalEntryRepository;

    public Map<String, Object> getSummary(UUID reconciliationId) {
        BankReconciliation recon = reconciliationService.findById(reconciliationId);
        List<ReconciliationItem> items = reconciliationItemRepository
                .findByReconciliationIdWithRelations(reconciliationId);

        long matched = items.stream()
                .filter(i -> i.getMatchStatus() == StatementItemMatchStatus.MATCHED).count();
        long bankOnly = items.stream()
                .filter(i -> i.getMatchStatus() == StatementItemMatchStatus.BANK_ONLY).count();
        long bookOnly = items.stream()
                .filter(i -> i.getMatchStatus() == StatementItemMatchStatus.BOOK_ONLY).count();

        BigDecimal matchedAmount = items.stream()
                .filter(i -> i.getMatchStatus() == StatementItemMatchStatus.MATCHED && i.getStatementItem() != null)
                .map(i -> i.getStatementItem().getNetAmount().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal bankOnlyAmount = items.stream()
                .filter(i -> i.getMatchStatus() == StatementItemMatchStatus.BANK_ONLY && i.getStatementItem() != null)
                .map(i -> i.getStatementItem().getNetAmount().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal bookOnlyAmount = items.stream()
                .filter(i -> i.getMatchStatus() == StatementItemMatchStatus.BOOK_ONLY && i.getTransaction() != null)
                .map(i -> i.getTransaction().getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return Map.of(
                "matchedCount", matched,
                "bankOnlyCount", bankOnly,
                "bookOnlyCount", bookOnly,
                "totalItems", requireComputed(recon.getTotalStatementItems(), "total_statement_items", recon),
                "matchedAmount", matchedAmount,
                "bankOnlyAmount", bankOnlyAmount,
                "bookOnlyAmount", bookOnlyAmount,
                "bookBalance", requireComputed(recon.getBookBalance(), "book_balance", recon),
                "bankBalance", requireComputed(recon.getBankBalance(), "bank_balance", recon)
        );
    }

    public Map<String, Object> getReconciliationStatement(UUID reconciliationId) {
        BankReconciliation recon = reconciliationService.findById(reconciliationId);
        List<ReconciliationItem> items = reconciliationItemRepository
                .findByReconciliationIdWithRelations(reconciliationId);

        BigDecimal bookBalance = requireComputed(recon.getBookBalance(), "book_balance", recon);
        BigDecimal bankBalance = requireComputed(recon.getBankBalance(), "bank_balance", recon);

        BigDecimal[] bankOnlyTotals = computeBankOnlyTotals(items);
        BigDecimal bankOnlyDebitTotal = bankOnlyTotals[0];
        BigDecimal bankOnlyCreditTotal = bankOnlyTotals[1];
        BigDecimal[] bookOnlyTotals = computeBookOnlyTotals(items, recon.getBankAccount().getGlAccount().getId());
        BigDecimal bookOnlyDebitTotal = bookOnlyTotals[0];
        BigDecimal bookOnlyCreditTotal = bookOnlyTotals[1];

        BigDecimal adjustedBookBalance = bookBalance.add(bankOnlyCreditTotal).subtract(bankOnlyDebitTotal);
        BigDecimal adjustedBankBalance = bankBalance.add(bookOnlyDebitTotal).subtract(bookOnlyCreditTotal);
        BigDecimal difference = adjustedBookBalance.subtract(adjustedBankBalance);

        return Map.of(
                "bookBalance", bookBalance,
                "bankBalance", bankBalance,
                "bankOnlyDebitTotal", bankOnlyDebitTotal,
                "bankOnlyCreditTotal", bankOnlyCreditTotal,
                "bookOnlyDebitTotal", bookOnlyDebitTotal,
                "bookOnlyCreditTotal", bookOnlyCreditTotal,
                "adjustedBookBalance", adjustedBookBalance,
                "adjustedBankBalance", adjustedBankBalance,
                "difference", difference
        );
    }

    private BigDecimal[] computeBankOnlyTotals(List<ReconciliationItem> items) {
        BigDecimal debitTotal = BigDecimal.ZERO;
        BigDecimal creditTotal = BigDecimal.ZERO;
        for (ReconciliationItem ri : items) {
            if (ri.getMatchStatus() != StatementItemMatchStatus.BANK_ONLY || ri.getStatementItem() == null) {
                continue;
            }
            BankStatementItem si = ri.getStatementItem();
            if (si.getDebitAmount() != null && si.getDebitAmount().compareTo(BigDecimal.ZERO) > 0) {
                debitTotal = debitTotal.add(si.getDebitAmount());
            }
            if (si.getCreditAmount() != null && si.getCreditAmount().compareTo(BigDecimal.ZERO) > 0) {
                creditTotal = creditTotal.add(si.getCreditAmount());
            }
        }
        return new BigDecimal[]{debitTotal, creditTotal};
    }

    /**
     * Book-only transactions split by their effect on the bank GL account: debits are deposits
     * the bank has not credited yet, credits are payments the bank has not debited yet.
     */
    private BigDecimal[] computeBookOnlyTotals(List<ReconciliationItem> items, UUID glAccountId) {
        BigDecimal debitTotal = BigDecimal.ZERO;
        BigDecimal creditTotal = BigDecimal.ZERO;
        for (ReconciliationItem ri : items) {
            if (ri.getMatchStatus() != StatementItemMatchStatus.BOOK_ONLY || ri.getTransaction() == null) {
                continue;
            }
            for (JournalEntry je : journalEntryRepository.findByTransactionIdWithAccount(ri.getTransaction().getId())) {
                if (je.getAccount().getId().equals(glAccountId)) {
                    debitTotal = debitTotal.add(je.getDebitAmount());
                    creditTotal = creditTotal.add(je.getCreditAmount());
                }
            }
        }
        return new BigDecimal[]{debitTotal, creditTotal};
    }

    /** Balances and counts are set when the reconciliation is created; null means the row is corrupt. */
    private static <T> T requireComputed(T value, String column, BankReconciliation recon) {
        if (value == null) {
            throw new IllegalStateException("Rekonsiliasi " + recon.getId() + " tidak memiliki " + column);
        }
        return value;
    }

    public List<ReconciliationItem> getItems(UUID reconciliationId) {
        return reconciliationItemRepository.findByReconciliationIdWithRelations(reconciliationId);
    }

    public List<ReconciliationItem> getOutstandingItems(UUID reconciliationId) {
        List<ReconciliationItem> items = reconciliationItemRepository
                .findByReconciliationIdWithRelations(reconciliationId);

        return items.stream()
                .filter(i -> i.getMatchStatus() == StatementItemMatchStatus.BANK_ONLY
                        || i.getMatchStatus() == StatementItemMatchStatus.BOOK_ONLY)
                .toList();
    }
}
