package com.artivisi.accountingfinance.scheduler;

import com.artivisi.accountingfinance.entity.DepreciationEntry;
import com.artivisi.accountingfinance.service.AmortizationBatchService;
import com.artivisi.accountingfinance.service.FixedAssetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Scheduler for automated monthly journal entries.
 * Handles both amortization entries and fixed asset depreciation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MonthlyJournalScheduler {

    private static final String SYSTEM_USER = "SYSTEM";

    private final AmortizationBatchService amortizationBatchService;
    private final FixedAssetService fixedAssetService;

    /**
     * Run daily at 6:00 AM to process auto-post amortization entries.
     * Entries are processed if their period end date is <= today and
     * the schedule has auto_post = true.
     */
    @Scheduled(cron = "${app.amortization.schedule:0 0 6 * * *}")
    public void processAmortizationEntries() {
        log.info("Starting scheduled amortization batch processing");
        try {
            AmortizationBatchService.BatchResult result = amortizationBatchService.processAutoPostEntries(LocalDate.now());
            log.info("Scheduled amortization batch completed: {} processed, {} success, {} errors",
                    result.totalProcessed(), result.successCount(), result.errorCount());
        } catch (Exception e) {
            log.error("Scheduled amortization batch failed", e);
        }
    }

    /**
     * Run on the 1st day of each month at 7:00 AM to process depreciation entries
     * up to and including the previous month. Catch-up generation (#31): assets
     * registered after their purchase month's run get all missed periods
     * generated on the next run instead of permanently skipping them.
     */
    @Scheduled(cron = "${app.depreciation.schedule:0 0 7 1 * *}")
    public void processDepreciationEntries() {
        YearMonth previousMonth = YearMonth.now().minusMonths(1);
        log.info("Starting scheduled depreciation batch processing up to period: {}", previousMonth);

        try {
            // Step 1: Generate all owed depreciation entries (catch-up, idempotent)
            List<DepreciationEntry> generatedEntries = fixedAssetService.generateDepreciationCatchUp(previousMonth);

            if (generatedEntries.isEmpty()) {
                log.info("No assets need depreciation for period {}", previousMonth);
                return;
            }

            // Step 2: Post only auto-post-enabled assets' entries; others stay PENDING
            // for manual accounting review (mirrors AmortizationSchedule.autoPost).
            int postedCount = 0;
            int pendingCount = 0;
            int errorCount = 0;

            for (DepreciationEntry entry : generatedEntries) {
                if (!entry.getFixedAsset().isAutoPost()) {
                    pendingCount++;
                    continue;
                }
                if (tryPostDepreciationEntry(entry)) {
                    postedCount++;
                } else {
                    errorCount++;
                }
            }

            log.info("Scheduled depreciation batch completed: {} generated, {} posted, {} left PENDING, {} errors",
                    generatedEntries.size(), postedCount, pendingCount, errorCount);
        } catch (Exception e) {
            log.error("Scheduled depreciation batch failed", e);
        }
    }

    private boolean tryPostDepreciationEntry(DepreciationEntry entry) {
        try {
            fixedAssetService.postDepreciationEntry(entry.getId(), SYSTEM_USER);
            return true;
        } catch (Exception e) {
            log.error("Failed to post depreciation entry: asset={}, period={}, error={}",
                    entry.getFixedAsset().getAssetCode(), entry.getPeriodNumber(), e.getMessage());
            return false;
        }
    }
}
