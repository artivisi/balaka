# Test Suite Audit 2026-09

Started 2026-09-25 from a CI failure (`ServiceDocumentTest`) that exposed tests asserting nothing. Scope grew to the entire suite: ~80 test classes rewritten to verify the feature they name, plus the application bugs those tests exposed. Test-writing rules and the reserved-year table now live in `docs/developer-guide/extending/05-testing.md`.

## Status: complete

- All audit commits are on `main`. Released in **2026.11-RELEASE** (tag on `c8da6957`), deployed to artivisi on 2026-11-19 together with V006-V023.
- The release-notes draft that was kept here is published in `docs/releases/2026.11-RELEASE.md` (Security, API changes, accounting/tax fixes, data/backups).
- Deploy checklist at that deploy: V006 + V007 applied; posting accounts configured in company settings (Akun Posting).
- **Not applied (checked 2026-11-20):** the nginx `X-Forwarded-For` overwrite. `/etc/nginx/snippets/proxy-params.conf` on artivisi and on the demo VPS still has `$proxy_add_x_forwarded_for`. `deploy.yml` does not touch nginx; the `nginx` role in `site.yml` installs the snippet.
- The test-run procedure here referred to Apple Container + socktainer. This machine now runs Testcontainers on OrbStack; see CLAUDE.md (Container Runtime) and `run-tests.sh`.

## Open Decisions

The backlog below was recorded on 2026-09-27 and is kept as the list of known defects and decisions not yet taken. Items marked resolved were confirmed in the code; the others have not been re-verified since that date.

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
- ~~JP ceiling 10,042,300 labelled 2025~~ resolved 2026-11-06: dated table in `BpjsCalculationService` (2024-03 10,042,300; 2025-03 10,547,400; 2026-03 11,086,300).
- BPJS calculation treats null/out-of-range risk class as class 1 (fallback).

Seed / demo data:
- Coffee-shop production orders inconsistent (PROD-001 cost 106,920 vs consumed 207,300; PROD-002 87,760 vs 188,000); RAGI/GARAM priced per gram at 3,000/8,000.
- Online-seller seed leaves Bank BCA at −193,500,000 on 2024-01-31.
- "Pengakuan Pendapatan Invoice" / "Tagihan Vendor" templates only in it-service → invoices/bills fail in other packs; campus has no PPN accounts.
- ~25 header-only seed CSVs have headers not matching the import format; CSV import reads by position (headers ignored); unknown ZIP entries silently ignored; empty import reports success; COA export writes `created_at` where import reads `is_permanent`; journal export drops `is_reversal`/`id_reversed_entry`.

Security / config:
- App-level login rate limit never applies (filter order); nginx limits `/login` in production. Reordering enforces 10/min per IP (shared office IPs) — decide.
- `/documents/api/{id}` serializes `storagePath`.
- ~~`/clients/search`, `/vendors/search` only require authentication (EMPLOYEE can list names).~~ resolved 2026-11-20: they require `CLIENT_VIEW` / `VENDOR_VIEW` like the rest of their controllers.
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
