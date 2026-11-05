# Test Suite Audit 2026-09

Started 2026-09-25 from a CI failure (`ServiceDocumentTest`) that exposed tests asserting nothing. Scope grew to the entire suite: ~80 test classes rewritten to verify the feature they name, plus the application bugs those tests exposed. Test-writing rules and the reserved-year table now live in `docs/developer-guide/extending/05-testing.md`.

## Status (2026-09-27)

- `origin/main` = `182c32c8` (83 commits pushed). CI on it failed 2/3224: `QuickTransactionTest.shouldCreateTransactionViaQuickForm`, `JournalEntryApiTest.fullLifecycle`.
- Local, **not pushed** (fix both CI failures + one found by the full local run):
  - `cb9c6700` fix(journal): explicit per-transaction `line_order` (migration **V007**)
  - `4ca846b7` fix(ui): stop re-creating Alpine components on `htmx:afterSettle`
  - `9c4c8c94` fix(ui): drop stale account-picker search responses; demo loader fails loudly
- Verified on local HEAD: CoffeeShopVerificationTest, OnlineSellerVerificationTest, 189-test UI batch, 1417 service integration tests, journal/transaction classes.
- **Not yet verified**: `DemoVerificationTest` (it-service) and `CampusVerificationTest` — last attempt invalid (host on battery, slept; run killed for macOS update).

## Resume Checklist

1. Start the container runtime: `container system start`, then `launchctl kickstart -k gui/$(id -u)/com.socktainer.local`; `./pull-test-images.sh`. AC power, lid open.
2. `./run-tests.sh -Dtest='DemoVerificationTest,CampusVerificationTest' -DexcludedGroups=` (~20 min).
3. Full suite: `./run-tests.sh` (~2h15m). Any `TimeoutError` → check `events.log` for sleep before investigating.
4. Push, cancel superseded CI runs, watch CI.
5. Before deploying to artivisi: see Deploy Checklist.

## Deploy Checklist (artivisi)

- **Migrations V006 + V007** not yet applied in production. Back up first.
  - V006: widens `device_tokens.scopes` / `api_clients.scopes`, adds new scopes to existing device tokens.
  - V007: adds `journal_entries.line_order` (backfilled from journal number order), `UNIQUE (id_transaction, line_order)`.
- **Receivable/payable accounts must be configured** in company settings, otherwise dashboard KPI widget shows a configuration notice and `/api/analysis/snapshot|receivables|payables` return 500 `CONFIGURATION_ERROR`.
- **API clients (client_credentials)** need the new write scopes granted explicitly (Pengaturan → API Klien) if they use payroll, fiscal adjustment, template, bank reconciliation or draft endpoints.
- **nginx**: `proxy-params.conf.j2` now overwrites `X-Forwarded-For` — apply via Ansible.
- **Seed packs already imported** (coffee-shop, online-seller): inventory templates were imported without lines (`is_system=true` skipped lines). Fix manually if production used those packs.

## Release Notes Draft (next release)

Security:
- Web write endpoints require their write permission (previously many needed only `*_VIEW`; AUDITOR/STAFF could create invoices, approve drafts, close fiscal periods, post payroll). BOM, production, milestone, payment term, document and data export controllers had no permission check at all.
- API tokens act with at most the permissions of their user's current roles; tokens of deactivated users return 401. New scopes: `payroll:write`, `tax-export:write`, `templates:read`/`templates:write`, `bank-recon:read`/`bank-recon:write`; `drafts:*` now enforced; `POST /api/transactions` requires `transactions:post`. Existing device tokens receive the new scopes automatically; **API clients do not** (grant under Pengaturan → API Klien). Browser sessions can no longer call `/api/**`; session requests to `/*/api/**` require the CSRF header. New permissions `ANALYSIS_REPORT_CREATE`, `ANALYSIS_REPORT_DELETE`.
- Tax detail update/delete (web and API) verify the detail belongs to the transaction in the URL.
- CSP nonce leaked into redirect URLs; stale-nonce fallback removed; nginx `X-Forwarded-For` overwrite (rate-limit key spoofing).
- Uploads over 1MB failed with 500 (multipart limit now 10MB/11MB, 413 on oversize).

API changes:
- Error responses carry the documented `error` code and service message (409 → `CONFLICT`; new `INVALID_REQUEST`, `CONFIGURATION_ERROR`).
- Bank reconciliation statement: `bookOnlyTotal` replaced by `bookOnlyDebitTotal` / `bookOnlyCreditTotal`.
- `/api/tax-export/rekonsiliasi-fiskal` adds `lossCompensation`, `pkpAfterLoss`.
- Analysis receivables/payables use the configured company accounts (were hard-coded 1.1.04 / 2.1.01).

Accounting / tax fixes:
- PPh Badan computed on PKP after fiscal loss compensation (L1, SPT checklist, rekonsiliasi fiskal, Coretax); fiscal loss no longer counted as compensable in or before its origin year.
- Bank reconciliation: book-only items split by debit/credit (were all added to the bank balance).
- Declining-balance depreciation rounding; depreciation report excluded pending/skipped entries.
- Journal line order is explicit and deterministic (V007).

Data / backups:
- Journal export (`20_journal_entries.csv`) has a trailing `line_order` column; exports made before this release no longer import.
- Company config export includes posting accounts; import rejects unknown account codes.
- Seed packs configure receivable/payable/PPN accounts.

UI fixes (selection): date inputs rendered empty on edit forms (milestone, payment term, project, employee, asset, inventory); edit forms posted to another record after a rejected code change (client, project, vendor, bill, employee, BOM, product); default dates used the UTC date (wrong 00:00–07:00 WIB); account picker could select a stale result; Alpine components re-created after every HTMX swap; flash messages missing on many pages; multi-statement Alpine handlers ignored by the CSP build; `.75`-style number rendering.

