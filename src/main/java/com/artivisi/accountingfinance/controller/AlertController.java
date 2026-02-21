package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.AlertEvent;
import com.artivisi.accountingfinance.entity.AlertRule;
import com.artivisi.accountingfinance.enums.AlertSeverity;
import com.artivisi.accountingfinance.enums.AlertType;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.artivisi.accountingfinance.controller.ViewConstants.*;

@Controller
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permission.ALERT_VIEW + "')")
public class AlertController {

    private static final String ATTR_SUCCESS_MESSAGE = "successMessage";

    private final AlertService alertService;

    @GetMapping("/alerts")
    public String activeAlerts(Model model) {
        List<AlertEvent> activeAlerts = alertService.findActiveAlerts();
        model.addAttribute("alerts", activeAlerts);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_ALERTS);
        return "alerts/active";
    }

    @PostMapping("/alerts/{id}/acknowledge")
    @PreAuthorize("hasAuthority('" + Permission.ALERT_ACKNOWLEDGE + "')")
    public String acknowledge(@PathVariable UUID id, Authentication authentication, RedirectAttributes redirectAttributes) {
        alertService.acknowledge(id, authentication.getName());
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Peringatan telah dikonfirmasi");
        return "redirect:/alerts";
    }

    @GetMapping("/alerts/config")
    @PreAuthorize("hasAuthority('" + Permission.ALERT_CONFIG + "')")
    public String config(Model model) {
        List<AlertRule> rules = alertService.findAllRules();
        model.addAttribute("rules", rules);
        model.addAttribute("alertTypes", AlertType.values());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_ALERT_CONFIG);
        return "alerts/config";
    }

    @PostMapping("/alerts/config/{id}")
    @PreAuthorize("hasAuthority('" + Permission.ALERT_CONFIG + "')")
    public String updateRule(
            @PathVariable UUID id,
            @RequestParam BigDecimal threshold,
            @RequestParam(required = false) boolean enabled,
            RedirectAttributes redirectAttributes) {
        alertService.updateRule(id, threshold, enabled);
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Konfigurasi peringatan berhasil diperbarui");
        return "redirect:/alerts/config";
    }

    @GetMapping("/alerts/history")
    public String history(
            @RequestParam(required = false) AlertType alertType,
            @RequestParam(required = false) AlertSeverity severity,
            @RequestParam(required = false) Boolean acknowledged,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            @PageableDefault(size = 20) Pageable pageable,
            Model model) {

        Page<AlertEvent> events = alertService.findAlertHistory(alertType, severity, acknowledged, pageable);
        model.addAttribute("events", events);
        model.addAttribute("alertTypes", AlertType.values());
        model.addAttribute("severities", AlertSeverity.values());
        model.addAttribute("selectedAlertType", alertType);
        model.addAttribute("selectedSeverity", severity);
        model.addAttribute("selectedAcknowledged", acknowledged);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_ALERT_HISTORY);

        if ("true".equals(hxRequest)) {
            return "alerts/fragments/alert-history-table :: table";
        }

        return "alerts/history";
    }

    @GetMapping("/dashboard/alerts")
    public String dashboardWidget(Model model) {
        List<AlertEvent> activeAlerts = alertService.findActiveAlerts();
        long activeCount = alertService.countActiveAlerts();

        long criticalCount = activeAlerts.stream()
                .filter(e -> e.getSeverity() == AlertSeverity.CRITICAL).count();
        long warningCount = activeAlerts.stream()
                .filter(e -> e.getSeverity() == AlertSeverity.WARNING).count();
        long infoCount = activeAlerts.stream()
                .filter(e -> e.getSeverity() == AlertSeverity.INFO).count();

        List<AlertEvent> recentAlerts = activeAlerts.stream().limit(5).toList();

        model.addAttribute("activeCount", activeCount);
        model.addAttribute("criticalCount", criticalCount);
        model.addAttribute("warningCount", warningCount);
        model.addAttribute("infoCount", infoCount);
        model.addAttribute("recentAlerts", recentAlerts);

        return "fragments/alert-widget :: widget";
    }
}
