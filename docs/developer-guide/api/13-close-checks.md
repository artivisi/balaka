# Close Checks API

Read-only period close checks and the shareholder/director current account statement (issue #50). Account roles are set on the chart of accounts (`closeCheckRole` on `/api/accounts`: `TAX_CREDIT`, `CLEARING`, `SHAREHOLDER_CURRENT`).

All endpoints require `Authorization: Bearer <token>` and the `close-checks:read` scope (effective with `FISCAL_PERIOD_VIEW`). See [Authentication](01-authentication.md#scopes-and-authorization). V014 adds `close-checks:read` to existing device-flow tokens that hold `analysis:read`; API clients get no new scope.

Base URL: `/api/close-checks`

## Endpoints

| Method | Path | Result |
|--------|------|--------|
| GET | `?from=&to=` | Checklist: `from`, `to`, `checks[]` in fixed order. `400` if a date is missing or `to` is before `from` |
| GET | `/tax-credits?returnYear=&asOf=` | `returnYear`, `asOf`, `filedDate`, `uncleared[]` (`accountId`, `accountCode`, `accountName`, `balanceAtYearEnd`, `clearedAfterYearEnd`, `uncleared`). `filedDate` null and `uncleared` empty when no reported PPh Badan return of `returnYear` is filed on or before `asOf` |
| GET | `/clearing?from=&to=` | `CLEARING` accounts with a balance at `to` and no reducing movement in the period: `balance` (debit minus credit), `lastMovement` |
| GET | `/unmoved-liabilities?asOf=` | `asOf`, `months` (LIABILITY_UNMOVED alert threshold), `accounts[]` with `balance` (credit minus debit) and `lastMovement` |
| GET | `/invoices-outside-receivables?from=&to=` | Issued invoices dated in the period: `invoiceTotal`, `postedToReceivable`. `409` when the receivable account is not configured |
| GET | `/cut-off?from=&to=` | Posted journals dated after `to` for documents dated in the period: `source` (`INVOICE`, `BILL`, `FAKTUR`), `documentNumber`, `documentDate`, `transactionNumber`, `transactionDate`, `amount` |
| GET | `/asset-proceeds?from=&to=` | Disposals with proceeds to a non-company account: `issue` (`SHAREHOLDER_ACCOUNT`, `NOT_COMPANY_BANK_ACCOUNT`), `accountCode`, `proceeds` |
| GET | `/shareholder-statement?accountId=&from=&to=` | `openingBalance`, `lines[]` (`date`, `transactionId`, `transactionNumber`, `description`, `debit`, `credit`, `balance`), `totalDebit`, `totalCredit`, `closingBalance`. Balance is credit minus debit. `400` if the account is not `SHAREHOLDER_CURRENT` |

## Checklist

Each `checks[]` entry: `code`, `title`, `status`, `message`, `findings[]` (`reference`, `date`, `description`, `amount`, `transactionId`). Status `PASS`, `WARNING`, `NOT_APPLICABLE` or `ERROR`; `ERROR` names the missing configuration in `message`.

| Code | Rule |
|------|------|
| `TAX_CREDIT_UNCLEARED` | Return year is the year before `from`. `NOT_APPLICABLE` until that PPh Badan return is FILED, LATE or CORRECTED with a filed date on or before `to`; `ERROR` without a `TAX_CREDIT` account |
| `CLEARING_NOT_CREDITED` | `NOT_APPLICABLE` without a `CLEARING` account |
| `LIABILITY_UNMOVED` | Every LIABILITY account; last posted line on or before `to` minus N months. `ERROR` when the alert rule is missing or its threshold is not a whole number of at least 1 |
| `INVOICE_NOT_IN_RECEIVABLES` | SENT, PARTIAL, PAID, OVERDUE invoices; debits to the configured receivable account from posted journals with the invoice as source document, dated up to `to` |
| `CUT_OFF` | Invoice date, bill date or faktur date in the period, posted journal after `to` |
| `ASSET_PROCEEDS_PERSONAL` | Non-cancelled disposals in the period with proceeds; company accounts are the GL accounts of active company bank accounts |

Only posted journals count.
