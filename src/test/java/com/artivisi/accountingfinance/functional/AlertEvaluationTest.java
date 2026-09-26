package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.entity.AlertEvent;
import com.artivisi.accountingfinance.entity.AlertRule;
import com.artivisi.accountingfinance.entity.Client;
import com.artivisi.accountingfinance.entity.Invoice;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.JournalTemplateLine;
import com.artivisi.accountingfinance.entity.Project;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.AlertSeverity;
import com.artivisi.accountingfinance.enums.AlertType;
import com.artivisi.accountingfinance.enums.InvoiceStatus;
import com.artivisi.accountingfinance.enums.ProjectStatus;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.AlertEventRepository;
import com.artivisi.accountingfinance.repository.AlertRuleRepository;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.ClientRepository;
import com.artivisi.accountingfinance.repository.InvoiceRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.ProjectRepository;
import com.artivisi.accountingfinance.service.AlertService;
import com.artivisi.accountingfinance.service.DashboardService;
import com.artivisi.accountingfinance.service.ProjectProfitabilityService;
import com.artivisi.accountingfinance.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Alert rule evaluation (AlertService.evaluateAllAlerts). Each test runs in a rolled-back
 * transaction, enables only the rule under test, measures the relevant baseline from the
 * database, adds its own postings/invoices/projects dated around today, and asserts the exact
 * event (message, severity, details) the rule produces. Project rules first mark every other
 * ACTIVE project COMPLETED inside the transaction so only the test's projects are evaluated.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, ServiceTestDataInitializer.class})
@ActiveProfiles("functional")
@DisplayName("Alert Evaluation Tests")
@Transactional
class AlertEvaluationTest {

    private static final String INCOME = "Pendapatan Jasa Training"; // BANK D / 4.1.01 C
    private static final String EXPENSE = "Bayar Beban Listrik";     // 5.1.07 D / BANK C

    @Autowired private AlertService alertService;
    @Autowired private AlertRuleRepository alertRuleRepository;
    @Autowired private AlertEventRepository alertEventRepository;
    @Autowired private DashboardService dashboardService;
    @Autowired private ProjectProfitabilityService profitabilityService;
    @Autowired private TransactionService transactionService;
    @Autowired private JournalTemplateRepository templateRepository;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("CASH_LOW triggers once a posting takes cash + bank below the threshold")
    void shouldEvaluateCashLow() {
        BigDecimal cash = cashBalance();
        assertThat(cash).as("baseline cash + bank").isGreaterThan(new BigDecimal("100000"));
        AlertRule rule = onlyRule(AlertType.CASH_LOW, cash);

        assertThat(alertService.evaluateAllAlerts()).as("cash equal to threshold").isZero();

        post(EXPENSE, LocalDate.now(), "100000", null);
        assertThat(alertService.evaluateAllAlerts()).isEqualTo(1);

        AlertEvent event = activeEvent(rule);
        assertThat(event.getSeverity()).isEqualTo(AlertSeverity.WARNING);
        assertThat(event.getMessage()).isEqualTo(String.format("Saldo kas + bank: Rp %,.0f (ambang batas: Rp %,.0f)",
                cash.subtract(new BigDecimal("100000")), cash));
        assertThat(alertRuleRepository.findById(rule.getId()).orElseThrow().getLastTriggeredAt()).isNotNull();
    }

