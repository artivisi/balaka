package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.AmortizationEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AmortizationBatchService {

    private final AmortizationEntryService entryService;

    @Transactional
    public BatchResult processAutoPostEntries(LocalDate asOfDate) {
        log.info("Processing auto-post amortization entries for date: {}", asOfDate);

        List<AmortizationEntry> pendingEntries = entryService.findPendingAutoPostEntriesDueByDate(asOfDate);

        int successCount = 0;
        int errorCount = 0;

        for (AmortizationEntry entry : pendingEntries) {
            try {
                entryService.postEntry(entry.getId());
                successCount++;
                log.info("Posted amortization entry: schedule={}, period={}",
                        entry.getSchedule().getCode(), entry.getPeriodNumber());
            } catch (Exception e) {
                errorCount++;
                log.error("Failed to post amortization entry: schedule={}, period={}, error={}",
                        entry.getSchedule().getCode(), entry.getPeriodNumber(), e.getMessage());
            }
        }

        log.info("Batch processing complete: {} success, {} errors", successCount, errorCount);
        return new BatchResult(pendingEntries.size(), successCount, errorCount);
    }

    public record BatchResult(int totalProcessed, int successCount, int errorCount) {
        public boolean hasErrors() {
            return errorCount > 0;
        }
    }
}
