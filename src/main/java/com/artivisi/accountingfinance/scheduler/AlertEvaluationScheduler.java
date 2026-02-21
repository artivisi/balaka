package com.artivisi.accountingfinance.scheduler;

import com.artivisi.accountingfinance.service.AlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AlertEvaluationScheduler {

    private final AlertService alertService;

    @Scheduled(cron = "${app.alerts.schedule:0 0 8 * * *}")
    public void evaluateAlerts() {
        log.info("Starting scheduled alert evaluation");
        try {
            int triggered = alertService.evaluateAllAlerts();
            log.info("Scheduled alert evaluation completed: {} alerts triggered", triggered);
        } catch (Exception e) {
            log.error("Scheduled alert evaluation failed", e);
        }
    }
}
