# TODO: Basic Reports (1.3)

Validate journal entries and provide financial output. Item is only checked when verified by Playwright functional test.

## Purpose

- Trial Balance is the ultimate test of double-entry correctness
- Provide financial statements for business decisions
- Reused by account balance display and validation checks

## Dependencies

- COA (1.1) ✅ Complete
- Journal Entries (1.2) ✅ Complete

---

## TODO List

### 1. Trial Balance Report

- [ ] TrialBalanceService.calculateTrialBalance(asOfDate)
- [ ] Calculate debit/credit totals per account
- [ ] Show only accounts with activity
- [ ] Trial Balance UI page (`/reports/trial-balance`)
- [ ] Date selector (as of date)
- [ ] Verify debit total = credit total

### 2. General Ledger Report

- [ ] GeneralLedgerService already exists (reuse JournalEntryService.getGeneralLedger)
- [ ] General Ledger UI page (`/reports/general-ledger`)
- [ ] Account selector dropdown
- [ ] Date range filter
- [ ] Opening balance, transactions, closing balance
- [ ] Running balance column

### 3. Balance Sheet (Laporan Posisi Keuangan)

- [ ] BalanceSheetService.generateBalanceSheet(asOfDate)
- [ ] Group accounts by type: Asset, Liability, Equity
- [ ] Calculate subtotals per account type
- [ ] Verify Assets = Liabilities + Equity
- [ ] Balance Sheet UI page (`/reports/balance-sheet`)
- [ ] Date selector (as of date)
- [ ] Hierarchical account display

### 4. Income Statement (Laporan Laba Rugi)

- [ ] IncomeStatementService.generateIncomeStatement(startDate, endDate)
- [ ] Group accounts by type: Revenue, Expense
- [ ] Calculate Net Income = Revenue - Expense
- [ ] Income Statement UI page (`/reports/income-statement`)
- [ ] Date range filter
- [ ] Hierarchical account display

### 5. Export Features

- [ ] PDF export for all reports
- [ ] Excel export for all reports
- [ ] Print-friendly CSS

---

## Service Methods

```java
AccountBalanceCalculator {
    calculateTrialBalance(LocalDate asOf) → List<AccountBalance>
    calculateAccountBalance(UUID accountId, DateRange) → Balance
    getAccountTransactions(UUID accountId, DateRange) → List<JournalEntry>
}

BalanceSheetService {
    generateBalanceSheet(LocalDate asOf) → BalanceSheet
}

IncomeStatementService {
    generateIncomeStatement(LocalDate start, LocalDate end) → IncomeStatement
}
```

---

## Test Files

1. `TrialBalanceTest.java` - Trial balance calculation and display
2. `GeneralLedgerReportTest.java` - General ledger report
3. `BalanceSheetTest.java` - Balance sheet generation
4. `IncomeStatementTest.java` - Income statement generation
5. `ReportExportTest.java` - PDF/Excel export

---

## Notes

- Balances calculated on-the-fly from journal_entries (no materialized balances)
- Only POSTED entries included in calculations
- VOID entries excluded from calculations
