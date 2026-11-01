package com.artivisi.accountingfinance.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TagReportService {

    private final EntityManager entityManager;

    public List<TagTypeSummary> generateReport(LocalDate startDate, LocalDate endDate) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT tt.name AS tag_type_name, tg.name AS tag_name,
                       jt.category,
                       COALESCE(SUM(t.amount), 0) AS total_amount,
                       COUNT(DISTINCT t.id) AS transaction_count
                FROM transaction_tags txn_tag
                JOIN transactions t ON t.id = txn_tag.id_transaction
                JOIN tags tg ON tg.id = txn_tag.id_tag
                JOIN tag_types tt ON tt.id = tg.id_tag_type
                JOIN journal_templates jt ON jt.id = t.id_journal_template
                WHERE t.status = 'POSTED'
                AND t.transaction_date >= :startDate
                AND t.transaction_date <= :endDate
                AND tt.deleted_at IS NULL
                AND tg.deleted_at IS NULL
                GROUP BY tt.name, tg.name, jt.category
                ORDER BY tt.name, tg.name, jt.category
                """)
                .setParameter("startDate", startDate)
                .setParameter("endDate", endDate)
                .getResultList();

        // Aggregate into TagTypeSummary structure
        Map<String, Map<String, TagSummary>> grouped = new LinkedHashMap<>();

        for (Object[] row : rows) {
            String tagTypeName = (String) row[0];
            String tagName = (String) row[1];
            String category = (String) row[2];
            BigDecimal amount = (BigDecimal) row[3];
            long count = ((Number) row[4]).longValue();

            grouped
                .computeIfAbsent(tagTypeName, k -> new LinkedHashMap<>())
                .computeIfAbsent(tagName, k -> new TagSummary(tagName, BigDecimal.ZERO, BigDecimal.ZERO, 0))
                .accumulate(category, amount, count);
        }

        List<TagTypeSummary> result = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            List<TagSummary> tags = new ArrayList<>(entry.getValue().values());
            BigDecimal subtotalIncome = tags.stream().map(TagSummary::incomeAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal subtotalExpense = tags.stream().map(TagSummary::expenseAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            long subtotalCount = tags.stream().mapToLong(TagSummary::transactionCount).sum();
            result.add(new TagTypeSummary(entry.getKey(), tags, subtotalIncome, subtotalExpense, subtotalCount));
        }

        return result;
    }

    public record TagTypeSummary(
            String tagTypeName,
            List<TagSummary> tags,
            BigDecimal subtotalIncome,
            BigDecimal subtotalExpense,
            long subtotalTransactions
    ) {}

    public static class TagSummary {
        private final String tagName;
        private BigDecimal incomeAmount;
        private BigDecimal expenseAmount;
        private long transactionCount;

        public TagSummary(String tagName, BigDecimal incomeAmount, BigDecimal expenseAmount, long transactionCount) {
            this.tagName = tagName;
            this.incomeAmount = incomeAmount;
            this.expenseAmount = expenseAmount;
            this.transactionCount = transactionCount;
        }

        void accumulate(String category, BigDecimal amount, long count) {
            if ("INCOME".equals(category) || "RECEIPT".equals(category)) {
                this.incomeAmount = this.incomeAmount.add(amount);
            } else {
                this.expenseAmount = this.expenseAmount.add(amount);
            }
            this.transactionCount += count;
        }

        public String tagName() { return tagName; }
        public BigDecimal incomeAmount() { return incomeAmount; }
        public BigDecimal expenseAmount() { return expenseAmount; }
        public long transactionCount() { return transactionCount; }
        public BigDecimal netAmount() { return incomeAmount.subtract(expenseAmount); }
    }
}
