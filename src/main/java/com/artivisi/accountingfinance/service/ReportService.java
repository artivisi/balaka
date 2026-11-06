package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.AccountType;
import com.artivisi.accountingfinance.enums.CashFlowCategory;
import com.artivisi.accountingfinance.enums.NormalBalance;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final ChartOfAccountRepository chartOfAccountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final TransactionRepository transactionRepository;

    public TrialBalanceReport generateTrialBalance(LocalDate asOfDate) {
        List<ChartOfAccount> accounts = chartOfAccountRepository.findAllTransactableAccounts();
        List<TrialBalanceItem> items = new ArrayList<>();
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        for (ChartOfAccount account : accounts) {
            TrialBalanceItem item = calculateTrialBalanceItem(account, asOfDate);
            if (item != null) {
                items.add(item);
                totalDebit = totalDebit.add(item.debitBalance());
                totalCredit = totalCredit.add(item.creditBalance());
            }
        }

        return new TrialBalanceReport(asOfDate, items, totalDebit, totalCredit);
    }

    private TrialBalanceItem calculateTrialBalanceItem(ChartOfAccount account, LocalDate asOfDate) {
        BigDecimal debit = journalEntryRepository.sumDebitByAccountAndDateRange(
                account.getId(), LocalDate.of(1900, 1, 1), asOfDate);
        BigDecimal credit = journalEntryRepository.sumCreditByAccountAndDateRange(
                account.getId(), LocalDate.of(1900, 1, 1), asOfDate);

        BigDecimal balance = calculateBalance(account.getNormalBalance(), debit, credit);

        if (balance.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        BigDecimal[] debitCredit = splitBalanceToDebitCredit(account.getNormalBalance(), balance);
        return new TrialBalanceItem(account, debitCredit[0], debitCredit[1]);
    }

    private BigDecimal calculateBalance(NormalBalance normalBalance, BigDecimal debit, BigDecimal credit) {
        return normalBalance == NormalBalance.DEBIT
                ? debit.subtract(credit)
                : credit.subtract(debit);
    }

    private BigDecimal[] splitBalanceToDebitCredit(NormalBalance normalBalance, BigDecimal balance) {
        boolean isPositive = balance.compareTo(BigDecimal.ZERO) > 0;
        BigDecimal debitBalance = BigDecimal.ZERO;
        BigDecimal creditBalance = BigDecimal.ZERO;

        if (normalBalance == NormalBalance.DEBIT) {
            if (isPositive) {
                debitBalance = balance;
            } else {
                creditBalance = balance.negate();
            }
        } else {
            if (isPositive) {
                creditBalance = balance;
            } else {
                debitBalance = balance.negate();
            }
        }
        return new BigDecimal[]{debitBalance, creditBalance};
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

    /**
     * Generate income statement excluding closing entries (BUG-014).
     * Used by tax export services to get pre-closing P&L figures.
     */
    public IncomeStatementReport generateIncomeStatementExcludingClosing(LocalDate startDate, LocalDate endDate) {
        List<ChartOfAccount> revenueAccounts = chartOfAccountRepository
                .findByAccountTypeAndActiveOrderByAccountCodeAsc(AccountType.REVENUE, true);
        List<ChartOfAccount> expenseAccounts = chartOfAccountRepository
                .findByAccountTypeAndActiveOrderByAccountCodeAsc(AccountType.EXPENSE, true);

        List<IncomeStatementItem> revenueItems = calculateAccountBalancesExcludingClosing(revenueAccounts, startDate, endDate);
        List<IncomeStatementItem> expenseItems = calculateAccountBalancesExcludingClosing(expenseAccounts, startDate, endDate);

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
        // Contra-equity (DEBIT normal balance like Prive and Dividen) reduces equity: its line carries the
        // negative sign, so every consumer of the lines (pages, exports, API) agrees with totalEquity
        List<BalanceSheetItem> equityItems = calculateBalanceSheetItems(equityAccounts, periodStart, asOfDate).stream()
                .map(item -> item.account().getNormalBalance() == NormalBalance.DEBIT
                        ? new BalanceSheetItem(item.account(), item.balance().negate())
                        : item)
                .toList();

        // Contra-assets (CREDIT normal balance like Accumulated Depreciation) reduce total assets
        BigDecimal totalAssets = assetItems.stream()
                .map(item -> item.account().getNormalBalance() == NormalBalance.CREDIT
                        ? item.balance().negate()
                        : item.balance())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalLiabilities = liabilityItems.stream()
                .map(BalanceSheetItem::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalEquity = equityItems.stream()
                .map(BalanceSheetItem::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDate fiscalYearStart = asOfDate.withDayOfYear(1);

        // Calculate prior year retained earnings (all net income before current fiscal year)
        IncomeStatementReport priorYearsIncome = generateIncomeStatement(
                LocalDate.of(1900, 1, 1), fiscalYearStart.minusDays(1));
        BigDecimal retainedEarnings = priorYearsIncome.netIncome();

        // Calculate current year earnings
        IncomeStatementReport incomeStatement = generateIncomeStatement(fiscalYearStart, asOfDate);
        BigDecimal currentYearEarnings = incomeStatement.netIncome();

        totalEquity = totalEquity.add(retainedEarnings).add(currentYearEarnings);

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

    private List<IncomeStatementItem> calculateAccountBalancesExcludingClosing(List<ChartOfAccount> accounts,
                                                                               LocalDate startDate, LocalDate endDate) {
        List<IncomeStatementItem> items = new ArrayList<>();

        for (ChartOfAccount account : accounts) {
            if (account.getIsHeader()) continue;

            BigDecimal debit = journalEntryRepository.sumDebitByAccountAndDateRangeExcludingClosing(
                    account.getId(), startDate, endDate);
            BigDecimal credit = journalEntryRepository.sumCreditByAccountAndDateRangeExcludingClosing(
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

    // ==================== CASH FLOW STATEMENT ====================

    public CashFlowReport generateCashFlowStatement(LocalDate startDate, LocalDate endDate) {
        // Get cash/bank accounts dynamically (all active leaf accounts matching 1.1.0x pattern)
        List<ChartOfAccount> cashAccounts = chartOfAccountRepository.findCashBankAccounts();

        // Calculate beginning cash balance (before startDate)
        BigDecimal beginningCashBalance = calculateCashBalance(cashAccounts, LocalDate.of(1900, 1, 1), startDate.minusDays(1));

        // Calculate ending cash balance (up to endDate)
        BigDecimal endingCashBalance = calculateCashBalance(cashAccounts, LocalDate.of(1900, 1, 1), endDate);

        // Get posted transactions in the period
        List<Transaction> transactions = transactionRepository.findPostedTransactionsBetweenDates(startDate, endDate);

        // Group cash flows by category
        Map<CashFlowCategory, List<CashFlowItem>> cashFlowsByCategory = new EnumMap<>(CashFlowCategory.class);
        for (CashFlowCategory category : CashFlowCategory.values()) {
            cashFlowsByCategory.put(category, new ArrayList<>());
        }

        // Aggregate by template name within each category
        Map<CashFlowCategory, Map<String, BigDecimal>> aggregatedFlows = new EnumMap<>(CashFlowCategory.class);
        for (CashFlowCategory category : CashFlowCategory.values()) {
            aggregatedFlows.put(category, new HashMap<>());
        }

        for (Transaction transaction : transactions) {
            CashFlowCategory category = transaction.getJournalTemplate().getCashFlowCategory();
            if (category == null) {
                category = CashFlowCategory.OPERATING; // default to operating
            }

            String templateName = transaction.getJournalTemplate().getTemplateName();

            // Calculate net cash impact from this transaction
            BigDecimal cashImpact = calculateCashImpact(transaction, cashAccounts);

            if (cashImpact.compareTo(BigDecimal.ZERO) != 0) {
                Map<String, BigDecimal> categoryFlows = aggregatedFlows.get(category);
                categoryFlows.merge(templateName, cashImpact, BigDecimal::add);
            }
        }

        // Convert aggregated flows to CashFlowItem lists
        for (CashFlowCategory category : CashFlowCategory.values()) {
            Map<String, BigDecimal> categoryFlows = aggregatedFlows.get(category);
            List<CashFlowItem> items = cashFlowsByCategory.get(category);

            for (Map.Entry<String, BigDecimal> entry : categoryFlows.entrySet()) {
                items.add(new CashFlowItem(entry.getKey(), entry.getValue()));
            }

            // Sort by absolute amount descending
            items.sort((a, b) -> b.amount().abs().compareTo(a.amount().abs()));
        }

        // Calculate section totals
        BigDecimal operatingTotal = sumCashFlowItems(cashFlowsByCategory.get(CashFlowCategory.OPERATING));
        BigDecimal investingTotal = sumCashFlowItems(cashFlowsByCategory.get(CashFlowCategory.INVESTING));
        BigDecimal financingTotal = sumCashFlowItems(cashFlowsByCategory.get(CashFlowCategory.FINANCING));

        BigDecimal netCashChange = operatingTotal.add(investingTotal).add(financingTotal);

        // Cash account breakdown for reconciliation
        List<CashAccountBalance> cashAccountBalances = new ArrayList<>();
        for (ChartOfAccount account : cashAccounts) {
            BigDecimal balance = calculateAccountBalance(account, LocalDate.of(1900, 1, 1), endDate);
            if (balance.compareTo(BigDecimal.ZERO) != 0) {
                cashAccountBalances.add(new CashAccountBalance(account.getAccountName(), balance));
            }
        }

        return new CashFlowReport(
                startDate,
                endDate,
                cashFlowsByCategory.get(CashFlowCategory.OPERATING),
                cashFlowsByCategory.get(CashFlowCategory.INVESTING),
                cashFlowsByCategory.get(CashFlowCategory.FINANCING),
                operatingTotal,
                investingTotal,
                financingTotal,
                netCashChange,
                beginningCashBalance,
                endingCashBalance,
                cashAccountBalances
        );
    }

    private BigDecimal calculateCashBalance(List<ChartOfAccount> cashAccounts, LocalDate startDate, LocalDate endDate) {
        BigDecimal total = BigDecimal.ZERO;
        for (ChartOfAccount account : cashAccounts) {
            total = total.add(calculateAccountBalance(account, startDate, endDate));
        }
        return total;
    }

    private BigDecimal calculateAccountBalance(ChartOfAccount account, LocalDate startDate, LocalDate endDate) {
        BigDecimal debit = journalEntryRepository.sumDebitByAccountAndDateRange(account.getId(), startDate, endDate);
        BigDecimal credit = journalEntryRepository.sumCreditByAccountAndDateRange(account.getId(), startDate, endDate);

        if (account.getNormalBalance() == NormalBalance.DEBIT) {
            return debit.subtract(credit);
        } else {
            return credit.subtract(debit);
        }
    }

    private BigDecimal calculateCashImpact(Transaction transaction, List<ChartOfAccount> cashAccounts) {
        List<JournalEntry> entries = journalEntryRepository.findByTransactionIdOrderByLineOrderAsc(transaction.getId());

        BigDecimal cashInflow = BigDecimal.ZERO;
        BigDecimal cashOutflow = BigDecimal.ZERO;

        for (JournalEntry entry : entries) {
            boolean isCashAccount = cashAccounts.stream()
                    .anyMatch(ca -> ca.getId().equals(entry.getAccount().getId()));

            if (isCashAccount) {
                // For cash accounts (DEBIT normal balance):
                // Debit = cash inflow, Credit = cash outflow
                cashInflow = cashInflow.add(entry.getDebitAmount());
                cashOutflow = cashOutflow.add(entry.getCreditAmount());
            }
        }

        return cashInflow.subtract(cashOutflow);
    }

    private BigDecimal sumCashFlowItems(List<CashFlowItem> items) {
        return items.stream()
                .map(CashFlowItem::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public record CashFlowReport(
            LocalDate startDate,
            LocalDate endDate,
            List<CashFlowItem> operatingItems,
            List<CashFlowItem> investingItems,
            List<CashFlowItem> financingItems,
            BigDecimal operatingTotal,
            BigDecimal investingTotal,
            BigDecimal financingTotal,
            BigDecimal netCashChange,
            BigDecimal beginningCashBalance,
            BigDecimal endingCashBalance,
            List<CashAccountBalance> cashAccountBalances
    ) {}

    public record CashFlowItem(
            String description,
            BigDecimal amount
    ) {}

    public record CashAccountBalance(
            String accountName,
            BigDecimal balance
    ) {}
}
