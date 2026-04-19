package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.JournalEntry;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Distributes sub-rupiah rounding residuals so debit == credit after per-line HALF_UP rounding.
 *
 * <p>FormulaEvaluator rounds each line to whole rupiah per PER-11/PJ/2025 Pasal 129 (HALF_UP).
 * When multi-line tax templates use division formulas (e.g., {@code amount / 1.09} for DPP
 * extraction), independent rounding of each line still produces a 1-rupiah debit/credit
 * imbalance on certain amounts regardless of rounding mode.
 *
 * <p>The absorber preserves the <em>input</em> line (formula {@code amount}) because it
 * represents the user-entered value — typically a bank receipt that must match an external
 * statement. It then picks the largest-magnitude derived line on the side that balances the
 * residual, adding to the lighter side or subtracting from the heavier side, whichever
 * keeps the input line untouched. If the residual exceeds the line count, the imbalance is
 * left alone so {@code TransactionService#validateJournalBalance} can reject it as a real
 * template error.
 */
final class JournalBalancer {

    private static final String INPUT_FORMULA = "amount";

    private JournalBalancer() {}

    static void absorbRoundingResidual(List<JournalEntry> entries, List<String> formulas) {
        if (entries == null || entries.isEmpty()) return;
        if (formulas == null || formulas.size() != entries.size()) {
            throw new IllegalArgumentException("formulas list must parallel entries list");
        }

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
        // diff > 0 → debit heavier. Prefer reducing debit on the heavier side, OR increasing
        // the lighter credit side. Skip input lines so user's entered amount stays exact.
        int targetIdx = diff.signum() > 0
                ? pickAbsorberForCreditIncrease(entries, formulas)
                : pickAbsorberForDebitIncrease(entries, formulas);

        if (targetIdx < 0) return;

        JournalEntry target = entries.get(targetIdx);
        if (diff.signum() > 0) {
            target.setCreditAmount(target.getCreditAmount().add(adjustment));
        } else {
            target.setDebitAmount(target.getDebitAmount().add(adjustment));
        }
    }

    static List<TemplateExecutionEngine.PreviewEntry> absorbPreviewResidual(
            List<TemplateExecutionEngine.PreviewEntry> entries, List<String> formulas) {
        if (entries == null || entries.isEmpty()) return entries;
        if (formulas == null || formulas.size() != entries.size()) {
            throw new IllegalArgumentException("formulas list must parallel entries list");
        }

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
                ? pickPreviewAbsorber(entries, formulas, true)
                : pickPreviewAbsorber(entries, formulas, false);
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

    private static int pickAbsorberForCreditIncrease(List<JournalEntry> entries, List<String> formulas) {
        int bestIdx = -1;
        BigDecimal bestValue = null;
        for (int i = 0; i < entries.size(); i++) {
            if (isInputFormula(formulas.get(i))) continue;
            BigDecimal value = entries.get(i).getCreditAmount();
            if (value == null || value.signum() == 0) continue;
            if (bestValue == null || bestValue.compareTo(value) < 0) {
                bestValue = value;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    private static int pickAbsorberForDebitIncrease(List<JournalEntry> entries, List<String> formulas) {
        int bestIdx = -1;
        BigDecimal bestValue = null;
        for (int i = 0; i < entries.size(); i++) {
            if (isInputFormula(formulas.get(i))) continue;
            BigDecimal value = entries.get(i).getDebitAmount();
            if (value == null || value.signum() == 0) continue;
            if (bestValue == null || bestValue.compareTo(value) < 0) {
                bestValue = value;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    private static int pickPreviewAbsorber(List<TemplateExecutionEngine.PreviewEntry> entries,
                                            List<String> formulas, boolean creditSide) {
        int bestIdx = -1;
        BigDecimal bestValue = null;
        for (int i = 0; i < entries.size(); i++) {
            if (isInputFormula(formulas.get(i))) continue;
            BigDecimal value = creditSide ? entries.get(i).creditAmount() : entries.get(i).debitAmount();
            if (value == null || value.signum() == 0) continue;
            if (bestValue == null || bestValue.compareTo(value) < 0) {
                bestValue = value;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    private static boolean isInputFormula(String formula) {
        if (formula == null) return true;
        return INPUT_FORMULA.equalsIgnoreCase(formula.trim());
    }
}
