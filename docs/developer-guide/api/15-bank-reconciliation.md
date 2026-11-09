# Bank Reconciliation API

Import bank statements (CSV), reconcile them against posted journals on the bank's GL account, and read the reconciliation reports.

All endpoints require `Authorization: Bearer <token>`. Reads need `bank-recon:read`, writes need `bank-recon:write`. See [Authentication](01-authentication.md#scopes-and-authorization).

Base URL: `/api/bank-reconciliation`

## Parser Configs and Statements

| Method | Path | Result |
|--------|------|--------|
| GET | `/parser-configs` | Active configs: `id`, `bankType`, `configName`, `description` |
| POST | `/parser-configs` | `201` with the config. Body: `bankType`, `configName`, `dateColumn`, `descriptionColumn`, `debitColumn`, `creditColumn`, `balanceColumn` (0-based), `dateFormat`, `delimiter`, `skipHeaderRows`, `encoding`, `decimalSeparator`, `thousandSeparator`. `400` on a duplicate `configName` |
| POST | `/statements/import` | Multipart: `bankAccountId`, `parserConfigId`, `periodStart`, `periodEnd`, `openingBalance`, `closingBalance`, `file`. `201` with `id`, `totalItems`, `totalDebit`, `totalCredit`. `400` on an unparseable row (message names the line) or a file without data rows |
| GET | `/statements/{id}` | Statement header |
| GET | `/statements/{id}/items[?matchStatus=]` | Lines: `id`, `lineNumber`, `transactionDate`, `description`, `debitAmount`, `creditAmount`, `balance`, `matchStatus` (`UNMATCHED`, `MATCHED`, `BANK_ONLY`), `matchType`. `404` for an unknown statement |

## Reconciliations

| Method | Path | Result |
|--------|------|--------|
| POST | `/reconciliations` | Body `bankStatementId`, `notes`. `201`, status `DRAFT`, `bookBalance` from posted journals on the bank's GL account up to `periodEnd`, `bankBalance` from the statement. `400` when the bank account has no GL account |
| GET | `/reconciliations/{id}` | Header with counts: `totalStatementItems`, `matchedCount`, `unmatchedBankCount`, `unmatchedBookCount` |
| POST | `/reconciliations/{id}/auto-match` | `matchCount`. Passes: exact (same date and amount), fuzzy date (±1 day), keyword (±3 days, description overlap) |
| POST | `/reconciliations/{id}/match` | Body `statementItemId`, `transactionId`. Manual match |
| POST | `/reconciliations/{id}/mark-bank-only` | Body `statementItemId`, `notes` |
| POST | `/reconciliations/{id}/mark-book-only` | Body `transactionId`, `notes` |
| POST | `/reconciliations/{id}/create-transaction` | Body `statementItemId`, `templateId`, `description` (statement text when blank). Creates a DRAFT transaction for the line's date and amount and matches it |
| GET | `/reconciliations/{id}/items` | Every reconciliation item: `id`, `matchStatus`, `matchType`, `matchConfidence`, `notes`, `statementItemId`, `transactionId` |
| DELETE | `/reconciliations/{id}/items/{itemId}` | `204`. Removes the item and resets its statement line to `UNMATCHED` |
| GET | `/reconciliations/{id}/unmatched-book` | Posted transactions on the GL account in the period that are not matched or marked: `id`, `transactionDate`, `description`, `amount` |
| POST | `/reconciliations/{id}/complete` | Status `COMPLETED`. `409` while statement lines are `UNMATCHED` |

Rules for the write endpoints:

- `statementItemId` must be a line of the reconciliation's own statement, else `404`. The line must be `UNMATCHED`, else `409`; unmatch it first.
- `transactionId` must be listed by `/unmatched-book` (posted on the bank's GL account within the period, not yet matched or marked), else `400`.
- `itemId` must belong to the reconciliation in the path, else `404`.
- Every write on a `COMPLETED` reconciliation returns `409`.

## Reports

| Method | Path | Result |
|--------|------|--------|
| GET | `/reconciliations/{id}/summary` | `matchedCount`, `bankOnlyCount`, `bookOnlyCount`, `totalItems`, `matchedAmount`, `bankOnlyAmount`, `bookOnlyAmount`, `bookBalance`, `bankBalance` |
| GET | `/reconciliations/{id}/statement` | Book balance adjusted by bank-only lines, bank balance adjusted by book-only transactions, and the difference |
| GET | `/reconciliations/{id}/outstanding` | `BANK_ONLY` and `BOOK_ONLY` items, same fields as `/items` |
