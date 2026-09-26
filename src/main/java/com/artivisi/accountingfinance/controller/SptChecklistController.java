package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.FiscalLossCarryforward;
import com.artivisi.accountingfinance.enums.FiscalPeriodStatus;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.CoretaxExportService;
import com.artivisi.accountingfinance.service.DepreciationReportService;
import com.artivisi.accountingfinance.service.FiscalPeriodService;
import com.artivisi.accountingfinance.service.PayrollService;
import com.artivisi.accountingfinance.service.ReportService;
import com.artivisi.accountingfinance.service.SptTahunanExportService;
import com.artivisi.accountingfinance.service.TaxReportDetailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static com.artivisi.accountingfinance.controller.ViewConstants.ATTR_CURRENT_PAGE;
import static com.artivisi.accountingfinance.controller.ViewConstants.PAGE_SPT_CHECKLIST;

@Controller
@RequestMapping("/reports/spt-checklist")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAuthority('" + Permission.TAX_REPORT_VIEW + "')")
public class SptChecklistController {

    private final ReportService reportService;
    private final TaxReportDetailService taxReportDetailService;
    private final SptTahunanExportService sptTahunanExportService;
    private final FiscalPeriodService fiscalPeriodService;
    private final PayrollService payrollService;
    private final DepreciationReportService depreciationReportService;

    @GetMapping
    public String show(
            @RequestParam(required = false) Integer year,
            Model model) {

        int targetYear = year != null ? year : Year.now().getValue() - 1;
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_SPT_CHECKLIST);
        model.addAttribute("year", targetYear);

        // Generate checklist items
        ChecklistResult checklist = generateChecklist(targetYear);
        model.addAttribute("checklist", checklist);

        // The three previous years, plus the displayed year when it lies outside them
        int currentYear = Year.now().getValue();
        TreeSet<Integer> availableYears = new TreeSet<>(Comparator.reverseOrder());
        availableYears.addAll(List.of(currentYear - 1, currentYear - 2, currentYear - 3, targetYear));
        model.addAttribute("availableYears", List.copyOf(availableYears));

        return "reports/spt-checklist";
    }

    private ChecklistResult generateChecklist(int year) {
        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDate = LocalDate.of(year, 12, 31);
        Map<String, String> errors = new LinkedHashMap<>();

        // 1. Financial statements available
        boolean hasIncomeStatement;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal netIncome = BigDecimal.ZERO;
        try {
            var incomeStatement = reportService.generateIncomeStatementExcludingClosing(startDate, endDate);
            totalRevenue = incomeStatement.totalRevenue();
            netIncome = incomeStatement.netIncome();
            hasIncomeStatement = totalRevenue.compareTo(BigDecimal.ZERO) != 0
                    || incomeStatement.totalExpense().compareTo(BigDecimal.ZERO) != 0;
        } catch (RuntimeException e) {
            hasIncomeStatement = false;
            recordFailure(errors, "check-financial-statements", year, e);
        }

        // 2. Fiscal adjustments entered
        var adjustments = taxReportDetailService.findAdjustmentsByYear(year);
        boolean hasFiscalAdjustments = !adjustments.isEmpty();
        int adjustmentCount = adjustments.size();

        // 3. PPh Badan calculated
        boolean hasPphBadan = false;
        BigDecimal pphTerutang = BigDecimal.ZERO;
        BigDecimal pph29 = BigDecimal.ZERO;
        try {
            var rekon = taxReportDetailService.generateRekonsiliasiFiskal(year);
            hasPphBadan = true;
            pphTerutang = rekon.pphBadan().pphTerutang();
            pph29 = rekon.pphBadan().pph29();
        } catch (RuntimeException e) {
            recordFailure(errors, "check-pph-badan", year, e);
        }

        // 4. All monthly fiscal periods closed
        long closedMonths = 0;
        try {
            var periods = fiscalPeriodService.findByYear(year);
            closedMonths = periods.stream()
                    .filter(p -> p.getStatus() == FiscalPeriodStatus.MONTH_CLOSED
                            || p.getStatus() == FiscalPeriodStatus.TAX_FILED)
                    .count();
        } catch (RuntimeException e) {
            recordFailure(errors, "check-fiscal-periods", year, e);
        }
        boolean allMonthsClosed = closedMonths == 12;

        // 5. Fixed assets depreciation run
        boolean hasDepreciation;
        int assetCount = 0;
        try {
            var depReport = depreciationReportService.generateReport(year);
            assetCount = depReport.items().size();
            hasDepreciation = assetCount > 0;
        } catch (RuntimeException e) {
            hasDepreciation = false;
            recordFailure(errors, "check-depreciation", year, e);
        }

        // 6. Payroll complete
        int payrollMonths = 0;
        try {
            var employeeIds = payrollService.getEmployeesWithPayrollInYear(year);
            payrollMonths = employeeIds.isEmpty() ? 0 :
                    payrollService.getYearlyPayrollSummary(employeeIds.getFirst(), year).monthCount();
        } catch (RuntimeException e) {
            recordFailure(errors, "check-payroll", year, e);
        }

        // 7. Loss carryforwards
        var lossReport = sptTahunanExportService.generateLossCarryforward(year);
        boolean hasActiveLosses = lossReport.totalActiveRemaining().compareTo(BigDecimal.ZERO) > 0;

        return new ChecklistResult(
                year, hasIncomeStatement, totalRevenue, netIncome,
                hasFiscalAdjustments, adjustmentCount,
                hasPphBadan, pphTerutang, pph29,
                allMonthsClosed, closedMonths,
                hasDepreciation, assetCount,
                payrollMonths,
                hasActiveLosses, lossReport.totalActiveRemaining(),
                errors
        );
    }

    private void recordFailure(Map<String, String> errors, String item, int year, RuntimeException e) {
        log.error("SPT checklist {} failed for year {}", item, year, e);
        errors.put(item, e.getMessage() != null ? e.getMessage() : e.getClass().getName());
    }

    public record ChecklistResult(
            int year,
            boolean hasIncomeStatement,
            BigDecimal totalRevenue,
            BigDecimal netIncome,
            boolean hasFiscalAdjustments,
            int adjustmentCount,
            boolean hasPphBadan,
            BigDecimal pphTerutang,
            BigDecimal pph29,
            boolean allMonthsClosed,
            long closedMonths,
            boolean hasDepreciation,
            int assetCount,
            int payrollMonths,
            boolean hasActiveLosses,
            BigDecimal activeLossAmount,
            Map<String, String> errors
    ) {}
}
