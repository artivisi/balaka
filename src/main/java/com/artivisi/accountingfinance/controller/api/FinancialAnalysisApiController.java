package com.artivisi.accountingfinance.controller.api;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.CompanyConfig;
import com.artivisi.accountingfinance.enums.AccountType;
import com.artivisi.accountingfinance.enums.AuditEventType;
import com.artivisi.accountingfinance.enums.TaxFilingStatus;
import com.artivisi.accountingfinance.enums.TaxFilingType;
import com.artivisi.accountingfinance.repository.CompanyConfigRepository;
import com.artivisi.accountingfinance.service.DashboardService;
import com.artivisi.accountingfinance.service.ReportService;
import com.artivisi.accountingfinance.service.SecurityAuditService;
import com.artivisi.accountingfinance.service.TaxFilingService;
import com.artivisi.accountingfinance.service.TaxReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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
import java.util.List;
import java.util.Map;

/**
 * Read-only financial analysis API for external AI tools.
 * Provides company info, snapshots, and financial report data.
 */
@RestController
@RequestMapping("/api/analysis")
@Tag(name = "Financial Analysis", description = "Read-only financial data for AI analysis (reports, snapshots, ledgers)")
@PreAuthorize("hasAuthority('SCOPE_analysis:read')")
@RequiredArgsConstructor
public class FinancialAnalysisApiController {

    private static final String META_CURRENCY = "currency";
    private static final String META_CURRENCY_IDR = "IDR";
    private static final String META_DESCRIPTION = "description";
    private static final String META_ACCOUNTING_BASIS = "accountingBasis";
    private static final String META_ACCRUAL = "accrual";
    private static final String PARAM_COMPANY = "company";
    private static final String PARAM_AS_OF_DATE = "asOfDate";
    private static final String PARAM_START_DATE = "startDate";
    private static final String PARAM_END_DATE = "endDate";

    private final ReportService reportService;
    private final DashboardService dashboardService;
    private final TaxReportService taxReportService;
    private final TaxFilingService taxFilingService;
    private final CompanyConfigRepository companyConfigRepository;
    private final SecurityAuditService securityAuditService;

    @GetMapping("/company")
    public ResponseEntity<AnalysisResponse<CompanyDto>> getCompany() {
        CompanyConfig config = companyConfigRepository.findFirst().orElse(null);
        if (config == null) {
            return ResponseEntity.ok(new AnalysisResponse<>(
                    PARAM_COMPANY, LocalDateTime.now(), Map.of(),
                    new CompanyDto(null, null, null, null, null, null),
                    Map.of(META_DESCRIPTION, "No company configuration found.")));
        }

        CompanyDto data = new CompanyDto(
                config.getCompanyName(), config.getIndustry(),
                config.getCurrencyCode(), config.getFiscalYearStartMonth(),
                config.getIsPkp(), config.getNpwp());

        auditAccess(PARAM_COMPANY, Map.of());

        return ResponseEntity.ok(new AnalysisResponse<>(
                PARAM_COMPANY, LocalDateTime.now(), Map.of(), data,
                Map.of(META_DESCRIPTION, "Company configuration. The 'industry' field determines "
                        + "which analysis types and KPIs are relevant for this business.")));
    }

