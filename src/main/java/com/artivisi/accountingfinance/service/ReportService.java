package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.enums.AccountType;
import com.artivisi.accountingfinance.enums.NormalBalance;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final ChartOfAccountRepository chartOfAccountRepository;
    private final JournalEntryRepository journalEntryRepository;

    public TrialBalanceReport generateTrialBalance(LocalDate asOfDate) {
        List<ChartOfAccount> accounts = chartOfAccountRepository.findAllTransactableAccounts();
        List<TrialBalanceItem> items = new ArrayList<>();

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        LocalDate periodStart = asOfDate.withDayOfMonth(1);

        for (ChartOfAccount account : accounts) {
            BigDecimal debit = journalEntryRepository.sumDebitByAccountAndDateRange(
                    account.getId(), LocalDate.of(1900, 1, 1), asOfDate);
            BigDecimal credit = journalEntryRepository.sumCreditByAccountAndDateRange(
                    account.getId(), LocalDate.of(1900, 1, 1), asOfDate);

            BigDecimal balance;
            if (account.getNormalBalance() == NormalBalance.DEBIT) {
                balance = debit.subtract(credit);
            } else {
                balance = credit.subtract(debit);
            }

            if (balance.compareTo(BigDecimal.ZERO) != 0) {
                BigDecimal debitBalance = BigDecimal.ZERO;
                BigDecimal creditBalance = BigDecimal.ZERO;

                if (account.getNormalBalance() == NormalBalance.DEBIT) {
                    if (balance.compareTo(BigDecimal.ZERO) > 0) {
                        debitBalance = balance;
                    } else {
                        creditBalance = balance.negate();
                    }
                } else {
                    if (balance.compareTo(BigDecimal.ZERO) > 0) {
                        creditBalance = balance;
                    } else {
                        debitBalance = balance.negate();
                    }
                }

                items.add(new TrialBalanceItem(account, debitBalance, creditBalance));
                totalDebit = totalDebit.add(debitBalance);
                totalCredit = totalCredit.add(creditBalance);
            }
        }

        return new TrialBalanceReport(asOfDate, items, totalDebit, totalCredit);
    }

    public IncomeStatementReport generateIncomeStatement(LocalDate startDate, LocalDate endDate) {
        List<ChartOfAccount> revenueAccounts = chartOfAccountRepository
                .findByAccountTypeAndActiveOrderByAccountCodeAsc(AccountType.REVENUE, true);
        List<ChartOfAccount> expenseAccounts = chartOfAccountRepository
                .findByAccountTypeAndActiveOrderByAccountCodeAsc(AccountType.EXPENSE, true);

        List<IncomeStatementItem> revenueItems = calculateAccountBalances(revenueAccounts, startDate, endDate);
        List<IncomeStatementItem> expenseItems = calculateAccountBalances(expenseAccounts, startDate, endDate);

        BigDecimal totalRevenue = revenueItems.stream()
                .map(IncomeStatementItem::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalExpense = expenseItems.stream()
                .map(IncomeStatementItem::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netIncome = totalRevenue.subtract(totalExpense);

        return new IncomeStatementReport(startDate, endDate, revenueItems, expenseItems,
                totalRevenue, totalExpense, netIncome);
    }

    public BalanceSheetReport generateBalanceSheet(LocalDate asOfDate) {
        List<ChartOfAccount> assetAccounts = chartOfAccountRepository
                .findByAccountTypeAndActiveOrderByAccountCodeAsc(AccountType.ASSET, true);
        List<ChartOfAccount> liabilityAccounts = chartOfAccountRepository
                .findByAccountTypeAndActiveOrderByAccountCodeAsc(AccountType.LIABILITY, true);
        List<ChartOfAccount> equityAccounts = chartOfAccountRepository
                .findByAccountTypeAndActiveOrderByAccountCodeAsc(AccountType.EQUITY, true);

        LocalDate periodStart = LocalDate.of(1900, 1, 1);

        List<BalanceSheetItem> assetItems = calculateBalanceSheetItems(assetAccounts, periodStart, asOfDate);
        List<BalanceSheetItem> liabilityItems = calculateBalanceSheetItems(liabilityAccounts, periodStart, asOfDate);
        List<BalanceSheetItem> equityItems = calculateBalanceSheetItems(equityAccounts, periodStart, asOfDate);

        BigDecimal totalAssets = assetItems.stream()
                .map(BalanceSheetItem::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalLiabilities = liabilityItems.stream()
                .map(BalanceSheetItem::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalEquity = equityItems.stream()
                .map(BalanceSheetItem::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDate fiscalYearStart = asOfDate.withDayOfYear(1);
        IncomeStatementReport incomeStatement = generateIncomeStatement(fiscalYearStart, asOfDate);
        BigDecimal currentYearEarnings = incomeStatement.netIncome();

        totalEquity = totalEquity.add(currentYearEarnings);

        return new BalanceSheetReport(asOfDate, assetItems, liabilityItems, equityItems,
                totalAssets, totalLiabilities, totalEquity, currentYearEarnings);
    }

    private List<IncomeStatementItem> calculateAccountBalances(List<ChartOfAccount> accounts,
                                                               LocalDate startDate, LocalDate endDate) {
        List<IncomeStatementItem> items = new ArrayList<>();

        for (ChartOfAccount account : accounts) {
            if (account.getIsHeader()) continue;

            BigDecimal debit = journalEntryRepository.sumDebitByAccountAndDateRange(
                    account.getId(), startDate, endDate);
            BigDecimal credit = journalEntryRepository.sumCreditByAccountAndDateRange(
                    account.getId(), startDate, endDate);

            BigDecimal balance;
            if (account.getNormalBalance() == NormalBalance.DEBIT) {
                balance = debit.subtract(credit);
            } else {
                balance = credit.subtract(debit);
            }

            if (balance.compareTo(BigDecimal.ZERO) != 0) {
                items.add(new IncomeStatementItem(account, balance));
            }
        }

        return items;
    }

    private List<BalanceSheetItem> calculateBalanceSheetItems(List<ChartOfAccount> accounts,
                                                              LocalDate startDate, LocalDate endDate) {
        List<BalanceSheetItem> items = new ArrayList<>();

        for (ChartOfAccount account : accounts) {
            if (account.getIsHeader()) continue;

            BigDecimal debit = journalEntryRepository.sumDebitByAccountAndDateRange(
                    account.getId(), startDate, endDate);
            BigDecimal credit = journalEntryRepository.sumCreditByAccountAndDateRange(
                    account.getId(), startDate, endDate);

            BigDecimal balance;
            if (account.getNormalBalance() == NormalBalance.DEBIT) {
                balance = debit.subtract(credit);
            } else {
                balance = credit.subtract(debit);
            }

            if (balance.compareTo(BigDecimal.ZERO) != 0) {
                items.add(new BalanceSheetItem(account, balance));
            }
        }

        return items;
    }

    public record TrialBalanceReport(
            LocalDate asOfDate,
            List<TrialBalanceItem> items,
            BigDecimal totalDebit,
            BigDecimal totalCredit
    ) {}

    public record TrialBalanceItem(
            ChartOfAccount account,
            BigDecimal debitBalance,
            BigDecimal creditBalance
    ) {}

    public record IncomeStatementReport(
            LocalDate startDate,
            LocalDate endDate,
            List<IncomeStatementItem> revenueItems,
            List<IncomeStatementItem> expenseItems,
            BigDecimal totalRevenue,
            BigDecimal totalExpense,
            BigDecimal netIncome
    ) {}

    public record IncomeStatementItem(
            ChartOfAccount account,
            BigDecimal balance
    ) {}

    public record BalanceSheetReport(
            LocalDate asOfDate,
            List<BalanceSheetItem> assetItems,
            List<BalanceSheetItem> liabilityItems,
            List<BalanceSheetItem> equityItems,
            BigDecimal totalAssets,
            BigDecimal totalLiabilities,
            BigDecimal totalEquity,
            BigDecimal currentYearEarnings
    ) {}

    public record BalanceSheetItem(
            ChartOfAccount account,
            BigDecimal balance
    ) {}
}
