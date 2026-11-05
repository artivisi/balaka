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

## Issue #43: Withholding on non-employee and vendor payments

### Built

- Payee master on `Vendor`: `payeeType`, `defaultWithholding`, `defaultTaxObjectCode`, NPWP, NIK, `countryCode`, `foreignTin`, `treatyRate`, `dgtFormNumber`, `dgtValidUntil`. Web form and `/api/withholding/payees`.
- Payment flow: `WithholdingPaymentService.create` computes gross, tax and net, composes a DRAFT transaction with template `Pembayaran dengan Pemotongan PPh` and attaches a `TaxTransactionDetail`. Nothing reaches the ledger until posted, on `/withholding` or through the transaction screens. Credits Hutang PPh 21/23/26.
- Bills: a payment can settle an approved bill. It debits Hutang Usaha, records the `BillPayment` on post through `TransactionPostedEvent`, and reverses it on void through `TransactionVoidedEvent` and `BillService.reversePayment`.
- Deposits: `WithholdingDepositService` with DRAFT transactions from template `Setor PPh Dipotong`; status per masa against `TaxDeadline` (`PPH_26_PAYMENT` is new).
- Exports: `CoretaxExportService.exportBp21`, web and `/api/tax-export/bp21`; PPh 26 in Bupot Unifikasi with a `PPH26` sheet.
- Report: posted debits on service accounts with no PPh detail, per masa. `ChartOfAccount.serviceExpense` is nullable; form, API, CSV import and export carry it.
- Read API for #45 and #51: `WithholdingQueryService`. API scopes `withholding:read`, `withholding:write`.

### Schema (V009)

`vendors`: payee columns; `npwp`, `nik` widened. `chart_of_accounts.service_expense`. `tax_transaction_details`: `PPH_26` in the check, `counterparty_country`, `counterparty_foreign_tin`, `dgt_form_number`, `counterparty_nik` widened. `tax_deadlines`: `PPH_26_PAYMENT`. New tables `withholding_payments`, `withholding_deposits`. Test data V914. V914 also renames V903's second "Hutang PPh 23" (2.1.05).

### Decisions made on the owner's behalf

- "Per masa" means the Pasal 17 brackets run over the payee's cumulated gross within one calendar month of the payment date and restart the next month. Each payment carries the increment. Annual accumulation and PTKP are not applied. Masa order: posted first, then drafts by creation.
- 20% higher only when both NPWP and NIK are absent or blank.
- Cancelling a draft recomputes later PPh 21 drafts of that payee and masa. Drafts are not editable: cancel and recreate. Generic transaction edit and delete refuse these drafts. Voiding a PPh 21 payment with a later non-void payment in the same masa is refused, because the later tax depends on it.
- Payee identity on a draft is a snapshot. Adding an NPWP later does not change existing payments.
- Hutang accounts resolve by exact name, since codes differ per pack (`Hutang PPh 21` is 2.1.20, 2.1.14, 2.1.03). Zero or several matches fail. Packs without Hutang PPh 23 (coffee-shop, campus) get `2.1.21`.
- New pack account `Beban Jasa Profesional`: it-service 5.1.22, online-seller 5.4.07, coffee-shop 6.2.13, campus 5.2.23.
- Seed `service_expense = true` on leaf expense names containing Sewa, Pemeliharaan, Kebersihan, Keamanan, Marketing, Iklan, Cloud, Software, Honorarium, Jasa Profesional; `false` on other leaf expenses. Marketplace admin fees are `false`. Unverified by a tax reviewer.
- Report: a PPN detail does not document a service payment; a PPh 4(2) detail does. All posted debits count, not only cash ones, so bill accruals are caught; an accrual leaves the report once the bill has a non-void withholding payment. No per-transaction "not subject to withholding" flag exists, so an exempt payment stays listed.
- Bupot Unifikasi no longer contains PPh 21, so a masa is not uploaded twice. This changes that export for existing PPh 21 tax details.
- Deposits count only tax withheld through this flow. Payroll PPh 21 shares the Hutang PPh 21 account and keeps its own deposit template. A single SSP covering both is recorded in two parts.
- Templates have `is_system = false`, same as `Tagihan Vendor`: `DataImportService` skips the lines of system templates, which left a template without lines and posted empty journals. `DocumentPostingService` now rejects a template with no lines.
- `vendors.npwp`, `vendors.nik`, `tax_transaction_details.counterparty_nik` are encrypted through `EncryptedStringConverter`. Existing rows stay plaintext until saved again, because SQL has no key. `counterparty_npwp` stays plaintext because `ClientController` queries it by equality; an individual's 16-digit NPWP is therefore plaintext there. API responses mask NPWP and NIK; the vendor page hides them without `VENDOR_EDIT`.
- Permissions reuse existing ones: reads `TAX_REPORT_VIEW`, writes `TRANSACTION_CREATE`/`POST`/`DELETE`, exports `TAX_EXPORT`, payees `VENDOR_*`. V009 grants the new scopes to device tokens holding `tax-export:read` / `transactions:post`; API clients get none.

