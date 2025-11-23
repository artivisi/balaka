package com.artivisi.accountingfinance.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/reports")
public class ReportController {

    @GetMapping
    public String index(Model model) {
        model.addAttribute("currentPage", "reports");
        return "reports/index";
    }

    @GetMapping("/trial-balance")
    public String trialBalance(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String asOfDate,
            Model model) {
        model.addAttribute("currentPage", "reports");
        model.addAttribute("reportType", "trial-balance");
        model.addAttribute("period", period);
        model.addAttribute("asOfDate", asOfDate);
        return "reports/trial-balance";
    }

    @GetMapping("/income-statement")
    public String incomeStatement(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String compareWith,
            Model model) {
        model.addAttribute("currentPage", "reports");
        model.addAttribute("reportType", "income-statement");
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("compareWith", compareWith);
        return "reports/income-statement";
    }

    @GetMapping("/balance-sheet")
    public String balanceSheet(
            @RequestParam(required = false) String asOfDate,
            @RequestParam(required = false) String compareWith,
            Model model) {
        model.addAttribute("currentPage", "reports");
        model.addAttribute("reportType", "balance-sheet");
        model.addAttribute("asOfDate", asOfDate);
        model.addAttribute("compareWith", compareWith);
        return "reports/balance-sheet";
    }

    @GetMapping("/cash-flow")
    public String cashFlow(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            Model model) {
        model.addAttribute("currentPage", "reports");
        model.addAttribute("reportType", "cash-flow");
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        return "reports/cash-flow";
    }
}
