# Accounting Tutorial Plan

Industry-specific accounting tutorials for non-accounting users.
Each tutorial uses demo company data and auto-generated screenshots.

## Screenshot Strategy

Screenshots are captured by the existing demo Playwright loaders during transaction execution.
Add `takeManualScreenshot("tutorials/{industry}/{step}")` calls at key navigation points.
Output: `target/user-manual/screenshots/tutorials/`

## Common Guides

| # | File | Topic | Screenshots | Status |
|---|------|-------|-------------|--------|
| 01 | `common/01-setup.md` | Install, login, company setup | login, dashboard, company-config | |
| 02 | `common/02-chart-of-accounts.md` | What is COA, account types, hierarchy | coa-list, coa-detail | |
| 03 | `common/03-debit-credit.md` | Core concept, visual examples | journal-entry-example | |
| 04 | `common/04-journal-templates.md` | SIMPLE vs DETAILED, formulas, hints, variables, custom templates | template-list, template-form, template-detail | |
| 05 | `common/05-financial-reports.md` | Trial balance, P&L, balance sheet, cash flow | trial-balance, income-statement, balance-sheet | |
| 06 | `common/06-payroll.md` | BPJS rates, PPh 21 TER, December reconciliation | payroll-form, payroll-detail, payroll-list | |
| 07 | `common/07-ppn.md` | PPN Keluaran/Masukan, DPP Nilai Lain, Setor PPN | transaction-ppn, ppn-report | |
| 08 | `common/08-pph.md` | PPh 21, 23, 4(2), 25, 29, Final UMKM | pph-report, bukti-potong | |
| 09 | `common/09-bpjs.md` | Kesehatan, JHT, JP, JKK, JKM — rates & caps | bpjs-calculator | |
| 10 | `common/10-fixed-assets.md` | Purchase, depreciation, disposal | asset-form, asset-detail, depreciation-list | |
| 11 | `common/11-monthly-closing.md` | Period close workflow, checklist | fiscal-period-list, close-month | |
| 12 | `common/12-year-end-closing.md` | Closing journal, rekonsiliasi fiskal, SPT | rekonsiliasi-fiskal, fiscal-closing | |
| 13 | `common/13-coretax-export.md` | Lampiran I-V, Transkrip 8A | spt-checklist, coretax-export | |

## IT Service Tutorials

Demo company: PT Solusi Digital Nusantara (PKP, 5 employees, IT consulting)

| # | File | Topic | Key Templates |
|---|------|-------|---------------|
| 00 | `it-service/00-overview.md` | Year-in-the-life narrative | — |
| 01 | `it-service/01-opening-balance.md` | Setoran modal, COA setup | Setoran Modal |
| 02 | `it-service/02-recording-income.md` | 3 income patterns: +PPN, +PPh23, BUMN | Pendapatan Jasa +PPN/+PPh23/BUMN |
| 03 | `it-service/03-paying-expenses.md` | Cloud, rent, telecom, operations | Bayar Beban * |
| 04 | `it-service/04-payroll.md` | Monthly cycle, PPh 21 TER | Post Gaji, Bayar Hutang Gaji/BPJS |
| 05 | `it-service/05-fixed-assets.md` | Buy laptop/server, depreciation | Pembelian Aset Tetap |
| 06 | `it-service/06-monthly-closing.md` | Tax deposits, period close | Setor PPh/PPN |
| 07 | `it-service/07-year-end.md` | Closing journal, SPT, Coretax | Jurnal Penutup Tahun |
| 08 | `it-service/08-common-mistakes.md` | PPN nihil, PPh 23 kredit, cash vs accrual | — |

## Online Seller Tutorials

Demo company: Toko Gadget Sejahtera (UMKM non-PKP, 4 employees)

| # | File | Topic | Key Templates |
|---|------|-------|---------------|
| 00 | `online-seller/00-overview.md` | Marketplace seller daily life | — |
| 01 | `online-seller/01-marketplace-sales.md` | Tokopedia/Shopee/TikTok with admin fees | Penjualan Tokopedia/Shopee/TikTok |
| 02 | `online-seller/02-withdrawals.md` | Saldo → bank, timing | Withdraw Saldo * |
| 03 | `online-seller/03-inventory.md` | Stock tracking, purchase, adjustment | Pembelian Barang, Penyesuaian |
| 04 | `online-seller/04-expenses.md` | Ongkir, packing, iklan, sewa gudang | Ongkir, Biaya Packing, Iklan |
| 05 | `online-seller/05-pph-final-umkm.md` | 0.5% monthly, PP 55/2022 | Bayar PPh Final UMKM |
| 06 | `online-seller/06-payroll.md` | Small team payroll | Post Gaji, Bayar Hutang * |
| 07 | `online-seller/07-monthly-closing.md` | Monthly + year-end cycle | — |

## Coffee Shop Tutorials

Demo company: Kedai Kopi Nusantara (UMKM non-PKP, 6 employees)

| # | File | Topic | Key Templates |
|---|------|-------|---------------|
| 00 | `coffee-shop/00-overview.md` | Running a kedai kopi | — |
| 01 | `coffee-shop/01-raw-materials.md` | Buying kopi, susu — tunai vs kredit | Pembelian Bahan Baku |
| 02 | `coffee-shop/02-daily-sales.md` | POS recording, COGS | Penjualan Tunai + COGS |
| 03 | `coffee-shop/03-online-delivery.md` | GrabFood/GoFood with platform fees | Penjualan Online |
| 04 | `coffee-shop/04-production-bom.md` | BOM for croissant, production order | Produksi Barang Jadi |
| 05 | `coffee-shop/05-expenses.md` | Sewa, listrik, operational | Bayar Sewa/Listrik |
| 06 | `coffee-shop/06-payroll.md` | Barista/kasir payroll | Post Gaji |
| 07 | `coffee-shop/07-tax-umkm.md` | PPh Final, when to upgrade to PKP | Bayar PPh Final UMKM |

## Campus Tutorials

Demo company: STMIK Merdeka Digital (non-PKP educational, 8 employees)

| # | File | Topic | Key Templates |
|---|------|-------|---------------|
| 00 | `campus/00-overview.md` | Managing campus finances | — |
| 01 | `campus/01-tuition-billing.md` | SPP, uang pangkal, praktikum cycle | Tagihan SPP/UP/Praktikum |
| 02 | `campus/02-receiving-payments.md` | Cicilan, reconciliation | Pembayaran SPP/UP |
| 03 | `campus/03-grants-donations.md` | Hibah Kemendikbud, donasi alumni | Terima Hibah/Donasi |
| 04 | `campus/04-scholarships.md` | Beasiswa prestasi vs tidak mampu | Beasiswa * |
| 05 | `campus/05-faculty-payroll.md` | Dosen tetap, honorer, PPh 21 | Gaji Dosen/Karyawan |
| 06 | `campus/06-campus-operations.md` | Utilities, maintenance, security | Bayar Listrik/Air/Internet |
| 07 | `campus/07-reporting.md` | Laporan ke yayasan, accreditation | Financial reports |

## Execution Order

1. Add screenshot captures to existing demo loaders (all 4 industries)
2. Write `common/` guides (reusable across all industries)
3. Write IT Service tutorials (most verified demo data)
4. Write remaining 3 industry tutorials
5. Cross-link: industry tutorials → common guides
6. Review: verify all screenshots render, all links work
