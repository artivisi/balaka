package com.artivisi.accountingfinance.scheduler;

import com.artivisi.accountingfinance.service.AmortizationBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class AmortizationScheduler {

    private final AmortizationBatchService batchService;

    /**
     * Run daily at 6:00 AM to process auto-post amortization entries.
     * Entries are processed if their period end date is <= today and
     * the schedule has auto_post = true.
     */
    @Scheduled(cron = "${app.amortization.schedule:0 0 6 * * *}")
    public void processAutoPostEntries() {
        log.info("Starting scheduled amortization batch processing");
        try {
            AmortizationBatchService.BatchResult result = batchService.processAutoPostEntries(LocalDate.now());
            log.info("Scheduled amortization batch completed: {} processed, {} success, {} errors",
                    result.totalProcessed(), result.successCount(), result.errorCount());
        } catch (Exception e) {
            log.error("Scheduled amortization batch failed", e);
        }
    }
}
