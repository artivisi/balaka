# Demo Data Implementation Guide

Reference for implementing demo data loaders across all 4 industries. Based on the verified IT Service implementation.

## Architecture

```
demo-transactions.csv (transaction plan)
        ↓
DemoDataLoaderBase (Playwright orchestration)
        ↓
For each month:
  1. Execute transactions from CSV via transaction form UI
  2. Run payroll via payroll form UI (create → calculate → approve → post)
  3. Auto-create: Bayar Hutang Gaji, Bayar Hutang BPJS (amounts from payroll)
  4. Auto-create: Setor PPh 21 (amount from payroll, deposited next month 10th)
  5. Auto-create: Setor PPN (PKP only, previous month's PPN Keluaran, deposited 15th)
  6. Generate + post depreciation via depreciation UI
        ↓
After all months:
  7. Create fiscal adjustments via rekonsiliasi fiskal UI
        ↓
DemoVerificationTest validates everything
```

## File Structure Per Industry

```
src/test/resources/demo-data/{industry}/
├── README.md                    # Company description
├── SCENARIO.md                  # Transaction flow + expected balances (authoritative source)
├── 01_company_config.csv        # Company name, NPWP, PKP status, industry
├── 07_clients.csv               # Clients (needed for tax auto-populate)
├── 08_projects.csv              # Projects linked to clients (IT service only)
├── 11_fiscal_periods.csv        # All periods OPEN (loader closes them)
├── 15_employees.csv             # Employees with PTKP status
└── demo-transactions.csv        # Transaction plan (CSV)
```

## demo-transactions.csv Format

```csv
date,template_name,amount,inputs,description,reference,project,status
```

| Column | Description |
|--------|-------------|
| `date` | YYYY-MM-DD |
| `template_name` | Exact template name from seed data, or `__ASSET__` |
| `amount` | Amount for SIMPLE templates (0 for DETAILED) |
| `inputs` | Pipe-separated key:value for DETAILED variables or account hints |
| `description` | Transaction description |
| `reference` | Reference number |
| `project` | Code of an ACTIVE project (the transaction form lists only active projects; links to client for tax auto-populate) |
| `status` | POST or DRAFT |

### Special Actions

**`__ASSET__`** — Creates fixed asset via asset form (CATEGORY is the asset category code) + purchase transaction debiting the category's asset account:
```csv
2025-01-15,__ASSET__,,AST-CODE|Asset Name|CATEGORY|cost|usefulLife|residualValue|depMethod,Description,reference,,POST
```

### DETAILED Template Variables

For DETAILED templates (amount=0), use `inputs` column with `var_` prefix:
```csv
2025-01-10,Penjualan Tokopedia,0,var_grossSales:25000000|var_adminFee:1250000,Description,REF,,POST
```

### Comments

Lines starting with `#` are ignored:
```csv
# === JANUARY 2025 ===
```

## Loader Class Template

```java
@Slf4j
@DisplayName("Demo: {Industry} Data Loader")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Demo{Industry}DataLoader extends DemoDataLoaderBase {

    @Override protected String industryName() { return "{Industry}"; }
    @Override protected String seedDataPath() { return "industry-seed/{industry}/seed-data"; }
    @Override protected String demoDataPath() { return "src/test/resources/demo-data/{industry}"; }
    @Override protected int jkkRiskClass() { return 1; }       // BPJS JKK risk class
    @Override protected long baseSalary() { return 15000000; } // Gross salary for ALL employees
    @Override protected boolean ppnEnabled() { return true; }  // false for UMKM non-PKP

    @Test @Order(1) void importSeed() throws Exception { importSeedData(); }
    @Test @Order(2) void importMaster() throws Exception { importMasterData(); }
    @Test @Order(3) void createUsers() { createDemoUsers(); }
    @Test @Order(4) void executeTransactions() {
        executeDemoTransactions("demo-data/{industry}/demo-transactions.csv");
    }
    @Test @Order(5) void validateBalance() {
        validateTrialBalance(LocalDate.of(2025, 12, 31));
    }
    @Test @Order(6) void validateDashboardLoads() { validateDashboard(); }
}
```

## Seed Data Requirements

Each industry seed pack (`industry-seed/{industry}/seed-data/`) MUST have:

### Required Templates (check and add if missing)

