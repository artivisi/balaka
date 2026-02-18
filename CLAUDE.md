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
- **Phase 6:** 🔄 In Progress (Security Hardening)
  - 6.1-6.5: ✅ Complete (Critical fixes, Encryption, Auth hardening, Input validation, Audit logging)
  - 6.6: ✅ Complete (Data Protection)
  - 6.7: ✅ Complete (API Security)
  - 6.8: 🔄 Partial (GDPR/UU PDP - consent management, breach response pending)
  - 6.9: 🔄 Partial (DevSecOps - container security, API fuzzing pending)
  - 6.10: ✅ Complete (Security Documentation)
- **Phase 7:** ⏳ Not Started (API Foundation)
- **Phase 9:** ✅ Complete (Bank Reconciliation)
- **AI Analysis Reports:** ✅ Complete (structured report publishing with per-industry KPIs)
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
| User Manual | `docs/user-manual/*.md` (16 files, 14-section structure) |
| User Manual Guidelines | `docs/user-manual-creation-guidelines.md` (section extraction rules, duplicate prevention) |
| Security Exclusions | `spotbugs-exclude.xml` (SpotBugs false positives with justifications) |
| Entities | `src/main/java/.../entity/` |
| Services | `src/main/java/.../service/` |
| Controllers | `src/main/java/.../controller/` |
| Templates | `src/main/resources/templates/` |
| Migrations (Production) | `src/main/resources/db/migration/` (V001-V007) |
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
./mvnw test

# Run specific functional test
./mvnw test -Dtest=MfgBomTest

# Run with visible browser (debugging)
./mvnw test -Dtest=MfgBomTest -Dplaywright.headless=false -Dplaywright.slowmo=100

# Run SpotBugs security analysis
./mvnw spotbugs:check
# Results: target/spotbugsXml.xml

# Run only DAST tests
./mvnw test -Dtest=ZapDastTest
# Results: target/security-reports/zap-*.html

# Run DAST in quick mode (passive scan only, ~1 min)
./mvnw test -Dtest=ZapDastTest -Ddast.quick=true
```

## Database

- PostgreSQL via Testcontainers (tests)
- Production migrations: V001-V007 (schema + minimal bootstrap + bank recon + analysis reports)
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

## Current Release

**2026.02-RELEASE** deployed to production (akunting.artivisi.id). See `docs/releases/2026.02-RELEASE.md` for release notes.

## Current Focus

Phase 6 (Security Hardening) partially complete. Next: Phase 7 (API Foundation).

User Manual (14-section structure, published at artivisi.com/aplikasi-akunting/):
- 01-setup-awal.md through 14-rekonsiliasi-bank.md
- Lampiran (appendices) section at the end
- Screenshots auto-generated by functional tests via `UserManualGenerator`

See `docs/06-implementation-plan.md` for full plan
