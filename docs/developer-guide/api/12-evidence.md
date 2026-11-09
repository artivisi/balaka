# Evidence API

Read-only access to evidence requirements, per-transaction evidence and gaps, the contract register and missing bank statements. Rules, contracts and statement archives are maintained in the web UI.

All endpoints require `Authorization: Bearer <token>` and the `evidence:read` scope (effective with `EVIDENCE_VIEW`). See [Authentication](01-authentication.md#scopes-and-authorization). V013 adds `evidence:read` to existing device-flow tokens that hold `analysis:read`; API clients get no new scope.

Base URL: `/api/evidence`

## Endpoints

| Method | Path | Result |
|--------|------|--------|
| GET | `/requirements` | Every rule: `id`, `evidenceType`, `accountId`/`accountCode`/`accountName` or `templateId`/`templateName`, `minAmount` (null: every amount), `notes`, `label` |
| GET | `/transactions/{id}` | Evidence of one transaction, any status. `404` if unknown |
| GET | `/gaps?from=&to=` | Posted, non-closing transactions in the period with at least one required type missing, by date then number. `400` if `to` is before `from` |
| GET | `/index?from=&to=` | Every posted, non-closing transaction in the period, complete or not. The audit pack's evidence index |
| GET | `/contracts[?clientId=][&vendorId=]` | Contract register, newest start date first |
| GET | `/bank-statements/missing?asOf=&graceDays=` | Months without an ACTIVE archive, per active bank account. A month is due when `asOf` is later than its end plus `graceDays`. `400` on negative `graceDays` |

## Rule evaluation

A rule applies when the transaction amount is at least `minAmount` and either a journal line is on the rule's account or one of its sub-accounts, or the transaction's template is in the rule's template family (the first version and every later version). Evidence types: `CONTRACT`, `INVOICE`, `PROOF_OF_PAYMENT`, `BUPOT`. `OTHER` classifies a file and cannot be required.

## Transaction evidence

| Field | Content |
|-------|---------|
| `required[]` | `evidenceType` and `rules[]` (labels of the rules asking for it) |
| `evidence[]` | `evidenceType`, `source`, `reference`, `documentId` (null when no file backs it) |
| `missing[]` | Required types with no evidence, in enum order |
| `files[]` | Files on the transaction, its journal lines and its invoice: `documentId`, `filename`, `evidenceType` (null: unclassified), `fileSize`, `checksumSha256` |
| `notes[]` | Contract linked without a file; unclassified files while something is missing |

| `source` | Satisfies | When |
|----------|-----------|------|
| `ATTACHMENT` | Its `evidenceType` | Classified file on the transaction, a journal line or the linked invoice |
| `INVOICE` | `INVOICE` | Invoice with `id_transaction` = the transaction, or the transaction's `source_document_type = 'INVOICE'` |
| `CONTRACT` | `CONTRACT` | Contract linked to the transaction or its invoice, with a file |
| `BUPOT` | `BUPOT` | Non-blank `bupot_number` on the transaction's tax detail |
| `BANK_MATCH` | `PROOF_OF_PAYMENT` | Bank statement line with status `MATCHED` to the transaction |

## Missing statement

`bankAccountId`, `bankName`, `accountName`, `month` (`yyyy-MM`). Expected months start at the earlier of the first posted journal line on the account's GL account and the first archived month. An account with neither has no expected month.

## Service API

`EvidenceQueryService` exposes the same data in-process: `requirements()`, `evidenceFor(transactionId)`, `index(from, to)`, `gaps(from, to)`, `contracts(clientId, vendorId)`, `contractsActiveOn(date)`. Missing statements: `BankStatementArchiveService.missingStatements(asOf, graceDays)`.