## Open Decisions (not changed)

Systemic:
- **Hard-coded template UUIDs** do not exist after seed import (IDs regenerated): invoice "Tandai Lunas" (`e0000000-…-010`) → 404; payroll posting (`-014`) falls back to lookup by name (violates no-fallback); asset disposal (`-017`) fixed by #49 (templates resolved by name, in all four packs). Needs a template-resolution design (per pack), plus disposal proceeds account UI and gain/loss accounts.
- **Encrypted-column equality lookups**: duplicate-NPWP check never matches with encryption on (AES-GCM random IV). Needs blind index (keyed hash column + migration); check other encrypted columns.
- **API token validation** bcrypt-compares against every active token per request (ApiAuthorizationTest 692 s) → indexed token id/prefix.
- **Company identity**: importing a pack wipes company name/NPWP (COA truncate cascades; `getConfig()` recreates "My Company" — a fallback); setup wizard never asks for a company name.

Accounting integrity:
- `voidTransaction` ignores fiscal period — voiding in a closed period changes its reports.
- Journal/transaction numbers use the posting year, not the transaction date's year.
- Depreciation entries computed from posted state at generation time → stale values when several months are pending (scheduler catch-up); straight-line can drop below residual.
- Acquisition DRAFT not linked to the asset: fixed by #49 for delete and disposal, through `source_document_type = ASSET`. Edits after depreciation silently ignore cost/useful-life changes.
- Posting a template with no lines may create a POSTED transaction without journal entries (unverified).
- Invoice/bill payments post no journal (missing bridge, see DRAFT-by-default memory); bill "Tandai Lunas" sets PAID without a payment record; aging "as of" is not historical.
- Amortization: schedule with a skipped entry never reaches COMPLETED; batch runs in one transaction (one failure likely rolls back all); batch ignores post day.
- Inventory: purchase/sale templates' BANK line never mapped (drafts not postable from web UI); missing COGS/sales account silently leaves lines unmapped; sale without price → zero revenue in profitability.

Tax:
- Lampiran III kredit pajak / pph23-detail include PPh 23 we withheld from vendors; PPh Badan credit uses ledger 1.1.26 — they disagree.
- Auto-populated PPh 23 on vendor payment uses the project's client as counterparty.
- Tax due dates not shifted to next working day (PMK 81/2024).
- **Verify**: `BPJS_JP_CEILING = 10,042,300` labelled 2025; may be the 2024 figure (2025 reportedly ≈ Rp 10,547,400 from March 2025). Changing it changes payroll JP amounts and tests.
- BPJS calculation treats null/out-of-range risk class as class 1 (fallback).

Seed / demo data:
- Coffee-shop production orders inconsistent (PROD-001 cost 106,920 vs consumed 207,300; PROD-002 87,760 vs 188,000); RAGI/GARAM priced per gram at 3,000/8,000.
- Online-seller seed leaves Bank BCA at −193,500,000 on 2024-01-31.
- "Pengakuan Pendapatan Invoice" / "Tagihan Vendor" templates only in it-service → invoices/bills fail in other packs; campus has no PPN accounts.
- ~25 header-only seed CSVs have headers not matching the import format; CSV import reads by position (headers ignored); unknown ZIP entries silently ignored; empty import reports success; COA export writes `created_at` where import reads `is_permanent`; journal export drops `is_reversal`/`id_reversed_entry`.

Security / config:
- App-level login rate limit never applies (filter order); nginx limits `/login` in production. Reordering enforces 10/min per IP (shared office IPs) — decide.
- `/documents/api/{id}` serializes `storagePath`.
- `/clients/search`, `/vendors/search` only require authentication (EMPLOYEE can list names).
- Confirm permission choices: fiscal adjustments → `FISCAL_PERIOD_MANAGE`; deletes/cancels without own permission → `*_EDIT`; data export → `DATA_IMPORT`; payroll cancel → `PAYROLL_CANCEL` (ACCOUNTANT lacks it); BOM writes → `PRODUCT_*`, production → `INVENTORY_ADJUST`; company logo GET open to any authenticated user.

UX / smaller:
- English messages in the Indonesian UI (milestone/payment term sequence, COA, template service, company form, amortization period label "JANUARY 2045", alert number locale).
- Template "Aktif" toggle broken (form `isActive` vs DTO `active`; inactive templates cannot be reactivated from UI).
- Payroll: no recalculate button; controller flashes raw exception messages.
- `RestExceptionHandler` returns JSON 400 for browser page requests (payslip/1721-A1 without data, unknown product/draft id).
- Milestone/payment-term: FK on delete of referenced milestone; no server-side checks (project ACTIVE, ownership by URL project, multiple invoices per term, FIXED_DATE default now+30d, term without percentage/amount).
- Transactions: search ignores other filters; unknown `projectCode` ignored; soft-deleted account/tag codes cannot be reused (plain UNIQUE); `PUT /api/accounts/{id}` can change a root account's type; `/journals` PDF/Excel buttons do nothing.
- Dead code/endpoints: `POST /transactions/quick`, `/about` (duplicates `/settings/about`), `JournalEntryService.calculateAccountImpact`.
- Misc: bank-account delete has no UI; Telegram code generation allowed when bot disabled; anonymization keeps linked user name/email; product deactivation with stock/in BOM unguarded; reopen fiscal period overwrites notes; client/vendor email not validated; staff sees bank-recon controls that return 403; stock-balance auto-submit script dead; user-manual screenshots from ServiceReportsTest show 2043 dates.
