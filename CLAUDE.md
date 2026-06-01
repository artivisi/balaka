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
# Requires Docker for Testcontainers (PostgreSQL, ZAP)
# IMPORTANT: Full test suite takes 60-90 minutes. Always run in background
# with log capture. NEVER run multiple instances simultaneously.
./mvnw test 2>&1 | tee target/test-output.log
# Or in background:
nohup ./mvnw test > target/test-output.log 2>&1 &

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
