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

## Issue #44: Sales tax chain

### Built

- Link: `InvoicePayment.pph23Withheld`, `TaxTransactionDetail.invoicePayment`. Invoice page section Faktur dan Bukti Potong with forms; payment form field PPh 23 Dipotong Pelanggan.
- Bupot: `InvoiceTaxLinkService.recordBupot` composes a DRAFT with template `Pemotongan PPh 23 oleh Pelanggan` (Dr Kredit Pajak PPh 23, Cr Piutang) and its PPh 23 detail.
- Report: `/sales-tax-chain` and `/api/sales-tax-chain` with exceptions and monthly reconciliation.
- Read API for #45 and #51: `SalesTaxChainService.getChain`, `findExceptions`, `getReconciliation`. API scopes `sales-tax-chain:read`, `sales-tax-chain:write`.

### Schema (V010)

`invoice_payments.pph23_withheld` (NOT NULL, existing rows 0, check 0..amount); `tax_transaction_details.id_invoice_payment` (ON DELETE SET NULL). Account `Kredit Pajak PPh 23` if the name is absent; template; device-token scopes. Test data V915. Packs: template in all four, account `1.1.26` added to coffee-shop and campus.

### Decisions made on the owner's behalf

- Source of truth: numbers, dates and amounts of faktur and bupot live only on `tax_transaction_details`. The invoice link is derived from the transaction (`source_document_type/id`, or `invoices.id_transaction`), so existing details join the chain with no backfill and the generic tax-detail screens stay valid. A row with no number is not a faktur or bupot.
- `invoice_payments.amount` keeps meaning receivable settled, and includes the withheld part. Balance, aging and statement queries are unchanged. The withheld amount is the expected-bupot signal, because the bupot usually arrives later. It is entered, not computed: no rate check against the client.
- `recordPayment` still journals nothing; the cash part goes through `Terima Pelunasan Piutang`. Bupot posting credits Piutang for the withheld part.
- SPT Masa PPN DPP means numbered fakturs on POSTED transactions by faktur date. The register stores no filed DPP, so this is computed, not what DJP received. The e-Faktur export selects by transaction date and includes unnumbered rows; the export was not changed, and the report shows `exportDifference`.
- A faktur on a DRAFT transaction is outside the DPP and appears as `FAKTUR_NO_REVENUE`. A faktur on a VOID transaction also appears, unless the invoice still has posted revenue, because voiding does not cancel it at DJP. It clears when the detail is deleted.
- "Paid" includes part-paid: PPN is due on a down payment. `taxAmount = 0` invoices expect no faktur.
- More than one faktur on an invoice is always listed, even when their DPP adds up to the invoice. There is no dismiss action, so a deliberate split stays listed.
- `MISSING_BUPOT` also covers an unnumbered PPh 23 row on an invoice or revenue transaction, and a Kredit Pajak debit with no PPh 23 row. A DRAFT bupot counts as received. A bupot with no receipt link covers the oldest receipts.
- PPN and PPh 23 reconciling lines appear only on an exact match of the unit's DPP minus revenue (`-PPN`, `+PPh 23`, or both). Otherwise `OTHER_DIFFERENCE`. Code `04` differences have their own line. Revenue is compared per invoice, or per transaction outside an invoice.
- `Pendapatan tanpa faktur` includes non-taxable revenue such as interest: no account flag says which revenue carries PPN.
- Exceptions on the web page run from the beginning up to the end of the selected masa, because they do not expire.
- The invoice page refuses a second faktur; other paths can still create one and the report lists it.
- Bupot from customers leaves `findEBupotUnifikasiByDateRange` when the transaction's source is `INVOICE`; the customer issues it. Legacy revenue-template rows without that source stay in the export (noted below).
- Reads run in memory over rows per call; acceptable for a single-tenant volume.
- Permissions: reads `TAX_REPORT_VIEW`; faktur `INVOICE_EDIT` + `TAX_EXPORT`; bupot adds `TRANSACTION_CREATE`. V010 grants scopes to device tokens holding `tax-export:read` / `transactions:post`; API clients get none.

