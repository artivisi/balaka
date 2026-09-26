package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.exception.MissingConfigurationException;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.YearMonth;

import static com.artivisi.accountingfinance.controller.ViewConstants.*;

@Controller
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAuthority('" + Permission.DASHBOARD_VIEW + "')")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/")
    public String home() {
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_DASHBOARD);
        return "dashboard";
    }

    @GetMapping("/dashboard/kpis")
    public String dashboardKPIs(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            Model model) {
        if (month == null) {
            month = YearMonth.now();
        }

        model.addAttribute("selectedMonth", month);
        try {
            model.addAttribute("kpi", dashboardService.calculateKPIs(month));
        } catch (MissingConfigurationException e) {
            log.error("Dashboard KPIs unavailable: {}", e.getMessage());
            model.addAttribute("configError", e.getMessage());
            return "fragments/dashboard-kpis :: config-error";
        }

        return "fragments/dashboard-kpis :: kpis";
    }

    @GetMapping("/dashboard/cash-bank-breakdown")
    public String cashBankBreakdown(Model model) {
        LocalDate monthEnd = YearMonth.now().atEndOfMonth();
        model.addAttribute("cashBankItems", dashboardService.calculateCashBankBreakdown(monthEnd));
        model.addAttribute("totalCash", dashboardService.calculateCashBalance(monthEnd));
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