### Fixed along the way

The vendor form had no NIK input, so saving it erased a stored NIK. Added.

### Unverified regulatory values

Set in `application.properties` (`app.withholding.*`).

- PPh 21 bukan pegawai: 50% and 20% come from the issue. Implemented as stated; the "per masa" reading above and the PMK 168/2023 citation are from memory. Pasal 17 brackets are the existing `Pph21CalculationService` values.
- PPh 23: 2% on all `TaxObjectCode` PPh 23 entries, 100% higher without NPWP or NIK. NIK treated as NPWP. UU PPh Pasal 23, PMK 141/2015, PMK 112/2022, from memory. Objects at 15% (dividend, interest, royalty, prize) have no object code and cannot be selected.
- PPh 26: 20% of gross (UU PPh Pasal 26 ayat (1)). Tax treaty: the rate is entered per payee, no treaty table exists. It applies only when a DGT form number is on file and valid on the payment date (PER-25/PJ/2018, from memory); otherwise 20% with the reason in `rateBasis`. No check on DGT form content or beneficial ownership. A 0% treaty rate posts no tax line. Branch-profit and 26(4) cases are not handled.
- Object code `27-100-99` and the BP21 and `PPH26` sheet layouts are unchecked against Coretax. `21-100-09` is the only PPh 21 object code offered.
- Due day 15 for PPh 26 mirrors PPh 23.

### Cut

- No project link on payments, and no PPN on non-bill payments: a service with PPN goes through a bill.
- No screen to edit a draft; cancel and recreate.
- No reduced base for individuals with an SKB or NPPN.
- New tables are not in the export/import ZIP, like `benefit_in_kind`. After a restore their transactions and tax details remain, but the payment and deposit rows do not.
- No dashboard alert: the warning is the report and the deposit status on `/withholding`.

### Noted, not fixed

- Auto-populated PPh 23 still uses the project's client as counterparty. This flow attaches its detail before posting, so auto-populate skips it. The legacy template `Pembayaran Jasa dengan PPh 23` keeps the problem, and such a transaction leaves the report with the wrong counterparty.
- Audit tracker item confirmed: posting a template with no lines creates a POSTED transaction with no journal. Guarded only in `DocumentPostingService`.
- Lampiran III / pph23-detail still counts PPh 23 withheld from vendors as credit; this flow adds rows there.
- Due dates are not moved to the next working day.
- `DataExportService` has no vendors section although the importer reads `28_vendors.csv`.

### Deploy

V009 runs on deploy. It fails if a template named `Pembayaran dengan Pemotongan PPh` or `Setor PPh Dipotong` exists; rename it first.

After deploy:
1. Make sure the chart has exactly one each of `Hutang PPh 21`, `Hutang PPh 23`, `Hutang PPh 26`.
2. Classify expense accounts under Bagan Akun, column Akun Jasa. Until then the report only warns about them.
3. Fill Jenis Penerima on vendors that receive withheld payments.
4. Re-save vendors holding NPWP or NIK to encrypt them.
5. Have a tax reviewer check PPh 23/26 rates and the export columns.