    @Test
    @DisplayName("RECEIVABLE_OVERDUE triggers when a new overdue invoice pushes the overdue total above the threshold")
    void shouldEvaluateReceivableOverdue() {
        List<Invoice> overdue = invoiceRepository.findOverdueInvoices(LocalDate.now());
        BigDecimal overdueTotal = overdue.stream().map(Invoice::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        AlertRule rule = onlyRule(AlertType.RECEIVABLE_OVERDUE, overdueTotal);

        assertThat(alertService.evaluateAllAlerts()).as("overdue total equal to threshold").isZero();

        Client client = client();
        invoice(client, "2500000", InvoiceStatus.SENT, LocalDate.now().minusDays(1), null, null);
        assertThat(alertService.evaluateAllAlerts()).isEqualTo(1);

        int count = overdue.size() + 1;
        AlertEvent event = activeEvent(rule);
        assertThat(event.getSeverity()).isEqualTo(count > 5 ? AlertSeverity.CRITICAL : AlertSeverity.WARNING);
        assertThat(event.getMessage()).isEqualTo(String.format("%d piutang jatuh tempo, total: Rp %,.0f",
                count, overdueTotal.add(new BigDecimal("2500000"))));
    }

    @Test
    @DisplayName("EXPENSE_SPIKE compares this month's expense with the average of the previous three months")
    void shouldEvaluateExpenseSpike() {
        YearMonth month = YearMonth.now();
        BigDecimal current = dashboardService.calculateKPIs(month).expense().add(new BigDecimal("10000000"));
        BigDecimal previous = BigDecimal.ZERO;
        for (int i = 1; i <= 3; i++) {
            previous = previous.add(dashboardService.calculateKPIs(month.minusMonths(i)).expense())
                    .add(new BigDecimal("1000000"));
            post(EXPENSE, month.minusMonths(i).atDay(15), "1000000", null);
        }
        post(EXPENSE, LocalDate.now(), "10000000", null);
        BigDecimal average = previous.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
        BigDecimal increase = current.subtract(average).multiply(BigDecimal.valueOf(100))
                .divide(average, 2, RoundingMode.HALF_UP);
        assertThat(increase).as("spike created by the test").isGreaterThan(new BigDecimal("50"));

        AlertRule rule = onlyRule(AlertType.EXPENSE_SPIKE, increase);
        assertThat(alertService.evaluateAllAlerts()).as("increase equal to threshold").isZero();

        rule.setThreshold(new BigDecimal("50"));
        assertThat(alertService.evaluateAllAlerts()).isEqualTo(1);

        AlertEvent event = activeEvent(rule);
        assertThat(event.getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
        assertThat(event.getMessage()).isEqualTo(String.format(
                "Biaya bulan ini naik %.1f%% dari rata-rata 3 bulan sebelumnya (Rp %,.0f vs Rp %,.0f)",
                increase, current, average));
    }

    @Test
    @DisplayName("PROJECT_COST_OVERRUN counts active projects that spent far more of their budget than their progress")
    void shouldEvaluateProjectCostOverrun() {
        isolateProjects();
        Client client = client();
        Project overrun = project(client, "10000000");
        post(EXPENSE, LocalDate.now(), "9000000", overrun);
        Project onBudget = project(client, "10000000");
        post(EXPENSE, LocalDate.now(), "500000", onBudget);
        AlertRule rule = onlyRule(AlertType.PROJECT_COST_OVERRUN, BigDecimal.ZERO);

        assertThat(alertService.evaluateAllAlerts()).isEqualTo(1);

        AlertEvent event = activeEvent(rule);
        assertThat(event.getSeverity()).isEqualTo(AlertSeverity.WARNING);
        assertThat(event.getMessage()).isEqualTo("1 proyek berisiko melebihi anggaran");
        assertThat(event.getDetails()).isEqualTo(String.format("%s (spent %.0f%% of budget at %d%% progress), ",
                overrun.getName(), new BigDecimal("90.00"), 0));
    }

    @Test
    @DisplayName("PROJECT_MARGIN_DROP counts projects below the margin threshold, critical when one is negative")
    void shouldEvaluateProjectMarginDrop() {
        isolateProjects();
        Client client = client();
        Project thin = project(client, "20000000");
        post(INCOME, LocalDate.now(), "10000000", thin);
        post(EXPENSE, LocalDate.now(), "9000000", thin);
        Project healthy = project(client, "20000000");
        post(INCOME, LocalDate.now(), "10000000", healthy);
        post(EXPENSE, LocalDate.now(), "2000000", healthy);
        AlertRule rule = onlyRule(AlertType.PROJECT_MARGIN_DROP, new BigDecimal("20"));

        assertThat(alertService.evaluateAllAlerts()).isEqualTo(1);
        AlertEvent warning = activeEvent(rule);
        assertThat(warning.getSeverity()).isEqualTo(AlertSeverity.WARNING);
        assertThat(warning.getMessage()).isEqualTo("1 proyek dengan margin di bawah 20%");
        assertThat(warning.getDetails()).isEqualTo(margin(thin, "10.00"));

        alertService.acknowledge(warning.getId(), "test");
        Project loss = project(client, "20000000");
        post(INCOME, LocalDate.now(), "1000000", loss);
        post(EXPENSE, LocalDate.now(), "2000000", loss);

        assertThat(alertService.evaluateAllAlerts()).isEqualTo(1);
        AlertEvent critical = activeEvent(rule);
        assertThat(critical.getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
        assertThat(critical.getMessage()).isEqualTo("2 proyek dengan margin di bawah 20%");
        assertThat(critical.getDetails()).contains(margin(thin, "10.00")).contains(margin(loss, "-100.00"))
                .hasSize((margin(thin, "10.00") + margin(loss, "-100.00")).length());
    }

    @Test
    @DisplayName("COLLECTION_SLOWDOWN averages send-to-payment days of invoices paid in the last six months")
    void shouldEvaluateCollectionSlowdown() {
        LocalDateTime sixMonthsAgo = LocalDateTime.now().minusMonths(6);
        List<Invoice> paid = invoiceRepository.findByStatus(InvoiceStatus.PAID).stream()
                .filter(i -> i.getPaidAt() != null && i.getSentAt() != null && i.getPaidAt().isAfter(sixMonthsAgo))
                .toList();
        long days = paid.stream().mapToLong(i -> ChronoUnit.DAYS.between(i.getSentAt(), i.getPaidAt())).sum();

        LocalDateTime paidAt = LocalDateTime.now().minusDays(1);
        invoice(client(), "1000000", InvoiceStatus.PAID, LocalDate.now().minusDays(70), paidAt.minusDays(120), paidAt);
        double average = (double) (days + 120) / (paid.size() + 1);
        AlertRule rule = onlyRule(AlertType.COLLECTION_SLOWDOWN, BigDecimal.valueOf(average));

        assertThat(alertService.evaluateAllAlerts()).as("average not above threshold").isZero();

        BigDecimal threshold = BigDecimal.valueOf(average).subtract(BigDecimal.ONE);
        rule.setThreshold(threshold);
        assertThat(alertService.evaluateAllAlerts()).isEqualTo(1);
        AlertEvent event = activeEvent(rule);
        assertThat(event.getSeverity()).isEqualTo(AlertSeverity.WARNING);
        assertThat(event.getMessage()).isEqualTo(String.format("Rata-rata penagihan: %.0f hari (ambang batas: %.0f hari)",
                average, threshold));
    }

    @Test
    @DisplayName("CLIENT_CONCENTRATION names the client whose share of 12-month project revenue exceeds the threshold")
    void shouldEvaluateClientConcentration() {
        LocalDate today = LocalDate.now();
        BigDecimal others = profitabilityService.getClientRanking(today.minusMonths(12), today, 0).stream()
                .map(ProjectProfitabilityService.ClientRankingItem::totalRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Client client = client();
        post(INCOME, today, "1000000000", project(client, "1000000000"));
        BigDecimal share = new BigDecimal("1000000000").multiply(BigDecimal.valueOf(100))
                .divide(others.add(new BigDecimal("1000000000")), 2, RoundingMode.HALF_UP);
        assertThat(share).as("client share created by the test").isGreaterThan(new BigDecimal("50"));
        AlertRule rule = onlyRule(AlertType.CLIENT_CONCENTRATION, share);

        assertThat(alertService.evaluateAllAlerts()).as("share equal to threshold").isZero();

        rule.setThreshold(new BigDecimal("50"));
        assertThat(alertService.evaluateAllAlerts()).isEqualTo(1);
        AlertEvent event = activeEvent(rule);
        assertThat(event.getSeverity()).isEqualTo(AlertSeverity.WARNING);
        assertThat(event.getMessage()).isEqualTo(String.format(
                "Klien %s menyumbang %.1f%% pendapatan (ambang batas: %.0f%%)", client.getName(), share, new BigDecimal("50")));
    }

    @Test
    @DisplayName("An unacknowledged event from the last 24 hours suppresses the rule; acknowledged or older events do not")
    void shouldNotCreateDuplicateAlerts() {
        AlertRule rule = onlyRule(AlertType.CASH_LOW, cashBalance().add(BigDecimal.ONE));

        assertThat(alertService.evaluateAllAlerts()).isEqualTo(1);
        AlertEvent first = activeEvent(rule);

        assertThat(alertService.evaluateAllAlerts()).as("within 24h, unacknowledged").isZero();
        assertThat(activeEvents(rule)).containsExactly(first);

        first.setTriggeredAt(LocalDateTime.now().minusHours(25));
        alertEventRepository.saveAndFlush(first);
        assertThat(alertService.evaluateAllAlerts()).as("older than 24h").isEqualTo(1);
        List<AlertEvent> twoActive = activeEvents(rule);
        assertThat(twoActive).hasSize(2);

        twoActive.forEach(e -> alertService.acknowledge(e.getId(), "test"));
        assertThat(alertService.evaluateAllAlerts()).as("acknowledged").isEqualTo(1);
        assertThat(activeEvents(rule)).hasSize(1);
    }

    @Test
    @DisplayName("Acknowledging stores who and when and removes the event from active alerts")
    void shouldAcknowledgeAlertEvent() {
        AlertRule rule = onlyRule(AlertType.CASH_LOW, cashBalance().add(BigDecimal.ONE));
        alertService.evaluateAllAlerts();
        AlertEvent event = activeEvent(rule);
        long activeBefore = alertService.countActiveAlerts();

        alertService.acknowledge(event.getId(), "test-admin");

        AlertEvent acknowledged = alertEventRepository.findById(event.getId()).orElseThrow();
        assertThat(acknowledged.getAcknowledgedBy()).isEqualTo("test-admin");
        assertThat(acknowledged.getAcknowledgedAt()).isBetween(LocalDateTime.now().minusMinutes(1), LocalDateTime.now());
        assertThat(alertService.findActiveAlerts()).doesNotContain(acknowledged);
        assertThat(alertService.countActiveAlerts()).isEqualTo(activeBefore - 1);
    }

    @Test
    @DisplayName("History filters by type, severity and acknowledgement")
    void shouldQueryAlertHistoryWithFilters() {
        AlertRule rule = onlyRule(AlertType.CASH_LOW, cashBalance().add(BigDecimal.ONE));
        alertService.evaluateAllAlerts();
        AlertEvent open = activeEvent(rule);
        open.setTriggeredAt(LocalDateTime.now().minusHours(25));
        alertEventRepository.saveAndFlush(open);
        alertService.evaluateAllAlerts();
        AlertEvent acked = activeEvents(rule).stream().filter(e -> !e.getId().equals(open.getId())).findFirst().orElseThrow();
        alertService.acknowledge(acked.getId(), "test");

        List<AlertEvent> cashLow = alertService.findAlertHistory(AlertType.CASH_LOW, null, null, PageRequest.of(0, 500)).getContent();
        assertThat(cashLow).contains(open, acked).allMatch(e -> e.getAlertRule().getAlertType() == AlertType.CASH_LOW);
        assertThat(alertService.findAlertHistory(AlertType.EXPENSE_SPIKE, null, null, PageRequest.of(0, 500)).getContent())
                .doesNotContain(open, acked);
        assertThat(alertService.findAlertHistory(null, AlertSeverity.WARNING, null, PageRequest.of(0, 500)).getContent())
                .contains(open, acked).allMatch(e -> e.getSeverity() == AlertSeverity.WARNING);
        assertThat(alertService.findAlertHistory(null, AlertSeverity.CRITICAL, null, PageRequest.of(0, 500)).getContent())
                .doesNotContain(open, acked);
        assertThat(alertService.findAlertHistory(AlertType.CASH_LOW, null, true, PageRequest.of(0, 500)).getContent())
                .contains(acked).doesNotContain(open).allMatch(e -> e.getAcknowledgedAt() != null);
        assertThat(alertService.findAlertHistory(AlertType.CASH_LOW, null, false, PageRequest.of(0, 500)).getContent())
                .contains(open).doesNotContain(acked).allMatch(e -> e.getAcknowledgedAt() == null);
    }

    @Test
    @DisplayName("Updating a rule stores the new threshold and enabled flag")
    void shouldUpdateRuleThresholdAndEnabled() {
        AlertRule rule = alertRuleRepository.findByAlertType(AlertType.CASH_LOW)
                .orElseThrow(() -> new IllegalStateException("Seeded alert rule missing: CASH_LOW"));

        alertService.updateRule(rule.getId(), new BigDecimal("5000000"), false);

        AlertRule stored = alertService.findRuleById(rule.getId()).orElseThrow();
        assertThat(stored.getAlertType()).isEqualTo(AlertType.CASH_LOW);
        assertThat(stored.getThreshold()).isEqualByComparingTo("5000000");
        assertThat(stored.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("Disabled rules are not evaluated even when their condition holds")
    void shouldRespectDisabledRules() {
        AlertRule rule = onlyRule(AlertType.CASH_LOW, cashBalance().add(BigDecimal.ONE));
        rule.setEnabled(false);
        alertRuleRepository.saveAndFlush(rule);

        assertThat(alertService.evaluateAllAlerts()).isZero();
        assertThat(activeEvents(rule)).isEmpty();
    }

    // ==================== helpers ====================

    /** Enables only the given rule with the threshold and acknowledges its open events. */
    private AlertRule onlyRule(AlertType type, BigDecimal threshold) {
        for (AlertRule r : alertRuleRepository.findAll()) {
            r.setEnabled(r.getAlertType() == type);
            alertRuleRepository.save(r);
        }
        AlertRule rule = alertRuleRepository.findByAlertType(type)
                .orElseThrow(() -> new IllegalStateException("Seeded alert rule missing: " + type));
        rule.setThreshold(threshold);
        alertRuleRepository.saveAndFlush(rule);
        activeEvents(rule).forEach(e -> alertService.acknowledge(e.getId(), "test-baseline"));
        return rule;
    }

    /** Detail fragment as AlertService formats it (JVM default locale, like the message). */
    private static String margin(Project project, String percent) {
        return String.format("%s (margin: %.1f%%), ", project.getName(), new BigDecimal(percent));
    }

    private List<AlertEvent> activeEvents(AlertRule rule) {
        return alertEventRepository.findByAcknowledgedAtIsNullOrderByTriggeredAtDesc().stream()
                .filter(e -> e.getAlertRule().getId().equals(rule.getId()))
                .toList();
    }

    private AlertEvent activeEvent(AlertRule rule) {
        List<AlertEvent> events = activeEvents(rule);
        assertThat(events).as("active events for %s", rule.getAlertType()).hasSize(1);
        return events.getFirst();
    }

    private BigDecimal cashBalance() {
        return dashboardService.calculateKPIs(YearMonth.now()).cashBalance();
    }

    private void isolateProjects() {
        jdbc.update("UPDATE projects SET status = 'COMPLETED' WHERE status = 'ACTIVE'");
    }

    private Client client() {
        String id = UUID.randomUUID().toString().substring(0, 8);
        Client client = new Client();
        client.setCode("ALR-" + id);
        client.setName("Klien Alert " + id);
        return clientRepository.saveAndFlush(client);
    }

    private Project project(Client client, String budget) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        Project project = new Project();
        project.setCode("ALR-" + id);
        project.setName("Proyek Alert " + id);
        project.setClient(client);
        project.setStatus(ProjectStatus.ACTIVE);
        project.setBudgetAmount(new BigDecimal(budget));
        project.setContractValue(new BigDecimal(budget));
        return projectRepository.saveAndFlush(project);
    }

    private void invoice(Client client, String amount, InvoiceStatus status, LocalDate dueDate,
                         LocalDateTime sentAt, LocalDateTime paidAt) {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("INV-ALR-" + UUID.randomUUID().toString().substring(0, 8));
        invoice.setClient(client);
        invoice.setInvoiceDate(dueDate.minusDays(30));
        invoice.setDueDate(dueDate);
        invoice.setAmount(new BigDecimal(amount));
        invoice.setStatus(status);
        invoice.setSentAt(sentAt);
        invoice.setPaidAt(paidAt);
        invoiceRepository.saveAndFlush(invoice);
    }

    private void post(String templateName, LocalDate date, String amount, Project project) {
        JournalTemplate template = templateRepository.findByTemplateNameWithLines(templateName)
                .orElseThrow(() -> new IllegalStateException("Seeded template missing: " + templateName));
        JournalTemplateLine bankLine = template.getLines().stream()
                .filter(l -> "BANK".equals(l.getAccountHint())).findFirst()
                .orElseThrow(() -> new IllegalStateException("Template has no BANK line: " + templateName));
        Transaction transaction = new Transaction();
        transaction.setJournalTemplate(template);
        transaction.setTransactionDate(date);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setDescription(templateName + " alert test");
        transaction.setProject(project);
        Transaction created = transactionService.create(transaction, Map.of(bankLine.getId(),
                accountRepository.findByAccountCode("1.1.02")
                        .orElseThrow(() -> new IllegalStateException("Seeded account missing: 1.1.02")).getId()));
        transactionService.post(created.getId(), "admin");
    }
}