### Unverified regulatory values

No rate is introduced. Faktur date as the masa basis and PPN due on a down payment follow UU PPN Pasal 13 from memory.

### Cut

- New columns are not in the export/import ZIP: `invoice_payments` and `id_invoice_payment` are not exported.
- No web export of the report, no dashboard alert, no dismiss action, no filed-DPP entry.
- No API for recording an invoice payment (no invoice API exists); `pph23Withheld` is web only.

### Noted, not fixed

- PPh 23 rows from legacy revenue templates (`Pendapatan Jasa + PPh 23`) are still in Bupot Unifikasi.
- `TaxReportDetailService` and `TaxTransactionDetailService` hardcode account codes `2.1.03`, `1.1.25`, `1.1.26`.
- `tax_filings.period_year` stops at 2100; tests with filings cannot use the reserved far-future years.
- Duplicate faktur or bupot numbers are refused globally, including between two customers' bupots.
- `invoiceService.cancel` leaves revenue drafts and posted revenue in place.

### Deploy

V010 runs on deploy. It fails if a template named `Pemotongan PPh 23 oleh Pelanggan` exists, if `1.1.26` holds another account while no account is named `Kredit Pajak PPh 23`, or if parent `1.1` is missing in that case.

After deploy:
1. Make sure the chart has exactly one account named `Kredit Pajak PPh 23`.
2. Enter faktur on paid invoices listed under Dibayar tanpa faktur. Older invoices will list here, since no faktur link existed unless a tax detail was on their revenue transaction.
3. Earlier payments have no withheld amount; correct by recording bupot where it exists.

## Issue #47: Single source of salary cost and complete 1721-A1

### Built

- `payroll_additions` rows (run, employee, `BONUS`/`THR`, amount) with a section on the run page and `/api/payroll/{id}/additions`. `PayrollDetail.bonus`, `.thr`; `grossSalary` is now cash gross: base + bonus + THR. They enter the TER base and December reconciliation through the existing `getTaxableGross`, next to #46 natura.
- Run journal: `Post Gaji Bulanan` gets lines `bonus` on 5.8.01, `thr` on 5.8.02, `pph21Overpaid` as a debit on the PPh 21 account. The `grossSalary` formula variable now excludes bonus and THR. 1721-A1 PDF has row 8c; the API has `gajiDanTunjangan`, `bonusDanThr`, `natura` and monthly bonus/THR/natura. The payslip shows them.
- `PayrollReconciliationService`: `warningsForTransaction`, `warningsForRun`, `reconcile(YearMonth)`, `reconcileYear(int)`. Page `/payroll/reconciliation`, `GET /api/payroll/reconciliation?year=` and `/{period}`. #45 and #51 read `runTaxableIncome`, `runPph21` and `ledgerSalaryExpense` per masa.
- Warnings: banner on transaction detail and run detail; `warnings` on transaction, draft, bulk-post and payroll API responses. Posting from the transaction list redirects to the detail page when there is a warning. `DocumentPostingService` drafts show it on their detail page and post response.
- Alert `PAYROLL_UNPOSTED`.

### Schema (V011)

`payroll_details.bonus`, `.thr`; table `payroll_additions`; three lines and a description suffix on the current `Post Gaji Bulanan`; alert rule `PAYROLL_UNPOSTED` (threshold 0). Test data: integration V916 adds the lines to the V800 template.

### Fixed along the way

- `TerCategory` rate columns were wrong in all three categories (separate commit). Checked page by page against the lampiran of PP 58/2023 on pajak.go.id. Bracket bounds were right; the rates had steps the regulation does not have (A: 4.5%, 5.5%; B: 3.5%, 4.5%, 5.5%; C: 2.25%, 2.5%, 3.5%, 4.5%, 5.5%) and lacked 33%, so rates above the first wrong row were too low. Rp 15,000,000 in category A was 5%, is 6%. Annual tax was already right through December reconciliation; Jan-Nov withholding was understated. Posted runs do not change. This overrides the "do not fix unrelated" rule because bonus and THR land in exactly these brackets and the exact-arithmetic tests would otherwise assert wrong rates. Revert the one commit if the owner disagrees; `PayrollBonusThrTest` and the API and UI tests then need the old rates.
- BPJS report columns print the base salary, the wage BPJS is computed from, instead of gross.
- Data export gains `base_salary`, `bonus`, `thr` on payroll details. Import previously left `base_salary` at zero; a pre-V011 export now restores it from gross.

