# Claude Instructions

## Project Overview

Indonesian accounting application for small businesses. Spring Boot 4.0 + Thymeleaf + PostgreSQL. Licensed under Apache License 2.0.

## Current Status

- **Phase 0:** ✅ Complete (project setup, auth, CI/CD)
- **Phase 1:** ✅ Complete (Core Accounting MVP)
- **Phase 2:** ✅ Complete (Tax Compliance + Cash Flow)
- **Phase 3:** ✅ Complete (Payroll + RBAC + Employee Self-Service)
- **Phase 4:** ✅ Complete (Fixed Assets)
- **Phase 5:** ✅ Complete (Inventory & Production)
  - 5.1 Product Master: ✅ Complete
  - 5.2 Inventory Transactions: ✅ Complete
  - 5.3 Inventory Reports: ✅ Complete
  - 5.4 Simple Production (BOM): ✅ Complete
  - 5.5 Integration with Sales: ✅ Complete
- **Phase 6:** ✅ Complete (Security Hardening)
- **Phase 7:** ✅ Complete (API Foundation — OAuth 2.0 device auth, 7 API controllers, pagination, device token management UI)
- **Phase 8:** ✅ Complete (Bank Reconciliation)
- **Phase 9:** ✅ Complete (Analytics & Insights — 9.2 Smart Alerts ✅, 9.3 Transaction Tags ✅)
- **AI Analysis Reports:** ✅ Complete (structured report publishing with per-industry KPIs)
- **Phase 10:** ✅ Complete (Invoice & Bill Management — invoices, vendor bills, bill API, payment tracking, aging reports, customer/vendor statements)
- **Phase 11:** ✅ Complete (Recurring Transactions — templates, scheduling, auto-posting)
- **Phase 12:** ✅ Complete (Tax Data Management — 12.1 PPN formula fix, 12.2 tax detail entry UI, 12.3 tax detail & document API, 12.4 auto-populate tax details, 12.5 client management UI, 12.6 fiscal period management, 12.7 tax report enhancements, 12.8 tax deadline updates, 12.9 retrofit 2025 data, 12.10 tax export API, 12.11 PPN docs update)
- **Phase 13:** ✅ Complete (OpenAPI Migration — springdoc-openapi 3.0.1, 11 @Tag controllers, x- extensions, Swagger UI)
- **Phase 14:** ✅ Complete (Fiscal Adjustments API)
- **Phase 15:** ✅ Complete (Payroll API + PPh 21)
- **Bug Fixes:** ✅ Complete (BUG-001 PPN rounding, BUG-002 PUT broken DRAFTs, BUG-003 PUT overrides 409, BUG-004 empty journalEntries)
- **Phase 16:** ✅ Complete (User Manual Revamp — AI-Operated Lifecycle)
- **Phase 17:** ✅ Complete (SPT Tahunan Badan Data Export — L1 rekonsiliasi fiskal, L4 penghasilan final, L9 penyusutan DJP format, transkrip 8A, e-Bupot PPh 21 annual, fiscal loss carryforward, SPT checklist dashboard)
- **Phase 18:** ✅ Complete (PPh 21 TER Method — PMK 168/2023, TerCategory enum, monthly TER Jan–Nov, December reconciliation, web calculator)
- **Phase 19:** ✅ Complete (Scheduled Payroll — automated monthly payroll run creation, CRUD API, daily scheduler with startup catch-up)
- **Phase 20:** ✅ Complete (Free-Form Journal Entry API — `POST /api/transactions/journal-entry`, arbitrary debit/credit lines, no template required, for closing/adjusting/opening entries)
- **BUG-014:** ✅ Complete (Tax export excluded closing journal from P&L via `closingEntry` boolean)
- **SPT Lampiran Export:** ✅ Complete (`GET /api/tax-export/spt-tahunan/lampiran?year=`, consolidated Coretax-ready data)
- **BUG-016/017/018:** ✅ Complete (Transkrip 8A asset mapping, Lampiran I pasal field, accountSlots lineOrder fallback)
- **Period Report:** ✅ Complete (`/reports/period` — fiscal period dropdown, closing-entry-excluded P&L)
- **Sidebar Reorg:** ✅ Complete (Master Data separated from Pengaturan)
- **Tax Filing (FR-001–006):** ✅ Complete (L1 non-operating expenses fix, L9 depreciation fallback, PKP rounding, excludeClosing API param, financial statements PDF, Coretax SPT export)
- **Service Auth + Retry-Safe Posting (issues #28, #29):** ✅ Complete (OAuth2 client_credentials grant — `api_clients` table, `POST /api/oauth/token`, Pengaturan → API Klien; `Idempotency-Key` header on `POST /api/transactions` with replay semantics)
- **Depreciation Fixes (issues #31, #32):** ✅ Complete (schedule-derived periodNumber + `uk_asset_period(id_fixed_asset, period_end)`; scheduler catch-up for late-registered assets; `/api/fixed-assets/depreciation` endpoints)
- **Fixed Asset API (issue #30):** ✅ Complete (`/api/fixed-assets` CRUD + `/categories`, scopes `assets:read`/`assets:write`, register via funding account → acquisition DRAFT or `purchaseTransactionId` → link existing posted journal without new draft)
- See `docs/06-implementation-plan.md` for full plan

## Key Files

| Purpose | Location |
|---------|----------|
| Features & Roadmap | `docs/01-features-and-roadmap.md` |
| Architecture | `docs/02-architecture.md` |
| Operations Guide | `docs/03-operations-guide.md` |
| Tax Compliance | `docs/04-tax-compliance.md` |
| Implementation Plan | `docs/06-implementation-plan.md` |
| ADRs | `docs/adr/` |
| User Manual | `docs/user-manual/*.md` (20 files, 17 sections + appendices) |
| User Manual Guidelines | `docs/user-manual-creation-guidelines.md` (section extraction rules, duplicate prevention) |
| Security Exclusions | `spotbugs-exclude.xml` (SpotBugs false positives with justifications) |
| Entities | `src/main/java/.../entity/` |
| Services | `src/main/java/.../service/` |
| Controllers | `src/main/java/.../controller/` |
| Templates | `src/main/resources/templates/` |
| Migrations (Production) | `src/main/resources/db/migration/` (V001-V004) |
| Test Migrations (Integration) | `src/test/resources/db/test/integration/` (V900-V912) |
| Industry Seed Packs | `industry-seed/{it-service,online-seller,coffee-shop,campus}/` (loaded via DataImportService) |
| Functional Tests | `src/test/java/.../functional/` |
| Infrastructure (Pulumi) | `deploy/pulumi/` |
| Configuration (Ansible) | `deploy/ansible/` |

## Development Guidelines

1. **Feature completion criteria:** Item is only checked when verified by Playwright functional test
2. **No fallback/default values:** Throw errors instead of silently handling missing data
3. **Technical language:** No marketing speak, strictly technical documentation
4. **Test-driven:** Write functional tests for new features
5. **Migration strategy:** Modify existing migrations instead of creating new ones (pre-production)
6. **Code quality:** Maintain SpotBugs 0-issue status. Any new exclusions in `spotbugs-exclude.xml` must have comprehensive justifications with mitigation details

## Running the App

```bash
# First-time setup on Ubuntu (install Playwright browsers)
./setup-ubuntu.sh

# Run all tests (unit, integration, functional, DAST)
# Requires a Docker-API container runtime for Testcontainers (PostgreSQL, ZAP)
# — see "Container Runtime" below
# IMPORTANT: Full test suite takes 60-90 minutes. NEVER run two at once.
# Use the wrapper — it holds off sleep, writes logs outside target/ (which
# `mvn clean` would delete mid-run), samples memory/engine health every 30s,
# and dumps forensics if the engine wedges. Maven args pass straight through.
./run-tests.sh                        # full suite
./run-tests.sh -Dtest=MfgBomTest      # single test
nohup ./run-tests.sh > /dev/null 2>&1 &   # background; logs land in logs/

# Artifacts: logs/test-run-<timestamp>/{test-output.log,metrics.tsv,events.log}
# If it reports the machine slept, re-run before investigating any TimeoutError.

# Run specific functional test
./mvnw test -Dtest=MfgBomTest

# Run with visible browser (debugging)
./mvnw test -Dtest=MfgBomTest -Dplaywright.headless=false -Dplaywright.slowmo=100

# Run SpotBugs security analysis
./mvnw spotbugs:check
# Results: target/spotbugsXml.xml

# Run only DAST tests (excluded from default surefire run via excludedGroups)
./mvnw test -Dtest=ZapDastTest -DexcludedGroups= -Ddast.enabled=true
# Results: target/security-reports/zap-*.html

# Run DAST in quick mode (passive scan only, ~1 min)
./mvnw test -Dtest=ZapDastTest -DexcludedGroups= -Ddast.enabled=true -Ddast.quick=true
```

## Container Runtime

Testcontainers needs a Docker-API endpoint. Linux/CI uses Docker Engine. The macOS dev machine uses **Apple Container** (`container` CLI) with **socktainer** providing the Docker API, registered as docker context `socktainer`.

Testcontainers 2.0.5 resolves the docker context automatically — do **not** set `DOCKER_HOST` or `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`. Plain `./mvnw test` works.

**Pre-pull images before the first run.** docker-java cannot parse socktainer's pull-progress stream and aborts with `Could not pull image: Image digest: sha256:...`, even though the image downloads successfully. `POST /images/create` ends the stream with `Image digest: sha256:…` where Docker sends `Status: Downloaded newer image for <image>:<tag>`, and docker-java accepts only the latter. The failing test passes on re-run, so pull up front:

```bash
./pull-test-images.sh
```

Keep the image list in that script in sync with the test code (`postgres:18-alpine`, `ghcr.io/zaproxy/zaproxy:stable`) and with the Testcontainers version in `pom.xml` (`testcontainers/ryuk`, `testcontainers/sshd`).

**Delete the pre-pull step once socktainer ships a release containing PR #365.** Reported as [socktainer#359](https://github.com/socktainer/socktainer/issues/359) (repro: [socktainer-pull-repro](https://github.com/endymuhardin/socktainer-pull-repro)); fixed on `main` 2026-08-16, but the latest release is still v1.2.1 (2026-08-01), which predates it. Check with `brew list --versions socktainer` against the [releases](https://github.com/socktainer/socktainer/releases).

**Every container is its own VM.** Unlike Docker Engine and OrbStack, Apple Container gives each container a dedicated VM with a *fixed* reservation — 1 GB and 4 CPUs by default — so container count multiplies real RAM. A full suite run holds ~17 Postgres containers concurrently: 18 GB reserved on a 16 GB machine, which swaps hard and makes Playwright navigations exceed their 15s timeout (tests then fail as `TimeoutError`, not as logic errors).

`ContainerResourceDefaults` (registered via `META-INF/services`) therefore caps every container — 512 MB / 2 CPUs by default, 2 GB for ZAP, 256 MB for Ryuk and the sshd helper. Note the reservation is a *ceiling*, not a pre-wired allocation: idle containers cost almost nothing, so this bounds worst-case over-commit rather than steady-state usage.

**Ryuk is disabled locally** (`TESTCONTAINERS_RYUK_DISABLED=true` in `run-tests.sh`, which reaps containers itself on exit). Twice it destroyed a live session mid-run — 16 containers on 2026-08-17 20:10, 10 on 2026-08-18 00:34 — after which every remaining test failed with `Connection to localhost:<port> refused` and the containers never returned, because the cached Spring contexts still referenced the dead ports. Ryuk reaps when its heartbeat from the JVM drops and cannot tell that apart from the JVM exiting.

Not reported upstream: it has never been reproduced outside the real suite, and may be specific to this machine (M5 MacBook Air, macOS 26.5, Apple Container 1.2.2, socktainer 1.2.1, often with a second Testcontainers suite running). Four deliberate attempts failed to trigger it, so **do not re-test these** — churning 400 containers in 5m27s; Postgres containers holding live JDBC connections; sustained host starvation at `free` 0.06 GB for 13 minutes; and elapsed time alone. Exhausting the test JVM's heap *does* cause a reap, but correctly — the JVM really did die. Untested and still plausible: Playwright's browser lifecycle and Spring context eviction, both bursty events absent from those attempts. Since disabling it: 4 full runs, ~15,000 tests, zero collapses.

**Do not let the machine sleep during a run.** This is the failure mode that actually bites. macOS sleeps on idle every ~15 min on battery, and closing the lid sleeps unconditionally. Sleeping mid-run suspends the container VMs and the engine's XPC services, which:

- makes Playwright navigations and awaitility waits blow their timeouts, so tests fail as `TimeoutError` and single tests report 400–900s elapsed
- can wedge the engine — `container list` then fails with `XPC timeout for request to com.apple.container.apiserver/containerList`
- can strand VM memory as **wired** if containers are force-removed while the engine is unresponsive. Wired memory is not reclaimable from userspace; observed 14 GB wired with only 0.5 GB total process RSS, recoverable only by reboot

Verified 2026-08-16: a suite run was interrupted by seven sleeps plus a one-hour clamshell sleep, which produced exactly this. Always `caffeinate -is`, stay on AC, and leave the lid open. Check afterwards with `pmset -g log | grep -E "Sleep|Wake"` before believing any bulk timeout failure.

If you see functional tests timing out in bulk, check sleep history and reservations before suspecting the code:

```bash
pmset -g log | grep -E "Sleep|Wake"   # did the machine sleep mid-run?
vm_stat                               # "Pages wired down" — 14 GB wired means stranded VMs
container list                        # CPUS and MEMORY columns, per container
container builder stop                # the build VM alone reserves 2 GB when idle
```

## Database

- PostgreSQL via Testcontainers (tests)
- Production migrations: V001-V004 (V001 security, V002 core schema, V003 feature schema, V004 seed data)
- **Migration caveat:** Modifying already-applied migrations requires manual schema fix on production + checksum update in `flyway_schema_history`. See `docs/03-operations-guide.md` Troubleshooting section.
- Test data:
  - Functional tests: NO migrations - all data loaded via `@TestConfiguration` initializers from industry-seed/ packs
  - Integration tests: V900-V912 (preloaded data for unit/service/security tests)
- Industry seed packs: `industry-seed/{it-service,online-seller,coffee-shop}/seed-data/` (COA, templates, products, BOMs, etc.)

## Architecture

```
User → Controller (MVC) → Service → Repository → PostgreSQL
         ↓
    Thymeleaf Templates (HTMX + Alpine.js)
```

**Transaction-centric design:** There are no standalone journal entries. Every `journal_entry` row is generated from a `transaction` + `journal_template`, with user-selected accounts resolved via `transaction_account_mappings` and amounts computed by the SpEL formula engine. `JournalEntryService` + `JournalBalancer` enforce double-entry. `FormulaEvaluator` evaluates SpEL using `SimpleEvaluationContext.forReadOnlyDataBinding()` — no type refs, constructors, or bean refs allowed.

**System vs user templates:** Templates with `is_system=true` (payroll, depreciation, year-end closing) are owned by internal services and must not be edited by users. User templates (revenue, expenses, transfers, etc.) are customizable.

**Single-tenant:** One company per database instance. No multi-tenancy logic in code.

**API surface:** Web controllers live in `controller/`; REST API controllers in `controller/api/` — only the `api` package is scanned by springdoc-openapi (`OpenApiConfig.packagesToScan(...)`).

## Runtime Configuration

App listens on **port 10000** (not Spring's default 8080). Default DB URL is `jdbc:postgresql://localhost:12345/accountingdb?sslmode=require`. Override via env vars:

| Env var | Purpose |
|---------|---------|
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | DB connection |
| `APP_ENCRYPTION_KEY` | AES-256-GCM key for PII fields (`openssl rand -base64 32`) |
| `TELEGRAM_BOT_*` | Telegram bot integration (disabled by default) |
| `GOOGLE_APPLICATION_CREDENTIALS`, `GOOGLE_CLOUD_VISION_ENABLED` | Receipt OCR |
| `APP_DEMO_MODE` | Shows reset banner on every page |

Hibernate runs with `ddl-auto=validate` — schema is owned by Flyway, not JPA.

Frontend assets (Tailwind, Alpine) are built automatically during Maven `generate-resources` via `frontend-maven-plugin` from `src/main/frontend/`. No manual `npm install` needed.

## Current Release

**2026.06-RELEASE** tagged. See `docs/releases/2026.06-RELEASE.md` for release notes.

## Current Focus

Phases 0-20 complete.

See `docs/06-implementation-plan.md` for full plan
