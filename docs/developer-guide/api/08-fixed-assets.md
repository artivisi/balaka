# Fixed Assets API

Register and manage depreciable fixed assets. Registered assets are picked up by the monthly depreciation scheduler, so purchases registered through this API get auto-generated depreciation entries (PENDING by default, posted automatically when `autoPost` is true).

All endpoints require `Authorization: Bearer <token>`. Read endpoints need the `assets:read` scope; write endpoints need `assets:write`. The token user's role must also hold the operation's permission: register `ASSET_CREATE`, update `ASSET_EDIT`, delete `ASSET_DELETE`, depreciation generate/post/skip/post-all `ASSET_DEPRECIATE` (see [Authentication](01-authentication.md#scopes-and-authorization)).

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

Same body as register (acquisition-source fields are ignored — funding/purchase linkage is immutable). Assets with recorded depreciation only accept `name`, `description`, `notes`, and identity changes; other fields are ignored in that state. Disposed assets and assets with a draft disposal return `409`.

Identity fields, on register and update: `brand`, `model`, `serialNumber`, `custodian`, `location`, `condition` (`GOOD`, `MINOR_DAMAGE`, `MAJOR_DAMAGE`). All optional; on update an absent one clears it. A change of custodian, location or condition writes a register movement. The detail response adds these plus `lastVerifiedDate`, `hasPhoto`, `disposalDate`, `disposalType`, `disposalProceeds`, `gainLossOnDisposal`. Photo upload is web only.

## Delete Asset

```http
DELETE /api/fixed-assets/{id}
```

Returns `204`. For a registration that never reached the ledger: no depreciation history, no linked purchase transaction, no posted acquisition journal, no disposal record. Its acquisition DRAFT is removed too. Otherwise `409`; use the disposal endpoints below.

## Disposal

Reads need `assets:read`; writes need `assets:write` and `ASSET_DISPOSE`.

| Endpoint | Description |
|----------|-------------|
| `GET /{id}/disposal/preview?type=&proceeds=` | Tax effect, nothing stored. `400` for `SOLD` without proceeds or proceeds on another type |
| `POST /{id}/disposal` | Create the record and its DRAFT journal (`201`). `409` when the asset state does not allow it |
| `POST /disposals/{disposalId}/cancel` | Remove the DRAFT journal, record becomes `CANCELLED`. `409` unless `DRAFT` |
| `GET /disposals/{disposalId}` | One record |
| `GET /disposals?year=&status=` | Records dated in the year; `status` optional |

```json
{
  "type": "SOLD",
  "date": "2026-03-31",
  "proceeds": 13000000,
  "proceedsAccountId": "…",
  "counterparty": "PT Pembeli",
  "reason": "Diganti unit baru"
}
```

`type`: `SOLD`, `GIFTED`, `WRITTEN_OFF`, `TRANSFERRED`. `proceeds` and `proceedsAccountId` are required on `SOLD` and refused on the others. `counterparty` is required except on `WRITTEN_OFF`. `proceeds` excludes PPN; the journal records none.

Status: the asset stays as it is while the record is `DRAFT`, and edits and depreciation are refused. Posting `transactionId` through `POST /api/transactions/{id}/post` sets the record `POSTED` and the asset `DISPOSED`. Voiding that transaction sets the record `VOIDED` and returns the asset to `ACTIVE` or `FULLY_DEPRECIATED`. Generic edit and delete of the draft answer `409`.

Preconditions (`409`): acquisition journal posted, no `PENDING` depreciation entry, an entry of any status for every month before the disposal month, exactly one account named `Laba Penjualan Aset`, `Rugi Penjualan Aset`, `Beban Tidak Dapat Dikurangkan`.

`taxEffect` in the response: `gainLoss` (proceeds minus book value), `taxableIncomeEffect`, `fiscalCorrection`, `estimatedPpn`, `note`. Fiscal treatment is unverified and `note` says so. `taxableIncomeEffect` and `fiscalCorrection` are `null` on `TRANSFERRED`; `estimatedPpn` is `null` except on `SOLD`.

## Verification

| Endpoint | Scope | Description |
|----------|-------|-------------|
| `POST /{id}/verification` | `assets:write`, `ASSET_EDIT` | Record the year's check. `FOUND` requires `condition`, `location`, `custodian` and writes them to the register; `NOT_FOUND` leaves it. A second call in the same year replaces the result |
| `GET /verification?year=` | `assets:read` | Every asset bought by year end and not disposed by year end, with the result and `exceptions` |
| `GET /verification/exceptions?year=` | `assets:read` | Rows with at least one exception |

```json
{ "year": 2026, "date": "2026-12-15", "result": "FOUND", "condition": "GOOD", "location": "Gudang", "custodian": "Siti", "notes": null }
```

`exceptions`: `NOT_VERIFIED`, `NOT_FOUND`, `MAJOR_DAMAGE`, `MISSING_BRAND_MODEL`, `MISSING_SERIAL`, `MISSING_CUSTODIAN`, `MISSING_LOCATION`, `MISSING_CONDITION`, `MISSING_PHOTO`.

## Movement

Scope `assets:read`.

| Endpoint | Description |
|----------|-------------|
| `GET /movement?year=` | Roll-forward per category and `total`: `openingCost`, `additions`, `disposals`, `closingCost`, `openingAccumulated`, `depreciation`, `disposedAccumulated`, `closingAccumulated`. Disposals count once posted. Accumulated figures sum `POSTED` entries |
| `GET /register-movements?year=` | Custodian, location and condition changes dated in the year |

Internal read API: `AssetRegisterQueryService` (`findDisposals`, `findDisposalsOfAsset`, `findMovements`, `findMovementsOfAsset`, `costMovement`).

## Depreciation Endpoints

All under `/api/fixed-assets/depreciation`; listing needs `assets:read`, mutations `assets:write`.

| Endpoint | Description |
|----------|-------------|
| `GET /depreciation?period=YYYY-MM&status=` | List entries. Both filters optional; status is `PENDING`/`POSTED`/`SKIPPED` |
| `POST /depreciation/generate?period=YYYY-MM` | Generate entries for one month; without `period`, catch-up generates every owed month up to the previous month (idempotent per asset+month). Returns the PENDING entries |
| `POST /depreciation/{entryId}/post` | Post a PENDING entry to the journal (`409` otherwise) |
| `POST /depreciation/{entryId}/skip` | Mark a PENDING entry SKIPPED (`204`) |
| `POST /depreciation/post-all?period=YYYY-MM` | Post every PENDING entry with period end ≤ the given month; returns `{"postedCount": n}` |

## Depreciation Behavior

- The monthly scheduler (1st, 07:00) generates all owed months up to the previous month — an asset registered after its purchase month's run self-heals on the next run. `POST /depreciation/generate` (no period) triggers the same catch-up on demand.
- Entry `periodNumber` is derived from the schedule (`months between depreciationStartDate and the period, + 1`), so consecutive months can be generated while earlier entries are still PENDING.
- Entries are created as PENDING and reviewed in the web UI (`/assets/depreciation`) or via the endpoints above; assets with `autoPost: true` are posted by the scheduler directly.
- Straight line: `(purchaseCost - residualValue) / usefulLifeMonths`. Declining balance: `bookValue × (depreciationRate / 12 / 100)`.
