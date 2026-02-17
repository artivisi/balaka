package com.artivisi.accountingfinance.controller.api;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.DraftTransaction;
import com.artivisi.accountingfinance.enums.AccountType;
import com.artivisi.accountingfinance.enums.AuditEventType;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.DraftTransactionRepository;
import com.artivisi.accountingfinance.service.DashboardService;
import com.artivisi.accountingfinance.service.ReportService;
import com.artivisi.accountingfinance.service.SecurityAuditService;
import com.artivisi.accountingfinance.service.TaxReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only financial analysis API for external AI tools.
 * All endpoints return structured data that AI tools can interpret.
 */
@RestController
@RequestMapping("/api/analysis")
@PreAuthorize("hasAuthority('SCOPE_analysis:read')")
@RequiredArgsConstructor
@Slf4j
public class FinancialAnalysisApiController {

    private final ReportService reportService;
    private final DashboardService dashboardService;
    private final TaxReportService taxReportService;
    private final ChartOfAccountRepository chartOfAccountRepository;
    private final DraftTransactionRepository draftTransactionRepository;
    private final SecurityAuditService securityAuditService;

    @GetMapping("/snapshot")
    public ResponseEntity<AnalysisResponse<SnapshotDto>> getSnapshot(
            @RequestParam String month) {

        YearMonth ym = YearMonth.parse(month);
        DashboardService.DashboardKPI kpi = dashboardService.calculateKPIs(ym);

        List<CashBankItemDto> cashBankItems = kpi.cashBankItems().stream()
                .map(i -> new CashBankItemDto(i.accountName(), i.balance()))
                .toList();

        SnapshotDto data = new SnapshotDto(
                ym.toString(), kpi.revenue(), kpi.revenueChange(),
                kpi.expense(), kpi.expenseChange(),
                kpi.netProfit(), kpi.profitChange(),
                kpi.profitMargin(), kpi.marginChange(),
                kpi.cashBalance(), kpi.receivablesBalance(), kpi.payablesBalance(),
                kpi.transactionCount(), cashBankItems);

        auditAccess("snapshot", Map.of("month", month));

        return ResponseEntity.ok(new AnalysisResponse<>(
                "snapshot", LocalDateTime.now(),
                Map.of("month", month),
                data,
                Map.of("currency", "IDR",
                        "accountingBasis", "accrual",
                        "description", "Financial KPI snapshot for " + month
                                + ". Change percentages are vs previous month.")));
    }

    @GetMapping("/trial-balance")
    public ResponseEntity<AnalysisResponse<TrialBalanceDto>> getTrialBalance(
            @RequestParam String asOfDate) {

        LocalDate date = LocalDate.parse(asOfDate);
        ReportService.TrialBalanceReport report = reportService.generateTrialBalance(date);

        List<TrialBalanceItemDto> items = report.items().stream()
                .map(this::toTrialBalanceItemDto)
                .toList();

        TrialBalanceDto data = new TrialBalanceDto(items, report.totalDebit(), report.totalCredit());

        auditAccess("trial-balance", Map.of("asOfDate", asOfDate));

        return ResponseEntity.ok(new AnalysisResponse<>(
                "trial-balance", LocalDateTime.now(),
                Map.of("asOfDate", asOfDate),
                data,
                Map.of("currency", "IDR",
                        "accountingBasis", "accrual",
                        "description", "Trial balance as of " + asOfDate
                                + ". Each account shows debit and credit balances. "
                                + "Total debits must equal total credits.")));
    }

    @GetMapping("/income-statement")
    public ResponseEntity<AnalysisResponse<IncomeStatementDto>> getIncomeStatement(
            @RequestParam String startDate,
            @RequestParam String endDate) {

        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        ReportService.IncomeStatementReport report = reportService.generateIncomeStatement(start, end);

        List<LineItemDto> revenueItems = report.revenueItems().stream()
                .map(this::toLineItemDto)
                .toList();
        List<LineItemDto> expenseItems = report.expenseItems().stream()
                .map(this::toLineItemDto)
                .toList();

        IncomeStatementDto data = new IncomeStatementDto(
                revenueItems, expenseItems,
                report.totalRevenue(), report.totalExpense(), report.netIncome());

        auditAccess("income-statement", Map.of("startDate", startDate, "endDate", endDate));

        return ResponseEntity.ok(new AnalysisResponse<>(
                "income-statement", LocalDateTime.now(),
                Map.of("startDate", startDate, "endDate", endDate),
                data,
                Map.of("currency", "IDR",
                        "accountingBasis", "accrual",
                        "description", "Income statement for period " + startDate + " to " + endDate
                                + ". Net income = total revenue - total expense.")));
    }