| Template | Purpose | Type |
|----------|---------|------|
| Post Gaji Bulanan | Payroll journal posting | DETAILED, `is_system=false` |
| Bayar Hutang Gaji | Pay salary liability | SIMPLE |
| Bayar Hutang BPJS | Pay BPJS liability | SIMPLE |
| Setor PPh 21 | Monthly PPh 21 deposit | SIMPLE |
| Penyusutan Aset | Depreciation posting | DETAILED (BEBAN_PENYUSUTAN/AKUM_PENYUSUTAN hints) |
| Jurnal Penutup Tahun | Fiscal year closing | SIMPLE |
| Jurnal Manual | Ad-hoc/adjustment entries | SIMPLE |
| Beban Admin Bank | Monthly bank fee | SIMPLE |
| Pembelian Aset Tetap | Fixed asset purchase | DETAILED (ASET_TETAP hint) |

### For PKP Companies (IT Service)
| Template | Purpose |
|----------|---------|
| Setor PPN | Monthly PPN deposit |
| Pendapatan + PPN templates | Revenue with PPN Keluaran |
| Pembelian + PPN templates | Purchase with PPN Masukan |

### For UMKM (Online Seller, Coffee Shop)
| Template | Purpose |
|----------|---------|
| Bayar PPh Final UMKM | Monthly 0.5% final tax |

### Required COA Accounts

| Account | Purpose |
|---------|---------|
| 2.1.10 (or equivalent) Hutang Gaji | Payroll liability |
| 2.1.13 (or equivalent) Hutang BPJS | BPJS liability |
| 2.1.20 (or equivalent) Hutang PPh 21 | PPh 21 liability |
| 3.2.01 Laba Ditahan | Retained earnings (closing target) |
| 3.2.02 Laba Berjalan | Current year earnings (closing intermediate) |

### Required Asset Categories (with correct accounts)

Each asset category must have:
- `asset_account_code` → fixed asset account (e.g., 1.2.01)
- `accumulated_depreciation_account_code` → contra-asset (e.g., 1.2.02)
- `depreciation_expense_account_code` → expense account (e.g., 5.1.12)

## Account Mapping Auto-Select

The loader auto-selects accounts for dynamic template lines based on hint labels:

| Hint | Selects Account Starting With |
|------|------------------------------|
| BANK | 1.1.02 (Bank BCA) |
| PENDAPATAN | 4.1. (first revenue account) |
| BEBAN | 5. (first expense account) |
| ASET_TETAP / FIXED_ASSET | 1.2. (first fixed asset account) |

If a template uses a different hint, add the mapping in `DemoDataLoaderBase.createTransaction()`.

## Fiscal Adjustments

Override `fiscalAdjustments()` in the loader subclass:

```java
@Override
protected List<FiscalAdj> fiscalAdjustments() {
    return List.of(
        new FiscalAdj("Description", "PERMANENT", "POSITIVE", amount, "accountCode", "notes"),
        new FiscalAdj("Description", "TEMPORARY", "NEGATIVE", amount, "accountCode", "notes")
    );
}
```

Categories: `PERMANENT` (Beda Tetap), `TEMPORARY` (Beda Waktu)
Directions: `POSITIVE` (increases taxable income), `NEGATIVE` (decreases)

## Fiscal Periods

All fiscal periods in `11_fiscal_periods.csv` must be **OPEN**. The loader closes them after processing each month. Format:
```csv
year,month,status,month_closed_at,month_closed_by,tax_filed_at,tax_filed_by
2025,1,OPEN,,,,
```

## Payroll Configuration

- `baseSalary()` — same gross salary for ALL employees (payroll UI limitation)
- `jkkRiskClass()` — BPJS JKK class (1-5, affects JKK rate)
- PPh 21 TER rate depends on employee PTKP status and salary bracket
- December: annual reconciliation (progressive rates), typically higher than monthly TER

### BPJS Rates (2025)

| Component | Employee | Company | Cap |
|-----------|----------|---------|-----|
| Kesehatan | 1% | 4% | Base max 12,000,000 |
| JHT | 2% | 3.7% | No cap |
| JP | 1% | 2% | Base max 10,042,300 (Jan-Feb 2025), 10,547,400 (from Mar 2025) |
| JKK | — | 0.24-1.74% | No cap |
| JKM | — | 0.3% | No cap |

## Tax Auto-Populate

Tax details (PPN faktur, PPh 23 bukti potong) auto-populate when:
1. Transaction template name contains tax keywords ("PPN", "PPH 23", etc.)
2. Transaction is linked to a project that has a client
3. Client has a valid counterparty name

