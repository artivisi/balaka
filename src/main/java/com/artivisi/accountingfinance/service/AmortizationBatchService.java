package com.artivisi.accountingfinance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AmortizationBatchService {

    private final AmortizationEntryService entryService;
    private final PlatformTransactionManager transactionManager;

    /**
     * Each entry posts in its own transaction, so one failing entry does not roll back the others.
     * Called inside an existing transaction (tests), the entries join it.
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    public BatchResult processAutoPostEntries(LocalDate asOfDate) {
        log.info("Processing auto-post amortization entries for date: {}", asOfDate);

        TransactionTemplate perEntry = new TransactionTemplate(transactionManager);
        List<EntryRef> pendingEntries = perEntry.execute(status ->
                entryService.findPendingAutoPostEntriesDueByDate(asOfDate).stream()
                        .map(e -> new EntryRef(e.getId(), e.getSchedule().getCode(), e.getPeriodNumber()))
                        .toList());

        int successCount = 0;
        int errorCount = 0;

        for (EntryRef entry : pendingEntries) {
            try {
                perEntry.executeWithoutResult(status -> entryService.postEntry(entry.id()));
                successCount++;
                log.info("Posted amortization entry: schedule={}, period={}", entry.scheduleCode(), entry.periodNumber());
            } catch (RuntimeException e) {
                errorCount++;
                log.error("Failed to post amortization entry: schedule={}, period={}, error={}",
                        entry.scheduleCode(), entry.periodNumber(), e.getMessage());
            }
        }

        log.info("Batch processing complete: {} success, {} errors", successCount, errorCount);
        return new BatchResult(pendingEntries.size(), successCount, errorCount);
    }

    private record EntryRef(UUID id, String scheduleCode, Integer periodNumber) {}

    public record BatchResult(int totalProcessed, int successCount, int errorCount) {
        public boolean hasErrors() {
            return errorCount > 0;
        }
    }
}
