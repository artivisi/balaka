package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.AlertEvent;
import com.artivisi.accountingfinance.entity.AlertRule;
import com.artivisi.accountingfinance.entity.Invoice;
import com.artivisi.accountingfinance.entity.Project;
import com.artivisi.accountingfinance.enums.AlertSeverity;
import com.artivisi.accountingfinance.enums.AlertType;
import com.artivisi.accountingfinance.enums.InvoiceStatus;
import com.artivisi.accountingfinance.enums.ProjectStatus;
import com.artivisi.accountingfinance.repository.AlertEventRepository;
import com.artivisi.accountingfinance.repository.AlertRuleRepository;
import com.artivisi.accountingfinance.repository.InvoiceRepository;
import com.artivisi.accountingfinance.repository.ProjectRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AlertService {

    private final AlertRuleRepository alertRuleRepository;
    private final AlertEventRepository alertEventRepository;
    private final DashboardService dashboardService;
    private final InvoiceRepository invoiceRepository;
    private final ProjectProfitabilityService projectProfitabilityService;
    private final ProjectRepository projectRepository;

    // --- CRUD ---

    public List<AlertRule> findAllRules() {
        return alertRuleRepository.findAllByOrderByAlertTypeAsc();
    }

    public Optional<AlertRule> findRuleById(UUID id) {
        return alertRuleRepository.findById(id);
    }

    @Transactional
    public AlertRule updateRule(UUID id, BigDecimal threshold, boolean enabled) {
        AlertRule rule = alertRuleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Alert rule not found: " + id));
        rule.setThreshold(threshold);
        rule.setEnabled(enabled);
        return alertRuleRepository.save(rule);
    }

    public List<AlertEvent> findActiveAlerts() {
        return alertEventRepository.findByAcknowledgedAtIsNullOrderByTriggeredAtDesc();
    }

    public long countActiveAlerts() {
        return alertEventRepository.countByAcknowledgedAtIsNull();
    }

    @Transactional
    public void acknowledge(UUID eventId, String username) {
        AlertEvent event = alertEventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Alert event not found: " + eventId));
        event.setAcknowledgedAt(LocalDateTime.now());
        event.setAcknowledgedBy(username);
        alertEventRepository.save(event);
    }

    public Page<AlertEvent> findAlertHistory(AlertType alertType, AlertSeverity severity, Boolean acknowledged, Pageable pageable) {
        return alertEventRepository.findByFilters(alertType, severity, acknowledged, pageable);
    }

    // --- Evaluation ---

    @Transactional
    public int evaluateAllAlerts() {
        List<AlertRule> enabledRules = alertRuleRepository.findByEnabledTrue();
        int triggered = 0;

        for (AlertRule rule : enabledRules) {
            try {
                // Dedup: skip if unacknowledged event for same rule within 24h
                if (alertEventRepository.existsRecentUnacknowledged(rule.getId(), LocalDateTime.now().minusHours(24))) {
                    log.debug("Skipping {} — recent unacknowledged event exists", rule.getAlertType());
                    continue;
                }

                AlertEvent event = evaluateRule(rule);
                if (event != null) {
                    alertEventRepository.save(event);
                    rule.setLastTriggeredAt(LocalDateTime.now());
                    alertRuleRepository.save(rule);
                    triggered++;
                    log.info("Alert triggered: {} [{}] — {}", rule.getAlertType(), event.getSeverity(), event.getMessage());
                }
            } catch (Exception e) {
                log.error("Failed to evaluate alert rule: {}", rule.getAlertType(), e);
            }
        }

        return triggered;
    }

    private AlertEvent evaluateRule(AlertRule rule) {
        return switch (rule.getAlertType()) {
            case CASH_LOW -> evaluateCashLow(rule);
            case RECEIVABLE_OVERDUE -> evaluateReceivableOverdue(rule);
            case EXPENSE_SPIKE -> evaluateExpenseSpike(rule);
            case PROJECT_COST_OVERRUN -> evaluateProjectCostOverrun(rule);
            case PROJECT_MARGIN_DROP -> evaluateProjectMarginDrop(rule);
            case COLLECTION_SLOWDOWN -> evaluateCollectionSlowdown(rule);
            case CLIENT_CONCENTRATION -> evaluateClientConcentration(rule);
        };
    }

    private AlertEvent evaluateCashLow(AlertRule rule) {
        YearMonth now = YearMonth.now();
        DashboardService.DashboardKPI kpi = dashboardService.calculateKPIs(now);
        BigDecimal cashBalance = kpi.cashBalance();

        if (cashBalance.compareTo(rule.getThreshold()) < 0) {
            AlertSeverity severity = cashBalance.compareTo(BigDecimal.ZERO) <= 0
                    ? AlertSeverity.CRITICAL : AlertSeverity.WARNING;

            AlertEvent event = new AlertEvent();
            event.setAlertRule(rule);
            event.setSeverity(severity);
            event.setMessage(String.format("Saldo kas + bank: Rp %,.0f (ambang batas: Rp %,.0f)",
                    cashBalance, rule.getThreshold()));
            return event;
        }
        return null;
    }

    private AlertEvent evaluateReceivableOverdue(AlertRule rule) {
        List<Invoice> overdueInvoices = invoiceRepository.findOverdueInvoices(LocalDate.now());

        if (overdueInvoices.isEmpty()) {
            return null;
        }

        BigDecimal totalOverdue = overdueInvoices.stream()
                .map(Invoice::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalOverdue.compareTo(rule.getThreshold()) > 0) {
            AlertSeverity severity = overdueInvoices.size() > 5
                    ? AlertSeverity.CRITICAL : AlertSeverity.WARNING;

            AlertEvent event = new AlertEvent();
            event.setAlertRule(rule);
            event.setSeverity(severity);
            event.setMessage(String.format("%d piutang jatuh tempo, total: Rp %,.0f",
                    overdueInvoices.size(), totalOverdue));
            return event;
        }
        return null;
    }

    private AlertEvent evaluateExpenseSpike(AlertRule rule) {
        YearMonth currentMonth = YearMonth.now();
        DashboardService.DashboardKPI currentKpi = dashboardService.calculateKPIs(currentMonth);
        BigDecimal currentExpense = currentKpi.expense();

        // Calculate average of previous 3 months
        BigDecimal avgExpense = BigDecimal.ZERO;
        for (int i = 1; i <= 3; i++) {
            DashboardService.DashboardKPI prevKpi = dashboardService.calculateKPIs(currentMonth.minusMonths(i));
            avgExpense = avgExpense.add(prevKpi.expense());
        }
        avgExpense = avgExpense.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);

        if (avgExpense.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        BigDecimal increasePercent = currentExpense.subtract(avgExpense)
                .multiply(BigDecimal.valueOf(100))
                .divide(avgExpense, 2, RoundingMode.HALF_UP);

        if (increasePercent.compareTo(rule.getThreshold()) > 0) {
            AlertSeverity severity = increasePercent.compareTo(BigDecimal.valueOf(50)) > 0
                    ? AlertSeverity.CRITICAL : AlertSeverity.WARNING;

            AlertEvent event = new AlertEvent();
            event.setAlertRule(rule);
            event.setSeverity(severity);
            event.setMessage(String.format("Biaya bulan ini naik %.1f%% dari rata-rata 3 bulan sebelumnya (Rp %,.0f vs Rp %,.0f)",
                    increasePercent, currentExpense, avgExpense));
            return event;
        }
        return null;
    }

    private AlertEvent evaluateProjectCostOverrun(AlertRule rule) {
        List<Project> activeProjects = projectRepository.findByStatus(ProjectStatus.ACTIVE);
        int highRiskCount = 0;
        StringBuilder details = new StringBuilder();

        for (Project project : activeProjects) {
            ProjectProfitabilityService.CostOverrunReport report =
                    projectProfitabilityService.calculateCostOverrun(project.getId());

            if (report.riskLevel() == ProjectProfitabilityService.CostOverrunRisk.HIGH) {
                highRiskCount++;
                details.append(String.format("%s (spent %.0f%% of budget at %d%% progress), ",
                        project.getName(), report.budgetSpentPercent(), report.progressPercent()));
            }
        }

        if (highRiskCount > 0) {
            AlertSeverity severity = highRiskCount > 2
                    ? AlertSeverity.CRITICAL : AlertSeverity.WARNING;

            AlertEvent event = new AlertEvent();
            event.setAlertRule(rule);
            event.setSeverity(severity);
            event.setMessage(String.format("%d proyek berisiko melebihi anggaran", highRiskCount));
            event.setDetails(details.toString());
            return event;
        }
        return null;
    }

    private AlertEvent evaluateProjectMarginDrop(AlertRule rule) {
        List<Project> activeProjects = projectRepository.findByStatus(ProjectStatus.ACTIVE);
        int lowMarginCount = 0;
        boolean hasNegativeMargin = false;
        StringBuilder details = new StringBuilder();

        LocalDate startDate = LocalDate.of(1900, 1, 1);
        LocalDate endDate = LocalDate.now();

        for (Project project : activeProjects) {
            ProjectProfitabilityService.ProjectProfitabilityReport report =
                    projectProfitabilityService.calculateProjectProfitability(project.getId(), startDate, endDate);

            if (report.totalRevenue().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            if (report.profitMargin().compareTo(rule.getThreshold()) < 0) {
                lowMarginCount++;
                if (report.profitMargin().compareTo(BigDecimal.ZERO) < 0) {
                    hasNegativeMargin = true;
                }
                details.append(String.format("%s (margin: %.1f%%), ", project.getName(), report.profitMargin()));
            }
        }

        if (lowMarginCount > 0) {
            AlertSeverity severity = hasNegativeMargin
                    ? AlertSeverity.CRITICAL : AlertSeverity.WARNING;

            AlertEvent event = new AlertEvent();
            event.setAlertRule(rule);
            event.setSeverity(severity);
            event.setMessage(String.format("%d proyek dengan margin di bawah %.0f%%", lowMarginCount, rule.getThreshold()));
            event.setDetails(details.toString());
            return event;
        }
        return null;
    }

    private AlertEvent evaluateCollectionSlowdown(AlertRule rule) {
        List<Invoice> paidInvoices = invoiceRepository.findByStatus(InvoiceStatus.PAID);

        // Filter to last 6 months and calculate avg days
        LocalDateTime sixMonthsAgo = LocalDateTime.now().minusMonths(6);
        List<Invoice> recentPaid = paidInvoices.stream()
                .filter(i -> i.getPaidAt() != null && i.getSentAt() != null)
                .filter(i -> i.getPaidAt().isAfter(sixMonthsAgo))
                .toList();

        if (recentPaid.isEmpty()) {
            return null;
        }

        long totalDays = recentPaid.stream()
                .mapToLong(i -> ChronoUnit.DAYS.between(i.getSentAt(), i.getPaidAt()))
                .sum();
        double avgDays = (double) totalDays / recentPaid.size();

        if (BigDecimal.valueOf(avgDays).compareTo(rule.getThreshold()) > 0) {
            AlertEvent event = new AlertEvent();
            event.setAlertRule(rule);
            event.setSeverity(AlertSeverity.WARNING);
            event.setMessage(String.format("Rata-rata penagihan: %.0f hari (ambang batas: %.0f hari)",
                    avgDays, rule.getThreshold()));
            return event;
        }
        return null;
    }

    private AlertEvent evaluateClientConcentration(AlertRule rule) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusMonths(12);

        List<ProjectProfitabilityService.ClientRankingItem> rankings =
                projectProfitabilityService.getClientRanking(startDate, endDate, 0);

        for (ProjectProfitabilityService.ClientRankingItem item : rankings) {
            if (item.revenuePercentage().compareTo(rule.getThreshold()) > 0) {
                AlertEvent event = new AlertEvent();
                event.setAlertRule(rule);
                event.setSeverity(AlertSeverity.WARNING);
                event.setMessage(String.format("Klien %s menyumbang %.1f%% pendapatan (ambang batas: %.0f%%)",
                        item.client().getName(), item.revenuePercentage(), rule.getThreshold()));
                return event;
            }
        }
        return null;
    }
}