**Ensure income transactions have project codes in the CSV** to link to clients.

## Hardcoded Template UUID Fallbacks

Three services use hardcoded template UUIDs from V004 seed data. All have name-based fallbacks:

| Service | Config/Constant | Fallback Name |
|---------|----------------|---------------|
| PayrollService | `app.payroll.template-id` | "Post Gaji Bulanan" |
| FixedAssetService | `DEPRECIATION_TEMPLATE_ID` | "Penyusutan Aset" |
| FiscalYearClosingService | `CLOSING_TEMPLATE_ID` | "Jurnal Penutup Tahun" |

## Verification Checklist

For each industry, the DemoVerificationTest should check:

### 1. Trial Balance
- [ ] All expected accounts present
- [ ] Total debit = total credit
- [ ] Bank account positive (healthy cash)

### 2. Payroll
- [ ] 12 monthly payroll runs, all POSTED
- [ ] Journal entries: 6 lines per run (Beban Gaji, BPJS Kes, BPJS TK, Hutang Gaji, Hutang BPJS, Hutang PPh 21)
- [ ] Hutang Gaji = 0 at each month end
- [ ] Hutang BPJS = 0 at each month end
- [ ] PPh 21 deposits match payroll accruals (except December = outstanding)

### 3. Depreciation
- [ ] Entries generated and posted for all months
- [ ] Akum. Penyusutan balance correct
- [ ] Beban Penyusutan balance correct

### 4. P&L (Income Statement)
- [ ] Revenue matches expected total
- [ ] Expenses match expected total
- [ ] Net Income = Revenue - Expense

### 5. Balance Sheet (before closing)
- [ ] Assets = Liabilities + Equity
- [ ] Laba Berjalan = P&L Net Income

### 6. Fiscal Year Closing
- [ ] Revenue accounts zeroed
- [ ] Expense accounts zeroed
- [ ] Laba Ditahan populated with net income

### 7. Balance Sheet (after closing)
- [ ] Laba Berjalan = 0
- [ ] Assets still = Liabilities + Equity

### 8. Tax
- [ ] PPN auto-populated (PKP only)
- [ ] PPh 23 auto-populated (from client-linked transactions)
- [ ] Fiscal adjustments present

### 9. Coretax SPT Lampiran
- [ ] Transkrip 8A: Aktiva = Pasiva
- [ ] Lampiran I: PKP > 0, koreksi fiskal present
- [ ] Lampiran II: beban usaha + beban luar usaha
- [ ] Lampiran III: kredit pajak PPh 23
- [ ] PPh Badan: PKP, PPh terutang, kredit pajak, PPh 29

## Industry-Specific Notes

### IT Service (PKP)
- Revenue templates: Pendapatan Jasa + PPN, +PPh 23, BUMN FP03
- All income linked to projects → clients for tax auto-populate
- Fixed assets: computers, servers (KOMPUTER category)

### Online Seller (UMKM, non-PKP)
- Revenue templates: Penjualan Tokopedia/Shopee/TikTok (DETAILED with grossSales/adminFee)
- Marketplace withdrawal templates (Withdraw Saldo)
- PPh Final UMKM 0.5% of monthly gross revenue
- Products in seed data (36_products.csv)
- `ppnEnabled() = false`

### Coffee Shop (UMKM, non-PKP)
- Revenue templates: Penjualan Tunai + COGS (DETAILED with amount/cogs)
- Raw material purchases: Pembelian Bahan Baku (Tunai/Kredit)
- BOM + production orders in seed data
- `ppnEnabled() = false`

### Campus (non-PKP education)
- Revenue: Tagihan SPP, Uang Pangkal, Praktikum, Wisuda (billing + payment pairs)
- Grants: Terima Hibah
- Scholarships: Beasiswa Prestasi/Tidak Mampu
- Foundation: Setoran Modal Yayasan
- `ppnEnabled() = false`

## Running Tests

```bash
# Single industry
./mvnw test -Dtest=DemoItServiceDataLoader

# With visible browser (debugging)
./mvnw test -Dtest=DemoItServiceDataLoader -Dplaywright.headless=false -Dplaywright.slowmo=100

# Full verification (IT Service)
./mvnw test -Dtest=DemoVerificationTest

# Expected runtime: ~8 minutes per industry
```
