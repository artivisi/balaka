# IT Service Demo Scenario: PT Solusi Digital Nusantara

## Company Profile

| Field | Value |
|-------|-------|
| Company | PT Solusi Digital Nusantara |
| Industry | IT Consulting & Development |
| NPWP | 01.234.567.8-201.000 |
| PKP | Yes (since 2019-01-01) |
| Fiscal Year | January - December |
| Currency | IDR |

## Employees (5 permanent, salary 15M/month each)

| ID | Name | PTKP | TER Category | Monthly PPh 21 (Jan-Nov) |
|----|------|------|-------------|------------------------|
| EMP-D001 | Ahmad Fauzi (CTO) | K_2 | B | 675,000 |
| EMP-D002 | Sari Wulandari (PM) | TK_0 | A | 750,000 |
| EMP-D003 | Riko Pratama (Senior Dev) | K_1 | B | 675,000 |
| EMP-D004 | Maya Anggraini (BA) | TK_0 | A | 750,000 |
| EMP-D005 | Dian Kusuma (QA Lead) | K_0 | A | 750,000 |

## Fixed Assets

| Code | Name | Purchase Date | Cost | Useful Life | Monthly Depreciation |
|------|------|---------------|------|-------------|---------------------|
| AST-LPT-001 | Laptop HP EliteBook 860 | 2025-01-15 | 25,000,000 | 48 months | 520,833.33 |
| AST-SRV-001 | Server Dell PowerEdge R750 | 2025-03-20 | 45,000,000 | 48 months | 937,500.00 |

## Monthly Payroll (per employee at 15,000,000 gross)

### BPJS
| Component | Employee | Company |
|-----------|----------|---------|
| Kesehatan (cap 12M) | 120,000 | 480,000 |
| JHT | 300,000 | 555,000 |
| JP (cap 10,042,300) | 100,423 | 200,846 |
| JKK (class 1) | — | 36,000 |
| JKM | — | 45,000 |

### Monthly Payroll Totals (5 employees)
| Item | Jan-Nov | December |
|------|---------|----------|
| Total Gross | 75,000,000 | 75,000,000 |
| Total Employee BPJS | 2,602,115 | 2,602,115 |
| Total PPh 21 | 3,600,000 | 16,350,000 |
| Total Deductions | 6,202,115 | 18,952,115 |
| Total Net Pay | 68,797,885 | 56,047,885 |
| Total Company BPJS | 6,584,230 | 6,584,230 |

## Income Schedule (2025)

| Month | Client | Template | Amount | PPN 11% |
|-------|--------|----------|--------|---------|
| Jan | MANDIRI | +PPN+PPh23 | 150,000,000 | 16,500,000 |
| Jan | PLN | BUMN FP03 | 200,000,000 | (dipungut) |
| Feb | GRAB | +PPN | 80,000,000 | 8,800,000 |
| Feb | TELKOM | +PPN+PPh23 | 120,000,000 | 13,200,000 |
| Mar | KOMINFO | BUMN FP03 | 180,000,000 | (dipungut) |
| Apr | MANDIRI | +PPN+PPh23 | 100,000,000 | 11,000,000 |
| May | TELKOM | +PPN+PPh23 | 130,000,000 | 14,300,000 |
| Jun | PLN | BUMN FP03 | 250,000,000 | (dipungut) |
| Jul | — | +PPN | 90,000,000 | 9,900,000 |
| Aug | MANDIRI | +PPN+PPh23 | 160,000,000 | 17,600,000 |
| Sep | KOMINFO | BUMN FP03 | 220,000,000 | (dipungut) |
| Oct | TELKOM | +PPN+PPh23 | 140,000,000 | 15,400,000 |
| Nov | — | +PPN | 110,000,000 | 12,100,000 |
| Dec | MANDIRI | +PPN+PPh23 | 180,000,000 | 19,800,000 |
| **Total** | | | **2,110,000,000** | **138,600,000** |

## Monthly Cycle (automated by loader)

For each month January-December 2025:

