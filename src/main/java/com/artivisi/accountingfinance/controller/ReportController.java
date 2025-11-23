package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;

@Controller
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping
    public String index(Model model) {
        model.addAttribute("currentPage", "reports");
        return "reports/index";
    }

    @GetMapping("/trial-balance")
    public String trialBalance(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) LocalDate asOfDate,
            Model model) {
        model.addAttribute("currentPage", "reports");
        model.addAttribute("reportType", "trial-balance");
        model.addAttribute("period", period);

        LocalDate reportDate = asOfDate != null ? asOfDate : LocalDate.now();
        model.addAttribute("asOfDate", reportDate);
        model.addAttribute("report", reportService.generateTrialBalance(reportDate));

        return "reports/trial-balance";
    }

    @GetMapping("/income-statement")
    public String incomeStatement(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) String compareWith,
            Model model) {
        model.addAttribute("currentPage", "reports");
        model.addAttribute("reportType", "income-statement");
        model.addAttribute("compareWith", compareWith);

        LocalDate start = startDate != null ? startDate : LocalDate.now().withDayOfMonth(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now();

        model.addAttribute("startDate", start);
        model.addAttribute("endDate", end);
        model.addAttribute("report", reportService.generateIncomeStatement(start, end));

        return "reports/income-statement";
    }

    @GetMapping("/balance-sheet")
    public String balanceSheet(
            @RequestParam(required = false) LocalDate asOfDate,
            @RequestParam(required = false) String compareWith,
            Model model) {
        model.addAttribute("currentPage", "reports");
        model.addAttribute("reportType", "balance-sheet");
        model.addAttribute("compareWith", compareWith);

        LocalDate reportDate = asOfDate != null ? asOfDate : LocalDate.now();
        model.addAttribute("asOfDate", reportDate);
        model.addAttribute("report", reportService.generateBalanceSheet(reportDate));

        return "reports/balance-sheet";
    }

    @GetMapping("/cash-flow")
    public String cashFlow(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            Model model) {
        model.addAttribute("currentPage", "reports");
        model.addAttribute("reportType", "cash-flow");

        LocalDate start = startDate != null ? startDate : LocalDate.now().withDayOfMonth(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now();

        model.addAttribute("startDate", start);
        model.addAttribute("endDate", end);

        return "reports/cash-flow";
    }

    // REST API Endpoints

    @GetMapping("/api/trial-balance")
    @ResponseBody
    public ResponseEntity<ReportService.TrialBalanceReport> apiTrialBalance(
            @RequestParam(required = false) LocalDate asOfDate) {
        LocalDate reportDate = asOfDate != null ? asOfDate : LocalDate.now();
        return ResponseEntity.ok(reportService.generateTrialBalance(reportDate));
    }

    @GetMapping("/api/income-statement")
    @ResponseBody
    public ResponseEntity<ReportService.IncomeStatementReport> apiIncomeStatement(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        return ResponseEntity.ok(reportService.generateIncomeStatement(startDate, endDate));
    }

    @GetMapping("/api/balance-sheet")
    @ResponseBody
    public ResponseEntity<ReportService.BalanceSheetReport> apiBalanceSheet(
            @RequestParam(required = false) LocalDate asOfDate) {
        LocalDate reportDate = asOfDate != null ? asOfDate : LocalDate.now();
        return ResponseEntity.ok(reportService.generateBalanceSheet(reportDate));
    }
}