    @GetMapping("/snapshot")
    public ResponseEntity<AnalysisResponse<SnapshotDto>> getSnapshot(
            @RequestParam String month,
            @RequestParam(required = false) Integer year) {

        YearMonth ym = parseYearMonth(month, year);
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
                Map.of(META_CURRENCY, META_CURRENCY_IDR,
                        META_ACCOUNTING_BASIS, META_ACCRUAL,
                        META_DESCRIPTION, "Financial KPI snapshot for " + month
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

        auditAccess("trial-balance", Map.of(PARAM_AS_OF_DATE, asOfDate));

        return ResponseEntity.ok(new AnalysisResponse<>(
                "trial-balance", LocalDateTime.now(),
                Map.of(PARAM_AS_OF_DATE, asOfDate),
                data,
                Map.of(META_CURRENCY, META_CURRENCY_IDR,
                        META_ACCOUNTING_BASIS, META_ACCRUAL,
                        META_DESCRIPTION, "Trial balance as of " + asOfDate
                                + ". Each account shows debit and credit balances. "
                                + "Total debits must equal total credits.")));
    }

    @GetMapping("/income-statement")
    public ResponseEntity<AnalysisResponse<IncomeStatementDto>> getIncomeStatement(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(defaultValue = "false") boolean excludeClosing) {

        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        ReportService.IncomeStatementReport report = excludeClosing
                ? reportService.generateIncomeStatementExcludingClosing(start, end)
                : reportService.generateIncomeStatement(start, end);

        List<LineItemDto> revenueItems = report.revenueItems().stream()
                .map(this::toLineItemDto)
                .toList();
        List<LineItemDto> expenseItems = report.expenseItems().stream()
                .map(this::toLineItemDto)
                .toList();

        IncomeStatementDto data = new IncomeStatementDto(
                revenueItems, expenseItems,
                report.totalRevenue(), report.totalExpense(), report.netIncome());

        auditAccess("income-statement", Map.of(PARAM_START_DATE, startDate, PARAM_END_DATE, endDate));

        return ResponseEntity.ok(new AnalysisResponse<>(
                "income-statement", LocalDateTime.now(),
                Map.of(PARAM_START_DATE, startDate, PARAM_END_DATE, endDate),
                data,
                Map.of(META_CURRENCY, META_CURRENCY_IDR,
                        META_ACCOUNTING_BASIS, META_ACCRUAL,
                        META_DESCRIPTION, "Income statement for period " + startDate + " to " + endDate
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

        auditAccess("balance-sheet", Map.of(PARAM_AS_OF_DATE, asOfDate));

        return ResponseEntity.ok(new AnalysisResponse<>(
                "balance-sheet", LocalDateTime.now(),
                Map.of(PARAM_AS_OF_DATE, asOfDate),
                data,
                Map.of(META_CURRENCY, META_CURRENCY_IDR,
                        META_ACCOUNTING_BASIS, META_ACCRUAL,
                        META_DESCRIPTION, "Balance sheet as of " + asOfDate
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

        auditAccess("cash-flow", Map.of(PARAM_START_DATE, startDate, PARAM_END_DATE, endDate));

        return ResponseEntity.ok(new AnalysisResponse<>(
                "cash-flow", LocalDateTime.now(),
                Map.of(PARAM_START_DATE, startDate, PARAM_END_DATE, endDate),
                data,
                Map.of(META_CURRENCY, META_CURRENCY_IDR,
                        META_ACCOUNTING_BASIS, META_ACCRUAL,
                        META_DESCRIPTION, "Cash flow statement for period " + startDate + " to " + endDate
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

        // Filing status comes from the tax filing register (issue #35). Only registered
        // filings are listed — a masa absent from this list has no record at all, which
        // is itself the signal that it still needs filing.
        List<TaxFilingStatusDto> filings = taxFilingService.findOverlapping(start, end).stream()
                .map(f -> new TaxFilingStatusDto(
                        f.getTaxType(), f.getPeriod(), f.getPembetulanNo(), f.getStatus(),
                        f.getFiledDate(), f.getBpeNumber(), f.getKurangBayar(), f.getLebihBayar(),
                        f.isNihil(), f.getPaidDate()))
                .toList();

        TaxSummaryDto data = new TaxSummaryDto(items, report.totalBalance(), filings);

        auditAccess("tax-summary", Map.of(PARAM_START_DATE, startDate, PARAM_END_DATE, endDate));

        return ResponseEntity.ok(new AnalysisResponse<>(
                "tax-summary", LocalDateTime.now(),
                Map.of(PARAM_START_DATE, startDate, PARAM_END_DATE, endDate),
                data,
                Map.of(META_CURRENCY, META_CURRENCY_IDR,
                        META_DESCRIPTION, "Tax account summary for period " + startDate + " to " + endDate
                                + ". Includes PPN (VAT), PPh (income tax), and other tax accounts. "
                                + "filings lists the SPT registered for periods overlapping this range; "
                                + "a masa with no entry has not been registered as filed.")));
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

        auditAccess("receivables", Map.of(PARAM_AS_OF_DATE, asOfDate));

        return ResponseEntity.ok(new AnalysisResponse<>(
                "receivables", LocalDateTime.now(),
                Map.of(PARAM_AS_OF_DATE, asOfDate),
                data,
                Map.of(META_CURRENCY, META_CURRENCY_IDR,
                        META_DESCRIPTION, "Accounts receivable (Piutang) as of " + asOfDate
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

        auditAccess("payables", Map.of(PARAM_AS_OF_DATE, asOfDate));

        return ResponseEntity.ok(new AnalysisResponse<>(
                "payables", LocalDateTime.now(),
                Map.of(PARAM_AS_OF_DATE, asOfDate),
                data,
                Map.of(META_CURRENCY, META_CURRENCY_IDR,
                        META_DESCRIPTION, "Accounts payable (Hutang Usaha) as of " + asOfDate
                                + ". LIABILITY accounts with code prefix 2.1.01.")));
    }

    /**
     * Parse month parameter supporting both "YYYY-MM" format and separate month+year params.
     * Examples: month=2025-12 OR month=12&year=2025
     */
    private YearMonth parseYearMonth(String month, Integer year) {
        try {
            if (year != null) {
                return YearMonth.of(year, Integer.parseInt(month));
            }
            return YearMonth.parse(month);
        } catch (NumberFormatException | java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid month format: " + month, e);
        }
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

    public record CompanyDto(
            String companyName,
            String industry,
            String currencyCode,
            Integer fiscalYearStartMonth,
            Boolean isPkp,
            String npwp
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
            BigDecimal totalBalance,
            List<TaxFilingStatusDto> filings
    ) {}

    public record TaxFilingStatusDto(
            TaxFilingType taxType,
            String period,
            Integer pembetulanNo,
            TaxFilingStatus status,
            LocalDate filedDate,
            String bpeNumber,
            BigDecimal kurangBayar,
            BigDecimal lebihBayar,
            boolean nihil,
            LocalDate paidDate
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
}