### Decisions made on the owner's behalf

- Warn on period, not employee: journal lines have no employee. An employee named in the description or notes is named in the warning. Warnings never block.
- Salary accounts are derived from the payroll template (debit lines with `grossSalary`, `bonus`, `thr`), so no new configuration and no per-pack code list. Company BPJS lines are excluded. An account outside the template, such as a separate contractor wage account, is not detected. Only debits warn; a credit is a correction.
- A run in any status except CANCELLED counts as existing, including DRAFT.
- The match is on transaction date: a January salary booked by manual journal dated February warns on the February run, not January.
- If a run exists and the template is missing or has no salary line, the warning check throws, which blocks that journal. Without a run the template is not read.
- Rows are per run, not salary components: the run still calculates from one base salary for all employees. Adding to a CALCULATED run recalculates at once, so approval never sees stale figures. APPROVED rejects changes.
- A row for an employee who later became inactive stops the calculation rather than dropping income from 1721-A1.
- BPJS stays on the base salary.
- `pph21Overpaid`: an over-withheld December is a negative PPh 21 and adds to net pay. Journal lines cannot be negative, so a net refund in a run posts as a debit on the liability. A refund nets against other employees in the same run. Before this, such a posting failed on validation. Large bonuses make it reachable.
- Alert threshold is days after the period end, seed 0: there is no `approved_at`, and an approved run before month end is normal. All overdue periods go in one event, per the existing per-rule dedup.
- `difference` on an unposted run is the whole ledger amount, since the run has put nothing in the ledger. Comparing with the unposted total would show zero in the exact case the issue describes.
- No new scopes, same as #46: reads `tax-export:read`/`PAYROLL_VIEW`, writes `payroll:write`/`PAYROLL_CREATE`.

### Unverified regulatory values

No new rate. TER rates are verified against the lampiran. From memory: bonus and THR as income of the month paid under PMK 168/2023; BPJS wage excluding bonus and THR (PP 44/2015, Perpres 82/2018).

### Cut

- `payroll_additions`, like `benefit_in_kind`, is not in the data export ZIP. After a restore the counted totals stay, but recalculating a restored unposted run drops the bonus.
- No warning while typing on the transaction form (it appears after saving, on the detail page). No warning surfaces for recurring auto-posting or reconciliation-created journals, where no person is posting; those show on the run and reconciliation pages.
- Scheduled runs with auto-approve cannot take a bonus; the manual says to turn auto-approve off for those months.

### Noted, not fixed

- A cancelled run blocks a new run for the same period: `createPayrollRun` checks `existsByPayrollPeriod` regardless of status. A wrong approved run cannot be redone, which raises the cost of the APPROVED restriction.
- PPh 21 neto differs across three places. December reconciliation and the 1721-A1 API do not deduct employee JHT/JP. The 1721-A1 PDF deducts all employee BPJS, including Kesehatan, so its row 14 PKP does not produce its row 15 tax. None adds company-paid premiums to bruto. In `PayrollBonusThrTest.buktiPotongTotals`: API-method PKP 77,000,000, PDF row 14 72,200,000, tax 5,550,000 on both.
- `calculateTer` and the reconciliation default a null PTKP status to TK/0.
- 1721-A1 PDF prints a placeholder NPWP and company address.

### Deploy

V011 runs on deploy. It fails if a current `Post Gaji Bulanan` exists and 5.8.01/5.8.02 are missing (V008 creates them), or if its `pph21` credit line has no fixed account; fix the template first.

The TER fix changes monthly PPh 21 from the next calculation for income above the first wrong row (category A above Rp 12,500,000). Calculated, unposted runs keep the old figures until recalculated: recalculate them after deploy.


## Issue #49: Fixed-asset register

