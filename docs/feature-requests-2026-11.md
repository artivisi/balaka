# Feature Requests 2026-11

## Issue #46: Deductibility flag and benefit-in-kind

### Built

- `ChartOfAccount.fiscalTreatment`: `DEDUCTIBLE`, `NON_DEDUCTIBLE`, `CONDITIONAL`; on the account form, list badge, account API, seed CSV and export.
- Fiscal adjustment drafts: `FiscalAdjustmentDraftService.generate(year)`, web section on Rekonsiliasi Fiskal, `POST /api/fiscal-adjustments/generate` and `/{id}/confirm`. Only `CONFIRMED` rows reach the reconciliation, L1 and SPT exports. Pending drafts show on the SPT checklist and as `pendingDraftAdjustments` on the rekonsiliasi API.
- Benefit-in-kind: `benefit_in_kind` rows (transaction, employee, amount, description), UI section on transaction detail, `/api/benefits-in-kind`. System tag `NATURA` is attached and removed automatically. `PayrollService.calculatePayroll` adds pending rows to the TER base and December reconciliation; `PayrollDetail.benefitInKind` holds the counted value. 1721-A1 PDF/API, PPh 21 summary and annual e-Bupot use cash gross plus natura.
- Seed accounts in the four packs and V008, same codes everywhere:
  `2.1.25` Hutang PPh 26, `2.1.40` Hutang Dividen, `3.2.03` Dividen, `5.8` header, `5.8.01` Beban Bonus, `5.8.02` Beban THR, `5.8.03` Beban Natura/Kenikmatan, `5.8.04` Beban Tidak Dapat Dikurangkan.

### Schema (V008)

`chart_of_accounts.fiscal_treatment` (nullable, CHECK expense only); `fiscal_adjustments.status`, `.source` (existing rows `CONFIRMED`/`MANUAL`); `payroll_details.benefit_in_kind`; table `benefit_in_kind`; tag type and tag `NATURA`. Test data: integration V913 adds the seed accounts to the V800 chart.

### Decisions made on the owner's behalf

- Existing accounts stay `NULL` (not classified): no rule can derive their treatment. Generation lists those with a balance. The account form does not require the field.
- Conditional rule: a draft for the year balance minus benefit-in-kind with a recipient on that account. Review decides the rest; the draft notes show the figures. No percentage or threshold logic.
- Generated drafts are `PERMANENT`/`POSITIVE`. Regeneration replaces all generated drafts of the year, including edited ones and re-proposing rejected ones (rejection is deletion). Confirmed rows on the same `accountCode` reduce the new draft.
- Recipients only on `POSTED` transactions, total capped at the transaction's expense debit. A dedicated table instead of a tag per employee; the tag remains for filter and report.
- A row is linked to the payroll detail that counted it. Late rows enter the next run of the same year. A row counted by a run blocks deleting it and voiding its transaction until the run is recalculated or cancelled; a POSTED run has no way back except the existing flows.
- Rows dated after the employee's last run of the year never reach PPh 21. 1721-A1 prints a warning with that value and excludes it from bruto.
- PPh 21 on natura is withheld from cash pay; the payroll journal is unchanged because the natura expense was already booked by its own transaction.
- 5.8 sits under root `5`, including on coffee-shop where `5` is named Harga Pokok Penjualan, to keep codes stable. `SptTahunanExportService` treats 5.8 as operating expense alongside 5.1. Reports on the coffee-shop pack group it under that root.
- No dividend accounts in the campus pack: a yayasan does not distribute profit (UU Yayasan Pasal 3).
- Permissions: recipients need `PAYROLL_VIEW`/`PAYROLL_CREATE` (personal income data), API `tax-export:read`/`payroll:write`. Drafts need `FISCAL_PERIOD_MANAGE`, API `tax-export:write`. No new scopes, so no scope migration.
- Seed classification: `DEDUCTIBLE` on non-header expenses, except PPh 25/29/final and denda (`NON_DEDUCTIBLE`), PPh 21 borne by the company and "Beban Pajak"/"Beban Pajak Lainnya" (`CONDITIONAL`). Unverified by a tax reviewer.

### Fixed along the way

`ReportService.generateBalanceSheet` and the 8A mapping added debit-normal equity (Prive, now Dividen) to total equity. They now subtract it. Needed for the dividend account; also changes balance sheets that carry a Prive balance.

### Unverified regulatory values

No rate is introduced. Pasal references on drafts (`Pasal 9 ayat (1) UU PPh`, `Pasal 6 dan Pasal 9 UU PPh`) and the treatment of natura as conditional follow UU HPP, PP 55/2022 and PMK 66/2023 from memory, not checked against the current text. PMK 66/2023 exemption categories and thresholds are not implemented: the user records only the taxable value.

### Cut

- `benefit_in_kind`, like `fiscal_adjustments` and `transaction_tags`, is not in the data export/import ZIP. `payroll_details.benefit_in_kind` and `fiscal_treatment` are. After a restore the counted totals stay but the rows do not.
- No journal template for natura; the user books it through an existing template or free-form journal entry. Payroll-side bonus/THR/benefit is issue #47.

### Noted, not fixed

`DataExportService` writes `created_at` as COA column 7 while the importer reads column 7 as `is_permanent`.

### Deploy

V008 runs on deploy. It fails if `2.1.25`, `2.1.40`, `3.2.03`, `5.8` or `5.8.0x` exists with another account type; rename the account first. A code used with the same type is kept as is, including its name. It also fails if parents `2.1`, `3.2` or `5` are missing from a non-empty chart; create them first. An empty chart skips the insert. A flyway failure rolls back and leaves the previous schema.

After deploy, classify existing expense accounts under Bagan Akun.
