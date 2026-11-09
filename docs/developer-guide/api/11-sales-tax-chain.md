# Sales Tax Chain API

Chain from invoice to faktur, receipt and bupot; exception report; monthly reconciliation of booked revenue to the DPP of SPT Masa PPN.

All endpoints require `Authorization: Bearer <token>`. Read endpoints need the `sales-tax-chain:read` scope (effective with `TAX_REPORT_VIEW`). Write endpoints need `sales-tax-chain:write` plus the permissions listed below. See [Authentication](01-authentication.md#scopes-and-authorization).

Base URL: `/api/sales-tax-chain`

## Source of truth

Faktur and bupot numbers, dates and amounts live on `tax_transaction_details` only. A detail belongs to an invoice through its transaction: `source_document_type = 'INVOICE'` with `source_document_id`, or `invoices.id_transaction`. A detail without a number is not a faktur or bupot. Details entered through `/api/transactions/{id}/tax-details` on such a transaction are part of the chain.

## Endpoints

| Method | Path | Permission | Result |
|--------|------|------------|--------|
| GET | `/invoices/{id}` | read scope | Transactions, faktur, receipts, bupot and open exceptions of one invoice |
| GET | `/exceptions?to=[&from=][&type=]` | read scope | Open exceptions dated up to `to`, oldest first |
| GET | `/reconciliation?year=&month=` | read scope | Reconciliation of one masa |
| POST | `/invoices/{id}/faktur` | `TAX_EXPORT`, `INVOICE_EDIT` | `201`. Detail on the first non-void revenue transaction. `409` if the invoice already has a faktur or has no revenue transaction |
| POST | `/invoices/{id}/bupot` | `TAX_EXPORT`, `INVOICE_EDIT`, `TRANSACTION_CREATE` | `201`. DRAFT transaction Dr Kredit Pajak PPh 23, Cr Piutang with its detail. Post through `/api/transactions/{id}/post` |

Faktur body: `fakturNumber`, `fakturDate`, `transactionCode`, `dpp`, `ppn`, all required.

Bupot body: `bupotNumber`, `bupotDate`, `taxObjectCode`, `grossAmount`, `taxRate`, `taxAmount` required; `paymentId` optional. `bupotDate` becomes the transaction date. The detail stays out of the Bupot Unifikasi export because the customer issued it.

## Exceptions

| `type` | Row appears when | `amount` | `date` |
|--------|------------------|----------|--------|
| `PAID_NO_FAKTUR` | Issued invoice with `taxAmount > 0`, a payment or status PAID, and no numbered faktur on a non-void transaction | Invoice total | First payment, otherwise paid date |
| `FAKTUR_NO_REVENUE` | Numbered PPN Keluaran detail whose invoice, or whose transaction outside an invoice, has no net posted revenue. Includes voided transactions | DPP | Faktur date |
| `DUPLICATE_FAKTUR` | Invoice with more than one numbered faktur on non-void transactions. `reference` lists the numbers | Sum of DPP | Latest faktur date |
| `MISSING_BUPOT` | Withheld PPh 23 on a receipt not covered by non-void numbered bupots; or an unnumbered PPh 23 detail on a posted invoice or revenue transaction; or a posted debit on Kredit Pajak PPh 23 with no PPh 23 detail | Open amount | Payment or transaction date |

A bupot with no `paymentId` covers the invoice's oldest receipts. Vendor withholding rows are excluded.

## Reconciliation

`bookedRevenue + sum(lines[].amount) = sptDpp`. `unreconciled` reports any gap.

- `bookedRevenue`: posted net credits on REVENUE accounts dated in the masa, closing entries excluded.
- `sptDpp`: DPP of numbered PPN Keluaran details on POSTED transactions with `fakturDate` in the masa.
- `lines[].type`: `PPN_IN_REVENUE`, `PPH23_NETTED_FROM_REVENUE`, `REVENUE_NO_FAKTUR`, `FAKTUR_IN_OTHER_MASA`, `REVENUE_IN_OTHER_MASA`, `FAKTUR_NO_REVENUE`, `DPP_NILAI_LAIN`, `OTHER_DIFFERENCE`. Each line lists `items` per invoice, or per transaction outside an invoice. PPN and PPh 23 lines require an exact match of the difference; anything else is `OTHER_DIFFERENCE`.
- `taxEffects`: `ppnPerFaktur`, `ppnPerLedger` (output tax account from company configuration), `ppnDifference`, `invoiceValue` (DPP + PPN), `pph23PerReceipts`, `pph23PerBupot` (posted), `pph23PerLedger` (`Kredit Pajak PPh 23`), `pph23Difference`, `netOfPph23`.
- `exportedDpp`, `exportDifference`: DPP of the e-Faktur export for the same dates minus `sptDpp`. The export selects by transaction date and includes unnumbered rows.
- `filings`: the SPT Masa PPN rows of the filing register for the masa. The register holds no DPP, so `sptDpp` is computed from this application's data.

A missing output tax account in company configuration, or zero or several accounts named `Kredit Pajak PPh 23`, returns an error on `/reconciliation` and `/exceptions`.
