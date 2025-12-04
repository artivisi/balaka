package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.YearMonth;

@Controller
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permission.DASHBOARD_VIEW + "')")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/")
    public String home() {
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("currentPage", "dashboard");
        return "dashboard";
    }

    @GetMapping("/dashboard/kpis")
    public String dashboardKPIs(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            Model model) {
        if (month == null) {
            month = YearMonth.now();
        }

        var kpi = dashboardService.calculateKPIs(month);
        model.addAttribute("kpi", kpi);
        model.addAttribute("selectedMonth", month);

        return "fragments/dashboard-kpis :: kpis";
    }

    @GetMapping("/dashboard/cash-bank-breakdown")
    public String cashBankBreakdown(Model model) {
        var kpi = dashboardService.calculateKPIs(YearMonth.now());
        model.addAttribute("cashBankItems", kpi.cashBankItems());
        model.addAttribute("totalCash", kpi.cashBalance());
        return "fragments/cash-bank-breakdown :: breakdown";
    }

    @GetMapping("/dashboard/recent-transactions")
    public String recentTransactions(Model model) {
        var transactions = dashboardService.getRecentTransactions(10);
        model.addAttribute("transactions", transactions);
        return "fragments/recent-transactions :: transactions";
    }

    @GetMapping("/dashboard/amortization-widget")
    public String amortizationWidget(Model model) {
        var summary = dashboardService.getAmortizationSummary();
        model.addAttribute("summary", summary);
        return "fragments/amortization-widget :: widget";
    }

    @GetMapping("/login")
    @PreAuthorize("permitAll()")
    public String login() {
        return "login";
    }
}