### Built

- Register: `brand`, `model`, `custodian`, `condition`, `lastVerifiedDate`, `photoDocument` on `FixedAsset`; `location` and `serial_number` already existed. Web form and detail page, `/assets/{id}/photo`, asset API. Changes of custodian, location or condition write `asset_movements`.
- Verification: `/assets/verification?year=`, checklist with one card per asset and an exceptions table. `AssetVerificationService`, `/api/fixed-assets/verification`, `/verification/exceptions`, `POST /{id}/verification`.
- Disposal: `AssetDisposalService` writes an `asset_disposals` record (`DSP-year-seq`) with reason, counterparty, proceeds and amount snapshot, plus a DRAFT journal. Pages `/assets/{id}/dispose`, `/assets/disposals`, `/assets/disposals/{id}`. Tax effect from `DisposalTaxCalculator` on the form (HTMX preview), the record and the API.
- Read API for #45 and #51: `AssetRegisterQueryService.findDisposals`, `findDisposalsOfAsset`, `findMovements`, `findMovementsOfAsset`, `costMovement(year)`; also `/api/fixed-assets/movement` and `/register-movements`.

### Schema (V012)

`fixed_assets` new nullable columns; `disposal_type` gains `GIFTED`. New tables `asset_disposals` (at most one `DRAFT` or `POSTED` per asset; checks gain = proceeds - book value and sale fields), `asset_verifications` (one per asset per year), `asset_movements`. Accounts `Laba Penjualan Aset`, `Rugi Penjualan Aset` if absent; templates `Pelepasan Aset Tetap`, `Penghapusan Aset Tetap`. Test data V917 replaces the V800 template of that name. Packs: both templates in all four; accounts added to online-seller (4.2.04, 5.5.03), coffee-shop (7.1.02, 8.1.04), campus (4.2.04, 5.4.04).

### Decisions made on the owner's behalf

- The audit defect is closed: no template UUID, templates and gain and loss accounts resolve by exact name and fail when absent or duplicated. The proceeds account is chosen on the form.
- Two templates, because `DocumentPostingService` requires an account for every hint and a non-sale has no proceeds account.
- Asset status: DISPOSED when the journal is posted, by `TransactionPostedEvent`. Until then the status stays and the asset is locked: edit, depreciation generation and posting, second disposal, and generic edit or delete of the draft are refused. Cancel removes the draft and keeps the record `CANCELLED`. Voiding a posted journal sets the record `VOIDED` and returns the asset to ACTIVE or FULLY_DEPRECIATED, by `TransactionVoidedEvent`. Numbers of cancelled and voided records are not reused.
- Removal: `delete` stays for a registration mistake only: no depreciation history, no linked purchase, no POSTED acquisition, no disposal record of any status. The acquisition DRAFT goes with it, which also fixes the dangling draft in the audit list. A VOID acquisition does not block. An old asset with no acquisition transaction and no depreciation stays deletable, since nothing shows it reached the ledger.
- Disposal refuses an unposted acquisition, any PENDING depreciation entry, and a month before the disposal month with no entry of any status. A SKIPPED entry counts as settled. Depreciation for the disposal month is the user's choice: the date only has to be on or after the last posted period end, so disposing mid-month after that month's entry is refused.
- Sale needs positive proceeds; every other type refuses proceeds and proceeds account. A sale at zero is a write-off. Counterparty is required except on write-off.
- A gift debits `Beban Tidak Dapat Dikurangkan` (5.8.04), so #46 fiscal drafts propose the correction without new logic. Write-off and transfer debit `Rugi Penjualan Aset`, including the account name's "Penjualan".
- `proceeds` excludes PPN and the journal records none: PPN needs a faktur decision the app cannot make here. The record shows an indicative figure only.
- Custodian is free text, not an employee link: it can be a unit or a non-employee, and ASSET roles do not hold employee permissions.
- Verification FOUND requires condition, location and custodian and writes them to the register. NOT_FOUND changes nothing; only the disposal flow removes an asset. Same year overwrites the row, with no history of the earlier result. `lastVerifiedDate` never moves back.
- Checklist scope: bought by year end, and not disposed by year end. A draft disposal stays listed.
- Every asset category is checked for every identity field, with no dismiss action, so a building stays listed for a missing serial.
- Edits log with today's date: the form has no effective date. Register creation writes no movement row.
- Permissions: reads `ASSET_VIEW`, verification and photo `ASSET_EDIT`, disposal `ASSET_DISPOSE`. API `assets:read`/`assets:write`, no new scopes and no scope migration. Posting needs the usual `TRANSACTION_POST`.
- `DisposalType.TRANSFERRED` is kept, with fiscal effect shown as needing review.