    @GetMapping("/balance-sheet")
    public ResponseEntity<AnalysisResponse<BalanceSheetDto>> getBalanceSheet(
            @RequestParam String asOfDate) {

        LocalDate date = LocalDate.parse(asOfDate);
        ReportService.BalanceSheetReport report = reportService.generateBalanceSheet(date);

        List<LineItemDto> assetItems = report.assetItems().stream()
                .map(this::toBalanceSheetLineItemDto)
                .toList();
        List<LineItemDto> liabilityItems = report.liabilityItems().stream()
                .map(this::toBalanceSheetLineItemDto)
                .toList();
        List<LineItemDto> equityItems = report.equityItems().stream()
                .map(this::toBalanceSheetLineItemDto)
                .toList();

        BalanceSheetDto data = new BalanceSheetDto(
                assetItems, liabilityItems, equityItems,
                report.totalAssets(), report.totalLiabilities(), report.totalEquity(),
                report.currentYearEarnings());

        auditAccess("balance-sheet", Map.of("asOfDate", asOfDate));

        return ResponseEntity.ok(new AnalysisResponse<>(
                "balance-sheet", LocalDateTime.now(),
                Map.of("asOfDate", asOfDate),
                data,
                Map.of("currency", "IDR",
                        "accountingBasis", "accrual",
                        "description", "Balance sheet as of " + asOfDate
                                + ". Assets = Liabilities + Equity + Current Year Earnings.")));
    }

    @GetMapping("/cash-flow")
    public ResponseEntity<AnalysisResponse<CashFlowDto>> getCashFlow(
            @RequestParam String startDate,
            @RequestParam String endDate) {

        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        ReportService.CashFlowReport report = reportService.generateCashFlowStatement(start, end);

        List<CashFlowItemDto> operatingItems = report.operatingItems().stream()
                .map(i -> new CashFlowItemDto(i.description(), i.amount()))
                .toList();
        List<CashFlowItemDto> investingItems = report.investingItems().stream()
                .map(i -> new CashFlowItemDto(i.description(), i.amount()))
                .toList();
        List<CashFlowItemDto> financingItems = report.financingItems().stream()
                .map(i -> new CashFlowItemDto(i.description(), i.amount()))
                .toList();
        List<CashBankItemDto> cashAccountBalances = report.cashAccountBalances().stream()
                .map(i -> new CashBankItemDto(i.accountName(), i.balance()))
                .toList();

        CashFlowDto data = new CashFlowDto(
                operatingItems, investingItems, financingItems,
                report.operatingTotal(), report.investingTotal(), report.financingTotal(),
                report.netCashChange(), report.beginningCashBalance(), report.endingCashBalance(),
                cashAccountBalances);

        auditAccess("cash-flow", Map.of("startDate", startDate, "endDate", endDate));

        return ResponseEntity.ok(new AnalysisResponse<>(
                "cash-flow", LocalDateTime.now(),
                Map.of("startDate", startDate, "endDate", endDate),
                data,
                Map.of("currency", "IDR",
                        "accountingBasis", "accrual",
                        "description", "Cash flow statement for period " + startDate + " to " + endDate
                                + ". Positive amounts = cash inflow, negative = cash outflow.")));
    }

    @GetMapping("/tax-summary")
    public ResponseEntity<AnalysisResponse<TaxSummaryDto>> getTaxSummary(
            @RequestParam String startDate,
            @RequestParam String endDate) {

        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        TaxReportService.TaxSummaryReport report = taxReportService.generateTaxSummary(start, end);

        List<TaxItemDto> items = report.items().stream()
                .map(i -> new TaxItemDto(
                        i.account().getAccountCode(), i.account().getAccountName(),
                        i.label(), i.debit(), i.credit(), i.balance()))
                .toList();

        TaxSummaryDto data = new TaxSummaryDto(items, report.totalBalance());

        auditAccess("tax-summary", Map.of("startDate", startDate, "endDate", endDate));

        return ResponseEntity.ok(new AnalysisResponse<>(
                "tax-summary", LocalDateTime.now(),
                Map.of("startDate", startDate, "endDate", endDate),
                data,
                Map.of("currency", "IDR",
                        "description", "Tax account summary for period " + startDate + " to " + endDate
                                + ". Includes PPN (VAT), PPh (income tax), and other tax accounts.")));
    }

