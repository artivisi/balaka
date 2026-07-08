# Fixed Assets API

Register and manage depreciable fixed assets. Registered assets are picked up by the monthly depreciation scheduler, so purchases registered through this API get auto-generated depreciation entries (PENDING by default, posted automatically when `autoPost` is true).

All endpoints require `Authorization: Bearer <token>`. Read endpoints need the `assets:read` scope; write endpoints need `assets:write`.

Base URL: `/api/fixed-assets`

## Acquisition Sources

`POST /api/fixed-assets` requires exactly one acquisition source:

| Source | Fields | Effect |
|--------|--------|--------|
| Funding account | `fundingAccountId` or `fundingAccountCode` | Composes an acquisition DRAFT journal (Dr asset account / Cr funding account) via the template engine |
| Existing transaction | `purchaseTransactionId` | Links an already-recorded purchase journal to the asset. No new journal is created — use this when the purchase was posted before registration |

Providing both, or neither, returns `400`.

## List Asset Categories

```http
GET /api/fixed-assets/categories
```

Returns active categories (kelompok). Each category supplies the default depreciation method, useful life, rate, and the asset / accumulated-depreciation / expense accounts.

```json
[
  {
    "id": "…",
    "code": "KOMPUTER",
    "name": "Peralatan Komputer",
    "depreciationMethod": "STRAIGHT_LINE",
    "usefulLifeMonths": 48,
    "depreciationRate": null,
    "assetAccountCode": "1.2.01",
    "accumulatedDepreciationAccountCode": "1.2.02",
    "depreciationExpenseAccountCode": "5.1.12"
  }
]
```

## List Fixed Assets

```http
GET /api/fixed-assets?year=2026&categoryId=<uuid>&status=ACTIVE&page=0&size=20
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `year` | int | no | Filter by purchase year |
| `categoryId` | UUID | no | Filter by asset category |
| `status` | enum | no | `ACTIVE`, `FULLY_DEPRECIATED`, `DISPOSED` |
| `page`, `size` | int | no | Pagination (default size 20) |

## Get Asset Detail

```http
GET /api/fixed-assets/{id}
```

Returns full asset data including account codes, `monthlyDepreciation`, `accumulatedDepreciation`, `bookValue`, `depreciationPeriodsCompleted`, and `purchaseTransactionId` (when linked).

## Register Asset

```http
POST /api/fixed-assets
```

```json
{
  "assetCode": "AST-2026-001",
  "name": "MacBook Air 13 M5 16GB/1TB",
  "categoryCode": "KOMPUTER",
  "purchaseDate": "2026-06-10",
  "purchaseCost": 24000000,
  "supplier": "iBox",
  "invoiceNumber": "SX1752606000155",
  "serialNumber": "SJL9L0MVNCJ",
  "purchaseTransactionId": "…"
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `assetCode` | yes | Unique, max 30 chars |
| `name` | yes | Max 255 chars |
| `categoryId` or `categoryCode` | yes (exactly one) | Asset category (kelompok) |
| `purchaseDate` | yes | ISO date |
| `purchaseCost` | yes | Positive amount |
| `fundingAccountId` / `fundingAccountCode` / `purchaseTransactionId` | yes (see Acquisition Sources) | |
| `depreciationMethod` | no | `STRAIGHT_LINE` or `DECLINING_BALANCE`; defaults from category |
| `usefulLifeMonths` | no | 1–600; defaults from category |
| `depreciationRate` | no | Annual % for declining balance; defaults from category |
| `residualValue` | no | Defaults to 0 |
| `depreciationStartDate` | no | Defaults to the first day of the purchase month |
| `autoPost` | no | When true, the scheduler posts generated depreciation entries directly; default false (entries stay PENDING for review) |
| `description`, `supplier`, `invoiceNumber`, `serialNumber`, `location`, `notes` | no | Metadata |

Response `201` with the asset detail (same shape as GET detail).

## Update Asset

```http
PUT /api/fixed-assets/{id}
```

Same body as register (acquisition-source fields are ignored — funding/purchase linkage is immutable). Assets with recorded depreciation only accept `name`, `description`, `location`, `serialNumber`, and `notes` changes; other fields are ignored in that state. Disposed assets return `409`.

## Delete Asset

```http
DELETE /api/fixed-assets/{id}
```

Returns `204`. Only allowed for assets without depreciation history and without a purchase transaction; otherwise `409`. Assets with history should be disposed through the web disposal flow instead.

## Depreciation Behavior

- Depreciation entries are generated monthly by the scheduler for `ACTIVE` assets whose `depreciationStartDate` has passed.
- Entries are created as PENDING and reviewed in the web UI (`/assets/depreciation`), or posted automatically when the asset has `autoPost: true`.
- Straight line: `(purchaseCost - residualValue) / usefulLifeMonths`. Declining balance: `bookValue × (depreciationRate / 12 / 100)`.