### Unverified regulatory values

All fiscal text is from memory and marked in the app. Gain or loss in taxable income: Pasal 4 ayat (1) huruf d UU PPh. Gift non-deductible: Pasal 9 ayat (1) huruf g UU PPh; exceptions for qualifying donations are not modelled. Write-off as loss: Pasal 11 ayat (7) UU PPh. PPN on asset delivery: Pasal 16D UU PPN. Indicative PPN `app.asset-disposal.ppn-rate-percent=11` (12% on DPP 11/12, PMK 131/2024). Used-vehicle DPP (PMK 65/2022) is not modelled. Figures use the one book value on the register; a fiscal book value that differs is not tracked.

### Cut

- New tables, like `fixed_assets` itself, are not in the export/import ZIP.
- Disposal record is a web page; no PDF. Browser print works on it.
- Photo upload is web only; no API.
- No backfill of `asset_disposals` for assets already DISPOSED by hand. They show in `costMovement` through their `disposal_date`, not in `findDisposals`.

### Noted, not fixed

- `FixedAssetService` still looks up depreciation by UUID -016 first, then by name.
- `DataSubject` erasure does not touch `custodian`, including history rows.
- `reason` on posted records has no edit path; a wrong one needs void and redo.
- A soft-deleted photo keeps no history; replacing a photo deletes the file.

### Deploy

V012 runs on deploy. It fails if a template named `Pelepasan Aset Tetap` or `Penghapusan Aset Tetap` exists; rename it first. It fails if `Beban Tidak Dapat Dikurangkan` is missing. With no account named `Laba Penjualan Aset` or `Rugi Penjualan Aset`, it creates `4.2.02` and `5.2.02`, and fails if that code holds another account or parent `4.2` or `5.2` is missing.

After deploy:
1. Make sure the chart has exactly one account with each of the three names.
2. Run the checklist for the current year and fill the listed fields.
3. Have a tax reviewer check the fiscal notes and the PPN rate.


## Issue #48: Evidence completeness and audit pack

### Built

- Evidence rules (`evidence_requirements`): by account, covering its sub-accounts, or by template family; evidence type `CONTRACT`, `INVOICE`, `PROOF_OF_PAYMENT` or `BUPOT`; optional minimum transaction amount. Page `/evidence/requirements`.
- `documents.evidence_type`, chosen on upload and changeable per file on the transaction page. Evidence panel on the transaction detail page, refreshed by `HX-Trigger: evidence-changed` after upload, reclassification or delete.
- Missing-evidence report `/evidence/missing?from=&to=`.
- Contract register `/contracts`: client or vendor, period, value, file; links to invoices (`invoices.id_contract`) and transactions (`transactions.id_contract`).
- Bank statement archive `/evidence/bank-statements?year=`: one ACTIVE file per account per month, replacement keeps the old row as SUPERSEDED, retention date per row, delete refused before it. Alert `BANK_STATEMENT_MISSING`.
- Audit pack `/evidence/audit-pack`: ZIP with `README.txt`, ledger CSV, trial balance XLSX, financial statements PDF, evidence index CSV, statement archive index CSV, contract register CSV, and optionally the files under `dokumen/`.
- Query API for #51: `EvidenceQueryService.requirements`, `evidenceFor`, `index`, `gaps`, `contracts`, `contractsActiveOn`; `BankStatementArchiveService.missingStatements`, `yearGrid`. REST: `GET /api/evidence/requirements|transactions/{id}|gaps|index|contracts|bank-statements/missing`, scope `evidence:read`.

