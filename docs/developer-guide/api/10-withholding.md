# Withholding API

Payees, payments with PPh 21 bukan pegawai / PPh 23 / PPh 26 withheld, deposits per masa, and the report of service payments without a PPh tax detail.

All endpoints require `Authorization: Bearer <token>`. Read endpoints need the `withholding:read` scope (effective with `TAX_REPORT_VIEW`). Write endpoints need `withholding:write` plus the permission listed below. See [Authentication](01-authentication.md#scopes-and-authorization).

Base URL: `/api/withholding`

## Rules

| Treatment | Payee type | Base | Rate | Without NPWP/NIK | Credits |
|-----------|-----------|------|------|------------------|---------|
| `PPH_21_BUKAN_PEGAWAI` | `ORANG_PRIBADI` | 50% of gross per masa | Pasal 17 | 20% higher | `Hutang PPh 21` |
| `PPH_23` | `BADAN`, `ORANG_PRIBADI` | gross | 2% (unverified) | 100% higher (unverified) | `Hutang PPh 23` |
| `PPH_26` | `LUAR_NEGERI` | gross | 20%, or the payee's treaty rate with a valid DGT form (unverified) | no effect | `Hutang PPh 26` |

- PPh 21 brackets accumulate per payee within one masa and restart in the next. `taxAmount` on a payment is the increment it adds. PTKP does not apply.
- Hutang accounts are resolved by exact name because codes differ between seed packs. Zero or more than one match returns `409`.
- Rates are set in `application.properties` (`app.withholding.*`). A missing key fails startup.

## Payees

| Method | Path | Permission | Result |
|--------|------|------------|--------|
| GET | `/payees` | read scope | Vendors with a `payeeType`. NPWP and NIK are returned masked (`npwpMasked`, `nikMasked`) |
| POST | `/payees` | `VENDOR_CREATE` | `201` |
| PUT | `/payees/{id}` | `VENDOR_EDIT` | `200`. Replaces the listed fields |

Body: `code`, `name`, `payeeType` (required), `address`, `email`, `defaultWithholding`, `defaultTaxObjectCode`, `npwp`, `nik`, `countryCode` (required for `LUAR_NEGERI`), `foreignTin`, `treatyRate`, `dgtFormNumber`, `dgtValidUntil`.

A payee is a row in `vendors`; the same record serves bills.

## Payments

| Method | Path | Permission | Result |
|--------|------|------------|--------|
| GET | `/payments?year=&month=[&treatment=][&vendorId=]` | read scope | Every transaction status |
| GET | `/payments/{id}` | read scope | |
| POST | `/payments` | `TRANSACTION_CREATE` | `201`, DRAFT transaction with its tax detail. No journal yet |
| POST | `/payments/{id}/post` | `TRANSACTION_POST` | `200`, journal posted |
| DELETE | `/payments/{id}` | `TRANSACTION_DELETE` | `204`, DRAFT only. Later PPh 21 drafts in the masa are recomputed |

```json
{
  "vendorId": "…",
  "paymentDate": "2026-03-10",
  "grossAmount": 10000000,
  "expenseAccountId": "…",
  "bankAccountId": "…",
  "description": "Honor desain",
  "treatment": "PPH_21_BUKAN_PEGAWAI",
  "taxObjectCode": "21-100-20"
}
```

`treatment` and `taxObjectCode` take the payee's default when omitted; both absent returns `400`.

To settle an approved bill, send `billId`, `settledAmount` (includes PPN) and `paymentMethod`, and omit `expenseAccountId`. `grossAmount` stays the withholding base. The journal debits the company's Hutang Usaha. The `BillPayment` is recorded when the transaction is posted and removed when it is voided.

Response fields: `grossAmount`, `dppAmount`, `taxRate` (effective rate on DPP, rounded), `taxAmount`, `settledAmount`, `netAmount`, `hasTaxId`, `rateBasis`, `transactionId`, `transactionStatus`, `billId`.

`rateBasis` `PPH26_TREATY_DGT_INVALID` means the payee has a treaty rate but no DGT form valid on the payment date; the general rate was applied.

The transaction API refuses `PUT`/`DELETE` on these drafts (`409`). `POST /api/transactions/{id}/post` works and has the same effect as `/payments/{id}/post`. Void through `POST /api/transactions/{id}/void`; a PPh 21 payment with a later non-void payment to the same payee in the masa cannot be voided (`409`).

## Deposits

| Method | Path | Permission | Result |
|--------|------|------------|--------|
| GET | `/deposits?year=&month=` | read scope | |
| POST | `/deposits` | `TRANSACTION_CREATE` | `201`, DRAFT. `400` when the amount exceeds posted tax minus non-void deposits |
| POST | `/deposits/{id}/post` | `TRANSACTION_POST` | `200` |
| DELETE | `/deposits/{id}` | `TRANSACTION_DELETE` | `204`, DRAFT only |

Body: `treatment`, `year`, `month`, `amount`, `depositDate`, `bankAccountId`, `billingCode`, `ntpn`.

These rows cover tax withheld through this API. PPh 21 on payroll has its own deposit template.

## Reports

`GET /masa-status?year=&month=[&asOf=]` returns one row per treatment: `gross`, `withheld`, `draftWithheld`, `deposited`, `outstanding`, `dueDate`, `lastDepositDate`, `status`. `asOf` is the date overdue is judged against and defaults to today. A deadline that is missing or inactive in the tax calendar returns `409`.

| `status` | Meaning |
|----------|---------|
| `NOTHING_DUE` | Nothing withheld or deposited |
| `OPEN` | Outstanding, due date not passed |
| `OVERDUE` | Outstanding, due date passed |
| `ON_TIME` | Fully deposited, last deposit on or before the due date |
| `LATE` | Fully deposited, last deposit after the due date |
| `OVERDEPOSITED` | Deposits exceed posted tax, for example after a void |

`GET /undocumented-service-payments?year=&month=` returns `rows` (posted net debits on accounts with `serviceExpense = true` on transactions with no PPh 21/23/26/4(2) tax detail), `total`, and `unclassifiedAccounts` (expense accounts with activity in the masa and `serviceExpense = null`, which the report cannot judge). A PPN detail does not count. A bill's accrual is excluded once the bill has a non-void withholding payment.

Set `serviceExpense` through `POST`/`PUT /api/accounts`.

## Exports

| Path | Content |
|------|---------|
| `GET /api/tax-export/bp21?startMonth=&endMonth=` | PPH_21 tax details on posted transactions. Scope `tax-export:read` |
| `GET /api/tax-export/bupot-unifikasi?startMonth=&endMonth=` | PPH_23, PPH_26, PPH_42. Sheet `PPH26` adds country, foreign TIN and DGT number. PPh 21 moved to BP21 |

Column layouts are not verified against the current Coretax converter.

## Service Use

Later features read this data through `WithholdingQueryService`: `findPayees()`, `findPayment(id)`, `findPayments(year, month, treatment, vendorId)`, `findDeposit(id)`, `findDeposits(year, month)`, `getMasaStatus(year, month, asOf)`, `findUndocumentedServicePayments(year, month)`.
