package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.exception.MissingConfigurationException;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.entity.CompanyConfig;
import com.artivisi.accountingfinance.service.CompanyConfigService;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.security.TotpService;
import com.artivisi.accountingfinance.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Base64;

import static com.artivisi.accountingfinance.controller.ViewConstants.*;

@Controller
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAuthority('" + Permission.DASHBOARD_VIEW + "')")
public class DashboardController {

    private final DashboardService dashboardService;
    private final CompanyConfigService companyConfigService;
    private final TotpService totpService;
    private final UserRepository userRepository;

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
    public String login(HttpServletRequest request, Model model) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("MFA_PENDING_USER") != null) {
            model.addAttribute("mfaPending", true);
            model.addAttribute("mfaUsername", session.getAttribute("MFA_PENDING_USER"));
        }

        // Check for MFA error message
        if (session != null && session.getAttribute("MFA_ERROR_MESSAGE") != null) {
            model.addAttribute("errorMessage", session.getAttribute("MFA_ERROR_MESSAGE"));
            session.removeAttribute("MFA_ERROR_MESSAGE");
        }

        // Check if authenticated user needs MFA setup
        org.springframework.security.core.Authentication auth =
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && !(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
            User user = userRepository.findByUsername(auth.getName()).orElse(null);
            if (user != null && !user.getMfaEnabled()) {
                model.addAttribute("mfaSetup", true);
                model.addAttribute("mfaUsername", user.getUsername());
                String secret = totpService.generateSecret();
                String otpAuthUri = totpService.getOtpAuthUri(secret, user.getUsername(), "Balaka");
                model.addAttribute("mfaSecret", secret);
                model.addAttribute("mfaOtpAuthUri", otpAuthUri);
                // Generate QR code as base64 data URI using external API
                try {
                    String encodedUri = java.net.URLEncoder.encode(otpAuthUri, java.nio.charset.StandardCharsets.UTF_8);
                    String qrUrl = "https://api.qrserver.com/v1/create-qr-code/?size=180x180&data=" + encodedUri;
                    java.net.URL url = new java.net.URL(qrUrl);
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    java.io.InputStream is = conn.getInputStream();
                    byte[] imageBytes = is.readAllBytes();
                    is.close();
                    String base64 = Base64.getEncoder().encodeToString(imageBytes);
                    model.addAttribute("qrCodeDataUri", "data:image/png;base64," + base64);
                } catch (Exception e) {
                    log.warn("Failed to generate QR code image: {}", e.getMessage());
                }
            }
        }

        try {
            CompanyConfig company = companyConfigService.getConfig();
            model.addAttribute("company", company);
        } catch (Exception e) {
            // ignore if no company config
        }
        return "login";
    }
}