### Schema (V013)

`documents.evidence_type`; tables `evidence_requirements`, `contracts`, `bank_statement_archives`; `invoices.id_contract`, `transactions.id_contract`; alert rule `BANK_STATEMENT_MISSING` (threshold 10 days); `evidence:read` for device tokens holding `analysis:read`. No test migration.

### Decisions made on the owner's behalf

- Evidence sources besides a classified file: an invoice issued in the app is its journal's INVOICE evidence; a bupot number on the tax detail is BUPOT; a bank statement line MATCHED in reconciliation is PROOF_OF_PAYMENT; a contract linked to the transaction or its invoice is CONTRACT once its file is attached. A bill is data entry of a vendor invoice, not evidence: the vendor's invoice must be attached.
- Files on the transaction, on its journal lines and on its linked invoice count. Unclassified files (all existing rows) satisfy nothing; the report says when one is present.
- Only posted, non-closing transactions are checked. Draft, void and year-end closing journals are not.
- The minimum compares the transaction amount, not the amount on the matching line.
- Template rules attach to the first version and match every version, so editing a template does not drop its rules.
- No rules are seeded in the packs: required evidence is a business decision. Without rules nothing is reported missing.
- Imported bank statements (CSV in reconciliation) do not count as the archive: only the original filename is kept, not the file.
- Expected statement months start at the earlier of the first posted journal line on the bank's GL account and the first archived month. Inactive bank accounts are not checked; there is no closing date to stop at.
- The page's due date and the alert share one setting, the alert rule threshold, so they agree.
- Retention: deletion is refused, not only warned, until the end of the statement month plus 10 years. A wrong file is fixed by uploading again; the earlier file stays as SUPERSEDED under the same retention. Retention applies to statement archives only; transaction attachments keep their existing delete behaviour.
- Contract and archive files cannot be deleted or reclassified through the generic document endpoints.
- Audit pack files: included only on request and only when their total is within `app.audit-pack.max-document-bytes` (500 MB). Above it the download is refused with the size; the index always carries each file's SHA-256. A file missing from storage fails the export.
- CSV cells starting with `=`, `+`, `-` or `@` are prefixed with `'` against formula injection.
- Permissions: `EVIDENCE_VIEW` (all roles except EMPLOYEE), `EVIDENCE_MANAGE` for contracts and archive (ADMIN, OWNER, ACCOUNTANT), `EVIDENCE_CONFIG` for rules (ADMIN, OWNER). The audit pack uses `REPORT_EXPORT`. The API is read-only.

### Unverified regulatory values

- 10-year retention of books, records and documents: UU KUP Pasal 28 ayat (11), from memory. `app.evidence.retention-years=10`.

### Cut

- New tables are not in the data export/import ZIP.
- No API for writing rules, contracts or archives; no audit pack through the API.
- No link from a contract to a bill; vendor-side coverage goes through linked transactions.
- Contract edit has no history; a replaced contract file stays in `documents` without a link.

### Noted, not fixed

- `docs/developer-guide/api/11-sales-tax-chain.md` is not registered in `UserManualGenerator`.

### Deploy

V013 runs on deploy, additive only. After deploy:
1. Set rules under Master Data > Aturan Bukti.
2. Classify existing attachments that should count as evidence.
3. Archive past bank statements, or turn the `BANK_STATEMENT_MISSING` rule off until they are archived: the first evaluation lists every month since each account's first posted journal.

## Issue #50: Period close checks

### Built