1. **Revenue transactions** — from demo-transactions.csv (via Playwright transaction form)
2. **Expense transactions** — cloud, software, rent, telecom, ops, bank admin (via Playwright)
3. **Fixed asset purchase** — if scheduled this month (via asset form + Pembelian Aset Tetap template)
4. **Payroll run** — create, calculate, approve, post (via Playwright payroll form)
5. **Bayar Hutang Gaji** — amount from payrollRun.totalNetPay (auto-generated)
6. **Bayar Hutang BPJS** — amount from payroll BPJS totals (auto-generated)
7. **Setor PPh 21** — amount from payrollRun.totalPph21, deposited 10th of next month (auto-generated)
8. **Setor PPN** — previous month's PPN Keluaran, deposited 15th of current month (auto-generated)
9. **Generate + post depreciation** — via depreciation management UI
10. **Close fiscal period** — via fiscal period management UI

## Verified Trial Balance at 2025-12-31

| Account | Debit | Credit |
|---------|-------|--------|
| 1.1.02 Bank BCA | 1,288,859,240 | |
| 1.1.26 Kredit Pajak PPh 23 | 36,600,000 | |
| 1.2.01 Peralatan Komputer | 70,000,000 | |
| 1.2.02 Akum. Penyusutan Peralatan Komputer | | 15,625,000 |
| 2.1.03 Hutang PPN | | 69,300,000 |
| 2.1.20 Hutang PPh 21 | | 16,350,000 |
| 3.1.01 Modal Disetor | | 500,000,000 |
| 4.1.01 Pendapatan Jasa Training | | 2,110,000,000 |
| 5.1.01 Beban Gaji | 900,000,000 | |
| 5.1.02 Beban BPJS Kesehatan | 28,800,000 | |
| 5.1.03 Beban BPJS Ketenagakerjaan | 50,210,760 | |
| 5.1.05 Beban Sewa | 180,000,000 | |
| 5.1.06 Beban Telekomunikasi | 30,000,000 | |
| 5.1.12 Beban Penyusutan | 15,625,000 | |
| 5.1.20 Beban Cloud & Server | 66,000,000 | |
| 5.1.21 Beban Software & Lisensi | 9,900,000 | |
| 5.1.99 Beban Operasional Lainnya | 35,100,000 | |
| 5.2.01 Beban Bank | 180,000 | |
| **TOTAL** | **2,711,275,000** | **2,711,275,000** |

## Key Verification Points

### Payroll
- PPh 21 Jan-Nov: 3,600,000/month (5 employees × varying TER rates)
- PPh 21 December: 16,350,000 (annual reconciliation with progressive rates)
- Total PPh 21 annual: 55,950,000
- PPh 21 deposits match payroll accruals exactly
- Hutang Gaji = 0 at each month end (paid same month)
- Hutang BPJS = 0 at each month end (paid same month)
- Hutang PPh 21 = 16,350,000 at year end (December, deposited in January 2026)

### Fixed Assets & Depreciation
- 2 assets: laptop (12 months depreciation) + server (10 months, from March)
- Monthly depreciation: laptop 520,833.33 + server 937,500.00 = 1,458,333.33 (from March onward)
- Annual depreciation: 15,625,000 (matches Akum. Penyusutan)
- Net book value: 70,000,000 - 15,625,000 = 54,375,000

### PPN
- PPN Keluaran: 138,600,000 (from +PPN and +PPN+PPh23 templates)
- BUMN FP03 transactions: PPN dipungut pembeli (not recorded by seller)
- PPN deposited: 69,300,000 (7 months, some months nihil for BUMN-only income)
- Hutang PPN outstanding: 69,300,000

### Revenue
- All revenue recorded via app's template formulas (PPN 11%, PPh 23 2%, BUMN FP03)
- Kredit Pajak PPh 23: 36,600,000 (from 7 income transactions with PPh 23)

## Transaction Summary
- **157 POSTED transactions** total
- 78 manual transactions (from CSV)
- 12 payroll postings
- 12 Bayar Hutang Gaji
- 12 Bayar Hutang BPJS
- 12 Setor PPh 21 (including December reconciliation amount)
- 7 Setor PPN
- 2 asset purchases (Pembelian Aset Tetap template)
- 22 depreciation postings (12 laptop + 10 server)
