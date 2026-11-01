package com.artivisi.accountingfinance.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class WeeklyFinancialReportService {

    private final EntityManager entityManager;

    public String generateReport() {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(6);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("id"));

        BigDecimal expense = queryDecimal(
                "SELECT COALESCE(SUM(je.debit_amount),0) FROM journal_entries je " +
                "JOIN chart_of_accounts coa ON coa.id=je.id_account " +
                "JOIN transactions t ON t.id=je.id_transaction " +
                "WHERE coa.account_type='EXPENSE' AND t.status='POSTED' " +
                "AND t.transaction_date BETWEEN :start AND :end",
                start, end);

        BigDecimal cashIn = queryDecimal(
                "SELECT COALESCE(SUM(je.debit_amount),0) FROM journal_entries je " +
                "JOIN chart_of_accounts coa ON coa.id=je.id_account " +
                "JOIN transactions t ON t.id=je.id_transaction " +
                "WHERE coa.is_cash_bank=true AND t.status='POSTED' " +
                "AND t.transaction_date BETWEEN :start AND :end",
                start, end);

        BigDecimal cashOut = queryDecimal(
                "SELECT COALESCE(SUM(je.credit_amount),0) FROM journal_entries je " +
                "JOIN chart_of_accounts coa ON coa.id=je.id_account " +
                "JOIN transactions t ON t.id=je.id_transaction " +
                "WHERE coa.is_cash_bank=true AND t.status='POSTED' " +
                "AND t.transaction_date BETWEEN :start AND :end",
                start, end);

        BigDecimal initialBalance = queryDecimal(
                "SELECT COALESCE(SUM(je.debit_amount),0) FROM journal_entries je " +
                "JOIN chart_of_accounts coa ON coa.id=je.id_account " +
                "JOIN transactions t ON t.id=je.id_transaction " +
                "WHERE coa.is_cash_bank=true AND t.status='POSTED' AND t.deleted_at IS NULL",
                null, null);

        BigDecimal totalCashOutAllTime = queryDecimal(
                "SELECT COALESCE(SUM(je.credit_amount),0) FROM journal_entries je " +
                "JOIN chart_of_accounts coa ON coa.id=je.id_account " +
                "JOIN transactions t ON t.id=je.id_transaction " +
                "WHERE coa.is_cash_bank=true AND t.status='POSTED' AND t.deleted_at IS NULL",
                null, null);

        BigDecimal balance = initialBalance.subtract(totalCashOutAllTime);
        BigDecimal revenue = BigDecimal.ZERO; // no operational revenue accounts tracked separately

        List<Object[]> txRows = entityManager.createNativeQuery(
                "SELECT transaction_number, transaction_date, description, amount " +
                "FROM transactions WHERE status='POSTED' " +
                "AND transaction_date BETWEEN :start AND :end " +
                "ORDER BY transaction_date, transaction_number")
                .setParameter("start", start)
                .setParameter("end", end)
                .getResultList();

        StringBuilder sb = new StringBuilder();
        sb.append("Laporan Keuangan Mingguan\n");
        sb.append("Periode: ").append(start.format(fmt)).append(" - ").append(end.format(fmt)).append("\n\n");

        sb.append("Arus Kas:\n");
        sb.append("- Kas masuk: Rp").append(fmtRp(cashIn)).append("\n");
        sb.append("- Kas keluar: Rp").append(fmtRp(cashOut)).append("\n");
        sb.append("- Perubahan kas: -Rp").append(fmtRp(cashOut.subtract(cashIn))).append("\n\n");

        sb.append("Laba/Rugi (akrual):\n");
        sb.append("- Pemasukan operasional: Rp").append(fmtRp(revenue)).append("\n");
        sb.append("- Beban: Rp").append(fmtRp(expense)).append("\n");
        sb.append("- Laba/Rugi: Rp").append(fmtRp(revenue.subtract(expense))).append("\n\n");

        sb.append("Saldo Kas (BRI Simpedes):\n");
        sb.append("- Saldo awal (total debit): Rp").append(fmtRp(initialBalance)).append("\n");
        sb.append("- Total kas keluar: Rp").append(fmtRp(totalCashOutAllTime)).append("\n");
        sb.append("- Saldo akhir: Rp").append(fmtRp(balance)).append("\n\n");

        sb.append("Jumlah transaksi: ").append(txRows.size()).append("\n\n");

        sb.append("Catatan:\n");
        sb.append("- Kas keluar termasuk pembelian, gaji, pinjaman, pelunasan hutang, dll.\n");
        sb.append("- Beban ").append(fmtRp(expense)).append(" adalah pengeluaran yang diakui sebagai beban.\n");
        sb.append("- Tidak ada kas masuk karena belum ada pendapatan.\n\n");

        sb.append("Daftar Transaksi (").append(txRows.size()).append("):\n");
        int i = 1;
        for (Object[] row : txRows) {
            String num = String.valueOf(row[0]);
            LocalDate date = (row[1] instanceof LocalDate) ? (LocalDate) row[1] : LocalDate.parse(String.valueOf(row[1]));
            String desc = String.valueOf(row[2]);
            BigDecimal amt = new BigDecimal(String.valueOf(row[3]));
            sb.append(i++).append(". ").append(num).append(" (").append(date.format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("id")))).append(") ")
                    .append(desc).append(" - Rp").append(fmtRp(amt)).append("\n");
        }

        sb.append("\nSumber: https://fasternet-finance.qansa.web.id/\n");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private BigDecimal queryDecimal(String sql, LocalDate start, LocalDate end) {
        var q = entityManager.createNativeQuery(sql);
        if (start != null) q.setParameter("start", start);
        if (end != null) q.setParameter("end", end);
        Object result = q.getSingleResult();
        if (result == null) return BigDecimal.ZERO;
        if (result instanceof BigDecimal) return (BigDecimal) result;
        if (result instanceof Number) return new BigDecimal(result.toString());
        return new BigDecimal(String.valueOf(result));
    }

    private String fmtRp(BigDecimal value) {
        if (value == null) return "0";
        return String.format("%,.0f", value).replace(",", ".");
    }
}
