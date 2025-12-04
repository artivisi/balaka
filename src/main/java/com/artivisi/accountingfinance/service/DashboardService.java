package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.AmortizationEntry;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.enums.AccountType;
import com.artivisi.accountingfinance.enums.NormalBalance;
import com.artivisi.accountingfinance.repository.AmortizationEntryRepository;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final ChartOfAccountRepository chartOfAccountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final TransactionRepository transactionRepository;
    private final AmortizationEntryRepository amortizationEntryRepository;

    // Account codes for specific KPIs
    private static final String PIUTANG_USAHA_CODE = "1.1.04";
    private static final String HUTANG_USAHA_CODE = "2.1.01";
    private static final List<String> CASH_BANK_CODES = List.of("1.1.01", "1.1.02", "1.1.03");

    public DashboardKPI calculateKPIs(YearMonth month) {
        LocalDate startDate = month.atDay(1);
        LocalDate endDate = month.atEndOfMonth();

        // Previous month for comparison
        YearMonth prevMonth = month.minusMonths(1);
        LocalDate prevStartDate = prevMonth.atDay(1);
        LocalDate prevEndDate = prevMonth.atEndOfMonth();

        // Calculate revenue and expenses for current and previous month
        BigDecimal currentRevenue = calculateTotalRevenue(startDate, endDate);
        BigDecimal prevRevenue = calculateTotalRevenue(prevStartDate, prevEndDate);

        BigDecimal currentExpense = calculateTotalExpense(startDate, endDate);
        BigDecimal prevExpense = calculateTotalExpense(prevStartDate, prevEndDate);

        BigDecimal currentNetProfit = currentRevenue.subtract(currentExpense);
        BigDecimal prevNetProfit = prevRevenue.subtract(prevExpense);

        // Calculate profit margin
        BigDecimal currentProfitMargin = currentRevenue.compareTo(BigDecimal.ZERO) > 0
                ? currentNetProfit.multiply(BigDecimal.valueOf(100)).divide(currentRevenue, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal prevProfitMargin = prevRevenue.compareTo(BigDecimal.ZERO) > 0
                ? prevNetProfit.multiply(BigDecimal.valueOf(100)).divide(prevRevenue, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Calculate change percentages
        BigDecimal revenueChange = calculateChangePercent(currentRevenue, prevRevenue);
        BigDecimal expenseChange = calculateChangePercent(currentExpense, prevExpense);
        BigDecimal profitChange = calculateChangePercent(currentNetProfit, prevNetProfit);
        BigDecimal marginChange = currentProfitMargin.subtract(prevProfitMargin); // Points difference

        // Calculate balances as of end of current month
        BigDecimal cashBalance = calculateCashBalance(endDate);
        BigDecimal receivablesBalance = calculateAccountBalance(PIUTANG_USAHA_CODE, endDate);
        BigDecimal payablesBalance = calculateAccountBalance(HUTANG_USAHA_CODE, endDate);

        // Transaction count for current month
        long transactionCount = transactionRepository.countByTransactionDateBetween(startDate, endDate);

        // Cash/Bank breakdown
        List<CashBankItem> cashBankItems = calculateCashBankBreakdown(endDate);

        return new DashboardKPI(
                month,
                currentRevenue,
                revenueChange,
                currentExpense,
                expenseChange,
                currentNetProfit,
                profitChange,
                currentProfitMargin,
                marginChange,
                cashBalance,
                receivablesBalance,
                payablesBalance,
                transactionCount,
                cashBankItems
        );
    }

    private BigDecimal calculateTotalRevenue(LocalDate startDate, LocalDate endDate) {
        List<ChartOfAccount> revenueAccounts = chartOfAccountRepository
                .findByAccountTypeAndActiveOrderByAccountCodeAsc(AccountType.REVENUE, true);

        BigDecimal total = BigDecimal.ZERO;
        for (ChartOfAccount account : revenueAccounts) {
            if (account.getIsHeader()) continue;

            BigDecimal debit = journalEntryRepository.sumDebitByAccountAndDateRange(
                    account.getId(), startDate, endDate);
            BigDecimal credit = journalEntryRepository.sumCreditByAccountAndDateRange(
                    account.getId(), startDate, endDate);

            BigDecimal balance = account.getNormalBalance() == NormalBalance.CREDIT
                    ? credit.subtract(debit)
                    : debit.subtract(credit);

            if (balance.compareTo(BigDecimal.ZERO) > 0) {
                total = total.add(balance);
            }
        }
        return total;
    }

    private BigDecimal calculateTotalExpense(LocalDate startDate, LocalDate endDate) {
        List<ChartOfAccount> expenseAccounts = chartOfAccountRepository
                .findByAccountTypeAndActiveOrderByAccountCodeAsc(AccountType.EXPENSE, true);

        BigDecimal total = BigDecimal.ZERO;
        for (ChartOfAccount account : expenseAccounts) {
            if (account.getIsHeader()) continue;

            BigDecimal debit = journalEntryRepository.sumDebitByAccountAndDateRange(
                    account.getId(), startDate, endDate);
            BigDecimal credit = journalEntryRepository.sumCreditByAccountAndDateRange(
                    account.getId(), startDate, endDate);

            BigDecimal balance = account.getNormalBalance() == NormalBalance.DEBIT
                    ? debit.subtract(credit)
                    : credit.subtract(debit);

            if (balance.compareTo(BigDecimal.ZERO) > 0) {
                total = total.add(balance);
            }
        }
        return total;
    }

    private BigDecimal calculateCashBalance(LocalDate asOfDate) {
        BigDecimal total = BigDecimal.ZERO;
        for (String code : CASH_BANK_CODES) {
            total = total.add(calculateAccountBalance(code, asOfDate));
        }
        return total;
    }

    private BigDecimal calculateAccountBalance(String accountCode, LocalDate asOfDate) {
        return chartOfAccountRepository.findByAccountCode(accountCode)
                .map(account -> {
                    BigDecimal debit = journalEntryRepository.sumDebitByAccountAndDateRange(
                            account.getId(), LocalDate.of(1900, 1, 1), asOfDate);
                    BigDecimal credit = journalEntryRepository.sumCreditByAccountAndDateRange(
                            account.getId(), LocalDate.of(1900, 1, 1), asOfDate);

                    if (account.getNormalBalance() == NormalBalance.DEBIT) {
                        return debit.subtract(credit);
                    } else {
                        return credit.subtract(debit);
                    }
                })
                .orElse(BigDecimal.ZERO);
    }

    private List<CashBankItem> calculateCashBankBreakdown(LocalDate asOfDate) {
        List<CashBankItem> items = new ArrayList<>();
        for (String code : CASH_BANK_CODES) {
            chartOfAccountRepository.findByAccountCode(code).ifPresent(account -> {
                BigDecimal balance = calculateAccountBalance(code, asOfDate);
                if (balance.compareTo(BigDecimal.ZERO) != 0) {
                    items.add(new CashBankItem(account.getAccountName(), balance));
                }
            });
        }
        return items;
    }

    private BigDecimal calculateChangePercent(BigDecimal current, BigDecimal previous) {
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            if (current.compareTo(BigDecimal.ZERO) > 0) {
                return BigDecimal.valueOf(100); // 100% increase from zero
            }
            return BigDecimal.ZERO;
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous.abs(), 1, RoundingMode.HALF_UP);
    }

    // DTOs
    public record DashboardKPI(
            YearMonth month,
            BigDecimal revenue,
            BigDecimal revenueChange,
            BigDecimal expense,
            BigDecimal expenseChange,
            BigDecimal netProfit,
            BigDecimal profitChange,
            BigDecimal profitMargin,
            BigDecimal marginChange,
            BigDecimal cashBalance,
            BigDecimal receivablesBalance,
            BigDecimal payablesBalance,
            long transactionCount,
            List<CashBankItem> cashBankItems
    ) {}

    public record CashBankItem(
            String accountName,
            BigDecimal balance
    ) {}

    public List<RecentTransaction> getRecentTransactions(int limit) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusMonths(1);
        
        return transactionRepository.findPostedTransactionsBetweenDates(startDate, endDate)
                .stream()
                .limit(limit)
                .map(tx -> new RecentTransaction(
                        tx.getTransactionNumber(),
                        tx.getDescription(),
                        tx.getTransactionDate(),
                        tx.getAmount(),
                        tx.getJournalTemplate().getCategory()
                ))
                .toList();
    }

    public record RecentTransaction(
            String transactionNumber,
            String description,
            LocalDate transactionDate,
            BigDecimal amount,
            com.artivisi.accountingfinance.enums.TemplateCategory category
    ) {}

    /**
     * Get amortization summary for dashboard widget.
     * Shows pending entries count, overdue count, and upcoming entries due this month.
     */
    public AmortizationSummary getAmortizationSummary() {
        LocalDate today = LocalDate.now();
        LocalDate endOfMonth = YearMonth.now().atEndOfMonth();

        // Total pending entries
        long totalPending = amortizationEntryRepository.countPendingEntries();

        // Overdue entries (period end before today)
        List<AmortizationEntry> overdueEntries = amortizationEntryRepository.findPendingEntriesDueByDate(today.minusDays(1));
        long overdueCount = overdueEntries.size();

        // Due this month (period end between today and end of month)
        List<AmortizationEntry> dueThisMonth = amortizationEntryRepository.findPendingEntriesDueByDate(endOfMonth);
        // Exclude overdue from due this month
        long dueThisMonthCount = dueThisMonth.stream()
                .filter(e -> !e.getPeriodEnd().isBefore(today))
                .count();

        // Get next 3 upcoming entries
        List<UpcomingAmortization> upcomingEntries = amortizationEntryRepository.findPendingEntriesDueByDate(endOfMonth.plusMonths(2))
                .stream()
                .limit(3)
                .map(e -> new UpcomingAmortization(
                        e.getSchedule().getCode(),
                        e.getSchedule().getDescription(),
                        e.getPeriodEnd(),
                        e.getAmount(),
                        e.getPeriodEnd().isBefore(today)
                ))
                .toList();

        return new AmortizationSummary(totalPending, overdueCount, dueThisMonthCount, upcomingEntries);
    }

    public record AmortizationSummary(
            long totalPending,
            long overdueCount,
            long dueThisMonth,
            List<UpcomingAmortization> upcomingEntries
    ) {}

    public record UpcomingAmortization(
            String scheduleCode,
            String description,
            LocalDate dueDate,
            BigDecimal amount,
            boolean isOverdue
    ) {}
}
