# API Authentication

Balaka uses OAuth 2.0 Device Authorization Flow ([RFC 8628](https://tools.ietf.org/html/rfc8628)) for API authentication. This flow is designed for devices and CLI tools that cannot open a browser-based login form directly.

## Authentication Flow

```
CLI/Device                    Balaka API                     Browser
    |                             |                             |
    |-- POST /api/device/code --->|                             |
    |<-- deviceCode, userCode ----|                             |
    |                             |                             |
    |  (display userCode to user)                               |
    |                             |                             |
    |                             |<-- user visits /device ----->|
    |                             |    enters userCode           |
    |                             |    clicks Authorize          |
    |                             |                             |
    |-- POST /api/device/token -->|                             |
    |<-- accessToken, Bearer -----|                             |
    |                             |                             |
    |-- GET /api/analysis/...  -->|                             |
    |   Authorization: Bearer XXX |                             |
```

### Step 1: Request Device Code

```http
POST /api/device/code
Content-Type: application/json

{
  "clientId": "my-integration"
}
```

Response:

```json
{
  "deviceCode": "abc123...",
  "userCode": "ABCD-EFGH",
  "verificationUri": "https://balaka.example.com/device",
  "verificationUriComplete": "https://balaka.example.com/device?code=ABCD-EFGH",
  "expiresIn": 900,
  "interval": 5
}
```

- `deviceCode` -- opaque string used to poll for the token (do not show to user).
- `userCode` -- short code the user enters in their browser.
- `verificationUri` -- URL where the user authorizes the device.
- `expiresIn` -- device code validity in seconds (15 minutes).
- `interval` -- minimum polling interval in seconds.

### Step 2: User Authorization

The user opens `verificationUriComplete` in their browser (or navigates to `verificationUri` and enters the `userCode` manually). They must be logged in to Balaka. After entering the code, they click "Authorize" to grant access.

### Step 3: Poll for Token

While the user authorizes, the client polls for an access token:

```http
POST /api/device/token
Content-Type: application/json

{
  "deviceCode": "abc123..."
}
```

**Pending response** (HTTP 400):

```json
{
  "error": "authorization_pending",
  "errorDescription": "The authorization request is still pending"
}
```

**Success response** (HTTP 200):

```json
{
  "accessToken": "bk_xxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "tokenType": "Bearer",
  "expiresIn": 2592000,
  "scope": "drafts:read,drafts:create,drafts:approve,analysis:read,analysis:write,transactions:post,templates:read,templates:write,data:import,bills:read,bills:create,bills:approve,tax-export:read,tax-export:write,payroll:write,accounts:read,accounts:write,assets:read,assets:write,tax-filings:read,tax-filings:write,bank-recon:read,bank-recon:write"
}
```

**Error responses** (HTTP 400):

| error | meaning |
|-------|---------|
| `authorization_pending` | User has not yet authorized. Keep polling. |
| `expired_token` | Device code expired (15 min). Restart from Step 1. |
| `access_denied` | User denied authorization. |
| `invalid_request` | Invalid or missing device code. |

### Step 4: Use the Token

Include the token in the `Authorization` header for all API requests:

```http
GET /api/analysis/trial-balance?asOfDate=2025-12-31
Authorization: Bearer bk_xxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

## Client Credentials Grant (Service-to-Service)

Unattended backend callers (schedulers, outbox dispatchers) cannot complete the
interactive device flow. For these, register an **API client** under
Pengaturan → API Klien (requires `SETTINGS_EDIT`). The client_id and
client_secret are shown once at creation.

Exchange the credentials for a short-lived bearer token ([RFC 6749 §4.4](https://datatracker.ietf.org/doc/html/rfc6749#section-4.4)):

```http
POST /api/oauth/token
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials&client_id=ar-outbox-1a2b3c4d&client_secret=...
```

HTTP Basic authentication is accepted as an alternative to body parameters.

Response:

```json
{
  "access_token": "opaque-token",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "transactions:post"
}
```

- Tokens are restricted to the client's registered scopes (intersected with the
  service user's role permissions, see below) and expire after
  `app.api-client.token-expiry-minutes` (default 60). Cache the token and
  request a new one on expiry.
- Postings authenticate as the client's configured service user (audit trail).
- Errors follow RFC 6749: `401 {"error":"invalid_client"}` for unknown/inactive
  clients or wrong secrets, `400 {"error":"unsupported_grant_type"}` otherwise.

## Scopes and Authorization

A request is allowed only when **both** hold:

1. The token carries the scope the endpoint requires, and the token's user currently holds at
   least one of the scope's granting permissions (below). Scopes whose permissions the user's
   roles lack are not effective.
2. The user's roles hold the specific permission of the operation, where a scope covers several
   operations with different permissions (e.g. `transactions:post` covers create, post, void,
   delete and purge; void needs `TRANSACTION_VOID`).

Both checks use the user's roles **at request time**. Changing a user's roles changes what their
existing tokens can do on the next request; deactivating the user makes their tokens return 401.
The effective API permissions therefore never exceed what the same user can do in the web UI.

Device-flow tokens carry every scope listed below. Client-credentials tokens carry the scopes chosen
when the API client was registered, evaluated against the client's service user. Browser session
cookies are not accepted on `/api/**` endpoints (403); a request with a `Bearer` header is
authenticated by the token only, and an invalid token returns 401 even if a session cookie is sent.

| Scope | Grants access to | Effective when the user holds any of |
|-------|------------------|--------------------------------------|
| `drafts:read` | Read draft transactions | `DRAFT_VIEW` |
| `drafts:create` | Create drafts (direct, from receipt, from text), update drafts | `TRANSACTION_CREATE` |
| `drafts:approve` | Approve (`DRAFT_APPROVE`) / reject (`DRAFT_REJECT`) drafts | `DRAFT_APPROVE`, `DRAFT_REJECT` |
| `analysis:read` | Financial reports, snapshots, ledgers, transactions, analysis reports | `REPORT_VIEW` |
| `analysis:write` | Publish AI analysis reports | `ANALYSIS_REPORT_CREATE` |
| `transactions:post` | Create (`TRANSACTION_CREATE` + `TRANSACTION_POST` for direct posting), update (`TRANSACTION_EDIT`), post / bulk-post (`TRANSACTION_POST`), void (`TRANSACTION_VOID`), delete (`TRANSACTION_DELETE`), purge (`TRANSACTION_PURGE`); free-form journal entries (`TRANSACTION_CREATE`); transaction documents; tax details (`TAX_EXPORT`, writes also `TRANSACTION_EDIT`) | any `TRANSACTION_*` write permission |
| `templates:read` | List/read journal templates (`/api/templates`, `/api/drafts/templates`) | `TEMPLATE_VIEW` |
| `templates:write` | Create (`TEMPLATE_CREATE`), update (`TEMPLATE_EDIT`), delete (`TEMPLATE_DELETE`) templates | `TEMPLATE_CREATE`, `TEMPLATE_EDIT`, `TEMPLATE_DELETE` |
| `data:import` | Import industry seed data | `DATA_IMPORT` |
| `bills:read` | Read vendor bills | `BILL_VIEW` |
| `bills:create` | Create vendor bills | `BILL_CREATE` |
| `bills:approve` | Approve (`BILL_APPROVE`), mark paid (`BILL_MARK_PAID`) | `BILL_APPROVE`, `BILL_MARK_PAID` |
| `tax-export:read` | Tax exports, SPT data; reads of payroll (`PAYROLL_VIEW`), employees (`EMPLOYEE_VIEW`), salary components (`SALARY_COMPONENT_VIEW`), fiscal adjustments | `TAX_EXPORT` |
| `tax-export:write` | Create/update/delete fiscal adjustments and loss carryforwards | `FISCAL_PERIOD_MANAGE` |
| `payroll:write` | Payroll runs: create/calculate/delete (`PAYROLL_CREATE`), approve (`PAYROLL_APPROVE`), post (`PAYROLL_POST`), schedule (`PAYROLL_CREATE` + `PAYROLL_APPROVE`); employees create (`EMPLOYEE_CREATE`) / update and salary assignments (`EMPLOYEE_EDIT`); salary components (`SALARY_COMPONENT_MANAGE`) | `PAYROLL_CREATE`, `PAYROLL_APPROVE`, `PAYROLL_POST`, `EMPLOYEE_CREATE`, `EMPLOYEE_EDIT`, `SALARY_COMPONENT_MANAGE` |
| `accounts:read` | Read chart of accounts (`/api/accounts`, `/api/drafts/accounts`) | `ACCOUNT_VIEW` |
| `accounts:write` | Create (`ACCOUNT_CREATE`), update (`ACCOUNT_EDIT`), delete (`ACCOUNT_DELETE`) accounts | `ACCOUNT_CREATE`, `ACCOUNT_EDIT`, `ACCOUNT_DELETE` |
| `assets:read` | Read fixed assets, categories, depreciation entries | `ASSET_VIEW` |
| `assets:write` | Register (`ASSET_CREATE`), update (`ASSET_EDIT`), delete (`ASSET_DELETE`) assets; generate/post/skip depreciation (`ASSET_DEPRECIATE`) | `ASSET_CREATE`, `ASSET_EDIT`, `ASSET_DELETE`, `ASSET_DEPRECIATE` |
| `tax-filings:read` | Read the tax filing register and documents | `TAX_CALENDAR_VIEW` |
| `tax-filings:write` | Register tax filings, attach documents | `TAX_CALENDAR_MANAGE` |
| `bank-recon:read` | Read parser configs, statements, reconciliations | `BANK_RECONCILIATION_VIEW` |
| `bank-recon:write` | Parser configs (`BANK_RECONCILIATION_CONFIG`), statement import (`BANK_RECONCILIATION_IMPORT`), matching / create transaction (`BANK_RECONCILIATION_MATCH`), complete (`BANK_RECONCILIATION_COMPLETE`) | any `BANK_RECONCILIATION_*` write permission |

The same list, with the granting permissions, is published in the OpenAPI spec under
`x-authentication.scopes` (`GET /v3/api-docs`).

A 403 response means the token lacks the scope or the user's role lacks the permission.

## Token Properties

- Validity: 30 days from issuance.
- Storage: tokens are hashed (BCrypt) before storage. The plaintext token is returned only once during issuance.
- Revocation: tokens can be revoked from the web UI at `/settings/devices`.

## Token Management UI

Administrators can manage device tokens at **Settings > Perangkat Terhubung** (`/settings/devices`):

- View all active tokens (device name, client ID, last used, scopes).
- Revoke individual tokens.
- See when tokens were last used and from which IP.

## No Authentication Required

The device code and token endpoints (`/api/device/code` and `/api/device/token`) do not require a Bearer token. They are the entry point for obtaining one.

---

Next: [Quickstart](02-quickstart.md)
