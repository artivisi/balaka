# Tax Self-Test API

Read-only tax-office self-test (issue #45): yearly cross-checks with book figure, tax figure, difference and the user's explanation per line, and the rows behind each figure. Explanations are written on the web page `/reports/self-test`.

All endpoints require `Authorization: Bearer <token>` and the `self-test:read` scope (effective with `TAX_REPORT_VIEW`). See [Authentication](01-authentication.md#scopes-and-authorization). V015 adds `self-test:read` to existing device-flow tokens that hold `tax-export:read`; API clients get no new scope.

Base URL: `/api/self-test`

## Endpoints

| Method | Path | Result |
|--------|------|--------|
| GET | `?year=` | `year`, `unitemisedThreshold`, `lines[]`. `400` without `year` |
| GET | `/trace?year=&line=&month=` | `year`, `line`, `month`, `bookFigure`, `taxFigure`, `bookRows[]`, `taxRows[]`. `bookRows` add up to `bookFigure`, `taxRows` to `taxFigure`. `400` for a month not valid for the line or an unknown line; `409` when the line is `NOT_CONFIGURED` |

`lines[]` entry: `line`, `month` (0 = whole year), `title`, `bookBasis`, `taxBasis`, `bookFigure`, `taxFigure`, `difference`, `status`, `message`, `explanation`, `explainedBy`, `explainedAt`. `difference` = `bookFigure - taxFigure`, or `bookFigure` when the line has no tax figure. Per-masa lines come as the month 0 total followed by months 1-12.

`status`: `MATCHED`, `DIFFERENCE`, `MOVEMENT` (no tax figure, nonzero movement), `NO_ACTIVITY`, `NOT_CONFIGURED` (no account carries the role; figures null, `message` names the role).

Trace row: `source`, `referenceId`, `transactionId`, `transactionNumber`, `date`, `accountCode`, `reference`, `description`, `amount`.

| `source` | `referenceId` | Notes |
|---|---|---|
| `JOURNAL_LINE` | journal entry id | `reference` is the journal number |
| `REVENUE_TRANSACTION` | transaction id | Revenue of one transaction (credit minus debit on REVENUE accounts) |
| `FAKTUR` | tax detail id | `reference` faktur number, `amount` DPP |
| `BUPOT` | tax detail id | `reference` bupot number, `amount` gross |
| `PAYROLL_DETAIL` | payroll detail id | `reference` employee id, `amount` taxable gross |
| `ASSET` | fixed asset id | `reference` asset code, `amount` cost |

## Lines

Ledger figures count POSTED, non-closing journal lines dated in the period.

| `line` | Per masa | Book figure | Tax figure |
|---|---|---|---|
| `REVENUE_VS_PPN_DPP` | yes | `SalesTaxChainService` reconciliation `bookedRevenue` | `sptDpp`: numbered PPN Keluaran details on POSTED transactions by faktur date |
| `SALARY_VS_PPH21_DPP` | yes | `PayrollReconciliationService` `ledgerSalaryExpense` | `runTaxableIncome` of a POSTED run, else 0 with `message` |
| `SERVICE_VS_PPH23_DPP` | no | Debit minus credit on `SERVICE_PPH23` accounts | Gross of PPh 23 details in the e-Bupot Unifikasi export (customer bupot on invoices excluded) |
| `RENT_VS_PPH42_DPP` | no | Debit minus credit on `RENT_PPH42` accounts | Gross of PPh 4(2) details in the same export |
| `FIXED_ASSET_ADDITIONS` | no | Debits on register cost accounts (category or asset `assetAccount`) | `AssetRegisterQueryService.costMovement` additions |
| `FIXED_ASSET_DISPOSALS` | no | Credits on the same accounts | `costMovement` disposals |
| `CAPITAL_MOVEMENT` | no | Credit minus debit on every EQUITY account | null |
| `NON_OPERATING_ASSET_MOVEMENT` | no | Debit minus credit on `NON_OPERATING_ASSET` accounts | null |
| `UNITEMISED_OTHER_EXPENSE` | no | Debit minus credit on `OTHER_EXPENSE` accounts | Book figure less unitemised lines: debit lines of at least `unitemisedThreshold` whose transaction has a blank description or no evidence item (`EvidenceQueryService`) |

Account roles are set on the chart of accounts (`selfTestRole` on `/api/accounts`: `SERVICE_PPH23`, `RENT_PPH42`, `OTHER_EXPENSE` on expense accounts; `NON_OPERATING_ASSET` on asset accounts).