    @GetMapping("/receivables")
    public ResponseEntity<AnalysisResponse<ReceivablesPayablesDto>> getReceivables(
            @RequestParam String asOfDate) {

        LocalDate date = LocalDate.parse(asOfDate);
        ReportService.TrialBalanceReport report = reportService.generateTrialBalance(date);

        List<LineItemDto> items = report.items().stream()
                .filter(i -> i.account().getAccountType() == AccountType.ASSET
                        && i.account().getAccountCode().startsWith("1.1.04"))
                .map(i -> new LineItemDto(
                        i.account().getAccountCode(), i.account().getAccountName(),
                        i.debitBalance()))
                .toList();

        BigDecimal totalBalance = items.stream()
                .map(LineItemDto::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        ReceivablesPayablesDto data = new ReceivablesPayablesDto(items, totalBalance);

        auditAccess("receivables", Map.of("asOfDate", asOfDate));

        return ResponseEntity.ok(new AnalysisResponse<>(
                "receivables", LocalDateTime.now(),
                Map.of("asOfDate", asOfDate),
                data,
                Map.of("currency", "IDR",
                        "description", "Accounts receivable (Piutang) as of " + asOfDate
                                + ". ASSET accounts with code prefix 1.1.04.")));
    }

    @GetMapping("/payables")
    public ResponseEntity<AnalysisResponse<ReceivablesPayablesDto>> getPayables(
            @RequestParam String asOfDate) {

        LocalDate date = LocalDate.parse(asOfDate);
        ReportService.TrialBalanceReport report = reportService.generateTrialBalance(date);

        List<LineItemDto> items = report.items().stream()
                .filter(i -> i.account().getAccountType() == AccountType.LIABILITY
                        && i.account().getAccountCode().startsWith("2.1.01"))
                .map(i -> new LineItemDto(
                        i.account().getAccountCode(), i.account().getAccountName(),
                        i.creditBalance()))
                .toList();

        BigDecimal totalBalance = items.stream()
                .map(LineItemDto::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        ReceivablesPayablesDto data = new ReceivablesPayablesDto(items, totalBalance);

        auditAccess("payables", Map.of("asOfDate", asOfDate));

        return ResponseEntity.ok(new AnalysisResponse<>(
                "payables", LocalDateTime.now(),
                Map.of("asOfDate", asOfDate),
                data,
                Map.of("currency", "IDR",
                        "description", "Accounts payable (Hutang Usaha) as of " + asOfDate
                                + ". LIABILITY accounts with code prefix 2.1.01.")));
    }

    @GetMapping("/accounts")
    public ResponseEntity<AnalysisResponse<AccountsDto>> getAccounts() {

        List<ChartOfAccount> accounts = chartOfAccountRepository.findAllTransactableAccounts();

        List<AccountDto> items = accounts.stream()
                .map(a -> new AccountDto(
                        a.getId(), a.getAccountCode(), a.getAccountName(),
                        a.getAccountType().name(), a.getNormalBalance().name()))
                .toList();

        AccountsDto data = new AccountsDto(items);

        auditAccess("accounts", Map.of());

        return ResponseEntity.ok(new AnalysisResponse<>(
                "accounts", LocalDateTime.now(),
                Map.of(),
                data,
                Map.of("description", "Chart of accounts (leaf/transactable accounts only). "
                        + "normalBalance indicates whether the account normally carries a DEBIT or CREDIT balance.")));
    }

    @GetMapping("/drafts")
    public ResponseEntity<AnalysisResponse<DraftsDto>> getDrafts() {

        List<DraftTransaction> pendingDrafts = draftTransactionRepository
                .findByStatus(DraftTransaction.Status.PENDING);

        List<DraftItemDto> items = pendingDrafts.stream()
                .map(d -> new DraftItemDto(
                        d.getId(),
                        d.getStatus().name(),
                        d.getMerchantName(),
                        d.getAmount(),
                        d.getCurrency(),
                        d.getTransactionDate(),
                        d.getSource() != null ? d.getSource().name() : null,
                        d.getApiSource(),
                        d.getOverallConfidence(),
                        d.getSuggestedTemplate() != null ? d.getSuggestedTemplate().getTemplateName() : null,
                        d.getCreatedBy(),
                        d.getCreatedAt()))
                .toList();

        DraftsDto data = new DraftsDto(items, (long) items.size());

        auditAccess("drafts", Map.of());

        return ResponseEntity.ok(new AnalysisResponse<>(
                "drafts", LocalDateTime.now(),
                Map.of(),
                data,
                Map.of("currency", "IDR",
                        "description", "Pending draft transactions awaiting review. "
                                + "Higher confidence scores indicate more reliable AI extraction.")));
    }

    private void auditAccess(String reportType, Map<String, String> params) {
        securityAuditService.logAsync(AuditEventType.API_CALL,
                "Analysis API: " + reportType + " " + params);
    }

    private TrialBalanceItemDto toTrialBalanceItemDto(ReportService.TrialBalanceItem item) {
        ChartOfAccount a = item.account();
        return new TrialBalanceItemDto(
                a.getAccountCode(), a.getAccountName(),
                a.getAccountType().name(), a.getNormalBalance().name(),
                item.debitBalance(), item.creditBalance());
    }

    private LineItemDto toLineItemDto(ReportService.IncomeStatementItem item) {
        ChartOfAccount a = item.account();
        return new LineItemDto(a.getAccountCode(), a.getAccountName(), item.balance());
    }

    private LineItemDto toBalanceSheetLineItemDto(ReportService.BalanceSheetItem item) {
        ChartOfAccount a = item.account();
        return new LineItemDto(a.getAccountCode(), a.getAccountName(), item.balance());
    }

    // --- DTOs ---

    public record AnalysisResponse<T>(
            String reportType,
            LocalDateTime generatedAt,
            Map<String, String> parameters,
            T data,
            Map<String, String> metadata
    ) {}

    public record SnapshotDto(
            String month,
            BigDecimal revenue, BigDecimal revenueChange,
            BigDecimal expense, BigDecimal expenseChange,
            BigDecimal netProfit, BigDecimal profitChange,
            BigDecimal profitMargin, BigDecimal marginChange,
            BigDecimal cashBalance, BigDecimal receivablesBalance, BigDecimal payablesBalance,
            long transactionCount,
            List<CashBankItemDto> cashBankItems
    ) {}

    public record TrialBalanceDto(
            List<TrialBalanceItemDto> items,
            BigDecimal totalDebit,
            BigDecimal totalCredit
    ) {}

    public record TrialBalanceItemDto(
            String accountCode, String accountName,
            String accountType, String normalBalance,
            BigDecimal debitBalance, BigDecimal creditBalance
    ) {}

    public record IncomeStatementDto(
            List<LineItemDto> revenueItems,
            List<LineItemDto> expenseItems,
            BigDecimal totalRevenue,
            BigDecimal totalExpense,
            BigDecimal netIncome
    ) {}

    public record LineItemDto(
            String accountCode, String accountName,
            BigDecimal balance
    ) {}

    public record BalanceSheetDto(
            List<LineItemDto> assetItems,
            List<LineItemDto> liabilityItems,
            List<LineItemDto> equityItems,
            BigDecimal totalAssets,
            BigDecimal totalLiabilities,
            BigDecimal totalEquity,
            BigDecimal currentYearEarnings
    ) {}

    public record CashFlowDto(
            List<CashFlowItemDto> operatingItems,
            List<CashFlowItemDto> investingItems,
            List<CashFlowItemDto> financingItems,
            BigDecimal operatingTotal,
            BigDecimal investingTotal,
            BigDecimal financingTotal,
            BigDecimal netCashChange,
            BigDecimal beginningCashBalance,
            BigDecimal endingCashBalance,
            List<CashBankItemDto> cashAccountBalances
    ) {}

    public record CashFlowItemDto(
            String description,
            BigDecimal amount
    ) {}

    public record CashBankItemDto(
            String accountName,
            BigDecimal balance
    ) {}

    public record TaxSummaryDto(
            List<TaxItemDto> items,
            BigDecimal totalBalance
    ) {}

    public record TaxItemDto(
            String accountCode, String accountName,
            String label,
            BigDecimal debit, BigDecimal credit, BigDecimal balance
    ) {}

    public record ReceivablesPayablesDto(
            List<LineItemDto> items,
            BigDecimal totalBalance
    ) {}

    public record AccountsDto(
            List<AccountDto> accounts
    ) {}

    public record AccountDto(
            UUID id, String code, String name,
            String type, String normalBalance
    ) {}

    public record DraftsDto(
            List<DraftItemDto> items,
            long pendingCount
    ) {}

    public record DraftItemDto(
            UUID id,
            String status,
            String merchantName,
            BigDecimal amount,
            String currency,
            LocalDate transactionDate,
            String source,
            String apiSource,
            BigDecimal overallConfidence,
            String suggestedTemplateName,
            String createdBy,
            LocalDateTime createdAt
    ) {}
}
