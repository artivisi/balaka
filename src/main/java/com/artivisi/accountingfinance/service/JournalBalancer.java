package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.JournalEntry;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Distributes sub-rupiah rounding residuals so debit == credit after per-line FLOOR rounding.
 *
 * <p>FormulaEvaluator floors each line to whole rupiah to match Indonesian tax practice
 * (BUG-001, commit e29ce49). When multi-line tax templates use division formulas
 * (e.g., {@code amount / 1.09} for DPP extraction), independent flooring produces
 * fractional losses that accumulate into a debit/credit imbalance of 1-2 rupiah.
 *
 * <p>This balancer absorbs that residual into the largest-magnitude line on the lighter
 * side, preserving exact tax amounts while keeping the journal balanced. If the residual
 * exceeds the line count, the imbalance is left alone for
 * {@code TransactionService#validateJournalBalance} to reject as a real template error.
 */
final class JournalBalancer {

    private JournalBalancer() {}

    static void absorbRoundingResidual(List<JournalEntry> entries) {
        if (entries == null || entries.isEmpty()) return;

        BigDecimal totalDebit = entries.stream()
                .map(JournalEntry::getDebitAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = entries.stream()
                .map(JournalEntry::getCreditAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal diff = totalDebit.subtract(totalCredit);
        if (diff.signum() == 0) return;

        if (diff.abs().compareTo(BigDecimal.valueOf(entries.size())) > 0) return;

        BigDecimal adjustment = diff.abs();
        if (diff.signum() > 0) {
            JournalEntry target = pickLargest(entries, JournalEntry::getCreditAmount);
            if (target != null) target.setCreditAmount(target.getCreditAmount().add(adjustment));
        } else {
            JournalEntry target = pickLargest(entries, JournalEntry::getDebitAmount);
            if (target != null) target.setDebitAmount(target.getDebitAmount().add(adjustment));
        }
    }

    static List<TemplateExecutionEngine.PreviewEntry> absorbPreviewResidual(
            List<TemplateExecutionEngine.PreviewEntry> entries) {
        if (entries == null || entries.isEmpty()) return entries;

        BigDecimal totalDebit = entries.stream()
                .map(TemplateExecutionEngine.PreviewEntry::debitAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = entries.stream()
                .map(TemplateExecutionEngine.PreviewEntry::creditAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal diff = totalDebit.subtract(totalCredit);
        if (diff.signum() == 0) return entries;

        if (diff.abs().compareTo(BigDecimal.valueOf(entries.size())) > 0) return entries;

        int targetIdx = diff.signum() > 0
                ? indexOfLargest(entries, TemplateExecutionEngine.PreviewEntry::creditAmount)
                : indexOfLargest(entries, TemplateExecutionEngine.PreviewEntry::debitAmount);
        if (targetIdx < 0) return entries;

        List<TemplateExecutionEngine.PreviewEntry> adjusted = new ArrayList<>(entries);
        TemplateExecutionEngine.PreviewEntry target = adjusted.get(targetIdx);
        BigDecimal adjustment = diff.abs();
        TemplateExecutionEngine.PreviewEntry replacement = diff.signum() > 0
                ? new TemplateExecutionEngine.PreviewEntry(
                        target.accountCode(), target.accountName(), target.description(),
                        target.debitAmount(), target.creditAmount().add(adjustment))
                : new TemplateExecutionEngine.PreviewEntry(
                        target.accountCode(), target.accountName(), target.description(),
                        target.debitAmount().add(adjustment), target.creditAmount());
        adjusted.set(targetIdx, replacement);
        return adjusted;
    }

    private static JournalEntry pickLargest(List<JournalEntry> entries,
                                             java.util.function.Function<JournalEntry, BigDecimal> amount) {
        JournalEntry best = null;
        for (JournalEntry entry : entries) {
            BigDecimal value = amount.apply(entry);
            if (value == null || value.signum() == 0) continue;
            if (best == null || amount.apply(best).compareTo(value) < 0) {
                best = entry;
            }
        }
        return best;
    }

    private static int indexOfLargest(List<TemplateExecutionEngine.PreviewEntry> entries,
                                       java.util.function.Function<TemplateExecutionEngine.PreviewEntry, BigDecimal> amount) {
        int bestIdx = -1;
        BigDecimal bestValue = null;
        for (int i = 0; i < entries.size(); i++) {
            BigDecimal value = amount.apply(entries.get(i));
            if (value == null || value.signum() == 0) continue;
            if (bestValue == null || bestValue.compareTo(value) < 0) {
                bestValue = value;
                bestIdx = i;
            }
        }
        return bestIdx;
    }
}