- `ChartOfAccount.closeCheckRole`: `TAX_CREDIT` (asset), `CLEARING`, `SHAREHOLDER_CURRENT` (asset or liability), non-header only; on the account form, account API, seed CSV (column 10) and export.
- `CloseCheckService`: `checklist(from, to)`, `checklistForMonth`, `checklistForYear`, and one query per check: `taxCredits(returnYear, asOf)`, `staleClearingBalances`, `unmovedLiabilities(asOf, months)` with `configuredUnmovedMonths()`, `invoicesOutsideReceivables`, `cutOffExceptions` (returns `CutOffFinding`), `assetProceedsWarnings` and `proceedsWarning(disposal)`.
- Checklist on the fiscal period detail page (that month) and on Penutupan Tahun Buku (that year). Read API `/api/close-checks` with scope `close-checks:read`.
- Shareholder/director current account: seed account `2.1.50 Rekening Koran Pemegang Saham/Direksi`, statement page `/statements/shareholder` (sidebar Laporan), `StatementService.generateShareholderStatement`, API `/api/close-checks/shareholder-statement`.
- Proceeds warning on the disposal record page. A `SHAREHOLDER_CURRENT` account is accepted as the proceeds account of a sale (it was asset-only).
- Alert `LIABILITY_UNMOVED`.

### Schema (V014)

`chart_of_accounts.close_check_role` with a type/header CHECK; `1.1.26 Kredit Pajak PPh 23` set to `TAX_CREDIT`; `2.1.50` inserted under `2.1` on a non-empty chart (migration fails if `2.1.50` belongs to another account or `2.1` is missing); alert rule `LIABILITY_UNMOVED` threshold 6; `close-checks:read` for device tokens holding `analysis:read`. No test migration.

### Decisions made on the owner's behalf

- Classification is an account attribute, not a configuration list: one account has at most one role, it travels with seed packs and exports, and the account form validates it.
- N months for unmoved liabilities is the threshold of the `LIABILITY_UNMOVED` alert rule, seeded 6 by V014; checklist and alert read the same value. A missing rule or a non-integer or non-positive threshold is an `ERROR` on the checklist and an exception elsewhere. 6 is a business choice, not a regulation.
- Unmoved: every LIABILITY account with a non-zero balance at the period end whose last posted line is on or before the period end minus N months.
- Tax credits: a checklist for a period in year Y checks the PPh Badan return of Y-1. It applies once that return is FILED, LATE or CORRECTED with a filed date on or before the period end (earliest such date). Remaining = debit balance at 31 December Y-1 minus credits posted from 1 January Y to the period end. PPN Masukan is not a tax credit for this check (it is settled monthly).
- Clearing: balance at the period end and no movement on the reducing side within the period. Partial clearing passes. No account gets the role by default.
- Invoices outside receivables: SENT, PARTIAL, PAID, OVERDUE invoices dated in the period; posted debits to the CompanyConfig receivable account from journals with the invoice as source document, dated up to the period end, must reach `amount + taxAmount`. AR booked by a manual journal without the source link is reported.
- Cut-off: a posted journal dated after the period end whose invoice date, bill date or faktur date lies in the period. Payments are not checked.
- "Personal account": a disposal (not cancelled) with proceeds whose proceeds account has role `SHAREHOLDER_CURRENT`, or is not the GL account of an active `CompanyBankAccount`. Cash (Kas) therefore warns.
- Statement balance is credit minus debit; one account per shareholder or director.
- Checklist is informational; it does not block month close or year-end closing.
- Permissions: checklist under the page's existing permission (`FISCAL_PERIOD_VIEW`, `REPORT_VIEW` for year-end closing); statement `REPORT_VIEW`; scope `close-checks:read` granted by `FISCAL_PERIOD_VIEW`.

### Unverified regulatory values

None.

### Cut

- No functional test of the disposal record warning; the service test covers the rule the page calls.
- No print/PDF for the shareholder statement.

### Noted, not fixed

- `DataExportService` writes `created_at` in column 7 of the chart of accounts CSV, which `DataImportService` reads as `is_permanent`.

### Deploy

V014 runs on deploy. Before deploy, check that `2.1.50` is unused or already named `Rekening Koran Pemegang Saham/Direksi`; otherwise the migration fails. After deploy:
1. Mark clearing accounts with the role Akun antara, and other tax credit accounts (PPh 22, PPh 25 prepayments) with Kredit pajak.
2. Create one shareholder current account per shareholder or director if more than one is needed.
3. Review the `LIABILITY_UNMOVED` threshold under Peringatan > Konfigurasi; the first evaluation lists every old liability balance.
