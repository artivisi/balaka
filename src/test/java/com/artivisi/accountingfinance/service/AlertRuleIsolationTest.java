package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * evaluateAllAlerts runs each rule in its own transaction. Deliberately not @Transactional: the defect was the
 * whole run being rolled back at commit because one rule threw. Rule rows are restored and the events this test
 * created are deleted afterwards.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@DisplayName("Alert evaluation isolates a failing rule")
class AlertRuleIsolationTest {

    @Autowired private AlertService alertService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private List<Map<String, Object>> savedRules;
    private Timestamp start;

    @BeforeEach
    void isolateRules() {
        start = Timestamp.valueOf(LocalDateTime.now().minusSeconds(1));
        savedRules = jdbcTemplate.queryForList("SELECT id, threshold, enabled, last_triggered_at FROM alert_rules");
        jdbcTemplate.update("UPDATE alert_rules SET enabled = FALSE");
        // A threshold no cash balance reaches: CASH_LOW triggers on any data
        jdbcTemplate.update("UPDATE alert_rules SET enabled = TRUE, threshold = 999999999999999 WHERE alert_type = 'CASH_LOW'");
        // Not a whole number of months: CloseCheckService.configuredUnmovedMonths throws
        jdbcTemplate.update("UPDATE alert_rules SET enabled = TRUE, threshold = 1.5 WHERE alert_type = 'LIABILITY_UNMOVED'");
    }

    @AfterEach
    void restoreRules() {
        jdbcTemplate.update("DELETE FROM alert_events WHERE triggered_at >= ?", start);
        for (Map<String, Object> rule : savedRules) {
            jdbcTemplate.update("UPDATE alert_rules SET threshold = ?, enabled = ?, last_triggered_at = ? WHERE id = ?",
                    rule.get("threshold"), rule.get("enabled"), rule.get("last_triggered_at"), rule.get("id"));
        }
    }

    @Test
    @DisplayName("A misconfigured rule is skipped and logged; the triggered rule's event and timestamp commit")
    void failingRuleDoesNotRollBackOthers() {
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM alert_events e JOIN alert_rules r ON r.id = e.id_alert_rule "
                + "WHERE r.alert_type = 'CASH_LOW' AND e.acknowledged_at IS NULL AND e.triggered_at > ?", Long.class,
                Timestamp.valueOf(LocalDateTime.now().minusHours(24))))
                .as("no unacknowledged CASH_LOW event within 24h, otherwise the rule is deduplicated").isZero();

        assertThat(alertService.evaluateAllAlerts()).isEqualTo(1);

        assertThat(jdbcTemplate.queryForList("SELECT r.alert_type FROM alert_events e JOIN alert_rules r ON r.id = e.id_alert_rule "
                + "WHERE e.triggered_at >= ?", String.class, start)).containsExactly("CASH_LOW");
        assertThat(jdbcTemplate.queryForObject("SELECT last_triggered_at FROM alert_rules WHERE alert_type = 'CASH_LOW'",
                Timestamp.class)).isAfterOrEqualTo(start);
    }
}
