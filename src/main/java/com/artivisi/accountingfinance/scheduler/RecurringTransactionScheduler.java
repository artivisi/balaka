package com.artivisi.accountingfinance.scheduler;

import com.artivisi.accountingfinance.service.RecurringTransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RecurringTransactionScheduler {

    private final RecurringTransactionService recurringTransactionService;

    @Scheduled(cron = "${app.recurring.schedule:0 0 5 * * *}")
    public void processRecurringTransactions() {
        log.info("Starting scheduled recurring transaction processing");
        try {
            int processed = recurringTransactionService.processAllDue();
            log.info("Recurring transaction processing completed: {} transactions processed", processed);
        } catch (Exception e) {
            log.error("Scheduled recurring transaction processing failed", e);
        }
    }
}
