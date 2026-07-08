# Transactions API

All transaction endpoints require `Authorization: Bearer <token>` with the `transactions:post` scope.

Base URL: `/api/transactions`

## Transaction Lifecycle

```
DRAFT ──POST──> POSTED ──VOID──> VOID
  │                                
  └──DELETE──> (removed)          
```

- **DRAFT**: created but not yet affecting the ledger. Can be edited or deleted.
- **POSTED**: affects the ledger. Journal entries are generated and numbered. Can only be voided.
- **VOID**: reversed. A reversal journal entry is created. Cannot be modified further.

## Create Transaction (Direct Post)

Creates and immediately posts a transaction. Used by AI assistants after user approval.

```http
POST /api/transactions
Content-Type: application/json
Authorization: Bearer <token>
```

```json
{
  "templateId": "e0000000-0000-0000-0000-000000000001",
  "merchant": "Toko Bangunan",
  "amount": 1500000,
  "transactionDate": "2025-12-15",
  "description": "Office renovation materials",
  "category": "office-supplies",
  "source": "claude-code",
  "userApproved": true,
  "accountSlots": {
    "BANK": "648eaabb-1234-5678-9abc-def012345678"
  },
  "variables": {
    "assetCost": 1500000
  }
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `templateId` | UUID | yes | Journal template to use |
| `merchant` | string | yes | Merchant/vendor name |
| `amount` | decimal | yes | Transaction amount (> 0) |
| `transactionDate` | date | yes | Format: `yyyy-MM-dd` |
| `description` | string | yes | Transaction description |
| `category` | string | no | Optional category |
| `items` | string[] | no | Line item descriptions |
| `source` | string | no | Source identifier (e.g., `claude-code`) |
| `userApproved` | boolean | no | Whether user approved the transaction |
| `accountSlots` | map | no | Account overrides for template lines with `accountHint`. Key = hint or line order number, value = account UUID |
| `variables` | map | no | Formula variables for DETAILED templates. Key = variable name, value = amount |

Response: `201 Created` with `TransactionResponse`.

## Idempotent Posting (Retry Safety)

`POST /api/transactions` accepts an optional `Idempotency-Key` header (max 100
chars, caller-generated, unique per logical posting). The key is persisted with
the transaction in the same database commit as the posting, so a dispatcher
retry after a lost response cannot double-post:

```http
POST /api/transactions
Authorization: Bearer <token>
Idempotency-Key: journal-posting-42
Content-Type: application/json
```

- First request: `201 Created` with the `TransactionResponse`.
- Repeat with the same key: `200 OK` with the original transaction (id,
  number, journal entries) — nothing new is created, whatever the payload.
- Concurrent duplicates race on a unique index; the loser returns the winner's
  transaction with `200 OK`.

Outbox dispatchers should use a stable identifier of their own posting row as
the key, so every logical event maps to exactly one GL transaction across
retries.

## Create Draft Transaction

Creates a DRAFT transaction via the draft workflow.

```http
POST /api/drafts
Content-Type: application/json
Authorization: Bearer <token>
```

```json
{
  "templateId": "e0000000-0000-0000-0000-000000000001",
  "description": "Monthly electricity bill",
  "amount": 750000,
  "transactionDate": "2025-12-01",
  "accountSlots": {
    "BANK": "648eaabb-1234-5678-9abc-def012345678"
  }
}
```

Response: `201 Created` with `TransactionResponse` (status = `DRAFT`).

## Create Free-Form Journal Entry

Creates a DRAFT transaction with arbitrary debit/credit lines. No template required. Use for closing journals, adjusting entries, or opening balances.

```http
POST /api/transactions/journal-entry
Content-Type: application/json
Authorization: Bearer <token>
```

```json
{
  "transactionDate": "2025-12-31",
  "description": "Closing journal - accrued expenses",
  "category": "CLOSING",
  "lines": [
    {
      "accountId": "a1b2c3d4-...",
      "debit": 1000000,
      "credit": 0
    },
    {
      "accountId": "e5f6a7b8-...",
      "debit": 0,
      "credit": 1000000
    }
  ]
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `transactionDate` | date | yes | Format: `yyyy-MM-dd` |
| `description` | string | yes | Journal entry description (max 500 chars) |
| `category` | string | no | Optional category stored in transaction notes |
| `lines` | array | yes | Minimum 2 lines. Total debits must equal total credits |
| `lines[].accountId` | UUID | yes | Chart of account UUID (must be a leaf account, not a header) |
| `lines[].debit` | decimal | yes | Debit amount (>= 0) |
| `lines[].credit` | decimal | yes | Credit amount (>= 0) |

Response: `201 Created` with `TransactionResponse` (status = `DRAFT`). Post via `POST /api/transactions/{id}/post`.

Validation errors (`400 Bad Request`):
- Unbalanced debits and credits.
- Less than 2 lines.
- Header account used (only leaf accounts allowed).
- A line has both debit and credit as zero or both non-zero.

## Update Draft Transaction

Updates a DRAFT transaction. All fields are optional; only provided fields are updated.

```http
PUT /api/transactions/{id}
Content-Type: application/json
Authorization: Bearer <token>
```

```json
{
  "templateId": "e0000000-0000-0000-0000-000000000002",
  "description": "Updated description",
  "amount": 800000,
  "transactionDate": "2025-12-02",
  "accountSlots": {
    "BANK": "new-account-uuid"
  }
}
```

Response: `200 OK` with updated `TransactionResponse`.

Returns `409 Conflict` if the transaction was modified concurrently (optimistic locking via `row_version`).

## Post a Draft Transaction

```http
POST /api/transactions/{id}/post
Authorization: Bearer <token>
```

Response: `200 OK` with `TransactionResponse` (status = `POSTED`, `transactionNumber` assigned).

## Delete a Draft Transaction

```http
DELETE /api/transactions/{id}
Authorization: Bearer <token>
```

Response: `204 No Content`. Only DRAFT transactions can be deleted.

## Void a Posted Transaction

```http
POST /api/transactions/{id}/void
Content-Type: application/json
Authorization: Bearer <token>
```

```json
{
  "reason": "DUPLICATE",
  "notes": "Already recorded in transaction JU-202512-0001"
}
```

| Void Reason | Description |
|-------------|-------------|
| `INPUT_ERROR` | Data entry mistake |
| `DUPLICATE` | Duplicate transaction |
| `CANCELLED` | Transaction was cancelled |
| `OTHER` | Other reason (explain in notes) |

Response: `200 OK` with `TransactionResponse` (status = `VOID`).

## Journal Preview

Preview the journal entries that will be generated when posting a DRAFT transaction.

```http
GET /api/transactions/{id}/journal-preview
Authorization: Bearer <token>
```

Response:

```json
{
  "valid": true,
  "errors": [],
  "entries": [
    {
      "accountCode": "5-1200",
      "accountName": "Beban Perlengkapan Kantor",
      "debitAmount": 500000.00,
      "creditAmount": 0.00
    },
    {
      "accountCode": "1-1100",
      "accountName": "Kas",
      "debitAmount": 0.00,
      "creditAmount": 500000.00
    }
  ],
  "totalDebit": 500000.00,
  "totalCredit": 500000.00
}
```

## Bulk Post

Post multiple DRAFT transactions in a single request.

```http
POST /api/transactions/bulk-post
Content-Type: application/json
Authorization: Bearer <token>
```

```json
{
  "transactionIds": [
    "uuid-1",
    "uuid-2",
    "uuid-3"
  ]
}
```

Response:

```json
{
  "results": [
    { "transactionId": "uuid-1", "success": true, "transactionNumber": "JU-202512-0001", "error": null },
    { "transactionId": "uuid-2", "success": true, "transactionNumber": "JU-202512-0002", "error": null },
    { "transactionId": "uuid-3", "success": false, "transactionNumber": null, "error": "Transaction is not in DRAFT status" }
  ],
  "successCount": 2,
  "failureCount": 1
}
```

Individual failures do not roll back successful posts.

## Purge Voided Transactions

Permanently delete all voided transactions (optionally before a cutoff date).

```http
DELETE /api/transactions/purge-voided?before=2025-01-01
Authorization: Bearer <token>
```

Response: `200 OK` with count of purged transactions.

## Response Structure

All transaction endpoints return `TransactionResponse`:

```json
{
  "transactionId": "uuid",
  "transactionNumber": "JU-202512-0001",
  "status": "POSTED",
  "merchant": "Toko Bangunan",
  "amount": 1500000,
  "transactionDate": "2025-12-15",
  "description": "Office renovation materials",
  "journalEntries": [
    {
      "journalNumber": "JE-202512-0001",
      "accountCode": "5-1200",
      "accountName": "Beban Perlengkapan Kantor",
      "debitAmount": 1500000.00,
      "creditAmount": 0.00
    }
  ]
}
```

---

Next: [Reports](04-reports.md)
