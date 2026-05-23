# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Balaka** — Indonesian accounting application for small businesses. Spring Boot 4.0.4 + Thymeleaf + PostgreSQL 18, Java 25. Licensed Apache 2.0. Maven artifact `com.artivisi:accounting-finance`, current version `2026.05-SNAPSHOT`.

All 20 phases complete — see `docs/06-implementation-plan.md` for status and `docs/01-features-and-roadmap.md` for feature inventory.

## Architecture

```
User → Controller (MVC) → Service → Repository → PostgreSQL
         ↓
    Thymeleaf Templates (HTMX + Alpine.js, Bootstrap + Tailwind)
```

**Transaction-centric design:** There are no standalone journal entries. Every `journal_entry` row is generated from a `transaction` + `journal_template`, with user-selected accounts resolved via `transaction_account_mappings` and amounts computed by the SpEL formula engine. `JournalService` enforces double-entry; `FormulaService` evaluates SpEL using `SimpleEvaluationContext.forReadOnlyDataBinding()` (no type refs, constructors, or bean refs).

**System vs user templates:** Templates with `is_system=true` (e.g. payroll, depreciation, year-end closing) are owned by internal services and must not be edited by users. User templates (e.g. revenue, expenses, transfers) are customizable.

**Single-tenant:** One company per database instance. No multi-tenancy logic in code.

**Layout:** 60 controllers, 74 services, 88 JPA entities (UUID PKs, soft-delete pattern, audit fields on `BaseEntity`). API controllers live under `controller/api/` — only this package is scanned by springdoc-openapi.

## Common Commands

```bash
# Requires: Java 25, Docker (for Testcontainers — PostgreSQL + ZAP)
# Maven wrapper is bundled; no system Maven needed.

# Run all tests (unit + integration + functional + DAST, 60–90 min)
# NEVER run multiple instances in parallel — Testcontainers will conflict.
nohup ./mvnw test > target/test-output.log 2>&1 &

# Run a single functional test (Playwright-driven, headless by default)
./mvnw test -Dtest=MfgBomTest

# Debug a functional test with a visible browser
./mvnw test -Dtest=MfgBomTest -Dplaywright.headless=false -Dplaywright.slowmo=100

# SpotBugs + FindSecBugs (static security)
./mvnw spotbugs:check          # results in target/spotbugsXml.xml

# OWASP ZAP DAST (excluded from default surefire run via excludedGroups)
./mvnw test -Dtest=ZapDastTest -Ddast.enabled=true                # full active scan (~20 min)
./mvnw test -Dtest=ZapDastTest -Ddast.enabled=true -Ddast.quick=true  # passive only (~5 min)

# First-time Ubuntu setup (installs Playwright browser deps)
./setup-ubuntu.sh
```

Frontend assets (Tailwind, Vite) are built automatically during `generate-resources` via the `frontend-maven-plugin` (Node 22.12.0 / npm 11.7.0) from `src/main/frontend/`. No manual `npm install` needed.

## Runtime Configuration

App listens on **port 10000** (not Spring's default 8080). Defaults expect PostgreSQL on `localhost:12345` with `sslmode=require`. Override via env vars:

| Env var | Purpose |
|---------|---------|
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | DB connection |
| `APP_ENCRYPTION_KEY` | AES-256-GCM key for PII fields (`openssl rand -base64 32`) |
| `TELEGRAM_BOT_*` | Telegram bot integration (disabled by default) |
| `GOOGLE_APPLICATION_CREDENTIALS`, `GOOGLE_CLOUD_VISION_ENABLED` | Receipt OCR |
| `APP_DEMO_MODE` | Shows reset banner on every page |

Session timeout is 15 minutes; cookies are `HttpOnly` + `SameSite=strict`. Hibernate runs with `ddl-auto=validate` — schema is owned by Flyway.

## Database

- Production migrations: `src/main/resources/db/migration/` (V001 security, V002 core schema, V003 feature schema, V004 seed data).
- **Migration policy:** Pre-production — *modify* existing V001–V004 rather than adding new files. Modifying an already-applied migration requires manual schema fix + checksum update in `flyway_schema_history`; see `docs/03-operations-guide.md` Troubleshooting.
- Integration tests: V900–V912 under `src/test/resources/db/test/integration/` (preloaded data for unit/service/security tests).
- Functional tests: **no migrations** — data is loaded via `@TestConfiguration` initializers from `industry-seed/{it-service,online-seller,coffee-shop,campus}/seed-data/` (COA, templates, products, BOMs, etc.) using `DataImportService`.

## Development Guidelines

1. **Feature completion criteria:** Item is only checked when verified by a Playwright functional test.
2. **No fallback/default values:** Throw errors instead of silently handling missing data.
3. **Technical language:** No marketing speak in docs.
4. **Test-driven:** Write functional tests for new features.
5. **Migration strategy:** Modify existing migrations instead of creating new ones (pre-production — see Database section).
6. **Code quality:** Maintain SpotBugs 0-issue status. Any new exclusion in `spotbugs-exclude.xml` must include a comprehensive justification with mitigation details.

## Key Files

| Purpose | Location |
|---------|----------|
| Features & Roadmap | `docs/01-features-and-roadmap.md` |
| Architecture | `docs/02-architecture.md` |
| Operations Guide | `docs/03-operations-guide.md` |
| Tax Compliance | `docs/04-tax-compliance.md` |
| Implementation Plan | `docs/06-implementation-plan.md` |
| ADRs | `docs/adr/` |
| User Manual | `docs/user-manual/*.md` |
| User Manual Guidelines | `docs/user-manual-creation-guidelines.md` |
| Security Exclusions | `spotbugs-exclude.xml` |
| Entities / Services / Controllers | `src/main/java/com/artivisi/accountingfinance/{entity,service,controller}/` |
| API Controllers (scanned by OpenAPI) | `src/main/java/com/artivisi/accountingfinance/controller/api/` |
| Templates | `src/main/resources/templates/` |
| Production migrations | `src/main/resources/db/migration/` (V001–V004) |
| Integration-test migrations | `src/test/resources/db/test/integration/` (V900–V912) |
| Industry seed packs | `industry-seed/{it-service,online-seller,coffee-shop,campus}/` |
| Functional tests | `src/test/java/com/artivisi/accountingfinance/functional/` |
| Infrastructure (Pulumi) | `deploy/pulumi/` |
| Configuration (Ansible) | `deploy/ansible/` |
